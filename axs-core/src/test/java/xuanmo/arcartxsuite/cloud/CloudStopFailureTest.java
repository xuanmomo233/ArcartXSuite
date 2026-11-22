package xuanmo.arcartxsuite.cloud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class CloudStopFailureTest {
    @Test
    void aHeartbeatDetachFailureStillWaitsForAcceptedWorkAndClearsTheLocalSecret() throws Exception {
        var service = mock(CloudModuleService.class,CALLS_REAL_METHODS);
        var tasks = new CloudTasks(new CloudTasksTest.Manual());
        var heartbeat = mock(BukkitTask.class);
        doThrow(new IllegalStateException("scheduler detach failed")).when(heartbeat).cancel();
        set(service,"tasks",tasks);set(service,"heartbeatTask",heartbeat);
        set(service,"cachedAxb",new ConcurrentHashMap<>());set(service,"downloading",new ConcurrentHashMap<>());
        byte[] secret = new byte[32];Arrays.fill(secret,(byte)9);set(service,"serverSecret",secret);
        var actual = new CompletableFuture<Boolean>();tasks.request(() -> actual);
        var exited = service.shutdown().toCompletableFuture();
        assertFalse(exited.isDone());
        actual.complete(true);
        assertThrows(java.util.concurrent.ExecutionException.class,()->exited.get(2,TimeUnit.SECONDS));
        assertArrayEquals(new byte[32],secret);
    }

    private void set(Object target,String name,Object value) throws Exception {
        var field=CloudModuleService.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
