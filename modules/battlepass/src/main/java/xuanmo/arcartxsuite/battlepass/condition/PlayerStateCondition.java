package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * 基于玩家状态的任务条件。
 * <p>
 * 支持 chronos、region、world、permission、gamemode 和 world_environment 状态类型。
 */
public final class PlayerStateCondition implements TaskCondition {

    private static final String SUPPORTED_STATE_TYPES =
        "chronos, region, world, permission, gamemode, world_environment";

    private final String stateType;
    private final String stateId;
    private final boolean valid;

    /**
     * 构造玩家状态条件。
     *
     * @param stateType 状态类型
     * @param stateId 目标状态标识
     * @param logger 日志记录器
     * @param taskId 任务 ID
     */
    public PlayerStateCondition(
        String stateType, String stateId, Logger logger, String taskId
    ) {
        this.stateType = stateType == null ? "" : stateType;
        this.stateId = stateId == null ? "" : stateId;
        this.valid = switch (this.stateType) {
            case "chronos", "region", "world", "permission",
                "gamemode", "world_environment" -> true;
            default -> {
                if (logger != null) {
                    logger.warning("[battlepass] 任务 " + taskId
                        + " 包含未知玩家状态类型，支持类型: " + SUPPORTED_STATE_TYPES
                        + "，条件已忽略: " + this.stateType);
                }
                yield false;
            }
        };
    }

    /**
     * 使用无日志校验的方式构造玩家状态条件。
     *
     * @param stateType 状态类型
     * @param stateId 目标状态标识
     */
    public PlayerStateCondition(String stateType, String stateId) {
        this(stateType, stateId, null, "");
    }

    /**
     * {@inheritDoc}
     *
     * @param player 触发事件的玩家
     * @param payload 事件载荷
     * @return {@code true} 表示玩家当前状态匹配
     */
    @Override
    public boolean test(Player player, Map<String, String> payload) {
        if (!valid || player == null) return false;
        return switch (stateType) {
            case "chronos" -> testChronosState(player);
            case "region" -> testRegionState(payload);
            case "world" -> player.getWorld().getName().equals(stateId);
            case "permission" -> player.hasPermission(stateId);
            case "gamemode" -> player.getGameMode().name().equalsIgnoreCase(stateId);
            case "world_environment" ->
                player.getWorld().getEnvironment().name().equalsIgnoreCase(stateId);
            default -> false;
        };
    }

    private boolean testChronosState(Player player) {
        try {
            priv.seventeen.artist.arcartx.chronos.api.ChronosAPI api =
                priv.seventeen.artist.arcartx.chronos.api.ChronosAPI.getInstanceAPI();
            if (api == null) return false;
            return stateId.equals(api.getPlayerStateId(player));
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    private boolean testRegionState(Map<String, String> payload) {
        if (payload == null) return false;
        String regionId = payload.get("region_id");
        return regionId != null && regionId.equals(stateId);
    }

    /**
     * 返回条件类型。
     *
     * @return {@code player_state}
     */
    @Override
    public String type() {
        return "player_state";
    }

    /**
     * 返回状态类型。
     *
     * @return 状态类型
     */
    public String stateType() {
        return stateType;
    }

    /**
     * 返回状态标识。
     *
     * @return 状态标识
     */
    public String stateId() {
        return stateId;
    }
}
