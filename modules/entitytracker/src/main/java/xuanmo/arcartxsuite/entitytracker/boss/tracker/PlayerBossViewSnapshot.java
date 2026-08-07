package xuanmo.arcartxsuite.entitytracker.boss.tracker;

import java.util.List;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossSortMode;

/**
 * 玩家 Boss 视图快照，属于 entitytracker 模块的 boss 追踪子系统。
 * <p>
 * 持有玩家可见的 Boss 数量统计、排序模式和可见槽位列表，
 * 是占位符扩展和管理命令获取玩家视角 Boss 数据的入口。
 *
 * @param bossCount       当前可见 Boss 数量
 * @param totalBossCount  全局 Boss 总数
 * @param maxVisibleBars  最大可见条数
 * @param sortMode        排序模式
 * @param visibleSlots    可见槽位列表
 */
public record PlayerBossViewSnapshot(
    int bossCount,
    long totalBossCount,
    int maxVisibleBars,
    BossSortMode sortMode,
    List<BossViewSlot> visibleSlots
) {

    /**
     * 返回空的玩家视图快照。
     *
     * @param maxVisibleBars 最大可见条数
     * @param sortMode       排序模式
     * @return 空快照
     */
    public static PlayerBossViewSnapshot empty(int maxVisibleBars, BossSortMode sortMode) {
        return new PlayerBossViewSnapshot(0, 0L, Math.max(1, maxVisibleBars), sortMode, List.of());
    }

    /**
     * 按槽位索引（从 1 开始）获取视图槽位。
     *
     * @param slotIndex 槽位索引
     * @return 对应槽位，越界时返回空槽位
     */
    public BossViewSlot slot(int slotIndex) {
        int zeroBased = slotIndex - 1;
        if (zeroBased < 0 || zeroBased >= visibleSlots.size()) {
            return BossViewSlot.empty();
        }
        return visibleSlots.get(zeroBased);
    }
}

