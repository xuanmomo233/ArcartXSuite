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
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.MessagesConfiguration;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration.ShopConfiguration;
import xuanmo.arcartxsuite.market.dynamic.DynamicShopService;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/**
 * 系统商店业务服务。
 */
public class ShopService {
    private xuanmo.arcartxsuite.market.MarketLedger ledger;

    public void setLedger(xuanmo.arcartxsuite.market.MarketLedger ledger) { this.ledger = ledger; }


    /** 限购更新跨服广播回调（由 MarketService 注入，无注入时不广播）。 */
    public interface LimitUpdateBroadcaster {
        void broadcast(UUID player, String shopId, String itemId, int newCount, long timestamp, long resetTime);
    }

    private final JavaPlugin plugin;
    private final ShopConfiguration config;
    private final MessagesConfiguration messages;
    private final MarketRepository repository;
    private final CurrencyBridgeAPI currencyManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ScriptConditionEvaluator conditionEvaluator;
    private final File dataFolder;
    private final Logger logger;

    private final Map<String, ShopDefinition> shops = new LinkedHashMap<>();
    private final Map<String, Long> restockIntervals = new java.util.concurrent.ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    private volatile LimitUpdateBroadcaster limitBroadcaster;
    private volatile DynamicShopService dynamicShopService;

    /** 单次购买数量上限（36 格 × 64），防止客户端传入超大值导致整数溢出 / 刷物品。 */
    private static final int MAX_PURCHASE_AMOUNT = 36 * 64;

    public ShopService(JavaPlugin plugin, ShopConfiguration config, MessagesConfiguration messages,
                       MarketRepository repository, CurrencyBridgeAPI currencyManager,
                       ItemSourceRegistry itemSourceRegistry, ScriptConditionEvaluator conditionEvaluator,
                       File dataFolder, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.repository = repository;
        this.currencyManager = currencyManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.conditionEvaluator = conditionEvaluator;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    /** 注入跨服广播回调。 */
    public void setLimitBroadcaster(LimitUpdateBroadcaster broadcaster) {
        this.limitBroadcaster = broadcaster;
    }

    /** 注入动态商品策略服务。 */
    public void setDynamicShopService(DynamicShopService service) {
        this.dynamicShopService = service;
    }

    public void start() {
        loadShops();
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshStocks, config.refreshIntervalTicks(), config.refreshIntervalTicks());
        logger.info("[Shop] 系统商店已加载 " + shops.size() + " 个商店");
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

    private synchronized void loadShops() {
        shops.clear();
        restockIntervals.clear();
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
                YamlConfiguration yaml = loadEditableYaml(file);
                ShopDefinition shop = parseShop(shopId, yaml);
                if (shop != null) {
                    shops.put(shopId, shop);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Shop] 加载商店文件失败: " + file.getName(), e);
            }
        }
    }

    private @Nullable ShopDefinition parseShop(String shopId, YamlConfiguration yaml) {
        String displayName = yaml.getString("display-name", shopId);
        IconDefinition icon = IconDefinition.load(yaml, "icon");
        String permission = yaml.getString("permission", "");
        List<String> tags = yaml.getStringList("tags");

        ConfigurationSection itemsSec = yaml.getConfigurationSection("items");
        if (itemsSec == null) return null;

        Map<String, ShopItem> items = new LinkedHashMap<>();
        for (String itemKey : itemsSec.getKeys(false)) {
            ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemKey);
            if (itemSec == null) continue;

            ShopItemType type = ShopItemType.parse(itemSec.getString("type", "item"), logger, itemKey);
            IconDefinition itemIcon = IconDefinition.load(itemSec, "icon");
            List<String> rewardCommands = itemSec.getStringList("reward-commands");
            ConfigurationSection itemDefinitionSection = itemSec.getConfigurationSection("item");
            ItemDefinition itemDefinition = itemDefinitionSection == null
                ? new ItemDefinition("minecraft", "", null)
                : new ItemDefinition(
                    itemDefinitionSection.getString("source", "minecraft"),
                    itemDefinitionSection.getString("id", ""),
                    itemDefinitionSection.getString("nbt", null)
                );
            String itemDisplayName = itemSec.getString("display-name", itemDefinition.id());
            restockIntervals.put(shopId + "/" + itemKey, Math.max(0L, itemSec.getLong("restock-interval-seconds", 0)));
            double buyPrice = itemSec.getDouble("buy-price", 0);
            double sellPrice = itemSec.getDouble("sell-price", 0);
            // 价格非负校验：负价格可能导致玩家购买反而获得货币
            if (buyPrice < 0) {
                logger.warning("[Shop] 商品 " + itemKey + " 的 buy-price 为负数 (" + buyPrice + ")，已强制设为 0");
                buyPrice = 0;
            }
            if (sellPrice < 0) {
                logger.warning("[Shop] 商品 " + itemKey + " 的 sell-price 为负数 (" + sellPrice + ")，已强制设为 0");
                sellPrice = 0;
            }
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

            // 上架条件：走 AXS 统一条件系统（内联行 / Map 列表 / 嵌套节三种形态均可）
            List<ScriptCondition> conditions = ScriptConditionsLoader.loadModuleConditions(
                itemSec, logger, "[Shop] 商品 " + itemKey + " 的 conditions 配置无效: ");

            items.put(itemKey, new ShopItem(
                itemKey, type, itemIcon, rewardCommands, itemDefinition,
                itemDisplayName, buyPrice, sellPrice, currency,
                stockMode, stockAmount, limitPerPlayer, limitReset, discount, conditions
            ));
        }

        return new ShopDefinition(shopId, displayName, icon, permission, tags, items);
    }

    /**
     * 购买系统商店物品。
     */
    public BuyResult buy(Player player, String shopId, String itemId, int amount) {
        return buy(player, shopId, itemId, amount, null);
    }

    public BuyResult buy(Player player, String shopId, String itemId, int amount, @Nullable BigDecimal expectedUnitPrice) {
        return ledger.trade("SHOP_BUY", player.getUniqueId(), shopId + "/" + itemId + ",amount=" + amount,
            () -> buyWork(player, shopId, itemId, amount, expectedUnitPrice), result -> result.success());
    }

