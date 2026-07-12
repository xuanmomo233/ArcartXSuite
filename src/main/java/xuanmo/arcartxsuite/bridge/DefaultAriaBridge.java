package xuanmo.arcartxsuite.bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.script.AriaBridge;

public final class DefaultAriaBridge implements AriaBridge {

    // ── 新版 Aria API (priv.seventeen.artist.aria) ──────────────
    private static final String NEW_ARIA_CLASS = "priv.seventeen.artist.aria.Aria";
    private static final String NEW_CONTEXT_CLASS = "priv.seventeen.artist.aria.context.Context";
    private static final String NEW_VARIABLE_KEY_CLASS = "priv.seventeen.artist.aria.context.VariableKey";
    private static final String NEW_GLOBAL_STORAGE_CLASS = "priv.seventeen.artist.aria.context.GlobalStorage";
    private static final String NEW_IVALUE_CLASS = "priv.seventeen.artist.aria.value.IValue";
    private static final String NEW_OBJECT_VALUE_CLASS = "priv.seventeen.artist.aria.value.ObjectValue";
    private static final String NEW_IARIA_OBJECT_CLASS = "priv.seventeen.artist.aria.object.IAriaObject";
    private static final String NEW_JAVA_OBJECT_MIRROR_CLASS = "priv.seventeen.artist.aria.interop.JavaObjectMirror";

    // ── 旧版 Blink API ──────────────────────────────────────────
    private static final String OLD_MANAGER_CLASS = "priv.seventeen.artist.blink.script.AriaScriptManager";

    // ── 宿主插件发现 ────────────────────────────────────────────
    // ArcartX 为硬依赖（plugin.yml depend），内置 Aria 语言，优先从其 classloader 发现；
    // 其余为旧版 Blink 生态的兼容回退（已非必需）。
    private static final String[] DISCOVERY_PLUGINS = {
        "ArcartX", "Aria", "Symphony", "Overture", "BlinkAriaHost"
    };

    private final JavaPlugin plugin;

    private boolean available;
    private String version;
    private boolean newApi;

    // 新版 API 反射缓存
    private Method newCreateContext;
    private Method newEval;
    private Method newVariableKeyOf;
    private Method newGetGlobalStorage;
    private Method newGetGlobalVariable;
    private Method newSetValue;
    private Constructor<?> newJavaObjectMirrorCtor;
    private Constructor<?> newObjectValueCtor;

    // 旧版 API 反射缓存
    private Object oldManagerInstance;
    private Method oldEvalMethod;

