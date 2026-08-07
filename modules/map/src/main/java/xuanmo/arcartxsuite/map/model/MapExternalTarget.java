package xuanmo.arcartxsuite.map.model;

/**
 * 外部导航目标记录，表示由其他模块（如 QuestGPS）注入的临时导航点。
 * <p>
 * 关联模块：map。与 {@code MapNavigable} capability 配合，
 * 供任务系统等外部模块向地图推送目标坐标并在 UI 上显示。
 *
 * @param targetId    目标唯一标识
 * @param source      来源模块标识（如 "QuestGPS"）
 * @param worldId     所在世界 ID
 * @param title       显示标题
 * @param description 显示描述
 * @param x           X 坐标
 * @param y           Y 坐标
 * @param z           Z 坐标
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
    int sortOrder
) {
}
