package xuanmo.arcartxsuite.abyss.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstanceManager;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;
import xuanmo.arcartxsuite.abyss.template.EntryRequirements;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionAccessTest {
    @Test void interruptedConsumptionRestoresTransactionIdentityWithoutReplayingProvider() throws Exception {
        withServer(() -> {
            var service = fake(AbyssService.class);
            var repository = fake(JdbcAbyssRepository.class);
            UUID request = UUID.randomUUID(), transaction = UUID.randomUUID();
            when(repository.loadAdmission("xc", request)).thenReturn(new JdbcAbyssRepository.AdmissionReceipt(
                "xc", request.toString(), "fingerprint", "phase: COMMITTING_COST\ncost:\n  owner: keys\n  id: "
                + transaction + "\n  state: RESERVED\n"));
            var access = new AdmissionAccess(service);
            var writer = fake(PlayerDataManager.class);
            when(writer.submitDatabase(any())).thenAnswer(call ->
                CompletableFuture.completedFuture(call.<PlayerDataManager.DatabaseRead<?>>getArgument(0).execute()));
            access.load(repository, writer);
            var state = access.lookupEntry("xc", request).join();
            assertEquals(AbyssAdmissionAccess.Phase.REVIEW_REQUIRED, state.phase());
            assertEquals(transaction, state.cost().transactionId());
            assertEquals(xuanmo.arcartxsuite.api.capability.AbyssEntryCostAccess.State.UNKNOWN, state.cost().lastKnownState());
            verifyNoInteractions(service);
        });
    }

    @Test void waitsForDurableReservationAndReturnsSameOutcomeForRetries() throws Exception {
        withServer(() -> {
            var service = fake(AbyssService.class);
            when(service.running()).thenReturn(true);
            when(service.instances()).thenReturn(new AbyssInstanceManager());
            doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(service).runOnMain(any());
            var repository = fake(JdbcAbyssRepository.class);
            when(repository.loadAdmissions()).thenReturn(List.of());
            var writer = fake(PlayerDataManager.class);
            var reserve = new CompletableFuture<Void>();
            when(writer.writeAdmission(any(), eq(true))).thenReturn(reserve);
            when(writer.writeAdmission(any(), eq(false))).thenReturn(CompletableFuture.completedFuture(null));
            var access = new AdmissionAccess(service);
            access.load(repository, writer);
            var intent = intent(null);
            when(service.preflightExternal(intent)).thenReturn(List.of());
            when(service.enterExternal(eq(intent), any())).thenReturn(AbyssService.EnterResult.fail("TEST_REJECTION"));
            var token = access.preflight(intent).token();
            assertEquals(AbyssAdmissionAccess.Phase.PREPARING, access.requestEntry(intent, token).phase());
            assertEquals(AbyssAdmissionAccess.Phase.PREPARING, access.requestEntry(intent, null).phase());
            verify(service, never()).enterExternal(any(), any());
            reserve.complete(null);
            assertEquals(AbyssAdmissionAccess.Phase.REJECTED, access.requestEntry(intent, token).phase());
            var conflict = new AbyssAdmissionAccess.Intent(intent.owner(), intent.requestId(), intent.actor(),
                intent.members(), intent.dungeon(), null, "hard", null);
            assertEquals(AbyssAdmissionAccess.Phase.CONFLICT, access.requestEntry(conflict, token).phase());
            verify(service, times(1)).enterExternal(eq(intent), any());
            verify(writer, times(1)).writeAdmission(any(), eq(true));
        });
    }

    @Test void policyHandlesAreIndependentAndOrdered() throws Exception {
        withServer(() -> {
            var service = fake(AbyssService.class);
            when(service.running()).thenReturn(true);
            var access = new AdmissionAccess(service);
            var template = fake(AbyssTemplate.class);
            when(template.requirements()).thenReturn(EntryRequirements.defaults());
            var last = access.registerPolicy("xc", 10, i -> List.of(AdmissionAccess.deny("LAST", "last")));
            var first = access.registerPolicy("xc", 0, i -> List.of(AdmissionAccess.deny("FIRST", "first")));
            assertEquals(List.of("FIRST", "LAST"), access.policies(intent(null), template).stream()
                .map(AbyssAdmissionAccess.Denial::code).toList());
            first.close();
            first.close();
            assertEquals("LAST", access.policies(intent(null), template).get(0).code());
            last.close();
            assertTrue(access.policies(intent(null), template).isEmpty());
        });
    }

    private static AbyssAdmissionAccess.Intent intent(String difficulty) {
        UUID player = UUID.randomUUID();
        return new AbyssAdmissionAccess.Intent("xc", UUID.randomUUID(), player, List.of(player),
            "example", null, difficulty, null);
    }

    private static <T> T fake(Class<T> type) {
        return mock(type, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
    }

    private static void withServer(org.junit.jupiter.api.function.Executable test) throws Exception {
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        var server = mock(Server.class);
        when(server.isPrimaryThread()).thenReturn(true);
        field.set(null, server);
        try { assertDoesNotThrow(test); }
        finally { field.set(null, previous); }
    }
}
