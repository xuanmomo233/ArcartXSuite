package xuanmo.arcartxsuite.title.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MythicLib 属性同步计划。
 * <p>
 * 由 {@link TitleMythicLibSyncPlanner} 生成，描述本次同步需要注册的修饰器列表、
 * 当前属性键集合、以及需要触及（移除旧修饰器并更新）的属性键集合。
 *
 * @param displayModifiers       展示属性修饰器列表
 * @param collectionModifiers    收集属性修饰器列表
 * @param displayStats           当前展示属性键集合
 * @param collectionStats        当前收集属性键集合
 * @param displayTouchedStats    需要触及的展示属性键集合（含上一次同步的属性键）
 * @param collectionTouchedStats 需要触及的收集属性键集合（含上一次同步的属性键）
 * @param touchedStats           全部需要触及的属性键集合
 */
public record TitleMythicLibSyncPlan(
    List<TitleMythicLibModifierSpec> displayModifiers,
    List<TitleMythicLibModifierSpec> collectionModifiers,
    Set<String> displayStats,
    Set<String> collectionStats,
    Set<String> displayTouchedStats,
    Set<String> collectionTouchedStats,
    Set<String> touchedStats
) {
    public TitleMythicLibSyncPlan {
        displayModifiers = List.copyOf(displayModifiers);
        collectionModifiers = List.copyOf(collectionModifiers);
        displayStats = immutableCopy(displayStats);
        collectionStats = immutableCopy(collectionStats);
        displayTouchedStats = immutableCopy(displayTouchedStats);
        collectionTouchedStats = immutableCopy(collectionTouchedStats);
        touchedStats = immutableCopy(touchedStats);
    }

    private static Set<String> immutableCopy(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}
