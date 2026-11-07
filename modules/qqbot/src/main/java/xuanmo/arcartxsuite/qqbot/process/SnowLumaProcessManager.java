package xuanmo.arcartxsuite.qqbot.process;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.*;
import java.util.zip.ZipInputStream;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.qqbot.config.QQBotSnowLumaConfig;

/**
 * Optional, single-use process owner. External mode executes no process commands.
 * Native ownership is a retained Process handle; Docker ownership requires both a
 * private local token and matching container labels. Never infer ownership from a port/name.
 */
public final class SnowLumaProcessManager {
    public enum Status { STOPPED, STARTING, RUNNING, INSTALLING }
    private static final String RELEASE_API = "https://api.github.com/repos/SnowLuma/SnowLuma/releases/latest";
    static final String OWNER_LABEL = "io.arcartxsuite.qqbot.owner";
    static final String MANAGED_LABEL = "io.arcartxsuite.qqbot.managed";
    static final String DOCKER_DATA = "/app/data";
    private static final String OWNER_FILE = ".axs-qqbot-owner";
    private static final String NODE_RANGE = "^24.22.1 || >=26.12.1";
    private static final Pattern PASSWORD = Pattern.compile(
        "(?:initial\\s+credentials.*password\\s*=\\s*|临时密码\\s*[:：]\\s*)(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;?\\d]*[a-zA-Z]");
    private static final String EXTERNAL = "external 模式：请在外部管理 OneBot，本模块只连接服务；托管需明确选择 native 或 docker 后重载。";
    private final QQBotSnowLumaConfig config;
    private final Path installDir;
    private final Logger logger;
    private final BooleanSupplier debug;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object launchLock = new Object();
    private final ReentrantLock mutation = new ReentrantLock();
    private final CommandRunner runner;
    private volatile Status status = Status.STOPPED;
    private volatile Process nativeProcess;
    private volatile InputStream download;
    private volatile String owner, phase, webUiPassword;
    private volatile Thread shutdownHook;
    private volatile CompletableFuture<Void> shutdownCompletion = CompletableFuture.completedFuture(null);

    record CommandResult(int code, String output) {}
    @FunctionalInterface interface CommandRunner {
        CommandResult run(List<String> command, Duration timeout, boolean cleanup) throws Exception;
    }

    public SnowLumaProcessManager(Path root, QQBotSnowLumaConfig config, Logger logger,
                                 BooleanSupplier debug, int ignoredPort) {
        this(root, config, logger, debug, null);
    }

    SnowLumaProcessManager(Path root, QQBotSnowLumaConfig config, Logger logger,
                          BooleanSupplier debug, @Nullable CommandRunner runner) {
        this.config = config;
        root = root.toAbsolutePath().normalize();
        installDir = root.resolve(config.dir()).toAbsolutePath().normalize();
        if (config.isManaged() && (!installDir.startsWith(root) || installDir.equals(root)))
            throw new IllegalArgumentException("托管 dir 必须是服务端根目录下的独立子目录");
        this.logger = logger;
        this.debug = debug;
        this.runner = runner == null ? this::execute : runner;
    }

    public void init() {
        if (!config.isManaged() || closed.get()) return;
        if (config.isDocker() && config.dockerAutoInstall() && !isInstalled()) log(installAsync(null).join());
        if (config.autoStart() && !closed.get()) log(start());
    }

    public Status getStatus() {
        if (!config.isManaged() || closed.get()) return Status.STOPPED;
        if (status == Status.INSTALLING || status == Status.STARTING) return status;
        if (config.isNative()) return nativeProcess != null && nativeProcess.isAlive() ? Status.RUNNING : Status.STOPPED;
        try {
            JsonObject info = ownedContainer(false);
            return info != null && info.getAsJsonObject("State").get("Running").getAsBoolean() ? Status.RUNNING : Status.STOPPED;
        } catch (Exception e) { return Status.STOPPED; }
    }

    public boolean isInstalled() {
        if (!config.isManaged()) return false;
        try {
            return config.isDocker() ? ownedContainer(false) != null
                : readOwner() != null && Files.isRegularFile(installDir.resolve("index.mjs"));
        } catch (Exception e) { return false; }
    }

