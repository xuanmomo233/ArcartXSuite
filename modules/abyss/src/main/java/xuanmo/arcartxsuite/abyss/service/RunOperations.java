package xuanmo.arcartxsuite.abyss.service;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.abyss.instance.LeaveReason;
import xuanmo.arcartxsuite.api.capability.AbyssRunAccess;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import static xuanmo.arcartxsuite.api.capability.AbyssRunAccess.*;

/** LEAVE saves its immutable request before touching the live world. */
final class RunOperations {
    private record Stored(Operation request, OperationReceipt receipt) { }
    private final AbyssService service;
    private final RunLedger ledger;
    private final LinkedHashMap<String, CompletableFuture<OperationReceipt>> inFlight = new LinkedHashMap<>();
    private final LinkedHashMap<String, Operation> requests = new LinkedHashMap<>();
    private final LinkedHashMap<String, Stored> recent = new LinkedHashMap<>();
    RunOperations(AbyssService service, RunLedger ledger) { this.service = service; this.ledger = ledger; }

    private static String key(String owner, UUID run, UUID operation) { return "leave/" + owner + "/" + run + "/" + operation; }
    private static boolean valid(Operation request) {
        return request != null && request.runId() != null && request.operationId() != null && request.playerId() != null
            && request.owner() != null && request.owner().matches("[a-z0-9_.:-]{1,96}") && request.action() == Action.LEAVE;
    }
    private OperationReceipt receipt(Operation request, OperationState state, String code, long revision) {
        return new OperationReceipt(request.operationId(), request.runId(), request.owner(), state, code,
            revision, System.currentTimeMillis());
    }
    OperationResult begin(Operation request) {
        if (!valid(request)) return new OperationResult(false, "INVALID_REQUEST", 0);
        var previous = recent.get(key(request.owner(), request.runId(), request.operationId()));
        if (previous != null && !previous.request().equals(request)) return new OperationResult(false, "OPERATION_CONFLICT", 0);
        if (previous != null && previous.receipt().state() != OperationState.ACCEPTED
                && previous.receipt().state() != OperationState.UNKNOWN)
            return new OperationResult(previous.receipt().state() == OperationState.COMPLETED,
                previous.receipt().code(), previous.receipt().revision());
        execute(request).exceptionally(error -> {
            service.logRunResultFailure(request.runId(), error);
            return null;
        });
        return new OperationResult(false, "IN_PROGRESS", 0);
    }

    CompletableFuture<OperationReceipt> execute(Operation request) {
        RunLedger.requireMain();
        if (!valid(request)) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid LEAVE"));
        String key = key(request.owner(), request.runId(), request.operationId());
        var cached = recent.get(key);
        if (cached != null && !cached.request().equals(request))
            return CompletableFuture.completedFuture(receipt(request, OperationState.REJECTED, "OPERATION_CONFLICT", 0));
        if (inFlight.containsKey(key)) {
            if (!request.equals(requests.get(key)))
                return CompletableFuture.completedFuture(receipt(request, OperationState.REJECTED, "OPERATION_CONFLICT", 0));
            return inFlight.get(key);
        }
        if (inFlight.size() >= 1024) return CompletableFuture.failedFuture(new IllegalStateException("OPERATION_CAPACITY"));
        var result = new CompletableFuture<OperationReceipt>();
        inFlight.put(key, result);
        requests.put(key, request);
        ledger.database.submitDatabase(() -> ledger.repository.runDocument(key)).whenComplete((raw, error) -> {
            if (error != null) { complete(request, result, null, error); return; }
            service.runOnMain(() -> {
                Stored previous = raw == null ? null : RunLedger.JSON.fromJson(raw, Stored.class);
                if (previous != null && !previous.request().equals(request)) {
                    complete(request, result, receipt(request, OperationState.REJECTED, "OPERATION_CONFLICT", 0), null);
                    return;
                }
                if (previous != null && previous.receipt().state() != OperationState.ACCEPTED
                        && previous.receipt().state() != OperationState.UNKNOWN) {
                    complete(request, result, previous.receipt(), null);
                    return;
                }
                var run = service.instances().getInstance(request.runId());
                if (previous != null && (run == null || !run.hasPlayer(request.playerId()))) {
                    query(request.owner(), request.runId(), request.operationId())
                        .whenComplete((value, failure) -> complete(request, result, value, failure));
                    return;
                }
                String rejected = authorize(request, previous == null);
                if (rejected != null) {
                    var value = receipt(request, OperationState.REJECTED, rejected, revision(request.runId()));
                    if (run == null) { complete(request, result, value, null); return; }
                    persist(request, value, previous == null).whenComplete((ignored, failure) -> complete(request, result, value, failure));
                    return;
                }
                var accepted = receipt(request, OperationState.ACCEPTED, "IN_PROGRESS", revision(request.runId()));
                persist(request, accepted, previous == null).whenComplete((ignored, failure) -> {
                    if (failure != null) { complete(request, result, null, failure); return; }
                    service.runOnMain(() -> apply(request, result));
                });
            });
        });
        return result;
    }

