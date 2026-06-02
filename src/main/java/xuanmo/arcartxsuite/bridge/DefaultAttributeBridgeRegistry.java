package xuanmo.arcartxsuite.bridge;

import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.attribute.AttributePlusBridge;
import xuanmo.arcartxsuite.api.attribute.CraneAttributeBridge;
import xuanmo.arcartxsuite.api.attribute.MythicLibBridge;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;

/**
 * 宿主维护的全局属性桥接注册表单例。
 * 持有所有外部属性插件桥接，统一初始化和生命周期。
 */
public final class DefaultAttributeBridgeRegistry implements AttributeBridgeRegistry {

    private final DefaultAttributePlusBridge attributePlusBridge;
    private final DefaultCraneAttributeBridge craneAttributeBridge;
    private final DefaultMythicLibBridge mythicLibBridge;
    private final DefaultSymphonyBridge symphonyBridge;

    public DefaultAttributeBridgeRegistry(JavaPlugin plugin) {
        this.attributePlusBridge = new DefaultAttributePlusBridge(plugin);
        this.craneAttributeBridge = new DefaultCraneAttributeBridge(plugin);
        this.mythicLibBridge = new DefaultMythicLibBridge(plugin);
        this.symphonyBridge = new DefaultSymphonyBridge(plugin);
    }

    public void initialize() {
        attributePlusBridge.initialize();
        craneAttributeBridge.initialize();
        mythicLibBridge.initialize();
        symphonyBridge.initialize();
    }

    public void shutdown() {
        // 当前桥接无需特殊清理，保留扩展点
    }

    @Override public AttributePlusBridge attributePlus() { return attributePlusBridge; }
    @Override public CraneAttributeBridge craneAttribute() { return craneAttributeBridge; }
    @Override public MythicLibBridge mythicLib() { return mythicLibBridge; }
    @Override public SymphonyBridge symphony() { return symphonyBridge; }
}
