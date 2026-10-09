package xuanmo.arcartxsuite.abyss.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig.MonsterGroup;

/**
 * floors.yml / rooms.yml 解析：多层房间地牢定义（config.yml {@code mode: floors}）。
 *
 * <p>坐标语义统一为「房间原点 + 相对坐标」：
 * <ul>
 *   <li>{@code source: region} —— 大地图区域模式，房间 {@code origin} 默认 0,0,0，
 *       即所有坐标直接写世界绝对值（建筑已在模板世界中）。</li>
 *   <li>{@code source: pool} —— 随机拼接模式，房间内坐标相对 schematic 原点，
 *       实例创建时随机抽房粘贴到锚点，运行期物化为绝对坐标。</li>
 * </ul>
 *
 * <pre>
 * # floors.yml
 * defaults:
 *   lock-material: BARRIER
 *   border-pullback: true
 *   room-spacing: 200
 *   paste-y: 64
 *   advance-timeout: 60
 *
 * floors:
 *   - id: f1
 *     name: "第 1 层"
 *     source: region            # 固定区域（世界内手工建好的房间链）
 *     spawn: "0,65,0,0,0"       # 本层入口站位（绝对）
 *     rooms: [room_a, room_b]   # rooms.yml 引用链
 *     boss-room: boss_1         # 层末房（可空；不配则链尾房即层末）
 *     advance: portal           # portal=踩出口传送 | auto=清完自动传送
 *     advance-timeout: 60       # portal 兜底秒数（0=不兜底）
 *     on-enter: [...]
 *     on-clear: [...]
 *   - id: f2
 *     source: pool              # 随机拼接（虚空世界 + schematic 池）
 *     pool: [combat_a, combat_b, shop_a]
 *     room-count: 4
 *     boss-room: boss_2
 *     direction: x              # 排列轴 x|z
 *     advance: auto
 *
 * # rooms.yml
 * rooms:
 *   combat_a:
 *     type: combat              # combat|safe|boss|shop —— safe 不锁门
 *     schematic: "rooms/combat_a.schem"   # pool 必填；region 忽略
 *     origin: "0,0,0"                    # region 可选（默认 0,0,0）
 *     region: ["0,60,0", "29,80,29"]     # 房间 box（相对 origin）
 *     spawn: "1,64,15,90,0"              # 进房站位（相对 origin）
 *     exit: ["29,63,13", "31,67,17"]     # 出口传送区（可选；清完激活）
 *     doors:                             # 物理门盒（锁=填塞，开=还原）
 *       - region: ["30,64,14", "30,66,16"]
 *         material: BARRIER              # 可空=defaults.lock-material
 *     groups:                            # 房内怪组（格式同 monsters.yml）
 *       w1:
 *         spawn-timing: AUTO_START       # 锁门时触发
 *         monsters:
 *           - mob-id: minecraft:zombie
 *             location: "15,64,15"
 *             amount: 4
 *       w2:
 *         spawn-timing: TRIGGERED
 *         trigger-after: [w1]
 *         monsters: [...]
 *     on-enter: [...]   # 玩家首次进房
 *     on-lock: [...]    # 锁门瞬间
 *     on-clear: [...]   # 房内怪全清（含无怪房进房即清）
 * </pre>
 */
