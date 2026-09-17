package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 区域查询能力接口（公开 capability）。
 * <p>
 * 由 Regions 模块实现，供外部插件查询保护区域信息。
 * 区域变更可订阅 EventBus 主题 {@code axs.regions.region_change}。
 * <p>
 * 外部插件经 {@code AxsCapabilities.get(RegionQueryable.class)} 获取。
 * <p>
 * 线程约定：请在 Bukkit 主线程调用。Region 内部成员集合非并发容器，
 * 快照拷贝遇异步并发修改可能抛出 ConcurrentModificationException。
 */
@PublicCapability
public interface RegionQueryable {

    /**
     * 查询指定坐标命中的全部区域（按优先级排序）。
     */
    @NotNull List<RegionInfo> regionsAt(@NotNull String world, int x, int y, int z);

    /**
     * 查询指定世界中的全部区域。
     */
    @NotNull List<RegionInfo> regionsInWorld(@NotNull String world);

    /**
     * 查询全部区域。
     */
    @NotNull List<RegionInfo> allRegions();

    /**
     * 统计某玩家作为 owner 的区域数量。
     */
    int countRegionsByOwner(@NotNull UUID owner);

    /**
     * 区域只读视图。
     *
     * @param owners  所有者 UUID 集合（不可变）
     * @param members 成员 UUID 集合（不可变，不含 owner）
     */
    record RegionInfo(
        @NotNull String id,
        @NotNull String world,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        int priority,
        @Nullable String parentId,
        @NotNull Set<UUID> owners,
        @NotNull Set<UUID> members
    ) {
        public RegionInfo {
            owners = Set.copyOf(owners);
            members = Set.copyOf(members);
        }

        /** 判断坐标是否落在本区域内。 */
        public boolean contains(@NotNull String w, int x, int y, int z) {
            return world.equals(w)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
        }

        /** 玩家是否为所有者或成员（不含权限组判定）。 */
        public boolean hasAccess(@NotNull UUID playerUuid) {
            return owners.contains(playerUuid) || members.contains(playerUuid);
        }
    }
}
