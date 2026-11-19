package xuanmo.arcartxsuite.map.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.AnchorDefinition;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapPin;
import xuanmo.arcartxsuite.map.model.MapTeleportSession;
import xuanmo.arcartxsuite.map.model.MapWaypoint;
import xuanmo.arcartxsuite.map.storage.MapUnlockTransaction;

/** Server-thread validation and settlement around asynchronous repository transactions. */
final class MapAsyncActions {
    private static final NamespacedKey UNLOCK_RECEIPT = new NamespacedKey("arcartxsuite", "map_unlock_cost");
    private final MapService service;
    MapAsyncActions(MapService service) { this.service = service; }

    private CompletableFuture<MapOperationResult> failure(String key, Object... args) {
        return CompletableFuture.completedFuture(MapOperationResult.failure(service.message(key, args)));
    }

    CompletableFuture<MapOperationResult> unlock(Player player, String id, boolean free) {
        return unlock(player, id, free, false);
    }

    CompletableFuture<MapOperationResult> unlock(Player player, String id, boolean free, boolean alreadyOwnedIsSuccess) {
        return unlock(player, id, free, alreadyOwnedIsSuccess, free ? null : MapTeleportSession.capture(player));
    }

    CompletableFuture<MapOperationResult> unlock(MapTeleportSession session, String id) {
        return unlock(session.player(), id, false, false, session);
    }

