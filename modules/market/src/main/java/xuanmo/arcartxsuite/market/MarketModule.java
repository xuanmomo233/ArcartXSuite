package xuanmo.arcartxsuite.market;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.api.event.ModuleLifecycleEvent;
import xuanmo.arcartxsuite.market.exchange.NpcTradeRepository;
import xuanmo.arcartxsuite.market.exchange.NpcTradingService;
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
import org.bukkit.Bukkit;

/**
 * Market 模块主类，整合商店、拍卖、回收三大子功能。
 * <p>
 * 负责配置加载、UI 资源导出、JDBC 仓储初始化、命令绑定与 PlaceholderAPI 注册。
 * 实现 {@link MailDispatchable} 与 {@link PlayerDataPurgeable} 以支持邮件发货与玩家数据清理。
 */
public final class MarketModule extends AbstractAXSModule implements ModuleCommandHandler, Listener {

    private static final String SHOP_UI_RESOURCE_PATH = "arcartx/ui/market_shop.yml";
    private static final String SHOP_UI_FILE_PATH = "ui/market_shop.yml";
    private static final String AUCTION_ROOM_RESOURCE_PATH = "arcartx/ui/market_auction_room.yml";
    private static final String AUCTION_ROOM_FILE_PATH = "ui/market_auction_room.yml";
    private static final String AUCTION_UI_RESOURCE_PATH = "arcartx/ui/market_auction.yml";
    private static final String AUCTION_UI_FILE_PATH = "ui/market_auction.yml";
    private static final String AUCTION_SELL_UI_RESOURCE_PATH = "arcartx/ui/market_sell.yml";
    private static final String AUCTION_SELL_UI_FILE_PATH = "ui/market_sell.yml";
    private static final String PLAYER_SHOP_UI_RESOURCE_PATH = "arcartx/ui/market_player_shop.yml";
    private static final String PLAYER_SHOP_UI_FILE_PATH = "ui/market_player_shop.yml";
    private static final String RECYCLE_UI_RESOURCE_PATH = "arcartx/ui/market_recycle.yml";
    private static final String RECYCLE_UI_FILE_PATH = "ui/market_recycle.yml";
    private static final String HISTORY_UI_RESOURCE_PATH = "arcartx/ui/market_history.yml";
    private static final String HISTORY_UI_FILE_PATH = "ui/market_history.yml";
    private static final String SHOP_EDIT_UI_RESOURCE_PATH = "arcartx/ui/market_shop_edit.yml";
    private static final String SHOP_EDIT_UI_FILE_PATH = "ui/market_shop_edit.yml";

