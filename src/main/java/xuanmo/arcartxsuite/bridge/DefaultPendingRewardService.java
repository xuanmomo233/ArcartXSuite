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
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.module.ModuleRegistry;

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
 */
public final class DefaultPendingRewardService implements PendingRewardService, Listener {

    /** 最大补发尝试次数，超过后自动丢弃 */
    private static final int MAX_ATTEMPTS = 10;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final StorageManager storageManager;
    private final ModuleRegistry registry;

    public DefaultPendingRewardService(JavaPlugin plugin, Logger logger,
                                       StorageManager storageManager, ModuleRegistry registry) {
        this.plugin = plugin;
        this.logger = logger;
        this.storageManager = storageManager;
        this.registry = registry;
    }

    /**
     * 初始化数据库表。
     */
    public void initialize() {
        try (Connection conn = storageManager.getConnection();
             Statement stmt = conn.createStatement()) {
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
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "初始化 axs_pending_rewards 表失败", e);
        }
    }

    // ─── PendingRewardService 接口实现 ──────────────────────────

    @Override
    public void savePending(@NotNull PendingReward reward) {
        try (Connection conn = storageManager.getConnection();
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
        try (Connection conn = storageManager.getConnection();
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
        try (Connection conn = storageManager.getConnection();
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
            flushPending(player, uuid);
        }, 40L);
    }

    /**
     * 加载并补发指定玩家的所有待发放奖励。
     * <p>
     * 补发结果（markDelivered / incrementAttempts）在单个事务中批量提交，减少 DB 往返。
     */
    private void flushPending(Player player, UUID uuid) {
        List<PendingReward> pendingList = loadPending(uuid);
        if (pendingList.isEmpty()) return;

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
        // 批量提交结果
        batchUpdate(toDelete, toIncrement);
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
        try (Connection conn = storageManager.getConnection()) {
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
                    try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE axs_pending_rewards SET attempts = attempts + 1 WHERE id = ?")) {
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

    private boolean dispatchItemSerialized(Player player, PendingReward pending) {
        ItemRewardDispatcher dispatcher = registry.itemRewardDispatcher();
        if (dispatcher == null) return false;
        String base64Data = extractJsonField(pending.payload(), "data");
        if (base64Data == null || base64Data.isBlank()) return false;
        try {
            org.bukkit.inventory.ItemStack item = xuanmo.arcartxsuite.api.util.ItemSerializer.deserialize(
                java.util.Base64.getDecoder().decode(base64Data));
            if (item == null) return false;
            ItemRewardDispatcher.DispatchOptions options = ItemRewardDispatcher.DispatchOptions.builder(pending.sourceModule())
                .sourceDetail("pending:" + pending.id())
                .overflowMailSubject("待发放奖励溢出物品")
                .overflowMailBody("您的背包已满，待发放奖励物品通过邮件补发。")
                .overflowMailSenderName(pending.sourceModule())
                .build();
            ItemRewardDispatcher.DispatchResult result = dispatcher.dispatchItem(player, item, options);
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
        ItemRewardDispatcher.DispatchOptions options = ItemRewardDispatcher.DispatchOptions.builder(pending.sourceModule())
            .sourceDetail("pending:" + pending.id())
            .overflowMailSubject("待发放奖励溢出物品")
            .overflowMailBody("您的背包已满，待发放奖励物品通过邮件补发。")
            .overflowMailSenderName(pending.sourceModule())
            .build();
        ItemRewardDispatcher.DispatchResult result = dispatcher.dispatchItem(player, item, options);
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
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
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

    private static @Nullable String extractJsonField(@NotNull String json, @NotNull String field) {
        // 匹配 "field":"value"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    private static int extractJsonInt(@NotNull String json, @NotNull String field, int defaultValue) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*(\\d+)");
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
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*([\\d.]+)");
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
        try (Connection conn = storageManager.getConnection();
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
