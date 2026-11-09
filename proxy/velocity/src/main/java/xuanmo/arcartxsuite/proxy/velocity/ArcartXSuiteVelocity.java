package xuanmo.arcartxsuite.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import xuanmo.arcartxsuite.proxy.common.auth.AuthlibInjectorDetector;
import xuanmo.arcartxsuite.proxy.common.auth.AuthlibInjectorManager;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;

@Plugin(
    id = "arcartxsuite-proxy",
    name = "ArcartXSuite Proxy",
    version = "2.0.0",
    description = "ArcartXSuite 代理端伴侣插件：混合登录管理 + 离线拦截 + 后端信息透传",
    authors = {"xuanmo"}
)
public class ArcartXSuiteVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final File dataFolder;

    private ProxyConfig config;
    private VelocityAuthListener authListener;
    private VelocityProofForwarder proofForwarder;

    @Inject
    public ArcartXSuiteVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = dataDirectory.toFile();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║      ArcartXSuite Proxy (Velocity) 加载中...                ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");

        // 加载配置
        config = new ProxyConfig(logger, dataFolder);
        config.load("proxy-config.yml");

        // 注册事件监听器
        authListener = new VelocityAuthListener(this, server, config, logger);
        server.getEventManager().register(this, authListener);
        try { proofForwarder=new VelocityProofForwarder(this,server,dataFolder.toPath(),config.authForwarding(),logger); }
        catch(Exception e) { throw new IllegalStateException("可信代理转发初始化失败，请检查节点密钥与网关配置",e); }

        // 注册命令
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("axsproxy").build(),
            new ProxyCommand(this, config, logger)
        );

        // authlib-injector + 混合代理检测
        detectAuthAndProxy();

        logger.info("ArcartXSuite Proxy (Velocity) 加载完成。");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if(proofForwarder!=null) proofForwarder.close();
        logger.info("ArcartXSuite Proxy (Velocity) 已关闭。");
    }

    public ProxyServer getServer() {
        return server;
    }

    public ProxyConfig getConfig() {
        return config;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    private void detectAuthAndProxy() {
        boolean agentLoaded = AuthlibInjectorDetector.detect();

        if (!config.authEnabled()) {
            if (agentLoaded) {
                logger.info("[Auth] 已检测到 authlib-injector，但 auth.enabled=false。");
                logger.info("[Auth] 若需混合登录，请在 proxy-config.yml 中设置 auth.enabled: true");
            }
            return;
        }

        AuthlibInjectorManager mgr = new AuthlibInjectorManager(
            logger, dataFolder, config.mixedProxyPort(),
            config.mixedProxyBindHost(), config.mixedProxyToken(), config.mixedProxyAllowIps(),
            AuthlibInjectorManager.Platform.VELOCITY);

        if (!agentLoaded) {
            logger.warning("[Auth] 未检测到 authlib-injector！");
            logger.warning("[Auth] 请执行 /axsproxy auth setup 生成启动脚本，");
            logger.warning("[Auth] 然后使用 start-mixed-auth 脚本启动 Velocity。");
            return;
        }

        logger.info("[Auth] 已检测到 authlib-injector。");

        if (mgr.isMixedProxyReachable()) {
            logger.info("[Auth] 本地混合代理已就绪 (端口 " + config.mixedProxyPort() + ")。");
            logger.info("[Auth] auth-forwarding 启用后，向已登记子服传递当前连接的认证证明。");
        } else {
            logger.warning("[Auth] 本地混合代理未启动 (端口 " + config.mixedProxyPort() + ")！");
            logger.warning("[Auth] 请使用 start-mixed-auth 脚本启动 Velocity，");
            logger.warning("[Auth] 脚本会先启动混合代理再启动 Velocity。");
        }

        if (config.authAutoCheckVersion()) {
            mgr.checkVersionAndNotify();
        }
    }
}
