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

import xuanmo.arcartxsuite.proxy.common.auth.YggdrasilAuthenticator;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;

@Plugin(
    id = "arcartxsuite-proxy",
    name = "ArcartXSuite Proxy",
    version = "1.1.0",
    description = "ArcartXSuite 代理端伴侣插件：多方认证辅助与后端信息透传",
    authors = {"xuanmo"}
)
public class ArcartXSuiteVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final File dataFolder;

    private ProxyConfig config;
    private YggdrasilAuthenticator authenticator;
    private VelocityAuthListener authListener;

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
        config.loadDefaults();
        config.extractDefaultConfig("proxy-config.yml");

        // 初始化认证器
        authenticator = new YggdrasilAuthenticator(logger, config.debug());

        // 注册事件监听器
        authListener = new VelocityAuthListener(this, server, config, authenticator, logger);
        server.getEventManager().register(this, authListener);

        // 注册命令
        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("axsproxy").build(),
            new ProxyCommand(this, config, logger)
        );

        // authlib-injector 检测提示
        detectAuthlibInjector();

        logger.info("ArcartXSuite Proxy (Velocity) 加载完成。");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("ArcartXSuite Proxy (Velocity) 已关闭。");
    }

    public ProxyServer getServer() {
        return server;
    }

    public ProxyConfig getConfig() {
        return config;
    }

    public YggdrasilAuthenticator getAuthenticator() {
        return authenticator;
    }

    /**
     * 检测代理端是否以 authlib-injector 启动。
     */
    private void detectAuthlibInjector() {
        boolean loaded = false;
        try {
            Class.forName("moe.yushi.authlibinjector.AuthlibInjector");
            loaded = true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.contains("-javaagent") && arg.toLowerCase().contains("authlib-injector")) {
                    loaded = true;
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        if (loaded) {
            logger.info("[Auth] 已检测到 authlib-injector，LittleSkin 多方认证已启用。");
        } else {
            logger.warning("[Auth] 未检测到 authlib-injector！");
            logger.warning("[Auth] 若需支持 LittleSkin 多方认证，请在 Velocity 启动参数中添加:");
            logger.warning("[Auth]   -javaagent:authlib-injector.jar=https://littleskin.cn/api/yggdrasil?mixed");
            logger.warning("[Auth]   --add-opens java.base/java.lang=ALL-UNNAMED");
            logger.warning("[Auth]   --add-opens java.base/java.net=ALL-UNNAMED");
        }
    }
}
