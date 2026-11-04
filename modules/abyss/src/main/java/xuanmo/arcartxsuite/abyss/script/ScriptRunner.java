package xuanmo.arcartxsuite.abyss.script;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.api.condition.ScriptActionKind;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

/**
 * 脚本执行器：动作行分派 + 行内 {@code aria:}/{@code js:} 委托宿主双引擎。
 * <p>
 * 语法：
 * <pre>
 *   message @all &a副本开始          # 模块动作
 *   wait 5                          # 挂起 5 秒（支持 5 / 5s / 5t / 5tick）
 *   aria: heal(@trigger, 20)        # Aria 表达式 → 宿主执行器
 *   js: player.setHealth(20)        # JavaScript → 宿主执行器
 *   condition: <expr> ? [...]       # 预留（行内条件暂走 condition 动作）
 * </pre>
 */
public final class ScriptRunner {

    private final Plugin plugin;
    private final SchedulerAPI scheduler;
    private final ActionRegistry actions;
    private final Supplier<xuanmo.arcartxsuite.abyss.service.AbyssService> service;
    private final Logger logger;

    public ScriptRunner(Plugin plugin, SchedulerAPI scheduler,
                        Supplier<xuanmo.arcartxsuite.abyss.service.AbyssService> service,
                        Logger logger) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.service = service;
        this.logger = logger;
        this.actions = new ActionRegistry();
        BuiltinActions.registerAll(actions, this, service);
    }

    public ActionRegistry actions() { return actions; }

    /**
     * 运行一段脚本（异步会话）：立即创建会话并在主线程 resume。
     *
     * @return 会话（wait 挂起后可被实例 cancel）
     */
    public ScriptSession run(String name, List<String> lines, ScriptContext context,
                             AbyssInstance instance) {
        ScriptSession session = new ScriptSession(name, lines, context, this);
        instance.registerScriptSession(session);
        session.completion().whenComplete((done,error)->instance.unregisterScriptSession(session));
        runOnMain(session::resume);
        return session;
    }

    /**
     * 执行单行。
     *
     * @return true 表示该行使会话挂起（wait）；false 继续下一行
     */
    boolean executeLine(String line, ScriptContext context, ScriptSession session) {
        var trial = context.getInstance().draftTrial();
        if (trial != null && !trial.consumeStep()) return false;
        if (line.startsWith("flow_if ")) {
            int end = line.indexOf(' ', 8);
            if (end < 0) { session.cancel(); return false; }
            String condition = line.substring(8, end);
            if (!condition.matches("(?:var:|group_cleared:|room_cleared:)[\\p{L}\\p{N}_.-]{1,64}")) {
                session.cancel();
                return false;
            }
            var stages = service.get().stages();
            if (stages == null || !stages.evalCond(context.getInstance(), condition)) return false;
            line = line.substring(end + 1).trim();
            if (line.startsWith("flow_if ")) { session.cancel(); return false; }
        }
        // ── 行内宿主脚本 ──
        String lower = line.toLowerCase();
        if (trial != null) {
            int split = line.indexOf(' ');
            String action = split < 0 ? lower : lower.substring(0, split);
            String args = split < 0 ? "" : line.substring(split + 1).trim();
            if (action.equals("wait")) {
                long ticks = Math.min(200, parseWaitTicks(args));
                trial.trace("等待 " + ticks + " tick（试跑上限 200）");
                if (ticks > 0) return suspend(session, ticks);
                return false;
            }
            trial.execute(action, args);
            return false;
        }
        if (lower.startsWith("aria:")) {
            evalInline(context.getTrigger(), ScriptActionKind.ARIA, line.substring(5).trim());
            return false;
        }
        if (lower.startsWith("js:") || lower.startsWith("javascript:")) {
            String body = lower.startsWith("js:")
                ? line.substring(3).trim() : line.substring(11).trim();
            evalInline(context.getTrigger(), ScriptActionKind.JS, body);
            return false;
        }
        if (lower.startsWith("wait ") || lower.equals("wait")) {
            return doWait(line.substring(4).trim(), context, session);
        }

        // ── 模块动作 ──
        int sp = line.indexOf(' ');
        String name = (sp < 0 ? line : line.substring(0, sp)).toLowerCase();
        String args = sp < 0 ? "" : line.substring(sp + 1).trim();
        Action action = actions.get(name);
        if (action == null) {
            logger.warning("[abyss] 未知脚本动作: " + name + " (实例 "
                + context.getInstance().getInstanceId() + ")");
            session.cancel();return false;
        }
        try {
            action.execute(context, args, session);
        } catch (Throwable t) {
            logger.warning("[abyss] 脚本动作 " + name + " 执行异常: " + t.getMessage());
            session.cancel();
            if(context.getTrigger()!=null)context.getTrigger().sendMessage("§c副本操作未完成，后续动作已停止，请联系管理员。");
        }
        return false;
    }

    private void evalInline(@Nullable Player player, ScriptActionKind kind, String code) {
        if (code.isEmpty()) return;
        try {
            ScriptConditionServices.actionExecutor().execute(player, kind, code);
        } catch (Throwable t) {
            logger.warning("[abyss] " + kind + " 行内脚本异常: " + t.getMessage());
        }
    }

    /**
     * wait 解析：数字默认秒；"5s"/"5秒" 秒；"5t"/"5tick" tick。
     * 挂起：调度 delayTicks 后 resume。返回 true。
     */
    private boolean doWait(String arg, ScriptContext context, ScriptSession session) {
        long ticks = parseWaitTicks(context.replace(arg));
        if (ticks <= 0) return false;
        return suspend(session, ticks);
    }

    private boolean suspend(ScriptSession session, long ticks) {
        session.suspend(scheduler.scheduleOnce(plugin, () -> {
            if (!session.isCancelled()) {
                runOnMain(session::resume);
            }
        }, ticks));
        return true;
    }

    private static long parseWaitTicks(String s) {
        if (s == null || s.isBlank()) return 0;
        String v = s.trim().toLowerCase();
        try {
            if (v.endsWith("tick") || v.endsWith("t")) {
                return Long.parseLong(v.replaceAll("[^0-9.]", "")) ;
            }
            if (v.endsWith("秒") || v.endsWith("s") || v.matches("^[0-9.]+$")) {
                double sec = Double.parseDouble(v.replaceAll("[^0-9.]", ""));
                return (long) (sec * 20);
            }
            return (long) (Double.parseDouble(v) * 20);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void runOnMain(Runnable r) {
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    public SchedulerAPI scheduler() { return scheduler; }
    public Plugin plugin() { return plugin; }
    public Logger logger() { return logger; }
}
