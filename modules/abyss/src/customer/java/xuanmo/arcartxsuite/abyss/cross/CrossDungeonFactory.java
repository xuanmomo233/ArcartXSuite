package xuanmo.arcartxsuite.abyss.cross;

import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;

public final class CrossDungeonFactory {
    private CrossDungeonFactory() {}

    public static CrossDungeon create(AbyssService service, CrossServerAPI crossServer, Plugin plugin, Logger logger) {
        return new CrossDungeonManager(service, crossServer, plugin, logger);
    }
}
