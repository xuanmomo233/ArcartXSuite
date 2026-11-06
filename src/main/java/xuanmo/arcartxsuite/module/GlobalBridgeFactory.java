package xuanmo.arcartxsuite.module;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.currency.CurrencyDefinition;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.bridge.DefaultAriaBridge;
import xuanmo.arcartxsuite.bridge.DefaultAttributeBridgeRegistry;
import xuanmo.arcartxsuite.bridge.DefaultItemRewardDispatcher;
import xuanmo.arcartxsuite.bridge.DefaultItemSourceRegistry;
import xuanmo.arcartxsuite.bridge.DefaultPendingRewardService;
import xuanmo.arcartxsuite.bridge.DefaultPotionAuraService;
import xuanmo.arcartxsuite.bridge.DefaultRondoBridge;
import xuanmo.arcartxsuite.bridge.DefaultVanillaItemNameBridge;
import xuanmo.arcartxsuite.bridge.ContentModeratorImpl;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeManager;
import xuanmo.arcartxsuite.api.item.ItemMatcherSupport;
import xuanmo.arcartxsuite.condition.DefaultScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;

/**
 * 全局桥接工厂：管理所有宿主级桥接单例的创建、初始化和查询。
 * <p>
 * 从 {@link ModuleRegistry} 拆分而来，职责单一（SRP）。
 * 桥接对象在首次调用 {@link #initialize()} 时创建，后续 reload 复用单例。
 */
final class GlobalBridgeFactory {

    private static final java.util.logging.Logger LOGGER = new PluginConsoleLogger("AXS-GlobalBridgeFactory", null);

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final PlaceholderResolverAPI placeholderResolver;
    private final StorageManager storageManager;
    private final ModuleRegistry registry;
    private final CapabilityRegistry capabilityRegistry;

    // ─── 全局桥接单例 ──────────────────────────────────────────
    private DefaultItemSourceRegistry itemSourceRegistry;
    private DefaultItemRewardDispatcher itemRewardDispatcher;
    private DefaultPendingRewardService pendingRewardService;
    private ItemMatcherSupport itemMatcherSupport;
    private CurrencyBridgeManager currencyBridgeManager;
    private DefaultAttributeBridgeRegistry attributeBridgeRegistry;
    private DefaultRondoBridge rondoBridge;
    private DefaultAriaBridge ariaBridge;
    private ScriptConditionEvaluator scriptConditionEvaluator;
    private DefaultVanillaItemNameBridge vanillaItemNameBridge;
    private ContentModeratorImpl contentModerator;

    GlobalBridgeFactory(
        org.bukkit.plugin.java.JavaPlugin plugin,
        PlaceholderResolverAPI placeholderResolver,
        StorageManager storageManager,
        ModuleRegistry registry,
        CapabilityRegistry capabilityRegistry
    ) {
        this.plugin = plugin;
        this.placeholderResolver = placeholderResolver;
        this.storageManager = storageManager;
        this.registry = registry;
        this.capabilityRegistry = capabilityRegistry;
    }

    /**
     * 初始化（或重新初始化）所有全局桥接。
     * <p>
     * 首次调用创建单例，后续调用复用已有实例仅重新加载配置。
     */
    void initialize() {
        initItemSources();
        initCurrencyAndAttributes();
        initScripting();
        initEventBus();
        capabilityRegistry.registerCore(xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability.class, registry.authenticationRuntime());
        capabilityRegistry.registerCore(xuanmo.arcartxsuite.api.capability.BindingService.class, registry.authenticationRuntime());
        EventBusCapability authBus = capabilityRegistry.get(EventBusCapability.class);
        for (String topic : java.util.List.of("axs.auth.admission_granted", "axs.auth.profile_first_admitted", "axs.auth.presence_day_started", "axs.auth.binding_changed")) {
            authBus.registerPublisher(topic);
        }
        initContentModerator();
    }

