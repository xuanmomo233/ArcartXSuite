package xuanmo.arcartxsuite.fishing.talent;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * 天赋树定义，包含树元数据与节点列表。
 *
 * @param displayName 树显示名称
 * @param description 树描述
 * @param icon        树图标物品 ID
 * @param nodes       节点列表
 */
public record TalentTreeDefinition(
    @NotNull String displayName,
    @NotNull String description,
    @NotNull String icon,
    @NotNull List<TalentNode> nodes
) {
}
