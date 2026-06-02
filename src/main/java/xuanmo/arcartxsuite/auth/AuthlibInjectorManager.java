package xuanmo.arcartxsuite.auth;

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

import org.bukkit.ChatColor;

/**
 * authlib-injector 全局管理器：版本检测、自动下载、启动脚本生成、更新提醒。
 * <p>
 * 从原 {@code modules/loginview/authlib/AuthlibInjectorHelper} 升级而来，
 * 提升为宿主级服务，供所有模块共享。
 */
public final class AuthlibInjectorManager {

    private static final String LATEST_API = "https://authlib-injector.yushi.moe/artifact/latest.json";
    private static final String AGENT_CLASS = "moe.yushi.authlibinjector.AuthlibInjector";
    private static final String JAR_NAME = "authlib-injector.jar";

    private static final String JAVA17_OPENS =
        "--add-opens java.base/java.lang=ALL-UNNAMED "
        + "--add-opens java.base/java.net=ALL-UNNAMED "
        + "--add-opens java.base/sun.net.util=ALL-UNNAMED";

    /** 混合代理独立进程入口类（必须与 proguard -keep 保持一致）。 */
    private static final String PROXY_MAIN_CLASS = "xuanmo.arcartxsuite.auth.MixedYggdrasilProxy";

    private static final int DEFAULT_PROXY_PORT = 25599;

    private final Logger logger;
    private final File dataFolder;
    private final File serverRoot;
    private final int mixedProxyPort;

    public AuthlibInjectorManager(Logger logger, File dataFolder) {
        this(logger, dataFolder, DEFAULT_PROXY_PORT);
    }

    public AuthlibInjectorManager(Logger logger, File dataFolder, int mixedProxyPort) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.serverRoot = resolveServerRoot(dataFolder);
        this.mixedProxyPort = (mixedProxyPort > 0 && mixedProxyPort <= 65535) ? mixedProxyPort : DEFAULT_PROXY_PORT;
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
            logger.warning("    请执行 /axs auth update 自动更新");
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

    public boolean generateStartScripts(String yggdrasilUrl, String serverJarName, boolean useLocalMixed) {
        File authlibJar = new File(dataFolder, JAR_NAME);
        if (!authlibJar.exists()) {
            logger.warning("[AuthlibInjector] 未找到 authlib-injector.jar，请先执行下载。");
            return false;
        }
        String agentPath = getRelativePath(authlibJar);
        String axsJarPath = resolveAxsJarPath();
        try {
            generateBatScript(agentPath, yggdrasilUrl, serverJarName, useLocalMixed, axsJarPath);
            generateShScript(agentPath, yggdrasilUrl, serverJarName, useLocalMixed, axsJarPath);
            logger.info("[AuthlibInjector] 启动脚本已生成: start-mixed-auth.bat / start-mixed-auth.sh");
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[AuthlibInjector] 生成启动脚本失败", e);
            return false;
        }
    }

    /** 本地混合代理监听端口（固定，由 config 注入）。 */
    public int getMixedProxyPort() {
        return mixedProxyPort;
    }

    /** 本地混合代理 URL（authlib-injector 指向此地址）。 */
    public String getMixedProxyUrl() {
        return "http://127.0.0.1:" + mixedProxyPort;
    }

