package xuanmo.arcartxsuite.abyss.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * puzzles.yml 解析：副本内机关解谜定义。
 *
 * <pre>
 * # 序列型：按顺序触发 interact 点名
 * valve_sequence:
 *   name: "压力阀序列"
 *   type: sequence
 *   steps: [valve_4, valve_2, valve_1, valve_3]   # interacts.yml 中的点名
 *   reset-on-miss: true          # 错序时清空进度重来
 *   miss-limit: 0                # 错序次数上限（0=不限，超限触发 on-fail）
 *   on-step:     ["sound @trigger ui.button.click 1 1.4"]
 *   on-miss:     ["subtitle @all &c顺序错误", "spawn_group noise_lure"]
 *   on-complete: ["delete_obstacle pump_door"]
 *   on-fail:     []
 *
 * # 输入型：弹 ArcartX 输入面板，键盘输入答案（密码/口令）
 * elevator_code:
 *   name: "加密电梯终端"
 *   type: input
 *   answer: "4312"               # 正确答案（仅服务端持有，不发给客户端）
 *   case-sensitive: false
 *   length: 4                    # 输入框最大长度
 *   input-pattern: "^[0-9]*$"    # 输入框字符正则（客户端过滤）
 *   title: "加密电梯终端"
 *   hint: "请输入四位通行码"      # 输入框空提示
 *   masked: false                # true=密码掩码显示（*）
 *   max-attempts: 3              # 允许错误次数（0=不限；超限 on-fail 并锁定）
 *   per-player: false            # true=每玩家独立计数；false=全队共享计数
 *   on-complete: ["fillblocks ..."]
 *   on-miss:     ["title @trigger &c密码错误"]
 *   on-fail:     ["broadcast &c终端已锁定"]
 * </pre>
 *
 * 打开输入面板：interact/NPC/脚本动作 {@code puzzle_prompt <puzzle>}。
 * 实例变量：{@code {var:puzzle.<id>}}=当前进度（sequence=步数/input=已尝试次数），
 * {@code {var:puzzle.<id>.done}}=完成标记（"1"）。
 */
public record PuzzleConfig(
    Map<String, Puzzle> puzzles
) {

    public static final PuzzleConfig EMPTY = new PuzzleConfig(Map.of());

    public enum Type {
        /** 序列机关：按 steps 顺序触发对应 interact 点名。 */
        SEQUENCE,
        /** 输入机关：ArcartX 输入面板，玩家键盘输入答案。 */
        INPUT
    }

    /**
     * @param id          机关 id（puzzles.yml 键名）
     * @param name        显示名（可含 &amp; 颜色，用于输入面板标题兜底/消息）
     * @param type        SEQUENCE / INPUT
     * @param steps       sequence：interact 点名顺序（可重复点名）
     * @param resetOnMiss sequence：错序是否清空进度（false=错序仅惩罚不回退）
     * @param answer      input：正确答案（仅服务端比对，永不下发客户端）
     * @param caseSensitive input：是否大小写敏感
     * @param length      input：输入框最大长度（0=不限制）
     * @param inputPattern input：客户端字符过滤正则（可空）
     * @param title       input：面板标题（空用 name）
     * @param hint        input：输入框空提示
     * @param masked      input：掩码显示（密码位 *）
     * @param maxAttempts input：最大错误次数（0=不限）
     * @param perPlayer   input：尝试次数按玩家独立计数（false=副本共享）
     * @param onStep      每步正确后执行
     * @param onMiss      错误后执行（错序/答案错误）
     * @param onComplete  完成时执行
     * @param onFail      超过错误上限时执行（并锁定机关：puzzle.locked=1）
     */
    public record Puzzle(
        String id,
        String name,
        Type type,
        List<String> steps,
        boolean resetOnMiss,
        @Nullable String answer,
        boolean caseSensitive,
        int length,
        @Nullable String inputPattern,
        @Nullable String title,
        @Nullable String hint,
        boolean masked,
        int maxAttempts,
        boolean perPlayer,
        List<String> onStep,
        List<String> onMiss,
        List<String> onComplete,
        List<String> onFail
    ) {

        static Puzzle load(String id, ConfigurationSection s) {
            Type type;
            try {
                type = Type.valueOf(s.getString("type", "sequence").toUpperCase());
            } catch (IllegalArgumentException e) {
                type = Type.SEQUENCE;
            }
            return new Puzzle(
                id,
                s.getString("name", id),
                type,
                s.getStringList("steps"),
                s.getBoolean("reset-on-miss", s.getBoolean("reset_on_miss", true)),
                s.getString("answer", null),
                s.getBoolean("case-sensitive", s.getBoolean("case_sensitive", false)),
                s.getInt("length", 0),
                s.getString("input-pattern", s.getString("input_pattern", null)),
                s.getString("title", null),
                s.getString("hint", null),
                s.getBoolean("masked", false),
                s.getInt("max-attempts", s.getInt("max_attempts", 0)),
                s.getBoolean("per-player", s.getBoolean("per_player", false)),
                strList(s, "on-step"),
                strList(s, "on-miss"),
                strList(s, "on-complete"),
                strList(s, "on-fail")
            );
        }

        private static List<String> strList(ConfigurationSection s, String kebab) {
            List<String> v = s.getStringList(kebab);
            if (v.isEmpty()) v = s.getStringList(kebab.replace('-', '_'));
            return v;
        }
    }

    public static PuzzleConfig load(@Nullable ConfigurationSection root) {
        Map<String, Puzzle> map = new LinkedHashMap<>();
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s != null) {
                    map.put(key, Puzzle.load(key, s));
                }
            }
        }
        return new PuzzleConfig(map);
    }
}
