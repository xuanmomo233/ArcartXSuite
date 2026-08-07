package xuanmo.arcartxsuite.combateffect.display.service;

import xuanmo.arcartxsuite.combateffect.display.config.CombatDisplayDamageSourceMode;

/**
 * 伤害来源解析器。
 * <p>
 * 属于 combateffect 模块的 display 子模块，根据配置模式和各属性插件的
 * 可用性，解析出当前实际生效的伤害来源（{@link CombatDisplayDamageSource}）。
 * 支持自动检测（AUTO）和指定来源回退（fallback）策略。
 */
public final class CombatDisplayDamageSourceResolver {

    private CombatDisplayDamageSourceResolver() {
    }

    /**
     * 解析当前生效的伤害来源。
     * <p>
     * 当模式为 {@link CombatDisplayDamageSourceMode#AUTO} 时按优先级自动检测；
     * 当指定来源不可用时，若 {@code fallback} 为 {@code true} 则回退到下一个可用来源，
     * 否则返回 {@link CombatDisplayDamageSource#NONE}。
     *
     * @param mode                   配置的伤害来源模式
     * @param fallback               指定来源不可用时是否回退
     * @param mythicLibAvailable     MythicLib 是否可用
     * @param craneAttributeAvailable CraneAttribute 是否可用
     * @param attributePlusAvailable  AttributePlus 是否可用
     * @param symphonyAvailable       Symphony 是否可用
     * @param bukkitAvailable         Bukkit 原生伤害是否可用
     * @return 解析出的伤害来源
     */
    public static CombatDisplayDamageSource resolve(
        CombatDisplayDamageSourceMode mode,
        boolean fallback,
        boolean mythicLibAvailable,
        boolean craneAttributeAvailable,
        boolean attributePlusAvailable,
        boolean symphonyAvailable,
        boolean bukkitAvailable
    ) {
        CombatDisplayDamageSourceMode effectiveMode = mode == null ? CombatDisplayDamageSourceMode.AUTO : mode;
        return switch (effectiveMode) {
            case AUTO -> firstAvailable(mythicLibAvailable, craneAttributeAvailable, attributePlusAvailable, symphonyAvailable, bukkitAvailable);
            case MYTHICLIB -> mythicLibAvailable
                ? CombatDisplayDamageSource.MYTHICLIB
                : (fallback ? firstAvailable(false, craneAttributeAvailable, attributePlusAvailable, symphonyAvailable, bukkitAvailable) : CombatDisplayDamageSource.NONE);
            case CRANEATTRIBUTE -> craneAttributeAvailable
                ? CombatDisplayDamageSource.CRANEATTRIBUTE
                : (fallback ? firstAvailable(false, false, attributePlusAvailable, symphonyAvailable, bukkitAvailable) : CombatDisplayDamageSource.NONE);
            case ATTRIBUTEPLUS -> attributePlusAvailable
                ? CombatDisplayDamageSource.ATTRIBUTEPLUS
                : (fallback ? firstAvailable(false, false, false, symphonyAvailable, bukkitAvailable) : CombatDisplayDamageSource.NONE);
            case SYMPHONY -> symphonyAvailable
                ? CombatDisplayDamageSource.SYMPHONY
                : (fallback ? firstAvailable(false, false, false, false, bukkitAvailable) : CombatDisplayDamageSource.NONE);
            case BUKKIT -> bukkitAvailable ? CombatDisplayDamageSource.BUKKIT : CombatDisplayDamageSource.NONE;
        };
    }

    private static CombatDisplayDamageSource firstAvailable(
        boolean mythicLibAvailable,
        boolean craneAttributeAvailable,
        boolean attributePlusAvailable,
        boolean symphonyAvailable,
        boolean bukkitAvailable
    ) {
        if (mythicLibAvailable) {
            return CombatDisplayDamageSource.MYTHICLIB;
        }
        if (craneAttributeAvailable) {
            return CombatDisplayDamageSource.CRANEATTRIBUTE;
        }
        if (attributePlusAvailable) {
            return CombatDisplayDamageSource.ATTRIBUTEPLUS;
        }
        if (symphonyAvailable) {
            return CombatDisplayDamageSource.SYMPHONY;
        }
        if (bukkitAvailable) {
            return CombatDisplayDamageSource.BUKKIT;
        }
        return CombatDisplayDamageSource.NONE;
    }
}

