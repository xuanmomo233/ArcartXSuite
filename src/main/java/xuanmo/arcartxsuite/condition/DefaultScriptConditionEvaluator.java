package xuanmo.arcartxsuite.condition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionKind;
import xuanmo.arcartxsuite.api.condition.ScriptConditionOperator;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;

public final class DefaultScriptConditionEvaluator implements ScriptConditionEvaluator {

    private final AriaBridge ariaBridge;
    private final ScriptEngine jsEngine;
    private final PlaceholderResolverAPI placeholderResolver;

    public DefaultScriptConditionEvaluator(AriaBridge ariaBridge) {
        this(ariaBridge, null);
    }

    public DefaultScriptConditionEvaluator(AriaBridge ariaBridge, PlaceholderResolverAPI placeholderResolver) {
        this.ariaBridge = ariaBridge == null ? new UnavailableAriaBridge() : ariaBridge;
        this.placeholderResolver = placeholderResolver;
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        if (engine == null) {
            engine = manager.getEngineByName("nashorn");
        }
        this.jsEngine = engine;
    }

    @Override
    public boolean passes(@Nullable Player player, @NotNull List<ScriptCondition> conditions) {
        return firstFailed(player, conditions) == null;
    }

    @Override
    public @Nullable ScriptCondition firstFailed(@Nullable Player player, @NotNull List<ScriptCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        if (player == null) {
            return conditions.get(0);
        }
        for (ScriptCondition condition : conditions) {
            if (!evaluate(player, condition)) {
                return condition;
            }
        }
        return null;
    }

    @Override
    public @NotNull String applyPlaceholders(@Nullable Player player, @NotNull String input) {
        if (input == null) {
            return "";
        }
        if (player == null) {
            return input;
        }
        String withPlayer = input.replace("{player}", player.getName());
        if (placeholderResolver == null) {
            return withPlayer;
        }
        String result = placeholderResolver.applyPlaceholders(player, withPlayer);
        return result == null ? withPlayer : result;
    }

    @Override
    public @Nullable Object execute(@Nullable Player player, @NotNull ScriptConditionKind kind, @NotNull String script) {
        if (player == null || script == null || script.isBlank()) {
            return null;
        }
        if (kind == ScriptConditionKind.ARIA) {
            if (!ariaBridge.available()) {
                return null;
            }
            // Aria 无法反射调用注入的活对象（官方 Blink 模式）：
            // 先将 PAPI / {player} 展开进脚本源码，脚本只做纯运算。
            String expanded = applyPlaceholders(player, script);
            return ariaBridge.eval(expanded, new HashMap<>());
        }
        if (kind == ScriptConditionKind.JS) {
            if (jsEngine == null) {
                return null;
            }
            try {
                Bindings bindings = jsEngine.createBindings();
                bindings.put("player", player);
                bindings.put("Bukkit", Bukkit.class);
                return jsEngine.eval(script, bindings);
            } catch (Exception exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger()
                    .warning("[Menu] JS 动作执行失败: " + exception.getMessage());
                return null;
            }
        }
        return null;
    }

    private boolean evaluate(Player player, ScriptCondition condition) {
        if (condition.kind() == ScriptConditionKind.ARIA) {
            return evaluateAria(player, condition.script());
        }
        if (condition.kind() == ScriptConditionKind.JS) {
            return evaluateJs(player, condition.script());
        }
        String actual = resolvePlaceholder(player, condition.placeholder());
        ScriptConditionOperator operator = condition.operator() == null
            ? ScriptConditionOperator.EQ
            : condition.operator();
        return operator.evaluate(actual, condition.value());
    }

    private boolean evaluateAria(Player player, @Nullable String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        if (!ariaBridge.available()) {
            return false;
        }
        // Aria 条件同样采用“值拼进源码”模式：先展开 PAPI / {player}，再求值。
        String expanded = applyPlaceholders(player, script);
        return ariaBridge.evalBoolean(expanded, new HashMap<>());
    }

    private boolean evaluateJs(Player player, @Nullable String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        if (jsEngine == null) {
            return false;
        }
        try {
            Bindings bindings = jsEngine.createBindings();
            bindings.put("player", player);
            bindings.put("Bukkit", Bukkit.class);
            Object result = jsEngine.eval(script, bindings);
            return AriaBridge.toBoolean(result);
        } catch (Exception exception) {
            return false;
        }
    }

    private String resolvePlaceholder(Player player, @Nullable String placeholder) {
        if (placeholder == null) {
            return "";
        }
        if (placeholder.isBlank()) {
            return placeholder;
        }
        if (placeholderResolver == null) {
            return placeholder;
        }
        String result = placeholderResolver.applyPlaceholders(player, placeholder);
        return result == null ? "" : result;
    }

    private static final class UnavailableAriaBridge implements AriaBridge {
        @Override public boolean available() { return false; }
        @Override public @Nullable String version() { return null; }
        @Override public @Nullable Object eval(@NotNull String code, @NotNull Map<String, Object> bindings) { return null; }
    }
}