    private void initItemSources() {
        if (itemSourceRegistry == null) {
            itemSourceRegistry = new DefaultItemSourceRegistry(plugin);
        }
        itemSourceRegistry.initialize();

        if (itemRewardDispatcher == null) {
            itemRewardDispatcher = new DefaultItemRewardDispatcher(plugin, LOGGER, registry);
        }

        if (pendingRewardService == null) {
            pendingRewardService = new DefaultPendingRewardService(plugin, LOGGER, storageManager, registry);
            Bukkit.getPluginManager().registerEvents(pendingRewardService, plugin);
        }
        pendingRewardService.initialize();
        capabilityRegistry.registerCore(PendingRewardService.class, pendingRewardService);

        if (vanillaItemNameBridge == null) {
            vanillaItemNameBridge = new DefaultVanillaItemNameBridge();
        }

        itemMatcherSupport = new ItemMatcherSupport(
            itemSourceRegistry::mythicItemId,
            itemSourceRegistry::neigeItemId,
            itemSourceRegistry::overtureItemId
        );
    }

    private void initContentModerator() {
        if (contentModerator == null) {
            contentModerator = new ContentModeratorImpl(plugin);
        }
        contentModerator.initialize();
    }

    private void initCurrencyAndAttributes() {
        if (currencyBridgeManager == null) {
            currencyBridgeManager = new CurrencyBridgeManager(plugin, Map.of(), placeholderResolver);
        }
        YamlConfiguration rootConfig = loadRootConfig();
        currencyBridgeManager.registerCurrencies(parseGlobalCurrencies(rootConfig));
        currencyBridgeManager.initialize();

        if (attributeBridgeRegistry == null) {
            attributeBridgeRegistry = new DefaultAttributeBridgeRegistry(plugin);
        }
        ConfigurationSection attrBridges = rootConfig.getConfigurationSection("attribute-bridges");
        boolean apEnabled = attrBridges == null || attrBridges.getBoolean("attributeplus", true);
        boolean caEnabled = attrBridges == null || attrBridges.getBoolean("craneattribute", true);
        boolean mlEnabled = attrBridges == null || attrBridges.getBoolean("mythiclib", true);
        boolean symEnabled = attrBridges == null || attrBridges.getBoolean("symphony", true);
        attributeBridgeRegistry.setSourceEnabled(apEnabled, caEnabled, mlEnabled, symEnabled);
        attributeBridgeRegistry.initialize();

        // 持续药水效果（光环）服务：实例由 ModuleRegistry 持有，此处仅按最新配置刷新参数与任务
        ConfigurationSection auraSection = rootConfig.getConfigurationSection("potion-aura");
        boolean auraEnabled = auraSection == null || auraSection.getBoolean("enabled", true);
        int auraInterval = auraSection == null ? 60 : auraSection.getInt("refresh-interval-ticks", 60);
        int auraDuration = auraSection == null ? 200 : auraSection.getInt("effect-duration-ticks", 200);
        boolean auraParticles = auraSection != null && auraSection.getBoolean("particles", false);
        boolean auraIcon = auraSection == null || auraSection.getBoolean("icon", true);
        DefaultPotionAuraService potionAuraService = registry.potionAura();
        potionAuraService.configure(auraEnabled, auraInterval, auraDuration, auraParticles, auraIcon);
        potionAuraService.initialize();

        // Mod 实体属性服务：实例由 ModuleRegistry 持有，此处仅按最新配置刷新开关
        ConfigurationSection modAttrSection = rootConfig.getConfigurationSection("mod-attributes");
        boolean modAttrEnabled = modAttrSection == null || modAttrSection.getBoolean("enabled", true);
        registry.modAttributes().configure(modAttrEnabled);
        registry.modAttributes().initialize();

        if (rondoBridge == null) {
            rondoBridge = new DefaultRondoBridge();
        }
        rondoBridge.initialize();
    }

    private void initScripting() {
        if (ariaBridge == null) {
            ariaBridge = new DefaultAriaBridge(plugin);
        }
        ariaBridge.initialize();
        scriptConditionEvaluator = new DefaultScriptConditionEvaluator(ariaBridge, placeholderResolver);
        ScriptConditionServices.install(scriptConditionEvaluator);
    }

