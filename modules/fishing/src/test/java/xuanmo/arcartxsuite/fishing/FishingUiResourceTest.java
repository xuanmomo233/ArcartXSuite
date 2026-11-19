package xuanmo.arcartxsuite.fishing;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FishingUiResourceTest {
    private static final List<String> PAGES = List.of("main", "collection", "rewards", "talents", "tasks", "baits", "minigame");

    private YamlConfiguration load(String page) throws Exception {
        try (var stream = getClass().getResourceAsStream("/arcartx/ui/fishing_" + page + ".yml")) {
            assertNotNull(stream);
            var yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void allPagesKeepValidControls() throws Exception {
        for (String page : PAGES) {
            var yaml = load(page);
            assertFalse(yaml.getBoolean("ui.isHud"));
            assertFalse(yaml.getBoolean("ui.background"));
            assertEquals(!page.equals("minigame"), yaml.getBoolean("ui.escClose"));
            var refs = new HashMap<String, ConfigurationSection>();
            visit(yaml.getConfigurationSection("controls"), refs);
            String adaptive = "controls.自适应全局布局.attribute";
            assertEquals(1920, yaml.getInt(adaptive + ".width"));
            assertEquals(1080, yaml.getInt(adaptive + ".height"));
            if (!page.equals("minigame")) {
                assertTrue(yaml.getString("ui.action.open").contains("refresh_" + page));
                assertTrue(yaml.getString("ui.action.resize").contains("Screen.getWidth()"));
            }
        }
    }

    @Test
    void zoomAndResizeNeverRebuildGraphOrDecodeIcons() throws Exception {
        var yaml = load("talents");
        var refs = new HashMap<String, ConfigurationSection>();
        visit(yaml.getConfigurationSection("controls"), refs);
        for (String script : List.of(yaml.getString("ui.action.resize"),
                yaml.getString("ui.action.keyPress"), refs.get("天赋视口").getString("action.wheel"))) {
            assertNotNull(script);
            for (String forbidden : List.of("needRebuild = true", ".copy(", ".remove(", "setItemIcon", "iconDelay")) {
                assertFalse(script.contains(forbidden), forbidden);
            }
        }
        assertTrue(yaml.getString("ui.packetHandler.init").contains("var.layoutKey != layoutKey"));
        assertTrue(yaml.getString("ui.action.tick").contains("var.nodeRefs"));
        assertTrue(yaml.getString("ui.action.tick").contains("var.iconJsonCache"));
        assertTrue(refs.get("天赋节点模板").getString("attribute.x").contains("self.worldX * var.zoomScale"));
        assertTrue(refs.get("联动连线段").getString("attribute.width").contains("self.worldLength * var.zoomScale"));
    }

    @Test
    void draggingIsViewportScopedAndCannotPurchaseOnMouseDown() throws Exception {
        var yaml = load("talents");
        var refs = new HashMap<String, ConfigurationSection>();
        visit(yaml.getConfigurationSection("controls"), refs);
        String press = yaml.getString("ui.action.clickLeft");
        assertNotNull(press);
        assertTrue(press.contains("val.天赋视口.isHovered()"));
        assertFalse(press.contains("Packet.send"));
        assertNull(yaml.get("ui.action.click"));
        String release = yaml.getString("ui.action.releaseLeft");
        assertTrue(release.contains("!var.dragMoved"));
        assertTrue(release.contains("var.actionToken"));
        assertTrue(release.contains("canLevelUp"));
        assertTrue(yaml.getString("ui.action.tick").contains("if(var.isDragging && !var.dragMoved)"));
    }

    @Test
    void viewportPansFromLiveCursorAndCommitsTheDisplayedPosition() throws Exception {
        var yaml = load("talents");
        var refs = new HashMap<String, ConfigurationSection>();
        visit(yaml.getConfigurationSection("controls"), refs);
        var viewport = refs.get("天赋视口");
        String release = yaml.getString("ui.action.releaseLeft");
        String tick = yaml.getString("ui.action.tick");
        String wheel = viewport.getString("action.wheel");
        for (String axis : List.of("X", "Y")) {
            String move = viewport.getString("attribute.move" + axis);
            assertTrue(move.contains("var.isDragging && var.dragSpan" + axis + " > 0"));
            assertTrue(move.contains("Mouse." + axis.toLowerCase(java.util.Locale.ROOT) + "()"));
            assertTrue(move.contains("math.max(0, math.min(1,"));
            int commit = release.indexOf("var.pan" + axis + " = val.天赋视口.move" + axis);
            assertTrue(commit >= 0 && commit < release.indexOf("var.isDragging = false"));
            assertTrue(wheel.contains("oldS" + axis + " = self.move" + axis));
            assertFalse(tick.contains("var.pan" + axis + " ="));
        }
        assertFalse(refs.containsKey("横向滑块"));
        assertFalse(refs.containsKey("竖向滑块"));
        assertFalse(refs.get("天赋节点模板").getString("children.节点图标.attribute.normal").contains("Frosted"));
    }

    @Test
    void stateChangingPacketsKeepTheirActionTokens() throws Exception {
        for (String page : List.of("talents", "rewards", "baits")) {
            var yaml = load(page);
            assertTrue(yaml.getString("ui.packetHandler.init").contains("packet['action_token']"));
            for (String path : yaml.getKeys(true)) {
                Object value = yaml.get(path);
                if (!(value instanceof String script)) continue;
                for (String line : script.split("\n")) {
                    if (line.stripLeading().startsWith("//")) continue;
                    if (line.contains("Packet.send") && (line.contains("'claim_reward'")
                            || line.contains("'level_up_talent'") || line.contains("'reset_talents'")
                            || line.contains("'put_bait'") || line.contains("'take_bait'") || line.contains("'swap_bait'"))) {
                        assertTrue(line.contains("var.actionToken"), page + ": " + line);
                    }
                }
            }
        }
    }

    private void visit(ConfigurationSection children, Map<String, ConfigurationSection> refs) {
        assertNotNull(children);
        for (String key : children.getKeys(false)) {
            var node = children.getConfigurationSection(key);
            assertNotNull(node);
            String type = node.getString("type");
            assertNotNull(type, node.getCurrentPath());
            String ref = node.getString("val");
            if (ref != null) assertNull(refs.put(ref, node), ref);
            var attr = node.getConfigurationSection("attribute");
            assertNotNull(attr);
            if ("HStack".equals(type)) assertTrue(attr.contains("height"));
            if ("VStack".equals(type)) assertTrue(attr.contains("width"));
            var nested = node.getConfigurationSection("children");
            if ("Scroll".equals(type)) {
                assertNotNull(nested);
                assertEquals(1, nested.getKeys(false).size());
            }
            if (nested != null) visit(nested, refs);
        }
    }
}
