package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Server-thread calls, asynchronous durable results. Only trusted server plugins may use this capability. */
@PublicCapability
public interface AbyssReviveAccess {
    enum State { AVAILABLE, RESERVED, COMMITTING, COMMITTED, RELEASED, EXHAUSTED, REJECTED, UNKNOWN, NOT_FOUND }
    record Status(UUID runId, UUID playerId, int maximum, int used, int remaining, long revision,
                  boolean member, State state, String code) { }
    record Receipt(String owner, UUID operationId, UUID runId, UUID playerId, State state,
                   String code, int remaining, long revision, long expiresAt) { }
    CompletionStage<Status> reviveStatus(UUID runId, UUID playerId);
    /** operationId must be globally unique within owner. Reservations last 30 seconds. */
    CompletionStage<Receipt> reserve(String owner, UUID operationId, UUID runId, UUID playerId,
                                     String reason, long expectedRevision);
    /** Abyss performs the actual resurrection. XC must not independently teleport or revive the target. */
    CompletionStage<Receipt> commit(String owner, UUID operationId);
    CompletionStage<Receipt> release(String owner, UUID operationId);
    /** Read/reconcile an existing operation. UNKNOWN requires review and must never trigger a new reservation. */
    CompletionStage<Receipt> query(String owner, UUID operationId);
}
