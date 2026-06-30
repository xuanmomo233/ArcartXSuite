package xuanmo.arcartxsuite.title.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.MythicLibBridge;
import xuanmo.arcartxsuite.api.mythiclib.MythicLibStatKeyNormalizer;
import xuanmo.arcartxsuite.title.config.TitleMythicLibConfiguration;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;
import xuanmo.arcartxsuite.module.AxsLog;

public final class TitleMythicLibService {

    private final JavaPlugin plugin;
    private final TitleMythicLibConfiguration configuration;
    private final MythicLibBridge bridge;
    private final Set<String> warnedUnknownStats = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SyncedState> syncedStates = new ConcurrentHashMap<>();

    public TitleMythicLibService(JavaPlugin plugin, TitleMythicLibConfiguration configuration, MythicLibBridge bridge) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.bridge = bridge;
    }

    public void start() {
        warnedUnknownStats.clear();
        syncedStates.clear();
    }

    public void shutdown() {
        warnedUnknownStats.clear();
        syncedStates.clear();
    }

    public boolean hooked() {
        return bridge.available();
    }

    public void sync(Player player, ResolvedTitleState resolvedState) {
        if (!hooked() || player == null || resolvedState == null) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        LinkedHashMap<String, Double> displayStats = resolveKnownStats(resolvedState.displayAttributes());
        LinkedHashMap<String, Double> collectionStats = resolveKnownStats(resolvedState.collectionAttributes());
        SyncedState previousState = syncedStates.getOrDefault(playerUuid, SyncedState.EMPTY);
        TitleMythicLibSyncPlan plan = TitleMythicLibSyncPlanner.plan(
            configuration,
            displayStats,
            collectionStats,
            previousState.displayStats(),
            previousState.collectionStats()
        );
        if (!applyPlan(player, plan)) {
            return;
        }
        if (plan.displayStats().isEmpty() && plan.collectionStats().isEmpty()) {
            syncedStates.remove(playerUuid);
            return;
        }
        syncedStates.put(playerUuid, new SyncedState(plan.displayStats(), plan.collectionStats()));
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        SyncedState previousState = syncedStates.remove(player.getUniqueId());
        if (!hooked() || previousState == null) {
            return;
        }

        TitleMythicLibSyncPlan plan = TitleMythicLibSyncPlanner.plan(
            configuration,
            Map.of(),
            Map.of(),
            previousState.displayStats(),
            previousState.collectionStats()
        );
        applyPlan(player, plan);
    }

    private boolean applyPlan(Player player, TitleMythicLibSyncPlan plan) {
        if (plan.touchedStats().isEmpty()) {
            return true;
        }

        Object playerData = bridge.getPlayerData(player);
        if (playerData == null) {
            return false;
        }
        Object statMap = bridge.getStatMap(playerData);
        if (statMap == null) {
            return false;
        }

        for (String statId : plan.displayTouchedStats()) {
            bridge.removeStatModifier(statMap, statId, configuration.displayModifierName(statId));
        }
        for (String statId : plan.collectionTouchedStats()) {
            bridge.removeStatModifier(statMap, statId, configuration.collectionModifierName(statId));
        }
        for (TitleMythicLibModifierSpec modifier : plan.displayModifiers()) {
            bridge.registerStatModifier(playerData, modifier.modifierName(), modifier.statId(), modifier.value());
        }
        for (TitleMythicLibModifierSpec modifier : plan.collectionModifiers()) {
            bridge.registerStatModifier(playerData, modifier.modifierName(), modifier.statId(), modifier.value());
        }
        for (String statId : plan.touchedStats()) {
            bridge.updateStat(statMap, statId);
        }
        return true;
    }

    private LinkedHashMap<String, Double> resolveKnownStats(Map<String, Double> rawValues) {
        LinkedHashMap<String, Double> resolved = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return resolved;
        }

        for (Map.Entry<String, Double> entry : rawValues.entrySet()) {
            String statId = MythicLibStatKeyNormalizer.normalize(entry.getKey());
            if (statId.isBlank()) {
                continue;
            }
            if (!bridge.isRegisteredStat(statId)) {
                warnUnknownStat(entry.getKey(), statId);
                continue;
            }
            resolved.merge(statId, entry.getValue(), Double::sum);
        }
        return resolved;
    }

    private void warnUnknownStat(String rawKey, String statId) {
        if (!warnedUnknownStats.add(statId)) {
            return;
        }
        AxsLog.logger().warning("Title MythicLib 属性未注册，已跳过: " + rawKey + " -> " + statId);
    }

    private record SyncedState(Set<String> displayStats, Set<String> collectionStats) {
        private static final SyncedState EMPTY = new SyncedState(Set.of(), Set.of());
    }
}

