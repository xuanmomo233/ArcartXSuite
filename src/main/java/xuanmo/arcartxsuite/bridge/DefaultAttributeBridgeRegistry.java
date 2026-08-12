package xuanmo.arcartxsuite.bridge;

import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.attribute.AttributeBridgeRegistry;
import xuanmo.arcartxsuite.api.attribute.AttributeDamageListener;
import xuanmo.arcartxsuite.api.attribute.AttributeHealListener;
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
    private final AttributeDamageDispatcher damageDispatcher;

    private boolean attributePlusEnabled = true;
    private boolean craneAttributeEnabled = true;
    private boolean mythicLibEnabled = true;
    private boolean symphonyEnabled = true;

    public DefaultAttributeBridgeRegistry(JavaPlugin plugin) {
        this.attributePlusBridge = safeCreate(() -> new DefaultAttributePlusBridge(plugin), "AttributePlus");
        this.craneAttributeBridge = safeCreate(() -> new DefaultCraneAttributeBridge(plugin), "CraneAttribute");
        this.mythicLibBridge = safeCreate(() -> new DefaultMythicLibBridge(plugin), "MythicLib");
        this.symphonyBridge = safeCreate(() -> new DefaultSymphonyBridge(plugin), "Symphony");
        this.damageDispatcher = safeCreate(() -> new AttributeDamageDispatcher(plugin), "AttributeDamageDispatcher");
    }

    private <T> T safeCreate(java.util.function.Supplier<T> supplier, String name) {
        try {
            return supplier.get();
        } catch (NoClassDefFoundError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(name + " 桥接类加载失败（插件未安装?）: " + e.getMessage());
            return null;
        }
    }

    /**
     * 设置各属性插件是否启用。由 {@code ModuleRegistry} 在初始化时
     * 根据 {@code config.yml} 中的 {@code attribute-bridges} 节调用。
     */
    public void setSourceEnabled(boolean attributePlus, boolean craneAttribute, boolean mythicLib, boolean symphony) {
        this.attributePlusEnabled = attributePlus;
        this.craneAttributeEnabled = craneAttribute;
        this.mythicLibEnabled = mythicLib;
        this.symphonyEnabled = symphony;
    }

    public void initialize() {
        if (attributePlusEnabled && attributePlusBridge != null) attributePlusBridge.initialize();
        if (craneAttributeEnabled && craneAttributeBridge != null) craneAttributeBridge.initialize();
        if (mythicLibEnabled && mythicLibBridge != null) mythicLibBridge.initialize();
        if (symphonyEnabled && symphonyBridge != null) symphonyBridge.initialize();
        if (damageDispatcher != null) {
            damageDispatcher.initialize(attributePlusEnabled, craneAttributeEnabled, mythicLibEnabled, symphonyEnabled);
        }
    }

    public void shutdown() {
        if (damageDispatcher != null) damageDispatcher.shutdown();
    }

    @Override public AttributePlusBridge attributePlus() { return attributePlusBridge; }
    @Override public CraneAttributeBridge craneAttribute() { return craneAttributeBridge; }
    @Override public MythicLibBridge mythicLib() { return mythicLibBridge; }
    @Override public SymphonyBridge symphony() { return symphonyBridge; }

    @Override
    public boolean hasDamageSource() {
        return (attributePlusBridge != null && attributePlusBridge.available())
            || (craneAttributeBridge != null && craneAttributeBridge.available())
            || (mythicLibBridge != null && mythicLibBridge.available())
            || (symphonyBridge != null && symphonyBridge.available());
    }

    @Override
    public boolean hasHealSource() {
        return (craneAttributeBridge != null && craneAttributeBridge.available());
    }

    @Override
    public void registerHealListener(AttributeHealListener listener) {
        if (damageDispatcher != null) damageDispatcher.registerHealListener(listener);
    }

    @Override
    public void unregisterHealListener(AttributeHealListener listener) {
        if (damageDispatcher != null) damageDispatcher.unregisterHealListener(listener);
    }

    @Override
    public void registerDamageListener(AttributeDamageListener listener) {
        if (damageDispatcher != null) damageDispatcher.registerListener(listener);
    }

    @Override
    public void unregisterDamageListener(AttributeDamageListener listener) {
        if (damageDispatcher != null) damageDispatcher.unregisterListener(listener);
    }
}
