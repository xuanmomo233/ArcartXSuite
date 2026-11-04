package xuanmo.arcartxsuite.abyss.design;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.template.FloorConfig;

/**
 * 设计模式管理器：设计会话生命周期 + 对象快照 CRUD + 归属推断 + 标记落地。
 * <p>
 * 对象快照直接读 YAML 节的 {@code getValues(false)}（kebab/snake 键双读归一），
 * 编辑只动快照；保存由 {@link DesignWriter} 整节写回（快照即全量，天然无损）。
 * <p>
 * UI 是纯渲染器——字段 schema 由 {@link DesignObject#schema} 服务端下发。
 */
public final class DesignManager {
    private java.util.function.BiFunction<AbyssInstance, Location,
        xuanmo.arcartxsuite.abyss.procgen.ProcGenManager.DesignAnchor> designAnchor;

    public void setDesignAnchor(java.util.function.BiFunction<AbyssInstance, Location,
            xuanmo.arcartxsuite.abyss.procgen.ProcGenManager.DesignAnchor> resolver) {
        designAnchor = resolver;
    }

    /** 流程节点（主面板顶部节点链）。 */
    public record Node(String key, String label, int depth, int objectCount) { }

    private final Logger logger;
    private final BiConsumer<Player, String> messager;

    /** 玩家 → 会话。 */
    private final Map<UUID, DesignSession> sessions = new ConcurrentHashMap<>();
    /** 实例id → 会话（实例销毁时清理）。 */
    private final Map<UUID, DesignSession> byInstance = new ConcurrentHashMap<>();
    /** 玩家 → 已下发的世界贴图 id 集（diff 清理用）。 */
    private final Map<UUID, Set<String>> markers = new ConcurrentHashMap<>();

    /** UI 回调（打开主面板/编辑面板/副面板、提示刷新），由 UiService 注入。 */
    public interface Ui {
        void openMain(Player p, DesignSession s);
        void openEdit(Player p, DesignSession s, String objKey);
        void openPick(Player p, DesignSession s, String nodeKey);
        void refreshMain(Player p, DesignSession s);
        void closeAll(Player p);
    }
    private volatile @Nullable Ui ui;
    /** 世界贴图桥（R3 世界内对象标记；ArcartX 缺席时为 null 自动跳过）。 */
    private volatile @Nullable xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI textureBridge;

    public DesignManager(Logger logger, BiConsumer<Player, String> messager) {
        this.logger = logger;
        this.messager = messager;
    }

    public void setUi(Ui ui) { this.ui = ui; }

    public void setTextureBridge(
            @Nullable xuanmo.arcartxsuite.api.bridge.WorldTextureBridgeAPI bridge) {
        this.textureBridge = bridge;
    }

    public @Nullable DesignSession session(Player p) { return sessions.get(p.getUniqueId()); }
    public @Nullable DesignSession sessionOf(AbyssInstance i) { return byInstance.get(i.getInstanceId()); }
    public boolean isDesign(AbyssInstance i) { return byInstance.containsKey(i.getInstanceId()); }

    // ═══ 世界内贴图标记（ArcartX WorldTexture）═══

    /** 标记纹理资源 id（沿用导航箭头；后续可做成配置）。 */
    private static final String MARKER_TEXTURE = "nav_arrow";

