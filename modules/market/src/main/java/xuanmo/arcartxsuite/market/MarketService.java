package xuanmo.arcartxsuite.market;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
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
    private final AuctionItemSerializer itemSerializer;
    private final @Nullable ItemBridgeAPI itemStackBridge;
    private final @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier;
    private final Logger logger;

    private static final int AUCTION_PAGE_SIZE = 20;
    private static final int HISTORY_PAGE_SIZE = 20;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private volatile java.util.function.Supplier<SignalDispatchable> signalProvider;
    private volatile java.util.function.Supplier<QQBotBroadcastable> qqBotProvider;
    private volatile java.util.function.Supplier<EventBusCapability> eventBusProvider;
    private double qqBroadcastThreshold = 1000.0;

    private MarketRepository repository;
    private RedisMarketCache redisCache;
    private AuctionService auctionService;
    private ShopService shopService;
    private RecycleService recycleService;

    public MarketService(JavaPlugin plugin, MarketModuleConfiguration config,
                         PacketBridgeAPI packetBridge, CurrencyBridgeAPI currencyManager,
                         ItemSourceRegistry itemSourceRegistry, AuctionItemSerializer itemSerializer,
                         @Nullable ItemBridgeAPI itemStackBridge,
                         @Nullable java.util.function.Supplier<MailDispatchable> mailSupplier,
                         Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.packetBridge = packetBridge;
        this.currencyManager = currencyManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemSerializer = itemSerializer;
        this.itemStackBridge = itemStackBridge;
        this.mailSupplier = mailSupplier;
        this.logger = logger;
    }

    public void setSignalProvider(java.util.function.Supplier<SignalDispatchable> provider) {
        this.signalProvider = provider;
    }

    public void setQQBotProvider(java.util.function.Supplier<QQBotBroadcastable> provider, double threshold) {
        this.qqBotProvider = provider;
        this.qqBroadcastThreshold = threshold;
    }

    public void setEventBusProvider(java.util.function.Supplier<EventBusCapability> provider) {
        this.eventBusProvider = provider;
    }

    public void start(java.io.File dataFolder) throws Exception {
        // 初始化存储
        repository = new JdbcMarketRepository(config.storage(), logger, dataFolder);
        repository.initialize();

        // 初始化 Redis
        redisCache = new RedisMarketCache(config.redis(), logger);
        redisCache.initialize(this::handleRedisMessage);

        // 启动子服务
        if (config.auction().enabled()) {
            auctionService = new AuctionService(plugin, config.auction(), config.messages(),
                repository, redisCache, currencyManager, mailSupplier, itemSerializer, logger);
            auctionService.start(config.schedulerIntervalTicks());
        }

        if (config.shop().enabled()) {
            shopService = new ShopService(plugin, config.shop(), config.messages(),
                repository, currencyManager, itemSourceRegistry, dataFolder, logger);
            shopService.start();
        }

        if (config.recycle().enabled()) {
            recycleService = new RecycleService(config.recycle(), config.messages(),
                repository, currencyManager, dataFolder, logger);
            recycleService.start();
        }

        logger.info("[Market] 全球市场服务已启动");
    }

    public void shutdown() {
        if (auctionService != null) { auctionService.shutdown(); auctionService = null; }
        if (shopService != null) { shopService.shutdown(); shopService = null; }
        if (recycleService != null) { recycleService.shutdown(); recycleService = null; }
        if (redisCache != null) { redisCache.shutdown(); redisCache = null; }
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
            player.sendMessage(ChatColor.RED + "拍卖行未启用");
            return;
        }
        if (duration <= 0) duration = config.auction().defaultDurationSeconds();
        var result = auctionService.createListing(player, item, buyNowPrice, startingBid, currency, duration);
        if (result.success()) {
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
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.messages().auctionCancelled()));
        } else {
            player.sendMessage(ChatColor.RED + "取消失败（不存在或非本人上架）");
        }
    }

    public void searchAuction(Player player, String keyword) {
        // 通过 packet 推送搜索结果到客户端 UI
        openAuctionUi(player);
    }

    // ─── 系统商店操作 ────────────────────────────────────────

    public void buyFromShop(Player player, String shopId, String itemId, int amount) {
        if (shopService == null) {
            player.sendMessage(ChatColor.RED + "系统商店未启用");
            return;
        }
        var result = shopService.buy(player, shopId, itemId, amount);
        if (result.success()) {
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
            player.sendMessage(ChatColor.RED + "回收商店未启用");
            return;
        }
        var result = recycleService.recycleBatch(player);
        if (result.success()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().recycleSuccess().replace("%amount%",
                    currencyManager.format(result.currency(), BigDecimal.valueOf(result.totalAmount())))));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', result.error()));
        }
    }

    // ─── UI 打开 ────────────────────────────────────────────

    public void openAuctionUi(Player player) {
        packetBridge.openUi(player, config.ui().auctionId());
    }

    public void openShopListUi(Player player) {
        packetBridge.openUi(player, config.ui().shopId());
    }

    public void openShopUi(Player player, String shopId) {
        packetBridge.openUi(player, config.ui().shopId());
    }

    public void openRecycleUi(Player player) {
        packetBridge.openUi(player, config.ui().recycleId());
    }

    public void openHistoryUi(Player player) {
        packetBridge.openUi(player, config.ui().historyId());
    }

    public void openMyListingsUi(Player player) {
        packetBridge.openUi(player, config.ui().auctionId());
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
        switch (action) {
            case "auction_list" -> handleAuctionListPacket(player, data);
            case "auction_buy" -> handleAuctionBuyPacket(player, data);
            case "auction_bid" -> handleAuctionBidPacket(player, data);
            case "auction_cancel" -> handleAuctionCancelPacket(player, data);
            case "auction_favorite" -> handleAuctionFavoritePacket(player, data);
            case "shop_list" -> handleShopListPacket(player, data);
            case "shop_buy" -> handleShopBuyPacket(player, data);
            case "recycle_all" -> handleRecycleAll(player);
            case "recycle_single" -> handleRecycleSinglePacket(player, data);
            case "recycle_preview" -> handleRecyclePreviewPacket(player);
            case "history_list" -> handleHistoryListPacket(player, data);
            default -> { return false; }
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

    private void handleAuctionListPacket(Player player, List<String> data) {
        if (auctionService == null) return;
        int page = data.size() > 1 ? parseIntSafe(data.get(1), 0) : 0;
        String category = data.size() > 2 ? data.get(2) : null;
        String keyword = data.size() > 3 ? data.get(3) : null;

        List<AuctionListing> listings;
        if ("my".equals(category)) {
            listings = auctionService.getMyListings(player.getUniqueId());
        } else if (keyword != null && !keyword.isEmpty()) {
            listings = auctionService.searchListings(keyword, page, AUCTION_PAGE_SIZE);
        } else if (category != null && !category.isEmpty() && !"all".equals(category)) {
            listings = auctionService.getListingsByCategory(category, page, AUCTION_PAGE_SIZE);
        } else {
            listings = auctionService.getActiveListings(page, AUCTION_PAGE_SIZE);
        }

        int totalCount = "my".equals(category)
            ? auctionService.countMy(player.getUniqueId())
            : auctionService.countActive();
        int totalPages = Math.max(1, (totalCount + AUCTION_PAGE_SIZE - 1) / AUCTION_PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        Map<String, Object> packet = buildAuctionPacket(player, listings, page, totalPages,
            category != null ? category : "all");
        sendUiPacket(player, config.ui().auctionId(), "update", packet);
    }

    private void handleAuctionBuyPacket(Player player, List<String> data) {
        if (auctionService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (listingId <= 0) return;
        var result = auctionService.buyNow(player, listingId);
        if (result.success()) {
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
                "formatted_price", currencyManager.format("money", BigDecimal.valueOf(result.price()))
            ));
            // 大额交易群播报（后备直接调用，后续可移除）
            if (result.price() >= qqBroadcastThreshold) {
                broadcastToQQ(player.getName() + " 以 " + currencyManager.format("money", BigDecimal.valueOf(result.price())) + " 购买了拍卖行物品");
            }
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
    }

    private void handleAuctionFavoritePacket(Player player, List<String> data) {
        if (auctionService == null) return;
        long listingId = data.size() > 1 ? parseLongSafe(data.get(1), 0) : 0;
        if (listingId <= 0) return;
        auctionService.toggleFavorite(player.getUniqueId(), listingId);
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
        recycleBatch(player);
        // 回收后刷新 UI
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
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.messages().recycleSuccess().replace("%amount%",
                    currencyManager.format(result.currency(), BigDecimal.valueOf(result.totalAmount())))));
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
        packetBridge.sendPacket(player, uiId, handler, packet);
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
        categories.put("all", true);
        categoryTexts.put("all", "&f全部");
        for (var entry : config.auction().categories().entrySet()) {
            categories.put(entry.getKey(), true);
            categoryTexts.put(entry.getKey(), "&f" + entry.getValue().displayName());
        }
        packet.put("categories", categories);
        packet.put("categoryTexts", categoryTexts);

        // 物品列表数据
        Map<String, Object> listingsMap = new LinkedHashMap<>();
        Map<String, Object> listingTexts = new LinkedHashMap<>();
        for (int i = 0; i < listings.size() && i < AUCTION_PAGE_SIZE; i++) {
            AuctionListing listing = listings.get(i);
            String key = Integer.toString(i);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", listing.getId());
            row.put("name", listing.getItemDisplayName());
            row.put("seller", listing.getSellerName());
            row.put("price", listing.getBuyNowPrice() > 0 ? formatPrice(listing.getBuyNowPrice()) : "-");
            row.put("bid", listing.getCurrentBid() > 0 ? formatPrice(listing.getCurrentBid()) : formatPrice(listing.getStartingBid()));
            row.put("timeLeft", formatTimeLeft(listing.getExpiresAt()));
            row.put("currencyName", listing.getCurrency());
            row.put("canCancel", listing.getSeller().equals(player.getUniqueId()));
            listingsMap.put(key, row);
            listingTexts.put(key, listing.getItemDisplayName());

            // 物品 JSON（flat 字段）
            packet.put("itemJson" + i, listingItemJson(listing));
        }
        packet.put("listings", listingsMap);
        packet.put("listingTexts", listingTexts);
        return packet;
    }

    private Map<String, Object> buildShopPacket(Player player, String selectedShopId) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());

        // 商店列表
        Map<String, Object> shops = new LinkedHashMap<>();
        Map<String, Object> shopTexts = new LinkedHashMap<>();
        String firstShopId = null;
        if (shopService != null) {
            for (var entry : shopService.getShops().entrySet()) {
                String id = entry.getKey();
                if (firstShopId == null) firstShopId = id;
                shops.put(id, true);
                shopTexts.put(id, "&f" + entry.getValue().displayName());
            }
        }
        packet.put("shops", shops);
        packet.put("shopTexts", shopTexts);

        // 当前选中商店
        String activeShopId = (selectedShopId != null && !selectedShopId.isEmpty()) ? selectedShopId : firstShopId;
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
                    packet.put("shopItemJson" + idx, shopItemJson(shopItem));
                    idx++;
                }
            }
        }
        packet.put("items", items);
        return packet;
    }

    private Map<String, Object> buildRecyclePacket(Player player) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());

        double totalValue = 0;
        double multiplier = recycleService != null ? recycleService.getMultiplier(player) : 1.0;
        String currency = config.recycle().defaultCurrency();

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

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", slot);
            row.put("name", itemDisplayName(item));
            row.put("amount", item.getAmount());
            row.put("unitPrice", formatPrice(unitPrice));
            row.put("totalPrice", formatPrice(total));
            recyclables.put(key, row);

            // 物品 JSON（flat 字段）
            packet.put("recycleItemJson" + idx, itemToJson(item));
            idx++;
        }
        packet.put("recyclables", recyclables);
        packet.put("totalValue", formatPrice(totalValue));
        packet.put("currency", currency);
        packet.put("multiplier", String.format("%.1f", multiplier));
        return packet;
    }

    private Map<String, Object> buildHistoryPacket(Player player, int page) {
        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("packetId", config.ui().packetId());

        List<AuctionHistory> histories = repository.getHistoryByPlayer(
            player.getUniqueId(), page * HISTORY_PAGE_SIZE, HISTORY_PAGE_SIZE);
        // 简易 total 估算（获取多一条判断是否有下一页）
        int totalPages = Math.max(1, histories.size() < HISTORY_PAGE_SIZE ? page + 1 : page + 2);
        page = Math.max(0, Math.min(page, totalPages - 1));

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

    private String shopItemJson(ShopService.ShopItem shopItem) {
        try {
            ItemStack item = switch (shopItem.source().toLowerCase()) {
                case "mythic" -> itemSourceRegistry.generateMythicItem(shopItem.itemId(), 1);
                case "neige" -> itemSourceRegistry.generateNeigeItem(shopItem.itemId(), 1);
                case "overture" -> itemSourceRegistry.generateOvertureItem(shopItem.itemId(), null, 1);
                case "mmoitems" -> {
                    String[] parts = shopItem.itemId().split(";", 2);
                    yield parts.length == 2
                        ? itemSourceRegistry.generateMmoItem(parts[0], parts[1], 1)
                        : null;
                }
                default -> {
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(shopItem.itemId());
                    ItemStack base = mat != null ? new ItemStack(mat, 1) : null;
                    yield applyItemNbt(base, shopItem.itemNbt());
                }
            };
            return item != null ? itemToJson(item) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static @Nullable ItemStack applyItemNbt(@Nullable ItemStack item, @Nullable String nbt) {
        if (item == null || nbt == null || nbt.isBlank()) {
            return item;
        }
        try {
            return org.bukkit.Bukkit.getUnsafe().modifyItemStack(item.clone(), nbt);
        } catch (Exception e) {
            return item;
        }
    }

    private String itemDisplayName(ItemStack item) {
        if (item == null) return "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
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
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static long parseLongSafe(String s, long def) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return def; }
    }

    private static double parseDoubleSafe(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    // ─── 管理接口 ───────────────────────────────────────────

    public int getAuctionCount() {
        return auctionService != null ? auctionService.countActive() : 0;
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

    public boolean isRedisConnected() {
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

    private void handleRedisMessage(String message) {
        // 处理跨服消息（如缓存失效、通知等）
        logger.fine("[Market] Redis 消息: " + message);
    }

    // ─── 跨模块联动 ─────────────────────────────────────────

    private void dispatchSignal(String signal, Player player, Map<String, String> variables) {
        if (signalProvider == null) return;
        SignalDispatchable sd = signalProvider.get();
        if (sd != null && player != null) {
            sd.dispatchSignal(signal, player, variables);
        }
    }

    private void broadcastToQQ(String message) {
        if (qqBotProvider == null) return;
        QQBotBroadcastable qqBot = qqBotProvider.get();
        if (qqBot != null) {
            qqBot.sendToAllGroups("[交易] " + message);
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
