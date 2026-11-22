package xuanmo.arcartxsuite.essentials;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;
import xuanmo.arcartxsuite.essentials.service.EssentialsWork;

class EssentialsRetirementTest {
    @Test void moduleWaitsForProducerAndStorageSeparately() throws Exception {
        var module=new EssentialsModule();var work=new EssentialsWork(null);var scope=mock(ModuleStorage.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var retired=new CompletableFuture<Void>();
        var fence=new AtomicReference<CompletionStage<?>>();when(scope.retired()).thenReturn(retired);
        doAnswer(call->{fence.set(call.getArgument(0));return null;}).when(scope).retireAfter(any());
        for(var pair:new Object[][]{{"work",work},{"storageScope",scope}}){var field=EssentialsModule.class.getDeclaredField((String)pair[0]);field.setAccessible(true);field.set(module,pair[1]);}
        try {
            work.submit(()->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;});assertTrue(entered.await(5,TimeUnit.SECONDS));
            module.stopService();assertFalse(fence.get().toCompletableFuture().isDone());assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
            release.countDown();fence.get().toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());retired.complete(null);module.shutdownCompletion().toCompletableFuture().get(5,TimeUnit.SECONDS);
        }finally{release.countDown();work.close();retired.complete(null);}
    }
}