    public DefaultAriaBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();
        ClassLoader classLoader = resolveClassLoader();
        if (classLoader == null) {
            return;
        }
        // 优先尝试新版 Aria API
        if (tryInitNewApi(classLoader)) {
            return;
        }
        // 回退到旧版 Blink API
        tryInitOldApi(classLoader);
    }

    // ── 新版 Aria 初始化 ────────────────────────────────────────

    private boolean tryInitNewApi(ClassLoader cl) {
        try {
            Class<?> ariaClass = Class.forName(NEW_ARIA_CLASS, true, cl);
            Class<?> contextClass = Class.forName(NEW_CONTEXT_CLASS, true, cl);
            Class<?> variableKeyClass = Class.forName(NEW_VARIABLE_KEY_CLASS, true, cl);
            Class<?> globalStorageClass = Class.forName(NEW_GLOBAL_STORAGE_CLASS, true, cl);
            Class<?> iValueClass = Class.forName(NEW_IVALUE_CLASS, true, cl);
            Class<?> objectValueClass = Class.forName(NEW_OBJECT_VALUE_CLASS, true, cl);
            Class<?> iAriaObjectClass = Class.forName(NEW_IARIA_OBJECT_CLASS, true, cl);
            Class<?> javaObjectMirrorClass = Class.forName(NEW_JAVA_OBJECT_MIRROR_CLASS, true, cl);

            newCreateContext = ariaClass.getMethod("createContext");
            newEval = ariaClass.getMethod("eval", String.class, contextClass);
            newVariableKeyOf = variableKeyClass.getMethod("of", String.class);
            newGetGlobalStorage = contextClass.getMethod("getGlobalStorage");
            newGetGlobalVariable = globalStorageClass.getMethod("getGlobalVariable", variableKeyClass);
            // VariableReference.setValue(IValue) —— 官方 Overture 注入路径
            Class<?> variableReferenceClass = newGetGlobalVariable.getReturnType();
            newSetValue = variableReferenceClass.getMethod("setValue", iValueClass);
            // 活对象注入：new ObjectValue(new JavaObjectMirror(obj))
            newJavaObjectMirrorCtor = javaObjectMirrorClass.getConstructor(Object.class);
            newObjectValueCtor = objectValueClass.getConstructor(iAriaObjectClass);

            // 尝试获取版本（AriaEngine 可能暴露版本信息）
            version = resolveNewApiVersion(ariaClass);
            available = true;
            newApi = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine(
                "[Aria] 新版桥接初始化成功" + (version != null ? " (v" + version + ")" : "")
            );
            return true;
        } catch (ClassNotFoundException ignored) {
            // 新版 Aria 不在 classpath 中
        } catch (ReflectiveOperationException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().fine(
                "[Aria] 新版 API 反射失败，回退旧版: " + exception.getMessage()
            );
        }
        return false;
    }

    private @Nullable String resolveNewApiVersion(Class<?> ariaClass) {
        try {
            // Aria 本体无 getVersion，尝试从 AriaEngine 获取或返回 null
            Class<?> engineClass = Class.forName(
                "priv.seventeen.artist.aria.api.AriaEngine", true, ariaClass.getClassLoader()
            );
            // 检查是否有版本字段
            try {
                Field versionField = engineClass.getDeclaredField("VERSION");
                versionField.setAccessible(true);
                Object val = versionField.get(null);
                return val != null ? String.valueOf(val) : null;
            } catch (NoSuchFieldException ignored) {
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return "new";
    }

    // ── 旧版 Blink 初始化 ───────────────────────────────────────

    private void tryInitOldApi(ClassLoader cl) {
        try {
            Class<?> managerClass = Class.forName(OLD_MANAGER_CLASS, true, cl);
            oldManagerInstance = resolveKotlinObject(managerClass);
            if (oldManagerInstance == null) {
                return;
            }
            Method isAvailableMethod = managerClass.getMethod("isAvailable");
            Object availableFlag = isAvailableMethod.invoke(oldManagerInstance);
            if (!(availableFlag instanceof Boolean bool) || !bool) {
                oldManagerInstance = null;
                return;
            }
            oldEvalMethod = managerClass.getMethod("eval", String.class, Map.class);
            Method versionGetter = managerClass.getMethod("getVersion");
            Object versionValue = versionGetter.invoke(oldManagerInstance);
            version = versionValue == null ? null : String.valueOf(versionValue);
            available = true;
            newApi = false;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine(
                "[Aria] 旧版桥接初始化成功 (v" + (version == null ? "?" : version) + ")"
            );
        } catch (ReflectiveOperationException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                "[Aria] 桥接初始化失败: " + exception.getMessage()
            );
            reset();
        }
    }

    // ── AriaBridge 接口实现 ──────────────────────────────────────

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public @Nullable String version() {
        return version;
    }

    @Override
    public @Nullable Object eval(@NotNull String code, @NotNull Map<String, Object> bindings) {
        if (!available) {
            return null;
        }
        return newApi ? evalNewApi(code, bindings) : evalOldApi(code, bindings);
    }

    private @Nullable Object evalNewApi(@NotNull String code, @NotNull Map<String, Object> bindings) {
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
    public boolean evalBoolean(@NotNull String code, @NotNull Map<String, Object> bindings) {
        if (!available) {
            return false;
        }
        if (newApi) {
            return evalBooleanNewApi(code, bindings);
        }
        return AriaBridge.toBoolean(evalOldApi(code, bindings));
    }

    private boolean evalBooleanNewApi(@NotNull String code, @NotNull Map<String, Object> bindings) {
        try {
            Object context = newCreateContext.invoke(null);
            injectBindings(context, bindings);
            Object result = newEval.invoke(null, code, context);
            if (result == null) {
                return false;
            }
            // IValue<?>.booleanValue() 直接返回 boolean
            Method booleanValue = result.getClass().getMethod("booleanValue");
            Object bv = booleanValue.invoke(result);
            return bv instanceof Boolean b && b;
        } catch (ReflectiveOperationException exception) {
            logEvalFailure("evalBoolean", exception);
            return false;
        }
    }

    // 官方 Overture 注入路径：把活对象包装成 ObjectValue(JavaObjectMirror(obj))，
    // 写入全局变量，脚本内以裸名（如 player）访问并可反射调用其 Java 方法。
    private void injectBindings(Object context, Map<String, Object> bindings) throws ReflectiveOperationException {
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

    // 按 IValue 具体类型取原生 Java 值：NumberValue.jvmValue() 在 aria-1.0.1 返回 0.0，
    // 故数值走 numberValue()，字符串走 stringValue()，布尔走 booleanValue()，其余走 jvmValue()。
    private @Nullable Object unwrap(@Nullable Object result) throws ReflectiveOperationException {
        if (result == null) {
            return null;
        }
        String typeName = result.getClass().getSimpleName();
        switch (typeName) {
            case "NoneValue" -> {
                return null;
            }
            case "NumberValue" -> {
                double d = ((Number) result.getClass().getMethod("numberValue").invoke(result)).doubleValue();
                if (d == Math.rint(d) && !Double.isInfinite(d)) {
                    return (long) d;
                }
                return d;
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

    private void logEvalFailure(String tag, ReflectiveOperationException exception) {
        Throwable cause = exception.getCause();
        String msg = cause != null ? cause.getMessage() : exception.getMessage();
        xuanmo.arcartxsuite.module.AxsLog.logger().fine("[Aria] " + tag + " 失败: " + msg);
    }

    private @Nullable Object evalOldApi(@NotNull String code, @NotNull Map<String, Object> bindings) {
        if (oldEvalMethod == null || oldManagerInstance == null) {
            return null;
        }
        try {
            return oldEvalMethod.invoke(oldManagerInstance, code, bindings);
        } catch (ReflectiveOperationException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[Aria] eval 失败: " + exception.getMessage());
            return null;
        }
    }

    // ── 宿主发现 ────────────────────────────────────────────────

    private @Nullable ClassLoader resolveClassLoader() {
        for (String pluginName : DISCOVERY_PLUGINS) {
            Plugin candidate = Bukkit.getPluginManager().getPlugin(pluginName);
            if (candidate == null || !candidate.isEnabled()) {
                continue;
            }
            ClassLoader cl = candidate.getClass().getClassLoader();
            // 优先检测新版 Aria
            try {
                Class.forName(NEW_ARIA_CLASS, false, cl);
                return cl;
            } catch (ClassNotFoundException ignored) {
            }
            // 回退检测旧版 Blink
            try {
                Class.forName(OLD_MANAGER_CLASS, false, cl);
                return cl;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private static @Nullable Object resolveKotlinObject(Class<?> managerClass) throws ReflectiveOperationException {
        Field instanceField = managerClass.getField("INSTANCE");
        return instanceField.get(null);
    }

    private void reset() {
        available = false;
        version = null;
        newApi = false;
        newCreateContext = null;
        newEval = null;
        newVariableKeyOf = null;
        newGetGlobalStorage = null;
        newGetGlobalVariable = null;
        newSetValue = null;
        newJavaObjectMirrorCtor = null;
        newObjectValueCtor = null;
        oldManagerInstance = null;
        oldEvalMethod = null;
    }
}
