package xuanmo.arcartxsuite.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * AXS 本地 Yggdrasil 混合代理（单端混合登录）。
 * <p>
 * authlib-injector 原生不支持单端「正版 + 外置」混合登录（见其 Issue #56，至今未实现），
 * 官方建议始终使用单一认证源。本代理实现与 MultiLogin 相同的思路：
 * 在本地启动一个轻量 HTTP 服务，被 authlib-injector 当作唯一的 Yggdrasil 认证源，
 * 收到 hasJoined / profile 查询时，先尝试 LittleSkin，未命中再 fallback 到 Mojang 官方。
 * <p>
 * 关键点：
 * <ul>
 *   <li>Yggdrasil 规范路径为 {root}/sessionserver/session/minecraft/...，
 *       而 Mojang 官方端点无 /sessionserver 前缀，fallback 时需移除。</li>
 *   <li>不透传 LittleSkin 的 ALI（X-Authlib-Injector-API-Location）响应头，
 *       否则 authlib-injector 会绕过本代理直连 LittleSkin，导致 fallback 失效。</li>
 * </ul>
 */
public class MixedYggdrasilProxy {

    private static final String LITTLESKIN_BASE = "https://littleskin.cn/api/yggdrasil";
    private static final String MOJANG_SESSION = "https://sessionserver.mojang.com";
    /** Yggdrasil 规范的 sessionserver 路径前缀；Mojang 官方端点无此前缀。 */
    private static final String SESSION_PREFIX = "/sessionserver";

    private final Logger logger;
    private final boolean debug;

    private HttpServer server;
    private volatile int port = -1;

    public MixedYggdrasilProxy(Logger logger, boolean debug) {
        this.logger = logger;
        this.debug = debug;
    }

    /**
     * 启动本地代理，端口由系统自动分配。
     *
     * @return 实际监听端口，失败返回 -1
     */
    public int start() {
        return startOnPort(0);
    }

