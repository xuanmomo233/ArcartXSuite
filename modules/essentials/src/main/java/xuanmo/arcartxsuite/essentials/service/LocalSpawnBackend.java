package xuanmo.arcartxsuite.essentials.service;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

/**
 * 本地出生点后端。
 * <p>
 * 未安装 Multiverse-Core 时使用：出生点持久化在模块数据库
 * {@code spawn} 表（id=1 单行，全服共用一个点），死亡重生与
 * 新玩家首次进服由 {@link TeleportService} 的事件监听接管。
 */
public final class LocalSpawnBackend implements SpawnBackend {

    private final EssentialsRepository repository;
    private final Logger logger;

    public LocalSpawnBackend(EssentialsRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    @Override
    public boolean setSpawn(@NotNull Location loc) {
        try {
            repository.setSpawn(loc);
            return true;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Essentials.setSpawn 失败", e);
            return false;
        }
    }

    @Override
    public @Nullable Location getSpawn(@NotNull World context) {
        try {
            return repository.getSpawn();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Essentials.getSpawn 失败", e);
            return null;
        }
    }
}
