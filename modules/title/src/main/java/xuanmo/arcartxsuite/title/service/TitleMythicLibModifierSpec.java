package xuanmo.arcartxsuite.title.service;

/**
 * MythicLib 属性修饰器规格。
 * <p>
 * 描述单个 MythicLib 属性修饰器的名称、属性键与数值，
 * 用于同步计划中批量注册/移除修饰器。
 *
 * @param modifierName 修饰器名称
 * @param statId       MythicLib 属性键
 * @param value        属性数值
 */
public record TitleMythicLibModifierSpec(
    String modifierName,
    String statId,
    double value
) {
}
