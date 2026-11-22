package xuanmo.arcartxsuite.chat.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

class ChatGuardShutdownTest {
    @Test
    void wrappedPoolPreservesIgnoreGuardsAndClosesWithoutTakingTheCreationLockOnTheCaller() throws Exception {
        var source = mock(DataSource.class);
        var shared = mock(HikariDataSource.class);
        when(source.unwrap(HikariDataSource.class)).thenReturn(shared);
        var descriptor = mock(StorageDescriptor.class);
        when(descriptor.isMysql()).thenReturn(true);
        var repository = new JdbcChatRepository(source, descriptor, null, Logger.getAnonymousLogger());
        assertTrue(repository.supportsIgnoreGuards());
        var field = JdbcChatRepository.class.getDeclaredField("guardPoolCreation");
        field.setAccessible(true);
        Object creation = field.get(repository);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var creating = CompletableFuture.runAsync(() -> {
            synchronized (creation) {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime();
            repository.close();
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(repository.closed().toCompletableFuture().isDone());
        } finally { release.countDown(); }
        creating.get(2, TimeUnit.SECONDS);
        repository.closed().toCompletableFuture().get(2, TimeUnit.SECONDS);
        repository.close();
        verify(shared, never()).close();
    }
}
