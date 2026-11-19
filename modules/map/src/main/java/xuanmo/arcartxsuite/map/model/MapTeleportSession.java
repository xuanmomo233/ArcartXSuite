package xuanmo.arcartxsuite.map.model;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;

/** Player and admission captured before a Map action can leave the server thread. */
public record MapTeleportSession(Player player, AuthSnapshot admission) {
    public static MapTeleportSession capture(Player player) {
        return new MapTeleportSession(player, AuthAccess.snapshot(player.getUniqueId()));
    }

    public boolean current() {
        if (!admission.admitted() || !player.isOnline() || Bukkit.getPlayer(admission.playerId()) != player) {
            return false;
        }
        AuthSnapshot current = AuthAccess.snapshot(admission.playerId());
        return current.admitted() && current.generation() == admission.generation()
            && Objects.equals(current.connectionId(), admission.connectionId());
    }
}
