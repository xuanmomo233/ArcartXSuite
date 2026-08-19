package xuanmo.arcartxsuite.qqbot.config;

/**
 * QQBot 模块统一 UI 配置（从原 bind / notify / admin 嵌套节抽离为扁平 ui 节）。
 *
 * <p>UI 文件名（qqbot_bind.yml 等）为代码常量（{@code QQBotUiService.BIND_UI_FILE} 等），
 * 不再从配置读取，因此本 record 不包含 ui-file 字段。
 *
 * @param bindUiId          绑定中心 UI ID
 * @param notifyUiId        消息通知 HUD UI ID
 * @param adminUiId         管理后台 UI ID
 * @param notifyEnabled     是否启用消息通知 HUD（屏幕右上角弹出新消息）
 * @param registerUiOnEnable 模块启动时是否自动注册 UI
 * @param overwriteUiFiles  是否强制覆盖已存在的 UI 文件
 */
public record QQBotUiConfig(
    String bindUiId,
    String notifyUiId,
    String adminUiId,
    boolean notifyEnabled,
    boolean registerUiOnEnable,
    boolean overwriteUiFiles
) {
}
