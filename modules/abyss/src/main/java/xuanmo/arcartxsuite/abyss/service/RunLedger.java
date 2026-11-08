package xuanmo.arcartxsuite.abyss.service;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.api.capability.AbyssRunLootAccess;

/** Durable run-scoped facts. Every mutation uses the shared database queue and a SQL run-row lock. */
public final class RunLedger implements AbyssRunLootAccess {
    static final Gson JSON = new Gson();
    static final class Data {
        UUID run;
        String owner;
        String state = "ACTIVE";
        int reviveLimit;
        long revision = 1;
        List<UUID> roster = new ArrayList<>();
        List<UUID> departed = new ArrayList<>();
        Map<UUID, Integer> used = new LinkedHashMap<>();
        Map<String, ReviveTransactions.Lease> leases = new LinkedHashMap<>();
        List<LootEntry> loot = new ArrayList<>();
    }
    private final AbyssService service;
    private record Delivery(UUID run, String key, UUID player,
                            List<xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward> rewards,
                            List<LootEntry> entries) { }
    private xuanmo.arcartxsuite.api.item.PendingRewardService rewards;
    private final java.util.concurrent.atomic.AtomicBoolean replaying = new java.util.concurrent.atomic.AtomicBoolean();
    private long nextReplay;
    private String replayAfter = "";
    final JdbcAbyssRepository repository;
    final PlayerDataManager database;

    RunLedger(AbyssService service, JdbcAbyssRepository repository, PlayerDataManager database) {
        this.service = service;
        this.repository = repository;
        this.database = database;
    }

    public void rewards(xuanmo.arcartxsuite.api.item.PendingRewardService rewards) { this.rewards = rewards; }

    public static List<LootEntry> describe(String key, String source,
            List<xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward> rewards) {
        var entries = new ArrayList<LootEntry>();
        for (int index = 0; index < rewards.size(); index++) {
            var reward = rewards.get(index);
            String id = reward.type().name();
            String name = reward.description() == null ? id : reward.description();
            double quantity = 1;
            var payload = com.google.gson.JsonParser.parseString(reward.payload()).getAsJsonObject();
            switch (reward.type()) {
                case ITEM_SERIALIZED -> {
                    var item = xuanmo.arcartxsuite.api.util.ItemSerializer.deserialize(
                        java.util.Base64.getDecoder().decode(payload.get("data").getAsString()));
                    id = item.getType().getKey().toString();
                    quantity = item.getAmount();
                    name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName() : item.getType().name();
                }
                case ITEM -> {
                    id = payload.get("source").getAsString() + ":" + payload.get("id").getAsString();
                    quantity = payload.get("amount").getAsDouble();
                }
                case CURRENCY -> {
                    id = "currency:" + payload.get("currencyId").getAsString();
                    quantity = payload.get("amount").getAsDouble();
                }
                default -> { }
            }
            entries.add(new LootEntry(key + "/" + index, id, name, quantity, null, source,
                reward.playerUuid(), Scope.PERSONAL, State.PENDING, System.currentTimeMillis()));
        }
        return List.copyOf(entries);
    }

    /** Metadata is captured on the server thread; the journal and host submission run on the database queue. */
    public CompletableFuture<Void> submit(UUID run, String key,
            List<xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward> batch, List<LootEntry> entries) {
        if (batch.isEmpty()) return CompletableFuture.completedFuture(null);
        var delivery = new Delivery(run, key, batch.get(0).playerUuid(), List.copyOf(batch), List.copyOf(entries));
        return database.submitDatabase(() -> {
            submitNow(delivery);
            return null;
        });
    }

    private void submitNow(Delivery delivery) throws Exception {
        UUID run = delivery.run();
        String document = deliveryKey(delivery.key());
        repository.runTransaction(run, documents -> {
            String accepted = documents.read("accepted/" + document.substring(9));
            if (accepted != null) {
                verifyDelivery(accepted, delivery);
                return null;
            }
            String existing = documents.read(document);
            if (existing != null) {
                verifyDelivery(existing, delivery);
                return null;
            }
            documents.insert(document, delivery.player(), JSON.toJson(delivery));
            var data = read(documents, run);
            for (var entry : delivery.entries()) {
                data.loot.removeIf(old -> old.entryId().equals(entry.entryId()));
                data.loot.add(entry);
            }
            if (data.loot.size() > 10000) throw new IllegalStateException("LOOT_CAPACITY");
            save(documents, data);
            return null;
        });
        String pending = repository.runDocument(document);
        if (pending != null) deliver(JSON.fromJson(pending, Delivery.class));
    }

