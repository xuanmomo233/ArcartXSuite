package xuanmo.arcartxsuite.abyss.room;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.abyss.mob.MonsterGroupManager;
import xuanmo.arcartxsuite.abyss.mob.MonsterGroupManager.GroupState;
import xuanmo.arcartxsuite.abyss.template.FloorConfig;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.Door;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.Floor;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.RoomTemplate;
import xuanmo.arcartxsuite.abyss.template.FloorConfig.RoomType;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig.MonsterGroup;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig.MonsterSpawn;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig.SpawnTiming;
import xuanmo.arcartxsuite.abyss.world.SchematicWorldService;

/**
 * 楼层/房间管理器：多层房间地牢运行期核心。
 * <p>
 * 职责：
 * <ul>
 *   <li>实例启动时物化楼层房间链（region=绝对坐标；pool=随机抽房粘贴）</li>
 *   <li>tick：玩家进房检测 → 锁门（BARRIER 填充）+ 刷怪 → 组全清 → 开门激活出口</li>
 *   <li>边界拉回（FIGHTING 房逃逸兜底）+ 出口传送/层推进</li>
 *   <li>层末清完 → 下一层；最终层清完 → 通关回调</li>
 * </ul>
 * 坐标物化策略：房间一切坐标相对 origin；region 房 origin 默认 0,0,0（即绝对坐标），
 * pool 房 origin = 粘贴锚点。
 */
public final class RoomManager {

    // ═══ 回调（由 AbyssService 注入，避免循环依赖）═══

    public interface Callback {
        void runScript(AbyssInstance instance, List<String> lines,
                       String contextName, @Nullable Player trigger);
        void publishEvent(String topic, @Nullable Player player, Map<String, Object> data);
        void broadcast(AbyssInstance instance, String message);
        String msg(String key, String... kv);
        void onDungeonComplete(AbyssInstance instance);
        /** 实例变量写入（{floor}/{room} 等占位符）。 */
        void setVar(AbyssInstance instance, String key, Object value);
    }

    // ═══ 运行态 ═══

    public enum RoomState { SEALED, FIGHTING, CLEARED }

    /** 门盒运行态：锁门填充前的原始方块快照。 */
    private static final class DoorRuntime {
        final List<Block> blocks = new ArrayList<>();
        final Map<Location, Material> snapshot = new ConcurrentHashMap<>();
        final String material;
        DoorRuntime(String material) { this.material = material; }
    }

    /** 房间运行态（物化完成后的绝对坐标）。 */
    public static final class RoomRuntime {
        public final String id;            // f{floor}_r{index}_{模板id}，实例内唯一
        public final RoomTemplate tpl;
        public final int floorIndex;
        public final int roomIndex;
        public volatile RoomState state = RoomState.SEALED;
        Location boxMin, boxMax;
        Location spawn;
        Location exitMin, exitMax;         // 可空
        final List<DoorRuntime> doors = new ArrayList<>();
        final List<String> groupNames = new ArrayList<>();   // 物化组名
        /** 组名 → 原始 delaySeconds（锁门时延迟刷怪用）。 */
        final Map<String, Integer> groupDelays = new ConcurrentHashMap<>();
        final Set<UUID> inside = ConcurrentHashMap.newKeySet();
        /** 战斗房登记集合：进过房的玩家永驻（拉回判定用；inside 每 tick 进出变动不可靠）。 */
        final Set<UUID> enrolled = ConcurrentHashMap.newKeySet();
        volatile long clearedAt;
        volatile boolean advanceFired;

        RoomRuntime(String id, RoomTemplate tpl, int floorIndex, int roomIndex) {
            this.id = id;
            this.tpl = tpl;
            this.floorIndex = floorIndex;
            this.roomIndex = roomIndex;
        }
    }

    /** 层运行态。 */
    public static final class FloorRuntime {
        public final Floor floor;
        public final int index;
        public final List<RoomRuntime> rooms = new ArrayList<>();
        public volatile boolean cleared;
        FloorRuntime(Floor floor, int index) { this.floor = floor; this.index = index; }
    }

