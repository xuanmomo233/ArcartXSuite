package xuanmo.arcartxsuite.fishing.model;

/**
 * 鱼类稀有度枚举，附带权重乘数用于抽卡概率修正。
 * <p>
 * 关联模块：{@code fishing}。稀有度越高，{@link #weightMultiplier()} 越小，出现概率越低。
 */
public enum FishRarity {
    COMMON(1.0),
    UNCOMMON(0.6),
    RARE(0.4),
    LEGENDARY(0.1);

    private final double weightMultiplier;

    FishRarity(double weightMultiplier) {
        this.weightMultiplier = weightMultiplier;
    }

    public double weightMultiplier() {
        return weightMultiplier;
    }
}
