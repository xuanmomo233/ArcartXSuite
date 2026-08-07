package xuanmo.arcartxsuite.map.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import xuanmo.arcartxsuite.map.model.MapExternalTarget;
import xuanmo.arcartxsuite.map.model.MapNavigationState;
import xuanmo.arcartxsuite.map.model.MapPlayerViewState;
import xuanmo.arcartxsuite.map.model.MapWaypoint;

/**
 * 地图 UI 快照构建器，将玩家视图状态、世界 / 锚点 / 路径点 / 外部目标数据
 * 组装为菜单快照（{@code MenuSnapshot}）和 HUD 快照（{@code HudSnapshot}）。
 * <p>
 * 关联模块：map。由 {@code MapService} 在每次同步时调用，
 * 生成的快照通过数据包发送给 ArcartX 客户端渲染。
 */
public final class MapSnapshotBuilder {

    /**
     * 根据输入数据构建菜单与 HUD 快照。
     *
     * @param input 构建输入数据，包含世界、锚点、路径点、外部目标、视图状态等
     * @return 包含菜单快照与 HUD 快照的构建结果
     */
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

    /**
     * 快照构建输入数据。
     *
     * @param packetId        数据包标识
     * @param worlds          世界视图列表
     * @param anchors         锚点视图列表
     * @param waypoints       玩家路径点列表
     * @param externalTargets 外部目标列表
     * @param viewState       玩家视图状态
     * @param navigationState 玩家导航状态
     * @param playerPosition  玩家位置信息
     * @param playerWorldId   玩家当前所在世界 ID
     * @param waypointLimit   路径点数量上限
     * @param navigationEnabled 导航功能是否启用
     * @param waypointsEnabled 自定义路径点功能是否启用
     */
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

    /**
     * 快照构建结果，包含菜单快照与 HUD 快照。
     *
     * @param menu 菜单快照
     * @param hud  HUD 快照
     */
    public record BuildResult(
        MenuSnapshot menu,
        HudSnapshot hud
    ) {
    }

    /**
     * 世界视图数据，供快照构建器使用的世界信息投影。
     *
     * @param id            世界 ID
     * @param displayName   显示名称
     * @param texture       纹理文件路径
     * @param imageWidth    纹理图片宽度
     * @param imageHeight   纹理图片高度
     * @param pixelOffsetX  像素 X 偏移
     * @param pixelOffsetZ  像素 Z 偏移
     * @param defaultZoom   菜单默认缩放
     * @param hudZoom       HUD 缩放
     * @param hudSize       HUD 尺寸
     */
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

    /**
     * 锚点视图数据，供快照构建器使用的锚点信息投影。
     *
     * @param id               锚点 ID
     * @param worldId          所在世界 ID
     * @param displayName      显示名称
     * @param description      描述文本
     * @param x                X 坐标
     * @param y                Y 坐标
     * @param z                Z 坐标
     * @param unlocked         是否已解锁
     * @param unlockCostText   解锁费用文本
     * @param teleportCostText 传送费用文本
     * @param canUnlock        是否可解锁
     * @param canTeleport      是否可传送
     * @param sortOrder        排序权重
     */
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

    /**
     * 玩家位置信息。
     *
     * @param x   X 坐标
     * @param y   Y 坐标
     * @param z   Z 坐标
     * @param yaw 朝向角
     */
    public record PlayerPosition(
        double x,
        double y,
        double z,
        float yaw
    ) {
    }

    /**
     * 菜单快照，发送给客户端渲染地图菜单 UI 的完整数据。
     *
     * @param packetId          数据包标识
     * @param selectedWorldId   当前选中的世界 ID
     * @param selectedWorldName 当前选中的世界名称
     * @param worldRows         世界列表行数据
     * @param anchorRows        锚点列表行数据
     * @param waypointRows      路径点列表行数据
     * @param externalTargetRows 外部目标列表行数据
     * @param waypointLimit     路径点数量上限
     * @param waypointCount     当前路径点数量
     * @param canCreateWaypoint 是否可创建新路径点
     * @param clearTrackVisible 是否显示清除追踪按钮
     * @param trackingText      当前追踪状态文本
     * @param detail            选中目标的详情快照
     */
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

    /**
     * HUD 快照，发送给客户端渲染小地图 HUD 的数据。
     *
     * @param packetId       数据包标识
     * @param visible        HUD 是否可见
     * @param worldId        当前世界 ID
     * @param texture        纹理文件路径
     * @param imageWidth     纹理图片宽度
     * @param imageHeight    纹理图片高度
     * @param hudZoom        HUD 缩放
     * @param hudSize        HUD 尺寸
     * @param clippedPlayerX 裁剪后的玩家像素 X 坐标
     * @param clippedPlayerZ 裁剪后的玩家像素 Z 坐标
     * @param playerYaw      玩家朝向角
     * @param trackingText   当前追踪状态文本
     */
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

    /**
     * 世界列表行数据，对应菜单中的一个世界条目。
     *
     * @param id          世界 ID
     * @param displayName 显示名称
     * @param texture     纹理文件路径
     * @param selected    是否当前选中
     */
    public record WorldRow(
        String id,
        String displayName,
        String texture,
        boolean selected
    ) {
    }

    /**
     * 锚点列表行数据，对应菜单中的一个锚点条目。
     *
     * @param id               锚点 ID
     * @param displayName      显示名称
     * @param description      描述文本
     * @param x                X 坐标
     * @param y                Y 坐标
     * @param z                Z 坐标
     * @param unlocked         是否已解锁
     * @param unlockCostText   解锁费用文本
     * @param teleportCostText 传送费用文本
     * @param selected         是否当前选中
     * @param tracked          是否正在追踪
     */
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

    /**
     * 路径点列表行数据，对应菜单中的一个自定义路径点条目。
     *
     * @param id       路径点 ID
     * @param name     路径点名称
     * @param x        X 坐标
     * @param y        Y 坐标
     * @param z        Z 坐标
     * @param selected 是否当前选中
     * @param tracked  是否正在追踪
     */
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

    /**
     * 外部目标列表行数据，对应菜单中的一个外部导航目标条目。
     *
     * @param id          外部目标 ID
     * @param source      来源模块标识
     * @param name        显示名称
     * @param description 描述文本
     * @param x           X 坐标
     * @param y           Y 坐标
     * @param z           Z 坐标
     * @param selected    是否当前选中
     * @param tracked     是否正在追踪
     */
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

    /**
     * 选中目标详情快照，描述菜单右侧详情面板的显示内容。
     *
     * @param selectedType       选中目标类型（anchor / waypoint / external / none）
     * @param selectedId         选中目标 ID
     * @param title              详情标题
     * @param description        详情描述
     * @param unlocked           是否已解锁
     * @param unlockCostText     解锁费用文本
     * @param teleportCostText   传送费用文本
     * @param externalTarget     是否为外部目标
     * @param canUnlock          是否可执行解锁
     * @param canTeleport        是否可执行传送
     * @param canTrackAnchor     是否可追踪锚点
     * @param canTrackWaypoint   是否可追踪路径点
     * @param canTrackExternal   是否可追踪外部目标
     * @param canDeleteWaypoint  是否可删除路径点
     * @param canCreateWaypoint  是否可创建路径点
     * @param clearTrackVisible  是否显示清除追踪按钮
     * @param trackingText       当前追踪状态文本
     */
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
