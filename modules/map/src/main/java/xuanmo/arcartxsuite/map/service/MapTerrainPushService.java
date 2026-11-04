package xuanmo.arcartxsuite.map.service;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 地形预加载推送服务：把服务端已生成区块的地形（每列 topY + 顶层方块材质）
 * 经 plugin message {@code axsmap:terrain} 推给客户端 mod。
 * <p>
 * 管线：
 * <ol>
 *   <li>{@link #submit} 入队 MapChunk（64² 方块）坐标集合</li>
 *   <li>每 tick 主线程分片收集快照，最多访问 4 个 MC chunk，访问后检查 2ms 预算；
 *       一个 MapChunk 完成后轮转到下一个任务（不触发世界生成）</li>
 *   <li>快照齐了 → 异步线程提取列数据（topY + 材质 key 色板）→ 组包</li>
 *   <li>回到主线程 {@code player.sendPluginMessage} 逐块下发</li>
 * </ol>
 * <p>
 * 包格式（{@code axsmap:terrain}，DataOutputStream 序）：
 * <pre>{@code
 * writeUTF(dimKey) | int cx | int cz | int sourceVersion
 * | byte paletteSize | paletteSize × writeUTF(materialKey)
 * | 4096 × (short topY | byte paletteIdx)
 * }</pre>
 * 请求格式（{@code axsmap:request}，客户端→服务端）：
 * <pre>{@code
 * writeUTF(worldId) | int count | count × (int cx, int cz)
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

    private final Plugin plugin;
    private final Logger logger;

    /** 待推送任务队列，按 MapChunk 轮转。 */
    private final Deque<PushJob> jobs = new ArrayDeque<>();
    /** 待发送的成品包（异步线程生产，主线程消费）。 */
    private final ConcurrentLinkedQueue<PendingPacket> outbox = new ConcurrentLinkedQueue<>();
    private final Set<WorkKey> pending = new HashSet<>();
    private final Map<UUID, Object> playerSessions = new HashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private Capture capture;

    private record WorkKey(UUID player, Object recipient, UUID world, int version, long coordinate) { }

    private static final class Capture {
        final PushJob job;
        final long coordinate;
        final ChunkSnapshot[] snapshots = new ChunkSnapshot[16];
        int next;

        Capture(PushJob job, long coordinate) {
            this.job = job;
            this.coordinate = coordinate;
        }
    }

    private WorkKey key(PushJob job, long coordinate) {
        return new WorkKey(job.playerUuid, job.relaySink == null ? job.playerSession : job.relaySink,
            job.worldUuid, job.sourceVersion, coordinate);
    }

    private void enqueue(PushJob job) {
        synchronized (jobs) {
            if (stopped) return;
            if (job.playerUuid != null) job.playerSession = playerSessions.computeIfAbsent(job.playerUuid, ignored -> new Object());
            int[] playerPending = {(int) pending.stream()
                .filter(k -> job.playerUuid != null && job.playerUuid.equals(k.player())).count()};
            job.chunks.removeIf(coordinate -> {
                if (pending.size() >= MAX_PENDING_CHUNKS
                    || job.playerUuid != null && playerPending[0] >= MAX_PLAYER_CHUNKS) return true;
                if (!pending.add(key(job, coordinate))) return true;
                playerPending[0]++;
                return false;
            });
            if (!job.chunks.isEmpty()) jobs.addLast(job);
        }
    }

    private org.bukkit.scheduler.BukkitTask pumpTask;
    private volatile boolean stopped;

    private static final class PushJob {
        final UUID playerUuid;
        final UUID worldUuid;
        final String dimKey;
        final int sourceVersion;
        Object playerSession;
        final ArrayDeque<Long> chunks; // packed MapChunk 坐标
        /** 非 null = 跨服中继模式：产出块体（不含 dimKey 前缀）交给 sink 而非发玩家。 */
        final java.util.function.Consumer<byte[]> relaySink;

        PushJob(UUID playerUuid, UUID worldUuid, String dimKey, int sourceVersion,
                ArrayDeque<Long> chunks, java.util.function.Consumer<byte[]> relaySink) {
            this.playerUuid = playerUuid;
            this.worldUuid = worldUuid;
            this.dimKey = dimKey;
            this.sourceVersion = sourceVersion;
            this.chunks = chunks;
            this.relaySink = relaySink;
        }
    }

    private static final class PendingPacket {
        final WorkKey key;
        final UUID playerUuid;
        final byte[] bytes;
        /** 中继模式：非 null 时 pump 直接回调（bytes = 块体，无 dimKey 前缀）。 */
        final java.util.function.Consumer<byte[]> relaySink;

        PendingPacket(WorkKey key, UUID playerUuid, byte[] bytes, java.util.function.Consumer<byte[]> relaySink) {
            this.key = key;
            this.playerUuid = playerUuid;
            this.bytes = bytes;
            this.relaySink = relaySink;
        }
    }

    public MapTerrainPushService(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
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
            pending.clear();
            playerSessions.clear();
            outbox.clear();
            capture = null;
        }
    }

    public void cancelPlayer(UUID uuid) {
        synchronized (jobs) {
            jobs.removeIf(job -> uuid.equals(job.playerUuid));
            playerSessions.remove(uuid);
            pending.removeIf(key -> uuid.equals(key.player()));
            outbox.removeIf(packet -> uuid.equals(packet.playerUuid));
            if (capture != null && uuid.equals(capture.job.playerUuid)) capture = null;
        }
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
        ArrayDeque<Long> deque = new ArrayDeque<>();
        Set<Long> unique = new HashSet<>();
        for (Long packed : chunkCoords) {
            if (deque.size() >= MAX_PLAYER_CHUNKS) break;
            if (packed != null && unique.add(packed)) {
                deque.add(packed);
            }
        }
        if (deque.isEmpty()) {
            return;
        }
        enqueue(new PushJob(player.getUniqueId(), world.getUID(),
            world.getKey().toString(), sourceVersion, deque, null));
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
                               java.util.function.Consumer<byte[]> chunkBodySink) {
        if (stopped || world == null || chunkCoords == null || chunkBodySink == null) {
            return;
        }
        ArrayDeque<Long> deque = new ArrayDeque<>();
        Set<Long> unique = new HashSet<>();
        for (Long packed : chunkCoords) {
            if (deque.size() >= MAX_PLAYER_CHUNKS) break;
            if (packed != null && unique.add(packed)) {
                deque.add(packed);
            }
        }
        if (deque.isEmpty()) {
            return;
        }
        enqueue(new PushJob(null, world.getUID(), "", sourceVersion, deque, chunkBodySink));
    }

    /** 每 tick：发送成品包 + 派发新的 MapChunk 抓取任务。 */
    private void pump() {
        if (stopped) {
            return;
        }
        // 1. 先发成品（sendPluginMessage 走主线程）
        // 先判 sent 再 poll：反序会在达到上限时白丢一个已出队的包
        PendingPacket packet;
        int sent = 0;
        while (sent++ < 8 && (packet = outbox.poll()) != null) {
            synchronized (jobs) { pending.remove(packet.key); }
            if (packet.relaySink != null) {
                try {
                    packet.relaySink.accept(packet.bytes);
                } catch (Exception e) {
                    logger.fine("[Map] 中继地形包回调失败: " + e.getMessage());
                }
                continue;
            }
            Player player = Bukkit.getPlayer(packet.playerUuid);
            if (player != null && player.isOnline()) {
                try {
                    player.sendPluginMessage(plugin, CHANNEL_TERRAIN, packet.bytes);
                } catch (Exception e) {
                    logger.fine("[Map] 地形包发送失败: " + e.getMessage());
                }
            }
        }
        // Split synchronous disk loads across ticks. One slow load cannot be preempted.
        if (capture == null && inFlight.get() < MAX_IN_FLIGHT) {
            synchronized (jobs) {
                PushJob job = jobs.pollFirst();
                if (job != null) {
                    capture = new Capture(job, job.chunks.removeFirst());
                    if (!job.chunks.isEmpty()) jobs.addLast(job);
                }
            }
        }
        if (capture != null) captureChunk();
    }

    /**
     * 主线程分 tick 抓取一个 MapChunk 的 16 个快照，完成后异步组包。
     */
    private void captureChunk() {
        Capture current = capture;
        PushJob job = current.job;
        long packed = current.coordinate;
        int cx = (int) (packed >> 32);
        int cz = (int) (packed);
        World world = Bukkit.getWorld(job.worldUuid);
        // 中继模式无玩家对象：只要求世界在线，块体产出后由 sink 回传请求节点
        Player player = job.relaySink == null && job.playerUuid != null
            ? Bukkit.getPlayer(job.playerUuid) : null;
        if (world == null || (job.relaySink == null && (player == null || !player.isOnline()))) {
            synchronized (jobs) { pending.remove(key(job, packed)); }
            capture = null;
            return;
        }
        // gen=false only loads existing chunks; a single disk load is not preemptible.
        long deadline = System.nanoTime() + CAPTURE_BUDGET_NANOS;
        int captured = 0;
        while (current.next < 16 && captured++ < SNAPSHOTS_PER_TICK) {
            int i = current.next++;
            int mcx = cx * 4 + (i % 4);
            int mcz = cz * 4 + (i / 4);
            try {
                if (world.loadChunk(mcx, mcz, false)) {
                    // Height-map access requires includeMaxblocky=true.
                    current.snapshots[i] = world.getChunkAt(mcx, mcz).getChunkSnapshot(true, false, false);
                }
            } catch (Exception ignored) {
                // Leave the failed subchunk empty.
            }
            if (System.nanoTime() >= deadline) break;
        }
        if (current.next < 16) return;
        capture = null;
        inFlight.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (stopped) return;
                byte[] bytes = job.relaySink != null
                    ? buildChunkBody(cx, cz, job.sourceVersion, current.snapshots)
                    : buildPacket(job.dimKey, cx, cz, job.sourceVersion, current.snapshots);
                synchronized (jobs) {
                    if (!stopped && bytes != null && pending.contains(key(job, packed))) {
                        outbox.offer(new PendingPacket(key(job, packed), job.playerUuid, bytes, job.relaySink));
                    } else {
                        pending.remove(key(job, packed));
                    }
                }
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    /**
     * 快照 → 完整地形包：{@code writeUTF(dimKey)} + {@link #buildChunkBody}。
     */
    private static byte[] buildPacket(String dimKey, int cx, int cz, int sourceVersion,
                                      ChunkSnapshot[] snaps) {
        byte[] body = buildChunkBody(cx, cz, sourceVersion, snaps);
        if (body == null) {
            return null;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(body.length + 32);
            DataOutputStream out = new DataOutputStream(bos);
            out.writeUTF(dimKey);
            out.write(body);
            out.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 快照 → 块体（不含 dimKey 前缀）：{@code int cx | int cz | int sourceVersion
     * | byte paletteSize | palette | 4096 × (short topY | byte paletteIdx)}。
     * 中继链路复用——远端收到后自行补 writeUTF(dimKey) 前缀再发客户端。
     */
    public static byte[] buildChunkBody(int cx, int cz, int sourceVersion,
                                        ChunkSnapshot[] snaps) {
        try {
            short[] topY = new short[4096];
            byte[] paletteIdx = new byte[4096];
            Map<String, Byte> palette = new HashMap<>();
            List<String> paletteList = new ArrayList<>();
            for (int i = 0; i < 4096; i++) {
                int lx = i % 64, lz = i / 64;
                int mcIdx = (lz / 16) * 4 + (lx / 16);
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
                String key = mat == null || mat.isAir()
                    ? "minecraft:air" : mat.getKey().toString();
                Byte idx = palette.get(key);
                if (idx == null) {
                    if (paletteList.size() >= 255) {
                        paletteIdx[i] = -1;
                        continue;
                    }
                    idx = (byte) paletteList.size();
                    palette.put(key, idx);
                    paletteList.add(key);
                }
                paletteIdx[i] = idx;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream(12500);
            DataOutputStream out = new DataOutputStream(bos);
            out.writeInt(cx);
            out.writeInt(cz);
            out.writeInt(sourceVersion);
            out.writeByte(paletteList.size());
            for (String key : paletteList) {
                out.writeUTF(key);
            }
            for (int i = 0; i < 4096; i++) {
                out.writeShort(topY[i]);
                out.writeByte(paletteIdx[i]);
            }
            out.flush();
            return bos.toByteArray();
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
            if (worldId.isBlank() || count < 0 || count > 1024 || in.available() != count * 8) {
                return null;
            }
            List<long[]> coords = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                coords.add(new long[]{in.readInt(), in.readInt()});
            }
            return new RequestData(worldId, coords);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析后的请求数据。 */
    public record RequestData(String worldId, List<long[]> chunks) {
    }
}
