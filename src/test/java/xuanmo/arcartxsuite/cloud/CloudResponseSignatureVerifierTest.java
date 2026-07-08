package xuanmo.arcartxsuite.cloud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class CloudResponseSignatureVerifierTest {

    @Test
    void validSignaturePasses() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String body = "{\"ok\":true,\"serverCode\":\"ABC\",\"token\":\"abc123\"}";
        String ts = String.valueOf(System.currentTimeMillis());

        CloudResponseSignatureVerifier verifier = new CloudResponseSignatureVerifier(
            List.of(base64PublicKey(keyPair)),
            Logger.getLogger("test")
        );

        CloudResponseSignatureVerifier.VerificationResult result = verifier.verifyResponseSignature(
            body,
            sign(keyPair.getPrivate(), ts, body),
            ts
        );

        assertTrue(result.enforced());
        assertTrue(result.accepted());
        assertNull(result.errorCode());
    }

    @Test
    void tamperedBodyFails() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String body = "{\"ok\":true,\"serverCode\":\"ABC\",\"token\":\"abc123\"}";
        String tampered = "{\"ok\":false,\"serverCode\":\"ABC\",\"token\":\"abc123\"}";
        String ts = String.valueOf(System.currentTimeMillis());

        CloudResponseSignatureVerifier verifier = new CloudResponseSignatureVerifier(
            List.of(base64PublicKey(keyPair)),
            Logger.getLogger("test")
        );

        CloudResponseSignatureVerifier.VerificationResult result = verifier.verifyResponseSignature(
            tampered,
            sign(keyPair.getPrivate(), ts, body),
            ts
        );

        assertTrue(result.enforced());
        assertFalse(result.accepted());
    }

    @Test
    void wrongKeyFails() throws Exception {
        KeyPair signingKey = generateKeyPair();
        KeyPair pinnedKey = generateKeyPair();
        String body = "{\"ok\":true,\"serverCode\":\"ABC\",\"token\":\"abc123\"}";
        String ts = String.valueOf(System.currentTimeMillis());

        CloudResponseSignatureVerifier verifier = new CloudResponseSignatureVerifier(
            List.of(base64PublicKey(pinnedKey)),
            Logger.getLogger("test")
        );

        CloudResponseSignatureVerifier.VerificationResult result = verifier.verifyResponseSignature(
            body,
            sign(signingKey.getPrivate(), ts, body),
            ts
        );

        assertTrue(result.enforced());
        assertFalse(result.accepted());
    }

    @Test
    void staleTimestampFails() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String body = "{\"ok\":true,\"serverCode\":\"ABC\",\"token\":\"abc123\"}";
        String ts = String.valueOf(System.currentTimeMillis() - 10 * 60_000L);

        CloudResponseSignatureVerifier verifier = new CloudResponseSignatureVerifier(
            List.of(base64PublicKey(keyPair)),
            Logger.getLogger("test")
        );

        CloudResponseSignatureVerifier.VerificationResult result = verifier.verifyResponseSignature(
            body,
            sign(keyPair.getPrivate(), ts, body),
            ts
        );

        assertTrue(result.enforced());
        assertFalse(result.accepted());
    }

    @Test
    void emptyPinnedKeyListDisablesVerification() throws Exception {
        CloudResponseSignatureVerifier verifier = new CloudResponseSignatureVerifier(List.of(), Logger.getLogger("test"));

        CloudResponseSignatureVerifier.VerificationResult result = verifier.verifyResponseSignature(
            "{\"ok\":true}",
            null,
            null
        );

        assertFalse(result.enforced());
        assertTrue(result.accepted());
        assertNull(result.errorCode());
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
        return keyPairGenerator.generateKeyPair();
    }

    private static String sign(PrivateKey privateKey, String ts, String body) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update((ts + "\n" + body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private static String base64PublicKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
