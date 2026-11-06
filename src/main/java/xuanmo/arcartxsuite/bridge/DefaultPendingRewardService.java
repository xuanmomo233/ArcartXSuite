package xuanmo.arcartxsuite.bridge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemRewardDispatcher;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;

/**
 * 统一待发放奖励服务的宿主默认实现。
 * <p>
 * 使用单张数据库表 {@code axs_pending_rewards} 存储所有模块的待发放奖励，
 * payload 字段以 JSON 格式存储奖励详情。
 * <p>
 * 玩家上线时自动加载并补发：物品通过 ItemRewardDispatcher、货币通过 CurrencyBridge、
 * 命令通过控制台执行、邮件预设通过 MailDispatchable。
 * <p>
 * 补发失败时递增尝试次数，达到 {@link #MAX_ATTEMPTS} 后自动丢弃避免无限重试。
 * <p>
 * 补发物品时设置 {@code skipPendingOverflow(true)}，避免背包满+邮件不可用时
 * 物品再次存入 pending 形成循环。
 */
public final class DefaultPendingRewardService implements PendingRewardService, Listener {

    /** 最大补发尝试次数，超过后自动丢弃 */
    private static final int MAX_ATTEMPTS = 10;

    /** 认领 TTL：节点崩溃后其 claimed 行在该时长后被其他节点回收补发 */
    private static final long CLAIM_TTL_MS = 600_000L;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final StorageManager storageManager;
    private final ModuleRegistry registry;

    /**
     * 本节点认领标识（claimed_by 写入值）。
     * 默认为启动随机 UUID；宿主在跨服服务就绪后经 {@link #setClaimOwner} 注入 nodeId。
     */
    private volatile String claimOwner = UUID.randomUUID().toString();
    private final java.nio.file.Path outbox;
    private final Object outboxLock=new Object();
    private record RewardBatch(String key,String fingerprint,List<PendingReward> rewards) { }

