package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent;

/**
 * External entry-cost participant. The provider is authoritative for the whole roster
 * and must journal operations idempotently using the transaction ID.
 * Invocation is on the server thread; slow work must complete asynchronously.
 */
@PublicCapability
public interface AbyssEntryCostAccess {
    enum State { NONE, RESERVED, COMMITTED, RELEASED, COMPENSATED, UNKNOWN }
    record Transaction(UUID id, Intent intent) { }
    interface Participant {
        CompletionStage<State> reserve(Transaction transaction);
        CompletionStage<State> commit(Transaction transaction);
        CompletionStage<State> release(Transaction transaction);
        CompletionStage<State> compensate(Transaction transaction);
        CompletionStage<State> query(UUID transactionId);
    }

    AbyssAdmissionAccess.Registration register(String owner, Participant participant);
    CompletionStage<State> query(String owner, UUID transactionId);
}
