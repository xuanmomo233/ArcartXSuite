package xuanmo.arcartxsuite.onlinerewards.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.MakeupCardOperation;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Attendance;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ItemCheckpoint;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ItemSink;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressCommit;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressPlan;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressRequest;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Reward;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.RewardBatch;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.SignInStatus;

class SignInDeliveryTest {
    @TempDir Path directory;

    private JdbcOnlineRewardsRepository repository() throws Exception {
        var options = new SQLiteConfig();
        options.setBusyTimeout(5000);
        var source = new SQLiteDataSource(options);
        source.setUrl("jdbc:sqlite:" + directory.resolve("rewards.sqlite"));
        var repository = new JdbcOnlineRewardsRepository(source, StorageDescriptor.sqlite("rewards.sqlite"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        return repository;
    }

    @Test
    void signInAndFrozenRewardRollbackTogetherAndUncertainDeliveryIsNotReplayed() throws Exception {
        var first = repository();
        var player = UUID.randomUUID();
        var date = "2026-11-11";
        assertThrows(IllegalStateException.class, () ->
            first.recordSignInWithRewards(player, "Tester", date, false, ignored -> {
                throw new IllegalStateException("plan failed");
            }));
        assertFalse(first.hasSignInRecord(player, date));
        assertTrue(first.readyDeliveries(player, 10).isEmpty());
        assertEquals(0, first.loadState(player).signInTotal());

        var reward = new Reward("COMMAND", "give Tester apple 1", "");
        assertEquals(Set.of(date), first.recordSignInWithRewards(player, "Tester", date, false, dates -> List.of(reward)));
        var second = repository();
        assertEquals(Set.of(), second.recordSignInWithRewards(player, "Tester", date, false, dates -> {
            fail("duplicate sign in must not recalculate rewards");
            return List.of();
        }));
        var row = second.readyDeliveries(player, 10).get(0);
        assertEquals(date, second.loadState(player).lastSignInDate());
        assertEquals(1, second.loadState(player).signInTotal());
        assertEquals(reward, row.reward());
        UUID attempt = UUID.randomUUID();
        assertTrue(second.beginDelivery(row, attempt));
        assertFalse(first.beginDelivery(row, UUID.randomUUID()));
        assertFalse(first.resolveDelivery(player, row.batchId(), row.step(), attempt, false, true));
        assertTrue(repository().readyDeliveries(player, 10).isEmpty(), "a crash while executing must not replay");
        second.finishDelivery(row, attempt, false);
        assertTrue(repository().readyDeliveries(player, 10).isEmpty(), "a provider failure must stay in review");
        var review = first.deliveriesForReview(player, 10).get(0);
        assertEquals(attempt, review.attempt());
        assertTrue(first.resolveDelivery(player, row.batchId(), row.step(), attempt, false, true));
        var retry = UUID.randomUUID();
        assertTrue(first.beginDelivery(row, retry));
        second.finishDelivery(row, attempt, true);
        assertEquals(retry, first.deliveriesForReview(player, 10).get(0).attempt(), "old receipt cannot finish a new attempt");
        assertTrue(first.resolveDelivery(player, row.batchId(), row.step(), retry, true, true));
        assertTrue(first.deliveriesForReview(player, 10).isEmpty());
    }

    @Test
    void theLastMakeupCardCanOnlyCommitOneDateAndStaleProgressCannotRefillIt() throws Exception {
        var first = repository();
        var second = repository();
        UUID player = UUID.randomUUID();
        var snapshot = new OnlineRewardsPlayerState();
        snapshot.setPlayerName("Tester");
        snapshot.setMakeupCards(1);
        first.saveState(player, snapshot);
        var reward = new Reward("COMMAND", "give Tester apple 1", "");
        String one = "2026-11-10";
        String two = "2026-11-11";
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var left = workers.submit(() -> {
                start.await();
                return first.commitSignInWithRewards(player, "Tester", one, true, dates -> List.of(reward));
            });
            var right = workers.submit(() -> {
                start.await();
                return second.commitSignInWithRewards(player, "Tester", two, true, dates -> List.of(reward));
            });
            start.countDown();
            var commits = List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
            assertEquals(Set.of(SignInStatus.APPLIED, SignInStatus.NO_CARDS),
                Set.of(commits.get(0).status(), commits.get(1).status()));
            assertTrue(commits.stream().allMatch(commit -> commit.makeupCards() == 0));
            String acceptedDate = commits.get(0).status() == SignInStatus.APPLIED ? one : two;
            assertEquals(Set.of(acceptedDate), first.loadAllSignInDates(player));
            assertEquals(List.of("signin:" + acceptedDate), first.readyDeliveries(player, 10).stream()
                .map(OnlineRewardsRepository.Delivery::batchId).toList());
            var duplicate = second.commitSignInWithRewards(player, "Tester", acceptedDate, true, dates -> {
                fail("same date must not recalculate an award after the card balance reaches zero");
                return List.of();
            });
            assertEquals(SignInStatus.REPEATED, duplicate.status());
            assertEquals(0, duplicate.makeupCards());
        } finally {
            workers.shutdownNow();
        }

        snapshot.setOnlineMinutes(45);
        second.saveProgress(player, snapshot);
        assertEquals(45, first.loadState(player).onlineMinutes());
        assertEquals(0, first.loadState(player).makeupCards());
        assertEquals(1, first.loadState(player).signInTotal());
        assertEquals(1, first.loadState(player).signInStreak());
        second.saveProgressBatch(List.of(new ProgressCommit(player, snapshot, List.of())));
        assertEquals(1, first.loadState(player).signInTotal());
        assertEquals(1, first.readyDeliveries(player, 10).size());
    }

    @Test
    void failedMakeupPlanningRollsBackItsDebitAndCardAdjustmentsDoNotOverwriteProgress() throws Exception {
        var repository = repository();
        UUID player = UUID.randomUUID();
        assertEquals(1, repository.adjustMakeupCards(player, "Tester", MakeupCardOperation.ADD, 1));
        String date = "2026-11-10";
        assertThrows(IllegalStateException.class, () ->
            repository.commitSignInWithRewards(player, "Tester", date, true, dates -> {
                throw new IllegalStateException("cannot resolve rewards");
            }));
        assertEquals(1, repository.loadState(player).makeupCards());
        assertFalse(repository.hasSignInRecord(player, date));
        assertTrue(repository.readyDeliveries(player, 10).isEmpty());

        var progress = new OnlineRewardsPlayerState();
        progress.setPlayerName("Tester");
        progress.setOnlineMinutes(90);
        repository.saveProgress(player, progress);
        assertEquals(3, repository.adjustMakeupCards(player, "Tester", MakeupCardOperation.ADD, 2));
        assertEquals(2, repository.adjustMakeupCards(player, "Tester", MakeupCardOperation.REMOVE, 1));
        assertEquals(0, repository.adjustMakeupCards(player, "Tester", MakeupCardOperation.REMOVE, 100));
        assertEquals(Integer.MAX_VALUE, repository.adjustMakeupCards(player, "Tester", MakeupCardOperation.SET, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, repository.adjustMakeupCards(player, "Tester", MakeupCardOperation.ADD, 1));
        assertEquals(0, repository.adjustMakeupCards(player, "Tester", MakeupCardOperation.SET, 0));
        assertEquals(90, repository.loadState(player).onlineMinutes());

        UUID unsaved = UUID.randomUUID();
        progress.setMakeupCards(42);
        repository.saveProgress(unsaved, progress);
        assertEquals(0, repository.loadState(unsaved).makeupCards());
    }

    @Test
    void attendanceReplayUsesCommittedClockEvenAfterAnotherConnectionAndManualAdjustment() throws Exception {
        var first = repository();
        var second = repository();
        UUID player = UUID.randomUUID();
        UUID oldConnection = UUID.randomUUID();
        UUID newConnection = UUID.randomUUID();
        var reward = new RewardBatch("duration:test:stage", List.of(new Reward("COMMAND", "give Tester apple 1", "")));
        var request = attendance(player, oldConnection, 4, reward);
        var start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var left = workers.submit(() -> { start.await(); return first.updateProgress(List.of(request)).get(0); });
            var right = workers.submit(() -> { start.await(); return second.updateProgress(List.of(request)).get(0); });
            start.countDown();
            var one = left.get(5, TimeUnit.SECONDS);
            var two = right.get(5, TimeUnit.SECONDS);
            assertNotEquals(one.changed(), two.changed());
            assertEquals(4, one.accountedMinutes());
            assertEquals(4, two.accountedMinutes());
        } finally {
            workers.shutdownNow();
        }
        second.updateProgress(List.of(new ProgressRequest(player, "Tester", null, (state, ignored) -> {
            state.setOnlineMinutes(state.onlineMinutes() + 20);
            return new ProgressPlan(true, List.of());
        })));
        second.updateProgress(List.of(attendance(player, newConnection, 2, reward)));

        var replay = repository().updateProgress(List.of(request)).get(0);
        assertFalse(replay.changed());
        assertEquals(26, replay.state().onlineMinutes());
        assertEquals(1, first.readyDeliveries(player, 10).size());
    }

    @Test
    void failedRewardTransactionDoesNotConsumeTheAttendanceCursor() throws Exception {
        var repository = repository();
        UUID player = UUID.randomUUID();
        UUID connection = UUID.randomUUID();
        var invalid = new RewardBatch("duration:test", List.of(new Reward("ITEM", null, "")));
        assertThrows(java.sql.SQLException.class, () ->
            repository.updateProgress(List.of(attendance(player, connection, 7, invalid))));
        assertEquals(0, repository.loadState(player).onlineMinutes());
        var valid = new RewardBatch("duration:test", List.of(new Reward("ITEM", "item-data", "")));
        var result = repository.updateProgress(List.of(attendance(player, connection, 7, valid))).get(0);
        assertTrue(result.changed());
        assertEquals(7, result.accountedMinutes());
        assertEquals(7, result.state().onlineMinutes());
        assertEquals(1, repository.readyDeliveries(player, 10).size());
    }

    @Test
    void aHotUpgradeStartsAtTheObservedClockWhenThereIsNoReceipt() throws Exception {
        var repository = repository();
        UUID player = UUID.randomUUID();
        UUID connection = UUID.randomUUID();
        var existing = new OnlineRewardsPlayerState();
        existing.setOnlineMinutes(40);
        repository.saveState(player, existing);
        var result = repository.updateProgress(List.of(new ProgressRequest(player, "Tester",
            new Attendance(connection, 22, 20), (state, delta) -> {
                assertEquals(2, delta);
                state.setOnlineMinutes(state.onlineMinutes() + Math.toIntExact(delta));
                return new ProgressPlan(true, List.of());
            }))).get(0);
        assertEquals(42, result.state().onlineMinutes());
        assertEquals(22, result.accountedMinutes());
    }

    private static ProgressRequest attendance(UUID player, UUID connection, long minutes, RewardBatch reward) {
        return new ProgressRequest(player, "Tester", new Attendance(connection, minutes), (state, delta) -> {
            if (delta == 0) return ProgressPlan.UNCHANGED;
            state.setOnlineMinutes(state.onlineMinutes() + Math.toIntExact(delta));
            return new ProgressPlan(true, List.of(reward));
        });
    }

    @Test
    void timedBatchRollsBackWholeChunkAndRetryKeepsTheOriginalFrozenRewards() throws Exception {
        var first = repository();
        var second = repository();
        var alice = new UUID(0, 1);
        var bob = new UUID(0, 2);
        var initial = new OnlineRewardsPlayerState();
        initial.setMakeupCards(2);
        first.saveState(alice, initial);
        var progress = initial.copy();
        progress.setOnlineMinutes(12);
        progress.setMakeupCards(999);
        var original = new Reward("ITEM", "apple", "minute-12");
        var alternate = new Reward("COMMAND", "give Tester diamond 1", "");
        String firstBatch = "time:daily:2026-11-11:stage1";
        String secondBatch = "time:weekly:2026-W46:reward";
        var commit = new ProgressCommit(alice, progress, List.of(new RewardBatch(firstBatch, List.of(original))));
        var invalid = new ProgressCommit(bob, progress, List.of(new RewardBatch(secondBatch,
            List.of(new Reward("ITEM", null, "invalid payload")))));

        assertThrows(java.sql.SQLException.class, () -> first.saveProgressBatch(List.of(invalid, commit)));
        assertEquals(0, second.loadState(alice).onlineMinutes());
        assertEquals(0, second.loadState(bob).onlineMinutes());
        assertEquals(2, second.loadState(alice).makeupCards());
        assertTrue(second.readyDeliveries(Set.of(alice, bob), 10).isEmpty());

        var valid = new ProgressCommit(bob, progress, List.of(new RewardBatch(secondBatch, List.of(alternate))));
        second.saveProgressBatch(List.of(valid, commit));
        assertEquals(12, first.loadState(alice).onlineMinutes());
        assertEquals(12, first.loadState(bob).onlineMinutes());
        assertEquals(2, first.loadState(alice).makeupCards());
        assertEquals(0, first.loadState(bob).makeupCards());
        assertEquals(List.of(alice, bob), first.readyDeliveries(Set.of(bob, alice), 2).stream()
            .map(OnlineRewardsRepository.Delivery::playerUuid).toList());
        assertEquals(List.of(alice), first.readyDeliveries(Set.of(bob, alice), 1).stream()
            .map(OnlineRewardsRepository.Delivery::playerUuid).toList());
        assertEquals(List.of(bob), first.readyDeliveries(Set.of(bob), 2).stream()
            .map(OnlineRewardsRepository.Delivery::playerUuid).toList());
        assertTrue(first.readyDeliveries(Set.of(), 1).isEmpty());

        var deposited = first.readyDeliveries(alice, 1).get(0);
        UUID attemptId = UUID.randomUUID();
        assertTrue(first.beginDelivery(deposited, attemptId));
        first.finishDelivery(deposited, attemptId, true);
        var modifiedPlan = new ProgressCommit(alice, progress,
            List.of(new RewardBatch(firstBatch, List.of(alternate, alternate))));
        second.saveProgressBatch(List.of(modifiedPlan, valid));
        assertTrue(first.readyDeliveries(alice, 10).isEmpty(), "completed time reward must never reopen after config changes");
        assertEquals(List.of(alternate), first.readyDeliveries(bob, 10).stream().map(OnlineRewardsRepository.Delivery::reward).toList());
    }

    @Test
    void onlyTheOwnerOfAnUnstartedAttemptCanReturnItToReady() throws Exception {
        var first = repository();
        var second = repository();
        var player = UUID.randomUUID();
        var reward = new Reward("COMMAND", "say first", "");
        first.saveProgressBatch(List.of(new ProgressCommit(player, new OnlineRewardsPlayerState(),
            List.of(new RewardBatch("time:release", List.of(reward))))));
        var delivery = first.readyDeliveries(player, 1).get(0);
        var attempt = UUID.randomUUID();
        var nextAttempt = UUID.randomUUID();

        assertTrue(first.beginDelivery(delivery, attempt));
        assertFalse(second.releaseUnstartedDelivery(delivery, nextAttempt));
        assertTrue(second.readyDeliveries(player, 1).isEmpty());
        assertTrue(first.releaseUnstartedDelivery(delivery, attempt));
        assertFalse(first.releaseUnstartedDelivery(delivery, attempt));
        assertTrue(second.beginDelivery(delivery, nextAttempt));
        assertFalse(first.releaseUnstartedDelivery(delivery, attempt));
        second.finishDelivery(delivery, nextAttempt, true);
        assertFalse(first.releaseUnstartedDelivery(delivery, nextAttempt));
        assertTrue(first.readyDeliveries(player, 1).isEmpty());
    }

    @Test
    void itemCheckpointIsImmutableAndBlocksOnlyOtherItemsUntilTheOwnerCompletes() throws Exception {
        var first = repository();
        var second = repository();
        UUID player = UUID.randomUUID();
        var item = new Reward("ITEM", "fixture spec", "");
        var command = new Reward("COMMAND", "say other reward", "");
        first.saveProgressBatch(List.of(new ProgressCommit(player, new OnlineRewardsPlayerState(),
            List.of(new RewardBatch("time:item", List.of(item, item, item, command))))));
        var ready = first.readyDeliveries(player, 4);
        var delivery = ready.get(0);
        var competing = ready.get(1);
        var notYetClaimed = ready.get(2);
        var attempt = UUID.randomUUID();
        var competingAttempt = UUID.randomUUID();
        assertTrue(first.beginDelivery(delivery, attempt));
        assertTrue(second.beginDelivery(competing, competingAttempt));

        var frozen = new ItemCheckpoint(delivery, attempt, "Hub", "original-item", ItemSink.INVENTORY);
        assertTrue(first.stageItemCheckpoint(frozen));
        assertFalse(first.stageItemCheckpoint(new ItemCheckpoint(delivery, attempt, "Hub", "changed-item", ItemSink.MAIL)));
        assertFalse(second.stageItemCheckpoint(new ItemCheckpoint(competing, competingAttempt, "Other", "other-item", ItemSink.MAIL)));
        assertTrue(second.releaseUnstartedDelivery(competing, competingAttempt), "losing checkpoint insert must roll back its state change");
        assertFalse(second.beginDelivery(notYetClaimed, UUID.randomUUID()), "a stale ready page may not start an additional item");
        assertEquals(List.of(ready.get(3)), second.readyDeliveries(player, 10));

        assertFalse(first.releaseUnstartedDelivery(delivery, attempt));
        first.finishDelivery(delivery, attempt, true);
        assertFalse(second.resolveDelivery(player, delivery.batchId(), delivery.step(), attempt, true, true));
        assertFalse(second.completeItemCheckpoint(new ItemCheckpoint(delivery, attempt, "hub", "original-item", ItemSink.INVENTORY)));
        assertFalse(second.completeItemCheckpoint(new ItemCheckpoint(delivery, attempt, "Hub", "original-item", ItemSink.MAIL)));
        assertFalse(second.completeItemCheckpoint(new ItemCheckpoint(delivery, UUID.randomUUID(), "Hub", "original-item", ItemSink.INVENTORY)));
        assertEquals(frozen, second.itemCheckpoint(player).orElseThrow());
        assertTrue(second.completeItemCheckpoint(frozen));
        assertFalse(first.completeItemCheckpoint(frozen));
        assertTrue(first.itemCheckpoint(player).isEmpty());
        assertEquals(ready.subList(1, 4), first.readyDeliveries(player, 10));

        var mail = new ItemCheckpoint(competing, UUID.randomUUID(), "Other", "mail-item", ItemSink.MAIL);
        assertTrue(second.beginDelivery(competing, mail.attempt()));
        assertTrue(second.stageItemCheckpoint(mail));
        assertEquals(mail, first.itemCheckpoint(player).orElseThrow());
        assertTrue(first.completeItemCheckpoint(mail));
    }

    @Test
    void multipleChunksKeepStateAndEveryFrozenReward() throws Exception {
        var writer = repository();
        var reader = repository();
        var state = new OnlineRewardsPlayerState();
        state.setMakeupCards(5);
        writer.saveState(new UUID(0, 1), state);
        state.setOnlineMinutes(36);
        var reward = new Reward("ITEM", "奖励".repeat(4_096), "large payload");
        var commits = new ArrayList<ProgressCommit>();
        var ids = new HashSet<UUID>();
        for (int i = 1; i <= 130; i++) {
            var player = new UUID(0, i);
            ids.add(player);
            commits.add(new ProgressCommit(player, state, List.of(new RewardBatch("time:chunk", List.of(reward)))));
        }
        Collections.reverse(commits);
        writer.saveProgressBatch(commits);
        var pending = reader.readyDeliveries(ids, 131);
        assertEquals(130, pending.size());
        assertEquals(new UUID(0, 1), pending.get(0).playerUuid());
        assertEquals(new UUID(0, 130), pending.get(129).playerUuid());
        assertTrue(pending.stream().allMatch(delivery -> reward.equals(delivery.reward())));
        assertEquals(36, reader.loadState(new UUID(0, 130)).onlineMinutes());
        assertEquals(5, reader.loadState(new UUID(0, 1)).makeupCards());
        assertEquals(0, reader.loadState(new UUID(0, 130)).makeupCards());
    }

    @Test
    void scheduledRewardEnqueueMergesRecipientsWithoutWritingProgress() throws Exception {
        var first = repository();
        var second = repository();
        UUID shared = new UUID(0, 1);
        UUID left = new UUID(0, 2);
        UUID right = new UUID(0, 3);
        var progress = new OnlineRewardsPlayerState();
        progress.setPlayerName("Already saved");
        progress.setOnlineMinutes(43);
        progress.setMakeupCards(7);
        first.saveState(shared, progress);
        String batch = "calendar:weekly:2026-11-11";
        var one = new Reward("COMMAND", "say first plan", "");
        var two = new Reward("COMMAND", "say second plan", "");
        var workers = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = workers.submit(() -> {
                start.await();
                return first.enqueueRewardBatch(batch, Map.of(shared, List.of(one), left, List.of(one)));
            });
            var b = workers.submit(() -> {
                start.await();
                return second.enqueueRewardBatch(batch, Map.of(shared, List.of(two, two), right, List.of(two)));
            });
            start.countDown();
            assertEquals(3, a.get(8, TimeUnit.SECONDS) + b.get(8, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }

        var sharedPlan = first.readyDeliveries(shared, 10).stream().map(OnlineRewardsRepository.Delivery::reward).toList();
        assertTrue(sharedPlan.equals(List.of(one)) || sharedPlan.equals(List.of(two, two)));
        assertEquals(List.of(one), second.readyDeliveries(left, 10).stream().map(OnlineRewardsRepository.Delivery::reward).toList());
        assertEquals(List.of(two), first.readyDeliveries(right, 10).stream().map(OnlineRewardsRepository.Delivery::reward).toList());
        assertEquals(0, second.enqueueRewardBatch(batch, Map.of(shared, List.of(one, two))));
        assertEquals(sharedPlan, first.readyDeliveries(shared, 10).stream().map(OnlineRewardsRepository.Delivery::reward).toList());
        assertEquals(43, second.loadState(shared).onlineMinutes());
        assertEquals(7, second.loadState(shared).makeupCards());
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("rewards.sqlite"));
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM online_rewards_players")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1), "fresh calendar recipients must not be inserted into progress");
        }

        String emptyPlan = batch + ":empty";
        assertEquals(1, first.enqueueRewardBatch(emptyPlan, Map.of(shared, List.of())));
        assertEquals(0, second.enqueueRewardBatch(emptyPlan, Map.of(shared, List.of(one))));
        assertEquals(sharedPlan, first.readyDeliveries(shared, 10).stream().map(OnlineRewardsRepository.Delivery::reward).toList());
        assertEquals(0, first.enqueueRewardBatch(batch, Map.of()));
    }

