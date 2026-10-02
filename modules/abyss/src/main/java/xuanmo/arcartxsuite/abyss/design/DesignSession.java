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

    private volatile PendingMark pending;
    private volatile int[] regionPointA;
    private volatile String selectedNode;
    private volatile long lastAction = System.currentTimeMillis();

    public DesignSession(UUID playerId, AbyssInstance instance) {
        this.playerId = playerId;
        this.instance = instance;
    }

    public UUID playerId() { return playerId; }
    public AbyssInstance instance() { return instance; }
    public String designToken() { return designToken; }

    public Map<String, DesignObject> objects() { return objects; }
    public @Nullable DesignObject get(String key) { return objects.get(key); }

    public void put(DesignObject obj) {
        objects.put(obj.key(), obj);
        originalIds.putIfAbsent(obj.key(), obj.id());
    }

    public void putAll(List<DesignObject> list) {
        for (DesignObject o : list) put(o);
    }

    /** 删除对象（新建的物理移除；模板原有的进删除名单）。 */
    public void remove(String key) {
        DesignObject o = objects.remove(key);
        if (o != null && originalIds.containsKey(key)) {
            deletions.put(key, originalIds.get(key));
        }
    }

    public Map<String, String> deletions() { return deletions; }
    public String originalId(String key) { return originalIds.getOrDefault(key, key); }

    /** 保存后重置追踪态（对象表/删除名单/原 id 索引全清，由 loadObjects 重建）。 */
    public void resetTracking() {
        objects.clear();
        deletions.clear();
        originalIds.clear();
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
            if (node.equals(o.node())) out.add(o);
        }
        return out;
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
        for (DesignObject o : objects.values()) {
            if (o.kind() == kind && o.id().equals(id)
                && (exceptKey == null || !o.key().equals(exceptKey))) {
                return true;
            }
        }
        return false;
    }
}
