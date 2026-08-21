package xuanmo.arcartxsuite.fishing.model;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 鱼饵定义，描述一种鱼饵的展示与属性修正。
 * <p>
 * 关联模块：{@code fishing}。由配置目录 {@code baits/} 下的 yml 文件加载。
 *
 * @param id                    鱼饵唯一标识
 * @param displayName           展示名称
 * @param itemRef               对应物品引用
 * @param isDefault             是否为默认鱼饵
 * @param fishAttractModifiers  鱼种吸引力修正映射（鱼种 id → 修正系数）
 * @param treasureChanceBoost   宝藏概率加成
 * @param maxDurabilityBonus    鱼竿耐久度加成上限
 */
public record BaitDefinition(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull IconDefinition itemRef,
    boolean isDefault,
    @NotNull Map<String, Double> fishAttractModifiers,
    double treasureChanceBoost,
    int maxDurabilityBonus
) {

    public double attractModifier(@NotNull String fishId) {
        return fishAttractModifiers.getOrDefault(fishId, 1.0);
    }
}