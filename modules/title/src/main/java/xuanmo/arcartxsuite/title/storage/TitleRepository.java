package xuanmo.arcartxsuite.title.storage;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import xuanmo.arcartxsuite.title.model.PlayerOwnedTitle;
import xuanmo.arcartxsuite.title.model.PlayerTitleState;

public interface TitleRepository {

    void initialize() throws SQLException;

    PlayerTitleState loadState(UUID playerUuid) throws SQLException;

    void saveEquippedTitle(UUID playerUuid, String groupId, String titleId, Instant updatedAt) throws SQLException;

    void deleteEquippedGroup(UUID playerUuid, String groupId) throws SQLException;

    void deleteEquippedTitle(UUID playerUuid, String titleId) throws SQLException;

    void deleteAllEquippedGroups(UUID playerUuid) throws SQLException;

    void saveOwnedTitle(UUID playerUuid, PlayerOwnedTitle ownedTitle) throws SQLException;

    void deleteOwnedTitle(UUID playerUuid, String titleId) throws SQLException;

    void saveDisplayTitle(UUID playerUuid, String titleId, Instant updatedAt) throws SQLException;

    void deleteDisplayTitle(UUID playerUuid) throws SQLException;

    int deleteExpiredTitles(Instant now) throws SQLException;

    void close();
}
