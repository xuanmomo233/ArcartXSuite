package xuanmo.arcartxsuite.title.service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;
import xuanmo.arcartxsuite.title.config.TitleSymphonyConfiguration;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

public final class TitleSymphonyService {

    private final JavaPlugin plugin;
    private final TitleSymphonyConfiguration configuration;
    private final SymphonyBridge bridge;

    /** playerUuid -> (display/collection_key -> sourceKey) */
    private final ConcurrentMap<UUID, ConcurrentMap<String, String>> activeSourceKeys = new ConcurrentHashMap<>();

    public TitleSymphonyService(JavaPlugin plugin, TitleSymphonyConfiguration configuration, SymphonyBridge bridge) {
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

        UUID playerUuid = player.getUniqueId();
        ConcurrentMap<String, String> currentKeys = activeSourceKeys.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());

        // 先移除旧属性
        for (String oldKey : currentKeys.values()) {
            bridge.removeAttribute(player, oldKey);
        }
        currentKeys.clear();

        // 设置 display 属性（flat）
        for (Map.Entry<String, Double> entry : resolvedState.displayAttributes().entrySet()) {
            String attributeId = entry.getKey();
            String sourceKey = configuration.displaySourceName() + "_" + sanitize(attributeId);
            bridge.setAttribute(player, attributeId, false, entry.getValue(), sourceKey);
            currentKeys.put("display_" + attributeId, sourceKey);
        }

        // 设置 collection 属性（flat）
        for (Map.Entry<String, Double> entry : resolvedState.collectionAttributes().entrySet()) {
            String attributeId = entry.getKey();
            String sourceKey = configuration.collectionSourceName() + "_" + sanitize(attributeId);
            bridge.setAttribute(player, attributeId, false, entry.getValue(), sourceKey);
            currentKeys.put("collection_" + attributeId, sourceKey);
        }

        if (!currentKeys.isEmpty()) {
            bridge.recalculate(player);
        }
    }

    public void clear(Player player) {
        if (!hooked() || player == null) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        ConcurrentMap<String, String> currentKeys = activeSourceKeys.remove(playerUuid);
        if (currentKeys == null || currentKeys.isEmpty()) {
            return;
        }

        for (String sourceKey : currentKeys.values()) {
            bridge.removeAttribute(player, sourceKey);
        }
        bridge.recalculate(player);
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim().replaceAll("[^A-Za-z0-9_\\-]", "_").toLowerCase(Locale.ROOT);
    }
}
