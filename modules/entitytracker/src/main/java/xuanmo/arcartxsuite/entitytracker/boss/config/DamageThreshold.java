package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

/**
 * 伤害门槛定义，属于 entitytracker 模块的 boss 伤害排名子系统。
 * <p>
 * 支持三种模式：任意伤害均可、绝对值门槛、百分比门槛，
 * 用于判断玩家造成的伤害是否足以参与排名。
 *
 * @param mode     门槛模式
 * @param value    门槛数值（绝对值或百分比小数）
 * @param rawValue 原始配置文本，用于回显显示
 */
public record DamageThreshold(Mode mode, double value, String rawValue) {

    /**
     * 门槛模式枚举。
     * <ul>
     *   <li>{@link #ANY} — 任意伤害均可参与</li>
     *   <li>{@link #ABSOLUTE} — 绝对值门槛</li>
     *   <li>{@link #PERCENTAGE} — 百分比门槛（基于参考血量）</li>
     * </ul>
     */
    public enum Mode {
        ANY,
        ABSOLUTE,
        PERCENTAGE
    }

    private static final DamageThreshold DEFAULT = new DamageThreshold(Mode.PERCENTAGE, 0.01D, "1%");
    private static final DamageThreshold ANY = new DamageThreshold(Mode.ANY, 0.0D, "");

    /**
     * 从配置文本解析伤害门槛，支持百分比（如 "1%"）和绝对值格式。
     *
     * @param rawValue 原始配置文本
     * @return 解析得到的门槛，空值或解析失败时返回默认值或 ANY
     */
    public static DamageThreshold parse(String rawValue) {
        if (rawValue == null) {
            return DEFAULT;
        }

        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return DEFAULT;
        }

        try {
            if (normalized.endsWith("%")) {
                double percentage = Double.parseDouble(normalized.substring(0, normalized.length() - 1).trim()) / 100.0D;
                if (!Double.isFinite(percentage) || percentage < 0.0D) {
                    return ANY;
                }
                return new DamageThreshold(Mode.PERCENTAGE, percentage, normalized);
            }

            double absolute = Double.parseDouble(normalized);
            if (!Double.isFinite(absolute) || absolute < 0.0D) {
                return ANY;
            }
            return new DamageThreshold(Mode.ABSOLUTE, absolute, normalized);
        } catch (NumberFormatException ignored) {
            return ANY;
        }
    }

    /**
     * 返回默认的伤害门槛（1% 百分比）。
     *
     * @return 默认门槛
     */
    public static DamageThreshold defaults() {
        return DEFAULT;
    }

    /**
     * 判断给定伤害是否通过门槛。
     *
     * @param damage        玩家造成的伤害
     * @param referenceValue 参考血量（百分比模式使用）
     * @return {@code true} 表示伤害通过门槛
     */
    public boolean passes(double damage, double referenceValue) {
        if (damage <= 0.0D) {
            return false;
        }
        return switch (mode) {
            case ANY -> true;
            case ABSOLUTE -> damage >= value;
            case PERCENTAGE -> damage >= Math.max(0.0D, referenceValue) * value;
        };
    }

    /**
     * 返回门槛的可显示文本。
     *
     * @return 显示文本
     */
    public String displayText() {
        if (rawValue != null && !rawValue.isBlank()) {
            return rawValue;
        }
        return switch (mode) {
            case ANY -> "any";
            case ABSOLUTE -> Double.toString(value);
            case PERCENTAGE -> String.format(Locale.ROOT, "%.2f%%", value * 100.0D);
        };
    }
}

