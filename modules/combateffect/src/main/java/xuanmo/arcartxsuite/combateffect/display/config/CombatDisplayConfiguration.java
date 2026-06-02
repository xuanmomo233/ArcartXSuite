package xuanmo.arcartxsuite.combateffect.display.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

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
    boolean healEnabled,
    String healConfigId,
    double healMinAmount,
    boolean mythicHealEnabled,
    String mythicHealConfigId,
    double mythicHealMinAmount,
    boolean mythicHealExactMode
) {

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
            configuration.getBoolean("heal-display.original.enabled", true),
            readString(configuration, "heal-display.original.config-id", "heal"),
            Math.max(0.0D, configuration.getDouble("heal-display.original.min-amount", 1.0D)),
            configuration.getBoolean("heal-display.mythic.enabled", true),
            readString(configuration, "heal-display.mythic.config-id", "heal"),
            Math.max(0.0D, configuration.getDouble("heal-display.mythic.min-amount", 1.0D)),
            configuration.getBoolean("heal-display.mythic.exact-mode", true)
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


