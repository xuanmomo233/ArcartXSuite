package xuanmo.arcartxsuite.market.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherLoader;
import xuanmo.arcartxsuite.market.dynamic.DynamicStrategyConfig;

/**
 * Market 模块配置记录，聚合 UI、存储、Redis、跨服、拍卖、商店、回收、消息等子配置。
 *
 * @param debug                   是否启用调试日志
 * @param schedulerIntervalTicks  调度间隔（tick，至少 20）
 * @param ui                      UI 配置
 * @param storage                 存储配置
 * @param redis                   Redis 配置
 * @param crossServer             跨服通道配置
 * @param crossServerSecurity     跨服入站消息安全策略
 * @param auction                 拍卖配置
 * @param shop                    商店配置
 * @param recycle                 回收配置
 * @param dynamic                 动态商品策略配置
 * @param playerShop              玩家商店配置
 * @param messages                消息配置
 */
public record MarketModuleConfiguration(
    boolean debug,
    long schedulerIntervalTicks,
    UiConfiguration ui,
    StorageConfiguration storage,
    RedisConfiguration redis,
    CrossServerChannelConfig crossServer,
    CrossServerSecurityConfiguration crossServerSecurity,
    AuctionConfiguration auction,
    ShopConfiguration shop,
    RecycleConfiguration recycle,
    DynamicConfiguration dynamic,
    PlayerShopConfiguration playerShop,
    MessagesConfiguration messages
) {

    public static MarketModuleConfiguration load(FileConfiguration config, FileConfiguration messagesConfig, Logger logger) {
        boolean debug = config.getBoolean("settings.debug", false);
        long schedulerInterval = Math.max(20L, config.getLong("settings.scheduler-interval-ticks", 200L));

        UiConfiguration ui = loadUi(config);
        StorageConfiguration storage = loadStorage(config);
        RedisConfiguration redis = loadRedis(config);
        ConfigurationSection crossServerSection = config.getConfigurationSection("cross-server");
        CrossServerChannelConfig crossServer = CrossServerChannelConfigs.fromSection(crossServerSection);
        CrossServerSecurityConfiguration crossServerSecurity = new CrossServerSecurityConfiguration(
            crossServerSection != null && crossServerSection.getBoolean("strict-validation", false),
            crossServerSection != null && crossServerSection.getBoolean("require-signature", false)
        );
        AuctionConfiguration auction = loadAuction(config, logger);
        ShopConfiguration shop = loadShop(config);
        RecycleConfiguration recycle = loadRecycle(config);
        DynamicConfiguration dynamic = loadDynamic(config);
        PlayerShopConfiguration playerShop = loadPlayerShop(config, logger);
        MessagesConfiguration messages = loadMessages(messagesConfig);

        return new MarketModuleConfiguration(debug, schedulerInterval, ui, storage, redis, crossServer, crossServerSecurity, auction, shop, recycle, dynamic, playerShop, messages);
    }

    private static UiConfiguration loadUi(FileConfiguration config) {
        return new UiConfiguration(
            readStr(config, "ui.shop-ui-id", "AXS:market_shop"),
            readStr(config, "ui.shop-file", "arcartx/ui/market_shop.yml"),
            readStr(config, "ui.auction-ui-id", "AXS:market_auction"),
            readStr(config, "ui.auction-file", "arcartx/ui/market_auction.yml"),
            readStr(config, "ui.recycle-ui-id", "AXS:market_recycle"),
            readStr(config, "ui.recycle-file", "arcartx/ui/market_recycle.yml"),
            readStr(config, "ui.history-ui-id", "AXS:market_history"),
            readStr(config, "ui.history-file", "arcartx/ui/market_history.yml"),
            readStr(config, "ui.sell-ui-id", "AXS:market_sell"),
            readStr(config, "ui.sell-file", "arcartx/ui/market_sell.yml"),
            readStr(config, "ui.player-shop-ui-id", "AXS:market_player_shop"),
            readStr(config, "ui.player-shop-file", "arcartx/ui/market_player_shop.yml"),
            readStr(config, "ui.shop-edit-ui-id", "AXS:market_shop_edit"),
            readStr(config, "ui.shop-edit-file", "arcartx/ui/market_shop_edit.yml"),
            readStr(config, "ui.packet-id", "AXS_MARKET"),
            config.getBoolean("ui.register-ui-on-enable", true),
            config.getBoolean("ui.overwrite-ui-files", false),
            readStr(config,"ui.auction-room-ui-id","AXS:market_auction_room"),
            readStr(config,"ui.auction-room-file","arcartx/ui/market_auction_room.yml"),
            readStr(config,"ui.player-shops-ui-id","AXS:market_player_shops"),
            readStr(config,"ui.player-store-ui-id","AXS:market_player_store"),
            readStr(config,"ui.player-store-edit-ui-id","AXS:market_player_store_edit"),
            readStr(config,"ui.recycle-protection-ui-id","AXS:market_recycle_protection"),
            readStr(config,"ui.admin-trades-ui-id","AXS:market_admin_trades")
        );
    }

    private static StorageConfiguration loadStorage(FileConfiguration config) {
        return StorageConfiguration.load(config.getConfigurationSection("storage"));
    }

    private static RedisConfiguration loadRedis(FileConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("redis");
        if (sec == null) return RedisConfiguration.disabled();
        return new RedisConfiguration(
            sec.getBoolean("enabled", false),
            readStr(sec, "host", "127.0.0.1"),
            Math.max(1, sec.getInt("port", 6379)),
            readStr(sec, "password", ""),
            sec.getInt("database", 0),
            Math.max(10, sec.getInt("cache-ttl-seconds", 60))
        );
    }

    private static Map<String, Double> readTaxDiscounts(ConfigurationSection config) {
        Map<String, Double> discounts = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("tax-discount");
        if (section != null) {
            section.getValues(true).forEach((permission, value) -> {
                if (value instanceof Number rate) {
                    discounts.put(permission, rate.doubleValue());
                }
            });
        }
        return discounts;
    }

    private static AuctionConfiguration loadAuction(FileConfiguration config, Logger logger) {
        ConfigurationSection sec = config.getConfigurationSection("auction");
        if (sec == null) return AuctionConfiguration.defaults();

        Map<String, Double> taxDiscount = readTaxDiscounts(sec);

        ItemMatcher blacklist = ItemMatcherLoader.load(
            sec.getConfigurationSection("blacklist"), logger, "auction.blacklist");

        List<CurrencyOption> currencies = new ArrayList<>();
        for (Map<?, ?> raw : sec.getMapList("currencies")) {
            Object rawId = raw.get("id");
            String id = rawId == null ? "" : String.valueOf(rawId).trim();
            Object rawName = raw.get("name");
            if (rawName == null) rawName = raw.get("display-name");
            String name = rawName == null ? id : String.valueOf(rawName).trim();
            if (!id.isEmpty()) currencies.add(new CurrencyOption(id, name.isEmpty() ? id : name));
        }
        if (currencies.isEmpty()) currencies.add(new CurrencyOption("money", "money"));
        String defaultCurrency = readStr(sec, "default-currency", currencies.get(0).id());
        Map<String, CategoryDefinition> categories = new LinkedHashMap<>();
        ConfigurationSection catSec = sec.getConfigurationSection("categories");
        if (catSec != null) {
            for (String catId : catSec.getKeys(false)) {
                ConfigurationSection cat = catSec.getConfigurationSection(catId);
                if (cat == null) continue;
                List<String> nbtValues = cat.getStringList("nbt.values");
                categories.put(catId, new CategoryDefinition(
                    readStr(cat, "display-name", catId),
                    cat.getInt("priority", 100),
                    readStr(cat, "nbt.path", ""),
                    nbtValues,
                    cat.getBoolean("default", false)
                ));
            }
        }

        return new AuctionConfiguration(
            sec.getBoolean("enabled", true),
            currencies, defaultCurrency,
            sec.getDouble("min-price", 1.0),
            sec.getDouble("max-price", 1000000000.0),
            Math.max(0, sec.getInt("message-max-length", 50)),
            sec.getLong("min-duration-seconds", 3600L),
            sec.getLong("max-duration-seconds", 604800L),
            sec.getLong("default-duration-seconds", 86400L),
            sec.getInt("max-listings-per-player", 10),
            sec.getDouble("min-bid-increment-ratio", 0.05),
            sec.getDouble("min-bid-increment-absolute", 10.0),
            sec.getDouble("listing-fee", 0.0),
            readStr(sec, "listing-fee-currency", "money"),
            sec.getDouble("transaction-tax-rate", 0.05),
            readStr(sec, "transaction-tax-currency", "money"),
            taxDiscount,
            readStr(sec, "expired-return-method", "mail"),
            readStr(sec, "outbid-notify", "chat"),
            blacklist,
            categories,
            sec.getBoolean("allow-cancel-with-bids", false),
            sec.getBoolean("end-protection.enabled", false),
            Math.max(1,Math.min(3600,sec.getInt("end-protection.window-seconds",120))),
            Math.max(1,Math.min(3600,sec.getInt("end-protection.extend-seconds",120))),
            Math.max(1,Math.min(86400,sec.getInt("end-protection.max-extension-seconds",1800))),
            sec.getBoolean("notifications.outbid",true),
            sec.getBoolean("notifications.settlement",true),
            sec.getBoolean("notifications.watch-bids",false)
        );
    }

    private static PlayerShopConfiguration loadPlayerShop(FileConfiguration config, Logger logger) {
        ConfigurationSection sec = config.getConfigurationSection("player-shop");
        if (sec == null) return PlayerShopConfiguration.defaults();

        Map<String, Double> taxDiscount = readTaxDiscounts(sec);

        ItemMatcher blacklist = ItemMatcherLoader.load(
            sec.getConfigurationSection("blacklist"), logger, "player-shop.blacklist");

        List<CurrencyOption> currencies = new ArrayList<>();
        for (Map<?, ?> raw : sec.getMapList("currencies")) {
            Object rawId = raw.get("id");
            String id = rawId == null ? "" : String.valueOf(rawId).trim();
            Object rawName = raw.get("name");
            if (rawName == null) rawName = raw.get("display-name");
            String name = rawName == null ? id : String.valueOf(rawName).trim();
            if (!id.isEmpty()) currencies.add(new CurrencyOption(id, name.isEmpty() ? id : name));
        }
        if (currencies.isEmpty()) currencies.add(new CurrencyOption("money", "money"));
        String defaultCurrency = readStr(sec, "default-currency", currencies.get(0).id());

        return new PlayerShopConfiguration(
            sec.getBoolean("enabled", true),
            currencies, defaultCurrency,
            sec.getDouble("min-price", 1.0),
            sec.getDouble("max-price", 1000000000.0),
            Math.max(0, sec.getInt("message-max-length", 50)),
            sec.getLong("min-duration-seconds", 3600L),
            sec.getLong("max-duration-seconds", 2592000L),
            sec.getLong("default-duration-seconds", 604800L),
            sec.getInt("max-listings-per-player", 20),
            sec.getDouble("listing-fee", 0.0),
            readStr(sec, "listing-fee-currency", "money"),
            sec.getDouble("transaction-tax-rate", 0.05),
            readStr(sec, "transaction-tax-currency", "money"),
            taxDiscount,
            readStr(sec, "expired-return-method", "mail"),
            blacklist,
            Math.max(1,Math.min(64,sec.getInt("profile.name-max-length",32))),
            Math.max(1,Math.min(1024,sec.getInt("profile.description-max-length",256))),
            Math.max(1,Math.min(2048,sec.getInt("profile.announcement-max-length",512))),
            Math.max(1,Math.min(1000,sec.getInt("profile.max-favorites",100)))
        );
    }

    private static ShopConfiguration loadShop(FileConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("shop");
        if (sec == null) return ShopConfiguration.defaults();
        return new ShopConfiguration(
            sec.getBoolean("enabled", true),
            readStr(sec, "shops-directory", "shops"),
            Math.max(20L, sec.getLong("refresh-interval-ticks", 6000L)),
            readStr(sec, "default-currency", "money")
        );
    }

    private static RecycleConfiguration loadRecycle(FileConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("recycle");
        if (sec == null) return RecycleConfiguration.defaults();

        Map<String, Double> priceMultiplier = new LinkedHashMap<>();
        ConfigurationSection pmSec = sec.getConfigurationSection("price-multiplier");
        if (pmSec != null) {
            for (String key : pmSec.getKeys(false)) {
                priceMultiplier.put(key, pmSec.getDouble(key, 1.0));
            }
        }

        return new RecycleConfiguration(
            sec.getBoolean("enabled", true),
            readStr(sec, "recycle-directory", "recycle"),
            readStr(sec, "default-currency", "money"),
            sec.getBoolean("allow-auto-recycle", true),
            sec.getBoolean("batch-recycle-enabled", true),
            sec.getBoolean("batch-confirm", true),
            priceMultiplier,
            sec.getBoolean("protection.default-auto-recycle",false),
            Math.max(1,Math.min(1000,sec.getInt("protection.max-rules",200)))
        );
    }

    private static MessagesConfiguration loadMessages(FileConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("messages");
        if (sec == null) sec = config;
        return new MessagesConfiguration(
            readStr(sec, "prefix", "&8[&6AXS市场&8] &7"),
            readStr(sec, "auction-listed", "&a物品已成功上架！"),
            readTradeMessage(sec, "auction-bid-placed", "&a出价成功！当前最高出价: &e{amount}"),
            readTradeMessage(sec, "auction-outbid", "&c您对 &e{item} &c的出价已被超越！新最高价: &e{amount}"),
            readTradeMessage(sec, "auction-expired", "&7您上架的 &e{item} &7已到期，已退回。"),
            readTradeMessage(sec, "auction-sold", "&a您上架的 &e{item} &a已售出！收入: &e{amount}"),
            readStr(sec, "auction-cancelled", "&7已取消上架。"),
            readTradeMessage(sec, "shop-bought", "&a购买成功！花费: &e{amount}"),
            readStr(sec, "shop-limit-reached", "&c该商品已达购买上限。"),
            readStr(sec, "shop-condition-denied", "&c你不满足该商品的上架条件。"),
            readTradeMessage(sec, "recycle-success", "&a回收已受理，收益等待入账: &e{amount}"),
            readStr(sec, "recycle-nothing", "&7背包中没有可回收的物品。"),
            readStr(sec, "insufficient-funds", "&c余额不足。"),
            readStr(sec, "item-blacklisted", "&c该物品不允许上架。"),
            readStr(sec, "batch-recycle-disabled", "&c一键回收功能已关闭"),
            readStr(sec, "player-shop-listed", "&a商品已上架到你的商店！"),
            readTradeMessage(sec, "player-shop-bought", "&a购买成功！花费: &e{amount}"),
            readTradeMessage(sec, "player-shop-sold", "&a你商店中的 &e{item} &a已售出！收入: &e{amount}"),
            readTradeMessage(sec, "player-shop-expired", "&7你商店中的 &e{item} &7已到期，已退回。"),
            readStr(sec, "player-shop-cancelled", "&7已下架商品。")
        );
    }

    private static String readTradeMessage(ConfigurationSection section, String key, String fallback) {
        return readStr(section, key, fallback).replace("%item%", "{item}").replace("%amount%", "{amount}");
    }

    private static String readStr(ConfigurationSection sec, String path, String def) {
        String val = sec.getString(path);
        return val == null || val.isEmpty() ? def : val;
    }

    // ─── 内部配置记录 ────────────────────────────────────────

    public record UiConfiguration(
        String shopId, String shopFile,
        String auctionId, String auctionFile,
        String recycleId, String recycleFile,
        String historyId, String historyFile,
        String sellId, String sellFile,
        String playerShopId, String playerShopFile,
        String shopEditId, String shopEditFile,
        String packetId,
        boolean registerOnEnable, boolean overwriteUiFiles,
        String auctionRoomId, String auctionRoomFile,
        String playerShopsId,String playerStoreId,String playerStoreEditId,String recycleProtectionId,String adminTradesId
    ) {
        public UiConfiguration {
            var existing=new java.util.HashSet<>(List.of(shopId,auctionId,recycleId,historyId,sellId,playerShopId,shopEditId,auctionRoomId,"AXS:market_auction_sell"));
            var storeIds=List.of(playerShopsId,playerStoreId,playerStoreEditId,recycleProtectionId,adminTradesId);
            if(new java.util.HashSet<>(storeIds).size()!=5 || storeIds.stream().anyMatch(existing::contains))
                throw new IllegalArgumentException("店铺、回收保护、交易管理等新增界面的UI ID必须互不相同，且不能与其他市场界面共用");
        }
    }

    public record StorageConfiguration(
        boolean shared,
        String mode, String sqliteFileName,
        String host, int port, String database,
        String username, String password, String tablePrefix, int poolSize
    ) {
        public boolean isSqlite() {
            return !"mysql".equalsIgnoreCase(mode);
        }

        /**
         * 从配置节加载存储配置。
         * <p>
         * shared=true（默认）时使用全部默认值，不读取其余字段；
         * shared=false 时读取 mode/sqlite/mysql 等自建存储字段。
         *
         * @param section storage 配置节，可为 {@code null}
         * @return 解析后的存储配置
         */
        public static StorageConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            boolean shared = section.getBoolean("shared", true);
            // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
            if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
                shared = false;
            }
            if (shared) {
                return new StorageConfiguration(true, "sqlite", "market.db",
                    "127.0.0.1", 3306, "arcartxsuite", "root", "",
                    "axs_market_", 8);
            }
            return new StorageConfiguration(false,
                readStr(section, "mode", "sqlite"),
                readStr(section, "sqlite.file", "market.db"),
                readStr(section, "mysql.host", "127.0.0.1"),
                Math.max(1, section.getInt("mysql.port", 3306)),
                readStr(section, "mysql.database", "arcartxsuite"),
                readStr(section, "mysql.username", "root"),
                readStr(section, "mysql.password", ""),
                readStr(section, "mysql.table-prefix", "axs_market_"),
                Math.max(1, section.getInt("pool-size", 8))
            );
        }

        public static StorageConfiguration defaults() {
            return new StorageConfiguration(true, "sqlite", "market.db", "127.0.0.1", 3306, "arcartxsuite", "root", "", "axs_market_", 8);
        }

        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (!isSqlite()) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }

        /**
         * 判断是否为覆盖模式（模块自建连接池）。
         * <p>
         * 当 shared=false 时返回 true（模块独立连接，自建存储）；
         * shared=true 时返回 false（使用本体共享数据源）。
         *
         * @return {@code true} 表示模块应自建连接池
         */
        public boolean hasOverride() {
            return !shared;
        }
    }

    /**
     * 跨服入站消息安全策略。
     *
     * @param strictValidation 严格业务校验：BID_PLACED 校验挂单存在/有效/拍卖类型/金额范围，
     *                         DYNAMIC_SHOP_UPDATE 对价格与乘数做范围 clamp
     * @param requireSignature 要求入站消息必须带有效签名（宿主跨服签名开启且验签通过），
     *                         未签名消息直接丢弃；签名未启用时开启此项等于拒绝全部跨服消息
     */
    public record CrossServerSecurityConfiguration(
        boolean strictValidation,
        boolean requireSignature
    ) {
        public static CrossServerSecurityConfiguration defaults() {
            return new CrossServerSecurityConfiguration(false, false);
        }
    }

    public record RedisConfiguration(
        boolean enabled, String host, int port, String password,
        int database, int cacheTtlSeconds
    ) {
        public static RedisConfiguration disabled() {
            return new RedisConfiguration(false, "127.0.0.1", 6379, "", 0, 60);
        }
    }

    public record AuctionConfiguration(
        boolean enabled,
        List<CurrencyOption> currencies, String defaultCurrency,
        double minPrice, double maxPrice, int messageMaxLength,
        long minDurationSeconds, long maxDurationSeconds, long defaultDurationSeconds,
        int maxListingsPerPlayer,
        double minBidIncrementRatio, double minBidIncrementAbsolute,
        double listingFee, String listingFeeCurrency,
        double transactionTaxRate, String transactionTaxCurrency,
        Map<String, Double> taxDiscount,
        String expiredReturnMethod, String outbidNotify,
        ItemMatcher blacklist,
        Map<String, CategoryDefinition> categories,
        boolean allowCancelWithBids, boolean endProtectionEnabled,
        int endWindowSeconds, int endExtendSeconds, int endMaxExtensionSeconds,
        boolean notifyOutbid, boolean notifySettlement, boolean notifyWatchBids
    ) {
        public static AuctionConfiguration defaults() {
            return new AuctionConfiguration(
                true, List.of(new CurrencyOption("money", "money")), "money",
                1.0, 1000000000.0, 50,
                3600L, 604800L, 86400L, 10, 0.05, 10.0,
                0.0, "money", 0.05, "money",
                Map.of(), "mail", "chat",
                ItemMatcher.empty(), Map.of(), false, false, 120, 120, 1800, true, true, false
            );
        }
    }

    /** 玩家商店配置（固定价格、有期限、数量限制）。 */
    public record PlayerShopConfiguration(
        boolean enabled,
        List<CurrencyOption> currencies, String defaultCurrency,
        double minPrice, double maxPrice, int messageMaxLength,
        long minDurationSeconds, long maxDurationSeconds, long defaultDurationSeconds,
        int maxListingsPerPlayer,
        double listingFee, String listingFeeCurrency,
        double transactionTaxRate, String transactionTaxCurrency,
        Map<String, Double> taxDiscount,
        String expiredReturnMethod,
        ItemMatcher blacklist, int nameMaxLength,int descriptionMaxLength,int announcementMaxLength,int maxShopFavorites
    ) {
        public static PlayerShopConfiguration defaults() {
            return new PlayerShopConfiguration(
                true, List.of(new CurrencyOption("money", "money")), "money",
                1.0, 1000000000.0, 50,
                3600L, 2592000L, 604800L, 20,
                0.0, "money", 0.05, "money",
                Map.of(), "mail",
                ItemMatcher.empty(),32,256,512,100
            );
        }
    }

    public record CategoryDefinition(
        String displayName, int priority, String nbtPath, List<String> nbtValues, boolean isDefault
    ) {}

    public record CurrencyOption(String id, String name) {}

    public record ShopConfiguration(
        boolean enabled, String shopsDirectory, long refreshIntervalTicks, String defaultCurrency
    ) {
        public static ShopConfiguration defaults() {
            return new ShopConfiguration(true, "shops", 6000L, "money");
        }
    }

    /** 动态商品策略配置。 */
    public record DynamicConfiguration(
        boolean enabled,
        List<DynamicStrategyConfig> strategies
    ) {
        public static DynamicConfiguration defaults() {
            return new DynamicConfiguration(false, java.util.List.of());
        }
    }

    private static DynamicConfiguration loadDynamic(FileConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("shop.dynamic");
        if (sec == null) return DynamicConfiguration.defaults();
        boolean enabled = sec.getBoolean("enabled", false);
        List<DynamicStrategyConfig> strategies = new ArrayList<>();
        ConfigurationSection strategiesSec = sec.getConfigurationSection("strategies");
        if (strategiesSec != null) {
            for (String key : strategiesSec.getKeys(false)) {
                ConfigurationSection strategySec = strategiesSec.getConfigurationSection(key);
                if (strategySec != null) {
                    try {
                        strategies.add(DynamicStrategyConfig.load(key, strategySec));
                    } catch (Exception e) {
                        Logger.getLogger("ArcartXSuite").warning(
                            "[Market] 加载动态策略配置失败: " + key + " | " + e.getMessage());
                    }
                }
            }
        }
        return new DynamicConfiguration(enabled, strategies);
    }

    public record RecycleConfiguration(
        boolean enabled, String recycleDirectory, String defaultCurrency,
        boolean allowAutoRecycle, boolean batchRecycleEnabled, boolean batchConfirm,
        Map<String, Double> priceMultiplier,boolean defaultAutoRecycle,int maxProtectionRules
    ) {
        public static RecycleConfiguration defaults() {
            return new RecycleConfiguration(true, "recycle", "money", true, true, true, Map.of(), false,200);
        }
    }

    public record MessagesConfiguration(
        String prefix, String auctionListed,
        String auctionBidPlaced, String auctionOutbid, String auctionExpired,
        String auctionSold, String auctionCancelled,
        String shopBought, String shopLimitReached,
        String shopConditionDenied,
        String recycleSuccess, String recycleNothing,
        String insufficientFunds, String itemBlacklisted, String batchRecycleDisabled,
        String playerShopListed, String playerShopBought, String playerShopSold,
        String playerShopExpired, String playerShopCancelled
    ) {
        public static MessagesConfiguration defaults() {
            return new MessagesConfiguration(
                "&8[&6AXS市场&8] &7", "&a物品已成功上架！",
                "&a出价成功！当前最高出价: &e{amount}", "&c您对 &e{item} &c的出价已被超越！新最高价: &e{amount}",
                "&7您上架的 &e{item} &7已到期，已退回。", "&a您上架的 &e{item} &a已售出！收入: &e{amount}",
                "&7已取消上架。", "&a购买成功！花费: &e{amount}", "&c该商品已达购买上限。",
                "&c你不满足该商品的上架条件。",
                "&a回收已受理，收益等待入账: &e{amount}", "&7背包中没有可回收的物品。",
                "&c余额不足。",
                "&c该物品不允许上架。",
                "&c一键回收功能已关闭",
                "&a商品已上架到你的商店！", "&a购买成功！花费: &e{amount}",
                "&a你商店中的 &e{item} &a已售出！收入: &e{amount}",
                "&7你商店中的 &e{item} &7已到期，已退回。", "&7已下架商品。"
            );
        }
    }
}
