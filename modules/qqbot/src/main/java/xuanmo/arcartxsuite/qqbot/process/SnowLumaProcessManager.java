package xuanmo.arcartxsuite.qqbot.process;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.qqbot.config.QQBotSnowLumaConfig;

public final class SnowLumaProcessManager {
    public enum Status { STOPPED, STARTING, RUNNING, INSTALLING }
    private static final String GITHUB_API = "https://api.github.com/repos/SnowLuma/SnowLuma/releases/latest";
    // SnowLuma 首次启动在 stdout 打印一次初始凭据：
    //   "initial credentials: user=admin password=<pwd>"（英文版）
    //   "临时密码: <pwd>"（中文/旧版 Docker 镜像输出）
    private static final Pattern CREDENTIALS_EN =
        Pattern.compile("initial\\s+credentials.*password\\s*=\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDENTIALS_CN =
        Pattern.compile("临时密码\\s*[:：]?\\s*(\\S+)");
    // 子进程输出里的告警行识别：词边界匹配，避免 "errorlevel"（bat 回显）等误报。
    private static final Pattern ERROR_LINE =
        Pattern.compile("\\b(error|warn|warning|fail|failed|fatal|exception|eaddrinuse|denied)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS_IN_USE =
        Pattern.compile("eaddrinuse|address already in use", Pattern.CASE_INSENSITIVE);
    // ANSI 颜色转义序列（SnowLuma/docker 输出可能带色，先剥离再匹配与展示）
    private static final Pattern ANSI_SEQ = Pattern.compile("\\u001B\\[[;?\\d]*[a-zA-Z]");
    /** WebUI 初始密码捕获后落盘的文件名（位于 SnowLuma 安装目录） */
    private static final String PASSWORD_FILE = "webui-password.txt";
    /** native 模式 WebUI 默认端口（SnowLuma 固定，仅 docker 模式可在宿主机侧改映射端口） */
    private static final int NATIVE_WEBUI_PORT = 5099;

    private final QQBotSnowLumaConfig config;
    private final Path installDir;
    private final Logger logger;
    private final BooleanSupplier debugMode;
    // 原生模式下 SnowLuma 的 WS 端口（由 onebot.ws-url 解析得到），用于「按端口找残留」兜底；
    // 用户改了端口时也能正确清理，而不是写死 3001。
    private final AtomicReference<Status> status = new AtomicReference<>(Status.STOPPED);
    private volatile Process process;
    private Thread outputThread;
    private volatile Boolean nodeAvailable;
    private volatile String nodeVersion;
    private volatile Thread shutdownHook;
    /** 本次运行从 stdout 捕获到的 WebUI 初始密码（未捕获到时为 null） */
    private volatile String webUiPassword;
    /** INSTALLING 状态下的最近一条进度描述，供 status 展示 */
    private volatile String installPhase;

    private final int nativeWsPort;

    public SnowLumaProcessManager(Path serverRoot, QQBotSnowLumaConfig config, Logger logger, BooleanSupplier debugMode, int nativeWsPort) {
        this.config = config;
        this.installDir = serverRoot.resolve(config.dir());
        this.logger = logger;
        this.debugMode = debugMode;
        this.nativeWsPort = nativeWsPort > 0 ? nativeWsPort : 3001;
    }

    public void init() {
        if (config.isDocker()) {
            if (!config.autoStart() && !config.dockerAutoInstall()) return;
            registerShutdownHook();
            if (isInstalled()) {
                if (config.autoStart()) logger.info("[SnowLuma] " + startDocker());
                return;
            }
            if (config.dockerAutoInstall()) {
                logger.info("[SnowLuma] Docker: 未检测到容器 '" + config.dockerContainerName()
                    + "'，auto-install 已启用，开始自动部署（拉取镜像可能需要几分钟）...");
                String result = installDockerAsync(null).join();
                for (String line : result.split("\n")) logger.info("[SnowLuma] auto-install: " + line);
                // docker run -d 创建即运行，无需再单独 start
                return;
            }
            logger.warning("[SnowLuma] Docker: 未检测到容器 '" + config.dockerContainerName() + "'。"
                + "执行 /axs qqbot snowluma install 自动部署，"
                + "或在配置中开启 onebot.snowluma.docker.auto-install 让模块自动安装。");
            return;
        }
        // native 模式
        if (!config.autoStart()) return;
        registerShutdownHook();
        if (!isInstalled()) {
            logger.warning("[SnowLuma] 未检测到安装目录 " + installDir
                + "，auto-start 无法拉起。执行 /axs qqbot snowluma install 可自动下载安装。");
            return;
        }
        String startResult = startNative();
        if (startResult.startsWith("启动失败") || startResult.contains("未安装")) {
            logger.warning("[SnowLuma] " + startResult);
        } else {
            logger.info("[SnowLuma] " + startResult);
        }
    }

    private boolean checkNodeEnvironment() {
        return checkNodeEnvironment(false);
    }

    private boolean checkNodeEnvironment(boolean force) {
        if (config.isDocker()) return true;
        // 完整版安装包自带 node 运行时（安装目录下的 node.exe / node），无需系统 Node。
        Path bundled = bundledNodeBin();
        if (bundled != null) {
            nodeAvailable = true;
            nodeVersion = "内置运行时";
            return true;
        }
        if (!force && nodeAvailable != null) return nodeAvailable;
        nodeAvailable = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (ok && p.exitValue() == 0) {
                String v = readAll(p.getInputStream()).trim();
                if (isSupportedNodeVersion(v)) {
                    nodeVersion = v;
                    nodeAvailable = true;
                    return true;
                }
                if (!v.isEmpty()) {
                    nodeVersion = v + "（版本过低）";
                    logger.warning("[SnowLuma] Node.js 版本不满足要求: " + v
                        + "（需要 22.13+ 或 23.4+）");
                }
            }
        } catch (Exception e) {
            if (debugMode.getAsBoolean()) logger.info("[SnowLuma] Node.js 检测异常: " + e.getMessage());
        }
        nodeAvailable = false;
        return false;
    }

    /**
     * SnowLuma 要求的 Node 版本（见官方 check-node-version.cjs）：^22.13.0 || >=23.4.0
     */
    private static boolean isSupportedNodeVersion(String version) {
        Matcher m = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)").matcher(version.trim());
        if (!m.find()) return false;
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        if (major == 22) return minor >= 13;
        if (major == 23) return minor >= 4;
        return major > 23;
    }

