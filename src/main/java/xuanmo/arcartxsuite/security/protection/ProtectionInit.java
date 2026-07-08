package xuanmo.arcartxsuite.security.protection;

import xuanmo.arcartxsuite.security.NativeBridge;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * 保护子系统初始化器。
 * 在插件最早生命周期（onLoad 或 static block）调用，负责：
 * 1. 初始化 native 保护引擎（密钥派生）
 * 2. 启动 JVM + native 双层反调试监控
 * 3. 执行 JAR 完整性校验（Merkle 树 + Ed25519 签名）
 * 4. 设置 ProtectedClassLoader（如果 JAR 包含 ENCRYPTED/ 目录）
 */
public final class ProtectionInit {

    private static volatile boolean initialized = false;
    private static volatile boolean active = false;
    private static volatile ProtectedClassLoader protectedLoader = null;
    private static final Logger LOGGER = Logger.getLogger("AXS-Protection");

    // 检测间隔（秒），加随机偏移防止时序分析
    private static final int CHECK_INTERVAL_SECONDS = 60;

    private ProtectionInit() {}

    /**
     * 初始化保护系统。必须在插件主类的 <b>静态初始化块</b> 中、尽可能早地调用，
     * 以保证在任何加密类被加载之前完成 PluginClassLoader 的 parent 注入。
     *
     * <p>JAR 路径与 PluginClassLoader 都从 {@code pluginMainClass} 推导，无需外部传参。
     *
     * @param pluginMainClass 插件主类（其 ClassLoader 即 Bukkit PluginClassLoader）
     * @return true = 初始化成功（或为兼容模式的明文 JAR）
     */
    public static synchronized boolean initialize(Class<?> pluginMainClass) {
        if (initialized) return true;
        initialized = true; // 防重入：即使后续失败也不再重试，避免半安装状态

        ClassLoader pluginCL = pluginMainClass.getClassLoader();
        String jarPath = resolveJarPath(pluginMainClass);
        boolean nativeOk = NativeBridge.isAvailable();

        boolean encrypted = false;
        try {
            encrypted = jarPath != null && jarHasEncryptedClasses(jarPath);
        } catch (Exception ignored) {}

        // 兼容模式：明文 JAR（开发/未加密构建）——不安装 classloader hook
        if (!encrypted) {
            if (nativeOk) {
                try { NativeBridge.n5(); } catch (Throwable ignored) {}
                ProtectionEnvironment.ensureCleanEnvironment();
                JvmAntiDebug.startMonitoring(ProtectionInit::onThreatDetected, CHECK_INTERVAL_SECONDS);
            }
            active = true;
            LOGGER.info("[Protection] 未检测到加密类，保护层以兼容模式运行（明文 JAR）。");
            return true;
        }

        // 加密 JAR：native 必须可用，否则无法解密任何类
        if (!nativeOk) {
            LOGGER.severe("[Protection] 加密 JAR 但 native 库不可用，无法解密: " + NativeBridge.getLoadError());
            return false;
        }

        int initResult;
        try { initResult = NativeBridge.n5(); }
        catch (Throwable t) { LOGGER.severe("[Protection] native n5 异常: " + t); return false; }
        if (initResult != 0) {
            LOGGER.severe("[Protection] native 初始化失败 code=" + initResult);
            return false;
        }

        ProtectionEnvironment.ensureCleanEnvironment();
        try {
            if (!verifyJarIntegrity(jarPath)) {
                LOGGER.severe("[Protection] JAR 完整性校验失败");
                triggerTamperResponse();
                return false;
            }
        } catch (Exception e) {
            LOGGER.severe("[Protection] 完整性校验异常: " + e);
            return false;
        }

        // 安装 ProtectedClassLoader 为 PluginClassLoader 的 parent（方案 ②）。
        // 必须在启动反调试监控之前完成：监控会触发 JvmAntiDebug 等类加载，
        // 这些类需经由已注入的 ProtectedClassLoader 统一加载，避免与 PluginClassLoader 双重定义。
        try {
            JarFile jar = new JarFile(jarPath);
            Map<String, Class<?>> captured = captureBootstrapClasses(pluginMainClass, pluginCL, jar);
            ClassLoader originalParent = pluginCL.getParent();
            ProtectedClassLoader pcl = new ProtectedClassLoader(originalParent, jar, captured);

            if (!installAsParent(pluginCL, pcl)) {
                LOGGER.severe("[Protection] 无法将 ProtectedClassLoader 注入 PluginClassLoader（parent 替换失败），"
                        + "加密类将无法加载。PluginClassLoader=" + pluginCL.getClass().getName());
                jar.close();
                return false;
            }
            protectedLoader = pcl;
            LOGGER.info("[Protection] ProtectedClassLoader 已注入 (引导类=" + captured.size()
                    + ", 原parent=" + (originalParent == null ? "null" : originalParent.getClass().getName()) + ")");
        } catch (Throwable t) {
            LOGGER.severe("[Protection] ClassLoader 注入失败: " + t);
            return false;
        }

        // hook 就位后再启动反调试监控
        JvmAntiDebug.startMonitoring(ProtectionInit::onThreatDetected, CHECK_INTERVAL_SECONDS);

        active = true;
        return true;
    }

