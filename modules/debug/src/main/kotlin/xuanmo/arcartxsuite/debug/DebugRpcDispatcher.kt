package xuanmo.arcartxsuite.debug

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

/**
 * JSON-RPC 请求路由器。
 *
 * 接收 `{method, params}`，分发到对应的处理逻辑并返回结果对象（由 WebSocketServer
 * 包装为 JSON-RPC 响应）。未识别的 method 抛 [NoSuchMethodException]，
 * 参数错误抛 [IllegalArgumentException]。
 *
 * 涉及 Bukkit API（玩家、UI、命令等）的操作通过 [syncOnMain] 调度到主线程同步执行，
 * 避免在 WebSocket 线程直接调用导致线程安全问题。
 */
class DebugRpcDispatcher(
    private val module: DebugModule,
    private val packetCapture: PacketCaptureManager,
    private val logger: Logger,
) {

    /**
     * 分发一个 RPC 方法。
     *
     * @return 结果对象（可被 Gson 序列化）
     * @throws NoSuchMethodException 方法不存在
     * @throws IllegalArgumentException 参数错误
     */
    fun dispatch(method: String, params: JsonObject): Any = when (method) {
        // ── UI 操控 ──
        "ui.list" -> uiList()
        "ui.read" -> uiRead(params)
        "ui.reload" -> uiReload(params)
        "ui.open" -> uiOpen(params)
        "ui.close" -> uiClose(params)
        "ui.is_open" -> uiIsOpen(params)
        "ui.send_packet" -> uiSendPacket(params)
        // ── ARIA 脚本 ──
        "aria.eval" -> ariaEval(params)
        "aria.available" -> ariaAvailable()
        // ── 模块管理 ──
        "module.list" -> moduleList()
        "module.reload" -> moduleReload(params)
        // ── 配置诊断 ──
        "config.diagnose" -> configDiagnose(params)
        // ── 玩家 ──
        "player.list" -> playerList()
        // ── 服务端命令 ──
        "server.command" -> serverCommand(params)
        // ── 日志 ──
        "log.tail" -> logTail(params)
        // ── 包流捕获 ──
        "packet.get_captured" -> packetGetCaptured(params)
        "packet.capture" -> packetGetCaptured(params)
        "packet.clear" -> packetClear()
        "packet.status" -> packetStatus()
        else -> throw NoSuchMethodException("Method not found: $method")
    }

    // ════════════════════════════════════════════════════════════
    //  UI 操控
    // ════════════════════════════════════════════════════════════

    /** 列出 ui/ 目录所有 .yml 文件 */
    private fun uiList(): Any {
        val uiDir = File(module.pluginDataFolder(), "ui")
        val files = uiDir.listFiles { f -> f.isFile && (f.name.endsWith(".yml") || f.name.endsWith(".yaml")) }
            ?: emptyArray()
        val arr = JsonArray()
        for (f in files.sortedBy { it.name }) {
            arr.add(f.name)
        }
        return ok("uiFiles" to arr)
    }

    /** 读取指定 UI yml 文件内容 (params: uiId) */
    private fun uiRead(params: JsonObject): Any {
        val uiId = requireParam(params, "uiId")
        val base = stripNamespace(uiId)
        val file = File(module.pluginDataFolder(), "ui/$base.yml")
        if (!file.isFile) {
            throw IllegalArgumentException("UI 文件不存在: ui/$base.yml")
        }
        val content = Files.readString(file.toPath())
        return ok("uiId" to base, "content" to content)
    }

    /** 热重载指定 UI (params: uiId) → packetBridge.registerOrReloadUi */
    private fun uiReload(params: JsonObject): Any {
        val uiId = requireParam(params, "uiId")
        val base = stripNamespace(uiId)
        val bridge = module.packetBridge()
            ?: throw IllegalStateException("PacketBridge 不可用")
        val file = File(module.pluginDataFolder(), "ui/$base.yml")
        if (!file.isFile) {
            throw IllegalArgumentException("UI 文件不存在: ui/$base.yml")
        }
        return syncOnMain {
            val result = bridge.registerOrReloadUi(uiId, file)
            ok(
                "success" to result.success(),
                "runtimeUiId" to result.runtimeUiId(),
                "registeredUiId" to result.registeredUiId(),
                "action" to result.action(),
                "message" to result.message(),
            )
        }
    }

    /** 打开 UI (params: player, uiId) → packetBridge.openUi */
    private fun uiOpen(params: JsonObject): Any {
        val player = requirePlayer(params)
        val uiId = requireParam(params, "uiId")
        val bridge = module.packetBridge()
            ?: throw IllegalStateException("PacketBridge 不可用")
        return syncOnMain {
            val success = bridge.openUi(player, uiId)
            ok("success" to success, "message" to if (success) "UI opened" else "Failed to open UI")
        }
    }

    /** 关闭 UI (params: player, uiId) */
    private fun uiClose(params: JsonObject): Any {
        val player = requirePlayer(params)
        val uiId = requireParam(params, "uiId")
        val bridge = module.packetBridge()
            ?: throw IllegalStateException("PacketBridge 不可用")
        return syncOnMain {
            val success = bridge.closeUi(player, uiId)
            ok("success" to success, "message" to if (success) "UI closed" else "Failed to close UI")
        }
    }

    /** 查询 UI 是否打开 (params: player, uiId) */
    private fun uiIsOpen(params: JsonObject): Any {
        val player = requirePlayer(params)
        val uiId = requireParam(params, "uiId")
        val bridge = module.packetBridge()
            ?: throw IllegalStateException("PacketBridge 不可用")
        return syncOnMain {
            ok("open" to bridge.isUiOpen(player, uiId))
        }
    }

    /** 向 UI 发包 (params: player, uiId, handler, payload) */
    private fun uiSendPacket(params: JsonObject): Any {
        val player = requirePlayer(params)
        val uiId = requireParam(params, "uiId")
        val handler = requireParam(params, "handler")
        val payload = if (params.has("payload")) params.get("payload") else null
        val bridge = module.packetBridge()
            ?: throw IllegalStateException("PacketBridge 不可用")
        return syncOnMain {
            val payloadObj: Any? = payload
            val success = bridge.sendPacket(player, uiId, handler, payloadObj)
            ok("success" to success, "message" to if (success) "Packet sent" else "Failed to send packet")
        }
    }

    // ════════════════════════════════════════════════════════════
    //  ARIA 脚本
    // ════════════════════════════════════════════════════════════

    /** 检查 ARIA 是否可用 */
    private fun ariaAvailable(): Any {
        val bridge = module.ariaBridge()
        return ok("available" to bridge.available(), "version" to bridge.version())
    }

    /** 执行 ARIA 脚本 (params: code, bindings?) → ariaBridge.eval */
    private fun ariaEval(params: JsonObject): Any {
        val code = requireParam(params, "code")
        val bridge = module.ariaBridge()
        if (!bridge.available()) {
            throw IllegalStateException("ARIA 脚本运行时不可用")
        }
        val bindings = mutableMapOf<String, Any>()
        if (params.has("bindings") && params.get("bindings").isJsonObject) {
            val bj = params.getAsJsonObject("bindings")
            for (e in bj.entrySet()) {
                bindings[e.key] = e.value
            }
        }
        return syncOnMain {
            val result = bridge.eval(code, bindings)
            ok("result" to result)
        }
    }

    // ════════════════════════════════════════════════════════════
    //  模块管理
    // ════════════════════════════════════════════════════════════

    /** 列出所有已加载模块（通过 data/<moduleId>/ 目录 + ModuleAdminCapability 探测状态） */
    private fun moduleList(): Any {
        val admin = module.moduleAdmin()
        val dataDir = File(module.pluginDataFolder(), "data")
        val ids = dataDir.listFiles { f -> f.isDirectory }?.map { it.name }?.toMutableSet() ?: mutableSetOf()
        // 确保当前 debug 模块也在列表中
        ids.add("debug")
        val arr = JsonArray()
        for (id in ids.sorted()) {
            val obj = JsonObject()
            obj.addProperty("id", id)
            val ready = try {
                admin?.findModule(id)?.map { it.isReady }?.orElse(false) ?: false
            } catch (_: Exception) {
                false
            }
            obj.addProperty("ready", ready)
            arr.add(obj)
        }
        return ok("modules" to arr)
    }

    /** 重载指定模块 (params: moduleId) → module.onReload() */
    private fun moduleReload(params: JsonObject): Any {
        val moduleId = requireParam(params, "moduleId")
        val admin = module.moduleAdmin()
            ?: throw IllegalStateException("ModuleAdminCapability 不可用")
        return syncOnMain {
            admin.reloadModule(moduleId)
            ok("success" to true, "message" to "Module $moduleId reloaded")
        }
    }

    // ════════════════════════════════════════════════════════════
    //  配置诊断
    // ════════════════════════════════════════════════════════════

    /**
     * 对指定模块运行配置诊断。
     *
     * ConfigDiagnosticEngine 不在 axs-api 公开接口中，无法直接调用，
     * 因此返回该模块声明的 [xuanmo.arcartxsuite.api.config.ModuleConfigSpec] 列表，
     * 供外部工具了解模块的配置规约（同步策略、版本、校验规则等）。
     */
    private fun configDiagnose(params: JsonObject): Any {
        val moduleId = requireParam(params, "moduleId")
        val admin = module.moduleAdmin()
            ?: throw IllegalStateException("ModuleAdminCapability 不可用")
        val target = admin.findModule(moduleId)
            .orElseThrow { IllegalArgumentException("模块未加载: $moduleId") }
        val specs = target.configSpecs()
        val arr = JsonArray()
        for (spec in specs) {
            val obj = JsonObject()
            obj.addProperty("ownerId", spec.ownerId())
            obj.addProperty("currentVersion", spec.currentVersion())
            obj.addProperty("versionPath", spec.versionPath())
            obj.addProperty("migrationFolder", spec.migrationFolder())
            val sync = spec.sync()
            obj.addProperty("resourcePath", sync.resourcePath())
            obj.addProperty("targetRelativePath", sync.targetRelativePath())
            val validations = JsonArray()
            for (v in spec.validations()) {
                val vo = JsonObject()
                vo.addProperty("path", v.path())
                vo.addProperty("type", v.type().name)
                vo.addProperty("required", v.required())
                if (v.min() != null) vo.addProperty("min", v.min().toString())
                if (v.max() != null) vo.addProperty("max", v.max().toString())
                if (v.allowedValues() != null) {
                    val av = JsonArray()
                    for (s in v.allowedValues()) av.add(s)
                    vo.add("allowedValues", av)
                }
                validations.add(vo)
            }
            obj.add("validations", validations)
            arr.add(obj)
        }
        return ok("moduleId" to moduleId, "specs" to arr)
    }

    // ════════════════════════════════════════════════════════════
    //  玩家
    // ════════════════════════════════════════════════════════════

    /** 列出在线玩家 */
    private fun playerList(): Any {
        return syncOnMain {
            val arr = JsonArray()
            for (p in Bukkit.getOnlinePlayers()) {
                val obj = JsonObject()
                obj.addProperty("name", p.name)
                obj.addProperty("uuid", p.uniqueId.toString())
                obj.addProperty("world", p.world.name)
                arr.add(obj)
            }
            ok("players" to arr)
        }
    }

    // ════════════════════════════════════════════════════════════
    //  服务端命令
    // ════════════════════════════════════════════════════════════

    /** 执行服务端命令 (params: command, sender?) */
    private fun serverCommand(params: JsonObject): Any {
        val command = requireParam(params, "command")
        val senderName = optionalParam(params, "sender")
        return syncOnMain {
            val sender = if (senderName != null) {
                Bukkit.getPlayerExact(senderName)
                    ?: throw IllegalArgumentException("玩家不在线: $senderName")
            } else {
                Bukkit.getConsoleSender()
            }
            val success = Bukkit.dispatchCommand(sender, command)
            ok("success" to success, "message" to if (success) "Command dispatched" else "Command not found or failed")
        }
    }

    // ════════════════════════════════════════════════════════════
    //  日志
    // ════════════════════════════════════════════════════════════

    /** 获取最近 N 行日志 (params: lines?)，读取服务端 logs/latest.log */
    private fun logTail(params: JsonObject): Any {
        val lines = optionalParam(params, "lines")?.toIntOrNull() ?: 100
        // Bukkit.getWorldContainer() 返回服务端根目录（绝对路径，可靠）
        val serverRoot = Bukkit.getWorldContainer()
        val logFile = File(serverRoot, "logs/latest.log")
        if (!logFile.isFile) {
            throw IllegalArgumentException("日志文件不存在: ${logFile.absolutePath}")
        }
        val all = Files.readAllLines(logFile.toPath())
        val tail = if (all.size <= lines) all else all.subList(all.size - lines, all.size)
        val arr = JsonArray()
        for (l in tail) arr.add(l)
        return ok("lines" to arr, "total" to all.size)
    }

    // ════════════════════════════════════════════════════════════
    //  包流捕获
    // ════════════════════════════════════════════════════════════

    /** 查询捕获的包 (params: limit?) */
    private fun packetGetCaptured(params: JsonObject): Any {
        val limit = optionalParam(params, "limit")?.toIntOrNull() ?: 100
        val records = packetCapture.recent(limit)
        val arr = JsonArray()
        for (r in records) {
            val obj = JsonObject()
            obj.addProperty("timestamp", r.timestamp.toString())
            obj.addProperty("player", r.player)
            obj.addProperty("packetId", r.packetId)
            val data = JsonArray()
            for (d in r.data) data.add(d)
            obj.add("data", data)
            arr.add(obj)
        }
        return ok("packets" to arr, "count" to records.size)
    }

    /** 清空捕获缓冲 */
    private fun packetClear(): Any {
        packetCapture.clear()
        return ok("success" to true, "message" to "Capture buffer cleared")
    }

    /** 捕获状态 */
    private fun packetStatus(): Any {
        return ok("size" to packetCapture.size())
    }

    // ════════════════════════════════════════════════════════════
    //  工具方法
    // ════════════════════════════════════════════════════════════

    /** 将任务同步调度到主线程执行并阻塞等待结果 */
    private fun <T> syncOnMain(supplier: () -> T): T {
        val plugin = module.plugin()
        val future = CompletableFuture<T>()
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                future.complete(supplier())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        })
        return future.join()
    }

    /** 必填字符串参数 */
    private fun requireParam(params: JsonObject, key: String): String {
        if (!params.has(key)) {
            throw IllegalArgumentException("缺少必填参数: $key")
        }
        val v = params.get(key)
        if (v.isJsonNull) {
            throw IllegalArgumentException("参数为 null: $key")
        }
        return if (v.isJsonPrimitive) v.asString else v.toString()
    }

    /** 可选字符串参数 */
    private fun optionalParam(params: JsonObject, key: String): String? {
        if (!params.has(key)) return null
        val v = params.get(key)
        return if (v.isJsonNull) null else if (v.isJsonPrimitive) v.asString else v.toString()
    }

    /** 查找在线玩家（必填 player 参数） */
    private fun requirePlayer(params: JsonObject): org.bukkit.entity.Player {
        val name = requireParam(params, "player")
        return Bukkit.getPlayerExact(name)
            ?: throw IllegalArgumentException("玩家不在线: $name")
    }

    /** 去掉 UI id 的命名空间前缀（如 "AXS:menu_panel" → "menu_panel"） */
    private fun stripNamespace(uiId: String): String {
        val idx = uiId.indexOf(':')
        return if (idx >= 0) uiId.substring(idx + 1) else uiId
    }

    /** 构造成功结果 JsonObject，支持可变数量的 (key, value) 对 */
    private fun ok(vararg pairs: Pair<String, Any?>): JsonObject {
        val obj = JsonObject()
        for ((k, v) in pairs) {
            when (v) {
                null -> { obj.add(k, com.google.gson.JsonNull.INSTANCE) }
                is Boolean -> obj.addProperty(k, v)
                is Number -> obj.addProperty(k, v)
                is String -> obj.addProperty(k, v)
                is JsonArray -> obj.add(k, v)
                is JsonObject -> obj.add(k, v)
                else -> obj.addProperty(k, v.toString())
            }
        }
        return obj
    }
}
