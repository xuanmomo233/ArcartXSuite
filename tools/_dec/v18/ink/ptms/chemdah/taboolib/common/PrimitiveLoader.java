/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.lucko.jarrelocator.JarRelocator
 *  me.lucko.jarrelocator.Relocation
 */
package ink.ptms.chemdah.taboolib.common;

import ink.ptms.chemdah.taboolib.common.ClassAppender;
import ink.ptms.chemdah.taboolib.common.PrimitiveIO;
import ink.ptms.chemdah.taboolib.common.PrimitiveSettings;
import ink.ptms.chemdah.taboolib.common.TabooLib;
import ink.ptms.chemdah.taboolib.common.classloader.IsolatedClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;

public class PrimitiveLoader {
    public static final String TABOOLIB_GROUP = "!io.izzel.taboolib".substring(1);
    public static final String TABOOLIB_PACKAGE_NAME = "ink.ptms.chemdah.taboolib";
    public static final String TABOOPROJECT_GROUP = "!org.tabooproject".substring(1);
    public static final String ASM_GROUP = "!org.objectweb.asm".substring(1);
    public static final String JR_GROUP = "!me.lucko.jarrelocator".substring(1);
    static String projectPackageName;

    static List<String[]> deps() {
        ArrayList<String[]> deps = new ArrayList<String[]>();
        deps.add(new String[]{"me.lucko", "jar-relocator", "1.7"});
        deps.add(new String[]{"org.ow2.asm", "asm", "9.6"});
        deps.add(new String[]{"org.ow2.asm", "asm-util", "9.6"});
        deps.add(new String[]{"org.ow2.asm", "asm-commons", "9.6"});
        return deps;
    }

    static List<String[]> rule() {
        ArrayList<String[]> rule = new ArrayList<String[]>();
        rule.add(new String[]{TABOOPROJECT_GROUP, "ink.ptms.chemdah.taboolib.library"});
        rule.add(new String[]{JR_GROUP + ".", JR_GROUP + "15."});
        rule.add(new String[]{ASM_GROUP + ".", ASM_GROUP + "9."});
        if (!PrimitiveSettings.SKIP_TABOOLIB_RELOCATE) {
            rule.add(new String[]{PrimitiveSettings.ID, TABOOLIB_PACKAGE_NAME});
        }
        return rule;
    }

    public static void init() throws Throwable {
        PrimitiveIO.debug("Running in development mode.", PrimitiveIO.getRunningFileName());
        long time = TabooLib.execution(() -> {
            boolean isIsolated = PrimitiveLoader.class.getClassLoader() instanceof IsolatedClassLoader;
            for (String[] i : PrimitiveLoader.deps()) {
                PrimitiveLoader.load(PrimitiveSettings.REPO_CENTRAL, i[0], i[1], i[2], isIsolated, true, new ArrayList<String[]>());
            }
            for (String[] i : PrimitiveLoader.deps()) {
                PrimitiveLoader.load(PrimitiveSettings.REPO_CENTRAL, i[0], i[1], i[2], PrimitiveSettings.IS_ISOLATED_MODE, true, PrimitiveLoader.rule());
            }
            PrimitiveLoader.load(PrimitiveSettings.REPO_REFLEX, TABOOPROJECT_GROUP + ".reflex", "reflex", "1.1.7", PrimitiveSettings.IS_ISOLATED_MODE, true, PrimitiveLoader.rule());
            PrimitiveLoader.load(PrimitiveSettings.REPO_REFLEX, TABOOPROJECT_GROUP + ".reflex", "analyser", "1.1.7", PrimitiveSettings.IS_ISOLATED_MODE, true, PrimitiveLoader.rule());
        });
        PrimitiveIO.debug("Base dependencies loaded in {0} ms.", time);
        PrimitiveLoader.loadAll();
    }

