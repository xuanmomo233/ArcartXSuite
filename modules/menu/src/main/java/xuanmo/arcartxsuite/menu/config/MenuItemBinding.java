package xuanmo.arcartxsuite.menu.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.block.Action;

/**
 * 菜单物品绑定定义，描述通过特定物品交互触发打开菜单的规则。
 * <p>
 * 支持按材质、名称包含/正则、Lore 包含、自定义模型数据等条件匹配物品，
 * 由 {@link xuanmo.arcartxsuite.menu.listener.MenuBindingListener} 在玩家交互时匹配。
 *
 * @param menuId         目标菜单 ID
 * @param material       物品材质名称，空字符串表示不限制
 * @param nameContains   物品显示名称需包含的文本
 * @param nameRegex      物品显示名称需匹配的正则表达式
 * @param loreContains   物品 Lore 需包含的文本
 * @param customModelData 自定义模型数据 ID，{@code -1} 表示不限制
 * @param clickAction    触发所需的点击类型
 * @param requireSneak   潜行要求：{@code null} 不限，{@code true} 必须潜行，{@code false} 必须非潜行
 * @param mainHand       是否允许主手触发
 * @param offHand        是否允许副手触发
 * @param permission     触发所需的权限节点，空字符串表示无限制
 */
public record MenuItemBinding(
    String menuId,
    String material,
    String nameContains,
    String nameRegex,
    String loreContains,
    int customModelData,
    Action clickAction,
    Boolean requireSneak,
    boolean mainHand,
    boolean offHand,
    String permission
) {

    /**
     * 从原始列表批量加载物品绑定定义，兼容 ConfigurationSection 和 Map 两种条目类型。
     *
     * @param rawList       原始列表
     * @param defaultMenuId 默认菜单 ID
     * @return 不可变的物品绑定列表
     */
    public static List<MenuItemBinding> loadList(List<?> rawList, String defaultMenuId) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<MenuItemBinding> bindings = new ArrayList<>();
        for (Object raw : rawList) {
            if (raw instanceof ConfigurationSection section) {
                bindings.add(load(section, defaultMenuId));
            } else if (raw instanceof java.util.Map<?, ?> map) {
                org.bukkit.configuration.MemoryConfiguration memory = new org.bukkit.configuration.MemoryConfiguration();
                for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        memory.set(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                bindings.add(load(memory, defaultMenuId));
            }
        }
        return List.copyOf(bindings);
    }

    /**
     * 从配置节加载单个物品绑定定义。
     *
     * @param section       配置节
     * @param defaultMenuId 默认菜单 ID
     * @return 解析后的物品绑定定义
     */
    public static MenuItemBinding load(ConfigurationSection section, String defaultMenuId) {
        String menuId = section.getString("menu", section.getString("open", defaultMenuId));
        ClickSpec clickSpec = parseClickSpec(section.getString("action", section.getString("click", "RIGHT_CLICK")));
        Boolean requireSneak = clickSpec.sneak() ? Boolean.TRUE : null;
        if (section.isSet("sneak")) {
            requireSneak = switch (section.getString("sneak", "any").trim().toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on", "require", "required", "sneak", "shift" -> Boolean.TRUE;
                case "false", "no", "off", "never", "stand", "standing" -> Boolean.FALSE;
                default -> null;
            };
        }
        return new MenuItemBinding(
            menuId == null ? "" : menuId,
            section.getString("material", ""),
            section.getString("name-contains", section.getString("nameContains", "")),
            section.getString("name-regex", section.getString("nameRegex", "")),
            section.getString("lore-contains", section.getString("loreContains", "")),
            section.getInt("custom-model-data", section.getInt("customModelData", -1)),
            clickSpec.action(),
            requireSneak,
            section.getBoolean("main-hand", section.getBoolean("mainHand", true)),
            section.getBoolean("off-hand", section.getBoolean("offHand", true)),
            section.getString("permission", "")
        );
    }

    /**
     * 解析 action 写法，返回基础点击类型与是否要求潜行。
     * {@code SHIFT_*} / {@code SNEAK_*} 前缀表示必须潜行，其余值仅区分左右键。
     */
    private static ClickSpec parseClickSpec(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ClickSpec(Action.RIGHT_CLICK_AIR, false);
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        boolean sneak = false;
        if (normalized.startsWith("SHIFT_") || normalized.startsWith("SNEAK_")) {
            sneak = true;
            normalized = normalized.substring(normalized.indexOf('_') + 1);
        }
        return switch (normalized) {
            case "LEFT", "LEFT_CLICK", "LEFT_CLICK_AIR", "LEFT_CLICK_BLOCK" ->
                new ClickSpec(Action.LEFT_CLICK_AIR, sneak);
            case "PHYSICAL" -> new ClickSpec(Action.PHYSICAL, sneak);
            default -> new ClickSpec(Action.RIGHT_CLICK_AIR, sneak);
        };
    }

    private record ClickSpec(Action action, boolean sneak) {
    }
}
