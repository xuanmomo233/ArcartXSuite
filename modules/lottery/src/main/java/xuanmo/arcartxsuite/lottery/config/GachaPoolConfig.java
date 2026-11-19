package xuanmo.arcartxsuite.lottery.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.lottery.model.PoolItem;

/**
 * GACHA 抽卡奖池配置。
 * <p>
 * 包含保底机制（硬保底/软保底）、基础概率、UP 概率、命运点系统、
 * 共享保底组以及各星级奖品列表。
 *
 * @param poolType           奖池类型（角色/武器/常驻）
 * @param pity5star          5 星硬保底抽数
 * @param pity4star          4 星硬保底抽数
 * @param softPityStart      软保底起始抽数
 * @param softPityIncrement  软保底每抽概率增量
 * @param base5starRate      5 星基础概率
 * @param base4starRate      4 星基础概率
 * @param upRate             UP 物品在对应星级中的占比概率
 * @param fatePointCap       命运点上限（武器池定轨机制），0 表示不启用
 * @param sharedPityGroup    共享保底组名，为 {@code null} 表示不共享
 * @param up5starItems       UP 5 星奖品列表
 * @param standard5starItems 常驻 5 星奖品列表
 * @param up4starItems       UP 4 星奖品列表
 * @param standard4starItems 常驻 4 星奖品列表
 * @param star3Items         3 星奖品列表
 */
public record GachaPoolConfig(
    GachaPoolType poolType,
    int pity5star,
    int pity4star,
    int softPityStart,
    double softPityIncrement,
    double base5starRate,
    double base4starRate,
    double upRate,
    int fatePointCap,
    @Nullable String sharedPityGroup,
    @NotNull List<PoolItem> up5starItems,
    @NotNull List<PoolItem> standard5starItems,
    @NotNull List<PoolItem> up4starItems,
    @NotNull List<PoolItem> standard4starItems,
    @NotNull List<PoolItem> star3Items
) {
    /**
     * GACHA 奖池类型枚举。
     */
    public enum GachaPoolType {
        CHARACTER, WEAPON, STANDARD
    }

    public boolean hasUp5starItems() {
        return up5starItems != null && !up5starItems.isEmpty();
    }

    /**
     * 从 YAML 配置节加载 GACHA 奖池配置。
     *
     * @param section 配置节，为 {@code null} 时使用默认值
     * @param logger  日志记录器
     * @return 解析后的 {@link GachaPoolConfig} 实例
     * @throws IllegalArgumentException 当保底/概率配置无效或必需奖品列表为空时抛出
     */
    public static GachaPoolConfig load(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) section = new org.bukkit.configuration.MemoryConfiguration();

        String poolTypeStr = section.getString("pool-type", "CHARACTER");
        GachaPoolType poolType = GachaPoolType.valueOf(poolTypeStr.toUpperCase());

        int pity5 = section.getInt("pity-5star", 90);
        int pity4 = section.getInt("pity-4star", 10);
        int softStart = section.getInt("soft-pity-start", 73);
        double softInc = section.getDouble("soft-pity-increment", 0.06);
        double base5 = section.getDouble("base-5star-rate", 0.006);
        double base4 = section.getDouble("base-4star-rate", 0.051);
        double upRate = section.getDouble("up-rate", 0.5);
        int fateCap = section.getInt("fate-point-cap", 0);
        String sharedGroup = section.getString("shared-pity-group");
        if (sharedGroup != null && sharedGroup.isBlank()) sharedGroup = null;

        List<PoolItem> up5 = loadItems(section.getConfigurationSection("up-5star-items"), logger);
        List<PoolItem> std5 = loadItems(section.getConfigurationSection("standard-5star-items"), logger);
        List<PoolItem> up4 = loadItems(section.getConfigurationSection("up-4star-items"), logger);
        List<PoolItem> std4 = loadItems(section.getConfigurationSection("standard-4star-items"), logger);
        List<PoolItem> star3 = loadItems(section.getConfigurationSection("star3-items"), logger);
        if (pity5 <= 0 || pity4 <= 0 || softStart < 0
                || softInc < 0 || base5 < 0 || base4 < 0 || upRate < 0 || upRate > 1
                || fateCap < 0) {
            throw new IllegalArgumentException("GACHA 保底、概率或命运值配置无效");
        }
        requireItems("star3", star3);
        if (pity4 > 0 || base4 > 0) {
            // 4 星 UP 与标准至少有一个非空即可
            if ((up4 == null || up4.isEmpty()) && (std4 == null || std4.isEmpty())) {
                throw new IllegalArgumentException("GACHA 4 星配置无效: up4star 与 standard4star 至少需要一个非空");
            }
        }
        if (pity5 > 0 || base5 > 0) {
            if (upRate > 0) requireItems("up5star", up5);
            if (upRate < 1) requireItems("standard5star", std5);
        }

        return new GachaPoolConfig(
            poolType, pity5, pity4, softStart, softInc, base5, base4, upRate, fateCap, sharedGroup,
            up5, std5, up4, std4, star3
        );
    }

    private static void requireItems(String tier, List<PoolItem> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("GACHA 可达星级层 " + tier + " 没有可用奖品");
        }
    }

    private static List<PoolItem> loadItems(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) return List.of();
        List<PoolItem> items = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSec = section.getConfigurationSection(key);
            if (itemSec == null) continue;
            PoolItem item = PoolItem.load(itemSec, logger);
            if (item != null) items.add(item);
        }
        return Collections.unmodifiableList(items);
    }
}
