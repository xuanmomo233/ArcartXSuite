package xuanmo.arcartxsuite.title.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
