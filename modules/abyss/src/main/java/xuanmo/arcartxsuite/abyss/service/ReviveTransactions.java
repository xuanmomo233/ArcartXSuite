package xuanmo.arcartxsuite.abyss.service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.instance.AbyssStatus;
import xuanmo.arcartxsuite.api.capability.AbyssReviveAccess;

final class ReviveTransactions implements AbyssReviveAccess {
    static final class Lease {
        String owner;
        UUID operation;
        UUID run;
        UUID player;
        String reason;
        long expected;
        int death;
        State state;
        String code;
        long expires;
        UUID epoch;
        UUID rescuer;
        boolean nativeRequest;
    }
    private final AbyssService service;
    private final RunLedger ledger;
    private final UUID epoch = UUID.randomUUID();
    private final java.util.Set<String> executions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    ReviveTransactions(AbyssService service, RunLedger ledger) { this.service = service; this.ledger = ledger; }

    private static String key(String owner, UUID operation) { return "revive/" + owner + "/" + operation; }
    private static void validate(String owner, UUID operation) {
        RunLedger.requireMain();
        if (owner == null || !owner.matches("[a-z0-9_.:-]{1,96}") || operation == null)
            throw new IllegalArgumentException("Invalid revive operation");
    }
    private AbyssInstance live(UUID run) {
        return service.running() ? service.instances().getInstance(run) : null;
    }
    private String eligibility(AbyssInstance run, UUID player, String owner, UUID rescuer) {
        if (run == null || run.designMode() || !run.admitted()) return "UNKNOWN_RUN";
        if (rescuer == null && (!Objects.equals(owner, run.getTemplate().requirements().externalControlOwner())
                || service.admissionOperation(run) == null
                || !Objects.equals(owner, service.admissionOperation(run).intent.owner()))) return "NOT_OWNER";
        if (rescuer != null) {
            String denied = service.nativeRuns().canRescue(run, rescuer, player);
            if (denied != null) return denied;
        }
        if (run.getStatus() != AbyssStatus.ACTIVE) return "NOT_ACTIVE";
        Player target = Bukkit.getPlayer(player);
        if (!run.hasPlayer(player) || target == null || !target.isOnline() || target.getWorld() != run.getWorld())
            return "NOT_A_MEMBER";
        return run.isPlayerCurrentlyDead(target) ? null : "NOT_DEAD";
    }

    @Override public CompletableFuture<Status> reviveStatus(UUID run, UUID player) {
        RunLedger.requireMain();
        Objects.requireNonNull(run); Objects.requireNonNull(player);
        var instance = live(run);
        boolean member = instance != null && instance.hasPlayer(player);
        return ledger.database.submitDatabase(() -> {
            String raw = ledger.repository.runDocument("run/" + run);
            if (raw == null) return new Status(run, player, 0, 0, 0, 0, false, State.NOT_FOUND, "UNKNOWN_RUN");
            var data = RunLedger.JSON.fromJson(raw, RunLedger.Data.class);
            if (!data.roster.contains(player)) return new Status(run, player, 0, 0, 0, 0, false, State.REJECTED, "NOT_A_MEMBER");
            int remaining = remaining(data, player);
            return new Status(run, player, data.reviveLimit, data.used.getOrDefault(player, 0), remaining,
                data.revision, member, member && data.state.equals("ACTIVE")
                    ? remaining > 0 ? State.AVAILABLE : State.EXHAUSTED : State.REJECTED,
                member && data.state.equals("ACTIVE") ? "OK" : "NOT_ACTIVE");
        });
    }

    @Override public CompletableFuture<Receipt> reserve(String owner, UUID operation, UUID run, UUID player,
                                                       String reason, long expectedRevision) {
        if (RunOperations.NATIVE_OWNER.equals(owner)) return CompletableFuture.failedFuture(new IllegalArgumentException("Reserved owner"));
        return reserve(owner, operation, run, player, reason, expectedRevision, null);
    }

    CompletableFuture<Receipt> reserveNative(UUID operation, UUID run, UUID rescuer, UUID player, long revision) {
        return reserve(RunOperations.NATIVE_OWNER, operation, run, player, "native-rescue", revision, rescuer);
    }

    CompletableFuture<UUID> lastNative(UUID run, UUID rescuer) {
        return ledger.database.submitDatabase(() -> {
            String value = ledger.repository.runDocument("native-rescue/" + run + "/" + rescuer);
            return value == null ? null : UUID.fromString(value);
        });
    }

