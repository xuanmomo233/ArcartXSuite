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

public record LotteryModuleConfiguration(
    StorageConfiguration storage,
    String poolsDirectory,
    Map<String, String> sharedPityGroups,
    Map<String, PoolDefinition> pools
) {

    public static LotteryModuleConfiguration load(YamlConfiguration yaml, File dataFolder, Logger logger) {
        StorageConfiguration storage = StorageConfiguration.load(yaml.getConfigurationSection("storage"));
        String poolsDir = yaml.getString("pools-directory", "lottery/pools");
        Map<String, String> sharedGroups = loadSharedPityGroups(yaml.getConfigurationSection("shared-pity-groups"));

        File poolsDirectory = new File(dataFolder, poolsDir);
        Map<String, PoolDefinition> pools = loadPoolsFromDirectory(poolsDirectory, logger);

        return new LotteryModuleConfiguration(storage, poolsDir, sharedGroups, pools);
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

        File[] files = directory.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".yml"));
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

        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            boolean isMysql = "mysql".equalsIgnoreCase(mode);
            if (isMysql) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, poolSize, "");
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }
    }
}
