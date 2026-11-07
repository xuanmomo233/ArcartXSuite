package xuanmo.arcartxsuite.qqbot.onebot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Universal WS transport. Socket responses never wait on business SQL: events use a bounded worker.
 * Missing acknowledgements are unknown deliveries and are never automatically replayed.
 */
public final class OneBotClient implements AutoCloseable {
    public static final String REASON_REMOTE_CLOSED = "远端关闭连接";
    public static final String REASON_CONNECTION_REFUSED = "连接被拒绝（请检查实现端与端口）";
    public static final String REASON_TIMEOUT = "连接超时";
    public static final String REASON_UNKNOWN = "连接断开";
    private static final int MAX_PENDING = 128;
    private static final int MAX_FRAME_CHARS = 262144;
    private static final long REQUEST_TIMEOUT_SECONDS = 10;
    private final URI uri;
    private final String accessToken;
    private final int reconnectSeconds;
    private final int heartbeatSeconds;
    private final Logger logger;
    private final Consumer<OneBotEvent> eventHandler;
    private final Runnable onConnected;
    private final Consumer<String> onDisconnected;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, CompletableFuture<OneBotResult>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "QQBot-Transport"); thread.setDaemon(true); return thread;
    });
    private final ThreadPoolExecutor events = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(256), r -> {
            Thread thread = new Thread(r, "QQBot-Events"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    private volatile WebSocketClient wsClient;
    private volatile boolean running;
    private volatile boolean connected;
    private ScheduledFuture<?> reconnectTask;
    private volatile xuanmo.arcartxsuite.qqbot.service.SlidingWindowRateLimiter<Long> groupBudget =
        new xuanmo.arcartxsuite.qqbot.service.SlidingWindowRateLimiter<>(30, 60000L, 1024);

    public OneBotClient(String wsUrl, String accessToken, int reconnectIntervalSeconds,
                        int heartbeatIntervalSeconds, Logger logger, Consumer<OneBotEvent> eventHandler,
                        Runnable onConnected, Consumer<String> onDisconnected) {
        this.uri = URI.create(wsUrl);
        if (!("ws".equalsIgnoreCase(uri.getScheme()) || "wss".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("OneBot 地址必须是有效的 ws:// 或 wss:// 地址");
        }
        this.accessToken = accessToken == null ? "" : accessToken;
        this.reconnectSeconds = Math.max(1, Math.min(300, reconnectIntervalSeconds));
        this.heartbeatSeconds = Math.max(10, Math.min(300, heartbeatIntervalSeconds));
        this.logger = logger;
        this.eventHandler = eventHandler;
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
    }

    public synchronized void start() {
        if (closed.get() || running) return;
        running = true;
        connect();
    }
    public boolean isConnected() { return connected && !closed.get(); }
    public int pendingRequests() { return pending.size(); }
    public int queuedEvents() { return events.getQueue().size(); }
    public void setGroupRateLimit(int perMinute) {
        groupBudget = new xuanmo.arcartxsuite.qqbot.service.SlidingWindowRateLimiter<>(
            Math.max(1, Math.min(300, perMinute)), 60000L, 1024);
    }
    @Override public void close() { shutdown(); }

    public synchronized void shutdown() {
        if (!closed.compareAndSet(false, true)) return;
        running = false;
        connected = false;
        if (reconnectTask != null) reconnectTask.cancel(false);
        WebSocketClient old = wsClient;
        wsClient = null;
        if (old != null) old.close();
        failPending(-2, "连接已关闭，在途消息结果未知，请勿自动重发");
        events.shutdownNow();
        scheduler.shutdownNow();
    }

    /** Untrusted strings are text, never executable CQ segments. */
    public CompletableFuture<OneBotResult> sendGroupMessage(long groupId, String message) {
        JsonObject params = new JsonObject();
        params.addProperty("group_id", groupId);
        params.addProperty("message", message);
        params.addProperty("auto_escape", true);
        return groupId <= 0 ? failed("群号无效") : request("send_group_msg", params);
    }

    public CompletableFuture<OneBotResult> sendGroupMessageAt(long groupId, long qqId, String message) {
        if (groupId <= 0 || qqId <= 0) return failed("群号或 QQ 号无效");
        JsonArray segments = new JsonArray();
        JsonObject atData = new JsonObject(); atData.addProperty("qq", String.valueOf(qqId));
        JsonObject at = new JsonObject(); at.addProperty("type", "at"); at.add("data", atData);
        segments.add(at);
        JsonObject textData = new JsonObject(); textData.addProperty("text", message == null ? "" : message);
        JsonObject text = new JsonObject(); text.addProperty("type", "text"); text.add("data", textData);
        segments.add(text);
        JsonObject params = new JsonObject(); params.addProperty("group_id", groupId); params.add("message", segments);
        return request("send_group_msg", params);
    }
    public CompletableFuture<OneBotResult> deleteMessage(int messageId) {
        JsonObject params = new JsonObject(); params.addProperty("message_id", messageId);
        return request("delete_msg", params);
    }
    public CompletableFuture<OneBotResult> setGroupBan(long groupId, long qqId, int durationSec) {
        if (groupId <= 0 || qqId <= 0) return failed("群号或 QQ 号无效");
        JsonObject params = new JsonObject(); params.addProperty("group_id", groupId);
        params.addProperty("user_id", qqId); params.addProperty("duration", Math.max(0, Math.min(2592000, durationSec)));
        return request("set_group_ban", params);
    }
    private static CompletableFuture<OneBotResult> failed(String message) {
        return CompletableFuture.completedFuture(OneBotResult.failure(-1, message));
    }

    private synchronized CompletableFuture<OneBotResult> request(String action, JsonObject params) {
        WebSocketClient socket = wsClient;
        if (!isConnected() || socket == null || !socket.isOpen()) return failed("机器人未连接，消息未发送");
        if (pending.size() >= MAX_PENDING) return failed("机器人发送队列已满，请稍后重试");
        if ("send_group_msg".equals(action)
                && !groupBudget.allow(params.get("group_id").getAsLong(), System.nanoTime() / 1_000_000L))
            return failed("该群发送频率已达上限，请稍后重试");
        String echo = UUID.randomUUID().toString();
        CompletableFuture<OneBotResult> result = new CompletableFuture<>();
        pending.put(echo, result);
        try {
            ScheduledFuture<?> timeout = scheduler.schedule(() -> {
                CompletableFuture<OneBotResult> waiting = pending.remove(echo);
                if (waiting != null) waiting.complete(OneBotResult.failure(-2, "等待机器人回执超时，结果未知，请先核对群消息"));
            }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            result.whenComplete((value, error) -> { pending.remove(echo, result); timeout.cancel(false); });
            JsonObject payload = new JsonObject(); payload.addProperty("action", action);
            payload.add("params", params); payload.addProperty("echo", echo);
            socket.send(payload.toString());
        } catch (RuntimeException error) {
            pending.remove(echo);
            result.complete(OneBotResult.failure(-2, "发送连接发生异常，结果未知，请先核对群消息"));
        }
        return result;
    }

    private synchronized void connect() {
        if (!running || closed.get()) return;
        WebSocketClient old = wsClient;
        wsClient = null;
        if (old != null) old.close();
        WebSocketClient socket = new WebSocketClient(uri) {
            @Override public void onOpen(ServerHandshake handshake) {
                if (closed.get() || !running || wsClient != this) { close(); return; }
                connected = true;
                logger.info("[OneBot] WebSocket 已连接");
                if (onConnected != null) onConnected.run();
            }
            @Override public void onMessage(String message) {
                if (!closed.get() && wsClient == this) receive(message);
            }
            @Override public void onClose(int code, String reason, boolean remote) {
                if (closed.get() || wsClient != this) return;
                connected = false;
                failPending(-2, "连接断开，在途消息结果未知，请先核对后再操作");
                String detail = code == 1008 || code == 403 || code == 401
                    ? "鉴权失败，请核对 OneBot access-token" : (remote ? REASON_REMOTE_CLOSED : REASON_UNKNOWN);
                if (onDisconnected != null) onDisconnected.accept(detail);
                scheduleReconnect();
            }
            @Override public void onError(Exception error) { logger.log(Level.FINE, "[OneBot] 连接异常", error); }
        };
        socket.setConnectionLostTimeout(heartbeatSeconds);
        if (!accessToken.isBlank()) socket.addHeader("Authorization", "Bearer " + accessToken);
        wsClient = socket;
        try { socket.connect(); }
        catch (RuntimeException error) {
            connected = false;
            if (onDisconnected != null) onDisconnected.accept(REASON_CONNECTION_REFUSED);
            scheduleReconnect();
        }
    }

    private void receive(String message) {
        if (message.length() > MAX_FRAME_CHARS) { logger.warning("[OneBot] 忽略超长协议消息"); return; }
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            if (json.has("echo") && json.has("retcode")) {
                String echo = json.get("echo").getAsString();
                if (!pending.containsKey(echo)) return;
                int code = json.get("retcode").getAsInt();
                String status = json.has("status") ? json.get("status").getAsString() : "failed";
                JsonObject data = json.has("data") && json.get("data").isJsonObject() ? json.getAsJsonObject("data") : new JsonObject();
                String detail = json.has("wording") && !json.get("wording").isJsonNull() ? json.get("wording").getAsString()
                    : json.has("message") && !json.get("message").isJsonNull() ? json.get("message").getAsString() : "机器人拒绝操作（" + code + "）";
                CompletableFuture<OneBotResult> result = pending.remove(echo);
                if (result != null) result.complete(code == 0 && "ok".equals(status) ? OneBotResult.success(data) : OneBotResult.failure(code, detail));
                return;
            }
            OneBotEvent event = new OneBotEvent(json);
            if (!(event.isGroupMessage() || event.isNotice())) return;
            try {
                events.execute(() -> {
                    if (closed.get()) return;
                    try { if (eventHandler != null) eventHandler.accept(event); }
                    catch (Exception error) { logger.log(Level.WARNING, "[OneBot] 业务事件处理失败", error); }
                });
            } catch (java.util.concurrent.RejectedExecutionException overload) {
                if (!closed.get()) {
                    logger.warning("[OneBot] 事件队列已满，暂停连接；请检查数据库和群消息频率");
                    WebSocketClient socket = wsClient; if (socket != null) socket.close();
                }
            }
        } catch (RuntimeException error) {
            // Do not print raw frames containing player data or authentication material.
            logger.log(Level.FINE, "[OneBot] 忽略无效协议消息", error);
        }
    }

    private synchronized void scheduleReconnect() {
        if (!running || closed.get() || (reconnectTask != null && !reconnectTask.isDone())) return;
        reconnectTask = scheduler.schedule(() -> {
            synchronized (OneBotClient.this) { reconnectTask = null; }
            connect();
        }, reconnectSeconds, TimeUnit.SECONDS);
    }
    private void failPending(int code, String reason) {
        pending.forEach((echo, future) -> future.complete(OneBotResult.failure(code, reason)));
        pending.clear();
    }
}
