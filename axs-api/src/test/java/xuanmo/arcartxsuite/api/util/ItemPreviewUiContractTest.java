package xuanmo.arcartxsuite.api.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ItemPreviewUiContractTest {
    @ParameterizedTest
    @CsvSource({"abyss,abyss_chest,9", "battlepass,battlepass_rewards,3", "market,market_auction,1",
        "market,market_player_shop,1", "market,market_history,1", "fishing,fishing_rewards,1", "questgps,questgps_menu,1",
        "vanilla,vanilla_crafting,3", "vanilla,vanilla_furnace,3", "onlinerewards,online_rewards_menu,3"})
    void virtualItemSlotsDeclareIndependentQuantitiesAndEmptyState(String module, String ui, int expected) throws Exception {
        Path path = Path.of("..", "modules", module, "src/main/resources/arcartx/ui", ui + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(path.toFile());
        List<ConfigurationSection> slots = new ArrayList<>();
        collect(yaml.getConfigurationSection("controls"), slots);
        assertEquals(expected, slots.size(), ui);
        for (ConfigurationSection slot : slots) {
            assertEquals("~Icon", slot.getString("attribute.slotType"));
            assertEquals("Text", slot.getString("children.数量.type"));
            assertTrue(slot.getBoolean("children.数量.attribute.through"));
            String seticon = slot.getString("attribute.seticon");
            assertTrue(seticon.startsWith("self.setItemIcon("));
            assertFalse(seticon.contains("\n"));
            assertFalse(seticon.contains(".texts"));
            assertTrue(slot.getString("children.数量.attribute.texts").contains(": none"));
        }
    }

    private static void collect(ConfigurationSection section, List<ConfigurationSection> result) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) continue;
            if ("Slot".equals(child.getString("type")) && child.isConfigurationSection("children.数量")) result.add(child);
            ConfigurationSection controls = child.getConfigurationSection("children");
            if (controls != null) collect(controls, result);
        }
    }
}
