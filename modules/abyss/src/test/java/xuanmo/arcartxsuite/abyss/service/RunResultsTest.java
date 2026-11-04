package xuanmo.arcartxsuite.abyss.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunResultsTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void databaseOutageLeavesAReplayableResultWithoutPublishing() throws Exception {
        var service = fake(AbyssService.class);
        when(service.running()).thenReturn(true);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(service).runOnMain(any());
        var repository = fake(JdbcAbyssRepository.class);
        when(repository.insertRunResult(any(), anyString(), anyLong(), anyString()))
            .thenThrow(new java.sql.SQLException("unavailable")).thenReturn(true);
        var database = fake(PlayerDataManager.class);
        when(database.submitDatabase(any())).thenAnswer(call -> {
            PlayerDataManager.DatabaseRead<?> task = call.getArgument(0);
            try { return CompletableFuture.completedFuture(task.execute()); }
            catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
        });
        var results = new RunResults(service);
        results.configure(repository, database, directory);
        var instance = new AbyssInstance(UUID.randomUUID(), fake(AbyssTemplate.class), null);
        UUID player = UUID.randomUUID();
        results.admitted(instance, new AdmissionAccess.Operation(new Intent("xc", UUID.randomUUID(),
            player, List.of(player), "example", null, null, null)));
        results.finish(instance, Outcome.COMPLETED, "COMPLETED");
        var file = directory.resolve(instance.getInstanceId() + ".yml");
        assertTrue(java.nio.file.Files.isRegularFile(file));
        verify(service, never()).publishRunResult(any());
        var reopened = new RunResults(service);
        reopened.configure(repository, database, directory);
        reopened.replay();
        verify(service).publishRunResult(argThat(event -> event.runId().equals(instance.getInstanceId())));
        assertFalse(java.nio.file.Files.exists(file));
    }

    @Test void terminalSnapshotIncludesOfflineMembersAndNotifiesOnlyAfterCommit() throws Exception {
        var service = fake(AbyssService.class);
        when(service.running()).thenReturn(true);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(service).runOnMain(any());
        var repository = fake(JdbcAbyssRepository.class);
        var database = fake(PlayerDataManager.class);
        var pending = new CompletableFuture<Boolean>();
        var writes = new ArrayList<PlayerDataManager.DatabaseRead<Boolean>>();
        when(database.<Boolean>submitDatabase(any())).thenAnswer(call -> {
            writes.add(call.getArgument(0));
            return pending;
        });
        var results = new RunResults(service);
        results.configure(repository, database);
        var template = fake(AbyssTemplate.class);
        var instance = new AbyssInstance(UUID.randomUUID(), template, null);
        var leader = UUID.randomUUID();
        var offline = UUID.randomUUID();
        var request = UUID.randomUUID();
        var intent = new Intent("xc", request, leader, List.of(leader, offline), "test", "route", "hard", "v1");
        results.admitted(instance, new AdmissionAccess.Operation(intent));
        results.started(instance);
        results.member(instance, offline, MemberState.DISCONNECTED, "DISCONNECT", 2);
        var notices = org.mockito.ArgumentCaptor.forClass(RunNotice.class);
        verify(service, times(3)).publishRunNotice(notices.capture());
        assertEquals(List.of(1L, 2L, 3L), notices.getAllValues().stream().map(RunNotice::sequence).toList());
        assertEquals(NoticeKind.DISCONNECTED, notices.getValue().kind());
        results.finish(instance, Outcome.COMPLETED, "COMPLETED");
        results.finish(instance, Outcome.CANCELLED, "DUPLICATE");
        assertEquals(1, writes.size());
        verify(service, never()).publishRunResult(any());
        var snapshot = new ArrayList<String>();
        when(repository.insertRunResult(eq(instance.getInstanceId()), eq("xc"), anyLong(), anyString()))
            .thenAnswer(call -> { snapshot.add(call.getArgument(3)); return true; });
        pending.complete(writes.get(0).execute());
        verify(service).publishRunResult(argThat(event -> event.runId().equals(instance.getInstanceId())
            && event.requestId().equals(request)));
        var result = RunResults.decode(snapshot.get(0));
        assertEquals(List.of(leader, offline), result.roster().stream().map(Member::player).toList());
        assertEquals(MemberState.DISCONNECTED, result.roster().get(1).state());
        assertEquals(2, result.roster().get(1).deaths());
        assertEquals(Outcome.COMPLETED, result.outcome());
        assertEquals("hard", result.difficulty());
    }

    private static <T> T fake(Class<T> type) {
        return mock(type, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
    }
}
