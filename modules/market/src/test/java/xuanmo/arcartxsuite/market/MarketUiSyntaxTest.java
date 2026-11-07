package xuanmo.arcartxsuite.market;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketUiSyntaxTest {
    @Test
    void bundledMarketScreensAreValidYamlWithControls() throws Exception {
        try (var paths = Files.list(Path.of("src/main/resources/arcartx/ui"))) {
            var screens = paths.filter(path -> path.toString().endsWith(".yml")).toList();
            assertFalse(screens.isEmpty());
            for (Path screen : screens) {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(screen.toFile());
                assertNotNull(yaml.getConfigurationSection("ui"), screen.toString());
                assertNotNull(yaml.getConfigurationSection("controls"), screen.toString());
            }
        }
    }
}
