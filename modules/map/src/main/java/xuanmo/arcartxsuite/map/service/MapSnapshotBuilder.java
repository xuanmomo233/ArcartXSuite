package xuanmo.arcartxsuite.map.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import xuanmo.arcartxsuite.map.model.MapExternalTarget;
import xuanmo.arcartxsuite.map.model.MapNavigationState;
import xuanmo.arcartxsuite.map.model.MapPlayerViewState;
import xuanmo.arcartxsuite.map.model.MapWaypoint;

public final class MapSnapshotBuilder {

    public BuildResult build(BuildInput input) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(input.worlds(), "input.worlds");
        Objects.requireNonNull(input.anchors(), "input.anchors");
        Objects.requireNonNull(input.waypoints(), "input.waypoints");
        Objects.requireNonNull(input.externalTargets(), "input.externalTargets");
        Objects.requireNonNull(input.viewState(), "input.viewState");

        String selectedWorldId = chooseWorldId(input);
        WorldView selectedWorld = findWorld(input.worlds(), selectedWorldId);
        String selectedWorldName = selectedWorld == null ? "" : selectedWorld.displayName();

        List<WorldRow> worldRows = new ArrayList<>();
        for (WorldView world : input.worlds()) {
            worldRows.add(new WorldRow(world.id(), world.displayName(), world.texture(), world.id().equalsIgnoreCase(selectedWorldId)));
        }

