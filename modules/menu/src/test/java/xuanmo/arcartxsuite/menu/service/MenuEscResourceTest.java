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

class MenuEscResourceTest {

    private static final String ROOT = "controls.自适应全局布局";
    private static final String PANEL = ROOT + ".children.全屏画布.children.菜单面板";

    private YamlConfiguration load() throws Exception {
        try (var stream = getClass().getResourceAsStream("/arcartx/ui/menu_esc.yml")) {
            assertNotNull(stream);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void replacesPauseScreenWithDesignScaleAndSlidingPanel() throws Exception {
        YamlConfiguration yaml = load();
        assertEquals("暂停界面", yaml.getStringList("ui.match").get(0));
        assertFalse(yaml.getBoolean("ui.escClose"));
        assertEquals("Adaptive", yaml.getString(ROOT + ".type"));
        assertEquals("~top_center", yaml.getString(ROOT + ".attribute.point"));
        assertEquals(1920, yaml.getInt(ROOT + ".attribute.width"));
        assertEquals(1080, yaml.getInt(ROOT + ".attribute.height"));
        assertTrue(yaml.getString("ui.action.load").contains("self.actions['resize']()"));
        String resize = yaml.getString("ui.action.resize");
        assertTrue(resize.contains("Screen.getWidth()"));
        assertTrue(resize.contains("Screen.getHeight()"));
        assertEquals("var.viewH", yaml.getString(PANEL + ".attribute.height"));
        assertEquals(820, yaml.getInt(PANEL + ".attribute.width"));
        assertTrue(yaml.getString(PANEL + ".attribute.x").contains("var.anim.get()"));
    }

    @Test
    void followsGenshinLayoutOrder() throws Exception {
        YamlConfiguration yaml = load();
        ConfigurationSection panel = yaml.getConfigurationSection(PANEL + ".children");
        ConfigurationSection card = panel.getConfigurationSection("玩家信息卡");
        ConfigurationSection strip = panel.getConfigurationSection("侧栏");
        ConfigurationSection grid = panel.getConfigurationSection("网格滚动区");
        assertNotNull(card);
        assertNotNull(strip);
        assertNotNull(grid);
        int cardBottom = card.getInt("attribute.y") + card.getInt("attribute.height");
        assertTrue(cardBottom < strip.getInt("attribute.y"), "信息卡必须位于图标栏与网格上方");
        assertEquals(strip.getInt("attribute.y"), grid.getInt("attribute.y"), "图标栏与网格顶部对齐");
        assertTrue(strip.getInt("attribute.x") + strip.getInt("attribute.width") <= grid.getInt("attribute.x"),
            "图标栏必须位于网格左侧");
        assertEquals("Scroll", grid.getString("type"));
        assertTrue(card.getString("children.头像.attribute.normal").contains("PlayerSkin:"));
        assertEquals("~circle", card.getString("children.头像.attribute.shape"));
        assertEquals("Progress", card.getString("children.经验条.type"));
        assertTrue(card.getString("children.经验条.attribute.progress").contains("server.player_exp"));
        assertTrue(card.getString("children.经验数值.attribute.texts").contains("server.player_exp_to_level"));
        assertTrue(card.getString("children.昵称.attribute.texts").contains("Player.getName()"));

        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(yaml.getConfigurationSection("controls"), "UI", refs);
        assertTrue(refs.keySet().containsAll(Set.of("button_rows", "footer_rows", "grid_slider")));
        assertEquals("HGrid", refs.get("button_rows").getString("type"));
        assertEquals(4, refs.get("button_rows").getInt("attribute.column"));
        assertEquals("VStack", refs.get("footer_rows").getString("type"));
    }

    @Test
    void validatesLayoutAnchorsScrollChildrenAndTypography() throws Exception {
        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(load().getConfigurationSection("controls"), "UI", refs);
    }

    @Test
    void writeActionsKeepTokensAndClientActionsStayLocal() throws Exception {
        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(load().getConfigurationSection("controls"), "UI", refs);
        for (String name : Set.of("button_rows", "footer_rows")) {
            ConfigurationSection children = refs.get(name).getConfigurationSection("children");
            ConfigurationSection button = children.getConfigurationSection(children.getKeys(false).iterator().next());
            assertEquals("Canvas", button.getString("type"));
            assertTrue(button.getString("action.clickLeft").contains("var.actionToken, self.entry['id']"));
            assertTrue(button.getString("action.clickLeft").contains("self.entry['enabled']"));
            assertEquals("0.round()", button.getString("attribute.entryKey"));
            assertNull(button.get("attribute.enable"));
            ConfigurationSection icon = button.getConfigurationSection("children.图标");
            assertEquals("Slot", icon.getString("type"));
            assertTrue(icon.getString("attribute.seticon").contains("self.parent.entryKey"));
            assertTrue(button.getString("children.字形.attribute.visible").contains("!self.parent.entry['hasIcon']"));
        }
        String footerClick = refs.get("footer_rows").getString("children.页脚按钮.action.clickLeft");
        assertTrue(footerClick.contains("Game.options()"));
        assertTrue(footerClick.contains("Game.quit()"));
        assertTrue(footerClick.contains("'footer'"));
        assertTrue(refs.get("button_rows").getString("children.菜单按钮.action.clickLeft").contains("'click'"));
    }

    @Test
    void updatesReuseControlsAndRestoreCameraOnClose() throws Exception {
        YamlConfiguration yaml = load();
        for (String handler : Set.of("init", "update")) {
            assertTrue(yaml.getString("ui.packetHandler." + handler).contains("self.actions['applyPacket']()"));
        }
        String apply = yaml.getString("ui.action.applyPacket");
        assertTrue(apply.contains("if(目标按钮数 > 0)"));
        assertTrue(apply.contains("if(目标页脚数 > 0)"));
        assertTrue(apply.contains("val.button_rows.get(0)"));
        assertTrue(apply.contains("var.按钮引用[i.round()] = mb"));
        assertTrue(apply.contains("var.页脚引用[i.round()] = fb"));
        assertTrue(apply.contains("mb['图标'].seticon()"));
        assertTrue(apply.contains("var.iconJsons[key] = entry['hasIcon'] ? entry['itemJson'] : var.air"));
        assertTrue(apply.contains("var.footerIconJsons[key] = entry['hasIcon'] ? entry['itemJson'] : var.air"));
        assertFalse(apply.contains("['菜单按钮'].copy"));
        assertFalse(apply.contains("setItemIcon"));
        assertTrue(apply.contains("var.glyphs[key]"));
        assertTrue(apply.contains("var.footerGlyphs[key]"));
        assertFalse(apply.contains(".clear()"));
        assertFalse(yaml.getString("ui.action.tick").contains(".copy("));
        assertTrue(yaml.getString("ui.action.open").contains("Packet.send(var.packetId, 'esc_open')"));
        String closeMenu = yaml.getString("ui.action.closeMenu");
        assertTrue(closeMenu.contains("Camera.clearState()"));
        assertTrue(closeMenu.contains("Camera.setThirdPerson(false)"));
        assertTrue(closeMenu.contains("Screen.close()"));
        assertTrue(yaml.getString("ui.action.keyPress").contains("'ESCAPE'"));
        assertTrue(yaml.getString("ui.action.keyPress").contains("self.actions['closeMenu']()"));
        String close = yaml.getString("ui.action.close");
        assertTrue(close.contains("Camera.setThirdPerson(false)"));
        assertTrue(close.contains("Camera.clearState()"));
        assertTrue(yaml.getString(ROOT + ".children.全屏画布.children.关闭按钮.action.clickLeft")
            .contains("var.menuUi.actions['closeMenu']()"));
    }

    @Test
    void viewportGeometryKeepsGridAndSliderInsideScreen() {
        for (double[] size : new double[][] {{1920, 1080}, {1280, 720}, {1024, 768}, {3440, 1440}, {960, 1080}}) {
            double scale = Math.min(size[0] / 1920, size[1] / 1080);
            double viewW = size[0] / scale;
            double viewH = size[1] / scale;
            assertEquals(size[1], viewH * scale, 0.001);
            assertTrue(viewW >= 820, "面板宽度不能超出可视宽度");
            assertTrue(viewH - 370 >= 188, "网格区至少容纳一行磁贴");
            assertTrue(viewH - 442 > 0, "滑块拖动范围必须为正");
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
                assertTrue(Set.of(36, 48, 54, 64, 72, 81, 100).contains(attr.getInt("fontSize")), node.getCurrentPath());
            }
            if ("HStack".equals(type)) assertTrue(attr.contains("height"));
            if ("VStack".equals(type)) assertTrue(attr.contains("width"));
            ConfigurationSection nested = node.getConfigurationSection("children");
            if ("Scroll".equals(type)) {
                assertNotNull(nested);
                assertEquals(1, nested.getKeys(false).size());
                String only = nested.getKeys(false).iterator().next();
                assertTrue(Set.of("Canvas", "HStack", "VStack", "HGrid").contains(nested.getString(only + ".type")));
            }
            if (nested != null) visit(nested, type, refs);
        }
    }
}
