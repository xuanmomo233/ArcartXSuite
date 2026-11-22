package xuanmo.arcartxsuite.fishing.service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import xuanmo.arcartxsuite.api.script.AriaBridge;

/** Loads the numeric Aria API from the jar supplied to the simulation task. */
final class SimulationAriaBridge implements AriaBridge {
    private static final String ARIA_PACKAGE = "priv.seventeen.artist.aria.";
    private final String jarName;
    private final Method createContext;
    private final Method compile;
    private final Method execute;
    private final Method getStorage;
    private final Method variableKey;
    private final Method getVariable;
    private final Method setValue;
    private final Constructor<?> number;
    private final Method jvmValue;
    private final Map<String, Object> compiled = new HashMap<>();
    private IllegalStateException failure;

    SimulationAriaBridge(String jarName) {
        this.jarName = jarName;
        try {
            Class<?> aria = type("Aria");
            Class<?> context = type("context.Context");
            Class<?> key = type("context.VariableKey");
            Class<?> value = type("value.IValue");
            createContext = aria.getMethod("createContext");
            compile = aria.getMethod("compile", String.class, String.class);
            execute = compile.getReturnType().getMethod("execute", context);
            getStorage = context.getMethod("getGlobalStorage");
            variableKey = key.getMethod("of", String.class);
            getVariable = getStorage.getReturnType().getMethod("getGlobalVariable", key);
            setValue = getVariable.getReturnType().getMethod("setValue", value);
            number = type("value.NumberValue").getConstructor(double.class);
            jvmValue = value.getMethod("jvmValue");
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalArgumentException("无法从 " + jarName + " 加载 Aria。请检查 JAR 和 Java 版本。", e);
        }
    }

    private static Class<?> type(String suffix) throws ClassNotFoundException {
        return Class.forName(ARIA_PACKAGE + suffix);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String version() {
        return jarName;
    }

    @Override
    public Object eval(String code, Map<String, Object> bindings) {
        verify();
        try {
            Object context = createContext.invoke(null);
            Object storage = getStorage.invoke(context);
            for (var binding : bindings.entrySet()) {
                Number value = (Number) binding.getValue();
                Object variable = getVariable.invoke(storage, variableKey.invoke(null, binding.getKey()));
                setValue.invoke(variable, number.newInstance(value.doubleValue()));
            }
            Object routine = compiled.get(code);
            if (routine == null) {
                routine = compile.invoke(null, "fishing-simulation", code);
                compiled.put(code, routine);
            }
            Object result = jvmValue.invoke(execute.invoke(routine, context));
            if (result instanceof Number value && Double.isFinite(value.doubleValue())) return result;
            throw new IllegalStateException("公式未返回有限数值");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException invocation ? invocation.getCause() : e;
            failure = new IllegalStateException("Aria 公式无法用于试钓: " + code, cause);
            throw failure;
        }
    }

    /** The live fight can fall back on invalid formulas. Reject those runs before reporting numbers. */
    void verify() {
        if (failure != null) throw failure;
    }
}
