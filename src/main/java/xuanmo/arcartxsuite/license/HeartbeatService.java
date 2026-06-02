package xuanmo.arcartxsuite.license;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.module.ModuleRegistry;

/**
 * 定时向授权服务器发送心跳，报告服务器运行状态和模块信息。
 */
public final class HeartbeatService {

    private static final long HEARTBEAT_INTERVAL_MS = 3 * 60 * 1000L;
    private static final long INITIAL_DELAY_MS = 30 * 1000L;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final LicenseService licenseService;
    private final ModuleRegistry moduleRegistry;
    private ScheduledExecutorService scheduler;

    public HeartbeatService(JavaPlugin plugin, LicenseService licenseService, ModuleRegistry moduleRegistry) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.licenseService = licenseService;
        this.moduleRegistry = moduleRegistry;
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "AXS-Heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, INITIAL_DELAY_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.info("心跳服务已启动，间隔 " + (HEARTBEAT_INTERVAL_MS / 1000) + " 秒。");
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            logger.info("心跳服务已停止。");
        }
    }

    private void sendHeartbeat() {
        try {
            LicenseConfig config = licenseService.currentConfig();
            if (config == null || !config.hasLicenseIdentity()) {
                return;
            }
            HostFingerprint.Snapshot fingerprint = licenseService.fingerprint();
            if (fingerprint == null) {
                return;
            }
            FailoverLicenseGateway gateway = licenseService.currentGateway();
            if (gateway == null) {
                return;
            }

            JsonObject request = buildHeartbeatPayload(config, fingerprint);
            JsonObject response = gateway.heartbeat(request);
            if (response != null && response.has("anomalyCount")) {
                int anomalyCount = response.get("anomalyCount").getAsInt();
                if (anomalyCount > 0) {
                    logger.warning("授权服务器检测到 " + anomalyCount + " 个异常，请检查授权状态。");
                }
            }
        } catch (LicenseGateway.LicenseNetworkException exception) {
            logger.fine("心跳发送失败（网络）: " + exception.getMessage());
        } catch (LicenseGateway.LicenseAuthException exception) {
            logger.fine("心跳发送失败（认证）: " + exception.errorCode());
        } catch (Exception exception) {
            logger.log(Level.FINE, "心跳发送异常", exception);
        }
    }

    private JsonObject buildHeartbeatPayload(LicenseConfig config, HostFingerprint.Snapshot fingerprint) {
        JsonObject request = new JsonObject();
        request.addProperty("qq", config.qq());
        request.addProperty("installId", config.installId());
        request.addProperty("fingerprintHash", fingerprint.hash());
        request.addProperty("pluginVersion", plugin.getDescription().getVersion());
        request.addProperty("serverVersion", Bukkit.getVersion());
        request.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        request.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        request.addProperty("uptimeMs", System.currentTimeMillis() - serverStartTime());
        request.addProperty("javaVersion", System.getProperty("java.version", "unknown"));
        request.addProperty("osName", System.getProperty("os.name", "unknown"));

        double[] tps = getRecentTps();
        if (tps != null && tps.length > 0) {
            request.addProperty("tps", Math.round(tps[0] * 10.0) / 10.0);
        }

        // 模块列表
        JsonArray modules = new JsonArray();
        if (moduleRegistry != null) {
            LicenseDecision decision = licenseService.decision();
            Set<String> allowedModules = decision != null ? decision.modules() : Set.of();
            Map<String, Boolean> statusMap = moduleRegistry.moduleStatusMap();
            List<ModuleDescriptor> descriptors = moduleRegistry.listModules();
            for (ModuleDescriptor descriptor : descriptors) {
                JsonObject mod = new JsonObject();
                mod.addProperty("id", descriptor.id());
                mod.addProperty("version", descriptor.version());
                Boolean enabled = statusMap.get(descriptor.name());
                mod.addProperty("status", Boolean.TRUE.equals(enabled) ? "running" : "disabled");
                mod.addProperty("licensed", allowedModules.contains(descriptor.id()) || !LicenseService.PAID_MODULES.contains(descriptor.id()));
                modules.add(mod);
            }
        }
        request.add("modules", modules);

        // 客户端检测到的异常
        JsonArray anomalies = new JsonArray();
        request.add("anomalies", anomalies);

        return request;
    }

    private long serverStartTime() {
        try {
            return ManagementFactory.getRuntimeMXBean().getStartTime();
        } catch (Exception exception) {
            return System.currentTimeMillis();
        }
    }

    private double[] getRecentTps() {
        try {
            Object server = Bukkit.getServer();
            java.lang.reflect.Method method = server.getClass().getMethod("getTPS");
            return (double[]) method.invoke(server);
        } catch (Exception exception) {
            return null;
        }
    }
}
