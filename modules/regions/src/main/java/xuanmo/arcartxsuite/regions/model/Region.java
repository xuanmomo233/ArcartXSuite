package xuanmo.arcartxsuite.regions.model;

import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;

/**
 * 区域定义。
 */
public final class Region {

    public enum Kind { MANAGED, CLAIM, GLOBAL }

    private final String id;
    private final String world;
    private UUID uid;
    private Kind kind = Kind.MANAGED;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private int priority;
    private String parentId;

    private final Set<Material> blockedInteractions = EnumSet.noneOf(Material.class);
    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();
    private final Set<String> ownerGroups = new HashSet<>();
    private final Set<String> memberGroups = new HashSet<>();
    private final Map<RegionFlag, RegionFlag.State> flags = new EnumMap<>(RegionFlag.class);
    private final Map<RegionFlag, String> flagData = new EnumMap<>(RegionFlag.class);

    public Region(String id, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.id = id;
        this.world = world;
        this.uid = UUID.nameUUIDFromBytes(("axs:regions:" + world + ":" + id).getBytes(StandardCharsets.UTF_8));
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    // ─── 基础属性 ───

    public String id() { return id; }
    public String world() { return world; }
    public String key() { return world + ":" + id; }
    public UUID uid() { return uid; }
    public void setUid(UUID uid) { this.uid = uid; }
    public Kind kind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
    public int priority() { return priority; }
    public String parentId() { return parentId; }

    public void setPriority(int priority) { this.priority = priority; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public void redefine(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public Region copy(String newId) {
        Region copy = new Region(newId, world, minX, minY, minZ, maxX, maxY, maxZ);
        copy.uid = uid;
        copy.blockedInteractions.addAll(blockedInteractions);
        copy.kind = kind;
        copy.priority = priority;
        copy.parentId = parentId;
        copy.owners.addAll(owners);
        copy.members.addAll(members);
        copy.ownerGroups.addAll(ownerGroups);
        copy.memberGroups.addAll(memberGroups);
        copy.flags.putAll(flags);
        copy.flagData.putAll(flagData);
        return copy;
    }

    public long volume() {
        return volume(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static long volume(int x1, int y1, int z1, int x2, int y2, int z2) {
        try {
            long width = Math.abs((long) x2 - x1) + 1;
            long height = Math.abs((long) y2 - y1) + 1;
            long length = Math.abs((long) z2 - z1) + 1;
            return Math.multiplyExact(Math.multiplyExact(width, height), length);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    // ─── 包含检测 ───

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(String w, int x, int y, int z) {
        return world.equals(w) && contains(x, y, z);
    }

    public boolean intersects(Region other) {
        if (!world.equals(other.world)) return false;
        return minX <= other.maxX && maxX >= other.minX
            && minY <= other.maxY && maxY >= other.minY
            && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    // ─── 标志 ───

    public RegionFlag.State getFlag(RegionFlag flag) {
        return flags.getOrDefault(flag, RegionFlag.State.NONE);
    }

    public void setFlag(RegionFlag flag, RegionFlag.State state) {
        if (state == RegionFlag.State.NONE) {
            flags.remove(flag);
            flagData.remove(flag);
        } else {
            flags.put(flag, state);
        }
    }

    public String getFlagData(RegionFlag flag) {
        return flagData.get(flag);
    }

    public void setFlagData(RegionFlag flag, String data) {
        if (data == null || data.isBlank()) {
            flagData.remove(flag);
        } else {
            flagData.put(flag, data);
        }
    }

    public Map<RegionFlag, RegionFlag.State> flags() {
        return Collections.unmodifiableMap(flags);
    }

    public Map<RegionFlag, String> flagData() {
        return Collections.unmodifiableMap(flagData);
    }

    public Set<Material> blockedInteractions() {
        return Collections.unmodifiableSet(blockedInteractions);
    }

    public void setBlockedInteraction(Material material, boolean blocked) {
        if (blocked) blockedInteractions.add(material);
        else blockedInteractions.remove(material);
    }

    // ─── 成员管理 ───

    public Set<UUID> owners() { return owners; }
    public Set<UUID> members() { return members; }
    public Set<String> ownerGroups() { return ownerGroups; }
    public Set<String> memberGroups() { return memberGroups; }

    public void addOwner(UUID uuid) { owners.add(uuid); }
    public void removeOwner(UUID uuid) { owners.remove(uuid); }
    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public void addOwnerGroup(String group) { ownerGroups.add(group); }
    public void removeOwnerGroup(String group) { ownerGroups.remove(group); }
    public void addMemberGroup(String group) { memberGroups.add(group); }
    public void removeMemberGroup(String group) { memberGroups.remove(group); }

    public boolean isOwner(UUID uuid) { return owners.contains(uuid); }
    public boolean isMember(UUID uuid) { return members.contains(uuid) || owners.contains(uuid); }

    /**
     * 检查玩家是否为此区域的成员（含所有者）。
     * 如果玩家属于成员组也算。
     */
    public boolean hasAccess(UUID uuid, Set<String> playerGroups) {
        if (isOwner(uuid) || members.contains(uuid)) return true;
        if (playerGroups != null) {
            for (String group : playerGroups) {
                if (ownerGroups.contains(group) || memberGroups.contains(group)) return true;
            }
        }
        return false;
    }
}
