package xuanmo.arcartxsuite.map.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.WaypointBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI.CurrencyBridge;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.AnchorDefinition;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.CurrencyCost;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.DefaultUnlockRule;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.ItemCost;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.WorldDefinition;
import xuanmo.arcartxsuite.map.model.MapNavigationState;
import xuanmo.arcartxsuite.map.model.MapNavigationState.TargetType;
import xuanmo.arcartxsuite.map.model.MapExternalTarget;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapPlayerViewState;
import xuanmo.arcartxsuite.map.model.MapWaypoint;
import xuanmo.arcartxsuite.map.storage.MapRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

public final class MapService implements Listener, MapUiPacketHandler.ActionTarget {

    public static final String MENU_UI_RESOURCE_PATH = "arcartx/ui/map_menu.yml";
    public static final String MENU_UI_FILE_PATH = "ui/map_menu.yml";
    public static final String HUD_UI_RESOURCE_PATH = "arcartx/ui/map_hud.yml";
    public static final String HUD_UI_FILE_PATH = "ui/map_hud.yml";

    private static final String PREFIX = ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;

    private final JavaPlugin plugin;
    private final PacketGuardAPI packetGuard;
    private final MapModuleConfiguration configuration;
    private final MapRepository repository;
    private final PacketBridgeAPI bridge;
    private final String menuUiId;
    private final String hudUiId;
    private final MapSnapshotBuilder snapshotBuilder = new MapSnapshotBuilder();
    private final MapUiPacketHandler uiPacketHandler;
    private final CurrencyBridgeAPI currencyBridgeManager;
    private final ItemSourceRegistry itemSourceRegistry;
    private final ItemMatcherAPI itemMatcherSupport;
    private final WaypointBridgeAPI waypointBridge;
    private final ConcurrentMap<UUID, MapPlayerViewState> viewStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, MapNavigationState> navigationStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, MapExternalTarget>> externalTargets = new ConcurrentHashMap<>();
    private final Set<UUID> openedHudPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> defaultUnlocksEnsured = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, CachedPlayerMapData> playerDataCache = new ConcurrentHashMap<>();

    private record CachedPlayerMapData(Set<String> unlockedAnchors, List<MapWaypoint> waypoints) {
    }

    public MapService(
        JavaPlugin plugin,
        PacketGuardAPI packetGuard,
        MapModuleConfiguration configuration,
        MapRepository repository,
        PacketBridgeAPI bridge,
        String menuUiId,
        String hudUiId,
        ItemSourceRegistry itemSourceRegistry,
        ItemMatcherAPI itemMatcherSupport,
        CurrencyBridgeAPI currencyBridgeManager,
        WaypointBridgeAPI waypointBridge
    ) {
        this.plugin = plugin;
        this.packetGuard = packetGuard;
        this.configuration = configuration;
        this.repository = repository;
        this.bridge = bridge;
        this.menuUiId = menuUiId;
        this.hudUiId = hudUiId;
        this.uiPacketHandler = new MapUiPacketHandler(this, configuration.client().packetId());
        this.currencyBridgeManager = currencyBridgeManager;
        this.itemSourceRegistry = itemSourceRegistry;
        this.itemMatcherSupport = itemMatcherSupport;
        this.waypointBridge = waypointBridge;
    }

    public void start() throws Exception {
        repository.initialize();
        if (configuration.navigation().enabled()) {
            waypointBridge.initialize("Map 导航");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearTrackSilently(player);
            if (bridge != null && !hudUiId.isBlank()) {
                bridge.closeUi(player, hudUiId);
            }
        }
        repository.close();
        waypointBridge.shutdown();
        viewStates.clear();
        navigationStates.clear();
        externalTargets.clear();
        openedHudPlayers.clear();
        defaultUnlocksEnsured.clear();
        playerDataCache.clear();
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        return uiPacketHandler.handleClientPacket(player, packetId, data);
    }

    public void handleClientInitialized(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ensureDefaultUnlocks(player);
        if (!configuration.join().showHudOnJoin()) {
            return;
        }
        long delay = configuration.join().showHudDelayTicks();
        Bukkit.getScheduler().runTaskLater(
            plugin,
            () -> {
                if (player.isOnline()) {
                    setHudInternal(player, true, false);
                }
            },
            delay
        );
    }

    public List<String> configuredWorldIds() {
        return new ArrayList<>(configuration.worlds().keySet());
    }

    public List<String> configuredAnchorIds() {
        return new ArrayList<>(configuration.anchors().keySet());
    }

    public int configuredWorldCount() {
        return configuration.worlds().size();
    }

    public int configuredAnchorCount() {
        return configuration.anchors().size();
    }

    public int trackingPlayerCount() {
        return (int) navigationStates.values().stream().filter(MapNavigationState::active).count();
    }

    public boolean waypointRuntimeReady() {
        return !configuration.navigation().enabled() || waypointBridge.available();
    }

    public MapOperationResult openMenuFor(Player player, String worldId) {
        return openMenuInternal(player, worldId, true);
    }

