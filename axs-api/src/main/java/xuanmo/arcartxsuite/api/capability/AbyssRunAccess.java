package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;

/** Live run operations and snapshots for trusted server plugins. Call on the server thread. */
@PublicCapability
public interface AbyssRunAccess {
    String ENTITY_TOPIC = "axs.abyss.entity_notice";
    String OBJECTIVES_TOPIC = "axs.abyss.objectives_changed";
    record Objective(String kind, String id, String state, int progress, int total) { }
    record Snapshot(UUID runId, long revision, String state, List<Objective> objectives) {
        public Snapshot { objectives = List.copyOf(objectives); }
    }
    record Entity(UUID id, UUID runId, UUID worldId, String groupId, String mobId,
                  String source, String encounterId, String role, String stageAtSpawn) { }
    enum EntityChange { SPAWNED, DIED, REMOVED, VANISHED }
    record EntityNotice(int schemaVersion, UUID eventId, Entity entity, EntityChange change, long occurredAt) { }

    enum Action { LEAVE, CANCEL, FAIL, COMPLETE, SET_VARIABLE }
    record Operation(UUID operationId, UUID runId, long expectedRevision, String owner,
                     Action action, UUID playerId, String key, String value) { }
    record OperationResult(boolean applied, String code, long revision) { }
    enum OperationState { ACCEPTED, COMPLETED, REJECTED, UNKNOWN, NOT_FOUND }
    record OperationReceipt(UUID operationId, UUID runId, String owner, OperationState state,
                            String code, long revision, long updatedAt) { }
    interface ObjectiveProvider {
        List<Objective> objectives(UUID runId);
        default boolean canComplete(UUID runId, List<Objective> current) { return false; }
    }

    Snapshot snapshot(UUID runId);
    List<Entity> entities(UUID runId);
    Entity entity(UUID entityId);
    List<UUID> audience(UUID runId);
    OperationResult execute(Operation operation);
    /**
     * Persistent LEAVE receipt. Call on the server thread, completion may occur on a database thread.
     * Reuse the complete original operation on retry. Storage failures complete exceptionally.
     */
    default java.util.concurrent.CompletionStage<OperationReceipt> executeAsync(Operation operation) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
    /** Server-thread call. Retained receipts survive reloads and run cleanup. No side effects. */
    default java.util.concurrent.CompletionStage<OperationReceipt> queryOperation(String owner, UUID runId, UUID operationId) {
        return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
    AbyssAdmissionAccess.Registration registerObjectives(String owner, ObjectiveProvider provider);
}
