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

    public DefaultPendingRewardService(JavaPlugin plugin, Logger logger,
                                       StorageManager storageManager, ModuleRegistry registry) {
        this.plugin = plugin;
        this.logger = logger;
        this.storageManager = storageManager;
        this.registry = registry;
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
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // 延迟 40 tick 补发，确保玩家数据加载完成
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            // 异步认领+加载 pending 数据，避免主线程 DB 查询；
            // 原子认领保证多节点部署下同一条目只被一个节点补发
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<PendingReward> pendingList = claimAndLoadPending(uuid);
                if (pendingList.isEmpty()) return;
                // 物品发放等操作需在主线程执行
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        // 玩家在主线程切换前离线：释放认领，等待下次上线重试
                        releaseClaimsAsync(pendingList);
                        return;
                    }
                    flushPending(player, pendingList);
                });
            });
        }, 40L);
    }

    /**
     * 原子认领并加载玩家的待发放奖励。
     * <p>
     * 先用单条 UPDATE 抢认领（仅未认领或认领已过 TTL 的行可被抢到），
     * 再 SELECT 本次认领的行。并发节点间靠 claimed_at 的 WHERE 条件天然互斥：
     * 后到者的 UPDATE 不影响已被认领且未过期的行。
     * 节点崩溃时其 claimed 行在 {@link #CLAIM_TTL_MS} 后被其他节点回收。
     */
    private @NotNull List<PendingReward> claimAndLoadPending(@NotNull UUID playerUuid) {
        long claimStamp = System.currentTimeMillis();
        long expiredBefore = claimStamp - CLAIM_TTL_MS;
        String owner = claimOwner;
        try (Connection conn = storageManager.getHostConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE axs_pending_rewards SET claimed_at = ?, claimed_by = ? "
                + "WHERE player_uuid = ? AND (claimed_at IS NULL OR claimed_at < ?)")) {
                ps.setLong(1, claimStamp);
                ps.setString(2, owner);
                ps.setString(3, playerUuid.toString());
                ps.setLong(4, expiredBefore);
                ps.executeUpdate();
            }
            // 只取本次认领的行（claimed_by+claimed_at 精确匹配，不拿其他节点历史的行）
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM axs_pending_rewards WHERE player_uuid = ? AND claimed_by = ? AND claimed_at = ? "
                + "ORDER BY created_at ASC")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, owner);
                ps.setLong(3, claimStamp);
                try (ResultSet rs = ps.executeQuery()) {
                    List<PendingReward> result = new ArrayList<>();
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
                    return result;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "认领待发放奖励失败: player=" + playerUuid, e);
            return List.of();
        }
    }

    /** 释放一组奖励的认领（玩家离线/不补发时调用，使其可被后续认领）。 */
    private void releaseClaimsAsync(List<PendingReward> pendingList) {
        if (pendingList.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = storageManager.getHostConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE axs_pending_rewards SET claimed_at = NULL, claimed_by = NULL WHERE id = ?")) {
                for (PendingReward p : pendingList) {
                    ps.setLong(1, p.id());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "释放待发放奖励认领失败", e);
            }
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
    private void flushPending(Player player, List<PendingReward> pendingList) {
        List<Long> toDelete = new ArrayList<>();
        List<Long> toIncrement = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        for (PendingReward pending : pendingList) {
            if (pending.attempts() >= MAX_ATTEMPTS) {
                logger.warning("待发放奖励超过最大尝试次数，自动丢弃: id=" + pending.id()
                    + " module=" + pending.sourceModule() + " player=" + player.getName());
                toDelete.add(pending.id());
                continue;
            }
            try {
                boolean success = dispatchPending(player, pending);
                if (success) {
                    toDelete.add(pending.id());
                    successCount++;
                } else {
                    toIncrement.add(pending.id());
                    failCount++;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "补发待发放奖励异常: id=" + pending.id()
                    + " module=" + pending.sourceModule(), e);
                toIncrement.add(pending.id());
                failCount++;
            }
        }
        // 批量提交结果（异步执行，避免主线程 DB 操作）
        List<Long> finalToDelete = toDelete;
        List<Long> finalToIncrement = toIncrement;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
            batchUpdate(finalToDelete, finalToIncrement));
        if (successCount > 0) {
            logger.info("待发放奖励补发完成: player=" + player.getName()
                + " success=" + successCount + " fail=" + failCount);
        }
    }

    /**
     * 批量执行删除和递增操作，在单个事务中提交以减少 DB 往返。
     */
    private void batchUpdate(List<Long> toDelete, List<Long> toIncrement) {
        if (toDelete.isEmpty() && toIncrement.isEmpty()) return;
        try (Connection conn = storageManager.getHostConnection()) {
            conn.setAutoCommit(false);
            try {
                if (!toDelete.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM axs_pending_rewards WHERE id = ?")) {
                        for (Long id : toDelete) {
                            ps.setLong(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                if (!toIncrement.isEmpty()) {
                    // 失败行递增 attempts 并清认领：本节点下次上线或其他节点（TTL 后）可重试
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE axs_pending_rewards SET attempts = attempts + 1, "
                        + "claimed_at = NULL, claimed_by = NULL WHERE id = ?")) {
                        for (Long id : toIncrement) {
                            ps.setLong(1, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "批量更新待发放奖励失败", e);
        }
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
