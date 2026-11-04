package xuanmo.arcartxsuite.abyss.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Phase;
import xuanmo.arcartxsuite.api.capability.AbyssEntryCostAccess.Participant;
import xuanmo.arcartxsuite.api.capability.AbyssEntryCostAccess.State;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntryCostsTest {
    @Test void cancellationDuringReserveReleasesWithoutCommitting() throws Exception {
        exercise((costs, provider, operation, changed) -> {
            var reserve = new CompletableFuture<State>();
            when(provider.reserve(any())).thenReturn(reserve);
            when(provider.release(any())).thenReturn(CompletableFuture.completedFuture(State.RELEASED));
            costs.begin(operation, changed, () -> fail("Cancelled entry must not begin"));
            assertEquals(Phase.RESERVING_COST, operation.phase);
            operation.cancelRequested = true;
            reserve.complete(State.RESERVED);
            verify(provider).release(argThat(transaction -> transaction.id().equals(operation.costId)));
            verify(provider, never()).commit(any());
            assertEquals(State.RELEASED, operation.costState);
            assertEquals(Phase.CANCELLED, operation.phase);
        });
    }

    @Test void uncertainCommitDoesNotGuessWhetherToReleaseOrCompensate() throws Exception {
        exercise((costs, provider, operation, changed) -> {
            when(provider.reserve(any())).thenReturn(CompletableFuture.completedFuture(State.RESERVED));
            when(provider.commit(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("lost reply")));
            costs.begin(operation, changed, () -> fail("Uncertain entry must not begin"));
            assertEquals(Phase.REVIEW_REQUIRED, operation.phase);
            assertEquals(State.UNKNOWN, operation.costState);
            verify(provider, never()).release(any());
            verify(provider, never()).compensate(any());
        });
    }

    @Test void aRejectedAdmissionCompensatesConfirmedConsumptionOnlyOnce() throws Exception {
        exercise((costs, provider, operation, changed) -> {
            when(provider.reserve(any())).thenReturn(CompletableFuture.completedFuture(State.RESERVED));
            when(provider.commit(any())).thenReturn(CompletableFuture.completedFuture(State.COMMITTED));
            when(provider.compensate(any())).thenReturn(CompletableFuture.completedFuture(State.COMPENSATED));
            costs.begin(operation, changed, () -> {
                assertTrue(costs.authorized(operation));
                operation.phase = Phase.REJECTED;
                changed.accept(operation);
            });
            changed.accept(operation);
            verify(provider, times(1)).compensate(argThat(transaction -> transaction.id().equals(operation.costId)));
            verify(provider, never()).release(any());
            assertEquals(State.COMPENSATED, operation.costState);
        });
    }

    @Test void confirmedConsumptionIsNotCompensatedWhenItIsUnknownWhetherPlayStarted() throws Exception {
        exercise((costs, provider, operation, changed) -> {
            when(provider.reserve(any())).thenReturn(CompletableFuture.completedFuture(State.RESERVED));
            when(provider.commit(any())).thenReturn(CompletableFuture.completedFuture(State.COMMITTED));
            costs.begin(operation, changed, () -> {
                operation.phase = Phase.REVIEW_REQUIRED;
                operation.entryOutcomeUncertain = true;
                changed.accept(operation);
            });
            assertEquals(Phase.REVIEW_REQUIRED, operation.phase);
            assertEquals(State.COMMITTED, operation.costState);
            verify(provider, never()).compensate(any());
        });
    }

    @FunctionalInterface interface Scenario {
        void run(EntryCosts costs, Participant provider, AdmissionAccess.Operation operation,
                 Consumer<AdmissionAccess.Operation> changed);
    }

    private static void exercise(Scenario scenario) throws Exception {
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        var server = mock(Server.class);
        when(server.isPrimaryThread()).thenReturn(true);
        field.set(null, server);
        try {
            var service = mock(AbyssService.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
            when(service.running()).thenReturn(true);
            doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(service).runOnMain(any());
            var costs = new EntryCosts(service);
            var provider = mock(Participant.class);
            UUID player = UUID.randomUUID();
            var op = new AdmissionAccess.Operation(new Intent("xc", UUID.randomUUID(), player, List.of(player),
                "test", null, null, null));
            op.costOwner = "keys";
            op.costId = UUID.randomUUID();
            op.costState = State.NONE;
            try (var registration = costs.register("keys", provider)) {
                scenario.run(costs, provider, op, changed -> costs.compensate(changed, ignored -> { }));
            }
        } finally {
            field.set(null, previous);
        }
    }
}
