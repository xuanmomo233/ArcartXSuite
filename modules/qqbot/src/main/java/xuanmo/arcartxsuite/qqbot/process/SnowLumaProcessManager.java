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

        status.set(Status.STARTING);
        try {
            ProcessBuilder pb = buildProcess();
            pb.directory(installDir.toFile());
            pb.redirectErrorStream(true);
            process = pb.start();

            // 转发子进程输出到日志（仅 debug 模式，ERROR/WARN 始终显示）
            outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String lower = line.toLowerCase();
                        if (lower.contains("error") || lower.contains("warn") || lower.contains("fail")) {
                            logger.warning("[SnowLuma] " + line);
                        } else if (debugMode.getAsBoolean()) {
                            logger.info("[SnowLuma] " + line);
                        }
                    }
                } catch (IOException ignored) {}
            }, "AXS-SnowLuma-Output");
            outputThread.setDaemon(true);
            outputThread.start();

            status.set(Status.RUNNING);
            logger.info("[QQBot/SnowLuma] 进程已启动 (PID: " + process.pid() + ")");
            return "SnowLuma 已启动 (PID: " + process.pid() + ")，WebUI: http://127.0.0.1:5099";
        } catch (Exception e) {
            status.set(Status.STOPPED);
            logger.log(Level.SEVERE, "[QQBot/SnowLuma] 启动失败", e);
            return "启动失败: " + e.getMessage();
        }
    }

    /**
     * 停止 SnowLuma 子进程。
     */
    public String stop() {
        if (process == null || !process.isAlive()) {
            status.set(Status.STOPPED);
            return "SnowLuma 未在运行";
        }
        long pid = process.pid();
        // Windows 下 destroy() 无法杀子进程树，用 taskkill /F /T 强制终止整棵进程树
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                    .redirectErrorStream(true).start().waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warning("[QQBot/SnowLuma] taskkill 失败，尝试 destroyForcibly: " + e.getMessage());
                process.destroyForcibly();
            }
        } else {
            process.destroy();
            try {
                boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        // 等待端口释放
        try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        status.set(Status.STOPPED);
        process = null;
        logger.info("[QQBot/SnowLuma] 进程已停止 (PID: " + pid + ")");
        return "SnowLuma 已停止 (PID: " + pid + ")";
    }

    /**
     * 关闭（模块卸载时调用）。
     */
    public void shutdown() {
        if (process != null && process.isAlive()) {
            stop();
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
