package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Map;

public record BossDamageRewardAction(
    BossDamageRewardActionType type,
    String itemId,
    int amount,
    String command,
    BossDamageRewardMessageTarget target,
    String text,
    String presetId,
    String signal
) {

    public static BossDamageRewardAction from(Map<?, ?> rawAction) {
        if (rawAction == null || rawAction.isEmpty()) {
            return null;
        }

        BossDamageRewardActionType type = BossDamageRewardActionType.parse(string(rawAction.get("type")));
        if (type == null) {
            return null;
        }

        String itemId = string(rawAction.get("item-id"));
        int amount = positiveInt(rawAction.get("amount"), 1);
        String command = string(rawAction.get("command"));
        BossDamageRewardMessageTarget target = BossDamageRewardMessageTarget.parse(string(rawAction.get("target")));
        String text = string(rawAction.get("text"));
        String presetId = string(rawAction.get("preset-id"));
        String signal = string(rawAction.get("signal"));
        return new BossDamageRewardAction(type, itemId, amount, command, target, text, presetId, signal);
    }

    private static String string(Object value) {
        if (value == null) {
            return "";
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    private static int positiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value instanceof String string) {
            try {
                return Math.max(1, Integer.parseInt(string.trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}

