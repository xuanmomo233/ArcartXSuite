package xuanmo.arcartxsuite.market.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record MarketModuleConfiguration(
    boolean debug,
    long schedulerIntervalTicks,
    UiConfiguration ui,
    StorageConfiguration storage,
    RedisConfiguration redis,
    AuctionConfiguration auction,
    ShopConfiguration shop,
    RecycleConfiguration recycle,
    MessagesConfiguration messages
) {

    public static MarketModuleConfiguration load(FileConfiguration config, Logger logger) {
        boolean debug = config.getBoolean("settings.debug", false);
        long schedulerInterval = Math.max(20L, config.getLong("settings.scheduler-interval-ticks", 200L));

        UiConfiguration ui = loadUi(config);
        StorageConfiguration storage = loadStorage(config);
        RedisConfiguration redis = loadRedis(config);
        AuctionConfiguration auction = loadAuction(config, logger);
        ShopConfiguration shop = loadShop(config);
        RecycleConfiguration recycle = loadRecycle(config);
        MessagesConfiguration messages = loadMessages(config);

        return new MarketModuleConfiguration(debug, schedulerInterval, ui, storage, redis, auction, shop, recycle, messages);
    }

    private static UiConfiguration loadUi(FileConfiguration config) {
        return new UiConfiguration(
            readStr(config, "ui.shop-id", "AXS:market_shop"),
            readStr(config, "ui.shop-file", "arcartx/ui/market_shop.yml"),
            readStr(config, "ui.auction-id", "AXS:market_auction"),
            readStr(config, "ui.auction-file", "arcartx/ui/market_auction.yml"),
            readStr(config, "ui.recycle-id", "AXS:market_recycle"),
            readStr(config, "ui.recycle-file", "arcartx/ui/market_recycle.yml"),
            readStr(config, "ui.history-id", "AXS:market_history"),
            readStr(config, "ui.history-file", "arcartx/ui/market_history.yml"),
            readStr(config, "ui.packet-id", "AXS_MARKET"),
            config.getBoolean("ui.register-ui-on-enable", true),
            config.getBoolean("ui.overwrite-ui-files", false)
        );
    }

    private static StorageConfiguration loadStorage(FileConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("storage");
        if (sec == null) return StorageConfiguration.defaults();
        return new StorageConfiguration(
            readStr(sec, "mode", "sqlite"),
            readStr(sec, "sqlite.file", "market.db"),
            readStr(sec, "mysql.host", "127.0.0.1"),
            Math.max(1, sec.getInt("mysql.port", 3306)),
            readStr(sec, "mysql.database", "arcartxsuite"),
            readStr(sec, "mysql.username", "root"),
            readStr(sec, "mysql.password", ""),
            readStr(sec, "mysql.table-prefix", "axs_market_"),
            Math.max(1, sec.getInt("pool-size", 8))
        );
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
            readStr(sec, "channel", "axs:market:sync"),
            Math.max(10, sec.getInt("cache-ttl-seconds", 60))
        );
    }

    private static AuctionConfiguration loadAuction(FileConfiguration config, Logger logger) {
        ConfigurationSection sec = config.getConfigurationSection("auction");
        if (sec == null) return AuctionConfiguration.defaults();

        Map<String, Double> taxDiscount = new LinkedHashMap<>();
        ConfigurationSection tdSec = sec.getConfigurationSection("tax-discount");
        if (tdSec != null) {
            for (String key : tdSec.getKeys(false)) {
                taxDiscount.put(key, tdSec.getDouble(key, 0.0));
            }
        }

        List<String> blacklistNameContains = sec.getStringList("blacklist.name-contains");
        List<String> blacklistLoreContains = sec.getStringList("blacklist.lore-contains");
        List<String> blacklistMaterialIds = sec.getStringList("blacklist.material-ids");
        List<String> blacklistMythicIds = sec.getStringList("blacklist.mythic-item-ids");
        List<String> blacklistNeigeIds = sec.getStringList("blacklist.neige-item-ids");
        List<String> blacklistNameRegex = sec.getStringList("blacklist.name-regex");
        List<String> blacklistLoreRegex = sec.getStringList("blacklist.lore-regex");

        AuctionBlacklist blacklist = new AuctionBlacklist(
            blacklistMaterialIds, blacklistMythicIds, blacklistNeigeIds,
            blacklistNameContains, blacklistLoreContains, blacklistNameRegex, blacklistLoreRegex
        );

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
            sec.getDouble("qq-broadcast-threshold", 1000.0)
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
            sec.getBoolean("batch-confirm", true),
            priceMultiplier
        );
    }

    private static MessagesConfiguration loadMessages(FileConfiguration config) {
        ConfigurationSection sec = config.getConfigurationSection("messages");
        if (sec == null) return MessagesConfiguration.defaults();
        return new MessagesConfiguration(
            readStr(sec, "prefix", "&8[&6AXS市场&8] &7"),
            readStr(sec, "auction-listed", "&a物品已成功上架！"),
            readStr(sec, "auction-bought", "&a购买成功！"),
            readStr(sec, "auction-bid-placed", "&a出价成功！当前最高出价: &e%amount%"),
            readStr(sec, "auction-outbid", "&c您对 &e%item% &c的出价已被超越！新最高价: &e%amount%"),
            readStr(sec, "auction-expired", "&7您上架的 &e%item% &7已到期，已退回。"),
            readStr(sec, "auction-sold", "&a您上架的 &e%item% &a已售出！收入: &e%amount%"),
            readStr(sec, "auction-cancelled", "&7已取消上架。"),
            readStr(sec, "shop-bought", "&a购买成功！花费: &e%amount%"),
            readStr(sec, "shop-limit-reached", "&c该商品已达购买上限。"),
            readStr(sec, "recycle-success", "&a回收成功！获得: &e%amount%"),
            readStr(sec, "recycle-nothing", "&7背包中没有可回收的物品。"),
            readStr(sec, "insufficient-funds", "&c余额不足。"),
            readStr(sec, "item-blacklisted", "&c该物品不允许上架。")
        );
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
        String packetId,
        boolean registerOnEnable, boolean overwriteUiFiles
    ) {}

    public record StorageConfiguration(
        String mode, String sqliteFileName,
        String host, int port, String database,
        String username, String password, String tablePrefix, int poolSize
    ) {
        public boolean isSqlite() {
            return !"mysql".equalsIgnoreCase(mode);
        }

        public static StorageConfiguration defaults() {
            return new StorageConfiguration("sqlite", "market.db", "127.0.0.1", 3306, "arcartxsuite", "root", "", "axs_market_", 8);
        }

        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (!isSqlite()) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }
    }

    public record RedisConfiguration(
        boolean enabled, String host, int port, String password,
        int database, String channel, int cacheTtlSeconds
    ) {
        public static RedisConfiguration disabled() {
            return new RedisConfiguration(false, "127.0.0.1", 6379, "", 0, "axs:market:sync", 60);
        }
    }

    public record AuctionConfiguration(
        boolean enabled,
        long minDurationSeconds, long maxDurationSeconds, long defaultDurationSeconds,
        int maxListingsPerPlayer,
        double minBidIncrementRatio, double minBidIncrementAbsolute,
        double listingFee, String listingFeeCurrency,
        double transactionTaxRate, String transactionTaxCurrency,
        Map<String, Double> taxDiscount,
        String expiredReturnMethod, String outbidNotify,
        AuctionBlacklist blacklist,
        Map<String, CategoryDefinition> categories,
        double qqBroadcastThreshold
    ) {
        public static AuctionConfiguration defaults() {
            return new AuctionConfiguration(
                true, 3600L, 604800L, 86400L, 10, 0.05, 10.0,
                0.0, "money", 0.05, "money",
                Map.of(), "mail", "chat",
                AuctionBlacklist.empty(), Map.of(), 1000.0
            );
        }
    }

    public record AuctionBlacklist(
        List<String> materialIds, List<String> mythicItemIds, List<String> neigeItemIds,
        List<String> nameContains, List<String> loreContains,
        List<String> nameRegex, List<String> loreRegex
    ) {
        public static AuctionBlacklist empty() {
            return new AuctionBlacklist(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public record CategoryDefinition(
        String displayName, int priority, String nbtPath, List<String> nbtValues, boolean isDefault
    ) {}

    public record ShopConfiguration(
        boolean enabled, String shopsDirectory, long refreshIntervalTicks, String defaultCurrency
    ) {
        public static ShopConfiguration defaults() {
            return new ShopConfiguration(true, "shops", 6000L, "money");
        }
    }

    public record RecycleConfiguration(
        boolean enabled, String recycleDirectory, String defaultCurrency,
        boolean allowAutoRecycle, boolean batchConfirm,
        Map<String, Double> priceMultiplier
    ) {
        public static RecycleConfiguration defaults() {
            return new RecycleConfiguration(true, "recycle", "money", true, true, Map.of());
        }
    }

    public record MessagesConfiguration(
        String prefix, String auctionListed, String auctionBought,
        String auctionBidPlaced, String auctionOutbid, String auctionExpired,
        String auctionSold, String auctionCancelled,
        String shopBought, String shopLimitReached,
        String recycleSuccess, String recycleNothing,
        String insufficientFunds, String itemBlacklisted
    ) {
        public static MessagesConfiguration defaults() {
            return new MessagesConfiguration(
                "&8[&6AXS市场&8] &7", "&a物品已成功上架！", "&a购买成功！",
                "&a出价成功！当前最高出价: &e%amount%", "&c您对 &e%item% &c的出价已被超越！新最高价: &e%amount%",
                "&7您上架的 &e%item% &7已到期，已退回。", "&a您上架的 &e%item% &a已售出！收入: &e%amount%",
                "&7已取消上架。", "&a购买成功！花费: &e%amount%", "&c该商品已达购买上限。",
                "&a回收成功！获得: &e%amount%", "&7背包中没有可回收的物品。",
                "&c余额不足。", "&c该物品不允许上架。"
            );
        }
    }
}
