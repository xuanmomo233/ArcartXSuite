package xuanmo.arcartxsuite.menu.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 菜单页面定义，描述单个页面的标题和按钮集合。
 * <p>
 * 一个菜单可包含多个页面，由 {@link xuanmo.arcartxsuite.menu.config.MenuDefinition} 管理。
 *
 * @param id      页面标识
 * @param title   页面标题
 * @param buttons 页面按钮映射（按钮 ID → 按钮定义）
 */
public record MenuPageDefinition(
    String id,
    String title,
    Map<String, MenuButtonDefinition> buttons
) {

    /**
     * 从配置节加载页面定义。
     *
     * @param section 页面配置节
     * @param logger  模块 logger，用于记录未知动作类型警告
     * @return 解析后的页面定义，为 null 时返回默认空页面
     */
    public static MenuPageDefinition load(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return new MenuPageDefinition("main", "", Map.of());
        }
        String id = section.getString("id", "main");
        String title = section.getString("title", id);
        Map<String, MenuButtonDefinition> buttons = new LinkedHashMap<>();
        ConfigurationSection buttonsSection = section.getConfigurationSection("buttons");
        if (buttonsSection != null) {
            for (String buttonId : buttonsSection.getKeys(false)) {
                buttons.put(buttonId, MenuButtonDefinition.load(buttonId, buttonsSection.getConfigurationSection(buttonId), logger));
            }
        }
        return new MenuPageDefinition(id, title, Map.copyOf(buttons));
    }

    /**
     * 从原始 Map 列表批量加载页面定义，格式错误的页面会被跳过。
     *
     * @param rawPages 原始页面 Map 列表
     * @param logger   模块 logger，用于记录未知动作类型警告
     * @return 不可变的页面定义列表
     */
    public static List<MenuPageDefinition> loadList(List<Map<?, ?>> rawPages, Logger logger) {
        if (rawPages == null || rawPages.isEmpty()) {
            return List.of(new MenuPageDefinition("main", "Menu", Map.of()));
        }
        List<MenuPageDefinition> pages = new ArrayList<>();
        org.yaml.snakeyaml.Yaml yamlDumper = new org.yaml.snakeyaml.Yaml();
        for (Map<?, ?> rawPage : rawPages) {
            if (rawPage == null) {
                continue;
            }
            String dump = yamlDumper.dump(rawPage);
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            try {
                config.loadFromString(dump);
                pages.add(load(config, logger));
            } catch (Exception ignored) {
                // 跳过格式错误的 page
            }
        }
        return List.copyOf(pages);
    }
}
