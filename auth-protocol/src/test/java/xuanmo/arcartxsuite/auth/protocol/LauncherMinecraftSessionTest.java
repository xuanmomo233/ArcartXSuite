package xuanmo.arcartxsuite.auth.protocol;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LauncherMinecraftSessionTest {
    @Test void honorsOnlyTheConfiguredCentersSessionApiAndRedactsTheTokenFromToString() throws Exception {
        var origin = URI.create("https://center.example.invalid");
        String endpoint = origin.resolve(MinecraftSessionProtocol.API_ROOT).toString();
        String token = "x".repeat(43);
        String reply = """
            {"sessionApi":"%s","profileId":"0123456789abcdef0123456789abcdef","name":"AXS_0123456789ab",
             "accessToken":"%s","expiresIn":300}
            """.formatted(endpoint,token);
        var session = LauncherAuthorizationClient.parseMinecraftSession(origin,reply);
        assertEquals(endpoint,session.sessionApi().toString());
        assertEquals(token,session.accessToken());
        assertFalse(session.toString().contains(token));
        assertThrows(IOException.class,() -> LauncherAuthorizationClient.parseMinecraftSession(origin,
            reply.replace("center.example.invalid","wrong.example.invalid")));
        assertThrows(IOException.class,() -> LauncherAuthorizationClient.parseMinecraftSession(origin,
            reply.replace("AXS_0123456789ab","UnownedName")));
        assertThrows(IOException.class,() -> LauncherAuthorizationClient.parseMinecraftSession(origin,
            reply.replace("300","86400")));
    }
}
