package xuanmo.arcartxsuite.condition;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
import xuanmo.arcartxsuite.api.script.AriaBridge;

public final class DefaultScriptConditionEvaluator implements ScriptConditionEvaluator {

    private static final MethodHandle PAPI_SET_PLACEHOLDERS = resolvePapiMethodHandle();

    private final AriaBridge ariaBridge;
    private final ScriptEngine jsEngine;

    public DefaultScriptConditionEvaluator(AriaBridge ariaBridge) {
        this.ariaBridge = ariaBridge == null ? new UnavailableAriaBridge() : ariaBridge;
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
        if (PAPI_SET_PLACEHOLDERS == null) {
            return input.replace("{player}", player.getName());
        }
        try {
            String withPlayer = input.replace("{player}", player.getName());
            String result = (String) PAPI_SET_PLACEHOLDERS.invokeExact(player, withPlayer);
            return result == null ? withPlayer : result;
        } catch (Throwable throwable) {
            return input.replace("{player}", player.getName());
        }
    }

    public static boolean hasPermission(@Nullable Player player, @Nullable String permission) {
        return permission == null || permission.isBlank() || (player != null && player.hasPermission(permission));
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
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("player", player);
        return ariaBridge.evalBoolean(script, bindings);
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
        if (PAPI_SET_PLACEHOLDERS == null || placeholder.isBlank()) {
            return placeholder;
        }
        try {
            String result = (String) PAPI_SET_PLACEHOLDERS.invokeExact(player, placeholder);
            return result == null ? "" : result;
        } catch (Throwable throwable) {
            return placeholder;
        }
    }

    private static MethodHandle resolvePapiMethodHandle() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return null;
        }
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return MethodHandles.publicLookup().findStatic(
                papiClass,
                "setPlaceholders",
                MethodType.methodType(String.class, Player.class, String.class)
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class UnavailableAriaBridge implements AriaBridge {
        @Override public boolean available() { return false; }
        @Override public @Nullable String version() { return null; }
        @Override public @Nullable Object eval(@NotNull String code, @NotNull Map<String, Object> bindings) { return null; }
    }
}
