package xuanmo.arcartxsuite.debug

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Debug Bridge 的 WebSocket 服务端。
 *
 * 监听配置端口，连接时通过 URL 参数 `?token=xxx` 进行 token 认证，
 * 接收 JSON-RPC 请求文本帧，委托给 [DebugRpcDispatcher] 分发处理后回写响应。
 *
 * 协议示例：
 * - 请求: `{"id":"req_001","method":"ui.is_open","params":{"player":"Steve","uiId":"AXS:market_shop"}}`
 * - 响应: `{"id":"req_001","result":{"open":true}}`
 * - 错误: `{"id":"req_001","error":{"code":-32601,"message":"Method not found"}}`
 */
class DebugWebSocketServer(
    host: String,
    port: Int,
    private val token: String,
    private val dispatcher: DebugRpcDispatcher,
    private val logger: Logger,
) : WebSocketServer(InetSocketAddress(host, port)) {

    private val gson: Gson = Gson()

    override fun onStart() {
        logger.info("Debug WebSocket 服务端已就绪，等待连接 (token=${token})")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        // Token 认证：从连接 URL 的 query 参数中提取 token
        val uri = handshake.resourceDescriptor
        val provided = extractToken(uri)
        if (provided == null || provided != token) {
            logger.warning("Debug WebSocket 拒绝连接: token 认证失败 (uri=$uri)")
            conn.close(4401, "Unauthorized: invalid or missing token")
            return
        }
        logger.info("Debug WebSocket 客户端已连接: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        logger.fine("Debug WebSocket 客户端断开: ${conn.remoteSocketAddress} code=$code reason=$reason")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        val response = handleRpc(message)
        conn.send(response)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        logger.log(Level.WARNING, "Debug WebSocket 异常: ${ex.message}", ex)
    }

    /** 解析并分发一条 JSON-RPC 请求，返回 JSON 响应字符串 */
    private fun handleRpc(raw: String): String {
        val id: String
        val method: String
        val params: JsonObject
        try {
            val root = JsonParser.parseString(raw).asJsonObject
            id = if (root.has("id")) root.get("id").asString else "unknown"
            if (!root.has("method")) {
                return errorResponse(id, -32600, "Invalid Request: missing method")
            }
            method = root.get("method").asString
            params = if (root.has("params") && root.get("params").isJsonObject) {
                root.getAsJsonObject("params")
            } else {
                JsonObject()
            }
        } catch (e: Exception) {
            return errorResponse("unknown", -32700, "Parse error: ${e.message}")
        }

        return try {
            val result = dispatcher.dispatch(method, params)
            successResponse(id, result)
        } catch (e: NoSuchMethodException) {
            errorResponse(id, -32601, "Method not found: $method")
        } catch (e: IllegalArgumentException) {
            errorResponse(id, -32602, "Invalid params: ${e.message}")
        } catch (e: Exception) {
            errorResponse(id, -32603, "Internal error: ${e.message}")
        }
    }

    private fun successResponse(id: String, result: Any?): String {
        val resp = JsonObject()
        resp.addProperty("id", id)
        resp.add("result", gson.toJsonTree(result))
        return gson.toJson(resp)
    }

    private fun errorResponse(id: String, code: Int, message: String): String {
        val resp = JsonObject()
        resp.addProperty("id", id)
        val err = JsonObject()
        err.addProperty("code", code)
        err.addProperty("message", message)
        resp.add("error", err)
        return gson.toJson(resp)
    }

    /** 从 URI（如 `/?token=xxx`）中提取 token 参数 */
    private fun extractToken(uri: String?): String? {
        if (uri == null) return null
        val queryStart = uri.indexOf('?')
        if (queryStart < 0) return null
        val query = uri.substring(queryStart + 1)
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq) == "token") {
                return pair.substring(eq + 1)
            }
        }
        return null
    }
}
