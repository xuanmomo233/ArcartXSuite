package xuanmo.arcartxsuite.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.storage.AbstractModuleRepository;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import javax.sql.DataSource;

/** Core-owned persistent secondary-password service. */
public final class SecondaryPasswordService implements SecondaryPasswordAccess, AutoCloseable {
    public static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final long VERIFY_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Repository repository;
    private final long unlockTimeoutMillis;
    private final int minLength;
    private final int maxLength;
    private final ConcurrentMap<UUID, PasswordState> states = new ConcurrentHashMap<>();
    private final Set<UUID> verifications = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final xuanmo.arcartxsuite.api.scheduler.ModuleExecutor verifier = new xuanmo.arcartxsuite.api.scheduler.ModuleExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(32), task -> {
            Thread thread = new Thread(task, "AXS-SecondaryPassword");
            thread.setDaemon(true);
            return thread;
        });
    private volatile boolean closed;

    public SecondaryPasswordService(java.io.File dataFolder, long unlockTimeoutSeconds,
                                    int minLength, int maxLength,
                                    DataSource dataSource, StorageDescriptor descriptor,
                                    Logger logger) throws SQLException {
        this.unlockTimeoutMillis = Math.max(1L, unlockTimeoutSeconds) * 1000L;
        this.minLength = Math.max(1, minLength);
        this.maxLength = Math.max(this.minLength, maxLength);
        this.repository = new Repository(dataSource, descriptor, dataFolder, logger);
        repository.initialize();
    }

    @Override
    public int minLength() {
        return minLength;
    }

    @Override
    public int maxLength() {
        return maxLength;
    }

    private Connection connection() throws SQLException {
        return repository.open();
    }

    private PasswordState state(UUID uuid) {
        return states.computeIfAbsent(uuid, key -> new PasswordState());
    }

    @Override
    public boolean isPasswordSet(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        PasswordState state = state(uuid);
        if (closed || !state.operation.tryLock()) return true;
        try {
            return load(uuid).isPresent();
        } catch (SQLException exception) {
            return true;
        } finally {
            state.operation.unlock();
        }
    }

    @Override
    public boolean isUnlocked(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        return readStatus(uuid, AuthAccess.snapshot(uuid)).unlocked();
    }

    @Override
    public CompletionStage<Status> statusAsync(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(uuid);
        if (!session.admitted()) return CompletableFuture.completedFuture(Status.LOCKED);
        return submit(() -> readStatus(uuid, session), Status.LOCKED);
    }

    private Status readStatus(UUID uuid, AuthSnapshot session) {
        PasswordState state = state(uuid);
        if (closed || !sameSession(session, AuthAccess.snapshot(uuid)) || !state.operation.tryLock()) return Status.LOCKED;
        try {
            long revision = state.revision();
            Optional<StoredPassword> stored = load(uuid);
            Unlock unlock = state.unlock;
            boolean permitted = stored.isEmpty()
                || (unlock != null && unlock.password().equals(stored.get()) && sameSession(unlock.session(), session));
            long expiresAt = stored.isEmpty() || unlock == null ? Long.MAX_VALUE : unlock.expiresAt();
            return new Status(stored.isPresent(), () -> permitted && !closed && revision == state.revision()
                && expiresAt > System.currentTimeMillis() && sameSession(session, AuthAccess.snapshot(uuid)),
                () -> submit(() -> matchesStoredPassword(uuid, state, stored), false));
        } catch (SQLException exception) {
            return Status.LOCKED;
        } finally {
            state.operation.unlock();
        }
    }

    private boolean matchesStoredPassword(UUID uuid, PasswordState state, Optional<StoredPassword> expected) {
        if (!state.operation.tryLock()) return false;
        try {
            return expected.equals(load(uuid));
        } catch (SQLException exception) {
            return false;
        } finally {
            state.operation.unlock();
        }
    }

    @Override
    public boolean verify(@NotNull Player player, @NotNull String password) {
        UUID uuid = player.getUniqueId();
        PasswordState state = state(uuid);
        return verify(uuid, password, AuthAccess.snapshot(uuid), state, state.revision());
    }

    @Override
    public CompletionStage<Boolean> verifyAsync(@NotNull Player player, @NotNull String password) {
        UUID uuid = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(uuid);
        if (!validInput(password) || !session.admitted()) return CompletableFuture.completedFuture(false);
        PasswordState state = state(uuid);
        long revision = state.revision();
        if (!verifications.add(uuid)) return CompletableFuture.completedFuture(false);
        return submit(() -> verify(uuid, password, session, state, revision), false)
            .whenComplete((ignored, failure) -> verifications.remove(uuid));
    }

    private <T> CompletionStage<T> submit(Supplier<T> operation, T unavailable) {
        if (closed) return CompletableFuture.completedFuture(unavailable);
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        try {
            verifier.execute(() -> {
                try {
                    result.complete(closed ? unavailable : operation.get());
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                } finally {
                    pending.remove(result);
                }
            });
        } catch (RejectedExecutionException rejected) {
            pending.remove(result);
            result.complete(unavailable);
        }
        return result;
    }

    private boolean validInput(String password) {
        return password != null && password.length() <= Math.max(1024, maxLength) && !password.isBlank();
    }

    private boolean verify(UUID uuid, String password, AuthSnapshot session, PasswordState state, long revision) {
        if (closed || !validInput(password) || !sameSession(session, AuthAccess.snapshot(uuid))) return false;
        if (!state.operation.tryLock()) return false;
        try {
            if (state.revision() != revision) return false;
            long now = System.nanoTime();
            if (state.lastVerification != 0 && now - state.lastVerification < VERIFY_INTERVAL_NANOS) return false;
            state.lastVerification = now;
            Optional<StoredPassword> stored = load(uuid);
            if (stored.isEmpty()) return sameSession(session, AuthAccess.snapshot(uuid));
            StoredPassword value = stored.get();
            byte[] actual = derive(password, Base64.getDecoder().decode(value.salt()));
            if (!MessageDigest.isEqual(Base64.getDecoder().decode(value.hash()), actual)) return false;
            return grant(uuid, state, revision, value, session);
        } catch (Exception exception) {
            return false;
        } finally {
            state.operation.unlock();
        }
    }

    private boolean grant(UUID uuid, PasswordState state, long revision, StoredPassword password, AuthSnapshot session) {
        return !closed && sameSession(session, AuthAccess.snapshot(uuid))
            && state.grant(revision, new Unlock(password, session, System.currentTimeMillis() + unlockTimeoutMillis));
    }

    private static boolean sameSession(AuthSnapshot first, AuthSnapshot second) {
        return Objects.equals(first.connectionId(), second.connectionId()) && first.generation() == second.generation()
            && (!first.admitted() || second.admitted());
    }

    @Override
    public boolean set(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword) {
        UUID uuid = player.getUniqueId();
        PasswordState state = state(uuid);
        return set(uuid, oldPassword, newPassword, AuthAccess.snapshot(uuid), state, state.revision());
    }

    @Override
    public CompletionStage<Boolean> setAsync(@NotNull Player player, @NotNull String oldPassword, @NotNull String newPassword) {
        UUID uuid = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(uuid);
        if (!session.admitted()) return CompletableFuture.completedFuture(false);
        PasswordState state = state(uuid);
        long revision = state.revision();
        return submit(() -> set(uuid, oldPassword, newPassword, session, state, revision), false);
    }

    private boolean set(UUID uuid, String oldPassword, String newPassword, AuthSnapshot session, PasswordState state, long revision) {
        String trimmed = newPassword == null ? "" : newPassword.trim();
        if (trimmed.length() < minLength || trimmed.length() > maxLength) {
            return false;
        }
        if (closed || !state.operation.tryLock()) return false;
        try {
            if (revision != state.revision() || !sameSession(session, AuthAccess.snapshot(uuid))) return false;
            Optional<StoredPassword> existing = load(uuid);
            if (existing.isPresent() && !verify(uuid, oldPassword, session, state, revision)) {
                return false;
            }
            byte[] salt = new byte[SALT_BYTES];
            RANDOM.nextBytes(salt);
            var replacement = new StoredPassword(Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(derive(trimmed, salt)));
            if (closed || revision != state.revision() || !sameSession(session, AuthAccess.snapshot(uuid))) return false;
            String sql = existing.isPresent()
                ? "UPDATE axs_secondary_password SET salt=?,hash=?,updated_at=? WHERE player_uuid=? AND salt=? AND hash=?"
                : "INSERT INTO axs_secondary_password(salt,hash,updated_at,player_uuid) VALUES(?,?,?,?)";
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, replacement.salt());
                statement.setString(2, replacement.hash());
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, uuid.toString());
                if (existing.isPresent()) {
                    statement.setString(5, existing.get().salt());
                    statement.setString(6, existing.get().hash());
                }
                if (statement.executeUpdate() != 1) return false;
            }
            state.replace(revision, !closed && sameSession(session, AuthAccess.snapshot(uuid))
                ? new Unlock(replacement, session, System.currentTimeMillis() + unlockTimeoutMillis) : null);
            return true;
        } catch (Exception exception) {
            return false;
        } finally {
            state.operation.unlock();
        }
    }

    @Override
    public boolean clear(@NotNull Player player, @NotNull String password) {
        UUID uuid = player.getUniqueId();
        PasswordState state = state(uuid);
        return clear(uuid, password, AuthAccess.snapshot(uuid), state, state.revision());
    }

    @Override
    public CompletionStage<Boolean> clearAsync(@NotNull Player player, @NotNull String password) {
        UUID uuid = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(uuid);
        if (!session.admitted()) return CompletableFuture.completedFuture(false);
        PasswordState state = state(uuid);
        long revision = state.revision();
        return submit(() -> clear(uuid, password, session, state, revision), false);
    }

    private boolean clear(UUID uuid, String password, AuthSnapshot session, PasswordState state, long revision) {
        if (closed || !state.operation.tryLock()) return false;
        try {
            Optional<StoredPassword> stored = load(uuid);
            if (stored.isEmpty() || !verify(uuid, password, session, state, revision)
                || closed || revision != state.revision() || !sameSession(session, AuthAccess.snapshot(uuid))) return false;
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM axs_secondary_password WHERE player_uuid=? AND salt=? AND hash=?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, stored.get().salt());
                statement.setString(3, stored.get().hash());
                if (statement.executeUpdate() != 1) return false;
            }
            state.revoke();
            return true;
        } catch (SQLException exception) {
            return false;
        } finally {
            state.operation.unlock();
        }
    }

    @Override
    public void lock(@NotNull Player player) {
        state(player.getUniqueId()).revoke();
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
        PasswordState state = state(playerUuid);
        if (closed || !state.operation.tryLock()) return false;
        try {
            try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM axs_secondary_password WHERE player_uuid=?")) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            }
            state.revoke();
            return true;
        } catch (SQLException exception) {
            return false;
        } finally {
            state.operation.unlock();
        }
    }

    public CompletionStage<Boolean> clearPasswordByUuidAsync(Player actor, UUID target) {
        UUID actorId = actor.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(actorId);
        if (!session.admitted()) return CompletableFuture.completedFuture(false);
        return submit(() -> sameSession(session, AuthAccess.snapshot(actorId)) && clearPasswordByUuid(target), false);
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
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    @Override
    public void close() {
        closed = true;
        verifier.shutdownNow();
        pending.forEach(result -> result.cancel(false));
        pending.clear();
        verifications.clear();
        states.clear();
        verifier.stopped().whenComplete((ignored, failure) -> repository.shutdown());
    }

    public CompletionStage<Void> stopped() { return verifier.stopped(); }

    private record StoredPassword(String salt, String hash) {}
    private record Unlock(StoredPassword password, AuthSnapshot session, long expiresAt) {}

    private static final class PasswordState {
        private final ReentrantLock operation = new ReentrantLock();
        private volatile Unlock unlock;
        private long lastVerification;
        private long revision;

        private synchronized long revision() {
            return revision;
        }

        private synchronized boolean grant(long expectedRevision, Unlock next) {
            if (revision != expectedRevision) return false;
            unlock = next;
            return true;
        }

        private synchronized void revoke() {
            revision++;
            unlock = null;
        }

        private synchronized void replace(long expectedRevision, Unlock next) {
            unlock = revision == expectedRevision ? next : null;
            revision++;
        }
    }

    private static final class Repository extends AbstractModuleRepository {

        private Repository(DataSource dataSource, StorageDescriptor descriptor,
                           java.io.File dataFolder, Logger logger) {
            super("AXS-SecondaryPassword", dataSource, descriptor, dataFolder, logger);
        }

        private Connection open() throws SQLException {
            return getConnection();
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
