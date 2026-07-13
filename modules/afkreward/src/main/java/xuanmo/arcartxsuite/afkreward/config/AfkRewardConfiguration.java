package xuanmo.arcartxsuite.afkreward.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;

public record AfkRewardConfiguration(
    boolean debug,
    String areasDirectory,
    Map<String, AfkArea> areas,
    StorageConfig storage,
    UiConfig ui,
    ManualConfig manual,
    AntiAbuseConfig antiAbuse,
    AuditConfig audit,
    PerformanceConfig performance
) {

    public record AntiAbuseConfig(
        TimeLimitConfig timeLimit,
        BotDetectionConfig botDetection,
        IpLimitConfig ipLimit,
        DecayConfig decay
    ) {}

    public record TimeLimitConfig(
        boolean enable, int sessionSeconds, int dailySeconds,
        String onExceed, String exemptPermission
    ) {}

    public record BotDetectionConfig(
        boolean enable, int windowSeconds, int minViewChanges,
        String action, String exemptPermission
    ) {}

    public record IpLimitConfig(boolean enable, String exemptPermission) {}

    public record DecayConfig(
        boolean enable, int startSeconds, double factorPerHour, double minMultiplier
    ) {}

    public record AuditConfig(boolean enable, String file) {}

    public record PerformanceConfig(boolean pauseOnLowTps, double minTps) {}

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
        int combatCooldownSeconds,
        int leaderboardSize,
        String signalOnReward,
        String signalOnEnd,
        String subtitleOnReward,
        String subtitleOnEnd,
        List<String> endMailPresets,
        Protections protections,
        int permissionRecheckSeconds
    ) {
        public record Protections(
            boolean movement,
            boolean teleport,
            boolean interact,
            boolean blockBreak,
            boolean inventory,
            boolean receiveDamage,
            boolean dealDamage,
            boolean entityTarget,
            boolean vehicleEnter,
            boolean interactEntity,
            boolean dropItem,
            boolean swapHand,
            boolean pickupItem,
            boolean experience,
            boolean collidable
        ) {}
    }

    public AfkRewardConfiguration withAreas(Map<String, AfkArea> newAreas) {
        return new AfkRewardConfiguration(
            debug, areasDirectory, Collections.unmodifiableMap(newAreas),
            storage, ui, manual, antiAbuse, audit, performance
        );
    }

    public static AfkRewardConfiguration load(YamlConfiguration yaml, Logger logger) {
        boolean debug = yaml.getBoolean("debug", false);

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
        ConfigurationSection protectionsSec = manualSec != null
            ? manualSec.getConfigurationSection("protections") : null;
        ManualConfig.Protections protections = new ManualConfig.Protections(
            protectionsSec == null || protectionsSec.getBoolean("movement", true),
            protectionsSec == null || protectionsSec.getBoolean("teleport", true),
            protectionsSec == null || protectionsSec.getBoolean("interact", true),
            protectionsSec == null || protectionsSec.getBoolean("block-break", true),
            protectionsSec == null || protectionsSec.getBoolean("inventory", true),
            protectionsSec == null || protectionsSec.getBoolean("receive-damage", true),
            protectionsSec == null || protectionsSec.getBoolean("deal-damage", true),
            protectionsSec == null || protectionsSec.getBoolean("entity-target", true),
            protectionsSec == null || protectionsSec.getBoolean("vehicle-enter", true),
            protectionsSec == null || protectionsSec.getBoolean("interact-entity", true),
            protectionsSec == null || protectionsSec.getBoolean("drop-item", true),
            protectionsSec == null || protectionsSec.getBoolean("swap-hand", true),
            protectionsSec == null || protectionsSec.getBoolean("pickup-item", true),
            protectionsSec == null || protectionsSec.getBoolean("experience", true),
            protectionsSec == null || protectionsSec.getBoolean("collidable", true)
        );
        ManualConfig manual = new ManualConfig(
            manualSec != null && manualSec.getBoolean("restrict-actions", true),
            manualSec != null && manualSec.getBoolean("return-on-end", false),
            manualSec != null && manualSec.getBoolean("broadcast-rewards", true),
            manualSec != null ? Math.max(0, manualSec.getInt("combat-cooldown-seconds", 10)) : 10,
            manualSec != null ? Math.max(1, Math.min(100, manualSec.getInt("leaderboard-size", 10))) : 10,
            manualSec != null ? manualSec.getString("signal-on-reward", "") : "",
            manualSec != null ? manualSec.getString("signal-on-end", "") : "",
            manualSec != null ? manualSec.getString("subtitle-on-reward", "") : "",
            manualSec != null ? manualSec.getString("subtitle-on-end", "") : "",
            manualSec != null ? manualSec.getStringList("end-mail-presets") : List.of(),
            protections,
            manualSec != null ? Math.max(1, manualSec.getInt("permission-recheck-seconds", 5)) : 5
        );

        ConfigurationSection antiAbuseSec = yaml.getConfigurationSection("anti-abuse");
        ConfigurationSection timeLimitSec = antiAbuseSec != null
            ? antiAbuseSec.getConfigurationSection("time-limit") : null;
        ConfigurationSection botSec = antiAbuseSec != null
            ? antiAbuseSec.getConfigurationSection("bot-detection") : null;
        ConfigurationSection ipSec = antiAbuseSec != null
            ? antiAbuseSec.getConfigurationSection("ip-limit") : null;
        ConfigurationSection decaySec = antiAbuseSec != null
            ? antiAbuseSec.getConfigurationSection("decay") : null;
        AntiAbuseConfig antiAbuse = new AntiAbuseConfig(
            new TimeLimitConfig(
                timeLimitSec != null && timeLimitSec.getBoolean("enable", false),
                timeLimitSec != null ? Math.max(0, timeLimitSec.getInt("session-seconds", 0)) : 0,
                timeLimitSec != null ? Math.max(0, timeLimitSec.getInt("daily-seconds", 0)) : 0,
                timeLimitSec != null ? timeLimitSec.getString("on-exceed", "STOP") : "STOP",
                timeLimitSec != null ? timeLimitSec.getString("exempt-permission",
                    "axs.afkreward.bypass.timelimit") : "axs.afkreward.bypass.timelimit"
            ),
            new BotDetectionConfig(
                botSec != null && botSec.getBoolean("enable", false),
                botSec != null ? Math.max(1, botSec.getInt("window-seconds", 300)) : 300,
                botSec != null ? Math.max(0, botSec.getInt("min-view-changes", 3)) : 3,
                botSec != null ? botSec.getString("action", "NO_REWARD") : "NO_REWARD",
                botSec != null ? botSec.getString("exempt-permission",
                    "axs.afkreward.bypass.botcheck") : "axs.afkreward.bypass.botcheck"
            ),
            new IpLimitConfig(
                ipSec != null && ipSec.getBoolean("enable", false),
                ipSec != null ? ipSec.getString("exempt-permission",
                    "axs.afkreward.bypass.iplimit") : "axs.afkreward.bypass.iplimit"
            ),
            new DecayConfig(
                decaySec != null && decaySec.getBoolean("enable", false),
                decaySec != null ? Math.max(0, decaySec.getInt("start-seconds", 3600)) : 3600,
                decaySec != null ? Math.max(0.0, decaySec.getDouble("factor-per-hour", 0.2)) : 0.2,
                decaySec != null ? Math.max(0.0, decaySec.getDouble("min-multiplier", 0.3)) : 0.3
            )
        );

        ConfigurationSection auditSec = yaml.getConfigurationSection("audit");
        AuditConfig audit = new AuditConfig(
            auditSec == null || auditSec.getBoolean("enable", true),
            auditSec != null ? auditSec.getString("file", "audit.log") : "audit.log"
        );

        ConfigurationSection performanceSec = yaml.getConfigurationSection("performance.pause-on-low-tps");
        PerformanceConfig performance = new PerformanceConfig(
            performanceSec != null && performanceSec.getBoolean("enable", false),
            performanceSec != null ? performanceSec.getDouble("min-tps", 15.0) : 15.0
        );

        return new AfkRewardConfiguration(debug, areasDirectory,
            Collections.unmodifiableMap(areas), storage, ui, manual, antiAbuse, audit, performance);
    }
}
