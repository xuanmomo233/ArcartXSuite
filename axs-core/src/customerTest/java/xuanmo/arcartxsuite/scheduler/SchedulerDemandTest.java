package xuanmo.arcartxsuite.scheduler;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulerDemandTest {
    @TempDir Path directory;
    record Pulse(Runnable run, BukkitTask handle, boolean async, long period) { }
    static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final Plugin plugin = mock(Plugin.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final List<Pulse> pulses = new ArrayList<>();
        final SchedulerServiceImpl service;
        Fixture(DataSource source) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
                var task = mock(BukkitTask.class); pulses.add(new Pulse(call.getArgument(1), task, false, call.getArgument(3))); return task;
            });
            when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
                var task = mock(BukkitTask.class); pulses.add(new Pulse(call.getArgument(1), task, true, call.getArgument(3))); return task;
            });
            service = new SchedulerServiceImpl(plugin, Logger.getAnonymousLogger(), "node-a", null, source, "test_leaders");
        }
        Pulse calendar() { return pulses.stream().filter(p -> !p.async() && p.period() == 1200).findFirst().orElseThrow(); }
        Pulse heartbeat() { return pulses.stream().filter(Pulse::async).findFirst().orElseThrow(); }
        @Override public void close() { service.shutdown().toCompletableFuture().join(); bukkit.close(); }
    }
    private static ScheduleSpec dueNow() {
        var spec = mock(ScheduleSpec.class);
        when(spec.mode()).thenReturn(ScheduleSpec.Mode.DAILY);
        when(spec.nextExecutionAfter(any())).thenReturn(ZonedDateTime.now().minusSeconds(10));
        return spec;
    }

    @Test void idleStartOwnsNoDaemonsAndDoesNotTouchStorage() {
        var source = mock(DataSource.class);
        try (var f = new Fixture(source)) {
            f.service.start(); f.service.start();
            assertTrue(f.pulses.isEmpty()); verifyNoInteractions(source);
        }
    }

    @Test void calendarDemandStartsOneDaemonAndLastCancelStopsIt() {
        try (var f = new Fixture(null)) {
            var count = new AtomicInteger();
            var first = f.service.scheduleCalendar(f.plugin, count::incrementAndGet, dueNow());
            assertTrue(f.pulses.isEmpty()); // Registration before host start is supported.
            f.service.start();
            var second = f.service.scheduleCalendar(f.plugin, count::incrementAndGet, dueNow());
            f.service.start(); assertEquals(1, f.pulses.size());
            f.calendar().run().run(); assertEquals(2, count.get());
            f.calendar().run().run(); assertEquals(2, count.get());
            first.cancel(); verify(f.calendar().handle(), never()).cancel();
            second.cancel(); verify(f.calendar().handle()).cancel();
        }
    }

    @Test void firstLeaderUseInitializesSqliteOnceAndRetriesTransientInitializationFailure() throws Exception {
        var raw = new SQLiteDataSource(); raw.setUrl("jdbc:sqlite:" + directory.resolve("leaders.db"));
        DataSource source = spy(raw);
        doThrow(new SQLException("temporary")).doAnswer(call -> raw.getConnection()).when(source).getConnection();
        try (var f = new Fixture(source)) {
            f.service.start(); verify(source, never()).getConnection();
            var count = new AtomicInteger();
            var leader = f.service.scheduleCalendarLeaderOnly("reward", f.plugin, count::incrementAndGet, dueNow());
            assertEquals(2, f.pulses.size()); verify(source, never()).getConnection();
            f.heartbeat().run().run(); verify(source, times(1)).getConnection();
            f.heartbeat().run().run(); verify(source, times(3)).getConnection(); // DDL + lease.
            f.heartbeat().run().run(); verify(source, times(4)).getConnection(); // Lease only.
            f.calendar().run().run(); assertEquals(1, count.get());
            f.calendar().run().run(); assertEquals(1, count.get());
            leader.cancel(); verify(f.calendar().handle()).cancel(); verify(f.heartbeat().handle()).cancel();
        }
    }

    @Test void cancellingOldLeaderKeepsTheReplacementAndItsDaemons() {
        try (var f = new Fixture(null)) {
            f.service.start();
            var first = f.service.scheduleCalendarLeaderOnly("same", f.plugin, () -> {}, dueNow());
            var replacement = f.service.scheduleCalendarLeaderOnly("same", f.plugin, () -> {}, dueNow());
            first.cancel();
            verify(f.calendar().handle(), never()).cancel(); verify(f.heartbeat().handle(), never()).cancel();
            replacement.cancel(); verify(f.calendar().handle()).cancel(); verify(f.heartbeat().handle()).cancel();
        }
    }

    @Test void dueCalendarCanAcquireItsFirstLeaseWithASingleConnectionPool() {
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + directory.resolve("first-use.db"));
        config.setMaximumPoolSize(1); config.setMinimumIdle(0); config.setConnectionTimeout(1000);
        try (var pool = new com.zaxxer.hikari.HikariDataSource(config); var f = new Fixture(pool)) {
            f.service.start();
            var count = new AtomicInteger();
            f.service.scheduleCalendarLeaderOnly("first", f.plugin, count::incrementAndGet, dueNow());
            f.calendar().run().run(); assertEquals(1, count.get()); // No heartbeat has run yet.
            f.heartbeat().run().run();
            f.calendar().run().run(); assertEquals(1, count.get());
        }
    }

    @Test void intervalLeadersDoNotStartTheCalendarDaemon() {
        try (var f = new Fixture(null)) {
            f.service.start();
            var task = f.service.scheduleCalendarLeaderOnly("interval", f.plugin, () -> {}, ScheduleSpec.everyTicks(20));
            assertEquals(2, f.pulses.size());
            assertTrue(f.pulses.stream().noneMatch(p -> p.period() == 1200));
            task.cancel();
            for (var pulse : f.pulses) verify(pulse.handle()).cancel();
        }
    }

    @Test void shutdownStillWaitsForAnInflightLeaderHeartbeat() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> {
            entered.countDown(); release.await(5, TimeUnit.SECONDS); throw new SQLException("offline");
        });
        var worker = Executors.newSingleThreadExecutor();
        try (var f = new Fixture(source)) {
            try {
                f.service.start(); f.service.scheduleCalendarLeaderOnly("lease", f.plugin, () -> {}, dueNow());
                var work = worker.submit(f.heartbeat().run()); assertTrue(entered.await(5, TimeUnit.SECONDS));
                var stopped = f.service.shutdown().toCompletableFuture(); assertFalse(stopped.isDone());
                release.countDown(); work.get(5, TimeUnit.SECONDS); stopped.get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        } finally { worker.shutdownNow(); }
    }
}
