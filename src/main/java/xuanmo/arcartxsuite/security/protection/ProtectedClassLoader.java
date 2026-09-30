package xuanmo.arcartxsuite.security.protection;

import xuanmo.arcartxsuite.security.NativeBridge;
import xuanmo.arcartxsuite.security.protection.ProtectionEnvironment;
import xuanmo.arcartxsuite.util.HashUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 受保护的 ClassLoader（方案 ②：作为 Bukkit PluginClassLoader 的 parent 注入）。
 *
 * <p>注入后，PluginClassLoader 的 parent 指向本加载器。本加载器采用 <b>child-first</b> 委托策略：
 * 优先从本 JAR 加载类/资源，找不到时才委托给 originalParent（AppClassLoader 系）。
 * 这确保插件 JAR 内的加密类/明文类由本加载器统一定义，避免 PluginClassLoader 重复 define。
 *
 * <p>本加载器是插件 JAR 内全部类与资源的唯一权威来源：
 * <ol>
 *   <li>加密类（{@code ENCRYPTED/<path>.enc}）：native n6 解密 → defineClass；</li>
 *   <li>明文类（JAR 内 {@code <path>.class}，如 ProGuard -keep 的主类/桥接库/被保留的第三方库）：
 *       直接从 JAR 读取字节 defineClass；</li>
 *   <li>引导类（hook 安装前已被 PluginClassLoader 定义的类，见 capturedClasses）：复用，避免重复 define；</li>
 *   <li>其它（Bukkit API、JDK）：委托给 originalParent。</li>
 * </ol>
 * 资源（yml/lang/axb 等）同样优先从本 JAR 提供，保证被本加载器定义的类能 getResource 自身资源。
 *
 * <p><b>注意</b>：child-first 策略意味着如果 JAR 内打包了与 Bukkit API 同名的类（如 shade 了
 * {@code org.bukkit.xxx}），会优先使用 JAR 内的版本而非服务端版本。构建时需确保不 shade 服务端 API。
 */
