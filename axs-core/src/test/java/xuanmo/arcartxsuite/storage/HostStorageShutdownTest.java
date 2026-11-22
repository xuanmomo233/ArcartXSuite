package xuanmo.arcartxsuite.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class HostStorageShutdownTest {
    @Test
    void aLateInitializationCloseFailureIsReportedAndOtherPoolsStillClose() throws Exception {
        var scopes = new ModuleStorageScopes();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var shared = new AtomicInteger();
        scopes.open("late-close-error", () -> {
            entered.countDown();
            try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new SQLException(error); }
            return new ModuleStorageScopes.OpenedSource(mock(DataSource.class), () -> { throw new IllegalStateException("pool close failed"); });
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var stopped = scopes.shutdown(shared::incrementAndGet).toCompletableFuture();
        release.countDown();
        assertThrows(java.util.concurrent.ExecutionException.class, () -> stopped.get(3, TimeUnit.SECONDS));
        assertEquals(1, shared.get());
    }

    @Test
    void shutdownWaitsForInitializationAndClosesOffThread() throws Exception {
        var scopes = new ModuleStorageScopes();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closed = new AtomicInteger();
        var shared = new AtomicInteger();
        var scope = scopes.open("initializing", () -> {
            entered.countDown();
            try { assertTrue(release.await(3, TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new SQLException(error); }
            return new ModuleStorageScopes.OpenedSource(mock(DataSource.class), () -> {
                assertEquals("AXS-Storage-Lifecycle", Thread.currentThread().getName());
                closed.incrementAndGet();
            });
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var stopped = scopes.shutdown(shared::incrementAndGet).toCompletableFuture();
        try {
            assertFalse(stopped.isDone());
            assertEquals(0, shared.get());
            assertThrows(SQLException.class, () -> scope.dataSource().getConnection());
        } finally { release.countDown(); }
        stopped.get(3, TimeUnit.SECONDS);
        assertEquals(1, closed.get());
        assertEquals(1, shared.get());
    }

    @Test
    void aBorrowedConnectionIsReturnedBeforeItsPoolCloses() throws Exception {
        var scopes = new ModuleStorageScopes();
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var closed = new AtomicInteger();
        var scope = scopes.open("borrowed", () -> new ModuleStorageScopes.OpenedSource(source, closed::incrementAndGet));
        scope.ready().toCompletableFuture().get(2, TimeUnit.SECONDS);
        var borrowed = scope.dataSource().getConnection();
        var stopped = scopes.shutdown().toCompletableFuture();
        assertFalse(stopped.isDone());
        assertEquals(0, closed.get());
        borrowed.close();
        stopped.get(2, TimeUnit.SECONDS);
        assertEquals(1, closed.get());
        verify(connection, times(1)).close();
    }
}
