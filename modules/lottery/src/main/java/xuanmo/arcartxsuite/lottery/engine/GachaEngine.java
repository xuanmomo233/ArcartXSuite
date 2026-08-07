package xuanmo.arcartxsuite.lottery.engine;

import java.util.ArrayList;
import java.util.List;
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

        for (int i = 0; i < count; i++) {
            int currentPity5 = state.pity5star() + 1;
            int currentPity4 = state.pity4star() + 1;
            boolean isGuaranteed = state.guaranteedUp();
            int fatePoints = state.fatePoints();
            String fateTarget = state.fateTarget();

            double p5 = calculate5StarProbability(currentPity5, pool);

            if (currentPity5 >= pool.pity5star() || ThreadLocalRandom.current().nextDouble() < p5) {
                // 5星
                boolean isUp = isGuaranteed || rollUpChance(pool);
                if (isUp) {
                    if (pool.poolType() == GachaPoolConfig.GachaPoolType.WEAPON && pool.fatePointCap() > 0) {
                        PoolItem selected = resolveWeaponUp(pool, fatePoints, fateTarget);
                        if (selected != null && !selected.id().equals(fateTarget)) {
                            fatePoints++;
                        } else if (selected != null && selected.id().equals(fateTarget)) {
                            fatePoints = 0;
                            fateTarget = null;
                            fateTriggered = true;
                        }
                        results.add(selected != null ? selected : randomFrom(pool.up5starItems()));
                    } else {
                        results.add(randomFrom(pool.up5starItems()));
                    }
                    isGuaranteed = false;
                } else {
                    results.add(randomFrom(pool.standard5starItems()));
                    isGuaranteed = true;
                }
                currentPity5 = 0;
                currentPity4 = 0;
            } else if (currentPity4 >= pool.pity4star() || ThreadLocalRandom.current().nextDouble() < pool.base4starRate()) {
                // 4星
                currentPity4 = 0;
                results.add(randomFrom(merge(pool.up4starItems(), pool.standard4starItems())));
            } else {
                // 3星
                results.add(randomFrom(pool.star3Items()));
            }

            if (results.get(results.size() - 1) == null) {
                return new GachaResult(List.of(), state, state.guaranteedUp(), fateTriggered);
            }

            // 更新 state（使用新的 record 实例）
            state = new PlayerGachaState(
                state.playerUuid(), state.poolId(),
                currentPity5, currentPity4, isGuaranteed, fatePoints, fateTarget
            );
        }

        return new GachaResult(results, state, state.guaranteedUp(), fateTriggered);
    }

    private double calculate5StarProbability(int pity, GachaPoolConfig pool) {
        if (pity < pool.softPityStart()) return pool.base5starRate();
        int steps = pity - pool.softPityStart();
        return Math.min(pool.base5starRate() + steps * pool.softPityIncrement(), 1.0);
    }

    private boolean rollUpChance(GachaPoolConfig pool) {
        return ThreadLocalRandom.current().nextDouble() < pool.upRate();
    }

    private PoolItem randomFrom(List<PoolItem> items) {
        if (items == null || items.isEmpty()) return null;
        if (items.size() == 1) return items.get(0);
        int totalWeight = items.stream().mapToInt(PoolItem::weight).sum();
        if (totalWeight <= 0) return items.get(0);
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (PoolItem item : items) {
            current += item.weight();
            if (roll < current) return item;
        }
        return items.get(items.size() - 1);
    }

    private List<PoolItem> merge(List<PoolItem> a, List<PoolItem> b) {
        List<PoolItem> merged = new ArrayList<>();
        if (a != null) merged.addAll(a);
        if (b != null) merged.addAll(b);
        return merged;
    }

    private PoolItem resolveWeaponUp(GachaPoolConfig pool, int fatePoints, String fateTarget) {
        if (pool.fatePointCap() > 0 && fatePoints >= pool.fatePointCap() && fateTarget != null) {
            for (PoolItem item : pool.up5starItems()) {
                if (item.id().equals(fateTarget)) return item;
            }
        }
        return randomFrom(pool.up5starItems());
    }
}
