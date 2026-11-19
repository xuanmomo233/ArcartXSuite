package xuanmo.arcartxsuite.questgps.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QuestGpsQuestChangesTest {
    @Test
    void refusesASecondChangeForTheSamePlayerUntilTheBackendFinishes() {
        var changes = new QuestGpsQuestChanges();
        var player = UUID.randomUUID();
        var backend = new CompletableFuture<Boolean>();
        var completed = new AtomicReference<Boolean>();
        assertTrue(changes.submit(player, () -> backend, (result, failure) -> completed.set(result)));
        assertTrue(changes.pending(player));
        assertFalse(changes.submit(player, () -> fail("duplicate backend operation"), (result, failure) -> {}));
        assertTrue(changes.submit(UUID.randomUUID(), () -> CompletableFuture.completedFuture(true), (result, failure) -> {}));

        backend.complete(true);
        assertEquals(Boolean.TRUE, completed.get());
        assertFalse(changes.pending(player));
        assertTrue(changes.submit(player, () -> CompletableFuture.completedFuture(false), (result, failure) -> {}));
    }

    @Test
    void synchronousAndAsynchronousFailuresBothReleaseThePlayer() {
        var changes = new QuestGpsQuestChanges();
        var player = UUID.randomUUID();
        var reported = new AtomicReference<Throwable>();
        var error = new IllegalStateException("provider failure");
        assertTrue(changes.submit(player, () -> { throw error; }, (result, failure) -> reported.set(failure)));
        assertSame(error, reported.get());
        assertFalse(changes.pending(player));

        var backend = new CompletableFuture<Boolean>();
        assertTrue(changes.submit(player, () -> backend, (result, failure) -> reported.set(failure)));
        var later = new IllegalStateException("backend failure");
        backend.completeExceptionally(later);
        assertSame(later, reported.get());
        assertFalse(changes.pending(player));
    }
}