        List<AnchorView> visibleAnchors = input.anchors().stream()
            .filter(anchor -> anchor.worldId().equalsIgnoreCase(selectedWorldId))
            .sorted(Comparator.comparingInt(AnchorView::sortOrder).thenComparing(AnchorView::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<AnchorRow> anchorRows = new ArrayList<>();
        for (AnchorView anchor : visibleAnchors) {
            anchorRows.add(
                new AnchorRow(
                    anchor.id(),
                    anchor.displayName(),
                    anchor.description(),
                    anchor.x(),
                    anchor.y(),
                    anchor.z(),
                    anchor.unlocked(),
                    anchor.unlockCostText(),
                    anchor.teleportCostText(),
                    anchor.id().equalsIgnoreCase(input.viewState().selectedAnchorId()),
                    input.navigationState().matchesAnchor(anchor.id())
                )
            );
        }

        List<MapWaypoint> visibleWaypoints = input.waypoints().stream()
            .filter(waypoint -> waypoint.world().equalsIgnoreCase(selectedWorldId))
            .sorted(Comparator.comparingLong(MapWaypoint::updatedAt).reversed().thenComparing(MapWaypoint::waypointId, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<WaypointRow> waypointRows = new ArrayList<>();
        for (MapWaypoint waypoint : visibleWaypoints) {
            waypointRows.add(
                new WaypointRow(
                    waypoint.waypointId(),
                    waypoint.name(),
                    waypoint.x(),
                    waypoint.y(),
                    waypoint.z(),
                    waypoint.waypointId().equalsIgnoreCase(input.viewState().selectedWaypointId()),
                    input.navigationState().matchesWaypoint(waypoint.waypointId())
                )
            );
        }

        List<MapExternalTarget> visibleExternalTargets = input.externalTargets().stream()
            .filter(target -> target.worldId().equalsIgnoreCase(selectedWorldId))
            .sorted(Comparator.comparingInt(MapExternalTarget::sortOrder).thenComparing(MapExternalTarget::title, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<ExternalTargetRow> externalRows = new ArrayList<>();
        for (MapExternalTarget target : visibleExternalTargets) {
            externalRows.add(
                new ExternalTargetRow(
                    target.targetId(),
                    target.source(),
                    target.title(),
                    target.description(),
                    target.x(),
                    target.y(),
                    target.z(),
                    target.targetId().equalsIgnoreCase(input.viewState().selectedExternalTargetId()),
                    input.navigationState().matchesExternal(target.targetId())
                )
            );
        }

        boolean canCreateWaypoint = input.waypointsEnabled() && visibleWaypoints.size() < input.waypointLimit();
        DetailSnapshot detail = buildDetail(input, visibleAnchors, visibleWaypoints, visibleExternalTargets, canCreateWaypoint, selectedWorldId);
        HudSnapshot hud = buildHudSnapshot(input, selectedWorld, selectedWorldId);

        return new BuildResult(
            new MenuSnapshot(
                input.packetId(),
                selectedWorldId,
                selectedWorldName,
                List.copyOf(worldRows),
                List.copyOf(anchorRows),
                List.copyOf(waypointRows),
                List.copyOf(externalRows),
                input.waypointLimit(),
                visibleWaypoints.size(),
                canCreateWaypoint,
                input.navigationState().active(),
                trackingText(input.navigationState()),
                detail
            ),
            hud
        );
    }

    private DetailSnapshot buildDetail(
        BuildInput input,
        List<AnchorView> visibleAnchors,
        List<MapWaypoint> visibleWaypoints,
        List<MapExternalTarget> visibleExternalTargets,
        boolean canCreateWaypoint,
        String selectedWorldId
    ) {
        for (AnchorView anchor : visibleAnchors) {
            if (anchor.id().equalsIgnoreCase(input.viewState().selectedAnchorId())) {
                return new DetailSnapshot(
                    "anchor",
                    anchor.id(),
                    anchor.displayName(),
                    anchor.description(),
                    anchor.unlocked(),
                    anchor.unlockCostText(),
                    anchor.teleportCostText(),
                    false,
                    anchor.canUnlock(),
                    anchor.canTeleport(),
                    input.navigationEnabled(),
                    false,
                    false,
                    false,
                    canCreateWaypoint,
                    input.navigationState().active(),
                    trackingText(input.navigationState())
                );
            }
        }
        for (MapWaypoint waypoint : visibleWaypoints) {
            if (waypoint.waypointId().equalsIgnoreCase(input.viewState().selectedWaypointId())) {
                return new DetailSnapshot(
                    "waypoint",
                    waypoint.waypointId(),
                    waypoint.name(),
                    "世界: " + selectedWorldId + "  坐标: " + trim(waypoint.x()) + ", " + trim(waypoint.y()) + ", " + trim(waypoint.z()),
                    true,
                    "",
                    "",
                    false,
                    false,
                    false,
                    false,
                    input.navigationEnabled(),
                    false,
                    true,
                    canCreateWaypoint,
                    input.navigationState().active(),
                    trackingText(input.navigationState())
                );
            }
        }
        for (MapExternalTarget target : visibleExternalTargets) {
            if (target.targetId().equalsIgnoreCase(input.viewState().selectedExternalTargetId())) {
                return new DetailSnapshot(
                    "external",
                    target.targetId(),
                    target.title(),
                    target.description(),
                    true,
                    "",
                    "",
                    true,
                    false,
                    false,
                    false,
                    false,
                    input.navigationEnabled(),
                    false,
                    canCreateWaypoint,
                    input.navigationState().active(),
                    trackingText(input.navigationState())
                );
            }
        }
        return new DetailSnapshot(
            "none",
            "",
            "未选中目标",
            "请选择一个锚点、自定义路径点或任务目标。",
            true,
            "",
            "",
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            canCreateWaypoint,
            input.navigationState().active(),
            trackingText(input.navigationState())
        );
    }

    private HudSnapshot buildHudSnapshot(BuildInput input, WorldView selectedWorld, String selectedWorldId) {
        if (!input.viewState().hudVisible() || selectedWorld == null || input.playerPosition() == null) {
            return new HudSnapshot(input.packetId(), false, selectedWorldId, "", 0, 0, 0D, 0, 0, 0, 0F, "");
        }
        double pixelX = clip(input.playerPosition().x() + selectedWorld.pixelOffsetX(), 0D, selectedWorld.imageWidth());
        double pixelZ = clip(input.playerPosition().z() + selectedWorld.pixelOffsetZ(), 0D, selectedWorld.imageHeight());
        return new HudSnapshot(
            input.packetId(),
            true,
            selectedWorldId,
            selectedWorld.texture(),
            selectedWorld.imageWidth(),
            selectedWorld.imageHeight(),
            selectedWorld.hudZoom(),
            selectedWorld.hudSize(),
            pixelX,
            pixelZ,
            input.playerPosition().yaw(),
            trackingText(input.navigationState())
        );
    }

    private static String chooseWorldId(BuildInput input) {
        String selectedWorldId = input.viewState().selectedWorldId();
        if (!selectedWorldId.isBlank() && findWorld(input.worlds(), selectedWorldId) != null) {
            return selectedWorldId;
        }
        if (input.playerWorldId() != null && findWorld(input.worlds(), input.playerWorldId()) != null) {
            return input.playerWorldId();
        }
        return input.worlds().isEmpty() ? "" : input.worlds().get(0).id();
    }

    private static WorldView findWorld(List<WorldView> worlds, String worldId) {
        for (WorldView world : worlds) {
            if (world.id().equalsIgnoreCase(worldId)) {
                return world;
            }
        }
        return null;
    }

    private static double clip(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(double value) {
        return BigDecimalString.of(value);
    }

    static String trackingText(MapNavigationState navigationState) {
        if (navigationState == null || !navigationState.active()) {
            return "当前未导航";
        }
        return switch (navigationState.targetType()) {
            case ANCHOR -> "当前导航锚点: " + navigationState.label();
            case WAYPOINT -> "当前导航路径点: " + navigationState.label();
            case EXTERNAL -> "当前导航任务点: " + navigationState.label();
            default -> "当前未导航";
        };
    }

    public record BuildInput(
        String packetId,
        List<WorldView> worlds,
        List<AnchorView> anchors,
        List<MapWaypoint> waypoints,
        List<MapExternalTarget> externalTargets,
        MapPlayerViewState viewState,
        MapNavigationState navigationState,
        PlayerPosition playerPosition,
        String playerWorldId,
        int waypointLimit,
        boolean navigationEnabled,
        boolean waypointsEnabled
    ) {
    }

    public record BuildResult(
        MenuSnapshot menu,
        HudSnapshot hud
    ) {
    }

    public record WorldView(
        String id,
        String displayName,
        String texture,
        int imageWidth,
        int imageHeight,
        int pixelOffsetX,
        int pixelOffsetZ,
        double defaultZoom,
        double hudZoom,
        int hudSize
    ) {
    }

    public record AnchorView(
        String id,
        String worldId,
        String displayName,
        String description,
        double x,
        double y,
        double z,
        boolean unlocked,
        String unlockCostText,
        String teleportCostText,
        boolean canUnlock,
        boolean canTeleport,
        int sortOrder
    ) {
    }

    public record PlayerPosition(
        double x,
        double y,
        double z,
        float yaw
    ) {
    }

    public record MenuSnapshot(
        String packetId,
        String selectedWorldId,
        String selectedWorldName,
        List<WorldRow> worldRows,
        List<AnchorRow> anchorRows,
        List<WaypointRow> waypointRows,
        List<ExternalTargetRow> externalTargetRows,
        int waypointLimit,
        int waypointCount,
        boolean canCreateWaypoint,
        boolean clearTrackVisible,
        String trackingText,
        DetailSnapshot detail
    ) {
    }

    public record HudSnapshot(
        String packetId,
        boolean visible,
        String worldId,
        String texture,
        int imageWidth,
        int imageHeight,
        double hudZoom,
        int hudSize,
        double clippedPlayerX,
        double clippedPlayerZ,
        float playerYaw,
        String trackingText
    ) {
    }

    public record WorldRow(
        String id,
        String displayName,
        String texture,
        boolean selected
    ) {
    }

    public record AnchorRow(
        String id,
        String displayName,
        String description,
        double x,
        double y,
        double z,
        boolean unlocked,
        String unlockCostText,
        String teleportCostText,
        boolean selected,
        boolean tracked
    ) {
    }

    public record WaypointRow(
        String id,
        String name,
        double x,
        double y,
        double z,
        boolean selected,
        boolean tracked
    ) {
    }

    public record ExternalTargetRow(
        String id,
        String source,
        String name,
        String description,
        double x,
        double y,
        double z,
        boolean selected,
        boolean tracked
    ) {
    }

    public record DetailSnapshot(
        String selectedType,
        String selectedId,
        String title,
        String description,
        boolean unlocked,
        String unlockCostText,
        String teleportCostText,
        boolean externalTarget,
        boolean canUnlock,
        boolean canTeleport,
        boolean canTrackAnchor,
        boolean canTrackWaypoint,
        boolean canTrackExternal,
        boolean canDeleteWaypoint,
        boolean canCreateWaypoint,
        boolean clearTrackVisible,
        String trackingText
    ) {
    }

    private static final class BigDecimalString {

        private BigDecimalString() {
        }

        private static String of(double value) {
            java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(value);
            return decimal.stripTrailingZeros().toPlainString();
        }
    }
}
