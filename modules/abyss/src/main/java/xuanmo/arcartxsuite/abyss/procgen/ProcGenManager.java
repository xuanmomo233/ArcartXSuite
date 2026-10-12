package xuanmo.arcartxsuite.abyss.procgen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.procgen.LayoutGenerator.DoorBox;
import xuanmo.arcartxsuite.abyss.procgen.LayoutGenerator.Layout;
import xuanmo.arcartxsuite.abyss.procgen.ProcGenMaterializer.RoomRuntime;
import xuanmo.arcartxsuite.abyss.procgen.ProcGenMaterializer.Result;
import xuanmo.arcartxsuite.abyss.procgen.ProcGenRoomAsset.RoomKind;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.template.InteractConfig.Interact;
import xuanmo.arcartxsuite.abyss.template.InteractConfig.InteractType;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig.MonsterGroup;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig.MonsterSpawn;
import xuanmo.arcartxsuite.abyss.template.MonsterConfig.SpawnTiming;
import xuanmo.arcartxsuite.abyss.template.ProcGenConfig;

/**
 * 随机地牢运行管理器（procgen.yml 启用）。
 *
 * <p>生命周期：</p>
 * <pre>
 * materialize  世界就绪后物化：生成布局 → 铺房+走廊 → 注册怪组/交互/NPC
 * tick        1s 慢扫：玩家所在房间检测 → 进房触发（封门/刷怪/on-enter）
 * onGroupComplete  波次链：怪组清完 → 延迟下一波 → 全波清完 → 开门+清房
 * </pre>
 *
 * <p>清房类型：</p>
 * <ul>
 *   <li>战斗房（有波次的 combat/elite/boss）：封门 → 波次 → 清完开门</li>
 *   <li>manual-clear 事件房：无怪，等 {@code procgen_clear_room} 动作开门</li>
 *   <li>商店/宝藏/休息等非战斗房：进房即记已清（计入评级探索分）</li>
 * </ul>
 */
public final class ProcGenManager {

    /** 房运行态。 */
    private enum RoomState { WAITING, FIGHTING, CLEARED }

    /** 实例运行态。 */
    private static final class InstState {
        Layout layout;
        List<RoomRuntime> rooms;
        ProcGenConfig cfg;
        /** 房间运行态：roomIndex → state。 */
        final Map<Integer, RoomState> roomState = new HashMap<>();
        final Map<Integer, Long> clearedAt = new HashMap<>();
        /** 每房当前波次（1 基）。 */
        final Map<Integer, Integer> wave = new HashMap<>();
        /** 每房词缀 id（进房 roll 一次）。 */
        final Map<Integer, String> modifier = new HashMap<>();
        /** 每房限时挑战截止时间戳（0=无）。 */
        final Map<Integer, Long> timedEnd = new HashMap<>();
        /** 连战加赏已用标记。 */
        final Map<Integer, Boolean> encoreUsed = new HashMap<>();
        /** 每房刷怪组序号（组名去重，encore/reinforce 可重放同波）。 */
        final Map<Integer, Integer> spawnSeq = new HashMap<>();
        /** 已进过房的玩家（on-enter 一次）。 */
        final Map<Integer, java.util.Set<UUID>> entered = new HashMap<>();
    }

    private final AbyssService service;
    private final RoomSetLibrary library;
    private final Logger logger;
    private final Map<UUID, InstState> states = new ConcurrentHashMap<>();

    public ProcGenManager(AbyssService service, RoomSetLibrary library, Logger logger) {
        this.service = service;
        this.library = library;
        this.logger = logger;
    }

    public RoomSetLibrary library() { return library; }

    // ── 物化（continueWorldReady 阶段，world_init 之前） ──

