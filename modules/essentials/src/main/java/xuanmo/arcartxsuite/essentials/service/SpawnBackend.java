package xuanmo.arcartxsuite.essentials.service;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 出生点后端抽象。
 * <p>
 * 安装 Multiverse-Core 时由 {@link MultiverseSpawnBackend} 接管
 * （出生点读写委托给 MV 的 worlds.yml，重生/首次进服由 MV 全权处理）；
 * 未安装时使用 {@link LocalSpawnBackend}（模块数据库存储，
 * 重生/首次进服由 {@link TeleportService} 自行接管）。
 * 后端选择在每次操作时动态解析，MV 后加载可无缝切换。
 */
public interface SpawnBackend {

    /**
     * 以指定位置设置出生点。
     *
     * @param loc 出生点位置
     * @return 设置成功返回 {@code true}
     */
    boolean setSpawn(@NotNull Location loc);

    /**
     * 查询出生点位置。
     *
     * @param context 上下文世界（按世界取出生点的后端使用，单点后端可忽略）
     * @return 出生点位置，未设置或查询失败时返回 {@code null}
     */
    @Nullable Location getSpawn(@NotNull World context);
}
