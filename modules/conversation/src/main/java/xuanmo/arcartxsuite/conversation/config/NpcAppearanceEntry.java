package xuanmo.arcartxsuite.conversation.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 单条 NPC 外观配置：NPC 名称 → 模型 + 动画。
 *
 * <p>动画支持两种模式：
 * <ul>
 *   <li><b>默认状态</b>（持久）：填写 {@code state} + {@code animation}，不填 {@code animation-speed}，
 *       调用 {@code setDefaultState(state, animation)}。</li>
 *   <li><b>一次性播放</b>（带速度）：填写 {@code animation} + {@code animation-speed}，
 *       调用 {@code playAnimation(animation, speed, transition-time, keep-time)}。
 *       此时 {@code state} 字段将被忽略。</li>
 * </ul>
 *
 * <pre>
 * npc-appearances:
 *   - npc: "村长老王"
 *     model: my_model
 *     scale: 1.0
 *     state: idle
 *     animation: idle_loop
 *   - npc: "铁匠张三"
 *     model: npc_blacksmith
 *     scale: 1.0
 *     animation: hammer_swing
 *     animation-speed: 1.5
 *     transition-time: 100
 *     keep-time: -1
 * </pre>
 */
public record NpcAppearanceEntry(
    String npcName,
    String modelId,
    double scale,
    String state,
    String animName,
    double animationSpeed,
    int transitionTime,
    long keepTime
) {

    /** 是否有动画配置（playAnimation 模式：填写了 animName 和 animationSpeed > 0）。 */
    public boolean hasPlayAnimation() {
        return animName != null && !animName.isBlank() && animationSpeed > 0.0D;
    }

    /** 是否为 setDefaultState 模式（state + animName，且未填 animationSpeed）。 */
    public boolean hasDefaultState() {
        return state != null && !state.isBlank()
            && animName != null && !animName.isBlank()
            && animationSpeed <= 0.0D;
    }

    public static List<NpcAppearanceEntry> loadList(FileConfiguration root) {
        if (root == null) {
            return List.of();
        }
        List<?> rawList = root.getList("npc-appearances");
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<NpcAppearanceEntry> entries = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            String npcName = str(map.get("npc"));
            String modelId = str(map.get("model"));
            if (npcName == null || modelId == null) {
                continue;
            }
            double scale = parseDouble(map.get("scale"), 1.0D);
            String state = str(map.get("state"));
            String animName = str(map.get("animation"));
            double animationSpeed = parseDoubleAny(map.get("animation-speed"), 0.0D);
            int transitionTime = parseInt(map.get("transition-time"), 5);
            long keepTime = parseLong(map.get("keep-time"), -1L);
            entries.add(new NpcAppearanceEntry(npcName, modelId, scale, state, animName, animationSpeed, transitionTime, keepTime));
        }
        return List.copyOf(entries);
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return d > 0.0D ? d : fallback;
        }
        if (value instanceof String string) {
            try {
                double d = Double.parseDouble(string.trim());
                return d > 0.0D ? d : fallback;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double parseDoubleAny(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static long parseLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
