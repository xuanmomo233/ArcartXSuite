package xuanmo.arcartxsuite.bridge;

import ink.ptms.adyeshach.api.AdyeshachAPI;
import ink.ptms.adyeshach.common.entity.EntityInstance;
import ink.ptms.adyeshach.common.entity.EntityTypes;
import ink.ptms.adyeshach.common.entity.manager.Manager;
import ink.ptms.adyeshach.core.Adyeshach;
import ink.ptms.adyeshach.core.AdyeshachEntityFinder;
import ink.ptms.adyeshach.core.util.Components;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.core.network.ArcartXNetworkSender;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc;
import xuanmo.arcartxsuite.api.bridge.AdyeshachNpcScanBridge;

/**
 * {@link xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI} 的默认实现，
 * 桥接 Adyeshach NPC 插件，提供 NPC 创建、查找、可见性控制等能力。
 * <p>
 * Adyeshach 为可选依赖，运行时通过 {@link #initialize()} 懒检测可用性。
 */
public final class AdyeshachNpcBridge implements xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI, AdyeshachNpcScanBridge {

    private static final Comparator<AdyeshachNearbyNpc> NEARBY_ORDER =
        Comparator.comparingDouble(AdyeshachNearbyNpc::distanceSquared)
            .thenComparing(npc -> npc.label().toLowerCase(Locale.ROOT))
            .thenComparing(npc -> npc.npcId().toLowerCase(Locale.ROOT));

    private final JavaPlugin plugin;
    private final ConcurrentMap<String, EntityInstance> privateMarkerEntities = new ConcurrentHashMap<>();

    private boolean available;
    private boolean debug;
    private Manager publicManager;
    private Manager publicTemporaryManager;
    private AdyeshachEntityFinder entityFinder;

    private Listener adyeshachVisibleListener;
    private BiConsumer<Player, Object> adyeshachVisibleHandler;
    private Method adyVisibleGetEntityMethod;
    private Method adyVisibleGetViewerMethod;
    private Method adyVisibleGetVisibleMethod;

    private Listener adyeshachInteractListener;
    private BiConsumer<Player, Object> adyeshachInteractHandler;
    private Method adyInteractGetEntityMethod;
    private Method adyInteractGetPlayerMethod;

