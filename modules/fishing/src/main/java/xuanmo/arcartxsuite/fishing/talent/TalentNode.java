package xuanmo.arcartxsuite.fishing.talent;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.icon.IconDefinition;

/**
 * 天赋节点定义。
 *
 * @param id          节点唯一 ID
 * @param treeId      所属树 ID
 * @param tier        层级（1-5）
 * @param branch      分支（root / a / b / c / d）
 * @param displayName 显示名称
 * @param description 描述
 * @param effects     效果列表（联动节点可能有多个效果）
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
    @NotNull List<TalentEffect> effects,
    @NotNull List<String> prerequisites,
    @Nullable IconDefinition icon
) {

    /**
     * 判断是否为联动节点（不属于任何树）。
     *
     * @return {@code true} 表示是联动节点
     */
    public boolean isLink() {
        return treeId.isEmpty();
    }
}
