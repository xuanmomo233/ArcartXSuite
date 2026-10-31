package xuanmo.arcartxsuite.abyss.party;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 队伍实体：成员、队长、邀请、聊天开关、设置、
 * 组队大厅（公开可见/申请审批/目标副本）与房间消息流。
 */
public final class Party {

    private final UUID id = UUID.randomUUID();
    private volatile UUID leader;
    private final List<UUID> members = new ArrayList<>();
    private final Map<UUID, Long> invites = new ConcurrentHashMap<>(); // invitedUuid -> expireMillis
    private final Set<UUID> chatEnabled = ConcurrentHashMap.newKeySet();
    private volatile String name;
    private volatile String color = "&f";
    private final long createTime = System.currentTimeMillis();
    /** 队伍模式："free"自由组队（默认）/"guild"公会组队/外部插件自定义。 */
    private volatile String mode = "free";
    /** 队伍人数上限（>0 覆盖全局 party.max-members）。 */
    private volatile int maxSize;

    // ── 组队大厅 ──
    /** 加入方式：OPEN=直接加入，APPLY=需队长审批。 */
    public enum JoinMode { OPEN, APPLY }
    private volatile boolean listed;                       // 公开到组队大厅
    private volatile JoinMode joinMode = JoinMode.APPLY;   // 公开队伍的加入方式
    private volatile @Nullable String dungeonId;           // 目标副本
    private final Map<UUID, Long> applications = new ConcurrentHashMap<>(); // 申请人 -> 申请时间
    private final Set<UUID> ready = ConcurrentHashMap.newKeySet();
    private final Set<UUID> observers=ConcurrentHashMap.newKeySet();
    private volatile Runnable onChange=()->{};
    private long revision;
    void onChange(Runnable listener) { onChange=listener; }
    void changed() { onChange.run(); }
    public Set<UUID> observers() { return Set.copyOf(observers); }
    public synchronized long revision() { return revision; }
    private synchronized void configurationChanged() { ready.clear(); revision++; changed(); }
    public void resetReadiness() { ready.clear(); changed(); }
    private final Deque<FeedEntry> feed = new ArrayDeque<>();               // 房间消息流（聊天+事件）
    private static final int FEED_LIMIT = 40;

    /** 房间消息流条目（sender 为 null 表示系统事件）。 */
    public record FeedEntry(long time, @Nullable String sender, String text) { }

    public Party(UUID leader, int maxMembers) {
        this.leader = leader;
        this.maxSize = maxMembers;
        members.add(leader);
        observers.add(leader);
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public long createTime() { return createTime; }

    public String mode() { return mode; }
    public void mode(String m) { String value=m==null||m.isBlank()?"free":m; if(!value.equals(mode)){mode=value;configurationChanged();} }
    /** 实际人数上限（构造时写入，默认取全局 party.max-members）。 */
    public int maxSize() { return maxSize; }
    public void maxSize(int v) { if(v>0 && v!=maxSize){maxSize=v;configurationChanged();} }

    public synchronized List<UUID> members() { return List.copyOf(members); }
    public synchronized int size() { return members.size(); }
    public synchronized boolean isMember(UUID u) { return members.contains(u); }
    public boolean isLeader(UUID u) { return leader.equals(u); }

    public synchronized boolean addMember(UUID u) {
        if (members.contains(u) || members.size() >= maxSize) return false;
        members.add(u);
        observers.add(u);
        invites.remove(u);
        configurationChanged();
        return true;
    }

    public synchronized boolean removeMember(UUID u) {
        if (!members.remove(u)) return false;
        chatEnabled.remove(u);
        ready.remove(u);
        if (leader.equals(u) && !members.isEmpty()) {
            leader = members.get(0);
        }
        configurationChanged();
        return true;
    }

    public synchronized void setLeader(UUID u) {
        if(members.contains(u) && !leader.equals(u)){leader=u;configurationChanged();}
    }

    // ── 邀请 ──
    public void invite(UUID u, int timeoutSeconds) {
        invites.put(u, System.currentTimeMillis() + timeoutSeconds * 1000L);
        observers.add(u); changed();
    }

    public boolean hasInvite(UUID u) {
        Long exp = invites.get(u);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            invites.remove(u);
            return false;
        }
        return true;
    }

    /** 撤销某玩家的邀请（拒绝/过期外显式移除）。 */
    public boolean inviteRemove(UUID u) {
        return invites.remove(u) != null;
    }

    // ── 聊天 ──
    public boolean toggleChat(UUID u) {
        if (chatEnabled.remove(u)) return false;
        chatEnabled.add(u);
        return true;
    }
    public boolean isChatEnabled(UUID u) { return chatEnabled.contains(u); }

    // ── 设置 ──
    public String name() { return name != null ? name : "队伍-" + id.toString().substring(0, 4); }
    public void name(String n) { this.name = n; changed(); }
    public String color() { return color; }
    public void color(String c) { this.color = c; changed(); }

    // ── 广播 ──
    public void broadcast(String message) {
        for (UUID u : members()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) p.sendMessage(message);
        }
    }

    public List<Player> onlineMembers() {
        return members().stream().map(Bukkit::getPlayer)
            .filter(p -> p != null && p.isOnline()).toList();
    }

    // ── 组队大厅 ──

    public boolean listed() { return listed; }
    public void listed(boolean v) { listed = v; changed(); }
    public JoinMode joinMode() { return joinMode; }
    public void joinMode(JoinMode m) { joinMode = m == null ? JoinMode.APPLY : m; changed(); }
    public @Nullable String dungeonId() { return dungeonId; }
    public void dungeonId(@Nullable String id) { if(!java.util.Objects.equals(id,dungeonId)){dungeonId=id;configurationChanged();} }

    // ── 加入申请 ──
    /** 提交加入申请。 */
    public void apply(UUID u) {
        applications.put(u, System.currentTimeMillis());
        observers.add(u); changed();
    }

    /** 取出并移除申请（审批后清掉）。 */
    public boolean takeApplication(UUID u) {
        return applications.remove(u) != null;
    }

    public Set<UUID> applications() {
        return java.util.Set.copyOf(applications.keySet());
    }

    /** 拒绝申请。 */
    public boolean denyApplication(UUID u) {
        return applications.remove(u) != null;
    }

    // ── 准备状态 ──
    /** 切换成员准备状态，返回切换后的状态。 */
    public boolean toggleReady(UUID u) {
        if (!isMember(u)) return false;
        if (ready.remove(u)) { changed(); return false; }
        ready.add(u);
        changed();
        return true;
    }

    public boolean isReady(UUID u) {
        return isLeader(u) || ready.contains(u); // 队长视为已准备
    }

    /** 是否全员就绪（队长+所有非队长成员已准备）。 */
    public synchronized boolean allReady() {
        for (UUID u : members) {
            if (!isReady(u)) return false;
        }
        return !members.isEmpty();
    }

    // ── 消息流 ──
    /** 追加一条消息（聊天或系统事件），超出上限时丢弃最旧的。 */
    public synchronized void appendFeed(@Nullable String sender, String text) {
        if (feed.size() >= FEED_LIMIT) feed.pollFirst();
        feed.addLast(new FeedEntry(System.currentTimeMillis(), sender, text));
        changed();
    }

    /** 消息流快照（用于 UI 推送）。 */
    public synchronized List<FeedEntry> feedSnapshot() {
        return List.copyOf(feed);
    }
}
