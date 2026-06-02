package xuanmo.arcartxsuite.regions.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.model.Selection;
import xuanmo.arcartxsuite.regions.storage.RegionsRepository;

/**
 * 区域管理器 — 内存缓存所有区域，提供快速查询。
 */
public final class RegionManager {

    private final RegionsRepository repository;
    private final RegionsConfiguration config;
    private final Logger logger;

    private final List<Region> regions = new ArrayList<>();
    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastRegion = new ConcurrentHashMap<>();

    public RegionManager(RegionsRepository repository, RegionsConfiguration config, Logger logger) {
        this.repository = repository;
        this.config = config;
        this.logger = logger;
    }

    public void loadAll() throws SQLException {
        regions.clear();
        regions.addAll(repository.loadAllRegions());
        logger.info("已加载 " + regions.size() + " 个区域。");
    }

    // ─── 选区 ───

    public Selection getSelection(UUID player) {
        return selections.computeIfAbsent(player, k -> new Selection());
    }

    public void clearSelection(UUID player) {
        selections.remove(player);
    }

    // ─── 进出追踪 ───

    public String getLastRegion(UUID player) {
        return lastRegion.get(player);
    }

    public void setLastRegion(UUID player, String regionId) {
        if (regionId == null) lastRegion.remove(player);
        else lastRegion.put(player, regionId);
    }

    // ─── 区域 CRUD ───

    public Region getRegion(String id, String world) {
        for (Region region : regions) {
            if (region.id().equalsIgnoreCase(id) && region.world().equals(world)) return region;
        }
        return null;
    }

    public Region getRegion(String id) {
        for (Region region : regions) {
            if (region.id().equalsIgnoreCase(id)) return region;
        }
        return null;
    }

    public List<Region> getRegionsInWorld(String world) {
        List<Region> result = new ArrayList<>();
        for (Region region : regions) {
            if (region.world().equals(world)) result.add(region);
        }
        return result;
    }

    public List<Region> getAllRegions() {
        return Collections.unmodifiableList(regions);
    }

    public Region createRegion(String id, Selection sel) throws SQLException {
        Region region = new Region(id, sel.world(), sel.x1(), sel.y1(), sel.z1(), sel.x2(), sel.y2(), sel.z2());
        repository.saveRegion(region);
        regions.add(region);
        return region;
    }

    public void deleteRegion(Region region) throws SQLException {
        repository.deleteRegion(region.id(), region.world());
        regions.remove(region);
    }

    public void saveRegion(Region region) throws SQLException {
        repository.saveRegion(region);
    }

    public int countRegionsByOwner(UUID owner) {
        int count = 0;
        for (Region region : regions) {
            if (region.isOwner(owner)) count++;
        }
        return count;
    }

    // ─── 查询 ───

    /**
     * 获取某坐标所在的所有区域（按优先级降序）。
     */
    public List<Region> getRegionsAt(String world, int x, int y, int z) {
        List<Region> result = new ArrayList<>();
        for (Region region : regions) {
            if (region.contains(world, x, y, z)) {
                result.add(region);
            }
        }
        result.sort(Comparator.comparingInt(Region::priority).reversed());
        return result;
    }

    /**
     * 获取某位置的最高优先级区域。
     */
    public Region getHighestPriorityRegion(String world, int x, int y, int z) {
        List<Region> at = getRegionsAt(world, x, y, z);
        return at.isEmpty() ? null : at.get(0);
    }

    /**
     * 检查某位置的某个标志状态（含继承）。
     * 搜索逻辑：按优先级从高到低，找到第一个设置了该标志的区域，返回其值。
     * 如果所有区域都是 NONE，返回 NONE。
     */
    public RegionFlag.State queryFlag(String world, int x, int y, int z, RegionFlag flag) {
        List<Region> at = getRegionsAt(world, x, y, z);
        for (Region region : at) {
            RegionFlag.State state = resolveFlag(region, flag);
            if (state != RegionFlag.State.NONE) return state;
        }
        return RegionFlag.State.NONE;
    }

    /**
     * 检查标志，考虑玩家是否为区域成员（成员跳过保护）。
     */
    public RegionFlag.State queryFlagForPlayer(Location loc, RegionFlag flag, Player player) {
        String world = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        List<Region> at = getRegionsAt(world, x, y, z);
        if (at.isEmpty()) return RegionFlag.State.NONE;

        // 管理员跳过
        if (player.hasPermission("axs.regions.bypass")) return RegionFlag.State.ALLOW;

        for (Region region : at) {
            RegionFlag.State state = resolveFlag(region, flag);
            if (state == RegionFlag.State.NONE) continue;
            if (state == RegionFlag.State.DENY) {
                // 成员检查 — 仅方块/交互类标志对成员豁免
                if (isMemberExemptFlag(flag) && region.hasAccess(player.getUniqueId(), getPlayerGroups(player))) {
                    return RegionFlag.State.ALLOW;
                }
                return RegionFlag.State.DENY;
            }
            return state;
        }
        return RegionFlag.State.NONE;
    }

    private RegionFlag.State resolveFlag(Region region, RegionFlag flag) {
        RegionFlag.State state = region.getFlag(flag);
        if (state != RegionFlag.State.NONE) return state;
        // 继承父区域
        if (region.parentId() != null) {
            Region parent = getRegion(region.parentId(), region.world());
            if (parent != null) return resolveFlag(parent, flag);
        }
        return RegionFlag.State.NONE;
    }

    private boolean isMemberExemptFlag(RegionFlag flag) {
        return switch (flag) {
            case BLOCK_BREAK, BLOCK_PLACE, USE, CHEST_ACCESS, VEHICLE_DESTROY, VEHICLE_PLACE,
                 ITEM_DROP, ITEM_PICKUP, RIDE, SLEEP -> true;
            default -> false;
        };
    }

    private Set<String> getPlayerGroups(Player player) {
        // 简单实现：通过权限节点 axs.regions.group.<name> 确定
        Set<String> groups = new java.util.HashSet<>();
        for (var perm : player.getEffectivePermissions()) {
            String p = perm.getPermission();
            if (p.startsWith("axs.regions.group.") && perm.getValue()) {
                groups.add(p.substring("axs.regions.group.".length()));
            }
        }
        return groups;
    }

    public void cleanup(UUID player) {
        selections.remove(player);
        lastRegion.remove(player);
    }
}
