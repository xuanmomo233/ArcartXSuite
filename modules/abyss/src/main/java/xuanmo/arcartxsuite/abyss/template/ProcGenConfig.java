package xuanmo.arcartxsuite.abyss.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * 随机地牢生成配置（地牢目录 {@code procgen.yml}，存在即启用随机拼装模式）。
 *
 * <pre>
 * room-set: grand              # 房间集名（默认地牢 id）；池目录 data/abyss/room-sets/&lt;名&gt;/
 * length: standard             # short/standard/long（房型数量档）
 * counts:                      # 精确覆盖 length（数字或 "a-b" 范围）
 *   combat: "4-6"
 *   elite: 1
 * mob-level: "1-3"             # MythicMobs 等级，范围时每个怪点随机
 * seal-combat-doors: true      # 战斗房封门
 * seal-material: IRON_BARS
 * wave-delay: 40               # 波次间隔 tick
 * elite-level-bonus: 2         # 精英房额外等级
 * spawn-telegraph: true        # 刷怪预兆（或子段 delay/particle/sound/volume/pitch）
 * modifiers: 0.35              # 词缀概率；或 {chance, pool:[id], include-boss}
 * corridor:                    # 走廊与门
 *   length: 6                  # 房间间距（0=相贴）
 *   door-width: 5 / door-height: 5
 *   floor/wall/ceiling: STONE_BRICKS
 * challenges:                  # 挑战（默认关）
 *   timed: { chance: 0.25, seconds: 60 }
 *   encore: true               # 连战加赏：清房后潜行右键宝箱再战一轮
 *   reward: 稀有礼包            # challenges 达成奖励（rewards.yml id）
 * grading:                     # 通关评级（默认开）
 *   par-seconds-per-room: 60
 *   scripts: { S: [action...] }
 * seed-mode: random            # random/daily/fixed
 * seed-value: 0                # fixed 时用
 * </pre>
 */
