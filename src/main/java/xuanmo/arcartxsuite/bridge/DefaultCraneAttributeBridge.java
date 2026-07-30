package xuanmo.arcartxsuite.bridge;

import cn.org.bukkit.craneattribute.api.AttributeAPI;
import cn.org.bukkit.craneattribute.api.attribute.data.AttributeData;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.CraneAttributeBridge;

public final class DefaultCraneAttributeBridge implements CraneAttributeBridge {

    private static final String PLUGIN_NAME = "CraneAttribute";

    private final JavaPlugin plugin;

    private boolean available;

    public DefaultCraneAttributeBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();

        Plugin craneAttribute = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (craneAttribute == null || !craneAttribute.isEnabled()) {
            return;
        }

        try {
            available = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[CraneAttribute] 桥接初始化成功");
        } catch (RuntimeException | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[CraneAttribute] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override public boolean available() { return available; }

    @Override
    public Object getAttrData(Player player) {
        if (!available || player == null) return null;
        try { return AttributeAPI.getAttrData(player); } catch (RuntimeException | LinkageError e) { return null; }
    }

    @Override
    public void addSource(Object attrData, String sourceName, List<String> lines) {
        if (!(attrData instanceof AttributeData data) || lines == null || lines.isEmpty()) return;
        try {
            AttributeAPI.addAttributeSource(data, sourceName, List.copyOf(lines));
        } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[CraneAttribute] addSource 失败: " + e.getMessage());
        }
    }

    @Override
    public void addStaticSource(Object attrData, String sourceName, List<String> lines) {
        if (!(attrData instanceof AttributeData data) || lines == null || lines.isEmpty()) return;
        try {
            AttributeAPI.addStaticAttributeSource(data, sourceName, List.copyOf(lines));
        } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[CraneAttribute] addStaticSource 失败: " + e.getMessage());
        }
    }

    @Override public boolean supportsStaticSource() { return true; }

    @Override
    public void removeSource(Object attrData, String sourceName) {
        if (!(attrData instanceof AttributeData data) || sourceName == null) return;
        try { AttributeAPI.removeAttributeSource(data, sourceName); } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[CraneAttribute] removeSource 失败: " + e.getMessage());
        }
    }

    @Override
    public void updateAttribute(Player player) {
        if (!available || player == null) return;
        try { AttributeAPI.updateAttribute(player); } catch (RuntimeException | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[CraneAttribute] updateAttribute 失败: " + e.getMessage());
        }
    }

    private void reset() {
        available = false;
    }
}