    /** 从主类的 CodeSource 推导插件 JAR 的绝对路径。 */
    private static String resolveJarPath(Class<?> c) {
        try {
            java.security.CodeSource src = c.getProtectionDomain().getCodeSource();
            if (src == null || src.getLocation() == null) return null;
            return new java.io.File(src.getLocation().toURI()).getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean jarHasEncryptedClasses(String jarPath) throws Exception {
        try (JarFile jar = new JarFile(jarPath)) {
            java.util.Enumeration<java.util.jar.JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                String n = e.nextElement().getName();
                if (n.startsWith("ENCRYPTED/") && n.endsWith(".enc")) return true;
            }
        }
        return false;
    }

    /**
     * 捕获 hook 安装前已被 PluginClassLoader 定义的明文引导类，供 ProtectedClassLoader 复用，
     * 避免被重复 define 造成 LinkageError。这些类必须与 encrypt-jar 的 --bootstrap-classes 一致。
     */
    private static Map<String, Class<?>> captureBootstrapClasses(Class<?> mainClass, ClassLoader pluginCL, JarFile jar) {
        Map<String, Class<?>> m = new java.util.HashMap<>();
        // 注意：不含 JvmAntiDebug —— 它在 hook 安装后才首次加载，交由 ProtectedClassLoader 统一加载即可。
        Class<?>[] boot = {
            mainClass,
            NativeBridge.class,
            ProtectionInit.class,
            ProtectedClassLoader.class,
            ProtectionEnvironment.class,
        };
        for (Class<?> c : boot) {
            m.put(c.getName(), c);
            // 捕获其全部内部类/匿名类（ProGuard 已 -keep 为明文），在 hook 安装前由 PluginClassLoader 定义，
            // 与外部类保持同一 ClassLoader，避免 nestmate 跨加载器 IllegalAccessError。
            String base = c.getName().replace('.', '/') + "$";
            java.util.Enumeration<java.util.jar.JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                String n = e.nextElement().getName();
                if (n.endsWith(".class") && n.startsWith(base)) {
                    String fqn = n.substring(0, n.length() - ".class".length()).replace('/', '.');
                    try {
                        m.put(fqn, Class.forName(fqn, false, pluginCL));
                    } catch (Throwable ignored) {}
                }
            }
        }
        return m;
    }

    /**
     * 用 sun.misc.Unsafe 把 target 的 {@code parent} 字段（java.base 的 final 字段，
     * Java 17 普通反射封封无法写）替换为 newParent。这是方案 ② 在「不加 --add-opens /
     * 不改启动脚本」前提下唯一可行的 parent 替换手段，也是最依赖运行时内部实现的一环。
     */
    @SuppressWarnings("removal")
    private static boolean installAsParent(ClassLoader target, ClassLoader newParent) {
        if (target.getParent() == newParent) return true;
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field tu = unsafeClass.getDeclaredField("theUnsafe");
            tu.setAccessible(true);
            Object unsafe = tu.get(null);
            java.lang.reflect.Method putM =
                    unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);

