package xuanmo.arcartxsuite.eventpacket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;

class ModuleRetirementTest {
    @Test void hostWaitsForActualWorkerThenStorageRetirement() throws Exception {
        var module=new EventPacketModule();var worker=mock(DurableRules.class);var scope=mock(ModuleStorage.class);
        var stopped=new CompletableFuture<Void>();var retired=new CompletableFuture<Void>();
        var captured=new AtomicReference<CompletionStage<?>>();
        when(worker.stopped()).thenReturn(stopped);when(scope.retired()).thenReturn(retired);
        doAnswer(call->{captured.set(call.getArgument(0));return null;}).when(scope).retireAfter(any());
        for(var pair:new Object[][]{{"durable",worker},{"storageScope",scope}}) {
            var field=EventPacketModule.class.getDeclaredField((String)pair[0]);field.setAccessible(true);field.set(module,pair[1]);
        }
        module.stopService();verify(worker).close();assertFalse(captured.get().toCompletableFuture().isDone());
        assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
        stopped.complete(null);assertTrue(captured.get().toCompletableFuture().isDone());
        assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
        retired.complete(null);module.shutdownCompletion().toCompletableFuture().get(5,TimeUnit.SECONDS);
    }
}
