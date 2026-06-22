package xuanmo.arcartxsuite.menu.service;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;

public final class MenuConditionEvaluator {

    private MenuConditionEvaluator() {
    }

    public static boolean passes(Player player, List<ScriptCondition> conditions) {
        return ScriptConditionServices.evaluator().passes(player, conditions);
    }

    @Nullable
    public static ScriptCondition firstFailed(Player player, List<ScriptCondition> conditions) {
        return ScriptConditionServices.evaluator().firstFailed(player, conditions);
    }

    public static boolean hasPermission(Player player, String permission) {
        return ScriptConditionServices.evaluator().hasPermission(player, permission);
    }

    public static String resolvePlaceholder(Player player, String placeholder) {
        if (placeholder == null) {
            return "";
        }
        return ScriptConditionServices.evaluator().applyPlaceholders(player, placeholder);
    }

    public static String applyPlaceholders(Player player, String input) {
        return ScriptConditionServices.evaluator().applyPlaceholders(player, input);
    }
}
