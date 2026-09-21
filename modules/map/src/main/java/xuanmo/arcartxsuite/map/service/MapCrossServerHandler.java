package xuanmo.arcartxsuite.map.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.AnchorDefinition;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.WorldDefinition;
import xuanmo.arcartxsuite.map.crossserver.MapCrossServerService;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapPlayerViewState;

/**
 * 跨服地图回调实现（{@link MapCrossServerService.Handler}），自 {@link MapService} 内部类提取。
 * <p>所有回调均在主线程执行（宿主投递保证）。方向分两侧：
 * 源服 = 发起浏览/传送的服；目标服 = 提供世界/接受落地的服。
 * <p>通过持有 {@link MapService} 引用回调其 package-private 成员，不改变对外 API。
 */
    // ─── 跨服地图回调（MapCrossServerService.Handler）───────────────
    //
    // 所有回调均在主线程执行（宿主投递保证）。方向分两侧：
    //   源服 = 发起浏览/传送的服；目标服 = 提供世界/接受落地的服。

    final class MapCrossServerHandler implements MapCrossServerService.Handler {

    private final MapService service;

    MapCrossServerHandler(MapService service) {
        this.service = service;
    }

        /**
         * 本机对外广播的共享世界目录：worlds/<world>.yml 中 cross-server.share=true
         * 的世界，含显示名/维度/中心点/预加载覆盖/锚点摘要。
         */
        @Override
        public List<MapCrossServerService.SharedWorldInfo> sharedWorldCatalog() {
            List<MapCrossServerService.SharedWorldInfo> out = new ArrayList<>();
            for (WorldDefinition world : service.configuration.worlds().values()) {
                if (!world.crossShare()) {
                    continue;
                }
                Location center = service.resolveWorldCenter(world);
                int preloadVersion = world.hasPreload() ? service.effectivePreloadVersion(world) : 0;
                List<MapCrossServerService.SharedAnchor> sharedAnchors = new ArrayList<>();
                for (AnchorDefinition anchor : service.configuration.anchors().values()) {
                    if (!MapService.normalizeId(anchor.worldId()).equals(world.id())) {
                        continue;
                    }
                    sharedAnchors.add(new MapCrossServerService.SharedAnchor(
                        anchor.id(),
                        anchor.displayName(),
                        anchor.description(),
                        anchor.x(),
                        anchor.y(),
                        anchor.z(),
                        anchor.sortOrder(),
                        service.formatTeleportCostText(anchor)
                    ));
                }
                out.add(new MapCrossServerService.SharedWorldInfo(
                    world.id(),
                    service.resolveDisplayName(world),
                    MapService.resolveDimension(world),
                    center == null ? 0 : center.getX(),
                    center == null ? 0 : center.getZ(),
                    preloadVersion,
                    preloadVersion > 0
                        ? MapCrossServerService.chunkCsv(
                            service.preloadChunkCoords(world).stream()
                                .map(c -> new long[]{c[0], c[1]})
                                .toList())
                        : "",
                    world.crossArrive(),
                    sharedAnchors
                ));
            }
            return out;
        }

        /**
         * 远程目录变化：刷新正在浏览该节点世界的玩家的菜单（选中世界被摘除时
         * 快照构建会自动回退到玩家当前世界）。
         */
        @Override
        public void remoteCatalogChanged(String nodeId) {
            String prefix = nodeId + "/";
            for (Player player : Bukkit.getOnlinePlayers()) {
                MapPlayerViewState state = service.viewStates.get(player.getUniqueId());
                if (state != null && state.selectedWorldId().startsWith(prefix)) {
                    service.sync(player, false);
                }
            }
        }

        /**
         * 目标服侧：校验世界可共享中继并打包票据。
         * 只接受 crossShare=true 且有预加载的世界；请求块 ⊆ 预加载覆盖。
         */
        @Override
        public MapCrossServerService.TerrainRelayTicket prepareTerrainRelay(String worldId, List<long[]> chunks) {
            WorldDefinition world = service.configuration.worlds().get(worldId);
            if (world == null || !world.crossShare() || !world.hasPreload()) {
                return null;
            }
            World bukkitWorld = MapService.resolveWorld(world.id());
            if (bukkitWorld == null) {
                return null;
            }
            Set<Long> allowed = new HashSet<>();
            for (int[] c : service.preloadChunkCoords(world)) {
                allowed.add(((long) c[0] << 32) | (c[1] & 0xffffffffL));
            }
            List<Long> accepted = new ArrayList<>(chunks.size());
            for (long[] c : chunks) {
                if (c == null || c.length < 2) {
                    continue;
                }
                long packed = ((long) c[0] << 32) | (c[1] & 0xffffffffL);
                if (allowed.contains(packed)) {
                    accepted.add(packed);
                }
            }
            return accepted.isEmpty()
                ? null
                : new MapCrossServerService.TerrainRelayTicket(bukkitWorld, accepted, service.effectivePreloadVersion(world));
        }

        /**
         * 目标服侧：落地前校验（玩家此时不在线）。要求 arrivals.enabled、
         * 世界允许落地、锚点属于该世界；require-anchor-unlock 时查解锁记录。
         */
        @Override
        public MapOperationResult validateArrival(UUID playerUuid, String worldId, String anchorId) {
            if (!service.configuration.crossServer().arrivals().enabled()) {
                return MapOperationResult.failure("arrivals-disabled");
            }
            WorldDefinition world = service.configuration.worlds().get(worldId);
            if (world == null || !world.crossArrive()) {
                return MapOperationResult.failure("world-no-arrive");
            }
            if (!anchorId.isEmpty()) {
                AnchorDefinition anchor = service.configuration.anchor(anchorId);
                if (anchor == null || !MapService.normalizeId(anchor.worldId()).equals(world.id())) {
                    return MapOperationResult.failure("anchor-unknown");
                }
                if (service.configuration.crossServer().arrivals().requireAnchorUnlock()) {
                    try {
                        if (!service.repository.loadUnlockedAnchors(playerUuid).contains(anchor.id())) {
                            return MapOperationResult.failure("anchor-locked");
                        }
                    } catch (Exception e) {
                        return MapOperationResult.failure("storage-error");
                    }
                }
            }
            return MapService.resolveWorld(world.id()) == null
                ? MapOperationResult.failure("world-unloaded")
                : MapOperationResult.success("");
        }

        /**
         * 目标服侧：玩家已进服，执行落地传送。
         * 锚点落地按 chargeTeleportCost 决定是否走本服锚点传送扣费链；
         * 世界出生点落地永远免费（与本机 teleportToWorld 一致）。
         */
        @Override
        public void executeArrival(Player player, String worldId, String anchorId) {
            WorldDefinition world = service.configuration.worlds().get(worldId);
            if (world == null || !world.crossArrive()) {
                service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.cross-server-arrive-failed", worldId)));
                return;
            }
            World bukkitWorld = MapService.resolveWorld(world.id());
            if (bukkitWorld == null) {
                service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.anchor-world-unloaded", worldId)));
                return;
            }
            if (anchorId.isEmpty()) {
                Location spawn = service.resolveWorldSpawn(bukkitWorld);
                if (spawn == null) {
                    service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.anchor-world-unloaded", worldId)));
                    return;
                }
                player.teleport(spawn);
                service.sendPlayerResult(player, MapOperationResult.success(
                    service.message("player.cross-server-arrived", service.resolveDisplayName(world))));
                service.sync(player, false);
                return;
            }
            AnchorDefinition anchor = service.configuration.anchor(anchorId);
            if (anchor == null || !MapService.normalizeId(anchor.worldId()).equals(world.id())) {
                service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.anchor-not-found", anchorId)));
                return;
            }
            // 收费路径复用本服锚点传送链（扣费→传送→失败回滚）；免费则直传
            MapOperationResult result = service.configuration.crossServer().arrivals().chargeTeleportCost()
                ? service.performAnchorTeleport(player, anchor)
                : service.performFreeAnchorTeleport(player, anchor, bukkitWorld);
            service.sendPlayerResult(player, result);
        }

        @Override
        public void notifyPlayerResult(UUID playerUuid, MapOperationResult result) {
            Player player = playerUuid == null ? null : Bukkit.getPlayer(playerUuid);
            if (player != null) {
                service.sendPlayerResult(player, result);
            }
        }

        @Override
        public String resolveMessage(String key, Object... args) {
            return service.message(key, args);
        }
    }
