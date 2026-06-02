package xuanmo.arcartxsuite.license;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class LicenseVerifier {

    public static final String PUBLIC_KEY_SPki_B64 = "MCowBQYDK2VwAyEAqlW71cP0jU3d8knd7rVmi2SXJVzpsAhwlsGAWtpKLb8=";

    private static final String TICKET_PROTOCOL = "AXS-TICKET-v1";
    private final Gson gson = new Gson();
    private final PublicKey publicKey;

    public LicenseVerifier() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(PUBLIC_KEY_SPki_B64);
            this.publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalStateException("无法加载授权公钥", exception);
        }
    }

    public LicenseTicket verifyTicket(String ticket, String expectedInstallId, String expectedFingerprintHash, long now) {
        return verifyTicket(ticket, "", expectedInstallId, expectedFingerprintHash, now);
    }

    public LicenseTicket verifyTicket(String ticket, String expectedQq, String expectedInstallId, String expectedFingerprintHash, long now) {
        return verifyTicket(ticket, expectedQq, expectedInstallId, expectedFingerprintHash, "", now, false);
    }

    private LicenseTicket verifyTicket(
        String ticket,
        String expectedQq,
        String expectedInstallId,
        String expectedFingerprintHash,
        String expectedLocalSaltHash,
        long now,
        boolean allowLocalSaltCacheFallback
    ) {
        if (ticket == null || ticket.isBlank()) {
            throw new LicenseVerificationException("ticket 为空");
        }
        String[] parts = ticket.split("\\.");
        if (parts.length != 4) {
            throw new LicenseVerificationException("ticket 格式无效");
        }
        if (!TICKET_PROTOCOL.equals(parts[0])) {
            throw new LicenseVerificationException("ticket 协议不匹配: " + parts[0]);
        }

        String signingInput = parts[0] + "." + parts[1] + "." + parts[2];
        if (!verifySignature(signingInput, parts[3])) {
            throw new LicenseVerificationException("ticket 签名无效");
        }

        JsonObject payload = gson.fromJson(new String(base64UrlDecode(parts[2]), StandardCharsets.UTF_8), JsonObject.class);
        LicenseTicket parsed = LicenseTicket.fromPayload(ticket, payload);
        if (!expectedInstallId.equals(parsed.installId())) {
            throw new LicenseVerificationException("installId 不匹配");
        }
        if (!expectedFingerprintHash.equals(parsed.fingerprintHash())) {
            boolean localSaltMatches = allowLocalSaltCacheFallback
                && expectedLocalSaltHash != null
                && !expectedLocalSaltHash.isBlank()
                && expectedLocalSaltHash.equals(parsed.localSaltHash());
            if (!localSaltMatches) {
                throw new LicenseVerificationException("fingerprintHash 不匹配");
            }
        }
        if (expectedQq != null && !expectedQq.isBlank() && !expectedQq.equals(parsed.ownerQq())) {
            throw new LicenseVerificationException("QQ 不匹配");
        }
        if (expectedQq != null && !expectedQq.isBlank() && !parsed.subjectId().isBlank()) {
            String expectedSubject = "qq:" + expectedQq + ":install:" + expectedInstallId;
            if (!expectedSubject.equals(parsed.subjectId())) {
                throw new LicenseVerificationException("subjectId 不匹配");
            }
        }
        if (parsed.notBefore() > 0 && now < parsed.notBefore()) {
            throw new LicenseVerificationException("ticket 尚未生效");
        }
        if (parsed.hardExpireAt() > 0 && now >= parsed.hardExpireAt()) {
            throw new LicenseVerificationException("ticket 已硬过期");
        }
        return parsed;
    }

    public LicenseDecision decisionFor(String ticket, String expectedInstallId, String expectedFingerprintHash, long now) {
        return decisionFor(ticket, "", expectedInstallId, expectedFingerprintHash, now);
    }

    public LicenseDecision decisionFor(String ticket, String expectedQq, String expectedInstallId, String expectedFingerprintHash, long now) {
        LicenseTicket parsed = verifyTicket(ticket, expectedQq, expectedInstallId, expectedFingerprintHash, now);
        return decisionForParsed(parsed, now, "");
    }

    public LicenseDecision decisionForCached(
        String ticket,
        String expectedQq,
        String expectedInstallId,
        String expectedFingerprintHash,
        String expectedLocalSaltHash,
        long now
    ) {
        LicenseTicket parsed = verifyTicket(
            ticket,
            expectedQq,
            expectedInstallId,
            expectedFingerprintHash,
            expectedLocalSaltHash,
            now,
            true
        );
        String suffix = expectedFingerprintHash.equals(parsed.fingerprintHash())
            ? ""
            : "（本地缓存机器指纹变化，已按 localSalt 离线兼容）";
        return decisionForParsed(parsed, now, suffix);
    }

    private LicenseDecision decisionForParsed(LicenseTicket parsed, long now, String reasonSuffix) {
        String suffix = reasonSuffix == null ? "" : reasonSuffix;
        if (now < parsed.expiresAt()) {
            return new LicenseDecision(LicenseDecision.State.VALID, "在线票据有效" + suffix, parsed.modules(), parsed);
        }
        if (now < parsed.offlineGraceUntil()) {
            return new LicenseDecision(LicenseDecision.State.GRACE, "票据过期，处于离线宽限期" + suffix, parsed.modules(), parsed);
        }
        if (now < parsed.hardExpireAt()) {
            return new LicenseDecision(LicenseDecision.State.EMERGENCY_GRACE, "票据处于应急宽限期" + suffix, parsed.modules(), parsed);
        }
        return LicenseDecision.disabled("ticket 已超过 hardExpireAt");
    }

    public boolean verifyTimeSignature(String kid, String nonce, long serverTime, String signature) {
        return verifySignature("AXS-TIME-v1." + kid + "." + nonce + "." + serverTime, signature);
    }

    private boolean verifySignature(String signingInput, String signatureBase64Url) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(publicKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.verify(base64UrlDecode(signatureBase64Url));
        } catch (Exception exception) {
            return false;
        }
    }

    static byte[] base64UrlDecode(String input) {
        String normalized = input.replace('-', '+').replace('_', '/');
        int padding = (4 - normalized.length() % 4) % 4;
        return Base64.getDecoder().decode(normalized + "=".repeat(padding));
    }

    static String base64UrlEncode(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    public static final class LicenseVerificationException extends RuntimeException {
        public LicenseVerificationException(String message) {
            super(message);
        }
    }
}
