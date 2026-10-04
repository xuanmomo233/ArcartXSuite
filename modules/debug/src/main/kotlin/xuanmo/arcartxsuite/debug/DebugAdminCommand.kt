package xuanmo.arcartxsuite.debug

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import xuanmo.arcartxsuite.api.ModuleCommandHandler
import java.util.Locale
import java.util.function.Supplier

/**
 * `/axs debug <action>` 管理子命令——包链路诊断入口。
 *
 * 用法：
 *   /axs debug watch <player> [秒]   实时播报该玩家的 RAW/EVT 包（默认 60 秒）
 *   /axs debug unwatch [player]      停止播报（缺省停自己的全部 watch）
 *   /axs debug info <player>         玩家通道/握手状态快照 + 计数 + 判定结论
 *   /axs debug stats [player]        全部或指定玩家的计数汇总
 *   /axs debug reset [player]        清零计数（缺省清全部）
 */
class DebugAdminCommand(
    private val diagProvider: Supplier<RawChannelDiag?>,
    private val packetGuardProvider: Supplier<Any?>,
) : ModuleCommandHandler {

    companion object {
        private val ACTIONS = listOf("help", "watch", "unwatch", "info", "stats", "guard", "reset")
        private const val PERMISSION = "arcartxsuite.debug.admin"
    }

    override fun commandId(): String = "debug"
    override fun actions(): List<String> = ACTIONS

    override fun onCommand(@NotNull sender: CommandSender, @NotNull label: String, @NotNull args: Array<String>): Boolean {
        if (sender is Player && !sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§c无权限。")
            return true
        }
        val diag = diagProvider.get()
        if (diag == null) {
            sender.sendMessage("§cPackDiag 未启动。")
            return true
        }
        val action = if (args.size >= 2) args[1].lowercase(Locale.ROOT) else "help"
        when (action) {
            "watch" -> {
                val target = args.getOrNull(2)?.let { Bukkit.getPlayerExact(it) }
                if (target == null) {
                    sender.sendMessage("§c用法: /$label debug watch <在线玩家> [秒]")
                    return true
                }
                val seconds = args.getOrNull(3)?.toIntOrNull()?.coerceIn(5, 600) ?: 60
                diag.addWatch(target, sender, seconds)
                sender.sendMessage("§a已挂 watch: ${target.name} (${seconds}s)。让玩家去点仓库/菜单按钮。")
            }
            "unwatch" -> {
                val target = args.getOrNull(2)?.let { Bukkit.getPlayerExact(it) }
                if (target == null) {
                    sender.sendMessage("§c用法: /$label debug unwatch <在线玩家>")
                    return true
                }
                val removed = diag.removeWatch(target.uniqueId, sender)
                sender.sendMessage(if (removed) "§a已停止 watch ${target.name}" else "§e没有在 watch ${target.name}")
            }
            "info" -> {
                val target = args.getOrNull(2)?.let { Bukkit.getPlayerExact(it) }
                if (target == null) {
                    sender.sendMessage("§c用法: /$label debug info <在线玩家>")
                    return true
                }
                for (line in diag.infoReport(target)) sender.sendMessage(line)
            }
            "stats" -> {
                val target = args.getOrNull(2)?.let { Bukkit.getPlayerExact(it) }
                if (target != null) {
                    for (line in diag.statsLines(target.name, target.uniqueId)) sender.sendMessage(line)
                } else {
                    val all = diag.allStats()
                    if (all.isEmpty()) {
                        sender.sendMessage("§7(暂无采样数据)")
                        return true
                    }
                    sender.sendMessage("§6=== PackDiag 全局采样 ===")
                    for ((uuid, _) in all) {
                        val name = Bukkit.getPlayer(uuid)?.name ?: uuid.toString()
                        for (line in diag.statsLines(name, uuid)) sender.sendMessage(line)
                    }
                }
            }
            "guard" -> {
                val target = args.getOrNull(2)?.let { Bukkit.getPlayerExact(it) }
                if (target == null) {
                    sender.sendMessage("§c用法: /$label debug guard <在线玩家>")
                    return true
                }
                for (line in diag.guardReport(target, packetGuardProvider.get())) sender.sendMessage(line)
            }
            "reset" -> {
                val target = args.getOrNull(2)?.let { Bukkit.getPlayerExact(it) }
                if (target != null) {
                    diag.reset(target.uniqueId)
                    sender.sendMessage("§a已清零 ${target.name} 的计数")
                } else {
                    diag.reset(null)
                    sender.sendMessage("§a已清零全部计数")
                }
            }
            else -> {
                sender.sendMessage("§6=== PackDiag 帮助 ===")
                sender.sendMessage("§7/$label debug watch <player> [秒] §8- 实时播报该玩家的 RAW/EVT 包")
                sender.sendMessage("§7/$label debug unwatch <player>  §8- 停止播报")
                sender.sendMessage("§7/$label debug info <player>    §8- 通道/握手快照 + 判定结论")
                sender.sendMessage("§7/$label debug guard <player>   §8- PacketGuard 键集内省（验证 64 键塞满假说）")
                sender.sendMessage("§7/$label debug stats [player]   §8- 计数汇总")
                sender.sendMessage("§7/$label debug reset [player]   §8- 清零计数")
                sender.sendMessage("§8判定语义: 无RAW=客户端没发包 | 有RAW无EVT=ArcartX解码丢弃 | 有EVT=到达AXS路由")
            }
        }
        return true
    }

    override fun onTabComplete(@NotNull sender: CommandSender, @NotNull args: Array<String>): List<String>? {
        if (args.size == 2) return ACTIONS.filter { it.startsWith(args[1].lowercase(Locale.ROOT)) }
        if (args.size == 3 && args[1].lowercase(Locale.ROOT) in listOf("watch", "unwatch", "info", "stats", "guard", "reset")) {
            return null // 玩家名补全
        }
        return emptyList()
    }
}
