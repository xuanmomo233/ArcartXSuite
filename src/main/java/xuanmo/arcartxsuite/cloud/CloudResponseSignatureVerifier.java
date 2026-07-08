package xuanmo.arcartxsuite.cloud;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public final class CloudResponseSignatureVerifier {

    public static final long DEFAULT_MAX_CLOCK_SKEW_MS = 5 * 60_000L;

    private final Logger logger;
    private final boolean enabled;
    private final List<PublicKey> publicKeys;
    private final long maxClockSkewMs;

    public record VerificationResult(boolean enforced, boolean accepted, String errorCode, String message) {}

    public CloudResponseSignatureVerifier(List<String> publicKeyBase64List, Logger logger) {
        this(publicKeyBase64List, DEFAULT_MAX_CLOCK_SKEW_MS, logger);
    }

    public CloudResponseSignatureVerifier(List<String> publicKeyBase64List, long maxClockSkewMs, Logger logger) {
        this.logger = logger;
        this.enabled = publicKeyBase64List != null && !publicKeyBase64List.isEmpty();
        this.maxClockSkewMs = Math.max(0L, maxClockSkewMs);

        List<PublicKey> keys = new ArrayList<>();
        if (publicKeyBase64List != null) {
            for (String base64 : publicKeyBase64List) {
                if (base64 == null || base64.isBlank()) {
                    continue;
                }
                try {
                    byte[] decoded = Base64.getDecoder().decode(base64.trim());
                    KeyFactory kf = KeyFactory.getInstance("Ed25519");
                    keys.add(kf.generatePublic(new java.security.spec.X509EncodedKeySpec(decoded)));
                } catch (Exception e) {
                    if (logger != null) {
                        logger.warning("[Cloud] Invalid response signature public key: " + e.getMessage());
                    }
                }
            }
        }

        this.publicKeys = Collections.unmodifiableList(keys);

        if (this.enabled && this.publicKeys.isEmpty() && logger != null) {
            logger.warning("[Cloud] response-signature-public-keys is configured, but no usable Ed25519 public key could be loaded; signed responses will be rejected until fixed");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public VerificationResult verifyResponseSignature(String rawBody, String signatureB64, String timestampHeader) {
        if (!enabled) {
            return new VerificationResult(false, true, null, "response signature verification disabled");
        }

        if (publicKeys.isEmpty()) {
            return failure("SIGNATURE_VERIFICATION_ERROR", "no usable response signature public keys configured");
        }

        if (rawBody == null) {
            return failure("SIGNATURE_VERIFICATION_ERROR", "missing raw response body");
        }
        if (signatureB64 == null || signatureB64.isBlank()) {
            return failure("SIGNATURE_VERIFICATION_ERROR", "missing X-AXS-Signature");
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return failure("SIGNATURE_VERIFICATION_ERROR", "missing X-AXS-Signature-Ts");
        }

        final long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return failure("SIGNATURE_VERIFICATION_ERROR", "invalid X-AXS-Signature-Ts");
        }

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > maxClockSkewMs) {
            return failure("SIGNATURE_VERIFICATION_FAILED", "stale X-AXS-Signature-Ts");
        }

        final byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureB64.trim());
        } catch (IllegalArgumentException e) {
            return failure("SIGNATURE_VERIFICATION_ERROR", "invalid X-AXS-Signature");
        }

        byte[] message = (timestampHeader.trim() + "\n" + rawBody).getBytes(StandardCharsets.UTF_8);
        for (PublicKey publicKey : publicKeys) {
            try {
                Signature sig = Signature.getInstance("Ed25519");
                sig.initVerify(publicKey);
                sig.update(message);
                if (sig.verify(signatureBytes)) {
                    return new VerificationResult(true, true, null, null);
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("[Cloud] Response signature verification error: " + e.getMessage());
                }
            }
        }

        return failure("SIGNATURE_VERIFICATION_FAILED", "response signature did not match any pinned key");
    }

    private static VerificationResult failure(String errorCode, String message) {
        return new VerificationResult(true, false, errorCode, message);
    }
}
