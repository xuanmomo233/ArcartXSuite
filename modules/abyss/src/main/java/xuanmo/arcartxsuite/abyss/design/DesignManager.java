package xuanmo.arcartxsuite.abyss.design;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
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

    /** 流程节点（主面板顶部节点链）。 */
    public record Node(String key, String label, int depth, int objectCount) { }

    private final Logger logger;
    private final BiConsumer<Player, String> messager;

    /** 玩家 → 会话。 */
    private final Map<UUID, DesignSession> sessions = new ConcurrentHashMap<>();
    /** 实例id → 会话（实例销毁时清理）。 */
    private final Map<UUID, DesignSession> byInstance = new ConcurrentHashMap<>();

    /** UI 回调（打开主面板/编辑面板/副面板、提示刷新），由 UiService 注入。 */
    public interface Ui {
        void openMain(Player p, DesignSession s);
        void openEdit(Player p, DesignSession s, String objKey);
        void openPick(Player p, DesignSession s, String nodeKey);
        void refreshMain(Player p, DesignSession s);
        void closeAll(Player p);
    }
    private volatile @Nullable Ui ui;

    public DesignManager(Logger logger, BiConsumer<Player, String> messager) {
        this.logger = logger;
        this.messager = messager;
    }

    public void setUi(Ui ui) { this.ui = ui; }

    public @Nullable DesignSession session(Player p) { return sessions.get(p.getUniqueId()); }
    public @Nullable DesignSession sessionOf(AbyssInstance i) { return byInstance.get(i.getInstanceId()); }
    public boolean isDesign(AbyssInstance i) { return byInstance.containsKey(i.getInstanceId()); }

    // ═══ 会话生命周期 ═══

    /** 建立会话：加载模板对象快照 → 归属推断 → 注册。 */
    public DesignSession open(Player player, AbyssInstance instance) {
        DesignSession s = new DesignSession(player.getUniqueId(), instance);
        s.putAll(loadObjects(instance.getTemplate()));
        sessions.put(player.getUniqueId(), s);
        byInstance.put(instance.getInstanceId(), s);
        s.selectedNode(defaultNode(instance.getTemplate()));
        return s;
    }

    /** 会话结束（玩家退出/设计实例销毁时调用）。 */
    public void close(Player player) {
        DesignSession s = sessions.remove(player.getUniqueId());
        if (s != null) byInstance.remove(s.instance().getInstanceId(), s);
    }

    /** 保存后刷新：对象表从磁盘重建（dirty 清零，id 沿用文件键）。 */
    public void refresh(DesignSession s) {
        s.resetTracking();
        s.putAll(loadObjects(s.instance().getTemplate()));
    }

    public void closeInstance(AbyssInstance instance) {
        DesignSession s = byInstance.remove(instance.getInstanceId());
        if (s != null) sessions.remove(s.playerId(), s);
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
        loadSection(out, t, dir, "buffs.yml", "pools", DesignObject.Kind.BUFF, roomBoxes);

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
        return fixed;
    }

    private void loadSection(List<DesignObject> out, AbyssTemplate t, File dir,
                             String file, String path, DesignObject.Kind kind,
                             Map<String, int[]> roomBoxes) {
        YamlConfiguration yml = loadYaml(dir, file);
        if (yml == null) return;
        ConfigurationSection root = path.isEmpty() ? yml : yml.getConfigurationSection(path);
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            Map<String, Object> vals = deepValues(sec);
            int[] anchor = anchorOf(kind, vals);
            int[] box = kind == DesignObject.Kind.INTERACT
                ? DesignCoords.box(strListRaw(vals, "region")) : null;
            String node = inferNode(t, anchor, box, roomBoxes, kind);
            out.add(new DesignObject(kind.name().toLowerCase() + ":" + id, kind, id,
                file, path, summaryOf(kind, id, vals, anchor, box), anchor, box,
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
            for (FloorConfig.Floor f : t.floors().floors()) {
                int count = 0;
                List<Node> subs = new ArrayList<>();
                List<String> refs = new ArrayList<>(f.roomRefs());
                if (f.bossRoom() != null && !refs.contains(f.bossRoom())) refs.add(f.bossRoom());
                for (String rid : refs) {
                    int n = 1 + s.nodeObjects("room:" + rid).size(); // +房间对象自身
                    subs.add(new Node("room:" + rid, roomLabel(t, rid), 1,
                        s.nodeObjects("room:" + rid).size()));
                    count += n;
                }
                out.add(new Node("floor:" + f.id(), f.name(), 0, count));
                out.addAll(subs);
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
        return out;
    }

    public String defaultNode(AbyssTemplate t) {
        if (t.mode() == AbyssTemplate.Mode.FLOORS && !t.floors().floors().isEmpty()) {
            FloorConfig.Floor f = t.floors().floors().get(0);
            return !f.roomRefs().isEmpty() ? "room:" + f.roomRefs().get(0) : "misc:global";
        }
        return "misc:objects";
    }

    private String roomLabel(AbyssTemplate t, String rid) {
        FloorConfig.RoomTemplate r = t.floors().rooms().get(rid);
        return r != null ? rid : rid;
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
                spawn.put("mob-id", "?");
                spawn.put("location", DesignCoords.pointStr(pt));
                spawn.put("amount", 1);
                spawn.put("level", 1);
                spawns.add(spawn);
                vals.put("monsters", spawns);
                s.put(new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
                    o.summary(), pt, o.box(), o.node(), vals, true));
                s.pending(null);
                messager.accept(p, msg("design.spawn-added", "id", o.id()));
                if (ui != null) ui.openEdit(p, s, o.key());
                return;
            }
            Map<String, Object> vals = new LinkedHashMap<>(o.values());
            vals.put(field, DesignCoords.pointStr(pt));
            DesignObject next = new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
                o.summary(), pt, o.box(),
                inferNode(s.instance().getTemplate(), pt, o.box(),
                    floorsRegionBoxes(s.instance().getTemplate()), o.kind()),
                vals, true);
            s.put(next);
            s.pending(null);
            messager.accept(p, msg("design.relocated", "id", o.id()));
            if (ui != null) ui.openEdit(p, s, o.key());
            return;
        }
        // 新建对象
        DesignObject.Kind kind = pending.objKind();
        String id = "point_" + (s.objects().size() + 1);
        Map<String, Object> vals = new LinkedHashMap<>();
        seedDefaults(kind, vals);
        if ("monsters".equals(pending.field())
            && (kind == DesignObject.Kind.GROUP || kind == DesignObject.Kind.ROOM_GROUP)) {
            Map<String, Object> spawn = new LinkedHashMap<>();
            spawn.put("mob-id", "?");
            spawn.put("location", DesignCoords.pointStr(pt));
            spawn.put("amount", 1);
            spawn.put("level", 1);
            vals.put("monsters", new ArrayList<>(List.of(spawn)));
        } else {
            vals.put(pending.field() != null ? pending.field() : "location",
                DesignCoords.pointStr(pt));
        }
        AbyssTemplate t = s.instance().getTemplate();
        String node = inferNode(t, pt, null, floorsRegionBoxes(t), kind);
        String key = s.uniqueKey(kind, id);
        DesignObject o = new DesignObject(key, kind, id, kind.file(), kind.path(),
            kind.label() + " " + DesignCoords.pointStr(pt), pt, null, node, vals, true);
        s.put(o);
        s.pending(null);
        messager.accept(p, msg("design.marked", "id", id));
        if (ui != null) ui.openEdit(p, s, key);
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
            vals.put(pending.field() != null ? pending.field() : "region", regionStr);
            DesignObject next = new DesignObject(o.key(), o.kind(), o.id(), o.file(), o.path(),
                o.summary(), o.anchor(), box,
                inferNode(t, o.anchor(), box, floorsRegionBoxes(t), o.kind()), vals, true);
            s.put(next);
            messager.accept(p, msg("design.relocated", "id", o.id()));
            if (ui != null) ui.openEdit(p, s, o.key());
            return;
        }
        DesignObject.Kind kind = pending != null ? pending.objKind() : DesignObject.Kind.OBSTACLE;
        String id = "region_" + (s.objects().size() + 1);
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
        }
        String node = inferNode(t, DesignCoords.center(box), box,
            floorsRegionBoxes(t), kind);
        String key = s.uniqueKey(kind, id);
        DesignObject o = new DesignObject(key, kind, id, kind.file(), kind.path(),
            kind.label() + " " + summaryTail, DesignCoords.center(box), box,
            node, vals, true);
        s.put(o);
        messager.accept(p, msg("design.marked", "id", id));
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

    // ═══ 编辑操作 ═══

    /** 字段写入（UI 回包 design_set）。raw 已按 editor 类型格式化。 */
    public boolean setField(DesignSession s, String key, String field, String raw) {
        DesignObject o = s.get(key);
        if (o == null) return false;
        DesignObject.FieldSpec spec = specOf(o.kind(), field);
        Object value;
        if (spec == null) {
            value = raw;
        } else {
            value = switch (spec.editor()) {
                case NUMBER -> parseNum(raw);
                case TOGGLE -> "true".equalsIgnoreCase(raw) || "1".equals(raw);
                case SCRIPT, LIST -> null; // 走 script_* 动作
                default -> raw.isBlank() ? null : raw;
            };
            if (spec.editor() == DesignObject.Editor.SCRIPT
                || spec.editor() == DesignObject.Editor.LIST) {
                return false;
            }
            if (value == null) {
                o = removeField(o, field);
                s.put(o);
                return true;
            }
        }
        s.put(o.withValue(field, value));
        return true;
    }

    /** CYCLE 循环钮：返回下一个可选值。 */
    public @Nullable String cycleNext(DesignSession s, String key, String field) {
        DesignObject o = s.get(key);
        if (o == null) return null;
        DesignObject.FieldSpec spec = specOf(o.kind(), field);
        if (spec == null || spec.options().isEmpty()) return null;
        String cur = fieldStr(o, field);
        int idx = spec.options().indexOf(cur);
        String next = spec.options().get((idx + 1) % spec.options().size());
        s.put(o.withValue(field, next));
        return next;
    }

    /** 脚本/列表行追加。 */
    public void lineAdd(DesignSession s, String key, String field, String line) {
        DesignObject o = s.get(key);
        if (o == null || line.isBlank()) return;
        List<String> lines = new ArrayList<>(o.strList(field));
        lines.add(line);
        s.put(o.withValue(field, lines));
    }

    public void lineSet(DesignSession s, String key, String field, int idx, String line) {
        DesignObject o = s.get(key);
        if (o == null) return;
        List<String> lines = new ArrayList<>(o.strList(field));
        if (idx < 0 || idx >= lines.size()) return;
        lines.set(idx, line);
        s.put(o.withValue(field, lines));
    }

    public void lineDel(DesignSession s, String key, String field, int idx) {
        DesignObject o = s.get(key);
        if (o == null) return;
        List<String> lines = new ArrayList<>(o.strList(field));
        if (idx < 0 || idx >= lines.size()) return;
        lines.remove(idx);
        s.put(o.withValue(field, lines));
    }

    public void lineMove(DesignSession s, String key, String field, int idx, int dir) {
        DesignObject o = s.get(key);
        if (o == null) return;
        List<String> lines = new ArrayList<>(o.strList(field));
        int to = idx + dir;
        if (idx < 0 || idx >= lines.size() || to < 0 || to >= lines.size()) return;
        String tmp = lines.get(idx);
        lines.set(idx, lines.get(to));
        lines.set(to, tmp);
        s.put(o.withValue(field, lines));
    }

    /** 重命名 id。 */
    public boolean rename(DesignSession s, String key, String newId) {
        DesignObject o = s.get(key);
        if (o == null || newId.isBlank() || !newId.matches("[a-zA-Z0-9_\\-]+")) return false;
        if (s.idTaken(o.kind(), newId, key)) return false;
        s.put(o.withId(newId));
        return true;
    }

    /** 删除对象。 */
    public void delete(DesignSession s, String key) {
        s.remove(key);
    }

    // ═══ 显示数据（UI 下发用）═══

    /** 编辑面板字段行：name|editor|label|value|options(CYCLE 逗号分隔)。 */
    public List<String> fieldRows(DesignObject o) {
        List<String> out = new ArrayList<>();
        for (DesignObject.FieldSpec f : DesignObject.schema(o.kind())) {
            if (f.editor() == DesignObject.Editor.SCRIPT
                || f.editor() == DesignObject.Editor.LIST) continue;
            String val = fieldStr(o, f.name());
            String opts = f.options().isEmpty() ? "-" : String.join(",", f.options());
            out.add(f.name() + "|" + f.editor().name() + "|" + f.label()
                + "|" + val.replace("|", "/") + "|" + opts);
        }
        return out;
    }

    /** 脚本/列表字段行：field|label|行数。List<Map> 字段（结构化行）不进组——防止行编辑把结构降级成字符串。 */
    public List<String> scriptGroupRows(DesignObject o) {
        List<String> out = new ArrayList<>();
        for (DesignObject.FieldSpec f : DesignObject.schema(o.kind())) {
            if (f.editor() != DesignObject.Editor.SCRIPT
                && f.editor() != DesignObject.Editor.LIST) continue;
            Object v = o.values().get(f.name());
            if (v == null) v = o.values().get(f.name().replace('-', '_'));
            if (v instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map) {
                continue; // 结构化字段（monsters/options/pool）走专用通道
            }
            out.add(f.name() + "|" + f.label() + "|" + o.strList(f.name()).size());
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
            return raw;
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
            case NPC -> DesignCoords.point(str(vals, "location"));
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
            case INTERACT -> str(vals, "type");
            case OBSTACLE -> str(vals, "trigger-mode").isEmpty()
                ? str(vals, "trigger_mode") : str(vals, "trigger-mode");
            case GROUP, ROOM_GROUP -> {
                Object mobs = vals.get("monsters");
                int n = mobs instanceof List<?> l ? l.size() : 0;
                yield str(vals, "spawn-timing") + "·" + n + "处";
            }
            case NPC -> str(vals, "name");
            case PUZZLE -> str(vals, "type") + "·" + strListRaw(vals, "steps").size() + "步";
            case TASK -> str(vals, "type");
            case REWARD -> str(vals, "type").isEmpty() ? "fixed" : str(vals, "type");
            case CHEST -> str(vals, "title");
            case BUFF -> str(vals, "name");
            case ROOM -> str(vals, "type");
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
            case CHEST -> vals.put("rows", 3);
            case ROOM -> vals.put("type", "combat");
            case BUFF -> { vals.put("pick", 1); vals.put("presentation", "panel"); }
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
        return deepCopy(sec.getValues(false));
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
        int vol = (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
        if (vol > 4000) return out; // 防误圈巨大区域卡服
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
