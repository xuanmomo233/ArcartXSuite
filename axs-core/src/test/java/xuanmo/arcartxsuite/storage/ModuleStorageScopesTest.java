package xuanmo.arcartxsuite.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class ModuleStorageScopesTest {
    @Test
    void schemaPreparationRunsOffThreadAndRetirementWaitsForIt() throws Exception {
        var scopes = new ModuleStorageScopes();
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var closed = new AtomicInteger();
        try {
            var source = mock(DataSource.class);
            when(source.getConnection()).thenReturn(mock(java.sql.Connection.class));
            var first = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(source, closed::incrementAndGet));
            first.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var caller = Thread.currentThread();
            var prepared = first.initialize(() -> {
                assertNotSame(caller, Thread.currentThread());
                started.countDown();
                assertTrue(finish.await(3, TimeUnit.SECONDS));
                try (var connection = first.dataSource().getConnection()) {
                    assertNotNull(connection);
                }
            }).toCompletableFuture();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            first.retireAfter(CompletableFuture.completedFuture(null));
            var successor = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(mock(DataSource.class), null));
            assertFalse(prepared.isDone());
            assertFalse(first.retired().toCompletableFuture().isDone());
            assertFalse(successor.ready().toCompletableFuture().isDone());
            finish.countDown();
            prepared.get(2, TimeUnit.SECONDS);
            first.retired().toCompletableFuture().get(2, TimeUnit.SECONDS);
            successor.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, closed.get());
        } finally {
            finish.countDown();
            scopes.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void successorWaitsForDatabaseExitAndPoolClosure() throws Exception {
        var scopes = new ModuleStorageScopes();
        var closeStarted = new CountDownLatch(1);
        var closeRelease = new CountDownLatch(1);
        var closed = new AtomicInteger();
        var stopped = new CompletableFuture<Void>();
        try {
            var first = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(mock(DataSource.class), () -> {
                closeStarted.countDown();
                await(closeRelease);
                closed.incrementAndGet();
            }));
            first.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            long start = System.nanoTime();
            first.retireAfter(stopped);
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            var second = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(mock(DataSource.class), null));
            assertFalse(second.ready().toCompletableFuture().isDone());
            stopped.complete(null);
            assertThrows(SQLException.class, () -> first.dataSource().getConnection());
            assertTrue(closeStarted.await(2, TimeUnit.SECONDS));
            assertFalse(second.ready().toCompletableFuture().isDone());
            var successorFromCallback = first.retired().thenRun(() -> {
                try {
                    second.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(interrupted);
                } catch (ExecutionException | java.util.concurrent.TimeoutException failure) {
                    throw new CompletionException(failure);
                }
            });
            closeRelease.countDown();
            successorFromCallback.toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(1, closed.get());
            first.retireAfter(CompletableFuture.completedFuture(null));
            assertEquals(1, closed.get());
            second.retireAfter(CompletableFuture.completedFuture(null));
            second.retired().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            stopped.complete(null);
            closeRelease.countDown();
            scopes.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void closingAWaitingInstanceDoesNotInitializeItOrCloseItsSuccessor() throws Exception {
        var scopes = new ModuleStorageScopes();
        var stopped = new CompletableFuture<Void>();
        var initializations = new AtomicInteger();
        var successorCloses = new AtomicInteger();
        try {
            var first = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(mock(DataSource.class), null));
            first.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            first.retireAfter(stopped);
            var abandoned = scopes.open("title", () -> {
                initializations.incrementAndGet();
                return new ModuleStorageScopes.OpenedSource(mock(DataSource.class), null);
            });
            abandoned.retireAfter(CompletableFuture.completedFuture(null));
            var successor = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(
                mock(DataSource.class), successorCloses::incrementAndGet));
            stopped.complete(null);
            successor.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(0, initializations.get());
            abandoned.retireAfter(CompletableFuture.completedFuture(null));
            assertEquals(0, successorCloses.get());
            successor.retireAfter(CompletableFuture.completedFuture(null));
            successor.retired().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, successorCloses.get());
        } finally {
            stopped.complete(null);
            scopes.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void aFailedOpenCanRetireAndBeReplaced() throws Exception {
        var scopes = new ModuleStorageScopes();
        try {
            var failed = scopes.open("title", () -> { throw new SQLException("test database unavailable"); });
            assertThrows(java.util.concurrent.ExecutionException.class,
                () -> failed.ready().toCompletableFuture().get(2, TimeUnit.SECONDS));
            failed.retireAfter(CompletableFuture.completedFuture(null));
            failed.retired().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var replacement = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(mock(DataSource.class), null));
            replacement.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            scopes.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void driverLinkageErrorsSettleOpenAndRetirementStages() throws Exception {
        var scopes = new ModuleStorageScopes();
        try {
            var loadFailure = new NoClassDefFoundError("test JDBC driver dependency");
            var failed = scopes.open("title", () -> { throw loadFailure; });
            assertSame(loadFailure, assertThrows(ExecutionException.class,
                () -> failed.ready().toCompletableFuture().get(2, TimeUnit.SECONDS)).getCause());
            failed.retireAfter(CompletableFuture.completedFuture(null));
            failed.retired().toCompletableFuture().get(2, TimeUnit.SECONDS);

            var closeFailure = new NoClassDefFoundError("test JDBC close dependency");
            var closing = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(
                mock(DataSource.class), () -> { throw closeFailure; }));
            closing.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            closing.retireAfter(CompletableFuture.completedFuture(null));
            assertSame(closeFailure, assertThrows(ExecutionException.class,
                () -> closing.retired().toCompletableFuture().get(2, TimeUnit.SECONDS)).getCause());
            var replacement = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(mock(DataSource.class), null));
            replacement.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            scopes.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void failedBorrowReleasesItsReservationBeforeRetirement() throws Exception {
        var scopes = new ModuleStorageScopes();
        var source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new NoClassDefFoundError("test JDBC connection dependency"));
        try {
            var first = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(source, null));
            first.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertThrows(NoClassDefFoundError.class, () -> first.dataSource().getConnection());
            first.retireAfter(CompletableFuture.completedFuture(null));
            first.retired().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            scopes.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void anAuxiliaryBorrowedConnectionAlsoDelaysRetirement() throws Exception {
        var scopes = new ModuleStorageScopes();
        var source = mock(DataSource.class);
        var connection = mock(java.sql.Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var closes = new AtomicInteger();
        try {
            var first = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(source, closes::incrementAndGet));
            first.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var borrowed = first.dataSource().getConnection();
            first.retireAfter(CompletableFuture.completedFuture(null));
            var next = scopes.open("title", () -> new ModuleStorageScopes.OpenedSource(mock(DataSource.class), null));
            assertFalse(next.ready().toCompletableFuture().isDone());
            assertEquals(0, closes.get());
            borrowed.close();
            borrowed.close();
            next.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
            verify(connection).close();
            assertEquals(1, closes.get());
        } finally {
            scopes.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void forcedClosureDuringInitializationReleasesExactlyOnce() throws Exception {
        var scopes = new ModuleStorageScopes();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closes = new AtomicInteger();
        try {
            var storage = scopes.open("title", () -> {
                entered.countDown();
                await(release);
                return new ModuleStorageScopes.OpenedSource(mock(DataSource.class), closes::incrementAndGet);
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            scopes.forceClose("title");
            storage.retireAfter(CompletableFuture.completedFuture(null));
            release.countDown();
            storage.retired().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, closes.get());
        } finally {
            release.countDown();
            scopes.shutdown().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("test latch timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