    private CompletableFuture<Receipt> reserve(String owner, UUID operation, UUID run, UUID player,
                                               String reason, long expectedRevision, UUID rescuer) {
        validate(owner, operation);
        Objects.requireNonNull(run); Objects.requireNonNull(player);
        if (reason == null || reason.length() > 256) throw new IllegalArgumentException("Invalid reason");
        var instance = live(run);
        String invalid = eligibility(instance, player, owner, rescuer);
        int death = instance == null ? -1 : instance.getDeaths(player);
        return ledger.database.submitDatabase(() -> {
            String previous = ledger.repository.runDocument(key(owner, operation));
            if (previous != null) {
                var lease = RunLedger.JSON.fromJson(previous, Lease.class);
                if (!lease.run.equals(run) || !lease.player.equals(player) || !lease.reason.equals(reason)
                        || lease.expected != expectedRevision || !Objects.equals(lease.rescuer, rescuer))
                    return rejected(owner, operation, run, player, "OPERATION_CONFLICT");
                return refresh(lease);
            }
            if (invalid != null) return rejected(owner, operation, run, player, invalid);
            return ledger.repository.runTransaction(run, documents -> {
                var data = RunLedger.read(documents, run);
                var lease = new Lease();
                lease.owner = owner; lease.operation = operation; lease.run = run; lease.player = player;
                lease.reason = reason; lease.expected = expectedRevision; lease.death = death;
                lease.epoch = epoch;
                lease.rescuer = rescuer;
                lease.nativeRequest = rescuer != null;
                if (data.leases.size() >= 10000) throw new IllegalStateException("REVIVE_OPERATION_CAPACITY");
                String denied = !data.state.equals("ACTIVE") ? "NOT_ACTIVE"
                    : data.departed.contains(player) ? "NOT_A_MEMBER"
                    : data.revision != expectedRevision ? "STALE_REVISION"
                    : data.leases.values().stream().anyMatch(existing -> existing.player.equals(player) && holding(existing))
                        ? "TARGET_RESERVED" : remaining(data, player) <= 0 ? "EXHAUSTED" : null;
                lease.state = denied == null ? State.RESERVED : denied.equals("EXHAUSTED") ? State.EXHAUSTED : State.REJECTED;
                lease.code = denied == null ? "OK" : denied;
                lease.expires = System.currentTimeMillis() + 30000;
                documents.insert(key(owner, operation), player, RunLedger.JSON.toJson(lease));
                if (rescuer != null && lease.state == State.RESERVED)
                    documents.write("native-rescue/" + run + "/" + rescuer, rescuer, operation.toString());
                data.leases.put(key(owner, operation), lease);
                RunLedger.save(documents, data);
                return receipt(lease, data);
            });
        });
    }

    @Override public CompletableFuture<Receipt> query(String owner, UUID operation) {
        validate(owner, operation);
        return ledger.database.submitDatabase(() -> {
            String raw = ledger.repository.runDocument(key(owner, operation));
            return raw == null ? rejected(owner, operation, null, null, "NOT_FOUND")
                : refresh(RunLedger.JSON.fromJson(raw, Lease.class));
        });
    }

    private Receipt refresh(Lease lease) throws Exception {
        return ledger.repository.runTransaction(lease.run, documents -> {
            var data = RunLedger.read(documents, lease.run);
            var current = data.leases.get(key(lease.owner, lease.operation));
            if (current.state == State.COMMITTING
                    && (!epoch.equals(current.epoch) || !executions.contains(key(current.owner, current.operation)))) {
                current.state = State.UNKNOWN;
                current.code = epoch.equals(current.epoch) ? "COMMIT_INTERRUPTED" : "RESTART_DURING_COMMIT";
                store(documents, data, current);
            }
            if (current.state == State.RESERVED && (current.expires <= System.currentTimeMillis()
                    || !data.state.equals("ACTIVE") || data.departed.contains(current.player))) {
                current.state = State.RELEASED;
                current.code = "EXPIRED_OR_ENDED";
                store(documents, data, current);
            }
            return receipt(current, data);
        });
    }

    @Override public CompletableFuture<Receipt> release(String owner, UUID operation) {
        validate(owner, operation);
        return ledger.database.submitDatabase(() -> {
            String raw = ledger.repository.runDocument(key(owner, operation));
            if (raw == null) return rejected(owner, operation, null, null, "NOT_FOUND");
            var lease = RunLedger.JSON.fromJson(raw, Lease.class);
            return ledger.repository.runTransaction(lease.run, documents -> {
                var data = RunLedger.read(documents, lease.run);
                var current = data.leases.get(key(owner, operation));
                if (current.state == State.RESERVED) {
                    current.state = State.RELEASED; current.code = "RELEASED";
                    store(documents, data, current);
                }
                return receipt(current, data);
            });
        });
    }

