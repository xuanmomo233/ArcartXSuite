package xuanmo.arcartxsuite.map.service;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import xuanmo.arcartxsuite.api.BuildEdition;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;

/**
 * 地形预加载推送服务：把服务端已生成区块的地形（每列 topY + 顶层方块材质）
 * 经 plugin message {@code axsmap:terrain} 推给客户端 mod。
 * <p>
 * 管线：
 * <ol>
 *   <li>{@link #submit} 入队 MapChunk（64² 方块）坐标集合</li>
 *   <li>异步加载已生成区块，主线程按 tick 分批抓取快照</li>
 *   <li>快照齐了 → 异步线程提取列数据（topY + 材质 key 色板）→ 组包</li>
 *   <li>回到主线程 {@code player.sendPluginMessage} 逐块下发</li>
 * </ol>
 * <p>
 * 包格式（{@code axsmap:terrain}，DataOutputStream 序）：
 * <pre>{@code
 * writeUTF(dimKey) | int cx | int cz | int sourceVersion | unsigned short resolvedMask
 * | byte paletteSize | paletteSize × writeUTF(materialKey)
 * | resolved columns in row order × (short topY | byte paletteIdx)
 * }</pre>
 * 请求格式（{@code axsmap:request}，客户端→服务端）：
 * <pre>{@code
 * writeUTF(worldId) | int count | count × (int cx, int cz, unsigned short missingMask)
 * }</pre>
 */
public final class MapTerrainPushService {

    /** 客户端→服务端请求通道。 */
    public static final String CHANNEL_REQUEST = "axsmap:request";
    /** 服务端→客户端地形数据通道。 */
    public static final String CHANNEL_TERRAIN = "axsmap:terrain";

    /** 主线程分片抓取快照；时间预算在每次区块访问后检查。 */
    private static final int SNAPSHOTS_PER_TICK = 4;
    private static final long CAPTURE_BUDGET_NANOS = 2_000_000L;
    private static final int MAX_PENDING_CHUNKS = 4096;
    private static final int MAX_PLAYER_CHUNKS = 1024;
    private static final int MAX_IN_FLIGHT = 4;
    private static final int ALL_SUBCHUNKS = 0xffff;
    private static final Method LOAD_CHUNK_ASYNC = findAsyncChunkLoader();

    private final Plugin plugin;
    private final Logger logger;
    private final Predicate<Player> canReceive;
    private final Function<UUID, AuthSnapshot> admissionSnapshot;

    /** 请求队列轮转，每个地图块在同一时间只采样一次。 */
    private final Deque<Deque<TerrainWork>> jobs = new ArrayDeque<>();
    private final Deque<Delivery> outbox = new ArrayDeque<>();
    private final Map<ChunkKey, TerrainWork> terrain = new HashMap<>();
    private final Map<UUID, PlayerQueue> players = new HashMap<>();
    private int pendingRecipients;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger loadingChunks = new AtomicInteger();
    private final Deque<Capture> waitingCaptures = new ArrayDeque<>();
    private Capture capture;

    public record ChunkRequest(long coordinate, int mask) {}
    private record ChunkKey(UUID world, int version, ChunkRequest request) {}

    private static final class PlayerQueue {
        final UUID id;
        final Player player;
        final AuthSnapshot admission;
        final Set<TerrainWork> pending = new HashSet<>();

        PlayerQueue(UUID id, Player player, AuthSnapshot admission) {
            this.id = id;
            this.player = player;
            this.admission = admission;
        }
    }

    private record Recipient(PlayerQueue local, BiConsumer<ChunkRequest, byte[]> relay) {
        Object key() { return local == null ? relay : local; }
    }

    private static final class TerrainWork {
        final ChunkKey key;
        final Map<Object, Recipient> recipients = new LinkedHashMap<>();
        EncodedTerrain encoded;
        boolean finished;

        TerrainWork(ChunkKey key) {
            this.key = key;
        }
    }

    private record EncodedTerrain(byte[] packet, int bodyOffset) {}
    private record Delivery(TerrainWork work, Recipient recipient) {}

    private static final class Capture {
        final TerrainWork work;
        final ChunkSnapshot[] snapshots = new ChunkSnapshot[16];
        final Map<Integer, CompletableFuture<Chunk>> pending = new LinkedHashMap<>();
        int next;
        int resolved;

        Capture(TerrainWork work) {
            this.work = work;
        }

        boolean hasReadyChunk() {
            for (var load : pending.values()) {
                if (load.isDone()) return true;
            }
            return false;
        }
    }

