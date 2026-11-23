package xuanmo.arcartxsuite.auth.runtime;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability.Result;
import xuanmo.arcartxsuite.api.storage.StorageManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
class OfflinePasswordLoginTest {
    @TempDir Path directory;

    @Test
    void remotePlayerRegistersReconnectsAndChangesTheirOwnPassword() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("offline-password-test"));
        var storage = mock(StorageManager.class);
        String url = "jdbc:sqlite:" + directory.resolve("accounts.sqlite");
        when(storage.getHostConnection()).thenAnswer(call -> DriverManager.getConnection(url));
        var config = new YamlConfiguration();
        config.loadFromString("schema-version: 1\nbackend: standalone\nrealm-id: local\nrequirements:\n  terms: false\n");
        var settings = AuthenticationSettings.read(config, new LoginTransport(false, false, List.of()));

        var tasks = new LinkedBlockingQueue<Runnable>();
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1, Runnable.class));
            return null;
        });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previousServer = serverField.get(null);
        var runtime = new AuthenticationRuntime(plugin, storage, () -> null, settings);
        serverField.set(null, server);
        try {
            runtime.start();
            UUID profile = UUID.randomUUID();
            Player first = player(profile, "192.0.2.18");
            var fresh = join(runtime, first, tasks);
            assertEquals(AuthSnapshot.Registration.UNREGISTERED, fresh.registration());
            assertFalse(fresh.admitted());
            var registered = complete(runtime.authenticate(profile, fresh.connectionId(),
                    "first-test-password".toCharArray(), true, false, ""), tasks);
            assertTrue(registered.success(), registered.code());
            assertEquals("local-password", registered.snapshot().provider());
            assertTrue(registered.snapshot().admitted());
            runtime.onQuit(new PlayerQuitEvent(first, ""));

            Player returning = player(profile, "198.51.100.24");
            var next = join(runtime, returning, tasks);
            assertNotEquals(fresh.connectionId(), next.connectionId());
            assertEquals(AuthSnapshot.Registration.REGISTERED, next.registration());
            assertFalse(next.identityVerified());
            var wrong = complete(runtime.authenticate(profile, next.connectionId(), "wrong-password".toCharArray(), false, false, ""), tasks);
            assertFalse(wrong.success());
            assertFalse(wrong.snapshot().admitted());
            var login = complete(runtime.authenticate(profile, next.connectionId(), "first-test-password".toCharArray(), false, false, ""), tasks);
            assertTrue(login.snapshot().admitted(), login.code());
            assertEquals(registered.snapshot().accountId(), login.snapshot().accountId());

            var changed = complete(runtime.changePassword(profile, next.connectionId(),
                    "first-test-password".toCharArray(), "second-test-password".toCharArray()), tasks);
            assertTrue(changed.success(), changed.code());
            assertFalse(changed.snapshot().admitted());
            runtime.onQuit(new PlayerQuitEvent(returning, ""));
            var afterChange = join(runtime, first, tasks);
            assertFalse(complete(runtime.authenticate(profile, afterChange.connectionId(),
                    "first-test-password".toCharArray(), false, false, ""), tasks).success());
            assertTrue(complete(runtime.authenticate(profile, afterChange.connectionId(),
                    "second-test-password".toCharArray(), false, false, ""), tasks).snapshot().admitted());
        } finally {
            try {
                runtime.close();
                runtime.drained().toCompletableFuture().get(15, TimeUnit.SECONDS);
            } finally {
                serverField.set(null, previousServer);
            }
        }
    }

    private static Player player(UUID profile, String address) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(profile);
        when(player.getName()).thenReturn("Alex");
        when(player.getAddress()).thenReturn(new InetSocketAddress(address, 49152));
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static AuthSnapshot join(AuthenticationRuntime runtime, Player player, BlockingQueue<Runnable> tasks) throws Exception {
        runtime.onJoin(new PlayerJoinEvent(player, ""));
        verify(player, never()).kickPlayer(anyString());
        UUID id = player.getUniqueId();
        pumpUntil(tasks, () -> runtime.snapshot(id).stage() == AuthSnapshot.Stage.AWAITING_IDENTITY);
        return runtime.snapshot(id);
    }

    private static Result complete(CompletionStage<Result> submitted, BlockingQueue<Runnable> tasks) throws Exception {
        var future = submitted.toCompletableFuture();
        pumpUntil(tasks, future::isDone);
        return future.get();
    }

    private static void pumpUntil(BlockingQueue<Runnable> tasks, BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!done.getAsBoolean()) {
            Runnable action = tasks.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            assertNotNull(action, "Timed out waiting for the login result on the server thread");
            action.run();
        }
    }
}
