package xuanmo.arcartxsuite.lottery.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * 抽奖消耗配置。
 * <p>
 * 支持货币扣除与物品扣除两种模式，分别对应单抽与十连消耗。
 *
 * @param mode       消耗模式（CURRENCY 或 ITEM）
 * @param currency   货币 ID，仅 CURRENCY 模式生效
 * @param single     单次抽奖消耗数量
 * @param ten        十连抽奖消耗数量
 * @param itemId     物品 ID，仅 ITEM 模式生效
 * @param itemAmount 每次扣除的物品数量，仅 ITEM 模式生效
 */
public record CostConfig(
    CostMode mode,
    String currency,
    int single,
    int ten,
    String itemId,
    int itemAmount
) {
    /**
     * 消耗模式枚举。
     */
    public enum CostMode {
        CURRENCY, ITEM
    }

    /**
     * 从 YAML 配置节加载消耗配置。
     *
     * @param section 配置节，为 {@code null} 时使用默认值
     * @return 解析后的 {@link CostConfig} 实例
     * @throws IllegalArgumentException 当 mode 字段非合法枚举值时抛出
     */
    public static CostConfig load(@Nullable ConfigurationSection section) {
        if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();
        String modeStr = section.getString("mode", "CURRENCY");
        CostMode mode = CostMode.valueOf(modeStr.toUpperCase());
        return new CostConfig(
            mode,
            section.getString("currency", ""),
            section.getInt("single", 1),
            section.getInt("ten", 10),
            section.getString("item-id", ""),
            section.getInt("item-amount", 1)
        );
    }
}
