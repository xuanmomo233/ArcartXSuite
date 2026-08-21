package xuanmo.arcartxsuite.fishing.model;

import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 宝藏定义，描述钓鱼可获得的非鱼类物品。
 * <p>
 * 关联模块：{@code fishing}。由配置目录 {@code treasures/} 下的 yml 文件加载。
 *
 * @param id          宝藏唯一标识
 * @param displayName 展示名称
 * @param itemRef     对应物品图标定义
 * @param chance      掉落概率（0~1）
 */
public record TreasureDefinition(
    @NotNull String id,
    @NotNull String displayName,
    @NotNull IconDefinition itemRef,
    double chance
) {
}