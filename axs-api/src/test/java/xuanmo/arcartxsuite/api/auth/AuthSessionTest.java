package xuanmo.arcartxsuite.api.auth;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthSessionTest {
    @Test void lateLeaseExpiryObservationCannotEarnTimeAfterAuthorityExpired() {
        var time=new java.util.concurrent.atomic.AtomicLong(100);
        var session=new AuthSession(UUID.randomUUID(),time::get);
        var operation=session.begin(); session.loaded(operation,true);
        session.verified(operation,UUID.randomUUID(),"local-password",Set.of()); session.admit(operation);
        time.set(200); session.pauseEligibilityAt(150);
        assertEquals(50,session.eligibleNanos());
        time.set(300); session.revoke();
        assertEquals(50,session.eligibleNanos());
    }
    @Test void credentialVerificationDoesNotAdmitBeforeCommit() {
        var session = new AuthSession(UUID.randomUUID());
        var op = session.begin(); session.loaded(op, false);
        assertTrue(session.verified(op, UUID.randomUUID(), "local-password", Set.of()));
        assertTrue(session.snapshot().identityVerified());
        assertFalse(session.snapshot().admitted());
        assertTrue(session.admit(op));
        assertFalse(session.admit(op));
    }
    @Test void missingRequirementBlocksAdmission() {
        var session = new AuthSession(UUID.randomUUID());
        var op = session.begin(); session.loaded(op, true);
        session.verified(op, UUID.randomUUID(), "verified-handshake", Set.of(AuthSnapshot.Requirement.QQ_BINDING));
        assertFalse(session.admit(op));
        assertTrue(session.satisfy(AuthSnapshot.Requirement.QQ_BINDING));
        assertTrue(session.admit(op));
    }
    @Test void revokedOperationCannotRestoreIdentity() {
        var session = new AuthSession(UUID.randomUUID());
        var challenge=session.challengeId(); var connection=session.snapshot().connectionId();
        var op = session.begin(); session.loaded(op, true); session.revoke();
        assertFalse(session.verified(op, UUID.randomUUID(), "local-password", Set.of()));
        assertFalse(session.admit(op));
        assertNotEquals(challenge,session.challengeId());
        assertEquals(connection,session.snapshot().connectionId());
    }
    @Test void reconnectAndClosedConnectionRejectOldOperations() {
        UUID player = UUID.randomUUID();
        var first = new AuthSession(player); var old = first.begin(); first.close();
        var second = new AuthSession(player);
        assertFalse(first.current(old)); assertFalse(second.current(old));
        assertThrows(IllegalStateException.class, first::begin);
        first.revoke();
        assertEquals(AuthSnapshot.Stage.CLOSED,first.snapshot().stage());
        assertThrows(IllegalStateException.class,first::begin);
    }
    @Test void unknownRegistrationCannotBeUsedToCreateAccount() {
        var session = new AuthSession(UUID.randomUUID()); var op = session.begin();
        assertEquals(AuthSnapshot.Registration.UNKNOWN, session.snapshot().registration());
        assertFalse(session.verified(op, UUID.randomUUID(), "local-password", Set.of()));
    }
    @Test void eligibleTimeExcludesWaitingAndPreservesPartialIntervalsAcrossRevocation() {
        var time=new java.util.concurrent.atomic.AtomicLong();
        var session=new AuthSession(UUID.randomUUID(),time::get);
        var op=session.begin(); session.loaded(op,true);
        time.addAndGet(120_000_000_000L); assertEquals(0,session.eligibleNanos());
        session.verified(op,UUID.randomUUID(),"local-password",Set.of()); session.admit(op);
        time.addAndGet(40_000_000_000L); session.pauseEligibility();
        time.addAndGet(10_000_000_000L); session.revoke();
        time.addAndGet(120_000_000_000L); assertEquals(40_000_000_000L,session.eligibleNanos());
        op=session.begin(); session.loaded(op,true);
        session.verified(op,UUID.randomUUID(),"local-password",Set.of()); session.admit(op);
        time.addAndGet(20_000_000_000L); assertEquals(60_000_000_000L,session.eligibleNanos());
        session.close(); time.addAndGet(120_000_000_000L); assertEquals(60_000_000_000L,session.eligibleNanos());
    }
}
