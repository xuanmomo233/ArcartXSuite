package xuanmo.arcartxsuite.entitytracker.boss.config;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 单条奖励动作定义，属于 entitytracker 模块的 boss 伤害排名奖励子系统。
 * <p>
 * 描述奖励的类型及对应参数（物品、命令、消息、邮件、信号等），
 * 由结算服务在发放奖励时根据类型执行相应逻辑。
 *
 * @param type     奖励动作类型
 * @param source   物品来源（minecraft/mythic/neige/overture/mmoitems），物品类奖励使用，默认 minecraft
 * @param id       物品来源 ID（物品类奖励使用）
 * @param amount   物品数量（至少为 1）
 * @param nbt      自定义 NBT（SNBT 字符串，仅 minecraft 源有效），可为 null
 * @param command  命令类奖励执行的命令文本
 * @param target   消息类奖励的发送目标
 * @param text     消息/邮件的文本内容
 * @param presetId 预设 ID（如邮件预设）
 * @param signal   信号类奖励派发的信号名称
 */
public record BossDamageRewardAction(
    BossDamageRewardActionType type,
    String source,
    String id,
    int amount,
    @Nullable String nbt,
    String command,
    BossDamageRewardMessageTarget target,
    String text,
    String presetId,
    String signal
) {

    /**
     * 从原始 Map 解析单条奖励动作。
     *
     * @param rawAction 原始配置 Map，为 {@code null} 或空时返回 {@code null}
     * @return 解析得到的动作实例，无法识别类型时返回 {@code null}
     */
    public static BossDamageRewardAction from(Map<?, ?> rawAction) {
        if (rawAction == null || rawAction.isEmpty()) {
            return null;
        }

        BossDamageRewardActionType type = BossDamageRewardActionType.parse(string(rawAction.get("type")));
        if (type == null) {
            return null;
        }

        String source = string(rawAction.get("source"));
        if (source.isBlank()) {
            // 兼容旧配置：type 字段曾同时表示物品来源，此处做回退推断
            source = legacySourceFromType(string(rawAction.get("type")));
        }
        String id = string(rawAction.get("id"));
        if (id.isBlank()) {
            // 兼容旧配置 item-id 字段
            id = string(rawAction.get("item-id"));
        }
        int amount = positiveInt(rawAction.get("amount"), 1);
        String nbt = nullableString(rawAction.get("nbt"));
        String command = string(rawAction.get("command"));
        BossDamageRewardMessageTarget target = BossDamageRewardMessageTarget.parse(string(rawAction.get("target")));
        String text = string(rawAction.get("text"));
        String presetId = string(rawAction.get("preset-id"));
        String signal = string(rawAction.get("signal"));
        return new BossDamageRewardAction(type, source, id, amount, nbt, command, target, text, presetId, signal);
    }

    /**
     * 兼容旧配置：当未显式配置 source 时，从旧 type 字段推断物品来源。
     */
    private static String legacySourceFromType(String rawType) {
        String normalized = rawType == null ? "" : rawType.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "neigeitems", "neigeitem" -> "neige";
            case "mythicitems", "mythicitem" -> "mythic";
            case "overture", "overtureitems", "overtureitem" -> "overture";
            default -> "minecraft";
        };
    }

    private static String string(Object value) {
        if (value == null) {
            return "";
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    @Nullable
    private static String nullableString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
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
