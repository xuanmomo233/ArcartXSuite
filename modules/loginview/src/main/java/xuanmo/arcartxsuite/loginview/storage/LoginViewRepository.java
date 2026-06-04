package xuanmo.arcartxsuite.loginview.storage;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

public interface LoginViewRepository extends AutoCloseable {

    void initialize() throws SQLException;

    Optional<LoginViewAccount> find(String playerName) throws SQLException;

    boolean exists(String playerName) throws SQLException;

    void create(String playerName, String passwordHash, String hashAlgorithm, Player player) throws SQLException;

    void updatePassword(String playerName, String passwordHash, String hashAlgorithm) throws SQLException;

    void updateLogin(String playerName, Player player) throws SQLException;

    void importAccount(MigratedAuthMeAccount account) throws SQLException;

    int countAccounts() throws SQLException;

    void createOrUpdateSession(UUID uuid, String playerName, String ip, long expiresAt) throws SQLException;

    Optional<LoginViewSession> findSession(UUID uuid) throws SQLException;

    void deleteSession(UUID uuid) throws SQLException;

    void deleteExpiredSessions() throws SQLException;

    @Override
    void close();
}
