package xuanmo.arcartxsuite.onlinerewards.service;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Delivery;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ItemCheckpoint;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ItemSink;

/** Stores the exact item and sink before making either inventory or mail visible. */
final class OnlineRewardsItemDelivery implements AutoCloseable {
    enum StageResult { OWNED, UNSTARTED, INVALID }

    private static final int MAX_ACTIVE = 32;
    static final NamespacedKey RECEIPT = new NamespacedKey("arcartxsuite", "onlinerewards_item_receipt");
    private final JavaPlugin plugin;
    private final OnlineRewardsRepository repository;
    private final Path playerData;
    private final Supplier<OnlineRewardsItemMail> mailProvider;
    private final Logger logger;
    private final ModuleExecutor worker = new ModuleExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(MAX_ACTIVE * 2), task -> {
            Thread thread = new Thread(task, "AXS-OnlineRewards-Item");
            thread.setDaemon(true);
            return thread;
        });
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<PrimaryDelivery> primaryQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean primaryScheduled = new AtomicBoolean();
    private volatile boolean closed;
    private String node;

    OnlineRewardsItemDelivery(JavaPlugin plugin, OnlineRewardsRepository repository, Path playerData,
                              Supplier<OnlineRewardsItemMail> mailProvider, Logger logger) {
        this.plugin = plugin;
        this.repository = repository;
        this.playerData = playerData;
        this.mailProvider = mailProvider;
        this.logger = logger;
    }

    void stage(Player player, Delivery delivery, UUID attempt, ItemStack item, BooleanSupplier current,
               Consumer<StageResult> result) {
        UUID playerId = delivery.playerUuid();
        if (!acquire(playerId)) {
            result.accept(StageResult.UNSTARTED);
            return;
        }
        try {
            byte[] serialized = ItemSerializer.serialize(item);
            if (serialized == null || serialized.length == 0) throw new IllegalArgumentException("物品无法序列化");
            String data = Base64.getEncoder().encodeToString(serialized);
            OnlineRewardsItemMail mail = mailProvider.get();
            ItemSink sink = !fits(player, item) && mail != null && mail.available() ? ItemSink.MAIL : ItemSink.INVENTORY;
            if (submit(() -> stage(player, delivery, attempt, data, sink, current, result))) return;
        } catch (RuntimeException failure) {
            warn(playerId, "冻结物品奖励失败", failure);
            active.remove(playerId);
            result.accept(StageResult.INVALID);
            return;
        }
        active.remove(playerId);
        result.accept(StageResult.UNSTARTED);
    }

    private void stage(Player player, Delivery delivery, UUID attempt, String data, ItemSink sink, BooleanSupplier current,
                       Consumer<StageResult> result) {
        ItemCheckpoint checkpoint;
        try {
            checkpoint = new ItemCheckpoint(delivery, attempt, sourceNode(), data, sink);
            if (!repository.stageItemCheckpoint(checkpoint)) {
                finish(delivery.playerUuid(), true);
                result.accept(StageResult.UNSTARTED);
                return;
            }
        } catch (SQLException failure) {
            warn(delivery.playerUuid(), "保存物品待交付记录失败", failure);
            finish(delivery.playerUuid(), true);
            result.accept(StageResult.UNSTARTED);
            return;
        } catch (IOException | RuntimeException failure) {
            warn(delivery.playerUuid(), "无法确定物品存档归属", failure);
            finish(delivery.playerUuid(), true);
            result.accept(StageResult.INVALID);
            return;
        }
        result.accept(StageResult.OWNED);
        resume(player, checkpoint, current, null, false);
    }

    void recover(Player player, BooleanSupplier current) {
        UUID id = player.getUniqueId();
        if (!acquire(id)) return;
        String live = player.getPersistentDataContainer().get(RECEIPT, PersistentDataType.STRING);
        if (!submit(() -> recover(player, id, live, current))) finish(id, true);
    }

    private void recover(Player player, UUID id, String live, BooleanSupplier current) {
        try {
            ItemCheckpoint checkpoint = repository.itemCheckpoint(id).orElse(null);
            if (checkpoint == null) {
                finish(id, false);
                return;
            }
            // Durable shared Mail can replay the original delivery key from any node.
            // Local Mail and inventory still require the original player-data identity.
            OnlineRewardsItemMail mail = checkpoint.sink() == ItemSink.MAIL ? mailProvider.get() : null;
            boolean sharedMail = mail != null && mail.canRecoverAcrossNodes();
            if (!sharedMail && !sourceNode().equals(checkpoint.node())) {
                finish(id, false);
                return;
            }
            if (checkpoint.sink() == ItemSink.MAIL) {
                resume(player, checkpoint, current, null, false);
                return;
            }
            Path file = playerFile(id);
            String saved = PlayerDataReceipt.readExisting(file, RECEIPT.toString());
            String token = checkpoint.attempt().toString();
            if (token.equals(saved) != token.equals(live)) {
                warn(id, "在线背包与玩家存档的物品凭证不一致，等待重新登录", null);
                finish(id, false);
            } else if (token.equals(saved)) {
                acknowledge(checkpoint);
            } else {
                resume(player, checkpoint, current, live, true);
            }
        } catch (NoSuchFileException missingSave) {
            finish(id, true);
        } catch (IOException | SQLException | RuntimeException failure) {
            warn(id, "读取物品待交付记录失败", failure);
            finish(id, true);
        }
    }

    private void resume(Player player, ItemCheckpoint checkpoint, BooleanSupplier current, String expected, boolean recovered) {
        UUID id = checkpoint.delivery().playerUuid();
        PrimaryDelivery pending = new PrimaryDelivery(id, () -> apply(player, checkpoint, current, expected, recovered));
        primaryQueue.add(pending);
        if (closed && primaryQueue.remove(pending)) {
            finish(id, false);
            return;
        }
        schedulePrimary();
    }

    private void apply(Player player, ItemCheckpoint checkpoint, BooleanSupplier current, String expected, boolean recovered) {
        UUID id = checkpoint.delivery().playerUuid();
        if (closed || !current.getAsBoolean()) {
            finish(id, !closed);
            return;
        }
        try {
            ItemStack item = ItemSerializer.deserializeStrict(Base64.getDecoder().decode(checkpoint.itemData()));
            if (InventoryItems.empty(item)) throw new IllegalStateException("待交付物品无法完整反序列化");
            if (checkpoint.sink() == ItemSink.MAIL) {
                sendMail(checkpoint, item);
            } else if (!recovered || java.util.Objects.equals(expected,
                player.getPersistentDataContainer().get(RECEIPT, PersistentDataType.STRING))) {
                applyInventory(player, checkpoint, item);
            } else {
                finish(id, false);
            }
        } catch (IOException | RuntimeException failure) {
            warn(id, "物品交付暂未确认", failure);
            finish(id, true);
        }
    }

    private void schedulePrimary() {
        if (closed || !primaryScheduled.compareAndSet(false, true)) return;
        try {
            // SQL may complete many checkpoints together. Spread the actual player saves across ticks.
            Bukkit.getScheduler().runTaskLater(plugin, this::runPrimaryDelivery, 1L);
        } catch (RuntimeException rejected) {
            primaryScheduled.set(false);
            for (PrimaryDelivery job; (job = primaryQueue.poll()) != null; ) finish(job.player(), !closed);
        }
    }

    private void runPrimaryDelivery() {
        try {
            PrimaryDelivery pending = primaryQueue.poll();
            if (!closed && pending != null) pending.action().run();
        } finally {
            primaryScheduled.set(false);
            if (!primaryQueue.isEmpty()) schedulePrimary();
        }
    }

    private record PrimaryDelivery(UUID player, Runnable action) {}

    private void applyInventory(Player player, ItemCheckpoint checkpoint, ItemStack item) throws IOException {
        UUID id = checkpoint.delivery().playerUuid();
        var inventory = player.getInventory();
        ItemStack[] before = InventoryItems.copy(inventory.getStorageContents());
        ItemStack[] after = InventoryItems.copy(before);
        if (!InventoryItems.put(after, item)) {
            finish(id, true);
            return;
        }
        var data = player.getPersistentDataContainer();
        String prior = data.get(RECEIPT, PersistentDataType.STRING);
        String token = checkpoint.attempt().toString();
        boolean saved = false;
        try {
            inventory.setStorageContents(after);
            data.set(RECEIPT, PersistentDataType.STRING, token);
            saveInventoryCheckpoint(player);
            // Verify before returning control to gameplay. A failed/unknown save must not expose a movable item.
            if (!token.equals(PlayerDataReceipt.read(playerFile(id), RECEIPT.toString()))) {
                throw new IOException("玩家存档未写入本次在线奖励凭证");
            }
            saved = true;
        } finally {
            if (!saved) {
                inventory.setStorageContents(before);
                if (prior == null) data.remove(RECEIPT);
                else data.set(RECEIPT, PersistentDataType.STRING, prior);
            }
        }
        if (!submit(() -> acknowledge(checkpoint))) finish(id, true);
    }

    private void saveInventoryCheckpoint(Player player) {
        player.saveData();
    }

    private void sendMail(ItemCheckpoint checkpoint, ItemStack item) {
        UUID id = checkpoint.delivery().playerUuid();
        OnlineRewardsItemMail mail = mailProvider.get();
        if (mail == null || !mail.available()) {
            finish(id, true);
            return;
        }
        mail.deliver(checkpoint, item).whenComplete((result, failure) -> {
            if (failure == null && Boolean.TRUE.equals(result) && submit(() -> acknowledge(checkpoint))) return;
            if (failure != null) warn(id, "邮件物品发放暂未确认", failure);
            finish(id, true);
        });
    }

    private void acknowledge(ItemCheckpoint checkpoint) {
        UUID id = checkpoint.delivery().playerUuid();
        boolean completed = false;
        try {
            completed = repository.completeItemCheckpoint(checkpoint);
        } catch (SQLException failure) {
            warn(id, "确认在线物品奖励失败", failure);
        } finally {
            finish(id, !completed);
        }
    }

    Set<UUID> waitingPlayers() {
        return Set.copyOf(waiting);
    }

    void forget(UUID player) {
        waiting.remove(player);
    }

    private boolean acquire(UUID player) {
        return !closed && active.size() < MAX_ACTIVE && active.add(player);
    }

    private void finish(UUID player, boolean retry) {
        if (!closed && retry) waiting.add(player);
        else waiting.remove(player);
        active.remove(player);
    }

    private boolean submit(Runnable task) {
        if (closed) return false;
        try {
            worker.execute(task);
            return true;
        } catch (RejectedExecutionException busy) {
            return false;
        }
    }

    private String sourceNode() throws IOException {
        if (node != null) return node;
        Files.createDirectories(playerData);
        Path identity = playerData.resolve(".arcartxsuite-onlinerewards-node");
        if (!Files.exists(identity)) {
            try {
                Files.writeString(identity, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) {
                // Another initializer created the persistent player-data identity.
            }
        }
        node = UUID.fromString(Files.readString(identity).trim()).toString();
        return node;
    }

    private Path playerFile(UUID id) {
        return playerData.resolve(id + ".dat");
    }

    private static boolean fits(Player player, ItemStack item) {
        return InventoryItems.put(InventoryItems.copy(player.getInventory().getStorageContents()), item);
    }

    private void warn(UUID player, String message, Throwable failure) {
        logger.log(Level.WARNING, message + " " + player, failure);
    }

    @Override
    public void close() {
        closed = true;
        waiting.clear();
        primaryQueue.clear();
        worker.shutdown();
    }

    java.util.concurrent.CompletionStage<Void> stopped() { return worker.stopped(); }
}
