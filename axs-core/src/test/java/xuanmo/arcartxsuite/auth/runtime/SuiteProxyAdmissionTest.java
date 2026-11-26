package xuanmo.arcartxsuite.auth.runtime;

import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.sql.DriverManager;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.auth.minecraft.ProxyProofs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
class SuiteProxyAdmissionTest {
    @Test void signedProxyAdmissionFailsClosedAndTracksHostLeaseChanges() throws Exception {
        var suiteProxyAllowed = new AtomicBoolean(false);
        var messenger = mock(Messenger.class);
        var plugin = mock(JavaPlugin.class);
        var player = player();
        UUID profile = player.getUniqueId();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getMessenger).thenReturn(messenger);
            try (var runtime = runtime(plugin, AuthenticationSettings.Ingress.SIGNED_PROXY, suiteProxyAllowed::get)) {
                runtime.onJoin(join(player));
                runtime.refreshProxyIngress();
                assertTrue(runtime.requiresAdmission());
                assertEquals("SUITE_PROXY_LICENSE_REQUIRED", runtime.snapshot(profile).reason());
                assertEquals(AuthSnapshot.Stage.RESTRICTED, runtime.snapshot(profile).stage());
                assertFalse(runtime.snapshot(profile).admitted());
                assertEquals(0L, runtime.eligibleNanos(profile));
                assertNull(runtime.proxyChallenge(player));
                verifyNoInteractions(messenger);

                suiteProxyAllowed.set(true);
                runtime.refreshProxyIngress();
                var handler = ArgumentCaptor.forClass(ProxyProofIngress.class);
                verify(messenger).registerIncomingPluginChannel(eq(plugin), eq(ProxyProofIngress.CHANNEL), handler.capture());
                verify(messenger).registerOutgoingPluginChannel(plugin, ProxyProofIngress.CHANNEL);
                assertEquals(AuthSnapshot.Stage.LOADING, runtime.snapshot(profile).stage());
                long initialGeneration = runtime.snapshot(profile).generation();

                suiteProxyAllowed.set(false);
                assertFalse(runtime.snapshot(profile).admitted());
                assertEquals("SUITE_PROXY_LICENSE_REQUIRED", runtime.snapshot(profile).reason());
                var unsolicited = mock(Player.class);
                handler.getValue().onPluginMessageReceived(ProxyProofIngress.CHANNEL, unsolicited, new byte[0]);
                runtime.acceptProxyProof(unsolicited, UUID.randomUUID(), "untrusted");
                verifyNoInteractions(unsolicited);
                runtime.refreshProxyIngress();
                verify(messenger).unregisterIncomingPluginChannel(plugin, ProxyProofIngress.CHANNEL, handler.getValue());
                verify(messenger).unregisterOutgoingPluginChannel(plugin, ProxyProofIngress.CHANNEL);
                assertTrue(runtime.snapshot(profile).generation() > initialGeneration);

                suiteProxyAllowed.set(true);
                runtime.refreshProxyIngress();
                verify(messenger, times(2)).registerOutgoingPluginChannel(plugin, ProxyProofIngress.CHANNEL);
                assertEquals("REVOKED", runtime.snapshot(profile).reason());
                assertFalse(runtime.snapshot(profile).identityVerified());
            }
        }
    }

    @Test void validProofAdmitsWithASeatButDoesNotSurviveRevocation(@TempDir Path directory) throws Exception {
        var suiteProxyAllowed = new AtomicBoolean(true);
        var plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("suite-proxy-admission-test"));
        var storage = mock(StorageManager.class);
        when(storage.getHostConnection()).thenAnswer(call ->
            DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("proxy-auth.sqlite")));
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var trust = new ProxyProofs.Trust("proxy", keys.getPublic(), Set.of("microsoft"));
        var scheduler = mock(BukkitScheduler.class);
        var tasks = new LinkedBlockingQueue<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1, Runnable.class));
            return null;
        });
        var server = mock(Server.class);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(server.getMessenger()).thenReturn(mock(Messenger.class));
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.getScheduler()).thenReturn(scheduler);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object originalServer = serverField.get(null);
        serverField.set(null, server);
        try (var runtime = new AuthenticationRuntime(plugin, storage, () -> null,
                settings(AuthenticationSettings.Ingress.SIGNED_PROXY, List.of(trust)), suiteProxyAllowed::get)) {
            runtime.start();
            Player player = player();
            UUID profile = player.getUniqueId();
            runtime.onJoin(join(player));
            runCompletion(tasks);

            var challenge = JsonParser.parseString(runtime.proxyChallenge(player)).getAsJsonObject();
            UUID connection = UUID.fromString(challenge.get("connection").getAsString());
            UUID nonce = UUID.fromString(challenge.get("challenge").getAsString());
            long now = System.currentTimeMillis();
            String proof = ProxyProofs.sign("proxy", keys.getPrivate(),
                new ProxyProofs.Identity(profile, player.getName(), "microsoft", now),
                new ProxyProofs.Target("test", "node", connection, nonce), now);
            runtime.acceptProxyProof(player, connection, proof);
            runCompletion(tasks);
            assertTrue(runtime.snapshot(profile).admitted());

            suiteProxyAllowed.set(false);
            assertFalse(runtime.snapshot(profile).admitted());
            runtime.refreshProxyIngress();
            suiteProxyAllowed.set(true);
            runtime.refreshProxyIngress();
            var newChallenge = JsonParser.parseString(runtime.proxyChallenge(player)).getAsJsonObject();
            assertNotEquals(nonce.toString(), newChallenge.get("challenge").getAsString());
            assertFalse(runtime.snapshot(profile).admitted());
            runtime.acceptProxyProof(player, connection, proof);
            runCompletion(tasks);
            assertEquals("SIGNED_PROOF_REJECTED", runtime.snapshot(profile).reason());
            assertFalse(runtime.snapshot(profile).admitted());
        } finally {
            serverField.set(null, originalServer);
        }
    }

    @Test void directIngressDoesNotQueryTheSuiteProxyEntitlement() throws Exception {
        var plugin = mock(JavaPlugin.class);
        for (var mode : List.of(AuthenticationSettings.Ingress.PASSWORD, AuthenticationSettings.Ingress.ONLINE,
                AuthenticationSettings.Ingress.LOCAL_MIXED)) {
            try (var runtime = runtime(plugin, mode, () -> {
                throw new AssertionError("A direct login must not check proxy entitlement");
            })) {
                var player = player();
                runtime.onJoin(join(player));
                runtime.refreshProxyIngress();
                assertEquals(AuthSnapshot.Stage.LOADING, runtime.snapshot(player.getUniqueId()).stage());
                assertEquals(mode == AuthenticationSettings.Ingress.PASSWORD, runtime.passwordAuthenticationAllowed());
                assertNull(runtime.proxyChallenge(player));
            }
        }
    }

    private static AuthenticationRuntime runtime(JavaPlugin plugin, AuthenticationSettings.Ingress ingress,
                                                  BooleanSupplier suiteProxyAllowed) throws Exception {
        var trustedProxies = ingress == AuthenticationSettings.Ingress.SIGNED_PROXY
            ? List.of(new ProxyProofs.Trust("proxy", KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic(), Set.of("microsoft")))
            : List.<ProxyProofs.Trust>of();
        return new AuthenticationRuntime(plugin, mock(StorageManager.class), () -> null, settings(ingress, trustedProxies), suiteProxyAllowed);
    }

    private static AuthenticationSettings settings(AuthenticationSettings.Ingress ingress,
                                                   List<ProxyProofs.Trust> trustedProxies) {
        return new AuthenticationSettings("test", ZoneId.of("UTC"), 30_000_000_000L, null, trustedProxies, "node", ingress,
            new AuthenticationSettings.PasswordPolicy(8, 128, 5, 300_000), false,
            new AuthRuntimeCapability.Terms(false, "", List.of()), new AuthenticationSettings.Performance(1, 8, 1, 8), null);
    }

    private static void runCompletion(LinkedBlockingQueue<Runnable> tasks) throws InterruptedException {
        Runnable completion = tasks.poll(15, TimeUnit.SECONDS);
        assertNotNull(completion, "Timed out waiting for the authentication worker");
        completion.run();
    }

    private static Player player() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        return player;
    }

    private static PlayerJoinEvent join(Player player) {
        var event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }
}
