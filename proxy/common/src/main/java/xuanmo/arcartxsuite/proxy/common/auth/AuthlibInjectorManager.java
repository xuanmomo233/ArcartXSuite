package xuanmo.arcartxsuite.proxy.common.auth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * authlib-injector 全局管理器（代理端版本）：版本检测、自动下载、启动脚本生成、更新提醒。
 * <p>
 * 供 BungeeCord / Velocity 代理端共用。与单端版本的区别：
 * <ul>
 *   <li>不依赖 Bukkit API（无 ChatColor，命令返回纯文本由平台插件自行着色）</li>
 *   <li>生成的启动脚本启动 BungeeCord / Velocity 而非 Paper</li>
 *   <li>检测代理端 jar（BungeeCord / Velocity）而非 Paper jar</li>
 * </ul>
 */
public final class AuthlibInjectorManager {

    private static final String LATEST_API = "https://authlib-injector.yushi.moe/artifact/latest.json";
    private static final String AGENT_CLASS = "moe.yushi.authlibinjector.AuthlibInjector";
    private static final String JAR_NAME = "authlib-injector.jar";

    private static final String JAVA17_OPENS =
        "--add-opens java.base/java.lang=ALL-UNNAMED "
        + "--add-opens java.base/java.net=ALL-UNNAMED "
        + "--add-opens java.base/sun.net.util=ALL-UNNAMED";

    /** 混合代理独立进程入口类（proxy:common 版本）。 */
    private static final String PROXY_MAIN_CLASS =
        "xuanmo.arcartxsuite.proxy.common.auth.MixedYggdrasilProxy";

    private static final int DEFAULT_PROXY_PORT = 25599;

    /** 代理端平台类型，决定生成脚本时启动哪个 jar。 */
    public enum Platform {
        BUNGEECORD("BungeeCord"),
        VELOCITY("Velocity");

        private final String displayName;
        Platform(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    private final Logger logger;
    private final File dataFolder;
    private final File serverRoot;
    private final int mixedProxyPort;
    private final Platform platform;
    /** 混合代理绑定地址（单机 127.0.0.1，跨机由配置指定）。 */
    private final String mixedProxyBindHost;
    /** account-source 接口鉴权 token（空表示不校验）。 */
    private final String mixedProxyToken;
    /** 允许访问 account-source 的客户端 IP 白名单。 */
    private final java.util.List<String> mixedProxyAllowIps;

    public AuthlibInjectorManager(Logger logger, File dataFolder, Platform platform) {
        this(logger, dataFolder, DEFAULT_PROXY_PORT, "127.0.0.1", null, null, platform);
    }

    public AuthlibInjectorManager(Logger logger, File dataFolder, int mixedProxyPort, Platform platform) {
        this(logger, dataFolder, mixedProxyPort, "127.0.0.1", null, null, platform);
    }

    public AuthlibInjectorManager(Logger logger, File dataFolder, int mixedProxyPort,
                                  String bindHost, String token, java.util.Collection<String> allowIps,
                                  Platform platform) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.serverRoot = resolveServerRoot(dataFolder);
        this.mixedProxyPort = (mixedProxyPort > 0 && mixedProxyPort <= 65535) ? mixedProxyPort : DEFAULT_PROXY_PORT;
        this.mixedProxyBindHost = (bindHost == null || bindHost.isBlank()) ? "127.0.0.1" : bindHost.trim();
        this.mixedProxyToken = (token == null || token.isBlank()) ? null : token.trim();
        this.mixedProxyAllowIps = (allowIps == null) ? java.util.List.of()
            : allowIps.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        this.platform = platform;
    }

    // ═════════════════════════════════════════════════════════════════
    //  检测
    // ═════════════════════════════════════════════════════════════════

