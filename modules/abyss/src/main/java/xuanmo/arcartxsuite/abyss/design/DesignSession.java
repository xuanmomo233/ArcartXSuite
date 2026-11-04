package xuanmo.arcartxsuite.abyss.design;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;

/**
 * 设计会话：一名设计师对一个地牢模板的可写编辑状态。
 * <p>
 * 持有编辑中的对象快照表（key → DesignObject）、删除名单、
 * 待标记状态（{@link PendingMark}）、当前选中节点。
 * 全部对象操作走 {@link DesignManager}，本类只承载状态。
 */
public final class DesignSession {

    /** 待标记：等玩家右键方块/闭合选区后落到目标对象上。 */
    public record PendingMark(
        Kind kind,
        @Nullable String targetKey,   // null=新建对象（type 已定）；非空=重新定位已有对象
        @Nullable String field,       // 重新定位时写哪个字段（location/region）
        DesignObject.Kind objKind     // 新建对象时的类型
    ) {
        public enum Kind { POINT, REGION }
    }

    private final UUID playerId;
    private final AbyssInstance instance;
    private final String designToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    /** 编辑中对象表（key → 快照）。 */
    private final Map<String, DesignObject> objects = new LinkedHashMap<>();
    /** 删除名单（原模板存在、本次删除的 key → 原 id）。 */
    private final Map<String, String> deletions = new LinkedHashMap<>();
    /** 原 id 索引（重命名时删旧键用）：key → 原始 id。 */
    private final Map<String, String> originalIds = new ConcurrentHashMap<>();
    private final Map<String, DesignObject> originalObjects = new LinkedHashMap<>();

    private volatile PendingMark pending;
    private volatile int[] regionPointA;
    private volatile String selectedNode;
    private volatile long lastAction = System.currentTimeMillis();
    private boolean saving;
    private DraftTrial trial;
    public DraftTrial trial() { return trial; }
    public void trial(DraftTrial next) {
        if (trial != null) trial.close();
        trial = next;
    }
    private boolean worldDirty;
    private boolean worldSnapshot;
    private boolean unknownWorldEdits;
    private final java.util.Set<String> editedBlocks = new java.util.LinkedHashSet<>();
    private long discardRequestedAt;
    private final Map<String, Long> deletionRequests = new LinkedHashMap<>();
    private final Map<String, Integer> linePages = new LinkedHashMap<>();
    private final Map<String, String> fileHashes = new LinkedHashMap<>();

    public DesignSession(UUID playerId, AbyssInstance instance) {
        this.playerId = playerId;
        this.instance = instance;
    }