    private MarketModuleConfiguration configuration;
    private MarketService service;
    private MarketAdminCommand adminCommand;
    private NpcTradingService exchange;
    private boolean exchangeEnabled;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("market")
            .name("Market")
            .version("2.0.0")
            .mainClass(getClass().getName())
            .softDepends(List.of("warehouse"))
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXMarket.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("auction.categories")
                    .dynamicSection("auction.blacklist")
                    .dynamicSection("auction.tax-discount")
                    .dynamicSection("player-shop.blacklist")
                    .dynamicSection("player-shop.tax-discount")
                    .dynamicSection("recycle.price-multiplier")
                    .dynamicSection("shop.dynamic.strategies")
                    .dynamicSection("messages")
                .build())
            .currentVersion(4)
            .validations(List.of(
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    ValidationRule.of("storage.pool-size", ValueType.INT)
                        .withRange(1, 100),
                    ValidationRule.of("auction.max-listings-per-player", ValueType.INT)
                        .withRange(1, 1000),
                    ValidationRule.of("auction.min-bid-increment-ratio", ValueType.DOUBLE)
                        .withRange(0.01, 1.0),
                    ValidationRule.of("auction.transaction-tax-rate", ValueType.DOUBLE)
                        .withRange(0.0, 0.99),
                    ValidationRule.of("auction.min-duration-seconds", ValueType.INT)
                        .withRange(60, null),
                    ValidationRule.of("auction.max-duration-seconds", ValueType.INT)
                        .withRange(3600, null),
                    ValidationRule.of("auction.allow-cancel-with-bids", ValueType.BOOLEAN),
                    ValidationRule.of("auction.end-protection.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("auction.end-protection.window-seconds", ValueType.INT).withRange(1,3600),
                    ValidationRule.of("auction.end-protection.extend-seconds", ValueType.INT).withRange(1,3600),
                    ValidationRule.of("auction.end-protection.max-extension-seconds", ValueType.INT).withRange(1,86400),
                    ValidationRule.of("auction.notifications.outbid", ValueType.BOOLEAN),
                    ValidationRule.of("auction.notifications.settlement", ValueType.BOOLEAN),
                    ValidationRule.of("auction.notifications.watch-bids", ValueType.BOOLEAN),
                    ValidationRule.of("player-shop.max-listings-per-player", ValueType.INT)
                        .withRange(1, 1000),
                    ValidationRule.of("player-shop.transaction-tax-rate", ValueType.DOUBLE)
                        .withRange(0.0, 0.99),
                    ValidationRule.of("player-shop.profile.name-max-length",ValueType.INT).withRange(1,64),
                    ValidationRule.of("player-shop.profile.description-max-length",ValueType.INT).withRange(1,1024),
                    ValidationRule.of("player-shop.profile.announcement-max-length",ValueType.INT).withRange(1,2048),
                    ValidationRule.of("player-shop.profile.max-favorites",ValueType.INT).withRange(1,1000),
                    ValidationRule.of("recycle.protection.default-auto-recycle",ValueType.BOOLEAN),
                    ValidationRule.of("recycle.protection.max-rules",ValueType.INT).withRange(1,1000),
                    ValidationRule.of("redis.cache-ttl-seconds", ValueType.INT)
                        .withRange(10, 86400)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(SHOP_UI_RESOURCE_PATH, SHOP_UI_FILE_PATH);
        mappings.put(AUCTION_UI_RESOURCE_PATH, AUCTION_UI_FILE_PATH);
        mappings.put(configuration!=null?configuration.ui().auctionRoomFile():AUCTION_ROOM_RESOURCE_PATH, AUCTION_ROOM_FILE_PATH);
        mappings.put(AUCTION_SELL_UI_RESOURCE_PATH, AUCTION_SELL_UI_FILE_PATH);
        mappings.put(PLAYER_SHOP_UI_RESOURCE_PATH, PLAYER_SHOP_UI_FILE_PATH);
        mappings.put("arcartx/ui/market_player_shops.yml","ui/market_player_shops.yml");
        mappings.put("arcartx/ui/market_player_store.yml","ui/market_player_store.yml");
        mappings.put("arcartx/ui/market_player_store_edit.yml","ui/market_player_store_edit.yml");
        mappings.put("arcartx/ui/market_recycle_protection.yml","ui/market_recycle_protection.yml");
        mappings.put("arcartx/ui/market_admin_trades.yml","ui/market_admin_trades.yml");
        mappings.put(RECYCLE_UI_RESOURCE_PATH, RECYCLE_UI_FILE_PATH);
        mappings.put(HISTORY_UI_RESOURCE_PATH, HISTORY_UI_FILE_PATH);
        mappings.put(SHOP_EDIT_UI_RESOURCE_PATH, SHOP_EDIT_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((player, packetId, data) -> {
            NpcTradingService exchangeService = exchange;
            if (exchangeService != null && exchangeService.ownsPacket(packetId)) {
                if (Bukkit.isPrimaryThread()) exchangeService.handle(player, packetId, data);
                else Bukkit.getScheduler().runTask(plugin, () -> exchangeService.handle(player, packetId, data));
                return true;
            }
            MarketService current = service;
            if (current == null || !current.ownsPacket(packetId)) {
                return false;
            }
            // 市场交易涉及背包与经济操作，统一切到主线程串行执行，
            // 消除并发竞态（重复购买/限购绕过）与异步线程操作 Bukkit API 的风险。
            if (org.bukkit.Bukkit.isPrimaryThread()) {
                current.handleClientPacket(player, packetId, data);
            } else {
                Bukkit.getScheduler().runTask(plugin,
                    () -> current.handleClientPacket(player, packetId, data));
            }
            return true;
        }, 0, null, null);
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMarket.yml 配置文件缺失");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        exchangeEnabled = yaml.getBoolean("exchange.enabled", false);
        configuration = MarketModuleConfiguration.load(
            yaml,
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
        return List.of("axs.market.listing_created", "axs.market.auction_purchased", "axs.market.shop_dynamic_triggered");
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
            itemStackBridge, vanillaItemNameBridge, mailSupplier, itemRewardDispatcher, itemMatcher, logger, crossServer, scheduler, messages(), contentModerator, scriptConditionEvaluator);
        service.setSignalProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.SignalDispatchable.class));
        service.setEventBusProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.start(dataFolder, marketRepo);

        // 注册待发放队列消费者（玩家上线补发离线期间累积的物品/货币）
        PendingDeliveryService deliveries = new PendingDeliveryService(
            plugin, service.getRepository(), currencyManager, itemSerializer, itemRewardDispatcher, logger, service.getLedger());
        registerListener(deliveries);
        service.attachDeliveries(deliveries);

        // 注册 UI 到 ArcartX 桥接层
        bindMarketUi(configuration.ui().shopId(), SHOP_UI_FILE_PATH);
        bindMarketUi(configuration.ui().auctionId(), AUCTION_UI_FILE_PATH);
        bindMarketUi(configuration.ui().auctionRoomId(), AUCTION_ROOM_FILE_PATH);
        bindMarketUi("AXS:market_auction_sell", AUCTION_SELL_UI_FILE_PATH);
        bindMarketUi(configuration.ui().sellId(), AUCTION_SELL_UI_FILE_PATH);
        bindMarketUi(configuration.ui().playerShopId(), PLAYER_SHOP_UI_FILE_PATH);
        bindMarketUi(configuration.ui().playerShopsId(),"ui/market_player_shops.yml");
        bindMarketUi(configuration.ui().playerStoreId(),"ui/market_player_store.yml");
        bindMarketUi(configuration.ui().playerStoreEditId(),"ui/market_player_store_edit.yml");
        bindMarketUi(configuration.ui().recycleProtectionId(),"ui/market_recycle_protection.yml");
        bindMarketUi(configuration.ui().adminTradesId(),"ui/market_admin_trades.yml");
        bindMarketUi(configuration.ui().recycleId(), RECYCLE_UI_FILE_PATH);
        bindMarketUi(configuration.ui().historyId(), HISTORY_UI_FILE_PATH);
        bindMarketUi(configuration.ui().shopEditId(), SHOP_EDIT_UI_FILE_PATH);

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

        if (exchangeEnabled) startExchange(marketDs, marketDesc, marketRepo);
        else marketRepo.includeExchangeOperationsIfPresent(marketDesc.tablePrefix() + NpcTradeRepository.OPERATIONS_TABLE_SUFFIX);
        registerCapability(xuanmo.arcartxsuite.api.capability.AxsMarketService.class,
            new xuanmo.arcartxsuite.api.capability.AxsMarketService() {
                @Override public boolean openSystemShop(Player player, String id) {
                    return service != null && service.openSystemShop(player, id);
                }

                @Override public void openUi(@NotNull org.bukkit.entity.Player player,
                        @NotNull MarketView view) {
                    if (service == null) return;
                    switch (view) {
                        case AUCTION -> service.openAuctionUi(player);
                        case AUCTION_SELL -> service.openAuctionSellUi(player);
                        case SHOP_LIST -> service.openShopListUi(player);
                        case SHOP -> service.openShopUi(player);
                        case RECYCLE -> service.openRecycleUi(player);
                        case HISTORY -> service.openHistoryUi(player);
                        case SELL -> service.openSellUi(player);
                        case PLAYER_SHOP -> service.openPlayerShopUi(player);
                        case MY_LISTINGS -> service.openMyListingsUi(player);
                    }
                }
                @Override public @NotNull java.util.concurrent.CompletableFuture<Boolean>
                        createAuctionListing(@NotNull org.bukkit.entity.Player player,
                        @NotNull ItemStack item, double startingBid, @Nullable String currency,
                        long durationSeconds) {
                    return java.util.concurrent.CompletableFuture.completedFuture(
                        service != null && service.createListing(player, item, startingBid, currency, durationSeconds));
                }
                @Override public @NotNull java.util.concurrent.CompletableFuture<Boolean>
                        cancelListing(@NotNull org.bukkit.entity.Player player, long listingId) {
                    return java.util.concurrent.CompletableFuture.completedFuture(
                        service != null && service.cancelListing(player, listingId));
                }
                @Override public @NotNull java.util.concurrent.CompletableFuture<Boolean>
                        searchAuction(@NotNull org.bukkit.entity.Player player, @Nullable String keyword) {
                    if (service == null) {
                        return java.util.concurrent.CompletableFuture.completedFuture(false);
                    }
                    service.searchAuction(player, keyword);
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                }
                @Override public int auctionCount() {
                    return service != null ? service.getAuctionCount() : 0;
                }
                @Override public boolean openAuctionLot(@NotNull org.bukkit.entity.Player player,long listingId) {
                    return service != null && service.openAuctionRoom(player,listingId);
                }
                @Override public boolean openPlayerStore(@NotNull org.bukkit.entity.Player player,@NotNull java.util.UUID owner) {
                    return service!=null && service.openPlayerStore(player,owner);
                }
                @Override public boolean openOwnStoreEditor(@NotNull org.bukkit.entity.Player player) {
                    if(service==null || !service.isPlayerShopEnabled())return false;
                    service.openPlayerStoreEditor(player);return true;
                }
                @Override public int shopCount() {
                    return service != null ? service.getShopCount() : 0;
                }
                @Override public boolean openRecycleProtection(@NotNull org.bukkit.entity.Player player){
                    if(service==null || !configuration.recycle().enabled())return false;service.openRecycleProtection(player);return true;
                }
                @Override public boolean openTradeManagement(@NotNull org.bukkit.entity.Player player){
                    if(service==null || !player.hasPermission("arcartxsuite.market.admin"))return false;
                    service.openTradeManagement(player);return true;
                }
                @Override public int myListingCount(@NotNull org.bukkit.entity.Player player) {
                    return service != null ? service.getMyListingsCount(player) : 0;
                }
                @Override public boolean playerShopEnabled() {
                    return service != null && service.isPlayerShopEnabled();
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

    private void startExchange(javax.sql.DataSource source,
                               xuanmo.arcartxsuite.api.storage.StorageDescriptor marketDescriptor,
                               JdbcMarketRepository marketRepository) throws Exception {
        var buyer = registerModuleUi("arcartx/ui/market_exchange.yml", "ui/market_exchange.yml", "AXS:market_exchange", true);
        var editor = registerModuleUi("arcartx/ui/market_exchange_editor.yml", "ui/market_exchange_editor.yml", "AXS:market_exchange_editor", true);
        var repository = new NpcTradeRepository(source, marketDescriptor, dataFolder, logger);
        repository.initialize();
        marketRepository.includeExchangeOperations(repository.operationsTable());
        PacketBridgeAPI notices = xuanmo.arcartxsuite.api.message.UiNoticeBridge.wrap(packetBridge, messages(), dataFolder);
        exchange = new NpcTradingService(plugin, dataFolder, notices, currencyManager, itemSourceRegistry,
            itemStackBridge, vanillaItemNameBridge, createAdyeshachNpcBridge(),
            () -> getCapability(WarehouseMaterialAccess.class), repository, buyer.runtimeUiId(), editor.runtimeUiId(), logger);
    }

    @Override
    protected @NotNull List<Listener> createListeners() {
        return exchange == null ? List.of() : List.of(this, exchange);
    }

    @EventHandler
    public void onWarehouseLifecycle(ModuleLifecycleEvent event) {
        if (exchange != null && "warehouse".equals(event.moduleId())
            && event.phase() == ModuleLifecycleEvent.Phase.DISABLING) {
            exchange.warehouseChanged();
        }
    }

    @Override
    protected void stopService() {
        if (exchange != null) {
            exchange.close();
            exchange = null;
        }
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

    @Override
    public boolean allowsWithoutSuiteAdmin(@NotNull CommandSender sender, @NotNull String[] args) {
        return args.length > 1 && "exchange".equalsIgnoreCase(args[1])
            && ((args.length > 2 && "open".equalsIgnoreCase(args[2]))
                || sender.hasPermission(NpcTradingService.ADMIN));
    }

    @Override public List<String> actions() {
        List<String> actions = new ArrayList<>(adminCommand != null ? adminCommand.actions() : List.of("help", "status"));
        actions.add("exchange");
        return List.copyOf(actions);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length > 1 && "exchange".equalsIgnoreCase(args[1])) {
            if (exchange == null) sender.sendMessage("NPC 兑换未启用");
            else try {
                return exchange.command(sender, Arrays.copyOfRange(args, 1, args.length));
            } catch (IllegalArgumentException exception) {
                sender.sendMessage("§c" + exception.getMessage());
            }
            return true;
        }
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return actions().stream().filter(action -> action.startsWith(args[1])).toList();
        if (args.length >= 3 && "exchange".equalsIgnoreCase(args[1])) {
            if (args.length == 3) {
                List<String> actions = sender.hasPermission(NpcTradingService.ADMIN)
                    ? List.of("open", "edit", "ledger", "evidence", "confirm") : List.of("open");
                return actions.stream().filter(action -> action.startsWith(args[2])).toList();
            }
            if (exchange != null && args.length == 4 && List.of("open", "edit").contains(args[2])) {
                return exchange.shopIds().stream().filter(id -> id.startsWith(args[3])).toList();
            }
        }
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}

