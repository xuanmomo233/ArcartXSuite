package xuanmo.arcartxsuite.fishing.model;

public record TreasureDefinition(
    String id,
    String displayName,
    String item,
    double chance,
    int minAmount,
    int maxAmount
) {
}
