package xuanmo.arcartxsuite.abyss.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;

/** Keeps the immutable admission roster separately from the live membership map. */
final class RunResults implements AbyssRunResultAccess {
    private record Live(AdmissionAccess.Operation operation, long admittedAt, Map<UUID, Member> members,
                        java.util.concurrent.atomic.AtomicLong sequence) { }
    private final AbyssService service;
    private final Map<UUID, Live> live = new LinkedHashMap<>();
    private volatile JdbcAbyssRepository repository;
    private volatile PlayerDataManager database;
    private Path outbox;
    private long nextReplay;

    RunResults(AbyssService service) { this.service = service; }

    void configure(JdbcAbyssRepository repository, PlayerDataManager database) {
        configure(repository, database, null);
    }

    void configure(JdbcAbyssRepository repository, PlayerDataManager database, Path outbox) {
        this.repository = repository;
        this.database = database;
        this.outbox = outbox;
    }

    void admitted(AbyssInstance instance, AdmissionAccess.Operation operation) {
        if (operation == null || instance.designMode()) return;
        long now = System.currentTimeMillis();
        var members = new LinkedHashMap<UUID, Member>();
        for (UUID id : operation.intent.members()) members.put(id, new Member(id, MemberState.PRESENT, 0, null, now));
        var facts = new Live(operation, now, members, new java.util.concurrent.atomic.AtomicLong());
        if (live.putIfAbsent(instance.getInstanceId(), facts) == null) notice(instance, facts, NoticeKind.ADMITTED, null);
    }

    void started(AbyssInstance instance) {
        var facts = live.get(instance.getInstanceId());
        if (facts != null) notice(instance, facts, NoticeKind.STARTED, null);
    }

    void reconnected(AbyssInstance instance, UUID player) {
        var facts = live.get(instance.getInstanceId());
        var previous = facts == null ? null : facts.members().get(player);
        if (previous == null) return;
        var member = new Member(player, MemberState.PRESENT, previous.deaths(), null, System.currentTimeMillis());
        facts.members().put(player, member);
        notice(instance, facts, NoticeKind.RECONNECTED, member);
    }

    void member(AbyssInstance instance, UUID player, MemberState state, String reason, Integer deaths) {
        var facts = live.get(instance.getInstanceId());
        if (facts == null) return;
        var previous = facts.members().get(player);
        if (previous == null) return;
        var updated = new Member(player, state, deaths == null ? previous.deaths() : deaths,
            reason, System.currentTimeMillis());
        facts.members().put(player, updated);
        notice(instance, facts, switch (state) {
            case LEFT -> NoticeKind.LEFT;
            case DISCONNECTED -> NoticeKind.DISCONNECTED;
            case PRESENT -> NoticeKind.DIED;
        }, updated);
    }

    private void notice(AbyssInstance instance, Live facts, NoticeKind kind, Member member) {
        service.publishRunNotice(new RunNotice(1, UUID.randomUUID(), instance.getInstanceId(),
            facts.operation().intent.requestId(), facts.sequence().incrementAndGet(),
            System.currentTimeMillis(), kind, member));
    }

    void finish(AbyssInstance instance, Outcome outcome, String reason) {
        UUID id = instance.getInstanceId();
        var facts = live.remove(id);
        if (facts == null || database == null) return;
        var intent = facts.operation().intent;
        long endedAt = instance.getEndTime() == 0 ? System.currentTimeMillis() : instance.getEndTime();
        var result = new Result(id, id, intent.requestId(), intent.owner(), intent.dungeon(),
            (String) instance.getVariables().get("plan"), intent.difficulty(), intent.rulesVersion(), intent.actor(),
            facts.admittedAt(), instance.getStartTime(), endedAt, outcome, reason, List.copyOf(facts.members().values()));
        persist(result, 0);
    }

    private void persist(Result result, int attempt) {
        database.submitDatabase(() -> commit(result, encode(result), true))
            .whenComplete((inserted, error) -> service.runOnMain(() -> {
                if (error != null) {
                    if (attempt < 2 && service.running()) {
                        service.scheduleDelayedRaw(() -> persist(result, attempt + 1), 20L << attempt);
                    } else {
                        service.logRunResultFailure(result.runId(), error);
                    }
                } else if (Boolean.TRUE.equals(inserted) && service.running()) {
                    service.publishRunResult(new ResultCommitted(1, result.resultId(), result.runId(),
                        result.requestId(), result.endedAt()));
                }
            }));
    }

