package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Regions 的位置判定与区域标识。应在 Bukkit 主线程查询。 */
@PublicCapability
public interface RegionProtectionQueryable {

    /**
     * 玩家实际跨越区域边界后发布。既有字段为 region_id、type，
     * 补充字段为 world、region_key、region_uuid、cause。
     */
    String REGION_CHANGE_TOPIC = "axs.regions.region_change";

    /**
     * 查询 Regions 对指定坐标和标志的判定。player 为 null 时忽略成员和绕过权限。
     * 标志名称使用配置键，例如 block-break。未知标志抛出 IllegalArgumentException。
     * 包含 Regions 的世界默认旗标。按方块材质配置的交互限制由实际交互事件判定。
     */
    @NotNull Decision decide(@NotNull String world, int x, int y, int z, @NotNull String flag, @Nullable Player player);

    /**
     * 读取某世界的有界区域，供地图等消费者绘制边界。世界默认规则没有几何边界。
     * UUID 在区域改名、调整范围后保持不变。
     */
    @NotNull List<Boundary> boundaries(@NotNull String world);

    enum State { NONE, ALLOW, DENY }
    enum Reason { UNSET, BYPASS, DIRECT, INHERITED, MEMBER_EXEMPT }
    enum Kind { MANAGED, CLAIM, GLOBAL }

    record Identity(@NotNull UUID uuid, @NotNull String world, @NotNull String id, @NotNull Kind kind) {}

    record Boundary(@NotNull Identity region, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int priority) {}

    record Decision(@NotNull State state, @NotNull Reason reason, @Nullable Identity matched, @Nullable Identity source) {}
}
