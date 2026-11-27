package xuanmo.arcartxsuite.scheduler;

import java.util.logging.Logger;
import javax.sql.DataSource;
import xuanmo.arcartxsuite.crossserver.CrossServerService;

/** The only server is always responsible for its scheduled tasks. */
final class SchedulerLeaderElection {
    SchedulerLeaderElection(Logger logger, String nodeId, CrossServerService crossServerService,
                            DataSource dataSource, String leaderTable) {}

    boolean requiresHeartbeat() { return false; }
    boolean isRedisAvailable() { return false; }
    boolean hasDatabase() { return false; }
    boolean isLeader(String taskKey) { return true; }
    void refreshHeartbeat(String taskKey) {}
}