    /** objKey → 贴图 id（sanitize 为安全字符）。 */
    private static String markerId(String objKey) {
        return "design_" + UUID.nameUUIDFromBytes(objKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .toString().replace("-", "");
    }

    /**
     * diff 同步设计标记：为每个带锚点的对象下发一个面向玩家的高亮浮动贴图。
     * 已存在的同锚点标记跳过；锚点移动/新建的重发；消失的对象标记删除。
     */
    public void syncMarkers(DesignSession s) {
        var bridge = textureBridge;
        if (bridge == null) return;
        Player p = Bukkit.getPlayer(s.playerId());
        if (p == null || !p.isOnline()) return;
        org.bukkit.World world = s.instance().getWorld();
        Set<String> want = new LinkedHashSet<>();
        for (DesignObject o : s.objects().values()) {
            int[] a = o.anchor();
            if (a == null || world == null) continue;
            String mid = markerId(o.key());
            want.add(mid);
            try {
                bridge.spawnForPlayer(p, mid,
                    new Location(world, a[0] + 0.5, a[1] + 1.6, a[2] + 0.5),
                    MARKER_TEXTURE, 1.1, 1.1, 0f, 0f, true, true, -1, null);
            } catch (Throwable t) {
                logger.fine("[abyss/design] 贴图下发失败 " + mid + ": " + t.getMessage());
            }
        }
        Set<String> prev = markers.computeIfAbsent(s.playerId(), k -> new LinkedHashSet<>());
        for (String mid : new ArrayList<>(prev)) {
            if (!want.contains(mid)) {
                try { bridge.removeForPlayer(p, mid); }
                catch (Throwable ignored) { }
            }
        }
        markers.put(s.playerId(), want);
    }

    /** 清空玩家设计标记（退出/实例销毁/玩家离线时调用）。 */
    public void clearMarkers(DesignSession s) {
        Set<String> prev = markers.remove(s.playerId());
        var bridge = textureBridge;
        if (bridge == null || prev == null || prev.isEmpty()) return;
        Player p = Bukkit.getPlayer(s.playerId());
        if (p == null) return;
        for (String mid : prev) {
            try { bridge.removeForPlayer(p, mid); }
            catch (Throwable ignored) { }
        }
    }

    // ═══ 会话生命周期 ═══

    /** 建立会话：加载模板对象快照 → 归属推断 → 注册。 */
    public DesignSession open(Player player, AbyssInstance instance) {
        instance.designMode(true);
        DesignSession s = new DesignSession(player.getUniqueId(), instance);
        s.putAll(loadObjects(instance.getTemplate()));
        DesignWriter.captureBaseline(instance.getTemplate(), s);
        sessions.put(player.getUniqueId(), s);
        byInstance.put(instance.getInstanceId(), s);
        s.selectedNode(defaultNode(instance.getTemplate()));
        syncMarkers(s);
        return s;
    }

    /** 重开主面板（命令/杖手势入口）。 */
    public void openMain(Player p) {
        DesignSession s = session(p);
        if (s != null && ui != null) ui.openMain(p, s);
    }

    /** 会话结束（玩家退出/设计实例销毁时调用）。 */
    public void close(Player player) {
        DesignSession s = sessions.remove(player.getUniqueId());
        if (s != null) {
            DesignWriter.saveDraft(s.instance().getTemplate(), s, logger);
            byInstance.remove(s.instance().getInstanceId(), s);
            clearMarkers(s);
        }
    }

    /** 保存后刷新：对象表从磁盘重建（dirty 清零，id 沿用文件键）。 */
    public void refresh(DesignSession s) {
        s.resetTracking();
        s.putAll(loadObjects(s.instance().getTemplate()));
        DesignWriter.captureBaseline(s.instance().getTemplate(), s);
        if (buildNodes(s).stream().noneMatch(n -> n.key().equals(s.selectedNode())))
            s.selectedNode(defaultNode(s.instance().getTemplate()));
        syncMarkers(s);
    }

    public void closeInstance(AbyssInstance instance) {
        DesignSession s = byInstance.remove(instance.getInstanceId());
        if (s != null) {
            DesignWriter.saveDraft(instance.getTemplate(), s, logger);
            sessions.remove(s.playerId(), s);
            clearMarkers(s);
        }
    }

    // ═══ 对象加载（YAML 节 → 快照）═══

    private List<DesignObject> loadObjects(AbyssTemplate t) {
        List<DesignObject> out = new ArrayList<>();
        File dir = t.dir();
        // floors 房间 box 表（归属推断用）
        Map<String, int[]> roomBoxes = floorsRegionBoxes(t);
        Map<String, String> roomFloor = new LinkedHashMap<>();
        if (t.mode() == AbyssTemplate.Mode.FLOORS) {
            for (FloorConfig.Floor f : t.floors().floors()) {
                for (String r : f.roomRefs()) roomFloor.putIfAbsent(r, f.id());
                if (f.bossRoom() != null) roomFloor.putIfAbsent(f.bossRoom(), f.id());
            }
        }

        loadSection(out, t, dir, "interacts.yml", "", DesignObject.Kind.INTERACT, roomBoxes);
        loadSection(out, t, dir, "obstacles.yml", "", DesignObject.Kind.OBSTACLE, roomBoxes);
        loadSection(out, t, dir, "monsters.yml", "", DesignObject.Kind.GROUP, roomBoxes);
        loadSection(out, t, dir, "npcs.yml", "", DesignObject.Kind.NPC, roomBoxes);
        loadSection(out, t, dir, "puzzles.yml", "", DesignObject.Kind.PUZZLE, roomBoxes);
        loadSection(out, t, dir, "tasks.yml", "", DesignObject.Kind.TASK, roomBoxes);
        loadSection(out, t, dir, "rewards.yml", "rewards", DesignObject.Kind.REWARD, roomBoxes);
        loadSection(out, t, dir, "rewards.yml", "chest-rewards", DesignObject.Kind.CHEST, roomBoxes);
        loadSection(out, t, dir, "rewards.yml", "chest_rewards", DesignObject.Kind.CHEST, roomBoxes);
        loadSection(out, t, dir, "chest_rewards.yml", "", DesignObject.Kind.CHEST, roomBoxes);
        loadSection(out, t, dir, "buffs.yml", "pools", DesignObject.Kind.BUFF, roomBoxes);
        loadStages(out, dir);
        loadLayoutAndSettlement(out, dir);

        // floors：房间模板本身 + 房内怪组
        if (t.mode() == AbyssTemplate.Mode.FLOORS) {
            YamlConfiguration rooms = loadYaml(dir, "rooms.yml");
            ConfigurationSection rs = rooms != null ? rooms.getConfigurationSection("rooms") : null;
            if (rs != null) {
                for (String rid : rs.getKeys(false)) {
                    ConfigurationSection sec = rs.getConfigurationSection(rid);
                    if (sec == null) continue;
                    Map<String, Object> vals = deepValues(sec);
                    int[] origin = DesignCoords.point(str(vals, "origin"));
                    int[] box = DesignCoords.box(strListRaw(vals, "region"));
                    int[] absBox = (origin != null && box != null)
                        ? DesignCoords.boxOffsetBy(origin, box) : box;
                    int[] anchor = absBox != null ? DesignCoords.center(absBox)
                        : (origin != null ? origin : null);
                    String floor = roomFloor.get(rid);
                    String node = floor != null ? "room:" + rid : "misc:global";
                    out.add(new DesignObject("room:" + rid, DesignObject.Kind.ROOM, rid,
                        "rooms.yml", "rooms", roomSummary(sec), anchor, absBox, node, vals, false));
                    // 房内怪组
                    ConfigurationSection gs = sec.getConfigurationSection("groups");
                    if (gs != null) {
                        for (String gid : gs.getKeys(false)) {
                            ConfigurationSection g = gs.getConfigurationSection(gid);
                            if (g == null) continue;
                            Map<String, Object> gv = deepValues(g);
                            int[] ga = groupAnchor(gv, origin);
                            out.add(new DesignObject("group:" + rid + "." + gid,
                                DesignObject.Kind.ROOM_GROUP, gid,
                                "rooms.yml", "rooms." + rid + ".groups",
                                groupSummary(gv), ga, null, "room:" + rid, gv, false));
                        }
                    }
                }
            }
        }

        // 机关 steps 吸附：puzzle 的 anchor = 首步骤 interact 的 anchor/归属
        Map<String, DesignObject> byId = new LinkedHashMap<>();
        for (DesignObject o : out) byId.put(o.key(), o);
        List<DesignObject> fixed = new ArrayList<>(out.size());
        for (DesignObject o : out) {
            if (o.kind() == DesignObject.Kind.PUZZLE && o.anchor() == null) {
                List<String> steps = o.strList("steps");
                for (String step : steps) {
                    DesignObject it = byId.get("interact:" + step);
                    if (it != null && it.anchor() != null) {
                        o = new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
                            o.summary(), it.anchor(), it.box(), it.node(), o.values(), o.dirty());
                        break;
                    }
                }
            }
            fixed.add(o);
        }
        List<DesignObject> result = crossLink(fixed);
        List<String> editableFiles=new ArrayList<>(DesignWriter.FILES);
        if(t.procgen()!=null) {
            String set=t.procgen().roomSet();
            if(!xuanmo.arcartxsuite.abyss.procgen.RoomSetLibrary.validName(set))throw new IllegalArgumentException("房间集名称无效");
            File assets=new File(dir.getParentFile().getParentFile(),"room-sets/"+set);
            File[] metas=assets.listFiles(f->f.isFile() && f.getName().endsWith(".yml") && !f.getName().endsWith(".region.yml"));
            if(metas!=null) for(File meta:metas) editableFiles.add("../../room-sets/"+set+"/"+meta.getName());
        }
        for (String file : editableFiles) {
            try {
                File source = new File(dir, file);
                List<String> content = source.isFile()
                    ? java.nio.file.Files.readAllLines(source.toPath(), java.nio.charset.StandardCharsets.UTF_8)
                    : new ArrayList<>();
                result.add(new DesignObject("document:" + file, DesignObject.Kind.DOCUMENT, file,
                    file, "", file.startsWith("../../")?"共享构件元数据 · 使用房内相对坐标":"完整YAML配置 · 保存时校验", null, null,
                    file.startsWith("../../")?"misc:assets":"misc:files",
                    new LinkedHashMap<>(Map.of("content", new ArrayList<>(content))), false));
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("读取配置文件失败: " + file, ex);
            }
        }
        return result;
    }

    /** 脚本动作 → 其首个参数指向的对象类型（交叉引用吸附白名单）。 */
    private static final Map<String, DesignObject.Kind> REF_ACTIONS = Map.ofEntries(
        Map.entry("spawn_group", DesignObject.Kind.GROUP),
        Map.entry("kill_group", DesignObject.Kind.GROUP),
        Map.entry("remove_group", DesignObject.Kind.GROUP),
        Map.entry("group_alive_count", DesignObject.Kind.GROUP),
        Map.entry("create_obstacle", DesignObject.Kind.OBSTACLE),
        Map.entry("delete_obstacle", DesignObject.Kind.OBSTACLE),
        Map.entry("create_interact", DesignObject.Kind.INTERACT),
        Map.entry("delete_interact", DesignObject.Kind.INTERACT),
        Map.entry("puzzle_reset", DesignObject.Kind.PUZZLE),
        Map.entry("puzzle_solve", DesignObject.Kind.PUZZLE),
        Map.entry("puzzle_prompt", DesignObject.Kind.PUZZLE),
        Map.entry("puzzle_state", DesignObject.Kind.PUZZLE),
        Map.entry("room_lock", DesignObject.Kind.ROOM),
        Map.entry("room_unlock", DesignObject.Kind.ROOM),
        Map.entry("room_status", DesignObject.Kind.ROOM));

    /**
     * 交叉引用吸附：扫描对象脚本行，被引用的游离对象（misc:*）自动归进引用者的
     * 房间节点——"由这个房间引用的门/怪组/机关"天然属于该房间。
     * 仅吸附到 room: 节点；引用者本身在 misc:* 时不做处理（方向不可逆推）。
     */
    private List<DesignObject> crossLink(List<DesignObject> objects) {
        Map<String, DesignObject> byKey = new LinkedHashMap<>();
        for (DesignObject o : objects) byKey.put(o.key(), o);
        Map<String, DesignObject> moved = new LinkedHashMap<>();
        for (DesignObject o : objects) {
            String node = o.node();
            if (node == null || !node.startsWith("room:")) continue;
            for (DesignObject.FieldSpec f : DesignObject.schema(o.kind())) {
                if (f.editor() != DesignObject.Editor.SCRIPT
                    && f.editor() != DesignObject.Editor.LIST) continue;
                for (String line : o.strList(f.name())) {
                    String[] tk = line.trim().split("\\s+");
                    if (tk.length < 2) continue;
                    DesignObject.Kind rk = REF_ACTIONS.get(tk[0]);
                    if (rk == null) continue;
                    String refId = tk[1].replaceAll("[\"'\\[\\],;]", "");
                    if (refId.isEmpty()) continue;
                    DesignObject ref = byKey.get(rk.name().toLowerCase() + ":" + refId);
                    if (ref == null) {
                        // 房内怪组后缀匹配（group:room.gid）
                        for (var e : byKey.entrySet()) {
                            if (e.getKey().endsWith("." + refId)
                                && e.getValue().kind() == DesignObject.Kind.ROOM_GROUP) {
                                ref = e.getValue();
                                break;
                            }
                        }
                    }
                    if (ref != null && ref.node() != null && ref.node().startsWith("misc:")) {
                        moved.put(ref.key(), ref.withNode(node));
                    }
                }
            }
        }
        if (moved.isEmpty()) return objects;
        List<DesignObject> out = new ArrayList<>(objects.size());
        for (DesignObject o : objects) {
            out.add(moved.getOrDefault(o.key(), o));
        }
        return out;
    }

