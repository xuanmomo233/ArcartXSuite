package xuanmo.arcartxsuite.debug

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener
import priv.seventeen.artist.arcartx.core.entity.ArcartXEntityManager
import priv.seventeen.artist.arcartx.network.message.DecodeType
import priv.seventeen.artist.arcartx.network.message.MessageID
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger
import java.util.zip.GZIPInputStream

/**
 * `arcartx:main` 原始通道诊断器。
 *
 * 与 ArcartX 的 NetworkManager 并挂同一个 incoming plugin channel（Bukkit 会将消息
 * 分发给所有已注册监听器，互不干扰），旁路解析 BaseMessage 外壳字段
 * （id/type/code/part/size），**不解密 message 负载**。
 *
 * 用于定位"个别玩家 UI 按钮全部失效"的断点层：
 *  - raw == 0            → 客户端根本没发包（Packet.send 未触发 / 脚本死 / 阉割版）
 *  - raw 有 CUSTOM 但 evt == 0 → 包到了但被 ArcartX 网络层静默丢弃（解码/查表/分片）
 *  - evt > 0             → 包已变成 ClientCustomPacketEvent，到达 AXS 路由层
 *
 * 额外识别两类协议不匹配特征：
 *  - flag 首字节 != 32   → 帧格式不匹配（ArcartX 端会直接丢，这里单独计数）
 *  - type=AES 但服务端 encryptor 未建立 → 解码返回 null 静默丢
 */
