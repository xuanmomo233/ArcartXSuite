package xuanmo.arcartxsuite.prop.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PropLanguageConfiguration(
    String coolDown,
    String noPermission,
    String noKey,
    String noHand,
    String conditionNotMet
) {

    private static final String DEFAULT_COOLDOWN = "&7[&dArcartXProp&7]&c{NAME}&f还在冷却,你还需要等待&a{TIME}秒&f才能使用道具";
    private static final String DEFAULT_NO_PERMISSION = "&7[&dArcartXProp&7]&f你没有权限使用 &c{NAME}";
    private static final String DEFAULT_NO_KEY = "&7[&dArcartXProp&7]&c{NAME} &f无法按键使用";
    private static final String DEFAULT_NO_HAND = "&7[&dArcartXProp&7]&c{NAME} &f该道具无法手持使用";
    private static final String DEFAULT_CONDITION_NOT_MET = "&7[&dArcartXProp&7]&c{NAME} &f使用条件不满足: &e{CONDITION}";

    public static PropLanguageConfiguration load(FileConfiguration configuration) {
        return new PropLanguageConfiguration(
            readString(configuration, "COOL_DOWN", DEFAULT_COOLDOWN),
            readString(configuration, "NO_PERMISSION", DEFAULT_NO_PERMISSION),
            readString(configuration, "NO_KEY", DEFAULT_NO_KEY),
            readString(configuration, "NO_HAND", DEFAULT_NO_HAND),
            readString(configuration, "CONDITION_NOT_MET", DEFAULT_CONDITION_NOT_MET)
        );
    }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
