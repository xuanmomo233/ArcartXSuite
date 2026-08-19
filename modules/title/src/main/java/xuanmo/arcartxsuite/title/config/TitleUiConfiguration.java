package xuanmo.arcartxsuite.title.config;

/**
 * 称号模块 UI 配置。
 * <p>
 * 定义称号菜单的 UI ID、是否自动注册、是否覆盖 UI 文件、属性行颜色前缀与空属性占位符。
 *
 * @param uiId                    称号菜单 UI ID
 * @param registerUiOnEnable      启用时是否自动注册 UI
 * @param overwriteUiFiles        启用时是否覆盖用户已修改的 UI 文件
 * @param attributeLineColorPrefix 属性行颜色前缀
 * @param emptyAttributePlaceholder 空属性占位文本
 */
public record TitleUiConfiguration(
    String uiId,
    boolean registerUiOnEnable,
    boolean overwriteUiFiles,
    String attributeLineColorPrefix,
    String emptyAttributePlaceholder
) {
}
