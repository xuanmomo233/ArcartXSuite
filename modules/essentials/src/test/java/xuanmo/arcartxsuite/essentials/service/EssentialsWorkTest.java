package xuanmo.arcartxsuite.essentials.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EssentialsWorkTest {
    @Test void retirementDiscardsUnstartedReadsButKeepsAcceptedWrites() throws Exception {
        var work=new EssentialsWork(null);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var reads=new AtomicInteger();var writes=new AtomicInteger();
        try {
            work.submit(()->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;});
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var rejected=new java.util.ArrayList<CompletableFuture<Integer>>();
            for(int i=0;i<64;i++){rejected.add(work.read(reads::incrementAndGet));work.submit(writes::incrementAndGet);}
            work.close();release.countDown();work.stopped().toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertEquals(0,reads.get());assertEquals(64,writes.get());
            for(var result:rejected)assertTrue(result.isCompletedExceptionally());
        }finally{release.countDown();work.close();}
    }

    @Test void fullQueueDrainsAcceptedWorkAndCopiesDoNotCancelIt() throws Exception {
        var work=new EssentialsWork(null);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var sum=new AtomicInteger();
        try {
            work.submit(()->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;});
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(int i=1;i<=256;i++){int value=i;work.submit(()->{sum.addAndGet(value);return null;}).cancel(false);}
            assertThrows(ExecutionException.class,()->work.submit(()->null).get());
            work.close();work.stopped().toCompletableFuture().cancel(false);
            assertFalse(work.stopped().toCompletableFuture().isDone());
            assertThrows(ExecutionException.class,()->work.submit(()->null).get());
            release.countDown();work.stopped().toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertEquals(32896,sum.get());
        }finally{release.countDown();work.close();}
    }

    @Test void externalReadFinishesBeforeRetirementAndNewCallsAreIgnored() throws Exception {
        var work=new EssentialsWork(null);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var reader=Executors.newSingleThreadExecutor();
        try {
            var result=reader.submit(()->work.call(()->{
                entered.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException failure){throw new RuntimeException(failure);}return 42;
            },-1));
            assertTrue(entered.await(5,TimeUnit.SECONDS));work.close();
            assertFalse(work.stopped().toCompletableFuture().isDone());assertEquals(-1,work.call(()->99,-1));
            release.countDown();assertEquals(42,result.get(5,TimeUnit.SECONDS));work.stopped().toCompletableFuture().get(5,TimeUnit.SECONDS);
        }finally{release.countDown();work.close();reader.shutdownNow();}
    }
}
