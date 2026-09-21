package xuanmo.arcartxsuite.map.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.WorldDefinition;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapPin;
import xuanmo.arcartxsuite.map.model.MapWaypoint;

/**
 * 地图点位分享域：分享码生成/领取/过期清扫/聊天卡片与文本广播。
 * <p>自 {@link MapService} 提取，持有主类引用回调仓库/视图状态/消息。
 */
final class MapShareService {

    private final MapService service;
    private final ConcurrentMap<String, SharedPoint> shareCache = new ConcurrentHashMap<>();

    MapShareService(MapService service) {
        this.service = service;
    }

    void clearAll() {
        shareCache.clear();
    }


    void shareWaypoint(Player player, String waypointId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!shareEnabled()) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.share-disabled")));
            return;
        }
        MapWaypoint waypoint = service.findWaypoint(player, waypointId);
        if (waypoint == null) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-not-found", waypointId)));
            return;
        }
        sweepExpiredShares();
        String code = newShareCode();
        shareCache.put(code, new SharedPoint(
            "waypoint", waypoint.name(), waypoint.description(), MapService.normalizeId(waypoint.world()),
            waypoint.x(), waypoint.y(), waypoint.z(), player.getName(),
            System.currentTimeMillis() + service.configuration.share().expireMinutes() * 60_000L
        ));
        broadcastShare(player, code, "waypoint", waypoint.name(), waypoint.world(),
            waypoint.x(), waypoint.y(), waypoint.z());
        service.sendPlayerResult(player, MapOperationResult.success(service.message("player.waypoint-shared", waypoint.name())));
    }


    void sharePin(Player player, String pinId) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!shareEnabled()) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.share-disabled")));
            return;
        }
        MapPin pin = service.findPin(player, pinId);
        if (pin == null) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.pin-not-found", pinId)));
            return;
        }
        sweepExpiredShares();
        String code = newShareCode();
        shareCache.put(code, new SharedPoint(
            "pin", pin.name(), pin.description(), MapService.normalizeId(pin.world()),
            pin.x(), 0, pin.z(), player.getName(),
            System.currentTimeMillis() + service.configuration.share().expireMinutes() * 60_000L
        ));
        broadcastShare(player, code, "pin", pin.name(), pin.world(), pin.x(), 0, pin.z());
        service.sendPlayerResult(player, MapOperationResult.success(service.message("player.pin-shared", pin.name())));
    }


    /**
     * 领取分享点位（聊天卡片点击 → Packet.send('AXS_MAP', 'claim_share', code)，或 /map claim &lt;code&gt;）。
     * <p>
     * 把快照复制为点击者自己的路径点/标记点，受其世界内数量上限约束；
     * 不需要点击者与分享点位处于同一世界。
     */
    void claimShare(Player player, String code) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!shareEnabled()) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.share-disabled")));
            return;
        }
        String normalized = MapService.normalizeId(code);
        SharedPoint point = shareCache.get(normalized);
        if (point == null) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.share-not-found", code)));
            return;
        }
        if (point.expiresAt() < System.currentTimeMillis()) {
            shareCache.remove(normalized);
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.share-expired")));
            return;
        }
        if ("pin".equals(point.type())) {
            claimPinShare(player, point);
        } else {
            claimWaypointShare(player, point);
        }
    }


    void claimWaypointShare(Player player, SharedPoint point) {
        if (!service.configuration.waypoints().enabled()) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.custom-waypoints-disabled")));
            return;
        }
        if (!service.configuration.worlds().containsKey(point.world())) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-world-not-configured")));
            return;
        }
        int limit = service.resolveWaypointLimit(player);
        long countInWorld = service.loadWaypoints(player).stream()
            .filter(waypoint -> MapService.normalizeId(waypoint.world()).equals(point.world())).count();
        if (countInWorld >= limit) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-limit-reached", limit)));
            return;
        }
        long now = System.currentTimeMillis();
        String waypointId = service.configuration.waypoints().idPrefix() + UUID.randomUUID().toString().replace("-", "");
        MapWaypoint waypoint = new MapWaypoint(
            MapService.normalizeId(waypointId), point.name(), point.description(),
            point.world(), point.x(), point.y(), point.z(), now, now
        );
        try {
            if (!service.repository.createWaypointIfUnderWorldLimit(player.getUniqueId(), waypoint, point.world(), limit)) {
                service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-limit-reached", limit)));
                return;
            }
            service.invalidatePlayerDataCache(player.getUniqueId());
            service.state(player).selectWorld(point.world());
            service.state(player).selectWaypoint(waypoint.waypointId());
            service.sync(player, false);
            service.sendPlayerResult(player, MapOperationResult.success(
                service.message("player.waypoint-claimed", point.name(), point.senderName())));
        } catch (Exception e) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-save-failed")));
        }
    }


    void claimPinShare(Player player, SharedPoint point) {
        if (!service.configuration.waypoints().enabled()) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.custom-waypoints-disabled")));
            return;
        }
        if (!service.configuration.worlds().containsKey(point.world())) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-world-not-configured")));
            return;
        }
        int limit = service.resolveWaypointLimit(player);
        long countInWorld = service.loadPins(player).stream()
            .filter(pin -> MapService.normalizeId(pin.world()).equals(point.world())).count();
        if (countInWorld >= limit) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-limit-reached", limit)));
            return;
        }
        long now = System.currentTimeMillis();
        String pinId = service.configuration.waypoints().idPrefix() + "pin-" + UUID.randomUUID().toString().replace("-", "");
        MapPin pin = new MapPin(
            MapService.normalizeId(pinId), point.name(), point.description(),
            point.world(), point.x(), point.z(), now, now
        );
        try {
            if (!service.repository.createPinIfUnderWorldLimit(player.getUniqueId(), pin, point.world(), limit)) {
                service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-limit-reached", limit)));
                return;
            }
            service.invalidatePlayerDataCache(player.getUniqueId());
            service.state(player).selectWorld(point.world());
            service.state(player).selectPin(pin.pinId());
            service.sync(player, false);
            service.sendPlayerResult(player, MapOperationResult.success(
                service.message("player.pin-claimed", point.name(), point.senderName())));
        } catch (Exception e) {
            service.sendPlayerResult(player, MapOperationResult.failure(service.message("player.waypoint-save-failed")));
        }
    }


    /**
     * 向全服在线玩家广播分享消息。
     * <p>
     * {@code share.card-id} 配置非空 → 聊天卡片（ArcartX/chat_card/&lt;card-id&gt;.yml）；
     * 留空 → 纯文本降级广播，附带可点击的"[点击领取]"（RUN_COMMAND 执行 /map claim &lt;code&gt;）。
     */
    void broadcastShare(Player sender, String code, String type, String name,
                                String worldId, double x, double y, double z) {
        WorldDefinition definition = service.configuration.worlds().get(MapService.normalizeId(worldId));
        String worldName = definition == null ? worldId : service.resolveDisplayName(definition);
        String coords = "waypoint".equals(type)
            ? (long) x + ", " + (long) y + ", " + (long) z
            : (long) x + ", " + (long) z;
        String cardId = service.configuration.share() == null ? "" : service.configuration.share().cardId();
        if (cardId != null && !cardId.isBlank() && service.bridge != null) {
            Map<String, String> payload = Map.of(
                "type", type,
                "title", name == null ? "" : name,
                "sender", sender.getName(),
                "world", worldName,
                "coords", coords,
                "code", code,
                "expire", String.valueOf(service.configuration.share().expireMinutes())
            );
            for (Player online : Bukkit.getOnlinePlayers()) {
                service.bridge.sendChatCard(online, cardId, payload);
            }
            return;
        }
        broadcastShareText(sender, code, type, name, worldName, coords);
    }


    /** 无聊天卡片时的纯文本广播降级：主文本 + 可点击的"[点击领取]"组件。 */
    void broadcastShareText(Player sender, String code, String type, String name,
                                    String worldName, String coords) {
        String typeName = "pin".equals(type)
            ? service.message("player.share-type-pin")
            : service.message("player.share-type-waypoint");
        TextComponent line = new TextComponent(TextComponent.fromLegacyText(
            ChatColor.translateAlternateColorCodes('&', service.message("player.share-broadcast",
                sender.getName(), typeName, name, worldName, coords))));
        TextComponent claimButton = new TextComponent(TextComponent.fromLegacyText(
            ChatColor.translateAlternateColorCodes('&', service.message("player.share-claim-button"))));
        claimButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/map claim " + code));
        claimButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(service.message("player.share-claim-hover", "/map claim " + code)).create()));
        line.addExtra(claimButton);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.spigot().sendMessage(line);
        }
    }


    boolean shareEnabled() {
        return service.configuration.share() == null || service.configuration.share().enabled();
    }


    /** 生成不与现有分享冲突的 8 位随机码。 */
    String newShareCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (shareCache.containsKey(code));
        return code;
    }


    /** 惰性清扫过期分享（在分享/领取入口调用，无需独立定时任务）。 */
    void sweepExpiredShares() {
        long now = System.currentTimeMillis();
        shareCache.values().removeIf(point -> point.expiresAt() < now);
    }


    /**
     * 一条被分享的点位快照。
     * <p>
     * 路径点/标记点是玩家私有数据，分享不直接引用原记录 ID——
     * 存完整快照，原主改名/删除不影响已领取和待领取的副本。
     *
     * @param type        {@code "waypoint"} 或 {@code "pin"}
     * @param name        点位名称
     * @param description 点位描述
     * @param world       所在世界 ID（已 normalize）
     * @param expiresAt   过期时间戳（毫秒）
     */
    record SharedPoint(
        String type, String name, String description, String world,
        double x, double y, double z, String senderName, long expiresAt
    ) {
    }
}
