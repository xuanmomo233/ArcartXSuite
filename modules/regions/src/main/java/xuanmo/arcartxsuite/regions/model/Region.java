package xuanmo.arcartxsuite.regions.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 区域定义。
 */
public final class Region {

    private final String id;
    private final String world;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private int priority;
    private String parentId;

    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();
    private final Set<String> ownerGroups = new HashSet<>();
    private final Set<String> memberGroups = new HashSet<>();
    private final Map<RegionFlag, RegionFlag.State> flags = new EnumMap<>(RegionFlag.class);
    private final Map<RegionFlag, String> flagData = new EnumMap<>(RegionFlag.class);

    public Region(String id, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.id = id;
        this.world = world;
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

    public long volume() {
        return (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
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
