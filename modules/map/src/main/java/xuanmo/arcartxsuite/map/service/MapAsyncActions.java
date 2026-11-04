package xuanmo.arcartxsuite.map.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.AnchorDefinition;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapPin;
import xuanmo.arcartxsuite.map.model.MapWaypoint;
import xuanmo.arcartxsuite.map.storage.MapUnlockTransaction;

/** Server-thread validation and settlement around asynchronous repository transactions. */
final class MapAsyncActions {
    private final MapService service;
    MapAsyncActions(MapService service) { this.service = service; }

    private CompletableFuture<MapOperationResult> failure(String key, Object... args) {
        return CompletableFuture.completedFuture(MapOperationResult.failure(service.message(key, args)));
    }

    CompletableFuture<MapOperationResult> unlock(Player player, String id, boolean free) {
        AnchorDefinition anchor = service.configuration.anchor(id);
        if (anchor == null) return failure("player.anchor-not-found", id);
        if (service.isAnchorUnlocked(player, anchor)) return failure("player.anchor-already-unlocked");
        if (!free) {
            MapOperationResult check = checkUnlock(player, anchor);
            if (!check.success()) return CompletableFuture.completedFuture(check);
        }
        UUID uuid = player.getUniqueId();
        long time = System.currentTimeMillis();
        return MapUnlockTransaction.execute(service.storage,
            () -> service.repository.tryUnlockAnchor(uuid, anchor.id(), time),
            () -> service.sessionCurrent(player),
            () -> {
                if (!free) {
                    MapOperationResult check = checkUnlock(player, anchor);
                    if (!check.success()) return check;
                    // Re-prepare after the SQL gap: never overwrite inventory with a pre-wait snapshot.
                    var items = service.charge.prepareItemConsumption(player, anchor.unlockItems());
                    if (!items.success()) return MapOperationResult.failure(items.message());
                    var money = service.charge.chargeCurrencies(player, anchor.unlockCurrencies());
                    if (!money.success()) return MapOperationResult.failure(money.message());
                    MapOperationResult applied;
                    try { applied = service.charge.applyItemConsumption(player, items); }
                    catch (RuntimeException error) {
                        service.charge.rollbackCurrencies(player, money.successfulCharges());
                        throw error;
                    }
                    if (!applied.success()) {
                        service.charge.rollbackCurrencies(player, money.successfulCharges());
                        return applied;
                    }
                }
                var data = service.cachedData(uuid);
                var unlocked = new LinkedHashSet<>(data.unlockedAnchors());
                unlocked.add(anchor.id());
                service.cacheData(uuid, new MapService.CachedPlayerMapData(java.util.Set.copyOf(unlocked), data.waypoints(), data.pins()));
                try { service.pushAnchorTerrain(player, anchor); }
                catch (RuntimeException error) { service.logger.warning("[Map] 解锁已结算，地形推送失败: " + error); }
                return MapOperationResult.success(service.message("player.anchor-unlocked", anchor.displayName()));
            },
            () -> { service.repository.removeUnlock(uuid, anchor.id()); return null; },
            MapOperationResult.failure(service.message("player.anchor-already-unlocked")),
            MapOperationResult.failure(service.message("player.offline")));
    }

    private MapOperationResult checkUnlock(Player player, AnchorDefinition anchor) {
        MapOperationResult access = service.validateAnchorAccess(player, anchor);
        if (!access.success()) return access;
        MapOperationResult range = service.validateUnlockRange(player, anchor);
        if (!range.success()) return range;
        var items = service.charge.prepareItemConsumption(player, anchor.unlockItems());
        return items.success() ? MapOperationResult.success("") : MapOperationResult.failure(items.message());
    }

    CompletableFuture<MapOperationResult> createWaypoint(Player player) {
        if (!service.configuration.waypoints().enabled()) return failure("player.custom-waypoints-disabled");
        String world = MapService.normalizeId(player.getWorld().getName());
        if (!service.configuration.worlds().containsKey(world)) return failure("player.waypoint-world-not-configured");
        long count = service.loadWaypoints(player).stream().filter(p -> MapService.normalizeId(p.world()).equals(world)).count();
        int limit = service.resolveWaypointLimit(player);
        if (count >= limit) return failure("player.waypoint-limit-reached", limit);
        Location position = player.getLocation();
        long now = System.currentTimeMillis();
        String name = service.configuration.waypoints().waypointNamePrefix() + "-" + (count + 1);
        MapWaypoint point = new MapWaypoint(newId(false), name, "", world, position.getX(), position.getY(), position.getZ(), now, now);
        return insertWaypoint(player, point, limit, service.message("player.waypoint-created", name));
    }

