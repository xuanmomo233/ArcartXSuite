package xuanmo.arcartxsuite.map.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import xuanmo.arcartxsuite.map.model.MapExternalTarget;
import xuanmo.arcartxsuite.map.model.MapNavigationState;
import xuanmo.arcartxsuite.map.model.MapPin;
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
            worldRows.add(new WorldRow(world.id(), world.displayName(), world.dimension(), world.id().equalsIgnoreCase(selectedWorldId)));
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
                    waypoint.description(),
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
                    target.iconId() == null ? "" : target.iconId(),
                    target.targetId().equalsIgnoreCase(input.viewState().selectedExternalTargetId()),
                    target.navigated()
                )
            );
        }

        List<MapPin> visiblePins = input.pins().stream()
            .filter(pin -> pin.world().equalsIgnoreCase(selectedWorldId))
            .sorted(Comparator.comparingLong(MapPin::updatedAt).reversed().thenComparing(MapPin::pinId, String.CASE_INSENSITIVE_ORDER))
            .toList();
        List<PinRow> pinRows = new ArrayList<>();
        for (MapPin pin : visiblePins) {
            pinRows.add(
                new PinRow(
                    pin.pinId(),
                    pin.name(),
                    pin.description(),
                    pin.x(),
                    pin.z(),
                    pin.pinId().equalsIgnoreCase(input.viewState().selectedPinId())
                )
            );
        }

        // 点亮区域并集：已解锁锚点的 lightRegions（多边形）+ lightRadius（圆形转多边形）。
        // litEnabled=false 表示该世界没有任何点亮配置 → 客户端全亮不遮罩。
        boolean litEnabled = visibleAnchors.stream().anyMatch(a ->
            a.unlocked() && (!a.lightRegions().isEmpty() || a.lightRadius() > 0));
        List<List<List<Integer>>> litRegions = new ArrayList<>();
        if (litEnabled) {
            for (AnchorView a : visibleAnchors) {
                if (!a.unlocked()) continue;
                for (List<int[]> r : a.lightRegions()) {
                    litRegions.add(toIntLists(r));
                }
                if (a.lightRadius() > 0) {
                    litRegions.add(toIntLists(circleToPolygon(a.x(), a.z(), a.lightRadius())));
                }
            }
        }

        boolean canCreateWaypoint = input.waypointsEnabled() && visibleWaypoints.size() < input.waypointLimit();
        DetailSnapshot detail = buildDetail(input, visibleAnchors, visibleWaypoints, visibleExternalTargets, visiblePins, canCreateWaypoint, selectedWorldId);
        HudSnapshot hud = buildHudSnapshot(input, selectedWorld, selectedWorldId, litEnabled, litRegions);

        return new BuildResult(
            new MenuSnapshot(
                input.packetId(),
                selectedWorldId,
                selectedWorldName,
                List.copyOf(worldRows),
                List.copyOf(anchorRows),
                List.copyOf(waypointRows),
                List.copyOf(externalRows),
                List.copyOf(pinRows),
                input.waypointLimit(),
                visibleWaypoints.size(),
                canCreateWaypoint,
                input.navigationState().active(),
                trackingText(input.navigationState()),
                detail,
                litEnabled,
                litRegions,
                input.playerWorldId()
            ),
            hud
        );
    }

    private static List<List<Integer>> toIntLists(List<int[]> points) {
        List<List<Integer>> out = new ArrayList<>(points.size());
        for (int[] p : points) {
            out.add(List.of(p[0], p[1]));
        }
        return out;
    }

    /** 以 (cx,cz) 为中心、radius 为半径的圆，近似为 16 边形顶点列表。 */
    private static List<int[]> circleToPolygon(double cx, double cz, double radius) {
        List<int[]> points = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            points.add(new int[]{
                (int) Math.round(cx + radius * Math.cos(angle)),
                (int) Math.round(cz + radius * Math.sin(angle))
            });
        }
        return points;
    }

    private DetailSnapshot buildDetail(
        BuildInput input,
        List<AnchorView> visibleAnchors,
        List<MapWaypoint> visibleWaypoints,
        List<MapExternalTarget> visibleExternalTargets,
        List<MapPin> visiblePins,
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
                    false,
                    canCreateWaypoint,
                    input.navigationState().active(),
                    trackingText(input.navigationState()),
                    input.navigationState().matchesAnchor(anchor.id())
                );
            }
        }
        for (MapWaypoint waypoint : visibleWaypoints) {
            if (waypoint.waypointId().equalsIgnoreCase(input.viewState().selectedWaypointId())) {
                return new DetailSnapshot(
                    "waypoint",
                    waypoint.waypointId(),
                    waypoint.name(),
                    waypoint.description().isBlank() ? "世界: " + selectedWorldId + "  坐标: " + trim(waypoint.x()) + ", " + trim(waypoint.y()) + ", " + trim(waypoint.z()) : waypoint.description(),
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
                    false,
                    canCreateWaypoint,
                    input.navigationState().active(),
                    trackingText(input.navigationState()),
                    input.navigationState().matchesWaypoint(waypoint.waypointId())
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
                    false,
                    canCreateWaypoint,
                    input.navigationState().active(),
                    trackingText(input.navigationState()),
                    target.navigated()
                );
            }
        }
        for (MapPin pin : visiblePins) {
            if (pin.pinId().equalsIgnoreCase(input.viewState().selectedPinId())) {
                return new DetailSnapshot(
                    "pin",
                    pin.pinId(),
                    pin.name(),
                    pin.description().isBlank() ? "世界: " + selectedWorldId + "  坐标: " + trim(pin.x()) + ", " + trim(pin.z()) : pin.description(),
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
                    true,
                    canCreateWaypoint,
                    input.navigationState().active(),
                    trackingText(input.navigationState()),
                    false
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
            false,
            canCreateWaypoint,
            input.navigationState().active(),
            trackingText(input.navigationState()),
            false
        );
    }

    private HudSnapshot buildHudSnapshot(BuildInput input, WorldView selectedWorld, String selectedWorldId,
                                         boolean litEnabled, List<List<List<Integer>>> litRegions) {
        if (!input.viewState().hudVisible() || selectedWorld == null || input.playerPosition() == null) {
            return new HudSnapshot(input.packetId(), false, selectedWorldId, "", 0F, "", false, List.of());
        }
        return new HudSnapshot(
            input.packetId(),
            true,
            selectedWorldId,
            selectedWorld.dimension(),
            input.playerPosition().yaw(),
            trackingText(input.navigationState()),
            litEnabled,
            litRegions
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
        List<MapPin> pins,
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
     * 地图完全由客户端 mod 渲染，无需纹理/图片参数。
     *
     * @param id          世界 ID
     * @param displayName 显示名称
     * @param dimension   MC 维度 key（minecraft:overworld 等），供客户端 mod 匹配本地维度
     */
    public record WorldView(
        String id,
        String displayName,
        String dimension
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
     * @param lightRegions     解锁后点亮的多边形区域列表
     * @param lightRadius      解锁后点亮的圆形半径（≤0 = 不用圆形）
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
        int sortOrder,
        List<List<int[]>> lightRegions,
        double lightRadius
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
     * @param litEnabled        该世界是否启用点亮机制（配置了任何区域）
     * @param litRegions        当前已点亮的多边形区域并集（always-lit ∪ 已解锁锚点区域）
     */
    public record MenuSnapshot(
        String packetId,
        String selectedWorldId,
        String selectedWorldName,
        List<WorldRow> worldRows,
        List<AnchorRow> anchorRows,
        List<WaypointRow> waypointRows,
        List<ExternalTargetRow> externalTargetRows,
        List<PinRow> pinRows,
        int waypointLimit,
        int waypointCount,
        boolean canCreateWaypoint,
        boolean clearTrackVisible,
        String trackingText,
        DetailSnapshot detail,
        boolean litEnabled,
        List<List<List<Integer>>> litRegions,
        String playerWorldId
    ) {
    }

    /**
     * HUD 快照，发送给客户端渲染小地图 HUD 的数据。
     * 地图渲染完全由 mod 控制，服务端只下发世界/维度/点亮数据。
     *
     * @param packetId     数据包标识
     * @param visible      HUD 是否可见
     * @param worldId      当前世界 ID
     * @param dimension    MC 维度 key，供 mod 匹配本地维度
     * @param playerYaw    玩家朝向角
     * @param trackingText 当前追踪状态文本
     * @param litEnabled   是否启用点亮机制
     * @param litRegions   已点亮多边形区域并集
     */
    public record HudSnapshot(
        String packetId,
        boolean visible,
        String worldId,
        String dimension,
        float playerYaw,
        String trackingText,
        boolean litEnabled,
        List<List<List<Integer>>> litRegions
    ) {
    }

    /**
     * 世界列表行数据，对应菜单中的一个世界条目。
     *
     * @param id          世界 ID
     * @param displayName 显示名称
     * @param dimension   MC 维度 key，供 mod 匹配本地维度
     * @param selected    是否当前选中
     */
    public record WorldRow(
        String id,
        String displayName,
        String dimension,
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
        String description,
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
     * @param iconId      图标 id（可空字符串）
     * @param selected    是否当前选中
     * @param tracked     来源是否正在导航该目标（驱动"导航/取消"按钮态）
     */
    public record ExternalTargetRow(
        String id,
        String source,
        String name,
        String description,
        double x,
        double y,
        double z,
        String iconId,
        boolean selected,
        boolean tracked
    ) {
    }

    /**
     * 标记点列表行数据，对应菜单中的一个地图标记点条目。
     *
     * @param id          标记点 ID
     * @param name        显示名称
     * @param description 描述文本
     * @param x           X 坐标
     * @param z           Z 坐标
     * @param selected    是否当前选中
     */
    public record PinRow(
        String id,
        String name,
        String description,
        double x,
        double z,
        boolean selected
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
     * @param navigated          当前选中目标是否正被导航（anchor/waypoint 看地图自身导航态；
     *                           external 看来源回推的 navigated 标记；驱动"导航/取消"按钮切换）
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
        boolean canDeletePin,
        boolean canCreateWaypoint,
        boolean clearTrackVisible,
        String trackingText,
        boolean navigated
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
