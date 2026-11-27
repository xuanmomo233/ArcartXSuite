package xuanmo.arcartxsuite.eventpacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.plugin.IllegalPluginAccessException;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.condition.ScriptConditionKind;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

/** Only offer() runs on publishers. Generation changes and draining run on the server thread. */
final class RealtimeRules {
    record Snapshot(UUID player, UUID connection, long sessionGeneration, Map<String, String> fields) {
        Snapshot {
            fields = Map.copyOf(fields);
        }

        static Snapshot from(AuthSnapshot session, Map<String, String> fields) {
            return new Snapshot(session == null ? null : session.playerId(),
                session == null ? null : session.connectionId(), session == null ? 0 : session.generation(),
                fields);
        }

        boolean sameSession(AuthSnapshot current) {
            return current.admitted() && connection != null && connection.equals(current.connectionId())
                && sessionGeneration == current.generation();
        }
    }

    record Execution(RuleConfiguration.LiveRule rule, RuleConfiguration.LiveAction action, Snapshot event) {}
    record Statistics(int queued, long accepted, long unmatched, long dropped, Map<String, Long> droppedByTopic,
                      long discarded, long actions, long slowestTickNanos) {}

    private record Pending(Snapshot snapshot, List<RuleConfiguration.LiveRule> rules) {}
    private record TopicRules(List<RuleConfiguration.LiveRule> rules, Set<String> fields, boolean unconditional) {
        private static TopicRules prepare(List<RuleConfiguration.LiveRule> rules) {
            var fields = new HashSet<String>();
            boolean unconditional = true;
            for (var rule : rules) {
                unconditional &= rule.conditions().isEmpty();
                fields.addAll(rule.conditions().keySet());
                rule.actions().forEach(action -> {
                    fields.addAll(action.value().fields());
                    action.data().values().forEach(template -> fields.addAll(template.fields()));
                });
                for (var check : rule.checks()) {
                    if (check.kind() == ScriptConditionKind.CONTEXT) {
                        addReference(fields, check.placeholder());
                        addReference(fields, check.value());
                    }
                }
            }
            return new TopicRules(rules, Set.copyOf(fields), unconditional);
        }

        private static void addReference(Set<String> fields, String expression) {
            if (expression != null && expression.length() > 2 && expression.startsWith("{") && expression.endsWith("}")) {
                fields.add(expression.substring(1, expression.length() - 1));
            }
        }

        private Map<String, String> copyFields(Object payload) {
            if (fields.isEmpty() || !(payload instanceof Map<?, ?> values) || values.isEmpty()) return Map.of();
            var copy = new HashMap<String, String>();
            int inspected = 0;
            for (var entry : values.entrySet()) {
                if (++inspected > 64) break;
                if (entry.getKey() instanceof String key && key.length() <= 64 && fields.contains(key)
                    && entry.getValue() instanceof String value && value.length() <= 1024) {
                    copy.put(key, value);
                }
            }
            return copy;
        }

        private List<RuleConfiguration.LiveRule> matching(Map<String, String> values) {
            if (unconditional) return rules;
            if (rules.size() == 1) return matches(rules.get(0).conditions(), values) ? rules : List.of();
            var selected = new ArrayList<RuleConfiguration.LiveRule>();
            for (var rule : rules) if (matches(rule.conditions(), values)) selected.add(rule);
            return selected;
        }
    }

    private static final class Generation {
        private final RuleConfiguration configuration;
        private final Map<String, TopicRules> topics;
        private final ArrayDeque<Pending> queue;
        private final Map<String, LongAdder> droppedByTopic;
        private final Function<Runnable, ScheduledTask> schedule;
        private final Consumer<Execution> execute;
        private final BiPredicate<RuleConfiguration.LiveRule, Snapshot> prepare;
        private TickLoop loop;
        private Pending current;
        private int ruleIndex;
        private int actionIndex;
        private boolean rulePrepared;

        private Generation(RuleConfiguration configuration, Function<Runnable, ScheduledTask> schedule,
                           BiPredicate<RuleConfiguration.LiveRule, Snapshot> prepare, Consumer<Execution> execute) {
            this.configuration = configuration;
            this.queue = new ArrayDeque<>(configuration.queueCapacity());
            this.schedule = schedule;
            this.prepare = prepare;
            this.execute = execute;
            var counts = new HashMap<String, LongAdder>();
            var prepared = new HashMap<String, TopicRules>();
            configuration.topics().forEach((topic, rules) -> {
                counts.put(topic, new LongAdder());
                prepared.put(topic, TopicRules.prepare(rules));
            });
            this.topics = Map.copyOf(prepared);
            this.droppedByTopic = Map.copyOf(counts);
        }
    }

    private final LongAdder accepted = new LongAdder();
    private final LongAdder unmatched = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final LongAdder discarded = new LongAdder();
    private final LongAdder actions = new LongAdder();
    private final AtomicLong slowestTickNanos = new AtomicLong();
    private volatile Generation active;

