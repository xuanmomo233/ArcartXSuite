package xuanmo.arcartxsuite.title.service;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.CraneAttributeBridge;
import xuanmo.arcartxsuite.title.config.TitleCraneAttributeConfiguration;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

public final class TitleCraneAttributeService {

    private final JavaPlugin plugin;
    private final TitleCraneAttributeConfiguration configuration;
    private final CraneAttributeBridge bridge;

    public TitleCraneAttributeService(JavaPlugin plugin, TitleCraneAttributeConfiguration configuration, CraneAttributeBridge bridge) {
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
        if (bridge.supportsStaticSource()) {
            bridge.addStaticSource(attributeData, configuration.displaySourceName(), resolvedState.displayAttributeSourceLines());
            bridge.addStaticSource(attributeData, configuration.collectionSourceName(), resolvedState.collectionAttributeSourceLines());
        } else {
            bridge.addSource(attributeData, configuration.displaySourceName(), resolvedState.displayAttributeSourceLines());
            bridge.addSource(attributeData, configuration.collectionSourceName(), resolvedState.collectionAttributeSourceLines());
        }
        bridge.updateAttribute(player);
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
        bridge.updateAttribute(player);
    }
}

