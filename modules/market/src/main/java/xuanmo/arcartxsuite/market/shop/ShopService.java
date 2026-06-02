package xuanmo.arcartxsuite.market.shop;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.ShopConfiguration;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/**
 * 系统商店业务服务。
 */
public class ShopService {

    private final JavaPlugin plugin;
    private final ShopConfiguration config;
    private final MessagesConfiguration messages;
    private final MarketRepository repository;
    private final CurrencyBridgeAPI currencyManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final File dataFolder;
    private final Logger logger;

    private final Map<String, ShopDefinition> shops = new LinkedHashMap<>();
    private BukkitTask refreshTask;

    public ShopService(JavaPlugin plugin, ShopConfiguration config, MessagesConfiguration messages,
                       MarketRepository repository, CurrencyBridgeAPI currencyManager,
                       ItemSourceRegistry itemSourceRegistry, File dataFolder, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.repository = repository;
        this.currencyManager = currencyManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void start() {
        loadShops();
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshStocks, config.refreshIntervalTicks(), config.refreshIntervalTicks());
        logger.info("[Market-Shop] 系统商店已加载 " + shops.size() + " 个商店");
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        shops.clear();
    }

    public void reload() {
        shutdown();
        start();
    }

    private void loadShops() {
        shops.clear();
        File shopDir = new File(dataFolder, config.shopsDirectory());
        if (!shopDir.exists()) {
            shopDir.mkdirs();
            return;
        }
        File[] files = shopDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            try {
                String shopId = file.getName().replaceFirst("\\.(yml|yaml)$", "");
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ShopDefinition shop = parseShop(shopId, yaml);
                if (shop != null) {
                    shops.put(shopId, shop);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Market-Shop] 加载商店文件失败: " + file.getName(), e);
            }
        }
    }

    private @Nullable ShopDefinition parseShop(String shopId, YamlConfiguration yaml) {
        String displayName = yaml.getString("display-name", shopId);
        String icon = yaml.getString("icon", "CHEST");
        String permission = yaml.getString("permission", "");
        List<String> tags = yaml.getStringList("tags");

        ConfigurationSection itemsSec = yaml.getConfigurationSection("items");
        if (itemsSec == null) return null;

        Map<String, ShopItem> items = new LinkedHashMap<>();
        for (String itemKey : itemsSec.getKeys(false)) {
            ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemKey);
            if (itemSec == null) continue;

            String source = itemSec.getString("source", "minecraft");
            String itemId = itemSec.getString("item-id", "STONE");
            String itemDisplayName = itemSec.getString("display-name", itemId);
            double buyPrice = itemSec.getDouble("buy-price", 0);
            double sellPrice = itemSec.getDouble("sell-price", 0);
            String currency = itemSec.getString("currency", config.defaultCurrency());
            String stockMode = itemSec.getString("stock-mode", "unlimited");
            int stockAmount = itemSec.getInt("stock-amount", 0);
            int limitPerPlayer = itemSec.getInt("limit-per-player", 0);
            String limitReset = itemSec.getString("limit-reset", "never");

            Map<String, Double> discount = new LinkedHashMap<>();
            ConfigurationSection discountSec = itemSec.getConfigurationSection("discount");
            if (discountSec != null) {
                for (String perm : discountSec.getKeys(false)) {
                    discount.put(perm, discountSec.getDouble(perm, 1.0));
                }
            }

            items.put(itemKey, new ShopItem(
                itemKey, source, itemId, itemSec.getString("item-nbt", null),
                itemDisplayName, buyPrice, sellPrice, currency,
                stockMode, stockAmount, limitPerPlayer, limitReset, discount
            ));
        }

        return new ShopDefinition(shopId, displayName, icon, permission, tags, items);
    }

    /**
     * 购买系统商店物品。
     */
    public BuyResult buy(Player player, String shopId, String itemId, int amount) {
        ShopDefinition shop = shops.get(shopId);
        if (shop == null) return BuyResult.fail("商店不存在");
        if (!shop.permission().isEmpty() && !player.hasPermission(shop.permission())) {
            return BuyResult.fail("无权限访问该商店");
        }

        ShopItem shopItem = shop.items().get(itemId);
        if (shopItem == null) return BuyResult.fail("商品不存在");

        // 限购检查
        if (shopItem.limitPerPlayer() > 0) {
            ShopLimitRecord limit = repository.getShopLimit(player.getUniqueId(), shopId, itemId);
            int purchased = limit == null ? 0 : limit.purchasedCount();
            if (purchased + amount > shopItem.limitPerPlayer()) {
                return BuyResult.fail(messages.shopLimitReached());
            }
        }

        // 计算价格（含折扣）
        double unitPrice = shopItem.buyPrice();
        for (var entry : shopItem.discount().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                unitPrice *= entry.getValue();
                break;
            }
        }
        double totalPrice = unitPrice * amount;

        // 扣款
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(shopItem.currency());
        if (bridge == null || !bridge.available()) {
            return BuyResult.fail("货币系统不可用");
        }
        CurrencyTransactionResult result = bridge.withdraw(player, BigDecimal.valueOf(totalPrice));
        if (!result.success()) {
            return BuyResult.fail(messages.insufficientFunds());
        }

        // 生成物品并给予
        ItemStack item = createItem(shopItem, amount);
        if (item == null) {
            // 退款
            bridge.deposit(player, BigDecimal.valueOf(totalPrice));
            return BuyResult.fail("物品生成失败");
        }
        player.getInventory().addItem(item);

        // 更新限购记录
        if (shopItem.limitPerPlayer() > 0) {
            ShopLimitRecord existing = repository.getShopLimit(player.getUniqueId(), shopId, itemId);
            int newCount = (existing == null ? 0 : existing.purchasedCount()) + amount;
            long resetTime = calculateResetTime(shopItem.limitReset());
            repository.upsertShopLimit(new ShopLimitRecord(
                player.getUniqueId(), shopId, itemId, newCount, System.currentTimeMillis(), resetTime
            ));
        }

        return BuyResult.success(totalPrice);
    }

    /**
     * 获取所有商店列表。
     */
    public Map<String, ShopDefinition> getShops() {
        return Collections.unmodifiableMap(shops);
    }

    public @Nullable ShopDefinition getShop(String shopId) {
        return shops.get(shopId);
    }

    private @Nullable ItemStack createItem(ShopItem shopItem, int amount) {
        try {
            ItemStack item = switch (shopItem.source().toLowerCase()) {
                case "mythic" -> itemSourceRegistry.generateMythicItem(shopItem.itemId(), amount);
                case "neige" -> itemSourceRegistry.generateNeigeItem(shopItem.itemId(), amount);
                case "overture" -> itemSourceRegistry.generateOvertureItem(shopItem.itemId(), null, amount);
                default -> {
                    // minecraft 原版物品
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(shopItem.itemId());
                    yield mat != null ? new ItemStack(mat, amount) : null;
                }
            };
            return item;
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Market-Shop] 创建物品失败: " + shopItem.source() + ":" + shopItem.itemId(), e);
            return null;
        }
    }

    private long calculateResetTime(String resetType) {
        long now = System.currentTimeMillis();
        return switch (resetType.toLowerCase()) {
            case "daily" -> now + 86400_000L;
            case "weekly" -> now + 604800_000L;
            case "monthly" -> now + 2592000_000L;
            default -> 0L;
        };
    }

    private void refreshStocks() {
        repository.resetExpiredShopLimits("auto");
    }

    // ─── 数据模型 ───────────────────────────────────────────

    public record ShopDefinition(
        String id, String displayName, String icon, String permission,
        List<String> tags, Map<String, ShopItem> items
    ) {}

    public record ShopItem(
        String key, String source, String itemId, @Nullable String itemNbt,
        String displayName, double buyPrice, double sellPrice,
        String currency, String stockMode, int stockAmount,
        int limitPerPlayer, String limitReset, Map<String, Double> discount
    ) {}

    public record BuyResult(boolean success, @Nullable String error, double totalPrice) {
        public static BuyResult success(double price) { return new BuyResult(true, null, price); }
        public static BuyResult fail(String error) { return new BuyResult(false, error, 0); }
    }
}
