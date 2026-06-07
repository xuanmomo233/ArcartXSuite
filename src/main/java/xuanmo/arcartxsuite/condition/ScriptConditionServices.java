package xuanmo.arcartxsuite.condition;

import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;

public final class ScriptConditionServices {

    private static volatile ScriptConditionEvaluator evaluator = ScriptConditionEvaluator.noop();

    private ScriptConditionServices() {
    }

    public static ScriptConditionEvaluator evaluator() {
        return evaluator;
    }

    public static void install(ScriptConditionEvaluator installed) {
        evaluator = installed == null ? ScriptConditionEvaluator.noop() : installed;
    }

    public static void reset() {
        evaluator = ScriptConditionEvaluator.noop();
    }
}
