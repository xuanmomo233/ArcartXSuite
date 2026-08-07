package xuanmo.arcartxsuite.prop.service;

/**
 * Symphony 属性效果模型。
 * <p>
 * 由 {@link PropService} 从效果行解析而来，表示对某个 Symphony 属性的数值修改。
 *
 * @param attributeId Symphony 属性标识
 * @param value       属性数值
 * @param percent     是否为百分比修饰
 */
public record PropSymphonyEffect(
    String attributeId,
    double value,
    boolean percent
) {
}
