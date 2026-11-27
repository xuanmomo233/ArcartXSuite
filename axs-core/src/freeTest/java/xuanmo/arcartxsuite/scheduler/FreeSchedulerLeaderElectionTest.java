package xuanmo.arcartxsuite.scheduler;

import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FreeSchedulerLeaderElectionTest {
    @Test void configuredMysqlDoesNotParticipateInDistributedElection() {
        var dataSource = mock(DataSource.class);
        var election = new SchedulerLeaderElection(Logger.getAnonymousLogger(), "solo", null, dataSource, "test_leader");
        assertTrue(election.isLeader("calendar"));
        assertFalse(election.requiresHeartbeat());
        election.refreshHeartbeat("calendar");
        verifyNoInteractions(dataSource);
    }
}
