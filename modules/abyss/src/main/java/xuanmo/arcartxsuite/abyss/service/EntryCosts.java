package xuanmo.arcartxsuite.abyss.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Phase;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Registration;
import xuanmo.arcartxsuite.api.capability.AbyssEntryCostAccess;

/** Coordinates provider transitions without guessing the outcome of an uncertain call. */
final class EntryCosts implements AbyssEntryCostAccess {
    private final AbyssService service;
    private final Map<String, Participant> providers = new LinkedHashMap<>();

    EntryCosts(AbyssService service) { this.service = service; }

    @Override public Registration register(String owner, Participant participant) {
        requireMain();
        if (!service.running()) throw new IllegalStateException("Abyss unavailable");
        if (owner == null || !owner.matches("[a-z0-9_.:-]{1,96}")) throw new IllegalArgumentException("Invalid owner");
        if (providers.putIfAbsent(owner, Objects.requireNonNull(participant)) != null)
            throw new IllegalStateException("Cost owner already registered");
        return () -> {
            requireMain();
            providers.remove(owner, participant);
        };
    }

    @Override public CompletionStage<State> query(String owner, UUID transactionId) {
        requireMain();
        var provider = providers.get(owner);
        return provider == null ? CompletableFuture.completedFuture(State.UNKNOWN)
            : bounded(() -> provider.query(transactionId));
    }

    boolean available(String owner) { return providers.containsKey(owner); }
    boolean authorized(AdmissionAccess.Operation op) {
        return op.costId == null || (op.costState == State.COMMITTED
            && op.costParticipant != null && providers.get(op.costOwner) == op.costParticipant);
    }
    void clear() { providers.clear(); }

    void begin(AdmissionAccess.Operation op, Consumer<AdmissionAccess.Operation> changed, Runnable enter) {
        var provider = providers.get(op.costOwner);
        if (provider == null) {
            op.phase = Phase.REJECTED;
            op.reason = "COST_PROVIDER_UNAVAILABLE";
            changed.accept(op);
            return;
        }
        var transaction = new Transaction(op.costId, op.intent);
        op.costParticipant = provider;
        op.phase = Phase.RESERVING_COST;
        changed.accept(op);
        invoke(() -> provider.reserve(transaction), reserved -> {
            op.costState = reserved;
            if (reserved != State.RESERVED) {
                op.phase = reserved == State.NONE ? Phase.REJECTED : Phase.REVIEW_REQUIRED;
                op.reason = reserved == State.NONE ? "COST_REJECTED" : "COST_RESERVATION_UNKNOWN";
                changed.accept(op);
                return;
            }
            if (op.cancelRequested || providers.get(op.costOwner) != provider || !service.running()) {
                op.phase = Phase.CANCELLED;
                op.reason = "COST_CANCELLED";
                changed.accept(op);
                return;
            }
            op.phase = Phase.COMMITTING_COST;
            changed.accept(op);
            invoke(() -> provider.commit(transaction), committed -> {
                op.costState = committed;
                if (committed != State.COMMITTED) {
                    op.phase = committed == State.RESERVED ? Phase.REJECTED : Phase.REVIEW_REQUIRED;
                    op.reason = committed == State.RESERVED ? "COST_COMMIT_REJECTED" : "COST_COMMIT_UNKNOWN";
                    changed.accept(op);
                    return;
                }
                if (op.cancelRequested || providers.get(op.costOwner) != provider || !service.running()) {
                    op.phase = Phase.CANCELLED;
                    op.reason = "COST_CANCELLED";
                    changed.accept(op);
                    return;
                }
                op.phase = Phase.PREPARING;
                changed.accept(op);
                enter.run();
            });
        });
    }

    void compensate(AdmissionAccess.Operation op, Consumer<AdmissionAccess.Operation> persist) {
        if (op.compensationAttempted) return;
        if (op.costState != State.COMMITTED && op.costState != State.RESERVED) return;
        Phase terminal = op.phase;
        if (terminal != Phase.REJECTED && terminal != Phase.CANCELLED && terminal != Phase.COMPENSATED
            && terminal != Phase.REVIEW_REQUIRED) return;
        op.compensationAttempted = true;
        var provider = providers.get(op.costOwner);
        if (provider == null || provider != op.costParticipant || !service.running()) {
            op.phase = Phase.REVIEW_REQUIRED;
            op.reason = "COST_PROVIDER_UNAVAILABLE";
            op.costState = State.UNKNOWN;
            return;
        }
        boolean committed = op.costState == State.COMMITTED;
        var transaction = new Transaction(op.costId, op.intent);
        op.phase = Phase.COMPENSATING_COST;
        persist.accept(op);
        invoke(() -> committed ? provider.compensate(transaction) : provider.release(transaction), result -> {
            op.costState = result;
            if (result == (committed ? State.COMPENSATED : State.RELEASED)) {
                op.phase = terminal;
            } else {
                op.phase = Phase.REVIEW_REQUIRED;
                op.reason = "COST_COMPENSATION_UNKNOWN";
            }
            persist.accept(op);
        });
    }

    private void invoke(Supplier<CompletionStage<State>> call, Consumer<State> done) {
        bounded(call).thenAccept(state -> service.runOnMain(() -> done.accept(state)));
    }

    private static CompletableFuture<State> bounded(Supplier<CompletionStage<State>> call) {
        var future = new CompletableFuture<State>();
        try {
            call.get().whenComplete((state, error) -> future.complete(error == null && state != null ? state : State.UNKNOWN));
        } catch (RuntimeException failure) {
            future.complete(State.UNKNOWN);
        }
        return future.completeOnTimeout(State.UNKNOWN, 15, TimeUnit.SECONDS);
    }

    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call Abyss entry costs on the server thread");
    }
}
