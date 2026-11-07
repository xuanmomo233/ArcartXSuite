package xuanmo.arcartxsuite.qqbot.config;

/**
 * SnowLuma 进程/容器管理配置。
 * 支持三种运行模式：
 *   - external：只连接外部 OneBot 服务，不安装、启动或停止服务（默认）
 *   - native：本地子进程（Windows / Linux 均适用）
 *   - docker：Docker 容器模式（推荐 Linux VPS 使用）
 *
 * @param webUiPassword    WebUI 初始密码（可选，透传 SNOWLUMA_WEBUI_BOOTSTRAP_PASSWORD，
 *                         仅 SnowLuma 首次初始化时生效；留空则自动生成随机临时密码）
 * @param acceptAgreements 自动同意 SnowLuma EULA 与隐私协议（透传 SNOWLUMA_ACCEPT_EULA /
 *                         SNOWLUMA_ACCEPT_PRIVACY），首次打开 WebUI 不再显示协议确认页
 * @param dockerNovncPort  noVNC 网页远程桌面端口（docker 模式下浏览器操作 QQ 客户端登录）
 * @param dockerVncPassword VNC/noVNC 密码（非回环绑定时必须显式提供）
 * @param dockerBindAddress Docker 发布端口绑定的 IP 字面量，默认 127.0.0.1
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
    boolean dockerAutoInstall,
    String dockerBindAddress
) {
    /** Existing callers retain loopback-only publishing. */
    public QQBotSnowLumaConfig(String mode, String dir, boolean autoStart, String webUiPassword,
                              boolean acceptAgreements, String dockerContainerName, String dockerImage,
                              int dockerWebUiPort, int dockerWsPort, int dockerHttpPort, int dockerNovncPort,
                              String dockerVncPassword, boolean dockerAutoInstall) {
        this(mode, dir, autoStart, webUiPassword, acceptAgreements, dockerContainerName, dockerImage,
            dockerWebUiPort, dockerWsPort, dockerHttpPort, dockerNovncPort, dockerVncPassword,
            dockerAutoInstall, "127.0.0.1");
    }

    public QQBotSnowLumaConfig {
        mode = mode == null ? "external" : mode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("external", "native", "docker").contains(mode)) {
            throw new IllegalArgumentException("onebot.snowluma.mode 必须是 external、native 或 docker");
        }
        dockerBindAddress = literalAddress(dockerBindAddress == null ? "127.0.0.1" : dockerBindAddress);
    }

    public boolean isManaged() {
        return !"external".equals(mode);
    }

    public boolean isDocker() {
        return "docker".equals(mode);
    }

    public boolean isNative() {
        return "native".equals(mode);
    }

    public boolean dockerLoopbackBinding() {
        try { return java.net.InetAddress.getByName(dockerBindAddress).isLoopbackAddress(); }
        catch (java.net.UnknownHostException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String literalAddress(String value) {
        // Validate characters before InetAddress: names and scoped addresses must never trigger DNS.
        boolean ipv4 = value.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");
        boolean ipv6 = value.contains(":") && value.matches("[0-9a-fA-F:.]+");
        if (ipv4) {
            for (String part : value.split("\\.")) {
                if (Integer.parseInt(part) > 255) throw invalidAddress();
            }
        } else if (!ipv6) throw invalidAddress();
        try { return java.net.InetAddress.getByName(value).getHostAddress(); }
        catch (java.net.UnknownHostException error) { throw invalidAddress(); }
    }

    private static IllegalArgumentException invalidAddress() {
        return new IllegalArgumentException("onebot.snowluma.docker.bind-address 必须是 IPv4/IPv6 字面量，不能是主机名、带 zone 的地址或命令文本");
    }
}
