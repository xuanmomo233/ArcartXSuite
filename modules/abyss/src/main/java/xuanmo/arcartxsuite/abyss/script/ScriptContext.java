package xuanmo.arcartxsuite.abyss.script;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;

/**
 * 脚本执行上下文：持有实例、触发者、动作参数、变量访问。
 * <p>
 * 选择器解析：{@code @all}=副本全员 {@code @trigger}=触发者
 * {@code @random}=随机存活玩家 {@code @dead}=死亡玩家 {@code <玩家名>}。
 * 变量解析：{@code {var:key}} 读实例变量；{@code {player}} 触发者名。
 */
public final class ScriptContext {

    private final AbyssInstance instance;
    private final @Nullable Player trigger;
    private final @Nullable String mobGroup;     // 怪物组回调时的组名
    private final Map<String, String> extraVars; // 运行时额外变量（如 {task_time}）
    private boolean settlement;

    public ScriptContext(AbyssInstance instance, @Nullable Player trigger,
                         @Nullable String mobGroup, @Nullable Map<String, String> extraVars) {
        this.instance = instance;
        this.trigger = trigger;
        this.mobGroup = mobGroup;
        this.extraVars = extraVars == null ? Map.of() : extraVars;
    }

    public static ScriptContext of(AbyssInstance instance, @Nullable Player trigger) {
        return new ScriptContext(instance, trigger, null, null);
    }
    public static ScriptContext settlement(AbyssInstance instance, @Nullable Player trigger) {
        ScriptContext context = of(instance, trigger);
        context.settlement = true;
        return context;
    }
    public boolean allowsFinishedInstance() { return settlement; }

    public static ScriptContext group(AbyssInstance instance, String group, @Nullable Player trigger) {
        return new ScriptContext(instance, trigger, group, null);
    }

    public static ScriptContext withVars(AbyssInstance instance, @Nullable Player trigger,
                                         Map<String, String> vars) {
        return new ScriptContext(instance, trigger, null, vars);
    }

    public AbyssInstance getInstance() { return instance; }
    public @Nullable Player getTrigger() { return trigger; }
    public @Nullable String getMobGroup() { return mobGroup; }

    public @Nullable Object getVariable(String key) { return instance.getVariables().get(key); }
    public void setVariable(String key, Object value) { instance.getVariables().put(key, value); }

    /** 解析玩家选择器为玩家列表。 */
    public List<Player> resolvePlayers(String selector) {
        if (selector == null || selector.isBlank() || "@trigger".equalsIgnoreCase(selector)) {
            return trigger != null ? List.of(trigger) : List.of();
        }
        return switch (selector.toLowerCase()) {
            case "@all" -> instance.getPlayers();
            case "@random" -> {
                List<Player> alive = instance.getPlayers().stream()
                    .filter(p -> !instance.isPlayerCurrentlyDead(p)).toList();
                yield alive.isEmpty() ? List.of()
                    : List.of(alive.get(ThreadLocalRandom.current().nextInt(alive.size())));
            }
            case "@dead" -> instance.getPlayers().stream()
                .filter(instance::isPlayerCurrentlyDead).toList();
            default -> {
                Player p = Bukkit.getPlayerExact(selector);
                yield p != null ? List.of(p) : List.of();
            }
        };
    }

    /** 占位符替换：{player} {dungeon} {instance_id} {mob_group} {var:key} {x:y}额外变量。 */
    public String replace(String input) {
        if (input == null || input.isEmpty()) return input;
        String r = input;
        r = r.replace("{player}", trigger != null ? trigger.getName() : "");
        r = r.replace("{dungeon}", instance.getTemplate().name());
        r = r.replace("{dungeon_id}", instance.getTemplate().id());
        r = r.replace("{instance_id}", instance.getInstanceId().toString());
        r = r.replace("{mob_group}", mobGroup != null ? mobGroup : "");
        for (Map.Entry<String, String> e : extraVars.entrySet()) {
            r = r.replace("{" + e.getKey() + "}", e.getValue());
        }
        // {var:key}
        int idx;
        while ((idx = r.indexOf("{var:")) >= 0) {
            int end = r.indexOf('}', idx);
            if (end < 0) break;
            String key = r.substring(idx + 5, end);
            Object v = instance.getVariables().get(key);
            r = r.substring(0, idx) + (v == null ? "" : String.valueOf(v)) + r.substring(end + 1);
        }
        return r;
    }
}
