package xuanmo.arcartxsuite.map.config;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherLoader;

public record MapModuleConfiguration(
    boolean debug,
    ClientConfiguration client,
    KeybindConfiguration keybinds,
    JoinConfiguration join,
    StorageConfiguration storage,
    NavigationConfiguration navigation,
    Map<String, WorldDefinition> worlds,
    List<DefaultUnlockRule> defaultUnlocks,
    Map<String, AnchorDefinition> anchors,
    WaypointConfiguration waypoints
) {

    private static final String DEFAULT_PACKET_ID = "AXS_MAP";
    private static final String DEFAULT_MENU_UI_ID = "AXS:map_menu";
    private static final String DEFAULT_HUD_UI_ID = "AXS:map_hud";

    public static MapModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, logger, null);
    }

    public static MapModuleConfiguration load(FileConfiguration configuration, Logger logger, File anchorsDirectory) {
        boolean debug = configuration.getBoolean("debug", false);

        ConfigurationSection clientSection = configuration.getConfigurationSection("client");
        ClientConfiguration client = new ClientConfiguration(
            string(clientSection == null ? null : clientSection.getString("packet-id"), DEFAULT_PACKET_ID),
            string(clientSection == null ? null : clientSection.getString("menu-ui-id"), DEFAULT_MENU_UI_ID),
            string(clientSection == null ? null : clientSection.getString("hud-ui-id"), DEFAULT_HUD_UI_ID),
            clientSection == null || clientSection.getBoolean("register-ui-on-enable", true),
            clientSection != null && clientSection.getBoolean("overwrite-ui-files", false)
        );

        ConfigurationSection keybindSection = configuration.getConfigurationSection("keybinds");
        KeybindConfiguration keybinds = new KeybindConfiguration(
            string(keybindSection == null ? null : keybindSection.getString("category"), "AXS Map"),
            readKeybind(keybindSection == null ? null : keybindSection.getConfigurationSection("open-menu"), "打开地图", "M"),
            readKeybind(keybindSection == null ? null : keybindSection.getConfigurationSection("toggle-hud"), "切换小地图", "H")
        );

        ConfigurationSection joinSection = configuration.getConfigurationSection("join");
        JoinConfiguration join = new JoinConfiguration(
            joinSection == null || joinSection.getBoolean("show-hud-on-join", true),
            joinSection == null ? 20L : Math.max(0L, joinSection.getLong("show-hud-delay-ticks", 20L))
        );

        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        StorageConfiguration storage = new StorageConfiguration(
            StorageDialect.parse(storageSection == null ? null : storageSection.getString("mode", "sqlite")),
            storageSection == null ? "map.db" : string(storageSection.getString("sqlite.file", "map.db"), "map.db"),
            storageSection == null ? "127.0.0.1" : string(storageSection.getString("mysql.host", "127.0.0.1"), "127.0.0.1"),
            storageSection == null ? 3306 : Math.max(1, storageSection.getInt("mysql.port", 3306)),
            storageSection == null ? "arcartxsuite" : string(storageSection.getString("mysql.database", "arcartxsuite"), "arcartxsuite"),
            storageSection == null ? "root" : string(storageSection.getString("mysql.username", "root"), "root"),
            storageSection == null ? "" : string(storageSection.getString("mysql.password", ""), ""),
            storageSection == null ? 4 : Math.max(1, storageSection.getInt("pool-size", 4))
        );

        ConfigurationSection navigationSection = configuration.getConfigurationSection("navigation");
        NavigationConfiguration navigation = new NavigationConfiguration(
            navigationSection == null || navigationSection.getBoolean("enabled", true),
            string(navigationSection == null ? null : navigationSection.getString("waypoint-style-id"), "default"),
            string(navigationSection == null ? null : navigationSection.getString("anchor-id-prefix"), "AXS-map-anchor-"),
            string(navigationSection == null ? null : navigationSection.getString("waypoint-id-prefix"), "AXS-map-track-")
        );

        Map<String, WorldDefinition> worlds = loadWorlds(configuration.getConfigurationSection("worlds"));
        List<DefaultUnlockRule> defaultUnlocks = loadDefaultUnlockRules(configuration.getMapList("default-unlocks"));
        WaypointConfiguration waypoints = loadWaypoints(configuration.getConfigurationSection("waypoints"));
        Map<String, AnchorDefinition> anchors = new LinkedHashMap<>();
        if (anchorsDirectory != null && anchorsDirectory.isDirectory()) {
            loadAnchorsFromDirectory(anchorsDirectory, anchors, logger);
        }

        return new MapModuleConfiguration(
            debug,
            client,
            keybinds,
            join,
            storage,
            navigation,
            immutableCopy(worlds),
            List.copyOf(defaultUnlocks),
            immutableCopy(anchors),
            waypoints
        );
    }

    public WorldDefinition world(String worldId) {
        return worlds.get(normalizeId(worldId));
    }

    public AnchorDefinition anchor(String anchorId) {
        return anchors.get(normalizeId(anchorId));
    }

    private static KeybindDefinition readKeybind(ConfigurationSection section, String defaultName, String defaultKey) {
        return new KeybindDefinition(
            section == null || section.getBoolean("enabled", true),
            string(section == null ? null : section.getString("display-name"), defaultName),
            string(section == null ? null : section.getString("default-key"), defaultKey)
        );
    }

    private static Map<String, WorldDefinition> loadWorlds(ConfigurationSection section) {
        LinkedHashMap<String, WorldDefinition> values = new LinkedHashMap<>();
        if (section == null) {
            values.put("world", new WorldDefinition("world", "主世界", "Map/world.png", 2048, 2048, 0, 0, 1.6D, 0.18D, 180));
            return values;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) {
                continue;
            }
            String id = normalizeId(rawId);
            values.put(
                id,
                new WorldDefinition(
                    id,
                    string(child.getString("display-name", rawId), rawId),
                    string(child.getString("texture"), ""),
                    Math.max(1, child.getInt("image-width", 2048)),
                    Math.max(1, child.getInt("image-height", 2048)),
                    child.getInt("pixel-offset-x", 0),
                    child.getInt("pixel-offset-z", 0),
                    positiveDouble(child.get("default-zoom"), 1.6D),
                    positiveDouble(child.get("hud-zoom"), 0.18D),
                    Math.max(64, child.getInt("hud-size", 180))
                )
            );
        }
        return values;
    }

    private static List<DefaultUnlockRule> loadDefaultUnlockRules(List<Map<?, ?>> rawRules) {
        List<DefaultUnlockRule> rules = new ArrayList<>();
        if (rawRules == null) {
            return rules;
        }
        for (Map<?, ?> rawRule : rawRules) {
            String permission = string(rawRule.get("permission"), "");
            List<String> anchors = normalizeStringList(rawList(rawRule.get("anchors")));
            if (!permission.isBlank() && !anchors.isEmpty()) {
                rules.add(new DefaultUnlockRule(permission, anchors));
            }
        }
        return rules;
    }

    private static WaypointConfiguration loadWaypoints(ConfigurationSection section) {
        List<WaypointLimit> limits = new ArrayList<>();
        if (section != null) {
            List<Map<?, ?>> rawLimits = section.getMapList("limits");
            for (Map<?, ?> rawLimit : rawLimits) {
                String permission = string(rawLimit.get("permission"), "");
                int maxCount = positiveInt(rawLimit.get("max-count"), 5);
                limits.add(new WaypointLimit(permission, maxCount));
            }
        }
        return new WaypointConfiguration(
            section == null || section.getBoolean("enabled", true),
            string(section == null ? null : section.getString("default-style-id"), "default"),
            string(section == null ? null : section.getString("id-prefix"), "AXS-map-wp-"),
            string(section == null ? null : section.getString("auto-name-prefix"), "标记点"),
            section == null ? 5 : Math.max(1, section.getInt("default-max-count", 5)),
            List.copyOf(limits)
        );
    }

    private static Map<String, AnchorDefinition> loadAnchors(ConfigurationSection section, Logger logger) {
        LinkedHashMap<String, AnchorDefinition> values = new LinkedHashMap<>();
        if (section == null) {
            values.put(
                "spawn",
                new AnchorDefinition(
                    "spawn",
                    "新手村",
                    "world",
                    0D,
                    80D,
                    0D,
                    "默认示例锚点。",
                    "",
                    0,
                    List.of(),
                    List.of(),
                    List.of()
                )
            );
            return values;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) {
                continue;
            }
            String id = normalizeId(rawId);
            values.put(
                id,
                new AnchorDefinition(
                    id,
                    string(child.getString("display-name", rawId), rawId),
                    normalizeId(child.getString("world", "")),
                    child.getDouble("x"),
                    child.getDouble("y"),
                    child.getDouble("z"),
                    string(child.getString("description", ""), ""),
                    string(child.getString("permission", ""), ""),
                    child.getInt("sort-order", values.size()),
                    loadCurrencyCosts(child.getMapList("unlock-currencies")),
                    loadCurrencyCosts(child.getMapList("teleport-currencies")),
                    loadItemCosts(child.getMapList("unlock-items"), logger, "anchors." + id + ".unlock-items")
                )
            );
        }
        return values;
    }

    private static void loadAnchorsFromDirectory(
        File directory,
        Map<String, AnchorDefinition> target,
        Logger logger
    ) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String rawId : yaml.getKeys(false)) {
                ConfigurationSection child = yaml.getConfigurationSection(rawId);
                if (child == null || !child.getBoolean("enabled", true)) continue;
                String id = normalizeId(rawId);
                target.put(id, new AnchorDefinition(
                    id,
                    string(child.getString("display-name", rawId), rawId),
                    normalizeId(child.getString("world", "")),
                    child.getDouble("x"),
                    child.getDouble("y"),
                    child.getDouble("z"),
                    string(child.getString("description", ""), ""),
                    string(child.getString("permission", ""), ""),
                    child.getInt("sort-order", target.size()),
                    loadCurrencyCosts(child.getMapList("unlock-currencies")),
                    loadCurrencyCosts(child.getMapList("teleport-currencies")),
                    loadItemCosts(child.getMapList("unlock-items"), logger, "anchors." + id + ".unlock-items")
                ));
            }
        }
    }

    private static List<CurrencyCost> loadCurrencyCosts(List<Map<?, ?>> rawCosts) {
        List<CurrencyCost> values = new ArrayList<>();
        if (rawCosts == null) {
            return List.of();
        }
        for (Map<?, ?> rawCost : rawCosts) {
            String currencyId = normalizeId(rawCost.get("currency"));
            BigDecimal amount = parseDecimal(rawCost.get("amount"), BigDecimal.ZERO);
            if (!currencyId.isBlank() && amount.compareTo(BigDecimal.ZERO) > 0) {
                values.add(new CurrencyCost(currencyId, amount));
            }
        }
        return List.copyOf(values);
    }

    private static List<ItemCost> loadItemCosts(List<Map<?, ?>> rawCosts, Logger logger, String path) {
        List<ItemCost> values = new ArrayList<>();
        if (rawCosts == null) {
            return List.of();
        }
        for (int index = 0; index < rawCosts.size(); index++) {
            Map<?, ?> rawCost = rawCosts.get(index);
            int amount = positiveInt(rawCost.get("amount"), 1);
            ItemMatcher matcher = ItemMatcherLoader.load(
                mapToSection(rawCost.get("matcher")),
                logger,
                path + "[" + index + "].matcher"
            );
            if (!matcher.emptyMatcher()) {
                values.add(new ItemCost(amount, matcher));
            }
        }
        return List.copyOf(values);
    }

    private static ConfigurationSection mapToSection(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        org.bukkit.configuration.MemoryConfiguration configuration = new org.bukkit.configuration.MemoryConfiguration();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            configuration.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return configuration;
    }

    private static List<String> rawList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(String.valueOf(value));
    }

    private static List<String> normalizeStringList(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String normalizedValue = normalizeId(value);
            if (!normalizedValue.isBlank()) {
                normalized.add(normalizedValue);
            }
        }
        return List.copyOf(normalized);
    }

    private static String string(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static int positiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue() > 0 ? number.intValue() : fallback;
        }
        if (value instanceof String string) {
            try {
                int parsed = Integer.parseInt(string.trim());
                return parsed > 0 ? parsed : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double positiveDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue() > 0.0D ? number.doubleValue() : fallback;
        }
        if (value instanceof String string) {
            try {
                double parsed = Double.parseDouble(string.trim());
                return parsed > 0.0D ? parsed : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static BigDecimal parseDecimal(Object rawValue, BigDecimal fallback) {
        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (rawValue != null) {
            try {
                return new BigDecimal(String.valueOf(rawValue).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String normalizeId(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public record ClientConfiguration(
        String packetId,
        String menuUiId,
        String hudUiId,
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
    }

    public record KeybindConfiguration(
        String category,
        KeybindDefinition openMenu,
        KeybindDefinition toggleHud
    ) {
    }

    public record KeybindDefinition(
        boolean enabled,
        String displayName,
        String defaultKey
    ) {
    }

    public record JoinConfiguration(
        boolean showHudOnJoin,
        long showHudDelayTicks
    ) {
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
            return "mysql".equalsIgnoreCase(string(rawValue, "sqlite")) ? MYSQL : SQLITE;
        }
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

    public record NavigationConfiguration(
        boolean enabled,
        String waypointStyleId,
        String anchorIdPrefix,
        String waypointIdPrefix
    ) {
    }

    public record WorldDefinition(
        String id,
        String displayName,
        String texture,
        int imageWidth,
        int imageHeight,
        int pixelOffsetX,
        int pixelOffsetZ,
        double defaultZoom,
        double hudZoom,
        int hudSize
    ) {
    }

    public record DefaultUnlockRule(
        String permission,
        List<String> anchorIds
    ) {
    }

    public record AnchorDefinition(
        String id,
        String displayName,
        String worldId,
        double x,
        double y,
        double z,
        String description,
        String permission,
        int sortOrder,
        List<CurrencyCost> unlockCurrencies,
        List<CurrencyCost> teleportCurrencies,
        List<ItemCost> unlockItems
    ) {
    }

    public record CurrencyCost(
        String currencyId,
        BigDecimal amount
    ) {
    }

    public record ItemCost(
        int amount,
        ItemMatcher matcher
    ) {
    }

    public record WaypointConfiguration(
        boolean enabled,
        String defaultStyleId,
        String idPrefix,
        String autoNamePrefix,
        int defaultMaxCount,
        List<WaypointLimit> limits
    ) {
    }

    public record WaypointLimit(
        String permission,
        int maxCount
    ) {
    }
}
