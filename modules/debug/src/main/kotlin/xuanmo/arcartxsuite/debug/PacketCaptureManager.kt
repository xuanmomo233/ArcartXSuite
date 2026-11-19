package xuanmo.arcartxsuite.debug

import org.bukkit.entity.Player
import java.time.Instant
import java.util.ArrayDeque

/**
 * 从 ArcartX 客户端包事件记录诊断数据，供 RPC 查询。
 */
class PacketCaptureManager(
    private val bufferSize: Int,
    private val logPayload: Boolean,
) {

    /** 单条捕获记录 */
    data class CapturedPacket(
        val timestamp: Instant,
        val player: String,
        val packetId: String,
        val data: List<String>,
    )

    private val buffer: ArrayDeque<CapturedPacket> = ArrayDeque(bufferSize)
    private val lock = Any()

    fun record(player: Player, packetId: String, data: List<String>) {
        val payload = if (logPayload) data.toList() else emptyList()
        val record = CapturedPacket(Instant.now(), player.name, packetId, payload)
        synchronized(lock) {
            if (buffer.size >= bufferSize) {
                buffer.pollFirst()
            }
            buffer.addLast(record)
        }
    }

    /** 返回最近捕获的包（按时间倒序，最多 limit 条） */
    fun recent(limit: Int): List<CapturedPacket> = synchronized(lock) {
        buffer.reversed().take(limit)
    }

    /** 返回全部已捕获的包（按时间正序） */
    fun all(): List<CapturedPacket> = synchronized(lock) {
        buffer.toList()
    }

    /** 清空捕获缓冲 */
    fun clear() {
        synchronized(lock) { buffer.clear() }
    }

    /** 当前缓冲中的记录数 */
    fun size(): Int = synchronized(lock) { buffer.size }
}
