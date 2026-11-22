package xuanmo.arcartxsuite.vanilla.backpack.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Vanilla 随身背包配置。物品分类由 Warehouse 提供。
 *
 * @param ui          UI 相关配置（是否注册 UI、是否覆盖 UI 文件）
 * @param enabled 是否启用随身背包
 * @param presentation 背包专用的转场与动作配置
 * @param mmoItemsAttributeSlots 参与 MMOItems 属性结算的 AX 饰品槽 ID
 */
public record ExtraBackpackConfiguration(
    UiConfiguration ui,
    boolean enabled,
    BackpackPresentationConfig presentation,
    Set<String> mmoItemsAttributeSlots
) {

    /**
     * 从 Bukkit 配置文件解析并构建 {@link ExtraBackpackConfiguration}。
     *
     * @param configuration Bukkit 配置对象
     * @return 解析后的配置实例
     */
    public static ExtraBackpackConfiguration load(ConfigurationSection configuration) {
        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        UiConfiguration ui = new UiConfiguration(
            uiSection == null || uiSection.getBoolean("register-ui-on-enable", true),
            uiSection != null && uiSection.getBoolean("overwrite-ui-files", false)
        );
        var accessorySlots = new LinkedHashSet<String>();
        String slotKey = configuration.isSet("mmoitems-attribute-slots")
            ? "mmoitems-attribute-slots" : "mmoitems-accessory-slots";
        for (String slot : configuration.getStringList(slotKey)) {
            if (!slot.isBlank()) accessorySlots.add(slot.strip());
        }
        return new ExtraBackpackConfiguration(ui, configuration.getBoolean("enabled", false),
            BackpackPresentationConfig.load(configuration.getConfigurationSection("presentation")),
            Set.copyOf(accessorySlots));
    }

    /**
     * UI 相关配置 record。
     *
     * @param registerUiOnEnable 启用时是否自动注册 UI 资源
     * @param overwriteUiFiles   启用时是否覆盖已存在的 UI 文件
     */
    public record UiConfiguration(
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
    }

}
