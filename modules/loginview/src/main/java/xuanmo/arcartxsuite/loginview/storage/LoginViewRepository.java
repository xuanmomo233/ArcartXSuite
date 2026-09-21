package xuanmo.arcartxsuite.loginview.storage;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * LoginView 仓储接口，定义账号与登录会话的持久化操作。
 * <p>
 * JDBC 实现见 {@link JdbcLoginViewRepository}。
 */
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

    /**
     * 读取玩家的密码失败计数（持久化）。
     *
     * @param maxAgeMillis 计数有效窗口；超过窗口的历史计数视为 0（防止长期累积误伤）
     * @return 有效失败次数
     */
    int loadFailureAttempts(UUID uuid, long maxAgeMillis) throws SQLException;

    /** 保存玩家的密码失败计数（upsert）。 */
    void saveFailureAttempts(UUID uuid, int attempts) throws SQLException;

    /** 清除玩家的密码失败计数（登录成功/重置密码后调用）。 */
    void deleteFailureAttempts(UUID uuid) throws SQLException;

    @Override
    void close();
}
