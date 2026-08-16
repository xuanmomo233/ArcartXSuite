package xuanmo.arcartxsuite.battlepass.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;

/**
 * 任务触发频率限制器。
 * <p>
 * 状态仅保存在内存中，不持久化；服务重启后冷却和自然日计数会清零。
 * 每个玩家和任务组合使用独立状态对象锁，保证并发事件下的检查与计数更新原子完成。
 */
public final class TaskTriggerLimiter {

    private final Map<TriggerKey, TriggerState> states = new ConcurrentHashMap<>();

    /**
     * 尝试计入一次任务触发。
     *
     * @param playerUuid 玩家 UUID
     * @param taskId 任务 ID
     * @param limits 任务触发限制
     * @return {@code true} 表示本次允许计入
     */
    public boolean tryAcquire(
        UUID playerUuid, String taskId, BattlePassTask.TriggerLimits limits
    ) {
        if (limits == null || limits.unlimited()) return true;
        TriggerKey key = new TriggerKey(playerUuid, taskId);
        TriggerState state = states.computeIfAbsent(key, ignored -> new TriggerState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            LocalDate today = LocalDate.now();
            if (!today.equals(state.date)) {
                state.date = today;
                state.count = 0;
            }
            if (limits.cooldownMs() > 0
                && state.lastAcceptedAt > 0
                && now - state.lastAcceptedAt < limits.cooldownMs()) {
                return false;
            }
            if (limits.maxPerDay() > 0 && state.count >= limits.maxPerDay()) {
                return false;
            }
            state.lastAcceptedAt = now;
            state.count++;
            return true;
        }
    }

    /**
     * 清理指定玩家的全部限流状态。
     *
     * @param playerUuid 玩家 UUID
     */
    public void clear(UUID playerUuid) {
        states.keySet().removeIf(key -> key.playerUuid().equals(playerUuid));
    }

    /**
     * 清空所有限流状态。
     */
    public void clear() {
        states.clear();
    }

    private record TriggerKey(UUID playerUuid, String taskId) {}

    private static final class TriggerState {
        private LocalDate date;
        private long lastAcceptedAt;
        private int count;
    }
}
