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
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.auction.AuctionService;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;
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
    private final xuanmo.arcartxsuite.api.bridge.VanillaItemNameBridge vanillaItemNameBridge;
    private final @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier;
    private final xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher;
    private final ItemMatcherAPI itemMatcher;
    private final Logger logger;
    private final CrossServerAPI crossServer;
    private final MessageProvider messages;
    private final Map<String, Map<UUID, String>> actionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> actionTokensInFlight = ConcurrentHashMap.newKeySet();

    private static final int AUCTION_PAGE_SIZE = 20;
    private static final String AUCTION_SELL_UI_ID = "AXS:market_auction_sell";
    private static final int HISTORY_PAGE_SIZE = 20;
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
                         MessageProvider messages) {
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
        this.messages = messages;
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
            delivery -> handleCrossServerMessage(delivery.payload())
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
            auctionService.start(config.schedulerIntervalTicks());
        }

        if (config.shop().enabled()) {
            shopService = new ShopService(plugin, config.shop(), config.messages(),
                repository, currencyManager, itemSourceRegistry, dataFolder, logger);
            shopService.start();
        }

        if (config.recycle().enabled()) {
            recycleService = new RecycleService(config.recycle(), config.messages(),
                repository, currencyManager, itemSourceRegistry, dataFolder, logger);
            recycleService.start();
        }

        logger.info("全球市场服务已启动");
    }

    public void shutdown() {
        if (auctionService != null) { auctionService.shutdown(); auctionService = null; }
        if (shopService != null) { shopService.shutdown(); shopService = null; }
        if (recycleService != null) { recycleService.shutdown(); recycleService = null; }
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

    public void createListing(Player player, ItemStack item, double buyNowPrice, double startingBid, String currency, long duration) {
        if (auctionService == null) {
            player.sendMessage(messages.get("player.auction-unavailable"));
            return;
        }
        if (duration <= 0) duration = config.auction().defaultDurationSeconds();
        var result = auctionService.createListing(player, item, buyNowPrice, startingBid, currency, duration);
        if (result.success()) {
            rotateToken(player, config.ui().auctionId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionListed()));
            dispatchSignal("market_listing_created", player, Map.of(
                "price", String.valueOf(buyNowPrice),
                "currency", currency != null ? currency : "money"
            ));
            publishEvent("market.listing_created", player, Map.of(
                "price", String.valueOf(buyNowPrice),
                "currency", currency != null ? currency : "money"
            ));
        } else {
            player.sendMessage(ChatColor.RED + result.error());
        }
    }

    public void cancelListing(Player player, long listingId) {
        if (auctionService == null) return;
        boolean success = auctionService.cancelListing(player, listingId);
        if (success) {
            rotateToken(player, config.ui().auctionId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionCancelled()));
        } else {
            player.sendMessage(messages.get("player.cancel-failed"));
        }
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
        packetBridge.openUi(player, config.ui().shopId());
        issueActionToken(player, config.ui().shopId());
    }

    // 当前所有商店共用一个 UI ID；未来若支持多商店，可在此按 shopId 查找对应 UI
    public void openShopUi(Player player) {
        packetBridge.openUi(player, config.ui().shopId());
        issueActionToken(player, config.ui().shopId());
    }

    public void openRecycleUi(Player player) {
        packetBridge.openUi(player, config.ui().recycleId());
        issueActionToken(player, config.ui().recycleId());
    }

    public void openHistoryUi(Player player) {
        packetBridge.openUi(player, config.ui().historyId());
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
        if (requiresActionToken(action) && !beginActionToken(player, action, data)) return true;
        switch (action) {
            case "auction_list" -> handleAuctionListPacket(player, data);
            case "auction_buy" -> handleAuctionBuyPacket(player, data);
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
            default -> { return false; }
        }
        if (requiresActionToken(action)) {
            actionTokensInFlight.remove(player.getUniqueId());
        }
        return true;
    }

    // 协议格式: data[0]=action, data[1..n]=参数
    // auction_list: [action, page, category?, keyword?]
    // auction_buy: [action, listingId]
    // auction_bid: [action, listingId, amount]
    // auction_cancel: [action, listingId]
    // auction_favorite: [action, listingId]
    // shop_list: [action, shopId?]
    // shop_buy: [action, shopId, itemId, amount]
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
        var currencyOption = config.auction().currencies().stream()
            .filter(option -> option.id().equalsIgnoreCase(currency))
            .findFirst().orElse(null);
        var currencyBridge = currencyOption == null ? null : currencyManager.bridge(currencyOption.id());
        if (currencyOption == null || currencyBridge == null || !currencyBridge.available()) {
            player.sendMessage(messages.get("player.sell.invalid-currency"));
            return;
        }
        var result = auctionService.createListing(
            player, slot, 0, startingBid, currencyOption.id(), duration, message);
        if (!result.success()) {
            player.sendMessage(ChatColor.RED + result.error());
            return;
        }
        rotateToken(player, AUCTION_SELL_UI_ID);
        player.sendMessage(ChatColor.translateAlternateColorCodes((char) 38, config.messages().auctionListed()));
        openAuctionUi(player);
        handleAuctionListPacket(player, List.of("auction_list", "0", "all", ""));
    }

    private void handleAuctionListPacket(Player player, List<String> data) {
        if (auctionService == null) return;
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

    private void handleAuctionBuyPacket(Player player, List<String> data) {
        if (auctionService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (listingId <= 0) return;
        var result = auctionService.buyNow(player, listingId);
        if (result.success()) {
            rotateToken(player, config.ui().auctionId());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionBought()));
            // 触发信号
            dispatchSignal("market_purchase", player, Map.of(
                "listing_id", String.valueOf(listingId),
                "price", String.valueOf(result.price())
            ));
            // 发布到事件总线（订阅方自行决定是否播报）
            publishEvent("market.auction_purchased", player, Map.of(
                "listing_id", String.valueOf(listingId),
                "price", String.valueOf(result.price()),
                "formatted_price", currencyManager.format(result.currency(), BigDecimal.valueOf(result.price()))
            ));
        } else {
            player.sendMessage(ChatColor.RED + result.error());
        }
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
        if (!config.recycle().allowAutoRecycle()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().autoRecycleDisabled()));
            return;
        }
        recycleBatch(player);
        handleRecyclePreviewPacket(player);
    }

    private void handleRecycleSinglePacket(Player player, List<String> data) {
        if (recycleService == null) return;
        int slot = data.size() > 1 ? parseIntSafe(data.get(1), -1) : -1;
        if (slot < 0 || slot >= player.getInventory().getSize()) return;
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
            case "auction_buy", "auction_bid", "auction_cancel", "auction_list_create",
                "shop_buy", "recycle_all", "recycle_single" -> true;
            default -> false;
        };
    }

    private boolean requiresUiToken(String uiId) {
        return uiId.equals(config.ui().auctionId()) || uiId.equals(AUCTION_SELL_UI_ID)
            || uiId.equals(config.ui().shopId()) || uiId.equals(config.ui().recycleId());
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
            case "shop_buy" -> config.ui().shopId();
            case "recycle_all", "recycle_single" -> config.ui().recycleId();
            default -> config.ui().auctionId();
        };
        int index = switch (action) {
            case "auction_buy", "auction_cancel" -> 2;
            case "auction_bid" -> 3;
            case "auction_list_create" -> 6;
            case "shop_buy" -> 4;
            case "recycle_single" -> 2;
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
            boolean canBuyNow = listing.getBuyNowPrice() > 0 && (listing.getType() == AuctionListing.ListingType.BUY_NOW || listing.getType() == AuctionListing.ListingType.BOTH);
            boolean canBid = listing.getType() == AuctionListing.ListingType.AUCTION || listing.getType() == AuctionListing.ListingType.BOTH;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", listing.getId());
            row.put("name", listing.getItemDisplayName());
            row.put("seller", listing.getSellerName());
            row.put("price", canBuyNow ? formatPrice(listing.getBuyNowPrice()) : "-");
            row.put("minBid", minBid);
            row.put("type", listing.getType().name());
            row.put("canBuyNow", canBuyNow);
            row.put("canBid", canBid);
            row.put("isFavorite", favoriteIds.contains(listing.getId()));
            row.put("bid", listing.getCurrentBid() > 0 ? formatPrice(listing.getCurrentBid()) : formatPrice(listing.getStartingBid()));
            row.put("timeLeft", formatTimeLeft(listing.getExpiresAt()));
            row.put("currencyName", listing.getCurrency());
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

        // 商品列表
        Map<String, Object> items = new LinkedHashMap<>();
        if (activeShopId != null && shopService != null) {
            var shopDef = shopService.getShop(activeShopId);
            if (shopDef != null) {
                packet.put("shopName", shopDef.displayName());
                int idx = 0;
                for (var itemEntry : shopDef.items().entrySet()) {
                    var shopItem = itemEntry.getValue();
                    String key = Integer.toString(idx);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", itemEntry.getKey());
                    row.put("name", shopItem.displayName());
                    row.put("price", formatPrice(shopItem.buyPrice()));
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

        double totalValue = 0;
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
            totalValue += total;
            String itemCurrency = recycleService.getRecycleCurrency(item);
            if (itemCurrency != null) totalsByCurrency.merge(itemCurrency, total, Double::sum);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", slot);
            row.put("name", itemDisplayName(item));
            row.put("amount", item.getAmount());
            row.put("unitPrice", formatPrice(unitPrice));
            row.put("totalPrice", formatPrice(total));
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
            row.put("amount", formatPrice(h.price()) + " " + h.currency());
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

    private void handleCrossServerMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        if (message.startsWith("BID_PLACED:")) {
            String[] parts = message.split(":", -1);
            if (parts.length == 4) {
                try {
                    long listingId = Long.parseLong(parts[1]);
                    double amount = Double.parseDouble(parts[2]);
                    java.util.UUID bidder = java.util.UUID.fromString(parts[3]);
                    if (amount > 0
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

        if ((message.startsWith("LISTING_")
                || message.startsWith("BID_"))
                && redisCache != null
                && redisCache.isAvailable()) {
            redisCache.invalidateByPrefix("market:listings:");
        }
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
