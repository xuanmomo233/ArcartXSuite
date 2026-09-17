package xuanmo.arcartxsuite.api.capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 地图外部目标规格（公开契约）。
 * <p>
 * 由来源模块/插件通过 {@code MapNavigable.upsertExternalTarget} 推送到地图，
 * 在地图与 HUD 上渲染为紫色标记点。外部目标不占用玩家路径点数量上限，
 * 生命周期完全由来源控制（推送/更新/删除）。
 *
 * @param targetId    目标唯一标识（来源命名空间内唯一）
 * @param source      来源标识（如 "questgps"），用于归属清理与导航回调路由
 * @param worldId     世界 ID（地图 worlds 目录中的世界名，大小写不敏感）
 * @param title       标记点与详情面板标题
 * @param description 详情面板描述文本
 * @param x           X 坐标
 * @param y           Y 坐标
 * @param z           Z 坐标
 * @param iconId      图标 id（可空，预留给客户端自定义图标渲染）
 * @param navigated   来源当前是否正在导航该目标（true 时面板按钮显示"取消"）
 * @param sortOrder   排序权重，越小越靠前
 */
public record MapExternalTargetSpec(
    @NotNull String targetId,
    @NotNull String source,
    @NotNull String worldId,
    @NotNull String title,
    @Nullable String description,
    double x,
    double y,
    double z,
    @Nullable String iconId,
    boolean navigated,
    int sortOrder
) {

    /**
     * 便捷构造：navigated=false、sortOrder=0、无图标。
     */
    public MapExternalTargetSpec(
        @NotNull String targetId,
        @NotNull String source,
        @NotNull String worldId,
        @NotNull String title,
        @Nullable String description,
        double x,
        double y,
        double z
    ) {
        this(targetId, source, worldId, title, description, x, y, z, null, false, 0);
    }
}
