package xuanmo.arcartxsuite.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.ModuleDescriptor;

class ModuleReloadOrderTest {
    @Test
    void disabledHardDependencyStopsDependentsBeforeItAndKeepsSoftDependents() {
        var running = List.of(
            ModuleDescriptor.builder("storage").build(),
            ModuleDescriptor.builder("mail").depends(List.of("storage")).build(),
            ModuleDescriptor.builder("quest").depends(List.of("mail")).build(),
            ModuleDescriptor.builder("tab").softDepends(List.of("mail")).build());

        assertEquals(List.of("quest", "mail", "storage"), ModuleRegistry.modulesToStop(running,
            Set.of("mail", "quest", "tab")::contains));
        assertEquals(List.of(), ModuleRegistry.modulesToStop(running, id -> true));
    }
}
