package xuanmo.arcartxsuite.security.protection;

import xuanmo.arcartxsuite.security.NativeBridge;
import xuanmo.arcartxsuite.security.protection.ProtectionEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * <p>注入后，PluginClassLoader 采用 parent-first 委托，把每个类/资源请求先转交本加载器。
 * 本加载器是插件 JAR 内全部类与资源的唯一权威来源：
 * <ol>
 *   <li>加密类（{@code ENCRYPTED/<path>.enc}）：native n6 解密 → defineClass；</li>
 *   <li>明文类（JAR 内 {@code <path>.class}，如 ProGuard -keep 的主类/桥接库/被保留的第三方库）：
 *       直接从 JAR 读取字节 defineClass；</li>
 *   <li>引导类（hook 安装前已被 PluginClassLoader 定义的类，见 capturedClasses）：复用，避免重复 define；</li>
 *   <li>其它（Bukkit API、JDK）：委托给 originalParent。</li>
 * </ol>
 * 资源（yml/lang/axb 等）同样优先从本 JAR 提供，保证被本加载器定义的类能 getResource 自身资源。
 *
 * <p>由于本加载器统一供给 JAR 内所有类，PluginClassLoader 在 parent-first 下基本不会再自行
 * define JAR 类（除了 hook 安装前已加载的引导类），从而避免重复定义。
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
    private volatile boolean protectionActive = true;

    public ProtectedClassLoader(ClassLoader originalParent, JarFile protectedJar,
                                Map<String, Class<?>> capturedClasses) {
        super(originalParent);
        this.protectedJar = protectedJar;
        this.capturedClasses = capturedClasses != null
                ? new ConcurrentHashMap<>(capturedClasses) : new ConcurrentHashMap<>();
        String prefix;
        try {
            prefix = "jar:" + new java.io.File(protectedJar.getName()).toURI().toURL() + "!/";
        } catch (Exception e) {
            prefix = null;
        }
        this.jarUrlPrefix = prefix;
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
                JarEntry enc = protectedJar.getJarEntry(encEntryPath(name));
                if (enc != null) {
                    c = defineEncrypted(name, enc);
                } else {
                    JarEntry plain = protectedJar.getJarEntry(classEntryPath(name));
                    if (plain != null) {
                        c = definePlain(name, plain);
                    } else {
                        c = getParent().loadClass(name);
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
            byte[] classNameHash = sha256(name);
            plain = NativeBridge.n6(classNameHash, encData);
            if (plain == null || plain.length == 0) {
                throw new ClassNotFoundException("Decryption failed: " + name);
            }
            definePackageIfNeeded(name);
            Class<?> clazz = defineClass(name, plain, 0, plain.length);
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
            Class<?> clazz = defineClass(name, data, 0, data.length);
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
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
