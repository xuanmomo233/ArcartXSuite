package xuanmo.arcartxsuite.module;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import static org.junit.jupiter.api.Assertions.*;

class ModuleRevocationsTest {
    private static ModuleDescriptor module(String id, String... dependencies) {
        return ModuleDescriptor.builder(id).name(id).version("1.0.0").mainClass("test.Main")
            .depends(List.of(dependencies)).build();
    }
    @Test void stopsLocalDependentsBeforeTheRevokedProtectedProvider() {
        var descriptors = List.of(module("market"), module("menu", "market"),
            module("questgps", "menu"), module("chat"));
        assertEquals(List.of("questgps", "menu", "market"), ModuleRevocations.order(descriptors, Set.of("market")));
    }
    @Test void preservesUnrelatedAndStillAuthorizedProviders() {
        var descriptors = List.of(module("mail"), module("market", "mail"), module("menu", "market"));
        assertEquals(List.of("menu", "market"), ModuleRevocations.order(descriptors, Set.of("market")));
        assertTrue(ModuleRevocations.order(descriptors, Set.of()).isEmpty());
    }
}
