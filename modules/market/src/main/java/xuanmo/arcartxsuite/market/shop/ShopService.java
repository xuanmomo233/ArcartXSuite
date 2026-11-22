package xuanmo.arcartxsuite.market.shop;

import xuanmo.arcartxsuite.market.MarketWork;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
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
import xuanmo.arcartxsuite.market.storage.MarketOperation;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.ShopCheckout;

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
    private final MarketWork work;
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
    private volatile long paymentGeneration;
    private final java.util.concurrent.ConcurrentLinkedQueue<QueuedPayment> payments = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.Semaphore backgroundDebits = new java.util.concurrent.Semaphore(4);
    private static final int PAYMENTS_PER_TICK = 4;
    private static final long PAYMENT_BUDGET_NANOS = 2_000_000L;
    private volatile LimitUpdateBroadcaster limitBroadcaster;
    private volatile DynamicShopService dynamicShopService;

    /** 单次购买数量上限（36 格 × 64），防止客户端传入超大值导致整数溢出 / 刷物品。 */
    private static final int MAX_PURCHASE_AMOUNT = 36 * 64;

    public ShopService(MarketWork work, JavaPlugin plugin, ShopConfiguration config, MessagesConfiguration messages,
                       MarketRepository repository, CurrencyBridgeAPI currencyManager,
                       ItemSourceRegistry itemSourceRegistry, ScriptConditionEvaluator conditionEvaluator,
                       File dataFolder, Logger logger) {
        this.plugin = plugin;this.work = work;
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
        refreshTask = work.repeatAsync(this::refreshStocks, config.refreshIntervalTicks(), config.refreshIntervalTicks());
        logger.info("[Shop] 系统商店已加载 " + shops.size() + " 个商店");
    }

    public void shutdown() {
        paymentGeneration++;
        for (QueuedPayment payment; (payment = payments.poll()) != null; ) {
            finishAsync(payment, CurrencyTransactionResult.failure("商店已重载，请重新报价"));
        }
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
        return checkout(player, shopId, itemId, amount, expectedUnitPrice, null,
            UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public BuyResult checkout(Player player, String shopId, String itemId, int amount, @Nullable BigDecimal expectedUnitPrice,
                              @Nullable ShopItem expectedItem, String quote, String request) {
        var session = AuthAccess.snapshot(player.getUniqueId());
        long generation = paymentGeneration;
        try {
            requireMarketAccess(player, session);
            var plan = prepareCheckout(player, shopId, itemId, amount, expectedUnitPrice, expectedItem, quote, request);
            var payment = armPayment(plan);
            if (payment == null) return previousOrder(plan, quote);
            var rejection = debitRejection(player, plan, session, generation);
            return finishPayment(payment, rejection == null ? invokePayment(player, plan) : rejection);
        } catch (CheckoutRejected rejection) {
            return BuyResult.fail(rejection.getMessage());
        }
    }

    public void checkoutAsync(Player player, String shopId, String itemId, int amount, BigDecimal expectedUnitPrice,
                              ShopItem expectedItem, String quote, String request, java.util.function.Consumer<BuyResult> reply) {
        var session = AuthAccess.snapshot(player.getUniqueId());
        long generation = paymentGeneration;
        PreparedCheckout plan;
        try {
            requireMarketAccess(player, session);
            plan = prepareCheckout(player, shopId, itemId, amount, expectedUnitPrice, expectedItem, quote, request);
        } catch (CheckoutRejected rejection) {
            reply.accept(BuyResult.fail(rejection.getMessage()));
            return;
        }
        var backgroundDebit = plan.bridge().prepareBackgroundDebit(player, plan.totalPrice());
        work.async(() -> {
            try {
                var payment = armPayment(plan);
                if (payment == null) {
                    replyOnMain(reply, previousOrder(plan, quote));
                    return;
                }
                if (generation != paymentGeneration) {
                    replyOnMain(reply, finishPayment(payment, CurrencyTransactionResult.failure("商店已重载，请重新报价")));
                    return;
                }
                var queued = new QueuedPayment(player, session, payment, reply, generation, backgroundDebit);
                payments.add(queued);
                if (generation != paymentGeneration && payments.remove(queued)) {
                    replyOnMain(reply, finishPayment(payment, CurrencyTransactionResult.failure("商店已重载，请重新报价")));
                }
            } catch (RuntimeException exception) {
                replyOnMain(reply, unknownOrder(plan.order(), exception));
            }
        });
    }

    private void requireMarketAccess(Player player, AuthSnapshot session) {
        if (!session.admitted() || !player.hasPermission("arcartxsuite.market.use")) {
            throw new CheckoutRejected("当前无法使用市场，请重新报价");
        }
    }

    private ShopItem requirePurchasableItem(Player player, String shopId, String itemId, @Nullable ShopItem expectedItem) {
        ShopDefinition shop = getEffectiveShop(shopId);
        if (shop == null) throw new CheckoutRejected("商店不存在");
        if (!shop.permission().isEmpty() && !player.hasPermission(shop.permission())) {
            throw new CheckoutRejected("无权限访问该商店");
        }

        ShopItem shopItem = shop.items().get(itemId);
        if (shopItem == null) throw new CheckoutRejected("商品不存在");
        if (expectedItem != null && !expectedItem.equals(shopItem)) throw new CheckoutRejected("商品已变化，请重新报价");

        // 上架条件校验（AXS 统一条件系统）
        // 正常途径下不满足条件的商品不会出现在商店 UI 中，此处为伪造包兜底
        List<ScriptCondition> conditions = shopItem.conditions();
        if (conditions != null && !conditions.isEmpty()
            && conditionEvaluator.firstFailed(player, conditions) != null) {
            throw new CheckoutRejected(messages.shopConditionDenied());
        }
        return shopItem;
    }

    private PreparedCheckout prepareCheckout(Player player, String shopId, String itemId, int amount,
                                              @Nullable BigDecimal expectedUnitPrice, @Nullable ShopItem expectedItem,
                                              String quote, String request) {
        var shopItem = requirePurchasableItem(player, shopId, itemId, expectedItem);
        // 数量校验：防止客户端传入 <=0 或超大值（整数溢出绕过限购 / 刷物品）
        if (amount < 1 || amount > MAX_PURCHASE_AMOUNT) {
            throw new CheckoutRejected("购买数量非法");
        }

        BigDecimal unitPrice = displayBuyPriceDecimal(player, shopItem);
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(amount));
        if (expectedUnitPrice != null && unitPrice.compareTo(expectedUnitPrice) != 0) {
            throw new CheckoutRejected("商品价格已变化，请刷新并重新确认金额");
        }

        // 扣款
        CurrencyBridgeAPI.CurrencyBridge bridge = currencyManager.bridge(shopItem.currency());
        if (bridge == null || !bridge.available()) {
            throw new CheckoutRejected("货币系统不可用");
        }
        if (totalPrice.signum() > 0 && bridge.debitRequiresReconciliation()) {
            throw new CheckoutRejected("该货币无法确认扣款");
        }

        if (shopItem.type() == ShopItemType.COMMAND && !hasRewardCommands(shopItem)) {
            throw new CheckoutRejected("指令商品未配置有效的 reward-commands");
        }

        ItemStack preparedItem = shopItem.type() == ShopItemType.ITEM ? createItem(shopItem, amount, player) : null;
        if (shopItem.type() == ShopItemType.ITEM && preparedItem == null) throw new CheckoutRejected("物品生成失败");
        if (preparedItem != null && preparedItem.getAmount() != amount) throw new CheckoutRejected("物品来源未生成请求数量，已取消购买");
        String snapshot = preparedItem == null ? "" : java.util.Base64.getEncoder().encodeToString(
            xuanmo.arcartxsuite.api.util.ItemSerializer.serialize(preparedItem));
        String display = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', shopItem.displayName()));
        long now = System.currentTimeMillis();
        var order = new MarketOperation(request, request, player.getUniqueId(), "SHOP_BUY",
            orderReference(quote) + shopId + "/" + itemId, "RUNNING", shopItem.currency(), totalPrice.toPlainString(),
            snapshot, "已预留库存与限购，等待扣款", ledger.owner(), now, now,
            display.substring(0, Math.min(255, display.length())), preparedItem == null ? "COMMAND" : preparedItem.getType().name());
        var checkout = new ShopCheckout(shopId, itemId, amount, availabilityRequest(shopItem).stockMode(),
            shopItem.stockAmount(), shopItem.limitPerPlayer(), shopItem.limitReset());
        return new PreparedCheckout(order, checkout, bridge, totalPrice, shopItem, shopItem.displayName(),
            prepareRewards(player, shopItem, amount, order));
    }

    private @Nullable ArmedPayment armPayment(PreparedCheckout plan) {
        var reservation = repository.reserveShopOrder(plan.order(), plan.checkout(), plan.rewards());
        return reservation.started() ? new ArmedPayment(plan, ledger.beginShopDebit(plan.order())) : null;
    }

    private BuyResult previousOrder(PreparedCheckout plan, String quote) {
        var existing = repository.shopOrder(plan.order().id());
        return matchesOrder(existing, plan.order().player(), quote) ? orderResult(existing) : BuyResult.fail("订单请求无效");
    }

    private CurrencyTransactionResult invokePayment(Player player, PreparedCheckout plan) {
        return invokePayment(plan, () -> plan.bridge().withdraw(player, plan.totalPrice()));
    }

    private CurrencyTransactionResult invokePayment(PreparedCheckout plan, java.util.function.Supplier<CurrencyTransactionResult> debit) {
        try {
            return debit.get();
        } catch (RuntimeException | LinkageError exception) {
            logger.log(Level.WARNING, "系统商店扣款结果待核对 " + plan.order().id(), exception);
            return CurrencyTransactionResult.unknown("扣款结果不明，请核对交易流水");
        }
    }

    private BuyResult finishPayment(ArmedPayment payment, CurrencyTransactionResult result) {
        var plan = payment.plan();
        var order = plan.order();
        var checkout = plan.checkout();
        ledger.recordShopDebit(payment.debit(), result);
        if (result.uncertain()) {
            repository.transitionOperation(order.id(), "RUNNING", "REVIEW", "扣款结果不明，请核对交易流水", order.owner());
            return BuyResult.fail("扣款结果不明，请核对交易流水");
        }
        String failure = result.message().isBlank() ? messages.insufficientFunds() : result.message();
        repository.finishShopOrder(order, result.success(),
            result.success() ? "已扣款，奖励已进入交付队列" : failure);
        if (!result.success()) return BuyResult.fail(failure);
        if (limitBroadcaster != null && checkout.limit() > 0) {
            var latest = repository.getShopLimit(order.player(), checkout.shop(), checkout.item());
            if (latest != null) limitBroadcaster.broadcast(order.player(), checkout.shop(), checkout.item(), latest.purchasedCount(),
                latest.lastPurchaseTime(), latest.resetTime());
        }
        ledger.withShopOrder(order, () -> {
            recordHistory(plan);
            return null;
        });
        if (!order.itemData().isEmpty() && dynamicShopService != null) {
            dynamicShopService.recordPurchase(checkout.shop(), checkout.item(), checkout.quantity());
        }
        return BuyResult.success(plan.totalPrice().doubleValue(), order.currency());
    }

    public boolean hasQueuedPayments() {
        var payment = payments.peek();
        return payment != null && (payment.backgroundDebit() == null || backgroundDebits.availablePermits() > 0);
    }

    private boolean currentPaymentSession(AuthSnapshot session, long generation) {
        if (generation != paymentGeneration || work.isStopping()) return false;
        try {
            var current = AuthAccess.snapshot(session.playerId());
            return current.admitted() && current.generation() == session.generation()
                && Objects.equals(current.connectionId(), session.connectionId());
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "系统商店无法确认支付会话 " + session.playerId(), failure);
            return false;
        }
    }

    private @Nullable CurrencyTransactionResult debitRejection(Player player, PreparedCheckout plan, AuthSnapshot session,
                                                                long generation) {
        try {
            if (!currentPaymentSession(session, generation) || !player.isOnline() || Bukkit.getPlayer(session.playerId()) != player) {
                return CurrencyTransactionResult.failure("玩家会话或商店已变化，请重新报价");
            }
            requireMarketAccess(player, session);
            var item = requirePurchasableItem(player, plan.checkout().shop(), plan.checkout().item(), plan.item());
            var total = displayBuyPriceDecimal(player, item).multiply(BigDecimal.valueOf(plan.checkout().quantity()));
            if (plan.totalPrice().compareTo(total) != 0) {
                return CurrencyTransactionResult.failure("商品价格已变化，请刷新并重新确认金额");
            }
            return currentPaymentSession(session, generation) ? null
                : CurrencyTransactionResult.failure("玩家会话或商店已变化，请重新报价");
        } catch (CheckoutRejected rejection) {
            return CurrencyTransactionResult.failure(rejection.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "系统商店无法确认支付准入 " + plan.order().id(), failure);
            return CurrencyTransactionResult.failure("无法确认购买条件，请重新报价");
        }
    }

    public void drainPayments() {
        long start = System.nanoTime();
        for (int i = 0; i < PAYMENTS_PER_TICK; i++) {
            if (!hasQueuedPayments()) return;
            var queued = payments.poll();
            if (queued == null) return;
            var plan = queued.payment().plan();
            var player = queued.player();
            var rejection = debitRejection(player, plan, queued.session(), queued.generation());
            if (rejection != null) {
                finishAsync(queued, rejection);
            } else if (queued.backgroundDebit() != null) {
                dispatchBackgroundDebit(queued);
            } else {
                finishAsync(queued, invokePayment(player, plan));
            }
            if (System.nanoTime() - start >= PAYMENT_BUDGET_NANOS) return;
        }
    }

    private void dispatchBackgroundDebit(QueuedPayment queued) {
        // Only the main-thread queue consumer takes a permit. Workers never block waiting for one.
        if (!backgroundDebits.tryAcquire()) {
            payments.add(queued);
            return;
        }
        try {
            work.async(() -> {
                try {
                    var result = currentPaymentSession(queued.session(), queued.generation())
                        ? invokePayment(queued.payment().plan(), queued.backgroundDebit())
                        : CurrencyTransactionResult.failure("玩家会话或商店已变化，请重新报价");
                    finishOnWorker(queued, result);
                } finally {
                    backgroundDebits.release();
                }
            });
        } catch (RuntimeException exception) {
            backgroundDebits.release();
            finishAsync(queued, CurrencyTransactionResult.failure("订单支付未启动，请重新报价"));
        }
    }

    private void finishAsync(QueuedPayment queued, CurrencyTransactionResult result) {
        if (!plugin.isEnabled()) return;
        work.async(() -> finishOnWorker(queued, result));
    }

    private void finishOnWorker(QueuedPayment queued, CurrencyTransactionResult result) {
        BuyResult reply;
        try { reply = finishPayment(queued.payment(), result); }
        catch (RuntimeException exception) { reply = unknownOrder(queued.payment().plan().order(), exception); }
        replyOnMain(queued.reply(), reply);
    }

    private void replyOnMain(java.util.function.Consumer<BuyResult> reply, BuyResult result) {
        if (plugin.isEnabled()) work.main(() -> reply.accept(result));
    }

    private BuyResult unknownOrder(MarketOperation order, RuntimeException exception) {
        logger.log(Level.WARNING, "系统商店订单待核对 " + order.id(), exception);
        return BuyResult.pending("订单结果待确认，请使用原请求查询");
    }

    private record PreparedCheckout(MarketOperation order, ShopCheckout checkout, CurrencyBridgeAPI.CurrencyBridge bridge,
                                    BigDecimal totalPrice, ShopItem item, String displayName, List<MarketOperation> rewards) { }
    private record ArmedPayment(PreparedCheckout plan, MarketOperation debit) { }
    private record QueuedPayment(Player player, AuthSnapshot session, ArmedPayment payment, java.util.function.Consumer<BuyResult> reply,
                                 long generation, @Nullable java.util.function.Supplier<CurrencyTransactionResult> backgroundDebit) { }

    private static final class CheckoutRejected extends RuntimeException {
        CheckoutRejected(String message) { super(message, null, false, false); }
    }

    private static String orderReference(String quote) { return "shop-order:" + quote + ":"; }

    public static boolean matchesOrder(@Nullable MarketOperation order, UUID player, String quote) {
        return order != null && player.equals(order.player()) && order.reference().startsWith(orderReference(quote));
    }

    public static BuyResult orderResult(MarketOperation order) {
        return switch (order.state()) {
            case "DONE" -> BuyResult.success(new BigDecimal(order.amount()).doubleValue(), order.currency());
            case "RUNNING" -> BuyResult.pending("订单处理中，可用原请求查询");
            case "REFUND_PENDING" -> BuyResult.pending("订单退款处理中，可用原请求查询");
            default -> BuyResult.fail(order.note());
        };
    }

    /**
     * 玩家是否满足商品上架条件（items.&lt;key&gt;.conditions，AXS 统一条件系统评估）。
     */
    public boolean meetsConditions(Player player, ShopItem item) {
        List<ScriptCondition> conditions = item.conditions();
        if (conditions == null || conditions.isEmpty()) return true;
        return conditionEvaluator.passes(player, conditions);
    }

    private void recordHistory(PreparedCheckout plan) {
        try {
            var order = plan.order();
            repository.insertHistory(new xuanmo.arcartxsuite.market.storage.AuctionHistory(
                0, 0, order.player(), null, order.itemData(), plan.displayName() + " ×" + plan.checkout().quantity(),
                plan.totalPrice().doubleValue(), order.currency(), 0, "SHOP_BUY", System.currentTimeMillis()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "[Shop] 交易已完成，但历史写入失败: " + plan.order().id(), exception);
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

    private boolean hasRewardCommands(ShopItem shopItem) {
        return shopItem.rewardCommands().stream().anyMatch(command -> command != null && !command.isBlank());
    }

    /**
     * 投递商品奖励命令。命令采用“提交即成功”语义，不依据 Bukkit 的返回值退款，
     * 与 lottery 奖励命令保持一致；返回值为 false 的单条命令仅记录警告。
     */
    private List<MarketOperation> prepareRewards(Player player, ShopItem item, int amount, MarketOperation order) {
        List<MarketOperation> rewards = new ArrayList<>();
        if (!order.itemData().isEmpty()) rewards.add(reward(order, "ITEM", order.itemData(), "shop_item:" + item.key()));
        for(String raw:item.rewardCommands()) {
            if(raw==null || raw.isBlank()) continue;
            String name=xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
            String command=raw.replace("{player}",name).replace("{uuid}",player.getUniqueId().toString()).replace("{amount}",Integer.toString(amount));
            if(command.startsWith("/"))command=command.substring(1);
            rewards.add(reward(order, "COMMAND", command, "shop_command:" + item.key()));
        }
        return rewards;
    }

    private static MarketOperation reward(MarketOperation order, String kind, String payload, String reference) {
        return new MarketOperation(UUID.randomUUID().toString(), order.id(), order.player(), kind, reference,
            "PREPARED", "", "0", payload, "已保存快照，尚未确认扣款", order.owner(),
            order.createdAt(), order.updatedAt(), order.itemName(), order.material());
    }

    private @Nullable ItemStack createItem(ShopItem shopItem, int amount, Player player) {
        return ShopItemFactory.create(itemSourceRegistry, shopItem.item(), amount, player, logger);
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
                                BigDecimal unitPrice, String currency, @Nullable ItemStack preview) {}

    public ShopCheckout.Product availabilityRequest(ShopItem item) {
        return new ShopCheckout.Product(item.key(), hasLimitedStock(item) ? item.stockMode().toLowerCase(java.util.Locale.ROOT) : "unlimited",
            hasLimitedStock(item) ? item.stockAmount() : MAX_PURCHASE_AMOUNT,
            item.limitPerPlayer());
    }

    public java.util.function.Supplier<BigDecimal> prepareQuoteBalance(Player player, ShopItem item) {
        var bridge = currencyManager.bridge(item.currency());
        if (bridge == null || !bridge.available()) return null;
        return bridge.prepareBackgroundBalance(player);
    }

    public PurchaseQuote quote(Player player, ShopItem item, ShopCheckout.Availability availability, @Nullable BigDecimal quotedBalance) {
        int stock = availability.stock();
        int limit = availability.limit();
        int capacity = MAX_PURCHASE_AMOUNT;
        ItemStack sample = null;
        if (item.type() == ShopItemType.ITEM) {
            sample = createItem(item, 1, player);
            if (sample == null) return null;
            capacity = 0;
            for (ItemStack current : player.getInventory().getStorageContents()) {
                if(current==null || current.getType().isAir()) capacity+=sample.getMaxStackSize();
                else if(current.isSimilar(sample)) capacity+=Math.max(0,sample.getMaxStackSize()-current.getAmount());
            }
        }
        BigDecimal unit=displayBuyPriceDecimal(player,item);
        var bridge=currencyManager.bridge(item.currency());
        int affordable=bridge==null || !bridge.available() ? 0 : unit.signum()==0 ? MAX_PURCHASE_AMOUNT
            : (quotedBalance == null ? bridge.balance(player) : quotedBalance).max(BigDecimal.ZERO).divide(unit,0,java.math.RoundingMode.DOWN)
                .min(BigDecimal.valueOf(MAX_PURCHASE_AMOUNT)).intValue();
        int maximum=Math.max(0,Math.min(MAX_PURCHASE_AMOUNT,Math.min(affordable,Math.min(stock,Math.min(limit,capacity)))));
        return new PurchaseQuote(maximum,stock,limit,capacity,BigDecimal.valueOf(item.buyPrice()),unit,item.currency(),sample);
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

    public record BuyResult(boolean success, @Nullable String error, double totalPrice, String currency, boolean pending) {
        public static BuyResult success(double price, String currency) {
            return new BuyResult(true, null, price, currency, false);
        }

        public static BuyResult fail(String error) {
            return new BuyResult(false, error, 0, "", false);
        }

        public static BuyResult pending(String message) {
            return new BuyResult(false, message, 0, "", true);
        }
    }
}
