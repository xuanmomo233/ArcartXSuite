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
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.bootstrap.SuiteCore;
import xuanmo.arcartxsuite.command.ArcartXSuiteCommand;
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
import xuanmo.arcartxsuite.security.GroundTransferGuard;
import xuanmo.arcartxsuite.security.MohistCompat;
import xuanmo.arcartxsuite.security.NativeBridge;
import xuanmo.arcartxsuite.security.protection.ProtectionInit;

import xuanmo.arcartxsuite.api.util.TemporaryOpExecutor;
import xuanmo.arcartxsuite.security.SecondaryPasswordService;

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

    /** 宿主真实插件实例（Bukkit 注册的 JavaPlugin）。 */
    private JavaPlugin host;
    private java.util.concurrent.CompletableFuture<Void> shutdownCompletion;
    private final java.util.concurrent.CompletableFuture<Void> hostStorageReady = new java.util.concurrent.CompletableFuture<>();
    private final java.util.concurrent.CompletableFuture<Void> crossServerModulesReady = new java.util.concurrent.CompletableFuture<>();
    private java.util.concurrent.CompletableFuture<SecondaryPasswordService> preparedPasswords;
    private java.util.concurrent.CompletableFuture<Boolean> rewardStorageRetry;

    private ClientPacketGuard clientPacketGuard;
    private final GroundTransferGuard groundTransferGuard = new GroundTransferGuard();
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
    private SecondaryPasswordService secondaryPasswordService;
    private xuanmo.arcartxsuite.selection.DefaultPolygonSelectionManager polygonSelectionManager;
    private xuanmo.arcartxsuite.scheduler.SchedulerServiceImpl schedulerService;
    private xuanmo.arcartxsuite.storage.StorageManagerImpl storageManager;
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
        groundTransferGuard.register(host);
        TemporaryOpExecutor.init(
            new File(getDataFolder(), "data/temp-op-ledger"),
            getLogger()
        );
        TemporaryOpExecutor.reconcile();
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

        // 1. ClientPacketGuard
        reloadClientPacketGuard();

        // 1.5 节流命令执行器（奖励/结算类命令削峰队列，每 tick 限量执行）
        xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor.init(
            host, getConfig().getInt("command-throttle-per-tick", 20));

        // 2. Bridges
        bridgeLifecycleManager = new BridgeLifecycleManager(host);
        if (!bridgeLifecycleManager.initialize(
            getConfig().getBoolean("tacz-compat.enabled", true),
            getConfig().getBoolean("tacz-compat.debug", false)
        )) {
            consoleError("ArcartX 桥接初始化失败，已禁用插件。");
            Bukkit.getPluginManager().disablePlugin(host);
            return;
        }

        // 3. 全局按键注册
        keybindService = new KeybindService(host, bridgeLifecycleManager.propBridge());
        keybindService.initialize(getConfig());

        // 4. 跨服传输
        crossServerService = new CrossServerService(host);
        crossServerService.start(crossServerModulesReady);

        // 5. 客户端事件转发到 ModuleRegistry
        clientEventLifecycleManager = new ClientEventLifecycleManager(host, () -> moduleRegistry);
        clientEventLifecycleManager.start();

        // 6. 统一 PAPI 解析器
        placeholderResolver = new PlaceholderResolverImpl();

        // 7. 统一数据源管理器（1.4.0+）
        try {
            storageManager = new xuanmo.arcartxsuite.storage.StorageManagerImpl(getConfig(), getDataFolder());
            consoleInfo("统一数据源已配置 (" + (storageManager.getDescriptor().isMysql() ? "MySQL" : "SQLite") + ")");
        } catch (Exception e) {
            consoleError("统一数据源配置失败: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(host);
            return;
        }

        // 8. ModuleRegistry：扫描并加载所有外部模块
        //    先创建统一调度服务（供所有模块使用日历触发/跨服单点调度）
        schedulerService = new xuanmo.arcartxsuite.scheduler.SchedulerServiceImpl(
            host,
            getLogger(),
            crossServerService != null ? crossServerService.nodeId() : "single",
            crossServerService,
            storageManager.getDataSource(),
            "axs_scheduler_leader");
        schedulerService.start();

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
            placeholderResolver,
            bridgeLifecycleManager.worldTextureBridge(),
            storageManager,
            schedulerService
        );
        moduleRegistry.attachCore(this);
        // 外部插件公开 API 门面：AxsCapabilities.get() 直连 capability 注册表，
        // 仅 @PublicCapability 标记的类型对外可见（过滤逻辑在 axs-api 门面侧）
        xuanmo.arcartxsuite.api.AxsCapabilities.setLookup(moduleRegistry::getCapability);
        // 8.5 多边形选区管理器（核心 capability，供 fishing/afkreward 等模块使用）
        polygonSelectionManager = new xuanmo.arcartxsuite.selection.DefaultPolygonSelectionManager();
        String selectionWand = getConfig().getString("selection.wand-item", "STICK");
        polygonSelectionManager.setWandMaterial(selectionWand);
        getServer().getPluginManager().registerEvents(
            (org.bukkit.event.Listener) polygonSelectionManager, host);
        moduleRegistry.registerCoreCapability(
            xuanmo.arcartxsuite.api.selection.PolygonSelectionCapability.class,
            (xuanmo.arcartxsuite.api.selection.PolygonSelectionCapability) () -> polygonSelectionManager);

        // 8.6 SystemMail 降级适配器（core capability，在模块加载前注册）
        // 如果 AXS mail 模块加载，会覆盖此 capability；如果 mail 模块未加载但 SystemMail 存在，
        // 所有依赖 MailDispatchable 的模块将降级使用 SystemMail 发送邮件
        if (xuanmo.arcartxsuite.bridge.SystemMailMailDispatchable.isSystemMailReady()) {
            moduleRegistry.registerCoreCapability(
                xuanmo.arcartxsuite.api.capability.MailDispatchable.class,
                new xuanmo.arcartxsuite.bridge.SystemMailMailDispatchable(getLogger()));
            consoleInfo("检测到 SystemMail 插件，已注册邮件降级适配器（MailDispatchable → SystemMail）");
        }

        var localModules = moduleRegistry.loadAll(this::prepareHostStorage);

        // 9. 聊天签名绕过（Paper 1.21+ 混合登录兼容）
        // 在宿主准入运行时就绪后处理聊天会话，账号分类不能代替签名状态。
        boolean chatSignBypassEnabled = getConfig().getBoolean("chat-sign-bypass.enabled", true);
        chatSignBypassService = new ChatSignBypassService(
            host, chatSignBypassEnabled
        );
        chatSignBypassService.initialize();

        // 云端同步等待本地依赖就绪。
        cloudModuleService = new CloudModuleService(this, moduleRegistry);

        // 11. 注册主命令
        PluginCommand command = getCommand("arcartxsuite");
        if (command != null) {
            ArcartXSuiteCommand handler = new ArcartXSuiteCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        // 13. 异步版本检查
        versionCheckService = new VersionCheckService(host);
        versionCheckService.checkAsync();
        getServer().getPluginManager().registerEvents(versionCheckService, host);

        // 14. authlib-injector 多方认证管理（默认禁用，按需显式开启）
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

        localModules.whenComplete((summary, failure) -> {
            if (!host.isEnabled()) return;
            if (failure != null) {
                crossServerModulesReady.completeExceptionally(failure);
                consoleError("模块加载未完成: " + failure.getMessage());
                return;
            }
            try { finishModuleStartup(summary); }
            catch (RuntimeException | LinkageError startupFailure) {
                crossServerModulesReady.completeExceptionally(startupFailure);
                getLogger().log(java.util.logging.Level.SEVERE, "模块启动收尾失败", startupFailure);
            }
        });
    }

    private java.util.concurrent.CompletionStage<Void> prepareHostStorage() {
        var registry = moduleRegistry;
        var storage = storageManager;
        var rewards = (xuanmo.arcartxsuite.bridge.DefaultPendingRewardService) registry.pendingRewardService();
        if (crossServerService != null) rewards.setClaimOwner(crossServerService.nodeId());
        var config = getConfig();
        var folder = getDataFolder();
        var logger = getLogger();
        long timeoutSeconds = config.getLong("security.secondary-password.unlock-timeout-seconds", 1800L);
        int minLength = config.getInt("security.secondary-password.min-length", 4);
        int maxLength = config.getInt("security.secondary-password.max-length", 32);
        String base = "security.secondary-password.storage";
        var descriptor = "mysql".equalsIgnoreCase(config.getString(base + ".mode", "sqlite"))
            ? xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                config.getString(base + ".mysql.host", "127.0.0.1"), config.getInt(base + ".mysql.port", 3306),
                config.getString(base + ".mysql.database", "arcartxsuite"), config.getString(base + ".mysql.username", "root"),
                config.getString(base + ".mysql.password", ""), config.getInt(base + ".pool-size", 4), "")
            : xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite("secondary-password.db");
        preparedPasswords = storage.prepareHost(() -> {
            rewards.initialize();
            try {
                return new SecondaryPasswordService(folder, timeoutSeconds, minLength, maxLength,
                    storage.resolveModuleDataSource("secondary-password", null, folder, descriptor), descriptor, logger);
            } catch (Exception | LinkageError failure) {
                logger.log(java.util.logging.Level.SEVERE, "统一二级密码服务初始化失败", failure);
                return null;
            }
        });
        preparedPasswords.whenComplete((passwords, failure) -> {
            if (hostStorageReady.isDone()) return;
            Runnable publish = () -> {
                if (hostStorageReady.isDone() || !host.isEnabled()) return;
                try {
                    if (failure != null) throw new java.util.concurrent.CompletionException(failure);
                    if (passwords != null) {
                        secondaryPasswordService = passwords;
                        registry.registerCoreCapability(xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess.class, passwords);
                    }
                    resumePendingRewards(rewards);
                    hostStorageReady.complete(null);
                } catch (RuntimeException | LinkageError publishFailure) {
                    hostStorageReady.completeExceptionally(publishFailure);
                }
            };
            try {
                if (Bukkit.isPrimaryThread()) publish.run();
                else Bukkit.getScheduler().runTask(host, publish);
            } catch (RuntimeException | LinkageError dispatchFailure) {
                hostStorageReady.completeExceptionally(dispatchFailure);
            }
        });
        return hostStorageReady.copy();
    }

    private void resumePendingRewards(xuanmo.arcartxsuite.bridge.DefaultPendingRewardService rewards) {
        if (!rewards.storageReady()) return;
        rewards.startPendingItemDelivery();
        for (var player : Bukkit.getOnlinePlayers()) rewards.flushPlayer(player);
    }

    private void retryPendingRewardStorage() {
        if (storageManager == null || moduleRegistry == null || !hostStorageReady.isDone()
            || (rewardStorageRetry != null && !rewardStorageRetry.isDone())) return;
        if (!(moduleRegistry.pendingRewardService() instanceof xuanmo.arcartxsuite.bridge.DefaultPendingRewardService rewards)
            || rewards.storageReady()) return;
        rewardStorageRetry = storageManager.prepareHost(() -> {
            rewards.initialize();
            return rewards.storageReady();
        });
        rewardStorageRetry.thenAccept(ready -> {
            if (!ready || !host.isEnabled()) return;
            Bukkit.getScheduler().runTask(host, () -> {
                if (shutdownCompletion == null) resumePendingRewards(rewards);
            });
        });
    }

    private void finishModuleStartup(ModuleRegistry.LoadSummary summary) {
        consoleInfo("模块加载完成: 发现 " + summary.discoveredCount()
            + " | 已启用 " + summary.enabledCount() + " | 已跳过 " + summary.skippedCount()
            + " | 失败 " + summary.failedCount());
        if (!summary.enabledModules().isEmpty()) consoleInfo("启用模块: " + String.join(", ", summary.enabledModules()));
        if (!summary.skippedModules().isEmpty()) consoleInfo(ChatColor.GRAY + "跳过模块: " + String.join(", ", summary.skippedModules()));
        if (!summary.failedModules().isEmpty()) consoleWarn("失败模块: " + String.join(", ", summary.failedModules()));
        keybindService.reportDeferred();
        runConfigDiagnosis(null, false);
        printConfigDiagnosisSummary();
        cloudModuleService.syncModules().whenComplete((ignored, failure) -> {
            crossServerModulesReady.complete(null);
            if (failure == null) consoleInfo("[Cloud] 云端模块同步完成");
        });
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
                        InputStream input = moduleRegistry.openResource(ownerId, resourcePath, loader);
                        if (input != null) {
                            return input;
                        }
                    } catch (IOException ignored) {
                        // 跳过，回退直接读取
                    }
                }
                ClassLoader effective = loader != null ? loader : host.getClass().getClassLoader();
                InputStream input = effective.getResourceAsStream(resourcePath);
                if (input != null) return input;
                return MohistCompat.getResourceSafe(resourcePath, effective);
            },
            host.getClass().getClassLoader(),
            getLogger()
        );
        configDiagnosisStore = new ConfigDiagnosisStore();

        // 宿主 config.yml 作为一个 ModuleConfigSpec
        hostConfigSpecs.add(new ModuleConfigSpec(
            "axs-core",
            new ConfigSyncSpec("config.yml", "config.yml",
                SyncPolicy.builder()
                    .dynamicSection("currencies")
                    .dynamicSection("keybinds")
                    .build()),
            1, "config-version", "", List.of(
                ValidationRule.of("cloud.module-source", ValueType.STRING).withEnum(java.util.Set.of("cloud", "local")),
                ValidationRule.of("moderation.ignore-separators", ValueType.BOOLEAN),
                ValidationRule.of("moderation.redact.enabled", ValueType.BOOLEAN)
            )
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
    public java.util.concurrent.CompletionStage<Void> onDisable() {
        if (shutdownCompletion != null) return shutdownCompletion.copy();
        var completed = new java.util.concurrent.CompletableFuture<Void>();
        shutdownCompletion = completed;
        crossServerModulesReady.cancel(false);
        var sequence = new xuanmo.arcartxsuite.lifecycle.ShutdownSequence(getLogger());
        if (chatSignBypassService != null) sequence.stop("聊天签名桥", chatSignBypassService::shutdown);
        if (keybindService != null) sequence.stop("按键入口", keybindService::shutdown);
        if (cloudModuleService != null) sequence.await("云端任务", cloudModuleService::shutdown);
        if (moduleRegistry != null) {
            var modules = moduleRegistry;
            if (modules.pendingRewardService() instanceof xuanmo.arcartxsuite.bridge.DefaultPendingRewardService rewards) {
                sequence.await("待发物品存档核对", rewards::stopPendingItemDelivery);
            }
            sequence.await("模块及类加载器", modules::shutdown);
            sequence.stop("Mod 属性清理", () -> modules.modAttributes().shutdown());
        }
        sequence.stop("能力入口", () -> xuanmo.arcartxsuite.api.AxsCapabilities.setLookup(null));
        if (schedulerService != null) sequence.await("调度器", schedulerService::shutdown);
        hostStorageReady.completeExceptionally(new java.util.concurrent.CancellationException("宿主正在关闭"));
        if (preparedPasswords != null) sequence.await("二级密码任务", () ->
            preparedPasswords.handle((passwords, failure) -> passwords).thenCompose(passwords -> {
                if (passwords == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
                passwords.close();
                return passwords.stopped();
            }));
        if (polygonSelectionManager != null) sequence.stop("选区", polygonSelectionManager::clearAll);
        if (crossServerService != null) sequence.await("跨服后台任务", crossServerService::shutdown);
        if (clientEventLifecycleManager != null) sequence.stop("客户端入口", clientEventLifecycleManager::stop);
        if (bridgeLifecycleManager != null) sequence.stop("ArcartX 桥接", bridgeLifecycleManager::shutdown);
        if (clientPacketGuard != null) sequence.stop("客户端包守卫", clientPacketGuard::shutdown);
        sequence.stop("掉落物守卫", groundTransferGuard::unregister);
        sequence.stop("命令入口", xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor::shutdown);
        if (moduleRegistry != null) sequence.await("命令奖励回执", moduleRegistry::stopPendingCommandReceipts);
        var storage = storageManager;
        chatSignBypassService = null;
        keybindService = null;
        cloudModuleService = null;
        moduleRegistry = null;
        schedulerService = null;
        secondaryPasswordService = null;
        polygonSelectionManager = null;
        crossServerService = null;
        storageManager = null;
        clientPacketGuard = null;
        clientPacketGuardConfiguration = null;
        sequence.finish(() -> storage == null ? java.util.concurrent.CompletableFuture.completedFuture(null) : storage.shutdown())
            .whenComplete((ignored, failure) -> {
                if (failure == null) {
                    try { getLogger().info("Suite 后台资源已全部退出。"); }
                    catch (Throwable ignoredLogging) { /* Server logging may already be stopped by another JVM hook. */ }
                    finally { completed.complete(null); }
                } else completed.completeExceptionally(failure);
            });
        return completed.copy();
    }

    // ─── 公共访问 ─────────────────────────────────────────────

    public ModuleRegistry getModuleRegistry() {
        return hostStorageReady.isDone() && !hostStorageReady.isCompletedExceptionally() ? moduleRegistry : null;
    }

    public SecondaryPasswordService getSecondaryPasswordService() {
        return secondaryPasswordService;
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
        if (clientPacketGuard == null) {
            clientPacketGuard = new ClientPacketGuard(host, clientPacketGuardConfiguration);
        } else {
            // 实例保持稳定、只热替换配置：路由层与各模块捕获的 PacketGuardAPI
            // 引用在 reload 后仍然有效（此前重建实例会导致 router/模块继续
            // 持有已 shutdown 的旧实例，reload 不生效）。
            clientPacketGuard.updateConfiguration(clientPacketGuardConfiguration);
        }
        // start() 内部先 shutdown()——清空限流状态、按新配置重启清理任务。
        clientPacketGuard.start();
    }

    // ─── 本体 config 热重载 ───────────────────────────────────

    /**
     * 热重载宿主 config.yml 并刷新可安全热重载的子系统。
     * <p>
     * 以下子系统会立即生效：
     * <ul>
     *   <li>ClientPacketGuard（客户端包频率限制配置）</li>
     *   <li>KeybindService（全局按键定义 keybinds 节；模块注册的 handler 会在后续 reloadAll 时重新注册）</li>
     *   <li>多边形选区 wand 物品（selection.wand-item）</li>
     *   <li>全局桥接（currencies / attribute-bridges，由 ModuleRegistry.reloadAll 内部重新初始化）</li>
     *   <li>配置诊断（重新跑一轮全量诊断）</li>
     * </ul>
     * 以下子系统<strong>不会</strong>热重载，需重启服务器生效：
     * <ul>
     *   <li>统一数据源（storage.mode SQLite↔MySQL 切换）</li>
     *   <li>跨服传输（cross-server Redis/Proxy 后端）</li>
     *   <li>统一二级密码（security.secondary-password 存储模式）</li>
     *   <li>多方认证（auth.enabled / auth.mixed-proxy-port / auth.yggdrasil-source）</li>
     *   <li>ArcartX 桥接（tacz-compat）</li>
     *   <li>聊天签名绕过（chat-sign-bypass）</li>
     * </ul>
     */
    public void reloadCoreConfig() {
        // 1. 重新读取 config.yml
        reloadConfig();

        // 2. ClientPacketGuard
        reloadClientPacketGuard();

        // 3. KeybindService — 先 shutdown 清理旧按键注册，再 initialize 重新注册
        if (keybindService != null) {
            keybindService.shutdown();
            keybindService.initialize(getConfig());
        }

        // 4. 多边形选区 wand 物品
        if (polygonSelectionManager != null) {
            String selectionWand = getConfig().getString("selection.wand-item", "STICK");
            polygonSelectionManager.setWandMaterial(selectionWand);
        }

        // 5. 重新跑一轮全量配置诊断（只报警，不写盘）
        runConfigDiagnosis(null, false);
        retryPendingRewardStorage();

        consoleInfo("宿主 config.yml 已热重载（部分子系统需重启生效，详见 /axs reload 提示）。");
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
        getLogger().info(message);
    }

    public void consoleWarn(String message) {
        getLogger().warning(message);
    }

    public void consoleError(String message) {
        getLogger().severe(message);
    }
}
