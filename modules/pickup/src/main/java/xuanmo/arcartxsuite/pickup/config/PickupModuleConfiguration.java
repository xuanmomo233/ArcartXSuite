package xuanmo.arcartxsuite.pickup.config;

import java.util.logging.Logger;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherLoader;

/**
 * Pickup 模块配置模型。
 * <p>
 * 支持两种工作模式：通知模式（notification）和扫描模式（scanner），
 * 各自拥有独立的 UI 配置和行为参数。
 *
 * @param debug        是否输出调试日志
 * @param mode         工作模式
 * @param ui           统一 UI 配置（所有 UI ID 与注册行为集中管理）
 * @param notification 通知模式配置
 * @param scanner      扫描模式配置
 * @param filter       过滤系统配置（仅扫描模式生效）
 */
public record PickupModuleConfiguration(
    boolean debug,
    PickupMode mode,
    UiConfig ui,
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
     * 统一 UI 配置（从原 notification / scanner 嵌套节抽离）。
     *
     * @param notificationUiId 通知模式目标 HUD 的 UI ID
     * @param scannerUiId      扫描模式目标 HUD 的 UI ID
     * @param interactUiId     扫描模式交互 Menu 的 UI ID（按键打开的透明菜单）
     * @param registerUiOnEnable 启动时是否自动注册 UI
     * @param overwriteUiFiles 是否强制覆盖 UI 文件
     */
    public record UiConfig(
        String notificationUiId,
        String scannerUiId,
        String interactUiId,
        boolean registerUiOnEnable,
        boolean overwriteUiFiles
    ) {
    }

    /**
     * 通知模式配置。
     *
     * @param maxVisible        同时最多显示的提示条数
     * @param entryTtlMs        每条提示存活时间（毫秒）
     */
    public record NotificationConfig(
        int maxVisible,
        long entryTtlMs
    ) {
    }

    /**
     * 扫描模式配置。
     *
     * @param scanRadius          扫描附近掉落物的半径（格）
     * @param scanIntervalTicks   扫描间隔（ticks）
     * @param displayRows         HUD 每页显示行数，不限制清单总长度
     * @param disableAutoPickup   是否禁用自动拾取
     * @param warehouseAutoDeposit 拾取后是否自动存入仓库
     * @param pickupDelayTicks    掉落物落地后多久开始显示（ticks）
     * @param mergeSameItems       同类物品是否合并显示
     * @param keybindName          拾取按键注册名（ArcartX 客户端按键 ID，须与宿主 config.yml keybinds 节中的 name 一致）
     */
    public record ScannerConfig(
        double scanRadius,
        int scanIntervalTicks,
        int displayRows,
        boolean disableAutoPickup,
        boolean warehouseAutoDeposit,
        int pickupDelayTicks,
        boolean mergeSameItems,
        String keybindName,
        String previousKey,
        String nextKey,
        String collectAllKey,
        String detailsKey,
        int maxCandidates,
        boolean batchEnabled,
        boolean batchMatchingOnly,
        ItemMatcher batchMatcher,
        int entitiesPerTick,
        int timeBudgetMs,
        boolean chatSummary
    ) {
    }

    /**
     * 过滤系统配置（仅扫描模式生效）。
     * <p>
     * 各维度通过独立的 {@link ItemMatcher} 实现，由 {@link xuanmo.arcartxsuite.pickup.filter.LootFilterEngine}
     * 调用 {@link xuanmo.arcartxsuite.api.item.ItemMatcherAPI#matches} 判断。
     *
     * @param mode                过滤模式（黑名单/白名单），控制材质过滤使用 blacklist 还是 whitelist
     * @param materialBlacklist   材质黑名单 matcher（mode=BLACKLIST 时，matches 为 true 则不显示）
     * @param materialWhitelist   材质白名单 matcher（mode=WHITELIST 时，matches 为 false 则不显示）
     * @param nameBlacklist       名称黑名单 matcher（matches 为 true 则不显示）
     * @param loreBlacklist       Lore 黑名单 matcher（matches 为 true 则不显示）
     * @param loreWhitelist       Lore 白名单 matcher（非空时 matches 为 false 则不显示）
     * @param nbtBlacklist        NBT 键黑名单 matcher（matches 为 true 则不显示）
     * @param nbtWhitelist        NBT 键白名单 matcher（非空时 matches 为 false 则不显示）
     * @param minAmount           最小堆叠数量
     */
    public record FilterConfig(
        FilterMode mode,
        ItemMatcher materialBlacklist,
        ItemMatcher materialWhitelist,
        ItemMatcher nameBlacklist,
        ItemMatcher loreBlacklist,
        ItemMatcher loreWhitelist,
        ItemMatcher nbtBlacklist,
        ItemMatcher nbtWhitelist,
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
    public static PickupModuleConfiguration load(FileConfiguration config, Logger logger) {
        boolean debug = config.getBoolean("settings.debug", false);
        String modeStr = readString(config, "settings.mode", "notification");
        PickupMode mode = "scanner".equalsIgnoreCase(modeStr) ? PickupMode.SCANNER : PickupMode.NOTIFICATION;

        UiConfig ui = new UiConfig(
            readString(config, "ui.notification-ui-id", "AXS:pickup_hud"),
            readString(config, "ui.scanner-ui-id", "AXS:loot_panel"),
            readString(config, "ui.interact-ui-id", "AXS:loot_interact"),
            config.getBoolean("ui.register-ui-on-enable", true),
            config.getBoolean("ui.overwrite-ui-files", false)
        );

        NotificationConfig notification = new NotificationConfig(
            Math.min(16, Math.max(1, config.getInt("notification.max-visible", 4))),
            Math.min(30000L, Math.max(500L, config.getLong("notification.entry-ttl-ms", 3000L)))
        );

        ScannerConfig scanner = new ScannerConfig(
            boundedRadius(config.getDouble("scanner.scan-radius", 5.0)),
            Math.min(100, Math.max(1, config.getInt("scanner.scan-interval-ticks", 5))),
            Math.min(16, Math.max(1, config.getInt("scanner.display-rows", 5))),
            config.getBoolean("scanner.disable-auto-pickup", true),
            config.getBoolean("scanner.warehouse-auto-deposit", false),
            Math.max(0, config.getInt("scanner.pickup-delay-ticks", 40)),
            config.getBoolean("scanner.merge-same-items", true),
            readString(config, "scanner.keybind.name", "AXS_PICKUP_PICK"),
            readString(config, "scanner.keybind.previous", "AXS_PICKUP_PREVIOUS"),
            readString(config, "scanner.keybind.next", "AXS_PICKUP_NEXT"),
            readString(config, "scanner.keybind.collect-all", "AXS_PICKUP_ALL"),
            readString(config, "scanner.keybind.details", "AXS_PICKUP_DETAILS"),
            Math.min(4096, Math.max(32, config.getInt("scanner.max-candidates", 512))),
            config.getBoolean("scanner.batch.enabled", true),
            batchMatchingOnly(config),
            ItemMatcherLoader.load(config.getConfigurationSection("scanner.batch.matcher"), logger, "scanner.batch.matcher"),
            Math.min(64, Math.max(1, config.getInt("scanner.batch.entities-per-tick", 8))),
            Math.min(10, Math.max(1, config.getInt("scanner.batch.time-budget-ms", 4))),
            config.getBoolean("scanner.batch.chat-summary", false)
        );

        FilterConfig filter = loadFilter(config, logger);

        if (mode == PickupMode.SCANNER && List.of(scanner.keybindName(), scanner.previousKey(),
            scanner.nextKey(), scanner.collectAllKey(), scanner.detailsKey()).stream()
            .map(key -> key.toUpperCase(java.util.Locale.ROOT)).distinct().count() != 5) {
            throw new IllegalArgumentException("scanner.keybind 的五个按键 ID 必须互不相同");
        }

        return new PickupModuleConfiguration(debug, mode, ui, notification, scanner, filter);
    }

    /** 加载过滤系统配置。 */
    private static FilterConfig loadFilter(FileConfiguration config, Logger logger) {
        ConfigurationSection filterSec = config.getConfigurationSection("filter");
        String filterModeStr = readString(config, "filter.mode", "blacklist");
        FilterConfig.FilterMode filterMode = "whitelist".equalsIgnoreCase(filterModeStr)
            ? FilterConfig.FilterMode.WHITELIST
            : FilterConfig.FilterMode.BLACKLIST;

        ItemMatcher materialBlacklist = ItemMatcherLoader.load(
            filterSec == null ? null : filterSec.getConfigurationSection("material-blacklist"),
            logger, "filter.material-blacklist");
        ItemMatcher materialWhitelist = ItemMatcherLoader.load(
            filterSec == null ? null : filterSec.getConfigurationSection("material-whitelist"),
            logger, "filter.material-whitelist");
        ItemMatcher nameBlacklist = ItemMatcherLoader.load(
            filterSec == null ? null : filterSec.getConfigurationSection("name-blacklist"),
            logger, "filter.name-blacklist");
        ItemMatcher loreBlacklist = ItemMatcherLoader.load(
            filterSec == null ? null : filterSec.getConfigurationSection("lore-blacklist"),
            logger, "filter.lore-blacklist");
        ItemMatcher loreWhitelist = ItemMatcherLoader.load(
            filterSec == null ? null : filterSec.getConfigurationSection("lore-whitelist"),
            logger, "filter.lore-whitelist");
        ItemMatcher nbtBlacklist = ItemMatcherLoader.load(
            filterSec == null ? null : filterSec.getConfigurationSection("nbt-blacklist"),
            logger, "filter.nbt-blacklist");
        ItemMatcher nbtWhitelist = ItemMatcherLoader.load(
            filterSec == null ? null : filterSec.getConfigurationSection("nbt-whitelist"),
            logger, "filter.nbt-whitelist");

        int minAmount = Math.max(1, config.getInt("filter.min-amount", 1));
        return new FilterConfig(filterMode,
            materialBlacklist, materialWhitelist,
            nameBlacklist, loreBlacklist, loreWhitelist,
            nbtBlacklist, nbtWhitelist, minAmount);
    }

    // 向后兼容：通知模式时的快捷访问（UI 配置已统一到 ui 节）
    public String uiId() { return ui.notificationUiId(); }
    public boolean registerUiOnEnable() { return ui.registerUiOnEnable(); }
    public boolean overwriteUiFile() { return ui.overwriteUiFiles(); }
    public int maxVisible() { return notification.maxVisible(); }
    public long entryTtlMs() { return notification.entryTtlMs(); }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static double boundedRadius(double value) {
        return Double.isFinite(value) ? Math.min(32, Math.max(1, value)) : 5;
    }

    private static boolean batchMatchingOnly(FileConfiguration config) {
        return switch (readString(config, "scanner.batch.mode", "all").toLowerCase(java.util.Locale.ROOT)) {
            case "all" -> false;
            case "matching" -> true;
            default -> throw new IllegalArgumentException("scanner.batch.mode 只允许 all 或 matching");
        };
    }
}