    static boolean load(String repo, String group2, String name, String version, boolean isIsolated, boolean isExternal, List<String[]> relocate) {
        File shaFile;
        if (name.isEmpty()) {
            return false;
        }
        boolean downloaded = false;
        File envFile = new File(PrimitiveLoader.getLibraryFile(), String.format("%s/%s/%s/%s-%s.jar", group2.replace(".", "/"), name, version, name, version));
        if (!PrimitiveIO.validation(envFile, shaFile = new File(PrimitiveLoader.getLibraryFile(), String.format("%s/%s/%s/%s-%s.jar.sha1", group2.replace(".", "/"), name, version, name, version))) || PrimitiveSettings.IS_FORCE_DOWNLOAD_IN_DEV_MODE && PrimitiveSettings.IS_DEV_MODE && group2.equals(TABOOLIB_GROUP)) {
            try {
                PrimitiveIO.println(PrimitiveIO.t("\u6b63\u5728\u4e0b\u8f7d\u4f9d\u8d56 {0}:{1}:{2}", "Downloading library {0}:{1}:{2}"), group2, name, version);
                String url = String.format("%s/%s/%s/%s/%s-%s.jar", repo, group2.replace(".", "/"), name, version, name, version);
                PrimitiveIO.downloadFile(new URL(url), envFile);
                PrimitiveIO.downloadFile(new URL(url + ".sha1"), shaFile);
                downloaded = true;
            }
            catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            if (!PrimitiveIO.validation(envFile, shaFile)) {
                PrimitiveIO.println(PrimitiveIO.t("\u65e0\u6cd5\u4e0b\u8f7d {0}-{1}.jar", "Failed to download {0}-{1}.jar"), name, version);
                return false;
            }
        }
        try {
            PrimitiveLoader.loadFile(envFile, isIsolated, isExternal, relocate, downloaded);
        }
        catch (Throwable ex) {
            throw new RuntimeException(PrimitiveIO.t("\u65e0\u6cd5\u52a0\u8f7d " + envFile, "Failed to load " + envFile), ex);
        }
        return true;
    }

    static void loadAll() throws Throwable {
        if (PrimitiveSettings.TABOOLIB_VERSION.equals("skip")) {
            PrimitiveIO.println(PrimitiveIO.t("TabooLib \u7248\u672c\u6ca1\u6709\u5b9a\u4e49\uff0c\u5c06\u8df3\u8fc7\u52a0\u8f7d\u3002", "TabooLib version is not specified, skip loading."), new Object[0]);
            return;
        }
        long time = TabooLib.execution(() -> {
            List<String[]> rule = PrimitiveLoader.rule();
            PrimitiveLoader.load(PrimitiveSettings.REPO_TABOOLIB, TABOOLIB_GROUP, "common-env", PrimitiveSettings.TABOOLIB_VERSION, PrimitiveSettings.IS_ISOLATED_MODE, true, rule);
            if (!TabooLib.isKotlinEnvironment()) {
                String kotlinClass = "kotlin1822.Lazy";
                throw new IllegalStateException(PrimitiveIO.t("\u65e0\u6cd5\u542f\u52a8 Kotlin \u73af\u5883\u3002(\u672a\u80fd\u627e\u5230 " + kotlinClass + ")", "Failed to setup Kotlin environment. (" + kotlinClass + " not found)"));
            }
            PrimitiveLoader.load(PrimitiveSettings.REPO_TABOOLIB, TABOOLIB_GROUP, "common-util", PrimitiveSettings.TABOOLIB_VERSION, PrimitiveSettings.IS_ISOLATED_MODE, true, rule);
            PrimitiveLoader.load(PrimitiveSettings.REPO_TABOOLIB, TABOOLIB_GROUP, "common-legacy-api", PrimitiveSettings.TABOOLIB_VERSION, PrimitiveSettings.IS_ISOLATED_MODE, false, rule);
            PrimitiveLoader.load(PrimitiveSettings.REPO_TABOOLIB, TABOOLIB_GROUP, "common-platform-api", PrimitiveSettings.TABOOLIB_VERSION, PrimitiveSettings.IS_ISOLATED_MODE, false, rule);
            for (String i : PrimitiveSettings.INSTALL_MODULES) {
                PrimitiveLoader.load(PrimitiveSettings.REPO_TABOOLIB, TABOOLIB_GROUP, i, PrimitiveSettings.TABOOLIB_VERSION, PrimitiveSettings.IS_ISOLATED_MODE, false, rule);
            }
        });
        PrimitiveIO.debug("All dependencies loaded in {0} ms.", time);
    }

