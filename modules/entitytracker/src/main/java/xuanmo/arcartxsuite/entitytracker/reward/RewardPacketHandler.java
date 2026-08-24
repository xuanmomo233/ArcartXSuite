package xuanmo.arcartxsuite.entitytracker.reward;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.entitytracker.boss.config.PluginConfiguration;
import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardRecord;
import xuanmo.arcartxsuite.entitytracker.scheduler.RankingRewardScheduler;
import xuanmo.arcartxsuite.entitytracker.service.RankingRewardService;

/**
 * 排行榜奖励 UI 的客户端包处理器。
 * <p>
 * 处理来自 ranking_rewards / reward_editor / reward_history 三个 UI 的 9 个 action。
 */
public final class RewardPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_ENTITY_TRACKER_REWARD";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final RankingRewardService rewardService;
    private final RankingRewardScheduler rewardScheduler;
    private final Supplier<PluginConfiguration> configurationSupplier;
    private final String managerUiId;
    private final String editorUiId;
    private final String historyUiId;
    private final File dataFolder;
    private final Logger logger;

    /** 记录每个玩家当前查看的奖励周期类型，用于 toggle action */
    private final Map<UUID, String> currentRewardType = new ConcurrentHashMap<>();

    public RewardPacketHandler(
        JavaPlugin plugin,
        PacketBridgeAPI packetBridge,
        PacketGuardAPI packetGuard,
        RankingRewardService rewardService,
        RankingRewardScheduler rewardScheduler,
        Supplier<PluginConfiguration> configurationSupplier,
        String managerUiId,
        String editorUiId,
        String historyUiId,
        File dataFolder,
        Logger logger
    ) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.rewardService = rewardService;
        this.rewardScheduler = rewardScheduler;
        this.configurationSupplier = configurationSupplier;
        this.managerUiId = managerUiId;
        this.editorUiId = editorUiId;
        this.historyUiId = historyUiId;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;

        String action = data.isEmpty() ? "list-configs" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。

        switch (action) {
            case "list-configs" -> handleListConfigs(player, data);
            case "scheduler-status" -> handleSchedulerStatus(player);
            case "open-history" -> handleOpenHistory(player);
            case "manual-distribute" -> handleManualDistribute(player, data);
            case "toggle" -> handleToggle(player, data);
            case "history-list" -> handleHistoryList(player, data);
            case "open-manage" -> handleOpenManage(player);
            case "retry" -> handleRetry(player, data);
            case "save-config" -> handleSaveConfig(player, data);
            default -> pushResult(player, managerUiId, "§c未知操作: " + action);
        }
        return true;
    }

    // ─── Action 1: list-configs ───────────────────────────

    private void handleListConfigs(Player player, List<String> data) {
        if (packetBridge == null) return;
        String rewardType = safe(data.size() > 1 ? data.get(1) : "weekly");
        if (!"weekly".equals(rewardType) && !"monthly".equals(rewardType)) {
            rewardType = "weekly";
        }
        currentRewardType.put(player.getUniqueId(), rewardType);

        List<Map<String, Object>> configs = rewardService.getConfigsForPeriod(rewardType);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("rewardType", rewardType);
        payload.put("total", Integer.toString(configs.size()));
        payload.put("configs", configs);

        packetBridge.sendPacket(player, managerUiId, "config-list", payload);
    }

    // ─── Action 2: scheduler-status ────────────────────────

    private void handleSchedulerStatus(Player player) {
        if (packetBridge == null || rewardScheduler == null) return;

        boolean weeklyRunning = rewardScheduler.isWeeklyTaskRunning();
        boolean monthlyRunning = rewardScheduler.isMonthlyTaskRunning();

        // 从 getNextExecutionInfo 中解析下次执行时间
        String nextInfo = rewardScheduler.getNextExecutionInfo();
        String nextWeekly = "";
        String nextMonthly = "";
        if (nextInfo != null && !nextInfo.isBlank()) {
            for (String line : nextInfo.split("\n")) {
                if (line.contains("周奖励")) {
                    nextWeekly = line.replaceFirst(".*:\\s*", "").trim();
                } else if (line.contains("月奖励")) {
                    nextMonthly = line.replaceFirst(".*:\\s*", "").trim();
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("weeklyEnabled", Boolean.toString(weeklyRunning));
        payload.put("monthlyEnabled", Boolean.toString(monthlyRunning));
        payload.put("nextWeekly", nextWeekly);
        payload.put("nextMonthly", nextMonthly);
        payload.put("weeklyRunning", Boolean.toString(weeklyRunning));
        payload.put("monthlyRunning", Boolean.toString(monthlyRunning));

        packetBridge.sendPacket(player, managerUiId, "scheduler-status", payload);
    }

    // ─── Action 3: open-history ────────────────────────────

    private void handleOpenHistory(Player player) {
        if (packetBridge == null || historyUiId == null) return;
        packetBridge.openUi(player, historyUiId);
    }

    // ─── Action 4: manual-distribute ───────────────────────

    private void handleManualDistribute(Player player, List<String> data) {
        if (rewardScheduler == null) {
            pushResult(player, managerUiId, "§c奖励调度器未启用");
            return;
        }

        String rewardType = safe(data.size() > 1 ? data.get(1) : "weekly");
        if (!"weekly".equals(rewardType) && !"monthly".equals(rewardType)) {
            pushResult(player, managerUiId, "§c无效的奖励周期: " + rewardType);
            return;
        }

        String periodDisplay = "weekly".equals(rewardType) ? "周" : "月";
        pushResult(player, managerUiId, "§e正在发放" + periodDisplay + "奖励，请稍候...");

        final String finalRewardType = rewardType;
        final String finalPeriodDisplay = periodDisplay;
        java.util.concurrent.CompletableFuture<Integer> future = "weekly".equals(rewardType)
            ? rewardScheduler.manualTriggerWeeklyRewards()
            : rewardScheduler.manualTriggerMonthlyRewards();

        future.thenAccept(total -> {
            if (player.isOnline()) {
                pushResult(player, managerUiId, "§a" + finalPeriodDisplay + "奖励发放完成，共发放 §e" + total + " §a个奖励");
            }
        }).exceptionally(throwable -> {
            if (player.isOnline()) {
                pushResult(player, managerUiId, "§c" + finalPeriodDisplay + "奖励发放失败: " + throwable.getMessage());
            }
            return null;
        });
    }

    // ─── Action 5: toggle ──────────────────────────────────

    private void handleToggle(Player player, List<String> data) {
        String configId = safe(data.size() > 1 ? data.get(1) : "");
        if (configId.isBlank()) {
            pushResult(player, managerUiId, "§c缺少配置 ID");
            return;
        }

        String rewardType = currentRewardType.getOrDefault(player.getUniqueId(), "weekly");
        String message = rewardService.toggleConfig(configId, rewardType);
        pushResult(player, managerUiId, message);

        // 刷新列表
        handleListConfigs(player, List.of("list-configs", rewardType));
    }

    // ─── Action 6: history-list ────────────────────────────

    private void handleHistoryList(Player player, List<String> data) {
        if (packetBridge == null) return;

        // data.get(1) = filter (none/success/failed/pending)
        // data.get(2) = rewardType (none 或具体类型)
        // data.get(3) = page
        String filter = safe(data.size() > 1 ? data.get(1) : "");
        String rewardType = safe(data.size() > 2 ? data.get(2) : "");
        int page = parseInt(safe(data.size() > 3 ? data.get(3) : "1"), 1);

        // "none" 或空表示不筛选
        String statusFilter = (filter.isBlank() || "none".equalsIgnoreCase(filter)) ? null : filter;
        String typeFilter = (rewardType.isBlank() || "none".equalsIgnoreCase(rewardType)) ? null : rewardType;

        rewardService.getRewardRecords(typeFilter, statusFilter, null)
            .thenAccept(records -> {
                if (!player.isOnline()) return;

                int pageSize = 20;
                int totalCount = records.size();
                int totalPages = Math.max(1, (totalCount + pageSize - 1) / pageSize);
                int safePage = Math.max(1, Math.min(page, totalPages));
                int fromIndex = (safePage - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, totalCount);

                List<Map<String, Object>> recordMaps = new ArrayList<>();
                if (fromIndex < totalCount) {
                    for (RankingRewardRecord record : records.subList(fromIndex, toIndex)) {
                        recordMaps.add(recordToMap(record));
                    }
                }

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("packetId", PACKET_ID);
                payload.put("records", recordMaps);
                payload.put("page", Integer.toString(safePage));
                payload.put("totalPages", Integer.toString(totalPages));
                payload.put("totalCount", Integer.toString(totalCount));

                packetBridge.sendPacket(player, historyUiId, "history-list", payload);
            })
            .exceptionally(throwable -> {
                if (player.isOnline()) {
                    pushResult(player, historyUiId, "§c获取历史记录失败: " + throwable.getMessage());
                }
                return null;
            });
    }

    // ─── Action 7: open-manage ─────────────────────────────

    private void handleOpenManage(Player player) {
        if (packetBridge == null || managerUiId == null) return;
        packetBridge.openUi(player, managerUiId);
    }

    // ─── Action 8: retry ───────────────────────────────────

    private void handleRetry(Player player, List<String> data) {
        String recordIdStr = safe(data.size() > 1 ? data.get(1) : "");
        int recordId = parseInt(recordIdStr, -1);
        if (recordId < 0) {
            pushResult(player, historyUiId, "§c无效的记录 ID");
            return;
        }

        String message = rewardService.retryDistribution(recordId);
        pushResult(player, historyUiId, message);

        // 刷新历史列表
        if (packetBridge != null && historyUiId != null && packetBridge.isUiOpen(player, historyUiId)) {
            handleHistoryList(player, List.of("history-list", "none", "none", "1"));
        }
    }

    // ─── Action 9: save-config ─────────────────────────────

    private void handleSaveConfig(Player player, List<String> data) {
        // data: save-config, configId, rewardType, rankingType, bossId, rankStart, rankEnd,
        //       rewardName, rewardDescription, rewardMoney, rewardDkp
        String configId = safe(data.size() > 1 ? data.get(1) : "");
        String rewardType = safe(data.size() > 2 ? data.get(2) : "weekly");
        String rankingType = safe(data.size() > 3 ? data.get(3) : "boss_damage");
        String bossId = safe(data.size() > 4 ? data.get(4) : "");
        int rankStart = parseInt(safe(data.size() > 5 ? data.get(5) : "1"), 1);
        int rankEnd = parseInt(safe(data.size() > 6 ? data.get(6) : "1"), 1);
        String rewardName = safe(data.size() > 7 ? data.get(7) : "");
        String rewardDescription = safe(data.size() > 8 ? data.get(8) : "");
        int rewardMoney = parseInt(safe(data.size() > 9 ? data.get(9) : "0"), 0);
        int rewardDkp = parseInt(safe(data.size() > 10 ? data.get(10) : "0"), 0);

        String message = rewardService.saveConfig(
            configId, rewardType, rankingType, bossId,
            rankStart, rankEnd, rewardName, rewardDescription,
            rewardMoney, rewardDkp
        );

        boolean success = message.startsWith("§a");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("message", message);
        payload.put("success", Boolean.toString(success));
        if (packetBridge != null && editorUiId != null) {
            packetBridge.sendPacket(player, editorUiId, "result", payload);
        }
    }

    // ─── 辅助方法 ──────────────────────────────────────────

    private void pushResult(Player player, String uiId, String message) {
        if (packetBridge == null || uiId == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("message", message);
        packetBridge.sendPacket(player, uiId, "result", payload);
    }

    private static Map<String, Object> recordToMap(RankingRewardRecord record) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.getId() != null ? Integer.toString(record.getId()) : "0");
        item.put("rewardType", record.getRewardType() != null ? record.getRewardType() : "");
        item.put("rankingType", record.getRankingType() != null ? record.getRankingType() : "");
        item.put("rankingTypeDisplay", rankingTypeDisplay(record.getRankingType()));
        item.put("bossId", record.getBossId() != null ? record.getBossId() : "");
        item.put("playerName", record.getPlayerName() != null ? record.getPlayerName() : "");
        item.put("rank", record.getRank() != null ? Integer.toString(record.getRank()) : "0");
        item.put("score", record.getScore() != null ? Integer.toString(record.getScore()) : "0");
        item.put("status", record.getStatus() != null ? record.getStatus() : "");
        item.put("statusIcon", statusIcon(record.getStatus()));
        item.put("periodDisplay", formatPeriod(record.getPeriodStart(), record.getPeriodEnd()));
        item.put("rewardSummary", buildRewardSummary(record));
        item.put("issuedTime", record.getIssuedTime() != null ? record.getIssuedTime().toString() : "");
        item.put("failureReason", record.getFailureReason() != null ? record.getFailureReason() : "");
        item.put("retryCount", Integer.toString(record.getRetryCount() != null ? record.getRetryCount() : 0));
        return item;
    }

    private static String rankingTypeDisplay(String rankingType) {
        if (rankingType == null) return "";
        return switch (rankingType) {
            case "best_damage" -> "最高伤害";
            case "boss_damage" -> "Boss伤害";
            case "kills" -> "击杀排行";
            case "participate" -> "参与排行";
            default -> rankingType;
        };
    }

    private static String statusIcon(String status) {
        if (status == null) return "";
        return switch (status) {
            case "success" -> "&a成功";
            case "failed" -> "&c失败";
            case "pending" -> "&e待发";
            default -> status;
        };
    }

    private static String formatPeriod(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null && end == null) return "";
        StringBuilder sb = new StringBuilder();
        if (start != null) {
            sb.append(start.toLocalDate());
        }
        sb.append(" ~ ");
        if (end != null) {
            sb.append(end.toLocalDate());
        }
        return sb.toString();
    }

    private static String buildRewardSummary(RankingRewardRecord record) {
        List<String> parts = new ArrayList<>();
        if (record.getRewardMoney() != null && record.getRewardMoney() > 0) {
            parts.add("金币×" + record.getRewardMoney());
        }
        if (record.getRewardDkp() != null && record.getRewardDkp() > 0) {
            parts.add("DKP×" + record.getRewardDkp());
        }
        if (record.getRewardItems() != null && !record.getRewardItems().isBlank()) {
            parts.add("物品");
        }
        if (record.getRewardCommands() != null && !record.getRewardCommands().isBlank()) {
            parts.add("指令");
        }
        return parts.isEmpty() ? "无" : String.join(", ", parts);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
