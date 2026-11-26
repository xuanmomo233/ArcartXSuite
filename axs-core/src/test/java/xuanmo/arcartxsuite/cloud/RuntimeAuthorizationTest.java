package xuanmo.arcartxsuite.cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeAuthorizationTest {
    private final RuntimeAuthorization authorization = new RuntimeAuthorization();
    private final KeyPair issuer;
    RuntimeAuthorizationTest() throws Exception { issuer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
    private JsonObject claims(long now) {
        var body = new JsonObject();
        body.addProperty("purpose", RuntimeAuthorization.PURPOSE);
        body.addProperty("issuer", "axs-primary");
        body.addProperty("serverCode", "SERVER_A");
        body.addProperty("serverPublicKey", "INSTALLATION");
        body.addProperty("instancePublicKey", authorization.instancePublicKey());
        body.addProperty("leaseId", "01234567-89ab-cdef-0123-456789abcdef");
        body.addProperty("issuedAt", now);
        body.addProperty("expiresAt", now + RuntimeAuthorization.LEASE_MILLIS);
        var permissions = new JsonArray();
        permissions.add("market");
        body.add("moduleIds", permissions);
        var expiries = new JsonObject();
        expiries.addProperty("market", now + RuntimeAuthorization.LEASE_MILLIS);
        body.add("moduleExpiresAt", expiries);
        return body;
    }
    private String envelope(JsonObject body) throws Exception {
        byte[] raw = body.toString().getBytes(StandardCharsets.UTF_8);
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(issuer.getPrivate());
        signature.update((body.get("issuedAt").getAsString() + "\n" + RuntimeAuthorization.PURPOSE + "\n").getBytes(StandardCharsets.UTF_8));
        signature.update(raw);
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(raw) + "." + encoder.encodeToString(signature.sign());
    }
    private boolean verify(long at, byte[] raw, byte[] signed) {
        try {
            var signature = Signature.getInstance("Ed25519");
            signature.initVerify(issuer.getPublic());
            signature.update((at + "\n").getBytes(StandardCharsets.UTF_8));
            signature.update(raw);
            return signature.verify(signed);
        } catch (Exception failure) { return false; }
    }
    @Test void acceptsSignedPermissionsAndStopsAtTheMonotonicDeadline() throws Exception {
        long now = System.currentTimeMillis(), nanos = System.nanoTime();
        authorization.accept(envelope(claims(now)), "SERVER_A", "INSTALLATION", this::verify, now, nanos);
        assertTrue(authorization.valid(nanos));
        assertFalse(authorization.valid(nanos + RuntimeAuthorization.LEASE_MILLIS * 1_000_000));
        assertTrue(authorization.permits("market"));
        assertFalse(authorization.permits("mail"));
        authorization.invalidate();
        assertFalse(authorization.valid());
    }
    @Test void copyingTheInstallationDoesNotCopyTheBootPrivateKey() throws Exception {
        long now = System.currentTimeMillis();
        String envelope = envelope(claims(now));
        var otherBoot = new RuntimeAuthorization();
        assertThrows(IllegalArgumentException.class, () -> otherBoot.accept(envelope, "SERVER_A", "INSTALLATION",
            this::verify, now, System.nanoTime()));
        assertThrows(IllegalArgumentException.class, () -> authorization.accept(envelope, "SERVER_B", "INSTALLATION",
            this::verify, now, System.nanoTime()));
    }
    @Test void rejectsExpiredOrOverlongTicketsAndWrongSignatures() throws Exception {
        long now = System.currentTimeMillis();
        for (int adjustment : new int[]{0, 1_800_001}) {
            var body = claims(now);
            body.addProperty("expiresAt", now + adjustment);
            String encoded = envelope(body);
            assertThrows(IllegalArgumentException.class, () -> authorization.accept(encoded, "SERVER_A",
                "INSTALLATION", this::verify, now, System.nanoTime()));
        }
        String valid = envelope(claims(now));
        assertThrows(IllegalArgumentException.class, () -> authorization.accept(valid, "SERVER_A",
            "INSTALLATION", (at, bytes, signature) -> false, now, System.nanoTime()));
    }
    @Test void networkTransitAndClockSkewCannotExtendPrimaryOwnership() throws Exception {
        long now = System.currentTimeMillis(), start = System.nanoTime();
        authorization.accept(envelope(claims(now)), "SERVER_A", "INSTALLATION", this::verify,
            now - 30_000, start + 5_000_000_000L, start);
        assertTrue(authorization.valid(start + 179_000_000_000L));
        assertFalse(authorization.valid(start + 180_000_000_000L));
    }
    @Test void signsTheExactRequestedFileAndDisallowsUnlistedModules() throws Exception {
        long now = System.currentTimeMillis();
        authorization.accept(envelope(claims(now)), "SERVER_A", "INSTALLATION", this::verify);
        byte[] axb = new byte[]{1, 2, 3};
        var proof = authorization.keyProof("SERVER_A", "market", "release", axb);
        assertEquals(RuntimeAuthorization.checksum(axb), proof.checksum());
        assertThrows(IllegalStateException.class, () -> authorization.keyProof("SERVER_A", "mail", "release", axb));
        assertEquals(proof.checksum(), RuntimeAuthorization.normalizeChecksum(
            Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(axb))));
    }
    @Test void bindingCanonicalizationMatchesTheCloudProtocol() {
        assertEquals("ArcartXSuite/bind/v1\n123456789\nPUBLIC\nANCHOR\nFINGERPRINT\nTXkgU2VydmVy\n1800000000000\nrequest-id",
            RuntimeAuthorization.bindingMessage("123456789", "PUBLIC", "ANCHOR", "FINGERPRINT", "My Server", 1_800_000_000_000L, "request-id"));
    }
    @Test void customerPrivilegesRequireAMatchingSignedReleaseAndUnexpiredPaidSeat() throws Exception {
        String release = "2.0.0-local." + "a".repeat(40) + "-suite.0.0-ci.aaaaaaa";
        long now = System.currentTimeMillis(), nanos = System.nanoTime();
        var body = claims(now);
        authorization.accept(envelope(body), "SERVER_A", "INSTALLATION", this::verify, now, nanos);
        assertFalse(authorization.permitsCustomerFeatures(release, null, nanos));
        body.addProperty("customerRelease", release);
        body.getAsJsonObject("moduleExpiresAt").addProperty("market", now + 1_000L);
        authorization.accept(envelope(body), "SERVER_A", "INSTALLATION", this::verify, now, nanos);
        assertTrue(authorization.permitsCustomerFeatures(release, null, nanos));
        assertTrue(authorization.permitsCustomerFeatures(release, "market", nanos));
        assertFalse(authorization.permitsCustomerFeatures(release, "eventpacket", nanos));
        assertFalse(authorization.permitsCustomerFeatures(null, null, nanos));
        assertFalse(authorization.permitsCustomerFeatures(release.replace("2.0.0", "2.0.1"), null, nanos));
        assertFalse(authorization.permitsCustomerFeatures(release, null, nanos + 1_000_000_000L));
    }
    @Test void leaseProofRetainsLegacyShapeAndBindsCustomerReleaseWhenPresent() throws Exception {
        String release = "2.0.0-local." + "a".repeat(40) + "-suite.0.0-ci.aaaaaaa";
        String old = authorization.leaseRequestMessage("SERVER_A", "nonce", 1L, "request-id");
        assertEquals(old, authorization.leaseRequestMessage("SERVER_A", "nonce", 1L, "request-id", null));
        assertEquals(old + "\n" + release, authorization.leaseRequestMessage("SERVER_A", "nonce", 1L, "request-id", release));
        long now = System.currentTimeMillis();
        var body = claims(now);
        body.addProperty("customerRelease", release.replace("ci.aaaaaaa", "ci.bbbbbbb"));
        String invalid = envelope(body);
        assertThrows(IllegalArgumentException.class, () -> authorization.accept(invalid, "SERVER_A", "INSTALLATION", this::verify));
    }
    @Test void oneExpiringLicenseDoesNotExpireUnrelatedModules() throws Exception {
        long now = System.currentTimeMillis(), nanos = System.nanoTime();
        var body = claims(now);
        body.getAsJsonArray("moduleIds").add("chat");
        body.getAsJsonObject("moduleExpiresAt").addProperty("market", now + 1_000);
        body.getAsJsonObject("moduleExpiresAt").addProperty("chat", now + RuntimeAuthorization.LEASE_MILLIS);
        authorization.accept(envelope(body), "SERVER_A", "INSTALLATION", this::verify, now, nanos);
        assertEquals(java.util.Set.of("market", "chat"), authorization.permittedModules(nanos));
        assertEquals(java.util.Set.of("chat"), authorization.permittedModules(nanos + 1_000_000_000L));
    }
    @Test void acceptsTheTrustedStandbyIssuerAndKeepsReleaseAtThatAuthority() throws Exception {
        long now = System.currentTimeMillis();
        var body = claims(now);
        body.addProperty("issuer", "axs-standby");
        authorization.accept(envelope(body), "SERVER_A", "INSTALLATION", this::verify);
        assertEquals("axs-standby", authorization.releaseProof("SERVER_A").issuer());
        var first = authorization.keyProof("SERVER_A", "market", "release", new byte[]{1});
        var retry = authorization.keyProof("SERVER_A", "market", "release", new byte[]{1});
        assertNotEquals(first.requestId(), retry.requestId());
        assertNotEquals(first.signature(), retry.signature());
    }
}
