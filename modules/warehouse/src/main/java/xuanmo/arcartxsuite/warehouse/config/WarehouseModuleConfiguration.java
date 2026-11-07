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
 * 在 {@link xuanmo.arcartxsuite.warehouse.WarehouseModule#configSpec()} 的 syncPolicy 中声明，不会被 ConfigDiagnosticEngine 覆盖。
 */
public record WarehouseModuleConfiguration(
    boolean debug,
    long transferConfirmExpireHours,
    VerifyMode verifyOnDeposit,
    long maxItemBytes,
    UiConfiguration ui,
    StorageConfiguration storage,
    PickupConfiguration pickup,
    SearchConfiguration search,
    Map<String, WarehouseDefinition> warehouses,
    Map<String, CategoryDefinition> categories,
    List<String> bankCurrencies,
    Map<String, DepositProductDefinition> depositProducts,
    SharedConfiguration shared,
    Map<String, SortProfile> sortProfiles,
    List<ItemMatcher> blacklist,
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
        long transferConfirmExpireHours = configuration.getLong("settings.transfer-confirm-expire-hours", 24L);
        VerifyMode verifyOnDeposit = VerifyMode.parse(configuration.getString("settings.verify-on-deposit", "meta"), logger);
        long maxItemBytes = Math.max(0L, configuration.getLong("settings.max-item-bytes", 4194304L));

        UiConfiguration ui = new UiConfiguration(
            readString(configuration, "ui.ui-id", "AXS:warehouse_storage"),
            readString(configuration, "ui.manage-ui-id", "AXS:warehouse_manage"),
            readString(configuration, "ui.bank-ui-id", "AXS:warehouse_bank"),
            readString(configuration, "ui.packet-id", "AXS_WAREHOUSE"),
            configuration.getBoolean("ui.register-ui-on-enable", true),
            configuration.getBoolean("ui.overwrite-ui-files", false),
            Math.max(1, configuration.getInt("ui.page-size", 72))
        );

        StorageConfiguration storage = StorageConfiguration.load(
            configuration.getConfigurationSection("storage")
        );

        ConfigurationSection pickupSection = configuration.getConfigurationSection("pickup");
        PickupConfiguration pickup = new PickupConfiguration(
            pickupSection == null || pickupSection.getBoolean("enabled", true),
            pickupSection == null || pickupSection.getBoolean("auto-store-on-pickup", true),
            pickupSection != null && pickupSection.getBoolean("auto-store-mythic-loot", false),
            pickupSection == null || pickupSection.getBoolean("mythic-loot-enabled", true),
            pickupSection == null || pickupSection.getBoolean("notify-on-auto-store", true)
        );

        SearchConfiguration search = new SearchConfiguration(
            Math.max(4, configuration.getInt("search.page-size", 18)),
            normalizeId(configuration.getString("search.default-sort", "default"))
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
        List<ItemMatcher> blacklist = new ArrayList<>();
        ConfigurationSection blacklistSection = configuration.getConfigurationSection("blacklist");
        if (blacklistSection != null) {
            // 黑名单的各字段独立拦截；不把名称、Lore 等拼成必须同时命中的 AND 条件。
            for (String key : blacklistSection.getKeys(false)) {
                var rule = new org.bukkit.configuration.file.YamlConfiguration();
                ConfigurationSection child = blacklistSection.getConfigurationSection(key);
                if (child != null) rule.createSection(key, child.getValues(false));
                else rule.set(key, blacklistSection.get(key));
                ItemMatcher matcher = ItemMatcherLoader.load(rule, logger, "blacklist." + key);
                if (!matcher.emptyMatcher()) blacklist.add(matcher);
            }
        }

        ConfigurationSection showcaseSection = configuration.getConfigurationSection("showcase");
        ShowcaseConfiguration showcase = new ShowcaseConfiguration(
            showcaseSection == null || showcaseSection.getBoolean("enabled", true),
            showcaseSection == null ? 60 : Math.max(5, showcaseSection.getInt("cooldown-seconds", 60)),
            showcaseSection == null ? "" : readString(showcaseSection, "card-id", ""),
            showcaseSection == null ? "arcartxsuite.warehouse.showcase" : readString(showcaseSection, "permission", "arcartxsuite.warehouse.showcase")
        );

        return new WarehouseModuleConfiguration(
            debug,
            transferConfirmExpireHours,
            verifyOnDeposit,
            maxItemBytes,
            ui,
            storage,
            pickup,
            search,
            immutableCopy(warehouses),
            immutableCopy(categories),
            bankCurrencies,
            immutableCopy(depositProducts),
            shared,
            immutableCopy(sortProfiles),
            List.copyOf(blacklist),
            showcase
        );
    }

    /** 仓库展示配置：冷却、最大展示物品数、聊天卡片 ID。 */
    public record ShowcaseConfiguration(boolean enabled, int cooldownSeconds, String cardId, String permission) {
        public boolean useCard() {
            return cardId != null && !cardId.isBlank();
        }
    }

    /**
     * 存入前往返校验级别（settings.verify-on-deposit）：
     * <ul>
     *   <li>{@code OFF} — 不做校验（最快，损坏物品可能入库后取出时才发现）</li>
     *   <li>{@code META} — 校验所有带 ItemMeta 的物品（默认，覆盖潜影盒/附魔/mod 物品等全部风险项）</li>
     *   <li>{@code ALL} — 校验全部物品，包括无 Meta 的纯物品</li>
     * </ul>
     */
    public enum VerifyMode {
        OFF,
        META,
        ALL;

        static VerifyMode parse(String raw, Logger logger) {
            String normalized = normalizeId(raw);
            return switch (normalized) {
                case "off", "false", "none" -> OFF;
                case "all", "true" -> ALL;
                case "meta", "" -> META;
                default -> {
                    if (logger != null) {
                        logger.warning("未知 settings.verify-on-deposit 值 '" + raw + "'，已回退为 meta");
                    }
                    yield META;
                }
            };
        }

        /** @return 是否需要对该物品执行序列化→反序列化往返校验 */
        public boolean covers(org.bukkit.inventory.ItemStack item) {
            return switch (this) {
                case OFF -> false;
                case ALL -> true;
                case META -> item != null && item.hasItemMeta();
            };
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

    public List<String> sortProfileIds() {
        List<String> ids = new ArrayList<>();
        ids.add("default");
        sortProfiles.keySet().stream().filter(id -> !id.equals("default")).forEach(ids::add);
        return List.copyOf(ids);
    }

    public CategoryDefinition otherCategory() {
        return categories.getOrDefault("other", new CategoryDefinition("other", "其它", 9999, "", List.of(), List.of(), List.of(), true));
    }

    private static Map<String, WarehouseDefinition> loadWarehouses(ConfigurationSection section, Logger logger) {
        LinkedHashMap<String, WarehouseDefinition> values = new LinkedHashMap<>();
        if (section == null) {
            values.put("personal", new WarehouseDefinition("personal", "&6个人仓库", true, "", 72L, 720L, null));
            return values;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(rawId);
            if (child == null) {
                continue;
            }
            String id = normalizeId(rawId);
            long baseSlots = Math.max(1L, child.getLong("base-slots", 72L));
            long maxSlots = Math.max(baseSlots, child.getLong("max-slots", 720L));
            UpgradeCost slotPrice = loadUpgradeCost(child, "slot-price");
            values.put(
                id,
                new WarehouseDefinition(
                    id,
                    readString(child, "display-name", rawId),
                    child.getBoolean("default-owned", false),
                    readString(child, "permission", ""),
                    baseSlots,
                    maxSlots,
                    slotPrice
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
                List<String> materialIds = normalizeMaterialKeyList(child.getStringList("material-ids"));
                List<String> materialPrefixes = normalizeMaterialKeyList(child.getStringList("material-prefixes"));
                values.put(
                    id,
                    new CategoryDefinition(
                        id,
                        readString(child, "display-name", rawId),
                        child.getInt("priority", values.size()),
                        path,
                        valuesList,
                        materialIds,
                        materialPrefixes,
                        child.getBoolean("default", "other".equals(id))
                    )
                );
            }
        }
        values.putIfAbsent("other", new CategoryDefinition("other", "其它", 9999, "", List.of(), List.of(), List.of(), true));
        return values;
    }

    /** 材质键列表归一化：小写、去空白、去重（注册键形如 minecraft:diamond / tacz:modern_kinetic_gun）。 */
    private static List<String> normalizeMaterialKeyList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (String entry : raw) {
            String normalized = normalizeId(entry);
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
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

    /** 存储后端配置：共享模式或自建 SQLite/MySQL 连接参数。 */
    public record StorageConfiguration(
        boolean shared,
        String mode,
        String sqliteFileName,
        String host,
        int port,
        String database,
        String username,
        String password,
        String tablePrefix,
        int poolSize
    ) {
        /**
         * 从配置节加载存储配置。
         * <p>
         * shared=true（默认）时使用全部默认值，不读取其余字段；
         * shared=false 时读取 mode/sqlite/mysql 等自建存储字段。
         *
         * @param section storage 配置节，可为 {@code null}
         * @return 解析后的存储配置
         */
        public static StorageConfiguration load(ConfigurationSection section) {
            if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
            boolean shared = section.getBoolean("shared", true);
            // 向后兼容：如果 shared 字段不存在但 mode=mysql 存在，视为 shared=false
            if (!section.isSet("shared") && "mysql".equalsIgnoreCase(section.getString("mode", "sqlite"))) {
                shared = false;
            }
            if (shared) {
                return new StorageConfiguration(true, "sqlite", "warehouse.db",
                    "127.0.0.1", 3306, "arcartxsuite", "root", "",
                    "axs_warehouse_", 4);
            }
            return new StorageConfiguration(false,
                readString(section, "mode", "sqlite"),
                readString(section, "sqlite.file", "warehouse.db"),
                readString(section, "mysql.host", "127.0.0.1"),
                Math.max(1, section.getInt("mysql.port", 3306)),
                readString(section, "mysql.database", "arcartxsuite"),
                readString(section, "mysql.username", "root"),
                readString(section, "mysql.password", ""),
                readString(section, "mysql.table-prefix", "axs_warehouse_"),
                Math.max(1, section.getInt("pool-size", 4))
            );
        }

        public boolean isSqlite() {
            return !"mysql".equalsIgnoreCase(mode);
        }

        public xuanmo.arcartxsuite.api.storage.StorageDescriptor toDescriptor() {
            if (!isSqlite()) {
                return xuanmo.arcartxsuite.api.storage.StorageDescriptor.mysql(
                    host, port, database, username, password, poolSize, tablePrefix);
            }
            return xuanmo.arcartxsuite.api.storage.StorageDescriptor.sqlite(sqliteFileName);
        }

        /**
         * 判断是否为覆盖模式（模块自建连接池）。
         * <p>
         * 当 shared=false 时返回 true（模块独立连接，自建存储）；
         * shared=true 时返回 false（使用本体共享数据源）。
         *
         * @return {@code true} 表示模块应自建连接池
         */
        public boolean hasOverride() {
            return !shared;
        }
    }

    /**
     * 二级密码安全策略已由宿主统一管控（security.secondary-password.min-length/max-length），
     * 本模块不再持有密码策略配置。
     */
    /**
     * 自动拾取配置。
     *
     * @param enabled            服务器总开关：false 时全服自动入库功能失效，玩家个人开关不生效
     * @param autoStoreOnPickup  拾取时自动存入仓库（玩家个人设置默认值）
     * @param autoStoreMythicLoot 自动存入 MythicMobs 自定义掉落物（玩家个人设置默认值）
     * @param mythicLootEnabled  服务器开关：是否允许 MythicMobs 自定义掉落物自动入库
     * @param notifyOnAutoStore  自动存入时通知玩家（玩家个人设置默认值）
     */
    public record PickupConfiguration(boolean enabled, boolean autoStoreOnPickup, boolean autoStoreMythicLoot, boolean mythicLootEnabled, boolean notifyOnAutoStore) {
    }

    /** 搜索与排序默认配置。 */
    public record SearchConfiguration(int pageSize, String defaultSortId) {
    }

    /** 个人仓库定义：显示名、默认拥有、权限、逐格解锁参数。 */
    public record WarehouseDefinition(
        String id,
        String displayName,
        boolean defaultOwned,
        String permission,
        long baseSlots,
        long maxSlots,
        UpgradeCost slotPrice
    ) {
    }

    /** 共享仓库单级定义：等级、容量、升级消耗。 */
    public record WarehouseLevelDefinition(int level, long capacity, UpgradeCost upgradeCost) {
    }

    /** 可购买槽位容量配置：初始容量、上限、每槽单价。 */
    /** 升级/创建费用：货币 ID 与金额。 */
    public record UpgradeCost(String currencyId, BigDecimal amount) {
    }

    /**
     * 物品分类定义：按物品注册键（material-ids 精确 / material-prefixes 前缀）
     * 或 NBT 路径与匹配值归类，三组规则为"或"关系，fallback 为兜底分类。
     */
    public record CategoryDefinition(
        String id,
        String displayName,
        int priority,
        String nbtPath,
        List<String> values,
        List<String> materialIds,
        List<String> materialPrefixes,
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
