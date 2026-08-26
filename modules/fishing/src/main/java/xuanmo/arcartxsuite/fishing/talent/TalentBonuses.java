package xuanmo.arcartxsuite.fishing.talent;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * 天赋加成聚合容器，封装所有已点亮天赋的效果总和。
 * <p>
 * 在每场小游戏开始时一次性计算，传入 {@link xuanmo.arcartxsuite.fishing.minigame.FishingMinigame}。
 * 提供 getXxx 方法按效果类型获取加成值。
 */
public final class TalentBonuses {

    private final Map<String, Double> bonuses;

    public TalentBonuses(@NotNull Map<String, Double> bonuses) {
        this.bonuses = bonuses;
    }

    /** 绿条高度加成（像素，累加） */
    public double barHeightBonus() {
        return get(TalentEffect.Type.BAR_HEIGHT.key());
    }

    /** 绿条阻尼加成（0-1，累加） */
    public double barDampingBonus() {
        return get(TalentEffect.Type.BAR_DAMPING.key());
    }

    /** 绿条反弹减少（0-1，累加） */
    public double barBounceReduction() {
        return get(TalentEffect.Type.BAR_BOUNCE.key());
    }

    /** 绿条力系数加成（0-1，累加） */
    public double barForceBonus() {
        return get(TalentEffect.Type.BAR_FORCE.key());
    }

    /** 鱼速度降低（0-1，累加） */
    public double fishSpeedReduction() {
        return get(TalentEffect.Type.FISH_SPEED.key());
    }

    /** 鱼停留时间减少（0-1，累加） */
    public double fishDwellReduction() {
        return get(TalentEffect.Type.FISH_DWELL.key());
    }

    /** 鱼突进概率降低（0-1，累加） */
    public double fishDartReduction() {
        return get(TalentEffect.Type.FISH_DART.key());
    }

    /** 鱼噪声降低（0-1，累加） */
    public double fishNoiseReduction() {
        return get(TalentEffect.Type.FISH_NOISE.key());
    }

    /** 鱼边缘反弹降低（0-1，累加） */
    public double fishEdgeReduction() {
        return get(TalentEffect.Type.FISH_EDGE.key());
    }

    /** 逃离概率降低（0-1，累加） */
    public double fleeChanceReduction() {
        return get(TalentEffect.Type.FLEE_CHANCE.key());
    }

    /** 逃离速度降低（0-1，累加） */
    public double fleeSpeedReduction() {
        return get(TalentEffect.Type.FLEE_SPEED.key());
    }

    /** 逃离冷却增加（0-1，累加） */
    public double fleeCooldownBonus() {
        return get(TalentEffect.Type.FLEE_COOLDOWN.key());
    }

    /** 逃离持续时间减少（0-1，累加） */
    public double fleeDurationReduction() {
        return get(TalentEffect.Type.FLEE_DURATION.key());
    }

    /** 进度增速加成（0-1，累加） */
    public double progressRateBonus() {
        return get(TalentEffect.Type.PROGRESS_RATE.key());
    }

    /** 进度下降减少（0-1，累加） */
    public double progressDrainReduction() {
        return get(TalentEffect.Type.PROGRESS_DRAIN.key());
    }

    /** 完美窗口增大（0-1，累加） */
    public double perfectWindowBonus() {
        return get(TalentEffect.Type.PERFECT_WINDOW.key());
    }

    /** 起始进度增加（0-1，累加） */
    public double startProgressBonus() {
        return get(TalentEffect.Type.START_PROGRESS.key());
    }

    /** 钓鱼经验加成（0-1，累加） */
    public double xpMultiplierBonus() {
        return get(TalentEffect.Type.XP_MULTIPLIER.key());
    }

    /** 鱼售价加成（0-1，累加） */
    public double priceMultiplierBonus() {
        return get(TalentEffect.Type.PRICE_MULTIPLIER.key());
    }

    /** 传说鱼概率加成（0-1，累加） */
    public double legendaryChanceBonus() {
        return get(TalentEffect.Type.LEGENDARY_CHANCE.key());
    }

    /** 宝藏概率加成（0-1，累加） */
    public double treasureChanceBonus() {
        return get(TalentEffect.Type.TREASURE_CHANCE.key());
    }

    /** 稀有鱼概率加成（0-1，累加） */
    public double rareChanceBonus() {
        return get(TalentEffect.Type.RARE_CHANCE.key());
    }

    /** 绿条内经验加成（0-1，累加） */
    public double xpInBarBonus() {
        return get(TalentEffect.Type.XP_IN_BAR.key());
    }

    /** 完美捕获经验加成（0-1，累加） */
    public double perfectXpBonus() {
        return get(TalentEffect.Type.PERFECT_XP.key());
    }

    /** 钓鱼超时时间加成（0-1，累加） */
    public double catchDurationBonus() {
        return get(TalentEffect.Type.CATCH_DURATION.key());
    }

    /** 绿条重力降低（0-1，累加） */
    public double barGravityReduction() {
        return get(TalentEffect.Type.BAR_GRAVITY.key());
    }

    /** 绿条点击力加成（0-1，累加） */
    public double barClickForceBonus() {
        return get(TalentEffect.Type.BAR_CLICK_FORCE.key());
    }

    private double get(String key) {
        return bonuses.getOrDefault(key, 0.0);
    }

    /** 空加成（无天赋时使用） */
    public static TalentBonuses empty() {
        return new TalentBonuses(Map.of());
    }
}
