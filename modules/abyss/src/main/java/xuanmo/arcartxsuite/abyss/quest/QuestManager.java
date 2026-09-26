package xuanmo.arcartxsuite.abyss.quest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.script.ScriptContext;
import xuanmo.arcartxsuite.abyss.script.ScriptRunner;
import xuanmo.arcartxsuite.abyss.template.QuestConfig;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;

/**
 * 任务管理器：副本任务的接取/提交状态机（QueDP quest 对齐）。
 * <p>
 * 状态存于实例变量（quest.&lt;id&gt; = ACCEPTED/COMPLETED），玩家级状态不区分成员——
 * 副本内任务为全队共享。接取/提交脚本经 {@link ScriptRunner} 执行，
 * 提交条件走宿主 {@link ScriptConditionEvaluator}。
 */
public final class QuestManager {

    public enum QuestStatus { NONE, ACCEPTED, COMPLETED }

    private final ScriptConditionEvaluator conditionEvaluator;

    public QuestManager(ScriptConditionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    /** 任务当前状态。 */
    public QuestStatus status(AbyssInstance instance, String questId) {
        Object v = instance.getVariables().get(questKey(questId));
        if (v instanceof QuestStatus qs) return qs;
        if (v instanceof String s) {
            try { return QuestStatus.valueOf(s); } catch (IllegalArgumentException ignored) { }
        }
        return QuestStatus.NONE;
    }

    /**
     * 接取任务：发送接受文本 + 跑接受脚本 + 标记 ACCEPTED。
     *
     * @return false=任务不存在/已接取/已完成
     */
    public boolean accept(AbyssInstance instance, Player trigger,
                          ScriptRunner runner, String questId) {
        QuestConfig.Quest q = find(instance, questId);
        if (q == null || status(instance, questId) != QuestStatus.NONE) return false;
        instance.getVariables().put(questKey(questId), QuestStatus.ACCEPTED);
        ScriptContext ctx = ScriptContext.of(instance, trigger);
        if (!q.acceptText().isEmpty()) {
            trigger.sendMessage(color(ctx.replace(q.acceptText())));
        }
        if (!q.acceptScript().isEmpty()) {
            runner.run("quest_accept_" + questId, q.acceptScript(), ctx, instance);
        }
        return true;
    }

    /**
     * 提交任务：条件校验 → 提交文本 + 提交脚本 + 标记 COMPLETED。
     *
     * @return SubmitResult
     */
    public SubmitResult submit(AbyssInstance instance, Player trigger,
                               ScriptRunner runner, String questId) {
        QuestConfig.Quest q = find(instance, questId);
        if (q == null) return SubmitResult.NOT_FOUND;
        if (status(instance, questId) == QuestStatus.COMPLETED) return SubmitResult.ALREADY_DONE;
        if (status(instance, questId) != QuestStatus.ACCEPTED) return SubmitResult.NOT_ACCEPTED;
        // 提交条件（空条件=直接可交）
        if (!q.submitConditions().isEmpty()) {
            ScriptCondition failed = conditionEvaluator.firstFailed(trigger, q.submitConditions());
            if (failed != null) return SubmitResult.CONDITION_FAILED;
        }
        instance.getVariables().put(questKey(questId), QuestStatus.COMPLETED);
        ScriptContext ctx = ScriptContext.of(instance, trigger);
        if (!q.submitText().isEmpty()) {
            trigger.sendMessage(color(ctx.replace(q.submitText())));
        }
        if (!q.submitScript().isEmpty()) {
            runner.run("quest_submit_" + questId, q.submitScript(), ctx, instance);
        }
        return SubmitResult.OK;
    }

    public enum SubmitResult {
        OK, NOT_FOUND, NOT_ACCEPTED, CONDITION_FAILED, ALREADY_DONE
    }

    /** 实例内全部任务 id → 状态（UI/调试展示用）。 */
    public Map<String, QuestStatus> statusMap(AbyssInstance instance) {
        Map<String, QuestStatus> out = new ConcurrentHashMap<>();
        for (String id : instance.getTemplate().quests().quests().keySet()) {
            out.put(id, status(instance, id));
        }
        return out;
    }

    private @Nullable QuestConfig.Quest find(AbyssInstance instance, String questId) {
        return instance.getTemplate().quests().quests().get(questId);
    }

    private static String questKey(String id) { return "quest." + id; }

    private static String color(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}