    /**
     * 检测本地混合代理是否已就绪（由 start-mixed-auth 脚本独立进程启动）。
     * <p>
     * 注意：代理是独立进程，不由本插件启动/停止，这里只做连通性探测。
     */
    public boolean isMixedProxyReachable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", mixedProxyPort), 1000);
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
     */
    public String setupAll(String yggdrasilUrl) {
        // ?mixed 是 AXS 用来识别「启用本地混合代理」的开关（并非 authlib-injector 的有效参数）
        boolean useLocalMixed = yggdrasilUrl != null && yggdrasilUrl.contains("?mixed");
        String effectiveUrl;
        if (useLocalMixed) {
            // authlib-injector 指向本地代理（固定端口），代理由脚本在服务器启动前独立拉起
            effectiveUrl = getMixedProxyUrl();
            logger.info("[AuthlibInjector] 混合登录：脚本将先独立启动本地代理（端口 "
                + mixedProxyPort + "），authlib-injector 指向 " + effectiveUrl);
        } else {
            effectiveUrl = yggdrasilUrl;
        }

        File jar = downloadOrUpdate();
        if (jar == null) {
            return ChatColor.RED + "authlib-injector 下载失败，请检查网络连接。";
        }
        String serverJar = detectServerJar();
        if (!generateStartScripts(effectiveUrl, serverJar, useLocalMixed)) {
            return ChatColor.RED + "启动脚本生成失败。authlib-injector.jar 已下载到: " + jar.getAbsolutePath();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GREEN).append("authlib-injector 配置完成！\n");
        sb.append(ChatColor.GRAY).append("  ").append(ChatColor.WHITE).append("jar: ").append(jar.getAbsolutePath()).append("\n");
        sb.append(ChatColor.GRAY).append("  ").append(ChatColor.WHITE).append("启动脚本: start-mixed-auth.bat / .sh\n");
        if (useLocalMixed) {
            sb.append(ChatColor.AQUA).append("  [混合登录] 脚本会先启动本地代理（端口 ").append(mixedProxyPort)
              .append("）再启动服务器，支持 LittleSkin + 微软正版\n");
            sb.append(ChatColor.YELLOW).append("  [重要] 本模式必须使用 start-mixed-auth 脚本启动，\n");
            sb.append(ChatColor.YELLOW).append("         不能只在原脚本加 -javaagent（代理需先于服务器启动）。\n");
        }
        sb.append(ChatColor.YELLOW).append("\n  请执行以下操作:\n");
        sb.append(ChatColor.WHITE).append("  1. 关闭服务器 (stop)\n");
        sb.append(ChatColor.WHITE).append("  2. 使用 start-mixed-auth.bat (Windows) 或 start-mixed-auth.sh (Linux) 启动\n");
        sb.append(ChatColor.YELLOW).append("\n  请确保 server.properties 中 online-mode=true");
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════
    //  server.properties 检测
    // ═════════════════════════════════════════════════════════════════

    public boolean checkServerProperties() {
        File props = new File(serverRoot, "server.properties");
        if (!props.exists()) {
            logger.warning("[AuthlibInjector] 未找到 server.properties");
            return false;
        }
        try {
            String content = Files.readString(props.toPath(), StandardCharsets.UTF_8);
            if (content.contains("online-mode=false")) {
                logger.warning("");
                logger.warning("  [AuthlibInjector] server.properties 中 online-mode=false!");
                logger.warning("    使用 authlib-injector + Mixed Mode 时必须设置为 online-mode=true");
                logger.warning("    否则 LittleSkin 账号会被分配 v3 UUID，无法与离线玩家区分。");
                logger.warning("");
                return false;
            }
            return true;
        } catch (IOException e) {
            logger.warning("[AuthlibInjector] 读取 server.properties 失败: " + e.getMessage());
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  内部辅助
    // ═════════════════════════════════════════════════════════════════

    private void printNotLoadedWarning() {
        logger.warning("");
        logger.warning("  [AuthlibInjector] 未检测到 authlib-injector JVM Agent!");
        logger.warning("    若需支持 LittleSkin + 微软正版 多方认证，请执行:");
        logger.warning("      /axs auth setup");
        logger.warning("    该命令会自动下载并生成启动脚本。");
        logger.warning("");
    }

    private String detectLocalVersion() {
        // authlib-injector 本身不提供版本 API，尝试从 jar 文件名或 Agent 属性推断
        File jar = new File(dataFolder, JAR_NAME);
        if (!jar.exists()) return null;
        try {
            java.util.jar.JarFile jf = new java.util.jar.JarFile(jar);
            java.util.jar.Manifest manifest = jf.getManifest();
            if (manifest != null) {
                String v = manifest.getMainAttributes().getValue("Implementation-Version");
                if (v != null) return v;
            }
            jf.close();
        } catch (IOException ignored) {
        }
        return null;
    }

    private String fetchLatestVersion() {
        String json = fetchJson(LATEST_API);
        if (json == null) return null;
        return extractJsonString(json, "version");
    }

    private String fetchLatestDownloadUrl() {
        String json = fetchJson(LATEST_API);
        if (json == null) return null;
        return extractJsonString(json, "download_url");
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
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
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

    private void generateBatScript(String agentPath, String yggdrasilUrl, String serverJar,
                                   boolean useLocalMixed, String axsJarPath) throws IOException {
        File script = new File(serverRoot, "start-mixed-auth.bat");
        StringBuilder c = new StringBuilder();
        c.append("@echo off\r\n");
        c.append("cd /d \"%~dp0\"\r\n");
        c.append("title Minecraft Server (Mixed Auth Mode)\r\n");
        if (useLocalMixed) {
            c.append("set \"PROXY_PORT=").append(mixedProxyPort).append("\"\r\n");
            c.append("echo [ArcartXSuite] Starting AXS mixed-auth proxy on port %PROXY_PORT% ...\r\n");
            c.append("start \"AXS Mixed-Auth Proxy\" /min java -cp \"").append(axsJarPath)
             .append("\" ").append(PROXY_MAIN_CLASS).append(" %PROXY_PORT%\r\n");
            c.append("echo [ArcartXSuite] Waiting for proxy to be ready ...\r\n");
            c.append("set /a _tries=0\r\n");
            c.append(":axs_waitproxy\r\n");
            c.append("powershell -NoProfile -Command \"$c=New-Object Net.Sockets.TcpClient;try{$c.Connect('127.0.0.1',%PROXY_PORT%);$c.Close();exit 0}catch{exit 1}\" >nul 2>&1\r\n");
            c.append("if not errorlevel 1 goto axs_proxyready\r\n");
            c.append("set /a _tries+=1\r\n");
            c.append("if %_tries% geq 30 (echo [ArcartXSuite][WARN] proxy not ready, continuing anyway & goto axs_proxyready)\r\n");
            c.append("ping -n 2 127.0.0.1 >nul\r\n");
            c.append("goto axs_waitproxy\r\n");
            c.append(":axs_proxyready\r\n");
            c.append("echo [ArcartXSuite] Proxy ready. Launching server ...\r\n");
        } else {
            c.append("echo [ArcartXSuite] Starting with authlib-injector ...\r\n");
        }
        c.append("java ").append(JAVA17_OPENS).append(" -javaagent:").append(agentPath).append("=").append(yggdrasilUrl)
         .append(" -Xmx4G -Xms1G -jar ").append(serverJar).append(" nogui\r\n");
        c.append("pause\r\n");
        Files.writeString(script.toPath(), c.toString(), StandardCharsets.UTF_8);
    }

    private void generateShScript(String agentPath, String yggdrasilUrl, String serverJar,
                                  boolean useLocalMixed, String axsJarPath) throws IOException {
        File script = new File(serverRoot, "start-mixed-auth.sh");
        StringBuilder c = new StringBuilder();
        c.append("#!/bin/bash\n");
        c.append("cd \"$(dirname \"$0\")\"\n");
        if (useLocalMixed) {
            c.append("PROXY_PORT=").append(mixedProxyPort).append("\n");
            c.append("echo \"[ArcartXSuite] Starting AXS mixed-auth proxy on port $PROXY_PORT ...\"\n");
            c.append("java -cp \"").append(axsJarPath).append("\" ").append(PROXY_MAIN_CLASS).append(" $PROXY_PORT &\n");
            c.append("AXS_PROXY_PID=$!\n");
            c.append("echo \"[ArcartXSuite] Waiting for proxy to be ready ...\"\n");
            c.append("_tries=0\n");
            c.append("until bash -c \"echo > /dev/tcp/127.0.0.1/$PROXY_PORT\" 2>/dev/null; do\n");
            c.append("  _tries=$((_tries+1))\n");
            c.append("  if [ $_tries -ge 30 ]; then echo \"[ArcartXSuite][WARN] proxy not ready, continuing\"; break; fi\n");
            c.append("  sleep 1\n");
            c.append("done\n");
            c.append("echo \"[ArcartXSuite] Proxy ready. Launching server ...\"\n");
            c.append("java ").append(JAVA17_OPENS).append(" -javaagent:").append(agentPath).append("=").append(yggdrasilUrl)
             .append(" -Xmx4G -Xms1G -jar ").append(serverJar).append(" nogui\n");
            c.append("kill $AXS_PROXY_PID 2>/dev/null\n");
        } else {
            c.append("echo \"[ArcartXSuite] Starting with authlib-injector ...\"\n");
            c.append("java ").append(JAVA17_OPENS).append(" -javaagent:").append(agentPath).append("=").append(yggdrasilUrl)
             .append(" -Xmx4G -Xms1G -jar ").append(serverJar).append(" nogui\n");
        }
        Files.writeString(script.toPath(), c.toString(), StandardCharsets.UTF_8);
        script.setExecutable(true);
    }

    /**
     * 解析 AXS 自身 jar 的相对路径（用于独立进程代理的 classpath）。
     */
    private String resolveAxsJarPath() {
        try {
            File jar = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.isFile()) {
                return getRelativePath(jar);
            }
        } catch (Exception ignored) {
        }
        return "plugins/ArcartXSuite.jar";
    }

    private String detectServerJar() {
        if (serverRoot == null || !serverRoot.isDirectory()) return "server.jar";
        File[] files = serverRoot.listFiles((d, n) -> n.endsWith(".jar"));
        if (files != null) {
            for (File f : files) {
                String l = f.getName().toLowerCase();
                if (l.contains("paper") || l.contains("purpur") || l.contains("spigot")
                    || l.contains("mohist") || l.contains("catserver")) {
                    return f.getName();
                }
            }
        }
        return "server.jar";
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

    private static String extractJsonString(String json, String key) {
        String p = "\"" + key + "\"";
        int i = json.indexOf(p);
        if (i < 0) return null;
        int c = json.indexOf(':', i + p.length());
        if (c < 0) return null;
        int s = json.indexOf('"', c + 1);
        if (s < 0) return null;
        int e = json.indexOf('"', s + 1);
        if (e < 0) return null;
        return json.substring(s + 1, e);
    }
}
