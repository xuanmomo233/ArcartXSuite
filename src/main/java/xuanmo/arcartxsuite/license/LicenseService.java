package xuanmo.arcartxsuite.license;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.license.LicenseGateway.LicenseAuthException;
import xuanmo.arcartxsuite.license.LicenseGateway.LicenseNetworkException;

public final class LicenseService {

    public static final Set<String> PAID_MODULES = Set.of("warehouse", "map", "mail", "title", "questgps", "conversation", "tab", "entitytracker", "market", "qqbot");

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final LicenseVerifier verifier = new LicenseVerifier();
    private final HostFingerprint hostFingerprint;
    private final SecureClock secureClock;
    private final ResourceKeyManager resourceKeyManager = new ResourceKeyManager();
    private final EncryptedResourceLoader encryptedResourceLoader = new EncryptedResourceLoader(resourceKeyManager);
    private final File cacheFile;

    private LicenseConfig config;
    private FailoverLicenseGateway gateway;
    private HostFingerprint.Snapshot fingerprint;
    private LicenseDecision decision = LicenseDecision.notConfigured();
    private String preflightSummary = "";
    private boolean usingCache;

    public LicenseService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.hostFingerprint = new HostFingerprint(plugin);
        this.secureClock = new SecureClock(plugin);
        File securityDir = new File(plugin.getDataFolder(), "security");
        if (!securityDir.exists()) {
            securityDir.mkdirs();
        }
        this.cacheFile = new File(securityDir, "license.cache");
    }

    public void initialize() {
        reloadConfig();
        loadCacheDecision();
        refresh(false);
    }

    public void reloadConfig() {
        config = LicenseConfig.load(plugin, logger);
        gateway = FailoverLicenseGateway.fromConfig(config);
        fingerprint = hostFingerprint.snapshot();
    }

    public LicenseDecision refresh(boolean forceActivate) {
        return refreshInternal(forceActivate, false);
    }

    public LicenseDecision rebind() {
        return refreshInternal(false, true);
    }

    public JsonObject cloudChallenge() throws LicenseNetworkException, LicenseAuthException {
        if (config == null || gateway == null || fingerprint == null) {
            reloadConfig();
        }
        JsonObject request = new JsonObject();
        request.addProperty("installId", config.installId());
        request.addProperty("fingerprintHash", fingerprint.hash());
        request.addProperty("localSaltHash", fingerprint.localSaltHash());
        return gateway.cloudChallenge(request);
    }

    private LicenseDecision refreshInternal(boolean forceActivate, boolean forceRebind) {
        if (config == null) {
            reloadConfig();
        }
        usingCache = false;
        if (!config.hasLicenseIdentity()) {
            decision = LicenseDecision.notConfigured();
            resourceKeyManager.update(null);
            return decision;
        }
        try {
            runPreflightIfEnabled();
            JsonObject response = forceRebind
                ? gateway.rebind(licenseRequest())
                : (forceActivate || !hasCachedTicket() ? gateway.activate(licenseRequest()) : gateway.verify(verifyRequest()));
            acceptRemoteResponse(response);
            usingCache = false;
        } catch (LicenseAuthException exception) {
            if ("BINDING_NOT_FOUND".equals(exception.errorCode())) {
                try {
                    JsonObject response = gateway.activate(licenseRequest());
                    acceptRemoteResponse(response);
                    usingCache = false;
                    logger.info("ArcartXSuite 授权未找到当前绑定，已自动执行首次激活。");
                    return decision;
                } catch (LicenseAuthException activateAuth) {
                    exception = activateAuth;
                } catch (LicenseNetworkException activateNetwork) {
                    useCacheAfterNetworkError(activateNetwork);
                    logger.warning("ArcartXSuite 授权自动激活网络错误，将尝试使用本地缓存: " + activateNetwork.getMessage());
                    return decision;
                }
            }
            String reason = LicenseMessages.authError(exception.errorCode());
            decision = new LicenseDecision(LicenseDecision.State.AUTH_DENIED, reason, Set.of(), null);
            resourceKeyManager.update(null);
            logger.warning("ArcartXSuite 授权被拒绝: " + reason);
        } catch (LicenseNetworkException exception) {
            useCacheAfterNetworkError(exception);
            logger.warning("ArcartXSuite 授权网络错误，将尝试使用本地缓存: " + exception.getMessage());
        } catch (RuntimeException exception) {
            decision = LicenseDecision.disabled(exception.getMessage());
            resourceKeyManager.update(null);
            logger.warning("ArcartXSuite 授权校验失败: " + exception.getMessage());
        }
        return decision;
    }

    private void useCacheAfterNetworkError(LicenseNetworkException exception) {
        loadCacheDecision();
        if (decision.allowsPaidModules()) {
            usingCache = true;
            logger.info("ArcartXSuite 已使用本地授权缓存: " + decision.state() + " | " + decision.reason());
            return;
        }
        String cacheReason = decision.reason();
        String reason = exception.getMessage();
        if (cacheReason != null && !cacheReason.isBlank()) {
            reason = reason + "；本地缓存不可用: " + cacheReason;
        }
        decision = new LicenseDecision(LicenseDecision.State.NETWORK_ERROR, reason, Set.of(), null);
        resourceKeyManager.update(null);
        usingCache = false;
    }

    public boolean isPaidModule(String moduleId) {
        return PAID_MODULES.contains(normalize(moduleId));
    }

    public boolean isModuleAllowed(String moduleId) {
        String normalized = normalize(moduleId);
        return !isPaidModule(normalized) || decision.allowsModule(normalized);
    }

    public LicenseDecision decision() {
        return decision;
    }

    public LicenseDiagnostics diagnostics() {
        LicenseTicket ticket = decision.ticket();
        return new LicenseDiagnostics(
            decision.state(),
            decision.reason(),
            ticket == null ? "" : ticket.licenseId(),
            ticket == null ? "" : ticket.activationId(),
            ticket == null ? (config == null ? "" : config.qq()) : ticket.ownerQq(),
            ticket == null ? "" : ticket.subjectId(),
            ticket == null ? "" : String.join(",", ticket.modules()),
            ticket == null ? "" : keyResultSummary(ticket),
            ticket == null ? 0L : ticket.expiresAt(),
            ticket == null ? 0L : ticket.refreshAfter(),
            secureClock.rollbackDetected(),
            fingerprint == null ? "" : fingerprint.hash(),
            config == null ? "" : config.endpointSummary(),
            config == null ? "" : config.proxyConfig().diagnosticSummary(),
            gateway == null ? "" : gateway.lastOperation(),
            gateway == null ? "" : gateway.lastSuccessfulEndpoint(),
            gateway == null ? "" : gateway.lastFailureSummary(),
            preflightSummary,
            usingCache
        );
    }

    public HostFingerprint.Snapshot fingerprint() {
        return fingerprint == null ? hostFingerprint.snapshot() : fingerprint;
    }

    public LicenseConfig currentConfig() {
        return config;
    }

    public FailoverLicenseGateway currentGateway() {
        return gateway;
    }

    public EncryptedResourceLoader encryptedResourceLoader() {
        return encryptedResourceLoader;
    }

    public SecureClock secureClock() {
        return secureClock;
    }

    private JsonObject licenseRequest() {
        JsonObject request = commonRequest();
        JsonObject fingerprintJson = fingerprintJson();
        request.add("fingerprint", fingerprintJson);
        request.addProperty("fingerprintHash", fingerprint.hash());
        request.addProperty("localSaltHash", fingerprint.localSaltHash());
        return request;
    }

    private JsonObject verifyRequest() {
        JsonObject request = commonRequest();
        request.addProperty("fingerprintHash", fingerprint.hash());
        request.addProperty("localSaltHash", fingerprint.localSaltHash());
        request.add("timeState", gson.toJsonTree(secureClock.timeState()));
        LicenseTicket ticket = decision.ticket();
        if (ticket != null && !ticket.ticketId().isBlank()) {
            request.addProperty("ticketId", ticket.ticketId());
        }
        return request;
    }

    private JsonObject commonRequest() {
        JsonObject request = new JsonObject();
        request.addProperty("protocol", "AXS-LICENSE-v1.3");
        request.addProperty("qq", config.qq());
        request.addProperty("product", "ArcartXSuite");
        request.addProperty("pluginVersion", plugin.getDescription().getVersion());
        request.addProperty("installId", config.installId());
        JsonArray keys = new JsonArray();
        config.licenseKeys().forEach(keys::add);
        request.add("licenseKeys", keys);
        JsonArray modules = new JsonArray();
        PAID_MODULES.forEach(modules::add);
        request.add("requestedModules", modules);
        return request;
    }

    private void runPreflightIfEnabled() throws LicenseNetworkException, LicenseAuthException {
        if (config == null || !config.preflightTimeCheck()) {
            preflightSummary = "disabled";
            return;
        }
        JsonObject response = gateway.time();
        long serverTime = response.has("serverTime") ? response.get("serverTime").getAsLong() : 0L;
        if (serverTime > 0L) {
            secureClock.trustServerTime(serverTime);
        }
        preflightSummary = "OK via " + gateway.lastSuccessfulEndpoint();
    }

    private JsonObject fingerprintJson() {
        JsonObject root = new JsonObject();
        root.addProperty("hash", fingerprint.hash());
        root.addProperty("localSaltHash", fingerprint.localSaltHash());
        JsonArray components = new JsonArray();
        for (HostFingerprint.Component component : fingerprint.components()) {
            JsonObject item = new JsonObject();
            item.addProperty("name", component.name());
            item.addProperty("hash", component.hash());
            item.addProperty("weight", component.weight());
            components.add(item);
        }
        root.add("components", components);
        return root;
    }

    private void acceptRemoteResponse(JsonObject response) {
        long serverTime = response.has("serverTime") ? response.get("serverTime").getAsLong() : System.currentTimeMillis();
        secureClock.trustServerTime(serverTime);
        String ticket = response.has("ticket") ? response.get("ticket").getAsString() : "";
        decision = verifier.decisionFor(ticket, config.qq(), config.installId(), fingerprint.hash(), secureClock.now());
        resourceKeyManager.update(decision.ticket());
        writeCache(ticket);
    }

    private void loadCacheDecision() {
        String ticket = readCachedTicket();
        if (ticket.isBlank()) {
            decision = config != null && !config.hasLicenseIdentity()
                ? LicenseDecision.notConfigured()
                : LicenseDecision.disabled("无 license.cache");
            resourceKeyManager.update(null);
            return;
        }
        try {
            decision = verifier.decisionForCached(
                ticket,
                config == null ? "" : config.qq(),
                config.installId(),
                fingerprint.hash(),
                fingerprint.localSaltHash(),
                secureClock.now()
            );
            resourceKeyManager.update(decision.ticket());
        } catch (RuntimeException exception) {
            decision = LicenseDecision.disabled("本地缓存无效: " + exception.getMessage());
            resourceKeyManager.update(null);
        }
    }

    private boolean hasCachedTicket() {
        try {
            return !readCachedTicket().isBlank();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String readCachedTicket() {
        try {
            if (!cacheFile.exists()) {
                return "";
            }
            JsonObject cache = gson.fromJson(Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8), JsonObject.class);
            return cache != null && cache.has("ticket") ? cache.get("ticket").getAsString() : "";
        } catch (Exception exception) {
            return "";
        }
    }

    private void writeCache(String ticket) {
        try {
            JsonObject cache = new JsonObject();
            cache.addProperty("protocol", "AXS-LICENSE-CACHE-v1");
            cache.addProperty("ticket", ticket);
            cache.addProperty("updatedAt", System.currentTimeMillis());
            Files.writeString(cacheFile.toPath(), gson.toJson(cache), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            logger.warning("写入 license.cache 失败: " + exception.getMessage());
        }
    }

    private String normalize(String moduleId) {
        return moduleId == null ? "" : moduleId.trim().toLowerCase();
    }

    private String keyResultSummary(LicenseTicket ticket) {
        if (ticket.keyResults().isEmpty()) {
            return "";
        }
        return ticket.keyResults().stream()
            .map(LicenseKeyResult::summary)
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }
}
