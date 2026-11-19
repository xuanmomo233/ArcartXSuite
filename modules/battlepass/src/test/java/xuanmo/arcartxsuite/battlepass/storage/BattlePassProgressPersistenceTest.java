package xuanmo.arcartxsuite.battlepass.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;

class BattlePassProgressPersistenceTest {
    @TempDir Path directory;

    @Test
    void failedTaskWriteCannotStoreXpWithoutTheCompletedTask() throws Exception {
        var sqliteConfig = new SQLiteConfig();
        sqliteConfig.setBusyTimeout(5000);
        var source = new SQLiteDataSource(sqliteConfig);
        source.setUrl("jdbc:sqlite:" + directory.resolve("battlepass.sqlite"));
        verifySplitAndAtomicWrites(source, StorageDescriptor.sqlite("battlepass.sqlite"), directory);
    }

    static void verifySplitAndAtomicWrites(DataSource source, StorageDescriptor descriptor, Path directory)
            throws Exception {
        var writer = new JdbcBattlePassRepository(source, descriptor, directory.toFile(), Logger.getAnonymousLogger());
        writer.initialize();
        var reader = new JdbcBattlePassRepository(source, descriptor, directory.toFile(), Logger.getAnonymousLogger());
        reader.initialize();
        UUID player = new UUID(0, 1);
        String season = "fixture";
        LocalDate date = LocalDate.of(2026, 11, 15);
        var before = new BattlePassPlayerProgress(player, season)
            .withXp(3).withPassTier(BattlePassPlayerProgress.PassTier.PREMIUM).withDailyReset(date);
        var after = before.withXp(38);
        var ordinary = new PlayerTaskInstance(player, "normal", BattlePassTask.TaskCategory.DAILY, 1, date);
        var failing = new PlayerTaskInstance(player, "fault", BattlePassTask.TaskCategory.DAILY, 1, date);
        var weekly = new PlayerTaskInstance(player, "weekly", BattlePassTask.TaskCategory.WEEKLY, 1, date);
        List<PlayerTaskInstance> priorTasks = List.of(ordinary, failing, weekly);
        List<PlayerTaskInstance> completedTasks = List.of(
            ordinary.withProgress(1).withCompleted(true), failing.withProgress(1).withCompleted(true));
        LocalDate nextDate = date.plusDays(1);
        var reset = after.withDailyReset(nextDate);
        List<PlayerTaskInstance> replacement = List.of(
            new PlayerTaskInstance(player, "normal", BattlePassTask.TaskCategory.DAILY, 1, nextDate),
            new PlayerTaskInstance(player, "fault", BattlePassTask.TaskCategory.DAILY, 1, nextDate)
                .withProgress(1).withCompleted(true));
        Set<BattlePassTask.TaskCategory> daily = Set.of(BattlePassTask.TaskCategory.DAILY);
        writer.saveProgress(before);
        writer.savePlayerTasks(player, season, priorTasks);
        writer.saveClaimedReward(player, season, "already-owned");
        installFailure(source, descriptor.isMysql());

        writer.saveProgress(after);
        assertThrows(SQLException.class, () -> writer.savePlayerTasks(player, season, completedTasks));
        assertSnapshot(reader, player, season, 38, date, priorTasks);

        writer.saveProgress(before);
        assertThrows(SQLException.class, () -> writer.saveProgressAndTasks(after, completedTasks, Set.of()));
        assertSnapshot(reader, player, season, 3, date, priorTasks);
        assertThrows(SQLException.class, () -> writer.saveProgressAndTasks(reset, replacement, daily));
        assertSnapshot(reader, player, season, 3, date, priorTasks);

        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER fail_completed_fixture_task");
        }
        writer.saveProgressAndTasks(after, completedTasks, Set.of());
        assertSnapshot(reader, player, season, 38, date, List.of(completedTasks.get(0), completedTasks.get(1), weekly));
        writer.saveProgressAndTasks(reset, replacement, daily);
        assertSnapshot(reader, player, season, 38, nextDate, List.of(replacement.get(0), replacement.get(1), weekly));
        writer.saveProgressAndTasks(reset, replacement, daily);
        assertSnapshot(reader, player, season, 38, nextDate, List.of(replacement.get(0), replacement.get(1), weekly));
    }

    private static void installFailure(DataSource source, boolean mysql) throws SQLException {
        String sql = mysql ? """
            CREATE TRIGGER fail_completed_fixture_task BEFORE INSERT ON bp_player_tasks FOR EACH ROW
            BEGIN
                IF NEW.template_id = 'fault' AND NEW.completed = 1 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fixture failed the second task write';
                END IF;
            END
            """ : """
            CREATE TRIGGER fail_completed_fixture_task BEFORE INSERT ON bp_player_tasks
            WHEN NEW.template_id = 'fault' AND NEW.completed = 1
            BEGIN
                SELECT RAISE(ABORT, 'fixture failed the second task write');
            END
            """;
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void assertSnapshot(JdbcBattlePassRepository reader, UUID player, String season, int xp, LocalDate date,
                                       List<PlayerTaskInstance> tasks) throws SQLException {
        var stored = reader.loadProgress(player, season);
        assertEquals(xp, stored.currentXp());
        assertEquals(date, stored.lastDailyResetDate());
        assertEquals(BattlePassPlayerProgress.PassTier.PREMIUM, stored.passTier());
        assertEquals(Set.of("already-owned"), stored.claimedRewards());
        assertEquals(new HashSet<>(tasks), new HashSet<>(reader.loadPlayerTasks(player, season)));
    }
}
