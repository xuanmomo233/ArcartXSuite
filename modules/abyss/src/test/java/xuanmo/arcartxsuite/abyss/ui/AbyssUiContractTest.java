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
    @Test void textClipTargetsCannotRecurseIntoTheSameControl()throws Exception {
        try(var paths=Files.list(resources.resolve("arcartx/ui"))){
            for(Path file:paths.filter(p->p.toString().endsWith(".yml")).toList()){
                var yaml=new YamlConfiguration();yaml.load(file.toFile());
                for(String key:yaml.getKeys(true)){
                    if(!key.endsWith(".attribute.limitControl"))continue;
                    assertNotEquals("self",yaml.getString(key),file+":"+key);
                    if("self.parent".equals(yaml.getString(key))){
                        String control=key.substring(0,key.length()-".attribute.limitControl".length());
                        int child=control.lastIndexOf(".children.");
                        assertTrue(child>0,"Clip target must have a real parent: "+control);
                        String parent=control.substring(0,child);
                        assertEquals("Canvas",yaml.getString(parent+".type"));
                        assertNull(yaml.getString(parent+".attribute.limitControl"),"Clip canvas must not point back to its child");
                    }
                }
            }
        }
    }
    @Test void editorUsesIntegerDictionaryKeysAndChestShowsEveryStackAmount()throws Exception {
        String editor=Files.readString(resources.resolve("arcartx/ui/abyss_design_edit.yml"));
        assertTrue(editor.contains("var.fieldRows['' + fi.round()]"));
        assertTrue(editor.contains("var.groupRows['' + gi.round()]"));
        String chest=Files.readString(resources.resolve("arcartx/ui/abyss_chest.yml"));
        for(int i=0;i<9;i++)assertTrue(chest.contains("var.itemAmount"+i+".round()"));
    }
}
