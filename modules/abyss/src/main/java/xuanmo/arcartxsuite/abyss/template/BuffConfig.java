package xuanmo.arcartxsuite.abyss.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * buffs.yml 解析：增益选择池（roguelike 祝福/三选一式增益）。
 *
 * <pre>
 * pools:
 *   ember_blessing:
 *     name: "余烬祝福"
 *     pick: 1                # 每人选几个（<=0 = 非选择型，全部按权重直接生效一个）
 *     candidates: 3          # 面板候选数（从 options 按 weight 抽；<=0 或超池=全显）
 *     presentation: panel    # panel=AX 选择卡片 / hud=不弹窗，直接生效+HUD 提示
 *     timeout: 20            # 选择限时秒（0=不限；超时按 timeout-auto 兜底）
 *     timeout-auto: random   # first|random|none（none=放弃不给）
 *     once-per-instance: true  # 同一实例内本池只能 offer 一次（默认 false）
 *     options:
 *       - id: sharp_edge
 *         name: "&c利刃"
 *         icon: IRON_SWORD      # 面板图标材质名（可空）
 *         rarity: rare          # common|rare|epic|legendary —— 卡片描边色
 *         weight: 10            # 抽中权重（默认 10）
 *         desc: ["攻击伤害 +15%"]
 *         effects:              # 脚本行（脚本 DSL 全动作可用，@trigger=选择者）
 *           - "apply_attribute @trigger attack_damage 15 percent"
 * </pre>
 *
 * 触发不写在这里——在生命周期段/interact/机关脚本里用 {@code buff_offer <池id>} 动作，
 * 或 {@code buff_grant <池id> <optId> <选择器>} 直接赋予。
 */
public record BuffConfig(Map<String, Pool> pools) {

    public static final BuffConfig EMPTY = new BuffConfig(Map.of());

    public record Pool(
        String id,
        String name,
        int pick,
        int candidates,
        String presentation,
        int timeout,
        String timeoutAuto,
        boolean oncePerInstance,
        List<Option> options
    ) {
        public boolean isPanel() {
            return "panel".equalsIgnoreCase(presentation);
        }
    }

    public record Option(
        String id,
        String name,
        String icon,
        String rarity,
        int weight,
        List<String> desc,
        List<String> effects
    ) { }

    public static BuffConfig load(ConfigurationSection root) {
        if (root == null) return EMPTY;
        ConfigurationSection pools = root.getConfigurationSection("pools");
        if (pools == null) return EMPTY;

        Map<String, Pool> result = new LinkedHashMap<>();
        for (String pid : pools.getKeys(false)) {
            ConfigurationSection ps = pools.getConfigurationSection(pid);
            if (ps == null) continue;
            List<Option> options = new ArrayList<>();
            for (Map<?, ?> m : ps.getMapList("options")) {
                Object oid = m.get("id");
                if (oid == null) continue;
                options.add(new Option(
                    String.valueOf(oid),
                    m.get("name") != null ? String.valueOf(m.get("name")) : String.valueOf(oid),
                    m.get("icon") != null ? String.valueOf(m.get("icon")) : "",
                    m.get("rarity") != null ? String.valueOf(m.get("rarity")).toLowerCase() : "common",
                    intOf(m.get("weight"), 10),
                    stringListOf(m.get("desc")),
                    stringListOf(m.get("effects"))));
            }
            if (options.isEmpty()) continue;
            result.put(pid, new Pool(
                pid,
                ps.getString("name", pid),
                ps.getInt("pick", 1),
                ps.getInt("candidates", 0),
                ps.getString("presentation", "panel"),
                ps.getInt("timeout", 20),
                ps.getString("timeout-auto", ps.getString("timeout_auto", "random")).toLowerCase(),
                ps.getBoolean("once-per-instance", ps.getBoolean("once_per_instance", false)),
                List.copyOf(options)));
        }
        return result.isEmpty() ? EMPTY : new BuffConfig(Map.copyOf(result));
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return o != null ? Integer.parseInt(String.valueOf(o)) : def; }
        catch (NumberFormatException e) { return def; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListOf(Object o) {
        if (o instanceof List<?> list) {
            List<String> r = new ArrayList<>(list.size());
            for (Object e : list) r.add(String.valueOf(e));
            return List.copyOf(r);
        }
        return o != null ? List.of(String.valueOf(o)) : List.of();
    }
}
