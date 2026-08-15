package xuanmo.arcartxsuite.lottery.config;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lottery 模块顶层配置。
 * <p>
 * 持有存储配置、奖池目录路径、共享保底组映射以及全部奖池定义。
 * 由 {@link #load} 从主配置 YAML 与奖池目录加载。
 *
 * @param storage          存储配置
 * @param poolsDirectory   奖池文件目录相对路径
 * @param sharedPityGroups 共享保底组名到组 ID 的映射
 * @param pools            奖池 ID 到奖池定义的映射
 */
public record LotteryModuleConfiguration(
    StorageConfiguration storage,
    String poolsDirectory,
    String exchangeDirectory,
    boolean overwriteUiFiles,
    Map<String, String> sharedPityGroups,
    Map<String, PoolDefinition> pools,
    Map<String, ExchangeShopItem> exchangeItems
) {

    /**
     * 从主配置 YAML 加载模块配置，并扫描奖池目录加载全部奖池定义。
     *
     * @param yaml       主配置 YAML
     * @param dataFolder 模块数据目录
     * @param logger     日志记录器
     * @return 解析后的 {@link LotteryModuleConfiguration} 实例
     */
    public static LotteryModuleConfiguration load(YamlConfiguration yaml, File dataFolder, Logger logger) {
        StorageConfiguration storage = StorageConfiguration.load(yaml.getConfigurationSection("storage"));
        String poolsDir = yaml.getString("pools-directory", "lottery/pools");
        String exchangeDir = yaml.getString("exchange-directory", "lottery/exchange");
        Map<String, String> sharedGroups = loadSharedPityGroups(yaml.getConfigurationSection("shared-pity-groups"));

        File poolsDirectory = new File(dataFolder, poolsDir);
        Map<String, PoolDefinition> pools = loadPoolsFromDirectory(poolsDirectory, logger);
        Map<String, PointDefinition> knownPoints = new LinkedHashMap<>();
        for (PoolDefinition pool : pools.values()) {
            for (PointDefinition point : pool.points().values()) {
                PointDefinition previous = knownPoints.putIfAbsent(point.id(), point);
                if (previous != null && (!previous.displayName().equals(point.displayName())
                        || !previous.icon().equals(point.icon()))) {
                    logger.warning("Lottery point definition conflict, keeping first: " + point.id());
                }
            }
        }
        Map<String, ExchangeShopItem> exchangeItems =
            loadExchangeItems(new File(dataFolder, exchangeDir), logger);

        boolean overwriteUiFiles = yaml.getBoolean("ui.overwrite-ui-files", false);

        return new LotteryModuleConfiguration(storage, poolsDir, exchangeDir, overwriteUiFiles, sharedGroups, pools, exchangeItems);
    }

    private static Map<String, String> loadSharedPityGroups(@Nullable ConfigurationSection section) {
        Map<String, String> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static Map<String, PoolDefinition> loadPoolsFromDirectory(File directory, Logger logger) {
        Map<String, PoolDefinition> pools = new LinkedHashMap<>();
        if (!directory.exists() || !directory.isDirectory()) {
            logger.warning("Lottery 奖池目录不存在: " + directory.getPath());
            return pools;
        }

        File[] files = directory.listFiles(file -> file.isFile()
            && (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".yml")
                || file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".yaml")));
        if (files == null) return pools;

        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            try {
                YamlConfiguration poolYaml = YamlConfiguration.loadConfiguration(file);
                PoolDefinition pool = PoolDefinition.load(poolYaml, logger);
                if (pool == null || !pool.enabled()) continue;

                if (pools.containsKey(pool.id())) {
                    logger.warning("Lottery 奖池 ID 重复，已覆盖: " + pool.id());
                }
                pools.put(pool.id(), pool);
            } catch (Exception e) {
                logger.warning("加载奖池文件失败: " + file.getName() + " - " + e.getMessage());
            }
        }

        logger.info("Lottery 已加载 " + pools.size() + " 个奖池");
        return pools;
    }

    private static Map<String, ExchangeShopItem> loadExchangeItems(File directory, Logger logger) {
        Map<String, ExchangeShopItem> result = new LinkedHashMap<>();
        if (!directory.exists() || !directory.isDirectory()) return result;
        File[] files = directory.listFiles(file -> file.isFile()
            && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files == null) return result;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection items = yaml.getConfigurationSection("items");
                if (items == null) continue;
                for (String key : items.getKeys(false)) {
                    ConfigurationSection section = items.getConfigurationSection(key);
                    if (section == null) continue;
                    ExchangeShopItem item = ExchangeShopItem.load(section, logger);
                    if (result.putIfAbsent(item.id(), item) != null) {
                        logger.warning("Lottery exchange item duplicated: " + item.id());
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to load exchange file " + file.getName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * 存储子配置。
     * <p>
     * 支持 SQLite 与 MySQL 两种模式，通过 {@link #toDescriptor} 转换为
     * {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor} 供存储层使用。
     *
     * @param mode           存储模式（sqlite 或 mysql）
     * @param sqliteFileName SQLite 文件名
     * @param poolSize       连接池大小
     * @param mysqlHost      MySQL 主机地址
     * @param mysqlPort      MySQL 端口
     * @param mysqlDatabase  MySQL 数据库名
     * @param mysqlUsername  MySQL 用户名
     * @param mysqlPassword  MySQL 密码
     * @param tablePrefix    表名前缀
     */
    public record StorageConfiguration(
        String mode,
        String sqliteFileName,
        int poolSize,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String tablePrefix
    ) {
        /**
         * 从配置节加载存储配置。
         *
         * @param section 配置节，为 {@code null} 时使用默认值
         * @return 解析后的 {@link StorageConfiguration} 实例
         */
        public static StorageConfiguration load(@Nullable ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            return new StorageConfiguration(
                section.getString("mode", "sqlite"),
                section.getString("sqlite-file-name", "lottery.db"),
                section.getInt("pool-size", 1),
                section.getString("mysql-host", "localhost"),
                section.getInt("mysql-port", 3306),
                section.getString("mysql-database", "axs_lottery"),
                section.getString("mysql-username", "root"),
                section.getString("mysql-password", ""),
                section.getString("table-prefix", "lottery_")
            );
        }

        /**
         * 将存储配置转换为存储描述符。
         *
         * @return 对应的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}
         */
        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            boolean isMysql = "mysql".equalsIgnoreCase(mode);
            if (isMysql) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, poolSize, "");
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
}
