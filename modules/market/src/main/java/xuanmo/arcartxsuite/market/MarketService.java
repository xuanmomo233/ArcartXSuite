package xuanmo.arcartxsuite.market;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.auction.AuctionService;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;
import xuanmo.arcartxsuite.market.dynamic.DynamicShopService;
import xuanmo.arcartxsuite.market.playershop.PlayerShopListing;
import xuanmo.arcartxsuite.market.playershop.PlayerShopService;
import xuanmo.arcartxsuite.market.recycle.RecycleEntry;
import xuanmo.arcartxsuite.market.recycle.RecycleService;
import xuanmo.arcartxsuite.market.shop.ShopService;
import xuanmo.arcartxsuite.market.storage.AuctionHistory;
import xuanmo.arcartxsuite.market.storage.JdbcMarketRepository;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;

/**
 * 市场模块顶层门面服务，协调拍卖行/系统商店/回收商店三个子服务。
 */
public class MarketService {

    private final JavaPlugin plugin;
    private final MarketModuleConfiguration config;
    private final PacketBridgeAPI packetBridge;
    private final CurrencyBridgeAPI currencyManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final IconResolver iconResolver;
    private final AuctionItemSerializer itemSerializer;
    private final @Nullable ItemBridgeAPI itemStackBridge;
    private final xuanmo.arcartxsuite.api.bridge.ContentModerator contentModerator;
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier;
    private final xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher;
    private final ItemMatcherAPI itemMatcher;
    private final Logger logger;
    private final CrossServerAPI crossServer;
    private final MessageProvider messages;
    private final xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator scriptConditionEvaluator;
    private final Map<String, Map<UUID, String>> actionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> actionTokensInFlight = ConcurrentHashMap.newKeySet();
    /** 打开商店/编辑 UI 时预选商店的暂存（在对应 list 包处理时消费）。 */
    private final Map<UUID, String> pendingShopSelections = new ConcurrentHashMap<>();
    private final Set<UUID> pendingMyAuctions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingMyShops = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> sellModes = new ConcurrentHashMap<>();

    private static final int AUCTION_PAGE_SIZE = 20;
    private static final String AUCTION_SELL_UI_ID = "AXS:market_auction_sell";
    private static final String SHOP_ADMIN_PERMISSION = "arcartxsuite.market.admin";
    private static final int HISTORY_PAGE_SIZE = 20;
    private static final int PSHOP_PAGE_SIZE = 20;
    private static final int PSHOP_SELLERS_PAGE_SIZE = 20;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    /**
     * 将 ARIA UI ~Backpack 槽位编号转换为 Bukkit inventory slot index。
     * ARIA rawSlot 9-35 → Bukkit 9-35（主背包 27 格），
     * ARIA rawSlot 36-44 → Bukkit 0-8（快捷栏 9 格）。
     */
    private static int toBukkitBackpackSlot(int rawSlot) {
        if (rawSlot >= 36 && rawSlot <= 44) {
            return rawSlot - 36;
        }
        return rawSlot;
    }

    private volatile java.util.function.Supplier<SignalDispatchable> signalProvider;
    private volatile java.util.function.Supplier<EventBusCapability> eventBusProvider;

    private xuanmo.arcartxsuite.market.auction.AuctionRoomService auctionRooms;
    private final Map<UUID,List<String>> auctionBrowseContext=new ConcurrentHashMap<>();
    private final Set<UUID> restoreAuctionBrowse=ConcurrentHashMap.newKeySet();
    private final Map<UUID,String> pendingAuctionCategories=new ConcurrentHashMap<>();
    private xuanmo.arcartxsuite.market.playershop.PlayerShopController playerStores;
    private final Map<UUID,List<String>> playerShopBrowseContext=new ConcurrentHashMap<>();
    private final Set<UUID> restorePlayerShopBrowse=ConcurrentHashMap.newKeySet();
    private MarketManagementController management;
    private MarketLedger ledger;
    private PendingDeliveryService deliveries;
    private MarketRepository repository;
    private RedisMarketCache redisCache;
    private CrossServerChannel crossServerChannel;
    private AuctionService auctionService;
    private ShopService shopService;
    private RecycleService recycleService;
    private DynamicShopService dynamicShopService;
    private PlayerShopService playerShopService;
    private final SchedulerAPI scheduler;

    public MarketService(JavaPlugin plugin, MarketModuleConfiguration config,
                         PacketBridgeAPI packetBridge, CurrencyBridgeAPI currencyManager,
                         ItemSourceRegistry itemSourceRegistry, AuctionItemSerializer itemSerializer,
                         @Nullable ItemBridgeAPI itemStackBridge,
                         xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge,
                         @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier,
                         xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher,
                         ItemMatcherAPI itemMatcher,
                         Logger logger,
                         CrossServerAPI crossServer,
                         SchedulerAPI scheduler,
                         MessageProvider messages,
                         xuanmo.arcartxsuite.api.bridge.ContentModerator contentModerator,
                         xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator scriptConditionEvaluator) {
        this.plugin = plugin;
        this.config = config;
        this.packetBridge = packetBridge;
        this.currencyManager = currencyManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.iconResolver = new IconResolver(itemStackBridge, itemSourceRegistry);
        this.itemSerializer = itemSerializer;
        this.itemStackBridge = itemStackBridge;
        this.vanillaItemNameBridge = vanillaItemNameBridge;
        this.mailSupplier = mailSupplier;
        this.itemRewardDispatcher = itemRewardDispatcher;
        this.itemMatcher = itemMatcher;
        this.logger = logger;
        this.crossServer = crossServer;
        this.scheduler = scheduler;
        this.messages = messages;
        this.contentModerator = contentModerator != null ? contentModerator : new xuanmo.arcartxsuite.market.NoopContentModerator();
        this.scriptConditionEvaluator = scriptConditionEvaluator != null
            ? scriptConditionEvaluator
            : xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator.noop();
    }

    public void setSignalProvider(java.util.function.Supplier<SignalDispatchable> provider) {
        this.signalProvider = provider;
    }

    public void setEventBusProvider(java.util.function.Supplier<EventBusCapability> provider) {
        this.eventBusProvider = provider;
    }

    /** 玩家退出时清理其 actionToken 与在飞行标记，避免内存累积。 */
    public void clearPlayerState(UUID playerUuid) {
        actionTokens.values().forEach(map -> map.remove(playerUuid));
        actionTokensInFlight.remove(playerUuid);
        if(management!=null)management.clear(playerUuid);
        playerShopBrowseContext.remove(playerUuid);restorePlayerShopBrowse.remove(playerUuid);
        if(playerStores!=null)playerStores.clear(playerUuid);
        if(auctionRooms!=null)auctionRooms.leave(playerUuid);
        auctionBrowseContext.remove(playerUuid);restoreAuctionBrowse.remove(playerUuid);pendingAuctionCategories.remove(playerUuid);
        pendingShopSelections.remove(playerUuid);
        pendingMyAuctions.remove(playerUuid);
        pendingMyShops.remove(playerUuid);
        sellModes.remove(playerUuid);
    }

    public void start(java.io.File dataFolder, JdbcMarketRepository repository) throws Exception {
        // 初始化存储
        this.repository = repository;
        repository.initialize();
        ledger = new MarketLedger(repository, logger);
        CurrencyBridgeAPI tradingCurrencies = ledger.wrap(currencyManager);

        // 初始化 Redis
        redisCache = new RedisMarketCache(config.redis(), logger);
        redisCache.initialize();
        redisCache.invalidateByPrefix("market:listings:");
        redisCache.invalidateByPrefix("market:pshop:");

        crossServerChannel = crossServer.openChannel(
            "market",
            config.crossServer(),
            delivery -> handleCrossServerMessage(delivery)
        );
        java.util.function.Consumer<String> crossServerPublisher = message -> {
            if (crossServerChannel != null && crossServerChannel.isActive()) {
                crossServerChannel.publish(message);
            }
        };

        management=new MarketManagementController(plugin,repository,config,packetBridge,itemStackBridge,itemSerializer,vanillaItemNameBridge,
            new MarketManagementController.Host(){
                public void open(Player player,String ui){packetBridge.openUi(player,ui);issueActionToken(player,ui);}
                public void send(Player player,String ui,String handler,Map<String,Object> packet){sendUiPacket(player,ui,handler,packet);}
                public void rotate(Player player,String ui){rotateToken(player,ui);}
                public void recycle(Player player){openRecycleUi(player);}
                public void publish(String payload){var channel=crossServerChannel;try{if(channel!=null && channel.isActive())channel.publish(payload);}catch(RuntimeException exception){logger.warning("保护设置同步通知暂不可用");}}
                public void protectionChanged(Player player,xuanmo.arcartxsuite.market.recycle.RecyclePreferences preferences){
                    if(recycleService!=null && packetBridge.isUiOpen(player,config.ui().recycleId()))handleRecyclePreviewPacket(player);
                }
                public void protectionUnavailable(Player player){
                    if(recycleService!=null && packetBridge.isUiOpen(player,config.ui().recycleId()))handleRecyclePreviewPacket(player);
                }
            },logger);
        // 启动子服务
        if (config.auction().enabled()) {
            auctionService = new AuctionService(plugin, config.auction(), config.messages(),
                repository, redisCache, crossServerPublisher, tradingCurrencies, itemSerializer, logger, vanillaItemNameBridge, itemRewardDispatcher, itemMatcher);
            auctionService.setEventPublisher(this::publishEvent);
            auctionService.setLedger(ledger);
            auctionService.start(config.schedulerIntervalTicks());
            auctionRooms=new xuanmo.arcartxsuite.market.auction.AuctionRoomService(plugin,repository,packetBridge,config.ui().auctionRoomId(),
                this::sendAuctionRoom,(player,message)->{
                    Map<String,Object> packet=new LinkedHashMap<>();packet.put("message",message);packet.put("packetId",config.ui().packetId());
                    sendUiPacket(player,config.ui().auctionRoomId(),"room_error",packet);
                },logger);
            auctionRooms.start();
        }

        if (config.shop().enabled()) {
            shopService = new ShopService(plugin, config.shop(), config.messages(),
                repository, tradingCurrencies, itemSourceRegistry, scriptConditionEvaluator, dataFolder, logger);
            // 注入跨服限购广播回调
            shopService.setLimitBroadcaster((playerUuid, shopId, itemId, newCount, timestamp, resetTime) -> {
                if (crossServerChannel != null && crossServerChannel.isActive()) {
                    crossServerChannel.publish("SHOP_LIMIT_UPDATE:" + playerUuid + ":" + shopId + ":"
                        + itemId + ":" + newCount + ":" + timestamp + ":" + resetTime);
                }
            });
            shopService.setLedger(ledger);
            shopService.start();

            // 初始化动态商品策略服务
            if (config.dynamic().enabled() && !config.dynamic().strategies().isEmpty() && scheduler != null) {
                dynamicShopService = new DynamicShopService(
                    plugin, logger, scheduler, shopService, repository, config.dynamic().strategies()
                );
                dynamicShopService.setStateBroadcaster((action, strategyKey, shopId, data) -> {
                    if (crossServerChannel != null && crossServerChannel.isActive()) {
                        crossServerChannel.publish("DYNAMIC_SHOP_UPDATE:" + strategyKey + ":" + shopId + ":" + data);
                    }
                });
                dynamicShopService.setEventPublisher((topic, payload) -> publishEvent(topic, null, payload));
                shopService.setDynamicShopService(dynamicShopService);
                dynamicShopService.start();
            }
        }

        if (config.recycle().enabled()) {
            recycleService = new RecycleService(config.recycle(), config.messages(),
                repository, tradingCurrencies, itemSourceRegistry, dataFolder, logger);
            recycleService.setLedger(ledger);
            recycleService.setProtection(management.protection());
            recycleService.start();
        }

        if (config.playerShop().enabled()) {
            playerShopService = new PlayerShopService(plugin, config.playerShop(), config.messages(),
                repository, redisCache, crossServerPublisher, tradingCurrencies, itemSerializer, logger,
                vanillaItemNameBridge, itemRewardDispatcher, itemMatcher);
            playerShopService.setLedger(ledger);
            playerShopService.start(config.schedulerIntervalTicks());
            playerStores=new xuanmo.arcartxsuite.market.playershop.PlayerShopController(plugin,repository,config,packetBridge,itemStackBridge,itemSerializer,currencyManager,contentModerator,
                new xuanmo.arcartxsuite.market.playershop.PlayerShopController.Host(){
                    public void open(Player player,String ui){packetBridge.openUi(player,ui);issueActionToken(player,ui);}
                    public void send(Player player,String ui,String handler,Map<String,Object> packet){sendUiPacket(player,ui,handler,packet);}
                    public void rotate(Player player,String ui){rotateToken(player,ui);}
                    public void market(Player player){openPlayerShopMarketUi(player);}
                    public void backMarket(Player player){restorePlayerShopBrowse.add(player.getUniqueId());openPlayerShopMarketUi(player);}
                    public void sell(Player player){openSellUi(player,"pshop");}
                    public void publish(String payload){
                        var channel=crossServerChannel;
                        try{if(channel!=null && channel.isActive())channel.publish(payload);}
                        catch(RuntimeException exception){logger.warning("店铺资料已保存，但同步通知暂不可用: "+exception.getMessage());}
                    }
                },logger);
        }

        for(Player player:org.bukkit.Bukkit.getOnlinePlayers())management.warm(player);
        logger.info("全球市场服务已启动");
    }

    public MarketLedger getLedger() { return ledger; }

    public void attachDeliveries(PendingDeliveryService deliveries) { this.deliveries = deliveries; deliveries.start(); }

    public void shutdown() {
        if(management!=null){management.close();management=null;}
        if(playerStores!=null){playerStores.close();playerStores=null;}
        playerShopBrowseContext.clear();restorePlayerShopBrowse.clear();
        if(auctionRooms!=null){auctionRooms.close();auctionRooms=null;}
        auctionBrowseContext.clear();restoreAuctionBrowse.clear();pendingAuctionCategories.clear();
        if (deliveries != null) { deliveries.close(); deliveries = null; }
        if (auctionService != null) { auctionService.shutdown(); auctionService = null; }
        if (dynamicShopService != null) { dynamicShopService.shutdown(); dynamicShopService = null; }
        if (shopService != null) { shopService.shutdown(); shopService = null; }
        if (recycleService != null) { recycleService.shutdown(); recycleService = null; }
        if (playerShopService != null) { playerShopService.shutdown(); playerShopService = null; }
        if (redisCache != null) { redisCache.shutdown(); redisCache = null; }
        if (crossServerChannel != null) { crossServerChannel.close(); crossServerChannel = null; }
        if (repository != null) { repository.shutdown(); repository = null; }
    }

    public MarketRepository getRepository() {
        return repository;
    }

    public void reload() {
        if (shopService != null) shopService.reload();
        if (recycleService != null) recycleService.reload();
    }

    // ─── 拍卖行操作 ─────────────────────────────────────────

