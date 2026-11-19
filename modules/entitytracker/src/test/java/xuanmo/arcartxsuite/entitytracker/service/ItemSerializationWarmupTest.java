package xuanmo.arcartxsuite.entitytracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;

class ItemSerializationWarmupTest {
    @Test void preparationDoesNotAccessBukkitServerOrItems() {
        try(var bukkit=mockStatic(Bukkit.class)) {
            assertDoesNotThrow(ItemSerializationWarmup::prepare);
            bukkit.verifyNoInteractions();
        }
    }
}
