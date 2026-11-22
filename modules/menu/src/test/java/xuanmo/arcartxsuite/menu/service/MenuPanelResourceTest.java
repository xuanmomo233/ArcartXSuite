package xuanmo.arcartxsuite.menu.service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static xuanmo.arcartxsuite.menu.service.MenuRepeatGridAssertions.*;

class MenuPanelResourceTest {

    private YamlConfiguration load() throws Exception {
        try (var stream = getClass().getResourceAsStream("/arcartx/ui/menu_panel.yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void fullscreenUsesDesignScaleAndRecomputesViewportOnResize() throws Exception {
        YamlConfiguration yaml = load();
        String root = "controls.自适应全局布局";
        assertEquals("Adaptive", yaml.getString(root + ".type"));
        assertEquals("~top_center", yaml.getString(root + ".attribute.point"));
        assertEquals(1920, yaml.getInt(root + ".attribute.width"));
        assertEquals(1080, yaml.getInt(root + ".attribute.height"));
        assertTrue(yaml.getString("ui.action.load").contains("self.actions['resize']()"));
        String resize = yaml.getString("ui.action.resize");
        assertTrue(resize.contains("Screen.getWidth()"));
        assertTrue(resize.contains("Screen.getHeight()"));
        String panel = root + ".children.全屏画布.children.详情面板";
        assertEquals("var.viewH", yaml.getString(panel + ".attribute.height"));
        assertEquals("var.rightW", yaml.getString(panel + ".attribute.width"));
    }

    @Test
    void validatesLayoutAnchorsScrollChildrenAndTypography() throws Exception {
        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(load().getConfigurationSection("controls"), "UI", refs);
        assertTrue(refs.keySet().containsAll(Set.of("button_rows", "footer_rows", "detail_rows",
            "button_slider", "footer_slider", "detail_slider", "detail_icon")));
        assertGrid(refs, "footer_rows", "column", "footerRows");
        assertGrid(refs, "button_rows", "row", "buttonRows");
        assertGrid(refs, "detail_rows", "row", "detailRows");
        assertEquals(1, refs.get("footer_rows").getInt("attribute.row"));
        assertEquals(1, refs.get("button_rows").getInt("attribute.column"));
    }

    @Test
    void hoverOnlyPreviewsAndWriteActionsKeepTokens() throws Exception {
        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(load().getConfigurationSection("controls"), "UI", refs);
        assertButton(assertGrid(refs, "button_rows", "row", "buttonRows"), "click", "iconJsons");
        assertButton(assertGrid(refs, "footer_rows", "column", "footerRows"), "footer", "footerIconJsons");
    }

    @Test
    void updatesReuseControlsAndPreserveSelectionById() throws Exception {
        YamlConfiguration yaml = load();
        for (String handler : Set.of("init", "update")) {
            assertTrue(yaml.getString("ui.packetHandler." + handler).contains("self.actions['applyPacket']()"));
        }
        String apply = yaml.getString("ui.action.applyPacket");
        assertTrue(apply.contains("var.selectedId == entry['id']"));
        assertIndexedRows(apply, "button_rows", "buttonCount");
        assertIndexedRows(apply, "footer_rows", "footerCount");
        assertIconRefresh(yaml, "button_rows", "buttonCount");
        assertIconRefresh(yaml, "footer_rows", "footerCount");
        assertTrue(apply.contains("var.iconJsons[key] = entry['hasIcon'] ? entry['itemJson'] : var.air"));
        assertTrue(apply.contains("var.footerIconJsons[key] = entry['hasIcon'] ? entry['itemJson'] : var.air"));
        assertFalse(apply.contains("['菜单按钮'].copy"));
        assertFalse(apply.contains("setItemIcon"));
        assertFalse(apply.contains(".clear()"));
        String refresh = yaml.getString("ui.action.refreshDetails");
        assertTrue(refresh.contains(": var.air"));
        assertIndexedRows(refresh, "detail_rows", "detailCount");
        assertTrue(apply.contains("previousId != var.selectedId"));
        assertTrue(apply.contains("previousFooter != var.selectedFooter"));
        assertTrue(apply.contains("val.detail_slider.setDragYRatio(0)"));
        assertTrue(refresh.indexOf("iconJson = var.air") < refresh.indexOf("if(var.selected != none)"));
        assertTrue(refresh.indexOf("var.detailRows = var.selected['detailRows']")
            > refresh.indexOf("if(var.selected != none)"));
        assertFalse(yaml.getString("ui.action.tick").contains(".copy("));
    }

    @Test
    void viewportGeometryKeepsBothPanelsInsideScreen() {
        for (double[] size : new double[][] {{1920, 1080}, {1280, 720}, {1024, 768}, {3440, 1440}, {960, 1080}}) {
            double scale = Math.min(size[0] / 1920, size[1] / 1080);
            double viewW = size[0] / scale;
            double viewH = size[1] / scale;
            double leftW = viewW * 0.64;
            double rightW = viewW - leftW;
            assertEquals(size[0], (leftW + rightW) * scale, 0.001);
            assertEquals(size[1], viewH * scale, 0.001);
            assertTrue(leftW - 434 > 0);
            assertTrue(viewH - 808 > 0);
            assertTrue(rightW - 112 >= 579);
        }
    }

    private void visit(ConfigurationSection children, String parentType, Map<String, ConfigurationSection> refs) {
        for (String key : children.getKeys(false)) {
            ConfigurationSection node = children.getConfigurationSection(key);
            assertNotNull(node);
            String type = node.getString("type");
            assertNotNull(type, node.getCurrentPath());
            String ref = node.getString("val");
            if (ref != null) {
                assertNull(refs.put(ref, node), "Duplicate val: " + ref);
            }
            ConfigurationSection attr = node.getConfigurationSection("attribute");
            assertNotNull(attr);
            if (attr.contains("point")) {
                assertTrue(Set.of("UI", "Canvas", "Adaptive").contains(parentType), node.getCurrentPath());
            }
            if ("Text".equals(type)) {
                assertFalse(attr.contains("width"), node.getCurrentPath());
                assertFalse(attr.contains("lineSpace"));
                assertFalse(attr.contains("shadow"));
                assertTrue(Set.of(36, 48, 54, 64, 72, 81, 100).contains(attr.getInt("fontSize")));
            }
            if ("HStack".equals(type)) assertTrue(attr.contains("height"));
            if ("VStack".equals(type)) assertTrue(attr.contains("width"));
            ConfigurationSection nested = node.getConfigurationSection("children");
            if ("Scroll".equals(type)) {
                assertNotNull(nested);
                assertEquals(1, nested.getKeys(false).size());
                String only = nested.getKeys(false).iterator().next();
                assertTrue(Set.of("Canvas", "HStack", "VStack", "HGrid", "RepeatGrid").contains(nested.getString(only + ".type")));
            }
            if (nested != null) visit(nested, type, refs);
        }
    }
}
