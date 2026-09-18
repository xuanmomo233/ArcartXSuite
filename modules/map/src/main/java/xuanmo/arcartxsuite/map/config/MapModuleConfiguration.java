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
 * 关联模块：map。包含客户端通信、入服行为、存储、导航、
 * 世界定义、锚点定义（含锚点级自动解锁策略）及路径点配置等全部子节。
 * 通过 {@link #load(FileConfiguration, Logger, File)} 工厂方法从 YAML 加载。
 *
 * @param debug         是否开启调试日志
 * @param client        客户端通信与 UI 配置
 * @param join          入服行为配置
 * @param storage       存储后端配置
 * @param navigation    导航功能配置
 * @param worlds        世界定义映射（键为世界 ID）
 * @param anchors       锚点定义映射（键为锚点 ID）
 * @param waypoints     路径点功能配置
 * @param share         点位分享配置
 */
public record MapModuleConfiguration(
    boolean debug,
    ClientConfiguration client,
    JoinConfiguration join,
    StorageConfiguration storage,
    NavigationConfiguration navigation,
    Map<String, WorldDefinition> worlds,
    Map<String, AnchorDefinition> anchors,
    WaypointConfiguration waypoints,
    ShareConfiguration share
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

        ConfigurationSection joinSection = configuration.getConfigurationSection("join");
        JoinConfiguration join = new JoinConfiguration(
            joinSection == null || joinSection.getBoolean("show-hud-on-join", true),
            joinSection == null ? 20L : Math.max(0L, joinSection.getLong("show-hud-delay-ticks", 20L))
        );

        StorageConfiguration storage = StorageConfiguration.load(configuration.getConfigurationSection("storage"));

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
            markerSection == null ? 2000 : markerSection.getInt("path-max-iterations", 2000),
            markerSection == null ? 8.0 : markerSection.getDouble("path-reshow-buffer", 8.0)
        );
        ConfigurationSection waypointSection = navigationSection == null ? null : navigationSection.getConfigurationSection("waypoint");
        NavWaypointConfiguration waypoint = new NavWaypointConfiguration(
            waypointSection == null || waypointSection.getBoolean("enabled", true),
            string(waypointSection == null ? null : waypointSection.getString("style-id"), "default"),
            string(waypointSection == null ? null : waypointSection.getString("title-template"), "")
        );
        NavigationConfiguration navigation = new NavigationConfiguration(
            navigationSection == null || navigationSection.getBoolean("enabled", true),
            string(navigationSection == null ? null : navigationSection.getString("anchor-id-prefix"), "AXS-map-anchor-"),
            string(navigationSection == null ? null : navigationSection.getString("waypoint-id-prefix"), "AXS-map-track-"),
            navigationSection == null ? 20 : navigationSection.getInt("refresh-ticks", 20),
            navigationSection == null ? 30 : navigationSection.getInt("world-hint-interval", 30),
            navigationSection == null ? 0.0 : navigationSection.getDouble("finish-range", 0.0),
            normalizeStringList(navigationSection == null ? List.of() : navigationSection.getStringList("finish-commands")),
            navigationSection == null ? 3.0 : navigationSection.getDouble("finish-rearm-buffer", 3.0),
            waypoint,
            marker
        );

        // 世界和锚点从 worlds/ 目录加载（每个世界一个 yml 文件，内含 anchors 节）
        Map<String, WorldDefinition> worlds = new LinkedHashMap<>();
        Map<String, AnchorDefinition> anchors = new LinkedHashMap<>();
        if (anchorsDirectory != null && anchorsDirectory.isDirectory()) {
            loadWorldsFromDirectory(anchorsDirectory, worlds, anchors, logger);
        }
        // 兼容：worlds 节仍可直接写在 config.yml（旧格式降级）
        if (worlds.isEmpty()) {
            ConfigurationSection worldsSection = configuration.getConfigurationSection("worlds");
            if (worldsSection != null) {
                loadWorldsInline(worldsSection, worlds, anchors, logger);
                if (!worlds.isEmpty()) {
                    logger.warning("检测到 ArcartXMap.yml 内联 worlds 节（已弃用）："
                        + "该格式仅在没有 worlds/ 目录文件时才生效，请迁移到 worlds/<world>.yml 后删除此节。");
                }
            }
        }

        if (configuration.isSet("default-unlocks")) {
            logger.warning("ArcartXMap.yml 中的 default-unlocks 节已移除，请在 worlds/<world>.yml 锚点的 unlock 节配置 default / on-permission。");
        }
        WaypointConfiguration waypoints = loadWaypoints(configuration.getConfigurationSection("waypoints"));

        ConfigurationSection shareSection = configuration.getConfigurationSection("share");
        ShareConfiguration share = new ShareConfiguration(
            shareSection == null || shareSection.getBoolean("enabled", true),
            string(shareSection == null ? null : shareSection.getString("card-id"), "axs_map_share"),
            shareSection == null ? 30 : Math.max(1, shareSection.getInt("expire-minutes", 30))
        );

        return new MapModuleConfiguration(
            debug,
            client,
            join,
            storage,
            navigation,
            immutableCopy(worlds),
            immutableCopy(anchors),
            waypoints,
            share
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

    /**
     * 从 worlds/ 目录加载世界和锚点。每个 {@code <worldId>.yml} 文件含世界配置 + anchors 节。
     * 文件名（去掉 .yml）即世界 ID。
     */
    private static void loadWorldsFromDirectory(
        File directory,
        Map<String, WorldDefinition> worlds,
        Map<String, AnchorDefinition> anchors,
        Logger logger
    ) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            String worldId = normalizeId(file.getName().substring(0, file.getName().length() - 4));
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (!yaml.getBoolean("enabled", true)) continue;
            // preload 节：radius（圆半径）、regions（多边形列表）、center（可选圆心，
            // 不配则按 MV→ess→Bukkit 出生点链解析）
            ConfigurationSection preload = yaml.getConfigurationSection("preload");
            double preloadRadius = preload == null ? 0D
                : positiveDouble(preload.get("radius"), 0D);
            List<PolygonRegion> preloadRegions = loadPolygonList(
                preload == null ? List.of() : preload.getMapList("regions"),
                logger, "worlds." + worldId + ".preload.regions");
            ConfigurationSection center = preload == null ? null
                : preload.getConfigurationSection("center");
            Double preloadCenterX = center == null || !center.isSet("x") ? null
                : center.getDouble("x");
            Double preloadCenterZ = center == null || !center.isSet("z") ? null
                : center.getDouble("z");
            // 预加载版本 = 配置内容 + 文件 mtime 哈希：改配置/文件自动失效客户端缓存。
            // 掩码到正数：客户端以 version<=0 表示"无预加载"，哈希负数会静默失效。
            int preloadVersion = (preloadRadius > 0 || !preloadRegions.isEmpty())
                ? Math.max(1, java.util.Objects.hash(worldId, preloadRadius,
                    preloadRegions, preloadCenterX, preloadCenterZ,
                    file.lastModified()) & 0x7FFFFFFF)
                : 0;
            worlds.put(worldId, new WorldDefinition(
                worldId,
                string(yaml.getString("display-name"), ""),
                preloadRadius,
                preloadRegions,
                preloadVersion,
                preloadCenterX,
                preloadCenterZ
            ));
            // 解析 anchors 节
            ConfigurationSection anchorsSection = yaml.getConfigurationSection("anchors");
            if (anchorsSection != null) {
                loadAnchorsInSection(anchorsSection, worldId, anchors, logger);
            }
        }
    }

    /** 兼容旧格式：从 config.yml 的 worlds 节内联加载（无 anchors，锚点仍从 anchors/ 目录）。 */
    private static void loadWorldsInline(
        ConfigurationSection section,
        Map<String, WorldDefinition> worlds,
        Map<String, AnchorDefinition> anchors,
        Logger logger
    ) {
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) continue;
            String id = normalizeId(rawId);
            worlds.put(id, new WorldDefinition(
                id,
                string(child.getString("display-name"), ""),
                0D, List.of(), 0, null, null
            ));
        }
    }

    /** 从 ConfigurationSection 加载锚点（世界文件内的 anchors 节）。 */
    private static void loadAnchorsInSection(
        ConfigurationSection section,
        String worldId,
        Map<String, AnchorDefinition> target,
        Logger logger
    ) {
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) continue;
            String id = normalizeId(rawId);
            if (target.containsKey(id)) {
                AnchorDefinition existing = target.get(id);
                logger.warning("锚点 id " + id + " 重复定义（先前在世界 " + existing.worldId()
                    + "，本次在世界 " + worldId + "），后者覆盖前者——全局锚点 id 必须唯一。");
            }
            String permission = string(child.getString("permission", ""), "");
            ConfigurationSection unlockSection = child.getConfigurationSection("unlock");
            AnchorUnlock unlock = loadAnchorUnlock(unlockSection);
            if (unlock.onPermission() && permission.isBlank()) {
                logger.warning("世界 " + worldId + " 的锚点 " + id + " 配置了 unlock.on-permission 但未设置 permission，自动解锁不会生效。");
            }
            // light 节：radius（圆形点亮半径）+ region（多边形点亮区域）
            ConfigurationSection lightSection = child.getConfigurationSection("light");
            // teleport 节：currencies/items（每次传送消耗）
            ConfigurationSection teleportSection = child.getConfigurationSection("teleport");
            target.put(id, new AnchorDefinition(
                id,
                string(child.getString("display-name", rawId), rawId),
                worldId,
                child.getDouble("x"),
                child.getDouble("y"),
                child.getDouble("z"),
                string(child.getString("description", ""), ""),
                permission,
                child.getInt("sort-order", target.size()),
                loadCurrencyCosts(unlockSection == null ? List.of() : unlockSection.getMapList("currencies")),
                loadItemCosts(unlockSection == null ? List.of() : unlockSection.getMapList("items"),
                    logger, "anchors." + id + ".unlock.items"),
                loadCurrencyCosts(teleportSection == null ? List.of() : teleportSection.getMapList("currencies")),
                loadItemCosts(teleportSection == null ? List.of() : teleportSection.getMapList("items"),
                    logger, "anchors." + id + ".teleport.items"),
                unlock,
                loadLightRegion(lightSection == null ? null : lightSection.getConfigurationSection("region")),
                lightSection == null ? 0D : positiveDouble(lightSection.get("radius"), 0D)
            ));
        }
    }

    /**
     * 解析锚点的 {@code unlock} 自动解锁节。配置格式：
     * <pre>{@code
     * unlock:
     *   default: true        # 所有玩家进服时自动免费解锁
     *   on-permission: true  # 拥有锚点 permission 权限的玩家进服时自动解锁
     *   radius: 8            # 手动解锁三维距离门槛（>0 时玩家须在锚点 radius 格内）
     *   currencies: [...]    # 手动解锁货币消耗
     *   items: [...]         # 手动解锁物品消耗
     * }</pre>
     */
    private static AnchorUnlock loadAnchorUnlock(ConfigurationSection section) {
        if (section == null) {
            return AnchorUnlock.NONE;
        }
        return new AnchorUnlock(
            section.getBoolean("default", false),
            section.getBoolean("on-permission", false),
            positiveDouble(section.get("radius"), 0D)
        );
    }

    /**
     * 解析多边形区域列表（世界 preload.regions 用）。配置格式：
     * <pre>{@code
     * preload:
     *   regions:
     *     - points: [[x, z], [x, z], ...]
     *     - points: [[x, z], ...]
     * }</pre>
     */
    private static List<PolygonRegion> loadPolygonList(
        List<Map<?, ?>> list, Logger logger, String path
    ) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<PolygonRegion> out = new ArrayList<>();
        int i = 0;
        for (Map<?, ?> entry : list) {
            Object raw = entry == null ? null : entry.get("points");
            List<int[]> points = new ArrayList<>();
            if (raw instanceof List<?> pointList) {
                for (Object p : pointList) {
                    if (p instanceof List<?> pair && pair.size() >= 2) {
                        points.add(new int[]{toInt(pair.get(0)), toInt(pair.get(1))});
                    } else if (p instanceof Map<?, ?> pairMap) {
                        points.add(new int[]{toInt(pairMap.get("x")), toInt(pairMap.get("z"))});
                    }
                }
            }
            if (points.size() >= 3) {
                out.add(new PolygonRegion("preload-" + i, List.copyOf(points)));
            } else {
                logger.warning(path + "[" + i + "] 顶点不足 3 个，跳过该区域");
            }
            i++;
        }
        return out;
    }

    /**
     * 解析单个多边形区域（锚点 light.region 用）。配置格式：
     * <pre>{@code
     * light:
     *   region:
     *     points: [[x, z], [x, z], ...]
     * }</pre>
     */
    private static List<PolygonRegion> loadLightRegion(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<int[]> points = new ArrayList<>();
        Object raw = section.get("points");
        if (raw instanceof List<?> pointList) {
            for (Object p : pointList) {
                if (p instanceof List<?> pair && pair.size() >= 2) {
                    points.add(new int[]{toInt(pair.get(0)), toInt(pair.get(1))});
                } else if (p instanceof Map<?, ?> pairMap) {
                    points.add(new int[]{toInt(pairMap.get("x")), toInt(pairMap.get("z"))});
                }
            }
        }
        if (points.size() >= 3) {
            return List.of(new PolygonRegion("light", List.copyOf(points)));
        }
        return List.of();
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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

    /**
     * 解析物品消耗列表（fishing 同款 match 模糊匹配器格式）。配置格式：
     * <pre>{@code
     * items:
     *   - amount: 3
     *     match:
     *       material-ids: [diamond]
     *   - amount: 1
     *     match:
     *       mythic-item-ids: [teleport_scroll]
     * }</pre>
     */
    private static List<ItemCost> loadItemCosts(List<Map<?, ?>> rawCosts, Logger logger, String path) {
        List<ItemCost> values = new ArrayList<>();
        if (rawCosts == null) {
            return List.of();
        }
        for (int index = 0; index < rawCosts.size(); index++) {
            Map<?, ?> rawCost = rawCosts.get(index);
            int amount = positiveInt(rawCost.get("amount"), 1);
            ItemMatcher matcher = ItemMatcherLoader.load(
                mapToSection(rawCost.get("match")),
                logger,
                path + "[" + index + "].match"
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

    private static String string(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static List<String> normalizeStringList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(raw.size());
        for (String entry : raw) {
            if (entry != null && !entry.isBlank()) {
                result.add(entry.trim());
            }
        }
        return List.copyOf(result);
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
     * @param shared          是否共享存储模式（true=共享本体数据源，false=模块自建连接池）
     * @param mode          存储模式（sqlite / mysql），仅在 shared=false 时有意义
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
        boolean shared,
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
         * 当 shared=false 时返回 true（模块独立连接，自建存储模式）；
         * shared=true 时返回 false（使用本体共享数据源）。
         *
         * @return {@code true} 表示模块应自建连接池
         * @since 1.4.0
         */
        public boolean hasOverride() {
            return !shared;
        }

        /**
         * 从配置节加载存储配置。
         * <p>
         * shared=true（默认）时使用默认值，不读 mode/sqlite/mysql；
         * shared=false 时读取自建存储模式的全部字段。
         *
         * @param section storage 配置节，为 {@code null} 时按共享模式处理
         * @return 解析后的存储配置实例
         */
        public static StorageConfiguration load(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            boolean shared = section.getBoolean("shared", true);
            // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
            if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
                shared = false;
            }
            if (shared) {
                return new StorageConfiguration(true, "sqlite", "map.db",
                    "127.0.0.1", 3306, "arcartxsuite", "root", "",
                    "axs_map_", 4);
            }
            return new StorageConfiguration(false,
                string(section.getString("mode"), "sqlite"),
                string(section.getString("sqlite.file"), "map.db"),
                string(section.getString("mysql.host"), "127.0.0.1"),
                Math.max(1, section.getInt("mysql.port", 3306)),
                string(section.getString("mysql.database"), "arcartxsuite"),
                string(section.getString("mysql.username"), "root"),
                string(section.getString("mysql.password"), ""),
                string(section.getString("mysql.table-prefix"), "axs_map_"),
                Math.max(1, section.getInt("pool-size", 4))
            );
        }
    }

    /**
     * 导航功能配置。
     *
     * @param enabled           是否启用导航
     * @param anchorIdPrefix    锚点路径点 ID 前缀
     * @param waypointIdPrefix  自定义路径点 ID 前缀
     * @param refreshTicks      目标刷新间隔 tick（diff 推送与到达判定）
     * @param worldHintIntervalSeconds 跨世界提示节流间隔（秒），0 = 不提示
     * @param finishRange       到达触发半径（格），0 = 不启用
     * @param finishCommands    到达命令（控制台执行，%player% 占位）
     * @param rearmBuffer       到达判定滞回缓冲（格）
     * @param waypoint          ArcartX 路标渲染配置（目标点光柱与标题）
     * @param marker            导航标记模型配置
     */
    public record NavigationConfiguration(
        boolean enabled,
        String anchorIdPrefix,
        String waypointIdPrefix,
        int refreshTicks,
        int worldHintIntervalSeconds,
        double finishRange,
        List<String> finishCommands,
        double rearmBuffer,
        NavWaypointConfiguration waypoint,
        MarkerConfiguration marker
    ) {
    }

    /**
     * 导航用 ArcartX 路标（waypoint）渲染配置：目标点光柱与标题，样式在 ArcartX waypoint 目录定义。
     * 与 {@code waypoints.*}（玩家自定义路径点功能）的 WaypointConfiguration 区分。
     *
     * @param enabled       是否向客户端推送路标（false 时导航仅保留追踪态/到达判定/路径标记，不发路标包）
     * @param styleId       路标样式 ID
     * @param titleTemplate 路标标题模板（%label%/%distance%/%world%/%x%/%y%/%z%/%type% 占位；空=显示目标名）
     */
    public record NavWaypointConfiguration(
        boolean enabled,
        String styleId,
        String titleTemplate
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
     * @param pathReshowBuffer  距离滞回缓冲（格）：超距隐藏后须回到 maxDistance - 本值 内才重显示
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
        int pathMaxIterations,
        double pathReshowBuffer
    ) implements xuanmo.arcartxsuite.api.util.NavigationMarkerConfig {
        public static MarkerConfiguration defaults() {
            return new MarkerConfiguration(false, "nav_beacon", 1.0, "idle", "rotate", 2.0,
                3.0, 20, 10, 64.0, 2000, 8.0);
        }
    }

    /**
     * 世界定义。地图完全由客户端 mod 渲染，无需纹理参数。
     * 每个世界对应 {@code worlds/<worldId>.yml} 单文件，文件内含世界配置 + anchors 节。
     *
     * @param id             世界 ID（Bukkit world name）
     * @param displayName    显示名称；留空时运行时从 Multiverse-Core colourless alias 补
     * @param preloadRadius  预加载半径（方块，以世界出生点为圆心）；≤0 = 不用圆形预加载
     * @param preloadRegions 预加载多边形区域列表；空 = 无区域预加载
     * @param preloadVersion 预加载版本号（配置内容+文件 mtime 哈希；0 = 无预加载）
     */
    public record WorldDefinition(
        String id,
        String displayName,
        double preloadRadius,
        List<PolygonRegion> preloadRegions,
        int preloadVersion,
        Double preloadCenterX,
        Double preloadCenterZ
    ) {
        /** 该世界是否配置了任何预加载（圆形或区域）。 */
        public boolean hasPreload() {
            return preloadRadius > 0 || !preloadRegions.isEmpty();
        }

        /** 预加载圆形中心是否显式配置（否则按 MV→ess→Bukkit 出生点链解析）。 */
        public boolean hasPreloadCenter() {
            return preloadCenterX != null && preloadCenterZ != null;
        }
    }

    /**
     * 多边形点亮区域（世界 XZ 平面上的多边形）。
     * <p>
     * 顶点列表按顺序构成闭合多边形（至少 3 点），由 PolygonSelection 选区工具
     * 或配置文件手工填写。
     *
     * @param id     区域标识（调试/命令管理用）
     * @param points 顶点列表，每个元素为 [x, z]
     */
    public record PolygonRegion(
        String id,
        List<int[]> points
    ) {
    }

    /**
     * 锚点自动解锁策略（锚点 {@code unlock} 节）。
     * <p>
     * 自动解锁在玩家客户端初始化时免费写入解锁记录，不消耗 unlock.currencies / unlock.items；
     * 解锁记录与 {@code permission} 使用检查相互独立——{@code defaultUnlock} 解锁的玩家
     * 若锚点配了 permission，仍需持有该权限才能传送。
     *
     * @param defaultUnlock 所有玩家进服时自动解锁
     * @param onPermission  拥有锚点 {@code permission} 权限的玩家进服时自动解锁
     * @param radius        手动解锁三维距离门槛（方块）：>0 时玩家须在锚点 radius 格内
     *                      才能点"解锁"；≤0 = 不限距离。自动解锁与 {@code MapAnchorUnlockable#unlock}
     *                      免费强解不受此门限制
     */
    public record AnchorUnlock(
        boolean defaultUnlock,
        boolean onPermission,
        double radius
    ) {
        public static final AnchorUnlock NONE = new AnchorUnlock(false, false, 0D);
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
     * @param unlockCurrencies   解锁所需货币列表（锚点 {@code unlock.currencies}）
     * @param unlockItems        解锁所需物品列表（锚点 {@code unlock.items}）
     * @param teleportCurrencies 传送所需货币列表（锚点 {@code teleport.currencies}）
     * @param teleportItems      传送所需物品列表（锚点 {@code teleport.items}）
     * @param unlock             自动解锁策略（锚点 {@code unlock} 节）
     * @param lightRegions       解锁后点亮的多边形区域列表；空 = 该锚点不用多边形点亮
     * @param lightRadius        解锁后点亮的圆形半径（以锚点为中心）；≤0 = 不用圆形点亮
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
        List<ItemCost> unlockItems,
        List<CurrencyCost> teleportCurrencies,
        List<ItemCost> teleportItems,
        AnchorUnlock unlock,
        List<PolygonRegion> lightRegions,
        double lightRadius
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
     * 物品消耗定义（fishing 同款 match 模糊匹配器）。
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

    /**
     * 路径点/标记点分享配置。
     *
     * @param enabled       分享功能总开关：false 时分享按钮隐藏、share/claim 一律拒绝
     * @param cardId        聊天卡片模板 ID（对应 ArcartX/chat_card/&lt;id&gt;.yml）；
     *                      空字符串 = 不用卡片，降级为纯文本广播（点击文本执行 /map claim）
     * @param expireMinutes 分享链接有效期（分钟），内存缓存，过期后无法领取
     */
    public record ShareConfiguration(
        boolean enabled,
        String cardId,
        int expireMinutes
    ) {
    }
}
