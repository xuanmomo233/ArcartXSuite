package xuanmo.arcartxsuite.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 宿主薄壳与业务核心（Suite-core）之间的稳定契约。
 *
 * <p>本接口是宿主 jar 内唯一明文、版本化的耦合点：宿主薄壳只引用本接口与 JDK/Bukkit
 * 类型，因此 Paper 在验证宿主主类时不会触发加载任何加密业务类（根治
 * {@code NoClassDefFoundError: internal.*}）。真正的业务实现 {@code SuiteCoreImpl}
 * 被加密，待保护层（ProtectedClassLoader）就位后由宿主反射加载。
 *
 * <p>实现类约定：必须提供 public 无参构造器（供宿主反射实例化），随后宿主调用
 * {@link #onEnable(JavaPlugin)} 移交宿主引用并启动业务。
 */
public interface SuiteCore {

    /** 宿主当前支持的契约版本。Core 与宿主版本不一致时宿主拒绝加载。 */
    int HOST_API_VERSION = 1;

    /** Core 自身实现的契约版本，必须等于其构建时的 {@link #HOST_API_VERSION}。 */
    int apiVersion();

    /**
     * 由宿主在保护层就位后调用，移交宿主 {@link JavaPlugin} 引用并启动全部业务。
     *
     * @param host 宿主插件实例（Bukkit 注册的真实 JavaPlugin，用于调度/事件/资源）
     */
    void onEnable(JavaPlugin host);

    /** 由宿主在插件停用时调用，释放全部业务资源。 */
    void onDisable();
}
