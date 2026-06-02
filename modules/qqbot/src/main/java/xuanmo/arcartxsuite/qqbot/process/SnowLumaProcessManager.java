package xuanmo.arcartxsuite.qqbot.process;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 管理 SnowLuma 进程的启动、停止、安装。
 * 由 QQBotModule 在 startService 时创建，stopService 时销毁。
 */
public final class SnowLumaProcessManager {

    public enum Status { STOPPED, STARTING, RUNNING, INSTALLING }

    private static final String GITHUB_API = "https://api.github.com/repos/SnowLuma/SnowLuma/releases/latest";

    private final Path installDir;
    private final boolean autoStart;
    private final Logger logger;
    private final BooleanSupplier debugMode;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.STOPPED);
    private volatile Process process;
    private Thread outputThread;

    public SnowLumaProcessManager(Path serverRoot, String installDirName, boolean autoStart, Logger logger, BooleanSupplier debugMode) {
        this.installDir = serverRoot.resolve(installDirName);
        this.autoStart = autoStart;
        this.logger = logger;
        this.debugMode = debugMode;
    }

    /**
     * 模块启动时调用，如果 autoStart=true 且已安装则自动启动。
     */
    public void init() {
        if (autoStart && isInstalled()) {
            start();
        } else if (autoStart && !isInstalled()) {
            logger.info("[QQBot/SnowLuma] 未检测到安装目录，请执行 /axs qqbot snowluma install");
        }
    }

    public Status getStatus() {
        // 如果进程对象存在但已退出，修正状态
        if (status.get() == Status.RUNNING && (process == null || !process.isAlive())) {
            status.set(Status.STOPPED);
        }
        return status.get();
    }

    public boolean isInstalled() {
        return Files.exists(getLauncher());
    }

    /**
     * 启动 SnowLuma 子进程。
     * 启动前会自动清理占用 5099/3001 的残留进程。
     */
    public String start() {
        if (status.get() == Status.RUNNING && process != null && process.isAlive()) {
            return "SnowLuma 已在运行中 (PID: " + process.pid() + ")";
        }
        if (status.get() == Status.INSTALLING) {
            return "正在安装中，请稍后再试";
        }
        if (!isInstalled()) {
            return "SnowLuma 未安装，请先执行 /axs qqbot snowluma install";
        }

        // ── 端口预检：清理残留实例 ──
        String cleanupMsg = killResidue();
        if (cleanupMsg != null) {
            logger.info("[QQBot/SnowLuma] " + cleanupMsg);
        }

        status.set(Status.STARTING);
        try {
            ProcessBuilder pb = buildProcess();
            pb.directory(installDir.toFile());
            pb.redirectErrorStream(true);
            process = pb.start();
            final long thisPid = process.pid();

            // 转发子进程输出到日志（仅 debug 模式，ERROR/WARN 始终显示）
            // 同时监听致命错误（如端口冲突），检测到后自动标记 STOPPED
            AtomicReference<String> fatalError = new AtomicReference<>();
            outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String lower = line.toLowerCase();
                        if (lower.contains("eaddrinuse") || lower.contains("address already in use")) {
                            fatalError.set("端口冲突: " + line);
                            logger.severe("[SnowLuma] " + line);
                        } else if (lower.contains("error") || lower.contains("warn") || lower.contains("fail")) {
                            logger.warning("[SnowLuma] " + line);
                        } else if (debugMode.getAsBoolean()) {
                            logger.info("[SnowLuma] " + line);
                        }
                    }
                } catch (IOException ignored) {}
            }, "AXS-SnowLuma-Output");
            outputThread.setDaemon(true);
            outputThread.start();

            // 给 SnowLuma 3 秒初始化窗口，检查是否已因端口冲突崩溃
            try { Thread.sleep(3000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            if (fatalError.get() != null) {
                forceKill(thisPid);
                status.set(Status.STOPPED);
                process = null;
                return "启动失败: " + fatalError.get() + "\n请检查是否有其他 SnowLuma / Node 进程占用 5099 或 3001 端口。";
            }
            if (!process.isAlive()) {
                status.set(Status.STOPPED);
                process = null;
                return "启动失败: SnowLuma 进程启动后立即退出，请查看日志排查原因。";
            }

            status.set(Status.RUNNING);
            logger.info("[QQBot/SnowLuma] 进程已启动 (PID: " + thisPid + ")");
            return "SnowLuma 已启动 (PID: " + thisPid + ")，WebUI: http://127.0.0.1:5099";
        } catch (Exception e) {
            status.set(Status.STOPPED);
            logger.log(Level.SEVERE, "[QQBot/SnowLuma] 启动失败", e);
            return "启动失败: " + e.getMessage();
        }
    }

    /**
     * 停止 SnowLuma 子进程，并清理残留端口占用。
     */
    public String stop() {
        if (process == null || !process.isAlive()) {
            // 当前没托管进程，但仍可能残留旧实例
            String cleanup = killResidue();
            status.set(Status.STOPPED);
            return cleanup != null ? "SnowLuma 已强制停止残留进程: " + cleanup : "SnowLuma 未在运行";
        }
        long pid = process.pid();
        forceKill(pid);
        // 再清一次端口，确保无残留 node 实例
        String cleanup = killResidue();
        status.set(Status.STOPPED);
        process = null;
        logger.info("[QQBot/SnowLuma] 进程已停止 (PID: " + pid + ")");
        String msg = "SnowLuma 已停止 (PID: " + pid + ")";
        if (cleanup != null) msg += "，同时清理了残留: " + cleanup;
        return msg;
    }

    /**
     * 关闭（模块卸载时调用）。
     */
    public void shutdown() {
        if (process != null && process.isAlive()) {
            stop();
        } else {
            // 即使当前没托管进程，也尝试清理残留
            String cleanup = killResidue();
            if (cleanup != null) {
                logger.info("[QQBot/SnowLuma] 模块卸载时清理残留: " + cleanup);
            }
        }
    }

    // ─── 残留进程清理 ───

    private String killResidue() {
        StringBuilder killed = new StringBuilder();
        // 检查 5099 (WebUI) 和 3001 (WS)
        for (int port : new int[]{5099, 3001}) {
            long pid = findPidOnPort(port);
            if (pid > 0) {
                // 如果是当前托管的进程，跳过
                if (process != null && process.isAlive() && process.pid() == pid) {
                    continue;
                }
                if (isSnowLumaOrNodeProcess(pid)) {
                    logger.warning("[QQBot/SnowLuma] 端口 " + port + " 被 PID " + pid + " 占用，强制终止残留进程");
                    forceKill(pid);
                    if (killed.length() > 0) killed.append("; ");
                    killed.append("PID ").append(pid).append(" (端口 ").append(port).append(")");
                } else {
                    logger.warning("[QQBot/SnowLuma] 端口 " + port + " 被 PID " + pid + " 占用，但该进程不是 SnowLuma/Node，请手动处理");
                }
            }
        }
        if (killed.length() > 0) {
            // 等端口释放
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return killed.toString();
        }
        return null;
    }

    private void forceKill(long pid) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid));
                pb.redirectErrorStream(true);
                Process killProc = pb.start();
                killProc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warning("[QQBot/SnowLuma] taskkill /F /T /PID " + pid + " 失败: " + e.getMessage());
                if (process != null && process.pid() == pid) {
                    process.destroyForcibly();
                }
            }
        } else {
            // Unix: 先尝试 SIGTERM，再 SIGKILL
            try {
                ProcessBuilder pb = new ProcessBuilder("kill", "-TERM", String.valueOf(pid));
                pb.redirectErrorStream(true).start().waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                // 检查是否还活着
                if (isProcessAlive(pid)) {
                    new ProcessBuilder("kill", "-KILL", String.valueOf(pid))
                        .redirectErrorStream(true).start().waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                logger.warning("[QQBot/SnowLuma] kill 失败: " + e.getMessage());
                if (process != null && process.pid() == pid) {
                    process.destroyForcibly();
                }
            }
        }
    }

    private long findPidOnPort(int port) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                // 先过滤 LISTENING 状态，再找目标端口
                // 顺序不能反：findstr LISTENING 先执行，避免匹配到远程端为 PORT 的 ESTABLISHED 连接
                ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c",
                    "netstat -ano | findstr LISTENING | findstr \":" + port + "\""
                );
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 格式: TCP  0.0.0.0:5099  0.0.0.0:0  LISTENING  3832
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 5) {
                            String state = parts[parts.length - 2].toUpperCase();
                            if (state.contains("LISTENING")) {
                                return Long.parseLong(parts[parts.length - 1]);
                            }
                        }
                    }
                }
                proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                // lsof -ti:<port>
                ProcessBuilder pb = new ProcessBuilder("lsof", "-ti:" + port);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.isBlank()) {
                        return Long.parseLong(line.trim());
                    }
                }
                proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            if (debugMode.getAsBoolean()) {
                logger.info("[QQBot/SnowLuma] 端口 " + port + " PID 查询失败: " + e.getMessage());
            }
        }
        return -1;
    }

    private boolean isSnowLumaOrNodeProcess(long pid) {
        // 无法 100% 确认，但 SnowLuma 是 Node 进程，多渠道验证
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                // 通道 1：wmic 查 CommandLine（最准确）
                ProcessBuilder pb = new ProcessBuilder("wmic", "process", "where", "ProcessId=" + pid, "get", "CommandLine", "/format:list");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String lower = line.toLowerCase();
                        if (lower.contains("snowluma") || lower.contains("node") || lower.contains("index.js")) {
                            return true;
                        }
                    }
                }
                proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);

                // 通道 2：tasklist 查进程名（备选，更快更可靠）
                ProcessBuilder pb2 = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH");
                pb2.redirectErrorStream(true);
                Process proc2 = pb2.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc2.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) {
                        String lower = line.toLowerCase();
                        if (lower.contains("node.exe") || lower.contains("node") || lower.contains("snowluma")) {
                            return true;
                        }
                    }
                }
                proc2.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            } else {
                // Linux: 读取 /proc/<pid>/cmdline
                Path cmdline = Paths.get("/proc", String.valueOf(pid), "cmdline");
                if (Files.exists(cmdline)) {
                    String content = Files.readString(cmdline).toLowerCase();
                    return content.contains("snowluma") || content.contains("node") || content.contains("index.js");
                }
            }
        } catch (Exception e) {
            if (debugMode.getAsBoolean()) {
                logger.info("[QQBot/SnowLuma] PID " + pid + " 进程识别失败: " + e.getMessage());
            }
        }
        return false; // 保守策略：不认识就不杀
    }

    private boolean isProcessAlive(long pid) {
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains(String.valueOf(pid))) return true;
                    }
                }
                proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                return false;
            } else {
                return Files.exists(Paths.get("/proc", String.valueOf(pid)));
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 异步安装最新版 SnowLuma。
     */
    public CompletableFuture<String> installAsync() {
        if (status.get() == Status.INSTALLING) {
            return CompletableFuture.completedFuture("已在安装中，请勿重复执行");
        }
        if (status.get() == Status.RUNNING) {
            return CompletableFuture.completedFuture("请先停止 SnowLuma 再安装/更新");
        }
        status.set(Status.INSTALLING);
        logger.info("[QQBot/SnowLuma] 开始从 GitHub 下载最新版...");

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

                // 1. 获取最新 release
                HttpRequest apiReq = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API))
                    .header("User-Agent", "ArcartXSuite-QQBot")
                    .GET().build();
                HttpResponse<String> apiResp = client.send(apiReq, HttpResponse.BodyHandlers.ofString());
                String body = apiResp.body();

                // 简单解析 browser_download_url（找第一个 .zip）
                String downloadUrl = extractZipUrl(body);
                if (downloadUrl == null) {
                    status.set(Status.STOPPED);
                    return "未找到可用的 Release 资源";
                }

                logger.info("[QQBot/SnowLuma] 下载: " + downloadUrl);

                // 2. 下载 zip
                HttpRequest dlReq = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("User-Agent", "ArcartXSuite-QQBot")
                    .GET().build();
                Path zipFile = installDir.getParent().resolve("snowluma-download.zip");
                Files.createDirectories(installDir);
                client.send(dlReq, HttpResponse.BodyHandlers.ofFile(zipFile));

                // 3. 解压
                logger.info("[QQBot/SnowLuma] 解压中...");
                unzip(zipFile, installDir);
                Files.deleteIfExists(zipFile);

                // 4. 整理目录（如果解压后有嵌套）
                flattenIfNeeded();

                status.set(Status.STOPPED);
                logger.info("[QQBot/SnowLuma] 安装完成！执行 /axs qqbot snowluma start 启动");
                return "安装完成！执行 /axs qqbot snowluma start 启动";
            } catch (Exception e) {
                status.set(Status.STOPPED);
                logger.log(Level.SEVERE, "[QQBot/SnowLuma] 安装失败", e);
                return "安装失败: " + e.getMessage();
            }
        });
    }

    public String statusReport() {
        Status s = getStatus();
        StringBuilder sb = new StringBuilder();
        sb.append("§6[SnowLuma 状态]\n");
        sb.append("§7  安装目录: §f").append(installDir).append("\n");
        sb.append("§7  已安装: §f").append(isInstalled() ? "是" : "否").append("\n");
        sb.append("§7  状态: §f").append(s.name()).append("\n");
        if (s == Status.RUNNING && process != null) {
            sb.append("§7  PID: §f").append(process.pid()).append("\n");
        }
        sb.append("§7  自动启动: §f").append(autoStart ? "是" : "否");
        return sb.toString();
    }

    // ─── 内部方法 ───

    private ProcessBuilder buildProcess() {
        Path launcher = getLauncher();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            // Windows: cmd /c launcher.bat
            return new ProcessBuilder("cmd", "/c", launcher.getFileName().toString());
        } else {
            // Linux/Mac
            return new ProcessBuilder("/bin/bash", launcher.getFileName().toString());
        }
    }

    private Path getLauncher() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            Path bat = installDir.resolve("launcher.bat");
            if (Files.exists(bat)) return bat;
        } else {
            Path sh = installDir.resolve("launcher.sh");
            if (Files.exists(sh)) return sh;
        }
        // fallback: node index.js
        Path index = installDir.resolve("index.js");
        if (Files.exists(index)) return index;
        return installDir.resolve("launcher.bat");
    }

    private String extractZipUrl(String json) {
        // 简易解析：找 "browser_download_url": "...zip"
        String os = System.getProperty("os.name", "").toLowerCase();
        String keyword = os.contains("win") ? "win" : "linux";

        // 优先匹配平台特定的
        int idx = json.indexOf("browser_download_url");
        while (idx != -1) {
            int start = json.indexOf("\"", idx + 22) + 1;
            int end = json.indexOf("\"", start);
            if (start > 0 && end > start) {
                String url = json.substring(start, end);
                if (url.endsWith(".zip") && url.toLowerCase().contains(keyword)) {
                    return url;
                }
            }
            idx = json.indexOf("browser_download_url", end);
        }
        // fallback: 任意 .zip
        idx = json.indexOf("browser_download_url");
        while (idx != -1) {
            int start = json.indexOf("\"", idx + 22) + 1;
            int end = json.indexOf("\"", start);
            if (start > 0 && end > start) {
                String url = json.substring(start, end);
                if (url.endsWith(".zip")) {
                    return url;
                }
            }
            idx = json.indexOf("browser_download_url", end);
        }
        return null;
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = destDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(destDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (OutputStream os = Files.newOutputStream(outPath)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void flattenIfNeeded() throws IOException {
        // 如果解压后只有一个子目录且里面有 launcher，提上来
        File[] children = installDir.toFile().listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()) {
            File inner = children[0];
            File launcherCheck = new File(inner, "launcher.bat");
            File launcherCheck2 = new File(inner, "index.js");
            if (launcherCheck.exists() || launcherCheck2.exists()) {
                for (File f : inner.listFiles()) {
                    Files.move(f.toPath(), installDir.resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING);
                }
                inner.delete();
            }
        }
    }
}