class RawChannelDiag(
    private val plugin: Plugin,
    private val logger: Logger,
) : PluginMessageListener {

    companion object {
        const val CHANNEL = "arcartx:main"
        private const val COMPRESSED_FLAG = 0x20
        private val COLOR_CODE = Regex("§[0-9a-fk-or]")
        private fun intField(text: String, name: String): Int? =
            Regex("\"$name\"\\s*:\\s*(-?\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** 每个玩家的通道/事件计数。 */
    class PlayerStats {
        val rawTotal = AtomicLong()
        val malformed = AtomicLong()       // flag != 0x20 或长度异常
        val unparseable = AtomicLong()     // 解压/JSON 外壳解析失败
        val fragments = AtomicLong()       // size > 1 的分片包
        val byCode = ConcurrentHashMap<Int, AtomicLong>()
        val byType = ConcurrentHashMap<Int, AtomicLong>()
        val evtTotal = AtomicLong()
        val byPacketId = ConcurrentHashMap<String, AtomicLong>()
        @Volatile var lastRawSnippet: String = ""
        @Volatile var lastRawAt: Long = 0L
        @Volatile var lastEventAt: Long = 0L

        // 客户端生命周期信号（ClientInitializedEvent 子事件 + ClientChannelEvent）
        @Volatile var initStartAt: Long = 0L
        @Volatile var initEndAt: Long = 0L
        @Volatile var resourceLoadedAt: Long = 0L
        @Volatile var reloadAt: Long = 0L
        @Volatile var channelEventAt: Long = 0L
        @Volatile var joinedAt: Long = 0L
    }

    private data class Parsed(
        val code: Int,
        val type: Int,
        val part: Int,
        val size: Int,
        val msgId: Int?,
        val snippet: String,
    )

    private class Watch(
        val targetName: String,
        val senders: MutableSet<CommandSender>,
        @Volatile var deadline: Long,
    )

    private val stats = ConcurrentHashMap<UUID, PlayerStats>()
    private val watches = ConcurrentHashMap<UUID, Watch>()
    private val codeNames = ConcurrentHashMap<Int, String>()
    private val typeNames = ConcurrentHashMap<Int, String>()
    private var taskId = -1

    fun start() {
        plugin.server.messenger.registerIncomingPluginChannel(plugin, CHANNEL, this)
        for (c in MessageID.Client.values()) codeNames[c.id] = c.name
        for (t in DecodeType.values()) typeNames[t.id] = t.name
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Runnable {
            val now = System.currentTimeMillis()
            val it = watches.entries.iterator()
            while (it.hasNext()) {
                val w = it.next()
                if (now >= w.value.deadline) {
                    it.remove()
                    logger.info("[PackDiag] watch ${w.value.targetName} 到期自动结束")
                    // 到期自动输出玩家快照——免手动补 info
                    Bukkit.getPlayer(w.key)?.let { p ->
                        val lines = infoReport(p)
                        for (line in lines) {
                            logger.info(COLOR_CODE.replace(line, ""))
                            for (sender in w.value.senders) {
                                try { sender.sendMessage(line) } catch (_: Throwable) {}
                            }
                        }
                    }
                }
            }
        }, 20L, 20L)
        logger.info("[PackDiag] $CHANNEL 原始通道监听已挂载（code=CUSTOM 值: ${customCode()}）")
    }

    fun shutdown() {
        try {
            plugin.server.messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, this)
        } catch (_: Throwable) {}
        if (taskId >= 0) {
            try { Bukkit.getScheduler().cancelTask(taskId) } catch (_: Throwable) {}
            taskId = -1
        }
        watches.clear()
        stats.clear()
    }

    // ── 原始通道回调 ──────────────────────────────────────

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != CHANNEL) return
        val s = stats(player.uniqueId)
        s.rawTotal.incrementAndGet()
        s.lastRawAt = System.currentTimeMillis()
        val parsed = parse(message)
        if (parsed == null) {
            s.malformed.incrementAndGet()
            val flag = if (message.isEmpty()) "empty" else "0x%02x".format(message[0].toInt() and 0xff)
            notifyWatchers(player.uniqueId, player.name, "RAW 非法帧 flag=$flag bytes=${message.size}（ArcartX 端会静默丢弃）")
            return
        }
        s.byCode.computeIfAbsent(parsed.code) { AtomicLong() }.incrementAndGet()
        if (parsed.code == -1) s.unparseable.incrementAndGet()
        if (parsed.type >= 0) s.byType.computeIfAbsent(parsed.type) { AtomicLong() }.incrementAndGet()
        if (parsed.size > 1) s.fragments.incrementAndGet()
        s.lastRawSnippet = parsed.snippet
        notifyWatchers(
            player.uniqueId, player.name,
            "RAW code=${codeName(parsed.code)}(${parsed.code}) type=${typeName(parsed.type)}(${parsed.type}) part=${parsed.part}/${parsed.size} msgId=${parsed.msgId} bytes=${message.size}"
        )
    }

    // ── ClientCustomPacketEvent 计数（由 RawPacketEventListener 喂入）──────

    fun recordEvent(player: Player, id: String, data: List<String>) {
        val s = stats(player.uniqueId)
        s.evtTotal.incrementAndGet()
        s.byPacketId.computeIfAbsent(id) { AtomicLong() }.incrementAndGet()
        s.lastEventAt = System.currentTimeMillis()
        val preview = if (data.size <= 6) data.toString() else data.take(6).toString() + "...(+${data.size - 6})"
        notifyWatchers(player.uniqueId, player.name, "EVT id=$id data=$preview")
    }

    // ── 客户端生命周期事件（由 RawPacketEventListener 喂入）──────────

    /** ClientInitializedEvent 子事件: Start / End / ResourceLoaded / Reload */
    fun recordInit(player: Player, phase: String) {
        val s = stats(player.uniqueId)
        val now = System.currentTimeMillis()
        when (phase) {
            "Start" -> s.initStartAt = now
            "End" -> s.initEndAt = now
            "ResourceLoaded" -> s.resourceLoadedAt = now
            "Reload" -> s.reloadAt = now
        }
        notifyWatchers(player.uniqueId, player.name, "INIT phase=$phase")
    }

    /** ClientChannelEvent：客户端（取消）注册 arcartx 通道 */
    fun recordChannelEvent(player: Player) {
        stats(player.uniqueId).channelEventAt = System.currentTimeMillis()
        notifyWatchers(player.uniqueId, player.name, "CHANNEL-EVENT")
    }

    /**
     * 进服快照：延迟数秒后把该玩家的关键状态打到控制台。
     * 无需手动 watch——每次进服都留一行证据，事后 grep "PackDiag" 即可对比好坏玩家。
     */
    fun onJoin(player: Player) {
        val s = stats(player.uniqueId)
        s.joinedAt = System.currentTimeMillis()
        // stats 按 UUID 跨重连保留，生命周期信号按本次会话清零
        s.initStartAt = 0L; s.initEndAt = 0L; s.resourceLoadedAt = 0L; s.reloadAt = 0L
        s.channelEventAt = 0L; s.rawTotal.set(0); s.malformed.set(0); s.unparseable.set(0)
        s.fragments.set(0); s.evtTotal.set(0)
        s.byCode.clear(); s.byType.clear(); s.byPacketId.clear()
        s.lastRawAt = 0L; s.lastEventAt = 0L
        val uuid = player.uniqueId
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val p = Bukkit.getPlayer(uuid) ?: return@Runnable
            val s = stats(uuid)
            val channels = try { p.listeningPluginChannels } catch (_: Throwable) { emptySet<String>() }
            val encryptorOk = try { ArcartXEntityManager.getPlayer(p)?.encryptor != null } catch (_: Throwable) { null }
            val sentRes = try { ArcartXEntityManager.getPlayer(p)?.sentResource } catch (_: Throwable) { null }
            logger.info(
                "[PackDiag] 进服快照 ${p.name} | channel:${mark(channels.contains(CHANNEL))}" +
                    " encryptor:${mark(encryptorOk)} initEnd:${mark(s.initEndAt > 0)}" +
                    " resLoaded:${mark(s.resourceLoadedAt > 0)} sentRes:${mark(sentRes)} raw=${s.rawTotal.get()}"
            )
        }, 80L) // 4 秒——给握手/资源加载留时间
    }

    fun onQuit(uuid: UUID) {
        watches.remove(uuid)
    }

    private fun mark(v: Boolean?): String = when (v) { true -> "√"; false -> "×"; null -> "?" }

    // ── 解析 BaseMessage 外壳 ─────────────────────────────

    private fun parse(message: ByteArray): Parsed? {
        if (message.size < 2) return null
        if (message[0].toInt() and 0xff != COMPRESSED_FLAG) return null
        val payload = message.copyOfRange(1, message.size)
        val text: String = try {
            if (payload.size >= 2 && payload[0] == 0x1f.toByte() && payload[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                String(payload, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            return null
        }
        val code = intField(text, "code") ?: run {
            // 有合法 JSON 但没有 code 字段，记为不可解析而不是 malformed
            val s = text.take(120)
            return Parsed(-1, -1, -1, -1, null, s)
        }
        val type = intField(text, "type") ?: -1
        val part = intField(text, "part") ?: -1
        val size = intField(text, "size") ?: -1
        val msgId = intField(text, "id")
        return Parsed(code, type, part, size, msgId, text.take(140))
    }

    // ── 命令支撑 ─────────────────────────────────────────

    fun addWatch(target: Player, sender: CommandSender, seconds: Int) {
        val w = watches.computeIfAbsent(target.uniqueId) {
            Watch(target.name, mutableSetOf(), 0L)
        }
        w.senders.add(sender)
        w.deadline = System.currentTimeMillis() + seconds * 1000L
    }

    fun removeWatch(uuid: UUID, sender: CommandSender?): Boolean {
        val w = watches[uuid] ?: return false
        if (sender == null) {
            watches.remove(uuid)
            return true
        }
        val removed = w.senders.remove(sender)
        if (w.senders.isEmpty()) watches.remove(uuid, w)
        return removed
    }

    fun watching(uuid: UUID): Boolean = watches.containsKey(uuid)

    fun stats(uuid: UUID): PlayerStats = stats.computeIfAbsent(uuid) { PlayerStats() }
    fun allStats(): Map<UUID, PlayerStats> = stats
    fun reset(uuid: UUID?) {
        if (uuid == null) stats.clear() else stats.remove(uuid)
    }

    // ── 判定与报告 ───────────────────────────────────────

    fun customCode(): Int = MessageID.Client.CUSTOM.id

    fun codeName(code: Int): String = codeNames[code] ?: "?"
    fun typeName(type: Int): String = typeNames[type] ?: "?"

    /**
     * 单玩家快照 + 判定结论。
     */
    fun infoReport(player: Player): List<String> {
        val out = ArrayList<String>()
        val s = stats(player.uniqueId)
        val channels = try { player.listeningPluginChannels } catch (_: Throwable) { emptySet<String>() }
        val channelOk = channels.contains(CHANNEL)
        var encryptorOk: Boolean? = null
        var sentResource: Boolean? = null
        try {
            val axp = ArcartXEntityManager.getPlayer(player)
            encryptorOk = axp?.encryptor != null
            sentResource = axp?.sentResource
        } catch (t: Throwable) {
            out.add("§7ArcartXPlayer 读取失败: ${t.javaClass.simpleName}")
        }
        val aesCount = s.byType.entries.firstOrNull { typeName(it.key).equals("AES", true) }?.value?.get() ?: 0
        val customCount = s.byCode[customCode()]?.get() ?: 0

        out.add("§6=== PackDiag: ${player.name} ===")
        out.add("§7通道注册 listeningPluginChannels 含 $CHANNEL: ${yn(channelOk)}")
        out.add("§7握手状态: encryptor=${nb(encryptorOk)} sentResource=${nb(sentResource)}")
        out.add("§7客户端生命周期: initStart=${ts(s.initStartAt)} initEnd=${ts(s.initEndAt)} resLoaded=${ts(s.resourceLoadedAt)} channelEvt=${ts(s.channelEventAt)} (进服 ${ago(s.joinedAt)})")
        out.add("§7原始包: 共 §f${s.rawTotal.get()}§7 条 (非法帧 ${s.malformed.get()}, 分片 ${s.fragments.get()})，最近 ${ago(s.lastRawAt)}")
        out.add("§7  code: " + histogram(s.byCode) { codeName(it) })
        out.add("§7  type: " + histogram(s.byType) { typeName(it) })
        out.add("§7事件包: 共 §f${s.evtTotal.get()}§7 条，最近 ${ago(s.lastEventAt)}")
        out.add("§7  id: " + histogram(s.byPacketId) { it })
        out.add("§e判定: " + verdict(s, channelOk, encryptorOk, aesCount, customCount))
        return out
    }

    fun statsLines(name: String, uuid: UUID): List<String> {
        val s = stats(uuid)
        return listOf(
            "§7$name: raw=${s.rawTotal.get()} (mal=${s.malformed.get()},frag=${s.fragments.get()}) evt=${s.evtTotal.get()}" +
                " code={${s.byCode.entries.joinToString(",") { "${codeName(it.key)}:${it.value.get()}" }}}" +
                " id={${s.byPacketId.entries.joinToString(",") { "${it.key}:${it.value.get()}" }}"
        )
    }

    private fun verdict(s: PlayerStats, channelOk: Boolean, encryptorOk: Boolean?, aesCount: Long, customCount: Long): String {
        val notes = ArrayList<String>()
        if (s.rawTotal.get() == 0L) {
            notes.add("客户端从未发出任何 $CHANNEL 包——连握手包都没有，疑似 Packet.send 在客户端侧失效/阉割版，或代理把 C→S 方向整段丢了")
        } else {
            if (customCount == 0L && s.evtTotal.get() == 0L) {
                notes.add("底层包在收但没有 CUSTOM——客户端 mod 在工作，但 Packet.send 从未触发（按钮脚本没执行/未发包）")
            }
            if (customCount > 0L && s.evtTotal.get() == 0L) {
                notes.add("CUSTOM 包已到服务端但没产出事件——死在 ArcartX 解码/查包表/分片重组，典型的协议版本不匹配")
            }
            if (s.evtTotal.get() > 0L) {
                notes.add("事件已到 AXS 路由层——开模块 debug 看 [WarehouseDebug] IN 是否有对应记录，若无则是路由/guard 问题")
            }
        }
        if (s.joinedAt > 0L && s.initEndAt == 0L && s.initStartAt == 0L) {
            notes.add("进服后从未触发 ClientInitializedEvent——客户端 mod 未初始化（假客户端/握手根本没开始），所有 UI 交互注定失效")
        } else if (s.initStartAt > 0L && s.initEndAt == 0L) {
            notes.add("initStart 触发但 initEnd 没到——握手/资源流程中断，encryptor 大概率未建立 → AES 包全丢")
        }
        if (!channelOk) notes.add("客户端未注册 $CHANNEL 监听通道——双向其实都可能断")
        if (encryptorOk == false && aesCount > 0L) notes.add("客户端发 AES 包但服务端 encryptor 未建立——解码得 null 全静默丢")
        if (s.malformed.get() > 0L) notes.add("存在 flag!=0x20 的非法帧——协议帧格式不匹配，ArcartX 会全丢")
        if (s.byCode.containsKey(-1)) notes.add("存在无法解析外壳的包——BaseMessage 格式不匹配")
        if (notes.isEmpty()) notes.add("采样内暂无异常特征，建议先 watch 后让玩家实际点按钮再判定")
        return notes.joinToString("；")
    }

    private fun <K> histogram(map: Map<K, AtomicLong>, name: (K) -> String): String =
        if (map.isEmpty()) "(无)" else map.entries.joinToString(", ") { "${name(it.key)}=${it.value.get()}" }

    private fun ago(at: Long): String =
        if (at <= 0L) "从未" else "${(System.currentTimeMillis() - at) / 1000}秒前"

    private fun ts(at: Long): String =
        if (at <= 0L) "§c未触发" else "§a${(System.currentTimeMillis() - at) / 1000}s前"

    private fun yn(v: Boolean) = if (v) "§a是" else "§c否"
    private fun nb(v: Boolean?) = when (v) { null -> "§7未知"; true -> "§a已建立"; false -> "§c未建立" }

    private fun notifyWatchers(uuid: UUID, name: String, line: String) {
        val w = watches[uuid] ?: return
        if (System.currentTimeMillis() > w.deadline) return
        val msg = "§8[PackDiag] §f$name §7$line"
        for (sender in w.senders) {
            try {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try { sender.sendMessage(msg) } catch (_: Throwable) {}
                })
            } catch (_: Throwable) {}
        }
        logger.info("[PackDiag] $name $line")
    }
}
