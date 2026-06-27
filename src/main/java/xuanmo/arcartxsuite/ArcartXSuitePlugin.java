package xuanmo.arcartxsuite;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.bootstrap.SuiteCore;
import xuanmo.arcartxsuite.security.protection.ProtectionInit;

/**
 * ArcartXSuite 宿主薄壳（加载器框架，几乎不再变）。
 * <p>
 * 本类是 Paper 注册的插件主类，只承担三件事：
 * <ol>
 *   <li>在静态初始化块（最早时机，早于构造器/onEnable）注入保护层 ProtectedClassLoader；</li>
 *   <li>onEnable 时反射加载已被解密的业务核心 {@code SuiteCoreImpl}（实现 {@link SuiteCore}）；</li>
 *   <li>把宿主引用移交业务核心，并在 onDisable 时回收。</li>
 * </ol>
 * <p>
 * 关键：本类<strong>只引用</strong> {@link SuiteCore} 契约接口与 JDK/Bukkit/保护层引导类，
 * 不直接引用任何加密业务类。因此 Paper 验证/链接本主类时不会触发加载加密类，
 * 根治了 {@code NoClassDefFoundError: xuanmo/arcartxsuite/internal/*}（解密器尚未安装即被引用）。
 */
public class ArcartXSuitePlugin extends JavaPlugin {

    static {
        // 方案 ②：在主类静态初始化（最早时机，早于构造器与 onLoad/onEnable）注入保护层，
        // 用 Unsafe 把 ProtectedClassLoader 设为 PluginClassLoader 的 parent，
        // 确保后续任何加密类被加载前 hook 已就位。明文 JAR 下自动降级为兼容模式。
        try {
            ProtectionInit.initialize(ArcartXSuitePlugin.class);
        } catch (Throwable t) {
            java.util.logging.Logger.getLogger("AXS-Protection")
                .severe("[Protection] 静态初始化注入异常: " + t);
        }
    }

    /** 业务核心（加密交付，运行时由保护层解密后反射加载）。 */
    private SuiteCore core;

    @Override
    public void onEnable() {
        try {
            // 此时静态块已安装 ProtectedClassLoader，加密类可被解密加载。
            Class<?> implClass = Class.forName(
                "xuanmo.arcartxsuite.SuiteCoreImpl", true, getClassLoader());
            SuiteCore loaded = (SuiteCore) implClass.getDeclaredConstructor().newInstance();
            if (loaded.apiVersion() != SuiteCore.HOST_API_VERSION) {
                getLogger().severe("Suite-core 契约版本不兼容: 宿主=" + SuiteCore.HOST_API_VERSION
                    + " 核心=" + loaded.apiVersion() + "，已禁用插件。");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            this.core = loaded;
            core.onEnable(this);
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "业务核心 SuiteCore 加载失败，已禁用插件。", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (core != null) {
            try {
                core.onDisable();
            } catch (Throwable t) {
                getLogger().log(Level.SEVERE, "业务核心 SuiteCore 卸载异常。", t);
            }
            core = null;
        }
        // 关闭保护子系统（引导层职责）
        try {
            ProtectionInit.shutdown();
        } catch (Throwable ignored) {
        }
    }
}
