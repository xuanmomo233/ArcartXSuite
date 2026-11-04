package xuanmo.arcartxsuite.api.capability;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Durable run facts. The consumer owns eligibility decisions and its reward ledger. */
@PublicCapability
public interface AbyssRunResultAccess {
    String RESULT_COMMITTED_TOPIC = "axs.abyss.result_committed";
    String RUN_NOTICE_TOPIC = "axs.abyss.run_notice";
    String ENTRY_NOTICE_TOPIC = "axs.abyss.entry_notice";

    enum Outcome { COMPLETED, FAILED, CANCELLED }
    enum MemberState { PRESENT, DISCONNECTED, LEFT }
    record Member(UUID player, MemberState state, int deaths, String leaveReason, long lastChangedAt) { }
    record Result(UUID resultId, UUID runId, UUID requestId, String owner, String dungeon,
                  String plan, String difficulty, String rulesVersion, UUID leader,
                  long admittedAt, long startedAt, long endedAt, Outcome outcome,
                  String reason, List<Member> roster) {
        public Result { roster = List.copyOf(roster); }
    }
    record Cursor(long endedAt, UUID runId) { }
    record ResultCommitted(int schemaVersion, UUID eventId, UUID runId, UUID requestId,
                           long occurredAt) { }
    enum NoticeKind { ADMITTED, STARTED, DISCONNECTED, LEFT, DIED }
    /** Server-thread notification. Sequence is scoped to a run; notices are not replayed. */
    record RunNotice(int schemaVersion, UUID eventId, UUID runId, UUID requestId, long sequence,
                     long occurredAt, NoticeKind kind, Member member) { }
    /** Published after the corresponding admission snapshot has been stored. */
    record EntryNotice(int schemaVersion, UUID eventId, UUID generation, String owner, UUID requestId,
                       long sequence, long occurredAt, AbyssAdmissionAccess.Entry entry) { }

    /** Completes with null if there is no committed result; storage failure completes exceptionally. */
    CompletableFuture<Result> result(UUID runId);
    /** History pagination, not a commit-order change feed. Ascending (endedAt, runId), max 100. */
    CompletableFuture<List<Result>> results(String owner, Cursor after, int limit);
}
