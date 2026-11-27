package xuanmo.arcartxsuite.eventpacket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

final class DurableRules implements AutoCloseable {
    private static final int PAGE_SIZE = 16;
    private static final int REUSABLE_PAGES = 8;
    private static final long WAIT_SECONDS = 8L;
    private static final long LEASE_MILLIS = 30_000L;
    private static final long BUSY_POLL_MILLIS = 1_000L;
    private static final long ABYSS_POLL_MILLIS = 5_000L;
    private static final long MAIL_UNAVAILABLE_POLL_MILLIS = 5_000L;
    private static final long SHARED_POLL_MILLIS = 30_000L;
    private final RuleJournal journal;
    private final Supplier<AbyssRunResultAccess> abyss;
    private final Supplier<AxsMailService> mail;
    private final Supplier<RuleConfiguration> rules;
    private final BooleanSupplier advancedCrossServerOrchestrationAllowed;
    private final Logger logger;
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicBoolean wakeRequested = new AtomicBoolean();
    private final ModuleExecutor worker = new ModuleExecutor(0, 1, 5, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(128), task -> {
            Thread thread = new Thread(task, "AXS-EventPacket-SQL");
            thread.setDaemon(true);
            return thread;
        });
    private volatile boolean closed;
    private volatile long nextPollAt;
    private volatile long retryNotBefore;
    private long abyssRetryNotBefore;
    private boolean initialized;
    private long nextWarning;
    private volatile String status = "starting";

    DurableRules(RuleJournal journal, Supplier<AbyssRunResultAccess> abyss, Supplier<AxsMailService> mail,
                 Supplier<RuleConfiguration> rules, BooleanSupplier advancedCrossServerOrchestrationAllowed, Logger logger) {
        this.journal = journal;
        this.abyss = abyss;
        this.mail = mail;
        this.rules = rules;
        this.advancedCrossServerOrchestrationAllowed = advancedCrossServerOrchestrationAllowed;
        this.logger = logger;
    }

    void wake() {
        if (closed) return;
        wakeRequested.set(true);
        startPoll();
    }

    void abyssCommitted() {
        var configuration = rules.get();
        if (configuration != null && !configuration.abyss().isEmpty() && canOrchestrateResults()) wake();
    }

    void pollIfDue() {
        long deadline = nextPollAt;
        if (wakeRequested.get() || deadline == 0L || System.nanoTime() - deadline >= 0L) startPoll();
    }

