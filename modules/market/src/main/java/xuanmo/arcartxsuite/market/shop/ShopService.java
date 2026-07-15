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
 * ÃÂ§ÃÂ³ÃÂ»ÃÂ§ÃÂ»ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂÃÂ¡ÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂ¡ÃÂ£ÃÂÃÂ
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

    /** ÃÂ¥ÃÂÃÂÃÂ¦ÃÂ¬ÃÂ¡ÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ°ÃÂ¦ÃÂÃÂ°ÃÂ©ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ©ÃÂÃÂÃÂ¯ÃÂ¼ÃÂ36 ÃÂ¦ÃÂ ÃÂ¼ ÃÂÃÂ 64ÃÂ¯ÃÂ¼ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂ²ÃÂ¦ÃÂ­ÃÂ¢ÃÂ¥ÃÂ®ÃÂ¢ÃÂ¦ÃÂÃÂ·ÃÂ§ÃÂ«ÃÂ¯ÃÂ¤ÃÂ¼ÃÂ ÃÂ¥ÃÂÃÂ¥ÃÂ¨ÃÂ¶ÃÂÃÂ¥ÃÂ¤ÃÂ§ÃÂ¥ÃÂÃÂ¼ÃÂ¥ÃÂ¯ÃÂ¼ÃÂ¨ÃÂÃÂ´ÃÂ¦ÃÂÃÂ´ÃÂ¦ÃÂÃÂ°ÃÂ¦ÃÂºÃÂ¢ÃÂ¥ÃÂÃÂº / ÃÂ¥ÃÂÃÂ·ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ£ÃÂÃÂ */
    private static final int MAX_PURCHASE_AMOUNT = 36 * 64;

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
        logger.info("[Market-Shop] ÃÂ§ÃÂ³ÃÂ»ÃÂ§ÃÂ»ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂÃÂ¥ÃÂ·ÃÂ²ÃÂ¥ÃÂÃÂ ÃÂ¨ÃÂ½ÃÂ½ " + shops.size() + " ÃÂ¤ÃÂ¸ÃÂªÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂ");
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
                logger.log(Level.WARNING, "[Market-Shop] ÃÂ¥ÃÂÃÂ ÃÂ¨ÃÂ½ÃÂ½ÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂÃÂ¦ÃÂÃÂÃÂ¤ÃÂ»ÃÂ¶ÃÂ¥ÃÂ¤ÃÂ±ÃÂ¨ÃÂ´ÃÂ¥: " + file.getName(), e);
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
     * ÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ°ÃÂ§ÃÂ³ÃÂ»ÃÂ§ÃÂ»ÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ£ÃÂÃÂ
     */
    public BuyResult buy(Player player, String shopId, String itemId, int amount) {
        ShopDefinition shop = shops.get(shopId);
        if (shop == null) return BuyResult.fail("ÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂ­ÃÂÃÂ¥ÃÂÃÂ¨");
        if (!shop.permission().isEmpty() && !player.hasPermission(shop.permission())) {
            return BuyResult.fail("ÃÂ¦ÃÂÃÂ ÃÂ¦ÃÂÃÂÃÂ©ÃÂÃÂÃÂ¨ÃÂ®ÃÂ¿ÃÂ©ÃÂÃÂ®ÃÂ¨ÃÂ¯ÃÂ¥ÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂ");
        }

        ShopItem shopItem = shop.items().get(itemId);
        if (shopItem == null) return BuyResult.fail("ÃÂ¥ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¥ÃÂ­ÃÂÃÂ¥ÃÂÃÂ¨");

        // ÃÂ¦ÃÂÃÂ°ÃÂ©ÃÂÃÂÃÂ¦ÃÂ ÃÂ¡ÃÂ©ÃÂªÃÂÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂ²ÃÂ¦ÃÂ­ÃÂ¢ÃÂ¥ÃÂ®ÃÂ¢ÃÂ¦ÃÂÃÂ·ÃÂ§ÃÂ«ÃÂ¯ÃÂ¤ÃÂ¼ÃÂ ÃÂ¥ÃÂÃÂ¥ <=0 ÃÂ¦ÃÂÃÂÃÂ¨ÃÂ¶ÃÂÃÂ¥ÃÂ¤ÃÂ§ÃÂ¥ÃÂÃÂ¼ÃÂ¯ÃÂ¼ÃÂÃÂ¦ÃÂÃÂ´ÃÂ¦ÃÂÃÂ°ÃÂ¦ÃÂºÃÂ¢ÃÂ¥ÃÂÃÂºÃÂ§ÃÂ»ÃÂÃÂ¨ÃÂ¿ÃÂÃÂ©ÃÂÃÂÃÂ¨ÃÂ´ÃÂ­ / ÃÂ¥ÃÂÃÂ·ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂ
        if (amount < 1 || amount > MAX_PURCHASE_AMOUNT) {
            return BuyResult.fail("ÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ°ÃÂ¦ÃÂÃÂ°ÃÂ©ÃÂÃÂÃÂ©ÃÂÃÂÃÂ¦ÃÂ³ÃÂ");
        }

        // ÃÂ©ÃÂÃÂÃÂ¨ÃÂ´ÃÂ­ÃÂ¦ÃÂ£ÃÂÃÂ¦ÃÂÃÂ¥ÃÂ¯ÃÂ¼ÃÂÃÂ§ÃÂÃÂ¨ long ÃÂ¨ÃÂ¿ÃÂÃÂ§ÃÂ®ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ©ÃÂÃÂ¿ÃÂ¥ÃÂÃÂ int ÃÂ¦ÃÂºÃÂ¢ÃÂ¥ÃÂÃÂºÃÂ§ÃÂ»ÃÂÃÂ¨ÃÂ¿ÃÂÃÂ¯ÃÂ¼ÃÂ
        if (shopItem.limitPerPlayer() > 0) {
            ShopLimitRecord limit = repository.getShopLimit(player.getUniqueId(), shopId, itemId);
            long purchased = limit == null ? 0L : limit.purchasedCount();
            if (purchased + (long) amount > shopItem.limitPerPlayer()) {
                return BuyResult.fail(messages.shopLimitReached());
            }
        }

        // ÃÂ¥ÃÂºÃÂÃÂ¥ÃÂ­ÃÂÃÂ¦ÃÂ£ÃÂÃÂ¦ÃÂÃÂ¥ÃÂ¯ÃÂ¼ÃÂstock-mode: global / per-playerÃÂ¯ÃÂ¼ÃÂ
        // ÃÂ¨ÃÂ®ÃÂ¡ÃÂ§ÃÂ®ÃÂÃÂ¤ÃÂ»ÃÂ·ÃÂ¦ÃÂ ÃÂ¼ÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂ«ÃÂ¦ÃÂÃÂÃÂ¦ÃÂÃÂ£ÃÂ¯ÃÂ¼ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂÃÂ¨ÃÂ§ÃÂ¨ÃÂ BigDecimal ÃÂ©ÃÂÃÂ¿ÃÂ¥ÃÂÃÂÃÂ¦ÃÂµÃÂ®ÃÂ§ÃÂÃÂ¹ÃÂ§ÃÂ´ÃÂ¯ÃÂ¨ÃÂ®ÃÂ¡ÃÂ¨ÃÂ¯ÃÂ¯ÃÂ¥ÃÂ·ÃÂ®
        BigDecimal unitPrice = BigDecimal.valueOf(shopItem.buyPrice());
        for (var entry : shopItem.discount().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                unitPrice = unitPrice.multiply(BigDecimal.valueOf(entry.getValue()));
                break;
            }
        }
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount));

        // ÃÂ¦ÃÂÃÂ£ÃÂ¦ÃÂ¬ÃÂ¾
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(shopItem.currency());
        if (bridge == null || !bridge.available()) {
            return BuyResult.fail("Ã¨Â´Â§Ã¥Â¸ÂÃ§Â³Â»Ã§Â»ÂÃ¤Â¸ÂÃ¥ÂÂ¯Ã§ÂÂ¨");
        }

        if (!tryConsumeStock(player, shopId, itemId, shopItem, amount)) {
            return BuyResult.fail("Ã¥ÂÂÃ¥ÂÂÃ¥ÂºÂÃ¥Â­ÂÃ¤Â¸ÂÃ¨Â¶Â³");
        }
        CurrencyTransactionResult result = bridge.withdraw(player, totalPrice);
        if (!result.success()) {
            restoreStock(player, shopId, itemId, shopItem, amount);
            return BuyResult.fail(messages.insufficientFunds());
        }

        // ÃÂ§ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ
        ItemStack item = createItem(shopItem, amount);
        if (item == null) {
            depositSafe(player, shopItem.currency(), totalPrice, "shop_item_generation_refund");
            restoreStock(player, shopId, itemId, shopItem, amount);
            return BuyResult.fail("ÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¥ÃÂ¤ÃÂ±ÃÂ¨ÃÂ´ÃÂ¥");
        }

        // ÃÂ§ÃÂ»ÃÂÃÂ¤ÃÂºÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¨ÃÂ£ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ¤ÃÂ¸ÃÂÃÂ§ÃÂÃÂÃÂ©ÃÂÃÂ¨ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¦ÃÂ¯ÃÂÃÂ¤ÃÂ¾ÃÂÃÂ©ÃÂÃÂÃÂ¦ÃÂ¬ÃÂ¾ÃÂ¯ÃÂ¼ÃÂÃÂ¦ÃÂÃÂÃÂ§ÃÂ»ÃÂ"ÃÂ¦ÃÂÃÂ£ÃÂ¤ÃÂºÃÂÃÂ©ÃÂÃÂ±ÃÂ¥ÃÂÃÂ´ÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ"
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        int leftover = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        int delivered = amount - leftover;
        if (leftover > 0) {
            depositSafe(player, shopItem.currency(), unitPrice.multiply(BigDecimal.valueOf(leftover)), "shop_inventory_overflow_refund");
            restoreStock(player, shopId, itemId, shopItem, leftover);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&eÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ§ÃÂ©ÃÂºÃÂ©ÃÂÃÂ´ÃÂ¤ÃÂ¸ÃÂÃÂ¨ÃÂ¶ÃÂ³ÃÂ¯ÃÂ¼ÃÂÃÂ¤ÃÂ»ÃÂÃÂ¨ÃÂ´ÃÂ­ÃÂ¤ÃÂ¹ÃÂ° " + delivered + " ÃÂ¤ÃÂ¸ÃÂªÃÂ¯ÃÂ¼ÃÂÃÂ¥ÃÂ¤ÃÂÃÂ¤ÃÂ½ÃÂÃÂ¦ÃÂ¬ÃÂ¾ÃÂ©ÃÂ¡ÃÂ¹ÃÂ¥ÃÂ·ÃÂ²ÃÂ©ÃÂÃÂÃÂ¨ÃÂ¿ÃÂ"));
        }
        if (delivered <= 0) {
            return BuyResult.fail("ÃÂ¨ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ§ÃÂ©ÃÂºÃÂ©ÃÂÃÂ´ÃÂ¤ÃÂ¸ÃÂÃÂ¨ÃÂ¶ÃÂ³");
        }

        // ÃÂ¦ÃÂÃÂ´ÃÂ¦ÃÂÃÂ°ÃÂ©ÃÂÃÂÃÂ¨ÃÂ´ÃÂ­ÃÂ¨ÃÂ®ÃÂ°ÃÂ¥ÃÂ½ÃÂÃÂ¯ÃÂ¼ÃÂÃÂ¦ÃÂÃÂÃÂ¥ÃÂ®ÃÂÃÂ©ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¤ÃÂºÃÂ¤ÃÂ¦ÃÂÃÂ°ÃÂ©ÃÂÃÂÃÂ¨ÃÂ®ÃÂ¡ÃÂ¯ÃÂ¼ÃÂ
        if (shopItem.limitPerPlayer() > 0) {
            ShopLimitRecord existing = repository.getShopLimit(player.getUniqueId(), shopId, itemId);
            int newCount = (existing == null ? 0 : existing.purchasedCount()) + delivered;
            long resetTime = calculateResetTime(shopItem.limitReset());
            repository.upsertShopLimit(new ShopLimitRecord(
                player.getUniqueId(), shopId, itemId, newCount, System.currentTimeMillis(), resetTime
            ));
        }

        return BuyResult.success(unitPrice.multiply(BigDecimal.valueOf(delivered)).doubleValue(), shopItem.currency());
    }

    /**
     * ÃÂ¨ÃÂÃÂ·ÃÂ¥ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¦ÃÂÃÂÃÂ¥ÃÂÃÂÃÂ¥ÃÂºÃÂÃÂ¥ÃÂÃÂÃÂ¨ÃÂ¡ÃÂ¨ÃÂ£ÃÂÃÂ
     */
    public Map<String, ShopDefinition> getShops() {
        return Collections.unmodifiableMap(shops);
    }

    public @Nullable ShopDefinition getShop(String shopId) {
        return shops.get(shopId);
    }

    private void depositSafe(Player player, String currency, BigDecimal amount, String reason) {
        if (amount == null || amount.signum() <= 0) return;
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(currency);
        if (bridge != null && bridge.available()) {
            CurrencyTransactionResult result = bridge.deposit(player, amount);
            if (result.success()) return;
            logger.warning("[Market-Shop] refund failed; queueing pending currency: player=" + player.getName()
                + " currency=" + currency + " amount=" + amount + " reason=" + reason);
        }
        repository.addPendingCurrency(player.getUniqueId(), currency, amount.doubleValue(), reason);
    }

    private @Nullable ItemStack createItem(ShopItem shopItem, int amount) {
        try {
            ItemStack item = switch (shopItem.source().toLowerCase()) {
                case "mythic" -> itemSourceRegistry.generateMythicItem(shopItem.itemId(), amount);
                case "neige" -> itemSourceRegistry.generateNeigeItem(shopItem.itemId(), amount);
                case "overture" -> itemSourceRegistry.generateOvertureItem(shopItem.itemId(), null, amount);
                case "mmoitems" -> {
                    String[] parts = shopItem.itemId().split(";", 2);
                    yield parts.length == 2
                        ? itemSourceRegistry.generateMmoItem(parts[0], parts[1], amount)
                        : null;
                }
                default -> {
                    // minecraft ÃÂ¥ÃÂÃÂÃÂ§ÃÂÃÂÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂ
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(shopItem.itemId());
                    ItemStack base = mat != null ? new ItemStack(mat, amount) : null;
                    yield applyItemNbt(base, shopItem.itemNbt());
                }
            };
            return item;
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Market-Shop] ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ»ÃÂºÃÂ§ÃÂÃÂ©ÃÂ¥ÃÂÃÂÃÂ¥ÃÂ¤ÃÂ±ÃÂ¨ÃÂ´ÃÂ¥: " + shopItem.source() + ":" + shopItem.itemId(), e);
            return null;
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

    private static boolean hasLimitedStock(ShopService.ShopItem shopItem) {
        return shopItem.stockAmount() > 0
            && ("global".equalsIgnoreCase(shopItem.stockMode())
            || "per-player".equalsIgnoreCase(shopItem.stockMode()));
    }

    private boolean tryConsumeStock(Player player, String shopId, String itemId, ShopItem shopItem, int amount) {
        if (!hasLimitedStock(shopItem)) {
            return true;
        }
        if ("global".equalsIgnoreCase(shopItem.stockMode())) {
            return repository.tryConsumeGlobalShopStock(shopId, itemId, amount, shopItem.stockAmount());
        }
        return repository.tryConsumePlayerShopStock(
            player.getUniqueId(), shopId, itemId, amount, shopItem.stockAmount()
        );
    }

    private void restoreStock(Player player, String shopId, String itemId, ShopItem shopItem, int amount) {
        if (!hasLimitedStock(shopItem) || amount <= 0) {
            return;
        }
        if ("global".equalsIgnoreCase(shopItem.stockMode())) {
            repository.restoreGlobalShopStock(shopId, itemId, amount);
        } else {
            repository.restorePlayerShopStock(player.getUniqueId(), shopId, itemId, amount);
        }
    }

    // ÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂ ÃÂ¦ÃÂÃÂ°ÃÂ¦ÃÂÃÂ®ÃÂ¦ÃÂ¨ÃÂ¡ÃÂ¥ÃÂÃÂ ÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂÃÂ¢ÃÂÃÂ

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

    public record BuyResult(boolean success, @Nullable String error, double totalPrice, String currency) {
        public static BuyResult success(double price, String currency) {
            return new BuyResult(true, null, price, currency);
        }

        public static BuyResult fail(String error) {
            return new BuyResult(false, error, 0, "");
        }
    }
}
