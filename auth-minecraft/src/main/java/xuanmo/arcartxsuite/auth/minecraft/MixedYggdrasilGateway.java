package xuanmo.arcartxsuite.auth.minecraft;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.auth.protocol.MinecraftSessionProtocol;

/**
 * Shared local verification gateway. Only successful hasJoined requests authenticate a transport.
 * Profile/metadata responses are never persisted as identity proofs. Unsupported credential routes are rejected.
 */
public class MixedYggdrasilGateway {
    private final HandshakeReceipts receipts=new HandshakeReceipts();
    private final YggdrasilVerifier handshakeVerifier;
    private final java.net.URI suiteCenterOrigin;
    private final java.util.List<YggdrasilVerifier.Provider> providers;

    private static final String LITTLESKIN_BASE = "https://littleskin.cn/api/yggdrasil";
    private static final String MOJANG_SESSION = "https://sessionserver.mojang.com";
    /** Yggdrasil 规范的 sessionserver 路径前缀；Mojang 官方端点无此前缀。 */
    private static final String SESSION_PREFIX = "/sessionserver";
    /** 内部健康接口；不发布账号来源或认证凭据。 */
    private static final String INTERNAL_HEALTH_PATH = "/axs-internal/health";
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_REQUEST_BYTES = 512 * 1024;

    private final Logger logger;
    private final boolean debug;
    /** 绑定地址：默认 127.0.0.1（单机），跨机场景由配置指定。 */
    private final String bindHost;
    /** 内部 health 接口鉴权 token（null 表示不校验，仅 loopback 模式安全）。 */
    private final String authToken;
    /** 允许访问 health 的客户端 IP 白名单（null/空 表示不限制，依赖 token）。 */
    private final java.util.Set<String> allowIps;

    private HttpServer server;
    private ExecutorService executor;
    private volatile int port = -1;

    public MixedYggdrasilGateway(Logger logger, boolean debug) {
        this(logger, debug, LOOPBACK_HOST, null, null);
    }

    /**
     * @param bindHost   监听地址（单机用 127.0.0.1，跨机用具体 IP 或 0.0.0.0）
     * @param authToken  health 接口鉴权 token（null 表示不校验，仅 loopback 安全）
     * @param allowIps   允许访问 health 的客户端 IP 白名单（null/空 表示不限制）
     */
    public MixedYggdrasilGateway(Logger logger, boolean debug, String bindHost, String authToken, java.util.Collection<String> allowIps) {
        this(logger, debug, bindHost, authToken, allowIps, System.getenv("AXS_MIXED_CENTER_HAS_JOINED"),
            System.getenv("AXS_MIXED_CENTER_ORIGIN"));
    }

    MixedYggdrasilGateway(Logger logger, boolean debug, String bindHost, String authToken,
            java.util.Collection<String> allowIps, String centerEndpoint, String suiteCenter) {
        this.logger = logger;
        this.debug = debug;
        this.suiteCenterOrigin = suiteCenterOrigin(suiteCenter);
        this.providers = verificationProviders(centerEndpoint, suiteCenter);
        this.handshakeVerifier = new YggdrasilVerifier(providers);
        this.bindHost = (bindHost == null || bindHost.isBlank()) ? LOOPBACK_HOST : bindHost.trim();
        this.authToken = (authToken == null || authToken.isBlank()) ? null : authToken.trim();
        this.allowIps = (allowIps == null || allowIps.isEmpty())
            ? java.util.Collections.emptySet()
            : java.util.Collections.unmodifiableSet(
                new java.util.HashSet<>(allowIps.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .toList()));
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
            server = HttpServer.create(new InetSocketAddress(bindHost, portToBind), 0);
            server.createContext("/", new YggdrasilHandler());
            executor = new java.util.concurrent.ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(64),new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            server.setExecutor(executor);
            server.start();
            port = server.getAddress().getPort();
            logger.info("[MixedProxy] Yggdrasil 混合代理已启动: http://" + bindHost + ":" + port);
            if (!LOOPBACK_HOST.equals(bindHost)) {
                logger.info("[MixedProxy] 跨机模式: health 接口"
                    + (authToken != null ? "已启用 token 鉴权" : "未启用 token 鉴权（不安全！）")
                    + (allowIps.isEmpty() ? "" : " + IP 白名单 " + allowIps));
            }
            if (debug) {
                logger.info("[MixedProxy] 会话验证来源: " + providers.stream().map(YggdrasilVerifier.Provider::id).toList());
            }
            return port;
        } catch (IOException e) {
            logger.severe("[MixedProxy] 启动失败 (地址 " + bindHost + ":" + bindPort + "): " + e.getMessage());
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
        return "http://" + bindHost + ":" + port;
    }

