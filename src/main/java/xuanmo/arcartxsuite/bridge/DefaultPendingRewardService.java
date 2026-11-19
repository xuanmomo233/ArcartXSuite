package xuanmo.arcartxsuite.bridge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;
import xuanmo.arcartxsuite.api.command.CommandSanitizer;

/**
 * 统一待发放奖励服务的宿主默认实现。
 * <p>
 * 使用单张数据库表 {@code axs_pending_rewards} 存储所有模块的待发放奖励，
 * payload 字段以 JSON 格式存储奖励详情。
 * <p>
 * 玩家上线时自动加载并补发：物品通过 ItemRewardDispatcher、货币通过 CurrencyBridge、
 * 命令通过控制台执行、邮件预设通过 Mail 的持久投递接口。
 * <p>
 * 明确失败的奖励可再次认领。战令和命令奖励结果不明时保留记录供核对。
 * <p>
 * 补发物品时设置 {@code skipPendingOverflow(true)}，避免背包满+邮件不可用时
 * 物品再次存入 pending 形成循环。
 */
public final class DefaultPendingRewardService implements PendingRewardService, Listener {

    /** 认领 TTL：节点崩溃后其 claimed 行在该时长后被其他节点回收补发 */
    private static final long CLAIM_TTL_MS = 600_000L;
    private static final int CLAIM_PAGE_SIZE = 32;
    private static final String CLAIMABLE = "player_uuid=? AND (claimed_at IS NULL OR claimed_at<?)"
        + " AND (reward_type<>'COMMAND' OR attempts=0) AND attempts>=0";
    private static final String CLAIM_PAGE = "UPDATE axs_pending_rewards SET claimed_at=?,claimed_by=? WHERE "
        + CLAIMABLE + " AND id IN (SELECT id FROM (SELECT id FROM axs_pending_rewards WHERE " + CLAIMABLE
        + " AND (created_at>? OR (created_at=? AND id>?)) ORDER BY created_at,id LIMIT ?) AS candidates)";
    // 通用奖励开始执行后，只有明确失败才允许自动重试。
    private static final int STARTED_UNCONFIRMED = -1;
    // Real request keys are hex digests. This reserved key identifies their shared receipt store.
    private static final String STORE_IDENTITY_KEY = "receipt-store:v1";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final StorageManager storageManager;
    private final ModuleRegistry registry;
    private final PendingItemSaveStore itemSaveStore;
    private final PendingCommandReceipts commandReceipts;
    private final ConcurrentHashMap<UUID, Flush> flushes = new ConcurrentHashMap<>();
    private PendingItemDelivery pendingItems;

    /**
     * 本节点认领标识（claimed_by 写入值）。
     * 默认为启动随机 UUID；宿主在跨服服务就绪后经 {@link #setClaimOwner} 注入 nodeId。
     */
    private volatile String claimOwner = UUID.randomUUID().toString();
    private final java.nio.file.Path outbox;
    private final Object outboxLock=new Object();
    private final AtomicBoolean outboxStoreWarning = new AtomicBoolean();
    private volatile boolean schemaInitialized;
    private volatile UUID rewardReceiptStoreId;
    private record RewardBatch(String key, String fingerprint, List<PendingReward> rewards, UUID receiptStoreId) { }

    public DefaultPendingRewardService(JavaPlugin plugin, Logger logger,
                                       StorageManager storageManager, ModuleRegistry registry) {
        this(plugin, logger, storageManager, registry, new PendingCommandReceipts(storageManager, logger));
    }

    public DefaultPendingRewardService(JavaPlugin plugin, Logger logger, StorageManager storageManager,
                                       ModuleRegistry registry, PendingCommandReceipts commandReceipts) {
        this.plugin = plugin;
        this.logger = logger;
        this.storageManager = storageManager;
        this.registry = registry;
        this.commandReceipts = commandReceipts;
        this.itemSaveStore = new PendingItemSaveStore(storageManager);
        this.outbox=plugin!=null?plugin.getDataFolder().toPath().resolve("pending-reward-outbox"):null;
    }

