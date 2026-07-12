package xuanmo.arcartxsuite.condition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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

    private static final Pattern RETURN_PATTERN = Pattern.compile("\\breturn\\b");

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
            // 官方 Overture 写法：注入活的 player 对象（可调用其 Bukkit 方法），
            // 同时展开 PAPI / {player} 便于引用不在 Player 对象上的占位符值。
            // 动作脚本按原样执行（副作用即执行），返回值由调用方决定是否使用。
            String expanded = applyPlaceholders(player, script);
            return ariaBridge.eval(expanded, ariaBindings(player));
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
        // 条件为单个表达式，需补 return 才能取回布尔值（Aria 顶层裸表达式不产出返回值）；
        // 若用户已自行写了 return / if-return 则原样执行。同时注入活的 player 对象。
        String expanded = ensureReturn(applyPlaceholders(player, script));
        return ariaBridge.evalBoolean(expanded, ariaBindings(player));
    }

    private static Map<String, Object> ariaBindings(Player player) {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("player", player);
        return bindings;
    }

    private static String ensureReturn(String script) {
        if (script == null || script.isBlank()) {
            return script;
        }
        if (RETURN_PATTERN.matcher(script).find()) {
            return script;
        }
        return "return " + script;
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
