package xuanmo.arcartxsuite.prop.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

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

/**
 * Symphony 属性效果服务。
 * <p>
 * 通过 {@link SymphonyBridge} 为玩家添加临时属性修改，在持续时间到期后自动移除并重新计算。
 * 维护每个玩家当前活跃的属性来源键，支持手动清除。
 */
public final class PropSymphonyService {

    private final JavaPlugin plugin;
    private final SymphonyBridge bridge;

    private final ConcurrentMap<UUID, ConcurrentMap<String, String>> activeModifiers = new ConcurrentHashMap<>();

    /**
     * 构造 Symphony 属性服务。
     *
     * @param plugin 宿主插件实例
     * @param bridge Symphony 桥接
     */
    public PropSymphonyService(JavaPlugin plugin, SymphonyBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    /**
     * 关闭服务，移除全部在线玩家的活跃属性并重新计算。
     */
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

    /**
     * 检查 Symphony 桥接是否可用。
     *
     * @return {@code true} 表示已挂钩
     */
    public boolean hooked() {
        return bridge.available();
    }

    /**
     * 为玩家添加临时 Symphony 属性，持续指定时长后自动移除并重新计算。
     *
     * @param player         目标玩家
     * @param propId         道具 ID
     * @param effects        效果列表
     * @param durationSeconds 持续时间（秒）
     * @return {@code true} 表示至少成功添加一个属性
     */
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
            AxsScheduler.runTaskLater(plugin, () -> {
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

    /**
     * 清除指定玩家的全部活跃属性并重新计算。
     *
     * @param player 目标玩家，为 {@code null} 时直接返回
     */
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
