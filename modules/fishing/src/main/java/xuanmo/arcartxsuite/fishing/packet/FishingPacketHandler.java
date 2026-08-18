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

    private final java.util.function.Function<Player, FishingMinigame> minigameLookup;
    private final Supplier<FishingService> serviceSupplier;
    private final Supplier<String> collectionUiIdSupplier;
    private final Supplier<String> talentsUiIdSupplier;
    private final Supplier<String> rewardsUiIdSupplier;
    private final Supplier<String> tasksUiIdSupplier;

    public FishingPacketHandler(java.util.function.Function<Player, FishingMinigame> minigameLookup,
                                Supplier<FishingService> serviceSupplier,
                                Supplier<String> collectionUiIdSupplier) {
        this(minigameLookup, serviceSupplier, collectionUiIdSupplier, null, null, null);
    }

    public FishingPacketHandler(java.util.function.Function<Player, FishingMinigame> minigameLookup,
                                Supplier<FishingService> serviceSupplier,
                                Supplier<String> collectionUiIdSupplier,
                                Supplier<String> talentsUiIdSupplier,
                                Supplier<String> rewardsUiIdSupplier,
                                Supplier<String> tasksUiIdSupplier) {
        this.minigameLookup = minigameLookup;
        this.serviceSupplier = serviceSupplier;
        this.collectionUiIdSupplier = collectionUiIdSupplier;
        this.talentsUiIdSupplier = talentsUiIdSupplier;
        this.rewardsUiIdSupplier = rewardsUiIdSupplier;
        this.tasksUiIdSupplier = tasksUiIdSupplier;
    }

    @Override
    public boolean handleClientPacket(@NotNull Player player, @NotNull String packetId, @NotNull List<String> data) {
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
        return false;
    }

    private boolean handleFishingPacket(@NotNull Player player, @NotNull List<String> data) {
        String action = data.isEmpty() ? "" : data.get(0).toLowerCase(Locale.ROOT);
        switch (action) {
            case "input" -> {
                if (data.size() > 1) {
                    String pressingValue = data.get(1);
                    if (!"true".equalsIgnoreCase(pressingValue)
                        && !"false".equalsIgnoreCase(pressingValue)) {
                        break;
                    }
                    boolean pressing = Boolean.parseBoolean(pressingValue);
                    FishingMinigame minigame = minigameLookup.apply(player);
                    if (minigame != null && !minigame.isFinished()) {
                        minigame.getSession().setPressing(pressing);
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
            case "open_minigame" -> {
                FishingMinigame minigame = minigameLookup.apply(player);
                if (minigame != null && !minigame.isFinished()) {
                    minigame.sendInitPacket(player);
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
            case "unlock_talent", "reset_talents" -> {
                service.handleUiAction(player, action, data);
                // 操作后刷新数据
                String uiId = talentsUiIdSupplier != null ? talentsUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.pushTalentsData(player, uiId);
                }
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
                service.handleUiAction(player, action, data);
                String uiId = rewardsUiIdSupplier != null ? rewardsUiIdSupplier.get() : null;
                if (uiId != null && !uiId.isBlank()) {
                    service.pushRewardsData(player, uiId);
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
}
