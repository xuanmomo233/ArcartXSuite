package xuanmo.arcartxsuite.auth.runtime;

import java.net.InetSocketAddress;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationSettingsTest {
    private static final LoginTransport ONLINE = new LoginTransport(true,"0.0.0.0",false,List.of());
    private static final LoginTransport LOCAL = new LoginTransport(false,"127.0.0.1",false,List.of());
    private AuthenticationSettings load(String extra) throws Exception {
        return load(extra, ONLINE);
    }
    private AuthenticationSettings load(String extra, LoginTransport transport) throws Exception {
        return document("schema-version: 1\nbackend: standalone\nrealm-id: test\nrequirements:\n  terms: false\n"+extra, transport);
    }
    private AuthenticationSettings document(String content, LoginTransport transport) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(content);
        return AuthenticationSettings.read(config, transport);
    }
    private static final String CENTER="""
        schema-version: 1
        backend: center
        realm-id: network
        ingress:
          type: external
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
        var settings=document(CENTER, ONLINE);
        assertNotNull(settings.center());
        assertEquals("lobby",settings.center().nodeId());
        assertEquals("network",settings.realm());
        assertEquals("lobby-node",settings.audience());
        assertFalse(settings.passwordAllowed());
    }
    @Test void centerRejectsMixedIngressAndConflictingLocalPolicies() {
        assertThrows(IllegalArgumentException.class,()->document(CENTER.replace("type: external","type: signed-proxy"), ONLINE));
        assertThrows(IllegalArgumentException.class,()->document(CENTER+"requirements:\n  terms: false\n", ONLINE));
        assertThrows(IllegalArgumentException.class,()->document(CENTER, LOCAL));
    }
    @Test void onlineDefaultsAuthenticateMicrosoftWithoutGamePassword() throws Exception {
        var settings=load("");
        assertEquals(AuthenticationSettings.Ingress.ONLINE, settings.ingress());
        assertFalse(settings.passwordAllowed());
        assertNull(settings.nativeGateway());
        assertTrue(settings.trustedProxies().isEmpty());
        assertFalse(settings.terms().enabled());
    }
    @Test void gamePasswordIsAvailableOnlyForDirectLoopbackOfflineServers() throws Exception {
        assertTrue(load("", LOCAL).passwordAllowed());
        assertThrows(IllegalArgumentException.class, () -> load("", new LoginTransport(false,"",false,List.of())));
        assertThrows(IllegalArgumentException.class, () -> load("", new LoginTransport(false,"0.0.0.0",false,List.of())));
        assertThrows(IllegalArgumentException.class, () -> load("", new LoginTransport(false,"127.0.0.1",true,List.of())));
        assertThrows(IllegalArgumentException.class, () -> load("ingress:\n  type: password\n"));
        assertThrows(IllegalArgumentException.class, () -> load("ingress:\n  type: online\n  password-login: true\n"));
        assertThrows(IllegalArgumentException.class, () -> load("ingress:\n  type: online\n", new LoginTransport(true,"127.0.0.1",true,List.of())));
        assertTrue(LoginTransport.localPeer(new InetSocketAddress("127.0.0.1", 25565)));
        assertTrue(LoginTransport.localPeer(new InetSocketAddress("::1", 25565)));
        assertFalse(LoginTransport.localPeer(new InetSocketAddress("192.0.2.1", 25565)));
        assertFalse(LoginTransport.localPeer(InetSocketAddress.createUnresolved("localhost",25565)));
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
