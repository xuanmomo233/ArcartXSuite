package xuanmo.arcartxsuite.title.config;

/**
 * 称号品质定义。
 * <p>
 * 用于称号的分级展示与排序。
 *
 * @param id        品质唯一标识
 * @param name      品质显示名称
 * @param sortOrder 排序权重
 */
public record TitleQualityDefinition(
    String id,
    String name,
    int sortOrder
) {
}
