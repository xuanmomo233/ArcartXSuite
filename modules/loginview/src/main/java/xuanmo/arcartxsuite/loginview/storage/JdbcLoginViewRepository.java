package xuanmo.arcartxsuite.loginview.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.loginview.config.LoginViewModuleConfiguration.StorageConfiguration;
import xuanmo.arcartxsuite.loginview.config.LoginViewModuleConfiguration.StorageDialect;

public final class JdbcLoginViewRepository extends AbstractModuleRepository implements LoginViewRepository {

    private final StorageConfiguration configuration;
    private final String accountsTable;
    private final String sessionsTable;
    private final boolean sqlite;

    public JdbcLoginViewRepository(File dataFolder, StorageConfiguration configuration, Logger logger) {
        super("AXS-LoginView", dataFolder, configuration.toDescriptor(), logger);
        this.configuration = configuration;
        this.sqlite = configuration.dialect() == StorageDialect.SQLITE;
        this.accountsTable = configuration.tablePrefix() + "accounts";
        this.sessionsTable = configuration.tablePrefix() + "sessions";
    }

    @Override
    protected void onInitialize(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + accountsTable + " ("
                    + "lower_name VARCHAR(64) PRIMARY KEY,"
                    + "real_name VARCHAR(64) NOT NULL,"
                    + "password_hash TEXT NOT NULL,"
                    + "hash_algorithm VARCHAR(48) NOT NULL,"
                    + "email VARCHAR(128) NOT NULL DEFAULT '',"
                    + "registration_ip VARCHAR(64) NOT NULL DEFAULT '',"
                    + "last_ip VARCHAR(64) NOT NULL DEFAULT '',"
                    + "registered_at BIGINT NOT NULL DEFAULT 0,"
                    + "last_login_at BIGINT NOT NULL DEFAULT 0,"
                    + "migrated " + integerType() + " NOT NULL DEFAULT 0,"
                    + "updated_at BIGINT NOT NULL DEFAULT 0"
                    + ")"
            );
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS " + sessionsTable + " ("
                    + "uuid VARCHAR(36) PRIMARY KEY,"
                    + "player_name VARCHAR(64) NOT NULL,"
                    + "ip VARCHAR(64) NOT NULL DEFAULT '',"
                    + "created_at BIGINT NOT NULL DEFAULT 0,"
                    + "expires_at BIGINT NOT NULL DEFAULT 0"
                    + ")"
            );
        }
        logger.fine("LoginView 账户与 session 存储已初始化: " + configuration.dialect().configKey()
            + " | accounts=" + accountsTable + " | sessions=" + sessionsTable);
    }

    @Override
    protected List<String> playerDataTables() {
        // LoginView 使用 lower_name 主键，不支持按 player_uuid 删除
        return List.of();
    }

    @Override
    protected List<String> allTables() {
        return List.of(accountsTable, sessionsTable);
    }

    @Override
    public Optional<LoginViewAccount> find(String playerName) throws SQLException {
        String sql = "SELECT lower_name, real_name, password_hash, hash_algorithm, email, registration_ip, last_ip,"
            + " registered_at, last_login_at, migrated FROM " + accountsTable + " WHERE lower_name = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(playerName));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readAccount(resultSet));
            }
        }
    }

    @Override
    public boolean exists(String playerName) throws SQLException {
        String sql = "SELECT 1 FROM " + accountsTable + " WHERE lower_name = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(playerName));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public void create(String playerName, String passwordHash, String hashAlgorithm, Player player) throws SQLException {
        long now = Instant.now().toEpochMilli();
        String address = playerAddress(player);
        String sql = "INSERT INTO " + accountsTable
            + " (lower_name, real_name, password_hash, hash_algorithm, registration_ip, last_ip, registered_at, last_login_at, migrated, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(playerName));
            statement.setString(2, playerName);
            statement.setString(3, passwordHash);
            statement.setString(4, hashAlgorithm);
            statement.setString(5, address);
            statement.setString(6, address);
            statement.setLong(7, now);
            statement.setLong(8, now);
            statement.setLong(9, now);
            statement.executeUpdate();
        }
    }

    @Override
    public void updatePassword(String playerName, String passwordHash, String hashAlgorithm) throws SQLException {
        String sql = "UPDATE " + accountsTable
            + " SET password_hash = ?, hash_algorithm = ?, migrated = 0, updated_at = ? WHERE lower_name = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setString(2, hashAlgorithm);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.setString(4, normalize(playerName));
            statement.executeUpdate();
        }
    }

    @Override
    public void updateLogin(String playerName, Player player) throws SQLException {
        String sql = "UPDATE " + accountsTable + " SET last_ip = ?, last_login_at = ?, updated_at = ? WHERE lower_name = ?";
        long now = Instant.now().toEpochMilli();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerAddress(player));
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.setString(4, normalize(playerName));
            statement.executeUpdate();
        }
    }

    @Override
    public void importAccount(MigratedAuthMeAccount account) throws SQLException {
        String sql = sqlite
            ? "INSERT INTO " + accountsTable
                + " (lower_name, real_name, password_hash, hash_algorithm, email, registration_ip, last_ip, registered_at, last_login_at, migrated, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)"
                + " ON CONFLICT(lower_name) DO UPDATE SET real_name = excluded.real_name, password_hash = excluded.password_hash,"
                + " hash_algorithm = excluded.hash_algorithm, email = excluded.email, registration_ip = excluded.registration_ip,"
                + " last_ip = excluded.last_ip, registered_at = excluded.registered_at, last_login_at = excluded.last_login_at,"
                + " migrated = 1, updated_at = excluded.updated_at"
            : "INSERT INTO " + accountsTable
                + " (lower_name, real_name, password_hash, hash_algorithm, email, registration_ip, last_ip, registered_at, last_login_at, migrated, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)"
                + " ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash),"
                + " hash_algorithm = VALUES(hash_algorithm), email = VALUES(email), registration_ip = VALUES(registration_ip),"
                + " last_ip = VALUES(last_ip), registered_at = VALUES(registered_at), last_login_at = VALUES(last_login_at),"
                + " migrated = 1, updated_at = VALUES(updated_at)";
        long now = Instant.now().toEpochMilli();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(account.playerName()));
            statement.setString(2, blank(account.realName()) ? account.playerName() : account.realName());
            statement.setString(3, account.passwordHash());
            statement.setString(4, account.hashAlgorithm());
            statement.setString(5, value(account.email()));
            statement.setString(6, value(account.registrationIp()));
            statement.setString(7, value(account.lastIp()));
            statement.setLong(8, account.registeredAt());
            statement.setLong(9, account.lastLoginAt());
            statement.setLong(10, now);
            statement.executeUpdate();
        }
    }

    @Override
    public int countAccounts() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + accountsTable)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private LoginViewAccount readAccount(ResultSet resultSet) throws SQLException {
        return new LoginViewAccount(
            resultSet.getString("lower_name"),
            resultSet.getString("real_name"),
            resultSet.getString("password_hash"),
            resultSet.getString("hash_algorithm"),
            resultSet.getString("email"),
            resultSet.getString("registration_ip"),
            resultSet.getString("last_ip"),
            resultSet.getLong("registered_at"),
            resultSet.getLong("last_login_at"),
            resultSet.getInt("migrated") != 0
        );
    }

    private String integerType() {
        return sqlite ? "INTEGER" : "TINYINT";
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String playerAddress(Player player) {
        if (player == null || player.getAddress() == null || player.getAddress().getAddress() == null) {
            return "";
        }
        return player.getAddress().getAddress().getHostAddress();
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    // ── Session methods ─────────────────────────────────────

    @Override
    public void createOrUpdateSession(UUID uuid, String playerName, String ip, long expiresAt) throws SQLException {
        long now = Instant.now().toEpochMilli();
        String sql = sqlite
            ? "INSERT INTO " + sessionsTable
                + " (uuid, player_name, ip, created_at, expires_at) VALUES (?, ?, ?, ?, ?)"
                + " ON CONFLICT(uuid) DO UPDATE SET player_name = excluded.player_name, ip = excluded.ip,"
                + " created_at = excluded.created_at, expires_at = excluded.expires_at"
            : "INSERT INTO " + sessionsTable
                + " (uuid, player_name, ip, created_at, expires_at) VALUES (?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), ip = VALUES(ip),"
                + " created_at = VALUES(created_at), expires_at = VALUES(expires_at)";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, playerName);
            statement.setString(3, ip);
            statement.setLong(4, now);
            statement.setLong(5, expiresAt);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<LoginViewSession> findSession(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, player_name, ip, created_at, expires_at FROM " + sessionsTable + " WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LoginViewSession(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("player_name"),
                    rs.getString("ip"),
                    rs.getLong("created_at"),
                    rs.getLong("expires_at")
                ));
            }
        }
    }

    @Override
    public void deleteSession(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + sessionsTable + " WHERE uuid = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteExpiredSessions() throws SQLException {
        long now = Instant.now().toEpochMilli();
        String sql = "DELETE FROM " + sessionsTable + " WHERE expires_at <= ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            int deleted = statement.executeUpdate();
            if (deleted > 0) {
                logger.fine("LoginView 已清理 " + deleted + " 条过期 session");
            }
        }
    }
}
