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
 * @param ui               UI 配置
 * @param poolsDirectory   奖池文件目录相对路径
 * @param sharedPityGroups 共享保底组映射: key 为共享保底池 ID，value 为匹配奖池 ID 的通配符
 * @param pools            奖池 ID 到奖池定义的映射
 */
public record LotteryModuleConfiguration(
    StorageConfiguration storage,
    UiConfiguration ui,
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
        Map<String, ExchangeShopItem> exchangeItems =
            loadExchangeItems(new File(dataFolder, exchangeDir), logger);

        boolean overwriteUiFiles = yaml.getBoolean("ui.overwrite-ui-files", false);
        UiConfiguration ui = UiConfiguration.load(yaml.getConfigurationSection("ui"));

        return new LotteryModuleConfiguration(storage, ui, poolsDir, exchangeDir, overwriteUiFiles, sharedGroups, pools, exchangeItems);
    }

    /**
     * UI 子配置。
     * <p>
     * 指定祈愿/开箱界面绑定的 UI ID，允许腐竹将 UI 重映射到自定义 ID。
     *
     * @param gachaUiId 祈愿（GACHA）界面 UI ID
     * @param caseUiId  开箱（CASE）界面 UI ID
     * @param wishUiId  祈愿（WISH）界面 UI ID
     */
    public record UiConfiguration(
        String gachaUiId,
        String caseUiId,
        String wishUiId
    ) {
        /**
         * 从 {@code ui} 配置节加载 UI 配置。
         *
         * @param section 配置节，为 {@code null} 时使用默认值
         * @return 解析后的 {@link UiConfiguration} 实例
         */
        public static UiConfiguration load(@Nullable ConfigurationSection section) {
            if (section == null) {
                return new UiConfiguration("AXS:lottery_gacha", "AXS:lottery_case", "AXS:lottery_wish");
            }
            return new UiConfiguration(
                section.getString("gacha-ui-id", "AXS:lottery_gacha"),
                section.getString("case-ui-id", "AXS:lottery_case"),
                section.getString("wish-ui-id", "AXS:lottery_wish")
            );
        }
    }

    /** 加载共享保底组配置: key = 共享保底池 ID，value = 匹配奖池 ID 的通配符。 */
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
     * <p>
     * 当 {@code shared=true} 时，模块使用本体共享数据源（由本体 config.yml 的 storage 节统一管控）；
     * 当 {@code shared=false} 时，模块自建独立连接池（不推荐）。
     *
     * @param shared         是否使用共享存储模式（true=共享本体数据源，false=自建连接池）
     * @param mode           存储模式（sqlite 或 mysql），仅在 shared=false 时生效
     * @param sqliteFileName SQLite 文件名，仅在 shared=false 时生效
     * @param host           MySQL 主机地址，仅在 shared=false 时生效
     * @param port           MySQL 端口，仅在 shared=false 时生效
     * @param database       MySQL 数据库名，仅在 shared=false 时生效
     * @param username       MySQL 用户名，仅在 shared=false 时生效
     * @param password       MySQL 密码，仅在 shared=false 时生效
     * @param tablePrefix    表名前缀，仅在 shared=false 时生效
     * @param poolSize       连接池大小，仅在 shared=false 时生效
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
        /**
         * 从配置节加载存储配置。
         * <p>
         * shared=true 时使用默认值（不读取其余字段）；shared=false 时读取 mode/sqlite/mysql 配置。
         *
         * @param section 配置节，为 {@code null} 时使用默认值（shared=true）
         * @return 解析后的 {@link StorageConfiguration} 实例
         */
        public static StorageConfiguration load(@Nullable ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            boolean shared = section.getBoolean("shared", true);
            // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
            if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
                shared = false;
            }
            if (shared) {
                return new StorageConfiguration(true, "sqlite", "lottery.db",
                    "127.0.0.1", 3306, "arcartxsuite", "root", "",
                    "axs_lottery_", 1);
            }
            return new StorageConfiguration(false,
                section.getString("mode", "sqlite"),
                section.getString("sqlite.file", "lottery.db"),
                section.getString("mysql.host", "127.0.0.1"),
                section.getInt("mysql.port", 3306),
                section.getString("mysql.database", "arcartxsuite"),
                section.getString("mysql.username", "root"),
                section.getString("mysql.password", ""),
                section.getString("mysql.table-prefix", "axs_lottery_"),
                section.getInt("pool-size", 1)
            );
        }

        /**
         * 判断当前存储模式是否为 SQLite（非 mysql 均视为 SQLite）。
         *
         * @return {@code true} 表示使用 SQLite
         */
        public boolean isSqlite() {
            return !"mysql".equalsIgnoreCase(mode);
        }

        /**
         * 将存储配置转换为存储描述符。
         *
         * @return 对应的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}
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
         *
         * @return {@code true} 表示模块应自建连接池（shared=false）
         * @since 1.4.0
         */
        public boolean hasOverride() {
            return !shared;
        }
    }
}
