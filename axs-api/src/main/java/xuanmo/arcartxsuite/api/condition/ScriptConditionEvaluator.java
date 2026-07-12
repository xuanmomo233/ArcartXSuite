package xuanmo.arcartxsuite.api.condition;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ScriptConditionEvaluator {

    boolean passes(@Nullable Player player, @NotNull List<ScriptCondition> conditions);

    @Nullable
    ScriptCondition firstFailed(@Nullable Player player, @NotNull List<ScriptCondition> conditions);

    @NotNull
    String applyPlaceholders(@Nullable Player player, @NotNull String input);

    /**
     * 执行一段脚本（用于动作/副作用），返回脚本结果；PAPI 类型不支持，返回 null。
     * 默认实现不执行任何脚本。
     */
    @Nullable
    default Object execute(@Nullable Player player, @NotNull ScriptConditionKind kind, @NotNull String script) {
        return null;
    }

    default boolean hasPermission(@Nullable Player player, @Nullable String permission) {
        return permission == null || permission.isBlank() || (player != null && player.hasPermission(permission));
    }

    static ScriptConditionEvaluator noop() {
        return NoopScriptConditionEvaluator.INSTANCE;
    }
}
