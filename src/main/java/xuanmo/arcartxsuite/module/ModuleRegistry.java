package xuanmo.arcartxsuite.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.AXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.ModuleLoadException;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeManager;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemMatcherSupport;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.bridge.ArcartXWorldTextureService;
import xuanmo.arcartxsuite.bridge.DefaultAttributeBridgeRegistry;
import xuanmo.arcartxsuite.bridge.DefaultAriaBridge;
import xuanmo.arcartxsuite.bridge.DefaultItemSourceRegistry;
import xuanmo.arcartxsuite.bridge.DefaultVanillaItemNameBridge;
import xuanmo.arcartxsuite.bridge.ContentModeratorImpl;
import xuanmo.arcartxsuite.bridge.TaczCombatBridge;
import xuanmo.arcartxsuite.condition.DefaultScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.crossserver.CrossServerService;
import xuanmo.arcartxsuite.keybind.KeybindService;
import xuanmo.arcartxsuite.security.ClientPacketGuard;

/**
 * 模块注册表：扫描、加载、启用、禁用和查询模块。
 */
public final class ModuleRegistry {

    private static final Logger LOGGER = new PluginConsoleLogger("AXS-ModuleRegistry", null);
    private static final List<String> ADMISSION_MODULES = List.of("loginview", "essentials");

    private final JavaPlugin plugin;
    /** 业务核心实例（用于 ConfigSpec 注册诊断），由 SuiteCoreImpl 在构造后注入。 */
    private xuanmo.arcartxsuite.SuiteCoreImpl core;
    private final File modulesDir;
    private final PacketBridgeAPI packetBridge;
    private final ClientBridgeAPI clientBridge;
    private final ItemBridgeAPI itemStackBridge;
    private final PropBridgeAPI propBridge;
    private final ClientPacketGuard packetGuard;
    private final ClientPacketQueue queuedClientPackets;
    private final KeybindService keybindService;
    private final TaczCombatBridge taczCombatBridge;
    private final CrossServerService crossServerService;
    private final ModuleSignatureVerifier signatureVerifier;
    private volatile java.util.function.Predicate<String> protectedModuleAuthorization = ignored -> false;

    public void setProtectedModuleAuthorization(java.util.function.Predicate<String> authorization) {
        protectedModuleAuthorization = java.util.Objects.requireNonNull(authorization);
    }

    private final PlaceholderResolverAPI placeholderResolver;
    private final ArcartXWorldTextureService worldTextureBridge;
    private final xuanmo.arcartxsuite.api.storage.StorageManager storageManager;
    private final xuanmo.arcartxsuite.bridge.PendingCommandReceipts pendingCommandReceipts;
    private final xuanmo.arcartxsuite.api.scheduler.SchedulerAPI scheduler;
    /** 持续药水效果服务：持有在 Registry 层，GlobalBridgeFactory 重建时实例不变，避免重复调度任务 */
    private final xuanmo.arcartxsuite.bridge.DefaultPotionAuraService potionAuraService;
    /** Mod 实体属性服务：持有在 Registry 层，实例不变 */
    private final xuanmo.arcartxsuite.bridge.DefaultModAttributeService modAttributeService;

    // ─── 拆分组件 ──────────────────────────────────────────────
    private final CapabilityRegistry capabilityRegistry;
    private final ClientPacketRouter clientPacketRouter;
    private GlobalBridgeFactory globalBridgeFactory;
    private volatile xuanmo.arcartxsuite.auth.runtime.LoginViewAuthentication authenticationRuntime;
    private xuanmo.arcartxsuite.auth.runtime.AdmissionGuard admissionGuard;
    private volatile boolean shuttingDown;
    private java.util.concurrent.CompletableFuture<Void> shutdownCompletion;