    public boolean createListing(Player player, ItemStack item, double startingBid, String currency, long duration) {
        if (auctionService == null) {
            player.sendMessage(messages.get("player.auction-unavailable"));
            return false;
        }
        // 价格范围校验（覆盖命令路径，UI 路径在 handleAuctionListCreatePacket 已校验）
        if (!Double.isFinite(startingBid) || startingBid < config.auction().minPrice()) {
            player.sendMessage(messages.get("player.sell.price-out-of-range",
                formatPrice(config.auction().minPrice()),
                formatPrice(config.auction().maxPrice())));
            return false;
        }
        if (startingBid > 0 && (startingBid < config.auction().minPrice() || startingBid > config.auction().maxPrice())) {
            player.sendMessage(messages.get("player.sell.price-out-of-range",
                formatPrice(config.auction().minPrice()),
                formatPrice(config.auction().maxPrice())));
            return false;
        }
        // 货币校验
        String effectiveCurrency = currency != null && !currency.isBlank() ? currency : config.auction().defaultCurrency();
        var currencyOption = config.auction().currencies().stream()
            .filter(option -> option.id().equalsIgnoreCase(effectiveCurrency))
            .findFirst().orElse(null);
        if (currencyOption == null || currencyManager.bridge(effectiveCurrency) == null
            || !currencyManager.bridge(effectiveCurrency).available()) {
            player.sendMessage(messages.get("player.sell.invalid-currency"));
            return false;
        }
        if (duration <= 0) duration = config.auction().defaultDurationSeconds();
        var result = auctionService.createListing(player, item, startingBid, currencyOption.id(), duration);
        if (result.success()) {
            rotateToken(player, config.ui().auctionId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionListed()));
            dispatchSignal("market_listing_created", player, Map.of(
                "price", String.valueOf(startingBid),
                "currency", currencyOption.id()
            ));
            publishEvent("axs.market.listing_created", player, Map.of(
                "price", String.valueOf(startingBid),
                "currency", currencyOption.id()
            ));
            return true;
        }
        player.sendMessage(ChatColor.RED + result.error());
        return false;
    }

    public boolean cancelListing(Player player, long listingId) {
        if (auctionService == null) return false;
        boolean success = auctionService.cancelListing(player, listingId);
        if (success) {
            rotateToken(player, activeAuctionUi(player));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionCancelled()));
        } else {
            player.sendMessage(messages.get("player.cancel-failed"));
            if(!config.auction().allowCancelWithBids()) player.sendMessage(ChatColor.GRAY+"已有有效出价或已到期的拍卖不能由卖家下架；管理员可强制处理。");
        }
        return success;
    }

    public void searchAuction(Player player, String keyword) {
        // 打开拍卖 UI 并立即用 keyword 触发服务端搜索，把结果推送给客户端
        packetBridge.openUi(player, config.ui().auctionId());
        issueActionToken(player, config.ui().auctionId());
        handleAuctionListPacket(player, List.of("auction_list", "0", "all", keyword == null ? "" : keyword));
    }

    // ─── 系统商店操作 ────────────────────────────────────────

    public void buyFromShop(Player player, String shopId, String itemId, int amount) {
        buyFromShop(player, shopId, itemId, amount, null);
    }

    private void buyFromShop(Player player, String shopId, String itemId, int amount, @Nullable BigDecimal expectedPrice) {
        if (shopService == null) {
            player.sendMessage(messages.get("player.shop-unavailable"));
            return;
        }
        var result = shopService.buy(player, shopId, itemId, amount, expectedPrice);
        if (result.success()) {
            rotateToken(player, config.ui().shopId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().shopBought().replace("{amount}",
                    currencyManager.format(result.currency(), BigDecimal.valueOf(result.totalPrice())))));
        } else {
            player.sendMessage(ChatColor.RED + result.error());
        }
    }

    // ─── 回收操作 ───────────────────────────────────────────

    public void recycleBatch(Player player) {
        if (recycleService == null) {
            player.sendMessage(messages.get("player.recycle-unavailable"));
            return;
        }
        if (!config.recycle().batchRecycleEnabled()) {
            player.sendMessage(messages.get("player.batch-recycle-disabled"));
            return;
        }
        try {
            var result = recycleService.recycleBatch(player);
            if (result.success()) {
                rotateToken(player, config.ui().recycleId());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.messages().recycleSuccess().replace("{amount}",
                        formatRecycleEarnings(result.earningsByCurrency()))));
            } else {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', result.error()));
            }
        } catch(RuntimeException exception) {
            logger.warning("[MarketRecycle] 回收处理需核对 player="+player.getUniqueId()+": "+exception.getMessage());
            player.sendMessage(ChatColor.RED+"回收处理中断，请联系管理员通过交易管理核对已受理的记录。");
        }
    }

    public void requestBatchRecycle(Player player) {
        if (config.recycle().batchConfirm()) openRecycleUi(player);
        else recycleBatch(player);
    }

    // ─── UI 打开 ────────────────────────────────────────────

    public void openAuctionUi(Player player) {
        if (auctionService == null) { player.sendMessage(messages.get("player.auction-unavailable")); return; }
        packetBridge.openUi(player, config.ui().auctionId());
        issueActionToken(player, config.ui().auctionId());
    }

    public void openAuctionSellUi(Player player) {
        openSellUi(player, "auction");
    }

    public void openShopListUi(Player player) {
        openShopUi(player);
    }

    // 当前所有商店共用一个 UI ID；未来若支持多商店，可在此按 shopId 查找对应 UI
    public void openShopUi(Player player) {
        openShopUi(player, null);
    }

    /**
     * 打开系统商店 UI，可预选商店（由下一个 shop_list 包消费）。
     */
    public boolean openSystemShop(Player player, String id) {
        if (shopService == null || id == null || !packetBridge.isAvailable()) return false;
        var shop = shopService.getShop(id);
        if (shop == null || (!shop.permission().isBlank() && !player.hasPermission(shop.permission()))) return false;
        openShopUi(player, id);
        return packetBridge.isUiOpen(player, config.ui().shopId());
    }

    public void openShopUi(Player player, @Nullable String shopId) {
        if (shopService == null) { player.sendMessage(messages.get("player.shop-unavailable")); return; }
        if (shopId != null && !shopId.isBlank()) {
            pendingShopSelections.put(player.getUniqueId(), shopId);
        } else {
            pendingShopSelections.remove(player.getUniqueId());
        }
        packetBridge.openUi(player, config.ui().shopId());
        issueActionToken(player, config.ui().shopId());
    }

    /**
     * 打开系统商店编辑 UI（仅管理员），可预选商店（由 shop_edit_list 包消费）。
     */
    public void openShopEditUi(Player player, @Nullable String shopId) {
        if (shopService == null) {
            player.sendMessage(messages.get("player.shop-unavailable"));
            return;
        }
        if (!isShopAdmin(player)) {
            player.sendMessage(messages.get("common.no-permission"));
            return;
        }
        if (shopId != null && !shopId.isBlank()) {
            pendingShopSelections.put(player.getUniqueId(), shopId);
        } else {
            pendingShopSelections.remove(player.getUniqueId());
        }
        packetBridge.openUi(player, shopEditUiId());
        issueActionToken(player, shopEditUiId());
    }

    /** 商店编辑 UI 与编辑包的服务端权限门槛。 */
    public boolean isShopAdmin(Player player) {
        return player.hasPermission(SHOP_ADMIN_PERMISSION);
    }

    /** 商店编辑 UI 的运行时 ID（可在 ArcartXMarket.yml 的 ui.shop-edit-ui-id 重映射）。 */
    private String shopEditUiId() {
        return config.ui().shopEditId();
    }

    public void warmMarketPlayer(Player player){if(management!=null)management.warm(player);}
    public void openRecycleProtection(Player player){
        if(management==null || recycleService==null){player.sendMessage(messages.get("player.recycle-unavailable"));return;}
        management.openProtection(player);
    }
    public void openTradeManagement(Player player){if(management!=null)management.openAudit(player);}

    public void openRecycleUi(Player player) {
        if (recycleService == null) { player.sendMessage(messages.get("player.recycle-unavailable")); return; }
        packetBridge.openUi(player, config.ui().recycleId());
        issueActionToken(player, config.ui().recycleId());
    }

    public void openHistoryUi(Player player) {
        packetBridge.openUi(player, config.ui().historyId());
    }

    public void openSellUi(Player player) {
        openSellUi(player, auctionService != null ? "auction" : "pshop");
    }

    public void openSellUi(Player player, String mode) {
        if (auctionService == null && playerShopService == null) {
            player.sendMessage(messages.get("player.sell-unavailable"));
            return;
        }
        sellModes.put(player.getUniqueId(), "pshop".equals(mode) && playerShopService != null ? "pshop"
            : auctionService != null ? "auction" : "pshop");
        packetBridge.openUi(player, config.ui().sellId());
        issueActionToken(player, config.ui().sellId());
    }

    public void openPlayerShopUi(Player player) {
        if(playerStores==null){player.sendMessage(messages.get("player.pshop-unavailable"));return;}
        playerStores.openDirectory(player);
    }

    public boolean openPlayerStore(Player player,UUID owner) {
        if(playerStores==null){player.sendMessage(messages.get("player.pshop-unavailable"));return false;}
        return playerStores.openStore(player,owner);
    }

    public void openPlayerStoreEditor(Player player) {
        if(playerStores==null){player.sendMessage(messages.get("player.pshop-unavailable"));return;}
        playerStores.openEditor(player);
    }

    public void openPlayerShopMarketUi(Player player) {
        if (playerShopService == null) { player.sendMessage(messages.get("player.pshop-unavailable")); return; }
        packetBridge.openUi(player, config.ui().playerShopId());
        issueActionToken(player, config.ui().playerShopId());
    }

    public void openMyListingsUi(Player player) {
        pendingMyAuctions.add(player.getUniqueId());
        openAuctionUi(player);
    }

    public void openMyPlayerShopUi(Player player) {
        openPlayerStore(player,player.getUniqueId());
    }

    // ─── 客户端包处理 ────────────────────────────────────────

    /** 判断该包是否归本模块处理（供模块层在切主线程前快速判定）。 */
    public boolean ownsPacket(String packetId) {
        return config.ui().packetId().equals(packetId) || "AXS_MARKET".equals(packetId);
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (!ownsPacket(packetId)) return false;
        if (data.isEmpty()) return false;

        // data[0] = action, 后续字段为参数
        String action = data.get(0);
        // Bundled UI files bootstrap with the stable ID. Only allow the initial
        // read for the screen actually open, then advertise the configured ID.
        if (!config.ui().packetId().equals(packetId)) {
            String bootstrapUi = switch (action) {
                case "auction_list" -> config.ui().auctionId();
                case "auction_room_open" -> config.ui().auctionRoomId();
                case "shop_list" -> config.ui().shopId();
                case "recycle_preview" -> config.ui().recycleId();
                case "guard_open" -> config.ui().recycleProtectionId();
                case "audit_open" -> config.ui().adminTradesId();
                case "history_list" -> config.ui().historyId();
                case "sell_open" -> config.ui().sellId();
                case "pshop_list", "pshop_sellers" -> config.ui().playerShopId();
                case "pshop_directory_open" -> config.ui().playerShopsId();
                case "pshop_store_open" -> config.ui().playerStoreId();
                case "pshop_profile_open" -> config.ui().playerStoreEditId();
                case "shop_edit_list" -> shopEditUiId();
                default -> null;
            };
            if (bootstrapUi == null || !packetBridge.isUiOpen(player, bootstrapUi)) return false;
        }
        // 商店编辑系列包全部要求管理员权限（UI 内按钮不可信）
        if (action.startsWith("shop_edit") && !isShopAdmin(player)) return true;
        if (action.startsWith("shop_edit") && !action.equals("shop_edit_ui_open")
            && !packetBridge.isUiOpen(player, shopEditUiId())) return true;
        if (requiresActionToken(action) && !beginActionToken(player, action, data)) {
            sendActionState(player, action,true);
            return true;
        }
        try {
        switch (action) {
            case "auction_list" -> handleAuctionListPacket(player, data);
            case "auction_bid" -> handleAuctionBidPacket(player, data);
            case "auction_quote" -> handleAuctionQuote(player, data);
            case "auction_room_ui_open" -> {if(data.size()>1)openAuctionRoom(player,parseLongSafe(data.get(1),0));}
            case "auction_room_open", "auction_room_refresh" -> {
                if(auctionRooms!=null && auctionRooms.selected(player)>0)auctionRooms.request(player);
                else sendAuctionRoom(player,null);
            }
            case "auction_room_back" -> {
                if(auctionRooms!=null)auctionRooms.leave(player.getUniqueId());
                restoreAuctionBrowse.add(player.getUniqueId());openAuctionUi(player);
            }
            case "auction_cancel" -> handleAuctionCancelPacket(player, data);
            case "auction_sell_ui_open" -> openAuctionSellUi(player);
            case "auction_sell_open" -> handleAuctionSellOpenPacket(player);
            case "auction_list_create" -> handleAuctionListCreatePacket(player, data);
            case "auction_favorite" -> handleAuctionFavoritePacket(player, data);
            case "shop_list" -> handleShopListPacket(player, data);
            case "shop_quote" -> handleShopQuote(player, data);
            case "shop_buy" -> handleShopBuyPacket(player, data);
            case "recycle_all" -> handleRecycleAll(player, data);
            case "recycle_single" -> handleRecycleSinglePacket(player, data);
            case "recycle_preview" -> handleRecyclePreviewPacket(player);
            case "history_list" -> handleHistoryListPacket(player, data);
            case "sell_open" -> handleSellOpenPacket(player);
            case "sell_ui_open" -> openSellUi(player, data.size() > 1 ? data.get(1) : "auction");
            case "auction_ui_open" -> openAuctionUi(player);
            case "pshop_ui_open" -> openPlayerShopUi(player);
            case "shop_ui_open" -> openShopUi(player, data.size() > 1 ? data.get(1) : null);
            case "shop_edit_ui_open" -> openShopEditUi(player, data.size() > 1 ? data.get(1) : null);
            case "shop_edit_list" -> handleShopEditList(player);
            case "shop_edit_shop" -> handleShopEditShop(player, data);
            case "shop_edit_item" -> handleShopEditItem(player, data);
            case "shop_edit_pick" -> handleShopEditPick(player, data);
            case "shop_edit_preview" -> handleShopEditPreview(player, data);
            case "shop_edit_stock" -> handleShopEditStock(player, data);
            case "shop_edit_shop_save" -> handleShopEditShopSave(player, data);
            case "shop_edit_shop_delete" -> handleShopEditShopDelete(player, data);
            case "shop_edit_item_save" -> handleShopEditItemSave(player, data);
            case "shop_edit_item_delete" -> handleShopEditItemDelete(player, data);
            case "recycle_ui_open" -> openRecycleUi(player);
            case "guard_ui_open" -> openRecycleProtection(player);
            case "audit_ui_open" -> openTradeManagement(player);
            case "guard_open","guard_list","guard_refresh","guard_back","guard_pick","guard_add","guard_remove","guard_auto",
                "audit_open","audit_filter","audit_page","audit_detail","audit_group","audit_back_filter","audit_cancel" -> {
                    if(management!=null)management.handle(player,action,data);
                }
            case "history_ui_open" -> openHistoryUi(player);
            case "pshop_directory_ui_open","pshop_market_ui_open","pshop_store_ui_open","pshop_profile_ui_open",
                "pshop_directory_open","pshop_directory_list","pshop_store_open","pshop_store_list","pshop_store_quote","pshop_store_back","pshop_store_share","pshop_profile_back",
                "pshop_profile_open","pshop_profile_status","pshop_profile_pick","pshop_profile_save","pshop_store_follow","pshop_store_sell" -> {
                    if(playerStores!=null)playerStores.handle(player,action,data);
                }
            case "pshop_list" -> handlePlayerShopListPacket(player, data);
            case "pshop_sellers" -> handlePlayerShopSellersPacket(player, data);
            case "pshop_buy" -> handlePlayerShopBuyPacket(player, data);
            case "pshop_cancel" -> handlePlayerShopCancelPacket(player, data);
            case "pshop_create" -> handlePlayerShopCreatePacket(player, data);
            default -> { return false; }
        }
        return true;
        } catch (RuntimeException exception) {
            logger.severe("[Market] 操作中断 action=" + action + " player=" + player.getUniqueId() + ": " + exception.getMessage());
            player.sendMessage(ChatColor.RED + "市场操作中断，请查看交易流水或联系管理员核对，勿重复提交未确认的交易。");
            if ("shop_quote".equals(action) && data.size() > 2) {
                Map<String,Object> failed = new LinkedHashMap<>(); failed.put("packetId",config.ui().packetId());
                failed.put("shopId",data.get(1)); failed.put("itemId",data.get(2)); failed.put("available",false);
                sendUiPacket(player,config.ui().shopId(),"shop_quote",failed);
            }
            if ("auction_quote".equals(action) && data.size()>1) {
                Map<String,Object> failed=new LinkedHashMap<>();failed.put("packetId",config.ui().packetId());
                failed.put("id",data.get(1));failed.put("available",false);failed.put("reason","报价暂不可用，请关闭弹窗后重试");
                sendUiPacket(player,activeAuctionUi(player),"auction_quote",failed);
            }
            return true;
        } finally {
            if (requiresActionToken(action)) {
                actionTokensInFlight.remove(player.getUniqueId());
                sendActionState(player, action);
            }
        }
    }

    // 协议格式: data[0]=action, data[1..n]=参数
    // auction_list: [action, page, category?, keyword?]
    // auction_bid: [action, listingId, amount]
    // auction_cancel: [action, listingId]
    // auction_favorite: [action, listingId]
    // shop_list: [action, shopId?]
    // shop_buy: [action, shopId, itemId, amount]
    // shop_edit_ui_open: [action, shopId?]
    // shop_edit_list: [action]
    // shop_edit_shop: [action, shopId]
    // shop_edit_item: [action, shopId, itemKey]
    // shop_edit_pick: [action, rawSlot(9-44), target(shop_icon|item_icon|item_def)]
    // shop_edit_shop_save: [action, shopId, isNew(0/1), json, token]
    // shop_edit_shop_delete: [action, shopId, token]
    // shop_edit_item_save: [action, shopId, isNew(0/1), json, token]
    // shop_edit_item_delete: [action, shopId, itemKey, token]
    // recycle_all: [action]
    // recycle_single: [action, slot]
    // recycle_preview: [action]
    // history_list: [action, page]

    private void handleAuctionSellOpenPacket(Player player) {
        if (auctionService == null) return;
        sendUiPacket(player, AUCTION_SELL_UI_ID, "update", buildAuctionSellPacket(player));
    }

    private void handleAuctionListCreatePacket(Player player, List<String> data) {
        if (auctionService == null) return;
        int rawSlot = data.size() > 1 ? parseIntSafe(data.get(1), -1) : -1;
        double startingBid = data.size() > 2 ? parseDoubleSafe(data.get(2), 0) : 0;
        String message = data.size() > 3 && data.get(3) != null ? data.get(3) : "";
        long duration = data.size() > 4 ? parseLongSafe(data.get(4), 0) : 0;
        String currency = data.size() > 5 && data.get(5) != null && !data.get(5).isBlank()
            ? data.get(5) : config.auction().defaultCurrency();
        if (rawSlot < 9 || rawSlot > 44) {
            player.sendMessage(messages.get("player.sell.invalid-slot"));
            return;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        if (startingBid < config.auction().minPrice()
                || startingBid > config.auction().maxPrice()) {
            player.sendMessage(messages.get("player.sell.price-out-of-range",
                formatPrice(config.auction().minPrice()),
                formatPrice(config.auction().maxPrice())));
            return;
        }
        if (duration < config.auction().minDurationSeconds()
                || duration > config.auction().maxDurationSeconds()) {
            player.sendMessage(messages.get("player.sell.invalid-duration"));
            return;
        }
        if (message.length() > config.auction().messageMaxLength()) {
            player.sendMessage(messages.get("player.sell.message-too-long",
                String.valueOf(config.auction().messageMaxLength())));
            return;
        }
        message = filterMessage(player, message);
        if (message == null) return;
        var currencyOption = config.auction().currencies().stream()
            .filter(option -> option.id().equalsIgnoreCase(currency))
            .findFirst().orElse(null);
        var currencyBridge = currencyOption == null ? null : currencyManager.bridge(currencyOption.id());
        if (currencyOption == null || currencyBridge == null || !currencyBridge.available()) {
            player.sendMessage(messages.get("player.sell.invalid-currency"));
            return;
        }
        var result = auctionService.createListing(
            player, slot, startingBid, currencyOption.id(), duration, message);
        if (!result.success()) {
            player.sendMessage(ChatColor.RED + result.error());
            return;
        }
        rotateToken(player, AUCTION_SELL_UI_ID);
        player.sendMessage(ChatColor.translateAlternateColorCodes((char) 38, config.messages().auctionListed()));
        openAuctionUi(player);
        handleAuctionListPacket(player, List.of("auction_list", "0", "all", ""));
    }

    // ─── 玩家商店包处理 ──────────────────────────────────────

    private void handleSellOpenPacket(Player player) {
        if (playerShopService == null && auctionService == null) return;
        // 无论从何处打开本 UI（Screen.open 或 openSellUi），都在此签发 token，
        // 保证确认上架按钮的 enable 条件 var.actionToken != '' 成立
        issueActionToken(player, config.ui().sellId());
        sendUiPacket(player, config.ui().sellId(), "update", buildSellPacket(player));
    }

    private Map<String, Object> buildSellPacket(Player player) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("initialMode", sellModes.getOrDefault(player.getUniqueId(), "auction"));
        packet.put("packetId", config.ui().packetId());
        // 拍卖行参数
        Map<String, Object> auctionCurrencies = new LinkedHashMap<>();
        int auctionIdx = 0;
        for (var option : config.auction().currencies()) {
            Map<String, Object> currency = new LinkedHashMap<>();
            currency.put("id", option.id());
            currency.put("name", option.name());
            auctionCurrencies.put(Integer.toString(auctionIdx++), currency);
        }
        packet.put("auctionCurrencies", auctionCurrencies);
        packet.put("auctionMaxCurrencyCount", auctionCurrencies.size());
        packet.put("auctionDefaultCurrency", config.auction().defaultCurrency());
        packet.put("auctionPriceMin", config.auction().minPrice());
        packet.put("auctionPriceMax", config.auction().maxPrice());
        packet.put("auctionMessageMaxLength", config.auction().messageMaxLength());
        packet.put("auctionEnabled", config.auction().enabled());
        packet.put("auctionDurationMin", config.auction().minDurationSeconds());
        packet.put("auctionDurationMax", config.auction().maxDurationSeconds());
        packet.put("auctionDurationDefault", config.auction().defaultDurationSeconds());
        // 玩家商店参数
        Map<String, Object> pshopCurrencies = new LinkedHashMap<>();
        int pshopIdx = 0;
        for (var option : config.playerShop().currencies()) {
            Map<String, Object> currency = new LinkedHashMap<>();
            currency.put("id", option.id());
            currency.put("name", option.name());
            pshopCurrencies.put(Integer.toString(pshopIdx++), currency);
        }
        packet.put("pshopCurrencies", pshopCurrencies);
        packet.put("pshopMaxCurrencyCount", pshopCurrencies.size());
        packet.put("pshopDefaultCurrency", config.playerShop().defaultCurrency());
        packet.put("pshopPriceMin", config.playerShop().minPrice());
        packet.put("pshopPriceMax", config.playerShop().maxPrice());
        packet.put("pshopMessageMaxLength", config.playerShop().messageMaxLength());
        packet.put("pshopEnabled", config.playerShop().enabled());
        packet.put("pshopDurationMin", config.playerShop().minDurationSeconds());
        packet.put("pshopDurationMax", config.playerShop().maxDurationSeconds());
        packet.put("pshopDurationDefault", config.playerShop().defaultDurationSeconds());
        return packet;
    }

    private void handlePlayerShopCreatePacket(Player player, List<String> data) {
        if (playerShopService == null && auctionService == null) return;
        // data: [action, slot, price, message, duration, currency, mode, actionToken]
        // mode: "auction" 或 "pshop"
        int rawSlot = data.size() > 1 ? parseIntSafe(data.get(1), -1) : -1;
        double price = data.size() > 2 ? parseDoubleSafe(data.get(2), 0) : 0;
        String message = data.size() > 3 && data.get(3) != null ? data.get(3) : "";
        long duration = data.size() > 4 ? parseLongSafe(data.get(4), 0) : 0;
        String currency = data.size() > 5 && data.get(5) != null && !data.get(5).isBlank()
            ? data.get(5) : config.playerShop().defaultCurrency();
        String mode = data.size() > 6 && data.get(6) != null ? data.get(6) : "pshop";

        if ("auction".equalsIgnoreCase(mode)) {
            // 拍卖行模式：price 作为起拍价，无一口价（纯竞价拍卖）
            handleAuctionModeCreate(player, rawSlot, price, message, duration, currency);
            return;
        }

        // 玩家商店上架
        if (playerShopService == null) return;
        if (rawSlot < 9 || rawSlot > 44) {
            player.sendMessage(messages.get("player.sell.invalid-slot"));
            return;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        if (price < config.playerShop().minPrice() || price > config.playerShop().maxPrice()) {
            player.sendMessage(messages.get("player.sell.price-out-of-range",
                formatPrice(config.playerShop().minPrice()),
                formatPrice(config.playerShop().maxPrice())));
            return;
        }
        if (duration < config.playerShop().minDurationSeconds()
                || duration > config.playerShop().maxDurationSeconds()) {
            player.sendMessage(messages.get("player.sell.invalid-duration"));
            return;
        }
        if (message.length() > config.playerShop().messageMaxLength()) {
            player.sendMessage(messages.get("player.sell.message-too-long",
                String.valueOf(config.playerShop().messageMaxLength())));
            return;
        }
        message = filterMessage(player, message);
        if (message == null) return;
        var currencyOption = config.playerShop().currencies().stream()
            .filter(option -> option.id().equalsIgnoreCase(currency))
            .findFirst().orElse(null);
        var currencyBridge = currencyOption == null ? null : currencyManager.bridge(currencyOption.id());
        if (currencyOption == null || currencyBridge == null || !currencyBridge.available()) {
            player.sendMessage(messages.get("player.sell.invalid-currency"));
            return;
        }
        var result = playerShopService.createListing(player, slot, price, currencyOption.id(), duration, message);
        if (!result.success()) {
            player.sendMessage(ChatColor.RED + result.error());
            return;
        }
        rotateToken(player, config.ui().sellId());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().playerShopListed()));
        openPlayerStore(player,player.getUniqueId());
    }

    /**
     * 统一上架 UI 的拍卖行模式处理。
     * <p>
     * 与 {@link #handleAuctionListCreatePacket} 不同，此方法使用 sell UI 的 token。
     * price 作为起拍价（startingBid），无一口价（纯竞价拍卖）。
     * 上架成功后打开拍卖行 UI 供玩家查看。
     */
    private void handleAuctionModeCreate(Player player, int rawSlot, double startingBid,
                                          String message, long duration, String currency) {
        if (auctionService == null) return;
        if (rawSlot < 9 || rawSlot > 44) {
            player.sendMessage(messages.get("player.sell.invalid-slot"));
            return;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        if (startingBid < config.auction().minPrice() || startingBid > config.auction().maxPrice()) {
            player.sendMessage(messages.get("player.sell.price-out-of-range",
                formatPrice(config.auction().minPrice()),
                formatPrice(config.auction().maxPrice())));
            return;
        }
        if (duration < config.auction().minDurationSeconds()
                || duration > config.auction().maxDurationSeconds()) {
            player.sendMessage(messages.get("player.sell.invalid-duration"));
            return;
        }
        if (message.length() > config.auction().messageMaxLength()) {
            player.sendMessage(messages.get("player.sell.message-too-long",
                String.valueOf(config.auction().messageMaxLength())));
            return;
        }
        message = filterMessage(player, message);
        if (message == null) return;
        var currencyOption = config.auction().currencies().stream()
            .filter(option -> option.id().equalsIgnoreCase(currency))
            .findFirst().orElse(null);
        var currencyBridge = currencyOption == null ? null : currencyManager.bridge(currencyOption.id());
        if (currencyOption == null || currencyBridge == null || !currencyBridge.available()) {
            player.sendMessage(messages.get("player.sell.invalid-currency"));
            return;
        }
        // price 作为起拍价（纯竞价拍卖）
        var result = auctionService.createListing(
            player, slot, startingBid, currencyOption.id(), duration, message);
        if (!result.success()) {
            player.sendMessage(ChatColor.RED + result.error());
            return;
        }
        // 使用 sell UI 的 token（而非 AUCTION_SELL_UI_ID），与 beginActionToken 一致
        rotateToken(player, config.ui().sellId());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionListed()));
        openAuctionUi(player);
        handleAuctionListPacket(player, List.of("auction_list", "0", "all", ""));
    }

    private void handlePlayerShopListPacket(Player player, List<String> data) {
        if (playerShopService == null) return;
        if(restorePlayerShopBrowse.remove(player.getUniqueId()))data=playerShopBrowseContext.getOrDefault(player.getUniqueId(),data);
        // 无论从何处打开本 UI（Screen.open 或 openPlayerShopUi），都在首次列表请求时签发 token，
        // 保证购买/下架按钮的 enable 条件 var.actionToken != '' 成立
        issueActionToken(player, config.ui().playerShopId());
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        String keyword = data.size() > 2 ? data.get(2) : null;
        String sellerUuid = data.size() > 3 ? data.get(3) : null;
        if (pendingMyShops.remove(player.getUniqueId())) sellerUuid = player.getUniqueId().toString();
        page = Math.max(0, page);
        playerShopBrowseContext.put(player.getUniqueId(),List.of("pshop_list",String.valueOf(page),keyword==null?"":keyword,sellerUuid==null?"":sellerUuid,"refresh"));
        String cacheKey = listCacheKey("pshop", player, page, keyword, sellerUuid);
        if (data.size() > 4 && "refresh".equals(data.get(4))) {
            redisCache.invalidateByPrefix("market:pshop:" + player.getUniqueId() + ":");
        }
        Map<String, Object> cached = readListCache(cacheKey);
        if (cached != null) {
            sendUiPacket(player, config.ui().playerShopId(), "pshop_list", cached);
            return;
        }

        List<PlayerShopListing> listings;
        int totalCount;
        if (sellerUuid != null && !sellerUuid.isEmpty() && !"all".equals(sellerUuid)) {
            try {
                UUID seller = UUID.fromString(sellerUuid);
                if (keyword != null && !keyword.isBlank()) {
                    listings = playerShopService.searchListingsBySeller(seller, keyword, page, PSHOP_PAGE_SIZE);
                    totalCount = playerShopService.countSearchBySeller(seller, keyword);
                } else {
                    listings = playerShopService.getListingsBySeller(seller, page, PSHOP_PAGE_SIZE);
                    totalCount = playerShopService.countBySeller(seller);
                }
            } catch (IllegalArgumentException e) {
                listings = playerShopService.getActiveListings(page, PSHOP_PAGE_SIZE);
                totalCount = playerShopService.countActive();
            }
        } else if (keyword != null && !keyword.isEmpty()) {
            listings = playerShopService.searchListings(keyword, page, PSHOP_PAGE_SIZE);
            totalCount = playerShopService.countSearch(keyword);
        } else {
            listings = playerShopService.getActiveListings(page, PSHOP_PAGE_SIZE);
            totalCount = playerShopService.countActive();
        }
        int totalPages = Math.max(1, (totalCount + PSHOP_PAGE_SIZE - 1) / PSHOP_PAGE_SIZE);
        if (page >= totalPages) {
            handlePlayerShopListPacket(player, List.of("pshop_list", String.valueOf(totalPages - 1),
                keyword == null ? "" : keyword, sellerUuid == null ? "" : sellerUuid));
            return;
        }
        Map<String, Object> packet = buildPlayerShopPacket(player, listings, page, totalPages, keyword, sellerUuid);
        writeListCache(cacheKey, packet);
        sendUiPacket(player, config.ui().playerShopId(), "pshop_list", packet);
    }

    private void handlePlayerShopSellersPacket(Player player, List<String> data) {
        if (playerShopService == null) return;
        int totalCount = playerShopService.countSellers();
        int totalPages = Math.max(1, (totalCount + PSHOP_SELLERS_PAGE_SIZE - 1) / PSHOP_SELLERS_PAGE_SIZE);
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        page = Math.max(0, Math.min(page, totalPages - 1));
        var sellers = playerShopService.getSellers(page, PSHOP_SELLERS_PAGE_SIZE);
        sendUiPacket(player, config.ui().playerShopId(), "pshop_sellers",
            buildPlayerShopSellersPacket(player, sellers, page, totalPages));
    }

    private void handlePlayerShopBuyPacket(Player player, List<String> data) {
        if (playerShopService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        var result = playerStores!=null && playerStores.isStoreOpen(player)
            ?playerShopService.purchaseFromStore(player,listingId,playerStores.selectedOwner(player))
            :playerShopService.purchase(player, listingId);
        if (!result.success()) {
            player.sendMessage(ChatColor.RED + result.error());
            refreshPlayerShopAfterAction(player, data);
            return;
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            config.messages().playerShopBought().replace("{amount}",
                currencyManager.format(result.currency(), BigDecimal.valueOf(result.price())))));
        // 刷新列表
        refreshPlayerShopAfterAction(player, data);
    }

    private void handlePlayerShopCancelPacket(Player player, List<String> data) {
        if (playerShopService == null) return;
        if(playerStores!=null && playerStores.isStoreOpen(player) && !player.getUniqueId().equals(playerStores.selectedOwner(player))) {
            player.sendMessage(ChatColor.RED+"请在自己的店铺中下架商品");return;
        }
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (playerShopService.cancelListing(player, listingId)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().playerShopCancelled()));
        } else {
            player.sendMessage(ChatColor.RED + "下架失败");
        }
        refreshPlayerShopAfterAction(player, data);
    }

    private void refreshPlayerShopAfterAction(Player player, List<String> data) {
        if(playerStores!=null && playerStores.isStoreOpen(player)){
            rotateToken(player,config.ui().playerStoreId());playerStores.refresh(player);return;
        }
        handlePlayerShopListPacket(player, List.of("pshop_list", data.size() > 3 ? data.get(3) : "0",
            data.size() > 4 ? data.get(4) : "", data.size() > 5 ? data.get(5) : ""));
        handlePlayerShopSellersPacket(player, List.of("pshop_sellers", "0"));
    }

    private Map<String, Object> buildPlayerShopPacket(Player player, List<PlayerShopListing> listings,
                                                       int page, int totalPages, String keyword, String sellerUuid) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("page", page);
        packet.put("totalPages", totalPages);
        packet.put("pageText", (page + 1) + "/" + totalPages);
        packet.put("keyword", keyword == null ? "" : keyword);
        packet.put("sellerUuid", sellerUuid == null ? "" : sellerUuid);

        Map<String, Object> listingsMap = new LinkedHashMap<>();
        Map<String, Object> listingTexts = new LinkedHashMap<>();
        for (int i = 0; i < listings.size() && i < PSHOP_PAGE_SIZE; i++) {
            PlayerShopListing listing = listings.get(i);
            String key = Integer.toString(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", listing.getId());
            item.put("seller", listing.getSeller().toString());
            item.put("sellerName", listing.getSellerName());
            // itemJson 分离模式：itemData 反序列化为 ItemStack 再转 JSON，减小嵌套 packet 体积
            ItemDisplay preview = serializedItemPreview(listing.getItemData());
            packet.put("itemJson" + key, preview.json());
            item.put("itemDisplayName", listing.getItemDisplayName());
            item.put("itemAmount", preview.amount());
            item.put("price", BigDecimal.valueOf(listing.getPrice()).stripTrailingZeros().toPlainString());
            item.put("currency", listing.getCurrency());
            item.put("currencyName", currencyDisplayName(listing.getCurrency()));
            item.put("message", listing.getMessage());
            item.put("createdAt", listing.getCreatedAt());
            item.put("expiresAt", listing.getExpiresAt());
            item.put("isMine", listing.getSeller().equals(player.getUniqueId()));
            listingsMap.put(key, item);
            listingTexts.put(key, "&f" + listing.getItemDisplayName() + " &7| &e" + formatPrice(listing.getPrice()));
        }
        packet.put("listings", listingsMap);
        packet.put("listingTexts", listingTexts);
        packet.put("maxItemCount", listingsMap.size());
        packet.put("selfUuid", player.getUniqueId().toString());
        return packet;
    }

    private Map<String, Object> buildPlayerShopSellersPacket(Player player,
                                                              List<MarketRepository.PlayerShopSellerInfo> sellers,
                                                              int page, int totalPages) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("page", page);
        packet.put("totalPages", totalPages);
        packet.put("pageText", (page + 1) + "/" + totalPages);

        Map<String, Object> sellersMap = new LinkedHashMap<>();
        Map<String, Object> sellerTexts = new LinkedHashMap<>();
        for (int i = 0; i < sellers.size() && i < PSHOP_SELLERS_PAGE_SIZE; i++) {
            var info = sellers.get(i);
            String key = Integer.toString(i);
            Map<String, Object> seller = new LinkedHashMap<>();
            seller.put("uuid", info.seller().toString());
            seller.put("name", info.sellerName());
            seller.put("activeCount", info.activeCount());
            sellersMap.put(key, seller);
            sellerTexts.put(key, "&f" + info.sellerName() + " &7(" + info.activeCount() + "件)");
        }
        packet.put("sellers", sellersMap);
        packet.put("sellerTexts", sellerTexts);
        packet.put("maxSellerCount", PSHOP_SELLERS_PAGE_SIZE);
        return packet;
    }

    // ─── 玩家商店公共 API ────────────────────────────────────

    public void createPlayerShopListing(Player player, ItemStack item, double price, String currency, long duration) {
        if (playerShopService == null) {
            player.sendMessage(messages.get("player.pshop-unavailable"));
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        var result = playerShopService.createListing(player, slot, price, currency, duration, "");
        if (!result.success()) {
            player.sendMessage(ChatColor.RED + result.error());
            return;
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().playerShopListed()));
    }

    public void cancelPlayerShopListing(Player player, long listingId) {
        if (playerShopService == null) return;
        if (playerShopService.cancelListing(player, listingId)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().playerShopCancelled()));
        } else {
            player.sendMessage(ChatColor.RED + "下架失败");
        }
    }

    public List<Long> getMyPlayerShopListingIds(Player player) {
        if (playerShopService == null) return List.of();
        var listings = playerShopService.getListingsBySeller(player.getUniqueId(), 0, 100);
        return listings.stream().map(PlayerShopListing::getId).toList();
    }

    public boolean isPlayerShopEnabled() {
        return playerShopService != null;
    }

    private void handleAuctionListPacket(Player player, List<String> data) {
        if(restoreAuctionBrowse.remove(player.getUniqueId()))data=auctionBrowseContext.getOrDefault(player.getUniqueId(),data);
        if (auctionService == null) return;
        // 无论从何处打开本 UI（Screen.open 或 openAuctionUi），都在首次列表请求时签发 token，
        // 保证购买/出价/取消按钮的 enable 条件 var.actionToken != '' 成立
        issueActionToken(player, config.ui().auctionId());
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        String category = data.size() > 2 ? data.get(2) : null;
        if (pendingMyAuctions.remove(player.getUniqueId())) category = "my";
        String keyword = data.size() > 3 ? data.get(3) : null;
        String pendingCategory=pendingAuctionCategories.remove(player.getUniqueId());
        if(pendingCategory!=null)category=pendingCategory;
        auctionBrowseContext.put(player.getUniqueId(),List.of("auction_list",String.valueOf(page),category==null?"all":category,keyword==null?"":keyword,"refresh"));
        String cacheKey = listCacheKey("listings", player, page, category, keyword);
        if (data.size() > 4 && "refresh".equals(data.get(4))) {
            redisCache.invalidateByPrefix("market:listings:" + player.getUniqueId() + ":");
        }
        Map<String, Object> cached = readListCache(cacheKey);
        if (cached != null) {
            sendUiPacket(player, config.ui().auctionId(), "update", cached);
            return;
        }

        int totalCount;
        if ("ending".equals(category) || "participating".equals(category)) {
            totalCount=repository.countBrowseAuction(category,player.getUniqueId(),keyword);
        } else if ("favorites".equals(category)) {
            totalCount=repository.countFavoriteListings(player.getUniqueId(),keyword);
        } else if ("my".equals(category)) {
            totalCount = auctionService.countMy(player.getUniqueId());
        } else if (keyword != null && !keyword.isEmpty()) {
            totalCount = auctionService.countSearch(keyword);
        } else if (category != null && !category.isEmpty() && !"all".equals(category)) {
            totalCount = auctionService.countCategory(category);
        } else {
            totalCount = auctionService.countActive();
        }
        int totalPages = Math.max(1, (totalCount + AUCTION_PAGE_SIZE - 1) / AUCTION_PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        List<AuctionListing> listings;
        if ("ending".equals(category) || "participating".equals(category)) {
            listings=repository.browseAuction(category,player.getUniqueId(),keyword,page*AUCTION_PAGE_SIZE,AUCTION_PAGE_SIZE);
        } else if ("favorites".equals(category)) {
            listings=repository.favoriteListings(player.getUniqueId(),keyword,page*AUCTION_PAGE_SIZE,AUCTION_PAGE_SIZE);
        } else if ("my".equals(category)) {
            listings = auctionService.getMyListings(player.getUniqueId(), page, AUCTION_PAGE_SIZE);
        } else if (keyword != null && !keyword.isEmpty()) {
            listings = auctionService.searchListings(keyword, page, AUCTION_PAGE_SIZE);
        } else if (category != null && !category.isEmpty() && !"all".equals(category)) {
            listings = auctionService.getListingsByCategory(category, page, AUCTION_PAGE_SIZE);
        } else {
            listings = auctionService.getActiveListings(page, AUCTION_PAGE_SIZE);
        }

        Map<String, Object> packet = buildAuctionPacket(player, listings, page, totalPages,
            category != null ? category : "all" );
        packet.put("keyword", keyword == null ? "" : keyword);
        writeListCache(cacheKey, packet);
        sendUiPacket(player, config.ui().auctionId(), "update", packet);
    }

    private String activeAuctionUi(Player player) {
        return packetBridge!=null && packetBridge.isUiOpen(player,config.ui().auctionRoomId())?config.ui().auctionRoomId():config.ui().auctionId();
    }

    private boolean isAuctionUiOpen(Player player) {
        return packetBridge!=null && (packetBridge.isUiOpen(player,config.ui().auctionId()) || packetBridge.isUiOpen(player,config.ui().auctionRoomId()));
    }

    private boolean isSelectedAuctionIntent(Player player,long id) {
        return !packetBridge.isUiOpen(player,config.ui().auctionRoomId()) || (auctionRooms!=null && auctionRooms.selected(player)==id);
    }

    public boolean openAuctionRoom(Player player,long id) {
        if(auctionService==null || auctionRooms==null) {player.sendMessage(messages.get("player.auction-unavailable"));return false;}
        if(id<=0){player.sendMessage(ChatColor.RED+"拍品 ID 必须为正整数");return false;}
        auctionRooms.select(player,id);
        packetBridge.openUi(player,config.ui().auctionRoomId());
        issueActionToken(player,config.ui().auctionRoomId());
        return true;
    }

    public void openAuctionCategory(Player player,String category) {
        pendingAuctionCategories.put(player.getUniqueId(),category);openAuctionUi(player);
    }

    private void refreshAuctionSurface(Player player,List<String> listRequest) {
        if(auctionRooms!=null && packetBridge.isUiOpen(player,config.ui().auctionRoomId()))auctionRooms.request(player);
        else handleAuctionListPacket(player,listRequest);
    }

    private String auctionBidRevision(AuctionListing listing) {
        return listing.getId()+"|"+listing.getCurrentBid()+"|"+listing.getHighestBidder()+"|"+listing.getExpiresAt()+"|"+listing.getStatus();
    }

    private String auctionTime(long time) {return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time));}

    private void sendAuctionRoom(Player player,xuanmo.arcartxsuite.market.auction.AuctionRoomSnapshot snapshot) {
        Map<String,Object> packet=new LinkedHashMap<>();packet.put("packetId",config.ui().packetId());packet.put("found",snapshot!=null);
        if(snapshot==null){packet.put("message","拍品不存在或已被清理，请返回拍卖大厅");sendUiPacket(player,config.ui().auctionRoomId(),"room_snapshot",packet);return;}
        AuctionListing lot=snapshot.listing();boolean seller=lot.getSeller().equals(player.getUniqueId());
        boolean leading=player.getUniqueId().equals(lot.getHighestBidder());boolean active=lot.isActive();
        String status=lot.getStatus()==AuctionListing.ListingStatus.ACTIVE?(active?"正在竞价":"到时等待落槌")
            :lot.getStatus()==AuctionListing.ListingStatus.SOLD?"已落槌成交":lot.getStatus()==AuctionListing.ListingStatus.EXPIRED?"无人出价流拍":"拍品已下架";
        String participation=seller?"你是这件拍品的卖家":leading?"你当前领先":snapshot.myHighestBid()>0?"你的出价已被超越":"你尚未参与竞价";
        if(lot.getStatus()==AuctionListing.ListingStatus.SOLD)participation=seller?"已成交，收入按交易税率结算":leading?"恭喜，你已得标":snapshot.myHighestBid()>0?"本场已结束，你未得标":"本场已结束";
        else if(!active && lot.getStatus()!=AuctionListing.ListingStatus.ACTIVE)participation=seller?"拍品将按交易记录退还":"本场已结束，请查看自己的交付进度";
        ItemDisplay item=serializedItemPreview(lot.getItemData());
        packet.put("id",String.valueOf(lot.getId()));packet.put("name",lot.getItemDisplayName());packet.put("seller",lot.getSellerName());
        packet.put("itemJson",item.json());packet.put("itemAmount",item.amount());packet.put("message",lot.getMessage());
        packet.put("bidRevision",auctionBidRevision(lot));packet.put("status",status);packet.put("participation",participation);
        packet.put("active",active);packet.put("waiting",lot.getStatus()==AuctionListing.ListingStatus.ACTIVE && !active);
        packet.put("isSeller",seller);packet.put("isLeading",leading);packet.put("followed",snapshot.followed());
        packet.put("canBid",active && !seller && lot.getType()!=AuctionListing.ListingType.BUY_NOW && auctionService.minimumBid(lot).doubleValue()<=config.auction().maxPrice());
        packet.put("canCancel",active && seller && (lot.getHighestBidder()==null || config.auction().allowCancelWithBids()));
        packet.put("startingPrice",currencyManager.format(lot.getCurrency(),BigDecimal.valueOf(lot.getStartingBid())));
        packet.put("currentPrice",lot.getHighestBidder()==null?"等待首个有效出价":currencyManager.format(lot.getCurrency(),BigDecimal.valueOf(lot.getCurrentBid())));
        packet.put("minimum",auctionService.minimumBid(lot).toPlainString());packet.put("currency",currencyDisplayName(lot.getCurrency()));
        packet.put("highest",leading?"你（领先）":lot.getHighestBidder()==null?"暂无领先者":nameFromUuid(lot.getHighestBidder()));
        double myHighest=Math.max(snapshot.myHighestBid(),leading?lot.getCurrentBid():0);
        packet.put("myHighest",myHighest>0?currencyManager.format(lot.getCurrency(),BigDecimal.valueOf(myHighest)):"尚无有效出价");
        var bridge=currencyManager.bridge(lot.getCurrency());
        packet.put("myBalance",bridge!=null && bridge.available()?currencyManager.format(lot.getCurrency(),bridge.balance(player)):"货币暂不可用");
        packet.put("myHeld",currencyManager.format(lot.getCurrency(),leading?BigDecimal.valueOf(lot.getCurrentBid()):BigDecimal.ZERO));
        packet.put("remainingSeconds",Math.max(0,(lot.getExpiresAt()-System.currentTimeMillis()+999)/1000));
        packet.put("createdAt",auctionTime(lot.getCreatedAt()));packet.put("deadline",auctionTime(lot.getExpiresAt()));
        packet.put("originalDeadline",auctionTime(lot.getOriginalExpiresAt()));packet.put("extendedSeconds",Math.max(0,(lot.getExpiresAt()-lot.getOriginalExpiresAt())/1000));
        packet.put("bidCount",snapshot.bidCount());packet.put("bidderCount",snapshot.bidderCount());packet.put("watchCount",snapshot.watchCount());
        packet.put("protection",config.auction().endProtectionEnabled()?"末 "+config.auction().endWindowSeconds()+" 秒有效出价可延时；累计上限 "+config.auction().endMaxExtensionSeconds()+" 秒":"本场未启用临近结束延时");
        packet.put("myDelivery",snapshot.myReviewDeliveries()>0?"需管理员核对："+snapshot.myReviewDeliveries()+" 条"
            :snapshot.myPendingDeliveries()>0?"待交付："+snapshot.myPendingDeliveries()+" 条物品或款项"
            :snapshot.myDoneDeliveries()>0?"你的已有交付记录均已完成":"尚无属于你的款项/物品交付记录");
        Map<String,Object> timeline=new LinkedHashMap<>();int index=0;
        if(snapshot.endedAt()>0) {
            Map<String,Object> ending=new LinkedHashMap<>();ending.put("title",status);
            ending.put("detail",auctionTime(snapshot.endedAt())+" · 正式结算记录");ending.put("leading",false);
            timeline.put(String.valueOf(index++),ending);
        }
        String announcement=lot.getHighestBidder()==null?"拍品已上架，等待首个有效出价":"当前领先："+packet.get("highest")+" · "+packet.get("currentPrice");
        for(var bid:snapshot.recentBids()) {
            Map<String,Object> row=new LinkedHashMap<>();boolean current=bid.bidder().equals(lot.getHighestBidder()) && Double.compare(bid.amount(),lot.getCurrentBid())==0;
            row.put("title",bid.bidderName()+" 出价 "+currencyManager.format(lot.getCurrency(),BigDecimal.valueOf(bid.amount())));
            long extension=bid.expiresAt()>bid.previousExpiresAt() && bid.previousExpiresAt()>0?(bid.expiresAt()-bid.previousExpiresAt())/1000:0;
            row.put("detail",new SimpleDateFormat("HH:mm:ss").format(new Date(bid.timestamp()))+(extension>0?" · 触发延时 +"+extension+" 秒":" · 有效出价")+(current?" · 当前领先":""));
            row.put("leading",current);timeline.put(String.valueOf(index++),row);
        }
        if(!snapshot.recentBids().isEmpty()) {
            var latest=snapshot.recentBids().get(0);packet.put("lastBidId",String.valueOf(latest.id()));
            announcement=latest.bidderName()+" 最新有效出价 "+currencyManager.format(lot.getCurrency(),BigDecimal.valueOf(latest.amount()));
            if(latest.expiresAt()>latest.previousExpiresAt() && latest.previousExpiresAt()>0)announcement+="，截止时间已顺延";
        } else packet.put("lastBidId","");
        Map<String,Object> opening=new LinkedHashMap<>();opening.put("title",lot.getSellerName()+" 上架拍品");
        opening.put("detail",auctionTime(lot.getCreatedAt())+" · 起拍价 "+packet.get("startingPrice"));opening.put("leading",false);
        timeline.put(String.valueOf(index++),opening);
        if(!active)announcement=status+(lot.getStatus()==AuctionListing.ListingStatus.SOLD?" · "+packet.get("highest")+" 得标，成交价 "+packet.get("currentPrice"):"");
        packet.put("announcement",announcement);packet.put("timeline",timeline);packet.put("timelineCount",index);
        packet.put("lore",java.util.Map.of());packet.put("loreCount",0);
        try {
            ItemStack stack=itemSerializer.deserialize(lot.getItemData());
            if(stack!=null && stack.hasItemMeta() && stack.getItemMeta().hasLore()) {
                Map<String,String> lore=new LinkedHashMap<>();int line=0;
                for(String text:stack.getItemMeta().getLore()) {if(line>=48)break;lore.put(String.valueOf(line++),text);}
                packet.put("lore",lore);packet.put("loreCount",line);
            }
        }catch(RuntimeException exception){logger.fine("拍品Lore暂不可用: "+lot.getId());}
        sendUiPacket(player,config.ui().auctionRoomId(),"room_snapshot",packet);
    }

    private void handleAuctionQuote(Player player,List<String> data) {
        if(auctionService==null || data.size()<3 || !isAuctionUiOpen(player))return;
        long id=parseLongSafe(data.get(1),0);
        if(!isSelectedAuctionIntent(player,id))return;
        var listing=repository.getListing(id);
        Map<String,Object> packet=new LinkedHashMap<>(); packet.put("packetId",config.ui().packetId());packet.put("id",String.valueOf(id));
        boolean active=listing!=null && listing.isActive() && listing.getType()!=AuctionListing.ListingType.BUY_NOW
            && !listing.getSeller().equals(player.getUniqueId());
        packet.put("available",active);
        packet.put("revision",listing==null?"":auctionBidRevision(listing));
        if(active) {
            BigDecimal minimum=auctionService.minimumBid(listing);
            double requested=parseDoubleSafe(data.get(2),Double.NaN);
            BigDecimal amount=Double.isFinite(requested) && requested==0?minimum
                :Double.isFinite(requested)?BigDecimal.valueOf(requested):BigDecimal.ZERO;
            if("step1".equals(data.get(2)) || "step2".equals(data.get(2))) {
                amount=minimum;
                int steps="step1".equals(data.get(2))?1:2;
                for(int step=0;step<steps;step++)amount=amount.add(amount.multiply(BigDecimal.valueOf(config.auction().minBidIncrementRatio()))
                    .max(BigDecimal.valueOf(config.auction().minBidIncrementAbsolute()))).setScale(auctionService.currencyScale(listing.getCurrency()),java.math.RoundingMode.CEILING);
            }
            boolean own=player.getUniqueId().equals(listing.getHighestBidder());
            BigDecimal held=own?BigDecimal.valueOf(listing.getCurrentBid()):BigDecimal.ZERO;
            BigDecimal debit=amount.subtract(held).max(BigDecimal.ZERO);
            var bridge=currencyManager.bridge(listing.getCurrency());
            boolean allowed=amount.compareTo(minimum)>=0 && amount.compareTo(BigDecimal.valueOf(config.auction().maxPrice()))<=0
                && amount.stripTrailingZeros().scale()<=auctionService.currencyScale(listing.getCurrency())
                && bridge!=null && bridge.available() && bridge.balance(player).compareTo(debit)>=0;
            packet.put("canSubmit",allowed);packet.put("amount",amount.toPlainString());packet.put("minimum",minimum.toPlainString());
            packet.put("current",listing.getHighestBidder()==null?"暂无出价":currencyManager.format(listing.getCurrency(),BigDecimal.valueOf(listing.getCurrentBid())));
            packet.put("highest",own?"你正在领先":listing.getHighestBidder()==null?"暂无领先者":nameFromUuid(listing.getHighestBidder()));
            packet.put("debit",currencyManager.format(listing.getCurrency(),debit));packet.put("held",currencyManager.format(listing.getCurrency(),held));
            packet.put("deadline",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(listing.getExpiresAt())));
            packet.put("protection",config.auction().endProtectionEnabled()?"临近结束有效出价可延时；累计最多 "+config.auction().endMaxExtensionSeconds()+" 秒":"未启用临近结束延时");
            packet.put("reason",allowed?"确认后才扣款；报价不会自动提交出价":"金额低于最低出价、精度不符、超出上限或余额不足，请修改后重新报价");
        } else packet.put("reason","该拍卖已结束、已下架或不能对自己的商品出价");
        sendUiPacket(player,activeAuctionUi(player),"auction_quote",packet);
    }

    private void handleAuctionBidPacket(Player player, List<String> data) {
        if (auctionService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if(!isSelectedAuctionIntent(player,listingId))return;
        double amount = data.size() > 2 ? parseDoubleSafe(data.get(2), 0) : 0;
        if (listingId <= 0 || amount <= 0) return;
        var result = auctionService.placeBid(player, listingId, amount);
        if (result.success()) {
            rotateToken(player, activeAuctionUi(player));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().auctionBidPlaced().replace("{amount}", String.valueOf(result.amount()))));
        } else {
            player.sendMessage(ChatColor.RED + result.error());
        }
        if(auctionRooms!=null)auctionRooms.changed(listingId);
        refreshAuctionSurface(player, List.of("auction_list", data.size() > 4 ? data.get(4) : "0",
            data.size() > 5 ? data.get(5) : "all", data.size() > 6 ? data.get(6) : ""));
    }

    private void handleAuctionCancelPacket(Player player, List<String> data) {
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if(!isSelectedAuctionIntent(player,listingId))return;
        if (listingId <= 0) return;
        cancelListing(player, listingId);
        // 取消后刷新当前页列表
        String page = data.size() > 3 && data.get(3) != null ? data.get(3) : "0";
        String category = data.size() > 4 && data.get(4) != null ? data.get(4) : "all";
        String search = data.size() > 5 && data.get(5) != null ? data.get(5) : "";
        if(auctionRooms!=null)auctionRooms.changed(listingId);
        refreshAuctionSurface(player, List.of("auction_list", page, category, search));
    }

    private void handleAuctionFavoritePacket(Player player, List<String> data) {
        if (auctionService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if(!isSelectedAuctionIntent(player,listingId))return;
        if (listingId <= 0) return;
        if(repository.getListing(listingId)==null) {player.sendMessage(ChatColor.YELLOW+"该拍卖已不存在");return;}
        if(!repository.isFavorite(player.getUniqueId(),listingId) && repository.getFavorites(player.getUniqueId()).size()>=100) {
            player.sendMessage(ChatColor.YELLOW+"最多关注100件商品，请先取消部分关注");return;
        }
        boolean following=auctionService.toggleFavorite(player.getUniqueId(), listingId);
        player.sendMessage(following?ChatColor.GREEN+"已关注，可在我的关注中查看；结束提醒随通知配置生效。":ChatColor.GRAY+"已取消关注");
        rotateToken(player,activeAuctionUi(player));
        if(auctionRooms!=null)auctionRooms.changed(listingId);
        redisCache.invalidateByPrefix("market:listings:");
        // 保留玩家当前分类与搜索条件（由客户端随包附带），避免收藏后列表跳回"全部"
        String currentCategory = data.size() > 2 && data.get(2) != null ? data.get(2) : "all";
        String currentSearch = data.size() > 3 && data.get(3) != null ? data.get(3) : "";
        refreshAuctionSurface(player, List.of("auction_list", data.size() > 4 ? data.get(4) : "0", currentCategory, currentSearch));
    }

    private void handleShopListPacket(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1) : "";
        if (shopId.isBlank()) {
            String pending = pendingShopSelections.remove(player.getUniqueId());
            if (pending != null) shopId = pending;
        }
        Map<String, Object> packet = buildShopPacket(player, shopId);
        sendUiPacket(player, config.ui().shopId(), "update", packet);
    }

    private void handleShopBuyPacket(Player player, List<String> data) {
        String shopId = data.size() > 1 ? data.get(1) : "";
        String itemId = data.size() > 2 ? data.get(2) : "";
        int amount = data.size() > 3 ? parseIntSafe(data.get(3), 1) : 1;
        if (shopId.isEmpty() || itemId.isEmpty()) return;
        BigDecimal expectedPrice = null;
        if (data.size() > 5) {
            try { expectedPrice = new BigDecimal(data.get(5)); }
            catch (NumberFormatException exception) {
                player.sendMessage(ChatColor.RED + "确认金额无效，请刷新商店");
                return;
            }
        }
        buyFromShop(player, shopId, itemId, amount, expectedPrice);
        // 购买后刷新商店 UI
        if (shopService != null) {
            Map<String, Object> packet = buildShopPacket(player, shopId);
            sendUiPacket(player, config.ui().shopId(), "update", packet);
        }
    }

    private void handleShopQuote(Player player, List<String> data) {
        if (shopService == null || !packetBridge.isUiOpen(player, config.ui().shopId()) || data.size() < 4) return;
        String shop = data.get(1), item = data.get(2);
        var quote = shopService.quote(player, shop, item);
        Map<String,Object> packet = new LinkedHashMap<>();
        packet.put("packetId",config.ui().packetId()); packet.put("shopId",shop); packet.put("itemId",item);
        packet.put("available",quote != null);
        if (quote != null) {
            int quantity = Math.max(0,Math.min(quote.maximum(),Math.max(1,parseIntSafe(data.get(3),1))));
            packet.put("quantity",quantity); packet.put("maximum",quote.maximum());
            packet.put("unitPrice",quote.unitPrice().toPlainString());
            packet.put("basePrice",currencyManager.format(quote.currency(),quote.basePrice()));
            packet.put("total",currencyManager.format(quote.currency(),quote.unitPrice().multiply(BigDecimal.valueOf(quantity))));
            packet.put("stock",quote.stock()); packet.put("limit",quote.limit()); packet.put("capacity",quote.capacity());
            var definition=shopService.getEffectiveShop(shop).items().get(item);
            packet.put("stockDisplay",formatShopStock(player,shop,item,definition));
            packet.put("limitDisplay",definition.limitPerPlayer()>0?String.valueOf(quote.limit()):"不限");
        }
        sendUiPacket(player,config.ui().shopId(),"shop_quote",packet);
    }

    private void handleRecycleAll(Player player, List<String> data) {
        if (!config.recycle().batchRecycleEnabled()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().batchRecycleDisabled()));
            return;
        }
        if (data.size() <= 3 || !inventoryFingerprint(player).equals(data.get(2)) || !recycleProtectionMatches(player,data.get(3))) {
            player.sendMessage(ChatColor.YELLOW + "背包或回收保护已变化，请重新预览并确认回收");
            handleRecyclePreviewPacket(player);
            return;
        }
        recycleBatch(player);
        handleRecyclePreviewPacket(player);
    }

    private void handleRecycleSinglePacket(Player player, List<String> data) {
        if (recycleService == null) return;
        int slot = data.size() > 1 ? parseIntSafe(data.get(1), -1) : -1;
        // 仅允许主背包 0-35，禁止回收装备栏(36-40)
        if (slot < 0 || slot >= 36) return;
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) return;
        if (data.size() <= 4 || !itemFingerprint(item).equals(data.get(3)) || !recycleProtectionMatches(player,data.get(4))) {
            player.sendMessage(ChatColor.YELLOW + "槽位物品或回收保护已变化，请重新选择回收物品");
            handleRecyclePreviewPacket(player);
            return;
        }
        try {
            var result = recycleService.recycleSlot(player, slot);
            if (result.success()) {
                rotateToken(player, config.ui().recycleId());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.messages().recycleSuccess().replace("{amount}",
                        formatRecycleEarnings(result.earningsByCurrency()))));
            } else {
                player.sendMessage(ChatColor.RED + result.error());
            }
        } catch(RuntimeException exception) {
            logger.warning("[MarketRecycle] 单件回收需核对 player="+player.getUniqueId()+": "+exception.getMessage());
            player.sendMessage(ChatColor.RED+"回收处理中断，请联系管理员通过交易管理核对该物品及收益记录。");
        }
        // 回收后刷新 UI
        handleRecyclePreviewPacket(player);
    }

    private void handleRecyclePreviewPacket(Player player) {
        Map<String, Object> packet = buildRecyclePacket(player);
        sendUiPacket(player, config.ui().recycleId(), "update", packet);
    }
    private boolean recycleProtectionMatches(Player player,String version){
        if(management==null)return false;
        var preferences=management.protection().cached(player);
        if(preferences==null || !String.valueOf(preferences.version()).equals(version))return false;
        return management.protection().currentVersion(player);
    }

    private void handleHistoryListPacket(Player player, List<String> data) {
        if (repository == null) return;
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        Map<String, Object> packet = buildHistoryPacket(player, page);
        sendUiPacket(player, config.ui().historyId(), "update", packet);
    }

    // ─── 系统商店编辑（管理员）────────────────────────────

    private void handleShopEditList(Player player) {
        if (shopService == null) return;
        // 打开/刷新列表时轮换 token，保证后续保存操作携带最新 token
        issueActionToken(player, shopEditUiId());
        sendUiPacket(player, shopEditUiId(), "edit_list", buildShopEditListPacket(player));
        String pending = pendingShopSelections.remove(player.getUniqueId());
        if (pending != null && shopService.getShop(pending) != null) {
            sendUiPacket(player, shopEditUiId(), "edit_shop", buildShopEditShopPacket(player, pending));
        }
    }

    private void handleShopEditShop(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1) : "";
        sendUiPacket(player, shopEditUiId(), "edit_shop", buildShopEditShopPacket(player, shopId));
    }

    private void handleShopEditItem(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1) : "";
        String key = data.size() > 2 ? data.get(2) : "";
        sendUiPacket(player, shopEditUiId(), "edit_item", buildShopEditItemPacket(player, shopId, key));
    }

    /**
     * 背包槽位选物：只接收 raw slot(9-44)，服务端读真实背包，不信任客户端物品内容。
     * target: shop_icon（商店图标）/ item_icon（商品图标）/ item_def（商品物品定义）
     */
    private void handleShopEditPick(Player player, List<String> data) {
        int rawSlot = data.size() > 1 ? parseIntSafe(data.get(1), -1) : -1;
        if (rawSlot < 9 || rawSlot > 44) return;
        String target = data.size() > 2 ? data.get(2) : "";
        if (!target.equals("shop_icon") && !target.equals("item_icon") && !target.equals("item_def")) return;
        ItemStack item = player.getInventory().getItem(toBukkitBackpackSlot(rawSlot));
        if (item == null || item.getType().isAir()) {
            sendEditResult(player, false, "该槽位已没有物品，请重新选择", "pick");
            return;
        }
        ItemStack single = item.clone();
        single.setAmount(1);
        String json = itemToJson(single);
        if (json.isBlank()) {
            sendEditResult(player, false, "物品预览生成失败，请检查物品桥接后重试", "pick");
            return;
        }

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("target", target);
        packet.put("itemJson", json);
        packet.put("name", itemDisplayName(item));

        String source = "minecraft";
        String id = item.getType().name();
        if ("item_def".equals(target) && itemSourceRegistry != null) {
            try {
                if (itemSourceRegistry.mmoBridgeAvailable() && itemSourceRegistry.isMmoItem(item)) {
                    source = "mmoitems";
                    id = nullToEmpty(itemSourceRegistry.mmoItemId(item));
                } else if (itemSourceRegistry.mythicBridgeAvailable() && itemSourceRegistry.isMythicItem(item)) {
                    source = "mythic";
                    id = nullToEmpty(itemSourceRegistry.mythicItemId(item));
                } else if (itemSourceRegistry.neigeBridgeAvailable() && itemSourceRegistry.isNeigeItem(item)) {
                    source = "neige";
                    id = nullToEmpty(itemSourceRegistry.neigeItemId(item));
                } else if (itemSourceRegistry.overtureBridgeAvailable() && itemSourceRegistry.isOvertureItem(item)) {
                    source = "overture";
                    id = nullToEmpty(itemSourceRegistry.overtureItemId(item));
                }
            } catch (RuntimeException ignored) {
                // 外部物品桥异常时按原版物品处理
            }
            if (id.isBlank()) {
                source = "minecraft";
                id = item.getType().name();
            }
            packet.put("source", source);
            packet.put("id", id);
            String nbt = "";
            if ("minecraft".equals(source)) {
                String tag = snbtTopLevelValue(json, "tag");
                if (tag != null) nbt = tag;
            }
            packet.put("nbt", nbt);
        }
        sendUiPacket(player, shopEditUiId(), "edit_pick", packet);
    }

    private void handleShopEditShopSave(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1).trim() : "";
        boolean create = "1".equals(data.size() > 2 ? data.get(2) : "");
        Map<String, Object> form = parseJsonMap(data.size() > 3 ? data.get(3) : "");
        if (form == null) {
            sendEditResult(player, false, "表单数据解析失败", "shop_save");
            return;
        }
        String displayName = capStr(jsonString(form, "displayName"), 128);
        String permission = capStr(jsonString(form, "permission"), 128);
        List<String> tags = splitCsv(jsonString(form, "tags"), 32, 32);
        ShopService.IconEdit icon = parseIconEdit(form);
        String error = shopService.saveShop(shopId, create,
            new ShopService.ShopEdit(displayName, permission, tags, icon), jsonString(form, "revision"));
        sendEditResult(player, error == null, error == null ? "商店已保存" : error, "shop_save");
        if (error == null) {
            rotateToken(player, shopEditUiId());
            sendUiPacket(player, shopEditUiId(), "edit_list", buildShopEditListPacket(player));
            sendUiPacket(player, shopEditUiId(), "edit_shop", buildShopEditShopPacket(player, shopId));
        }
    }

    private void handleShopEditPreview(Player player, List<String> data) {
        if (shopService == null || data.size() < 4) return;
        String source = normalizeItemSource(data.get(1));
        String itemId = capStr(data.get(2), 128).trim();
        String nbt = capStr(data.get(3), 4096).trim();
        ItemStack item = xuanmo.arcartxsuite.market.shop.ShopItemFactory.create(itemSourceRegistry,
            new ShopService.ItemDefinition(source, itemId, nbt.isBlank() ? null : nbt), 1, player, logger);
        if (item == null) {
            sendEditResult(player, false, "物品生成失败，请检查来源、ID 和 NBT", "preview");
            return;
        }
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("itemJson", itemToJson(item));
        sendUiPacket(player, shopEditUiId(), "edit_preview", packet);
    }

    private void handleShopEditShopDelete(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1).trim() : "";
        String error = shopService.deleteShop(shopId, data.size() > 3 ? data.get(3) : "");
        sendEditResult(player, error == null, error == null ? "商店已删除" : error, "shop_delete");
        if (error == null) {
            rotateToken(player, shopEditUiId());
            sendUiPacket(player, shopEditUiId(), "edit_list", buildShopEditListPacket(player));
            Map<String, Object> cleared = new LinkedHashMap<>();
            cleared.put("packetId", config.ui().packetId());
            cleared.put("found", false);
            cleared.put("shopId", shopId);
            sendUiPacket(player, shopEditUiId(), "edit_shop", cleared);
        }
    }

    private void handleShopEditItemSave(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1).trim() : "";
        boolean create = "1".equals(data.size() > 2 ? data.get(2) : "");
        Map<String, Object> form = parseJsonMap(data.size() > 3 ? data.get(3) : "");
        if (form == null) {
            sendEditResult(player, false, "表单数据解析失败", "item_save");
            return;
        }
        String key = capStr(jsonString(form, "key"), 64).trim();
        String type = "command".equalsIgnoreCase(jsonString(form, "type")) ? "command" : "item";
        String displayName = capStr(jsonString(form, "displayName"), 128);
        double buyPrice = parseDoubleSafe(jsonString(form, "buyPrice"), Double.NaN);
        double sellPrice = parseDoubleSafe(jsonString(form, "sellPrice"), Double.NaN);
        if (!Double.isFinite(buyPrice) || !Double.isFinite(sellPrice) || buyPrice < 0 || sellPrice < 0
            || buyPrice > 1_000_000_000 || sellPrice > 1_000_000_000) {
            sendEditResult(player, false, "价格必须为 0 到 1000000000 之间的有效数字", "item_save");
            return;
        }
        String currency = capStr(jsonString(form, "currency"), 64);
        String stockMode = normalizeStockMode(jsonString(form, "stockMode"));
        if ("unlimited".equals(stockMode) && "true".equals(jsonString(form,"applyStock"))) {
            sendEditResult(player,false,"无限库存无需补满，请取消保存后应用库存选项","item_save"); return;
        }
        int stockAmount = clampInt(parseIntSafe(jsonString(form, "stockAmount"), 0), 0, 1_000_000);
        int limitPerPlayer = clampInt(parseIntSafe(jsonString(form, "limitPerPlayer"), 0), 0, 1_000_000);
        if (!jsonString(form, "stockAmount").matches("[0-9]{1,7}")
            || !jsonString(form, "limitPerPlayer").matches("[0-9]{1,7}")
            || parseLongSafe(jsonString(form, "stockAmount"), -1) > 1_000_000
            || parseLongSafe(jsonString(form, "limitPerPlayer"), -1) > 1_000_000) {
            sendEditResult(player, false, "库存和限购数量必须为 0 到 1000000 之间的整数", "item_save");
            return;
        }
        if (!"unlimited".equals(stockMode) && stockAmount == 0) {
            sendEditResult(player, false, "有限库存必须大于 0；不限制库存请选无限模式", "item_save");
            return;
        }
        String effectiveCurrency = currency.isBlank() ? config.shop().defaultCurrency() : currency;
        var currencyBridge = currencyManager.bridge(effectiveCurrency);
        if (currencyBridge == null || !currencyBridge.available()) {
            sendEditResult(player, false, "货币不可用: " + effectiveCurrency, "item_save");
            return;
        }
        String limitReset = normalizeLimitReset(jsonString(form, "limitReset"));
        long restockSeconds = parseLongSafe(jsonString(form,"restockInterval"),0);
        if (restockSeconds < 0 || restockSeconds > 31536000L) {
            sendEditResult(player,false,"补货间隔须为 0 到 31536000 秒；0 表示关闭周期补货","item_save");
            return;
        }
        List<String> conditionLines = splitLines(jsonString(form, "conditions"), 64, 512);
        List<String> commands = splitLines(jsonString(form, "commands"), 64, 256);
        Map<String, Double> discount = parseDiscounts(jsonString(form, "discounts"));
        for (String field : List.of("conditions", "commands", "discounts")) {
            String[] lines = jsonString(form, field).split("\\R");
            int maximumLines = "discounts".equals(field) ? 32 : 64;
            int maximumLength = "conditions".equals(field) ? 512 : 256;
            if (lines.length > maximumLines || java.util.Arrays.stream(lines).anyMatch(line -> line.length() > maximumLength)) {
                sendEditResult(player, false, field + " 内容过长，请减少行数或单行长度", "item_save");
                return;
            }
        }
        for (String line : jsonString(form, "discounts").split("\\R")) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('=');
            double ratio = separator < 0 ? Double.NaN : parseDoubleSafe(line.substring(separator + 1).trim(), Double.NaN);
            if (separator <= 0 || line.substring(0, separator).trim().isEmpty()
                || !Double.isFinite(ratio) || ratio < 0 || ratio > 100) {
                sendEditResult(player, false, "折扣格式应为 权限=倍率，倍率范围为 0 到 100", "item_save");
                return;
            }
        }
        String source = normalizeItemSource(jsonString(form, "source"));
        String itemId = capStr(jsonString(form, "itemId"), 128).trim();
        String nbt = capStr(jsonString(form, "nbt"), 4096).trim();

        if ("item".equals(type)) {
            if (itemId.isBlank()) {
                sendEditResult(player, false, "物品ID不能为空", "item_save");
                return;
            }
            if ("minecraft".equals(source)) {
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(itemId);
                if (material == null) {
                    material = org.bukkit.Material.matchMaterial(itemId.toUpperCase(java.util.Locale.ROOT));
                }
                if (material == null || material.isAir() || !material.isItem()) {
                    sendEditResult(player, false, "无效的原版物品ID: " + itemId, "item_save");
                    return;
                }
                itemId = material.name();
            }
            if (!nbt.isBlank() && !(nbt.startsWith("{") && nbt.endsWith("}"))) {
                sendEditResult(player, false, "NBT 必须是 {…} 形式的 SNBT 对象", "item_save");
                return;
            }
        }
        if ("command".equals(type) && commands.isEmpty()) {
            sendEditResult(player, false, "指令商品至少填写一条奖励指令", "item_save");
            return;
        }

        ShopService.IconEdit icon = parseIconEdit(form);
        var edit = new ShopService.ItemEdit(key, type, displayName, buyPrice, sellPrice, currency,
            stockMode, stockAmount, limitPerPlayer, limitReset,
            conditionLines, commands, discount,
            source, itemId, nbt, icon, restockSeconds);
        String error = shopService.saveShopItem(shopId, create, edit, jsonString(form, "revision"));
        sendEditResult(player, error == null, error == null ? "商品已保存" : error, "item_save");
        if (error == null) {
            if ("true".equals(jsonString(form,"applyStock"))) shopService.manageStock(shopId,key,"reset",0,null);
            rotateToken(player, shopEditUiId());
            sendUiPacket(player, shopEditUiId(), "edit_shop", buildShopEditShopPacket(player, shopId));
            sendUiPacket(player, shopEditUiId(), "edit_item", buildShopEditItemPacket(player, shopId, key));
        }
    }

    private void handleShopEditItemDelete(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1).trim() : "";
        String key = data.size() > 2 ? data.get(2).trim() : "";
        String error = shopService.deleteShopItem(shopId, key, data.size() > 4 ? data.get(4) : "");
        sendEditResult(player, error == null, error == null ? "商品已删除" : error, "item_delete");
        if (error == null) {
            rotateToken(player, shopEditUiId());
            sendUiPacket(player, shopEditUiId(), "edit_shop", buildShopEditShopPacket(player, shopId));
            Map<String, Object> cleared = new LinkedHashMap<>();
            cleared.put("packetId", config.ui().packetId());
            cleared.put("found", false);
            cleared.put("shopId", shopId);
            cleared.put("key", key);
            sendUiPacket(player, shopEditUiId(), "edit_item", cleared);
        }
    }

    private Map<String, Object> buildShopEditListPacket(Player player) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        Map<String, Object> shops = new LinkedHashMap<>();
        Map<String, Object> shopTexts = new LinkedHashMap<>();
        int idx = 0;
        for (var entry : shopService.getShops().entrySet()) {
            ShopService.ShopDefinition shop = entry.getValue();
            String key = Integer.toString(idx);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", shop.id());
            row.put("name", shop.displayName());
            row.put("itemCount", shop.items().size());
            row.put("permission", shop.permission());
            shops.put(key, row);
            shopTexts.put(key, shop.displayName());
            packet.put("shopIconJson" + key, iconResolver.resolveItemJson(player, shop.icon()));
            idx++;
        }
        packet.put("shops", shops);
        packet.put("shopTexts", shopTexts);
        packet.put("maxShopCount", idx);
        packet.put("defaultCurrency", config.shop().defaultCurrency());
        return packet;
    }

    private void handleShopEditStock(Player player, List<String> data) {
        if (shopService == null || data.size() < 8) return;
        String shop=data.get(1), item=data.get(2), action=data.get(3);
        var snapshot=shopService.editableShop(shop);
        if(snapshot==null || !snapshot.revision().equals(data.get(7))) {
            sendEditResult(player,false,"商店配置已变化，请重新加载后管理库存","stock"); return;
        }
        UUID target=null;
        if(!data.get(5).isBlank()) {
            try {target=UUID.fromString(data.get(5));}
            catch(IllegalArgumentException exception) {sendEditResult(player,false,"目标玩家必须填写 UUID，留空表示全部玩家","stock");return;}
        }
        final UUID stockTarget=target;
        int amount=parseIntSafe(data.get(4),-1);
        String error=ledger.trade("STOCK_ADMIN",player.getUniqueId(),shop+"/"+item+":"+action,
            () -> shopService.manageStock(shop,item,action,amount,stockTarget), result -> result==null);
        if(error==null) rotateToken(player,shopEditUiId());
        sendEditResult(player,error==null,error==null?"库存/限购操作已完成":error,"stock");
        var definition=snapshot.shop().items().get(item);
        if(definition!=null) {
            Map<String,Object> packet=new LinkedHashMap<>(); packet.put("packetId",config.ui().packetId());
            packet.put("summary",stockTarget!=null && "per-player".equals(definition.stockMode())
                ? "玩家 "+stockTarget+" 剩余 "+repository.getPlayerShopStock(stockTarget,shop,item,definition.stockAmount())+"/"+definition.stockAmount()
                : repository.shopStockSummary(shop,item,definition.stockMode(),definition.stockAmount()));
            sendUiPacket(player,shopEditUiId(),"stock_summary",packet);
        }
    }

    /** 商店详情（编辑用，读取静态定义，不含动态商品）。 */
    private Map<String, Object> buildShopEditShopPacket(Player player, String shopId) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        ShopService.EditableShop snapshot = shopService.editableShop(shopId);
        ShopService.ShopDefinition shop = snapshot == null ? null : snapshot.shop();
        packet.put("revision", snapshot == null ? "" : snapshot.revision());
        packet.put("found", shop != null);
        packet.put("shopId", shopId);
        if (shop == null) return packet;
        packet.put("displayName", shop.displayName());
        packet.put("permission", shop.permission());
        packet.put("tags", String.join(",", shop.tags()));
        packet.put("iconPreview", iconResolver.resolveItemJson(player, shop.icon()));
        Map<String, Object> items = new LinkedHashMap<>();
        int idx = 0;
        for (var entry : shop.items().entrySet()) {
            ShopService.ShopItem item = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", entry.getKey());
            row.put("name", item.displayName());
            row.put("type", item.type() == ShopService.ShopItemType.COMMAND ? "command" : "item");
            row.put("price", formatPrice(item.buyPrice()));
            row.put("currencyName", currencyDisplayName(item.currency()));
            row.put("stock", formatEditStock(item));
            row.put("limit", item.limitPerPlayer() > 0 ? String.valueOf(item.limitPerPlayer()) : "");
            items.put(Integer.toString(idx), row);
            packet.put("editItemJson" + idx, shopItemJson(player, item));
            idx++;
        }
        packet.put("items", items);
        packet.put("maxItemCount", idx);
        return packet;
    }

    /** 商品详情（编辑用）。 */
    private Map<String, Object> buildShopEditItemPacket(Player player, String shopId, String key) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        ShopService.EditableShop snapshot = shopService.editableShop(shopId);
        ShopService.ShopDefinition shop = snapshot == null ? null : snapshot.shop();
        packet.put("revision", snapshot == null ? "" : snapshot.revision());
        ShopService.ShopItem item = shop == null ? null : shop.items().get(key);
        packet.put("found", item != null);
        packet.put("shopId", shopId);
        packet.put("key", key);
        if (item == null) return packet;
        packet.put("type", item.type() == ShopService.ShopItemType.COMMAND ? "command" : "item");
        packet.put("displayName", item.displayName());
        packet.put("buyPrice", String.valueOf(item.buyPrice()));
        packet.put("sellPrice", String.valueOf(item.sellPrice()));
        packet.put("currency", item.currency());
        packet.put("stockMode", item.stockMode());
        packet.put("stockAmount", String.valueOf(item.stockAmount()));
        packet.put("stockSummary",repository.shopStockSummary(shopId,key,item.stockMode(),item.stockAmount()));
        packet.put("restockInterval",String.valueOf(shopService.getRestockInterval(shopId,key)));
        packet.put("limitPerPlayer", String.valueOf(item.limitPerPlayer()));
        packet.put("limitReset", item.limitReset());
        // 条件回显为原始表达式行（每行一条），UI 多行编辑后原样写回
        List<xuanmo.arcartxsuite.api.condition.ScriptCondition> conditions = item.conditions();
        packet.put("conditions", conditions == null ? ""
            : conditions.stream()
                .map(xuanmo.arcartxsuite.api.condition.ScriptCondition::raw)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("\n")));
        packet.put("commands", String.join("\n", item.rewardCommands()));
        StringBuilder discounts = new StringBuilder();
        for (var entry : item.discount().entrySet()) {
            if (discounts.length() > 0) discounts.append('\n');
            discounts.append(entry.getKey()).append('=').append(entry.getValue());
        }
        packet.put("discounts", discounts.toString());
        ShopService.ItemDefinition def = item.item();
        packet.put("source", def != null ? def.source() : "minecraft");
        packet.put("itemId", def != null ? def.id() : "");
        packet.put("nbt", def != null && def.nbt() != null ? def.nbt() : "");
        packet.put("iconPreview", iconResolver.resolveItemJson(player, item.icon()));
        packet.put("itemPreview", shopItemJson(player, item));
        packet.put("defaultCurrency", config.shop().defaultCurrency());
        return packet;
    }

    private static String formatEditStock(ShopService.ShopItem item) {
        return switch (item.stockMode()) {
            case "global" -> "全服 " + item.stockAmount();
            case "per-player" -> "每人 " + item.stockAmount();
            default -> "无限";
        };
    }

    private void sendEditResult(Player player, boolean success, String message, String op) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("success", success);
        packet.put("message", message);
        packet.put("op", op);
        sendUiPacket(player, shopEditUiId(), "edit_result", packet);
    }

    /**
     * 从 itemToJson 产生的顶层 JSON 对象中提取指定字段的 {...} 值（如 "tag"），
     * 按花括号配平扫描，字符串内转义已处理。提取不到返回 null。
     */
    private static @Nullable String snbtTopLevelValue(String json, String key) {
        if (json == null || json.isEmpty()) return null;
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i += needle.length();
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != ':') return null;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '{') return null;
        int start = i;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
        }
        return null;
    }

    // ─── 商店编辑表单解析 ─────────────────────────────────

    private static @Nullable Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return xuanmo.arcartxsuite.api.util.SimpleJsonParser.parse(json);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String jsonString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String capStr(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static List<String> splitCsv(String s, int maxEntries, int maxLen) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new java.util.ArrayList<>();
        for (String part : s.split("[,，]")) {
            String t = part.trim();
            if (!t.isEmpty() && out.size() < maxEntries) out.add(capStr(t, maxLen));
        }
        return out;
    }

    private static List<String> splitLines(String s, int maxLines, int maxLen) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new java.util.ArrayList<>();
        for (String line : s.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty() && out.size() < maxLines) out.add(capStr(t, maxLen));
        }
        return out;
    }

    private static Map<String, Double> parseDiscounts(String s) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (s == null || s.isBlank()) return map;
        for (String line : s.split("\\R")) {
            String t = line.trim();
            int eq = t.indexOf('=');
            if (eq <= 0) continue;
            String perm = capStr(t.substring(0, eq).trim(), 128);
            if (perm.isEmpty()) continue;
            double ratio = parseDoubleSafe(t.substring(eq + 1).trim(), -1);
            if (ratio < 0 || ratio > 100 || Double.isNaN(ratio)) continue;
            map.put(perm, ratio);
            if (map.size() >= 32) break;
        }
        return map;
    }

    private static String normalizeStockMode(String s) {
        return switch (s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "global" -> "global";
            case "per-player", "perplayer", "player" -> "per-player";
            default -> "unlimited";
        };
    }

    private static String normalizeLimitReset(String s) {
        return switch (s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "daily" -> "daily";
            case "weekly" -> "weekly";
            case "monthly" -> "monthly";
            default -> "never";
        };
    }

    private static String normalizeItemSource(String s) {
        return switch (s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "mythic", "mythicmobs" -> "mythic";
            case "neige", "neigeitems" -> "neige";
            case "overture" -> "overture";
            case "mmoitems", "mmo" -> "mmoitems";
            default -> "minecraft";
        };
    }

    private static ShopService.IconEdit parseIconEdit(Map<String, Object> form) {
        return switch (jsonString(form, "iconMode")) {
            case "none" -> ShopService.IconEdit.none();
            case "material" -> ShopService.IconEdit.ofMaterial(capStr(jsonString(form, "iconMaterial"), 128));
            case "json" -> ShopService.IconEdit.ofJson(capStr(jsonString(form, "iconJson"), 8192));
            default -> ShopService.IconEdit.keep();
        };
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    // ─── UI 数据包构建 ──────────────────────────────────────

    private void sendUiPacket(Player player, String uiId, String handler, Map<String, Object> packet) {
        if (packetBridge == null) return;
        if (requiresUiToken(uiId)) {
            packet.put("action_token", tokenMap(uiId).getOrDefault(player.getUniqueId(), ""));
        }
        packetBridge.sendPacket(player, uiId, handler, packet);
    }

    private String listCacheKey(String kind, Player player, int page, String first, String second) {
        String query = page + "\n" + (first == null ? "" : first) + "\n" + (second == null ? "" : second);
        return "market:" + kind + ":" + player.getUniqueId() + ":"
            + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(query.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private @Nullable Map<String, Object> readListCache(String key) {
        if (redisCache == null || !redisCache.isAvailable()) return null;
        String json = redisCache.getCached(key);
        if (json == null) return null;
        try { return xuanmo.arcartxsuite.api.util.SimpleJsonParser.parse(json); }
        catch (RuntimeException exception) { return null; }
    }

    private void writeListCache(String key, Map<String, Object> packet) {
        if (redisCache != null && redisCache.isAvailable()) {
            redisCache.cacheListings(key, xuanmo.arcartxsuite.market.storage.MarketPacketJson.encode(packet));
        }
    }

    private boolean requiresActionToken(String action) {
        return switch (action) {
            case "auction_bid", "auction_cancel", "auction_list_create",
                "shop_buy", "recycle_all", "recycle_single",
                "pshop_buy", "pshop_cancel", "pshop_create",
                "shop_edit_shop_save", "shop_edit_shop_delete",
                "shop_edit_item_save", "shop_edit_item_delete" -> true;
            case "shop_edit_stock", "auction_favorite", "pshop_profile_save", "pshop_profile_pick", "pshop_store_follow",
                "guard_pick","guard_add","guard_remove","guard_auto" -> true;
            default -> false;
        };
    }

    private boolean requiresUiToken(String uiId) {
        return uiId.equals(config.ui().auctionId()) || uiId.equals(config.ui().auctionRoomId()) || uiId.equals(AUCTION_SELL_UI_ID)
            || uiId.equals(config.ui().shopId()) || uiId.equals(config.ui().recycleId())
            || uiId.equals(config.ui().sellId()) || uiId.equals(config.ui().playerShopId())
            || uiId.equals(config.ui().playerShopsId()) || uiId.equals(config.ui().playerStoreId())
            || uiId.equals(config.ui().playerStoreEditId()) || uiId.equals(config.ui().recycleProtectionId())
            || uiId.equals(config.ui().adminTradesId()) || uiId.equals(shopEditUiId());
    }

    private Map<UUID, String> tokenMap(String uiId) {
        return actionTokens.computeIfAbsent(uiId, ignored -> new ConcurrentHashMap<>());
    }

    private void issueActionToken(Player player, String uiId) {
        tokenMap(uiId).put(player.getUniqueId(), UUID.randomUUID().toString());
    }

    private void rotateToken(Player player, String uiId) {
        issueActionToken(player, uiId);
    }

    private boolean beginActionToken(Player player, String action, List<String> data) {
        String uiId = actionUiId(player,action);
        int index = switch (action) {
            case "auction_cancel" -> 2;
            case "auction_bid" -> 3;
            case "auction_list_create" -> 6;
            case "shop_buy" -> 4;
            case "recycle_single" -> 2;
            case "pshop_buy", "pshop_cancel" -> 2;
            case "pshop_create" -> 7;
            case "shop_edit_shop_delete" -> 2;
            case "shop_edit_item_delete" -> 3;
            case "shop_edit_shop_save", "shop_edit_item_save" -> 4;
            case "shop_edit_stock" -> 6;
            case "auction_favorite" -> 5;
            case "pshop_profile_save", "pshop_profile_pick", "pshop_store_follow" -> 2;
            case "guard_pick","guard_add","guard_remove","guard_auto" -> 3;
            default -> 1;
        };
        String supplied = data.size() > index ? data.get(index) : "";
        return packetBridge != null && packetBridge.isUiOpen(player, uiId)
            && tokenMap(uiId).get(player.getUniqueId()) != null
            && tokenMap(uiId).get(player.getUniqueId()).equals(supplied)
            && actionTokensInFlight.add(player.getUniqueId());
    }

    private void sendActionState(Player player,String action){sendActionState(player,action,false);}

    private void sendActionState(Player player, String action,boolean rejected) {
        String uiId = actionUiId(player,action);
        if (packetBridge == null || !packetBridge.isUiOpen(player, uiId)) return;
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("rejected",rejected);
        sendUiPacket(player, uiId, "action_result", packet);
    }

    private String actionUiId(Player player,String action) {
        if(List.of("auction_bid","auction_cancel","auction_favorite").contains(action))return activeAuctionUi(player);
        return switch (action) {
            case "auction_list_create" -> AUCTION_SELL_UI_ID;
            case "pshop_create" -> config.ui().sellId();
            case "pshop_buy", "pshop_cancel" -> playerStores==null?config.ui().playerShopId():playerStores.tradeUi(player);
            case "pshop_profile_save", "pshop_profile_pick" -> config.ui().playerStoreEditId();
            case "guard_pick","guard_add","guard_remove","guard_auto" -> config.ui().recycleProtectionId();
            case "pshop_store_follow" -> playerStores==null?config.ui().playerShopsId():playerStores.followUi(player);
            case "shop_buy" -> config.ui().shopId();
            case "recycle_all", "recycle_single" -> config.ui().recycleId();
            case "shop_edit_shop_save", "shop_edit_shop_delete",
                "shop_edit_item_save", "shop_edit_item_delete" -> shopEditUiId();
            case "shop_edit_stock" -> shopEditUiId();
            default -> config.ui().auctionId();
        };
    }

    private Map<String, Object> buildAuctionSellPacket(Player player) {
        Map<String, Object> packet = buildSellPacket(player);
        packet.put("packetId", config.ui().packetId());
        Map<String, Object> currencies = new LinkedHashMap<>();
        int currencyIndex = 0;
        for (var option : config.auction().currencies()) {
            Map<String, Object> currency = new LinkedHashMap<>();
            currency.put("id", option.id());
            currency.put("name", option.name());
            currencies.put(Integer.toString(currencyIndex++), currency);
        }
        packet.put("currencies", currencies);
        packet.put("maxCurrencyCount", currencies.size());
        packet.put("defaultCurrency", config.auction().defaultCurrency());
        packet.put("priceMin", config.auction().minPrice());
        packet.put("priceMax", config.auction().maxPrice());
        packet.put("messageMaxLength", config.auction().messageMaxLength());
        return packet;
    }

    private Map<String, Object> buildAuctionPacket(Player player, List<AuctionListing> listings,
                                                    int page, int totalPages, String currentCategory) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("page", page);
        packet.put("totalPages", totalPages);
        packet.put("pageText", (page + 1) + "/" + totalPages);
        packet.put("currentCategory", currentCategory);

        // 分类数据
        Map<String, Object> categories = new LinkedHashMap<>();
        Map<String, Object> categoryTexts = new LinkedHashMap<>();
        int categoryIndex = 0;
        Map<String, Object> allCategory = new LinkedHashMap<>();
        allCategory.put("id", "all");
        categories.put(Integer.toString(categoryIndex), allCategory);
        categoryTexts.put(Integer.toString(categoryIndex), "&f全部");
        categoryIndex++;
        Map<String,Object> followingCategory=new LinkedHashMap<>();followingCategory.put("id","favorites");
        categories.put(Integer.toString(categoryIndex),followingCategory);categoryTexts.put(Integer.toString(categoryIndex++),"&e我的关注");
        for(String mode:List.of("ending","participating")) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",mode);
            categories.put(Integer.toString(categoryIndex),row);
            categoryTexts.put(Integer.toString(categoryIndex++),"ending".equals(mode)?"&6即将结束":"&b我的竞拍");
        }
        for (var entry : config.auction().categories().entrySet()) {
            Map<String, Object> category = new LinkedHashMap<>();
            category.put("id", entry.getKey());
            String key = Integer.toString(categoryIndex++);
            categories.put(key, category);
            categoryTexts.put(key, "&f" + entry.getValue().displayName());
        }
        packet.put("categories", categories);
        packet.put("categoryTexts", categoryTexts);

        // 物品列表数据
        Map<String, Object> listingsMap = new LinkedHashMap<>();
        Map<String, Object> listingTexts = new LinkedHashMap<>();
        Set<Long> favoriteIds = new java.util.HashSet<>(repository.getFavorites(player.getUniqueId()));
        for (int i = 0; i < listings.size() && i < AUCTION_PAGE_SIZE; i++) {
            AuctionListing listing = listings.get(i);
            String key = Integer.toString(i);
            double minBid=auctionService.minimumBid(listing).doubleValue();
            boolean active=listing.isActive();
            boolean hasBid=listing.getHighestBidder()!=null;
            boolean canBid=active && listing.getType()!=AuctionListing.ListingType.BUY_NOW
                && !listing.getSeller().equals(player.getUniqueId()) && minBid<=config.auction().maxPrice();
            String status=listing.getStatus()==AuctionListing.ListingStatus.ACTIVE
                ?active?(hasBid?"竞价中":"暂无出价"):"等待结算"
                :listing.getStatus()==AuctionListing.ListingStatus.SOLD?"已成交":listing.getStatus()==AuctionListing.ListingStatus.EXPIRED?"已流拍":"已下架";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", listing.getId());
            row.put("name", listing.getItemDisplayName());
            ItemDisplay preview = serializedItemPreview(listing.getItemData());
            row.put("itemAmount", preview.amount());
            row.put("seller", listing.getSellerName());
            row.put("price", currencyManager.format(listing.getCurrency(),BigDecimal.valueOf(listing.getStartingBid())));
            row.put("minBid", minBid);
            row.put("type", listing.getType().name());
            row.put("canBuyNow", false);
            row.put("canBid", canBid);
            row.put("isFavorite", favoriteIds.contains(listing.getId()));
            row.put("bid",hasBid?currencyManager.format(listing.getCurrency(),BigDecimal.valueOf(listing.getCurrentBid())):"暂无出价");
            row.put("status",status);row.put("closed",!active);
            row.put("remainingSeconds",Math.max(0,(listing.getExpiresAt()-System.currentTimeMillis()+999)/1000));
            row.put("isHighest",player.getUniqueId().equals(listing.getHighestBidder()));
            row.put("highest",player.getUniqueId().equals(listing.getHighestBidder())?"你（领先）":hasBid?nameFromUuid(listing.getHighestBidder()):"暂无领先者");
            row.put("timeLeft", formatTimeLeft(listing.getExpiresAt()));
            row.put("currencyName", currencyDisplayName(listing.getCurrency()));
            row.put("message", listing.getMessage());
            row.put("canCancel",active && listing.getSeller().equals(player.getUniqueId())
                && (!hasBid || config.auction().allowCancelWithBids()));
            listingsMap.put(key, row);
            listingTexts.put(key, listing.getItemDisplayName());

            // 物品 JSON（flat 字段）
            packet.put("itemJson" + i, preview.json());
        }
        packet.put("listings", listingsMap);
        packet.put("listingTexts", listingTexts);
        packet.put("maxCategoryCount", categories.size());
        packet.put("maxListingCount", listingsMap.size());
        return packet;
    }

    private Map<String, Object> buildShopPacket(Player player, String selectedShopId) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("isAdmin", isShopAdmin(player));

        // 商店列表
        Map<String, Object> shops = new LinkedHashMap<>();
        Map<String, Object> shopTexts = new LinkedHashMap<>();
        String firstShopId = null;
        int shopIndex = 0;
        if (shopService != null) {
            for (var entry : shopService.getShops().entrySet()) {
                if (!entry.getValue().permission().isBlank() && !player.hasPermission(entry.getValue().permission())) continue;
                String id = entry.getKey();
                if (firstShopId == null) firstShopId = id;
                String key = Integer.toString(shopIndex++);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                shops.put(key, row);
                packet.put("shopIconJson" + key, iconResolver.resolveItemJson(player, entry.getValue().icon()));
                shopTexts.put(key, "&f" + entry.getValue().displayName());
            }
        }
        packet.put("shops", shops);
        packet.put("shopTexts", shopTexts);

        // 当前选中商店
        String activeShopId = selectedShopId != null
            && !selectedShopId.isEmpty()
            && shopService != null
            && shopService.getShop(selectedShopId) != null
            && (shopService.getShop(selectedShopId).permission().isBlank()
                || player.hasPermission(shopService.getShop(selectedShopId).permission()))
            ? selectedShopId
            : firstShopId;
        packet.put("shopId", activeShopId != null ? activeShopId : "");
        packet.put("shopName", "");

        // 商品列表（使用合并动态商品和价格乘数后的有效商店定义）
        Map<String, Object> items = new LinkedHashMap<>();
        if (activeShopId != null && shopService != null) {
            var shopDef = shopService.getEffectiveShop(activeShopId);
            if (shopDef != null) {
                packet.put("shopName", shopDef.displayName());
                int idx = 0;
                for (var itemEntry : shopDef.items().entrySet()) {
                    var shopItem = itemEntry.getValue();
                    // 上架条件不满足的商品对该玩家不显示
                    if (!shopService.meetsConditions(player, shopItem)) continue;
                    String key = Integer.toString(idx);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", itemEntry.getKey());
                    // 商品卡由 UI 控制正文颜色，物品原有颜色仍保留在图标及悬浮提示中。
                    row.put("name", ChatColor.stripColor(MessageProvider.translateColors(shopItem.displayName())));
                    row.put("price", shopService.displayBuyPriceDecimal(player, shopItem).stripTrailingZeros().toPlainString());
                    row.put("priceRaw", shopService.displayBuyPriceDecimal(player, shopItem).toPlainString());
                    row.put("currencyName", currencyDisplayName(shopItem.currency()));
                    row.put("stock", formatShopStock(player, activeShopId, itemEntry.getKey(), shopItem));
                    row.put("limitText", shopItem.limitPerPlayer() > 0 ? "限购 " + shopItem.limitPerPlayer() + " 个" : "");
                    items.put(key, row);

                    // 物品 JSON（flat 字段）
                    packet.put("shopItemJson" + idx, shopItemJson(player, shopItem));
                    idx++;
                }
            }
        }
        packet.put("items", items);
        packet.put("maxShopCount", shops.size());
        packet.put("maxItemCount", items.size());
        return packet;
    }

    private String formatRecycleEarnings(Map<String, Double> earningsByCurrency) {
        return earningsByCurrency.entrySet().stream()
            .map(entry -> currencyManager.format(entry.getKey(), BigDecimal.valueOf(entry.getValue())))
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private String itemFingerprint(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return "-";
        try {
            byte[] bytes = itemSerializer.serialize(item.clone()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            logger.warning("[Market] 无法读取物品快照: " + exception.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    private String inventoryFingerprint(Player player) {
        StringBuilder snapshot = new StringBuilder();
        for (int slot = 0; slot < 36; slot++) {
            snapshot.append(slot).append(':').append(itemFingerprint(player.getInventory().getItem(slot))).append(';');
        }
        return snapshot.toString();
    }

    private Map<String, Object> buildRecyclePacket(Player player) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("inventoryFingerprint", inventoryFingerprint(player));
        var preferences=management==null?null:management.protection().cached(player);
        packet.put("protectionVersion",preferences==null?"":String.valueOf(preferences.version()));

        double multiplier = recycleService != null ? recycleService.getMultiplier(player) : 1.0;
        Map<String, Double> totalsByCurrency = new LinkedHashMap<>();
        Map<String, Object> recyclables = new LinkedHashMap<>();
        int idx = 0,eligible=0,protectedItems=0;
        for (int slot = 0; slot < 36 && recycleService != null; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            double unitPrice = recycleService.getRecyclePrice(item, player);
            if (unitPrice <= 0) continue;
            String key = Integer.toString(idx);
            double total = unitPrice * item.getAmount();
            String itemCurrency = recycleService.getRecycleCurrency(item);
            String protectedReason=recycleService.protectionReason(player,item);
            if(protectedReason==null){eligible++;if(itemCurrency!=null)totalsByCurrency.merge(itemCurrency,total,Double::sum);}
            else protectedItems+=item.getAmount();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("protected",protectedReason!=null);row.put("protectedReason",protectedReason==null?"":protectedReason);
            row.put("slot", slot);
            row.put("fingerprint", itemFingerprint(item));
            row.put("name", itemDisplayName(item));
            row.put("amount", item.getAmount());
            row.put("unitPrice", formatPrice(unitPrice));
            row.put("totalPrice", formatPrice(total));
            row.put("currencyName", currencyDisplayName(itemCurrency));
            recyclables.put(key, row);
            packet.put("recycleItemJson" + idx, itemToJson(item));
            idx++;
        }
        packet.put("recyclables", recyclables);
        // 收益标题：单货币="可回收总价值:"，多货币="可回收收益明细:"
        boolean singleCurrency = totalsByCurrency.size() == 1;
        packet.put("earningsTitle", singleCurrency ? "可回收总价值:" : "可回收收益明细:");
        // 收益文本（UI 分行展示，格式："货币名: 金额"）
        Map<String, String> earningsTexts = new LinkedHashMap<>();
        int eIdx = 0;
        for (var entry : totalsByCurrency.entrySet()) {
            String currencyId = entry.getKey();
            var def = currencyManager.definition(currencyId);
            String displayName = def != null && def.displayName() != null && !def.displayName().isEmpty()
                ? def.displayName() : currencyId;
            earningsTexts.put(Integer.toString(eIdx),
                displayName + ": " + currencyManager.format(currencyId, BigDecimal.valueOf(entry.getValue())));
            eIdx++;
        }
        packet.put("earningsTexts", earningsTexts);
        packet.put("maxEarningsCount", earningsTexts.size());
        packet.put("multiplier", String.format("%.1f", multiplier));
        packet.put("maxRecycleCount", recyclables.size());
        packet.put("eligibleCount",eligible);packet.put("protectedItems",protectedItems);
        packet.put("autoEffective",management!=null && management.protection().automatic(player));
        packet.put("batchConfirm", config.recycle().batchConfirm());
        packet.put("batchEnabled", config.recycle().batchRecycleEnabled());
        return packet;
    }

    private Map<String, Object> buildHistoryPacket(Player player, int page) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("isAdmin",isShopAdmin(player));
        packet.put("packetId", config.ui().packetId());
        int totalCount = repository.countHistoryByPlayer(player.getUniqueId());
        int totalPages = Math.max(1, (totalCount + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        List<AuctionHistory> histories = repository.getHistoryByPlayer(
            player.getUniqueId(), page * HISTORY_PAGE_SIZE, HISTORY_PAGE_SIZE);

        packet.put("page", page);
        packet.put("totalPages", totalPages);

        Map<String, Object> records = new LinkedHashMap<>();
        for (int i = 0; i < histories.size() && i < HISTORY_PAGE_SIZE; i++) {
            AuctionHistory h = histories.get(i);
            String key = Integer.toString(i);
            Map<String, Object> row = new LinkedHashMap<>();
            boolean isSeller = h.seller().equals(player.getUniqueId());
            String typeText;
            String typeColor;
            String counterpart;
            switch (h.transactionType()) {
                case "SHOP_BUY" -> {
                    typeText = "系统商店购买";
                    typeColor = "&e";
                    counterpart = "";
                }
                case "RECYCLE" -> {
                    typeText = "回收收入";
                    typeColor = "&a";
                    counterpart = "";
                }
                case "PSHOP_PURCHASE" -> {
                    typeText = isSeller ? "玩家商店售出" : "玩家商店购买";
                    typeColor = isSeller ? "&a" : "&e";
                    counterpart = isSeller ? "买家: " + nameFromUuid(h.buyer()) : "卖家: " + nameFromUuid(h.seller());
                }
                case "BUY_NOW" -> {
                    typeText = isSeller ? "售出" : "购买";
                    typeColor = isSeller ? "&a" : "&e";
                    counterpart = isSeller ? "买家: " + nameFromUuid(h.buyer()) : "卖家: " + nameFromUuid(h.seller());
                }
                case "BID_WIN" -> {
                    typeText = isSeller ? "竞价售出" : "竞价获得";
                    typeColor = isSeller ? "&a" : "&b";
                    counterpart = isSeller ? "买家: " + nameFromUuid(h.buyer()) : "卖家: " + nameFromUuid(h.seller());
                }
                case "EXPIRED", "PSHOP_EXPIRED" -> {
                    typeText = "过期退还";
                    typeColor = "&7";
                    counterpart = "";
                }
                case "CANCELLED", "ADMIN_CANCELLED", "PSHOP_CANCELLED" -> {
                    typeText = "已取消";
                    typeColor = "&c";
                    counterpart = "";
                }
                default -> {
                    typeText = h.transactionType();
                    typeColor = "&7";
                    counterpart = "";
                }
            }
            row.put("typeText", typeText);
            row.put("typeColor", typeColor);
            row.put("itemName", h.itemDisplayName());
            ItemDisplay preview = serializedItemPreview(h.itemData());
            row.put("itemAmount", preview.amount());
            row.put("counterpart", counterpart);
            row.put("amount", formatPrice(h.price()) + " " + currencyDisplayName(h.currency()));
            row.put("time", TIME_FMT.format(new Date(h.timestamp())));
            records.put(key, row);

            // 物品 JSON（flat 字段）
            packet.put("historyItemJson" + i, preview.json());
        }
        packet.put("records", records);
        packet.put("maxRecordCount", records.size());
        return packet;
    }

    // ─── 辅助方法 ─────────────────────────────────────────────

    /** 将货币 ID 解析为显示名，找不到定义时回退为原始 ID。 */
    private String currencyDisplayName(String currencyId) {
        var def = currencyManager.definition(currencyId);
        return def != null && def.displayName() != null && !def.displayName().isEmpty()
            ? def.displayName() : currencyId;
    }

    private String formatShopStock(Player player, String shopId, String itemKey, ShopService.ShopItem shopItem) {
        if ("unlimited".equalsIgnoreCase(shopItem.stockMode())) {
            return "无限";
        }
        if (repository == null || shopItem.stockAmount() <= 0) {
            return "无限";
        }
        if ("global".equalsIgnoreCase(shopItem.stockMode())) {
            return String.valueOf(repository.getGlobalShopStock(shopId, itemKey, shopItem.stockAmount()));
        }
        if ("per-player".equalsIgnoreCase(shopItem.stockMode())) {
            return String.valueOf(repository.getPlayerShopStock(
                player.getUniqueId(), shopId, itemKey, shopItem.stockAmount()
            ));
        }
        return String.valueOf(shopItem.stockAmount());
    }

    private String itemToJson(ItemStack item) {
        if (item == null || item.getType().isAir()) return "";
        if (itemStackBridge != null) {
            return itemStackBridge.itemToPreviewJson(item).orElse("");
        }
        return "";
    }

    private ItemDisplay serializedItemPreview(String itemData) {
        if (itemData == null || itemData.isBlank()) return new ItemDisplay("", 0);
        int amount = 0;
        try {
            ItemStack item = itemSerializer.deserialize(itemData);
            if (item == null) return new ItemDisplay("", 0);
            amount = item.getAmount();
            return new ItemDisplay(itemToJson(item), amount);
        } catch (Exception exception) {
            return new ItemDisplay("", amount);
        }
    }

    private record ItemDisplay(String json, int amount) {
    }

    private String shopItemJson(Player player, ShopService.ShopItem shopItem) {
        if (shopItem.icon() != null) return iconResolver.resolveItemJson(player, shopItem.icon());
        try {
            ItemStack item = xuanmo.arcartxsuite.market.shop.ShopItemFactory.create(
                itemSourceRegistry, shopItem.item(), 1, player, logger);
            return item != null ? itemToJson(item) : "";
        } catch (Exception exception) {
            return "";
        }
    }

    private String itemDisplayName(ItemStack item) {
        if (item == null) return "";
        return vanillaItemNameBridge.resolveDisplayName(item);
    }

    private String formatPrice(double price) {
        if (price == (long) price) return String.valueOf((long) price);
        return String.format("%.2f", price);
    }

    private String formatTimeLeft(long expiresAt) {
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) return "已过期";
        long hours = remaining / (1000 * 60 * 60);
        long minutes = (remaining / (1000 * 60)) % 60;
        if (hours >= 24) return (hours / 24) + "天" + (hours % 24) + "时";
        if (hours > 0) return hours + "时" + minutes + "分";
        return minutes + "分";
    }

    private String nameFromUuid(java.util.UUID uuid) {
        if (uuid == null) return "未知";
        Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString().substring(0, 8);
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        try { return (int) Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    private static long parseLongSafe(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        try { return (long) Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    private static double parseDoubleSafe(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    /**
     * 对留言执行敏感词过滤。
     * @return 过滤后的留言；若被拦截则返回 null
     */
    private String filterMessage(Player player, String message) {
        var result = contentModerator.filter(message);
        if (result.blocked()) {
            player.sendMessage(messages.get("player.sell.message-filtered"));
            return null;
        }
        return result.text();
    }

    // ─── 管理接口 ───────────────────────────────────────────

    public int getAuctionCount() {
        return auctionService != null ? auctionService.countActive() : 0;
    }

    public int getMyListingsCount(Player player) {
        return auctionService != null ? auctionService.countMy(player.getUniqueId()) : 0;
    }

    /**
     * 获取玩家自己的上架物品 ID 列表，用于 tab 补全。
     *
     * @param player 玩家
     * @return 上架 ID 字符串列表，服务不可用时返回空列表
     */
    public List<String> getMyListingIds(Player player) {
        if (auctionService == null) return List.of();
        return auctionService.getMyListings(player.getUniqueId()).stream()
            .map(listing -> String.valueOf(listing.getId()))
            .toList();
    }

    public int getShopCount() {
        return shopService != null ? shopService.getShops().size() : 0;
    }

    public Set<String> getShopIds() {
        return shopService != null ? shopService.getShops().keySet() : Set.of();
    }

    public xuanmo.arcartxsuite.market.recycle.RecycleService getRecycleService() {
        return recycleService;
    }

    public int getRecycleEntryCount() {
        return recycleService != null ? recycleService.getEntries().size() : 0;
    }

    public boolean crossServerActive() {
        return crossServerChannel != null && crossServerChannel.isActive();
    }

    public boolean isListCacheConnected() {
        return redisCache != null && redisCache.isAvailable();
    }

    public int clearExpired() {
        return (auctionService == null ? 0 : auctionService.triggerExpiredProcessing())
            + (playerShopService == null ? 0 : playerShopService.triggerExpiredProcessing());
    }

    public boolean adminRemoveListing(UUID actor,long listingId){return auctionService!=null && auctionService.adminForceCancelListing(actor,listingId);}
    public boolean adminRemovePlayerShopListing(UUID actor,long listingId){return playerShopService!=null && playerShopService.adminForceCancel(actor,listingId);}

    public boolean adminRemoveListing(long listingId) {
        if (auctionService == null) return false;
        return auctionService.adminForceCancelListing(listingId);
    }

    public boolean adminRemovePlayerShopListing(long listingId) {
        return playerShopService != null && playerShopService.adminForceCancel(listingId);
    }

    public int getPlayerShopCount() {
        return playerShopService == null ? 0 : playerShopService.countActive();
    }

    public String subsystemStatus() {
        return "拍卖行=" + (auctionService != null) + "，玩家商店=" + (playerShopService != null)
            + "，系统商店=" + (shopService != null) + "，回收商店=" + (recycleService != null)
            + "，动态商店=" + (dynamicShopService != null);
    }

    private void handleCrossServerMessage(xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery delivery) {
        String message = delivery == null ? null : delivery.payload();
        if (message == null || message.isBlank()) {
            return;
        }

        // require-signature：开启时丢弃未验签通过的消息（签名未启用时等于拒绝全部入站，属管理员显式选择）
        if (config.crossServerSecurity().requireSignature() && !delivery.signed()) {
            logger.warning("cross-server require-signature 开启，丢弃未签名消息: "
                + (message.length() > 64 ? message.substring(0, 64) : message));
            return;
        }
        boolean strict = config.crossServerSecurity().strictValidation();

        if(auctionRooms!=null && (message.startsWith("BID_") || message.startsWith("LISTING_"))) {
            String[] parts=message.split(":",3);
            if(parts.length>1)auctionRooms.changed(parseLongSafe(parts[1],0));
        }
        if(message.startsWith("RECYCLE_PROTECTION_CHANGED:") && management!=null){
            try{java.util.UUID owner=java.util.UUID.fromString(message.substring("RECYCLE_PROTECTION_CHANGED:".length()));
                Runnable refresh=()->{if(management!=null)management.protection().invalidate(owner);};
                if(org.bukkit.Bukkit.isPrimaryThread())refresh.run();else org.bukkit.Bukkit.getScheduler().runTask(plugin,refresh);
            }catch(IllegalArgumentException ignored){}
        }
        // 拍卖消息不再重复写入竞价：写库、押金与延时由出价节点原子处理。

        // 跨服限购同步：其他服购买后广播，本地 upsert 限购记录
        if (message.startsWith("SHOP_LIMIT_UPDATE:")) {
            String[] parts = message.split(":", -1);
            if (parts.length == 7) {
                try {
                    java.util.UUID playerUuid = java.util.UUID.fromString(parts[1]);
                    String shopId = parts[2];
                    String itemId = parts[3];
                    int newCount = Integer.parseInt(parts[4]);
                    long timestamp = Long.parseLong(parts[5]);
                    long resetTime = Long.parseLong(parts[6]);
                    if (newCount >= 0 && (!strict || newCount <= 1_000_000)) {
                        repository.upsertShopLimit(new xuanmo.arcartxsuite.market.shop.ShopLimitRecord(
                            playerUuid, shopId, itemId, newCount, timestamp, resetTime
                        ));
                        logger.fine("applied remote shop limit update player=" + playerUuid
                            + " shop=" + shopId + " item=" + itemId + " count=" + newCount);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("invalid remote shop limit payload: " + message);
                }
            } else {
                logger.warning("remote shop limit payload malformed; ignored: " + message);
            }
        }

        if ((message.startsWith("LISTING_")
                || message.startsWith("BID_"))
                && redisCache != null
                && redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
        }

        if(message.startsWith("PSHOP_") && playerStores!=null)playerStores.changed();
        // 玩家商店跨服同步：收到广播后失效本地缓存
        if (message.startsWith("PSHOP_")
                && redisCache != null
                && redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:pshop:");
        }

        // 跨服动态商品状态同步
        if (message.startsWith("DYNAMIC_SHOP_UPDATE:") && dynamicShopService != null) {
            // 格式: DYNAMIC_SHOP_UPDATE:strategyKey:shopId:jsonData
            // jsonData 可能包含冒号，所以最多分 4 段
            String[] parts = message.split(":", 4);
            if (parts.length == 4) {
                try {
                    dynamicShopService.handleCrossServerMessage(
                        "DYNAMIC_SHOP_UPDATE", parts[1], parts[2], parts[3], strict);
                    logger.fine("applied remote dynamic shop update: strategy=" + parts[1]);
                } catch (Exception e) {
                    logger.warning("failed to apply remote dynamic shop update: " + e.getMessage());
                }
            }
        }
    }
    /**
     * strict-validation 下的远程出价校验：挂单存在且活跃、拍卖类型、金额在配置范围内、
     * 满足最小加价幅度。伪造/畸形消息在这里被拦截，不写库。
     */
    private boolean validateRemoteBid(long listingId, double amount) {
        xuanmo.arcartxsuite.market.auction.AuctionListing listing = repository.getListing(listingId);
        if (listing == null || !listing.isActive()) {
            return false;
        }
        if (listing.getType() == xuanmo.arcartxsuite.market.auction.AuctionListing.ListingType.BUY_NOW) {
            return false;
        }
        var auctionConfig = config.auction();
        if (amount < auctionConfig.minPrice() || amount > auctionConfig.maxPrice()) {
            return false;
        }
        double current = Math.max(listing.getCurrentBid(), listing.getStartingBid());
        double minIncrement = Math.max(
            current * auctionConfig.minBidIncrementRatio(),
            auctionConfig.minBidIncrementAbsolute());
        return amount >= current + minIncrement;
    }

    private void dispatchSignal(String signal, Player player, Map<String, String> variables) {
        if (signalProvider == null) return;
        SignalDispatchable sd = signalProvider.get();
        if (sd != null && player != null) {
            sd.dispatchSignal(signal, player, variables);
        }
    }

    private void publishEvent(String topic, Player player, Map<String, String> payload) {
        if (eventBusProvider == null) return;
        EventBusCapability bus = eventBusProvider.get();
        if (bus != null) {
            bus.publish(topic, player, payload);
        }
    }
}