    /** 安装包内自带的 Node 可执行文件（完整版才有），不存在返回 null */
    private @Nullable Path bundledNodeBin() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path bin = installDir.resolve(os.contains("win") ? "node.exe" : "node");
        return Files.isRegularFile(bin) ? bin : null;
    }

    public Status getStatus() {
        if (config.isDocker()) return dockerContainerRunning() ? Status.RUNNING : Status.STOPPED;
        if (status.get() == Status.RUNNING && (process == null || !process.isAlive())) status.set(Status.STOPPED);
        return status.get();
    }

    public boolean isInstalled() {
        if (config.isDocker()) return dockerContainerExists();
        return Files.exists(getLauncher());
    }

    public String start() { return config.isDocker() ? startDocker() : startNative(); }
    public String stop() { return config.isDocker() ? stopDocker() : stopNative(); }
    public void shutdown() {
        unregisterShutdownHook();
        if (config.isDocker()) { if (dockerContainerRunning()) stopDocker(); return; }
        if (process != null && process.isAlive()) stopNative();
        else { String c = killResidue(); if (c != null) logger.info("[SnowLuma] 卸载清理残留: " + c); }
    }

    private void registerShutdownHook() {
        if (shutdownHook != null) return;
        Thread hook = new Thread(() -> { try { shutdown(); } catch (Throwable ignored) {} }, "AXS-SnowLuma-ShutdownHook");
        try { Runtime.getRuntime().addShutdownHook(hook); shutdownHook = hook; }
        catch (IllegalStateException ignored) { /* JVM 已在关闭中，无需注册 */ }
    }

    private void unregisterShutdownHook() {
        Thread hook = shutdownHook;
        if (hook == null) return;
        shutdownHook = null;
        // 若是关闭钩子自身触发的 shutdown()，此时 JVM 正在关闭，removeShutdownHook 会抛 IllegalStateException，忽略即可。
        try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException ignored) {}
    }

    public CompletableFuture<String> installAsync(@Nullable Consumer<String> progress) {
        return config.isDocker() ? installDockerAsync(progress) : installNativeAsync(progress);
    }
    public String logs() { return config.isDocker() ? dockerLogs() : "仅 Docker 模式支持日志查看（native 模式日志直接输出到服务器控制台）"; }
    public String statusReport() { return config.isDocker() ? statusReportDocker() : statusReportNative(); }

    /**
     * WebUI 地址（宿主侧访问地址，写死 127.0.0.1 + 端口）。
     */
    public String webUiUrl() {
        return "http://127.0.0.1:" + (config.isDocker() ? config.dockerWebUiPort() : NATIVE_WEBUI_PORT);
    }

    /**
     * 查看 WebUI 登录密码（admin 账号）。
     * native：优先返回本次运行捕获的临时密码，其次读安装目录下保存的 webui-password.txt；
     * docker：从 docker logs 中提取最后一次输出的临时密码。
     */
    public String webUiPasswordReport() {
        if (config.isDocker()) return dockerWebUiPasswordReport();
        String pwd = webUiPassword;
        if (pwd == null) pwd = readSavedPassword();
        StringBuilder sb = new StringBuilder("§6[SnowLuma WebUI 登录信息]\n");
        sb.append("§7  地址: §f").append(webUiUrl()).append("\n");
        sb.append("§7  账号: §fadmin\n");
        if (pwd != null) {
            sb.append("§7  临时密码: §f").append(pwd).append("\n");
            sb.append("§7  首次登录后请立即修改；若跳过改密，SnowLuma 下次启动会重新生成（本信息随之更新）。");
            return sb.toString();
        }
        if (config.webUiPassword() != null && !config.webUiPassword().isBlank()) {
            sb.append("§7  密码: §f已在配置 onebot.snowluma.webui-password 中指定\n");
            sb.append("§7  （仅 SnowLuma 首次初始化时生效；若已初始化过，请使用你自己修改后的密码）");
            return sb.toString();
        }
        sb.append("§7  未捕获到临时密码，可能原因:\n");
        sb.append("§7  • SnowLuma 尚未首次启动 —— 执行 /axs qqbot snowluma start 后密码会打印在控制台\n");
        sb.append("§7  • 已在 WebUI 修改过密码 —— 请使用修改后的密码登录\n");
        sb.append("§7  • 忘记密码：停止 SnowLuma，删除 ")
            .append(installDir.resolve("config").resolve("webui.json"))
            .append(" 后重新启动将生成新临时密码");
        return sb.toString();
    }

    /**
     * 生成「实现端未就绪」提示（用于 OneBot 断开告警末尾追加）。
     * 仅在 ws-url 指向本机且 SnowLuma 未运行/未安装时返回非 null。
     */
    public @Nullable String notReadyHint(boolean localWs) {
        if (!localWs) return null;
        if (getStatus() == Status.RUNNING) return null;
        if (!isInstalled()) {
            return "（提示: 本机 SnowLuma 未安装，执行 /axs qqbot snowluma install 可一键部署内置实现端）";
        }
        return "（提示: SnowLuma 已安装但未运行，执行 /axs qqbot snowluma start 启动）";
    }

    // --- Docker ---
    private String startDocker() {
        if (dockerContainerRunning()) return "SnowLuma Docker 容器已在运行中";
        if (!dockerContainerExists()) return "Docker 容器 '" + config.dockerContainerName() + "' 不存在，请先 install";
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "start", config.dockerContainerName());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (ok && p.exitValue() == 0) {
                return "SnowLuma Docker 容器已启动，WebUI: " + webUiUrl()
                    + "（账号 admin，临时密码执行 /axs qqbot snowluma password 获取）";
            }
            return "启动失败: " + readAll(p.getInputStream());
        } catch (Exception e) { logger.log(Level.SEVERE, "[SnowLuma] Docker start", e); return "启动失败: " + e.getMessage(); }
    }

    private String stopDocker() {
        if (!dockerContainerExists()) return "Docker 容器不存在";
        if (!dockerContainerRunning()) return "SnowLuma Docker 容器未在运行";
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "stop", config.dockerContainerName());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (ok && p.exitValue() == 0) return "SnowLuma Docker 容器已停止";
            return "停止失败: " + readAll(p.getInputStream());
        } catch (Exception e) { logger.log(Level.SEVERE, "[SnowLuma] Docker stop", e); return "停止失败: " + e.getMessage(); }
    }

    private CompletableFuture<String> installDockerAsync(@Nullable Consumer<String> progress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                report(progress, "docker pull " + config.dockerImage() + "（拉取镜像可能需要几分钟）...");
                ProcessBuilder pull = new ProcessBuilder("docker", "pull", config.dockerImage());
                pull.redirectErrorStream(true);
                Process pp = pull.start();
                // 拉取进度在独立线程中泵出，主线程仅做超时等待（镜像较大，放宽到 10 分钟）
                AtomicReference<String> tailRef = new AtomicReference<>("");
                Thread pump = new Thread(() -> {
                    try { tailRef.set(pumpProgress(pp.getInputStream(), progress, "docker pull")); }
                    catch (IOException ignored) {}
                }, "AXS-SnowLuma-DockerPull");
                pump.setDaemon(true);
                pump.start();
                if (!pp.waitFor(600, java.util.concurrent.TimeUnit.SECONDS)) {
                    pp.destroyForcibly();
                    return "镜像拉取超时（10 分钟），请检查网络后重试";
                }
                pump.join(3000);
                if (pp.exitValue() != 0)
                    return "镜像拉取失败: " + tailRef.get();
                if (dockerContainerExists()) return "容器已存在，执行 start 启动";
                ProcessBuilder run = buildDockerRun();
                run.redirectErrorStream(true);
                Process pr = run.start();
                if (!pr.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || pr.exitValue() != 0)
                    return "容器创建失败: " + readAll(pr.getInputStream());
                return "Docker 容器已创建并启动。下一步:\n"
                    + "1. 浏览器打开 noVNC http://<服务器IP>:" + config.dockerNovncPort() + " 操作 QQ 客户端扫码登录\n"
                    + "2. WebUI 管理面板: " + webUiUrl() + "（账号 admin，临时密码执行 /axs qqbot snowluma password 获取）\n"
                    + "3. QQ 登录成功后模块会自动连接 OneBot";
            } catch (Exception e) { logger.log(Level.SEVERE, "[SnowLuma] Docker install", e); return "安装失败: " + e.getMessage(); }
        });
    }

    private ProcessBuilder buildDockerRun() {
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
            "docker", "run", "-d",
            "--name", config.dockerContainerName(), "--restart", "unless-stopped", "--shm-size=1g",
            "--cap-add=SYS_PTRACE", "--security-opt", "seccomp=unconfined",
            "-p", config.dockerWebUiPort() + ":5099",
            "-p", config.dockerHttpPort() + ":3000",
            "-p", config.dockerWsPort() + ":3001",
            "-p", config.dockerNovncPort() + ":6081",
            "-v", config.dockerContainerName() + "-data:/app/data",
            "-v", config.dockerContainerName() + "-qq-config:/app/.config",
            "-v", config.dockerContainerName() + "-qq-data:/app/.local/share"
        ));
        if (config.dockerVncPassword() != null && !config.dockerVncPassword().isBlank()) {
            cmd.add("-e");
            cmd.add("VNC_PASSWD=" + config.dockerVncPassword());
        }
        if (config.webUiPassword() != null && !config.webUiPassword().isBlank()) {
            cmd.add("-e");
            cmd.add("SNOWLUMA_WEBUI_BOOTSTRAP_PASSWORD=" + config.webUiPassword());
        }
        if (config.acceptAgreements()) {
            cmd.add("-e");
            cmd.add("SNOWLUMA_ACCEPT_EULA=1");
            cmd.add("-e");
            cmd.add("SNOWLUMA_ACCEPT_PRIVACY=1");
        }
        cmd.add(config.dockerImage());
        return new ProcessBuilder(cmd);
    }

    private String dockerLogs() {
        if (!dockerContainerExists()) return "容器不存在";
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "logs", "--tail", "50", config.dockerContainerName());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            String out = stripAnsi(readAll(p.getInputStream()));
            if (!ok) out += "\n(读取超时)";
            return out.isBlank() ? "(无日志)" : out;
        } catch (Exception e) { return "读取日志失败: " + e.getMessage(); }
    }

    /**
     * docker logs 全文里检索最后一次输出的 WebUI 临时密码。
     * 临时密码只会在全新数据卷的首次启动时打印；未改密重启会重新生成并再次打印。
     */
    private String dockerWebUiPasswordReport() {
        if (!dockerContainerExists()) return "Docker 容器不存在，请先执行 /axs qqbot snowluma install";
        StringBuilder sb = new StringBuilder("§6[SnowLuma WebUI 登录信息]\n");
        sb.append("§7  地址: §f").append(webUiUrl()).append("\n");
        sb.append("§7  账号: §fadmin\n");
        String pwd = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "logs", config.dockerContainerName());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String found = extractPassword(stripAnsi(line));
                    if (found != null) pwd = found; // 取最后一次出现（未改密重启会重新生成）
                }
            }
            p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            sb.append("§7  读取容器日志失败: ").append(e.getMessage());
            return sb.toString();
        }
        if (pwd != null) {
            sb.append("§7  临时密码: §f").append(pwd).append("\n");
            sb.append("§7  首次登录后请立即修改；若跳过改密，容器下次启动会重新生成。");
        } else if (config.webUiPassword() != null && !config.webUiPassword().isBlank()) {
            sb.append("§7  密码: §f已在配置 onebot.snowluma.webui-password 中指定（仅首次初始化生效）");
        } else {
            sb.append("§7  容器日志中未找到临时密码。若已在 WebUI 修改过密码，请使用修改后的密码；\n");
            sb.append("§7  忘记密码可删除容器后重新 install（数据卷会重建，将生成新临时密码）。");
        }
        return sb.toString();
    }

    private String statusReportDocker() {
        boolean exists = dockerContainerExists(), running = dockerContainerRunning();
        String localVer = getLocalVersion();
        StringBuilder sb = new StringBuilder("§6[SnowLuma Docker 状态]\n");
        sb.append("§7  容器名: §f").append(config.dockerContainerName()).append("\n");
        sb.append("§7  镜像: §f").append(config.dockerImage()).append("\n");
        sb.append("§7  本地版本: §f").append(localVer != null ? localVer : "未知").append("\n");
        sb.append("§7  容器存在: §f").append(exists ? "是" : "否").append("\n");
        sb.append("§7  运行状态: §f").append(running ? "运行中" : "已停止").append("\n");
        if (running) {
            sb.append("§7  WebUI: §f").append(webUiUrl()).append(" §7(账号 admin)\n");
            sb.append("§7  noVNC: §fhttp://<服务器IP>:").append(config.dockerNovncPort()).append("\n");
            sb.append("§7  WS端口: §f").append(config.dockerWsPort()).append("\n");
        }
        if (!exists) {
            sb.append("§7  提示: §f未安装，执行 /axs qqbot snowluma install 自动部署\n");
        }
        sb.append("§7  自动启动: §f").append(config.autoStart() ? "是" : "否");
        return sb.toString();
    }

    private boolean dockerContainerExists() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker","ps","-a","--filter","name="+config.dockerContainerName(),"--format","{{.Names}}");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) return false;
            return readAll(p.getInputStream()).trim().contains(config.dockerContainerName());
        } catch (Exception e) { return false; }
    }

    private boolean dockerContainerRunning() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker","inspect","--format={{.State.Running}}",config.dockerContainerName());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) return false;
            return "true".equalsIgnoreCase(readAll(p.getInputStream()).trim());
        } catch (Exception e) { return false; }
    }

    // --- Native ---
    private String startNative() {
        if (status.get()==Status.RUNNING && process!=null && process.isAlive())
            return "SnowLuma 已在运行中 (PID: "+process.pid()+")";
        if (status.get()==Status.INSTALLING) return "正在安装中，请稍后再试";
        if (!isInstalled())
            return "SnowLuma 未安装，请先执行 /axs qqbot snowluma install 下载安装";
        if (!checkNodeEnvironment(true)) {
            return "启动失败: 未检测到可用的 Node.js（SnowLuma 要求 22.13+ 或 23.4+）。\n"
                + "推荐执行 /axs qqbot snowluma install 安装完整版（自带 Node 运行时，无需额外环境）\n"
                + "或手动安装 Node.js 22.13+（https://nodejs.org），完成后执行 /axs reload qqbot 重载模块。";
        }
        String cleanup = killResidue(); if (cleanup!=null) logger.info("[SnowLuma] "+cleanup);
        status.set(Status.STARTING);
        try {
            ProcessBuilder pb = buildProcess();
            pb.directory(installDir.toFile()); pb.redirectErrorStream(true);
            final Process proc = pb.start();
            process = proc;
            final long thisPid = proc.pid();
            registerShutdownHook();
            AtomicReference<String> fatal = new AtomicReference<>();
            outputThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line=r.readLine())!=null) handleOutputLine(line, fatal);
                } catch (IOException ignored) {}
                // stdout 关闭 = 进程已退出。主动 stop 时 status 已置 STOPPED，仅记录信息级日志；
                // 仍在 RUNNING 却退出说明异常崩溃，升级为警告。
                try {
                    int code = proc.waitFor();
                    if (status.get() == Status.RUNNING) {
                        status.set(Status.STOPPED);
                        logger.warning("[SnowLuma] 进程意外退出 (code=" + code + ")，可执行 /axs qqbot snowluma start 重启");
                    } else if (debugMode.getAsBoolean()) {
                        logger.info("[SnowLuma] 进程已退出 (code=" + code + ")");
                    }
                } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }, "AXS-SnowLuma-Output");
            outputThread.setDaemon(true); outputThread.start();
            try { Thread.sleep(3000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            if (fatal.get()!=null) { forceKill(thisPid); status.set(Status.STOPPED); process=null; return "启动失败: "+fatal.get()+"\n检查是否有其他进程占用 "+NATIVE_WEBUI_PORT+"/"+nativeWsPort; }
            if (!proc.isAlive()) { status.set(Status.STOPPED); process=null; return "启动失败: 进程立即退出，详见上方 [SnowLuma] 日志"; }
            status.set(Status.RUNNING);
            logger.info("[SnowLuma] 已启动 (PID: "+thisPid+")");
            return "SnowLuma 已启动 (PID: "+thisPid+")\n"
                + "WebUI: "+webUiUrl()+"（账号 admin，临时密码见控制台输出，或执行 /axs qqbot snowluma password 查看）\n"
                + "登录 WebUI 添加 QQ 账号后，模块会自动连接 OneBot";
        } catch (Exception e) { status.set(Status.STOPPED); logger.log(Level.SEVERE,"[SnowLuma] 启动失败",e); return "启动失败: "+e.getMessage(); }
    }

    /**
     * 子进程 stdout 行处理：识别初始密码、端口冲突、告警行。
     * 密码行始终显示（这是用户唯一能拿到初始密码的途径）。
     */
    private void handleOutputLine(String line, AtomicReference<String> fatal) {
        line = stripAnsi(line);
        String pwd = extractPassword(line);
        if (pwd != null) {
            captureWebUiPassword(pwd);
            logger.info("[SnowLuma] " + line.trim());
            return;
        }
        if (ADDRESS_IN_USE.matcher(line).find()) {
            fatal.set("端口冲突: " + line);
            logger.severe("[SnowLuma] " + line);
            return;
        }
        if (ERROR_LINE.matcher(line).find()) {
            logger.warning("[SnowLuma] " + line);
        } else if (debugMode.getAsBoolean()) {
            logger.info("[SnowLuma] " + line);
        }
    }

    /** 从一行输出中提取 WebUI 临时密码（匹配官方两种输出格式），无匹配返回 null */
    private static @Nullable String extractPassword(String line) {
        Matcher m = CREDENTIALS_EN.matcher(line);
        if (m.find()) return m.group(1);
        m = CREDENTIALS_CN.matcher(line);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String stripAnsi(String s) {
        return ANSI_SEQ.matcher(s).replaceAll("");
    }

    /** 捕获到临时密码：存内存 + 落盘到安装目录，方便管理员重启后/命令查询 */
    private void captureWebUiPassword(String pwd) {
        if (pwd == null || pwd.isBlank() || pwd.equals(webUiPassword)) return;
        webUiPassword = pwd;
        logger.info("[SnowLuma] WebUI 初始密码已保存到 " + installDir.resolve(PASSWORD_FILE)
            + "，也可随时执行 /axs qqbot snowluma password 查看");
        try {
            Files.createDirectories(installDir);
            Files.writeString(installDir.resolve(PASSWORD_FILE),
                "SnowLuma WebUI 登录信息\n"
                    + "地址: " + webUiUrl() + "\n"
                    + "账号: admin\n"
                    + "临时密码: " + pwd + "\n"
                    + "说明: 首次登录后请立即修改密码。若未修改，SnowLuma 下次启动会重新生成新密码（本文件随之更新）。\n"
                    + "也可以在配置 onebot.snowluma.webui-password 中指定固定初始密码（仅首次初始化生效）。\n",
                StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (debugMode.getAsBoolean()) logger.info("[SnowLuma] 密码文件写入失败: " + e.getMessage());
        }
    }

    private @Nullable String readSavedPassword() {
        try {
            Path f = installDir.resolve(PASSWORD_FILE);
            if (!Files.exists(f)) return null;
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("临时密码")) {
                    int idx = t.indexOf(':');
                    if (idx < 0) idx = t.indexOf('：');
                    if (idx >= 0 && idx + 1 < t.length()) return t.substring(idx + 1).trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String stopNative() {
        status.set(Status.STOPPED); // 先置位，避免退出回调误报「意外退出」
        if (process==null||!process.isAlive()) { String c=killResidue(); return c!=null?"SnowLuma 已强制停止残留: "+c:"SnowLuma 未在运行"; }
        long pid=process.pid(); forceKill(pid); String c=killResidue(); process=null;
        logger.info("[SnowLuma] 已停止 (PID: "+pid+")");
        String m="SnowLuma 已停止 (PID: "+pid+")"; if (c!=null) m+="，清理残留: "+c; return m;
    }

    /** 需要清理残留的端口：docker 取宿主映射端口，native 取 WebUI(5099)+用户配置的 WS 端口。 */
    private int[] residuePorts() {
        if (config.isDocker()) return new int[]{config.dockerWebUiPort(), config.dockerWsPort(), config.dockerHttpPort()};
        return nativeWsPort == NATIVE_WEBUI_PORT ? new int[]{NATIVE_WEBUI_PORT} : new int[]{NATIVE_WEBUI_PORT, nativeWsPort};
    }

    private String killResidue() {
        StringBuilder killed = new StringBuilder();
        for (int port : residuePorts()) {
            long pid = findPidOnPort(port);
            if (pid>0) {
                if (process!=null && process.isAlive() && process.pid()==pid) continue;
                if (isSnowLumaOrNodeProcess(pid)) {
                    logger.warning("[SnowLuma] 端口 "+port+" 被 PID "+pid+" 占用，强制终止残留");
                    forceKill(pid); if (killed.length()>0) killed.append("; "); killed.append("PID ").append(pid).append(" (端口 ").append(port).append(")");
                } else { logger.warning("[SnowLuma] 端口 "+port+" 被 PID "+pid+" 占用，但不是 SnowLuma/Node，请手动处理"); }
            }
        }
        if (killed.length()>0) { try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } return killed.toString(); }
        return null;
    }

    private void forceKill(long pid) {
        // 先精确杀掉「该 PID 派生的子孙进程」——descendants() 仅按进程父子关系圈定本进程树，
        // 不按进程名全局匹配，绝不会误伤系统里其它 node/无关进程。
        try {
            java.util.Optional<ProcessHandle> handleOpt = ProcessHandle.of(pid);
            if (handleOpt.isPresent()) {
                // 先快照（避免父进程被杀后子进程被 init 收养、丢失父子链导致漏杀）
                java.util.List<ProcessHandle> descendants = handleOpt.get().descendants().toList();
                for (ProcessHandle child : descendants) { try { child.destroy(); } catch (Exception ignored) {} }
                for (ProcessHandle child : descendants) {
                    if (child.isAlive()) { try { child.destroyForcibly(); } catch (Exception ignored) {} }
                }
                if (!descendants.isEmpty()) logger.info("[SnowLuma] 已清理 " + descendants.size() + " 个子进程 (父 PID: " + pid + ")");
            }
        } catch (Exception e) { if (debugMode.getAsBoolean()) logger.info("[SnowLuma] 子进程清理异常: " + e.getMessage()); }
        String os = System.getProperty("os.name","").toLowerCase();
        if (os.contains("win")) {
            try { new ProcessBuilder("taskkill","/F","/T","/PID",String.valueOf(pid)).redirectErrorStream(true).start().waitFor(10,java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception e) { logger.warning("taskkill 失败: "+e.getMessage()); if (process!=null&&process.pid()==pid) process.destroyForcibly(); }
        } else {
            try {
                new ProcessBuilder("kill","-TERM",String.valueOf(pid)).redirectErrorStream(true).start().waitFor(3,java.util.concurrent.TimeUnit.SECONDS);
                if (isProcessAlive(pid)) new ProcessBuilder("kill","-KILL",String.valueOf(pid)).redirectErrorStream(true).start().waitFor(3,java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { logger.warning("kill 失败: "+e.getMessage()); if (process!=null&&process.pid()==pid) process.destroyForcibly(); }
        }
    }

    private long findPidOnPort(int port) {
        String os = System.getProperty("os.name","").toLowerCase();
        try {
            if (os.contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("cmd","/c","netstat -ano | findstr LISTENING | findstr \":"+port+"\"");
                pb.redirectErrorStream(true); Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line; while ((line=r.readLine())!=null) { String[] parts=line.trim().split("\\s+"); if (parts.length>=5 && parts[parts.length-2].toUpperCase().contains("LISTENING")) return Long.parseLong(parts[parts.length-1]); }
                }
                p.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);
            } else {
                ProcessBuilder pb = new ProcessBuilder("lsof","-ti:"+port); pb.redirectErrorStream(true); Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { String line=r.readLine(); if (line!=null&&!line.isBlank()) return Long.parseLong(line.trim()); }
                p.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) { if (debugMode.getAsBoolean()) logger.info("端口 "+port+" PID 查询失败: "+e.getMessage()); }
        return -1;
    }

    private boolean isSnowLumaOrNodeProcess(long pid) {
        String os = System.getProperty("os.name","").toLowerCase();
        try {
            if (os.contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("wmic","process","where","ProcessId="+pid,"get","CommandLine","/format:list");
                pb.redirectErrorStream(true); Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { String line; while ((line=r.readLine())!=null) { String l=line.toLowerCase(); if (l.contains("snowluma")||l.contains("node")||l.contains("index.js")) return true; } }
                p.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);
                ProcessBuilder pb2 = new ProcessBuilder("tasklist","/FI","PID eq "+pid,"/FO","CSV","/NH");
                pb2.redirectErrorStream(true); Process p2 = pb2.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p2.getInputStream()))) { String line=r.readLine(); if (line!=null) { String l=line.toLowerCase(); if (l.contains("node.exe")||l.contains("node")||l.contains("snowluma")) return true; } }
                p2.waitFor(3,java.util.concurrent.TimeUnit.SECONDS);
            } else {
                Path cmdline = Paths.get("/proc",String.valueOf(pid),"cmdline");
                if (Files.exists(cmdline)) { String c = Files.readString(cmdline).toLowerCase(); return c.contains("snowluma")||c.contains("node")||c.contains("index.mjs")||c.contains("index.js"); }
            }
        } catch (Exception e) { if (debugMode.getAsBoolean()) logger.info("PID "+pid+" 进程识别失败: "+e.getMessage()); }
        return false;
    }

    private boolean isProcessAlive(long pid) {
        try {
            if (System.getProperty("os.name","").toLowerCase().contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("tasklist","/FI","PID eq "+pid); pb.redirectErrorStream(true); Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { String line; while ((line=r.readLine())!=null) if (line.contains(String.valueOf(pid))) return true; }
                p.waitFor(2,java.util.concurrent.TimeUnit.SECONDS); return false;
            } else { return Files.exists(Paths.get("/proc",String.valueOf(pid))); }
        } catch (Exception e) { return false; }
    }

    private CompletableFuture<String> installNativeAsync(@Nullable Consumer<String> progress) {
        if (status.get()==Status.INSTALLING) return CompletableFuture.completedFuture("已在安装中，执行 /axs qqbot snowluma status 查看进度");
        if (status.get()==Status.RUNNING) return CompletableFuture.completedFuture("请先停止再安装");
        if (status.get()==Status.STARTING) return CompletableFuture.completedFuture("SnowLuma 正在启动中，请稍后执行 /axs qqbot snowluma status 查看结果");
        boolean reinstall = isInstalled();
        status.set(Status.INSTALLING);
        report(progress, reinstall ? "检测到已安装，将下载最新版覆盖更新..." : "开始安装 SnowLuma...");
        return CompletableFuture.supplyAsync(() -> {
            Path archive = null;
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest apiReq = HttpRequest.newBuilder().uri(URI.create(GITHUB_API)).header("User-Agent","ArcartXSuite-QQBot").GET().build();
                HttpResponse<String> apiResp = client.send(apiReq, HttpResponse.BodyHandlers.ofString());
                if (apiResp.statusCode() / 100 != 2) {
                    status.set(Status.STOPPED);
                    return "查询最新版本失败: GitHub API 返回 HTTP " + apiResp.statusCode()
                        + "（可能是网络无法访问 GitHub，可手动下载后解压到 " + installDir + "）";
                }
                String downloadUrl = extractDownloadUrl(apiResp.body());
                if (downloadUrl==null) { status.set(Status.STOPPED); return "未找到适配当前系统的 Release 资产"; }
                String tag = extractTag(apiResp.body());
                report(progress, "下载 " + (tag != null ? tag + " " : "") + "完整包（自带 Node 运行时）:\n" + downloadUrl);
                String ext = downloadUrl.toLowerCase().endsWith(".tar.gz") ? ".tar.gz" : ".zip";
                archive = installDir.getParent().resolve("snowluma-download"+ext); Files.createDirectories(installDir);
                HttpRequest dlReq = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).header("User-Agent","ArcartXSuite-QQBot").GET().build();
                HttpResponse<InputStream> dlResp = client.send(dlReq, HttpResponse.BodyHandlers.ofInputStream());
                if (dlResp.statusCode() / 100 != 2) {
                    status.set(Status.STOPPED);
                    return "下载失败: HTTP " + dlResp.statusCode()
                        + "（可手动下载 " + downloadUrl + " 解压到 " + installDir + "）";
                }
                long total = dlResp.headers().firstValueAsLong("Content-Length").orElse(-1);
                downloadWithProgress(dlResp.body(), archive, total, progress);
                report(progress, "解压中...");
                extractArchive(archive, installDir);
                Files.deleteIfExists(archive); archive = null;
                flattenIfNeeded(); status.set(Status.STOPPED);
                report(progress, "安装完成");
                return "SnowLuma " + (tag != null ? tag : "") + " 安装完成！下一步:\n"
                    + "1. 执行 /axs qqbot snowluma start 启动\n"
                    + "2. 浏览器打开 " + webUiUrl() + " 登录 WebUI（账号 admin，临时密码见控制台输出或 /axs qqbot snowluma password）\n"
                    + "3. 在 WebUI 中添加 QQ 账号并登录，OneBot 连接将自动建立";
            } catch (Exception e) {
                status.set(Status.STOPPED);
                if (archive != null) try { Files.deleteIfExists(archive); } catch (IOException ignored) {}
                logger.log(Level.SEVERE,"[SnowLuma] 安装失败",e);
                return "安装失败: " + e.getMessage() + "（可访问 https://github.com/SnowLuma/SnowLuma/releases 手动下载解压到 " + installDir + "）";
            }
        });
    }

    /** 流式下载并按 ~10% 粒度回报进度（Content-Length 缺失时按每 10MB 回报） */
    private void downloadWithProgress(InputStream in, Path dest, long total, @Nullable Consumer<String> progress) throws IOException {
        try (in; OutputStream out = Files.newOutputStream(dest)) {
            byte[] buf = new byte[65536];
            long done = 0;
            int lastStep = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int step = (int) (done * 10 / total); // 0..10
                    if (step > lastStep) {
                        lastStep = step;
                        report(progress, String.format("下载中 %d%%（%.1f / %.1f MB）",
                            step * 10, done / 1048576.0, total / 1048576.0));
                    }
                } else {
                    int step = (int) (done / (10 * 1048576L)); // 每 10MB
                    if (step > lastStep) {
                        lastStep = step;
                        report(progress, "已下载 " + (done / 1048576L) + " MB（服务端未返回总大小）");
                    }
                }
            }
        }
    }

    /** 进度回报：控制台日志 + 记录当前阶段 + 透传给命令发起人 */
    private void report(@Nullable Consumer<String> progress, String msg) {
        installPhase = msg;
        logger.info("[SnowLuma] " + msg);
        if (progress != null) {
            try { progress.accept(msg); } catch (Exception ignored) {}
        }
    }

    /** 读取子进程输出，节流回报最后一行进度，返回末尾若干行供失败时拼进错误信息 */
    private String pumpProgress(InputStream in, @Nullable Consumer<String> progress, String prefix) throws IOException {
        java.util.Deque<String> tail = new java.util.ArrayDeque<>();
        long lastReport = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                tail.addLast(t);
                while (tail.size() > 12) tail.pollFirst();
                long now = System.currentTimeMillis();
                if (now - lastReport > 4000) {
                    lastReport = now;
                    report(progress, prefix + ": " + (t.length() > 120 ? t.substring(0, 120) : t));
                }
            }
        }
        return String.join("\n", tail);
    }

    private String statusReportNative() {
        Status s = getStatus(); String localVer = getLocalVersion();
        checkNodeEnvironment(true);
        StringBuilder sb = new StringBuilder("§6[SnowLuma 状态]\n");
        sb.append("§7  安装目录: §f").append(installDir).append("\n");
        sb.append("§7  本地版本: §f").append(localVer != null ? localVer : "未知").append("\n");
        sb.append("§7  Node.js: §f").append(nodeVersion != null ? nodeVersion : "未检测到").append("\n");
        sb.append("§7  已安装: §f").append(isInstalled()?"是":"否").append("\n");
        sb.append("§7  状态: §f").append(s.name()).append("\n");
        if (s == Status.INSTALLING && installPhase != null) {
            sb.append("§7  安装进度: §f").append(installPhase).append("\n");
        }
        if (s==Status.RUNNING && process!=null) sb.append("§7  PID: §f").append(process.pid()).append("\n");
        if (isInstalled()) {
            sb.append("§7  WebUI: §f").append(webUiUrl()).append(" §7(账号 admin，密码: /axs qqbot snowluma password)\n");
        } else {
            sb.append("§7  提示: §f未安装，执行 /axs qqbot snowluma install 自动下载安装\n");
        }
        sb.append("§7  自动启动: §f").append(config.autoStart()?"是":"否"); return sb.toString();
    }

    /**
     * 构建启动命令：直接以 node 运行 index.mjs（优先安装目录自带的 node 运行时，
     * 其次 PATH 上的 node），不经 launcher 脚本 —— 避免 cmd 回显批处理行造成的
     * 告警误判，也避免 launcher.bat 末尾 pause 在无控制台时挂住退出流程。
     * 找不到入口文件时回退到官方 launcher 脚本。
     */
    private ProcessBuilder buildProcess() {
        Path bundled = bundledNodeBin();
        String nodeBin = bundled != null ? bundled.toAbsolutePath().toString() : "node";
        Path entry = null;
        if (Files.exists(installDir.resolve("index.mjs"))) entry = installDir.resolve("index.mjs");
        else if (Files.exists(installDir.resolve("index.js"))) entry = installDir.resolve("index.js");
        ProcessBuilder pb;
        if (entry != null) {
            pb = new ProcessBuilder(nodeBin, entry.getFileName().toString());
        } else {
            // 兜底：无 index 入口的老布局，走官方 launcher
            Path launcher = getLauncher();
            String os = System.getProperty("os.name","").toLowerCase();
            String fn = launcher.getFileName().toString();
            if (os.contains("win")) pb = new ProcessBuilder("cmd","/c",fn);
            else if (fn.endsWith(".sh")) pb = new ProcessBuilder("/bin/bash",fn);
            else pb = new ProcessBuilder(nodeBin, fn);
        }
        // SnowLuma 支持的环境变量：固定初始密码（仅首次初始化生效）与 EULA 自动同意
        var env = pb.environment();
        if (config.webUiPassword() != null && !config.webUiPassword().isBlank()) {
            env.put("SNOWLUMA_WEBUI_BOOTSTRAP_PASSWORD", config.webUiPassword());
        }
        if (config.acceptAgreements()) {
            env.put("SNOWLUMA_ACCEPT_EULA", "1");
            env.put("SNOWLUMA_ACCEPT_PRIVACY", "1");
        }
        return pb;
    }

    private Path getLauncher() {
        String os = System.getProperty("os.name","").toLowerCase();
        if (os.contains("win")) { Path bat=installDir.resolve("launcher.bat"); if (Files.exists(bat)) return bat; }
        else { Path sh=installDir.resolve("launcher.sh"); if (Files.exists(sh)) return sh; }
        Path mjs=installDir.resolve("index.mjs"); if (Files.exists(mjs)) return mjs;
        Path js=installDir.resolve("index.js"); if (Files.exists(js)) return js;
        return installDir.resolve("launcher.bat");
    }

    /**
     * 从 GitHub Release JSON 挑选当前平台的下载地址。
     * 优先完整版（文件名不含 lite，自带 node 运行时），lite 作为兜底。
     */
    private String extractDownloadUrl(String json) {
        String os = System.getProperty("os.name","").toLowerCase();
        String arch = System.getProperty("os.arch","").toLowerCase();
        boolean isArm64 = arch.contains("aarch64") || arch.contains("arm64");

        String precisePlatform;
        if (os.contains("win")) {
            precisePlatform = "win-x64";
        } else if (isArm64) {
            precisePlatform = "linux-arm64";
        } else {
            precisePlatform = "linux-x64";
        }

        String url = findUrl(json, precisePlatform, Boolean.FALSE);  if (url!=null) return url;
        url = findUrl(json, precisePlatform, Boolean.TRUE);          if (url!=null) return url;

        // fallback: 模糊匹配（兼容旧命名或边缘平台）
        String loose = os.contains("win") ? "win" : "linux";
        url = findUrl(json, loose, Boolean.FALSE); if (url!=null) return url;
        url = findUrl(json, loose, Boolean.TRUE);  if (url!=null) return url;

        return findUrl(json, null, null);
    }

    /**
     * @param liteFilter TRUE=只要 lite 包，FALSE=只要完整版（不含 lite），null=不限
     */
    private String findUrl(String json, String platformKeyword, Boolean liteFilter) {
        int idx = json.indexOf("browser_download_url");
        int end = idx;
        while (idx!=-1) {
            int start=json.indexOf("\"", idx+22)+1; end=json.indexOf("\"", start);
            if (start>0 && end>start) {
                String url=json.substring(start,end); String lower=url.toLowerCase();
                boolean isArchive = lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
                if (isArchive) {
                    boolean platformMatch = platformKeyword==null || lower.contains(platformKeyword);
                    // x64 平台排除 arm64，防止误匹配
                    if (platformKeyword!=null && (platformKeyword.equals("win-x64")||platformKeyword.equals("linux-x64"))) {
                        platformMatch = platformMatch && !lower.contains("arm64");
                    }
                    boolean liteMatch = liteFilter == null
                        || (liteFilter ? lower.contains("lite") : !lower.contains("lite"));
                    if (platformMatch && liteMatch) return url;
                }
            }
            idx = json.indexOf("browser_download_url", end);
        }
        return null;
    }

    private void extractArchive(Path archiveFile, Path destDir) throws IOException {
        String name = archiveFile.getFileName().toString().toLowerCase();
        if (name.endsWith(".zip")) unzip(archiveFile, destDir);
        else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) untarGz(archiveFile, destDir);
        else throw new IOException("不支持的格式: "+name);
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry; byte[] buf = new byte[8192];
            while ((entry=zis.getNextEntry())!=null) {
                Path out = destDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(destDir)) throw new IOException("Zip entry outside target: "+entry.getName());
                if (entry.isDirectory()) Files.createDirectories(out);
                else { Files.createDirectories(out.getParent()); try (OutputStream os=Files.newOutputStream(out)) { int len; while ((len=zis.read(buf))>0) os.write(buf,0,len); } }
                zis.closeEntry();
            }
        }
    }

    private void untarGz(Path tarGzFile, Path destDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("tar","-xzf",tarGzFile.toString(),"-C",destDir.toString());
            pb.redirectErrorStream(true); Process p = pb.start();
            if (!p.waitFor(30,java.util.concurrent.TimeUnit.SECONDS) || p.exitValue()!=0)
                throw new IOException("tar 解压失败: "+readAll(p.getInputStream()));
        } catch (IOException e) { throw e; } catch (Exception e) { throw new IOException("tar 异常", e); }
    }

    private void flattenIfNeeded() throws IOException {
        File[] children = installDir.toFile().listFiles();
        if (children!=null && children.length==1 && children[0].isDirectory()) {
            File inner = children[0]; boolean hasLauncher=false;
            String os = System.getProperty("os.name","").toLowerCase();
            if (os.contains("win")) hasLauncher = new File(inner,"launcher.bat").exists() || new File(inner,"index.mjs").exists() || new File(inner,"index.js").exists();
            else hasLauncher = new File(inner,"launcher.sh").exists() || new File(inner,"index.mjs").exists() || new File(inner,"index.js").exists();
            if (hasLauncher) { for (File f:inner.listFiles()) Files.move(f.toPath(), installDir.resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING); inner.delete(); }
        }
    }

    // --- 版本检测 ---

    public CompletableFuture<String> checkUpdateAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(GITHUB_API)).header("User-Agent","ArcartXSuite-QQBot").GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                String latestTag = extractTag(resp.body());
                if (latestTag == null) return "无法获取最新版本信息";
                String localVer = getLocalVersion();
                if (localVer == null) return "最新版本: " + latestTag + " | 本地版本: 未检测到";
                if (localVer.equalsIgnoreCase(latestTag)) return "当前已是最新版本: " + latestTag;
                return "发现新版本: " + latestTag + " (当前: " + localVer + ")，执行 /axs qqbot snowluma install 可更新";
            } catch (Exception e) { return "版本检查失败: " + e.getMessage(); }
        });
    }

    private String extractTag(String json) {
        int idx = json.indexOf("\"tag_name\"");
        if (idx == -1) return null;
        int start = json.indexOf("\"", idx + 10) + 1;
        int end = json.indexOf("\"", start);
        if (start > 0 && end > start) return json.substring(start, end);
        return null;
    }

    private String getLocalVersion() {
        if (config.isDocker()) {
            try {
                ProcessBuilder pb = new ProcessBuilder("docker","inspect","--format={{.Config.Image}}",config.dockerContainerName());
                pb.redirectErrorStream(true); Process p = pb.start();
                if (p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                    String image = readAll(p.getInputStream()).trim();
                    int colon = image.lastIndexOf(':');
                    if (colon > 0 && !"latest".equals(image.substring(colon+1))) return image.substring(colon+1);
                    // latest 标签无法确定具体版本，尝试读取容器内 package.json
                    try {
                        ProcessBuilder pb2 = new ProcessBuilder("docker","exec",config.dockerContainerName(),"cat","/app/snowluma-data/package.json");
                        pb2.redirectErrorStream(true); Process p2 = pb2.start();
                        if (p2.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p2.exitValue() == 0) {
                            String pkg = readAll(p2.getInputStream());
                            int vIdx = pkg.indexOf("\"version\"");
                            if (vIdx != -1) { int s = pkg.indexOf("\"", vIdx+9)+1; int e = pkg.indexOf("\"", s); if (s>0&&e>s) return "v"+pkg.substring(s,e); }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {}
            return null;
        } else {
            try {
                Path pkg = installDir.resolve("package.json");
                if (Files.exists(pkg)) {
                    String content = Files.readString(pkg);
                    int idx = content.indexOf("\"version\"");
                    if (idx != -1) {
                        int start = content.indexOf("\"", idx + 9) + 1;
                        int end = content.indexOf("\"", start);
                        if (start > 0 && end > start) return "v" + content.substring(start, end);
                    }
                }
            } catch (Exception e) {}
            return null;
        }
    }

    private String readAll(InputStream is) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line=r.readLine())!=null) { if (sb.length()>0) sb.append("\n"); sb.append(line); }
            return sb.toString();
        }
    }
}