    public String start() {
        if (!config.isManaged()) return EXTERNAL;
        if (closed.get()) return "此托管实例已关闭，请重载模块";
        if (!mutation.tryLock()) return "另一项 SnowLuma 操作正在执行";
        try {
            ensureOpen();
            status = Status.STARTING;
            if (config.isDocker()) {
                requireDockerNetwork();
                JsonObject info = ownedContainer(false);
                if (info == null) return "没有本模块拥有的容器，请先 install；同名外部容器不会被接管";
                if (!info.getAsJsonObject("State").get("Running").getAsBoolean())
                    success(run(List.of("docker", "start", requireOwnedId(false)), Duration.ofSeconds(30), false));
            } else {
                if (nativeProcess != null && nativeProcess.isAlive()) return "托管进程已在运行";
                if (!isInstalled()) return "未找到本模块安装的 SnowLuma，请先 install；外部目录不会被接管";
                Path bundled = installDir.resolve(isWindows() ? "node.exe" : "node");
                String node = Files.isRegularFile(bundled) ? bundled.toString() : "node";
                CommandResult version = run(List.of(node, "--version"), Duration.ofSeconds(10), false);
                if (version.code() != 0 || !supportsNode(version.output().trim(), nodeRange()))
                    return "Node.js 不满足安装包要求 " + nodeRange() + "；请安装相应运行时或 install 完整包";
                ProcessBuilder builder = new ProcessBuilder(node, "index.mjs").directory(installDir.toFile()).redirectErrorStream(true);
                builder.environment().put("SNOWLUMA_DATA", installDir.resolve("data").toString());
                configureEnvironment(builder.environment());
                synchronized (launchLock) { ensureOpen(); nativeProcess = builder.start(); }
                Process started = nativeProcess;
                CompletableFuture.runAsync(() -> consumeOutput(started));
                if (started.waitFor(3, TimeUnit.SECONDS)) return "启动失败，进程已退出；检查 [SnowLuma] 日志";
            }
            ensureOpen();
            registerShutdownHook();
            return "SnowLuma 已启动。WebUI: " + webUiUrl()
                + "\n登录 QQ 后配置对应账号的 Universal 正向 WS，将 access token 填入 onebot.access-token，再重载 QQBot。";
        } catch (Exception e) {
            if (closed.get()) cleanupOwned();
            return failure("启动", e);
        } finally { status = Status.STOPPED; mutation.unlock(); }
    }

    public String stop() {
        if (!config.isManaged()) return EXTERNAL;
        if (closed.get()) return "此托管实例已关闭";
        if (!mutation.tryLock()) return "另一项 SnowLuma 操作正在执行";
        try { ensureOpen(); stopOwned(false); return "本模块托管的 SnowLuma 已停止"; }
        catch (Exception e) { return failure("停止", e); }
        finally { status = Status.STOPPED; mutation.unlock(); }
    }

    /** Non-blocking for the Bukkit thread. This instance can never be enabled again. */
    public void shutdown() {
        synchronized (launchLock) {
            if (!closed.compareAndSet(false, true)) return;
            shutdownCompletion = new CompletableFuture<>();
        }
        unregisterShutdownHook();
        InputStream stream = download;
        if (stream != null) try { stream.close(); } catch (IOException ignored) {}
        Process process = nativeProcess;
        if (process != null) terminate(process);
        status = Status.STOPPED;
        CompletableFuture.runAsync(() -> {
            mutation.lock();
            try {
                if (cleanupOwned()) shutdownCompletion.complete(null);
                else shutdownCompletion.completeExceptionally(new IOException("旧托管进程/容器清理失败，不能启动新实例"));
            } finally { mutation.unlock(); }
        });
    }

    /** Await on the module's startup worker before enabling a replacement manager. */
    public CompletableFuture<Void> shutdownAsync() {
        shutdown();
        synchronized (launchLock) { return shutdownCompletion; }
    }

