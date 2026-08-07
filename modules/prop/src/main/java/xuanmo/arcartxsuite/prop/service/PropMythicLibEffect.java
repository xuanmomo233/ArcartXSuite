package xuanmo.arcartxsuite.prop.service;

/**
 * MythicLib 属性效果模型。
 * <p>
 * 由 {@link PropMythicLibEffectParser} 从效果行解析而来，表示对某个 MythicLib 属性的数值修改。
 *
 * @param statId MythicLib 属性标识（已归一化）
 * @param value  属性数值
 */
public record PropMythicLibEffect(
    String statId,
    double value
) {
}
