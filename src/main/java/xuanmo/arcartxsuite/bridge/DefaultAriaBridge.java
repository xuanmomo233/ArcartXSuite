package xuanmo.arcartxsuite.bridge;

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
    private static final String NEW_NATIVE_CALLABLE_CLASS = "priv.seventeen.artist.aria.callable.NativeCallable";
    private static final String NEW_IVALUE_CLASS = "priv.seventeen.artist.aria.value.IValue";

    // ── 旧版 Blink API ──────────────────────────────────────────
    private static final String OLD_MANAGER_CLASS = "priv.seventeen.artist.blink.script.AriaScriptManager";

    // ── 宿主插件发现 ────────────────────────────────────────────
    private static final String[] DISCOVERY_PLUGINS = {
        "Aria", "Symphony", "Overture", "BlinkAriaHost"
    };

    private final JavaPlugin plugin;

    private boolean available;
    private String version;
    private boolean newApi;

    // 新版 API 反射缓存
    private Method newCreateContext;
    private Method newEval;
    private Method newVariableKeyOf;
    private Method newWrapObject;
    private Method newForceSetLocalValue;
    private Class<?> newVariableKeyClass;
    private Class<?> newIValueClass;

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
            newVariableKeyClass = Class.forName(NEW_VARIABLE_KEY_CLASS, true, cl);
            Class<?> nativeCallableClass = Class.forName(NEW_NATIVE_CALLABLE_CLASS, true, cl);
            newIValueClass = Class.forName(NEW_IVALUE_CLASS, true, cl);

            newCreateContext = ariaClass.getMethod("createContext");
            newEval = ariaClass.getMethod("eval", String.class, contextClass);
            newVariableKeyOf = newVariableKeyClass.getMethod("of", String.class);
            newWrapObject = nativeCallableClass.getMethod("wrapObject", Object.class);
            newForceSetLocalValue = contextClass.getMethod("forceSetLocalValue", newVariableKeyClass, newIValueClass);

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
            for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                Object key = newVariableKeyOf.invoke(null, entry.getKey());
                Object wrapped = newWrapObject.invoke(null, entry.getValue());
                newForceSetLocalValue.invoke(context, key, wrapped);
            }
            Object result = newEval.invoke(null, code, context);
            if (result == null) {
                return null;
            }
            // IValue<?>.jvmValue() 返回原生 Java 对象
            Method jvmValue = result.getClass().getMethod("jvmValue");
            return jvmValue.invoke(result);
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception.getCause();
            String msg = cause != null ? cause.getMessage() : exception.getMessage();
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[Aria] eval 失败: " + msg);
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
            for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                Object key = newVariableKeyOf.invoke(null, entry.getKey());
                Object wrapped = newWrapObject.invoke(null, entry.getValue());
                newForceSetLocalValue.invoke(context, key, wrapped);
            }
            Object result = newEval.invoke(null, code, context);
            if (result == null) {
                return false;
            }
            // IValue<?>.booleanValue() 直接返回 boolean
            Method booleanValue = result.getClass().getMethod("booleanValue");
            Object bv = booleanValue.invoke(result);
            return bv instanceof Boolean b && b;
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception.getCause();
            String msg = cause != null ? cause.getMessage() : exception.getMessage();
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[Aria] evalBoolean 失败: " + msg);
            return false;
        }
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
        newWrapObject = null;
        newForceSetLocalValue = null;
        newVariableKeyClass = null;
        newIValueClass = null;
        oldManagerInstance = null;
        oldEvalMethod = null;
    }
}
