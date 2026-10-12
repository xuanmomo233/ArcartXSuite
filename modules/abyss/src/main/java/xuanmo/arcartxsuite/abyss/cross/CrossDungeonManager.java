package xuanmo.arcartxsuite.abyss.cross;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery;

/**
 * 跨服副本协调器（V1）。
 *
 * <p>模型：副本 {@code config.yml} 声明 {@code cross-server: <宿主节点id>}
 * （节点 id 即宿主 {@code cross-server.node-id}，约定等于代理服注册名）。
 * 本地玩家 {@code enter} 远端副本时：</p>
 * <ol>
 *   <li>本节点广播 {@code req_enter}（requestId + 副本 + 玩家表）</li>
 *   <li>宿主节点校验本地模板 → 登记 pendingIn → 回 {@code accept}</li>
 *   <li>源节点收 accept → 经 BungeeCord {@code Connect} 把本地玩家前送宿主节点</li>
 *   <li>宿主 join 钩子收编：等全部 expected 玩家抵达（或 30s 超时按已到人数）
 *       → 调 {@code enterDungeon(arrived)} 正常开实例</li>
 * </ol>
 *
 * <p>载荷走宿主 {@link CrossServerAPI} 通道（Redis/Proxy 双后端），
 * 本类只做协议编解码与状态机，不感知底层。</p>
 */
public final class CrossDungeonManager {

    private static final String CHANNEL_MODULE = "abyss";
    private static final long OUT_TIMEOUT_MS = 12_000;   // accept 等待
    private static final long IN_TIMEOUT_MS = 30_000;    // 玩家抵达等待

    /** 源节点：一次远端进本请求。 */
    private record PendingForward(String dungeon,
                                  List<UUID> players,
                                  long deadline) { }

    /** 宿主节点：一次远端队伍预订。 */
    private static final class PendingEnter {
        final String dungeon;
        final List<UUID> expected;
        final List<UUID> arrived = new ArrayList<>();
        final long created = System.currentTimeMillis();
        boolean triggered;

        PendingEnter(String dungeon, List<UUID> expected) {
            this.dungeon = dungeon;
            this.expected = expected;
        }
    }

    private final AbyssService service;
    private final CrossServerAPI api;
    private final Plugin plugin;
    private final Logger logger;
    private CrossServerChannel channel;
    private final Map<String, PendingForward> pendingOut = new ConcurrentHashMap<>();
    private final Map<String, PendingEnter> pendingIn = new ConcurrentHashMap<>();
    private final Map<UUID, String> expectedArrivals = new ConcurrentHashMap<>();

    public CrossDungeonManager(AbyssService service,
                               @Nullable CrossServerAPI api,
                               Plugin plugin, Logger logger) {
        this.service = service;
        this.api = api;
        this.plugin = plugin;
        this.logger = logger;
    }

    /** 服务 start 时调用：注册 BungeeCord 出站 + 开 abyss 跨服通道。 */
    public void start() {
        try {
            Bukkit.getMessenger()
                .registerOutgoingPluginChannel(plugin, "BungeeCord");
        } catch (Throwable t) {
            logger.fine("[abyss] BungeeCord 出站通道注册失败（无代理环境）: "
                + t.getMessage());
        }
        if (api == null) return;
        try {
            channel = api.openChannel(CHANNEL_MODULE,
                CrossServerChannelConfig.enabledDefault(), this::onDelivery);
        } catch (Throwable t) {
            logger.warning("[abyss] 跨服通道开启失败: " + t.getMessage());
        }
    }

    public void stop() {
        if (channel != null) {
            try { channel.close(); } catch (Throwable ignored) { }
        }
        pendingOut.clear();
        pendingIn.clear();
        expectedArrivals.clear();
    }

    public boolean active() {
        return channel != null && channel.isActive();
    }

    public String nodeId() { return api != null ? api.nodeId() : ""; }

    // ── 源节点：发起远端进本 ──────────────────────────────

    /** 广播 req_enter，accept 后前送玩家。立即返回（异步握手）。 */
    public void requestRemoteEnter(List<Player> players, AbyssTemplate template) {
        String reqId = UUID.randomUUID().toString();
        List<UUID> uuids = players.stream().map(Player::getUniqueId).toList();
        pendingOut.put(reqId, new PendingForward(template.id(), uuids,
            System.currentTimeMillis() + OUT_TIMEOUT_MS));
        for (Player p : players) {
            p.sendMessage(service.msg("cross.requesting",
                "node", template.crossServer()));
        }
        publish("op=req_enter;req=" + reqId + ";dungeon=" + template.id()
            + ";from=" + nodeId() + ";players=" + joinUuids(uuids));
        // 超时兜底
        service.scheduleDelayedRaw(() -> {
            PendingForward pf = pendingOut.remove(reqId);
            if (pf != null) {
                notifyPlayers(pf.players,
                    service.msg("cross.timeout", "dungeon", template.id()));
            }
        }, 240); // 12s
    }

    // ── 消息路由 ──────────────────────────────────────────

    private void onDelivery(CrossServerDelivery d) {
        Map<String, String> kv = parsePayload(d.payload());
        String op = kv.get("op");
        if (op == null) return;
        switch (op) {
            case "req_enter" -> onReqEnter(kv);
            case "accept" -> onAccept(kv);
            case "decline" -> onDecline(kv);
            default -> { }
        }
    }

