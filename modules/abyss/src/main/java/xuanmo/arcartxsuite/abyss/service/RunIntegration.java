package xuanmo.arcartxsuite.abyss.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.abyss.instance.FailReason;
import xuanmo.arcartxsuite.abyss.instance.LeaveReason;
import xuanmo.arcartxsuite.api.capability.AbyssRunAccess;

final class RunIntegration implements AbyssRunAccess {
    private record Observation(String state, List<UUID> members, List<Objective> objectives) { }
    private record Key(UUID run, UUID operation) { }
    private record Receipt(Operation request, OperationResult result, long expiresAt) { }
    private static final class Revision {
        long number;
        Observation observation;
    }
    private final AbyssService service;
    private final Map<UUID, Revision> revisions = new LinkedHashMap<>();
    private final Map<Key, Receipt> receipts = new LinkedHashMap<>();
    private final Map<String, ObjectiveProvider> providers = new LinkedHashMap<>();
    private boolean readingProvider;

    RunIntegration(AbyssService service) { this.service = service; }

    @Override public Snapshot snapshot(UUID runId) {
        requireMain();
        if (readingProvider) return null;
        var run = active(runId);
        return run == null ? null : observe(run);
    }

    @Override public List<Entity> entities(UUID runId) {
        requireMain();
        return active(runId) == null ? List.of() : service.mobGroups().entityContexts(runId);
    }

    @Override public Entity entity(UUID entityId) {
        requireMain();
        if (!service.running()) return null;
        var entity = service.mobGroups().entityContext(entityId);
        return entity != null && active(entity.runId()) != null ? entity : null;
    }

    @Override public List<UUID> audience(UUID runId) {
        requireMain();
        var run = active(runId);
        return run == null ? List.of() : run.getPlayers().stream().filter(player -> player.getWorld() == run.getWorld())
            .map(org.bukkit.entity.Player::getUniqueId).sorted().toList();
    }

    @Override public OperationResult execute(Operation request) {
        requireMain();
        if (readingProvider) return denied("CALLBACK_REENTRANCY", 0);
        if (request == null || request.operationId() == null || request.runId() == null || request.action() == null)
            return denied("INVALID_REQUEST", 0);
        long now = System.currentTimeMillis();
        receipts.values().removeIf(receipt -> receipt.expiresAt() <= now);
        Key key = new Key(request.runId(), request.operationId());
        var previous = receipts.get(key);
        if (previous != null) return previous.request().equals(request) ? previous.result() : denied("OPERATION_CONFLICT", 0);
        if (receipts.size() >= 32768) return denied("OPERATION_CAPACITY", 0);
        var run = active(request.runId());
        if (run == null) return denied("UNKNOWN_RUN", 0);
        var operation = service.admissionOperation(run);
        var owner = run.getTemplate().requirements().externalControlOwner();
        if (owner == null || operation == null || !owner.equals(request.owner()) || !owner.equals(operation.intent.owner()))
            return denied("NOT_OWNER", 0);
        var snapshot = observe(run);
        if (snapshot.revision() != request.expectedRevision()) return denied("STALE_REVISION", snapshot.revision());
        if (run.getStatus() != AbyssStatus.ACTIVE) return denied("NOT_ACTIVE", snapshot.revision());
        var pending = new Receipt(request, denied("IN_PROGRESS", snapshot.revision()), now + 300000L);
        receipts.put(key, pending);
        OperationResult result;
        try {
            String error = apply(request, run);
            if (error == null) {
                var revision = revisions.get(request.runId());
                long next = snapshot.revision() + 1;
                if (revision != null) {
                    observe(run);
                    revision.number = Math.max(next, revision.number);
                    next = revision.number;
                }
                result = new OperationResult(true, "APPLIED", next);
            } else result = denied(error, snapshot.revision());
        } catch (RuntimeException failure) {
            result = denied("OUTCOME_UNKNOWN", snapshot.revision());
        }
        receipts.put(key, new Receipt(request, result, now + 300000L));
        return result;
    }

    private String apply(Operation request, AbyssInstance run) {
        return switch (request.action()) {
            case CANCEL -> { service.cancelDungeon(run); yield null; }
            case FAIL -> { service.failDungeon(run, FailReason.SCRIPTED); yield null; }
            case COMPLETE -> {
                var provider = providers.get(request.owner());
                if (provider == null) yield "OBJECTIVE_PROVIDER_UNAVAILABLE";
                var current = observe(run);
                if (current.revision() != request.expectedRevision()) yield "STALE_REVISION";
                if (current.objectives().stream().anyMatch(objective -> objective.state().equals("UNAVAILABLE")))
                    yield "OBJECTIVE_PROVIDER_UNAVAILABLE";
                boolean allowed;
                readingProvider = true;
                try { allowed = provider.canComplete(run.getInstanceId(), current.objectives()); }
                finally { readingProvider = false; }
                if (!allowed || run.getStatus() != AbyssStatus.ACTIVE) yield "OBJECTIVES_INCOMPLETE";
                service.completeDungeon(run);
                yield run.getStatus() == AbyssStatus.COMPLETED ? null : "COMPLETION_REJECTED";
            }
            case LEAVE -> {
                var player = request.playerId() == null ? null : Bukkit.getPlayer(request.playerId());
                if (player == null || !player.isOnline() || !run.hasPlayer(player.getUniqueId())) yield "NOT_A_MEMBER";
                yield service.playerExit(run, player, LeaveReason.ADMIN) ? null : "RETURN_PENDING";
            }
            case SET_VARIABLE -> {
                if (request.key() == null || !request.key().matches("[a-zA-Z0-9_.-]{1,64}")
                    || request.value() == null || request.value().length() > 1024
                    || request.value().contains("{") || request.value().contains("}")) yield "INVALID_VARIABLE";
                String key = "external:" + request.owner() + ":" + request.key();
                run.getVariables().put(key, request.value());
                yield null;
            }
        };
    }

