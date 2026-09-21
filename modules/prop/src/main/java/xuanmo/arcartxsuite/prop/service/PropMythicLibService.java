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
import java.util.logging.Logger;

/**
 * MythicLib 属性效果服务。
 * <p>
 * 通过 {@link MythicLibBridge} 为玩家注册临时属性修改器，在持续时间到期后自动关闭。
 * 维护每个玩家当前活跃的修改器句柄，支持手动清除。未注册的属性会记录一次性警告。
 */
public final class PropMythicLibService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PropMythicLibConfiguration configuration;
    private final MythicLibBridge bridge;
    private final Set<String> warnedUnknownStats = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<String, Object>> activeModifiers = new ConcurrentHashMap<>();

    /**
     * 构造 MythicLib 属性服务。
     *
     * @param plugin       宿主插件实例
     * @param logger       日志记录器
     * @param configuration MythicLib 配置
     * @param bridge       MythicLib 桥接
     */
    public PropMythicLibService(JavaPlugin plugin,
        Logger logger, PropMythicLibConfiguration configuration, MythicLibBridge bridge) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.bridge = bridge;
    }

    /**
     * 启动服务，清空历史状态。
     */
    public void start() {
        warnedUnknownStats.clear();
        activeModifiers.clear();
    }

    /**
     * 检查 MythicLib 桥接是否可用。
     *
     * @return {@code true} 表示已挂钩
     */
    public boolean hooked() {
        return bridge.available();
    }

    /**
     * 关闭服务，清除全部玩家的活跃修改器与警告缓存。
     */
    public void shutdown() {
        for (UUID playerUuid : List.copyOf(activeModifiers.keySet())) {
            clear(playerUuid);
        }
        warnedUnknownStats.clear();
    }

    /**
     * 为玩家注册临时属性修改器，持续指定时长后自动关闭。
     * <p>
     * 仅处理已注册的属性，未注册的属性会被跳过并记录警告。
     *
     * @param player         目标玩家
     * @param propId         道具 ID
     * @param effects        效果列表
     * @param durationSeconds 持续时间（秒）
     * @return {@code true} 表示至少成功注册一个修改器
     */
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

    /**
     * 清除指定玩家的全部活跃修改器。
     *
     * @param player 目标玩家，为 {@code null} 时直接返回
     */
    public void clear(Player player) {
        if (player == null) {
            return;
        }
        clear(player.getUniqueId());
    }

    /**
     * 清除指定玩家的全部活跃修改器。
     *
     * @param playerUuid 玩家 UUID
     */
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
        this.logger.warning("Prop MythicLib 属性未注册，已跳过: " + statId);
    }
}


