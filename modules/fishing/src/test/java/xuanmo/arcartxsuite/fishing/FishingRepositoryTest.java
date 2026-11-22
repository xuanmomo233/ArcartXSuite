package xuanmo.arcartxsuite.fishing;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.script.AriaBridge;
import xuanmo.arcartxsuite.api.script.XpCurve;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.fishing.model.BaitQueueEntry;
import xuanmo.arcartxsuite.fishing.model.FishCollectionEntry;
import xuanmo.arcartxsuite.fishing.model.FishingCatchRecord;
import xuanmo.arcartxsuite.fishing.model.FishingPermanentData;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.reward.RewardDispatcher;
import xuanmo.arcartxsuite.fishing.reward.SeasonRewardDefinition;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.RewardReservation;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.DisplayData;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.FishSale;
import xuanmo.arcartxsuite.fishing.storage.JdbcFishingRepository;
import xuanmo.arcartxsuite.fishing.task.FishingTask.TaskCategory;
import xuanmo.arcartxsuite.fishing.task.PlayerTaskInstance;

class FishingRepositoryTest {
    private static final String SEASON = "season-test";
    private static final XpCurve CURVE = new XpCurve(List.of(new XpCurve.Breakpoint(1, "100")), new AriaBridge() {
        @Override public boolean available() { return true; }
        @Override public String version() { return "test"; }
        @Override public Object eval(String code, Map<String, Object> bindings) { return 100; }
    });

    @TempDir Path directory;
    private final UUID player = UUID.randomUUID();
    private SQLiteDataSource source;
    private JdbcFishingRepository repository;

