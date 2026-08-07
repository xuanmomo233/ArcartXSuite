package xuanmo.arcartxsuite.title.config;

/**
 * 称号分组定义。
 * <p>
 * 每个分组控制一组称号的装备槽位，同一分组只能装备一个称号。
 *
 * @param id          分组唯一标识
 * @param name        分组显示名称
 * @param sortOrder   排序权重
 * @param permission  访问该分组所需权限，为空表示无限制
 */
public record TitleGroupDefinition(
    String id,
    String name,
    int sortOrder,
    String permission
) {
}
