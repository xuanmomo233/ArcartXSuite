package xuanmo.arcartxsuite.abyss.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository;
import xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.AdmissionReceipt;
import xuanmo.arcartxsuite.abyss.data.PlayerDataManager;
import xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess;
import xuanmo.arcartxsuite.api.capability.AbyssEntryCostAccess.State;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.party.Party;
import xuanmo.arcartxsuite.abyss.template.AbyssTemplate;

/** Single-server admission journal. All access is confined to the server thread. */
final class AdmissionAccess implements AbyssAdmissionAccess {
    private record Key(String owner, UUID id) { }
    private record Binding(AbyssTemplate template, Party party, long partyRevision, long policyRevision) { }
    private record Ticket(Intent intent, Binding binding, long expires) { }
    private record Rule(org.bukkit.plugin.Plugin plugin, String owner, int priority, Policy policy) {
        boolean available() { return plugin == null || plugin.isEnabled(); }
    }
    private static final int LIMIT = 32768;
    private final UUID generation = UUID.randomUUID();
    private final AbyssService service;
    private final Map<UUID, Ticket> tickets = new LinkedHashMap<>();
    private final Map<UUID, Rule> rules = new LinkedHashMap<>();
    private final Map<Key, Operation> journal = new LinkedHashMap<>();
    private final Map<UUID, Operation> operations = new LinkedHashMap<>();
    private final Map<UUID, Operation> reservations = new LinkedHashMap<>();
    private long policyRevision;
    private long catalogRevision;
    private boolean checkingPolicy;
    private PlayerDataManager storage;

    static final class Operation {
        final Intent intent;
        final Map<UUID, String> outcomes = new LinkedHashMap<>();
        Phase phase = Phase.PREPARING;
        UUID runId;
        String reason;
        long policyRevision;
        String fingerprint;
        boolean persisted;
        boolean costUncertain;
        String costOwner;
        UUID costId;
        State costState;
        boolean cancelRequested;
        xuanmo.arcartxsuite.api.capability.AbyssEntryCostAccess.Participant costParticipant;
        boolean compensationAttempted;
        boolean entryOutcomeUncertain;
        Operation(Intent intent) {
            this.intent = intent;
            this.fingerprint = fingerprint(intent);
            intent.members().forEach(id -> outcomes.put(id, "PENDING"));
        }
        Entry snapshot() { return new Entry(phase, runId, reason, outcomes,
            costId == null ? null : new Cost(costOwner, costId, costState)); }
    }

    AdmissionAccess(AbyssService service) { this.service = service; }

    void load(JdbcAbyssRepository repository, PlayerDataManager writer) throws Exception {
        var receipts = repository.loadAdmissions();
        if (receipts.size() > LIMIT) throw new IllegalStateException("Admission journal exceeds capacity");
        for (var receipt : receipts) {
            UUID id = UUID.fromString(receipt.requestId());
            var yaml = new YamlConfiguration();
            yaml.loadFromString(receipt.snapshot());
            var op = new Operation(new Intent(receipt.owner(), id, null, List.of(), null, null, null, null));
            op.fingerprint = receipt.fingerprint();
            op.persisted = true;
            op.phase = Phase.valueOf(yaml.getString("phase"));
            op.reason = yaml.getString("reason");
            if (yaml.isString("run")) op.runId = UUID.fromString(yaml.getString("run"));
            if (yaml.isString("cost.id")) {
                op.costOwner = yaml.getString("cost.owner");
                op.costId = UUID.fromString(yaml.getString("cost.id"));
                op.costState = State.valueOf(yaml.getString("cost.state"));
            }
            var outcomes = yaml.getConfigurationSection("members");
            if (outcomes != null) for (String member : outcomes.getKeys(false)) {
                op.outcomes.put(UUID.fromString(member), outcomes.getString(member));
            }
            if (op.phase == Phase.PREPARING || op.phase == Phase.TELEPORTING
                || op.phase == Phase.RESERVING_COST || op.phase == Phase.COMMITTING_COST || op.phase == Phase.COMPENSATING_COST) {
                op.phase = Phase.REVIEW_REQUIRED;
                op.reason = "INTERRUPTED_ADMISSION";
                if (op.costId != null) op.costState = State.UNKNOWN;
            }
            journal.put(new Key(receipt.owner(), id), op);
        }
        storage = writer;
    }

