package xuanmo.arcartxsuite.abyss.ui;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class AbyssUiContractTest {
    Path resources=Path.of("src/main/resources");
    @Test void everyUiParsesAndHasLifecycleAndControls()throws Exception {
        try(var paths=Files.list(resources.resolve("arcartx/ui"))){
            for(Path file:paths.filter(p->p.toString().endsWith(".yml")).toList()){
                var yaml=new YamlConfiguration();yaml.load(file.toFile());
                assertTrue(yaml.isConfigurationSection("ui"),file.toString());
                assertTrue(yaml.isConfigurationSection("controls"),file.toString());
            }
        }
    }
    @Test void browserUsesConfirmationAndDesignerExposesSaveCapabilities()throws Exception {
        String browser=Files.readString(resources.resolve("arcartx/ui/abyss_browser.yml"));
        assertTrue(browser.contains("'open_confirm'"));assertFalse(browser.contains("'enter', var.selectedId"));
        String design=Files.readString(resources.resolve("arcartx/ui/abyss_design.yml"));
        assertTrue(design.contains("packet['worldSaveLabel']"));assertTrue(design.contains("packet['saving']"));
        assertTrue(design.contains("'world', var.actionToken"));
    }
    @Test void structuredRowsCanCarryCompleteNestedOptions()throws Exception {
        String editor=Files.readString(resources.resolve("arcartx/ui/abyss_design_edit.yml"));
        assertTrue(editor.contains("length: 8192"));
        assertTrue(editor.contains("'design_line_set'"));assertTrue(editor.contains("'design_line_add'"));
    }
}
