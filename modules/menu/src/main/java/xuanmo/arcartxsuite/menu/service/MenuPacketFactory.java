package xuanmo.arcartxsuite.menu.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.menu.config.MenuButtonDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuLayoutType;
import xuanmo.arcartxsuite.menu.config.MenuPageDefinition;

/**
 * 菜单数据包构建工厂，将菜单定义和会话状态组装为发送给客户端 UI 的 payload。
 * <p>
 * 负责收集可见按钮、分页裁剪、图标解析和行列布局计算，生成 UI 引擎所需的数据结构。
 */
public final class MenuPacketFactory {

    private static final Comparator<MenuButtonDefinition> BUTTON_ORDER =
        Comparator.comparingInt(MenuButtonDefinition::order)
            .thenComparing(MenuButtonDefinition::id);

    private MenuPacketFactory() {
    }

    /**
     * 构建发送给客户端 UI 的菜单 payload。
     *
     * @param packetId     数据包 ID
     * @param player       目标玩家
     * @param definition   菜单定义
     * @param session      当前会话
     * @param iconResolver 图标解析器
     * @return 包含菜单元数据、按钮行和底栏行的 payload 映射
     */
    public static Map<String, Object> buildPayload(
        String packetId,
        Player player,
        MenuDefinition definition,
        MenuSession session,
        IconResolver iconResolver
    ) {
        return buildPayload(packetId, player, definition, session,
            new MenuPresentation(iconResolver == null ? (viewer, icon) -> "" : iconResolver::resolveItemJson),
            orderedButtons(definition.pageAt(session.pageIndex()).buttons().values()),
            orderedButtons(definition.footerButtons().values()));
    }

    static List<MenuButtonDefinition> orderedButtons(Collection<MenuButtonDefinition> buttons) {
        return buttons.stream().sorted(BUTTON_ORDER).toList();
    }

    static Map<String, Object> buildPayload(
        String packetId,
        Player player,
        MenuDefinition definition,
        MenuSession session,
        MenuPresentation presentation,
        List<MenuButtonDefinition> orderedButtons,
        List<MenuButtonDefinition> orderedFooters
    ) {
        return buildView(packetId, player, definition, session.pageIndex(), presentation, orderedButtons, orderedFooters).payload();
    }

    static MenuView buildView(
        String packetId,
        Player player,
        MenuDefinition definition,
        int pageIndex,
        MenuPresentation presentation,
        List<MenuButtonDefinition> orderedButtons,
        List<MenuButtonDefinition> orderedFooters
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        MenuPageDefinition page = definition.pageAt(pageIndex);
        payload.put("packetId", packetId);
        payload.put("menuId", definition.id());
        payload.put("layout", definition.layout().configKey());
        payload.put("title", MenuConditionEvaluator.applyPlaceholders(player, definition.title()));
        payload.put("pageId", page.id());
        payload.put("pageTitle", MenuConditionEvaluator.applyPlaceholders(player, page.title()));
        payload.put("pageIndex", pageIndex);
        payload.put("pageCount", definition.pages().size());
        payload.put("columns", definition.columns());
        payload.put("hasPrev", pageIndex > 0);
        payload.put("hasNext", pageIndex + 1 < definition.pages().size());

        List<MenuButtonDefinition> pageButtons =
            collectVisibleButtons(player, orderedButtons, definition.buttonsPerPage());

        LinkedHashMap<String, Object> buttonRows = new LinkedHashMap<>();
        boolean iconsReady = true;
        for (int index = 0; index < pageButtons.size(); index++) {
            MenuButtonDefinition button = pageButtons.get(index);
            Map<String, Object> row = buttonRow(player, button, index, definition.columns(), presentation);
            iconsReady &= iconReady(button, row);
            buttonRows.put(rowKey(index), row);
        }
        payload.put("buttonRows", Collections.unmodifiableMap(buttonRows));
        payload.put("buttonCount", pageButtons.size());

        List<MenuButtonDefinition> visibleFooters =
            collectVisibleButtons(player, orderedFooters, orderedFooters.size());

        LinkedHashMap<String, Object> footerRows = new LinkedHashMap<>();
        int footerIndex = 0;
        for (MenuButtonDefinition footer : visibleFooters) {
            boolean enabled = MenuConditionEvaluator.passes(player, footer.useConditions());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", footer.id());
            row.put("text", MenuConditionEvaluator.applyPlaceholders(player, footer.text()));
            row.put("order", footer.order());
            row.put("clientAction", footer.clientAction());
            row.put("enabled", enabled);
            String footerItemJson = presentation.icon(player, footer.icon());
            row.put("itemJson", footerItemJson);
            row.put("hasIcon", footerItemJson != null && !footerItemJson.isBlank());
            addDetails(player, footer, row, enabled, presentation);
            iconsReady &= iconReady(footer, row);
            footerRows.put(rowKey(footerIndex++), Collections.unmodifiableMap(row));
        }
        payload.put("footerRows", Collections.unmodifiableMap(footerRows));
        payload.put("footerCount", footerRows.size());
        payload.put("maxButtonCount", definition.buttonsPerPage());
        payload.put("maxFooterCount", definition.footerButtons().size());
        return new MenuView(Collections.unmodifiableMap(payload), indexButtons(pageButtons), indexButtons(visibleFooters), iconsReady);
    }

