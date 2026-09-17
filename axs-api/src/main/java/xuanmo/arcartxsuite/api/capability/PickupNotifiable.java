package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 拾取通知能力接口（公开 capability）。
 * <p>
 * 由 Pickup 模块（通知模式）实现，供 Warehouse 等模块查询
 * 某玩家是否已有 HUD 拾取通知，以避免重复的聊天栏提示。
 * <p>
 * 获取方式：
 * <ul>
 *   <li>外部插件：{@code AxsCapabilities.get(PickupNotifiable.class)}</li>
 *   <li>AXS 模块：{@code getCapability(PickupNotifiable.class)}</li>
 * </ul>
 * <p>
 * 注意：本能力仅在 Pickup 模块以 <b>notification（通知）模式</b>运行时注册；
 * scanner（扫描面板）模式下无 HUD 可推送，{@code get()} 返回 {@code null}。
 * <p>
 * 线程期望：请在 Bukkit 主线程调用（实现内部按 tick 调度发包）。
 */
@PublicCapability
public interface PickupNotifiable {

    /**
     * 查询该玩家的拾取 HUD 通知是否处于活跃状态。
     *
     * @param playerId 玩家 UUID
     * @return {@code true} 表示通知模式已启用且该玩家未关闭
     */
    boolean isNotificationActive(UUID playerId);

    /**
     * 主动请求为该玩家推送一条拾取通知。
     * <p>
     * 适用于物品并未真正进入背包、而是被其他模块直接接管的场景：
     * 例如 Warehouse 自动入库会取消 {@code EntityPickupItemEvent}，
     * 导致本模块的事件监听器（{@code ignoreCancelled = true}）无法收到通知，
     * 此时需由 Warehouse 显式调用本方法补发 HUD 提示。
     * <p>
     * 调用成功通过前置校验（模块激活、玩家在线未关闭通知、数量有效）后，
     * 会同步发布 EventBus 主题 {@code axs.pickup.item_pickup}
     * （payload 含 {@code material}/{@code amount}），与监听器路径语义一致：
     * 每次补发即一次拾取。
     *
     * @param player    目标玩家
     * @param itemStack 物品原型（仅用于读取显示信息，实现方不得修改）
     * @param amount    实际入库/获得的数量
     * @return {@code true} 表示已成功排入推送流程
     */
    default boolean notifyPickup(@NotNull Player player, @NotNull ItemStack itemStack, int amount) {
        return false;
    }
}