    private static void verifyDelivery(String raw, Delivery delivery) {
        var previous = JSON.fromJson(raw, Delivery.class);
        if (!previous.run().equals(delivery.run()) || !sameRewards(previous, delivery))
            throw new IllegalArgumentException("DELIVERY_CONFLICT");
    }

    private static boolean sameRewards(Delivery left, Delivery right) {
        return left.rewards().stream().map(reward -> reward.playerUuid() + "/" + reward.type() + "/" + reward.payload())
            .toList().equals(right.rewards().stream()
                .map(reward -> reward.playerUuid() + "/" + reward.type() + "/" + reward.payload()).toList());
    }

    /** Worker-thread association, retained with the offer after instance cleanup. */
    public void linkOffer(UUID offer, UUID run) throws Exception {
        repository.runTransaction(run, documents -> {
            documents.write("offer/" + offer, null, run.toString());
            return null;
        });
    }

    public boolean submitGame(UUID offer, int slot, UUID player,
            List<xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward> batch, List<LootEntry> metadata) throws Exception {
        String raw = repository.runDocument("offer/" + offer);
        String key = "reward-game:" + offer + ":" + slot;
        if (raw == null) return rewards.savePendingBatchConfirmed(key, batch);
        var entries = new ArrayList<LootEntry>();
        for (int index = 0; index < batch.size(); index++) {
            var meta = index < metadata.size() ? metadata.get(index) : null;
            entries.add(new LootEntry(key + "/" + index, meta == null ? batch.get(index).type().name() : meta.rewardId(),
                meta == null ? batch.get(index).description() : meta.displayName(), meta == null ? 1 : meta.quantity(),
                meta == null ? null : meta.quality(), "reward-game:" + offer, player,
                Scope.PERSONAL, State.PENDING, System.currentTimeMillis()));
        }
        submitNow(new Delivery(UUID.fromString(raw), key, player, batch, entries));
        return true;
    }

