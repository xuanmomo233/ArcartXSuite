package xuanmo.arcartxsuite.menu.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;

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
    MenuIconDefinition icon
) {

    public static MenuButtonDefinition load(String id, ConfigurationSection section) {
        if (section == null) {
            return new MenuButtonDefinition(id, id, 0, "", List.of(), List.of(), "", List.of(), "", null);
        }
        List<ScriptCondition> viewConditions = ScriptConditionsLoader.load(
            section,
            "requirements",
            "view-conditions",
            "viewConditions",
            "conditions",
            "aria-conditions",
            "ariaConditions"
        );
        List<ScriptCondition> useConditions = ScriptConditionsLoader.load(
            section,
            "condition",
            "use-conditions",
            "useConditions",
            "click-conditions",
            "clickConditions",
            "aria-condition",
            "ariaCondition"
        );
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
            MenuIconDefinition.load(section.getConfigurationSection("icon"))
        );
    }
}
