package xuanmo.arcartxsuite.menu.config;

import java.util.List;

/**
 * 菜单按钮动作定义，描述单个动作的类型和参数值。
 * <p>
 * 由配置中的 {@code actions} 列表解析而来，交由
 * {@link xuanmo.arcartxsuite.menu.service.MenuActionExecutor} 执行。
 *
 * @param type  动作类型，见 {@link MenuActionType}
 * @param value 动作参数值，具体含义取决于类型（如命令文本、菜单 ID、信号名称等）
 */
public record MenuActionDefinition(
    MenuActionType type,
    String value
) {

    /**
     * 将单行文本解析为动作定义，格式为 {@code type:value}。
     *
     * @param raw 原始文本行
     * @return 解析后的动作定义，空行返回 NONE 类型
     */
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

    /**
     * 将多行文本批量解析为动作定义列表，过滤掉空行和 NONE 空值条目。
     *
     * @param lines 原始文本行列表
     * @return 不可变的动作定义列表
     */
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
