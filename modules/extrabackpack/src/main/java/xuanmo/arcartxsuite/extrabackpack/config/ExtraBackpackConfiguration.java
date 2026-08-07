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

/**
 * ExtraBackpack 模块的根配置 record，承载调试开关、UI、存储与扩展背包分类的全部配置。
 * <p>
 * 通过 {@link #load(FileConfiguration, Logger)} 从 Bukkit {@link FileConfiguration} 解析构建，
 * 供 {@link xuanmo.arcartxsuite.extrabackpack.ExtraBackpackModule} 在启动时读取。
 *
 * @param debug       是否开启调试日志
 * @param ui          UI 相关配置（是否注册 UI、是否覆盖 UI 文件）
 * @param storage     存储相关配置（方言、连接信息、连接池大小等）
 * @param extraBackpack 扩展背包功能配置（是否启用、分类列表）
 */
public record ExtraBackpackConfiguration(
    boolean debug,
    UiConfiguration ui,
    StorageConfiguration storage,
    ExtraBackpackConfig extraBackpack
) {

    /**
     * 从 Bukkit 配置文件解析并构建 {@link ExtraBackpackConfiguration}。
     *
     * @param configuration Bukkit 配置对象
     * @param logger        模块日志，用于输出配置警告；可为 {@code null}
     * @return 解析后的配置实例
     */
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

    /**
     * 存储方言枚举，区分 SQLite 与 MySQL 两种持久化模式。
     */
    public enum StorageDialect {
        SQLITE("sqlite"),
        MYSQL("mysql");

        private final String configKey;

        StorageDialect(String configKey) {
            this.configKey = configKey;
        }

        /**
         * 返回该方言在配置文件中对应的键值。
         *
         * @return 配置键字符串
         */
        public String configKey() {
            return configKey;
        }

        /**
         * 将配置中的原始字符串解析为方言枚举，无法识别时默认返回 {@link #SQLITE}。
         *
         * @param rawValue 原始字符串值
         * @return 解析后的方言
         */
        public static StorageDialect parse(String rawValue) {
            return "mysql".equals(normalizeId(rawValue)) ? MYSQL : SQLITE;
        }
    }

    /**
     * UI 相关配置 record。
     *
     * @param registerUiOnEnable 启用时是否自动注册 UI 资源
     * @param overwriteUiFiles   启用时是否覆盖已存在的 UI 文件
     */
    public record UiConfiguration(
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
    }

    /**
     * 存储相关配置 record，封装 SQLite/MySQL 连接参数与连接池大小。
     * <p>
     * 通过 {@link #toDescriptor()} 转换为 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}，
     * 供 {@link xuanmo.arcartxsuite.extrabackpack.storage.JdbcExtraBackpackRepository} 构造连接池使用。
     *
     * @param dialect           存储方言（SQLite / MySQL）
     * @param sqliteFileName    SQLite 模式下的数据库文件名
     * @param mysqlHost         MySQL 主机地址
     * @param mysqlPort         MySQL 端口
     * @param mysqlDatabase     MySQL 数据库名
     * @param mysqlUsername     MySQL 用户名
     * @param mysqlPassword     MySQL 密码
     * @param connectionPoolSize 连接池大小
     */
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
        /**
         * 将本配置转换为 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}，
         * 根据 {@link #dialect} 选择 SQLite 或 MySQL 描述符。
         *
         * @return 存储描述符
         */
        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (dialect == StorageDialect.MYSQL) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }

        /**
         * 判断是否为覆盖模式（模块自建连接池）。
         * <p>
         * 当 dialect=MYSQL 时返回 true（模块独立连接 MySQL，向后兼容旧配置）；
         * dialect=SQLITE 时返回 false（使用本体共享数据源，各模块仍用各自的 .db 文件）。
         *
         * @return {@code true} 表示模块应自建连接池（旧路径）
         * @since 1.4.0
         */
        public boolean hasOverride() {
            return dialect == StorageDialect.MYSQL;
        }
    }

    /**
     * 扩展背包槽位升级费用 record。
     *
     * @param currencyId 货币标识
     * @param amount     单个槽位的升级金额
     */
    public record UpgradeCost(String currencyId, BigDecimal amount) {
    }

    /**
     * 扩展背包功能配置 record，包含是否启用以及分类映射。
     * <p>
     * {@link #DISABLED} 为禁用时的单例默认值。
     *
     * @param enabled    是否启用扩展背包功能
     * @param categories 分类标识到分类配置的有序映射
     */
    public record ExtraBackpackConfig(
        boolean enabled,
        Map<String, ExtraBackpackCategory> categories
    ) {
        /**
         * 禁用状态下的默认配置单例。
         */
        public static final ExtraBackpackConfig DISABLED =
            new ExtraBackpackConfig(false, Map.of());
    }

    /**
     * 扩展背包分类配置 record，描述单个分类的显示、槽位、升级费用与 NBT 匹配规则。
     *
     * @param id           分类标识（小写归一化）
     * @param displayName  显示名称
     * @param priority     优先级，数值越大越优先匹配
     * @param initialSlots 初始槽位数量
     * @param maxSlots     最大槽位数量
     * @param pricePerSlot 每个槽位的升级费用；为 {@code null} 表示不可升级
     * @param nbtPath      NBT 匹配路径
     * @param values       NBT 匹配值列表
     * @param fallback     是否作为兜底分类（无匹配项时使用）
     */
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