    private void loadStages(List<DesignObject> out, File dir) {
        YamlConfiguration yaml = loadYaml(dir, "stages.yml");
        if (yaml == null) return;
        for (String id : yaml.getKeys(false)) {
            if (id.equalsIgnoreCase("entry")) continue;
            var section = yaml.getConfigurationSection(id);
            if (section == null) continue;
            Map<String, Object> values = deepValues(section);
            if (values.get("next") instanceof String target && !target.isBlank()) {
                values.put("next", List.of(Map.of("target", target)));
            } else if (values.get("next") instanceof List<?> branches) {
                values.put("next", branches.stream().map(branch -> branch instanceof String target
                    ? Map.of("target", target) : branch).toList());
            }
            if (id.equals(yaml.getString("entry"))) values.put("design-entry", true);
            out.add(new DesignObject("stage:" + id, DesignObject.Kind.STAGE, id,
                "stages.yml", "", "流程阶段", null, null, "misc:stages", values, false));
        }
    }

    private void loadLayoutAndSettlement(List<DesignObject> out, File dir) {
        YamlConfiguration floors = loadYaml(dir, "floors.yml");
        if (floors != null) {
            int order = 0;
            for (var row : floors.getMapList("floors")) {
                Map<String, Object> values = new LinkedHashMap<>();
                row.forEach((key, value) -> values.put(String.valueOf(key), DesignValues.copyValue(value)));
                String id = String.valueOf(values.getOrDefault("id", "floor_" + order));
                values.remove("id");
                values.put("design-order", ++order);
                out.add(new DesignObject("floor:" + id, DesignObject.Kind.FLOOR, id, "floors.yml",
                    "floors", "楼层路线", null, null, "misc:layout", values, false));
            }
        }
        YamlConfiguration config = loadYaml(dir, "config.yml");
        var settlement = xuanmo.arcartxsuite.abyss.template.SettlementConfig.load(
            config == null ? null : config.getConfigurationSection("settlement"));
        addSettlement(out, "settlement", "通关结算", settlement.complete());
        addSettlement(out, "failure", "失败结算", settlement.failure());
    }

