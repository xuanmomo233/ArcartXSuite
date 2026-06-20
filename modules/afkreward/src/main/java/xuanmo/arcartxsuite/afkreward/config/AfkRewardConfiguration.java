package xuanmo.arcartxsuite.afkreward.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;
import xuanmo.arcartxsuite.afkreward.model.AfkRewardType;

public record AfkRewardConfiguration(
    boolean debug,
    String areasDirectory,
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
        int leaderboardSize,
        String signalOnReward,
        String signalOnEnd,
        String subtitleOnReward,
        String subtitleOnEnd,
        List<String> endMailPresets
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
                List<String> mailPresets = typeSec.getStringList("mail-presets");
                Map<String, List<String>> tiers = new LinkedHashMap<>();
                for (String tierKey : typeSec.getKeys(false)) {
                    if ("describe".equals(tierKey) || "mail-presets".equals(tierKey)) continue;
                    List<String> cmds = typeSec.getStringList(tierKey);
                    if (!cmds.isEmpty()) {
                        tiers.put(tierKey, new ArrayList<>(cmds));
                    }
                }
                types.put(typeName, new AfkRewardType(typeName, describe,
                    Collections.unmodifiableMap(tiers), Collections.unmodifiableList(mailPresets)));
            }
        }

        // areas 现在由独立配置文件加载，此处仅解析 areas-directory
        String areasDirectory = yaml.getString("areas-directory", "areas");
        Map<String, AfkArea> areas = new LinkedHashMap<>();

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
            manualSec != null ? Math.max(1, Math.min(100, manualSec.getInt("leaderboard-size", 10))) : 10,
            manualSec != null ? manualSec.getString("signal-on-reward", "") : "",
            manualSec != null ? manualSec.getString("signal-on-end", "") : "",
            manualSec != null ? manualSec.getString("subtitle-on-reward", "") : "",
            manualSec != null ? manualSec.getString("subtitle-on-end", "") : "",
            manualSec != null ? manualSec.getStringList("end-mail-presets") : List.of()
        );

        return new AfkRewardConfiguration(debug, areasDirectory, reward, Collections.unmodifiableMap(types),
            Collections.unmodifiableMap(areas), storage, ui, manual);
    }
}
