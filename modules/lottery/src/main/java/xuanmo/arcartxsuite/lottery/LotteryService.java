package xuanmo.arcartxsuite.lottery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.lottery.config.CostConfig;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.config.LotteryModuleConfiguration;
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
        this.distributor = new PrizeDistributor(itemSourceRegistry, mailSupplier, logger);
    }

    public void setMessageProvider(MessageProvider provider) {
        this.messageProvider = provider;
    }

    public void start() {
        logger.info("[Lottery] 服务已启动");
    }

    public void shutdown() {
        logger.info("[Lottery] 服务已关闭");
    }

    // ─── 查询 ─────────────────────────────────────────────

    public Map<String, PoolDefinition> getPools() {
        return configuration.pools();
    }

    @Nullable
    public PoolDefinition getPool(@NotNull String poolId) {
        return configuration.pools().get(poolId);
    }

    public PlayerGachaState getGachaState(@NotNull Player player, @NotNull String poolId) {
        String effectivePoolId = resolveSharedPoolId(poolId);
        return repository.loadGachaState(player.getUniqueId(), effectivePoolId);
    }

    public PlayerCaseState getCaseState(@NotNull Player player, @NotNull String poolId) {
        return repository.loadCaseState(player.getUniqueId(), poolId);
    }

    // ─── Gacha 抽奖 ────────────────────────────────────────

    @NotNull
    public GachaResult pullGacha(@NotNull Player player, @NotNull String poolId, int count) {
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
        repository.logGachaPull(player.getUniqueId(), poolId, count, itemsJson, result.finalState().pity5star(), result.guaranteed());

        // 发放奖品
        for (PoolItem item : result.items()) {
            if (item != null) {
                distributor.distribute(player, item);
            }
        }

        return result;
    }

    // ─── Case 开箱 ────────────────────────────────────────

    @NotNull
    public CaseResult openCase(@NotNull Player player, @NotNull String poolId) {
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
            repository.logCaseOpen(player.getUniqueId(), poolId, result.item().id(), result.rarity(),
                result.stattrak(), result.wearValue(), result.wearTier());
        }

        // 发放奖品
        if (result != null && result.item() != null) {
            distributor.distribute(player, result.item());
        }

        return result;
    }

    // ─── 消耗扣除 ─────────────────────────────────────────

    private boolean deductCost(Player player, CostConfig cost, int multiplier) {
        int amount = (multiplier == 10) ? cost.ten() : cost.single() * multiplier;

        if (cost.mode() == CostConfig.CostMode.CURRENCY) {
            if (currencyManager == null) return false;
            var bridge = currencyManager.bridge(cost.currency());
            if (bridge == null || !bridge.available()) {
                sendMessage(player, "common.insufficient-currency", cost.currency());
                return false;
            }
            var result = bridge.withdraw(player, BigDecimal.valueOf(amount));
            if (!result.success()) {
                sendMessage(player, "common.insufficient-currency", cost.currency());
                return false;
            }
        } else {
            // ITEM 模式：检查并扣除物品
            // 简化处理：直接尝试从背包扣除指定物品
            // 实际应该用 ItemMatcher 匹配
            if (!removeItemFromInventory(player, cost.itemId(), cost.itemAmount() * multiplier)) {
                sendMessage(player, "common.insufficient-item", cost.itemId());
                return false;
            }
        }
        return true;
    }

    private boolean removeItemFromInventory(Player player, String itemId, int amount) {
        // 简化实现：后续可以用 ItemMatcher 精确匹配
        // 这里尝试生成物品并匹配 Material
        int removed = 0;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack stack = player.getInventory().getItem(i);
            if (stack == null) continue;
            // 简化匹配：先检查 Mythic/Neige ID
            String mythicId = itemSourceRegistry.mythicItemId(stack);
            String neigeId = itemSourceRegistry.neigeItemId(stack);
            if (itemId.equals(mythicId) || itemId.equals(neigeId) || stack.getType().name().equalsIgnoreCase(itemId)) {
                int toRemove = Math.min(stack.getAmount(), amount - removed);
                stack.setAmount(stack.getAmount() - toRemove);
                removed += toRemove;
                if (removed >= amount) break;
            }
        }
        return removed >= amount;
    }

    // ─── 共享保底组解析 ────────────────────────────────────

    private String resolveSharedPoolId(String poolId) {
        PoolDefinition pool = configuration.pools().get(poolId);
        if (pool == null || pool.type() != PoolType.GACHA || pool.gacha() == null) return poolId;
        String group = pool.gacha().sharedPityGroup();
        if (group == null || group.isBlank()) return poolId;
        return group; // 以 group name 作为 pool_id 存储
    }

    // ─── 历史记录 ─────────────────────────────────────────

    public List<LotteryRepository.GachaLogEntry> getGachaHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        return repository.getGachaHistory(playerUuid, poolId, limit);
    }

    public List<LotteryRepository.CaseLogEntry> getCaseHistory(@NotNull UUID playerUuid, @NotNull String poolId, int limit) {
        return repository.getCaseHistory(playerUuid, poolId, limit);
    }

    // ─── 管理操作 ─────────────────────────────────────────

    public void resetGachaState(@NotNull UUID playerUuid, @NotNull String poolId) {
        String effectivePoolId = resolveSharedPoolId(poolId);
        repository.deleteGachaState(playerUuid, effectivePoolId);
    }

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

    private void sendMessage(Player player, String key, Object... args) {
        if (messageProvider == null) return;
        String prefix = messageProvider.get("prefix");
        String msg = messageProvider.get(key, args);
        player.sendMessage(prefix + msg);
    }
}
