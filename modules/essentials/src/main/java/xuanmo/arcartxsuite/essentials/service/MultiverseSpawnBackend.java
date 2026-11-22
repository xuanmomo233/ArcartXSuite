package xuanmo.arcartxsuite.essentials.service;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Multiverse-Core 出生点后端。
 * <p>
 * 安装 Multiverse-Core 时使用：出生点读写全部委托 MV API
 * （{@code MultiverseCoreApi.get().getWorldManager()}），实际持久化到
 * MV 的 {@code worlds.yml} 各世界 {@code spawn-location} 节点；
 * 死亡重生与新玩家进服由 MV 自身的监听器全权处理，Essentials 不干预。
 * <p>
 * 所有 MV 类型仅在方法体内引用（延迟解析），本类只有在
 * {@link #isLoaded()} 为真时才应被实例化。
 */
public final class MultiverseSpawnBackend implements SpawnBackend {

    private final Logger logger;

    public MultiverseSpawnBackend(Logger logger) {
        this.logger = logger;
    }

    /**
     * Multiverse-Core API 是否已就绪。
     * <p>
     * 调用方必须先确认服务端安装了 Multiverse-Core 再调用此方法，
     * 否则类解析会抛出 {@link NoClassDefFoundError}。
     *
     * @return MV 服务定位器初始化完成返回 {@code true}
     */
    public static boolean isLoaded() {
        try {
            return org.mvplugins.multiverse.core.MultiverseCoreApi.isLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public java.util.concurrent.CompletionStage<Boolean> setSpawn(@NotNull Location loc) {
        return java.util.concurrent.CompletableFuture.completedFuture(setSpawnNow(loc));
    }

    private boolean setSpawnNow(Location loc) {
        try {
            org.mvplugins.multiverse.core.world.WorldManager worldManager =
                org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager();
            org.mvplugins.multiverse.external.vavr.control.Option<org.mvplugins.multiverse.core.world.LoadedMultiverseWorld> mvWorld =
                worldManager.getLoadedWorld(loc.getWorld());
            if (mvWorld == null || mvWorld.isEmpty()) {
                return false;
            }
            org.mvplugins.multiverse.external.vavr.control.Try<Void> result =
                mvWorld.get().setSpawnLocation(loc);
            if (result.isSuccess()) {
                worldManager.saveWorldsConfig();
                return true;
            }
            logger.warning("Essentials.setSpawn 写入 Multiverse 失败: " + result.getCause());
            return false;
        } catch (Throwable t) {
            logger.warning("Essentials.setSpawn 调用 Multiverse API 失败: " + t.getMessage());
            return false;
        }
    }

    @Override
    public java.util.concurrent.CompletionStage<Location> getSpawn(@NotNull World context) {
        return java.util.concurrent.CompletableFuture.completedFuture(getSpawnNow(context));
    }

    private @Nullable Location getSpawnNow(World context) {
        try {
            org.mvplugins.multiverse.external.vavr.control.Option<org.mvplugins.multiverse.core.world.LoadedMultiverseWorld> mvWorld =
                org.mvplugins.multiverse.core.MultiverseCoreApi.get().getWorldManager().getLoadedWorld(context);
            if (mvWorld == null || mvWorld.isEmpty()) {
                return null;
            }
            return mvWorld.get().getSpawnLocation();
        } catch (Throwable t) {
            logger.warning("Essentials.getSpawn 调用 Multiverse API 失败: " + t.getMessage());
            return null;
        }
    }
}
