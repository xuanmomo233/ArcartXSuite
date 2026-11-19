package xuanmo.arcartxsuite.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;

class SchedulerCancellationTest {
    @Test
    void calendarCancellationImmediatelyReleasesTheCallback() throws Exception {
        var plugin = mock(Plugin.class);
        var scheduler = new SchedulerServiceImpl(plugin, Logger.getAnonymousLogger(), "lab", null, null, "lab_leaders");
        var task = scheduler.scheduleCalendar(plugin, () -> {}, ScheduleSpec.dailyAt(8, 0));
        assertEquals(1, entries(scheduler, "calendarTasks").size());
        task.cancel();
        assertTrue(entries(scheduler, "calendarTasks").isEmpty());
        assertTrue(task.isCancelled());
    }

    @Test
    void cancellingAnOldLeaderDoesNotRemoveTheReplacementWithTheSameKey() throws Exception {
        var plugin = mock(Plugin.class);
        var scheduler = new SchedulerServiceImpl(plugin, Logger.getAnonymousLogger(), "lab", null, null, "lab_leaders");
        var first = scheduler.scheduleCalendarLeaderOnly("lab:reward", plugin, () -> {}, ScheduleSpec.dailyAt(8, 0));
        var next = scheduler.scheduleCalendarLeaderOnly("lab:reward", plugin, () -> {}, ScheduleSpec.dailyAt(8, 0));
        first.cancel();
        assertSame(next, entries(scheduler, "calendarTasks").get("leader:lab:reward"));
        assertSame(next, entries(scheduler, "leaderTasks").get("leader:lab:reward"));
        assertFalse(next.isCancelled());
        next.cancel();
        assertTrue(entries(scheduler, "calendarTasks").isEmpty());
        assertTrue(entries(scheduler, "leaderTasks").isEmpty());
    }

    private static Map<?, ?> entries(SchedulerServiceImpl scheduler, String name) throws Exception {
        var field = SchedulerServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?, ?>) field.get(scheduler);
    }
}
