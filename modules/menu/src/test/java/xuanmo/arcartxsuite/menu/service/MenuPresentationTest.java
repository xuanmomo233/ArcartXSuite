package xuanmo.arcartxsuite.menu.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.condition.ScriptConditionServices;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.menu.config.MenuButtonDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuLayoutType;

import static org.junit.jupiter.api.Assertions.*;

class MenuPresentationTest {

    @AfterEach
    void resetEvaluator() {
        ScriptConditionServices.reset();
    }

    @Test
    void legacyButtonsReceiveUsableDetailsWithoutChangingActions() throws Exception {
        Map<String, Object> row = firstRow(payload("""
            text: 仓库
            actions: ['command: warehouse open']
            """));
        assertEquals("仓库", row.get("text"));
        assertEquals("浏览功能详情", row.get("summary"));
        assertEquals("功能导航", row.get("category"));
        assertEquals("214,190,143,255", row.get("accent"));
        assertEquals("点击左侧功能进入", row.get("actionHint"));
        assertEquals("可使用", row.get("statusText"));
        assertEquals(false, row.get("hasArtwork"));
        assertTrue(((Number) row.get("detailCount")).intValue() > 0);
        assertFalse(row.containsKey("actions"));
    }

    @Test
    void loadsDetailsAndExpandsPlaceholdersBeforeWrapping() throws Exception {
        installEvaluator(true);
        Map<String, Object> row = firstRow(payload("""
            text: 仓库
            details:
              summary: '{player} 的物资空间'
              category: 物资管理
              accent: '#9BBFC2'
              artwork: menu/warehouse.png
              description:
                - '你好，{player}'
                - '整理你的冒险收获。'
              requirements: ['解锁仓库后使用']
              action-hint: 打开仓库界面
            """));
        assertEquals("Alex 的物资空间", row.get("summary"));
        assertEquals("物资管理", row.get("category"));
        assertEquals("155,191,194,255", row.get("accent"));
        assertEquals("menu/warehouse.png", row.get("artwork"));
        assertEquals(true, row.get("hasArtwork"));
        assertEquals("打开仓库界面", row.get("actionHint"));
        assertTrue(row.get("detailRows").toString().contains("Alex"));
        assertTrue(row.get("detailRows").toString().contains("解锁仓库后使用"));
        assertFalse(row.get("detailRows").toString().contains("{player}"));
    }

    @Test
    void disabledDetailsUseConfiguredReasonAndNeverExposeConditions() throws Exception {
        installEvaluator(false);
        Map<String, Object> row = firstRow(payload("""
            text: 奖励
            use-conditions: ['%secret_expression% >= 5']
            deny-message: 需要达到五级
            """));
        assertEquals(false, row.get("enabled"));
        assertEquals("暂不可用", row.get("statusText"));
        assertTrue(row.get("detailRows").toString().contains("需要达到五级"));
        assertFalse(row.toString().contains("secret_expression"));
        assertEquals("查看上方使用说明", row.get("actionHint"));
    }

    @Test
    void explicitDisabledReasonOverridesDenyMessage() throws Exception {
        installEvaluator(false);
        Map<String, Object> row = firstRow(payload("""
            use-conditions: ['%player_level% >= 5']
            deny-message: 默认拒绝消息
            details:
              disabled-reason: 等级尚未解锁
            """));
        assertTrue(row.get("detailRows").toString().contains("等级尚未解锁"));
        assertFalse(row.get("detailRows").toString().contains("默认拒绝消息"));
    }

    @Test
    void acceptsMultilineDescriptionAndFallsBackFromInvalidAccent() throws Exception {
        Map<String, Object> row = firstRow(payload("""
            details:
              accent: 'invalid expression'
              description: |-
                第一段
                第二段
            """));
        assertEquals("214,190,143,255", row.get("accent"));
        assertTrue(row.get("detailRows").toString().contains("第一段"));
        assertTrue(row.get("detailRows").toString().contains("第二段"));
    }