    /** 实例运行态。 */
    public static final class InstanceRuntime {
        public final List<FloorRuntime> floors = new ArrayList<>();
        public volatile int currentFloor;
        public volatile int currentRoom;
        InstanceRuntime() { }
    }

    private final Map<UUID, InstanceRuntime> states = new ConcurrentHashMap<>();
    /** instanceId@groupName → 房间（组完成反查用）。 */
    private final Map<String, RoomRuntime> groupToRoom = new ConcurrentHashMap<>();

    private final org.bukkit.plugin.Plugin plugin;
    private final java.util.logging.Logger logger;
    private final MonsterGroupManager mobGroups;
    private final SchematicWorldService schematics;
    private final xuanmo.arcartxsuite.api.scheduler.SchedulerAPI scheduler;
    private Callback callback;

    public RoomManager(org.bukkit.plugin.Plugin plugin,
                       xuanmo.arcartxsuite.api.scheduler.SchedulerAPI scheduler,
                       MonsterGroupManager mobGroups,
                       SchematicWorldService schematics,
                       java.util.logging.Logger logger) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.mobGroups = mobGroups;
        this.schematics = schematics;
        this.logger = logger;
    }

    public void setCallback(Callback cb) { this.callback = cb; }

    /** pool 拼接依赖的 WorldEdit/FAWE 是否可用。 */
    public boolean isWorldEditAvailable() {
        return schematics.isWorldEditAvailable();
    }

    public @Nullable InstanceRuntime stateOf(AbyssInstance instance) {
        return states.get(instance.getInstanceId());
    }

    /** 当前房间（可空）。 */
    public @Nullable RoomRuntime currentRoom(AbyssInstance instance) {
        InstanceRuntime rt = states.get(instance.getInstanceId());
        if (rt == null || rt.floors.isEmpty()
            || rt.currentFloor >= rt.floors.size()) return null;
        FloorRuntime f = rt.floors.get(rt.currentFloor);
        if (f.rooms.isEmpty() || rt.currentRoom >= f.rooms.size()) return null;
        return f.rooms.get(rt.currentRoom);
    }

    public @Nullable FloorRuntime currentFloor(AbyssInstance instance) {
        InstanceRuntime rt = states.get(instance.getInstanceId());
        if (rt == null || rt.floors.isEmpty()
            || rt.currentFloor >= rt.floors.size()) return null;
        return rt.floors.get(rt.currentFloor);
    }

    // ═══ 物化 ═══

    /**
     * 副本 start：按 floors 定义物化全部楼层房间链。
     * <p>世界须已就绪（instance.getWorld()）。pool 模式房间逐一粘贴；
     * 任一房粘贴失败 → 抛异常由上层 fail 处理。</p>
     */
    public void initInstance(AbyssInstance instance) {
        FloorConfig fc = instance.getTemplate().floors();
        if (fc == null || fc.floors().isEmpty()) return;
        World world = instance.getWorld();
        if (world == null) return;
        InstanceRuntime rt = new InstanceRuntime();
        states.put(instance.getInstanceId(), rt);

        int fi = 0;
        for (Floor floor : fc.floors()) {
            FloorRuntime fr = new FloorRuntime(floor, fi);
            rt.floors.add(fr);
            List<String> chain = resolveChain(floor);
            int ri = 0;
            int anchorX = 0, anchorZ = 0;
            for (String ref : chain) {
                RoomTemplate tpl = fc.rooms().get(ref);
                if (tpl == null) {
                    logger.warning("[abyss] floors 房间引用不存在: " + ref);
                    ri++;
                    continue;
                }
                String roomId = "f" + fi + "_r" + ri + "_" + ref;
                RoomRuntime room = new RoomRuntime(roomId, tpl, fi, ri);
                fr.rooms.add(room);

                int ox, oy, oz;
                if (floor.source() == FloorConfig.Source.POOL) {
                    ox = "z".equals(floor.direction()) ? 0 : anchorX;
                    oz = "z".equals(floor.direction()) ? anchorZ : 0;
                    oy = fc.defaults().pasteY();
                    File schem = resolveSchematic(instance, tpl);
                    if (schem == null || !schem.isFile()) {
                        throw new IllegalStateException(
                            "[abyss] pool 房间缺 schematic: " + ref
                                + "（地牢 " + instance.getTemplate().id() + "）");
                    }
                    if (!schematics.paste(schem, world, ox, oy, oz)) {
                        throw new IllegalStateException(
                            "[abyss] pool 房间粘贴失败: " + ref);
                    }
                    if ("z".equals(floor.direction())) anchorZ += fc.defaults().roomSpacing();
                    else anchorX += fc.defaults().roomSpacing();
                } else {
                    int[] o = parseVec(tpl.origin(), 0, 0, 0);
                    ox = o[0]; oy = o[1]; oz = o[2];
                }
                materializeRoom(instance, world, room, tpl, ox, oy, oz);
                ri++;
            }
            fi++;
        }

        if (callback != null && !rt.floors.isEmpty()) {
            callback.setVar(instance, "floor", 1);
            callback.setVar(instance, "floors_total", rt.floors.size());
            callback.setVar(instance, "room", 1);
            callback.setVar(instance, "floor_name", rt.floors.get(0).floor.name());
        }
    }

    /** 生成房间链：region=roomRefs+bossRoom 末尾；pool=随机抽 roomCount+bossRoom 末尾。 */
    private List<String> resolveChain(Floor floor) {
        List<String> chain;
        if (floor.source() == FloorConfig.Source.REGION) {
            chain = new ArrayList<>(floor.roomRefs());
        } else {
            List<String> bag = new ArrayList<>(floor.pool());
            Collections.shuffle(bag);
            int n = floor.roomCount() <= 0 ? bag.size() : Math.min(floor.roomCount(), bag.size());
            chain = new ArrayList<>(bag.subList(0, n));
        }
        if (floor.bossRoom() != null) chain.add(floor.bossRoom());
        return chain;
    }

    /** 物化单房：坐标偏移 + 门盒准备 + 怪组注册（组名 roomId:短名，坐标+origin）。 */
    private void materializeRoom(AbyssInstance instance, World world,
                                 RoomRuntime room, RoomTemplate tpl,
                                 int ox, int oy, int oz) {
        if (tpl.region().size() >= 2) {
            int[] a = parseVec(tpl.region().get(0), 0, 0, 0);
            int[] b = parseVec(tpl.region().get(1), 0, 0, 0);
            room.boxMin = new Location(world,
                Math.min(a[0], b[0]) + ox, Math.min(a[1], b[1]) + oy, Math.min(a[2], b[2]) + oz);
            room.boxMax = new Location(world,
                Math.max(a[0], b[0]) + ox, Math.max(a[1], b[1]) + oy, Math.max(a[2], b[2]) + oz);
        }
        Location spawn = resolveLoc(world, tpl.spawn(), ox, oy, oz);
        room.spawn = spawn != null ? spawn : new Location(world, ox, oy, oz);
        if (tpl.exit() != null && tpl.exit().size() >= 2) {
            int[] a = parseVec(tpl.exit().get(0), 0, 0, 0);
            int[] b = parseVec(tpl.exit().get(1), 0, 0, 0);
            room.exitMin = new Location(world,
                Math.min(a[0], b[0]) + ox, Math.min(a[1], b[1]) + oy, Math.min(a[2], b[2]) + oz);
            room.exitMax = new Location(world,
                Math.max(a[0], b[0]) + ox, Math.max(a[1], b[1]) + oy, Math.max(a[2], b[2]) + oz);
        }
        String defMat = instance.getTemplate().floors().defaults().lockMaterial();
        for (Door d : tpl.doors()) {
            if (d.region().size() < 2) continue;
            int[] a = parseVec(d.region().get(0), 0, 0, 0);
            int[] b = parseVec(d.region().get(1), 0, 0, 0);
            DoorRuntime dr = new DoorRuntime(d.material() != null ? d.material() : defMat);
            for (int x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++) {
                for (int y = Math.min(a[1], b[1]); y <= Math.max(a[1], b[1]); y++) {
                    for (int z = Math.min(a[2], b[2]); z <= Math.max(a[2], b[2]); z++) {
                        dr.blocks.add(world.getBlockAt(x + ox, y + oy, z + oz));
                    }
                }
            }
            room.doors.add(dr);
        }
        for (Map.Entry<String, MonsterGroup> e : tpl.groups().entrySet()) {
            MonsterGroup g = e.getValue();
            String runtimeName = room.id + ":" + e.getKey();
            List<MonsterSpawn> shifted = new ArrayList<>();
            for (MonsterSpawn ms : g.monsters()) {
                shifted.add(new MonsterSpawn(ms.mobId(),
                    shiftLoc(ms.location(), ox, oy, oz), ms.amount(), ms.level()));
            }
            List<String> deps = new ArrayList<>();
            for (String dep : g.triggerAfter()) {
                deps.add(dep.contains(":") ? dep : room.id + ":" + dep);
            }
            SpawnTiming timing = g.spawnTiming();
            // 房间内 AUTO_START/DELAYED 语义 = 锁门触发（由本管理器调度）
            if (timing == SpawnTiming.AUTO_START || timing == SpawnTiming.DELAYED) {
                timing = SpawnTiming.MANUAL;
            }
            room.groupDelays.put(runtimeName, g.delaySeconds());
            // 波次休整：组自身 rest-seconds 优先，房间 wave-rest 兜底
            int rest = g.restSeconds() >= 0 ? g.restSeconds() : tpl.restSeconds();
            mobGroups.registerRuntimeGroup(instance, new MonsterGroup(
                runtimeName, false, timing, g.delaySeconds(), rest, deps, shifted,
                g.completeWhenAliveBelow(), g.onPrespawn(),
                g.onStart(), g.onEnd(), g.plans()));
            room.groupNames.add(runtimeName);
            groupToRoom.put(groupKey(instance, runtimeName), room);
        }
    }

    private @Nullable File resolveSchematic(AbyssInstance instance, RoomTemplate tpl) {
        if (tpl.schematic() == null) return null;
        File f = new File(tpl.schematic());
        return f.isAbsolute() ? f : new File(instance.getTemplate().dir(), tpl.schematic());
    }

    // ═══ tick：进房检测 / 拉回 / 出口推进 ═══

    /** proximityTick 周期调用（1s）。 */
    public void tick(AbyssInstance instance) {
        InstanceRuntime rt = states.get(instance.getInstanceId());
        if (rt == null || instance.isFinished()
            || instance.getStatus() != AbyssStatus.ACTIVE) return;
        if (rt.currentFloor >= rt.floors.size()) return;
        FloorRuntime fr = rt.floors.get(rt.currentFloor);

        for (Player p : instance.getPlayers()) {
            if (instance.isPlayerCurrentlyDead(p)) continue;
            for (int ri = rt.currentRoom; ri < fr.rooms.size(); ri++) {
                RoomRuntime room = fr.rooms.get(ri);
                if (room.boxMin == null) continue;
                if (inBox(p.getLocation(), room.boxMin, room.boxMax)) {
                    if (room.inside.add(p.getUniqueId())) {
                        onEnterRoom(instance, rt, fr, room, p);
                    }
                } else {
                    room.inside.remove(p.getUniqueId());
                }
            }
        }

        RoomRuntime cur = currentRoom(instance);
        if (cur == null) return;

        // 边界拉回：FIGHTING 房逃逸兜底。
        // 用 enrolled（进过房即登记）而非 inside——inside 每 tick 随进出增删，
        // 离开盒子的玩家同一 tick 已被 remove，永不可能满足 inside&&!inBox。
        if (cur.state == RoomState.FIGHTING
            && instance.getTemplate().floors().defaults().borderPullback()) {
            for (Player p : instance.getPlayers()) {
                if (instance.isPlayerCurrentlyDead(p)) continue;
                if (cur.enrolled.contains(p.getUniqueId()) && cur.boxMin != null
                    && !inBox(p.getLocation(), cur.boxMin, cur.boxMax)) {
                    p.teleport(cur.spawn);
                }
            }
        }
        // 虚空防护：掉到当前房底部以下 → 拉回（pool 虚空世界/region 摔坑通用）。
        // 旁观玩家同样拉回——旁观态在虚空里会持续坠落且本检查跳过死亡玩家会让他们无限漂失
        if (cur.boxMin != null) {
            double floorY = cur.boxMin.getY() - 8;
            for (Player p : instance.getPlayers()) {
                if (p.getWorld().equals(cur.boxMin.getWorld())
                    && p.getLocation().getY() < floorY) {
                    p.teleport(cur.spawn);
                }
            }
        }

        // 出口推进（portal）：当前房已清 + exit 激活 + 玩家进入
        if (cur.state == RoomState.CLEARED && cur.exitMin != null && !cur.advanceFired) {
            for (Player p : instance.getPlayers()) {
                if (instance.isPlayerCurrentlyDead(p)) continue;
                if (inBox(p.getLocation(), cur.exitMin, cur.exitMax)) {
                    cur.advanceFired = true;
                    advance(instance, rt, fr, cur);
                    return;
                }
            }
        }
    }

    /** 玩家首次进入房间。 */
    private void onEnterRoom(AbyssInstance instance, InstanceRuntime rt,
                             FloorRuntime fr, RoomRuntime room, Player p) {
        // 进过房即登记（FIGHTING 拉回判定基准；非战斗房登记无副作用）
        room.enrolled.add(p.getUniqueId());
        if (room.floorIndex == rt.currentFloor && room.roomIndex > rt.currentRoom) {
            rt.currentRoom = room.roomIndex;
            if (callback != null) callback.setVar(instance, "room", room.roomIndex + 1);
        }
        if (callback != null) {
            callback.publishEvent("axs.abyss.room_enter", p, Map.of(
                "dungeon", instance.getTemplate().id(),
                "instance", instance.getInstanceId().toString(),
                "floor", room.floorIndex + 1,
                "floor_id", fr.floor.id(),
                "room", room.tpl.id(),
                "room_index", room.roomIndex,
                "room_type", room.tpl.type().name()));
            callback.runScript(instance, room.tpl.onEnter(), room.id, p);
        }

        if (room.state != RoomState.SEALED) return;
        boolean locks = room.tpl.type() == RoomType.COMBAT || room.tpl.type() == RoomType.BOSS;
        if (!locks || room.groupNames.isEmpty()) {
            clearRoom(instance, rt, fr, room);
            return;
        }
        room.state = RoomState.FIGHTING;
        lockDoors(room);
        if (callback != null) {
            callback.runScript(instance, room.tpl.onLock(), room.id, p);
            callback.broadcast(instance,
                callback.msg("dungeon.room-locked", "room", room.tpl.id()));
        }
        // 锁门触发房内组：delay>0 排延迟任务，否则立即刷
        for (String gn : room.groupNames) {
            int delay = room.groupDelays.getOrDefault(gn, 0);
            if (delay > 0) {
                instance.registerTask(scheduler.scheduleOnceSeconds(plugin,
                    () -> mobGroups.spawnGroup(instance, gn, null), delay));
            } else {
                mobGroups.spawnGroup(instance, gn, null);
            }
        }
    }

    /** 房内组全清回调（MonsterGroupManager → service → 这里）。 */
    public void onGroupComplete(AbyssInstance instance, String groupName) {
        RoomRuntime room = groupToRoom.get(groupKey(instance, groupName));
        if (room == null || room.state != RoomState.FIGHTING) return;
        InstanceRuntime rt = states.get(instance.getInstanceId());
        if (rt == null || room.floorIndex >= rt.floors.size()) return;
        for (String gn : room.groupNames) {
            if (mobGroups.groupState(instance, gn) != GroupState.COMPLETED) return;
        }
        clearRoom(instance, rt, rt.floors.get(room.floorIndex), room);
    }

    /** 房间清完：开门 + on-clear + 推进判定。 */
    private void clearRoom(AbyssInstance instance, InstanceRuntime rt,
                           FloorRuntime fr, RoomRuntime room) {
        if (room.state == RoomState.CLEARED) return;
        room.state = RoomState.CLEARED;
        room.clearedAt = System.currentTimeMillis();
        unlockDoors(room);
        if (callback != null) {
            callback.runScript(instance, room.tpl.onClear(), room.id, null);
            callback.publishEvent("axs.abyss.room_clear", null, Map.of(
                "dungeon", instance.getTemplate().id(),
                "instance", instance.getInstanceId().toString(),
                "floor", room.floorIndex + 1,
                "room", room.tpl.id(),
                "room_index", room.roomIndex));
            callback.broadcast(instance,
                callback.msg("dungeon.room-clear", "room", room.tpl.id()));
        }

        boolean isLastRoom = room.roomIndex >= fr.rooms.size() - 1;
        if (!isLastRoom) {
            if (fr.floor.advance() == FloorConfig.Advance.AUTO) {
                room.advanceFired = true;
                instance.registerTask(scheduler.scheduleOnceSeconds(plugin, () -> {
                    if (!instance.isFinished()) advance(instance, rt, fr, room);
                }, 3));
            }
            // portal 模式：exit 区域由 tick 检测
            return;
        }
        // 层末房清完 → 层清完
        fr.cleared = true;
        if (callback != null) {
            callback.runScript(instance, fr.floor.onClear(), fr.floor.id(), null);
            callback.publishEvent("axs.abyss.floor_clear", null, Map.of(
                "dungeon", instance.getTemplate().id(),
                "instance", instance.getInstanceId().toString(),
                "floor", room.floorIndex + 1,
                "floor_id", fr.floor.id()));
        }
        boolean isLastFloor = room.floorIndex >= rt.floors.size() - 1;
        if (isLastFloor) {
            if (callback != null) callback.onDungeonComplete(instance);
            return;
        }
        if (fr.floor.advance() == FloorConfig.Advance.AUTO) {
            room.advanceFired = true;
            advance(instance, rt, fr, room);
        } else {
            scheduleAdvanceTimeout(instance, rt, fr, room);
        }
    }

    /**
     * 推进：房→下一房 / 层末→下一层首房（全员传送）。
     *
     * @param cleared 刚清完的那个房（推进基准；可能落后/领先 currentRoom，以它为准）
     */
    private void advance(AbyssInstance instance, InstanceRuntime rt,
                         FloorRuntime fr, RoomRuntime cleared) {
        Location target;
        if (cleared.roomIndex >= fr.rooms.size() - 1) {
            int nf = cleared.floorIndex + 1;
            if (nf >= rt.floors.size()) return;
            rt.currentFloor = nf;
            rt.currentRoom = 0;
            FloorRuntime next = rt.floors.get(nf);
            target = floorSpawn(next, instance);
            if (callback != null) {
                callback.setVar(instance, "floor", nf + 1);
                callback.setVar(instance, "floor_name", next.floor.name());
                callback.setVar(instance, "room", 1);
                callback.publishEvent("axs.abyss.floor_enter", null, Map.of(
                    "dungeon", instance.getTemplate().id(),
                    "instance", instance.getInstanceId().toString(),
                    "floor", nf + 1,
                    "floor_id", next.floor.id()));
                callback.runScript(instance, next.floor.onEnter(), next.floor.id(), null);
                callback.broadcast(instance, callback.msg("dungeon.floor-enter",
                    "floor", next.floor.name()));
            }
        } else {
            int nr = cleared.roomIndex + 1;
            if (nr >= fr.rooms.size()) return;
            rt.currentRoom = nr;
            RoomRuntime next = fr.rooms.get(nr);
            target = next.spawn;
            if (callback != null) callback.setVar(instance, "room", nr + 1);
        }
        if (target == null) return;
        for (Player p : instance.getPlayers()) {
            if (instance.isPlayerCurrentlyDead(p)) continue;
            p.teleport(target);
        }
    }

    /** portal 兜底：层末房清完超时未踩出口 → 自动推进。 */
    private void scheduleAdvanceTimeout(AbyssInstance instance, InstanceRuntime rt,
                                        FloorRuntime fr, RoomRuntime room) {
        int timeout = fr.floor.advanceTimeout();
        if (timeout <= 0) return;
        instance.registerTask(scheduler.scheduleOnceSeconds(plugin, () -> {
            if (!instance.isFinished() && !room.advanceFired) {
                room.advanceFired = true;
                advance(instance, rt, fr, room);
            }
        }, timeout));
    }

    /** 层入口站位：floor.spawn（绝对）→ 缺省首房 spawn。 */
    private @Nullable Location floorSpawn(FloorRuntime fr, AbyssInstance instance) {
        if (fr.floor.spawn() != null && instance.getWorld() != null) {
            String[] v = fr.floor.spawn().split(",");
            if (v.length >= 3) {
                try {
                    return new Location(instance.getWorld(),
                        Double.parseDouble(v[0].trim()),
                        Double.parseDouble(v[1].trim()),
                        Double.parseDouble(v[2].trim()),
                        v.length > 3 ? Float.parseFloat(v[3].trim()) : 0f,
                        v.length > 4 ? Float.parseFloat(v[4].trim()) : 0f);
                } catch (NumberFormatException ignored) { }
            }
        }
        return fr.rooms.isEmpty() ? null : fr.rooms.get(0).spawn;
    }

    /** 锁门：门盒填塞并快照原方块。 */
    private void lockDoors(RoomRuntime room) {
        for (DoorRuntime d : room.doors) {
            Material mat = Material.matchMaterial(d.material.toUpperCase());
            if (mat == null) mat = Material.BARRIER;
            for (Block b : d.blocks) {
                d.snapshot.putIfAbsent(b.getLocation(), b.getType());
                b.setType(mat);
            }
        }
    }

    /** 开门：还原门盒原方块。 */
    private void unlockDoors(RoomRuntime room) {
        for (DoorRuntime d : room.doors) {
            for (Map.Entry<Location, Material> e : d.snapshot.entrySet()) {
                try { e.getKey().getBlock().setType(e.getValue()); } catch (Throwable ignored) { }
            }
        }
    }

    // ═══ 脚本动作支持 ═══

    /** 手动解锁/清房（room_unlock）。 */
    public boolean unlockRoom(AbyssInstance instance, String roomId) {
        InstanceRuntime rt = states.get(instance.getInstanceId());
        if (rt == null) return false;
        for (FloorRuntime fr : rt.floors) {
            for (RoomRuntime r : fr.rooms) {
                if (r.id.equals(roomId) || r.tpl.id().equals(roomId)) {
                    if (r.state != RoomState.CLEARED) {
                        clearRoom(instance, rt, fr, r);
                    } else {
                        unlockDoors(r);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** 手动锁门（room_lock，不清状态）。 */
    public boolean lockRoom(AbyssInstance instance, String roomId) {
        InstanceRuntime rt = states.get(instance.getInstanceId());
        if (rt == null) return false;
        for (FloorRuntime fr : rt.floors) {
            for (RoomRuntime r : fr.rooms) {
                if (r.id.equals(roomId) || r.tpl.id().equals(roomId)) {
                    lockDoors(r);
                    return true;
                }
            }
        }
        return false;
    }

    /** 强制推进一层（floor_next）。 */
    public void advanceFloor(AbyssInstance instance) {
        InstanceRuntime rt = states.get(instance.getInstanceId());
        if (rt == null || rt.floors.isEmpty()
            || rt.currentFloor >= rt.floors.size() - 1) return;
        FloorRuntime fr = rt.floors.get(rt.currentFloor);
        RoomRuntime last = fr.rooms.get(fr.rooms.size() - 1);
        advance(instance, rt, fr, last);
    }

    /** 当前房 spawn（重进/拉回用）。 */
    public @Nullable Location currentSpawn(AbyssInstance instance) {
        RoomRuntime cur = currentRoom(instance);
        if (cur != null) return cur.spawn;
        FloorRuntime fr = currentFloor(instance);
        return fr != null ? floorSpawn(fr, instance) : null;
    }

    public void cleanup(AbyssInstance instance) {
        InstanceRuntime rt = states.remove(instance.getInstanceId());
        if (rt != null) {
            for (FloorRuntime fr : rt.floors) {
                for (RoomRuntime r : fr.rooms) {
                    for (String gn : r.groupNames) {
                        groupToRoom.remove(groupKey(instance, gn));
                    }
                }
            }
        }
    }

    public void shutdown() {
        states.clear();
        groupToRoom.clear();
    }

    // ═══ 坐标工具 ═══

    private static String groupKey(AbyssInstance i, String groupName) {
        return i.getInstanceId() + "@" + groupName;
    }

    /** "x,y,z" → int[3]；失败回退默认。 */
    private static int[] parseVec(String s, int dx, int dy, int dz) {
        if (s == null) return new int[]{dx, dy, dz};
        String[] v = s.split(",");
        if (v.length < 3) return new int[]{dx, dy, dz};
        try {
            return new int[]{
                (int) Double.parseDouble(v[0].trim()),
                (int) Double.parseDouble(v[1].trim()),
                (int) Double.parseDouble(v[2].trim())};
        } catch (NumberFormatException e) {
            return new int[]{dx, dy, dz};
        }
    }

    /** "x,y,z[,yaw,pitch]" 相对原点偏移 → Location。 */
    private static @Nullable Location resolveLoc(World w, String s, int ox, int oy, int oz) {
        if (s == null) return null;
        String[] v = s.split(",");
        if (v.length < 3) return null;
        try {
            return new Location(w,
                Double.parseDouble(v[0].trim()) + ox,
                Double.parseDouble(v[1].trim()) + oy,
                Double.parseDouble(v[2].trim()) + oz,
                v.length > 3 ? Float.parseFloat(v[3].trim()) : 0f,
                v.length > 4 ? Float.parseFloat(v[4].trim()) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "x,y,z[,yaw,pitch]" 偏移 → 新字符串（怪组物化用）。 */
    private static String shiftLoc(String loc, int ox, int oy, int oz) {
        if (loc == null) return "0,64,0";
        String[] v = loc.split(",");
        if (v.length < 3) return loc;
        try {
            double x = Double.parseDouble(v[0].trim()) + ox;
            double y = Double.parseDouble(v[1].trim()) + oy;
            double z = Double.parseDouble(v[2].trim()) + oz;
            String tail = v.length > 3 ? "," + v[3].trim() : "";
            tail += v.length > 4 ? "," + v[4].trim() : "";
            return fmt(x) + "," + fmt(y) + "," + fmt(z) + tail;
        } catch (NumberFormatException e) {
            return loc;
        }
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** box 判定：max 角 +1（region 两角指方块本身）。 */
    private static boolean inBox(Location l, Location min, Location max) {
        if (l.getWorld() == null || !l.getWorld().equals(min.getWorld())) return false;
        return l.getX() >= min.getX() && l.getX() <= max.getX() + 1
            && l.getY() >= min.getY() && l.getY() <= max.getY() + 1
            && l.getZ() >= min.getZ() && l.getZ() <= max.getZ() + 1;
    }
}
