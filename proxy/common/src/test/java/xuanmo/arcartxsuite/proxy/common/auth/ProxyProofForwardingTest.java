package xuanmo.arcartxsuite.proxy.common.auth;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProxyProofForwardingTest {
    private final UUID profile = UUID.randomUUID(), connection = UUID.randomUUID(), challenge = UUID.randomUUID();
    private final ProxyProofForwarding.Destination destination = new ProxyProofForwarding.Destination("network", "lobby-node");

    private JsonObject challengeFrame() {
        JsonObject body = new JsonObject();
        body.addProperty("type", "challenge");
        body.addProperty("profile", profile.toString());
        body.addProperty("name", "Alice");
        body.addProperty("realm", "network");
        body.addProperty("audience", "lobby-node");
        body.addProperty("connection", connection.toString());
        body.addProperty("challenge", challenge.toString());
        return body;
    }

    private byte[] bytes(JsonObject body) { return body.toString().getBytes(StandardCharsets.UTF_8); }

    @Test void validChallengePreservesBothConnectionAndRotatingNonce() {
        var target = ProxyProofForwarding.parseChallenge(bytes(challengeFrame()), profile, "Alice", destination);
        assertNotNull(target);
        assertEquals(connection, target.connection());
        assertEquals(challenge, target.challenge());
    }

    @Test void otherProfilesRealmsAndAudiencesCannotRequestTheSameProof() {
        JsonObject body = challengeFrame();
        assertNull(ProxyProofForwarding.parseChallenge(bytes(body), UUID.randomUUID(), "Alice", destination));
        assertNull(ProxyProofForwarding.parseChallenge(bytes(body), profile, "Bob", destination));
        body.addProperty("realm", "other");
        assertNull(ProxyProofForwarding.parseChallenge(bytes(body), profile, "Alice", destination));
        body = challengeFrame();
        body.addProperty("audience", "other-node");
        assertNull(ProxyProofForwarding.parseChallenge(bytes(body), profile, "Alice", destination));
    }

    @Test void unsupportedFramesAndCoercedValuesAreRejected() {
        JsonObject body = challengeFrame();
        body.addProperty("version", 0);
        assertNull(ProxyProofForwarding.parseChallenge(bytes(body), profile, "Alice", destination));
        body = challengeFrame();
        body.addProperty("name", 123);
        assertNull(ProxyProofForwarding.parseChallenge(bytes(body), profile, "123", destination));
        body = challengeFrame();
        body.remove("challenge");
        assertNull(ProxyProofForwarding.parseChallenge(bytes(body), profile, "Alice", destination));
        assertNull(ProxyProofForwarding.parseChallenge(new byte[4097], profile, "Alice", destination));
    }

    @Test void bothProxyAdaptersUseTheSameStrictConfiguration() {
        var config = ProxyProofForwarding.Configuration.parse(Map.of("enabled", true, "issuer", "proxy-main",
            "gateway-url", "http://127.0.0.1:25599", "targets", Map.of("lobby", Map.of("realm", "network", "audience", "lobby-node"))));
        assertTrue(config.enabled());
        assertEquals(destination, config.targets().get("lobby"));
        assertThrows(IllegalArgumentException.class, () -> ProxyProofForwarding.Configuration.parse(Map.of("enabled", "true")));
        assertThrows(IllegalArgumentException.class, () -> ProxyProofForwarding.Configuration.parse(Map.of(
            "enabled", true, "issuer", "proxy-main", "gateway-url", "http://example.com:25599",
            "targets", Map.of("lobby", Map.of("realm", "network", "audience", "lobby-node")))));
        assertFalse(ProxyProofForwarding.Configuration.parse(Map.of()).enabled());
    }
}
