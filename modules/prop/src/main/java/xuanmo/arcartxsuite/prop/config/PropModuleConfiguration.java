package xuanmo.arcartxsuite.prop.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Prop 模块主配置模型。
 * <p>
 * 从 {@code ArcartXProp.yml} 解析，包含调试开关、MythicLib 子配置与自动触发子配置。
 *
 * @param debug       是否启用调试日志
 * @param mythicLib   MythicLib 相关配置
 * @param autoTrigger 自动触发全局配置
 */
public record PropModuleConfiguration(
    boolean debug,
    PropMythicLibConfiguration mythicLib,
    PropAutoTriggerConfiguration autoTrigger
) {

    /**
     * 从 YAML 配置加载模块配置。
     *
     * @param configuration YAML 配置
     * @return 解析后的 {@link PropModuleConfiguration}
     */
    public static PropModuleConfiguration load(FileConfiguration configuration) {
        return new PropModuleConfiguration(
            configuration.getBoolean("settings.debug", false),
            new PropMythicLibConfiguration(
                configuration.getString("mythiclib.source-prefix", "AXS_PROP")
            ),
            new PropAutoTriggerConfiguration(
                configuration.getBoolean("auto-trigger.enabled", false),
                Math.max(1, configuration.getInt("auto-trigger.interval", 20)),
                PropAutoTriggerConfiguration.Mode.parse(configuration.getString("auto-trigger.mode", "all"))
            )
        );
    }
}
