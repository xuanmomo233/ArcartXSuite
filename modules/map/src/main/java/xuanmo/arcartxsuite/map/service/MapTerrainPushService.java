package xuanmo.arcartxsuite.map.service;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

/**
 * 地形预加载推送服务：把服务端已生成区块的地形（每列 topY + 顶层方块材质）
 * 经 plugin message {@code axsmap:terrain} 推给客户端 mod。
 * <p>
 * 管线：
 * <ol>
 *   <li>{@link #submit} 入队 MapChunk（64² 方块）坐标集合</li>
 *   <li>每 tick 泵：取队头 MapChunk → {@link AxsScheduler#runLocationTask} 在所属区域
 *       线程内收集 16 个 MC chunk 的 {@link ChunkSnapshot}（只取已生成区块，不触发世界生成）</li>
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

    /** 每 tick 最多提交的 MapChunk 任务数（单块内最多 16 次 chunk 访问）。 */
    private static final int MAP_CHUNKS_PER_TICK = 1;

    private final Plugin plugin;
    private final Logger logger;

    /** 待推送任务队列（FIFO）。 */
    private final Deque<PushJob> jobs = new ArrayDeque<>();
    /** 待发送的成品包（异步线程生产，主线程消费）。 */
    private final ConcurrentLinkedQueue<PendingPacket> outbox = new ConcurrentLinkedQueue<>();

    private Object pumpTask;
    private volatile boolean stopped;

    private static final class PushJob {
        final UUID playerUuid;
        final UUID worldUuid;
        final String dimKey;
        final int sourceVersion;
        final ArrayDeque<Long> chunks; // packed MapChunk 坐标

        PushJob(UUID playerUuid, UUID worldUuid, String dimKey, int sourceVersion, ArrayDeque<Long> chunks) {
            this.playerUuid = playerUuid;
            this.worldUuid = worldUuid;
            this.dimKey = dimKey;
            this.sourceVersion = sourceVersion;
            this.chunks = chunks;
        }
    }

    private static final class PendingPacket {
        final UUID playerUuid;
        final byte[] bytes;

        PendingPacket(UUID playerUuid, byte[] bytes) {
            this.playerUuid = playerUuid;
            this.bytes = bytes;
        }
    }

    public MapTerrainPushService(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /** 启动每 tick 泵（主线程/global scheduler）。 */
    public void start() {
        stopped = false;
        pumpTask = AxsScheduler.runTaskTimer(plugin, this::pump, 1L, 1L);
    }

    public void shutdown() {
        stopped = true;
        if (pumpTask != null) {
            AxsScheduler.cancelTask(pumpTask);
            pumpTask = null;
        }
        jobs.clear();
        outbox.clear();
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
        for (Long packed : chunkCoords) {
            if (packed != null) {
                deque.add(packed);
            }
        }
        if (deque.isEmpty()) {
            return;
        }
        synchronized (jobs) {
            jobs.addLast(new PushJob(player.getUniqueId(), world.getUID(),
                world.getKey().toString(), sourceVersion, deque));
        }
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
            Player player = Bukkit.getPlayer(packet.playerUuid);
            if (player != null && player.isOnline()) {
                try {
                    player.sendPluginMessage(plugin, CHANNEL_TERRAIN, packet.bytes);
                } catch (Exception e) {
                    logger.fine("[Map] 地形包发送失败: " + e.getMessage());
                }
            }
        }
        // 2. 派发抓取任务（每 tick 最多 MAP_CHUNKS_PER_TICK 个 MapChunk）
        for (int i = 0; i < MAP_CHUNKS_PER_TICK; i++) {
            PushJob job;
            Long packed;
            synchronized (jobs) {
                job = jobs.peekFirst();
                if (job == null) {
                    return;
                }
                packed = job.chunks.pollFirst();
                if (job.chunks.isEmpty()) {
                    jobs.pollFirst();
                }
            }
            if (packed == null) {
                continue;
            }
            dispatchChunk(job, packed);
        }
    }

    /**
     * 派发单个 MapChunk：在所属区域线程抓 16 个 MC chunk 的快照，
     * 随后异步组包并入发送队列。
     */
    private void dispatchChunk(PushJob job, long packed) {
        int cx = (int) (packed >> 32);
        int cz = (int) (packed);
        World world = Bukkit.getWorld(job.worldUuid);
        Player player = Bukkit.getPlayer(job.playerUuid);
        if (world == null || player == null || !player.isOnline()) {
            return;
        }
        Location probe = new Location(world, cx * 64.0 + 32, 64, cz * 64.0 + 32);
        AxsScheduler.runLocationTask(plugin, probe, () -> {
            if (stopped) {
                return;
            }
            // 区域线程：只抓已生成区块（isChunkGenerated 不触发世界生成）
            ChunkSnapshot[] snaps = new ChunkSnapshot[16];
            for (int i = 0; i < 16; i++) {
                int mcx = cx * 4 + (i % 4);
                int mcz = cz * 4 + (i / 4);
                try {
                    if (world.isChunkGenerated(mcx, mcz)) {
                        // includeMaxblocky=true：getHighestBlockYAt 需要高度图，
                        // 否则 snapshot 抛 IllegalStateException("without height map")
                        snaps[i] = world.getChunkAt(mcx, mcz).getChunkSnapshot(
                            true, false, false);
                    }
                } catch (Exception ignored) {
                    // 单块失败跳过——该 1/16 区域留空
                }
            }
            AxsScheduler.runAsync(plugin, () -> {
                byte[] bytes = buildPacket(job.dimKey, cx, cz, job.sourceVersion, snaps);
                if (bytes != null) {
                    outbox.offer(new PendingPacket(job.playerUuid, bytes));
                }
            });
        });
    }

    /**
     * 快照 → 包体：每列 topY（short）+ 材质色板索引（byte）。
     * 色板按材质 key 去重，客户端解成 MapColor。
     */
    private static byte[] buildPacket(String dimKey, int cx, int cz, int sourceVersion,
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
                int hy = snap.getHighestBlockYAt(lx % 16, lz % 16);
                Material mat = snap.getBlockType(lx % 16, hy, lz % 16);
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
            out.writeUTF(dimKey);
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
            if (count < 0 || count > 1024) {
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
