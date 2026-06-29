/*
 * Decompiled with CFR 0.152.
 */
package ink.ptms.chemdah.taboolib.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

public class PrimitiveSettings {
    public static final String ID = "!taboolib".substring(1);
    public static final Properties RUNTIME_PROPERTIES = PrimitiveSettings.getProperties("env", true);
    public static final Properties VERSION_PROPERTIES = PrimitiveSettings.getProperties("version", false);
    public static final String KOTLIN_VERSION = VERSION_PROPERTIES.getProperty("!kotlin".substring(1), System.getProperty("ink.ptms.chemdah.taboolib.kotlin.stdlib", "1.8.22"));
    public static final String KOTLIN_COROUTINES_VERSION = VERSION_PROPERTIES.getProperty("!kotlin-coroutines".substring(1), System.getProperty("ink.ptms.chemdah.taboolib.kotlin.coroutines", "1.7.3"));
    public static final String TABOOLIB_VERSION = VERSION_PROPERTIES.getProperty(ID, System.getProperty("ink.ptms.chemdah.taboolib.version", "skip"));
    public static final boolean SKIP_KOTLIN_RELOCATE = VERSION_PROPERTIES.getProperty("skip-kotlin-relocate", System.getProperty("ink.ptms.chemdah.taboolib.skip-relocate.kotlin", "false")).equals("true");
    public static final boolean SKIP_TABOOLIB_RELOCATE = VERSION_PROPERTIES.getProperty("skip-taboolib-relocate", System.getProperty("ink.ptms.chemdah.taboolib.skip-relocate.self", "false")).equals("true");
    public static final boolean IS_DEV_MODE = TABOOLIB_VERSION.endsWith("-dev") || System.getProperty("ink.ptms.chemdah.taboolib.dev", "false").equals("true");
    public static final boolean IS_DEBUG_MODE = IS_DEV_MODE || RUNTIME_PROPERTIES.getProperty("debug", System.getProperty("ink.ptms.chemdah.taboolib.debug", "false")).equals("true");
    public static final boolean IS_FORCE_DOWNLOAD_IN_DEV_MODE = RUNTIME_PROPERTIES.getProperty("force-download-in-dev", "true").equals("true");
    public static final String REPO_CENTRAL = RUNTIME_PROPERTIES.getProperty("repo-central", System.getProperty("ink.ptms.chemdah.taboolib.repo.central", "https://maven.aliyun.com/repository/central"));
    public static final String REPO_TABOOLIB = RUNTIME_PROPERTIES.getProperty("repo-taboolib", System.getProperty("ink.ptms.chemdah.taboolib.repo.self", "http://sacredcraft.cn:8081/repository/releases"));
    public static final String REPO_REFLEX = RUNTIME_PROPERTIES.getProperty("repo-reflex", System.getProperty("ink.ptms.chemdah.taboolib.repo.reflex", "http://sacredcraft.cn:8081/repository/releases"));
    public static final String FILE_LIBS = RUNTIME_PROPERTIES.getProperty("file-libs", "libraries");
    public static final String FILE_ASSETS = RUNTIME_PROPERTIES.getProperty("file-assets", "assets");
    public static boolean IS_ISOLATED_MODE = RUNTIME_PROPERTIES.getProperty("enable-isolated-classloader", "false").equals("true");
    public static final String[] INSTALL_MODULES = RUNTIME_PROPERTIES.getProperty("module", System.getProperty("ink.ptms.chemdah.taboolib.module", "")).split(",");

    public static String formatVersion(String str) {
        return str.replaceAll("[._-]", "");
    }

    public static String getRelocatedKotlinVersion() {
        String kt = "!kotlin".substring(1);
        String kv = PrimitiveSettings.formatVersion(KOTLIN_VERSION);
        return kt + kv;
    }

    public static String getRelocatedKotlinCoroutinesVersion() {
        String kt = "!kotlin".substring(1);
        String kv = PrimitiveSettings.formatVersion(KOTLIN_VERSION);
        String kvc = PrimitiveSettings.formatVersion(KOTLIN_COROUTINES_VERSION);
        return kt + kv + "x.coroutines" + kvc;
    }

    private static Properties getProperties(String name, boolean allowGlobal) {
        File globalFile;
        Properties prop = new Properties();
        URL url = PrimitiveSettings.class.getClassLoader().getResource("META-INF/taboolib/" + name + ".properties");
        if (url != null) {
            try {
                prop.load(url.openStream());
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
        if (allowGlobal && (globalFile = new File(name + ".properties")).exists()) {
            try (FileInputStream fis = new FileInputStream(globalFile);){
                prop.load(fis);
            }
            catch (IOException iOException) {
                // empty catch block
            }
        }
        return prop;
    }
}

