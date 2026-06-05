package xuanmo.arcartxsuite.title.config;

public record TitleSymphonyConfiguration(
    String sourcePrefix
) {
    public TitleSymphonyConfiguration {
        sourcePrefix = sourcePrefix == null || sourcePrefix.isBlank() ? "AXS_TITLE" : sourcePrefix.trim();
    }

    public String displaySourceName() {
        return sourcePrefix + "_DISPLAY";
    }

    public String collectionSourceName() {
        return sourcePrefix + "_COLLECTION";
    }
}