    @Override public CompletableFuture<Receipt> commit(String owner, UUID operation) {
        validate(owner, operation);
        String execution = key(owner, operation);
        if (!executions.add(execution)) return query(owner, operation);
        var result = new CompletableFuture<Receipt>();
        result.whenComplete((ignored, failure) -> executions.remove(execution));
        ledger.database.submitDatabase(() -> {
            String raw = ledger.repository.runDocument(key(owner, operation));
            if (raw == null) return null;
            var lease = RunLedger.JSON.fromJson(raw, Lease.class);
            return ledger.repository.runTransaction(lease.run, documents -> {
                var data = RunLedger.read(documents, lease.run);
                var current = data.leases.get(key(owner, operation));
                if (current.state != State.RESERVED) return null;
                if (current.expires <= System.currentTimeMillis() || !data.state.equals("ACTIVE")) {
                    current.state = State.RELEASED; current.code = "EXPIRED_OR_ENDED";
                    store(documents, data, current);
                    return null;
                }
                current.state = State.COMMITTING; current.code = "IN_PROGRESS";
                current.epoch = epoch;
                store(documents, data, current);
                return current;
            });
        }).whenComplete((lease, error) -> {
            if (error != null) { result.completeExceptionally(error); return; }
            service.runOnMain(() -> {
                if (lease == null) {
                    query(owner, operation).whenComplete((receipt, failure) -> finish(result, receipt, failure));
                    return;
                }
                var run = live(lease.run);
                String invalid = eligibility(run, lease.player, owner, lease.nativeRequest ? lease.rescuer : null);
                if (invalid == null && lease.nativeRequest && !service.nativeRuns().canCommit(lease.operation, lease.rescuer))
                    invalid = "RESCUE_INTERRUPTED";
                State outcome = State.RELEASED;
                String code = invalid;
                if (invalid == null && run.getDeaths(lease.player) != lease.death) code = "DEATH_CHANGED";
                if (invalid == null && run.getDeaths(lease.player) == lease.death) {
                    try {
                        boolean revived = service.tryRevivePlayer(run, Bukkit.getPlayer(lease.player));
                        outcome = revived ? State.COMMITTED : State.RELEASED;
                        code = revived ? "OK" : "REVIVE_FAILED";
                    } catch (RuntimeException failure) { outcome = State.UNKNOWN; code = "OUTCOME_UNKNOWN"; }
                }
                State finalState = outcome;
                String finalCode = code;
                ledger.database.submitDatabase(() -> ledger.repository.runTransaction(lease.run, documents -> {
                    var data = RunLedger.read(documents, lease.run);
                    var current = data.leases.get(key(owner, operation));
                    if (current.state == State.COMMITTING) {
                        current.state = finalState; current.code = finalCode;
                        if (finalState == State.COMMITTED) data.used.merge(lease.player, 1, Integer::sum);
                        store(documents, data, current);
                    }
                    return receipt(current, data);
                })).whenComplete((receipt, failure) -> finish(result, receipt, failure));
            });
        });
        return result.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static <T> void finish(CompletableFuture<T> future, T value, Throwable error) {
        if (error == null) future.complete(value); else future.completeExceptionally(error);
    }
    private static boolean holding(Lease lease) {
        return lease.state == State.COMMITTING || lease.state == State.UNKNOWN
            || lease.state == State.RESERVED && lease.expires > System.currentTimeMillis();
    }
    static int remaining(RunLedger.Data data, UUID player) {
        long held = data.leases.values().stream().filter(lease -> lease.player.equals(player) && holding(lease)).count();
        return (int)Math.max(0, data.reviveLimit - data.used.getOrDefault(player, 0) - held);
    }
    private static Receipt receipt(Lease lease, RunLedger.Data data) {
        return new Receipt(lease.owner, lease.operation, lease.run, lease.player, lease.state, lease.code,
            remaining(data, lease.player), data.revision, lease.expires);
    }
    private static Receipt rejected(String owner, UUID operation, UUID run, UUID player, String code) {
        return new Receipt(owner, operation, run, player, code.equals("NOT_FOUND") ? State.NOT_FOUND : State.REJECTED,
            code, 0, 0, 0);
    }
    private static void store(xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.RunDocuments documents,
                              RunLedger.Data data, Lease lease) throws Exception {
        RunLedger.save(documents, data);
        documents.write(key(lease.owner, lease.operation), lease.player, RunLedger.JSON.toJson(lease));
    }
}
