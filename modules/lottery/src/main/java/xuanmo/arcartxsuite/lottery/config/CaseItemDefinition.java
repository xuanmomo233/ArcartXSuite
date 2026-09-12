package xuanmo.arcartxsuite.lottery.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcher;
import xuanmo.arcartxsuite.api.item.ItemMatcherLoader;

import java.util.logging.Logger;

/**
 * CASE 开箱的箱子物品定义。
 * <p>
 * 描述玩家手持触发开箱 UI 的物品匹配规则与消耗数量，
 * 同时携带 UI 展示所需的显示名与图标。
 *
 * @param matcher     物品匹配器，用于识别玩家手持的箱子物品
 * @param amount      单次开箱消耗的箱子数量（正整数）
 * @param displayName UI 显示名称，为 null 时使用 "箱子"
 * @param icon        UI 图标定义，可为 null
 */
public record CaseItemDefinition(
    @NotNull ItemMatcher matcher,
    int amount,
    @Nullable String displayName,
    @Nullable IconDefinition icon
) {
    /**
     * 从 YAML 配置节加载箱子物品定义。
     *
     * @param section 配置节（case-item 块）
     * @param logger  日志记录器
     * @param path    配置路径前缀（用于日志定位）
     * @return 解析后的 {@link CaseItemDefinition}，配置缺失或无效时返回 {@code null}
     */
    public static @Nullable CaseItemDefinition load(@Nullable ConfigurationSection section, Logger logger, String path) {
        if (section == null) return null;
        ItemMatcher matcher = ItemMatcherLoader.load(section.getConfigurationSection("matcher"), logger, path + ".matcher");
        if (matcher.emptyMatcher()) {
            logger.warning(path + ": case-item.matcher 为空，已跳过 case-item 配置");
            return null;
        }
        int amount = section.getInt("amount", 1);
        if (amount <= 0) {
            logger.warning(path + ": case-item.amount 必须为正数，已重置为 1");
            amount = 1;
        }
        String displayName = section.getString("display-name");
        IconDefinition icon = IconDefinition.load(section, "icon");
        return new CaseItemDefinition(matcher, amount, displayName, icon);
    }
}
