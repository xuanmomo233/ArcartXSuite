package xuanmo.arcartxsuite.auth.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationSettingsTest {
    @TempDir Path directory;
    private AuthenticationSettings load(String extra) throws Exception {
        return document("schema-version: 1\nbackend: standalone\nrealm-id: test\nrequirements:\n  terms: false\n"+extra);
    }
    private AuthenticationSettings document(String content) throws Exception {
        JavaPlugin plugin=mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        Files.writeString(directory.resolve("authentication.yml"),content,StandardCharsets.UTF_8);
        return AuthenticationSettings.load(plugin);
    }
    private static final String CENTER="""
        schema-version: 1
        backend: center
        realm-id: network
        ingress:
          type: password
          audience: lobby-node
        performance:
          workers: 2
          queue-capacity: 128
          renewal-workers: 2
          renewal-queue-capacity: 4096
        center:
          url: http://127.0.0.1:25600
          node-id: lobby
          key-environment: AXS_AUTH_LOBBY_KEY
        """;
    @Test void centerSelectsRemoteAdapterWithoutLoadingLocalAccountPolicies() throws Exception {
        var settings=document(CENTER);
        assertNotNull(settings.center());
        assertEquals("lobby",settings.center().nodeId());
        assertEquals("network",settings.realm());
        assertEquals("lobby-node",settings.audience());
    }
    @Test void centerRejectsMixedIngressAndConflictingLocalPolicies() {
        assertThrows(IllegalArgumentException.class,()->document(CENTER.replace("type: password","type: signed-proxy")));
        assertThrows(IllegalArgumentException.class,()->document(CENTER+"requirements:\n  terms: false\n"));
    }
    @Test void standaloneDefaultsHaveOneUsableIdentityMethod() throws Exception {
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
        bukkit.when(org.bukkit.Bukkit::getOnlineMode).thenReturn(true);
        var settings=load("");
        assertTrue(settings.passwordAllowed());
        assertNull(settings.nativeGateway());
        assertTrue(settings.trustedProxies().isEmpty());
        assertFalse(settings.terms().enabled());
        }
    }
    @Test void passwordIngressRejectsUnencryptedOfflineTransport() {
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getOnlineMode).thenReturn(false);
            assertThrows(IllegalArgumentException.class,()->load(""));
        }
    }
    @Test void futureBackendAndUnknownPoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->load("client-integrity: true\n"));
        assertThrows(IllegalArgumentException.class,()->load("ingress:\n  type: center\n"));
    }
    @Test void passwordIngressCannotDisableItsOnlyMethod() {
        assertThrows(IllegalArgumentException.class,()->load("ingress:\n  type: password\n  password-login: false\n"));
    }
    @Test void coercedBooleansAndWrappedIntegerPoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->load("ingress:\n  password-login: 'true'\n"));
        assertThrows(IllegalArgumentException.class,()->load("password:\n  max-attempts: 4294967301\n"));
    }
    @Test void signedIngressRequiresExplicitPinnedTrustObjects() {
        assertThrows(IllegalArgumentException.class,()->load("ingress:\n  type: signed-proxy\n  audience: node\n  trusted-proxies: []\n"));
        assertThrows(IllegalArgumentException.class,()->load("ingress:\n  type: signed-proxy\n  audience: node\n  trusted-proxies: [invalid]\n"));
    }
}
