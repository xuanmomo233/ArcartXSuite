package xuanmo.arcartxsuite.lottery.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.model.GachaResult;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

public class GachaEngine {

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
