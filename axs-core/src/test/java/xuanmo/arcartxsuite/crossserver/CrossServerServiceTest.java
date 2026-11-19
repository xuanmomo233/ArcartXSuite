package xuanmo.arcartxsuite.crossserver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerDelivery;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;

class CrossServerServiceTest {
    @TempDir Path dataFolder;

    @Test
    void redisWaitsForModuleRegistrationAndDoesNotBlockOnAnUnavailableChannel() throws Exception {
        var ready = new java.util.concurrent.CompletableFuture<Void>();
        var leased = new java.util.concurrent.CountDownLatch(1);
        var deliveries = new LinkedBlockingQueue<CrossServerDelivery>();
        var jedis = mock(redis.clients.jedis.Jedis.class);
        when(jedis.scriptLoad(anyString())).thenReturn("checkpoint");
        when(jedis.evalsha(eq("checkpoint"), anyList(), anyList())).thenAnswer(call -> {
            if (call.<List<String>>getArgument(2).get(1).equals("0-0")) leased.countDown();
            return 1L;
        });
        var emitted = new java.util.concurrent.atomic.AtomicBoolean();
        var envelope = new CrossServerEnvelope(1, "chat", "remote", UUID.randomUUID().toString(),
            System.currentTimeMillis(), "积压消息", "");
        var wire = CrossServerEnvelopeCodec.encode(CrossServerSigner.withSignature(envelope, "test-key".getBytes(StandardCharsets.UTF_8)));
        var dormant = new CrossServerEnvelope(1, "dormant", "remote", UUID.randomUUID().toString(),
            System.currentTimeMillis(), "暂存消息", "");
        var dormantWire = CrossServerEnvelopeCodec.encode(
            CrossServerSigner.withSignature(dormant, "test-key".getBytes(StandardCharsets.UTF_8)));
        try (var store = new CrossServerReplayStore(dataFolder.resolve("data/cross-server-replay.db"))) {
            var item = new CrossServerReplayStore.InboxItem(dormant.messageId(), dormant.timestamp() + 60_000, dormantWire);
            store.stage("127.0.0.1:6379/0\nAXS:CROSS\nlocal", "0-0", List.of(item), dormant.timestamp());
        }
        when(jedis.xread(any(redis.clients.jedis.params.XReadParams.class), anyMap())).thenAnswer(call -> {
            if (!emitted.compareAndSet(false, true)) {
                Thread.sleep(10);
                return List.of();
            }
            var stream = call.<Map<String, redis.clients.jedis.StreamEntryID>>getArgument(1).keySet().iterator().next();
            var entry = new redis.clients.jedis.resps.StreamEntry(new redis.clients.jedis.StreamEntryID("1-0"), Map.of("wire", wire));
            return List.of(Map.entry(stream, List.of(entry)));
        });
        try (var bukkit = mockStatic(Bukkit.class);
             var pools = mockConstruction(redis.clients.jedis.JedisPool.class, (pool, context) -> when(pool.getResource()).thenReturn(jedis))) {
            var fixture = new Fixture(bukkit, true, true, "test-key");
            var serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            var previousServer = serverField.get(null);
            serverField.set(null, fixture.server);
            try (fixture) {
                fixture.configuration.set("cross-server.redis.enabled", true);
                fixture.service.start(ready);
                fixture.service.openChannel("early", CrossServerChannelConfig.enabledDefault(), ignored -> {});
                assertTrue(leased.await(5, TimeUnit.SECONDS));
                verify(jedis, never()).xread(any(redis.clients.jedis.params.XReadParams.class), anyMap());
                fixture.service.openChannel("chat", CrossServerChannelConfig.enabledDefault(), deliveries::add);
                ready.complete(null);
                var receiverCallback = fixture.timerTasks.poll(5, TimeUnit.SECONDS);
                assertNotNull(receiverCallback);
                receiverCallback.run();
                var delivered = deliveries.poll(5, TimeUnit.SECONDS);
                assertNotNull(delivered);
                assertEquals("积压消息", delivered.payload());

                fixture.service.openChannel("dormant", CrossServerChannelConfig.enabledDefault(), deliveries::add);
                var recoveryCallback = fixture.timerTasks.poll(5, TimeUnit.SECONDS);
                assertNotNull(recoveryCallback);
                recoveryCallback.run();
                var recovered = deliveries.poll(5, TimeUnit.SECONDS);
                assertNotNull(recovered);
                assertEquals("暂存消息", recovered.payload());
            } finally {
                ready.cancel(false);
                serverField.set(null, previousServer);
            }
        }
    }

