package xuanmo.arcartxsuite.lottery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.lottery.storage.JdbcLotteryRepository;

class LotteryClaimTransferTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();

    @Test void directDrawUsesAnIdempotentKeyForEachPrizePosition() {
        var host = mock(PendingRewardService.class);
        var keys = new HashMap<String, String>();
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            String key = call.getArgument(0);
            List<PendingReward> awards = call.getArgument(1);
            var item = awards.get(0);
            assertEquals(player, item.playerUuid());
            assertEquals(PendingReward.Type.ITEM_SERIALIZED, item.type());
            String previous = keys.putIfAbsent(key, item.payload());
            return previous == null || previous.equals(item.payload());
        });
        var transfer = new LotteryClaimTransfer(null, host, () -> "fixture");
        var draw = UUID.randomUUID();
        assertTrue(transfer.acceptDrawPrize(player, draw, 0, "AQ=="));
        assertTrue(transfer.acceptDrawPrize(player, draw, 0, "AQ=="));
        assertFalse(transfer.acceptDrawPrize(player, draw, 0, "Ag=="));
        assertTrue(transfer.acceptDrawPrize(player, draw, 1, "Ag=="));
        assertEquals(java.util.Set.of("draw:" + draw + ":0", "draw:" + draw + ":1"), keys.keySet());
    }

    @Test void legacyRowUsesTheSameRequestAfterACommittedHostHandoffLosesItsLocalConfirmation() throws Exception {
        var source = source();
        // Start with the actual old table shape, without either handoff column.
        execute(source, "CREATE TABLE pending_claim (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, "
            + "pool_id TEXT NOT NULL, item_metadata TEXT NOT NULL, item_data TEXT, created_time INTEGER NOT NULL, "
            + "claimed INTEGER NOT NULL DEFAULT 0, attempts INTEGER NOT NULL DEFAULT 0)");
        // A row from the old version already exists before the module opens the database.
        try (var conn = source.getConnection();
             var insert = conn.prepareStatement("INSERT INTO pending_claim (player_uuid, pool_id, item_metadata, item_data, created_time) "
                + "VALUES (?, 'legacy', 'item', 'AQ==', 7)")) {
            insert.setString(1, player.toString());
            insert.executeUpdate();
        }
        var firstRepository = repository(source);
        var host = mock(PendingRewardService.class);
        var durable = new HashMap<String, String>();
        var attempts = new ArrayList<String>();
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            String key = call.getArgument(0);
            List<PendingReward> batch = call.getArgument(1);
            attempts.add(key);
            String payload = batch.get(0).payload();
            String previous = durable.putIfAbsent(key, payload);
            return previous == null || previous.equals(payload);
        });
        var storage = StorageDescriptor.sqlite("host.db");
        var first = new LotteryClaimTransfer(firstRepository, host, LotteryClaimTransfer.target(storage, true, directory));
        execute(source, "CREATE TRIGGER lost_ack BEFORE UPDATE OF claimed ON pending_claim BEGIN SELECT RAISE(ABORT, 'ack lost'); END");
        assertThrows(SQLException.class, () -> first.transfer(player));
        assertEquals(1, durable.size());
        assertEquals(1, firstRepository.countPendingClaims(player));
        execute(source, "DROP TRIGGER lost_ack");

        var secondRepository = repository(source);
        var restarted = new LotteryClaimTransfer(secondRepository, host, LotteryClaimTransfer.target(storage, true, directory));
        assertEquals(new LotteryClaimTransfer.Result(1, 0, true), restarted.transfer(player));
        assertEquals(2, attempts.size());
        assertEquals(attempts.get(0), attempts.get(1));
        assertEquals(1, durable.size());
        assertEquals(new LotteryClaimTransfer.Result(0, 0, false), restarted.transfer(player));
        verify(host, times(2)).savePendingBatchConfirmed(anyString(), anyList());
    }

    @Test void anotherHostCannotTakeOverABoundRowWhileUnboundRowsStillUseTheNewHost() throws Exception {
        var source = source();
        var repository = repository(source);
        assertTrue(repository.enqueuePendingClaim(player, "pool", "item", "AQ==") > 0);
        var storage = StorageDescriptor.sqlite("host.db");
        var firstHost = mock(PendingRewardService.class);
        var first = new LotteryClaimTransfer(repository, firstHost, LotteryClaimTransfer.target(storage, true, directory));
        assertEquals(new LotteryClaimTransfer.Result(0, 1, false), first.transfer(player));
        verify(firstHost).savePendingBatchConfirmed(anyString(), anyList());

        Path other = Files.createDirectory(directory.resolve("other"));
        var secondHost = mock(PendingRewardService.class);
        when(secondHost.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(true);
        var second = new LotteryClaimTransfer(repository, secondHost, LotteryClaimTransfer.target(storage, true, other));
        assertEquals(new LotteryClaimTransfer.Result(0, 1, false), second.transfer(player));
        verify(secondHost, never()).savePendingBatchConfirmed(anyString(), anyList());
        assertTrue(repository.enqueuePendingClaim(player, "pool", "manual-only", null) > 0);
        assertTrue(repository.enqueuePendingClaim(player, "pool", "other-item", "Ag==") > 0);
        assertEquals(new LotteryClaimTransfer.Result(1, 2, true), second.transfer(player));
        verify(secondHost, times(1)).savePendingBatchConfirmed(anyString(), anyList());
    }

    private SQLiteDataSource source() {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("lottery.sqlite"));
        return source;
    }

    private JdbcLotteryRepository repository(SQLiteDataSource source) throws SQLException {
        var repository = new JdbcLotteryRepository(source, StorageDescriptor.sqlite("lottery.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        return repository;
    }

    private void execute(SQLiteDataSource source, String sql) throws SQLException {
        try (var conn = source.getConnection(); var stmt = conn.createStatement()) { stmt.execute(sql); }
    }
}
