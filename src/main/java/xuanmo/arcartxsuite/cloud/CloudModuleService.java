package xuanmo.arcartxsuite.cloud;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.ArcartXSuitePlugin;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import xuanmo.arcartxsuite.security.NativeBridge;

/**
 * 云端模块服务：负责与 AXS Cloud Platform 交互，下载、解密并加载云端模块。
 */
public final class CloudModuleService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    /** 云端平台地址：硬编码于宿主内，禁止通过 config.yml 篡改。 */
    private static final String API_BASE_URL = "https://cloud.021209.xyz";

    private final ArcartXSuitePlugin plugin;
    private final ModuleRegistry registry;
    private final String apiBaseUrl;
    private final String qq;
    private final String password;
    private final String serverName;

    private volatile String serverCode;
    private final KeyPair keyPair;
    private final String serverPublicKeyB64;

    private volatile String moduleToken;
    private volatile long tokenExpiry;
    private volatile List<String> allowedModules = List.of();
    private static final long AXB_CACHE_TTL_MS = 3600 * 1000L;
    private static final class CachedAxb {
        final byte[] data;
        final long expiresAt;
        CachedAxb(byte[] data, long expiresAt) { this.data = data; this.expiresAt = expiresAt; }
    }
    private final Map<String, CachedAxb> cachedAxb = new ConcurrentHashMap<>();

    private BukkitTask heartbeatTask;
    private final long pluginStartTime = System.currentTimeMillis();

    public CloudModuleService(ArcartXSuitePlugin plugin, ModuleRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        this.apiBaseUrl = API_BASE_URL;
        this.qq = config.getString("cloud.qq", "").trim();
        this.password = config.getString("cloud.password", "");
        this.serverName = config.getString("cloud.server-name", plugin.getServer().getName());
        this.serverCode = config.getString("cloud.server-code", "").trim();
        this.keyPair = generateKeyPair();
        this.serverPublicKeyB64 = keyPair != null
            ? Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
            : "";
    }

    private boolean hasServerCode() {
        return serverCode != null && !serverCode.isEmpty();
    }

    // -- 服务器首次绑定 ---------------------------------------

    /**
     * 用 QQ+密码向云端换取服务器码。成功后写回 config.yml 的 cloud.server-code。
     */
    public CompletableFuture<Boolean> bindServer() {
        if (qq.isEmpty() || password.isEmpty()) {
            plugin.consoleWarn("[Cloud] 未配置 cloud.qq / cloud.password，无法绑定服务器。");
            return CompletableFuture.completedFuture(false);
        }
        String fingerprint = generateFingerprint();
        String payload = "{"
            + "\"qq\":\"" + escapeJson(qq) + "\","
            + "\"password\":\"" + escapeJson(password) + "\","
            + "\"pubkey\":\"" + serverPublicKeyB64 + "\","
            + "\"fingerprintHash\":\"" + sha256(fingerprint) + "\","
            + "\"name\":\"" + escapeJson(serverName) + "\""
            + "}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/servers/bind"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> {
                if (resp.statusCode() == 200) {
                    String body = resp.body();
                    String code = extractJsonField(body, "serverCode");
                    String token = extractJsonField(body, "token");
                    if (code != null && !code.isEmpty()) {
                        this.serverCode = code;
                        this.allowedModules = extractStringArray(body, "allowedModules");
                        if (token != null) {
                            this.moduleToken = token;
                            this.tokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000;
                        }
                        // 回写 server-code 到配置（主线程保存）
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getConfig().set("cloud.server-code", code);
                            plugin.saveConfig();
                        });
                        plugin.consoleInfo("[Cloud] 服务器绑定成功，服务器码: " + code);
                        return true;
                    }
                    plugin.consoleWarn("[Cloud] 绑定响应缺少 serverCode: " + body);
                    return false;
                }
                plugin.consoleWarn("[Cloud] 服务器绑定失败: " + resp.statusCode() + " " + resp.body());
                return false;
            })
            .exceptionally(ex -> {
                plugin.consoleWarn("[Cloud] 服务器绑定异常: " + ex.getMessage());
                return false;
            });
    }

    // -- 申请/刷新模块令牌 --------------------------------------

    public CompletableFuture<Boolean> refreshToken() {
        if (moduleToken != null && System.currentTimeMillis() < tokenExpiry - 300_000) {
            return CompletableFuture.completedFuture(true);
        }
        if (!hasServerCode()) {
            return CompletableFuture.completedFuture(false);
        }

        long timestamp = System.currentTimeMillis();
        String signature = sign(timestamp + serverCode);
        String refreshPayload = "{"
            + "\"password\":\"" + escapeJson(password) + "\""
            + "}";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/servers/refresh"))
            .header("Content-Type", "application/json")
            .header("X-Server-Code", serverCode)
            .header("X-Timestamp", String.valueOf(timestamp))
            .header("X-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(refreshPayload))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> {
                if (resp.statusCode() == 200) {
                    String body = resp.body();
                    String token = extractJsonField(body, "token");
                    if (token != null) {
                        this.moduleToken = token;
                        this.tokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000;
                        this.allowedModules = extractStringArray(body, "allowedModules");
                        plugin.consoleInfo("[Cloud] 模块令牌已刷新，授权模块: " + allowedModules.size());
                        startHeartbeat();
                        return true;
                    }
                }
                plugin.consoleWarn("[Cloud] 刷新令牌失败: " + resp.statusCode() + " " + resp.body());
                return false;
            })
            .exceptionally(ex -> {
                plugin.consoleWarn("[Cloud] 刷新令牌异常: " + ex.getMessage());
                return false;
            });
    }

    // -- 同步并加载云端模块 --------------------------------------

    public CompletableFuture<Void> syncModules() {
        // 1. 未绑定则先用 QQ+密码绑定服务器
        CompletableFuture<Boolean> ready = hasServerCode()
            ? CompletableFuture.completedFuture(true)
            : bindServer();

        return ready.thenCompose(bound -> {
            if (!bound) {
                plugin.consoleWarn("[Cloud] 未能绑定服务器，跳过云端模块同步。");
                return CompletableFuture.<Void>completedFuture(null);
            }
            return refreshToken().thenCompose(ok -> {
                if (!ok) return CompletableFuture.<Void>completedFuture(null);
                List<String> mods = allowedModules;
                if (mods == null || mods.isEmpty()) {
                    plugin.consoleInfo("[Cloud] 该服务器未装备任何云端模块。");
                    return CompletableFuture.<Void>completedFuture(null);
                }
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                for (String id : mods) {
                    if (id == null || id.isEmpty()) continue;
                    chain = chain.thenCompose(v -> downloadAndLoad(id));
                }
                return chain;
            });
        });
    }

    private static final java.util.regex.Pattern MODULE_ID_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]+$");

    private boolean isValidModuleId(String moduleId) {
        return moduleId != null && MODULE_ID_PATTERN.matcher(moduleId).matches();
    }

    private CompletableFuture<Void> downloadAndLoad(String moduleId) {
        if (!isValidModuleId(moduleId)) {
            plugin.consoleWarn("[Cloud] 非法 moduleId 格式，跳过: " + moduleId);
            return CompletableFuture.<Void>completedFuture(null);
        }
        if (!NativeBridge.isAvailable()) {
            plugin.consoleWarn("[Cloud] Native 安全库未加载，跳过云端模块 " + moduleId + " 的解密加载");
            return CompletableFuture.<Void>completedFuture(null);
        }
        return downloadAxb(moduleId).thenCompose(axb -> {
            if (axb == null || axb.length == 0) {
                plugin.consoleWarn("[Cloud] 下载模块 " + moduleId + " 失败");
                return CompletableFuture.<Void>completedFuture(null);
            }
            return requestModuleKey(moduleId).thenAccept(keyResp -> {
                if (keyResp == null) {
                    plugin.consoleWarn("[Cloud] 获取模块 " + moduleId + " 密钥失败");
                    return;
                }
                String keyB64 = extractJsonField(keyResp, "key");
                if (keyB64 == null) {
                    plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 密钥数据不完整");
                    return;
                }
                byte[] key = Base64.getDecoder().decode(keyB64);
                // axb 自包含 IV（前 12 字节），native 内部读取，无需单独传入
                byte[] jarBytes = NativeBridge.n4(axb, key);
                if (jarBytes == null || jarBytes.length == 0) {
                    plugin.consoleWarn("[Cloud] 解密模块 " + moduleId + " 失败");
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    boolean ok = registry.loadCloudModule(jarBytes);
                    if (ok) {
                        plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 已加载");
                    } else {
                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 加载失败");
                    }
                });
            });
        });
    }

    private CompletableFuture<byte[]> downloadAxb(String moduleId) {
        CachedAxb cached = cachedAxb.get(moduleId);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return CompletableFuture.completedFuture(cached.data);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/modules/" + moduleId + "/download"))
            .header("X-Module-Token", moduleToken)
            .header("X-Server-Code", serverCode)
            .GET()
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(resp -> {
                if (resp.statusCode() == 200) {
                    byte[] data = resp.body();
                    cachedAxb.put(moduleId, new CachedAxb(data, System.currentTimeMillis() + AXB_CACHE_TTL_MS));
                    return data;
                }
                plugin.consoleWarn("[Cloud] 下载 " + moduleId + " 失败: " + resp.statusCode());
                return null;
            })
            .exceptionally(ex -> {
                plugin.consoleWarn("[Cloud] 下载 " + moduleId + " 异常: " + ex.getMessage());
                return null;
            });
    }

    private CompletableFuture<String> requestModuleKey(String moduleId) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/modules/" + moduleId + "/key"))
            .header("Content-Type", "application/json")
            .header("X-Module-Token", moduleToken)
            .header("X-Server-Code", serverCode)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> resp.statusCode() == 200 ? resp.body() : null)
            .exceptionally(ex -> null);
    }

    // -- 心跳 ------------------------------------------------

    private void startHeartbeat() {
        if (heartbeatTask != null) return;
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::sendHeartbeat, 30L * 20L, 30L * 20L);
        plugin.consoleInfo("[Cloud] 心跳任务已启动（30秒/次）");
    }

    public void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
            plugin.consoleInfo("[Cloud] 心跳任务已停止");
        }
    }

    private void sendHeartbeat() {
        if (moduleToken == null || moduleToken.isEmpty()) return;

        int online = plugin.getServer().getOnlinePlayers().size();
        int max = plugin.getServer().getMaxPlayers();
        long uptime = System.currentTimeMillis() - pluginStartTime;
        String javaVer = System.getProperty("java.version", "unknown");
        String os = System.getProperty("os.name", "unknown");

        double tps = -1.0;
        try {
            // Paper/Spigot 兼容反射取 TPS
            Object[] tpsArray = (Object[]) plugin.getServer().getClass().getMethod("getTPS").invoke(plugin.getServer());
            if (tpsArray != null && tpsArray.length > 0) {
                tps = ((double) tpsArray[0]);
            }
        } catch (Exception ignored) {
            // 非 Paper 或没有 TPS API
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"pluginVersion\":\"").append(escapeJson(plugin.getDescription().getVersion())).append("\",");
        json.append("\"onlinePlayers\":").append(online).append(",");
        json.append("\"maxPlayers\":").append(max).append(",");
        json.append("\"uptimeMs\":").append(uptime).append(",");
        json.append("\"javaVersion\":\"").append(escapeJson(javaVer)).append("\",");
        json.append("\"osName\":\"").append(escapeJson(os)).append("\",");
        if (tps >= 0) {
            json.append("\"tps\":").append(String.format("%.2f", tps)).append(",");
        }
        json.append("\"modules\":[]");
        json.append("}");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/heartbeat"))
            .header("Content-Type", "application/json")
            .header("X-Module-Token", moduleToken)
            .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
            .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                if (resp.statusCode() != 200) {
                    plugin.consoleWarn("[Cloud] 心跳上报失败: " + resp.statusCode());
                }
            })
            .exceptionally(ex -> {
                plugin.consoleWarn("[Cloud] 心跳上报异常: " + ex.getMessage());
                return null;
            });
    }

    // -- 工具方法 ------------------------------------------------

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            return kpg.generateKeyPair();
        } catch (Exception e) {
            plugin.consoleWarn("[Cloud] 生成 Ed25519 密钥对失败: " + e.getMessage());
            return null;
        }
    }

    /** 用服务器私钥对数据进行 Ed25519 签名，返回 Base64。 */
    private String sign(String data) {
        if (keyPair == null) return "";
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(keyPair.getPrivate());
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            plugin.consoleWarn("[Cloud] 签名失败: " + e.getMessage());
            return "";
        }
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String generateFingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(java.net.InetAddress.getLocalHost().getHostName().getBytes(StandardCharsets.UTF_8));
            md.update(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        // 逐个字符扫描，处理 \" 转义
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    private static String extractJsonArray(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        int bracketOpen = json.indexOf('[', keyIdx + key.length());
        if (bracketOpen < 0) return null;
        int bracketClose = json.indexOf(']', bracketOpen + 1);
        if (bracketClose < 0) return null;
        return json.substring(bracketOpen, bracketClose + 1);
    }

    /** 从 JSON 中提取字符串数组字段（如 ["a","b"]）。 */
    private static List<String> extractStringArray(String json, String field) {
        String arr = extractJsonArray(json, field);
        List<String> result = new ArrayList<>();
        if (arr == null) return result;
        int i = 0;
        while (i < arr.length()) {
            int firstQuote = arr.indexOf('"', i);
            if (firstQuote < 0) break;
            int secondQuote = arr.indexOf('"', firstQuote + 1);
            if (secondQuote < 0) break;
            result.add(arr.substring(firstQuote + 1, secondQuote));
            i = secondQuote + 1;
        }
        return result;
    }
}
