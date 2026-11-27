package xuanmo.arcartxsuite.regions.listener;

import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.service.RegionManager.Decision;

final class RegionFlight {
    private RegionFlight() {}

    static boolean enforce(Player player, Decision decision, RegionsConfiguration config, boolean attempted) {
        if (decision.state() != RegionFlag.State.DENY) return false;
        player.setFlying(false);
        Region source = decision.source();
        String message = source != null && source.kind() == Region.Kind.GLOBAL
            ? config.notifications().flightDenied(source.world())
            : attempted ? config.messages().noPermissionRegion() : "";
        if (!message.isBlank()) player.sendMessage(config.messages().format(message));
        return true;
    }
}
