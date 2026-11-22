package xuanmo.arcartxsuite.lottery;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.lottery.config.CaseItemDefinition;
import xuanmo.arcartxsuite.lottery.config.CostConfig;
import xuanmo.arcartxsuite.lottery.config.ExchangeShopItem;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.config.LotteryModuleConfiguration;
import xuanmo.arcartxsuite.lottery.config.PointDefinition;
import xuanmo.arcartxsuite.lottery.config.PoolDefinition;
import xuanmo.arcartxsuite.lottery.config.PoolType;
import xuanmo.arcartxsuite.lottery.engine.CaseOpeningEngine;
import xuanmo.arcartxsuite.lottery.engine.GachaEngine;
import xuanmo.arcartxsuite.lottery.engine.PrizeDistributor;
import xuanmo.arcartxsuite.lottery.model.CaseResult;
import xuanmo.arcartxsuite.lottery.model.GachaResult;
import xuanmo.arcartxsuite.lottery.model.PlayerCaseState;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.model.PoolItem;
import xuanmo.arcartxsuite.lottery.storage.LotteryRepository;

/**
 * Lottery 模块核心服务。
 * <p>
 * 统筹 GACHA 抽卡与 CASE 开箱的完整流程：费用扣除、引擎抽奖、
 * 状态持久化、日志记录、奖品发放与待领取入队。
 * 每个玩家同时只接受一项业务操作，跨节点由持久预留串行。
 */
public class LotteryService {

    private static final NamespacedKey COST_RECEIPT = new NamespacedKey("arcartxsuite", "lottery_item_cost");

    private final JavaPlugin plugin;
    private final LotteryModuleConfiguration configuration;
    private final LotteryRepository repository;
    private final CurrencyBridgeAPI currencyManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemMatcherAPI itemMatcherSupport;
    private final Supplier<MailDispatchable> mailSupplier;
    private final Logger logger;
    private final GachaEngine gachaEngine;
    private final CaseOpeningEngine caseEngine;
    private final PrizeDistributor distributor;
    private final LotteryPlaceholderReads placeholderReads;
    private final LotteryWork work;
    private final LotteryClaimTransfer claimTransfer;
    private final java.util.Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    private volatile MessageProvider messageProvider;

