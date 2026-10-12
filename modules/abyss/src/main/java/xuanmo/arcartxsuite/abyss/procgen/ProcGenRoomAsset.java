package xuanmo.arcartxsuite.abyss.procgen;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.RegionTemplate;

/**
 * 房间构件：一份几何 + 一份元数据。
 *
 * <p>房间集目录 {@code data/abyss/room-sets/<集>/} 下三种文件配对：</p>
 * <ul>
 *   <li>{@code xxx.schem} + {@code xxx.yml} — WorldEdit 构件（支持旋转）</li>
 *   <li>{@code xxx.region.yml} + {@code xxx.yml} — 本服圈选 region 快照（支持旋转，
 *       经 BlockData.rotate 转换朝向方块）</li>
 *   <li>{@code xxx.yml} 单独存在 = 只有元数据，无几何 → 加载时报错跳过</li>
 * </ul>
 *
 * <p>元数据 yml 字段（ProcGen 约定，全部可省）：</p>
 * <pre>
 * type: combat                # start/combat/elite/boss/shop/treasure/event/rest
 * weight: 10                  # 抽取权重
 * manual-clear: false         # 手动清房（脚本 procgen_clear_room 才开门）
 * spawn: "10,1,10"            # 起点房出生点（房内相对坐标，地板 y=0 上 y=1）
 * mobs: ["4,1,4: MobId"]      # 第 1 波（坐标=房内相对坐标）
 * mobs-wave-2: [...]          # 第 2 波，不能跳号
 * chests: ["12,1,3: 宝箱id"]   # chest_rewards.yml 宝箱
 * npcs: ["8,1,8: npc名"]       # npcs.yml NPC
 * interacts: ["4,1,4: 交互名"] # interacts.yml 交互点
 * doors: ["north,8,5,5"]      # 自定义门：墙,沿墙中心格,宽,高
 * scripts:
 *   on-enter: [action...]
 *   on-wave: { '2': [action...] }
 *   on-clear: [action...]
 * </pre>
 */
public record ProcGenRoomAsset(
    String name,
    RoomKind kind,
    int weight,
    boolean manualClear,
    @Nullable int[] spawn,             // [x,y,z] 房内相对
    List<RoomMob>[] waves,             // [0]=第1波… 空槽位=跳号空洞（不该有）
    List<PlacedRef> chests,
    List<PlacedRef> npcs,
    List<PlacedRef> interacts,
    List<DoorSpec> doors,
    List<String> onEnter,
    Map<Integer, List<String>> onWave,
    List<String> onClear,
    @Nullable File schemFile,          // 二选一
    @Nullable RegionTemplate region    // 二选一
) {

    public enum RoomKind { START, COMBAT, ELITE, BOSS, SHOP, TREASURE, EVENT, REST }

    /** 房内相对坐标 + 外部引用 id（宝箱/NPC/交互点）。 */
    public record PlacedRef(int x, int y, int z, String ref) { }

    /** 房内怪物点。 */
    public record RoomMob(int x, int y, int z, String mobId) { }

    /** 自定义门：wall=N/S/E/W，center=沿墙中轴偏移格数，w/h=门洞截面。 */
    public record DoorSpec(String wall, int center, int w, int h) { }

    /** 几何尺寸（物化/布局用）：region 直接读；schem 由物化器读剪贴板维度。 */
    public @Nullable int[] size() {
        return region != null
            ? new int[]{region.width(), region.height(), region.depth()} : null;
    }

    @SuppressWarnings("unchecked")
    public static @Nullable ProcGenRoomAsset load(String name, File metaFile,
                                                  @Nullable File schemFile,
                                                  @Nullable RegionTemplate region) {
        if (!metaFile.isFile() || (schemFile == null && region == null)) return null;
        org.bukkit.configuration.file.YamlConfiguration yml =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(metaFile);

        RoomKind kind;
        try {
            kind = RoomKind.valueOf(
                yml.getString("type", "combat").toUpperCase());
        } catch (IllegalArgumentException e) {
            kind = RoomKind.COMBAT;
        }

        // 波次：mobs + mobs-wave-2..N（不跳号）
        List<List<RoomMob>> waveList = new ArrayList<>();
        waveList.add(parseMobs(yml.getStringList("mobs")));
        for (int i = 2; i <= 32; i++) {
            List<String> l = yml.getStringList("mobs-wave-" + i);
            if (l.isEmpty()) break;
            waveList.add(parseMobs(l));
        }
        // 至少留一个空波槽防越界
        @SuppressWarnings("unchecked")
        List<RoomMob>[] waves = waveList.toArray(new List[0]);

        // doors
        List<DoorSpec> doors = new ArrayList<>();
        for (String d : yml.getStringList("doors")) {
            String[] p = d.split(",");
            if (p.length < 2) continue;
            doors.add(new DoorSpec(p[0].trim().toLowerCase(),
                parseInt(p[1], 0),
                p.length > 2 ? parseInt(p[2], 5) : 5,
                p.length > 3 ? parseInt(p[3], 5) : 5));
        }

        // scripts
        ConfigurationSection scripts = yml.getConfigurationSection("scripts");
        List<String> onEnter = List.of(), onClear = List.of();
        Map<Integer, List<String>> onWave = new LinkedHashMap<>();
        if (scripts != null) {
            onEnter = lines(scripts, "on-enter", "on_enter");
            onClear = lines(scripts, "on-clear", "on_clear");
            ConfigurationSection ow = scripts.getConfigurationSection("on-wave");
            if (ow == null) ow = scripts.getConfigurationSection("on_wave");
            if (ow != null) {
                for (String k : ow.getKeys(false)) {
                    try {
                        onWave.put(Integer.parseInt(k), ow.getStringList(k));
                    } catch (NumberFormatException ignored) { }
                }
            }
        }

        return new ProcGenRoomAsset(
            name, kind,
            yml.getInt("weight", 10),
            yml.getBoolean("manual-clear", yml.getBoolean("manual_clear", false)),
            parseLoc(yml.getString("spawn", null)),
            waves,
            parseRefs(yml.getStringList("chests")),
            parseRefs(yml.getStringList("npcs")),
            parseRefs(yml.getStringList("interacts")),
            doors, onEnter, onWave, onClear,
            schemFile, region);
    }

    /** {@code "x,y,z"} 或 {@code "x,y,z,yaw,pitch"} → int 三元组。 */
    static @Nullable int[] parseLoc(@Nullable String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.split(",");
        if (p.length < 3) return null;
        return new int[]{parseInt(p[0], 0), parseInt(p[1], 0), parseInt(p[2], 0)};
    }

    /** {@code "x,y,z: 引用id"} 列表。 */
    static List<PlacedRef> parseRefs(List<String> raw) {
        List<PlacedRef> out = new ArrayList<>();
        for (String s : raw) {
            int ci = s.indexOf(':');
            if (ci < 0) continue;
            int[] loc = parseLoc(s.substring(0, ci).trim());
            if (loc != null) out.add(new PlacedRef(loc[0], loc[1], loc[2],
                s.substring(ci + 1).trim()));
        }
        return out;
    }

    /** {@code "x,y,z: MobId"} 列表。 */
    static List<RoomMob> parseMobs(List<String> raw) {
        List<RoomMob> out = new ArrayList<>();
        for (String s : raw) {
            int ci = s.indexOf(':');
            if (ci < 0) continue;
            int[] loc = parseLoc(s.substring(0, ci).trim());
            if (loc != null) out.add(new RoomMob(loc[0], loc[1], loc[2],
                s.substring(ci + 1).trim()));
        }
        return out;
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

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }
}
