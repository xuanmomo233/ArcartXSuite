package xuanmo.arcartxsuite.bridge;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.WarehouseRewardDepositable;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.mail.AxsMailRequest;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.bridge.PendingItemSaveStore.Save;
import xuanmo.arcartxsuite.bridge.PendingItemSaveStore.Mode;
import xuanmo.arcartxsuite.bridge.PendingItemSaveStore.Resolution;

/** Delivers staged items to a confirmed player save, warehouse or mailbox. */
final class PendingItemDelivery {
    static final int MAX_ITEMS = 8;
    static final NamespacedKey RECEIPT = new NamespacedKey("arcartxsuite", "fishing_reward_save");
    static final long BOOT = ManagementFactory.getRuntimeMXBean().getStartTime();
    private static final int PAGE = 32;
    private static final int MAX_STAGED = 64;
    private static final int MAX_WAITING_INVENTORY = 512;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path playerData;
    private final Path installation;
    private final PendingItemSaveStore store;
    private final Consumer<Player> retry;
    private final Supplier<AxsMailService> mailProvider;
    private final Supplier<WarehouseRewardDepositable> warehouseProvider;
    private final BiFunction<Player, PendingReward, ItemStack> resolveItem;
    private final boolean sharedHostStorage;
    private final ModuleExecutor io = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), operation -> {
        var thread = new Thread(operation, "AXS-PendingItemDelivery");
        thread.setDaemon(true);
        return thread;
    });
    private final Semaphore staging = new Semaphore(MAX_STAGED);
    private final ArrayBlockingQueue<Application> applications = new ArrayBlockingQueue<>(MAX_STAGED);
    private final ConcurrentHashMap<Save, Resolution> completions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Save> waitingForEvidence = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Save> warehouseDeclines = new ConcurrentLinkedQueue<>();
    private final HashSet<Save> localStages = new HashSet<>();
    private final AtomicBoolean applicationScheduled = new AtomicBoolean();
    private final AtomicBoolean scanQueued = new AtomicBoolean();
    private final LinkedHashSet<UUID> waitingInventory = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, FileVersion> checked = new LinkedHashMap<>();
    private final BukkitTask timer;
    private volatile boolean closed;
    private String node;
    private String cursor = "";
    private long idleUntil;

    PendingItemDelivery(JavaPlugin plugin, Logger logger, Path playerData, PendingItemSaveStore store,
                       Consumer<Player> retry, Supplier<AxsMailService> mailProvider,
                       Supplier<WarehouseRewardDepositable> warehouseProvider,
                       BiFunction<Player, PendingReward, ItemStack> resolveItem) {
        this.plugin = plugin;
        this.logger = logger;
        this.playerData = playerData;
        this.installation = plugin.getDataFolder().toPath().resolve("pending-item-installation.id");
        this.store = store;
        this.retry = retry;
        this.mailProvider = mailProvider;
        this.warehouseProvider = warehouseProvider;
        this.resolveItem = resolveItem;
        this.sharedHostStorage = store.sharedHostStorage();
        this.timer = plugin.getServer().getScheduler().runTaskTimer(plugin, this::requestScan, 20L, 100L);
    }

    void stage(Player player, AuthSnapshot auth, String claim, long claimedAt, List<PendingReward> rewards,
               Consumer<List<PendingReward>> release) {
        if (rewards.isEmpty()) return;
        if (closed || !staging.tryAcquire()) {
            release.accept(rewards);
            return;
        }
        var selected = new ArrayList<Prepared>();
        var deferred = new ArrayList<PendingReward>();
        boolean accepted = false;
        boolean selectionComplete = false;
        try {
            var contents = InventoryItems.copy(player.getInventory().getStorageContents());
            boolean full = false;
            Mode destination = PendingItemSaveStore.requiresCheckpoint(rewards.get(0)) ? Mode.CHECKPOINT : Mode.NATURAL;
            for (var reward : rewards) {
                if (full && !destination.routed()) {
                    deferred.add(reward);
                    continue;
                }
                ItemStack item = resolveItem.apply(player, reward);
                if (InventoryItems.empty(item)) {
                    deferred.add(reward);
                } else if (destination.routed() || InventoryItems.put(contents, item)) {
                    selected.add(prepareItem(reward, item));
                } else {
                    full = true;
                    if (selected.isEmpty()) {
                        if (availableWarehouse() != null) destination = Mode.WAREHOUSE;
                        else if (availableMail() != null) destination = Mode.MAIL;
                    }
                    if (destination.routed()) selected.add(prepareItem(reward, item));
                    else deferred.add(reward);
                }
            }
            if (full && !destination.routed()) deferForSpace(auth.playerId());
            if (!deferred.isEmpty()) release.accept(deferred);
            selectionComplete = true;
            Mode mode = destination;
            accepted = !selected.isEmpty() && submit(() -> reserve(player, auth, claim, claimedAt, mode, selected, release));
        } catch (RuntimeException error) {
            logger.log(Level.WARNING, "待发物品背包预检失败: player=" + auth.playerId(), error);
        } finally {
            if (!accepted) {
                staging.release();
                // Covers rejection before the durable staging transaction.
                if (!selectionComplete) release.accept(rewards);
                else if (!selected.isEmpty()) release.accept(selected.stream().map(Prepared::reward).toList());
            }
        }
    }

    private static Prepared prepareItem(PendingReward reward, ItemStack item) {
        if (reward.type() == PendingReward.Type.ITEM_SERIALIZED || reward.type() == PendingReward.Type.ITEM_NATURAL) {
            return new Prepared(reward, item, null);
        }
        String data = Base64.getEncoder().encodeToString(ItemSerializer.serialize(item));
        String payload = PendingReward.itemSerialized(reward.playerUuid(), reward.sourceModule(), data,
            reward.description()).payload();
        return new Prepared(reward, item, payload);
    }

    private void reserve(Player player, AuthSnapshot auth, String claim, long claimedAt, Mode mode, List<Prepared> items,
                         Consumer<List<PendingReward>> release) {
        boolean staged = false;
        boolean scheduled = false;
        try {
            String owner = !sharedHostStorage ? node() : switch (mode) {
                case MAIL -> PendingItemSaveStore.SHARED_MAIL;
                case WAREHOUSE -> PendingItemSaveStore.SHARED_WAREHOUSE;
                default -> node();
            };
            var save = new Save(auth.playerId(), owner, UUID.randomUUID().toString(), BOOT, mode,
                items.stream().map(item -> item.reward().id()).toList());
            Map<Long, String> frozen = Map.of();
            for (var item : items) {
                if (item.frozenPayload() == null) continue;
                if (frozen.isEmpty()) frozen = new LinkedHashMap<>();
                frozen.put(item.reward().id(), item.frozenPayload());
            }
            staged = store.reserve(save, claim, claimedAt, frozen);
            if (!staged) return;
            idleUntil = 0;
            Runnable action = mode.routed() ? () -> dispatchRouted(save, items.get(0).reward().sourceModule(),
                items.stream().map(Prepared::stack).toList())
                : () -> apply(player, auth, save, items);
            scheduled = queueApplication(save, action);
            if (!scheduled && !mode.routed()) store.finish(save, Resolution.UNAPPLIED);
        } catch (IOException | SQLException | RuntimeException error) {
            logger.log(Level.WARNING, "待发物品存档暂存未确认: player=" + auth.playerId(), error);
        } finally {
            if (!staged) release.accept(items.stream().map(Prepared::reward).toList());
            if (!scheduled) staging.release();
        }
    }

    private boolean queueApplication(Save save, Runnable action) {
        if (closed) return false;
        var application = new Application(save, action);
        if (!applications.offer(application)) return false;
        if (closed && applications.remove(application)) return false;
        localStages.add(save);
        scheduleApplications();
        return true;
    }

    private AxsMailService availableMail() {
        AxsMailService mail = mailProvider.get();
        return mail != null && (!sharedHostStorage || mail.usesSharedHostStorage()) ? mail : null;
    }

    private WarehouseRewardDepositable availableWarehouse() {
        var warehouse = warehouseProvider.get();
        return warehouse != null && (!sharedHostStorage || warehouse.usesSharedHostStorage()) ? warehouse : null;
    }

    private void dispatchRouted(Save save, String source, List<ItemStack> items) {
        if (items.isEmpty() || items.stream().anyMatch(InventoryItems::empty)) {
            waitingForEvidence.add(save);
        } else if (save.mode() == Mode.WAREHOUSE) {
            sendWarehouse(save, items);
        } else {
            sendMail(save, source, items);
        }
    }

    private void sendWarehouse(Save save, List<ItemStack> items) {
        try {
            var warehouse = availableWarehouse();
            if (warehouse == null) {
                waitingForEvidence.add(save);
                return;
            }
            warehouse.depositReward(UUID.fromString(save.receipt()), save.player(), items).whenComplete((result, error) -> {
                if (error == null && result == WarehouseRewardDepositable.Result.STORED) {
                    completions.put(save, Resolution.STORED);
                    requestScan();
                } else if (error == null && result == WarehouseRewardDepositable.Result.DECLINED) {
                    warehouseDeclines.add(save);
                    requestScan();
                } else {
                    if (error != null) logger.log(Level.FINE, "溢出入库暂未确认: receipt=" + save.receipt(), error);
                    waitingForEvidence.add(save);
                }
            });
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "溢出入库暂未确认: receipt=" + save.receipt(), failure);
            waitingForEvidence.add(save);
        }
    }

    private void sendMail(Save save, String source, List<ItemStack> items) {
        try {
            AxsMailService mail = availableMail();
            if (mail == null) {
                waitingForEvidence.add(save);
                return;
            }
            String title = switch (source) {
                case "fishing" -> "钓鱼物品溢出";
                case "entitytracker" -> "Boss 奖励溢出";
                case "afkreward" -> "挂机奖励溢出";
                case "lottery" -> "抽奖奖励溢出";
                default -> "红包物品溢出";
            };
            String sender = switch (source) {
                case "fishing" -> "钓鱼";
                case "entitytracker" -> "Boss 追踪";
                case "afkreward" -> "挂机奖励";
                case "lottery" -> "抽奖";
                default -> "聊天红包";
            };
            String body = switch (source) {
                case "fishing" -> "您的背包空间不足，钓鱼物品通过邮件发放。";
                case "entitytracker" -> "您的背包空间不足，Boss 奖励通过邮件发放。";
                case "afkreward" -> "您的背包空间不足，挂机奖励物品通过邮件发放。";
                case "lottery" -> "您的背包空间不足，抽奖奖励物品通过邮件发放。";
                default -> "您的背包空间不足，红包物品通过邮件发放。";
            };
            var request = AxsMailRequest.builder("reward-overflow:" + save.receipt(), title)
                .recipient(save.player())
                .content(body)
                .senderName(sender)
                .sourcePlugin(source)
                .items(items)
                .build();
            mail.sendDurable(request).whenComplete((receipt, error) -> {
                if (error == null && receipt != null && receipt.mailId() > 0) {
                    completions.put(save, Resolution.STORED);
                    requestScan();
                } else {
                    if (error != null) logger.log(Level.FINE, "溢出邮件暂未确认: receipt=" + save.receipt(), error);
                    waitingForEvidence.add(save);
                }
            });
        } catch (RuntimeException error) {
            logger.log(Level.WARNING, "溢出邮件暂未确认: receipt=" + save.receipt(), error);
            waitingForEvidence.add(save);
        }
    }

    private void recoverRouted(Save save, long now) throws SQLException {
        if (!staging.tryAcquire()) return;
        boolean queued = false;
        try {
            if (!store.claimRouted(save, now)) return;
            var rewards = store.loadRoutedRewards(save);
            queued = queueApplication(save, () -> {
                try {
                    dispatchRouted(save, rewards.get(0).sourceModule(),
                        rewards.stream().map(reward -> resolveItem.apply(null, reward)).toList());
                } catch (RuntimeException error) {
                    logger.log(Level.WARNING, "溢出物品暂不可用: receipt=" + save.receipt(), error);
                    waitingForEvidence.add(save);
                }
            });
        } finally {
            if (!queued) staging.release();
        }
    }

    private void scheduleApplications() {
        if (closed || !applicationScheduled.compareAndSet(false, true)) return;
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::applyNext, 1L);
        } catch (RuntimeException rejected) {
            applicationScheduled.set(false);
            if (!closed) logger.log(Level.WARNING, "待发物品等待主线程交付", rejected);
        }
    }

    private void applyNext() {
        applicationScheduled.set(false);
        if (closed) return;
        var next = applications.poll();
        if (next != null) next.apply().run();
        if (!applications.isEmpty()) scheduleApplications();
    }

    private void apply(Player player, AuthSnapshot auth, Save save, List<Prepared> items) {
        Resolution resolution = Resolution.UNAPPLIED;
        try {
            var current = AuthAccess.snapshot(save.player());
            if (!current.admitted() || current.generation() != auth.generation()
                || !Objects.equals(current.connectionId(), auth.connectionId())
                || Bukkit.getPlayer(save.player()) != player || !player.isOnline()) {
                return;
            }
            var inventory = player.getInventory();
            var original = InventoryItems.copy(inventory.getStorageContents());
            var next = InventoryItems.copy(original);
            for (var item : items) {
                if (!InventoryItems.put(next, item.stack())) {
                    deferForSpace(save.player());
                    return;
                }
            }
            var pdc = player.getPersistentDataContainer();
            String previous = pdc.get(RECEIPT, PersistentDataType.STRING);
            resolution = Resolution.REVIEW;
            boolean keepInventory = false;
            try {
                inventory.setStorageContents(next);
                pdc.set(RECEIPT, PersistentDataType.STRING, save.receipt());
                if (save.mode() == Mode.NATURAL) {
                    keepInventory = true;
                    resolution = null;
                    return;
                }
                player.saveData();
                Path file = playerFile(save.player());
                keepInventory = evidence(file, version(file), save) == SaveEvidence.STORED;
                if (keepInventory) resolution = Resolution.STORED;
            } finally {
                if (!keepInventory) {
                    inventory.setStorageContents(original);
                    if (previous == null) pdc.remove(RECEIPT);
                    else pdc.set(RECEIPT, PersistentDataType.STRING, previous);
                }
            }
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.WARNING, "待发物品交付待核对: receipt=" + save.receipt(), failure);
        } finally {
            if (resolution == null) waitingForEvidence.add(save);
            else completions.put(save, resolution);
            requestScan();
        }
    }

    private void persistCompletions() throws SQLException, IOException {
        for (Save save; (save = waitingForEvidence.poll()) != null; ) {
            localStages.remove(save);
            staging.release();
        }
        for (Save save; (save = warehouseDeclines.peek()) != null; ) {
            if (availableMail() != null) {
                if (store.routeWarehouseToMail(save, sharedHostStorage ? PendingItemSaveStore.SHARED_MAIL : node())) idleUntil = 0;
            } else if (store.finish(save, Resolution.UNAPPLIED)) {
                retryAfterWarehouseDecline(save.player());
            }
            warehouseDeclines.remove();
            localStages.remove(save);
            staging.release();
        }
        for (var entry : completions.entrySet()) {
            Save save = entry.getKey();
            Resolution result = entry.getValue();
            boolean changed = store.finish(save, result);
            completions.remove(save);
            localStages.remove(save);
            staging.release();
            if (changed && result == Resolution.STORED) {
                retryOnline(save.player());
            } else if (changed && result == Resolution.REVIEW) {
                logReview(save);
            }
        }
    }

    private void retryAfterWarehouseDecline(UUID id) {
        if (closed) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (closed || player == null || !player.isOnline()) return;
            if (player.getInventory().firstEmpty() >= 0) retry.accept(player);
            else deferForSpace(id);
        });
    }

    private void retryOnline(UUID player) {
        if (closed) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(player);
            if (!closed && online != null && online.isOnline()) retry.accept(online);
        });
    }

    private void logReview(Save save) {
        logger.warning("待发物品需人工核对: player=" + save.player() + " receipt=" + save.receipt()
            + " rewards=" + save.rewards());
    }

    private void deferForSpace(UUID player) {
        if (waitingInventory.size() >= MAX_WAITING_INVENTORY) waitingInventory.remove(waitingInventory.iterator().next());
        waitingInventory.add(player);
    }

    void inventoryChanged(Player player) {
        if (!closed && waitingInventory.remove(player.getUniqueId())) retry.accept(player);
    }

    void quit(UUID player) {
        waitingInventory.remove(player);
    }

    private void requestScan() {
        if (!applications.isEmpty()) scheduleApplications();
        if (closed || !scanQueued.compareAndSet(false, true)) return;
        if (!submit(() -> {
            try {
                persistCompletions();
                scan();
            }
            catch (IOException | SQLException | RuntimeException failure) {
                logger.log(Level.FINE, "读取待发物品存档凭证失败", failure);
            } finally { scanQueued.set(false); }
        })) scanQueued.set(false);
    }

    private void scan() throws IOException, SQLException {
        if (closed || idleUntil != 0 && System.nanoTime() - idleUntil < 0) return;
        long now = System.currentTimeMillis();
        boolean mailAvailable = availableMail() != null;
        boolean warehouseAvailable = availableWarehouse() != null;
        var rows = store.load(node(), sharedHostStorage && mailAvailable, sharedHostStorage && warehouseAvailable, cursor, PAGE, now);
        if (rows.isEmpty() && cursor.isEmpty()) idleUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        cursor = rows.size() == PAGE ? rows.get(rows.size() - 1).player().toString() : "";
        for (Save save : rows) {
            if (closed) break;
            // Never acknowledge a file while the primary-thread write may still roll back.
            if (localStages.contains(save)) continue;
            try {
                if (save.mode().routed()) {
                    if (save.mode() == Mode.MAIL ? mailAvailable : warehouseAvailable) recoverRouted(save, now);
                    continue;
                }
                var evidence = inspect(save);
                if (evidence != SaveEvidence.WAIT && store.finish(save, evidence.resolution)) {
                    checked.remove(save.player());
                    if (evidence == SaveEvidence.REVIEW) logReview(save);
                    else retryOnline(save.player());
                }
            } catch (IOException | RuntimeException failure) {
                logger.log(Level.FINE, "待发物品存档核对暂缓: receipt=" + save.receipt(), failure);
            }
        }
    }

    private SaveEvidence inspect(Save save) throws IOException {
        Path file = playerFile(save.player());
        FileVersion before = version(file);
        if (before.equals(checked.get(save.player()))) return SaveEvidence.WAIT;
        SaveEvidence evidence = evidence(file, before, save);
        if (evidence == SaveEvidence.WAIT) {
            if (checked.size() >= 128) checked.remove(checked.keySet().iterator().next());
            checked.put(save.player(), before);
        }
        return evidence;
    }

    private Path playerFile(UUID player) {
        return playerData.resolve(player + ".dat");
    }

    enum SaveEvidence {
        STORED(Resolution.STORED), UNAPPLIED(Resolution.UNAPPLIED), REVIEW(Resolution.REVIEW), WAIT(null);

        private final Resolution resolution;

        SaveEvidence(Resolution resolution) {
            this.resolution = resolution;
        }
    }

    static SaveEvidence evidence(Path file, FileVersion before, Save save) throws IOException {
        String marker = PlayerDataReceipt.readExisting(file, RECEIPT.toString());
        if (!before.equals(version(file))) return SaveEvidence.WAIT;
        if (save.receipt().equals(marker)) return SaveEvidence.STORED;
        if (save.boot() == BOOT) return SaveEvidence.WAIT;
        // Natural-save items could have left the inventory before the file was written.
        return save.mode() == Mode.CHECKPOINT ? SaveEvidence.UNAPPLIED : SaveEvidence.REVIEW;
    }

    static FileVersion version(Path file) throws IOException {
        var attributes = Files.readAttributes(file, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) throw new IOException("玩家存档不是普通文件");
        return new FileVersion(attributes.lastModifiedTime(), attributes.size(), attributes.fileKey());
    }

    record FileVersion(FileTime modified, long size, Object key) {}

    private String node() throws IOException {
        if (node != null) return node;
        try {
            Files.writeString(installation, UUID.randomUUID().toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        } catch (FileAlreadyExistsException ignored) {
            // Keep the stable owner of inventories saved on this installation.
        }
        return node = UUID.fromString(Files.readString(installation, StandardCharsets.UTF_8).trim()).toString();
    }

    private boolean submit(Runnable operation) {
        if (closed) return false;
        try { io.execute(operation); return true; }
        catch (RejectedExecutionException unavailable) { return false; }
    }

    java.util.concurrent.CompletionStage<Void> shutdown() {
        closed = true;
        timer.cancel();
        waitingInventory.clear();
        for (Application job; (job = applications.poll()) != null; ) {
            Save save = job.save();
            if (save.mode().routed()) continue;
            try {
                io.execute(() -> {
                    try { store.finish(save, Resolution.UNAPPLIED); }
                    catch (SQLException failure) {
                        logger.log(Level.WARNING, "停服前未能释放待发物品暂存: receipt=" + save.receipt(), failure);
                    }
                });
            } catch (RejectedExecutionException unavailable) {
                // A queued application that never ran is also recoverable from the previous boot's file.
            }
        }
        try {
            io.execute(() -> {
                try { persistCompletions(); }
                catch (IOException | SQLException failure) {
                    logger.log(Level.WARNING, "停服前待发物品回执未全部提交", failure);
                }
            });
        } catch (RejectedExecutionException unavailable) {
            // The source player's file still covers any confirmed inventory write.
        }
        io.shutdown();
        return io.stopped();
    }

    private record Application(Save save, Runnable apply) {}
    private record Prepared(PendingReward reward, ItemStack stack, String frozenPayload) {}
}
