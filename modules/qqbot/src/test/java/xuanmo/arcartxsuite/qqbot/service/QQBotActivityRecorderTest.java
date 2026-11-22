package xuanmo.arcartxsuite.qqbot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.qqbot.storage.JdbcQQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.ActivityKey;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.ActivityRecord;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.StorageFailure;

class QQBotActivityRecorderTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 19);

    @Test
    void blockedWriteDoesNotDelayNewMessagesAndRetirementDrainsTheBoundedBatch() throws Exception {
        var repository = mock(QQBotRepository.class);
        var recorder = new QQBotActivityRecorder(repository, Logger.getAnonymousLogger());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        List<Map<ActivityKey, Integer>> writes = new ArrayList<>();
        doAnswer(call -> {
            assertEquals("QQBot-Activity", Thread.currentThread().getName());
            writes.add(Map.copyOf(call.getArgument(0)));
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(repository).recordActivities(anyMap());

        try {
            recorder.record(1, 5, DAY);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            for (int i = 0; i < 1023; i++) recorder.record(1, 5, DAY);
            recorder.record(1, 5, DAY.plusDays(1));
            recorder.record(2, 5, DAY);
            var stopped = recorder.retire().toCompletableFuture();
            assertFalse(stopped.isDone());
            recorder.record(3, 5, DAY);
            release.countDown();
            stopped.get(3, TimeUnit.SECONDS);
            assertEquals(List.of(Map.of(new ActivityKey(1, 5, DAY), 1),
                Map.of(new ActivityKey(1, 5, DAY), 1023, new ActivityKey(1, 5, DAY.plusDays(1)), 1)), writes);
        } finally {
            release.countDown();
            recorder.retire().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void rankingFlushCommitsDeltasAndFailedBatchRollsBack(@TempDir Path folder) throws Exception {
        var source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + folder.resolve("qqbot.db"));
        var logger = Logger.getAnonymousLogger();
        var repository = new JdbcQQBotRepository(source, StorageDescriptor.sqlite("qqbot.db"), folder.toFile(), logger);
        repository.initialize();
        var recorder = new QQBotActivityRecorder(repository, logger);
        try {
            recorder.record(1, 5, DAY);
            recorder.record(1, 5, DAY);
            recorder.record(1, 6, DAY);
            recorder.record(2, 5, DAY.plusDays(1));
            recorder.flush();
            repository.recordActivities(Map.of(new ActivityKey(1, 5, DAY), 2));
            assertEquals(List.of(new ActivityRecord(1, 5)), repository.getActivityLeaderboard(DAY.toString(), DAY.toString(), 10));
            assertEquals(List.of(new ActivityRecord(2, 1)), repository.getActivityLeaderboard(
                DAY.plusDays(1).toString(), DAY.plusDays(1).toString(), 10));

            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER reject_activity BEFORE INSERT ON axs_qqbot_activity"
                    + " WHEN NEW.qq_id = 3 BEGIN SELECT RAISE(ABORT, 'test rollback'); END");
            }
            var batch = new LinkedHashMap<ActivityKey, Integer>();
            batch.put(new ActivityKey(1, 5, DAY), 3);
            for (int group = 10; group < 137; group++) batch.put(new ActivityKey(1, group, DAY), 1);
            batch.put(new ActivityKey(3, 5, DAY), 1);
            assertThrows(StorageFailure.class, () -> repository.recordActivities(batch));
            assertEquals(List.of(new ActivityRecord(1, 5)), repository.getActivityLeaderboard(DAY.toString(), DAY.toString(), 10));
        } finally {
            recorder.retire().toCompletableFuture().get(3, TimeUnit.SECONDS);
            repository.close();
        }
    }
}
