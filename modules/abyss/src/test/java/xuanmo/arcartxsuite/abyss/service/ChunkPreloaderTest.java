package xuanmo.arcartxsuite.abyss.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkPreloaderTest {
    @Test void instancesShareTheConcurrencyBudgetAndCancelledJobsStopDispatching() {
        var scheduler = mock(SchedulerAPI.class);
        when(scheduler.scheduleAtFixedRate(isNull(), any(), anyLong(), anyLong())).thenReturn(mock(ScheduledTask.class));
        var loader = new ChunkPreloader(scheduler, null, 2, 3);
        var pending = new ArrayList<CompletableFuture<Void>>();
        var owners = new ArrayList<String>();
        var valid = new AtomicBoolean(true);
        var chunks = List.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{2, 0}, new int[]{3, 0});
        var first = loader.submit(chunks, chunk -> {
            owners.add("first");
            var future = new CompletableFuture<Void>(); pending.add(future); return future;
        }, valid::get);
        var second = loader.submit(chunks, chunk -> {
            owners.add("second");
            var future = new CompletableFuture<Void>(); pending.add(future); return future;
        }, () -> true);
        try {
            for (int i = 0; i < 10; i++) loader.tick();
            assertEquals(3, pending.size());
            assertTrue(owners.contains("first") && owners.contains("second"), owners::toString);
            valid.set(false);
            loader.tick();
            assertTrue(first.isCompletedExceptionally());
            int firstRequests = (int) owners.stream().filter("first"::equals).count();
            for (int i = 0; i < 20 && !second.isDone(); i++) {
                List.copyOf(pending).forEach(future -> future.complete(null));
                loader.tick();
            }
            assertTrue(second.isDone());
            assertFalse(second.isCompletedExceptionally());
            assertEquals(firstRequests, owners.stream().filter("first"::equals).count());
        } finally { loader.close(); }
        assertTrue(loader.submit(chunks, chunk -> fail("Closed"), () -> true).isCompletedExceptionally());
    }
}
