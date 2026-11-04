package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abyss integration contract for trusted server-side plugins.
 * Call on the server thread. Policies must be bounded and read-only.
 * Requests are journaled on the host server before any admission side effects.
 * The first revision does not forward API requests to another server.
 * A request remains deduplicated while its receipt is retained. An interrupted
 * admission is reported as REVIEW_REQUIRED and is never automatically replayed.
 * Receipts are not automatically expired; the host rejects new API requests
 * when its retained journal reaches capacity.
 */
@PublicCapability
public interface AbyssAdmissionAccess {
    record IntegrationInfo(int apiVersion, UUID generation, long catalogRevision, boolean available, String reason,
                           Map<String, String> features) {
        public IntegrationInfo { features = Map.copyOf(features); }
    }
    record Plan(String id, String name, List<String> lore) {
        public Plan { lore = List.copyOf(lore); }
    }
    record Dungeon(String id, String name, String description, String difficultyLabel,
                   int minimumPlayers, int maximumPlayers, boolean partyRequired,
                   String partyMode, int capacity, List<Plan> plans, String host,
                   long cooldownSeconds, double moneyCost, String currency, int staminaCost) {
        public Dungeon { plans = List.copyOf(plans); }
    }
    record Intent(String owner, UUID requestId, UUID actor, List<UUID> members,
                  String dungeon, String plan, String difficulty, String rulesVersion,
                  String externalPartyId, long rosterRevision, long selectionRevision) {
        public Intent { members = List.copyOf(members); }
        public Intent(String owner, UUID requestId, UUID actor, List<UUID> members,
                      String dungeon, String plan, String difficulty, String rulesVersion) {
            this(owner, requestId, actor, members, dungeon, plan, difficulty, rulesVersion, null, 0, 0);
        }
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
    record PlayerState(boolean available, UUID requestId, UUID runId, String state) { }
    record Run(UUID id, String dungeon, String plan, String difficulty, String rulesVersion,
               String state, List<UUID> roster, List<UUID> members, UUID world,
               UUID requestId, UUID leader, String externalPartyId,
               long createdAt, long startedAt, Map<UUID, String> memberStates) {
        public Run {
            roster = List.copyOf(roster);
            members = List.copyOf(members);
            memberStates = Map.copyOf(memberStates);
        }
    }
    @FunctionalInterface interface Policy {
        List<Denial> check(Intent intent);
    }
    interface Registration extends AutoCloseable { @Override void close(); }

    IntegrationInfo integrationInfo();
    List<Dungeon> catalog();
    PlayerState playerState(UUID player);
    Run run(UUID runId);
    List<Run> activeRuns();
    Preflight preflight(Intent intent);
    Entry requestEntry(Intent intent, UUID token);
    /** Returns null for an unknown request. */
    Entry entryStatus(String owner, UUID requestId);
    /** Cancels only before teleport begins; otherwise returns the existing state. */
    Entry cancelEntry(String owner, UUID requestId);
    Registration registerPolicy(String owner, int priority, Policy policy);
    /** Optional Bukkit owner binding. Disabled owners no longer satisfy required policies. */
    Registration registerPolicy(org.bukkit.plugin.Plugin plugin, String owner, int priority, Policy policy);
}
