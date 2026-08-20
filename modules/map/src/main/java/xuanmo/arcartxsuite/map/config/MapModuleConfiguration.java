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

/**
 * 地图模块配置记录，从 {@code ArcartXMap.yml} 解析的完整配置快照。
 * <p>
 * 关联模块：map。包含客户端通信、按键绑定、入服行为、存储、导航、
 * 世界定义、默认解锁规则、锚点定义及路径点配置等全部子节。
 * 通过 {@link #load(FileConfiguration, Logger, File)} 工厂方法从 YAML 加载。
 *
 * @param debug         是否开启调试日志
 * @param client        客户端通信与 UI 配置
 * @param keybinds      按键绑定配置
 * @param join          入服行为配置
 * @param storage       存储后端配置
 * @param navigation    导航功能配置
 * @param worlds        世界定义映射（键为世界 ID）
 * @param defaultUnlocks 默认解锁规则列表
 * @param anchors       锚点定义映射（键为锚点 ID）
 * @param waypoints     路径点功能配置
 */
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

    /**
     * 从 YAML 配置加载模块配置（不加载外部锚点目录）。
     *
     * @param configuration YAML 配置对象
     * @param logger        日志记录器
     * @return 解析后的模块配置实例
     */
    public static MapModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, logger, null);
    }

    /**
     * 从 YAML 配置加载模块配置，并从指定目录加载外部锚点定义文件。
     *
     * @param configuration    YAML 配置对象
     * @param logger           日志记录器
     * @param anchorsDirectory 锚点定义文件目录，为 {@code null} 时跳过外部锚点加载
     * @return 解析后的模块配置实例
     */
    public static MapModuleConfiguration load(FileConfiguration configuration, Logger logger, File anchorsDirectory) {
        boolean debug = configuration.getBoolean("debug", false);

        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        ClientConfiguration client = new ClientConfiguration(
            string(uiSection == null ? null : uiSection.getString("packet-id"), DEFAULT_PACKET_ID),
            string(uiSection == null ? null : uiSection.getString("menu-ui-id"), DEFAULT_MENU_UI_ID),
            string(uiSection == null ? null : uiSection.getString("hud-ui-id"), DEFAULT_HUD_UI_ID),
            uiSection == null || uiSection.getBoolean("register-ui-on-enable", true),
            uiSection != null && uiSection.getBoolean("overwrite-ui-files", false)
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
            storageSection == null ? "sqlite" : string(storageSection.getString("mode", "sqlite"), "sqlite"),
            storageSection == null ? "map.db" : string(storageSection.getString("sqlite.file", "map.db"), "map.db"),
            storageSection == null ? "127.0.0.1" : string(storageSection.getString("mysql.host", "127.0.0.1"), "127.0.0.1"),
            storageSection == null ? 3306 : Math.max(1, storageSection.getInt("mysql.port", 3306)),
            storageSection == null ? "arcartxsuite" : string(storageSection.getString("mysql.database", "arcartxsuite"), "arcartxsuite"),
            storageSection == null ? "root" : string(storageSection.getString("mysql.username", "root"), "root"),
            storageSection == null ? "" : string(storageSection.getString("mysql.password", ""), ""),
            storageSection == null ? "axs_map_" : string(storageSection.getString("mysql.table-prefix", "axs_map_"), "axs_map_"),
            storageSection == null ? 4 : Math.max(1, storageSection.getInt("pool-size", 4))
        );

        ConfigurationSection navigationSection = configuration.getConfigurationSection("navigation");
        ConfigurationSection markerSection = navigationSection == null ? null : navigationSection.getConfigurationSection("marker");
        MarkerConfiguration marker = new MarkerConfiguration(
            markerSection == null || markerSection.getBoolean("enabled", false),
            string(markerSection == null ? null : markerSection.getString("model-id"), "nav_beacon"),
            markerSection == null ? 1.0 : markerSection.getDouble("scale", 1.0),
            string(markerSection == null ? null : markerSection.getString("default-state"), "idle"),
            string(markerSection == null ? null : markerSection.getString("animation"), "rotate"),
            markerSection == null ? 2.0 : markerSection.getDouble("y-offset", 2.0),
            markerSection == null ? 3.0 : markerSection.getDouble("path-interval", 3.0),
            markerSection == null ? 20 : markerSection.getInt("path-max-markers", 20),
            markerSection == null ? 10 : markerSection.getInt("path-update-ticks", 10),
            markerSection == null ? 64.0 : markerSection.getDouble("path-max-distance", 64.0),
            markerSection == null ? 2000 : markerSection.getInt("path-max-iterations", 2000)
        );
        NavigationConfiguration navigation = new NavigationConfiguration(
            navigationSection == null || navigationSection.getBoolean("enabled", true),
            string(navigationSection == null ? null : navigationSection.getString("waypoint-style-id"), "default"),
            string(navigationSection == null ? null : navigationSection.getString("anchor-id-prefix"), "AXS-map-anchor-"),
            string(navigationSection == null ? null : navigationSection.getString("waypoint-id-prefix"), "AXS-map-track-"),
            marker
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

    /**
     * 按世界 ID 获取世界定义。
     *
     * @param worldId 世界 ID（不区分大小写）
     * @return 世界定义，不存在时返回 {@code null}
     */
    public WorldDefinition world(String worldId) {
        return worlds.get(normalizeId(worldId));
    }

    /**
     * 按锚点 ID 获取锚点定义。
     *
     * @param anchorId 锚点 ID（不区分大小写）
     * @return 锚点定义，不存在时返回 {@code null}
     */
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

    /**
     * 客户端通信与 UI 配置。
     *
     * @param packetId           数据包标识
     * @param menuUiId           菜单 UI 标识
     * @param hudUiId            HUD UI 标识
     * @param registerUiOnEnable 启用时是否注册 UI
     * @param overwriteUiFiles   启用时是否覆盖已有 UI 文件
     */
    public record ClientConfiguration(
        String packetId,
        String menuUiId,
        String hudUiId,
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
    }

    /**
     * 按键绑定配置。
     *
     * @param category   按键分类名称
     * @param openMenu   打开地图菜单的按键定义
     * @param toggleHud  切换小地图 HUD 的按键定义
     */
    public record KeybindConfiguration(
        String category,
        KeybindDefinition openMenu,
        KeybindDefinition toggleHud
    ) {
    }

    /**
     * 单个按键定义。
     *
     * @param enabled     是否启用
     * @param displayName 显示名称
     * @param defaultKey  默认按键
     */
    public record KeybindDefinition(
        boolean enabled,
        String displayName,
        String defaultKey
    ) {
    }

    /**
     * 入服行为配置。
     *
     * @param showHudOnJoin     入服时是否自动显示 HUD
     * @param showHudDelayTicks 显示 HUD 的延迟 tick 数
     */
    public record JoinConfiguration(
        boolean showHudOnJoin,
        long showHudDelayTicks
    ) {
    }

    /**
     * 存储后端配置。
     *
     * @param mode          存储模式（sqlite / mysql）
     * @param sqliteFileName SQLite 文件名
     * @param host          MySQL 主机地址
     * @param port          MySQL 端口
     * @param database      MySQL 数据库名
     * @param username      MySQL 用户名
     * @param password      MySQL 密码
     * @param tablePrefix   MySQL 表名前缀
     * @param poolSize      连接池大小
     */
    public record StorageConfiguration(
        String mode,
        String sqliteFileName,
        String host,
        int port,
        String database,
        String username,
        String password,
        String tablePrefix,
        int poolSize
    ) {
        public boolean isSqlite() {
            return !"mysql".equalsIgnoreCase(mode);
        }

        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (!isSqlite()) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }

        /**
         * 判断是否为覆盖模式（模块自建连接池）。
         * <p>
         * 当 mode=mysql 时返回 true（模块独立连接 MySQL，向后兼容旧配置）；
         * mode=sqlite 时返回 false（使用本体共享数据源，各模块仍用各自的 .db 文件）。
         *
         * @return {@code true} 表示模块应自建连接池（旧路径）
         * @since 1.4.0
         */
        public boolean hasOverride() {
            return "mysql".equalsIgnoreCase(mode);
        }
    }

    /**
     * 导航功能配置。
     *
     * @param enabled           是否启用导航
     * @param waypointStyleId   路径点样式 ID
     * @param anchorIdPrefix    锚点路径点 ID 前缀
     * @param waypointIdPrefix  自定义路径点 ID 前缀
     * @param marker            导航标记模型配置
     */
    public record NavigationConfiguration(
        boolean enabled,
        String waypointStyleId,
        String anchorIdPrefix,
        String waypointIdPrefix,
        MarkerConfiguration marker
    ) {
    }

    /**
     * 导航标记模型配置，控制路径标记实体的外观与行为。
     *
     * @param enabled          是否启用标记模型
     * @param modelId          模型 ID
     * @param scale            缩放比例
     * @param defaultState     默认状态
     * @param animation        动画类型
     * @param yOffset          Y 轴偏移
     * @param pathInterval     路径标记间距
     * @param pathMaxMarkers   路径最大标记数
     * @param pathUpdateTicks  路径更新间隔 tick
     * @param pathMaxDistance  路径最大距离
     * @param pathMaxIterations 路径计算最大迭代次数
     */
    public record MarkerConfiguration(
        boolean enabled,
        String modelId,
        double scale,
        String defaultState,
        String animation,
        double yOffset,
        double pathInterval,
        int pathMaxMarkers,
        int pathUpdateTicks,
        double pathMaxDistance,
        int pathMaxIterations
    ) implements xuanmo.arcartxsuite.api.util.NavigationMarkerConfig {
        public static MarkerConfiguration defaults() {
            return new MarkerConfiguration(false, "nav_beacon", 1.0, "idle", "rotate", 2.0,
                3.0, 20, 10, 64.0, 2000);
        }
    }

    /**
     * 世界定义，描述单个世界的地图纹理与显示参数。
     *
     * @param id            世界 ID
     * @param displayName   显示名称
     * @param texture       纹理文件路径
     * @param imageWidth    纹理图片宽度（像素）
     * @param imageHeight   纹理图片高度（像素）
     * @param pixelOffsetX  像素 X 偏移
     * @param pixelOffsetZ  像素 Z 偏移
     * @param defaultZoom   菜单默认缩放
     * @param hudZoom       HUD 缩放
     * @param hudSize       HUD 尺寸（像素）
     */
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

    /**
     * 默认解锁规则，按权限自动为玩家解锁指定锚点。
     *
     * @param permission 所需权限节点
     * @param anchorIds  自动解锁的锚点 ID 列表
     */
    public record DefaultUnlockRule(
        String permission,
        List<String> anchorIds
    ) {
    }

    /**
     * 锚点定义，描述一个可解锁 / 可传送的地图标记点。
     *
     * @param id                 锚点 ID
     * @param displayName        显示名称
     * @param worldId            所在世界 ID
     * @param x                  X 坐标
     * @param y                  Y 坐标
     * @param z                  Z 坐标
     * @param description        描述文本
     * @param permission         所需权限节点
     * @param sortOrder          排序权重
     * @param unlockCurrencies   解锁所需货币列表
     * @param teleportCurrencies 传送所需货币列表
     * @param unlockItems        解锁所需物品列表
     */
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

    /**
     * 货币消耗定义。
     *
     * @param currencyId 货币 ID
     * @param amount     消耗金额
     */
    public record CurrencyCost(
        String currencyId,
        BigDecimal amount
    ) {
    }

    /**
     * 物品消耗定义。
     *
     * @param amount  消耗数量
     * @param matcher 物品匹配器
     */
    public record ItemCost(
        int amount,
        ItemMatcher matcher
    ) {
    }

    /**
     * 路径点功能配置。
     *
     * @param enabled         是否启用自定义路径点
     * @param defaultStyleId  默认样式 ID
     * @param idPrefix        路径点 ID 前缀
     * @param autoNamePrefix  自动命名前缀
     * @param defaultMaxCount 默认每世界最大数量
     * @param limits          按权限区分的数量限制列表
     */
    public record WaypointConfiguration(
        boolean enabled,
        String defaultStyleId,
        String idPrefix,
        String autoNamePrefix,
        int defaultMaxCount,
        List<WaypointLimit> limits
    ) {
    }

    /**
     * 路径点数量限制规则，按权限区分。
     *
     * @param permission 权限节点
     * @param maxCount   每世界最大路径点数量
     */
    public record WaypointLimit(
        String permission,
        int maxCount
    ) {
    }
}
