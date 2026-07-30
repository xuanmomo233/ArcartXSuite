package xuanmo.arcartxsuite.module;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 每个模块 Jar 独立使用的 ClassLoader。
 * <p>
 * parent 设置为宿主插件的 ClassLoader，因此模块可以访问：
 * <ul>
 *   <li>axs-api 中的接口（AXSModule、ModuleContext 等）</li>
 *   <li>Spigot API</li>
 *   <li>宿主插件中的 bridge 类</li>
 * </ul>
 * 模块之间默认隔离，通过 ModuleContext.getModule() 进行安全的跨模块通信。
 * <p>
 * 在混合端（Mohist/Arclight）上，parent 委托链可能将 Bukkit API 类错误地
 * 解析为 Forge 类。覆写 loadClass 确保服务端 API 类优先从服务端 ClassLoader 加载。
 */
public final class ModuleClassLoader extends URLClassLoader {

    private final String moduleId;
    private final ClassLoader serverClassLoader;

    public ModuleClassLoader(String moduleId, URL jarUrl, ClassLoader parent) {
        super(new URL[]{jarUrl}, parent);
        this.moduleId = moduleId;
        this.serverClassLoader = detectServerClassLoader(parent);
    }

    public String moduleId() {
        return moduleId;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 优先从服务端 ClassLoader 加载 Bukkit/Spigot/NMS 类，避免混合端 Forge 委托
        if (serverClassLoader != null && shouldPreferServerLoader(name)) {
            try {
                Class<?> clazz = serverClassLoader.loadClass(name);
                if (resolve) resolveClass(clazz);
                return clazz;
            } catch (ClassNotFoundException ignored) {
                // 回退到正常委托链
            }
        }
        return super.loadClass(name, resolve);
    }

    private static boolean shouldPreferServerLoader(String name) {
        return name.startsWith("org.bukkit.")
            || name.startsWith("org.spigotmc.")
            || name.startsWith("net.minecraft.")
            || name.startsWith("com.mojang.")
            || name.startsWith("io.papermc.");
    }

    private static ClassLoader detectServerClassLoader(ClassLoader parent) {
        // 从 parent 链中找到第一个能加载 org.bukkit.Server 的 ClassLoader
        ClassLoader loader = parent;
        while (loader != null) {
            try {
                loader.loadClass("org.bukkit.Server");
                return loader;
            } catch (ClassNotFoundException ignored) {
                loader = loader.getParent();
            }
        }
        // 回退到 Bukkit.getServer().getClass().getClassLoader()
        try {
            return org.bukkit.Bukkit.getServer().getClass().getClassLoader();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
