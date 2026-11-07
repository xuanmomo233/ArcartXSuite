package xuanmo.arcartxsuite.qqbot.onebot;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Real loopback WS tests: no QQ account, external service or game server is used. */
@Timeout(15)
class OneBotClientTest {
    private final CountDownLatch eventStarted = new CountDownLatch(1);
    private final CountDownLatch releaseEvent = new CountDownLatch(1);
    private final CountDownLatch connected = new CountDownLatch(1);
    private TestServer server;
    private OneBotClient client;

    @BeforeEach void start() throws Exception {
        server = new TestServer(); server.start();
        assertTrue(server.started.await(5, TimeUnit.SECONDS));
        client = new OneBotClient("ws://127.0.0.1:" + server.getPort(), "secret", 1, 10,
            Logger.getAnonymousLogger(), event -> {
                eventStarted.countDown();
                try { releaseEvent.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }, connected::countDown, reason -> {});
        client.start();
        assertTrue(connected.await(5, TimeUnit.SECONDS));
    }
    @AfterEach void stop() throws Exception {
        releaseEvent.countDown();
        if (client != null) client.shutdown();
        if (server != null) server.stop(1000);
    }
    @Test void acknowledgesEscapedTextAndPreservesRealMentionSegments() throws Exception {
        assertTrue(client.sendGroupMessage(123, "[CQ:at,qq=all]").get(3, TimeUnit.SECONDS).success());
        JsonObject params = server.frames.poll(3, TimeUnit.SECONDS).getAsJsonObject("params");
        assertTrue(params.get("auto_escape").getAsBoolean());
        assertEquals("[CQ:at,qq=all]", params.get("message").getAsString());
        assertTrue(client.sendGroupMessageAt(123, 456, "text").get(3, TimeUnit.SECONDS).success());
        params = server.frames.poll(3, TimeUnit.SECONDS).getAsJsonObject("params");
        assertEquals("at", params.getAsJsonArray("message").get(0).getAsJsonObject().get("type").getAsString());
    }
    @Test void acknowledgementsDoNotWaitForSlowBusinessEvent() throws Exception {
        server.broadcast("{\"post_type\":\"message\",\"message_type\":\"group\",\"user_id\":456,\"group_id\":123,\"message\":\"hello\"}");
        assertTrue(eventStarted.await(3, TimeUnit.SECONDS));
        assertTrue(client.sendGroupMessage(123, "reply").get(3, TimeUnit.SECONDS).success());
        assertEquals(1, releaseEvent.getCount(), "Business handler must still be blocked");
    }
    @Test void reportsRemoteFailureAndBoundsPerGroupTraffic() throws Exception {
        client.setGroupRateLimit(1);
        server.reject.set(true);
        OneBotResult rejected = client.sendGroupMessage(123, "first").get(3, TimeUnit.SECONDS);
        assertFalse(rejected.success()); assertEquals(100, rejected.retcode());
        OneBotResult rateLimited = client.sendGroupMessage(123, "second").get(1, TimeUnit.SECONDS);
        assertFalse(rateLimited.success()); assertTrue(rateLimited.message().contains("频率"));
        assertEquals(0, client.pendingRequests());
    }
    @Test void closingSettlesUnknownDeliveryAndCannotRestartOldClient() throws Exception {
        server.acknowledge.set(false);
        var pending = client.sendGroupMessage(123, "unacknowledged");
        assertNotNull(server.frames.poll(3, TimeUnit.SECONDS));
        client.shutdown(); client.start();
        assertEquals(-2, pending.get(1, TimeUnit.SECONDS).retcode());
        assertFalse(client.isConnected());
        assertFalse(client.sendGroupMessage(123, "after shutdown").get(1, TimeUnit.SECONDS).success());
        assertEquals(0, client.pendingRequests());
    }
    @Test void malformedReplyCannotLoseItsPendingFuture() throws Exception {
        server.malformedFirst.set(true);
        assertTrue(client.sendGroupMessage(123, "hello").get(3, TimeUnit.SECONDS).success());
        assertEquals(0, client.pendingRequests());
    }

    private static final class TestServer extends WebSocketServer {
        final CountDownLatch started = new CountDownLatch(1);
        final BlockingQueue<JsonObject> frames = new LinkedBlockingQueue<>();
        final AtomicBoolean reject = new AtomicBoolean();
        final AtomicBoolean acknowledge = new AtomicBoolean(true);
        final AtomicBoolean malformedFirst = new AtomicBoolean();
        TestServer() { super(new InetSocketAddress("127.0.0.1", 0)); }
        @Override public void onStart() { started.countDown(); }
        @Override public void onOpen(WebSocket socket, ClientHandshake handshake) {
            if (!"Bearer secret".equals(handshake.getFieldValue("Authorization"))) socket.close(1008);
        }
        @Override public void onMessage(WebSocket socket, String text) {
            JsonObject frame = JsonParser.parseString(text).getAsJsonObject(); frames.add(frame);
            if (!acknowledge.get()) return;
            JsonObject response = new JsonObject(); response.add("echo", frame.get("echo"));
            if (malformedFirst.getAndSet(false)) {
                response.add("retcode", new JsonObject()); socket.send(response.toString());
            }
            response.addProperty("retcode", reject.get() ? 100 : 0);
            response.addProperty("status", reject.get() ? "failed" : "ok");
            response.addProperty("wording", "test response"); response.add("data", new JsonObject());
            socket.send(response.toString());
        }
        @Override public void onClose(WebSocket socket, int code, String reason, boolean remote) {}
        @Override public void onError(WebSocket socket, Exception error) {}
    }
}
