package xuanmo.arcartxsuite.title.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xuanmo.arcartxsuite.title.config.TitleDefinition;

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
    List<String> setBonusAttributeSourceLines
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
