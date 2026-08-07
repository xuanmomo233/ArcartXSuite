package xuanmo.arcartxsuite.regions.config;

import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Regions 模块的聚合配置记录。
 * <p>
 * 由 {@link #load(YamlConfiguration, YamlConfiguration, Logger)} 从 {@code ArcartXRegions.yml}
 * 与附属 {@code messages.yml} 解析得到，贯穿选区、默认值、通知、存储、消息等全部子配置。
 * 关联模块：{@code regions}。
 *
 * @param debug         是否开启调试日志
 * @param selection     选区工具与体积/数量限制配置
 * @param defaults      区域默认值（优先级、全局区域 ID）
 * @param notifications 通知展示配置（Actionbar、显示模式）
 * @param storage       存储后端配置（SQLite / MySQL）
 * @param messages      玩家可见消息模板
 */
public record RegionsConfiguration(
    boolean debug,
    SelectionConfig selection,
    DefaultsConfig defaults,
    NotificationConfig notifications,
    StorageConfig storage,
    MessagesConfig messages
) {

    /**
     * 选区工具与限制配置。
     *
     * @param wandItem            选区工具物品类型
     * @param maxVolume           单次选区最大方块体积
     * @param maxRegionsPerPlayer 单玩家最大区域数量
     */
    public record SelectionConfig(Material wandItem, long maxVolume, int maxRegionsPerPlayer) {}

    /**
     * 区域默认值配置。
     *
     * @param priority       新建区域默认优先级
     * @param globalRegionId 全局区域 ID
     */
    public record DefaultsConfig(int priority, String globalRegionId) {}

    /**
     * 通知展示配置。
     *
     * @param showActionbar 是否在 Actionbar 展示通知
     * @param displayMode   显示模式（如 {@code actionbar}）
     */
    public record NotificationConfig(boolean showActionbar, String displayMode) {}

    /**
     * 存储后端配置，支持 SQLite 与 MySQL。
     *
     * @param dialect     数据库方言
     * @param sqliteFile  SQLite 数据库文件名
     * @param host        MySQL 主机地址
     * @param port        MySQL 端口
     * @param database    MySQL 数据库名
     * @param username    MySQL 用户名
     * @param password    MySQL 密码
     * @param tablePrefix 表名前缀
     * @param poolSize    连接池大小
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
             * 由字符串解析方言，不区分大小写，非 {@code mysql} 一律视为 {@link #SQLITE}。
             *
             * @param s 方言字符串
             * @return 对应的 {@link Dialect}
             */
            public static Dialect from(String s) {
                return "mysql".equalsIgnoreCase(s) ? MYSQL : SQLITE;
            }
        }

        /**
         * 将本配置转换为平台统一的 {@link xuanmo.arcartxsuite.api.storage.StorageDescriptor}。
         *
         * @return 对应的存储描述符
         */
        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (dialect == Dialect.MYSQL) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFile);
        }
    }

    /**
     * 玩家可见消息模板集合。
     *
     * @param prefix             消息前缀
     * @param wandPos1           选区点 1 提示
     * @param wandPos2           选区点 2 提示
     * @param regionCreated      区域创建提示
     * @param regionDeleted      区域删除提示
     * @param regionNotFound     未找到区域提示
     * @param regionExists       区域已存在提示
     * @param noSelection        未选区提示
     * @param selectionTooLarge  选区过大提示
     * @param maxRegionsReached  达到最大区域数量提示
     * @param flagSet            标志设置提示
     * @param flagRemoved        标志移除提示
     * @param flagUnknown        未知标志提示
     * @param memberAdded        成员添加提示
     * @param memberRemoved      成员移除提示
     * @param noPermissionRegion 区域无权限提示
     * @param noBuild            禁止建造提示
     * @param noInteract         禁止交互提示
     * @param noPvp              禁止 PVP 提示
     * @param regionEnter        进入区域提示
     * @param regionLeave        离开区域提示
     */
    public record MessagesConfig(
        String prefix,
        String wandPos1, String wandPos2,
        String regionCreated, String regionDeleted,
        String regionNotFound, String regionExists,
        String noSelection, String selectionTooLarge,
        String maxRegionsReached,
        String flagSet, String flagRemoved, String flagUnknown,
        String memberAdded, String memberRemoved,
        String noPermissionRegion, String noBuild, String noInteract, String noPvp,
        String regionEnter, String regionLeave
    ) {
        /**
         * 将消息模板拼接前缀并翻译 {@code &} 颜色代码。
         *
         * @param msg 原始消息模板
         * @return 带颜色代码的最终消息
         */
        public String format(String msg) {
            return ChatColor.translateAlternateColorCodes('&', prefix + msg);
        }
    }

    /**
     * 从主配置与消息配置 YAML 加载并构造 {@link RegionsConfiguration}。
     *
     * @param yaml         主配置 {@code ArcartXRegions.yml}
     * @param messagesYaml 消息配置 {@code messages.yml}
     * @param logger       日志记录器，用于记录加载过程中的警告
     * @return 解析后的配置记录
     */
    public static RegionsConfiguration load(YamlConfiguration yaml, YamlConfiguration messagesYaml, Logger logger) {
        var sel = yaml.getConfigurationSection("selection");
        Material wand = Material.matchMaterial(sel != null ? sel.getString("wand-item", "WOODEN_AXE") : "WOODEN_AXE");
        if (wand == null) wand = Material.WOODEN_AXE;

        SelectionConfig selection = new SelectionConfig(
            wand,
            sel != null ? sel.getLong("max-volume", 500000) : 500000,
            sel != null ? sel.getInt("max-regions-per-player", 20) : 20
        );

        var def = yaml.getConfigurationSection("defaults");
        DefaultsConfig defaults = new DefaultsConfig(
            def != null ? def.getInt("priority", 0) : 0,
            def != null ? def.getString("global-region-id", "__global__") : "__global__"
        );

        var notif = yaml.getConfigurationSection("notifications");
        NotificationConfig notifications = new NotificationConfig(
            notif != null && notif.getBoolean("show-actionbar", true),
            notif != null ? notif.getString("display-mode", "actionbar") : "actionbar"
        );

        var stor = yaml.getConfigurationSection("storage");
        StorageConfig storage = new StorageConfig(
            StorageConfig.Dialect.from(stor != null ? stor.getString("dialect", "sqlite") : "sqlite"),
            stor != null ? stor.getString("sqlite-file", "regions.db") : "regions.db",
            stor != null ? stor.getString("host", "127.0.0.1") : "127.0.0.1",
            stor != null ? stor.getInt("port", 3306) : 3306,
            stor != null ? stor.getString("database", "arcartxsuite") : "arcartxsuite",
            stor != null ? stor.getString("username", "root") : "root",
            stor != null ? stor.getString("password", "") : "",
            stor != null ? stor.getString("table-prefix", "axs_rg_") : "axs_rg_",
            stor != null ? stor.getInt("pool-size", 3) : 3
        );

        var msg = messagesYaml;
        MessagesConfig messages = new MessagesConfig(
            s(msg, "prefix", "&8[&6Regions&8] &r"),
            s(msg, "wand-pos1", "&a已设置选区点 1: &f{x}, {y}, {z}"),
            s(msg, "wand-pos2", "&a已设置选区点 2: &f{x}, {y}, {z}"),
            s(msg, "region-created", "&a区域 &f{name} &a已创建。"),
            s(msg, "region-deleted", "&c区域 &f{name} &c已删除。"),
            s(msg, "region-not-found", "&c未找到区域: &f{name}"),
            s(msg, "region-exists", "&c区域 &f{name} &c已存在。"),
            s(msg, "no-selection", "&c请先用选区工具选定两个点。"),
            s(msg, "selection-too-large", "&c选区体积超过限制 ({max} 方块)。"),
            s(msg, "max-regions-reached", "&c你已达到最大区域数量限制 ({max})。"),
            s(msg, "flag-set", "&a区域 &f{region} &a标志 &f{flag} &a已设置为 &f{value}&a。"),
            s(msg, "flag-removed", "&a区域 &f{region} &a标志 &f{flag} &a已移除。"),
            s(msg, "flag-unknown", "&c未知标志: &f{flag}"),
            s(msg, "member-added", "&a已将 &f{player} &a添加为区域 &f{region} &a的{role}。"),
            s(msg, "member-removed", "&a已将 &f{player} &a从区域 &f{region} &a移除。"),
            s(msg, "no-permission-region", "&c你没有在此区域执行该操作的权限。"),
            s(msg, "no-build", "&c你不能在此区域放置或破坏方块。"),
            s(msg, "no-interact", "&c你不能在此区域进行交互。"),
            s(msg, "no-pvp", "&c此区域禁止 PVP。"),
            s(msg, "region-enter", "&a[进入] &f{region}"),
            s(msg, "region-leave", "&a[离开] &f{region}")
        );

        return new RegionsConfiguration(yaml.getBoolean("debug", false), selection, defaults, notifications, storage, messages);
    }

    private static String s(org.bukkit.configuration.ConfigurationSection section, String key, String def) {
        return section != null ? section.getString(key, def) : def;
    }
}