public BuyResult buyWork(Player player, String shopId, String itemId, int amount, @Nullable BigDecimal expectedUnitPrice) {
        ShopDefinition shop = getEffectiveShop(shopId);
        if (shop == null) return BuyResult.fail("商店不存在");
        if (!shop.permission().isEmpty() && !player.hasPermission(shop.permission())) {
            return BuyResult.fail("无权限访问该商店");
        }

        ShopItem shopItem = shop.items().get(itemId);
        if (shopItem == null) return BuyResult.fail("商品不存在");
        ledger.describe(shopItem.displayName(),shopItem.type()==ShopItemType.COMMAND?"COMMAND":shopItem.item().id(),"");

        // 上架条件校验（AXS 统一条件系统）
        // 正常途径下不满足条件的商品不会出现在商店 UI 中，此处为伪造包兜底
        List<ScriptCondition> conditions = shopItem.conditions();
        if (conditions != null && !conditions.isEmpty()
            && conditionEvaluator.firstFailed(player, conditions) != null) {
            return BuyResult.fail(messages.shopConditionDenied());
        }

        // 数量校验：防止客户端传入 <=0 或超大值（整数溢出绕过限购 / 刷物品）
        if (amount < 1 || amount > MAX_PURCHASE_AMOUNT) {
            return BuyResult.fail("购买数量非法");
        }

        // 限购检查（用 long 运算，避免 int 溢出绕过）
        if (shopItem.limitPerPlayer() > 0) {
            ShopLimitRecord limit = repository.getShopLimit(player.getUniqueId(), shopId, itemId);
            long purchased = limit == null || (limit.resetTime() > 0 && limit.resetTime() <= System.currentTimeMillis())
                ? 0L : limit.purchasedCount();
            if (purchased + (long) amount > shopItem.limitPerPlayer()) {
                return BuyResult.fail(messages.shopLimitReached());
            }
        }

        // 库存检查（stock-mode: global / per-player）
        // 计算价格（含折扣），全程 BigDecimal 避免浮点累计误差
        BigDecimal unitPrice = BigDecimal.valueOf(shopItem.buyPrice());
        BigDecimal bestDiscount = null;
        for (var entry : shopItem.discount().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                BigDecimal discount = BigDecimal.valueOf(entry.getValue());
                if (bestDiscount == null || discount.compareTo(bestDiscount) < 0) {
                    bestDiscount = discount;
                }
            }
        }
        if (bestDiscount != null) {
            unitPrice = unitPrice.multiply(bestDiscount);
        }
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount));
        if (expectedUnitPrice != null && unitPrice.compareTo(expectedUnitPrice) != 0) {
            return BuyResult.fail("商品价格已变化，请刷新并重新确认金额");
        }

        // 扣款
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(shopItem.currency());
        if (bridge == null || !bridge.available()) {
            return BuyResult.fail("货币系统不可用");
        }

        if (shopItem.type() == ShopItemType.COMMAND && !hasRewardCommands(shopItem)) {
            return BuyResult.fail("指令商品未配置有效的 reward-commands");
        }

        ItemStack preparedItem = shopItem.type() == ShopItemType.ITEM ? createItem(shopItem, amount, player) : null;
        if (shopItem.type() == ShopItemType.ITEM && preparedItem == null) return BuyResult.fail("物品生成失败");
        if (preparedItem != null && preparedItem.getAmount() != amount) return BuyResult.fail("物品来源未生成请求数量，已取消购买");
        if(preparedItem!=null)ledger.describe(shopItem.displayName(),preparedItem.getType().name(),
            java.util.Base64.getEncoder().encodeToString(xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(preparedItem)));
        List<String> preparedRewards = prepareRewards(player,shopItem,amount,preparedItem);
        if (!tryConsumeStock(player, shopId, itemId, shopItem, amount)) {
            ledger.activatePrepared(preparedRewards,false);
            return BuyResult.fail("商品库存不足");
        }
        CurrencyTransactionResult result = bridge.withdraw(player, totalPrice);
        if (!result.success()) {
            ledger.activatePrepared(preparedRewards,false);
            restoreStock(player, shopId, itemId, shopItem, amount);
            return BuyResult.fail(messages.insufficientFunds());
        }

        ledger.activatePrepared(preparedRewards,true);

        // 生成物品
        if (shopItem.type() == ShopItemType.COMMAND) {
            updateLimit(player, shopId, itemId, shopItem, amount);
            recordHistory(player, shopItem, amount, totalPrice, null);
            return BuyResult.success(totalPrice.doubleValue(), shopItem.currency());
        }

        ItemStack item = preparedItem;
        int delivered = amount;

        // 更新限购记录（按实际成交数量计）
        updateLimit(player, shopId, itemId, shopItem, delivered);
        recordHistory(player, shopItem, delivered, unitPrice.multiply(BigDecimal.valueOf(delivered)), item);

        // 记录成交量（供需模式价格浮动策略据此调价）
        if (dynamicShopService != null) {
            dynamicShopService.recordPurchase(shopId, itemId, delivered);
        }

        return BuyResult.success(unitPrice.multiply(BigDecimal.valueOf(delivered)).doubleValue(), shopItem.currency());
    }

    /**
     * 玩家是否满足商品上架条件（items.&lt;key&gt;.conditions，AXS 统一条件系统评估）。
     */
    public boolean meetsConditions(Player player, ShopItem item) {
        List<ScriptCondition> conditions = item.conditions();
        if (conditions == null || conditions.isEmpty()) return true;
        return conditionEvaluator.passes(player, conditions);
    }

    private void recordHistory(Player player, ShopItem item, int amount, BigDecimal price, @Nullable ItemStack stack) {
        try {
        String data = "";
        if (stack != null) {
            ItemStack snapshot = stack.clone();
            snapshot.setAmount(amount);
            data = java.util.Base64.getEncoder().encodeToString(
                xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(snapshot));
        }
        repository.insertHistory(new xuanmo.arcartxsuite.market.storage.AuctionHistory(
            0, 0, player.getUniqueId(), null, data, item.displayName() + " ×" + amount,
            price.doubleValue(), item.currency(), 0, "SHOP_BUY", System.currentTimeMillis()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "[Shop] 交易已完成，但历史写入失败: " + player.getUniqueId(), exception);
        }
    }

    /**
     * 获取所有商店列表。
     */
    public Map<String, ShopDefinition> getShops() {
        return Collections.unmodifiableMap(shops);
    }

    /** 与成交使用相同折扣，向商品卡和确认弹窗显示实际单价。 */
    public double displayBuyPrice(Player player, ShopItem item) {
        return displayBuyPriceDecimal(player, item).doubleValue();
    }

    public BigDecimal displayBuyPriceDecimal(Player player, ShopItem item) {
        double discount = item.discount().entrySet().stream()
            .filter(entry -> player.hasPermission(entry.getKey()))
            .mapToDouble(Map.Entry::getValue).min().orElse(1.0);
        return BigDecimal.valueOf(item.buyPrice()).multiply(BigDecimal.valueOf(discount));
    }

    public @Nullable ShopDefinition getShop(String shopId) {
        return shops.get(shopId);
    }

    /**
     * 获取合并动态商品层和价格乘数后的有效商店定义。
     * <p>
     * 如果没有动态策略作用于该商店，返回与 {@link #getShop(String)} 相同的静态定义。
     *
     * @param shopId 商店 ID
     * @return 有效商店定义，商店不存在时返回 null
     */
    public @Nullable ShopDefinition getEffectiveShop(String shopId) {
        ShopDefinition staticShop = shops.get(shopId);
        if (staticShop == null) return null;
        if (dynamicShopService == null) return staticShop;

        // 合并动态商品
        Map<String, ShopItem> dynamicItems = dynamicShopService.getDynamicItems(shopId);
        Map<String, Double> multipliers = dynamicShopService.getPriceMultipliers(shopId);
        if (dynamicItems.isEmpty() && multipliers.isEmpty()) return staticShop;

        // 合并商品列表
        Map<String, ShopItem> effectiveItems = new LinkedHashMap<>(staticShop.items());
        effectiveItems.putAll(dynamicItems);

        // 应用价格乘数
        if (!multipliers.isEmpty()) {
            Map<String, ShopItem> pricedItems = new LinkedHashMap<>();
            for (Map.Entry<String, ShopItem> entry : effectiveItems.entrySet()) {
                String key = entry.getKey();
                ShopItem item = entry.getValue();
                double multiplier = multipliers.getOrDefault(key, 1.0);
                if (multiplier != 1.0) {
                    item = new ShopItem(
                        item.key(), item.type(), item.icon(), item.rewardCommands(),
                        item.item(), item.displayName(),
                        item.buyPrice() * multiplier,
                        item.sellPrice() * multiplier,
                        item.currency(), item.stockMode(), item.stockAmount(),
                        item.limitPerPlayer(), item.limitReset(), item.discount(),
                        item.conditions()
                    );
                }
                pricedItems.put(key, item);
            }
            effectiveItems = pricedItems;
        }

        return new ShopDefinition(
            staticShop.id(), staticShop.displayName(), staticShop.icon(),
            staticShop.permission(), staticShop.tags(), effectiveItems
        );
    }

    private void updateLimit(Player player, String shopId, String itemId, ShopItem shopItem, int delivered) {
        if (shopItem.limitPerPlayer() <= 0 || delivered <= 0) return;
        ShopLimitRecord existing = repository.getShopLimit(player.getUniqueId(), shopId, itemId);
        int newCount = (existing == null || (existing.resetTime() > 0 && existing.resetTime() <= System.currentTimeMillis())
            ? 0 : existing.purchasedCount()) + delivered;
        long now = System.currentTimeMillis();
        long resetTime = calculateResetTime(shopItem.limitReset());
        repository.upsertShopLimit(new ShopLimitRecord(
            player.getUniqueId(), shopId, itemId, newCount, now, resetTime
        ));
        // 跨服广播限购更新，其他服收到后 upsert 本地 DB
        if (limitBroadcaster != null) {
            limitBroadcaster.broadcast(player.getUniqueId(), shopId, itemId, newCount, now, resetTime);
        }
    }

    private boolean hasRewardCommands(ShopItem shopItem) {
        return shopItem.rewardCommands().stream().anyMatch(command -> command != null && !command.isBlank());
    }

    /**
     * 投递商品奖励命令。命令采用“提交即成功”语义，不依据 Bukkit 的返回值退款，
     * 与 lottery 奖励命令保持一致；返回值为 false 的单条命令仅记录警告。
     */
    private List<String> prepareRewards(Player player, ShopItem item, int amount, @Nullable ItemStack stack) {
        List<String> ids=new ArrayList<>();
        if(stack!=null) {
            String data=java.util.Base64.getEncoder().encodeToString(xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(stack));
            ids.add(ledger.prepareDelivery(xuanmo.arcartxsuite.market.MarketLedger.delivery(player.getUniqueId(),"ITEM","","0",data,"shop_item:"+item.key())));
        }
        for(String raw:item.rewardCommands()) {
            if(raw==null || raw.isBlank()) continue;
            String name=xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
            String command=raw.replace("{player}",name).replace("{uuid}",player.getUniqueId().toString()).replace("{amount}",Integer.toString(amount));
            if(command.startsWith("/"))command=command.substring(1);
            ids.add(ledger.prepareDelivery(xuanmo.arcartxsuite.market.MarketLedger.delivery(player.getUniqueId(),"COMMAND","","0",command,"shop_command:"+item.key())));
        }
        return ids;
    }

    private void depositSafe(Player player, String currency, BigDecimal amount, String reason) {
        if (amount != null && amount.signum() > 0) repository.addPendingCurrency(player.getUniqueId(), currency, amount.doubleValue(), reason);
    }

    private @Nullable ItemStack createItem(ShopItem shopItem, int amount, Player player) {
        return ShopItemFactory.create(itemSourceRegistry, shopItem.item(), amount, player, logger);
    }

    private long calculateResetTime(String resetType) {
        // 按自然日/周/月边界重置（基于服务器时区），而非固定毫秒偏移
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault());
        return switch (resetType.toLowerCase()) {
            case "daily" -> now.plusDays(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS).toInstant().toEpochMilli();
            case "weekly" -> now.plusWeeks(1)
                .with(java.time.DayOfWeek.MONDAY).truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .toInstant().toEpochMilli();
            case "monthly" -> now.plusMonths(1)
                .withDayOfMonth(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                .toInstant().toEpochMilli();
            default -> 0L;
        };
    }

    private synchronized void refreshStocks() {
        repository.resetExpiredShopLimits("auto");
        for (var shop : List.copyOf(shops.values())) for (var item : shop.items().values()) {
            long seconds = getRestockInterval(shop.id(), item.key());
            if (seconds > 0) repository.restockShopIfDue(shop.id(), item.key(), item.stockMode(), item.stockAmount(),
                Math.min(seconds, 31536000L) * 1000L);
        }
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
            repository.restoreGlobalShopStock(shopId, itemId, amount, shopItem.stockAmount());
        } else {
            repository.restorePlayerShopStock(player.getUniqueId(), shopId, itemId, amount, shopItem.stockAmount());
        }
    }

    // ─── 商店编辑（管理 UI 持久化）──────────────────────────

    /** 商店/商品 ID 合法字符（文件名与 YAML key 安全，杜绝路径穿越）。 */
    private static final java.util.regex.Pattern ID_PATTERN =
        java.util.regex.Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /** 按商店 ID 查找已有的 yml/yaml 文件，找不到返回 null。 */
    private @Nullable File findShopFile(String shopId) {
        File dir = new File(dataFolder, config.shopsDirectory());
        File yml = new File(dir, shopId + ".yml");
        if (yml.isFile()) return yml;
        File yaml = new File(dir, shopId + ".yaml");
        return yaml.isFile() ? yaml : null;
    }

    /**
     * 保存商店基础信息（新建或覆盖编辑），仅写管理字段，保留 items 节不动。
     *
     * @return null 表示成功，否则为错误信息
     */
    public synchronized @Nullable String saveShop(String shopId, boolean create, ShopEdit edit) {
        return saveShop(shopId, create, edit, null);
    }

    public synchronized @Nullable String saveShop(String shopId, boolean create, ShopEdit edit, @Nullable String expectedRevision) {
        if (!isValidId(shopId)) return "商店ID非法（仅允许字母/数字/_/-，1-64位）";
        File dir = new File(dataFolder, config.shopsDirectory());
        File file = findShopFile(shopId);
        if (!create && !matchesRevision(file, expectedRevision)) return "商店已被其他操作修改，请先重新加载再编辑（当前表单已保留）";
        if (create && file != null) return "商店ID已存在: " + shopId;
        if (!create && file == null) return "商店不存在: " + shopId;
        if (file == null) file = new File(dir, shopId + ".yml");
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return "商店目录创建失败";
            YamlConfiguration yaml = file.isFile()
                ? loadEditableYaml(file) : new YamlConfiguration();
            yaml.set("display-name",
                edit.displayName() == null || edit.displayName().isBlank() ? shopId : edit.displayName());
            yaml.set("permission", edit.permission() == null ? "" : edit.permission().trim());
            yaml.set("tags", edit.tags() == null ? List.of() : edit.tags());
            writeIcon(yaml, "icon", edit.icon());
            if (yaml.getConfigurationSection("items") == null) yaml.createSection("items");
            saveEditableYaml(yaml, file);
            loadShops();
            return null;
        } catch (Exception exception) {
            logger.log(Level.WARNING, "[Shop] 保存商店失败: " + shopId, exception);
            return "保存商店失败: " + exception.getMessage();
        }
    }

    /**
     * 删除商店：移除文件与内存定义。返回 null 表示成功。
     */
    public synchronized @Nullable String deleteShop(String shopId) {
        return deleteShop(shopId, null);
    }

    public synchronized @Nullable String deleteShop(String shopId, @Nullable String expectedRevision) {
        if (!isValidId(shopId)) return "商店ID非法";
        File file = findShopFile(shopId);
        if (!matchesRevision(file, expectedRevision)) return "商店已被其他操作修改，请先重新加载再编辑（当前表单已保留）";
        if (file == null) return "商店不存在: " + shopId;
        try { backupEditableFile(file); }
        catch (java.io.IOException exception) { return "备份商店失败: " + exception.getMessage(); }
        if (!file.delete()) return "删除商店文件失败: " + file.getName();
        shops.remove(shopId);
        return null;
    }

    /**
     * 保存商品（新建或覆盖编辑）。返回 null 表示成功。
     */
    public synchronized @Nullable String saveShopItem(String shopId, boolean create, ItemEdit edit) {
        return saveShopItem(shopId, create, edit, null);
    }

    public synchronized @Nullable String saveShopItem(String shopId, boolean create, ItemEdit edit, @Nullable String expectedRevision) {
        if (!isValidId(shopId)) return "商店ID非法";
        File file = findShopFile(shopId);
        if (!matchesRevision(file, expectedRevision)) return "商店已被其他操作修改，请先重新加载再编辑（当前表单已保留）";
        if (file == null) return "商店不存在: " + shopId;
        String key = edit.key();
        if (!isValidId(key)) return "商品ID非法（仅允许字母/数字/_/-，1-64位）";
        boolean command = "command".equalsIgnoreCase(edit.type());
        if (!command && (edit.itemId() == null || edit.itemId().isBlank())) {
            return "物品商品必须填写物品ID";
        }
        List<String> commands = edit.rewardCommands() == null ? List.of()
            : edit.rewardCommands().stream()
                .filter(c -> c != null && !c.isBlank()).map(String::trim).toList();
        if (command && commands.isEmpty()) {
            return "指令商品必须至少填写一条奖励指令";
        }
        try {
            YamlConfiguration yaml = loadEditableYaml(file);
            ConfigurationSection items = yaml.getConfigurationSection("items");
            if (items == null) items = yaml.createSection("items");
            if (create && items.contains(key)) return "商品ID已存在: " + key;
            if (!create && !items.isConfigurationSection(key)) return "商品不存在: " + key;
            String base = "items." + key + ".";
            yaml.set(base + "type", command ? "command" : "item");
            if (command) {
                yaml.set(base + "item", null);
            } else {
                yaml.set(base + "item.source", edit.itemSource());
                yaml.set(base + "item.id", edit.itemId());
                yaml.set(base + "item.nbt",
                    edit.itemNbt() == null || edit.itemNbt().isBlank() ? null : edit.itemNbt());
            }
            yaml.set(base + "display-name",
                edit.displayName() == null || edit.displayName().isBlank() ? key : edit.displayName());
            yaml.set(base + "buy-price", Math.max(0, edit.buyPrice()));
            yaml.set(base + "sell-price", Math.max(0, edit.sellPrice()));
            yaml.set(base + "currency",
                edit.currency() == null || edit.currency().isBlank()
                    ? config.defaultCurrency() : edit.currency());
            yaml.set(base + "stock-mode", edit.stockMode());
            yaml.set(base + "stock-amount", Math.max(0, edit.stockAmount()));
            yaml.set(base + "limit-per-player", Math.max(0, edit.limitPerPlayer()));
            yaml.set(base + "limit-reset", edit.limitReset());
            yaml.set(base + "restock-interval-seconds", edit.restockIntervalSeconds());
            yaml.set(base + "reward-commands", commands.isEmpty() ? null : commands);
            ConfigurationSection itemSec = yaml.getConfigurationSection("items." + key);
            // discount 的 key 是权限节点（含 '.'），必须以 Map 整体写入避免路径拆分
            itemSec.set("discount", null);
            if (edit.discount() != null && !edit.discount().isEmpty()) {
                itemSec.set("discount", new LinkedHashMap<>(edit.discount()));
            }
            // conditions 以原始表达式行写回（loader 支持内联行形态，round-trip 完整）
            List<String> conditionLines = edit.conditionLines() == null ? List.of()
                : edit.conditionLines().stream()
                    .filter(c -> c != null && !c.isBlank()).map(String::trim).toList();
            List<String> previousLines = ScriptConditionsLoader.loadModuleConditions(
                    itemSec, logger, "[Shop] 条件配置读取失败: ").stream().map(ScriptCondition::raw)
                    .filter(java.util.Objects::nonNull).toList();
            // Preserve structured condition metadata when the expression text was not edited.
            if (create || !previousLines.equals(conditionLines)) {
                itemSec.set("conditions", conditionLines.isEmpty() ? null : conditionLines);
            }
            writeIcon(itemSec, "icon", edit.icon());
            saveEditableYaml(yaml, file);
            loadShops();
            return null;
        } catch (Exception exception) {
            logger.log(Level.WARNING, "[Shop] 保存商品失败: " + shopId + "/" + key, exception);
            return "保存商品失败: " + exception.getMessage();
        }
    }

    /**
     * 删除商品并回写商店文件。返回 null 表示成功。
     */
    public synchronized @Nullable String deleteShopItem(String shopId, String itemKey) {
        return deleteShopItem(shopId, itemKey, null);
    }

    public synchronized @Nullable String deleteShopItem(String shopId, String itemKey, @Nullable String expectedRevision) {
        if (!isValidId(shopId) || !isValidId(itemKey)) return "ID非法";
        File file = findShopFile(shopId);
        if (!matchesRevision(file, expectedRevision)) return "商店已被其他操作修改，请先重新加载再编辑（当前表单已保留）";
        if (file == null) return "商店不存在: " + shopId;
        try {
            YamlConfiguration yaml = loadEditableYaml(file);
            if (!yaml.isConfigurationSection("items." + itemKey)) {
                return "商品不存在: " + itemKey;
            }
            yaml.set("items." + itemKey, null);
            saveEditableYaml(yaml, file);
            loadShops();
            return null;
        } catch (Exception exception) {
            logger.log(Level.WARNING, "[Shop] 删除商品失败: " + shopId + "/" + itemKey, exception);
            return "删除商品失败: " + exception.getMessage();
        }
    }

    public record EditableShop(ShopDefinition shop, String revision) {}

    /** Parse and fingerprint the same bytes, so externally edited files are not silently overwritten. */
    public synchronized @Nullable EditableShop editableShop(String shopId) {
        if (!isValidId(shopId)) return null;
        File file = findShopFile(shopId);
        if (file == null) return null;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            ShopDefinition definition = parseShop(shopId, yaml);
            if (definition == null) return null;
            return new EditableShop(definition, fingerprint(bytes));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "[Shop] 无法读取编辑快照: " + shopId, exception);
            return null;
        }
    }

    private static String fingerprint(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static boolean matchesRevision(@Nullable File file, @Nullable String expected) {
        if (expected == null) return true; // Existing non-UI callers retain their contract.
        if (file == null || expected.isBlank()) return false;
        try { return expected.equals(fingerprint(java.nio.file.Files.readAllBytes(file.toPath()))); }
        catch (Exception exception) { return false; }
    }

    private static YamlConfiguration loadEditableYaml(File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    private static void backupEditableFile(File file) throws java.io.IOException {
        if (file.isFile()) {
            java.nio.file.Files.copy(file.toPath(),
                file.toPath().resolveSibling(file.getName() + "." + java.util.UUID.randomUUID() + ".bak"));
        }
    }

    private static void saveEditableYaml(YamlConfiguration yaml, File file) throws java.io.IOException {
        backupEditableFile(file);
        yaml.save(file);
    }

    /** 按编辑模式写入 icon 节：keep 不动 / none 移除 / material / json。 */
    private void writeIcon(ConfigurationSection parent, String key, @Nullable IconEdit icon) {
        if (icon == null || "keep".equals(icon.mode())) return;
        parent.set(key, null);
        switch (icon.mode()) {
            case "json" -> {
                if (icon.json() != null && !icon.json().isBlank()) {
                    parent.set(key + ".json", icon.json());
                }
            }
            case "material" -> {
                if (icon.material() != null && !icon.material().isBlank()) {
                    parent.set(key + ".material", icon.material());
                }
            }
            default -> { /* none: 仅移除 */ }
        }
    }

    /** 图标编辑输入：mode = keep（不修改）/ none（移除）/ material / json（物品 JSON）。 */
    public record IconEdit(String mode, String material, String json) {
        public static IconEdit keep() { return new IconEdit("keep", "", ""); }
        public static IconEdit none() { return new IconEdit("none", "", ""); }
        public static IconEdit ofMaterial(String material) { return new IconEdit("material", material, ""); }
        public static IconEdit ofJson(String json) { return new IconEdit("json", "", json); }
    }

    /** 商店编辑输入。 */
    public record ShopEdit(String displayName, String permission, List<String> tags, IconEdit icon) {}

    /** 商品编辑输入。 */
    public record ItemEdit(
        String key, String type, String displayName,
        double buyPrice, double sellPrice, String currency,
        String stockMode, int stockAmount, int limitPerPlayer, String limitReset,
        List<String> conditionLines,
        List<String> rewardCommands, Map<String, Double> discount,
        String itemSource, String itemId, @Nullable String itemNbt,
        IconEdit icon, long restockIntervalSeconds
    ) {}

    public long getRestockInterval(String shop, String item) { return restockIntervals.getOrDefault(shop + "/" + item, 0L); }

    public String manageStock(String shopId, String itemId, String action, int amount, @Nullable UUID target) {
        EditableShop snapshot = editableShop(shopId);
        ShopDefinition shop = snapshot == null ? null : snapshot.shop();
        ShopItem item = shop == null ? null : shop.items().get(itemId);
        if (item == null) return "商品不存在，请先保存商品";
        if ("inspect".equals(action)) return null;
        if ("reset-limit".equals(action)) { repository.resetShopLimits(shopId,itemId,target); return null; }
        if ("global".equals(item.stockMode()) && target != null) return "全服库存不能按玩家操作，请清空目标 UUID";
        return repository.adjustShopStock(shopId,itemId,item.stockMode(),item.stockAmount(),action,amount,target)
            ? null : "库存操作无效；有限库存的数量不得超过配置上限";
    }

    public record PurchaseQuote(int maximum, int stock, int limit, int capacity, BigDecimal basePrice,
                                BigDecimal unitPrice, String currency) {}

    public PurchaseQuote quote(Player player, String shopId, String itemId) {
        ShopDefinition shop = getEffectiveShop(shopId);
        if (shop == null || (!shop.permission().isEmpty() && !player.hasPermission(shop.permission()))) return null;
        ShopItem item = shop.items().get(itemId);
        if (item == null || !meetsConditions(player,item)) return null;
        int stock = MAX_PURCHASE_AMOUNT;
        if (hasLimitedStock(item)) stock = "global".equalsIgnoreCase(item.stockMode())
            ? repository.getGlobalShopStock(shopId,itemId,item.stockAmount())
            : repository.getPlayerShopStock(player.getUniqueId(),shopId,itemId,item.stockAmount());
        int limit = MAX_PURCHASE_AMOUNT;
        if (item.limitPerPlayer() > 0) {
            ShopLimitRecord record = repository.getShopLimit(player.getUniqueId(),shopId,itemId);
            int used = record == null || (record.resetTime()>0 && record.resetTime()<=System.currentTimeMillis()) ? 0 : record.purchasedCount();
            limit = Math.max(0,item.limitPerPlayer()-used);
        }
        int capacity = MAX_PURCHASE_AMOUNT;
        if (item.type() == ShopItemType.ITEM) {
            ItemStack sample = createItem(item,1,player);
            if (sample == null) return null;
            capacity=0;
            for (int slot=0;slot<36;slot++) {
                ItemStack current=player.getInventory().getItem(slot);
                if(current==null || current.getType().isAir()) capacity+=sample.getMaxStackSize();
                else if(current.isSimilar(sample)) capacity+=Math.max(0,sample.getMaxStackSize()-current.getAmount());
            }
        }
        BigDecimal unit=displayBuyPriceDecimal(player,item);
        var bridge=currencyManager.bridge(item.currency());
        int affordable=bridge==null || !bridge.available() ? 0 : unit.signum()==0 ? MAX_PURCHASE_AMOUNT
            : bridge.balance(player).max(BigDecimal.ZERO).divide(unit,0,java.math.RoundingMode.DOWN)
                .min(BigDecimal.valueOf(MAX_PURCHASE_AMOUNT)).intValue();
        int maximum=Math.max(0,Math.min(MAX_PURCHASE_AMOUNT,Math.min(affordable,Math.min(stock,Math.min(limit,capacity)))));
        return new PurchaseQuote(maximum,stock,limit,capacity,BigDecimal.valueOf(item.buyPrice()),unit,item.currency());
    }

    // ─── 数据模型 ───────────────────────────────────────────

    public record ShopDefinition(
        String id, String displayName, @Nullable IconDefinition icon, String permission,
        List<String> tags, Map<String, ShopItem> items
    ) {}

    public enum ShopItemType {
        ITEM, COMMAND;

        public static ShopItemType parse(String value, Logger logger, String itemKey) {
            if (value == null || value.isBlank()) return ITEM;
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                logger.warning("[Shop] 商品 " + itemKey + " 的 type 无效，已回落为 item");
                return ITEM;
            }
        }
    }

    public record ItemDefinition(String source, String id, @Nullable String nbt) {
        public ItemDefinition {
            source = source == null || source.isBlank() ? "minecraft" : source;
            id = id == null ? "" : id;
        }
    }

    /**
     * @param conditions 商品上架条件（配置节 items.&lt;key&gt;.conditions），
     *                   由 AXS 统一条件系统（{@link ScriptConditionEvaluator}）评估，
     *                   支持 PAPI 占位符 / aria: / js: / {context} 表达式；
     *                   不满足条件的商品对该玩家不显示且无法购买
     */
    public record ShopItem(
        String key, ShopItemType type, @Nullable IconDefinition icon, List<String> rewardCommands,
        ItemDefinition item, String displayName, double buyPrice, double sellPrice,
        String currency, String stockMode, int stockAmount,
        int limitPerPlayer, String limitReset, Map<String, Double> discount,
        List<ScriptCondition> conditions
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
