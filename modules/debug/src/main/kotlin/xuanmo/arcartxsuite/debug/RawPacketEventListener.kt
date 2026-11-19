package xuanmo.arcartxsuite.debug

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import priv.seventeen.artist.arcartx.event.client.ClientChannelEvent
import priv.seventeen.artist.arcartx.event.client.ClientCustomPacketEvent
import priv.seventeen.artist.arcartx.event.client.ClientInitializedEvent
import xuanmo.arcartxsuite.api.auth.AuthAccess

/**
 * 包链路信号监听器（MONITOR 只读），把各层事件喂给 [RawChannelDiag] 计数：
 *  - [ClientCustomPacketEvent]：包走出 ArcartX 网络层、进入 Bukkit 事件层
 *  - [ClientInitializedEvent] 四个子阶段：客户端 mod 握手/初始化里程碑
 *  - [ClientChannelEvent]：客户端注册 arcartx 通道
 *  - PlayerJoin/Quit：进服快照 + watch 清理
 *
 * 不消费、不拦截——事件均不可取消或被动旁路，绝不影响业务链路。
 */
class RawPacketEventListener(
    private val diag: RawChannelDiag,
    private val capture: PacketCaptureManager?,
    private val logPayload: Boolean,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onCustomPacket(event: ClientCustomPacketEvent) {
        try {
            val data = event.data ?: emptyList()
            val sensitive = event.id.equals("AXS_auth_v1", ignoreCase = true)
                || data.firstOrNull()?.contains("password", ignoreCase = true) == true
            val safe = !sensitive && AuthAccess.admitted(event.player.uniqueId)
            val payload = if (safe && logPayload) data else null
            diag.recordEvent(event.player, event.id, data.size, payload)
            if (safe) capture?.record(event.player, event.id, payload ?: emptyList())
        } catch (_: Throwable) {
            // 诊断旁路绝不能影响业务链路
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInitStart(event: ClientInitializedEvent.Start) = record(event.player, "Start")

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInitEnd(event: ClientInitializedEvent.End) = record(event.player, "End")

    @EventHandler(priority = EventPriority.MONITOR)
    fun onResourceLoaded(event: ClientInitializedEvent.ResourceLoaded) = record(event.player, "ResourceLoaded")

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInitReload(event: ClientInitializedEvent.Reload) = record(event.player, "Reload")

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChannel(event: ClientChannelEvent) {
        try { diag.recordChannelEvent(event.player) } catch (_: Throwable) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        try { diag.onJoin(event.player) } catch (_: Throwable) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        try { diag.onQuit(event.player.uniqueId) } catch (_: Throwable) {}
    }

    private fun record(player: org.bukkit.entity.Player, phase: String) {
        try { diag.recordInit(player, phase) } catch (_: Throwable) {}
    }
}