    private static boolean iconReady(MenuButtonDefinition button, Map<String, Object> row) {
        return button.icon() == null || !button.icon().hasIcon() || Boolean.TRUE.equals(row.get("hasIcon"));
    }

    private static Map<String, MenuButtonDefinition> indexButtons(List<MenuButtonDefinition> buttons) {
        Map<String, MenuButtonDefinition> index = new HashMap<>();
        for (MenuButtonDefinition button : buttons) index.put(button.id(), button);
        return Map.copyOf(index);
    }

    private static List<MenuButtonDefinition> collectVisibleButtons(
        Player player,
        List<MenuButtonDefinition> ordered,
        int limit
    ) {
        List<MenuButtonDefinition> result = new ArrayList<>();
        for (MenuButtonDefinition button : ordered) {
            if (result.size() >= limit) break;
            if (!MenuConditionEvaluator.hasPermission(player, button.permission())) {
                continue;
            }
            if (!MenuConditionEvaluator.passes(player, button.viewConditions())) {
                continue;
            }
            result.add(button);
        }
        return result;
    }

    private static Map<String, Object> buttonRow(
        Player player,
        MenuButtonDefinition button,
        int index,
        int columns,
        MenuPresentation presentation
    ) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", button.id());
        row.put("text", MenuConditionEvaluator.applyPlaceholders(player, button.text()));
        row.put("order", button.order());
        row.put("column", index % columns);
        row.put("row", index / columns);
        row.put("enabled", MenuConditionEvaluator.passes(player, button.useConditions()));
        String itemJson = presentation.icon(player, button.icon());
        row.put("itemJson", itemJson);
        row.put("hasIcon", itemJson != null && !itemJson.isBlank());
        addDetails(player, button, row, Boolean.TRUE.equals(row.get("enabled")), presentation);
        return Collections.unmodifiableMap(row);
    }

    private static void addDetails(Player player, MenuButtonDefinition button, Map<String, Object> row, boolean enabled,
                                   MenuPresentation presentation) {
        MenuButtonDefinition.Details details = button.details();
        row.put("summary", MenuConditionEvaluator.applyPlaceholders(player, details.summary()));
        row.put("category", MenuConditionEvaluator.applyPlaceholders(player, details.category()));
        row.put("accent", details.accent());
        row.put("artwork", details.artwork());
        row.put("hasArtwork", !details.artwork().isBlank());
        row.put("statusText", enabled ? "可使用" : "暂不可用");
        row.put("actionHint", enabled
            ? MenuConditionEvaluator.applyPlaceholders(player, details.actionHint()) : "查看上方使用说明");
        LinkedHashMap<String, Object> lines = new LinkedHashMap<>();
        presentation.appendLines(player, lines, details.description(), "&7");
        if (!details.requirements().isEmpty()) {
            presentation.appendLines(player, lines, List.of("", "使用说明"), "&f");
            presentation.appendLines(player, lines, details.requirements(), "&7");
        }
        if (!enabled) {
            String reason = details.disabledReason().isBlank() ? button.denyMessage() : details.disabledReason();
            presentation.appendLines(player, lines, List.of("", reason.isBlank() ? "当前未满足使用条件" : reason), "&c");
        }
        row.put("detailRows", Collections.unmodifiableMap(lines));
        row.put("detailCount", lines.size());
    }

    /**
     * 将索引转换为 UI 引擎使用的行键（纯数字字符串）。
     *
     * @param index 行索引
     * @return 数字字符串键
     */
    public static String rowKey(int index) {
        return Integer.toString(index);
    }

    /**
     * 根据布局类型返回对应的运行时 UI ID。
     *
     * @param layout    布局类型
     * @param panelUiId 面板 UI ID
     * @param escUiId   ESC UI ID
     * @return 对应布局的运行时 UI ID
     */
    public static String runtimeUiId(MenuLayoutType layout, String panelUiId, String escUiId) {
        return layout == MenuLayoutType.ESC ? escUiId : panelUiId;
    }

    record MenuView(Map<String, Object> payload, Map<String, MenuButtonDefinition> buttons,
                    Map<String, MenuButtonDefinition> footers, boolean iconsReady) {}
}
