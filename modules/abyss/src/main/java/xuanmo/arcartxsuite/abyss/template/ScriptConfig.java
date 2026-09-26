package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * scripts.yml 解析：生命周期脚本段 + 自定义段。
 * 每段是动作行列表（支持模块动作命令 + "aria: ..." / "js: ..." 行内脚本）。
 *
 * <pre>
 * init: [...]
 * start: [...]
 * complete: [...]
 * fail: [...]
 * player_death: [...]
 * all_death: [...]
 * exit: [...]
 * victory_effect: [...]        # 自定义段，用 action.run_script victory_effect 调用
 * </pre>
 */
public record ScriptConfig(
    List<String> init,
    List<String> start,
    List<String> complete,
    List<String> fail,
    List<String> playerDeath,
    List<String> allDeath,
    List<String> exit,
    Map<String, List<String>> customs
) {

    private static final List<String> LIFECYCLE = List.of(
        "init", "start", "complete", "fail", "player_death", "all_death", "exit");

    public static ScriptConfig load(ConfigurationSection root) {
        if (root == null) {
            return new ScriptConfig(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of());
        }
        Map<String, List<String>> customs = new LinkedHashMap<>();
        for (String key : root.getKeys(false)) {
            String norm = key.toLowerCase();
            if (!LIFECYCLE.contains(norm)) {
                customs.put(norm, root.getStringList(key));
            }
        }
        return new ScriptConfig(
            root.getStringList("init"),
            root.getStringList("start"),
            root.getStringList("complete"),
            root.getStringList("fail"),
            firstNonEmpty(root, "player_death", "player-death"),
            firstNonEmpty(root, "all_death", "all-death"),
            root.getStringList("exit"),
            customs
        );
    }

    private static List<String> firstNonEmpty(ConfigurationSection s, String a, String b) {
        List<String> r = s.getStringList(a);
        return r.isEmpty() ? s.getStringList(b) : r;
    }
}