    private int enqueue(Player player, UUID world, List<ChunkRequest> requests, int version,
                        BiConsumer<ChunkRequest, byte[]> relay) {
        synchronized (jobs) {
            if (stopped) return 0;
            PlayerQueue local = null;
            if (player != null) {
                UUID id = player.getUniqueId();
                AuthSnapshot admission = admissionSnapshot.apply(id);
                if (!admission.admitted()) return 0;
                local = players.get(id);
                if (local != null && (local.player != player || !sameAdmission(local.admission, admission))) {
                    cancelPlayer(id);
                    local = null;
                }
                if (local == null) {
                    local = new PlayerQueue(id, player, admission);
                    players.put(id, local);
                }
            }
            var recipient = new Recipient(local, relay);
            var batch = new ArrayDeque<TerrainWork>();
            int accepted = 0;
            int processed = 0;
            for (; processed < requests.size(); processed++) {
                if (pendingRecipients >= MAX_PENDING_CHUNKS || accepted >= MAX_PLAYER_CHUNKS
                        || (local != null && local.pending.size() >= MAX_PLAYER_CHUNKS)) break;
                ChunkRequest request = requests.get(processed);
                if (request.mask() == 0) continue;
                var key = new ChunkKey(world, version, request);
                TerrainWork work = terrain.get(key);
                if (work == null) {
                    work = new TerrainWork(key);
                    terrain.put(key, work);
                    batch.addLast(work);
                }
                if (work.recipients.putIfAbsent(recipient.key(), recipient) != null) continue;
                pendingRecipients++;
                accepted++;
                if (local != null) local.pending.add(work);
                if (work.finished) outbox.addLast(new Delivery(work, recipient));
            }
            if (!batch.isEmpty()) jobs.addLast(batch);
            if (local != null && local.pending.isEmpty()) players.remove(local.id, local);
            return processed;
        }
    }

    private org.bukkit.scheduler.BukkitTask pumpTask;
    private volatile boolean stopped;

    public MapTerrainPushService(Plugin plugin, Logger logger, Predicate<Player> canReceive,
                                 Function<UUID, AuthSnapshot> admissionSnapshot) {
        this.plugin = plugin;
        this.logger = logger;
        this.canReceive = canReceive;
        this.admissionSnapshot = admissionSnapshot;
    }

    /** Rechecks the original player session and its current permission at delivery time. */
    public boolean canSendTerrain(Player player, AuthSnapshot admission) {
        return !stopped && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
            && canReceive.test(player) && sameAdmission(admission, admissionSnapshot.apply(player.getUniqueId()));
    }

    private static boolean sameAdmission(AuthSnapshot started, AuthSnapshot current) {
        return started.admitted() && current.admitted() && started.generation() == current.generation()
            && Objects.equals(started.connectionId(), current.connectionId());
    }

