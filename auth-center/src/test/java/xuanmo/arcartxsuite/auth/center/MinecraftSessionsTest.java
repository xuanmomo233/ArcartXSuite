package xuanmo.arcartxsuite.auth.center;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftSessionsTest {
    @Test void aLaunchTicketAndPendingHandshakeHaveSeparateExpiryBounds() throws Exception {
        var clock = new AtomicLong(1_000_000);
        var profile = new GameExternalStore.MinecraftProfile(UUID.randomUUID(),"AXS_0123456789ab");
        var identity = new GameExternalStore.MinecraftIdentity("realm",new OidcLogin.Identity("fixture","subject"),
            new GameExternalStore.Ownership(UUID.randomUUID(),1),profile);
        var sessions = new MinecraftSessions(URI.create("https://center.example.invalid/yggdrasil/"),candidate -> true,clock::get);
        var expired = sessions.issue(identity);
        clock.addAndGet(300_000);
        assertFalse(sessions.join(expired.accessToken(),expired.profileId(),"abc","127.0.0.1"));

        var fresh = sessions.issue(identity);
        assertTrue(sessions.join(fresh.accessToken(),fresh.profileId(),"abc","127.0.0.1"));
        clock.addAndGet(30_000);
        assertNull(sessions.hasJoined(profile.name(),"abc",null));

        var nearExpiry = sessions.issue(identity);
        clock.addAndGet(299_000);
        assertTrue(sessions.join(nearExpiry.accessToken(),nearExpiry.profileId(),"def","127.0.0.1"));
        clock.addAndGet(1_000);
        assertNull(sessions.hasJoined(profile.name(),"def",null),"join cannot extend the launcher credential lifetime");
    }
}