    /**
     * 物化随机地牢。
     * @return 起点房出生点；null = 配置/房间集问题（回退模板 spawn）
     */
    public @Nullable Location materialize(AbyssInstance instance, World world) {
        AbyssTemplate t = instance.getTemplate();
        ProcGenConfig cfg = t.procgen();
        if (cfg == null) return null;
        Map<RoomKind, List<ProcGenRoomAsset>> byKind = library.byKind(cfg.roomSet());
        if (byKind.isEmpty()) {
            logger.warning("[abyss] procgen 房间集为空: " + cfg.roomSet());
            return null;
        }
        // 种子
        long seed = switch (cfg.seedMode()) {
            case DAILY -> Long.parseLong(LocalDate.now()
                .format(DateTimeFormatter.BASIC_ISO_DATE));
            case FIXED -> cfg.seedValue();
            default -> new java.util.Random().nextLong();
        };
        // 布局原点：以 (0, baseY, 0) 为中心向四周铺，负坐标从 -pitch*N 起
        Layout layout = LayoutGenerator.generate(byKind, cfg, seed, 64, 0, 0);
        if (layout == null) {
            logger.warning("[abyss] procgen 布局生成失败: " + t.id());
            return null;
        }
        Result res = new ProcGenMaterializer(logger).materialize(world, layout, cfg);
        if (res == null) return null;

        InstState st = new InstState();
        st.layout = layout;
        st.rooms = res.rooms;
        st.cfg = cfg;
        for (int i = 0; i < res.rooms.size(); i++) {
            st.roomState.put(i, RoomState.WAITING);
        }
        states.put(instance.getInstanceId(), st);
        instance.getVariables().put("procgen_seed", seed);
        instance.getVariables().put("procgen_rooms", res.rooms.size());
        logger.info("[abyss] procgen 物化完成: " + t.id() + " 房间=" + res.rooms.size()
            + " seed=" + seed);
        return res.spawn;
    }

