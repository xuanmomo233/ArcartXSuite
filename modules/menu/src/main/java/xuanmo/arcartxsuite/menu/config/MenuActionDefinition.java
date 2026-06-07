package xuanmo.arcartxsuite.menu.config;

import java.util.List;

public record MenuActionDefinition(
    MenuActionType type,
    String value
) {

    public static MenuActionDefinition parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new MenuActionDefinition(MenuActionType.NONE, "");
        }
        String trimmed = raw.trim();
        int separator = trimmed.indexOf(':');
        if (separator <= 0) {
            return new MenuActionDefinition(MenuActionType.parse(trimmed), "");
        }
        String typePart = trimmed.substring(0, separator).trim();
        String valuePart = trimmed.substring(separator + 1).trim();
        return new MenuActionDefinition(MenuActionType.parse(typePart), valuePart);
    }

    public static List<MenuActionDefinition> parseList(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
            .map(MenuActionDefinition::parse)
            .filter(action -> action.type() != MenuActionType.NONE || !action.value().isBlank())
            .toList();
    }
}
