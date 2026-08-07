package xuanmo.arcartxsuite.combateffect.display.service;

/**
 * 战斗显示伤害来源枚举。
 * <p>
 * 属于 combateffect 模块的 display 子模块，标识当前实际生效的属性伤害来源。
 * {@link #NONE} 表示无可用来源。
 */
public enum CombatDisplayDamageSource {
    NONE,
    MYTHICLIB,
    CRANEATTRIBUTE,
    ATTRIBUTEPLUS,
    SYMPHONY,
    BUKKIT
}