    public MapOperationResult setHud(Player player, String mode) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure("玩家当前不在线。");
        }
        boolean targetVisible;
        if ("on".equalsIgnoreCase(mode)) {
            targetVisible = true;
        } else if ("off".equalsIgnoreCase(mode)) {
            targetVisible = false;
        } else {
            targetVisible = !state(player).hudVisible();
        }
        return setHudInternal(player, targetVisible, true);
    }

    public MapOperationResult clearTrackCommand(Player player) {
        return clearTrackInternal(player, true);
    }

    public void upsertExternalTarget(Player player, MapExternalTarget target, boolean select) {
        if (player == null || target == null) {
            return;
        }
        externalTargets
            .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
            .put(normalizeId(target.targetId()), target);
        if (select) {
            state(player).selectWorld(normalizeId(target.worldId()));
            state(player).selectExternalTarget(normalizeId(target.targetId()));
        }
        sync(player, false);
    }

    public void removeExternalTarget(Player player, String targetId, boolean syncView) {
        if (player == null || targetId == null || targetId.isBlank()) {
            return;
        }
        MapNavigationState navigationState = navigationStates.get(player.getUniqueId());
        if (navigationState != null && navigationState.matchesExternal(targetId)) {
            clearTrackSilently(player);
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        if (state != null) {
            state.remove(normalizeId(targetId));
            if (state.isEmpty()) {
                externalTargets.remove(player.getUniqueId());
            }
        }
        if (this.state(player).selectedExternalTargetId().equalsIgnoreCase(normalizeId(targetId))) {
            this.state(player).clearSelection();
        }
        if (syncView) {
            sync(player, false);
        }
    }

    public void clearExternalTargets(Player player, String source, boolean syncView) {
        if (player == null) {
            return;
        }
        MapNavigationState navigationState = navigationStates.get(player.getUniqueId());
        String normalizedSource = normalizeId(source);
        if (navigationState != null
            && navigationState.targetType() == TargetType.EXTERNAL
            && (normalizedSource.isBlank() || normalizeId(navigationState.source()).equals(normalizedSource))) {
            clearTrackSilently(player);
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        if (state == null || state.isEmpty()) {
            return;
        }
        state.entrySet().removeIf(entry -> normalizedSource.isBlank() || normalizeId(entry.getValue().source()).equals(normalizedSource));
        if (state.isEmpty()) {
            externalTargets.remove(player.getUniqueId());
        }
        if (syncView) {
            sync(player, false);
        }
    }

    @Override
    public boolean allowClientPacket(Player player, String action) {
        return packetGuard == null || packetGuard.allow(player, "map", action, configuration.debug());
    }

    @Override
    public void refresh(Player player) {
        sync(player, false);
    }

    @Override
    public void openMenu(Player player, String worldId) {
        openMenuInternal(player, worldId, true);
    }

    @Override
    public void openWorld(Player player, String worldId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String resolvedWorldId = normalizeId(worldId);
        if (!configuration.worlds().containsKey(resolvedWorldId)) {
            player.sendMessage(PREFIX + ChatColor.RED + "未配置地图世界: " + worldId);
            return;
        }
        state(player).selectWorld(resolvedWorldId);
        sync(player, false);
    }

    @Override
    public void selectAnchor(Player player, String anchorId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (anchor == null || !configuration.worlds().containsKey(anchor.worldId())) {
            sync(player, false);
            return;
        }
        state(player).selectWorld(anchor.worldId());
        state(player).selectAnchor(anchor.id());
        sync(player, false);
    }

    @Override
    public void selectWaypoint(Player player, String waypointId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (waypoint == null) {
            sync(player, false);
            return;
        }
        state(player).selectWorld(normalizeId(waypoint.world()));
        state(player).selectWaypoint(normalizeId(waypoint.waypointId()));
        sync(player, false);
    }

    @Override
    public void selectExternalTarget(Player player, String targetId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapExternalTarget target = findExternalTarget(player, targetId);
        if (target == null) {
            sync(player, false);
            return;
        }
        state(player).selectWorld(normalizeId(target.worldId()));
        state(player).selectExternalTarget(normalizeId(target.targetId()));
        sync(player, false);
    }

    @Override
    public void unlockAnchor(Player player, String anchorId) {
        MapOperationResult result = unlockAnchorInternal(player, anchorId);
        sendPlayerResult(player, result);
    }

    @Override
    public void teleportAnchor(Player player, String anchorId) {
        MapOperationResult result = teleportAnchorInternal(player, anchorId);
        sendPlayerResult(player, result);
    }

    @Override
    public void trackAnchor(Player player, String anchorId) {
        MapOperationResult result = trackAnchorInternal(player, anchorId);
        sendPlayerResult(player, result);
    }

    @Override
    public void trackWaypoint(Player player, String waypointId) {
        MapOperationResult result = trackWaypointInternal(player, waypointId);
        sendPlayerResult(player, result);
    }

    @Override
    public void trackExternalTarget(Player player, String targetId) {
        MapOperationResult result = trackExternalTargetInternal(player, targetId);
        sendPlayerResult(player, result);
    }

    @Override
    public void clearTrack(Player player) {
        sendPlayerResult(player, clearTrackInternal(player, true));
    }

    @Override
    public void createWaypoint(Player player) {
        sendPlayerResult(player, createWaypointInternal(player));
    }

    @Override
    public void deleteWaypoint(Player player, String waypointId) {
        sendPlayerResult(player, deleteWaypointInternal(player, waypointId));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        viewStates.remove(uuid);
        navigationStates.remove(uuid);
        externalTargets.remove(uuid);
        openedHudPlayers.remove(uuid);
        playerDataCache.remove(uuid);
    }

    private MapOperationResult openMenuInternal(Player player, String worldId, boolean pushInitPacket) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure("玩家当前不在线。");
        }
        String resolvedWorldId = resolveOpenWorld(player, worldId);
        if (resolvedWorldId.isBlank()) {
            return MapOperationResult.failure("当前世界未配置地图，且没有可用的默认地图。");
        }
        if (!configuration.worlds().containsKey(resolvedWorldId)) {
            return MapOperationResult.failure("未配置地图世界: " + worldId);
        }
        state(player).selectWorld(resolvedWorldId);
        bridge.openUi(player, menuUiId);
        syncMenu(player, pushInitPacket);
        return MapOperationResult.success("已打开地图界面。");
    }

    private MapOperationResult setHudInternal(Player player, boolean visible, boolean announce) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure("玩家当前不在线。");
        }
        String resolvedWorldId = resolveOpenWorld(player, "");
        if (visible && resolvedWorldId.isBlank()) {
            return MapOperationResult.failure("当前没有可显示的小地图世界。");
        }
        if (!resolvedWorldId.isBlank()) {
            state(player).selectWorld(resolvedWorldId);
        }
        state(player).setHudVisible(visible);
        if (!visible) {
            openedHudPlayers.remove(player.getUniqueId());
            bridge.closeUi(player, hudUiId);
            return announce ? MapOperationResult.success("已关闭小地图 HUD。") : MapOperationResult.success("");
        }
        syncHud(player, !openedHudPlayers.contains(player.getUniqueId()));
        return announce ? MapOperationResult.success("已显示小地图 HUD。") : MapOperationResult.success("");
    }

    private MapOperationResult unlockAnchorInternal(Player player, String anchorId) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure("玩家当前不在线。");
        }
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (anchor == null) {
            return MapOperationResult.failure("未找到锚点: " + anchorId);
        }
        if (isAnchorUnlocked(player, anchor)) {
            return MapOperationResult.failure("该锚点已解锁。");
        }
        MapOperationResult accessResult = validateAnchorAccess(player, anchor);
        if (!accessResult.success()) {
            return accessResult;
        }
        PreparedItemConsumption preparedItems = prepareItemConsumption(player, anchor.unlockItems());
        if (!preparedItems.success()) {
            return MapOperationResult.failure(preparedItems.message());
        }
        long unlockTime = System.currentTimeMillis();
        try {
            if (!repository.tryUnlockAnchor(player.getUniqueId(), anchor.id(), unlockTime)) {
                return MapOperationResult.failure("该锚点已解锁。");
            }
        } catch (Exception exception) {
            return MapOperationResult.failure("写入锚点解锁数据失败。");
        }
        CurrencyChargeReceipt currencyReceipt = chargeCurrencies(player, anchor.unlockCurrencies());
        if (!currencyReceipt.success()) {
            rollbackUnlock(player.getUniqueId(), anchor.id());
            return MapOperationResult.failure(currencyReceipt.message());
        }
        MapOperationResult itemResult = applyItemConsumption(player, preparedItems);
        if (!itemResult.success()) {
            rollbackCurrencies(player, currencyReceipt.successfulCharges());
            rollbackUnlock(player.getUniqueId(), anchor.id());
            return itemResult;
        }
        invalidatePlayerDataCache(player.getUniqueId());
        sync(player, false);
        return MapOperationResult.success("已解锁锚点: " + anchor.displayName());
    }

    private void rollbackUnlock(UUID playerUuid, String anchorId) {
        try {
            repository.removeUnlock(playerUuid, anchorId);
            invalidatePlayerDataCache(playerUuid);
        } catch (Exception ignored) {
        }
    }

    private MapOperationResult teleportAnchorInternal(Player player, String anchorId) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure("玩家当前不在线。");
        }
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (anchor == null) {
            return MapOperationResult.failure("未找到锚点: " + anchorId);
        }
        if (!isAnchorUnlocked(player, anchor)) {
            return MapOperationResult.failure("该锚点尚未解锁。");
        }
        MapOperationResult accessResult = validateAnchorAccess(player, anchor);
        if (!accessResult.success()) {
            return accessResult;
        }
        World world = Bukkit.getWorld(anchor.worldId());
        if (world == null) {
            return MapOperationResult.failure("目标世界当前未加载: " + anchor.worldId());
        }
        CurrencyChargeReceipt currencyReceipt = chargeCurrencies(player, anchor.teleportCurrencies());
        if (!currencyReceipt.success()) {
            return MapOperationResult.failure(currencyReceipt.message());
        }
        boolean teleported = player.teleport(
            new Location(world, anchor.x(), anchor.y(), anchor.z(), player.getLocation().getYaw(), player.getLocation().getPitch())
        );
        if (!teleported) {
            rollbackCurrencies(player, currencyReceipt.successfulCharges());
            return MapOperationResult.failure("传送失败，请稍后重试。");
        }
        sync(player, false);
        return MapOperationResult.success("已传送到锚点: " + anchor.displayName());
    }

    private MapOperationResult trackAnchorInternal(Player player, String anchorId) {
        if (!configuration.navigation().enabled()) {
            return MapOperationResult.failure("当前未启用导航功能。");
        }
        AnchorDefinition anchor = configuration.anchor(anchorId);
        if (player == null || !player.isOnline() || anchor == null) {
            return MapOperationResult.failure("未找到锚点: " + anchorId);
        }
        if (!waypointBridge.available()) {
            return MapOperationResult.failure("当前找不到可用的 ArcartX waypoint 能力。");
        }
        clearTrackSilently(player);
        String waypointId = configuration.navigation().anchorIdPrefix() + sanitize(anchor.id());
        String styleId = waypointBridge.resolveStyleId(
            configuration.waypoints().defaultStyleId(),
            configuration.navigation().waypointStyleId(),
            "Map"
        );
        if (!waypointBridge.addWaypoint(player, waypointId, anchor.displayName(), styleId, anchor.x(), anchor.y(), anchor.z())) {
            return MapOperationResult.failure("创建锚点导航失败。");
        }
        navigationStates.put(
            player.getUniqueId(),
            new MapNavigationState(true, TargetType.ANCHOR, anchor.worldId(), anchor.id(), waypointId, anchor.displayName(), "map")
        );
        sync(player, false);
        return MapOperationResult.success("已开始导航锚点: " + anchor.displayName());
    }

    private MapOperationResult trackWaypointInternal(Player player, String waypointId) {
        if (!configuration.navigation().enabled()) {
            return MapOperationResult.failure("当前未启用导航功能。");
        }
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (player == null || !player.isOnline() || waypoint == null) {
            return MapOperationResult.failure("未找到路径点: " + waypointId);
        }
        if (!waypointBridge.available()) {
            return MapOperationResult.failure("当前找不到可用的 ArcartX waypoint 能力。");
        }
        clearTrackSilently(player);
        String runtimeWaypointId = configuration.navigation().waypointIdPrefix() + sanitize(waypoint.waypointId());
        String styleId = waypointBridge.resolveStyleId(
            configuration.waypoints().defaultStyleId(),
            configuration.navigation().waypointStyleId(),
            "Map"
        );
        if (!waypointBridge.addWaypoint(player, runtimeWaypointId, waypoint.name(), styleId, waypoint.x(), waypoint.y(), waypoint.z())) {
            return MapOperationResult.failure("创建路径点导航失败。");
        }
        navigationStates.put(
            player.getUniqueId(),
            new MapNavigationState(
                true,
                TargetType.WAYPOINT,
                normalizeId(waypoint.world()),
                waypoint.waypointId(),
                runtimeWaypointId,
                waypoint.name(),
                "map"
            )
        );
        sync(player, false);
        return MapOperationResult.success("已开始导航路径点: " + waypoint.name());
    }

    private MapOperationResult trackExternalTargetInternal(Player player, String targetId) {
        if (!configuration.navigation().enabled()) {
            return MapOperationResult.failure("当前未启用导航功能。");
        }
        MapExternalTarget target = findExternalTarget(player, targetId);
        if (player == null || !player.isOnline() || target == null) {
            return MapOperationResult.failure("未找到任务导航点: " + targetId);
        }
        if (!waypointBridge.available()) {
            return MapOperationResult.failure("当前找不到可用的 ArcartX waypoint 能力。");
        }
        clearTrackSilently(player);
        String runtimeWaypointId = configuration.navigation().waypointIdPrefix() + "external-" + sanitize(target.targetId());
        String styleId = waypointBridge.resolveStyleId(
            configuration.waypoints().defaultStyleId(),
            configuration.navigation().waypointStyleId(),
            "Map"
        );
        if (!waypointBridge.addWaypoint(player, runtimeWaypointId, target.title(), styleId, target.x(), target.y(), target.z())) {
            return MapOperationResult.failure("创建任务导航点失败。");
        }
        navigationStates.put(
            player.getUniqueId(),
            new MapNavigationState(
                true,
                TargetType.EXTERNAL,
                normalizeId(target.worldId()),
                normalizeId(target.targetId()),
                runtimeWaypointId,
                target.title(),
                normalizeId(target.source())
            )
        );
        state(player).selectWorld(normalizeId(target.worldId()));
        state(player).selectExternalTarget(normalizeId(target.targetId()));
        sync(player, false);
        return MapOperationResult.success("已开始导航任务点: " + target.title());
    }

    private MapOperationResult clearTrackInternal(Player player, boolean sync) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure("玩家当前不在线。");
        }
        MapNavigationState state = navigationStates.get(player.getUniqueId());
        if (state == null || !state.active()) {
            if (sync) {
                sync(player, false);
            }
            return MapOperationResult.success("当前没有活动导航。");
        }
        if (!waypointBridge.removeWaypoint(player, state.waypointId(), false)) {
            return MapOperationResult.failure("清除导航失败。");
        }
        navigationStates.remove(player.getUniqueId());
        if (sync) {
            sync(player, false);
        }
        return MapOperationResult.success("已清除当前导航。");
    }

    private MapOperationResult createWaypointInternal(Player player) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure("玩家当前不在线。");
        }
        if (!configuration.waypoints().enabled()) {
            return MapOperationResult.failure("当前未启用自定义路径点。");
        }
        String worldId = normalizeId(player.getWorld().getName());
        if (!configuration.worlds().containsKey(worldId)) {
            return MapOperationResult.failure("当前世界未配置地图，无法创建路径点。");
        }
        List<MapWaypoint> waypoints = loadWaypoints(player);
        int limit = resolveWaypointLimit(player);
        long countInWorld = waypoints.stream().filter(waypoint -> normalizeId(waypoint.world()).equals(worldId)).count();
        if (countInWorld >= limit) {
            return MapOperationResult.failure("当前世界的路径点数量已达到上限: " + limit);
        }
        long now = System.currentTimeMillis();
        String waypointId = configuration.waypoints().idPrefix() + UUID.randomUUID().toString().replace("-", "");
        String name = configuration.waypoints().autoNamePrefix() + "-" + (countInWorld + 1);
        Location location = player.getLocation();
        MapWaypoint waypoint = new MapWaypoint(
            normalizeId(waypointId),
            name,
            worldId,
            location.getX(),
            location.getY(),
            location.getZ(),
            now,
            now
        );
        try {
            if (!repository.createWaypointIfUnderWorldLimit(
                player.getUniqueId(), waypoint, worldId, limit
            )) {
                return MapOperationResult.failure("当前世界的路径点数量已达到上限: " + limit);
            }
            invalidatePlayerDataCache(player.getUniqueId());
            state(player).selectWorld(worldId);
            state(player).selectWaypoint(waypoint.waypointId());
            sync(player, false);
            return MapOperationResult.success("已创建路径点: " + name);
        } catch (Exception exception) {
            return MapOperationResult.failure("保存路径点失败。");
        }
    }

    private MapOperationResult deleteWaypointInternal(Player player, String waypointId) {
        if (player == null || !player.isOnline()) {
            return MapOperationResult.failure("玩家当前不在线。");
        }
        MapWaypoint waypoint = findWaypoint(player, waypointId);
        if (waypoint == null) {
            return MapOperationResult.failure("未找到路径点: " + waypointId);
        }
        try {
            if (!repository.deleteWaypoint(player.getUniqueId(), waypoint.waypointId())) {
                return MapOperationResult.failure("删除路径点失败。");
            }
            invalidatePlayerDataCache(player.getUniqueId());
            if (navigationStates.getOrDefault(player.getUniqueId(), MapNavigationState.none()).matchesWaypoint(waypoint.waypointId())) {
                clearTrackSilently(player);
            }
            if (state(player).selectedWaypointId().equalsIgnoreCase(waypoint.waypointId())) {
                state(player).clearSelection();
            }
            sync(player, false);
            return MapOperationResult.success("已删除路径点: " + waypoint.name());
        } catch (Exception exception) {
            return MapOperationResult.failure("删除路径点失败。");
        }
    }

    private MapOperationResult validateAnchorAccess(Player player, AnchorDefinition anchor) {
        if (!anchor.permission().isBlank() && !player.hasPermission(anchor.permission())) {
            return MapOperationResult.failure("你没有使用该锚点的权限。");
        }
        if (!configuration.worlds().containsKey(anchor.worldId())) {
            return MapOperationResult.failure("锚点所在世界未配置地图: " + anchor.worldId());
        }
        return MapOperationResult.success("");
    }

    private CurrencyChargeReceipt chargeCurrencies(Player player, List<CurrencyCost> costs) {
        if (costs == null || costs.isEmpty()) {
            return CurrencyChargeReceipt.success(List.of());
        }
        for (CurrencyCost cost : costs) {
            CurrencyBridge bridge = currencyBridgeManager.bridge(cost.currencyId());
            if (bridge == null || !bridge.available()) {
                return CurrencyChargeReceipt.failure("货币桥接不可用: " + cost.currencyId());
            }
            if (bridge.balance(player).compareTo(cost.amount()) < 0) {
                return CurrencyChargeReceipt.failure("余额不足: " + cost.currencyId());
            }
        }
        List<CurrencyCost> successfulCharges = new ArrayList<>();
        for (CurrencyCost cost : costs) {
            CurrencyBridge bridge = currencyBridgeManager.bridge(cost.currencyId());
            if (bridge == null || !bridge.available()) {
                rollbackCurrencies(player, successfulCharges);
                return CurrencyChargeReceipt.failure("货币桥接不可用: " + cost.currencyId());
            }
            CurrencyTransactionResult result = bridge.withdraw(player, cost.amount());
            if (!result.success()) {
                rollbackCurrencies(player, successfulCharges);
                return CurrencyChargeReceipt.failure(result.message().isBlank() ? "扣费失败: " + cost.currencyId() : result.message());
            }
            successfulCharges.add(cost);
        }
        return CurrencyChargeReceipt.success(successfulCharges);
    }

    private void rollbackCurrencies(Player player, List<CurrencyCost> successfulCharges) {
        for (CurrencyCost cost : successfulCharges) {
            CurrencyBridge bridge = currencyBridgeManager.bridge(cost.currencyId());
            if (bridge != null && bridge.available()) {
                bridge.deposit(player, cost.amount());
            }
        }
    }

    private PreparedItemConsumption prepareItemConsumption(Player player, List<ItemCost> itemCosts) {
        if (itemCosts == null || itemCosts.isEmpty()) {
            return PreparedItemConsumption.empty();
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] snapshot = cloneContents(contents);
        int[] remaining = new int[contents.length];
        int[] deductions = new int[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            remaining[slot] = contents[slot] == null ? 0 : contents[slot].getAmount();
        }
        for (ItemCost cost : itemCosts) {
            int need = Math.max(1, cost.amount());
            for (int slot = 0; slot < contents.length && need > 0; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType().isAir() || remaining[slot] <= 0 || !itemMatcherSupport.matches(cost.matcher(), stack)) {
                    continue;
                }
                int take = Math.min(need, remaining[slot]);
                remaining[slot] -= take;
                deductions[slot] += take;
                need -= take;
            }
            if (need > 0) {
                return PreparedItemConsumption.failure("缺少解锁所需物品。");
            }
        }
        return PreparedItemConsumption.success(snapshot, deductions);
    }

    private MapOperationResult applyItemConsumption(Player player, PreparedItemConsumption prepared) {
        if (prepared == null || !prepared.success() || !prepared.hasChanges()) {
            return MapOperationResult.success("");
        }
        ItemStack[] updatedContents = cloneContents(prepared.originalContents());
        for (int slot = 0; slot < updatedContents.length; slot++) {
            if (prepared.deductions()[slot] <= 0 || updatedContents[slot] == null) {
                continue;
            }
            int updatedAmount = updatedContents[slot].getAmount() - prepared.deductions()[slot];
            if (updatedAmount <= 0) {
                updatedContents[slot] = null;
            } else {
                updatedContents[slot].setAmount(updatedAmount);
            }
        }
        player.getInventory().setStorageContents(updatedContents);
        player.updateInventory();
        return MapOperationResult.success("");
    }

    private void rollbackItemConsumption(Player player, PreparedItemConsumption prepared) {
        if (player == null || prepared == null || !prepared.success() || !prepared.hasChanges()) {
            return;
        }
        player.getInventory().setStorageContents(cloneContents(prepared.originalContents()));
        player.updateInventory();
    }

    private void sync(Player player, boolean initPacket) {
        syncMenu(player, initPacket);
        if (state(player).hudVisible()) {
            syncHud(player, !openedHudPlayers.contains(player.getUniqueId()));
        }
    }

    private void syncMenu(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapSnapshotBuilder.BuildResult snapshot = buildSnapshot(player);
        bridge.sendPacket(player, menuUiId, initPacket ? "init" : "update", buildMenuPayload(snapshot.menu()));
    }

    private void syncHud(Player player, boolean initPacket) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapSnapshotBuilder.BuildResult snapshot = buildSnapshot(player);
        if (!snapshot.hud().visible()) {
            bridge.closeUi(player, hudUiId);
            openedHudPlayers.remove(player.getUniqueId());
            return;
        }
        if (initPacket) {
            bridge.openUi(player, hudUiId);
            openedHudPlayers.add(player.getUniqueId());
        }
        bridge.sendPacket(player, hudUiId, initPacket ? "init" : "update", buildHudPayload(snapshot.hud()));
    }

    private MapSnapshotBuilder.BuildResult buildSnapshot(Player player) {
        MapPlayerViewState state = state(player);
        List<MapSnapshotBuilder.WorldView> worlds = new ArrayList<>();
        for (WorldDefinition world : configuration.worlds().values()) {
            worlds.add(
                new MapSnapshotBuilder.WorldView(
                    world.id(),
                    world.displayName(),
                    world.texture(),
                    world.imageWidth(),
                    world.imageHeight(),
                    world.pixelOffsetX(),
                    world.pixelOffsetZ(),
                    world.defaultZoom(),
                    world.hudZoom(),
                    world.hudSize()
                )
            );
        }

        Set<String> unlockedAnchors = loadUnlockedAnchors(player);
        List<MapSnapshotBuilder.AnchorView> anchors = new ArrayList<>();
        for (AnchorDefinition anchor : configuration.anchors().values()) {
            boolean unlocked = unlockedAnchors.contains(anchor.id());
            anchors.add(
                new MapSnapshotBuilder.AnchorView(
                    anchor.id(),
                    anchor.worldId(),
                    anchor.displayName(),
                    anchor.description(),
                    anchor.x(),
                    anchor.y(),
                    anchor.z(),
                    unlocked,
                    formatUnlockCostText(anchor),
                    formatTeleportCostText(anchor),
                    !unlocked,
                    unlocked,
                    anchor.sortOrder()
                )
            );
        }

        Location location = player == null ? null : player.getLocation();
        return snapshotBuilder.build(
            new MapSnapshotBuilder.BuildInput(
                configuration.client().packetId(),
                worlds,
                anchors,
                loadWaypoints(player),
                loadExternalTargets(player),
                state,
                navigationStates.getOrDefault(player.getUniqueId(), MapNavigationState.none()),
                location == null ? null : new MapSnapshotBuilder.PlayerPosition(location.getX(), location.getY(), location.getZ(), location.getYaw()),
                location == null ? "" : normalizeId(location.getWorld().getName()),
                resolveWaypointLimit(player),
                configuration.navigation().enabled() && waypointRuntimeReady(),
                configuration.waypoints().enabled()
            )
        );
    }

    private Map<String, Object> buildMenuPayload(MapSnapshotBuilder.MenuSnapshot snapshot) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", snapshot.packetId());
        payload.put("selectedWorldId", snapshot.selectedWorldId());
        payload.put("selectedWorldName", snapshot.selectedWorldName());

        LinkedHashMap<String, Object> worldRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.worldRows().size(); i++) {
            MapSnapshotBuilder.WorldRow row = snapshot.worldRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("displayName", row.displayName());
            entry.put("texture", row.texture());
            entry.put("selected", row.selected());
            worldRows.put("w" + i, entry);
        }
        payload.put("worldRows", worldRows);

        LinkedHashMap<String, Object> anchorRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.anchorRows().size(); i++) {
            MapSnapshotBuilder.AnchorRow row = snapshot.anchorRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("displayName", row.displayName());
            entry.put("description", row.description());
            entry.put("x", row.x());
            entry.put("y", row.y());
            entry.put("z", row.z());
            entry.put("unlocked", row.unlocked());
            entry.put("unlockCostText", row.unlockCostText());
            entry.put("teleportCostText", row.teleportCostText());
            entry.put("selected", row.selected());
            entry.put("tracked", row.tracked());
            anchorRows.put("a" + i, entry);
        }
        payload.put("anchorRows", anchorRows);

        LinkedHashMap<String, Object> waypointRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.waypointRows().size(); i++) {
            MapSnapshotBuilder.WaypointRow row = snapshot.waypointRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("name", row.name());
            entry.put("x", row.x());
            entry.put("y", row.y());
            entry.put("z", row.z());
            entry.put("selected", row.selected());
            entry.put("tracked", row.tracked());
            waypointRows.put("wp" + i, entry);
        }
        payload.put("waypointRows", waypointRows);

        LinkedHashMap<String, Object> externalRows = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.externalTargetRows().size(); i++) {
            MapSnapshotBuilder.ExternalTargetRow row = snapshot.externalTargetRows().get(i);
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.id());
            entry.put("source", row.source());
            entry.put("name", row.name());
            entry.put("description", row.description());
            entry.put("x", row.x());
            entry.put("y", row.y());
            entry.put("z", row.z());
            entry.put("selected", row.selected());
            entry.put("tracked", row.tracked());
            externalRows.put("e" + i, entry);
        }
        payload.put("externalTargetRows", externalRows);

        payload.put("waypointLimit", snapshot.waypointLimit());
        payload.put("waypointCount", snapshot.waypointCount());
        payload.put("canCreateWaypoint", snapshot.canCreateWaypoint());
        payload.put("clearTrackVisible", snapshot.clearTrackVisible());
        payload.put("trackingText", snapshot.trackingText());
        payload.put("detailSelectedType", snapshot.detail().selectedType());
        payload.put("detailSelectedId", snapshot.detail().selectedId());
        payload.put("detailTitle", snapshot.detail().title());
        payload.put("detailDescription", snapshot.detail().description());
        payload.put("detailUnlocked", snapshot.detail().unlocked());
        payload.put("detailUnlockCostText", snapshot.detail().unlockCostText());
        payload.put("detailTeleportCostText", snapshot.detail().teleportCostText());
        payload.put("detailExternalTarget", snapshot.detail().externalTarget());
        payload.put("detailCanUnlock", snapshot.detail().canUnlock());
        payload.put("detailCanTeleport", snapshot.detail().canTeleport());
        payload.put("detailCanTrackAnchor", snapshot.detail().canTrackAnchor());
        payload.put("detailCanTrackWaypoint", snapshot.detail().canTrackWaypoint());
        payload.put("detailCanTrackExternal", snapshot.detail().canTrackExternal());
        payload.put("detailCanDeleteWaypoint", snapshot.detail().canDeleteWaypoint());
        payload.put("detailCanCreateWaypoint", snapshot.detail().canCreateWaypoint());
        payload.put("detailClearTrackVisible", snapshot.detail().clearTrackVisible());
        payload.put("detailTrackingText", snapshot.detail().trackingText());
        return payload;
    }

    private Map<String, Object> buildHudPayload(MapSnapshotBuilder.HudSnapshot snapshot) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("packetId", snapshot.packetId());
        payload.put("visible", snapshot.visible());
        payload.put("worldId", snapshot.worldId());
        payload.put("texture", snapshot.texture());
        payload.put("imageWidth", snapshot.imageWidth());
        payload.put("imageHeight", snapshot.imageHeight());
        payload.put("hudZoom", snapshot.hudZoom());
        payload.put("hudSize", snapshot.hudSize());
        payload.put("clippedPlayerX", snapshot.clippedPlayerX());
        payload.put("clippedPlayerZ", snapshot.clippedPlayerZ());
        payload.put("playerYaw", snapshot.playerYaw());
        payload.put("trackingText", snapshot.trackingText());
        return payload;
    }


    private String formatUnlockCostText(AnchorDefinition anchor) {
        List<String> parts = new ArrayList<>();
        for (CurrencyCost cost : anchor.unlockCurrencies()) {
            parts.add(formatCurrencyCost(cost));
        }
        for (ItemCost cost : anchor.unlockItems()) {
            parts.add("物品条件 x" + cost.amount());
        }
        return parts.isEmpty() ? "免费解锁" : String.join(" + ", parts);
    }

    private String formatTeleportCostText(AnchorDefinition anchor) {
        List<String> parts = new ArrayList<>();
        for (CurrencyCost cost : anchor.teleportCurrencies()) {
            parts.add(formatCurrencyCost(cost));
        }
        return parts.isEmpty() ? "免费传送" : String.join(" + ", parts);
    }

    private String formatCurrencyCost(CurrencyCost cost) {
        CurrencyBridge bridge = currencyBridgeManager.bridge(cost.currencyId());
        String displayName = bridge == null || bridge.definition() == null ? cost.currencyId() : bridge.definition().displayName();
        return displayName + " x" + cost.amount().setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
    }

    private Set<String> loadUnlockedAnchors(Player player) {
        if (player == null) {
            return Set.of();
        }
        return loadPlayerData(player.getUniqueId()).unlockedAnchors();
    }

    private void ensureDefaultUnlocks(Player player) {
        if (player == null || !player.isOnline() || !defaultUnlocksEnsured.add(player.getUniqueId())) {
            return;
        }
        try {
            Set<String> existing = new LinkedHashSet<>(loadUnlockedAnchors(player));
            boolean changed = false;
            long now = System.currentTimeMillis();
            for (DefaultUnlockRule rule : configuration.defaultUnlocks()) {
                if (!player.hasPermission(rule.permission())) {
                    continue;
                }
                for (String anchorId : rule.anchorIds()) {
                    if (existing.add(anchorId)) {
                        repository.unlockAnchor(player.getUniqueId(), anchorId, now);
                        changed = true;
                    }
                }
            }
            if (changed) {
                invalidatePlayerDataCache(player.getUniqueId());
            }
        } catch (Exception ignored) {
        }
    }

    private int resolveWaypointLimit(Player player) {
        int limit = configuration.waypoints().defaultMaxCount();
        if (player == null) {
            return limit;
        }
        for (MapModuleConfiguration.WaypointLimit entry : configuration.waypoints().limits()) {
            if (entry.permission().isBlank() || player.hasPermission(entry.permission())) {
                limit = Math.max(limit, entry.maxCount());
            }
        }
        return limit;
    }

    private List<MapWaypoint> loadWaypoints(Player player) {
        if (player == null) {
            return List.of();
        }
        return loadPlayerData(player.getUniqueId()).waypoints();
    }

    private CachedPlayerMapData loadPlayerData(UUID playerUuid) {
        return playerDataCache.computeIfAbsent(playerUuid, uuid -> {
            try {
                Set<String> anchors = repository.loadUnlockedAnchors(uuid);
                List<MapWaypoint> waypoints = repository.loadWaypoints(uuid);
                return new CachedPlayerMapData(Set.copyOf(anchors), List.copyOf(waypoints));
            } catch (Exception exception) {
                return new CachedPlayerMapData(Set.of(), List.of());
            }
        });
    }

    private void invalidatePlayerDataCache(UUID playerUuid) {
        if (playerUuid != null) {
            playerDataCache.remove(playerUuid);
        }
    }

    private List<MapExternalTarget> loadExternalTargets(Player player) {
        if (player == null) {
            return List.of();
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        if (state == null || state.isEmpty()) {
            return List.of();
        }
        return List.copyOf(state.values());
    }

    private MapWaypoint findWaypoint(Player player, String waypointId) {
        String normalized = normalizeId(waypointId);
        for (MapWaypoint waypoint : loadWaypoints(player)) {
            if (normalizeId(waypoint.waypointId()).equals(normalized)) {
                return waypoint;
            }
        }
        return null;
    }

    private MapExternalTarget findExternalTarget(Player player, String targetId) {
        if (player == null) {
            return null;
        }
        ConcurrentMap<String, MapExternalTarget> state = externalTargets.get(player.getUniqueId());
        return state == null ? null : state.get(normalizeId(targetId));
    }

    private boolean isAnchorUnlocked(Player player, AnchorDefinition anchor) {
        if (anchor == null) {
            return false;
        }
        return loadUnlockedAnchors(player).contains(anchor.id());
    }

    private String resolveOpenWorld(Player player, String requestedWorldId) {
        String requested = normalizeId(requestedWorldId);
        if (!requested.isBlank()) {
            return configuration.worlds().containsKey(requested) ? requested : "";
        }
        MapPlayerViewState viewState = state(player);
        if (!viewState.selectedWorldId().isBlank() && configuration.worlds().containsKey(viewState.selectedWorldId())) {
            return viewState.selectedWorldId();
        }
        if (player != null && player.getWorld() != null) {
            String currentWorldId = normalizeId(player.getWorld().getName());
            if (configuration.worlds().containsKey(currentWorldId)) {
                return currentWorldId;
            }
        }
        return configuration.worlds().isEmpty() ? "" : configuration.worlds().keySet().iterator().next();
    }

    private MapPlayerViewState state(Player player) {
        return viewStates.computeIfAbsent(player.getUniqueId(), ignored -> new MapPlayerViewState());
    }

    private void clearTrackSilently(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        MapNavigationState state = navigationStates.remove(player.getUniqueId());
        if (state != null && state.active()) {
            waypointBridge.removeWaypoint(player, state.waypointId(), false);
        }
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents == null ? 0 : contents.length];
        if (contents == null) {
            return clone;
        }
        for (int index = 0; index < contents.length; index++) {
            clone[index] = contents[index] == null ? null : contents[index].clone();
        }
        return clone;
    }

    private void sendPlayerResult(Player player, MapOperationResult result) {
        if (player == null || result == null || result.message().isBlank()) {
            return;
        }
        player.sendMessage(PREFIX + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static String sanitize(String value) {
        return normalizeId(value).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record CurrencyChargeReceipt(
        boolean success,
        String message,
        List<CurrencyCost> successfulCharges
    ) {

        private static CurrencyChargeReceipt success(List<CurrencyCost> successfulCharges) {
            return new CurrencyChargeReceipt(true, "", List.copyOf(successfulCharges));
        }

        private static CurrencyChargeReceipt failure(String message) {
            return new CurrencyChargeReceipt(false, message, List.of());
        }
    }

    private record PreparedItemConsumption(
        boolean success,
        String message,
        ItemStack[] originalContents,
        int[] deductions
    ) {

        private static PreparedItemConsumption empty() {
            return new PreparedItemConsumption(true, "", new ItemStack[0], new int[0]);
        }

        private static PreparedItemConsumption success(ItemStack[] originalContents, int[] deductions) {
            return new PreparedItemConsumption(true, "", originalContents, deductions.clone());
        }

        private static PreparedItemConsumption failure(String message) {
            return new PreparedItemConsumption(false, message, new ItemStack[0], new int[0]);
        }

        private boolean hasChanges() {
            for (int deduction : deductions) {
                if (deduction > 0) {
                    return true;
                }
            }
            return false;
        }
    }
}
