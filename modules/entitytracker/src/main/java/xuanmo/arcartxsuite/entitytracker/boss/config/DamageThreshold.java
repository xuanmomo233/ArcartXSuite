package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Locale;

public record DamageThreshold(Mode mode, double value, String rawValue) {

    public enum Mode {
        ANY,
        ABSOLUTE,
        PERCENTAGE
    }

    private static final DamageThreshold DEFAULT = new DamageThreshold(Mode.PERCENTAGE, 0.01D, "1%");
    private static final DamageThreshold ANY = new DamageThreshold(Mode.ANY, 0.0D, "");

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

    public static DamageThreshold defaults() {
        return DEFAULT;
    }

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

