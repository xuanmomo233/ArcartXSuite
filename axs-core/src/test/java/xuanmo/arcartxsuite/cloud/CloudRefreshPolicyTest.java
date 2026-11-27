package xuanmo.arcartxsuite.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CloudRefreshPolicyTest {
    @Test
    void customerPackageNeverReplacesASelectedServerCodeAfterOnlineRejection() {
        String missingServer = "{\"error\":\"SERVER_NOT_FOUND\"}";
        assertFalse(CloudRefreshPolicy.canRepairBinding(true, false, 404, missingServer));
        assertTrue(CloudRefreshPolicy.canRepairBinding(false, false, 404, missingServer));
        assertFalse(CloudRefreshPolicy.canRepairBinding(false, true, 404, missingServer));
    }

    @Test
    void connectionUnavailabilityDoesNotIncludePeerCertificateFailure() {
        assertTrue(CloudRefreshPolicy.isConnectionUnavailable(new java.net.http.HttpTimeoutException("timeout")));
        assertTrue(CloudRefreshPolicy.isConnectionUnavailable(new java.net.NoRouteToHostException("disconnected")));
        assertFalse(CloudRefreshPolicy.isConnectionUnavailable(new javax.net.ssl.SSLHandshakeException("peer certificate")));
        assertFalse(CloudRefreshPolicy.isConnectionUnavailable(new java.io.IOException("unclassified response error")));
    }

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
