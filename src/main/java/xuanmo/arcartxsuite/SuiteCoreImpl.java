package xuanmo.arcartxsuite;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.config.ConfigDiagnosisReport;
import xuanmo.arcartxsuite.lifecycle.BridgeLifecycleManager;
import xuanmo.arcartxsuite.lifecycle.ClientEventLifecycleManager;
import xuanmo.arcartxsuite.api.config.ConfigIssueSeverity;
import xuanmo.arcartxsuite.api.config.ConfigSyncSpec;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.bootstrap.SuiteCore;
import xuanmo.arcartxsuite.command.ArcartXSuiteCommand;
import xuanmo.arcartxsuite.config.ProtectedResourceStore;
import xuanmo.arcartxsuite.config.diagnostic.ConfigDiagnosisStore;
import xuanmo.arcartxsuite.config.diagnostic.ConfigDiagnosticEngine;
import xuanmo.arcartxsuite.config.diagnostic.RetentionCleaner;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import xuanmo.arcartxsuite.chat.ChatSignBypassService;
import xuanmo.arcartxsuite.crossserver.CrossServerService;
import xuanmo.arcartxsuite.keybind.KeybindService;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import xuanmo.arcartxsuite.module.PluginConsoleLogger;
import xuanmo.arcartxsuite.placeholder.PlaceholderResolverImpl;
import xuanmo.arcartxsuite.module.VersionCheckService;
import xuanmo.arcartxsuite.cloud.CloudModuleService;
import xuanmo.arcartxsuite.security.ClientPacketGuard;
import xuanmo.arcartxsuite.security.ClientPacketGuardConfiguration;
import xuanmo.arcartxsuite.security.MohistCompat;
import xuanmo.arcartxsuite.security.NativeBridge;
import xuanmo.arcartxsuite.security.protection.ProtectionInit;

/**
 * ArcartXSuite 业务核心实现（Suite-core）。
 * <p>
 * 承接原宿主 {@code ArcartXSuitePlugin} 的全部业务编排（Bridges、ClientPacketGuard、
 * ModuleRegistry、云端同步、命令注册、配置诊断等）。本类被加密交付，待保护层
 * （ProtectedClassLoader）就位后由宿主薄壳反射加载，故不会触发验证期加载加密类。
 * <p>
 * 本类不再继承 {@link JavaPlugin}；它持有宿主真实插件引用 {@link #host}，
 * 需要 Bukkit 调度/事件/资源时一律使用 {@code host}，对业务子系统则暴露与原宿主
 * 同名的访问器（getModuleRegistry 等）以及若干 JavaPlugin 转发方法（getConfig 等）。
 */
public final class SuiteCoreImpl implements SuiteCore {

    private static final String AUTHOR_NAME = "墨墨墨 Q";
    private static final String DISPLAY_NAME = "ArcartXSuite";
    private static final String CONSOLE_PREFIX =
        ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;

    /** 宿主真实插件实例（Bukkit 注册的 JavaPlugin）。 */
    private JavaPlugin host;

    private ClientPacketGuard clientPacketGuard;
    private ClientPacketGuardConfiguration clientPacketGuardConfiguration;
    private ModuleRegistry moduleRegistry;
    private PlaceholderResolverImpl placeholderResolver;
    private CloudModuleService cloudModuleService;
    private KeybindService keybindService;
    private CrossServerService crossServerService;
    private ConfigDiagnosticEngine configDiagnosticEngine;
    private ConfigDiagnosisStore configDiagnosisStore;
    private VersionCheckService versionCheckService;
    private xuanmo.arcartxsuite.auth.AuthlibInjectorManager authlibInjectorManager;
    private xuanmo.arcartxsuite.auth.AuthCommand authCommand;
    private ChatSignBypassService chatSignBypassService;
    private BridgeLifecycleManager bridgeLifecycleManager;
    private ClientEventLifecycleManager clientEventLifecycleManager;
    /** 宿主自身的 spec（config.yml） */
    private final List<ModuleConfigSpec> hostConfigSpecs = new ArrayList<>();
    /** 外部模块提交的 spec： ownerId -> (spec, classLoader) */
    private final java.util.LinkedHashMap<String, ModuleSpecRegistration> moduleConfigSpecs = new java.util.LinkedHashMap<>();

    private record ModuleSpecRegistration(ModuleConfigSpec spec, ClassLoader classLoader) {}

    private PluginConsoleLogger consoleLogger;

