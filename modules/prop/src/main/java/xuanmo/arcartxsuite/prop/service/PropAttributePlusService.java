package xuanmo.arcartxsuite.prop.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;

public final class PropAttributePlusService {

    private final JavaPlugin plugin;
    private final AttributePlusBridge bridge;

    public PropAttributePlusService(JavaPlugin plugin, AttributePlusBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public boolean hooked() {
        return bridge.available();
    }

    public boolean apply(Player player, String propName, List<String> attributeLines, int durationSeconds) {
        if (!bridge.available() || player == null || attributeLines == null || attributeLines.isEmpty()) {
            return false;
        }

        Object attributeData = bridge.getAttrData(player);
        if (attributeData == null) {
            return false;
        }
        Object attributeSource = bridge.getAttributeSource(List.copyOf(attributeLines));
        if (attributeSource == null) {
            return false;
        }
        String sourceId = "ArcartXProp_" + sanitizeSourceName(propName);
        bridge.addSource(attributeData, sourceId, attributeSource);

        long removeDelayTicks = Math.max(1L, durationSeconds) * 20L;
        Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> removeSource(player.getUniqueId(), sourceId),
            removeDelayTicks
        );
        return true;
    }

    private void removeSource(UUID playerUuid, String sourceId) {
        if (!bridge.available() || playerUuid == null || sourceId == null || sourceId.isBlank()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        Object attributeData = bridge.getAttrData(player);
        if (attributeData == null) {
            return;
        }
        bridge.removeSource(attributeData, sourceId);
    }

    private static String sanitizeSourceName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "unknown";
        }
        return rawName
            .trim()
            .replaceAll("[^A-Za-z0-9_\\-]", "_")
            .toLowerCase(Locale.ROOT);
    }
}
