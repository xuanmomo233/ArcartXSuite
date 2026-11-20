package xuanmo.arcartxsuite.bridge;

import java.nio.file.Path;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingSchemaInitializationTest {
    @TempDir Path directory;

    @Test void successfulSchemaIsCheckedOnceAcrossRepeatedGlobalInitialization() throws Exception {
        var storage = mock(StorageManager.class); var opened = new AtomicInteger();
        when(storage.getHostConnection()).thenAnswer(call -> { opened.incrementAndGet(); return DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("pending.db")); });
        var service = new DefaultPendingRewardService(null, Logger.getAnonymousLogger(), storage, null);
        for (int i = 0; i < 30; i++) service.initialize();
        assertEquals(1, opened.get());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("pending.db"));
             var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT claimed_at,claimed_by FROM axs_pending_rewards WHERE 1=0")) {
            assertFalse(rows.next());
        }
    }

    @Test void incompleteSchemaDoesNotPoisonTheSuccessCacheAndCanRetry() throws Exception {
        var storage = mock(StorageManager.class); var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(storage.getHostConnection()).thenReturn(connection)
            .thenAnswer(call -> DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("retry.db")));
        when(connection.createStatement()).thenReturn(statement);
        String probe = "SELECT claimed_at, claimed_by FROM axs_pending_rewards WHERE 1=0";
        when(statement.executeQuery(probe)).thenThrow(new SQLException("column upgrade incomplete"));
        var quiet = Logger.getAnonymousLogger(); quiet.setUseParentHandlers(false);
        var service = new DefaultPendingRewardService(null, quiet, storage, null);
        service.initialize();
        assertFalse(service.storageReady());
        service.initialize();
        assertTrue(service.storageReady());
        service.initialize();
        verify(storage, times(2)).getHostConnection();
        verify(statement).executeQuery(probe);
    }
}
