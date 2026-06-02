package xuanmo.arcartxsuite.map.model;

public final class MapPlayerViewState {

    private String selectedWorldId = "";
    private String selectedAnchorId = "";
    private String selectedWaypointId = "";
    private String selectedExternalTargetId = "";
    private boolean hudVisible;

    public String selectedWorldId() {
        return selectedWorldId;
    }

    public String selectedAnchorId() {
        return selectedAnchorId;
    }

    public String selectedWaypointId() {
        return selectedWaypointId;
    }

    public String selectedExternalTargetId() {
        return selectedExternalTargetId;
    }

    public boolean hudVisible() {
        return hudVisible;
    }

    public void selectWorld(String worldId) {
        selectedWorldId = safe(worldId);
        selectedAnchorId = "";
        selectedWaypointId = "";
        selectedExternalTargetId = "";
    }

    public void selectAnchor(String anchorId) {
        selectedAnchorId = safe(anchorId);
        selectedWaypointId = "";
        selectedExternalTargetId = "";
    }

    public void selectWaypoint(String waypointId) {
        selectedWaypointId = safe(waypointId);
        selectedAnchorId = "";
        selectedExternalTargetId = "";
    }

    public void selectExternalTarget(String targetId) {
        selectedExternalTargetId = safe(targetId);
        selectedAnchorId = "";
        selectedWaypointId = "";
    }

    public void clearSelection() {
        selectedAnchorId = "";
        selectedWaypointId = "";
        selectedExternalTargetId = "";
    }

    public void setHudVisible(boolean hudVisible) {
        this.hudVisible = hudVisible;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
