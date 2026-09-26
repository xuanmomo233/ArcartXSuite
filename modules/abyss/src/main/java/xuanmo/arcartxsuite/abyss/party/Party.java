package xuanmo.arcartxsuite.abyss.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration.LootMode;

/**
 * 队伍实体：成员（有序=轮换掉落顺序）、队长、邀请、聊天开关、仓库、设置。
 */
public final class Party {

    private final UUID id = UUID.randomUUID();
    private volatile UUID leader;
    private final List<UUID> members = new ArrayList<>();
    private final Map<UUID, Long> invites = new ConcurrentHashMap<>(); // invitedUuid -> expireMillis
    private final Set<UUID> chatEnabled = ConcurrentHashMap.newKeySet();
    private final Map<Integer, ItemStack> storage = new ConcurrentHashMap<>(); // slot -> item（简化为线性槽位）
    private volatile String name;
    private volatile String color = "&f";
    private volatile LootMode lootMode;
    private volatile int lootCursor;
    private final long createTime = System.currentTimeMillis();

    public Party(UUID leader, int maxMembers, LootMode lootMode) {
        this.leader = leader;
        this.lootMode = lootMode;
        members.add(leader);
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public long createTime() { return createTime; }

    public synchronized List<UUID> members() { return List.copyOf(members); }
    public synchronized int size() { return members.size(); }
    public synchronized boolean isMember(UUID u) { return members.contains(u); }
    public boolean isLeader(UUID u) { return leader.equals(u); }

    public synchronized boolean addMember(UUID u, int maxMembers) {
        if (members.contains(u) || members.size() >= maxMembers) return false;
        members.add(u);
        invites.remove(u);
        return true;
    }

    public synchronized boolean removeMember(UUID u) {
        if (!members.remove(u)) return false;
        chatEnabled.remove(u);
        if (leader.equals(u) && !members.isEmpty()) {
            leader = members.get(0);
        }
        return true;
    }

    public synchronized void setLeader(UUID u) {
        if (members.contains(u)) leader = u;
    }

    // ── 邀请 ──
    public void invite(UUID u, int timeoutSeconds) {
        invites.put(u, System.currentTimeMillis() + timeoutSeconds * 1000L);
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

    // ── 聊天 ──
    public boolean toggleChat(UUID u) {
        if (chatEnabled.remove(u)) return false;
        chatEnabled.add(u);
        return true;
    }
    public boolean isChatEnabled(UUID u) { return chatEnabled.contains(u); }

    // ── 仓库 ──
    public @Nullable ItemStack getStorageItem(int slot) { return storage.get(slot); }
    public void setStorageItem(int slot, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) storage.remove(slot);
        else storage.put(slot, item.clone());
    }
    public Map<Integer, ItemStack> storageItems() { return Map.copyOf(storage); }

    // ── 设置 ──
    public String name() { return name != null ? name : "队伍-" + id.toString().substring(0, 4); }
    public void name(String n) { this.name = n; }
    public String color() { return color; }
    public void color(String c) { this.color = c; }
    public LootMode lootMode() { return lootMode; }
    public void lootMode(LootMode m) { this.lootMode = m; lootCursor = 0; }

    /** 轮换掉落下一位成员。 */
    public synchronized @Nullable UUID nextLootOwner() {
        if (members.isEmpty()) return null;
        UUID u = members.get(lootCursor % members.size());
        lootCursor++;
        return u;
    }

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
}
