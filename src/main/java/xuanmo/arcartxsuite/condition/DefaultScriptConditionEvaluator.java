package xuanmo.arcartxsuite.condition;

import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.condition.ScriptActionExecutor;
import xuanmo.arcartxsuite.api.condition.ScriptActionKind;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionKind;
import xuanmo.arcartxsuite.api.condition.ScriptConditionOperator;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.script.AriaBridge;

public final class DefaultScriptConditionEvaluator
    implements ScriptConditionEvaluator, ScriptActionExecutor {

    private final ScriptPlaceholderSupport placeholders;
    private final AriaScriptExecutor ariaScripts;
    private final JavaScriptExecutor javaScript;

    public DefaultScriptConditionEvaluator(AriaBridge ariaBridge) {
        this(ariaBridge, null);
    }

    public DefaultScriptConditionEvaluator(
        AriaBridge ariaBridge,
        PlaceholderResolverAPI placeholderResolver
    ) {
        AriaBridge bridge = ariaBridge == null
            ? new UnavailableAriaBridge()
            : ariaBridge;
        this.placeholders = new ScriptPlaceholderSupport(placeholderResolver);
        ScriptBindingsFactory bindings =
            new ScriptBindingsFactory(placeholderResolver);
        this.ariaScripts = new AriaScriptExecutor(
            bridge,
            bindings,
            placeholders
        );
        this.javaScript = new JavaScriptExecutor(bindings);
    }

    @Override
    public boolean passes(
        @Nullable Player player,
        @NotNull List<ScriptCondition> conditions
    ) {
        return firstFailed(player, conditions) == null;
    }

    @Override
    public @Nullable ScriptCondition firstFailed(
        @Nullable Player player,
        @NotNull List<ScriptCondition> conditions
    ) {
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
    public @NotNull String applyPlaceholders(
        @Nullable Player player,
        @NotNull String input
    ) {
        return placeholders.applyPlaceholders(player, input);
    }

    @Override
    public @Nullable Object execute(
        @Nullable Player player,
        @NotNull ScriptActionKind kind,
        @NotNull String script
    ) {
        if (player == null || script == null || script.isBlank()) {
            return null;
        }
        if (kind == ScriptActionKind.ARIA) {
            return ariaScripts.executeAction(player, script);
        }
        if (kind == ScriptActionKind.JS) {
            return javaScript.executeAction(player, script);
        }
        return null;
    }

    private boolean evaluate(
        Player player,
        ScriptCondition condition
    ) {
        if (condition.kind() == ScriptConditionKind.ARIA) {
            return ariaScripts.evaluateCondition(player, condition.script());
        }
        if (condition.kind() == ScriptConditionKind.JS) {
            return javaScript.evaluateCondition(player, condition.script());
        }
        String actual = placeholders.resolvePlaceholder(
            player,
            condition.placeholder()
        );
        ScriptConditionOperator operator = condition.operator() == null
            ? ScriptConditionOperator.EQ
            : condition.operator();
        return operator.evaluate(actual, condition.value());
    }

    private static final class UnavailableAriaBridge implements AriaBridge {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public @Nullable String version() {
            return null;
        }

        @Override
        public @Nullable Object eval(
            @NotNull String code,
            @NotNull Map<String, Object> bindings
        ) {
            return null;
        }
    }
}
