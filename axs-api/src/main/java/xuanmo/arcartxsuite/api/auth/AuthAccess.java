package xuanmo.arcartxsuite.api.auth;

import java.util.UUID;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;

/** Installed by the host before modules load; absence fails closed, including during reload. */
public final class AuthAccess {
    private static volatile Supplier<AuthRuntimeCapability> runtime = () -> null;
    private AuthAccess() {}
    public static void install(Supplier<AuthRuntimeCapability> provider) { runtime = java.util.Objects.requireNonNull(provider); }
    public static AuthSnapshot snapshot(UUID playerId) {
        var service = runtime.get();
        return service == null ? AuthSnapshot.absent(playerId) : service.snapshot(playerId);
    }
    public static boolean admitted(UUID playerId) { return snapshot(playerId).admitted(); }
    public static long eligibleNanos(UUID playerId) {
        var service = runtime.get();
        return service == null ? 0 : service.eligibleNanos(playerId);
    }
}