    // ═════════════════════════════════════════════════════════════════
    //  内部 Handler
    // ═════════════════════════════════════════════════════════════════

    private class YggdrasilHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path=exchange.getRequestURI().getPath();
            String query=exchange.getRequestURI().getRawQuery();
            String fullPath=path+(query==null?"":"?"+query);
            if(path.equals("/axs-internal/claim-handshake")) {
                if(!exchange.getRemoteAddress().getAddress().isLoopbackAddress() || !"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(403,-1); exchange.close(); return;
                }
                try {
                    byte[] body=exchange.getRequestBody().readNBytes(4097);
                    if(body.length>4096) throw new IllegalArgumentException();
                    var request=com.google.gson.JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();
                    var claim=receipts.claim(java.util.UUID.fromString(request.get("profile").getAsString()),request.get("name").getAsString(),
                        java.util.UUID.fromString(request.get("challenge").getAsString()),request.get("realm").getAsString());
                    if(claim==null) { exchange.sendResponseHeaders(409,-1); exchange.close(); return; }
                    var result=new com.google.gson.JsonObject(); var receipt=claim.receipt();
                    result.addProperty("profile",receipt.profile().toString()); result.addProperty("name",receipt.name());
                    result.addProperty("provider",receipt.provider()); result.addProperty("receiptId",receipt.receiptId().toString());
                    result.addProperty("issuedAt",receipt.issuedAt()); result.addProperty("expiresAt",receipt.expiresAt());
                    result.addProperty("challenge",claim.challenge().toString()); result.addProperty("realm",claim.realm());
                    sendJson(exchange,200,result.toString());
                } catch(RuntimeException e) { exchange.sendResponseHeaders(400,-1); exchange.close(); }
                return;
            }
            if(path.equals(INTERNAL_HEALTH_PATH)) {
                if(!isAuthorizedClient(exchange)) { exchange.sendResponseHeaders(403,-1); exchange.close(); return; }
                sendJson(exchange,200,"{\"service\":\"axs-yggdrasil-gateway\",\"protocol\":1}");
                return;
            }
            if(!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405,-1); exchange.close(); return;
            }
            if(path.equals("/sessionserver/session/minecraft/hasJoined")
                    || path.matches("/sessionserver/session/minecraft/profile/[a-fA-F0-9]{32}")) {
                handleSessionWithFallback(exchange,fullPath);
            } else if(path.equals("/") || path.isEmpty()) {
                proxyDirectly(exchange,LITTLESKIN_BASE+fullPath);
            } else {
                exchange.sendResponseHeaders(404,-1); exchange.close();
            }
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
        if (exchange.getRequestURI().getPath().equals("/sessionserver/session/minecraft/hasJoined")) {
            handleVerifiedHandshake(exchange);
            return;
        }
        // 1. 先尝试 Mojang 官方（移除 /sessionserver 前缀；hasJoined 基于 serverId 加密握手
        //    哈希验证，Mojang 仅对真正用微软账号登录的玩家返回 200，命中即微软正版）
        String mojangPath = fullPath.startsWith(SESSION_PREFIX)
            ? fullPath.substring(SESSION_PREFIX.length())
            : fullPath;
        String response = tryFetch(MOJANG_SESSION + mojangPath);
        if (response != null) {
            if (debug) {
                logger.info("[MixedProxy] Mojang 命中 -> 200");
            }
            sendJson(exchange, 200, response);
            return;
        }

        // 2. fallback 到 LittleSkin（保留完整 Yggdrasil 路径）
        response = tryFetch(LITTLESKIN_BASE + fullPath);
        if (response != null) {
            if (debug) {
                logger.info("[MixedProxy] LittleSkin 命中 -> 200");
            }
            sendJson(exchange, 200, response);
            return;
        }

        if (suiteCenterOrigin != null) {
            String path = exchange.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            if (id.matches("[a-fA-F0-9]{32}")) {
                response = tryFetch(suiteCenterOrigin.resolve(MinecraftSessionProtocol.PROFILE + id).toString());
                if (response != null) { sendJson(exchange,200,response); return; }
            }
        }

        // 无档案时按 session API 返回 204。
        if (debug) {
            logger.info("[MixedProxy] Mojang + LittleSkin 均未命中 -> 204 | " + fullPath);
        }
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }


    static java.util.List<YggdrasilVerifier.Provider> verificationProviders(String centerEndpoint) {
        return verificationProviders(centerEndpoint,null);
    }

    static java.util.List<YggdrasilVerifier.Provider> verificationProviders(String centerEndpoint,String suiteCenter) {
        var providers = new java.util.ArrayList<YggdrasilVerifier.Provider>();
        java.net.URI origin = suiteCenterOrigin(suiteCenter);
        if (origin != null) {
            if (centerEndpoint != null && !centerEndpoint.isBlank())
                throw new IllegalArgumentException("Suite AuthCenter 与自定义认证端点只能选择一个");
            providers.add(new YggdrasilVerifier.Provider("center",origin.resolve(MinecraftSessionProtocol.HAS_JOINED),true));
        }
        providers.add(new YggdrasilVerifier.Provider("microsoft",
            java.net.URI.create(MOJANG_SESSION + "/session/minecraft/hasJoined")));
        providers.add(new YggdrasilVerifier.Provider("littleskin",
            java.net.URI.create(LITTLESKIN_BASE + SESSION_PREFIX + "/session/minecraft/hasJoined")));
        if (centerEndpoint != null && !centerEndpoint.isBlank()) {
            var center = java.net.URI.create(centerEndpoint.strip());
            if (providers.stream().anyMatch(provider -> provider.hasJoinedEndpoint().equals(center))) {
                throw new IllegalArgumentException("独立认证中心应使用自己的 hasJoined 服务地址");
            }
            providers.add(new YggdrasilVerifier.Provider("center", center));
        }
        return java.util.List.copyOf(providers);
    }

    private static java.net.URI suiteCenterOrigin(String configured) {
        if (configured == null || configured.isBlank()) return null;
        var uri = java.net.URI.create(configured.strip());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getPort() == 0 || uri.getPort() > 65535 || !uri.getRawPath().isEmpty()
                || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("AXS_MIXED_CENTER_ORIGIN 必须是 Suite AuthCenter 的 HTTPS 根地址");
        return uri;
    }

    private void handleVerifiedHandshake(HttpExchange exchange) throws IOException {
        if(!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
            exchange.sendResponseHeaders(403,-1); exchange.close(); return;
        }
        java.util.Map<String,String> parameters = new java.util.HashMap<>();
        try {
            String query = exchange.getRequestURI().getRawQuery();
            if (query == null || query.length() > 2048) throw new IllegalArgumentException();
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) throw new IllegalArgumentException();
                String key = java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8);
                if (parameters.putIfAbsent(key,value) != null) throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            exchange.sendResponseHeaders(400,-1); exchange.close(); return;
        }
        if(!java.util.Set.of("username","serverId","ip").containsAll(parameters.keySet())) {
            exchange.sendResponseHeaders(400,-1); exchange.close(); return;
        }
        var result = handshakeVerifier.verify(parameters.get("username"), parameters.get("serverId"), parameters.get("ip"));
        if (result.status() == xuanmo.arcartxsuite.auth.minecraft.YggdrasilVerifier.Status.VERIFIED) {
            if(!receipts.observe(result,parameters.get("serverId"))) {
                exchange.sendResponseHeaders(503,-1); exchange.close(); return;
            }
            sendJson(exchange,200,result.response());
            return;
        }
        int status = switch (result.status()) {
            case INVALID -> 400;
            case UNAVAILABLE -> 503;
            default -> 204;
        };
        exchange.sendResponseHeaders(status,-1); exchange.close();
    }






    /**
     * 校验客户端是否有权访问 health 接口。
     * <ul>
     *   <li>loopback 模式：仅允许本机访问</li>
     *   <li>跨机模式：校验 token（若配置）+ IP 白名单（若配置）</li>
     * </ul>
     */
    private boolean isAuthorizedClient(HttpExchange exchange) {
        InetSocketAddress remote = exchange.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return false;
        }
        InetAddress address = remote.getAddress();

        // loopback 模式：仅允许本机
        if (LOOPBACK_HOST.equals(bindHost) && address.isLoopbackAddress()) {
            return true;
        }

        // 跨机模式：IP 白名单校验（若配置）
        if (!allowIps.isEmpty()) {
            String clientIp = address.getHostAddress();
            // 处理 IPv6 zone id
            int pct = clientIp.indexOf('%');
            if (pct > 0) {
                clientIp = clientIp.substring(0, pct);
            }
            if (!allowIps.contains(clientIp)) {
                if (debug) {
                    logger.warning("[MixedProxy] health 拒绝 IP: " + clientIp);
                }
                return false;
            }
        }

        // token 校验（若配置）
        if (authToken != null) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            String expected = "Bearer " + authToken;
            if (authHeader == null || !authHeader.equals(expected)) {
                if (debug) {
                    logger.warning("[MixedProxy] health token 校验失败");
                }
                return false;
            }
        }

        return true;
    }




    // ─── HTTP 辅助 ───────────────────────────────────────────

    /**
     * 尝试从目标 URL 获取响应。
     *
     * @return 200 OK 时的响应体；204/404/异常时返回 null
     */
    private String tryFetch(String urlString) {
        HttpURLConnection conn = null;
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestProperty("User-Agent", "ArcartXSuite-MixedProxy/1.0");
            conn.setInstanceFollowRedirects(false);

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream input = conn.getInputStream()) {
                    return readBounded(input,MAX_RESPONSE_BYTES,deadline);
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
            byte[] requestBody = readAllBytesLimited(exchange.getRequestBody(), MAX_REQUEST_BYTES);
            String method = exchange.getRequestMethod();

            conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ArcartXSuite-MixedProxy/1.0");
            conn.setInstanceFollowRedirects(false);

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
                byte[] body = readAllBytesLimited(in, MAX_RESPONSE_BYTES);
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

    private static byte[] readAllBytesLimited(InputStream in, int maxBytes) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        try (in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(tmp)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("Body exceeds " + maxBytes + " bytes limit");
                }
                buf.write(tmp, 0, n);
            }
            return buf.toByteArray();
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  独立进程入口
    // ═════════════════════════════════════════════════════════════════

    /**
     * 独立进程入口：由 start-mixed-auth 脚本在代理端 JVM 启动【之前】拉起，
     * 确保 authlib-injector 在 premain 阶段就能连接到本代理获取元数据。
     * <p>
     * 用法：{@code java -cp ArcartXSuite-Proxy-<platform>.jar xuanmo.arcartxsuite.proxy.common.auth.MixedYggdrasilGateway <port> [--bind <host>] [--token <token>] [--allow-ips <ip1,ip2,...>] [--debug]}
     * <p>
     * 若端口已被占用（已有一个代理实例在跑），本进程会复用现有实例并正常退出。
     */
    public static void main(String[] args) {
        int bindPort = 25599;
        String bindHost = LOOPBACK_HOST;
        String token = null;
        java.util.List<String> allowIps = new java.util.ArrayList<>();
        boolean dbg = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--debug".equalsIgnoreCase(a)) {
                dbg = true;
            } else if ("--bind".equalsIgnoreCase(a) && i + 1 < args.length) {
                bindHost = args[++i].trim();
            } else if ("--token".equalsIgnoreCase(a) && i + 1 < args.length) {
                token = args[++i].trim();
            } else if ("--allow-ips".equalsIgnoreCase(a) && i + 1 < args.length) {
                for (String ip : args[++i].split(",")) {
                    String t = ip.trim();
                    if (!t.isEmpty()) allowIps.add(t);
                }
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

        MixedYggdrasilGateway proxy = new MixedYggdrasilGateway(log, dbg, bindHost, token, allowIps);
        int actual = proxy.startOnPort(bindPort);
        if (actual <= 0) {
            if (isExistingProxy(bindHost, bindPort)) {
                log.info("地址 " + bindHost + ":" + bindPort + " 已有代理实例在运行，复用现有实例，本进程退出。");
                System.exit(0);
            }
            log.severe("无法在 " + bindHost + ":" + bindPort + " 启动混合代理，请检查端口/地址是否可用。");
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(proxy::stop));
        log.info("独立进程运行中，" + bindHost + ":" + actual + "。关闭此窗口即停止代理。");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 检测目标地址是否已有本代理在运行（TCP 可连且 HTTP 根路径返回 Yggdrasil 元数据特征）。
     */
    private static boolean isExistingProxy(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
        } catch (IOException e) {
            return false;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL("http://" + host + ":" + port + INTERNAL_HEALTH_PATH).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            if (conn.getResponseCode() != 200) {
                return false;
            }
            try (InputStream input = conn.getInputStream()) {
                String json=readBounded(input,4096,System.nanoTime()+TimeUnit.SECONDS.toNanos(2));
                return json.contains("\"axs-yggdrasil-gateway\"") && json.contains("\"protocol\":1");
            }
        } catch (IOException e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    private static String readBounded(InputStream input,int limit,long deadline) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        byte[] buffer=new byte[4096];
        int count;
        while((count=input.read(buffer))!=-1) {
            if(System.nanoTime()-deadline>=0 || count>limit-output.size())
                throw new IOException("Response limit exceeded");
            output.write(buffer,0,count);
        }
        if(System.nanoTime()-deadline>=0) throw new IOException("Response deadline exceeded");
        return output.toString(StandardCharsets.UTF_8);
    }
}
