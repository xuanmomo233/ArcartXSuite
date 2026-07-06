package xuanmo.arcartxsuite.cloud;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.SuiteCoreImpl;
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

    /** 云端平台地址：按优先级尝试。 */
    private static final List<String> API_BASE_URLS = List.of(
        "https://cloud.021209.xyz",
        "https://cloud-backup.021209.xyz",
        "https://cloud-mirror.021209.xyz"
    );

    /** 加密密钥对文件格式版本标识。 */
    private static final String KEYPAIR_MAGIC = "AXSKP1";
    /** 指纹绑定密钥派生用的 HKDF salt，必须与云端 crypto.ts 中的 KEY_BIND_SALT 一致。 */
    private static final String KEY_BIND_SALT = "ArcartXSuite-key-bind-v1";
    /** PBKDF2 迭代次数，用于从机器指纹派生密钥文件的加密密钥。 */
    private static final int KDF_ITERATIONS = 120_000;

    private final SuiteCoreImpl plugin;
    private final ModuleRegistry registry;
    /** 为 true 时输出签名、密钥 hash 等敏感诊断日志（见 config.yml cloud.debug）。 */
    private final boolean debugLogging;
    private volatile String apiBaseUrl;
    private final String qq;
    private final String apiKey;
    private final String serverName;

    private volatile String serverCode;
    private final KeyPair keyPair;
    private final String serverPublicKeyB64;

    private volatile String moduleToken;
    private volatile long tokenExpiry;
    private volatile List<String> allowedModules = List.of();
    private static final long AXB_CACHE_TTL_MS = 3600 * 1000L;
    private int syncCounter = 0;
    private static final class CachedAxb {
        final byte[] data;
        final long expiresAt;
        CachedAxb(byte[] data, long expiresAt) { this.data = data; this.expiresAt = expiresAt; }
    }
    private final Map<String, CachedAxb> cachedAxb = new ConcurrentHashMap<>();
    /** 正在下载中的模块去重：同一模块多个并发入口时复用同一个 Future */
    private final Map<String, CompletableFuture<Void>> downloading = new ConcurrentHashMap<>();

    private BukkitTask heartbeatTask;
    private final long pluginStartTime = System.currentTimeMillis();

    public CloudModuleService(SuiteCoreImpl plugin, ModuleRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        this.debugLogging = config.getBoolean("cloud.debug", false);
        this.qq = config.getString("cloud.qq", "").trim();
        this.apiKey = config.getString("cloud.apiKey", "").trim();
        this.serverName = config.getString("cloud.server-name", plugin.getServer().getName());
        this.serverCode = config.getString("cloud.server-code", "").trim();
        this.keyPair = loadOrGenerateKeyPair();
        this.serverPublicKeyB64 = encodeRawEd25519PublicKey(keyPair);
        this.apiBaseUrl = API_BASE_URLS.get(0);
    }

    private enum CloudStatus {
        OK,
        TRANSIENT,
        REJECTED
    }

    private record CloudResponse<T>(CloudStatus status, T body, int httpStatus, String endpoint, String message, String rawBody) {
        boolean isOk() {
            return status == CloudStatus.OK;
        }

        boolean isTransient() {
            return status == CloudStatus.TRANSIENT;
        }

        boolean isRejected() {
            return status == CloudStatus.REJECTED;
        }
    }

    private record PreparedModule(byte[] jarBytes, byte[] moduleSeed) {
    }

    private List<String> orderedApiBaseUrls() {
        List<String> ordered = new ArrayList<>(API_BASE_URLS);
        if (apiBaseUrl != null && !apiBaseUrl.isBlank() && ordered.remove(apiBaseUrl)) {
            ordered.add(0, apiBaseUrl);
        }
        return ordered;
    }

    private void rememberSuccessfulEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            this.apiBaseUrl = endpoint;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.util.concurrent.CompletionException || current instanceof java.util.concurrent.ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    private static boolean isTransientException(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause instanceof java.net.ConnectException
            || cause instanceof java.net.UnknownHostException
            || cause instanceof java.net.http.HttpTimeoutException
            || cause instanceof java.net.SocketTimeoutException
            || cause instanceof java.io.IOException;
    }

    private static boolean isTransientStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private static boolean isRejectedStatus(int statusCode) {
        return statusCode == 401 || statusCode == 403 || statusCode == 410 || (statusCode >= 400 && statusCode < 500);
    }

    private static boolean bodyHasRejectMarker(String body) {
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("revoked") || lower.contains("unbind") || lower.contains("unbound") || lower.contains("disabled");
    }

    private <T> CloudResponse<T> classifyResponse(String endpoint, HttpResponse<T> response, Throwable throwable) {
        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            if (isTransientException(cause)) {
                return new CloudResponse<>(CloudStatus.TRANSIENT, null, 0, endpoint, cause.getMessage(), null);
            }
            return new CloudResponse<>(CloudStatus.REJECTED, null, 0, endpoint, cause.getMessage(), null);
        }
        int statusCode = response.statusCode();
        T body = response.body();
        String rawBody = body instanceof String ? (String) body : null;
        if (statusCode >= 200 && statusCode < 300) {
            if (bodyHasRejectMarker(rawBody)) {
                return new CloudResponse<>(CloudStatus.REJECTED, body, statusCode, endpoint, rawBody, rawBody);
            }
            return new CloudResponse<>(CloudStatus.OK, body, statusCode, endpoint, null, rawBody);
        }
        if (isTransientStatus(statusCode)) {
            return new CloudResponse<>(CloudStatus.TRANSIENT, body, statusCode, endpoint, rawBody, rawBody);
        }
        if (isRejectedStatus(statusCode)) {
            return new CloudResponse<>(CloudStatus.REJECTED, body, statusCode, endpoint, rawBody, rawBody);
        }
        return new CloudResponse<>(CloudStatus.REJECTED, body, statusCode, endpoint, rawBody, rawBody);
    }

    private <T> CompletableFuture<CloudResponse<T>> sendWithFailover(
        String operation,
        java.util.function.Function<String, HttpRequest> requestFactory,
        HttpResponse.BodyHandler<T> bodyHandler
    ) {
        return sendWithFailover(operation, requestFactory, bodyHandler, orderedApiBaseUrls(), 0);
    }

    private <T> CompletableFuture<CloudResponse<T>> sendWithFailover(
        String operation,
        java.util.function.Function<String, HttpRequest> requestFactory,
        HttpResponse.BodyHandler<T> bodyHandler,
        List<String> endpoints,
        int index
    ) {
        if (endpoints == null || index >= endpoints.size()) {
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.TRANSIENT, null, 0, null, operation + " exhausted", null));
        }
        String endpoint = endpoints.get(index);
        HttpRequest request = requestFactory.apply(endpoint);
        return HTTP.sendAsync(request, bodyHandler)
            .handle((response, throwable) -> classifyResponse(endpoint, response, throwable))
            .thenCompose((CloudResponse<T> result) -> {
                if (result.isTransient() && index + 1 < endpoints.size()) {
                    return sendWithFailover(operation, requestFactory, bodyHandler, endpoints, index + 1);
                }
                if ((result.isOk() || result.isRejected()) && result.endpoint() != null) {
                    rememberSuccessfulEndpoint(result.endpoint());
                }
                return CompletableFuture.completedFuture(result);
            });
    }

    private boolean hasServerCode() {
        return serverCode != null && !serverCode.isEmpty();
    }

    private void clearServerCode() {
        this.serverCode = null;
        plugin.getServer().getScheduler().runTask(plugin.host(), () -> {
            plugin.getConfig().set("cloud.server-code", "");
            plugin.saveConfig();
        });
    }

    // -- 服务器首次绑定 ---------------------------------------

    /**
     * 用 QQ+apiKey 向云端换取服务器码。成功后写回 config.yml 的 cloud.server-code。
     */
    private CompletableFuture<CloudResponse<Void>> bindServerGate() {
        if (qq.isEmpty() || apiKey.isEmpty()) {
            plugin.consoleWarn("[Cloud] 未配置 cloud.qq / cloud.apiKey，无法绑定服务器。");
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.REJECTED, null, 0, null, "missing cloud config", null));
        }
        String fingerprint = generateFingerprint();
        String payload = "{"
            + "\"qq\":\"" + escapeJson(qq) + "\","
            + "\"apiKey\":\"" + escapeJson(apiKey) + "\","
            + "\"pubkey\":\"" + serverPublicKeyB64 + "\","
            + "\"fingerprintHash\":\"" + sha256(fingerprint) + "\","
            + "\"name\":\"" + escapeJson(serverName) + "\""
            + "}";

        return sendWithFailover(
            "bind",
            endpoint -> HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/v1/servers/bind"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).thenApply(resp -> {
            if (!resp.isOk()) {
                if (resp.isTransient()) {
                    plugin.consoleWarn("[Cloud] 服务器绑定暂时失败: " + resp.httpStatus() + " " + resp.message());
                } else {
                    plugin.consoleWarn("[Cloud] 服务器绑定失败: " + resp.httpStatus() + " " + resp.message());
                }
                return new CloudResponse<>(resp.status(), null, resp.httpStatus(), resp.endpoint(), resp.message(), resp.rawBody());
            }
            String body = resp.rawBody();
            String code = extractJsonField(body, "serverCode");
            String token = extractJsonField(body, "token");
            String responseApiKey = extractJsonField(body, "apiKey");
            if (code != null && !code.isEmpty()) {
                this.serverCode = code;
                this.allowedModules = extractStringArray(body, "allowedModules");
                if (token != null) {
                    this.moduleToken = token;
                    this.tokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000;
                }
                plugin.getServer().getScheduler().runTask(plugin.host(), () -> {
                    plugin.getConfig().set("cloud.server-code", code);
                    if (responseApiKey != null && !responseApiKey.isEmpty()) {
                        plugin.getConfig().set("cloud.apiKey", responseApiKey);
                    }
                    plugin.saveConfig();
                });
                plugin.consoleInfo("[Cloud] 服务器绑定成功，服务器码: " + code);
                return new CloudResponse<>(CloudStatus.OK, null, resp.httpStatus(), resp.endpoint(), null, body);
            }
            plugin.consoleWarn("[Cloud] 绑定响应缺少 serverCode: " + body);
            return new CloudResponse<>(CloudStatus.REJECTED, null, resp.httpStatus(), resp.endpoint(), "missing serverCode", body);
        });
    }

    public CompletableFuture<Boolean> bindServer() {
        return bindServerGate().thenApply(CloudResponse::isOk);
    }

    // -- 申请/刷新模块令牌 --------------------------------------

    public CompletableFuture<Boolean> refreshToken() {
        return refreshToken(false);
    }

    /**
     * 强制向云端刷新令牌并同步授权模块列表（跳过本地缓存），用于手动触发同步。
     */
    public CompletableFuture<Boolean> forceSyncModules() {
        if (!hasServerCode()) {
            plugin.consoleWarn("[Cloud] 服务器尚未绑定，无法同步。请检查 cloud.qq 和 cloud.apiKey 配置。");
            return CompletableFuture.completedFuture(false);
        }
        return refreshToken(true);
    }

    private CompletableFuture<Boolean> refreshToken(boolean isRebind) {
        return refreshTokenGate(isRebind).thenApply(CloudResponse::isOk);
    }

    private CompletableFuture<CloudResponse<Void>> refreshTokenGate(boolean isRebind) {
        // 自动修复重新绑定后必须强制刷新，以获取正确的 allowedModules
        if (!isRebind && moduleToken != null && System.currentTimeMillis() < tokenExpiry - 300_000) {
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.OK, null, 200, apiBaseUrl, null, null));
        }
        if (!hasServerCode()) {
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.REJECTED, null, 0, null, "missing server code", null));
        }

        long timestamp = System.currentTimeMillis();
        String signMessage = "POST\n/v1/servers/refresh\n" + timestamp + "\n" + serverCode;
        debugLog("[Cloud] 签名消息: " + signMessage.replace("\n", "\\n"));
        debugLog("[Cloud] serverCode: " + serverCode + " | 私钥存在: " + (keyPair != null && keyPair.getPrivate() != null));
        String signature = sign(signMessage);
        debugLog("[Cloud] 签名结果(base64): " + signature);
        String refreshPayload = "{"
            + "\"apiKey\":\"" + escapeJson(apiKey) + "\""
            + "}";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/servers/refresh"))
            .header("Content-Type", "application/json")
            .header("X-Server-Code", serverCode)
            .header("X-Timestamp", String.valueOf(timestamp))
            .header("X-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(refreshPayload))
            .build();

        return sendWithFailover(
            "refresh",
            endpoint -> HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/v1/servers/refresh"))
                .header("Content-Type", "application/json")
                .header("X-Server-Code", serverCode)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(refreshPayload))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).thenCompose(resp -> {
            if (!resp.isOk()) {
                String body = resp.rawBody();
                if (resp.isRejected() && resp.httpStatus() == 403 && !isRebind
                    && (body != null && (body.contains("SIGNATURE_VERIFICATION_ERROR") || body.contains("SIGNATURE_VERIFICATION_FAILED")))) {
                    plugin.consoleWarn("[Cloud] 签名验证失败，自动重新绑定服务器...");
                    clearServerCode();
                    return bindServerGate().thenCompose(bound -> {
                        if (bound.isOk()) {
                            plugin.consoleInfo("[Cloud] 重新绑定成功，再次刷新令牌...");
                            return refreshTokenGate(true);
                        }
                        plugin.consoleWarn("[Cloud] 自动重新绑定失败，跳过云端模块同步。");
                        return CompletableFuture.completedFuture(bound);
                    });
                }
                if (resp.isTransient()) {
                    plugin.consoleWarn("[Cloud] 刷新令牌暂时失败: " + resp.httpStatus() + " " + body);
                } else {
                    plugin.consoleWarn("[Cloud] 刷新令牌失败: " + resp.httpStatus() + " " + body);
                }
                return CompletableFuture.completedFuture(new CloudResponse<>(resp.status(), null, resp.httpStatus(), resp.endpoint(), resp.message(), body));
            }
            String body = resp.rawBody();
            debugLog("[Cloud] refresh 响应: " + body);
            String token = extractJsonField(body, "token");
            if (token != null) {
                this.moduleToken = token;
                this.tokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000;
                List<String> oldAllowed = this.allowedModules;
                this.allowedModules = extractStringArray(body, "allowedModules");
                plugin.consoleInfo("[Cloud] 模块令牌已刷新，授权模块: " + allowedModules.size() + " 列表: " + allowedModules);
                syncModuleChanges(oldAllowed);
                startHeartbeat();
                return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.OK, null, resp.httpStatus(), resp.endpoint(), null, body));
            }
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.REJECTED, null, resp.httpStatus(), resp.endpoint(), "missing token", body));
        });
    }

    // -- 同步并加载云端模块 --------------------------------------

    public CompletableFuture<Void> syncModules() {
        // 1. 未绑定则先用 QQ+apiKey 绑定服务器
        plugin.consoleInfo("[Cloud] 开始云端模块同步...");
        CompletableFuture<CloudResponse<Void>> ready = hasServerCode()
            ? CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.OK, null, 200, apiBaseUrl, null, null))
            : bindServerGate();

        return ready.thenCompose(bound -> {
            if (bound.isTransient()) {
                plugin.consoleWarn("[Cloud] 未能绑定服务器（暂时故障），保留现有云模块并等待下次同步。");
                return CompletableFuture.<Void>completedFuture(null);
            }
            if (!bound.isOk()) {
                plugin.consoleWarn("[Cloud] 未能绑定服务器，跳过云端模块同步。请检查 config.yml 中 cloud.qq 和 cloud.apiKey 是否正确。");
                return CompletableFuture.<Void>completedFuture(null);
            }
            plugin.consoleInfo("[Cloud] 服务器已绑定: " + serverCode);
            return refreshTokenGate(false).thenCompose(refresh -> {
                if (refresh.isTransient()) {
                    plugin.consoleWarn("[Cloud] 刷新模块令牌暂时失败，保留现有云模块。");
                    return CompletableFuture.<Void>completedFuture(null);
                }
                if (!refresh.isOk()) {
                    plugin.consoleWarn("[Cloud] 刷新模块令牌失败，跳过云端模块同步。");
                    return CompletableFuture.<Void>completedFuture(null);
                }
                List<String> mods = allowedModules;
                if (mods == null || mods.isEmpty()) {
                    plugin.consoleInfo("[Cloud] 该服务器未装备任何云端模块。");
                    return CompletableFuture.<Void>completedFuture(null);
                }
                plugin.consoleInfo("[Cloud] 本次需同步 " + mods.size() + " 个云端模块: " + mods);
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                for (String id : mods) {
                    if (id == null || id.isEmpty()) continue;
                    chain = chain.thenCompose(v -> downloadAndLoad(id));
                }
                return chain;
            });
        });
    }

    // -- 手动强制更新模块 ------------------------------------------

    /**
     * 强制更新指定云端模块：卸载旧版本（如已加载）→ 清除 axb 缓存 → 重新下载 → 重新加载。
     *
     * @param moduleId 云端模块 ID
     * @return 更新后是否成功加载
     */
    public CompletableFuture<Boolean> updateModule(String moduleId) {
        if (!isValidModuleId(moduleId)) {
            plugin.consoleWarn("[Cloud] 非法 moduleId 格式，跳过: " + moduleId);
            return CompletableFuture.completedFuture(false);
        }

        // 若本地缓存不包含该模块，先刷新令牌获取最新授权列表（应对启动后云端新装备模块的场景）
        CompletableFuture<Boolean> ready;
        if (!allowedModules.contains(moduleId)) {
            plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 不在本地缓存授权列表中，尝试刷新令牌...");
            ready = refreshToken();
        } else {
            ready = CompletableFuture.completedFuture(true);
        }

        return ready.thenCompose(ok -> {
            if (!ok) {
                plugin.consoleWarn("[Cloud] 刷新令牌失败，无法确认模块 " + moduleId + " 的授权状态。");
                return CompletableFuture.completedFuture(false);
            }
            if (!allowedModules.contains(moduleId)) {
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 不在云端授权列表中，无法通过云端更新。");
                return CompletableFuture.completedFuture(false);
            }

            if (!NativeBridge.isAvailable()) {
                plugin.consoleWarn("[Cloud] Native 安全库未加载，跳过云端模块 " + moduleId + " 的更新。错误: " + NativeBridge.getLoadError());
                return CompletableFuture.completedFuture(false);
            }

            cachedAxb.remove(moduleId);
            return downloadAxb(moduleId).thenCompose(axb -> {
                if (axb == null || axb.length == 0) {
                    plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：axb 下载失败");
                    return CompletableFuture.completedFuture(false);
                }
                if (axb.length < 32) {
                    plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：axb 文件过小");
                    return CompletableFuture.completedFuture(false);
                }
                return requestModuleKey(moduleId).thenCompose(keyResp -> {
                    if (keyResp == null) {
                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：无法获取解密密钥");
                        return CompletableFuture.completedFuture(false);
                    }
                    String keyB64 = extractJsonField(keyResp, "key");
                    if (keyB64 == null || keyB64.isEmpty()) {
                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：云端密钥响应缺少 key 字段");
                        return CompletableFuture.completedFuture(false);
                    }
                    byte[] key;
                    try {
                        key = Base64.getDecoder().decode(keyB64);
                    } catch (IllegalArgumentException e) {
                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：密钥不是有效的 Base64 编码");
                        return CompletableFuture.completedFuture(false);
                    }
                    if (key.length != 32) {
                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：密钥长度错误 (" + key.length + " 字节)");
                        return CompletableFuture.completedFuture(false);
                    }
                    String keyBind = extractJsonField(keyResp, "keyBind");
                    if ("fp-hkdf-v1".equals(keyBind)) {
                        String version = extractJsonField(keyResp, "version");
                        String fph = sha256(generateFingerprint());
                        byte[] pad = hkdfSha256(
                            fph.getBytes(StandardCharsets.UTF_8),
                            KEY_BIND_SALT.getBytes(StandardCharsets.UTF_8),
                            ("axs-module-key|" + moduleId + "|" + (version == null ? "" : version)).getBytes(StandardCharsets.UTF_8),
                            32);
                        if (pad == null) {
                            plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：指纹密钥派生失败");
                            return CompletableFuture.completedFuture(false);
                        }
                        for (int i = 0; i < 32; i++) {
                            key[i] ^= pad[i];
                        }
                    }
                    byte[] sigBytes;
                    try {
                        String sigB64 = extractJsonField(keyResp, "signature");
                        sigBytes = (sigB64 == null || sigB64.isEmpty())
                            ? new byte[0]
                            : Base64.getDecoder().decode(sigB64);
                    } catch (IllegalArgumentException e) {
                        sigBytes = new byte[0];
                    }
                    byte[] jarBytes;
                    try {
                        jarBytes = NativeBridge.n10(axb, key, sigBytes);
                    } catch (Exception e) {
                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " native 解密抛异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        jarBytes = null;
                    }
                    if (jarBytes == null || jarBytes.length == 0) {
                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：native 解密失败");
                        return CompletableFuture.completedFuture(false);
                    }
                    final byte[] finalJarBytes = jarBytes;
                    final byte[] moduleSeed = key.clone();
                    if (!registry.validateCloudModule(finalJarBytes, moduleSeed)) {
                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：新模块未通过本地校验");
                        return CompletableFuture.completedFuture(false);
                    }
                    ModuleRegistry.CloudModuleSnapshot snapshot = registry.getLoadedCloudModuleSnapshot(moduleId).orElse(null);
                    CompletableFuture<Boolean> result = new CompletableFuture<>();
                    plugin.getServer().getScheduler().runTask(plugin.host(), () -> {
                        boolean unloaded = true;
                        if (registry.isModuleLoaded(moduleId)) {
                            unloaded = registry.unloadModule(moduleId);
                        }
                        if (!unloaded) {
                            plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 卸载失败，保留当前版本。");
                            result.complete(false);
                            return;
                        }
                        boolean loaded = registry.loadCloudModule(finalJarBytes, moduleSeed);
                        if (loaded) {
                            plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 更新成功并已加载");
                            result.complete(true);
                            return;
                        }
                        if (snapshot != null && registry.loadCloudModule(snapshot.jarBytes(), snapshot.moduleSeed())) {
                            plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败，已回滚旧版本");
                        } else {
                            plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败且旧版本回滚失败");
                        }
                        result.complete(false);
                    });
                    return result;
                });
            });
        });
    }

    /**
     * 批量更新所有已加载的云端模块。
     *
     * @return true 表示全部更新成功（无模块时也算成功），false 表示至少一个失败
     */
    public CompletableFuture<Boolean> updateAllModules() {
        List<String> loadedCloud = registry.getLoadedCloudModuleIds();
        if (loadedCloud.isEmpty()) {
            plugin.consoleInfo("[Cloud] 没有已加载的云端模块需要更新。");
            return CompletableFuture.completedFuture(true);
        }

        plugin.consoleInfo("[Cloud] 开始批量更新 " + loadedCloud.size() + " 个云端模块: " + loadedCloud);
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (String id : loadedCloud) {
            chain = chain.thenCompose(prev -> updateModule(id).thenApply(current -> prev && current));
        }
        return chain;
    }

    private static final java.util.regex.Pattern MODULE_ID_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]+$");

    private boolean isValidModuleId(String moduleId) {
        return moduleId != null && MODULE_ID_PATTERN.matcher(moduleId).matches();
    }

    /**
     * 指数退避重试包装器。maxRetries=0 表示不重试。
     * 每次重试间隔 = baseDelayMs * 2^attempt
     */
    private <T> CompletableFuture<T> retryAsync(
        String moduleId,
        String operation,
        int maxRetries,
        long baseDelayMs,
        java.util.function.Supplier<CompletableFuture<T>> supplier
    ) {
        return supplier.get().thenCompose(result -> {
            if (result != null || maxRetries <= 0) {
                return CompletableFuture.completedFuture(result);
            }
            return retryWithDelay(moduleId, operation, 1, maxRetries, baseDelayMs, supplier);
        });
    }

    private <T> CompletableFuture<T> retryWithDelay(
        String moduleId,
        String operation,
        int attempt,
        int maxRetries,
        long baseDelayMs,
        java.util.function.Supplier<CompletableFuture<T>> supplier
    ) {
        long delay = baseDelayMs * (1L << (attempt - 1));
        plugin.consoleInfo("[Cloud] 模块 " + moduleId + " " + operation + " 失败，" + delay + "ms 后第 " + attempt + "/" + maxRetries + " 次重试...");

        CompletableFuture<T> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin.host(), () -> {
            supplier.get().thenAccept(result -> {
                if (result != null || attempt >= maxRetries) {
                    future.complete(result);
                } else {
                    retryWithDelay(moduleId, operation, attempt + 1, maxRetries, baseDelayMs, supplier)
                        .thenAccept(future::complete)
                        .exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                }
            }).exceptionally(ex -> {
                future.completeExceptionally(ex);
                return null;
            });
        }, delay / 50L); // Bukkit tick = 50ms
        return future;
    }

    private CompletableFuture<Void> downloadAndLoad(String moduleId) {
        if (!isValidModuleId(moduleId)) {
            plugin.consoleWarn("[Cloud] 非法 moduleId 格式，跳过: " + moduleId);
            return CompletableFuture.<Void>completedFuture(null);
        }
        if (!NativeBridge.isAvailable()) {
            plugin.consoleWarn("[Cloud] Native 安全库未加载，跳过云端模块 " + moduleId + " 的解密加载。错误: " + NativeBridge.getLoadError());
            return CompletableFuture.<Void>completedFuture(null);
        }
        // 并发去重：syncModules 和 syncModuleChanges 可能同时触发同一模块
        CompletableFuture<Void> existing = downloading.get(moduleId);
        if (existing != null) {
            plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 正在下载中，复用已有任务");
            return existing;
        }
        CompletableFuture<Void> placeholder = new CompletableFuture<>();
        if (downloading.putIfAbsent(moduleId, placeholder) != null) {
            return downloading.get(moduleId);
        }

        plugin.consoleInfo("[Cloud] 开始同步模块: " + moduleId + " (native 版本: " + NativeBridge.n0() + ")");
        CompletableFuture<Void> work = retryAsync(moduleId, "axb下载", 3, 2000L, () -> downloadAxb(moduleId)).thenCompose(axb -> {
            if (axb == null || axb.length == 0) {
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：axb 下载失败");
                return CompletableFuture.<Void>completedFuture(null);
            }
            // 新版 axb 结构：magic(4) + iv(12) + ciphertext + authTag(16)，至少 32 字节
            if (axb.length < 32) {
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：axb 文件过小 (" + axb.length + " 字节)，可能下载不完整或文件损坏。期望至少 32 字节 (4 字节 magic + 12 字节 IV + 16 字节 tag)");
                return CompletableFuture.<Void>completedFuture(null);
            }
            return decryptAndLoad(moduleId, axb, false);
        });

        work.whenComplete((v, ex) -> {
            downloading.remove(moduleId);
            if (ex != null) placeholder.completeExceptionally(ex);
            else placeholder.complete(v);
        });
        return placeholder;
    }

    /**
     * 获取模块密钥 → 指纹解绑 → native 解密 → 主线程加载。
     * 当下发了指纹绑定 key（keyBind=fp-hkdf-v1）但 native 解密失败时（多为本机指纹与服务端
     * 存库指纹漂移，导致解绑 pad 不一致、还原出错误 key），自动重新绑定一次（服务端按
     * userQq+pubkey 命中既有 serverCode 并刷新 fingerprintHash）后用刷新的指纹/令牌重试一次，
     * isRetry 作为防循环标志，确保最多自愈一次。
     */
    private CompletableFuture<Void> decryptAndLoad(String moduleId, byte[] axb, boolean isRetry) {
        return retryAsync(moduleId, "密钥获取", 3, 2000L, () -> requestModuleKey(moduleId)).thenCompose(keyResp -> {
            if (keyResp == null) {
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：无法获取解密密钥");
                return CompletableFuture.<Void>completedFuture(null);
            }
            String keyB64 = extractJsonField(keyResp, "key");
            if (keyB64 == null || keyB64.isEmpty()) {
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：云端密钥响应缺少 key 字段");
                return CompletableFuture.<Void>completedFuture(null);
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(keyB64);
            } catch (IllegalArgumentException e) {
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：密钥不是有效的 Base64 编码");
                return CompletableFuture.<Void>completedFuture(null);
            }
            if (key.length != 32) {
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：密钥长度错误 (" + key.length + " 字节)，应为 32 字节。请检查云端模块上传时填写的 moduleKey");
                return CompletableFuture.<Void>completedFuture(null);
            }
            // 指纹绑定密钥还原：云端下发的 key = moduleKey XOR HKDF(本机指纹)。
            // 用本机实时指纹重新派生 pad 还原真实 moduleKey；换机器指纹不同 → 还原出错误 key → native 解密失败。
            String keyBind = extractJsonField(keyResp, "keyBind");
            if ("fp-hkdf-v1".equals(keyBind)) {
                String version = extractJsonField(keyResp, "version");
                String fph = sha256(generateFingerprint());
                byte[] pad = hkdfSha256(
                    fph.getBytes(StandardCharsets.UTF_8),
                    KEY_BIND_SALT.getBytes(StandardCharsets.UTF_8),
                    ("axs-module-key|" + moduleId + "|" + (version == null ? "" : version)).getBytes(StandardCharsets.UTF_8),
                    32);
                if (pad == null) {
                    plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：指纹密钥派生失败");
                    return CompletableFuture.<Void>completedFuture(null);
                }
                for (int i = 0; i < 32; i++) {
                    key[i] ^= pad[i];
                }
            }
            if (debugLogging) {
                String keyHash = sha256Hex(key);
                String ivHex = bytesToHex(java.util.Arrays.copyOfRange(axb, 4, 16));
                debugLog("[Cloud] 模块 " + moduleId + " 诊断: keyHash=" + keyHash + ", ivHex=" + ivHex + ", axbLen=" + axb.length);
            }

            // V6：.axb Ed25519 签名（服务端用平台私钥对完整 axb 签名）。
            // native 在 AES-GCM 解密前先验签；签名为空（后端未启用签名）时 native 跳过验签。
            String sigB64 = extractJsonField(keyResp, "signature");
            byte[] sigBytes;
            try {
                sigBytes = (sigB64 == null || sigB64.isEmpty())
                    ? new byte[0]
                    : Base64.getDecoder().decode(sigB64);
            } catch (IllegalArgumentException e) {
                sigBytes = new byte[0];
            }
            byte[] jarBytes;
            try {
                // native 自己处理 magic(4)，传完整 axb；先验签再解密
                jarBytes = NativeBridge.n10(axb, key, sigBytes);
            } catch (Exception e) {
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " native 解密抛异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                jarBytes = null;
            }
            if (jarBytes == null || jarBytes.length == 0) {
                // 自愈：指纹绑定 key 但 native 解密失败，多为本机指纹与服务端存库指纹漂移。
                // 重新绑定一次（服务端按 userQq+pubkey 命中既有 serverCode 并刷新指纹）后重试，isRetry 防循环。
                if (!isRetry && "fp-hkdf-v1".equals(keyBind)) {
                    plugin.consoleWarn("[Cloud] 模块 " + moduleId + " native 解密失败，疑似本机指纹已变化，自动重新绑定并重试一次...");
                    return bindServer().thenCompose(bound -> {
                        if (!bound) {
                            plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 自动重新绑定失败，放弃加载。");
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        return refreshToken(true).thenCompose(ok -> {
                            if (!ok) {
                                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 重绑后刷新令牌失败，放弃加载。");
                                return CompletableFuture.<Void>completedFuture(null);
                            }
                            plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 已重新绑定指纹，重试解密同步...");
                            return decryptAndLoad(moduleId, axb, true);
                        });
                    });
                }
                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " native 解密失败，已拒绝加载该模块。"
                    + "解密仅在 native 安全库中进行，不提供 Java 回退。请确认 native 库版本与 axb 匹配且 moduleKey 正确。");
                return CompletableFuture.<Void>completedFuture(null);
            }
            plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 解密成功: " + jarBytes.length + " 字节");
            final byte[] finalJarBytes = jarBytes;
            // 方案 B：把云端下发的 moduleKey 同时作为逐类解密种子下传给 ByteArrayModuleClassLoader（n11），
            // 使模块逐类加密与本体 root_seed 解耦、自包含。clone 一份，避免被后续清理影响。
            final byte[] moduleSeed = key.clone();
            plugin.getServer().getScheduler().runTask(plugin.host(), () -> {
                if (registry.isModuleLoaded(moduleId)) {
                    plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 已加载，跳过重复加载");
                    return;
                }
                boolean ok = registry.loadCloudModule(finalJarBytes, moduleSeed);
                if (ok) {
                    plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 已加载");
                } else {
                    plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 加载失败：可能缺少依赖或该模块已在 config.yml 中关闭");
                }
            });
            return CompletableFuture.<Void>completedFuture(null);
        });
    }

    private CompletableFuture<byte[]> downloadAxb(String moduleId) {
        CachedAxb cached = cachedAxb.get(moduleId);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            plugin.consoleInfo("[Cloud] 使用缓存 axb: " + moduleId + " (" + cached.data.length + " 字节)");
            return CompletableFuture.completedFuture(cached.data);
        }
        return sendWithFailover(
            "download",
            endpoint -> HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/v1/modules/" + moduleId + "/download"))
                .header("X-Module-Token", moduleToken)
                .header("X-Server-Code", serverCode)
                .header("X-Fingerprint", sha256(generateFingerprint()))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray()
        ).thenApply(resp -> {
            if (!resp.isOk()) {
                if (resp.isTransient()) {
                    plugin.consoleWarn("[Cloud] 下载 " + moduleId + " 暂时不可达: " + resp.httpStatus() + " " + resp.message());
                } else {
                    plugin.consoleWarn("[Cloud] 下载 " + moduleId + " 被拒绝: " + resp.httpStatus() + " " + resp.message());
                }
                return null;
            }
            byte[] data = resp.body();
            if (data == null || data.length == 0) {
                plugin.consoleWarn("[Cloud] 下载 " + moduleId + " 成功但响应体为空");
                return null;
            }
            plugin.consoleInfo("[Cloud] 下载 " + moduleId + " 成功: " + data.length + " 字节");
            cachedAxb.put(moduleId, new CachedAxb(data, System.currentTimeMillis() + AXB_CACHE_TTL_MS));
            rememberSuccessfulEndpoint(resp.endpoint());
            return data;
        });
    }

    private CompletableFuture<String> requestModuleKey(String moduleId) {
        return sendWithFailover(
            "key",
            endpoint -> HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/v1/modules/" + moduleId + "/key"))
                .header("Content-Type", "application/json")
                .header("X-Module-Token", moduleToken)
                .header("X-Server-Code", serverCode)
                .header("X-Fingerprint", sha256(generateFingerprint()))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).thenApply(resp -> {
            if (!resp.isOk()) {
                if (resp.isTransient()) {
                    plugin.consoleWarn("[Cloud] 获取模块 " + moduleId + " 密钥暂时失败: " + resp.httpStatus() + " " + resp.message());
                } else {
                    plugin.consoleWarn("[Cloud] 获取模块 " + moduleId + " 密钥被拒绝: " + resp.httpStatus() + " " + resp.message());
                }
                return null;
            }
            String body = resp.rawBody();
            if (body == null || body.isEmpty()) {
                plugin.consoleWarn("[Cloud] 获取模块 " + moduleId + " 密钥成功但响应体为空");
                return null;
            }
            String key = extractJsonField(body, "key");
            if (key == null || key.isEmpty()) {
                plugin.consoleWarn("[Cloud] 获取模块 " + moduleId + " 密钥失败，响应中缺少 key 字段");
                return null;
            }
            plugin.consoleInfo("[Cloud] 获取模块 " + moduleId + " 密钥成功");
            rememberSuccessfulEndpoint(resp.endpoint());
            return body;
        });
    }

    // -- 心跳 ------------------------------------------------

    private void startHeartbeat() {
        if (heartbeatTask != null) return;
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin.host(), this::sendHeartbeat, 30L * 20L, 30L * 20L);
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

        syncCounter++;
        boolean shouldSync = syncCounter >= 60 || System.currentTimeMillis() > tokenExpiry - 5 * 60 * 1000;
        if (shouldSync) {
            syncCounter = 0;
        }

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

        sendWithFailover(
            "heartbeat",
            endpoint -> HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/v1/heartbeat"))
                .header("Content-Type", "application/json")
                .header("X-Module-Token", moduleToken)
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).thenAccept(resp -> {
            if (resp.isOk()) {
                debugLog("[Cloud] 心跳响应: " + resp.rawBody());
            } else if (resp.isTransient()) {
                plugin.consoleWarn("[Cloud] 心跳暂时失败: " + resp.httpStatus() + " " + resp.message());
            } else {
                plugin.consoleWarn("[Cloud] 心跳上报失败: " + resp.httpStatus() + " " + resp.message());
            }
        }).thenRun(() -> {
            if (shouldSync) {
                refreshToken().thenAccept(ok -> {
                    if (ok) {
                        plugin.consoleInfo("[Cloud] 定期同步完成");
                    }
                });
            }
        }).exceptionally(ex -> {
            plugin.consoleWarn("[Cloud] 心跳上报异常: " + ex.getMessage());
            return null;
        });
    }

    // -- 模块授权变更同步 ------------------------------------------

    /**
     * 对比前后授权列表，卸载已撤销的云端模块，加载新授权的模块。
     */
    private void syncModuleChanges(List<String> oldAllowed) {
        List<String> currentAllowed = this.allowedModules;
        List<String> loadedCloud = registry.getLoadedCloudModuleIds();

        // 需要卸载的：已加载的云端模块不再在授权列表中
        for (String id : loadedCloud) {
            if (!currentAllowed.contains(id)) {
                plugin.getServer().getScheduler().runTask(plugin.host(), () -> {
                    boolean ok = registry.unloadModule(id);
                    if (ok) {
                        plugin.consoleInfo("[Cloud] 模块 " + id + " 授权已撤销，已卸载");
                    }
                    cachedAxb.remove(id);
                });
            }
        }

        // 需要加载的：新出现在授权列表中且尚未加载
        List<String> toLoad = currentAllowed.stream()
            .filter(id -> !loadedCloud.contains(id))
            .toList();

        if (!toLoad.isEmpty()) {
            plugin.consoleInfo("[Cloud] 检测到 " + toLoad.size() + " 个新授权模块，开始加载: " + toLoad);
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (String id : toLoad) {
                chain = chain.thenCompose(v -> downloadAndLoad(id));
            }
        }
    }

    // -- 工具方法 ------------------------------------------------

    private void debugLog(String message) {
        if (debugLogging) {
            plugin.consoleInfo(message);
        }
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            return kpg.generateKeyPair();
        } catch (Exception e) {
            plugin.consoleWarn("[Cloud] 生成 Ed25519 密钥对失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从持久化文件加载 Ed25519 密钥对；不存在则生成并保存。
     * 必须持久化：每次生成新密钥对会导致后端 pubkey 与私钥不匹配。
     */
    private KeyPair loadOrGenerateKeyPair() {
        File keyFile = new File(plugin.getDataFolder(), ".cloud-keypair");
        if (keyFile.exists()) {
            KeyPair loaded = tryLoadKeyPair(keyFile);
            if (loaded != null) {
                return loaded;
            }
            plugin.consoleWarn("[Cloud] 持久化密钥对无法在本机解密或已损坏，将重新生成（会触发自动重新绑定）。");
        }
        KeyPair kp = generateKeyPair();
        if (kp != null) {
            saveKeyPair(kp, keyFile);
        }
        return kp;
    }

    /**
     * 尝试加载持久化密钥对：优先按加密格式（AXSKP1）解密。
     */
    private KeyPair tryLoadKeyPair(File keyFile) {
        try {
            String content = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.UTF_8).trim();
            String[] parts = content.split("\\R");
            if (parts.length >= 5 && KEYPAIR_MAGIC.equals(parts[0].trim())) {
                byte[] publicEncoded = Base64.getDecoder().decode(parts[1].trim());
                byte[] salt = Base64.getDecoder().decode(parts[2].trim());
                byte[] iv = Base64.getDecoder().decode(parts[3].trim());
                byte[] ciphertext = Base64.getDecoder().decode(parts[4].trim());
                byte[] privateEncoded = decryptPrivateKey(ciphertext, iv, salt);
                if (privateEncoded == null) return null;
                KeyPair kp = rebuildKeyPair(publicEncoded, privateEncoded);
                if (kp != null) {
                    debugLog("[Cloud] 已加载加密持久化 Ed25519 密钥对，裸公钥: " + encodeRawEd25519PublicKey(kp));
                }
                return kp;
            }
        } catch (Exception e) {
            plugin.consoleWarn("[Cloud] 读取持久化密钥对失败: " + e.getMessage());
        }
        return null;
    }

    private KeyPair rebuildKeyPair(byte[] publicEncoded, byte[] privateEncoded) {
        try {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(publicEncoded));
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateEncoded));
            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            plugin.consoleWarn("[Cloud] 重建密钥对失败: " + e.getMessage());
            return null;
        }
    }

    private void saveKeyPair(KeyPair keyPair, File keyFile) {
        try {
            if (!keyFile.getParentFile().exists()) {
                keyFile.getParentFile().mkdirs();
            }
            byte[] salt = new byte[16];
            byte[] iv = new byte[12];
            SecureRandom rng = new SecureRandom();
            rng.nextBytes(salt);
            rng.nextBytes(iv);
            byte[] ciphertext = encryptPrivateKey(keyPair.getPrivate().getEncoded(), iv, salt);
            if (ciphertext == null) {
                plugin.consoleWarn("[Cloud] 私钥加密失败，未写入密钥文件。");
                return;
            }
            String out = KEYPAIR_MAGIC + "\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()) + "\n"
                + Base64.getEncoder().encodeToString(salt) + "\n"
                + Base64.getEncoder().encodeToString(iv) + "\n"
                + Base64.getEncoder().encodeToString(ciphertext);
            Files.write(keyFile.toPath(), out.getBytes(StandardCharsets.UTF_8));
            restrictFilePermissions(keyFile);
            plugin.consoleInfo("[Cloud] Ed25519 密钥对已加密持久化到 " + keyFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.consoleWarn("[Cloud] 保存密钥对失败: " + e.getMessage());
        }
    }

    /** 基于机器指纹派生 256 位 AES 密钥，使密钥文件与本机硬件绑定，复制到其它机器无法解密。 */
    private static SecretKeySpec deriveKeyFromFingerprint(byte[] salt) throws Exception {
        char[] password = generateFingerprint().toCharArray();
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, KDF_ITERATIONS, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] encryptPrivateKey(byte[] plaintext, byte[] iv, byte[] salt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKeyFromFingerprint(salt), new GCMParameterSpec(128, iv));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            plugin.consoleWarn("[Cloud] 私钥加密异常: " + e.getMessage());
            return null;
        }
    }

    private byte[] decryptPrivateKey(byte[] ciphertext, byte[] iv, byte[] salt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKeyFromFingerprint(salt), new GCMParameterSpec(128, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            // 指纹变化（换机器/换网卡）或文件损坏都会走到这里
            return null;
        }
    }

    /** 尽力收紧密钥文件权限（POSIX 设为 owner-only；Windows 等不支持则降级为只读）。 */
    private void restrictFilePermissions(File keyFile) {
        try {
            java.nio.file.Path path = keyFile.toPath();
            java.nio.file.attribute.PosixFileAttributeView posix =
                Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class);
            if (posix != null) {
                Files.setPosixFilePermissions(path, java.util.EnumSet.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } else {
                keyFile.setReadable(false, false);
                keyFile.setReadable(true, true);
            }
        } catch (Exception ignored) {
            // 权限收紧失败不影响主流程
        }
    }

    /**
     * 从 Java X.509 SubjectPublicKeyInfo 编码中提取裸 32 字节 Ed25519 公钥。
     * Java 15+ 的 EdECPublicKey.getEncoded() 返回 44 字节 X.509 格式
     *（12 字节 ASN.1 头 + 32 字节裸公钥），后端验证需要裸 32 字节。
     */
    private static String encodeRawEd25519PublicKey(KeyPair keyPair) {
        if (keyPair == null) return "";
        byte[] encoded = keyPair.getPublic().getEncoded();
        // 标准 X.509 SubjectPublicKeyInfo for Ed25519 固定 44 字节，最后 32 字节为裸公钥
        if (encoded != null && encoded.length == 44) {
            byte[] raw = java.util.Arrays.copyOfRange(encoded, 12, 44);
            return Base64.getEncoder().encodeToString(raw);
        }
        return Base64.getEncoder().encodeToString(encoded);
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

    /**
     * 生成机器指纹。除操作系统信息外，还纳入网卡 MAC 地址、主机名、CPU 核心数等
     * 较难在虚拟机中伪造的硬件特征，使凭据更难脱离原机器复用。
     * MAC 列表经排序，保证同一机器多次启动结果稳定。
     */
    private static String generateFingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(System.getProperty("os.name", "unknown").getBytes(StandardCharsets.UTF_8));
            md.update(System.getProperty("os.arch", "unknown").getBytes(StandardCharsets.UTF_8));
            md.update(System.getProperty("os.version", "unknown").getBytes(StandardCharsets.UTF_8));
            md.update(Integer.toString(Runtime.getRuntime().availableProcessors()).getBytes(StandardCharsets.UTF_8));
            try {
                md.update(java.net.InetAddress.getLocalHost().getHostName().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // 某些环境无法解析主机名，忽略
            }
            for (String mac : collectHardwareAddresses()) {
                md.update(mac.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 收集所有可用物理网卡的 MAC 地址（去重、排序、过滤回环与未启用网卡）。 */
    private static List<String> collectHardwareAddresses() {
        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                try {
                    if (nic.isLoopback() || nic.isVirtual() || !nic.isUp()) continue;
                } catch (Exception ignored) {
                    continue;
                }
                byte[] hw = nic.getHardwareAddress();
                if (hw == null || hw.length == 0) continue;
                String mac = bytesToHex(hw);
                if (!macs.contains(mac)) {
                    macs.add(mac);
                }
            }
        } catch (Exception ignored) {
            // 无法枚举网卡时返回已收集到的内容
        }
        java.util.Collections.sort(macs);
        return macs;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * HKDF-SHA256（RFC 5869），仅用于从机器指纹派生模块密钥还原 pad。
     * 使用 HMAC-SHA256（非 AES/GZIP/Cipher 解密），不构成模块解密回退路径。
     */
    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            // extract: PRK = HMAC(salt, ikm)
            byte[] saltKey = (salt == null || salt.length == 0) ? new byte[32] : salt;
            mac.init(new javax.crypto.spec.SecretKeySpec(saltKey, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            // expand: T(n) = HMAC(PRK, T(n-1) | info | n)
            mac.init(new javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"));
            byte[] okm = new byte[length];
            byte[] t = new byte[0];
            int pos = 0;
            byte counter = 1;
            while (pos < length) {
                mac.update(t);
                mac.update(info);
                mac.update(counter);
                t = mac.doFinal();
                int n = Math.min(t.length, length - pos);
                System.arraycopy(t, 0, okm, pos, n);
                pos += n;
                counter++;
            }
            return okm;
        } catch (Exception e) {
            return null;
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

    /** 从 JSON 中提取字符串数组字段（如 ["a","b"]），正确处理转义引号。 */
    private static List<String> extractStringArray(String json, String field) {
        String arr = extractJsonArray(json, field);
        List<String> result = new ArrayList<>();
        if (arr == null) return result;
        int i = 0;
        while (i < arr.length()) {
            int firstQuote = arr.indexOf('"', i);
            if (firstQuote < 0) break;
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            int j = firstQuote + 1;
            boolean found = false;
            for (; j < arr.length(); j++) {
                char c = arr.charAt(j);
                if (escaped) {
                    sb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    found = true;
                    break;
                } else {
                    sb.append(c);
                }
            }
            if (found) {
                result.add(sb.toString());
                i = j + 1;
            } else {
                break;
            }
        }
        return result;
    }

    // -- 诊断辅助方法 ----------------------------------------------

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(data));
        } catch (Exception e) {
            return "ERR";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

}
