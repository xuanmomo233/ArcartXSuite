package xuanmo.arcartxsuite.combateffect.display.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 战斗显示模块的配置记录。
 * <p>
 * 属于 combateffect 模块的 display 子模块，集中定义伤害与治疗数字显示的
 * 所有参数，包括伤害来源模式、各属性插件（MythicLib / CraneAttribute /
 * AttributePlus / Symphony）的显示开关与阈值、合并窗口、以及治疗显示配置。
 *
 * @param damageSourceMode                  伤害来源解析模式
 * @param damageSourceFallback              指定来源不可用时是否回退
 * @param damageSourceDebug                 是否输出伤害来源调试日志
 * @param damageEnabled                     是否启用原版伤害显示
 * @param damageConfigId                    原版伤害显示的 UI 配置 ID
 * @param damageMinAmount                   原版伤害显示的最小金额阈值
 * @param damageAttributePlusCompatible     原版伤害是否兼容 AttributePlus
 * @param playerDamageEnabled               是否启用玩家受击伤害显示
 * @param playerDamageConfigId              玩家受击伤害显示的 UI 配置 ID
 * @param playerDamageMinAmount             玩家受击伤害显示的最小金额阈值
 * @param mythicLibDamageEnabled            是否启用 MythicLib 伤害显示
 * @param mythicLibDamageConfigId           MythicLib 伤害显示的 UI 配置 ID
 * @param mythicLibPlayerDamageConfigId     MythicLib 玩家受击伤害显示的 UI 配置 ID
 * @param mythicLibDamageMinAmount          MythicLib 伤害显示的最小金额阈值
 * @param mythicLibPlayerDamageMinAmount    MythicLib 玩家受击伤害显示的最小金额阈值
 * @param craneAttributeDamageEnabled       是否启用 CraneAttribute 伤害显示
 * @param craneAttributeDamageConfigId      CraneAttribute 伤害显示的 UI 配置 ID
 * @param craneAttributePlayerDamageConfigId CraneAttribute 玩家受击伤害显示的 UI 配置 ID
 * @param craneAttributeDamageMinAmount     CraneAttribute 伤害显示的最小金额阈值
 * @param craneAttributePlayerDamageMinAmount CraneAttribute 玩家受击伤害显示的最小金额阈值
 * @param symphonyDamageEnabled             是否启用 Symphony 伤害显示
 * @param symphonyDamageConfigId            Symphony 伤害显示的 UI 配置 ID
 * @param symphonyPlayerDamageConfigId      Symphony 玩家受击伤害显示的 UI 配置 ID
 * @param symphonyDamageMinAmount           Symphony 伤害显示的最小金额阈值
 * @param symphonyPlayerDamageMinAmount     Symphony 玩家受击伤害显示的最小金额阈值
 * @param healEnabled                       是否启用原版治疗显示
 * @param healConfigId                      原版治疗显示的 UI 配置 ID
 * @param healMinAmount                     原版治疗显示的最小金额阈值
 * @param mythicHealEnabled                 是否启用 MythicMobs 治疗显示
 * @param mythicHealConfigId                MythicMobs 治疗显示的 UI 配置 ID
 * @param mythicHealMinAmount               MythicMobs 治疗显示的最小金额阈值
 * @param mythicHealExactMode               MythicMobs 治疗是否使用精确模式（扣除溢出）
 * @param damageMergeEnabled                是否启用伤害合并
 * @param damageMergeWindowTicks            伤害合并窗口（tick）
 * @param damageMergeMinAmount              伤害合并后的最小显示金额
 * @param damageMergeMaxEntries             伤害合并缓冲区最大条目数
 * @param showOthersDamage                  是否向旁观者显示伤害数字
 * @param healMergeEnabled                  是否启用治疗合并
 * @param healMergeWindowTicks              治疗合并窗口（tick）
 * @param healMergeMinAmount                治疗合并后的最小显示金额
 * @param healMergeMaxEntries               治疗合并缓冲区最大条目数
 * @param criticalEnabled                   是否启用暴击飘字
 * @param criticalConfigId                  暴击伤害显示的 UI 配置 ID
 * @param criticalPlayerConfigId            暴击 PVP 伤害显示的 UI 配置 ID
 * @param criticalMinAmount                 暴击伤害显示的最小金额阈值
 * @param dodgedEnabled                     是否启用闪避飘字
 * @param dodgedConfigId                    闪避显示的 UI 配置 ID
 * @param advantagedEnabled                 是否启用克制（优势）飘字
 * @param advantagedConfigId                优势克制伤害显示的 UI 配置 ID
 * @param advantagedPlayerConfigId          优势克制 PVP 伤害显示的 UI 配置 ID
 * @param disadvantagedEnabled              是否启用被克制（劣势）飘字
 * @param disadvantagedConfigId             劣势克制伤害显示的 UI 配置 ID
 * @param disadvantagedPlayerConfigId       劣势克制 PVP 伤害显示的 UI 配置 ID
 */
