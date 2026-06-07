package xuanmo.arcartxsuite.menu.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.condition.ScriptCondition;
import xuanmo.arcartxsuite.api.condition.ScriptConditionsLoader;

public record MenuDefinition(
    String id,
    String title,
    MenuLayoutType layout,
    int columns,
    int buttonsPerPage,
    String permission,
    boolean matchEsc,
    List<ScriptCondition> openRequirements,
    List<MenuActionDefinition> openActions,
    List<MenuActionDefinition> closeActions,
    List<String> commands,
    List<String> commandRegex,
    List<MenuItemBinding> itemBinds,
    List<MenuPageDefinition> pages,
    Map<String, MenuButtonDefinition> footerButtons,
    String sourceFile
) {

    public MenuPageDefinition pageAt(int index) {
        if (pages.isEmpty()) {
            return new MenuPageDefinition("main", title, Map.of());
        }
        if (index < 0) {
            return pages.get(0);
        }
        if (index >= pages.size()) {
            return pages.get(pages.size() - 1);
        }
        return pages.get(index);
    }

    public int pageIndexOf(String pageId) {
        if (pageId == null || pageId.isBlank()) {
            return 0;
        }
        for (int i = 0; i < pages.size(); i++) {
            if (pageId.equalsIgnoreCase(pages.get(i).id())) {
                return i;
            }
        }
        return 0;
    }

    public static MenuDefinition load(YamlConfiguration yaml, String sourceFile, MenuLayoutType defaultLayout, int defaultColumns, int defaultButtonsPerPage) {
        String id = yaml.getString("id", "");
        if (id.isBlank()) {
            throw new IllegalArgumentException("菜单缺少 id: " + sourceFile);
        }
        List<ScriptCondition> openRequirements = new ArrayList<>(
            ScriptConditionsLoader.load(yaml, "open-requirements", "aria-conditions", "ariaConditions")
        );
        Map<String, MenuButtonDefinition> footerButtons = new LinkedHashMap<>();
        ConfigurationSection footerSection = yaml.getConfigurationSection("footer-buttons");
        if (footerSection != null) {
            for (String footerId : footerSection.getKeys(false)) {
                footerButtons.put(footerId, MenuButtonDefinition.load(footerId, footerSection.getConfigurationSection(footerId)));
            }
        }
        List<MenuPageDefinition> pages = new ArrayList<>();
        List<Map<?, ?>> rawPages = yaml.getMapList("pages");
        if (rawPages.isEmpty()) {
            ConfigurationSection pagesSection = yaml.getConfigurationSection("pages");
            if (pagesSection != null) {
                for (String pageKey : pagesSection.getKeys(false)) {
                    ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
                    if (pageSection != null) {
                        if (!pageSection.isSet("id")) {
                            pageSection.set("id", pageKey);
                        }
                        pages.add(MenuPageDefinition.load(pageSection));
                    }
                }
            }
        } else {
            pages.addAll(MenuPageDefinition.loadList(rawPages));
        }
        if (pages.isEmpty()) {
            pages.add(new MenuPageDefinition("main", yaml.getString("title", id), Map.of()));
        }
        List<MenuItemBinding> itemBinds = MenuItemBinding.loadList(yaml.getMapList("item-binds"), id);
        if (itemBinds.isEmpty()) {
            itemBinds = MenuItemBinding.loadList(yaml.getMapList("item-bindings"), id);
        }
        ConfigurationSection itemBindSection = yaml.getConfigurationSection("item-binds");
        if (itemBinds.isEmpty() && itemBindSection != null) {
            List<MenuItemBinding> legacy = new ArrayList<>();
            for (String key : itemBindSection.getKeys(false)) {
                legacy.add(MenuItemBinding.load(itemBindSection.getConfigurationSection(key), id));
            }
            itemBinds = List.copyOf(legacy);
        }

        return new MenuDefinition(
            id,
            yaml.getString("title", id),
            MenuLayoutType.parse(yaml.getString("layout"), defaultLayout),
            Math.max(1, yaml.getInt("columns", defaultColumns)),
            Math.max(1, yaml.getInt("buttons-per-page", defaultButtonsPerPage)),
            yaml.getString("permission", ""),
            yaml.getBoolean("match-esc", false),
            List.copyOf(openRequirements),
            MenuActionDefinition.parseList(yaml.getStringList("open-actions")),
            MenuActionDefinition.parseList(yaml.getStringList("close-actions")),
            List.copyOf(yaml.getStringList("commands")),
            List.copyOf(yaml.getStringList("command-regex")),
            itemBinds,
            List.copyOf(pages),
            Map.copyOf(footerButtons),
            sourceFile
        );
    }
}
