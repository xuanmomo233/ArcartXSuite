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
    List<MenuUiTarget> uiTargets,
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
        if (pages.isEmpty()) {
            List<Map<?, ?>> rawPages = yaml.getMapList("pages");
            if (!rawPages.isEmpty()) {
                pages.addAll(MenuPageDefinition.loadList(rawPages));
            }
        }
        if (pages.isEmpty()) {
            pages.add(new MenuPageDefinition("main", yaml.getString("title", id), Map.of()));
        }
        List<MenuItemBinding> itemBinds = MenuItemBinding.loadList(yaml.getMapList("item-binds"), id);
        List<MenuUiTarget> uiTargets = loadUiTargets(yaml);

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
            uiTargets,
            sourceFile
        );
    }

    /**
     * 解析 UI 发包目标。
     * <p>
     * 优先读取 {@code ui-targets} 列表；若不存在则读取简写 {@code ui-id + packet-handler}。
     * 两者都未配置时返回空列表，表示使用模块默认的 layout-based UI 与 init/update handler。
     */
    @SuppressWarnings("unchecked")
    private static List<MenuUiTarget> loadUiTargets(YamlConfiguration yaml) {
        List<Map<?, ?>> targetMaps = yaml.getMapList("ui-targets");
        if (!targetMaps.isEmpty()) {
            List<MenuUiTarget> targets = new ArrayList<>(targetMaps.size());
            for (Map<?, ?> map : targetMaps) {
                String uiId = readString(map, "ui-id");
                String handler = readString(map, "packet-handler");
                if (uiId == null || handler == null) {
                    continue;
                }
                targets.add(new MenuUiTarget(uiId, handler));
            }
            return List.copyOf(targets);
        }
        String uiId = yaml.getString("ui-id", "").trim();
        String handler = yaml.getString("packet-handler", "").trim();
        if (!uiId.isBlank() && !handler.isBlank()) {
            return List.of(new MenuUiTarget(uiId, handler));
        }
        return List.of();
    }

    private static String readString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String str = value.toString().trim();
        return str.isBlank() ? null : str;
    }
}