    @Override public IntegrationInfo integrationInfo() {
        boolean running = service.running();
        return new IntegrationInfo(1, generation, catalogRevision, running, running ? null : "UNAVAILABLE",
            Map.of("catalog", running ? "AVAILABLE" : "UNAVAILABLE",
                "hostEntry", running && storage != null ? "AVAILABLE" : "UNAVAILABLE",
                "remoteEntry", "UNSUPPORTED", "originalRunReconnect", "UNSUPPORTED",
                "externalCost", running && storage != null ? "AVAILABLE" : "UNAVAILABLE"));
    }

    @Override public List<Dungeon> catalog() {
        requireMain();
        if (!service.running()) return List.of();
        return service.instances().getAllTemplates().stream().sorted(Comparator.comparing(AbyssTemplate::id))
            .map(t -> new Dungeon(t.id(), t.name(), String.join("\n", t.description()), t.difficulty(),
                t.requirements().minPlayers(), t.requirements().maxPlayers(), t.requirements().partyRequired(),
                t.requirements().partyMode(), t.maxInstances(),
                t.plans().values().stream().map(plan -> new Plan(plan.id(), plan.name(), plan.lore())).toList(),
                t.crossServer(), t.requirements().cooldownSeconds(), t.requirements().moneyCost(),
                t.requirements().currencyId(), t.requirements().staminaCost())).toList();
    }

    @Override public PlayerState playerState(UUID player) {
        requireMain();
        if (!service.running()) return new PlayerState(false, null, null, "UNKNOWN");
        var run = service.instances().getPlayerInstance(player);
        if (run == null) {
            var pending = reservations.get(player);
            return pending == null ? new PlayerState(true, null, null, "IDLE")
                : new PlayerState(true, pending.intent.requestId(), null, "RESERVING");
        }
        var op = operations.get(run.getInstanceId());
        String state = run.designMode() ? "DESIGN" : !run.hasEntered(player) && !run.isFinished()
            ? "ADMISSION_PENDING" : run.getStatus().name();
        return new PlayerState(true, op == null ? null : op.intent.requestId(), run.getInstanceId(), state);
    }

    @Override public Run run(UUID id) {
        requireMain();
        if (!service.running()) return null;
        var instance = service.instances().getInstance(id);
        if (instance == null || instance.designMode()) return null;
        var op = operations.get(id);
        var memberStates = new LinkedHashMap<UUID, String>();
        for (UUID member : instance.getPlayerUUIDs()) {
            var online = Bukkit.getPlayer(member);
            memberStates.put(member, online == null || !online.isOnline() ? "OFFLINE"
                : !instance.hasEntered(member) ? "ADMISSION_PENDING"
                : instance.isPlayerCurrentlyDead(online) ? "DEAD" : "PRESENT");
        }
        return new Run(id, instance.getTemplate().id(), (String)instance.getVariables().get("plan"),
            op == null ? null : op.intent.difficulty(), op == null ? null : op.intent.rulesVersion(),
            instance.getStatus().name(), op == null ? List.of() : op.intent.members(),
            List.copyOf(instance.getPlayerUUIDs()), instance.getWorld() == null ? null : instance.getWorld().getUID(),
            op == null ? null : op.intent.requestId(), op == null ? null : op.intent.actor(),
            op == null ? null : op.intent.externalPartyId(), instance.getCreateTime(), instance.getStartTime(), memberStates);
    }

