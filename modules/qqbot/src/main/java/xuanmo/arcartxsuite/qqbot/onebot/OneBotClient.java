package xuanmo.arcartxsuite.qqbot.onebot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * OneBot v11 协议的 WebSocket 客户端封装。
 * <p>
 * 关联模块：{@code qqbot}。负责与 OneBot 实现（如 SnowLuma）建立 WebSocket 长连接，
 * 支持自动重连与心跳保活，提供发送群消息、@群员、撤回消息、禁言等操作。
 * 收到的事件经 {@link OneBotEvent} 包装后回调给上层处理器。
 */
public final class OneBotClient {

    /** 断开原因：远端主动关闭 */
    public static final String REASON_REMOTE_CLOSED = "远端关闭连接";
    /** 断开原因：连接被拒绝（OneBot 服务未启动或地址端口错误） */
    public static final String REASON_CONNECTION_REFUSED = "连接被拒绝（OneBot 服务未启动或地址端口错误）";
    /** 断开原因：连接超时 */
    public static final String REASON_TIMEOUT = "连接超时";
    /** 断开原因：未知/其他 */
    public static final String REASON_UNKNOWN = "连接断开";

    private final String wsUrl;
    private final String accessToken;
    private final int reconnectIntervalSeconds;
    private final int heartbeatIntervalSeconds;
    private final Logger logger;
    private final Consumer<OneBotEvent> eventHandler;
    private final Runnable onConnected;
    private final Consumer<String> onDisconnected;

