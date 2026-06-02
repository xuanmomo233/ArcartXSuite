package xuanmo.arcartxsuite.prop.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;

public final class PropSymphonyService {

    private final JavaPlugin plugin;
    private final SymphonyBridge bridge;

    private final ConcurrentMap<UUID, ConcurrentMap<String, String>> activeModifiers = new ConcurrentHashMap<>();

    public PropSymphonyService(JavaPlugin plugin, SymphonyBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void shutdown() {
        for (Map.Entry<UUID, ConcurrentMap<String, String>> entry : activeModifiers.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                for (String sourceKey : entry.getValue().values()) {
                    bridge.removeAttribute(player, sourceKey);
                }
                bridge.recalculate(player);
            }
        }
        activeModifiers.clear();
    }

    public boolean hooked() {
        return bridge.available();
    }

    public boolean apply(Player player, String propId, List<PropSymphonyEffect> effects, int durationSeconds) {
        if (!bridge.available() || player == null || effects == null || effects.isEmpty()) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        ConcurrentMap<String, String> modifiersByKey = activeModifiers.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
        boolean applied = false;

        for (PropSymphonyEffect effect : effects) {
            String sourceKey = "AXS_PROP_" + sanitizeSegment(propId) + "_" + sanitizeSegment(effect.attributeId());

            String existingKey = modifiersByKey.remove(sourceKey);
            if (existingKey != null) {
                bridge.removeAttribute(player, existingKey);
            }

            bridge.setAttribute(player, effect.attributeId(), effect.percent(), effect.value(), sourceKey);
            modifiersByKey.put(sourceKey, sourceKey);
            applied = true;

            long removeDelayTicks = Math.max(1L, durationSeconds) * 20L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ConcurrentMap<String, String> current = activeModifiers.get(playerUuid);
                if (current != null && current.remove(sourceKey) != null) {
                    Player online = Bukkit.getPlayer(playerUuid);
                    if (online != null && online.isOnline()) {
                        bridge.removeAttribute(online, sourceKey);
                        bridge.recalculate(online);
                    }
                    if (current.isEmpty()) {
                        activeModifiers.remove(playerUuid);
                    }
                }
            }, removeDelayTicks);
        }

        if (applied) {
            bridge.recalculate(player);
        }

        if (modifiersByKey.isEmpty()) {
            activeModifiers.remove(playerUuid);
        }
        return applied;
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        ConcurrentMap<String, String> modifiers = activeModifiers.remove(player.getUniqueId());
        if (modifiers == null || modifiers.isEmpty()) {
            return;
        }
        for (String sourceKey : modifiers.values()) {
            bridge.removeAttribute(player, sourceKey);
        }
        bridge.recalculate(player);
    }

    private static String sanitizeSegment(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "unknown";
        }
        return rawValue
            .trim()
            .replaceAll("[^A-Za-z0-9_\\-]", "_")
            .toLowerCase(Locale.ROOT);
    }
}
