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
import xuanmo.arcartxsuite.module.ModuleRegistry.CloudModuleSnapshot;
import xuanmo.arcartxsuite.security.NativeBridge;
import xuanmo.arcartxsuite.security.protection.ProtectionEnvironment;
import xuanmo.arcartxsuite.security.protection.ProtectionInit;


/**

 * 云端模块服务：负责与 AXS Cloud Platform 交互，下载、解密并加载云端模块。

 */
public final class CloudModuleService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    /** 请求级总超时（连接+响应），防止对端缓慢挂起时请求永久阻塞。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    /** 网络层失败时同端点重试前的延迟（容忍瞬态抖动/高延迟，不立即切换备用端）。 */
    private static final long RETRY_DELAY_MS = 1500;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();


    /** 云端平台地址：按优先级尝试。 */

    private static final List<String> API_BASE_URLS = List.of(
        "https://cloud.021209.xyz",
        "https://flare.021209.xyz"
    );


    /** 加密密钥对文件格式版本标识（旧版：机器指纹加密私钥，仅用于迁移读取）。 */

    private static final String KEYPAIR_MAGIC = "AXSKP1";

    /** 当前密钥对文件格式：AXSKP2，私钥由文件内 serverSecret 派生密钥加密，与机器无关。 */

    private static final String KEYPAIR_MAGIC_V2 = "AXSKP2";

    /** 指纹绑定密钥派生用的 HKDF salt，必须与云端 crypto.ts 中的 KEY_BIND_SALT 一致。 */

    private static final String KEY_BIND_SALT = "ArcartXSuite-key-bind-v1";

    /** PBKDF2 迭代次数，用于从机器指纹派生密钥文件的加密密钥。 */

    private static final int KDF_ITERATIONS = 120_000;
    private static final List<String> EXCLUDED_INTERFACE_PATTERNS = List.of(
        "tun", "tap", "vpn", "ppp", "docker", "veth", "vethernet", "wsl",
        "vmware", "vmnet", "virtualbox", "vbox", "hyper-v", "hyperv", "loopback",
        "isatap", "teredo", "bluetooth", "zerotier", "tailscale", "wg", "wireguard",
        "clash", "radmin", "npcap", "pcap"
    );

    private final SuiteCoreImpl plugin;
    private final ModuleRegistry registry;
    private final CloudTasks tasks;

    /** 为 true 时输出签名、密钥 hash 等敏感诊断日志（见 config.yml cloud.debug）。 */

    private final boolean debugLogging;
    /** true 时 native 验签不可用（明文构建/库缺失）云端响应一律按暂时故障拒绝 */
    private final boolean requireNativeVerify;
    /** 验签不可用告警只发一次（启动后首条云端响应），避免每请求刷屏 */
    private volatile boolean insecureVerifyWarned;
    private volatile String apiBaseUrl;
    private final String qq;
    private final String apiKey;
    private final String serverName;

    private volatile String serverCode;
    private final KeyPair keyPair;
    private final String serverPublicKeyB64;

    /** 服务器密钥锚：.cloud-keypair 内随机持久化，模块密钥下发绑定的身份根，与机器指纹无关。 */

    private volatile byte[] serverSecret;

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
    private final ResponseSignatureVerifier responseSignatureVerifier = new ResponseSignatureVerifier();
    private final String suiteRelease;
    private final String customerRelease;
    private final boolean localModuleSource;
    private final RuntimeAuthorization runtimeAuthorization = new RuntimeAuthorization();
    private CompletableFuture<Void> pendingRuntimeLease;
    private BukkitTask runtimeLeaseWatchdog;
    private volatile boolean protectedModulesSuspended;

    private BukkitTask heartbeatTask;
    private final long pluginStartTime = System.currentTimeMillis();

    public CloudModuleService(SuiteCoreImpl plugin, ModuleRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        String verifiedRelease = parseSuiteRelease(ProtectionInit.verifiedBuildProvenance());
        this.suiteRelease = verifiedRelease.isEmpty() ? readSuiteRelease(plugin) : verifiedRelease;
        this.customerRelease = RuntimeAuthorization.isCustomerRelease(verifiedRelease) ? verifiedRelease : null;
        this.tasks = new CloudTasks((action, delay, async) -> {
            var scheduler = plugin.getServer().getScheduler();
            BukkitTask scheduled = async
                ? scheduler.runTaskLaterAsynchronously(plugin.host(), action, delay)
                : scheduler.runTaskLater(plugin.host(), action, delay);
            return scheduled::cancel;
        });
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        this.debugLogging = config.getBoolean("cloud.debug", false);
        this.requireNativeVerify = config.getBoolean("cloud.require-native-verify", false);
        this.qq = config.getString("cloud.qq", "").trim();
        this.apiKey = config.getString("cloud.apiKey", "").trim();
        this.serverName = config.getString("cloud.server-name", plugin.getServer().getName());
        this.serverCode = config.getString("cloud.server-code", "").trim();
        this.keyPair = loadOrGenerateKeyPair();
        this.serverPublicKeyB64 = encodeRawEd25519PublicKey(keyPair);
        String source = config.getString("cloud.module-source", "cloud");
        if (!java.util.Set.of("cloud", "local").contains(source))
            throw new IllegalArgumentException("cloud.module-source must be cloud or local");
        this.localModuleSource = "local".equals(source) || RuntimeAuthorization.isCustomerRelease(suiteRelease);
        registry.setProtectedModuleAuthorization(this::moduleRuntimeAuthorized);

        this.apiBaseUrl = API_BASE_URLS.get(0);
    }

    private static String readSuiteRelease(SuiteCoreImpl plugin) {
        try (var input = plugin.getResource("axs-build.json")) {
            if (input == null) return "";
            return parseSuiteRelease(new String(input.readNBytes(4096), StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            plugin.consoleWarn("[Cloud] 无法读取宿主发行批次: " + failure.getMessage());
            return "";
        }
    }

    private static String parseSuiteRelease(String provenance) {
        if (provenance.isEmpty()) return "";
        try {
            var release = com.google.gson.JsonParser.parseString(provenance).getAsJsonObject().getAsJsonPrimitive("release");
            if (release != null && release.isString()
                && release.getAsString().matches("[A-Za-z0-9._-]+-suite\\.\\d+\\.\\d+-ci\\.[0-9a-f]{7}")) {
                return release.getAsString();
            }
        } catch (RuntimeException invalid) {
            return "";
        }
        return "";
    }

    /** moduleId 为 null 时校验宿主功能；模块功能需要同一客户批次的有效模块席位。 */
    public boolean customerFeaturesAllowed(String moduleId) {
        return !tasks.isClosed()
            && runtimeAuthorization.permitsCustomerFeatures(customerRelease, moduleId, System.nanoTime());
    }

    private boolean moduleRuntimeAuthorized(String moduleId) {
        return customerRelease == null ? runtimeAuthorization.permits(moduleId) : customerFeaturesAllowed(moduleId);
    }

    List<String> authorizedModuleIds() {
        return customerRelease == null ? allowedModules : runtimeModuleIds().stream().sorted().toList();
    }

    private java.util.Set<String> runtimeModuleIds() {
        return customerRelease == null ? runtimeAuthorization.permittedModules()
            : runtimeAuthorization.permittedCustomerModules(customerRelease);
    }

    void acceptPublicModuleList(String responseBody) {
        if (customerRelease == null) allowedModules = extractStringArray(responseBody, "allowedModules");
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

    private record SignedCloudResponse(
        CloudStatus status,
        byte[] body,
        int httpStatus,
        String endpoint,
        String message,
        String rawBody,
        String signatureB64,
        String signatureTs
    ) {
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
            || cause instanceof java.util.concurrent.TimeoutException
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
        return sendWithFailover(operation, requestFactory, bodyHandler, orderedApiBaseUrls(), 0, 0);
    }

    private static CompletableFuture<Void> retryDelay() {
        return CompletableFuture.runAsync(() -> {},
            CompletableFuture.delayedExecutor(RETRY_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    private <T> CompletableFuture<CloudResponse<T>> sendWithFailover(
        String operation,
        java.util.function.Function<String, HttpRequest> requestFactory,
        HttpResponse.BodyHandler<T> bodyHandler,
        List<String> endpoints,
        int index,
        int attempt
    ) {
        if (tasks.isClosed()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("云端模块服务正在停止"));
        if (endpoints == null || index >= endpoints.size()) {
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.TRANSIENT, null, 0, null, operation + " exhausted", null));
        }
        String endpoint = endpoints.get(index);
        HttpRequest request = requestFactory.apply(endpoint);
        return tasks.transfer(HTTP.sendAsync(request, bodyHandler))
            .orTimeout(REQUEST_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS)
            .handle((response, throwable) -> classifyResponse(endpoint, response, throwable))
            .thenCompose((CloudResponse<T> result) -> {
                if (tasks.isClosed()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("云端模块服务正在停止"));
                if (result.isTransient() || ("download".equals(operation)
                    && (result.httpStatus() == 404 || result.httpStatus() == 409))) {
                    // 网络层失败（httpStatus=0：DNS/连接/超时）先延迟重试本端点一次；
                    // HTTP 层暂时性状态（429/5xx）直接换下一端点，不做无谓重试。
                    if (result.httpStatus() == 0 && attempt < 1) {
                        return retryDelay().thenCompose(v ->
                            sendWithFailover(operation, requestFactory, bodyHandler, endpoints, index, attempt + 1));
                    }
                    if (index + 1 < endpoints.size()) {
                        return sendWithFailover(operation, requestFactory, bodyHandler, endpoints, index + 1, 0);
                    }
                }
                if ((result.isOk() || result.isRejected()) && result.endpoint() != null) {
                    rememberSuccessfulEndpoint(result.endpoint());
                }
                return CompletableFuture.completedFuture(result);
            });

    }


    private CompletableFuture<SignedCloudResponse> sendSignedWithFailover(
        String operation,
        java.util.function.Function<String, HttpRequest> requestFactory,
        java.util.function.Function<SignedCloudResponse, SignedCloudResponse> responseProcessor
    ) {
        return sendSignedWithFailover(operation, requestFactory, responseProcessor, orderedApiBaseUrls(), 0, 0, false);
    }

    private CompletableFuture<SignedCloudResponse> sendSignedWithFailover(
        String operation,
        java.util.function.Function<String, HttpRequest> requestFactory,
        java.util.function.Function<SignedCloudResponse, SignedCloudResponse> responseProcessor,
        List<String> endpoints,
        int index,
        int attempt,
        boolean sawTransient
    ) {
        if (tasks.isClosed()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("云端模块服务正在停止"));
        if (endpoints == null || index >= endpoints.size()) {
            return CompletableFuture.completedFuture(new SignedCloudResponse(CloudStatus.TRANSIENT, null, 0, null, operation + " exhausted", null, null, null));
        }
        String endpoint = endpoints.get(index);
        HttpRequest request = requestFactory.apply(endpoint);
        return tasks.transfer(HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()))
            .orTimeout(REQUEST_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS)
            .handle((response, throwable) -> classifySignedResponse(endpoint, response, throwable))
            .thenCompose(result -> {
                if (tasks.isClosed()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("云端模块服务正在停止"));
                SignedCloudResponse processed = responseProcessor.apply(result);
                if (processed.isTransient() || ("key".equals(operation)
                    && (processed.httpStatus() == 404 || processed.httpStatus() == 409))) {
                    if (processed.httpStatus() == 0 && attempt < 1) {
                        return retryDelay().thenCompose(v ->
                            sendSignedWithFailover(operation, requestFactory, responseProcessor, endpoints, index, attempt + 1, true));
                    }
                    if (index + 1 < endpoints.size()) {
                        return sendSignedWithFailover(operation, requestFactory, responseProcessor, endpoints, index + 1, 0, true);
                    }
                }
                // 主备库可能尚未同步；先寻找认识此服务器码的端点，不能直接重绑生成新码。
                if ("refresh".equals(operation) && processed.isRejected()
                    && CloudRefreshPolicy.isMissingServer(processed.httpStatus(), processed.rawBody())) {
                    if (index + 1 < endpoints.size()) {
                        plugin.consoleWarn("[Cloud] 当前端点未找到服务器记录，尝试备用端点: " + endpoint);
                        return sendSignedWithFailover(operation, requestFactory, responseProcessor, endpoints, index + 1, 0, sawTransient)
                            .thenApply(alternative -> {
                                // 备用端在主站健康时默认关闭；明确的维护响应不能阻止主站修复失效码。
                                if (!sawTransient && alternative.isTransient()
                                    && CloudRefreshPolicy.isStandbyDisabled(alternative.httpStatus(), alternative.rawBody())
                                    && java.util.Objects.equals(alternative.message(), alternative.rawBody())) {
                                    return processed;
                                }
                                return alternative;
                            });
                    }
                    if (sawTransient) {
                        return CompletableFuture.completedFuture(transientSignedResponse(processed,
                            "部分端点不可用，无法确认服务器记录缺失，保留原绑定"));
                    }
                }
                return CompletableFuture.completedFuture(processed);
            });
    }

    private SignedCloudResponse classifySignedResponse(String endpoint, HttpResponse<byte[]> response, Throwable throwable) {
        if (throwable != null) {
            Throwable cause = unwrap(throwable);
            if (isTransientException(cause)) {
                return new SignedCloudResponse(CloudStatus.TRANSIENT, null, 0, endpoint, cause.getMessage(), null, null, null);
            }
            return new SignedCloudResponse(CloudStatus.REJECTED, null, 0, endpoint, cause.getMessage(), null, null, null);
        }
        int statusCode = response.statusCode();
        byte[] body = response.body();
        String rawBody = body == null ? null : new String(body, StandardCharsets.UTF_8);
        String signature = response.headers().firstValue("X-AXS-Signature").orElse(null);
        String signatureTs = response.headers().firstValue("X-AXS-Signature-Ts").orElse(null);
        if (statusCode >= 200 && statusCode < 300) {
            if (bodyHasRejectMarker(rawBody)) {
                return new SignedCloudResponse(CloudStatus.REJECTED, body, statusCode, endpoint, rawBody, rawBody, signature, signatureTs);
            }
            return new SignedCloudResponse(CloudStatus.OK, body, statusCode, endpoint, null, rawBody, signature, signatureTs);
        }
        if (isTransientStatus(statusCode)) {
            return new SignedCloudResponse(CloudStatus.TRANSIENT, body, statusCode, endpoint, rawBody, rawBody, signature, signatureTs);
        }
        if (isRejectedStatus(statusCode)) {
            return new SignedCloudResponse(CloudStatus.REJECTED, body, statusCode, endpoint, rawBody, rawBody, signature, signatureTs);
        }
        return new SignedCloudResponse(CloudStatus.REJECTED, body, statusCode, endpoint, rawBody, rawBody, signature, signatureTs);
    }

    private SignedCloudResponse validateSignedResponse(String endpointKey, String label, SignedCloudResponse resp) {
        if (!responseSignatureVerifier.isActive()) {
            // 验签不可用 = 明文信任。默认 fail-open 兼容开发构建；
            // require-native-verify=true 时改 fail-closed，防验签失效被静默降级
            if (requireNativeVerify) {
                plugin.consoleWarn(label + " Native 验签不可用（cloud.require-native-verify=true），按暂时故障拒绝响应。");
                return transientSignedResponse(resp, "native signature verifier unavailable");
            }
            if (!insecureVerifyWarned) {
                insecureVerifyWarned = true;
                plugin.consoleWarn("⚠ Native 验签不可用，云端响应将不做签名校验（明文信任）。"
                    + "生产环境建议修复 native 安全库或开启 cloud.require-native-verify。");
            }
            return resp;
        }
        if (resp == null) {
            plugin.consoleWarn(label + " 响应为空，按暂时故障处理。");
            return transientSignedResponse(resp, "response signature verification failed");
        }
        if (resp.signatureB64() == null || resp.signatureB64().isBlank() || resp.signatureTs() == null || resp.signatureTs().isBlank()) {
            plugin.consoleWarn(label + " 缺少响应签名头，按暂时故障处理。");
            return transientSignedResponse(resp, "response signature verification failed");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(resp.signatureTs().trim());
        } catch (NumberFormatException e) {
            plugin.consoleWarn(label + " 响应时间戳无效，按暂时故障处理。");
            return transientSignedResponse(resp, "response signature verification failed");
        }

        long now = System.currentTimeMillis();
        if (!ResponseSignatureVerifier.isFresh(now, timestamp)) {
            plugin.consoleWarn(label + " 响应时间戳不在允许窗口内，按暂时故障处理。");
            return transientSignedResponse(resp, "response signature verification failed");
        }
        if (responseSignatureVerifier.isReplay(endpointKey, timestamp)) {
            plugin.consoleWarn(label + " 响应时间戳回退，按暂时故障处理。");
            return transientSignedResponse(resp, "response signature verification failed");
        }

        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(resp.signatureB64().trim());
        } catch (IllegalArgumentException e) {
            plugin.consoleWarn(label + " 响应签名不是有效 Base64，按暂时故障处理。");
            return transientSignedResponse(resp, "response signature verification failed");
        }

        byte[] body = resp.body() == null ? new byte[0] : resp.body();
        if (!responseSignatureVerifier.verify(timestamp, body, signature)) {
            plugin.consoleWarn(label + " 响应签名校验失败，按暂时故障处理。");
            return transientSignedResponse(resp, "response signature verification failed");
        }

        responseSignatureVerifier.recordTimestamp(endpointKey, timestamp);
        return resp;
    }

    private SignedCloudResponse transientSignedResponse(SignedCloudResponse resp, String message) {
        if (resp == null) {
            return new SignedCloudResponse(CloudStatus.TRANSIENT, null, 0, null, message, null, null, null);
        }
        return new SignedCloudResponse(
            CloudStatus.TRANSIENT,
            resp.body(),
            resp.httpStatus(),
            resp.endpoint(),
            message,
            resp.rawBody(),
            resp.signatureB64(),
            resp.signatureTs()
        );
    }

    private boolean hasServerCode() {
        return serverCode != null && !serverCode.isEmpty();
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
        String fingerprint = sha256(generateFingerprint());
        long proofAt = runtimeAuthorization.timestamp();
        String proofId = java.util.UUID.randomUUID().toString();
        String anchor = serverSecretB64();
        var binding = new com.google.gson.JsonObject();
        binding.addProperty("qq", qq);
        binding.addProperty("apiKey", apiKey);
        binding.addProperty("pubkey", serverPublicKeyB64);
        binding.addProperty("keyAnchor", anchor);
        binding.addProperty("fingerprintHash", fingerprint);
        binding.addProperty("name", serverName);
        binding.addProperty("proofAt", proofAt);
        binding.addProperty("proofId", proofId);
        binding.addProperty("proof", sign(RuntimeAuthorization.bindingMessage(qq, serverPublicKeyB64, anchor, fingerprint, serverName, proofAt, proofId)));
        String payload = binding.toString();

        return sendSignedWithFailover(
            "bind",
            endpoint -> HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/v1/servers/bind"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(),
            resp -> validateSignedResponse("bind", "[Cloud] 服务器绑定响应", resp)
        ).thenApply(resp -> {
            if (!resp.isOk()) {
                plugin.consoleWarn((resp.isTransient() ? "[Cloud] 服务器绑定暂时失败，HTTP " : "[Cloud] 服务器绑定失败，HTTP ")
                    + resp.httpStatus());
                return new CloudResponse<>(resp.status(), null, resp.httpStatus(), resp.endpoint(), resp.message(), resp.rawBody());
            }
            rememberSuccessfulEndpoint(resp.endpoint());
            String body = resp.rawBody();
            String code = extractJsonField(body, "serverCode");
            String token = extractJsonField(body, "token");
            String responseApiKey = extractJsonField(body, "apiKey");
            if (code != null && !code.isEmpty()) {
                if (!code.equals(this.serverCode)) runtimeAuthorization.invalidate();
                this.serverCode = code;
                acceptPublicModuleList(body);
                if (token != null) {
                    this.moduleToken = token;
                    this.tokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000;
                }
                tasks.main(() -> {
                    plugin.getConfig().set("cloud.server-code", code);
                    if (responseApiKey != null && !responseApiKey.isEmpty()) {
                        plugin.getConfig().set("cloud.apiKey", responseApiKey);
                    }
                    plugin.saveConfig();
                    return null;
                });

                plugin.consoleInfo("[Cloud] 服务器绑定成功，服务器码: " + code);

                return new CloudResponse<>(CloudStatus.OK, null, resp.httpStatus(), resp.endpoint(), null, body);
            }

            plugin.consoleWarn("[Cloud] 绑定响应缺少 serverCode: " + body);

            return new CloudResponse<>(CloudStatus.REJECTED, null, resp.httpStatus(), resp.endpoint(), "missing serverCode", body);
        });
    }

    public CompletableFuture<Boolean> bindServer() {
        return tasks.request(() -> bindServerRequest());
    }

    private CompletableFuture<Boolean> bindServerRequest() {
        return bindServerGate().thenApply(CloudResponse::isOk);
    }


    // -- 申请/刷新模块令牌 --------------------------------------


    public CompletableFuture<Boolean> refreshToken() {
        return tasks.request(() -> refreshTokenRequest());
    }

    private CompletableFuture<Boolean> refreshTokenRequest() {
        return refreshToken(false);
    }

    /**

     * 强制向云端刷新令牌并同步授权模块列表（跳过本地缓存），用于手动触发同步。

     */
    public CompletableFuture<Boolean> forceSyncModules() {
        return tasks.request(() -> forceSyncModulesRequest());
    }

    private CompletableFuture<Boolean> forceSyncModulesRequest() {
        if (!hasServerCode()) {

            plugin.consoleWarn("[Cloud] 服务器尚未绑定，无法同步。请检查 cloud.qq 和 cloud.apiKey 配置。");

            return CompletableFuture.completedFuture(false);
        }
        return refreshToken(true).thenApply(ok -> {
            if (ok && customerRelease != null) syncModuleChanges();
            return ok;
        });
    }

    private CompletableFuture<Boolean> refreshToken(boolean forceRefresh) {
        return refreshTokenGate(forceRefresh).thenCompose(response -> {
            if (!response.isOk()) return CompletableFuture.completedFuture(false);
            return prepareCustomerModuleAccess(forceRefresh).thenApply(ignored -> true);
        });
    }

    CompletableFuture<Void> prepareCustomerModuleAccess(boolean forceRenewal) {
        if (customerRelease == null) return CompletableFuture.completedFuture(null);
        // A newly bound customer server may have a cached bearer token but no public equipment or lease yet.
        startHeartbeat();
        return ensureRuntimeLease(forceRenewal);
    }

    private CompletableFuture<CloudResponse<Void>> refreshTokenGate(boolean forceRefresh) {
        return refreshTokenGate(forceRefresh, false);
    }

    private CompletableFuture<CloudResponse<Void>> refreshTokenGate(boolean forceRefresh, boolean recoveryAttempted) {

        // 自动修复重新绑定后必须强制刷新，以获取正确的 allowedModules

        if (!forceRefresh && moduleToken != null && System.currentTimeMillis() < tokenExpiry - 300_000) {
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.OK, null, 200, apiBaseUrl, null, null));
        }
        if (!hasServerCode()) {
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.REJECTED, null, 0, null, "missing server code", null));
        }

        String requestServerCode = serverCode;
        long timestamp = runtimeAuthorization.timestamp();
        String signMessage = "POST\n/v1/servers/refresh\n" + timestamp + "\n" + requestServerCode;

        String signature = sign(signMessage);

        String refreshPayload = "{"
            + "\"apiKey\":\"" + escapeJson(apiKey) + "\""
            + ",\"keyAnchor\":\"" + escapeJson(serverSecretB64()) + "\""
            + "}";
        return sendSignedWithFailover(
            "refresh",
            endpoint -> HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/v1/servers/refresh"))
                .header("Content-Type", "application/json")
                .header("X-Server-Code", requestServerCode)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(refreshPayload))
                .build(),
            resp -> validateSignedResponse("refresh", "[Cloud] 刷新令牌响应", resp)
        ).thenCompose(resp -> {
            if (!resp.isOk()) {
                String body = resp.rawBody();
                boolean missingServer = CloudRefreshPolicy.isMissingServer(resp.httpStatus(), body);
                if (resp.isRejected() && !recoveryAttempted
                    && (missingServer || CloudRefreshPolicy.isSignatureMismatch(resp.httpStatus(), body))) {

                    plugin.consoleWarn(missingServer
                        ? "[Cloud] 云端服务器记录不存在且备用端无可用记录，使用现有密钥尝试重新绑定一次..."
                        : "[Cloud] 签名验证失败，使用现有密钥尝试重新绑定一次...");

                    return bindServerGate().thenCompose(bound -> {
                        if (bound.isOk()) {

                            plugin.consoleInfo("[Cloud] 重新绑定成功，再次刷新令牌...");

                            return refreshTokenGate(true, true);
                        }

                        plugin.consoleWarn("[Cloud] 自动重新绑定失败，跳过云端模块同步。");

                        return CompletableFuture.completedFuture(bound);
                    });
                }
                plugin.consoleWarn((resp.isTransient() ? "[Cloud] 刷新令牌暂时失败，HTTP " : "[Cloud] 刷新令牌失败，HTTP ")
                    + resp.httpStatus());
                return CompletableFuture.completedFuture(new CloudResponse<>(resp.status(), null, resp.httpStatus(), resp.endpoint(), resp.message(), body));
            }
            rememberSuccessfulEndpoint(resp.endpoint());
            String body = resp.rawBody();

            String token = extractJsonField(body, "token");
            if (token != null) {
                this.moduleToken = token;
                this.tokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000;
                acceptPublicModuleList(body);
                List<String> authorized = authorizedModuleIds();
                plugin.consoleInfo("[Cloud] 模块令牌已刷新，授权模块: " + authorized.size() + " 列表: " + authorized);
                if (customerRelease == null) syncModuleChanges();
                startHeartbeat();
                return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.OK, null, resp.httpStatus(), resp.endpoint(), null, body));
            }
            return CompletableFuture.completedFuture(new CloudResponse<>(CloudStatus.REJECTED, null, resp.httpStatus(), resp.endpoint(), "missing token", body));
        });
    }


    // -- 同步并加载云端模块 --------------------------------------


    public CompletableFuture<Void> syncModules() {
        return tasks.request(() -> syncModulesRequest());
    }

    private CompletableFuture<Void> syncModulesRequest() {
        return syncModules(null);
    }

    public CompletableFuture<Void> syncConfiguredModules(java.util.Set<String> moduleIds) {
        return tasks.request(() -> syncConfiguredModulesRequest(moduleIds));
    }

    private CompletableFuture<Void> syncConfiguredModulesRequest(java.util.Set<String> moduleIds) {
        if (!hasServerCode() || moduleIds.isEmpty()) return CompletableFuture.completedFuture(null);
        return syncModules(java.util.Set.copyOf(moduleIds));
    }

    private CompletableFuture<Void> syncModules(java.util.Set<String> requested) {

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
                return syncAuthorizedModules(requested);
            });
        });
    }

    CompletableFuture<Void> syncAuthorizedModules(java.util.Set<String> requested) {
        return prepareCustomerModuleAccess(false).thenCompose(ignored -> {
            List<String> mods = authorizedModuleIds();
            if (mods.isEmpty()) {
                plugin.consoleInfo("[Cloud] 该服务器未装备任何适用于当前发行批次的模块。");
                return CompletableFuture.completedFuture(null);
            }
            plugin.consoleInfo("[Cloud] 本次需同步 " + mods.size() + " 个授权模块: " + mods);
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (String id : mods) {
                if (id == null || id.isEmpty() || (requested != null && !requested.contains(id))) continue;
                chain = chain.thenCompose(v -> syncModuleFile(id));
            }
            return chain;
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
        return tasks.request(() -> updateModuleRequest(moduleId));
    }

    private CompletableFuture<Boolean> updateModuleRequest(String moduleId) {
        if (!isValidModuleId(moduleId)) return updateModuleBody(moduleId, null);
        var reserved = new CompletableFuture<Void>();
        if (downloading.putIfAbsent(moduleId, reserved) != null) {
            plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 已有下载或更新任务，请等待完成。");
            return CompletableFuture.completedFuture(false);
        }
        var payload = new CloudPayload();
        var actual = tasks.withCleanup(() -> updateModuleBody(moduleId, payload), payload::close);
        actual.whenComplete((value, failure) -> {
            downloading.remove(moduleId, reserved);
            if (failure == null) reserved.complete(null); else reserved.completeExceptionally(failure);
        });
        return actual;
    }

    private CompletableFuture<Boolean> installUpdate(String moduleId, CloudPayload payload) {
        var snapshot = new java.util.concurrent.atomic.AtomicReference<CloudModuleSnapshot>();
        return tasks.withCleanup(() -> tasks.main(() -> {
            if (!registry.validateCloudModule(moduleId, payload.jar, payload.seed)) return false;
            snapshot.set(registry.createSnapshot(moduleId));
            return !registry.isModuleLoaded(moduleId) || registry.unloadModule(moduleId);
        }).thenCompose(unloaded -> {
            if (!unloaded) return CompletableFuture.completedFuture(false);
            return awaitRetirement(moduleId).thenCompose(ignored -> tasks.main(() -> {
                if (registry.isModuleLoaded(moduleId)) return CompletableFuture.completedFuture(false);
                return registry.loadCloudModule(moduleId, payload.jar, payload.seed);
            }).thenCompose(stage -> stage)).thenCompose(loaded -> {
                if (loaded) {
                    plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 更新成功并已加载");
                    return CompletableFuture.completedFuture(true);
                }
                var rollback = snapshot.get();
                if (rollback == null) return CompletableFuture.completedFuture(false);
                return awaitRetirement(moduleId).thenCompose(ignored -> tasks.main(() -> {
                    if (registry.isModuleLoaded(moduleId)) return CompletableFuture.completedFuture(false);
                    return registry.loadCloudModule(moduleId, rollback.jarBytes(), rollback.moduleSeed());
                }).thenCompose(stage -> stage)).thenApply(restored -> {
                    if (restored) plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败，已回滚旧版本。");
                    else plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败，旧版本未能恢复，请核对状态。");
                    return false;
                });
            });
        }), () -> {
            var retained = snapshot.getAndSet(null);
            if (retained != null) retained.close();
        });
    }

    private CompletableFuture<Void> awaitRetirement(String moduleId) {
        return registry.retirement(moduleId).toCompletableFuture().copy()
            .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    private CompletableFuture<Boolean> updateModuleBody(String moduleId, CloudPayload payload) {
        if (!isValidModuleId(moduleId)) {

            plugin.consoleWarn("[Cloud] 非法 moduleId 格式，跳过: " + moduleId);

            return CompletableFuture.completedFuture(false);
        }


        // 若本地缓存不包含该模块，先刷新令牌获取最新授权列表（应对启动后云端新装备模块的场景）

        CompletableFuture<Boolean> ready;
        if (!authorizedModuleIds().contains(moduleId)) {

            plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 不在本地缓存授权列表中，尝试刷新令牌...");

            ready = customerRelease == null ? refreshToken() : refreshToken(true);
        } else {
            ready = CompletableFuture.completedFuture(true);
        }

        return ready.thenCompose(ok -> {
            if (!ok) {

                plugin.consoleWarn("[Cloud] 刷新令牌失败，无法确认模块 " + moduleId + " 的授权状态。");

                return CompletableFuture.completedFuture(false);
            }
            if (!authorizedModuleIds().contains(moduleId)) {

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
                return requestModuleKey(moduleId, axb).thenCompose(keyResp -> {
                    if (tasks.isClosed()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("云端模块服务正在停止"));
                    if (keyResp == null) {

                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：无法获取解密密钥");

                        return CompletableFuture.completedFuture(false);
                    }
                    String keyB64 = extractJsonField(keyResp, "key");
                    if (keyB64 == null || keyB64.isEmpty()) {

                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：云端密钥响应缺少 key 字段");

                        return CompletableFuture.completedFuture(false);
                    }

                    byte[] key = null;
                    byte[] sigBytes = null;

                    try {
                        key = payload.key = Base64.getDecoder().decode(keyB64);
                    } catch (IllegalArgumentException e) {

                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：密钥不是有效的 Base64 编码");

                        return CompletableFuture.completedFuture(false);
                    }
                    if (key.length != 32) {

                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：密钥长度错误 (" + key.length + " 字节)");

                        return CompletableFuture.completedFuture(false);
                    }
                    if (!unwrapBoundKey(key, keyResp, moduleId)) {

                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：模块密钥绑定解包失败");

                        return CompletableFuture.completedFuture(false);
                    }


                    try {
                        String sigB64 = extractJsonField(keyResp, "signature");
                        sigBytes = payload.signature = (sigB64 == null || sigB64.isEmpty())
                            ? new byte[0]
                            : Base64.getDecoder().decode(sigB64);
                    } catch (IllegalArgumentException e) {
                        sigBytes = payload.signature = new byte[0];
                    }
                    byte[] jarBytes;
                    try {

                        if (!ProtectionEnvironment.ensureCleanEnvironment()) {
                    throw new IllegalStateException("Protection environment compromised");
                }
                jarBytes = payload.jar = NativeBridge.n10(axb, key, sigBytes);

                    } catch (Exception e) {

                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " native 解密抛异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());

                        jarBytes = null;
                    }
                    if (jarBytes == null || jarBytes.length == 0) {

                        plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 更新失败：native 解密失败");

                        return CompletableFuture.completedFuture(false);
                    }
                    payload.seed = key.clone();
                    return installUpdate(moduleId, payload);
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
        return tasks.request(() -> updateAllModulesRequest());
    }

    private CompletableFuture<Boolean> updateAllModulesRequest() {
        return tasks.main(registry::getLoadedCloudModuleIds).thenCompose(loadedCloud -> {
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
        });
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


        return tasks.delay(delay / 50L).thenCompose(ignored -> supplier.get()).thenCompose(result -> {
            if (result != null || attempt >= maxRetries) return CompletableFuture.completedFuture(result);
            return retryWithDelay(moduleId, operation, attempt + 1, maxRetries, baseDelayMs, supplier);
        });
    }

    private CompletableFuture<Void> downloadAndLoad(String moduleId) {
        return tasks.request(() -> downloadAndLoadRequest(moduleId));
    }

    CompletableFuture<Void> syncModuleFile(String moduleId) {
        CompletableFuture<Void> work = downloadAndLoad(moduleId);
        if (!localModuleSource) return work;
        return work.exceptionally(failure -> {
            Throwable cause = unwrap(failure);
            if (cause instanceof java.util.concurrent.CancellationException) throw (java.util.concurrent.CancellationException) cause;
            plugin.consoleWarn("[Cloud] 本地模块 " + moduleId + " 加载失败: " + failure.getMessage());
            return null;
        });
    }

    private CompletableFuture<Void> downloadAndLoadRequest(String moduleId) {
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
        var competing = downloading.putIfAbsent(moduleId, placeholder);
        if (competing != null) return competing;


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
        if (tasks.isClosed()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("云端模块服务正在停止"));
        var payload = new CloudPayload();
        return tasks.withCleanup(() -> decryptAndLoadBody(moduleId, axb, isRetry, payload), payload::close);
    }

    private CompletableFuture<Void> decryptAndLoadBody(String moduleId, byte[] axb, boolean isRetry, CloudPayload payload) {

        return retryAsync(moduleId, "密钥获取", 3, 2000L, () -> requestModuleKey(moduleId, axb)).thenCompose(keyResp -> {
            if (tasks.isClosed()) return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException("云端模块服务正在停止"));

            if (keyResp == null) {

                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：无法获取解密密钥");

                return CompletableFuture.<Void>completedFuture(null);
            }
            String keyB64 = extractJsonField(keyResp, "key");
            if (keyB64 == null || keyB64.isEmpty()) {

                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：云端密钥响应缺少 key 字段");

                return CompletableFuture.<Void>completedFuture(null);
            }

            byte[] key = null;
            byte[] sigBytes = null;

            try {
                key = payload.key = Base64.getDecoder().decode(keyB64);
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
            if (!unwrapBoundKey(key, keyResp, moduleId)) {

                plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 同步中止：模块密钥绑定解包失败");

                return CompletableFuture.<Void>completedFuture(null);
            }
            if (debugLogging) {
                String keyHash = sha256Hex(key);
                String ivHex = bytesToHex(java.util.Arrays.copyOfRange(axb, 4, 16));

                debugLog("[Cloud] 模块 " + moduleId + " 诊断: keyHash=" + keyHash + ", ivHex=" + ivHex + ", axbLen=" + axb.length);

            }


            // V6：.axb Ed25519 签名（服务端用平台私钥对完整 axb 签名）。

            // native 在 AES-GCM 解密前先验签；签名为空（后端未启用签名）时 native 跳过验签。

            String sigB64 = extractJsonField(keyResp, "signature");


            try {
                sigBytes = payload.signature = (sigB64 == null || sigB64.isEmpty())
                    ? new byte[0]
                    : Base64.getDecoder().decode(sigB64);
            } catch (IllegalArgumentException e) {
                sigBytes = payload.signature = new byte[0];
            }
            byte[] jarBytes;
            try {

                // native 自己处理 magic(4)，传完整 axb；先验签再解密

                if (!ProtectionEnvironment.ensureCleanEnvironment()) {
                    throw new IllegalStateException("Protection environment compromised");
                }
                jarBytes = payload.jar = NativeBridge.n10(axb, key, sigBytes);
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

            payload.seed = key.clone();
            return tasks.main(() -> {
                if (registry.isModuleLoaded(moduleId)) {
                    plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 已加载，跳过重复加载");
                    return CompletableFuture.<Void>completedFuture(null);
                }
                return registry.loadCloudModule(moduleId, payload.jar, payload.seed).thenAccept(loaded -> {
                    if (loaded) plugin.consoleInfo("[Cloud] 模块 " + moduleId + " 已加载");
                    else plugin.consoleWarn("[Cloud] 模块 " + moduleId + " 加载失败，请核对依赖与模块开关。");
                });
            }).thenCompose(stage -> stage);
        });
    }

    private CompletableFuture<byte[]> downloadAxb(String moduleId) {
        if (localModuleSource) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return new LocalModuleFiles(plugin.getDataFolder().toPath().resolve("modules")).read(moduleId);
                } catch (java.io.IOException failure) {
                    throw new java.util.concurrent.CompletionException("本地受保护模块不可用，请安装同批次的 " + moduleId + ".axb；不会回退下载", failure);
                }
            });
        }
        if (suiteRelease.isEmpty()) {
            plugin.consoleWarn("[Cloud] 当前宿主缺少发行批次，无法获取云端模块。");
            return CompletableFuture.completedFuture(null);
        }
        CachedAxb cached = cachedAxb.get(moduleId);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {

            plugin.consoleInfo("[Cloud] 使用缓存 axb: " + moduleId + " (" + cached.data.length + " 字节)");

            return CompletableFuture.completedFuture(cached.data);
        }
        return sendWithFailover(
            "download",
            endpoint -> HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/v1/modules/" + moduleId + "/download"))
                .header("X-Suite-Release", suiteRelease)
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

    private synchronized CompletableFuture<Void> ensureRuntimeLease(boolean forceRenewal) {
        if (!forceRenewal && !runtimeAuthorization.needsRenewal()) return CompletableFuture.completedFuture(null);
        if (pendingRuntimeLease != null) return pendingRuntimeLease.copy();
        CompletableFuture<Void> work = requestRuntimeLease();
        pendingRuntimeLease = work;
        work.whenComplete((ignored, failure) -> {
            synchronized (this) { if (pendingRuntimeLease == work) pendingRuntimeLease = null; }
        });
        return work.copy();
    }

    private HttpRequest authenticatedRuntimeRequest(String endpoint, String path, String method, String body) {
        long at = runtimeAuthorization.timestamp();
        String proof = sign(method + "\n" + path + "\n" + at + "\n" + serverCode);
        return HttpRequest.newBuilder().uri(URI.create(endpoint + path))
            .header("Content-Type", "application/json").header("X-Server-Code", serverCode)
            .header("X-Timestamp", Long.toString(at)).header("X-Signature", proof)
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .build();
    }

    private CompletableFuture<Void> requestRuntimeLease() {
        try {
            if (!NativeBridge.isAvailable() || !NativeBridge.responseVerifyActive())
                return CompletableFuture.failedFuture(new IllegalStateException("受保护模块需要可用的 Native 发行公钥验签"));
        } catch (LinkageError unavailable) {
            return CompletableFuture.failedFuture(unavailable);
        }
        return sendSignedWithFailover("runtime-challenge",
            endpoint -> authenticatedRuntimeRequest(endpoint, "/v1/servers/runtime-challenge", "GET", null),
            response -> validateSignedResponse("runtime-challenge:" + response.endpoint(), "[Cloud] 运行实例挑战", response))
            .thenCompose(challenge -> {
                if (!challenge.isOk()) {
                    if (challenge.isRejected()) runtimeAuthorization.invalidate();
                    return CompletableFuture.failedFuture(new IllegalStateException("无法取得运行授权挑战"));
                }
                String nonce = extractJsonField(challenge.rawBody(), "nonce");
                if (nonce == null || nonce.length() > 256)
                    return CompletableFuture.failedFuture(new IllegalStateException("运行挑战格式无效"));
                rememberSuccessfulEndpoint(challenge.endpoint());
                long at = runtimeAuthorization.timestamp();
                String id = java.util.UUID.randomUUID().toString();
                String message = runtimeAuthorization.leaseRequestMessage(serverCode, nonce, at, id, customerRelease);
                var body = new com.google.gson.JsonObject();
                body.addProperty("instancePublicKey", runtimeAuthorization.instancePublicKey());
                if (customerRelease != null) body.addProperty("customerRelease", customerRelease);
                body.addProperty("apiKey", apiKey);
                body.addProperty("nonce", nonce);
                body.addProperty("proofAt", at);
                body.addProperty("proofId", id);
                body.addProperty("proof", sign(message));
                body.addProperty("instanceProof", runtimeAuthorization.signInstance(message));
                String previousLease = runtimeAuthorization.currentEnvelope();
                if (previousLease != null) body.addProperty("previousLease", previousLease);
                long requestStarted = System.nanoTime();
                return sendSignedWithFailover("runtime-lease",
                    endpoint -> authenticatedRuntimeRequest(endpoint, "/v1/servers/runtime-lease", "POST", body.toString()),
                    response -> validateSignedResponse("runtime-lease:" + response.endpoint(), "[Cloud] 运行实例授权", response))
                    .thenApply(response -> {
                        if (!response.isOk()) {
                            if (response.isRejected()) runtimeAuthorization.invalidate();
                            String denialCode = extractJsonField(response.rawBody(), "error");
                            if (denialCode == null || !denialCode.matches("[A-Z_]{1,64}")) denialCode = "UNKNOWN_AUTH_ERROR";
                            throw new IllegalStateException("运行授权未确认: " + denialCode + "，HTTP " + response.httpStatus());
                        }
                        String envelope = extractJsonField(response.rawBody(), "runtimeLease");
                        List<String> previous = authorizedModuleIds();
                        runtimeAuthorization.accept(envelope, serverCode, serverPublicKeyB64, NativeBridge::verifyResponseSig,
                            System.currentTimeMillis(), System.nanoTime(), requestStarted);
                        rememberSuccessfulEndpoint(response.endpoint());
                        java.util.Set<String> permitted = runtimeModuleIds();
                        if (customerRelease == null) {
                            var ordered = new java.util.LinkedHashSet<String>();
                            previous.stream().filter(permitted::contains).forEach(ordered::add);
                            permitted.stream().sorted().forEach(ordered::add);
                            allowedModules = List.copyOf(ordered);
                        }
                        boolean resume = protectedModulesSuspended;
                        protectedModulesSuspended = false;
                        if (resume || !java.util.Set.copyOf(previous).equals(permitted)) syncModuleChanges();
                        startRuntimeWatchdog();
                        return (Void) null;
                    });
            });
    }

    private void startRuntimeWatchdog() {
        tasks.main(() -> {
            if (runtimeLeaseWatchdog == null && !tasks.isClosed() && plugin.host().isEnabled()) {
                runtimeLeaseWatchdog = plugin.getServer().getScheduler().runTaskTimer(plugin.host(), () -> {
                    List<String> stopped = registry.revokeProtectedModulesNotIn(runtimeModuleIds());
                    if (!stopped.isEmpty()) {
                        protectedModulesSuspended = true;
                        stopped.forEach(cachedAxb::remove);
                        plugin.consoleWarn("[Cloud] 运行授权已失效或撤销，已停止相关模块及其依赖: " + stopped);
                    }
                }, 20L, 20L);
            }
            return null;
        });
    }

    private CompletableFuture<String> requestModuleKey(String moduleId, byte[] axb) {
        return requestModuleKey(moduleId, axb, true);
    }

    private CompletableFuture<String> requestModuleKey(String moduleId, byte[] axb, boolean recoverToken) {
        return ensureRuntimeLease(false).thenCompose(ignored -> {
            if (customerRelease != null && !moduleRuntimeAuthorized(moduleId)) {
                return CompletableFuture.failedFuture(new IllegalStateException("客户模块发行批次或席位未授权"));
            }
            // Retries get distinct proofs. A consumed request must never be replayed to a cloud.
            Map<String, RuntimeAuthorization.KeyProof> attempts = new ConcurrentHashMap<>();
            return sendSignedWithFailover("key",
                endpoint -> {
                    RuntimeAuthorization.KeyProof proof = runtimeAuthorization.keyProof(serverCode, moduleId, suiteRelease, axb);
                    attempts.put(proof.requestId(), proof);
                    var body = new com.google.gson.JsonObject();
                    body.addProperty("version", suiteRelease);
                    body.addProperty("checksum", proof.checksum());
                    return HttpRequest.newBuilder()
                        .uri(URI.create(endpoint + "/v1/modules/" + moduleId + "/key"))
                        .header("Content-Type", "application/json")
                        .header("X-Module-Token", moduleToken).header("X-Server-Code", serverCode)
                        .header("X-Fingerprint", sha256(generateFingerprint()))
                        .header("X-AXS-Runtime-Lease", proof.envelope())
                        .header("X-AXS-Runtime-Proof", proof.signature())
                        .header("X-AXS-Request-Time", Long.toString(proof.timestamp()))
                        .header("X-AXS-Request-Id", proof.requestId())
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                },
                response -> validateSignedResponse("key:" + moduleId + ":" + response.endpoint(), "[Cloud] 模块密钥授权响应", response))
                .thenCompose(response -> {
                    if (!response.isOk()) {
                        // A standby-issued opaque token may not yet exist in the recovered primary database.
                        if (recoverToken && response.httpStatus() == 401
                            && "INVALID_TOKEN".equals(extractJsonField(response.rawBody(), "error"))) {
                            return refreshTokenGate(true).thenCompose(refreshed -> {
                                if (refreshed.isOk()) return requestModuleKey(moduleId, axb, false);
                                if (refreshed.isRejected()) runtimeAuthorization.invalidate();
                                if (refreshed.isTransient()) return CompletableFuture.<String>failedFuture(
                                    new java.io.IOException("跨云模块令牌刷新暂时失败"));
                                return CompletableFuture.<String>completedFuture(null);
                            });
                        }
                        if (response.isTransient()) return CompletableFuture.<String>failedFuture(
                            new java.io.IOException("模块取钥暂时失败，HTTP " + response.httpStatus()));
                        return CompletableFuture.<String>completedFuture(null);
                    }
                    String raw = response.rawBody();
                    String requestId = extractJsonField(raw, "runtimeRequestId");
                    RuntimeAuthorization.KeyProof proof = requestId == null ? null : attempts.get(requestId);
                    if (proof == null || !suiteRelease.equals(extractJsonField(raw, "version"))
                        || !proof.checksum().equals(RuntimeAuthorization.normalizeChecksum(extractJsonField(raw, "checksum"))))
                        throw new IllegalArgumentException("本地模块发行批次、摘要或取钥请求不匹配");
                    String key = extractJsonField(raw, "key");
                    if (key == null || key.isEmpty()) throw new IllegalArgumentException("授权响应缺少密钥");
                    rememberSuccessfulEndpoint(response.endpoint());
                    return CompletableFuture.completedFuture(raw);
                });
        });
    }

    private synchronized void startHeartbeat() {
        if (tasks.isClosed() || !plugin.host().isEnabled() || heartbeatTask != null) return;
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin.host(), () -> tasks.request(this::sendHeartbeat), 30L * 20L, 30L * 20L);

        plugin.consoleInfo("[Cloud] 心跳任务已启动（30秒/次）");

    }

    public java.util.concurrent.CompletionStage<Void> shutdown() {
        Throwable heartbeatFailure = null;
        try { stopHeartbeat(); }
        catch (Throwable failure) { heartbeatFailure = failure; }
        var exited = tasks.shutdown();
        if (heartbeatFailure != null) {
            Throwable original = heartbeatFailure;
            exited = exited.handle((ignored, failure) -> {
                if (failure != null && failure != original) original.addSuppressed(failure);
                throw new java.util.concurrent.CompletionException(original);
            });
        }
        return exited.thenCompose(ignored -> releaseRuntimeLease()).whenComplete((ignored, failure) -> {
            cachedAxb.clear();
            downloading.clear();
            byte[] secret = serverSecret;
            serverSecret = null;
            if (secret != null) java.util.Arrays.fill(secret, (byte) 0);
            moduleToken = null;
            if (runtimeAuthorization != null) runtimeAuthorization.invalidate();
        });
    }

    public void stopHeartbeat() {
        if (runtimeLeaseWatchdog != null) {
            runtimeLeaseWatchdog.cancel();
            runtimeLeaseWatchdog = null;
        }
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;

            plugin.consoleInfo("[Cloud] 心跳任务已停止");

        }
    }
    private CompletableFuture<Void> releaseRuntimeLease() {
        if (runtimeAuthorization == null || keyPair == null || serverCode == null || serverCode.isEmpty())
            return CompletableFuture.completedFuture(null);
        RuntimeAuthorization.ReleaseProof proof = runtimeAuthorization.releaseProof(serverCode);
        if (proof == null) return CompletableFuture.completedFuture(null);
        var body = new com.google.gson.JsonObject();
        body.addProperty("runtimeLease", proof.envelope());
        body.addProperty("proofAt", proof.timestamp());
        body.addProperty("proofId", proof.requestId());
        body.addProperty("proof", proof.signature());
        String endpoint = API_BASE_URLS.get("axs-standby".equals(proof.issuer()) ? 1 : 0);
        var request = authenticatedRuntimeRequest(endpoint, "/v1/servers/runtime-release", "POST", body.toString());
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .handle((response, failure) -> {
                if (failure != null || response.statusCode() != 200)
                    plugin.consoleWarn("[Cloud] 运行租约未能主动释放，将按原期限自动过期");
                return (Void) null;
            });
    }

    private CompletableFuture<Void> sendHeartbeat() {
        CompletableFuture<Void> renewal = customerRelease != null || runtimeAuthorization.hasLease() || protectedModulesSuspended
            ? ensureRuntimeLease(false) : CompletableFuture.completedFuture(null);
        return renewal.handle((ignored, failure) -> {
            if (failure != null) plugin.consoleWarn("[Cloud] 运行授权续租暂未完成，期限由本地监控独立执行");
            return (Void) null;
        }).thenCompose(ignored -> sendHeartbeatReport());
    }

    private CompletableFuture<Void> sendHeartbeatReport() {
        if (moduleToken == null || moduleToken.isEmpty()) return CompletableFuture.completedFuture(null);

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

        return sendWithFailover(
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

                debugLog("[Cloud] 心跳上报成功，HTTP " + resp.httpStatus());

            } else if (resp.isTransient()) {

                plugin.consoleWarn("[Cloud] 心跳暂时失败，HTTP " + resp.httpStatus());

            } else {

                plugin.consoleWarn("[Cloud] 心跳上报失败，HTTP " + resp.httpStatus());

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
    private void syncModuleChanges() {
        tasks.main(() -> { syncModuleChangesMain(); return null; });
    }

    private void syncModuleChangesMain() {
        List<String> currentAllowed = authorizedModuleIds();
        List<String> loadedCloud = registry.getLoadedCloudModuleIds();


        for (String id : registry.revokeProtectedModulesNotIn(java.util.Set.copyOf(currentAllowed))) {
            cachedAxb.remove(id);
            plugin.consoleInfo("[Cloud] 模块 " + id + " 授权或依赖已撤销，已停止");
        }

        // 需要加载的：新出现在授权列表中且尚未加载

        List<String> toLoad = currentAllowed.stream()
            .filter(id -> !loadedCloud.contains(id))
            .toList();

        if (!toLoad.isEmpty()) {

            plugin.consoleInfo("[Cloud] 检测到 " + toLoad.size() + " 个新授权模块，开始加载: " + toLoad);

            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (String id : toLoad) {
                chain = chain.thenCompose(v -> syncModuleFile(id));
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

    private byte[] newServerSecret() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.serverSecret = secret;
        return secret;
    }

    private String serverSecretB64() {
        byte[] secret = this.serverSecret;
        return secret == null ? "" : Base64.getEncoder().encodeToString(secret);
    }

    /**
     * 还原云端绑定的模块密钥：deliveredKey = moduleKey XOR HKDF(锚)。
     * anchor-hkdf-v1 锚 = 本机 serverSecret（.cloud-keypair 自包含，换 IP/换机不影响）；
     * fp-hkdf-v1 锚 = 机器指纹（旧云端兼容路径）；未知/未绑定类型原样放行。
     */
    private boolean unwrapBoundKey(byte[] key, String keyResp, String moduleId) {
        String keyBind = extractJsonField(keyResp, "keyBind");
        if (keyBind == null || keyBind.isEmpty()) {
            return true;
        }
        byte[] ikm;
        if ("anchor-hkdf-v1".equals(keyBind)) {
            String secretB64 = serverSecretB64();
            if (secretB64.isEmpty()) {
                return false;
            }
            ikm = secretB64.getBytes(StandardCharsets.UTF_8);
        } else if ("fp-hkdf-v1".equals(keyBind)) {
            ikm = sha256(generateFingerprint()).getBytes(StandardCharsets.UTF_8);
        } else {
            return true;
        }
        String version = extractJsonField(keyResp, "version");
        byte[] pad = hkdfSha256(
            ikm,
            KEY_BIND_SALT.getBytes(StandardCharsets.UTF_8),
            ("axs-module-key|" + moduleId + "|" + (version == null ? "" : version)).getBytes(StandardCharsets.UTF_8),
            32);
        if (pad == null) {
            return false;
        }
        for (int i = 0; i < 32; i++) {
            key[i] ^= pad[i];
        }
        return true;
    }

    /**

     * 从持久化文件加载 Ed25519 密钥对；不存在则生成并保存。

     * 必须持久化：每次生成新密钥对会导致后端 pubkey 与私钥不匹配。

     */
    private KeyPair loadOrGenerateKeyPair() {
        File keyFile = new File(plugin.getDataFolder(), ".cloud-keypair");
        if (keyFile.exists()) {
            KeyPair loaded = tryLoadKeyPairV2(keyFile);
            if (loaded == null) {
                loaded = tryLoadKeyPairV1(keyFile);
            }
            if (loaded != null) {
                if (this.serverSecret == null) {
                    // 旧格式迁移：保留原密钥对（pubkey 不变 → 云端按 pubkey 匹配复用原服务器码），
                    // 文件重写为 AXSKP2 并配发新的 serverSecret。
                    plugin.consoleInfo("[Cloud] 密钥文件迁移为 AXSKP2 格式（身份不再绑定机器指纹）。");
                    saveKeyPair(loaded, newServerSecret(), keyFile);
                }
                return loaded;
            }
        }
        KeyPair kp = generateKeyPair();
        if (kp != null) saveKeyPair(kp, newServerSecret(), keyFile);
        return kp;
    }

    /** V2 格式：AXSKP2 + 公钥 + serverSecret + salt + iv + 私钥密文（私钥由 serverSecret 派生密钥包裹）。 */
    private KeyPair tryLoadKeyPairV2(File keyFile) {
        try {
            String[] parts = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.UTF_8).trim().split("\\R", -1);
            if (parts.length == 0 || !KEYPAIR_MAGIC_V2.equals(parts[0].trim())) {
                return null;
            }
            if (parts.length != 6) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件格式损坏（AXSKP2 字段结构无效），将重新生成并触发自动重新绑定。");
                return null;
            }
            byte[] publicEncoded;
            byte[] secret;
            byte[] salt;
            byte[] iv;
            byte[] ciphertext;
            try {
                publicEncoded = Base64.getDecoder().decode(parts[1].trim());
                secret = Base64.getDecoder().decode(parts[2].trim());
                salt = Base64.getDecoder().decode(parts[3].trim());
                iv = Base64.getDecoder().decode(parts[4].trim());
                ciphertext = Base64.getDecoder().decode(parts[5].trim());
            } catch (IllegalArgumentException exception) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件格式损坏（Base64 字段无效），将重新生成并触发自动重新绑定。");
                return null;
            }
            if (publicEncoded.length == 0 || secret.length != 32 || salt.length != 16 || iv.length != 12 || ciphertext.length == 0) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件格式损坏（字段长度无效），将重新生成并触发自动重新绑定。");
                return null;
            }
            byte[] privateEncoded = decryptPrivateKey(ciphertext, iv, salt, Base64.getEncoder().encodeToString(secret));
            if (privateEncoded == null) {
                plugin.consoleWarn("[Cloud] 密钥文件结构有效但 serverSecret 无法解出私钥，将重新生成并触发自动重新绑定。");
                return null;
            }
            KeyPair kp = rebuildKeyPair(publicEncoded, privateEncoded);
            if (kp == null) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件解密内容无效，将重新生成并触发自动重新绑定。");
                return null;
            }
            this.serverSecret = secret;
            debugLog("[Cloud] 已加载持久化 Ed25519 密钥对（AXSKP2），裸公钥: " + encodeRawEd25519PublicKey(kp));
            return kp;
        } catch (Exception exception) {
            plugin.consoleWarn("[Cloud] 持久化密钥文件读取失败，将重新生成并触发自动重新绑定。: " + exception.getMessage());
            return null;
        }
    }

    /** Try the stable fingerprint first, then migrate from the legacy fingerprint. */
    private KeyPair tryLoadKeyPairV1(File keyFile) {
        try {
            String content = new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.UTF_8).trim();
            String[] parts = content.split("\\R", -1);
            if (parts.length != 5 || !KEYPAIR_MAGIC.equals(parts[0].trim())) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件格式损坏（magic/字段结构无效），将重新生成并触发自动重新绑定。");
                return null;
            }
            byte[] publicEncoded;
            byte[] salt;
            byte[] iv;
            byte[] ciphertext;
            try {
                publicEncoded = Base64.getDecoder().decode(parts[1].trim());
                salt = Base64.getDecoder().decode(parts[2].trim());
                iv = Base64.getDecoder().decode(parts[3].trim());
                ciphertext = Base64.getDecoder().decode(parts[4].trim());
            } catch (IllegalArgumentException exception) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件格式损坏（Base64 字段无效），将重新生成并触发自动重新绑定。");
                return null;
            }
            if (publicEncoded.length == 0 || salt.length != 16 || iv.length != 12 || ciphertext.length == 0) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件格式损坏（字段长度无效），将重新生成并触发自动重新绑定。");
                return null;
            }
            byte[] privateEncoded = decryptPrivateKey(ciphertext, iv, salt, generateFingerprint());
            boolean migrated = false;
            if (privateEncoded == null) {
                privateEncoded = decryptPrivateKey(ciphertext, iv, salt, generateLegacyFingerprint());
                migrated = privateEncoded != null;
            }
            if (privateEncoded == null) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件结构有效，但新旧机器指纹均无法解密；可能硬件指纹已变化，将重新生成并触发自动重新绑定。");
                return null;
            }
            KeyPair kp = rebuildKeyPair(publicEncoded, privateEncoded);
            if (kp == null) {
                plugin.consoleWarn("[Cloud] 持久化密钥文件解密内容无效，将重新生成并触发自动重新绑定。");
                return null;
            }
            if (migrated) {
                plugin.consoleInfo("[Cloud] 已使用旧版机器指纹恢复密钥对，服务器码将保持不变。");
            }
            debugLog("[Cloud] 已加载加密持久化 Ed25519 密钥对，裸公钥: " + encodeRawEd25519PublicKey(kp));
            return kp;
        } catch (Exception exception) {
            plugin.consoleWarn("[Cloud] 持久化密钥文件格式损坏（magic/字段结构无效），将重新生成并触发自动重新绑定。: " + exception.getMessage());
            return null;
        }
    }

    private KeyPair rebuildKeyPair(byte[] publicEncoded, byte[] privateEncoded) {
        try {
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(publicEncoded));
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateEncoded));
            return new KeyPair(publicKey, privateKey);
        } catch (Exception exception) {
            plugin.consoleWarn("[Cloud] 重建密钥对失败: " + exception.getMessage());
            return null;
        }
    }

    private void saveKeyPair(KeyPair keyPair, byte[] secret, File keyFile) {
        java.nio.file.Path temporaryPath = null;
        try {
            if (!keyFile.getParentFile().exists()) keyFile.getParentFile().mkdirs();
            byte[] salt = new byte[16];
            byte[] iv = new byte[12];
            SecureRandom rng = new SecureRandom();
            rng.nextBytes(salt);
            rng.nextBytes(iv);
            String secretB64 = Base64.getEncoder().encodeToString(secret);
            byte[] ciphertext = encryptPrivateKey(keyPair.getPrivate().getEncoded(), iv, salt, secretB64);
            if (ciphertext == null) {
                plugin.consoleWarn("[Cloud] 私钥加密失败，未写入密钥文件。");
                return;
            }
            String out = KEYPAIR_MAGIC_V2 + "\n"
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()) + "\n"
                + secretB64 + "\n"
                + Base64.getEncoder().encodeToString(salt) + "\n"
                + Base64.getEncoder().encodeToString(iv) + "\n"
                + Base64.getEncoder().encodeToString(ciphertext);
            java.nio.file.Path targetPath = keyFile.toPath();
            temporaryPath = Files.createTempFile(targetPath.getParent(), keyFile.getName(), ".tmp");
            Files.write(temporaryPath, out.getBytes(StandardCharsets.UTF_8));
            restrictFilePermissions(temporaryPath.toFile());
            try {
                Files.move(temporaryPath, targetPath,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, targetPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            temporaryPath = null;
            restrictFilePermissions(keyFile);
            plugin.consoleInfo("[Cloud] Ed25519 密钥对已加密持久化到 " + keyFile.getAbsolutePath());
        } catch (Exception exception) {
            plugin.consoleWarn("[Cloud] 重建密钥对失败: " + exception.getMessage());
        } finally {
            if (temporaryPath != null) {
                try { Files.deleteIfExists(temporaryPath); } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }

    /** Derive the AES key from the selected machine fingerprint. */
    private static SecretKeySpec deriveKeyFromFingerprint(byte[] salt) throws Exception {
        return deriveKeyFromFingerprint(salt, generateFingerprint());
    }

    private static SecretKeySpec deriveKeyFromFingerprint(byte[] salt, String fingerprint) throws Exception {
        char[] password = fingerprint.toCharArray();
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password, salt, KDF_ITERATIONS, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] encryptPrivateKey(byte[] plaintext, byte[] iv, byte[] salt, String password) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKeyFromFingerprint(salt, password), new GCMParameterSpec(128, iv));
            return cipher.doFinal(plaintext);
        } catch (Exception exception) {
                plugin.consoleWarn("[Cloud] 私钥加密异常: " + exception.getMessage());
            return null;
        }
    }

    private byte[] decryptPrivateKey(byte[] ciphertext, byte[] iv, byte[] salt, String fingerprint) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKeyFromFingerprint(salt, fingerprint), new GCMParameterSpec(128, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception ignored) {
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

    /** Generate a stable fingerprint from OS identity, architecture, and filtered physical MACs. */
    private static String generateFingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(System.getProperty("os.name", "unknown").getBytes(StandardCharsets.UTF_8));
            md.update(System.getProperty("os.arch", "unknown").getBytes(StandardCharsets.UTF_8));
            for (String mac : collectHardwareAddresses()) {
                md.update(mac.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception exception) {
            return "unknown";
        }
    }

    /** Preserve the old fingerprint algorithm for one-time key migration only. */
    private static String generateLegacyFingerprint() {
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
            for (String mac : collectLegacyHardwareAddresses()) {
                md.update(mac.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception exception) {
            return "unknown";
        }
    }

    /** Collect stable physical MACs without requiring interfaces to be up. */
    private static List<String> collectHardwareAddresses() {
        List<String> macs = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                try {
                    if (nic.isLoopback() || nic.isVirtual() || nic.isPointToPoint()
                        || isExcludedInterface(nic)) continue;
                } catch (Exception ignored) {
                    continue;
                }
                byte[] hw = nic.getHardwareAddress();
                if (hw == null || hw.length == 0 || isAllZeroMac(hw)) continue;
                String mac = bytesToHex(hw);
                if (!macs.contains(mac)) macs.add(mac);
            }
        } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
        }
        java.util.Collections.sort(macs);
        return macs;
    }

    private static boolean isExcludedInterface(NetworkInterface nic) {
        String name = nic.getName();
        String displayName = nic.getDisplayName();
        String value = ((name == null ? "" : name) + " "
            + (displayName == null ? "" : displayName)).toLowerCase(java.util.Locale.ROOT);
        for (String pattern : EXCLUDED_INTERFACE_PATTERNS) {
            if (value.contains(pattern)) return true;
        }
        return false;
    }

    private static boolean isAllZeroMac(byte[] hardwareAddress) {
        for (byte value : hardwareAddress) {
            if (value != 0) return false;
        }
        return true;
    }

    /** Preserve the old isUp-based collection for legacy key migration only. */
    private static List<String> collectLegacyHardwareAddresses() {
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
                if (!macs.contains(mac)) macs.add(mac);
            }
        } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
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
