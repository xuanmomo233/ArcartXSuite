package xuanmo.arcartxsuite.abyss.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * 阶段机配置（stages.yml，或 scripts.yml 内联 {@code stages:} 段）。
 *
 * <p>给副本一条事件驱动的流程主线：每个阶段声明通关条件，达成后推进下一阶段；
 * 最终阶段完成后走 scripts.yml 的 complete 流程。事件源：怪组清完、交互点触发、
 * 变量变化（慢扫兜底）、{@code complete_stage} 脚本动作、阶段超时。</p>
 *
 * <pre>
 * stages.yml:
 *   entry: clear_wave            # 可省，默认首个阶段
 *   clear_wave:
 *     name: "&amp;e清理前厅"        # 可省，默认阶段 key
 *     complete-when: "group_cleared:wave_1"
 *     timeout: 300               # 秒，0/不写=不限时
 *     on-start:  [action ...]    # 进入时执行一次
 *     on-complete: [action ...]  # 通关时执行一次
 *     on-timeout:  [action ...]  # 超时执行（不自动推进）
 *     next: boss                 # 或分支列表：
 *                                #   - target: 隐藏房
 *                                #     condition: "var:找到密道 == 1"
 *                                #   - target: 结算            # 无条件兜底放最后
 * </pre>
 *
 * <p>complete-when 条件 DSL（实例级，无需玩家上下文）：</p>
 * <ul>
 *   <li>{@code group_cleared:<组名>} — 怪物组已清完</li>
 *   <li>{@code room_cleared:<房id>} — floors 房间已清</li>
 *   <li>{@code var:<名> <op> <值>} — 实例变量比较（==,!=,&gt;,&gt;=,&lt;,&lt;=）</li>
 *   <li>{@code players_alive <op> <N>} — 存活玩家数比较</li>
 *   <li>{@code js:<表达式>} / {@code aria:<表达式>} — 宿主脚本引擎求真值</li>
 *   <li>空/缺省 — 仅由 {@code complete_stage} 动作手动推进</li>
 * </ul>
 */
public record StageConfig(
    @Nullable String entry,
    Map<String, Stage> stages
) {

    public record Stage(
        @Nullable String name,
        @Nullable String completeWhen,
        int timeoutSeconds,
        List<String> onStart,
        List<String> onComplete,
        List<String> onTimeout,
        List<Branch> next
    ) { }

    /** next 分支项：condition 为 null = 无条件兜底（应放列表最后）。 */
    public record Branch(@Nullable String condition, String target) { }

    public static final StageConfig NONE = new StageConfig(null, Map.of());

    public boolean isEmpty() { return stages.isEmpty(); }

    /** 入口阶段 id：显式 entry &gt; 首个阶段 key。 */
    public @Nullable String entryId() {
        if (entry != null && stages.containsKey(entry)) return entry;
        return stages.isEmpty() ? null : stages.keySet().iterator().next();
    }

    public static StageConfig load(@Nullable ConfigurationSection root,
                                   @Nullable ConfigurationSection scriptsInline) {
        Map<String, Stage> stages = new LinkedHashMap<>();
        String entry = null;
        // stages.yml 全文件即阶段表（顶层 entry: + 各阶段 key）
        if (root != null) {
            entry = str(root, "entry");
            collectStages(root, stages);
        }
        // scripts.yml 内联 stages: 段（低配载并入，文件优先——同名阶段被文件覆盖则文件定义生效）
        if (scriptsInline != null) {
            String ie = str(scriptsInline, "entry");
            if (entry == null) entry = ie;
            collectStages(scriptsInline, stages);
        }
        if (stages.isEmpty()) return NONE;
        return new StageConfig(entry, stages);
    }

    private static void collectStages(ConfigurationSection s, Map<String, Stage> out) {
        for (String key : s.getKeys(false)) {
            if ("entry".equalsIgnoreCase(key)) continue;
            ConfigurationSection st = s.getConfigurationSection(key);
            if (st == null) continue;
            out.put(key, loadStage(st));
        }
    }

    private static Stage loadStage(ConfigurationSection st) {
        return new Stage(
            str(st, "name"),
            str(st, "complete-when", "complete_when", "when"),
            st.getInt("timeout", st.getInt("time-limit", 0)),
            lines(st, "on-start", "on_start", "start-script"),
            lines(st, "on-complete", "on_complete", "complete-script"),
            lines(st, "on-timeout", "on_timeout", "timeout-script"),
            loadBranches(st)
        );
    }

    private static List<Branch> loadBranches(ConfigurationSection st) {
        Object raw = st.get("next");
        List<Branch> out = new ArrayList<>();
        if (raw instanceof String single) {
            if (!single.isBlank()) out.add(new Branch(null, single.trim()));
            return out;
        }
        List<?> list = st.getList("next");
        if (list == null) return out;
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object target = m.get("target");
                if (target == null) target = m.get("stage");
                Object cond = m.get("condition");
                if (cond == null) cond = m.get("when");
                if (target != null) {
                    out.add(new Branch(
                        cond == null ? null : String.valueOf(cond),
                        String.valueOf(target)));
                }
            } else if (o instanceof String t && !t.isBlank()) {
                out.add(new Branch(null, t.trim()));
            }
        }
        return out;
    }

    private static @Nullable String str(ConfigurationSection s, String... keys) {
        for (String k : keys) {
            String v = s.getString(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static List<String> lines(ConfigurationSection s, String... keys) {
        for (String k : keys) {
            if (s.isList(k)) {
                List<String> l = s.getStringList(k);
                if (!l.isEmpty()) return l;
            } else {
                String v = s.getString(k);
                if (v != null && !v.isBlank()) {
                    return List.of(v.split("\\r?\\n"));
                }
            }
        }
        return List.of();
    }
}