            long off = resolveParentFieldOffset(unsafeClass, unsafe);
            if (off < 0L) {
                LOGGER.severe("[Protection] 无法定位 ClassLoader.parent 字段偏移");
                return false;
            }
            putM.invoke(unsafe, target, off, newParent);
            return target.getParent() == newParent;
        } catch (Throwable t) {
            LOGGER.severe("[Protection] Unsafe parent 替换异常: " + t);
            return false;
        }
    }

    /**
     * 定位 {@code java.lang.ClassLoader.parent} 实例字段的偏移量。
     *
     * <p>Java 12+ 的核心反射「字段过滤」会隐藏 {@code java.lang.ClassLoader} 的全部声明字段，
     * 使 {@code ClassLoader.class.getDeclaredField("parent")} 抛 {@link NoSuchFieldException}。
     * 为此优先尝试常规反射（旧 JDK 可行），失败后改用「唯一哨兵探针」：构造一个 parent 指向
     * 全新唯一对象的临时 ClassLoader，用 Unsafe 逐偏移扫描该引用所在位置——因为 parent 声明于
     * 基类 ClassLoader，其字段偏移对所有子类（含 PluginClassLoader）一致。
     *
     * @return parent 字段偏移；定位失败返回 -1。
     */
    @SuppressWarnings("removal")
    private static long resolveParentFieldOffset(Class<?> unsafeClass, Object unsafe) {
        // 优先：常规反射 + objectFieldOffset（未启用字段过滤的旧 JDK 可行）。
        try {
            java.lang.reflect.Field parentField = ClassLoader.class.getDeclaredField("parent");
            java.lang.reflect.Method offsetM =
                    unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field.class);
            return (Long) offsetM.invoke(unsafe, parentField);
        } catch (Throwable ignored) {
            // 落到哨兵探针。
        }
        try {
            // 安全扫描：只用 getInt/getLong 读「原始字节」，绝不用 getObject——后者会把任意槽
            // 当作压缩 oop 解码，读到非引用槽时会产出野指针，GC/JIT 处理时崩溃（EXCEPTION_ACCESS_VIOLATION）。
            // 做法：把哨兵放进一个已知的 Object[] 槽，用 getInt/getLong 读出它的「压缩 oop 原始值」，
            // 再在 probe 的字节里精确匹配该值定位 parent 偏移。匹配是数值相等，不解引用，故安全。
            java.lang.reflect.Method getIntM =
                    unsafeClass.getMethod("getInt", Object.class, long.class);
            java.lang.reflect.Method getLongM =
                    unsafeClass.getMethod("getLong", Object.class, long.class);
            java.lang.reflect.Method abaseM =
                    unsafeClass.getMethod("arrayBaseOffset", Class.class);
            java.lang.reflect.Method ascaleM =
                    unsafeClass.getMethod("arrayIndexScale", Class.class);

            int scale = (Integer) ascaleM.invoke(unsafe, Object[].class);
            long base = ((Number) abaseM.invoke(unsafe, Object[].class)).longValue();

            final ClassLoader sentinel = new ClassLoader(null) {};
            final ClassLoader probe = new ClassLoader(sentinel) {};
            final Object[] holder = new Object[] { sentinel };

            // GC 可能在两次读之间移动 sentinel（其压缩 oop 值随之改变），重试数次规避 TOCTOU。
            for (int attempt = 0; attempt < 8; attempt++) {
                if (scale == 4) {
                    int needle = (Integer) getIntM.invoke(unsafe, holder, base);
                    if (needle == 0) continue;
                    for (long off = 8L; off <= 256L; off += 4L) {
                        if ((Integer) getIntM.invoke(unsafe, probe, off) == needle) {
                            return off;
                        }
                    }
                } else {
                    long needle = (Long) getLongM.invoke(unsafe, holder, base);
                    if (needle == 0L) continue;
                    for (long off = 8L; off <= 256L; off += 8L) {
                        if ((Long) getLongM.invoke(unsafe, probe, off) == needle) {
                            return off;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.severe("[Protection] 哨兵探针定位 parent 偏移失败: " + t);
        }
        return -1L;
    }

    /**
     * 获取保护 ClassLoader（加载加密类时使用）。
     */
    public static ProtectedClassLoader getProtectedLoader() {
        return protectedLoader;
    }

    /** @return true 表示保护层已成功就位（加密 JAR 已注入 hook，或明文 JAR 兼容模式）。 */
    public static boolean isInitialized() {
        return active;
    }

    /**
     * 关闭保护系统。
     */
    public static void shutdown() {
        JvmAntiDebug.stopMonitoring();
        if (protectedLoader != null) {
            protectedLoader.deactivate();
        }
        ProtectionEnvironment.reset();
        active = false;
    }

    // ─── 完整性校验 ─────────────────────────────────────────────

    private static boolean verifyJarIntegrity(String jarPath) throws Exception {
        JarFile jar = new JarFile(jarPath);
        try {
            // 读取 PROTECTION.MF
            var pmfEntry = jar.getJarEntry("META-INF/PROTECTION.MF");
            if (pmfEntry == null) {
                // 没有保护元数据 = 未加密 JAR，跳过校验
                return true;
            }

            Properties pmf = new Properties();
            try (InputStream is = jar.getInputStream(pmfEntry)) {
                pmf.load(is);
            }

            String integrityHashHex = pmf.getProperty("Integrity-Hash");
            String signatureHex = pmf.getProperty("Signature");
            if (integrityHashHex == null || signatureHex == null) return false;

            // 计算所有加密文件的 Merkle 根哈希
            byte[] computedRoot = computeMerkleRoot(jar);
            byte[] expectedRoot = hexToBytes(integrityHashHex);
            byte[] signature = hexToBytes(signatureHex);

            // 比对根哈希
            if (!MessageDigest.isEqual(computedRoot, expectedRoot)) {
                return false;
            }

            // 调用 native 验证 Ed25519 签名（公钥编译时嵌入 native）
            return NativeBridge.n7(computedRoot, signature);
        } finally {
            jar.close();
        }
    }

    private static byte[] computeMerkleRoot(JarFile jar) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        TreeMap<String, byte[]> hashes = new TreeMap<>();

        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("ENCRYPTED/") && name.endsWith(".enc")) {
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] data = readFully(is);
                    hashes.put(name, md.digest(data));
                    md.reset();
                }
            }
        }

        if (hashes.isEmpty()) return md.digest(new byte[0]);

        // 构建 Merkle 树
        byte[][] leaves = hashes.values().toArray(new byte[0][]);
        while (leaves.length > 1) {
            int newLen = (leaves.length + 1) / 2;
            byte[][] next = new byte[newLen][];
            for (int i = 0; i < leaves.length; i += 2) {
                if (i + 1 < leaves.length) {
                    md.update(leaves[i]);
                    md.update(leaves[i + 1]);
                } else {
                    md.update(leaves[i]);
                    md.update(leaves[i]);
                }
                next[i / 2] = md.digest();
                md.reset();
            }
            leaves = next;
        }
        return leaves[0];
    }

    // ─── 威胁响应 ───────────────────────────────────────────────

    private static void onThreatDetected(int threatLevel) {
        // 静默降级：不崩溃，但禁用保护功能（使后续类加载失败）
        LOGGER.warning("[Protection] Threat detected: 0x" + Integer.toHexString(threatLevel)
                + " (" + JvmAntiDebug.describeThreat(threatLevel) + ")");
        triggerTamperResponse();
    }

    private static void triggerTamperResponse() {
        // 延迟 5-30 秒后降级（避免二分法定位检测点）
        int delay = 5 + (int)(Math.random() * 25);
        new Thread(() -> {
            try { Thread.sleep(delay * 1000L); } catch (InterruptedException ignored) {}
            if (protectedLoader != null) {
                protectedLoader.deactivate();
            }
        }, "axs-gc-finalize").start();
    }

    // ─── 工具方法 ───────────────────────────────────────────────

    private static byte[] readFully(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
