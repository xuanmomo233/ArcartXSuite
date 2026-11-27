package xuanmo.arcartxsuite.scheduler;

import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.crossserver.CrossServerService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerSchedulerLeaderElectionTest {
    @Test void missingSeatPausesLeaderOnlyWorkEvenWithSharedMysqlConfigured() {
        var transport = mock(CrossServerService.class);
        var database = mock(DataSource.class);
        var election = new SchedulerLeaderElection(Logger.getAnonymousLogger(), "node", transport, database, "test_leader");
        assertFalse(election.isLeader("calendar"));
        election.refreshHeartbeat("calendar");
        verifyNoInteractions(database);
        verify(transport, never()).getJedisPoolInternal();
    }
}
