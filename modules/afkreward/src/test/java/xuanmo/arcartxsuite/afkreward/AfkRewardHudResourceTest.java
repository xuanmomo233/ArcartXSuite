package xuanmo.arcartxsuite.afkreward;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AfkRewardHudResourceTest {
    private static final String ROOT = "controls.自适应全局布局";

    private YamlConfiguration load() throws Exception {
        try (var stream = getClass().getResourceAsStream("/arcartx/ui/afk_reward_hud.yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void resourceUsesAdaptiveLayoutAndResizesFromTheActualViewport() throws Exception {
        var yaml = load();
        assertTrue(yaml.getBoolean("ui.screenScale"));
        assertEquals("Adaptive", yaml.getString(ROOT + ".type"));
        assertEquals("~top_center", yaml.getString(ROOT + ".attribute.point"));
        assertEquals(1920, yaml.getInt(ROOT + ".attribute.width"));
        assertEquals(1080, yaml.getInt(ROOT + ".attribute.height"));
        assertTrue(yaml.getString("ui.action.load").contains("self.actions['resize']()"));
        var resize = yaml.getString("ui.action.resize");
        for (String expression : Set.of("Screen.getWidth()", "Screen.getHeight()", "var.viewW", "var.viewH", "var.offX")) {
            assertTrue(resize.contains(expression), expression);
        }
    }

    @Test
    void layoutTypographyAndAllStaticValReferencesAreValid() throws Exception {
        var yaml = load();
        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(yaml.getConfigurationSection("controls"), "UI", refs);
        var matcher = Pattern.compile("val[.]([\\p{L}_][\\p{L}\\p{N}_]*)").matcher(yaml.saveToString());
        while (matcher.find()) {
            assertTrue(refs.containsKey(matcher.group(1)), "Missing val: " + matcher.group(1));
        }
    }

    private void visit(ConfigurationSection nodes, String parent, Map<String, ConfigurationSection> refs) {
        assertNotNull(nodes);
        for (String key : nodes.getKeys(false)) {
            var node = nodes.getConfigurationSection(key);
            assertNotNull(node);
            var type = node.getString("type");
            assertNotNull(type, node.getCurrentPath());
            var attr = node.getConfigurationSection("attribute");
            assertNotNull(attr, node.getCurrentPath());
            var ref = node.getString("val");
            if (ref != null) assertNull(refs.put(ref, node), "Duplicate val: " + ref);
            if (attr.contains("point")) assertTrue(Set.of("UI", "Canvas", "Adaptive").contains(parent), node.getCurrentPath());
            if (type.equals("Text")) {
                assertTrue(Set.of(36, 48, 54, 64, 72, 81, 100).contains(attr.getInt("fontSize")), node.getCurrentPath());
                assertFalse(attr.contains("width"));
                assertFalse(attr.contains("lineSpace"));
                assertFalse(attr.contains("shadow"));
                assertTrue(attr.getBoolean("through"));
            }
            if (type.equals("VStack")) assertTrue(attr.contains("width"));
            if (type.equals("HStack")) assertTrue(attr.contains("height"));
            if (type.equals("Canvas")) {
                assertTrue(attr.contains("width"));
                assertTrue(attr.contains("height"));
            }
            var children = node.getConfigurationSection("children");
            if (type.equals("Scroll")) {
                assertNotNull(children);
                assertEquals(1, children.getKeys(false).size());
                var only = children.getKeys(false).iterator().next();
                assertTrue(Set.of("Canvas", "HStack", "VStack", "HGrid").contains(children.getString(only + ".type")));
            }
            var actions = node.getConfigurationSection("action");
            if (actions != null) {
                for (String event : Set.of("enter", "leave")) {
                    assertFalse(actions.getString(event, "").contains("Packet.send"));
                }
            }
            if (children != null) visit(children, type, refs);
        }
    }

    @Test
    void hudIsClickThroughHiddenBeforeDataAndUsesOnlyServerTiming() throws Exception {
        var yaml = load();
        assertEquals("AXS:afk_reward_hud", yaml.getString("ui.id"));
        assertTrue(yaml.getBoolean("ui.isHud"));
        assertTrue(yaml.getBoolean("ui.through"));
        assertNull(yaml.get("ui.hide"));
        assertTrue(yaml.getString("ui.action.load").contains("var.visible = false"));
        var update = yaml.getString("ui.packetHandler.update");
        for (String field : Set.of("area", "time", "next", "players", "session_rewards", "session_time", "visible")) assertTrue(update.contains("packet['" + field + "']"));
        assertTrue(update.contains("packet['visible'] == true || packet['visible'] == 'true'"));
        assertNull(yaml.get("ui.action.tick"));
        assertNull(yaml.get("ui.action.seconds"));
        assertFalse(yaml.saveToString().contains("Packet.send"));
        assertEquals("~top_right", yaml.getString(ROOT + ".children.休憩状态.attribute.point"));
        assertEquals("var.visible", yaml.getString(ROOT + ".children.休憩状态.attribute.visible"));
        assertEquals(480, yaml.getInt(ROOT + ".children.休憩状态.attribute.width"));
        assertEquals(224, yaml.getInt(ROOT + ".children.休憩状态.attribute.height"));
    }

    @Test
    void hudStaysNearTheRealRightEdgeOnWideAndTallScreens() {
        for (double[] size : new double[][] {{1920,1080},{1280,720},{1024,768},{3440,1440},{960,1080}}) {
            double scale = Math.min(size[0] / 1920, size[1] / 1080);
            double viewW = size[0] / scale;
            double offX = (viewW - 1920) / 2;
            double right = offX + 1920 + offX - 32;
            assertEquals(size[0] - 32 * scale, right * scale, 0.001);
            assertTrue(right - 480 >= 0);
            assertTrue((132 + 224) * scale < size[1] / 2);
        }
    }
}
