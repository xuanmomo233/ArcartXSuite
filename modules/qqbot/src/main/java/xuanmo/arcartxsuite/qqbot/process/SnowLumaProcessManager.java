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
import xuanmo.arcartxsuite.qqbot.config.QQBotSnowLumaConfig;

public final class SnowLumaProcessManager {
    public enum Status { STOPPED, STARTING, RUNNING, INSTALLING }
    private static final String GITHUB_API = "https://api.github.com/repos/SnowLuma/SnowLuma/releases/latest";
    private final QQBotSnowLumaConfig config;
    private final Path installDir;
    private final Logger logger;
    private final BooleanSupplier debugMode;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.STOPPED);
    private volatile Process process;
    private Thread outputThread;

    public SnowLumaProcessManager(Path serverRoot, QQBotSnowLumaConfig config, Logger logger, BooleanSupplier debugMode) {
        this.config = config;
        this.installDir = serverRoot.resolve(config.dir());
        this.logger = logger;
        this.debugMode = debugMode;
    }

    public void init() {
        if (!config.autoStart()) return;
        if (config.isDocker()) {
            if (isInstalled()) { start(); }
            else { logger.info("[QQBot/SnowLuma] Docker: 未检测到容器 '" + config.dockerContainerName() + "'，请执行 install"); }
        } else {
            if (isInstalled()) { start(); }
            else { logger.info("[QQBot/SnowLuma] 未检测到安装目录，请执行 install"); }
        }
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
        if (config.isDocker()) { if (dockerContainerRunning()) stopDocker(); return; }
        if (process != null && process.isAlive()) stopNative();
        else { String c = killResidue(); if (c != null) logger.info("[QQBot/SnowLuma] 卸载清理残留: " + c); }
    }
    public CompletableFuture<String> installAsync() { return config.isDocker() ? installDockerAsync() : installNativeAsync(); }
    public String logs() { return config.isDocker() ? dockerLogs() : "仅 Docker 模式支持日志查看"; }
    public String statusReport() { return config.isDocker() ? statusReportDocker() : statusReportNative(); }

    // --- Docker ---
    private String startDocker() {
        if (dockerContainerRunning()) return "SnowLuma Docker 容器已在运行中";
        if (!dockerContainerExists()) return "Docker 容器 '" + config.dockerContainerName() + "' 不存在，请先 install";
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "start", config.dockerContainerName());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (ok && p.exitValue() == 0) return "SnowLuma Docker 容器已启动，WebUI: http://127.0.0.1:" + config.dockerWebUiPort();
            return "启动失败: " + readAll(p.getInputStream());
        } catch (Exception e) { logger.log(Level.SEVERE, "[QQBot/SnowLuma] Docker start", e); return "启动失败: " + e.getMessage(); }
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
        } catch (Exception e) { logger.log(Level.SEVERE, "[QQBot/SnowLuma] Docker stop", e); return "停止失败: " + e.getMessage(); }
    }

    private CompletableFuture<String> installDockerAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("[QQBot/SnowLuma] docker pull " + config.dockerImage());
                ProcessBuilder pull = new ProcessBuilder("docker", "pull", config.dockerImage());
                pull.redirectErrorStream(true);
                Process pp = pull.start();
                if (!pp.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) || pp.exitValue() != 0)
                    return "镜像拉取失败: " + readAll(pp.getInputStream());
                if (dockerContainerExists()) return "容器已存在，执行 start 启动";
                ProcessBuilder run = new ProcessBuilder("docker","run","-d",
                    "--name", config.dockerContainerName(),"--restart","unless-stopped","--shm-size=1g",
                    "--cap-add=SYS_PTRACE","--security-opt","seccomp=unconfined",
                    "-p", config.dockerWebUiPort()+":5099","-p",config.dockerHttpPort()+":3000","-p",config.dockerWsPort()+":3001",
                    "-v",config.dockerContainerName()+"-data:/app/snowluma-data",
                    "-v",config.dockerContainerName()+"-qq-config:/app/.config",
                    "-v",config.dockerContainerName()+"-qq-data:/app/.local/share",
                    config.dockerImage());
                run.redirectErrorStream(true);
                Process pr = run.start();
                if (!pr.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || pr.exitValue() != 0)
                    return "容器创建失败: " + readAll(pr.getInputStream());
                return "Docker 容器已创建。请通过 noVNC (http://<VPS_IP>:6081) 登录 QQ，然后通过 WebUI 管理。";
            } catch (Exception e) { logger.log(Level.SEVERE, "[QQBot/SnowLuma] Docker install", e); return "安装失败: " + e.getMessage(); }
        });
    }

    private String dockerLogs() {
        if (!dockerContainerExists()) return "容器不存在";
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "logs", "--tail", "50", config.dockerContainerName());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean ok = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            String out = readAll(p.getInputStream());
            if (!ok) out += "\n(读取超时)";
            return out.isBlank() ? "(无日志)" : out;
        } catch (Exception e) { return "读取日志失败: " + e.getMessage(); }
    }

    private String statusReportDocker() {
        boolean exists = dockerContainerExists(), running = dockerContainerRunning();
        String localVer = getLocalVersion();
        StringBuilder sb = new StringBuilder("§6[SnowLuma Docker 状态]\n");
        sb.append("§7  容器名: §f").append(config.dockerContainerName()).append("\n");
        sb.append("§7  镜像: §f").append(config.dockerImage()).append("\n");
        sb.append("§7  本地版本: §f").append(localVer != null ? localVer : "未知").append("\n");
        sb.append("§7  容器存在: §f").append(exists?"是":"否").append("\n");
        sb.append("§7  运行状态: §f").append(running?"运行中":"已停止").append("\n");
        if (running) {
            sb.append("§7  WebUI: §fhttp://127.0.0.1:").append(config.dockerWebUiPort()).append("\n");
            sb.append("§7  WS端口: §f").append(config.dockerWsPort()).append("\n");
        }
        sb.append("§7  自动启动: §f").append(config.autoStart()?"是":"否");
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
        if (!isInstalled()) return "SnowLuma 未安装，请先 install";
        String cleanup = killResidue(); if (cleanup!=null) logger.info("[QQBot/SnowLuma] "+cleanup);
        status.set(Status.STARTING);
        try {
            ProcessBuilder pb = buildProcess();
            pb.directory(installDir.toFile()); pb.redirectErrorStream(true);
            process = pb.start(); final long thisPid = process.pid();
            AtomicReference<String> fatal = new AtomicReference<>();
            outputThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line; while ((line=r.readLine())!=null) {
                        String l = line.toLowerCase();
                        if (l.contains("eaddrinuse")||l.contains("address already in use")) {
                            fatal.set("端口冲突: "+line); logger.severe("[SnowLuma] "+line);
                        } else if (l.contains("error")||l.contains("warn")||l.contains("fail")) {
                            logger.warning("[SnowLuma] "+line);
                        } else if (debugMode.getAsBoolean()) logger.info("[SnowLuma] "+line);
                    }
                } catch (IOException ignored) {}
            }, "AXS-SnowLuma-Output");
            outputThread.setDaemon(true); outputThread.start();
            try { Thread.sleep(3000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            if (fatal.get()!=null) { forceKill(thisPid); status.set(Status.STOPPED); process=null; return "启动失败: "+fatal.get()+"\n检查是否有其他进程占用 5099/3001"; }
            if (!process.isAlive()) { status.set(Status.STOPPED); process=null; return "启动失败: 进程立即退出"; }
            status.set(Status.RUNNING);
            logger.info("[QQBot/SnowLuma] 已启动 (PID: "+thisPid+")");
            return "SnowLuma 已启动 (PID: "+thisPid+")，WebUI: http://127.0.0.1:5099";
        } catch (Exception e) { status.set(Status.STOPPED); logger.log(Level.SEVERE,"[QQBot/SnowLuma] 启动失败",e); return "启动失败: "+e.getMessage(); }
    }

    private String stopNative() {
        if (process==null||!process.isAlive()) { String c=killResidue(); status.set(Status.STOPPED); return c!=null?"SnowLuma 已强制停止残留: "+c:"SnowLuma 未在运行"; }
        long pid=process.pid(); forceKill(pid); String c=killResidue(); status.set(Status.STOPPED); process=null;
        logger.info("[QQBot/SnowLuma] 已停止 (PID: "+pid+")");
        String m="SnowLuma 已停止 (PID: "+pid+")"; if (c!=null) m+="，清理残留: "+c; return m;
    }

    private String killResidue() {
        StringBuilder killed = new StringBuilder();
        for (int port : new int[]{5099,3001}) {
            long pid = findPidOnPort(port);
            if (pid>0) {
                if (process!=null && process.isAlive() && process.pid()==pid) continue;
                if (isSnowLumaOrNodeProcess(pid)) {
                    logger.warning("[QQBot/SnowLuma] 端口 "+port+" 被 PID "+pid+" 占用，强制终止残留");
                    forceKill(pid); if (killed.length()>0) killed.append("; "); killed.append("PID ").append(pid).append(" (端口 ").append(port).append(")");
                } else { logger.warning("[QQBot/SnowLuma] 端口 "+port+" 被 PID "+pid+" 占用，但不是 SnowLuma/Node，请手动处理"); }
            }
        }
        if (killed.length()>0) { try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } return killed.toString(); }
        return null;
    }

    private void forceKill(long pid) {
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

    private CompletableFuture<String> installNativeAsync() {
        if (status.get()==Status.INSTALLING) return CompletableFuture.completedFuture("已在安装中");
        if (status.get()==Status.RUNNING) return CompletableFuture.completedFuture("请先停止再安装");
        status.set(Status.INSTALLING); logger.info("[QQBot/SnowLuma] 开始从 GitHub 下载...");
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpRequest apiReq = HttpRequest.newBuilder().uri(URI.create(GITHUB_API)).header("User-Agent","ArcartXSuite-QQBot").GET().build();
                HttpResponse<String> apiResp = client.send(apiReq, HttpResponse.BodyHandlers.ofString());
                String downloadUrl = extractDownloadUrl(apiResp.body());
                if (downloadUrl==null) { status.set(Status.STOPPED); return "未找到可用的 Release"; }
                logger.info("[QQBot/SnowLuma] 下载: "+downloadUrl);
                String ext = downloadUrl.toLowerCase().endsWith(".tar.gz") ? ".tar.gz" : ".zip";
                Path archive = installDir.getParent().resolve("snowluma-download"+ext); Files.createDirectories(installDir);
                HttpRequest dlReq = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).header("User-Agent","ArcartXSuite-QQBot").GET().build();
                client.send(dlReq, HttpResponse.BodyHandlers.ofFile(archive));
                logger.info("[QQBot/SnowLuma] 解压中..."); extractArchive(archive, installDir); Files.deleteIfExists(archive);
                flattenIfNeeded(); status.set(Status.STOPPED);
                logger.info("[QQBot/SnowLuma] 安装完成"); return "安装完成！执行 start 启动";
            } catch (Exception e) { status.set(Status.STOPPED); logger.log(Level.SEVERE,"[QQBot/SnowLuma] 安装失败",e); return "安装失败: "+e.getMessage(); }
        });
    }

    private String statusReportNative() {
        Status s = getStatus(); String localVer = getLocalVersion();
        StringBuilder sb = new StringBuilder("§6[SnowLuma 状态]\n");
        sb.append("§7  安装目录: §f").append(installDir).append("\n");
        sb.append("§7  本地版本: §f").append(localVer != null ? localVer : "未知").append("\n");
        sb.append("§7  已安装: §f").append(isInstalled()?"是":"否").append("\n");
        sb.append("§7  状态: §f").append(s.name()).append("\n");
        if (s==Status.RUNNING && process!=null) sb.append("§7  PID: §f").append(process.pid()).append("\n");
        sb.append("§7  自动启动: §f").append(config.autoStart()?"是":"否"); return sb.toString();
    }

    private ProcessBuilder buildProcess() {
        Path launcher = getLauncher(); String os = System.getProperty("os.name","").toLowerCase();
        if (os.contains("win")) return new ProcessBuilder("cmd","/c",launcher.getFileName().toString());
        String fn = launcher.getFileName().toString();
        if (fn.endsWith(".sh")) return new ProcessBuilder("/bin/bash",fn);
        if (fn.endsWith(".mjs") || fn.endsWith(".js")) return new ProcessBuilder("node",fn);
        return new ProcessBuilder("/bin/bash",fn);
    }

    private Path getLauncher() {
        String os = System.getProperty("os.name","").toLowerCase();
        if (os.contains("win")) { Path bat=installDir.resolve("launcher.bat"); if (Files.exists(bat)) return bat; }
        else { Path sh=installDir.resolve("launcher.sh"); if (Files.exists(sh)) return sh; }
        Path mjs=installDir.resolve("index.mjs"); if (Files.exists(mjs)) return mjs;
        Path js=installDir.resolve("index.js"); if (Files.exists(js)) return js;
        return installDir.resolve("launcher.bat");
    }

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

        String url = findUrl(json, precisePlatform, true);  if (url!=null) return url;
        url = findUrl(json, precisePlatform, false);          if (url!=null) return url;

        // fallback: 模糊匹配（兼容旧命名或边缘平台）
        String loose = os.contains("win") ? "win" : "linux";
        url = findUrl(json, loose, true);  if (url!=null) return url;
        url = findUrl(json, loose, false); if (url!=null) return url;

        return findUrl(json, null, false);
    }

    private String findUrl(String json, String platformKeyword, boolean preferLite) {
        int idx = json.indexOf("browser_download_url");
        while (idx!=-1) {
            int start=json.indexOf("\"", idx+22)+1; int end=json.indexOf("\"", start);
            if (start>0 && end>start) {
                String url=json.substring(start,end); String lower=url.toLowerCase();
                boolean isArchive = lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz");
                if (isArchive) {
                    boolean platformMatch = platformKeyword==null || lower.contains(platformKeyword);
                    // x64 平台排除 arm64，防止误匹配
                    if (platformKeyword!=null && (platformKeyword.equals("win-x64")||platformKeyword.equals("linux-x64"))) {
                        platformMatch = platformMatch && !lower.contains("arm64");
                    }
                    boolean liteMatch = !preferLite || lower.contains("lite");
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
        if (idx == -1) idx = json.indexOf("\"tag_name\"");
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
