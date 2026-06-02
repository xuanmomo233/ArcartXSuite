package xuanmo.arcartxsuite.entitytracker.target.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.config.UiIdParser;
import xuanmo.arcartxsuite.api.combat.EntityCombatMetadata;

public record EntityTargetHudConfiguration(
    boolean debug,
    long refreshIntervalTicks,
    long targetTimeoutMs,
    double maxViewDistance,
    List<String> uiIds,
    boolean registerUiOnEnable,
    boolean overwriteUiFile,
    String titleFormat,
    String subtitleFormat,
    Set<String> blacklistedMythicMobIds,
    Set<String> blacklistedEntityTypes,
    boolean ignoreTrackedBosses
) {

    public EntityTargetHudConfiguration {
        blacklistedMythicMobIds = Set.copyOf(blacklistedMythicMobIds == null ? Set.of() : blacklistedMythicMobIds);
        blacklistedEntityTypes = Set.copyOf(blacklistedEntityTypes == null ? Set.of() : blacklistedEntityTypes);
    }

    private static final long DEFAULT_REFRESH_INTERVAL_TICKS = 5L;
    private static final long DEFAULT_TARGET_TIMEOUT_MS = 3000L;
    private static final double DEFAULT_MAX_VIEW_DISTANCE = 48.0D;
    private static final String DEFAULT_UI_ID = "AXS:attack_target_hud";
    private static final String DEFAULT_TITLE_FORMAT = "&c{display_name}";
    private static final String DEFAULT_SUBTITLE_FORMAT = "&7{entity_type_name} &8| &c{health}/{max_health} &8| &f{distance_text}";

    public static EntityTargetHudConfiguration load(ConfigurationSection configuration) {
        if (configuration == null) {
            configuration = new YamlConfiguration();
        }
        boolean debug = configuration.getBoolean("settings.debug", false);
        long refreshIntervalTicks = Math.max(1L, configuration.getLong("settings.refresh-interval-ticks", DEFAULT_REFRESH_INTERVAL_TICKS));
        long targetTimeoutMs = Math.max(250L, configuration.getLong("settings.target-timeout-ms", DEFAULT_TARGET_TIMEOUT_MS));
        double maxViewDistance = Math.max(1.0D, configuration.getDouble("settings.max-view-distance", DEFAULT_MAX_VIEW_DISTANCE));
        List<String> uiIds = UiIdParser.readUiIds(
            configuration.getConfigurationSection("settings"), "ui-id", DEFAULT_UI_ID
        );
        boolean registerUiOnEnable = configuration.getBoolean("settings.register-ui-on-enable", true);
        boolean overwriteUiFile = configuration.getBoolean("settings.overwrite-ui-file", false);
        String titleFormat = readString(configuration, "settings.title-format", DEFAULT_TITLE_FORMAT);
        String subtitleFormat = readString(configuration, "settings.subtitle-format", DEFAULT_SUBTITLE_FORMAT);
        Set<String> blacklistedMythicMobIds = readNormalizedSet(
            configuration,
            "settings.blacklist.mythic-mob-ids",
            EntityCombatMetadata::normalizeMythicMobId
        );
        Set<String> blacklistedEntityTypes = readNormalizedSet(
            configuration,
            "settings.blacklist.entity-types",
            EntityCombatMetadata::normalizeEntityType
        );
        boolean ignoreTrackedBosses = configuration.getBoolean("settings.entitytracker-link.ignore-configured-bosses", true);
        return new EntityTargetHudConfiguration(
            debug,
            refreshIntervalTicks,
            targetTimeoutMs,
            maxViewDistance,
            uiIds,
            registerUiOnEnable,
            overwriteUiFile,
            titleFormat,
            subtitleFormat,
            blacklistedMythicMobIds,
            blacklistedEntityTypes,
            ignoreTrackedBosses
        );
    }

    /** 向后兼容：返回首个 uiId。 */
    public String uiId() {
        return uiIds.isEmpty() ? "" : uiIds.get(0);
    }

    public boolean isBlacklisted(String mythicMobId, String entityType) {
        String normalizedMythicMobId = EntityCombatMetadata.normalizeMythicMobId(mythicMobId);
        String normalizedEntityType = EntityCombatMetadata.normalizeEntityType(entityType);
        return (!normalizedMythicMobId.isBlank() && blacklistedMythicMobIds.contains(normalizedMythicMobId))
            || (!normalizedEntityType.isBlank() && blacklistedEntityTypes.contains(normalizedEntityType));
    }

    private static String readString(ConfigurationSection configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static Set<String> readNormalizedSet(
        ConfigurationSection configuration,
        String path,
        Function<String, String> normalizer
    ) {
        Set<String> result = new LinkedHashSet<>();
        for (String rawValue : configuration.getStringList(path)) {
            String normalized = normalizer.apply(rawValue);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }
}