    private void addSettlement(List<DesignObject> out, String id, String label,
                               xuanmo.arcartxsuite.abyss.template.SettlementConfig.Policy policy) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("exit-mode", policy.mode().name());
        values.put("exit-delay-seconds", policy.seconds());
        values.put("manual-timeout-seconds", policy.manualTimeoutSeconds());
        values.put("show-stats", policy.showStats());
        values.put("show-stats-after-exit", policy.showAfterExit());
        values.put("protect-players", policy.protectPlayers());
        values.put("revive-players", policy.revivePlayers());
        values.put("script-timeout-seconds", policy.scriptTimeoutSeconds());
        out.add(new DesignObject("settlement:" + id, DesignObject.Kind.SETTLEMENT, id,
            "config.yml", id.equals("failure") ? "settlement" : "", label,
            null, null, "misc:settlement", values, false));
    }

    private void loadSection(List<DesignObject> out, AbyssTemplate t, File dir,
                             String file, String path, DesignObject.Kind kind,
                             Map<String, int[]> roomBoxes) {
        YamlConfiguration yml = loadYaml(dir, file);
        if (yml == null) return;
        ConfigurationSection root = path.isEmpty() ? yml : yml.getConfigurationSection(path);
        if (root == null && kind == DesignObject.Kind.REWARD) root = yml;
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            if (kind == DesignObject.Kind.REWARD && (id.equals("chest-rewards") || id.equals("chest_rewards"))) continue;
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            Map<String, Object> vals = deepValues(sec);
            int[] anchor = anchorOf(kind, vals);
            int[] box = kind == DesignObject.Kind.INTERACT
                ? DesignCoords.box(strListRaw(vals, "region")) : null;
            String node = inferNode(t, anchor, box, roomBoxes, kind);
            out.add(new DesignObject(kind.name().toLowerCase() + ":" + id, kind, id,
                file, root == yml ? "" : path, summaryOf(kind, id, vals, anchor, box), anchor, box,
                node, vals, false));
        }
    }

    /** floors region 模式：房间 id → 绝对 box。 */
    private Map<String, int[]> floorsRegionBoxes(AbyssTemplate t) {
        Map<String, int[]> map = new LinkedHashMap<>();
        if (t.mode() != AbyssTemplate.Mode.FLOORS) return map;
        for (var e : t.floors().rooms().entrySet()) {
            FloorConfig.RoomTemplate r = e.getValue();
            if (r.region() == null || r.region().size() < 2) continue;
            int[] origin = DesignCoords.point(r.origin());
            int[] box = DesignCoords.box(r.region());
            if (box == null) continue;
            map.put(e.getKey(), origin != null ? DesignCoords.boxOffsetBy(origin, box) : box);
        }
        return map;
    }

    /** 归属推断：floors-region 按房间 box 收编；无坐标/无归属进全局桶。 */
    private String inferNode(AbyssTemplate t, int[] anchor, int[] box,
                             Map<String, int[]> roomBoxes, DesignObject.Kind kind) {
        if (t.mode() == AbyssTemplate.Mode.FLOORS && !roomBoxes.isEmpty()) {
            int[] probe = anchor != null ? anchor
                : (box != null ? DesignCoords.center(box) : null);
            if (probe != null) {
                for (var e : roomBoxes.entrySet()) {
                    if (DesignCoords.inBox(e.getValue(), probe[0], probe[1], probe[2])) {
                        return "room:" + e.getKey();
                    }
                }
            }
            return "misc:global";
        }
        return switch (kind) {
            case REWARD, CHEST, BUFF -> "misc:reward";
            case TASK -> "misc:flow";
            default -> "misc:objects";
        };
    }

    // ═══ 节点链 ═══

    public List<Node> buildNodes(DesignSession s) {
        AbyssTemplate t = s.instance().getTemplate();
        List<Node> out = new ArrayList<>();
        if (t.mode() == AbyssTemplate.Mode.FLOORS) {
            Set<String> placed = new java.util.HashSet<>();
            for (DesignObject floor : s.floorsInOrder()) {
                out.add(new Node(floor.key(), floor.str("name").isBlank() ? floor.id() : floor.str("name"), 0, 1));
                Set<String> refs = new java.util.LinkedHashSet<>(floor.strList("rooms"));
                if (!floor.str("boss-room").isBlank()) refs.add(floor.str("boss-room"));
                for (String reference : refs) {
                    for (DesignObject room : s.objects().values()) {
                        if (room.kind() == DesignObject.Kind.ROOM && room.id().equals(reference) && placed.add(room.key())) {
                            out.add(new Node(room.key(), room.id(), 1, s.nodeObjects(room.key()).size()));
                        }
                    }
                }
            }
            for (DesignObject room : s.objects().values()) {
                if (room.kind() == DesignObject.Kind.ROOM && !placed.contains(room.key())) {
                    out.add(new Node(room.key(), room.id() + " · 未编入路线", 0, s.nodeObjects(room.key()).size()));
                }
            }
            out.add(new Node("misc:global", "全局对象", 0,
                s.nodeObjects("misc:global").size()));
        } else {
            out.add(new Node("misc:objects", "副本对象", 0,
                s.nodeObjects("misc:objects").size()));
            out.add(new Node("misc:flow", "任务流程", 0,
                s.nodeObjects("misc:flow").size()));
            out.add(new Node("misc:reward", "奖励结算", 0,
                s.nodeObjects("misc:reward").size()));
        }
        out.add(new Node("misc:files", "完整配置文件", 0, s.nodeObjects("misc:files").size()));
        if (t.mode() == AbyssTemplate.Mode.FLOORS && t.procgen() == null) {
            out.add(new Node("misc:layout", "楼层路线", 0, s.nodeObjects("misc:layout").size()));
        }
        out.add(new Node("misc:settlement", "结算策略", 0, s.nodeObjects("misc:settlement").size()));
        out.add(new Node("misc:stages", "流程阶段", 0, s.nodeObjects("misc:stages").size()));
        if(t.procgen()!=null)out.add(new Node("misc:assets","共享构件元数据",0,s.nodeObjects("misc:assets").size()));
        return out;
    }

    public String defaultNode(AbyssTemplate t) {
        if (t.mode() == AbyssTemplate.Mode.FLOORS && !t.floors().floors().isEmpty()) {
            FloorConfig.Floor f = t.floors().floors().get(0);
            return !f.roomRefs().isEmpty() ? "room:" + f.roomRefs().get(0) : "misc:global";
        }
        return "misc:objects";
    }

    // ═══ 标记落地 ═══

    /** 标记杖右键方块：有 pending→落地；命中已有对象→开编辑；否则提示。 */
    public void handleMarkClick(Player p, Location loc, boolean shift) {
        DesignSession s = session(p);
        if (s == null) return;
        s.touch();
        DesignSession.PendingMark pending = s.pending();
        if (pending != null) {
            if (pending.kind() == DesignSession.PendingMark.Kind.POINT) {
                landPoint(s, p, pending, loc);
            } else {
                landRegionPoint(s, p, loc);
            }
            return;
        }
        // 命中已有对象 → 开编辑
        DesignObject hit = findAt(s, loc);
        if (hit != null) {
            if (ui != null) ui.openEdit(p, s, hit.key());
        } else {
            messager.accept(p, msg("design.mark-hint"));
        }
    }

    private void landPoint(DesignSession s, Player p, DesignSession.PendingMark pending,
                           Location loc) {
        int[] pt = {loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()};
        if (pending.objKind() == DesignObject.Kind.GROUP
            || pending.objKind() == DesignObject.Kind.ROOM_GROUP
            || pending.objKind() == DesignObject.Kind.NPC) pt[1]++;
        if (s.instance().getTemplate().procgen() != null) {
            landProcgenPoint(s, p, pending, loc, pt);
            return;
        }
        if (pending.targetKey() != null) {
            // 重新定位已有对象
            DesignObject o = s.get(pending.targetKey());
            if (o == null) { s.pending(null); return; }
            String field = pending.field() != null ? pending.field() : "location";
            if ("monsters".equals(field)
                && (o.kind() == DesignObject.Kind.GROUP
                    || o.kind() == DesignObject.Kind.ROOM_GROUP)) {
                // 怪组刷怪点追加：{mob-id:?,location,amount,level}
                Map<String, Object> vals = new LinkedHashMap<>(o.values());
                List<Object> spawns = new ArrayList<>();
                Object cur = vals.get("monsters");
                if (cur instanceof List<?> l) spawns.addAll(l);
                Map<String, Object> spawn = new LinkedHashMap<>();
                spawn.put("mob-id", "vanilla:zombie");
                spawn.put("location", DesignCoords.pointStr(localPoint(s, o, pt)));
                spawn.put("amount", 1);
                spawn.put("level", 1);
                spawns.add(spawn);
                vals.put("monsters", spawns);
                s.put(new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
                    o.summary(), pt, o.box(), o.node(), vals, true));
                s.pending(null);
                messager.accept(p, msg("design.spawn-added", "id", o.id()));
                syncMarkers(s);
                if (ui != null) ui.openEdit(p, s, o.key());
                return;
            }
            Map<String, Object> vals = new LinkedHashMap<>(o.values());
            vals.put(field, DesignCoords.pointStr("origin".equals(field) ? pt : localPoint(s, o, pt)));
            DesignObject next = new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
                o.summary(), pt, o.box(),
                inferNode(s.instance().getTemplate(), pt, o.box(),
                    floorsRegionBoxes(s.instance().getTemplate()), o.kind()),
                vals, true);
            s.put(next);
            postEdit(s, next);
            if (o.kind() == DesignObject.Kind.CHEST) {
                DesignObject interact = s.get("interact:" + o.str("design-interact"));
                if (interact != null) {
                    s.put(interact.withValue("location", DesignCoords.pointStr(pt)));
                    postEdit(s, s.get(interact.key()));
                }
            }
            s.pending(null);
            messager.accept(p, msg("design.relocated", "id", o.id()));
            syncMarkers(s);
            if (ui != null) ui.openEdit(p, s, o.key());
            return;
        }
        // 新建对象
        DesignObject.Kind kind = pending.objKind();
        String id = s.nextId(kind, "point");
        Map<String, Object> vals = new LinkedHashMap<>();
        seedDefaults(kind, vals);
        if ("monsters".equals(pending.field())
            && (kind == DesignObject.Kind.GROUP || kind == DesignObject.Kind.ROOM_GROUP)) {
            Map<String, Object> spawn = new LinkedHashMap<>();
            spawn.put("mob-id", "vanilla:zombie");
            int[] local = pt;
            if (kind == DesignObject.Kind.ROOM_GROUP && s.selectedNode() != null) {
                DesignObject room = s.get(s.selectedNode());
                if (room != null) local = localPoint(s, room, pt);
            }
            spawn.put("location", DesignCoords.pointStr(local));
            spawn.put("amount", 1);
            spawn.put("level", 1);
            vals.put("monsters", new ArrayList<>(List.of(spawn)));
        } else {
            vals.put(pending.field() != null ? pending.field() : "location",
                DesignCoords.pointStr(pt));
        }
        AbyssTemplate t = s.instance().getTemplate();
        String node = inferNode(t, pt, null, floorsRegionBoxes(t), kind);
        String path = kind.path();
        String key = s.uniqueKey(kind, id);
        if (kind == DesignObject.Kind.ROOM_GROUP && s.selectedNode() != null
            && s.selectedNode().startsWith("room:")) {
            String rid = s.selectedNode().substring(5);
            path = "rooms." + rid + ".groups";
            key = "group:" + rid + "." + id;
            node = s.selectedNode();
        }
        DesignObject o = new DesignObject(key, kind, id, kind.file(), path,
            kind.label() + " " + DesignCoords.pointStr(pt), pt, null, node, vals, true);
        s.put(o);
        if (kind == DesignObject.Kind.CHEST) {
            String interactId = s.nextId(DesignObject.Kind.INTERACT, "chest");
            Map<String, Object> interact = new LinkedHashMap<>();
            interact.put("type", "RIGHT_CLICK_BLOCK");
            interact.put("location", DesignCoords.pointStr(pt));
            interact.put("script", List.of("open_chest_ui @trigger " + id));
            s.put(new DesignObject("interact:" + interactId, DesignObject.Kind.INTERACT, interactId,
                "interacts.yml", "", "宝箱交互 " + id, pt, null, node, interact, true));
            s.put(o.withValue("design-interact", interactId));
        }
        s.pending(null);
        messager.accept(p, msg("design.marked", "id", id));
        syncMarkers(s);
        if (ui != null) ui.openEdit(p, s, key);
    }

    private void landProcgenPoint(DesignSession s, Player player, DesignSession.PendingMark pending,
                                  Location clicked, int[] point) {
        var kind = pending.objKind();
        String section = switch (kind) {
            case INTERACT -> "interacts";
            case NPC -> "npcs";
            case CHEST -> "chests";
            default -> null;
        };
        if (section == null || pending.targetKey() != null) {
            messager.accept(player, "§e此点位需要在共享构件元数据中调整，不能保存为本次布局的坐标。");
            s.pending(null);
            return;
        }
        var anchor = designAnchor == null ? null : designAnchor.apply(s.instance(),
            new Location(clicked.getWorld(), point[0], point[1], point[2]));
        DesignObject document = anchor == null ? null : s.get("document:" + anchor.document());
        if (document == null) {
            messager.accept(player, "§e请选择构件内部的方块，走廊不能绑定锚点。");
            return;
        }
        String id = s.nextId(kind, "anchor");
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.options().parseComments(true);
        try {
            yaml.loadFromString(String.join("\n", document.strList("content")));
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            messager.accept(player, "§c共享构件草稿格式无效，请修正后重新标点。");
            return;
        }
        var rows = new ArrayList<>(yaml.getStringList(section));
        rows.add(anchor.position() + ": " + id);
        yaml.set(section, rows);
        Map<String, Object> values = new LinkedHashMap<>();
        seedDefaults(kind, values);
        // 构件引用在物化时注册。全局定义只提供属性和动作。
        if (kind != DesignObject.Kind.CHEST) values.put("location", "asset-anchor");
        String key = s.uniqueKey(kind, id);
        String node = inferNode(s.instance().getTemplate(), null, null, Map.of(), kind);
        s.put(document.withValue("content", yaml.saveToString().lines().toList()));
        s.put(new DesignObject(key, kind, id, kind.file(), kind.path(), kind.label(),
            point, null, node, values, true));
        s.pending(null);
        messager.accept(player, "§a锚点已绑定共享构件，保存后同名构件都会使用该定义。");
        syncMarkers(s);
        if (ui != null) ui.openEdit(player, s, key);
    }

    /** 选区模式入口：isB=false=A点、true=B点（B 点落定时完成选区）。 */
    public void handleRegionClick(Player p, Location loc, boolean isB) {
        DesignSession s = session(p);
        if (s == null) return;
        s.touch();
        if (!isB) {
            s.regionPointA(new int[]{loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()});
            messager.accept(p, msg("design.region-a"));
            return;
        }
        landRegionPoint(s, p, loc);
    }

    /** 选区模式：B点落定完成（A 未取时把本次点击当 A）。 */
    private void landRegionPoint(DesignSession s, Player p, Location loc) {
        if (s.instance().getTemplate().procgen() != null) {
            s.pending(null);
            messager.accept(p, "§e随机布局不保存世界绝对选区，请使用共享构件的局部数据。");
            return;
        }
        int[] a = s.regionPointA();
        if (a == null) {
            s.regionPointA(new int[]{loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()});
            messager.accept(p, msg("design.region-a"));
            return;
        }
        int[] b = {loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()};
        DesignSession.PendingMark pending = s.pending();
        s.pending(null);
        s.regionPointA(null);
        int[] box = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
            Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
        List<String> regionStr = DesignCoords.boxStrings(a[0], a[1], a[2], b[0], b[1], b[2]);
        AbyssTemplate t = s.instance().getTemplate();

        if (pending != null && pending.targetKey() != null) {
            DesignObject o = s.get(pending.targetKey());
            if (o == null) return;
            Map<String, Object> vals = new LinkedHashMap<>(o.values());
            if (o.kind() == DesignObject.Kind.ROOM) {
                regionStr = List.of(DesignCoords.pointStr(localPoint(s, o, a)),
                    DesignCoords.pointStr(localPoint(s, o, b)));
            }
            vals.put(pending.field() != null ? pending.field() : "region", regionStr);
            DesignObject next = new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
                o.summary(), o.anchor(), box,
                inferNode(t, o.anchor(), box, floorsRegionBoxes(t), o.kind()), vals, true);
            s.put(next);
            postEdit(s, next);
            messager.accept(p, msg("design.relocated", "id", o.id()));
            syncMarkers(s);
            if (ui != null) ui.openEdit(p, s, o.key());
            return;
        }
        DesignObject.Kind kind = pending != null ? pending.objKind() : DesignObject.Kind.OBSTACLE;
        String id = s.nextId(kind, "region");
        Map<String, Object> vals = new LinkedHashMap<>();
        seedDefaults(kind, vals);
        String summaryTail = regionStr.get(0);
        if (kind == DesignObject.Kind.OBSTACLE) {
            // 障碍：扫描选区内非空气方块 → blocks: ["x,y,z,MATERIAL", ...]
            List<String> blocks = scanBox(s.instance().getWorld(), box);
            if (blocks.isEmpty()) {
                messager.accept(p, msg("design.region-empty"));
                return;
            }
            vals.put("blocks", blocks);
            summaryTail = blocks.size() + "方块";
        } else {
            vals.put(pending != null && pending.field() != null ? pending.field() : "region",
                regionStr);
            if (kind == DesignObject.Kind.ROOM) {
                int[] center = DesignCoords.center(box);
                vals.put("spawn", DesignCoords.pointStr(new int[]{center[0], box[1] + 1, center[2]}));
            }
        }
        String node = inferNode(t, DesignCoords.center(box), box,
            floorsRegionBoxes(t), kind);
        String key = s.uniqueKey(kind, id);
        if (kind == DesignObject.Kind.ROOM) node = key;
        DesignObject o = new DesignObject(key, kind, id, kind.file(), kind.path(),
            kind.label() + " " + summaryTail, DesignCoords.center(box), box,
            node, vals, true);
        s.put(o);
        messager.accept(p, msg("design.marked", "id", id));
        syncMarkers(s);
        if (ui != null) ui.openEdit(p, s, key);
    }

    /** 命中检测：点选方块是否落在某对象 anchor（同方块）或 box 内。 */
    private @Nullable DesignObject findAt(DesignSession s, Location loc) {
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        for (DesignObject o : s.objects().values()) {
            if (o.anchor() != null && o.anchor()[0] == x && o.anchor()[1] == y
                && o.anchor()[2] == z) return o;
            if (o.box() != null && DesignCoords.inBox(o.box(), x, y, z)) return o;
        }
        return null;
    }

    // ═══ 枚举值 → 中文显示层（UI 展示用；YAML 原值不动）═══

    /** 全局枚举值翻译表（值级映射，跨字段不撞语义）。 */
    private static final Map<String, String> VALUE_ZH = Map.ofEntries(
        // spawn-timing
        Map.entry("AUTO_START", "进本即刷"), Map.entry("DELAYED", "延迟刷怪"),
        Map.entry("TRIGGERED", "联动触发"), Map.entry("MANUAL", "手动触发"),
        Map.entry("SCRIPTED", "脚本控制"),
        // interact type
        Map.entry("RIGHT_CLICK_BLOCK", "右键方块"),
        Map.entry("LEFT_CLICK_BLOCK", "左键方块"),
        Map.entry("BREAK_BLOCK", "破坏方块"), Map.entry("PLACE_BLOCK", "放置方块"),
        Map.entry("STEP_ON", "踩踏触发"), Map.entry("ENTER_REGION", "进入区域"),
        Map.entry("RIGHT_CLICK_ENTITY", "右键实体"),
        // obstacle trigger-mode
        Map.entry("NONE", "手动触发"), Map.entry("ENTER", "进入触发"),
        Map.entry("LEAVE", "离开触发"), Map.entry("PERSIST", "持续触发"),
        // room type / floor advance / source
        Map.entry("combat", "战斗房"), Map.entry("boss", "BOSS房"),
        Map.entry("safe", "安全房"), Map.entry("shop", "商店房"),
        Map.entry("COMBAT", "战斗房"), Map.entry("BOSS", "BOSS房"),
        Map.entry("SAFE", "安全房"), Map.entry("SHOP", "商店房"),
        Map.entry("portal", "出口传送"), Map.entry("auto", "自动推进"),
        Map.entry("region", "世界内区域"), Map.entry("pool", "随机拼接"),
        // task type
        Map.entry("TIMED", "定时触发"), Map.entry("INTERVAL", "周期触发"),
        Map.entry("EVENT", "事件触发"),
        // puzzle type
        Map.entry("SEQUENCE", "序列机关"), Map.entry("INPUT", "输入机关"),
        // reward / buff
        Map.entry("fixed", "固定奖励"), Map.entry("random", "随机抽取"),
        Map.entry("panel", "面板选择"),
        Map.entry("hud", "HUD直给"),
        // 通用
        Map.entry("true", "是"), Map.entry("false", "否"));

    /** 枚举值中文（无映射原样返回）。 */
    static String zh(String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) return value;
        return VALUE_ZH.getOrDefault(value, value);
    }

    // ═══ 编辑操作 ═══

    /** Room fields are persisted relative to their editable origin; other objects are absolute. */
    private static int[] localPoint(DesignSession s, DesignObject o, int[] absolute) {
        DesignObject room = o;
        if (o.kind() == DesignObject.Kind.ROOM_GROUP) {
            String path = o.path();
            String rid = path.substring("rooms.".length(), path.length() - ".groups".length());
            room = s.get("room:" + rid);
        } else if (o.kind() != DesignObject.Kind.ROOM) return absolute.clone();
        int[] origin = room != null ? DesignCoords.point(room.str("origin")) : null;
        return origin == null ? absolute.clone() : new int[]{absolute[0] - origin[0],
            absolute[1] - origin[1], absolute[2] - origin[2]};
    }

    /** 字段写入（UI 回包 design_set）。raw 已按 editor 类型格式化。 */
    public boolean setField(DesignSession s, String key, String field, String raw) {
        DesignObject o = s.get(key);
        if (o == null) return false;
        DesignObject.FieldSpec spec = specOf(o.kind(), field);
        Object value;
        if (spec == null) {
            throw new IllegalArgumentException("未知字段: " + field);
        } else {
            value = switch (spec.editor()) {
                case NUMBER -> parseNum(raw);
                case TOGGLE -> "true".equalsIgnoreCase(raw) || "1".equals(raw);
                case SCRIPT, LIST, YAML -> null; // 走行编辑动作
                default -> raw.isBlank() ? null : raw;
            };
            if (value instanceof Number n && (n.doubleValue() < 0 || !Double.isFinite(n.doubleValue())))
                throw new IllegalArgumentException("数值必须是有限的非负数");
            if ("rows".equals(field) && value instanceof Number n && (n.intValue() < 1 || n.intValue() > 6))
                throw new IllegalArgumentException("宝箱行数必须为1到6");
            if (spec.editor() == DesignObject.Editor.SCRIPT
                || spec.editor() == DesignObject.Editor.LIST || spec.editor() == DesignObject.Editor.YAML) {
                return false;
            }
            if (value == null) {
                o = removeField(o, field);
                s.put(o);
                postEdit(s, o);
                return true;
            }
        }
        if (o.kind() == DesignObject.Kind.STAGE && field.equals("design-entry") && Boolean.TRUE.equals(value)) {
            for (DesignObject stage : List.copyOf(s.objects().values())) {
                if (stage.kind() == DesignObject.Kind.STAGE && !stage.key().equals(key)
                    && Boolean.TRUE.equals(stage.values().get("design-entry"))) {
                    s.put(stage.withValue("design-entry", false));
                }
            }
        }
        s.put(o.withValue(field, value));
        postEdit(s, s.get(key));
        return true;
    }

    /** CYCLE 循环钮：返回下一个可选值。 */
    public @Nullable String cycleNext(DesignSession s, String key, String field) {
        DesignObject o = s.get(key);
        if (o == null) return null;
        if (o.kind() == DesignObject.Kind.FLOOR && field.equals("design-append-room")) {
            String room = o.str("design-room-ref");
            if (!objectIds(s, DesignObject.Kind.ROOM).contains(room))
                throw new IllegalArgumentException("请先选择要加入路线的房间");
            if (o.strList("rooms").contains(room)) throw new IllegalArgumentException("房间已在本层路线中");
            lineAdd(s, key, "rooms", room);
            return "已添加";
        }
        if (o.kind() == DesignObject.Kind.STAGE && field.equals("design-append-action")) {
            String hook = o.str("design-action-hook");
            if (hook.isBlank()) hook = "on-start";
            if (!Set.of("on-start", "on-complete", "on-timeout").contains(hook))
                throw new IllegalArgumentException("动作时机无效");
            validateActionTarget(s, o);
            lineAdd(s, key, hook, StageForm.action(o));
            return "已添加";
        }
        if (o.kind() == DesignObject.Kind.STAGE && field.equals("design-append-branch")) {
            String target = o.str("design-branch-target").trim();
            if (s.objects().values().stream().noneMatch(stage -> stage.kind() == DesignObject.Kind.STAGE
                && stage.id().equals(target))) throw new IllegalArgumentException("请选择已存在的阶段ID");
            var branches = editableLines(o, "next");
            if (branches.size() >= 128) throw new IllegalArgumentException("阶段分支最多128条");
            String condition = StageForm.condition(o.str("design-branch-condition"), o.str("design-branch-ref"));
            if (condition.isBlank()) branches.add(Map.of("target", target));
            else branches.add(Map.of("target", target, "condition", condition));
            s.put(o.withValue("next", branches));
            return "已添加";
        }
        DesignObject.FieldSpec spec = specOf(o.kind(), field);
        if (spec == null) return null;
        List<String> options = fieldOptions(s, o, spec);
        if (options.isEmpty()) return null;
        String cur = fieldStr(o, field);
        int idx = options.indexOf(cur);
        String next = options.get((idx + 1) % options.size());
        s.put(o.withValue(field, next));
        postEdit(s, s.get(key));
        return next;
    }

    /** 脚本/列表行追加。 */
    public List<String> lines(DesignObject o, String field) {
        DesignObject.FieldSpec spec = specOf(o.kind(), field);
        if (spec != null && spec.editor() == DesignObject.Editor.YAML) {
            Object raw = o.values().get(field);
            if (raw instanceof List<?> list) return list.stream().map(DesignValues::flow).toList();
            return List.of();
        }
        return o.strList(field);
    }

    private List<Object> editableLines(DesignObject o, String field) {
        DesignObject.FieldSpec spec = specOf(o.kind(), field);
        if (spec == null || !(spec.editor() == DesignObject.Editor.SCRIPT
            || spec.editor() == DesignObject.Editor.LIST || spec.editor() == DesignObject.Editor.YAML))
            throw new IllegalArgumentException("该字段不支持行编辑");
        Object raw = o.values().get(field);
        return raw instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>(o.strList(field));
    }

    private Object parseLine(DesignObject o, String field, String line) {
        if (line.length() > 8192) throw new IllegalArgumentException("单行长度不能超过8192");
        var spec = specOf(o.kind(), field);
        if (spec != null && spec.editor() == DesignObject.Editor.YAML) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString("value: " + line);
                Object value = DesignValues.copyValue(yaml.get("value"));
                if (!(value instanceof Map)) throw new IllegalArgumentException("请填写JSON对象，例如 {\"mob-id\":\"vanilla:zombie\",\"location\":\"0,65,0\",\"amount\":1,\"level\":1}");
                return value;
            } catch (org.bukkit.configuration.InvalidConfigurationException ex) {
                throw new IllegalArgumentException("对象格式错误: " + ex.getMessage());
            }
        }
        if ("times".equals(field)) {
            try { return Integer.parseInt(line.trim()); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("触发时间必须是整数秒"); }
        }
        return line;
    }

    public void lineAdd(DesignSession s, String key, String field, String line) {
        DesignObject o = s.get(key);
        if (o == null) return;
        List<Object> lines = editableLines(o, field);
        if (lines.size() >= 4096) throw new IllegalArgumentException("列表最多4096行");
        lines.add(parseLine(o, field, line));
        s.put(o.withValue(field, lines));
        postEdit(s, s.get(key));
    }

    public void lineSet(DesignSession s, String key, String field, int idx, String line) {
        DesignObject o = s.get(key);
        if (o == null) return;
        List<Object> lines = editableLines(o, field);
        if (idx < 0 || idx >= lines.size()) return;
        lines.set(idx, parseLine(o, field, line));
        s.put(o.withValue(field, lines));
        postEdit(s, s.get(key));
    }

    public void lineDel(DesignSession s, String key, String field, int idx) {
        DesignObject o = s.get(key);
        if (o == null) return;
        List<Object> lines = editableLines(o, field);
        if (idx < 0 || idx >= lines.size()) return;
        lines.remove(idx);
        s.put(o.withValue(field, lines));
        postEdit(s, s.get(key));
    }

    public void lineMove(DesignSession s, String key, String field, int idx, int dir) {
        DesignObject o = s.get(key);
        if (o == null) return;
        List<Object> lines = editableLines(o, field);
        int to = idx + dir;
        if (idx < 0 || idx >= lines.size() || to < 0 || to >= lines.size()) return;
        java.util.Collections.swap(lines, idx, to);
        s.put(o.withValue(field, lines));
        postEdit(s, s.get(key));
    }

    /** 重命名 id。 */
    public boolean rename(DesignSession s, String key, String newId) {
        DesignObject o = s.get(key);
        if (o == null || o.kind() == DesignObject.Kind.DOCUMENT || o.kind() == DesignObject.Kind.SETTLEMENT
            || (o.kind() == DesignObject.Kind.STAGE && newId.equalsIgnoreCase("entry"))
            || newId.isBlank() || !newId.matches("[\\p{L}\\p{N}_\\-]{1,64}")) return false;
        if (s.idTaken(o.kind(), newId, key)) return false;
        s.put(o.withId(newId));
        postEdit(s, s.get(key));
        return true;
    }

    /** 删除对象。 */
    public void delete(DesignSession s, String key) {
        if (s.get(key) != null && s.get(key).kind() == DesignObject.Kind.SETTLEMENT)
            throw new IllegalArgumentException("通关和失败结算属于固定设置");
        if (s.get(key) != null && s.get(key).kind() == DesignObject.Kind.DOCUMENT)
            throw new IllegalArgumentException("配置文件不能从对象面板删除，请编辑文件内容");
        DesignObject object = s.get(key);
        if (object != null && object.kind() == DesignObject.Kind.CHEST && !object.str("design-interact").isEmpty())
            s.remove("interact:" + object.str("design-interact"));
        s.remove(key);
        syncMarkers(s);
    }

    /**
     * 编辑后处理：坐标字段文本直写后重建 anchor/box/node/summary（数组按值比较），
     * 最后统一差量同步世界内贴图。
     */
    private void postEdit(DesignSession s, DesignObject o) {
        if (o == null) { syncMarkers(s); return; }
        int[] na = anchorOf(o.kind(), o.values());
        int[] nb = o.kind() == DesignObject.Kind.INTERACT
            ? DesignCoords.box(strListRaw(o.values(), "region")) : o.box();
        if (o.kind() == DesignObject.Kind.ROOM) {
            int[] origin = DesignCoords.point(o.str("origin"));
            nb = DesignCoords.box(o.strList("region"));
            if (nb != null && origin != null) nb = DesignCoords.boxOffsetBy(origin, nb);
            na = nb != null ? DesignCoords.center(nb) : origin;
        } else if (o.kind() == DesignObject.Kind.ROOM_GROUP) {
            String rid = o.path().substring(6, o.path().length() - 7);
            DesignObject room = s.get("room:" + rid);
            int[] origin = room != null ? DesignCoords.point(room.str("origin")) : null;
            na = groupAnchor(o.values(), origin);
        }
        if (!Arrays.equals(na, o.anchor()) || !Arrays.equals(nb, o.box())) {
            AbyssTemplate t = s.instance().getTemplate();
            s.put(new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
                summaryOf(o.kind(), o.id(), o.values(), na, nb), na, nb,
                (o.kind() == DesignObject.Kind.ROOM || o.kind() == DesignObject.Kind.ROOM_GROUP
                    || o.kind() == DesignObject.Kind.DOCUMENT) ? o.node()
                    : inferNode(t, na, nb, floorsRegionBoxes(t), o.kind()),
                o.values(), true));
        }
        syncMarkers(s);
    }

    // ═══ 显示数据（UI 下发用）═══

    /**
     * 编辑面板字段行：{@code name|editor|label|value|valueZh|options}。
     * valueZh=展示中文（CYCLE/TOGGLE 枚举值翻译；其余=value）；options 为原值列表。
     */
    public List<String> fieldRows(DesignSession s, DesignObject o) {
        List<String> out = new ArrayList<>();
        for (DesignObject.FieldSpec f : DesignObject.schema(o.kind())) {
            if (f.editor() == DesignObject.Editor.SCRIPT
                || f.editor() == DesignObject.Editor.LIST || f.editor() == DesignObject.Editor.YAML) continue;
            String val = fieldStr(o, f.name());
            List<String> options = fieldOptions(s, o, f);
            var editor = options.isEmpty() ? f.editor() : DesignObject.Editor.CYCLE;
            String valZh = (editor == DesignObject.Editor.CYCLE
                || f.editor() == DesignObject.Editor.TOGGLE) ? zh(val) : val;
            String opts = options.isEmpty() ? "-" : String.join(",", options);
            out.add(f.name() + "|" + editor.name() + "|" + f.label()
                + "|" + val.replace("|", "/") + "|"
                + valZh.replace("|", "/") + "|" + opts);
        }
        return out;
    }

    private List<String> fieldOptions(DesignSession s, DesignObject object, DesignObject.FieldSpec field) {
        var kind = switch (field.name()) {
            case "design-room-ref", "boss-room" -> DesignObject.Kind.ROOM;
            case "design-branch-target" -> DesignObject.Kind.STAGE;
            case "design-action-arg" -> StageForm.actionTarget(object.str("design-action"));
            case "design-condition-ref", "design-branch-ref" -> switch (object.str(
                field.name().equals("design-condition-ref") ? "design-condition" : "design-branch-condition")) {
                case "怪组清空" -> DesignObject.Kind.GROUP;
                case "房间清空" -> DesignObject.Kind.ROOM;
                default -> null;
            };
            default -> null;
        };
        if (kind == null) return field.options();
        List<String> options = new ArrayList<>(objectIds(s, kind));
        if (field.name().equals("boss-room")) options.add("");
        return options;
    }

    private List<String> objectIds(DesignSession session, DesignObject.Kind kind) {
        return session.objects().values().stream().filter(o -> o.kind() == kind)
            .map(DesignObject::id).distinct().toList();
    }

    private void validateActionTarget(DesignSession session, DesignObject object) {
        var kind = StageForm.actionTarget(object.str("design-action"));
        if (kind != null && !objectIds(session, kind).contains(object.str("design-action-arg").trim()))
            throw new IllegalArgumentException("请选择已存在的" + kind.label());
    }

    /** 脚本/列表字段行：field|label|行数。List<Map> 字段（结构化行）不进组——防止行编辑把结构降级成字符串。 */
    public List<String> scriptGroupRows(DesignObject o) {
        List<String> out = new ArrayList<>();
        for (DesignObject.FieldSpec f : DesignObject.schema(o.kind())) {
            if (f.editor() != DesignObject.Editor.SCRIPT
                && f.editor() != DesignObject.Editor.LIST && f.editor() != DesignObject.Editor.YAML) continue;
            Object v = o.values().get(f.name());
            if (v == null) v = o.values().get(f.name().replace('-', '_'));
            out.add(f.name() + "|" + f.label() + "|" + lines(o, f.name()).size());
        }
        return out;
    }

    /** 对象行（主面板节点内列表）：key|icon|id|summary。 */
    public String objectRow(DesignObject o) {
        return o.key() + "|" + o.kind().icon() + "|" + o.id() + "|" + o.summary();
    }

    // ═══ 内部工具 ═══

    private static @Nullable DesignObject.FieldSpec specOf(DesignObject.Kind kind, String field) {
        for (DesignObject.FieldSpec f : DesignObject.schema(kind)) {
            if (f.name().equals(field)) return f;
        }
        return null;
    }

    /** 字段读值：kebab 优先，snake 兜底。 */
    public static String fieldStr(DesignObject o, String field) {
        Object v = o.values().get(field);
        if (v == null) v = o.values().get(field.replace('-', '_'));
        if (v instanceof List<?> l) {
            if (!l.isEmpty() && l.get(0) instanceof Map) {
                return l.size() + "项"; // 结构化列表显示项数，不展开 map
            }
            return String.join(";", l.stream().map(String::valueOf).toList());
        }
        return v != null ? String.valueOf(v) : "";
    }

    private static Object parseNum(String raw) {
        try {
            if (raw.contains(".")) return Double.parseDouble(raw);
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("请输入有效数字");
        }
    }

    private static DesignObject removeField(DesignObject o, String field) {
        Map<String, Object> vals = new LinkedHashMap<>(o.values());
        vals.remove(field);
        vals.remove(field.replace('-', '_'));
        return new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
            o.summary(), o.anchor(), o.box(), o.node(), vals, true);
    }

    private static @Nullable int[] anchorOf(DesignObject.Kind kind, Map<String, Object> vals) {
        return switch (kind) {
            case INTERACT -> {
                int[] p = DesignCoords.point(str(vals, "location"));
                if (p != null) yield p;
                int[] b = DesignCoords.box(strListRaw(vals, "region"));
                yield b != null ? DesignCoords.center(b) : null;
            }
            case NPC, CHEST -> DesignCoords.point(str(vals, "location"));
            case GROUP, ROOM_GROUP -> groupAnchor(vals, null);
            case OBSTACLE -> {
                List<String> blocks = strListRaw(vals, "blocks");
                int[] p = blocks.isEmpty() ? null : DesignCoords.point(blocks.get(0));
                yield p;
            }
            default -> null;
        };
    }

    private static @Nullable int[] groupAnchor(Map<String, Object> vals, @Nullable int[] origin) {
        Object mobs = vals.get("monsters");
        if (mobs instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> m) {
            Object loc = m.get("location");
            int[] p = DesignCoords.point(loc != null ? String.valueOf(loc) : null);
            if (p != null) return origin != null ? DesignCoords.offsetBy(origin, p) : p;
        }
        return null;
    }

    private static String summaryOf(DesignObject.Kind kind, String id,
                                    Map<String, Object> vals,
                                    int[] anchor, int[] box) {
        String base = switch (kind) {
            case INTERACT -> zh(str(vals, "type"));
            case OBSTACLE -> zh(str(vals, "trigger-mode").isEmpty()
                ? str(vals, "trigger_mode") : str(vals, "trigger-mode"));
            case GROUP, ROOM_GROUP -> {
                Object mobs = vals.get("monsters");
                int n = mobs instanceof List<?> l ? l.size() : 0;
                yield zh(str(vals, "spawn-timing")) + "·" + n + "处";
            }
            case NPC -> str(vals, "name");
            case PUZZLE -> zh(str(vals, "type")) + "·"
                + strListRaw(vals, "steps").size() + "步";
            case TASK -> zh(str(vals, "type"));
            case REWARD -> str(vals, "type").isEmpty() ? "固定奖励" : zh(str(vals, "type"));
            case CHEST -> str(vals, "title");
            case BUFF -> str(vals, "name");
            case ROOM -> zh(str(vals, "type"));
            case FLOOR -> str(vals, "name");
            case STAGE -> str(vals, "name");
            case SETTLEMENT -> str(vals, "exit-mode");
            case DOCUMENT -> id;
        };
        String pos = anchor != null ? " " + DesignCoords.pointStr(anchor)
            : (box != null ? " 区域" : "");
        return (base.isEmpty() ? "?" : base) + pos;
    }

    private static String roomSummary(ConfigurationSection sec) {
        String type = sec.getString("type", "combat");
        int groups = 0;
        ConfigurationSection gs = sec.getConfigurationSection("groups");
        if (gs != null) groups = gs.getKeys(false).size();
        return type + "·" + groups + "组怪";
    }

    private static String groupSummary(Map<String, Object> vals) {
        Object mobs = vals.get("monsters");
        int n = mobs instanceof List<?> l ? l.size() : 0;
        String timing = str(vals, "spawn-timing");
        if (timing.isEmpty()) timing = str(vals, "spawn_timing");
        return timing + "·" + n + "处";
    }

    /** 新建对象的默认字段值（UI/Manager 共用）。 */
    public static void seedDefaults(DesignObject.Kind kind, Map<String, Object> vals) {
        switch (kind) {
            case INTERACT -> vals.put("type", "RIGHT_CLICK_BLOCK");
            case OBSTACLE -> vals.put("trigger-mode", "NONE");
            case GROUP, ROOM_GROUP -> {
                vals.put("spawn-timing", "MANUAL");
                vals.put("monsters", new ArrayList<>());
            }
            case NPC -> vals.put("type", "VILLAGER");
            case PUZZLE -> vals.put("type", "sequence");
            case TASK -> vals.put("type", "TIMED");
            case REWARD -> vals.put("items", new ArrayList<>());
            case CHEST -> {
                vals.put("rows", 3);
                vals.put("options", new ArrayList<>(List.of(new LinkedHashMap<>(Map.of(
                    "item", "material:IRON_INGOT:1", "weight", 10)))));
            }
            case ROOM -> vals.put("type", "combat");
            case FLOOR -> {
                vals.put("source", "region");
                vals.put("advance", "portal");
            }
            case SETTLEMENT -> { }
            case STAGE -> {
                vals.put("design-condition", "地图清空");
                vals.put("design-action-hook", "on-start");
                vals.put("design-action", "提示");
            }
            case BUFF -> { vals.put("pick", 1); vals.put("presentation", "panel"); }
            case DOCUMENT -> { }
        }
    }

    // ── YAML 快照工具 ──

    private static @Nullable YamlConfiguration loadYaml(File dir, String name) {
        File f = new File(dir, name);
        if (!f.isFile()) return null;
        return YamlConfiguration.loadConfiguration(f);
    }

    /** 节 → 深拷贝 Map（kebab/snake 键原样保留，写回时统一 kebab）。 */
    private static Map<String, Object> deepValues(ConfigurationSection sec) {
        return DesignValues.properties(sec.getValues(false));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m) {
                Map<String, Object> sub = new LinkedHashMap<>();
                for (var se : m.entrySet()) {
                    sub.put(String.valueOf(se.getKey()), deepCopyObj(se.getValue()));
                }
                out.put(e.getKey(), sub);
            } else {
                out.put(e.getKey(), deepCopyObj(v));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyObj(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> sub = new LinkedHashMap<>();
            for (var e : m.entrySet()) {
                sub.put(String.valueOf(e.getKey()), deepCopyObj(e.getValue()));
            }
            return sub;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(deepCopyObj(o));
            return out;
        }
        return v;
    }

    static String str(Map<String, Object> vals, String key) {
        Object v = vals.get(key);
        if (v == null) v = vals.get(key.replace('-', '_'));
        return v != null ? String.valueOf(v) : "";
    }

    static List<String> strListRaw(Map<String, Object> vals, String key) {
        Object v = vals.get(key);
        if (v == null) v = vals.get(key.replace('-', '_'));
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object o : l) out.add(String.valueOf(o));
            return out;
        }
        return v != null ? List.of(String.valueOf(v)) : List.of();
    }

    /** 扫描 box 内非空气方块 → {@code "x,y,z,MATERIAL"} 行表（障碍 blocks 格式）。上限 4000 方块。 */
    private static List<String> scanBox(@Nullable org.bukkit.World w, int[] box) {
        List<String> out = new ArrayList<>();
        if (w == null) return out;
        long width = (long) box[3] - box[0] + 1;
        long height = (long) box[4] - box[1] + 1;
        long depth = (long) box[5] - box[2] + 1;
        if (width <= 0 || height <= 0 || depth <= 0 || width > 4000 || height > 4000
            || depth > 4000 || width * height * depth > 4000) return out;
        for (int x = box[0]; x <= box[3]; x++) {
            for (int y = box[1]; y <= box[4]; y++) {
                for (int z = box[2]; z <= box[5]; z++) {
                    var b = w.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        out.add(x + "," + y + "," + z + "," + b.getType().name());
                    }
                }
            }
        }
        return out;
    }

    /** 消息解析（监听器直发用）。 */
    public String msgText(String key, String... kv) {
        return msg(key, kv);
    }

    private String msg(String key, String... kv) {
        return msgResolver != null ? msgResolver.apply(key, kv) : key;
    }

    private java.util.function.BiFunction<String, String[], String> msgResolver;
    public void setMsgResolver(java.util.function.BiFunction<String, String[], String> r) {
        this.msgResolver = r;
    }
}
