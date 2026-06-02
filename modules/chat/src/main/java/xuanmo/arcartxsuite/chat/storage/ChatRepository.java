package xuanmo.arcartxsuite.chat.storage;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatPlayerProfile;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;

public interface ChatRepository {

    void initialize() throws SQLException;

    ChatPlayerState loadState(UUID playerUuid, String defaultChannelId) throws SQLException;

    void saveState(ChatPlayerState state) throws SQLException;

    void saveIgnoredPlayers(UUID playerUuid, Set<UUID> ignoredPlayers) throws SQLException;

    Optional<ChatMuteRecord> loadMute(UUID playerUuid) throws SQLException;

    void saveMute(ChatMuteRecord muteRecord) throws SQLException;

    void deleteMute(UUID playerUuid) throws SQLException;

    void upsertProfile(ChatPlayerProfile profile) throws SQLException;

    Optional<ChatPlayerProfile> loadProfile(UUID playerUuid) throws SQLException;

    Optional<ChatPlayerProfile> findProfileByName(String playerName) throws SQLException;

    void close();
}