    CompletableFuture<MapOperationResult> createPin(Player player, double x, double z, String world) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return failure("player.waypoint-save-failed");
        if (!service.configuration.waypoints().enabled()) return failure("player.custom-waypoints-disabled");
        String id = MapService.normalizeId(world);
        if (!service.configuration.worlds().containsKey(id)) return failure("player.waypoint-world-not-configured");
        long count = service.loadPins(player).stream().filter(p -> MapService.normalizeId(p.world()).equals(id)).count();
        int limit = service.resolveWaypointLimit(player);
        if (count >= limit) return failure("player.waypoint-limit-reached", limit);
        long now = System.currentTimeMillis();
        String name = service.configuration.waypoints().autoNamePrefix() + "-" + (count + 1);
        return insertPin(player, new MapPin(newId(true), name, "", id, x, z, now, now), limit, service.message("player.pin-created", name));
    }

    String newId(boolean pin) {
        return MapService.normalizeId(service.configuration.waypoints().idPrefix() + (pin ? "pin-" : "") + UUID.randomUUID().toString().replace("-", ""));
    }

    CompletableFuture<MapOperationResult> insertWaypoint(Player player, MapWaypoint point, int limit, String message) {
        UUID uuid = player.getUniqueId();
        return write(player, () -> service.repository.createWaypointIfUnderWorldLimit(uuid, point, point.world(), limit),
            () -> {
                upsertWaypointCache(uuid, point);
                service.state(player).selectWorld(point.world());
                service.state(player).selectWaypoint(point.waypointId());
            }, message, "player.waypoint-limit-reached", limit);
    }

    CompletableFuture<MapOperationResult> insertPin(Player player, MapPin point, int limit, String message) {
        UUID uuid = player.getUniqueId();
        return write(player, () -> service.repository.createPinIfUnderWorldLimit(uuid, point, point.world(), limit),
            () -> {
                upsertPinCache(uuid, point);
                service.state(player).selectWorld(point.world());
                service.state(player).selectPin(point.pinId());
            }, message, "player.waypoint-limit-reached", limit);
    }

    CompletableFuture<MapOperationResult> deleteWaypoint(Player player, String id) {
        MapWaypoint point = service.findWaypoint(player, id);
        if (point == null) return failure("player.waypoint-not-found", id);
        UUID uuid = player.getUniqueId();
        return write(player, () -> service.repository.deleteWaypoint(uuid, point.waypointId()), () -> {
            var data = service.cachedData(uuid);
            service.cacheData(uuid, new MapService.CachedPlayerMapData(data.unlockedAnchors(),
                data.waypoints().stream().filter(p -> !p.waypointId().equals(point.waypointId())).toList(), data.pins()));
            service.clearDeletedPoint(player, false, point.waypointId());
        }, service.message("player.waypoint-deleted", point.name()), "player.waypoint-delete-failed");
    }

    CompletableFuture<MapOperationResult> deletePin(Player player, String id) {
        MapPin point = service.findPin(player, id);
        if (point == null) return failure("player.pin-not-found", id);
        UUID uuid = player.getUniqueId();
        return write(player, () -> service.repository.deletePin(uuid, point.pinId()), () -> {
            var data = service.cachedData(uuid);
            service.cacheData(uuid, new MapService.CachedPlayerMapData(data.unlockedAnchors(), data.waypoints(),
                data.pins().stream().filter(p -> !p.pinId().equals(point.pinId())).toList()));
            service.clearDeletedPoint(player, true, point.pinId());
        }, service.message("player.pin-deleted", point.name()), "player.waypoint-delete-failed");
    }

    CompletableFuture<MapOperationResult> updateWaypoint(Player player, String id, String name, String description) {
        MapWaypoint point = service.findWaypoint(player, id);
        if (point == null) return failure("player.waypoint-not-found", id);
        String title = name == null || name.isBlank() ? point.name() : MapService.clipText(name, 64);
        MapWaypoint updated = new MapWaypoint(point.waypointId(), title, MapService.clipText(description == null ? "" : description, 256),
            point.world(), point.x(), point.y(), point.z(), point.createdAt(), System.currentTimeMillis());
        UUID uuid = player.getUniqueId();
        return write(player, () -> { service.repository.upsertWaypoint(uuid, updated); return true; },
            () -> upsertWaypointCache(uuid, updated), service.message("player.waypoint-updated", title), "player.waypoint-save-failed");
    }

    CompletableFuture<MapOperationResult> updatePin(Player player, String id, String name, String description) {
        MapPin point = service.findPin(player, id);
        if (point == null) return failure("player.pin-not-found", id);
        String title = name == null || name.isBlank() ? point.name() : MapService.clipText(name, 64);
        MapPin updated = new MapPin(point.pinId(), title, MapService.clipText(description == null ? "" : description, 256),
            point.world(), point.x(), point.z(), point.createdAt(), System.currentTimeMillis());
        UUID uuid = player.getUniqueId();
        return write(player, () -> { service.repository.upsertPin(uuid, updated); return true; },
            () -> upsertPinCache(uuid, updated), service.message("player.pin-updated", title), "player.waypoint-save-failed");
    }

    private CompletableFuture<MapOperationResult> write(Player player, Callable<Boolean> sql, Runnable accepted,
        String message, String rejected, Object... arguments) {
        return service.storage.query(sql).thenApply(success -> {
            if (!success) return MapOperationResult.failure(service.message(rejected, arguments));
            if (service.sessionCurrent(player)) {
                try { accepted.run(); }
                catch (RuntimeException error) { service.logger.warning("[Map] 写入已提交，视图更新失败: " + error); }
            }
            return MapOperationResult.success(message);
        });
    }

    private void upsertWaypointCache(UUID uuid, MapWaypoint point) {
        var data = service.cachedData(uuid);
        var points = new ArrayList<>(data.waypoints());
        points.removeIf(p -> p.waypointId().equals(point.waypointId()));
        points.add(point);
        points.sort(Comparator.comparingLong(MapWaypoint::createdAt).thenComparing(MapWaypoint::waypointId));
        service.cacheData(uuid, new MapService.CachedPlayerMapData(data.unlockedAnchors(), List.copyOf(points), data.pins()));
    }

    private void upsertPinCache(UUID uuid, MapPin point) {
        var data = service.cachedData(uuid);
        var points = new ArrayList<>(data.pins());
        points.removeIf(p -> p.pinId().equals(point.pinId()));
        points.add(point);
        points.sort(Comparator.comparingLong(MapPin::createdAt).thenComparing(MapPin::pinId));
        service.cacheData(uuid, new MapService.CachedPlayerMapData(data.unlockedAnchors(), data.waypoints(), List.copyOf(points)));
    }
}
