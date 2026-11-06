package xuanmo.arcartxsuite.api.auth;

import java.util.Set;
import java.util.UUID;

/** Immutable, IO-free view of one connection. Identity verification is distinct from admission. */
public record AuthSnapshot(UUID playerId, UUID connectionId, long generation, UUID accountId,
                           Registration registration, Stage stage, String provider,
                           Set<Requirement> requirements, String reason) {
    public enum Registration { UNKNOWN, REGISTERED, UNREGISTERED }
    public enum Stage { ABSENT, LOADING, AWAITING_IDENTITY, AWAITING_REQUIREMENTS, ADMITTED, RESTRICTED, CLOSED }
    public enum Requirement { TERMS, QQ_BINDING }

    public AuthSnapshot {
        requirements = Set.copyOf(requirements);
    }

    public boolean admitted() { return stage == Stage.ADMITTED; }
    public boolean identityVerified() { return accountId != null && (admitted() || stage == Stage.AWAITING_REQUIREMENTS); }

    public static AuthSnapshot absent(UUID playerId) {
        return new AuthSnapshot(playerId, null, 0, null, Registration.UNKNOWN, Stage.ABSENT, "", Set.of(), "UNAVAILABLE");
    }
}