    private boolean commit(Result result, String snapshot, boolean stage) throws Exception {
        Path file = outbox == null ? null : outbox.resolve(result.runId() + ".yml");
        if (stage && file != null && !Files.exists(file)) {
            Files.createDirectories(outbox);
            Path temporary = Files.createTempFile(outbox, "result-", ".tmp");
            try {
                try (var channel = java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
                    var bytes = java.nio.ByteBuffer.wrap(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    while (bytes.hasRemaining()) channel.write(bytes);
                    channel.force(true);
                }
                Files.move(temporary, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        if (stage && file != null && !snapshot.equals(Files.readString(file)))
            throw new IllegalStateException("Conflicting result outbox snapshot: " + result.runId());
        boolean inserted = repository.insertRunResult(result.runId(), result.owner(), result.endedAt(), snapshot);
        if (!inserted && !snapshot.equals(repository.loadRunResult(result.runId())))
            throw new IllegalStateException("Conflicting immutable run result: " + result.runId());
        if (file != null) {
            try { Files.deleteIfExists(file); }
            catch (java.io.IOException failure) { service.logRunResultFailure(result.runId(), failure); }
        }
        return inserted;
    }

    void replay() {
        if (outbox == null || database == null || !service.running()) return;
        long now = System.currentTimeMillis();
        if (now < nextReplay) return;
        nextReplay = now + 60000L;
        database.submitDatabase(() -> {
            repository.backfillResultFeed();
            var committed = new java.util.ArrayList<Result>();
            if (!Files.isDirectory(outbox)) return committed;
            try (var files = Files.newDirectoryStream(outbox, "*.yml")) {
                int processed = 0;
                for (Path path : files) {
                    if (processed++ >= 100) break;
                    try {
                        String snapshot = Files.readString(path);
                        Result result = decode(snapshot);
                        if (!path.getFileName().toString().equals(result.runId() + ".yml"))
                            throw new IllegalArgumentException("Invalid result outbox filename");
                        if (commit(result, snapshot, false)) committed.add(result);
                    } catch (Exception failure) {
                        java.util.logging.Logger.getLogger("Abyss").log(java.util.logging.Level.WARNING,
                            "Pending run result remains in outbox: " + path.getFileName(), failure);
                    }
                }
            }
            return committed;
        }).thenAccept(committed -> service.runOnMain(() -> {
            if (!service.running()) return;
            for (Result result : committed) service.publishRunResult(new ResultCommitted(1, result.resultId(),
                result.runId(), result.requestId(), result.endedAt()));
        }));
    }

    @Override public CompletableFuture<Result> result(UUID runId) {
        var reader = database;
        if (reader == null || !service.running()) return CompletableFuture.failedFuture(new IllegalStateException("Abyss unavailable"));
        return reader.submitDatabase(() -> {
            String data = repository.loadRunResult(runId);
            return data == null ? null : decode(data);
        });
    }

    @Override public CompletableFuture<List<Result>> results(String owner, Cursor after, int limit) {
        var reader = database;
        if (reader == null || !service.running()) return CompletableFuture.failedFuture(new IllegalStateException("Abyss unavailable"));
        return reader.submitDatabase(() -> {
            var snapshots = repository.loadRunResults(owner, after == null ? 0 : after.endedAt(),
                after == null ? "" : after.runId().toString(), Math.min(100, Math.max(1, limit)));
            var results = new java.util.ArrayList<Result>();
            for (String snapshot : snapshots) results.add(decode(snapshot));
            return List.copyOf(results);
        });
    }

    @Override public CompletableFuture<List<Committed>> committedResults(String owner, long afterSequence, int limit) {
        var reader = database;
        if (reader == null || !service.running()) return CompletableFuture.failedFuture(new IllegalStateException("Abyss unavailable"));
        return reader.submitDatabase(() -> {
            var results = new java.util.ArrayList<Committed>();
            for (var snapshot : repository.committedResults(owner, Math.max(0, afterSequence), Math.min(100, Math.max(1, limit))))
                results.add(new Committed(snapshot.sequence(), decode(snapshot.snapshot())));
            return List.copyOf(results);
        });
    }

    static String encode(Result value) {
        var yaml = new YamlConfiguration();
        yaml.set("run", value.runId().toString());
        yaml.set("request", value.requestId().toString());
        yaml.set("owner", value.owner());
        yaml.set("dungeon", value.dungeon());
        yaml.set("plan", value.plan());
        yaml.set("difficulty", value.difficulty());
        yaml.set("rules", value.rulesVersion());
        yaml.set("leader", value.leader().toString());
        yaml.set("admitted", value.admittedAt());
        yaml.set("started", value.startedAt());
        yaml.set("ended", value.endedAt());
        yaml.set("outcome", value.outcome().name());
        yaml.set("reason", value.reason());
        for (Member member : value.roster()) {
            String key = "members." + member.player();
            yaml.set(key + ".state", member.state().name());
            yaml.set(key + ".deaths", member.deaths());
            yaml.set(key + ".reason", member.leaveReason());
            yaml.set(key + ".at", member.lastChangedAt());
        }
        return yaml.saveToString();
    }

    static Result decode(String snapshot) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(snapshot);
        UUID run = UUID.fromString(yaml.getString("run"));
        var roster = new java.util.ArrayList<Member>();
        var members = yaml.getConfigurationSection("members");
        if (members != null) for (String key : members.getKeys(false)) {
            roster.add(new Member(UUID.fromString(key), MemberState.valueOf(members.getString(key + ".state")),
                members.getInt(key + ".deaths"), members.getString(key + ".reason"), members.getLong(key + ".at")));
        }
        return new Result(run, run, UUID.fromString(yaml.getString("request")), yaml.getString("owner"),
            yaml.getString("dungeon"), yaml.getString("plan"), yaml.getString("difficulty"), yaml.getString("rules"),
            UUID.fromString(yaml.getString("leader")), yaml.getLong("admitted"), yaml.getLong("started"), yaml.getLong("ended"),
            Outcome.valueOf(yaml.getString("outcome")), yaml.getString("reason"), roster);
    }
}
