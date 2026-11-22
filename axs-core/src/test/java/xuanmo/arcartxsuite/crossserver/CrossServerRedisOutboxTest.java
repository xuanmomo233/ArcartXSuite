package xuanmo.arcartxsuite.crossserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;

class CrossServerRedisOutboxTest {
    @TempDir Path directory;

    @Test
    void commitsTwentyFourLargeSubmissionsWhileRedisHoldsADrainBatch() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("cross-server.max-payload-chars", 160 * 1024);
        var config = CrossServerGlobalConfiguration.from(yaml);
        String scope = config.redisHost() + ":" + config.redisPort() + "/" + config.redisDatabase()
            + "\n" + config.redisChannel() + "\n" + config.nodeId();
        Path database = directory.resolve("outbox.db");
        String payload = "x".repeat(128 * 1024);
        long now = System.currentTimeMillis();
        int frameBytes = config.maxPayloadChars() * 3;
        var seeded = new CrossServerOutboxStore.Write("tab", now + 60_000, payload.length(), payload);
        try (var store = new CrossServerOutboxStore(database)) {
            for (boolean inserted : store.admit(scope, Collections.nCopies(32, seeded), now,
                    config.redisOutboxMaxBytes(), frameBytes)) {
                assertTrue(inserted);
            }
        }

        var pool = mock(JedisPool.class);
        var jedis = mock(Jedis.class);
        var stream = mock(CrossServerRedisStream.class);
        when(pool.getResource()).thenReturn(jedis);
        var appending = new CountDownLatch(1);
        var allowRedisReply = new CountDownLatch(1);
        var firstBatchSize = new AtomicInteger();
        when(stream.append(same(jedis), anyList())).thenAnswer(call -> {
            firstBatchSize.compareAndSet(0, call.<List<String>>getArgument(1).size());
            appending.countDown();
            allowRedisReply.await(12, TimeUnit.SECONDS);
            return 0;
        });

        var outbox = new CrossServerRedisOutbox(pool, config, stream, database, frameBytes, () -> Set.of("tab"));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            assertTrue(appending.await(5, TimeUnit.SECONDS));
            var admissions = new ArrayList<CompletableFuture<Boolean>>(24);
            // Keep all submissions outstanding until both publishers have filled their windows.
            try (var lock = DriverManager.getConnection("jdbc:sqlite:" + database);
                 var transaction = lock.createStatement()) {
                transaction.execute("BEGIN IMMEDIATE");
                for (int index = 0; index < 24; index++) {
                    var envelope = new CrossServerEnvelope(CrossServerEnvelope.PROTOCOL_VERSION, "tab", config.nodeId(),
                        UUID.randomUUID().toString(), System.currentTimeMillis(), payload, "");
                    admissions.add(outbox.publish(envelope, outbox.serializationReservation(envelope)).toCompletableFuture());
                }
                transaction.execute("ROLLBACK");
            }
            int committed = 0;
            for (var admitted : admissions) {
                if (admitted.get(5, TimeUnit.SECONDS)) committed++;
            }
            assertEquals(24, committed, "Redis held " + firstBatchSize.get() + " frames");
            try (var read = DriverManager.getConnection("jdbc:sqlite:" + database);
                 var query = read.createStatement();
                 var rows = query.executeQuery("SELECT row_count FROM outbox_usage WHERE id=1")) {
                assertTrue(rows.next());
                assertEquals(56, rows.getInt(1));
            }
        } finally {
            outbox.shutdown();
            allowRedisReply.countDown();
            // Wait for SQLite handles to close before JUnit deletes the directory on Windows.
            for (String name : List.of("admission", "drain")) {
                var field = CrossServerRedisOutbox.class.getDeclaredField(name);
                field.setAccessible(true);
                Thread worker = (Thread) field.get(outbox);
                worker.join(5_000);
                assertFalse(worker.isAlive(), name);
            }
        }
    }
}