    public CompletableFuture<String> installAsync(@Nullable Consumer<String> progress) {
        if (!config.isManaged()) return CompletableFuture.completedFuture(EXTERNAL);
        if (closed.get()) return CompletableFuture.completedFuture("此托管实例已关闭");
        return CompletableFuture.supplyAsync(() -> {
            if (!mutation.tryLock()) return "另一项 SnowLuma 操作正在执行";
            try {
                ensureOpen();
                status = Status.INSTALLING;
                return config.isDocker() ? installDocker(progress) : installNative(progress);
            } catch (Exception e) { return failure("安装/更新", e); }
            finally {
                status = Status.STOPPED;
                if (closed.get()) cleanupOwned();
                mutation.unlock();
            }
        });
    }

    private String installDocker(@Nullable Consumer<String> progress) throws Exception {
        requireDockerNetwork();
        ensureOwnerDirectory();
        JsonObject previous = inspectContainer(false);
        if (previous != null && !hasOwnership(previous, owner))
            return "同名容器不属于本模块，已拒绝安装/更新。请选择新的 container-name；不会操作现有容器。";
        report(progress, "拉取 " + config.dockerImage());
        success(run(List.of("docker", "pull", config.dockerImage()), Duration.ofMinutes(10), false));
        ensureOpen();
        String oldImage = previous == null ? null : previous.get("Image").getAsString();
        boolean wasRunning = previous != null && previous.getAsJsonObject("State").get("Running").getAsBoolean();
        if (previous != null) {
            String id = requireOwnedId(false);
            success(run(List.of("docker", "stop", id), Duration.ofSeconds(45), false));
            ensureOpen();
            success(run(List.of("docker", "rm", id), Duration.ofSeconds(30), false));
        }
        try {
            ensureOpen();
            success(run(dockerCreateArgs(config.dockerImage()), Duration.ofSeconds(45), false));
            ensureOpen();
            success(run(List.of("docker", "start", requireOwnedId(false)), Duration.ofSeconds(30), false));
            ensureOpen();
            registerShutdownHook();
            return "Docker 托管容器已" + (previous == null ? "安装" : "更新重建")
                + "，保留命名数据卷。" + bindingDescription() + "\nnoVNC: http://" + browserHost() + ":"
                + config.dockerNovncPort() + "（密码见 logs）\nWebUI: " + webUiUrl()
                + "\n登录 QQ 后配置 Universal WS，将账号 token 填入 onebot.access-token 后重载。";
        } catch (Exception e) {
            if (!closed.get() && oldImage != null) {
                try {
                    JsonObject failed = inspectContainer(false);
                    if (failed != null) {
                        String failedId = requireOwnedId(false);
                        success(run(List.of("docker", "stop", failedId), Duration.ofSeconds(45), false));
                        success(run(List.of("docker", "rm", failedId), Duration.ofSeconds(30), false));
                    }
                    success(run(dockerCreateArgs(oldImage), Duration.ofSeconds(45), false));
                    if (wasRunning) success(run(List.of("docker", "start", requireOwnedId(false)), Duration.ofSeconds(30), false));
                } catch (Exception rollback) { logger.warning("[SnowLuma] 恢复旧镜像失败: " + rollback.getMessage()); }
            }
            throw e;
        }
    }

    List<String> dockerCreateArgs(String image) throws IOException {
        requireDockerNetwork();
        String token = readOwner();
        if (token == null) throw new IOException("缺少本地托管标记");
        List<String> args = new ArrayList<>(List.of("docker", "create", "--name", container(),
            "--label", MANAGED_LABEL + "=true", "--label", OWNER_LABEL + "=" + token,
            "--restart", "unless-stopped", "--shm-size=1g", "--cap-add=SYS_PTRACE", "--security-opt", "seccomp=unconfined",
            "--ulimit", "nofile=65536:1048576",
            "-p", bindingHost() + ":" + config.dockerWebUiPort() + ":5099",
            "-p", bindingHost() + ":" + config.dockerHttpPort() + ":3000",
            "-p", bindingHost() + ":" + config.dockerWsPort() + ":3001",
            "-p", bindingHost() + ":" + config.dockerNovncPort() + ":6081",
            "-v", container() + "-data:" + DOCKER_DATA, "-v", container() + "-qq-config:/app/.config",
            "-v", container() + "-qq-data:/app/.local/share",
            "-e", "SNOWLUMA_WEBUI_HOST=0.0.0.0", "-e", "SNOWLUMA_ONEBOT_HOST=0.0.0.0"));
        Map<String, String> env = new LinkedHashMap<>();
        configureEnvironment(env);
        if (config.dockerVncPassword() != null && !config.dockerVncPassword().isBlank()) env.put("VNC_PASSWD", config.dockerVncPassword());
        env.forEach((key, value) -> { args.add("-e"); args.add(key + "=" + value); });
        args.add(image);
        return List.copyOf(args);
    }