    /**
     * 在指定端口启动代理。
     *
     * @param bindPort 监听端口；&lt;= 0 表示由系统随机分配
     * @return 实际监听端口，失败返回 -1
     */
    public int startOnPort(int bindPort) {
        try {
            server = HttpServer.create(new InetSocketAddress(bindPort < 0 ? 0 : bindPort), 0);
            server.createContext("/", new YggdrasilHandler());
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            port = server.getAddress().getPort();
            logger.info("[MixedProxy] 本地 Yggdrasil 混合代理已启动: http://127.0.0.1:" + port);
            if (debug) {
                logger.info("[MixedProxy] hasJoined 顺序: LittleSkin -> Mojang");
            }
            return port;
        } catch (IOException e) {
            logger.severe("[MixedProxy] 启动失败 (端口 " + bindPort + "): " + e.getMessage());
            return -1;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("[MixedProxy] 已关闭");
            server = null;
            port = -1;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        return port;
    }

    public String getLocalUrl() {
        if (port <= 0) return null;
        return "http://127.0.0.1:" + port;
    }

    // ═════════════════════════════════════════════════════════════════
    //  内部 Handler
    // ═════════════════════════════════════════════════════════════════

    private class YggdrasilHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getQuery();
            String querySuffix = (query == null || query.isEmpty()) ? "" : "?" + query;
            String fullPath = path + querySuffix;

            if (debug) {
                logger.info("[MixedProxy] 收到 " + exchange.getRequestMethod() + " " + fullPath);
            }

            // sessionserver 的 hasJoined / profile 查询需要 LittleSkin -> Mojang fallback
            // （仅 GET，符合 Yggdrasil 规范）
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())
                && (path.startsWith("/sessionserver/session/minecraft/hasJoined")
                    || path.startsWith("/sessionserver/session/minecraft/profile/"))) {
                handleSessionWithFallback(exchange, fullPath);
                return;
            }

            // 其余请求（元数据 root、authserver、api 等）直接透传 LittleSkin
            proxyDirectly(exchange, LITTLESKIN_BASE + fullPath);
        }
    }

    /**
     * 处理 sessionserver 的 hasJoined / profile 查询，先 LittleSkin 后 Mojang fallback。
     * <p>
     * Yggdrasil 规范路径为 {root}/sessionserver/session/minecraft/...，
     * 而 Mojang 官方端点无 /sessionserver 前缀（即 /session/minecraft/...），
     * 因此 fallback 到 Mojang 时必须移除该前缀，否则会请求到不存在的路径。
     */
    private void handleSessionWithFallback(HttpExchange exchange, String fullPath) throws IOException {
        // 1. 先尝试 LittleSkin（保留完整 Yggdrasil 路径）
        String response = tryFetch(LITTLESKIN_BASE + fullPath);
        if (response != null) {
            if (debug) {
                logger.info("[MixedProxy] LittleSkin 命中 -> 200");
            }
            sendJson(exchange, 200, response);
            return;
        }

        // 2. fallback 到 Mojang 官方（移除 /sessionserver 前缀）
        String mojangPath = fullPath.startsWith(SESSION_PREFIX)
            ? fullPath.substring(SESSION_PREFIX.length())
            : fullPath;
        response = tryFetch(MOJANG_SESSION + mojangPath);
        if (response != null) {
            if (debug) {
                logger.info("[MixedProxy] Mojang 命中 -> 200");
            }
            sendJson(exchange, 200, response);
            return;
        }

        // 3. 都未命中 → 204 No Content（Minecraft 规范：验证失败）
        if (debug) {
            logger.info("[MixedProxy] LittleSkin + Mojang 均未命中 -> 204 | " + fullPath);
        }
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    // ═════════════════════════════════════════════════════════════════
    //  HTTP 辅助
    // ═════════════════════════════════════════════════════════════════

    /**
     * 尝试从目标 URL 获取响应。
     *
     * @return 200 OK 时的响应体；204/404/异常时返回 null
     */
    private String tryFetch(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ArcartXSuite-MixedProxy/1.0");
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            }
            if (code == 204 || code == 404) {
                return null;
            }
            if (debug) {
                logger.warning("[MixedProxy] 非预期 HTTP " + code + " from " + urlString);
            }
            return null;
        } catch (IOException e) {
            if (debug) {
                logger.warning("[MixedProxy] 请求异常: " + urlString + " | " + e.getMessage());
            }
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 直接把当前请求透传到目标 URL（用于非 hasJoined 请求）。
     */
    private void proxyDirectly(HttpExchange exchange, String targetUrl) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod(exchange.getRequestMethod());
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ArcartXSuite-MixedProxy/1.0");
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();

            // 透传 Content-Type；但【不透传】ALI（X-Authlib-Injector-API-Location），
            // 否则 authlib-injector 会绕过本代理直连 LittleSkin，导致 Mojang fallback 失效。
            String contentType = conn.getContentType();
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }

            // 透传 body
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (in != null) {
                byte[] body = readAllBytes(in);
                exchange.sendResponseHeaders(code, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else {
                exchange.sendResponseHeaders(code, -1);
            }
        } catch (IOException e) {
            logger.warning("[MixedProxy] 透传失败: " + targetUrl + " | " + e.getMessage());
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            exchange.close();
        }
    }

    private void sendJson(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        try (in) {
            return in.readAllBytes();
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  独立进程入口
    // ═════════════════════════════════════════════════════════════════

    /**
     * 独立进程入口：由 start-mixed-auth 脚本在 MC 服务器 JVM 启动【之前】拉起，
     * 确保 authlib-injector 在 premain 阶段就能连接到本代理获取元数据。
     * <p>
     * 用法：{@code java -cp ArcartXSuite.jar xuanmo.arcartxsuite.auth.MixedYggdrasilProxy <port> [--debug]}
     * <p>
     * 若端口已被占用（已有一个代理实例在跑），本进程会复用现有实例并正常退出。
     */
    public static void main(String[] args) {
        int bindPort = 25599;
        boolean dbg = false;
        for (String a : args) {
            if ("--debug".equalsIgnoreCase(a)) {
                dbg = true;
            } else {
                try {
                    bindPort = Integer.parseInt(a.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Logger log = Logger.getLogger("AXS-MixedProxy");
        log.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord r) {
                return "[AXS-MixedProxy] " + r.getMessage() + System.lineSeparator();
            }
        });
        log.addHandler(handler);

        MixedYggdrasilProxy proxy = new MixedYggdrasilProxy(log, dbg);
        int actual = proxy.startOnPort(bindPort);
        if (actual <= 0) {
            if (isPortReachable(bindPort)) {
                log.info("端口 " + bindPort + " 已有代理实例在运行，复用现有实例，本进程退出。");
                System.exit(0);
            }
            log.severe("无法在端口 " + bindPort + " 启动混合代理，请检查端口是否被其他程序占用。");
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(proxy::stop));
        log.info("独立进程运行中，端口 " + actual + "。关闭此窗口即停止代理。");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 检测本地端口是否可连接（用于判断是否已有代理实例）。
     */
    private static boolean isPortReachable(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
