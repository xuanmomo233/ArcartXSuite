package xuanmo.arcartxsuite.auth.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LauncherRateLimitTest {
    @Test void retryAfterIsBoundedAndMalformedHeadersUseASafeDelay() {
        assertEquals(1,LauncherAuthorizationClient.retryAfter("0"));
        assertEquals(60,LauncherAuthorizationClient.retryAfter("99"));
        assertEquals(5,LauncherAuthorizationClient.retryAfter(null));
        assertEquals(5,LauncherAuthorizationClient.retryAfter("99999999"));
        assertEquals(5,LauncherAuthorizationClient.retryAfter("invalid"));
    }
}