    @Override public List<Run> activeRuns() {
        requireMain();
        if (!service.running()) return List.of();
        return service.instances().getAllInstances().stream().filter(instance -> !instance.designMode() && !instance.isFinished())
            .map(instance -> run(instance.getInstanceId())).toList();
    }

    @Override public Preflight preflight(Intent intent) {
        requireMain();
        var failures = validate(intent);
        if (!failures.isEmpty()) return new Preflight(null, 0, failures);
        long now = System.currentTimeMillis();
        tickets.values().removeIf(ticket -> ticket.expires() <= now);
        if (tickets.size() >= LIMIT) return new Preflight(null, 0, List.of(deny("BUSY", "预检容量已满")));
        UUID token = UUID.randomUUID();
        long expires = now + 15000;
        tickets.put(token, new Ticket(intent, binding(intent), expires));
        return new Preflight(token, expires, List.of());
    }

    @Override public Entry requestEntry(Intent intent, UUID token) {
        requireMain();
        if (!valid(intent)) return rejected("INVALID_INTENT");
        Key key = new Key(intent.owner(), intent.requestId());
        Operation previous = journal.get(key);
        if (previous != null) return previous.fingerprint.equals(fingerprint(intent)) ? previous.snapshot()
            : new Entry(Phase.CONFLICT, null, "REQUEST_CONFLICT", Map.of());
        if (!service.running() || checkingPolicy) return rejected("UNAVAILABLE");
        Ticket ticket = tickets.get(token);
        if (ticket == null || ticket.expires() <= System.currentTimeMillis() || !ticket.intent().equals(intent)
            || !ticket.binding().equals(binding(intent))) return rejected("PREFLIGHT_STALE");
        if (journal.size() >= LIMIT) return rejected("JOURNAL_FULL");
        if (intent.members().stream().anyMatch(reservations::containsKey)) return rejected("MEMBER_BUSY");
        tickets.remove(token);
        var op = new Operation(intent);
        var template = ticket.binding().template();
        if (template != null && template.requirements().externalCostOwner() != null) {
            op.costOwner = template.requirements().externalCostOwner();
            op.costId = UUID.nameUUIDFromBytes(("axs.abyss.cost/" + intent.owner() + "/" + intent.requestId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            op.costState = State.NONE;
        }
        journal.put(key, op);
        if (storage == null) {
            op.phase = Phase.REJECTED;
            op.reason = "JOURNAL_UNAVAILABLE";
        } else {
            intent.members().forEach(member -> reservations.put(member, op));
            storage.writeAdmission(receipt(op), true).whenComplete((ignored, error) -> service.runOnMain(() -> {
                if (error != null) {
                    if (op.phase != Phase.CANCELLED) {
                        op.phase = Phase.REVIEW_REQUIRED;
                        op.reason = "JOURNAL_RESERVATION_UNKNOWN";
                    }
                    release(op);
                    return;
                }
                op.persisted = true;
                if (op.phase == Phase.CANCELLED || !service.running()) {
                    op.phase = Phase.CANCELLED;
                    changed(op);
                    release(op);
                    return;
                }
                if (op.costId != null && current(ticket)) {
                    var denials = service.preflightExternal(intent);
                    if (!denials.isEmpty()) {
                        op.phase = Phase.REJECTED;
                        op.reason = denials.get(0).code();
                        for (var denial : denials) if (denial.member() != null)
                            op.outcomes.put(denial.member(), denial.code());
                        changed(op);
                        return;
                    }
                    service.entryCosts().begin(op, this::changed, () -> continueEntry(op, ticket));
                } else continueEntry(op, ticket);
            }));
        }
        return op.snapshot();
    }

    private boolean current(Ticket ticket) {
        return ticket.expires() > System.currentTimeMillis() && ticket.binding().equals(binding(ticket.intent()));
    }

    private void continueEntry(Operation op, Ticket ticket) {
        if (!current(ticket)) {
            op.phase = Phase.REJECTED;
            op.reason = "PREFLIGHT_STALE";
        } else {
            try {
                var result = service.enterExternal(op.intent, op);
                if (!result.ok() && op.phase == Phase.PREPARING) {
                    op.phase = Phase.REJECTED;
                    op.reason = result.reason();
                }
            } catch (RuntimeException failure) {
                if (op.phase != Phase.STARTED) {
                    op.phase = Phase.REVIEW_REQUIRED;
                    op.reason = "ENTRY_RESULT_UNKNOWN";
                    op.entryOutcomeUncertain = true;
                }
            }
        }
        changed(op);
    }

    @Override public Entry entryStatus(String owner, UUID requestId) {
        requireMain();
        Operation op = journal.get(new Key(owner, requestId));
        return op == null ? null : op.snapshot();
    }

    @Override public Entry cancelEntry(String owner, UUID requestId) {
        requireMain();
        Operation op = journal.get(new Key(owner, requestId));
        if (op == null) return null;
        if (checkingPolicy) return op.snapshot();
        if (op.phase == Phase.RESERVING_COST || op.phase == Phase.COMMITTING_COST) {
            op.cancelRequested = true;
            return op.snapshot();
        }
        if (op.phase == Phase.PREPARING) {
            if (op.runId != null) service.cancelAdmission(op.runId);
            else {
                op.phase = Phase.CANCELLED;
                changed(op);
                release(op);
            }
        }
        return op.snapshot();
    }

    @Override public Registration registerPolicy(String owner, int priority, Policy policy) {
        return registerPolicy(null, owner, priority, policy);
    }

    @Override public Registration registerPolicy(org.bukkit.plugin.Plugin plugin, String owner, int priority, Policy policy) {
        requireMain();
        if (!service.running() || checkingPolicy) throw new IllegalStateException("Abyss admission unavailable");
        if (owner == null || !owner.matches("[a-zA-Z0-9_.:-]{1,96}")) throw new IllegalArgumentException("Invalid policy owner");
        UUID id = UUID.randomUUID();
        rules.put(id, new Rule(plugin, owner, priority, Objects.requireNonNull(policy)));
        policyRevision++;
        return () -> {
            requireMain();
            if (checkingPolicy) throw new IllegalStateException("Policy registration cannot change during evaluation");
            if (rules.remove(id) != null) policyRevision++;
        };
    }

    List<Denial> policies(Intent intent, AbyssTemplate template) {
        if (checkingPolicy) return List.of(deny("POLICY_REENTRANCY", "准入策略不能递归发起入场"));
        var failures = new ArrayList<Denial>();
        for (String required : template.requirements().admissionPolicies()) {
            if (rules.values().stream().noneMatch(rule -> rule.owner().equals(required) && rule.available()))
                failures.add(deny("POLICY_UNAVAILABLE", "必要准入策略不可用: " + required));
        }
        checkingPolicy = true;
        try {
            for (Rule rule : rules.values().stream().filter(Rule::available).sorted(Comparator.comparingInt(Rule::priority)).toList()) {
                try { failures.addAll(List.copyOf(rule.policy().check(intent))); }
                catch (RuntimeException failure) { failures.add(deny("POLICY_UNAVAILABLE", "准入策略不可用: " + rule.owner())); }
            }
        } finally {
            checkingPolicy = false;
        }
        return failures;
    }

    private List<Denial> validate(Intent intent) {
        if (!service.running()) return List.of(deny("UNAVAILABLE", "副本服务不可用"));
        if (!valid(intent)) return List.of(deny("INVALID_INTENT", "入场请求无效"));
        return service.preflightExternal(intent);
    }

    private Binding binding(Intent intent) {
        var actor = Bukkit.getPlayer(intent.actor());
        Party party = actor == null ? null : service.party().partyOf(actor);
        return new Binding(service.instances().getTemplate(intent.dungeon()), party,
            party == null ? 0 : party.revision(), policyRevision);
    }

    private static boolean valid(Intent intent) {
        return intent != null && intent.owner() != null && intent.owner().matches("[a-z0-9_.:-]{1,96}")
            && !intent.owner().equals("abyss") && intent.requestId() != null && intent.actor() != null
            && intent.dungeon() != null && !intent.dungeon().isBlank() && token(intent.dungeon())
            && token(intent.plan()) && token(intent.difficulty()) && token(intent.rulesVersion()) && intent.members().contains(intent.actor())
            && token(intent.externalPartyId()) && intent.rosterRevision() >= 0 && intent.selectionRevision() >= 0
            && intent.members().size() <= 256 && intent.members().stream().distinct().count() == intent.members().size();
    }

    private static boolean token(String value) { return value == null || value.matches("[\\p{L}\\p{N}_.:-]{0,128}"); }

    private static String fingerprint(Intent intent) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.DataOutputStream(bytes)) {
                for (String part : java.util.Arrays.asList(intent.owner(), String.valueOf(intent.requestId()),
                        String.valueOf(intent.actor()), intent.dungeon(), intent.plan(), intent.difficulty(),
                        intent.rulesVersion(), intent.externalPartyId())) {
                    out.writeBoolean(part != null);
                    if (part != null) out.writeUTF(part);
                }
                out.writeInt(intent.members().size());
                for (UUID member : intent.members()) out.writeUTF(member.toString());
                out.writeLong(intent.rosterRevision());
                out.writeLong(intent.selectionRevision());
            }
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static AdmissionReceipt receipt(Operation op) {
        var yaml = new YamlConfiguration();
        yaml.set("phase", op.phase.name());
        yaml.set("run", op.runId == null ? null : op.runId.toString());
        yaml.set("reason", op.reason);
        if (op.costId != null) {
            yaml.set("cost.owner", op.costOwner);
            yaml.set("cost.id", op.costId.toString());
            yaml.set("cost.state", op.costState.name());
        }
        op.outcomes.forEach((id, status) -> yaml.set("members." + id, status));
        return new AdmissionReceipt(op.intent.owner(), op.intent.requestId().toString(), op.fingerprint, yaml.saveToString());
    }

    void changed(Operation op) {
        if (op != null && op.costId != null) service.entryCosts().compensate(op, this::persist);
        persist(op);
    }

    private void persist(Operation op) {
        if (op == null) return;
        if (switch (op.phase) {
            case STARTED, REJECTED, CANCELLED, COMPENSATED, REVIEW_REQUIRED, CONFLICT -> true;
            default -> false;
        }) release(op);
        if (!op.persisted || storage == null) return;
        storage.writeAdmission(receipt(op), false).exceptionally(error -> {
            java.util.logging.Logger.getLogger("Abyss").warning("Admission journal write failed: " + op.intent.requestId());
            return null;
        });
    }

    static Denial deny(String code, String message) { return new Denial(null, code, message); }
    private static Entry rejected(String code) { return new Entry(Phase.REJECTED, null, code, Map.of()); }
    private static void requireMain() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Call Abyss admission on the server thread");
    }

    boolean reservedByOther(UUID member, Intent intent) {
        var pending = reservations.get(member);
        return pending != null && !pending.intent.equals(intent);
    }

    private void release(Operation op) {
        op.intent.members().forEach(member -> reservations.remove(member, op));
    }

    void attach(AbyssInstance instance, Operation op) {
        op.runId = instance.getInstanceId();
        op.policyRevision = policyRevision;
        operations.put(op.runId, op);
        changed(op);
    }
    Operation operation(AbyssInstance instance) { return operations.get(instance.getInstanceId()); }
    boolean policyUnchanged(Operation operation) { return operation.policyRevision == policyRevision; }
    void detached(AbyssInstance instance) { operations.remove(instance.getInstanceId()); }
    void catalogChanged() { catalogRevision++; }
    void shutdown() { tickets.clear(); rules.clear(); reservations.clear(); policyRevision++; }
}
