package xuanmo.arcartxsuite.menu.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import xuanmo.arcartxsuite.menu.config.MenuButtonDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuLayoutType;
import xuanmo.arcartxsuite.menu.config.MenuPageDefinition;

public final class MenuPacketFactory {

    private MenuPacketFactory() {
    }

    public static Map<String, Object> buildPayload(
        String packetId,
        Player player,
        MenuDefinition definition,
        MenuSession session,
        MenuIconResolver iconResolver
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

        LinkedHashMap<String, Object> footerRows = new LinkedHashMap<>();
        int footerIndex = 0;
        for (MenuButtonDefinition footer : definition.footerButtons().values()) {
            if (!MenuConditionEvaluator.hasPermission(player, footer.permission())
                || !MenuConditionEvaluator.passes(player, footer.viewConditions())) {
                continue;
            }
            boolean enabled = MenuConditionEvaluator.passes(player, footer.useConditions());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", footer.id());
            row.put("text", MenuConditionEvaluator.applyPlaceholders(player, footer.text()));
            row.put("clientAction", footer.clientAction());
            row.put("enabled", enabled);
            String footerItemJson = iconResolver == null ? "" : iconResolver.resolveItemJson(player, footer.icon());
            row.put("itemJson", footerItemJson);
            row.put("hasIcon", footerItemJson != null && !footerItemJson.isBlank());
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
        result.sort(Comparator.comparingInt(MenuButtonDefinition::order).thenComparing(MenuButtonDefinition::id));
        return result;
    }

    private static Map<String, Object> buttonRow(
        Player player,
        MenuButtonDefinition button,
        int index,
        int columns,
        MenuIconResolver iconResolver
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
        return row;
    }

    public static String rowKey(int index) {
        return Integer.toString(index);
    }

    public static String runtimeUiId(MenuLayoutType layout, String panelUiId, String escUiId) {
        return layout == MenuLayoutType.ESC ? escUiId : panelUiId;
    }
}
