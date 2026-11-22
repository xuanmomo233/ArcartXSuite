package xuanmo.arcartxsuite.auth.minecraft;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import xuanmo.arcartxsuite.auth.protocol.MinecraftSessionProtocol;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MixedYggdrasilConfigurationTest {
    @Test void separateCenterExtendsTheSameHandshakeProviderList() {
        String endpoint = "https://identity.example.invalid/sessionserver/session/minecraft/hasJoined";
        var providers = MixedYggdrasilGateway.verificationProviders(endpoint);
        assertEquals(List.of("microsoft", "littleskin", "center"),
            providers.stream().map(YggdrasilVerifier.Provider::id).toList());
        assertEquals(URI.create(endpoint), providers.get(2).hasJoinedEndpoint());
        assertEquals(2, MixedYggdrasilGateway.verificationProviders("").size());
    }

    @Test void centerRequiresAnExplicitHttpsEndpointWithoutCredentials() {
        assertThrows(IllegalArgumentException.class,
            () -> MixedYggdrasilGateway.verificationProviders("http://identity.example.invalid/hasJoined"));
        assertThrows(IllegalArgumentException.class,
            () -> MixedYggdrasilGateway.verificationProviders("https://user@identity.example.invalid/hasJoined"));
        assertThrows(IllegalArgumentException.class,
            () -> MixedYggdrasilGateway.verificationProviders("https://sessionserver.mojang.com/session/minecraft/hasJoined"));
    }

    @Test void suiteCenterVerifiesItsProfileBeforeEitherOtherProvider() {
        var providers = MixedYggdrasilGateway.verificationProviders(null,"https://center.example.invalid");
        assertEquals(List.of("center","microsoft","littleskin"),providers.stream().map(YggdrasilVerifier.Provider::id).toList());
        assertTrue(providers.get(0).centerProfilesOnly());
        assertEquals(MinecraftSessionProtocol.HAS_JOINED,providers.get(0).hasJoinedEndpoint().getPath());
        var requests = new ArrayList<String>();
        var verifier = new YggdrasilVerifier(providers, url -> {
            requests.add(url.getHost());
            assertEquals("center.example.invalid",url.getHost());
            return reply(url,200,"AXS_0123456789ab");
        });
        var result = verifier.verify("AXS_0123456789ab","abcd",null);
        assertEquals(YggdrasilVerifier.Status.VERIFIED,result.status());
        assertEquals("center",result.provider());
        assertEquals(List.of("center.example.invalid"),requests);
    }

    @Test void ordinaryProfilesKeepMicrosoftAndLittleSkinFallback() {
        var providers = MixedYggdrasilGateway.verificationProviders(null,"https://center.example.invalid");
        var requests = new ArrayList<String>();
        var verifier = new YggdrasilVerifier(providers, url -> {
            requests.add(url.getHost());
            if (url.getHost().equals("sessionserver.mojang.com"))
                return url.getQuery().contains("username=Steve") ? reply(url,200,"Steve") : reply(url,204,"");
            assertEquals("littleskin.cn",url.getHost());
            return reply(url,200,"Alex");
        });
        assertEquals("microsoft",verifier.verify("Steve","abcd",null).provider());
        assertEquals("littleskin",verifier.verify("Alex","abcd",null).provider());
        assertEquals(List.of("sessionserver.mojang.com","sessionserver.mojang.com","littleskin.cn"),requests);
        assertThrows(IllegalArgumentException.class,() -> MixedYggdrasilGateway.verificationProviders(
            "https://external.example.invalid/hasJoined","https://center.example.invalid"));
        assertThrows(IllegalArgumentException.class,
            () -> MixedYggdrasilGateway.verificationProviders(null,"http://center.example.invalid"));
    }

    private static HttpURLConnection reply(URI uri,int status,String name) throws IOException {
        return new HttpURLConnection(uri.toURL()) {
            @Override public int getResponseCode() { return status; }
            @Override public ByteArrayInputStream getInputStream() {
                String payload = "{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\""+name+"\"}";
                return new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
            }
            @Override public void disconnect() {}
            @Override public boolean usingProxy() { return false; }
            @Override public void connect() {}
        };
    }
}
