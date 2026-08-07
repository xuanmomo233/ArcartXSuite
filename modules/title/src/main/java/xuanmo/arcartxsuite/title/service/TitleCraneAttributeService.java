package xuanmo.arcartxsuite.title.service;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.CraneAttributeBridge;
import xuanmo.arcartxsuite.title.config.TitleCraneAttributeConfiguration;
import xuanmo.arcartxsuite.title.model.ResolvedTitleState;

/**
 * CraneAttribute 属性同步服务。
 * <p>
 * 将解析后的展示属性与收集属性以属性源形式写入玩家的 CraneAttribute 数据，
 * 优先使用静态属性源（若桥接支持），每次同步先移除旧属性源再添加新属性源。
 * 属于 title 模块属性桥接层。
 */
public final class TitleCraneAttributeService {

    private final JavaPlugin plugin;
    private final TitleCraneAttributeConfiguration configuration;
    private final CraneAttributeBridge bridge;

    /**
     * @param plugin        插件实例
     * @param configuration CraneAttribute 桥接配置
     * @param bridge        CraneAttribute 桥接接口
     */
    public TitleCraneAttributeService(JavaPlugin plugin, TitleCraneAttributeConfiguration configuration, CraneAttributeBridge bridge) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.bridge = bridge;
    }

    /**
     * @return CraneAttribute 桥接是否可用
     */
    public boolean hooked() {
        return bridge.available();
    }

    /**
     * 同步玩家的展示属性与收集属性到 CraneAttribute。
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
        if (bridge.supportsStaticSource()) {
            bridge.addStaticSource(attributeData, configuration.displaySourceName(), resolvedState.displayAttributeSourceLines());
            bridge.addStaticSource(attributeData, configuration.collectionSourceName(), resolvedState.collectionAttributeSourceLines());
        } else {
            bridge.addSource(attributeData, configuration.displaySourceName(), resolvedState.displayAttributeSourceLines());
            bridge.addSource(attributeData, configuration.collectionSourceName(), resolvedState.collectionAttributeSourceLines());
        }
        bridge.updateAttribute(player);
    }

    /**
     * 清除玩家在 CraneAttribute 中的称号属性源。
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
        bridge.updateAttribute(player);
    }
}

