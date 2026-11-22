package xuanmo.arcartxsuite.onlinerewards.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressCommit;

class OfflineSavingsPersistenceTest {
    @TempDir Path directory;

    @Test
    void legacyBalanceAndDailySavingsCountersSurviveEverySavePath() throws Exception {
        var options = new SQLiteConfig();
        options.setBusyTimeout(5000);
        var source = new SQLiteDataSource(options);
        source.setUrl("jdbc:sqlite:" + directory.resolve("savings.sqlite"));
        verifyLegacyAndRoundTrips(source, StorageDescriptor.sqlite("savings.sqlite"), directory);
    }

    static void verifyLegacyAndRoundTrips(DataSource source, StorageDescriptor descriptor, Path directory) throws Exception {
        UUID existing = new UUID(0, 1);
        try (var connection = source.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE online_rewards_players (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(64) NOT NULL DEFAULT '',
                    reward_date VARCHAR(32) NOT NULL DEFAULT '',
                    online_minutes INT NOT NULL DEFAULT 0,
                    reward_stage INT NOT NULL DEFAULT 0,
                    week_key VARCHAR(32) NOT NULL DEFAULT '',
                    week_minutes INT NOT NULL DEFAULT 0,
                    month_key VARCHAR(32) NOT NULL DEFAULT '',
                    month_minutes INT NOT NULL DEFAULT 0,
                    total_minutes INT NOT NULL DEFAULT 0,
                    last_sign_in_date VARCHAR(32) NOT NULL DEFAULT '',
                    sign_in_streak INT NOT NULL DEFAULT 0,
                    sign_in_total INT NOT NULL DEFAULT 0,
                    makeup_cards INT NOT NULL DEFAULT 0,
                    time_bonus_remainder DOUBLE NOT NULL DEFAULT 0,
                    offline_savings_minutes INT NOT NULL DEFAULT 0,
                    claimed_weekly_reward_ids TEXT NOT NULL,
                    claimed_monthly_reward_ids TEXT NOT NULL
                )
                """ + (descriptor.isMysql() ? " ENGINE=InnoDB" : ""));
            try (var seed = connection.prepareStatement("""
                    INSERT INTO online_rewards_players (player_uuid, reward_date, online_minutes, makeup_cards,
                        offline_savings_minutes, claimed_weekly_reward_ids, claimed_monthly_reward_ids)
                    VALUES (?, '2026-11-01', 25, 4, 17, 'weekly', 'monthly')
                    """)) {
                seed.setString(1, existing.toString());
                seed.executeUpdate();
            }
        }

        var writer = new JdbcOnlineRewardsRepository(source, descriptor, directory.toFile(), Logger.getAnonymousLogger());
        writer.initialize();
        var reader = new JdbcOnlineRewardsRepository(source, descriptor, directory.toFile(), Logger.getAnonymousLogger());
        reader.initialize();
        var legacy = reader.loadState(existing);
        check(legacy, 17, 4, "", 0, 0);
        assertEquals("2026-11-01", legacy.rewardDate());
        assertEquals(25, legacy.onlineMinutes());

        legacy.setOfflineSavingsDay("2026-10-31");
        legacy.setOfflineSavingsEarnedMinutes(13);
        legacy.setOfflineSavingsCreditedMinutes(8);
        var snapshot = legacy.copy();
        legacy.setOfflineSavingsDay(null);
        legacy.setOfflineSavingsEarnedMinutes(-1);
        legacy.setOfflineSavingsCreditedMinutes(-1);
        check(legacy, 17, 4, "", 0, 0);
        writer.saveState(existing, snapshot);
        check(reader.loadState(existing), 17, 4, "2026-10-31", 13, 8);

        snapshot.setMakeupCards(999);
        snapshot.setOfflineSavingsMinutes(19);
        snapshot.setOfflineSavingsDay("2026-11-02");
        snapshot.setOfflineSavingsEarnedMinutes(16);
        snapshot.setOfflineSavingsCreditedMinutes(10);
        writer.saveProgress(existing, snapshot);
        check(reader.loadState(existing), 19, 4, "2026-11-02", 16, 10);

        List<ProgressCommit> batch = new ArrayList<>();
        snapshot.setOfflineSavingsEarnedMinutes(20);
        snapshot.setOfflineSavingsCreditedMinutes(13);
        batch.add(new ProgressCommit(existing, snapshot, List.of()));
        for (int i = 2; i <= 49; i++) {
            var state = new OnlineRewardsPlayerState();
            state.setMakeupCards(999);
            state.setOfflineSavingsMinutes(i);
            state.setOfflineSavingsDay("2026-11-03");
            state.setOfflineSavingsEarnedMinutes(i + 1);
            state.setOfflineSavingsCreditedMinutes(i - 1);
            batch.add(new ProgressCommit(new UUID(0, i), state, List.of()));
        }
        writer.saveProgressBatch(batch);
        check(reader.loadState(existing), 19, 4, "2026-11-02", 20, 13);
        check(reader.loadState(new UUID(0, 47)), 47, 0, "2026-11-03", 48, 46);
        check(reader.loadState(new UUID(0, 49)), 49, 0, "2026-11-03", 50, 48);
    }

    private static void check(OnlineRewardsPlayerState state, int balance, int cards, String day, int earned, int credited) {
        assertEquals(balance, state.offlineSavingsMinutes());
        assertEquals(cards, state.makeupCards());
        assertEquals(day, state.offlineSavingsDay());
        assertEquals(earned, state.offlineSavingsEarnedMinutes());
        assertEquals(credited, state.offlineSavingsCreditedMinutes());
    }
}
