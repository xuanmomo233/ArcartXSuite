package xuanmo.arcartxsuite.condition;

import java.util.Map;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;

/**
 * Aria 脚本执行器（包私有），封装占位符预处理 + Aria 求值的流程。
 * <p>
 * 由 {@link DefaultScriptConditionEvaluator} 持有，负责 ARIA 类型条件与动作的实际执行。
 */
final class AriaScriptExecutor {

    private final AriaBridge ariaBridge;
    private final ScriptBindingsFactory bindingsFactory;
    private final ScriptPlaceholderSupport placeholders;

    AriaScriptExecutor(
        AriaBridge ariaBridge,
        ScriptBindingsFactory bindingsFactory,
        ScriptPlaceholderSupport placeholders
    ) {
        this.ariaBridge = ariaBridge;
        this.bindingsFactory = bindingsFactory;
        this.placeholders = placeholders;
    }

    @Nullable
    Object executeAction(Player player, @Nullable String script) {
        if (script == null || script.isBlank()) {
            return null;
        }
        if (!ariaBridge.available()) {
            logUnavailable("[Script] ARIA 动作无法执行：Aria 引擎不可用");
            return null;
        }
        String expanded = placeholders.applyPlaceholders(player, script);
        return ariaBridge.eval(
            expanded,
            bindingsFactory.create(player, false)
        );
    }

    boolean evaluateCondition(Player player, @Nullable String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        if (!ariaBridge.available()) {
            logUnavailable("[Script] ARIA 条件执行失败：Aria 引擎不可用");
            return false;
        }
        String expanded = ScriptPlaceholderSupport.ensureReturn(
            placeholders.applyPlaceholders(player, script)
        );
        return ariaBridge.evalBoolean(
            expanded,
            bindingsFactory.create(player, false)
        );
    }

    private static void logUnavailable(String message) {
        xuanmo.arcartxsuite.module.AxsLog.logger().warning(message);
    }
}
