package xuanmo.arcartxsuite.map.model;

/**
 * 玩家地图视图状态，记录玩家在地图 UI 中的当前选择与 HUD 可见性。
 * <p>
 * 关联模块：map。由 {@code MapService} 为每个在线玩家维护一份实例，
 * 用于驱动菜单快照和 HUD 快照的生成。选择切换时会自动清除其他类型的选中项。
 */
public final class MapPlayerViewState {

    private String selectedWorldId = "";
    private String selectedAnchorId = "";
    private String selectedWaypointId = "";
    private String selectedExternalTargetId = "";
    private boolean hudVisible;

    /**
     * 获取当前选中的世界 ID。
     *
     * @return 世界 ID，未选中时返回空字符串
     */
    public String selectedWorldId() {
        return selectedWorldId;
    }

    /**
     * 获取当前选中的锚点 ID。
     *
     * @return 锚点 ID，未选中时返回空字符串
     */
    public String selectedAnchorId() {
        return selectedAnchorId;
    }

    /**
     * 获取当前选中的路径点 ID。
     *
     * @return 路径点 ID，未选中时返回空字符串
     */
    public String selectedWaypointId() {
        return selectedWaypointId;
    }

    /**
     * 获取当前选中的外部目标 ID。
     *
     * @return 外部目标 ID，未选中时返回空字符串
     */
    public String selectedExternalTargetId() {
        return selectedExternalTargetId;
    }

    /**
     * 获取 HUD 是否可见。
     *
     * @return {@code true} 表示 HUD 当前处于显示状态
     */
    public boolean hudVisible() {
        return hudVisible;
    }

    /**
     * 切换选中的世界，同时清除锚点、路径点和外部目标的选中状态。
     *
     * @param worldId 世界 ID
     */
    public void selectWorld(String worldId) {
        selectedWorldId = safe(worldId);
        selectedAnchorId = "";
        selectedWaypointId = "";
        selectedExternalTargetId = "";
    }

    /**
     * 选中指定锚点，同时清除路径点和外部目标的选中状态。
     *
     * @param anchorId 锚点 ID
     */
    public void selectAnchor(String anchorId) {
        selectedAnchorId = safe(anchorId);
        selectedWaypointId = "";
        selectedExternalTargetId = "";
    }

    /**
     * 选中指定路径点，同时清除锚点和外部目标的选中状态。
     *
     * @param waypointId 路径点 ID
     */
    public void selectWaypoint(String waypointId) {
        selectedWaypointId = safe(waypointId);
        selectedAnchorId = "";
        selectedExternalTargetId = "";
    }

    /**
     * 选中指定外部目标，同时清除锚点和路径点的选中状态。
     *
     * @param targetId 外部目标 ID
     */
    public void selectExternalTarget(String targetId) {
        selectedExternalTargetId = safe(targetId);
        selectedAnchorId = "";
        selectedWaypointId = "";
    }

    /**
     * 清除锚点、路径点和外部目标的选中状态（不影响世界选择）。
     */
    public void clearSelection() {
        selectedAnchorId = "";
        selectedWaypointId = "";
        selectedExternalTargetId = "";
    }

    /**
     * 设置 HUD 可见性。
     *
     * @param hudVisible {@code true} 表示显示 HUD
     */
    public void setHudVisible(boolean hudVisible) {
        this.hudVisible = hudVisible;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
