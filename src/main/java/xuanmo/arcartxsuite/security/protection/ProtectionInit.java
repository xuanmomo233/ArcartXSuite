package xuanmo.arcartxsuite.security.protection;

import xuanmo.arcartxsuite.security.NativeBridge;
import xuanmo.arcartxsuite.util.HashUtils;
import xuanmo.arcartxsuite.bootstrap.RuntimeBootstrap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
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
    /** 保护层初始化时解析到的宿主插件 JAR（可能为 null——所有策略均失败时）。 */
    private static volatile java.io.File pluginJarFile = null;
    private static volatile java.net.URL pluginJarUrl = null;
    private static volatile String verifiedBuildProvenance = "";
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
        if (initialized) return active;
        initialized = true; // 防重入：即使后续失败也不再重试，避免半安装状态

        ClassLoader pluginCL = pluginMainClass.getClassLoader();
        java.io.File jarFile = resolvePluginJar(pluginMainClass);
        pluginJarFile = jarFile;
        if (jarFile != null) {
            try { pluginJarUrl = jarFile.toURI().toURL(); } catch (Throwable ignored) {}
        }
        String jarPath = jarFile != null ? jarFile.getAbsolutePath() : null;
        boolean encrypted;
        boolean hasRuntimeCatalog;
        try (JarFile jar = jarFile == null ? null : new JarFile(jarFile)) {
            encrypted = jar != null && requiresReleaseVerification(jar);
            hasRuntimeCatalog = jar != null && jar.getJarEntry(RuntimeBootstrap.CATALOG) != null;
            if (encrypted) {
                var nativeOrigin = fileFromCodeSource(NativeBridge.class.getProtectionDomain());
                if (nativeOrigin == null || !Files.isSameFile(nativeOrigin.toPath(), jarFile.toPath())) {
                    LOGGER.severe("[Protection] Native 验签目标与宿主加载来源不一致。");
                    return false;
                }
            }
        } catch (Exception e) {
            LOGGER.severe("[Protection] 无法读取宿主 JAR: " + e);
            return false;
        }

        boolean nativeOk = NativeBridge.isAvailable();
        if (encrypted && !nativeOk) {
            LOGGER.severe("[Protection] 加密 JAR 但 native 库不可用，无法解密: " + NativeBridge.getLoadError());
            return false;
        }
        if (nativeOk) {
            int initResult;
            try { initResult = NativeBridge.n5(); }
            catch (Throwable t) { LOGGER.severe("[Protection] native n5 异常: " + t); return false; }
            // n5 自检需要按路径读取明文库文件，故清理只能在自检返回后进行。
            finally { NativeBridge.cleanupExtractedLib(); }
            if (initResult != 0) {
                LOGGER.severe(initResult == -3
                    ? "[Protection] 宿主签名清单缺失、不可读或与发行包不符。"
                    : "[Protection] native 初始化失败 code=" + initResult);
                return false;
            }
            ProtectionEnvironment.ensureCleanEnvironment();
        }

        // 明文开发包也可使用分包加载器；传统 fat JAR / classes 目录保持原路径。
        if (!encrypted && !hasRuntimeCatalog) {
            if (nativeOk) JvmAntiDebug.startMonitoring(ProtectionInit::onThreatDetected, CHECK_INTERVAL_SECONDS);
            active = true;
            LOGGER.info("[Protection] 明文开发包已就绪。");
            return true;
        }
        if (encrypted) {
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
        }

        // 安装 ProtectedClassLoader 为 PluginClassLoader 的 parent（方案 ②）。
        // 必须在启动反调试监控之前完成：监控会触发 JvmAntiDebug 等类加载，
        // 这些类需经由已注入的 ProtectedClassLoader 统一加载，避免与 PluginClassLoader 双重定义。
        try {
            JarFile jar = new JarFile(jarFile);
            Map<String, Class<?>> captured = captureBootstrapClasses(pluginMainClass, pluginCL, jar);
            ClassLoader originalParent = pluginCL.getParent();
            // 传入 pluginCL 作为反向引用：ProtectedClassLoader 在 originalParent 找不到类时
            // 回退到 PluginClassLoader，利用其跨插件类查找能力加载其他插件提供的类
            // （如 ArcartX 插件的 KeyCallBack）。否则注入为 parent 后会切断跨插件可见性。
            // 传入主类的 ProtectionDomain：其 CodeSource 指向插件 JAR，使保护层定义的
            // 加密类继承同一 CodeSource；若其 location 缺失（Mohist 自定义加载链），
            // ProtectedClassLoader 内部会以 JAR 路径自行构造兜底。
            java.security.ProtectionDomain pd = null;
            try { pd = pluginMainClass.getProtectionDomain(); } catch (Throwable ignored) {}
            ProtectedClassLoader pcl = new ProtectedClassLoader(originalParent, jar, captured, pluginCL, pd);
            try { pcl.attachRuntime(RuntimeBootstrap.open(jar, pcl, encrypted)); }
            catch (Throwable failure) { jar.close(); throw failure; }

            if (!installAsParent(pluginCL, pcl)) {
                LOGGER.severe("[Protection] 无法将 ProtectedClassLoader 注入 PluginClassLoader（parent 替换失败），"
                        + "加密类将无法加载。PluginClassLoader=" + pluginCL.getClass().getName());
                pcl.closeRuntime();
                jar.close();
                return false;
            }
            protectedLoader = pcl;
            // Paper closes PluginClassLoader's JAR before asynchronous module retirement finishes.
            // Define API types now without running static initializers, so accepted workers can still link them.
            long apiStart = System.nanoTime();
            int apiTypes = prepareSharedApiTypes(pluginCL, jar);
            LOGGER.info("[Protection] 已准备共享 API: " + apiTypes + " 类, "
                + ((System.nanoTime() - apiStart) / 1_000_000) + " ms");
            LOGGER.info("[Protection] ProtectedClassLoader 已注入 (引导类=" + captured.size()
                    + ", 原parent=" + (originalParent == null ? "null" : originalParent.getClass().getName()) + ")");
        } catch (Throwable t) {
            LOGGER.severe("[Protection] ClassLoader 注入失败: " + t);
            return false;
        }

        // hook 就位后再启动反调试监控
        JvmAntiDebug.startMonitoring(ProtectionInit::onThreatDetected, CHECK_INTERVAL_SECONDS);

        if (encrypted) verifiedBuildProvenance = readVerifiedBuildProvenance(jarFile);
        active = true;
        return true;
    }

    /** Native 验证宿主签名后从同一宿主 JAR 读取的发行信息。明文构建不提供此证明。 */
    public static String verifiedBuildProvenance() {
        return active ? verifiedBuildProvenance : "";
    }

    private static String readVerifiedBuildProvenance(java.io.File archive) {
        try (JarFile jar = new JarFile(archive)) {
            var provenance = jar.getJarEntry("axs-build.json");
            var manifest = jar.getJarEntry("META-INF/BOOTSTRAP.MF");
            var signature = jar.getJarEntry("META-INF/BOOTSTRAP.SIG");
            if (provenance == null || manifest == null || signature == null
                || provenance.getSize() <= 0 || provenance.getSize() > 4096
                || manifest.getSize() <= 0 || manifest.getSize() > 4 * 1024 * 1024 || signature.getSize() != 64) return "";
            try (var provenanceInput = jar.getInputStream(provenance);
                 var manifestInput = jar.getInputStream(manifest);
                 var signatureInput = jar.getInputStream(signature)) {
                byte[] sealedManifest = manifestInput.readNBytes((int) manifest.getSize());
                byte[] build = provenanceInput.readNBytes((int) provenance.getSize());
                var sha256 = MessageDigest.getInstance("SHA-256");
                if (!NativeBridge.n7(sha256.digest(sealedManifest), signatureInput.readNBytes(64))) return "";
                String signedEntry = "axs-build.json " + java.util.HexFormat.of().formatHex(sha256.digest(build));
                if (new String(sealedManifest, java.nio.charset.StandardCharsets.UTF_8).lines().noneMatch(signedEntry::equals)) return "";
                return new String(build, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception failure) {
            LOGGER.warning("[Protection] 无法核对已签名的发行批次: " + failure.getMessage());
            return "";
        }
    }

    static int prepareSharedApiTypes(ClassLoader pluginLoader, JarFile jar) throws ClassNotFoundException {
        int count = 0;
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("xuanmo/arcartxsuite/api/") && name.endsWith(".class")) {
                Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, pluginLoader);
                count++;
            }
        }
        return count;
    }

    /**
     * 解析宿主插件 JAR 文件（多策略，永不抛异常）。
     *
     * <p>{@code CodeSource.getLocation()} 是否非空完全取决于定义该类的 ClassLoader：
     * Mohist/Forge 的重映射链、自定义加载器、运行时再生成类、以及本插件自身的
     * ProtectedClassLoader（裸 defineClass → JDK defaultDomain → location=null）
     * 都可能让 location 缺失或落在非 file: 协议上。因此必须逐级回退。
     */
    private static java.io.File resolvePluginJar(Class<?> anchor) {
        // 1) 锚点类（插件主类）的 CodeSource —— 标准 PluginClassLoader 路径
        java.io.File jar = fileFromCodeSource(anchor.getProtectionDomain());
        if (jar != null) return jar;

        // 2) ProtectionInit 自身的 CodeSource（同一 JAR；锚点类被运行时再生成/重映射时兜底）
        jar = fileFromCodeSource(ProtectionInit.class.getProtectionDomain());
        if (jar != null) return jar;

        // 3) 插件描述资源 URL 反推：plugin.yml 必在 JAR 根，形如 jar:file:/x.jar!/plugin.yml
        ClassLoader cl = anchor.getClassLoader();
        for (String desc : new String[]{"plugin.yml", "bungee.yml", "paper-plugin.yml"}) {
            jar = fileFromResourceUrl(cl, desc);
            if (jar != null) return jar;
        }

        // 4) 兜底：扫描 plugins/ 目录，找包含主类条目的 jar
        jar = scanPluginsDirForSelfJar();
        if (jar != null) {
            LOGGER.warning("[Protection] JAR 路径经 plugins 目录扫描回退解析: " + jar);
            return jar;
        }

        LOGGER.severe("[Protection] 无法定位宿主 JAR（CodeSource/资源URL/目录扫描均失败）");
        return null;
    }

    private static java.io.File fileFromCodeSource(java.security.ProtectionDomain pd) {
        try {
            java.security.CodeSource cs = (pd != null) ? pd.getCodeSource() : null;
            java.io.File f = urlToFile((cs != null) ? cs.getLocation() : null);
            return (f != null && f.isFile()) ? f : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把 file: / jar: 协议的 URL 解析为本地文件；其余协议返回 null。 */
    private static java.io.File urlToFile(java.net.URL url) {
        if (url == null) return null;
        try {
            String proto = url.getProtocol();
            if ("file".equals(proto)) {
                return new java.io.File(url.toURI());
            }
            if ("jar".equals(proto)) {
                // jar:file:/x.jar!/entry → 取 !/ 前的部分递归解析
                String spec = url.toExternalForm();
                int sep = spec.lastIndexOf("!/");
                if (sep > 4) {
                    return urlToFile(new java.net.URL(spec.substring(4, sep)));
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * 通过插件描述资源反推所在 JAR。
     * 校验返回的 JAR 确实包含主类条目，防止拿到别的插件/服务端 JAR。
     */
    private static java.io.File fileFromResourceUrl(ClassLoader cl, String entryName) {
        try {
            java.net.URL res = (cl != null)
                    ? cl.getResource(entryName) : ClassLoader.getSystemResource(entryName);
            java.io.File f = urlToFile(res);
            return (f != null && f.isFile() && jarContainsSelfMarker(f)) ? f : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean jarContainsSelfMarker(java.io.File jar) {
        try (JarFile jf = new JarFile(jar)) {
            return jf.getJarEntry("xuanmo/arcartxsuite/ArcartXSuitePlugin.class") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 兜底：扫描 <server>/plugins 下所有 jar，找含宿主主类条目的那个。 */
    private static java.io.File scanPluginsDirForSelfJar() {
        try {
            java.io.File pluginsDir = new java.io.File("plugins").getAbsoluteFile();
            if (!pluginsDir.isDirectory()) return null;
            java.io.File[] jars = pluginsDir.listFiles(
                    (d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
            if (jars == null) return null;
            // 名字像本插件的优先，减少 IO 次数
            java.util.Arrays.sort(jars, java.util.Comparator.comparing(
                    (java.io.File f) -> !f.getName().toLowerCase(java.util.Locale.ROOT)
                            .contains("arcartx")));
            for (java.io.File j : jars) {
                if (jarContainsSelfMarker(j)) return j;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** @return 保护层初始化时解析到的宿主插件 JAR；解析失败或未初始化为 null。 */
    public static java.io.File getPluginJarFile() {
        return pluginJarFile;
    }

    /** @return 宿主插件 JAR 的 file: URL；不可用时为 null。 */
    public static java.net.URL getPluginJarUrl() {
        return pluginJarUrl;
    }

    static boolean requiresReleaseVerification(JarFile jar) {
        if (jar.getJarEntry("META-INF/PROTECTION.MF") != null
            || jar.getJarEntry("META-INF/BOOTSTRAP.MF") != null
            || jar.getJarEntry("META-INF/BOOTSTRAP.SIG") != null
            || jar.getJarEntry("native/axs-native.enc") != null
            || jar.getJarEntry("native/libaxs-native.enc") != null) {
            return true;
        }
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith("ENCRYPTED/") && name.endsWith(".enc")) return true;
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
            xuanmo.arcartxsuite.bootstrap.SuiteCore.class,
            xuanmo.arcartxsuite.bootstrap.HostShutdown.class,
            RuntimeBootstrap.class,
            NativeBridge.class,
            ProtectionInit.class,
            ProtectedClassLoader.class,
            ProtectionEnvironment.class,
            HashUtils.class,
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
     *
     * <p><b>兼容性警告</b>：Unsafe.putObject 在 Java 18+ 受到进一步限制，
     * Java 21+（Paper 1.21）可能需要切换到 VarHandle 或 --add-opens 方案。
     * 若此方法返回 false，保护层将无法注入，加密类加载会失败。
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
            // 扫描范围 8~512 字节，覆盖已知 JVM（Java 8~21）的 ClassLoader 对象布局。
            for (int attempt = 0; attempt < 8; attempt++) {
                if (scale == 4) {
                    int needle = (Integer) getIntM.invoke(unsafe, holder, base);
                    if (needle == 0) continue;
                    for (long off = 8L; off <= 512L; off += 4L) {
                        if ((Integer) getIntM.invoke(unsafe, probe, off) == needle) {
                            return off;
                        }
                    }
                } else {
                    long needle = (Long) getLongM.invoke(unsafe, holder, base);
                    if (needle == 0L) continue;
                    for (long off = 8L; off <= 512L; off += 8L) {
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
            protectedLoader.closeRuntime();
            protectedLoader.deactivate();
        }
        ProtectionEnvironment.reset();
        verifiedBuildProvenance = "";
        active = false;
    }

    /** Paper may close the outer plugin JAR before asynchronous retirement finishes. */
    public static void prepareShutdown() throws ClassNotFoundException {
        ClassLoader loader = ProtectionInit.class.getClassLoader();
        Class.forName("xuanmo.arcartxsuite.security.protection.JvmAntiDebug", true, loader);
        Class.forName("xuanmo.arcartxsuite.security.protection.ProtectionEnvironment", true, loader);
    }

    // ─── 完整性校验 ─────────────────────────────────────────────

    private static boolean verifyJarIntegrity(String jarPath) throws Exception {
        try (JarFile jar = new JarFile(jarPath)) {
            // 读取 PROTECTION.MF
            var pmfEntry = jar.getJarEntry("META-INF/PROTECTION.MF");
            if (pmfEntry == null) return false;

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
        Thread t = new Thread(() -> {
            try { Thread.sleep(delay * 1000L); } catch (InterruptedException ignored) {}
            if (protectedLoader != null) {
                protectedLoader.deactivate();
            }
        }, "axs-gc-finalize");
        t.setDaemon(true);
        t.start();
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