    private static String deliveryKey(String key) {
        return "delivery/" + UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void deliver(Delivery delivery) throws Exception {
        if (!rewards.savePendingBatchConfirmed(delivery.key(), delivery.rewards()))
            throw new IllegalStateException("HOST_PENDING");
        repository.runTransaction(delivery.run(), documents -> {
            var data = read(documents, delivery.run());
            var ids = delivery.entries().stream().map(LootEntry::entryId).collect(java.util.stream.Collectors.toSet());
            data.loot.replaceAll(entry -> ids.contains(entry.entryId())
                ? new LootEntry(entry.entryId(), entry.rewardId(), entry.displayName(), entry.quantity(), entry.quality(),
                    entry.source(), entry.ownerId(), entry.scope(), State.HOST_ACCEPTED, System.currentTimeMillis())
                : entry);
            save(documents, data);
            documents.write("accepted/" + deliveryKey(delivery.key()).substring(9), delivery.player(), JSON.toJson(delivery));
            documents.delete(deliveryKey(delivery.key()));
            return null;
        });
        service.runOnMain(() -> {
            var player = Bukkit.getPlayer(delivery.player());
            if (service.running() && player != null) rewards.flushPlayer(player);
        });
    }

    void replayRewards() {
        long now = System.currentTimeMillis();
        if (now < nextReplay) return;
        if (!replaying.compareAndSet(false, true)) return;
        nextReplay = now + 30000;
        database.submitMaintenance(() -> {
            var page = repository.runDocuments("delivery/", replayAfter, 25);
            for (var document : page) {
                replayAfter = document.key();
                try { deliver(JSON.fromJson(document.snapshot(), Delivery.class)); }
                catch (Exception failure) { java.util.logging.Logger.getLogger("Abyss").warning("奖励补交等待重试: " + failure.getMessage()); }
            }
            if (page.size() < 25) replayAfter = "";
            return null;
        }).whenComplete((ignored, failure) -> replaying.set(false));
    }

    void admitted(AbyssInstance run) {
        var data = new Data();
        data.run = run.getInstanceId();
        data.owner = run.getTemplate().requirements().externalControlOwner();
        data.reviveLimit = run.getTemplate().requirements().reviveLimit();
        data.roster.addAll(run.getPlayerUUIDs());
        database.submitDatabase(() -> {
            repository.initializeRunDocument(data.run, JSON.toJson(data));
            return null;
        }).exceptionally(error -> { service.logRunResultFailure(data.run, error); return null; });
    }

    static Data read(JdbcAbyssRepository.RunDocuments documents, UUID run) throws Exception {
        String snapshot = documents.read("run/" + run);
        if (snapshot == null) throw new IllegalStateException("UNKNOWN_RUN");
        return JSON.fromJson(snapshot, Data.class);
    }

    static void save(JdbcAbyssRepository.RunDocuments documents, Data data) throws Exception {
        data.revision++;
        documents.write("run/" + data.run, null, JSON.toJson(data));
    }

    void finished(AbyssInstance run, String state) {
        UUID id = run.getInstanceId();
        database.submitDatabase(() -> repository.runTransaction(id, documents -> {
            var data = read(documents, id);
            if (!data.state.equals("ACTIVE")) return null;
            data.state = state;
            save(documents, data);
            return null;
        })).exceptionally(error -> { service.logRunResultFailure(id, error); return null; });
    }

    void departed(AbyssInstance run, UUID player) {
        UUID id = run.getInstanceId();
        database.submitDatabase(() -> repository.runTransaction(id, documents -> {
            var data = read(documents, id);
            if (!data.departed.contains(player)) data.departed.add(player);
            save(documents, data);
            return null;
        })).exceptionally(error -> { service.logRunResultFailure(id, error); return null; });
    }

    /** Called on a worker with the exact confirmed host batch identity. Does not deliver rewards. */
    void record(UUID run, List<LootEntry> entries) throws Exception {
        repository.runTransaction(run, documents -> {
            var data = read(documents, run);
            for (var entry : entries) {
                data.loot.removeIf(previous -> previous.entryId().equals(entry.entryId()));
                data.loot.add(entry);
            }
            if (data.loot.size() > 10000) throw new IllegalStateException("LOOT_CAPACITY");
            save(documents, data);
            return null;
        });
    }

    @Override public CompletableFuture<LootSnapshot> snapshot(UUID run, UUID viewer, String cursor, int limit) {
        requireMain();
        if (run == null || viewer == null || limit < 1 || limit > 100)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid loot query"));
        if (!service.running()) return CompletableFuture.completedFuture(
            new LootSnapshot(run, 0, "UNKNOWN", List.of(), null, true, "UNAVAILABLE"));
        return database.submitDatabase(() -> {
            String raw = repository.runDocument("run/" + run);
            if (raw == null) return new LootSnapshot(run, 0, "UNKNOWN", List.of(), null, true, "UNKNOWN_RUN");
            var data = JSON.fromJson(raw, Data.class);
            if (!data.roster.contains(viewer))
                return new LootSnapshot(run, 0, "UNKNOWN", List.of(), null, true, "FORBIDDEN");
            int offset = 0;
            if (cursor != null) {
                String[] parts = cursor.split("/", -1);
                if (parts.length != 4 || !parts[0].equals(run.toString()) || !parts[1].equals(viewer.toString()))
                    throw new IllegalArgumentException("Invalid loot cursor");
                if (Long.parseLong(parts[2]) != data.revision)
                    return new LootSnapshot(run, data.revision, data.state, List.of(), null, false, "STALE_CURSOR");
                offset = Integer.parseInt(parts[3]);
            }
            var visible = data.loot.stream()
                .filter(entry -> entry.scope() == Scope.SHARED || viewer.equals(entry.ownerId()))
                .sorted(java.util.Comparator.comparing(LootEntry::entryId)).toList();
            if (offset < 0 || offset > visible.size()) throw new IllegalArgumentException("Invalid loot offset");
            int end = Math.min(visible.size(), offset + limit);
            String next = end == visible.size() ? null : run + "/" + viewer + "/" + data.revision + "/" + end;
            return new LootSnapshot(run, data.revision, data.state, visible.subList(offset, end),
                next, next == null, "OK");
        });
    }

    static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call on the server thread");
    }
}
