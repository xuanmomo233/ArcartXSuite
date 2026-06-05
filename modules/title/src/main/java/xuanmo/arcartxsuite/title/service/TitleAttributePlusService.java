package xuanmo.arcartxsuite.title.service;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;
import xuanmo.arcartxsuite.title.config.TitleAttributePlusConfiguration;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

public final class TitleAttributePlusService {

    private final JavaPlugin plugin;
    private final TitleAttributePlusConfiguration configuration;
    private final AttributePlusBridge bridge;

    public TitleAttributePlusService(JavaPlugin plugin, TitleAttributePlusConfiguration configuration, AttributePlusBridge bridge) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.bridge = bridge;
    }

    public boolean hooked() {
        return bridge.available();
    }

    public void sync(Player player, ResolvedTitleState resolvedState) {
        if (!hooked() || player == null || resolvedState == null) {
            return;
        }

        Object attributeData = bridge.getAttrData(player);
        if (attributeData == null) {
            return;
        }

        bridge.removeSource(attributeData, configuration.displaySourceName());
        bridge.removeSource(attributeData, configuration.collectionSourceName());
        bridge.addSourceLines(attributeData, configuration.displaySourceName(), resolvedState.displayAttributeSourceLines());
        bridge.addSourceLines(attributeData, configuration.collectionSourceName(), resolvedState.collectionAttributeSourceLines());
        bridge.updateAttribute(player, attributeData);
    }

    public void clear(Player player) {
        if (!hooked() || player == null) {
            return;
        }

        Object attributeData = bridge.getAttrData(player);
        if (attributeData == null) {
            return;
        }

        bridge.removeSource(attributeData, configuration.displaySourceName());
        bridge.removeSource(attributeData, configuration.collectionSourceName());
        bridge.updateAttribute(player, attributeData);
    }
}

