package xuanmo.arcartxsuite.map.crossserver;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery;

import xuanmo.arcartxsuite.map.config.MapModuleConfiguration.CrossServerConfiguration;
import xuanmo.arcartxsuite.map.model.MapOperationResult;
import xuanmo.arcartxsuite.map.model.MapTeleportSession;
import xuanmo.arcartxsuite.map.service.MapTerrainPushService;
import xuanmo.arcartxsuite.map.service.MapTerrainPushService.ChunkRequest;

/**
 * Map 模块跨服服务：世界目录广播、远程目录缓存、远程地形中继与跨服传送握手。
 * <p>
 * 关联模块：map。消息经宿主 {@link CrossServerAPI} 通道收发（Redis/Proxy 双后端、
 * 签名与去重由宿主负责），投递在主线程执行。
 * <p>
 * 协议（JSON，{@code v}=协议版本，{@code t}=消息类型，{@code dst}=定向目标节点，
 * 空 = 广播）：
 * <ul>
 *   <li>{@code catalog {ids:[], srv, pname}} — 本机共享世界 id 清单（心跳首包，用于对账）</li>
 *   <li>{@code world {id,name,dim,cx,cz,pv,pc,arrive,srv}} — 单个共享世界元数据</li>
 *   <li>{@code anchors {world,list:[{id,name,desc,x,y,z,sort,tp}]}} — 世界锚点摘要</li>
 *   <li>{@code catalog_request} — 请求其他节点立即重播目录</li>
 *   <li>{@code treq {dst,req,world,player,chunks}} — 远程地形请求（世界侧中继）</li>
 *   <li>{@code tchunk {dst,req,cx,cz,data}} — 分块回传，未入队的坐标可用一个 {@code skipped} 列表回传</li>
 *   <li>{@code tpreq {dst,req,player,name,world,anchor}} — 跨服传送预握手</li>
 *   <li>{@code tprsp {dst,req,ok,reason}} — 传送握手应答</li>
 * </ul>
 * 远程世界在本地以 {@code nodeId/worldId} 复合 id 呈现；远程地形在客户端落到
 * 合成维度桶 {@code axsremote.<node>.<dim>}，与本机维度缓存天然隔离。
 */
public final class MapCrossServerService implements MapCrossServer {

    /** 跨服通道模块 id（同一宿主内所有 map 实例共用）。 */
    public static final String MODULE_ID = "map";
    /** BungeeCord 兼容插件消息通道（Velocity 同样识别）。 */
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final int PROTOCOL_VERSION = 1;
    /** 单次中继最多转发的 MapChunk 数（与客户端 axsmap:request count 上限一致）。 */
    private static final int MAX_RELAY_CHUNKS = 1024;
    /** 同一玩家同时挂起的远程地形请求上限。 */
    private static final int MAX_PENDING_RELAYS_PER_PLAYER = 2;
    /** 远程地形中继请求存活时间（超时未回传则丢弃）。 */
    private static final long RELAY_TTL_MS = 60_000L;
    /** 传送握手成功后，目标服等待玩家落地的窗口。 */
    private static final long ARRIVAL_TTL_MS = 90_000L;
    /** 两次主动广播的最小间隔（catalog_request 触发重播的节流）。 */
    private static final long ANNOUNCE_MIN_GAP_MS = 5_000L;
    /** 目录清扫 + 心跳调度周期（tick）。 */
    private static final long SWEEP_PERIOD_TICKS = 100L;
    /** 每世界广播的锚点摘要上限（防异常配置撑爆 proxy 后端 32KB 限制）。 */
    private static final int MAX_ANCHORS_PER_WORLD = 60;
    /** 入站锚点消息单条 list 的解析上限（与出站截断一致）。 */
    private static final int MAX_ANCHORS_INBOUND = 60;
    /** 目标服待落地传送（pendingArrivals）全局上限。 */
    private static final int MAX_PENDING_ARRIVALS = 512;
    /** 同一玩家两次 tpreq 的最小间隔（毫秒）。 */
    private static final long TPREQ_MIN_GAP_MS = 30_000L;
    /** connectName/pname 与签名告警的每节点节流间隔（毫秒）。 */
    private static final long WARN_THROTTLE_MS = 60_000L;

    private final Plugin plugin;
    private final Logger logger;
    private final CrossServerConfiguration config;
    private final CrossServerAPI api;
    private final MapTerrainPushService terrainPush;
    private final Handler handler;
    private final Gson gson = new Gson();

    /** nodeId → 远程节点目录（含其共享世界）。 */
    private final ConcurrentMap<String, RemoteNode> nodes = new ConcurrentHashMap<>();
    /** requestId → 源服挂起的传送请求（等待 tprsp）。 */
    private final ConcurrentMap<String, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    /** requestId → 源服挂起的地形中继（等待 tchunk）。 */
    private final ConcurrentMap<String, PendingRelay> pendingRelays = new ConcurrentHashMap<>();
    /** playerUuid → 目标服已批准的待落地传送（玩家准入且实际开始落地时消费）。 */
    private final ConcurrentMap<UUID, PendingArrival> pendingArrivals = new ConcurrentHashMap<>();
    /** playerUuid → 上次 tpreq 受理时间（限频，防伪造握手堆积）。 */
    private final ConcurrentMap<UUID, Long> lastTeleportRequestAt = new ConcurrentHashMap<>();
    /** 已告警过的 (节点|类别) 键——connectName 不符/未签名等告警节流。 */
    private final Set<String> throttledWarnings = ConcurrentHashMap.newKeySet();

    private volatile CrossServerChannel channel;
    private volatile org.bukkit.scheduler.BukkitTask sweepTask;
    private final Map<UUID, Object> validatingArrivals = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long lastAnnounceAt;

    public MapCrossServerService(
        Plugin plugin,
        Logger logger,
        CrossServerConfiguration config,
        CrossServerAPI api,
        MapTerrainPushService terrainPush,
        Handler handler
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
        this.api = api;
        this.terrainPush = terrainPush;
        this.handler = handler;
    }

