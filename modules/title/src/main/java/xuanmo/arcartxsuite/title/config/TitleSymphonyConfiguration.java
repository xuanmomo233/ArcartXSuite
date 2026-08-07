package xuanmo.arcartxsuite.title.config;

/**
 * Symphony 属性桥接配置。
 * <p>
 * 定义称号在 Symphony 中使用的属性源名称前缀，
 * 区分展示属性源与收集属性源。
 *
 * @param sourcePrefix 属性源名称前缀，为空时默认 {@code "AXS_TITLE"}
 */
public record TitleSymphonyConfiguration(
    String sourcePrefix
) {
    public TitleSymphonyConfiguration {
        sourcePrefix = sourcePrefix == null || sourcePrefix.isBlank() ? "AXS_TITLE" : sourcePrefix.trim();
    }

    /**
     * @return 展示属性源名称（前缀 + {@code _DISPLAY}）
     */
    public String displaySourceName() {
        return sourcePrefix + "_DISPLAY";
    }

    /**
     * @return 收集属性源名称（前缀 + {@code _COLLECTION}）
     */
    public String collectionSourceName() {
        return sourcePrefix + "_COLLECTION";
    }
}