    /** 宿主：收 req_enter → 校验 → 登记预订 → accept/decline。 */
    private void onReqEnter(Map<String, String> kv) {
        String reqId = kv.get("req");
        String dungeon = kv.get("dungeon");
        String from = kv.get("from");
        if (reqId == null || dungeon == null || from == null) return;
        if (from.equals(nodeId())) return; // 自己的回声
        AbyssTemplate tpl = service.instances().getTemplate(dungeon);
        String cs = tpl != null ? tpl.crossServer() : null;
        if (cs == null || !cs.equals(nodeId())) return;   // 非本节点宿主
        List<UUID> players = parseUuids(kv.get("players"));
        if (players.isEmpty() || !service.instances().hasAvailableSlot(tpl)) {
            publish("op=decline;req=" + reqId + ";dungeon=" + dungeon
                + ";reason=full");
            return;
        }
        PendingEnter pe = new PendingEnter(dungeon, players);
        pendingIn.put(reqId, pe);
        for (UUID u : players) expectedArrivals.put(u, reqId);
        publish("op=accept;req=" + reqId + ";dungeon=" + dungeon
            + ";host=" + nodeId());
        logger.info("[abyss] 跨服进本预订: " + dungeon + " 玩家=" + players.size()
            + " 来自 " + from);
        // 抵达超时：按已到玩家开本（至少 1 人）
        service.scheduleDelayedRaw(() -> expirePendingIn(reqId),
            IN_TIMEOUT_MS / 50);
    }

    /** 源节点：accept → 前送玩家到宿主节点。 */
    private void onAccept(Map<String, String> kv) {
        String reqId = kv.get("req");
        String host = kv.get("host");
        PendingForward pf = reqId != null ? pendingOut.remove(reqId) : null;
        if (pf == null || host == null) return;
        List<Player> online = new ArrayList<>();
        for (UUID u : pf.players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) online.add(p);
        }
        if (online.isEmpty()) return;
        for (Player p : online) {
            p.sendMessage(service.msg("cross.forwarding", "node", host));
            sendToServer(p, host);
        }
    }

    private void onDecline(Map<String, String> kv) {
        String reqId = kv.get("req");
        PendingForward pf = reqId != null ? pendingOut.remove(reqId) : null;
        if (pf == null) return;
        notifyPlayers(pf.players, service.msg("cross.declined",
            "reason", kv.getOrDefault("reason", "?")));
    }

    /** 宿主：join 钩子——收编远端抵达的玩家。 */
    public void onPlayerJoin(Player player) {
        String reqId = expectedArrivals.remove(player.getUniqueId());
        if (reqId == null) return;
        PendingEnter pe = pendingIn.get(reqId);
        if (pe == null || pe.triggered) return;
        pe.arrived.add(player.getUniqueId());
        player.sendMessage(service.msg("cross.arrived",
            "dungeon", pe.dungeon,
            "arrived", String.valueOf(pe.arrived.size()),
            "total", String.valueOf(pe.expected.size())));
        if (pe.arrived.size() >= pe.expected.size()) {
            triggerPendingIn(reqId);
        }
    }

    /** 抵达超时：已到的人照常开本；0 人则丢弃。 */
    private void expirePendingIn(String reqId) {
        PendingEnter pe = pendingIn.get(reqId);
        if (pe == null || pe.triggered) return;
        if (pe.arrived.isEmpty()) {
            pendingIn.remove(reqId);
            for (UUID u : pe.expected) expectedArrivals.remove(u);
            return;
        }
        triggerPendingIn(reqId);
    }

    private void triggerPendingIn(String reqId) {
        PendingEnter pe = pendingIn.remove(reqId);
        if (pe == null || pe.triggered) return;
        pe.triggered = true;
        for (UUID u : pe.expected) expectedArrivals.remove(u);
        List<Player> online = new ArrayList<>();
        for (UUID u : pe.arrived) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) online.add(p);
        }
        if (online.isEmpty()) return;
        // 走标准进本路径（requirements/预热/procgen 全量生效）
        var result = service.enterDungeon(online, pe.dungeon);
        if (!result.ok()) {
            for (Player p : online) {
                p.sendMessage(service.msg("cross.enter-failed",
                    "reason", String.valueOf(result.reason())));
            }
        }
    }

    /** 玩家离开：清掉等待中的预订引用。 */
    public void onPlayerQuit(Player player) {
        expectedArrivals.remove(player.getUniqueId());
    }

    // ── 工具 ─────────────────────────────────────────────

    private void publish(String payload) {
        CrossServerChannel ch = channel;
        if (ch != null) {
            try { ch.publish(payload); }
            catch (Throwable t) {
                logger.fine("[abyss] 跨服消息发送失败: " + t.getMessage());
            }
        }
    }

    /** BungeeCord 插件消息 Connect：把玩家前送到指定代理服。 */
    private void sendToServer(Player player, String server) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Throwable t) {
            player.sendMessage(service.msg("cross.forward-failed",
                "node", server));
            logger.fine("[abyss] 玩家前送失败 " + player.getName() + ": "
                + t.getMessage());
        }
    }

    private void notifyPlayers(List<UUID> uuids, String msg) {
        for (UUID u : uuids) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) p.sendMessage(msg);
        }
    }

    private static Map<String, String> parsePayload(String payload) {
        Map<String, String> kv = new HashMap<>();
        if (payload == null) return kv;
        for (String part : payload.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) kv.put(part.substring(0, i).trim(),
                part.substring(i + 1).trim());
        }
        return kv;
    }

    private static String joinUuids(List<UUID> uuids) {
        StringBuilder sb = new StringBuilder();
        for (UUID u : uuids) sb.append(u).append(',');
        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
    }

    private static List<UUID> parseUuids(String s) {
        List<UUID> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) {
            try { out.add(UUID.fromString(part.trim())); }
            catch (IllegalArgumentException ignored) { }
        }
        return out;
    }
}
