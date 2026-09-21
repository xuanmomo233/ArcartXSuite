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
    }

    public void start(java.io.File dataFolder, JdbcMarketRepository repository) throws Exception {
        // 初始化存储
        this.repository = repository;
        repository.initialize();

        // 初始化 Redis
        redisCache = new RedisMarketCache(config.redis(), logger);
        redisCache.initialize();

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

        // 启动子服务
        if (config.auction().enabled()) {
            auctionService = new AuctionService(plugin, config.auction(), config.messages(),
                repository, redisCache, crossServerPublisher, currencyManager, itemSerializer, logger, vanillaItemNameBridge, itemRewardDispatcher, itemMatcher);
            auctionService.setEventPublisher(this::publishEvent);
            auctionService.start(config.schedulerIntervalTicks());
        }

        if (config.shop().enabled()) {
            shopService = new ShopService(plugin, config.shop(), config.messages(),
                repository, currencyManager, itemSourceRegistry, scriptConditionEvaluator, dataFolder, logger);
            // 注入跨服限购广播回调
            shopService.setLimitBroadcaster((playerUuid, shopId, itemId, newCount, timestamp, resetTime) -> {
                if (crossServerChannel != null && crossServerChannel.isActive()) {
                    crossServerChannel.publish("SHOP_LIMIT_UPDATE:" + playerUuid + ":" + shopId + ":"
                        + itemId + ":" + newCount + ":" + timestamp + ":" + resetTime);
                }
            });
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
                repository, currencyManager, itemSourceRegistry, dataFolder, logger);
            recycleService.start();
        }

        if (config.playerShop().enabled()) {
            playerShopService = new PlayerShopService(plugin, config.playerShop(), config.messages(),
                repository, redisCache, crossServerPublisher, currencyManager, itemSerializer, logger,
                vanillaItemNameBridge, itemRewardDispatcher, itemMatcher);
            playerShopService.start(config.schedulerIntervalTicks());
        }

        logger.info("全球市场服务已启动");
    }

    public void shutdown() {
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
        if (startingBid < 0) {
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
            rotateToken(player, config.ui().auctionId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionCancelled()));
        } else {
            player.sendMessage(messages.get("player.cancel-failed"));
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
        if (shopService == null) {
            player.sendMessage(messages.get("player.shop-unavailable"));
            return;
        }
        var result = shopService.buy(player, shopId, itemId, amount);
        if (result.success()) {
            rotateToken(player, config.ui().shopId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().shopBought().replace("%amount%",
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
        var result = recycleService.recycleBatch(player);
        if (result.success()) {
            rotateToken(player, config.ui().recycleId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().recycleSuccess().replace("%amount%",
                    formatRecycleEarnings(result.earningsByCurrency()))));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', result.error()));
        }
    }

    // ─── UI 打开 ────────────────────────────────────────────

    public void openAuctionUi(Player player) {
        packetBridge.openUi(player, config.ui().auctionId());
        issueActionToken(player, config.ui().auctionId());
    }

    public void openAuctionSellUi(Player player) {
        packetBridge.openUi(player, AUCTION_SELL_UI_ID);
        issueActionToken(player, AUCTION_SELL_UI_ID);
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
    public void openShopUi(Player player, @Nullable String shopId) {
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

    public void openRecycleUi(Player player) {
        packetBridge.openUi(player, config.ui().recycleId());
        issueActionToken(player, config.ui().recycleId());
    }

    public void openHistoryUi(Player player) {
        packetBridge.openUi(player, config.ui().historyId());
    }

    public void openSellUi(Player player) {
        packetBridge.openUi(player, config.ui().sellId());
        issueActionToken(player, config.ui().sellId());
    }

    public void openPlayerShopUi(Player player) {
        packetBridge.openUi(player, config.ui().playerShopId());
        issueActionToken(player, config.ui().playerShopId());
    }

    public void openMyListingsUi(Player player) {
        packetBridge.openUi(player, config.ui().auctionId());
        issueActionToken(player, config.ui().auctionId());
    }

    // ─── 客户端包处理 ────────────────────────────────────────

    /** 判断该包是否归本模块处理（供模块层在切主线程前快速判定）。 */
    public boolean ownsPacket(String packetId) {
        return config.ui().packetId().equals(packetId);
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (!config.ui().packetId().equals(packetId)) return false;
        if (data.isEmpty()) return false;

        // data[0] = action, 后续字段为参数
        String action = data.get(0);
        // 商店编辑系列包全部要求管理员权限（UI 内按钮不可信）
        if (action.startsWith("shop_edit") && !isShopAdmin(player)) return true;
        if (requiresActionToken(action) && !beginActionToken(player, action, data)) return true;
        switch (action) {
            case "auction_list" -> handleAuctionListPacket(player, data);
            case "auction_bid" -> handleAuctionBidPacket(player, data);
            case "auction_cancel" -> handleAuctionCancelPacket(player, data);
            case "auction_sell_ui_open" -> openAuctionSellUi(player);
            case "auction_sell_open" -> handleAuctionSellOpenPacket(player);
            case "auction_list_create" -> handleAuctionListCreatePacket(player, data);
            case "auction_favorite" -> handleAuctionFavoritePacket(player, data);
            case "shop_list" -> handleShopListPacket(player, data);
            case "shop_buy" -> handleShopBuyPacket(player, data);
            case "recycle_all" -> handleRecycleAll(player);
            case "recycle_single" -> handleRecycleSinglePacket(player, data);
            case "recycle_preview" -> handleRecyclePreviewPacket(player);
            case "history_list" -> handleHistoryListPacket(player, data);
            case "sell_open" -> handleSellOpenPacket(player);
            case "sell_ui_open" -> openSellUi(player);
            case "auction_ui_open" -> openAuctionUi(player);
            case "pshop_ui_open" -> openPlayerShopUi(player);
            case "shop_ui_open" -> openShopUi(player);
            case "shop_edit_ui_open" -> openShopEditUi(player, data.size() > 1 ? data.get(1) : null);
            case "shop_edit_list" -> handleShopEditList(player);
            case "shop_edit_shop" -> handleShopEditShop(player, data);
            case "shop_edit_item" -> handleShopEditItem(player, data);
            case "shop_edit_pick" -> handleShopEditPick(player, data);
            case "shop_edit_shop_save" -> handleShopEditShopSave(player, data);
            case "shop_edit_shop_delete" -> handleShopEditShopDelete(player, data);
            case "shop_edit_item_save" -> handleShopEditItemSave(player, data);
            case "shop_edit_item_delete" -> handleShopEditItemDelete(player, data);
            case "recycle_ui_open" -> openRecycleUi(player);
            case "history_ui_open" -> openHistoryUi(player);
            case "pshop_list" -> handlePlayerShopListPacket(player, data);
            case "pshop_sellers" -> handlePlayerShopSellersPacket(player, data);
            case "pshop_buy" -> handlePlayerShopBuyPacket(player, data);
            case "pshop_cancel" -> handlePlayerShopCancelPacket(player, data);
            case "pshop_create" -> handlePlayerShopCreatePacket(player, data);
            default -> { return false; }
        }
        if (requiresActionToken(action)) {
            actionTokensInFlight.remove(player.getUniqueId());
        }
        return true;
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
        openPlayerShopUi(player);
        handlePlayerShopListPacket(player, List.of("pshop_list", "0", ""));
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
        // 无论从何处打开本 UI（Screen.open 或 openPlayerShopUi），都在首次列表请求时签发 token，
        // 保证购买/下架按钮的 enable 条件 var.actionToken != '' 成立
        issueActionToken(player, config.ui().playerShopId());
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        String keyword = data.size() > 2 ? data.get(2) : null;
        String sellerUuid = data.size() > 3 ? data.get(3) : null;

        List<PlayerShopListing> listings;
        int totalCount;
        if (sellerUuid != null && !sellerUuid.isEmpty() && !"all".equals(sellerUuid)) {
            try {
                UUID seller = UUID.fromString(sellerUuid);
                listings = playerShopService.getListingsBySeller(seller, page, PSHOP_PAGE_SIZE);
                totalCount = playerShopService.countBySeller(seller);
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
        sendUiPacket(player, config.ui().playerShopId(), "pshop_list", buildPlayerShopPacket(player, listings, page, totalPages, keyword, sellerUuid));
    }

    private void handlePlayerShopSellersPacket(Player player, List<String> data) {
        if (playerShopService == null) return;
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        var sellers = playerShopService.getSellers(page, PSHOP_SELLERS_PAGE_SIZE);
        int totalCount = playerShopService.countSellers();
        int totalPages = Math.max(1, (totalCount + PSHOP_SELLERS_PAGE_SIZE - 1) / PSHOP_SELLERS_PAGE_SIZE);
        sendUiPacket(player, config.ui().playerShopId(), "pshop_sellers", buildPlayerShopSellersPacket(player, sellers, page, totalPages));
    }

    private void handlePlayerShopBuyPacket(Player player, List<String> data) {
        if (playerShopService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        var result = playerShopService.purchase(player, listingId);
        if (!result.success()) {
            player.sendMessage(ChatColor.RED + result.error());
            return;
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            config.messages().playerShopBought().replace("%amount%",
                currencyManager.format(result.currency(), BigDecimal.valueOf(result.price())))));
        // 刷新列表
        handlePlayerShopListPacket(player, List.of("pshop_list", "0", ""));
    }

    private void handlePlayerShopCancelPacket(Player player, List<String> data) {
        if (playerShopService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (playerShopService.cancelListing(player, listingId)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().playerShopCancelled()));
        } else {
            player.sendMessage(ChatColor.RED + "下架失败");
        }
        handlePlayerShopListPacket(player, List.of("pshop_list", "0", ""));
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
            packet.put("itemJson" + key, pshopItemJson(listing));
            item.put("itemDisplayName", listing.getItemDisplayName());
            item.put("price", listing.getPrice());
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
        packet.put("maxItemCount", PSHOP_PAGE_SIZE);
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
            player.sendMessage(messages.get("player.auction-unavailable"));
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
        if (auctionService == null) return;
        // 无论从何处打开本 UI（Screen.open 或 openAuctionUi），都在首次列表请求时签发 token，
        // 保证购买/出价/取消按钮的 enable 条件 var.actionToken != '' 成立
        issueActionToken(player, config.ui().auctionId());
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        String category = data.size() > 2 ? data.get(2) : null;
        String keyword = data.size() > 3 ? data.get(3) : null;

        int totalCount;
        if ("my".equals(category)) {
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
        if ("my".equals(category)) {
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
        sendUiPacket(player, config.ui().auctionId(), "update", packet);
    }

    private void handleAuctionBidPacket(Player player, List<String> data) {
        if (auctionService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        double amount = data.size() > 2 ? parseDoubleSafe(data.get(2), 0) : 0;
        if (listingId <= 0 || amount <= 0) return;
        var result = auctionService.placeBid(player, listingId, amount);
        if (result.success()) {
            rotateToken(player, config.ui().auctionId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().auctionBidPlaced().replace("%amount%", String.valueOf(result.amount()))));
        } else {
            player.sendMessage(ChatColor.RED + result.error());
        }
    }

    private void handleAuctionCancelPacket(Player player, List<String> data) {
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (listingId <= 0) return;
        cancelListing(player, listingId);
        // 取消后刷新当前页列表
        String page = data.size() > 3 && data.get(3) != null ? data.get(3) : "0";
        String category = data.size() > 4 && data.get(4) != null ? data.get(4) : "all";
        String search = data.size() > 5 && data.get(5) != null ? data.get(5) : null;
        handleAuctionListPacket(player, List.of("auction_list", page, category, search));
    }

    private void handleAuctionFavoritePacket(Player player, List<String> data) {
        if (auctionService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (listingId <= 0) return;
        auctionService.toggleFavorite(player.getUniqueId(), listingId);
        // 保留玩家当前分类与搜索条件（由客户端随包附带），避免收藏后列表跳回"全部"
        String currentCategory = data.size() > 2 && data.get(2) != null ? data.get(2) : "all";
        String currentSearch = data.size() > 3 && data.get(3) != null ? data.get(3) : "";
        handleAuctionListPacket(player, List.of("auction_list", "0", currentCategory, currentSearch));
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
        buyFromShop(player, shopId, itemId, amount);
        // 购买后刷新商店 UI
        if (shopService != null) {
            Map<String, Object> packet = buildShopPacket(player, shopId);
            sendUiPacket(player, config.ui().shopId(), "update", packet);
        }
    }

    private void handleRecycleAll(Player player) {
        if (!config.recycle().batchRecycleEnabled()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().batchRecycleDisabled()));
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
        var result = recycleService.recycle(player, item);
        if (result.success()) {
            rotateToken(player, config.ui().recycleId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().recycleSuccess().replace("%amount%",
                    formatRecycleEarnings(result.earningsByCurrency()))));
        } else {
            player.sendMessage(ChatColor.RED + result.error());
        }
        // 回收后刷新 UI
        handleRecyclePreviewPacket(player);
    }

    private void handleRecyclePreviewPacket(Player player) {
        Map<String, Object> packet = buildRecyclePacket(player);
        sendUiPacket(player, config.ui().recycleId(), "update", packet);
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
        if (item == null || item.getType().isAir()) return;
        ItemStack single = item.clone();
        single.setAmount(1);
        String json = itemToJson(single);
        if (json.isBlank()) return;

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
            new ShopService.ShopEdit(displayName, permission, tags, icon));
        sendEditResult(player, error == null, error == null ? "商店已保存" : error, "shop_save");
        if (error == null) {
            rotateToken(player, shopEditUiId());
            sendUiPacket(player, shopEditUiId(), "edit_list", buildShopEditListPacket(player));
            sendUiPacket(player, shopEditUiId(), "edit_shop", buildShopEditShopPacket(player, shopId));
        }
    }

    private void handleShopEditShopDelete(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1).trim() : "";
        String error = shopService.deleteShop(shopId);
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
        double buyPrice = clampDouble(parseDoubleSafe(jsonString(form, "buyPrice"), 0), 0, 1_000_000_000);
        double sellPrice = clampDouble(parseDoubleSafe(jsonString(form, "sellPrice"), 0), 0, 1_000_000_000);
        String currency = capStr(jsonString(form, "currency"), 64);
        String stockMode = normalizeStockMode(jsonString(form, "stockMode"));
        int stockAmount = clampInt(parseIntSafe(jsonString(form, "stockAmount"), 0), 0, 1_000_000);
        int limitPerPlayer = clampInt(parseIntSafe(jsonString(form, "limitPerPlayer"), 0), 0, 1_000_000);
        String limitReset = normalizeLimitReset(jsonString(form, "limitReset"));
        List<String> conditionLines = splitLines(jsonString(form, "conditions"), 64, 512);
        List<String> commands = splitLines(jsonString(form, "commands"), 64, 256);
        Map<String, Double> discount = parseDiscounts(jsonString(form, "discounts"));
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
                if (material == null) {
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
            source, itemId, nbt, icon);
        String error = shopService.saveShopItem(shopId, create, edit);
        sendEditResult(player, error == null, error == null ? "商品已保存" : error, "item_save");
        if (error == null) {
            rotateToken(player, shopEditUiId());
            sendUiPacket(player, shopEditUiId(), "edit_shop", buildShopEditShopPacket(player, shopId));
            sendUiPacket(player, shopEditUiId(), "edit_item", buildShopEditItemPacket(player, shopId, key));
        }
    }

    private void handleShopEditItemDelete(Player player, List<String> data) {
        if (shopService == null) return;
        String shopId = data.size() > 1 ? data.get(1).trim() : "";
        String key = data.size() > 2 ? data.get(2).trim() : "";
        String error = shopService.deleteShopItem(shopId, key);
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

    /** 商店详情（编辑用，读取静态定义，不含动态商品）。 */
    private Map<String, Object> buildShopEditShopPacket(Player player, String shopId) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());
        ShopService.ShopDefinition shop = shopService.getShop(shopId);
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
        ShopService.ShopDefinition shop = shopService.getShop(shopId);
        ShopService.ShopItem item = shop == null ? null : shop.items().get(key);
        packet.put("found", item != null);
        packet.put("shopId", shopId);
        packet.put("key", key);
        if (item == null) return packet;
        packet.put("type", item.type() == ShopService.ShopItemType.COMMAND ? "command" : "item");
        packet.put("displayName", item.displayName());
        packet.put("buyPrice", formatPrice(item.buyPrice()));
        packet.put("sellPrice", formatPrice(item.sellPrice()));
        packet.put("currency", item.currency());
        packet.put("stockMode", item.stockMode());
        packet.put("stockAmount", String.valueOf(item.stockAmount()));
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
            discounts.append(entry.getKey()).append('=').append(formatPrice(entry.getValue()));
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

    private boolean requiresActionToken(String action) {
        return switch (action) {
            case "auction_bid", "auction_cancel", "auction_list_create",
                "shop_buy", "recycle_all", "recycle_single",
                "pshop_buy", "pshop_cancel", "pshop_create",
                "shop_edit_shop_save", "shop_edit_shop_delete",
                "shop_edit_item_save", "shop_edit_item_delete" -> true;
            default -> false;
        };
    }

    private boolean requiresUiToken(String uiId) {
        return uiId.equals(config.ui().auctionId()) || uiId.equals(AUCTION_SELL_UI_ID)
            || uiId.equals(config.ui().shopId()) || uiId.equals(config.ui().recycleId())
            || uiId.equals(config.ui().sellId()) || uiId.equals(config.ui().playerShopId())
            || uiId.equals(shopEditUiId());
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
        String uiId = switch (action) {
            case "auction_list_create" -> AUCTION_SELL_UI_ID;
            case "pshop_create" -> config.ui().sellId();
            case "pshop_buy", "pshop_cancel" -> config.ui().playerShopId();
            case "shop_buy" -> config.ui().shopId();
            case "recycle_all", "recycle_single" -> config.ui().recycleId();
            case "shop_edit_shop_save", "shop_edit_shop_delete",
                "shop_edit_item_save", "shop_edit_item_delete" -> shopEditUiId();
            default -> config.ui().auctionId();
        };
        int index = switch (action) {
            case "auction_cancel" -> 2;
            case "auction_bid" -> 3;
            case "auction_list_create" -> 6;
            case "shop_buy" -> 4;
            case "recycle_single" -> 2;
            case "pshop_buy" -> 2;
            case "pshop_cancel" -> 2;
            case "pshop_create" -> 7;
            case "shop_edit_shop_delete" -> 2;
            case "shop_edit_item_delete" -> 3;
            case "shop_edit_shop_save", "shop_edit_item_save" -> 4;
            default -> 1;
        };
        String supplied = data.size() > index ? data.get(index) : "";
        return packetBridge != null && packetBridge.isUiOpen(player, uiId)
            && tokenMap(uiId).get(player.getUniqueId()) != null
            && tokenMap(uiId).get(player.getUniqueId()).equals(supplied)
            && actionTokensInFlight.add(player.getUniqueId());
    }

    private Map<String, Object> buildAuctionSellPacket(Player player) {
        Map<String, Object> packet = new LinkedHashMap<>();
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
            double currentHighest = listing.getCurrentBid() > 0 ? listing.getCurrentBid() : listing.getStartingBid();
            double minIncrement = Math.max(currentHighest * config.auction().minBidIncrementRatio(), config.auction().minBidIncrementAbsolute());
            double minBid = listing.getCurrentBid() > 0 ? currentHighest + minIncrement : listing.getStartingBid();
            boolean canBid = listing.getType() == AuctionListing.ListingType.AUCTION || listing.getType() == AuctionListing.ListingType.BOTH;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", listing.getId());
            row.put("name", listing.getItemDisplayName());
            row.put("seller", listing.getSellerName());
            row.put("price", formatPrice(listing.getStartingBid()));
            row.put("minBid", minBid);
            row.put("type", listing.getType().name());
            row.put("canBuyNow", false);
            row.put("canBid", canBid);
            row.put("isFavorite", favoriteIds.contains(listing.getId()));
            row.put("bid", listing.getCurrentBid() > 0 ? formatPrice(listing.getCurrentBid()) : formatPrice(listing.getStartingBid()));
            row.put("timeLeft", formatTimeLeft(listing.getExpiresAt()));
            row.put("currencyName", currencyDisplayName(listing.getCurrency()));
            row.put("message", listing.getMessage());
            row.put("canCancel", listing.getSeller().equals(player.getUniqueId()));
            listingsMap.put(key, row);
            listingTexts.put(key, listing.getItemDisplayName());

            // 物品 JSON（flat 字段）
            packet.put("itemJson" + i, listingItemJson(listing));
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
                    row.put("name", shopItem.displayName());
                    row.put("price", formatPrice(shopItem.buyPrice()));
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

    private Map<String, Object> buildRecyclePacket(Player player) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());

        double multiplier = recycleService != null ? recycleService.getMultiplier(player) : 1.0;
        Map<String, Double> totalsByCurrency = new LinkedHashMap<>();
        Map<String, Object> recyclables = new LinkedHashMap<>();
        int idx = 0;
        for (int slot = 0; slot < 36 && recycleService != null; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            double unitPrice = recycleService.getRecyclePrice(item, player);
            if (unitPrice <= 0) continue;
            String key = Integer.toString(idx);
            double total = unitPrice * item.getAmount();
            String itemCurrency = recycleService.getRecycleCurrency(item);
            if (itemCurrency != null) totalsByCurrency.merge(itemCurrency, total, Double::sum);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", slot);
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
        packet.put("batchConfirm", config.recycle().batchConfirm());
        return packet;
    }

    private Map<String, Object> buildHistoryPacket(Player player, int page) {
        Map<String, Object> packet = new LinkedHashMap<>();
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
                case "EXPIRED" -> {
                    typeText = "过期退还";
                    typeColor = "&7";
                    counterpart = "";
                }
                case "CANCELLED" -> {
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
            row.put("counterpart", counterpart);
            row.put("amount", formatPrice(h.price()) + " " + currencyDisplayName(h.currency()));
            row.put("time", TIME_FMT.format(new Date(h.timestamp())));
            records.put(key, row);

            // 物品 JSON（flat 字段）
            packet.put("historyItemJson" + i, historyItemJson(h));
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
            return itemStackBridge.itemToJson(item).orElse("");
        }
        return "";
    }

    private String listingItemJson(AuctionListing listing) {
        try {
            ItemStack item = itemSerializer.deserialize(listing.getItemData());
            return item != null ? itemToJson(item) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String pshopItemJson(PlayerShopListing listing) {
        try {
            ItemStack item = itemSerializer.deserialize(listing.getItemData());
            return item != null ? itemToJson(item) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String historyItemJson(AuctionHistory h) {
        try {
            ItemStack item = itemSerializer.deserialize(h.itemData());
            return item != null ? itemToJson(item) : "";
        } catch (Exception e) {
            return "";
        }
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
        // 手动触发到期处理
        if (auctionService == null) return 0;
        return auctionService.triggerExpiredProcessing();
    }

    public boolean adminRemoveListing(long listingId) {
        if (auctionService == null) return false;
        return auctionService.adminForceCancelListing(listingId);
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

        if (message.startsWith("BID_PLACED:")) {
            String[] parts = message.split(":", -1);
            if (parts.length == 4) {
                try {
                    long listingId = Long.parseLong(parts[1]);
                    double amount = Double.parseDouble(parts[2]);
                    java.util.UUID bidder = java.util.UUID.fromString(parts[3]);
                    if (amount > 0 && (!strict || validateRemoteBid(listingId, amount))
                            && repository.updateListingBidIfHigher(
                                listingId,
                                amount,
                                bidder)) {
                        logger.fine("applied remote bid listing=" + listingId);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("invalid remote bid payload: " + message);
                }
            } else {
                logger.warning("remote bid missing bidder; ignored: " + message);
            }
        }

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
