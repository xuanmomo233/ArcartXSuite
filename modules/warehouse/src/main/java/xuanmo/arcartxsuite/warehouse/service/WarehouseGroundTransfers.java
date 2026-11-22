package xuanmo.arcartxsuite.warehouse.service;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable.GroundDepositResult;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.GroundSource;

/** A marked ground entity remains the authority for its remainder until the disk readback. */
final class WarehouseGroundTransfers implements Listener {
    private static final int MAX_ACTIVE = 64;
    private static final int MAX_CHUNKS_PER_TICK = 8;
    private static final int PROOF_RESULTS_PER_CALLBACK = 8;
    private static final int MAX_SETTLEMENTS_PER_TICK = 16;
    private static final int PARALLEL_SETTLEMENT_THRESHOLD = 16;
    private static final int SAVE_TIMEOUT_TICKS = 100;
    private static final int NATURAL_SAVE_RETRY_TICKS = 20;
    private static final int DENSE_CHUNK_PICKUPS = 4;
    private static final int SNAPSHOT_COALESCE_TICKS = 3;
    private record ChunkKey(Path directory, UUID world, int x, int z) {}
    private record Proof(Work entry, GroundEntityCheckpoint.Expected expected, boolean after, boolean expired,
                         GroundSource source, WarehouseService.GroundPlan plan) {}
    private enum ProofAction { RETRY, APPLIED, SETTLED, INSPECT, UNRESOLVED }
    private record ProofResult(Proof proof, ProofAction action) {}
    private final JavaPlugin plugin;
    private final WarehouseRepository repository;
    private final Logger logger;
    private final java.util.concurrent.Executor executor;
    private final NamespacedKey marker;
    private final NamespacedKey frozenGravity;
    private final Map<UUID, Work> work = new LinkedHashMap<>();
    private final Map<ChunkKey, Integer> workPerChunk = new java.util.HashMap<>();
    private final Set<ChunkKey> readingChunks = new HashSet<>();
    private final ArrayDeque<Work> settledWork = new ArrayDeque<>();
    private final Semaphore extraSettlementWorkers = new Semaphore(3);
    private BukkitTask retry;
    private volatile boolean active;
    private long ticks;
    private long checkpointOrder;

    WarehouseGroundTransfers(JavaPlugin plugin, WarehouseRepository repository, Logger logger, java.util.concurrent.Executor executor) {
        this.plugin = plugin;
        this.repository = repository;
        this.logger = logger;
        this.executor = executor;
        marker = new NamespacedKey("arcartxsuite", "warehouse_ground_transfer");
        frozenGravity = new NamespacedKey("arcartxsuite", "warehouse_ground_gravity");
    }

