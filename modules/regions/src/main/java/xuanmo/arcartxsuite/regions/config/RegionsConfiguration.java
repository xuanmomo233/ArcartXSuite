package xuanmo.arcartxsuite.regions.config;

import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

public record RegionsConfiguration(
    boolean debug,
    SelectionConfig selection,
    DefaultsConfig defaults,
    NotificationConfig notifications,
    StorageConfig storage,
    MessagesConfig messages
) {

    public record SelectionConfig(Material wandItem, long maxVolume, int maxRegionsPerPlayer) {}
    public record DefaultsConfig(int priority, String globalRegionId) {}
    public record NotificationConfig(boolean showActionbar, String displayMode) {}
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
        public String format(String msg) {
            return ChatColor.translateAlternateColorCodes('&', prefix + msg);
        }
    }

    public static RegionsConfiguration load(YamlConfiguration yaml, Logger logger) {
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

        var msg = yaml.getConfigurationSection("messages");
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
