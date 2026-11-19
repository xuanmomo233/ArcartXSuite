package xuanmo.arcartxsuite.entitytracker.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.entitytracker.boss.config.BossDamageRankingRewardsSettings;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementEntry;
import xuanmo.arcartxsuite.entitytracker.boss.tracker.BossDamageSettlementRecord;
import xuanmo.arcartxsuite.entitytracker.entity.RankingRewardRecord;
import xuanmo.arcartxsuite.entitytracker.storage.JdbcEntityTrackerRepository;

class PeriodRankingDaoTest {
    @TempDir Path directory;

    @Test
    void freezesOnlyBestScoresInsidePeriodAndClaimsEachActionOnce() throws Exception {
        var config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        var source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + directory.resolve("ranking.sqlite"));
        new JdbcEntityTrackerRepository(source, StorageDescriptor.sqlite("ranking.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger()).initialize();

        var records = new RankingRewardRecordDao(source, null);
        var first = new PeriodRankingDao(source, records);
        var second = new PeriodRankingDao(source, new RankingRewardRecordDao(source, null));
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        first.recordScores(kill(alice, "Alice", 800, 999));
        first.recordScores(kill(alice, "Alice", 100, 1000));
        first.recordScores(kill(alice, "Alice", 220, 1250));
        first.recordScores(kill(bob, "Bob", 200, 1600));
        first.recordScores(kill(bob, "Bob", 900, 2000));
        var start = LocalDateTime.of(2026, 11, 1, 0, 0);
        var end = start.plusWeeks(1);

        java.util.function.Function<PeriodRankingDao.Standing, RankingRewardRecord> plan = standing -> {
            var reward = new RankingRewardRecord(-1, "weekly", "boss_damage", start, end,
                standing.playerId(), standing.playerName(), standing.rank(), standing.score());
            reward.setBossId("boss");
            reward.setRewardPlan("actions: []");
            return reward;
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            var a = executor.submit(() -> {
                gate.await();
                first.freeze("boss", "weekly", start, 1000, 2000, 2, plan);
                return null;
            });
            var b = executor.submit(() -> {
                gate.await();
                second.freeze("boss", "weekly", start, 1000, 2000, 2, plan);
                return null;
            });
            gate.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        first.recordScores(kill(bob, "Bob", 500, 1800));
        second.freeze("boss", "weekly", start, 1000, 2000, 2, plan);
        var frozen = records.pendingPeriod("boss", "weekly", 1000);
        assertEquals(List.of(alice.toString(), bob.toString()),
            frozen.stream().map(RankingRewardRecord::getPlayerUuid).toList());
        assertEquals(List.of(220, 200), frozen.stream().map(RankingRewardRecord::getScore).toList());

        int reward = frozen.get(0).getId();
        String attemptA = UUID.randomUUID().toString();
        String attemptB = UUID.randomUUID().toString();
        assertTrue(records.updateAction(reward, 0, "pending", "issuing", 0, null, false, null, attemptA));
        assertFalse(records.updateAction(reward, 0, "pending", "issuing", 0, null, false, null, attemptB));
        assertTrue(records.updateAction(reward, 0, "issuing", "pending", 1, null, false, attemptA, null));
        assertFalse(records.updateAction(reward, 0, "pending", "issuing", 0, null, true, null, attemptB));
        assertTrue(records.updateAction(reward, 1, "pending", "issuing", 1, null, true, null, attemptB));
        assertFalse(records.updateAction(reward, 1, "issuing", "success", 2, null, false, attemptA, null));
        assertTrue(records.updateAction(reward, 1, "issuing", "review", 1, "uncertain", false, attemptB, attemptB));
        assertFalse(records.updateAction(reward, 1, "pending", "issuing", 1, null, true, null, attemptA));
        assertEquals(1, records.findById(reward).orElseThrow().getNextAction());
        assertEquals(attemptB, records.findById(reward).orElseThrow().getClaimAttempt());
        assertEquals(1, records.findByFilters("weekly", "failed", null).size());
    }

    private static BossDamageSettlementRecord kill(UUID player, String name, int damage, long time) {
        var entry = new BossDamageSettlementEntry(player, name, 1, true, damage, 100, 0, false, "", List.of());
        return new BossDamageSettlementRecord(UUID.randomUUID().toString(), time, "boss", "Boss",
            UUID.randomUUID().toString(), 1, 1, damage, BossDamageRankingRewardsSettings.defaults(),
            List.of(entry), Map.of(player, entry));
    }
}
