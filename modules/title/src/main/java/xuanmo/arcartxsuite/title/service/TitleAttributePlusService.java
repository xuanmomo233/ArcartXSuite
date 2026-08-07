package xuanmo.arcartxsuite.title.service;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;
import xuanmo.arcartxsuite.title.config.TitleAttributePlusConfiguration;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

/**
 * AttributePlus 属性同步服务。
 * <p>
 * 将解析后的展示属性与收集属性以属性源形式写入玩家的 AttributePlus 数据，
 * 每次同步先移除旧属性源再添加新属性源，确保属性值正确更新。
 * 属于 title 模块属性桥接层。
 */
public final class TitleAttributePlusService {

    private final JavaPlugin plugin;
    private final TitleAttributePlusConfiguration configuration;
    private final AttributePlusBridge bridge;

    /**
     * @param plugin        插件实例
     * @param configuration AttributePlus 桥接配置
     * @param bridge        AttributePlus 桥接接口
     */
    public TitleAttributePlusService(JavaPlugin plugin, TitleAttributePlusConfiguration configuration, AttributePlusBridge bridge) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.bridge = bridge;
    }

    /**
     * @return AttributePlus 桥接是否可用
     */
    public boolean hooked() {
        return bridge.available();
    }

    /**
     * 同步玩家的展示属性与收集属性到 AttributePlus。
     *
     * @param player       目标玩家
     * @param resolvedState 解析后的称号状态
     */
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
        bridge.addSourceLines(attributeData, configuration.displaySourceName(), resolvedState.displayAttributeSourceLines());
        bridge.addSourceLines(attributeData, configuration.collectionSourceName(), resolvedState.collectionAttributeSourceLines());
        bridge.updateAttribute(player, attributeData);
    }

    /**
     * 清除玩家在 AttributePlus 中的称号属性源。
     *
     * @param player 目标玩家
     */
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
        bridge.updateAttribute(player, attributeData);
    }
}