    private Snapshot observe(AbyssInstance run) {
        var objectives = new ArrayList<>(service.mobGroups().groupObjectives(run.getInstanceId()));
        var stages = service.stages();
        String stage = stages == null ? null : stages.currentStage(run);
        if (stage != null) objectives.add(new Objective("stage", stage, "ACTIVE", 0, 1));
        var rooms = service.roomManager();
        var room = rooms == null ? null : rooms.stateOf(run);
        if (room != null) for (var floor : room.floors) for (var entry : floor.rooms) {
            objectives.add(new Objective("room", entry.id, entry.state.name(), entry.state.name().equals("CLEARED") ? 1 : 0, 1));
        }
        var procgen = service.procgen();
        if (procgen != null && run.getTemplate().procgen() != null) {
            int[] progress = procgen.progress(run);
            objectives.add(new Objective("procgen", "rooms", "PROGRESS", progress[0], progress[1]));
        }
        var puzzles = service.puzzles();
        if (puzzles != null && run.getTemplate().puzzles() != null) {
            run.getTemplate().puzzles().puzzles().keySet().stream().sorted().forEach(id -> objectives.add(
                new Objective("puzzle", id, puzzles.isDone(run, id) ? "COMPLETED" : "PENDING",
                    puzzles.progress(run, id), -1)));
        }
        var admission = service.admissionOperation(run);
        var owner = admission == null ? null : admission.intent.owner();
        var provider = owner == null ? null : providers.get(owner);
        if (provider != null && !readingProvider) {
            readingProvider = true;
            try {
                var supplied = List.copyOf(provider.objectives(run.getInstanceId()));
                if (supplied.size() > 256) throw new IllegalArgumentException("Too many external objectives");
                supplied.stream().sorted(java.util.Comparator.comparing(Objective::id)).forEach(objective ->
                    objectives.add(new Objective("external:" + owner, objective.id(), objective.state(),
                        objective.progress(), objective.total())));
            } catch (RuntimeException failure) {
                objectives.add(new Objective("external:" + owner, "provider", "UNAVAILABLE", 0, -1));
            } finally {
                readingProvider = false;
            }
        }
        var state = new Observation(run.getStatus().name(), run.getPlayerUUIDs().stream().sorted().toList(), List.copyOf(objectives));
        var revision = revisions.computeIfAbsent(run.getInstanceId(), ignored -> new Revision());
        if (!state.equals(revision.observation)) {
            revision.observation = state;
            revision.number++;
        }
        return new Snapshot(run.getInstanceId(), revision.number, state.state(), state.objectives());
    }

    private AbyssInstance active(UUID runId) {
        if (!service.running()) return null;
        var run = service.instances().getInstance(runId);
        return run == null || run.designMode() || !run.admitted() ? null : run;
    }

    @Override public xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Registration registerObjectives(
            String owner, ObjectiveProvider provider) {
        requireMain();
        if (!service.running() || readingProvider) throw new IllegalStateException("Run objectives unavailable");
        if (owner == null || !owner.matches("[a-z0-9_.:-]{1,96}")) throw new IllegalArgumentException("Invalid owner");
        if (providers.putIfAbsent(owner, java.util.Objects.requireNonNull(provider)) != null)
            throw new IllegalStateException("Objective provider already registered");
        return () -> {
            requireMain();
            if (readingProvider) throw new IllegalStateException("Cannot unregister during verification");
            providers.remove(owner, provider);
        };
    }

    void tick(AbyssInstance instance) {
        var revision = revisions.get(instance.getInstanceId());
        if (revision == null || !instance.admitted() || instance.designMode()) return;
        long before = revision.number;
        var snapshot = observe(instance);
        if (snapshot.revision() != before) service.publishObjectiveNotice(snapshot);
    }

    void shutdown() { providers.clear(); }
    void detached(UUID runId) { revisions.remove(runId); }
    private static OperationResult denied(String code, long revision) { return new OperationResult(false, code, revision); }
    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call Abyss run access on the server thread");
    }
}