public record CombatDisplayConfiguration(
    CombatDisplayDamageSourceMode damageSourceMode,
    boolean damageSourceFallback,
    boolean damageSourceDebug,
    boolean damageEnabled,
    String damageConfigId,
    double damageMinAmount,
    boolean damageAttributePlusCompatible,
    boolean playerDamageEnabled,
    String playerDamageConfigId,
    double playerDamageMinAmount,
    boolean mythicLibDamageEnabled,
    String mythicLibDamageConfigId,
    String mythicLibPlayerDamageConfigId,
    double mythicLibDamageMinAmount,
    double mythicLibPlayerDamageMinAmount,
    boolean craneAttributeDamageEnabled,
    String craneAttributeDamageConfigId,
    String craneAttributePlayerDamageConfigId,
    double craneAttributeDamageMinAmount,
    double craneAttributePlayerDamageMinAmount,
    boolean symphonyDamageEnabled,
    String symphonyDamageConfigId,
    String symphonyPlayerDamageConfigId,
    double symphonyDamageMinAmount,
    double symphonyPlayerDamageMinAmount,
    boolean healEnabled,
    String healConfigId,
    double healMinAmount,
    boolean mythicHealEnabled,
    String mythicHealConfigId,
    double mythicHealMinAmount,
    boolean mythicHealExactMode,
    boolean damageMergeEnabled,
    int damageMergeWindowTicks,
    double damageMergeMinAmount,
    int damageMergeMaxEntries,
    boolean showOthersDamage,
    boolean healMergeEnabled,
    int healMergeWindowTicks,
    double healMergeMinAmount,
    int healMergeMaxEntries,
    boolean criticalEnabled,
    String criticalConfigId,
    String criticalPlayerConfigId,
    double criticalMinAmount,
    boolean dodgedEnabled,
    String dodgedConfigId,
    boolean advantagedEnabled,
    String advantagedConfigId,
    String advantagedPlayerConfigId,
    boolean disadvantagedEnabled,
    String disadvantagedConfigId,
    String disadvantagedPlayerConfigId
) {

    /**
     * 从配置节点加载战斗显示配置。
     * <p>
     * 当配置节点为 {@code null} 时使用空配置并填充默认值。
     *
     * @param configuration Bukkit 配置节点
     * @return 加载后的配置记录
     */
    public static CombatDisplayConfiguration load(ConfigurationSection configuration) {
        if (configuration == null) {
            configuration = new YamlConfiguration();
        }
        String damageConfigId = readString(configuration, "damage-display.original.config-id", "damage");
        String playerDamageConfigId = readString(configuration, "damage-display.player.config-id", "player-damage");
        return new CombatDisplayConfiguration(
            CombatDisplayDamageSourceMode.from(configuration.getString("damage-display.source.mode", "auto")),
            configuration.getBoolean("damage-display.source.fallback", true),
            configuration.getBoolean("damage-display.source.debug", false),
            configuration.getBoolean("damage-display.original.enabled", true),
            damageConfigId,
            Math.max(0.0D, configuration.getDouble("damage-display.original.min-amount", 1.0D)),
            configuration.getBoolean("damage-display.original.ap-compatible", true),
            configuration.getBoolean("damage-display.player.enabled", true),
            playerDamageConfigId,
            Math.max(0.0D, configuration.getDouble("damage-display.player.min-amount", 1.0D)),
            configuration.getBoolean("damage-display.mythiclib.enabled", false),
            readString(configuration, "damage-display.mythiclib.config-id", damageConfigId),
            readString(configuration, "damage-display.mythiclib.player-config-id", playerDamageConfigId),
            Math.max(0.0D, configuration.getDouble("damage-display.mythiclib.min-amount", 1.0D)),
            Math.max(0.0D, configuration.getDouble("damage-display.mythiclib.player-min-amount", 1.0D)),
            configuration.getBoolean("damage-display.craneattribute.enabled", false),
            readString(configuration, "damage-display.craneattribute.config-id", damageConfigId),
            readString(configuration, "damage-display.craneattribute.player-config-id", playerDamageConfigId),
            Math.max(0.0D, configuration.getDouble("damage-display.craneattribute.min-amount", 1.0D)),
            Math.max(0.0D, configuration.getDouble("damage-display.craneattribute.player-min-amount", 1.0D)),
            configuration.getBoolean("damage-display.symphony.enabled", false),
            readString(configuration, "damage-display.symphony.config-id", damageConfigId),
            readString(configuration, "damage-display.symphony.player-config-id", playerDamageConfigId),
            Math.max(0.0D, configuration.getDouble("damage-display.symphony.min-amount", 1.0D)),
            Math.max(0.0D, configuration.getDouble("damage-display.symphony.player-min-amount", 1.0D)),
            configuration.getBoolean("heal-display.original.enabled", true),
            readString(configuration, "heal-display.original.config-id", "heal"),
            Math.max(0.0D, configuration.getDouble("heal-display.original.min-amount", 1.0D)),
            configuration.getBoolean("heal-display.mythic.enabled", true),
            readString(configuration, "heal-display.mythic.config-id", "heal"),
            Math.max(0.0D, configuration.getDouble("heal-display.mythic.min-amount", 1.0D)),
            configuration.getBoolean("heal-display.mythic.exact-mode", true),
            configuration.getBoolean("damage-display.damage-merge.enabled", false),
            Math.max(0, configuration.getInt("damage-display.damage-merge.window-ticks", 2)),
            Math.max(0.0D, configuration.getDouble("damage-display.damage-merge.min-amount", 1.0D)),
            Math.max(1, configuration.getInt("damage-display.damage-merge.max-entries", 500)),
            configuration.getBoolean("damage-display.show-others-damage", false),
            configuration.getBoolean("heal-display.heal-merge.enabled", false),
            Math.max(0, configuration.getInt("heal-display.heal-merge.window-ticks", 2)),
            Math.max(0.0D, configuration.getDouble("heal-display.heal-merge.min-amount", 1.0D)),
            Math.max(1, configuration.getInt("heal-display.heal-merge.max-entries", 500)),
            // 暴击/闪避/克制（全局配置块，跨所有属性插件）
            configuration.getBoolean("damage-display.critical.enabled", false),
            readString(configuration, "damage-display.critical.config-id", "ArcartXSuite-critical"),
            readString(configuration, "damage-display.critical.player-config-id", "ArcartXSuite-critical"),
            Math.max(0.0D, configuration.getDouble("damage-display.critical.min-amount", 1.0D)),
            configuration.getBoolean("damage-display.dodged.enabled", false),
            readString(configuration, "damage-display.dodged.config-id", "ArcartXSuite-dodged"),
            configuration.getBoolean("damage-display.advantaged.enabled", false),
            readString(configuration, "damage-display.advantaged.config-id", "ArcartXSuite-advantaged"),
            readString(configuration, "damage-display.advantaged.player-config-id", "ArcartXSuite-advantaged"),
            configuration.getBoolean("damage-display.disadvantaged.enabled", false),
            readString(configuration, "damage-display.disadvantaged.config-id", "ArcartXSuite-disadvantaged"),
            readString(configuration, "damage-display.disadvantaged.player-config-id", "ArcartXSuite-disadvantaged")
        );
    }

    private static String readString(ConfigurationSection configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}


