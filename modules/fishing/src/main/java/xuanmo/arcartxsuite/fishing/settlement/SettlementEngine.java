package xuanmo.arcartxsuite.fishing.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.reward.RewardDispatcher;
import xuanmo.arcartxsuite.fishing.reward.SeasonRewardDefinition;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;

/**
 * 赛季结算引擎，根据 ARIA 表达式条件评估并发放结算奖励。
 * <p>
 * 关联模块：{@code fishing}。赛季结束时遍历所有结算条目，
 * 对每个玩家评估条件表达式，满足条件则通过 {@link RewardDispatcher} 发放奖励。
 */
public final class SettlementEngine {

    private final List<SettlementDefinition> settlements;
    private final AriaBridge ariaBridge;
    private final FishingRepository repository;
    private final RewardDispatcher dispatcher;
    private final Logger logger;
    private final String seasonId;

    public SettlementEngine(@NotNull List<SettlementDefinition> settlements,
                            @Nullable AriaBridge ariaBridge,
                            @NotNull FishingRepository repository,
                            @NotNull RewardDispatcher dispatcher,
                            @NotNull Logger logger,
                            @NotNull String seasonId) {
        this.settlements = settlements;
        this.ariaBridge = ariaBridge;
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.logger = logger;
        this.seasonId = seasonId;
    }

    /**
     * 对单个玩家执行赛季结算。
     *
     * @param uuid 玩家 UUID
     */
    public void settlePlayer(@NotNull UUID uuid) {
        FishingSeasonData seasonData = repository.loadSeasonData(uuid, seasonId);
        FishingPermanentData permData = repository.loadPermanentData(uuid);
        int collectionCount = repository.loadCollection(uuid).size();

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("level", seasonData.level());
        bindings.put("total_caught", permData.totalCaught());
        bindings.put("perfect_catches", permData.perfectCatches());
        bindings.put("treasure_caught", permData.treasureCaught());
        bindings.put("collection_count", collectionCount);
        bindings.put("season_id", seasonId);

        Player player = Bukkit.getPlayer(uuid);
        for (SettlementDefinition settlement : settlements) {
            if (evaluateCondition(settlement.condition(), bindings)) {
                for (SeasonRewardDefinition reward : settlement.rewards()) {
                    dispatcher.dispatch(player, uuid, reward, settlement.description());
                }
                logger.fine("玩家 " + uuid + " 命中结算条目: " + settlement.id());
            }
        }
    }

    /**
     * 对所有在线玩家执行赛季结算（离线玩家在上线时由 pending 补发）。
     */
    public void settleAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                settlePlayer(player.getUniqueId());
            } catch (Exception e) {
                logger.warning("结算玩家 " + player.getName() + " 失败: " + e.getMessage());
            }
        }
    }

    /**
     * 评估 ARIA 条件表达式。
     *
     * @param condition 条件表达式
     * @param bindings  变量绑定
     * @return {@code true} 表示条件满足
     */
    private boolean evaluateCondition(@NotNull String condition, @NotNull Map<String, Object> bindings) {
        if (condition.isBlank() || "true".equals(condition)) return true;
        if (ariaBridge == null || !ariaBridge.available()) {
            logger.warning("Aria 桥不可用，结算条件无法评估: " + condition);
            return false;
        }
        try {
            Object result = ariaBridge.eval(condition, bindings);
            if (result instanceof Boolean b) return b;
            if (result instanceof Number n) return n.doubleValue() != 0;
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (Exception e) {
            logger.warning("评估结算条件失败 [" + condition + "]: " + e.getMessage());
            return false;
        }
    }
}
