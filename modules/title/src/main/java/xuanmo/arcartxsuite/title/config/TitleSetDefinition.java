package xuanmo.arcartxsuite.title.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 称号套装定义。
 * <p>
 * 当玩家拥有套装中足够数量的称号时，激活套装奖励属性。
 *
 * @param id                   套装唯一标识
 * @param displayName          套装显示名称
 * @param requiredTitleIds     套装所需的称号 ID 列表
 * @param completionThreshold  激活阈值，{@code <= 0} 时默认使用所需称号总数
 * @param bonusAttributes      套装激活后的奖励属性映射
 * @param bonusAttributeLines  奖励属性原始文本行
 */
public record TitleSetDefinition(
    String id,
    String displayName,
    List<String> requiredTitleIds,
    int completionThreshold,
    Map<String, Double> bonusAttributes,
    List<String> bonusAttributeLines
) {
    public TitleSetDefinition {
        requiredTitleIds = requiredTitleIds == null ? List.of() : List.copyOf(requiredTitleIds);
        bonusAttributes = bonusAttributes == null || bonusAttributes.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(bonusAttributes));
        bonusAttributeLines = bonusAttributeLines == null ? List.of() : List.copyOf(bonusAttributeLines);
    }

    /**
     * @return 有效的激活阈值；{@code completionThreshold <= 0} 时返回所需称号总数，否则取两者较小值
     */
    public int effectiveThreshold() {
        return completionThreshold <= 0 ? requiredTitleIds.size() : Math.min(completionThreshold, requiredTitleIds.size());
    }
}