    @BeforeEach
    void connect() {
        source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("fishing.db"));
        repository = new JdbcFishingRepository(source, StorageDescriptor.sqlite("fishing.db"),
            directory.toFile(), Logger.getLogger("FishingRepositoryTest"));
    }

    @Test
    void onlyTheOriginalNodeCanRecoverAReservedSaleAndNeverAStartedPayment() throws Exception {
        repository.initialize();
        String node = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        var fish = List.of(PendingReward.itemSerialized(player, "fishing", "snapshot", "return"));
        var sale = new FishSale(player, UUID.randomUUID(), "shared", node, 1L, UUID.randomUUID(), "money", "10", fish);
        assertTrue(repository.reserveFishSale(sale));
        assertTrue(repository.reservedFishSales(other, "", 32).isEmpty());
        var recovered = repository.reservedFishSales(node, "", 32).get(0);
        assertEquals(sale.request(), recovered.request());
        assertEquals(sale.sourceSession(), recovered.sourceSession());
        assertTrue(repository.recoverUnpaidFishSale(recovered, true));
        assertFalse(repository.recoverUnpaidFishSale(recovered, true));
        assertEquals(fish, repository.pendingCatchDeliveries("shared", Long.MAX_VALUE, 32).get(0).rewards());

        var paying = new FishSale(player, UUID.randomUUID(), "shared", node, 1L, UUID.randomUUID(), "money", "10", fish);
        assertTrue(repository.reserveFishSale(paying));
        assertTrue(repository.beginFishSalePayment(paying));
        assertTrue(repository.reservedFishSales(node, "", 32).isEmpty());
        assertFalse(repository.recoverUnpaidFishSale(paying, true));
        assertFalse(repository.reserveFishSale(sale));
    }

    @Test
    void displayQueryCombinesSeasonLifetimeAndCollectionWithEmptyProfileDefaults() throws Exception {
        repository.initialize();
        var empty = new DisplayData(0, 0, 0, 0, 0, 0, 0, 0, Map.of());
        assertEquals(empty, repository.loadDisplayData(player, SEASON));

        repository.saveSeasonData(new FishingSeasonData(player, SEASON, 4, 9, 4, 2, Map.of("test", 2), 12, false));
        repository.savePermanentData(new FishingPermanentData(player, 8, 5, 1));
        repository.saveCollectionEntry(new FishCollectionEntry(player, "carp", 6, 11, 1));
        repository.saveCollectionEntry(new FishCollectionEntry(player, "perch", 2, 18, 2));

        var counts = Map.of("carp", 6, "perch", 2);
        assertEquals(new DisplayData(4, 9, 4, 2, 12, 8, 5, 1, counts), repository.loadDisplayData(player, SEASON));
        assertEquals(new DisplayData(0, 0, 0, 0, 0, 8, 5, 1, counts), repository.loadDisplayData(player, "other"));
        assertEquals(empty, repository.loadDisplayData(UUID.randomUUID(), SEASON));
        assertEquals(2, repository.countCollection(player));
        assertEquals(0, repository.countCollection(UUID.randomUUID()));
    }

    @Test
    void failedSwapRollsBackFirstSlotAndRejectsStaleWithdrawal() throws Exception {
        repository.initialize();
        BaitQueueEntry first = new BaitQueueEntry(player, 0, "worm", 7);
        BaitQueueEntry second = new BaitQueueEntry(player, 1, "bread", 4);
        assertTrue(repository.replaceBaitSlot(null, first));
        assertTrue(repository.replaceBaitSlot(null, second));
        BaitQueueEntry current = new BaitQueueEntry(player, 1, "bread", 3);
        assertTrue(repository.replaceBaitSlot(second, current));

        assertFalse(repository.swapBaitSlots(first, second));
        assertFalse(repository.replaceBaitSlot(second, new BaitQueueEntry(player, 1, "bread", 0)));
        assertEquals(List.of(first, current), repository.loadBaitQueue(player));

        assertTrue(repository.swapBaitSlots(first, current));
        assertEquals(List.of(new BaitQueueEntry(player, 0, "bread", 3),
            new BaitQueueEntry(player, 1, "worm", 7)), repository.loadBaitQueue(player));
    }

    @Test
    void biteConfirmsTheCurrentProfileAndOnlyConsumesTheCurrentHeadOnce() throws Exception {
        repository.initialize();
        var head = new BaitQueueEntry(player, 2, "worm", 3);
        var higherPriority = new BaitQueueEntry(player, 0, "corn", 1);
        assertTrue(repository.replaceBaitSlot(null, head));
        var before = repository.loadBiteProfile(player, SEASON);
        assertEquals(head, before.baitHead());
        assertTrue(repository.replaceBaitSlot(null, higherPriority));
        assertFalse(repository.confirmBite(before, true, () -> true));
        assertEquals(List.of(higherPriority, head), repository.loadBaitQueue(player));
        assertTrue(repository.replaceBaitSlot(higherPriority, new BaitQueueEntry(player, 0, "corn", 0)));

        repository.updateSeason(player, SEASON, season -> season.withFatigueAdded(2, 100));
        assertFalse(repository.confirmBite(before, true, () -> true));
        var current = repository.loadBiteProfile(player, SEASON);
        assertTrue(repository.confirmBite(current, false, () -> true));
        assertEquals(head, repository.loadBiteProfile(player, SEASON).baitHead());
        assertFalse(repository.confirmBite(current, true, () -> false));
        assertEquals(head, repository.loadBiteProfile(player, SEASON).baitHead());
        assertTrue(repository.confirmBite(current, true, () -> true));
        assertFalse(repository.confirmBite(current, true, () -> true));
        assertEquals(2, repository.loadBiteProfile(player, SEASON).baitHead().amount());
    }

    @Test
    void withdrawingBaitKeepsTheSlotAndDurableRewardInTheSameTransaction() throws Exception {
        repository.initialize();
        var before = new BaitQueueEntry(player, 0, "worm", 8);
        var after = new BaitQueueEntry(player, 0, "worm", 5);
        assertTrue(repository.replaceBaitSlot(null, before));
        var snapshot = repository.loadBaitEdit(player, SEASON, 0, 0);
        var reward = PendingReward.itemSerialized(player, "fishing", "three-worms", "test");
        UUID request = UUID.randomUUID();
        execute("""
            CREATE TRIGGER reject_delivery BEFORE INSERT ON axs_fishing_catch_delivery
            BEGIN SELECT RAISE(ABORT, 'delivery unavailable'); END
            """);
        assertThrows(SQLException.class, () -> repository.withdrawBait(snapshot, 3, request, "node", reward));
        assertEquals(List.of(before), repository.loadBaitQueue(player));
        assertTrue(repository.pendingCatchDeliveries("node", Long.MAX_VALUE, 8).isEmpty());

        execute("DROP TRIGGER reject_delivery");
        assertTrue(repository.withdrawBait(snapshot, 3, request, "node", reward));
        assertEquals(List.of(after), repository.loadBaitQueue(player));
        assertFalse(repository.withdrawBait(snapshot, 3, request, "node", reward));
        var current = repository.loadBaitEdit(player, SEASON, 0, 0);
        assertThrows(SQLException.class, () -> repository.withdrawBait(current, 3, request, "node", reward));
        assertEquals(List.of(after), repository.loadBaitQueue(player));
        var delivery = repository.pendingCatchDeliveries("node", Long.MAX_VALUE, 8);
        assertEquals(1, delivery.size());
        assertEquals(List.of(reward), delivery.get(0).rewards());
        assertEquals(request, delivery.get(0).eventId());
    }

    @Test
    void baitEditRejectsAnOlderPairAndAnOlderSeasonBeforeSwapping() throws Exception {
        repository.initialize();
        var a = new BaitQueueEntry(player, 0, "worm", 3);
        var b = new BaitQueueEntry(player, 1, "corn", 2);
        assertTrue(repository.replaceBaitSlot(null, a));
        assertTrue(repository.replaceBaitSlot(null, b));
        var before = repository.loadBaitEdit(player, SEASON, 0, 1);
        var changed = new BaitQueueEntry(player, 1, "corn", 1);
        assertTrue(repository.replaceBaitSlot(b, changed));
        assertFalse(repository.swapBaitEdit(before));
        var staleSeason = repository.loadBaitEdit(player, SEASON, 0, 1);
        repository.updateSeason(player, SEASON, state -> state.withFatigueAdded(1, 100));
        assertFalse(repository.swapBaitEdit(staleSeason));
        assertEquals(List.of(a, changed), repository.loadBaitQueue(player));
        assertTrue(repository.swapBaitEdit(repository.loadBaitEdit(player, SEASON, 0, 1)));
        assertEquals(List.of(new BaitQueueEntry(player, 0, "corn", 1), new BaitQueueEntry(player, 1, "worm", 3)),
            repository.loadBaitQueue(player));
    }

    @Test
    void baitDepositPublishesOnceFromItsOriginalNodeAndPreservesConcurrentConsumption() throws Exception {
        repository.initialize();
        var initial = new BaitQueueEntry(player, 0, "worm", 6);
        var corn = new BaitQueueEntry(player, 1, "corn", 3);
        assertTrue(repository.replaceBaitSlot(null, initial));
        assertTrue(repository.replaceBaitSlot(null, corn));
        var edit = repository.loadBaitEdit(player, SEASON, 0, 0);
        var draft = new xuanmo.arcartxsuite.fishing.storage.FishingRepository.BaitDeposit(player, UUID.randomUUID(),
            "node", 400, 0, "worm", 4);
        assertTrue(repository.reserveBaitDeposit(edit, draft));
        assertFalse(repository.reserveBaitDeposit(edit, draft));
        assertEquals(List.of(initial, corn), repository.loadBaitQueue(player));
        assertFalse(repository.swapBaitEdit(repository.loadBaitEdit(player, SEASON, 0, 1)));

        assertTrue(repository.confirmBite(repository.loadBiteProfile(player, SEASON), true, () -> true));
        var otherNode = new xuanmo.arcartxsuite.fishing.storage.FishingRepository.BaitDeposit(player, draft.request(),
            "other", draft.boot(), 0, "worm", 4);
        assertFalse(repository.publishBaitDeposit(otherNode));
        assertTrue(repository.pendingBaitDeposits("other", "", 10).isEmpty());
        assertEquals(List.of(draft), repository.pendingBaitDeposits("node", "", 10));
        assertTrue(repository.publishBaitDeposit(draft));
        assertFalse(repository.publishBaitDeposit(draft));
        assertEquals(List.of(new BaitQueueEntry(player, 0, "worm", 9), corn), repository.loadBaitQueue(player));
        assertTrue(repository.pendingBaitDeposits("node", "", 10).isEmpty());
    }

    @Test
    void completionAwardsXpOnceEvenAfterAnOlderSnapshotArrives() throws Exception {
        repository.initialize();
        PlayerTaskInstance task = task();
        repository.saveTaskProgress(task.withProgress(2), SEASON);
        assertTrue(repository.completeTask(task.withProgress(3), SEASON, 30, CURVE, 50).awarded());
        repository.saveTaskProgress(task.withProgress(1), SEASON);

        PlayerTaskInstance loaded = repository.loadTaskProgress(player, SEASON).get(0);
        assertEquals(3, loaded.currentProgress());
        assertTrue(loaded.completed());
        assertTrue(loaded.rewardClaimed());
        assertFalse(repository.completeTask(task.withProgress(3), SEASON, 30, CURVE, 50).awarded());
        assertEquals(30, repository.loadSeasonData(player, SEASON).currentXp());
    }

    @Test
    void experienceWriteFailureRollsBackCompletionAndPermitsRetry() throws Exception {
        repository.initialize();
        PlayerTaskInstance task = task();
        repository.saveTaskProgress(task.withProgress(2), SEASON);
        execute("""
            CREATE TRIGGER reject_xp BEFORE INSERT ON axs_fishing_season
            BEGIN SELECT RAISE(ABORT, 'xp unavailable'); END
            """);
        assertNull(repository.completeTask(task.withProgress(3), SEASON, 30, CURVE, 50));
        assertEquals(2, repository.loadTaskProgress(player, SEASON).get(0).currentProgress());
        assertFalse(repository.loadTaskProgress(player, SEASON).get(0).completed());

        execute("DROP TRIGGER reject_xp");
        assertTrue(repository.completeTask(task.withProgress(3), SEASON, 30, CURVE, 50).awarded());
        assertEquals(30, repository.loadSeasonData(player, SEASON).currentXp());
    }

    @Test
    void seasonTaskIsStableAcrossAssignmentDatesAndScopedBySeason() throws Exception {
        repository.initialize();
        PlayerTaskInstance task = new PlayerTaskInstance(player, "catch", TaskCategory.SEASON, 1, LocalDate.of(2026, 11, 9));
        PlayerTaskInstance later = new PlayerTaskInstance(player, "catch", TaskCategory.SEASON, 1, task.assignedDate().plusDays(1));
        assertTrue(repository.completeTask(task.withProgress(1), SEASON, 30, CURVE, 50).awarded());
        assertFalse(repository.completeTask(later.withProgress(1), SEASON, 30, CURVE, 50).awarded());
        assertTrue(repository.loadTaskProgress(player, "another-season").isEmpty());
        assertTrue(repository.completeTask(later.withProgress(1), "another-season", 30, CURVE, 50).awarded());
    }

    @Test
    void rewardReceiptsDistinguishConfirmedFailureUnknownAndDelivery() throws Exception {
        repository.initialize();
        String key = "level-reward";
        assertEquals(RewardReservation.ACQUIRED, repository.reserveReward(player, SEASON, key, 0));
        assertEquals(RewardReservation.IN_DOUBT, repository.reserveReward(player, SEASON, key, 0));
        repository.finishReward(player, SEASON, key, 0, false);
        assertEquals(RewardReservation.ACQUIRED, repository.reserveReward(player, SEASON, key, 0));
        repository.finishReward(player, SEASON, key, 0, true);
        assertEquals(RewardReservation.DELIVERED, repository.reserveReward(player, SEASON, key, 0));
        assertEquals(RewardReservation.ACQUIRED, repository.reserveReward(player, SEASON, key, 1));
        repository.finishReward(player, SEASON, key, 1, true);
        repository.markRewardComplete(player, SEASON, key);
        assertTrue(repository.completedRewardKeys(player, SEASON).contains(key));
        assertTrue(repository.completedRewardKeys(player, "another-season").isEmpty());
    }

    @Test
    void settlementRetryOnlyEnqueuesTheFailedStep() throws Exception {
        repository.initialize();
        var pending = new RecordingPendingService();
        var dispatcher = new RewardDispatcher(repository, pending, Logger.getLogger("FishingRepositoryTest"), SEASON);
        dispatcher.configureDeliveryOwner(() -> "local:test");
        var reward = new SeasonRewardDefinition("settlement-command", 0, "command", null, null, null, 0,
            List.of("say first", "say second"), null);
        String key = RewardDispatcher.rewardKey("settlement", reward.rewardId());

        assertFalse(dispatcher.dispatch(player, key, reward, "settlement"));
        assertTrue(repository.completedRewardKeys(player, SEASON).isEmpty());
        var edited = new SeasonRewardDefinition(reward.rewardId(), 0, "command", null, null, null, 0,
            List.of("say changed"), null);
        assertTrue(dispatcher.dispatch(player, key, edited, "settlement"));
        assertTrue(dispatcher.dispatch(player, key, reward, "settlement"));
        assertEquals(3, pending.calls.size());
        assertNotEquals(pending.calls.get(0), pending.calls.get(1));
        assertEquals(pending.calls.get(1), pending.calls.get(2));
        assertEquals(pending.received.get(1), pending.received.get(2));
        assertTrue(repository.completedRewardKeys(player, SEASON).contains(key));
    }

    @Test
    void seasonItemKeepsItsExactIconSpecificationForDurableHostDelivery() throws Exception {
        repository.initialize();
        var pending = new RecordingPendingService();
        var dispatcher = new RewardDispatcher(repository, pending, Logger.getLogger("FishingRepositoryTest"), SEASON);
        dispatcher.configureDeliveryOwner(() -> "local:test");
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString("item:\n  material: COD\n  amount: 3\n  name: '赛季奖励'\n  lore: ['原始说明']\n");
        var item = xuanmo.arcartxsuite.api.icon.IconDefinition.load(yaml, "item");
        var reward = new SeasonRewardDefinition("season-cod", 0, "item", null, item, null, 0, null, null);
        var key = RewardDispatcher.rewardKey("settlement", reward.rewardId());
        assertTrue(dispatcher.dispatch(player, key, reward, "结算"));
        assertTrue(dispatcher.dispatch(player, key, reward, "结算"));
        assertEquals(1, pending.received.size());
        var received = pending.received.get(0);
        assertEquals(PendingReward.Type.ITEM_SPEC, received.type());
        assertEquals(item, new com.google.gson.Gson().fromJson(received.payload(), PendingReward.ItemSpec.class).item());
    }

    @Test
    void catchHistoryDeduplicatesReplay() throws Exception {
        repository.initialize();

        FishingCatchRecord local = new FishingCatchRecord(UUID.randomUUID(), player, "fish", "鲤鱼:春季", "common", 5, true, 2);
        FishingCatchRecord remote = FishingCatchRecord.decodeCrossServer(local.encodeCrossServer(), 2);
        assertEquals(local, remote);
        repository.saveCatchRecord(local);
        repository.saveCatchRecord(remote);

        List<FishingCatchRecord> stored = repository.loadCatchRecords(player, 10);
        assertEquals(1, stored.size());
        assertEquals(local.eventId(), stored.get(0).eventId());
        assertEquals("鲤鱼:春季", stored.get(0).fishName());
    }

    @Test
    void captureCommitRollsBackProgressAndHistoryOnFailureAndRejectsReplay() throws Exception {
        repository.initialize();
        var first = new FishingCatchRecord(UUID.randomUUID(), player, "carp", "鲤鱼", "common", 10, true, 51);
        execute("""
            CREATE TRIGGER reject_catch BEFORE INSERT ON axs_fishing_permanent
            BEGIN SELECT RAISE(ABORT, 'permanent write unavailable'); END
            """);
        assertThrows(SQLException.class, () -> repository.settleCatch(first, SEASON,
            state -> state.withXpAdded(8, CURVE, 50)));
        assertEquals(FishingSeasonData.empty(player, SEASON), repository.loadSeasonData(player, SEASON));
        assertTrue(repository.loadCollection(player).isEmpty());
        assertTrue(repository.loadCatchRecords(player, 10).isEmpty());

        execute("DROP TRIGGER reject_catch");
        var result = repository.settleCatch(first, SEASON, state -> state.withXpAdded(8, CURVE, 50));
        assertTrue(result.firstCatch());
        assertEquals(1, result.permanent().perfectCatches());
        assertThrows(SQLException.class, () -> repository.settleCatch(first, SEASON,
            state -> state.withXpAdded(8, CURVE, 50)));

        var second = new FishingCatchRecord(UUID.randomUUID(), player, "carp", "鲤鱼", "common", 19, false, 52);
        var next = repository.settleCatch(second, SEASON, state -> state.withXpAdded(7, CURVE, 50));
        assertFalse(next.firstCatch());
        assertEquals(15, next.season().after().currentXp());
        assertEquals(new FishingPermanentData(player, 2, 1, 0), next.permanent());
        assertEquals(new FishCollectionEntry(player, "carp", 2, 19, 51), repository.loadCollectionEntry(player, "carp"));
        assertEquals(2, repository.loadCatchRecords(player, 10).size());
    }

    @Test
    void failureToStoreRewardsRollsBackTheWholeCapture() throws Exception {
        repository.initialize();
        execute("""
            CREATE TRIGGER reject_delivery BEFORE INSERT ON axs_fishing_catch_delivery
            BEGIN SELECT RAISE(ABORT, 'delivery unavailable'); END
            """);
        var record = new FishingCatchRecord(UUID.randomUUID(), player, "carp", "鲤鱼", "common", 12, false, 2);
        var reward = PendingReward.command(player, "fishing", List.of("say catch"), "test");
        var batch = new xuanmo.arcartxsuite.fishing.storage.FishingRepository.CatchRewards("owner", false,
            List.of(new xuanmo.arcartxsuite.fishing.storage.FishingRepository.ConditionalReward(reward, false)));
        assertThrows(SQLException.class, () -> repository.settleCatch(record, SEASON,
            state -> state.withXpAdded(10, CURVE, 50), batch));
        assertTrue(repository.loadCatchRecords(player, 10).isEmpty());
        assertTrue(repository.loadCollection(player).isEmpty());
        assertEquals(FishingSeasonData.empty(player, SEASON), repository.loadSeasonData(player, SEASON));
        assertEquals(FishingPermanentData.empty(player), repository.loadPermanentData(player));
    }

    @Test
    void taskExperienceAndTalentChangesUseTheLatestSeasonRow() throws Exception {
        repository.initialize();
        repository.saveSeasonData(new FishingSeasonData(player, SEASON, 2, 20, 2, 0, Map.of("skill", 2), 11, false));
        var task = task().withProgress(3);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var reset = workers.submit(() -> repository.updateSeason(player, SEASON, state -> {
                entered.countDown();
                try {
                    assertTrue(release.await(3, java.util.concurrent.TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
                return state.withTalentsReset();
            }));
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
            var grant = workers.submit(() -> repository.completeTask(task, SEASON, 10, CURVE, 50));
            release.countDown();
            assertEquals(2, reset.get(3, java.util.concurrent.TimeUnit.SECONDS).after().unspentTalentPoints());
            assertTrue(grant.get(3, java.util.concurrent.TimeUnit.SECONDS).awarded());
            var after = repository.loadSeasonData(player, SEASON);
            assertEquals(30, after.currentXp());
            assertEquals(2, after.unspentTalentPoints());
            assertEquals(11, after.fatigue());
            assertTrue(after.talents().isEmpty());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void fatigueRecoveryDoesNotReplaceExperienceOrOverwriteChangedFatigue() throws Exception {
        repository.initialize();
        var before = new FishingSeasonData(player, SEASON, 2, 47, 2, 1,
            Map.of("rest_boost", 1), 9, true);
        repository.saveSeasonData(before);
        repository.recoverFatigue(player, SEASON, 8, 6);
        assertEquals(before, repository.loadSeasonData(player, SEASON));
        repository.recoverFatigue(player, SEASON, 9, 7);
        assertEquals(before.withFatigueReduced(2), repository.loadSeasonData(player, SEASON));
    }

    private PlayerTaskInstance task() {
        return new PlayerTaskInstance(player, "catch", TaskCategory.DAILY, 3, LocalDate.of(2026, 11, 9));
    }

    private void execute(String sql) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static final class RecordingPendingService implements PendingRewardService {
        private final List<String> calls = new ArrayList<>();
        private final List<PendingReward> received = new ArrayList<>();

        @Override
        public boolean savePendingBatchConfirmed(String key, List<PendingReward> rewards) {
            calls.add(key);
            received.addAll(rewards);
            return calls.size() != 2;
        }

        @Override public void savePending(PendingReward reward) { throw new UnsupportedOperationException(); }
        @Override public List<PendingReward> loadPending(UUID uuid) { throw new UnsupportedOperationException(); }
        @Override public List<PendingReward> loadPending(String source, UUID uuid) { throw new UnsupportedOperationException(); }
        @Override public void markDelivered(long id) { throw new UnsupportedOperationException(); }
        @Override public void incrementAttempts(long id) { throw new UnsupportedOperationException(); }
    }
}
