package xuanmo.arcartxsuite.battlepass.packet;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

/**
 * 战令客户端包处理器，实现 {@link ClientPacketHandler}。
 * <p>
 * 负责处理来自 ArcartX 客户端的战令 UI 交互包（打开主界面、任务列表、奖励列表、领取奖励），
 * 通过 {@link PacketBridgeAPI} 向 UI 推送数据，并使用 action token 机制防止重复领取。
 */
public final class BattlePassPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_BATTLEPASS";

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final BattlePassService service;
    private final String mainUiId;
    private final String tasksUiId;
    private final String rewardsUiId;
    private final IconResolver iconResolver;
    private final java.util.function.BiFunction<String, Object[], String> messageResolver;
    private final Map<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> actionTokensInFlight = ConcurrentHashMap.newKeySet();

    /**
     * 构造战令包处理器。
     *
     * @param plugin          插件实例
     * @param packetBridge    Packet 桥接 API
     * @param packetGuard     包频率守卫
     * @param service         BattlePassService 实例
     * @param mainUiId        主界面 UI ID
     * @param tasksUiId       任务界面 UI ID
     * @param rewardsUiId     奖励界面 UI ID
     * @param itemSourceRegistry 物品来源注册表，可为 null
     * @param itemStackBridge    ItemStack 桥接 API，可为 null
     * @param messageResolver 消息解析函数
     */
    public BattlePassPacketHandler(JavaPlugin plugin, PacketBridgeAPI packetBridge,
                                    PacketGuardAPI packetGuard, BattlePassService service,
                                    String mainUiId, String tasksUiId, String rewardsUiId,
                                    @Nullable ItemSourceRegistry itemSourceRegistry,
                                    @Nullable ItemBridgeAPI itemStackBridge,
                                    java.util.function.BiFunction<String, Object[], String> messageResolver) {
        this.plugin = plugin;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.service = service;
        this.mainUiId = mainUiId;
        this.tasksUiId = tasksUiId;
        this.rewardsUiId = rewardsUiId;
        this.iconResolver = new IconResolver(itemStackBridge, itemSourceRegistry);
        this.messageResolver = messageResolver;
    }

    /**
     * 处理客户端发来的战令交互包。
     *
     * @param player   发送包的玩家
     * @param packetId 包标识（需匹配 {@link #PACKET_ID}）
     * @param data     包数据列表
     * @return {@code true} 表示已处理此包
     */
    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        if (!PACKET_ID.equalsIgnoreCase(packetId)) return false;
        String action = data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        if (packetGuard != null && !packetGuard.allow(player, "battlepass", action, false)) return true;

        switch (action) {
            case "open_main" -> openMain(player);
            case "open_tasks" -> openTasks(player);
            case "open_rewards" -> openRewards(player);
            case "claim" -> handleClaim(player, data);
            case "refresh" -> pushMainData(player);
            default -> pushMainData(player);
        }
        return true;
    }

    private void handleClaim(Player player, List<String> data) {
        UUID uuid = player.getUniqueId();
        String supplied = data.size() > 2 ? safe(data.get(data.size() - 1)) : "";
        String expected = actionTokens.get(uuid);
        // claim 可在主界面或奖励界面发起，两者都允许
        if (expected == null || supplied.isBlank() || !expected.equals(supplied)
            || !actionTokensInFlight.add(uuid)
            || packetBridge == null
            || (!packetBridge.isUiOpen(player, mainUiId) && !packetBridge.isUiOpen(player, rewardsUiId))) {
            actionTokensInFlight.remove(uuid);
            return;
        }
        int level;
        try {
            level = Integer.parseInt(safe(data.size() > 1 ? data.get(1) : ""));
        } catch (NumberFormatException e) {
            actionTokensInFlight.remove(uuid);
            return;
        }
        BattlePassService.ClaimResult result;
        try {
            result = service.claimRewards(player, level);
        } finally {
            actionTokensInFlight.remove(uuid);
        }
        String key = switch (result) {
            case SUCCESS -> "claim.success";
            case LEVEL_NOT_REACHED -> "claim.level-not-reached";
            case ALREADY_CLAIMED -> "claim.already-claimed";
            case NOT_FOUND -> "claim.none";
        };
        if (messageResolver != null) {
            player.sendMessage(messageResolver.apply(key, new Object[]{String.valueOf(level)}));
        }
        if (result == BattlePassService.ClaimResult.SUCCESS) {
            actionTokens.put(uuid, UUID.randomUUID().toString());
        }
        // 刷新奖励数据（如果奖励界面打开着）
        if (packetBridge.isUiOpen(player, rewardsUiId)) {
            pushRewardsData(player);
        }
    }

    /**
     * 为玩家打开战令主界面并推送进度数据。
     *
     * @param player 目标玩家
     */
    public void openMain(Player player) {
        if (packetBridge == null) return;
        packetBridge.openUi(player, mainUiId);
        actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        AxsScheduler.runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                pushMainData(player);
            }
        }, 2L);
    }

    /**
     * 为玩家打开任务界面并推送任务列表数据。
     *
     * @param player 目标玩家
     */
    public void openTasks(Player player) {
        if (packetBridge == null) return;
        packetBridge.openUi(player, tasksUiId);
        AxsScheduler.runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                pushTasksData(player);
            }
        }, 2L);
    }

    /**
     * 为玩家打开独立的奖励界面并推送奖励数据。
     *
     * @param player 目标玩家
     */
    public void openRewards(Player player) {
        if (packetBridge == null) return;
        packetBridge.openUi(player, rewardsUiId);
        actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        AxsScheduler.runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                pushRewardsData(player);
            }
        }, 2L);
    }

    private void pushRewardsData(Player player) {
        if (packetBridge == null) return;
        BattlePassPlayerProgress progress = service.getProgress(player);

        List<BattlePassReward> sorted = new ArrayList<>(service.configuration().rewards());
        sorted.sort(java.util.Comparator.comparingInt(BattlePassReward::level)
            .thenComparingInt(r -> r.tier().ordinal()));

        // 按等级分组：每个等级包含 free/premium/deluxe 三个档位
        Map<String, Map<String, String>> levelsMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> rewardsMap = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();

        int idx = 0;
        for (BattlePassReward reward : sorted) {
            String levelKey = String.valueOf(reward.level());
            if (!levelsMap.containsKey(levelKey)) {
                Map<String, String> levelEntry = new LinkedHashMap<>();
                levelEntry.put("level", levelKey);
                levelEntry.put("reached", String.valueOf(progress.currentLevel() >= reward.level()));
                levelsMap.put(levelKey, levelEntry);
            }

            String tierKey = reward.tier().name().toLowerCase();
            String entryKey = String.valueOf(idx);
            boolean tierUnlocked = switch (reward.tier()) {
                case FREE -> true;
                case PREMIUM -> progress.unlockedPremium();
                case DELUXE -> progress.unlockedDeluxe();
            };
            boolean levelReached = progress.currentLevel() >= reward.level();
            boolean claimed = progress.claimedRewards().contains(reward.rewardId());

            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("level", String.valueOf(reward.level()));
            entry.put("tier", reward.tier().name());
            entry.put("tierDisplay", rewardTierDisplay(reward.tier()));
            entry.put("tierKey", tierKey);
            entry.put("claimed", String.valueOf(claimed));
            entry.put("claimable", String.valueOf(!claimed && levelReached && tierUnlocked));
            entry.put("locked", String.valueOf(!tierUnlocked));
            entry.put("levelReached", String.valueOf(levelReached));
            entry.put("type", reward.type());
            rewardsMap.put(entryKey, entry);

            // 推送图标 itemJson（flat 字段）
            String iconJson = iconResolver.resolveItemJson(player, reward.icon());
            payload.put("iconJson" + idx, iconJson);

            levelsMap.get(levelKey).put(tierKey + "EntryKey", entryKey);
            idx++;
        }

        payload.put("packetId", PACKET_ID);
        payload.put("levels", levelsMap);
        payload.put("rewards", rewardsMap);
        payload.put("levelCount", String.valueOf(levelsMap.size()));
        payload.put("rewardCount", String.valueOf(idx));
        payload.put("currentLevel", String.valueOf(progress.currentLevel()));
        payload.put("action_token", actionTokens.getOrDefault(player.getUniqueId(), ""));

        packetBridge.sendPacket(player, rewardsUiId, "rewards", payload);
    }

    private static String rewardTierDisplay(BattlePassReward.RewardTier tier) {
        return switch (tier) {
            case FREE -> "&7免费";
            case PREMIUM -> "&6高级";
            case DELUXE -> "&5典藏";
        };
    }

    private void pushMainData(Player player) {
        if (packetBridge == null) return;
        BattlePassPlayerProgress progress = service.getProgress(player);
        var season = service.configuration().season();
        int xpNeeded = Math.max(0, season.xpPerLevel() - progress.currentXp());
        float ratio = season.xpPerLevel() > 0 ? (float) progress.currentXp() / season.xpPerLevel() : 0f;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("seasonName", season.displayName());
        payload.put("currentLevel", String.valueOf(progress.currentLevel()));
        payload.put("maxLevel", String.valueOf(season.maxLevel()));
        payload.put("currentXp", String.valueOf(progress.currentXp()));
        payload.put("xpPerLevel", String.valueOf(season.xpPerLevel()));
        payload.put("xpNeeded", String.valueOf(xpNeeded));
        payload.put("progressRatio", String.valueOf(ratio));
        payload.put("premiumUnlocked", String.valueOf(progress.unlockedPremium()));
        payload.put("deluxeUnlocked", String.valueOf(progress.unlockedDeluxe()));
        payload.put("tier", progress.passTier().name());
        payload.put("tierDisplay", tierDisplay(progress.passTier()));

        packetBridge.sendPacket(player, mainUiId, "init", payload);
    }

    private void pushTasksData(Player player) {
        if (packetBridge == null) return;
        List<PlayerTaskInstance> instances = service.getTaskInstances(player);

        Map<String, Map<String, String>> dailyMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> weeklyMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> seasonMap = new LinkedHashMap<>();

        int dIdx = 0, wIdx = 0, sIdx = 0;
        for (PlayerTaskInstance inst : instances) {
            BattlePassTask task = service.findTask(inst.templateId());
            Map<String, String> entry = new HashMap<>();
            entry.put("name", task != null ? task.displayName() : inst.templateId());
            entry.put("description", task != null ? task.description() : "");
            entry.put("progress", String.valueOf(inst.currentProgress()));
            entry.put("required", String.valueOf(inst.targetCount()));
            entry.put("completed", String.valueOf(inst.completed()));
            entry.put("xpReward", String.valueOf(task != null ? task.totalXpReward() : 0));
            entry.put("difficulty", task != null ? task.difficulty().name() : "EASY");
            entry.put("difficultyDisplay", difficultyDisplay(task != null ? task.difficulty() : BattlePassTask.TaskDifficulty.EASY));
            entry.put("progressPercent", String.format("%.0f%%", inst.progressPercent()));
            entry.put("category", inst.category().name());

            switch (inst.category()) {
                case DAILY -> dailyMap.put(String.valueOf(dIdx++), entry);
                case WEEKLY -> weeklyMap.put(String.valueOf(wIdx++), entry);
                case SEASON -> seasonMap.put(String.valueOf(sIdx++), entry);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("dailyTasks", dailyMap);
        payload.put("weeklyTasks", weeklyMap);
        payload.put("seasonTasks", seasonMap);
        payload.put("dailyTaskCount", String.valueOf(dailyMap.size()));
        payload.put("weeklyTaskCount", String.valueOf(weeklyMap.size()));
        payload.put("seasonTaskCount", String.valueOf(seasonMap.size()));
        payload.put("maxDailyCount", String.valueOf(dailyMap.size()));
        payload.put("maxWeeklyCount", String.valueOf(weeklyMap.size()));
        payload.put("maxSeasonCount", String.valueOf(seasonMap.size()));

        packetBridge.sendPacket(player, tasksUiId, "init", payload);
    }

    private static String tierDisplay(BattlePassPlayerProgress.PassTier tier) {
        return switch (tier) {
            case FREE -> "&7免费";
            case PREMIUM -> "&6高级";
            case DELUXE -> "&5典藏";
        };
    }

    private static String difficultyDisplay(BattlePassTask.TaskDifficulty diff) {
        return switch (diff) {
            case EASY -> "&a简单";
            case NORMAL -> "&e普通";
            case HARD -> "&c困难";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
