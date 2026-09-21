package xuanmo.arcartxsuite.prop.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 道具模块语言配置模型。
 * <p>
 * 从 {@code language.yml} 解析，包含冷却、无权限、无按键、无手持、条件不满足等提示消息模板。
 *
 * @param coolDown         冷却中提示，支持 {NAME} 和 {TIME} 占位符
 * @param noPermission     无权限提示，支持 {NAME} 占位符
 * @param noKey            无法按键使用提示，支持 {NAME} 占位符
 * @param noHand           无法手持使用提示，支持 {NAME} 占位符
 * @param conditionNotMet 条件不满足提示，支持 {NAME} 和 {CONDITION} 占位符
 * @param autoUsed        自动触发成功提示，支持 {NAME} 占位符
 */
public record PropLanguageConfiguration(
    String coolDown,
    String noPermission,
    String noKey,
    String noHand,
    String conditionNotMet,
    String autoUsed
) {

    private static final String DEFAULT_COOLDOWN = "&7[&dArcartXProp&7]&c{NAME}&f还在冷却,你还需要等待&a{TIME}秒&f才能使用道具";
    private static final String DEFAULT_NO_PERMISSION = "&7[&dArcartXProp&7]&f你没有权限使用 &c{NAME}";
    private static final String DEFAULT_NO_KEY = "&7[&dArcartXProp&7]&c{NAME} &f无法按键使用";
    private static final String DEFAULT_NO_HAND = "&7[&dArcartXProp&7]&c{NAME} &f该道具无法手持使用";
    private static final String DEFAULT_CONDITION_NOT_MET = "&7[&dArcartXProp&7]&c{NAME} &f使用条件不满足: &e{CONDITION}";
    private static final String DEFAULT_AUTO_USED = "&7[&dArcartXProp&7]&f已自动使用 &a{NAME}";

    /**
     * 从 YAML 配置加载语言配置。
     *
     * @param configuration YAML 配置
     * @return 解析后的 {@link PropLanguageConfiguration}
     */
    public static PropLanguageConfiguration load(FileConfiguration configuration) {
        return new PropLanguageConfiguration(
            readString(configuration, "COOL_DOWN", DEFAULT_COOLDOWN),
            readString(configuration, "NO_PERMISSION", DEFAULT_NO_PERMISSION),
            readString(configuration, "NO_KEY", DEFAULT_NO_KEY),
            readString(configuration, "NO_HAND", DEFAULT_NO_HAND),
            readString(configuration, "CONDITION_NOT_MET", DEFAULT_CONDITION_NOT_MET),
            readString(configuration, "AUTO_USED", DEFAULT_AUTO_USED)
        );
    }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
