package xuanmo.arcartxsuite.map.service;

import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.CrossServerConfiguration;
import xuanmo.arcartxsuite.map.crossserver.MapCrossServer;

final class MapCrossServerFactory {
    static MapCrossServer create(Plugin plugin, Logger logger, CrossServerConfiguration config,
                                 CrossServerAPI api, MapTerrainPushService terrain, MapService service) {
        return null;
    }
}
