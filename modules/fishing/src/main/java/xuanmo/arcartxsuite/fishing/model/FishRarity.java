package xuanmo.arcartxsuite.fishing.model;

public enum FishRarity {
    COMMON(1.0),
    UNCOMMON(0.5),
    RARE(0.15),
    LEGENDARY(0.02);

    private final double weightMultiplier;

    FishRarity(double weightMultiplier) {
        this.weightMultiplier = weightMultiplier;
    }

    public double weightMultiplier() {
        return weightMultiplier;
    }
}
