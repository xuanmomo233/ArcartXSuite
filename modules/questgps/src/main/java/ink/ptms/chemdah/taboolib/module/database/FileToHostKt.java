package ink.ptms.chemdah.taboolib.module.database;

import ink.ptms.chemdah.taboolib.library.configuration.ConfigurationSection;

/**
 * 编译期占位：运行时由 TabooLib 重定位的 module-database 提供。
 * 打包时通过 build.gradle.kts 排除，不会进入最终 JAR。
 */
public final class FileToHostKt {
    private FileToHostKt() {
    }

    public static HostSQL getHost(ConfigurationSection section, String node) {
        return null;
    }
}
