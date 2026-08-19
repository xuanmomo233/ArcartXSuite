package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;

/**
 * {@link AriaBridge} 的默认实现，通过反射懒加载 ArcartX 内置的 Aria 脚本引擎。
 * <p>
 * Aria 为可选依赖，运行时从多个候选插件中探测引擎类，不可用时降级为
 * {@link #available()} 返回 false。
 */
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
    private static final String NEW_NUMBER_VALUE_CLASS =
        "priv.seventeen.artist.aria.value.NumberValue";
    private static final String NEW_STRING_VALUE_CLASS =
        "priv.seventeen.artist.aria.value.StringValue";
    private static final String NEW_BOOLEAN_VALUE_CLASS =
        "priv.seventeen.artist.aria.value.BooleanValue";
    private static final String NEW_NONE_VALUE_CLASS =
        "priv.seventeen.artist.aria.value.NoneValue";
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
    // 原生 IValue 构造/工厂，用于按 Java 类型正确包装绑定值（避免 JavaObjectMirror.canMath()=false 导致数值运算失效）
    private Constructor<?> newNumberValueCtor;
    private Constructor<?> newStringValueCtor;
    private Method newBooleanValueOf;
    private Object newNoneValueNone;

    // unwrap 方法缓存：按值类型类名缓存 Method，避免每次 eval 都做 getMethod 查找
    private final ConcurrentMap<String, Method> unwrapMethodCache = new ConcurrentHashMap<>();

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

            // 原生 IValue 类型映射：Number→NumberValue, String→StringValue, Boolean→BooleanValue, null→NoneValue
            Class<?> numberValueClass = Class.forName(
                NEW_NUMBER_VALUE_CLASS, true, classLoader
            );
            Class<?> stringValueClass = Class.forName(
                NEW_STRING_VALUE_CLASS, true, classLoader
            );
            Class<?> booleanValueClass = Class.forName(
                NEW_BOOLEAN_VALUE_CLASS, true, classLoader
            );
            Class<?> noneValueClass = Class.forName(
                NEW_NONE_VALUE_CLASS, true, classLoader
            );
            newNumberValueCtor =
                numberValueClass.getConstructor(double.class);
            newStringValueCtor =
                stringValueClass.getConstructor(String.class);
            newBooleanValueOf =
                booleanValueClass.getMethod("of", boolean.class);
            Field noneField = noneValueClass.getDeclaredField("NONE");
            noneField.setAccessible(true);
            newNoneValueNone = noneField.get(null);

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
            Object ariaValue = wrapToAriaValue(value);
            if (ariaValue == null) {
                continue;
            }
            Object key = newVariableKeyOf.invoke(null, entry.getKey());
            Object reference = newGetGlobalVariable.invoke(storage, key);
            newSetValue.invoke(reference, ariaValue);
        }
    }

    /**
     * 按 Java 类型映射到 ARIA 原生 IValue，确保数值/字符串/布尔能正确参与 ARIA 运算。
     * <p>
     * 旧实现统一用 {@code ObjectValue(JavaObjectMirror)} 包装，而 {@code JavaObjectMirror}
     * 未重写 {@code IAriaObject.canMath()}（默认 false），导致 Number 绑定值在 ARIA 数值
     * 表达式中被当作不可运算对象，乘法/加法等结果归零（如 xp-curve 公式 level=2 结果=0）。
     * <p>
     * 映射规则（对齐 {@code NativeCallable.wrapObject}）：
     * <ul>
     *   <li>null → NoneValue.NONE</li>
     *   <li>Number → NumberValue(double)</li>
     *   <li>String → StringValue</li>
     *   <li>Boolean → BooleanValue</li>
     *   <li>其他 → ObjectValue(JavaObjectMirror)（保留对象语义，供脚本访问成员）</li>
     * </ul>
     * 注意：绑定值注入到 {@code global.} 命名空间，脚本中需用 {@code global.<name>} 访问。
     *
     * @param value Java 端绑定值
     * @return 对应的 ARIA IValue 实例，null 表示跳过该绑定
     */
    private @Nullable Object wrapToAriaValue(Object value)
        throws ReflectiveOperationException {
        if (value == null) {
            return newNoneValueNone;
        }
        if (value instanceof Number number) {
            return newNumberValueCtor.newInstance(number.doubleValue());
        }
        if (value instanceof String text) {
            return newStringValueCtor.newInstance(text);
        }
        if (value instanceof Boolean bool) {
            return newBooleanValueOf.invoke(null, bool);
        }
        // 非基础类型保留对象镜像语义（脚本可通过 dot/[] 访问成员）
        Object mirror = newJavaObjectMirrorCtor.newInstance(value);
        return newObjectValueCtor.newInstance(mirror);
    }

    private @Nullable Object unwrap(@Nullable Object result)
        throws ReflectiveOperationException {
        if (result == null) {
            return null;
        }
        Class<?> resultClass = result.getClass();
        String typeName = resultClass.getSimpleName();
        switch (typeName) {
            case "NoneValue" -> {
                return null;
            }
            case "NumberValue" -> {
                Method numberValue = unwrapMethodCache.computeIfAbsent("NumberValue", k -> {
                    try { return resultClass.getMethod("numberValue"); }
                    catch (NoSuchMethodException e) { throw new RuntimeException(e); }
                });
                double number = ((Number) numberValue.invoke(result)).doubleValue();
                if (number == Math.rint(number)
                    && !Double.isInfinite(number)) {
                    return (long) number;
                }
                return number;
            }
            case "StringValue" -> {
                Method stringValue = unwrapMethodCache.computeIfAbsent("StringValue", k -> {
                    try { return resultClass.getMethod("stringValue"); }
                    catch (NoSuchMethodException e) { throw new RuntimeException(e); }
                });
                return stringValue.invoke(result);
            }
            case "BooleanValue" -> {
                Method booleanValue = unwrapMethodCache.computeIfAbsent("BooleanValue", k -> {
                    try { return resultClass.getMethod("booleanValue"); }
                    catch (NoSuchMethodException e) { throw new RuntimeException(e); }
                });
                return booleanValue.invoke(result);
            }
            default -> {
                Method jvmValue = unwrapMethodCache.get(typeName);
                if (jvmValue == null) {
                    jvmValue = resultClass.getMethod("jvmValue");
                    unwrapMethodCache.put(typeName, jvmValue);
                }
                return jvmValue.invoke(result);
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
        newNumberValueCtor = null;
        newStringValueCtor = null;
        newBooleanValueOf = null;
        newNoneValueNone = null;
    }
}
