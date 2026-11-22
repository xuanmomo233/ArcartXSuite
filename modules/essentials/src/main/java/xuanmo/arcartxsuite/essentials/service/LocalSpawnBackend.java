package xuanmo.arcartxsuite.essentials.service;

import java.util.concurrent.CompletionStage;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

/**
 * 本地出生点后端。主动查询读取最新数据，同步重生事件只读已确认的快照。
 * <p>
 * 未安装 Multiverse-Core 时使用：出生点持久化在模块数据库
 * {@code spawn} 表（id=1 单行，全服共用一个点），死亡重生与
 * 新玩家首次进服由 {@link TeleportService} 的事件监听接管。
 */
public final class LocalSpawnBackend implements SpawnBackend {

    private final EssentialsRepository repository;
    private final EssentialsWork work;
    private volatile EssentialsRepository.Position snapshot;
    private boolean refreshing;
    private long lastRefresh;

    public LocalSpawnBackend(EssentialsRepository repository, EssentialsWork work) {
        this.repository = repository;
        this.work = work;
    }

    @Override
    public CompletionStage<Boolean> setSpawn(@NotNull Location loc) {
        var position = TeleportService.capture(loc);
        return work.submit(() -> {
            repository.setSpawn(position);
            snapshot = position;
            return true;
        });
    }

    @Override
    public CompletionStage<Location> getSpawn(@NotNull World context) {
        return work.read(() -> {
            var position = repository.getSpawn();
            snapshot = position;
            return position;
        }).thenCompose(position -> work.main(() -> TeleportService.resolve(position)));
    }

    /** Main-thread event view; never performs SQL or waits for the worker. */
    public @Nullable Location snapshot() { return TeleportService.resolve(snapshot); }

    /** Existing main-thread pulse requests a refresh while players are online. Outages keep at most one refresh queued. */
    public synchronized void refreshIfDue() {
        long now = System.nanoTime();
        if (refreshing || (lastRefresh != 0 && now - lastRefresh < 5_000_000_000L) || work.isClosed()) return;
        refreshing = true;
        lastRefresh = now;
        work.read(() -> {
            snapshot = repository.getSpawn();
            return null;
        }).whenComplete((unused, failure) -> { synchronized (this) { refreshing = false; } });
    }
}
