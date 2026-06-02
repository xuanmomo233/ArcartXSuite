package xuanmo.arcartxsuite.prop.service;

public record PropMythicLibModifierSpec(
    String key,
    String modifierName,
    String statId,
    double value,
    long durationMillis,
    long durationTicks
) {
}
