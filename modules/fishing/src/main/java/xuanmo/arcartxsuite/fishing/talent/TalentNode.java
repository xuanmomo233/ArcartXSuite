package xuanmo.arcartxsuite.fishing.talent;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 天赋节点定义。
 * <p>
 * 普通节点支持多级升级，每级的效果值通过 {@link #levelValues()} 显式配置（无规律）。
 * 联动节点固定 1 级（{@link #maxLevel()} = 1），使用 {@link #levelValues()} 的首元素。
 *
 * @param id          节点唯一 ID
 * @param treeId      所属树 ID（联动节点为空字符串）
 * @param tier        层级（1-5，联动节点为 0）
 * @param branch      分支（root / a / b / c / d，联动为 "link"）
 * @param displayName 显示名称
 * @param description 描述
 * @param effectType  效果类型（单效果节点）
 * @param levelValues 每级的效果值列表（索引 0 = 1 级的值），长度即 maxLevel
 * @param effects     多效果列表（仅联动节点 link-3 使用，普通节点为空）
 * @param prerequisites 前置节点 ID 列表
 * @param icon        节点图标定义（null 时回退到 effectType 映射）
 */
public record TalentNode(
    @NotNull String id,
    @NotNull String treeId,
    int tier,
    @NotNull String branch,
    @NotNull String displayName,
    @NotNull String description,
    @Nullable String effectType,
    @NotNull List<Double> levelValues,
    @NotNull List<TalentEffect> effects,
    @NotNull List<String> prerequisites,
    @Nullable IconDefinition icon
) {

    /**
     * 最大可升级等级。
     * <p>
     * 新版天赋系统所有节点固定 1 级：点亮/未点亮。
     * 读取配置时已有兼容处理，这里统一返回 1。
     *
     * @return 最大等级，固定 1
     */
    public int maxLevel() {
        return 1;
    }

    /**
     * 获取点亮后的效果值。
     *
     * @param level 当前等级（1-based）
     * @return 该等级的效果值，越界返回 0
     */
    public double valueAtLevel(int level) {
        if (level <= 0 || level > levelValues.size()) return 0.0;
        return levelValues.get(0);
    }

    /**
     * 判断是否为联动节点（不属于任何树）。
     *
     * @return {@code true} 表示是联动节点
     */
    public boolean isLink() {
        return treeId.isEmpty();
    }

    /**
     * 是否支持多级升级。
     * <p>
     * 新版所有天赋固定 1 级，不再有升级概念，只有解锁。
     *
     * @return {@code false}
     */
    public boolean isUpgradable() {
        return false;
    }
}
