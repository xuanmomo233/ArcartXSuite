package xuanmo.arcartxsuite.lottery.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.model.GachaResult;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

/**
 * GACHA 抽卡引擎。
 * <p>
 * 实现保底机制（硬保底/软保底）、UP 概率、大保底/小保底判定、
 * 武器池命运点定轨等核心抽卡逻辑。每次抽卡后返回更新后的玩家状态。
 */
public class GachaEngine {
    private final Map<GachaPoolConfig, PreparedGacha> prepared;

    public GachaEngine() {
        this(List.of());
    }

    public GachaEngine(Iterable<GachaPoolConfig> configurations) {
        var indexed = new IdentityHashMap<GachaPoolConfig, PreparedGacha>();
        for (var config : configurations) indexed.put(config, new PreparedGacha(config));
        prepared = Collections.unmodifiableMap(indexed);
    }

    /**
     * 执行指定次数的抽卡。
     *
     * @param pool  GACHA 奖池配置
     * @param state 玩家当前抽卡状态（保底计数、大保底标记、命运点等）
     * @param count 抽卡次数（1 或 10）
     * @return 抽卡结果，包含本次获得的奖品列表与更新后的状态
     */
    @NotNull
    public GachaResult pull(@NotNull GachaPoolConfig pool, @NotNull PlayerGachaState state, int count) {
        List<PoolItem> results = new ArrayList<>();
        boolean guaranteed = false;
        boolean fateTriggered = false;
        PreparedGacha known = prepared.get(pool);
        PreparedGacha table = known != null ? known : new PreparedGacha(pool);
        boolean hasUpFive = pool.hasUp5starItems();
        int currentPity5 = state.pity5star();
        int currentPity4 = state.pity4star();
        boolean isGuaranteed = state.guaranteedUp();
        int fatePoints = state.fatePoints();
        String fateTarget = state.fateTarget();

        for (int i = 0; i < count; i++) {
            currentPity5++;
            currentPity4++;

            double p5 = calculate5StarProbability(currentPity5, pool);

            if (currentPity5 >= pool.pity5star() || ThreadLocalRandom.current().nextDouble() < p5) {
                // 5星
                boolean isUp = hasUpFive && (isGuaranteed || rollUpChance(pool));
                if (isUp) {
                    if (pool.poolType() == GachaPoolConfig.GachaPoolType.WEAPON && pool.fatePointCap() > 0) {
                        PoolItem target = table.fateTargets.get(fateTarget);
                        PoolItem selected = target != null && fatePoints >= pool.fatePointCap() ? target : table.up5.roll(false);
                        if (selected != null && target != null) {
                            if (!selected.key().equals(target.key())) {
                                fatePoints++;
                            } else {
                                fatePoints = 0;
                                fateTarget = null;
                                fateTriggered = true;
                            }
                        }
                        results.add(selected);
                    } else {
                        results.add(table.up5.roll(false));
                    }
                    if (isGuaranteed) {
                        guaranteed = true;
                    }
                    isGuaranteed = false;
                } else {
                    results.add(table.standard5.roll(false));
                    if (hasUpFive) isGuaranteed = true;
                }
                currentPity5 = 0;
                currentPity4 = 0;
            } else if (currentPity4 >= pool.pity4star() || ThreadLocalRandom.current().nextDouble() < pool.base4starRate()) {
                // 4星: 按 upRate 分流 UP 与标准
                currentPity4 = 0;
                boolean isUp4 = rollUpChance(pool);
                if (isUp4) {
                    results.add(table.up4.roll(false));
                } else {
                    results.add(table.standard4.roll(false));
                }
            } else {
                // 3星
                results.add(table.star3.roll(false));
            }

            if (results.get(results.size() - 1) == null) {
                results.remove(results.size() - 1);
            }
        }

        if (count > 0) {
            state = new PlayerGachaState(state.playerUuid(), state.poolId(),
                currentPity5, currentPity4, isGuaranteed, fatePoints, fateTarget);
        }
        return new GachaResult(results, state, guaranteed, fateTriggered);
    }

    private double calculate5StarProbability(int pity, GachaPoolConfig pool) {
        if (pity < pool.softPityStart()) return pool.base5starRate();
        int steps = pity - pool.softPityStart();
        return Math.min(pool.base5starRate() + steps * pool.softPityIncrement(), 1.0);
    }

    private boolean rollUpChance(GachaPoolConfig pool) {
        return ThreadLocalRandom.current().nextDouble() < pool.upRate();
    }

    private static final class PreparedGacha {
        final WeightedPool<PoolItem> up5;
        final WeightedPool<PoolItem> standard5;
        final WeightedPool<PoolItem> up4;
        final WeightedPool<PoolItem> standard4;
        final WeightedPool<PoolItem> star3;
        final Map<String, PoolItem> fateTargets;

        PreparedGacha(GachaPoolConfig pool) {
            up5 = weights(pool.up5starItems());
            standard5 = weights(pool.standard5starItems());
            var up4Items = pool.up4starItems() == null ? List.<PoolItem>of() : pool.up4starItems();
            var standard4Items = pool.standard4starItems() == null ? List.<PoolItem>of() : pool.standard4starItems();
            up4 = weights(up4Items.isEmpty() ? standard4Items : up4Items);
            standard4 = weights(standard4Items.isEmpty() ? up4Items : standard4Items);
            star3 = weights(pool.star3Items());
            var targets = new HashMap<String, PoolItem>();
            if (pool.fatePointCap() > 0 && pool.up5starItems() != null) {
                for (var item : pool.up5starItems()) targets.putIfAbsent(item.key(), item);
            }
            fateTargets = Collections.unmodifiableMap(targets);
        }
    }

    private static WeightedPool<PoolItem> weights(List<PoolItem> items) {
        return new WeightedPool<>(items == null ? List.of() : items, PoolItem::weight);
    }

}
