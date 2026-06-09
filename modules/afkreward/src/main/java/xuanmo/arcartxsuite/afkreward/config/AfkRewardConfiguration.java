package xuanmo.arcartxsuite.afkreward.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;
import xuanmo.arcartxsuite.afkreward.model.AfkRewardType;

public record AfkRewardConfiguration(
    boolean debug,
    RewardConfig reward,
    Map<String, AfkRewardType> types,
    Map<String, AfkArea> areas,
    StorageConfig storage,
    UiConfig ui,
    ManualConfig manual
) {

    public record RewardConfig(
        int roundMinutes,
        MaxConfig max,
        PlayerLimitConfig player
    ) {
        public record MaxConfig(boolean enabled, int limit) {}
        public record PlayerLimitConfig(boolean enabled, int limit) {}
    }

    public record StorageConfig(
        Dialect dialect, String sqliteFile,
        String host, int port, String database,
        String username, String password,
        String tablePrefix, int poolSize
    ) {
        public enum Dialect {
            SQLITE, MYSQL;
            public static Dialect from(String s) {
                return "mysql".equalsIgnoreCase(s) ? MYSQL : SQLITE;
            }
        }

        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (dialect == Dialect.MYSQL) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFile);
        }
    }

    public record UiConfig(
        String hudId,
        boolean registerOnEnable,
        boolean overwriteUiFile
    ) {}

    public record ManualConfig(
        boolean restrictActions,
        boolean returnOnEnd,
        boolean broadcastRewards,
        int leaderboardSize
    ) {}

    public static AfkRewardConfiguration load(YamlConfiguration yaml, Logger logger) {
        boolean debug = yaml.getBoolean("debug", false);

        // reward
        ConfigurationSection rewardSec = yaml.getConfigurationSection("reward");
        RewardConfig reward = new RewardConfig(
            rewardSec != null ? Math.max(1, rewardSec.getInt("round", 15)) : 15,
            new RewardConfig.MaxConfig(
                rewardSec != null && rewardSec.getBoolean("max.enable", true),
                rewardSec != null ? rewardSec.getInt("max.limit", 32) : 32
            ),
            new RewardConfig.PlayerLimitConfig(
                rewardSec != null && rewardSec.getBoolean("player.enable", true),
                rewardSec != null ? rewardSec.getInt("player.limit", 30) : 30
            )
        );

        // types
        Map<String, AfkRewardType> types = new LinkedHashMap<>();
        ConfigurationSection typesSec = yaml.getConfigurationSection("types");
        if (typesSec != null) {
            for (String typeName : typesSec.getKeys(false)) {
                ConfigurationSection typeSec = typesSec.getConfigurationSection(typeName);
                if (typeSec == null) continue;
                String describe = typeSec.getString("describe", "");
                Map<String, List<String>> tiers = new LinkedHashMap<>();
                for (String tierKey : typeSec.getKeys(false)) {
                    if ("describe".equals(tierKey)) continue;
                    List<String> cmds = typeSec.getStringList(tierKey);
                    if (!cmds.isEmpty()) {
                        tiers.put(tierKey, new ArrayList<>(cmds));
                    }
                }
                types.put(typeName, new AfkRewardType(typeName, describe, Collections.unmodifiableMap(tiers)));
            }
        }

        // areas
        Map<String, AfkArea> areas = new LinkedHashMap<>();
        ConfigurationSection areasSec = yaml.getConfigurationSection("areas");
        if (areasSec != null) {
            for (String areaName : areasSec.getKeys(false)) {
                ConfigurationSection areaSec = areasSec.getConfigurationSection(areaName);
                if (areaSec == null) continue;
                boolean enabled = areaSec.getBoolean("enable", true);
                String world = areaSec.getString("world", "");
                List<String> posList = areaSec.getStringList("pos");
                String type = areaSec.getString("type", areaName);
                boolean manualEnabled = areaSec.getBoolean("manual-enabled", true);
                List<AfkArea.Point> points = new ArrayList<>();
                for (String pos : posList) {
                    String[] parts = pos.split(",");
                    if (parts.length >= 2) {
                        try {
                            int x = Integer.parseInt(parts[0].trim());
                            int z = Integer.parseInt(parts[1].trim());
                            points.add(new AfkArea.Point(x, z));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                // 读取传送点（原地挂机用）
                Location teleport = null;
                ConfigurationSection tpSec = areaSec.getConfigurationSection("teleport");
                if (tpSec != null) {
                    String tpWorld = tpSec.getString("world", world);
                    double tpx = tpSec.getDouble("x", 0);
                    double tpy = tpSec.getDouble("y", 64);
                    double tpz = tpSec.getDouble("z", 0);
                    float yaw = (float) tpSec.getDouble("yaw", 0);
                    float pitch = (float) tpSec.getDouble("pitch", 0);
                    teleport = AfkArea.buildTeleport(tpWorld, tpx, tpy, tpz, yaw, pitch);
                }
                if (points.size() >= 3) {
                    areas.put(areaName, new AfkArea(areaName, enabled, world, type,
                        Collections.unmodifiableList(points), teleport, manualEnabled));
                } else {
                    logger.warning("[AfkReward] 区域 '" + areaName + "' 的坐标点不足 3 个，已跳过。");
                }
            }
        }

        // storage
        ConfigurationSection storSec = yaml.getConfigurationSection("storage");
        StorageConfig storage = new StorageConfig(
            StorageConfig.Dialect.from(storSec != null ? storSec.getString("dialect", "sqlite") : "sqlite"),
            storSec != null ? storSec.getString("sqlite-file", "afkreward.db") : "afkreward.db",
            storSec != null ? storSec.getString("host", "127.0.0.1") : "127.0.0.1",
            storSec != null ? storSec.getInt("port", 3306) : 3306,
            storSec != null ? storSec.getString("database", "arcartxsuite") : "arcartxsuite",
            storSec != null ? storSec.getString("username", "root") : "root",
            storSec != null ? storSec.getString("password", "") : "",
            storSec != null ? storSec.getString("table-prefix", "axs_afk_") : "axs_afk_",
            storSec != null ? storSec.getInt("pool-size", 3) : 3
        );

        // ui
        ConfigurationSection uiSec = yaml.getConfigurationSection("ui");
        UiConfig ui = new UiConfig(
            uiSec != null ? uiSec.getString("hud-id", "AXS:afk_reward_hud") : "AXS:afk_reward_hud",
            uiSec != null && uiSec.getBoolean("register-on-enable", true),
            uiSec != null && uiSec.getBoolean("overwrite-ui-file", false)
        );

        // manual
        ConfigurationSection manualSec = yaml.getConfigurationSection("manual");
        ManualConfig manual = new ManualConfig(
            manualSec != null && manualSec.getBoolean("restrict-actions", true),
            manualSec != null && manualSec.getBoolean("return-on-end", false),
            manualSec != null && manualSec.getBoolean("broadcast-rewards", true),
            manualSec != null ? Math.max(1, Math.min(100, manualSec.getInt("leaderboard-size", 10))) : 10
        );

        return new AfkRewardConfiguration(debug, reward, Collections.unmodifiableMap(types),
            Collections.unmodifiableMap(areas), storage, ui, manual);
    }
}
