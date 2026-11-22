package xuanmo.arcartxsuite.api.bridge;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * ArcartX Prop 桥接中的玩家句柄，用于操作额外客户端槽位与冷却。
 *
 * @since 1.2.0
 */
@ApiStability.Internal
public interface PropPlayerHandle {

    /** 读取指定额外槽位的物品 */
    @Nullable ItemStack getSlotItemStack(String slotId);

    /** 设置指定额外槽位的物品 */
    void setSlotItemStack(String slotId, ItemStack itemStack);

    /** 写入并确认服务端槽位的新内容。供执行消耗前使用。 */
    boolean trySetSlotItemStack(String slotId, ItemStack itemStack);

    /** 回推一个槽位的服务端快照，且不触发槽位更新脚本。 */
    void syncSlotToClient(String slotId);

    /** 仅客户端移除指定前缀的额外槽位物品 */
    void removeSlotItemStackOnlyClient(String slotPrefix, boolean recursive);

    /** 设置标签冷却（毫秒） */
    void setTagCooldown(String tag, long cooldownMillis);

    /** 获取标签冷却剩余时间（毫秒） */
    long getTagCooldown(String tag);

    /** 同步槽位缓存到客户端 */
    void syncSlotCacheToClient();
}
