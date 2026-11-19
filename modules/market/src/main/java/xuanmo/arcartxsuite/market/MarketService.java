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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
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
import xuanmo.arcartxsuite.market.auction.AuctionBidTerms;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.auction.AuctionRoomSnapshot;
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
import xuanmo.arcartxsuite.market.storage.MarketListingDraft;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;
import xuanmo.arcartxsuite.market.storage.ShopCheckout;

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
    private final Map<UUID, ShopView> shopViews = new java.util.HashMap<>();
    private final Map<UUID, Runnable> nextShopRead = new java.util.HashMap<>();
    private final Set<UUID> activeShopReads = new java.util.HashSet<>();
    private final Map<UUID, String> activeShopOrders = new java.util.HashMap<>();
    private final Set<UUID> pendingMyAuctions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingMyShops = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> sellModes = new ConcurrentHashMap<>();

    private static final int AUCTION_PAGE_SIZE = 20;
    private static final int SHOP_PAGE_SIZE = 12;
    private static final long SHOP_QUOTE_TTL_NANOS = 30_000_000_000L;
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

    private volatile java.util.function.Supplier<EventBusCapability> eventBusProvider;

    private xuanmo.arcartxsuite.market.auction.AuctionRoomService auctionRooms;
    private final Map<UUID,List<String>> auctionBrowseContext=new ConcurrentHashMap<>();
    private final Set<UUID> pendingAuctionListInvalidations = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingPlayerShopListInvalidations = ConcurrentHashMap.newKeySet();
    private final Semaphore listCacheWrites = new Semaphore(8);
    private final Set<String> pendingListCacheInvalidations = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean listCacheInvalidationRunning = new AtomicBoolean();
    private org.bukkit.scheduler.BukkitTask auctionCatchUpTask;
    private boolean readingAuctionRevision;
    private long observedAuctionRevision = -1;
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
    private CompletableFuture<Void> storagePreparation = CompletableFuture.completedFuture(null);
    private volatile CrossServerChannel crossServerChannel;
    private AuctionService auctionService;
    private ShopService shopService;
    private RecycleService recycleService;
    private DynamicShopService dynamicShopService;
    private PlayerShopService playerShopService;
    private final SchedulerAPI scheduler;
    private final MarketWork work;
    private final MarketDebits debits;
    private MarketListings listings;
    private final MarketPageReads pageReads;
    private final Map<UUID, CompletableFuture<String>> shareRequests = new ConcurrentHashMap<>();
    private static final long AUCTION_QUOTE_TTL = TimeUnit.SECONDS.toNanos(15);
    private final Semaphore auctionQuoteSlots = new Semaphore(32);
    private final Semaphore auctionFavoriteSlots = new Semaphore(32);
    private final Map<UUID, Object> asyncActionTickets = new java.util.HashMap<>();
    private final Map<UUID, AuctionQuotes> auctionQuotes = new java.util.HashMap<>();
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor cleanup =
        new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(0, 1, 30, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "AXS-Market-Close");
                thread.setDaemon(true);
                return thread;
            });
    private final java.util.concurrent.CompletableFuture<Void> databaseStopped = new java.util.concurrent.CompletableFuture<>();

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
        this.work = new MarketWork(plugin);
        this.debits = new MarketDebits(work, logger);
        this.pageReads = new MarketPageReads(work, logger);
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


    public void setEventBusProvider(java.util.function.Supplier<EventBusCapability> provider) {
        this.eventBusProvider = provider;
    }

    /** 玩家退出时清理其 actionToken 与在飞行标记，避免内存累积。 */
    public void clearPlayerState(UUID playerUuid) {
        pageReads.clear(playerUuid);
        pendingAuctionListInvalidations.remove(playerUuid);
        pendingPlayerShopListInvalidations.remove(playerUuid);
        auctionQuotes.remove(playerUuid);
        asyncActionTickets.remove(playerUuid);
        shopViews.remove(playerUuid);
        nextShopRead.remove(playerUuid);
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

    java.util.concurrent.CompletionStage<Void> prepareStorage(xuanmo.arcartxsuite.api.storage.ModuleStorage scope,
            xuanmo.arcartxsuite.api.storage.ModuleStorage.Initializer initializeTables) {
        var cache = new RedisMarketCache(config.redis(), logger);
        redisCache = cache;
        storagePreparation = scope.initialize(() -> {
            initializeTables.run();
            cache.initialize();
            cache.invalidateByPrefix("market:listings:");
            cache.invalidateByPrefix("market:pshop:");
        }).toCompletableFuture();
        return storagePreparation;
    }

    public void start(java.io.File dataFolder, JdbcMarketRepository repository) throws Exception {
        this.repository = repository;
        ledger = new MarketLedger(repository, logger);
        if (config.auction().enabled() || config.playerShop().enabled()) work.repeat(debits::drain, 1L, 1L);
        CurrencyBridgeAPI tradingCurrencies = ledger.wrap(currencyManager);

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

        management=new MarketManagementController(work, plugin,repository,config,packetBridge,itemStackBridge,itemSerializer,vanillaItemNameBridge,
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
            auctionService = new AuctionService(work, plugin, config.auction(), config.messages(),
                repository, redisCache, crossServerPublisher, tradingCurrencies, itemSerializer, logger, vanillaItemNameBridge, itemRewardDispatcher, itemMatcher);
            auctionService.setEventPublisher(this::publishAuctionEvent);
            auctionService.setEventObserver((buyer, payload) ->
                publishEvent(EventBusCapability.MARKET_AUCTION_PURCHASED, buyer, payload));
            if (config.crossServer().enabled()) auctionService.setSettlementRefresh(this::publishSettlementRefresh);
            auctionService.setLedger(ledger);
            auctionService.start(config.schedulerIntervalTicks());
            auctionRooms=new xuanmo.arcartxsuite.market.auction.AuctionRoomService(work, repository,packetBridge,config.ui().auctionRoomId(), currencyManager,
                this::sendAuctionRoom,(player,message)->{
                    if (!canUseMarket(player, AuthAccess.snapshot(player.getUniqueId()))) return;
                    Map<String,Object> packet=new LinkedHashMap<>();packet.put("message",message);packet.put("packetId",config.ui().packetId());
                    sendUiPacket(player,config.ui().auctionRoomId(),"room_error",packet);
                },logger);
            auctionRooms.start();
            if (config.crossServer().enabled() && repository.getDescriptor().isMysql()) {
                auctionCatchUpTask = work.repeat(this::catchUpAuctionViews, 20L, 20L);
            }
        }

        if (config.shop().enabled()) {
            shopService = new ShopService(work, plugin, config.shop(), config.messages(),
                repository, currencyManager, itemSourceRegistry, scriptConditionEvaluator, dataFolder, logger);
            // 注入跨服限购广播回调
            if (!repository.getDescriptor().isMysql()) shopService.setLimitBroadcaster((playerUuid, shopId, itemId, newCount, timestamp, resetTime) -> {
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
                    work, plugin, logger, scheduler, shopService, repository, config.dynamic().strategies()
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
            playerShopService = new PlayerShopService(work, plugin, config.playerShop(), config.messages(),
                repository, redisCache, crossServerPublisher, tradingCurrencies, itemSerializer, logger,
                vanillaItemNameBridge, itemRewardDispatcher, itemMatcher, debits);
            playerShopService.setLedger(ledger);
            playerShopService.start(config.schedulerIntervalTicks());
            playerStores=new xuanmo.arcartxsuite.market.playershop.PlayerShopController(work, plugin,repository,config,packetBridge,itemStackBridge,itemSerializer,currencyManager,contentModerator,
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

    public void attachDeliveries(PendingDeliveryService deliveries) { this.deliveries = deliveries; deliveries.start(shopService); }

    public void attachListingSaves(java.nio.file.Path playerData, String sourceNode) {
        if (playerShopService == null && auctionService == null) return;
        listings = new MarketListings(work, repository, ledger, currencyManager, itemSerializer, debits, logger,
            playerData, sourceNode, this::listingCommitted);
    }

    private void listingCommitted(MarketListingDraft.Save save, boolean published) {
        boolean auction = save.kind() == MarketListingDraft.Kind.AUCTION;
        AuctionBidTerms details = null;
        if (published) {
            try {
                if (redisCache != null) redisCache.invalidateByPrefix(auction ? "market:listings:" : "market:pshop:");
                var channel = crossServerChannel;
                if (channel != null && channel.isActive()) channel.publish(
                    (auction ? "LISTING_CREATED:" : "PSHOP_LISTING_CREATED:") + save.listingId());
                if (auction) details = repository.readAuctionBidTerms(save.listingId());
            } catch (RuntimeException failure) {
                logger.warning("Market 上架已提交，刷新失败 #" + save.listingId() + ": " + failure.getMessage());
            }
        }
        var notification = details;
        work.main(() -> {
            Player seller = Bukkit.getPlayer(save.seller());
            if (published && auction) refreshOpenAuctionBrowsers();
            if (published && !auction && playerStores != null) playerStores.changed();
            if (notification != null) {
                publishEvent("axs.market.listing_created", seller, Map.of(
                    "price", String.valueOf(notification.startingBid()), "currency", notification.currency(),
                    "listing_id", String.valueOf(notification.id()), "seller_uuid", save.seller().toString()));
            }
            if (seller != null && AuthAccess.admitted(save.seller())) {
                seller.sendMessage(published ? ChatColor.translateAlternateColorCodes('&', auction
                    ? config.messages().auctionListed() : config.messages().playerShopListed())
                    : ChatColor.YELLOW + "上次上架扣除未写入存档，已释放暂存。上架费可在交易流水核对。");
            }
        });
    }

    public void shutdown() {
        if (work.isStopping()) return;
        work.stopCallbacks();
        if (listings != null) listings.close();
        debits.close();
        auctionQuotes.clear();
        asyncActionTickets.clear();
        List.copyOf(shareRequests.values()).forEach(request -> request.complete(null));
        try {
        if (auctionCatchUpTask != null) { auctionCatchUpTask.cancel(); auctionCatchUpTask = null; }
        shopViews.clear();
        nextShopRead.clear();
        activeShopOrders.clear();
        if(management!=null)management.close();
        if(playerStores!=null)playerStores.close();
        playerShopBrowseContext.clear();restorePlayerShopBrowse.clear();
        if(auctionRooms!=null)auctionRooms.close();
        auctionBrowseContext.clear();restoreAuctionBrowse.clear();pendingAuctionCategories.clear();
        pageReads.close();
        pendingAuctionListInvalidations.clear();
        pendingPlayerShopListInvalidations.clear();
        pendingListCacheInvalidations.clear();
        if (deliveries != null) deliveries.close();
        if (auctionService != null) auctionService.shutdown();
        if (dynamicShopService != null) dynamicShopService.shutdown();
        if (shopService != null) shopService.shutdown();
        if (recycleService != null) recycleService.shutdown();
        if (playerShopService != null) playerShopService.shutdown();
        if (crossServerChannel != null) { crossServerChannel.close(); crossServerChannel = null; }
        } finally {
            work.drain().thenCombine(storagePreparation.handle((ignored, preparationFailure) -> null),
                (workStopped, prepared) -> null).whenComplete((ignored, failure) -> finishShutdown(failure));
        }
    }

    public MarketWork work() { return work; }
    public boolean isStopping() { return work.isStopping(); }
    public java.util.concurrent.CompletionStage<Void> databaseStopped() { return databaseStopped.copy(); }

    private void finishShutdown(Throwable priorFailure) {
        var finalized = new java.util.concurrent.CompletableFuture<Void>();
        cleanup.execute(() -> {
            Throwable failure = priorFailure;
            try { if (dynamicShopService != null) dynamicShopService.finishShutdown(); }
            catch (Throwable error) { failure = error; }
            try { if (redisCache != null) redisCache.shutdown(); }
            catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            try { if (repository != null) repository.shutdown(); }
            catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
            if (failure == null) finalized.complete(null); else finalized.completeExceptionally(failure);
            cleanup.shutdown();
        });
        java.util.concurrent.CompletableFuture.allOf(finalized, cleanup.stopped().toCompletableFuture())
            .whenComplete((ignored, failure) -> {
                if (failure == null) databaseStopped.complete(null); else databaseStopped.completeExceptionally(failure);
            });
    }

    public MarketRepository getRepository() {
        return repository;
    }

    public void reviewShopOrder(String id, String action, String audit, boolean playerShop, java.util.function.Consumer<String> reply) {
        if (!shopUuid(id)) {
            reply.accept("订单 ID 格式无效");
            return;
        }
        var store = repository;
        work.async(() -> {
            String result;
            try { result = playerShop ? store.reviewPlayerShopPurchase(id, action, audit) : store.reviewShopOrder(id, action, audit); }
            catch (RuntimeException exception) {
                logger.log(java.util.logging.Level.WARNING, "商店订单人工核对失败 " + id, exception);
                result = "核对事务未确认，请查询原订单状态";
            }
            if (playerShop && redisCache != null) redisCache.invalidateByPrefix("market:pshop:");
            var message = result;
            if (plugin.isEnabled()) work.main(() -> {
                if (playerShop && playerStores != null) playerStores.changed();
                reply.accept(message);
            });
        });
    }

    public void reload() {
        if (shopService != null) shopService.reload();
        if (recycleService != null) recycleService.reload();
    }

    // ─── 拍卖行操作 ─────────────────────────────────────────

    public void createListing(Player player, ItemStack item, double startingBid, String currency, long duration) {
        submitAuctionFromHand(player, item, startingBid, currency, duration, false);
    }

    public CompletableFuture<Boolean> createAuctionListing(Player player, ItemStack item, double startingBid, String currency, long duration) {
        return submitAuctionFromHand(player, item, startingBid, currency, duration, true).published();
    }

    private MarketListings.Submission submitAuctionFromHand(Player player, ItemStack item, double bid, String currency,
                                                             long duration, boolean awaitPublication) {
        AuthSnapshot session = AuthAccess.snapshot(player.getUniqueId());
        if (auctionService == null || listings == null || !canUseMarket(player, session)) {
            return refuseAuctionListing(player, awaitPublication, messages.get("player.auction-unavailable"));
        }
        if (!Double.isFinite(bid) || bid < config.auction().minPrice() || bid > config.auction().maxPrice()) {
            return refuseAuctionListing(player, awaitPublication, messages.get("player.sell.price-out-of-range",
                formatPrice(config.auction().minPrice()), formatPrice(config.auction().maxPrice())));
        }
        String effective = currency != null && !currency.isBlank() ? currency : config.auction().defaultCurrency();
        var option = config.auction().currencies().stream().filter(row -> row.id().equalsIgnoreCase(effective)).findFirst().orElse(null);
        var bridge = option == null ? null : currencyManager.bridge(option.id());
        if (bridge == null || !bridge.available()) {
            return refuseAuctionListing(player, awaitPublication, messages.get("player.sell.invalid-currency"));
        }
        if (item == null || item.getType().isAir() || auctionService.isBlacklisted(item)) {
            return refuseAuctionListing(player, awaitPublication, "物品无法上架，请确认主手物品及黑名单");
        }
        int slot = player.getInventory().getHeldItemSlot();
        long seconds = duration <= 0 ? config.auction().defaultDurationSeconds() : duration;
        seconds = Math.max(config.auction().minDurationSeconds(), Math.min(config.auction().maxDurationSeconds(), seconds));
        var submission = listings.submit(player, slot, item, auctionTerms(item, bid, option.id(), seconds, ""),
            () -> canUseMarket(player, session) && player.getInventory().getHeldItemSlot() == slot, awaitPublication);
        submission.staged().whenComplete((result, failure) -> work.main(() -> {
            if (isCurrentMarketSession(player, session)) sendListingStage(player, result, failure);
        }));
        return submission;
    }

    private MarketListings.Submission refuseAuctionListing(Player player, boolean awaitPublication, String message) {
        player.sendMessage(message);
        return MarketListings.Submission.refused(awaitPublication, message);
    }

    private MarketListings.Terms auctionTerms(ItemStack item, double bid, String currency, long duration, String note) {
        var c = config.auction();
        return new MarketListings.Terms(MarketListingDraft.Kind.AUCTION, bid, currency, note, duration,
            auctionService.getItemDisplayName(item), auctionService.classifyItem(item), c.listingFee(), c.listingFeeCurrency(),
            c.maxListingsPerPlayer());
    }

    private boolean submitListingFromUi(Player player, String ui, String action, int slot, ItemStack item, MarketListings.Terms terms) {
        UUID viewer = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(viewer);
        String token = tokenMap(ui).get(viewer);
        BooleanSupplier authorized = () -> currentListView(player, session, ui, token);
        if (listings == null || !authorized.getAsBoolean()) return false;
        Object ticket = new Object();
        asyncActionTickets.put(viewer, ticket);
        listings.submit(player, slot, item, terms, authorized, false).staged().whenComplete((result, failure) -> work.main(() -> {
            if (!clearTrackedAction(viewer, ticket) || !isCurrentMarketSession(player, session)) return;
            sendListingStage(player, result, failure);
            if (!authorized.getAsBoolean()) return;
            rotateToken(player, ui);
            sendActionState(player, action);
        }));
        return true;
    }

    private void sendListingStage(Player player, MarketListings.Result result, Throwable failure) {
        if (failure != null) player.sendMessage(ChatColor.RED + "上架结果未确认，请核对交易流水。");
        else if (result.staged()) player.sendMessage(ChatColor.YELLOW + "商品已暂存，等待服务器自然保存背包后上架。");
        else player.sendMessage(ChatColor.RED + result.error());
    }

    public CompletableFuture<Boolean> cancelListing(Player player, long listingId) {
        var session = AuthAccess.snapshot(player.getUniqueId());
        return cancelOwnedAuction(player, listingId, session, () -> true, success -> {
            if (success && isAuctionUiOpen(player)) rotateToken(player, activeAuctionUi(player));
        });
    }

    private CompletableFuture<Boolean> cancelOwnedAuction(Player player, long listingId, AuthSnapshot session,
                                                           BooleanSupplier viewValid, Consumer<Boolean> completed) {
        var result = auctionService == null || !canUseMarket(player, session) ? CompletableFuture.completedFuture(false)
            : auctionService.cancelListing(session.playerId(), listingId, false,
                () -> canUseMarket(player, session) && viewValid.getAsBoolean());
        result.whenComplete((success, failure) -> work.main(() -> {
            boolean committed = Boolean.TRUE.equals(success);
            if (committed) refreshCancelledAuction(listingId);
            if (!isCurrentMarketSession(player, session)) return;
            try {
                sendCancellationResult(player, committed, failure);
            } finally {
                completed.accept(committed);
            }
        }));
        return result;
    }

    private void sendCancellationResult(Player player, boolean committed, Throwable failure) {
        if (failure != null) {
            player.sendMessage(ChatColor.RED + "撤单结果未确认，请刷新拍品状态或核对交易流水。");
        } else if (committed) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionCancelled()));
        } else {
            player.sendMessage(messages.get("player.cancel-failed"));
            if (!config.auction().allowCancelWithBids()) {
                player.sendMessage(ChatColor.GRAY + "已有有效出价或已到期的拍卖不能由卖家下架，管理员可强制处理。");
            }
        }
    }

    private void refreshCancelledAuction(long listingId) {
        if (auctionRooms != null) auctionRooms.changed(listingId);
        refreshOpenAuctionBrowsers();
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
        shopViews.remove(player.getUniqueId());
        nextShopRead.remove(player.getUniqueId());
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

    public CompletionStage<String> describeChatLink(Player viewer, String type, String key) {
        return resolveChatLink(viewer, type, key, false);
    }

    public CompletionStage<String> openChatLink(Player viewer, String type, String key) {
        return resolveChatLink(viewer, type, key, true);
    }

    private CompletionStage<String> resolveChatLink(Player viewer, String type, String key, boolean open) {
        UUID id = viewer.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(id);
        if (!canUseMarket(viewer, session)) return CompletableFuture.completedFuture(null);
        if ("shop".equals(type)) {
            var shop = shopService == null ? null : shopService.getShop(key);
            String description = shop != null && shop.permission().isBlank() && (!open || openSystemShop(viewer, key))
                ? "系统商店 " + shop.displayName() : null;
            return CompletableFuture.completedFuture(description);
        }
        if (shareRequests.size() >= 32) return CompletableFuture.completedFuture(null);
        var response = new CompletableFuture<String>();
        if (shareRequests.putIfAbsent(id, response) != null) return CompletableFuture.completedFuture(null);
        response.whenComplete((value, failure) -> shareRequests.remove(id, response));
        try {
            work.async(() -> {
                try {
                    if (isStopping()) {
                        response.complete(null);
                        return;
                    }
                    var target = readChatLink(type, key, id, open);
                    if (!plugin.isEnabled() || isStopping()) {
                        response.complete(null);
                        return;
                    }
                    work.main(() -> {
                        try {
                            if (target == null || !canUseMarket(viewer, session) || !target.available().getAsBoolean()) {
                                response.complete(null);
                                return;
                            }
                            String description = target.description().get();
                            response.complete(!open || target.open().test(viewer) ? description : null);
                        } catch (RuntimeException | LinkageError failure) {
                            response.completeExceptionally(failure);
                        }
                    });
                } catch (IllegalArgumentException invalidKey) {
                    response.complete(null);
                } catch (RuntimeException | LinkageError failure) {
                    response.completeExceptionally(failure);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            response.complete(null);
        }
        return response.copy();
    }

    private boolean canUseMarket(Player viewer, AuthSnapshot session) {
        return !isStopping() && repository != null && isCurrentMarketSession(viewer, session)
            && viewer.hasPermission("arcartxsuite.market.use");
    }

    private boolean isCurrentMarketSession(Player viewer, AuthSnapshot session) {
        if (!session.admitted() || !viewer.isOnline() || Bukkit.getPlayer(session.playerId()) != viewer) return false;
        return sameMarketAdmission(session);
    }

    private boolean sameMarketAdmission(AuthSnapshot session) {
        AuthSnapshot current = AuthAccess.snapshot(session.playerId());
        return current.admitted() && current.generation() == session.generation()
            && java.util.Objects.equals(current.connectionId(), session.connectionId());
    }

    private PreparedMarketLink readChatLink(String type, String key, UUID viewer, boolean open) {
        return switch (type) {
            case "auction" -> {
                if (auctionService == null || auctionRooms == null) yield null;
                long id = Long.parseLong(key);
                long readAt = System.nanoTime();
                AuctionRoomSnapshot room = open ? repository.readAuctionRoom(id, viewer) : null;
                var lot = open ? (room == null ? null : room.listing()) : repository.getListing(id);
                if (lot == null || !lot.isActive()) yield null;
                double price = lot.getType() == AuctionListing.ListingType.BUY_NOW
                    ? lot.getBuyNowPrice() : Math.max(lot.getCurrentBid(), lot.getStartingBid());
                yield new PreparedMarketLink(() -> "拍品 #" + lot.getId() + " " + lot.getItemDisplayName() + " · "
                    + currencyManager.format(lot.getCurrency(), BigDecimal.valueOf(price)), lot::isActive,
                    player -> openAuctionRoom(player, id, room, readAt));
            }
            case "listing" -> {
                var item = playerStores == null ? null : repository.getPublishedPlayerShopListing(Long.parseLong(key));
                if (item == null || !item.isActive()) yield null;
                yield new PreparedMarketLink(() -> "商品 #" + item.getId() + " " + item.getItemDisplayName() + " · "
                    + currencyManager.format(item.getCurrency(), BigDecimal.valueOf(item.getPrice())), item::isActive,
                    player -> playerStores.openSharedListing(player, item) && playerStores.isStoreOpen(player));
            }
            case "store" -> {
                var store = playerStores == null ? null : repository.playerShopProfile(UUID.fromString(key));
                if (store == null || !store.published()) yield null;
                yield new PreparedMarketLink(() -> "玩家店铺 " + store.shopName(), store::published,
                    player -> openPlayerStore(player, store.owner()) && playerStores.isStoreOpen(player));
            }
            default -> null;
        };
    }

    private record PreparedMarketLink(Supplier<String> description, BooleanSupplier available, Predicate<Player> open) {}

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

    List<String> packetIds() {
        return List.of(config.ui().packetId(), "AXS_MARKET");
    }

    /** 主动配置 ID 和内置界面的引导 ID 使用现有的精确大小写校验。 */
    public boolean ownsPacket(String packetId) {
        return config.ui().packetId().equals(packetId) || "AXS_MARKET".equals(packetId);
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (isStopping()) return true;
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
        boolean delayedAction = false;
        try {
        switch (action) {
            case "auction_list" -> handleAuctionListPacket(player, data);
            case "auction_bid" -> delayedAction = handleAuctionBidPacket(player, data);
            case "auction_quote" -> handleAuctionQuote(player, data);
            case "auction_room_ui_open" -> {if(data.size()>1)openAuctionRoom(player,parseLongSafe(data.get(1),0));}
            case "auction_room_open", "auction_room_refresh" -> {
                if(auctionRooms!=null && auctionRooms.selected(player)>0) {
                    if ("auction_room_open".equals(action)) auctionRooms.requestInitial(player);
                    else auctionRooms.request(player);
                }
                else sendAuctionRoom(player,null,null);
            }
            case "auction_room_back" -> {
                if(auctionRooms!=null)auctionRooms.leave(player.getUniqueId());
                restoreAuctionBrowse.add(player.getUniqueId());openAuctionUi(player);
            }
            case "auction_cancel" -> delayedAction = handleAuctionCancelPacket(player, data);
            case "auction_sell_ui_open" -> openAuctionSellUi(player);
            case "auction_sell_open" -> handleAuctionSellOpenPacket(player);
            case "auction_list_create" -> delayedAction = handleAuctionListCreatePacket(player, data);
            case "auction_favorite" -> delayedAction = handleAuctionFavoritePacket(player, data);
            case "shop_list" -> handleShopListPacket(player, data);
            case "shop_quote" -> handleShopQuote(player, data);
            case "shop_buy" -> handleShopBuyPacket(player, data);
            case "shop_close" -> closeShopView(player.getUniqueId(), data);
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
            case "pshop_buy" -> delayedAction = handlePlayerShopBuyPacket(player, data);
            case "pshop_cancel" -> delayedAction = handlePlayerShopCancelPacket(player, data);
            case "pshop_create" -> delayedAction = handlePlayerShopCreatePacket(player, data);
            default -> { return false; }
        }
        return true;
        } catch (RuntimeException exception) {
            logger.severe("[Market] 操作中断 action=" + action + " player=" + player.getUniqueId() + ": " + exception.getMessage());
            player.sendMessage(ChatColor.RED + "市场操作中断，请查看交易流水或联系管理员核对，勿重复提交未确认的交易。");
            if ("shop_quote".equals(action) && data.size() > 2) {
                Map<String,Object> failed = new LinkedHashMap<>(); failed.put("packetId",config.ui().packetId());
                failed.put("shopId",data.get(1)); failed.put("itemId",data.get(2)); failed.put("available",false);
                failed.put("sequence", data.size() > 4 ? data.get(4) : "");
                sendUiPacket(player,config.ui().shopId(),"shop_quote",failed);
            }
            if ("auction_quote".equals(action) && data.size()>1) {
                Map<String,Object> failed=new LinkedHashMap<>();failed.put("packetId",config.ui().packetId());
                failed.put("id",data.get(1));failed.put("available",false);failed.put("reason","报价暂不可用，请关闭弹窗后重试");
                sendUiPacket(player,activeAuctionUi(player),"auction_quote",failed);
            }
            return true;
        } finally {
            if (requiresActionToken(action) && !delayedAction) {
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

    private boolean handleAuctionListCreatePacket(Player player, List<String> data) {
        int slot = data.size() > 1 ? parseIntSafe(data.get(1), -1) : -1;
        double bid = data.size() > 2 ? parseDoubleSafe(data.get(2), 0) : 0;
        String note = data.size() > 3 && data.get(3) != null ? data.get(3) : "";
        long duration = data.size() > 4 ? parseLongSafe(data.get(4), 0) : 0;
        String currency = data.size() > 5 && data.get(5) != null && !data.get(5).isBlank()
            ? data.get(5) : config.auction().defaultCurrency();
        return handleAuctionModeCreate(player, slot, bid, note, duration, currency, AUCTION_SELL_UI_ID, "auction_list_create");
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

    private boolean handlePlayerShopCreatePacket(Player player, List<String> data) {
        if (playerShopService == null && auctionService == null) return false;
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
            return handleAuctionModeCreate(player, rawSlot, price, message, duration, currency, config.ui().sellId(), "pshop_create");
        }

        // 玩家商店上架
        if (playerShopService == null) return false;
        if (rawSlot < 9 || rawSlot > 44) {
            player.sendMessage(messages.get("player.sell.invalid-slot"));
            return false;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        if (price < config.playerShop().minPrice() || price > config.playerShop().maxPrice()) {
            player.sendMessage(messages.get("player.sell.price-out-of-range",
                formatPrice(config.playerShop().minPrice()),
                formatPrice(config.playerShop().maxPrice())));
            return false;
        }
        if (duration < config.playerShop().minDurationSeconds()
                || duration > config.playerShop().maxDurationSeconds()) {
            player.sendMessage(messages.get("player.sell.invalid-duration"));
            return false;
        }
        if (message.length() > config.playerShop().messageMaxLength()) {
            player.sendMessage(messages.get("player.sell.message-too-long",
                String.valueOf(config.playerShop().messageMaxLength())));
            return false;
        }
        message = filterMessage(player, message);
        if (message == null) return false;
        var currencyOption = config.playerShop().currencies().stream()
            .filter(option -> option.id().equalsIgnoreCase(currency))
            .findFirst().orElse(null);
        var currencyBridge = currencyOption == null ? null : currencyManager.bridge(currencyOption.id());
        if (currencyOption == null || currencyBridge == null || !currencyBridge.available()) {
            player.sendMessage(messages.get("player.sell.invalid-currency"));
            return false;
        }
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "没有可上架的物品");
            return false;
        }
        if (playerShopService.isBlacklisted(item)) {
            player.sendMessage(config.messages().itemBlacklisted());
            return false;
        }
        var c = config.playerShop();
        var terms = new MarketListings.Terms(MarketListingDraft.Kind.PLAYER_SHOP, price, currencyOption.id(), message,
            duration, playerShopService.getItemDisplayName(item), "", c.listingFee(), c.listingFeeCurrency(), c.maxListingsPerPlayer());
        return submitListingFromUi(player, config.ui().sellId(), "pshop_create", slot, item, terms);
    }

    /**
     * 统一上架 UI 的拍卖行模式处理。
     * <p>
     * 与 {@link #handleAuctionListCreatePacket} 不同，此方法使用 sell UI 的 token。
     * price 作为起拍价（startingBid），无一口价（纯竞价拍卖）。
     * 提交后显示暂存提示，自然存档确认后发布。
     */
    private boolean handleAuctionModeCreate(Player player, int rawSlot, double startingBid,
                                          String message, long duration, String currency, String ui, String action) {
        if (auctionService == null) return false;
        if (rawSlot < 9 || rawSlot > 44) {
            player.sendMessage(messages.get("player.sell.invalid-slot"));
            return false;
        }
        int slot = toBukkitBackpackSlot(rawSlot);
        if (!Double.isFinite(startingBid) || startingBid < config.auction().minPrice() || startingBid > config.auction().maxPrice()) {
            player.sendMessage(messages.get("player.sell.price-out-of-range",
                formatPrice(config.auction().minPrice()),
                formatPrice(config.auction().maxPrice())));
            return false;
        }
        if (duration < config.auction().minDurationSeconds()
                || duration > config.auction().maxDurationSeconds()) {
            player.sendMessage(messages.get("player.sell.invalid-duration"));
            return false;
        }
        if (message.length() > config.auction().messageMaxLength()) {
            player.sendMessage(messages.get("player.sell.message-too-long",
                String.valueOf(config.auction().messageMaxLength())));
            return false;
        }
        message = filterMessage(player, message);
        if (message == null) return false;
        var currencyOption = config.auction().currencies().stream()
            .filter(option -> option.id().equalsIgnoreCase(currency))
            .findFirst().orElse(null);
        var currencyBridge = currencyOption == null ? null : currencyManager.bridge(currencyOption.id());
        if (currencyOption == null || currencyBridge == null || !currencyBridge.available()) {
            player.sendMessage(messages.get("player.sell.invalid-currency"));
            return false;
        }
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "没有可上架的物品");
            return false;
        }
        if (auctionService.isBlacklisted(item)) {
            player.sendMessage(config.messages().itemBlacklisted());
            return false;
        }
        return submitListingFromUi(player, ui, action, slot, item, auctionTerms(item, startingBid, currencyOption.id(), duration, message));
    }

    private void handlePlayerShopListPacket(Player player, List<String> data) {
        UUID viewer = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(viewer);
        String ui = config.ui().playerShopId();
        if (playerShopService == null || !canUseMarket(player, session) || !packetBridge.isUiOpen(player, ui)) return;
        if (restorePlayerShopBrowse.remove(viewer)) data = playerShopBrowseContext.getOrDefault(viewer, data);
        String token = tokenMap(ui).computeIfAbsent(viewer, ignored -> UUID.randomUUID().toString());
        int page = data.size() > 1 ? Math.max(0, parseIntSafe(data.get(1), 0)) : 0;
        String keyword = data.size() > 2 && data.get(2) != null ? data.get(2) : "";
        UUID seller = pendingMyShops.remove(viewer) ? viewer : parseSellerFilter(data.size() > 3 ? data.get(3) : null);
        String sellerId = seller == null ? "" : seller.toString();
        var context = List.of("pshop_list", String.valueOf(page), keyword, sellerId, "refresh");
        playerShopBrowseContext.put(viewer, context);
        if (data.size() > 4 && "refresh".equals(data.get(4))) pendingPlayerShopListInvalidations.add(viewer);
        pageReads.submit(viewer, MarketPageReads.View.PLAYER_SHOP_LISTINGS,
            () -> playerShopBrowseContext.get(viewer) == context && currentListView(player, session, ui, token),
            () -> readMarketList("pshop", viewer, page, keyword, sellerId, true,
                pendingPlayerShopListInvalidations.remove(viewer), () -> queryPlayerShopPage(page, keyword, seller)),
            loaded -> sendMarketList(player, ui, "pshop_list", loaded, fresh -> buildPlayerShopPacket(player,
                fresh.listings(), fresh.page(), fresh.totalPages(), keyword, sellerId)),
            () -> player.sendMessage(ChatColor.YELLOW + "玩家商品暂不可用，请稍后刷新"));
    }

    private static @Nullable UUID parseSellerFilter(String seller) {
        if (seller == null || seller.isBlank() || "all".equals(seller)) return null;
        try { return UUID.fromString(seller); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private PlayerShopPage queryPlayerShopPage(int requestedPage, String keyword, @Nullable UUID seller) {
        boolean search = !keyword.isBlank();
        int count;
        if (seller == null) {
            count = search ? playerShopService.countSearch(keyword) : playerShopService.countActive();
        } else {
            count = search ? playerShopService.countSearchBySeller(seller, keyword) : playerShopService.countBySeller(seller);
        }
        int totalPages = Math.max(1, (count + PSHOP_PAGE_SIZE - 1) / PSHOP_PAGE_SIZE);
        int page = Math.min(requestedPage, totalPages - 1);
        List<PlayerShopListing> listings;
        if (seller == null) {
            listings = search ? playerShopService.searchListings(keyword, page, PSHOP_PAGE_SIZE)
                : playerShopService.getActiveListings(page, PSHOP_PAGE_SIZE);
        } else {
            listings = search ? playerShopService.searchListingsBySeller(seller, keyword, page, PSHOP_PAGE_SIZE)
                : playerShopService.getListingsBySeller(seller, page, PSHOP_PAGE_SIZE);
        }
        return new PlayerShopPage(listings, page, totalPages);
    }

    private void handlePlayerShopSellersPacket(Player player, List<String> data) {
        UUID viewer = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(viewer);
        String ui = config.ui().playerShopId();
        if (playerShopService == null || !canUseMarket(player, session) || !packetBridge.isUiOpen(player, ui)) return;
        String token = tokenMap(ui).computeIfAbsent(viewer, ignored -> UUID.randomUUID().toString());
        int requestedPage = data.size() > 1 ? Math.max(0, parseIntSafe(data.get(1), 0)) : 0;
        pageReads.submit(viewer, MarketPageReads.View.PLAYER_SHOP_SELLERS,
            () -> currentListView(player, session, ui, token),
            () -> {
                int count = playerShopService.countSellers();
                int totalPages = Math.max(1, (count + PSHOP_SELLERS_PAGE_SIZE - 1) / PSHOP_SELLERS_PAGE_SIZE);
                int page = Math.min(requestedPage, totalPages - 1);
                return new PlayerShopSellersPage(playerShopService.getSellers(page, PSHOP_SELLERS_PAGE_SIZE), page, totalPages);
            },
            loaded -> sendUiPacket(player, ui, "pshop_sellers",
                buildPlayerShopSellersPacket(player, loaded.sellers(), loaded.page(), loaded.totalPages())),
            () -> player.sendMessage(ChatColor.YELLOW + "卖家列表暂不可用，请稍后刷新"));
    }

    private record PlayerShopPage(List<PlayerShopListing> listings, int page, int totalPages) {}
    private record PlayerShopSellersPage(List<MarketRepository.PlayerShopSellerInfo> sellers, int page, int totalPages) {}

    private boolean handlePlayerShopBuyPacket(Player player, List<String> data) {
        if (playerShopService == null) return false;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        UUID viewer = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(viewer);
        boolean inStore = playerStores != null && playerStores.isStoreOpen(player);
        UUID owner = inStore ? playerStores.selectedOwner(player) : null;
        String ui = inStore ? config.ui().playerStoreId() : config.ui().playerShopId();
        String token = tokenMap(ui).get(viewer);
        BooleanSupplier authorized = () -> currentListView(player, session, ui, token)
            && (!inStore || java.util.Objects.equals(owner, playerStores.selectedOwner(player)));
        if (listingId <= 0 || (inStore && owner == null) || !authorized.getAsBoolean()) return false;
        Object ticket = new Object();
        asyncActionTickets.put(viewer, ticket);
        playerShopService.purchase(player, listingId, owner, currencyManager, authorized).whenComplete((result, failure) -> work.main(() -> {
            if (!clearTrackedAction(viewer, ticket) || !isCurrentMarketSession(player, session)) return;
            if (failure != null) {
                player.sendMessage(ChatColor.RED + "购买结果未确认，请核对交易流水。");
            } else if (result.success()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.messages().playerShopBought().replace("{amount}",
                        currencyManager.format(result.currency(), BigDecimal.valueOf(result.price())))));
            } else {
                player.sendMessage(ChatColor.RED + result.error());
            }
            if (!authorized.getAsBoolean()) return;
            try { refreshPlayerShopAfterAction(player, data); }
            finally { sendActionState(player, "pshop_buy"); }
        }));
        return true;
    }

    private boolean handlePlayerShopCancelPacket(Player player, List<String> data) {
        if (playerShopService == null) return false;
        UUID viewer = player.getUniqueId();
        boolean inStore = playerStores != null && playerStores.isStoreOpen(player);
        if (inStore && !viewer.equals(playerStores.selectedOwner(player))) {
            player.sendMessage(ChatColor.RED + "请在自己的店铺中下架商品");
            return false;
        }
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        AuthSnapshot session = AuthAccess.snapshot(viewer);
        String ui = inStore ? config.ui().playerStoreId() : config.ui().playerShopId();
        String token = tokenMap(ui).get(viewer);
        BooleanSupplier authorized = () -> currentListView(player, session, ui, token)
            && (!inStore || viewer.equals(playerStores.selectedOwner(player)));
        if (listingId <= 0 || !authorized.getAsBoolean()) return false;
        Object ticket = new Object();
        asyncActionTickets.put(viewer, ticket);
        playerShopService.cancelListing(viewer, listingId, false, authorized).whenComplete((removed, failure) -> work.main(() -> {
            if (!clearTrackedAction(viewer, ticket) || !isCurrentMarketSession(player, session)) return;
            sendPlayerShopCancellation(player, Boolean.TRUE.equals(removed), failure);
            if (!authorized.getAsBoolean()) return;
            try { refreshPlayerShopAfterAction(player, data); }
            finally { sendActionState(player, "pshop_cancel"); }
        }));
        return true;
    }

    private void refreshPlayerShopAfterAction(Player player, List<String> data) {
        if(playerStores!=null && playerStores.isStoreOpen(player)){
            rotateToken(player,config.ui().playerStoreId());playerStores.refresh(player);return;
        }
        rotateToken(player, config.ui().playerShopId());
        var current = playerShopBrowseContext.get(player.getUniqueId());
        handlePlayerShopListPacket(player, current != null ? current : List.of("pshop_list", data.size() > 3 ? data.get(3) : "0",
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


    public void cancelPlayerShopListing(Player player, long listingId) {
        if (playerShopService == null) return;
        AuthSnapshot session = AuthAccess.snapshot(player.getUniqueId());
        if (!canUseMarket(player, session)) return;
        playerShopService.cancelListing(session.playerId(), listingId, false, () -> canUseMarket(player, session))
            .whenComplete((removed, failure) -> work.main(() -> {
                if (isCurrentMarketSession(player, session)) sendPlayerShopCancellation(player, Boolean.TRUE.equals(removed), failure);
            }));
    }

    private void sendPlayerShopCancellation(Player player, boolean removed, Throwable failure) {
        if (failure != null) {
            player.sendMessage(ChatColor.RED + "下架结果未确认，请刷新商品状态或核对交易流水。");
        } else if (removed) {
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
        UUID viewer = player.getUniqueId();
        if (auctionService == null || !canUseMarket(player, AuthAccess.snapshot(viewer))
                || !packetBridge.isUiOpen(player, config.ui().auctionId())) return;
        if(restoreAuctionBrowse.remove(viewer))data=auctionBrowseContext.getOrDefault(viewer,data);
        // 无论从何处打开本 UI（Screen.open 或 openAuctionUi），都在首次列表请求时签发 token，
        // 保证购买/出价/取消按钮的 enable 条件 var.actionToken != '' 成立
        issueActionToken(player, config.ui().auctionId());
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        String category = data.size() > 2 ? data.get(2) : null;
        if (pendingMyAuctions.remove(player.getUniqueId())) category = "my";
        String keyword = data.size() > 3 ? data.get(3) : null;
        String pendingCategory=pendingAuctionCategories.remove(player.getUniqueId());
        if(pendingCategory!=null)category=pendingCategory;
        auctionBrowseContext.put(viewer, List.of("auction_list", String.valueOf(page), category == null ? "all" : category,
            keyword == null ? "" : keyword, "refresh"));
        enqueueAuctionPage(player, true, data.size() > 4 && "refresh".equals(data.get(4)));
    }

    private record AuctionPage(List<AuctionListing> listings, Set<Long> favorites, int page, int totalPages) {}

    private AuctionPage queryAuctionPage(UUID player, int page, String category, String keyword) {
        int totalCount;
        if ("ending".equals(category) || "participating".equals(category)) {
            totalCount=repository.countBrowseAuction(category,player,keyword);
        } else if ("favorites".equals(category)) {
            totalCount=repository.countFavoriteListings(player,keyword);
        } else if ("my".equals(category)) {
            totalCount = auctionService.countMy(player);
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
            listings=repository.browseAuction(category,player,keyword,page*AUCTION_PAGE_SIZE,AUCTION_PAGE_SIZE);
        } else if ("favorites".equals(category)) {
            listings=repository.favoriteListings(player,keyword,page*AUCTION_PAGE_SIZE,AUCTION_PAGE_SIZE);
        } else if ("my".equals(category)) {
            listings = auctionService.getMyListings(player, page, AUCTION_PAGE_SIZE);
        } else if (keyword != null && !keyword.isEmpty()) {
            listings = auctionService.searchListings(keyword, page, AUCTION_PAGE_SIZE);
        } else if (category != null && !category.isEmpty() && !"all".equals(category)) {
            listings = auctionService.getListingsByCategory(category, page, AUCTION_PAGE_SIZE);
        } else {
            listings = auctionService.getActiveListings(page, AUCTION_PAGE_SIZE);
        }

        return new AuctionPage(listings, new java.util.HashSet<>(repository.getFavorites(player)), page, totalPages);
    }

    private Map<String, Object> auctionPagePacket(Player player, AuctionPage result, String category, String keyword) {
        Map<String, Object> packet = buildAuctionPacket(player, result.listings(), result.favorites(), result.page(),
            result.totalPages(), category != null ? category : "all");
        packet.put("keyword", keyword == null ? "" : keyword);
        return packet;
    }

    private void refreshOpenAuctionBrowsers() {
        if (isStopping()) return;
        Runnable refresh = () -> {
            if (auctionService == null || packetBridge == null) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (packetBridge.isUiOpen(player, config.ui().auctionId())) refreshAuctionBrowser(player);
            }
        };
        if (Bukkit.isPrimaryThread()) refresh.run();
        else work.main(refresh);
    }

    private boolean needsAuctionCatchUp() {
        var channel = crossServerChannel;
        return channel != null && channel.requiresSharedStateCatchUp()
            && repository instanceof JdbcMarketRepository jdbc && jdbc.getDescriptor().isMysql();
    }

    private boolean hasOpenAuctionView() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAuctionUiOpen(player)) return true;
        }
        return false;
    }

    private void catchUpAuctionViews() {
        if (!needsAuctionCatchUp() || readingAuctionRevision || !hasOpenAuctionView()) return;
        var currentRepository = repository;
        readingAuctionRevision = true;
        work.async(() -> {
            long revision = -1;
            try { revision = currentRepository.auctionSettlementRevision(); }
            catch (RuntimeException failure) { logger.fine("[Market] 无法核对拍卖结算版本: " + failure.getMessage()); }
            long latest = revision;
            work.main(() -> {
                readingAuctionRevision = false;
                if (repository != currentRepository || latest < 0 || latest == observedAuctionRevision) return;
                refreshOpenAuctionBrowsers();
                boolean roomsQueued = true;
                if (auctionRooms != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!auctionRooms.request(player)) roomsQueued = false;
                    }
                }
                if (roomsQueued) observedAuctionRevision = latest;
            });
        });
    }

    private void refreshAuctionBrowser(Player player) {
        enqueueAuctionPage(player, false, false);
    }

    private void enqueueAuctionPage(Player player, boolean interactive, boolean invalidate) {
        UUID viewer = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(viewer);
        String ui = config.ui().auctionId();
        var context = auctionBrowseContext.get(viewer);
        if (context == null || !canUseMarket(player, session) || !packetBridge.isUiOpen(player, ui)) return;
        String token = tokenMap(ui).get(viewer);
        if (invalidate) pendingAuctionListInvalidations.add(viewer);
        int page = parseIntSafe(context.get(1), 0);
        String category = context.get(2), keyword = context.get(3);
        pageReads.submit(viewer, MarketPageReads.View.AUCTIONS,
            () -> auctionBrowseContext.get(viewer) == context && currentListView(player, session, ui, token),
            () -> readMarketList("listings", viewer, page, category, keyword, interactive && !needsAuctionCatchUp(),
                pendingAuctionListInvalidations.remove(viewer), () -> queryAuctionPage(viewer, page, category, keyword)),
            loaded -> sendMarketList(player, ui, "update", loaded, fresh -> auctionPagePacket(player, fresh, category, keyword)),
            () -> { if (interactive) player.sendMessage(ChatColor.YELLOW + "拍卖列表暂不可用，请稍后刷新"); });
    }

    private boolean currentListView(Player player, AuthSnapshot session, String ui, String token) {
        return canUseMarket(player, session) && packetBridge.isUiOpen(player, ui)
            && java.util.Objects.equals(tokenMap(ui).get(session.playerId()), token);
    }

    private <T> CachedList<T> readMarketList(String kind, UUID viewer, int page, String first, String second, boolean cache,
                                             boolean invalidate, Supplier<T> query) {
        if (invalidate && redisCache != null) redisCache.invalidateByPrefix("market:" + kind + ":" + viewer + ":");
        String key = cache && redisCache != null ? redisCache.versionedListPageKey(viewer,
            listCacheKey(kind, viewer, page, first, second)) : null;
        Map<String, Object> cached = key != null && !invalidate ? readListCache(key) : null;
        return new CachedList<>(cached, cached == null ? query.get() : null, key);
    }

    private <T> void sendMarketList(Player player, String ui, String handler, CachedList<T> result,
                                    java.util.function.Function<T, Map<String, Object>> render) {
        var packet = result.cached();
        if (packet == null) {
            packet = render.apply(result.fresh());
            cacheListPacketAsync(result.cacheKey(), packet);
        }
        sendUiPacket(player, ui, handler, packet);
    }

    private record CachedList<T>(Map<String, Object> cached, T fresh, String cacheKey) {}

    private void cacheListPacketAsync(String key, Map<String, Object> packet) {
        if (key == null || !listCacheWrites.tryAcquire()) return;
        var copy = new LinkedHashMap<>(packet);
        try {
            work.async(() -> {
                try { writeListCache(key, copy); }
                finally { listCacheWrites.release(); }
            });
        } catch (java.util.concurrent.RejectedExecutionException closing) {
            listCacheWrites.release();
        }
    }

    private void invalidateListCacheAsync(String prefix) {
        pendingListCacheInvalidations.add(prefix);
        dispatchCacheInvalidations();
    }

    private void dispatchCacheInvalidations() {
        if (isStopping() || !listCacheInvalidationRunning.compareAndSet(false, true)) return;
        try {
            work.async(() -> {
                try {
                    while (!isStopping() && !pendingListCacheInvalidations.isEmpty()) {
                        for (var prefix : List.copyOf(pendingListCacheInvalidations)) {
                            if (pendingListCacheInvalidations.remove(prefix)) redisCache.invalidateByPrefix(prefix);
                        }
                    }
                } finally {
                    listCacheInvalidationRunning.set(false);
                    if (!pendingListCacheInvalidations.isEmpty() && !isStopping()) dispatchCacheInvalidations();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closing) {
            listCacheInvalidationRunning.set(false);
        }
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
        return openAuctionRoom(player, id, null, 0);
    }

    private boolean openAuctionRoom(Player player, long id, @Nullable AuctionRoomSnapshot initial, long readAt) {
        if(auctionService==null || auctionRooms==null) {player.sendMessage(messages.get("player.auction-unavailable"));return false;}
        if(id<=0){player.sendMessage(ChatColor.RED+"拍品 ID 必须为正整数");return false;}
        auctionRooms.select(player,id,initial,readAt);
        if (!packetBridge.openUi(player,config.ui().auctionRoomId())) {
            auctionRooms.leave(player.getUniqueId());
            return false;
        }
        issueActionToken(player,config.ui().auctionRoomId());
        return true;
    }

    public void openAuctionCategory(Player player,String category) {
        pendingAuctionCategories.put(player.getUniqueId(),category);openAuctionUi(player);
    }

    private void refreshAuctionSurface(Player player,List<String> listRequest) {
        if(auctionRooms!=null && packetBridge.isUiOpen(player,config.ui().auctionRoomId()))auctionRooms.request(player);
        else if (packetBridge.isUiOpen(player, config.ui().auctionId())) {
            auctionBrowseContext.putIfAbsent(player.getUniqueId(), listRequest);
            refreshAuctionBrowser(player);
        }
    }

    private String auctionBidRevision(AuctionListing listing) {
        return AuctionBidTerms.revision(listing);
    }

    private String auctionTime(long time) {return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time));}

    private void sendAuctionRoom(Player player, @Nullable AuctionRoomSnapshot snapshot, @Nullable BigDecimal balance) {
        if (!canUseMarket(player, AuthAccess.snapshot(player.getUniqueId()))) return;
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
        packet.put("myBalance", balance == null ? "货币暂不可用" : currencyManager.format(lot.getCurrency(), balance));
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
        if(id<=0 || !isSelectedAuctionIntent(player,id))return;
        UUID viewer = player.getUniqueId();
        String ui = activeAuctionUi(player);
        var session = AuthAccess.snapshot(viewer);
        String token = tokenMap(ui).get(viewer);
        if (token == null || !session.admitted()) return;
        var state = auctionQuotes.computeIfAbsent(viewer, ignored -> new AuctionQuotes());
        var request = new AuctionQuoteRequest(player, session, ui, token, id, data.get(2));
        if (state.reading != null && request.equals(state.latest)) return;
        state.latest = request;
        state.issued = null;
        if (state.reading == null) startAuctionQuote(state, request);
    }

    private void startAuctionQuote(AuctionQuotes state, AuctionQuoteRequest request) {
        if (!currentAuctionQuote(state, request)) return;
        if (!auctionQuoteSlots.tryAcquire()) {
            sendUnavailableAuctionQuote(request, "报价请求较多，请稍后重试");
            return;
        }
        state.reading = request;
        readAuctionQuote(state, request, () -> repository.readAuctionBidTerms(request.listing()), terms -> {
            if (terms == null || !terms.canBid(request.viewer())) {
                sendUnavailableAuctionQuote(request, "该拍卖已结束、已下架或不能对自己的商品出价");
                finishAuctionQuote(state, request);
                return;
            }
            int scale = auctionService.currencyScale(terms.currency());
            BigDecimal minimum = auctionService.minimumBid(terms.startingBid(), terms.currentBid(), terms.highestBidder() != null, scale);
            double requested = parseDoubleSafe(request.amount(), Double.NaN);
            BigDecimal amount = Double.isFinite(requested) ? (requested == 0 ? minimum : BigDecimal.valueOf(requested)) : BigDecimal.ZERO;
            int steps = "step1".equals(request.amount()) ? 1 : "step2".equals(request.amount()) ? 2 : 0;
            if (steps > 0) {
                amount = minimum;
                for (int step = 0; step < steps; step++) {
                    amount = amount.add(amount.multiply(BigDecimal.valueOf(config.auction().minBidIncrementRatio()))
                        .max(BigDecimal.valueOf(config.auction().minBidIncrementAbsolute()))).setScale(scale, java.math.RoundingMode.CEILING);
                }
            }
            boolean own = request.viewer().equals(terms.highestBidder());
            BigDecimal held = own ? BigDecimal.valueOf(terms.currentBid()) : BigDecimal.ZERO;
            var quote = new CalculatedAuctionQuote(terms, minimum, amount, held, amount.subtract(held).max(BigDecimal.ZERO));
            boolean validAmount = amount.compareTo(minimum) >= 0
                && amount.compareTo(BigDecimal.valueOf(config.auction().maxPrice())) <= 0
                && amount.stripTrailingZeros().scale() <= scale;
            var bridge = validAmount ? currencyManager.bridge(terms.currency()) : null;
            if (bridge == null || !bridge.available()) {
                sendAuctionQuote(state, request, quote, false);
                return;
            }
            var backgroundBalance = bridge.prepareBackgroundBalance(request.player());
            if (backgroundBalance == null) {
                sendAuctionQuote(state, request, quote, bridge.balance(request.player()).compareTo(quote.debit()) >= 0);
            } else {
                readAuctionQuote(state, request, backgroundBalance,
                    balance -> sendAuctionQuote(state, request, quote, balance.compareTo(quote.debit()) >= 0));
            }
        });
    }

    private <T> void readAuctionQuote(AuctionQuotes state, AuctionQuoteRequest request, Supplier<T> query, Consumer<T> done) {
        try {
            work.async(() -> {
                try {
                    T result = query.get();
                    work.main(() -> {
                        if (!currentAuctionQuote(state, request)) {
                            finishAuctionQuote(state, request);
                            return;
                        }
                        try {
                            done.accept(result);
                        } catch (RuntimeException failure) {
                            failAuctionQuote(state, request, failure);
                        }
                    });
                } catch (RuntimeException failure) {
                    work.main(() -> failAuctionQuote(state, request, failure));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException stopping) {
            finishAuctionQuote(state, request);
        }
    }

    private boolean currentAuctionQuote(AuctionQuotes state, AuctionQuoteRequest request) {
        return auctionQuotes.get(request.viewer()) == state && state.latest == request && currentAuctionView(request);
    }

    private boolean currentAuctionView(AuctionQuoteRequest request) {
        Player player = request.player();
        return canUseMarket(player, request.session()) && request.ui().equals(activeAuctionUi(player))
            && packetBridge.isUiOpen(player, request.ui()) && request.token().equals(tokenMap(request.ui()).get(request.viewer()))
            && isSelectedAuctionIntent(player, request.listing());
    }

    private void failAuctionQuote(AuctionQuotes state, AuctionQuoteRequest request, RuntimeException failure) {
        logger.warning("读取拍卖报价失败 " + request.listing() + ": " + failure.getMessage());
        try {
            if (currentAuctionQuote(state, request)) {
                state.issued = null;
                sendUnavailableAuctionQuote(request, "报价暂不可用，请稍后重试");
            }
        } finally {
            finishAuctionQuote(state, request);
        }
    }

    private void finishAuctionQuote(AuctionQuotes state, AuctionQuoteRequest request) {
        if (state.reading != request) return;
        state.reading = null;
        auctionQuoteSlots.release();
        if (auctionQuotes.get(request.viewer()) == state && state.latest != request) startAuctionQuote(state, state.latest);
    }

    private void sendUnavailableAuctionQuote(AuctionQuoteRequest request, String reason) {
        sendUiPacket(request.player(), request.ui(), "auction_quote", new LinkedHashMap<>(Map.of(
            "packetId", config.ui().packetId(), "id", String.valueOf(request.listing()), "available", false,
            "revision", "", "reason", reason)));
    }

    private void sendAuctionQuote(AuctionQuotes state, AuctionQuoteRequest request, CalculatedAuctionQuote quote, boolean allowed) {
        AuctionBidTerms terms = quote.terms();
        if (!terms.canBid(request.viewer())) {
            sendUnavailableAuctionQuote(request, "该拍卖已结束、已下架或不能对自己的商品出价");
            finishAuctionQuote(state, request);
            return;
        }
        String currency = terms.currency();
        UUID highest = terms.highestBidder();
        String highestName = highest == null ? "暂无领先者" : request.viewer().equals(highest) ? "你正在领先"
            : terms.highestBidderName() == null || terms.highestBidderName().isBlank()
                ? highest.toString().substring(0, 8) : terms.highestBidderName();
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("id", String.valueOf(terms.id()));
        packet.put("available", true);
        packet.put("revision", terms.revision());
        packet.put("canSubmit", allowed);
        packet.put("amount", quote.amount().toPlainString());
        packet.put("minimum", quote.minimum().toPlainString());
        packet.put("current", highest == null ? "暂无出价" : currencyManager.format(currency, BigDecimal.valueOf(terms.currentBid())));
        packet.put("highest", highestName);
        packet.put("debit", currencyManager.format(currency, quote.debit()));
        packet.put("held", currencyManager.format(currency, quote.held()));
        packet.put("deadline", auctionTime(terms.expiresAt()));
        packet.put("protection", config.auction().endProtectionEnabled()
            ? "临近结束有效出价可延时；累计最多 " + config.auction().endMaxExtensionSeconds() + " 秒" : "未启用临近结束延时");
        packet.put("reason", allowed ? "确认后才扣款；报价不会自动提交出价" : "金额低于最低出价、精度不符、超出上限或余额不足，请修改后重新报价");
        if (allowed) state.issued = new IssuedAuctionQuote(request, quote.amount().doubleValue(), terms.revision(), System.nanoTime());
        sendUiPacket(request.player(), request.ui(), "auction_quote", packet);
        finishAuctionQuote(state, request);
    }

    private static final class AuctionQuotes {
        AuctionQuoteRequest latest;
        IssuedAuctionQuote issued;
        AuctionQuoteRequest reading;
    }

    private record AuctionQuoteRequest(Player player, AuthSnapshot session, String ui, String token, long listing, String amount) {
        UUID viewer() { return session.playerId(); }
    }

    private record CalculatedAuctionQuote(AuctionBidTerms terms, BigDecimal minimum, BigDecimal amount, BigDecimal held, BigDecimal debit) {}
    private record IssuedAuctionQuote(AuctionQuoteRequest request, double amount, String revision, long issuedAt) {}

    private boolean handleAuctionBidPacket(Player player, List<String> data) {
        if (auctionService == null) return false;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if(!isSelectedAuctionIntent(player,listingId))return false;
        double amount = data.size() > 2 ? parseDoubleSafe(data.get(2), 0) : 0;
        if (listingId <= 0 || !Double.isFinite(amount) || amount <= 0) return false;
        var quotes = auctionQuotes.get(player.getUniqueId());
        var quote = quotes == null ? null : quotes.issued;
        if (quote == null || quote.request().listing() != listingId || Double.compare(quote.amount(), amount) != 0
                || System.nanoTime() - quote.issuedAt() >= AUCTION_QUOTE_TTL || !currentAuctionQuote(quotes, quote.request())) {
            player.sendMessage(ChatColor.RED + "报价已过期或金额已变化，请重新报价后提交");
            return false;
        }
        quotes.issued = null;
        var request = quote.request();
        var confirmation = new AuctionService.BidConfirmation(listingId, amount, quote.revision(), quote.issuedAt() + AUCTION_QUOTE_TTL);
        List<String> refresh = List.of("auction_list", data.size() > 4 ? data.get(4) : "0",
            data.size() > 5 ? data.get(5) : "all", data.size() > 6 ? data.get(6) : "");
        auctionService.placeBid(player, confirmation, currencyManager, () -> currentAuctionView(request), result -> {
            if (result.success() && auctionRooms != null) auctionRooms.changed(listingId);
            if (!isCurrentMarketSession(player, request.session())) return;
            actionTokensInFlight.remove(request.viewer());
            boolean showing = currentAuctionView(request);
            if (result.success()) {
                if (showing) rotateToken(player, request.ui());
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.messages().auctionBidPlaced().replace("{amount}", String.valueOf(result.amount()))));
                if (result.extended()) player.sendMessage(ChatColor.GOLD + "本次有效出价触发临近结束延时，请查看最新截止时间。");
            } else {
                player.sendMessage(ChatColor.RED + result.error());
            }
            if (showing) {
                sendActionState(player, "auction_bid");
                refreshAuctionSurface(player, refresh);
            }
        });
        return true;
    }

    private boolean handleAuctionCancelPacket(Player player, List<String> data) {
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (listingId <= 0 || !isSelectedAuctionIntent(player, listingId)) return false;
        UUID viewer = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(viewer);
        String ui = activeAuctionUi(player);
        String token = tokenMap(ui).get(viewer);
        BooleanSupplier viewValid = () -> token != null && token.equals(tokenMap(ui).get(viewer))
            && ui.equals(activeAuctionUi(player)) && packetBridge.isUiOpen(player, ui) && isSelectedAuctionIntent(player, listingId);
        // 取消后刷新当前页列表
        String page = data.size() > 3 && data.get(3) != null ? data.get(3) : "0";
        String category = data.size() > 4 && data.get(4) != null ? data.get(4) : "all";
        String search = data.size() > 5 && data.get(5) != null ? data.get(5) : "";
        cancelOwnedAuction(player, listingId, session, viewValid, success -> {
            actionTokensInFlight.remove(viewer);
            if (!viewValid.getAsBoolean()) return;
            if (success) rotateToken(player, ui);
            sendActionState(player, "auction_cancel");
            refreshAuctionSurface(player, List.of("auction_list", page, category, search));
        });
        return true;
    }

    private boolean handleAuctionFavoritePacket(Player player, List<String> data) {
        if (auctionService == null) return false;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (listingId <= 0 || !isSelectedAuctionIntent(player, listingId)) return false;
        UUID viewer = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(viewer);
        if (!canUseMarket(player, session)) return false;
        if (!auctionFavoriteSlots.tryAcquire()) {
            player.sendMessage(ChatColor.YELLOW + "关注请求较多，请稍后重试");
            return false;
        }
        String ui = activeAuctionUi(player);
        String token = tokenMap(ui).get(viewer);
        String currentCategory = data.size() > 2 && data.get(2) != null ? data.get(2) : "all";
        String currentSearch = data.size() > 3 && data.get(3) != null ? data.get(3) : "";
        String page = data.size() > 4 && data.get(4) != null ? data.get(4) : "0";
        List<String> refresh = List.of("auction_list", page, currentCategory, currentSearch);
        Object action = new Object();
        asyncActionTickets.put(viewer, action);
        try {
            work.async(() -> {
                try {
                    if (!sameMarketAdmission(session)) {
                        work.main(() -> clearTrackedAction(viewer, action));
                        return;
                    }
                    var change = repository.toggleAuctionFavorite(viewer, listingId);
                    if (favoriteCommitted(change) && redisCache != null) {
                        redisCache.invalidateByPrefix("market:listings:" + viewer + ":");
                    }
                    work.main(() -> finishAuctionFavorite(player, session, listingId, ui, token, refresh, change, action));
                } catch (RuntimeException failure) {
                    logger.warning("拍卖关注提交结果未确认 #" + listingId + ": " + failure.getMessage());
                    work.main(() -> finishAuctionFavorite(player, session, listingId, ui, token, refresh, null, action));
                } finally {
                    auctionFavoriteSlots.release();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closing) {
            auctionFavoriteSlots.release();
            clearTrackedAction(viewer, action);
            return false;
        }
        return true;
    }

    private static boolean favoriteCommitted(MarketRepository.FavoriteChange change) {
        return change == MarketRepository.FavoriteChange.ADDED || change == MarketRepository.FavoriteChange.REMOVED;
    }

    private boolean clearTrackedAction(UUID player, Object action) {
        if (!asyncActionTickets.remove(player, action)) return false;
        actionTokensInFlight.remove(player);
        return true;
    }

    private void finishAuctionFavorite(Player player, AuthSnapshot session, long listingId, String ui, String token,
                                        List<String> refresh, MarketRepository.FavoriteChange change, Object action) {
        if (favoriteCommitted(change) && auctionRooms != null) auctionRooms.changed(listingId);
        UUID viewer = session.playerId();
        if (!clearTrackedAction(viewer, action) || !canUseMarket(player, session)) return;
        if (!ui.equals(activeAuctionUi(player)) || !packetBridge.isUiOpen(player, ui)) return;
        boolean sameView = java.util.Objects.equals(token, tokenMap(ui).get(viewer)) && isSelectedAuctionIntent(player, listingId);
        if (!sameView) {
            sendActionState(player, "auction_favorite");
            return;
        }
        if (change == null) {
            player.sendMessage(ChatColor.YELLOW + "关注结果未确认，请刷新页面后核对");
            rotateToken(player, ui);
        } else {
            player.sendMessage(switch (change) {
                case ADDED -> ChatColor.GREEN + "已关注，可在我的关注中查看。结束提醒随通知配置生效。";
                case REMOVED -> ChatColor.GRAY + "已取消关注";
                case LIMIT_REACHED -> ChatColor.YELLOW + "最多关注 100 件商品，请先取消部分关注";
                case MISSING -> ChatColor.YELLOW + "该拍卖已不存在";
            });
            if (favoriteCommitted(change)) rotateToken(player, ui);
        }
        sendActionState(player, "auction_favorite");
        if (change == null || favoriteCommitted(change)) refreshAuctionSurface(player, refresh);
    }

    private void handleShopListPacket(Player player, List<String> data) {
        if (shopService == null || !packetBridge.isUiOpen(player, config.ui().shopId())) return;
        UUID viewer = player.getUniqueId();
        String shopId = data.size() > 1 ? data.get(1) : "";
        if (shopId.isBlank()) {
            String pending = pendingShopSelections.remove(viewer);
            if (pending != null) shopId = pending;
        }
        var groups = shopService.getShops().values().stream()
            .filter(shop -> shop.permission().isBlank() || player.hasPermission(shop.permission())).toList();
        String selected = shopId;
        var group = groups.stream().filter(shop -> shop.id().equals(selected)).findFirst()
            .orElse(groups.isEmpty() ? null : groups.get(0));
        var effective = group == null ? null : shopService.getEffectiveShop(group.id());
        List<ShopService.ShopItem> visible = effective == null ? List.of() : effective.items().values().stream()
            .filter(item -> shopService.meetsConditions(player, item)).toList();
        int pages = Math.max(1, (visible.size() + SHOP_PAGE_SIZE - 1) / SHOP_PAGE_SIZE);
        int page = Math.max(0, Math.min(pages - 1, data.size() > 2 ? parseIntSafe(data.get(2), 0) : 0));
        int from = page * SHOP_PAGE_SIZE;
        var view = new ShopView(UUID.randomUUID().toString(), groups, group, page, pages,
            List.copyOf(visible.subList(from, Math.min(visible.size(), from + SHOP_PAGE_SIZE))));
        shopViews.put(viewer, view);
        var products = view.products.stream().map(shopService::availabilityRequest).toList();
        var store = repository;
        readShop(viewer, view, () -> store.shopAvailability(viewer, view.shopId(), products),
            (online, stock) -> sendUiPacket(online, config.ui().shopId(), "update", buildShopPacket(online, view, stock)),
            online -> sendUiPacket(online, config.ui().shopId(), "shop_error", new LinkedHashMap<>(
                Map.of("message", "商店库存暂不可用，请刷新后重试"))));
    }

    private void handleShopBuyPacket(Player player, List<String> data) {
        if (shopService == null || data.size() != 3 || !packetBridge.isUiOpen(player, config.ui().shopId())
            || !shopUuid(data.get(1)) || !shopUuid(data.get(2))) return;
        String quoteId = data.get(1), request = data.get(2);
        UUID viewer = player.getUniqueId();
        if (activeShopOrders.putIfAbsent(viewer, request) != null) {
            sendShopOrder(player, request, ShopService.BuyResult.pending("订单处理中，请稍后查询"));
            return;
        }
        var view = shopViews.get(viewer);
        var quote = view == null ? null : view.quote;
        if (quote == null || !quote.id().equals(quoteId) || !quote.request().equals(request)
            || System.nanoTime() - quote.issuedAt() >= SHOP_QUOTE_TTL_NANOS) {
            lookupShopOrder(player, quoteId, request);
            return;
        }
        view.quote = null;
        try {
            shopService.checkoutAsync(player, view.shopId(), quote.item().key(), quote.quantity(),
                quote.unitPrice(), quote.item(), quoteId, request,
                result -> finishShopRequest(player, view, request, result));
        } catch (RuntimeException exception) {
            logger.log(java.util.logging.Level.WARNING, "系统商店订单需核对 " + request, exception);
            finishShopRequest(player, view, request, ShopService.BuyResult.pending("订单结果待确认，请使用原请求查询"));
        }
    }

    private void lookupShopOrder(Player player, String quoteId, String request) {
        var viewer = player.getUniqueId();
        var store = repository;
        work.async(() -> {
            ShopService.BuyResult result;
            try {
                var existing = store.shopOrder(request);
                result = existing == null ? ShopService.BuyResult.fail("报价已过期或已失效，请重新报价")
                    : ShopService.matchesOrder(existing, viewer, quoteId) ? ShopService.orderResult(existing)
                    : ShopService.BuyResult.fail("订单请求无效");
            } catch (RuntimeException exception) {
                logger.log(java.util.logging.Level.WARNING, "系统商店订单查询失败 " + request, exception);
                result = ShopService.BuyResult.pending("订单暂不可查询，请使用原请求重试");
            }
            var reply = result;
            if (plugin.isEnabled()) work.main(() -> finishShopRequest(player, null, request, reply));
        });
    }

    private void finishShopRequest(Player player, @Nullable ShopView view, String request, ShopService.BuyResult result) {
        var viewer = player.getUniqueId();
        activeShopOrders.remove(viewer, request);
        if (shopService == null || !player.isOnline() || org.bukkit.Bukkit.getPlayer(viewer) != player) return;
        sendShopOrder(player, request, result);
        if (result.success() && view != null && shopViews.get(viewer) == view) {
            handleShopListPacket(player, List.of("shop_list", view.shopId(), Integer.toString(view.page)));
        }
    }

    private void sendShopOrder(Player player, String request, ShopService.BuyResult result) {
        String message = result.success() ? config.messages().shopBought().replace("{amount}",
            currencyManager.format(result.currency(), BigDecimal.valueOf(result.totalPrice()))) : result.error();
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        sendUiPacket(player, config.ui().shopId(), "shop_order", new LinkedHashMap<>(Map.of(
            "requestId", request, "success", result.success(), "pending", result.pending(), "message", message)));
    }

    private static boolean shopUuid(String value) {
        if (value.length() != 36) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private void closeShopView(UUID viewer, List<String> data) {
        var view = shopViews.get(viewer);
        if (view != null && data.size() == 2 && view.revision.equals(data.get(1))) {
            shopViews.remove(viewer);
            nextShopRead.remove(viewer);
        }
    }

    private void handleShopQuote(Player player, List<String> data) {
        if (shopService == null || !packetBridge.isUiOpen(player, config.ui().shopId())) return;
        if (data.size() != 6) {
            if (data.size() == 4) {
                player.sendMessage(ChatColor.RED + "系统商店界面版本过旧，请联系管理员更新后重新打开");
                sendUiPacket(player, config.ui().shopId(), "shop_quote", new LinkedHashMap<>(Map.of(
                    "shopId", data.get(1), "itemId", data.get(2), "available", false)));
            }
            return;
        }
        UUID viewer = player.getUniqueId();
        var view = shopViews.get(viewer);
        String shop = data.get(1), itemId = data.get(2), sequence = data.get(4);
        if (view == null || !view.revision.equals(data.get(5)) || !view.shopId().equals(shop)
            || !sequence.matches("[0-9]{1,10}")) return;
        view.quote = null;
        view.quoteSequence = sequence;
        var definition = shopService.getEffectiveShop(shop);
        var item = definition == null ? null : definition.items().get(itemId);
        if (item == null || view.products.stream().noneMatch(row -> row.key().equals(itemId))
            || (!definition.permission().isBlank() && !player.hasPermission(definition.permission()))
            || !shopService.meetsConditions(player, item)) {
            sendShopQuote(player, view, itemId, sequence, null, null, 0);
            return;
        }
        int wanted = Math.max(1, parseIntSafe(data.get(3), 1));
        var product = shopService.availabilityRequest(item);
        var store = repository;
        var balance = shopService.prepareQuoteBalance(player, item);
        readShop(viewer, view, () -> {
            var available = store.shopAvailability(viewer, shop, List.of(product)).get(itemId);
            return new ShopQuoteInputs(available, available == null || balance == null ? null : balance.get());
        }, (online, inputs) -> {
            if (!sequence.equals(view.quoteSequence)) return;
            var quote = inputs.availability() == null ? null : shopService.quote(online, item, inputs.availability(), inputs.balance());
            sendShopQuote(online, view, itemId, sequence, item, quote, wanted);
        }, online -> sendShopQuote(online, view, itemId, sequence, null, null, 0));
    }

    private record ShopQuoteInputs(@Nullable ShopCheckout.Availability availability, @Nullable BigDecimal balance) { }

    private void sendShopQuote(Player player, ShopView view, String itemId, String sequence,
                               @Nullable ShopService.ShopItem item, @Nullable ShopService.PurchaseQuote quote, int wanted) {
        if (!sequence.equals(view.quoteSequence)) return;
        Map<String, Object> packet = new LinkedHashMap<>(Map.of("shopId", view.shopId(), "itemId", itemId,
            "sequence", sequence, "available", quote != null));
        if (quote != null && item != null) {
            int quantity = Math.min(quote.maximum(), wanted);
            var issued = new ShopQuote(UUID.randomUUID().toString(), UUID.randomUUID().toString(), item,
                quote.unitPrice(), quantity, System.nanoTime());
            if (quantity > 0) view.quote = issued;
            packet.put("quoteId", quantity > 0 ? issued.id() : "");
            packet.put("requestId", quantity > 0 ? issued.request() : "");
            packet.put("quantity", quantity);
            packet.put("maximum", quote.maximum());
            packet.put("unitPrice", quote.unitPrice().toPlainString());
            packet.put("basePrice", currencyManager.format(quote.currency(), quote.basePrice()));
            packet.put("total", currencyManager.format(quote.currency(), quote.unitPrice().multiply(BigDecimal.valueOf(quantity))));
            packet.put("stockDisplay", shopService.availabilityRequest(item).stockMode().equals("unlimited") ? "无限" : String.valueOf(quote.stock()));
            packet.put("limitDisplay", item.limitPerPlayer() > 0 ? String.valueOf(quote.limit()) : "不限");
            packet.put("capacity", quote.capacity());
            var preview = quote.preview();
            packet.put("detailIcon", preview == null ? shopItemJson(player, item) : itemToJson(preview));
        }
        sendUiPacket(player, config.ui().shopId(), "shop_quote", packet);
    }

    private <T> void readShop(UUID viewer, ShopView view, java.util.function.Supplier<T> query,
                              java.util.function.BiConsumer<Player, T> reply, java.util.function.Consumer<Player> failed) {
        if (!activeShopReads.add(viewer)) {
            nextShopRead.put(viewer, () -> readShop(viewer, view, query, reply, failed));
            return;
        }
        work.async(() -> {
            T result = null;
            try { result = query.get(); }
            catch (RuntimeException exception) { logger.log(java.util.logging.Level.WARNING, "系统商店读取失败", exception); }
            T value = result;
            work.main(() -> {
                try {
                    Player player = org.bukkit.Bukkit.getPlayer(viewer);
                    if (shopService != null && shopViews.get(viewer) == view && player != null
                        && packetBridge.isUiOpen(player, config.ui().shopId())) {
                        if (value != null) reply.accept(player, value);
                        else failed.accept(player);
                    }
                } catch (RuntimeException exception) {
                    logger.log(java.util.logging.Level.WARNING, "系统商店展示失败", exception);
                    Player player = org.bukkit.Bukkit.getPlayer(viewer);
                    if (player != null && shopViews.get(viewer) == view) failed.accept(player);
                } finally {
                    activeShopReads.remove(viewer);
                    Runnable next = nextShopRead.remove(viewer);
                    if (next != null) next.run();
                }
            });
        });
    }

    private static final class ShopView {
        final String revision;
        final List<ShopService.ShopDefinition> groups;
        final ShopService.ShopDefinition group;
        final int page;
        final int pages;
        final List<ShopService.ShopItem> products;
        String quoteSequence;
        ShopQuote quote;

        ShopView(String revision, List<ShopService.ShopDefinition> groups, @Nullable ShopService.ShopDefinition group,
                 int page, int pages, List<ShopService.ShopItem> products) {
            this.revision = revision;
            this.groups = groups;
            this.group = group;
            this.page = page;
            this.pages = pages;
            this.products = products;
        }

        String shopId() { return group == null ? "" : group.id(); }
    }

    private record ShopQuote(String id, String request, ShopService.ShopItem item, BigDecimal unitPrice,
                             int quantity, long issuedAt) { }

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

    private String listCacheKey(String kind, UUID viewer, int page, String first, String second) {
        String query = page + "\n" + (first == null ? "" : first) + "\n" + (second == null ? "" : second);
        return "market:" + kind + ":" + viewer + ":"
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
                "recycle_all", "recycle_single",
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

    private Map<String, Object> buildAuctionPacket(Player player, List<AuctionListing> listings, Set<Long> favoriteIds,
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

    private Map<String, Object> buildShopPacket(Player player, ShopView view, Map<String, ShopCheckout.Availability> availability) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        packet.put("isAdmin", isShopAdmin(player));
        packet.put("revision", view.revision);
        packet.put("page", view.page);
        packet.put("pages", view.pages);

        Map<String, Object> shops = new LinkedHashMap<>();
        Map<String, Object> shopTexts = new LinkedHashMap<>();
        int shopIndex = 0;
        for (var group : view.groups) {
            String key = Integer.toString(shopIndex++);
            shops.put(key, Map.of("id", group.id()));
            packet.put("shopIconJson" + key, iconResolver.resolveItemJson(player, group.icon()));
            shopTexts.put(key, "&f" + group.displayName());
        }
        packet.put("shops", shops);
        packet.put("shopTexts", shopTexts);
        packet.put("shopId", view.shopId());
        packet.put("shopName", view.group == null ? "" : view.group.displayName());

        Map<String, Object> items = new LinkedHashMap<>();
        int idx = 0;
        for (var item : view.products) {
            String key = Integer.toString(idx++);
            var stock = availability.get(item.key());
            var product = shopService.availabilityRequest(item);
            BigDecimal price = shopService.displayBuyPriceDecimal(player, item);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", item.key());
            row.put("name", ChatColor.stripColor(MessageProvider.translateColors(item.displayName())));
            row.put("price", price.stripTrailingZeros().toPlainString());
            row.put("priceRaw", price.toPlainString());
            row.put("currencyName", currencyDisplayName(item.currency()));
            row.put("stock", product.stockMode().equals("unlimited") ? "无限" : stock == null ? "—" : String.valueOf(stock.stock()));
            row.put("limitText", item.limitPerPlayer() > 0 && stock != null ? "可购 " + stock.limit() + " 个" : "");
            items.put(key, row);
            packet.put("shopItemJson" + key, shopCardIcon(player, item));
        }
        packet.put("items", items);
        packet.put("maxShopCount", shops.size());
        packet.put("maxItemCount", items.size());
        return packet;
    }

    private String shopCardIcon(Player player, ShopService.ShopItem item) {
        if (item.icon() != null) return iconResolver.resolveItemJson(player, item.icon());
        if (item.type() == ShopService.ShopItemType.COMMAND) return itemToJson(new ItemStack(org.bukkit.Material.COMMAND_BLOCK));
        if ("minecraft".equalsIgnoreCase(item.item().source())) {
            var material = org.bukkit.Material.matchMaterial(item.item().id());
            if (material != null && material.isItem() && !material.isAir()) return itemToJson(new ItemStack(material));
        }
        return shopItemJson(player, item);
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

    public void clearExpired(java.util.function.IntConsumer reply) {
        work.async(() -> {
            try {
                int cleared = scanExpiredListings();
                if (plugin.isEnabled()) work.main(() -> reply.accept(cleared));
            } catch (RuntimeException exception) {
                logger.log(java.util.logging.Level.WARNING, "手动处理到期挂单失败", exception);
                if (plugin.isEnabled()) work.main(() -> reply.accept(-1));
            }
        });
    }

    private int scanExpiredListings() {
        return (auctionService == null ? 0 : auctionService.triggerExpiredProcessing())
            + (playerShopService == null ? 0 : playerShopService.triggerExpiredProcessing());
    }

    public void adminRemoveListing(CommandSender sender, long listingId, boolean playerShop, BiConsumer<Boolean, Throwable> reply) {
        Player player = sender instanceof Player actor ? actor : null;
        AuthSnapshot session = player == null ? null : AuthAccess.snapshot(player.getUniqueId());
        UUID actor = session == null ? new UUID(0, 0) : session.playerId();
        BooleanSupplier authorized = () -> sender.hasPermission("arcartxsuite.market.admin")
            && (player == null || isCurrentMarketSession(player, session));
        if ((playerShop ? playerShopService == null : auctionService == null) || !authorized.getAsBoolean()) {
            reply.accept(false, null);
            return;
        }
        var result = playerShop ? playerShopService.cancelListing(actor, listingId, true, authorized)
            : auctionService.cancelListing(actor, listingId, true, authorized);
        result.whenComplete((success, failure) -> work.main(() -> {
            if (Boolean.TRUE.equals(success)) {
                if (!playerShop) refreshCancelledAuction(listingId);
                else if (playerStores != null) playerStores.changed();
            }
            if (authorized.getAsBoolean()) reply.accept(Boolean.TRUE.equals(success), failure);
        }));
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
        if (isStopping()) return;
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
        if (message.startsWith("LISTING_SETTLED:") || message.startsWith("LISTING_CANCELLED:")) refreshOpenAuctionBrowsers();
        if(message.startsWith("RECYCLE_PROTECTION_CHANGED:") && management!=null){
            try{java.util.UUID owner=java.util.UUID.fromString(message.substring("RECYCLE_PROTECTION_CHANGED:".length()));
                Runnable refresh=()->{if(management!=null)management.protection().invalidate(owner);};
                if(org.bukkit.Bukkit.isPrimaryThread())refresh.run();else work.main(refresh);
            }catch(IllegalArgumentException ignored){}
        }
        // 拍卖消息不再重复写入竞价：写库、押金与延时由出价节点原子处理。

        // 跨服限购同步：其他服购买后广播，本地 upsert 限购记录
        if (message.startsWith("SHOP_LIMIT_UPDATE:") && repository instanceof JdbcMarketRepository jdbc
            && !jdbc.getDescriptor().isMysql()) {
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
            invalidateListCacheAsync("market:listings:");
        }

        if(message.startsWith("PSHOP_") && playerStores!=null)playerStores.changed();
        // 玩家商店跨服同步：收到广播后失效本地缓存
        if (message.startsWith("PSHOP_")
                && redisCache != null
                && redisCache.isAvailable()) {
            invalidateListCacheAsync("market:pshop:");
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

    private java.util.concurrent.CompletionStage<Boolean> publishSettlementRefresh(String payload) {
        var channel = crossServerChannel;
        return channel == null ? java.util.concurrent.CompletableFuture.completedFuture(false) : channel.publishWithReceipt(payload);
    }

    private boolean publishEvent(String topic, Player player, Map<String, String> payload) {
        if (eventBusProvider == null) return false;
        EventBusCapability bus = eventBusProvider.get();
        if (bus != null) {
            bus.publish(topic, player, payload);
            return true;
        }
        return false;
    }

    private java.util.concurrent.CompletionStage<Boolean> publishAuctionEvent(String topic, Player player, Map<String, String> payload) {
        EventBusCapability bus = eventBusProvider == null ? null : eventBusProvider.get();
        if (bus == null) return java.util.concurrent.CompletableFuture.completedFuture(false);
        return bus.publishConfirmed(topic, player, payload);
    }
}
