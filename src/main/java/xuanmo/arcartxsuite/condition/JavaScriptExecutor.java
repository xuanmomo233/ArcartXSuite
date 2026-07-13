package xuanmo.arcartxsuite.condition;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;

final class JavaScriptExecutor {

    private final ScriptEngine jsEngine;
    private final ScriptBindingsFactory bindingsFactory;

    JavaScriptExecutor(ScriptBindingsFactory bindingsFactory) {
        this.bindingsFactory = bindingsFactory;
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        if (engine == null) {
            engine = manager.getEngineByName("nashorn");
        }
        this.jsEngine = engine;
    }

    @Nullable
    Object executeAction(Player player, @Nullable String script) {
        if (script == null || script.isBlank()) {
            return null;
        }
        if (jsEngine == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[Script] JS 动作无法执行：JavaScript ScriptEngine 不可用"
            );
            return null;
        }
        try {
            Bindings bindings = jsEngine.createBindings();
            bindings.putAll(bindingsFactory.create(player, true));
            return jsEngine.eval(script, bindings);
        } catch (Exception exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().log(
                Level.WARNING,
                "[Script] JS 动作执行失败",
                exception
            );
            return null;
        }
    }

    boolean evaluateCondition(Player player, @Nullable String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        if (jsEngine == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[Script] JS 条件执行失败：JavaScript ScriptEngine 不可用"
            );
            return false;
        }
        try {
            Bindings bindings = jsEngine.createBindings();
            bindings.putAll(bindingsFactory.create(player, true));
            Object result = jsEngine.eval(script, bindings);
            return AriaBridge.toBoolean(result);
        } catch (Exception exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().log(
                Level.WARNING,
                "[Script] JS 条件执行失败",
                exception
            );
            return false;
        }
    }
}
