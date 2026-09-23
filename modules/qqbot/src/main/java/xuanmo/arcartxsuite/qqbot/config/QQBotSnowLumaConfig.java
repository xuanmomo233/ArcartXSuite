package xuanmo.arcartxsuite.qqbot.config;

/**
 * SnowLuma 进程/容器管理配置。
 * 支持两种运行模式：
 *   - native：本地子进程（Windows / Linux 均适用）
 *   - docker：Docker 容器模式（推荐 Linux VPS 使用）
 *
 * @param webUiPassword    WebUI 初始密码（可选，透传 SNOWLUMA_WEBUI_BOOTSTRAP_PASSWORD，
 *                         仅 SnowLuma 首次初始化时生效；留空则自动生成随机临时密码）
 * @param acceptAgreements 自动同意 SnowLuma EULA 与隐私协议（透传 SNOWLUMA_ACCEPT_EULA /
 *                         SNOWLUMA_ACCEPT_PRIVACY），首次打开 WebUI 不再显示协议确认页
 * @param dockerNovncPort  noVNC 网页远程桌面端口（docker 模式下浏览器操作 QQ 客户端登录）
 * @param dockerVncPassword VNC/noVNC 访问密码（可选，透传 VNC_PASSWD；公网暴露时建议设置）
 */
public record QQBotSnowLumaConfig(
    String mode,
    String dir,
    boolean autoStart,
    String webUiPassword,
    boolean acceptAgreements,
    String dockerContainerName,
    String dockerImage,
    int dockerWebUiPort,
    int dockerWsPort,
    int dockerHttpPort,
    int dockerNovncPort,
    String dockerVncPassword,
    boolean dockerAutoInstall
) {
    public boolean isDocker() {
        return "docker".equalsIgnoreCase(mode);
    }

    public boolean isNative() {
        return !isDocker();
    }
}
