package xuanmo.arcartxsuite.battlepass.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;
import xuanmo.arcartxsuite.battlepass.model.SeasonSettlementReward;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

/**
 * 战令客户端包处理器，实现 {@link ClientPacketHandler}。
 * <p>
 * 负责处理来自 ArcartX 客户端的战令 UI 交互包（打开主界面、任务列表、奖励列表、领取奖励），
 * 通过 {@link PacketBridgeAPI} 向 UI 推送数据，并使用 action token 机制防止重复领取。
 */
public final class BattlePassPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_BATTLEPASS";
    private static final int MAX_PUSH_RETRY = 3;
    private static final long RETRY_INTERVAL_TICKS = 20L; // 1秒

    private final JavaPlugin plugin;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final BattlePassService service;
    private final String mainUiId;
    private final String tasksUiId;
    private final String rewardsUiId;
    private final String settlementUiId;
    private final IconResolver iconResolver;
    private final RewardLayout rewardLayout;
    private final List<SeasonSettlementReward> orderedSettlements;
    private final Map<IconDefinition, String> staticIcons = new ConcurrentHashMap<>();
    private final java.util.function.BiFunction<String, Object[], String> messageResolver;
    private final Map<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MapNavigable> map = () -> null;
    private java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.QuestGpsNavigable> quests = () -> null;

    public void setNavigationProviders(
            java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MapNavigable> map,
            java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.QuestGpsNavigable> quests) {
        this.map = map;
        this.quests = quests;
    }
    private final Map<UUID, ClaimAttempt> actionTokensInFlight = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<RefreshKey, Refresh> pendingRefreshes = new ConcurrentHashMap<>();


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
     * @param settlementUiId  赛季结算奖励界面 UI ID
     * @param itemSourceRegistry 物品来源注册表，可为 null
     * @param itemStackBridge    ItemStack 桥接 API，可为 null
     * @param messageResolver 消息解析函数
     */
    public BattlePassPacketHandler(JavaPlugin plugin, PacketBridgeAPI packetBridge,
                                    PacketGuardAPI packetGuard, BattlePassService service,
                                    String mainUiId, String tasksUiId, String rewardsUiId,
                                    String settlementUiId,
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
        this.settlementUiId = settlementUiId;
        this.iconResolver = new IconResolver(itemStackBridge, itemSourceRegistry);
        this.rewardLayout = RewardLayout.compile(service.configuration().rewards());
        this.orderedSettlements = service.configuration().seasonRewards().stream()
            .sorted(Comparator.comparingInt(SeasonSettlementReward::minLevel)).toList();
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
        if (!canUse(player)) return true;
        String action = data.isEmpty() ? "refresh" : safe(data.get(0)).toLowerCase(Locale.ROOT);
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。

        switch (action) {
            case "open_main" -> openMain(player);
            case "open_tasks" -> openTasks(player);
            case "navigate_task" -> navigateTask(player, data);
            case "open_rewards" -> openRewards(player);
            case "open_settlement" -> openSettlement(player);
            case "claim" -> handleClaim(player, data);
            case "claim_all" -> handleClaimAll(player, data);
            case "refresh_tasks" -> requestRefresh(player, View.TASKS, Format.INIT, 0L);
            case "refresh_rewards" -> requestRefresh(player, View.REWARDS, Format.INIT, 0L);
            case "refresh_settlement" -> requestRefresh(player, View.SETTLEMENT, Format.INIT, 0L);
            default -> requestRefresh(player, View.MAIN, Format.INIT, 0L);
        }
        return true;
    }

    private void handleClaim(Player player, List<String> data) {
        UUID uuid = player.getUniqueId();
        String supplied = data.size() > 2 ? safe(data.get(data.size() - 1)) : "";
        // claim 可在主界面或奖励界面发起，两者都允许
        if (packetBridge == null
            || (!packetBridge.isUiOpen(player, mainUiId) && !packetBridge.isUiOpen(player, rewardsUiId))) {
            return;
        }
        int level;
        try {
            level = Integer.parseInt(safe(data.size() > 1 ? data.get(1) : ""));
        } catch (NumberFormatException e) {
            return;
        }
        var attempt = beginClaim(uuid, supplied);
        if (attempt == null) return;
        try {
            service.claimLevelAsync(player, level, () -> validClaim(player, attempt, false)).whenComplete((result, error) -> {
                var outcome = error == null ? result : BattlePassService.ClaimResult.RETRY;
                finishClaim(player, attempt, outcome == BattlePassService.ClaimResult.SUCCESS
                    || outcome == BattlePassService.ClaimResult.PARTIAL, outcome.messageKey(), level);
            });
        } catch (RuntimeException failure) {
            finishClaim(player, attempt, false, "claim.retry", level);
        }
    }

    private void navigateTask(Player player, List<String> data) {
        if (data.size() != 2 || packetBridge == null || !packetBridge.isUiOpen(player, tasksUiId)) return;
        navigateTask(player, data.get(1), true);
    }

    public void navigateTask(Player player, String requestedId, boolean fromTaskUi) {
        if (!service.isSeasonActive() || requestedId == null || requestedId.isBlank()) return;
        if (!canUse(player)) return;
        service.async( () -> {
            var navigation = service.getActiveTaskInstances(player).stream()
                .filter(instance -> requestedId.equals(fromTaskUi ? instance.instanceId() : instance.templateId()))
                .map(instance -> service.findTask(instance.templateId()))
                .filter(java.util.Objects::nonNull)
                .map(BattlePassTask::navigation)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
            service.onMain( () -> {
                if (!player.isOnline() || (fromTaskUi && !packetBridge.isUiOpen(player, tasksUiId))) return;
                if (!canUse(player)) return;
                if (navigation == null) {
                    sendClaimMessage(player, "tasks.navigation-unavailable");
                    return;
                }
                if (navigation.type().equals("map")) {
                    var provider = map.get();
                    if (provider != null && provider.navigateExternalTarget(player, navigation.target())) {
                        provider.openMenuFor(player, navigation.target());
                        return;
                    }
                } else {
                    var provider = quests.get();
                    if (provider != null) {
                        var state = provider.questState(player, navigation.target());
                        if (state == xuanmo.arcartxsuite.api.capability.QuestGpsNavigable.QuestState.ACTIVE) {
                            provider.trackQuest(player, navigation.target());
                        } else {
                            provider.offerQuest(player, navigation.target(), true);
                        }
                        return;
                    }
                }
                sendClaimMessage(player, "tasks.navigation-unavailable");
            });
        });
    }

    private void handleClaimAll(Player player, List<String> data) {
        UUID uuid = player.getUniqueId();
        String supplied = data.size() > 1 ? safe(data.get(data.size() - 1)) : "";
        if (packetBridge == null || !packetBridge.isUiOpen(player, rewardsUiId)) return;
        var attempt = beginClaim(uuid, supplied);
        if (attempt == null) return;
        try {
            service.claimBatchAsync(player, () -> validClaim(player, attempt, true)).whenComplete((batch, error) -> {
                if (error != null) {
                    finishClaim(player, attempt, false, "claim.retry");
                } else if (batch.accepted() > 0) {
                    finishClaim(player, attempt, true, batch.retry() ? "claim_all.partial" : "claim_all.success", batch.accepted());
                } else {
                    finishClaim(player, attempt, false, batch.retry() ? "claim.retry" : "claim_all.none");
                }
            });
        } catch (RuntimeException failure) {
            finishClaim(player, attempt, false, "claim.retry");
        }
    }

    private record ClaimAttempt(String token, AuthSnapshot session) {}

    private ClaimAttempt beginClaim(UUID id, String token) {
        if (token.isBlank() || !token.equals(actionTokens.get(id))) return null;
        AuthSnapshot session = AuthAccess.snapshot(id);
        if (!session.admitted()) return null;
        var attempt = new ClaimAttempt(token, session);
        return actionTokensInFlight.putIfAbsent(id, attempt) == null ? attempt : null;
    }

    private boolean validClaim(Player player, ClaimAttempt attempt, boolean rewardsOnly) {
        UUID id = attempt.session().playerId();
        return actionTokensInFlight.get(id) == attempt && attempt.token().equals(actionTokens.get(id))
            && currentClaimSession(player, attempt)
            && (packetBridge.isUiOpen(player, rewardsUiId) || (!rewardsOnly && packetBridge.isUiOpen(player, mainUiId)));
    }

    private boolean currentClaimSession(Player player, ClaimAttempt attempt) {
        return canUse(player) && sameSession(attempt.session(), AuthAccess.snapshot(attempt.session().playerId()));
    }

    private void finishClaim(Player player, ClaimAttempt attempt, boolean changed, String key, Object... args) {
        UUID id = attempt.session().playerId();
        try {
            if (actionTokensInFlight.get(id) != attempt || !currentClaimSession(player, attempt)) return;
            if (changed) actionTokens.replace(id, attempt.token(), UUID.randomUUID().toString());
            if (messageResolver != null) {
                service.onMain(() -> {
                    if (currentClaimSession(player, attempt)) player.sendMessage(messageResolver.apply(key, args));
                });
            }
            requestRefresh(player, View.REWARDS, Format.INIT, 0L);
        } finally {
            actionTokensInFlight.remove(id, attempt);
        }
    }

    private boolean canUse(Player player) {
        return service.isActive() && player.isOnline() && player.hasPermission("arcartxsuite.battlepass.use")
            && AuthAccess.admitted(player.getUniqueId());
    }

    private void sendClaimMessage(Player player, String key, Object... args) {
        if (messageResolver == null) return;
        service.onMain( () -> {
            if (player.isOnline()) player.sendMessage(messageResolver.apply(key, args));
        });
    }

    /**
     * 为玩家打开战令主界面并推送进度数据。
     *
     * @param player 目标玩家
     */
    public void openMain(Player player) {
        if (packetBridge == null || !canUse(player)) return;
        packetBridge.openUi(player, mainUiId);
        actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        requestRefresh(player, View.MAIN, Format.INIT, 2L);
    }

    /**
     * 为玩家打开任务界面并推送任务列表数据。
     *
     * @param player 目标玩家
     */
    public void openTasks(Player player) {
        if (packetBridge == null || !canUse(player)) return;
        packetBridge.openUi(player, tasksUiId);
        requestRefresh(player, View.TASKS, Format.INIT, 2L);
    }

    /**
     * 为玩家打开独立的奖励界面并推送奖励数据。
     *
     * @param player 目标玩家
     */
    public void openRewards(Player player) {
        if (packetBridge == null || !canUse(player)) return;
        packetBridge.openUi(player, rewardsUiId);
        actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        requestRefresh(player, View.REWARDS, Format.INIT, 2L);
    }

    /**
     * 为玩家打开赛季结算奖励界面并推送结算奖励数据。
     *
     * @param player 目标玩家
     */
    public void openSettlement(Player player) {
        if (packetBridge == null || !canUse(player)) return;
        packetBridge.openUi(player, settlementUiId);
        requestRefresh(player, View.SETTLEMENT, Format.INIT, 2L);
    }

    private Map<String, Object> buildRewardsData(Player player, BattlePassPlayerProgress progress) {
        Map<String, Map<String, String>> levelsMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> rewardsMap = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();

        for (RewardLevel level : rewardLayout.levels()) {
            var entry = new LinkedHashMap<>(level.fields());
            entry.put("reached", String.valueOf(progress.currentLevel() >= level.level()));
            levelsMap.put(level.key(), entry);
        }
        for (RewardSlot slot : rewardLayout.slots()) {
            BattlePassReward reward = slot.reward();
            boolean tierUnlocked = switch (reward.tier()) {
                case FREE -> true;
                case PREMIUM -> progress.unlockedPremium();
                case DELUXE -> progress.unlockedDeluxe();
            };
            boolean levelReached = progress.currentLevel() >= reward.level();
            boolean claimed = progress.claimedRewards().contains(reward.rewardId());

            Map<String, String> entry = new LinkedHashMap<>(slot.fields());
            entry.put("claimed", String.valueOf(claimed));
            entry.put("claimable", String.valueOf(!claimed && levelReached && tierUnlocked));
            entry.put("locked", String.valueOf(!tierUnlocked));
            entry.put("levelReached", String.valueOf(levelReached));
            rewardsMap.put(slot.key(), entry);
            payload.put(slot.iconKey(), previewIcon(player, reward.icon()));
        }

        payload.put("packetId", PACKET_ID);
        payload.put("levels", levelsMap);
        payload.put("rewards", rewardsMap);
        payload.put("levelCount", String.valueOf(levelsMap.size()));
        payload.put("rewardCount", String.valueOf(rewardLayout.slots().size()));
        payload.put("currentLevel", String.valueOf(progress.currentLevel()));
        payload.put("action_token", actionTokens.getOrDefault(player.getUniqueId(), ""));

        return payload;
    }

    private record RewardLevel(String key, int level, Map<String, String> fields) {}
    private record RewardSlot(String key, String iconKey, BattlePassReward reward, Map<String, String> fields) {}

    private record RewardLayout(List<RewardLevel> levels, List<RewardSlot> slots) {
        static RewardLayout compile(List<BattlePassReward> rewards) {
            var ordered = rewards.stream().sorted(Comparator.comparingInt(BattlePassReward::level)
                .thenComparingInt(reward -> reward.tier().ordinal())).toList();
            var levels = new LinkedHashMap<Integer, Map<String, String>>();
            var slots = new ArrayList<RewardSlot>(ordered.size());
            for (BattlePassReward reward : ordered) {
                String key = Integer.toString(slots.size());
                String tier = reward.tier().name();
                String tierKey = tier.toLowerCase(Locale.ROOT);
                String actualLevel = Integer.toString(reward.level());
                var level = levels.computeIfAbsent(reward.level(), ignored -> new LinkedHashMap<>());
                level.put("level", actualLevel);
                level.put(tierKey + "EntryKey", key);
                slots.add(new RewardSlot(key, "iconJson" + key, reward, Map.of(
                    "level", actualLevel, "tier", tier, "tierDisplay", rewardTierDisplay(reward.tier()),
                    "tierKey", tierKey, "type", reward.type(), "itemAmount", Integer.toString(previewAmount(reward)))));
            }
            var result = new ArrayList<RewardLevel>(levels.size());
            levels.forEach((level, fields) -> result.add(new RewardLevel(
                Integer.toString(result.size() + 1), level, Map.copyOf(fields))));
            return new RewardLayout(List.copyOf(result), List.copyOf(slots));
        }
    }

    private String previewIcon(Player player, IconDefinition icon) {
        if (icon == null) return "";
        if (!blank(icon.source()) || !blank(icon.mmoType()) || !blank(icon.mmoId()) || !blank(icon.skullTexture())) {
            return iconResolver.resolveItemJson(player, icon);
        }
        String preview = staticIcons.computeIfAbsent(icon, definition -> {
            String resolved = iconResolver.resolveItemJson(null, definition);
            return resolved.isBlank() ? null : resolved;
        });
        return preview == null ? "" : preview;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 向玩家推送赛季结算奖励数据。
     * <p>
     * 数据包格式：
     * <pre>
     * packetId: AXS_BATTLEPASS
     * seasonName: 赛季名称
     * currentLevel: 玩家当前等级
     * premiumUnlocked: 是否已解锁高级版
     * deluxeUnlocked: 是否已解锁豪华版
     * settlementCount: 结算奖励条目数
     * settlements: { "0": { id, minLevel, reached, free: { type, name, claimed }, premium: {...}, deluxe: {...} }, ... }
     * iconJson0..iconJsonN: 各奖励的图标 JSON
     * </pre>
     */
    private Map<String, Object> buildSettlementData(Player player, BattlePassPlayerProgress progress) {
        var season = service.configuration().season();


        Map<String, Map<String, Object>> settlementsMap = new LinkedHashMap<>();
        Map<String, Object> payload = new LinkedHashMap<>();

        int idx = 0;
        for (SeasonSettlementReward settlement : orderedSettlements) {
            boolean reached = progress.currentLevel() >= settlement.minLevel();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", settlement.settlementId());
            entry.put("minLevel", String.valueOf(settlement.minLevel()));
            entry.put("reached", String.valueOf(reached));

            // 各档位奖励信息
            int iconIdx = 0;
            for (String tier : new String[]{"free", "premium", "deluxe"}) {
                BattlePassReward reward = switch (tier) {
                    case "free" -> settlement.free();
                    case "premium" -> settlement.premium();
                    case "deluxe" -> settlement.deluxe();
                    default -> null;
                };
                Map<String, String> tierEntry = new LinkedHashMap<>();
                if (reward != null) {
                    tierEntry.put("type", reward.type());
                    tierEntry.put("tierDisplay", rewardTierDisplay(reward.tier()));
                    tierEntry.put("present", "true");
                    // 推送奖励名称（取 icon.name，供 UI 显示奖励描述）
                    String displayName = "";
                    if (reward.icon() != null && reward.icon().name() != null) {
                        displayName = reward.icon().name();
                    }
                    int amount = previewAmount(reward);
                    tierEntry.put("name", displayName + (amount > 1 ? " x" + amount : ""));
                    // 推送图标
                    String iconJson = previewIcon(player, reward.icon());
                    payload.put("iconJson" + idx + "_" + iconIdx, iconJson);
                } else {
                    tierEntry.put("present", "false");
                    tierEntry.put("name", "");
                }
                entry.put(tier, tierEntry);
                iconIdx++;
            }

            settlementsMap.put(String.valueOf(idx), entry);
            idx++;
        }

        payload.put("packetId", PACKET_ID);
        payload.put("seasonName", season.displayName());
        payload.put("currentLevel", String.valueOf(progress.currentLevel()));
        payload.put("premiumUnlocked", String.valueOf(progress.unlockedPremium()));
        payload.put("deluxeUnlocked", String.valueOf(progress.unlockedDeluxe()));
        payload.put("settlements", settlementsMap);
        payload.put("settlementCount", String.valueOf(idx));

        return payload;
    }

    private static String rewardTierDisplay(BattlePassReward.RewardTier tier) {
        return switch (tier) {
            case FREE -> "&7免费";
            case PREMIUM -> "&6高级";
            case DELUXE -> "&5典藏";
        };
    }

    private Map<String, Object> buildMainData(BattlePassPlayerProgress progress) {
        var season = service.configuration().season();
        // 按玩家当前等级动态计算该级所需经验（成长曲线）
        int xpPerLevel = service.xpRequiredForLevelOr(progress.currentLevel() + 1, 0);
        int xpNeeded = Math.max(0, xpPerLevel - progress.currentXp());
        float ratio = xpPerLevel > 0 ? (float) progress.currentXp() / xpPerLevel : 0f;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", PACKET_ID);
        payload.put("seasonName", season.displayName());
        payload.put("currentLevel", String.valueOf(progress.currentLevel()));
        payload.put("maxLevel", String.valueOf(season.maxLevel()));
        payload.put("currentXp", String.valueOf(progress.currentXp()));
        payload.put("xpPerLevel", String.valueOf(xpPerLevel));
        payload.put("xpNeeded", String.valueOf(xpNeeded));
        payload.put("progressRatio", String.valueOf(ratio));
        payload.put("premiumUnlocked", String.valueOf(progress.unlockedPremium()));
        payload.put("deluxeUnlocked", String.valueOf(progress.unlockedDeluxe()));
        payload.put("tier", progress.passTier().name());
        payload.put("tierDisplay", tierDisplay(progress.passTier()));

        return payload;
    }

    private Map<String, Object> buildTasksData(List<PlayerTaskInstance> instances) {
        Map<String, Map<String, String>> dailyMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> weeklyMap = new LinkedHashMap<>();
        Map<String, Map<String, String>> seasonMap = new LinkedHashMap<>();

        int dIdx = 0, wIdx = 0, sIdx = 0;
        for (PlayerTaskInstance inst : instances) {
            BattlePassTask task = service.findTask(inst.templateId());
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("name", task != null ? task.displayName() : inst.templateId());
            entry.put("instanceId", inst.instanceId());
            entry.put("navigable", String.valueOf(task != null && task.navigation() != null && !inst.completed()));
            entry.put("description", task != null ? task.description() : "");
            entry.put("progress", String.valueOf(inst.currentProgress()));
            entry.put("required", String.valueOf(inst.targetCount()));
            entry.put("completed", String.valueOf(inst.completed()));
            entry.put("xpReward", String.valueOf(task != null ? task.totalXpReward() : 0));
            entry.put("difficulty", task != null ? task.difficulty().name() : "EASY");
            entry.put("difficultyDisplay", difficultyDisplay(task != null ? task.difficulty() : BattlePassTask.TaskDifficulty.EASY));
            entry.put("progressPercent", String.format("%.0f%%", inst.progressPercent()));
            double ratio = inst.targetCount() > 0
                ? (double) inst.currentProgress() / inst.targetCount() : 0.0;
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            entry.put("progressRatio", String.format(Locale.ROOT, "%.3f", ratio));
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
        payload.put("totalTaskCount", String.valueOf(dailyMap.size() + weeklyMap.size() + seasonMap.size()));
        payload.put("dailyCompletedCount", String.valueOf(dailyMap.values().stream().filter(e -> "true".equals(e.get("completed"))).count()));
        payload.put("weeklyCompletedCount", String.valueOf(weeklyMap.values().stream().filter(e -> "true".equals(e.get("completed"))).count()));
        payload.put("seasonCompletedCount", String.valueOf(seasonMap.values().stream().filter(e -> "true".equals(e.get("completed"))).count()));
        payload.put("maxDailyCount", String.valueOf(dailyMap.size()));
        payload.put("maxWeeklyCount", String.valueOf(weeklyMap.size()));
        payload.put("maxSeasonCount", String.valueOf(seasonMap.size()));

        return payload;
    }


    /**
     * 接收服务端进度变化通知，并合并延迟 UI 刷新。
     *
     * @param playerUuid 玩家 UUID
     * @param structureChanged 任务集合结构是否变化
     */
    public void onServiceRefresh(UUID playerUuid, boolean structureChanged) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return;
        requestRefresh(player, View.TASKS, structureChanged ? Format.UPDATE : Format.PROGRESS, 10L);
        requestRefresh(player, View.MAIN, Format.INIT, 10L);
    }

    /**
     * 清理玩家离线后的 UI 刷新状态。
     *
     * @param playerUuid 玩家 UUID
     */
    public void clearPendingRefresh(UUID playerUuid) {
        for (View view : View.values()) pendingRefreshes.remove(new RefreshKey(playerUuid, view));
        actionTokens.remove(playerUuid);
        actionTokensInFlight.remove(playerUuid);
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

    private static int previewAmount(BattlePassReward reward) {
        if (!"item".equalsIgnoreCase(reward.type()) && !"mail".equalsIgnoreCase(reward.type())) return 0;
        return reward.item() == null ? 0 : Math.max(1, reward.item().amount());
    }

    private enum View { MAIN, TASKS, REWARDS, SETTLEMENT }
    private enum Format { PROGRESS, UPDATE, INIT }
    private record RefreshKey(UUID player, View view) {}

    private static final class Refresh {
        final Player player;
        final AuthSnapshot session;
        Format pending;
        Format sending;
        int retries;

        Refresh(Player player, AuthSnapshot session, Format format) {
            this.player = player;
            this.session = session;
            this.pending = format;
        }
    }

    private void requestRefresh(Player player, View view, Format format, long delay) {
        if (packetBridge == null || !canUse(player) || !packetBridge.isUiOpen(player, uiId(view))) return;
        UUID id = player.getUniqueId();
        var session = AuthAccess.snapshot(id);
        if (!session.admitted()) return;
        var key = new RefreshKey(id, view);
        var candidate = new Refresh(player, session, format);
        var selected = pendingRefreshes.compute(key, (ignored, current) -> {
            if (current == null || current.player != player || !sameSession(current.session, session)) return candidate;
            current.pending = strongest(current.pending, format);
            return current;
        });
        if (selected == candidate) scheduleRefresh(key, candidate, delay);
    }

    private void scheduleRefresh(RefreshKey key, Refresh request, long delay) {
        try {
            Runnable action = () -> runRefresh(key, request);
            if (delay > 0) service.laterAsync(action, delay);
            else service.async(action);
        } catch (RuntimeException | LinkageError failure) {
            pendingRefreshes.remove(key, request);
        }
    }

    private void runRefresh(RefreshKey key, Refresh request) {
        if (!currentRefresh(key, request)) {
            pendingRefreshes.remove(key, request);
            return;
        }
        var selected = pendingRefreshes.computeIfPresent(key, (ignored, current) -> {
            if (current == request) {
                request.sending = request.pending;
                request.pending = null;
            }
            return current;
        });
        if (selected != request) return;
        boolean retry = false;
        try {
            Map<String, Object> payload = loadRefreshData(key.view(), request.player);
            if (!currentRefresh(key, request)) {
                pendingRefreshes.remove(key, request);
                return;
            }
            retry = payload == null;
            if (!retry) {
                String handler = key.view() == View.REWARDS ? "rewards" : request.sending.name().toLowerCase(Locale.ROOT);
                packetBridge.sendPacket(request.player, uiId(key.view()), handler, payload);
            }
        } finally {
            finishRefresh(key, request, retry);
        }
    }

    private Map<String, Object> loadRefreshData(View view, Player player) {
        UUID id = player.getUniqueId();
        if (view == View.TASKS) {
            if (service.isLoadFailed(id)) service.getProgress(player);
            if (service.isLoadFailed(id)) return null;
            var tasks = service.getTaskInstances(player);
            return service.isLoadFailed(id) ? null : buildTasksData(tasks);
        }
        var progress = service.getProgress(player);
        if (service.isLoadFailed(id)) return null;
        return switch (view) {
            case MAIN -> buildMainData(progress);
            case REWARDS -> buildRewardsData(player, progress);
            case SETTLEMENT -> buildSettlementData(player, progress);
            case TASKS -> throw new IllegalStateException("Tasks load their own data");
        };
    }

    private void finishRefresh(RefreshKey key, Refresh request, boolean retry) {
        var next = pendingRefreshes.computeIfPresent(key, (ignored, current) -> {
            if (current != request) return current;
            if (retry) {
                if (request.retries >= MAX_PUSH_RETRY) return null;
                request.retries++;
                request.pending = strongest(request.pending, request.sending);
            } else {
                request.retries = 0;
            }
            request.sending = null;
            return request.pending == null ? null : request;
        });
        if (next == request) scheduleRefresh(key, request, retry ? RETRY_INTERVAL_TICKS : 1L);
    }

    private boolean currentRefresh(RefreshKey key, Refresh request) {
        return pendingRefreshes.get(key) == request && canUse(request.player)
            && Bukkit.getPlayer(key.player()) == request.player
            && sameSession(request.session, AuthAccess.snapshot(key.player()))
            && packetBridge.isUiOpen(request.player, uiId(key.view()));
    }

    private static boolean sameSession(AuthSnapshot first, AuthSnapshot second) {
        return second.admitted() && first.generation() == second.generation()
            && Objects.equals(first.connectionId(), second.connectionId());
    }

    private static Format strongest(Format first, Format second) {
        return first == null || second.ordinal() > first.ordinal() ? second : first;
    }

    private String uiId(View view) {
        return switch (view) {
            case MAIN -> mainUiId;
            case TASKS -> tasksUiId;
            case REWARDS -> rewardsUiId;
            case SETTLEMENT -> settlementUiId;
        };
    }
}
