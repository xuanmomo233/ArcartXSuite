package xuanmo.arcartxsuite.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import javax.sql.DataSource;

/** Core-owned persistent secondary-password service. */
public final class SecondaryPasswordService implements SecondaryPasswordAccess, AutoCloseable {
    public static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Repository repository;
    private final long unlockTimeoutMillis;
    private final ConcurrentMap<UUID, Long> unlockedUntil = new ConcurrentHashMap<>();
    /** 按 UUID 加锁的锁对象注册表，替代实例级 synchronized 细化并发粒度。 */
    private final ConcurrentMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    public SecondaryPasswordService(java.io.File dataFolder, long unlockTimeoutSeconds,
                                    DataSource dataSource, StorageDescriptor descriptor,
                                    Logger logger) throws SQLException {
        this.unlockTimeoutMillis = Math.max(1L, unlockTimeoutSeconds) * 1000L;
        this.repository = new Repository(dataSource, descriptor, dataFolder, logger);
        repository.initialize();
    }

    private Connection connection() throws SQLException {
        return repository.open();
    }

    /** 获取指定玩家的锁对象（按 UUID 粒度加锁，不同玩家互不阻塞）。 */
    private Object lockFor(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, k -> new Object());
    }

    @Override
    public boolean isPasswordSet(@NotNull Player player) {
        synchronized (lockFor(player.getUniqueId())) {
            try {
                return load(player.getUniqueId()).isPresent();
            } catch (SQLException exception) {
                return false;
            }
        }
    }

    @Override
    public boolean isUnlocked(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        synchronized (lockFor(uuid)) {
            try {
                if (load(uuid).isEmpty()) {
                    return true;
                }
            } catch (SQLException exception) {
                return false;
            }
            long expiry = unlockedUntil.getOrDefault(uuid, 0L);
            if (expiry > System.currentTimeMillis()) {
                return true;
            }
            unlockedUntil.remove(uuid);
            return false;
        }
    }

    @Override
    public boolean verify(@NotNull Player player, @NotNull String password) {
        // 空密码拒绝：与 set() 的空密码检查对称，防止历史空密码记录被"验证"通过
        if (password == null || password.isBlank()) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        synchronized (lockFor(uuid)) {
            try {
                Optional<StoredPassword> stored = load(uuid);
                if (stored.isEmpty()) {
                    return true;
                }
                StoredPassword value = stored.get();
                byte[] actual = derive(password, Base64.getDecoder().decode(value.salt()));
                boolean valid = MessageDigest.isEqual(
                    Base64.getDecoder().decode(value.hash()), actual);
                if (valid) {
                    unlockedUntil.put(uuid, System.currentTimeMillis() + unlockTimeoutMillis);
                }
                return valid;
            } catch (Exception exception) {
                return false;
            }
        }
    }

    @Override
    public boolean set(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword) {
        if (newPassword.isBlank()) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        synchronized (lockFor(uuid)) {
            try {
                Optional<StoredPassword> existing = load(uuid);
                if (existing.isPresent() && !verify(player, oldPassword)) {
                    return false;
                }
                byte[] salt = new byte[SALT_BYTES];
                RANDOM.nextBytes(salt);
                byte[] hash = derive(newPassword, salt);
                String upsert = repository.isMysqlDialect()
                    ? "INSERT INTO axs_secondary_password(player_uuid,salt,hash,updated_at) VALUES(?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE salt=VALUES(salt),hash=VALUES(hash),updated_at=VALUES(updated_at)"
                    : "INSERT INTO axs_secondary_password(player_uuid,salt,hash,updated_at) VALUES(?,?,?,?) "
                        + "ON CONFLICT(player_uuid) DO UPDATE SET salt=excluded.salt,hash=excluded.hash,updated_at=excluded.updated_at";
                try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(upsert)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, Base64.getEncoder().encodeToString(salt));
                    statement.setString(3, Base64.getEncoder().encodeToString(hash));
                    statement.setLong(4, System.currentTimeMillis());
                    statement.executeUpdate();
                }
                unlockedUntil.put(uuid, System.currentTimeMillis() + unlockTimeoutMillis);
                return true;
            } catch (Exception exception) {
                return false;
            }
        }
    }

    @Override
    public boolean clear(@NotNull Player player, @NotNull String password) {
        UUID uuid = player.getUniqueId();
        synchronized (lockFor(uuid)) {
            try {
                if (load(uuid).isEmpty() || !verify(player, password)) {
                    return false;
                }
                try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM axs_secondary_password WHERE player_uuid=?")) {
                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();
                }
                unlockedUntil.remove(uuid);
                return true;
            } catch (SQLException exception) {
                return false;
            }
        }
    }

    @Override
    public void lock(@NotNull Player player) {
        synchronized (lockFor(player.getUniqueId())) {
            unlockedUntil.remove(player.getUniqueId());
        }
    }

    public boolean setPassword(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword) {
        return set(player, oldPassword, newPassword);
    }

    public boolean clearPassword(@NotNull Player player, @NotNull String password) {
        return clear(player, password);
    }

    /**
     * 管理员免密清除指定玩家的二级密码。
     *
     * @param playerUuid 目标玩家 UUID
     * @return true 表示已清除（或本就未设置）；false 表示数据库异常
     */
    public boolean clearPasswordByUuid(@NotNull UUID playerUuid) {
        try {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM axs_secondary_password WHERE player_uuid=?")) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            }
            unlockedUntil.remove(playerUuid);
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    private Optional<StoredPassword> load(UUID uuid) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
            "SELECT salt,hash FROM axs_secondary_password WHERE player_uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                    ? Optional.of(new StoredPassword(result.getString("salt"), result.getString("hash")))
                    : Optional.empty();
            }
        }
    }

    private static byte[] derive(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
            password.getBytes(StandardCharsets.UTF_8).length == 0 ? new char[0] : password.toCharArray(),
            salt, PBKDF2_ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    @Override
    public void close() {
        unlockedUntil.clear();
        repository.shutdown();
    }

    private record StoredPassword(String salt, String hash) {}

    private static final class Repository extends AbstractModuleRepository {

        private Repository(DataSource dataSource, StorageDescriptor descriptor,
                           java.io.File dataFolder, Logger logger) {
            super("AXS-SecondaryPassword", dataSource, descriptor, dataFolder, logger);
        }

        private Connection open() throws SQLException {
            return getConnection();
        }

        private boolean isMysqlDialect() {
            return isMysql();
        }

        @Override
        protected void onInitialize(Connection connection) throws SQLException {
            String ddl = isMysql()
                ? "CREATE TABLE IF NOT EXISTS axs_secondary_password ("
                    + "player_uuid VARCHAR(36) PRIMARY KEY, salt TEXT NOT NULL, hash TEXT NOT NULL, updated_at BIGINT NOT NULL)"
                : "CREATE TABLE IF NOT EXISTS axs_secondary_password ("
                    + "player_uuid TEXT PRIMARY KEY, salt TEXT NOT NULL, hash TEXT NOT NULL, updated_at INTEGER NOT NULL)";
            try (PreparedStatement statement = connection.prepareStatement(ddl)) {
                statement.executeUpdate();
            }
        }

        @Override
        protected List<String> playerDataTables() {
            return List.of("axs_secondary_password");
        }
    }
}