    private String authorize(Operation request, boolean checkRevision) {
        if (!service.running()) return "UNAVAILABLE";
        var run = service.instances().getInstance(request.runId());
        if (run == null || run.designMode() || !run.admitted()) return "UNKNOWN_RUN";
        var admission = service.admissionOperation(run);
        if (!Objects.equals(request.owner(), run.getTemplate().requirements().externalControlOwner())
                || admission == null || !request.owner().equals(admission.intent.owner())) return "NOT_OWNER";
        if (checkRevision && revision(request.runId()) != request.expectedRevision()) return "STALE_REVISION";
        return run.hasPlayer(request.playerId()) ? null : "NOT_A_MEMBER";
    }

    private long revision(UUID run) {
        var snapshot = service.runAccess().snapshot(run);
        return snapshot == null ? 0 : snapshot.revision();
    }

    private void apply(Operation request, CompletableFuture<OperationReceipt> result) {
        OperationReceipt value;
        String invalid = authorize(request, false);
        if (invalid != null) {
            query(request.owner(), request.runId(), request.operationId())
                .whenComplete((receipt, error) -> complete(request, result, receipt, error));
            return;
        }
        try {
            var player = Bukkit.getPlayer(request.playerId());
            boolean returned = player != null && player.isOnline() && service.playerExit(
                service.instances().getInstance(request.runId()), player, LeaveReason.QUIT);
            value = receipt(request, returned ? OperationState.COMPLETED : OperationState.ACCEPTED,
                returned ? "APPLIED" : "RETURN_PENDING", revision(request.runId()));
        } catch (RuntimeException failure) {
            value = receipt(request, OperationState.UNKNOWN, "OUTCOME_UNKNOWN", revision(request.runId()));
        }
        var finalValue = value;
        persist(request, value, false).whenComplete((ignored, error) -> complete(request, result, finalValue, error));
    }

    private CompletableFuture<Void> persist(Operation request, OperationReceipt value, boolean insert) {
        return ledger.database.submitDatabase(() -> ledger.repository.runTransaction(request.runId(), documents -> {
            String key = key(request.owner(), request.runId(), request.operationId());
            String raw = RunLedger.JSON.toJson(new Stored(request, value));
            if (insert) documents.insert(key, request.playerId(), raw);
            else documents.write(key, request.playerId(), raw);
            return null;
        }));
    }

    CompletableFuture<OperationReceipt> query(String owner, UUID run, UUID operation) {
        RunLedger.requireMain();
        if (owner == null || !owner.matches("[a-z0-9_.:-]{1,96}") || run == null || operation == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid operation query"));
        var result = new CompletableFuture<OperationReceipt>();
        ledger.database.submitDatabase(() -> ledger.repository.runDocument(key(owner, run, operation))).whenComplete((raw, error) -> {
            if (error != null) { result.completeExceptionally(error); return; }
            service.runOnMain(() -> {
                if (raw == null) {
                    result.complete(new OperationReceipt(operation, run, owner, OperationState.NOT_FOUND, "NOT_FOUND", 0, System.currentTimeMillis()));
                    return;
                }
                var stored = RunLedger.JSON.fromJson(raw, Stored.class);
                if (stored.receipt().state() == OperationState.COMPLETED || stored.receipt().state() == OperationState.REJECTED) {
                    result.complete(stored.receipt()); return;
                }
                var live = service.instances().getInstance(run);
                if (live != null) {
                    if (live.hasPlayer(stored.request().playerId())) { result.complete(stored.receipt()); return; }
                    reconcile(stored, result);
                } else {
                    service.runResultAccess().result(run).whenComplete((facts, failure) -> {
                        if (failure != null) { result.completeExceptionally(failure); return; }
                        boolean left = facts != null && facts.roster().stream().anyMatch(member ->
                            member.player().equals(stored.request().playerId()) && member.state() == AbyssRunResultAccess.MemberState.LEFT);
                        if (left) reconcile(stored, result);
                        else result.complete(receipt(stored.request(), OperationState.UNKNOWN, "OUTCOME_UNKNOWN", stored.receipt().revision()));
                    });
                }
            });
        });
        return result.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void reconcile(Stored stored, CompletableFuture<OperationReceipt> result) {
        var value = receipt(stored.request(), OperationState.COMPLETED, "APPLIED", stored.receipt().revision());
        persist(stored.request(), value, false).whenComplete((ignored, failure) -> {
            if (failure == null) result.complete(value); else result.completeExceptionally(failure);
        });
    }

    private void complete(Operation request, CompletableFuture<OperationReceipt> result, OperationReceipt value, Throwable error) {
        service.runOnMain(() -> {
            String key = key(request.owner(), request.runId(), request.operationId());
            inFlight.remove(key);
            requests.remove(key);
            if (error == null) {
                if (!value.code().equals("OPERATION_CONFLICT")) recent.put(key, new Stored(request, value));
                if (recent.size() > 32768) recent.remove(recent.keySet().iterator().next());
                result.complete(value);
            } else result.completeExceptionally(error);
        });
    }
}
