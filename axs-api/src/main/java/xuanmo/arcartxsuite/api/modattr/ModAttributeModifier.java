package xuanmo.arcartxsuite.api.modattr;

import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 一条 Forge 实体属性修饰器描述：{@code 属性 ID → (数值, 运算模式)}。
 * <p>
 * {@code attributeId} 使用完整命名空间形式（如 {@code "gunsmithlib:bullet_damage"}、
 * {@code "apothic_attributes:crit_damage"}），不带命名空间时默认按 {@code minecraft} 解析。
 * <p>
 * 数值语义由对应 Mod 的属性消费方定义，AXS 只负责原样写入修饰器，
 * 不做百分比/倍率等二次换算。常用约定（以 GunsmithLib 为例）：
 * <ul>
 *   <li>{@code bullet_damage}：固定值加成，10 = +10 伤害；</li>
 *   <li>{@code headshot_multiplier} / {@code armor_piercing_ratio}：
 *       比例型属性，1.0 = +100%、0.1 = +10%；</li>
 *   <li>{@code rpm} / {@code ammo_capacity}：固定值（rpm 有效范围 300~1200）。</li>
 * </ul>
 *
 * @since 1.7.0
 */
@ApiStability.Stable
public record ModAttributeModifier(String attributeId, double amount, Operation operation) {

    /** 修饰器运算模式，语义同 {@code AttributeModifier.Operation}。 */
    public enum Operation {
        /** 加算：最终值 += amount（最常用，固定值与比例型属性都用它） */
        ADDITION,
        /** 乘算（基于基础值）：最终值 += 基础值 × amount */
        MULTIPLY_BASE,
        /** 乘算（基于当前总值）：最终值 ×= (1 + amount) */
        MULTIPLY_TOTAL
    }

    /** 创建加算修饰器（默认模式）。 */
    public static ModAttributeModifier addition(String attributeId, double amount) {
        return new ModAttributeModifier(attributeId, amount, Operation.ADDITION);
    }

    /** 创建乘算（基于基础值）修饰器。 */
    public static ModAttributeModifier multiplyBase(String attributeId, double amount) {
        return new ModAttributeModifier(attributeId, amount, Operation.MULTIPLY_BASE);
    }

    /** 创建乘算（基于当前总值）修饰器。 */
    public static ModAttributeModifier multiplyTotal(String attributeId, double amount) {
        return new ModAttributeModifier(attributeId, amount, Operation.MULTIPLY_TOTAL);
    }
}
