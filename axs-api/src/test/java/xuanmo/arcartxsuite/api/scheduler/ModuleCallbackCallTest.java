package xuanmo.arcartxsuite.api.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModuleCallbackCallTest {
    @Test
    void runningReturnValueCallsFinishBeforeTheScopeCloses() throws Exception {
        var callback = new ModuleCallback(() -> {});
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var result = CompletableFuture.supplyAsync(() -> callback.call(() -> {
            entered.countDown();
            try { assertTrue(release.await(3,TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new AssertionError(error); }
            return 42;
        },-1));
        assertTrue(entered.await(2,TimeUnit.SECONDS));
        callback.close();
        assertFalse(callback.stopped().toCompletableFuture().isDone());
        var lateCalls = new AtomicInteger();
        assertEquals(-1,callback.call(lateCalls::incrementAndGet,-1));
        release.countDown();
        assertEquals(42,result.get(2,TimeUnit.SECONDS));
        callback.stopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
        assertEquals(0,lateCalls.get());
    }

    @Test
    void anExceptionDoesNotLeaveTheCallCountOutstanding() {
        var callback = new ModuleCallback(() -> {});
        assertThrows(IllegalStateException.class,()->callback.call(()->{throw new IllegalStateException("failed call");},false));
        callback.close();
        assertTrue(callback.stopped().toCompletableFuture().isDone());
    }
}