    public UUID playerId() { return playerId; }
    public AbyssInstance instance() { return instance; }
    public String designToken() { return designToken; }
    public boolean saving() { return saving; }
    public void saving(boolean value) { saving = value; }
    public boolean worldDirty() { return worldDirty; }
    public boolean worldSnapshot() { return worldSnapshot; }
    public void worldSnapshot(boolean value) { worldSnapshot = value; }
    public void worldDirty(boolean value) {
        worldDirty = value;
        if (!value) { editedBlocks.clear(); unknownWorldEdits = false; }
        discardRequestedAt = 0;
    }
    public void markBuilt(org.bukkit.Location location) {
        editedBlocks.add(location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        worldDirty(true);
    }
    public void markUnknownBuild() { unknownWorldEdits = true; worldDirty(true); }
    public void acknowledgeRegion(int[] bounds) {
        editedBlocks.removeIf(value -> {
            int[] p = DesignCoords.point(value);
            return p != null && DesignCoords.inBox(bounds, p[0], p[1], p[2]);
        });
        worldDirty = unknownWorldEdits || !editedBlocks.isEmpty();
    }
    public Map<String, String> fileHashes() { return fileHashes; }
    public int linePage(String object, String field) { return linePages.getOrDefault(object + ":" + field, 0); }
    public void linePage(String object, String field, int page) {
        linePages.put(object + ":" + field, Math.max(0, Math.min(4096, page)));
    }
    public boolean confirmDiscard() {
        long now = System.currentTimeMillis();
        if (discardRequestedAt > 0 && now - discardRequestedAt < 10000) return true;
        discardRequestedAt = now;
        return false;
    }
    public boolean confirmDeletion(String key) {
        long now = System.currentTimeMillis();
        Long previous = deletionRequests.put(key, now);
        if (previous != null && now - previous < 10000) {
            deletionRequests.remove(key);
            return true;
        }
        return false;
    }
    public String nextId(DesignObject.Kind kind, String prefix) {
        for (int i = 1; ; i++) {
            String id = prefix + "_" + i;
            String key = kind.name().toLowerCase() + ":" + id;
            if (!idTaken(kind, id, null) && !objects.containsKey(key)
                && !deletions.containsKey(key)) return id;
        }
    }

    public Map<String, DesignObject> objects() { return objects; }
    public @Nullable DesignObject get(String key) { return objects.get(key); }

    public void put(DesignObject obj) {
        objects.put(obj.key(), obj);
        discardRequestedAt = 0;
    }

    public void putAll(List<DesignObject> list) {
        for (DesignObject o : list) {
            put(o);
            originalIds.putIfAbsent(o.key(), o.id());
            originalObjects.put(o.key(), o);
        }
    }

    /** 删除对象（新建的物理移除；模板原有的进删除名单）。 */
    public void remove(String key) {
        DesignObject o = objects.remove(key);
        if (o != null && originalIds.containsKey(key)) {
            deletions.put(key, originalIds.get(key));
        }
    }

    public Map<String, String> deletions() { return deletions; }
    public @Nullable DesignObject originalObject(String key) { return originalObjects.get(key); }
    public String originalId(String key) {
        DesignObject o = objects.get(key);
        return originalIds.getOrDefault(key, o != null ? o.id() : key);
    }

    /** 保存后重置追踪态（对象表/删除名单/原 id 索引全清，由 loadObjects 重建）。 */
    public void resetTracking() {
        objects.clear();
        deletions.clear();
        originalIds.clear();
        originalObjects.clear();
        fileHashes.clear();
        linePages.clear();
        pending = null;
        regionPointA = null;
    }

    /** 未保存改动数（脏对象 + 删除数）。 */
    public int dirtyCount() {
        int n = deletions.size();
        for (DesignObject o : objects.values()) {
            if (o.dirty()) n++;
        }
        return n;
    }

    public boolean hasDirty() { return dirtyCount() > 0; }

    public @Nullable PendingMark pending() { return pending; }
    public void pending(@Nullable PendingMark p) { this.pending = p; this.regionPointA = null; }

    /** 两点选区的 A 点（REGION pending 用）。 */
    public @Nullable int[] regionPointA() { return regionPointA; }
    public void regionPointA(@Nullable int[] a) { this.regionPointA = a; }

    public @Nullable String selectedNode() { return selectedNode; }
    public void selectedNode(@Nullable String node) { this.selectedNode = node; }

    public void touch() { lastAction = System.currentTimeMillis(); }
    public long lastAction() { return lastAction; }

    /** 该节点下的对象（按插入序）。 */
    public List<DesignObject> nodeObjects(String node) {
        List<DesignObject> out = new ArrayList<>();
        for (DesignObject o : objects.values()) {
            if (node.equals(o.node()) || (o.kind() == DesignObject.Kind.FLOOR && node.equals(o.key()))) out.add(o);
        }
        return out;
    }

    public List<DesignObject> floorsInOrder() {
        return objects.values().stream().filter(o -> o.kind() == DesignObject.Kind.FLOOR)
            .sorted(java.util.Comparator.comparingDouble(o ->
                o.values().get("design-order") instanceof Number n ? n.doubleValue() : Double.MAX_VALUE))
            .toList();
    }

    /** 新对象唯一键（kind:id，id 冲突自动加序号）。 */
    public String uniqueKey(DesignObject.Kind kind, String id) {
        String base = kind.name().toLowerCase() + ":" + id;
        if (!objects.containsKey(base)) return base;
        for (int i = 2; ; i++) {
            String k = base + "_" + i;
            if (!objects.containsKey(k)) return k;
        }
    }

    /** 会话内某类型对象 id 是否已被占（新建/重命名校验）。 */
    public boolean idTaken(DesignObject.Kind kind, String id, @Nullable String exceptKey) {
        DesignObject edited = exceptKey != null ? objects.get(exceptKey) : null;
        for (DesignObject o : objects.values()) {
            if (kind == DesignObject.Kind.ROOM_GROUP && edited != null && !edited.path().equals(o.path())) continue;
            if (o.kind() == kind && o.id().equals(id)
                && (exceptKey == null || !o.key().equals(exceptKey))) {
                return true;
            }
        }
        // Reserve original addresses until a successful save. Otherwise A->B and B->C can delete a newly written B.
        for (DesignObject o : originalObjects.values()) {
            if (kind == DesignObject.Kind.ROOM_GROUP && edited != null && !edited.path().equals(o.path())) continue;
            if (o.kind() == kind && o.id().equals(id) && !o.key().equals(exceptKey)) return true;
        }
        return false;
    }
}
