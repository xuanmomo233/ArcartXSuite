package xuanmo.arcartxsuite.title.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xuanmo.arcartxsuite.title.config.TitleMythicLibConfiguration;

/**
 * MythicLib 属性同步计划生成器。
 * <p>
 * 比较当前属性与上一次同步的属性，生成增量同步计划，
 * 确保移除已失效的修饰器并注册新增的修饰器。
 * 属于 title 模块属性桥接层。
 */
public final class TitleMythicLibSyncPlanner {

    private TitleMythicLibSyncPlanner() {
    }

    /**
     * 生成增量同步计划。
     *
     * @param configuration          MythicLib 桥接配置
     * @param displayAttributes      当前展示属性映射
     * @param collectionAttributes   当前收集属性映射
     * @param previousDisplayStats   上一次同步的展示属性键集合
     * @param previousCollectionStats 上一次同步的收集属性键集合
     * @return 同步计划
     */
    public static TitleMythicLibSyncPlan plan(
        TitleMythicLibConfiguration configuration,
        Map<String, Double> displayAttributes,
        Map<String, Double> collectionAttributes,
        Set<String> previousDisplayStats,
        Set<String> previousCollectionStats
    ) {
        List<TitleMythicLibModifierSpec> displayModifiers = buildModifiers(displayAttributes, true, configuration);
        List<TitleMythicLibModifierSpec> collectionModifiers = buildModifiers(collectionAttributes, false, configuration);

        LinkedHashSet<String> displayStats = new LinkedHashSet<>(displayAttributes.keySet());
        LinkedHashSet<String> collectionStats = new LinkedHashSet<>(collectionAttributes.keySet());

        LinkedHashSet<String> displayTouchedStats = new LinkedHashSet<>(previousDisplayStats);
        displayTouchedStats.addAll(displayStats);

        LinkedHashSet<String> collectionTouchedStats = new LinkedHashSet<>(previousCollectionStats);
        collectionTouchedStats.addAll(collectionStats);

        LinkedHashSet<String> touchedStats = new LinkedHashSet<>(displayTouchedStats);
        touchedStats.addAll(collectionTouchedStats);

        return new TitleMythicLibSyncPlan(
            displayModifiers,
            collectionModifiers,
            displayStats,
            collectionStats,
            displayTouchedStats,
            collectionTouchedStats,
            touchedStats
        );
    }

    private static List<TitleMythicLibModifierSpec> buildModifiers(
        Map<String, Double> values,
        boolean display,
        TitleMythicLibConfiguration configuration
    ) {
        List<TitleMythicLibModifierSpec> modifiers = new ArrayList<>();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            String statId = entry.getKey();
            modifiers.add(new TitleMythicLibModifierSpec(
                display ? configuration.displayModifierName(statId) : configuration.collectionModifierName(statId),
                statId,
                entry.getValue()
            ));
        }
        return modifiers;
    }
}