    /**
     * 打开跨服通道并启动目录广播与状态清扫。
     * 通道未激活（宿主跨服未启用/后端全灭）时仅记警告，浏览/传送请求一律按不可用处理。
     */
    public void start() {
        if (!config.enabled()) {
            return;
        }
        channel = api.openChannel(MODULE_ID, config.channel(), this::onDelivery);
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        } catch (Exception e) {
            logger.warning("[Map] 注册 BungeeCord 插件通道失败，跨服传送将不可用: " + e.getMessage());
        }
        if (channel == null || !channel.isActive()) {
            logger.warning("[Map] 跨服通道未激活（宿主 cross-server 未启用或后端全部失败），跨服地图不可用");
        } else {
            announceCatalog();
            publish(base("catalog_request"));
        }
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS);
    }

    /** 关闭通道、停止清扫并清空全部跨服状态。 */
    public void shutdown() {
        validatingArrivals.clear();
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        if (channel != null) {
            channel.close();
            channel = null;
        }
        try {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        } catch (Exception ignored) {
        }
        nodes.clear();
        pendingTeleports.clear();
        pendingRelays.clear();
        pendingArrivals.clear();
        lastTeleportRequestAt.clear();
        throttledWarnings.clear();
    }

    /** 跨服通道是否已激活（可收发）。 */
    public boolean active() {
        CrossServerChannel ch = channel;
        return ch != null && ch.isActive();
    }

    /** 本机节点 id（宿主 cross-server.node-id，已规范化）。 */
    public String nodeId() {
        return normalizeNode(api.nodeId());
    }

    /** 当前已知的远程节点数（含已过期未清扫的）。 */
    public int remoteNodeCount() {
        return nodes.size();
    }

    /** 当前已知的远程共享世界总数。 */
    public int remoteWorldCount() {
        int count = 0;
        for (RemoteNode node : nodes.values()) {
            count += node.worlds.size();
        }
        return count;
    }

    /** 当前已知的远程锚点总数（全节点合计，用于入站上限判定）。 */
    private int remoteAnchorCount() {
        int count = 0;
        for (RemoteNode node : nodes.values()) {
            for (RemoteWorld world : node.worlds.values()) {
                count += world.anchors.size();
            }
        }
        return count;
    }

    // ─── 远程目录（供快照构建读取）────────────────────────────────

    /**
     * 全部远程共享世界的快照视图（按节点分组，供菜单世界列表合入）。
     *
     * @return 远程世界视图列表
     */
    public List<RemoteWorldView> remoteWorlds() {
        if (!active()) return List.of();
        List<RemoteWorldView> out = new ArrayList<>();
        for (RemoteNode node : nodes.values()) {
            for (RemoteWorld world : node.worlds.values()) {
                out.add(toWorldView(node, world));
            }
        }
        return out;
    }

    /**
     * 按复合 id（{@code nodeId/worldId}）解析远程世界。
     *
     * @param compositeId 复合世界 id
     * @return 远程世界视图；未知/未广播时返回 {@code null}
     */
    public RemoteWorldView remoteWorld(String compositeId) {
        if (!active()) return null;
        String[] parts = splitComposite(compositeId);
        if (parts == null) {
            return null;
        }
        RemoteNode node = nodes.get(parts[0]);
        if (node == null) {
            return null;
        }
        RemoteWorld world = node.worlds.get(parts[1]);
        return world == null ? null : toWorldView(node, world);
    }

    /**
     * 按复合锚点 id（{@code nodeId/anchorId}）解析远程锚点。
     * 锚点 id 在单节点内全局唯一（配置加载强制），跨世界遍历查找。
     *
     * @param compositeAnchorId 复合锚点 id
     * @return 远程锚点视图；不存在返回 {@code null}
     */
    public RemoteAnchorView remoteAnchor(String compositeAnchorId) {
        if (!active()) return null;
        String[] parts = splitComposite(compositeAnchorId);
        if (parts == null) {
            return null;
        }
        RemoteNode node = nodes.get(parts[0]);
        if (node == null) {
            return null;
        }
        for (RemoteWorld world : node.worlds.values()) {
            RemoteAnchor anchor = world.anchors.get(parts[1]);
            if (anchor != null) {
                return toAnchorView(node, world, anchor);
            }
        }
        return null;
    }

    // ─── 地形中继 ────────────────────────────────────────────────

    /**
     * 源服侧：把客户端的远程世界地形请求中继到目标节点。
     * 只转发该远程世界已广播预加载覆盖内的块，超界静默丢弃。
     *
     * @param player           请求玩家（回传地形的接收方）
     * @param compositeWorldId 复合世界 id（{@code nodeId/worldId}）
     * @param chunks           客户端请求的 MapChunk 坐标列表
     */
    public void relayTerrainRequest(Player player, String compositeWorldId, List<ChunkRequest> chunks) {
        if (!active() || player == null || chunks == null) {
            return;
        }
        AuthSnapshot admission = AuthAccess.snapshot(player.getUniqueId());
        if (!admission.admitted()) return;
        RemoteWorldView world = remoteWorld(compositeWorldId);
        if (world == null || world.preloadVersion() <= 0) {
            return;
        }
        int pending = 0;
        long now = System.currentTimeMillis();
        for (PendingRelay relay : pendingRelays.values()) {
            if (relay.expiresAt() >= now && relay.playerUuid().equals(player.getUniqueId())) {
                pending++;
            }
        }
        if (pending >= MAX_PENDING_RELAYS_PER_PLAYER) {
            return;
        }
        List<ChunkRequest> accepted = new ArrayList<>(Math.min(chunks.size(), MAX_RELAY_CHUNKS));
        Set<Long> coordinates = new HashSet<>();
        for (var chunk : chunks) {
            if (!world.preloadChunkSet().contains(chunk.coordinate()) || !coordinates.add(chunk.coordinate())) {
                continue;
            }
            accepted.add(chunk);
            if (accepted.size() >= MAX_RELAY_CHUNKS) {
                break;
            }
        }
        if (accepted.isEmpty()) {
            return;
        }
        String requestId = UUID.randomUUID().toString();
        pendingRelays.put(requestId, new PendingRelay(
            player, admission, world.dimensionKey(), world.nodeId(),
            now + RELAY_TTL_MS, coordinates));
        JsonObject msg = base("treq");
        msg.addProperty("dst", world.nodeId());
        msg.addProperty("req", requestId);
        msg.addProperty("world", world.worldId());
        msg.addProperty("player", player.getUniqueId().toString());
        StringBuilder encoded = new StringBuilder();
        for (var chunk : accepted) {
            if (!encoded.isEmpty()) encoded.append(';');
            encoded.append((int) (chunk.coordinate() >> 32)).append(',')
                .append((int) chunk.coordinate()).append(',').append(chunk.mask());
        }
        msg.addProperty("chunks", encoded.toString());
        publish(msg);
    }

    // ─── 跨服传送握手 ────────────────────────────────────────────

    /**
     * 源服侧：发起跨服传送预握手。
     * 校验目标节点在线、世界允许落地、锚点存在；成功则挂起等待 tprsp。
     *
     * @param session  发起玩家及其准入
     * @param nodeId   目标节点 id
     * @param worldId  目标节点上的原始世界 id
     * @param anchorId 目标锚点 id（空 = 世界出生点落地）
     * @param authorized 源服侧执行传送需要保留的权限
     * @return 受理结果（请求发出 = success，后续成败由 tprsp/超时通知）
     */
    public MapOperationResult requestTeleport(MapTeleportSession session, String nodeId, String worldId,
                                              String anchorId, BooleanSupplier authorized) {
        if (!session.current() || !authorized.getAsBoolean()) {
            return MapOperationResult.failure(handler.resolveMessage("player.no-permission"));
        }
        if (!active()) {
            return MapOperationResult.failure(handler.resolveMessage("player.cross-server-unavailable"));
        }
        RemoteNode node = nodes.get(nodeId);
        if (node == null || stale(node)) {
            return MapOperationResult.failure(handler.resolveMessage("player.cross-server-target-offline"));
        }
        RemoteWorld world = node.worlds.get(worldId);
        if (world == null) {
            return MapOperationResult.failure(handler.resolveMessage("player.cross-server-target-offline"));
        }
        if (!world.arrive) {
            return MapOperationResult.failure(handler.resolveMessage("player.cross-server-world-no-arrive"));
        }
        if (!anchorId.isEmpty() && !world.anchors.containsKey(anchorId)) {
            return MapOperationResult.failure(handler.resolveMessage("player.cross-server-anchor-unknown"));
        }
        Player player = session.player();
        String requestId = UUID.randomUUID().toString();
        pendingTeleports.put(requestId, new PendingTeleport(
            session, authorized, nodeId,
            System.currentTimeMillis() + config.requestTimeoutSeconds() * 1000L));
        JsonObject msg = base("tpreq");
        msg.addProperty("dst", nodeId);
        msg.addProperty("req", requestId);
        msg.addProperty("player", player.getUniqueId().toString());
        msg.addProperty("name", player.getName());
        msg.addProperty("world", worldId);
        msg.addProperty("anchor", anchorId);
        publish(msg);
        return MapOperationResult.success(handler.resolveMessage("player.cross-server-requesting"));
    }

    /** 玩家完成准入（目标服侧）：在原连接仍有效时一次性执行落地。 */
    public void onPlayerAdmitted(MapTeleportSession session) {
        UUID playerUuid = session.admission().playerId();
        PendingArrival arrival = pendingArrivals.get(playerUuid);
        if (!active()) {
            if (arrival != null) pendingArrivals.remove(playerUuid, arrival);
            return;
        }
        if (arrival == null || arrival.expiresAt() < System.currentTimeMillis() || !session.current()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active()) {
                pendingArrivals.remove(playerUuid, arrival);
                return;
            }
            if (sweepTask != null && arrival.expiresAt() >= System.currentTimeMillis()
                && session.current() && pendingArrivals.remove(playerUuid, arrival)) {
                handler.executeArrival(session, arrival.worldId(), arrival.anchorId());
            }
        }, Math.max(0, config.arrivalDelayTicks()));
    }

    /** 玩家退出：清理其挂起的传送/中继/落地状态。 */
    public void onPlayerQuit(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        pendingArrivals.remove(playerUuid);
        lastTeleportRequestAt.remove(playerUuid);
        cancelTeleportRequests(playerUuid);
        cancelTerrainRelays(playerUuid);
    }

    public void cancelTeleportRequests(UUID playerUuid) {
        pendingTeleports.values().removeIf(p -> p.playerUuid().equals(playerUuid));
    }

    public void cancelTerrainRelays(UUID playerUuid) {
        pendingRelays.values().removeIf(r -> r.playerUuid().equals(playerUuid));
    }

    // ─── 目录广播 ────────────────────────────────────────────────

    /**
     * 立即广播本机共享世界目录（catalog 清单 + 逐世界元数据 + 锚点摘要）。
     */
    public void announceCatalog() {
        if (!active()) {
            return;
        }
        lastAnnounceAt = System.currentTimeMillis();
        List<SharedWorldInfo> shared = handler.sharedWorldCatalog();
        JsonObject catalog = base("catalog");
        JsonArray ids = new JsonArray();
        for (SharedWorldInfo world : shared) {
            ids.add(world.worldId());
        }
        catalog.add("ids", ids);
        catalog.addProperty("srv", serverDisplayName());
        catalog.addProperty("pname", proxyConnectName());
        publish(catalog);
        for (SharedWorldInfo world : shared) {
            JsonObject msg = base("world");
            msg.addProperty("id", world.worldId());
            msg.addProperty("name", world.displayName());
            msg.addProperty("dim", world.dimension());
            msg.addProperty("cx", world.centerX());
            msg.addProperty("cz", world.centerZ());
            msg.addProperty("pv", world.preloadVersion());
            msg.addProperty("pc", world.preloadChunkCsv());
            msg.addProperty("arrive", world.allowArrive());
            msg.addProperty("srv", serverDisplayName());
            msg.addProperty("pname", proxyConnectName());
            publish(msg);
            JsonObject anchorsMsg = base("anchors");
            anchorsMsg.addProperty("world", world.worldId());
            JsonArray list = new JsonArray();
            int count = 0;
            for (SharedAnchor anchor : world.anchors()) {
                if (++count > MAX_ANCHORS_PER_WORLD) {
                    logger.fine("[Map] 世界 " + world.worldId() + " 锚点超过 "
                        + MAX_ANCHORS_PER_WORLD + "，跨服广播已截断");
                    break;
                }
                JsonObject ao = new JsonObject();
                ao.addProperty("id", anchor.id());
                ao.addProperty("name", anchor.displayName());
                ao.addProperty("desc", anchor.description());
                ao.addProperty("x", anchor.x());
                ao.addProperty("y", anchor.y());
                ao.addProperty("z", anchor.z());
                ao.addProperty("sort", anchor.sortOrder());
                ao.addProperty("tp", anchor.teleportCost());
                list.add(ao);
            }
            anchorsMsg.add("list", list);
            publish(anchorsMsg);
        }
    }

    // ─── 入站消息分发 ────────────────────────────────────────────

    private void onDelivery(CrossServerDelivery delivery) {
        if (delivery == null || delivery.payload() == null) {
            return;
        }
        String source = normalizeNode(delivery.nodeId());
        if (source.isEmpty() || source.equals(nodeId())) {
            return;
        }
        JsonObject obj;
        try {
            JsonElement parsed = JsonParser.parseString(delivery.payload());
            if (!parsed.isJsonObject()) {
                return;
            }
            obj = parsed.getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        if (optInt(obj, "v", 0) != PROTOCOL_VERSION) {
            return;
        }
        String type = optString(obj, "t");
        String dst = normalizeNode(optString(obj, "dst"));
        // 定向消息只投递给 dst 节点；广播消息（dst 为空）所有节点处理
        if (!dst.isEmpty() && !dst.equals(nodeId())) {
            return;
        }
        switch (type) {
            case "catalog" -> onCatalog(source, obj);
            case "world" -> onRemoteWorld(source, obj);
            case "anchors" -> onRemoteAnchors(source, obj);
            case "catalog_request" -> maybeAnnounce();
            case "treq" -> {
                if (!dst.isEmpty()) {
                    onTerrainRequest(source, obj);
                }
            }
            case "tchunk" -> {
                if (!dst.isEmpty()) {
                    onTerrainChunk(source, obj);
                }
            }
            case "tpreq" -> {
                if (!dst.isEmpty()) {
                    onTeleportRequest(source, obj, delivery.signed());
                }
            }
            case "tprsp" -> {
                if (!dst.isEmpty()) {
                    onTeleportResponse(source, obj);
                }
            }
            default -> {
            }
        }
    }

    private void onCatalog(String source, JsonObject obj) {
        RemoteNode node = nodes.computeIfAbsent(source, RemoteNode::new);
        node.lastSeen = System.currentTimeMillis();
        String srv = optString(obj, "srv");
        if (!srv.isEmpty()) {
            node.serverName = srv;
        }
        applyConnectName(node, optString(obj, "pname"));
        Set<String> ids = new HashSet<>();
        JsonArray arr = obj.getAsJsonArray("ids");
        if (arr != null) {
            for (JsonElement e : arr) {
                String id = normalizeId(e.getAsString());
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        node.catalogIds = ids;
        // 目录对账：本轮广播不再包含的世界立即摘除（同批 world 消息随后补回仍存在的）
        if (node.worlds.keySet().removeIf(id -> !ids.contains(id))) {
            handler.remoteCatalogChanged(source);
        }
    }

    private void onRemoteWorld(String source, JsonObject obj) {
        String worldId = normalizeId(optString(obj, "id"));
        if (worldId.isEmpty()) {
            return;
        }
        RemoteNode node = nodes.computeIfAbsent(source, RemoteNode::new);
        node.lastSeen = System.currentTimeMillis();
        String srv = optString(obj, "srv");
        if (!srv.isEmpty()) {
            node.serverName = srv;
        }
        applyConnectName(node, optString(obj, "pname"));
        boolean isNew = !node.worlds.containsKey(worldId);
        // 入站世界总数上限：恶意节点可无限广播世界 id 撑内存/污染 UI，超限丢弃新条目
        if (isNew && remoteWorldCount() >= config.maxRemoteWorlds()) {
            warnThrottled("worlds-cap|" + source,
                "[Map] 远程共享世界总数已达上限 " + config.maxRemoteWorlds()
                    + "，丢弃节点 " + source + " 的新世界 " + worldId);
            return;
        }
        RemoteWorld world = node.worlds.computeIfAbsent(worldId, RemoteWorld::new);
        world.displayName = optString(obj, "name");
        world.dimension = optString(obj, "dim");
        world.centerX = optDouble(obj, "cx");
        world.centerZ = optDouble(obj, "cz");
        world.preloadVersion = optInt(obj, "pv", 0);
        List<int[]> chunkList = parseChunkList(optString(obj, "pc"));
        Set<Long> packed = new HashSet<>(chunkList.size() * 2);
        for (int[] c : chunkList) {
            packed.add(pack(c[0], c[1]));
        }
        world.preloadChunkList = chunkList;
        world.preloadChunks = packed;
        world.arrive = optBool(obj, "arrive");
        if (isNew) {
            handler.remoteCatalogChanged(source);
        }
    }

    /**
     * 入站 connectName（pname）校验：必须与来源节点 id 一致。
     * <p>
     * 恶意节点广播 {@code pname=任意后端服务器名} 时，玩家跨服传送会被
     * BungeeCord "Connect" 踢到非预期服务器。强制 pname=nodeId 后，
     * 合法部署的代理服务器名须与宿主 node-id 保持一致。
     */
    private void applyConnectName(RemoteNode node, String pname) {
        if (pname.isEmpty()) {
            return;
        }
        String normalized = normalizeNode(pname);
        if (!normalized.equals(node.nodeId)) {
            warnThrottled("pname|" + node.nodeId,
                "[Map] 节点 " + node.nodeId + " 广播的 connectName（" + pname + "）与其 nodeId 不符，已丢弃该声明");
            return;
        }
        node.connectName = normalized;
    }

    /** 每 (键) 节流 WARN_THROTTLE_MS 的告警，防恶意消息刷日志。 */
    private void warnThrottled(String key, String message) {
        if (throttledWarnings.add(key)) {
            logger.warning(message);
            // 节流窗口后移除键，允许再次告警（长期不一致应持续可见）
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> throttledWarnings.remove(key), WARN_THROTTLE_MS / 50L);
        }
    }

    private void onRemoteAnchors(String source, JsonObject obj) {
        String worldId = normalizeId(optString(obj, "world"));
        if (worldId.isEmpty()) {
            return;
        }
        RemoteNode node = nodes.computeIfAbsent(source, RemoteNode::new);
        node.lastSeen = System.currentTimeMillis();
        // 世界元数据尚未到达（消息乱序）时先建占位，下轮 world 消息补齐
        RemoteWorld world = node.worlds.computeIfAbsent(worldId, RemoteWorld::new);
        Map<String, RemoteAnchor> fresh = new ConcurrentHashMap<>();
        JsonArray list = obj.getAsJsonArray("list");
        int anchorBudget = config.maxRemoteAnchorsTotal() - remoteAnchorCount() + world.anchors.size();
        if (list != null) {
            for (JsonElement e : list) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject ao = e.getAsJsonObject();
                String anchorId = normalizeId(optString(ao, "id"));
                if (anchorId.isEmpty()) {
                    continue;
                }
                // 入站锚点双重上限：单世界条数 + 全节点总数（防恶意节点广播无限锚点撑内存）
                if (fresh.size() >= MAX_ANCHORS_INBOUND || fresh.size() >= anchorBudget) {
                    warnThrottled("anchors-cap|" + source,
                        "[Map] 节点 " + source + " 的锚点广播超上限（单世界 " + MAX_ANCHORS_INBOUND
                            + " / 全局 " + config.maxRemoteAnchorsTotal() + "），多余条目已丢弃");
                    break;
                }
                fresh.put(anchorId, new RemoteAnchor(
                    anchorId,
                    optString(ao, "name"),
                    optString(ao, "desc"),
                    optDouble(ao, "x"),
                    optDouble(ao, "y"),
                    optDouble(ao, "z"),
                    optInt(ao, "sort", 0),
                    optString(ao, "tp")
                ));
            }
        }
        world.anchors.clear();
        world.anchors.putAll(fresh);
    }

    private void onTerrainRequest(String source, JsonObject obj) {
        if (!config.terrainRelay()) {
            return;
        }
        String requestId = optString(obj, "req");
        String worldId = normalizeId(optString(obj, "world"));
        List<ChunkRequest> chunks = parseTerrainChunks(optString(obj, "chunks"));
        if (requestId.isEmpty() || worldId.isEmpty() || chunks.isEmpty()) {
            return;
        }
        TerrainRelayTicket ticket = handler.prepareTerrainRelay(worldId, chunks);
        var ready = ticket == null ? List.<ChunkRequest>of() : ticket.chunks();
        var allowed = new HashSet<>(ready);
        JsonArray skipped = new JsonArray();
        for (var chunk : chunks) {
            if (!allowed.contains(chunk)) skipped.add(chunk.coordinate());
        }
        if (!ready.isEmpty()) {
            var rejected = terrainPush.submitRequestsForRelay(ticket.world(), ready, ticket.sourceVersion(),
                (chunk, body) -> sendTerrainReply(source, requestId, chunk, body));
            rejected.forEach(chunk -> skipped.add(chunk.coordinate()));
        }
        if (skipped.size() != 0) {
            JsonObject out = terrainReply(source, requestId);
            out.add("skipped", skipped);
            publish(out);
        }
    }

    private JsonObject terrainReply(String destination, String requestId) {
        JsonObject out = base("tchunk");
        out.addProperty("dst", destination);
        out.addProperty("req", requestId);
        return out;
    }

    private void sendTerrainReply(String destination, String requestId, ChunkRequest chunk, byte[] body) {
        JsonObject out = terrainReply(destination, requestId);
        out.addProperty("cx", (int) (chunk.coordinate() >> 32));
        out.addProperty("cz", (int) chunk.coordinate());
        if (body != null) out.addProperty("data", Base64.getEncoder().encodeToString(body));
        publish(out);
    }

    private void onTerrainChunk(String source, JsonObject obj) {
        String requestId = optString(obj, "req");
        PendingRelay relay = requestId.isEmpty() ? null : pendingRelays.get(requestId);
        if (relay == null || !relay.nodeId().equals(source)) {
            return;
        }
        if (relay.expiresAt() < System.currentTimeMillis()) {
            pendingRelays.remove(requestId);
            return;
        }
        if (obj.get("skipped") instanceof JsonArray skipped) {
            if (skipped.size() > MAX_RELAY_CHUNKS) return;
            for (JsonElement entry : skipped) relay.remaining().remove(entry.getAsLong());
            if (relay.remaining().isEmpty()) pendingRelays.remove(requestId, relay);
            return;
        }
        if (!obj.has("cx") || !obj.has("cz")) return;
        long coordinate = pack(obj.get("cx").getAsInt(), obj.get("cz").getAsInt());
        if (!relay.remaining().contains(coordinate)) return;
        String data = optString(obj, "data");
        if (data.isEmpty()) {
            completeTerrainReply(requestId, relay, coordinate);
            return;
        }
        byte[] body;
        try {
            body = Base64.getDecoder().decode(data);
        } catch (Exception e) {
            return;
        }
        if (body.length < 15 || java.nio.ByteBuffer.wrap(body).getLong() != coordinate) return;
        Player player = relay.player();
        if (!terrainPush.canSendTerrain(player, relay.admission())) {
            pendingRelays.remove(requestId, relay);
            return;
        }
        // 还原本地包格式：writeUTF(dimKey) + 块体（int cx | int cz | int ver | ...）
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(body.length + 24);
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF(relay.dimKey());
            out.write(body);
            out.flush();
            player.sendPluginMessage(plugin, MapTerrainPushService.CHANNEL_TERRAIN, bos.toByteArray());
        } catch (Exception e) {
            logger.fine("[Map] 远程地形包下发失败: " + e.getMessage());
        } finally {
            completeTerrainReply(requestId, relay, coordinate);
        }
    }

    private void completeTerrainReply(String requestId, PendingRelay relay, long coordinate) {
        if (relay.remaining().remove(coordinate) && relay.remaining().isEmpty()) pendingRelays.remove(requestId, relay);
    }

    private void onTeleportRequest(String source, JsonObject obj, boolean signed) {
        // 严格模式：要求宿主 HMAC 验签通过，未签名握手直接丢弃（伪造者可替任意玩家预写落地）
        if (config.teleportRequireSignedRequest() && !signed) {
            warnThrottled("unsigned-tpreq|" + source,
                "[Map] 丢弃来自节点 " + source + " 的未签名传送握手（teleport.require-signed-request=true）");
            return;
        }
        String requestId = optString(obj, "req");
        UUID playerUuid = parseUuid(optString(obj, "player"));
        String worldId = normalizeId(optString(obj, "world"));
        String anchorId = normalizeId(optString(obj, "anchor"));
        if (requestId.isEmpty() || playerUuid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // 每玩家限频：伪造者可为同一 UUID 反复预写 pendingArrival
        Long last = lastTeleportRequestAt.get(playerUuid);
        if (last != null && now - last < TPREQ_MIN_GAP_MS) {
            respondTeleport(source, requestId, false,
                handler.resolveMessage("player.cross-server-rate-limited"));
            return;
        }
        // 全局上限：伪造随机 UUID 时限制 pendingArrivals 堆积
        if (!pendingArrivals.containsKey(playerUuid) && pendingArrivals.size() >= MAX_PENDING_ARRIVALS) {
            warnThrottled("arrivals-cap",
                "[Map] pendingArrivals 已达上限 " + MAX_PENDING_ARRIVALS + "，丢弃来自 " + source + " 的传送握手");
            return;
        }
        if (validatingArrivals.size() + pendingArrivals.size() >= MAX_PENDING_ARRIVALS) return;
        Object ticket = new Object();
        if (validatingArrivals.putIfAbsent(playerUuid, ticket) != null) return;
        lastTeleportRequestAt.put(playerUuid, now);
        handler.validateArrival(playerUuid, worldId, anchorId).whenComplete((check, error) -> {
            if (!validatingArrivals.remove(playerUuid, ticket) || sweepTask == null || !active()) return;
            if (error != null || !check.success()) {
                respondTeleport(source, requestId, false, error == null ? check.message() : "storage-error");
                return;
            }
            pendingArrivals.put(playerUuid, new PendingArrival(worldId, anchorId, System.currentTimeMillis() + ARRIVAL_TTL_MS));
            respondTeleport(source, requestId, true, "");
        });
    }

    private void onTeleportResponse(String source, JsonObject obj) {
        String requestId = optString(obj, "req");
        PendingTeleport pending = requestId.isEmpty() ? null : pendingTeleports.get(requestId);
        if (pending == null || !pending.nodeId().equals(source) || !pendingTeleports.remove(requestId, pending)) {
            return;
        }
        if (!pending.current()) {
            return;
        }
        if (pending.expiresAt() < System.currentTimeMillis()) {
            handler.notifyPlayerResult(pending.playerUuid(), MapOperationResult.failure(
                handler.resolveMessage("player.cross-server-timeout")));
            return;
        }
        if (!optBool(obj, "ok")) {
            String reason = optString(obj, "reason");
            handler.notifyPlayerResult(pending.playerUuid(), MapOperationResult.failure(
                handler.resolveMessage("player.cross-server-rejected",
                    reason.isEmpty() ? "-" : reason)));
            return;
        }
        RemoteNode node = nodes.get(pending.nodeId());
        String connectName = node == null ? pending.nodeId() : node.connectName;
        if (!sendConnect(pending.session().player(), connectName)) {
            handler.notifyPlayerResult(pending.playerUuid(), MapOperationResult.failure(
                handler.resolveMessage("player.cross-server-unavailable")));
            return;
        }
        handler.notifyPlayerResult(pending.playerUuid(), MapOperationResult.success(
            handler.resolveMessage("player.cross-server-accepted")));
    }

    private void respondTeleport(String targetNode, String requestId, boolean ok, String reason) {
        JsonObject msg = base("tprsp");
        msg.addProperty("dst", targetNode);
        msg.addProperty("req", requestId);
        msg.addProperty("ok", ok);
        msg.addProperty("reason", reason == null ? "" : reason);
        publish(msg);
    }

    /** BungeeCord/Velocity "Connect" 子消息：把玩家切到目标代理服务器。 */
    private boolean sendConnect(Player player, String serverName) {
        if (!active() || serverName == null || serverName.isBlank()) {
            return false;
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            out.flush();
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, buffer.toByteArray());
            return true;
        } catch (Exception e) {
            logger.fine("[Map] 跨服 Connect 发送失败: " + e.getMessage());
            return false;
        }
    }

    // ─── 周期任务：心跳广播 + 过期清扫 ────────────────────────────

    private void sweep() {
        long now = System.currentTimeMillis();
        if (active() && now - lastAnnounceAt >= config.announceIntervalSeconds() * 1000L) {
            announceCatalog();
        }
        long ttlMs = config.nodeTtlSeconds() * 1000L;
        for (RemoteNode node : nodes.values()) {
            if (now - node.lastSeen > ttlMs && nodes.remove(node.nodeId, node)) {
                handler.remoteCatalogChanged(node.nodeId);
            }
        }
        pendingTeleports.values().removeIf(p -> {
            if (p.expiresAt() >= now) {
                return false;
            }
            if (p.current()) {
                handler.notifyPlayerResult(p.playerUuid(), MapOperationResult.failure(
                    handler.resolveMessage("player.cross-server-timeout")));
            }
            return true;
        });
        pendingRelays.values().removeIf(r -> r.expiresAt() < now);
        pendingArrivals.values().removeIf(a -> {
            if (a.expiresAt() >= now) {
                return false;
            }
            // 落地记录过期时同步释放限频槽位
            return true;
        });
        // 限频表随 pendingArrivals 收缩：仅保留仍有 pending 的键（过期 pending 已删）
        lastTeleportRequestAt.keySet().removeIf(uuid -> !pendingArrivals.containsKey(uuid));
    }

    private void maybeAnnounce() {
        if (System.currentTimeMillis() - lastAnnounceAt >= ANNOUNCE_MIN_GAP_MS) {
            announceCatalog();
        }
    }

    private boolean stale(RemoteNode node) {
        return System.currentTimeMillis() - node.lastSeen > config.nodeTtlSeconds() * 1000L;
    }

    private String serverDisplayName() {
        return config.displayName().isBlank() ? nodeId() : config.displayName();
    }

    /** 本机在代理上的 Connect 目标名（留空 = node-id）。 */
    private String proxyConnectName() {
        return config.proxyName().isBlank() ? nodeId() : config.proxyName();
    }

    // ─── 视图/协议工具 ───────────────────────────────────────────

    private RemoteWorldView toWorldView(RemoteNode node, RemoteWorld world) {
        String composite = compositeId(node.nodeId, world.id);
        String dimKey = remoteDimensionKey(node.nodeId, world.dimension);
        List<RemoteAnchorView> anchorViews = new ArrayList<>(world.anchors.size());
        for (RemoteAnchor anchor : world.anchors.values()) {
            anchorViews.add(toAnchorView(node, world, anchor));
        }
        anchorViews.sort((a, b) -> {
            int cmp = Integer.compare(a.sortOrder(), b.sortOrder());
            return cmp != 0 ? cmp : a.displayName().compareToIgnoreCase(b.displayName());
        });
        return new RemoteWorldView(
            node.nodeId,
            node.serverName,
            node.connectName,
            world.id,
            composite,
            world.displayName,
            dimKey,
            world.centerX,
            world.centerZ,
            world.preloadVersion,
            world.preloadChunkList,
            world.preloadChunks,
            world.arrive,
            anchorViews
        );
    }

    private RemoteAnchorView toAnchorView(RemoteNode node, RemoteWorld world, RemoteAnchor anchor) {
        String compositeWorldId = compositeId(node.nodeId, world.id);
        return new RemoteAnchorView(
            node.nodeId,
            node.serverName,
            compositeWorldId,
            world.id,
            anchor.id,
            compositeId(node.nodeId, anchor.id),
            anchor.name,
            anchor.description,
            anchor.x,
            anchor.y,
            anchor.z,
            anchor.sortOrder,
            anchor.teleportCost,
            world.arrive
        );
    }

    /** 复合 id："nodeId/rawId"。节点 id 不含 '/'，按首个 '/' 切分。 */
    public static String compositeId(String nodeId, String rawId) {
        return normalizeNode(nodeId) + "/" + normalizeId(rawId);
    }

    /** 拆分复合 id 为 [nodeId, rawId]；无 '/' 或任一段为空返回 null。 */
    public static String[] splitComposite(String compositeId) {
        if (compositeId == null) {
            return null;
        }
        int sep = compositeId.indexOf('/');
        if (sep <= 0 || sep >= compositeId.length() - 1) {
            return null;
        }
        String node = normalizeNode(compositeId.substring(0, sep));
        String raw = normalizeId(compositeId.substring(sep + 1));
        return node.isEmpty() || raw.isEmpty() ? null : new String[]{node, raw};
    }

    /** 远程世界的客户端维度桶：axsremote.<node>.<dim>（仅 [a-z0-9._-]，目录名安全）。 */
    public static String remoteDimensionKey(String nodeId, String dimension) {
        return "axsremote." + sanitizeKey(nodeId) + "." + sanitizeKey(dimension);
    }

    /** MapChunk 坐标打包：(cx << 32) | (cz & 0xffffffff)。 */
    public static long pack(long cx, long cz) {
        return (cx << 32) | (cz & 0xffffffffL);
    }

    /** 长坐标列表 → "cx,cz;cx,cz"。 */
    public static String chunkCsv(List<long[]> chunks) {
        StringBuilder sb = new StringBuilder(chunks.size() * 12);
        for (long[] c : chunks) {
            if (c == null || c.length < 2) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(c[0]).append(',').append(c[1]);
        }
        return sb.toString();
    }

    /** Each terrain request coordinate carries the mask of subchunks still needed. */
    private static List<ChunkRequest> parseTerrainChunks(String csv) {
        List<ChunkRequest> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        for (String pair : csv.split(";")) {
            String[] xy = pair.split(",");
            if (xy.length != 3) {
                continue;
            }
            try {
                int mask = Integer.parseInt(xy[2]);
                if (mask <= 0 || mask > 0xffff) continue;
                out.add(new ChunkRequest(pack(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])), mask));
            } catch (NumberFormatException ignored) {
            }
            if (out.size() >= MAX_RELAY_CHUNKS) {
                break;
            }
        }
        return out;
    }

    private static List<int[]> parseChunkList(String csv) {
        List<int[]> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        for (String pair : csv.split(";")) {
            String[] xy = pair.split(",");
            if (xy.length != 2) {
                continue;
            }
            try {
                out.add(new int[]{Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim())});
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private JsonObject base(String type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("v", PROTOCOL_VERSION);
        obj.addProperty("t", type);
        return obj;
    }

    private void publish(JsonObject obj) {
        CrossServerChannel ch = channel;
        if (ch == null || !ch.isActive()) {
            return;
        }
        try {
            ch.publish(gson.toJson(obj));
        } catch (Exception e) {
            logger.fine("[Map] 跨服消息发布失败: " + e.getMessage());
        }
    }

    private static String optString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e == null || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    private static int optInt(JsonObject obj, String key, int fallback) {
        JsonElement e = obj.get(key);
        try {
            return e == null || !e.isJsonPrimitive() ? fallback : e.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double optDouble(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        try {
            return e == null || !e.isJsonPrimitive() ? 0 : e.getAsDouble();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean optBool(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNode(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 缓存桶安全化：仅保留 [a-z0-9._-]，其余替换为 '_'。 */
    private static String sanitizeKey(String value) {
        return normalizeId(value).replaceAll("[^a-z0-9._\\-]", "_");
    }

    // ─── 内部状态 ────────────────────────────────────────────────

    private static final class RemoteAnchor {
        final String id;
        final String name;
        final String description;
        final double x;
        final double y;
        final double z;
        final int sortOrder;
        final String teleportCost;

        RemoteAnchor(String id, String name, String description,
                     double x, double y, double z, int sortOrder, String teleportCost) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sortOrder = sortOrder;
            this.teleportCost = teleportCost;
        }
    }

    private static final class RemoteWorld {
        final String id;
        volatile String displayName = "";
        volatile String dimension = "";
        volatile double centerX;
        volatile double centerZ;
        volatile int preloadVersion;
        volatile List<int[]> preloadChunkList = List.of();
        volatile Set<Long> preloadChunks = Set.of();
        volatile boolean arrive;
        final ConcurrentMap<String, RemoteAnchor> anchors = new ConcurrentHashMap<>();

        RemoteWorld(String id) {
            this.id = id;
        }
    }

    private static final class RemoteNode {
        final String nodeId;
        volatile String serverName;
        volatile String connectName;
        volatile long lastSeen;
        volatile Set<String> catalogIds = Set.of();
        final ConcurrentMap<String, RemoteWorld> worlds = new ConcurrentHashMap<>();

        RemoteNode(String nodeId) {
            this.nodeId = nodeId;
            this.serverName = nodeId;
            this.connectName = nodeId;
        }
    }

    private record PendingTeleport(MapTeleportSession session, BooleanSupplier authorized, String nodeId, long expiresAt) {
        UUID playerUuid() { return session.admission().playerId(); }
        boolean current() { return session.current() && authorized.getAsBoolean(); }
    }

    private record PendingRelay(Player player, AuthSnapshot admission, String dimKey, String nodeId,
                                long expiresAt, Set<Long> remaining) {
        UUID playerUuid() { return player.getUniqueId(); }
    }

    private record PendingArrival(String worldId, String anchorId, long expiresAt) {
    }

    // ─── 对外数据类型 ────────────────────────────────────────────

    /**
     * 本机对外广播的锚点摘要。
     *
     * @param id            锚点 id
     * @param displayName   显示名
     * @param description   描述
     * @param x             X 坐标
     * @param y             Y 坐标
     * @param z             Z 坐标
     * @param sortOrder     排序权重
     * @param teleportCost  传送费用展示文本（远程详情面板显示用，实际扣费以目标服为准）
     */
    public record SharedAnchor(
        String id,
        String displayName,
        String description,
        double x,
        double y,
        double z,
        int sortOrder,
        String teleportCost
    ) {
    }

    /**
     * 本机对外广播的共享世界信息。
     *
     * @param worldId          世界 id
     * @param displayName      显示名
     * @param dimension        MC 维度 key（minecraft:overworld 等）
     * @param centerX          世界中心 X
     * @param centerZ          世界中心 Z
     * @param preloadVersion   预加载版本（0 = 不提供远程地形）
     * @param preloadChunkCsv  预加载覆盖的 MapChunk 坐标串（"cx,cz;..."），版本>0 时有值
     * @param allowArrive      是否接受跨服落地传送
     * @param anchors          锚点摘要列表
     */
    public record SharedWorldInfo(
        String worldId,
        String displayName,
        String dimension,
        double centerX,
        double centerZ,
        int preloadVersion,
        String preloadChunkCsv,
        boolean allowArrive,
        List<SharedAnchor> anchors
    ) {
    }

    /**
     * 目标服侧地形中继票据：世界校验通过后可直接交给 {@link MapTerrainPushService#submitRequestsForRelay}。
     *
     * @param world          Bukkit 世界
     * @param chunks         接受的 MapChunk 坐标（packed long，已 ⊆ 预加载覆盖）
     * @param sourceVersion  写入包体的数据版本
     */
    public record TerrainRelayTicket(World world, List<ChunkRequest> chunks, int sourceVersion) {
    }

    /**
     * 跨服服务回调（由 {@code MapService} 实现，所有调用均在主线程）。
     */
    public interface Handler {

        /** 本机对外广播的共享世界目录（每次心跳重建）。 */
        List<SharedWorldInfo> sharedWorldCatalog();

        /** 远程节点目录变化（世界增删/节点过期）→ 刷新正在浏览该节点的玩家。 */
        void remoteCatalogChanged(String nodeId);

        /**
         * 目标服侧：校验世界可共享中继并返回地形票据。
         *
         * @param worldId 原始世界 id
         * @param chunks  请求的 MapChunk 坐标（[cx, cz]）
         * @return 可中继票据；拒绝（未共享/无预加载/世界卸载/全部越界）返回 {@code null}
         */
        TerrainRelayTicket prepareTerrainRelay(String worldId, List<ChunkRequest> chunks);

        /**
         * 目标服侧：玩家到达前校验落地目标（世界/锚点/可选解锁记录）。
         *
         * @param playerUuid 玩家 UUID（此时玩家不在本机在线）
         * @param worldId    原始世界 id
         * @param anchorId   锚点 id（空 = 世界出生点）
         * @return 校验结果；失败 message 会透传回源服展示给玩家
         */
        java.util.concurrent.CompletableFuture<MapOperationResult> validateArrival(UUID playerUuid, String worldId, String anchorId);

        /**
         * 目标服侧：玩家已完成本机准入，执行落地传送（含可选费用）。
         *
         * @param session  已在线玩家的准入
         * @param worldId  原始世界 id
         * @param anchorId 锚点 id（空 = 世界出生点）
         */
        void executeArrival(MapTeleportSession session, String worldId, String anchorId);

        /** 给玩家发送操作结果（带前缀/颜色）；离线则静默丢弃。 */
        void notifyPlayerResult(UUID playerUuid, MapOperationResult result);

        /** 解析 messages.yml 文案（跨服服务不持有 MessageProvider）。 */
        String resolveMessage(String key, Object... args);
    }
}
