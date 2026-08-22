package xuanmo.arcartxsuite.debug

import org.bukkit.configuration.file.YamlConfiguration
import xuanmo.arcartxsuite.api.AbstractAXSModule
import xuanmo.arcartxsuite.api.ClientPacketHandler
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
 */
class DebugModule : AbstractAXSModule() {

    /** 模块配置（在 loadConfiguration 中解析） */
    private var config: DebugConfig? = null

    /** WebSocket 服务端实例 */
    private var webSocketServer: DebugWebSocketServer? = null

    /** 客户端包流拦截/捕获管理器 */
    private var packetCapture: PacketCaptureManager? = null

    override fun descriptor(): ModuleDescriptor =
        ModuleDescriptor.builder("debug")
            .name("ArcartXSuite Debug Bridge")
            .version("1.0.0")
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
        val cfg = config
            ?: throw IllegalStateException("Debug 配置未加载")

        if (!cfg.enabled) {
            logger.info("Debug Bridge 已在配置中禁用，跳过 WebSocket 启动")
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

    // ── 供 RPC 路由器访问的 API 暴露 ──────────────────────────
    // AbstractAXSModule 的 API 字段为 protected，仅子类内部可见，
    // 这里通过 internal 访问器暴露给同模块的 DebugRpcDispatcher 使用。

    /** 当前配置（供 dispatcher 读取），启动后非空 */
    internal fun currentConfig(): DebugConfig? = config

    /** 当前包流捕获管理器，供 RPC 查询 */
    internal fun currentPacketCapture(): PacketCaptureManager? = packetCapture

    /** PacketBridgeAPI（可能为 null） */
    internal fun packetBridge() = packetBridge

    /** AriaBridge（永不为 null，但 available() 可能为 false） */
    internal fun ariaBridge() = ariaBridge

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
