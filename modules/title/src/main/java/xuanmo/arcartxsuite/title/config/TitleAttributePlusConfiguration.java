package xuanmo.arcartxsuite.title.config;

public record TitleAttributePlusConfiguration(
    boolean enabled,
    String sourcePrefix
) {
    public TitleAttributePlusConfiguration {
        sourcePrefix = sourcePrefix == null || sourcePrefix.isBlank() ? "AXS_TITLE" : sourcePrefix.trim();
    }

    public String displaySourceName() {
        return sourcePrefix + "_DISPLAY";
    }

    public String collectionSourceName() {
        return sourcePrefix + "_COLLECTION";
    }
}
