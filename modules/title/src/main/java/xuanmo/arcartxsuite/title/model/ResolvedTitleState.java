package xuanmo.arcartxsuite.title.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xuanmo.arcartxsuite.title.config.TitleDefinition;

/**
 * 称号状态解析结果。
 * <p>
 * 由 {@link xuanmo.arcartxsuite.title.service.TitleStateResolver} 将 {@link PlayerTitleState}
 * 与 {@link xuanmo.arcartxsuite.title.config.TitleModuleConfiguration} 合并后生成，
 * 包含已装备称号定义、展示/收集/总属性汇总、套装完成度与奖励属性等。
 *
 * @param equippedTitleIdsByGroup         各分组已装备的称号 ID 映射
 * @param equippedTitlesByGroup           各分组已装备的称号定义映射
 * @param displayAttributes               展示属性汇总（仅装备称号）
 * @param collectionAttributes            收集属性汇总（所有拥有称号）
 * @param totalAttributes                 总属性汇总（收集 + 展示 + 套装奖励）
 * @param displayAttributeSourceLines     展示属性源文本行
 * @param collectionAttributeSourceLines  收集属性源文本行
 * @param totalAttributeSourceLines       总属性源文本行
 * @param ownedCount                      拥有称号数量
 * @param hiddenCount                     隐藏称号数量
 * @param setCompletionCounts             套装完成度映射（套装 ID → 已拥有数量）
 * @param setActiveMap                    套装激活状态映射（套装 ID → 是否激活）
 * @param setBonusAttributes              套装奖励属性汇总
 * @param setBonusAttributeSourceLines    套装奖励属性源文本行
 * @param totalDisplayTitle               展示称号定义，无展示称号时为 {@code null}
 */
public record ResolvedTitleState(
    Map<String, String> equippedTitleIdsByGroup,
    Map<String, TitleDefinition> equippedTitlesByGroup,
    Map<String, Double> displayAttributes,
    Map<String, Double> collectionAttributes,
    Map<String, Double> totalAttributes,
    List<String> displayAttributeSourceLines,
    List<String> collectionAttributeSourceLines,
    List<String> totalAttributeSourceLines,
    int ownedCount,
    int hiddenCount,
    Map<String, Integer> setCompletionCounts,
    Map<String, Boolean> setActiveMap,
    Map<String, Double> setBonusAttributes,
    List<String> setBonusAttributeSourceLines,
    TitleDefinition totalDisplayTitle
) {
    public ResolvedTitleState {
        equippedTitleIdsByGroup = immutableStringCopy(equippedTitleIdsByGroup);
        equippedTitlesByGroup = immutableTitleCopy(equippedTitlesByGroup);
        displayAttributes = immutableDoubleCopy(displayAttributes);
        collectionAttributes = immutableDoubleCopy(collectionAttributes);
        totalAttributes = immutableDoubleCopy(totalAttributes);
        displayAttributeSourceLines = immutableListCopy(displayAttributeSourceLines);
        collectionAttributeSourceLines = immutableListCopy(collectionAttributeSourceLines);
        totalAttributeSourceLines = immutableListCopy(totalAttributeSourceLines);
        setCompletionCounts = immutableIntCopy(setCompletionCounts);
        setActiveMap = immutableBoolCopy(setActiveMap);
        setBonusAttributes = immutableDoubleCopy(setBonusAttributes);
        setBonusAttributeSourceLines = immutableListCopy(setBonusAttributeSourceLines);
    }

    private static Map<String, Double> immutableDoubleCopy(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, TitleDefinition> immutableTitleCopy(Map<String, TitleDefinition> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, String> immutableStringCopy(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, Integer> immutableIntCopy(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, Boolean> immutableBoolCopy(Map<String, Boolean> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static List<String> immutableListCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }
}
