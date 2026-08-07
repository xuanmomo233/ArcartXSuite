package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * 基于玩家状态的任务条件，实现 {@link TaskCondition}。
 * <p>
 * 支持三种状态类型：chronos（Chronos 模块状态）、region（区域 ID）、world（世界名），
 * 通过外部模块 API 或事件 payload 判定玩家当前状态是否匹配预期。
 */
public final class PlayerStateCondition implements TaskCondition {

    private final String stateType;
    private final String stateId;

    /**
     * 构造玩家状态条件。
     *
     * @param stateType 状态类型（chronos/region/world）
     * @param stateId   目标状态标识
     */
    public PlayerStateCondition(String stateType, String stateId) {
        this.stateType = stateType;
        this.stateId = stateId;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 根据 stateType 分别调用 Chronos API、读取 payload 中的 region_id 或比较世界名。
     *
     * @param player  触发事件的玩家
     * @param payload 事件载荷（region 类型时读取 region_id）
     * @return {@code true} 表示玩家当前状态匹配
     */
    @Override
    public boolean test(Player player, Map<String, String> payload) {
        if (player == null) return false;
        return switch (stateType) {
            case "chronos" -> testChronosState(player);
            case "region" -> testRegionState(player, payload);
            case "world" -> player.getWorld().getName().equals(stateId);
            default -> false;
        };
    }

    private boolean testChronosState(Player player) {
        try {
            priv.seventeen.artist.arcartx.chronos.api.ChronosAPI api =
                priv.seventeen.artist.arcartx.chronos.api.ChronosAPI.getInstanceAPI();
            if (api == null) {
                return false;
            }
            String currentState = api.getPlayerStateId(player);
            return stateId.equals(currentState);
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    private boolean testRegionState(Player player, Map<String, String> payload) {
        if (payload == null) return false;
        String regionId = payload.get("region_id");
        return regionId != null && regionId.equals(stateId);
    }

    @Override
    public String type() {
        return "player_state";
    }

    public String stateType() { return stateType; }
    public String stateId() { return stateId; }
}
