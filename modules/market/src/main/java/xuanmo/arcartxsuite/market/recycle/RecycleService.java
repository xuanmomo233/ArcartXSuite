package xuanmo.arcartxsuite.market.recycle;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.RecycleConfiguration;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/**
 * Ã¥ÂÂÃ¦ÂÂ¶Ã¥ÂÂÃ¥ÂºÂÃ¤Â¸ÂÃ¥ÂÂ¡Ã¦ÂÂÃ¥ÂÂ¡Ã£ÂÂ
 */
public class RecycleService {

    private final RecycleConfiguration config;
    private final MessagesConfiguration messages;
    private final MarketRepository repository;
    private final CurrencyBridgeAPI currencyManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final File dataFolder;
    private final Logger logger;

    private final Map<String, RecycleEntry> entries = new LinkedHashMap<>();

    public RecycleService(RecycleConfiguration config, MessagesConfiguration messages,
                          MarketRepository repository, CurrencyBridgeAPI currencyManager,
                          ItemSourceRegistry itemSourceRegistry, File dataFolder, Logger logger) {
        this.config = config;
        this.messages = messages;
        this.repository = repository;
        this.currencyManager = currencyManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void start() {
        loadRecycleTables();
        logger.info("[Market-Recycle] Ã¥ÂÂÃ¦ÂÂ¶Ã¨Â¡Â¨Ã¥Â·Â²Ã¥ÂÂ Ã¨Â½Â½ " + entries.size() + " Ã¤Â¸ÂªÃ¦ÂÂ¡Ã§ÂÂ®");
    }

    public void shutdown() {
        entries.clear();
    }

    public void reload() {
        shutdown();
        start();
    }

    private void loadRecycleTables() {
        entries.clear();
        File recycleDir = new File(dataFolder, config.recycleDirectory());
        if (!recycleDir.exists()) {
            recycleDir.mkdirs();
            return;
        }
        File[] files = recycleDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection entriesSec = yaml.getConfigurationSection("entries");
                if (entriesSec == null) continue;

                for (String key : entriesSec.getKeys(false)) {
                    ConfigurationSection entrySec = entriesSec.getConfigurationSection(key);
                    if (entrySec == null) continue;
                    entries.put(key, new RecycleEntry(
                        key,
                        entrySec.getString("source", "minecraft"),
                        entrySec.getString("item-id", "STONE"),
                        entrySec.getDouble("price", 0),
                        entrySec.getString("currency", config.defaultCurrency())
                    ));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Market-Recycle] Ã¥ÂÂ Ã¨Â½Â½Ã¥ÂÂÃ¦ÂÂ¶Ã¨Â¡Â¨Ã¦ÂÂÃ¤Â»Â¶Ã¥Â¤Â±Ã¨Â´Â¥: " + file.getName(), e);
            }
        }
    }

    /**
     * Ã¥ÂÂÃ¦ÂÂ¶Ã¥ÂÂÃ¤Â¸ÂªÃ§ÂÂ©Ã¥ÂÂÃ£ÂÂ
     */
    public RecycleResult recycle(Player player, ItemStack item) {
        RecycleEntry entry = findEntry(item);
        if (entry == null) return RecycleResult.fail("Ã¨Â¯Â¥Ã§ÂÂ©Ã¥ÂÂÃ¤Â¸ÂÃ¥ÂÂ¯Ã¥ÂÂÃ¦ÂÂ¶");

        double pricePerUnit = entry.price();
        double multiplier = getMultiplier(player);
        double total = pricePerUnit * item.getAmount() * multiplier;

        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(entry.currency());
        if (bridge == null || !bridge.available()) {
            return RecycleResult.fail("Ã¨Â´Â§Ã¥Â¸ÂÃ§Â³Â»Ã§Â»ÂÃ¤Â¸ÂÃ¥ÂÂ¯Ã§ÂÂ¨");
        }
        CurrencyTransactionResult result = bridge.deposit(player, BigDecimal.valueOf(total));
        if (!result.success()) {
            return RecycleResult.fail("Ã¥Â­ÂÃ¦Â¬Â¾Ã¥Â¤Â±Ã¨Â´Â¥");
        }

        int count = item.getAmount();
        item.setAmount(0);

        // Ã§Â»ÂÃ¨Â®Â¡
        repository.addRecycleStats(player.getUniqueId(), entry.currency(), total, count);

        return RecycleResult.success(total, entry.currency(), count, Map.of(entry.currency(), total));
    }

    /**
     * Ã¦ÂÂ¹Ã©ÂÂÃ¥ÂÂÃ¦ÂÂ¶Ã¨ÂÂÃ¥ÂÂÃ¦ÂÂÃ¦ÂÂÃ¥ÂÂ¯Ã¥ÂÂÃ¦ÂÂ¶Ã§ÂÂ©Ã¥ÂÂÃ£ÂÂ
     */
    public RecycleResult recycleBatch(Player player) {
        double totalEarnings = 0;
        int totalItems = 0;
        String mainCurrency = config.defaultCurrency();
        Map<String, Double> earningsByCurrency = new LinkedHashMap<>();

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;

            RecycleEntry entry = findEntry(item);
            if (entry == null) continue;

            double multiplier = getMultiplier(player);
            double total = entry.price() * item.getAmount() * multiplier;

            CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(entry.currency());
            if (bridge == null || !bridge.available()) continue;

            CurrencyTransactionResult result = bridge.deposit(player, BigDecimal.valueOf(total));
            if (!result.success()) continue;

            totalItems += item.getAmount();
            earningsByCurrency.merge(entry.currency(), total, Double::sum);
            totalEarnings += total;

            repository.addRecycleStats(player.getUniqueId(), entry.currency(), total, item.getAmount());
            player.getInventory().setItem(i, null);
        }

        if (totalItems == 0) {
            return RecycleResult.fail(messages.recycleNothing());
        }

        return RecycleResult.success(totalEarnings, mainCurrency, totalItems, earningsByCurrency);
    }

    /**
     * Ã¦Â£ÂÃ¦ÂÂ¥Ã§ÂÂ©Ã¥ÂÂÃ¦ÂÂ¯Ã¥ÂÂ¦Ã¥ÂÂ¯Ã¥ÂÂÃ¦ÂÂ¶Ã£ÂÂ
     */
    public boolean isRecyclable(ItemStack item) {
        return findEntry(item) != null;
    }

    /**
     * Ã¨ÂÂ·Ã¥ÂÂÃ§ÂÂ©Ã¥ÂÂÃ¥ÂÂÃ¦ÂÂ¶Ã¤Â»Â·Ã¦Â Â¼Ã£ÂÂ
     */
    public @Nullable String getRecycleCurrency(ItemStack item) {
        RecycleEntry entry = findEntry(item);
        return entry == null ? null : entry.currency();
    }

    public double getRecyclePrice(ItemStack item, Player player) {
        RecycleEntry entry = findEntry(item);
        if (entry == null) return 0;
        return entry.price() * getMultiplier(player);
    }

    public Map<String, RecycleEntry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    private @Nullable RecycleEntry findEntry(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        for (RecycleEntry entry : entries.values()) {
            String source = entry.source();
            String itemId = identifyItemId(item, source);
            if (itemId != null && entry.itemId().equalsIgnoreCase(itemId)) return entry;
        }
        return null;
    }

    private @Nullable String identifyItemId(ItemStack item, String source) {
        if (source == null || source.isBlank() || "minecraft".equalsIgnoreCase(source)) {
            return item.getType().name();
        }
        if (itemSourceRegistry == null) return null;
        try {
            return switch (source.toLowerCase(java.util.Locale.ROOT)) {
                case "mythic", "mythicmobs" -> itemSourceRegistry.mythicBridgeAvailable()
                    ? itemSourceRegistry.mythicItemId(item) : null;
                case "neige", "neigeitems" -> itemSourceRegistry.neigeBridgeAvailable()
                    ? itemSourceRegistry.neigeItemId(item) : null;
                case "overture" -> itemSourceRegistry.overtureBridgeAvailable()
                    ? itemSourceRegistry.overtureItemId(item) : null;
                default -> null;
            };
        } catch (RuntimeException ex) {
            logger.log(Level.FINE, "[Market-Recycle] item source identification failed: " + source, ex);
            return null;
        }
    }

    public double getMultiplier(Player player) {
        for (var entry : config.priceMultiplier().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 1.0;
    }

    // Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂ Ã§Â»ÂÃ¦ÂÂ Ã¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂÃ¢ÂÂ

    public record RecycleResult(boolean success, @Nullable String error, double totalAmount, @Nullable String currency, int itemCount, Map<String, Double> earningsByCurrency) {
        public static RecycleResult success(double amount, String currency, int count, Map<String, Double> earnings) {
            return new RecycleResult(true, null, amount, currency, count,
                Collections.unmodifiableMap(new LinkedHashMap<>(earnings)));
        }
        public static RecycleResult fail(String error) {
            return new RecycleResult(false, error, 0, null, 0, Map.of());
        }
    }
}
