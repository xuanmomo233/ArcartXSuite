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
        var access = mock(AbyssRunAccess.class);
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
        await(database.stopped().toCompletableFuture());
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

    @Test void interruptedCommitInTheSameModuleIsUnknownAndNeverReplayed() throws Exception {
        UUID operation = reserve();
        mutateLease(operation, lease -> lease.state = AbyssReviveAccess.State.COMMITTING);
        var receipt = await(revives.query("xc", operation));
        assertEquals(AbyssReviveAccess.State.UNKNOWN, receipt.state());
        assertEquals("COMMIT_INTERRUPTED", receipt.code());
        assertEquals(AbyssReviveAccess.State.UNKNOWN, await(revives.commit("xc", operation)).state());
        verify(service, never()).tryRevivePlayer(any(), any());
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

    @Test void departureJournalReconcilesAfterTheWorldHasBeenDestroyed() throws Exception {
        var operations = new RunOperations(service, ledger);
        var request = leave(1);
        when(service.playerExit(run, player, LeaveReason.QUIT)).thenReturn(false);
        await(operations.execute(request));
        ledger.departed(run, playerId);
        when(service.instances().getInstance(runId)).thenReturn(null);
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

    @Test void anAcceptedRewardFinishesDuringRetirementAndRemainsDeduplicatedAfterRestart() throws Exception {
        var host = mock(PendingRewardService.class);
        ledger.rewards(host);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(host.savePendingBatchConfirmed(anyString(), anyList())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(4, TimeUnit.SECONDS));
            return true;
        });
        var batch = List.of(PendingRewardService.PendingReward.currency(playerId, "abyss", "money", 2, "loot"));
        var entries = RunLedger.describe("stop-batch", "reward:test", batch);
        var receipt = ledger.submit(runId, "stop-batch", batch, entries);
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            when(service.running()).thenReturn(false);
            database.shutdown();
            assertFalse(database.stopped().toCompletableFuture().isDone());
            assertFalse(receipt.isDone());
        } finally { release.countDown(); }
        await(receipt);
        await(database.stopped().toCompletableFuture());
        verify(host, never()).flushPlayer(any());

        var reopened = new PlayerDataManager(repository, Logger.getAnonymousLogger());
        try {
            var restarted = new RunLedger(service, repository, reopened);
            restarted.rewards(host);
            when(service.running()).thenReturn(true);
            await(restarted.submit(runId, "stop-batch", batch, entries));
            assertEquals(State.HOST_ACCEPTED, await(restarted.snapshot(runId, playerId, null, 10)).entries().get(0).state());
            verify(host, times(1)).savePendingBatchConfirmed("stop-batch", batch);
        } finally {
            reopened.shutdown();
            await(reopened.stopped().toCompletableFuture());
        }
    }

    private UUID reserve() throws Exception {
        UUID operation = UUID.randomUUID();
        long revision = await(revives.reviveStatus(runId, playerId)).revision();
        assertEquals(AbyssReviveAccess.State.RESERVED,
            await(revives.reserve("xc", operation, runId, playerId, "rescue", revision)).state());
        return operation;
    }

    private record NativeFixture(NativeRunGameplay game, java.util.concurrent.atomic.AtomicLong clock,
                                 CompletableFuture<Void> started, CompletableFuture<Void> finished) { }

    private NativeFixture nativeFixture(Player... peers) throws Exception {
        var config = new YamlConfiguration();
        config.set("revive-limit", 1);
        config.set("native-rescue.enabled", true);
        config.set("native-rescue.channel-seconds", 1);
        config.set("native-rescue.wipe-window-seconds", 3);
        var requirements = EntryRequirements.load(config);
        when(run.getTemplate().requirements()).thenReturn(requirements);
        when(service.instances().getInstance(player)).thenReturn(run);
        var members = new java.util.ArrayList<Player>();
        members.add(player);
        members.addAll(List.of(peers));
        when(run.getPlayers()).thenReturn(members);
        var origin = new org.bukkit.Location(run.getWorld(), 0, 64, 0);
        when(player.getLocation()).thenReturn(origin);
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var game = new NativeRunGameplay(service, clock::get);
        when(service.nativeRuns()).thenReturn(game);
        when(service.reviveAccess()).thenReturn(revives);
        when(service.runLootAccess()).thenReturn(ledger);
        when(service.reviveTransactions()).thenReturn(revives);
        var ui = fake(xuanmo.arcartxsuite.abyss.ui.AbyssUiService.class);
        when(service.uiFacade()).thenReturn(ui);
        var opened = new CompletableFuture<Void>();
        var started = new CompletableFuture<Void>();
        var finished = new CompletableFuture<Void>();
        doAnswer(call -> { opened.complete(null); return null; }).when(ui)
            .openRunPanel(eq(player), anyString(), anyString(), anyList(), anyBoolean(), anyBoolean());
        doAnswer(call -> { started.complete(null); return null; }).when(ui).closeRunPanel(player);
        doAnswer(call -> {
            if (call.<String>getArgument(0).contains("救援成功")) finished.complete(null);
            return null;
        }).when(player).sendMessage(anyString());
        game.open(player, "rescue");
        await(opened);
        return new NativeFixture(game, clock, started, finished);
    }

    @Test void nativeSelfRescueUsesTheExistingQuotaWithoutAnExternalOwner() throws Exception {
        var fixture = nativeFixture();
        when(service.tryRevivePlayer(run, player)).thenAnswer(call -> { dead.set(false); return true; });
        fixture.game().rescue(player, playerId);
        await(fixture.started());
        fixture.clock().set(2000);
        fixture.game().tick();
        await(fixture.finished());
        assertEquals(1, await(revives.reviveStatus(runId, playerId)).used());
        verify(service, times(1)).tryRevivePlayer(run, player);
        assertEquals("NOT_OWNER", await(revives.reserve("foreign", UUID.randomUUID(), runId, playerId, "external", 0)).code());
    }

    @Test void movingOrDamageDuringNativeRescueReleasesTheCredit() throws Exception {
        var fixture = nativeFixture();
        fixture.game().rescue(player, playerId);
        await(fixture.started());
        var moved = new org.bukkit.Location(run.getWorld(), 1, 64, 0);
        when(player.getLocation()).thenReturn(moved);
        fixture.game().tick();
        assertEquals(1, await(revives.reviveStatus(runId, playerId)).remaining());
        verify(service, never()).tryRevivePlayer(any(), any());
        var second = nativeFixture();
        second.game().rescue(player, playerId);
        await(second.started());
        second.game().interrupt(playerId, "受伤中断救援");
        assertEquals(1, await(revives.reviveStatus(runId, playerId)).remaining());
        verify(service, never()).tryRevivePlayer(any(), any());
    }

    @Test void nativeTeammateRescueChargesTheTargetRatherThanTheRescuer() throws Exception {
        dead.set(false);
        var peer = mock(Player.class);
        when(peer.getUniqueId()).thenReturn(teammate);
        when(peer.getName()).thenReturn("Peer");
        var world = run.getWorld();
        when(peer.getWorld()).thenReturn(world);
        when(peer.isOnline()).thenReturn(true);
        var server = (org.bukkit.Server)serverField.get(null);
        when(server.getPlayer(teammate)).thenReturn(peer);
        var peerDead = new AtomicBoolean(true);
        when(run.isPlayerCurrentlyDead(peer)).thenAnswer(call -> peerDead.get());
        when(run.getDeaths(teammate)).thenReturn(1);
        var fixture = nativeFixture(peer);
        when(service.tryRevivePlayer(run, peer)).thenAnswer(call -> { peerDead.set(false); return true; });
        fixture.game().rescue(player, teammate);
        await(fixture.started());
        fixture.clock().set(2000);
        fixture.game().tick();
        await(fixture.finished());
        assertEquals(1, await(revives.reviveStatus(runId, teammate)).used());
        assertEquals(0, await(revives.reviveStatus(runId, playerId)).used());
        verify(service, times(1)).tryRevivePlayer(run, peer);
        verify(service, never()).tryRevivePlayer(run, player);
    }

    @Test void lootPanelCannotStartARescueEvenWithAForgedTarget() throws Exception {
        var fixture = nativeFixture();
        fixture.game().open(player, "loot");
        when(service.reviveTransactions()).thenThrow(new AssertionError("Read-only loot panel started a rescue"));
        fixture.game().rescue(player, playerId);
        verify(service, never()).tryRevivePlayer(any(), any());
    }

    @Test void staleNativePanelCannotLeaveANewerRun() throws Exception {
        var fixture = nativeFixture();
        var other = fake(AbyssInstance.class);
        when(other.getInstanceId()).thenReturn(UUID.randomUUID());
        when(service.instances().getInstance(player)).thenReturn(other);
        fixture.game().leaveFromPanel(player);
        verify(service, never()).playerExit(any(), any(), any());
    }

    @Test void nativeLeaveReusesTheDurableOperationWithoutExternalOwnership() throws Exception {
        nativeFixture();
        var operations = new RunOperations(service, ledger);
        when(service.runOperations()).thenReturn(operations);
        when(service.playerExit(run, player, LeaveReason.QUIT)).thenAnswer(call -> { member.set(false); return true; });
        assertEquals("APPLIED", await(operations.leaveNative(runId, playerId)).code());
        assertEquals("APPLIED", await(operations.leaveNative(runId, playerId)).code());
        verify(service, times(1)).playerExit(run, player, LeaveReason.QUIT);
    }

    @Test void oldWipeWindowDoesNotEndARecoveredOrNewWipe() throws Exception {
        var fixture = nativeFixture();
        var deadlines = new java.util.ArrayList<Runnable>();
        doAnswer(call -> { deadlines.add(call.getArgument(1)); return null; }).when(service).scheduleOnce(eq(run), any(), anyLong());
        when(run.getAlivePlayerCount()).thenReturn(0);
        assertTrue(fixture.game().teamWipe(run));
        fixture.game().revived(run);
        assertTrue(fixture.game().teamWipe(run));
        deadlines.get(0).run();
        verify(service, never()).failDungeon(any(), any());
        deadlines.get(1).run();
        verify(service).failDungeon(run, FailReason.TEAM_WIPE);
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
