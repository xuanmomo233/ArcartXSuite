package xuanmo.arcartxsuite.essentials.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EssentialsAliasesTest {
    @Test
    void oldConfigCannotRegisterSortOrAnAliasForIt() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("command-aliases.sort", "sort");
        config.set("command-aliases.oldsort", "  SORT ");
        config.set("command-aliases.Sort", "home");
        config.set("command-aliases.hm", "home");

        assertEquals(Map.of("hm", "home"), EssentialsConfiguration.load(config,
            Logger.getLogger(getClass().getName())).commandAliases());
    }
}