public final class ProtectedClassLoader extends ClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private static final Logger LOGGER = Logger.getLogger("AXS-Protection");

    private final JarFile protectedJar;
    private final String jarUrlPrefix;
    private final Map<String, Class<?>> capturedClasses;
    private final ConcurrentHashMap<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();
    private final ThreadLocal<byte[]> readBuffer = ThreadLocal.withInitial(() -> new byte[8192]);
    private volatile boolean protectionActive = true;

    /**
     * 本加载器所定义类的 ProtectionDomain。
     * <p>裸 {@code ClassLoader#defineClass(String,byte[],int,int)} 使用 JDK 的
     * {@code defaultDomain}——其 CodeSource 非 null 但 {@code getLocation()==null}，
     * 导致加密类内任何 {@code CodeSource.getLocation().toURI()} 直接 NPE
     * （Mohist/Forge 混合端下以该方式加载的类即踩中此坑）。
     * 因此所有由本加载器定义的类统一挂到指向插件 JAR 的 CodeSource 上。
     */
    private final java.security.ProtectionDomain pluginDomain;

    /**
     * 宿主 PluginClassLoader 的反向引用，用于在 originalParent（AppClassLoader 系）
     * 找不到某类时回退到 PluginClassLoader，利用其跨插件类查找能力加载其他插件
     * 提供的类（如 ArcartX 插件的 {@code KeyCallBack}）。
     * <p>本加载器被注入为 PluginClassLoader 的 parent，故无法通过 {@link #getParent()}
     * 访问 PluginClassLoader；必须显式持有该引用。
     */
    private final ClassLoader pluginClassLoader;

    /**
     * 防循环标志：当本加载器回退委托给 PluginClassLoader 时置为 true。
     * <p>PluginClassLoader.loadClass 是 parent-first，会先回调本加载器（其 parent）；
     * 此时本加载器检测到标志为 true，不再回退到 PluginClassLoader，直接抛出
     * ClassNotFoundException，让 PluginClassLoader 自行 findClass 完成跨插件查找。
     */
    private final ThreadLocal<Boolean> delegatingToPlugin = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ProtectedClassLoader(ClassLoader originalParent, JarFile protectedJar,
                                Map<String, Class<?>> capturedClasses, ClassLoader pluginClassLoader,
                                java.security.ProtectionDomain pluginDomain) {
        super(originalParent);
        this.protectedJar = protectedJar;
        this.capturedClasses = capturedClasses != null
                ? new ConcurrentHashMap<>(capturedClasses) : new ConcurrentHashMap<>();
        this.pluginClassLoader = pluginClassLoader;
        String prefix;
        try {
            prefix = "jar:" + new java.io.File(protectedJar.getName()).toURI().toURL() + "!/";
        } catch (Exception e) {
            prefix = null;
        }
        this.jarUrlPrefix = prefix;

        // 传入的 PD（通常即插件主类的 ProtectionDomain）必须带有可定位到 JAR 的
        // CodeSource；否则自行以插件 JAR URL 构造一个，保证本加载器定义的类
        // getProtectionDomain().getCodeSource().getLocation() 始终非 null。
        if (pluginDomain == null || pluginDomain.getCodeSource() == null
                || pluginDomain.getCodeSource().getLocation() == null) {
            pluginDomain = buildDomainFromJar(protectedJar);
        }
        this.pluginDomain = pluginDomain;
    }

    /**
     * 以插件 JAR 文件本身为 CodeSource 构造 ProtectionDomain（权限对齐普通插件类）。
     * 构造失败返回 null——调用方按无 PD 的默认路径兜底，行为与旧版一致。
     */
    private static java.security.ProtectionDomain buildDomainFromJar(JarFile jar) {
        try {
            URL jarUrl = new java.io.File(jar.getName()).toURI().toURL();
            java.security.CodeSource cs =
                    new java.security.CodeSource(jarUrl, (java.security.CodeSigner[]) null);
            java.security.Permissions perms = new java.security.Permissions();
            perms.add(new java.security.AllPermission());
            return new java.security.ProtectionDomain(cs, perms);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String encEntryPath(String name) {
        return "ENCRYPTED/" + name.replace('.', '/') + ".enc";
    }

    private static String classEntryPath(String name) {
        return name.replace('.', '/') + ".class";
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) c = loadedClasses.get(name);
            if (c == null) c = capturedClasses.get(name);

            if (c == null) {
                // 1. 本 JAR 内的类（加密或明文）—— 本加载器的权威来源，优先于 parent
                JarEntry enc = protectedJar.getJarEntry(encEntryPath(name));
                if (enc != null) {
                    c = defineEncrypted(name, enc);
                } else {
                    JarEntry plain = protectedJar.getJarEntry(classEntryPath(name));
                    if (plain != null) {
                        c = definePlain(name, plain);
                    }
                }
            }

            if (c == null) {
                // 2. 委托 originalParent（AppClassLoader 系，提供 JDK/Bukkit API）
                try {
                    c = getParent().loadClass(name);
                } catch (ClassNotFoundException parentEx) {
                    // 3. originalParent 找不到（典型场景：其他 Bukkit 插件提供的类，
                    //    如 ArcartX 插件的 KeyCallBack）。回退到 PluginClassLoader，
                    //    利用其跨插件类查找能力。
                    //    防循环：PluginClassLoader.loadClass 是 parent-first，会先回调本加载器；
                    //    此时 delegatingToPlugin 标志为 true，本加载器不再回退，直接抛出，
                    //    让 PluginClassLoader 自行 findClass 完成跨插件查找。
                    if (pluginClassLoader != null && !delegatingToPlugin.get()) {
                        delegatingToPlugin.set(Boolean.TRUE);
                        try {
                            c = pluginClassLoader.loadClass(name);
                        } finally {
                            delegatingToPlugin.remove();
                        }
                    } else {
                        throw parentEx;
                    }
                }
            }

            if (resolve) resolveClass(c);
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> cached = loadedClasses.get(name);
        if (cached != null) return cached;
        JarEntry enc = protectedJar.getJarEntry(encEntryPath(name));
        if (enc != null) return defineEncrypted(name, enc);
        JarEntry plain = protectedJar.getJarEntry(classEntryPath(name));
        if (plain != null) return definePlain(name, plain);
        throw new ClassNotFoundException(name);
    }

    private Class<?> defineEncrypted(String name, JarEntry entry) throws ClassNotFoundException {
        Class<?> existing = loadedClasses.get(name);
        if (existing != null) return existing;

        if (!protectionActive || !NativeBridge.isAvailable()) {
            throw new ClassNotFoundException("Protection unavailable: " + name);
        }

        if (!ProtectionEnvironment.ensureCleanEnvironment()) {
            throw new ClassNotFoundException("Protection compromised: " + name);
        }
        byte[] plain = null;
        try {
            byte[] encData = readEntry(entry);
            byte[] classNameHash = HashUtils.sha256(name);
            plain = NativeBridge.n6(classNameHash, encData);
            if (plain == null || plain.length == 0) {
                throw new ClassNotFoundException("Decryption failed: " + name);
            }
            definePackageIfNeeded(name);
            Class<?> clazz = pluginDomain != null
                    ? defineClass(name, plain, 0, plain.length, pluginDomain)
                    : defineClass(name, plain, 0, plain.length);
            loadedClasses.put(name, clazz);
            if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine("[Protection] decrypt-define " + name);
            return clazz;
        } catch (IOException e) {
            throw new ClassNotFoundException("IO error loading " + name, e);
        } finally {
            if (plain != null) java.util.Arrays.fill(plain, (byte) 0);
        }
    }

    private Class<?> definePlain(String name, JarEntry entry) throws ClassNotFoundException {
        Class<?> existing = loadedClasses.get(name);
        if (existing != null) return existing;
        try {
            byte[] data = readEntry(entry);
            definePackageIfNeeded(name);
            Class<?> clazz = pluginDomain != null
                    ? defineClass(name, data, 0, data.length, pluginDomain)
                    : defineClass(name, data, 0, data.length);
            loadedClasses.put(name, clazz);
            return clazz;
        } catch (IOException e) {
            throw new ClassNotFoundException("IO error loading " + name, e);
        }
    }

    @Override
    protected URL findResource(String name) {
        if (jarUrlPrefix == null) return null;
        JarEntry entry = protectedJar.getJarEntry(name);
        if (entry == null) return null;
        try {
            return new URL(jarUrlPrefix + name);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected Enumeration<URL> findResources(String name) {
        URL u = findResource(name);
        if (u == null) return Collections.emptyEnumeration();
        return Collections.enumeration(java.util.List.of(u));
    }

    private void definePackageIfNeeded(String name) {
        int i = name.lastIndexOf('.');
        if (i <= 0) return;
        String pkg = name.substring(0, i);
        if (getDefinedPackage(pkg) == null) {
            try {
                definePackage(pkg, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException ignored) {
                // 并发下可能已被定义
            }
        }
    }

    public void deactivate() {
        protectionActive = false;
        LOGGER.severe("[Protection] ProtectedClassLoader deactivated; encrypted classes will fail to load.");
    }

    private byte[] readEntry(JarEntry entry) throws IOException {
        try (InputStream is = protectedJar.getInputStream(entry)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(64, entry.getSize()));
            byte[] buf = readBuffer.get();
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }
}
