package xuanmo.arcartxsuite.questgps.chemdah;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.questgps.config.QuestGpsModuleConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class NativeViewConfigurationTest {
    private final Logger logger = Logger.getAnonymousLogger();

    @Test
    void bundledExampleParsesEveryRewardPreviewType() throws Exception {
        var input = getClass().getResourceAsStream("/examples/chemdah-quest.yml");
        assertNotNull(input);
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var yaml = new YamlConfiguration();
            yaml.load(reader);
            var component = yaml.getConfigurationSection("suite_demo_hunt.addon.axs-view");
            assertNotNull(component);
            var rewards = QuestGpsModuleConfiguration.readNativeView(
                "suite_demo_hunt", component, Map.of(), Map.of(), logger
            ).rewards();
            assertEquals(List.of("material", "mythicmobs", "neigeitems", "overture", "mmoitems", "title", "text"),
                rewards.stream().map(QuestGpsModuleConfiguration.RewardPreviewDefinition::type).toList());
            assertEquals("APPLE", rewards.get(0).material());
            assertEquals(2, rewards.get(0).amount());
            assertEquals("MythicStarterSword", rewards.get(1).mythicItemId());
            assertEquals("NeigeStarterArmor", rewards.get(2).neigeItemId());
            assertEquals("OvertureStarterGem", rewards.get(3).overtureItemId());
            assertEquals("SWORD", rewards.get(4).mmoItemType());
            assertEquals("LEGENDARY_SWORD", rewards.get(4).mmoItemId());
            assertEquals("village_guard", rewards.get(5).titleId());
            assertFalse(rewards.get(6).text().isBlank());
        }
    }

    @Test
    void nativeTasksNeedNoDisplayComponent() {
        var view = QuestGpsModuleConfiguration.readNativeView("quest", new MemoryConfiguration(), Map.of(), Map.of(), logger);
        assertTrue(view.enabled());
        assertTrue(view.navigation().enabled());
        assertTrue(view.displayNameOverride().isEmpty());
        assertTrue(view.rewards().isEmpty());
    }

    @Test
    void templateAndTaskComponentsHaveIndependentFieldOverrides() throws Exception {
        var taskYaml = new YamlConfiguration();
        taskYaml.loadFromString("name: 清理僵尸\nsort-order: 3");
        var task = QuestGpsModuleConfiguration.readNativeTask("kills", taskYaml);
        var root = new YamlConfiguration();
        root.loadFromString("name: 讨伐\nallow-abandon: true\nrewards:\n  - type: text\n    text: 完成提示\nnavigation:\n  world: world\n  x: 1\n  y: 64\n  z: 2\n");
        var view = QuestGpsModuleConfiguration.readNativeView("hunt", root, Map.of("kills", task), Map.of(), logger);
        assertEquals("讨伐", view.displayNameOverride());
        assertEquals("清理僵尸", view.task("kills").displayText());
        assertTrue(view.allowAbandon());
        assertEquals(1, view.rewards().size());
        assertEquals(64, view.navigation().point().y());
    }

    @Test
    void aSingleOverrideDoesNotDiscardOtherNativeFields() {
        assertEquals("本地名称", ChemdahViewText.choose("本地名称", "原生名称", "id"));
        assertEquals("原生说明", ChemdahViewText.choose("", "原生说明", "id"));
        assertEquals(List.of("原生说明"), ChemdahViewText.choose(List.of(), List.of("原生说明"), List.of()));
    }

    @Test
    void visibilityAndNavigationCanBeDisabledExplicitly() {
        var root = new MemoryConfiguration();
        root.set("visible", false);
        root.set("navigation.enabled", false);
        var view = QuestGpsModuleConfiguration.readNativeView("quest", root, Map.of(), Map.of(), logger);
        assertFalse(view.enabled());
        assertFalse(view.navigation().enabled());
    }

    @Test
    void malformedDisplayComponentsAreReportedInsteadOfSilentlyIgnored() {
        var root = new MemoryConfiguration();
        root.set("visible", "yes");
        assertThrows(IllegalArgumentException.class, () -> QuestGpsModuleConfiguration.readNativeView("q", root, Map.of(), Map.of(), logger));
        root.set("visible", null);
        root.set("navigation.world", "world");
        root.set("navigation.x", Double.NaN);
        root.set("navigation.y", 64);
        root.set("navigation.z", 0);
        assertThrows(IllegalArgumentException.class, () -> QuestGpsModuleConfiguration.readNativeView("q", root, Map.of(), Map.of(), logger));
        root.set("navigation", null);
        root.set("unknown-key", true);
        assertThrows(IllegalArgumentException.class, () -> QuestGpsModuleConfiguration.readNativeView("q", root, Map.of(), Map.of(), logger));
    }

    @Test
    void wrongNavigationShapeAndTargetOnlySwitchesAreRejected() {
        var root = new MemoryConfiguration();
        root.set("navigation", "world 0 64 0");
        assertThrows(IllegalArgumentException.class, () -> QuestGpsModuleConfiguration.readNativeView("q", root, Map.of(), Map.of(), logger));
        root.set("navigation", null);
        root.set("name", 123);
        assertThrows(IllegalArgumentException.class, () -> QuestGpsModuleConfiguration.readNativeTask("kills", root));
        root.set("name", null);
        root.set("navigation.enabled", false);
        assertThrows(IllegalArgumentException.class, () -> QuestGpsModuleConfiguration.readNativeTask("kills", root));
    }

    @Test
    void nativeCategoriesCanBeUsedByTheGateWithoutRedefiningTheirStyles() {
        var root = new YamlConfiguration();
        root.set("category.fallback.enabled", false);
        root.set("gate.blocked-categories", List.of("日常"));
        var configuration = QuestGpsModuleConfiguration.load(root, logger);
        assertTrue(configuration.gate().blockedCategories().contains("日常"));
    }
}