    /** 启动每 tick 泵（主线程/global scheduler）。 */
    public void start() {
        if (pumpTask != null) return;
        stopped = false;
        pumpTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pump, 1L, 1L);
    }

    public void shutdown() {
        stopped = true;
        if (pumpTask != null) {
            pumpTask.cancel();
            pumpTask = null;
        }
        synchronized (jobs) {
            jobs.clear();
            terrain.clear();
            players.clear();
            pendingRecipients = 0;
            outbox.clear();
            waitingCaptures.clear();
            capture = null;
        }
    }

    public void cancelPlayer(UUID uuid) {
        synchronized (jobs) {
            var local = players.remove(uuid);
            if (local == null) return;
            for (var work : List.copyOf(local.pending)) detach(work, local);
            outbox.removeIf(delivery -> delivery.recipient().local() == local);
            jobs.removeIf(batch -> {
                batch.removeIf(work -> work.recipients.isEmpty());
                return batch.isEmpty();
            });
            waitingCaptures.removeIf(waiting -> waiting.work.recipients.isEmpty());
            if (capture != null && capture.work.recipients.isEmpty()) capture = null;
        }
    }

    private void detach(TerrainWork work, Object key) {
        Recipient removed = work.recipients.remove(key);
        if (removed == null) return;
        pendingRecipients--;
        if (removed.local() != null) {
            var local = removed.local();
            local.pending.remove(work);
            if (local.pending.isEmpty()) players.remove(local.id, local);
        }
        if (work.recipients.isEmpty()) terrain.remove(work.key, work);
    }

    /**
     * 入队一次推送：把指定 MapChunk 集合的地形发给玩家。
     *
     * @param player        目标玩家
     * @param world         Bukkit 世界
     * @param chunkCoords   MapChunk 坐标集合（packed {@code cx<<32|cz}）
     * @param sourceVersion 数据版本（写入包体，客户端做缓存去重）
     */
    public void submit(Player player, World world, Iterable<Long> chunkCoords, int sourceVersion) {
        if (stopped || player == null || world == null || chunkCoords == null) {
            return;
        }
        enqueue(player, world.getUID(), fullRequests(chunkCoords), sourceVersion, null);
    }

    public void submitRequests(Player player, World world, List<ChunkRequest> requests, int sourceVersion) {
        enqueue(player, world.getUID(), requests, sourceVersion, null);
    }

    private static List<ChunkRequest> fullRequests(Iterable<Long> coordinates) {
        List<ChunkRequest> requests = new ArrayList<>();
        for (Long coordinate : coordinates) {
            if (coordinate != null) requests.add(new ChunkRequest(coordinate, ALL_SUBCHUNKS));
            if (requests.size() == MAX_PLAYER_CHUNKS) break;
        }
        return requests;
    }

    /**
     * 跨服中继入队：抓取并组包后不发给玩家，逐块把<b>块体</b>（不含 dimKey
     * 前缀的 {@code int cx | int cz | int ver | palette | columns}）交给 sink。
     * 供 {@code MapCrossServerService} 把本机地形回传给请求节点。
     *
     * @param world         Bukkit 世界
     * @param chunkCoords   MapChunk 坐标集合（packed {@code cx<<32|cz}）
     * @param sourceVersion 数据版本（写入包体，远端缓存去重用）
     * @param chunkBodySink 块体回调（主线程触发，逐块一次）
     */
    public void submitForRelay(World world, Iterable<Long> chunkCoords, int sourceVersion,
                               Consumer<byte[]> chunkBodySink) {
         if (!BuildEdition.CROSS_SERVER_INCLUDED || stopped || world == null || chunkCoords == null || chunkBodySink == null) {
            return;
        }
        submitRequestsForRelay(world, fullRequests(chunkCoords), sourceVersion, (request, body) -> {
            if (body != null) chunkBodySink.accept(body);
        });
    }

    /** Returns the requests that did not fit in the queue. */
    public List<ChunkRequest> submitRequestsForRelay(World world, List<ChunkRequest> requests, int sourceVersion,
                                                    BiConsumer<ChunkRequest, byte[]> chunkBodySink) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return requests;
        int processed = enqueue(null, world.getUID(), requests, sourceVersion, chunkBodySink);
        return requests.subList(processed, requests.size());
    }

    /** 每 tick：发送成品包 + 派发新的 MapChunk 抓取任务。 */
    private void pump() {
        if (stopped) return;
        // 1. 先发成品（sendPluginMessage 走主线程）
        for (int sent = 0; sent < 8; sent++) {
            Delivery delivery;
            synchronized (jobs) {
                delivery = outbox.pollFirst();
                if (delivery == null) break;
                detach(delivery.work(), delivery.recipient().key());
            }
            deliver(delivery);
        }
        // Keep Bukkit snapshot access in this main-thread pump.
        Capture current;
        boolean canWait;
        synchronized (jobs) {
            if (capture == null && inFlight.get() < MAX_IN_FLIGHT) {
                var waiting = waitingCaptures.iterator();
                while (waiting.hasNext()) {
                    var candidate = waiting.next();
                    if (candidate.hasReadyChunk()
                        || (candidate.next < 16 && loadingChunks.get() < SNAPSHOTS_PER_TICK)) {
                        capture = candidate;
                        waiting.remove();
                        break;
                    }
                }
                if (capture == null) {
                    var batch = jobs.pollFirst();
                    if (batch != null) {
                        capture = new Capture(batch.removeFirst());
                        if (!batch.isEmpty()) jobs.addLast(batch);
                    }
                }
            }
            current = capture;
            canWait = waitingCaptures.size() < MAX_IN_FLIGHT;
        }
        if (current != null) captureChunk(current, canWait);
    }

    private void deliver(Delivery delivery) {
        var encoded = delivery.work().encoded;
        var recipient = delivery.recipient();
        try {
            if (BuildEdition.CROSS_SERVER_INCLUDED && recipient.relay() != null) {
                // The relay sink owns its byte array and must not be able to modify the shared packet.
                recipient.relay().accept(delivery.work().key.request(), encoded == null ? null : java.util.Arrays.copyOfRange(
                    encoded.packet(), encoded.bodyOffset(), encoded.packet().length));
            } else {
                Player player = recipient.local().player;
                if (encoded != null && canSendTerrain(player, recipient.local().admission)) {
                    player.sendPluginMessage(plugin, CHANNEL_TERRAIN, encoded.packet());
                }
            }
        } catch (RuntimeException failure) {
            logger.fine("[Map] 地形数据投递失败: " + failure.getMessage());
        }
    }

    /**
     * 主线程分 tick 抓取一个 MapChunk 的 16 个快照，完成后异步组包。
     */
    private void captureChunk(Capture current, boolean canWait) {
        var work = current.work;
        long packed = work.key.request().coordinate();
        int requested = work.key.request().mask();
        int cx = (int) (packed >> 32);
        int cz = (int) (packed);
        World world = Bukkit.getWorld(work.key.world());
        if (world == null) {
            synchronized (jobs) { if (capture == current) capture = null; }
            publish(work, null);
            return;
        }
        long deadline = System.nanoTime() + CAPTURE_BUDGET_NANOS;
        int captured = 0;
        var iterator = current.pending.entrySet().iterator();
        while (iterator.hasNext() && captured < SNAPSHOTS_PER_TICK && System.nanoTime() < deadline) {
            var entry = iterator.next();
            if (!entry.getValue().isDone()) continue;
            iterator.remove();
            captured++;
            try {
                Chunk chunk = entry.getValue().getNow(null);
                if (chunk != null && world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
                    current.snapshots[entry.getKey()] = chunk.getChunkSnapshot(true, false, false);
                    current.resolved |= 1 << entry.getKey();
                } else if (chunk == null) {
                    // With generation disabled, a null result indicates that there is no saved chunk.
                    current.resolved |= 1 << entry.getKey();
                }
            } catch (RuntimeException | LinkageError ignored) { }
        }

        boolean waitingForSlot = false;
        while (current.next < 16 && captured < SNAPSHOTS_PER_TICK && System.nanoTime() < deadline) {
            int i = current.next;
            if ((requested & (1 << i)) == 0) {
                current.next++;
                continue;
            }
            int mcx = cx * 4 + (i % 4);
            int mcz = cz * 4 + (i / 4);
            try {
                boolean loaded = world.isChunkLoaded(mcx, mcz);
                if (loaded) {
                    current.snapshots[i] = world.getChunkAt(mcx, mcz).getChunkSnapshot(true, false, false);
                    current.resolved |= 1 << i;
                } else if (canWait && LOAD_CHUNK_ASYNC != null) {
                    if (loadingChunks.get() >= SNAPSHOTS_PER_TICK) {
                        waitingForSlot = true;
                        break;
                    }
                    current.pending.put(i, loadChunkAsync(world, mcx, mcz));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
            current.next++;
            captured++;
        }
        if (waitingForSlot || !current.pending.isEmpty()) {
            if (waitingForSlot || current.next == 16 || loadingChunks.get() >= SNAPSHOTS_PER_TICK) {
                synchronized (jobs) {
                    if (capture == current && waitingCaptures.size() < MAX_IN_FLIGHT) {
                        waitingCaptures.addLast(current);
                        capture = null;
                    }
                }
            }
            return;
        }
        if (current.next < 16) return;
        synchronized (jobs) {
            if (capture != current) return;
            capture = null;
        }
        String dimension = world.getKey().toString();
        if (current.resolved == 0) {
            publish(work, null);
            return;
        }
        inFlight.incrementAndGet();
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    if (!stopped) {
                        var encoded = encode(dimension, cx, cz, work.key.version(), current.resolved, current.snapshots);
                        publish(work, encoded);
                    }
                } finally {
                    inFlight.decrementAndGet();
                }
            });
        } catch (RuntimeException failure) {
            inFlight.decrementAndGet();
            publish(work, null);
            logger.fine("[Map] 地形编码无法调度: " + failure.getMessage());
        }
    }

    private static Method findAsyncChunkLoader() {
        try {
            return World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
        } catch (NoSuchMethodException | LinkageError ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Chunk> loadChunkAsync(World world, int x, int z) throws ReflectiveOperationException {
        // Paper's generation flag keeps map reads from creating new chunks.
        var future = (CompletableFuture<Chunk>) LOAD_CHUNK_ASYNC.invoke(world, x, z, false);
        if (future == null) throw new IllegalStateException("Missing asynchronous chunk result");
        loadingChunks.incrementAndGet();
        return future.whenComplete((ignored, failure) -> loadingChunks.decrementAndGet());
    }

    private void publish(TerrainWork work, EncodedTerrain encoded) {
        synchronized (jobs) {
            if (stopped || terrain.get(work.key) != work) return;
            if (encoded == null) {
                for (var recipient : List.copyOf(work.recipients.values())) {
                    if (recipient.relay() == null) detach(work, recipient.key());
                }
            }
            work.encoded = encoded;
            work.finished = true;
            for (var recipient : work.recipients.values()) outbox.addLast(new Delivery(work, recipient));
        }
    }

    /**
     * 快照 → 块体（不含 dimKey 前缀）：{@code int cx | int cz | int sourceVersion | unsigned short resolvedMask
     * | byte paletteSize | palette | resolved columns × (short topY | byte paletteIdx)}。
     * 中继链路复用——远端收到后自行补 writeUTF(dimKey) 前缀再发客户端。
     */
    public static byte[] buildChunkBody(int cx, int cz, int sourceVersion,
                                        ChunkSnapshot[] snaps) {
        int resolved = 0;
        for (int i = 0; i < snaps.length; i++) if (snaps[i] != null) resolved |= 1 << i;
        var encoded = encode(null, cx, cz, sourceVersion, resolved, snaps);
        return encoded == null ? null : encoded.packet();
    }

    private static EncodedTerrain encode(String dimension, int cx, int cz, int sourceVersion, int resolved,
                                         ChunkSnapshot[] snaps) {
        try {
            short[] topY = new short[4096];
            byte[] paletteIdx = new byte[4096];
            Map<Material, Byte> palette = new HashMap<>();
            List<String> paletteList = new ArrayList<>();
            for (int i = 0; i < 4096; i++) {
                int lx = i % 64, lz = i / 64;
                int mcIdx = (lz / 16) * 4 + (lx / 16);
                if ((resolved & (1 << mcIdx)) == 0) continue;
                ChunkSnapshot snap = snaps[mcIdx];
                if (snap == null) {
                    topY[i] = Short.MIN_VALUE;
                    paletteIdx[i] = -1;
                    continue;
                }
                int hy;
                Material mat;
                try {
                    hy = snap.getHighestBlockYAt(lx % 16, lz % 16);
                    mat = snap.getBlockType(lx % 16, hy, lz % 16);
                } catch (IllegalArgumentException e) {
                    // Empty columns may report a height below the snapshot's range.
                    topY[i] = Short.MIN_VALUE;
                    paletteIdx[i] = -1;
                    continue;
                }
                topY[i] = (short) hy;
                Material key = mat == null || mat.isAir() ? Material.AIR : mat;
                Byte idx = palette.get(key);
                if (idx == null) {
                    if (paletteList.size() >= 255) {
                        paletteIdx[i] = -1;
                        continue;
                    }
                    idx = (byte) paletteList.size();
                    palette.put(key, idx);
                    paletteList.add(key.getKey().toString());
                }
                paletteIdx[i] = idx;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream(12500);
            DataOutputStream out = new DataOutputStream(bos);
            if (dimension != null) out.writeUTF(dimension);
            int bodyOffset = bos.size();
            out.writeInt(cx);
            out.writeInt(cz);
            out.writeInt(sourceVersion);
            out.writeShort(resolved);
            out.writeByte(paletteList.size());
            for (String key : paletteList) {
                out.writeUTF(key);
            }
            for (int i = 0; i < 4096; i++) {
                int subchunk = ((i / 64 / 16) * 4) + (i % 64 / 16);
                if ((resolved & (1 << subchunk)) == 0) continue;
                out.writeShort(topY[i]);
                out.writeByte(paletteIdx[i]);
            }
            out.flush();
            return new EncodedTerrain(bos.toByteArray(), bodyOffset);
        } catch (Exception e) {
            // 单包构建失败（快照异常/IO 等）→ 丢包降级，不上抛炸调度器
            return null;
        }
    }

    /**
     * 解析客户端 {@code axsmap:request} 请求体。
     *
     * @param message 原始字节
     * @return {worldId, 坐标列表}；格式非法返回 null
     */
    public static RequestData parseRequest(byte[] message) {
        try {
            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(message));
            String worldId = in.readUTF();
            int count = in.readInt();
            if (worldId.isBlank() || count < 0 || count > MAX_PLAYER_CHUNKS || in.available() != count * 10) {
                return null;
            }
            List<ChunkRequest> coords = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long coordinate = ((long) in.readInt() << 32) | (in.readInt() & 0xffffffffL);
                int mask = in.readUnsignedShort();
                if (mask == 0) return null;
                coords.add(new ChunkRequest(coordinate, mask));
            }
            return new RequestData(worldId, coords);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析后的请求数据。 */
    public record RequestData(String worldId, List<ChunkRequest> chunks) {
    }
}
