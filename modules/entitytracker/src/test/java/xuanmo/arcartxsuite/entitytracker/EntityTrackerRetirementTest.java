package xuanmo.arcartxsuite.entitytracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;
import xuanmo.arcartxsuite.entitytracker.scheduler.RankingRewardScheduler;
import xuanmo.arcartxsuite.entitytracker.service.RankingRewardService;

class EntityTrackerRetirementTest {
    @Test void storageRetirementIncludesWorkerEvenWhenRepositoryAlreadyDetached() throws Exception {
        var module = new EntityTrackerModule();
        var service = mock(RankingRewardService.class);
        var scope = mock(ModuleStorage.class);
        var worker = new CompletableFuture<Void>();
        var storage = new CompletableFuture<Void>();
        var captured = new AtomicReference<CompletionStage<?>>();
        when(service.databaseStopped()).thenReturn(worker);
        when(scope.retired()).thenReturn(storage);
        doAnswer(call -> { captured.set(call.getArgument(0)); return null; }).when(scope).retireAfter(any());
        set(module, "rewardService", service);
        set(module, "storageScope", scope);
        module.stopService();
        verify(service).shutdown();
        assertNotNull(captured.get());
        assertFalse(captured.get().toCompletableFuture().isDone());
        assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
        worker.complete(null);
        assertTrue(captured.get().toCompletableFuture().isDone());
        assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
        storage.complete(null);
        module.shutdownCompletion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNull(get(module, "rewardService"));
        assertNull(get(module, "storageScope"));
    }

    @Test void longDelayedRewardTimersDoNotHoldRetirementOrRestartAfterClose() throws Exception {
        var service = mock(RankingRewardService.class);
        var scheduler = new RankingRewardScheduler(service, mock(JavaPlugin.class), Logger.getAnonymousLogger());
        var config = new YamlConfiguration();
        config.set("new-features.ranking-rewards.schedule.weekly.enabled", true);
        config.set("new-features.ranking-rewards.schedule.weekly.day-of-week", "MONDAY");
        config.set("new-features.ranking-rewards.schedule.monthly.enabled", true);
        scheduler.initialize(config);
        assertTrue(scheduler.isWeeklyTaskRunning());
        assertTrue(scheduler.isMonthlyTaskRunning());
        scheduler.shutdown();
        scheduler.databaseStopped().toCompletableFuture().get(5, TimeUnit.SECONDS);
        scheduler.updateSchedule(config);
        assertFalse(scheduler.isWeeklyTaskRunning());
        assertFalse(scheduler.isMonthlyTaskRunning());
        assertThrows(ExecutionException.class, () -> scheduler.manualTriggerWeeklyRewards().get());
        verifyNoInteractions(service);
    }

    static void set(Object target, String name, Object value) throws Exception {
        Field field = EntityTrackerModule.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    static Object get(Object target, String name) throws Exception {
        Field field = EntityTrackerModule.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
