package xuanmo.arcartxsuite.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;

/** Core-owned persistent secondary-password service. */
public final class SecondaryPasswordService implements SecondaryPasswordAccess, AutoCloseable {
    public static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String jdbcUrl;
    private final long unlockTimeoutMillis;
    private final ConcurrentMap<UUID, Long> unlockedUntil = new ConcurrentHashMap<>();

    public SecondaryPasswordService(java.io.File dataFolder, long unlockTimeoutSeconds) throws SQLException {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new SQLException("Unable to create core data folder: " + dataFolder);
        }
        this.jdbcUrl = "jdbc:sqlite:" + new java.io.File(dataFolder, "secondary-password.db").getAbsolutePath();
        this.unlockTimeoutMillis = Math.max(1L, unlockTimeoutSeconds) * 1000L;
        initializeSchema();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initializeSchema() throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
            "CREATE TABLE IF NOT EXISTS axs_secondary_password ("
                + "player_uuid TEXT PRIMARY KEY, salt TEXT NOT NULL, hash TEXT NOT NULL, updated_at INTEGER NOT NULL)")) {
            statement.executeUpdate();
        }
    }

    @Override
    public synchronized boolean isPasswordSet(@NotNull Player player) {
        try {
            return load(player.getUniqueId()).isPresent();
        } catch (SQLException exception) {
            return false;
        }
    }

    @Override
    public synchronized boolean isUnlocked(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
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

    @Override
    public synchronized boolean verify(@NotNull Player player, @NotNull String password) {
        UUID uuid = player.getUniqueId();
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

    @Override
    public synchronized boolean set(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword) {
        if (newPassword.isBlank()) {
            return false;
        }
        try {
            UUID uuid = player.getUniqueId();
            Optional<StoredPassword> existing = load(uuid);
            if (existing.isPresent() && !verify(player, oldPassword)) {
                return false;
            }
            byte[] salt = new byte[SALT_BYTES];
            RANDOM.nextBytes(salt);
            byte[] hash = derive(newPassword, salt);
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO axs_secondary_password(player_uuid,salt,hash,updated_at) VALUES(?,?,?,?) "
                    + "ON CONFLICT(player_uuid) DO UPDATE SET salt=excluded.salt,hash=excluded.hash,updated_at=excluded.updated_at")) {
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

    @Override
    public synchronized boolean clear(@NotNull Player player, @NotNull String password) {
        try {
            UUID uuid = player.getUniqueId();
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

    public boolean setPassword(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword) {
        return set(player, oldPassword, newPassword);
    }

    public boolean clearPassword(@NotNull Player player, @NotNull String password) {
        return clear(player, password);
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
    }

    private record StoredPassword(String salt, String hash) {}
}