    private void startPoll() {
        if (closed || !polling.compareAndSet(false, true)) return;
        long retryAt = retryNotBefore;
        if (retryAt != 0L && System.nanoTime() - retryAt < 0L) {
            polling.set(false);
            return;
        }
        submit(() -> {
            // A fact committed after this point needs a second pass even if this pass sees an empty page.
            wakeRequested.set(false);
            return poll();
        }, false).whenComplete((delay, failure) -> {
            try {
                if (failure != null && !closed) {
                    retryNotBefore = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BUSY_POLL_MILLIS);
                    nextPollAt = retryNotBefore;
                    status = "retrying: " + failure.getClass().getSimpleName();
                    warn("持久规则将在下一轮重试", failure);
                } else if (delay != null) {
                    retryNotBefore = 0L;
                    nextPollAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay);
                }
            } finally {
                polling.set(false);
            }
            if (failure == null && wakeRequested.get()) startPoll();
        });
    }

    private long poll() throws Exception {
        status = "ready";
        long delay = SHARED_POLL_MILLIS;
        var configuration = rules.get();
        if (configuration != null && !configuration.abyss().isEmpty() && !canOrchestrateResults()) {
            status = "共享 MySQL 副本结果编排需要客户版有效授权";
        } else if (configuration != null && !configuration.abyss().isEmpty()) {
            delay = ABYSS_POLL_MILLIS;
            var source = abyss.get();
            if (source == null) {
                status = "waiting for Abyss";
            } else if (!canReadAbyssResults(source)) {
                status = BuildEdition.CROSS_SERVER_INCLUDED ? "Abyss must use shared MySQL"
                    : "免费云端版的副本结果编排需要 Abyss 本地 SQLite";
            } else if (abyssRetryNotBefore != 0L && System.nanoTime() - abyssRetryNotBefore < 0L) {
                status = "waiting to retry Abyss";
            } else {
                try {
                    if (consumeResults(configuration, source)) delay = BUSY_POLL_MILLIS;
                    abyssRetryNotBefore = 0L;
                } catch (InterruptedException interrupted) {
                    throw interrupted;
                } catch (Exception | LinkageError failure) {
                    // A separate source failure must not block already staged Mail jobs.
                    abyssRetryNotBefore = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ABYSS_POLL_MILLIS);
                    status = "retrying Abyss: " + failure.getClass().getSimpleName();
                    if (!closed) warn("副本结果补记稍后重试，继续处理待发送邮件", failure);
                }
            }
        }
        if (closed) return delay;

        var due = journal.nextDueDelayMillis();
        if (due.isEmpty()) return delay;
        var delivery = mail.get();
        if (delivery == null) {
            status = "waiting for Mail";
            return Math.min(delay, MAIL_UNAVAILABLE_POLL_MILLIS);
        }
        if (journal.mysql() && !delivery.usesSharedHostStorage()) {
            status = "Mail must use shared MySQL";
            return Math.min(delay, MAIL_UNAVAILABLE_POLL_MILLIS);
        }
        if (due.getAsLong() == 0L) {
            if (deliver(delivery)) return BUSY_POLL_MILLIS;
            if (closed) return delay;
            due = journal.nextDueDelayMillis();
        }
        return due.isEmpty() ? delay : Math.min(delay, Math.max(BUSY_POLL_MILLIS, due.getAsLong()));
    }

    CompletableFuture<Void> admit(String eventId, UUID player, Map<String, String> fields) {
        var configuration = rules.get();
        if (closed) return CompletableFuture.failedFuture(new RejectedExecutionException("EventPacket retired"));
        if (configuration == null || configuration.firstAdmission().isEmpty()) return CompletableFuture.completedFuture(null);
        return stageEvent("auth/" + eventId, player, fields, configuration.firstAdmission());
    }

    CompletableFuture<Void> auctionPurchased(String eventId, UUID buyer, Map<String, String> fields) {
        var configuration = rules.get();
        if (closed) return CompletableFuture.failedFuture(new RejectedExecutionException("EventPacket retired"));
        if (configuration == null || configuration.auctionPurchased().isEmpty()) return CompletableFuture.completedFuture(null);
        return stageEvent("auction/" + eventId, buyer, fields, configuration.auctionPurchased());
    }

    private CompletableFuture<Void> stageEvent(String key, UUID player, Map<String, String> fields,
                                               List<RuleConfiguration.MailRule> rules) {
        return submit(() -> {
            var jobs = new ArrayList<RuleJournal.MailJob>();
            for (var rule : rules) {
                if (RealtimeRules.matches(rule.conditions(), fields)) {
                    addJobs(jobs, key, player, fields, rule);
                }
            }
            if (journal.stageEvent(hash(key), jobs) && !jobs.isEmpty()) wake();
            return null;
        });
    }

    private boolean consumeResults(RuleConfiguration configuration, AbyssRunResultAccess source) throws Exception {
        if (!canConsumeResults(configuration, source)) return false;
        Long highWater = null;
        boolean more = false;
        var cursors = journal.cursors(configuration.abyss());
        var pages = new LinkedHashMap<ResultPosition, List<AbyssRunResultAccess.Committed>>(16, 0.75f, true);
        for (var rule : configuration.abyss()) {
            if (!canConsumeResults(configuration, source)) break;
            var position = cursors.get(new RuleJournal.CursorKey(rule.id(), rule.owner()));
            if (position == null) {
                Long start = rule.startSequence();
                if (start == null) {
                    if (highWater == null) highWater = source.latestCommittedSequence().get(WAIT_SECONDS, TimeUnit.SECONDS);
                    start = highWater;
                }
                if (!canConsumeResults(configuration, source)) break;
                journal.startCursor(rule.id(), rule.owner(), start, rule.startSequence() == null);
                position = journal.cursor(rule.id(), rule.owner()).orElseThrow();
            }
            long from = position.sequence();
            var key = new ResultPosition(rule.owner(), from);
            var page = pages.get(key);
            if (page == null) {
                if (!canConsumeResults(configuration, source)) break;
                page = source.committedResults(rule.owner(), from, PAGE_SIZE).get(WAIT_SECONDS, TimeUnit.SECONDS);
                if (pages.size() == REUSABLE_PAGES) pages.remove(pages.keySet().iterator().next());
                pages.put(key, page);
            }
            if (page.isEmpty() || !canConsumeResults(configuration, source)) continue;
            var jobs = new ArrayList<RuleJournal.MailJob>();
            long to = from;
            for (var entry : page) {
                if (entry.sequence() <= to || !rule.owner().equals(entry.result().owner())) {
                    throw new IllegalStateException("Abyss returned an invalid result page");
                }
                to = entry.sequence();
                if (entry.result().endedAt() >= position.notBefore()) {
                    planResult(jobs, entry.result(), rule);
                }
            }
            if (!canConsumeResults(configuration, source)) break;
            journal.stagePage(rule.id(), rule.owner(), from, to, jobs);
            more |= page.size() >= PAGE_SIZE;
        }
        return more;
    }

    private boolean canConsumeResults(RuleConfiguration configuration, AbyssRunResultAccess source) {
        return !closed && rules.get() == configuration && canOrchestrateResults() && canReadAbyssResults(source);
    }

    private boolean canReadAbyssResults(AbyssRunResultAccess source) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return source.usesLocalResultStorage();
        return !journal.mysql() || source.usesSharedHostStorage();
    }

    private boolean canOrchestrateResults() {
        return !journal.mysql() || BuildEdition.CROSS_SERVER_INCLUDED && advancedCrossServerOrchestrationAllowed.getAsBoolean();
    }

    private record ResultPosition(String owner, long cursor) {}

    static void planResult(List<RuleJournal.MailJob> jobs, AbyssRunResultAccess.Result result,
                           RuleConfiguration.MailRule rule) {
        if (result.outcome() != AbyssRunResultAccess.Outcome.COMPLETED) return;
        String dungeon = rule.conditions().get("dungeon");
        if (dungeon != null && !dungeon.equals(result.dungeon())) return;
        var planned = new java.util.HashSet<UUID>();
        for (var member : result.roster()) {
            if (member.state() == AbyssRunResultAccess.MemberState.LEFT || !planned.add(member.player())) continue;
            var fields = Map.of(
                "result_id", result.resultId().toString(), "run_id", result.runId().toString(),
                "owner", result.owner(), "dungeon", result.dungeon(), "plan", java.util.Objects.toString(result.plan(), ""),
                "difficulty", result.difficulty(), "outcome", result.outcome().name(),
                "member_state", member.state().name(), "deaths", Integer.toString(member.deaths()));
            if (RealtimeRules.matches(rule.conditions(), fields)) {
                addJobs(jobs, "abyss/" + result.resultId(), member.player(), fields, rule);
            }
        }
    }

    private static void addJobs(List<RuleJournal.MailJob> jobs, String fact, UUID player,
                                Map<String, String> fields, RuleConfiguration.MailRule rule) {
        for (var action : rule.actions()) {
            String key = hash(fact + "/" + rule.id() + "/" + action.id() + "/" + player);
            String title = action.title().render(fields);
            jobs.add(new RuleJournal.MailJob(key, player, title.isBlank() ? "系统奖励" : title,
                action.content().render(fields), action.currency(), action.amount()));
        }
    }

    private boolean deliver(AxsMailService delivery) throws Exception {
        int processed = 0;
        for (; processed < PAGE_SIZE && !closed; processed++) {
            var claim = journal.claim(LEASE_MILLIS);
            if (claim == null) return false;
            try {
                var receipt = delivery.sendDurable(claim.job().request()).get(WAIT_SECONDS, TimeUnit.SECONDS);
                if (receipt.mailId() <= 0) {
                    status = "waiting for Mail";
                    journal.retry(claim, delay(claim.attempt()));
                    return false;
                }
                journal.complete(claim, receipt.mailId());
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw failure;
            } catch (Exception failure) {
                journal.retry(claim, delay(claim.attempt()));
                throw failure;
            }
        }
        return processed == PAGE_SIZE;
    }

    private static long delay(int attempt) {
        return 1000L << Math.min(8, attempt);
    }

    private <T> CompletableFuture<T> submit(Callable<T> operation) {
        return submit(operation, true);
    }

    private <T> CompletableFuture<T> submit(Callable<T> operation, boolean finishOnClose) {
        var future = new CompletableFuture<T>();
        if (closed) return CompletableFuture.failedFuture(new RejectedExecutionException("EventPacket retired"));
        try {
            worker.execute(() -> {
                try {
                    if (closed && !finishOnClose) { future.complete(null); return; }
                    if (!initialized) {
                        journal.initialize();
                        initialized = true;
                    }
                    future.complete(operation.call());
                } catch (Exception | LinkageError failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException failure) {
            future.completeExceptionally(failure);
        }
        return future.copy();
    }

    private synchronized void warn(String message, Throwable failure) {
        long now = System.nanoTime();
        if (nextWarning != 0 && now - nextWarning < 0) return;
        nextWarning = now + TimeUnit.SECONDS.toNanos(30);
        logger.warning(message + " · " + failure.getMessage());
    }

    String status() {
        return status;
    }

    @Override public void close() {
        closed = true;
        worker.shutdown();
    }

    java.util.concurrent.CompletionStage<Void> stopped() { return worker.stopped(); }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }
}
