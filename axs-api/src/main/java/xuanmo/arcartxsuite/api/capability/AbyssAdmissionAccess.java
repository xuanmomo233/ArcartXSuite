package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Abyss integration contract. Call on the server thread. Policies must be bounded and read-only. */
@PublicCapability
public interface AbyssAdmissionAccess {
    record IntegrationInfo(int apiVersion, UUID generation, boolean available, String reason) { }
    record Dungeon(String id, String name, String description, String difficultyLabel,
                   int minimumPlayers, int maximumPlayers, boolean partyRequired,
                   String partyMode, int capacity, List<String> plans, String host) {
        public Dungeon { plans = List.copyOf(plans); }
    }
    record Intent(String owner, UUID requestId, UUID actor, List<UUID> members,
                  String dungeon, String plan, String difficulty, String rulesVersion) {
        public Intent { members = List.copyOf(members); }
    }
    record Denial(UUID member, String code, String message) { }
    record Preflight(UUID token, long expiresAt, List<Denial> denials) {
        public Preflight { denials = List.copyOf(denials); }
        public boolean allowed() { return token != null && denials.isEmpty(); }
    }
    enum Phase { PREPARING, TELEPORTING, STARTED, REJECTED, CANCELLED, COMPENSATED, REVIEW_REQUIRED, CONFLICT }
    record Entry(Phase phase, UUID runId, String reason, Map<UUID, String> members) {
        public Entry { members = Map.copyOf(members); }
    }
    record PlayerState(boolean available, UUID runId, String state) { }
    record Run(UUID id, String dungeon, String plan, String difficulty, String rulesVersion,
               String state, List<UUID> roster, List<UUID> members, UUID world) {
        public Run { roster = List.copyOf(roster); members = List.copyOf(members); }
    }
    @FunctionalInterface interface Policy {
        List<Denial> check(Intent intent);
    }
    interface Registration extends AutoCloseable { @Override void close(); }

    IntegrationInfo integrationInfo();
    List<Dungeon> catalog();
    PlayerState playerState(UUID player);
    Run run(UUID runId);
    Preflight preflight(Intent intent);
    Entry requestEntry(Intent intent, UUID token);
    Entry entryStatus(String owner, UUID requestId);
    Entry cancelEntry(String owner, UUID requestId);
    Registration registerPolicy(String owner, int priority, Policy policy);
}
