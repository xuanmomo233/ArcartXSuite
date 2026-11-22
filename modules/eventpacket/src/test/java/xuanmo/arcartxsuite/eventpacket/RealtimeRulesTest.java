package xuanmo.arcartxsuite.eventpacket;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;

class RealtimeRulesTest {
    private static final String FISH = "axs.fishing.success";
    private static final String QUEST = "axs.questgps.quest_completed";

    private static RuleConfiguration load(String subtitle) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            runtime:
              queue-capacity: 64
              work-per-tick: 8
              budget-micros: 5000
            rules:
              fish:
                trigger: event-bus
                topic: axs.fishing.success
                when:
                  fish_rarity: LEGENDARY
                actions:
                  - { type: subtitle, group: '%s' }
              quest:
                trigger: event-bus
                topic: axs.questgps.quest_completed
                when:
                  quest_id: chapter_one
                actions:
                  - { type: quest.offer, quest: chapter_two }
            """.formatted(subtitle));
        return RuleConfiguration.parse(yaml);
    }

    @Test
    void routesBothGameplayEventsAndDropsOldGenerationOnReload() throws Exception {
        var engine = new RealtimeRules();
        var scheduler = new TestScheduler();
        var received = new ArrayList<RealtimeRules.Execution>();
        engine.install(load("old-subtitle"), scheduler::schedule, received::add);
        assertTrue(scheduler.tasks.isEmpty());
        var fish = Map.of("fish_rarity", "LEGENDARY");
        var quest = Map.of("quest_id", "chapter_one");
        assertTrue(engine.offer(FISH, null, fish));
        var retired = scheduler.last();
        engine.install(load("new-subtitle"), scheduler::schedule, received::add);
        assertTrue(retired.isCancelled());
        assertTrue(engine.offer(FISH, null, fish));
        assertTrue(engine.offer(QUEST, null, quest));
        assertFalse(engine.offer(FISH, null, Map.of("fish_rarity", "COMMON")));

        retired.callback.run();
        assertTrue(received.isEmpty());
        var live = scheduler.last();
        while (engine.statistics().queued() > 0) live.callback.run();
        assertTrue(live.isCancelled());
        assertEquals(2, received.size());
        assertEquals(RuleConfiguration.ActionKind.SUBTITLE, received.get(0).action().kind());
        assertEquals("new-subtitle", received.get(0).action().value().render(Map.of()));
        assertEquals(RuleConfiguration.ActionKind.QUEST_OFFER, received.get(1).action().kind());
        assertEquals("chapter_two", received.get(1).action().value().render(Map.of()));
        assertEquals(1, engine.statistics().discarded());
    }

    @Test
    void eventsThatDoNotMatchNeverFillTheQueueAndOnlyRuleFieldsAreRetained() throws Exception {
        var engine = new RealtimeRules();
        var scheduler = new TestScheduler();
        var received = new ArrayList<RealtimeRules.Execution>();
        engine.install(load("award_{fish_name}"), scheduler::schedule, received::add);
        for (int i = 0; i < 128; i++) {
            assertFalse(engine.offer(FISH, null, Map.of("fish_rarity", "COMMON")));
        }
        var oversized = new LinkedHashMap<String, String>();
        for (int i = 0; i < 64; i++) oversized.put("other_" + i, "value");
        oversized.put("fish_rarity", "LEGENDARY");
        assertFalse(engine.offer(FISH, null, oversized));
        assertEquals(129, engine.statistics().unmatched());
        assertEquals(0, engine.statistics().dropped());
        assertTrue(scheduler.tasks.isEmpty());
        assertTrue(engine.offer(FISH, null,
            Map.of("fish_rarity", "LEGENDARY", "fish_name", "salmon", "unused", "not retained")));
        scheduler.last().callback.run();
        var execution = received.get(0);
        assertEquals(Map.of("fish_rarity", "LEGENDARY", "fish_name", "salmon"), execution.event().fields());
        assertEquals("award_salmon", execution.action().value().render(execution.event().fields()));
    }

    @Test
    void rejectsTheOldSessionEvenIfThePlayerIsAdmittedAgain() {
        UUID player = UUID.randomUUID();
        UUID connection = UUID.randomUUID();
        var event = new RealtimeRules.Snapshot(player, connection, 7L, Map.of());
        assertTrue(event.sameSession(session(player, connection, 7L, AuthSnapshot.Stage.ADMITTED)));
        assertFalse(event.sameSession(session(player, UUID.randomUUID(), 7L, AuthSnapshot.Stage.ADMITTED)));
        assertFalse(event.sameSession(session(player, connection, 8L, AuthSnapshot.Stage.ADMITTED)));
        assertFalse(event.sameSession(session(player, connection, 7L, AuthSnapshot.Stage.RESTRICTED)));
    }

    private static AuthSnapshot session(UUID player, UUID connection, long generation, AuthSnapshot.Stage stage) {
        return new AuthSnapshot(player, connection, generation, null, AuthSnapshot.Registration.UNKNOWN,
            stage, "test", Set.of(), "");
    }

    @Test
    void concurrentProducersRespectQueueAndTickLimits() throws Exception {
        var engine = new RealtimeRules();
        var scheduler = new TestScheduler();
        int[] count = {0};
        Thread tickThread = Thread.currentThread();
        engine.install(load("subtitle"), scheduler::schedule, execution -> {
            assertSame(tickThread, Thread.currentThread());
            count[0]++;
        });
        var event = Map.of("fish_rarity", "LEGENDARY");
        long start = System.nanoTime();
        var producers = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int n = 0; n < 8; n++) {
                futures.add(producers.submit(() -> {
                    for (int i = 0; i < 50_000; i++) engine.offer(FISH, null, event);
                }));
            }
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            producers.shutdownNow();
        }
        double elapsedMillis = (System.nanoTime() - start) / 1_000_000.0;
        assertEquals(64, engine.statistics().queued());
        assertEquals(400_000 - 64, engine.statistics().dropped());
        assertEquals(1, scheduler.tasks.size());
        var loop = scheduler.last();
        while (engine.statistics().queued() > 0) {
            int before = count[0];
            loop.callback.run();
            assertTrue(count[0] - before <= 8);
        }
        assertEquals(64, count[0]);
        assertTrue(loop.isCancelled());
        assertTrue(engine.offer(FISH, null, event));
        assertEquals(2, scheduler.tasks.size());
        loop.callback.run();
        assertEquals(64, count[0]);
        scheduler.last().callback.run();
        assertEquals(65, count[0]);
        System.out.printf("EventPacket: 400,000 offers, 8 producers, %.1f ms; %s%n", elapsedMillis, engine.statistics());
    }

    @Test
    void aPartialEventKeepsTheLoopActiveAcrossTicksAndAcceptsNewWorkDuringDrain() throws Exception {
        var engine = new RealtimeRules();
        var scheduler = new TestScheduler();
        var event = Map.of();
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
            runtime:
              work-per-tick: 1
              budget-micros: 5000
            rules:
              fish:
                trigger: event-bus
                topic: axs.fishing.success
                actions:
                  - { type: subtitle, group: 'first' }
                  - { type: subtitle, group: 'last' }
            """);
        var order = new ArrayList<String>();
        engine.install(RuleConfiguration.parse(yaml), scheduler::schedule, execution -> {
            order.add(execution.action().value().render(Map.of()));
            if (order.size() == 2) assertTrue(engine.offer(FISH, null, event));
        });
        assertTrue(engine.offer(FISH, null, event));
        var task = scheduler.last();
        task.callback.run();
        assertEquals(List.of("first"), order);
        assertEquals(1, engine.statistics().queued());
        assertFalse(task.isCancelled());

        for (int i = 0; i < 3; i++) task.callback.run();
        assertEquals(List.of("first", "last", "first", "last"), order);
        assertEquals(1, scheduler.tasks.size());
        assertTrue(task.isCancelled());
    }

    @Test
    void refusedSchedulerRegistrationDoesNotLeaveAnAcceptedEventBehind() throws Exception {
        var engine = new RealtimeRules();
        var scheduler = new TestScheduler();
        engine.install(load("subtitle"), task -> {
            throw new RejectedExecutionException("retired");
        }, execution -> fail("No action may run"));
        var event = Map.of("fish_rarity", "LEGENDARY");
        assertFalse(engine.offer(FISH, null, event));
        assertEquals(0, engine.statistics().queued());
        assertEquals(0, engine.statistics().accepted());
        assertEquals(1, engine.statistics().dropped());

        engine.install(load("subtitle"), scheduler::schedule, execution -> fail("Stopped before tick"));
        assertTrue(engine.offer(FISH, null, event));
        engine.stop();
        assertTrue(scheduler.last().isCancelled());
        scheduler.last().callback.run();
        assertEquals(1, engine.statistics().discarded());
        assertFalse(engine.offer(FISH, null, event));
    }

    private static final class TestScheduler {
        private final List<TestTask> tasks = new ArrayList<>();

        private ScheduledTask schedule(Runnable callback) {
            var task = new TestTask(callback);
            tasks.add(task);
            return task;
        }

        private TestTask last() {
            return tasks.get(tasks.size() - 1);
        }
    }

    private static final class TestTask implements ScheduledTask {
        private final Runnable callback;
        private boolean cancelled;

        private TestTask(Runnable callback) { this.callback = callback; }
        @Override public void cancel() { cancelled = true; }
        @Override public boolean isCancelled() { return cancelled; }
    }
}