    /**
     * 实例 ACTIVE 后调用（startInstance 内、mobGroups/interacts 初始化完成
     * 之后——它们的 init 会整体覆盖注册表，物化期注册会被抹掉）：
     * 注册房间怪组/宝箱交互/引用交互/NPC。
     */
    public void initRuntime(AbyssInstance instance) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null) return;
        AbyssTemplate t = instance.getTemplate();
        World world = instance.getWorld();
        for (int i = 0; i < st.rooms.size(); i++) {
            RoomRuntime rr = st.rooms.get(i);
            var asset = rr.placed.asset;
            // 宝箱交互（RIGHT_CLICK_BLOCK → procgen_chest 动作判 encore/开箱）
            int ci = 0;
            for (var c : asset.chests()) {
                int[] p = rr.placed.toWorld(c.x(), c.y(), c.z());
                service.interacts().registerRuntime(instance, new Interact(
                    "pg_chest_" + i + "_" + (ci++),
                    InteractType.RIGHT_CLICK_BLOCK,
                    p[0] + "," + p[1] + "," + p[2],
                    List.of(), null, null, null, false, null,
                    List.of(),
                    List.of("procgen_chest " + i + " " + c.ref())));
            }
            // 房间交互（interacts.yml 定义引用，克隆定义换世界坐标）
            for (var ir : asset.interacts()) {
                Interact tpl = t.interacts().interacts().get(ir.ref());
                if (tpl == null) {
                    logger.warning("[abyss] procgen 房间 " + asset.name()
                        + " 引用了不存在的交互: " + ir.ref());
                    continue;
                }
                int[] p = rr.placed.toWorld(ir.x(), ir.y(), ir.z());
                // region 清空走点匹配——模板 region 是绝对坐标，房内坐标已换算成点
                service.interacts().registerRuntime(instance, new Interact(
                    "pg_" + i + "_" + ir.ref(),
                    tpl.type(),
                    p[0] + "," + p[1] + "," + p[2],
                    List.of(), tpl.requiredItem(), tpl.requiredBlock(),
                    tpl.entityName(), tpl.once(), tpl.failMessage(),
                    tpl.conditions(), tpl.script()));
            }
            // NPC（房内相对坐标 → 世界落点；同名 NPC 多房共存需改名）
            if (world != null) {
                for (var n : asset.npcs()) {
                    var tpl = t.npcs().npcs().get(n.ref());
                    if (tpl == null) {
                        logger.warning("[abyss] procgen 房间 " + asset.name()
                            + " 引用了不存在的 NPC: " + n.ref());
                        continue;
                    }
                    int[] p = rr.placed.toWorld(n.x(), n.y(), n.z());
                    // spawnKey 带房间后缀防 dedup；点击解析仍走模板基名
                    service.npcs().spawnAt(instance, tpl,
                        new Location(world, p[0] + 0.5, p[1], p[2] + 0.5),
                        n.ref() + "@pg" + i);
                }
            }
        }
    }

    // ── 1s 慢扫（proximityTick 调用） ──

    public void tick(AbyssInstance instance) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null || instance.getWorld() == null || instance.isFinished()) return;
        for (Player p : instance.getPlayers()) {
            if (!p.isOnline()) continue;
            for (int i = 0; i < st.rooms.size(); i++) {
                RoomRuntime rr = st.rooms.get(i);
                if (!rr.contains(p.getLocation())) continue;
                if (st.entered.computeIfAbsent(i, k -> new java.util.HashSet<>())
                    .add(p.getUniqueId())) {
                    onRoomEnter(instance, st, i, p);
                }
                break;  // 一名玩家只可能在一间房
            }
        }
    }

    /** 怪组完成路由（service.onGroupComplete 调用）。 */
    public void onGroupComplete(AbyssInstance instance, String groupName) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null) return;
        int[] pw = parseGroup(groupName);
        if (pw == null) return;
        RoomRuntime rr = st.rooms.get(pw[0]);
        if (rr == null || st.roomState.get(pw[0]) == RoomState.CLEARED) return;
        int nextWave = pw[1] + 1;
        if (nextWave < rr.placed.asset.waves().length
            && rr.placed.asset.waves()[nextWave] != null
            && !rr.placed.asset.waves()[nextWave].isEmpty()) {
            // 下一波：预兆 + wave-delay
            runLines(instance, rr.placed.asset.onWave()
                .getOrDefault(nextWave + 1, List.of()), null);
            telegraph(instance, rr, nextWave);
            service.scheduleOnce(instance,
                () -> spawnWave(instance, st, pw[0], nextWave),
                Math.max(1, st.cfg.waveDelayTicks()));
        } else {
            clearRoom(instance, st, pw[0]);
        }
    }

    /** procgen_clear_room 动作（触发者所在的手动清房房完成）。 */
    public boolean clearCurrentRoom(AbyssInstance instance, Player trigger) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null || trigger == null) return false;
        for (int i = 0; i < st.rooms.size(); i++) {
            if (st.rooms.get(i).contains(trigger.getLocation())) {
                if (st.roomState.get(i) == RoomState.CLEARED) return false;
                clearRoom(instance, st, i);
                return true;
            }
        }
        return false;
    }

    /** 开/封门动作（procgen_open_doors / procgen_seal_doors，作用于触发者所在房）。 */
    public boolean doorsAt(AbyssInstance instance, Player trigger, boolean open) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null || trigger == null) return false;
        for (int i = 0; i < st.rooms.size(); i++) {
            RoomRuntime rr = st.rooms.get(i);
            if (rr.contains(trigger.getLocation())) {
                for (DoorBox box : rr.doorBoxes) {
                    fillDoor(instance.getWorld(), box,
                        open ? Material.AIR : st.cfg.sealMaterial());
                }
                return true;
            }
        }
        return false;
    }

    /** 强制下一波（procgen_next_wave 动作）。 */
    public boolean nextWaveAt(AbyssInstance instance, Player trigger) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null || trigger == null) return false;
        for (int i = 0; i < st.rooms.size(); i++) {
            RoomRuntime rr = st.rooms.get(i);
            if (rr.contains(trigger.getLocation())
                && st.roomState.get(i) == RoomState.FIGHTING) {
                int w = st.wave.getOrDefault(i, 0) + 1;
                if (w < rr.placed.asset.waves().length) {
                    spawnWave(instance, st, i, w);
                    return true;
                }
            }
        }
        return false;
    }

    /** 宝箱交互入口（procgen_chest 动作）：潜行+encore → 连战；否则开箱。 */
    public boolean onChestInteract(AbyssInstance instance, Player player,
                                   int roomIdx, String chestId) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null || roomIdx >= st.rooms.size()) return false;
        RoomRuntime rr = st.rooms.get(roomIdx);
        // 连战加赏：清房后潜行右键宝箱 → 再战一轮（每房一次）
        if (player.isSneaking() && st.cfg.challenges().encore()
            && st.roomState.get(roomIdx) == RoomState.CLEARED
            && !st.encoreUsed.getOrDefault(roomIdx, false)
            && hasWaves(rr)) {
            st.encoreUsed.put(roomIdx, true);
            st.roomState.put(roomIdx, RoomState.FIGHTING);
            sealRoom(instance, rr, st.cfg);
            telegraph(instance, rr, 0);
            service.scheduleOnce(instance,
                () -> spawnWave(instance, st, roomIdx, 0),
                Math.max(1, st.cfg.waveDelayTicks()));
            player.sendMessage(service.msg("procgen.encore-start"));
            return true;
        }
        // 普通开箱：转 open_chest_ui 语义（由动作层直接调）
        return false;   // 返回 false → 动作层 fallback 到 open_chest_ui
    }

    /** 增兵：向指定房重放一波怪（reinforce 动作；wave 为 0 基；不动波次链）。 */
    public void reinforce(AbyssInstance instance, int roomIdx, int wave) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null || roomIdx < 0 || roomIdx >= st.rooms.size()) return;
        RoomRuntime rr = st.rooms.get(roomIdx);
        if (wave < 0 || wave >= rr.placed.asset.waves().length) return;
        spawnGroupNow(instance, st, roomIdx, wave);
    }

    /** 房间是否已清（阶段条件 room_cleared: 用；按索引或构件名匹配）。 */
    public boolean isRoomCleared(AbyssInstance instance, int roomIdx) {
        InstState st = states.get(instance.getInstanceId());
        return st != null && st.roomState.get(roomIdx) == RoomState.CLEARED;
    }

    /** 按构件名或 "房idx" 数字查已清状态；无 procgen 态返回 null（交回 floors 判定）。 */
    public @Nullable Boolean roomClearedByName(AbyssInstance instance, String ref) {
        InstState st = states.get(instance.getInstanceId());
        if (st == null) return null;
        try {
            return st.roomState.get(Integer.parseInt(ref.trim())) == RoomState.CLEARED;
        } catch (NumberFormatException ignored) { }
        for (int i = 0; i < st.rooms.size(); i++) {
            if (st.rooms.get(i).placed.asset.name().equalsIgnoreCase(ref.trim())) {
                return st.roomState.get(i) == RoomState.CLEARED;
            }
        }
        return false;
    }

    public void cleanup(AbyssInstance instance) {
        states.remove(instance.getInstanceId());
    }

    // ── 内部 ─────────────────────────────────────────────

    private void onRoomEnter(AbyssInstance instance, InstState st, int ri, Player p) {
        RoomRuntime rr = st.rooms.get(ri);
        ProcGenRoomAsset asset = rr.placed.asset;
        var k = asset.kind();
        // 房间级事件只在"首次有人进房"（WAITING）触发——后续玩家进房不重roll词缀
        boolean firstEver = st.roomState.get(ri) == RoomState.WAITING;
        if (firstEver) {
            rollModifier(instance, st, ri, p);
            runLines(instance, asset.onEnter(), p);
        }
        if (k == RoomKind.START) return;

        boolean combat = hasWaves(rr) && !asset.manualClear();
        if (combat && st.roomState.get(ri) == RoomState.WAITING) {
            st.roomState.put(ri, RoomState.FIGHTING);
            st.clearedAt.remove(ri);
            // 封门 + 预兆 + 首波
            if (st.cfg.sealCombatDoors()) sealRoom(instance, rr, st.cfg);
            telegraph(instance, rr, 0);
            // 限时挑战 roll
            if (st.cfg.challenges().timedEnabled()
                && Math.random() < st.cfg.challenges().timedChance()) {
                st.timedEnd.put(ri, System.currentTimeMillis()
                    + st.cfg.challenges().timedSeconds() * 1000L);
                broadcastIn(instance, msg("procgen.timed-challenge",
                    "seconds", String.valueOf(st.cfg.challenges().timedSeconds())));
            }
            service.scheduleOnce(instance,
                () -> spawnWave(instance, st, ri, 0),
                Math.max(1, st.cfg.telegraph().delayTicks()));
        } else if (!combat && st.roomState.get(ri) == RoomState.WAITING) {
            // 非战斗/手动房：进房即清（事件房靠 procgen_clear_room，不在此列——
            // manual-clear 房不进战斗也不自动清，等动作）
            if (!asset.manualClear()) {
                clearRoom(instance, st, ri);
            } else {
                st.roomState.put(ri, RoomState.FIGHTING); // 等 procgen_clear_room
            }
        }
    }

    private void clearRoom(AbyssInstance instance, InstState st, int ri) {
        RoomRuntime rr = st.rooms.get(ri);
        if (rr == null || st.roomState.get(ri) == RoomState.CLEARED) return;
        st.roomState.put(ri, RoomState.CLEARED);
        st.clearedAt.put(ri, System.currentTimeMillis());
        // 开门
        for (DoorBox box : rr.doorBoxes) {
            fillDoor(instance.getWorld(), box, Material.AIR);
        }
        runLines(instance, rr.placed.asset.onClear(), null);
        broadcastIn(instance, msg("procgen.room-clear"));
        // 限时挑战达成判定
        Long end = st.timedEnd.remove(ri);
        if (end != null && System.currentTimeMillis() <= end
            && st.cfg.challenges().reward() != null) {
            for (Player p : instance.getPlayers()) {
                if (rr.contains(p.getLocation())) {
                    service.rewards().grant(p, st.cfg.challenges().reward(), instance);
                }
            }
            broadcastIn(instance, msg("procgen.challenge-clear"));
        }
        // Boss 房清完 → 评级 + 通关
        if (rr.placed.asset.kind() == RoomKind.BOSS) {
            gradeAndFinish(instance, st);
        }
        service.publishEvent("axs.abyss.procgen_room_clear", null,
            Map.of("room", rr.placed.asset.name(), "index", ri));
    }

    private void spawnWave(AbyssInstance instance, InstState st, int ri, int wave) {
        if (instance.isFinished() || st.roomState.get(ri) == RoomState.CLEARED) return;
        RoomRuntime rr = st.rooms.get(ri);
        List<ProcGenRoomAsset.RoomMob> mobs = rr.placed.asset.waves()[wave];
        if (mobs == null || mobs.isEmpty()) return;
        st.wave.put(ri, wave);
        spawnGroupNow(instance, st, ri, wave);
    }

    /** 注册并生成该房该波怪组（seq 递增保证组名唯一，可重放同波）。 */
    private void spawnGroupNow(AbyssInstance instance, InstState st,
                               int ri, int wave) {
        RoomRuntime rr = st.rooms.get(ri);
        List<ProcGenRoomAsset.RoomMob> mobs = rr.placed.asset.waves()[wave];
        if (mobs == null || mobs.isEmpty()) return;
        // 等级 = mob-level 基础 + 精英加成 + 词缀加成
        double levelBase = parseLevel(st.cfg.mobLevel());
        if (rr.placed.asset.kind() == RoomKind.ELITE) {
            levelBase += st.cfg.eliteLevelBonus();
        }
        String modId = st.modifier.get(ri);
        if (modId != null) {
            var def = st.cfg.modifierDefs().get(modId);
            if (def != null) levelBase += def.mobLevelBonus();
        }
        // 每次生成注册新组名——spawnGroup 拒绝 COMPLETED 组，
        // 连战加赏/增兵必须走全新组才能重放同一波
        int seq = st.spawnSeq.merge(ri, 1, Integer::sum);
        List<MonsterSpawn> spawns = new ArrayList<>();
        for (var m : mobs) {
            int[] p = rr.placed.toWorld(m.x(), m.y(), m.z());
            spawns.add(new MonsterSpawn(m.mobId(),
                p[0] + "," + p[1] + "," + p[2], 1, levelBase));
        }
        String gname = groupName(ri, wave, seq);
        service.mobGroups().registerRuntimeGroup(instance,
            new MonsterGroup(gname, false, SpawnTiming.MANUAL,
                0, -1, List.of(), spawns, 0,
                List.of(), List.of(), List.of(), List.of()));
        service.mobGroups().spawnGroup(instance, gname, null);
    }

    private void telegraph(AbyssInstance instance, RoomRuntime rr, int wave) {
        var tg = currentCfg(instance).telegraph();
        if (!tg.enabled()) return;
        World w = instance.getWorld();
        if (w == null) return;
        // 每个怪点打预兆粒子+音效
        List<ProcGenRoomAsset.RoomMob> mobs = wave < rr.placed.asset.waves().length
            ? rr.placed.asset.waves()[wave] : List.of();
        for (var m : mobs) {
            int[] p = rr.placed.toWorld(m.x(), m.y(), m.z());
            try {
                org.bukkit.Particle particle = org.bukkit.Particle.valueOf(
                    tg.particle().toUpperCase());
                w.spawnParticle(particle, p[0] + 0.5, p[1] + 1, p[2] + 0.5,
                    30, 0.3, 0.5, 0.3, 0.02);
            } catch (IllegalArgumentException ignored) { }
            try {
                w.playSound(new Location(w, p[0] + 0.5, p[1], p[2] + 0.5),
                    tg.sound(), tg.volume(), tg.pitch());
            } catch (Throwable ignored) { }
        }
    }

    private void sealRoom(AbyssInstance instance, RoomRuntime rr, ProcGenConfig cfg) {
        for (DoorBox box : rr.doorBoxes) {
            fillDoor(instance.getWorld(), box, cfg.sealMaterial());
        }
    }

    private static void fillDoor(@Nullable World world, DoorBox box, Material mat) {
        if (world == null) return;
        for (int x = box.x1; x <= box.x2; x++) {
            for (int y = box.y1; y <= box.y2; y++) {
                for (int z = box.z1; z <= box.z2; z++) {
                    world.getBlockAt(x, y, z).setType(mat, false);
                }
            }
        }
    }

    private void rollModifier(AbyssInstance instance, InstState st, int ri, Player p) {
        var cfg = st.cfg;
        var asset = st.rooms.get(ri).placed.asset;
        if (cfg.modifiers().chance() <= 0) return;
        if (asset.kind() == RoomKind.BOSS && !cfg.modifiers().includeBoss()) return;
        if (asset.kind() == RoomKind.START) return;
        Map<String, ProcGenConfig.ModifierDef> defs = cfg.modifierDefs();
        if (defs.isEmpty()) return;
        // pool 过滤 + 权重
        List<ProcGenConfig.ModifierDef> pool = new ArrayList<>();
        for (var d : defs.values()) {
            if (cfg.modifiers().pool().isEmpty()
                || cfg.modifiers().pool().contains(d.id())) pool.add(d);
        }
        if (pool.isEmpty() || Math.random() >= cfg.modifiers().chance()) return;
        ProcGenConfig.ModifierDef def = pool.get(
            new java.util.Random().nextInt(pool.size()));
        st.modifier.put(ri, def.id());
        runLines(instance, def.onEnter(), p);
        if (def.announce() != null) {
            broadcastIn(instance, def.announce());
        }
        instance.getVariables().put("procgen_modifier_" + ri, def.id());
    }

    private void gradeAndFinish(AbyssInstance instance, InstState st) {
        var g = st.cfg.grading();
        // 探索分：已清房/可清房（除 start）
        int explorable = 0, cleared = 0;
        for (int i = 0; i < st.rooms.size(); i++) {
            if (st.rooms.get(i).placed.asset.kind() == RoomKind.START) continue;
            explorable++;
            if (st.roomState.get(i) == RoomState.CLEARED) cleared++;
        }
        int explore = explorable == 0 ? 50
            : (int) Math.round(50.0 * cleared / explorable);
        // 生存分：全队零死 30，每死一次 -10 见底
        int deaths = instance.getPlayerUUIDs().stream()
            .mapToInt(instance::getDeaths).sum();
        int survive = Math.max(0, 30 - deaths * 10);
        // 速度分：总用时 ≤ par 得 20，超出线性衰减
        long elapsed = (System.currentTimeMillis() - instance.getCreateTime()) / 1000;
        int par = Math.max(1, g.parSecondsPerRoom() * Math.max(1, explorable));
        int speed = elapsed <= par ? 20
            : Math.max(0, 20 - (int) ((elapsed - par) * 20 / Math.max(1, par)));
        int score = explore + survive + speed;
        String grade = score >= 90 ? "S" : score >= 75 ? "A" : score >= 60 ? "B" : "C";
        instance.getVariables().put("procgen_grade", grade);
        instance.getVariables().put("procgen_score", score);
        runLines(instance, g.scripts().getOrDefault(grade, List.of()), null);
        broadcastIn(instance, msg("procgen.grade", "grade", grade,
            "score", String.valueOf(score)));
        // Boss 清完 → 副本通关
        service.completeDungeon(instance);
    }

    private static boolean hasWaves(RoomRuntime rr) {
        for (List<ProcGenRoomAsset.RoomMob> w : rr.placed.asset.waves()) {
            if (w != null && !w.isEmpty()) return true;
        }
        return false;
    }

    private static String groupName(int roomIdx, int wave, int seq) {
        return "pg_" + roomIdx + "_w" + (wave + 1) + "_s" + seq;
    }

    /** "pg_<ri>_w<n>_s<seq>" → [ri, n-1]。 */
    private static int[] parseGroup(String name) {
        if (!name.startsWith("pg_")) return null;
        String body = name.substring(3);
        int si = body.indexOf("_s");
        if (si > 0) body = body.substring(0, si);
        String[] p = body.split("_w");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ProcGenConfig currentCfg(AbyssInstance instance) {
        InstState st = states.get(instance.getInstanceId());
        return st != null ? st.cfg : instance.getTemplate().procgen();
    }

    private void broadcastIn(AbyssInstance instance, String text) {
        instance.getPlayers().forEach(pl -> pl.sendMessage(text));
    }

    private String msg(String key, String... kv) {
        return service.msg(key, kv);
    }

    private void runLines(AbyssInstance instance, List<String> lines,
                          @Nullable Player trigger) {
        if (lines.isEmpty()) return;
        service.runScriptPublic(instance, lines, trigger);
    }

    private static double parseLevel(String s) {
        if (s == null) return 1.0;
        if (s.contains("-")) {
            String[] p = s.split("-", 2);
            double lo = parseDouble(p[0], 1), hi = parseDouble(p[1], lo);
            return lo + Math.random() * Math.max(0, hi - lo);
        }
        return parseDouble(s, 1);
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); }
        catch (Exception e) { return def; }
    }
}

