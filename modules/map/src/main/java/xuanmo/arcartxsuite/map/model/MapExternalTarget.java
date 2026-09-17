package xuanmo.arcartxsuite.map.model;

import xuanmo.arcartxsuite.api.capability.MapExternalTargetSpec;

/**
 * 外部导航目标记录，表示由其他模块或外部插件（如 QuestGPS）注入的临时导航点。
 * <p>
 * 关联模块：map。与 {@code MapNavigable} capability 配合，由来源全权决定坐标与导航行为；
 * 地图只负责渲染标记（紫色）与详情面板、转发"导航/取消"点击。
 *
 * @param targetId    目标唯一标识
 * @param source      来源模块标识（如 "questgps"）
 * @param worldId     所在世界 ID
 * @param title       显示标题
 * @param description 显示描述
 * @param x           X 坐标
 * @param y           Y 坐标
 * @param z           Z 坐标
 * @param iconId      图标 id（可空）
 * @param navigated   来源当前是否正在导航该目标
 * @param sortOrder   排序权重，越小越靠前
 */
public record MapExternalTarget(
    String targetId,
    String source,
    String worldId,
    String title,
    String description,
    double x,
    double y,
    double z,
    String iconId,
    boolean navigated,
    int sortOrder
) {

    /** 从公开 spec 构建内部记录。 */
    public static MapExternalTarget fromSpec(MapExternalTargetSpec spec) {
        return new MapExternalTarget(
            spec.targetId(), spec.source(), spec.worldId(),
            spec.title(), spec.description(),
            spec.x(), spec.y(), spec.z(),
            spec.iconId(), spec.navigated(), spec.sortOrder()
        );
    }

    /** 返回 navigated 标志变更后的新实例（record 不可变，整体替换）。 */
    public MapExternalTarget withNavigated(boolean value) {
        return navigated == value ? this : new MapExternalTarget(
            targetId, source, worldId, title, description, x, y, z, iconId, value, sortOrder
        );
    }
}
