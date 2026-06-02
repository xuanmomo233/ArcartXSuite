package xuanmo.arcartxsuite.title.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TitleDefinition(
    String id,
    boolean enabled,
    String groupId,
    String qualityId,
    TitleKind kind,
    String displayName,
    String chatPrefix,
    String chatSuffix,
    String tabPrefix,
    String tabSuffix,
    String description,
    String source,
    Map<String, Double> displayAttributes,
    Map<String, Double> collectionAttributes,
    List<String> displayAttributeLines,
    List<String> collectionAttributeLines,
    int sortOrder,
    OverheadMode overheadMode,
    String overheadTexture,
    int overheadWidth,
    int overheadHeight,
    double overheadOffsetY,
    String overheadPrefix,
    String overheadSuffix
) {
    public TitleDefinition {
        displayAttributes = immutableCopy(displayAttributes);
        collectionAttributes = immutableCopy(collectionAttributes);
        displayAttributeLines = immutableListCopy(displayAttributeLines);
        collectionAttributeLines = immutableListCopy(collectionAttributeLines);
    }

    public enum OverheadMode {
        TEXTURE, TEXT, NONE;
        public static OverheadMode parse(String value) {
            if (value == null || value.isBlank()) return NONE;
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "texture" -> TEXTURE;
                case "text" -> TEXT;
                default -> NONE;
            };
        }
    }

    private static Map<String, Double> immutableCopy(Map<String, Double> values) {
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
