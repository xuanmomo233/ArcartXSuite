package xuanmo.arcartxsuite.title.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public int effectiveThreshold() {
        return completionThreshold <= 0 ? requiredTitleIds.size() : Math.min(completionThreshold, requiredTitleIds.size());
    }
}
