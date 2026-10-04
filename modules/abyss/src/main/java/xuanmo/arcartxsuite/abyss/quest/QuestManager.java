package xuanmo.arcartxsuite.abyss.quest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.links.AbyssLinks;
import xuanmo.arcartxsuite.abyss.script.ScriptContext;
import xuanmo.arcartxsuite.abyss.script.ScriptRunner;
import xuanmo.arcartxsuite.abyss.template.QuestConfig;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;

/**
 * 副本任务适配层：任务语义全部委托 questgps/Chemdah（玩家级档案），
 * 本类只保留 abyss 编排面——
 * <ul>
 *   <li>quests.yml 的 abyssId → chemdahId 映射解析</li>
 *   <li>接取/完成时的副本脚本钩子（on-accept/on-complete）</li>
 *   <li>manual-claim 任务的"回 NPC 领取"状态（实例变量）</li>
 *   <li>{@code quest.<id>} 实例变量投影（脚本 check 用，最后写入者为准）</li>
 * </ul>
 * Chemdah 任务为玩家级：接取/完成均按成员各自结算；重复刷本需任务配置为可重复接取。
 */
public final class QuestManager {

    public enum QuestStatus { NONE, ACCEPTED, COMPLETED }

    private final Supplier<AbyssLinks> linksSupplier;

    public QuestManager(Supplier<AbyssLinks> linksSupplier) {
        this.linksSupplier = linksSupplier;
    }

    private @Nullable AbyssLinks links() {
        return linksSupplier != null ? linksSupplier.get() : null;
    }

    /** 玩家在任务上的真实状态（questgps 缺席/未接取 → NONE）。 */
    public QuestStatus status(AbyssInstance instance, Player player, String questId) {
        QuestConfig.Quest q = find(instance, questId);
        AbyssLinks l = links();
        if (q == null || l == null || player == null) return QuestStatus.NONE;
        QuestGpsNavigable.QuestState state = l.questState(player, q.chemdahId());
        if (state == null) return QuestStatus.NONE;
        return switch (state) {
            case COMPLETED -> QuestStatus.COMPLETED;
            case ACTIVE -> QuestStatus.ACCEPTED;
            case NONE -> QuestStatus.NONE;
        };
    }

    /**
     * 接取任务：questgps acceptQuest（Chemdah 玩家级）+ on-accept 副本脚本
     * + auto-track 时自动追踪。返回 false=任务不存在/该玩家已接取/questgps 缺席。
     */
    public boolean accept(AbyssInstance instance, Player trigger,
                          ScriptRunner runner, String questId) {
        QuestConfig.Quest q = find(instance, questId);
        AbyssLinks l = links();
        if (q == null || l == null || trigger == null) return false;
        if (status(instance, trigger, questId) != QuestStatus.NONE) return false;
        l.questAccept(trigger, q.chemdahId());
        instance.getVariables().put(questKey(questId), QuestStatus.ACCEPTED);
        if (!q.onAccept().isEmpty()) {
            runner.run("quest_accept_" + questId, q.onAccept(),
                ScriptContext.of(instance, trigger), instance);
        }
        if (q.autoTrack()) {
            l.questTrack(trigger, q.chemdahId(), null);
        }
        return true;
    }

    /**
     * 提交任务（manual-claim 任务的"回 NPC 领取奖励"）：
     * 该玩家已完成且未领 → 跑 on-complete 副本脚本 + 记已领。
     * 非 manual-claim 的任务完成时已自动发放，此路径作为兜底领取。
     */
    public SubmitResult submit(AbyssInstance instance, Player trigger,
                               ScriptRunner runner, String questId) {
        QuestConfig.Quest q = find(instance, questId);
        if (q == null) return SubmitResult.NOT_FOUND;
        if (isClaimed(instance, trigger, q)) return SubmitResult.ALREADY_DONE;
        QuestStatus st = status(instance, trigger, questId);
        if (st == QuestStatus.NONE) return SubmitResult.NOT_ACCEPTED;
        if (st != QuestStatus.COMPLETED) return SubmitResult.CONDITION_FAILED;
        grantComplete(instance, trigger, runner, q);
        return SubmitResult.OK;
    }

    /**
     * EventBus {@code axs.questgps.quest_completed} 路由入口：
     * 按 chemdahId 匹配副本任务，manual-claim → 记可领取标记；
     * 否则立即跑 on-complete 发奖并记已领。写 quest.&lt;id&gt; 投影变量。
     */
    public void onQuestCompleted(AbyssInstance instance, Player player,
                               ScriptRunner runner, String chemdahId) {
        if (instance == null || player == null || chemdahId == null) return;
        for (QuestConfig.Quest q : instance.getTemplate().quests().quests().values()) {
            if (!q.chemdahId().equalsIgnoreCase(chemdahId)) continue;
            instance.getVariables().put(questKey(q.id()), QuestStatus.COMPLETED);
            if (q.manualClaim()) {
                instance.getVariables().put(claimableKey(q.id(), player), Boolean.TRUE);
            } else if (!isClaimed(instance, player, q)) {
                grantComplete(instance, player, runner, q);
            }
        }
    }

    /** 实例内全部任务 id → 该玩家状态（HUD 任务行用）。 */
    public Map<String, QuestStatus> statusMap(AbyssInstance instance, Player player) {
        Map<String, QuestStatus> out = new ConcurrentHashMap<>();
        for (String id : instance.getTemplate().quests().quests().keySet()) {
            out.put(id, status(instance, player, id));
        }
        return out;
    }

    /** abyss 任务 id → chemdah 任务 id（无映射时原样返回）。 */
    public String resolveChemdahId(AbyssInstance instance, String questId) {
        QuestConfig.Quest q = find(instance, questId);
        return q != null ? q.chemdahId() : questId;
    }

    /** 该玩家是否已领取完成奖励。 */
    public boolean isClaimed(AbyssInstance instance, Player player, QuestConfig.Quest q) {
        Object v = instance.getVariables().get(claimedKey(q.id(), player));
        return Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
    }

    /** 该玩家是否有待领取的完成奖励（manual-claim）。 */
    public boolean isClaimable(AbyssInstance instance, Player player, QuestConfig.Quest q) {
        Object v = instance.getVariables().get(claimableKey(q.id(), player));
        return Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
    }

    private void grantComplete(AbyssInstance instance, Player player,
                               ScriptRunner runner, QuestConfig.Quest q) {
        instance.getVariables().put(claimedKey(q.id(), player), Boolean.TRUE);
        instance.getVariables().remove(claimableKey(q.id(), player));
        if (!q.onComplete().isEmpty()) {
            runner.run("quest_complete_" + q.id(), q.onComplete(),
                ScriptContext.of(instance, player), instance);
        }
    }

    public enum SubmitResult {
        OK, NOT_FOUND, NOT_ACCEPTED, CONDITION_FAILED, ALREADY_DONE
    }

    private @Nullable QuestConfig.Quest find(AbyssInstance instance, String questId) {
        return instance.getTemplate().quests().quests().get(questId);
    }

    private static String questKey(String id) { return "quest." + id; }
    private static String claimableKey(String id, Player p) {
        return "quest.claimable." + id + "." + p.getUniqueId();
    }
    private static String claimedKey(String id, Player p) {
        return "quest.claimed." + id + "." + p.getUniqueId();
    }
}
