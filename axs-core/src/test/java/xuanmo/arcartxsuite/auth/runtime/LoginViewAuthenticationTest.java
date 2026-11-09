package xuanmo.arcartxsuite.auth.runtime;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginViewAuthenticationTest {
    private HostAuthentication runtime() {
        var runtime=mock(HostAuthentication.class);
        when(runtime.drained()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        return runtime;
    }
    @Test void missingLoginViewNeverConstructsAuthenticationBackend() {
        HostAuthentication ordinary=mock(HostAuthentication.class);
        @SuppressWarnings("unchecked") Supplier<HostAuthentication> backend=mock(Supplier.class);
        var lifecycle=new LoginViewAuthentication(()->ordinary,backend);
        lifecycle.start();
        lifecycle.setLoginViewEnabled(false);
        assertFalse(lifecycle.requiresAdmission());
        verify(ordinary).start();
        verifyNoInteractions(backend);
    }

    @Test void unloadingRestoresOrdinaryAdmissionThroughTheSameCapabilityHandle() {
        HostAuthentication ordinary=runtime(),restored=runtime(),backend=runtime();
        when(backend.requiresAdmission()).thenReturn(true);
        @SuppressWarnings("unchecked") Supplier<HostAuthentication> normalFactory=mock(Supplier.class);
        when(normalFactory.get()).thenReturn(ordinary,restored);
        UUID profile=UUID.randomUUID();
        var admitted=new xuanmo.arcartxsuite.api.auth.AuthSnapshot(profile,UUID.randomUUID(),0,null,
            xuanmo.arcartxsuite.api.auth.AuthSnapshot.Registration.UNKNOWN,
            xuanmo.arcartxsuite.api.auth.AuthSnapshot.Stage.ADMITTED,"server",java.util.Set.of(),"SERVER_ADMITTED");
        when(restored.snapshot(profile)).thenReturn(admitted);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var lifecycle=new LoginViewAuthentication(normalFactory,()->backend);
            lifecycle.start();
            lifecycle.setLoginViewEnabled(true);
            assertTrue(lifecycle.requiresAdmission());
            lifecycle.setLoginViewEnabled(false);
            assertFalse(lifecycle.requiresAdmission());
            assertSame(admitted,lifecycle.snapshot(profile));
            assertTrue(lifecycle.snapshot(profile).admitted());
            assertFalse(lifecycle.snapshot(profile).identityVerified());
            verify(ordinary).close();
            verify(backend).start();
            verify(backend).close();
            verify(restored).start();
        }
    }

    @Test void failedAuthenticationStartupRestoresOrdinaryAdmission() {
        HostAuthentication ordinary=runtime(),restored=runtime(),backend=runtime();
        @SuppressWarnings("unchecked") Supplier<HostAuthentication> normalFactory=mock(Supplier.class);
        when(normalFactory.get()).thenReturn(ordinary,restored);
        doThrow(new IllegalStateException("startup failed")).when(backend).start();
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var lifecycle=new LoginViewAuthentication(normalFactory,()->backend);
            lifecycle.start();
            assertThrows(IllegalStateException.class,()->lifecycle.setLoginViewEnabled(true));
            assertFalse(lifecycle.requiresAdmission());
            verify(backend).close();
            verify(restored).start();
        }
    }
    @Test void intermediateOrdinaryAdmissionCannotLoseAnUnfinishedBackendDrain() {
        HostAuthentication ordinary=runtime(),restored=runtime(),first=runtime(),second=runtime();
        var oldWrites=new java.util.concurrent.CompletableFuture<Void>();
        when(first.drained()).thenReturn(oldWrites);
        @SuppressWarnings("unchecked") Supplier<HostAuthentication> normal=mock(Supplier.class),auth=mock(Supplier.class);
        when(normal.get()).thenReturn(ordinary,restored); when(auth.get()).thenReturn(first,second);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var lifecycle=new LoginViewAuthentication(normal,auth);
            lifecycle.setLoginViewEnabled(true);
            lifecycle.setLoginViewEnabled(false);
            lifecycle.setLoginViewEnabled(true);
            @SuppressWarnings("unchecked") var barrier=org.mockito.ArgumentCaptor.forClass(java.util.concurrent.CompletionStage.class);
            verify(second).awaitPrevious(barrier.capture());
            assertFalse(barrier.getValue().toCompletableFuture().isDone());
            oldWrites.complete(null);
            assertTrue(barrier.getValue().toCompletableFuture().isDone());
        }
    }
}
