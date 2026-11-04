package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;

/** Registers authoritative roster snapshots for host-side admission. Server thread only. */
@PublicCapability
public interface AbyssExternalPartyAccess {
    record Roster(String partyId, UUID leader, List<UUID> members, String mode, int capacity,
                  long rosterRevision, long selectionRevision, boolean ready) {
        public Roster { members = List.copyOf(members); }
    }

    @FunctionalInterface interface Provider {
        /** Return null if the roster, selection, readiness, or entitlement cannot be verified. */
        Roster verify(AbyssAdmissionAccess.Intent intent);
    }

    AbyssAdmissionAccess.Registration register(String owner, Provider provider);
}
