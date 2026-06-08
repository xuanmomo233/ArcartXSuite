package xuanmo.arcartxsuite.combateffect.display.service;

import xuanmo.arcartxsuite.combateffect.display.config.CombatDisplayDamageSourceMode;

public final class CombatDisplayDamageSourceResolver {

    private CombatDisplayDamageSourceResolver() {
    }

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

