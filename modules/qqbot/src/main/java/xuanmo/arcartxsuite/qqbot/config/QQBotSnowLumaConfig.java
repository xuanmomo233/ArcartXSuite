package xuanmo.arcartxsuite.qqbot.config;

/**
 * SnowLuma 进程/容器管理配置。
 * 支持两种运行模式：
 *   - native：本地子进程（Windows / Linux 均适用）
 *   - docker：Docker 容器模式（推荐 Linux VPS 使用）
 */
public record QQBotSnowLumaConfig(
    String mode,
    String dir,
    boolean autoStart,
    String dockerContainerName,
    String dockerImage,
    int dockerWebUiPort,
    int dockerWsPort,
    int dockerHttpPort,
    boolean dockerAutoInstall
) {
    public boolean isDocker() {
        return "docker".equalsIgnoreCase(mode);
    }

    public boolean isNative() {
        return !isDocker();
    }
}
