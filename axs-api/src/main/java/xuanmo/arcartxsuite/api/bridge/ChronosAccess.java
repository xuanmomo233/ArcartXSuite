package xuanmo.arcartxsuite.api.bridge;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/** Shared access to the optional Chronos action and execution API through its plugin class loader. */
@ApiStability.Internal
public final class ChronosAccess {
    private static final String API_PACKAGE = "priv.seventeen.artist.arcartx.chronos.api.";
    private static final String NAME = "Chronos";
    private static final Set<String> REQUIRED_CAPABILITIES = Set.of("chronos:action-instance/1", "chronos:execute-loop/1");

    public enum Status { MISSING, DISABLED, API_UNAVAILABLE, UNSUPPORTED, READY }

    public record Probe(Status status, String detail, @Nullable ChronosAccess access) {}

    public record Execution(Player player, @Nullable UUID actionId, UUID stateInstanceId, String stateId,
                            String executionId, int index, Map<String, String> args) {}

    private record Methods(MethodHandle instance, MethodHandle capabilities, MethodHandle state, MethodHandle group,
                           MethodHandle controller, MethodHandle controlled) {
        Methods(Class<?> type, MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            // Resolve only the API signatures we use. Other methods can expose optional Aria/SPI types.
            this(lookup.findStatic(type, "getInstanceAPI", methodType(type)),
                lookup.findVirtual(type, "getCapabilities", methodType(Set.class)),
                lookup.findVirtual(type, "getPlayerStateId", methodType(String.class, Player.class)),
                lookup.findVirtual(type, "getPlayerStateGroupId", methodType(String.class, Player.class)),
                lookup.findVirtual(type, "getPlayerControllerId", methodType(String.class, Player.class)),
                lookup.findVirtual(type, "tryEnterControlledState",
                    methodType(void.class, Player.class, String.class, double.class)));
        }
    }

    private static final ClassValue<Methods> METHODS = new ClassValue<>() {
        @Override
        protected Methods computeValue(Class<?> type) {
            try {
                return new Methods(type, MethodHandles.publicLookup());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    private final Plugin owner;
    private final Methods methods;
    private final Object api;

    private ChronosAccess(Plugin owner, Methods methods, Object api) {
        this.owner = owner;
        this.methods = methods;
        this.api = api;
    }

    public static boolean isChronos(Plugin plugin) {
        return NAME.equals(plugin.getName());
    }

    public static Probe probe() {
        if (Bukkit.getServer() == null) {
            return new Probe(Status.MISSING, "", null);
        }
        var manager = Bukkit.getPluginManager();
        Plugin owner = manager.getPlugin(NAME);
        if (owner == null) {
            return new Probe(Status.MISSING, "", null);
        }
        String version = owner.getDescription().getVersion();
        if (!owner.isEnabled()) {
            return new Probe(Status.DISABLED, version, null);
        }
        try {
            Class<?> type = Class.forName(API_PACKAGE + "ChronosAPI", false, owner.getClass().getClassLoader());
            Methods methods = METHODS.get(type);
            Object api = invoke(methods.instance());
            if (!type.isInstance(api)) return new Probe(Status.API_UNAVAILABLE, version, null);
            if (!(invoke(methods.capabilities(), api) instanceof Set<?> capabilities)
                    || !capabilities.containsAll(REQUIRED_CAPABILITIES)) {
                return new Probe(Status.UNSUPPORTED, version, null);
            }
            return new Probe(Status.READY, version, new ChronosAccess(owner, methods, api));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof NoSuchMethodException || cause instanceof NoSuchMethodError) {
                return new Probe(Status.UNSUPPORTED, version, null);
            }
            return new Probe(Status.API_UNAVAILABLE, version + " (" + cause.getClass().getSimpleName() + ")", null);
        }
    }

    public boolean enabled() {
        return owner.isEnabled();
    }

    public Class<? extends Event> eventType(String simpleName) throws ClassNotFoundException {
        return Class.forName(API_PACKAGE + "event." + simpleName, false, owner.getClass().getClassLoader())
            .asSubclass(Event.class);
    }

    /** Registers a synchronous execution handler. False stops the current execution loop. */
    @SuppressWarnings("unchecked")
    public AutoCloseable registerExecutionHandler(Plugin plugin, String id, Predicate<Execution> callback)
            throws ReflectiveOperationException {
        ClassLoader loader = owner.getClass().getClassLoader();
        Class<?> handler = Class.forName(API_PACKAGE + "execution.ExecutionHandler", false, loader);
        Class<?> result = Class.forName(API_PACKAGE + "execution.ExecutionResult", false, loader);
        Class<?> registration = Class.forName(API_PACKAGE + "common.Registration", false, loader);
        Class<?> context = Class.forName(API_PACKAGE + "execution.ExecutionContext", false, loader);
        var player = context.getMethod("player");
        var action = context.getMethod("actionInstanceId");
        var stateInstance = context.getMethod("stateInstanceId");
        var state = context.getMethod("stateId");
        var execution = context.getMethod("executionId");
        var index = context.getMethod("index");
        var args = context.getMethod("args");
        Object continueLoop = result.getField("CONTINUE").get(null);
        Object cancelLoop = result.getField("CANCEL_THIS_LOOP").get(null);
        Object adapter = Proxy.newProxyInstance(loader, new Class<?>[]{handler}, (proxy, method, callArgs) ->
            switch (method.getName()) {
                case "execute" -> {
                    if (!enabled()) yield cancelLoop;
                    Object value = callArgs[0];
                    yield callback.test(new Execution((Player) player.invoke(value), (UUID) action.invoke(value),
                        (UUID) stateInstance.invoke(value), (String) state.invoke(value), (String) execution.invoke(value),
                        (Integer) index.invoke(value), (Map<String, String>) args.invoke(value))) ? continueLoop : cancelLoop;
                }
                case "equals" -> proxy == callArgs[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> id;
                default -> throw new UnsupportedOperationException(method.toString());
            });
        var register = MethodHandles.publicLookup().findVirtual(methods.instance().type().returnType(),
            "registerExecutionHandler", methodType(registration, Plugin.class, String.class, handler));
        AutoCloseable handle = (AutoCloseable) invoke(register, api, plugin, id, adapter);
        if (handle == null) throw new IllegalStateException("Chronos 未返回注册句柄");
        try {
            if (Boolean.TRUE.equals(registration.getMethod("isActive").invoke(handle))) return handle;
            throw new IllegalStateException("Chronos 执行处理器未启用: " + id);
        } catch (ReflectiveOperationException | RuntimeException e) {
            try {
                handle.close();
            } catch (Exception closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }

    public @Nullable String stateId(Player player) {
        return read(methods.state(), player);
    }

    public @Nullable String stateGroupId(Player player) {
        return read(methods.group(), player);
    }

    public @Nullable String controllerId(Player player) {
        return read(methods.controller(), player);
    }

    public void enterControlledState(Player player, String stateId, long durationMs)
        throws ReflectiveOperationException {
        if (enabled()) {
            invoke(methods.controlled(), api, player, stateId, (double) durationMs);
        }
    }

    private @Nullable String read(MethodHandle method, Player player) {
        try {
            return enabled() ? (String) invoke(method, api, player) : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Object invoke(MethodHandle method, Object... arguments) throws ReflectiveOperationException {
        try {
            return method.invokeWithArguments(arguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new ReflectiveOperationException(e);
        }
    }
}
