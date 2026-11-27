package xuanmo.arcartxsuite.cloud;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.SuiteCoreImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudCustomerModuleSyncTest {
    private static final String CUSTOMER_RELEASE = "2.0.0-local." + "a".repeat(40) + "-suite.0.0-ci.aaaaaaa";

    @Test
    void customerWithoutPublicEquipmentWaitsForThePaidLeaseThenLoadsItsModule() throws Exception {
        var lease = mock(RuntimeAuthorization.class);
        var service = customerService(lease);
        var plugin = mock(SuiteCoreImpl.class);
        set(service, "plugin", plugin);
        service.acceptPublicModuleList("{\"allowedModules\":[]}");
        when(lease.permittedCustomerModules(CUSTOMER_RELEASE)).thenReturn(Set.of());
        var acquisition = new CompletableFuture<Void>();
        doReturn(acquisition).when(service).prepareCustomerModuleAccess(false);
        doReturn(CompletableFuture.completedFuture(null)).when(service).syncModuleFile("abyss");

        var sync = service.syncAuthorizedModules(null);
        verify(service).prepareCustomerModuleAccess(false);
        assertFalse(sync.isDone());
        verify(service, never()).syncModuleFile("abyss");

        when(lease.permittedCustomerModules(CUSTOMER_RELEASE)).thenReturn(Set.of("abyss"));
        acquisition.complete(null);
        sync.join();
        verify(service).syncModuleFile("abyss");
        verify(plugin).consoleInfo("[本地模块] 本次需读取 1 个授权模块: [abyss]");
    }

    @Test
    void publicTokenRefreshCannotClearOrExpandThePaidLeaseScope() throws Exception {
        var lease = mock(RuntimeAuthorization.class);
        var service = customerService(lease);
        when(lease.permittedCustomerModules(CUSTOMER_RELEASE)).thenReturn(Set.of("eventpacket"));

        service.acceptPublicModuleList("{\"allowedModules\":[\"mail\"]}");
        assertEquals(List.of("eventpacket"), service.authorizedModuleIds());
        service.acceptPublicModuleList("{\"allowedModules\":[]}");
        assertEquals(List.of("eventpacket"), service.authorizedModuleIds());
        when(lease.permittedCustomerModules(CUSTOMER_RELEASE)).thenReturn(Set.of());
        assertEquals(List.of(), service.authorizedModuleIds());
    }

    private static CloudModuleService customerService(RuntimeAuthorization lease) throws ReflectiveOperationException {
        var service = mock(CloudModuleService.class, CALLS_REAL_METHODS);
        set(service, "customerRelease", CUSTOMER_RELEASE);
        set(service, "runtimeAuthorization", lease);
        set(service, "plugin", mock(SuiteCoreImpl.class));
        set(service, "allowedModules", List.of());
        set(service, "localModuleSource", true);
        return service;
    }

    private static void set(CloudModuleService service, String name, Object value) throws ReflectiveOperationException {
        var field = CloudModuleService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
