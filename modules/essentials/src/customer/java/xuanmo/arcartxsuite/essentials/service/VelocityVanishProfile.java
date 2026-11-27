package xuanmo.arcartxsuite.essentials.service;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import xuanmo.arcartxsuite.api.crossserver.ProxyTabVisibility;

/** Reads the per-connection handoff from Paper's authenticated modern forwarding profile. */
final class VelocityVanishProfile {
    private static final int MAX_PENDING = 4096;
    private final Method getProfile;
    private final Method removeProperty;
    private final BooleanSupplier suiteProxyAllowed;
    private final Map<UUID, InetAddress> pending = new HashMap<>();

    VelocityVanishProfile(Method getProfile, Method removeProperty, BooleanSupplier suiteProxyAllowed) {
        this.getProfile = getProfile;
        this.removeProperty = removeProperty;
        this.suiteProxyAllowed = suiteProxyAllowed;
    }

    static VelocityVanishProfile detect(Logger logger, BooleanSupplier suiteProxyAllowed) {
        Class<?> configuration;
        try {
            configuration = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
        } catch (ClassNotFoundException unsupportedServer) {
            return null;
        }
        try {
            Object global = configuration.getMethod("get").invoke(null);
            Object proxies = configuration.getField("proxies").get(global);
            Object velocity = proxies.getClass().getField("velocity").get(proxies);
            if (!velocity.getClass().getField("enabled").getBoolean(velocity)) return null;

            // Keep Paper-only types out of the Spigot API linkage.
            Method getter = AsyncPlayerPreLoginEvent.class.getMethod("getPlayerProfile");
            Class<?> profile = getter.getReturnType();
            return new VelocityVanishProfile(getter, profile.getMethod("removeProperty", String.class), suiteProxyAllowed);
        } catch (ReflectiveOperationException failure) {
            logger.log(Level.WARNING, "无法初始化 Velocity 隐身交接。", failure);
            return null;
        }
    }

    synchronized boolean capture(AsyncPlayerPreLoginEvent event) throws ReflectiveOperationException {
        // Online-mode profile completion can replace properties after this event. Strip the marker here
        // so it cannot enter the profile cache or appear in the native player list.
        Object profile = getProfile.invoke(event);
        boolean hidden = Boolean.TRUE.equals(removeProperty.invoke(profile, ProxyTabVisibility.VANISH_PROFILE_PROPERTY));
        UUID id = event.getUniqueId();
        pending.remove(id);
        if (!hidden || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED
                || !suiteProxyAllowed.getAsBoolean()) return true;
        if (pending.size() >= MAX_PENDING) return false;
        pending.put(id, event.getAddress());
        return true;
    }

    synchronized InetAddress take(UUID id) {
        InetAddress address = pending.remove(id);
        return address != null && suiteProxyAllowed.getAsBoolean() ? address : null;
    }
}
