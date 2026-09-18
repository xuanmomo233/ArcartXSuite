package xuanmo.arcartxsuite.map.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
            worldRows.add(new WorldRow(world.id(), world.displayName(), world.dimension(),
                world.id().equalsIgnoreCase(selectedWorldId),
                world.preloadVersion(), serializeChunks(world.preloadChunks()),
                world.centerX(), world.centerZ()));
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
                    input.navigationStates().values().stream().anyMatch(state -> state.matchesAnchor(anchor.id()))
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
                    input.navigationStates().values().stream().anyMatch(state -> state.matchesWaypoint(waypoint.waypointId()))
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

        // 点亮区域并集：已解锁锚点的 lightRegions（多边形）+ lightRadius（圆形转多边形）
        // ∪ 世界预加载区域（"推即亮"：推送地形天然应可见）。
        // litEnabled 仍只由锚点点亮配置驱动：无锚点灯的世界全亮不遮罩，
        // 预加载数据本身已可见；仅当世界存在锚点灯时才把预加载区并入遮罩并集。
        // litEnabled 只看世界是否配置点亮（不看玩家是否已解锁）：
        // 配置了灯但一个都没解锁 = 全黑迷雾，这才是"点亮"玩法的正确初态
        boolean litEnabled = visibleAnchors.stream().anyMatch(a ->
            !a.lightRegions().isEmpty() || a.lightRadius() > 0);
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
            // 推即亮：世界预加载区并入点亮并集（防止已推地形被迷雾遮住）
            if (selectedWorld != null) {
                for (List<int[]> r : selectedWorld.preloadRegions()) {
                    litRegions.add(toIntLists(r));
                }
            }
        }

        boolean canCreateWaypoint = input.waypointsEnabled() && visibleWaypoints.size() < input.waypointLimit();
        DetailSnapshot detail = buildDetail(input, visibleAnchors, visibleWaypoints, visibleExternalTargets, visiblePins, canCreateWaypoint, selectedWorldId);

        // HUD 数据完全按"玩家当前世界"构建，与菜单选中世界解耦：
        // 菜单里浏览其他世界时 HUD 仍应显示玩家所在世界的地形/标记/迷雾
        WorldView hudWorld = findWorld(input.worlds(), input.playerWorldId());
        String hudWorldId = hudWorld == null ? "" : hudWorld.id();
        List<AnchorView> hudAnchors = input.anchors().stream()
            .filter(anchor -> anchor.worldId().equalsIgnoreCase(hudWorldId))
            .sorted(Comparator.comparingInt(AnchorView::sortOrder).thenComparing(AnchorView::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        boolean hudLitEnabled = hudAnchors.stream().anyMatch(a ->
            !a.lightRegions().isEmpty() || a.lightRadius() > 0);
        List<List<List<Integer>>> hudLitRegions = new ArrayList<>();
        if (hudLitEnabled) {
            for (AnchorView a : hudAnchors) {
                if (!a.unlocked()) continue;
                for (List<int[]> r : a.lightRegions()) {
                    hudLitRegions.add(toIntLists(r));
                }
                if (a.lightRadius() > 0) {
                    hudLitRegions.add(toIntLists(circleToPolygon(a.x(), a.z(), a.lightRadius())));
                }
            }
            if (hudWorld != null) {
                for (List<int[]> r : hudWorld.preloadRegions()) {
                    hudLitRegions.add(toIntLists(r));
                }
            }
        }
        HudSnapshot hud = buildHudSnapshot(input, hudWorld, hudWorldId, hudAnchors, hudLitEnabled, hudLitRegions);

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
                !input.navigationStates().isEmpty(),
                trackingText(input.navigationStates()),
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

    /** MapChunk 坐标列表 → "cx,cz;cx,cz" 字符串（yml→mod 桥接只走字符串最稳）。 */
    private static String serializeChunks(List<int[]> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(chunks.size() * 12);
        for (int[] c : chunks) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(c[0]).append(',').append(c[1]);
        }
        return sb.toString();
    }

    /** 玩家到锚点的三维距离；跨世界/无坐标返回 {@code Double.NaN}。 */
    private static double distanceToAnchor(BuildInput input, AnchorView anchor) {
        PlayerPosition pos = input.playerPosition();
        if (pos == null || input.playerWorldId().isBlank()
            || !input.playerWorldId().equalsIgnoreCase(anchor.worldId())) {
            return Double.NaN;
        }
        double dx = pos.x() - anchor.x();
        double dy = pos.y() - anchor.y();
        double dz = pos.z() - anchor.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
                double dist = distanceToAnchor(input, anchor);
                double radius = anchor.unlockRadius();
                // 半径门槛：radius>0 且未解锁时详情卡显示"距离 112m / 需 ≤8m"
                // 并门控解锁按钮；已解锁/无门槛/其他目标类型不显示该行
                boolean inRange = radius <= 0 || (!Double.isNaN(dist) && dist <= radius);
                String rangeText = (!anchor.unlocked() && radius > 0)
                    ? (Double.isNaN(dist) ? "距离 --" : "距离 " + Math.round(dist) + "m")
                        + " / 需 ≤" + Math.round(radius) + "m"
                    : "";
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
                    !input.navigationStates().isEmpty(),
                    trackingText(input.navigationStates()),
                    input.navigationStates().values().stream().anyMatch(state -> state.matchesAnchor(anchor.id())),
                    radius, dist, inRange, rangeText,
                    anchor.worldId().equalsIgnoreCase(input.playerWorldId())
                        ? "" : anchor.teleportCostText()
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
                    !input.navigationStates().isEmpty(),
                    trackingText(input.navigationStates()),
                    input.navigationStates().values().stream().anyMatch(state -> state.matchesWaypoint(waypoint.waypointId())),
                    0, -1, true, "",
                    selectedWorldId.equalsIgnoreCase(input.playerWorldId())
                        ? "" : nearestAnchorTeleportCost(input, selectedWorldId, waypoint.x(), waypoint.z())
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
                    !input.navigationStates().isEmpty(),
                    trackingText(input.navigationStates()),
                    target.navigated(),
                    0, -1, true, "",
                    selectedWorldId.equalsIgnoreCase(input.playerWorldId())
                        ? "" : nearestAnchorTeleportCost(input, selectedWorldId, target.x(), target.z())
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
                    !input.navigationStates().isEmpty(),
                    trackingText(input.navigationStates()),
                    false,
                    0, -1, true, "",
                    ""
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
            !input.navigationStates().isEmpty(),
            trackingText(input.navigationStates()),
            false,
            0, -1, true, "",
            ""
        );
    }

    /**
     * 跨世界导航的锚点传送费用镜像：与 {@code MapService#teleportToNearestUnlockedAnchor}
     * 相同的"目标世界最近已解锁锚点"选法，返回该锚点的传送费用文本（无可用锚点返回空串）。
     */
    private static String nearestAnchorTeleportCost(BuildInput input, String worldId, double x, double z) {
        AnchorView nearest = null;
        double best = Double.MAX_VALUE;
        for (AnchorView anchor : input.anchors()) {
            if (!anchor.worldId().equalsIgnoreCase(worldId) || !anchor.unlocked()) {
                continue;
            }
            double distance = (anchor.x() - x) * (anchor.x() - x)
                + (anchor.z() - z) * (anchor.z() - z);
            if (distance < best) {
                best = distance;
                nearest = anchor;
            }
        }
        return nearest == null ? "" : nearest.teleportCostText();
    }

    private HudSnapshot buildHudSnapshot(BuildInput input, WorldView hudWorld, String hudWorldId,
                                         List<AnchorView> hudAnchors,
                                         boolean litEnabled, List<List<List<Integer>>> litRegions) {
        if (!input.viewState().hudVisible() || hudWorld == null || input.playerPosition() == null) {
            return new HudSnapshot(input.packetId(), false, hudWorldId, "", "", 0F, "",
                false, List.of(), 0, "", List.of());
        }
        return new HudSnapshot(
            input.packetId(),
            true,
            hudWorldId,
            hudWorld.displayName(),
            hudWorld.dimension(),
            input.playerPosition().yaw(),
            trackingText(input.navigationStates()),
            litEnabled,
            litRegions,
            hudWorld.preloadVersion(),
            serializeChunks(hudWorld.preloadChunks()),
            hudMarkerRows(input, hudWorldId, hudAnchors)
        );
    }

    /** HUD 标记行：玩家当前世界的锚点/路径点/外部目标/pin 合并列表（与菜单选中世界无关）。 */
    private List<HudMarkerRow> hudMarkerRows(BuildInput input, String hudWorldId, List<AnchorView> hudAnchors) {
        List<HudMarkerRow> rows = new ArrayList<>();
        for (AnchorView anchor : hudAnchors) {
            rows.add(new HudMarkerRow(
                anchor.id(),
                "anchor",
                anchor.displayName(),
                anchor.x(),
                anchor.z(),
                anchor.id().equalsIgnoreCase(input.viewState().selectedAnchorId()),
                input.navigationStates().values().stream().anyMatch(state -> state.matchesAnchor(anchor.id())),
                anchor.unlocked()
            ));
        }
        for (MapWaypoint waypoint : input.waypoints()) {
            if (!waypoint.world().equalsIgnoreCase(hudWorldId)) {
                continue;
            }
            rows.add(new HudMarkerRow(
                waypoint.waypointId(),
                "waypoint",
                waypoint.name(),
                waypoint.x(),
                waypoint.z(),
                waypoint.waypointId().equalsIgnoreCase(input.viewState().selectedWaypointId()),
                input.navigationStates().values().stream().anyMatch(state -> state.matchesWaypoint(waypoint.waypointId())),
                true
            ));
        }
        for (MapExternalTarget target : input.externalTargets()) {
            if (!target.worldId().equalsIgnoreCase(hudWorldId)) {
                continue;
            }
            rows.add(new HudMarkerRow(
                target.targetId(),
                "external",
                target.title(),
                target.x(),
                target.z(),
                target.targetId().equalsIgnoreCase(input.viewState().selectedExternalTargetId()),
                target.navigated(),
                true
            ));
        }
        for (MapPin pin : input.pins()) {
            if (!pin.world().equalsIgnoreCase(hudWorldId)) continue;
            rows.add(new HudMarkerRow(
                pin.pinId(),
                "pin",
                pin.name(),
                pin.x(),
                pin.z(),
                pin.pinId().equalsIgnoreCase(input.viewState().selectedPinId()),
                false,
                true
            ));
        }
        return List.copyOf(rows);
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

    static String trackingText(Map<String, MapNavigationState> navigationStates) {
        if (navigationStates == null || navigationStates.isEmpty()) {
            return "当前未导航";
        }
        if (navigationStates.size() > 1) {
            return "正在导航 " + navigationStates.size() + " 个目标";
        }
        MapNavigationState navigationState = navigationStates.values().iterator().next();
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
     * @param navigationStates 玩家导航状态表（导航键 type:id → 状态，支持多目标并行）
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
        Map<String, MapNavigationState> navigationStates,
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
    /**
     * 世界视图数据，供快照构建器使用的世界信息投影。
     * 地图完全由客户端 mod 渲染，无需纹理/图片参数。
     *
     * @param id             世界 ID
     * @param displayName    显示名称
     * @param dimension      MC 维度 key（minecraft:overworld 等），供客户端 mod 匹配本地维度
     * @param preloadVersion 预加载版本号（0 = 无预加载配置）
     * @param preloadRegions 预加载多边形并集（radius 已转多边形），点亮并集合并用
     * @param preloadChunks  预加载覆盖的 MapChunk 坐标（64² 网格），逐项 {cx, cz}
     * @param centerX        世界中心 X（preload.center ?? 出生点链；浏览非当前世界时的视野中心）
     * @param centerZ        世界中心 Z
     */
    public record WorldView(
        String id,
        String displayName,
        String dimension,
        int preloadVersion,
        List<List<int[]>> preloadRegions,
        List<int[]> preloadChunks,
        double centerX,
        double centerZ
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
     * @param unlockRadius     手动解锁三维距离门槛（≤0 = 不限距离）
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
        double lightRadius,
        double unlockRadius
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
     * <p>
     * worldId/worldName/dimension/litRegions/markerRows 全部按"玩家当前世界"
     * 构建（与菜单选中世界无关）——菜单里浏览其他世界不影响 HUD。
     *
     * @param packetId       数据包标识
     * @param visible        HUD 是否可见
     * @param worldId        玩家当前世界 ID（未配置世界为空串）
     * @param worldName      玩家当前世界显示名
     * @param dimension      MC 维度 key，供 mod 匹配本地维度
     * @param playerYaw      玩家朝向角
     * @param trackingText   当前追踪状态文本
     * @param litEnabled     当前世界是否启用点亮机制
     * @param litRegions     当前世界已点亮多边形区域并集
     * @param preloadVersion 当前世界预加载版本（供 mod 判重）
     * @param preloadChunks  当前世界预加载块坐标（"cx,cz;..."），供 mod 请求地形
     * @param markerRows     当前世界标记行（锚点/路径点/外部目标/pin）
     */
    public record HudSnapshot(
        String packetId,
        boolean visible,
        String worldId,
        String worldName,
        String dimension,
        float playerYaw,
        String trackingText,
        boolean litEnabled,
        List<List<List<Integer>>> litRegions,
        int preloadVersion,
        String preloadChunks,
        List<HudMarkerRow> markerRows
    ) {
    }

    /**
     * HUD 标记行数据，对应小地图上的一个标记点（玩家当前世界）。
     *
     * @param id       标记 ID
     * @param type     类型（anchor / waypoint / external / pin）
     * @param label    显示名
     * @param x        X 坐标
     * @param z        Z 坐标
     * @param selected 是否菜单当前选中
     * @param tracked  是否正在追踪/导航
     * @param unlocked 是否已解锁（锚点；其余类型恒 true）
     */
    public record HudMarkerRow(
        String id,
        String type,
        String label,
        double x,
        double z,
        boolean selected,
        boolean tracked,
        boolean unlocked
    ) {
    }

    /**
     * 世界列表行数据，对应菜单中的一个世界条目。
     *
     * @param id          世界 ID
     * @param displayName 显示名称
     * @param dimension   MC 维度 key，供 mod 匹配本地维度
     * @param selected    是否当前选中
     * @param centerX     世界中心 X（出生点链；客户端浏览非当前世界的默认视野中心）
     * @param centerZ     世界中心 Z
     */
    public record WorldRow(
        String id,
        String displayName,
        String dimension,
        boolean selected,
        int preloadVersion,
        String preloadChunks,
        double centerX,
        double centerZ
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
     * @param unlockRadius       手动解锁三维距离门槛（≤0 = 不限距离）
     * @param distanceToAnchor   玩家到锚点的三维距离（NaN = 跨世界/无坐标，不可用）
     * @param unlockInRange      当前距离是否满足解锁门槛（radius≤0 恒 true）
     * @param unlockRangeText    距离提示文本（"距离 112m / 需 ≤8m"；无门槛/已解锁/非锚点为空串）
     * @param crossWorldCostText 跨世界导航费用提示（目标在玩家当前世界时为空串；
     *                           锚点=自身传送费，路径点/外部目标=目标世界最近已解锁锚点的传送费）
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
        boolean navigated,
        double unlockRadius,
        double distanceToAnchor,
        boolean unlockInRange,
        String unlockRangeText,
        String crossWorldCostText
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
