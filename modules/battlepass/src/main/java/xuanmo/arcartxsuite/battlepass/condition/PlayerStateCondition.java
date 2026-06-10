package xuanmo.arcartxsuite.battlepass.condition;

import java.util.Map;
import org.bukkit.entity.Player;

public final class PlayerStateCondition implements TaskCondition {

    private final String stateType;
    private final String stateId;

    public PlayerStateCondition(String stateType, String stateId) {
        this.stateType = stateType;
        this.stateId = stateId;
    }

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
            Class<?> chronosApi = Class.forName("priv.seventeen.artist.arcartx.chronos.api.ChronosAPI");
            Object api = chronosApi.getMethod("getInstanceAPI").invoke(null);
            String currentState = (String) chronosApi.getMethod("getPlayerStateId", Player.class).invoke(api, player);
            return stateId.equals(currentState);
        } catch (Exception e) {
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
