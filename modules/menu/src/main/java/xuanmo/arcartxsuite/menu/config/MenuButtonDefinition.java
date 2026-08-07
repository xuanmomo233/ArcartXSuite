package xuanmo.arcartxsuite.menu.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 菜单按钮定义，描述单个按钮的显示文本、排序、权限、条件、动作和图标。
 * <p>
 * 由配置中的 {@code buttons} 节解析而来，包含查看条件和点击条件，
 * 分别控制按钮是否可见和是否可点击。
 *
 * @param id             按钮唯一标识
 * @param text           按钮显示文本
 * @param order          排序权重，越小越靠前
 * @param permission     使用该按钮所需的权限节点，空字符串表示无限制
 * @param viewConditions 控制按钮可见性的脚本条件列表
 * @param useConditions  控制按钮可点击性的脚本条件列表
 * @param denyMessage    条件不满足时的拒绝消息
 * @param actions        点击时执行的动作定义列表
 * @param clientAction   客户端侧动作标识，由 UI 引擎直接处理
 * @param icon           按钮图标定义
 */
public record MenuButtonDefinition(
    String id,
    String text,
    int order,
    String permission,
    List<ScriptCondition> viewConditions,
    List<ScriptCondition> useConditions,
    String denyMessage,
    List<MenuActionDefinition> actions,
    String clientAction,
    IconDefinition icon
) {

    /**
     * 从配置节加载按钮定义。
     *
     * @param id      按钮标识
     * @param section 按钮配置节，为 null 时返回默认值
     * @return 解析后的按钮定义
     */
    public static MenuButtonDefinition load(String id, ConfigurationSection section) {
        if (section == null) {
            return new MenuButtonDefinition(id, id, 0, "", List.of(), List.of(), "", List.of(), "", null);
        }
        List<ScriptCondition> viewConditions = ScriptConditionsLoader.loadViewConditions(section);
        List<ScriptCondition> useConditions = ScriptConditionsLoader.loadUseConditions(section);
        return new MenuButtonDefinition(
            id,
            section.getString("text", id),
            section.getInt("order", 0),
            section.getString("permission", ""),
            viewConditions,
            useConditions,
            section.getString("deny-message", section.getString("denyMessage", "")),
            MenuActionDefinition.parseList(section.getStringList("actions")),
            section.getString("client-action", section.getString("clientAction", "")),
            IconDefinition.load(section.getConfigurationSection("icon"))
        );
    }
}