    void install(RuleConfiguration configuration, Function<Runnable, ScheduledTask> schedule,
                 Consumer<Execution> execute) {
        install(configuration, schedule, (rule, event) -> true, execute);
    }

    void install(RuleConfiguration configuration, Function<Runnable, ScheduledTask> schedule,
                 BiPredicate<RuleConfiguration.LiveRule, Snapshot> prepare, Consumer<Execution> execute) {
        replace(configuration.topics().isEmpty() ? null : new Generation(configuration, schedule, prepare, execute));
    }

    void stop() {
        replace(null);
    }

    private void replace(Generation next) {
        TickLoop retired;
        synchronized (this) {
            var previous = active;
            active = next;
            if (previous == null) return;
            discarded.add(previous.queue.size() + (previous.current == null ? 0 : 1));
            previous.queue.clear();
            retired = previous.loop;
            previous.loop = null;
        }
        if (retired != null) retired.task.cancel();
    }

    boolean offer(String topic, AuthSnapshot session, Object payload) {
        var generation = active;
        if (generation == null || (session != null && !session.admitted())) return false;
        var topicRules = generation.topics.get(topic);
        if (topicRules == null) return false;
        Map<String, String> fields = topicRules.copyFields(payload);
        var rules = topicRules.matching(fields);
        if (rules.isEmpty()) {
            unmatched.increment();
            return false;
        }
        return enqueue(generation, topic, session, fields, rules);
    }

    private synchronized boolean enqueue(Generation generation, String topic, AuthSnapshot session,
                                         Map<String, String> fields, List<RuleConfiguration.LiveRule> rules) {
        if (generation != active) return false;
        if (generation.queue.size() >= generation.configuration.queueCapacity() || !startLoop(generation)) {
            dropped.increment();
            generation.droppedByTopic.get(topic).increment();
            return false;
        }
        generation.queue.addLast(new Pending(Snapshot.from(session, fields), rules));
        accepted.increment();
        return true;
    }

    private boolean startLoop(Generation generation) {
        if (generation.loop != null) return true;
        var loop = new TickLoop(generation);
        try {
            loop.task = generation.schedule.apply(loop);
        } catch (RejectedExecutionException | IllegalPluginAccessException stopped) {
            return false;
        }
        generation.loop = loop;
        return true;
    }

    private final class TickLoop implements Runnable {
        private final Generation generation;
        private ScheduledTask task;

        private TickLoop(Generation generation) {
            this.generation = generation;
        }

        @Override public void run() {
            synchronized (RealtimeRules.this) {
                if (generation.loop != this) return;
            }
            try {
                drain(generation);
            } finally {
                stopIfIdle();
            }
        }

        private void stopIfIdle() {
            synchronized (RealtimeRules.this) {
                if (generation.loop != this || generation.current != null || !generation.queue.isEmpty()) return;
                generation.loop = null;
            }
            task.cancel();
        }
    }

    private void drain(Generation generation) {
        long start = System.nanoTime();
        int work = 0;
        while (generation == active && work < generation.configuration.workPerTick()
                && System.nanoTime() - start < generation.configuration.nanosPerTick()) {
            if (generation.current == null) {
                synchronized (this) {
                    generation.current = generation.queue.pollFirst();
                }
                if (generation.current == null) break;
                generation.ruleIndex = 0;
                generation.actionIndex = 0;
                generation.rulePrepared = false;
            }
            var current = generation.current;
            var rule = current.rules().get(generation.ruleIndex);
            work++;
            if (!generation.rulePrepared && !rule.checks().isEmpty()) {
                if (generation.prepare.test(rule, current.snapshot())) generation.rulePrepared = true;
                else advanceRule(generation);
                continue;
            }
            var action = rule.actions().get(generation.actionIndex++);
            if (generation.actionIndex == rule.actions().size()) advanceRule(generation);
            generation.execute.accept(new Execution(rule, action, current.snapshot()));
            actions.increment();
        }
        slowestTickNanos.accumulateAndGet(System.nanoTime() - start, Math::max);
    }

    private static void advanceRule(Generation generation) {
        generation.actionIndex = 0;
        generation.rulePrepared = false;
        if (++generation.ruleIndex == generation.current.rules().size()) generation.current = null;
    }

    static boolean matches(Map<String, String> conditions, Map<String, String> fields) {
        for (var entry : conditions.entrySet()) {
            if (!entry.getValue().equals(fields.get(entry.getKey()))) return false;
        }
        return true;
    }

    synchronized Statistics statistics() {
        var generation = active;
        var counts = new HashMap<String, Long>();
        if (generation != null) generation.droppedByTopic.forEach((topic, count) -> {
            long total = count.sum();
            if (total > 0) counts.put(topic, total);
        });
        return new Statistics(generation == null ? 0 : generation.queue.size() + (generation.current == null ? 0 : 1),
            accepted.sum(), unmatched.sum(), dropped.sum(),
            Map.copyOf(counts), discarded.sum(), actions.sum(), slowestTickNanos.get());
    }
}
