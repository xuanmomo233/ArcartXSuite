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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    /** 内部账号来源查询 endpoint（供主进程 AccountTypeService 查询权威认证结果）。 */
    private static final String INTERNAL_SOURCE_PATH = "/axs-internal/account-source";
    /** 认证来源标记：微软正版（Mojang 命中）。 */
    private static final String SOURCE_MICROSOFT = "microsoft";
    /** 认证来源标记：LittleSkin。 */
    private static final String SOURCE_LITTLESKIN = "littleskin";
    private static final String LOOPBACK_HOST = "127.0.0.1";

    /**
     * 玩家认证来源权威记录：无横线小写 UUID -> source。
     * <p>在 hasJoined / profile 命中时写入，是玩家实际认证链路的真实结果，
     * 比主进程事后猜测 Mojang API 更准确。
     */
    private final ConcurrentMap<String, String> accountSource = new ConcurrentHashMap<>();

    private final Logger logger;
    private final boolean debug;

    private HttpServer server;
    private ExecutorService executor;
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
            int portToBind = bindPort < 0 ? 0 : bindPort;
            server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, portToBind), 0);
            server.createContext("/", new YggdrasilHandler());
            executor = Executors.newFixedThreadPool(4);
            server.setExecutor(executor);
            server.start();
            port = server.getAddress().getPort();
            logger.info("[MixedProxy] 本地 Yggdrasil 混合代理已启动: http://" + LOOPBACK_HOST + ":" + port);
            if (debug) {
                logger.info("[MixedProxy] hasJoined 顺序: Mojang -> LittleSkin");
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
            server = null;
            port = -1;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        logger.info("[MixedProxy] 已关闭");
    }

    public boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        return port;
    }

    public String getLocalUrl() {
        if (port <= 0) return null;
        return "http://" + LOOPBACK_HOST + ":" + port;
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

            // 内部账号来源查询（仅供本机主进程调用）
            if (path.startsWith(INTERNAL_SOURCE_PATH)) {
                handleInternalSourceQuery(exchange, query);
                return;
            }

            // sessionserver 的 hasJoined / profile 查询需要 Mojang -> LittleSkin fallback
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
     * 处理 sessionserver 的 hasJoined / profile 查询，先 Mojang 后 LittleSkin fallback。
     * <p>
     * Yggdrasil 规范路径为 {root}/sessionserver/session/minecraft/...，
     * 而 Mojang 官方端点无 /sessionserver 前缀（即 /session/minecraft/...），
     * 因此查询 Mojang 时必须移除该前缀，否则会请求到不存在的路径。
     */
    private void handleSessionWithFallback(HttpExchange exchange, String fullPath) throws IOException {
        // 1. 先尝试 Mojang 官方（移除 /sessionserver 前缀；hasJoined 基于 serverId 加密握手
        //    哈希验证，Mojang 仅对真正用微软账号登录的玩家返回 200，命中即微软正版）
        String mojangPath = fullPath.startsWith(SESSION_PREFIX)
            ? fullPath.substring(SESSION_PREFIX.length())
            : fullPath;
        String response = tryFetch(MOJANG_SESSION + mojangPath);
        if (response != null) {
            recordSource(response, SOURCE_MICROSOFT);
            if (debug) {
                logger.info("[MixedProxy] Mojang 命中 -> 200");
            }
            sendJson(exchange, 200, response);
            return;
        }

        // 2. fallback 到 LittleSkin（保留完整 Yggdrasil 路径）
        response = tryFetch(LITTLESKIN_BASE + fullPath);
        if (response != null) {
            recordSource(response, SOURCE_LITTLESKIN);
            if (debug) {
                logger.info("[MixedProxy] LittleSkin 命中 -> 200");
            }
            sendJson(exchange, 200, response);
            return;
        }

        // 3. 都未命中 → 204 No Content（Minecraft 规范：验证失败）
        if (debug) {
            logger.info("[MixedProxy] Mojang + LittleSkin 均未命中 -> 204 | " + fullPath);
        }
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    /**
     * 从 hasJoined / profile 响应 JSON 中提取玩家 UUID（id 字段）并记录认证来源。
     */
    private void recordSource(String responseJson, String source) {
        String id = extractJsonString(responseJson, "id");
        if (id == null || id.isBlank()) {
            return;
        }
        String key = id.replace("-", "").toLowerCase(Locale.ROOT);
        accountSource.put(key, source);
        if (debug) {
            logger.info("[MixedProxy] 记录认证来源: " + key + " -> " + source);
        }
    }

    /**
     * 处理内部账号来源查询：{@code GET /axs-internal/account-source?uuid=<uuid>}。
     * <p>命中返回 {@code {"source":"microsoft|littleskin"}}；未命中返回 404。
     */
    private void handleInternalSourceQuery(HttpExchange exchange, String query) throws IOException {
        if (!isLoopbackClient(exchange)) {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
            return;
        }
        String uuid = parseQueryParam(query, "uuid");
        if (uuid != null) {
            uuid = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        }
        String source = uuid == null ? null : accountSource.get(uuid);
        if (source == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        sendJson(exchange, 200, "{\"source\":\"" + source + "\"}");
    }

    private static boolean isLoopbackClient(HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        if (remote == null) {
            return false;
        }
        InetAddress address = remote.getAddress();
        return address != null && address.isLoopbackAddress();
    }

    /**
     * 从 query 字符串（如 {@code uuid=xxx&foo=bar}）中提取指定参数值。
     */
    private static String parseQueryParam(String query, String key) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    /**
     * 极简 JSON 字符串字段提取（与 AccountTypeServiceImpl 保持一致）。
     */
    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + pattern.length());
        if (colonIndex < 0) {
            return null;
        }
        int startQuote = json.indexOf('"', colonIndex + 1);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return null;
        }
        return json.substring(startQuote + 1, endQuote).trim();
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
     * 直接把当前请求透传到目标 URL（用于非 hasJoined 请求，含 POST body）。
     */
    private void proxyDirectly(HttpExchange exchange, String targetUrl) throws IOException {
        HttpURLConnection conn = null;
        try {
            byte[] requestBody = readAllBytes(exchange.getRequestBody());
            String method = exchange.getRequestMethod();

            conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ArcartXSuite-MixedProxy/1.0");
            conn.setInstanceFollowRedirects(true);

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType != null && !contentType.isBlank()) {
                conn.setRequestProperty("Content-Type", contentType);
            }

            if (requestBody.length > 0 || "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                try (OutputStream upstream = conn.getOutputStream()) {
                    upstream.write(requestBody);
                }
            }

            int code = conn.getResponseCode();

            // 透传 Content-Type；但【不透传】ALI（X-Authlib-Injector-API-Location），
            // 否则 authlib-injector 会绕过本代理直连 LittleSkin，导致 Mojang fallback 失效。
            String responseContentType = conn.getContentType();
            if (responseContentType != null) {
                exchange.getResponseHeaders().set("Content-Type", responseContentType);
            }

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
        if (in == null) {
            return new byte[0];
        }
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
            if (isExistingProxy(bindPort)) {
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
     * 检测本地端口是否已有本代理在运行（TCP 可连且 HTTP 根路径返回 Yggdrasil 元数据特征）。
     */
    private static boolean isExistingProxy(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LOOPBACK_HOST, port), 1000);
        } catch (IOException e) {
            return false;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://" + LOOPBACK_HOST + ":" + port + "/").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            if (conn.getResponseCode() != 200) {
                return false;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                String json = body.toString();
                return json.contains("\"meta\"") && json.contains("\"skinDomains\"");
            }
        } catch (IOException e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
