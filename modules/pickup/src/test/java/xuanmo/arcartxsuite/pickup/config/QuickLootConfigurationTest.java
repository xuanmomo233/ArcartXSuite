package xuanmo.arcartxsuite.pickup.config;

import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuickLootConfigurationTest {
    @Test void rejectsUnknownBatchModeInsteadOfCollectingEverything() {
        var yaml=new YamlConfiguration();yaml.set("scanner.batch.mode","matchng");
        assertThrows(IllegalArgumentException.class,()->PickupModuleConfiguration.load(yaml,Logger.getAnonymousLogger()));
    }
    @Test void rejectsDuplicateKeyIdsIgnoringCase() {
        var yaml=new YamlConfiguration();yaml.set("settings.mode","scanner");
        yaml.set("scanner.keybind.next","axs_pickup_pick");
        assertThrows(IllegalArgumentException.class,()->PickupModuleConfiguration.load(yaml,Logger.getAnonymousLogger()));
    }
}
