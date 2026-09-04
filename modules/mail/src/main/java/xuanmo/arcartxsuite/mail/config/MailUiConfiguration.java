package xuanmo.arcartxsuite.mail.config;

/**
 * 邮件 UI 配置，定义收件箱/写信/日志/管理界面的 UI ID、注册策略与通知卡片换行参数。
 * <p>
 * 关联模块：mail。由 {@link MailModuleConfiguration#load} 从 {@code ui} 节解析。
 * 卡片的宽度与高度由 UI 侧根据 Text 控件实际渲染尺寸自适应，Java 不再计算。
 *
 * @param inboxUiId            收件箱 UI ID
 * @param composeUiId          写信 UI ID
 * @param logsUiId             日志 UI ID
 * @param adminUiId            管理 UI ID
 * @param registerUiOnEnable   启用时是否自动注册 UI
 * @param overwriteUiFiles      是否覆盖用户已修改的 UI 文件
 * @param composeInventoryTitle 写信界面物品栏标题
 * @param notifyCardId          通知卡片 ID
 * @param charWidthFull         全角字符宽度（像素），用于换行测算
 * @param charWidthHalf         半角字符宽度（像素），用于换行测算
 * @param maxLineWidth          最大行宽（像素），超出则自动换行
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
    int maxLineWidth
) {
}
