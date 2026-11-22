package xuanmo.arcartxsuite.menu.service;

import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import static org.junit.jupiter.api.Assertions.*;

final class MenuRepeatGridAssertions {

    private MenuRepeatGridAssertions() {}

    static ConfigurationSection assertGrid(
        Map<String, ConfigurationSection> refs, String id, String flow, String rowVariable
    ) {
        ConfigurationSection grid = refs.get(id);
        assertNotNull(grid, id);
        assertEquals("RepeatGrid", grid.getString("type"), id);
        assertEquals("var." + id + "Rows", grid.getString("attribute.items"), id);
        assertEquals("~" + flow, grid.getString("attribute.flow"), id);
        assertEquals(Integer.MAX_VALUE, grid.getInt("attribute.maxSize"), "网格容量必须在控件创建前可用");

        ConfigurationSection children = grid.getConfigurationSection("children");
        assertNotNull(children, id);
        assertEquals(1, children.getKeys(false).size(), "RepeatGrid 只接受一个行模板");
        ConfigurationSection template = children.getConfigurationSection(children.getKeys(false).iterator().next());
        assertNotNull(template);
        assertEquals("Canvas", template.getString("type"));
        assertEquals("self.item", template.getString("attribute.entryKey"));
        assertEquals("var." + rowVariable + "[self.entryKey]", template.getString("attribute.entry"));
        return template;
    }

    static void assertButton(ConfigurationSection template, String action, String iconMap) {
        assertTrue(template.getString("attribute.visible").contains("self.entry != none"));
        assertNull(template.get("attribute.enable"));
        for (String handler : new String[] {"enter", "leave"}) {
            assertFalse(template.getString("action." + handler).contains("Packet.send"),
                "Hover 和离开只更新本地预览");
        }
        String click = template.getString("action.clickLeft");
        assertTrue(click.contains("self.entry != none"));
        assertTrue(click.contains("self.entry['enabled']"));
        assertTrue(click.contains("var.actionToken != none"));
        assertTrue(click.contains("!var.actionBusy"));
        assertTrue(click.contains("Packet.send(var.packetId, '" + action + "', var.actionToken, self.entry['id'])"));
        ConfigurationSection icon = template.getConfigurationSection("children.图标");
        assertNotNull(icon);
        assertEquals("Slot", icon.getString("type"));
        assertTrue(icon.getBoolean("attribute.through"), "图标应将点击交给行按钮");
        String setIcon = icon.getString("attribute.seticon");
        assertTrue(setIcon.contains("var." + iconMap + "[self.parent.entryKey]"));
        assertTrue(setIcon.contains(": var.air"));
    }

    static void assertIndexedRows(String script, String grid, String count) {
        assertTrue(script.contains("if(var." + count + " > 0)"), "空列表不能执行闭区间 range");
        assertTrue(script.contains("range(0, var." + count + " - 1)"));
        assertTrue(script.contains("网格数据.add(网格序号.round())"));
        assertTrue(script.contains("var." + grid + "Rows = 网格数据"));
        assertFalse(script.contains(".copy("), "行由 RepeatGrid 生成");
    }

    static void assertIconRefresh(YamlConfiguration yaml, String grid, String count) {
        String tick = yaml.getString("ui.action.tick");
        assertTrue(tick.contains("self.actions['refreshIcons']()"));
        assertFalse(tick.contains(".copy("));
        String apply = yaml.getString("ui.action.applyPacket");
        String refresh = yaml.getString("ui.action.refreshIcons");
        assertTrue(apply.contains("var." + grid + "IconsDirty = true"));
        assertFalse(apply.contains(".seticon("), "数据包先更新数据，图标等生成的行到齐后刷新");
        int readiness = refresh.indexOf("val." + grid + ".childrenCount() == var." + count);
        assertTrue(readiness >= 0);
        int firstRow = refresh.indexOf("val." + grid + ".get(网格序号.round())", readiness);
        assertTrue(firstRow > readiness);
        assertTrue(refresh.indexOf("网格项['图标'].seticon()", firstRow) > firstRow);
        assertTrue(refresh.contains("var." + grid + "IconsDirty = false"));
    }
}