    static void loadFile(File file, boolean isIsolated, boolean isExternal, List<String[]> relocate, boolean forceRelocate) throws Throwable {
        File jar = file;
        if (!relocate.isEmpty()) {
            ArrayList<Relocation> rel = new ArrayList<Relocation>();
            for (String[] r : relocate) {
                rel.add(new Relocation(r[0], r[1]));
            }
            if (!PrimitiveSettings.SKIP_KOTLIN_RELOCATE) {
                String kt = "!kotlin".substring(1);
                String ktc = "!kotlinx.coroutines".substring(1);
                rel.add(new Relocation(kt + ".", PrimitiveSettings.getRelocatedKotlinVersion() + "."));
                rel.add(new Relocation(ktc + ".", PrimitiveSettings.getRelocatedKotlinCoroutinesVersion() + "."));
            }
            String hash = PrimitiveIO.getHash(file.getName() + PrimitiveLoader.deepHashCode(relocate) + PrimitiveSettings.KOTLIN_VERSION + PrimitiveSettings.KOTLIN_COROUTINES_VERSION);
            String name = file.getName().substring(0, file.getName().lastIndexOf(46));
            jar = new File(PrimitiveLoader.getCacheFile(), name + "-" + hash.substring(0, 8) + ".jar");
            if (!jar.exists() && jar.length() == 0L || PrimitiveSettings.IS_FORCE_DOWNLOAD_IN_DEV_MODE && PrimitiveSettings.IS_DEV_MODE || forceRelocate) {
                jar.getParentFile().mkdirs();
                try {
                    new JarRelocator(PrimitiveIO.copyFile(file, File.createTempFile(file.getName(), ".jar")), jar, rel).run();
                }
                catch (Throwable e) {
                    throw new RuntimeException(PrimitiveIO.t("\u65e0\u6cd5\u91cd\u5b9a\u5411 " + file, "Failed to relocate " + file), e);
                }
            }
        }
        ClassLoader loader = ClassAppender.addPath(jar.toPath(), isIsolated, isExternal);
        try (JarFile jarFile = new JarFile(jar);){
            JarEntry extra = jarFile.getJarEntry("META-INF/taboolib/extra.properties");
            if (extra != null) {
                PrimitiveIO.debug("Loading extra properties from " + jar.getName(), new Object[0]);
                Properties extraProps = new Properties();
                extraProps.load(jarFile.getInputStream(extra));
                String main2 = extraProps.getProperty("main");
                String mainMethod = extraProps.getProperty("main-method");
                if (main2 != null && mainMethod != null) {
                    for (String cls : main2.split(",")) {
                        Class<?> mainClass = Class.forName("ink.ptms.chemdah.taboolib." + cls, true, loader);
                        Method declaredMethod = mainClass.getDeclaredMethod(mainMethod, new Class[0]);
                        declaredMethod.setAccessible(true);
                        declaredMethod.invoke(null, new Object[0]);
                        PrimitiveIO.debug(" = Invoke {0}.{1}()", mainClass.getName(), mainMethod);
                    }
                }
            }
        }
    }

    static File getCacheFile() {
        File file = new File("cache/taboolib/" + projectPackageName);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    static File getLibraryFile() {
        File file = new File(PrimitiveSettings.FILE_LIBS);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    static int deepHashCode(List<String[]> array) {
        int result = 1;
        for (Object[] objectArray : array) {
            result = 31 * result + Arrays.deepHashCode(objectArray);
        }
        return result;
    }

    static {
        try {
            projectPackageName = TABOOLIB_PACKAGE_NAME.substring(0, TABOOLIB_PACKAGE_NAME.length() - 9);
        }
        catch (Throwable ex) {
            projectPackageName = TABOOLIB_PACKAGE_NAME;
        }
    }
}

