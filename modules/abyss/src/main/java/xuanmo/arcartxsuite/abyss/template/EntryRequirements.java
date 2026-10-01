package xuanmo.arcartxsuite.abyss.template;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;

/**
 * 进本条件（全部可选，缺省即不限制）。
 *
 * @param minPlayers     最少人数
 * @param maxPlayers     最多人数
 * @param partyRequired  是否必须组队（maxPlayers>1 时建议开）
 * @param minLevel       最低等级
 * @param maxLevel       最高等级（0=不限）
 * @param cooldownSeconds 同地牢冷却秒数
 * @param moneyCost      门票金额（宿主货币）
 * @param currencyId     货币 id（宿主 currencies 池）
 * @param staminaCost    体力消耗
 * @param requiredItems  所需物品清单（material:数量 或 mythic:物品id:数量）
 * @param requiredPermissions 所需权限
 * @param conditions     条件表达式（PAPI/ARIA/JS/CONTEXT，走宿主统一评估器）
 * @param requireAllPlayers 是否所有进本玩家都需满足条件（false=仅队长）
 * @param leaderOnlyConditions 仅队长需要满足的额外条件
 * @param disabledCommands 副本内禁用命令（无前缀匹配）
 * @param disabledItems   副本内禁用物品 material 列表
 */
public record EntryRequirements(
    int minPlayers,
    int maxPlayers,
    boolean partyRequired,
    int minLevel,
    int maxLevel,
    long cooldownSeconds,
    double moneyCost,
    String currencyId,
    int staminaCost,
    List<RequiredItem> requiredItems,
    List<String> requiredPermissions,
    List<ScriptCondition> conditions,
    boolean requireAllPlayers,
    List<ScriptCondition> leaderOnlyConditions,
    List<String> disabledCommands,
    List<String> disabledItems
) {

    public record RequiredItem(String source, String id, int amount) {
        /**
         * 解析 "material:DIAMOND:1" / "mythic:SomeSword:1" / "neige:id:1" / "overture:id:1" / "mmo:TYPE:ID:1"。
         */
        public static RequiredItem parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String[] parts = raw.trim().split(":");
            if (parts.length < 2) return null;
            String source = parts[0].toLowerCase();
            return switch (source) {
                case "material", "item" -> new RequiredItem("material",
                    parts[1].toUpperCase(), parts.length > 2 ? parseInt(parts[2], 1) : 1);
                case "mythic", "neige", "overture" -> new RequiredItem(source,
                    parts[1], parts.length > 2 ? parseInt(parts[2], 1) : 1);
                case "mmo" -> parts.length > 3
                    ? new RequiredItem("mmo", parts[1] + ":" + parts[2], parseInt(parts[3], 1))
                    : null;
                default -> new RequiredItem("material",
                    parts[0].toUpperCase(), parts.length > 1 ? parseInt(parts[1], 1) : 1);
            };
        }

        private static int parseInt(String s, int def) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
        }
    }

    public static EntryRequirements defaults() {
        return new EntryRequirements(1, 4, false, 0, 0, 0L, 0.0, "money", 0,
            List.of(), List.of(), List.of(), true, List.of(), List.of(), List.of());
    }

    private static List<String> firstNonEmpty(List<String> a, List<String> b) {
        return a.isEmpty() ? b : a;
    }

    public static EntryRequirements load(ConfigurationSection s) {
        if (s == null) return defaults();
        return new EntryRequirements(
            s.getInt("min-players", s.getInt("min_players", 1)),
            s.getInt("max-players", s.getInt("max_players", 4)),
            s.getBoolean("party-required", s.getBoolean("party_required", false)),
            s.getInt("min-level", s.getInt("min_level", 0)),
            s.getInt("max-level", s.getInt("max_level", 0)),
            s.getLong("cooldown", 0L),
            s.getDouble("money-cost", s.getDouble("money_cost", 0.0)),
            s.getString("currency-id", s.getString("currency_id", "money")),
            s.getInt("stamina-cost", s.getInt("stamina_cost", 0)),
            firstNonEmpty(s.getStringList("required-items"),
                s.getStringList("required_items")).stream()
                .map(RequiredItem::parse).filter(i -> i != null).toList(),
            firstNonEmpty(s.getStringList("required-permissions"),
                s.getStringList("required_permissions")),
            ScriptConditionsLoader.load(s, "conditions",
                "placeholder-conditions", "placeholder_conditions"),
            s.getBoolean("require-all-players", s.getBoolean("require_all_players", true)),
            ScriptConditionsLoader.load(s, "leader-conditions",
                "leader_conditions", "leader-only-conditions"),
            firstNonEmpty(s.getStringList("disabled-commands"),
                s.getStringList("disabled_commands")),
            firstNonEmpty(s.getStringList("disabled-items"),
                s.getStringList("disabled_items"))
        );
    }
}
