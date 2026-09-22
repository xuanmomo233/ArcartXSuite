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
 * @param flushIntervalTicks 槽位数据异步落盘间隔（ticks，20=1 秒，最小 20）
 * @param ui          UI 相关配置（是否注册 UI、是否覆盖 UI 文件）
 * @param storage     存储相关配置（方言、连接信息、连接池大小等）
 * @param extraBackpack 扩展背包功能配置（是否启用、分类列表）
 */
public record ExtraBackpackConfiguration(
    boolean debug,
    long flushIntervalTicks,
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
        long flushIntervalTicks = Math.max(20L, configuration.getLong("settings.flush-interval-ticks", 100L));
        ConfigurationSection uiSection = configuration.getConfigurationSection("ui");
        UiConfiguration ui = new UiConfiguration(
            uiSection == null || uiSection.getBoolean("register-ui-on-enable", true),
            uiSection != null && uiSection.getBoolean("overwrite-ui-files", false)
        );
        StorageConfiguration storage = StorageConfiguration.load(configuration.getConfigurationSection("storage"));
        ExtraBackpackConfig extraBackpack =
            loadExtraBackpack(configuration.getConfigurationSection("extra-backpack"), logger);
        return new ExtraBackpackConfiguration(debug, flushIntervalTicks, ui, storage, extraBackpack);
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
     * @param shared          是否共享存储模式（true=共享本体数据源，false=模块自建连接池）
     * @param mode           存储模式（sqlite / mysql），仅在 shared=false 时有意义
     * @param sqliteFileName SQLite 模式下的数据库文件名
     * @param host           MySQL 主机地址
     * @param port           MySQL 端口
     * @param database       MySQL 数据库名
     * @param username       MySQL 用户名
     * @param password       MySQL 密码
     * @param tablePrefix    MySQL 表名前缀
     * @param poolSize       连接池大小
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

        /**
         * 将本配置转换为 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}，
         * 根据 {@link #mode} 选择 SQLite 或 MySQL 描述符。
         *
         * @return 存储描述符
         */
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
                return new StorageConfiguration(true, "sqlite", "extrabackpack.db",
                    "127.0.0.1", 3306, "arcartxsuite", "root", "",
                    "axs_extrabackpack_", 4);
            }
            return new StorageConfiguration(false,
                readString(section, "mode", "sqlite"),
                readString(section, "sqlite.file", "extrabackpack.db"),
                readString(section, "mysql.host", "127.0.0.1"),
                Math.max(1, section.getInt("mysql.port", 3306)),
                readString(section, "mysql.database", "arcartxsuite"),
                readString(section, "mysql.username", "root"),
                readString(section, "mysql.password", ""),
                readString(section, "mysql.table-prefix", "axs_extrabackpack_"),
                Math.max(1, section.getInt("pool-size", 4))
            );
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
