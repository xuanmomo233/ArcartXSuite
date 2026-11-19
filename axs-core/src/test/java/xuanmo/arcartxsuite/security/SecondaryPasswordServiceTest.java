package xuanmo.arcartxsuite.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;

class SecondaryPasswordServiceTest {
    @TempDir Path directory;

    @Test
    void asyncStatusReadsStorageOnWorkerAndDoesNotAuthorizeAfterLock() throws Exception {
        String jdbc = "jdbc:sqlite:" + directory.resolve("async-password.db");
        DataSource source = mock(DataSource.class);
        AtomicReference<Thread> reader = new AtomicReference<>();
        when(source.getConnection()).thenAnswer(call -> {
            reader.set(Thread.currentThread());
            return DriverManager.getConnection(jdbc);
        });
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        AuthRuntimeCapability runtime = mock(AuthRuntimeCapability.class);
        when(runtime.snapshot(id)).thenReturn(new AuthSnapshot(id, UUID.randomUUID(), 1, id,
            AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "", Set.of(), ""));
        AuthAccess.install(() -> runtime);
        try (var service = new SecondaryPasswordService(directory.toFile(), 60, 4, 32, source,
            StorageDescriptor.sqlite("async-password.db"), Logger.getAnonymousLogger())) {
            assertTrue(service.setAsync(player, "", "test-pass").toCompletableFuture().get(10, TimeUnit.SECONDS));
            var status = service.statusAsync(player).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(reader.get() != Thread.currentThread());
            assertTrue(status.passwordSet());
            assertTrue(status.unlocked());
            service.lock(player);
            assertFalse(status.unlocked());
        } finally {
            AuthAccess.install(() -> null);
        }
    }

    @Test
    void cannotTreatUnavailableOrChangedCredentialsAsUnlocked() throws Exception {
        String jdbc = "jdbc:sqlite:" + directory.resolve("password.db");
        DataSource source = mock(DataSource.class);
        AtomicBoolean unavailable = new AtomicBoolean();
        when(source.getConnection()).thenAnswer(call -> {
            if (unavailable.get()) throw new SQLException("storage unavailable");
            return DriverManager.getConnection(jdbc);
        });
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        try (var service = new SecondaryPasswordService(directory.toFile(), 60, 4, 32, source,
            StorageDescriptor.sqlite("password.db"), Logger.getAnonymousLogger())) {
            assertFalse(service.isPasswordSet(player));
            assertTrue(service.isUnlocked(player));
            assertTrue(service.set(player, "", "test-pass"));
            assertTrue(service.isUnlocked(player));

            unavailable.set(true);
            assertTrue(service.isPasswordSet(player));
            assertFalse(service.isUnlocked(player));
            assertFalse(service.verify(player, "test-pass"));

            unavailable.set(false);
            try (var connection = DriverManager.getConnection(jdbc); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE axs_secondary_password SET hash='replaced on another node'");
            }
            assertTrue(service.isPasswordSet(player));
            assertFalse(service.isUnlocked(player));
        }
    }
}