    @Test
    void scheduledRewardEnqueueRollsBackEveryRecipientOnBadPayload() throws Exception {
        var first = repository();
        var second = repository();
        UUID one = new UUID(0, 1);
        UUID two = new UUID(0, 2);
        String batch = "calendar:daily:2026-11-11";
        var valid = new Reward("COMMAND", "say valid", "");
        assertThrows(java.sql.SQLException.class, () -> first.enqueueRewardBatch(batch,
            Map.of(one, List.of(valid), two, List.of(new Reward("ITEM", null, "")))));
        assertTrue(second.readyDeliveries(Set.of(one, two), 10).isEmpty());
        assertEquals(2, second.enqueueRewardBatch(batch, Map.of(one, List.of(valid), two, List.of(valid))));
        assertEquals(List.of(one, two), first.readyDeliveries(Set.of(one, two), 10).stream()
            .map(OnlineRewardsRepository.Delivery::playerUuid).toList());
    }

    @Test
    void simultaneousNodesFreezeOneGoalAndIncludeEachObservedOnlineRecipientOnce() throws Exception {
        var first = repository();
        var second = repository();
        var one = UUID.randomUUID();
        var two = UUID.randomUUID();
        var offline = UUID.randomUUID();
        var oldService = UUID.randomUUID();
        var newService = UUID.randomUUID();
        first.savePresence("hub", oldService, Map.of(one, "Alpha"), 100);
        first.savePresence("hub", newService, Map.of(one, "Alpha"), 100);
        second.savePresence("dungeon", newService, Map.of(one, "Alpha", two, "Beta"), 100);
        second.savePresence("old", oldService, Map.of(offline, "Offline"), 1);
        first.removePresence("hub", oldService, one);
        assertEquals(Map.of(one, "Alpha", two, "Beta"), first.loadPresence(2));

        var payload = new Reward("MESSAGE", "Goal reached", "");
        var recipients = Map.of(one, List.of(payload), two, List.of(payload));
        var workers = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var left = workers.submit(() -> {
                start.await();
                return first.recordServerGoalWithRewards("2026-11-11", "goal", recipients);
            });
            var right = workers.submit(() -> {
                start.await();
                return second.recordServerGoalWithRewards("2026-11-11", "goal", recipients);
            });
            start.countDown();
            assertNotEquals(left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }
        assertEquals(1, first.readyDeliveries(one, 10).size());
        assertEquals(1, second.readyDeliveries(two, 10).size());
        assertTrue(first.readyDeliveries(offline, 10).isEmpty());
        first.commitSignInWithRewards(one, "Alpha", "2026-11-11", false, dates -> List.of());
        assertEquals(new OnlineRewardsRepository.SignInGoalStatus(1, Set.of("goal")), second.signInGoalStatus("2026-11-11"));
        assertEquals(new OnlineRewardsRepository.SignInGoalStatus(0, Set.of()), second.signInGoalStatus("2026-11-12"));
    }
}
