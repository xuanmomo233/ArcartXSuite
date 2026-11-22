package xuanmo.arcartxsuite.lottery;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.storage.JdbcLotteryRepository;
import xuanmo.arcartxsuite.lottery.storage.LotteryRepository.CaseLogEntry;
import xuanmo.arcartxsuite.lottery.storage.LotteryRepository.GachaLogEntry;

class LotteryReadProjectionTest {
    private static final String POOL = "banner";
    private static final String SHARED_PITY = "shared";
    private final UUID player = UUID.randomUUID();
    @TempDir Path folder;

    private SQLiteDataSource source;
    private JdbcLotteryRepository repository;

    @BeforeEach
    void initialize() throws SQLException {
        source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + folder.resolve("lottery.sqlite"));
        repository = new JdbcLotteryRepository(source, StorageDescriptor.sqlite("lottery.sqlite"),
            folder.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
    }

    @Test
    void capturesOnlyThisPlayersCommittedPityPointsAndExchangeCounts() throws SQLException {
        UUID operation = UUID.randomUUID();
        var pity = new PlayerGachaState(player, SHARED_PITY, 7, 2, true, 1, "weapon");
        assertTrue(repository.addPoints(player, "ticket", 10));
        assertTrue(repository.addPoints(UUID.randomUUID(), "ticket", 100));
        assertTrue(repository.incrementExchangeCount(player, "item"));
        assertTrue(repository.incrementExchangeCount(UUID.randomUUID(), "other-item"));
        assertTrue(repository.beginGachaOperation(player, operation, POOL, "test"));
        repository.commitGachaPull(operation, pity, POOL, 1, "[prize]", false, java.util.List.of(),
            Map.of("ticket", 3L), Map.of("dust", 2L));

        Set<String> points = Set.of("ticket", "dust", "omitted");
        var gacha = repository.loadGachaView(player, SHARED_PITY, points);
        var exchange = repository.loadExchangeView(player, points, Set.of("item", "other-item"));
        assertEquals(pity, gacha.state());
        assertEquals(Map.of("ticket", 7L, "dust", 2L), gacha.points());
        assertEquals(gacha.points(), exchange.points());
        assertEquals(Map.of("item", 1), exchange.counts());
        assertEquals(gacha.points(), repository.loadPointBalances(player, points));
        assertThrows(UnsupportedOperationException.class, () -> gacha.points().put("ticket", 0L));
        assertThrows(UnsupportedOperationException.class, () -> exchange.counts().clear());
        assertEquals("COMMITTED", repository.findGachaOperation(player).status());
    }

    @Test
    void batchesMoreThanOneChunkAndMatchesTheScalarLookupCollation() throws SQLException {
        assertTrue(repository.addPoints(player, "Ticket", 11));
        assertTrue(repository.addPoints(player, "other", 22));
        assertTrue(repository.incrementExchangeCount(player, "Prize"));
        Set<String> keys = new LinkedHashSet<>();
        keys.add("Ticket");
        keys.add("ticket");
        for (int i = 0; i < 103; i++) keys.add("absent-" + i);
        keys.add("other");

        Map<String, Long> points = repository.loadPointBalances(player, keys);
        var exchange = repository.loadExchangeView(player, keys, Set.of("Prize", "prize"));

        assertEquals(Map.of("Ticket", 11L, "other", 22L), points);
        assertEquals(repository.getPoint(player, "ticket"), points.getOrDefault("ticket", 0L));
        assertEquals(points, exchange.points());
        assertEquals(Map.of("Prize", 1), exchange.counts());
        assertEquals(repository.getExchangeCount(player, "prize"), exchange.counts().getOrDefault("prize", 0));
    }

    @Test
    void historyKeepsExistingOrderingAndBounds() throws SQLException {
        try (var connection = source.getConnection();
             var gacha = connection.prepareStatement("""
                 INSERT INTO gacha_log (player_uuid, pool_id, pull_time, items_json) VALUES (?, ?, ?, ?)
                 """);
             var opening = connection.prepareStatement("""
                 INSERT INTO case_log (player_uuid, pool_id, open_time, item_id, rarity) VALUES (?, ?, ?, ?, ?)
                 """)) {
            for (long time : List.of(1L, 3L, 2L)) {
                gacha.setString(1, player.toString());
                gacha.setString(2, POOL);
                gacha.setLong(3, time);
                gacha.setString(4, "[prize]");
                gacha.executeUpdate();
                opening.setString(1, player.toString());
                opening.setString(2, POOL);
                opening.setLong(3, time);
                opening.setString(4, "case-prize");
                opening.setString(5, "COMMON");
                opening.executeUpdate();
            }
        }

        var gachaHistory = repository.loadGachaHistory(player, POOL, 2);
        var caseHistory = repository.loadCaseHistory(player, POOL, 2);
        assertEquals(List.of(3L, 2L), gachaHistory.stream().map(GachaLogEntry::pullTime).toList());
        assertEquals(List.of(3L, 2L), caseHistory.stream().map(CaseLogEntry::openTime).toList());
        assertEquals(repository.getGachaHistory(player, POOL, 2), gachaHistory);
        assertEquals(repository.getCaseHistory(player, POOL, 2), caseHistory);
        assertThrows(UnsupportedOperationException.class, caseHistory::clear);
    }

    @Test
    void strictReadsDistinguishEmptyDataFromUnavailabilityAndSkipUnrequestedPoints() throws SQLException {
        var empty = PlayerGachaState.empty(player, SHARED_PITY);
        Set<String> points = Set.of("ticket");
        assertEquals(empty, repository.loadGachaView(player, SHARED_PITY, points).state());
        assertEquals(Map.of(), repository.loadPointBalances(player, points));
        assertEquals(List.of(), repository.loadGachaHistory(player, POOL, 2));
        assertEquals(List.of(), repository.loadCaseHistory(player, POOL, 2));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE points");
            statement.execute("DROP TABLE gacha_log");
            statement.execute("DROP TABLE case_log");
        }
        assertEquals(empty, repository.loadGachaView(player, SHARED_PITY, Set.of()).state());
        assertThrows(SQLException.class, () -> repository.loadGachaView(player, SHARED_PITY, points));
        assertThrows(SQLException.class, () -> repository.loadExchangeView(player, points, Set.of("item")));
        assertThrows(SQLException.class, () -> repository.loadPointBalances(player, points));
        assertThrows(SQLException.class, () -> repository.loadGachaHistory(player, POOL, 2));
        assertThrows(SQLException.class, () -> repository.loadCaseHistory(player, POOL, 2));
        assertEquals(Map.of(), repository.getPoints(player));
        assertEquals(List.of(), repository.getGachaHistory(player, POOL, 2));
        assertEquals(List.of(), repository.getCaseHistory(player, POOL, 2));
    }
}
