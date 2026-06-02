package xuanmo.arcartxsuite.eventpacket.storage;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public interface EventPacketRepository {

    void initialize() throws SQLException;

    int getKillCount(UUID playerUuid, String ruleId) throws SQLException;

    void setKillCount(UUID playerUuid, String ruleId, int count) throws SQLException;

    void incrementKillCount(UUID playerUuid, String ruleId) throws SQLException;

    Map<String, Integer> loadAllKillCounts(UUID playerUuid) throws SQLException;

    boolean hasFired(UUID playerUuid, String ruleId) throws SQLException;

    void markFired(UUID playerUuid, String ruleId) throws SQLException;

    void removeFired(UUID playerUuid, String ruleId) throws SQLException;

    void close();
}