    public boolean isAgentLoaded() {
        try {
            Class.forName(AGENT_CLASS);
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.contains("-javaagent") && arg.toLowerCase().contains("authlib-injector")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 检测 authlib-injector 版本并提示更新。
     */
    public void checkVersionAndNotify() {
        if (!isAgentLoaded()) {
            printNotLoadedWarning();
            return;
        }

        String latestVersion = fetchLatestVersion();
        if (latestVersion == null) {
            logger.warning("[AuthlibInjector] 无法获取最新版本信息。");
            return;
        }

        String localVersion = detectLocalVersion();
        if (localVersion == null) {
            logger.info("[AuthlibInjector] 当前版本未知，最新版本: " + latestVersion);
            return;
        }

        if (!latestVersion.equals(localVersion)) {
            logger.warning("");
            logger.warning("  [AuthlibInjector] 检测到新版本!");
            logger.warning("    当前版本: " + localVersion);
            logger.warning("    最新版本: " + latestVersion);
            logger.warning("    请执行 /axsproxy auth update 自动更新");
            logger.warning("    或手动下载: https://github.com/yushijinhun/authlib-injector/releases");
            logger.warning("");
        } else {
            logger.info("[AuthlibInjector] 当前已是最新版本: " + localVersion);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  下载 / 更新
    // ═════════════════════════════════════════════════════════════════

    public File downloadOrUpdate() {
        File targetFile = new File(dataFolder, JAR_NAME);
        String latestUrl = fetchLatestDownloadUrl();
        if (latestUrl == null) {
            logger.severe("[AuthlibInjector] 无法获取最新版本下载链接。");
            return null;
        }
        try {
            logger.info("[AuthlibInjector] 正在下载最新版: " + latestUrl);
            downloadFile(latestUrl, targetFile);
            logger.info("[AuthlibInjector] 下载完成: " + targetFile.getAbsolutePath());
            return targetFile;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[AuthlibInjector] 下载失败", e);
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  启动脚本
    // ═════════════════════════════════════════════════════════════════

    public boolean generateStartScripts(String yggdrasilUrl, String proxyJarName, boolean useLocalMixed) {
        File authlibJar = new File(dataFolder, JAR_NAME);
        if (!authlibJar.exists()) {
            logger.warning("[AuthlibInjector] 未找到 authlib-injector.jar，请先执行下载。");
            return false;
        }
        String agentPath = getRelativePath(authlibJar);
        String axsJarPath = resolveProxyJarPath();
        try {
            generateBatScript(agentPath, yggdrasilUrl, proxyJarName, useLocalMixed, axsJarPath);
            generateShScript(agentPath, yggdrasilUrl, proxyJarName, useLocalMixed, axsJarPath);
            logger.info("[AuthlibInjector] 启动脚本已生成: start-mixed-auth.bat / start-mixed-auth.sh");
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[AuthlibInjector] 生成启动脚本失败", e);
            return false;
        }
    }

    /**
     * 构造 MixedYggdrasilProxy 独立进程启动参数片段（--bind / --token / --allow-ips）。
     * 仅跨机模式（bindHost 非 127.0.0.1）时附加 token / allow-ips。
     */
    private String buildProxyArgs() {
        StringBuilder sb = new StringBuilder();
        sb.append(" --bind ").append(mixedProxyBindHost);
        if (mixedProxyToken != null) {
            sb.append(" --token ").append(mixedProxyToken);
        }
        if (!mixedProxyAllowIps.isEmpty()) {
            sb.append(" --allow-ips ").append(String.join(",", mixedProxyAllowIps));
        }
        return sb.toString();
    }

    /** 本地混合代理监听端口（固定，由 config 注入）。 */
    public int getMixedProxyPort() {
        return mixedProxyPort;
    }

    /** 混合代理 URL（authlib-injector 指向此地址）。 */
    public String getMixedProxyUrl() {
        return "http://" + mixedProxyBindHost + ":" + mixedProxyPort;
    }

    /**
     * 检测混合代理是否已就绪（由 start-mixed-auth 脚本独立进程启动）。
     * <p>
     * 注意：代理是独立进程，不由本插件启动/停止，这里只做连通性探测。
     */
    public boolean isMixedProxyReachable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(mixedProxyBindHost, mixedProxyPort), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 一键配置：下载 + 生成脚本。
     * <p>
     * 若 yggdrasilUrl 包含 "?mixed"，自动替换为本地混合代理地址，
     * 绕过 authlib-injector / LittleSkin ?mixed 的兼容性问题。
     *
     * @return 成功提示文本（纯文本，由平台插件着色）；失败返回 null
     */
    public String setupAll(String yggdrasilUrl) {
        // ?mixed 是 AXS 用来识别「启用本地混合代理」的开关（并非 authlib-injector 的有效参数）
        boolean useLocalMixed = yggdrasilUrl != null && yggdrasilUrl.contains("?mixed");
        String effectiveUrl;
        if (useLocalMixed) {
            // authlib-injector 指向本地代理（固定端口），代理由脚本在代理端启动前独立拉起
            effectiveUrl = getMixedProxyUrl();
            logger.info("[AuthlibInjector] 混合登录：脚本将先独立启动本地代理（端口 "
                + mixedProxyPort + "），authlib-injector 指向 " + effectiveUrl);
        } else {
            effectiveUrl = yggdrasilUrl;
        }

        File jar = downloadOrUpdate();
        if (jar == null) {
            return null;
        }
        String proxyJar = detectProxyJar();
        if (!generateStartScripts(effectiveUrl, proxyJar, useLocalMixed)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("authlib-injector 配置完成！\n");
        sb.append("  jar: ").append(jar.getAbsolutePath()).append("\n");
        sb.append("  启动脚本: start-mixed-auth.bat / start-mixed-auth.sh\n");
        sb.append("  代理端平台: ").append(platform.displayName).append("\n");
        if (useLocalMixed) {
            sb.append("  [混合登录] 脚本会先启动混合代理（").append(mixedProxyBindHost).append(":").append(mixedProxyPort)
              .append("）再启动 ").append(platform.displayName).append("，支持 LittleSkin + 微软正版\n");
            sb.append("  [重要] 本模式必须使用 start-mixed-auth 脚本启动，\n");
            sb.append("         不能只在原脚本加 -javaagent（代理需先于代理端启动）。\n");
            if (!"127.0.0.1".equals(mixedProxyBindHost)) {
                sb.append("  [跨机模式] 代理监听 ").append(mixedProxyBindHost).append("，后端子服可跨机连接\n");
                if (mixedProxyToken != null) {
                    sb.append("  [鉴权] account-source 已启用 token 鉴权\n");
                }
                if (!mixedProxyAllowIps.isEmpty()) {
                    sb.append("  [鉴权] account-source IP 白名单: ").append(String.join(",", mixedProxyAllowIps)).append("\n");
                }
            }
        }
        sb.append("\n  请执行以下操作:\n");
        sb.append("  1. 关闭 ").append(platform.displayName).append(" 代理端\n");
        sb.append("  2. 使用 start-mixed-auth.bat (Windows) 或 start-mixed-auth.sh (Linux) 启动\n");
        if (useLocalMixed) {
            sb.append("\n  [后端子服配置]\n");
            sb.append("  后端 Paper 子服的 AXS config.yml 中设置：\n");
            sb.append("    auth.enabled: true\n");
            sb.append("    auth.yggdrasil-source: \"https://littleskin.cn/api/yggdrasil?mixed\"\n");
            sb.append("    auth.mixed-proxy-host: ").append(mixedProxyBindHost).append("\n");
            sb.append("    auth.mixed-proxy-port: ").append(mixedProxyPort).append("\n");
            if (mixedProxyToken != null) {
                sb.append("    auth.mixed-proxy-token: \"").append(mixedProxyToken).append("\"\n");
            }
            sb.append("  后端 AccountTypeServiceImpl 会自动查询本代理获取权威账号类型。\n");
            if ("127.0.0.1".equals(mixedProxyBindHost)) {
                sb.append("  注意：后端子服必须与本代理端在同一台机器（127.0.0.1）。\n");
                sb.append("  跨机部署请在 proxy-config.yml 配置 mixed-proxy-bind-host / token / allow-ips。\n");
            }
        }
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════
    //  内部辅助
    // ═════════════════════════════════════════════════════════════════

    private void printNotLoadedWarning() {
        logger.warning("");
        logger.warning("  [AuthlibInjector] 未检测到 authlib-injector JVM Agent!");
        logger.warning("    若需支持 LittleSkin + 微软正版 多方认证，请执行:");
        logger.warning("      /axsproxy auth setup");
        logger.warning("    该命令会自动下载并生成启动脚本。");
        logger.warning("");
    }

    private String detectLocalVersion() {
        File jar = new File(dataFolder, JAR_NAME);
        if (!jar.exists()) return null;
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar)) {
            java.util.jar.Manifest manifest = jf.getManifest();
            if (manifest != null) {
                String v = manifest.getMainAttributes().getValue("Implementation-Version");
                if (v != null) return v;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private String fetchLatestVersion() {
        String json = fetchJson(LATEST_API);
        if (json == null) return null;
        return JsonParseUtil.getStringField(json, "version", logger, false);
    }

    private String fetchLatestDownloadUrl() {
        String json = fetchJson(LATEST_API);
        if (json == null) return null;
        return JsonParseUtil.getStringField(json, "download_url", logger, false);
    }

    private String fetchJson(String urlString) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "ArcartXSuite");
            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void downloadFile(String urlString, File target) throws IOException {
        if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "ArcartXSuite");
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
    }

    private void generateBatScript(String agentPath, String yggdrasilUrl, String proxyJar,
                                   boolean useLocalMixed, String axsJarPath) throws IOException {
        File script = new File(serverRoot, "start-mixed-auth.bat");
        StringBuilder c = new StringBuilder();
        c.append("@echo off\r\n");
        c.append("cd /d \"%~dp0\"\r\n");
        c.append("title ").append(platform.displayName).append(" (Mixed Auth Mode)\r\n");
        if (useLocalMixed) {
            String proxyArgs = buildProxyArgs();
            c.append("set \"PROXY_PORT=").append(mixedProxyPort).append("\"\r\n");
            c.append("set \"PROXY_HOST=").append(mixedProxyBindHost).append("\"\r\n");
            c.append("echo [ArcartXSuite] Starting AXS mixed-auth proxy on %PROXY_HOST%:%PROXY_PORT% ...\r\n");
            c.append("start \"AXS Mixed-Auth Proxy\" /min java -cp \"").append(axsJarPath)
             .append("\" ").append(PROXY_MAIN_CLASS).append(" %PROXY_PORT%").append(proxyArgs).append("\r\n");
            c.append("echo [ArcartXSuite] Waiting for proxy to be ready ...\r\n");
            c.append("set /a _tries=0\r\n");
            c.append(":axs_waitproxy\r\n");
            c.append("powershell -NoProfile -Command \"$c=New-Object Net.Sockets.TcpClient;try{$c.Connect('%PROXY_HOST%',%PROXY_PORT%);$c.Close();exit 0}catch{exit 1}\" >nul 2>&1\r\n");
            c.append("if not errorlevel 1 goto axs_proxyready\r\n");
            c.append("set /a _tries+=1\r\n");
            c.append("if %_tries% geq 30 (echo [ArcartXSuite][WARN] proxy not ready, continuing anyway & goto axs_proxyready)\r\n");
            c.append("ping -n 2 127.0.0.1 >nul\r\n");
            c.append("goto axs_waitproxy\r\n");
            c.append(":axs_proxyready\r\n");
            c.append("echo [ArcartXSuite] Proxy ready. Launching ").append(platform.displayName).append(" ...\r\n");
        } else {
            c.append("echo [ArcartXSuite] Starting with authlib-injector ...\r\n");
        }
        if (platform == Platform.VELOCITY) {
            // Velocity 用 -p 指定插件目录，nogui 参数不适用
            c.append("java ").append(JAVA17_OPENS).append(" -javaagent:").append(agentPath).append("=").append(yggdrasilUrl)
             .append(" -Xmx2G -Xms512M -jar ").append(proxyJar).append("\r\n");
        } else {
            // BungeeCord 支持 nogui 参数
            c.append("java ").append(JAVA17_OPENS).append(" -javaagent:").append(agentPath).append("=").append(yggdrasilUrl)
             .append(" -Xmx2G -Xms512M -jar ").append(proxyJar).append(" nogui\r\n");
        }
        c.append("pause\r\n");
        Files.writeString(script.toPath(), c.toString(), StandardCharsets.UTF_8);
    }

    private void generateShScript(String agentPath, String yggdrasilUrl, String proxyJar,
                                  boolean useLocalMixed, String axsJarPath) throws IOException {
        File script = new File(serverRoot, "start-mixed-auth.sh");
        StringBuilder c = new StringBuilder();
        c.append("#!/bin/bash\n");
        c.append("cd \"$(dirname \"$0\")\"\n");
        if (useLocalMixed) {
            String proxyArgs = buildProxyArgs();
            c.append("PROXY_PORT=").append(mixedProxyPort).append("\n");
            c.append("PROXY_HOST=").append(mixedProxyBindHost).append("\n");
            c.append("echo \"[ArcartXSuite] Starting AXS mixed-auth proxy on $PROXY_HOST:$PROXY_PORT ...\"\n");
            c.append("java -cp \"").append(axsJarPath).append("\" ").append(PROXY_MAIN_CLASS).append(" $PROXY_PORT").append(proxyArgs).append(" &\n");
            c.append("AXS_PROXY_PID=$!\n");
            c.append("echo \"[ArcartXSuite] Waiting for proxy to be ready ...\"\n");
            c.append("_tries=0\n");
            c.append("until bash -c \"echo > /dev/tcp/$PROXY_HOST/$PROXY_PORT\" 2>/dev/null; do\n");
            c.append("  _tries=$((_tries+1))\n");
            c.append("  if [ $_tries -ge 30 ]; then echo \"[ArcartXSuite][WARN] proxy not ready, continuing\"; break; fi\n");
            c.append("  sleep 1\n");
            c.append("done\n");
            c.append("echo \"[ArcartXSuite] Proxy ready. Launching ").append(platform.displayName).append(" ...\"\n");
        } else {
            c.append("echo \"[ArcartXSuite] Starting with authlib-injector ...\"\n");
        }
        if (platform == Platform.VELOCITY) {
            c.append("java ").append(JAVA17_OPENS).append(" -javaagent:").append(agentPath).append("=").append(yggdrasilUrl)
             .append(" -Xmx2G -Xms512M -jar ").append(proxyJar).append("\n");
        } else {
            c.append("java ").append(JAVA17_OPENS).append(" -javaagent:").append(agentPath).append("=").append(yggdrasilUrl)
             .append(" -Xmx2G -Xms512M -jar ").append(proxyJar).append(" nogui\n");
        }
        if (useLocalMixed) {
            c.append("kill $AXS_PROXY_PID 2>/dev/null\n");
        }
        Files.writeString(script.toPath(), c.toString(), StandardCharsets.UTF_8);
        script.setExecutable(true);
    }

    /**
     * 解析 proxy 插件自身 jar 的相对路径（用于独立进程代理的 classpath）。
     * <p>
     * {@code CodeSource.getLocation()} 在自定义/内嵌类加载器下可能为 null 或指向
     * 非 file: 协议 URL，按「CodeSource（判空）→ 插件描述资源 URL 反推 →
     * plugins 目录扫描 → 固定文件名兜底」逐级回退。
     */
    private String resolveProxyJarPath() {
        // 1) 自身 CodeSource（逐层判空 + 仅接受 file: 协议）
        try {
            java.security.CodeSource cs = getClass().getProtectionDomain().getCodeSource();
            URL loc = (cs != null) ? cs.getLocation() : null;
            if (loc != null && "file".equals(loc.getProtocol())) {
                File jar = new File(loc.toURI());
                if (jar.isFile()) {
                    return getRelativePath(jar);
                }
            }
        } catch (Exception ignored) {
        }

        // 2) 插件描述资源 URL 反推（jar:file:/x.jar!/desc → !/ 前的 file: 部分）
        try {
            ClassLoader cl = getClass().getClassLoader();
            String[] descriptors = {"bungee.yml", "velocity-plugin.json", "proxy-config.yml", "plugin.yml"};
            for (String desc : descriptors) {
                URL res = (cl != null) ? cl.getResource(desc) : ClassLoader.getSystemResource(desc);
                File jar = jarFileFromResourceUrl(res);
                if (jar != null && jar.isFile()) {
                    return getRelativePath(jar);
                }
            }
        } catch (Exception ignored) {
        }

        // 3) plugins 目录按文件名扫描（数据目录固定位于 plugins/<name>/）
        File pluginsDir = (dataFolder != null) ? dataFolder.getParentFile() : null;
        if (pluginsDir != null && pluginsDir.isDirectory()) {
            File[] jars = pluginsDir.listFiles((d, n) -> {
                String l = n.toLowerCase();
                return l.endsWith(".jar") && l.contains("arcartx") && l.contains("proxy");
            });
            if (jars != null && jars.length > 0) {
                return getRelativePath(jars[0]);
            }
        }

        // fallback：根据平台猜测
        return platform == Platform.VELOCITY
            ? "plugins/ArcartXSuite-Proxy-Velocity.jar"
            : "plugins/ArcartXSuite-Proxy-Bungee.jar";
    }

    /** 从 jar: 协议资源 URL 解析出所在的本地 JAR 文件；解析失败返回 null。 */
    private static File jarFileFromResourceUrl(URL res) {
        if (res == null || !"jar".equals(res.getProtocol())) return null;
        try {
            String spec = res.toExternalForm();
            int sep = spec.lastIndexOf("!/");
            if (sep <= 4) return null;
            URL jarUrl = new URL(spec.substring(4, sep));
            if (!"file".equals(jarUrl.getProtocol())) return null;
            return new File(jarUrl.toURI());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检测代理端 jar 文件名（BungeeCord.jar / velocity.jar 等）。
     */
    private String detectProxyJar() {
        if (serverRoot == null || !serverRoot.isDirectory()) {
            return platform == Platform.VELOCITY ? "velocity.jar" : "BungeeCord.jar";
        }
        File[] files = serverRoot.listFiles((d, n) -> n.endsWith(".jar"));
        if (files != null) {
            for (File f : files) {
                String l = f.getName().toLowerCase();
                if (platform == Platform.VELOCITY) {
                    if (l.contains("velocity")) return f.getName();
                } else {
                    if (l.contains("bungee") || l.contains("bungeecord")) return f.getName();
                }
            }
            // fallback：取第一个 jar
            return files[0].getName();
        }
        return platform == Platform.VELOCITY ? "velocity.jar" : "BungeeCord.jar";
    }

    private static File resolveServerRoot(File dataFolder) {
        File abs = dataFolder.getAbsoluteFile();
        File plugins = abs.getParentFile();
        if (plugins == null) return abs;
        File root = plugins.getParentFile();
        return root != null ? root : plugins;
    }

    private String getRelativePath(File file) {
        try {
            return serverRoot.toPath().relativize(file.getAbsoluteFile().toPath()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getAbsolutePath().replace('\\', '/');
        }
    }
}
