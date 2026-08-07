package xuanmo.arcartxsuite.menu.config;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Menu 模块顶层配置，聚合调试开关、客户端配置、设置配置和消息配置。
 * <p>
 * 由 {@link xuanmo.arcartxsuite.menu.MenuModule} 在启动时从主配置文件加载。
 *
 * @param debug    是否启用调试日志
 * @param client   客户端 UI 配置
 * @param settings 菜单设置配置
 * @param messages 消息配置
 */
public record MenuModuleConfiguration(
    boolean debug,
    MenuClientConfiguration client,
    MenuSettingsConfiguration settings,
    MenuMessagesConfiguration messages
) {

    /**
     * 从主配置 YAML 加载模块配置，消息从主配置的 {@code messages} 节读取。
     *
     * @param yaml 主配置
     * @return 解析后的模块配置
     */
    public static MenuModuleConfiguration load(@NotNull YamlConfiguration yaml) {
        return new MenuModuleConfiguration(
            yaml.getBoolean("debug.enabled", false),
            MenuClientConfiguration.load(yaml.getConfigurationSection("client")),
            MenuSettingsConfiguration.load(yaml.getConfigurationSection("settings")),
            MenuMessagesConfiguration.load(yaml.getConfigurationSection("messages"))
        );
    }

    /**
     * 从主配置 YAML 和独立消息 YAML 加载模块配置。
     *
     * @param yaml        主配置
     * @param messagesYaml 独立消息配置
     * @return 解析后的模块配置
     */
    public static MenuModuleConfiguration load(
            @NotNull YamlConfiguration yaml,
            @NotNull YamlConfiguration messagesYaml) {
        return new MenuModuleConfiguration(
            yaml.getBoolean("debug.enabled", false),
            MenuClientConfiguration.load(yaml.getConfigurationSection("client")),
            MenuSettingsConfiguration.load(yaml.getConfigurationSection("settings")),
            MenuMessagesConfiguration.load(messagesYaml)
        );
    }

    /**
     * 返回菜单定义文件所在的目录。
     *
     * @param dataFolder 模块数据目录
     * @return 菜单配置目录文件
     */
    public @NotNull File menusDirectory(@NotNull File dataFolder) {
        return new File(dataFolder, settings.menusDirectory());
    }
}
