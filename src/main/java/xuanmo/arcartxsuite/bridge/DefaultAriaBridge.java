package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;

public final class DefaultAriaBridge implements AriaBridge {

    private static final String NEW_ARIA_CLASS =
        "priv.seventeen.artist.aria.Aria";
    private static final String NEW_CONTEXT_CLASS =
        "priv.seventeen.artist.aria.context.Context";
    private static final String NEW_VARIABLE_KEY_CLASS =
        "priv.seventeen.artist.aria.context.VariableKey";
    private static final String NEW_GLOBAL_STORAGE_CLASS =
        "priv.seventeen.artist.aria.context.GlobalStorage";
    private static final String NEW_IVALUE_CLASS =
        "priv.seventeen.artist.aria.value.IValue";
    private static final String NEW_OBJECT_VALUE_CLASS =
        "priv.seventeen.artist.aria.value.ObjectValue";
    private static final String NEW_IARIA_OBJECT_CLASS =
        "priv.seventeen.artist.aria.object.IAriaObject";
    private static final String NEW_JAVA_OBJECT_MIRROR_CLASS =
        "priv.seventeen.artist.aria.interop.JavaObjectMirror";
    private static final String[] DISCOVERY_PLUGINS = {
        "BlinkAriaHost",
        "ArcartX",
        "Aria"
    };

    private boolean available;
    private String version;
    private Method newCreateContext;
    private Method newEval;
    private Method newVariableKeyOf;
    private Method newGetGlobalStorage;
    private Method newGetGlobalVariable;
    private Method newSetValue;
    private Constructor<?> newJavaObjectMirrorCtor;
    private Constructor<?> newObjectValueCtor;

    public DefaultAriaBridge(JavaPlugin plugin) {
    }

    public void initialize() {
        reset();
        ClassLoader classLoader = resolveClassLoader();
        if (classLoader == null) {
            logInitializationFailure(
                new IllegalStateException(
                    "新版 Aria API 未在 ArcartX classloader 中找到"
                )
            );
            return;
        }
        if (!tryInitNewApi(classLoader)) {
            reset();
        }
    }

    private boolean tryInitNewApi(ClassLoader classLoader) {
        try {
            Class<?> ariaClass = Class.forName(
                NEW_ARIA_CLASS, true, classLoader
            );
            Class<?> contextClass = Class.forName(
                NEW_CONTEXT_CLASS, true, classLoader
            );
            Class<?> variableKeyClass = Class.forName(
                NEW_VARIABLE_KEY_CLASS, true, classLoader
            );
            Class<?> globalStorageClass = Class.forName(
                NEW_GLOBAL_STORAGE_CLASS, true, classLoader
            );
            Class<?> iValueClass = Class.forName(
                NEW_IVALUE_CLASS, true, classLoader
            );
            Class<?> objectValueClass = Class.forName(
                NEW_OBJECT_VALUE_CLASS, true, classLoader
            );
            Class<?> iAriaObjectClass = Class.forName(
                NEW_IARIA_OBJECT_CLASS, true, classLoader
            );
            Class<?> javaObjectMirrorClass = Class.forName(
                NEW_JAVA_OBJECT_MIRROR_CLASS, true, classLoader
            );

            newCreateContext = ariaClass.getMethod("createContext");
            newEval = ariaClass.getMethod("eval", String.class, contextClass);
            newVariableKeyOf = variableKeyClass.getMethod("of", String.class);
            newGetGlobalStorage = contextClass.getMethod("getGlobalStorage");
            newGetGlobalVariable = globalStorageClass.getMethod(
                "getGlobalVariable", variableKeyClass
            );
            Class<?> variableReferenceClass =
                newGetGlobalVariable.getReturnType();
            newSetValue = variableReferenceClass.getMethod(
                "setValue", iValueClass
            );
            newJavaObjectMirrorCtor =
                javaObjectMirrorClass.getConstructor(Object.class);
            newObjectValueCtor =
                objectValueClass.getConstructor(iAriaObjectClass);

            version = resolveNewApiVersion(ariaClass);
            available = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine(
                "[Aria] 新版桥接初始化成功"
                    + (version != null ? " (v" + version + ")" : "")
            );
            return true;
        } catch (ClassNotFoundException exception) {
            logInitializationFailure(exception);
        } catch (ReflectiveOperationException exception) {
            logInitializationFailure(exception);
        }
        return false;
    }

