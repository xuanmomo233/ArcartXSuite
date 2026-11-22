package xuanmo.arcartxsuite.abyss.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseQueueTest {
    @Test void closeReturnsWhileSqlIsBlockedAndDrainsAcceptedWrites() throws Exception {
        var queue = new DatabaseQueue(4, 2, 2);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> slow = () -> {
                entered.countDown();
                assertTrue(release.await(4, TimeUnit.SECONDS));
                return 1;
            };
            var writer = queue.write(slow);
            var reader = queue.read(slow);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var trailingWrite = queue.write(() -> 2);
            var obsoleteRead = queue.read(() -> fail("obsolete read executed"));
            queue.close();
            assertFalse(queue.stopped().toCompletableFuture().isDone());
            assertFalse(trailingWrite.isDone());
            assertTrue(queue.write(() -> 3).isCompletedExceptionally());
            release.countDown();
            queue.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertEquals(1, writer.get());
            assertEquals(1, reader.get());
            assertEquals(2, trailingWrite.get());
            var cancellation = assertThrows(ExecutionException.class, obsoleteRead::get);
            assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, cancellation.getCause());
        } finally {
            release.countDown();
            queue.close();
        }
    }

    @Test void slowReadDoesNotHoldTheWriteQueueAndOverflowNeverRunsOnTheCaller() throws Exception {
        var queue = new DatabaseQueue(2, 2, 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var blocked = queue.read(() -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return 1; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var queued = queue.read(() -> 2);
            var rejected = queue.read(() -> fail("Rejected query must not run"));
            assertThrows(ExecutionException.class, () -> rejected.get(1, TimeUnit.SECONDS));
            assertEquals("Abyss-DB", queue.write(() -> Thread.currentThread().getName()).get(1, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(1, blocked.get(5, TimeUnit.SECONDS));
            assertEquals(2, queued.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            queue.close();
        }
    }

    @Test void writesStayOrderedAndMaintenanceGetsABoundedTurn() throws Exception {
        var queue = new DatabaseQueue(32, 4, 4);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var order = new ArrayList<Integer>();
        try {
            queue.write(() -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var background = queue.maintenance(() -> { order.add(-1); return null; });
            var writes = new ArrayList<java.util.concurrent.CompletableFuture<Integer>>();
            for (int i = 0; i < 16; i++) {
                int value = i;
                writes.add(queue.write(() -> { order.add(value); return value; }));
            }
            release.countDown();
            for (var write : writes) write.get(5, TimeUnit.SECONDS);
            background.get(5, TimeUnit.SECONDS);
            assertTrue(order.indexOf(-1) > 0 && order.indexOf(-1) <= 8, order::toString);
            assertEquals(java.util.stream.IntStream.range(0, 16).boxed().toList(), order.stream().filter(i -> i >= 0).toList());
        } finally {
            release.countDown();
            queue.close();
        }
        assertTrue(queue.write(() -> 1).isCompletedExceptionally());
    }
}
