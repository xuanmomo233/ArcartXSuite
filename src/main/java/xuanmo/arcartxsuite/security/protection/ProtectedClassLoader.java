package xuanmo.arcartxsuite.security.protection;

import xuanmo.arcartxsuite.security.NativeBridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 受保护的 ClassLoader：拦截类加载请求，从 ENCRYPTED/ 目录读取 .enc 文件，
 * 调用 native 层解密后通过 defineClass 加载。
 *
 * 安全要求：
 * - 解密后明文仅在 defineClass 调用期间短暂存在于 JVM 堆中
 * - 不缓存解密后的字节码
 * - 类名验证：解密前验证类名哈希防止伪造请求
 */
public final class ProtectedClassLoader extends ClassLoader {

    private final JarFile protectedJar;
    private final ConcurrentHashMap<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();
    private volatile boolean protectionActive = true;

    public ProtectedClassLoader(ClassLoader parent, JarFile protectedJar) {
        super(parent);
        this.protectedJar = protectedJar;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 检查是否已加载
        Class<?> cached = loadedClasses.get(name);
        if (cached != null) return cached;

        if (!protectionActive || !NativeBridge.isAvailable()) {
            throw new ClassNotFoundException("Protection unavailable: " + name);
        }

        // 转换类名为 JAR 路径格式
        String entryPath = "ENCRYPTED/" + name.replace('.', '/') + ".enc";
        JarEntry entry = protectedJar.getJarEntry(entryPath);
        if (entry == null) {
            // 不在加密区域，委托给 parent
            return super.findClass(name);
        }

        try {
            // 读取加密数据
            byte[] encData = readEntry(entry);

            // 计算类名哈希
            byte[] classNameHash = sha256(name);

            // 调用 native 解密
            byte[] plainBytecode = NativeBridge.n6(classNameHash, encData);
            if (plainBytecode == null || plainBytecode.length == 0) {
                throw new ClassNotFoundException("Decryption failed: " + name);
            }

            // defineClass
            Class<?> clazz = defineClass(name, plainBytecode, 0, plainBytecode.length);
            loadedClasses.put(name, clazz);

            // 解密后立即清零引用（GC 会回收实际内存，但避免长时间持有引用）
            java.util.Arrays.fill(plainBytecode, (byte) 0);

            return clazz;
        } catch (IOException e) {
            throw new ClassNotFoundException("IO error loading " + name, e);
        }
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 先检查是否已加载（JVM 缓存）
        Class<?> c = findLoadedClass(name);
        if (c != null) return c;

        // 检查是否在加密区域
        String entryPath = "ENCRYPTED/" + name.replace('.', '/') + ".enc";
        if (protectedJar.getJarEntry(entryPath) != null) {
            c = findClass(name);
        } else {
            // 非加密类委托给 parent
            c = getParent().loadClass(name);
        }

        if (resolve) resolveClass(c);
        return c;
    }

    public void deactivate() {
        protectionActive = false;
    }

    private byte[] readEntry(JarEntry entry) throws IOException {
        try (InputStream is = protectedJar.getInputStream(entry)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) entry.getSize());
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
