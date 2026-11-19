package xuanmo.arcartxsuite.fishing.packet;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.fishing.minigame.FishingMinigame;
import xuanmo.arcartxsuite.fishing.service.FishingService;

/**
 * 钓鱼模块客户端包处理器。
 * <p>
 * 处理客户端回包：input / open_collection / refresh_collection
 */
public final class FishingPacketHandler implements ClientPacketHandler {

    public static final String PACKET_ID = "AXS_FISHING";
    public static final String TALENTS_PACKET_ID = "AXS_FISHING_TALENTS";
    public static final String REWARDS_PACKET_ID = "AXS_FISHING_REWARDS";
    public static final String TASKS_PACKET_ID = "AXS_FISHING_TASKS";
    public static final String BAITS_PACKET_ID = "AXS_FISHING_BAITS";

    // 导航动作集合：所有页面共享的跨 UI 跳转动作
    private static final java.util.Set<String> NAV_ACTIONS = java.util.Set.of(
        "open_main", "open_collection", "open_rewards", "open_talents", "open_tasks", "open_baits"
    );

    private final java.util.function.Function<Player, FishingMinigame> minigameLookup;
    private final Supplier<FishingService> serviceSupplier;
    private final Supplier<String> mainUiIdSupplier;
    private final Supplier<String> collectionUiIdSupplier;
    private final Supplier<String> talentsUiIdSupplier;
    private final Supplier<String> rewardsUiIdSupplier;
    private final Supplier<String> tasksUiIdSupplier;
    private final Supplier<String> baitsUiIdSupplier;

    public FishingPacketHandler(java.util.function.Function<Player, FishingMinigame> minigameLookup,
                                Supplier<FishingService> serviceSupplier,
                                Supplier<String> collectionUiIdSupplier) {
        this(minigameLookup, serviceSupplier, null, collectionUiIdSupplier, null, null, null, null);
    }

    public FishingPacketHandler(java.util.function.Function<Player, FishingMinigame> minigameLookup,
                                Supplier<FishingService> serviceSupplier,
                                Supplier<String> collectionUiIdSupplier,
                                Supplier<String> talentsUiIdSupplier,
                                Supplier<String> rewardsUiIdSupplier,
                                Supplier<String> tasksUiIdSupplier) {
        this(minigameLookup, serviceSupplier, null, collectionUiIdSupplier, talentsUiIdSupplier, rewardsUiIdSupplier, tasksUiIdSupplier, null);
    }

    public FishingPacketHandler(java.util.function.Function<Player, FishingMinigame> minigameLookup,
                                Supplier<FishingService> serviceSupplier,
                                Supplier<String> mainUiIdSupplier,
                                Supplier<String> collectionUiIdSupplier,
                                Supplier<String> talentsUiIdSupplier,
                                Supplier<String> rewardsUiIdSupplier,
                                Supplier<String> tasksUiIdSupplier,
                                Supplier<String> baitsUiIdSupplier) {
        this.minigameLookup = minigameLookup;
        this.serviceSupplier = serviceSupplier;
        this.mainUiIdSupplier = mainUiIdSupplier;
        this.collectionUiIdSupplier = collectionUiIdSupplier;
        this.talentsUiIdSupplier = talentsUiIdSupplier;
        this.rewardsUiIdSupplier = rewardsUiIdSupplier;
        this.tasksUiIdSupplier = tasksUiIdSupplier;
        this.baitsUiIdSupplier = baitsUiIdSupplier;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
        // 先验证 packetId 是否属于本模块，避免拦截其他模块（如 battlepass）的同名导航动作
        boolean isFishingPacket = PACKET_ID.equalsIgnoreCase(packetId)
            || TALENTS_PACKET_ID.equalsIgnoreCase(packetId)
            || REWARDS_PACKET_ID.equalsIgnoreCase(packetId)
            || TASKS_PACKET_ID.equalsIgnoreCase(packetId)
            || BAITS_PACKET_ID.equalsIgnoreCase(packetId);
        if (!isFishingPacket) {
            return false;
        }
        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        // 导航动作：fishing 各页面共享的跨 UI 跳转（open_main/open_collection/...）
        if (NAV_ACTIONS.contains(action)) {
            return handleNavigation(player, action);
        }
        // 钓鱼小游戏包
        if (PACKET_ID.equalsIgnoreCase(packetId)) {
            return handleFishingPacket(player, data);
        }
        // 天赋树包
        if (TALENTS_PACKET_ID.equalsIgnoreCase(packetId)) {
            return handleTalentsPacket(player, data);
        }
        // 奖励包
        if (REWARDS_PACKET_ID.equalsIgnoreCase(packetId)) {
            return handleRewardsPacket(player, data);
        }
        // 任务包
        if (TASKS_PACKET_ID.equalsIgnoreCase(packetId)) {
            return handleTasksPacket(player, data);
        }
        // 饵料包
        if (BAITS_PACKET_ID.equalsIgnoreCase(packetId)) {
            return handleBaitsPacket(player, data);
        }
        return false;
    }

