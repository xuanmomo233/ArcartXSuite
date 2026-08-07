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
import java.util.logging.Logger;

/**
 * MythicLib 属性同步服务。
 * <p>
 * 将解析后的展示属性与收集属性以属性修饰器形式注册到玩家的 MythicLib 数据，
 * 通过同步计划（{@link TitleMythicLibSyncPlan}）实现增量更新，
 * 仅对 MythicLib 已注册的属性键生效，未注册的属性键会记录警告。
 * 属于 title 模块属性桥接层。
 */
public final class TitleMythicLibService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final TitleMythicLibConfiguration configuration;
    private final MythicLibBridge bridge;
    private final Set<String> warnedUnknownStats = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SyncedState> syncedStates = new ConcurrentHashMap<>();

    /**
     * @param plugin        插件实例
     * @param logger        日志器
     * @param configuration MythicLib 桥接配置
     * @param bridge        MythicLib 桥接接口
     */
    public TitleMythicLibService(JavaPlugin plugin,
        Logger logger, TitleMythicLibConfiguration configuration, MythicLibBridge bridge) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.bridge = bridge;
    }

    /**
     * 启动服务，清空警告缓存与同步状态。
     */
    public void start() {
        warnedUnknownStats.clear();
        syncedStates.clear();
    }

    /**
     * 关闭服务，清空警告缓存与同步状态。
     */
    public void shutdown() {
        warnedUnknownStats.clear();
        syncedStates.clear();
    }

    /**
     * @return MythicLib 桥接是否可用
     */
    public boolean hooked() {
        return bridge.available();
    }

    /**
     * 同步玩家的展示属性与收集属性到 MythicLib。
     *
     * @param player       目标玩家
     * @param resolvedState 解析后的称号状态
     */
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

    /**
     * 清除玩家在 MythicLib 中的全部称号属性修饰器。
     *
     * @param player 目标玩家
     */
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
        this.logger.warning("Title MythicLib 属性未注册，已跳过: " + rawKey + " -> " + statId);
    }

    private record SyncedState(Set<String> displayStats, Set<String> collectionStats) {
        private static final SyncedState EMPTY = new SyncedState(Set.of(), Set.of());
    }
}



