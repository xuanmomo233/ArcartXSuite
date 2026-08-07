package xuanmo.arcartxsuite.afkreward.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;

/**
 * 挂机奖励模块的顶层配置记录。
 * <p>
 * 关联模块：afkreward。由 {@code ArcartXAfkReward.yml} 解析而来，聚合存储、UI、手动挂机、
 * 防滥用、审计、性能等子配置，以及区域目录与已加载的区域映射。
 *
 * @param debug          是否开启调试日志
 * @param areasDirectory 区域配置文件所在目录（相对模块 data 目录）
 * @param areas          已加载的区域映射（key 为区域 id，value 为区域定义）
 * @param storage        存储子配置
 * @param ui             UI（HUD）子配置
 * @param manual         手动挂机子配置
 * @param antiAbuse      防滥用子配置
 * @param audit          审计日志子配置
 * @param performance    性能（低 TPS 暂停）子配置
 */
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

    /**
     * 防滥用聚合配置。
     *
     * @param timeLimit     时长限制子配置
     * @param botDetection  挂机脚本检测子配置
     * @param ipLimit       同 IP 限制子配置
     * @param decay         收益衰减子配置
     */
    public record AntiAbuseConfig(
        TimeLimitConfig timeLimit,
        BotDetectionConfig botDetection,
        IpLimitConfig ipLimit,
        DecayConfig decay
    ) {}

    /**
     * 单次/每日挂机时长限制配置。
     *
     * @param enable           是否启用时长限制
     * @param sessionSeconds   单次挂机最长秒数，{@code 0} 表示不限
     * @param dailySeconds     每日累计最长秒数，{@code 0} 表示不限
     * @param onExceed         超出限制时的处理动作（STOP / KICK / DECAY）
     * @param exemptPermission 豁免此限制的权限节点
     */
    public record TimeLimitConfig(
        boolean enable, int sessionSeconds, int dailySeconds,
        String onExceed, String exemptPermission
    ) {}

    /**
     * 挂机脚本（视角无变化）检测配置。
     *
     * @param enable           是否启用检测
     * @param windowSeconds    检测窗口秒数
     * @param minViewChanges   窗口内最少视角变化次数，低于此值判定为脚本
     * @param action           判定后的处理动作（NO_REWARD / KICK）
     * @param exemptPermission 豁免此检测的权限节点
     */
    public record BotDetectionConfig(
        boolean enable, int windowSeconds, int minViewChanges,
        String action, String exemptPermission
    ) {}

    /**
     * 同 IP 在线限制配置。
     *
     * @param enable           是否启用同 IP 限制
     * @param exemptPermission 豁免此限制的权限节点
     */
    public record IpLimitConfig(boolean enable, String exemptPermission) {}

    /**
     * 收益衰减配置，挂机越久奖励倍率越低。
     *
     * @param enable         是否启用衰减
     * @param startSeconds   开始衰减的累计秒数
     * @param factorPerHour  每小时衰减系数
     * @param minMultiplier  最低倍率下限
     */
    public record DecayConfig(
        boolean enable, int startSeconds, double factorPerHour, double minMultiplier
    ) {}

    /**
     * 审计日志配置。
     *
     * @param enable 是否启用审计日志
     * @param file   审计日志文件名（相对模块 data 目录）
     */
    public record AuditConfig(boolean enable, String file) {}

    /**
     * 性能配置，低 TPS 时暂停挂机结算。
     *
     * @param pauseOnLowTps 是否在低 TPS 时暂停结算
     * @param minTps        暂停阈值，低于此 TPS 时跳过本轮结算
     */
    public record PerformanceConfig(boolean pauseOnLowTps, double minTps) {}

    /**
     * 存储子配置，支持 SQLite 与 MySQL。
     *
     * @param dialect      存储方言（SQLITE / MYSQL）
     * @param sqliteFile   SQLite 数据库文件名
     * @param host         MySQL 主机地址
     * @param port         MySQL 端口
     * @param database     MySQL 数据库名
     * @param username     MySQL 用户名
     * @param password     MySQL 密码
     * @param tablePrefix  表名前缀
     * @param poolSize     连接池大小
     */
    public record StorageConfig(
        Dialect dialect, String sqliteFile,
        String host, int port, String database,
        String username, String password,
        String tablePrefix, int poolSize
    ) {
        /**
         * 存储方言枚举。
         */
        public enum Dialect {
            SQLITE, MYSQL;
            /**
             * 将字符串解析为方言，{@code mysql}（不区分大小写）返回 {@link #MYSQL}，其余返回 {@link #SQLITE}。
             *
             * @param s 方言字符串
             * @return 对应枚举值
             */
            public static Dialect from(String s) {
                return "mysql".equalsIgnoreCase(s) ? MYSQL : SQLITE;
            }
        }

        /**
         * 将本配置转换为平台统一的存储描述符。
         *
         * @return 对应方言的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}
         */
        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (dialect == Dialect.MYSQL) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFile);
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
            return dialect == Dialect.MYSQL;
        }
    }

    /**
     * UI（HUD）子配置。
     *
     * @param hudId           HUD 标识
     * @param registerOnEnable 启用时是否注册 HUD
     * @param overwriteUiFile  启用时是否覆盖已存在的 UI 文件
     */
    public record UiConfig(
        String hudId,
        boolean registerOnEnable,
        boolean overwriteUiFile
    ) {}

    /**
     * 手动挂机子配置。
     *
     * @param restrictActions        是否限制手动挂机期间的玩家行为
     * @param returnOnEnd            结束手动挂机时是否传送回原位置
     * @param broadcastRewards       是否广播奖励
     * @param combatCooldownSeconds  战斗冷却秒数，冷却期内不可开始挂机
     * @param leaderboardSize        排行榜最大条目数
     * @param signalOnReward         发放奖励时触发的信号标识
     * @param signalOnEnd            结束挂机时触发的信号标识
     * @param subtitleOnReward       发放奖励时播放的字幕标识
     * @param subtitleOnEnd          结束挂机时播放的字幕标识
     * @param endMailPresets         结束挂机时发送的预设邮件标识列表
     * @param protections            行为限制开关集合
     * @param permissionRecheckSeconds 权限复检间隔秒数
     */
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
