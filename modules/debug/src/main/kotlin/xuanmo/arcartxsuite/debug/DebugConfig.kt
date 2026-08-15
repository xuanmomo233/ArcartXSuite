package xuanmo.arcartxsuite.debug

import org.bukkit.configuration.file.YamlConfiguration

/**
 * Debug Bridge 模块配置。
 *
 * 从 [ArcartXDebug.yml] 解析，包含 WebSocket 监听参数与包流捕获设置。
 */
data class DebugConfig(
    /** 是否启用调试桥 */
    val enabled: Boolean,
    /** WebSocket 监听地址 */
    val host: String,
    /** WebSocket 监听端口 */
    val port: Int,
    /** 认证 token（连接时 URL 参数 ?token=xxx） */
    val token: String,
    /** 包流捕获环形缓冲容量 */
    val captureBufferSize: Int,
    /** 是否在捕获时记录包负载内容 */
    val logPayload: Boolean,
) {
    companion object {
        fun load(yaml: YamlConfiguration): DebugConfig {
            val enabled = yaml.getBoolean("debug.enabled", true)
            val host = yaml.getString("websocket.host") ?: "0.0.0.0"
            val port = yaml.getInt("websocket.port", 18899)
            val token = yaml.getString("websocket.token") ?: "axs-debug-local"
            val captureBufferSize = yaml.getInt("packet-capture.buffer-size", 256)
            val logPayload = yaml.getBoolean("packet-capture.log-payload", true)
            return DebugConfig(enabled, host, port, token, captureBufferSize, logPayload)
        }
    }
}
