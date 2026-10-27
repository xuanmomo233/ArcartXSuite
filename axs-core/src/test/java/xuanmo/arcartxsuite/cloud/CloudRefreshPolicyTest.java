package xuanmo.arcartxsuite.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CloudRefreshPolicyTest {
    @Test
    void recognizesBothCloudsMissingServerContracts() {
        assertTrue(CloudRefreshPolicy.isMissingServer(404, "{\"ok\":false,\"error\":\"SERVER_NOT_FOUND\"}"));
        assertTrue(CloudRefreshPolicy.isMissingServer(403, "{\"error\":\"SERVER_NOT_REGISTERED\"}"));
    }

    @Test
    void doesNotRecoverRevokedDisabledOrInvalidCredentials() {
        for (String error : new String[]{"SERVER_REVOKED", "USER_DISABLED", "INVALID_CREDENTIALS",
                "SIGNATURE_REPLAY", "SERVER_PUBKEY_MISSING"}) {
            String body = "{\"error\":\"" + error + "\"}";
            assertFalse(CloudRefreshPolicy.isMissingServer(403, body));
            assertFalse(CloudRefreshPolicy.isSignatureMismatch(403, body));
        }
    }

    @Test
    void doesNotRecoverProxyErrorsOrErrorTextEmbeddedInAnotherField() {
        assertFalse(CloudRefreshPolicy.isMissingServer(404, "<html>Not found</html>"));
        assertFalse(CloudRefreshPolicy.isMissingServer(404, "{\"message\":\"SERVER_NOT_FOUND\"}"));
        assertFalse(CloudRefreshPolicy.isMissingServer(500, "{\"error\":\"SERVER_NOT_FOUND\"}"));
        assertFalse(CloudRefreshPolicy.isMissingServer(404, null));
        assertFalse(CloudRefreshPolicy.isSignatureMismatch(403,
            "{\"error\":\"SERVER_REVOKED\",\"message\":\"SIGNATURE_VERIFICATION_FAILED\"}"));
    }

    @Test
    void onlyRecoversExplicitSignatureMismatch() {
        assertTrue(CloudRefreshPolicy.isSignatureMismatch(403, "{\"error\":\"SIGNATURE_VERIFICATION_FAILED\"}"));
        assertTrue(CloudRefreshPolicy.isSignatureMismatch(403, "{\"error\":\"SIGNATURE_VERIFICATION_ERROR\"}"));
        assertFalse(CloudRefreshPolicy.isSignatureMismatch(401, "{\"error\":\"SIGNATURE_VERIFICATION_FAILED\"}"));
    }

    @Test
    void distinguishesDisabledStandbyFromNetworkAndDatabaseFailures() {
        assertTrue(CloudRefreshPolicy.isStandbyDisabled(503, "{\"ok\":false,\"error\":\"MAINTENANCE\"}"));
        assertFalse(CloudRefreshPolicy.isStandbyDisabled(503, "{\"error\":\"DATABASE_UNAVAILABLE\"}"));
        assertFalse(CloudRefreshPolicy.isStandbyDisabled(503, "<html>Maintenance</html>"));
        assertFalse(CloudRefreshPolicy.isStandbyDisabled(0, null));
        assertFalse(CloudRefreshPolicy.isStandbyDisabled(403, "{\"error\":\"SERVER_REVOKED\"}"));
    }
}
