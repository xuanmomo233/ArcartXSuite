package xuanmo.arcartxsuite.fishing.minigame;

/**
 * 鱼的 AI 行为接口。
 * <p>
 * 每个 tick 调用一次，返回鱼在 [0, 1] 区间内的目标位置变化量（速度）。
 */
public interface FishBehavior {

    /**
     * 计算鱼在当前 tick 的速度（位置变化量）。
     *
     * @param currentPosition 鱼当前位置 [0, 1]
     * @param currentVelocity 鱼当前速度
     * @param tick            当前 tick 计数
     * @param difficulty      鱼的难度值 (1-100)
     * @return 新的速度值（位置变化量）
     */
    double tick(double currentPosition, double currentVelocity, long tick, int difficulty);
}