public record FloorConfig(
    Defaults defaults,
    List<Floor> floors,
    Map<String, RoomTemplate> rooms
) {

    public static final FloorConfig EMPTY =
        new FloorConfig(Defaults.DEFAULT, List.of(), Map.of());

    public enum Source { REGION, POOL, GEN }
    public enum Advance { PORTAL, AUTO }
    public enum RoomType { COMBAT, SAFE, BOSS, SHOP }

    // ═══ 默认值 ═══

    public record Defaults(
        String lockMaterial,
        boolean borderPullback,
        int roomSpacing,
        int pasteY,
        int advanceTimeout,
        xuanmo.arcartxsuite.abyss.gen.GenSpec gen
    ) {
        public static final Defaults DEFAULT = new Defaults("BARRIER", true, 200, 64, 60,
            xuanmo.arcartxsuite.abyss.gen.GenSpec.DEFAULT);

        static Defaults load(@Nullable ConfigurationSection s) {
            if (s == null) return DEFAULT;
            return new Defaults(
                s.getString("lock-material", s.getString("lock_material", "BARRIER")),
                s.getBoolean("border-pullback", s.getBoolean("border_pullback", true)),
                s.getInt("room-spacing", s.getInt("room_spacing", 200)),
                s.getInt("paste-y", s.getInt("paste_y", 64)),
                s.getInt("advance-timeout", s.getInt("advance_timeout", 60)),
                xuanmo.arcartxsuite.abyss.gen.GenSpec.load(
                    s.getConfigurationSection("gen"),
                    xuanmo.arcartxsuite.abyss.gen.GenSpec.DEFAULT)
            );
        }
    }

    // ═══ 楼层 ═══

    /**
     * @param roomRefs    region 模式：rooms.yml 引用链
     * @param pool        pool 模式：随机抽取的房间池（ref 列表）
     * @param roomCount   pool 抽取数（0=池全用且打乱顺序）
     * @param bossRoom    层末房 ref（可空）
     */
    public record Floor(
        String id,
        String name,
        Source source,
        @Nullable String spawn,
        List<String> roomRefs,
        List<String> pool,
        int roomCount,
        @Nullable String bossRoom,
        String direction,
        Advance advance,
        int advanceTimeout,
        List<String> onEnter,
        List<String> onClear,
        Map<String, Double> optionalRooms,
        @Nullable xuanmo.arcartxsuite.abyss.gen.GenSpec gen,
        @Nullable String palette
    ) {
        static Floor load(int index, ConfigurationSection s, Defaults d) {
            Source source;
            try {
                source = Source.valueOf(s.getString("source", "region").toUpperCase());
            } catch (IllegalArgumentException e) {
                source = Source.REGION;
            }
            Advance advance;
            try {
                advance = Advance.valueOf(s.getString("advance", "portal").toUpperCase());
            } catch (IllegalArgumentException e) {
                advance = Advance.PORTAL;
            }
            Map<String, Double> optional = new LinkedHashMap<>();
            ConfigurationSection opt = s.getConfigurationSection("optional-rooms");
            if (opt == null) opt = s.getConfigurationSection("optional_rooms");
            if (opt != null) {
                for (String k : opt.getKeys(false)) optional.put(k, opt.getDouble(k));
            }
            return new Floor(
                s.getString("id", "floor_" + index),
                s.getString("name", s.getString("id", "floor_" + index)),
                source,
                s.getString("spawn", null),
                s.getStringList("rooms"),
                s.getStringList("pool"),
                s.getInt("room-count", s.getInt("room_count", 0)),
                s.getString("boss-room", s.getString("boss_room", null)),
                s.getString("direction", "x").toLowerCase(),
                advance,
                s.getInt("advance-timeout", s.getInt("advance_timeout", d.advanceTimeout())),
                s.getStringList("on-enter").isEmpty()
                    ? s.getStringList("on_enter") : s.getStringList("on-enter"),
                s.getStringList("on-clear").isEmpty()
                    ? s.getStringList("on_clear") : s.getStringList("on-clear"),
                optional,
                s.getConfigurationSection("gen") != null
                    ? xuanmo.arcartxsuite.abyss.gen.GenSpec.load(
                        s.getConfigurationSection("gen"), d.gen())
                    : null,
                s.getString("palette", null)
            );
        }
    }

    // ═══ 房间模板 ═══

    /**
     * @param origin    region 模式房间原点 "x,y,z"（默认 0,0,0 = 坐标即绝对值）
     * @param region    房间 box 两角（相对 origin），必填
     * @param spawn     进房站位（相对 origin），必填
     * @param exit      出口传送区两角（相对 origin；可空）
     * @param doors     物理门盒（可空）
     * @param groups    房内怪组（坐标相对 origin；键为短名）
     */
    public record RoomTemplate(
        String id,
        RoomType type,
        @Nullable String schematic,
        String origin,
        List<String> region,
        String spawn,
        @Nullable List<String> exit,
        List<Door> doors,
        Map<String, MonsterGroup> groups,
        int restSeconds,       // 波次休整兜底（-1=未配）：房内 TRIGGERED 组 delay<=0 时用
        List<String> onEnter,
        List<String> onLock,
        List<String> onClear,
        @Nullable RoomGen gen
    ) {
        /** gen 模式的房间生成覆写：feature（装饰/锚点方案）、radius 半径区间、tall 挑高。 */
        public record RoomGen(
            @Nullable String feature,
            @Nullable int[] radius,
            boolean tall
        ) {
            static @Nullable RoomGen load(@Nullable ConfigurationSection s) {
                if (s == null) return null;
                int[] r = null;
                var rl = s.getIntegerList("radius");
                if (rl.size() >= 2) r = new int[]{rl.get(0), rl.get(1)};
                else {
                    String str = s.getString("radius", null);
                    if (str != null && str.contains("..")) {
                        String[] v = str.split("\\.\\.");
                        try {
                            r = new int[]{Integer.parseInt(v[0].trim()),
                                Integer.parseInt(v[1].trim())};
                        } catch (NumberFormatException ignored) { }
                    }
                }
                return new RoomGen(s.getString("feature", null), r,
                    s.getBoolean("tall", false));
            }
        }

        static RoomTemplate load(String id, ConfigurationSection s) {
            RoomType type;
            try {
                type = RoomType.valueOf(s.getString("type", "combat").toUpperCase());
            } catch (IllegalArgumentException e) {
                type = RoomType.COMBAT;
            }
            List<Door> doors = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("doors")) {
                org.bukkit.configuration.MemoryConfiguration mem =
                    new org.bukkit.configuration.MemoryConfiguration();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null) mem.set(String.valueOf(e.getKey()), e.getValue());
                }
                doors.add(Door.load(mem));
            }
            Map<String, MonsterGroup> groups = new LinkedHashMap<>();
            ConfigurationSection gs = s.getConfigurationSection("groups");
            if (gs != null) {
                for (String key : gs.getKeys(false)) {
                    ConfigurationSection g = gs.getConfigurationSection(key);
                    if (g != null) groups.put(key, MonsterGroup.load(key, g));
                }
            }
            List<String> exit = s.getStringList("exit");
            return new RoomTemplate(
                id,
                type,
                s.getString("schematic", null),
                s.getString("origin", "0,0,0"),
                s.getStringList("region"),
                s.getString("spawn", "0,64,0"),
                exit.isEmpty() ? null : exit,
                doors,
                groups,
                s.getInt("wave-rest", s.getInt("wave_rest",
                    s.getInt("rest-seconds", s.getInt("rest_seconds", -1)))),
                s.getStringList("on-enter").isEmpty()
                    ? s.getStringList("on_enter") : s.getStringList("on-enter"),
                s.getStringList("on-lock").isEmpty()
                    ? s.getStringList("on_lock") : s.getStringList("on-lock"),
                s.getStringList("on-clear").isEmpty()
                    ? s.getStringList("on_clear") : s.getStringList("on-clear"),
                RoomGen.load(s.getConfigurationSection("gen"))
            );
        }
    }

    /** 门盒：锁门时填塞、清完还原。region 为相对房间 origin 的两角。 */
    public record Door(List<String> region, @Nullable String material) {
        static Door load(ConfigurationSection s) {
            return new Door(
                s.getStringList("region"),
                s.getString("material", null)
            );
        }
    }

    // ═══ 加载 ═══

    public static FloorConfig load(@Nullable ConfigurationSection floorsRoot,
                                   @Nullable ConfigurationSection roomsRoot) {
        Defaults defaults = Defaults.load(
            floorsRoot != null ? floorsRoot.getConfigurationSection("defaults") : null);
        List<Floor> floors = new ArrayList<>();
        if (floorsRoot != null) {
            int i = 0;
            for (Map<?, ?> m : floorsRoot.getMapList("floors")) {
                org.bukkit.configuration.MemoryConfiguration mem =
                    new org.bukkit.configuration.MemoryConfiguration();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null) mem.set(String.valueOf(e.getKey()), e.getValue());
                }
                floors.add(Floor.load(i++, mem, defaults));
            }
        }
        Map<String, RoomTemplate> rooms = new LinkedHashMap<>();
        if (roomsRoot != null) {
            ConfigurationSection rs = roomsRoot.getConfigurationSection("rooms");
            if (rs != null) {
                for (String key : rs.getKeys(false)) {
                    ConfigurationSection s = rs.getConfigurationSection(key);
                    if (s != null) rooms.put(key, RoomTemplate.load(key, s));
                }
            }
        }
        return new FloorConfig(defaults, floors, rooms);
    }
}
