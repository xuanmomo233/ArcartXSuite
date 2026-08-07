package xuanmo.arcartxsuite.cloud;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import xuanmo.arcartxsuite.security.NativeBridge;

/**
 * 云端响应签名验证器，基于 native Ed25519 校验响应签名，并防重放。
 * <p>
 * 通过时间戳新鲜度检查（5 分钟有效、60 秒时钟偏移容忍）与已见时间戳去重，
 * 防止重放攻击。依赖 {@link NativeBridge} 的 native 实现。
 */
final class ResponseSignatureVerifier {

    private static final long MAX_AGE_MS = 300_000L;
    private static final long MAX_FUTURE_SKEW_MS = 60_000L;

    private final Map<String, Long> lastSeenTimestamps = new ConcurrentHashMap<>();

    boolean isActive() {
        if (!NativeBridge.isAvailable()) {
            return false;
        }
        try {
            return NativeBridge.responseVerifyActive();
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    static boolean isFresh(long now, long timestamp) {
        if (timestamp <= now) {
            return now - timestamp <= MAX_AGE_MS;
        }
        return timestamp - now <= MAX_FUTURE_SKEW_MS;
    }

    boolean isReplay(String endpointKey, long timestamp) {
        Long lastSeen = lastSeenTimestamps.get(endpointKey);
        return lastSeen != null && timestamp < lastSeen;
    }

    void recordTimestamp(String endpointKey, long timestamp) {
        lastSeenTimestamps.merge(endpointKey, timestamp, Math::max);
    }

    boolean verify(long timestamp, byte[] body, byte[] signature) {
        try {
            return NativeBridge.verifyResponseSig(timestamp, body, signature);
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }
}
