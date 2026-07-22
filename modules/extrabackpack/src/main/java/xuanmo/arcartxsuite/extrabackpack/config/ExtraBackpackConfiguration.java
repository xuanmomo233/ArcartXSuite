package xuanmo.arcartxsuite.extrabackpack.config;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record ExtraBackpackConfiguration(
    boolean debug,
    UiConfiguration ui,
    StorageConfiguration storage,
    ExtraBackpackConfig extraBackpack
) {

    public static ExtraBackpackConfiguration load(FileConfiguration configuration, Logger logger) {
        boolean debug = configuration.getBoolean("settings.debug", false);
        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        UiConfiguration ui = new UiConfiguration(
            uiSection == null || uiSection.getBoolean("register-ui-on-enable", true),
            uiSection != null && uiSection.getBoolean("overwrite-ui-files", false)
        );
        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        StorageConfiguration storage = new StorageConfiguration(
            StorageDialect.parse(storageSection == null ? null : storageSection.getString("mode", "sqlite")),
            storageSection == null ? "extrabackpack.db" : readString(storageSection, "sqlite.file", "extrabackpack.db"),
            storageSection == null ? "127.0.0.1" : readString(storageSection, "mysql.host", "127.0.0.1"),
            storageSection == null ? 3306 : Math.max(1, storageSection.getInt("mysql.port", 3306)),
            storageSection == null ? "arcartxsuite" : readString(storageSection, "mysql.database", "arcartxsuite"),
            storageSection == null ? "root" : readString(storageSection, "mysql.username", "root"),
            storageSection == null ? "" : readString(storageSection, "mysql.password", ""),
            storageSection == null ? 4 : Math.max(1, storageSection.getInt("pool-size", 4))
        );
        ExtraBackpackConfig extraBackpack =
            loadExtraBackpack(configuration.getConfigurationSection("extra-backpack"), logger);
        return new ExtraBackpackConfiguration(debug, ui, storage, extraBackpack);
    }

    private static ExtraBackpackConfig loadExtraBackpack(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return ExtraBackpackConfig.DISABLED;
        }
        boolean enabled = section.getBoolean("enabled", false);
        LinkedHashMap<String, ExtraBackpackCategory> categories = new LinkedHashMap<>();
        ConfigurationSection categoriesSection = section.getConfigurationSection("categories");
        if (categoriesSection != null) {
            for (String rawId : categoriesSection.getKeys(false)) {
                ConfigurationSection child = categoriesSection.getConfigurationSection(rawId);
                if (child == null) {
                    continue;
                }
                String id = normalizeId(rawId);
                int initial = Math.max(0, child.getInt("slots.initial", child.getInt("initial-slots", 9)));
                int max = Math.max(initial, child.getInt("slots.max", child.getInt("max-slots", initial)));
                String currencyId = normalizeId(child.getString("price.currency", ""));
                BigDecimal perSlot = parseDecimal(child.get("price.per-slot"), BigDecimal.ZERO);
                UpgradeCost price = currencyId.isBlank() || perSlot.compareTo(BigDecimal.ZERO) <= 0
                    ? null
                    : new UpgradeCost(currencyId, perSlot);
                String path = readString(child, "match.nbt.path", readString(child, "nbt.path", ""));
                List<String> values = normalizeStringList(child.getStringList("match.nbt.values"));
                if (values.isEmpty()) {
                    values = normalizeStringList(child.getStringList("nbt.values"));
                }
                boolean fallback = child.getBoolean("default", false);
                categories.put(id, new ExtraBackpackCategory(
                    id,
                    readString(child, "display-name", rawId),
                    child.getInt("priority", categories.size()),
                    initial,
                    max,
                    price,
                    path,
                    values,
                    fallback
                ));
            }
        }
        if (enabled && categories.isEmpty() && logger != null) {
            logger.warning("extra-backpack enabled but no categories are configured.");
        }
        return new ExtraBackpackConfig(enabled, immutableCopy(categories));
    }

    private static String readString(ConfigurationSection section, String path, String defaultValue) {
        String value = section.getString(path, defaultValue);
        return value == null ? defaultValue : value.trim();
    }

    private static BigDecimal parseDecimal(Object rawValue, BigDecimal defaultValue) {
        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (rawValue != null) {
            try {
                return new BigDecimal(String.valueOf(rawValue).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = normalizeId(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeId(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public enum StorageDialect {
        SQLITE("sqlite"),
        MYSQL("mysql");

        private final String configKey;

        StorageDialect(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }

        public static StorageDialect parse(String rawValue) {
            return "mysql".equals(normalizeId(rawValue)) ? MYSQL : SQLITE;
        }
    }

    public record UiConfiguration(
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
    }

    public record StorageConfiguration(
        StorageDialect dialect,
        String sqliteFileName,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        int connectionPoolSize
    ) {
        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (dialect == StorageDialect.MYSQL) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }
    }

    public record UpgradeCost(String currencyId, BigDecimal amount) {
    }

    public record ExtraBackpackConfig(
        boolean enabled,
        Map<String, ExtraBackpackCategory> categories
    ) {
        public static final ExtraBackpackConfig DISABLED =
            new ExtraBackpackConfig(false, Map.of());
    }

    public record ExtraBackpackCategory(
        String id,
        String displayName,
        int priority,
        int initialSlots,
        int maxSlots,
        UpgradeCost pricePerSlot,
        String nbtPath,
        List<String> values,
        boolean fallback
    ) {
    }
}
