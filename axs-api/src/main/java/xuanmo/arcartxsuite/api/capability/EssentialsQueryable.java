package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Essentials 模块状态查询能力接口。
 * <p>
 * 由 Essentials 模块实现，供 Tab、Chat 等模块查询玩家 AFK/Vanish/Mute/Nick 状态。
 * <p>
 * 公开 capability：外部插件可经 {@code AxsCapabilities.get(EssentialsQueryable.class)} 获取。
 */
@PublicCapability
public interface EssentialsQueryable {

    /**
     * 查询玩家是否处于 AFK 状态。
     */
    boolean isAfk(@NotNull UUID playerUuid);

    /**
     * 查询玩家是否处于隐身状态。
     */
    boolean isVanished(@NotNull UUID playerUuid);

    /**
     * 查询玩家是否被禁言（Essentials 侧）。
     */
    boolean isMuted(@NotNull UUID playerUuid);

    /**
     * 获取玩家自定义昵称，未设置时返回 null。
     */
    @Nullable
    String getNickname(@NotNull UUID playerUuid);

    /**
     * 查询玩家是否处于飞行模式。
     */
    boolean isFlying(@NotNull UUID playerUuid);

    /**
     * 查询玩家是否处于无敌模式。
     */
    boolean isGodMode(@NotNull UUID playerUuid);

    /**
     * 查询指定世界的出生点（经 Essentials 的 SpawnBackend：Multiverse-Core 已加载
     * 时返回该世界的 MV spawn，否则返回本地存储的全局 spawn）。
     * <p>
     * 未设置或 Essentials 模块未启用时返回 null——不回退世界默认出生点，
     * 兜底策略由调用方决定。
     *
     * @param world 上下文世界（MV 后端按世界取 spawn；本地后端忽略该参数）
     */
    @Nullable
    Location getSpawn(@NotNull World world);
}