    private String installNative(@Nullable Consumer<String> progress) throws Exception {
        if (nativeProcess != null && nativeProcess.isAlive()) return "请先 stop 托管进程再更新";
        ensureOwnerDirectory();
        JsonObject release = latestRelease();
        String asset = downloadUrl(release, System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        if (asset == null) return "发行版没有当前系统/架构的完整包；不会回退下载其他架构或 Lite 包";
        Path staging = Files.createTempDirectory(installDir.getParent(), ".axs-snowluma-");
        try {
            Path archive = staging.resolve(asset.endsWith(".zip") ? "release.zip" : "release.tar.gz");
            report(progress, "下载 " + release.get("tag_name").getAsString() + " 完整发行包");
            HttpResponse<InputStream> result = http().send(request(asset).timeout(Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (result.statusCode() / 100 != 2) { result.body().close(); throw new IOException("下载 HTTP " + result.statusCode()); }
            download = result.body();
            try (InputStream input = download; OutputStream output = Files.newOutputStream(archive)) {
                byte[] buffer = new byte[65536]; long count = 0, next = 10 * 1048576L; int n;
                while ((n = input.read(buffer)) != -1) {
                    ensureOpen(); output.write(buffer, 0, n); count += n;
                    if (count > 2L * 1024 * 1024 * 1024) throw new IOException("发行包超过 2 GiB");
                    if (count >= next) { report(progress, "已下载 " + count / 1048576 + " MiB"); next += 10 * 1048576L; }
                }
            } finally { download = null; }
            ensureOpen();
            Path content = Files.createDirectory(staging.resolve("content"));
            extract(archive, content);
            if (!Files.exists(content.resolve("index.mjs"))) {
                try (var children = Files.list(content)) {
                    List<Path> list = children.toList();
                    if (list.size() == 1 && Files.isDirectory(list.get(0))) content = list.get(0);
                }
            }
            if (!Files.isRegularFile(content.resolve("index.mjs"))) throw new IOException("发行包缺少 index.mjs，未修改运行时");
            report(progress, "更新运行时，保留 data/config/logs");
            try (var files = Files.walk(content)) {
                for (Path source : files.toList()) {
                    ensureOpen();
                    Path relative = content.relativize(source);
                    if (!relative.toString().isEmpty() && Set.of("data", "config", "logs", OWNER_FILE).contains(relative.getName(0).toString())) continue;
                    Path target = safeEntry(installDir, relative.toString());
                    if (Files.isSymbolicLink(source)) throw new IOException("发行包包含符号链接，拒绝复制");
                    if (Files.isDirectory(source)) Files.createDirectories(target);
                    else Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            return "安装/更新完成。执行 start；Windows 还需相同用户和权限运行受支持的 NTQQ 并扫码。随后配置 Universal WS 和 token。";
        } finally { removeStaging(staging); }
    }

    private void extract(Path archive, Path content) throws Exception {
        if (archive.toString().endsWith(".zip")) {
            try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
                java.util.zip.ZipEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    ensureOpen();
                    Path target = safeEntry(content, entry.getName());
                    if (entry.isDirectory()) Files.createDirectories(target);
                    else { Files.createDirectories(target.getParent()); Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        } else {
            CommandResult listing = run(List.of("tar", "-tzf", archive.toString()), Duration.ofSeconds(30), false);
            success(listing);
            for (String name : listing.output().split("\\R")) if (!name.isBlank()) safeEntry(content, name);
            success(run(List.of("tar", "-xzf", archive.toString(), "-C", content.toString()), Duration.ofSeconds(60), false));
        }
    }

    static Path safeEntry(Path target, String name) throws IOException {
        Path result = target.resolve(name).normalize();
        if (name.startsWith("/") || name.startsWith("\\") || name.matches("^[A-Za-z]:.*") || !result.startsWith(target))
            throw new IOException("发行包路径越界: " + name);
        return result;
    }

    static @Nullable String downloadUrl(JsonObject release, String osName, String architecture) {
        String os = osName.toLowerCase(Locale.ROOT), arch = architecture.toLowerCase(Locale.ROOT), platform;
        if (os.contains("win") && Set.of("amd64", "x86_64", "x64").contains(arch)) platform = "win-x64";
        else if (os.contains("linux") && Set.of("aarch64", "arm64").contains(arch)) platform = "linux-arm64";
        else if (os.contains("linux") && Set.of("amd64", "x86_64", "x64").contains(arch)) platform = "linux-x64";
        else return null;
        if (!release.has("assets")) return null;
        for (var item : release.getAsJsonArray("assets")) {
            String url = item.getAsJsonObject().get("browser_download_url").getAsString(), lower = url.toLowerCase(Locale.ROOT);
            if (url.startsWith("https://github.com/SnowLuma/SnowLuma/releases/download/")
                && lower.contains("-" + platform + ".") && !lower.contains("lite")
                && (lower.endsWith(".zip") || lower.endsWith(".tar.gz"))) return url;
        }
        return null;
    }

    public String logs() {
        if (!config.isManaged()) return EXTERNAL;
        if (!config.isDocker()) return "native 日志见服务端 [SnowLuma]；初始密码请用 password 查询";
        try { return run(List.of("docker", "logs", "--tail", "100", requireOwnedId(false)), Duration.ofSeconds(15), false).output(); }
        catch (Exception e) { return failure("读取日志", e); }
    }

    public String statusReport() {
        if (!config.isManaged()) return EXTERNAL;
        String detail;
        try { detail = config.isDocker() ? (ownedContainer(false) != null ? "容器归属已验证" : "无托管容器；外部同名容器不接管") : "只管理自身创建的进程"; }
        catch (Exception e) { detail = e.getMessage(); }
        return "SnowLuma 模式: " + config.mode() + " | 状态: " + getStatus() + "\n目录: " + installDir
            + "\n" + detail + " | 阶段: " + (phase == null ? "空闲" : phase) + "\nWebUI: " + webUiUrl()
            + "\n" + (config.isDocker() ? bindingDescription() : "本机访问 WebUI；远程使用 SSH 转发。" );
    }

    public String webUiUrl() { return "http://" + (config.isDocker() ? browserHost() : "127.0.0.1") + ":" + (config.isDocker() ? config.dockerWebUiPort() : 5099); }

    private void requireDockerNetwork() throws IOException {
        if (!config.dockerLoopbackBinding() && (config.dockerVncPassword() == null || config.dockerVncPassword().isBlank()))
            throw new IOException("非回环 Docker 绑定必须显式配置 vnc-password；推荐 bind-address: 127.0.0.1 并使用 SSH 转发");
    }

    private String bindingHost() {
        String address = config.dockerBindAddress();
        return address.contains(":") ? "[" + address + "]" : address;
    }

    private String browserHost() {
        String address = config.dockerBindAddress();
        if ("0.0.0.0".equals(address)) return "127.0.0.1";
        if ("0:0:0:0:0:0:0:0".equals(address)) return "[::1]";
        return bindingHost();
    }

    private String bindingDescription() {
        return config.dockerLoopbackBinding()
            ? "Docker 端口仅绑定 " + config.dockerBindAddress() + "；远程使用 SSH 转发。"
            : "Docker 端口显式绑定 " + config.dockerBindAddress() + "；请配置防火墙、访问范围及 OneBot 鉴权。";
    }

    public String webUiPasswordReport() {
        if (!config.isManaged()) return EXTERNAL;
        try {
            String password = webUiPassword;
            if (config.isDocker()) {
                String output = run(List.of("docker", "logs", "--tail", "2000", requireOwnedId(false)), Duration.ofSeconds(15), false).output();
                Matcher matcher = PASSWORD.matcher(ANSI.matcher(output).replaceAll(""));
                while (matcher.find()) password = matcher.group(1);
            }
            return "WebUI: " + webUiUrl() + " | 账号: admin\n" + (password != null
                ? "启动时初始密码: " + password + "（改密后失效；忘记密码可 password reset）"
                : "未捕获初始密码；使用已设置的密码或 password reset。webui-password 仅首次初始化生效。");
        } catch (Exception e) { return failure("读取密码", e); }
    }

    public String resetWebUiPassword() {
        if (!config.isManaged()) return EXTERNAL;
        if (closed.get()) return "此托管实例已关闭";
        if (!mutation.tryLock()) return "另一项 SnowLuma 操作正在执行";
        try {
            ensureOpen();
            String suffix = ".bak-" + System.currentTimeMillis();
            if (config.isDocker()) {
                requireDockerNetwork();
                String id = requireOwnedId(false);
                if (!ownedContainer(false).getAsJsonObject("State").get("Running").getAsBoolean()) return "请先 start 托管容器后重置";
                String auth = DOCKER_DATA + "/config/webui.json";
                success(run(List.of("docker", "exec", id, "mv", auth, auth + suffix), Duration.ofSeconds(15), false));
                success(run(List.of("docker", "restart", requireOwnedId(false)), Duration.ofSeconds(45), false));
                ensureOpen();
                webUiPassword = null;
                return "认证文件已备份、托管容器已重启；稍后 password 查看新初始密码。";
            }
            if (readOwner() == null) return "无本模块安装标记，拒绝修改目录";
            stopOwned(false);
            Path auth = installDir.resolve("data/config/webui.json");
            if (!Files.isRegularFile(auth)) return "认证文件不存在，未重置；请先启动并完成初始化";
            Files.move(auth, auth.resolveSibling(auth.getFileName() + suffix));
            webUiPassword = null;
            return "认证文件已备份，请 start 生成初始密码，再 password 查看。";
        } catch (Exception e) { return failure("重置密码", e); }
        finally { if (closed.get()) cleanupOwned(); mutation.unlock(); }
    }

    public CompletableFuture<String> checkUpdateAsync() {
        if (!config.isManaged()) return CompletableFuture.completedFuture(EXTERNAL);
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureOpen();
                String latest = latestRelease().get("tag_name").getAsString();
                if (config.isDocker()) return "上游发行版: " + latest + " | 配置镜像: " + config.dockerImage()
                    + "。install 拉取配置镜像并重建托管容器，保留命名卷；固定 tag 不会自动改为其他版本。";
                Path pkg = installDir.resolve("package.json");
                String local = Files.exists(pkg) ? JsonParser.parseString(Files.readString(pkg, StandardCharsets.UTF_8)).getAsJsonObject().get("version").getAsString() : "未知";
                return "上游发行版: " + latest + " | 本地: " + local + "。更新请先 stop，再 install。";
            } catch (Exception e) { return failure("版本检查", e); }
        });
    }

    public @Nullable String notReadyHint(boolean localWs) {
        if (!localWs) return null;
        if (!config.isManaged()) return "（external 模式，请检查外部 OneBot Universal WS 地址、token 和运行状态）";
        return getStatus() == Status.RUNNING ? null : "（托管实现端未运行，请查看 /axs qqbot snowluma status）";
    }

    private JsonObject latestRelease() throws Exception {
        ensureOpen();
        HttpResponse<String> result = http().send(request(RELEASE_API).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (result.statusCode() / 100 != 2) throw new IOException("GitHub API HTTP " + result.statusCode());
        ensureOpen();
        return JsonParser.parseString(result.body()).getAsJsonObject();
    }
    private static HttpClient http() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build(); }
    private static HttpRequest.Builder request(String url) { return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "ArcartXSuite-QQBot"); }

    private void ensureOwnerDirectory() throws IOException {
        ensureOpen();
        if (readOwner() != null) return;
        if (Files.exists(installDir)) try (var entries = Files.list(installDir)) {
            if (entries.findAny().isPresent()) throw new IOException("目录非空且无托管标记，拒绝覆盖；请选择新 dir");
        }
        Files.createDirectories(installDir);
        String token = UUID.randomUUID().toString();
        Files.writeString(installDir.resolve(OWNER_FILE), token, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        owner = token;
    }
    private @Nullable String readOwner() throws IOException {
        if (owner != null) return owner;
        Path file = installDir.resolve(OWNER_FILE);
        if (!Files.isRegularFile(file)) return null;
        String token = Files.readString(file, StandardCharsets.UTF_8).trim();
        try { UUID.fromString(token); } catch (IllegalArgumentException e) { throw new IOException("托管标记损坏，拒绝接管", e); }
        owner = token;
        return token;
    }
    static boolean hasOwnership(JsonObject container, @Nullable String expected) {
        if (expected == null || !container.has("Config")) return false;
        JsonObject cfg = container.getAsJsonObject("Config");
        if (!cfg.has("Labels") || cfg.get("Labels").isJsonNull()) return false;
        JsonObject labels = cfg.getAsJsonObject("Labels");
        return labels.has(MANAGED_LABEL) && labels.has(OWNER_LABEL)
            && "true".equals(labels.get(MANAGED_LABEL).getAsString()) && expected.equals(labels.get(OWNER_LABEL).getAsString());
    }
    private @Nullable JsonObject inspectContainer(boolean cleanup) throws Exception {
        CommandResult result = run(List.of("docker", "container", "inspect", container()), Duration.ofSeconds(10), cleanup);
        if (result.code() != 0) {
            String output = result.output().toLowerCase(Locale.ROOT);
            if (output.contains("no such container") || output.contains("no such object")) return null;
            throw new IOException("Docker inspect 失败: " + result.output());
        }
        var array = JsonParser.parseString(result.output()).getAsJsonArray();
        return array.isEmpty() ? null : array.get(0).getAsJsonObject();
    }
    private @Nullable JsonObject ownedContainer(boolean cleanup) throws Exception {
        String token = readOwner();
        if (token == null) return null;
        JsonObject info = inspectContainer(cleanup);
        return info != null && hasOwnership(info, token) ? info : null;
    }
    private String requireOwnedId(boolean cleanup) throws Exception {
        JsonObject info = ownedContainer(cleanup);
        if (info == null) throw new IOException("没有验证通过的托管容器，拒绝操作同名外部容器");
        return info.get("Id").getAsString();
    }
    private void stopOwned(boolean cleanup) throws Exception {
        if (config.isDocker()) {
            JsonObject info = ownedContainer(cleanup);
            if (info != null) success(run(List.of("docker", "stop", info.get("Id").getAsString()), Duration.ofSeconds(45), cleanup));
        } else {
            Process process = nativeProcess;
            if (process != null) {
                terminate(process);
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IOException("托管进程尚未退出");
                nativeProcess = null;
            }
        }
    }
    private boolean cleanupOwned() {
        if (!config.isManaged()) return true;
        try { stopOwned(true); return true; }
        catch (Exception e) { logger.warning("[SnowLuma] 托管清理失败: " + e.getMessage()); return false; }
    }
    private static void terminate(Process process) {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        descendants.forEach(handle -> { if (handle.isAlive()) handle.destroy(); });
        process.destroy();
        descendants.forEach(handle -> { if (handle.isAlive()) handle.destroyForcibly(); });
        if (process.isAlive()) process.destroyForcibly();
    }

    private CommandResult run(List<String> args, Duration timeout, boolean cleanup) throws Exception {
        if (!cleanup) ensureOpen();
        return runner.run(args, timeout, cleanup);
    }
    private CommandResult execute(List<String> args, Duration timeout, boolean cleanup) throws Exception {
        Process process;
        synchronized (launchLock) { if (!cleanup) ensureOpen(); process = new ProcessBuilder(args).redirectErrorStream(true).start(); }
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                StringBuilder buffer = new StringBuilder(); char[] chars = new char[4096]; int n;
                while ((n = reader.read(chars)) != -1) { buffer.append(chars, 0, n); if (buffer.length() > 1048576) buffer.delete(0, buffer.length() - 1048576); }
                return buffer.toString();
            } catch (IOException e) { return "读取命令输出失败"; }
        });
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            boolean daemonMutation = args.size() > 1 && "docker".equals(args.get(0)) && Set.of("create", "start", "restart", "rm", "stop").contains(args.get(1));
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                // Let already accepted daemon mutations settle, then inspect/stop only labelled ownership.
                if ((!cleanup && closed.get() && !daemonMutation) || System.nanoTime() > deadline) {
                    terminate(process); throw new IOException(closed.get() ? "操作已取消" : "命令超时");
                }
            }
            return new CommandResult(process.exitValue(), output.get(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) { terminate(process); Thread.currentThread().interrupt(); throw e; }
    }
    private static void success(CommandResult result) throws IOException {
        if (result.code() != 0) throw new IOException(result.output().isBlank() ? "命令返回 " + result.code() : result.output());
    }
    private String container() {
        if (config.dockerContainerName() == null || !config.dockerContainerName().matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))
            throw new IllegalArgumentException("container-name 格式无效");
        return config.dockerContainerName();
    }
    private void configureEnvironment(Map<String, String> env) {
        if (config.webUiPassword() != null && !config.webUiPassword().isBlank()) env.put("SNOWLUMA_WEBUI_BOOTSTRAP_PASSWORD", config.webUiPassword());
        if (config.acceptAgreements()) { env.put("SNOWLUMA_ACCEPT_EULA", "1"); env.put("SNOWLUMA_ACCEPT_PRIVACY", "1"); }
    }
    private String nodeRange() {
        try {
            JsonObject pkg = JsonParser.parseString(Files.readString(installDir.resolve("package.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            if (pkg.has("engines") && pkg.getAsJsonObject("engines").has("node")) return pkg.getAsJsonObject("engines").get("node").getAsString();
        } catch (Exception ignored) {}
        return NODE_RANGE;
    }
    static boolean supportsNode(String version, String range) {
        Matcher actual = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matcher(version.trim());
        if (!actual.matches()) return false;
        int[] installed = {Integer.parseInt(actual.group(1)), Integer.parseInt(actual.group(2)), Integer.parseInt(actual.group(3))};
        for (String alternative : range.split("\\|\\|")) {
            Matcher constraint = Pattern.compile("^(\\^|>=)?(\\d+)\\.(\\d+)\\.(\\d+)$").matcher(alternative.trim());
            if (!constraint.matches()) continue;
            int[] minimum = {Integer.parseInt(constraint.group(2)), Integer.parseInt(constraint.group(3)), Integer.parseInt(constraint.group(4))};
            int comparison = Arrays.compare(installed, minimum); String op = constraint.group(1);
            if (op == null && comparison == 0 || ">=".equals(op) && comparison >= 0
                || "^".equals(op) && installed[0] == minimum[0] && comparison >= 0) return true;
        }
        return false;
    }
    private void consumeOutput(Process process) {
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = ANSI.matcher(line).replaceAll("");
                Matcher credentials = PASSWORD.matcher(line);
                if (credentials.find()) { webUiPassword = credentials.group(1); log("初始密码已捕获，管理员可用 snowluma password 查询"); }
                else if (debug.getAsBoolean() || line.toLowerCase(Locale.ROOT).matches(".*(error|warn|fatal|failed).*")) log(line);
            }
        } catch (Exception e) { if (!closed.get()) logger.warning("[SnowLuma] 输出读取结束: " + e.getMessage()); }
    }
    private void ensureOpen() throws IOException { if (closed.get()) throw new IOException("此托管实例已关闭，操作已取消"); }
    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
    private void report(@Nullable Consumer<String> listener, String text) {
        phase = text; log(text);
        if (listener != null && !closed.get()) try { listener.accept(text); } catch (Exception ignored) {}
    }
    private void log(String text) { logger.info("[SnowLuma] " + text); }
    private String failure(String action, Exception error) { return action + "失败: " + error.getMessage(); }
    private void registerShutdownHook() {
        synchronized (launchLock) {
            if (closed.get() || shutdownHook != null) return;
            Thread hook = new Thread(() -> { closed.set(true); cleanupOwned(); }, "AXS-SnowLuma-Shutdown");
            try { Runtime.getRuntime().addShutdownHook(hook); shutdownHook = hook; } catch (IllegalStateException ignored) {}
        }
    }
    private void unregisterShutdownHook() {
        Thread hook = shutdownHook; shutdownHook = null;
        if (hook != null) try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException ignored) {}
    }
    private void removeStaging(Path staging) {
        if (!staging.getParent().equals(installDir.getParent()) || !staging.getFileName().toString().startsWith(".axs-snowluma-")) return;
        try (var files = Files.walk(staging)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        } catch (IOException e) { logger.warning("[SnowLuma] 临时目录未能清理: " + staging); }
    }
}
