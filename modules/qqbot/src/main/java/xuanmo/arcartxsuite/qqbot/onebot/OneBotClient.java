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

public final class OneBotClient {

    private final String wsUrl;
    private final String accessToken;
    private final int reconnectIntervalSeconds;
    private final int heartbeatIntervalSeconds;
    private final Logger logger;
    private final Consumer<OneBotEvent> eventHandler;
    private final Runnable onConnected;
    private final Runnable onDisconnected;

    private volatile WebSocketClient wsClient;
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private volatile boolean firstDisconnectLogged = false;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "OneBot-Client-Scheduler");
        t.setDaemon(true);
        return t;
    });

    public OneBotClient(
        String wsUrl,
        String accessToken,
        int reconnectIntervalSeconds,
        int heartbeatIntervalSeconds,
        Logger logger,
        Consumer<OneBotEvent> eventHandler,
        Runnable onConnected,
        Runnable onDisconnected
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

    public void start() {
        if (running) return;
        running = true;
        connect();
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        disconnect();
    }

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

    public void deleteMessage(int messageId) {
        if (!connected || wsClient == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "delete_msg");
        JsonObject params = new JsonObject();
        params.addProperty("message_id", messageId);
        payload.add("params", params);
        wsClient.send(payload.toString());
    }

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
                    firstDisconnectLogged = false;
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
                    connected = false;
                    if (!firstDisconnectLogged) {
                        logger.warning("[OneBot] WebSocket 断开: code=" + code + ", reason=" + reason);
                        firstDisconnectLogged = true;
                    }
                    if (onDisconnected != null) onDisconnected.run();
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    if (!firstDisconnectLogged) {
                        logger.log(Level.WARNING, "[OneBot] WebSocket 错误: " + ex.getMessage(), ex);
                        firstDisconnectLogged = true;
                    }
                }
            };
            if (accessToken != null && !accessToken.isEmpty()) {
                wsClient.addHeader("Authorization", "Bearer " + accessToken);
            }
            wsClient.connect();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[OneBot] 连接失败: " + e.getMessage(), e);
            scheduleReconnect();
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
        if (heartbeatIntervalSeconds <= 0) return;
        scheduler.scheduleAtFixedRate(() -> {
            if (wsClient != null && connected) {
                wsClient.send("{\"post_type\":\"meta_event\",\"meta_event_type\":\"heartbeat\"}");
            }
        }, heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);
    }
}
