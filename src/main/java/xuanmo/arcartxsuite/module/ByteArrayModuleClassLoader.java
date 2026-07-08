package xuanmo.arcartxsuite.module;

import xuanmo.arcartxsuite.security.NativeBridge;
import xuanmo.arcartxsuite.security.protection.ProtectionEnvironment;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**

 * 从内存中的 Jar 字节数组加载模块的 ClassLoader。

 * <p>

 * 用于云端模块：下载加密 .axb → native 解密为 jar bytes → 直接内存加载，不落盘。

 * <p>

 * 双层保护：.axb 整包 AES-GCM（native n4）解开后得到的 jar，其内部类可再做

 * <b>逐类字节码加密</b>（{@code ENCRYPTED/<path>.enc}）。本加载器识别这些条目并调用

 * native n6 逐类解密 → defineClass；明文类（未加密的，如保留的桥接/资源相关类）直接 define。

 * <p>

 * 加密类<b>无 Java 回退</b>：native 不可用或解密失败一律抛 {@link ClassNotFoundException}，

 * 与核心保护层"native-only"取向一致。

 */
public final class ByteArrayModuleClassLoader extends ClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private static final String ENC_PREFIX = "ENCRYPTED/";
    private static final String ENC_SUFFIX = ".enc";

    private final String moduleId;
    private final byte[] jarBytes;

    // 方案 B：模块自带的逐类解密种子（= 云端下发的 32 字节 moduleKey）。null 时回退本体 root_seed（n6）。

    private final byte[] moduleSeed;
    private final Map<String, byte[]> entries = new HashMap<>();

    // className(FQN, dotted) -> .enc 数据

    private final Map<String, byte[]> encryptedClasses = new HashMap<>();

    public ByteArrayModuleClassLoader(String moduleId, byte[] jarBytes, ClassLoader parent) {
        this(moduleId, jarBytes, null, parent);
    }

    public ByteArrayModuleClassLoader(String moduleId, byte[] jarBytes, byte[] moduleSeed, ClassLoader parent) {
        super(parent);
        this.moduleId = moduleId;

        this.jarBytes = jarBytes == null ? null : jarBytes.clone();

        this.moduleSeed = (moduleSeed != null && moduleSeed.length == 32) ? moduleSeed.clone() : null;
        try {
            indexEntries();
        } finally {
            wipe(this.jarBytes);
        }
    }

    private void indexEntries() {
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                byte[] data = jis.readAllBytes();
                if (entryName.startsWith(ENC_PREFIX) && entryName.endsWith(ENC_SUFFIX)) {
                    // ENCRYPTED/xuanmo/.../Foo.enc -> xuanmo...Foo
                    String internal = entryName.substring(ENC_PREFIX.length(),
                            entryName.length() - ENC_SUFFIX.length());
                    encryptedClasses.put(internal.replace('/', '.'), data);
                } else {
                    entries.put(entryName, data);
                }
            }
        } catch (IOException e) {

            throw new RuntimeException("无法索引 Jar 条目: " + moduleId, e);

        }
    }

    public String moduleId() {
        return moduleId;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] enc = encryptedClasses.get(name);
        if (enc != null) {
            return defineEncrypted(name, enc);
        }
        String path = name.replace('.', '/') + ".class";
        byte[] classBytes = entries.get(path);
        if (classBytes == null) {
            throw new ClassNotFoundException(name);
        }
        definePackageIfNeeded(name);
        return defineClass(name, classBytes, 0, classBytes.length);
    }

    private Class<?> defineEncrypted(String name, byte[] encData) throws ClassNotFoundException {
        if (!NativeBridge.isAvailable()) {

            throw new ClassNotFoundException("模块 " + moduleId + " 加密类无法加载：native 安全库不可用 - " + name);

        }

        if (!ProtectionEnvironment.ensureCleanEnvironment()) {
            throw new ClassNotFoundException("?? " + moduleId + " ?????????????? - " + name);
        }
        byte[] plain = null;
        try {
            byte[] classNameHash = sha256(name);

            // 方案 B：有模块种子走自包含 n11（与本体 root_seed 解耦）；否则回退 n6（兼容旧本体内嵌 seed 加密的模块）。

            plain = (moduleSeed != null)
                    ? NativeBridge.n11(classNameHash, encData, moduleSeed)
                    : NativeBridge.n6(classNameHash, encData);
            if (plain == null || plain.length == 0) {

                throw new ClassNotFoundException("模块 " + moduleId + " 加密类解密失败: " + name

                        + (moduleSeed == null ? "（无模块种子，回退 n6）" : "（n11 自包含种子）"));

            }
            definePackageIfNeeded(name);
            return defineClass(name, plain, 0, plain.length);
        } catch (ClassNotFoundException e) {
            throw e;
        } catch (Throwable t) {

            throw new ClassNotFoundException("模块 " + moduleId + " 加密类加载异常: " + name, t);

        } finally {
            if (plain != null) {
                java.util.Arrays.fill(plain, (byte) 0);
            }
        }
    }

    private void definePackageIfNeeded(String name) {
        int i = name.lastIndexOf('.');
        if (i <= 0) {
            return;
        }
        String pkg = name.substring(0, i);
        if (getDefinedPackage(pkg) == null) {
            try {
                definePackage(pkg, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException ignored) {

                // 并发下可能已被定义

            }
        }
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        byte[] data = entries.get(name);
        if (data != null) {
            return new ByteArrayInputStream(data);
        }
        return super.getResourceAsStream(name);
    }

    @Override
    protected URL findResource(String name) {
        byte[] data = entries.get(name);
        if (data == null) {
            return null;
        }
        try {
            return new URL("bytes", "", -1, "/" + name, new ByteArrayURLStreamHandler(data));
        } catch (IOException e) {
            return null;
        }
    }

    private static class ByteArrayURLStreamHandler extends URLStreamHandler {
        private final byte[] data;

        ByteArrayURLStreamHandler(byte[] data) {
            this.data = data;
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new URLConnection(u) {
                @Override
                public void connect() {}

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(data);
                }

                @Override
                public long getContentLengthLong() {
                    return data.length;
                }
            };
        }
    }

    public void close() throws IOException {
        entries.clear();
        encryptedClasses.clear();
        wipe(jarBytes);
        if (moduleSeed != null) {
            wipe(moduleSeed);
        }
    }

    private static void wipe(byte[] data) {

        if (data != null) {

            java.util.Arrays.fill(data, (byte) 0);

        }
    }
}
