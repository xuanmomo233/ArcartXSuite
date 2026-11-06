package xuanmo.arcartxsuite.api.auth;

import java.util.Set;
import java.util.UUID;
import static xuanmo.arcartxsuite.api.auth.AuthSnapshot.*;

/** State owned by the host, never by a UI. An operation ticket is invalidated by every new operation/revocation. */
public final class AuthSession {
    public record Operation(UUID connectionId, long generation) {}
    private final UUID playerId;
    private final java.util.function.LongSupplier clock;
    private final UUID connectionId = UUID.randomUUID();
    private UUID challengeId = UUID.randomUUID();
    private long generation;
    private UUID accountId;
    private Registration registration = Registration.UNKNOWN;
    private Stage stage = Stage.LOADING;
    private String provider = "";
    private String reason = "LOADING";
    private Set<Requirement> requirements = Set.of();
    private long eligibleNanos;
    private long admittedAt;
    private boolean eligibilityPaused;

    public AuthSession(UUID playerId) { this(playerId,System::nanoTime); }
    AuthSession(UUID playerId,java.util.function.LongSupplier clock) { this.playerId=playerId; this.clock=clock; }

    public synchronized Operation begin() {
        if (stage == Stage.CLOSED) throw new IllegalStateException("Connection closed");
        return new Operation(connectionId, ++generation);
    }
    public synchronized boolean current(Operation op) {
        return op != null && stage != Stage.CLOSED && connectionId.equals(op.connectionId()) && generation == op.generation();
    }
    public synchronized boolean loaded(Operation op, boolean registered) {
        if (!current(op) || stage != Stage.LOADING && stage != Stage.AWAITING_IDENTITY && stage != Stage.RESTRICTED) return false;
        registration = registered ? Registration.REGISTERED : Registration.UNREGISTERED;
        stage = Stage.AWAITING_IDENTITY;
        reason = "READY";
        return true;
    }
    public synchronized boolean verified(Operation op, UUID account, String method, Set<Requirement> pending) {
        if (!current(op) || stage == Stage.LOADING || stage == Stage.ADMITTED || account == null) return false;
        accountId = account;
        provider = method;
        registration = Registration.REGISTERED;
        requirements = Set.copyOf(pending);
        stage = Stage.AWAITING_REQUIREMENTS;
        reason = pending.isEmpty() ? "VERIFIED" : "REQUIREMENTS";
        return true;
    }
    public synchronized boolean satisfy(Requirement requirement) {
        if (stage != Stage.AWAITING_REQUIREMENTS || !requirements.contains(requirement)) return false;
        var remaining = new java.util.HashSet<>(requirements);
        remaining.remove(requirement);
        requirements = Set.copyOf(remaining);
        return true;
    }
    public synchronized boolean admit(Operation operation) {
        if (!current(operation) || stage != Stage.AWAITING_REQUIREMENTS || accountId == null || !requirements.isEmpty()) return false;
        stage = Stage.ADMITTED;
        reason = "ADMITTED";
        admittedAt = clock.getAsLong();
        eligibilityPaused = false;
        return true;
    }
    public synchronized long eligibleNanos() {
        return eligibleThrough(clock.getAsLong());
    }
    /** Read a lease-backed connection without crediting time past its authority deadline. */
    public synchronized long eligibleNanosUntil(long deadlineNanos) {
        long now=clock.getAsLong();
        return eligibleThrough(now-deadlineNanos>=0?deadlineNanos:now);
    }
    private long eligibleThrough(long end) {
        return eligibleNanos + (stage == Stage.ADMITTED && !eligibilityPaused ? Math.max(0,end-admittedAt) : 0);
    }
    /** Freeze accounting immediately while the host schedules the corresponding main-thread revocation. */
    public synchronized void pauseEligibility() {
        pauseEligibilityAt(clock.getAsLong());
    }
    /** Cap earned time at an expired authority lease, even if its expiry is observed later. */
    public synchronized void pauseEligibilityAt(long deadlineNanos) {
        if(stage==Stage.ADMITTED && !eligibilityPaused) {
            eligibleNanos=eligibleNanosUntil(deadlineNanos); eligibilityPaused=true;
        }
    }
    public synchronized void revoke() {
        if (stage == Stage.CLOSED) return;
        eligibleNanos = eligibleNanos();
        ++generation;
        challengeId = UUID.randomUUID();
        accountId = null;
        requirements = Set.of();
        stage = Stage.RESTRICTED;
        reason = "REVOKED";
    }
    public synchronized void close() { revoke(); stage = Stage.CLOSED; }
    public synchronized AuthSnapshot snapshot() {
        return new AuthSnapshot(playerId, connectionId, generation, accountId, registration, stage, provider, requirements, reason);
    }
    public synchronized void note(Operation operation,String reason) { if(current(operation)) this.reason=reason; }
    public synchronized UUID challengeId() { return challengeId; }
}
