package xuanmo.arcartxsuite.pickup.ui;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PickupUiContractTest {
    @TempDir Path temp;
    @Test void scannerLayoutsCoverEverySupportedSlotAndCallbacksUseUiScope() throws Exception {
        for (String file : List.of("loot_panel.yml","loot_interact.yml")) {
            YamlConfiguration yaml = new YamlConfiguration();
            try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/arcartx/ui/"+file)),StandardCharsets.UTF_8)) { yaml.load(reader); }
            var slots = yaml.getConfigurationSection("controls.自适应布局.children.拾取面板.children.物品列表.children");
            assertNotNull(slots);
            assertEquals(16,slots.getKeys(false).size());
            for(int i=0;i<16;i++) assertEquals("loot_slot_"+i,slots.getString("条目"+i+".children.图标.val"));
            assertFalse(yaml.contains("action"),"菜单回调必须在 ui.action 下");
            if(file.equals("loot_interact.yml")) {
                assertTrue(yaml.getString("ui.action.close").contains("var.actionToken"));
                assertTrue(yaml.getString("ui.action.wheel").contains("scroll_up"));
                for(String key : slots.getKeys(false)) assertTrue(slots.getString(key+".action.clickLeft").contains("Uuid"));
            } else assertTrue(yaml.getBoolean("ui.through"));
            if(file.equals("loot_panel.yml")) {
                assertFalse(yaml.contains("ui.action.wheel"),"常驻清单不应抢占快捷栏滚轮");
                assertFalse(yaml.saveToString().contains("Packet.send("),"常驻清单只展示，快捷键由宿主管理");
            } else {
                assertNotNull(yaml.getString("controls.自适应布局.children.拾取面板.children.批量收取.action.clickLeft"));
                assertEquals("Scroll",yaml.getString("controls.自适应布局.children.拾取面板.children.物品说明.type"));
            }
        }
    }
    @Test void generatedNotificationSupportsExtremeSlotCountsAndGuardedEmptyIcons() throws Exception {
        for(int count : List.of(1,16)) {
            Path file=temp.resolve("hud"+count+".yml");
            PickupHudTemplateWriter.write(file.toFile(),count,1500);
            YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file.toFile());
            assertEquals(count,yaml.getConfigurationSection("controls.自适应全局布局.children.拾取纵向布局.children").getKeys(false).size());
            assertTrue(yaml.getBoolean("ui.through"));
            assertTrue(yaml.getString("ui.action.load").contains("1500"));
            assertFalse(Files.readString(file).contains("getSlotItemStack().getName()"));
        }
    }
    @Test void bundledNotificationMatchesTheGeneratedDefault() throws Exception {
        Path generated=temp.resolve("default.yml");
        PickupHudTemplateWriter.write(generated.toFile(),4,3000);
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/arcartx/ui/pickup_hud.yml"))) {
            String bundled=new String(stream.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n").stripTrailing();
            assertEquals(Files.readString(generated).stripTrailing(),bundled);
        }
    }
    @Test void everyScannerShortcutIsRegisteredByTheHost() throws Exception {
        Path root=Path.of(System.getProperty("suite.root"));
        var host=YamlConfiguration.loadConfiguration(root.resolve("src/main/resources/config.yml").toFile());
        Set<String> names=new HashSet<>();
        var declarations=host.getConfigurationSection("keybinds");
        for(String key:declarations.getKeys(false)) if(declarations.getString(key+".module","").equals("pickup")) names.add(declarations.getString(key+".name"));
        var config=xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration.load(new YamlConfiguration(),java.util.logging.Logger.getAnonymousLogger()).scanner();
        assertTrue(names.containsAll(List.of(config.keybindName(),config.previousKey(),config.nextKey(),config.collectAllKey(),config.detailsKey())));
    }
}
