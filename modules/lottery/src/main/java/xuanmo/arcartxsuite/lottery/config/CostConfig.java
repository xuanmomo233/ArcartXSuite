package xuanmo.arcartxsuite.lottery.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherLoader;
import org.jetbrains.annotations.Nullable;

/**
 * 抽奖或兑换使用的多资源消耗配置。
 * <p>
 * {@code single} 与 {@code ten} 分别描述单抽与十连的完整消耗条目。
 * 每个条目可以是货币、物品或积分，通过资源类型与 ID 区分，彼此互不影响。
 * 十连不做倍数换算，未配置十连即表示该奖池不提供十连。
 *
 * @param single 单抽的完整消耗条目
 * @param ten 十连的完整消耗条目
 */
public record CostConfig(
    @NotNull List<CostEntry> single,
    @NotNull List<CostEntry> ten
) {
    private static final Logger LOGGER = Logger.getLogger("ArcartX-Lottery");

    /**
     * 消耗模式枚举。
     */
    public enum CostMode {
        CURRENCY, ITEM, POINT
    }

    /**
     * 从 YAML 配置节加载消耗配置。
     *
     * @param section 配置节，为 {@code null} 时使用默认值
     * @return 解析后的 {@link CostConfig} 实例
     * @throws IllegalArgumentException 当 type 非合法枚举值或 amount 非正整数时抛出
     */
    public static CostConfig load(@Nullable ConfigurationSection section) {
        if (section == null) {
            return new CostConfig(List.of(), List.of());
        }
        return new CostConfig(loadEntries(section, "single", "cost.single"), loadEntries(section, "ten", "cost.ten"));
    }

    /**
     * 解析消耗配置，解析失败时在异常信息中带上配置来源 ID。
     *
     * @param section cost 配置节点
     * @param ownerId 出错时用于定位的奖池或兑换商品 ID
     * @return 解析出的消耗配置
     * @throws IllegalArgumentException cost 配置不合法时抛出
     */
    public static CostConfig load(@Nullable ConfigurationSection section, String ownerId) {
        try {
            return new CostConfig(loadEntries(section, "single", ownerId + ".single"),
                loadEntries(section, "ten", ownerId + ".ten"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid cost configuration for "
                + ownerId + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * 解析直接以列表书写的 cost 条目，用于兑换商品配置。
     *
     * @param entries YAML 中的条目列表
     * @return 解析出的消耗条目列表
     * @throws IllegalArgumentException cost 条目不合法时抛出
     */
    public static List<CostEntry> loadDirectList(@Nullable List<Map<?, ?>> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        MemoryConfiguration configuration = new MemoryConfiguration();
        configuration.set("single", entries);
        return loadEntries(configuration, "single", "cost");
    }

    /**
     * 解析直接以列表书写的 cost 条目，解析失败时在异常信息中带上商品 ID。
     *
     * @param entries YAML 中的条目列表
     * @param ownerId 出错时用于定位的兑换商品 ID
     * @return 解析出的消耗条目列表
     * @throws IllegalArgumentException cost 条目不合法时抛出
     */
    public static List<CostEntry> loadDirectList(@Nullable List<Map<?, ?>> entries, String ownerId) {
        try {
            if (entries == null || entries.isEmpty()) return List.of();
            MemoryConfiguration configuration = new MemoryConfiguration();
            configuration.set("single", entries);
            return loadEntries(configuration, "single", ownerId + ".single");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid cost configuration for "
                + ownerId + ": " + exception.getMessage(), exception);
        }
    }

    private static List<CostEntry> loadEntries(ConfigurationSection section, String key, String path) {
        ConfigurationSection entriesSection = section.getConfigurationSection(key);
        if (entriesSection != null) {
            List<CostEntry> result = new ArrayList<>();
            for (String childKey : entriesSection.getKeys(false)) {
                ConfigurationSection entrySection = entriesSection.getConfigurationSection(childKey);
                if (entrySection != null) result.add(loadEntry(entrySection, path + "." + childKey));
            }
            return result;
        }
        List<Map<?, ?>> maps = section.getMapList(key);
        if (maps.isEmpty()) return Collections.emptyList();
        List<CostEntry> result = new ArrayList<>();
        for (int index = 0; index < maps.size(); index++) result.add(loadEntry(toSection(maps.get(index)), path + "[" + index + "]"));
        return result;
    }

    private static ConfigurationSection toSection(Map<?, ?> map) {
        MemoryConfiguration configuration = new MemoryConfiguration();
        configuration.set("entry", map);
        ConfigurationSection section = configuration.getConfigurationSection("entry");
        if (section == null) throw new IllegalArgumentException("Invalid cost entry");
        return section;
    }

    private static CostEntry loadEntry(ConfigurationSection section, String path) {
        String type = section.getString("type", "ITEM");
        String id = section.getString("id", "");
        CostMode mode;
        try {
            mode = CostMode.valueOf(type.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + ": invalid cost type " + type, exception);
        }
        Object rawAmount = section.get("amount", 1);
        int amount;
        try {
            if (rawAmount instanceof Number number) {
                amount = new java.math.BigDecimal(number.toString()).intValueExact();
            } else {
                amount = new java.math.BigDecimal(String.valueOf(rawAmount)).intValueExact();
            }
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid cost amount: " + rawAmount, exception);
        }
        String displayName = section.getString("display-name");
        IconDefinition icon = IconDefinition.load(section, "icon");
        ItemMatcher matcher = null;
        if (mode == CostMode.ITEM) {
            if (!id.isBlank()) {
                throw new IllegalArgumentException(path + ": ITEM cost must not define id; use matcher");
            }
            matcher = ItemMatcherLoader.load(section.getConfigurationSection("matcher"), LOGGER, path + ".matcher");
            if (matcher.emptyMatcher()) {
                throw new IllegalArgumentException(path + ": ITEM cost requires a non-empty matcher");
            }
        }
        return new CostEntry(mode, id, amount, displayName, icon, matcher);
    }

    public record CostEntry(
        @NotNull CostMode type,
        @NotNull String id,
        int amount,
        @Nullable String displayName,
        @Nullable IconDefinition icon,
        @Nullable ItemMatcher matcher
    ) {
        public CostEntry {
            if (amount <= 0) throw new IllegalArgumentException("Cost amount must be positive");
            if (type == CostMode.ITEM && (matcher == null || matcher.emptyMatcher())) {
                throw new IllegalArgumentException("ITEM cost requires a non-empty matcher");
            }
        }
    }
}
