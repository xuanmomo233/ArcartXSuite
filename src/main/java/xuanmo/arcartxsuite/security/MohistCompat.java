package xuanmo.arcartxsuite.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mohist 混合端兼容层。
 * <p>
 * Mohist 使用 Forge ModLauncher 与 Bukkit PluginClassLoader 混合的类加载体系，
 * 导致标准的 {@link JavaPlugin#saveResource(String, boolean)} 和
 * {@code Class.getClassLoader().getResourceAsStream(...)} 可能无法正确解析
 * 插件 JAR 内嵌的资源文件。
 * <p>
 * 本类提供多级回退策略，确保在所有 Bukkit 衍生服务端上均可正确加载资源。
 */
public final class MohistCompat {

    private static final Logger LOGGER = Logger.getLogger("AXS-MohistCompat");

    private static volatile Boolean mohistDetected;

    private MohistCompat() {}

    /**
     * 检测当前是否运行在 Mohist 服务端。
     */
    public static boolean isMohist() {
        if (mohistDetected == null) {
            synchronized (MohistCompat.class) {
                if (mohistDetected == null) {
                    mohistDetected = detectMohist();
                }
            }
        }
        return mohistDetected;
    }

    private static boolean detectMohist() {
        try {
            Class.forName("com.mohistmc.MohistMC");
            return true;
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("com.mohistmc.api.ServerAPI");
            return true;
        } catch (ClassNotFoundException ignored) {}
        String version = org.bukkit.Bukkit.getVersion();
        return version != null && version.toLowerCase().contains("mohist");
    }

    /**
     * 安全地从 ClassLoader 加载资源，兼容 Mohist 的类加载器问题。
     * <p>
     * 尝试顺序：
     * <ol>
     *   <li>指定的 classLoader</li>
     *   <li>线程上下文 ClassLoader</li>
     *   <li>MohistCompat 自身的 ClassLoader</li>
     *   <li>系统 ClassLoader</li>
     * </ol>
     *
     * @param path        资源路径
     * @param classLoader 首选的 ClassLoader（可为 null）
     * @return 资源的 InputStream，找不到返回 null
     */
    public static InputStream getResourceSafe(String path, ClassLoader classLoader) {
        // 策略 1：使用指定的 classLoader
        if (classLoader != null) {
            InputStream input = classLoader.getResourceAsStream(path);
            if (input != null) return input;
        }

        // 策略 2：线程上下文 ClassLoader
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null && contextLoader != classLoader) {
            InputStream input = contextLoader.getResourceAsStream(path);
            if (input != null) return input;
        }

        // 策略 3：本类的 ClassLoader（与宿主插件在同一个 JAR 中）
        ClassLoader selfLoader = MohistCompat.class.getClassLoader();
        if (selfLoader != classLoader && selfLoader != contextLoader) {
            InputStream input = selfLoader.getResourceAsStream(path);
            if (input != null) return input;
        }

        // 策略 4：系统 ClassLoader
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        if (systemLoader != selfLoader && systemLoader != classLoader) {
            InputStream input = systemLoader.getResourceAsStream(path);
            if (input != null) return input;
        }

        return null;
    }

    /**
     * 安全地释放资源到磁盘，兼容 Mohist。
     * <p>
     * 先尝试标准 {@link JavaPlugin#saveResource(String, boolean)}，
     * 失败后使用多级回退策略手动复制。
     *
     * @param plugin       插件实例
     * @param resourcePath JAR 内的资源路径
     * @param target       目标文件
     * @return 是否成功
     */
    public static boolean saveResourceSafe(JavaPlugin plugin, String resourcePath, File target) {
        // 策略 1：标准 Bukkit API
        try {
            if (!target.exists()) {
                plugin.saveResource(resourcePath, false);
            }
            if (target.exists()) return true;
        } catch (Exception exception) {
            if (isMohist()) {
                LOGGER.fine("Mohist 环境下 saveResource(\"" + resourcePath + "\") 失败: " + exception.getMessage());
            } else {
                LOGGER.log(Level.WARNING, "saveResource(\"" + resourcePath + "\") 失败", exception);
            }
        }

        // 策略 2：通过插件 ClassLoader 手动读取并写入
        ClassLoader pluginLoader = plugin.getClass().getClassLoader();
        InputStream input = getResourceSafe(resourcePath, pluginLoader);
        if (input != null) {
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException ioException) {
                LOGGER.warning("手动写出资源 \"" + resourcePath + "\" 失败: " + ioException.getMessage());
            } finally {
                try { input.close(); } catch (IOException ignored) {}
            }
        }

        // 策略 3：尝试从插件 JAR URL 直接读取
        try {
            URL jarUrl = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            if (jarUrl != null) {
                String jarPath = jarUrl.toExternalForm();
                if (jarPath.endsWith(".jar") || jarPath.endsWith(".jar!/")) {
                    String entryUrl = "jar:" + jarPath + "!/" + resourcePath;
                    URL resourceUrl = new URL(entryUrl);
                    URLConnection connection = resourceUrl.openConnection();
                    connection.setUseCaches(false);
                    try (InputStream jarInput = connection.getInputStream()) {
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }
                        Files.copy(jarInput, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        return true;
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.fine("通过 JAR URL 直接读取 \"" + resourcePath + "\" 失败: " + exception.getMessage());
        }

        LOGGER.severe("无法加载资源 \"" + resourcePath + "\"，所有策略均已失败" +
            (isMohist() ? "（Mohist 混合端环境）" : ""));
        return false;
    }

    /**
     * 获取插件的有效 ClassLoader（优先使用插件自身的 ClassLoader）。
     *
     * @param plugin 插件实例
     * @return 有效的 ClassLoader
     */
    public static ClassLoader effectiveClassLoader(JavaPlugin plugin) {
        if (plugin != null) {
            return plugin.getClass().getClassLoader();
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            return contextLoader;
        }
        return MohistCompat.class.getClassLoader();
    }
}
