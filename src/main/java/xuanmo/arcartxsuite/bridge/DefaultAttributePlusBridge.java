package xuanmo.arcartxsuite.bridge;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.serverct.ersha.api.AttributeAPI;
import org.serverct.ersha.attribute.data.AttributeData;
import org.serverct.ersha.attribute.data.AttributeSource;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;

/**
 * {@link AttributePlusBridge} 的默认实现，桥接 AttributePlus 属性插件。
 * <p>
 * AttributePlus 为可选依赖，运行时通过插件管理器检测可用性。
 */
public final class DefaultAttributePlusBridge implements AttributePlusBridge {

    private static final String PLUGIN_NAME = "AttributePlus";

    private final JavaPlugin plugin;

    private boolean available;

    public DefaultAttributePlusBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();

        Plugin attributePlus = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (attributePlus == null || !attributePlus.isEnabled()) {
            return;
        }

        try {
            available = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[AttributePlus] 桥接初始化成功");
        } catch (RuntimeException | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributePlus] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public Object getAttrData(Player player) {
        if (!available || player == null) {
            return null;
        }
        try {
            return AttributeAPI.getAttrData(player);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @Override
    public Object getAttributeSource(List<String> lines) {
        if (!available || lines == null || lines.isEmpty()) {
            return null;
        }
        try {
            return AttributeAPI.getAttributeSource(List.copyOf(lines));
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @Override
    public void addSource(Object attrData, String sourceId, Object attributeSource) {
        if (!(attrData instanceof AttributeData data) || !(attributeSource instanceof AttributeSource source) || sourceId == null) {
            return;
        }
        try {
            AttributeAPI.addSourceAttribute(data, sourceId, source);
        } catch (RuntimeException | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributePlus] addSource 失败: " + exception.getMessage());
        }
    }

    @Override
    public void addSourceLines(Object attrData, String sourceName, List<String> lines) {
        if (!(attrData instanceof AttributeData data) || lines == null || lines.isEmpty()) {
            return;
        }
        try {
            AttributeAPI.addSourceAttribute(data, sourceName, List.copyOf(lines), false);
        } catch (RuntimeException | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributePlus] addSourceLines 失败: " + exception.getMessage());
        }
    }

    @Override
    public void removeSource(Object attrData, String sourceId) {
        if (!(attrData instanceof AttributeData data) || sourceId == null) {
            return;
        }
        try {
            AttributeAPI.takeSourceAttribute(data, sourceId);
        } catch (RuntimeException | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributePlus] removeSource 失败: " + exception.getMessage());
        }
    }

    @Override
    public void updateAttribute(Player player, Object attrData) {
        if (!available || player == null) {
            return;
        }
        try {
            AttributeAPI.updateAttribute(player);
        } catch (RuntimeException | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[AttributePlus] updateAttribute 失败: " + exception.getMessage());
        }
    }

    private void reset() {
        available = false;
    }
}
