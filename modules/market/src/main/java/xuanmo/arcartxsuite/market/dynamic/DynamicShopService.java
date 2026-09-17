package xuanmo.arcartxsuite.market.dynamic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;
import xuanmo.arcartxsuite.market.shop.ShopService;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopDefinition;
import xuanmo.arcartxsuite.market.shop.ShopService.ShopItem;
import xuanmo.arcartxsuite.market.storage.MarketRepository;

/**
 * 动态商品策略管理服务。
 * <p>
 * 负责：
 * <ul>
 *   <li>加载和注册所有动态策略</li>
 *   <li>通过 {@link SchedulerAPI} 调度策略触发</li>
 *   <li>持久化策略状态到数据库</li>
 *   <li>跨服同步动态商品和价格变动</li>
 *   <li>发布 EventBus 事件（神秘商人降临等）</li>
 * </ul>
 *
 * @since 1.5.0
 */
public class DynamicShopService {

    /** 跨服广播回调（由 MarketService 注入）。 */
    public interface DynamicStateBroadcaster {
        void broadcast(String action, String strategyKey, String shopId, String data);
    }

    /** EventBus 发布回调（由 MarketService 注入）。 */
    public interface EventPublisher {
        void publish(String topic, Map<String, String> payload);
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private final SchedulerAPI scheduler;
    private final ShopService shopService;
    private final MarketRepository repository;
    private final List<DynamicStrategyConfig> strategyConfigs;

    // 策略实例：strategyKey -> 策略实例
    private final Map<String, DynamicShopStrategy> strategies = new ConcurrentHashMap<>();
    // 策略上下文：strategyKey -> 上下文
    private final Map<String, DynamicShopContextImpl> contexts = new ConcurrentHashMap<>();
    // 调度任务句柄
    private final List<ScheduledTask> scheduledTasks = new ArrayList<>();

    private volatile DynamicStateBroadcaster stateBroadcaster;
    private volatile EventPublisher eventPublisher;

    public DynamicShopService(
        JavaPlugin plugin, Logger logger, SchedulerAPI scheduler,
        ShopService shopService, MarketRepository repository,
        List<DynamicStrategyConfig> strategyConfigs
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.scheduler = scheduler;
        this.shopService = shopService;
        this.repository = repository;
        this.strategyConfigs = strategyConfigs;
    }

    public void setStateBroadcaster(DynamicStateBroadcaster broadcaster) {
        this.stateBroadcaster = broadcaster;
    }

