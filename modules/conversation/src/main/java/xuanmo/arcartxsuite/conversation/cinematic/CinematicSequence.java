package xuanmo.arcartxsuite.conversation.cinematic;

import java.util.List;

/**
 * 电影序列定义。
 * <p>
 * 一个序列由有序的动作列表组成，按顺序执行。
 */
public record CinematicSequence(
    String id,
    String name,
    List<CinematicAction> actions
) {

    public int actionCount() {
        return actions == null ? 0 : actions.size();
    }

    /**
     * 计算序列总时长（毫秒），包括所有动作的 delayAfterMs 之和。
     * 注意：camera_path 等异步动作的实际完成时间可能更长，
     * 此值仅用于预估。
     */
    public long estimatedDurationMs() {
        if (actions == null || actions.isEmpty()) return 0L;
        long total = 0L;
        for (CinematicAction action : actions) {
            total += action.delayAfterMs();
        }
        return total;
    }
}
