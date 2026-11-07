package xuanmo.arcartxsuite.auth.runtime;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginViewAuthenticationTest {
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
        HostAuthentication ordinary=mock(HostAuthentication.class),restored=mock(HostAuthentication.class),backend=mock(HostAuthentication.class);
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
        HostAuthentication ordinary=mock(HostAuthentication.class),restored=mock(HostAuthentication.class),backend=mock(HostAuthentication.class);
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
}
