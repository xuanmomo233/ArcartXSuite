package xuanmo.arcartxsuite.fishing.model;

import org.jetbrains.annotations.NotNull;

/**
 * 饵料队列条目，表示玩家饵料队列中的一个槽位。
 * <p>
 * 关联模块：{@code fishing}。存储在 {@code axs_fishing_bait_queue} 表中。
 * 鱼咬钩时按 slot_index 升序消耗队列头部的饵料。
 *
 * @param playerUuid 玩家 UUID
 * @param slotIndex  槽位序号（从 0 开始，按升序消耗）
 * @param baitId     饵料定义 ID（对应 {@link BaitDefinition#id()}）
 * @param amount     数量
 */
public record BaitQueueEntry(
    @NotNull java.util.UUID playerUuid,
    int slotIndex,
    @NotNull String baitId,
    int amount
) {
}