    /**
     * 构造 Lottery 服务。
     *
     * @param plugin            宿主插件实例
     * @param configuration     模块配置
     * @param repository        数据存储仓库
     * @param currencyManager   货币桥接 API
     * @param itemSourceRegistry 物品来源注册表
     * @param mailSupplier      邮件能力延迟供应器
     * @param logger            日志记录器
     */
    public LotteryService(JavaPlugin plugin,
                          LotteryModuleConfiguration configuration,
                          LotteryRepository repository,
                          CurrencyBridgeAPI currencyManager,
                          ItemSourceRegistry itemSourceRegistry,
                           ItemMatcherAPI itemMatcherSupport,
                           Supplier<MailDispatchable> mailSupplier,
                          Logger logger,
                          @Nullable LotteryClaimTransfer claimTransfer) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.currencyManager = currencyManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemMatcherSupport = itemMatcherSupport;
        this.mailSupplier = mailSupplier;
        this.logger = logger;
        this.claimTransfer = claimTransfer;
        this.gachaEngine = new GachaEngine(configuration.pools().values().stream()
            .map(PoolDefinition::gacha).filter(java.util.Objects::nonNull).toList());
        this.caseEngine = new CaseOpeningEngine(configuration.pools().values().stream()
            .map(PoolDefinition::caseConfig).filter(java.util.Objects::nonNull).toList());
        this.distributor = new PrizeDistributor(plugin, itemSourceRegistry, mailSupplier, logger);
        this.placeholderReads = new LotteryPlaceholderReads(repository, logger);
        this.work = new LotteryWork(plugin);
    }

    /**
     * 设置消息提供者，用于向玩家发送本地化消息。
     *
     * @param provider 消息提供者
     */
    public void setMessageProvider(MessageProvider provider) {
        this.messageProvider = provider;
    }

    /**
     * 返回模块日志记录器。
     *
     * @return 模块 logger
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * 启动服务。
     */
    public void start() {
        logger.info("服务已启动");
    }

    /**
     * 关闭服务，释放资源。
     */
    public void shutdown() {
        work.close();
        placeholderReads.close();
        logger.info("服务已关闭");
    }

    CompletionStage<Void> stopped() {
        return CompletableFuture.allOf(work.stopped().toCompletableFuture(), placeholderReads.stopped().toCompletableFuture());
    }

    public void reply(Runnable action) { work.reply(action); }

    private final class Actor {
        final Player player;
        final UUID id;
        final AuthSnapshot connection;

        Actor(Player player) {
            this.player = player;
            this.id = player.getUniqueId();
            this.connection = AuthAccess.snapshot(id);
        }

        boolean current() {
            AuthSnapshot now = AuthAccess.snapshot(id);
            return player.isOnline() && Bukkit.getPlayer(id) == player && connection.admitted() && now.admitted()
                && now.generation() == connection.generation()
                && java.util.Objects.equals(now.connectionId(), connection.connectionId());
        }

        <T> T effect(Supplier<T> action) {
            return work.main(() -> {
                if (!current()) throw new LotteryWork.BeforeEffect("Lottery player session changed");
                return action.get();
            });
        }

        void message(String key, Object... args) {
            if (messageProvider == null) return;
            work.reply(() -> { if (current()) sendMessage(player, key, args); });
        }
    }

    @FunctionalInterface
    private interface PlayerOperation<T> { T execute(Actor actor) throws Exception; }

    private <T> CompletionStage<T> submitPlayer(Player player, T fallback, PlayerOperation<T> operation) {
        if (!Bukkit.isPrimaryThread()) return CompletableFuture.failedFuture(
            new IllegalStateException("Submit Lottery player operations on the server thread"));
        var actor = new Actor(player);
        if (!actor.current()) return CompletableFuture.completedFuture(fallback);
        if (!activePlayers.add(actor.id)) {
            sendMessage(player, "common.lock-busy");
            return CompletableFuture.completedFuture(fallback);
        }
        return work.submit(() -> operation.execute(actor), false)
            .whenComplete((value, failure) -> activePlayers.remove(actor.id)).copy();
    }

    Long placeholderPoint(UUID player, String point) { return placeholderReads.point(player, point); }

    PlayerGachaState placeholderPity(UUID player, String poolId) {
        var pool = getPool(poolId);
        return pool == null || pool.type() != PoolType.GACHA ? null
            : placeholderReads.pity(player, resolveSharedPoolId(poolId));
    }

    PlayerCaseState placeholderOpened(UUID player, String poolId) {
        var pool = getPool(poolId);
        return pool == null || pool.type() != PoolType.CASE ? null : placeholderReads.opened(player, poolId);
    }

    // ─── 查询 ─────────────────────────────────────────────

    /**
     * 返回全部已加载的奖池定义。
     *
     * @return 奖池 ID 到奖池定义的映射
     */
    public Map<String, PoolDefinition> getPools() {
        return configuration.pools();
    }

    /**
     * 根据奖池 ID 获取奖池定义。
     *
     * @param poolId 奖池 ID
     * @return 奖池定义，不存在时返回 {@code null}
     */
    @Nullable
    public PoolDefinition getPool(@NotNull String poolId) {
        return configuration.pools().get(poolId);
    }

    /**
     * 根据奖品定义生成对应的 {@link ItemStack}，用于 UI 展示。
     *
     * @param item   奖品定义
     * @param player 玩家上下文（Overture 等需要），可为 {@code null}
     * @return 生成的物品堆，生成失败时返回 {@code null}
     */
    @Nullable
    public ItemStack generateItem(@NotNull PoolItem item, @Nullable Player player) {
        return distributor.generateItem(item, player);
    }

    /**
     * 获取玩家在指定 GACHA 奖池的抽卡状态。
     *
     * @param player 玩家
     * @param poolId 奖池 ID
     * @return 抽卡状态，若共享保底组则以组 ID 查询
     */
    public PlayerGachaState getGachaState(@NotNull Player player, @NotNull String poolId) {
        String effectivePoolId = resolveSharedPoolId(poolId);
        try {
            return repository.loadGachaState(player.getUniqueId(), effectivePoolId);
        } catch (SQLException failure) {
            throw new IllegalStateException("无法读取抽卡保底", failure);
        }
    }

    /**
     * 获取玩家在指定 CASE 奖池的开箱状态。
     *
     * @param player 玩家
     * @param poolId 奖池 ID
     * @return 开箱状态
     */
    public PlayerCaseState getCaseState(@NotNull Player player, @NotNull String poolId) {
        return repository.loadCaseState(player.getUniqueId(), poolId);
    }

    /**
     * 根据玩家手持物品查找匹配的 CASE 奖池。
     * <p>
     * 遍历所有已启用的 CASE 奖池，用 {@link CaseItemDefinition#matcher()} 匹配手持物品。
     * 匹配优先级：配置了 case-item 的奖池优先，未配置的不参与匹配。
     *
     * @param player 玩家
     * @param item   玩家手持物品
     * @return 第一个匹配的奖池定义，无匹配时返回 {@code null}
     */
    @Nullable
    public PoolDefinition findCasePoolByItem(@NotNull Player player, @Nullable org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        for (PoolDefinition pool : configuration.pools().values()) {
            if (!pool.enabled() || pool.type() != PoolType.CASE) continue;
            CaseItemDefinition caseItem = pool.caseItem();
            if (caseItem == null) continue;
            if (itemMatcherSupport.matches(caseItem.matcher(), item)) {
                return pool;
            }
        }
        return null;
    }

    // ─── Gacha 抽奖 ────────────────────────────────────────

    /**
     * 执行 GACHA 抽卡。
     * <p>
     * 通过玩家锁保证并发安全，扣除费用后调用引擎抽奖并发放奖品。
     *
     * @param player 玩家
     * @param poolId 奖池 ID
     * @param count  抽卡次数（1 或 10）
     * @return 抽卡结果，锁繁忙或费用不足时返回空结果
     * @throws IllegalArgumentException 当奖池不存在或类型错误时抛出
     */
    @NotNull
    public CompletionStage<GachaResult> pullGacha(@NotNull Player player, @NotNull String poolId, int count) {
        if (!Bukkit.isPrimaryThread()) return CompletableFuture.failedFuture(new IllegalStateException("Lottery draw requires the server thread"));
        var empty = new GachaResult(List.of(), PlayerGachaState.empty(player.getUniqueId(), poolId), false, false);
        return submitPlayer(player, empty, actor -> pullGachaLocked(actor, poolId, count));
    }

    private GachaResult pullGachaLocked(Actor actor, String poolId, int count) {
        if (count != 1 && count != 10) {
            throw new IllegalArgumentException("Lottery pull count must be 1 or 10");
        }
        PoolDefinition poolDef = getPool(poolId);
        if (poolDef == null || !poolDef.enabled() || poolDef.type() != PoolType.GACHA || poolDef.gacha() == null) {
            throw new IllegalArgumentException("奖池不存在或类型错误: " + poolId);
        }

        UUID playerId = actor.id;
        UUID operation = UUID.randomUUID();
        String effectivePoolId = resolveSharedPoolId(poolId);
        GachaResult empty = new GachaResult(List.of(), PlayerGachaState.empty(playerId, effectivePoolId), false, false);
        try {
            var entries = count == 10 ? poolDef.cost().ten() : poolDef.cost().single();
            if (count == 10 && entries.isEmpty()) {
                actor.message("common.ten-unavailable");
                return empty;
            }
            String costs = entries.stream()
                .map(LotteryService::describeCost)
                .collect(java.util.stream.Collectors.joining(", "));
            if (!repository.beginGachaOperation(playerId, operation, poolId,
                "抽数=" + count + " 保底组=" + effectivePoolId + " 费用=" + costs)) {
                var pending = repository.findGachaOperation(playerId);
                if (pending != null) actor.message("common.gacha-review", pending.id());
                else actor.message("common.lock-busy");
                return empty;
            }

            GachaResult result;
            Map<String, Long> pointCosts = new LinkedHashMap<>();
            List<CostConfig.CostEntry> externalCosts = new ArrayList<>();
            for (var entry : entries) {
                if (entry.type() == CostConfig.CostMode.POINT) pointCosts.merge(entry.id(), (long) entry.amount(), Long::sum);
                else externalCosts.add(entry);
            }
            try {
                var state = repository.loadGachaState(playerId, effectivePoolId);
                for (var entry : pointCosts.entrySet()) {
                    if (repository.getPoint(playerId, entry.getKey()) < entry.getValue()) {
                        repository.finishGachaOperation(playerId, operation, "DEBITING");
                        actor.message("common.insufficient-point", entry.getKey());
                        return empty;
                    }
                }
                result = gachaEngine.pull(poolDef.gacha(), state, count);
                if (result.items().size() != count || result.items().stream().anyMatch(java.util.Objects::isNull)) {
                    throw new IllegalStateException("奖池没有生成完整的抽卡结果");
                }
            } catch (SQLException | RuntimeException failure) {
                if (!repository.finishGachaOperation(playerId, operation, "DEBITING")) throw failure;
                logger.log(java.util.logging.Level.WARNING, "抽卡准备失败: " + operation, failure);
                actor.message("common.error");
                return empty;
            }
            CostTransaction cost = debitCosts(actor, externalCosts, operation);
            if (!cost.success()) {
                if (cost.refundComplete()) repository.finishGachaOperation(playerId, operation, "DEBITING");
                else {
                    repository.changeGachaOperation(playerId, operation, "DEBITING", "DEBIT_REVIEW");
                    actor.message("common.gacha-review", operation);
                }
                return empty;
            }

            if (!commitOrRefund(actor, operation, cost,
                () -> repository.commitGachaPull(operation, result.finalState(), poolId, count,
                    serializeItems(result.items()), result.guaranteed(), result.items(), pointCosts,
                    pointAwards(poolDef, result.items())))) return empty;
            placeholderReads.invalidatePity(playerId, effectivePoolId);
            placeholderReads.invalidatePoints(playerId);
            if (!deliverOperationPrizes(actor, poolId, operation)) return empty;
            return result;
        } catch (SQLException | RuntimeException failure) {
            logger.log(java.util.logging.Level.WARNING, "抽卡操作异常: player=" + playerId + " operation=" + operation, failure);
            actor.message("common.gacha-review", operation);
            return empty;
        }
    }

    @FunctionalInterface
    private interface CommitOperation {
        void commit() throws SQLException;
    }

    private boolean commitOrRefund(Actor actor, UUID operation, CostTransaction cost, CommitOperation writer) throws SQLException {
        UUID playerId = actor.id;
        try {
            writer.commit();
            return true;
        } catch (SQLException | RuntimeException failure) {
            // 取得退款权才代表奖池结果和积分未提交。
            if (repository.changeGachaOperation(playerId, operation, "DEBITING", "REFUNDING")) {
                if (!rollbackCost(actor, cost.deducted(), cost.snapshots(), operation, "REFUNDING")) {
                    repository.changeGachaOperation(playerId, operation, "REFUNDING", "REFUND_REVIEW");
                    throw failure;
                }
                if (!repository.finishGachaOperation(playerId, operation, "REFUNDING")) throw failure;
                logger.log(java.util.logging.Level.WARNING, "奖池保存失败，已退款: " + operation, failure);
                actor.message("common.error");
                return false;
            }
            var receipt = repository.findGachaOperation(playerId);
            if (receipt == null || !receipt.id().equals(operation) || !"COMMITTED".equals(receipt.status())) {
                throw failure;
            }
            return true;
        }
    }

    private boolean deliverOperationPrizes(Actor actor, String poolId, UUID operation) throws SQLException {
        UUID playerId = actor.id;
        var plan = repository.loadCommittedPrizes(playerId, operation);
        if (plan == null || plan.items().isEmpty() || plan.nextIndex() < 0 || plan.nextIndex() > plan.items().size()) {
            actor.message("common.gacha-review", operation);
            return false;
        }
        for (int index = plan.nextIndex(); index < plan.items().size(); index++) {
            PoolItem item = plan.items().get(index);
            String prize = (index + 1) + " " + describePrize(item);
            if (!repository.beginPrize(playerId, operation, index, "开始发放 " + prize)) return false;
            boolean confirmed;
            try {
                confirmed = item.delivery() == PoolItem.DeliveryType.DIRECT
                    ? handoffDrawItem(actor, poolId, operation, index, item)
                    : distributeOrEnqueue(actor, poolId, item);
            } catch (LotteryWork.BeforeEffect cancelled) {
                if (repository.changeGachaOperation(playerId, operation, "DELIVERING", "COMMITTED")) {
                    gachaEvidence(playerId, operation, "COMMITTED", "发放前取消，待继续 " + prize);
                    actor.message("common.gacha-prize-pending");
                }
                return false;
            }
            if (!confirmed) {
                gachaEvidence(playerId, operation, "DELIVERING", "发放待核对 " + prize);
                repository.changeGachaOperation(playerId, operation, "DELIVERING", "PRIZE_REVIEW");
                actor.message("common.gacha-review", operation);
                return false;
            }
            if (!repository.completePrize(playerId, operation, index, "确认发放或入队 " + prize)) {
                actor.message("common.gacha-review", operation);
                return false;
            }
        }
        if (repository.finishGachaOperation(playerId, operation, "COMMITTED")) return true;
        actor.message("common.gacha-review", operation);
        return false;
    }

    private static String describePrize(PoolItem item) {
        return item.key() + " " + item.delivery() + " " + item.source() + ":" + item.id() + " ×" + item.amount()
            + (item.nbt() == null ? "" : " NBT=" + item.nbt())
            + " 邮件=" + item.mailPresets() + " 命令=" + item.commands() + " 附加命令=" + item.extraCommands();
    }

    private static String describeCost(CostConfig.CostEntry entry) {
        return entry.type() + ":" + entry.id() + ":" + entry.amount()
            + (entry.matcher() == null ? "" : ":" + entry.matcher());
    }

    private void gachaEvidence(UUID playerId, UUID operation, String phase, String details) {
        if (operation == null) return;
        try {
            if (!repository.appendGachaEvidence(playerId, operation, phase, details)) {
                throw new IllegalStateException("抽卡凭证状态已变更: " + operation);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("无法保存抽卡操作凭证: " + operation, failure);
        }
    }

    // ─── Case 开箱 ────────────────────────────────────────

    /** Open at most ten cases per operation. */
    @NotNull
    public CompletionStage<List<CaseResult>> openCaseBatch(@NotNull Player player, @NotNull String poolId, int count) {
        if (count < 1 || count > 10) return CompletableFuture.completedFuture(List.of());
        return submitPlayer(player, List.of(), actor -> openCaseBatchLocked(actor, poolId, count));
    }

    private List<CaseResult> openCaseBatchLocked(Actor actor, String poolId, int count) {
        PoolDefinition poolDef = getPool(poolId);
        if (poolDef == null || !poolDef.enabled() || poolDef.type() != PoolType.CASE || poolDef.caseConfig() == null) {
            throw new IllegalArgumentException("奖池不存在或类型错误: " + poolId);
        }

        if (count <= 0) return List.of();
        List<CostConfig.CostEntry> entries = costEntries(actor, buildEffectiveCaseCost(poolDef), count);
        if (entries == null) return List.of();
        List<CaseResult> results = caseEngine.openCaseBatch(poolDef.caseConfig(), count);
        if (results.size() != count || results.stream().anyMatch(result -> result == null || result.item() == null)) {
            actor.message("common.error");
            return List.of();
        }
        List<PoolItem> items = results.stream().map(CaseResult::item).toList();
        Map<String, Long> pointCosts = new LinkedHashMap<>();
        List<CostConfig.CostEntry> externalCosts = new ArrayList<>();
        for (var entry : entries) {
            if (entry.type() == CostConfig.CostMode.POINT) pointCosts.merge(entry.id(), (long) entry.amount(), Long::sum);
            else externalCosts.add(entry);
        }
        UUID playerId = actor.id;
        for (var entry : pointCosts.entrySet()) {
            if (repository.getPoint(playerId, entry.getKey()) < entry.getValue()) {
                actor.message("common.insufficient-point", entry.getKey());
                return List.of();
            }
        }

        UUID operation = UUID.randomUUID();
        try {
            String details = "开箱数量=" + count + " 费用=" + entries.stream().map(LotteryService::describeCost).toList()
                + " 预计奖品=" + items.stream().map(LotteryService::describePrize).toList();
            if (!repository.beginGachaOperation(playerId, operation, poolId, details)) {
                var pending = repository.findGachaOperation(playerId);
                if (pending != null) actor.message("common.gacha-review", pending.id());
                else actor.message("common.lock-busy");
                return List.of();
            }
            CostTransaction cost = debitCosts(actor, externalCosts, operation);
            if (!cost.success()) {
                if (cost.refundComplete() && repository.finishGachaOperation(playerId, operation, "DEBITING")) {
                    return List.of();
                }
                repository.changeGachaOperation(playerId, operation, "DEBITING", "DEBIT_REVIEW");
                actor.message("common.gacha-review", operation);
                return List.of();
            }
            Map<String, Long> rewards = new LinkedHashMap<>();
            for (PoolItem item : items) {
                pointAwards(poolDef, List.of(item)).forEach((point, amount) -> rewards.merge(point, amount, Long::sum));
            }
            if (!commitOrRefund(actor, operation, cost,
                () -> repository.commitCaseOpen(playerId, operation, poolId, results, pointCosts, rewards))) return List.of();
            placeholderReads.invalidateOpened(playerId, poolId);
            placeholderReads.invalidatePoints(playerId);
            return deliverOperationPrizes(actor, poolId, operation) ? results : List.of();
        } catch (SQLException | RuntimeException failure) {
            logger.log(java.util.logging.Level.WARNING, "开箱操作异常: player=" + playerId + " operation=" + operation, failure);
            actor.message("common.gacha-review", operation);
            return List.of();
        }
    }

    private record PrizeEffect(PrizeDistributor.DistributionResult result, String itemData) {}

    private boolean handoffDrawItem(Actor actor, String poolId, UUID operation, int index, PoolItem item) {
        String metadata = item.key() + "|" + item.name() + "|DIRECT";
        String data = actor.effect(() -> {
            ItemStack stack = distributor.generateItem(item, actor.player);
            return xuanmo.arcartxsuite.api.inventory.InventoryItems.empty(stack) ? "" : distributor.toItemJson(stack);
        });
        if (data.isBlank()) {
            if (repository.enqueuePendingClaim(actor.id, poolId, metadata, null) > 0) {
                actor.message("common.pending-claim-manual", item.name());
            }
            actor.message("common.distribution-failed", item.name());
            return false;
        }
        if (claimTransfer == null || !claimTransfer.available()) {
            if (repository.enqueuePendingClaim(actor.id, poolId, metadata, data) <= 0) return false;
            actor.message("common.pending-claim-enqueued", item.name());
        } else {
            if (!claimTransfer.acceptDrawPrize(actor.id, operation, index, data)) return false;
            actor.message("common.draw-item-queued", item.name());
            work.reply(() -> {
                if (actor.current()) claimTransfer.flush(actor.player);
            });
        }
        try {
            return item.extraCommands().isEmpty() || actor.effect(() -> distributor.executeExtraCommands(actor.player, item));
        } catch (LotteryWork.BeforeEffect cancelled) {
            // The item is already handed off; this prize cannot be retried as entirely unstarted.
            return false;
        }
    }

    private boolean distributeOrEnqueue(Actor actor, String poolId, PoolItem item) {
        var effect = actor.effect(() -> {
            var outcome = distributor.distribute(actor.player, item);
            return new PrizeEffect(outcome, outcome.success() ? "" : distributor.toItemJson(outcome.item()));
        });
        var outcome = effect.result();
        if (outcome.success()) {
            if (outcome.mailSent()) {
                actor.message("common.mail-sent", item.name());
            }
            return true;
        }

        String itemData = effect.itemData();
        String metadata = item.key() + "|" + item.name() + "|" + item.delivery().name();
        if (!itemData.isBlank()) {
            long claimId = repository.enqueuePendingClaim(actor.id, poolId, metadata, itemData);
            if (claimId > 0) {
                actor.message("common.pending-claim-enqueued", item.name());
            }
            return claimId > 0;
        } else if (item.delivery() != PoolItem.DeliveryType.COMMAND) {
            long claimId = repository.enqueuePendingClaim(actor.id, poolId, metadata, null);
            if (claimId > 0) {
                actor.message("common.pending-claim-manual", item.name());
            }
            actor.message("common.distribution-failed", item.name());
            logger.severe("奖励投递失败且无法序列化，需管理员处理: player="
                + actor.id + ", pool=" + poolId + ", item=" + item.key()
                + ", reason=" + outcome.failureReason());
        } else {
            actor.message("common.distribution-failed", item.name());
            logger.severe("非物品奖励投递失败，需管理员处理: player="
                + actor.id + ", pool=" + poolId + ", item=" + item.key()
                + ", reason=" + outcome.failureReason());
        }
        return false;
    }

    /** Acknowledge host ownership on the SQL worker, then ask the host to try delivery. */
    public CompletionStage<Integer> claimPending(@NotNull Player player) {
        return submitPlayer(player, 0, actor -> {
            try {
                var operation = repository.findGachaOperation(actor.id);
                if (operation != null && "COMMITTED".equals(operation.status())
                        && deliverOperationPrizes(actor, operation.poolId(), operation.id())) {
                    actor.message("common.gacha-prize-resumed");
                }
                if (claimTransfer == null) return 0;
                var result = claimTransfer.transfer(actor.id);
                if (result.transferred() > 0) actor.message("common.pending-claim-transferred", result.transferred());
                if (result.remaining() > 0) actor.message("common.pending-claim-remaining", result.remaining());
                if (result.hostAccepted()) work.reply(() -> {
                    if (actor.current()) claimTransfer.flush(actor.player);
                });
                return result.transferred();
            } catch (SQLException | java.io.IOException | RuntimeException failure) {
                logger.log(java.util.logging.Level.WARNING, "抽奖待发物品交接暂未完成: " + actor.id, failure);
                actor.message("common.pending-claim-retry");
                return 0;
            }
        });
    }

    // ─── 消耗扣除 ─────────────────────────────────────────

    /**
     * 构建 CASE 开箱的实际消耗配置。
     * <p>
     * 若奖池配置了 {@code case-item}，则消耗 = 箱子 + (requiresKey ? 钥匙 : 空)；
     * 若未配置 {@code case-item}（向后兼容），退回原始 {@link PoolDefinition#cost()}。
     *
     * @param poolDef 奖池定义
     * @return 合并后的消耗配置
     */
    private CostConfig buildEffectiveCaseCost(PoolDefinition poolDef) {
        if (poolDef.caseItem() == null) {
            return poolDef.cost();
        }
        CaseItemDefinition caseItem = poolDef.caseItem();
        List<CostConfig.CostEntry> singleEntries = new ArrayList<>();
        List<CostConfig.CostEntry> tenEntries = new ArrayList<>();
        // 箱子消耗条目
        singleEntries.add(new CostConfig.CostEntry(
            CostConfig.CostMode.ITEM, "", caseItem.amount(),
            caseItem.displayName(), caseItem.icon(), caseItem.matcher()
        ));
        tenEntries.add(new CostConfig.CostEntry(
            CostConfig.CostMode.ITEM, "", caseItem.amount() * 10,
            caseItem.displayName(), caseItem.icon(), caseItem.matcher()
        ));
        // 钥匙消耗条目（仅有锁时）
        if (poolDef.requiresKey()) {
            singleEntries.addAll(poolDef.cost().single());
            tenEntries.addAll(poolDef.cost().ten());
        }
        return new CostConfig(singleEntries, tenEntries);
    }

    /**
     * 一次消耗扣除的结果，成功时携带回滚所需的条目与背包快照。
     *
     * @param success   是否扣除成功
     * @param deducted  已成功扣除的条目
     * @param snapshots 扣除前的物品快照，按物品 ID 分组
     */
    private record CostTransaction(
        boolean success,
        boolean refundComplete,
        List<CostConfig.CostEntry> deducted,
        Map<String, List<ItemStack>> snapshots
    ) {
        static CostTransaction failed() {
            return new CostTransaction(false, true, List.of(), Map.of());
        }
    }

    private List<CostConfig.CostEntry> costEntries(Actor actor, CostConfig cost, int multiplier) {
        List<CostConfig.CostEntry> source = multiplier == 10 ? cost.ten() : cost.single();
        if (multiplier == 10 && source.isEmpty()) {
            actor.message("common.ten-unavailable");
            return null;
        }
        List<CostConfig.CostEntry> entries = new ArrayList<>();
        for (CostConfig.CostEntry entry : source) {
            int amount = multiplier == 10 ? entry.amount() : Math.multiplyExact(entry.amount(), multiplier);
            if (amount <= 0) throw new IllegalArgumentException("Invalid Lottery cost");
            entries.add(new CostConfig.CostEntry(entry.type(), entry.id(), amount,
                entry.displayName(), entry.icon(), entry.matcher()));
        }
        return entries;
    }

    private record ItemPlan(List<ItemDebit> slots, String evidence) {}
    private enum Debit { APPLIED, REJECTED, UNCERTAIN }

    private CostTransaction debitCosts(Actor actor, List<CostConfig.CostEntry> entries, UUID operation) {
        try {
            var insufficient = actor.effect(() -> findInsufficientExternalCost(actor.player, entries));
            if (insufficient != null) {
                boolean item = insufficient.type() == CostConfig.CostMode.ITEM;
                actor.message(item ? "common.insufficient-item" : "common.insufficient-currency",
                    item && insufficient.displayName() != null ? insufficient.displayName() : insufficient.id());
                return CostTransaction.failed();
            }
        } catch (LotteryWork.BeforeEffect disconnected) { return CostTransaction.failed(); }

        List<CostConfig.CostEntry> changed = new ArrayList<>();
        Map<String, List<ItemStack>> snapshots = new LinkedHashMap<>();
        for (var entry : entries) {
            String description = describeCost(entry);
            boolean mayHaveApplied = false;
            boolean item = entry.type() == CostConfig.CostMode.ITEM;
            ItemPlan plan = null;
            Debit debit = Debit.REJECTED;
            try {
                plan = item ? actor.effect(() -> captureItemPlan(actor.player.getInventory(), entry)) : null;
                if (!item || !plan.slots().isEmpty()) {
                    ItemPlan snapshot = plan;
                    if (snapshot != null) description += " 物品快照=" + snapshot.evidence();
                    gachaEvidence(actor.id, operation, "DEBITING", "开始扣费 " + description);
                    mayHaveApplied = true;
                    debit = item
                        ? actor.effect(() -> applyItemDebits(actor.player.getInventory(), snapshot.slots()) ? Debit.APPLIED : Debit.REJECTED)
                        : actor.effect(() -> withdrawCurrency(actor.player, entry.id(), entry.amount()));
                }
            } catch (LotteryWork.BeforeEffect beforeEffect) {
                // The scheduled action never started.
            } catch (RuntimeException | LinkageError failure) {
                logger.log(java.util.logging.Level.WARNING, "抽奖扣费未确认: " + operation, failure);
                if (mayHaveApplied) debit = Debit.UNCERTAIN;
            }
            if (debit != Debit.APPLIED) return failedDebit(actor, operation, entry, changed, snapshots, debit == Debit.UNCERTAIN);
            changed.add(entry);
            if (plan != null) snapshots.computeIfAbsent(costKey(entry), ignored -> new ArrayList<>())
                .addAll(plan.slots().stream().map(ItemDebit::removed).toList());
            gachaEvidence(actor.id, operation, "DEBITING", "确认扣费 " + description);
        }
        if (!snapshots.isEmpty() && !confirmItemCostSave(actor, operation, "DEBITING", "deducted")) {
            return new CostTransaction(false, false, List.of(), Map.of());
        }
        return new CostTransaction(true, true, changed, snapshots);
    }

    private boolean confirmItemCostSave(Actor actor, UUID operation, String phase, String effect) {
        String token = operation + ":" + effect;
        try {
            gachaEvidence(actor.id, operation, phase, "等待背包存档回执 " + token);
            Path file = actor.effect(() -> {
                Path path = Bukkit.getWorlds().get(0).getWorldFolder().toPath()
                    .resolve("playerdata").resolve(actor.id + ".dat");
                PlayerDataReceipt.save(actor.player, COST_RECEIPT, token);
                return path;
            });
            if (token.equals(PlayerDataReceipt.read(file, COST_RECEIPT.toString()))) return true;
            logger.warning("抽奖未找到背包存档回执: " + token);
        } catch (IOException | RuntimeException | LinkageError failure) {
            logger.log(java.util.logging.Level.WARNING, "抽奖背包存档待核对: " + token, failure);
        }
        return false;
    }

    private CostConfig.CostEntry findInsufficientExternalCost(Player player, List<CostConfig.CostEntry> entries) {
        Map<String, Long> required = new LinkedHashMap<>();
        for (var entry : entries) {
            if (entry.type() == CostConfig.CostMode.POINT) throw new IllegalArgumentException("Points settle in SQL");
            required.merge(costKey(entry), (long) entry.amount(), Long::sum);
        }
        for (var entry : entries) {
            Long amount = required.remove(costKey(entry));
            if (amount == null) continue;
            boolean enough = entry.type() == CostConfig.CostMode.ITEM ? countInventory(player, entry.matcher()) >= amount
                : hasCurrency(player, entry.id(), amount);
            if (!enough) return entry;
        }
        return null;
    }

    private ItemPlan captureItemPlan(PlayerInventory inventory, CostConfig.CostEntry entry) {
        var slots = planItemDebits(inventory, entry.matcher(), entry.amount());
        String evidence = slots.stream().map(slot -> slot.slot() + "=" + encodeItem(slot.removed()))
            .collect(java.util.stream.Collectors.joining(","));
        return new ItemPlan(slots, evidence);
    }

    private static String encodeItem(ItemStack item) {
        return java.util.Base64.getEncoder().encodeToString(ItemSerializer.serialize(item));
    }

    private CostTransaction failedDebit(Actor actor, UUID operation, CostConfig.CostEntry entry,
                                         List<CostConfig.CostEntry> changed, Map<String, List<ItemStack>> snapshots,
                                         boolean uncertain) {
        boolean refunded = rollbackCost(actor, changed, snapshots, operation, "DEBITING");
        actor.message("common.resource-cost-failed", entry.id());
        return new CostTransaction(false, refunded && !uncertain, List.of(), Map.of());
    }

    private String costKey(CostConfig.CostEntry entry) {
        return entry.type().name() + ":" + entry.id() + ":" + (entry.matcher() == null ? "" : entry.matcher());
    }

    private long countInventory(Player player, ItemMatcher matcher) {
        long total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && itemMatcherSupport.matches(matcher, stack)) total += stack.getAmount();
        }
        return total;
    }

    private boolean hasCurrency(Player player, String id, long amount) {
        if (currencyManager == null) return false;
        var bridge = currencyManager.bridge(id);
        return bridge != null && bridge.available()
            && bridge.balance(player).compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    private Debit withdrawCurrency(Player player, String id, long amount) {
        var bridge = currencyManager == null ? null : currencyManager.bridge(id);
        if (bridge == null || !bridge.available()) return Debit.REJECTED;
        var result = bridge.withdraw(player, BigDecimal.valueOf(amount));
        if (result == null || result.uncertain()) return Debit.UNCERTAIN;
        if (!result.success()) return Debit.REJECTED;
        return bridge.debitRequiresReconciliation() ? Debit.UNCERTAIN : Debit.APPLIED;
    }

    private boolean rollbackCost(Actor actor, List<CostConfig.CostEntry> changed,
                                 Map<String, List<ItemStack>> snapshots, UUID operation, String phase) {
        boolean refunded = true;
        java.util.Set<String> restoredItems = new java.util.HashSet<>();
        List<RefundItem> overflow = new ArrayList<>();
        Map<String, Integer> itemAmounts = new java.util.HashMap<>();
        for (var entry : changed) if (entry.type() == CostConfig.CostMode.ITEM)
            itemAmounts.merge(costKey(entry), entry.amount(), Math::addExact);
        for (int i = changed.size() - 1; i >= 0; i--) {
            var entry = changed.get(i);
            try {
                String key = costKey(entry);
                if (entry.type() == CostConfig.CostMode.ITEM && !restoredItems.add(key)) continue;
                String description = entry.type() == CostConfig.CostMode.ITEM ? key + ":" + itemAmounts.get(key) : describeCost(entry);
                gachaEvidence(actor.id, operation, phase, "开始退款 " + description);
                boolean confirmed;
                if (entry.type() == CostConfig.CostMode.ITEM) {
                    var returned = actor.effect(() -> restoreItemSnapshots(actor.player, snapshots.getOrDefault(key, List.of()), itemAmounts.get(key)));
                    confirmed = returned.complete();
                    overflow.addAll(returned.leftovers());
                } else {
                    confirmed = actor.effect(() -> refundCurrency(actor.player, entry.id(), entry.amount()));
                }
                String status = !confirmed ? "退款待核对 " : entry.type() == CostConfig.CostMode.ITEM
                    ? "退料等待存档 " : "确认退款 ";
                gachaEvidence(actor.id, operation, phase, status + description);
                refunded &= confirmed;
            } catch (RuntimeException failure) {
                refunded = false;
                logger.log(java.util.logging.Level.WARNING, "退款异常: " + actor.id + " " + entry.id(), failure);
            }
        }
        if (!restoredItems.isEmpty()) {
            for (var leftover : overflow) {
                gachaEvidence(actor.id, operation, phase, "退料溢出待核对 " + leftover.metadata() + " " + leftover.data());
            }
            if (!confirmItemCostSave(actor, operation, phase, "returned")) return false;
            for (var leftover : overflow) {
                long claim = repository.enqueuePendingClaim(actor.id, LotteryRepository.refundClaimPool(operation),
                    leftover.metadata(), leftover.data());
                if (claim > 0) gachaEvidence(actor.id, operation, phase, "退料已开放待领 #" + claim);
                else refunded = false;
            }
        }
        return refunded;
    }

    private record RefundItem(String metadata, String data) {}
    private record ItemRefund(boolean complete, List<RefundItem> leftovers) {}

    private ItemRefund restoreItemSnapshots(Player player, List<ItemStack> snapshots, int amount) {
        int remaining = amount;
        List<RefundItem> pending = new ArrayList<>();
        for (ItemStack snapshot : snapshots) {
            if (remaining <= 0) break;
            ItemStack restored = snapshot.clone();
            restored.setAmount(Math.min(restored.getAmount(), remaining));
            remaining -= restored.getAmount();
            for (ItemStack leftover : player.getInventory().addItem(restored).values()) {
                if (leftover != null && leftover.getAmount() > 0)
                    pending.add(new RefundItem("rollback|" + leftover.getType().name(), encodeItem(leftover)));
            }
        }
        return new ItemRefund(remaining == 0, pending);
    }

    private static boolean confirmed(xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult result) {
        return result != null && result.success() && !result.uncertain();
    }

    private boolean refundCurrency(Player player, String id, long amount) {
        var bridge = currencyManager == null ? null : currencyManager.bridge(id);
        if (bridge == null || !bridge.available()
                || !confirmed(bridge.deposit(player, BigDecimal.valueOf(amount))) || bridge.creditRequiresReconciliation()) {
            logger.warning("Currency rollback failed for " + id);
            return false;
        }
        return true;
    }

    private Map<String, Long> pointAwards(PoolDefinition pool, List<PoolItem> items) {
        Map<String, Long> awards = new LinkedHashMap<>();
        for (PointDefinition point : pool.points().values()) {
            long amount = point.perPull();
            for (PoolItem item : items) {
                if (item != null) {
                    String rarity = item.rarity();
                    if (pool.gacha() != null) {
                        rarity = Integer.toString(describeGachaItem(pool, item).star());
                    }
                    amount += point.rarityRewards().getOrDefault(rarity, 0);
                }
            }
            if (amount > 0) awards.merge(point.id(), amount, Long::sum);
        }
        return awards;
    }

    public CompletionStage<Map<String, Long>> getPoints(@NotNull UUID playerUuid) {
        return work.submit(() -> repository.loadAllPointBalances(playerUuid), false).copy();
    }

    public CompletionStage<Long> getPoint(@NotNull UUID playerUuid, @NotNull String pointId) {
        return work.submit(() -> repository.loadPointBalances(playerUuid, java.util.Set.of(pointId))
            .getOrDefault(pointId, 0L), false).copy();
    }

    public LotteryRepository.GachaView loadGachaView(UUID playerId, String poolId, java.util.Set<String> pointIds)
        throws SQLException {
        return repository.loadGachaView(playerId, resolveSharedPoolId(poolId), pointIds);
    }

    public Map<String, Long> loadPointBalances(UUID playerId, java.util.Set<String> pointIds) throws SQLException {
        return repository.loadPointBalances(playerId, pointIds);
    }

    public LotteryRepository.ExchangeView loadExchangeView(UUID playerId, java.util.Set<String> pointIds,
                                                            java.util.Set<String> exchangeIds) throws SQLException {
        return repository.loadExchangeView(playerId, pointIds, exchangeIds);
    }

    public List<LotteryRepository.GachaLogEntry> loadGachaHistory(UUID playerId, String poolId, int limit)
        throws SQLException {
        return repository.loadGachaHistory(playerId, poolId, limit);
    }

    public List<LotteryRepository.CaseLogEntry> loadCaseHistory(UUID playerId, String poolId, int limit)
        throws SQLException {
        return repository.loadCaseHistory(playerId, poolId, limit);
    }

    public long getCurrencyBalance(@NotNull Player player, @NotNull String currencyId) {
        if (currencyManager == null) return -1L;
        var bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) return -1L;
        return bridge.balance(player).longValue();
    }

    public long getItemBalance(@NotNull Player player, @NotNull ItemMatcher matcher) {
        return countInventory(player, matcher);
    }

    public CompletionStage<Boolean> givePoints(@NotNull UUID playerUuid, @NotNull String pointId, long amount) {
        return work.submit(() -> {
            boolean saved = repository.addPoints(playerUuid, pointId, amount);
            if (saved) placeholderReads.invalidatePoints(playerUuid);
            return saved;
        }, true).copy();
    }

    public Map<String, ExchangeShopItem> getExchangeItems() {
        return configuration.exchangeItems();
    }

    public int getExchangeCount(@NotNull UUID playerUuid, @NotNull String itemId) {
        return repository.getExchangeCount(playerUuid, itemId);
    }

    public CompletionStage<Boolean> exchange(@NotNull Player player, @NotNull String itemId) {
        return submitPlayer(player, false, actor -> exchangeReserved(actor, itemId));
    }

    private boolean exchangeReserved(Actor actor, String itemId) {
        ExchangeShopItem item = configuration.exchangeItems().get(itemId);
        if (item == null) {
            actor.message("common.exchange-not-found");
            return false;
        }
        UUID operation = UUID.randomUUID();
        String pool = item.poolId() == null ? "exchange" : item.poolId();
        var pointCosts = new LinkedHashMap<String, Long>();
        var externalCosts = new ArrayList<CostConfig.CostEntry>();
        for (var entry : item.cost()) {
            if (entry.amount() <= 0) throw new IllegalArgumentException("Invalid Lottery exchange cost");
            if (entry.type() == CostConfig.CostMode.POINT) pointCosts.merge(entry.id(), (long) entry.amount(), Math::addExact);
            else externalCosts.add(entry);
        }
        try {
            String prize = describePrize(item.reward());
            if (!repository.beginGachaOperation(actor.id, operation, pool, "兑换 " + itemId + " 预计奖品=" + prize)) {
                var previous = repository.findGachaOperation(actor.id);
                if (previous == null) actor.message("common.lock-busy");
                else actor.message("common.gacha-review", previous.id());
                return false;
            }
            try {
                var stored = repository.loadExchangeView(actor.id, pointCosts.keySet(), java.util.Set.of(itemId));
                if (item.limit() > 0 && stored.counts().getOrDefault(itemId, 0) >= item.limit()) {
                    repository.finishGachaOperation(actor.id, operation, "DEBITING");
                    actor.message("common.exchange-limit");
                    return false;
                }
                for (var entry : pointCosts.entrySet()) {
                    if (stored.points().getOrDefault(entry.getKey(), 0L) < entry.getValue()) {
                        repository.finishGachaOperation(actor.id, operation, "DEBITING");
                        actor.message("common.insufficient-point", entry.getKey());
                        return false;
                    }
                }
            } catch (SQLException | RuntimeException rejected) {
                if (!repository.finishGachaOperation(actor.id, operation, "DEBITING")) throw rejected;
                actor.message("common.exchange-failed");
                logger.log(java.util.logging.Level.WARNING, "读取兑换状态失败: " + operation, rejected);
                return false;
            }
            var costs = debitCosts(actor, externalCosts, operation);
            if (!costs.success()) {
                if (!costs.refundComplete() || !repository.finishGachaOperation(actor.id, operation, "DEBITING")) {
                    repository.changeGachaOperation(actor.id, operation, "DEBITING", "DEBIT_REVIEW");
                    actor.message("common.gacha-review", operation);
                }
                return false;
            }
            if (!commitOrRefund(actor, operation, costs,
                () -> repository.commitExchange(actor.id, operation, itemId, item.limit(), prize, item.reward(), pointCosts))) return false;
            placeholderReads.invalidatePoints(actor.id);
            if (!deliverOperationPrizes(actor, pool, operation)) return false;
            actor.message("common.exchange-success", item.displayName());
            return true;
        } catch (SQLException | RuntimeException failure) {
            logger.log(java.util.logging.Level.WARNING, "兑换结果待核对: " + operation, failure);
            actor.message("common.gacha-review", operation);
            return false;
        }
    }

    public GachaItemInfo describeGachaItem(@NotNull PoolDefinition pool, @NotNull PoolItem item) {
        GachaPoolConfig gacha = pool.gacha();
        if (gacha == null) return new GachaItemInfo(0, false);
        if (contains(gacha.up5starItems(), item)) return new GachaItemInfo(5, true);
        if (contains(gacha.standard5starItems(), item)) return new GachaItemInfo(5, false);
        if (contains(gacha.up4starItems(), item)) return new GachaItemInfo(4, true);
        if (contains(gacha.standard4starItems(), item)) return new GachaItemInfo(4, false);
        return new GachaItemInfo(3, false);
    }

    private boolean contains(List<PoolItem> items, PoolItem target) {
        return items != null && items.stream().anyMatch(item -> item.key().equals(target.key()));
    }

    public record GachaItemInfo(int star, boolean up) {}

    private record ItemDebit(int slot, ItemStack original, ItemStack removed, ItemStack remaining) {}

    private List<ItemDebit> planItemDebits(PlayerInventory inventory, ItemMatcher matcher, int amount) {
        List<ItemDebit> debits = new ArrayList<>();
        int stillNeeded = amount;
        for (int i = 0; i < inventory.getSize() && stillNeeded > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || !itemMatcherSupport.matches(matcher, stack)) continue;
            int removedCount = Math.min(stack.getAmount(), stillNeeded);
            ItemStack removed = stack.clone();
            removed.setAmount(removedCount);
            ItemStack remainder = null;
            if (stack.getAmount() > removedCount) {
                remainder = stack.clone();
                remainder.setAmount(stack.getAmount() - removedCount);
            }
            debits.add(new ItemDebit(i, stack.clone(), removed, remainder));
            stillNeeded -= removedCount;
        }
        return stillNeeded == 0 ? debits : List.of();
    }

    private boolean applyItemDebits(PlayerInventory inventory, List<ItemDebit> debits) {
        for (ItemDebit debit : debits) {
            if (!java.util.Objects.equals(inventory.getItem(debit.slot()), debit.original())) return false;
        }
        for (ItemDebit debit : debits) inventory.setItem(debit.slot(), debit.remaining());
        return true;
    }

    private String resolveSharedPoolId(String poolId) {
        PoolDefinition pool = configuration.pools().get(poolId);
        if (pool == null || pool.type() != PoolType.GACHA || pool.gacha() == null) return poolId;
        // 优先使用奖池内显式声明的 shared-pity-group
        String group = pool.gacha().sharedPityGroup();
        if (group != null && !group.isBlank()) return group;
        // 其次用顶层 shared-pity-groups 通配符匹配
        Map<String, String> topGroups = configuration.sharedPityGroups();
        if (topGroups != null) {
            for (var entry : topGroups.entrySet()) {
                if (matchesWildcard(poolId, entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        return poolId;
    }

    private static boolean matchesWildcard(String text, String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return text.matches(regex);
    }

    // ─── 历史记录 ─────────────────────────────────────────

    /**
     * 查询玩家在指定奖池的 GACHA 抽卡历史。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @param limit      返回条数上限
     * @return 抽卡历史记录列表，按时间倒序
     */
    public List<LotteryRepository.GachaLogEntry> getGachaHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        return repository.getGachaHistory(playerUuid, poolId, limit);
    }

    /**
     * 查询玩家在指定奖池的 CASE 开箱历史。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     * @param limit      返回条数上限
     * @return 开箱历史记录列表，按时间倒序
     */
    public List<LotteryRepository.CaseLogEntry> getCaseHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        return repository.getCaseHistory(playerUuid, poolId, limit);
    }

    // ─── 管理操作 ─────────────────────────────────────────

    /**
     * 重置玩家在指定 GACHA 奖池的抽卡状态。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     */
    public boolean resetGachaState(@NotNull UUID playerUuid, @NotNull String poolId) {
        String effectivePoolId = resolveSharedPoolId(poolId);
        UUID operation = UUID.randomUUID();
        try {
            if (!repository.beginGachaOperation(playerUuid, operation, poolId, "管理员重置保底")) return false;
            repository.deleteGachaState(playerUuid, effectivePoolId);
            placeholderReads.invalidatePity(playerUuid, effectivePoolId);
            return repository.finishGachaOperation(playerUuid, operation, "DEBITING");
        } catch (SQLException failure) {
            logger.log(java.util.logging.Level.WARNING, "重置保底失败: " + operation, failure);
            return false;
        }
    }

    public LotteryRepository.GachaOperation getGachaOperation(UUID playerId) throws SQLException {
        return repository.findGachaOperation(playerId);
    }

    public LotteryRepository.GachaOperation getGachaOperation(UUID playerId, UUID operation) throws SQLException {
        return repository.findGachaOperation(playerId, operation);
    }

    public List<LotteryRepository.RefundClaim> getRefundClaims(UUID playerId, UUID operation) throws SQLException {
        return repository.findRefundClaims(playerId, operation);
    }

    public boolean resolveGachaOperation(UUID playerId, UUID operation, String expected, String reason)
        throws SQLException {
        if (!activePlayers.add(playerId)) return false;
        try {
            return repository.resolveGachaOperation(playerId, operation, expected, reason);
        } finally {
            activePlayers.remove(playerId);
        }
    }

    /**
     * 重置玩家在指定 CASE 奖池的开箱状态。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     */
    public boolean resetCaseState(@NotNull UUID playerUuid, @NotNull String poolId) {
        if (!activePlayers.add(playerUuid)) return false;
        try {
            repository.deleteCaseState(playerUuid, poolId);
            placeholderReads.invalidateOpened(playerUuid, poolId);
            return true;
        } finally { activePlayers.remove(playerUuid); }
    }

    // ─── 概率模拟 ─────────────────────────────────────────

    /**
     * 模拟指定奖池的抽奖统计（不扣费、不发奖、不保存状态）。
     *
     * @param poolId 奖池 ID
     * @param times  模拟次数
     * @return 按稀有度/星级分组的统计结果映射，key 为稀有度名称，value 为出现次数
     */
    public Map<String, Integer> simulate(@NotNull String poolId, int times) {
        PoolDefinition poolDef = getPool(poolId);
        if (poolDef == null) {
            throw new IllegalArgumentException("奖池不存在: " + poolId);
        }
        Map<String, Integer> stats = new java.util.LinkedHashMap<>();
        if (poolDef.type() == PoolType.GACHA && poolDef.gacha() != null) {
            PlayerGachaState state = PlayerGachaState.empty(java.util.UUID.randomUUID(), poolId);
            for (int i = 0; i < times; i++) {
                GachaResult result = gachaEngine.pull(poolDef.gacha(), state, 1);
                state = result.finalState();
                for (PoolItem item : result.items()) {
                    if (item == null) continue;
                    int star = describeGachaItem(poolDef, item).star();
                    String key = star + "star";
                    stats.merge(key, 1, Integer::sum);
                }
            }
        } else if (poolDef.type() == PoolType.CASE && poolDef.caseConfig() != null) {
            for (int i = 0; i < times; i++) {
                CaseResult result = caseEngine.openCase(poolDef.caseConfig());
                String rarity = result.rarity() == null ? "UNKNOWN" : result.rarity();
                stats.merge(rarity, 1, Integer::sum);
            }
        }
        return stats;
    }

    // ─── 辅助 ─────────────────────────────────────────────

    private String serializeItems(List<PoolItem> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            PoolItem item = items.get(i);
            sb.append(item != null ? item.key() : "null");
        }
        sb.append("]");
        return sb.toString();
    }

    public void sendFailureMessage(Player player) { sendMessage(player, "common.error"); }

    public String message(String key, Object... args) {
        if (messageProvider == null) return key;
        return messageProvider.get(key, args);
    }

    private void sendMessage(Player player, String key, Object... args) {
        if (messageProvider == null) return;
        String prefix = messageProvider.get("prefix");
        String msg = messageProvider.get(key, args);
        player.sendMessage(prefix + msg);
    }
}
