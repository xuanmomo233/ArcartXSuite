package xuanmo.arcartxsuite.cloud;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import xuanmo.arcartxsuite.security.NativeBridge;

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
