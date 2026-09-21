package xuanmo.arcartxsuite.prop.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;

/**
 * AttributePlus 属性效果服务。
 * <p>
 * 通过 {@link AttributePlusBridge} 为玩家添加临时属性来源，并在持续时间到期后自动移除。
 * 维护每个玩家当前活跃的来源集合，支持手动清除。
 */
public final class PropAttributePlusService {

    private final JavaPlugin plugin;
    private final AttributePlusBridge bridge;
    private final Map<UUID, Set<String>> activeSources = new ConcurrentHashMap<>();

    /**
     * 构造 AttributePlus 属性服务。
     *
     * @param plugin 宿主插件实例
     * @param bridge AttributePlus 桥接
     */
    public PropAttributePlusService(JavaPlugin plugin, AttributePlusBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    /**
     * 检查 AttributePlus 桥接是否可用。
     *
     * @return {@code true} 表示已挂钩
     */
    public boolean hooked() {
        return bridge.available();
    }

    /**
     * 为玩家添加临时属性来源，持续指定时长后自动移除。
     *
     * @param player         目标玩家
     * @param propName       道具名称，用于生成来源 ID
     * @param attributeLines 属性行列表
     * @param durationSeconds 持续时间（秒）
     * @return {@code true} 表示成功添加
     */
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
        activeSources.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(sourceId);

        long removeDelayTicks = Math.max(1L, durationSeconds) * 20L;
        Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> removeSource(player.getUniqueId(), sourceId),
            removeDelayTicks
        );
        return true;
    }

    /**
     * 清除指定玩家的全部活跃属性来源。
     *
     * @param player 目标玩家，为 {@code null} 时直接返回
     */
    public void clear(Player player) {
        if (player == null) {
            return;
        }
        clear(player.getUniqueId(), player);
    }

    private void clear(UUID playerUuid, Player onlinePlayer) {
        Set<String> sourceIds = activeSources.remove(playerUuid);
        if (sourceIds == null || sourceIds.isEmpty() || !bridge.available()) {
            return;
        }
        Player player = onlinePlayer != null && onlinePlayer.isOnline() ? onlinePlayer : Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        Object attributeData = bridge.getAttrData(player);
        if (attributeData == null) {
            return;
        }
        for (String sourceId : sourceIds) {
            bridge.removeSource(attributeData, sourceId);
        }
    }

    private void removeSource(UUID playerUuid, String sourceId) {
        if (!bridge.available() || playerUuid == null || sourceId == null || sourceId.isBlank()) {
            return;
        }
        Set<String> sourceIds = activeSources.get(playerUuid);
        if (sourceIds != null) {
            sourceIds.remove(sourceId);
            if (sourceIds.isEmpty()) {
                activeSources.remove(playerUuid);
            }
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
