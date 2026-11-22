package xuanmo.arcartxsuite.menu.service;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;

/**
 * 菜单条件评估工具类，封装对 {@link ScriptConditionServices} 的委托调用。
 * <p>
 * 提供条件通过性检查、首个失败条件查找、权限检查和占位符替换等静态方法，
 * 供菜单服务各组件统一调用。
 */
public final class MenuConditionEvaluator {

    private MenuConditionEvaluator() {
    }

    /**
     * 评估玩家是否满足全部脚本条件。
     *
     * @param player     目标玩家
     * @param conditions 脚本条件列表
     * @return {@code true} 表示全部条件通过
     */
    public static boolean passes(Player player, List<ScriptCondition> conditions) {
        return ScriptConditionServices.evaluator().passes(player, conditions);
    }

    /**
     * 返回第一个未通过的条件，全部通过时返回 {@code null}。
     *
     * @param player     目标玩家
     * @param conditions 脚本条件列表
     * @return 首个失败的条件，或 {@code null}
     */
    @Nullable
    public static ScriptCondition firstFailed(Player player, List<ScriptCondition> conditions) {
        return ScriptConditionServices.evaluator().firstFailed(player, conditions);
    }

    /**
     * 检查玩家是否拥有指定权限，空权限视为始终通过。
     *
     * @param player     目标玩家
     * @param permission 权限节点
     * @return {@code true} 表示拥有权限
     */
    public static boolean hasPermission(Player player, String permission) {
        return ScriptConditionServices.evaluator().hasPermission(player, permission);
    }

    /**
     * 解析单个占位符文本。
     *
     * @param player      目标玩家
     * @param placeholder 占位符文本
     * @return 解析后的文本，null 输入返回空字符串
     */
    public static String resolvePlaceholder(Player player, String placeholder) {
        if (placeholder == null) {
            return "";
        }
        return ScriptConditionServices.evaluator().applyPlaceholders(player, placeholder);
    }

    /**
     * 对输入文本中的所有占位符进行解析替换。
     *
     * @param player 目标玩家
     * @param input  含占位符的原始文本
     * @return 解析后的文本
     */
    public static String applyPlaceholders(Player player, String input) {
        if (input == null) return "";
        if (!hasPlaceholders(input)) return input;
        return ScriptConditionServices.evaluator().applyPlaceholders(player, input);
    }

    static boolean hasPlaceholders(String input) {
        return input != null && (input.indexOf('%') >= 0 || input.contains("{player}"));
    }
}