public record ProcGenConfig(
    String roomSet,
    @Nullable String length,
    Map<String, String> counts,
    String mobLevel,                 // "1-3" 或 "5"
    boolean sealCombatDoors,
    Material sealMaterial,
    int waveDelayTicks,
    int eliteLevelBonus,
    Telegraph telegraph,
    ModifierSel modifiers,
    Corridor corridor,
    Challenges challenges,
    Grading grading,
    SeedMode seedMode,
    long seedValue,
    Map<String, ModifierDef> modifierDefs
) {

    public enum SeedMode { RANDOM, DAILY, FIXED }

    /** 刷怪预兆。 */
    public record Telegraph(boolean enabled, int delayTicks, String particle,
                            String sound, float volume, float pitch) {
        static Telegraph load(@Nullable ConfigurationSection s, Object raw) {
            if (raw instanceof Boolean b) {
                return new Telegraph(b, 20, "PORTAL",
                    "entity.evoker.prepare_summon", 0.8f, 1.0f);
            }
            if (s == null) {
                return new Telegraph(false, 20, "PORTAL",
                    "entity.evoker.prepare_summon", 0.8f, 1.0f);
            }
            return new Telegraph(true,
                s.getInt("delay", 20),
                s.getString("particle", "PORTAL"),
                s.getString("sound", "entity.evoker.prepare_summon"),
                (float) s.getDouble("volume", 0.8),
                (float) s.getDouble("pitch", 1.0));
        }
    }

    /** 词缀抽取：chance 概率 + pool 过滤（空=全部）+ include-boss。 */
    public record ModifierSel(double chance, List<String> pool, boolean includeBoss) {
        static ModifierSel OFF = new ModifierSel(0, List.of(), true);
    }

    /** 词缀定义（modifier-defs 节每项）。 */
    public record ModifierDef(String id, double mobLevelBonus,
                              List<String> onEnter, String announce) { }

    /** 走廊与门洞。 */
    public record Corridor(int length, int doorWidth, int doorHeight,
                           Material floor, Material wall, Material ceiling,
                           @Nullable String template) { }

    /** 挑战配置。 */
    public record Challenges(boolean timedEnabled, double timedChance, int timedSeconds,
                             boolean encore, @Nullable String reward) { }

    /** 评级配置。 */
    public record Grading(boolean enabled, int parSecondsPerRoom,
                          Map<String, List<String>> scripts) { }

    public static ProcGenConfig load(@Nullable ConfigurationSection s, String dungeonId) {
        if (s == null) return null;
        Map<String, String> counts = new LinkedHashMap<>();
        ConfigurationSection cs = s.getConfigurationSection("counts");
        if (cs != null) {
            for (String k : cs.getKeys(false)) counts.put(k, cs.getString(k));
        }
        // 词缀
        ModifierSel mods = ModifierSel.OFF;
        Object mraw = s.get("modifiers");
        if (mraw instanceof Boolean b) {
            mods = new ModifierSel(b ? 0.35 : 0, List.of(), true);
        } else if (mraw instanceof Number n) {
            mods = new ModifierSel(n.doubleValue(), List.of(), true);
        } else {
            ConfigurationSection ms = s.getConfigurationSection("modifiers");
            if (ms != null) {
                mods = new ModifierSel(
                    ms.getDouble("chance", 0.35),
                    ms.getStringList("pool"),
                    ms.getBoolean("include-boss", true));
            }
        }
        // 词缀定义
        Map<String, ModifierDef> defs = new LinkedHashMap<>();
        ConfigurationSection ds = s.getConfigurationSection("modifier-defs");
        if (ds == null) ds = s.getConfigurationSection("modifier_defs");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection d = ds.getConfigurationSection(id);
                if (d == null) continue;
                defs.put(id, new ModifierDef(id,
                    d.getDouble("mob-level-bonus", d.getDouble("mob_level_bonus", 0)),
                    lines(d, "on-enter", "on_enter", "script"),
                    d.getString("announce", null)));
            }
        }
        // 走廊
        ConfigurationSection cor = s.getConfigurationSection("corridor");
        Corridor corridor = cor == null
            ? new Corridor(6, 5, 5, Material.STONE_BRICKS,
                Material.STONE_BRICKS, Material.STONE_BRICKS, null)
            : new Corridor(
                cor.getInt("length", 6),
                cor.getInt("door-width", cor.getInt("door_width", 5)),
                cor.getInt("door-height", cor.getInt("door_height", 5)),
                mat(cor.getString("floor"), Material.STONE_BRICKS),
                mat(cor.getString("wall"), Material.STONE_BRICKS),
                mat(cor.getString("ceiling"), Material.STONE_BRICKS),
                cor.getString("template", null));
        // 挑战
        ConfigurationSection ch = s.getConfigurationSection("challenges");
        Challenges challenges;
        if (ch == null) {
            challenges = new Challenges(false, 0.25, 60, false, null);
        } else {
            ConfigurationSection t = ch.getConfigurationSection("timed");
            challenges = new Challenges(
                t != null, t != null ? t.getDouble("chance", 0.25) : 0,
                t != null ? t.getInt("seconds", 60) : 60,
                ch.getBoolean("encore", false),
                ch.getString("reward", null));
        }
        // 评级
        ConfigurationSection gr = s.getConfigurationSection("grading");
        Grading grading;
        if (gr == null) {
            grading = new Grading(true, 60, Map.of());
        } else {
            Map<String, List<String>> gs = new LinkedHashMap<>();
            ConfigurationSection gss = gr.getConfigurationSection("scripts");
            if (gss != null) {
                for (String g : gss.getKeys(false)) {
                    gs.put(g.toUpperCase(), gss.getStringList(g));
                }
            }
            grading = new Grading(gr.getBoolean("enabled", true),
                gr.getInt("par-seconds-per-room",
                    gr.getInt("par_seconds_per_room", 60)), gs);
        }
        // 种子
        SeedMode seedMode;
        try {
            seedMode = SeedMode.valueOf(
                s.getString("seed-mode", s.getString("seed_mode", "random")).toUpperCase());
        } catch (IllegalArgumentException e) {
            seedMode = SeedMode.RANDOM;
        }

        return new ProcGenConfig(
            s.getString("room-set", s.getString("room_set", dungeonId)),
            s.getString("length", null),
            counts,
            s.getString("mob-level", s.getString("mob_level", "1")),
            s.getBoolean("seal-combat-doors", s.getBoolean("seal_combat_doors", true)),
            mat(s.getString("seal-material", s.getString("seal_material", "IRON_BARS")),
                Material.IRON_BARS),
            s.getInt("wave-delay", s.getInt("wave_delay", 40)),
            s.getInt("elite-level-bonus", s.getInt("elite_level_bonus", 2)),
            Telegraph.load(s.getConfigurationSection("spawn-telegraph"),
                s.get("spawn-telegraph") != null ? s.get("spawn-telegraph")
                    : s.get("spawn_telegraph")),
            mods, corridor, challenges, grading, seedMode,
            s.getLong("seed-value", s.getLong("seed_value", 0)),
            defs);
    }

    private static Material mat(@Nullable String name, Material def) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name.toUpperCase());
        return m != null ? m : def;
    }

    private static List<String> lines(ConfigurationSection s, String... keys) {
        for (String k : keys) {
            if (s.isList(k)) {
                List<String> l = s.getStringList(k);
                if (!l.isEmpty()) return new ArrayList<>(l);
            } else {
                String v = s.getString(k);
                if (v != null && !v.isBlank()) {
                    return new ArrayList<>(List.of(v.split("\\r?\\n")));
                }
            }
        }
        return List.of();
    }
}