    private void initEventBus() {
        EventBusCapability eventBus;
        var caps = capabilityRegistry.capabilitiesMap();
        if (!caps.containsKey(EventBusCapability.class)) {
            eventBus = new SimpleEventBus(xuanmo.arcartxsuite.module.AxsLog.logger());
            caps.put(EventBusCapability.class, eventBus);
        } else {
            eventBus = (EventBusCapability) caps.get(EventBusCapability.class);
        }

        eventBus.registerPublisher("axs.currency.spent");
        eventBus.registerPublisher("axs.currency.earned");

        final EventBusCapability finalEventBus = eventBus;
        currencyBridgeManager.setEventBusProvider(() -> finalEventBus);

        // 注册模块管理能力
        caps.put(xuanmo.arcartxsuite.api.capability.ModuleAdminCapability.class,
            new xuanmo.arcartxsuite.api.capability.ModuleAdminCapability() {
                @Override
                public java.util.Optional<xuanmo.arcartxsuite.api.AXSModule> findModule(@org.jetbrains.annotations.NotNull String moduleId) {
                    LoadedModule loaded = registry.getLoadedModule(moduleId);
                    if (loaded != null && loaded.isEnabled()) {
                        return java.util.Optional.of(loaded.instance());
                    }
                    return java.util.Optional.empty();
                }

                @Override
                public @org.jetbrains.annotations.NotNull java.util.List<xuanmo.arcartxsuite.api.ModuleDescriptor> listModules() {
                    return registry.listModules();
                }

                @Override
                public void reloadModule(@org.jetbrains.annotations.NotNull String moduleId) throws Exception {
                    registry.reloadModule(moduleId);
                }
            });
    }

    private YamlConfiguration loadRootConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(configFile);
    }

    private static Map<String, CurrencyDefinition> parseGlobalCurrencies(YamlConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("currencies");
        LinkedHashMap<String, CurrencyDefinition> result = new LinkedHashMap<>();
        if (section == null) {
            result.put("money", new CurrencyDefinition("money", "vault", "金币", 2, "", "", "", "DOWN"));
            result.put("points", new CurrencyDefinition("points", "playerpoints", "点券", 0, "", "", "", "DOWN"));
            return result;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) {
                continue;
            }
            String id = rawId.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            result.put(id, new CurrencyDefinition(
                id,
                child.getString("provider", "vault").trim().toLowerCase(java.util.Locale.ROOT),
                child.getString("display-name", rawId),
                child.contains("scale") ? child.getInt("scale", 2) : child.getInt("precision", 2),
                child.getString("balance-placeholder", ""),
                child.getString("withdraw-command", ""),
                child.getString("deposit-command", ""),
                child.getString("rounding", "DOWN")
            ));
        }
        result.putIfAbsent("money", new CurrencyDefinition("money", "vault", "金币", 2, "", "", "", "DOWN"));
        result.putIfAbsent("points", new CurrencyDefinition("points", "playerpoints", "点券", 0, "", "", "", "DOWN"));
        return result;
    }

    // ─── 桥接 getter（供 ModuleRegistry 委托） ─────────────────
    void detachGlobalListeners() {
        if(pendingRewardService!=null) org.bukkit.event.HandlerList.unregisterAll(pendingRewardService);
    }

    DefaultItemSourceRegistry itemSourceRegistry() { return itemSourceRegistry; }
    DefaultItemRewardDispatcher itemRewardDispatcher() { return itemRewardDispatcher; }
    DefaultPendingRewardService pendingRewardService() { return pendingRewardService; }
    ItemMatcherSupport itemMatcherSupport() { return itemMatcherSupport; }
    CurrencyBridgeManager currencyBridgeManager() { return currencyBridgeManager; }
    DefaultAttributeBridgeRegistry attributeBridgeRegistry() { return attributeBridgeRegistry; }
    DefaultRondoBridge rondoBridge() { return rondoBridge; }
    DefaultAriaBridge ariaBridge() {
        if (ariaBridge == null) {
            ariaBridge = new DefaultAriaBridge(plugin);
            ariaBridge.initialize();
        }
        return ariaBridge;
    }
    ScriptConditionEvaluator scriptConditionEvaluator() {
        if (scriptConditionEvaluator == null) {
            scriptConditionEvaluator = new DefaultScriptConditionEvaluator(ariaBridge(), placeholderResolver);
            ScriptConditionServices.install(scriptConditionEvaluator);
        }
        return scriptConditionEvaluator;
    }
    DefaultVanillaItemNameBridge vanillaItemNameBridge() { return vanillaItemNameBridge; }
    ContentModeratorImpl contentModerator() {
        if (contentModerator == null) {
            initContentModerator();
        }
        return contentModerator;
    }
}