    private @Nullable String resolveNewApiVersion(Class<?> ariaClass) {
        try {
            Class<?> engineClass = Class.forName(
                "priv.seventeen.artist.aria.api.AriaEngine",
                true,
                ariaClass.getClassLoader()
            );
            try {
                Field versionField = engineClass.getDeclaredField("VERSION");
                versionField.setAccessible(true);
                Object value = versionField.get(null);
                return value == null ? null : String.valueOf(value);
            } catch (NoSuchFieldException ignored) {
                // AriaEngine 没有公开版本字段时使用默认版本标识。
            }
        } catch (ReflectiveOperationException ignored) {
            // 版本信息是诊断信息，不影响 Aria 执行。
        }
        return "new";
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public @Nullable String version() {
        return version;
    }

    @Override
    public @Nullable Object eval(
        @NotNull String code,
        @NotNull Map<String, Object> bindings
    ) {
        if (!available) {
            return null;
        }
        return evalNewApi(code, bindings);
    }

    private @Nullable Object evalNewApi(
        @NotNull String code,
        @NotNull Map<String, Object> bindings
    ) {
        try {
            Object context = newCreateContext.invoke(null);
            injectBindings(context, bindings);
            Object result = newEval.invoke(null, code, context);
            return unwrap(result);
        } catch (ReflectiveOperationException exception) {
            logEvalFailure("eval", exception);
            return null;
        }
    }

    @Override
    public boolean evalBoolean(
        @NotNull String code,
        @NotNull Map<String, Object> bindings
    ) {
        if (!available) {
            return false;
        }
        return evalBooleanNewApi(code, bindings);
    }

    private boolean evalBooleanNewApi(
        @NotNull String code,
        @NotNull Map<String, Object> bindings
    ) {
        try {
            Object context = newCreateContext.invoke(null);
            injectBindings(context, bindings);
            Object result = newEval.invoke(null, code, context);
            return AriaBridge.toBoolean(unwrap(result));
        } catch (ReflectiveOperationException exception) {
            logEvalFailure("evalBoolean", exception);
            return false;
        }
    }

    private void injectBindings(
        Object context,
        Map<String, Object> bindings
    ) throws ReflectiveOperationException {
        if (bindings.isEmpty()) {
            return;
        }
        Object storage = newGetGlobalStorage.invoke(context);
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            Object key = newVariableKeyOf.invoke(null, entry.getKey());
            Object mirror = newJavaObjectMirrorCtor.newInstance(value);
            Object objectValue = newObjectValueCtor.newInstance(mirror);
            Object reference = newGetGlobalVariable.invoke(storage, key);
            newSetValue.invoke(reference, objectValue);
        }
    }

    private @Nullable Object unwrap(@Nullable Object result)
        throws ReflectiveOperationException {
        if (result == null) {
            return null;
        }
        String typeName = result.getClass().getSimpleName();
        switch (typeName) {
            case "NoneValue" -> {
                return null;
            }
            case "NumberValue" -> {
                double number = ((Number) result.getClass()
                    .getMethod("numberValue")
                    .invoke(result))
                    .doubleValue();
                if (number == Math.rint(number)
                    && !Double.isInfinite(number)) {
                    return (long) number;
                }
                return number;
            }
            case "StringValue" -> {
                return result.getClass().getMethod("stringValue").invoke(result);
            }
            case "BooleanValue" -> {
                return result.getClass().getMethod("booleanValue").invoke(result);
            }
            default -> {
                return result.getClass().getMethod("jvmValue").invoke(result);
            }
        }
    }

    private void logInitializationFailure(Exception exception) {
        xuanmo.arcartxsuite.module.AxsLog.logger().log(
            Level.WARNING,
            "[Aria] 桥接初始化失败，Aria 不可用",
            exception
        );
    }

    private void logEvalFailure(
        String operation,
        ReflectiveOperationException exception
    ) {
        xuanmo.arcartxsuite.module.AxsLog.logger().log(
            Level.WARNING,
            "[Aria] " + operation + " 失败",
            exception
        );
    }

    private @Nullable ClassLoader resolveClassLoader() {
        for (String pluginName : DISCOVERY_PLUGINS) {
            Plugin candidate = Bukkit.getPluginManager().getPlugin(pluginName);
            if (candidate == null || !candidate.isEnabled()) {
                continue;
            }
            ClassLoader classLoader = candidate.getClass().getClassLoader();
            try {
                Class<?> ariaClass = Class.forName(
                    NEW_ARIA_CLASS, false, classLoader
                );
                Class<?> contextClass = Class.forName(
                    NEW_CONTEXT_CLASS, false, classLoader
                );
                ariaClass.getMethod("eval", String.class, contextClass);
                return classLoader;
            } catch (
                ClassNotFoundException | NoSuchMethodException ignored
            ) {
                // Candidate does not expose a self-consistent Aria API.
            }
        }
        return null;
    }

    private void reset() {
        available = false;
        version = null;
        newCreateContext = null;
        newEval = null;
        newVariableKeyOf = null;
        newGetGlobalStorage = null;
        newGetGlobalVariable = null;
        newSetValue = null;
        newJavaObjectMirrorCtor = null;
        newObjectValueCtor = null;
    }
}
