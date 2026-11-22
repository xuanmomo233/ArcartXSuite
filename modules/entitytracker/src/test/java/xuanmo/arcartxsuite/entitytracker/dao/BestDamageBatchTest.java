package xuanmo.arcartxsuite.entitytracker.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.entitytracker.entity.PlayerBossBestDamage;
import xuanmo.arcartxsuite.entitytracker.storage.JdbcEntityTrackerRepository;

class BestDamageBatchTest {
    @TempDir Path folder;
    private final String boss = UUID.randomUUID().toString();

    @Test void sqliteCommitsBestDamageInOneBoundedBatch() throws Exception {
        exercise("jdbc:sqlite:" + folder.resolve("scores.db"), false);
    }

    @Test void mysqlProtocolDistinguishesARealImprovementFromASharedReplay() throws Exception {
        String url = System.getenv("AXS_BEST_DAMAGE_MYSQL_URL");
        assumeTrue(url != null && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/eventpacket_verify"));
        exercise(url, true);
    }

    private void exercise(String url, boolean mysql) throws Exception {
        var raw = mock(DataSource.class);
        when(raw.getConnection()).thenAnswer(call -> mysql
            ? DriverManager.getConnection(url, "root", "") : DriverManager.getConnection(url));
        var descriptor = mock(StorageDescriptor.class);
        when(descriptor.isMysql()).thenReturn(mysql);
        new JdbcEntityTrackerRepository(raw, descriptor, folder.toFile(), Logger.getAnonymousLogger()).initialize();
        var counted = mock(DataSource.class);
        var opened = new AtomicInteger();
        when(counted.getConnection()).thenAnswer(call -> {
            opened.incrementAndGet();
            return raw.getConnection();
        });
        var dao = new PlayerBossBestDamageDao(counted, null);
        var reader = new PlayerBossBestDamageDao(raw, null);
        var players = IntStream.range(0, 48).mapToObj(i -> UUID.randomUUID().toString()).toList();
        var initial = players.stream().map(id -> score(id, 100, "original")).toList();
        assertEquals(48, dao.saveBetterBatch(boss, initial).size());
        assertEquals(1, opened.get());

        String player = players.get(0);
        var changed = dao.saveBetterBatch(boss, List.of(score(player, 120, "improved"),
            score(players.get(1), 90, "lower"), score(players.get(2), 100, "equal")));
        assertEquals(List.of(player), changed.stream().map(PlayerBossBestDamage::getPlayerUuid).toList());
        assertEquals(2, opened.get());
        assertEquals("original", reader.findByPlayerAndBoss(players.get(1), boss).orElseThrow().getServerName());
        assertEquals("original", reader.findByPlayerAndBoss(players.get(2), boss).orElseThrow().getServerName());
        assertTrue(dao.saveBetterBatch(boss, players.stream().map(id -> score(id, 100, "replay")).toList()).isEmpty());
        assertEquals(3, opened.get());

        var start = new CountDownLatch(1);
        var nodes = Executors.newFixedThreadPool(2);
        try {
            var smaller = nodes.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return dao.insertOrUpdateIfBetter(score(player, 200, "left"));
            });
            var larger = nodes.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return dao.insertOrUpdateIfBetter(score(player, 300, "right"));
            });
            start.countDown();
            smaller.get(8, TimeUnit.SECONDS);
            assertTrue(larger.get(8, TimeUnit.SECONDS));
        } finally {
            nodes.shutdownNow();
        }
        var best = reader.findByPlayerAndBoss(player, boss).orElseThrow();
        assertEquals(300, best.getBestDamage());
        assertEquals("right", best.getServerName());
        assertNull(best.getLocationX());
    }

    private PlayerBossBestDamage score(String player, int damage, String node) {
        return new PlayerBossBestDamage(player, "Tester", boss, "Boss", damage, LocalDateTime.now(), node);
    }
}
