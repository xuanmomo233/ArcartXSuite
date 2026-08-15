package xuanmo.arcartxsuite.debug

import org.bukkit.entity.Player
import xuanmo.arcartxsuite.api.ClientPacketHandler
import java.time.Instant
import java.util.ArrayDeque

/**
 * 客户端包流拦截/捕获管理器。
 *
 * 实现 [ClientPacketHandler]，通过 [xuanmo.arcartxsuite.api.ModuleContext.registerClientPacketHandler]
 * 注册后，所有客户端自定义包都会流经此处，被记录到环形缓冲供 RPC 查询。
 *
 * 捕获器**不消费**包（始终返回 false），仅旁路记录，不影响其他模块处理。
 */
class PacketCaptureManager(
    private val bufferSize: Int,
    private val logPayload: Boolean,
) : ClientPacketHandler {

    /** 单条捕获记录 */
    data class CapturedPacket(
        val timestamp: Instant,
        val player: String,
        val packetId: String,
        val data: List<String>,
    )

    private val buffer: ArrayDeque<CapturedPacket> = ArrayDeque(bufferSize)
    private val lock = Any()

    override fun handleClientPacket(player: Player, packetId: String, data: List<String>): Boolean {
        val payload = if (logPayload) data else emptyList()
        val record = CapturedPacket(Instant.now(), player.name, packetId, payload)
        synchronized(lock) {
            if (buffer.size >= bufferSize) {
                buffer.pollFirst()
            }
            buffer.addLast(record)
        }
        return false // 不消费，让后续处理器继续处理
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
