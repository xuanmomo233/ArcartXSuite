package xuanmo.arcartxsuite.abyss.service;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.abyss.data.*;
import xuanmo.arcartxsuite.abyss.instance.*;
import xuanmo.arcartxsuite.abyss.template.*;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent;
import xuanmo.arcartxsuite.api.capability.AbyssReviveAccess;
import xuanmo.arcartxsuite.api.capability.AbyssRunAccess;
import xuanmo.arcartxsuite.api.capability.AbyssRunLootAccess.*;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunTransactionsTest {
    @TempDir Path directory;
    private Object previousServer;
    private java.lang.reflect.Field serverField;
    private JdbcAbyssRepository repository;
    private PlayerDataManager database;
    private AbyssService service;
    private AbyssInstance run;
    private RunLedger ledger;
    private ReviveTransactions revives;
    private Player player;
    private final UUID runId = UUID.randomUUID(), playerId = UUID.randomUUID(), teammate = UUID.randomUUID();
    private final AtomicBoolean member = new AtomicBoolean(true), dead = new AtomicBoolean(true);

    @BeforeEach void setup() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        var server = mock(org.bukkit.Server.class);
        when(server.isPrimaryThread()).thenReturn(true);
        serverField.set(null, server);
        var ds = mock(DataSource.class);
        when(ds.getConnection()).thenAnswer(call -> DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("runs.db")));
        repository = new JdbcAbyssRepository(ds, StorageDescriptor.sqlite("runs.db"), directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        database = new PlayerDataManager(repository, Logger.getAnonymousLogger());
        service = fake(AbyssService.class);
        when(service.running()).thenReturn(true);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(service).runOnMain(any());
        var instances = fake(AbyssInstanceManager.class);
        when(service.instances()).thenReturn(instances);
        run = fake(AbyssInstance.class);
        when(instances.getInstance(runId)).thenReturn(run);
        when(run.getInstanceId()).thenReturn(runId);
        when(run.admitted()).thenReturn(true);
        when(run.getStatus()).thenReturn(AbyssStatus.ACTIVE);
        when(run.getPlayerUUIDs()).thenReturn(java.util.Set.of(playerId, teammate));
        when(run.hasPlayer(playerId)).thenAnswer(call -> member.get());
        when(run.hasPlayer(teammate)).thenReturn(true);
        when(run.getDeaths(playerId)).thenReturn(1);
        var world = mock(World.class);
        when(run.getWorld()).thenReturn(world);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(server.getPlayer(playerId)).thenReturn(player);
        when(run.isPlayerCurrentlyDead(player)).thenAnswer(call -> dead.get());
        var template = fake(AbyssTemplate.class);
        var config = new YamlConfiguration();
        config.set("external-control-owner", "xc");
        config.set("revive-limit", 1);
        when(template.requirements()).thenReturn(EntryRequirements.load(config));
        when(run.getTemplate()).thenReturn(template);
        when(service.admissionOperation(run)).thenReturn(new AdmissionAccess.Operation(
            new Intent("xc", UUID.randomUUID(), playerId, List.of(playerId, teammate), "example", null, null, null)));
        var access = fake(AbyssRunAccess.class);
        when(access.snapshot(runId)).thenReturn(new AbyssRunAccess.Snapshot(runId, 1, "ACTIVE", List.of()));
        when(service.runAccess()).thenReturn(access);
        ledger = new RunLedger(service, repository, database);
        when(service.runLedger()).thenReturn(ledger);
        ledger.admitted(run);
        await(database.submitDatabase(() -> null));
        revives = new ReviveTransactions(service, ledger);
    }

    @AfterEach void cleanup() throws Exception {
        database.shutdown();
        repository.shutdown();
        serverField.set(null, previousServer);
    }

    @Test void concurrentRescueReservesOnlyOneLastCredit() throws Exception {
        long revision = await(revives.reviveStatus(runId, playerId)).revision();
        var first = revives.reserve("xc", UUID.randomUUID(), runId, playerId, "rescue", revision);
        var second = revives.reserve("xc", UUID.randomUUID(), runId, playerId, "rescue", revision);
        var results = List.of(await(first), await(second));
        assertEquals(1, results.stream().filter(receipt -> receipt.state() == AbyssReviveAccess.State.RESERVED).count());
        assertEquals(0, await(revives.reviveStatus(runId, playerId)).remaining());
    }

    @Test void commitAndResponseReplayResurrectExactlyOnce() throws Exception {
        UUID operation = reserve();
        when(service.tryRevivePlayer(run, player)).thenAnswer(call -> { dead.set(false); return true; });
        var first = await(revives.commit("xc", operation));
        var reopened = new ReviveTransactions(service, ledger);
        assertEquals(AbyssReviveAccess.State.COMMITTED, first.state());
        assertEquals(first, await(reopened.commit("xc", operation)));
        assertEquals(1, await(reopened.reviveStatus(runId, playerId)).used());
        verify(service, times(1)).tryRevivePlayer(run, player);
    }

    @Test void changedRequestCannotReuseAnOperation() throws Exception {
        long revision = await(revives.reviveStatus(runId, playerId)).revision();
        UUID operation = UUID.randomUUID();
        await(revives.reserve("xc", operation, runId, playerId, "rescue", revision));
        assertEquals("OPERATION_CONFLICT", await(revives.reserve("xc", operation, runId, playerId, "different", revision)).code());
    }

    @Test void interruptionAndFailedTeleportDoNotConsumeCredit() throws Exception {
        UUID operation = reserve();
        assertEquals(AbyssReviveAccess.State.RELEASED, await(revives.release("xc", operation)).state());
        assertEquals(1, await(revives.reviveStatus(runId, playerId)).remaining());
        operation = reserve();
        when(service.tryRevivePlayer(run, player)).thenReturn(false);
        assertEquals(AbyssReviveAccess.State.RELEASED, await(revives.commit("xc", operation)).state());
        assertEquals(0, await(revives.reviveStatus(runId, playerId)).used());
    }

    @Test void leavingAndNewDeathRejectTheOldRescue() throws Exception {
        UUID operation = reserve();
        member.set(false);
        assertEquals("NOT_A_MEMBER", await(revives.commit("xc", operation)).code());
        member.set(true);
        operation = reserve();
        when(run.getDeaths(playerId)).thenReturn(2);
        assertEquals("DEATH_CHANGED", await(revives.commit("xc", operation)).code());
        verify(service, never()).tryRevivePlayer(any(), any());
    }

    @Test void expiryAndRestartDuringCommitHaveDifferentOutcomes() throws Exception {
        UUID operation = reserve();
        mutateLease(operation, lease -> lease.expires = 0);
        assertEquals(AbyssReviveAccess.State.RELEASED, await(revives.query("xc", operation)).state());
        UUID uncertain = reserve();
        mutateLease(uncertain, lease -> { lease.state = AbyssReviveAccess.State.COMMITTING; lease.epoch = UUID.randomUUID(); });
        assertEquals(AbyssReviveAccess.State.UNKNOWN, await(new ReviveTransactions(service, ledger).query("xc", uncertain)).state());
        assertEquals(0, await(revives.reviveStatus(runId, playerId)).remaining());
    }

    @Test void leaveIsDurableAndAffectsOnlyTheRequestedMember() throws Exception {
        when(service.playerExit(run, player, LeaveReason.QUIT)).thenAnswer(call -> { member.set(false); return true; });
        var operations = new RunOperations(service, ledger);
        var request = leave(1);
        assertEquals(AbyssRunAccess.OperationState.COMPLETED, await(operations.execute(request)).state());
        var reopened = new RunOperations(service, ledger);
        assertEquals("APPLIED", await(reopened.query("xc", runId, request.operationId())).code());
        assertEquals("APPLIED", await(reopened.execute(request)).code());
        assertTrue(run.hasPlayer(teammate));
        verify(service, times(1)).playerExit(run, player, LeaveReason.QUIT);
    }

    @Test void pendingReturnCanRetryAndOldRevisionIsRejected() throws Exception {
        var operations = new RunOperations(service, ledger);
        assertEquals("STALE_REVISION", await(operations.execute(leave(0))).code());
        var request = leave(1);
        when(service.playerExit(run, player, LeaveReason.QUIT)).thenReturn(false);
        assertEquals("RETURN_PENDING", await(operations.execute(request)).code());
        when(service.playerExit(run, player, LeaveReason.QUIT)).thenAnswer(call -> { member.set(false); return true; });
        assertEquals("APPLIED", await(operations.execute(request)).code());
    }

    @Test void lostFinalLeaveReceiptReconcilesWithoutAnotherTeleport() throws Exception {
        var operations = new RunOperations(service, ledger);
        var request = leave(1);
        when(service.playerExit(run, player, LeaveReason.QUIT)).thenReturn(false);
        await(operations.execute(request));
        member.set(false);
        assertEquals("APPLIED", await(new RunOperations(service, ledger).query("xc", runId, request.operationId())).code());
        verify(service, times(1)).playerExit(any(), any(), any());
    }

    @Test void lootIsFilteredReadOnlyAndCursorConflictsAreExplicit() throws Exception {
        var privateEntry = loot("a", teammate, Scope.PERSONAL);
        var sharedEntry = loot("b", teammate, Scope.SHARED);
        ledger.record(runId, List.of(privateEntry, sharedEntry, loot("c", playerId, Scope.PERSONAL)));
        var first = await(ledger.snapshot(runId, playerId, null, 1));
        assertEquals(List.of(sharedEntry), first.entries());
        var next = await(ledger.snapshot(runId, playerId, first.nextCursor(), 1));
        assertEquals("c", next.entries().get(0).entryId());
        assertTrue(next.complete());
        assertEquals("FORBIDDEN", await(ledger.snapshot(runId, UUID.randomUUID(), null, 10)).code());
        ledger.record(runId, List.of(loot("d", playerId, Scope.PERSONAL)));
        assertEquals("STALE_CURSOR", await(ledger.snapshot(runId, playerId, first.nextCursor(), 1)).code());
        verify(service, never()).dispatchItemsConfirmed(any(), anyList(), anyString());
    }

    @Test void rewardOutageIsJournaledAndReplayedWithTheOriginalBatch() throws Exception {
        var host = mock(PendingRewardService.class);
        ledger.rewards(host);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(false);
        var batch = List.of(PendingRewardService.PendingReward.currency(playerId, "abyss", "money", 2, "loot"));
        var entries = RunLedger.describe("batch", "reward:test", batch);
        assertThrows(Exception.class, () -> await(ledger.submit(runId, "batch", batch, entries)));
        assertEquals(State.PENDING, await(ledger.snapshot(runId, playerId, null, 10)).entries().get(0).state());
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(true);
        ledger.replayRewards();
        await(database.submitMaintenance(() -> null));
        assertEquals(State.HOST_ACCEPTED, await(ledger.snapshot(runId, playerId, null, 10)).entries().get(0).state());
        await(ledger.submit(runId, "batch", batch, entries));
        assertEquals(1, await(ledger.snapshot(runId, playerId, null, 10)).entries().size());
        verify(host, times(2)).savePendingBatchConfirmed("batch", batch);
    }

    private UUID reserve() throws Exception {
        UUID operation = UUID.randomUUID();
        long revision = await(revives.reviveStatus(runId, playerId)).revision();
        assertEquals(AbyssReviveAccess.State.RESERVED,
            await(revives.reserve("xc", operation, runId, playerId, "rescue", revision)).state());
        return operation;
    }

    @Test void durableGrantRegistrationCanContinueWhileTheHostIsUnavailable() throws Exception {
        var host = mock(PendingRewardService.class);
        ledger.rewards(host);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(false);
        var batch = List.of(PendingRewardService.PendingReward.currency(playerId, "abyss", "money", 2, "loot"));
        await(ledger.registerGrant(runId, "grant", batch, RunLedger.describe("grant", "reward:test", batch)));
        assertEquals(State.PENDING, await(ledger.snapshot(runId, playerId, null, 10)).entries().get(0).state());
    }

    @Test void hiddenRewardOfferExposesOnlyTheSelectedPrize() throws Exception {
        var host = mock(PendingRewardService.class);
        ledger.rewards(host);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenReturn(true);
        UUID offer = UUID.randomUUID();
        ledger.linkOffer(offer, runId);
        assertTrue(await(ledger.snapshot(runId, playerId, null, 10)).entries().isEmpty());
        var batch = List.of(PendingRewardService.PendingReward.currency(playerId, "abyss", "money", 2, "selected"));
        var metadata = RunLedger.describe("slot/1", "flip", batch);
        assertTrue(ledger.submitGame(offer, 1, playerId, batch, metadata));
        assertTrue(ledger.submitGame(offer, 1, playerId, batch, metadata));
        var visible = await(ledger.snapshot(runId, playerId, null, 10));
        assertEquals(1, visible.entries().size());
        assertEquals(2, visible.entries().get(0).quantity());
        assertTrue(await(ledger.snapshot(runId, teammate, null, 10)).entries().isEmpty());
        verify(host, times(1)).savePendingBatchConfirmed("reward-game:" + offer + ":1", batch);
    }
    private void mutateLease(UUID operation, java.util.function.Consumer<ReviveTransactions.Lease> mutation) throws Exception {
        repository.runTransaction(runId, documents -> {
            var data = RunLedger.read(documents, runId);
            var lease = data.leases.get("revive/xc/" + operation);
            mutation.accept(lease);
            RunLedger.save(documents, data);
            documents.write("revive/xc/" + operation, playerId, RunLedger.JSON.toJson(lease));
            return null;
        });
    }
    private AbyssRunAccess.Operation leave(long revision) {
        return new AbyssRunAccess.Operation(UUID.randomUUID(), runId, revision, "xc",
            AbyssRunAccess.Action.LEAVE, playerId, null, null);
    }
    private LootEntry loot(String id, UUID owner, Scope scope) {
        return new LootEntry(id, "minecraft:diamond", "钻石", 1, null, "chest", owner, scope, State.HOST_ACCEPTED, 1);
    }
    private static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(10, TimeUnit.SECONDS); }
    private static <T> T fake(Class<T> type) {
        return mock(type, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
    }
}
