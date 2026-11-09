package xuanmo.arcartxsuite.market.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TradeCatalogTest {
    private static final String NATIVE_BINDING = "entity:11111111-2222-3333-4444-555555555555";

    @Test
    void loadsAdyeshachBindingsFromExistingCatalogWithoutChangingTheFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("npc-trades.yml");
        String original = """
            shops:
              - id: fixture
                name: Fixture
                enabled: true
                permission: ""
                revision: 1
                npcs: ["ady:merchant", "%s"]
                offers: []
            """.formatted(NATIVE_BINDING);
        Files.writeString(file, original);

        var catalog = new TradeCatalog(directory.toFile());

        assertEquals(List.of("ady:merchant"), catalog.shop("fixture").npcs());
        assertEquals(original, Files.readString(file));
    }

    @Test
    void rejectsSavingNativeBindings(@TempDir Path directory) throws Exception {
        var catalog = new TradeCatalog(directory.toFile());
        var draft = new TradeCatalog.Shop("fixture", "Fixture", true, "", 0,
            List.of(NATIVE_BINDING), List.of());

        assertThrows(IllegalArgumentException.class, () -> catalog.save(draft, 0, null));
    }
}
