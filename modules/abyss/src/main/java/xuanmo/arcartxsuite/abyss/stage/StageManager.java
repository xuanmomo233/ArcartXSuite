package xuanmo.arcartxsuite.abyss.stage;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.StageConfig;
import xuanmo.arcartxsuite.abyss.template.StageConfig.Branch;
import xuanmo.arcartxsuite.abyss.template.StageConfig.Stage;
import xuanmo.arcartxsuite.api.condition.ScriptActionKind;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

/**
 * 阶段机（stages.yml）：事件驱动的副本流程主线。
 *
 * <p>阶段推进由事件触发重估（怪组清完、交互点触发、{@code complete_stage}
 * 动作、阶段超时），另有低频慢扫兜底覆盖变量写入等无回调路径。
 * 最终阶段无 {@code next} 时走副本 complete 流程。</p>
 */
public final class StageManager {

    /** 脚本行执行器（service 注入；vars 进 ScriptContext 额外变量）。 */
    @FunctionalInterface
    public interface ScriptLauncher {
        void run(AbyssInstance instance, List<String> lines,
                 @Nullable Player trigger, Map<String, String> vars);
    }

    /** 实例运行态。 */
    private record Runtime(String stageId, long enteredAt,
                           @Nullable ScheduledTask timeoutTask) { }

    private final AbyssService service;
    private final Plugin plugin;
    private final SchedulerAPI scheduler;
    private final Logger logger;
    private final Map<UUID, Runtime> states = new ConcurrentHashMap<>();
    private volatile ScriptLauncher scriptLauncher;