    public void setEventPublisher(EventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    // ── 生命周期 ──────────────────────────────────────────

    public void start() {
        for (DynamicStrategyConfig config : strategyConfigs) {
            try {
                registerStrategy(config);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[DynamicShop] 注册策略失败: " + config.strategyKey(), e);
            }
        }
        logger.info("[DynamicShop] 已启动 " + strategies.size() + " 个动态策略");
    }

    public void shutdown() {
        for (ScheduledTask task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
        // 持久化所有策略状态
        for (Map.Entry<String, DynamicShopContextImpl> entry : contexts.entrySet()) {
            try {
                persistState(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                logger.log(Level.WARNING, "[DynamicShop] 持久化策略状态失败: " + entry.getKey(), e);
            }
        }
        strategies.clear();
        contexts.clear();
    }

    // ── 策略注册与调度 ────────────────────────────────────

    private void registerStrategy(DynamicStrategyConfig config) {
        DynamicShopStrategy strategy = createStrategy(config);
        if (strategy == null) {
            logger.warning("[DynamicShop] 未知策略类型: " + config.type() + " (key=" + config.strategyKey() + ")");
            return;
        }

        // 创建上下文
        DynamicShopContextImpl context = new DynamicShopContextImpl(
            config.strategyKey(), config.shopId(), shopService, repository
        );
        context.setStrategyType(config.type());
        contexts.put(config.strategyKey(), context);
        strategies.put(config.strategyKey(), strategy);

        // 从数据库恢复状态
        DynamicShopState savedState = repository.getDynamicShopState(config.strategyKey());
        if (savedState != null && !savedState.stateJson().isBlank()) {
            context.setRestoredState(savedState.stateJson());
        }
        strategy.onLoad(context);

        // 调度策略触发
        ScheduleSpec spec = config.schedule();
        String taskKey = "market:dynamic:" + config.strategyKey();

        ScheduledTask task = scheduler.scheduleCalendarLeaderOnly(taskKey, plugin, () -> {
            try {
                triggerStrategy(config, strategy, context);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[DynamicShop] 策略触发异常: " + config.strategyKey(), t);
            }
        }, spec);
        scheduledTasks.add(task);

        logger.fine("[DynamicShop] 已注册策略: " + config.strategyKey()
            + " (type=" + config.type() + ", shop=" + config.shopId() + ", schedule=" + spec + ")");
    }

    private void triggerStrategy(DynamicStrategyConfig config, DynamicShopStrategy strategy, DynamicShopContextImpl context) {
        // random-refresh 策略需要概率判定
        if ("random-refresh".equalsIgnoreCase(config.type())) {
            if (Math.random() >= config.probability()) {
                logger.fine("[DynamicShop] 策略 " + config.strategyKey() + " 概率判定未通过，跳过");
                return;
            }
        }

        strategy.onTrigger(context);
        context.setLastTriggerTime(System.currentTimeMillis());

        // 持久化状态
        persistState(config.strategyKey(), context);

        // 跨服广播
        if (stateBroadcaster != null) {
            stateBroadcaster.broadcast("DYNAMIC_SHOP_UPDATE",
                config.strategyKey(), config.shopId(), context.serializeForSync());
        }

        // 发布 EventBus 事件
        if (eventPublisher != null) {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("strategy_key", config.strategyKey());
            payload.put("strategy_type", config.type());
            payload.put("shop_id", config.shopId());
            eventPublisher.publish("axs.market.shop_dynamic_triggered", payload);
        }
    }

    // ── 持久化 ────────────────────────────────────────────

    private void persistState(String strategyKey, DynamicShopContextImpl context) {
        DynamicShopState state = new DynamicShopState(
            strategyKey,
            context.shopId(),
            context.getStrategyType(),
            context.getState() != null ? context.getState() : "",
            context.getLastTriggerTime(),
            0
        );
        repository.upsertDynamicShopState(state);
    }

    // ── 跨服消息处理 ──────────────────────────────────────

    /**
     * 处理来自其他服的动态状态同步消息。
     * <p>
     * 由 MarketService 调用。
     */
    public void handleCrossServerMessage(String action, String strategyKey, String shopId, String data) {
        if (!"DYNAMIC_SHOP_UPDATE".equals(action)) return;
        DynamicShopContextImpl context = contexts.get(strategyKey);
        if (context == null) {
            logger.fine("[DynamicShop] 收到未知策略的跨服同步: " + strategyKey);
            return;
        }
        context.deserializeFromSync(data);
        logger.fine("[DynamicShop] 已同步跨服动态状态: " + strategyKey);
    }

    // ── 外部查询接口 ──────────────────────────────────────

    /**
     * 获取指定商店的动态商品层。
     * <p>
     * 由 ShopService 在构建有效商品列表时调用。
     */
    public Map<String, ShopItem> getDynamicItems(String shopId) {
        Map<String, ShopItem> result = new LinkedHashMap<>();
        for (DynamicShopContextImpl context : contexts.values()) {
            if (context.shopId().equals(shopId)) {
                result.putAll(context.getDynamicItems());
            }
        }
        return result;
    }

    /**
     * 获取指定商店的价格乘数映射。
     * <p>
     * 由 ShopService 在计算有效价格时调用。
     */
    public Map<String, Double> getPriceMultipliers(String shopId) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (DynamicShopContextImpl context : contexts.values()) {
            if (context.shopId().equals(shopId)) {
                result.putAll(context.getAllPriceMultipliers());
            }
        }
        return result;
    }

    /**
     * 记录购买成交（供需模式策略据此调价）。
     */
    public void recordPurchase(String shopId, String itemId, int amount) {
        for (DynamicShopContextImpl context : contexts.values()) {
            if (context.shopId().equals(shopId)) {
                context.recordPurchase(itemId, amount);
            }
        }
    }

    // ── 策略工厂 ──────────────────────────────────────────

    private @Nullable DynamicShopStrategy createStrategy(DynamicStrategyConfig config) {
        return switch (config.type().toLowerCase(java.util.Locale.ROOT)) {
            case "random-refresh" -> {
                RandomRefreshStrategy s = new RandomRefreshStrategy();
                s.setConfig(config);
                yield s;
            }
            case "price-float" -> {
                PriceFloatStrategy s = new PriceFloatStrategy();
                s.setConfig(config);
                yield s;
            }
            case "scheduled-swap" -> {
                ScheduledSwapStrategy s = new ScheduledSwapStrategy();
                s.setConfig(config);
                yield s;
            }
            default -> null;
        };
    }
}
