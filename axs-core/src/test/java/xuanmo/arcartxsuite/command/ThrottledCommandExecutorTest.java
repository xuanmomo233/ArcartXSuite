package xuanmo.arcartxsuite.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;

class ThrottledCommandExecutorTest {
    private final Plugin plugin = mock(Plugin.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    private final List<Timer> timers = new ArrayList<>();
    private final List<String> executed = new ArrayList<>();

    @AfterEach
    void resetExecutor() {
        ThrottledCommandExecutor.init(null, 0);
    }

    @Test
    void idleQueueSchedulesNothingAndEachBurstUsesOneTimerWithinTheTickBudget() {
        try (var bukkit = server()) {
            ThrottledCommandExecutor.init(plugin, 2);
            assertTrue(timers.isEmpty());
            assertTrue(ThrottledCommandExecutor.dispatch("first"));
            assertTrue(ThrottledCommandExecutor.dispatch("second"));
            ThrottledCommandExecutor.dispatch("third", success -> {
                assertTrue(success);
                assertTrue(ThrottledCommandExecutor.dispatch("fourth"));
                assertTrue(ThrottledCommandExecutor.dispatch("fifth"));
            });
            assertEquals(1, timers.size());

            Timer timer = timers.get(0);
            timer.callback().run();
            assertEquals(List.of("first", "second"), executed);
            verify(timer.task(), never()).cancel();
            timer.callback().run();
            assertEquals(List.of("first", "second", "third", "fourth"), executed);
            assertEquals(1, ThrottledCommandExecutor.pendingCount());
            timer.callback().run();
            assertEquals(0, ThrottledCommandExecutor.pendingCount());
            verify(timer.task()).cancel();

            assertTrue(ThrottledCommandExecutor.dispatch("another"));
            assertEquals(2, timers.size());
            timer.callback().run();
            assertEquals(List.of("first", "second", "third", "fourth", "fifth"), executed);
            timers.get(1).callback().run();
            assertEquals("another", executed.get(executed.size() - 1));
            verify(timers.get(1).task()).cancel();
        }
    }

    @Test
    void replacementAndShutdownRejectPendingCommandsAndCompleteEachReceiptOnce() {
        try (var bukkit = server()) {
            var receipts = new ArrayList<Boolean>();
            ThrottledCommandExecutor.init(plugin, 1);
            ThrottledCommandExecutor.dispatch("old", receipts::add);
            Timer retired = timers.get(0);

            ThrottledCommandExecutor.init(plugin, 1);
            assertEquals(List.of(false), receipts);
            verify(retired.task()).cancel();
            ThrottledCommandExecutor.dispatch("current", receipts::add);
            retired.callback().run();
            assertTrue(executed.isEmpty());
            timers.get(1).callback().run();
            assertEquals(List.of("current"), executed);
            assertEquals(List.of(false, true), receipts);

            ThrottledCommandExecutor.dispatch("waiting", receipts::add);
            ThrottledCommandExecutor.shutdown();
            assertFalse(ThrottledCommandExecutor.dispatch("late"));
            ThrottledCommandExecutor.dispatch("late with receipt", receipts::add);
            timers.get(2).callback().run();
            assertEquals(List.of(false, true, false, false), receipts);
            assertEquals(List.of("current"), executed);
            assertEquals(0, ThrottledCommandExecutor.pendingCount());

            ThrottledCommandExecutor.init(plugin, 0);
            assertTrue(ThrottledCommandExecutor.dispatch("direct"));
            assertEquals(List.of("current", "direct"), executed);
            assertEquals(3, timers.size());
        }
    }

    @Test
    void concurrentAdmissionsKeepTheExactQueueLimit() throws Exception {
        try (var bukkit = server()) {
            ThrottledCommandExecutor.init(plugin, 2);
            assertTrue(ThrottledCommandExecutor.dispatch("first"));
            var workers = Executors.newFixedThreadPool(4);
            try {
                List<Future<?>> submissions = new ArrayList<>();
                for (int n = 0; n < 4; n++) {
                    int count = n == 0 ? 2499 : 2500;
                    submissions.add(workers.submit(() -> {
                        for (int i = 0; i < count; i++) {
                            assertTrue(ThrottledCommandExecutor.dispatch("queued"));
                        }
                    }));
                }
                for (var submission : submissions) submission.get(5, TimeUnit.SECONDS);
            } finally {
                workers.shutdownNow();
            }
            assertEquals(10_000, ThrottledCommandExecutor.pendingCount());
            assertEquals(1, timers.size());
            var rejected = new ArrayList<Boolean>();
            ThrottledCommandExecutor.dispatch("overflow", rejected::add);
            assertEquals(List.of(false), rejected);
            ThrottledCommandExecutor.shutdown();
            timers.get(0).callback().run();
            assertTrue(executed.isEmpty());
        }
    }

    @Test
    void aRejectedTimerLeavesNoCommandBehindAndTheNextAdmissionCanStartAgain() {
        try (var bukkit = server()) {
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
                .thenThrow(new IllegalPluginAccessException("scheduler rejected the task"))
                .thenAnswer(call -> register(call.getArgument(1)));
            ThrottledCommandExecutor.init(plugin, 1);
            var rejected = new ArrayList<Boolean>();
            ThrottledCommandExecutor.dispatch("refused", rejected::add);
            assertEquals(List.of(false), rejected);
            assertEquals(0, ThrottledCommandExecutor.pendingCount());
            assertTrue(ThrottledCommandExecutor.dispatch("accepted"));
            timers.get(0).callback().run();
            assertEquals(List.of("accepted"), executed);
        }
    }

    private MockedStatic<Bukkit> server() {
        var bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
        bukkit.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
            .thenAnswer(call -> register(call.getArgument(1)));
        bukkit.when(() -> Bukkit.dispatchCommand(eq(console), anyString())).thenAnswer(call -> {
            executed.add(call.<String>getArgument(1));
            return true;
        });
        return bukkit;
    }

    private BukkitTask register(Runnable callback) {
        var task = mock(BukkitTask.class);
        timers.add(new Timer(callback, task));
        return task;
    }

    private record Timer(Runnable callback, BukkitTask task) {}
}
