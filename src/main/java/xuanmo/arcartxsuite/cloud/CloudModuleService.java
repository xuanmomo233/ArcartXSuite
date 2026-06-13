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
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import xuanmo.arcartxsuite.security.NativeBridge;

/**
 * 云端模块服务：负责与 AXS Cloud Platform 交互，下载、解密并加载云端模块。
 */
public final class CloudModuleService {

    private static final Logger LOGGER = Logger.getLogger("AXS-Cloud");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    private final JavaPlugin plugin;
    private final ModuleRegistry registry;
    private final String apiBaseUrl;
    private final String installId;
    private final String serverPrivateKeyB64;
    private final String serverPublicKeyB64;

    private volatile String moduleToken;
    private volatile long tokenExpiry;
    private final Map<String, byte[]> cachedAxb = new ConcurrentHashMap<>();

    public CloudModuleService(JavaPlugin plugin, ModuleRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        this.apiBaseUrl = config.getString("cloud.api-url", "https://axs.021209.xyz").replaceAll("/$", "");
        this.installId = config.getString("cloud.install-id", generateInstallId());
        this.serverPrivateKeyB64 = config.getString("cloud.private-key", "");
        this.serverPublicKeyB64 = config.getString("cloud.public-key", "");

        if (!config.contains("cloud.install-id")) {
            config.set("cloud.install-id", installId);
            plugin.saveConfig();
        }
    }

    // -- 服务器注册 ---------------------------------------------

