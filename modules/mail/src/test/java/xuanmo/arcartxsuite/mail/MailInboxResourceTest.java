package xuanmo.arcartxsuite.mail;

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

class MailInboxResourceTest {
    private static final String ROOT = "controls.自适应全局布局";

    private YamlConfiguration load() throws Exception {
        return load("mail_inbox.yml");
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
    void composeUsesScrollableLetterAndValidLayoutReferences() throws Exception {
        var yaml = load("mail_compose.yml");
        assertEquals("Adaptive", yaml.getString(ROOT + ".type"));
        assertTrue(yaml.getBoolean("ui.screenScale"));
        assertTrue(yaml.getString("ui.action.load").contains("self.actions['resize']()"));
        Map<String, ConfigurationSection> refs = new HashMap<>();
        visit(yaml.getConfigurationSection("controls"), "UI", refs);
        var matcher = Pattern.compile("val[.]([\\p{L}_][\\p{L}\\p{N}_]*)").matcher(yaml.saveToString());
        while (matcher.find()) assertTrue(refs.containsKey(matcher.group(1)), matcher.group(1));
        assertNotNull(refs.get("附件网格"));
        assertNotNull(refs.get("背包网格"));
        assertNotNull(refs.get("信笺滑块"));
    }

    @Test
    void composePreservesAttachmentsPasswordAndSendArgumentOrder() throws Exception {
        var yaml = load("mail_compose.yml");
        String init = yaml.getString("ui.packetHandler.init");
        for (String field : Set.of("action_token", "allow_vault", "attachment_count", "attachment_jsons", "base_fee", "body_max", "is_admin", "item_fee", "max_attachments", "password_panel", "quote_message", "quote_success", "session_id", "subject_max", "total_fee", "total_fee_currency", "total_fee_currency_name")) {
            assertTrue(init.contains("packet['" + field + "']"), field);
        }
        String stage = ROOT + ".children.信笺工作台.children.";
        String inventory = stage + "背包面板.children.背包网格.children.槽位0.action.";
        assertEquals("Packet.send('AXS_MAIL', 'compose-add-attachment', self.entryKey, '1')", yaml.getString(inventory + "clickLeft"));
        assertEquals("Packet.send('AXS_MAIL', 'compose-add-attachment', self.entryKey, 'all')", yaml.getString(inventory + "clickRight"));
        assertEquals("Packet.send('AXS_MAIL', 'compose-remove-attachment', self.entryKey)", yaml.getString(stage + "附件面板.children.槽位网格.children.槽位0.action.clickLeft"));
        assertTrue(yaml.getString(stage + "信笺阅读区.children.信笺滚动.children.信件表单.children.二级密码面板.children.密码确认按钮.action.clickLeft")
            .contains("Packet.send('AXS_MAIL', 'password-unlock', var.passwordText)"));
        var send = yaml.getString(stage + "底部按钮栏.children.发送按钮.action.clickLeft");
        assertTrue(send.contains("if(!var.actionBusy && var.actionToken != '')"));
        assertTrue(send.contains("var.actionBusySeconds = 0"));
        assertTrue(send.contains("Packet.send('AXS_MAIL', 'compose-send', var.sessionId, var.composeRecipient, var.composeSubject, var.composeBody, var.composeVault, var.actionToken)"));
        assertEquals("~Backpack", yaml.getString(stage + "背包面板.children.背包网格.children.槽位0.attribute.slotType"));
        assertTrue(yaml.getBoolean(stage + "背包面板.children.背包网格.children.槽位0.attribute.lock"));
        assertTrue(yaml.getString(stage + "附件面板.children.槽位网格.action.create").contains("slot.entryKey = i.round()"));
        assertTrue(yaml.getString("ui.packetHandler.update").contains("slot.seticon()"));
    }

    @Test
    void composeKeepsInventoryAndScrollingFormInsideSupportedViewports() throws Exception {
        for (double[] size : new double[][] {{1920,1080},{1280,720},{1024,768},{3440,1440},{960,1080}}) {
            double scale = Math.min(size[0] / 1920, size[1] / 1080);
            double width = size[0] / scale;
            double height = size[1] / scale;
            double contentWidth = width - 96;
            double formWidth = contentWidth - 548;
            assertTrue(formWidth >= 1276);
            assertEquals(contentWidth, formWidth + 32 + 516);
            assertTrue(height - 432 >= 648);
            assertTrue(212 + 552 <= height - 312);
        }
        assertTrue(load("mail_compose.yml").getString("ui.action.resize").contains("var.formW = var.contentW - 548"));
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
    void mailPacketFieldsAndAttachmentKeysAreKeptAcrossSameSizeUpdates() throws Exception {
        var yaml = load();
        assertEquals("Packet.send('AXS_MAIL', 'refresh')", yaml.getString("ui.action.open"));
        for (String handler : Set.of("init", "update")) assertEquals("self.actions['applyPacket']()", yaml.getString("ui.packetHandler." + handler));
        var apply = yaml.getString("ui.action.applyPacket");
        for (String field : Set.of("messages", "filter_mode", "total_count", "unread_count", "claimable_count", "selected_id", "selected_subject", "selected_sender_name", "selected_source_text", "selected_status_text", "selected_created_at", "selected_expires_at", "selected_body", "selected_attachment_summary", "selected_attachments", "selected_claimable", "is_admin")) {
            assertTrue(apply.contains("packet['" + field + "']"), field);
        }
        for (String expression : Set.of("var.maxMailCount = var.messages.size()", "if(var.maxMailCount > 1)", "if(var.attachmentCount > 0)", "var.mailRefs[key].remove()", "var.attachmentRefs[key].remove()", "var.attachmentItemJsons = {}", "row.entryKey = key", "row['附件图标'].seticon()", "var.emptyItemJson", "var.confirmMode = ''")) {
            assertTrue(apply.contains(expression), expression);
        }
        assertTrue(apply.indexOf("row.entryKey = key") > apply.indexOf("if(var.attachmentCount != oldAttachmentCount)"));
        assertTrue(yaml.getString("ui.action.seconds").contains("var.actionBusySeconds >= 3"));
    }

    @Test
    void deletesRequireLocalConfirmationAndClaimingChecksClaimability() throws Exception {
        var yaml = load();
        var delete = yaml.getString(ROOT + ".children.信件阅读.children.删除.action.clickLeft");
        var deleteAll = yaml.getString(ROOT + ".children.底部工具.children.批量删除.action.clickLeft");
        assertFalse(delete.contains("Packet.send"));
        assertFalse(deleteAll.contains("Packet.send"));
        assertTrue(delete.contains("var.deleteTarget = var.selectedId"));
        var confirm = yaml.getString(ROOT + ".children.删除确认.children.确认框.children.确认.action.clickLeft");
        assertTrue(confirm.contains("!var.actionBusy"));
        assertTrue(confirm.contains("Packet.send('AXS_MAIL', 'deleteall')"));
        assertTrue(confirm.contains("Packet.send('AXS_MAIL', 'delete', var.deleteTarget)"));
        var claim = yaml.getString(ROOT + ".children.信件阅读.children.领取.action.clickLeft");
        assertTrue(claim.contains("var.selectedClaimable == true"));
        assertTrue(claim.contains("Packet.send('AXS_MAIL', 'claim', var.selectedId)"));
        var redeem = yaml.getString(ROOT + ".children.底部工具.children.兑换.action.clickLeft");
        assertTrue(redeem.contains("var.cdkCode != ''"));
        assertTrue(redeem.contains("Packet.send('AXS_MAIL', 'cdk', var.cdkCode)"));
    }

    @Test
    void readingAndBulkOperationsFitAtAllDesignViewports() {
        for (double[] size : new double[][] {{1920,1080},{1280,720},{1024,768},{3440,1440},{960,1080}}) {
            double scale = Math.min(size[0] / 1920, size[1] / 1080);
            double viewW = size[0] / scale;
            double viewH = size[1] / scale;
            assertEquals(24, 552 - (48 + 480));
            assertTrue(viewW - 600 >= 1320);
            assertEquals(viewW - 48, 552 + viewW - 600, 0.001);
            assertTrue(40 + viewH - 572 <= viewH - 428 - 104);
            assertTrue(viewW - 776 > 384);
        }
    }
}
