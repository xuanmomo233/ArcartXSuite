package xuanmo.arcartxsuite.pickup.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Pickup 模块配置模型。
 * <p>
 * 支持两种工作模式：通知模式（notification）和扫描模式（scanner），
 * 各自拥有独立的 UI 配置和行为参数。
 *
 * @param debug        是否输出调试日志
 * @param mode         工作模式
 * @param notification 通知模式配置
 * @param scanner      扫描模式配置
 * @param filter       过滤系统配置（仅扫描模式生效）
 */
public record PickupModuleConfiguration(
    boolean debug,
    PickupMode mode,
    NotificationConfig notification,
    ScannerConfig scanner,
    FilterConfig filter
) {

    /** 拾取模块工作模式。 */
    public enum PickupMode {
        /** 通知模式：拾取时弹出 HUD 提示 */
        NOTIFICATION,
        /** 扫描模式：禁用自动拾取，面板展示掉落物，按键交互拾取 */
        SCANNER
    }

    /**
     * 通知模式配置。
     *
     * @param uiId              目标 HUD 的 UI ID
     * @param registerUiOnEnable 启动时是否自动注册 HUD
     * @param overwriteUiFile   是否强制覆盖 UI 文件
     * @param maxVisible        同时最多显示的提示条数
     * @param entryTtlMs        每条提示存活时间（毫秒）
     */
    public record NotificationConfig(
        String uiId,
        boolean registerUiOnEnable,
        boolean overwriteUiFile,
        int maxVisible,
        long entryTtlMs
    ) {
    }

    /**
     * 扫描模式配置。
     *
     * @param uiId                目标 HUD 的 UI ID
     * @param registerUiOnEnable  启动时是否自动注册 HUD
     * @param overwriteUiFile     是否强制覆盖 UI 文件
     * @param scanRadius          扫描附近掉落物的半径（格）
     * @param scanIntervalTicks   扫描间隔（ticks）
     * @param maxDisplay          面板最多显示的物品数
     * @param disableAutoPickup   是否禁用自动拾取
     * @param warehouseAutoDeposit 拾取后是否自动存入仓库
     * @param pickupDelayTicks    掉落物落地后多久开始显示（ticks）
     * @param mergeSameItems       同类物品是否合并显示
     * @param interactUiId         交互 Menu 的 UI ID（按键打开的透明菜单）
     * @param keybindName          拾取按键注册名（ArcartX 客户端按键 ID）
     * @param keybindDefaultKey    拾取按键默认键位（GLFW 键名）
     */
    public record ScannerConfig(
        String uiId,
        boolean registerUiOnEnable,
        boolean overwriteUiFile,
        double scanRadius,
        int scanIntervalTicks,
        int maxDisplay,
        boolean disableAutoPickup,
        boolean warehouseAutoDeposit,
        int pickupDelayTicks,
        boolean mergeSameItems,
        String interactUiId,
        String keybindName,
        String keybindDefaultKey
    ) {
    }

    /**
     * 过滤系统配置（仅扫描模式生效）。
     *
     * @param mode               过滤模式（黑名单/白名单）
     * @param blacklist          材质黑名单
     * @param whitelist          材质白名单
     * @param nameBlacklistRegex 物品名称黑名单正则列表
     * @param loreBlacklistRegex Lore 黑名单正则（任意一行匹配则不显示）
     * @param loreWhitelistRegex Lore 白名单正则（非空时必须至少一行匹配才显示）
     * @param nbtBlacklistKeys   NBT 键黑名单（包含指定键则不显示，支持嵌套路径）
     * @param nbtWhitelistKeys   NBT 键白名单（非空时必须包含至少一个键才显示）
     * @param minAmount          最小堆叠数量
     */
    public record FilterConfig(
        FilterMode mode,
        Set<Material> blacklist,
        Set<Material> whitelist,
        List<Pattern> nameBlacklistRegex,
        List<Pattern> loreBlacklistRegex,
        List<Pattern> loreWhitelistRegex,
        List<String> nbtBlacklistKeys,
        List<String> nbtWhitelistKeys,
        int minAmount
    ) {
        /** 过滤模式。 */
        public enum FilterMode {
            /** 黑名单模式：名单内的物品不显示 */
            BLACKLIST,
            /** 白名单模式：只显示名单内的物品 */
            WHITELIST
        }
    }

    /** 从 YAML 配置文件加载完整配置。 */
    public static PickupModuleConfiguration load(FileConfiguration config) {
        boolean debug = config.getBoolean("settings.debug", false);
        String modeStr = readString(config, "settings.mode", "notification");
        PickupMode mode = "scanner".equalsIgnoreCase(modeStr) ? PickupMode.SCANNER : PickupMode.NOTIFICATION;

        NotificationConfig notification = new NotificationConfig(
            readString(config, "notification.ui-id", "AXS:pickup_hud"),
            config.getBoolean("notification.register-ui-on-enable", true),
            config.getBoolean("notification.overwrite-ui-file", false),
            Math.max(1, config.getInt("notification.max-visible", 4)),
            Math.max(500L, config.getLong("notification.entry-ttl-ms", 3000L))
        );

        ScannerConfig scanner = new ScannerConfig(
            readString(config, "scanner.ui-id", "AXS:loot_panel"),
            config.getBoolean("scanner.register-ui-on-enable", true),
            config.getBoolean("scanner.overwrite-ui-file", false),
            Math.max(1.0, config.getDouble("scanner.scan-radius", 5.0)),
            Math.max(1, config.getInt("scanner.scan-interval-ticks", 5)),
            Math.max(1, config.getInt("scanner.max-display", 8)),
            config.getBoolean("scanner.disable-auto-pickup", true),
            config.getBoolean("scanner.warehouse-auto-deposit", false),
            Math.max(0, config.getInt("scanner.pickup-delay-ticks", 40)),
            config.getBoolean("scanner.merge-same-items", true),
            readString(config, "scanner.interact-ui-id", "AXS:loot_interact"),
            readString(config, "scanner.keybind.name", "AXS_PICKUP_PICK"),
            readString(config, "scanner.keybind.default-key", "F")
        );

        FilterConfig filter = loadFilter(config);

        return new PickupModuleConfiguration(debug, mode, notification, scanner, filter);
    }

    /** 加载过滤系统配置。 */
    private static FilterConfig loadFilter(FileConfiguration config) {
        String filterModeStr = readString(config, "filter.mode", "blacklist");
        FilterConfig.FilterMode filterMode = "whitelist".equalsIgnoreCase(filterModeStr)
            ? FilterConfig.FilterMode.WHITELIST
            : FilterConfig.FilterMode.BLACKLIST;

        Set<Material> blacklist = parseMaterials(config.getStringList("filter.blacklist"));
        Set<Material> whitelist = parseMaterials(config.getStringList("filter.whitelist"));

        List<Pattern> nameBlacklistRegex = parsePatterns(config.getStringList("filter.name-blacklist-regex"));
        List<Pattern> loreBlacklistRegex = parsePatterns(config.getStringList("filter.lore-blacklist-regex"));
        List<Pattern> loreWhitelistRegex = parsePatterns(config.getStringList("filter.lore-whitelist-regex"));
        List<String> nbtBlacklistKeys = parseStringList(config.getStringList("filter.nbt-blacklist-keys"));
        List<String> nbtWhitelistKeys = parseStringList(config.getStringList("filter.nbt-whitelist-keys"));

        int minAmount = Math.max(1, config.getInt("filter.min-amount", 1));
        return new FilterConfig(filterMode, blacklist, whitelist,
            nameBlacklistRegex, loreBlacklistRegex, loreWhitelistRegex,
            nbtBlacklistKeys, nbtWhitelistKeys, minAmount);
    }

    /** 将字符串列表解析为正则 Pattern 列表，无效正则静默跳过。 */
    private static List<Pattern> parsePatterns(List<String> list) {
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : list) {
            if (regex != null && !regex.isBlank()) {
                try {
                    patterns.add(Pattern.compile(regex));
                } catch (Exception ignored) {
                }
            }
        }
        return patterns;
    }

    /** 解析非空字符串列表（去除空白项）。 */
    private static List<String> parseStringList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String item : list) {
            if (item != null && !item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    /** 将字符串列表解析为 Material 集合，无效名称静默跳过。 */
    private static Set<Material> parseMaterials(List<String> list) {
        Set<Material> materials = new HashSet<>();
        for (String name : list) {
            if (name == null || name.isBlank()) continue;
            try {
                materials.add(Material.valueOf(name.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return materials;
    }

    // 向后兼容：通知模式时的快捷访问
    public String uiId() { return notification.uiId(); }
    public boolean registerUiOnEnable() { return notification.registerUiOnEnable(); }
    public boolean overwriteUiFile() { return notification.overwriteUiFile(); }
    public int maxVisible() { return notification.maxVisible(); }
    public long entryTtlMs() { return notification.entryTtlMs(); }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
