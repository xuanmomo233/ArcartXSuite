package xuanmo.arcartxsuite.cloud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ResponseSignatureVerifierTest {

    @Test
    void freshnessWindowAcceptsRecentAndRejectsStaleOrFutureResponses() {
        long now = 1_000_000L;

        assertTrue(ResponseSignatureVerifier.isFresh(now, now));
        assertTrue(ResponseSignatureVerifier.isFresh(now, now - 299_999L));
        assertFalse(ResponseSignatureVerifier.isFresh(now, now - 300_001L));
        assertTrue(ResponseSignatureVerifier.isFresh(now, now + 59_999L));
        assertFalse(ResponseSignatureVerifier.isFresh(now, now + 60_001L));
    }

    @Test
    void monotonicReplayTrackingRejectsTimestampRollback() {
        ResponseSignatureVerifier verifier = new ResponseSignatureVerifier();

        assertFalse(verifier.isReplay("refresh", 2_000L));
        verifier.recordTimestamp("refresh", 2_000L);
        assertFalse(verifier.isReplay("refresh", 2_000L));
        assertTrue(verifier.isReplay("refresh", 1_999L));
        assertFalse(verifier.isReplay("refresh", 2_001L));
        verifier.recordTimestamp("refresh", 2_001L);
        assertTrue(verifier.isReplay("refresh", 2_000L));
    }
}
