package xuanmo.arcartxsuite.market;

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

class MarketShopResourceTest {
    private static final String ROOT = "controls.自适应全局布局";

    private YamlConfiguration load() throws Exception {
        return load("market_shop.yml");
    }

    private YamlConfiguration load(String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream("/arcartx/ui/" + resource)) {
            assertNotNull(stream);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void auctionAndRecycleLayoutsKeepValidTypographyAndReferences() throws Exception {
        for (String resource : Set.of("market_auction.yml", "market_recycle.yml")) {
            var yaml = load(resource);
            assertTrue(yaml.getBoolean("ui.screenScale"));
            assertEquals("Adaptive", yaml.getString(ROOT + ".type"));
            assertEquals(1920, yaml.getInt(ROOT + ".attribute.width"));
            assertEquals(1080, yaml.getInt(ROOT + ".attribute.height"));
            assertTrue(yaml.getString("ui.action.load").contains("self.actions['resize']()"));
            Map<String, ConfigurationSection> refs = new HashMap<>();
            visit(yaml.getConfigurationSection("controls"), "UI", refs);
            var matcher = Pattern.compile("val[.]([\\p{L}_][\\p{L}\\p{N}_]*)").matcher(yaml.saveToString());
            while (matcher.find()) assertTrue(refs.containsKey(matcher.group(1)), matcher.group(1));
        }
    }

    @Test
    void auctionKeepsBidCancellationAndDynamicIconContracts() throws Exception {
        var yaml = load("market_auction.yml");
        var update = yaml.getString("ui.packetHandler.update");
        for (String field : Set.of("packetId", "page", "totalPages", "pageText", "currentCategory", "categories", "categoryTexts", "listings", "listingTexts", "maxCategoryCount", "maxListingCount", "action_token")) {
            assertTrue(update.contains("packet['" + field + "']"), field);
        }
        for (String expression : Set.of("var.itemJsons = {}", "packet['itemJson' + key]", "var.categoryRefs[key].remove()", "var.listingRefs[key].remove()", "row.entryKey = i.round()", "if(var.maxListingCount > 0)", "['物品图标'].seticon()")) {
            assertTrue(update.contains(expression), expression);
        }
        String buttons = ROOT + ".children.拍卖画廊.children.物品滚动.children.物品列表.children.拍卖卡片.children.按钮组.children.";
        var bid = yaml.getString(buttons + "出价按钮.action.clickLeft");
        var cancel = yaml.getString(buttons + "取消按钮.action.clickLeft");
        for (String action : Set.of(bid, cancel)) {
            assertTrue(action.contains("if(!var.actionBusy && var.actionToken != '')"));
            assertTrue(action.contains("var.actionBusySeconds = 0"));
        }
        assertTrue(bid.contains("Packet.send(var.packetId, 'auction_bid', self.parent.parent.entry['id'].round(), self.parent.parent.entry['minBid'], var.actionToken)"));
        assertTrue(cancel.contains("Packet.send(var.packetId, 'auction_cancel', self.parent.parent.entry['id'].round(), var.actionToken, var.page, var.currentCategory, var.search)"));
        assertEquals("Packet.send(var.packetId, 'auction_list', '0', 'all', none)", yaml.getString("ui.action.open"));
        assertEquals("~bottom_center", yaml.getString(ROOT + ".children.拍卖画廊.children.页码.attribute.point"));
    }

    @Test
    void recyclingKeepsServerEstimatesAndTokenProtectedSlotActions() throws Exception {
        var yaml = load("market_recycle.yml");
        var update = yaml.getString("ui.packetHandler.update");
        for (String field : Set.of("packetId", "recyclables", "earningsTitle", "earningsTexts", "maxEarningsCount", "multiplier", "maxRecycleCount", "batchConfirm", "action_token")) {
            assertTrue(update.contains("packet['" + field + "']"), field);
        }
        for (String expression : Set.of("var.itemJsons = {}", "packet['recycleItemJson' + key]", "var.recycleRefs[key].remove()", "if(var.maxRecycleCount > 0)", "['物品图标'].seticon()")) {
            assertTrue(update.contains(expression), expression);
        }
        var all = yaml.getString(ROOT + ".children.回收结算.children.一键回收按钮.action.clickLeft");
        assertTrue(all.contains("if(!var.actionBusy && var.actionToken != '' && var.maxRecycleCount > 0)"));
        assertTrue(all.contains("Packet.send(var.packetId, 'recycle_all', var.actionToken)"));
        var source = yaml.saveToString();
        assertTrue(source.contains("Packet.send(var.packetId, 'recycle_single', self.parent.entry['slot'].round(), var.actionToken)"));
        assertEquals("Packet.send(var.packetId, 'recycle_preview')", yaml.getString("ui.action.open"));
    }

    @Test
    void secondaryLayoutsHavePositiveSpaceAcrossSupportedViewports() throws Exception {
        for (double[] size : new double[][] {{1920,1080},{1280,720},{1024,768},{3440,1440},{960,1080}}) {
            double scale = Math.min(size[0] / 1920, size[1] / 1080);
            double width = size[0] / scale;
            double height = size[1] / scale;
            assertTrue(height - 484 >= 596);
            for (int reserved : new int[] {380, 572}) assertTrue((width - reserved - 64 - 16) / 2 >= 624);
            assertTrue(48 + (width - 572) + 24 <= width - 500);
            assertTrue(width - 500 + 452 <= width - 48);
        }
        assertTrue(load("market_auction.yml").getString("ui.action.resize").contains("var.contentW = var.viewW - 380"));
        assertTrue(load("market_recycle.yml").getString("ui.action.resize").contains("var.contentW = var.viewW - 572"));
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
                    if (!type.equals("TextBox") || !event.equals("enter")) {
                        assertFalse(actions.getString(event, "").contains("Packet.send"));
                    }
                }
            }
            if (children != null) visit(children, type, refs);
        }
    }

    @Test
    void selectionIsLocalAndBuyingKeepsTheServerContract() throws Exception {
        var yaml = load();
        String card = ROOT + ".children.商品画廊.children.商品滚动.children.商品列表.children.商品卡片";
        assertEquals("0.round()", yaml.getString(card + ".attribute.entryKey"));
        assertEquals("var.items[self.entryKey]", yaml.getString(card + ".attribute.entry"));
        var select = yaml.getString(card + ".action.clickLeft");
        assertTrue(select.contains("var.selectedItemId = self.entry['key']"));
        assertFalse(select.contains("Packet.send"));
        var buy = yaml.getString(ROOT + ".children.交易详情.children.购买.action.clickLeft");
        assertTrue(buy.contains("!var.actionBusy"));
        assertTrue(buy.contains("var.actionToken != ''"));
        assertTrue(buy.contains("var.selectedItem['stock'] != '0'"));
        assertTrue(buy.contains("Packet.send(var.packetId, 'shop_buy', var.shopId, var.selectedItem['key'], '1', var.actionToken)"));
        assertTrue(buy.contains("var.actionBusySeconds = 0"));
        assertEquals("Packet.send(var.packetId, 'shop_list', '')", yaml.getString("ui.action.open"));
        assertTrue(yaml.getString("ui.action.seconds").contains("var.actionBusySeconds >= 3"));
    }

    @Test
    void updatesHandleEmptyListsFreshIconsAndSelectionByStableItemKey() throws Exception {
        var yaml = load();
        var update = yaml.getString("ui.packetHandler.update");
        for (String field : Set.of("packetId", "shops", "shopTexts", "shopId", "shopName", "items", "action_token", "isAdmin")) {
            assertTrue(update.contains("packet['" + field + "']"), field);
        }
        for (String expression : Set.of("var.itemJsons = {}", "var.shopIconJsons = {}", "packet['shopIconJson' + key]", "packet['shopItemJson' + key]", "var.emptyItemJson", "if(var.maxShopCount > 1)", "if(var.maxItemCount > 1)", "row.entryKey = i.round()", "var.shopRefs[key].remove()", "var.itemRefs[key].remove()", "var.selectedKey = none", "entry['key'] == var.selectedItemId", "self.actions['refreshSelection']()", "['商品图标'].seticon()")) {
            assertTrue(update.contains(expression), expression);
        }
        assertTrue(yaml.getString("ui.action.refreshSelection").contains("val.selected_icon.seticon()"));
        assertFalse(yaml.getString("ui.action.seconds").contains(".copy("));
    }

    @Test
    void geometryKeepsTheRailGridAndCheckoutInsideTheViewport() {
        for (double[] size : new double[][] {{1920,1080},{1280,720},{1024,768},{3440,1440},{960,1080}}) {
            double scale = Math.min(size[0] / 1920, size[1] / 1080);
            double viewW = size[0] / scale;
            double viewH = size[1] / scale;
            double galleryW = viewW - 828;
            double scrollW = galleryW - 56;
            double cardW = (scrollW - 20) / 2;
            assertTrue(cardW >= 508);
            assertEquals(scrollW, cardW * 2 + 20, 0.001);
            assertEquals(24, viewW - 468 - (336 + galleryW), 0.001);
            assertEquals(viewW - 48, viewW - 468 + 420, 0.001);
            assertTrue(120 + viewH - 408 <= viewH - 264);
        }
    }
}