    /** 设置认领标识（通常为跨服 nodeId；截断至 64 字符）。 */
    public void setClaimOwner(@NotNull String owner) {
        String trimmed = owner.trim();
        this.claimOwner = trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    /**
     * 初始化数据库表。
     * <p>
     * 区分 SQLite/MySQL 方言：SQLite 使用 AUTOINCREMENT，MySQL 使用 AUTO_INCREMENT。
     * SQLite 不支持内联 INDEX 语法，索引单独创建。
     */
    public synchronized void initialize() {
        if (schemaInitialized) return;
        StorageDescriptor descriptor = storageManager.getDescriptor();
        boolean isMysql = descriptor != null && descriptor.isMysql();
        try (Connection conn = storageManager.getHostConnection();
             Statement stmt = conn.createStatement()) {
            if (isMysql) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS axs_pending_rewards ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "player_uuid VARCHAR(36) NOT NULL, "
                    + "source_module VARCHAR(64) NOT NULL, "
                    + "reward_type VARCHAR(16) NOT NULL, "
                    + "payload TEXT NOT NULL, "
                    + "description VARCHAR(255), "
                    + "created_at BIGINT NOT NULL, "
                    + "attempts INT NOT NULL DEFAULT 0, "
                    + "INDEX idx_player (player_uuid), "
                    + "INDEX idx_pending_player_chrono (player_uuid, created_at, id), "
                    + "INDEX idx_module_player (source_module, player_uuid)"
                    + ")"
                );
            } else {
                // SQLite: AUTOINCREMENT 关键字，不支持内联 INDEX
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS axs_pending_rewards ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "player_uuid TEXT NOT NULL, "
                    + "source_module TEXT NOT NULL, "
                    + "reward_type TEXT NOT NULL, "
                    + "payload TEXT NOT NULL, "
                    + "description TEXT, "
                    + "created_at INTEGER NOT NULL, "
                    + "attempts INTEGER NOT NULL DEFAULT 0"
                    + ")"
                );
                // SQLite 索引单独创建（IF NOT EXISTS 避免重复创建报错）
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_player ON axs_pending_rewards(player_uuid)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_module_player ON axs_pending_rewards(source_module, player_uuid)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pending_player_chrono ON axs_pending_rewards(player_uuid, created_at, id)");
            }
            if (isMysql) ensureClaimIndex(stmt);
            // 认领列：老表无此列则 ALTER 补齐（重复执行被 tryExecute 吞掉）
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS axs_reward_receipts ("
                + "request_key VARCHAR(128) PRIMARY KEY, fingerprint VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL)");
            tryExecute(stmt, "ALTER TABLE axs_pending_rewards ADD COLUMN claimed_at " + (isMysql ? "BIGINT" : "INTEGER"));
            tryExecute(stmt, "ALTER TABLE axs_pending_rewards ADD COLUMN claimed_by " + (isMysql ? "VARCHAR(64)" : "TEXT"));
            // Only cache success after ignored duplicate-column errors have been distinguished
            // from an incomplete upgrade caused by a transient storage failure.
            try (ResultSet ignored = stmt.executeQuery("SELECT claimed_at, claimed_by FROM axs_pending_rewards WHERE 1=0")) {
                // Probe the existing columns before creating the additive item checkpoint table.
            }
            itemSaveStore.initialize(stmt, isMysql);
            rewardReceiptStoreId = identifyReceiptStore(conn, isMysql);
            schemaInitialized = true;
        } catch (SQLException | IllegalArgumentException e) {
            logger.log(Level.SEVERE, "初始化 axs_pending_rewards 表失败", e);
        }
    }

    private static UUID identifyReceiptStore(Connection conn, boolean mysql) throws SQLException {
        String insert = mysql
            ? "INSERT IGNORE INTO axs_reward_receipts (request_key,fingerprint,created_at) VALUES (?,?,?)"
            : "INSERT OR IGNORE INTO axs_reward_receipts (request_key,fingerprint,created_at) VALUES (?,?,?)";
        try (var statement = conn.prepareStatement(insert)) {
            statement.setString(1, STORE_IDENTITY_KEY);
            statement.setString(2, UUID.randomUUID().toString());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
        try (var statement = conn.prepareStatement("SELECT fingerprint FROM axs_reward_receipts WHERE request_key=?")) {
            statement.setString(1, STORE_IDENTITY_KEY);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("待发放奖励存储标识不可用");
                return UUID.fromString(rows.getString(1));
            }
        }
    }

    private static void tryExecute(Statement stmt, String sql) {
        try { stmt.executeUpdate(sql); } catch (SQLException ignored) {}
    }

    private static void ensureClaimIndex(Statement stmt) throws SQLException {
        try (var rows = stmt.executeQuery("SHOW INDEX FROM axs_pending_rewards WHERE Key_name='idx_pending_player_chrono'")) {
            if (rows.next()) return;
        }
        try {
            stmt.executeUpdate("CREATE INDEX idx_pending_player_chrono ON axs_pending_rewards(player_uuid, created_at, id)");
        } catch (SQLException failure) {
            // Another node may have upgraded the shared table after the preceding check.
            if (failure.getErrorCode() != 1061) throw failure;
        }
    }

    public boolean storageReady() { return schemaInitialized; }

    @Override
    public @Nullable UUID rewardReceiptStoreId() {
        return rewardReceiptStoreId;
    }

    public void startPendingItemDelivery() {
        var worlds = Bukkit.getWorlds();
        if (pendingItems != null || worlds.isEmpty() || !schemaInitialized) return;
        var playerData = worlds.get(0).getWorldFolder().toPath().resolve("playerdata");
        pendingItems = new PendingItemDelivery(plugin, logger, playerData, itemSaveStore, this::flushPlayer,
            () -> registry.getCapability(xuanmo.arcartxsuite.api.mail.AxsMailService.class),
            () -> registry.getCapability(xuanmo.arcartxsuite.api.capability.WarehouseRewardDepositable.class),
            (player, reward) -> switch (reward.type()) {
                case ITEM_SERIALIZED, ITEM_NATURAL -> readSerializedItem(reward, true);
                case ITEM_SPEC -> resolveItemSpec(player, readItemSpec(reward));
                default -> generatePendingItem(player, reward);
            });
    }

    public java.util.concurrent.CompletionStage<Void> stopPendingItemDelivery() {
        var active = pendingItems;
        pendingItems = null;
        return active == null ? java.util.concurrent.CompletableFuture.completedFuture(null) : active.shutdown();
    }

    // ─── PendingRewardService 接口实现 ──────────────────────────

    @Override
    public void savePending(@NotNull PendingReward reward) {
        try (Connection conn = storageManager.getHostConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO axs_pending_rewards "
                 + "(player_uuid, source_module, reward_type, payload, description, created_at, attempts) "
                 + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, reward.playerUuid().toString());
            ps.setString(2, reward.sourceModule());
            ps.setString(3, reward.type().name());
            ps.setString(4, reward.payload());
            ps.setString(5, reward.description() != null ? reward.description() : "");
            ps.setLong(6, reward.createdAt());
            ps.setInt(7, reward.attempts());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "保存待发放奖励失败: player=" + reward.playerUuid()
                + " module=" + reward.sourceModule(), e);
        }
    }

    @Override
    public @NotNull List<PendingReward> loadPending(@NotNull UUID playerUuid) {
        return loadPendingByCondition(
            "SELECT * FROM axs_pending_rewards WHERE player_uuid = ? ORDER BY created_at ASC",
            playerUuid.toString()
        );
    }

    @Override
    public @NotNull List<PendingReward> loadPending(@NotNull String sourceModule, @NotNull UUID playerUuid) {
        return loadPendingByCondition(
            "SELECT * FROM axs_pending_rewards WHERE source_module = ? AND player_uuid = ? ORDER BY created_at ASC",
            sourceModule, playerUuid.toString()
        );
    }

    @Override
    public void markDelivered(long id) {
        try (Connection conn = storageManager.getHostConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM axs_pending_rewards WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "删除待发放奖励失败: id=" + id, e);
        }
    }

    @Override
    public void incrementAttempts(long id) {
        try (Connection conn = storageManager.getHostConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE axs_pending_rewards SET attempts = attempts + 1 WHERE id = ? AND attempts >= 0")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "递增补发尝试次数失败: id=" + id, e);
        }
    }

    // ─── 上线自动补发 ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAdmission(xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent event) {
        if (!schemaInitialized) return;
        Player player = Bukkit.getPlayer(event.snapshot().playerId());
        if (player != null) flushPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (pendingItems != null && event.getPlayer() instanceof Player player) pendingItems.inventoryChanged(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (pendingItems != null) pendingItems.inventoryChanged(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (pendingItems != null) pendingItems.inventoryChanged(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFishingRewardQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        flushes.computeIfPresent(player.getUniqueId(), (id, flush) -> flush.player == player ? null : flush);
        if (pendingItems != null) pendingItems.quit(event.getPlayer().getUniqueId());
    }

    @Override
    public void flushPlayer(Player player) {
        UUID id = player.getUniqueId();
        var admission = AuthAccess.snapshot(id);
        if (!admission.admitted()) return;
        Flush request = new Flush(player, admission);
        var selected = flushes.compute(id, (ignored, current) -> {
            if (current != null && current.player == player && sameConnection(current.admission, admission)) {
                if (current.started) current.recheck = true;
                return current;
            }
            return request;
        });
        if (selected == request) schedulePage(request, null, 40L);
    }

    private static final class Flush {
        private final Player player;
        private final AuthSnapshot admission;
        // Mutated only in a flushes.compute operation for this player's UUID.
        private boolean started;
        private boolean recheck;

        private Flush(Player player, AuthSnapshot admission) {
            this.player = player;
            this.admission = admission;
        }
    }

    private void schedulePage(Flush request, ClaimCursor cursor, long delay) {
        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> claimPage(request, cursor), delay);
        } catch (RuntimeException failure) {
            flushes.remove(request.admission.playerId(), request);
            logger.log(Level.FINE, "待发奖励调度未受理", failure);
        }
    }

    private void claimPage(Flush request, ClaimCursor cursor) {
        UUID id = request.admission.playerId();
        if (!current(request)) {
            flushes.remove(id, request);
            return;
        }
        var selected = flushes.computeIfPresent(id, (ignored, active) -> {
            if (active == request) request.started = true;
            return active;
        });
        if (selected != request) return;
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> loadPage(request, cursor));
        } catch (RuntimeException failure) {
            flushes.remove(id, request);
            logger.log(Level.FINE, "待发奖励认领任务未受理", failure);
        }
    }

    private void loadPage(Flush request, ClaimCursor cursor) {
        UUID id = request.admission.playerId();
        if (flushes.get(id) != request) return;
        if (cursor == null) importOutbox(id);
        ClaimedBatch batch = cursor == null ? claimAndLoadPending(id) : claimAndLoadPending(id, cursor);
        if (batch.rewards().isEmpty()) {
            finishFlush(request);
        } else if (flushes.get(id) != request) {
            releaseClaims(batch);
        } else {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> deliverPage(request, batch));
            } catch (RuntimeException failure) {
                flushes.remove(id, request);
                releaseClaims(batch);
                logger.log(Level.FINE, "待发奖励交付任务未受理", failure);
            }
        }
    }

    private void deliverPage(Flush request, ClaimedBatch batch) {
        if (!current(request)) {
            flushes.remove(request.admission.playerId(), request);
            releaseClaimsAsync(batch);
            return;
        }
        flushPending(request.player, batch, request.admission).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.WARNING, "待发奖励交付中断", failure);
                finishFlush(request);
                return;
            }
            if (current(request) && batch.rewards().size() == CLAIM_PAGE_SIZE) {
                var last = batch.rewards().get(CLAIM_PAGE_SIZE - 1);
                schedulePage(request, new ClaimCursor(last.createdAt(), last.id()), 1L);
            } else {
                finishFlush(request);
            }
        });
    }

    private boolean current(Flush request) {
        UUID id = request.admission.playerId();
        return flushes.get(id) == request && request.player.isOnline()
            && sameConnection(request.admission, AuthAccess.snapshot(id));
    }

    private void finishFlush(Flush request) {
        var next = flushes.computeIfPresent(request.admission.playerId(), (ignored, active) -> {
            if (active != request) return active;
            if (!request.recheck) return null;
            request.started = false;
            request.recheck = false;
            return request;
        });
        if (next == request) schedulePage(request, null, 1L);
    }

    private static boolean sameConnection(AuthSnapshot first, AuthSnapshot current) {
        return current.admitted() && Objects.equals(first.connectionId(), current.connectionId())
            && first.generation() == current.generation();
    }

    @Override
    public boolean savePendingBatchConfirmed(String requestKey, List<PendingReward> rewards) {
        synchronized(outboxLock) { return saveConfirmed(requestKey,rewards,true); }
    }

    private boolean saveConfirmed(String requestKey,List<PendingReward> rewards,boolean allowOutbox) {
        if (requestKey == null || requestKey.isBlank() || rewards == null) return false;
        if (rewards.isEmpty()) return true;
        String scope = rewards.get(0).sourceModule();
        UUID player = rewards.get(0).playerUuid();
        if (rewards.stream().anyMatch(r -> !scope.equals(r.sourceModule()) || !player.equals(r.playerUuid())))
            throw new IllegalArgumentException("A reward batch must have one source and recipient");
        String key = digest(scope + ":" + requestKey);
        String fingerprint = digest(rewards.stream().map(r -> r.playerUuid() + ":" + r.type() + ":" + r.payload())
            .collect(java.util.stream.Collectors.joining("\n")));
        UUID storeId = rewardReceiptStoreId;
        if(allowOutbox && outbox!=null) {
            var existing=outbox.resolve(player.toString()).resolve(key+".json");
            if(java.nio.file.Files.exists(existing))try {
                RewardBatch accepted=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(existing,java.nio.charset.StandardCharsets.UTF_8),RewardBatch.class);
                if (!ownsOutbox(accepted, storeId, existing) || !fingerprint.equals(accepted.fingerprint())) return false;
            } catch(Exception invalid){logger.log(Level.SEVERE,"奖励恢复日志无法核对，拒绝覆盖",invalid);return false;}
        }
        boolean mysql = storageManager.getDescriptor() != null && storageManager.getDescriptor().isMysql();
        try (Connection conn = storageManager.getHostConnection()) {
            boolean previous = conn.getAutoCommit(); conn.setAutoCommit(false);
            try {
                int inserted;
                String sql = mysql
                    ? "INSERT IGNORE INTO axs_reward_receipts (request_key,fingerprint,created_at) VALUES (?,?,?)"
                    : "INSERT INTO axs_reward_receipts (request_key,fingerprint,created_at) VALUES (?,?,?) ON CONFLICT(request_key) DO NOTHING";
                try (PreparedStatement receipt = conn.prepareStatement(sql)) {
                    receipt.setString(1, key); receipt.setString(2, fingerprint); receipt.setLong(3, System.currentTimeMillis());
                    inserted = receipt.executeUpdate();
                }
                if (inserted == 0) {
                    try (PreparedStatement find = conn.prepareStatement("SELECT fingerprint FROM axs_reward_receipts WHERE request_key=?")) {
                        find.setString(1, key);
                        try (ResultSet rs = find.executeQuery()) {
                            boolean same = rs.next() && fingerprint.equals(rs.getString(1));
                            conn.rollback(); return same;
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO axs_pending_rewards "
                    + "(player_uuid,source_module,reward_type,payload,description,created_at,attempts) VALUES (?,?,?,?,?,?,?)")) {
                    for (PendingReward r : rewards) {
                        ps.setString(1, r.playerUuid().toString()); ps.setString(2, r.sourceModule());
                        ps.setString(3, r.type().name()); ps.setString(4, r.payload()); ps.setString(5, r.description());
                        ps.setLong(6, r.createdAt()); ps.setInt(7, r.attempts()); ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit(); return true;
            } catch (Exception error) { conn.rollback(); throw error; }
            finally { conn.setAutoCommit(previous); }
        } catch (Exception error) {
            logger.log(Level.WARNING, "确认奖励批次持久化失败: module=" + scope + " player=" + player, error);
            return allowOutbox && storeId != null
                && writeOutbox(new RewardBatch(requestKey, fingerprint, List.copyOf(rewards), storeId), key);
        }
    }

    private boolean writeOutbox(RewardBatch batch,String key) {
        if(outbox==null)return false;
        synchronized(outboxLock) {
            java.nio.file.Path temp=null;
            try {
                var folder=outbox.resolve(batch.rewards().get(0).playerUuid().toString());
                java.nio.file.Files.createDirectories(folder);
                var target=folder.resolve(key+".json");
                var gson=new com.google.gson.Gson();
                if (java.nio.file.Files.exists(target)) {
                    var existing = gson.fromJson(java.nio.file.Files.readString(target, java.nio.charset.StandardCharsets.UTF_8),
                        RewardBatch.class);
                    return ownsOutbox(existing, batch.receiptStoreId(), target)
                        && batch.fingerprint().equals(existing.fingerprint());
                }
                temp=java.nio.file.Files.createTempFile(folder,".pending-",".tmp");
                java.nio.file.Files.writeString(temp,gson.toJson(batch),java.nio.charset.StandardCharsets.UTF_8);
                try(var channel=java.nio.channels.FileChannel.open(temp,java.nio.file.StandardOpenOption.WRITE)){channel.force(true);}
                try {java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
                catch(java.nio.file.AtomicMoveNotSupportedException unsupported){java.nio.file.Files.move(temp,target);}
                logger.warning("奖励已保存到宿主恢复日志，等待数据库恢复: "+key);
                return true;
            } catch(Exception error) {
                logger.log(Level.SEVERE,"宿主奖励恢复日志写入失败，调用方保留领取资格",error);return false;
            } finally {if(temp!=null)try{java.nio.file.Files.deleteIfExists(temp);}catch(java.io.IOException ignored){}}
        }
    }

    private void importOutbox(UUID player) {
        UUID storeId = rewardReceiptStoreId;
        if (outbox == null || storeId == null) return;
        synchronized(outboxLock) {
            var folder=outbox.resolve(player.toString());
            if(!java.nio.file.Files.isDirectory(folder))return;
            try(var files=java.nio.file.Files.list(folder)) {
                for(var file:files.filter(p->p.getFileName().toString().endsWith(".json")).toList())try {
                    RewardBatch batch=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8),RewardBatch.class);
                    if(batch.rewards().isEmpty() || !batch.rewards().get(0).playerUuid().equals(player))continue;
                    if (!ownsOutbox(batch, storeId, file)) continue;
                    if(saveConfirmed(batch.key(),batch.rewards(),false))java.nio.file.Files.delete(file);
                } catch(Exception invalid){logger.log(Level.WARNING,"宿主奖励恢复日志尚未导入: "+file.getFileName(),invalid);}
            } catch(java.io.IOException failure){logger.log(Level.WARNING,"读取宿主奖励恢复日志失败",failure);}
        }
    }

    private boolean ownsOutbox(RewardBatch batch, UUID storeId, java.nio.file.Path file) {
        if (storeId != null && storeId.equals(batch.receiptStoreId())) return true;
        if (outboxStoreWarning.compareAndSet(false, true)) {
            logger.warning("宿主奖励恢复日志的原奖励库无法确认，保留待核对: " + file);
        }
        return false;
    }

    private static String digest(String text) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * 原子认领并加载玩家的待发放奖励。
     * <p>
     * 先用单条 UPDATE 抢认领（仅未认领或认领已过 TTL 的行可被抢到），
     * 再 SELECT 本次认领的行。并发节点间靠 claimed_at 的 WHERE 条件天然互斥：
     * 后到者的 UPDATE 不影响已被认领且未过期的行。
     * 节点崩溃时其 claimed 行在 {@link #CLAIM_TTL_MS} 后被其他节点回收。
     */
    private record ClaimedBatch(String owner,long stamp,List<PendingReward> rewards) {
        ClaimedBatch subset(List<PendingReward> remaining) { return new ClaimedBatch(owner,stamp,List.copyOf(remaining)); }
    }
    private record ClaimCursor(long createdAt, long id) {}

    private ClaimedBatch claimAndLoadPending(@NotNull UUID playerUuid) {
        return claimAndLoadPending(playerUuid, new ClaimCursor(Long.MIN_VALUE, 0L));
    }

    private ClaimedBatch claimAndLoadPending(UUID playerUuid, ClaimCursor cursor) {
        long stamp=System.currentTimeMillis();
        String owner=UUID.randomUUID().toString();
        try(Connection conn=storageManager.getHostConnection()) {
            boolean auto=conn.getAutoCommit(); conn.setAutoCommit(false);
            try {
                String player = playerUuid.toString();
                long expiry = stamp - CLAIM_TTL_MS;
                int claimed;
                try (PreparedStatement s = conn.prepareStatement(CLAIM_PAGE)) {
                    s.setLong(1, stamp);
                    s.setString(2, owner);
                    s.setString(3, player);
                    s.setLong(4, expiry);
                    s.setString(5, player);
                    s.setLong(6, expiry);
                    s.setLong(7, cursor.createdAt());
                    s.setLong(8, cursor.createdAt());
                    s.setLong(9, cursor.id());
                    s.setInt(10, CLAIM_PAGE_SIZE);
                    claimed = s.executeUpdate();
                }
                if (claimed == 0) {
                    conn.commit();
                    return new ClaimedBatch(owner, stamp, List.of());
                }
                List<PendingReward> rewards=new ArrayList<>(claimed);
                try(PreparedStatement s=conn.prepareStatement(
                    "SELECT * FROM axs_pending_rewards WHERE player_uuid=? AND claimed_by=? AND claimed_at=? ORDER BY created_at,id")) {
                    s.setString(1,player); s.setString(2,owner); s.setLong(3,stamp);
                    try(ResultSet r=s.executeQuery()) {
                        while(r.next()) rewards.add(new PendingReward(r.getLong("id"),UUID.fromString(r.getString("player_uuid")),
                            r.getString("source_module"),PendingReward.Type.valueOf(r.getString("reward_type")),r.getString("payload"),
                            r.getString("description"),r.getLong("created_at"),r.getInt("attempts")));
                    }
                }
                if (rewards.size() != claimed) throw new SQLException("待发奖励页与认领行数不一致");
                conn.commit();
                return new ClaimedBatch(owner,stamp,List.copyOf(rewards));
            } catch (SQLException | RuntimeException failure) {
                try { conn.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
            finally { conn.setAutoCommit(auto); }
        } catch (SQLException | RuntimeException e) {
            logger.log(Level.WARNING,"认领奖励失败: "+playerUuid,e);
            return new ClaimedBatch(owner,stamp,List.of());
        }
    }

    private boolean beginDispatch(ClaimedBatch batch, PendingReward reward) throws SQLException {
        long age = System.currentTimeMillis() - batch.stamp();
        if (age < 0 || age >= CLAIM_TTL_MS) return false;
        try (Connection conn = storageManager.getHostConnection(); PreparedStatement statement = conn.prepareStatement("""
                UPDATE axs_pending_rewards SET attempts=?
                WHERE id=? AND claimed_by=? AND claimed_at=? AND attempts=?
                """)) {
            statement.setInt(1, reward.type() == PendingReward.Type.COMMAND ? 1 : STARTED_UNCONFIRMED);
            statement.setLong(2, reward.id());
            statement.setString(3, batch.owner());
            statement.setLong(4, batch.stamp());
            statement.setInt(5, reward.attempts());
            return statement.executeUpdate() == 1;
        }
    }

    /** 释放一组奖励的认领（玩家离线/不补发时调用，使其可被后续认领）。 */
    private void releaseClaimsAsync(ClaimedBatch batch) {
        if (!batch.rewards().isEmpty()) Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> releaseClaims(batch));
    }

    private void releaseClaims(ClaimedBatch batch) {
        try (Connection c = storageManager.getHostConnection(); PreparedStatement s = c.prepareStatement(
            "UPDATE axs_pending_rewards SET claimed_at=NULL,claimed_by=NULL,"
                + "attempts=? WHERE id=? AND claimed_by=? AND claimed_at=? AND attempts<>-2")) {
            for (PendingReward reward : batch.rewards()) {
                s.setInt(1, reward.attempts()); s.setLong(2, reward.id());
                s.setString(3, batch.owner()); s.setLong(4, batch.stamp()); s.addBatch();
            }
            s.executeBatch();
        } catch (SQLException e) { logger.log(Level.WARNING, "释放奖励认领失败", e); }
    }

    /**
     * 加载并补发指定玩家的所有待发放奖励。
     * <p>
     * 补发结果（markDelivered / incrementAttempts）在单个事务中批量提交，减少 DB 往返。
     * <p>
     * 注意：此方法在主线程调用，pendingList 已在异步线程加载完毕。
     *
     * @param player      在线玩家
     * @param pendingList 已加载的待发放奖励列表
     */
    private CompletionStage<Void> flushPending(Player player, ClaimedBatch batch, AuthSnapshot admission) {
        var done = new CompletableFuture<Void>();
        if (!canDeliver(player, batch, admission)) {
            releaseClaimsAsync(batch);
            done.complete(null);
            return done;
        }
        List<PendingReward> itemBatch = new ArrayList<>();
        List<PendingReward> deferredItems = new ArrayList<>();
        List<PendingReward> generic = new ArrayList<>();
        for (PendingReward reward : batch.rewards()) {
            if (PendingItemSaveStore.tracks(reward)) {
                if (itemBatch.size() < PendingItemDelivery.MAX_ITEMS && (itemBatch.isEmpty()
                        || itemBatch.get(0).sourceModule().equals(reward.sourceModule())
                            && PendingItemSaveStore.requiresCheckpoint(itemBatch.get(0)) == PendingItemSaveStore.requiresCheckpoint(reward))) {
                    itemBatch.add(reward);
                } else deferredItems.add(reward);
            } else {
                generic.add(reward);
            }
        }
        if (!deferredItems.isEmpty()) releaseClaimsAsync(batch.subset(deferredItems));
        if (!itemBatch.isEmpty()) {
            if (pendingItems == null) releaseClaimsAsync(batch.subset(itemBatch));
            else pendingItems.stage(player, admission, batch.owner(), batch.stamp(), itemBatch,
                released -> releaseClaimsAsync(batch.subset(released)));
        }
        dispatchNext(player, batch.subset(generic), admission, 0, done);
        return done;
    }

    private void dispatchNext(Player player, ClaimedBatch batch, AuthSnapshot admission, int index,
                              CompletableFuture<Void> done) {
        try {
            var rewards = batch.rewards();
            for (; index < rewards.size(); index++) {
                if (!canDeliver(player, batch, admission)) {
                    releaseClaimsAsync(batch.subset(rewards.subList(index, rewards.size())));
                    break;
                }
                var reward = rewards.get(index);
                if (reward.type() == PendingReward.Type.MAIL_PRESET) {
                    // Durable mail can retry under its original key after the claim expires.
                    dispatchOne(player, batch, admission, reward);
                    continue;
                }
                int next = index + 1;
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean armed = false;
                    try {
                        armed = beginDispatch(batch, reward);
                        final boolean mayRun = armed;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                if (mayRun && canDeliver(player, batch, admission)) {
                                    if (reward.type() == PendingReward.Type.COMMAND) {
                                        // Never arm the next command reward while this one is queued or executing.
                                        dispatchCommand(player, reward, batch, admission).whenComplete((unused, failure) -> {
                                            if (failure != null) logger.log(Level.WARNING, "命令奖励回执未确认: id=" + reward.id(), failure);
                                            dispatchNext(player, batch, admission, next, done);
                                        });
                                        return;
                                    }
                                    dispatchOne(player, batch, admission, reward);
                                } else if (mayRun) {
                                    releaseClaimsAsync(batch.subset(List.of(reward)));
                                }
                            } catch (RuntimeException | LinkageError failure) {
                                logger.log(Level.WARNING, "奖励交付未确认: id=" + reward.id(), failure);
                            }
                            dispatchNext(player, batch, admission, next, done);
                        });
                    } catch (SQLException | RuntimeException | LinkageError failure) {
                        if (armed) releaseClaims(batch.subset(List.of(reward)));
                        done.completeExceptionally(failure);
                    }
                });
                return;
            }
            done.complete(null);
        } catch (RuntimeException | LinkageError failure) {
            done.completeExceptionally(failure);
        }
    }

    private void dispatchOne(Player player, ClaimedBatch batch, AuthSnapshot admission, PendingReward reward) {
        try {
            switch (dispatchPending(player, reward, batch, admission)) {
                case DELIVERED -> Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> batchUpdate(batch, List.of(reward.id()), List.of()));
                case RETRY -> Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> batchUpdate(batch, List.of(), List.of(reward)));
                case DEFERRED -> { }
            }
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "奖励发放结果不明: id=" + reward.id(), failure);
        }
    }

    private static boolean canDeliver(Player player, ClaimedBatch batch, AuthSnapshot admission) {
        if (!player.isOnline()) return false;
        var current = AuthAccess.snapshot(admission.playerId());
        long age = System.currentTimeMillis() - batch.stamp();
        return sameConnection(admission, current) && age >= 0 && age < CLAIM_TTL_MS;
    }

    /**
     * 批量执行删除和递增操作，在单个事务中提交以减少 DB 往返。
     */
    private void batchUpdate(ClaimedBatch batch,List<Long> success,List<PendingReward> failed) {
        if(success.isEmpty() && failed.isEmpty()) return;
        try(Connection c=storageManager.getHostConnection()) {
            boolean auto=c.getAutoCommit(); c.setAutoCommit(false);
            try {
                try(PreparedStatement s=c.prepareStatement("DELETE FROM axs_pending_rewards WHERE id=? AND claimed_by=? AND claimed_at=?")) {
                    for(Long id:success) { s.setLong(1,id); s.setString(2,batch.owner()); s.setLong(3,batch.stamp()); s.addBatch(); }
                    s.executeBatch();
                }
                try(PreparedStatement s=c.prepareStatement("UPDATE axs_pending_rewards SET attempts=?,claimed_at=NULL,claimed_by=NULL"
                    +" WHERE id=? AND claimed_by=? AND claimed_at=?")) {
                    for (PendingReward reward : failed) {
                        s.setInt(1, reward.attempts() + 1);
                        s.setLong(2, reward.id()); s.setString(3, batch.owner()); s.setLong(4, batch.stamp()); s.addBatch();
                    }
                    s.executeBatch();
                }
                c.commit();
            } catch(SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(auto); }
        } catch(SQLException e) { logger.log(Level.WARNING,"奖励结果回写失败，已开始的奖励保留待核对",e); }
    }

    private enum Delivery {
        DELIVERED, RETRY, DEFERRED;

        static Delivery from(boolean success) { return success ? DELIVERED : RETRY; }
    }

    private Delivery dispatchPending(Player player, PendingReward pending, ClaimedBatch batch, AuthSnapshot admission) {
        return switch (pending.type()) {
            case ITEM -> Delivery.from(dispatchItem(player, pending));
            case ITEM_SERIALIZED -> Delivery.from(dispatchItemSerialized(player, pending));
            case ITEM_NATURAL -> Delivery.RETRY;
            case ITEM_SPEC -> dispatchItemSpec(player, pending, batch);
            case CURRENCY -> Delivery.from(dispatchCurrency(player, pending));
            case COMMAND -> throw new IllegalStateException("命令奖励需要等待命令回执");
            case TITLE -> dispatchTitle(player, pending, batch);
            case MAP_ANCHOR -> dispatchAnchor(player, pending, batch);
            case MAIL_PRESET -> dispatchMailPreset(batch, pending);
        };
    }

    private Delivery dispatchTitle(Player player, PendingReward pending, ClaimedBatch batch) {
        var title = registry.getCapability(xuanmo.arcartxsuite.api.capability.TitleGrantable.class);
        String target = extractJsonField(pending.payload(), "targetId");
        if (title == null || target == null || target.isBlank()) return Delivery.RETRY;
        return awaitGrant(batch, pending, title.grantPermanent(player.getUniqueId(), target, pending.sourceModule()));
    }

    private Delivery dispatchAnchor(Player player, PendingReward pending, ClaimedBatch batch) {
        var map = registry.getCapability(xuanmo.arcartxsuite.api.capability.MapAnchorUnlockable.class);
        String target = extractJsonField(pending.payload(), "targetId");
        if (map == null || target == null || target.isBlank()) return Delivery.RETRY;
        return awaitGrant(batch, pending, map.ensureUnlocked(player, target));
    }

    private Delivery awaitGrant(ClaimedBatch batch, PendingReward pending,
                                java.util.concurrent.CompletionStage<Boolean> grant) {
        grant.whenComplete((accepted, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "联动奖励发放结果不明: id=" + pending.id(), error);
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (Boolean.TRUE.equals(accepted)) batchUpdate(batch, List.of(pending.id()), List.of());
                    else batchUpdate(batch, List.of(), List.of(pending));
                });
            }
        });
        return Delivery.DEFERRED;
    }

    private Delivery awaitMail(ClaimedBatch batch, PendingReward pending, CompletionStage<Boolean> delivery) {
        delivery.whenComplete((delivered, failure) -> {
            if (failure != null) {
                logger.log(Level.WARNING, "奖励邮件结果不明: id=" + pending.id(), failure);
                return;
            }
            try {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (Boolean.TRUE.equals(delivered)) batchUpdate(batch, List.of(pending.id()), List.of());
                    else batchUpdate(batch, List.of(), List.of(pending));
                });
            } catch (RuntimeException | LinkageError error) {
                logger.log(Level.WARNING, "奖励邮件回执未保存: id=" + pending.id(), error);
            }
        });
        return Delivery.DEFERRED;
    }

    /**
     * 构建补发用的 DispatchOptions。
     * <p>
     * 关键：设置 skipPendingOverflow(true)，避免补发物品时背包满+邮件不可用
     * 再次存入 pending 形成循环。补发失败的物品直接掉落地面。
     */
    private ItemRewardDispatcher.DispatchOptions buildPendingDispatchOptions(PendingReward pending) {
        return ItemRewardDispatcher.DispatchOptions.builder(pending.sourceModule())
            .sourceDetail("pending:" + pending.id())
            .overflowMailSubject("待发放奖励溢出物品")
            .overflowMailBody("您的背包已满，待发放奖励物品通过邮件补发。")
            .overflowMailSenderName(pending.sourceModule())
            .skipPendingOverflow(true)  // 避免补发循环
            .build();
    }

    private boolean dispatchItemSerialized(Player player, PendingReward pending) {
        ItemRewardDispatcher dispatcher = registry.itemRewardDispatcher();
        if (dispatcher == null) return false;
        ItemStack item = readSerializedItem(pending, false);
        return item != null && confirmItemDispatch(dispatcher.dispatchItem(player, item, buildPendingDispatchOptions(pending)));
    }

    private @Nullable ItemStack readSerializedItem(PendingReward pending, boolean strict) {
        String base64Data = extractJsonField(pending.payload(), "data");
        if (base64Data == null || base64Data.isBlank()) return null;
        try {
            byte[] serialized = java.util.Base64.getDecoder().decode(base64Data);
            return strict ? xuanmo.arcartxsuite.api.util.ItemSerializer.deserializeStrict(serialized) :
                xuanmo.arcartxsuite.api.util.ItemSerializer.deserialize(serialized);
        } catch (Exception e) {
            logger.warning("反序列化待发放物品失败: id=" + pending.id() + " | " + e.getMessage());
            return null;
        }
    }

    private Delivery dispatchItemSpec(Player player, PendingReward pending, ClaimedBatch batch) {
        var spec = readItemSpec(pending);
        var item = resolveItemSpec(player, spec);
        if (item == null) return Delivery.RETRY;
        var mail = spec.mailSubject() == null ? null : registry.getCapability(xuanmo.arcartxsuite.api.mail.AxsMailService.class);
        if (mail != null) {
            if (!durableMailOnSameStore(mail)) return Delivery.RETRY;
            var request = xuanmo.arcartxsuite.api.mail.AxsMailRequest
                .builder(pendingMailKey(pending) + ":item")
                .recipient(player.getUniqueId())
                .title(spec.mailSubject())
                .content(spec.mailBody() == null ? "" : spec.mailBody())
                .item(item)
                .senderName("战令")
                .sourcePlugin(pending.sourceModule())
                .build();
            return awaitMail(batch, pending, mail.sendDurable(request).thenApply(receipt -> receipt.mailId() > 0));
        }
        ItemRewardDispatcher dispatcher = registry.itemRewardDispatcher();
        if (dispatcher == null) return Delivery.RETRY;
        return Delivery.from(confirmItemDispatch(dispatcher.dispatchItem(player, item, buildPendingDispatchOptions(pending))));
    }

    private static @Nullable PendingReward.ItemSpec readItemSpec(PendingReward reward) {
        return new com.google.gson.Gson().fromJson(reward.payload(), PendingReward.ItemSpec.class);
    }

    private @Nullable ItemStack resolveItemSpec(@Nullable Player player, @Nullable PendingReward.ItemSpec spec) {
        if (player == null || spec == null || spec.item() == null) return null;
        var resolver = new xuanmo.arcartxsuite.api.icon.IconResolver(registry.itemStackBridge(), registry.itemSourceRegistry());
        return resolver.resolveItemStack(player, spec.item());
    }

    private boolean dispatchItem(Player player, PendingReward pending) {
        ItemRewardDispatcher dispatcher = registry.itemRewardDispatcher();
        if (dispatcher == null) return false;
        ItemStack item = generatePendingItem(player, pending);
        return item != null && confirmItemDispatch(dispatcher.dispatchItem(player, item, buildPendingDispatchOptions(pending)));
    }

    private @Nullable ItemStack generatePendingItem(Player player, PendingReward pending) {
        ItemSourceRegistry sources = registry.itemSourceRegistry();
        if (sources == null || player == null) return null;
        String source = extractJsonField(pending.payload(), "source");
        String id = extractJsonField(pending.payload(), "id");
        int amount = extractJsonInt(pending.payload(), "amount", 1);
        String nbt = extractJsonField(pending.payload(), "nbt");
        if (source == null || id == null) return null;
        return sources.generateItem(source, id, amount,
            (nbt != null && !nbt.isEmpty()) ? nbt : null, player);
    }

    private static boolean confirmItemDispatch(ItemRewardDispatcher.DispatchResult result) {
        if (!result.success() && (result.deliveredCount() > 0 || result.warehousedCount() > 0
                || result.mailedCount() > 0 || result.droppedCount() > 0 || result.pendingCount() > 0)) {
            throw new IllegalStateException("物品奖励已部分交付");
        }
        return result.success();
    }

    private boolean dispatchCurrency(Player player, PendingReward pending) {
        CurrencyBridgeAPI currencyManager = registry.currencyManager();
        if (currencyManager == null) return false;
        String currencyId = extractJsonField(pending.payload(), "currencyId");
        double amount = extractJsonDouble(pending.payload(), "amount", 0);
        if (currencyId == null || !Double.isFinite(amount) || amount <= 0) return false;
        var bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) return false;
        if ("fishing".equals(pending.sourceModule()) && bridge.creditRequiresReconciliation()) return false;
        var result = bridge.deposit(player, java.math.BigDecimal.valueOf(amount));
        if (result.uncertain() || result.success() && bridge.creditRequiresReconciliation()) {
            throw new IllegalStateException("货币奖励入账待核对");
        }
        return result.success();
    }

    private CompletionStage<Void> dispatchCommand(Player player, PendingReward pending, ClaimedBatch batch, AuthSnapshot admission) {
        List<String> templates = extractJsonArray(pending.payload(), "commands");
        if (templates.isEmpty()) {
            logger.warning("命令奖励缺少命令，保留待核对: id=" + pending.id());
            return CompletableFuture.completedFuture(null);
        }
        boolean battlePass = "battlepass".equals(pending.sourceModule());
        String name = player.getName();
        if (battlePass || "fishing".equals(pending.sourceModule())) name = CommandSanitizer.validatePlayerName(name);
        var commands = new ArrayList<String>(templates.size());
        for (String template : templates) {
            String parsed = template.replace("{player}", name).trim();
            // strip 前导 / ，Bukkit.dispatchCommand 期望不带 / 的命令
            if (parsed.startsWith("/")) parsed = parsed.substring(1);
            if (battlePass) parsed = parsed.replace("{uuid}", admission.playerId().toString());
            if (parsed.isBlank()) {
                logger.warning("命令奖励包含空命令，保留待核对: id=" + pending.id());
                return CompletableFuture.completedFuture(null);
            }
            commands.add(parsed);
        }

        BooleanSupplier allowed = () -> canDeliver(player, batch, admission);
        if (commands.size() == 1) {
            var completed = new CompletableFuture<Void>();
            ThrottledCommandExecutor.dispatchWithResult(commands.get(0), allowed, result -> {
                try {
                    finishCommandReward(pending, batch, 1, confirmedCount(result), result);
                } finally {
                    completed.complete(null);
                }
            });
            return completed;
        }
        CompletionStage<CommandProgress> chain = CompletableFuture.completedFuture(
            new CommandProgress(0, ThrottledCommandExecutor.Result.SUCCESS));
        for (String command : commands) {
            chain = chain.thenCompose(progress -> {
                if (progress.result() != ThrottledCommandExecutor.Result.SUCCESS) {
                    return CompletableFuture.completedFuture(progress);
                }
                var step = new CompletableFuture<CommandProgress>();
                ThrottledCommandExecutor.dispatchWithResult(command, allowed, result -> step.complete(
                    new CommandProgress(progress.confirmed() + confirmedCount(result), result)));
                return step;
            });
        }
        return chain.handle((progress, failure) -> {
            if (failure != null) {
                logger.log(Level.WARNING, "命令奖励结果不明，保留待核对: id=" + pending.id(), failure);
            } else {
                finishCommandReward(pending, batch, commands.size(), progress.confirmed(), progress.result());
            }
            return null;
        });
    }

    private record CommandProgress(int confirmed, ThrottledCommandExecutor.Result result) {}

    private static int confirmedCount(ThrottledCommandExecutor.Result result) {
        return result == ThrottledCommandExecutor.Result.SUCCESS ? 1 : 0;
    }

    private void finishCommandReward(PendingReward pending, ClaimedBatch batch, int total, int confirmed,
                                     ThrottledCommandExecutor.Result result) {
        boolean delivered = confirmed == total;
        boolean notRun = confirmed == 0 && result == ThrottledCommandExecutor.Result.NOT_EXECUTED;
        if (!delivered && !notRun) {
            logger.warning("命令奖励保留待核对: id=" + pending.id() + "，已确认 " + confirmed + "/" + total
                + "，末次结果 " + result);
        } else if (!commandReceipts.record(pending.id(), batch.owner(), batch.stamp(), delivered)) {
            logger.warning("命令奖励回执未入队，保留待核对: id=" + pending.id());
        }
    }

    private Delivery dispatchMailPreset(ClaimedBatch batch, PendingReward pending) {
        List<String> presetIds = extractJsonArray(pending.payload(), "presetIds");
        var mail = registry.getCapability(xuanmo.arcartxsuite.api.mail.AxsMailService.class);
        if (presetIds.isEmpty() || mail == null || !durableMailOnSameStore(mail)) return Delivery.RETRY;
        String key = pendingMailKey(pending);
        CompletableFuture<Boolean> delivery = CompletableFuture.completedFuture(true);
        for (int index = 0; index < presetIds.size(); index++) {
            String preset = presetIds.get(index);
            String presetKey = key + ":preset:" + index;
            delivery = delivery.thenCompose(allDelivered ->
                mail.sendPresetDurable(pending.playerUuid(), preset, pending.sourceModule(), presetKey)
                    .thenApply(receipt -> allDelivered && receipt.mailId() > 0));
        }
        return awaitMail(batch, pending, delivery);
    }

    private boolean durableMailOnSameStore(xuanmo.arcartxsuite.api.mail.AxsMailService mail) {
        StorageDescriptor descriptor = storageManager.getDescriptor();
        return descriptor == null || !descriptor.isMysql() || mail.usesSharedHostStorage();
    }

    private static String pendingMailKey(PendingReward pending) {
        return "pending:" + pending.playerUuid() + ":" + pending.createdAt() + ":" + pending.id();
    }

    // ─── JSON payload 读取 ─────────────────────────────────────

    // 缓存常用字段的 Pattern，避免每次调用重新编译
    private static final java.util.regex.Pattern FIELD_SOURCE =
        compileStringFieldPattern("source");
    private static final java.util.regex.Pattern FIELD_ID =
        compileStringFieldPattern("id");
    private static final java.util.regex.Pattern FIELD_NBT =
        compileStringFieldPattern("nbt");
    private static final java.util.regex.Pattern FIELD_DATA =
        compileStringFieldPattern("data");
    private static final java.util.regex.Pattern FIELD_CURRENCY_ID =
        compileStringFieldPattern("currencyId");

    private static java.util.regex.Pattern compileStringFieldPattern(String field) {
        return java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    }

    private static @Nullable String extractJsonField(@NotNull String json, @NotNull String field) {
        // 匹配 "field":"value"
        java.util.regex.Pattern pattern = switch (field) {
            case "source" -> FIELD_SOURCE;
            case "id" -> FIELD_ID;
            case "nbt" -> FIELD_NBT;
            case "data" -> FIELD_DATA;
            case "currencyId" -> FIELD_CURRENCY_ID;
            default -> compileStringFieldPattern(field);
        };
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    private static int extractJsonInt(@NotNull String json, @NotNull String field, int defaultValue) {
        // 支持负数：-?\d+
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static double extractJsonDouble(@NotNull String json, @NotNull String field, double defaultValue) {
        // 支持负数、科学计数法：-?[\d.]+(?:[eE][-+]?\d+)?
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*(-?[\\d.]+(?:[eE][-+]?\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static @NotNull List<String> extractJsonArray(@NotNull String json, @NotNull String field) {
        try {
            var root = com.google.gson.JsonParser.parseString(json);
            if (!root.isJsonObject()) return List.of();
            var value = root.getAsJsonObject().get(field);
            if (value == null || !value.isJsonArray()) return List.of();

            List<String> result = new ArrayList<>();
            for (var item : value.getAsJsonArray()) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                    result.add(item.getAsString());
                }
            }
            return result;
        } catch (com.google.gson.JsonParseException invalid) {
            return List.of();
        }
    }

    private static @NotNull String unescapeJson(@NotNull String value) {
        return value.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    }

    // ─── 内部查询 ──────────────────────────────────────────────

    private List<PendingReward> loadPendingByCondition(String sql, String... params) {
        List<PendingReward> result = new ArrayList<>();
        try (Connection conn = storageManager.getHostConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PendingReward(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("source_module"),
                        PendingReward.Type.valueOf(rs.getString("reward_type")),
                        rs.getString("payload"),
                        rs.getString("description"),
                        rs.getLong("created_at"),
                        rs.getInt("attempts")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "加载待发放奖励失败", e);
        }
        return result;
    }
}