    public java.util.concurrent.CompletionStage<Void> shutdown() {
        if (shutdownCompletion != null) return shutdownCompletion.copy();
        var completed = new java.util.concurrent.CompletableFuture<Void>();
        shutdownCompletion = completed;
        shuttingDown = true;
        Throwable failure = null;
        try { unloadAll(); }
        catch (Throwable error) { failure = error; }
        try { if (globalBridgeFactory != null) globalBridgeFactory.detachGlobalListeners(); }
        catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        Throwable detachFailure = failure;
        java.util.concurrent.CompletableFuture.allOf(retiringModules.values()
            .toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((ignored, resourceFailure) -> {
                Throwable problem = detachFailure == null ? resourceFailure : detachFailure;
                if (detachFailure != null && resourceFailure != null && detachFailure != resourceFailure) detachFailure.addSuppressed(resourceFailure);
                if (problem == null) completed.complete(null); else completed.completeExceptionally(problem);
            });
        return completed.copy();
    }

    TaczCombatBridge taczCombatBridge() { return taczCombatBridge; }

    synchronized xuanmo.arcartxsuite.auth.runtime.LoginViewAuthentication authenticationRuntime() {
        if (authenticationRuntime == null) {
            xuanmo.arcartxsuite.api.auth.AuthAccess.install(() -> authenticationRuntime);
            if(admissionGuard==null) {
                admissionGuard = new xuanmo.arcartxsuite.auth.runtime.AdmissionGuard(
                    () -> authenticationRuntime == null || authenticationRuntime.requiresAdmission());
                org.bukkit.Bukkit.getPluginManager().registerEvents(admissionGuard, plugin);
                admissionGuard.registerPaperChat(plugin);
            }
            authenticationRuntime = xuanmo.arcartxsuite.auth.runtime.HostAuthentication.forLoginView(
                plugin, storageManager, () -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
            authenticationRuntime.start();
        }
        return authenticationRuntime;
    }

    /** 按 id 排列的已加载模块，保持加载顺序 */
    private final LinkedHashMap<String, LoadedModule> modules = new LinkedHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Void>> retiringModules = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Boolean>> pendingLoads = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile java.util.concurrent.CompletableFuture<ReloadSummary> reloadingAll;
    private CompletableFuture<LoadSummary> loadingLocal;
    private volatile boolean eventPacketAuctionConsumerRequired;
    /** 外部模块注册的命令处理器 */
    private final LinkedHashMap<String, ModuleCommandHandler> commandHandlers = new LinkedHashMap<>();

    public ModuleRegistry(
        JavaPlugin plugin,
        File modulesDir,
        PacketBridgeAPI packetBridge,
        ClientBridgeAPI clientBridge,
        ItemBridgeAPI itemStackBridge,
        PropBridgeAPI propBridge,
        ClientPacketGuard packetGuard,
        KeybindService keybindService,
        TaczCombatBridge taczCombatBridge,
        CrossServerService crossServerService,
        PlaceholderResolverAPI placeholderResolver,
        ArcartXWorldTextureService worldTextureBridge,
        xuanmo.arcartxsuite.api.storage.StorageManager storageManager,
        xuanmo.arcartxsuite.api.scheduler.SchedulerAPI scheduler
    ) {
        this.plugin = plugin;
        this.modulesDir = modulesDir;
        this.packetBridge = packetBridge;
        this.clientBridge = clientBridge;
        this.itemStackBridge = itemStackBridge;
        this.propBridge = propBridge;
        this.packetGuard = packetGuard;
        this.keybindService = keybindService;
        this.taczCombatBridge = taczCombatBridge;
        this.crossServerService = crossServerService;
        this.placeholderResolver = placeholderResolver;
        this.worldTextureBridge = worldTextureBridge;
        this.storageManager = storageManager;
        this.pendingCommandReceipts = new xuanmo.arcartxsuite.bridge.PendingCommandReceipts(storageManager, LOGGER);
        this.scheduler = scheduler;
        this.potionAuraService = new xuanmo.arcartxsuite.bridge.DefaultPotionAuraService(plugin);
        this.modAttributeService = new xuanmo.arcartxsuite.bridge.DefaultModAttributeService(plugin);
        this.capabilityRegistry = new CapabilityRegistry();
        this.clientPacketRouter = new ClientPacketRouter(packetGuard);
        this.queuedClientPackets = new ClientPacketQueue(
            task -> scheduler.scheduleAtFixedRate(plugin, task, 1L, 1L), System::nanoTime, LOGGER);
        this.globalBridgeFactory = new GlobalBridgeFactory(plugin, placeholderResolver, storageManager, this, capabilityRegistry);
        String signatureKeysPath = "module-signature-public-keys";
        Object configuredKeys = plugin.getConfig().get(signatureKeysPath);
        List<String> sigPubKeys = plugin.getConfig().getStringList(signatureKeysPath);
        if (configuredKeys != null && sigPubKeys.isEmpty()
            && !(configuredKeys instanceof List<?> rawKeys && rawKeys.isEmpty())) {
            LOGGER.severe("[ModuleSignature] module-signature-public-keys 必须是 Ed25519 公钥列表。");
            sigPubKeys = List.of("");
        }
        if (plugin.getConfig().contains("module-signature-public-key")) {
            LOGGER.warning("[ModuleSignature] Legacy setting module-signature-public-key is no longer supported; use module-signature-public-keys (list). The legacy value will be ignored.");
        }
        this.signatureVerifier = new ModuleSignatureVerifier(sigPubKeys, LOGGER);
    }

    /** 由业务核心在构造完成后注入自身引用，用于 ConfigSpec 注册/诊断回调。 */
    public void attachCore(xuanmo.arcartxsuite.SuiteCoreImpl core) {
        this.core = core;
    }

    boolean customerFeaturesAllowed(String moduleId) {
        return core != null && core.customerFeaturesAllowed(moduleId);
    }

    // ─── 生命周期 ─────────────────────────────────────────────

    /**
     * 预扫描 modules/ 目录，返回发现的模块 id 集合（不做加载）。
     * 宿主在内置加载逻辑前调用此方法来判断哪些模块将由外部 Jar 接管。
     */
    public Set<String> scanAvailableModuleIds() {
        Set<String> ids = new HashSet<>();
        if (!modulesDir.isDirectory()) {
            return ids;
        }
        File[] jarFiles = modulesDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) {
            return ids;
        }
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                ids.add(descriptor.id());
            } catch (IOException | ModuleLoadException exception) {
                LOGGER.warning("预扫描模块 Jar 失败: " + jarFile.getName() + " | " + exception.getMessage());
            }
        }
        return ids;
    }

    /**
     * 扫描 modules/ 目录，解析描述符、验证密码、拓扑排序、加载和启用模块。
     */
    public CompletionStage<LoadSummary> loadAll(java.util.function.Supplier<CompletionStage<Void>> prepareHostStorage) {
        if (packetBridge != null) {
            packetBridge.resetUiRegistrationCount();
        }
        if (!modulesDir.isDirectory()) {
            modulesDir.mkdirs();
            LOGGER.fine("已创建模块目录: " + modulesDir.getAbsolutePath());
        }

        // 0. 初始化全局桥接单例（即使 modules/ 为空或后续热加载也需要）
        initializeGlobalBridges();
        return enableConfiguredLocalModules(loadRootConfig(), prepareHostStorage.get());
    }

    private CompletionStage<LoadSummary> enableConfiguredLocalModules(YamlConfiguration rootConfig) {
        return enableConfiguredLocalModules(rootConfig, CompletableFuture.completedFuture(null));
    }

    private CompletionStage<LoadSummary> enableConfiguredLocalModules(YamlConfiguration rootConfig, CompletionStage<Void> hostReady) {
        if (loadingLocal != null) return loadingLocal.copy();
        boolean eventPacketEnabled = isModuleEnabled(rootConfig, "eventpacket");
        if (!eventPacketEnabled) eventPacketAuctionConsumerRequired = false;
        File[] jarFiles = modulesDir.isDirectory()
            ? modulesDir.listFiles((dir, name) -> name.endsWith(".jar"))
            : null;
        if (jarFiles == null || jarFiles.length == 0) {
            LOGGER.fine("模块目录为空，未加载任何模块。");
            return hostReady.thenApply(ignored -> emptyLoadSummary());
        }

        // 1. 解析描述符
        Map<String, DiscoveredModule> discovered = new LinkedHashMap<>();
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                if (discovered.containsKey(descriptor.id())) {
                    LOGGER.warning("模块 id 冲突: " + descriptor.id()
                        + "（" + jarFile.getName() + " vs " + discovered.get(descriptor.id()).jarFile.getName() + "），跳过后者。");
                    continue;
                }
                discovered.put(descriptor.id(), new DiscoveredModule(descriptor, jarFile));
            } catch (IOException | ModuleLoadException exception) {
                LOGGER.warning("扫描模块 Jar 失败: " + jarFile.getName() + " | " + exception.getMessage());
            }
        }
        if (eventPacketEnabled && discovered.containsKey("eventpacket")) eventPacketAuctionConsumerRequired = true;

        // 2. enabled 检查
        Map<String, DiscoveredModule> authorized = new LinkedHashMap<>();
        List<String> skippedModules = new ArrayList<>();
        List<String> enabledModules = new ArrayList<>();
        List<String> failedModules = new ArrayList<>();
        for (Map.Entry<String, DiscoveredModule> entry : discovered.entrySet()) {
            String id = entry.getKey();
            DiscoveredModule dm = entry.getValue();
            if (modules.containsKey(id)) continue;

            String enabledPath = "modules." + id + ".enabled";
            if (!isModuleEnabled(rootConfig, id)) {
                if (!isModuleEnabledEntryPresent(rootConfig, id)) {
                    LOGGER.warning("检测到模块 " + dm.descriptor.name() + " (id=" + id + ") 但未启用；如需启用，请在 plugins/ArcartXSuite/config.yml 的 " + enabledPath + " 设为 true 后重启。");
                } else {
                    LOGGER.fine(dm.descriptor.name() + " 模块已在 config.yml 中关闭。");
                }
                skippedModules.add(dm.descriptor.name());
                continue;
            }

            authorized.put(id, dm);
        }

        // 3. 拓扑排序
        List<String> cyclicModules = new ArrayList<>();
        List<String> sorted = topologicalSort(authorized, cyclicModules);

        // 3.1 将循环依赖的模块标记为失败（仅计入 failedModules，避免与 skippedModules 重复统计）
        for (String cyclicId : cyclicModules) {
            LOGGER.warning("模块 " + cyclicId + " 因循环依赖被标记为失败，不予加载。");
            failedModules.add(cyclicId);
        }

        // 4. 按序加载与启用
        var result = new CompletableFuture<LoadSummary>();
        loadingLocal = result;
        CompletionStage<Void> sequence = CompletableFuture.completedFuture(null);
        for (String id : sorted) {
            var dm = authorized.get(id);
            if (!ADMISSION_MODULES.contains(id)) sequence = sequence.thenCompose(ignored -> hostReady);
            sequence = sequence.thenCompose(ignored -> {
                if (result.isDone() || shuttingDown) return CompletableFuture.completedFuture(null);
                return tryLoad(dm).handle((enabled, failure) -> {
                    if (failure != null) failedModules.add(dm.descriptor.name());
                    else if (enabled) enabledModules.add(dm.descriptor.name());
                    else skippedModules.add(dm.descriptor.name());
                    return null;
                });
            });
        }
        sequence.thenCompose(ignored -> hostReady).whenComplete((ignored, failure) -> {
            if (loadingLocal == result) loadingLocal = null;
            if (failure != null) { result.completeExceptionally(failure); return; }
            result.complete(new LoadSummary(
            discovered.size(),
            enabledModules.size(),
            skippedModules.size(),
            failedModules.size(),
            packetBridge == null ? 0 : packetBridge.successfulUiRegistrationCount(),
            enabledModules,
            skippedModules,
            failedModules
            ));
        });
        return result.copy();
    }

    private static LoadSummary emptyLoadSummary() {
        return new LoadSummary(0, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }

    /**
     * 逆序禁用并卸载所有模块。
     */
    public void unloadAll() {
        if (loadingLocal != null) loadingLocal.completeExceptionally(new java.util.concurrent.CancellationException("模块已卸载"));
        if (reloadingAll != null) reloadingAll.completeExceptionally(new java.util.concurrent.CancellationException("模块已卸载"));
        pendingLoads.values().forEach(result -> result.complete(false));
        List<CompletableFuture<Boolean>> interrupted = new ArrayList<>();
        List<String> ids = new ArrayList<>(modules.keySet());
        Collections.reverse(ids);
        for (String id : ids) {
            LoadedModule loaded = modules.get(id);
            if (loaded != null) {
                if (loaded.enabling() != null) interrupted.add(loaded.enabling());
                loaded.setEnabling(null);
                disableModule(loaded);
            }
        }
        for (String id : ids) {
            LoadedModule loaded = modules.get(id);
            if (loaded != null) {
                closeClassLoader(loaded);
            }
        }
        modules.clear();
        clearAllRegistrations();
        interrupted.forEach(result -> result.complete(false));
        if ((shuttingDown || !plugin.isEnabled()) && authenticationRuntime != null) {
            authenticationRuntime.close();
            authenticationRuntime = null;
            if (admissionGuard != null) org.bukkit.event.HandlerList.unregisterAll(admissionGuard);
            admissionGuard = null;
        }
        // 全局桥接单例的生命周期由 GlobalBridgeFactory 管理，此处重建并立即初始化，
        // 保证卸载后桥接对象始终就绪，支持「卸载全部 → 重新加载」场景。
        // 服务器关停路径（onDisable）中插件已被标记禁用，initialize() 内的
        // registerEvents/调度注册会抛 IllegalPluginAccessException，故仅在插件仍启用时重建。
        if (!shuttingDown && plugin.isEnabled()) {
            globalBridgeFactory.detachGlobalListeners();
            globalBridgeFactory = new GlobalBridgeFactory(plugin, placeholderResolver, storageManager, this, capabilityRegistry);
            globalBridgeFactory.initialize();
        }
    }

    /**
     * 重载指定模块（不卸载 ClassLoader）。
     */
    public java.util.concurrent.CompletionStage<Boolean> reloadModule(String moduleId) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) return onPrimaryStage(() -> reloadModule(moduleId));
        LoadedModule loaded = modules.get(moduleId);
        if (loaded != null && loaded.reloading() != null) return loaded.reloading().copy();
        if (loaded == null || !loaded.isEnabled() || shuttingDown) {
            LOGGER.warning("模块未加载或未启用，无法重载: " + moduleId);
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (loaded.moduleSeedDirect() != null && !protectedModuleAuthorization.test(moduleId)) {
            LOGGER.warning("受保护模块未取得有效运行授权，拒绝重载: " + moduleId);
            return CompletableFuture.completedFuture(false);
        }
        boolean inPlace = loaded.instance().reloadsInPlace();
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        loaded.setReloading(result);
        try {
            if (!inPlace) {
                loaded.setEnabled(false);
                DefaultModuleContext context = loaded.context();
                if (context != null) context.unregisterKeybindHandlers();
                removeCommandHandlers(loaded);
                removePacketHandlers(moduleId);
                removeInitializedHandlers(moduleId);
                removeCapabilities(moduleId);
            }
            loaded.instance().onReload().whenComplete((ignored, failure) ->
                dispatchReloadCompletion(loaded, result, inPlace, failure));
        } catch (Exception | LinkageError exception) {
            finishReload(loaded, result, inPlace, exception);
        }
        return result.copy();
    }

    private void dispatchReloadCompletion(LoadedModule loaded, java.util.concurrent.CompletableFuture<Boolean> result,
                                          boolean inPlace, Throwable failure) {
        Runnable finish = () -> finishReload(loaded, result, inPlace, failure);
        try {
            if (org.bukkit.Bukkit.isPrimaryThread()) finish.run();
            else if (!plugin.isEnabled()) result.complete(false);
            else org.bukkit.Bukkit.getScheduler().runTask(plugin, finish);
        } catch (RuntimeException | LinkageError error) {
            result.complete(false);
        }
    }

    private void finishReload(LoadedModule loaded, java.util.concurrent.CompletableFuture<Boolean> result,
                              boolean inPlace, Throwable failure) {
        String id = loaded.descriptor().id();
        if (modules.get(id) != loaded || loaded.reloading() != result) {
            result.complete(false);
            return;
        }
        loaded.setReloading(null);
        try {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, loaded.descriptor().name() + " 模块重载失败", failure);
                if (!inPlace) cleanupFailedModule(id);
                result.complete(false);
                return;
            }
            if (!inPlace) registerModuleEntryPoints(loaded);
            LOGGER.info(loaded.descriptor().name() + " 模块已重载。");
            result.complete(true);
        } catch (RuntimeException | LinkageError error) {
            if (!inPlace) cleanupFailedModule(id);
            result.complete(false);
        }
    }

    private void abandonReload(LoadedModule loaded) {
        var pending = loaded.reloading();
        loaded.setReloading(null);
        if (pending != null) pending.complete(false);
    }

    /**
     * 热卸载模块：执行 {@code onDisable} → 移除注册（命令/包/能力）→ 关闭 ClassLoader。
     * 卸载后 jar 仍在 {@code modules/} 中，可通过 {@link #loadModuleById(String)} 重新启用。
     *
     * @return 是否成功卸载
     */
    public boolean unloadModule(String moduleId) {
        var pending = pendingLoads.get(moduleId);
        if (pending != null) pending.complete(false);
        LoadedModule loaded = modules.get(moduleId);
        if (loaded == null) {
            if (pending != null) return true;
            LOGGER.warning("模块未加载，无法卸载: " + moduleId);
            return false;
        }
        // 检查反向依赖：若其他已启用模块声明依赖它，则禁止卸载
        List<String> dependents = new ArrayList<>();
        for (LoadedModule other : modules.values()) {
            if (other == loaded || (!other.isEnabled() && other.enabling() == null && other.reloading() == null)) continue;
            if (other.descriptor().depends().contains(moduleId)) {
                dependents.add(other.descriptor().id());
            }
        }
        if (!dependents.isEmpty()) {
            LOGGER.warning("无法卸载 " + moduleId + "，仍被依赖于: " + String.join(", ", dependents));
            return false;
        }

        var enabling = loaded.enabling();
        loaded.setEnabling(null);
        disableModule(loaded);
        modules.remove(moduleId, loaded);
        closeClassLoader(loaded);
        if (enabling != null) enabling.complete(false);
        LOGGER.info(loaded.descriptor().name() + " 模块已卸载，ClassLoader 在后台资源退出后释放。");
        return true;
    }

    /**
     * 加载云端模块：从内存中的 jar 字节数组直接加载并启用。
     *
     * @param jarBytes 解密后的 jar 字节数组
     * @return 是否成功加载并启用
     */
    /**
     * 加载云端模块：从内存中的 jar 字节数组直接加载并启用。
     *
     * @param jarBytes   解密后的 jar 字节数组
     * @param moduleSeed 云端下发的 32 字节 moduleKey
     * @return 是否成功加载并启用
     */
    public CompletionStage<Boolean> loadCloudModule(String expectedId, byte[] jarBytes, byte[] moduleSeed) {
        if (shuttingDown || moduleSeed == null || moduleSeed.length != 32) return CompletableFuture.completedFuture(false);
        String cloudModuleId = null;
        try {
            ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jarBytes);
            cloudModuleId = descriptor.id();
            if (!cloudModuleId.equals(expectedId)) {
                LOGGER.warning("云端模块 id 与请求不符，拒绝加载: " + expectedId + " / " + cloudModuleId);
                return CompletableFuture.completedFuture(false);
            }
            initializeGlobalBridges();
            String moduleId = cloudModuleId;
            if (modules.containsKey(moduleId)) {
                LOGGER.info("云端模块 " + moduleId + " 已加载，跳过。");
                return CompletableFuture.completedFuture(false);
            }
            if (!isModuleEnabled(loadRootConfig(), moduleId)) {
                LOGGER.fine("云端模块 " + moduleId + " 已在 config.yml 中关闭，跳过加载。");
                return CompletableFuture.completedFuture(false);
            }
            if (packetBridge != null) {
                packetBridge.resetUiRegistrationCount();
            }
            return tryLoad(new DiscoveredModule(descriptor, jarBytes, moduleSeed)).exceptionally(failure -> false);
        } catch (Exception | LinkageError exception) {
            LOGGER.log(Level.SEVERE, "加载云端模块失败", exception);
            return CompletableFuture.completedFuture(false);
        } catch (StackOverflowError error) {
            // StackOverflowError 是 VirtualMachineError，不会被上面的 catch(Exception|LinkageError) 捕获。
            // 模块加载过程中的无限递归（如配置树循环引用、toString/equals 循环）会抛出此错误，
            // 若不在此捕获则会直接传播到 Bukkit 调度器，丢失模块上下文信息。
            LOGGER.log(Level.SEVERE, "加载云端模块 " + (cloudModuleId != null ? cloudModuleId : "<未知>")
                + " 时发生 StackOverflowError"
                + "（可能由配置树循环引用或递归方法无终止条件引起）", error);
            if (cloudModuleId != null) {
                cleanupFailedModule(cloudModuleId);
            }
            return CompletableFuture.completedFuture(false);
        }
    }

    public boolean validateCloudModule(String expectedId, byte[] jarBytes, byte[] moduleSeed) {
        if (shuttingDown) return false;
        if (moduleSeed == null || moduleSeed.length != 32) return false;
        try {
            ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jarBytes);
            if (!descriptor.id().equals(expectedId)) {
                LOGGER.warning("云端模块 id 与请求不符，拒绝更新: " + expectedId + " / " + descriptor.id());
                return false;
            }
            initializeGlobalBridges();
            if (!isModuleEnabled(loadRootConfig(), descriptor.id())) {
                LOGGER.fine("云端模块 " + descriptor.id() + " 已在 config.yml 中关闭，跳过加载。");
                return false;
            }
            for (String externalPlugin : descriptor.externalDepends()) {
                org.bukkit.plugin.Plugin dep = Bukkit.getPluginManager().getPlugin(externalPlugin);
                if (dep == null) {
                    LOGGER.warning(descriptor.name() + " 模块需要 " + externalPlugin + " 插件，已跳过加载。");
                    return false;
                }
                if (!dep.isEnabled()) {
                    LOGGER.warning(descriptor.name() + " 模块依赖的外部插件 " + externalPlugin + " 已安装但未启用，已跳过加载。");
                    return false;
                }
            }
            for (String depId : descriptor.depends()) {
                if (!isModuleLoaded(depId)) {
                    LOGGER.warning(descriptor.name() + " 模块依赖 " + depId + " 未就绪，已跳过加载。");
                    return false;
                }
            }
            // 外部可选插件依赖：缺失仅提示，不影响校验通过
            for (String externalPlugin : descriptor.externalSoftDepends()) {
                org.bukkit.plugin.Plugin softDep = Bukkit.getPluginManager().getPlugin(externalPlugin);
                if (softDep == null) {
                    LOGGER.info(descriptor.name() + " 模块的可选外部插件 " + externalPlugin + " 未安装，相关功能将不可用。");
                } else if (!softDep.isEnabled()) {
                    LOGGER.info(descriptor.name() + " 模块的可选外部插件 " + externalPlugin + " 已安装但未启用，相关功能将不可用。");
                }
            }
            return signatureVerifier.verify(descriptor);
        } catch (Exception | LinkageError exception) {
            LOGGER.log(Level.SEVERE, "校验云端模块失败", exception);
            return false;
        }
    }

    /**
     * 热加载模块：从 {@code modules/} 目录扫描指定 id 的 jar 并加载启用。
     * 若该模块已加载，返回 false 并提示。
     *
     * @return 是否成功加载并启用
     */
    public java.util.concurrent.CompletionStage<Boolean> loadModuleById(String moduleId) {
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        var existing = pendingLoads.putIfAbsent(moduleId, result);
        if (existing != null) return existing.copy();
        var deadline = new java.util.concurrent.CompletableFuture<Void>();
        deadline.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).whenComplete((ignored, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
        });
        var retiring = retiringModules.get(moduleId);
        var idle = retiring == null ? java.util.concurrent.CompletableFuture.<Void>completedFuture(null) : retiring;
        idle.whenComplete((ignored, failure) -> {
            deadline.complete(null);
            if (deadline.isCompletedExceptionally()) result.completeExceptionally(new java.util.concurrent.TimeoutException("旧模块资源尚未退出"));
            if (failure != null || result.isDone()) {
                if (failure != null) result.completeExceptionally(failure);
                pendingLoads.remove(moduleId, result);
                return;
            }
            onPrimaryStage(() -> {
                if (result.isDone()) return java.util.concurrent.CompletableFuture.completedFuture(false);
                return loadModuleByIdNow(moduleId);
            }).whenComplete((loaded, error) -> {
                pendingLoads.remove(moduleId, result);
                if (error != null) result.completeExceptionally(error);
                else result.complete(loaded);
            });
        });
        return result.copy();
    }

    private <T> java.util.concurrent.CompletionStage<T> onPrimaryStage(
            java.util.function.Supplier<? extends java.util.concurrent.CompletionStage<T>> action) {
        var result = new java.util.concurrent.CompletableFuture<T>();
        Runnable run = () -> {
            try {
                if (shuttingDown) throw new java.util.concurrent.CancellationException("宿主正在关闭");
                action.get().whenComplete((value, failure) -> {
                    if (failure != null) result.completeExceptionally(failure);
                    else result.complete(value);
                });
            } catch (Exception | LinkageError failure) { result.completeExceptionally(failure); }
        };
        try {
            if (org.bukkit.Bukkit.isPrimaryThread()) run.run();
            else if (!plugin.isEnabled()) result.completeExceptionally(new java.util.concurrent.CancellationException("宿主已关闭"));
            else org.bukkit.Bukkit.getScheduler().runTask(plugin, run);
        } catch (RuntimeException | LinkageError failure) { result.completeExceptionally(failure); }
        return result.copy();
    }

    private CompletionStage<Boolean> loadModuleByIdNow(String moduleId) {
        if (modules.containsKey(moduleId)) {
            LOGGER.warning("模块已加载: " + moduleId + "，如需重启请使用 reload 或 unload + load。");
            return CompletableFuture.completedFuture(false);
        }
        YamlConfiguration rootConfig = loadRootConfig();
        String enabledPath = "modules." + moduleId + ".enabled";
        if (!isModuleEnabled(rootConfig, moduleId)) {
            if (!isModuleEnabledEntryPresent(rootConfig, moduleId)) {
                File[] jarFiles = modulesDir.listFiles((dir, name) -> name.endsWith(".jar"));
                String moduleName = null;
                if (jarFiles != null) {
                    for (File jarFile : jarFiles) {
                        try (JarFile jar = new JarFile(jarFile)) {
                            ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                            if (moduleId.equals(descriptor.id())) {
                                moduleName = descriptor.name();
                                break;
                            }
                        } catch (IOException | ModuleLoadException exception) {
                            LOGGER.warning("解析模块 Jar 失败: " + jarFile.getName() + " | " + exception.getMessage());
                        }
                    }
                }
                if (moduleName != null) {
                    LOGGER.warning("检测到模块 " + moduleName + " (id=" + moduleId + ") 但未启用；如需启用，请在 plugins/ArcartXSuite/config.yml 的 " + enabledPath + " 设为 true 后重启。");
                } else {
                    LOGGER.warning("检测到模块 id=" + moduleId + " 但未启用；如需启用，请在 plugins/ArcartXSuite/config.yml 的 " + enabledPath + " 设为 true 后重启。");
                }
            } else {
                LOGGER.fine("模块 " + moduleId + " 已在 config.yml 中关闭，无法热加载。");
            }
            return CompletableFuture.completedFuture(false);
        }

        initializeGlobalBridges();
        if (!modulesDir.isDirectory()) {
            LOGGER.warning("模块目录不存在: " + modulesDir.getAbsolutePath());
            return CompletableFuture.completedFuture(false);
        }
        File[] jarFiles = modulesDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) {
            return CompletableFuture.completedFuture(false);
        }
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                if (!moduleId.equals(descriptor.id())) continue;

                // packetBridge UI 计数复位（仅用于本次加载日志）
                if (packetBridge != null) {
                    packetBridge.resetUiRegistrationCount();
                }
                return tryLoad(new DiscoveredModule(descriptor, jarFile)).exceptionally(failure -> false);
            } catch (IOException | ModuleLoadException exception) {
                LOGGER.warning("解析模块 Jar 失败: " + jarFile.getName() + " | " + exception.getMessage());
            }
        }
        LOGGER.warning("modules/ 目录未找到模块 id: " + moduleId);
        return CompletableFuture.completedFuture(false);
    }

    private volatile List<String> discoverableCache;
    private volatile long discoverableCacheTime;
    private static final long DISCOVERABLE_CACHE_TTL_MS = 30_000;

    /**
     * 列出 {@code modules/} 中存在但当前未加载的模块 id（用于 tab 补全）。
     * 结果缓存 30 秒以避免频繁扫描 JAR。
     */
    public List<String> discoverableModuleIds() {
        long now = System.currentTimeMillis();
        if (discoverableCache != null && (now - discoverableCacheTime) < DISCOVERABLE_CACHE_TTL_MS) {
            return discoverableCache;
        }
        List<String> ids = new ArrayList<>();
        if (!modulesDir.isDirectory()) return ids;
        File[] jarFiles = modulesDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null) return ids;
        for (File jarFile : jarFiles) {
            try (JarFile jar = new JarFile(jarFile)) {
                ModuleDescriptor descriptor = ModuleDescriptorParser.parse(jar);
                if (!modules.containsKey(descriptor.id())) {
                    ids.add(descriptor.id());
                }
            } catch (IOException | ModuleLoadException ignored) {
            }
        }
        discoverableCache = List.copyOf(ids);
        discoverableCacheTime = now;
        return discoverableCache;
    }

    public record ReloadSummary(List<String> stopped, List<String> started, List<String> failed, Set<String> pendingCloud) {}

    /** Applies configured module state on the server thread. Cloud downloads are scheduled by the caller. */
    public java.util.concurrent.CompletionStage<ReloadSummary> reloadAll() {
        if (!org.bukkit.Bukkit.isPrimaryThread()) return onPrimaryStage(this::reloadAll);
        if (loadingLocal != null) return loadingLocal.thenCompose(ignored -> reloadAll());
        if (reloadingAll != null) return reloadingAll.copy();
        YamlConfiguration root = readModuleReloadConfig();
        if (root == null) return java.util.concurrent.CompletableFuture.completedFuture(
            new ReloadSummary(List.of(), List.of(), List.of("config.yml"), Set.of()));
        if (!isModuleEnabled(root, "eventpacket")) eventPacketAuctionConsumerRequired = false;
        List<String> stopped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        stopDisabledModules(root, stopped, failed);
        var result = new java.util.concurrent.CompletableFuture<ReloadSummary>();
        reloadingAll = result;
        result.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
        var idle = java.util.concurrent.CompletableFuture.allOf(retiringModules.values().toArray(java.util.concurrent.CompletableFuture<?>[]::new));
        idle.whenComplete((ignored, failure) -> {
            onPrimaryStage(() -> {
                if (failure != null) return java.util.concurrent.CompletableFuture.<ReloadSummary>failedFuture(failure);
                if (result.isDone()) return java.util.concurrent.CompletableFuture.<ReloadSummary>failedFuture(
                    new java.util.concurrent.CancellationException("重载请求已结束"));
                return reloadConfiguredModules(root, stopped, failed, () -> !result.isDone());
            }).whenComplete((summary, error) -> {
                if (reloadingAll == result) reloadingAll = null;
                if (error != null) result.completeExceptionally(error);
                else result.complete(summary);
            });
        });
        return result.copy();
    }

    private java.util.concurrent.CompletionStage<ReloadSummary> reloadConfiguredModules(
            YamlConfiguration root, List<String> stopped, List<String> failed, java.util.function.BooleanSupplier active) {
        globalBridgeFactory.reload();
        if (authenticationRuntime != null && authenticationRuntime.isAdmissionSuspended()
            && !isModuleLoaded("loginview") && !isModuleEnabled(root, "loginview")) {
            authenticationRuntime.setLoginViewEnabled(false);
        }
        java.util.concurrent.CompletionStage<Void> reloaded = java.util.concurrent.CompletableFuture.completedFuture(null);
        for (LoadedModule loaded : List.copyOf(modules.values())) {
            String id = loaded.descriptor().id();
            reloaded = reloaded.thenCompose(ignored -> {
                if (shuttingDown || !active.getAsBoolean()) return java.util.concurrent.CompletableFuture.failedFuture(
                    new java.util.concurrent.CancellationException("宿主正在关闭"));
                if ((loaded.isEnabled() || loaded.reloading() != null) && modules.get(id) == loaded && isModuleEnabled(root, id)
                    && loaded.descriptor().depends().stream().allMatch(this::isModuleLoaded)) {
                    return reloadModule(id).thenAccept(ok -> { if (!ok) failed.add(id); });
                }
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            });
        }
        return reloaded.thenCompose(ignored -> {
            if (!active.getAsBoolean()) throw new java.util.concurrent.CancellationException("重载请求已结束");
            stopDisabledModules(root, stopped, failed);
            return enableConfiguredLocalModules(root);
        }).thenApply(loads -> {
            failed.addAll(loads.failedModules());
            Set<String> pendingCloud = new java.util.LinkedHashSet<>();
            var configured = root.getConfigurationSection("modules");
            for (String id : configured.getKeys(false)) {
                if (isModuleEnabled(root, id) && !isModuleLoaded(id)) pendingCloud.add(id);
            }
            return new ReloadSummary(List.copyOf(stopped), loads.enabledModules(), List.copyOf(failed), Set.copyOf(pendingCloud));
        });
    }

    /** Retry local modules whose hard dependencies have just finished loading from the cloud. */
    public CompletionStage<LoadSummary> enableConfiguredLocalModules() {
        YamlConfiguration root = readModuleReloadConfig();
        return root == null ? CompletableFuture.completedFuture(emptyLoadSummary()) : enableConfiguredLocalModules(root);
    }

    private YamlConfiguration readModuleReloadConfig() {
        YamlConfiguration root = new YamlConfiguration();
        try {
            root.load(new File(plugin.getDataFolder(), "config.yml"));
            if (!root.isConfigurationSection("modules")) {
                LOGGER.warning("config.yml 缺少 modules 节，无法应用模块启停。");
                return null;
            }
            return root;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            LOGGER.log(Level.WARNING, "无法读取模块启停配置", e);
            return null;
        }
    }

    private void stopDisabledModules(YamlConfiguration root, List<String> stopped, List<String> failed) {
        var running = modules.values().stream().map(LoadedModule::descriptor).toList();
        for (String id : modulesToStop(running, moduleId -> isModuleEnabled(root, moduleId))) {
            if (unloadModule(id)) stopped.add(id);
            else failed.add(id);
        }
    }

    static List<String> modulesToStop(List<ModuleDescriptor> running, java.util.function.Predicate<String> configuredEnabled) {
        Set<String> available = new HashSet<>();
        for (var module : running) if (configuredEnabled.test(module.id())) available.add(module.id());
        boolean removed;
        do {
            removed = false;
            for (var module : running) {
                if (!available.containsAll(module.depends())) removed |= available.remove(module.id());
            }
        } while (removed);
        List<String> stopped = new ArrayList<>();
        for (int i = running.size() - 1; i >= 0; i--) {
            String id = running.get(i).id();
            if (!available.contains(id)) stopped.add(id);
        }
        return stopped;
    }

    // ─── 查询 ────────────────────────────────────────────────

    public List<ModuleDescriptor> listModules() {
        List<ModuleDescriptor> descriptors = new ArrayList<>();
        for (LoadedModule loaded : modules.values()) {
            descriptors.add(loaded.descriptor());
        }
        return descriptors;
    }

    public boolean isModuleLoaded(String moduleId) {
        LoadedModule loaded = modules.get(moduleId);
        return loaded != null && loaded.isEnabled();
    }

    /** 供 GlobalBridgeFactory 的 ModuleAdminCapability 查找模块实例。 */
    LoadedModule getLoadedModule(String moduleId) {
        return modules.get(moduleId);
    }

    public int countEnabled() {
        int count = 0;
        for (LoadedModule loaded : modules.values()) {
            if (loaded.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    public Optional<ModuleCommandHandler> getCommandHandler(String moduleId) {
        return Optional.ofNullable(commandHandlers.get(moduleId));
    }

    public List<String> externalModuleIds() {
        List<String> ids = new ArrayList<>();
        for (LoadedModule loaded : modules.values()) {
            if (loaded.isEnabled()) {
                ids.add(loaded.descriptor().id());
            }
        }
        return ids;
    }

    /**
     * 获取当前已加载的云端模块ID列表（从内存 jarBytes 加载的模块）。
     */
    public List<String> getLoadedCloudModuleIds() {
        List<String> ids = new ArrayList<>();
        for (LoadedModule loaded : modules.values()) {
            if (loaded.moduleSeedDirect() != null && loaded.classLoader() instanceof ByteArrayModuleClassLoader) {
                ids.add(loaded.descriptor().id());
            }
        }
        return ids;
    }

    /** Stop revoked protected modules and their hard dependents through normal lifecycle retirement. */
    public List<String> revokeProtectedModulesNotIn(java.util.Set<String> allowed) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) throw new IllegalStateException("Module revocation requires the server thread");
        var denied = new java.util.LinkedHashSet<String>();
        for (LoadedModule module : modules.values()) {
            if (module.moduleSeedDirect() != null && !allowed.contains(module.descriptor().id())) denied.add(module.descriptor().id());
        }
        var descriptors = modules.values().stream().map(LoadedModule::descriptor).toList();
        var order = ModuleRevocations.order(descriptors, denied);
        List<String> stopped = new ArrayList<>();
        for (String id : order) if (unloadModule(id)) stopped.add(id);
        return stopped;
    }

    /**
     * 为指定云端模块创建 jarBytes + moduleSeed 快照，用于更新失败时回滚。
     * 非云端模块或模块未加载时返回 null。
     */
    public CloudModuleSnapshot createSnapshot(String moduleId) {
        LoadedModule loaded = modules.get(moduleId);
        if (loaded == null || !(loaded.classLoader() instanceof ByteArrayModuleClassLoader)) {
            return null;
        }
        byte[] bytes = loaded.jarBytesDirect();
        byte[] seed = loaded.moduleSeedDirect();
        if (bytes == null) {
            return null;
        }
        return new CloudModuleSnapshot(bytes, seed);
    }

    public java.util.concurrent.CompletionStage<Void> retirement(String moduleId) {
        var retiring = retiringModules.get(moduleId);
        return retiring == null ? java.util.concurrent.CompletableFuture.completedFuture(null) : retiring.copy();
    }

    public Map<String, ModuleCommandHandler> commandHandlerMap() {
        return Collections.unmodifiableMap(commandHandlers);
    }

    public Map<String, Boolean> moduleStatusMap() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (LoadedModule loaded : modules.values()) {
            status.put(loaded.descriptor().name(), loaded.isEnabled());
        }
        return status;
    }

    // ─── 内部方法 ─────────────────────────────────────────────

    private CompletionStage<Boolean> tryLoad(DiscoveredModule dm) {
        if (shuttingDown || modules.containsKey(dm.descriptor.id())) return CompletableFuture.completedFuture(false);
        if ("eventpacket".equals(dm.descriptor.id())) eventPacketAuctionConsumerRequired = true;
        return loadAndEnableResult(dm).whenComplete((ignored, failure) -> {
            if (failure != null) LOGGER.log(Level.SEVERE, "加载模块 " + dm.descriptor.name() + " 失败", failure);
        });
    }

    boolean requiresEventPacketAuctionConsumer() { return eventPacketAuctionConsumerRequired; }

    private CompletionStage<Boolean> loadAndEnableResult(DiscoveredModule dm) {
        try {
            return loadAndEnable(dm);
        } catch (Exception | LinkageError | StackOverflowError failure) {
            cleanupFailedModule(dm.descriptor.id());
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<Boolean> loadAndEnable(DiscoveredModule dm) throws Exception {
        ModuleDescriptor descriptor = dm.descriptor;
        if (dm.moduleSeed() != null && !protectedModuleAuthorization.test(descriptor.id())) {
            LOGGER.warning("受保护模块未取得有效运行授权，拒绝启用: " + descriptor.id());
            return CompletableFuture.completedFuture(false);
        }
        if (retiringModules.containsKey(descriptor.id())) {
            LOGGER.warning("模块旧实例尚未退出，暂不能启用: " + descriptor.id());
            return CompletableFuture.completedFuture(false);
        }
        File jarFile = dm.jarFile;
        byte[] jarBytes = dm.jarBytes;

        // 检查外部插件依赖（必须存在且已启用，避免加载后因依赖未启用而 NoClassDefFoundError）
        for (String externalPlugin : descriptor.externalDepends()) {
            org.bukkit.plugin.Plugin dep = Bukkit.getPluginManager().getPlugin(externalPlugin);
            if (dep == null) {
                LOGGER.warning(descriptor.name() + " 模块需要 " + externalPlugin + " 插件，已跳过加载。");
                return CompletableFuture.completedFuture(false);
            }
            if (!dep.isEnabled()) {
                LOGGER.warning(descriptor.name() + " 模块依赖的外部插件 " + externalPlugin + " 已安装但未启用，已跳过加载。");
                return CompletableFuture.completedFuture(false);
            }
        }

        // 检查 AXS 模块依赖
        for (String depId : descriptor.depends()) {
            if (!isModuleLoaded(depId)) {
                LOGGER.warning(descriptor.name() + " 模块依赖 " + depId + " 未就绪，已跳过加载。");
                return CompletableFuture.completedFuture(false);
            }
        }

        // 检查外部可选插件依赖（缺失仅提示，不跳过加载；模块自行在运行时检测并降级）
        for (String externalPlugin : descriptor.externalSoftDepends()) {
            org.bukkit.plugin.Plugin softDep = Bukkit.getPluginManager().getPlugin(externalPlugin);
            if (softDep == null) {
                LOGGER.info(descriptor.name() + " 模块的可选外部插件 " + externalPlugin + " 未安装，相关功能将不可用。");
            } else if (!softDep.isEnabled()) {
                LOGGER.info(descriptor.name() + " 模块的可选外部插件 " + externalPlugin + " 已安装但未启用，相关功能将不可用。");
            }
        }

        if (jarFile != null && signatureVerifier.isEnabled()) {
            jarBytes = signatureVerifier.verifiedLocalJar(jarFile.toPath());
            if (jarBytes == null) return CompletableFuture.completedFuture(false);
            if (!descriptor.equals(ModuleDescriptorParser.parse(jarBytes))) {
                LOGGER.warning("模块描述在发现与验签之间发生变化: " + jarFile.getName());
                return CompletableFuture.completedFuture(false);
            }
        } else if (jarFile == null && !signatureVerifier.verify(descriptor)) {
            return CompletableFuture.completedFuture(false);
        }

        // 创建 ClassLoader（本地 jar 或内存 bytes）
        ClassLoader classLoader;
        if (jarBytes != null) {
            classLoader = new ByteArrayModuleClassLoader(descriptor.id(), jarBytes, dm.moduleSeed(), plugin.getClass().getClassLoader());
        } else {
            URL jarUrl = jarFile.toURI().toURL();
            classLoader = new ModuleClassLoader(descriptor.id(), jarUrl, plugin.getClass().getClassLoader());
        }

        // 实例化模块主类
        Class<?> mainClass = classLoader.loadClass(descriptor.mainClass());
        if (!AXSModule.class.isAssignableFrom(mainClass)) {
            if (classLoader instanceof java.io.Closeable closeable) {
                closeable.close();
            }
            throw new ModuleLoadException(descriptor.mainClass() + " 未实现 AXSModule 接口");
        }
        AXSModule instance = (AXSModule) mainClass.getDeclaredConstructor().newInstance();

        // 构建上下文
        DefaultModuleContext context = new DefaultModuleContext(
            plugin,
            descriptor.id(),
            packetBridge,
            clientBridge,
            itemStackBridge,
            propBridge,
            worldTextureBridge,
            packetGuard,
            this,
            classLoader,
            keybindService,
            taczCombatBridge,
            crossServerService,
            placeholderResolver,
            storageManager,
            scheduler
        );

        LoadedModule loaded = jarFile != null
            ? new LoadedModule(descriptor, instance, classLoader, jarFile)
            : new LoadedModule(descriptor, instance, classLoader, jarBytes, dm.moduleSeed());
        loaded.setContext(context);
        modules.put(descriptor.id(), loaded);

        // 在 onEnable 之前注册并跑模块的 ConfigSpec 诊断（dry-run，不动玩家 yml）
        try {
            if (core != null) {
                List<xuanmo.arcartxsuite.api.config.ModuleConfigSpec> specs = instance.configSpecs();
                if (specs != null && !specs.isEmpty()) {
                    core.registerModuleConfigSpecs(descriptor.id(), specs, classLoader);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warning("模块 " + descriptor.name() + " 配置诊断异常: " + exception.getMessage());
        } catch (StackOverflowError error) {
            // 配置树循环引用或递归方法无终止条件可能触发 StackOverflowError，
            // 诊断是 dry-run 不影响实际加载，此处吞掉避免阻断模块 onEnable。
            LOGGER.log(Level.WARNING, "模块 " + descriptor.name() + " 配置诊断时发生 StackOverflowError"
                + "（可能由配置树循环引用引起），已跳过诊断。", error);
        }

        var result = new CompletableFuture<Boolean>();
        loaded.setEnabling(result);
        instance.onEnable(context).whenComplete((enabled, failure) ->
            onPrimaryStage(() -> {
                finishEnable(loaded, result, Boolean.TRUE.equals(enabled), failure);
                return CompletableFuture.completedFuture(null);
            }).whenComplete((ignored, dispatchFailure) -> {
                if (dispatchFailure != null) result.completeExceptionally(dispatchFailure);
            }));
        return result;
    }

    private void finishEnable(LoadedModule loaded, CompletableFuture<Boolean> result, boolean enabled, Throwable failure) {
        var descriptor = loaded.descriptor();
        if (modules.get(descriptor.id()) != loaded || loaded.enabling() != result) return;
        loaded.setEnabling(null);
        try {
            if (!enabled || failure != null) {
                cleanupFailedModule(descriptor.id());
                if (failure != null) result.completeExceptionally(failure);
                else result.complete(false);
                return;
            }
            if ("loginview".equals(descriptor.id())) {
                authenticationRuntime().setLoginViewEnabled(true);
            }
            registerModuleEntryPoints(loaded);
            LOGGER.fine(descriptor.name() + " v" + descriptor.version() + " 模块已启用。");
            result.complete(true);
        } catch (Exception | LinkageError | StackOverflowError error) {
            try { cleanupFailedModule(descriptor.id()); }
            finally { result.completeExceptionally(error); }
        }
    }

    private void registerModuleEntryPoints(LoadedModule loaded) {
            var descriptor = loaded.descriptor();
            var instance = loaded.instance();
            loaded.setEnabled(true);
            // 注册声明归属该模块的客户端按键（keybinds.<entry>.module 包含此 id 的条目）
            if (keybindService != null) {
                keybindService.onModuleEnabled(descriptor.id());
            }
            if (instance instanceof ModuleCommandHandler handler) {
                commandHandlers.put(handler.commandId(), handler);
                for (String alias : handler.commandAliases()) {
                    commandHandlers.put(alias.toLowerCase(java.util.Locale.ROOT), handler);
                }
            } else {
                commandHandlers.put(descriptor.id(), new DefaultModuleCommandHandler(descriptor));
            }
    }

    private void disableModule(LoadedModule loaded) {
        abandonReload(loaded);
        try {
            loaded.instance().onDisable();
        } catch (Exception | LinkageError exception) {
            LOGGER.log(Level.SEVERE, loaded.descriptor().name() + " 模块 onDisable 异常", exception);
        } finally {
            releaseLoginViewAuthentication(loaded.descriptor().id());
            clearModuleRegistrations(loaded);
            loaded.setEnabled(false);
            LOGGER.info(loaded.descriptor().name() + " 模块已禁用。");
        }
    }

    private void closeClassLoader(LoadedModule loaded) {
        var services = loaded.instance().shutdownCompletion().toCompletableFuture();
        var context = loaded.context();
        var tasks = context == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
            : context.cancelScheduledTasks().toCompletableFuture();
        var released = new java.util.concurrent.CompletableFuture<Void>();
        String id = loaded.descriptor().id();
        retiringModules.put(id, released);
        java.util.concurrent.CompletableFuture.allOf(services, tasks).whenComplete((ignored, failure) -> {
            Throwable problem = failure;
            try {
                if (failure != null) {
                    LOGGER.log(Level.WARNING, loaded.descriptor().name() + " 后台资源退出异常", failure);
                }
            } catch (RuntimeException | LinkageError loggingFailure) {
                if (problem == null) problem = loggingFailure;
                else problem.addSuppressed(loggingFailure);
            } finally {
                try { closeRetiredClassLoader(loaded); }
                catch (RuntimeException | LinkageError closeFailure) {
                    if (problem == null) problem = closeFailure;
                    else problem.addSuppressed(closeFailure);
                }
                // Only pending retirements gate later requests. Existing waiters still receive
                // this attempt's failure after all services, tasks and the loader have retired.
                retiringModules.remove(id, released);
                if (problem == null) released.complete(null);
                else released.completeExceptionally(problem);
            }
        });
    }

    private void closeRetiredClassLoader(LoadedModule loaded) {
        try {
            if (loaded.classLoader() instanceof java.io.Closeable closeable) {
                closeable.close();
            }
        } catch (IOException exception) {
            LOGGER.warning("关闭模块 ClassLoader 失败: " + loaded.descriptor().id() + " | " + exception.getMessage());
        } finally {
            loaded.clearSensitiveMaterial();
        }
    }

    private void cleanupFailedModule(String moduleId) {
        LoadedModule loaded = modules.remove(moduleId);
        if (loaded == null) {
            return;
        }
        abandonReload(loaded);
        loaded.setEnabled(false);
        try {
            try { releaseLoginViewAuthentication(moduleId); }
            finally { clearModuleRegistrations(loaded); }
        } finally {
            try { loaded.instance().onDisable(); }
            catch (Exception | LinkageError error) { LOGGER.warning("清理失败模块 " + loaded.descriptor().name() + " 时发生异常: " + error.getClass().getSimpleName()); }
            finally { closeClassLoader(loaded); }
        }
    }

    private void releaseLoginViewAuthentication(String moduleId) {
        if("loginview".equals(moduleId) && authenticationRuntime!=null && !shuttingDown && plugin.isEnabled())
            authenticationRuntime.setLoginViewEnabled(false);
    }

    private void clearModuleRegistrations(LoadedModule loaded) {
        String moduleId = loaded.descriptor().id();
        DefaultModuleContext context = loaded.context();
        if (context != null) {
            context.cancelScheduledTasks();
            context.unbindCommands();
            context.unregisterKeybindHandlers();
        }
        removeCommandHandlers(loaded);
        removePacketHandlers(moduleId);
        removeInitializedHandlers(moduleId);
        removeCapabilities(moduleId);
        if (core != null) {
            core.unregisterModuleConfigSpecs(moduleId);
        }
    }

    private void removeCommandHandlers(LoadedModule loaded) {
        AXSModule instance = loaded.instance();
        if (instance instanceof ModuleCommandHandler handler) {
            commandHandlers.entrySet().removeIf(entry -> entry.getValue() == handler);
        } else {
            commandHandlers.remove(loaded.descriptor().id());
        }
    }

    private void clearAllRegistrations() {
        clientPacketRouter.clearAll();
        queuedClientPackets.clear(shuttingDown);
        commandHandlers.clear();
        capabilityRegistry.clearAll();
    }

    private List<String> topologicalSort(Map<String, DiscoveredModule> modules, List<String> cyclicModules) {
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        for (Map.Entry<String, DiscoveredModule> entry : modules.entrySet()) {
            Set<String> deps = new HashSet<>();
            for (String dep : entry.getValue().descriptor.depends()) {
                if (modules.containsKey(dep)) {
                    deps.add(dep);
                }
            }
            for (String dep : entry.getValue().descriptor.softDepends()) {
                if (modules.containsKey(dep)) {
                    deps.add(dep);
                }
            }
            dependencyGraph.put(entry.getKey(), deps);
        }

        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        // 先安装登录和封禁检查，再等其他模块完成数据库准备。
        for (String id : ADMISSION_MODULES) {
            if (dependencyGraph.containsKey(id)) topologicalVisit(id, dependencyGraph, visited, visiting, sorted, cyclicModules);
        }
        for (String id : dependencyGraph.keySet()) {
            if (!visited.contains(id)) {
                topologicalVisit(id, dependencyGraph, visited, visiting, sorted, cyclicModules);
            }
        }
        return sorted;
    }

    private void topologicalVisit(String id, Map<String, Set<String>> graph, Set<String> visited, Set<String> visiting, List<String> sorted, List<String> cyclicModules) {
        if (visiting.contains(id)) {
            LOGGER.warning("检测到模块循环依赖: " + id + "，已强制打断。");
            if (!cyclicModules.contains(id)) {
                cyclicModules.add(id);
            }
            return;
        }
        if (visited.contains(id)) {
            return;
        }
        visiting.add(id);
        Set<String> deps = graph.get(id);
        if (deps != null) {
            for (String dep : deps) {
                topologicalVisit(dep, graph, visited, visiting, sorted, cyclicModules);
            }
        }
        visiting.remove(id);
        visited.add(id);
        sorted.add(id);
    }

    // ─── 全局桥接初始化 ──────────────────────────────────────────

    private void initializeGlobalBridges() {
        globalBridgeFactory.initialize();
    }

    /** 获取全局物品来源注册表 */
    public ItemSourceRegistry itemSourceRegistry() {
        return globalBridgeFactory.itemSourceRegistry();
    }

    public ItemBridgeAPI itemStackBridge() {
        return itemStackBridge;
    }

    /** 获取统一物品奖励发放器 */
    public xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher() {
        return globalBridgeFactory.itemRewardDispatcher();
    }

    /** 获取统一待发放奖励服务 */
    public xuanmo.arcartxsuite.api.item.PendingRewardService pendingRewardService() {
        return globalBridgeFactory.pendingRewardService();
    }

    xuanmo.arcartxsuite.bridge.PendingCommandReceipts pendingCommandReceipts() {
        return pendingCommandReceipts;
    }

    public java.util.concurrent.CompletionStage<Void> stopPendingCommandReceipts() {
        return pendingCommandReceipts.stop();
    }

    /** 获取原版物品中文名称桥接 */
    public DefaultVanillaItemNameBridge vanillaItemNameBridge() {
        return globalBridgeFactory.vanillaItemNameBridge();
    }

    /** 获取内容审核桥接 */
    public ContentModeratorImpl contentModerator() {
        return globalBridgeFactory.contentModerator();
    }

    /** 获取全局物品匹配器 */
    public ItemMatcherAPI itemMatcher() {
        return globalBridgeFactory.itemMatcherSupport();
    }

    /** 获取全局货币管理器 */
    public CurrencyBridgeManager currencyManager() {
        return globalBridgeFactory.currencyBridgeManager();
    }

    /** 获取全局属性桥接注册表 */
    public DefaultAttributeBridgeRegistry attributeBridge() {
        return globalBridgeFactory.attributeBridgeRegistry();
    }

    /** 获取持续药水效果（光环）服务 */
    public xuanmo.arcartxsuite.bridge.DefaultPotionAuraService potionAura() {
        return potionAuraService;
    }

    /** 获取 Mod 实体属性服务 */
    public xuanmo.arcartxsuite.bridge.DefaultModAttributeService modAttributes() {
        return modAttributeService;
    }

    /** 获取 Rondo 经济系统桥接 */
    public xuanmo.arcartxsuite.bridge.DefaultRondoBridge rondoBridge() {
        return globalBridgeFactory.rondoBridge();
    }

    /** 获取 Aria 脚本桥接 */
    public AriaBridge ariaBridge() {
        return globalBridgeFactory.ariaBridge();
    }

    /** 获取宿主单例 WorldTexture 桥接（由 BridgeLifecycleManager 统一管理生命周期） */
    public ArcartXWorldTextureService worldTextureBridge() {
        return worldTextureBridge;
    }

    /** 获取统一条件评估器 */
    public ScriptConditionEvaluator scriptConditionEvaluator() {
        return globalBridgeFactory.scriptConditionEvaluator();
    }

    private YamlConfiguration loadRootConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(configFile);
    }

    private boolean isModuleEnabled(YamlConfiguration config, String moduleId) {
        return config.getBoolean("modules." + moduleId + ".enabled", false);
    }

    private boolean isModuleEnabledEntryPresent(YamlConfiguration config, String moduleId) {
        return config.contains("modules." + moduleId + ".enabled");
    }

    public InputStream openResource(String moduleId, String resourcePath, ClassLoader loader) throws IOException {
        ClassLoader effectiveLoader = loader != null ? loader : getClass().getClassLoader();
        InputStream input = effectiveLoader.getResourceAsStream(resourcePath);
        if (input != null) return input;
        return xuanmo.arcartxsuite.security.MohistCompat.getResourceSafe(resourcePath, effectiveLoader);
    }

    void recordUiRegistration(String moduleId, String uiId) {
        LOGGER.fine("模块 " + moduleId + " 注册/重载 UI: " + uiId);
    }

    // ─── 客户端事件路由 ────────────────────────────────────────

    /**
     * 注册客户端包处理器（由 DefaultModuleContext 调用）。
     */
    void registerClientPacketHandler(String moduleId, PacketHandlerSpec spec) {
        clientPacketRouter.registerPacketHandler(moduleId, spec);
    }

    /**
     * 注册客户端初始化处理器（由 DefaultModuleContext 调用）。
     */
    void registerClientInitializedHandler(String moduleId, ClientInitializedHandler handler) {
        clientPacketRouter.registerInitializedHandler(moduleId, handler);
    }

    /**
     * 移除指定模块注册的所有客户端初始化处理器。
     */
    void removeInitializedHandlers(String moduleId) {
        clientPacketRouter.removeInitializedHandlers(moduleId);
    }

    /**
     * 移除指定模块注册的所有客户端包处理器。
     */
    void removePacketHandlers(String moduleId) {
        clientPacketRouter.removePacketHandlers(moduleId);
    }

    /**
     * 路由客户端自定义包到已注册的处理器。
     * 按优先级遍历，第一个消费的处理器短路返回 true。
     *
     * @return true 表示包已被某个处理器消费
     */
    public boolean routeClientPacket(Player player, String packetId, List<String> data) {
        if (shuttingDown || player == null || packetId == null || data == null) return true;
        var playerId = player.getUniqueId();
        if (packetGuard != null && !packetGuard.allowIngress(playerId, packetId, data)) return true;
        if (Bukkit.isPrimaryThread()) {
            return !player.isOnline() || clientPacketRouter.routeClientPacket(player, packetId, data);
        }
        var routeToken = clientPacketRouter.routeToken(packetId);
        if (routeToken == null) return true;
        var session = ClientPacketRouter.isAuthPacket(packetId) ? null
            : xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(playerId);
        if (session != null && !session.admitted()) return true;
        queuedClientPackets.offer(playerId, () -> {
            List<String> copy = List.copyOf(data);
            return () -> {
                if (!shuttingDown) clientPacketRouter.routeQueuedPacket(player, packetId, copy, session, routeToken);
            };
        });
        return true;
    }

    /**
     * 通知所有已注册的客户端初始化处理器。
     */
    public void routeClientInitialized(Player player) {
        clientPacketRouter.routeClientInitialized(player);
    }

    // ─── Capability 注册表 ────────────────────────────────────

    /**
     * 注册模块提供的能力实例（由 DefaultModuleContext 调用）。
     */
    <T> void registerCapability(String moduleId, Class<T> capabilityType, T implementation) {
        capabilityRegistry.register(moduleId, capabilityType, implementation);
    }

    /** Registers a capability owned by the core before external modules load. */
    public <T> void registerCoreCapability(Class<T> capabilityType, T implementation) {
        capabilityRegistry.registerCore(capabilityType, implementation);
    }

    /**
     * 获取所有支持一键数据迁移的模块 ID。
     */
    public List<String> migratableModuleIds() {
        return capabilityRegistry.migratableModuleIds();
    }

    /**
     * 获取指定模块的迁移 Capability。
     */
    public Optional<xuanmo.arcartxsuite.api.capability.DatabaseMigratable> getMigratable(String moduleId) {
        return capabilityRegistry.getMigratable(moduleId);
    }

    /**
     * 删除指定玩家在所有已注册模块中的数据。
     *
     * @return 模块ID -> 受影响行数
     */
    public Map<String, Integer> purgePlayerData(java.util.UUID playerUuid) {
        return capabilityRegistry.purgePlayerData(playerUuid);
    }

    /**
     * 删除指定玩家在指定模块中的数据。
     *
     * @return 模块ID -> 受影响行数（仅一条）；模块不存在时返回空 map
     */
    public Map<String, Integer> purgePlayerData(java.util.UUID playerUuid, String moduleId) {
        return capabilityRegistry.purgePlayerData(playerUuid, moduleId);
    }

    /**
     * 清空所有已注册模块中的全部玩家数据。
     *
     * @return 模块ID -> 受影响行数
     */
    public Map<String, Integer> purgeAllPlayerData() {
        return capabilityRegistry.purgeAllPlayerData();
    }

    /**
     * 清空指定模块中的全部玩家数据。
     *
     * @return 模块ID -> 受影响行数；模块不存在时返回空 map
     */
    public Map<String, Integer> purgeAllPlayerData(String moduleId) {
        return capabilityRegistry.purgeAllPlayerData(moduleId);
    }

    /**
     * 获取所有已注册 PlayerDataPurgeable 的模块 ID 列表。
     */
    public List<String> purgeableModuleIds() {
        return capabilityRegistry.purgeableModuleIds();
    }

    /**
     * 按类型查找能力实例。
     */
    public <T> T getCapability(Class<T> capabilityType) {
        return capabilityRegistry.get(capabilityType);
    }

    /**
     * 移除指定模块注册的所有能力。
     * 清理 purgeables 列表和 capabilities 表中由该模块注册的实例。
     */
    void removeCapabilities(String moduleId) {
        capabilityRegistry.removeByModule(moduleId);
    }

    private record DiscoveredModule(ModuleDescriptor descriptor, File jarFile, byte[] jarBytes, byte[] moduleSeed) {
        DiscoveredModule(ModuleDescriptor descriptor, File jarFile) {
            this(descriptor, jarFile, null, null);
        }

        DiscoveredModule(ModuleDescriptor descriptor, byte[] jarBytes) {
            this(descriptor, null, jarBytes, null);
        }

        DiscoveredModule(ModuleDescriptor descriptor, byte[] jarBytes, byte[] moduleSeed) {
            this(descriptor, null, jarBytes, moduleSeed);
        }
    }

    public record LoadSummary(
        int discoveredCount,
        int enabledCount,
        int skippedCount,
        int failedCount,
        int uiRegistrationCount,
        List<String> enabledModules,
        List<String> skippedModules,
        List<String> failedModules
    ) {
        public LoadSummary {
            enabledModules = List.copyOf(enabledModules);
            skippedModules = List.copyOf(skippedModules);
            failedModules = List.copyOf(failedModules);
        }
    }
    public record CloudModuleSnapshot(byte[] jarBytes, byte[] moduleSeed) implements AutoCloseable {
        public CloudModuleSnapshot {
            jarBytes = jarBytes == null ? null : jarBytes.clone();
            moduleSeed = moduleSeed == null ? null : moduleSeed.clone();
        }
        @Override
        public void close() {
            wipe(jarBytes);
            wipe(moduleSeed);
        }
        private static void wipe(byte[] data) {
            if (data != null) {
                java.util.Arrays.fill(data, (byte) 0);
            }
        }
    }
}
