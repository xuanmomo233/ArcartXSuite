package xuanmo.arcartxsuite.questgps.service;

import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI;
import xuanmo.arcartxsuite.api.util.NavigationMarkerService;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;

/**
 * QuestGPS 导航路径标记服务 — 委托给本体 NavigationMarkerService。
 * <p>
 * 保留此类作为 questgps 模块的入口，内部逻辑全部由 NavigationMarkerService 实现。
 */
public final class QuestGpsMarkerService {

    private static final String MARKER_ID_PREFIX = "questgps_path_";

    private final NavigationMarkerService delegate;

    public QuestGpsMarkerService(
        JavaPlugin plugin,
        AdyeshachNpcBridgeAPI npcBridge,
        QuestGpsModuleConfiguration.MarkerDefaults config,
        Logger logger,
        boolean debug
    ) {
        this.delegate = new NavigationMarkerService(
            plugin, npcBridge, config, logger, debug, MARKER_ID_PREFIX
        );
    }

    public void start() {
        delegate.start();
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public void showMarker(Player player, QuestGpsNavigationService.NavigationPoint point) {
        if (point == null) return;
        delegate.showMarker(player, point.world(), point.x(), point.y(), point.z());
    }

    public void hideMarker(Player player) {
        delegate.hideMarker(player);
    }

    public boolean isAvailable() {
        return delegate.isAvailable();
    }
}
