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