    public DefaultPendingRewardService(JavaPlugin plugin, Logger logger,
                                       StorageManager storageManager, ModuleRegistry registry) {
        this.plugin = plugin;
        this.logger = logger;
        this.storageManager = storageManager;
        this.registry = registry;
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
    public void initialize() {
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
                tryExecute(stmt, "CREATE INDEX IF NOT EXISTS idx_pending_player ON axs_pending_rewards(player_uuid)");
                tryExecute(stmt, "CREATE INDEX IF NOT EXISTS idx_pending_module_player ON axs_pending_rewards(source_module, player_uuid)");
            }
            // 认领列：老表无此列则 ALTER 补齐（重复执行被 tryExecute 吞掉）
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS axs_reward_receipts ("
                + "request_key VARCHAR(128) PRIMARY KEY, fingerprint VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL)");
            tryExecute(stmt, "ALTER TABLE axs_pending_rewards ADD COLUMN claimed_at " + (isMysql ? "BIGINT" : "INTEGER"));
            tryExecute(stmt, "ALTER TABLE axs_pending_rewards ADD COLUMN claimed_by " + (isMysql ? "VARCHAR(64)" : "TEXT"));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "初始化 axs_pending_rewards 表失败", e);
        }
    }

    private static void tryExecute(Statement stmt, String sql) {
        try { stmt.executeUpdate(sql); } catch (SQLException ignored) {}
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
                 "UPDATE axs_pending_rewards SET attempts = attempts + 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "递增补发尝试次数失败: id=" + id, e);
        }
    }

    // ─── 上线自动补发 ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAdmission(xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent event) {
        Player player = Bukkit.getPlayer(event.snapshot().playerId());
        if (player != null) flushPlayer(player);
    }

    @Override
    public void flushPlayer(Player player) {
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) return;
        UUID uuid = player.getUniqueId();
        var admission=xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(uuid);
        // 延迟 40 tick 补发，确保玩家数据加载完成
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(uuid)) return;
            // 异步认领+加载 pending 数据，避免主线程 DB 查询；
            // 原子认领保证多节点部署下同一条目只被一个节点补发
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                importOutbox(uuid);
                ClaimedBatch batch = claimAndLoadPending(uuid);
                if (batch.rewards().isEmpty()) return;
                // 物品发放等操作需在主线程执行
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(uuid)) {
                        // 玩家在主线程切换前离线：释放认领，等待下次上线重试
                        releaseClaimsAsync(batch);
                        return;
                    }
                    flushPending(player, batch, admission);
                });
            });
        }, 40L);
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
        if(allowOutbox && outbox!=null) {
            var existing=outbox.resolve(player.toString()).resolve(key+".json");
            if(java.nio.file.Files.exists(existing))try {
                RewardBatch accepted=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(existing,java.nio.charset.StandardCharsets.UTF_8),RewardBatch.class);
                if(!fingerprint.equals(accepted.fingerprint()))return false;
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
            return allowOutbox && writeOutbox(new RewardBatch(requestKey,fingerprint,List.copyOf(rewards)),key);
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
                if(java.nio.file.Files.exists(target))return batch.fingerprint().equals(gson.fromJson(
                    java.nio.file.Files.readString(target,java.nio.charset.StandardCharsets.UTF_8),RewardBatch.class).fingerprint());
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
        if(outbox==null)return;
        synchronized(outboxLock) {
            var folder=outbox.resolve(player.toString());
            if(!java.nio.file.Files.isDirectory(folder))return;
            try(var files=java.nio.file.Files.list(folder)) {
                for(var file:files.filter(p->p.getFileName().toString().endsWith(".json")).toList())try {
                    RewardBatch batch=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8),RewardBatch.class);
                    if(batch.rewards().isEmpty() || !batch.rewards().get(0).playerUuid().equals(player))continue;
                    if(saveConfirmed(batch.key(),batch.rewards(),false))java.nio.file.Files.delete(file);
                } catch(Exception invalid){logger.log(Level.WARNING,"宿主奖励恢复日志尚未导入: "+file.getFileName(),invalid);}
            } catch(java.io.IOException failure){logger.log(Level.WARNING,"读取宿主奖励恢复日志失败",failure);}
        }
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
    private record ClaimedBatch(String owner,long stamp,List<PendingReward> rewards,java.util.Set<Long> preparedCommands) {
        ClaimedBatch subset(List<PendingReward> remaining) { return new ClaimedBatch(owner,stamp,List.copyOf(remaining),preparedCommands); }
    }
    private ClaimedBatch claimAndLoadPending(@NotNull UUID playerUuid) {
        long stamp=System.currentTimeMillis();
        String owner=UUID.randomUUID().toString();
        try(Connection conn=storageManager.getHostConnection()) {
            boolean auto=conn.getAutoCommit(); conn.setAutoCommit(false);
            try {
                try(PreparedStatement s=conn.prepareStatement(
                    "UPDATE axs_pending_rewards SET claimed_at=?,claimed_by=? WHERE player_uuid=?"
                    +" AND (claimed_at IS NULL OR claimed_at<?) AND (reward_type<>'COMMAND' OR attempts=0)")) {
                    s.setLong(1,stamp); s.setString(2,owner); s.setString(3,playerUuid.toString()); s.setLong(4,stamp-CLAIM_TTL_MS);
                    s.executeUpdate();
                }
                List<PendingReward> rewards=new ArrayList<>();
                try(PreparedStatement s=conn.prepareStatement(
                    "SELECT * FROM axs_pending_rewards WHERE player_uuid=? AND claimed_by=? AND claimed_at=? ORDER BY created_at ASC")) {
                    s.setString(1,playerUuid.toString()); s.setString(2,owner); s.setLong(3,stamp);
                    try(ResultSet r=s.executeQuery()) {
                        while(r.next()) rewards.add(new PendingReward(r.getLong("id"),UUID.fromString(r.getString("player_uuid")),
                            r.getString("source_module"),PendingReward.Type.valueOf(r.getString("reward_type")),r.getString("payload"),
                            r.getString("description"),r.getLong("created_at"),r.getInt("attempts")));
                    }
                }
                java.util.Set<Long> prepared=new java.util.HashSet<>();
                try(PreparedStatement s=conn.prepareStatement(
                    "UPDATE axs_pending_rewards SET attempts=1 WHERE id=? AND claimed_by=? AND claimed_at=? AND attempts=0 AND reward_type='COMMAND'")) {
                    for(PendingReward reward:rewards) {
                        if(reward.type()!=PendingReward.Type.COMMAND) continue;
                        s.setLong(1,reward.id()); s.setString(2,owner); s.setLong(3,stamp);
                        if(s.executeUpdate()==1) prepared.add(reward.id());
                    }
                }
                conn.commit();
                return new ClaimedBatch(owner,stamp,List.copyOf(rewards),java.util.Set.copyOf(prepared));
            } catch(SQLException e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(auto); }
        } catch(SQLException e) {
            logger.log(Level.WARNING,"认领奖励失败: "+playerUuid,e);
            return new ClaimedBatch(owner,stamp,List.of(),java.util.Set.of());
        }
    }

    /** 释放一组奖励的认领（玩家离线/不补发时调用，使其可被后续认领）。 */
    private void releaseClaimsAsync(ClaimedBatch batch) {
        if(batch.rewards().isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->{
            try(Connection c=storageManager.getHostConnection(); PreparedStatement s=c.prepareStatement(
                "UPDATE axs_pending_rewards SET claimed_at=NULL,claimed_by=NULL,"
                +"attempts=CASE WHEN reward_type='COMMAND' THEN 0 ELSE attempts END WHERE id=? AND claimed_by=? AND claimed_at=?")) {
                for(PendingReward reward:batch.rewards()) {
                    s.setLong(1,reward.id()); s.setString(2,batch.owner()); s.setLong(3,batch.stamp()); s.addBatch();
                }
                s.executeBatch();
            } catch(SQLException e) { logger.log(Level.WARNING,"释放奖励认领失败",e); }
        });
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
    private void flushPending(Player player,ClaimedBatch batch,xuanmo.arcartxsuite.api.auth.AuthSnapshot admission) {
        List<Long> success=new ArrayList<>(),failed=new ArrayList<>();
        List<PendingReward> rewards=batch.rewards();
        for(int index=0;index<rewards.size();index++) {
            var current=xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(player.getUniqueId());
            long age=System.currentTimeMillis()-batch.stamp();
            if(!player.isOnline() || !current.admitted() || !java.util.Objects.equals(current.connectionId(),admission.connectionId())
                    || current.generation()!=admission.generation() || age<0 || age>=CLAIM_TTL_MS) {
                releaseClaimsAsync(batch.subset(rewards.subList(index,rewards.size())));
                break;
            }
            PendingReward reward=rewards.get(index);
            if(reward.type()==PendingReward.Type.COMMAND && !batch.preparedCommands().contains(reward.id())) continue;
            try {
                if(dispatchPending(player,reward)) success.add(reward.id()); else failed.add(reward.id());
            } catch(Exception e) {
                failed.add(reward.id());
                logger.log(Level.WARNING,"奖励发放结果不明: id="+reward.id(),e);
            }
            if(failed.contains(reward.id()) && reward.type()==PendingReward.Type.COMMAND)
                logger.warning("命令奖励保留待核对，禁止自动重放: id="+reward.id());
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin,()->batchUpdate(batch,success,failed));
    }

    /**
     * 批量执行删除和递增操作，在单个事务中提交以减少 DB 往返。
     */
    private void batchUpdate(ClaimedBatch batch,List<Long> success,List<Long> failed) {
        if(success.isEmpty() && failed.isEmpty()) return;
        try(Connection c=storageManager.getHostConnection()) {
            boolean auto=c.getAutoCommit(); c.setAutoCommit(false);
            try {
                try(PreparedStatement s=c.prepareStatement("DELETE FROM axs_pending_rewards WHERE id=? AND claimed_by=? AND claimed_at=?")) {
                    for(Long id:success) { s.setLong(1,id); s.setString(2,batch.owner()); s.setLong(3,batch.stamp()); s.addBatch(); }
                    s.executeBatch();
                }
                try(PreparedStatement s=c.prepareStatement("UPDATE axs_pending_rewards SET attempts=attempts+1,claimed_at=NULL,claimed_by=NULL"
                    +" WHERE id=? AND claimed_by=? AND claimed_at=?")) {
                    for(Long id:failed) { s.setLong(1,id); s.setString(2,batch.owner()); s.setLong(3,batch.stamp()); s.addBatch(); }
                    s.executeBatch();
                }
                c.commit();
            } catch(SQLException e) { c.rollback(); throw e; }
            finally { c.setAutoCommit(auto); }
        } catch(SQLException e) { logger.log(Level.WARNING,"奖励结果回写失败；命令项保持待核对",e); }
    }

    /**
     * 根据奖励类型分发补发。
     *
     * @return true 表示补发成功
     */
    private boolean dispatchPending(Player player, PendingReward pending) {
        return switch (pending.type()) {
            case ITEM -> dispatchItem(player, pending);
            case ITEM_SERIALIZED -> dispatchItemSerialized(player, pending);
            case CURRENCY -> dispatchCurrency(player, pending);
            case COMMAND -> dispatchCommand(player, pending);
            case MAIL_PRESET -> dispatchMailPreset(player, pending);
        };
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
        String base64Data = extractJsonField(pending.payload(), "data");
        if (base64Data == null || base64Data.isBlank()) return false;
        try {
            org.bukkit.inventory.ItemStack item = xuanmo.arcartxsuite.api.util.ItemSerializer.deserialize(
                java.util.Base64.getDecoder().decode(base64Data));
            if (item == null) return false;
            ItemRewardDispatcher.DispatchResult result = dispatcher.dispatchItem(player, item, buildPendingDispatchOptions(pending));
            return result.success();
        } catch (Exception e) {
            logger.warning("反序列化待发放物品失败: id=" + pending.id() + " | " + e.getMessage());
            return false;
        }
    }

    private boolean dispatchItem(Player player, PendingReward pending) {
        ItemSourceRegistry registry = this.registry.itemSourceRegistry();
        ItemRewardDispatcher dispatcher = this.registry.itemRewardDispatcher();
        if (registry == null || dispatcher == null) return false;
        // 解析 JSON payload
        String source = extractJsonField(pending.payload(), "source");
        String id = extractJsonField(pending.payload(), "id");
        int amount = extractJsonInt(pending.payload(), "amount", 1);
        String nbt = extractJsonField(pending.payload(), "nbt");
        if (source == null || id == null) return false;
        org.bukkit.inventory.ItemStack item = registry.generateItem(source, id, amount,
            (nbt != null && !nbt.isEmpty()) ? nbt : null, player);
        if (item == null) return false;
        ItemRewardDispatcher.DispatchResult result = dispatcher.dispatchItem(player, item, buildPendingDispatchOptions(pending));
        return result.success();
    }

    private boolean dispatchCurrency(Player player, PendingReward pending) {
        CurrencyBridgeAPI currencyManager = registry.currencyManager();
        if (currencyManager == null) return false;
        String currencyId = extractJsonField(pending.payload(), "currencyId");
        double amount = extractJsonDouble(pending.payload(), "amount", 0);
        if (currencyId == null || amount <= 0) return false;
        var bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) return false;
        var result = bridge.deposit(player, java.math.BigDecimal.valueOf(amount));
        return result.success();
    }

    private boolean dispatchCommand(Player player, PendingReward pending) {
        List<String> commands = extractJsonArray(pending.payload(), "commands");
        if (commands.isEmpty()) return false;
        for (String cmd : commands) {
            String parsed = cmd.replace("{player}", player.getName());
            // strip 前导 / ，Bukkit.dispatchCommand 期望不带 / 的命令
            if (parsed.startsWith("/")) parsed = parsed.substring(1);
            ThrottledCommandExecutor.dispatch( parsed);
        }
        return true;
    }

    private boolean dispatchMailPreset(Player player, PendingReward pending) {
        List<String> presetIds = extractJsonArray(pending.payload(), "presetIds");
        if (presetIds.isEmpty()) return false;
        return xuanmo.arcartxsuite.api.capability.mail.MailPresetHelper.dispatchPresets(
            () -> registry.getCapability(MailDispatchable.class),
            player.getName(),
            presetIds,
            pending.sourceModule(),
            "pending:" + pending.id(),
            logger
        );
    }

    // ─── JSON payload 简易解析 ──────────────────────────────────
    // payload 由 PendingReward record 的便捷构造生成，格式固定，
    // 这里用简易正则解析避免引入完整 JSON 库依赖。
    // Pattern 缓存为 static final，避免每次调用重新编译。

    private static final java.util.regex.Pattern ITEM_STR_PATTERN =
        java.util.regex.Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

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
        List<String> result = new ArrayList<>();
        // 用平衡的引号感知解析，避免 ] 出现在字符串内部时被误判为数组结束
        String fieldKey = "\"" + java.util.regex.Pattern.quote(field) + "\"";
        int keyIdx = json.indexOf(fieldKey);
        if (keyIdx < 0) return result;
        int colonIdx = json.indexOf(':', keyIdx + fieldKey.length());
        if (colonIdx < 0) return result;
        int bracketStart = json.indexOf('[', colonIdx);
        if (bracketStart < 0) return result;
        // 逐字符扫描，跟踪引号内的内容，找到匹配的 ]
        int depth = 0;
        int i = bracketStart;
        boolean inString = false;
        int arrayEnd = -1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') { i += 2; continue; }
                if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '[') depth++;
                else if (c == ']') { depth--; if (depth == 0) { arrayEnd = i; break; } }
            }
            i++;
        }
        if (arrayEnd < 0) return result;
        String arrayContent = json.substring(bracketStart + 1, arrayEnd);
        java.util.regex.Matcher itemMatcher = ITEM_STR_PATTERN.matcher(arrayContent);
        while (itemMatcher.find()) {
            result.add(unescapeJson(itemMatcher.group(1)));
        }
        return result;
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
