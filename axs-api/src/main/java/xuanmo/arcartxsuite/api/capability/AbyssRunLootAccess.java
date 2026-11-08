package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Read-only, server-thread entry, database-thread completion. Results never execute or claim rewards. */
@PublicCapability
public interface AbyssRunLootAccess {
    enum Scope { PERSONAL, SHARED }
    enum State { PENDING, HOST_ACCEPTED, DELIVERED, UNKNOWN }
    record LootEntry(String entryId, String rewardId, String displayName, double quantity, String quality,
                     String source, UUID ownerId, Scope scope, State state, long updatedAt) { }
    record LootSnapshot(UUID runId, long revision, String runState, List<LootEntry> entries,
                        String nextCursor, boolean complete, String code) {
        public LootSnapshot { entries = List.copyOf(entries); }
    }
    /**
     * Authenticated member viewer only. Maximum 100 entries. Null cursor starts a page.
     * Mutation between pages returns STALE_CURSOR. Storage failure completes exceptionally.
     * A null quality means the item provider supplied no quality metadata.
     */
    CompletionStage<LootSnapshot> snapshot(UUID runId, UUID viewerId, String cursor, int limit);
}
