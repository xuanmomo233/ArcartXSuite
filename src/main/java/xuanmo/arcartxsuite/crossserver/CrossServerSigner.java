package xuanmo.arcartxsuite.crossserver;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import xuanmo.arcartxsuite.api.crossserver.CrossServerEnvelope;

/**
 * 跨服信封 HMAC-SHA256 签名工具。
 * <p>
 * 签名载荷为信封的关键字段拼接；验证使用常量时间比较防止时序攻击。
 */
public final class CrossServerSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private CrossServerSigner() {
    }

    public static String sign(CrossServerEnvelope envelope, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            mac.update(buildSigningHeader(envelope).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(nullToEmpty(envelope.payload()).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }

    public static boolean verify(CrossServerEnvelope envelope, String signatureHex, byte[] secret) {
        if (signatureHex == null || signatureHex.length() != 64) {
            return false;
        }
        String expected = sign(envelope, secret);
        return constantTimeEquals(expected, signatureHex.toLowerCase(Locale.ROOT));
    }

    public static CrossServerEnvelope withSignature(CrossServerEnvelope unsigned, byte[] secret) {
        String signature = sign(unsigned, secret);
        return new CrossServerEnvelope(
            unsigned.protocol(),
            unsigned.module(),
            unsigned.nodeId(),
            unsigned.messageId(),
            unsigned.timestamp(),
            unsigned.payload(),
            signature
        );
    }

    private static String buildSigningHeader(CrossServerEnvelope envelope) {
        return "axs-cross-envelope-v1\n"
            + envelope.protocol() + '\n'
            + nullToEmpty(envelope.module()) + '\n'
            + nullToEmpty(envelope.nodeId()) + '\n'
            + nullToEmpty(envelope.messageId()) + '\n'
            + envelope.timestamp() + '\n';
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int index = 0; index < left.length(); index++) {
            diff |= left.charAt(index) ^ right.charAt(index);
        }
        return diff == 0;
    }
}
