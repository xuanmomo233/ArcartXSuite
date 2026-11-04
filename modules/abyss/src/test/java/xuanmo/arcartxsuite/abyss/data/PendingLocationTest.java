package xuanmo.arcartxsuite.abyss.data;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingLocationTest {
    @TempDir Path dir;
    @Test void finalResultsAreImmutableAndHistoryPagingIsScopedByOwner() throws Exception {
        var ds = mock(DataSource.class);
        when(ds.getConnection()).thenAnswer(call -> DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("results.db")));
        var descriptor = StorageDescriptor.sqlite("results.db");
        var repository = new JdbcAbyssRepository(ds, descriptor, dir.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertTrue(repository.insertRunResult(a, "xc", 100, "original"));
        assertFalse(repository.insertRunResult(a, "xc", 200, "conflict"));
        assertTrue(repository.insertRunResult(b, "xc", 100, "second"));
        assertTrue(repository.insertRunResult(UUID.randomUUID(), "other", 101, "hidden"));
        var reopened = new JdbcAbyssRepository(ds, descriptor, dir.toFile(), Logger.getAnonymousLogger());
        reopened.initialize();
        assertEquals("original", reopened.loadRunResult(a));
        assertEquals(java.util.List.of("original"), reopened.loadRunResults("xc", 0, "", 1));
        assertEquals(java.util.List.of("second"), reopened.loadRunResults("xc", 100, a.toString(), 100));
    }
    @Test void admissionReservationIsUniqueAndReadableFromANewRepository() throws Exception {
        var ds = mock(DataSource.class);
        when(ds.getConnection()).thenAnswer(call -> DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("entry.db")));
        var descriptor = StorageDescriptor.sqlite("entry.db");
        var repository = new JdbcAbyssRepository(ds, descriptor, dir.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var receipt = new JdbcAbyssRepository.AdmissionReceipt("xc", UUID.randomUUID().toString(), "abc", "phase: PREPARING");
        repository.insertAdmission(receipt);
        assertThrows(java.sql.SQLException.class, () -> repository.insertAdmission(receipt));
        var reopened = new JdbcAbyssRepository(ds, descriptor, dir.toFile(), Logger.getAnonymousLogger());
        reopened.initialize();
        assertEquals(java.util.List.of(receipt), reopened.loadAdmissions());
        var completed = new JdbcAbyssRepository.AdmissionReceipt(receipt.owner(), receipt.requestId(), "abc", "phase: STARTED");
        reopened.updateAdmission(completed);
        assertEquals(java.util.List.of(completed), repository.loadAdmissions());
    }
    @Test void repeatedReadsPreserveRecoveryAndStaleConfirmationCannotDeleteNewerPosition()throws Exception {
        var ds=mock(DataSource.class);when(ds.getConnection()).thenAnswer(call->DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("recovery.db")));
        var repository=new JdbcAbyssRepository(ds,StorageDescriptor.sqlite("recovery.db"),dir.toFile(),Logger.getAnonymousLogger());repository.initialize();
        UUID player=UUID.randomUUID();repository.savePendingLocation(player,"home",1,64,1,0,0);
        var first=repository.loadPendingLocation(player);assertEquals(first,repository.loadPendingLocation(player));
        repository.savePendingLocation(player,"home",2,65,2,90,0);
        assertFalse(repository.confirmPendingLocation(player,first));var second=repository.loadPendingLocation(player);assertNotNull(second);assertEquals(2,second.x());
        assertTrue(repository.confirmPendingLocation(player,second));assertNull(repository.loadPendingLocation(player));
    }
}
