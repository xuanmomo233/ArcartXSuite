package xuanmo.arcartxsuite.battlepass.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import xuanmo.arcartxsuite.battlepass.condition.TaskCondition;
import xuanmo.arcartxsuite.battlepass.increment.IncrementStrategy;

/**
 * 战令任务模板 record，定义一个任务的完整配置信息。
 * <p>
 * 包含任务的基本信息（名称、描述、分类、难度）、触发条件（事件主题、条件列表）、
 * 奖励计算（基础 XP、难度倍率）及进度增量策略。
 * <p>
 * 事件源支持双声明：{@code eventTopic}（ArcartX EventBus 主题）和 {@code bukkitEvent}（原版 Bukkit 事件）。
 * 引擎启动时优先检测 EventBus 发布模块是否存在，存在则用 EventBus，不存在则降级到原版 Bukkit 监听器，
 * 确保 battlepass 模块不依赖其他 ArcartX 模块也能正常工作。
 *
 * @param taskId              任务唯一标识
 * @param displayName         任务显示名称
 * @param description         任务描述
 * @param taskCategory        任务分类（DAILY/WEEKLY/SEASON）
 * @param difficulty          任务难度（EASY/NORMAL/HARD）
 * @param eventTopic          触发事件的主题（ArcartX EventBus，可为空）
 * @param bukkitEvent         原版 Bukkit 事件降级配置，可为 null
 * @param requiredCount       完成所需次数
 * @param baseXpReward        基础 XP 奖励
 * @param difficultyMultiplier 难度倍率
 * @param conditions          触发条件列表
 * @param incrementStrategy   进度增量策略
 * @param weight              任务分配权重
 */
public record BattlePassTask(
    String taskId,
    String displayName,
    String description,
    TaskCategory taskCategory,
    TaskDifficulty difficulty,
    String eventTopic,
    BukkitEventSource bukkitEvent,
    int requiredCount,
    int baseXpReward,
    float difficultyMultiplier,
    List<TaskCondition> conditions,
    IncrementStrategy incrementStrategy,
    int weight,
    TriggerLimits limits
) {

    public BattlePassTask {
        if (conditions == null) conditions = Collections.emptyList();
        if (incrementStrategy == null) incrementStrategy = new xuanmo.arcartxsuite.battlepass.increment.FixedIncrementStrategy(1);
        if (difficultyMultiplier <= 0) difficultyMultiplier = 1.0f;
        if (weight <= 0) weight = 1;
        if (limits == null) limits = TriggerLimits.UNLIMITED;
    }

    /**
     * 任务触发频率限制。状态由服务内存中的限流器维护，不持久化；服务重启后状态清零。
     *
     * @param cooldownMs 同一玩家同一任务两次计入之间的最小间隔，0 表示不限制
     * @param maxPerDay 单个自然日最多计入次数，0 表示不限制
     */
    public record TriggerLimits(long cooldownMs, int maxPerDay) {
        public static final TriggerLimits UNLIMITED = new TriggerLimits(0L, 0);

        public TriggerLimits {
            if (cooldownMs < 0) cooldownMs = 0;
            if (maxPerDay < 0) maxPerDay = 0;
        }

        /**
         * 判断是否未配置任何限制。
         *
         * @return {@code true} 表示不限制
         */
        public boolean unlimited() {
            return cooldownMs == 0 && maxPerDay == 0;
        }
    }

    /**
     * 原版 Bukkit 事件降级配置，声明事件类名和 payload 提取规则。
     *
     * @param eventClasses  Bukkit 事件全类名列表（如 {@code ["org.bukkit.event.entity.EntityDeathEvent"]}），
     *                     支持多个事件类，任一触发即计入任务进度
     * @param payloadFields payload 提取规则：key = payload 字段名，value = 表达式
     */
    public record BukkitEventSource(
        List<String> eventClasses,
        Map<String, String> payloadFields
    ) {
        public BukkitEventSource {
            if (eventClasses == null) eventClasses = Collections.emptyList();
            if (payloadFields == null) payloadFields = Collections.emptyMap();
        }

        /**
         * 兼容旧 API：返回第一个事件类名。
         *
         * @return 第一个事件类名，列表为空时返回空字符串
         */
        public String eventClass() {
            return eventClasses.isEmpty() ? "" : eventClasses.get(0);
        }
    }

    /**
     * 计算任务的总 XP 奖励（基础奖励 × 难度倍率）。
     *
     * @return 总 XP 奖励值
     */
    public int totalXpReward() {
        return Math.round(baseXpReward * difficultyMultiplier);
    }

    /**
     * 判断玩家与事件载荷是否满足此任务的全部触发条件。
     *
     * @param player  触发事件的玩家
     * @param payload 事件载荷
     * @return {@code true} 表示所有条件均满足
     */
    public boolean matchesConditions(org.bukkit.entity.Player player, java.util.Map<String, String> payload) {
        for (TaskCondition condition : conditions) {
            if (!condition.test(player, payload)) return false;
        }
        return true;
    }

    /**
     * 委托增量策略计算本次事件的进度增量。
     *
     * @param player  触发事件的玩家
     * @param payload 事件载荷
     * @return 进度增量值
     */
    public int calculateIncrement(org.bukkit.entity.Player player, java.util.Map<String, String> payload) {
        return incrementStrategy.calculateIncrement(player, payload);
    }

    /**
     * 任务分类枚举（每日 / 每周 / 赛季）。
     */
    public enum TaskCategory {
        DAILY, WEEKLY, SEASON
    }

    /**
     * 任务难度枚举，每个难度对应一个 XP 倍率。
     */
    public enum TaskDifficulty {
        EASY(1.0f),
        NORMAL(1.2f),
        HARD(1.5f);

        private final float multiplier;

        TaskDifficulty(float multiplier) {
            this.multiplier = multiplier;
        }

        public float multiplier() {
            return multiplier;
        }
    }
}