    @Override
    public int apiVersion() {
        return SuiteCore.HOST_API_VERSION;
    }

    /** 宿主真实插件引用，供业务子系统注册调度/事件/资源使用。 */
    public JavaPlugin host() {
        return host;
    }

    // ─── JavaPlugin 转发（业务子系统沿用原宿主 API 调用方式） ──────────

    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        return host.getConfig();
    }

    public void reloadConfig() {
        host.reloadConfig();
    }

    public void saveConfig() {
        host.saveConfig();
    }

    public File getDataFolder() {
        return host.getDataFolder();
    }

    public org.bukkit.Server getServer() {
        return host.getServer();
    }

    public org.bukkit.plugin.PluginDescriptionFile getDescription() {
        return host.getDescription();
    }

    public InputStream getResource(String filename) {
        return host.getResource(filename);
    }

    public PluginCommand getCommand(String name) {
        return host.getCommand(name);
    }

    public java.util.logging.Logger getLogger() {
        if (consoleLogger == null) {
            consoleLogger = new PluginConsoleLogger(
                host.getDescription() == null ? "ArcartXSuite" : host.getDescription().getName(), null);
        }
        return consoleLogger;
    }

    @Override
    public void onEnable(JavaPlugin host) {
        this.host = host;
        ensureRootConfigExists();
        reloadConfig();
        printStartupBanner();
        consoleInfo("欢迎使用 " + DISPLAY_NAME);
        consoleInfo(ChatColor.RED + "作者保留一切权利，谢绝转载");
        if (MohistCompat.isMohist()) {
            consoleInfo(ChatColor.YELLOW + "检测到 Mohist 混合端，已启用兼容模式");
        }

        // 屏蔽 PlaceholderAPI 的 "Successfully registered internal expansion" 噪音日志，
        // 让控制台保持 ◆ ArcartXSuite | INFO: 前缀连续输出。
        suppressPlaceholderApiNoise();

        // 检查 Native 安全库
        if (!NativeBridge.isAvailable()) {
            consoleWarn("Native 安全库未加载: " + NativeBridge.getLoadError()
                + "（如使用云端模块，请先构建 native/ 目录并确保 axs-native.dll 已打包到 jar）");
        }

        // JAR 保护子系统已在宿主薄壳静态初始化块中注入。
        if (!ProtectionInit.isInitialized()) {
            consoleWarn("[Protection] 保护子系统未初始化（明文 JAR 或注入失败，插件仍可运行）");
        }

        // 0. 初始化智能配置诊断系统
        initConfigDiagnostic();

        // 2. ClientPacketGuard
        reloadClientPacketGuard();

        // 3. Bridges
        bridgeLifecycleManager = new BridgeLifecycleManager(host);
        if (!bridgeLifecycleManager.initialize(
            getConfig().getBoolean("tacz-compat.enabled", true),
            getConfig().getBoolean("tacz-compat.debug", false)
        )) {
            consoleError("ArcartX 桥接初始化失败，已禁用插件。");
            Bukkit.getPluginManager().disablePlugin(host);
            return;
        }

        // 4. 全局按键注册
        keybindService = new KeybindService(host, bridgeLifecycleManager.propBridge());
        keybindService.initialize(getConfig());

        crossServerService = new CrossServerService(host);
        crossServerService.start();

        // 5. 客户端事件转发到 ModuleRegistry
        clientEventLifecycleManager = new ClientEventLifecycleManager(host, () -> moduleRegistry);
        clientEventLifecycleManager.start();

        // 7.5 统一 PAPI 解析器
        placeholderResolver = new PlaceholderResolverImpl();

        // 8. ModuleRegistry：扫描并加载所有外部模块
        moduleRegistry = new ModuleRegistry(
            host,
            new File(getDataFolder(), "modules"),
            bridgeLifecycleManager.packetBridge(),
            bridgeLifecycleManager.clientBridge(),
            bridgeLifecycleManager.itemStackBridge(),
            bridgeLifecycleManager.propBridge(),
            clientPacketGuard,
            keybindService,
            bridgeLifecycleManager.taczCombatBridge(),
            crossServerService,
            placeholderResolver
        );
        moduleRegistry.attachCore(this);
        ModuleRegistry.LoadSummary summary = moduleRegistry.loadAll();
        consoleInfo(
            "模块加载完成: 发现 " + summary.discoveredCount()
                + " | 已启用 " + summary.enabledCount()
                + " | 已跳过 " + summary.skippedCount()
                + " | 失败 " + summary.failedCount()
        );
        if (!summary.enabledModules().isEmpty()) {
            consoleInfo("启用模块: " + String.join(", ", summary.enabledModules()));
        }
        if (!summary.skippedModules().isEmpty()) {
            consoleInfo(ChatColor.GRAY + "跳过模块: " + String.join(", ", summary.skippedModules()));
        }
        if (!summary.failedModules().isEmpty()) {
            consoleWarn("失败模块: " + String.join(", ", summary.failedModules()));
        }

        // 7. 聊天签名绕过（Paper 1.21+ 混合登录兼容）
        // 放在 ModuleRegistry 之后，确保 AccountTypeService 已就绪，实现按玩家类型精准 bypass
        boolean chatSignBypassEnabled = getConfig().getBoolean("chat-sign-bypass.enabled", true);
        boolean chatSignBypassOnlyNonPremium = getConfig().getBoolean("chat-sign-bypass.only-for-non-premium", true);
        chatSignBypassService = new ChatSignBypassService(
            host, chatSignBypassEnabled, chatSignBypassOnlyNonPremium,
            moduleRegistry.accountTypeService()
        );
        chatSignBypassService.initialize();

        // 9. 云端模块同步（异步，不阻塞启动；只要配置了 qq+password 就自动连接）
        cloudModuleService = new CloudModuleService(this, moduleRegistry);
        cloudModuleService.syncModules().thenRun(() -> {
            Bukkit.getScheduler().runTask(host, () ->
                consoleInfo("[Cloud] 云端模块同步完成")
            );
        });

        // 7. 注册主命令
        PluginCommand command = getCommand("arcartxsuite");
        if (command != null) {
            ArcartXSuiteCommand handler = new ArcartXSuiteCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        // 8. 跑一轮全量诊断（只报警，不写盘）
        runConfigDiagnosis(null, false);
        printConfigDiagnosisSummary();

        // 10. 异步版本检查
        versionCheckService = new VersionCheckService(host);
        versionCheckService.checkAsync();
        getServer().getPluginManager().registerEvents(versionCheckService, host);

        // 11. authlib-injector 多方认证管理（默认禁用，按需显式开启）
        boolean authEnabled = getConfig().getBoolean("auth.enabled", false);
        if (authEnabled) {
            int mixedProxyPort = getConfig().getInt("auth.mixed-proxy-port", 25599);
            authlibInjectorManager = new xuanmo.arcartxsuite.auth.AuthlibInjectorManager(getLogger(), getDataFolder(), mixedProxyPort);
            authCommand = new xuanmo.arcartxsuite.auth.AuthCommand(authlibInjectorManager, getLogger());

            // 异步检测版本 + 混合代理就绪状态（避免阻塞启动）
            // 注意：本地混合代理是【独立进程】，由 start-mixed-auth 脚本在服务器启动前拉起，
            // 本插件不再负责启动/停止，这里只做就绪探测与提示。
            String yggdrasilSource = getConfig().getString("auth.yggdrasil-source", "https://littleskin.cn/api/yggdrasil?mixed");
            boolean expectMixed = yggdrasilSource.contains("?mixed");
            boolean autoCheckVersion = getConfig().getBoolean("auth.auto-check-version", false);
            getServer().getScheduler().runTaskAsynchronously(host, () -> {
                if (autoCheckVersion) {
                    authlibInjectorManager.checkVersionAndNotify();
                }
                authlibInjectorManager.checkServerProperties();
                if (expectMixed) {
                    if (authlibInjectorManager.isMixedProxyReachable()) {
                        consoleInfo(ChatColor.AQUA + "本地混合代理就绪 | " + authlibInjectorManager.getMixedProxyUrl());
                    } else {
                        consoleInfo(ChatColor.YELLOW + "未检测到本地混合代理（端口 "
                            + authlibInjectorManager.getMixedProxyPort()
                            + "）。如需 LittleSkin+正版 混合登录，请用 start-mixed-auth 脚本启动服务器（执行 /axs auth setup 生成）。");
                    }
                }
            });
        }

        consoleInfo(ChatColor.GREEN + "加载完成");
    }

    // ─── 智能配置诊断 ─────────────────────────────

    private void initConfigDiagnostic() {
        configDiagnosticEngine = new ConfigDiagnosticEngine(
            getDataFolder(),
            Instant.now(),
            (ownerId, resourcePath, loader) -> {
                // 对于外部模块，优先走 ModuleRegistry
                if (moduleRegistry != null && ownerId != null && !"axs-core".equalsIgnoreCase(ownerId)) {
                    try {
                        InputStream input = moduleRegistry.openProtectedResource(ownerId, resourcePath, loader);
                        if (input != null) {
                            return input;
                        }
                    } catch (IOException ignored) {
                        // 跳过，回退 ProtectedResourceStore
                    }
                }
                if (ProtectedResourceStore.exists(resourcePath, loader)) {
                    return ProtectedResourceStore.open(resourcePath, loader);
                }
                ClassLoader effective = loader != null ? loader : host.getClass().getClassLoader();
                return MohistCompat.getResourceSafe(resourcePath, effective);
            },
            host.getClass().getClassLoader(),
            getLogger(),
            () -> moduleRegistry == null ? java.util.Set.of() : moduleRegistry.scanAvailableModuleIds()
        );
        configDiagnosisStore = new ConfigDiagnosisStore();

        // 宿主 config.yml 作为一个 ModuleConfigSpec
        hostConfigSpecs.add(ModuleConfigSpec.basic(
            "axs-core",
            new ConfigSyncSpec("config.yml", "config.yml",
                SyncPolicy.builder()
                    .dynamicSection("currencies")
                    .dynamicSection("keybinds")
                    .build())
        ));

        // 启动时清理过期诊断/备份目录
        try {
            new RetentionCleaner(getLogger(), Duration.ofDays(7), Duration.ofDays(60), 5)
                .cleanup(getDataFolder());
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * 模块加载期间注册其声明的 ConfigSpec 并跑一次诊断。
     * <p>
     * 由 {@code ModuleRegistry.loadAndEnable} 在调用模块 {@code onEnable} 前调用。
     */
    public void registerModuleConfigSpecs(String ownerId, List<ModuleConfigSpec> specs, ClassLoader moduleClassLoader) {
        if (configDiagnosticEngine == null || specs == null || specs.isEmpty()) {
            return;
        }
        for (ModuleConfigSpec spec : specs) {
            moduleConfigSpecs.put(spec.ownerId(), new ModuleSpecRegistration(spec, moduleClassLoader));
            // 立即跑诊断（以便报告在 onEnable 之前可见，启动阶段不写盘）
            ConfigDiagnosisReport report = configDiagnosticEngine.diagnose(spec, moduleClassLoader, false);
            configDiagnosisStore.put(spec, report);
        }
        // 启动阶段不写盘，诊断结果仅保留在内存中供控制台摘要显示
    }

    /** 除名外部模块的 spec（模块卸载时）。 */
    public void unregisterModuleConfigSpecs(String ownerId) {
        // 保留诊断报告供查询，只卸载 source spec
        moduleConfigSpecs.remove(ownerId);
    }

    /** 对全量或单个 ownerId 跑一次诊断。 */
    public void runConfigDiagnosis(String ownerId, boolean writeArtifacts) {
        if (configDiagnosticEngine == null) {
            return;
        }
        List<ConfigDiagnosisReport> reports = new ArrayList<>();
        // 宿主主表
        for (ModuleConfigSpec spec : hostConfigSpecs) {
            if (ownerId != null && !ownerId.equalsIgnoreCase(spec.ownerId())) {
                continue;
            }
            ConfigDiagnosisReport report = configDiagnosticEngine.diagnose(
                spec, host.getClass().getClassLoader(), writeArtifacts);
            configDiagnosisStore.put(spec, report);
            reports.add(report);
        }
        // 模块主表
        for (ModuleSpecRegistration reg : moduleConfigSpecs.values()) {
            if (ownerId != null && !ownerId.equalsIgnoreCase(reg.spec().ownerId())) {
                continue;
            }
            ConfigDiagnosisReport report = configDiagnosticEngine.diagnose(
                reg.spec(), reg.classLoader(), writeArtifacts);
            configDiagnosisStore.put(reg.spec(), report);
            reports.add(report);
        }
        if (!writeArtifacts) {
            return;
        }
        if (ownerId == null) {
            configDiagnosticEngine.writeSummary(reports);
        } else {
            // 单模块诊断后重写 summary（合并全量）
            List<ConfigDiagnosisReport> all = new ArrayList<>();
            for (var entry : configDiagnosisStore.all()) {
                all.add(entry.report());
            }
            configDiagnosticEngine.writeSummary(all);
        }
    }

    private void printConfigDiagnosisSummary() {
        if (configDiagnosticEngine == null) {
            return;
        }
        long info = 0, warn = 0, err = 0;
        for (var e : configDiagnosisStore.all()) {
            info += e.report().countOf(ConfigIssueSeverity.INFO);
            warn += e.report().countOf(ConfigIssueSeverity.WARN);
            err += e.report().countOf(ConfigIssueSeverity.ERROR);
        }
        consoleInfo("配置诊断: " + configDiagnosisStore.all().size() + " 个目标, "
            + err + " ERROR / " + warn + " WARN / " + info + " INFO");
        if (info + warn + err > 0) {
            consoleInfo(ChatColor.YELLOW + "检测到配置问题。运行 /axs config diagnose 生成详细报告，随后用 preview / apply 查看和修复。");
        }
    }

    public ConfigDiagnosticEngine getConfigDiagnosticEngine() {
        return configDiagnosticEngine;
    }

    public ConfigDiagnosisStore getConfigDiagnosisStore() {
        return configDiagnosisStore;
    }

    @Override
    public void onDisable() {
        // 注意：本地混合代理是独立进程，由 start-mixed-auth 脚本管理，此处无需停止。
        if (chatSignBypassService != null) {
            chatSignBypassService.shutdown();
            chatSignBypassService = null;
        }
        if (keybindService != null) {
            keybindService.shutdown();
            keybindService = null;
        }
        if (cloudModuleService != null) {
            cloudModuleService.stopHeartbeat();
        }
        if (moduleRegistry != null) {
            moduleRegistry.unloadAll();
            moduleRegistry = null;
        }
        if (crossServerService != null) {
            crossServerService.shutdown();
            crossServerService = null;
        }
        if (clientEventLifecycleManager != null) {
            clientEventLifecycleManager.stop();
        }
        if (bridgeLifecycleManager != null) {
            bridgeLifecycleManager.shutdown();
        }
        if (clientPacketGuard != null) {
            clientPacketGuard.shutdown();
            clientPacketGuard = null;
        }
        clientPacketGuardConfiguration = null;
        // 保护子系统的关闭由宿主薄壳负责（ProtectionInit.shutdown）。
    }

    // ─── 公共访问 ─────────────────────────────────────────────

    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    public xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI getPacketBridge() {
        return bridgeLifecycleManager == null ? null : bridgeLifecycleManager.packetBridge();
    }

    public xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI getClientBridge() {
        return bridgeLifecycleManager == null ? null : bridgeLifecycleManager.clientBridge();
    }

    public xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI getItemStackBridge() {
        return bridgeLifecycleManager == null ? null : bridgeLifecycleManager.itemStackBridge();
    }

    public xuanmo.arcartxsuite.api.bridge.PropBridgeAPI getPropBridge() {
        return bridgeLifecycleManager == null ? null : bridgeLifecycleManager.propBridge();
    }

    public ClientPacketGuard getClientPacketGuard() {
        return clientPacketGuard;
    }

    public xuanmo.arcartxsuite.auth.AuthCommand getAuthCommand() {
        return authCommand;
    }

    public CloudModuleService getCloudModuleService() {
        return cloudModuleService;
    }

    public String describePacketBridgeMode() {
        return bridgeLifecycleManager == null ? "unavailable" : bridgeLifecycleManager.describePacketMode();
    }

    // ─── ClientPacketGuard ───────────────────────────────────

    private void reloadClientPacketGuard() {
        clientPacketGuardConfiguration = ClientPacketGuardConfiguration.load(getConfig(), getLogger());
        if (clientPacketGuard != null) {
            clientPacketGuard.shutdown();
        }
        clientPacketGuard = new ClientPacketGuard(host, clientPacketGuardConfiguration);
        clientPacketGuard.start();
    }

    // ─── 资源/输出辅助 ────────────────────────────────────────

    private void ensureRootConfigExists() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("无法创建插件数据目录: " + getDataFolder().getAbsolutePath());
        }
        File rootConfigFile = new File(getDataFolder(), "config.yml");
        if (!rootConfigFile.exists()) {
            try {
                writeBundledResource("config.yml", rootConfigFile);
            } catch (IOException exception) {
                throw new IllegalStateException("写出 config.yml 失败", exception);
            }
        }
    }

    private InputStream openBundledResource(String resourcePath) throws IOException {
        if (ProtectedResourceStore.exists(resourcePath)) {
            return ProtectedResourceStore.open(resourcePath);
        }
        InputStream input = getResource(resourcePath);
        if (input != null) return input;
        return MohistCompat.getResourceSafe(resourcePath, host.getClass().getClassLoader());
    }

    private void writeBundledResource(String resourcePath, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            Files.createDirectories(parent.toPath());
        }
        try (InputStream input = openBundledResource(resourcePath)) {
            if (input == null) {
                throw new IOException("未找到资源: " + resourcePath);
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void printStartupBanner() {
        CommandSender console = Bukkit.getConsoleSender();
        String version = getDescription() == null ? "" : getDescription().getVersion();
        // ArcartX 部分按列水平渐变（青→深青→蓝→深蓝→紫），Suite 部分使用金色
        // banner.txt 每行：前 43 列 = ArcartX，第 44 列起 = Suite
        final int arcartxWidth = 44;
        final ChatColor[] gradient = {
            ChatColor.AQUA, ChatColor.DARK_AQUA, ChatColor.BLUE, ChatColor.DARK_BLUE
        };
        console.sendMessage("");
        try (InputStream in = MohistCompat.getResourceSafe("banner.txt", host.getClass().getClassLoader())) {
            if (in == null) {
                console.sendMessage(ChatColor.GOLD + "ArcartXSuite");
            } else {
                java.util.Scanner scanner = new java.util.Scanner(in, java.nio.charset.StandardCharsets.UTF_8);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    StringBuilder sb = new StringBuilder();
                    int len = line.length();
                    int leftLen = Math.min(arcartxWidth, len);
                    // 左侧 ArcartX：按列渐变
                    for (int i = 0; i < leftLen; i++) {
                        int seg = (gradient.length * i) / Math.max(1, arcartxWidth);
                        if (seg >= gradient.length) seg = gradient.length - 1;
                        if (i == 0 || (gradient.length * i) / Math.max(1, arcartxWidth)
                                    != (gradient.length * (i - 1)) / Math.max(1, arcartxWidth)) {
                            sb.append(gradient[seg]);
                        }
                        sb.append(line.charAt(i));
                    }
                    // 右侧 Suite：金色
                    if (len > arcartxWidth) {
                        sb.append(ChatColor.GOLD).append(line, arcartxWidth, len);
                    }
                    console.sendMessage(sb.toString());
                }
            }
        } catch (IOException ignored) {
            console.sendMessage(ChatColor.GOLD + "ArcartXSuite");
        }
        console.sendMessage("");
        console.sendMessage(ChatColor.GRAY + "  版本: " + ChatColor.YELLOW + "v" + version
            + ChatColor.GRAY + "  |  作者: " + ChatColor.WHITE + AUTHOR_NAME + "Q联系 1451759359");
        console.sendMessage("");
    }

    /**
     * 注册 JUL Filter 到 PlaceholderAPI 的 logger，屏蔽
     * {@code "Successfully registered internal expansion"} 这类批量噪音输出，
     * 让 ArcartXSuite 自己的 {@code ◆ ArcartXSuite | INFO:} 前缀在控制台保持连续。
     * <p>
     * 兼容性：保留 PAPI 已有的 Filter（如有）；若无 PAPI 则静默跳过。
     */
    private void suppressPlaceholderApiNoise() {
        try {
            org.bukkit.plugin.Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi == null) {
                return;
            }
            java.util.logging.Logger papiLogger = papi.getLogger();
            java.util.logging.Filter existing = papiLogger.getFilter();
            papiLogger.setFilter(record -> {
                String msg = record.getMessage();
                if (msg != null
                    && msg.contains("Successfully registered")
                    && msg.contains("expansion")) {
                    return false;
                }
                return existing == null || existing.isLoggable(record);
            });
        } catch (RuntimeException ignored) {
            // 任何反射/兼容性失败都不影响正常启动
        }
    }

    public void consoleInfo(String message) {
        Bukkit.getConsoleSender().sendMessage(CONSOLE_PREFIX + "INFO: " + message);
    }

    public void consoleWarn(String message) {
        Bukkit.getConsoleSender().sendMessage(CONSOLE_PREFIX + ChatColor.YELLOW + "WARN: " + message);
    }

    public void consoleError(String message) {
        Bukkit.getConsoleSender().sendMessage(CONSOLE_PREFIX + ChatColor.RED + "ERROR: " + message);
    }
}
