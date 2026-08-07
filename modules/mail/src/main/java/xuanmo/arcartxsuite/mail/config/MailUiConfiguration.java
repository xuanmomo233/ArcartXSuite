package xuanmo.arcartxsuite.mail.config;

/**
 * 邮件 UI 配置，定义收件箱/写信/日志/管理界面的 UI ID、注册策略与通知卡片渲染参数。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code ui} 节解析。
 *
 * @param inboxUiId            收件箱 UI ID
 * @param composeUiId          写信 UI ID
 * @param logsUiId             日志 UI ID
 * @param adminUiId            管理 UI ID
 * @param registerUiOnEnable   启用时是否自动注册 UI
 * @param overwriteUiFiles      是否覆盖用户已修改的 UI 文件
 * @param composeInventoryTitle 写信界面物品栏标题
 * @param notifyCardId          通知卡片 ID
 * @param charWidthFull         全角字符宽度（像素）
 * @param charWidthHalf         半角字符宽度（像素）
 * @param lineHeight            行高（像素）
 * @param maxLineWidth          最大行宽（像素）
 * @param textOffsetX           文本 X 轴偏移
 * @param padRight              右侧内边距
 * @param baseHeight            基础高度
 * @param minWidth              最小宽度
 */
public record MailUiConfiguration(
    String inboxUiId,
    String composeUiId,
    String logsUiId,
    String adminUiId,
    boolean registerUiOnEnable,
    boolean overwriteUiFiles,
    String composeInventoryTitle,
    String notifyCardId,
    int charWidthFull,
    int charWidthHalf,
    int lineHeight,
    int maxLineWidth,
    int textOffsetX,
    int padRight,
    int baseHeight,
    int minWidth
) {
}