    public CompletableFuture<Boolean> registerServer(String qq) {
        String fingerprint = generateFingerprint();
        String payload = "{"
            + "\"installId\":\"" + installId + "\","
            + "\"pubkey\":\"" + serverPublicKeyB64 + "\","
            + "\"fingerprintHash\":\"" + sha256(fingerprint) + "\","
            + "\"qq\":\"" + qq + "\""
            + "}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/servers/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> {
                if (resp.statusCode() == 200) {
                    LOGGER.info("[Cloud] 服务器注册成功: " + installId);
                    return true;
                }
                LOGGER.warning("[Cloud] 服务器注册失败: " + resp.statusCode() + " " + resp.body());
                return false;
            })
            .exceptionally(ex -> {
                LOGGER.warning("[Cloud] 服务器注册异常: " + ex.getMessage());
                return false;
            });
    }

    // -- 申请/刷新模块令牌 --------------------------------------

    public CompletableFuture<Boolean> refreshToken() {
        if (moduleToken != null && System.currentTimeMillis() < tokenExpiry - 300_000) {
            return CompletableFuture.completedFuture(true);
        }

        String payload = "{\"installId\":\"" + installId + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/modules/token"))
            .header("Content-Type", "application/json")
            .header("X-Install-Id", installId)
            .header("X-Module-Token", moduleToken != null ? moduleToken : "")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> {
                if (resp.statusCode() == 200) {
                    String body = resp.body();
                    String token = extractJsonField(body, "token");
                    if (token != null) {
                        this.moduleToken = token;
                        this.tokenExpiry = System.currentTimeMillis() + 23 * 3600 * 1000;
                        LOGGER.info("[Cloud] 模块令牌已刷新");
                        return true;
                    }
                }
                LOGGER.warning("[Cloud] 刷新令牌失败: " + resp.statusCode());
                return false;
            })
            .exceptionally(ex -> {
                LOGGER.warning("[Cloud] 刷新令牌异常: " + ex.getMessage());
                return false;
            });
    }

    // -- 同步并加载云端模块 --------------------------------------

    public CompletableFuture<Void> syncModules() {
        return refreshToken().thenCompose(ok -> {
            if (!ok) return CompletableFuture.<Void>completedFuture(null);
            return fetchModuleList().thenCompose(list -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> modules = (List<Map<String, Object>>) list;
                if (modules == null || modules.isEmpty()) {
                    return CompletableFuture.<Void>completedFuture(null);
                }
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                for (Map<String, Object> mod : modules) {
                    String id = (String) mod.get("id");
                    if (id == null) continue;
                    chain = chain.thenCompose(v -> downloadAndLoad(id));
                }
                return chain;
            });
        });
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<List<Map<String, Object>>> fetchModuleList() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/modules"))
            .header("X-Module-Token", moduleToken)
            .header("X-Install-Id", installId)
            .GET()
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> {
                if (resp.statusCode() == 200) {
                    String body = resp.body();
                    String modulesJson = extractJsonArray(body, "modules");
                    if (modulesJson != null) {
                        try {
                            org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
                            yaml.loadFromString("tmp:\n" + modulesJson.replaceAll("\\[", "").replaceAll("\\]", ""));
                            return List.<Map<String, Object>>of();
                        } catch (Exception e) {
                            LOGGER.warning("[Cloud] 解析模块列表失败: " + e.getMessage());
                        }
                    }
                }
                return List.<Map<String, Object>>of();
            });
    }

    private CompletableFuture<Void> downloadAndLoad(String moduleId) {
        return downloadAxb(moduleId).thenCompose(axb -> {
            if (axb == null || axb.length == 0) {
                LOGGER.warning("[Cloud] 下载模块 " + moduleId + " 失败");
                return CompletableFuture.<Void>completedFuture(null);
            }
            return requestModuleKey(moduleId).thenAccept(keyResp -> {
                if (keyResp == null) {
                    LOGGER.warning("[Cloud] 获取模块 " + moduleId + " 密钥失败");
                    return;
                }
                String keyB64 = extractJsonField(keyResp, "key");
                String ivB64  = extractJsonField(keyResp, "iv");
                if (keyB64 == null || ivB64 == null) {
                    LOGGER.warning("[Cloud] 模块 " + moduleId + " 密钥数据不完整");
                    return;
                }
                byte[] key = Base64.getDecoder().decode(keyB64);
                byte[] iv  = Base64.getDecoder().decode(ivB64);
                byte[] jarBytes = NativeBridge.decryptModule(axb, key, iv);
                if (jarBytes == null || jarBytes.length == 0) {
                    LOGGER.warning("[Cloud] 解密模块 " + moduleId + " 失败");
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    boolean ok = registry.loadCloudModule(jarBytes);
                    if (ok) {
                        LOGGER.info("[Cloud] 模块 " + moduleId + " 已加载");
                    } else {
                        LOGGER.warning("[Cloud] 模块 " + moduleId + " 加载失败");
                    }
                });
            });
        });
    }

    private CompletableFuture<byte[]> downloadAxb(String moduleId) {
        byte[] cached = cachedAxb.get(moduleId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/modules/" + moduleId + "/download"))
            .header("X-Module-Token", moduleToken)
            .header("X-Install-Id", installId)
            .GET()
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(resp -> {
                if (resp.statusCode() == 200) {
                    byte[] data = resp.body();
                    cachedAxb.put(moduleId, data);
                    return data;
                }
                LOGGER.warning("[Cloud] 下载 " + moduleId + " 失败: " + resp.statusCode());
                return null;
            })
            .exceptionally(ex -> {
                LOGGER.warning("[Cloud] 下载 " + moduleId + " 异常: " + ex.getMessage());
                return null;
            });
    }

    private CompletableFuture<String> requestModuleKey(String moduleId) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiBaseUrl + "/v1/modules/" + moduleId + "/key"))
            .header("Content-Type", "application/json")
            .header("X-Module-Token", moduleToken)
            .header("X-Install-Id", installId)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> resp.statusCode() == 200 ? resp.body() : null)
            .exceptionally(ex -> null);
    }

    // -- 工具方法 ------------------------------------------------

    private static String generateInstallId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
        int firstQuote = json.indexOf('"', colonIdx + 1);
        if (firstQuote < 0) return null;
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return json.substring(firstQuote + 1, secondQuote);
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
}
