package xuanmo.arcartxsuite.abyss.design;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.abyss.script.ScriptContext;
import xuanmo.arcartxsuite.abyss.script.ScriptRunner;
import xuanmo.arcartxsuite.abyss.stage.StageManager;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;

/** Isolated event rehearsal using the real stage and script dispatchers. */
public final class DraftTrial implements AutoCloseable {
    public record Hook(String object, String event) { }
    public record Event(String id, DesignObject.Kind kind, List<String> lines) {
        public Event { lines = List.copyOf(lines); }
    }

    private final AbyssInstance instance;
    private final StageManager stages;
    private final ScriptRunner scripts;
    private final Map<Hook, Event> hooks;
    private final Set<String> rooms = new HashSet<>();
    private final Set<String> groups = new HashSet<>();
    private final ArrayDeque<String> trace = new ArrayDeque<>();
    private final Consumer<String> output;
    private int remainingActions = 512;
    private int nestedScripts;
    private boolean closed;

    public DraftTrial(AbyssTemplate draft, StageManager stages, ScriptRunner scripts,
                      Map<Hook, Event> hooks, Consumer<String> output) {
        this.instance = new AbyssInstance(UUID.randomUUID(), draft, null);
        instance.draftTrial(this);
        this.stages = stages;
        this.scripts = scripts;
        this.hooks = Map.copyOf(hooks);
        this.output = output;
    }

    public void start() {
        if (!instance.transitionTo(AbyssStatus.PREPARING, AbyssStatus.ACTIVE)) return;
        trace("开始草稿事件试跑，世界、奖励和外部动作仅记录");
        after(run("初始化", instance.getTemplate().scripts().init(), false), () ->
            after(run("开始", instance.getTemplate().scripts().start(), false), () -> stages.start(instance)));
    }

    public void event(String object, String event) {
        requireReady();
        Event selected = hooks.get(new Hook(object, event));
        if (selected == null) throw new IllegalArgumentException("该事件不在试跑快照中");
        trace("注入事件 " + selected.id() + " / " + event);
        if (selected.kind() == DesignObject.Kind.ROOM && event.equals("on-clear")) rooms.add(selected.id());
        if ((selected.kind() == DesignObject.Kind.GROUP || selected.kind() == DesignObject.Kind.ROOM_GROUP)
            && event.equals("on-end")) groups.add(selected.id());
        after(run(event, selected.lines(), false), () -> stages.onEvent(instance));
    }

    public void mapCleared() {
        requireReady();
        instance.getVariables().put("map_cleared", true);
        trace("注入事件 地图清空");
        if (instance.getTemplate().stages().isEmpty()) finish(true);
        else stages.onEvent(instance);
    }

    public boolean matches(String condition) {
        if (condition.startsWith("group_cleared:")) return groups.contains(condition.substring(14).trim());
        if (condition.startsWith("room_cleared:")) return rooms.contains(condition.substring(13).trim());
        trace("跳过需要实时环境的条件 " + condition);
        return false;
    }

    public boolean consumeStep() {
        if (closed || --remainingActions < 0) {
            if (!closed) trace("动作预算已用完，结束试跑");
            close();
            return false;
        }
        return true;
    }

    public void execute(String action, String args) {
        switch (action) {
            case "run_script" -> runNested(args);
            case "set_stage" -> {
                trace("执行跳转 " + args);
                stages.jump(instance, args, null);
            }
            case "complete_stage" -> stages.completeCurrent(instance, null);
            case "complete_dungeon" -> finish(true);
            case "fail_dungeon" -> finish(false);
            case "message" -> trace("提示 " + args);
            default -> trace("记录动作 " + action.split(":", 2)[0]);
        }
    }

    private void runNested(String name) {
        var lines = instance.getTemplate().scripts().customs().get(name.toLowerCase(java.util.Locale.ROOT));
        if (lines == null || nestedScripts >= 16) {
            trace("跳过未定义或过深的子脚本 " + name);
            return;
        }
        trace("进入子脚本 " + name);
        nestedScripts++;
        try {
            after(run(name, lines, false), () -> stages.onEvent(instance));
        } finally {
            nestedScripts--;
        }
    }

    public void finish(boolean success) {
        if (!instance.transitionTo(AbyssStatus.ACTIVE, success ? AbyssStatus.COMPLETED : AbyssStatus.FAILED)) return;
        stages.clear(instance);
        var template = instance.getTemplate();
        var policy = success ? template.settlement().complete() : template.settlement().failure();
        trace((success ? "通关" : "失败") + "结算 " + policy.mode() + " " + policy.seconds() + " 秒");
        run("结算动作", success ? template.scripts().complete() : template.scripts().fail(), true);
    }

    public void trace(String value) {
        String line = value.replaceAll("[\\p{Cntrl}§]", " ");
        if (line.length() > 200) line = line.substring(0, 200);
        if (trace.size() >= 128) trace.removeFirst();
        trace.addLast(line);
        output.accept(line);
    }

    public List<String> traceSnapshot() { return List.copyOf(trace); }

    private void requireReady() {
        if (closed || instance.isFinished()) throw new IllegalArgumentException("请先开始新一轮草稿试跑");
        if (!instance.getScriptSessions().isEmpty()) throw new IllegalArgumentException("请等待当前动作结束");
    }

    private void after(xuanmo.arcartxsuite.abyss.script.ScriptSession script, Runnable next) {
        if (script == null) {
            if (!closed && !instance.isFinished()) next.run();
        } else {
            script.completion().thenAccept(ok -> {
                if (ok && !closed && !instance.isFinished()) next.run();
            });
        }
    }

    private xuanmo.arcartxsuite.abyss.script.ScriptSession run(String name, List<String> lines, boolean settlement) {
        if (lines.isEmpty() || closed || (instance.isFinished() && !settlement)) return null;
        return scripts.run(name, lines, settlement ? ScriptContext.settlement(instance, null)
            : ScriptContext.of(instance, null), instance);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        instance.transitionTo(AbyssStatus.ACTIVE, AbyssStatus.CANCELLED);
        stages.clear(instance);
        for (var task : List.copyOf(instance.getTasks())) task.cancel();
        for (var session : List.copyOf(instance.getScriptSessions())) session.cancel();
        instance.clearRuntime();
    }
}