    @Test
    void wrapsLongTextIntoIndexedRowsWithoutSplittingColorCodesOrUnicode() throws Exception {
        String description = "&a" + "旅途".repeat(70) + "𠀀";
        Map<String, Object> row = firstRow(payload("details:\n  description: '" + description + "'\n"));
        Map<?, ?> lines = (Map<?, ?>) row.get("detailRows");
        assertEquals(lines.size(), row.get("detailCount"));
        assertTrue(lines.size() > 3);
        for (int index = 0; index < lines.size(); index++) {
            Map<?, ?> line = (Map<?, ?>) lines.get(Integer.toString(index));
            assertNotNull(line);
            String text = line.get("text").toString();
            assertFalse(text.endsWith("&"));
            assertFalse(text.endsWith("§"));
            assertTrue(text.codePointCount(0, text.length()) <= 60);
        }
    }

    @Test
    void emptyPagesProduceNoPhantomButtons() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("id: empty\npages: []\n");
        MenuDefinition definition = MenuDefinition.load(yaml, "test", MenuLayoutType.PANEL, 2, 12, Logger.getAnonymousLogger());
        Map<String, Object> result = MenuPacketFactory.buildPayload("AXS_MENU", null, definition,
            new MenuSession("empty", MenuLayoutType.PANEL, "AXS:menu_panel", 0), null);
        assertEquals(0, result.get("buttonCount"));
        assertEquals(Map.of(), result.get("buttonRows"));
    }

    @Test
    void missingButtonSectionStillHasDefaultPresentation() {
        MenuButtonDefinition button = MenuButtonDefinition.load("empty", null, Logger.getAnonymousLogger());
        assertEquals("浏览功能详情", button.details().summary());
    }

    @Test
    void footersShareDetailsAndPermissionHiddenItemsStayOutOfPayload() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            id: test
            pages:
              main:
                buttons:
                  secret:
                    permission: private.menu
                    details:
                      description: 不应下发的内容
            footer-buttons:
              options:
                text: 游戏选项
                client-action: options
                details:
                  summary: 调整游戏体验
                  description: 更改画面和声音设置
              private:
                permission: private.menu
                details:
                  description: 不应下发的内容
            """);
        MenuDefinition definition = MenuDefinition.load(yaml, "test", MenuLayoutType.PANEL, 2, 12, Logger.getAnonymousLogger());
        Map<String, Object> result = MenuPacketFactory.buildPayload("AXS_MENU", null, definition,
            new MenuSession("test", MenuLayoutType.PANEL, "AXS:menu_panel", 0), null);
        assertEquals(0, result.get("buttonCount"));
        assertEquals(1, result.get("footerCount"));
        Map<?, ?> footer = (Map<?, ?>) ((Map<?, ?>) result.get("footerRows")).get("0");
        assertEquals("options", footer.get("clientAction"));
        assertEquals("调整游戏体验", footer.get("summary"));
        assertTrue(footer.get("detailRows").toString().contains("更改画面和声音设置"));
        assertFalse(result.toString().contains("不应下发的内容"));
    }

    @Test
    void noDescriptionOrRequirementsMeansZeroDetailRows() throws Exception {
        Map<String, Object> row = firstRow(payload("details:\n  description: []\n  requirements: []\n"));
        assertEquals(0, row.get("detailCount"));
        assertEquals(Map.of(), row.get("detailRows"));
    }

    @Test
    void stopsCheckingVisibilityOnceTheOrderedPageIsFull() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            id: test
            buttons-per-page: 1
            pages:
              main:
                buttons:
                  later:
                    order: 20
                    permission: hidden.permission
                  first:
                    order: 10
                    text: First
            """);
        Player player = org.mockito.Mockito.mock(Player.class);
        org.mockito.Mockito.when(player.getName()).thenReturn("tester");
        org.mockito.Mockito.when(player.getDisplayName()).thenReturn("tester");
        MenuDefinition definition = MenuDefinition.load(yaml, "test", MenuLayoutType.PANEL, 2, 12,
            Logger.getAnonymousLogger());
        Map<String, Object> result = MenuPacketFactory.buildPayload("AXS_MENU", player, definition,
            new MenuSession("test", MenuLayoutType.PANEL, "menu_panel", 0), null);
        assertEquals("first", firstRow(result).get("id"));
        org.mockito.Mockito.verify(player, org.mockito.Mockito.never()).hasPermission("hidden.permission");
    }

    @Test
    void staticDetailRowsAreSharedWhileDynamicLinesUseTheCurrentPlayer() throws Exception {
        installEvaluator(true);
        var alex = org.mockito.Mockito.mock(Player.class);
        var steve = org.mockito.Mockito.mock(Player.class);
        org.mockito.Mockito.when(alex.getName()).thenReturn("Alex");
        org.mockito.Mockito.when(steve.getName()).thenReturn("Steve");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            id: test
            pages:
              main:
                buttons:
                  visible:
                    details:
                      description: ['静态说明', '你好，{player}']
            """);
        var definition = MenuDefinition.load(yaml, "test", MenuLayoutType.PANEL, 2, 12, Logger.getAnonymousLogger());
        var ordered = MenuPacketFactory.orderedButtons(definition.pageAt(0).buttons().values());
        var presentation = new MenuPresentation((player, icon) -> "");
        var session = new MenuSession("test", MenuLayoutType.PANEL, "menu_panel", 0);
        var first = firstRow(MenuPacketFactory.buildPayload("AXS_MENU", alex, definition, session, presentation, ordered, List.of()));
        var second = firstRow(MenuPacketFactory.buildPayload("AXS_MENU", steve, definition, session, presentation, ordered, List.of()));
        var firstDetails = (Map<?, ?>) first.get("detailRows");
        var secondDetails = (Map<?, ?>) second.get("detailRows");
        assertSame(firstDetails.get("0"), secondDetails.get("0"));
        assertTrue(firstDetails.get("1").toString().contains("Alex"));
        assertTrue(secondDetails.get("1").toString().contains("Steve"));
    }

    @Test
    void sharesOnlySuccessfulStaticIconsAndClearsThemOnReload() {
        var renders = new AtomicInteger();
        var presentation = new MenuPresentation((player, icon) -> "preview-" + renders.incrementAndGet());
        assertEquals("preview-1", presentation.icon(null, IconDefinition.load("DIAMOND")));
        assertEquals("preview-1", presentation.icon(null, IconDefinition.load("DIAMOND")));

        var viewerIcon = new YamlConfiguration();
        viewerIcon.set("material", "DIAMOND");
        viewerIcon.set("name", "{player}");
        var dynamic = IconDefinition.load(viewerIcon);
        assertEquals("preview-2", presentation.icon(null, dynamic));
        assertEquals("preview-3", presentation.icon(null, dynamic));
        var external = new YamlConfiguration();
        external.set("source", "overture");
        external.set("id", "quest-icon");
        assertEquals("preview-4", presentation.icon(null, IconDefinition.load(external)));
        assertEquals("preview-5", presentation.icon(null, IconDefinition.load(external)));

        presentation.clear();
        assertEquals("preview-6", presentation.icon(null, IconDefinition.load("DIAMOND")));

        var attempts = new AtomicInteger();
        var recovering = new MenuPresentation((player, icon) -> attempts.incrementAndGet() == 1 ? "" : "ready");
        assertEquals("", recovering.icon(null, IconDefinition.load("STONE")));
        assertEquals("ready", recovering.icon(null, IconDefinition.load("STONE")));
        assertEquals("ready", recovering.icon(null, IconDefinition.load("STONE")));
        assertEquals(2, attempts.get());
    }

    private Map<String, Object> payload(String buttonYaml) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("id: test\npages:\n  main:\n    title: 功能\n    buttons:\n      entry:\n"
            + buttonYaml.indent(8));
        MenuDefinition definition = MenuDefinition.load(yaml, "test", MenuLayoutType.PANEL, 2, 12, Logger.getAnonymousLogger());
        return MenuPacketFactory.buildPayload("AXS_MENU", null, definition,
            new MenuSession("test", MenuLayoutType.PANEL, "AXS:menu_panel", 0), null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstRow(Map<String, Object> payload) {
        return (Map<String, Object>) ((Map<?, ?>) payload.get("buttonRows")).get("0");
    }

    private void installEvaluator(boolean enabled) {
        ScriptConditionServices.install(new ScriptConditionEvaluator() {
            @Override
            public boolean passes(Player player, List<ScriptCondition> conditions) {
                return conditions.isEmpty() || enabled;
            }

            @Override
            public ScriptCondition firstFailed(Player player, List<ScriptCondition> conditions) {
                return null;
            }

            @Override
            public String applyPlaceholders(Player player, String input) {
                return input.replace("{player}", player == null ? "Alex" : player.getName());
            }
        });
    }
}
