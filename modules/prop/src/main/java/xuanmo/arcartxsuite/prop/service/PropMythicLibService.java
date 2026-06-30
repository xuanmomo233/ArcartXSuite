package xuanmo.arcartxsuite.prop.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.MythicLibBridge;
import xuanmo.arcartxsuite.prop.config.PropMythicLibConfiguration;
import xuanmo.arcartxsuite.module.AxsLog;

public final class PropMythicLibService {

    private final JavaPlugin plugin;
    private final PropMythicLibConfiguration configuration;
    private final MythicLibBridge bridge;
    private final Set<String> warnedUnknownStats = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<String, Object>> activeModifiers = new ConcurrentHashMap<>();

    public PropMythicLibService(JavaPlugin plugin, PropMythicLibConfiguration configuration, MythicLibBridge bridge) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.bridge = bridge;
    }

    public void start() {
        warnedUnknownStats.clear();
        activeModifiers.clear();
    }

    public boolean hooked() {
        return bridge.available();
    }

    public void shutdown() {
        for (UUID playerUuid : List.copyOf(activeModifiers.keySet())) {
            clear(playerUuid);
        }
        warnedUnknownStats.clear();
    }

    public boolean apply(Player player, String propId, List<PropMythicLibEffect> effects, int durationSeconds) {
        if (!hooked() || player == null || effects == null || effects.isEmpty()) {
            return false;
        }

        List<PropMythicLibEffect> supportedEffects = resolveKnownEffects(effects);
        if (supportedEffects.isEmpty()) {
            return false;
        }

        List<PropMythicLibModifierSpec> modifiers = PropMythicLibModifierPlanner.plan(
            configuration,
            propId,
            supportedEffects,
            durationSeconds
        );
        if (modifiers.isEmpty()) {
            return false;
        }

        Object playerData = bridge.getPlayerData(player);
        if (playerData == null) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        Map<String, Object> modifiersByKey = activeModifiers.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
        boolean applied = false;
        for (PropMythicLibModifierSpec modifier : modifiers) {
            Object existing = modifiersByKey.remove(modifier.key());
            if (existing != null) {
                bridge.closeTemporaryModifier(existing);
            }

            Object handle = bridge.registerTemporaryModifier(playerData, modifier.modifierName(), modifier.statId(), modifier.value(), modifier.durationMillis());
            if (handle != null) {
                modifiersByKey.put(modifier.key(), handle);
                scheduleCleanup(playerUuid, modifier, handle);
                applied = true;
            }
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
        clear(player.getUniqueId());
    }

    public void clear(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        Map<String, Object> removed = activeModifiers.remove(playerUuid);
        if (removed == null || removed.isEmpty()) {
            return;
        }
        for (Object handle : removed.values()) {
            bridge.closeTemporaryModifier(handle);
        }
    }

    private List<PropMythicLibEffect> resolveKnownEffects(List<PropMythicLibEffect> effects) {
        List<PropMythicLibEffect> supported = new ArrayList<>();
        for (PropMythicLibEffect effect : effects) {
            if (!bridge.isRegisteredStat(effect.statId())) {
                warnUnknownStat(effect.statId());
                continue;
            }
            supported.add(effect);
        }
        return List.copyOf(supported);
    }

    private void scheduleCleanup(UUID playerUuid, PropMythicLibModifierSpec modifier, Object handle) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Map<String, Object> modifiersByKey = activeModifiers.get(playerUuid);
            if (modifiersByKey == null) {
                return;
            }
            Object current = modifiersByKey.get(modifier.key());
            if (current == null || current != handle) {
                return;
            }
            modifiersByKey.remove(modifier.key());
            bridge.closeTemporaryModifier(handle);
            if (modifiersByKey.isEmpty()) {
                activeModifiers.remove(playerUuid);
            }
        }, modifier.durationTicks());
    }

    private void warnUnknownStat(String statId) {
        if (!warnedUnknownStats.add(statId)) {
            return;
        }
        AxsLog.logger().warning("Prop MythicLib 属性未注册，已跳过: " + statId);
    }
}