    /**
     * 处理跨 UI 导航动作：打开目标 UI 并推送对应数据。
     * 所有页面的左侧导航按钮都通过此方法跳转到其他页面。
     */
    private boolean handleNavigation(@NotNull Player player, @NotNull String action) {
        FishingService service = serviceSupplier != null ? serviceSupplier.get() : null;
        if (service == null) return true;
        switch (action) {
            case "open_main" -> {
                String uiId = mainUiIdSupplier != null ? mainUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.openUi(player, uiId);
                }
            }
            case "open_collection" -> {
                String uiId = collectionUiIdSupplier != null ? collectionUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.openUi(player, uiId);
                }
            }
            case "open_rewards" -> {
                String uiId = rewardsUiIdSupplier != null ? rewardsUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.openUi(player, uiId);
                }
            }
            case "open_talents" -> {
                String uiId = talentsUiIdSupplier != null ? talentsUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.openUi(player, uiId);
                }
            }
            case "open_tasks" -> {
                String uiId = tasksUiIdSupplier != null ? tasksUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.openUi(player, uiId);
                }
            }
            case "open_baits" -> {
                String uiId = baitsUiIdSupplier != null ? baitsUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.openUi(player, uiId);
                }
            }
        }
        return true;
    }

    private boolean handleFishingPacket(@NotNull Player player, @NotNull List<String> data) {
        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "input" -> {
                if (data.size() == 4) {
                    String pressingValue = data.get(3);
                    if (!"true".equalsIgnoreCase(pressingValue)
                        && !"false".equalsIgnoreCase(pressingValue)) {
                        break;
                    }
                    FishingMinigame minigame = minigameLookup.apply(player);
                    if (minigame != null && minigame.isReady() && !minigame.isFinished()) {
                        try {
                            // Aria 数字转为字符串时可能带 .0。
                            double parsed = Double.parseDouble(data.get(2));
                            int sequence = (int) parsed;
                            if (sequence == parsed) {
                                minigame.getSession().updateBarInput(data.get(1), sequence, Boolean.parseBoolean(pressingValue));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            case "open_collection", "refresh_collection" -> {
                FishingService service = serviceSupplier != null ? serviceSupplier.get() : null;
                String uiId = collectionUiIdSupplier != null ? collectionUiIdSupplier.get() : null;
                if (service != null && uiId != null && !uiId.isBlank()) {
                    service.pushCollectionData(player, uiId);
                }
            }
            case "open_main", "refresh_main" -> {
                FishingService service = serviceSupplier != null ? serviceSupplier.get() : null;
                String uiId = mainUiIdSupplier != null ? mainUiIdSupplier.get() : null;
                if (service != null && uiId != null && !uiId.isBlank()) {
                    service.pushMainData(player, uiId);
                }
            }
            case "open_minigame" -> {
                FishingMinigame minigame = minigameLookup.apply(player);
                if (minigame != null && !minigame.isFinished()) {
                    minigame.sendInitPacket(player);
                }
            }
            case "slacken" -> {
                FishingMinigame minigame = minigameLookup.apply(player);
                if (data.size() == 2 && minigame != null && minigame.isReady() && !minigame.isFinished()) {
                    minigame.getSession().requestSlacken(data.get(1));
                }
            }
            case "set_hook" -> {
                FishingMinigame minigame = minigameLookup.apply(player);
                if (data.size() == 3 && minigame != null && minigame.isReady() && !minigame.isFinished()) {
                    minigame.getSession().requestHookSet(data.get(1), data.get(2));
                }
            }
            case "give_up" -> {
                FishingMinigame minigame = minigameLookup.apply(player);
                if (minigame != null && !minigame.isFinished()) {
                    minigame.giveUp(player);
                }
            }
            default -> {
                // unknown action
            }
        }
        return true;
    }

    private boolean handleTalentsPacket(@NotNull Player player, @NotNull List<String> data) {
        FishingService service = serviceSupplier != null ? serviceSupplier.get() : null;
        if (service == null) return true;
        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "open_talents", "refresh_talents" -> {
                String uiId = talentsUiIdSupplier != null ? talentsUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.pushTalentsData(player, uiId);
                }
            }
            case "level_up_talent", "reset_talents" -> {
                // 写操作：data[1] 为 action_token，业务参数自 data[2] 起
                String uiId = talentsUiIdSupplier != null ? talentsUiIdSupplier.get() : null;
                String supplied = data.size() > 1 ? data.get(1) : "";
                String nodeId = data.size() > 2 ? data.get(2) : null;
                if (uiId != null && !uiId.isBlank()) service.submitTalentWrite(player, uiId, supplied,
                    action.equals("reset_talents"), nodeId);
            }
        }
        return true;
    }

    private boolean handleRewardsPacket(@NotNull Player player, @NotNull List<String> data) {
        FishingService service = serviceSupplier != null ? serviceSupplier.get() : null;
        if (service == null) return true;
        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "open_rewards", "refresh_rewards" -> {
                String uiId = rewardsUiIdSupplier != null ? rewardsUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.pushRewardsData(player, uiId);
                }
            }
            case "claim_reward" -> {
                // 写操作：data[1] 为 action_token，业务参数自 data[2] 起
                String uiId = rewardsUiIdSupplier != null ? rewardsUiIdSupplier.get() : null;
                String supplied = data.size() > 1 ? data.get(1) : "";
                if (uiId != null && !uiId.isBlank() && data.size() > 2) {
                    service.submitRewardClaim(player, uiId, supplied, data.get(2));
                }
            }
        }
        return true;
    }

    private boolean handleTasksPacket(@NotNull Player player, @NotNull List<String> data) {
        FishingService service = serviceSupplier != null ? serviceSupplier.get() : null;
        if (service == null) return true;
        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        if ("open_tasks".equals(action) || "refresh_tasks".equals(action)) {
            String uiId = tasksUiIdSupplier != null ? tasksUiIdSupplier.get() : null;
            if (uiId != null && !uiId.isBlank()) {
                service.pushTasksData(player, uiId);
            }
        }
        return true;
    }

    private boolean handleBaitsPacket(@NotNull Player player, @NotNull List<String> data) {
        FishingService service = serviceSupplier != null ? serviceSupplier.get() : null;
        if (service == null) return true;
        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        String uiId = baitsUiIdSupplier != null ? baitsUiIdSupplier.get() : null;
        switch (action) {
            case "open_baits", "refresh_baits" -> {
                if (uiId != null && !uiId.isBlank()) {
                    service.pushBaitsData(player, uiId);
                }
            }
            case "put_bait" -> {
                // data: [put_bait, token, invSlot, queueSlot, amount]
                if (uiId == null || uiId.isBlank() || data.size() < 5) return true;
                try {
                    service.submitBaitDeposit(player, uiId, data.get(1), Integer.parseInt(data.get(2)),
                        Integer.parseInt(data.get(3)), Integer.parseInt(data.get(4)));
                } catch (NumberFormatException ignored) { }
            }
            case "take_bait", "swap_bait" -> {
                if (uiId == null || uiId.isBlank() || data.size() < 4) return true;
                try {
                    int first = Integer.parseInt(data.get(2));
                    int second = Integer.parseInt(data.get(3));
                    if (action.equals("take_bait")) service.submitBaitWithdrawal(player, uiId, data.get(1), first, second);
                    else service.submitBaitSwap(player, uiId, data.get(1), first, second);
                } catch (NumberFormatException ignored) { }
            }
        }
        return true;
    }
}
