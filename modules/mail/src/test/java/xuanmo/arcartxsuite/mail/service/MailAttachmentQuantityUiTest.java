package xuanmo.arcartxsuite.mail.service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MailAttachmentQuantityUiTest {
    @Test
    void eachMailScreenUsesIndependentClickThroughQuantityText() throws Exception {
        for (String file : List.of("mail_inbox", "mail_compose", "mail_admin")) {
            YamlConfiguration yaml = load(file);
            List<ConfigurationSection> slots = new ArrayList<>();
            collectQuantitySlots(yaml.getConfigurationSection("controls"), slots);
            assertEquals(1, slots.size(), file);
            ConfigurationSection slot = slots.get(0);
            assertEquals("~Icon", slot.getString("attribute.slotType"));
            assertEquals("Text", slot.getString("children.数量.type"));
            assertTrue(slot.getBoolean("children.数量.attribute.through"));
            assertTrue(slot.getString("children.数量.attribute.texts").contains(".round()"));
            String seticon = slot.getString("attribute.seticon");
            assertTrue(seticon.startsWith("self.setItemIcon("));
            assertFalse(seticon.contains("\n"));
            assertFalse(seticon.contains(".texts"));
            assertTrue(slot.getString("children.数量.attribute.texts").contains(": none"));
        }
    }

    @Test
    void composeAndAdminReceiveQuantitiesForInitialAndIncrementalUpdates() throws Exception {
        YamlConfiguration compose = load("mail_compose");
        for (String handler : List.of("init", "update")) {
            assertTrue(compose.getString("ui.packetHandler." + handler).contains("packet['attachment_amounts']"));
        }
        YamlConfiguration admin = load("mail_admin");
        for (String handler : List.of("preset-detail", "attachment-count")) {
            assertTrue(admin.getString("ui.packetHandler." + handler).contains("packet['attachment_amounts']"));
        }
    }

    private static YamlConfiguration load(String name) throws Exception {
        try (var stream = MailAttachmentQuantityUiTest.class.getResourceAsStream("/arcartx/ui/" + name + ".yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    private static void collectQuantitySlots(ConfigurationSection section, List<ConfigurationSection> result) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection control = section.getConfigurationSection(key);
            if (control == null) continue;
            if ("Slot".equals(control.getString("type")) && control.isConfigurationSection("children.数量")) {
                result.add(control);
            }
            ConfigurationSection children = control.getConfigurationSection("children");
            if (children != null) collectQuantitySlots(children, result);
        }
    }
}
