package xuanmo.arcartxsuite.regions.service;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** 将离线资料和文件读取放到 Bukkit 的后台线程，并在主线程返回结果。 */
public final class RegionProfiles {
    private RegionProfiles() {}

    public static CompletableFuture<UUID> resolve(Plugin plugin, String name) {
        try {
            return CompletableFuture.completedFuture(UUID.fromString(name));
        } catch (IllegalArgumentException ignored) {
            if (!name.matches("[A-Za-z0-9_]{1,16}")) {
                throw new IllegalArgumentException("请输入有效的玩家名或 UUID。");
            }
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) return CompletableFuture.completedFuture(online.getUniqueId());
            return background(plugin, () -> Bukkit.getOfflinePlayer(name).getUniqueId());
        }
    }

    public static String offlineName(UUID id) {
        return Bukkit.getOfflinePlayer(id).getName();
    }

    public static <T> CompletableFuture<T> background(Plugin plugin, Callable<T> work) {
        var future = new CompletableFuture<T>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                T result = work.call();
                Bukkit.getScheduler().runTask(plugin, () -> future.complete(result));
            } catch (Exception error) {
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, () -> future.completeExceptionally(error));
                }
            }
        });
        return future;
    }
}
