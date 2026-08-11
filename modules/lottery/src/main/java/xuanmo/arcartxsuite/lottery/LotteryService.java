package xuanmo.arcartxsuite.lottery;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
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
 * 通过每玩家 {@link ReentrantLock} 保证并发安全。
 */
public class LotteryService {

    private final JavaPlugin plugin;
    private final LotteryModuleConfiguration configuration;
    private final LotteryRepository repository;
    private final CurrencyBridgeAPI currencyManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final Supplier<MailDispatchable> mailSupplier;
    private final Logger logger;
    private final GachaEngine gachaEngine;
    private final CaseOpeningEngine caseEngine;
    private final PrizeDistributor distributor;

    private volatile MessageProvider messageProvider;
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

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
                          Supplier<MailDispatchable> mailSupplier,
                          Logger logger) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.currencyManager = currencyManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.mailSupplier = mailSupplier;
        this.logger = logger;
        this.gachaEngine = new GachaEngine();
        this.caseEngine = new CaseOpeningEngine();
        this.distributor = new PrizeDistributor(plugin, itemSourceRegistry, mailSupplier, logger);
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
     * 启动服务。
     */
    public void start() {
        logger.info("[Lottery] 服务已启动");
    }

    /**
     * 关闭服务，释放资源。
     */
    public void shutdown() {
        logger.info("[Lottery] 服务已关闭");
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
     * @param item 奖品定义
     * @return 生成的物品堆，生成失败时返回 {@code null}
     */
    @Nullable
    public ItemStack generateItem(@NotNull PoolItem item) {
        return distributor.generateItem(item);
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
        return repository.loadGachaState(player.getUniqueId(), effectivePoolId);
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
    public GachaResult pullGacha(@NotNull Player player, @NotNull String poolId, int count) {
        ReentrantLock lock = playerLocks.computeIfAbsent(player.getUniqueId(), ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            sendMessage(player, "common.lock-busy");
            return new GachaResult(List.of(), PlayerGachaState.empty(player.getUniqueId(), poolId), false, false);
        }
        try {
            return pullGachaLocked(player, poolId, count);
        } finally {
            unlock(player, lock);
        }
    }

    private GachaResult pullGachaLocked(@NotNull Player player, @NotNull String poolId, int count) {
        if (count != 1 && count != 10) {
            throw new IllegalArgumentException("Lottery pull count must be 1 or 10");
        }
        PoolDefinition poolDef = getPool(poolId);
        if (poolDef == null || poolDef.type() != PoolType.GACHA || poolDef.gacha() == null) {
            throw new IllegalArgumentException("奖池不存在或类型错误: " + poolId);
        }

        if (!deductCost(player, poolDef.cost(), count)) {
            return new GachaResult(List.of(), PlayerGachaState.empty(player.getUniqueId(), poolId), false, false);
        }

        String effectivePoolId = resolveSharedPoolId(poolId);
        PlayerGachaState state = repository.loadGachaState(player.getUniqueId(), effectivePoolId);
        GachaResult result = gachaEngine.pull(poolDef.gacha(), state, count);

        // 保存 GachaEngine 返回的最终状态
        repository.saveGachaState(result.finalState());

        // 日志
        String itemsJson = serializeItems(result.items());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
            repository.logGachaPull(player.getUniqueId(), poolId, count, itemsJson,
                result.finalState().pity5star(), result.guaranteed()));

        // 发放奖品
        for (PoolItem item : result.items()) {
            if (item != null) {
                distributeOrEnqueue(player, poolId, item);
            }
        }
        awardPoints(player, poolDef, result.items());

        return result;
    }

    // ─── Case 开箱 ────────────────────────────────────────

    /**
     * 执行一次 CASE 开箱。
     * <p>
     * 通过玩家锁保证并发安全，扣除费用后调用引擎开箱并发放奖品。
     *
     * @param player 玩家
     * @param poolId 奖池 ID
     * @return 开箱结果，锁繁忙时返回空结果
     * @throws IllegalArgumentException 当奖池不存在或类型错误时抛出
     * @throws IllegalStateException    当扣除开箱费用失败时抛出
     */
    @NotNull
    public CaseResult openCase(@NotNull Player player, @NotNull String poolId) {
        ReentrantLock lock = playerLocks.computeIfAbsent(player.getUniqueId(), ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            sendMessage(player, "common.lock-busy");
            return new CaseResult(null, "");
        }
        try {
            return openCaseLocked(player, poolId);
        } finally {
            unlock(player, lock);
        }
    }

    /**
     * 批量 CASE 开箱（十连抽）。
     * <p>
     * 一次性扣除十连费用，执行指定次数开箱并逐个发放奖品。
     *
     * @param player 玩家
     * @param poolId 奖池 ID
     * @param count  开箱次数
     * @return 开箱结果列表，锁繁忙时返回空列表
     * @throws IllegalArgumentException 当奖池不存在或类型错误时抛出
     * @throws IllegalStateException    当扣除开箱费用失败时抛出
     */
    @NotNull
    public List<CaseResult> openCaseBatch(@NotNull Player player, @NotNull String poolId, int count) {
        ReentrantLock lock = playerLocks.computeIfAbsent(player.getUniqueId(), ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            sendMessage(player, "common.lock-busy");
            return List.of();
        }
        try {
            return openCaseBatchLocked(player, poolId, count);
        } finally {
            unlock(player, lock);
        }
    }

    private CaseResult openCaseLocked(@NotNull Player player, @NotNull String poolId) {
        PoolDefinition poolDef = getPool(poolId);
        if (poolDef == null || poolDef.type() != PoolType.CASE || poolDef.caseConfig() == null) {
            throw new IllegalArgumentException("奖池不存在或类型错误: " + poolId);
        }

        if (!deductCost(player, poolDef.cost(), 1)) {
            throw new IllegalStateException("扣除开箱费用失败: " + poolId);
        }

        CaseResult result = caseEngine.openCase(poolDef.caseConfig());

        // 更新统计
        PlayerCaseState state = repository.loadCaseState(player.getUniqueId(), poolId);
        PlayerCaseState newState = new PlayerCaseState(
            state.playerUuid(), state.poolId(), state.openCount() + 1, System.currentTimeMillis());
        repository.saveCaseState(newState);

        // 日志
        if (result != null && result.item() != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                repository.logCaseOpen(player.getUniqueId(), poolId, result.item().id(), result.rarity()));
        }

        // 发放奖品
        if (result != null && result.item() != null) {
            distributeOrEnqueue(player, poolId, result.item());
        }
        if (result != null) awardPoints(player, poolDef, List.of(result.item()));

        return result;
    }

    private List<CaseResult> openCaseBatchLocked(@NotNull Player player, @NotNull String poolId, int count) {
        PoolDefinition poolDef = getPool(poolId);
        if (poolDef == null || poolDef.type() != PoolType.CASE || poolDef.caseConfig() == null) {
            throw new IllegalArgumentException("奖池不存在或类型错误: " + poolId);
        }

        if (!deductCost(player, poolDef.cost(), count)) {
            throw new IllegalStateException("扣除开箱费用失败: " + poolId);
        }

        List<CaseResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CaseResult result = caseEngine.openCase(poolDef.caseConfig());
            results.add(result);
            if (result != null && result.item() != null) {
                distributeOrEnqueue(player, poolId, result.item());
            }
        }

        // 更新统计
        PlayerCaseState state = repository.loadCaseState(player.getUniqueId(), poolId);
        PlayerCaseState newState = new PlayerCaseState(
            state.playerUuid(), state.poolId(), state.openCount() + count, System.currentTimeMillis());
        repository.saveCaseState(newState);

        // 日志（异步批量记录）
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (CaseResult result : results) {
                if (result != null && result.item() != null) {
                    repository.logCaseOpen(player.getUniqueId(), poolId, result.item().id(), result.rarity());
                }
            }
        });
        for (CaseResult result : results) {
            if (result != null) awardPoints(player, poolDef, List.of(result.item()));
        }

        return results;
    }

    private void distributeOrEnqueue(@NotNull Player player, @NotNull String poolId, @NotNull PoolItem item) {
        PrizeDistributor.DistributionResult outcome = distributor.distribute(player, item);
        if (outcome.success()) {
            if (outcome.mailSent()) {
                sendMessage(player, "common.mail-sent", item.name());
            }
            return;
        }

        String itemData = distributor.toItemJson(outcome.item());
        String metadata = item.id() + "|" + item.name() + "|" + item.delivery().name();
        if (!itemData.isBlank()) {
            long claimId = repository.enqueuePendingClaim(player.getUniqueId(), poolId, metadata, itemData);
            if (claimId > 0) {
                sendMessage(player, "common.pending-claim-enqueued", item.name());
            }
        } else if (item.delivery() != PoolItem.DeliveryType.COMMAND) {
            long claimId = repository.enqueuePendingClaim(player.getUniqueId(), poolId, metadata, null);
            if (claimId > 0) {
                sendMessage(player, "common.pending-claim-manual", item.name());
            }
            sendMessage(player, "common.distribution-failed", item.name());
            logger.severe("[Lottery] 奖励投递失败且无法序列化，需管理员处理: player="
                + player.getName() + ", pool=" + poolId + ", item=" + item.id()
                + ", reason=" + outcome.failureReason());
        } else {
            sendMessage(player, "common.distribution-failed", item.name());
            logger.severe("[Lottery] 非物品奖励投递失败，需管理员处理: player="
                + player.getName() + ", pool=" + poolId + ", item=" + item.id()
                + ", reason=" + outcome.failureReason());
        }
    }

    /**
     * 领取全部待发放奖励。
     *
     * @param player 玩家
     * @return 本次成功领取的奖励数量
     */
    public int claimPending(@NotNull Player player) {
        int claimed = 0;
        for (LotteryRepository.PendingClaim claim : repository.getPendingClaims(player.getUniqueId())) {
            if (claim.itemData() == null || claim.itemData().isBlank()) {
                continue;
            }
            try {
                org.bukkit.inventory.ItemStack stack = ItemSerializer.deserialize(
                    java.util.Base64.getDecoder().decode(claim.itemData()));
                if (stack == null) continue;
                if (!player.getInventory().addItem(stack).isEmpty()) {
                    break;
                }
                repository.markPendingClaimClaimed(claim.id());
                claimed++;
            } catch (Exception e) {
                logger.warning("[Lottery] 待领取奖励解析失败: " + e.getMessage());
            }
        }
        if (claimed > 0) {
            sendMessage(player, "common.pending-claim-claimed", claimed);
        }
        int remaining = repository.getPendingClaims(player.getUniqueId()).size();
        if (remaining > 0) {
            sendMessage(player, "common.pending-claim-available", remaining);
        }
        return claimed;
    }

    /**
     * 移除玩家的并发锁（在玩家退出时调用）。
     *
     * @param playerUuid 玩家 UUID
     */
    public void removePlayerLock(@NotNull UUID playerUuid) {
        ReentrantLock lock = playerLocks.get(playerUuid);
        if (lock == null || !lock.isLocked()) {
            playerLocks.remove(playerUuid, lock);
        }
    }

    private void unlock(Player player, ReentrantLock lock) {
        lock.unlock();
        removePlayerLock(player.getUniqueId());
    }

    // ─── 消耗扣除 ─────────────────────────────────────────

    /**
     * 一次消耗扣除的结果，成功时携带回滚所需的条目与背包快照。
     *
     * @param success   是否扣除成功
     * @param deducted  已成功扣除的条目
     * @param snapshots 扣除前的物品快照，按物品 ID 分组
     */
    private record CostTransaction(
        boolean success,
        List<CostConfig.CostEntry> deducted,
        Map<String, List<ItemStack>> snapshots
    ) {
        static CostTransaction failed() {
            return new CostTransaction(false, List.of(), Map.of());
        }
    }

    private boolean deductCost(Player player, CostConfig cost, int multiplier) {
        return deductCostTransaction(player, cost, multiplier).success();
    }

    private CostTransaction deductCostTransaction(Player player, CostConfig cost, int multiplier) {
        List<CostConfig.CostEntry> source = multiplier == 10 ? cost.ten() : cost.single();
        if (multiplier == 10 && source.isEmpty()) {
            sendMessage(player, "common.ten-unavailable");
            return CostTransaction.failed();
        }
        List<CostConfig.CostEntry> entries = new ArrayList<>();
        for (CostConfig.CostEntry entry : source) {
            int amount = multiplier == 10 ? entry.amount() : entry.amount() * multiplier;
            entries.add(new CostConfig.CostEntry(entry.type(), entry.id(), amount,
                entry.displayName(), entry.icon()));
        }
        if (entries.isEmpty()) return new CostTransaction(true, List.of(), Map.of());
        Map<String, Long> required = new java.util.LinkedHashMap<>();
        for (CostConfig.CostEntry entry : entries) {
            String key = entry.type().name() + ":" + entry.id();
            required.merge(key, (long) entry.amount(), Long::sum);
        }
        for (CostConfig.CostEntry entry : entries) {
            String key = entry.type().name() + ":" + entry.id();
            if (!required.containsKey(key)) continue;
            long amount = required.remove(key);
            boolean enough = switch (entry.type()) {
                case POINT -> repository.getPoint(player.getUniqueId(), entry.id()) >= amount;
                case ITEM -> countInventory(player, entry.id()) >= amount;
                case CURRENCY -> hasCurrency(player, entry.id(), amount);
            };
            if (!enough) {
                sendMessage(player, entry.type() == CostConfig.CostMode.POINT
                    ? "common.insufficient-point"
                    : entry.type() == CostConfig.CostMode.ITEM
                        ? "common.insufficient-item" : "common.insufficient-currency", entry.id());
                return CostTransaction.failed();
            }
        }
        List<CostConfig.CostEntry> changed = new ArrayList<>();
        Map<String, List<ItemStack>> snapshots = new java.util.LinkedHashMap<>();
        for (CostConfig.CostEntry entry : entries) {
            if (entry.type() == CostConfig.CostMode.ITEM) {
                snapshots.computeIfAbsent(entry.id(), ignored -> snapshotInventory(player, entry.id()));
            }
            boolean ok = switch (entry.type()) {
                case POINT -> repository.subtractPoints(player.getUniqueId(), entry.id(), entry.amount());
                case ITEM -> removeItemFromInventory(player, entry.id(), entry.amount());
                case CURRENCY -> withdrawCurrency(player, entry.id(), entry.amount());
            };
            if (!ok) {
                rollbackCost(player, changed, snapshots);
                sendMessage(player, "common.resource-cost-failed", entry.id());
                return CostTransaction.failed();
            }
            changed.add(entry);
        }
        return new CostTransaction(true, changed, snapshots);
    }

    private long countInventory(Player player, String itemId) {
        long total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && matchesItem(stack, itemId)) total += stack.getAmount();
        }
        return total;
    }

    private boolean hasCurrency(Player player, String id, long amount) {
        if (currencyManager == null) return false;
        var bridge = currencyManager.bridge(id);
        return bridge != null && bridge.available()
            && bridge.balance(player).compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    private boolean withdrawCurrency(Player player, String id, long amount) {
        if (currencyManager == null) return false;
        var bridge = currencyManager.bridge(id);
        return bridge != null && bridge.available()
            && bridge.withdraw(player, BigDecimal.valueOf(amount)).success();
    }

    private void rollbackCost(Player player, List<CostConfig.CostEntry> changed,
                              Map<String, List<ItemStack>> snapshots) {
        java.util.Set<String> restoredItems = new java.util.HashSet<>();
        Map<String, Integer> itemAmounts = new java.util.HashMap<>();
        for (CostConfig.CostEntry entry : changed) {
            if (entry.type() == CostConfig.CostMode.ITEM) {
                itemAmounts.merge(entry.id(), entry.amount(), Integer::sum);
            }
        }
        for (int i = changed.size() - 1; i >= 0; i--) {
            CostConfig.CostEntry entry = changed.get(i);
            switch (entry.type()) {
                case POINT -> repository.addPoints(player.getUniqueId(), entry.id(), entry.amount());
                case ITEM -> {
                    if (!restoredItems.add(entry.id())) break;
                    List<ItemStack> stacks = snapshots.get(entry.id());
                    if (stacks != null) {
                        restoreItemSnapshots(
                            player,
                            stacks,
                            itemAmounts.getOrDefault(entry.id(), 0)
                        );
                    }
                }
                case CURRENCY -> refundCurrency(player, entry.id(), entry.amount());
            }
        }
    }

    private List<ItemStack> snapshotInventory(Player player, String itemId) {
        List<ItemStack> snapshots = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && matchesItem(stack, itemId)) snapshots.add(stack.clone());
        }
        return snapshots;
    }

    private void restoreItemSnapshots(
            Player player,
            List<ItemStack> snapshots,
            int amount
    ) {
        int remaining = amount;
        for (ItemStack snapshot : snapshots) {
            if (remaining <= 0) break;
            ItemStack restored = snapshot.clone();
            restored.setAmount(Math.min(restored.getAmount(), remaining));
            remaining -= restored.getAmount();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(restored);
            enqueueOverflowItems(player, leftovers.values());
        }
    }

    private void enqueueOverflowItems(
            Player player,
            Collection<ItemStack> leftovers
    ) {
        for (ItemStack leftover : leftovers) {
            if (leftover == null || leftover.getAmount() <= 0) continue;
            String itemData = java.util.Base64.getEncoder().encodeToString(
                ItemSerializer.serialize(leftover)
            );
            repository.enqueuePendingClaim(
                player.getUniqueId(),
                "rollback",
                "rollback|" + leftover.getType().name(),
                itemData
            );
        }
    }

    private void refundCurrency(Player player, String id, long amount) {
        if (currencyManager == null) return;
        var bridge = currencyManager.bridge(id);
        if (bridge == null || !bridge.available()
                || !bridge.deposit(player, BigDecimal.valueOf(amount)).success()) {
            logger.warning("[Lottery] Currency rollback failed for " + id);
        }
    }

    private void awardPoints(Player player, PoolDefinition pool, List<PoolItem> items) {
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
            if (amount > 0) repository.addPoints(player.getUniqueId(), point.id(), amount);
        }
    }

    public Map<String, Long> getPoints(@NotNull UUID playerUuid) {
        return repository.getPoints(playerUuid);
    }

    public long getPoint(@NotNull UUID playerUuid, @NotNull String pointId) {
        return repository.getPoint(playerUuid, pointId);
    }

    public long getCurrencyBalance(@NotNull Player player, @NotNull String currencyId) {
        if (currencyManager == null) return -1L;
        var bridge = currencyManager.bridge(currencyId);
        if (bridge == null || !bridge.available()) return -1L;
        return bridge.balance(player).longValue();
    }

    public long getItemBalance(@NotNull Player player, @NotNull String itemId) {
        return countInventory(player, itemId);
    }

    public boolean givePoints(@NotNull UUID playerUuid, @NotNull String pointId, long amount) {
        return repository.addPoints(playerUuid, pointId, amount);
    }

    public Map<String, ExchangeShopItem> getExchangeItems() {
        return configuration.exchangeItems();
    }

    public int getExchangeCount(@NotNull UUID playerUuid, @NotNull String itemId) {
        return repository.getExchangeCount(playerUuid, itemId);
    }

    public boolean exchange(@NotNull Player player, @NotNull String itemId) {
        ExchangeShopItem item = configuration.exchangeItems().get(itemId);
        if (item == null) {
            sendMessage(player, "common.exchange-not-found");
            return false;
        }
        int used = repository.getExchangeCount(player.getUniqueId(), itemId);
        if (item.limit() > 0 && used >= item.limit()) {
            sendMessage(player, "common.exchange-limit");
            return false;
        }
        if (!repository.incrementExchangeCount(player.getUniqueId(), itemId)) {
            sendMessage(player, "common.exchange-failed");
            return false;
        }
        CostConfig cost = new CostConfig(item.cost(), item.cost());
        CostTransaction transaction = deductCostTransaction(player, cost, 1);
        if (!transaction.success()) {
            repository.decrementExchangeCount(player.getUniqueId(), itemId);
            return false;
        }
        try {
            distributeOrEnqueue(player, item.poolId() == null ? "exchange" : item.poolId(), item.reward());
        } catch (RuntimeException exception) {
            rollbackCost(player, transaction.deducted(), transaction.snapshots());
            repository.decrementExchangeCount(player.getUniqueId(), itemId);
            sendMessage(player, "common.exchange-failed");
            return false;
        }
        sendMessage(player, "common.exchange-success", item.displayName());
        return true;
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
        return items != null && items.stream().anyMatch(item -> item.id().equals(target.id()));
    }

    public record GachaItemInfo(int star, boolean up) {}

    private boolean removeItemFromInventory(Player player, String itemId, int amount) {
        if (amount <= 0) return false;
        int available = 0;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && matchesItem(stack, itemId)) {
                available += stack.getAmount();
                if (available >= amount) break;
            }
        }
        if (available < amount) return false;
        int removed = 0;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || !matchesItem(stack, itemId)) continue;
            int toRemove = Math.min(stack.getAmount(), amount - removed);
            stack.setAmount(stack.getAmount() - toRemove);
            removed += toRemove;
            if (removed >= amount) break;
        }
        return removed == amount;
    }

    private boolean matchesItem(org.bukkit.inventory.ItemStack stack, String itemId) {
        String mythicId = itemSourceRegistry.mythicItemId(stack);
        String neigeId = itemSourceRegistry.neigeItemId(stack);
        return itemId.equals(mythicId) || itemId.equals(neigeId)
            || stack.getType().name().equalsIgnoreCase(itemId);
    }

    private String resolveSharedPoolId(String poolId) {
        PoolDefinition pool = configuration.pools().get(poolId);
        if (pool == null || pool.type() != PoolType.GACHA || pool.gacha() == null) return poolId;
        String group = pool.gacha().sharedPityGroup();
        if (group == null || group.isBlank()) return poolId;
        return group; // 以 group name 作为 pool_id 存储
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
    public void resetGachaState(@NotNull UUID playerUuid, @NotNull String poolId) {
        String effectivePoolId = resolveSharedPoolId(poolId);
        repository.deleteGachaState(playerUuid, effectivePoolId);
    }

    /**
     * 重置玩家在指定 CASE 奖池的开箱状态。
     *
     * @param playerUuid 玩家 UUID
     * @param poolId     奖池 ID
     */
    public void resetCaseState(@NotNull UUID playerUuid, @NotNull String poolId) {
        repository.deleteCaseState(playerUuid, poolId);
    }

    // ─── 辅助 ─────────────────────────────────────────────

    private String serializeItems(List<PoolItem> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            PoolItem item = items.get(i);
            sb.append(item != null ? item.id() : "null");
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
