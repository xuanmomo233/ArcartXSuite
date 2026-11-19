package xuanmo.arcartxsuite.market.auction;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.Plugin;

/** Resolve the public API through LuckPerms' own plugin loader. */
final class OfflineAuctionTax {
    private final Object userManager;
    private final Method getUser;
    private final Method loadUser;
    private final Method cachedData;
    private final Method permissionData;
    private final Method checkPermission;
    private final Method asBoolean;

    OfflineAuctionTax(Plugin plugin) throws ReflectiveOperationException {
        var loader = plugin.getClass().getClassLoader();
        var provider = loader.loadClass("net.luckperms.api.LuckPermsProvider");
        var manager = loader.loadClass("net.luckperms.api.model.user.UserManager");
        var user = loader.loadClass("net.luckperms.api.model.user.User");
        var data = loader.loadClass("net.luckperms.api.cacheddata.CachedDataManager");
        var permissions = loader.loadClass("net.luckperms.api.cacheddata.CachedPermissionData");
        userManager = loader.loadClass("net.luckperms.api.LuckPerms").getMethod("getUserManager")
            .invoke(provider.getMethod("get").invoke(null));
        getUser = manager.getMethod("getUser", UUID.class);
        loadUser = manager.getMethod("loadUser", UUID.class);
        cachedData = user.getMethod("getCachedData");
        permissionData = data.getMethod("getPermissionData");
        checkPermission = permissions.getMethod("checkPermission", String.class);
        asBoolean = loader.loadClass("net.luckperms.api.util.Tristate").getMethod("asBoolean");
    }

    double rate(UUID seller, Map<String, Double> discounts, double fallback) throws Exception {
        Object user = getUser.invoke(userManager, seller);
        if (user == null) {
            user = ((CompletionStage<?>) loadUser.invoke(userManager, seller)).toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
        if (user == null) return fallback;
        Object permissions = permissionData.invoke(cachedData.invoke(user));
        double rate = Double.POSITIVE_INFINITY;
        for (var entry : discounts.entrySet()) {
            if (Boolean.TRUE.equals(asBoolean.invoke(checkPermission.invoke(permissions, entry.getKey())))) {
                rate = Math.min(rate, Math.max(0, entry.getValue()));
            }
        }
        return Double.isFinite(rate) ? rate : fallback;
    }
}
