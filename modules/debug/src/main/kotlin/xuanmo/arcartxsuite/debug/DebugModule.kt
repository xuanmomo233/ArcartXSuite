package xuanmo.arcartxsuite.debug

import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.Listener
import xuanmo.arcartxsuite.api.AbstractAXSModule
import xuanmo.arcartxsuite.api.ClientPacketHandler
import xuanmo.arcartxsuite.api.ModuleCommandHandler
import xuanmo.arcartxsuite.api.ModuleDescriptor
import xuanmo.arcartxsuite.api.PacketHandlerSpec
import xuanmo.arcartxsuite.api.config.ModuleConfig
import java.io.File
import java.util.logging.Level

/**
 * Debug Bridge 模块主类。
 *
 * 继承 [AbstractAXSModule]，在 [startService] 中启动 WebSocket 服务端，
 * 通过 JSON-RPC 暴露 ArcartXSuite 的调试能力给外部 MCP Server 调用。
 *
 * 另挂载 [RawChannelDiag]：旁路监听 `arcartx:main` 原始通道字节 +
 * [ClientCustomPacketEvent] 计数，用 `/axs debug watch/info/stats` 定位
 * "个别玩家 UI 按钮失效"的断包层（客户端没发包 / ArcartX 解码丢 / 到路由层）。
 */
class DebugModule : AbstractAXSModule(), ModuleCommandHandler {

    /** 模块配置（在 loadConfiguration 中解析） */
    private var config: DebugConfig? = null

    /** WebSocket 服务端实例 */
    private var webSocketServer: DebugWebSocketServer? = null

    /** 客户端包流拦截/捕获管理器 */
    private var packetCapture: PacketCaptureManager? = null

    /** arcartx:main 原始通道诊断器 */
    private var rawDiag: RawChannelDiag? = null

    /** /axs debug 管理子命令 */
    private var adminCommand: DebugAdminCommand? = null

    override fun descriptor(): ModuleDescriptor =
        ModuleDescriptor.builder("debug")
            .name("ArcartXSuite Debug Bridge")
            .version("1.4.4")
            .mainClass(this::class.java.name)
            .build()

    override fun configSpec(): ModuleConfig = ModuleConfig.builder()
        .configFileName("ArcartXDebug.yml")
        .build()

    override fun loadConfiguration(configFile: File?) {
        if (configFile == null || !configFile.exists()) {
            throw IllegalStateException("ArcartXDebug.yml 配置文件缺失")
        }
        val yaml = YamlConfiguration.loadConfiguration(configFile)
        config = DebugConfig.load(yaml)
    }

    override fun startService() {
        // 通道诊断不依赖 WebSocket，debug 模块启用时始终挂载
        rawDiag = RawChannelDiag(plugin, logger).also { it.start() }
        adminCommand = DebugAdminCommand { rawDiag }

        val cfg = config
            ?: throw IllegalStateException("Debug 配置未加载")

        if (!cfg.enabled) {
            logger.info("Debug Bridge WebSocket 已在配置中禁用，跳过启动（PackDiag 通道诊断仍可用）")
            return
        }

        // 创建包流捕获管理器（环形缓冲）
        packetCapture = PacketCaptureManager(cfg.captureBufferSize, cfg.logPayload)

        // 创建 RPC 路由器并启动 WebSocket 服务端
        val dispatcher = DebugRpcDispatcher(this, packetCapture!!, logger)
        val server = DebugWebSocketServer(cfg.host, cfg.port, cfg.token, dispatcher, logger)
        server.start()

        webSocketServer = server
        logger.info("Debug Bridge 已启动，WebSocket 监听 ${cfg.host}:${cfg.port} (token=${cfg.token})")
    }

    override fun stopService() {
        rawDiag?.let { diag ->
            try {
                diag.shutdown()
            } catch (e: Exception) {
                logger.log(Level.WARNING, "关闭 PackDiag 通道诊断异常: ${e.message}", e)
            }
        }
        rawDiag = null
        adminCommand = null
        // 关闭 WebSocket 服务端
        webSocketServer?.let { server ->
            try {
                server.stop(1000)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "关闭 Debug WebSocket 服务端异常: ${e.message}", e)
            }
        }
        webSocketServer = null
        packetCapture = null
        config = null
        logger.info("Debug Bridge 已停止")
    }

    override fun createPacketHandlerSpec(): PacketHandlerSpec =
        packetCapture?.let { PacketHandlerSpec.of(it) } ?: PacketHandlerSpec.NONE

    override fun createListeners(): List<Listener> =
        rawDiag?.let { listOf(RawPacketEventListener(it)) } ?: emptyList()

    // ── /axs debug 管理子命令 ──────────────────────────────

    override fun commandId(): String = "debug"

    override fun actions(): List<String> =
        adminCommand?.actions() ?: listOf("help")

    override fun onCommand(sender: CommandSender, label: String, args: Array<String>): Boolean =
        adminCommand?.onCommand(sender, label, args) ?: false

    override fun onTabComplete(sender: CommandSender, args: Array<String>): List<String>? =
        adminCommand?.onTabComplete(sender, args)

    // ── 供 RPC 路由器访问的 API 暴露 ──────────────────────────
    // AbstractAXSModule 的 API 字段为 protected，仅子类内部可见，
    // 这里通过 internal 访问器暴露给同模块的 DebugRpcDispatcher 使用。

    /** 当前配置（供 dispatcher 读取），启动后非空 */
    internal fun currentConfig(): DebugConfig? = config

    /** 当前包流捕获管理器，供 RPC 查询 */
    internal fun currentPacketCapture(): PacketCaptureManager? = packetCapture

    /** 原始通道诊断器，供 RPC/命令查询 */
    internal fun currentRawDiag(): RawChannelDiag? = rawDiag

    /** PacketBridgeAPI（可能为 null） */
    internal fun packetBridge() = packetBridge

    /** 模块日志器 */
    internal fun logger() = logger

    /** 宿主插件实例 */
    internal fun plugin() = plugin

    /** 模块私有数据目录 */
    internal fun dataFolder() = dataFolder

    /** 宿主数据目录（plugins/ArcartXSuite/） */
    internal fun pluginDataFolder() = pluginDataFolder

    /** 模块管理能力（由宿主核心注册，供 debug 工具查询/重载模块） */
    internal fun moduleAdmin(): xuanmo.arcartxsuite.api.capability.ModuleAdminCapability? =
        getCapability(xuanmo.arcartxsuite.api.capability.ModuleAdminCapability::class.java)
}
