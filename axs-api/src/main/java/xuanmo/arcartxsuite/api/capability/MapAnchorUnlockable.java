package xuanmo.arcartxsuite.api.capability;

import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.bridge.ApiStability;

/**
 * 地图锚点解锁能力接口（公开 capability）。
 * <p>
 * 由 Map 模块实现。供其他模块/外部插件（任务系统、活动玩法、脚本等）以代码路径
 * 解锁锚点——与玩家在地图菜单手动解锁、靠近距离门槛、进服自动解锁并存。
 * <p>
 * 获取方式：
 * <ul>
 *   <li>外部插件：{@code AxsCapabilities.get(MapAnchorUnlockable.class)}</li>
 *   <li>AXS 模块：{@code getCapability(MapAnchorUnlockable.class)}</li>
 * </ul>
 * <p>
 * 语义：锚点一旦解锁持久化保存；解锁成功后若锚点配置了
 * {@code light-region}/{@code light-radius}，对应区域的点亮与地形推送自动生效。
 * <p>
 * 线程期望：可在任意线程调用；实现内部会把实际解锁切回主线程
 * （背包/经济/存档操作均在主线程完成），future 在主线程完成后兑现。
 */
@ApiStability.Stable
@PublicCapability
public interface MapAnchorUnlockable {

    /**
     * 免费强制解锁锚点（跳过付费与距离门槛，仍校验锚点存在性/已解锁态）。
     * <p>
     * 适用：任务完成奖励、活动发放、管理指令等"系统授予"场景。
     * 不消耗 {@code unlock-currencies}/{@code unlock-items}。
     *
     * @param player   目标玩家（须在线）
     * @param anchorId 锚点 id
     * @return 解锁结果 future：{@code true} 本次新解锁；{@code false} 已解锁/锚点不存在/玩家离线
     */
    @NotNull CompletableFuture<Boolean> unlock(@NotNull Player player, @NotNull String anchorId);

    /**
     * 走完整正常解锁流程（等价玩家在地图菜单点击"解锁"）。
     * <p>
     * 校验 permission、{@code unlock.radius} 三维距离门槛，并收取
     * {@code unlock-currencies}/{@code unlock-items}。失败时玩家会收到原因消息。
     *
     * @param player   目标玩家（须在线）
     * @param anchorId 锚点 id
     * @return 解锁结果 future：{@code true} 本次新解锁
     */
    @NotNull CompletableFuture<Boolean> tryUnlock(@NotNull Player player, @NotNull String anchorId);
}