    void start() {
        active = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        retry = Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, 1L, 1L);
        for (var world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                if (!chunk.isEntitiesLoaded()) continue;
                for (var entity : chunk.getEntities()) if (entity instanceof Item item) recover(item);
            }
        }
    }

    void close() {
        active = false;
        if (retry != null) retry.cancel();
        HandlerList.unregisterAll(this);
        work.clear();
        workPerChunk.clear();
        readingChunks.clear();
        settledWork.clear();
    }

    boolean isReserved(Item item) {
        return item != null && (work.containsKey(item.getUniqueId()) || readMarker(item) != null);
    }

    boolean hasCapacity() {
        return work.size() < MAX_ACTIVE;
    }

    boolean begin(Item item, ItemStack requested, BooleanSupplier authorize, Planner planner,
                  Predicate<GroundDepositResult> completion) {
        if (!active || !hasCapacity() || isReserved(item) || !item.isValid() || item.isDead()
            || !item.isPersistent() || !item.getItemStack().isSimilar(requested)) return false;
        if (!authorize.getAsBoolean()) return false;
        Work entry = freeze(item, UUID.randomUUID().toString(), completion);
        if (entry == null) return false;
        String target = item.getWorld().getName() + " " + entry.chunkX + "," + entry.chunkZ;
        int original = item.getItemStack().getAmount();
        worker(entry, () -> {
            var plan = planner.plan(entry.id, original, target);
            if (plan == null) {
                main(entry, () -> finish(entry, false, 0, true));
                return;
            }
            var source = new GroundSource(entry.id, plan.transfer().ownerId(), entry.world, entry.entity.toString(),
                entry.chunkX, entry.chunkZ, original, Math.toIntExact(plan.stored()), "PREPARED");
            if (!repository.prepareGroundSource(plan.transfer(), source)) throw new IllegalStateException("未写入地面来源");
            main(entry, () -> {
                entry.plan = plan;
                entry.source = source;
                checkpoint(entry, entry.id + ":before", source.original(), false);
            });
        });
        return true;
    }

    private void recover(Item item) {
        if (!active || work.containsKey(item.getUniqueId())) return;
        String value = readMarker(item);
        if (value == null) return;
        String id = value.indexOf(':') == 36 ? value.substring(0, 36) : "";
        try { UUID.fromString(id); }
        catch (IllegalArgumentException malformed) { return; }
        Work entry = freeze(item, id, null);
        if (entry != null) inspect(entry);
    }

    private Work freeze(Item item, String id, Predicate<GroundDepositResult> completion) {
        if (work.size() >= MAX_ACTIVE) return null;
        var position = item.getLocation();
        var world = item.getWorld();
        int x = position.getBlockX() >> 4;
        int z = position.getBlockZ() >> 4;
        Work entry = new Work(item, id, world.getUID(), world.getWorldFolder().toPath(), x, z, completion);
        var container = item.getPersistentDataContainer();
        if (!container.has(frozenGravity, PersistentDataType.BYTE)) {
            container.set(frozenGravity, PersistentDataType.BYTE, item.hasGravity() ? (byte) 1 : (byte) 0);
        }
        if (readMarker(item) == null) container.set(marker, PersistentDataType.STRING, id + ":before");
        item.setGravity(false);
        item.setVelocity(new Vector());
        work.put(entry.entity, entry);
        workPerChunk.merge(entry.chunk, 1, Integer::sum);
        return entry;
    }

    private void decrementChunkWork(Work entry) {
        int count = workPerChunk.get(entry.chunk);
        if (count == 1) workPerChunk.remove(entry.chunk);
        else workPerChunk.put(entry.chunk, count - 1);
    }

    private void inspect(Work entry) {
        entry.inspect = true;
        entry.value = null;
        worker(entry, () -> {
            var source = repository.groundSource(entry.id);
            if (source != null && (!source.entity().equals(entry.entity.toString()) ||
                !source.world().equals(entry.world))) {
                main(entry, () -> unresolved(entry, "地面实体身份与入库凭证不一致"));
                return;
            }
            GroundSource current = source;
            main(entry, () -> {
                entry.inspect = false;
                entry.source = current;
                if (current == null || "RETURNED".equals(current.state())) {
                    if (current != null && entry.item.getItemStack().getAmount() != current.original()) {
                        unresolved(entry, "未提交的掉落物数量与来源凭证不一致");
                    } else finish(entry, false, 0, true);
                } else if ("SETTLED".equals(current.state())) {
                    queueSettled(entry);
                } else if ("COMMITTED".equals(current.state())) {
                    applyAndConfirm(entry);
                } else if ("PREPARED".equals(current.state())) {
                    if (entry.item.getItemStack().getAmount() != current.original()
                        || !(entry.id + ":before").equals(readMarker(entry.item))) {
                        unresolved(entry, "未提交的掉落物状态与来源凭证不一致");
                    } else {
                        cancelPrepared(entry, current);
                    }
                } else {
                    unresolved(entry, "入库凭证状态未知");
                }
            });
        });
    }

    private void cancelPrepared(Work entry, GroundSource source) {
        worker(entry, () -> {
            repository.finishGroundSource(source, false);
            main(entry, () -> inspect(entry));
        });
    }

    private void checkpoint(Work entry, String value, int amount, boolean after) {
        entry.inspect = false;
        entry.requestedSave = false;
        entry.naturalSave = false;
        entry.retries = 0;
        // A single native snapshot contains all marked entities in the chunk.
        // Briefly wait for adjacent admission batches only in a busy chunk.
        entry.snapshotReady = ticks + (workPerChunk.get(entry.chunk) >= DENSE_CHUNK_PICKUPS ? SNAPSHOT_COALESCE_TICKS : 0);
        entry.snapshotOrder = ++checkpointOrder;
        entry.value = value;
        entry.amount = amount;
        entry.after = after;
        entry.deadline = ticks + SAVE_TIMEOUT_TICKS;
        entry.next = ticks;
    }

    private void pulse() {
        ticks++;
        drainSettled();
        if (work.isEmpty()) return;
        boolean nativeSave = GroundEntitySave.available();
        if (nativeSave) requestNextSnapshot();
        int budget = MAX_CHUNKS_PER_TICK;
        Map<ChunkKey, List<Proof>> checks = null;
        for (var iterator = work.values().iterator(); iterator.hasNext();) {
            Work entry = iterator.next();
            if (entry.settledQueued) continue;
            if (!entry.item.isValid() || entry.item.isDead()) {
                if (!entry.inFlight) {
                    iterator.remove();
                    decrementChunkWork(entry);
                    mainCallback(() -> unresolvedCallback(entry, "冻结掉落物离开了已加载区块"));
                }
                continue;
            }
            if (entry.inFlight || entry.next > ticks) continue;
            if (entry.inspect) {
                if (budget == 0) continue;
                budget--;
                inspect(entry);
                continue;
            }
            if (entry.value == null) continue;
            if (readingChunks.contains(entry.chunk)) continue;
            // Do not spend a worker on an unchanged file while this chunk is still waiting
            // for its first snapshot. A natural save or final read can still prove the marker.
            if (nativeSave && !entry.requestedSave && !entry.naturalSave && ticks < entry.deadline) continue;
            List<Proof> batch = checks == null ? null : checks.get(entry.chunk);
            if (batch == null) {
                if (budget == 0) continue;
                budget--;
                if (checks == null) checks = new LinkedHashMap<>();
                batch = new ArrayList<>();
                checks.put(entry.chunk, batch);
            }
            int interval = entry.requestedSave ? 2 << Math.min(entry.retries, 3) : NATURAL_SAVE_RETRY_TICKS;
            entry.next = Math.min(entry.deadline, ticks + interval);
            entry.inFlight = true;
            batch.add(new Proof(entry, new GroundEntityCheckpoint.Expected(entry.entity, entry.value, entry.amount),
                entry.after, ticks >= entry.deadline, entry.source, entry.plan));
        }
        if (checks != null) checks.forEach(this::checkDisk);
    }

    private void requestNextSnapshot() {
        Work first = null;
        for (Work entry : work.values()) {
            if (entry.value != null && !entry.inspect && !entry.inFlight && !entry.settledQueued && !entry.requestedSave
                && entry.snapshotReady <= ticks && ticks < entry.deadline
                && (first == null || entry.snapshotOrder < first.snapshotOrder)) first = entry;
        }
        if (first == null) return;
        // Preserve the budget of one Paper entity serialization per tick, independent of worker retries.
        boolean queued = GroundEntitySave.request(first.item, first.worldId, first.chunkX, first.chunkZ);
        if (!queued) {
            first.naturalSave = true;
            first.snapshotReady = ticks + NATURAL_SAVE_RETRY_TICKS;
            first.snapshotOrder = ++checkpointOrder;
            return;
        }
        for (Work entry : work.values()) {
            if (entry.value != null && !entry.inspect && !entry.settledQueued && !entry.requestedSave && entry.chunk.equals(first.chunk)) {
                entry.requestedSave = true;
                entry.next = Math.min(entry.deadline, ticks + 1);
            }
        }
    }

    private void checkDisk(ChunkKey chunk, List<Proof> proofs) {
        readingChunks.add(chunk);
        try {
            executor.execute(() -> {
                Map<UUID, GroundEntityCheckpoint.Result> validated;
                try {
                    validated = GroundEntityCheckpoint.readBatch(
                        chunk.directory(), chunk.x(), chunk.z(), marker.toString(), proofs.stream().map(Proof::expected).toList());
                } catch (RuntimeException failure) {
                    logger.warning("拾取实体区块读回失败: " + failure.getMessage());
                    validated = Map.of();
                }
                // A slow SQL commit must not keep the chunk's disk reader occupied.
                mainCallback(() -> readingChunks.remove(chunk));
                dispatchProofs(proofs, validated);
            });
        } catch (RuntimeException failure) {
            readingChunks.remove(chunk);
            for (Proof proof : proofs) unresolved(proof.entry(), failure.getMessage());
        }
    }

    private void dispatchProofs(List<Proof> proofs, Map<UUID, GroundEntityCheckpoint.Result> validated) {
        int confirmed = 0;
        if (proofs.size() >= PARALLEL_SETTLEMENT_THRESHOLD && repository.supportsParallelGroundSettlement()) {
            for (Proof proof : proofs) {
                if (validated.get(proof.expected().entityId()) == GroundEntityCheckpoint.Result.CONFIRMED) confirmed++;
            }
        }
        int extra = 0;
        for (int max = Math.min(3, confirmed / 8 - 1); extra < max && extraSettlementWorkers.tryAcquire(); extra++) {
            // The caller is already a background worker and handles the first group.
        }
        if (extra == 0) {
            settleProofs(proofs, validated);
            return;
        }
        List<List<Proof>> groups = new ArrayList<>(extra + 1);
        for (int index = 0; index <= extra; index++) groups.add(new ArrayList<>());
        Map<String, Integer> owners = new LinkedHashMap<>();
        for (Proof proof : proofs) {
            if (validated.get(proof.expected().entityId()) != GroundEntityCheckpoint.Result.CONFIRMED) {
                groups.get(0).add(proof);
                continue;
            }
            // Work for one warehouse owner stays on one worker within a read batch.
            int index = owners.computeIfAbsent(proof.source().owner(), ignored -> owners.size() % groups.size());
            groups.get(index).add(proof);
        }
        for (int index = 1; index < groups.size(); index++) {
            List<Proof> group = groups.get(index);
            if (group.isEmpty()) {
                extraSettlementWorkers.release();
                continue;
            }
            try {
                executor.execute(() -> {
                    try {
                        settleProofs(group, validated);
                    } finally {
                        extraSettlementWorkers.release();
                    }
                });
            } catch (RuntimeException schedulingFailure) {
                extraSettlementWorkers.release();
                groups.get(0).addAll(group);
            }
        }
        settleProofs(groups.get(0), validated);
    }

    private void settleProofs(List<Proof> proofs, Map<UUID, GroundEntityCheckpoint.Result> validated) {
        List<ProofResult> results = new ArrayList<>(Math.min(proofs.size(), PROOF_RESULTS_PER_CALLBACK));
        int finished = 0;
        for (Proof proof : proofs) {
            ProofAction action = finishProof(proof, validated.get(proof.expected().entityId()));
            results.add(new ProofResult(proof, action));
            if (action != ProofAction.RETRY && ++finished == PROOF_RESULTS_PER_CALLBACK) {
                publishProofResults(results);
                results = new ArrayList<>(PROOF_RESULTS_PER_CALLBACK);
                finished = 0;
            }
        }
        if (!results.isEmpty()) publishProofResults(results);
    }

    private void publishProofResults(List<ProofResult> results) {
        mainCallback(() -> completeProofs(results));
    }

    private ProofAction finishProof(Proof proof, GroundEntityCheckpoint.Result result) {
        Work entry = proof.entry();
        try {
            if (result != GroundEntityCheckpoint.Result.CONFIRMED) {
                if (!proof.expired()) return ProofAction.RETRY;
                if (proof.after()) return ProofAction.UNRESOLVED;
                repository.finishGroundSource(proof.source(), false);
                return ProofAction.INSPECT;
            }
            if (proof.after()) {
                return repository.finishGroundSource(proof.source(), true) ? ProofAction.SETTLED : ProofAction.INSPECT;
            }
            var plan = proof.plan();
            if (!repository.commitGroundDeposit(plan.changes(), plan.transfer(), plan.stored())) return ProofAction.INSPECT;
            if (proof.source().remaining() != 0) return ProofAction.APPLIED;
            // A full deposit has no remainder to save. The confirmed before marker fences the
            // original entity even if the process stops after the terminal receipt but before removal.
            return repository.finishGroundSource(proof.source(), true) ? ProofAction.SETTLED : ProofAction.INSPECT;
        } catch (Exception failure) {
            logger.warning("拾取入库暂未确认 " + entry.id + "：" + failure.getMessage());
            return ProofAction.INSPECT;
        }
    }

    private void completeProofs(List<ProofResult> results) {
        for (ProofResult result : results) {
            Work entry = result.proof().entry();
            if (work.get(entry.entity) != entry) continue;
            entry.inFlight = false;
            if (!entry.item.isValid() || entry.item.isDead()) {
                unresolved(entry, "冻结掉落物离开了已加载区块");
                continue;
            }
            switch (result.action()) {
                case APPLIED -> applyAndConfirm(entry);
                case SETTLED -> queueSettled(entry);
                case INSPECT -> inspect(entry);
                case UNRESOLVED -> unresolved(entry, "余量存档未确认，保留实体标记等待重新加载");
                case RETRY -> entry.retries++;
            }
        }
    }

    private void applyAndConfirm(Work entry) {
        if (apply(entry)) {
            if (entry.source.remaining() == 0) {
                entry.inspect = true;
                worker(entry, () -> {
                    boolean settled = repository.finishGroundSource(entry.source, true);
                    main(entry, () -> {
                        if (settled) queueSettled(entry);
                        else inspect(entry);
                    });
                });
                return;
            }
            int amount = entry.source.remaining();
            checkpoint(entry, entry.id + ":after", amount, true);
        }
    }

    private boolean apply(Work entry) {
        GroundSource source = entry.source;
        int amount = entry.item.getItemStack().getAmount();
        String current = readMarker(entry.item);
        int after = source.remaining() > 0 ? source.remaining() : source.original();
        if (amount == after && (entry.id + ":after").equals(current)) return true;
        if (amount != source.original() || !(entry.id + ":before").equals(current)) {
            unresolved(entry, "掉落物数量与入库凭证不一致");
            return false;
        }
        if (source.remaining() == 0) return true;
        ItemStack remainder = entry.item.getItemStack().clone();
        remainder.setAmount(after);
        entry.item.setItemStack(remainder);
        entry.item.getPersistentDataContainer().set(marker, PersistentDataType.STRING, entry.id + ":after");
        return true;
    }

    private void finishSettled(Work entry) {
        if (!entry.item.isValid() || entry.item.isDead()) {
            unresolved(entry, "冻结掉落物离开了已加载区块");
            return;
        }
        if (apply(entry)) finish(entry, true, entry.source.stored(), true);
    }

    private void queueSettled(Work entry) {
        if (entry.settledQueued) return;
        // Leave the marker and ownership in place until the tick budget allows the live update.
        entry.settledQueued = true;
        settledWork.addLast(entry);
    }

    private void drainSettled() {
        for (int i = 0; i < MAX_SETTLEMENTS_PER_TICK; i++) {
            Work entry = settledWork.pollFirst();
            if (entry == null) return;
            if (work.get(entry.entity) == entry) finishSettled(entry);
        }
    }

    private void finish(Work entry, boolean committed, int stored, boolean notify) {
        if (!work.remove(entry.entity, entry)) return;
        decrementChunkWork(entry);
        if (entry.item.isValid() && !entry.item.isDead()) {
            if (committed && entry.source.remaining() == 0) {
                removeSettledItem(entry.item);
            } else {
                var container = entry.item.getPersistentDataContainer();
                Byte gravity = container.get(frozenGravity, PersistentDataType.BYTE);
                if (gravity != null) entry.item.setGravity(gravity != 0);
                container.remove(frozenGravity);
                container.remove(marker);
            }
        }
        boolean showNotice = entry.plan == null ? notify : entry.plan.notifyPlayer();
        if (entry.completion != null) entry.completion.test(new GroundDepositResult(committed, false, stored, showNotice, entry.id));
    }

    private void removeSettledItem(Item item) {
        item.remove();
    }

    private void unresolved(Work entry, String reason) {
        if (!work.remove(entry.entity, entry)) return;
        decrementChunkWork(entry);
        unresolvedCallback(entry, reason);
    }

    private void unresolvedCallback(Work entry, String reason) {
        logger.warning("拾取入库待核对 " + entry.id + "：" + reason);
        if (entry.completion != null) entry.completion.test(new GroundDepositResult(false, true, 0, true, entry.id));
    }

    private String readMarker(Item item) {
        return item.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
    }

    private void worker(Work entry, Checked action) {
        entry.inFlight = true;
        long generation = ++entry.generation;
        try {
            executor.execute(() -> {
                try { action.run(); }
                catch (Exception failure) {
                    main(entry, () -> {
                        logger.warning("拾取入库暂未确认 " + entry.id + "：" + failure.getMessage());
                        entry.next = ticks + 20;
                        if (entry.value == null) entry.inspect = true;
                    });
                } finally {
                    main(entry, () -> {
                        if (entry.generation == generation) entry.inFlight = false;
                    });
                }
            });
        } catch (RuntimeException failure) {
            unresolved(entry, failure.getMessage());
        }
    }

    private void main(Work entry, Runnable action) {
        mainCallback(() -> {
            if (active && work.get(entry.entity) == entry) action.run();
        });
    }

    private void mainCallback(Runnable action) {
        if (!active || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (active) action.run();
        });
    }

    @EventHandler public void load(EntitiesLoadEvent event) {
        for (var entity : event.getEntities()) if (entity instanceof Item item) recover(item);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void pickup(EntityPickupItemEvent event) {
        if (isReserved(event.getItem())) { event.setCancelled(true); recover(event.getItem()); }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void hopper(InventoryPickupItemEvent event) {
        if (isReserved(event.getItem())) { event.setCancelled(true); recover(event.getItem()); }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void merge(ItemMergeEvent event) {
        if (isReserved(event.getEntity()) || isReserved(event.getTarget())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void despawn(ItemDespawnEvent event) {
        if (isReserved(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && isReserved(item)) event.setCancelled(true);
    }

    @FunctionalInterface interface Planner {
        WarehouseService.GroundPlan plan(String id, int original, String target) throws Exception;
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }

    private static final class Work {
        final Item item;
        final UUID entity;
        final String id;
        final String world;
        final UUID worldId;
        final ChunkKey chunk;
        final int chunkX, chunkZ;
        final Predicate<GroundDepositResult> completion;
        WarehouseService.GroundPlan plan;
        GroundSource source;
        String value;
        int amount;
        boolean inFlight, requestedSave, naturalSave, after, inspect, settledQueued;
        int retries;
        long next, deadline, generation, snapshotReady, snapshotOrder;

        Work(Item item, String id, UUID worldId, Path directory, int chunkX, int chunkZ,
             Predicate<GroundDepositResult> completion) {
            this.item = item;
            this.entity = item.getUniqueId();
            this.id = id;
            this.worldId = worldId;
            this.world = worldId.toString();
            this.chunk = new ChunkKey(directory, worldId, chunkX, chunkZ);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.completion = completion;
        }
    }
}