    private CompletableFuture<MapOperationResult> unlock(Player player, String id, boolean free,
            boolean alreadyOwnedIsSuccess, MapTeleportSession session) {
        if (!free && !service.canUseMapSession(session)) return failure("common.no-permission");
        AnchorDefinition anchor = service.configuration.anchor(id);
        if (anchor == null) return failure("player.anchor-not-found", id);
        var alreadyOwned = new MapOperationResult(alreadyOwnedIsSuccess, service.message("player.anchor-already-unlocked"));
        if (service.isAnchorUnlocked(player, anchor)) return CompletableFuture.completedFuture(alreadyOwned);
        if (!free) {
            MapOperationResult access = checkUnlockAccess(player, anchor);
            if (!access.success()) return CompletableFuture.completedFuture(access);
        }
        UUID uuid = player.getUniqueId();
        var unlocked = MapOperationResult.success(service.message("player.anchor-unlocked", anchor.displayName()));
        Callable<MapOperationResult> existing = () -> service.repository.isAnchorUnlockConfirmed(uuid, anchor.id())
            ? alreadyOwned : service.repository.pendingAnchorReservation(uuid, anchor.id())
                .map(this::pendingUnlock).orElseGet(() -> MapOperationResult.failure(service.message("player.action-busy")));
        CompletableFuture<MapOperationResult> result;
        if (free || (anchor.unlockItems().isEmpty() && anchor.unlockCurrencies().isEmpty())) {
            result = service.storage.write(() -> service.repository.tryUnlockAnchor(uuid, anchor.id(), System.currentTimeMillis())
                ? unlocked : existing.call());
        } else {
            var initialItems = service.charge.prepareItemConsumption(player, anchor.unlockItems());
            if (!initialItems.success()) return CompletableFuture.completedFuture(MapOperationResult.failure(initialItems.message()));
            boolean consumesItems = initialItems.hasChanges();
            UUID reservation = UUID.randomUUID();
            String source = service.serverId();
            var pending = pendingUnlock(reservation);
            var review = new java.util.concurrent.atomic.AtomicBoolean();
            var itemSave = new java.util.concurrent.atomic.AtomicReference<Path>();
            var publicationDeferred = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.function.BooleanSupplier eligible = () -> service.canUseMapSession(session)
                && (anchor.permission().isBlank() || player.hasPermission(anchor.permission()));
            result = MapUnlockTransaction.execute(service.storage,
                () -> service.repository.reserveAnchorUnlock(uuid, anchor.id(), System.currentTimeMillis(), reservation, source),
                eligible,
                () -> {
                    MapOperationResult access = checkUnlockAccess(player, anchor);
                    if (!access.success()) return access;
                    var items = consumesItems && !Arrays.equals(initialItems.originalContents(), player.getInventory().getStorageContents())
                        ? service.charge.prepareItemConsumption(player, anchor.unlockItems()) : initialItems;
                    if (!items.success()) return MapOperationResult.failure(items.message());
                    review.set(true);
                    var money = service.charge.chargeCurrencies(player, anchor.unlockCurrencies(), eligible);
                    if (!money.success()) {
                        review.set(money.reviewRequired());
                        return money.reviewRequired() ? pending : MapOperationResult.failure(money.message());
                    }
                    if (!eligible.getAsBoolean()) {
                        review.set(!service.charge.rollbackCurrencies(player, money.successfulCharges()));
                        return review.get() ? pending : MapOperationResult.failure(service.message("common.no-permission"));
                    }
                    if (consumesItems && !money.successfulCharges().isEmpty()
                        && !Arrays.equals(items.originalContents(), player.getInventory().getStorageContents())) {
                        review.set(!service.charge.rollbackCurrencies(player, money.successfulCharges()));
                        return review.get() ? pending : MapOperationResult.failure(service.message("player.anchor-unlock-items-changed"));
                    }
                    if (!service.charge.applyItemConsumption(player, items).success()) return pending;
                    if (items.hasChanges()) {
                        try {
                            itemSave.set(Bukkit.getWorlds().get(0).getWorldFolder().toPath()
                                .resolve("playerdata").resolve(uuid + ".dat"));
                            PlayerDataReceipt.save(player, UNLOCK_RECEIPT, reservation.toString());
                        } catch (RuntimeException | LinkageError failure) {
                            service.logger.log(Level.WARNING, "[Map] 锚点材料存档待核对: " + reservation, failure);
                            return pending;
                        }
                    }
                    return unlocked;
                },
                () -> {
                    Path file = itemSave.get();
                    if (file != null) {
                        try {
                            if (!reservation.toString().equals(PlayerDataReceipt.read(file, UNLOCK_RECEIPT.toString()))) {
                                publicationDeferred.set(true);
                                service.logger.warning("[Map] 未找到锚点材料存档回执: " + reservation);
                                return null;
                            }
                        } catch (IOException | RuntimeException failure) {
                            publicationDeferred.set(true);
                            service.logger.log(Level.WARNING, "[Map] 锚点材料存档回执待核对: " + reservation, failure);
                            return null;
                        }
                    }
                    try {
                        if (!service.repository.confirmAnchorUnlock(uuid, anchor.id(), System.currentTimeMillis(), reservation)) {
                            throw new SQLException("锚点解锁预约未能确认: " + reservation);
                        }
                    } catch (SQLException failure) {
                        service.logger.log(Level.WARNING, "[Map] 锚点扣费完成，解锁记录待核对: player=" + uuid
                            + " anchor=" + anchor.id() + " request=" + reservation, failure);
                        throw failure;
                    }
                    return null;
                },
                () -> {
                    if (review.get()) {
                        service.logger.warning("[Map] 锚点解锁待核对: player=" + uuid + " anchor=" + anchor.id() + " request=" + reservation);
                    } else {
                        service.repository.cancelAnchorUnlock(uuid, anchor.id(), reservation);
                    }
                    return null;
                }, existing, MapOperationResult.failure(service.message("common.no-permission")))
                .thenApply(outcome -> publicationDeferred.get() ? pending : outcome);
        }
        return result.thenApply(outcome -> {
            // Standalone writes may finish on a worker during shutdown.
            if (service.storage.isClosing() || (outcome != unlocked && outcome != alreadyOwned) || !service.sessionCurrent(player)) return outcome;
            var data = service.cachedData(uuid);
            var anchors = new LinkedHashSet<>(data.unlockedAnchors());
            if (anchors.add(anchor.id())) {
                service.cacheData(uuid, new MapService.CachedPlayerMapData(java.util.Set.copyOf(anchors), data.waypoints(), data.pins()));
            }
            return outcome;
        });
    }

    private MapOperationResult pendingUnlock(UUID reservation) {
        return MapOperationResult.failure(service.message("player.anchor-unlock-pending", reservation));
    }

    private MapOperationResult checkUnlockAccess(Player player, AnchorDefinition anchor) {
        MapOperationResult access = service.validateAnchorAccess(player, anchor);
        if (!access.success()) return access;
        return service.validateUnlockRange(player, anchor);
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
        return service.storage.write(sql).thenApply(success -> {
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