    @Test
    void acceptsOnlyCompleteForwardedPayload() throws IOException {
        byte[] forwarded = frame("AXS_CROSS", "流云", 0);
        assertEquals("流云", CrossServerService.decodeProxyPayload(forwarded, "AXS_CROSS"));
        assertNull(CrossServerService.decodeProxyPayload(forwarded, "ANOTHER_CHANNEL"));
        assertNull(CrossServerService.decodeProxyPayload(frame("Forward", "流云", 0), "AXS_CROSS"));
        assertNull(CrossServerService.decodeProxyPayload(frame("AXS_CROSS", "流云", 1), "AXS_CROSS"));
        assertNull(CrossServerService.decodeProxyPayload(frame("AXS_CROSS", "流云", -1), "AXS_CROSS"));
    }

    @Test
    void rejectsDisabledTransportAndModuleBeforeRecordingTheId() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class);
             var fixture = new Fixture(bukkit, true, true, "test-key")) {
            fixture.service.start();
            var deliveries = new LinkedBlockingQueue<CrossServerDelivery>();
            byte[] forwarded = message("fresh", System.currentTimeMillis(), "test-key");
            fixture.service.openChannel("chat", CrossServerChannelConfig.disabled(), deliveries::add);
            fixture.receive(forwarded);
            fixture.service.openChannel("chat", new CrossServerChannelConfig(true, null, false), deliveries::add);
            fixture.receive(forwarded);
            assertTrue(deliveries.isEmpty());

            fixture.service.openChannel("chat", CrossServerChannelConfig.enabledDefault(), deliveries::add);
            fixture.receive(forwarded);
            fixture.receive(forwarded);
            fixture.receive(message("expired", System.currentTimeMillis() - 120_000L, "test-key"));
            fixture.receive(message("future", System.currentTimeMillis() + 30_000L, "test-key"));
            fixture.receive(message("wrong-key", System.currentTimeMillis(), "wrong-key"));
            fixture.receive(message("barrier", System.currentTimeMillis(), "test-key"));
            var fresh = deliveries.poll(10, TimeUnit.SECONDS);
            assertNotNull(fresh);
            assertTrue(fresh.signed());
            assertEquals("fresh", fresh.payload());
            var barrier = deliveries.poll(10, TimeUnit.SECONDS);
            assertNotNull(barrier);
            assertEquals("barrier", barrier.payload());
        }
    }

    @Test
    void failsClosedIfVerificationOrSigningIsRequestedWithoutKey() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            for (boolean signing : List.of(true, false)) {
                try (var fixture = new Fixture(bukkit, signing, !signing, "")) {
                    fixture.service.start();
                    List<CrossServerDelivery> deliveries = new ArrayList<>();
                    var channel = fixture.service.openChannel("chat", CrossServerChannelConfig.enabledDefault(), deliveries::add);
                    channel.publish("outbound");
                    fixture.receive(message("inbound", System.currentTimeMillis(), "test-key"));
                    assertFalse(channel.isActive());
                    assertTrue(deliveries.isEmpty());
                    verifyNoInteractions(fixture.messenger);
                }
            }
        }
    }

    @Test
    void preservesExplicitUnsignedTransport() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class);
             var fixture = new Fixture(bukkit, false, false, "")) {
            fixture.service.start();
            var deliveries = new LinkedBlockingQueue<CrossServerDelivery>();
            var channel = fixture.service.openChannel("chat", CrossServerChannelConfig.enabledDefault(), deliveries::add);
            var unsigned = new CrossServerEnvelope(1, "chat", "remote", UUID.randomUUID().toString(),
                System.currentTimeMillis(), "trusted-network", "");
            fixture.receive(frame("AXS_CROSS", CrossServerEnvelopeCodec.encode(unsigned), 0));
            assertTrue(channel.isActive());
            var delivery = deliveries.poll(10, TimeUnit.SECONDS);
            assertNotNull(delivery);
            assertFalse(delivery.signed());
        }
    }

    @Test
    void rejectsSignedReplayAfterRestartButAcceptsFreshMessages() throws Exception {
        var deliveries = new LinkedBlockingQueue<CrossServerDelivery>();
        byte[] original = message("original", System.currentTimeMillis(), "test-key");
        try (var bukkit = mockStatic(Bukkit.class)) {
            try (var fixture = new Fixture(bukkit, true, true, "test-key")) {
                fixture.service.start();
                fixture.service.openChannel("chat", CrossServerChannelConfig.enabledDefault(), deliveries::add);
                fixture.receive(original);
                var delivery = deliveries.poll(10, TimeUnit.SECONDS);
                assertNotNull(delivery);
                assertEquals("original", delivery.payload());
            }
            try (var restarted = new Fixture(bukkit, true, true, "test-key")) {
                restarted.service.start();
                restarted.service.openChannel("chat", CrossServerChannelConfig.enabledDefault(), deliveries::add);
                restarted.receive(original);
                restarted.receive(message("after-restart", System.currentTimeMillis(), "test-key"));
                var delivery = deliveries.poll(10, TimeUnit.SECONDS);
                assertNotNull(delivery);
                assertEquals("after-restart", delivery.payload());
            }
        }
    }

    @Test
    void batchCommitsOnlyFirstOccurrenceAndPreservesClaimsAfterReopening() throws Exception {
        Path database = dataFolder.resolve("replay.db");
        var first = new CrossServerReplayStore.Claim("a", 1_000);
        var second = new CrossServerReplayStore.Claim("b", 1_000);
        try (var store = new CrossServerReplayStore(database)) {
            assertArrayEquals(new boolean[] { true, false, true }, store.claim(List.of(first, first, second), 1_000, 1_000));
        }
        try (var store = new CrossServerReplayStore(database)) {
            assertArrayEquals(new boolean[] { false, false }, store.claim(List.of(second, first), 2_000, 1_000));
            var fresh = new CrossServerReplayStore.Claim("a", 3_001);
            assertArrayEquals(new boolean[] { true }, store.claim(List.of(fresh), 3_001, 1_000));
        }
    }

    @Test
    void redisCursorAndPendingPayloadSurviveRestartBeforeCallback() throws Exception {
        Path database = dataFolder.resolve("inbox.db");
        var item = new CrossServerReplayStore.InboxItem("item", 60_000, "signed envelope");
        var proxy = new CrossServerReplayStore.Claim("item", 1_000);
        try (var store = new CrossServerReplayStore(database)) {
            assertEquals("0-0", store.cursor("local"));
            assertArrayEquals(new boolean[] { true, false }, store.stage("local", "2000-1", List.of(item, item), 2_000));
        }
        try (var restarted = new CrossServerReplayStore(database)) {
            assertEquals("2000-1", restarted.cursor("local"));
            assertEquals(List.of(item), restarted.pending("local", 0, 128, 8_192).items());
            assertArrayEquals(new boolean[] { false }, restarted.claim(List.of(proxy), 3_000, 60_000));
            restarted.finish(List.of(item));
        }
        try (var restarted = new CrossServerReplayStore(database)) {
            assertTrue(restarted.pending("local", 0, 128, 8_192).items().isEmpty());
            assertArrayEquals(new boolean[] { false }, restarted.stage("local", "3000-1", List.of(item), 3_000));
        }
    }

    @Test
    void outboxCommitsWithinDiskBudgetAndRetainsUnsentWireAcrossRestart() throws Exception {
        Path database = dataFolder.resolve("outbox.db");
        var first = new CrossServerOutboxStore.Write("chat", 6_000, 4, "wire");
        var second = new CrossServerOutboxStore.Write("mail", 8_000, 3, "two");
        try (var store = new CrossServerOutboxStore(database)) {
            assertArrayEquals(new boolean[] {true, false}, store.admit("primary", List.of(first, second), 1_000, 6, 6));
        }
        try (var restarted = new CrossServerOutboxStore(database)) {
            assertTrue(restarted.peek("other", List.of("chat"), 2_000, 16).isEmpty());
            assertTrue(restarted.peek("primary", List.of("mail"), 2_000, 16).isEmpty());
            var heads = restarted.peek("primary", List.of("chat"), 2_000, 16);
            assertEquals(1, heads.size());
            assertEquals("wire", restarted.frames(heads).get(0).wire());
            assertEquals(1, restarted.purgeExpired(6_001));
            assertArrayEquals(new boolean[] {true}, restarted.admit("primary", List.of(second), 6_001, 6, 6));
            var tail = restarted.frames(restarted.peek("primary", List.of("mail"), 6_001, 16));
            restarted.remove(tail, 1);
            assertTrue(restarted.peek("primary", List.of("mail"), 6_001, 16).isEmpty());
        }
    }

    @Test
    void batchedOutboxRetiresOnlyTheAcknowledgedPrefix() throws Exception {
        try (var store = new CrossServerOutboxStore(dataFolder.resolve("batch-outbox.db"))) {
            var first = new CrossServerOutboxStore.Write("chat", 6_000, 4, "wire");
            var second = new CrossServerOutboxStore.Write("chat", 6_000, 3, "two");
            assertArrayEquals(new boolean[] {true, true}, store.admit("scope", List.of(first, second), 1_000, 7, 8));
            var frames = store.frames(store.peek("scope", List.of("chat"), 2_000, 32));
            assertEquals(List.of("wire", "two"), frames.stream().map(CrossServerOutboxStore.Frame::wire).toList());
            store.remove(frames, 1);
            assertEquals(List.of("two"), store.frames(store.peek("scope", List.of("chat"), 2_000, 32))
                .stream().map(CrossServerOutboxStore.Frame::wire).toList());
        }
    }

    @Test
    void utf8AccountingAndChunkedSignatureMatchLegacyEncoding() throws Exception {
        String payload = "é中😀\n" + (char) 0xd800 + "x" + (char) 0xdc00;
        assertEquals(payload.getBytes(StandardCharsets.UTF_8).length, CrossServerEnvelopeCodec.utf8Length(payload));
        var envelope = new CrossServerEnvelope(1, "chat", "node", "id", 123L, payload, "");
        byte[] key = "fixture".getBytes(StandardCharsets.UTF_8);
        var legacy = javax.crypto.Mac.getInstance("HmacSHA256");
        legacy.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
        byte[] signed = legacy.doFinal(("axs-cross-envelope-v1\n1\nchat\nnode\nid\n123\n" + payload)
            .getBytes(StandardCharsets.UTF_8));
        assertEquals(java.util.HexFormat.of().formatHex(signed), CrossServerSigner.sign(envelope, key));
    }

    @Test
    void boundedJsonEncodingPreservesWireAndRejectsBeforeOversizeAppend() {
        var envelope = new CrossServerEnvelope(1, "chat", "node", "id", 123L, "quote \"\n中😀", "");
        String wire = CrossServerEnvelopeCodec.encode(envelope);
        assertEquals(wire, CrossServerEnvelopeCodec.encodeWithin(envelope, wire.length()));
        assertNull(CrossServerEnvelopeCodec.encodeWithin(envelope, wire.length() - 1));
    }

    private static byte[] message(String payload, long timestamp, String secret) throws IOException {
        var envelope = new CrossServerEnvelope(1, "chat", "remote", UUID.randomUUID().toString(), timestamp, payload, "");
        var signed = CrossServerSigner.withSignature(envelope, secret.getBytes(StandardCharsets.UTF_8));
        return frame("AXS_CROSS", CrossServerEnvelopeCodec.encode(signed), 0);
    }

    private static byte[] frame(String channel, String payload, int lengthOffset) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        var output = new ByteArrayOutputStream();
        try (var frame = new DataOutputStream(output)) {
            frame.writeUTF(channel);
            frame.writeShort(bytes.length + lengthOffset);
            frame.write(bytes);
        }
        return output.toByteArray();
    }

    private final class Fixture implements AutoCloseable {
        final Messenger messenger = mock(Messenger.class);
        final Server server = mock(Server.class);
        final LinkedBlockingQueue<Runnable> timerTasks = new LinkedBlockingQueue<>();
        final CrossServerService service;
        final YamlConfiguration configuration = new YamlConfiguration();

        Fixture(MockedStatic<Bukkit> bukkit, boolean sign, boolean verify, String key) {
            var plugin = mock(JavaPlugin.class);
            var scheduler = mock(BukkitScheduler.class);
            var task = mock(BukkitTask.class);
            var console = mock(ConsoleCommandSender.class);
            configuration.set("cross-server.node-id", "local");
            configuration.set("cross-server.proxy.enabled", true);
            configuration.set("cross-server.signature.enabled", sign);
            configuration.set("cross-server.signature.verify", verify);
            configuration.set("cross-server.signature.secret", key);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getConfig()).thenReturn(configuration);
            when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
            when(server.getMessenger()).thenReturn(messenger);
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getConsoleSender()).thenReturn(console);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
                timerTasks.add(call.getArgument(1));
                return task;
            });
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                call.<Runnable>getArgument(1).run();
                return task;
            });
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            this.service = new CrossServerService(plugin);
        }

        void receive(byte[] frame) {
            service.onPluginMessageReceived("BungeeCord", null, frame);
        }

        @Override
        public void close() throws Exception {
            service.shutdownAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }
}