    public AdyeshachNpcBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public boolean initialize() {
        available = false;
        publicManager = null;
        publicTemporaryManager = null;
        entityFinder = null;
        privateMarkerEntities.clear();

        Plugin adyeshach = plugin.getServer().getPluginManager().getPlugin("Adyeshach");
        if (adyeshach == null) {
            return false;
        }

        try {
            publicManager = AdyeshachAPI.INSTANCE.getEntityManagerPublic();
            if (publicManager == null) {
                throw new IllegalStateException("Adyeshach 公共实体管理器不可用。");
            }
            try {
                publicTemporaryManager = AdyeshachAPI.INSTANCE.getEntityManagerPublicTemporary();
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("Adyeshach 公共临时管理器不可用，临时 NPC 能力停用: " + exception.getMessage());
            }
            entityFinder = Adyeshach.INSTANCE.api().getEntityFinder();
            available = true;
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("初始化 Adyeshach NPC 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void shutdown() {
        unregisterVisibleHandler();
        unregisterInteractHandler();
        clearAllPrivateMarkers();
        available = false;
        publicManager = null;
        publicTemporaryManager = null;
        entityFinder = null;
        privateMarkerEntities.clear();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public List<xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc> findNearby(Player player, double range) {
        if (!available || player == null || !player.isOnline() || range <= 0.0D) {
            return List.of();
        }

        World playerWorld = player.getWorld();
        Location playerLocation = player.getLocation();
        double rangeSquared = range * range;
        List<xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc> result = new ArrayList<>();
        for (Manager manager : publicManagers()) {
            if (manager == null) {
                continue;
            }
            for (EntityInstance entity : manager.getEntities()) {
                try {
                    xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc nearbyNpc = resolveNearbyNpc(entity, player, playerWorld, playerLocation, rangeSquared);
                    if (nearbyNpc != null) {
                        result.add(nearbyNpc);
                    }
                } catch (RuntimeException exception) {
                    logWarning("scan-entity", entity, exception);
                }
            }
        }

        result.sort(NEARBY_ORDER);
        return List.copyOf(result);
    }

    @Override
    public List<AdyeshachNearbyNpc> findVisibleNearby(Player player, double range, Predicate<String> eligibleId) {
        if (!canScan(player, range)) return List.of();
        Location origin = player.getLocation();
        double limit = range * range;
        List<AdyeshachNearbyNpc> matches = new ArrayList<>();
        for (var entity : entityFinder.getVisibleEntities(player, ignored -> true)) {
            try {
                var manager = entity.getManager();
                if (!isPublicManager(manager) || entity.isRemoved() || entity.isInvalid()) continue;
                String npcId = entity.getId();
                if (npcId == null || npcId.isBlank() || !eligibleId.test(npcId)) continue;
                Location point = entity.getLocation();
                double distance = distanceSquared(origin, point);
                if (!Double.isFinite(distance) || distance > limit || !entity.isVisibleViewer(player)
                    || manager.getEntityByUniqueId(entity.getUniqueId()) != entity) continue;

                String label = firstNonBlank(entity.getDisplayName());
                if (label.isEmpty()) {
                    label = firstNonBlank(Components.INSTANCE.toLegacyText(entity.<String>getMetadata("customName")), npcId);
                }
                matches.add(new AdyeshachNearbyNpc(npcId, label, point.clone(), entity, entity, distance));
            } catch (RuntimeException exception) {
                logWarning("scan-visible-entity", entity, exception);
            }
        }
        // Prefer the persistent manager when two entries have the same displayed sorting keys.
        var persistent = publicManager.v2();
        matches.sort(NEARBY_ORDER
            .thenComparing(npc -> ((ink.ptms.adyeshach.core.entity.EntityInstance) npc.rawEntity()).getManager() != persistent)
            .thenComparing(npc -> ((ink.ptms.adyeshach.core.entity.EntityInstance) npc.rawEntity()).getUniqueId()));
        return List.copyOf(matches);
    }

    @Override
    public boolean canInteract(Player player, double range, String npcId, Object conversationEntity) {
        if (!canScan(player, range)
            || !(conversationEntity instanceof ink.ptms.adyeshach.core.entity.EntityInstance entity)) return false;
        var manager = entity.getManager();
        if (!isPublicManager(manager) || entity.isRemoved() || entity.isInvalid() || !npcId.equalsIgnoreCase(entity.getId())
            || manager.getEntityByUniqueId(entity.getUniqueId()) != entity) return false;
        double distance = distanceSquared(player.getLocation(), entity.getLocation());
        return Double.isFinite(distance) && distance <= range * range && entity.isVisibleViewer(player);
    }

    private boolean canScan(Player player, double range) {
        return available && player != null && player.isOnline() && Double.isFinite(range) && range > 0;
    }

    private boolean isPublicManager(ink.ptms.adyeshach.core.entity.manager.Manager manager) {
        return manager != null && (manager == publicManager.v2()
            || publicTemporaryManager != null && manager == publicTemporaryManager.v2());
    }

    private static double distanceSquared(Location origin, Location target) {
        if (target == null || target.getWorld() == null || !target.getWorld().equals(origin.getWorld())) {
            return Double.POSITIVE_INFINITY;
        }
        return origin.distanceSquared(target);
    }

    @Override
    public Optional<Object> findByName(String name) {
        if (!available || name == null || name.isBlank()) {
            return Optional.empty();
        }
        String lower = name.strip().toLowerCase(Locale.ROOT);
        for (Manager manager : publicManagers()) {
            if (manager == null) {
                continue;
            }
            for (EntityInstance entity : manager.getEntities()) {
                if (entity == null || entity.isDeleted() || entity.getInvalid()) {
                    continue;
                }
                String displayName = entity.getDisplayName();
                if (displayName != null && !displayName.isBlank() && displayName.strip().toLowerCase(Locale.ROOT).equals(lower)) {
                    return Optional.of(entity);
                }
                String customName = entity.getCustomName();
                if (customName != null && !customName.isBlank() && customName.strip().toLowerCase(Locale.ROOT).equals(lower)) {
                    return Optional.of(entity);
                }
                String id = entity.getId();
                if (id != null && !id.isBlank() && id.strip().toLowerCase(Locale.ROOT).equals(lower)) {
                    return Optional.of(entity);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Location> locateByName(String name) {
        Optional<Object> found = findByName(name);
        if (found.isEmpty() || !(found.get() instanceof EntityInstance entity)) {
            return Optional.empty();
        }
        if (entity.isDeleted() || entity.getInvalid()) {
            return Optional.empty();
        }
        Location location = entity.getLocation();
        return location == null ? Optional.empty() : Optional.of(location.clone());
    }

    @Override
    public List<String> getEntityNames(Object adyeshachEntity) {
        if (!(adyeshachEntity instanceof EntityInstance entity)) {
            return List.of();
        }
        List<String> names = new ArrayList<>(3);
        String displayName = entity.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            names.add(displayName.strip());
        }
        String customName = entity.getCustomName();
        if (customName != null && !customName.isBlank()) {
            names.add(customName.strip());
        }
        String id = entity.getId();
        if (id != null && !id.isBlank()) {
            names.add(id.strip());
        }
        return names;
    }

    @Override
    public boolean registerVisibleHandler(BiConsumer<Player, Object> handler) {
        unregisterVisibleHandler();
        if (!available || handler == null) {
            return false;
        }
        Plugin adyeshach = plugin.getServer().getPluginManager().getPlugin("Adyeshach");
        if (adyeshach == null) {
            return false;
        }
        try {
            ClassLoader classLoader = adyeshach.getClass().getClassLoader();
            Class<?> eventClass = Class.forName(
                "ink.ptms.adyeshach.core.event.AdyeshachEntityVisibleEvent", true, classLoader);
            if (!Event.class.isAssignableFrom(eventClass)) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: AdyeshachEntityVisibleEvent 不是 Bukkit Event，无法注册监听器。");
                return false;
            }
            adyVisibleGetEntityMethod = eventClass.getMethod("getEntity");
            adyVisibleGetViewerMethod = eventClass.getMethod("getViewer");
            adyVisibleGetVisibleMethod = eventClass.getMethod("getVisible");

            adyeshachVisibleHandler = handler;
            adyeshachVisibleListener = new Listener() {};

            @SuppressWarnings("unchecked")
            Class<? extends Event> typed = (Class<? extends Event>) eventClass;
            EventExecutor executor = (listener, event) -> dispatchAdyeshachVisibleEvent(event);
            plugin.getServer().getPluginManager().registerEvent(
                typed,
                adyeshachVisibleListener,
                EventPriority.MONITOR,
                executor,
                plugin
            );
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: 已注册 AdyeshachEntityVisibleEvent 监听器。");
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 注册 AdyeshachEntityVisibleEvent 监听器失败 - " + exception.getMessage());
            unregisterVisibleHandler();
            return false;
        }
    }

    @Override
    public void unregisterVisibleHandler() {
        if (adyeshachVisibleListener != null) {
            xuanmo.arcartxsuite.util.ListenerUnregister.safeUnregisterAll(adyeshachVisibleListener, "AdyeshachNpcBridge");
            adyeshachVisibleListener = null;
        }
        adyeshachVisibleHandler = null;
        adyVisibleGetEntityMethod = null;
        adyVisibleGetViewerMethod = null;
        adyVisibleGetVisibleMethod = null;
    }

    private void dispatchAdyeshachVisibleEvent(Event event) {
        BiConsumer<Player, Object> handler = adyeshachVisibleHandler;
        if (handler == null || event == null) {
            return;
        }
        try {
            Object visibleResult = adyVisibleGetVisibleMethod.invoke(event);
            if (!(visibleResult instanceof Boolean visible) || !visible) {
                return;
            }
            Object entity = adyVisibleGetEntityMethod.invoke(event);
            Object viewerObj = adyVisibleGetViewerMethod.invoke(event);
            if (!(viewerObj instanceof Player viewer) || entity == null) {
                return;
            }
            handler.accept(viewer, entity);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 处理 AdyeshachEntityVisibleEvent 失败 - " + exception.getMessage());
            }
        }
    }

    // ======================== 私有临时实体（导航标记）========================

    @Override
    public Object spawnPrivateMarker(Player player, String markerId, Location location) {
        if (!available || player == null || markerId == null || location == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: spawnPrivateMarker 前置检查失败 available=" + available);
            return null;
        }
        try {
            Manager privateTempManager = AdyeshachAPI.INSTANCE.getEntityManagerPrivateTemporary(player);
            if (privateTempManager == null) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: getEntityManagerPrivateTemporary 返回 null, player=" + player.getName());
                return null;
            }
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: spawnPrivateMarker manager=" + privateTempManager.getClass().getSimpleName() + " loc=" + location);
            }
            EntityInstance entity = privateTempManager.create(EntityTypes.ARMOR_STAND, location);
            if (entity == null) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: manager.create() 返回 null");
                return null;
            }
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: 实体已创建 class=" + entity.getClass().getSimpleName());
            }
            // 隐藏名称标签
            try {
                entity.setCustomName("");
                entity.setCustomNameVisible(false);
            } catch (RuntimeException e) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 隐藏名称失败 - " + e.getMessage());
            }
            // 设置无碰撞元数据（ArmorStand marker 模式）
            try {
                entity.setMetadata("isSmall", true);
                entity.setMetadata("isMarker", true);
                entity.setMetadata("isNoGravity", true);
            } catch (RuntimeException ignored) {
            }
            privateMarkerEntities.put(markerKey(player, markerId), entity);
            return entity;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: spawnPrivateMarker 失败 - " + exception.getMessage());
            return null;
        }
    }

    @Override
    public boolean teleportMarker(Player player, String markerId, Location location) {
        if (player == null || markerId == null || location == null) {
            return false;
        }
        EntityInstance entity = privateMarkerEntities.get(markerKey(player, markerId));
        if (entity == null) {
            return false;
        }
        try {
            entity.teleport(location);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: teleportMarker 失败 - " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean removePrivateMarker(Player player, String markerId) {
        if (player == null || markerId == null) {
            return false;
        }
        EntityInstance entity = privateMarkerEntities.remove(markerKey(player, markerId));
        if (entity == null) {
            return false;
        }
        return deleteEntity(entity);
    }

    @Override
    public void clearAllPrivateMarkers() {
        for (EntityInstance entity : privateMarkerEntities.values()) {
            deleteEntity(entity);
        }
        privateMarkerEntities.clear();
    }

    @Override
    public Object getPrivateMarker(Player player, String markerId) {
        if (player == null || markerId == null) {
            return null;
        }
        return privateMarkerEntities.get(markerKey(player, markerId));
    }

    private boolean deleteEntity(EntityInstance entity) {
        if (entity == null) {
            return false;
        }
        try {
            entity.delete();
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: deleteEntity 失败 - " + exception.getMessage());
            return false;
        }
    }

    private static String markerKey(Player player, String markerId) {
        return player.getUniqueId().toString() + ":" + markerId;
    }

    // ======================== ArcartX 模型/动画 ========================

    @Override
    public boolean applyModel(Object adyeshachEntity, String modelId, double scale) {
        if (!available || adyeshachEntity == null || modelId == null || modelId.isBlank()) {
            return false;
        }
        return broadcastApplyModel(adyeshachEntity, modelId, scale);
    }

    @Override
    public boolean applyModelForPlayer(Player player, Object adyeshachEntity, String modelId, double scale) {
        if (!available || player == null || adyeshachEntity == null || modelId == null || modelId.isBlank()) {
            return false;
        }
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: applyModelForPlayer - 无法获取实体 UUID，回退到广播路径");
            return broadcastApplyModel(adyeshachEntity, modelId, scale);
        }
        if (debug) {
            xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: applyModelForPlayer UUID=" + entityUUID + " model=" + modelId + " scale=" + scale);
        }
        try {
            ArcartXNetworkSender.INSTANCE.sendSetEntityModel(player, entityUUID, modelId, scale);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: applyModelForPlayer 失败 - " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean applyAnimation(Object adyeshachEntity, String animation, double speed, int transitionTime, long keepTime) {
        if (!available || adyeshachEntity == null || animation == null || animation.isBlank()) {
            return false;
        }
        return broadcastApplyAnimation(adyeshachEntity, animation, speed, transitionTime, keepTime);
    }

    @Override
    public boolean applyAnimationForPlayer(Player player, Object adyeshachEntity, String animation, double speed, int transitionTime, long keepTime) {
        if (!available || player == null || adyeshachEntity == null || animation == null || animation.isBlank()) {
            return false;
        }
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            return broadcastApplyAnimation(adyeshachEntity, animation, speed, transitionTime, keepTime);
        }
        try {
            ArcartXNetworkSender.INSTANCE.sendSetEntityAnimation(player, entityUUID, animation, speed, transitionTime, keepTime);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: applyAnimationForPlayer 失败 - " + exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean applyDefaultState(Object adyeshachEntity, String state, String animName) {
        if (!available || adyeshachEntity == null || state == null || animName == null) {
            return false;
        }
        return broadcastApplyDefaultState(adyeshachEntity, state, animName);
    }

    @Override
    public boolean applyDefaultStateForPlayer(Player player, Object adyeshachEntity, String state, String animName) {
        if (!available || player == null || adyeshachEntity == null || state == null || animName == null) {
            return false;
        }
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            return broadcastApplyDefaultState(adyeshachEntity, state, animName);
        }
        try {
            // keepTime=-1 表示无限循环，模拟默认状态
            ArcartXNetworkSender.INSTANCE.sendSetEntityAnimation(player, entityUUID, animName, 1.0, 0, -1L);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: applyDefaultStateForPlayer 失败 - " + exception.getMessage());
            return false;
        }
    }

    /** 公共管理器集合（持久 + 临时；去重防缺临时管理器时重复扫描）。 */
    private List<Manager> publicManagers() {
        if (publicTemporaryManager != null && publicTemporaryManager != publicManager) {
            return List.of(publicManager, publicTemporaryManager);
        }
        return List.of(publicManager);
    }

    // ======================== 公共临时 NPC（实例化场景） ========================

    @Override
    public Object spawnPublicTemporaryNpc(String entityType, Location location, String npcId, String displayName) {
        if (!available || publicTemporaryManager == null || location == null) {
            return null;
        }
        try {
            EntityTypes type;
            try {
                type = entityType == null ? EntityTypes.VILLAGER : EntityTypes.valueOf(entityType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                type = EntityTypes.VILLAGER;
            }
            EntityInstance entity = publicTemporaryManager.create(type, location);
            if (entity == null) {
                return null;
            }
            if (npcId != null && !npcId.isBlank()) {
                try {
                    entity.setId(npcId);
                } catch (RuntimeException exception) {
                    logWarning("set-id", entity, exception);
                }
            }
            if (displayName != null && !displayName.isBlank()) {
                try {
                    entity.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
                    entity.setCustomNameVisible(true);
                } catch (RuntimeException exception) {
                    logWarning("set-name", entity, exception);
                }
            }
            try {
                entity.setVisibleAfterLoaded(true);
            } catch (RuntimeException ignored) {
            }
            return entity;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: spawnPublicTemporaryNpc 失败 - " + exception.getMessage());
            return null;
        }
    }

    @Override
    public boolean removeEntity(Object adyeshachEntity) {
        if (!(adyeshachEntity instanceof EntityInstance entity)) {
            return false;
        }
        return deleteEntity(entity);
    }

    @Override
    public boolean registerInteractHandler(BiConsumer<Player, Object> handler) {
        unregisterInteractHandler();
        if (!available || handler == null) {
            return false;
        }
        Plugin adyeshach = plugin.getServer().getPluginManager().getPlugin("Adyeshach");
        if (adyeshach == null) {
            return false;
        }
        try {
            ClassLoader classLoader = adyeshach.getClass().getClassLoader();
            Class<?> eventClass = Class.forName(
                "ink.ptms.adyeshach.api.event.AdyeshachEntityInteractEvent", true, classLoader);
            if (!Event.class.isAssignableFrom(eventClass)) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: AdyeshachEntityInteractEvent 不是 Bukkit Event，无法注册监听器。");
                return false;
            }
            adyInteractGetEntityMethod = eventClass.getMethod("getEntity");
            adyInteractGetPlayerMethod = eventClass.getMethod("getPlayer");

            adyeshachInteractHandler = handler;
            adyeshachInteractListener = new Listener() {};

            @SuppressWarnings("unchecked")
            Class<? extends Event> typed = (Class<? extends Event>) eventClass;
            EventExecutor executor = (listener, event) -> dispatchAdyeshachInteractEvent(event);
            plugin.getServer().getPluginManager().registerEvent(
                typed,
                adyeshachInteractListener,
                EventPriority.NORMAL,
                executor,
                plugin
            );
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: 已注册 AdyeshachEntityInteractEvent 监听器。");
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 注册 AdyeshachEntityInteractEvent 监听器失败 - " + exception.getMessage());
            unregisterInteractHandler();
            return false;
        }
    }

    @Override
    public void unregisterInteractHandler() {
        if (adyeshachInteractListener != null) {
            xuanmo.arcartxsuite.util.ListenerUnregister.safeUnregisterAll(adyeshachInteractListener, "AdyeshachNpcBridge");
            adyeshachInteractListener = null;
        }
        adyeshachInteractHandler = null;
        adyInteractGetEntityMethod = null;
        adyInteractGetPlayerMethod = null;
    }

    private void dispatchAdyeshachInteractEvent(Event event) {
        BiConsumer<Player, Object> handler = adyeshachInteractHandler;
        if (handler == null || event == null) {
            return;
        }
        try {
            Object entity = adyInteractGetEntityMethod.invoke(event);
            Object playerObj = adyInteractGetPlayerMethod.invoke(event);
            if (!(playerObj instanceof Player player) || entity == null) {
                return;
            }
            handler.accept(player, entity);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 处理 AdyeshachEntityInteractEvent 失败 - " + exception.getMessage());
            }
        }
    }

    // ======================== 内部工具方法 ========================

    private boolean broadcastApplyModel(Object adyeshachEntity, String modelId, double scale) {
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 无法获取 Adyeshach 实体 UUID，无法广播模型。");
            return false;
        }
        List<Player> viewers = collectVisibleViewers(adyeshachEntity);
        if (viewers.isEmpty()) {
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: broadcastApplyModel UUID=" + entityUUID + " 当前没有看见该实体的在线玩家，跳过广播。");
            }
            return true;
        }
        int success = 0;
        for (Player player : viewers) {
            try {
                ArcartXNetworkSender.INSTANCE.sendSetEntityModel(player, entityUUID, modelId, scale);
                success++;
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: sendSetEntityModel(" + player.getName() + ") 失败 - " + exception.getMessage());
            }
        }
        if (debug) {
            xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: broadcastApplyModel UUID=" + entityUUID + " model=" + modelId + " success=" + success + "/" + viewers.size());
        }
        return success > 0;
    }

    private boolean broadcastApplyAnimation(Object adyeshachEntity, String animation, double speed, int transitionTime, long keepTime) {
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 无法获取 Adyeshach 实体 UUID，无法广播动画。");
            return false;
        }
        List<Player> viewers = collectVisibleViewers(adyeshachEntity);
        if (viewers.isEmpty()) {
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: broadcastApplyAnimation UUID=" + entityUUID + " 当前没有看见该实体的在线玩家，跳过广播。");
            }
            return true;
        }
        int success = 0;
        for (Player player : viewers) {
            try {
                ArcartXNetworkSender.INSTANCE.sendSetEntityAnimation(player, entityUUID, animation, speed, transitionTime, keepTime);
                success++;
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: sendSetEntityAnimation(" + player.getName() + ") 失败 - " + exception.getMessage());
            }
        }
        if (debug) {
            xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: broadcastApplyAnimation UUID=" + entityUUID + " animation=" + animation + " success=" + success + "/" + viewers.size());
        }
        return success > 0;
    }

    private boolean broadcastApplyDefaultState(Object adyeshachEntity, String state, String animName) {
        UUID entityUUID = getAdyEntityUUID(adyeshachEntity);
        if (entityUUID == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 无法获取 Adyeshach 实体 UUID，无法广播默认状态。");
            return false;
        }
        List<Player> viewers = collectVisibleViewers(adyeshachEntity);
        if (viewers.isEmpty()) {
            if (debug) {
                xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: broadcastApplyDefaultState UUID=" + entityUUID + " 当前没有看见该实体的在线玩家，跳过广播。");
            }
            return true;
        }
        int success = 0;
        for (Player player : viewers) {
            try {
                ArcartXNetworkSender.INSTANCE.sendSetEntityAnimation(player, entityUUID, animName, 1.0, 0, -1L);
                success++;
            } catch (RuntimeException exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: sendSetEntityAnimation(default state, " + player.getName() + ") 失败 - " + exception.getMessage());
            }
        }
        if (debug) {
            xuanmo.arcartxsuite.module.AxsLog.logger().info("AdyeshachNpcBridge: broadcastApplyDefaultState UUID=" + entityUUID + " state=" + state + " anim=" + animName + " success=" + success + "/" + viewers.size());
        }
        return success > 0;
    }

    private xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc resolveNearbyNpc(
        EntityInstance entity,
        Player player,
        World playerWorld,
        Location playerLocation,
        double rangeSquared
    ) {
        if (entity == null || entity.isDeleted() || entity.getInvalid()) {
            return null;
        }
        Location location = entity.getLocation();
        if (location == null || location.getWorld() == null || !playerWorld.equals(location.getWorld())) {
            return null;
        }
        double distanceSquared = playerLocation.distanceSquared(location);
        if (distanceSquared > rangeSquared || !entity.isVisibleViewer(player)) {
            return null;
        }
        return toNearbyNpc(entity, location.clone(), distanceSquared);
    }

    private AdyeshachNearbyNpc toNearbyNpc(EntityInstance entity, Location point, double distanceSquared) {
        String npcId = entity.getId();
        if (npcId == null || npcId.isBlank()) {
            return null;
        }
        String displayName = entity.getDisplayName();
        String customName = entity.getCustomName();
        String label = firstNonBlank(displayName, customName, npcId);
        Object conversationEntity = entity.getV2();
        if (conversationEntity == null) {
            conversationEntity = entity;
        }
        return new AdyeshachNearbyNpc(npcId, label, point, entity, conversationEntity, distanceSquared);
    }

    private UUID getAdyEntityUUID(Object adyeshachEntity) {
        if (!(adyeshachEntity instanceof EntityInstance entity)) {
            return null;
        }
        try {
            UUID uuid = entity.getNormalizeUniqueId();
            if (uuid != null) {
                return uuid;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            String uniqueId = entity.getUniqueId();
            if (uniqueId != null && !uniqueId.isBlank()) {
                if (uniqueId.contains("-")) {
                    return UUID.fromString(uniqueId);
                } else if (uniqueId.length() == 32) {
                    String formatted = uniqueId.substring(0, 8) + "-" + uniqueId.substring(8, 12) + "-"
                        + uniqueId.substring(12, 16) + "-" + uniqueId.substring(16, 20) + "-" + uniqueId.substring(20);
                    return UUID.fromString(formatted);
                }
            }
        } catch (RuntimeException ignored) {
        }
        xuanmo.arcartxsuite.module.AxsLog.logger().warning("AdyeshachNpcBridge: 无法获取实体 UUID (class=" + adyeshachEntity.getClass().getName() + ")");
        return null;
    }

    private List<Player> collectVisibleViewers(Object adyeshachEntity) {
        List<Player> viewers = new ArrayList<>();
        if (!(adyeshachEntity instanceof EntityInstance entity)) {
            return viewers;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                if (entity.isVisibleViewer(player)) {
                    viewers.add(player);
                }
            } catch (RuntimeException ignored) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    private void logWarning(String action, Object target, Exception exception) {
        xuanmo.arcartxsuite.module.AxsLog.logger().warning(
            "Adyeshach NPC 操作失败(" + action + "): " + exception.getClass().getSimpleName()
                + (exception.getMessage() != null ? ": " + exception.getMessage() : "")
                + " | target=" + (target == null ? "null" : target.getClass().getName())
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null) {
                String normalized = value.trim();
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }
        return "";
    }
}
