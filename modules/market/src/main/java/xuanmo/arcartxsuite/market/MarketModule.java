package xuanmo.arcartxsuite.market;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.command.MarketAdminCommand;
import xuanmo.arcartxsuite.market.command.MarketPlayerCommand;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;
import xuanmo.arcartxsuite.market.listener.MarketEventListener;
import xuanmo.arcartxsuite.market.placeholder.MarketPlaceholderExpansion;
import xuanmo.arcartxsuite.market.storage.JdbcMarketRepository;

/**
 * Market 模块主类，整合商店、拍卖、回收三大子功能。
 * <p>
 * 负责配置加载、UI 资源导出、JDBC 仓储初始化、命令绑定与 PlaceholderAPI 注册。
 * 实现 {@link MailDispatchable} 与 {@link PlayerDataPurgeable} 以支持邮件发货与玩家数据清理。
 */
public final class MarketModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String SHOP_UI_RESOURCE_PATH = "arcartx/ui/market_shop.yml";
    private static final String SHOP_UI_FILE_PATH = "ui/market_shop.yml";
    private static final String AUCTION_UI_RESOURCE_PATH = "arcartx/ui/market_auction.yml";
    private static final String AUCTION_UI_FILE_PATH = "ui/market_auction.yml";
    private static final String AUCTION_SELL_UI_RESOURCE_PATH = "arcartx/ui/market_auction_sell.yml";
    private static final String AUCTION_SELL_UI_FILE_PATH = "ui/market_auction_sell.yml";
    private static final String RECYCLE_UI_RESOURCE_PATH = "arcartx/ui/market_recycle.yml";
    private static final String RECYCLE_UI_FILE_PATH = "ui/market_recycle.yml";
    private static final String HISTORY_UI_RESOURCE_PATH = "arcartx/ui/market_history.yml";
    private static final String HISTORY_UI_FILE_PATH = "ui/market_history.yml";

    private MarketModuleConfiguration configuration;
    private MarketService service;
    private MarketAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("market")
            .name("Market")
            .version("1.1.0")
            .mainClass(getClass().getName())
            .build();
    }

    

    

    

    

    

    

    

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMarket.yml 配置文件缺失");
        }
        configuration = MarketModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile),
            MessageProvider.loadYamlWithBundledDefaults(
                new File(configFile.getParentFile(), configSpec().messagesFileName()),
                configSpec().messagesFileName(),
                moduleClassLoader(),
                logger,
                resourcePath -> openResource(resourcePath, moduleClassLoader())),
            logger);
    }

    @Override
    protected List<String> publishedTopics() {
        return List.of("market.listing_created", "market.auction_purchased");
    }

    @Override
    protected void startService() throws Exception {

        // 构造物品序列化器（智能检测 Overture 物品并使用原生序列化）
        AuctionItemSerializer itemSerializer = new AuctionItemSerializer() {
            private static final String OVERTURE_PREFIX = "OVERTURE:";

            @Override
            public String serialize(ItemStack item) {
                // 检测是否为 Overture 物品，优先使用 Overture 原生 JSON 序列化
                if (itemSourceRegistry != null && itemSourceRegistry.overtureBridgeAvailable()
                    && itemSourceRegistry.isOvertureItem(item)) {
                    String json = itemSourceRegistry.overtureSerialize(item);
                    if (json != null && !json.isBlank()) {
                        return OVERTURE_PREFIX + json;
                    }
                }
                // 非 Overture 物品或 Overture 不可用，降级到 Bukkit 原生序列化
                byte[] bytes = xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(item);
                return java.util.Base64.getEncoder().encodeToString(bytes);
            }

            @Override
            public @Nullable ItemStack deserialize(String data) {
                if (data == null || data.isEmpty()) return null;
                // 检测 Overture 前缀，使用 Overture 原生反序列化
                if (data.startsWith(OVERTURE_PREFIX)) {
                    String json = data.substring(OVERTURE_PREFIX.length());
                    if (itemSourceRegistry != null && itemSourceRegistry.overtureBridgeAvailable()) {
                        ItemStack item = itemSourceRegistry.overtureDeserialize(json);
                        if (item != null) return item;
                        // Overture 反序列化失败，降级到 Bukkit（兼容 Overture 插件被卸载的情况）
                    }
                }
                // Bukkit 原生序列化
                byte[] bytes = java.util.Base64.getDecoder().decode(data);
                return xuanmo.arcartxsuite.api.util.ItemSerializer.deserialize(bytes);
            }
        };

        // MailDispatchable supplier（延迟查找）
        java.util.function.Supplier<MailDispatchable> mailSupplier = () -> getCapability(MailDispatchable.class);

        // 首次启动时自动导出示例商店和回收表
        ensureExampleShopExported();
        ensureDefaultRecycleExported();

        // 统一通过 StorageManager 解析数据源（共享或自建），连接池生命周期由本体管控
        xuanmo.arcartxsuite.api.storage.StorageDescriptor marketDesc;
        javax.sql.DataSource marketDs;
        if (configuration.storage().hasOverride()) {
            // 自建模式：模块配置了独立 storage 节
            marketDesc = configuration.storage().toDescriptor()
                .withTablePrefix(configuration.storage().tablePrefix());
            marketDs = storageManager.resolveModuleDataSource("market", null, dataFolder, marketDesc);
        } else {
            // 共享模式：使用本体统一数据源
            marketDesc = storageManager.getDescriptor()
                .withTablePrefix(configuration.storage().tablePrefix());
            marketDs = storageManager.resolveModuleDataSource("market",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        JdbcMarketRepository marketRepo = new JdbcMarketRepository(marketDs, marketDesc, dataFolder, logger);

        service = new MarketService(plugin, configuration, packetBridge,
            currencyManager, itemSourceRegistry, itemSerializer,
            itemStackBridge, vanillaItemNameBridge, mailSupplier, itemRewardDispatcher, itemMatcher, logger, crossServer, messages());
        service.setSignalProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.SignalDispatchable.class));
        service.setEventBusProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.start(dataFolder, marketRepo);

        // 注册待发放队列消费者（玩家上线补发离线期间累积的物品/货币）
        registerListener(new PendingDeliveryService(
            plugin, service.getRepository(), currencyManager, itemSerializer, itemRewardDispatcher, logger));

        // 注册 UI 到 ArcartX 桥接层
        bindMarketUi(configuration.ui().shopId(), SHOP_UI_FILE_PATH);
        bindMarketUi(configuration.ui().auctionId(), AUCTION_UI_FILE_PATH);
        bindMarketUi("AXS:market_auction_sell", AUCTION_SELL_UI_FILE_PATH);
        bindMarketUi(configuration.ui().recycleId(), RECYCLE_UI_FILE_PATH);
        bindMarketUi(configuration.ui().historyId(), HISTORY_UI_FILE_PATH);

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "market"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return marketRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return marketRepo.getDescriptor();
                }
            });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "market"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return marketRepo.deletePlayerData(playerUuid); }
                catch (Exception e) { logger.warning("Market purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return marketRepo.deleteAllPlayerData(); }
                catch (Exception e) { logger.warning("Market purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        adminCommand = new MarketAdminCommand(() -> service, messages());

        // 注册事件监听器（自动回收 + 玩家退出清理 token）
        registerListener(new MarketEventListener(
            () -> service != null ? service.getRecycleService() : null,
            configuration.recycle().enabled() && configuration.recycle().allowAutoRecycle(),
            () -> service
        ));

        logger.fine("Market 模块已载入 | 存储=" + configuration.storage().mode().toUpperCase()
            + " | 列表缓存=" + (configuration.redis().enabled() ? "启用" : "禁用")
            + " | 跨服=" + (service != null && service.crossServerActive() ? "ON" : "OFF"));
    }

    private void bindMarketUi(String configuredId, String relativeUiPath) {
        if (!configuration.ui().registerOnEnable()) return;
        registerModuleUi(relativeUiPath, configuredId, true);
    }

    private void ensureExampleShopExported() {
        File shopDir = new File(dataFolder, configuration.shop().shopsDirectory());
        if (!shopDir.exists()) {
            shopDir.mkdirs();
        }
        File[] files = shopDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            File target = new File(shopDir, "example_shop.yml");
            exportResource("shops/example_shop.yml", target, false);
            logger.info("已导出示例商店到 " + target.getPath());
        }
    }

    private void ensureDefaultRecycleExported() {
        File recycleDir = new File(dataFolder, configuration.recycle().recycleDirectory());
        if (!recycleDir.exists()) {
            recycleDir.mkdirs();
        }
        File[] files = recycleDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            File target = new File(recycleDir, "default_recycle.yml");
            exportResource("recycle/default_recycle.yml", target, false);
            logger.info("已导出默认回收表到 " + target.getPath());
        }
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        storageManager.closeModuleDataSource("market");
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        MarketPlayerCommand cmd = new MarketPlayerCommand(() -> service, messages());
        return Map.of("market", (TabExecutor) cmd);
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new MarketPlaceholderExpansion(plugin, () -> service);
    }

    

    // ClientPacketHandler.data 是 List<String>，MarketService 内部解析为 Map

    // ─── ModuleCommandHandler（/axs market）───────────────────

    @Override public String commandId() { return "market"; }

    @Override public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}


