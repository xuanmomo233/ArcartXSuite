package xuanmo.arcartxsuite.menu.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
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
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        MenuPageDefinition page = definition.pageAt(session.pageIndex());
        payload.put("packetId", packetId);
        payload.put("menuId", definition.id());
        payload.put("layout", definition.layout().configKey());
        payload.put("title", MenuConditionEvaluator.applyPlaceholders(player, definition.title()));
        payload.put("pageId", page.id());
        payload.put("pageTitle", MenuConditionEvaluator.applyPlaceholders(player, page.title()));
        payload.put("pageIndex", session.pageIndex());
        payload.put("pageCount", definition.pages().size());
        payload.put("columns", definition.columns());
        payload.put("hasPrev", session.pageIndex() > 0);
        payload.put("hasNext", session.pageIndex() + 1 < definition.pages().size());

        List<MenuButtonDefinition> visibleButtons = collectVisibleButtons(player, definition, page);
        int pageSize = definition.buttonsPerPage();
        int fromIndex = 0;
        int toIndex = Math.min(pageSize, visibleButtons.size());
        List<MenuButtonDefinition> pageButtons = visibleButtons.subList(fromIndex, toIndex);

        LinkedHashMap<String, Object> buttonRows = new LinkedHashMap<>();
        for (int index = 0; index < pageButtons.size(); index++) {
            MenuButtonDefinition button = pageButtons.get(index);
            buttonRows.put(rowKey(index), buttonRow(player, button, index, definition.columns(), iconResolver));
        }
        payload.put("buttonRows", buttonRows);
        payload.put("buttonCount", pageButtons.size());

        List<MenuButtonDefinition> visibleFooters = new ArrayList<>();
        for (MenuButtonDefinition footer : definition.footerButtons().values()) {
            if (!MenuConditionEvaluator.hasPermission(player, footer.permission())
                || !MenuConditionEvaluator.passes(player, footer.viewConditions())) {
                continue;
            }
            visibleFooters.add(footer);
        }
        visibleFooters.sort(BUTTON_ORDER);

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
            String footerItemJson = iconResolver == null ? "" : iconResolver.resolveItemJson(player, footer.icon());
            row.put("itemJson", footerItemJson);
            row.put("hasIcon", footerItemJson != null && !footerItemJson.isBlank());
            addDetails(player, footer, row, enabled);
            footerRows.put(rowKey(footerIndex++), row);
        }
        payload.put("footerRows", footerRows);
        payload.put("footerCount", footerRows.size());
        payload.put("maxButtonCount", definition.buttonsPerPage());
        payload.put("maxFooterCount", definition.footerButtons().size());
        return payload;
    }

    private static List<MenuButtonDefinition> collectVisibleButtons(
        Player player,
        MenuDefinition definition,
        MenuPageDefinition page
    ) {
        List<MenuButtonDefinition> result = new ArrayList<>();
        for (MenuButtonDefinition button : page.buttons().values()) {
            if (!MenuConditionEvaluator.hasPermission(player, button.permission())) {
                continue;
            }
            if (!MenuConditionEvaluator.passes(player, button.viewConditions())) {
                continue;
            }
            result.add(button);
        }
        result.sort(BUTTON_ORDER);
        return result;
    }

    private static Map<String, Object> buttonRow(
        Player player,
        MenuButtonDefinition button,
        int index,
        int columns,
        IconResolver iconResolver
    ) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", button.id());
        row.put("text", MenuConditionEvaluator.applyPlaceholders(player, button.text()));
        row.put("order", button.order());
        row.put("column", index % columns);
        row.put("row", index / columns);
        row.put("enabled", MenuConditionEvaluator.passes(player, button.useConditions()));
        String itemJson = iconResolver == null ? "" : iconResolver.resolveItemJson(player, button.icon());
        row.put("itemJson", itemJson);
        row.put("hasIcon", itemJson != null && !itemJson.isBlank());
        addDetails(player, button, row, Boolean.TRUE.equals(row.get("enabled")));
        return row;
    }

    private static void addDetails(Player player, MenuButtonDefinition button, Map<String, Object> row, boolean enabled) {
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
        appendLines(player, lines, details.description(), "&7");
        if (!details.requirements().isEmpty()) {
            appendLines(player, lines, List.of("", "使用说明"), "&f");
            appendLines(player, lines, details.requirements(), "&7");
        }
        if (!enabled) {
            String reason = details.disabledReason().isBlank() ? button.denyMessage() : details.disabledReason();
            appendLines(player, lines, List.of("", reason.isBlank() ? "当前未满足使用条件" : reason), "&c");
        }
        row.put("detailRows", lines);
        row.put("detailCount", lines.size());
    }

    private static void appendLines(Player player, Map<String, Object> rows, List<String> paragraphs, String color) {
        for (String paragraph : paragraphs) {
            String resolved = MenuConditionEvaluator.applyPlaceholders(player, paragraph);
            for (String line : resolved.split("\\R", -1)) {
                String text = ChatColor.translateAlternateColorCodes('&', color + line);
                StringBuilder wrapped = new StringBuilder();
                int width = 0;
                for (int offset = 0; offset < text.length();) {
                    int cp = text.codePointAt(offset);
                    offset += Character.charCount(cp);
                    if (cp == ChatColor.COLOR_CHAR && offset < text.length()) {
                        wrapped.appendCodePoint(cp).append(text.charAt(offset++));
                        continue;
                    }
                    int units = cp > 255 ? 2 : 1;
                    if (width + units > 44) {
                        String completed = wrapped.toString();
                        rows.put(rowKey(rows.size()), Map.of("text", completed));
                        wrapped = new StringBuilder(ChatColor.getLastColors(completed));
                        width = 0;
                    }
                    wrapped.appendCodePoint(cp);
                    width += units;
                }
                rows.put(rowKey(rows.size()), Map.of("text", wrapped.toString()));
            }
        }
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
}
