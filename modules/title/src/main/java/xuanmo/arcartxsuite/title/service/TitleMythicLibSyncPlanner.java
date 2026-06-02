package xuanmo.arcartxsuite.title.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xuanmo.arcartxsuite.title.config.TitleMythicLibConfiguration;

public final class TitleMythicLibSyncPlanner {

    private TitleMythicLibSyncPlanner() {
    }

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
