package xuanmo.arcartxsuite.essentials.config;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

public record EssentialsConfiguration(
    boolean debug,
    PlayerConfiguration player,
    TeleportConfiguration teleport,
    WorldConfiguration world,
    ModerationConfiguration moderation,
    InteractionConfiguration interaction,
    StorageConfiguration storage,
    MessagesConfiguration messages
) {

    public static EssentialsConfiguration load(ConfigurationSection root, Logger logger) {
        return new EssentialsConfiguration(
            root.getBoolean("debug", false),
            PlayerConfiguration.load(section(root, "player")),
            TeleportConfiguration.load(section(root, "teleport")),
            WorldConfiguration.load(section(root, "world")),
            ModerationConfiguration.load(section(root, "moderation")),
            InteractionConfiguration.load(section(root, "interaction")),
            StorageConfiguration.load(section(root, "storage"), logger),
            MessagesConfiguration.load(section(root, "messages"))
        );
    }

    // ─── Player ───
    public record PlayerConfiguration(
        int defaultFlySpeed,
        int defaultWalkSpeed,
        int afkTimeout,
        int afkKickTimeout,
        boolean vanishHideQuitMessage,
        boolean vanishHideJoinMessage
    ) {
        static PlayerConfiguration load(ConfigurationSection s) {
            return new PlayerConfiguration(
                clamp(s.getInt("default-fly-speed", 1), 1, 10),
                clamp(s.getInt("default-walk-speed", 1), 1, 10),
                Math.max(0, s.getInt("afk-timeout", 300)),
                Math.max(0, s.getInt("afk-kick-timeout", 0)),
                s.getBoolean("vanish-hide-quit-message", true),
                s.getBoolean("vanish-hide-join-message", true)
            );
        }
    }

    // ─── Teleport ───
    public record TeleportConfiguration(
        int tpaTimeout,
        int teleportDelay,
        boolean cancelOnMove,
        int maxHomes,
        int backStackSize,
        boolean safeTeleport
    ) {
        static TeleportConfiguration load(ConfigurationSection s) {
            return new TeleportConfiguration(
                Math.max(10, s.getInt("tpa-timeout", 60)),
                Math.max(0, s.getInt("teleport-delay", 3)),
                s.getBoolean("cancel-on-move", true),
                Math.max(1, s.getInt("max-homes", 3)),
                Math.max(1, s.getInt("back-stack-size", 5)),
                s.getBoolean("safe-teleport", true)
            );
        }
    }

    // ─── World ───
    public record WorldConfiguration(
        List<String> allowedWorlds
    ) {
        static WorldConfiguration load(ConfigurationSection s) {
            return new WorldConfiguration(
                s.getStringList("allowed-worlds")
            );
        }
    }

    // ─── Moderation ───
    public record ModerationConfiguration(
        String banMessage,
        String kickMessage,
        String muteMessage,
        int maxWarningsBeforeBan,
        int warningExpireDays
    ) {
        static ModerationConfiguration load(ConfigurationSection s) {
            return new ModerationConfiguration(
                color(s.getString("ban-message", "&c你已被服务器封禁。")),
                color(s.getString("kick-message", "&c你已被踢出服务器。")),
                color(s.getString("mute-message", "&c你已被禁言。")),
                Math.max(0, s.getInt("max-warnings-before-ban", 5)),
                Math.max(0, s.getInt("warning-expire-days", 30))
            );
        }
    }

    // ─── Interaction ───
    public record InteractionConfiguration(
        boolean sitOnStairs,
        boolean sitOnSlabs
    ) {
        static InteractionConfiguration load(ConfigurationSection s) {
            return new InteractionConfiguration(
                s.getBoolean("sit-on-stairs", true),
                s.getBoolean("sit-on-slabs", false)
            );
        }
    }

    // ─── Storage ───
    public record StorageConfiguration(
        Dialect dialect,
        String sqliteFile,
        String host,
        int port,
        String database,
        String username,
        String password,
        String tablePrefix
    ) {
        public enum Dialect {
            SQLITE("sqlite"), MYSQL("mysql");
            private final String key;
            Dialect(String key) { this.key = key; }
            public String configKey() { return key; }
            public static Dialect parse(String raw, Logger logger) {
                if (raw != null && raw.trim().toLowerCase(Locale.ROOT).equals("mysql")) return MYSQL;
                return SQLITE;
            }
        }

        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (dialect == Dialect.MYSQL) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, 5, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFile);
        }

        static StorageConfiguration load(ConfigurationSection s, Logger logger) {
            ConfigurationSection mysql = s.getConfigurationSection("mysql");
            return new StorageConfiguration(
                Dialect.parse(s.getString("dialect", "sqlite"), logger),
                s.getString("sqlite-file", "essentials.db"),
                mysql != null ? mysql.getString("host", "localhost") : "localhost",
                mysql != null ? mysql.getInt("port", 3306) : 3306,
                mysql != null ? mysql.getString("database", "arcartxsuite") : "arcartxsuite",
                mysql != null ? mysql.getString("username", "root") : "root",
                mysql != null ? mysql.getString("password", "") : "",
                mysql != null ? mysql.getString("table-prefix", "axs_ess_") : "axs_ess_"
            );
        }
    }

    // ─── Messages ───
    public record MessagesConfiguration(
        String flyEnabled, String flyDisabled,
        String godEnabled, String godDisabled,
        String heal, String feed,
        String speedSet,
        String afkEnter, String afkLeave,
        String vanishEnabled, String vanishDisabled,
        String homeSet, String homeDeleted, String homeNotFound, String homeLimit, String homeTeleported,
        String warpSet, String warpDeleted, String warpNotFound, String warpTeleported,
        String spawnSet, String spawnTeleported,
        String tpaSent, String tpaReceived, String tpaHereReceived,
        String tpaAccepted, String tpaDenied, String tpaExpired, String tpaNoPending,
        String teleportCancelled, String teleportWarmup,
        String backTeleported, String backNone,
        String repairSuccess, String repairNothing,
        String hatSuccess, String hatNothing,
        String nickSet, String nickReset,
        String sitDown, String layDown,
        String timeSet, String weatherSet,
        String playerNotFound, String noPermission,
        String banBroadcast, String muteBroadcast, String kickBroadcast, String warnBroadcast,
        String unbanSuccess, String unmuteSuccess,
        String sortDone, String treeFelled,
        String replantEnabled, String replantDisabled,
        String autotoolEnabled, String autotoolDisabled
    ) {
        public String sortDone() { return sortDone; }
        public String treeFelled() { return treeFelled; }
        public String replantEnabled() { return replantEnabled; }
        public String replantDisabled() { return replantDisabled; }
        public String autotoolEnabled() { return autotoolEnabled; }
        public String autotoolDisabled() { return autotoolDisabled; }
        static MessagesConfiguration load(ConfigurationSection s) {
            return new MessagesConfiguration(
                msg(s, "fly-enabled", "&a飞行模式已开启。"),
                msg(s, "fly-disabled", "&c飞行模式已关闭。"),
                msg(s, "god-enabled", "&a无敌模式已开启。"),
                msg(s, "god-disabled", "&c无敌模式已关闭。"),
                msg(s, "heal", "&a你的生命值已恢复。"),
                msg(s, "feed", "&a你的饥饿值已恢复。"),
                msg(s, "speed-set", "&a速度已设置为 {speed}。"),
                msg(s, "afk-enter", "&7{player} 已进入挂机状态。"),
                msg(s, "afk-leave", "&7{player} 已退出挂机状态。"),
                msg(s, "vanish-enabled", "&a你已隐身。"),
                msg(s, "vanish-disabled", "&c你已取消隐身。"),
                msg(s, "home-set", "&a家 &f{name} &a已设置。"),
                msg(s, "home-deleted", "&c家 &f{name} &c已删除。"),
                msg(s, "home-not-found", "&c家 &f{name} &c不存在。"),
                msg(s, "home-limit", "&c你的家数量已达上限 ({max})。"),
                msg(s, "home-teleported", "&a已传送到家 &f{name}&a。"),
                msg(s, "warp-set", "&a传送点 &f{name} &a已设置。"),
                msg(s, "warp-deleted", "&c传送点 &f{name} &c已删除。"),
                msg(s, "warp-not-found", "&c传送点 &f{name} &c不存在。"),
                msg(s, "warp-teleported", "&a已传送到 &f{name}&a。"),
                msg(s, "spawn-set", "&a出生点已设置。"),
                msg(s, "spawn-teleported", "&a已传送到出生点。"),
                msg(s, "tpa-sent", "&a传送请求已发送给 &f{player}&a。"),
                msg(s, "tpa-received", "&f{player} &a请求传送到你的位置。"),
                msg(s, "tpa-here-received", "&f{player} &a请求你传送到 TA 的位置。"),
                msg(s, "tpa-accepted", "&a传送请求已接受。"),
                msg(s, "tpa-denied", "&c传送请求已拒绝。"),
                msg(s, "tpa-expired", "&c传送请求已过期。"),
                msg(s, "tpa-no-pending", "&c没有待处理的传送请求。"),
                msg(s, "teleport-cancelled", "&c传送已取消（你移动了）。"),
                msg(s, "teleport-warmup", "&7传送中... &f{seconds} &7秒后到达，请勿移动。"),
                msg(s, "back-teleported", "&a已传送回上次位置。"),
                msg(s, "back-none", "&c没有可返回的位置。"),
                msg(s, "repair-success", "&a物品已修复。"),
                msg(s, "repair-nothing", "&c手中没有可修复的物品。"),
                msg(s, "hat-success", "&a物品已戴在头上。"),
                msg(s, "hat-nothing", "&c手中没有物品。"),
                msg(s, "nick-set", "&a昵称已设置为 &f{nick}&a。"),
                msg(s, "nick-reset", "&a昵称已重置。"),
                msg(s, "sit-down", "&7你坐下了。"),
                msg(s, "lay-down", "&7你躺下了。"),
                msg(s, "time-set", "&a世界 &f{world} &a的时间已设置为 &f{time}&a。"),
                msg(s, "weather-set", "&a世界 &f{world} &a的天气已设置为 &f{weather}&a。"),
                msg(s, "player-not-found", "&c玩家 &f{player} &c不在线。"),
                msg(s, "no-permission", "&c你没有权限执行此操作。"),
                msg(s, "ban-broadcast", "&c{player} 已被 {operator} 封禁。原因: {reason}"),
                msg(s, "mute-broadcast", "&c{player} 已被 {operator} 禁言。原因: {reason}"),
                msg(s, "kick-broadcast", "&c{player} 已被 {operator} 踢出。原因: {reason}"),
                msg(s, "warn-broadcast", "&e{player} 收到来自 {operator} 的警告。原因: {reason}"),
                msg(s, "unban-success", "&a已解封 &f{player}&a。"),
                msg(s, "unmute-success", "&a已解除 &f{player} &a的禁言。"),
                msg(s, "sort-done", "&a背包已整理。"),
                msg(s, "tree-felled", "&a一键砍树! 共砍落 {count} 个原木方块。"),
                msg(s, "replant-enabled", "&a自动补种已开启。"),
                msg(s, "replant-disabled", "&c自动补种已关闭。"),
                msg(s, "autotool-enabled", "&a自动切换工具已开启。"),
                msg(s, "autotool-disabled", "&c自动切换工具已关闭。")
            );
        }
    }

    // ─── Helpers ───
    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        if (s == null) s = parent.createSection(key);
        return s;
    }

    private static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String msg(ConfigurationSection s, String key, String def) {
        return color(s.getString(key, def));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
