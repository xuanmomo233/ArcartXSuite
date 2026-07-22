package xuanmo.arcartxsuite.warehouse.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherLoader;

/**
 * Warehouse 模块配置聚合，对应 {@code ArcartXWarehouse.yml}。
 * <p>
 * 所有字段均为不可变，通过 {@link #load(FileConfiguration, Logger, CurrencyBridgeAPI)} 从 Bukkit YAML 解析。
 * 动态节（warehouses / categories / bank.currencies / deposit-products / shared / sort-profiles）
 * 在 {@link xuanmo.arcartxsuite.warehouse.WarehouseModule#defaultSyncPolicy()} 中声明，不会被 ConfigDiagnosticEngine 覆盖。
 */
public record WarehouseModuleConfiguration(
    boolean debug,
    long flushIntervalTicks,
    long transferConfirmExpireHours,
    UiConfiguration ui,
    StorageConfiguration storage,
    SecurityConfiguration security,
    PickupConfiguration pickup,
    SearchConfiguration search,
    Map<String, WarehouseDefinition> warehouses,
    Map<String, CategoryDefinition> categories,
    List<String> bankCurrencies,
    Map<String, DepositProductDefinition> depositProducts,
    SharedConfiguration shared,
    Map<String, SortProfile> sortProfiles,
    ItemMatcher blacklist,
    ShowcaseConfiguration showcase
) {

    /**
     * 从 Bukkit {@link FileConfiguration} 解析完整配置。
     * 缺失值均使用安全默认值，非法值会被截断到合法范围。
     *
     * @param configuration Bukkit 配置对象
     * @param logger        用于输出解析警告
     * @return 不可变的配置实例
     */
    public static WarehouseModuleConfiguration load(FileConfiguration configuration, Logger logger) {
        return load(configuration, logger, null);
    }

    public static WarehouseModuleConfiguration load(FileConfiguration configuration, Logger logger, CurrencyBridgeAPI currencyBridge) {
        boolean debug = configuration.getBoolean("settings.debug", false);
        long flushIntervalTicks = Math.max(20L, configuration.getLong("settings.flush-interval-ticks", 100L));
        long transferConfirmExpireHours = configuration.getLong("settings.transfer-confirm-expire-hours", 24L);

        UiConfiguration ui = new UiConfiguration(
            readString(configuration, "ui.id", "AXS:warehouse_storage"),
            readString(configuration, "ui.manage-id", "AXS:warehouse_manage"),
            readString(configuration, "ui.bank-id", "AXS:warehouse_bank"),
            readString(configuration, "ui.packet-id", "AXS_WAREHOUSE"),
            configuration.getBoolean("ui.register-ui-on-enable", true),
            configuration.getBoolean("ui.overwrite-ui-files", false),
            Math.max(1, configuration.getInt("ui.page-size", configuration.getInt("search.page-size", 18)))
        );

        ConfigurationSection storageSection = configuration.getConfigurationSection("storage");
        StorageConfiguration storage = new StorageConfiguration(
            StorageDialect.parse(storageSection == null ? null : storageSection.getString("mode", "sqlite")),
            storageSection == null ? "warehouse.db" : readString(storageSection, "sqlite.file", "warehouse.db"),
            storageSection == null ? "127.0.0.1" : readString(storageSection, "mysql.host", "127.0.0.1"),
            storageSection == null ? 3306 : Math.max(1, storageSection.getInt("mysql.port", 3306)),
            storageSection == null ? "arcartxsuite" : readString(storageSection, "mysql.database", "arcartxsuite"),
            storageSection == null ? "root" : readString(storageSection, "mysql.username", "root"),
            storageSection == null ? "" : readString(storageSection, "mysql.password", ""),
            storageSection == null ? 4 : Math.max(1, storageSection.getInt("pool-size", 4))
        );

        ConfigurationSection securitySection = configuration.getConfigurationSection("security");
        SecurityConfiguration security = new SecurityConfiguration(
            securitySection == null ? 4 : Math.max(3, securitySection.getInt("min-length", 4)),
            securitySection == null ? 32 : Math.max(4, securitySection.getInt("max-length", 32)),
            securitySection == null ? 600000L : Math.max(10000L, securitySection.getLong("unlock-session-ms", 600000L)),
            securitySection != null && securitySection.getBoolean("allow-admin-password-reveal", false),
            securitySection == null ? "" : readString(securitySection, "admin-reveal-secret", "")
        );

        ConfigurationSection pickupSection = configuration.getConfigurationSection("pickup");
        PickupConfiguration pickup = new PickupConfiguration(
            pickupSection == null || pickupSection.getBoolean("auto-store-on-pickup", true),
            pickupSection == null || pickupSection.getBoolean("auto-store-mythic-loot", true),
            pickupSection == null || pickupSection.getBoolean("notify-on-auto-store", true)
        );

        SearchConfiguration search = new SearchConfiguration(
            Math.max(4, configuration.getInt("search.page-size", ui.pageSize())),
            normalizeId(configuration.getString("search.default-sort", "time"))
        );

        Map<String, WarehouseDefinition> warehouses = loadWarehouses(configuration.getConfigurationSection("warehouses"), logger);
        Map<String, CategoryDefinition> categories = loadCategories(configuration.getConfigurationSection("categories"), logger);
        List<String> bankCurrencies = loadBankCurrencies(
            configuration.getStringList("bank.currencies"),
            currencyBridge,
            logger
        );
        Map<String, DepositProductDefinition> depositProducts = loadDepositProducts(configuration.getConfigurationSection("bank.deposit-products"), logger);
        SharedConfiguration shared = loadShared(configuration.getConfigurationSection("shared"));
        Map<String, SortProfile> sortProfiles = loadSortProfiles(configuration.getConfigurationSection("sort-profiles"), logger);
        ItemMatcher blacklist = ItemMatcherLoader.load(configuration.getConfigurationSection("blacklist"), logger, "blacklist");

        ConfigurationSection showcaseSection = configuration.getConfigurationSection("showcase");
        ShowcaseConfiguration showcase = new ShowcaseConfiguration(
            showcaseSection == null || showcaseSection.getBoolean("enabled", true),
            showcaseSection == null ? 60 : Math.max(5, showcaseSection.getInt("cooldown-seconds", 60)),
            showcaseSection == null ? 9 : Math.max(1, showcaseSection.getInt("max-items", 9)),
            showcaseSection == null ? "" : readString(showcaseSection, "card-id", ""),
            showcaseSection == null ? "arcartxsuite.warehouse.showcase" : readString(showcaseSection, "permission", "arcartxsuite.warehouse.showcase")
        );

        return new WarehouseModuleConfiguration(
            debug,
            flushIntervalTicks,
            transferConfirmExpireHours,
            ui,
            storage,
            security,
            pickup,
            search,
            immutableCopy(warehouses),
            immutableCopy(categories),
            bankCurrencies,
            immutableCopy(depositProducts),
            shared,
            immutableCopy(sortProfiles),
            blacklist,
            showcase
        );
    }

    /** 仓库展示配置：冷却、最大展示物品数、聊天卡片 ID。 */
    public record ShowcaseConfiguration(boolean enabled, int cooldownSeconds, int maxItems, String cardId, String permission) {
        public boolean useCard() {
            return cardId != null && !cardId.isBlank();
        }
    }

    public WarehouseDefinition warehouse(String warehouseId) {
        return warehouses.get(normalizeId(warehouseId));
    }

    public CategoryDefinition category(String categoryId) {
        return categories.get(normalizeId(categoryId));
    }

    public DepositProductDefinition depositProduct(String productId) {
        return depositProducts.get(normalizeId(productId));
    }

    public SortProfile sortProfile(String sortId) {
        SortProfile profile = sortProfiles.get(normalizeId(sortId));
        if (profile != null) {
            return profile;
        }
        return sortProfiles.getOrDefault(search.defaultSortId(), sortProfiles.get("time"));
    }

    public CategoryDefinition otherCategory() {
        return categories.getOrDefault("other", new CategoryDefinition("other", "其它", 9999, "", List.of(), true));
    }

    private static Map<String, WarehouseDefinition> loadWarehouses(ConfigurationSection section, Logger logger) {
        LinkedHashMap<String, WarehouseDefinition> values = new LinkedHashMap<>();
        if (section == null) {
            values.put("personal", new WarehouseDefinition("personal", "&6个人仓库", true, "", 1, Map.of(1, new WarehouseLevelDefinition(1, 1000L, null))));
            return values;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null) {
                continue;
            }
            String id = normalizeId(rawId);
            Map<Integer, WarehouseLevelDefinition> levels = loadWarehouseLevels(child.getConfigurationSection("levels"));
            if (levels.isEmpty()) {
                levels = new LinkedHashMap<>();
                levels.put(1, new WarehouseLevelDefinition(1, 1000L, null));
            }
            int defaultLevel = Math.max(1, child.getInt("default-level", 1));
            if (!levels.containsKey(defaultLevel)) {
                defaultLevel = levels.keySet().stream().min(Integer::compareTo).orElse(1);
            }
            values.put(
                id,
                new WarehouseDefinition(
                    id,
                    readString(child, "display-name", rawId),
                    child.getBoolean("default-owned", false),
                    readString(child, "permission", ""),
                    defaultLevel,
                    immutableCopy(levels)
                )
            );
        }
        return values;
    }

    private static Map<Integer, WarehouseLevelDefinition> loadWarehouseLevels(ConfigurationSection section) {
        LinkedHashMap<Integer, WarehouseLevelDefinition> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String rawLevel : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawLevel);
            if (child == null) {
                continue;
            }
            try {
                int level = Integer.parseInt(rawLevel);
                String currencyId = normalizeId(child.getString("upgrade.currency", ""));
                BigDecimal amount = parseDecimal(child.get("upgrade.amount"), BigDecimal.ZERO);
                UpgradeCost upgradeCost = currencyId.isBlank() || amount.compareTo(BigDecimal.ZERO) <= 0
                    ? null
                    : new UpgradeCost(currencyId, amount);
                values.put(level, new WarehouseLevelDefinition(level, Math.max(1L, child.getLong("capacity", 1000L)), upgradeCost));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private static UpgradeCost loadUpgradeCost(ConfigurationSection section, String path) {
        if (section == null) {
            return null;
        }
        String currencyId = normalizeId(section.getString(path + ".currency", ""));
        BigDecimal amount = parseDecimal(section.get(path + ".amount"), BigDecimal.ZERO);
        return currencyId.isBlank() || amount.compareTo(BigDecimal.ZERO) <= 0
            ? null
            : new UpgradeCost(currencyId, amount);
    }

    private static Map<String, CategoryDefinition> loadCategories(ConfigurationSection section, Logger logger) {
        LinkedHashMap<String, CategoryDefinition> values = new LinkedHashMap<>();
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                ConfigurationSection child = section.getConfigurationSection(rawId);
                if (child == null) {
                    continue;
                }
                String id = normalizeId(rawId);
                String path = readString(child, "nbt.path", readString(child, "match.nbt.path", ""));
                List<String> valuesList = normalizeStringList(child.getStringList("nbt.values"));
                if (valuesList.isEmpty()) {
                    valuesList = normalizeStringList(child.getStringList("match.nbt.values"));
                }
                values.put(
                    id,
                    new CategoryDefinition(
                        id,
                        readString(child, "display-name", rawId),
                        child.getInt("priority", values.size()),
                        path,
                        valuesList,
                        child.getBoolean("default", "other".equals(id))
                    )
                );
            }
        }
        values.putIfAbsent("other", new CategoryDefinition("other", "其它", 9999, "", List.of(), true));
        return values;
    }

    private static List<String> loadBankCurrencies(
        List<String> rawCurrencies,
        CurrencyBridgeAPI currencyBridge,
        Logger logger
    ) {
        List<String> values = new ArrayList<>();
        for (String rawCurrency : rawCurrencies) {
            String id = normalizeId(rawCurrency);
            if (id.isBlank() || values.contains(id)) {
                continue;
            }
            if (currencyBridge != null && currencyBridge.definition(id) == null) {
                logger.warning("Warehouse bank currency is not defined in global currencies: " + id);
                continue;
            }
            values.add(id);
        }
        return List.copyOf(values);
    }

    private static Map<String, DepositProductDefinition> loadDepositProducts(ConfigurationSection section, Logger logger) {
        LinkedHashMap<String, DepositProductDefinition> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null || !child.getBoolean("enabled", true)) {
                continue;
            }
            String id = normalizeId(rawId);
            List<InterestTier> tiers = loadInterestTiers(child.getConfigurationSection("interest-tiers"), logger, "bank.deposit-products." + id);
            if (tiers.isEmpty()) {
                logger.warning("定期产品 '" + id + "' 没有有效利率阶梯，已跳过。");
                continue;
            }
            values.put(
                id,
                new DepositProductDefinition(
                    id,
                    readString(child, "display-name", rawId),
                    readString(child, "description", ""),
                    normalizeId(child.getString("currency", "money")),
                    Math.max(1L, child.getLong("duration-seconds", 86400L)),
                    parseDecimal(child.get("min-amount"), BigDecimal.ONE),
                    parseDecimal(child.get("max-amount"), BigDecimal.ZERO),
                    readString(child, "permission", ""),
                    List.copyOf(tiers)
                )
            );
        }
        return values;
    }

    private static List<InterestTier> loadInterestTiers(ConfigurationSection section, Logger logger, String path) {
        List<InterestTier> tiers = new ArrayList<>();
        if (section == null) {
            return tiers;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null) {
                continue;
            }
            BigDecimal min = parseDecimal(child.get("min"), BigDecimal.ZERO);
            BigDecimal max = parseDecimal(child.get("max"), BigDecimal.ZERO);
            BigDecimal rate = parseDecimal(child.get("rate"), BigDecimal.ZERO);
            if (rate.compareTo(BigDecimal.ZERO) < 0) {
                logger.warning(path + "." + rawId + " 利率小于 0，已跳过。");
                continue;
            }
            tiers.add(new InterestTier(min.max(BigDecimal.ZERO), max.max(BigDecimal.ZERO), rate));
        }
        tiers.sort(Comparator.comparing(InterestTier::minAmount));
        return tiers;
    }

    private static SharedConfiguration loadShared(ConfigurationSection section) {
        Map<String, SharedPermissionTier> tiers = new LinkedHashMap<>();
        Map<Integer, WarehouseLevelDefinition> levels = section == null
            ? Map.of()
            : loadWarehouseLevels(section.getConfigurationSection("levels"));
        int defaultLevel = section == null ? 1 : Math.max(1, section.getInt("default-level", 1));
        if (!levels.isEmpty() && !levels.containsKey(defaultLevel)) {
            defaultLevel = levels.keySet().stream().min(Integer::compareTo).orElse(1);
        }
        UpgradeCost createCost = loadUpgradeCost(section, "create-cost");
        SharedRoleNames roleNames = new SharedRoleNames(
            section == null ? "所有者" : readString(section, "role-names.owner", "所有者"),
            section == null ? "成员" : readString(section, "role-names.member", "成员"),
            section == null ? "观众" : readString(section, "role-names.viewer", "观众")
        );
        if (section != null && section.isConfigurationSection("permission-tiers")) {
            ConfigurationSection tierSection = section.getConfigurationSection("permission-tiers");
            for (String rawId : tierSection.getKeys(false)) {
                ConfigurationSection child = tierSection.getConfigurationSection(rawId);
                if (child == null) {
                    continue;
                }
                String id = normalizeId(rawId);
                tiers.put(
                    id,
                    new SharedPermissionTier(
                        id,
                        readString(child, "permission", ""),
                        child.getInt("priority", tiers.size()),
                        Math.max(0, child.getInt("max-owned", 1)),
                        Math.max(1, child.getInt("max-members", 6))
                    )
                );
            }
        }
        if (tiers.isEmpty()) {
            tiers.put("default", new SharedPermissionTier("default", "", 0, 1, 6));
        }
        return new SharedConfiguration(
            section == null || section.getBoolean("enabled", true),
            createCost,
            defaultLevel,
            roleNames,
            immutableCopy(levels),
            immutableCopy(tiers)
        );
    }

    private static Map<String, SortProfile> loadSortProfiles(ConfigurationSection section, Logger logger) {
        LinkedHashMap<String, SortProfile> values = new LinkedHashMap<>();
        if (section != null) {
            for (String rawId : section.getKeys(false)) {
                ConfigurationSection child = section.getConfigurationSection(rawId);
                if (child == null) {
                    continue;
                }
                String id = normalizeId(rawId);
                List<SortField> fields = new ArrayList<>();
                for (String rawField : child.getStringList("fields")) {
                    SortField field = parseSortField(rawField);
                    if (field == null) {
                        logger.warning("排序方案 '" + id + "' 包含无效字段 '" + rawField + "'，已跳过。");
                    } else {
                        fields.add(field);
                    }
                }
                if (!fields.isEmpty()) {
                    values.put(id, new SortProfile(id, List.copyOf(fields)));
                }
            }
        }
        values.putIfAbsent("time", new SortProfile("time", List.of(new SortField("updated", true), new SortField("name", false))));
        values.putIfAbsent("name", new SortProfile("name", List.of(new SortField("name", false))));
        values.putIfAbsent("amount", new SortProfile("amount", List.of(new SortField("amount", true), new SortField("updated", true))));
        return values;
    }

    private static SortField parseSortField(String rawField) {
        if (rawField == null || rawField.isBlank()) {
            return null;
        }
        String[] parts = rawField.trim().toLowerCase(Locale.ROOT).split(":", 2);
        String key = parts[0];
        boolean descending = parts.length > 1 && ("desc".equals(parts[1]) || "descending".equals(parts[1]));
        return switch (key) {
            case "name", "amount", "updated", "created", "material", "category", "warehouse" -> new SortField(key, descending);
            default -> null;
        };
    }

    private static String readString(FileConfiguration configuration, String path, String defaultValue) {
        String value = configuration.getString(path, defaultValue);
        return value == null ? defaultValue : value.trim();
    }

    private static String readString(ConfigurationSection section, String path, String defaultValue) {
        String value = section.getString(path, defaultValue);
        return value == null ? defaultValue : value.trim();
    }

    private static BigDecimal parseDecimal(Object rawValue, BigDecimal defaultValue) {
        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (rawValue != null) {
            try {
                return new BigDecimal(String.valueOf(rawValue).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = normalizeId(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeId(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT);
    }

    private static <K, V> Map<K, V> immutableCopy(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public enum StorageDialect {
        SQLITE("sqlite"),
        MYSQL("mysql");

        private final String configKey;

        StorageDialect(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }

        public static StorageDialect parse(String rawValue) {
            return "mysql".equals(normalizeId(rawValue)) ? MYSQL : SQLITE;
        }
    }

    /** 三套 AXUI 的 ID 与包标识配置。 */
    public record UiConfiguration(
        String uiId,
        String manageUiId,
        String bankUiId,
        String packetId,
        boolean registerUiOnEnable,
        boolean overwriteUiFiles,
        int pageSize
    ) {
    }

    /** 存储后端配置：SQLite 文件或 MySQL 连接参数。 */
    public record StorageConfiguration(
        StorageDialect dialect,
        String sqliteFileName,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        int connectionPoolSize
    ) {
        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (dialect == StorageDialect.MYSQL) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, connectionPoolSize, "");
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }
    }

    /** 二级密码安全策略：长度限制、会话有效期、管理员解密开关。 */
    public record SecurityConfiguration(
        int minLength,
        int maxLength,
        long unlockSessionMs,
        boolean allowAdminPasswordReveal,
        String adminRevealSecret
    ) {
    }

    /** 自动拾取配置：存入开关、Mythic 掉落开关、通知开关。 */
    public record PickupConfiguration(boolean autoStoreOnPickup, boolean autoStoreMythicLoot, boolean notifyOnAutoStore) {
    }

    /** 搜索与排序默认配置。 */
    public record SearchConfiguration(int pageSize, String defaultSortId) {
    }

    /** 个人仓库定义：显示名、默认拥有、权限、多等级容量。 */
    public record WarehouseDefinition(
        String id,
        String displayName,
        boolean defaultOwned,
        String permission,
        int defaultLevel,
        Map<Integer, WarehouseLevelDefinition> levels
    ) {
        public WarehouseLevelDefinition level(int level) {
            return levels.getOrDefault(level, levels.get(defaultLevel));
        }
    }

    /** 仓库单级定义：等级、容量、升级消耗。 */
    public record WarehouseLevelDefinition(int level, long capacity, UpgradeCost upgradeCost) {
    }

    /** 可购买槽位容量配置：初始容量、上限、每槽单价。 */
    /** 升级/创建费用：货币 ID 与金额。 */
    public record UpgradeCost(String currencyId, BigDecimal amount) {
    }

    /** 物品分类定义：按 NBT 路径与匹配值归类，fallback 为兜底分类。 */
    public record CategoryDefinition(
        String id,
        String displayName,
        int priority,
        String nbtPath,
        List<String> values,
        boolean fallback
    ) {
    }

    /** 定期存款产品定义：期限、金额区间、权限与阶梯利率。 */
    public record DepositProductDefinition(
        String id,
        String displayName,
        String description,
        String currencyId,
        long durationSeconds,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String permission,
        List<InterestTier> interestTiers
    ) {
        public InterestTier tierFor(BigDecimal amount) {
            InterestTier selected = null;
            for (InterestTier tier : interestTiers) {
                boolean aboveMin = amount.compareTo(tier.minAmount()) >= 0;
                boolean belowMax = tier.maxAmount().compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(tier.maxAmount()) <= 0;
                if (aboveMin && belowMax) {
                    selected = tier;
                }
            }
            return selected;
        }
    }

    /** 利率阶梯：金额区间与对应利率。 */
    public record InterestTier(BigDecimal minAmount, BigDecimal maxAmount, BigDecimal rate) {
    }

    /** 共享仓库全局配置：创建费用、等级、角色名、权限分层。 */
    public record SharedConfiguration(
        boolean enabled,
        UpgradeCost createCost,
        int defaultLevel,
        SharedRoleNames roleNames,
        Map<Integer, WarehouseLevelDefinition> levels,
        Map<String, SharedPermissionTier> permissionTiers
    ) {
    }

    /** 共享仓库角色显示名称映射。 */
    public record SharedRoleNames(String owner, String member, String viewer) {
    }

    /** 共享仓库权限分层：拥有权限节点可享受更高的 max-owned / max-members。 */
    public record SharedPermissionTier(
        String id,
        String permission,
        int priority,
        int maxOwned,
        int maxMembers
    ) {
    }

    /** 排序方案：按字段列表依次排序。 */
    public record SortProfile(String id, List<SortField> fields) {
    }

    /** 排序字段：键与是否降序。 */
    public record SortField(String key, boolean descending) {
    }

}
