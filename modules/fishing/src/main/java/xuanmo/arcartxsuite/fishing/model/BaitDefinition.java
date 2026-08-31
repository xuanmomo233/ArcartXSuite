package xuanmo.arcartxsuite.fishing.model;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;
import xuanmo.arcartxsuite.api.item.ItemMatcher;

/**
 * 鱼饵定义，描述一种鱼饵的展示与属性修正。
 * <p>
 * 关联模块：{@code fishing}。由配置目录 {@code baits/} 下的 yml 文件加载。
 * 鱼饵物品由外部物品库（NeigeItems/MythicMobs 等）生成，配置文件仅用于识别物品和设置效果。
 * 识别方式：用 {@link #matcher()}（ItemMatcher）匹配玩家背包里的物品。
 *
 * @param id                    鱼饵唯一标识
 * @param displayName           展示名称
 * @param itemRef               对应物品引用（用于 UI 图标显示）
 * @param matcher               物品匹配规则（用于识别玩家背包里的饵料物品）
 * @param isDefault             是否为默认鱼饵
 * @param fishAttractModifiers  鱼种吸引力修正映射（鱼种 id → 修正系数）
 * @param treasureChanceBoost   宝藏概率加成
 * @param maxDurabilityBonus    鱼竿耐久度加成上限
 */
public record BaitDefinition(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull IconDefinition itemRef,
    @Nullable ItemMatcher matcher,
    boolean isDefault,
    @NotNull Map<String, Double> fishAttractModifiers,
    double treasureChanceBoost,
    int maxDurabilityBonus
) {

    public double attractModifier(@NotNull String fishId) {
        return fishAttractModifiers.getOrDefault(fishId, 1.0);
    }
}