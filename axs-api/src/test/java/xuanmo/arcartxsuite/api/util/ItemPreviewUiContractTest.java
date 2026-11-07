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
        "vanilla,vanilla_crafting,4", "vanilla,vanilla_furnace,5", "onlinerewards,online_rewards_menu,3"})
    void virtualItemSlotsDeclareIndependentQuantitiesAndEmptyState(String module, String ui, int expected) throws Exception {
        Path path = Path.of("..", "modules", module, "src/main/resources/arcartx/ui", ui + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(path.toFile());
        if ("vanilla".equals(module)) {
            verifyVanillaPreviewLayout(yaml, expected);
            return;
        }
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
            String text = slot.getString("children.数量.attribute.texts");
            String visible = slot.getString("children.数量.attribute.visible", "");
            assertTrue(text.contains(": none")
                || visible.matches("var\\.itemAmount\\d+ > 1") && text.contains(visible.substring(0, visible.indexOf(' '))));
        }
    }

    private static void verifyVanillaPreviewLayout(YamlConfiguration yaml, int expectedIcons) {
        List<ConfigurationSection> controls = new ArrayList<>();
        collectControls(yaml.getConfigurationSection("controls"), controls);
        assertEquals(expectedIcons, controls.stream().filter(control -> "Slot".equals(control.getString("type"))
            && "~Icon".equals(control.getString("attribute.slotType"))).count());
        assertTrue(controls.stream().anyMatch(control -> "Text".equals(control.getString("type"))
            && control.getString("attribute.texts", "").contains("resultAmount")
            && control.getString("attribute.texts", "").contains("? none :")
            && control.getBoolean("attribute.through")), "配方产出数量必须独立显示并处理空选择");
        assertTrue(controls.stream().anyMatch(control -> "Text".equals(control.getString("type"))
            && control.getString("attribute.texts", "").contains("['amount']")
            && control.getString("attribute.texts", "").contains("== none ? none")
            && control.getBoolean("attribute.through")), "仓库数量必须独立显示并处理空条目");
        String scripts = yaml.getConfigurationSection("ui").getValues(true).values().toString();
        assertTrue(scripts.contains("setItemIcon('')"), "无效配方必须清除旧图标");
        assertTrue(scripts.contains("row == none ? '' : row['icon']"), "空仓库条目必须清除旧图标");
    }

    private static void collectControls(ConfigurationSection section, List<ConfigurationSection> result) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) continue;
            result.add(child);
            ConfigurationSection children = child.getConfigurationSection("children");
            if (children != null) collectControls(children, result);
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
