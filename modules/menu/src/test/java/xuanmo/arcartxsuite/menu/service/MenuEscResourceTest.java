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
        assertTrue(yaml.getString("ui.action.load").contains("var.displayTitle = '菜单'"));
        for (String child : Set.of("分区饰线", "分区饰点")) {
            assertTrue(yaml.getString(PANEL + ".children." + child + ".attribute.visible")
                .startsWith("var.displayTitle != none"));
        }
    }

    @Test
    void alignsProfileAndGridBesideFullHeightRail() throws Exception {
        YamlConfiguration yaml = load();
        ConfigurationSection panel = yaml.getConfigurationSection(PANEL + ".children");
        ConfigurationSection card = panel.getConfigurationSection("玩家信息卡");
        ConfigurationSection strip = panel.getConfigurationSection("侧栏");
        ConfigurationSection grid = panel.getConfigurationSection("网格滚动区");
        assertNotNull(card);
        assertNotNull(strip);
        assertNotNull(grid);
        int cardBottom = card.getInt("attribute.y") + card.getInt("attribute.height");
        assertTrue(cardBottom < grid.getInt("attribute.y"), "资料头部必须位于网格上方");
        assertEquals(0, strip.getInt("attribute.y"), "工具栏从屏幕顶部开始");
        assertEquals("var.viewH", strip.getString("attribute.height"));
        assertTrue(strip.getInt("attribute.x") + strip.getInt("attribute.width") <= card.getInt("attribute.x"),
            "工具栏必须位于资料头部左侧");
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
        assertGrid(refs, "button_rows", "row", "buttonRows");
        assertEquals(4, refs.get("button_rows").getInt("attribute.column"));
        assertGrid(refs, "footer_rows", "row", "footerRows");
        assertEquals(1, refs.get("footer_rows").getInt("attribute.column"));
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
        var button = assertGrid(refs, "button_rows", "row", "buttonRows");
        var footer = assertGrid(refs, "footer_rows", "row", "footerRows");
        assertButton(button, "click", "iconJsons");
        assertButton(footer, "footer", "footerIconJsons");
        for (ConfigurationSection template : new ConfigurationSection[] {button, footer}) {
            assertNull(template.get("children.字形"), "入口不得退化为名称首字");
            assertNull(template.get("children.线绘图标"), "ESC 菜单应完全由 icon 驱动");
            assertTrue(template.getString("action.clickLeft").contains("!var.closing"));
            assertTrue(template.getString("action.enter").contains("preview"));
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
        assertIndexedRows(apply, "button_rows", "buttonCount");
        assertIndexedRows(apply, "footer_rows", "footerCount");
        assertIconRefresh(yaml, "button_rows", "buttonCount");
        assertIconRefresh(yaml, "footer_rows", "footerCount");
        assertTrue(apply.contains("var.iconJsons[key] = entry['hasIcon'] ? entry['itemJson'] : var.air"));
        assertTrue(apply.contains("var.footerIconJsons[key] = entry['hasIcon'] ? entry['itemJson'] : var.air"));
        assertFalse(apply.contains("['菜单按钮'].copy"));
        assertFalse(apply.contains("setItemIcon"));
        assertTrue(apply.contains("var.labels[key]"));
        assertTrue(apply.contains("var.footerLabels[key]"));
        assertFalse(apply.contains("label.charAt(0) :"));
        assertTrue(yaml.getString("ui.action.tick").contains("server.player_yaw"));
        assertFalse(apply.contains(".clear()"));
        assertFalse(yaml.getString("ui.action.tick").contains(".copy("));
        assertTrue(yaml.getString("ui.action.open").contains("Packet.send(var.packetId, 'esc_open')"));
        String closeMenu = yaml.getString("ui.action.closeMenu");
        assertTrue(closeMenu.contains("Camera.clearState()"));
        assertTrue(closeMenu.contains("var.closeAt = Time.currentTime()"));
        assertFalse(closeMenu.contains("async {"));
        String tick = yaml.getString("ui.action.tick");
        assertTrue(tick.contains("Time.currentTime() >= var.closeAt"));
        assertTrue(tick.contains("Camera.setThirdPerson(false)"));
        assertTrue(tick.contains("Screen.close()"));
        assertTrue(yaml.getString("ui.action.open").contains("var.closeAt = none"));
        assertTrue(yaml.getString("ui.action.close").contains("var.closeAt = none"));
        assertTrue(yaml.getString("ui.action.keyPress").contains("'ESCAPE'"));
        assertTrue(yaml.getString("ui.action.keyPress").contains("self.actions['closeMenu']()"));
        String close = yaml.getString("ui.action.close");
        assertTrue(close.contains("Camera.setThirdPerson(false)"));
        assertTrue(close.contains("Camera.clearState()"));
        assertTrue(yaml.getString(PANEL + ".children.侧栏.children.关闭按钮.action.clickLeft")
            .contains("var.menuUi.actions['closeMenu']()"));
    }

    @Test
    void alternatePauseGridSplitsRowsAndRetainsFooterActions() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var stream = getClass().getResourceAsStream("/arcartx/ui/menu_esc_grid.yml")) {
            assertNotNull(stream);
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(yaml.getConfigurationSection("controls"), "UI", refs);
        for (String side : Set.of("left", "right")) {
            String grid = "grid_" + side;
            String footer = "footer_" + side;
            String count = side.equals("left") ? "左数" : "右数";
            String footerCount = side.equals("left") ? "左页脚数" : "右页脚数";
            assertButton(assertGrid(refs, grid, "row", "buttonRows"), "click", "iconJsons");
            assertButton(assertGrid(refs, footer, "column", "footerRows"), "footer", "footerIconJsons");
            assertIconRefresh(yaml, grid, count);
            assertIconRefresh(yaml, footer, footerCount);
            assertEquals(2, refs.get(grid).getInt("attribute.column"));
        }
        String apply = yaml.getString("ui.action.applyPacket");
        assertTrue(apply.contains("i % 4 < 2"));
        assertTrue(apply.contains("i % 2 == 0"));
        assertTrue(apply.contains("if(var.buttonCount > 0)"));
        assertTrue(apply.contains("if(var.footerCount > 0)"));
        assertTrue(apply.contains("val.grid_slider.setDragYRatio(0)"));
        assertTrue(yaml.getString("ui.action.open").contains("var.closeAt = none"));
        assertTrue(yaml.getString("ui.action.tick").contains("Screen.close()"));
    }

    @Test
    void usesOpaquePaperAndIconOnlyEntries() throws Exception {
        YamlConfiguration yaml = load();
        assertEquals("~237,232,220,255", yaml.getString(PANEL + ".children.面板底.attribute.normal"));
        assertTrue(yaml.getStringList("ui.hide").containsAll(Set.of("纯色块HUD", "hotbar", "experience_bar", "scoreboard")));
        assertFalse(yaml.getString("ui.action.load").contains("iconShapes"));
        assertNull(yaml.getConfigurationSection("template"));
        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(yaml.getConfigurationSection("controls"), "UI", refs);
        ConfigurationSection button = refs.get("button_rows").getConfigurationSection("children.菜单按钮");
        assertEquals("~rect", button.getString("children.图标底.attribute.shape"));
        assertTrue(button.getString("children.图标底.effect.stroke.width").contains("self.parent.isHovered()"));
        assertTrue(button.getString("attribute.middleScale").contains("pressAnim"));
        assertTrue(yaml.getString(PANEL + ".children.操作说明.children.说明内容.attribute.texts").contains("var.preview"));
    }

    @Test
    void viewportGeometryKeepsGridAndSliderInsideScreen() throws Exception {
        YamlConfiguration yaml = load();
        ConfigurationSection panel = yaml.getConfigurationSection(PANEL + ".children");
        ConfigurationSection scroll = panel.getConfigurationSection("网格滚动区.attribute");
        ConfigurationSection grid = panel.getConfigurationSection("网格滚动区.children.按钮网格");
        ConfigurationSection cell = grid.getConfigurationSection("children.菜单按钮.attribute");
        ConfigurationSection slider = panel.getConfigurationSection("网格滑块.attribute");
        ConfigurationSection notes = panel.getConfigurationSection("操作说明.attribute");
        int columns = grid.getInt("attribute.column");
        int gridWidth = grid.getInt("attribute.x") + columns * cell.getInt("width")
            + (columns - 1) * grid.getInt("attribute.spaceBetweenX");
        int threeRows = grid.getInt("attribute.y") + 3 * cell.getInt("height")
            + 2 * grid.getInt("attribute.spaceBetweenY");
        assertTrue(gridWidth <= scroll.getInt("width"), "四列格子不得裁切");
        for (double[] size : new double[][] {{1920, 1080}, {1280, 720}, {1024, 768}, {3440, 1440}, {960, 1080}}) {
            double scale = Math.min(size[0] / 1920, size[1] / 1080);
            double viewW = size[0] / scale;
            double viewH = size[1] / scale;
            double viewportHeight = viewHMinus(scroll.getString("height"), viewH);
            double notesY = viewHMinus(notes.getString("y"), viewH);
            assertEquals(size[1], viewH * scale, 0.001);
            assertTrue(viewW >= yaml.getInt(PANEL + ".attribute.width"));
            assertTrue(viewportHeight >= threeRows, "默认九个入口必须完整显示，不裁掉第三行");
            assertTrue(scroll.getInt("y") + viewportHeight < notesY, "说明区不能遮挡网格");
            assertTrue(notesY + notes.getInt("height") <= viewH);
            assertEquals(viewportHeight - slider.getInt("height"), viewHMinus(slider.getString("maxDragY"), viewH));
            assertTrue(slider.getInt("x") + slider.getInt("width") < yaml.getInt(PANEL + ".attribute.width"));
        }
    }

    private double viewHMinus(String expression, double viewH) {
        assertTrue(expression.startsWith("var.viewH - "));
        return viewH - Double.parseDouble(expression.substring("var.viewH - ".length()));
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
                assertTrue(Set.of("Canvas", "HStack", "VStack", "HGrid", "RepeatGrid").contains(nested.getString(only + ".type")));
            }
            if (nested != null) {
                if (node.contains("action.clickLeft")) {
                    for (String child : nested.getKeys(false)) {
                        assertTrue(nested.getBoolean(child + ".attribute.through"),
                            nested.getCurrentPath() + "." + child + " 不得拦截父按钮点击");
                    }
                }
                visit(nested, type, refs);
            }
        }
    }
}
