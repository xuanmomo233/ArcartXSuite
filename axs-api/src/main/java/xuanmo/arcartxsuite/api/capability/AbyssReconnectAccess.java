package xuanmo.arcartxsuite.api.capability;

import java.util.UUID;

/** Opt-in same-process return to the original run. Its gameplay timer continues during disconnect. */
@PublicCapability
public interface AbyssReconnectAccess {
    record Status(UUID runId, long deadline, boolean eligible, String code) { }
    Status reconnectStatus(UUID player);
    Status requestReconnect(UUID player, UUID runId);
}
