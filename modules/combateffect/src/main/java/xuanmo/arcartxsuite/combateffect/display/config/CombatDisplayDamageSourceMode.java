package xuanmo.arcartxsuite.combateffect.display.config;

import java.util.Locale;

/**
 * 战斗显示伤害来源模式枚举。
 * <p>
 * 属于 combateffect 模块的 display 子模块，用于配置伤害数字显示时
 * 优先采用哪个属性插件的伤害事件。{@link #AUTO} 表示自动检测可用来源。
 */
public enum CombatDisplayDamageSourceMode {
    AUTO,
    MYTHICLIB,
    CRANEATTRIBUTE,
    ATTRIBUTEPLUS,
    SYMPHONY,
    BUKKIT;

    /**
     * 从配置字符串解析伤害来源模式，无法识别时返回 {@link #AUTO}。
     *
     * @param rawValue 配置字符串，支持连字符分隔（如 {@code crane-attribute}）
     * @return 对应的伤害来源模式
     */
    public static CombatDisplayDamageSourceMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return AUTO;
        }
        String normalized = rawValue.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return CombatDisplayDamageSourceMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }
}

