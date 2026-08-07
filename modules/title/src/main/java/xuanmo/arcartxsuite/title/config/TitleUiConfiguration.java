package xuanmo.arcartxsuite.title.config;

/**
 * 称号模块 UI 配置。
 * <p>
 * 定义称号菜单的 UI ID、是否自动注册、属性行颜色前缀与空属性占位符。
 *
 * @param uiId                    称号菜单 UI ID
 * @param registerUiOnEnable      启用时是否自动注册 UI
 * @param attributeLineColorPrefix 属性行颜色前缀
 * @param emptyAttributePlaceholder 空属性占位文本
 */
public record TitleUiConfiguration(
    String uiId,
    boolean registerUiOnEnable,
    String attributeLineColorPrefix,
    String emptyAttributePlaceholder
) {
}
