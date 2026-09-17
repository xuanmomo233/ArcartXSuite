package xuanmo.arcartxsuite.bridge;

import ink.ptms.adyeshach.api.AdyeshachAPI;
import ink.ptms.adyeshach.common.entity.EntityInstance;
import ink.ptms.adyeshach.common.entity.EntityTypes;
import ink.ptms.adyeshach.common.entity.manager.Manager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
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

/**
 * {@link xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI} 的默认实现，
 * 桥接 Adyeshach NPC 插件，提供 NPC 创建、查找、可见性控制等能力。
 * <p>
 * Adyeshach 为可选依赖，运行时通过 {@link #initialize()} 懒检测可用性。
 */
public final class AdyeshachNpcBridge implements xuanmo.arcartxsuite.api.bridge.AdyeshachNpcBridgeAPI {

    private final JavaPlugin plugin;
    private final ConcurrentMap<String, EntityInstance> privateMarkerEntities = new ConcurrentHashMap<>();

    private boolean available;
    private boolean debug;
    private Manager publicManager;

    private Listener adyeshachVisibleListener;
    private BiConsumer<Player, Object> adyeshachVisibleHandler;
    private Method adyVisibleGetEntityMethod;
    private Method adyVisibleGetViewerMethod;
    private Method adyVisibleGetVisibleMethod;

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
        clearAllPrivateMarkers();
        available = false;
        publicManager = null;
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
        for (EntityInstance entity : publicManager.getEntities()) {
            try {
                xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc nearbyNpc = resolveNearbyNpc(entity, player, playerWorld, playerLocation, rangeSquared);
                if (nearbyNpc != null) {
                    result.add(nearbyNpc);
                }
            } catch (RuntimeException exception) {
                logWarning("scan-entity", entity, exception);
            }
        }

        result.sort(
            Comparator.comparingDouble(xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc::distanceSquared)
                .thenComparing(nearbyNpc -> nearbyNpc.label().toLowerCase(Locale.ROOT))
                .thenComparing(nearbyNpc -> nearbyNpc.npcId().toLowerCase(Locale.ROOT))
        );
        return List.copyOf(result);
    }

    @Override
    public Optional<Object> findByName(String name) {
        if (!available || name == null || name.isBlank()) {
            return Optional.empty();
        }
        String lower = name.strip().toLowerCase(Locale.ROOT);
        for (EntityInstance entity : publicManager.getEntities()) {
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
        if (!entity.isVisibleViewer(player)) {
            return null;
        }
        Location location = entity.getLocation();
        if (location == null || location.getWorld() == null || !playerWorld.equals(location.getWorld())) {
            return null;
        }
        double distanceSquared = playerLocation.distanceSquared(location);
        if (distanceSquared > rangeSquared) {
            return null;
        }
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
        return new xuanmo.arcartxsuite.api.bridge.AdyeshachNearbyNpc(npcId, label, location.clone(), entity, conversationEntity, distanceSquared);
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
