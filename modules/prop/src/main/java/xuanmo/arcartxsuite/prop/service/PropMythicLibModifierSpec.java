package xuanmo.arcartxsuite.prop.service;

/**
 * MythicLib 临时属性修改器的规格模型。
 * <p>
 * 由 {@link PropMythicLibModifierPlanner} 规划生成，供 {@link PropMythicLibService} 注册临时属性修改器使用。
 *
 * @param key            修改器唯一键，用于去重与清理
 * @param modifierName   修改器名称，传给 MythicLib API
 * @param statId         目标属性标识
 * @param value          属性数值
 * @param durationMillis 持续时间（毫秒），传给 MythicLib API
 * @param durationTicks  持续时间（tick），用于调度延迟清理
 */
public record PropMythicLibModifierSpec(
    String key,
    String modifierName,
    String statId,
    double value,
    long durationMillis,
    long durationTicks
) {
}