    public StageManager(AbyssService service, Plugin plugin,
                        SchedulerAPI scheduler, Logger logger) {
        this.service = service;
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void setScriptLauncher(ScriptLauncher l) { this.scriptLauncher = l; }

    /** 实例进入 ACTIVE 时调用：有 stages 配置的副本从入口阶段开始。 */
    public void start(AbyssInstance instance) {
        StageConfig cfg = instance.getTemplate().stages();
        if (cfg == null || cfg.isEmpty()) return;
        String entry = cfg.entryId();
        if (entry != null) enter(instance, entry);
    }

    /** 事件后重估当前阶段（怪组清完/交互/慢扫/显式动作）。 */
    public void onEvent(AbyssInstance instance) {
        Runtime rt = states.get(instance.getInstanceId());
        if (rt == null || instance.isFinished()) return;
        reevaluate(instance, rt, null);
    }

    /** {@code complete_stage} 脚本动作：立即完成当前阶段并推进。 */
    public void completeCurrent(AbyssInstance instance, @Nullable Player trigger) {
        Runtime rt = states.get(instance.getInstanceId());
        if (rt == null || instance.isFinished()) return;
        advance(instance, rt, trigger);
    }

    /** {@code set_stage} 动作：直接跳入指定阶段（跳过当前阶段 on-complete）。 */
    public void jump(AbyssInstance instance, String stageId, @Nullable Player trigger) {
        if (stageId == null || stageId.isBlank() || instance.isFinished()) return;
        enter(instance, stageId.trim());
    }

    /** 当前阶段 id（查询/PAPI/调试）。 */
    public @Nullable String currentStage(AbyssInstance instance) {
        Runtime rt = states.get(instance.getInstanceId());
        return rt == null ? null : rt.stageId();
    }

    /** 实例销毁清理。 */
    public void clear(AbyssInstance instance) {
        Runtime rt = states.remove(instance.getInstanceId());
        if (rt != null && rt.timeoutTask() != null) rt.timeoutTask().cancel();
    }

    // ── 内部 ─────────────────────────────────────────────

    private void reevaluate(AbyssInstance instance, Runtime rt, @Nullable Player trigger) {
        Stage stage = stageOf(instance, rt.stageId());
        if (stage == null) { states.remove(instance.getInstanceId()); return; }
        if (evalCond(instance, stage.completeWhen())) {
            advance(instance, rt, trigger);
        }
    }

    private void advance(AbyssInstance instance, Runtime rt, @Nullable Player trigger) {
        Stage stage = stageOf(instance, rt.stageId());
        if (stage == null) { states.remove(instance.getInstanceId()); return; }
        runLines(instance, stage.onComplete(), trigger, rt.stageId());
        // 分支按序判定：第一个条件成立的命中；无命中找无条件兜底
        String next = null;
        for (Branch b : stage.next()) {
            if (b.condition() == null || b.condition().isBlank()
                || evalCond(instance, b.condition())) { next = b.target(); break; }
        }
        if (next == null || next.isBlank()) {
            states.remove(instance.getInstanceId());
            service.completeDungeon(instance);
            return;
        }
        enter(instance, next);
    }

    private void enter(AbyssInstance instance, String stageId) {
        Stage stage = stageOf(instance, stageId);
        if (stage == null) {
            logger.fine("[abyss] 阶段不存在: " + stageId
                + "（地牢 " + instance.getTemplate().id() + "）");
            states.remove(instance.getInstanceId());
            return;
        }
        cancelTimeout(instance);
        ScheduledTask timeoutTask = null;
        if (stage.timeoutSeconds() > 0) {
            timeoutTask = scheduler.scheduleOnceSeconds(plugin,
                () -> onTimeout(instance, stageId), stage.timeoutSeconds());
            instance.registerTask(timeoutTask);
        }
        states.put(instance.getInstanceId(),
            new Runtime(stageId, System.currentTimeMillis(), timeoutTask));
        if (stage.name() != null) {
            String text = service.msg("dungeon.stage-enter", "stage", stage.name());
            instance.getPlayers().forEach(p -> p.sendMessage(text));
        }
        runLines(instance, stage.onStart(), null, stageId);
        // 进入即判一次：条件瞬间成立则连跳
        Runtime rt = states.get(instance.getInstanceId());
        if (rt != null && rt.stageId().equals(stageId)) {
            reevaluate(instance, rt, null);
        }
    }

    private void onTimeout(AbyssInstance instance, String stageId) {
        Runtime rt = states.get(instance.getInstanceId());
        if (rt == null || !rt.stageId().equals(stageId) || instance.isFinished()) return;
        Stage stage = stageOf(instance, stageId);
        if (stage != null) runLines(instance, stage.onTimeout(), null, stageId);
        // 超时只跑脚本不自动推进（作者可在 on-timeout 里 fail/complete_stage）
        reevaluate(instance, rt, null);
    }

    private void cancelTimeout(AbyssInstance instance) {
        Runtime rt = states.get(instance.getInstanceId());
        if (rt != null && rt.timeoutTask() != null) rt.timeoutTask().cancel();
    }

    private @Nullable Stage stageOf(AbyssInstance instance, String stageId) {
        StageConfig cfg = instance.getTemplate().stages();
        return cfg == null ? null : cfg.stages().get(stageId);
    }

    private void runLines(AbyssInstance instance, List<String> lines,
                          @Nullable Player trigger, String stageId) {
        if (lines.isEmpty() || scriptLauncher == null) return;
        scriptLauncher.run(instance, lines, trigger, Map.of("stage", stageId));
    }

    /**
     * 条件 DSL 求值（实例级，无玩家上下文）：
     * <pre>
     *   group_cleared:&lt;组&gt;   room_cleared:&lt;房id&gt;   players_alive &lt;op&gt; N
     *   var:&lt;名&gt; &lt;op&gt; &lt;值&gt;      js:&lt;表达式&gt;   aria:&lt;表达式&gt;
     *   裸表达式                  — 按 JS 求真值
     * </pre>
     * 空条件 = 不自动推进（manual，靠 complete_stage）。
     */
    private boolean evalCond(AbyssInstance instance, @Nullable String cond) {
        if (cond == null || cond.isBlank()) return false;
        String c = cond.trim();
        try {
            if (c.regionMatches(true, 0, "group_cleared:", 0, 14)) {
                return service.mobGroups().isGroupComplete(instance, c.substring(14).trim());
            }
            if (c.regionMatches(true, 0, "room_cleared:", 0, 13)) {
                String ref = c.substring(13).trim();
                var pg = service.procgen();
                if (pg != null) {
                    Boolean r = pg.roomClearedByName(instance, ref);
                    if (r != null) return r;
                }
                var rm = service.roomManager();
                return rm != null && rm.isRoomCleared(instance, ref);
            }
            if (c.regionMatches(true, 0, "players_alive", 0, 13)) {
                long alive = instance.getPlayers().stream()
                    .filter(p -> !instance.isPlayerCurrentlyDead(p)).count();
                return compare(alive, c.substring(13).trim());
            }
            if (c.regionMatches(true, 0, "var:", 0, 4)) {
                return compare(instance.getVariables(), c.substring(4).trim());
            }
            if (c.regionMatches(true, 0, "aria:", 0, 5)) {
                return truthy(service.scriptActions().execute(null,
                    ScriptActionKind.ARIA, c.substring(5).trim()));
            }
            if (c.regionMatches(true, 0, "js:", 0, 3)) {
                return truthy(service.scriptActions().execute(null,
                    ScriptActionKind.JS, c.substring(3).trim()));
            }
            return truthy(service.scriptActions().execute(null, ScriptActionKind.JS, c));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** {@code <名> <op> <值>} 变量比较。 */
    private static boolean compare(Map<String, Object> vars, String expr) {
        String[] p = expr.split(" ", 3);
        if (p.length < 2) return truthy(vars.get(expr));
        Object lhs = vars.get(p[0]);
        String op = p[1];
        String rhs = p.length > 2 ? p[2] : "";
        return compareRaw(lhs, op, rhs);
    }

    /** {@code <op> <值>} 数值比较（players_alive 已取左值）。 */
    private static boolean compare(long lhs, String expr) {
        String[] p = expr.split(" ", 2);
        String op = p[0];
        String rhs = p.length > 1 ? p[1].trim() : "0";
        return compareRaw(lhs, op, rhs);
    }

    private static boolean compareRaw(@Nullable Object lhs, String op, String rhs) {
        double ln = toNum(lhs), rn = toNum(rhs);
        boolean numeric = !Double.isNaN(ln) && !Double.isNaN(rn);
        String ls = lhs == null ? "" : String.valueOf(lhs);
        return switch (op) {
            case "==" -> numeric ? ln == rn : ls.equals(rhs);
            case "!=" -> numeric ? ln != rn : !ls.equals(rhs);
            case ">" -> numeric && ln > rn;
            case ">=" -> numeric && ln >= rn;
            case "<" -> numeric && ln < rn;
            case "<=" -> numeric && ln <= rn;
            default -> ls.equals(rhs);
        };
    }

    private static double toNum(@Nullable Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); }
        catch (Exception e) { return Double.NaN; }
    }

    private static boolean truthy(@Nullable Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.doubleValue() != 0.0;
        String s = String.valueOf(o).trim();
        return !(s.isEmpty() || s.equalsIgnoreCase("false") || s.equals("0"));
    }
}
