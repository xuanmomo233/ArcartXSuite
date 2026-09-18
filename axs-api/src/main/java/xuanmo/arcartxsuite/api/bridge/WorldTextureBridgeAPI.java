package xuanmo.arcartxsuite.api.bridge;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ArcartX WorldTexture 文字贴图特效桥接。
 * <p>
 * 在实体上方或世界坐标处渲染客户端自定义文字贴图。
 *
 * @since 1.2.0
 */
@ApiStability.Internal
public interface WorldTextureBridgeAPI {

    /** 初始化桥接 */
    boolean initialize();

    /** 桥接是否可用 */
    boolean isAvailable();

    /**
     * 在实体上方渲染文字贴图。
     */
    void spawnOnEntity(@NotNull Entity entity, @NotNull String id, @NotNull String texture,
                       double width, double height, double offsetY, boolean billboard);

    /**
     * 在世界坐标处渲染文字贴图。
     */
    void spawnAtLocation(@NotNull World world, @NotNull Location location, @NotNull String id,
                         @NotNull String texture, double width, double height);

    /**
     * 移除实体附着的贴图特效。
     */
    void removeFromEntity(@NotNull Entity entity, @NotNull String id);

    /**
     * 移除世界坐标上的贴图特效。
     */
    void removeFromWorld(@NotNull World world, @NotNull String id, @NotNull Location location);

    /**
     * 仅向指定玩家渲染一张世界贴图（私有贴图，其他玩家不可见）。
     * <p>
     * 与 {@link #spawnAtLocation} 的广播语义不同，本方法走 ArcartX 单玩家
     * {@code WORLD_TEXTURE} 包，用于路径导航提示等仅本人可见的贴图。
     * 同一 {@code id} 重复调用按客户端覆盖语义处理（可用于移动/更新贴图）。
     *
     * @param player   目标玩家
     * @param id       贴图标识（{@link #removeForPlayer} 按此 id 移除）
     * @param location 贴图锚点位置
     * @param texture  贴图资源 ID
     * @param width    渲染宽度
     * @param height   渲染高度
     * @param yaw      朝向角（度）
     * @param pitch    俯仰角（度；贴地渲染通常为 90）
     * @param facing   是否始终面向玩家（billboard）
     * @param glowing  是否泛光
     * @param lifeTime 存活时长（客户端单位；-1 = 永续直到显式移除）
     * @param pulse    可选缩放脉冲动画（{@code null} = 无动画）
     * @return 发送成功返回 {@code true}
     * @since 1.7.0
     */
    boolean spawnForPlayer(@NotNull Player player, @NotNull String id, @NotNull Location location,
                           @NotNull String texture, double width, double height,
                           float yaw, float pitch, boolean facing, boolean glowing,
                           int lifeTime, @Nullable WorldTexturePulse pulse);

    /**
     * 移除指定玩家的私有世界贴图（走 ArcartX {@code WORLD_TEXTURE_REMOVE} 包）。
     *
     * @param player 目标玩家
     * @param id     贴图标识
     * @return 发送成功返回 {@code true}
     * @since 1.7.0
     */
    boolean removeForPlayer(@NotNull Player player, @NotNull String id);
}