    private volatile WebSocketClient wsClient;
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private volatile String lastErrorReason;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "OneBot-Client-Scheduler");
        t.setDaemon(true);
        return t;
    });

    /**
     * 构造 OneBot 客户端。
     *
     * @param wsUrl                    OneBot WebSocket 服务地址
     * @param accessToken              鉴权令牌，为空表示不鉴权
     * @param reconnectIntervalSeconds 重连间隔（秒）
     * @param heartbeatIntervalSeconds 心跳间隔（秒），{@code <=0} 表示不发送心跳
     * @param logger                   日志器
     * @param eventHandler             事件回调处理器
     * @param onConnected              连接成功回调，可为 {@code null}
     * @param onDisconnected           连接断开回调，参数为断开原因描述，可为 {@code null}
     */
    public OneBotClient(
        String wsUrl,
        String accessToken,
        int reconnectIntervalSeconds,
        int heartbeatIntervalSeconds,
        Logger logger,
        Consumer<OneBotEvent> eventHandler,
        Runnable onConnected,
        Consumer<String> onDisconnected
    ) {
        this.wsUrl = wsUrl;
        this.accessToken = accessToken;
        this.reconnectIntervalSeconds = reconnectIntervalSeconds;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.logger = logger;
        this.eventHandler = eventHandler;
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
    }

    /**
     * 启动客户端并发起首次连接。重复调用将被忽略。
     */
    public void start() {
        if (running) return;
        running = true;
        connect();
    }

    /**
     * 关闭客户端，停止重连调度并断开当前连接。
     */
    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        disconnect();
    }

    /**
     * 向指定群发送纯文本消息。未连接时静默忽略。
     *
     * @param groupId 目标群号
     * @param message 消息文本
     */
    public void sendGroupMessage(long groupId, String message) {
        if (!connected || wsClient == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "send_group_msg");
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("message", message);
        payload.add("params", params);
        wsClient.send(payload.toString());
    }

    /**
     * 向指定群发送 @ 某群员 + 文本的消息（消息段数组形式）。未连接时静默忽略。
     *
     * @param groupId 目标群号
     * @param qqId    被 @ 的 QQ 号
     * @param message 附加文本，为空时仅发送 @ 段
     */
    public void sendGroupMessageAt(long groupId, long qqId, String message) {
        if (!connected || wsClient == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "send_group_msg");
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        // 使用消息段数组发送 @ + 文本
        JsonArray segments = new JsonArray();
        JsonObject atSeg = new JsonObject();
        atSeg.addProperty("type", "at");
        JsonObject atData = new JsonObject();
        atData.addProperty("qq", String.valueOf(qqId));
        atSeg.add("data", atData);
        segments.add(atSeg);
        if (message != null && !message.isEmpty()) {
            JsonObject textSeg = new JsonObject();
            textSeg.addProperty("type", "text");
            JsonObject textData = new JsonObject();
            textData.addProperty("text", message);
            textSeg.add("data", textData);
            segments.add(textSeg);
        }
        params.add("message", segments);
        payload.add("params", params);
        wsClient.send(payload.toString());
    }

    /**
     * 撤回指定消息。未连接时静默忽略。
     *
     * @param messageId 待撤回的消息 ID
     */
    public void deleteMessage(int messageId) {
        if (!connected || wsClient == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "delete_msg");
        JsonObject params = new JsonObject();
        params.addProperty("message_id", messageId);
        payload.add("params", params);
        wsClient.send(payload.toString());
    }

    /**
     * 在指定群禁言某成员。未连接时静默忽略。
     *
     * @param groupId    目标群号
     * @param qqId       被禁言的 QQ 号
     * @param durationSec 禁言时长（秒）
     */
    public void setGroupBan(long groupId, long qqId, int durationSec) {
        if (!connected || wsClient == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "set_group_ban");
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("user_id", qqId);
        params.addProperty("duration", durationSec);
        payload.add("params", params);
        wsClient.send(payload.toString());
    }

    private void connect() {
        if (!running) return;
        try {
            disconnect();
            URI uri = URI.create(wsUrl);
            wsClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    logger.info("[OneBot] WebSocket 已连接: " + wsUrl);
                    if (onConnected != null) onConnected.run();
                    startHeartbeat();
                }

                @Override
                public void onMessage(String message) {
                    try {
                        JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                        if (eventHandler != null) {
                            eventHandler.accept(new OneBotEvent(json));
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "[OneBot] 消息解析失败: " + message, e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    // 忽略重连过程中旧 client 的延迟回调，避免误报断开
                    if (OneBotClient.this.wsClient != this) return;
                    connected = false;
                    String reasonText = resolveDisconnectReason(remote);
                    if (onDisconnected != null) {
                        try { onDisconnected.accept(reasonText); } catch (Exception ignored) {}
                    }
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    // 忽略重连过程中旧 client 的延迟回调
                    if (OneBotClient.this.wsClient != this) return;
                    lastErrorReason = ex.getMessage();
                    // 堆栈降级为 FINE 调试级，默认不显示，开启 debug 时可查
                    logger.log(Level.FINE, "[OneBot] WebSocket 异常详情", ex);
                }

                @Override
                public void run() {
                    // 端口探测：在库线程内先检查 TCP 端口是否监听。
                    // 不通则直接触发 onClose 断开回调 + 重连调度，
                    // 不调用 super.run()，避免库内部 slf4j 打印 ConnectException 堆栈
                    // （WebSocketImpl.closeConnection 的 log.error 无法在客户端侧拦截）。
                    // 探测与连接在同一线程，竞态窗口极小。
                    if (!probePort()) {
                        onClose(-1, REASON_CONNECTION_REFUSED, false);
                        return;
                    }
                    try {
                        super.run();
                    } catch (Throwable t) {
                        lastErrorReason = t.getMessage();
                        logger.log(Level.FINE, "[OneBot] WebSocket run 线程异常兜底", t);
                    }
                }
            };
            if (accessToken != null && !accessToken.isEmpty()) {
                wsClient.addHeader("Authorization", "Bearer " + accessToken);
            }
            wsClient.connect();
        } catch (Exception e) {
            lastErrorReason = e.getMessage();
            logger.log(Level.FINE, "[OneBot] 连接失败详情", e);
            if (onDisconnected != null) {
                try { onDisconnected.accept(resolveDisconnectReason(false)); } catch (Exception ignored) {}
            }
            scheduleReconnect();
        }
    }

    /**
     * 根据远端标志与最近一次异常信息推断断开原因描述。
     *
     * @param remote 是否由远端关闭
     * @return 断开原因描述字符串
     */
    private String resolveDisconnectReason(boolean remote) {
        if (remote) return REASON_REMOTE_CLOSED;
        String err = lastErrorReason;
        lastErrorReason = null;
        if (err != null && !err.isEmpty()) {
            if (err.contains("Connection refused")) return REASON_CONNECTION_REFUSED;
            String lower = err.toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out")) return REASON_TIMEOUT;
            return REASON_UNKNOWN + ": " + err;
        }
        return REASON_UNKNOWN;
    }

    /**
     * 探测 OneBot WebSocket 服务端口是否可连。
     * <p>
     * 用独立 Socket 做一次 TCP connect（超时 1 秒），成功立即关闭。
     * 用于在启动 java-websocket 线程前过滤掉"服务未启动"的情况，
     * 避免库内部 slf4j 打印 ConnectException 堆栈刷屏。
     *
     * @return 端口可连返回 true；拒绝/超时/URL 解析失败返回 false
     */
    private boolean probePort() {
        try {
            URI uri = URI.create(wsUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || port <= 0) {
                logger.log(Level.FINE, "[OneBot] 端口探测跳过：ws-url 缺少 host/port: " + wsUrl);
                return true; // 无法解析时不拦截，交由后续 connect 正常处理
            }
            try (java.net.Socket probe = new java.net.Socket()) {
                probe.connect(new java.net.InetSocketAddress(host, port), 1000);
            }
            return true;
        } catch (Exception e) {
            logger.log(Level.FINE, "[OneBot] 端口探测失败: " + e.getMessage());
            return false;
        }
    }

    private void disconnect() {
        connected = false;
        if (wsClient != null) {
            try {
                wsClient.close();
            } catch (Exception ignored) {}
            wsClient = null;
        }
    }

    private void scheduleReconnect() {
        if (!running) return;
        try {
            scheduler.schedule(this::connect, reconnectIntervalSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[OneBot] 重连调度失败", e);
        }
    }

    private void startHeartbeat() {
        // OneBot v11 心跳由服务端（SnowLuma/NapCat 等）主动推送给客户端，
        // 客户端无需主动发送心跳事件。连接存活检测依赖 WebSocket 的 onClose/onError 回调。
        // heartbeatIntervalSeconds 参数保留以兼容现有配置接口，但不再发送心跳包。
    }
}
