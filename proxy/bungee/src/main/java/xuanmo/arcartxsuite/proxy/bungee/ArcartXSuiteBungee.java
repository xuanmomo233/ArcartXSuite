package xuanmo.arcartxsuite.proxy.bungee;

import java.io.File;
import java.util.logging.Logger;

import net.md_5.bungee.api.plugin.Plugin;

import xuanmo.arcartxsuite.proxy.common.auth.YggdrasilAuthenticator;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;

/**
 * ArcartXSuite BungeeCord 代理端伴侣插件。
 */
public class ArcartXSuiteBungee extends Plugin {

    private ProxyConfig config;
    private YggdrasilAuthenticator authenticator;
    private BungeeAuthListener authListener;

    @Override
    public void onEnable() {
        Logger logger = getLogger();

        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║      ArcartXSuite Proxy (BungeeCord) 加载中...             ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");

        // 加载配置
        config = new ProxyConfig(logger, getDataFolder());
        config.loadDefaults();
        config.extractDefaultConfig("proxy-config.yml");

        // 初始化认证器
        authenticator = new YggdrasilAuthenticator(logger, config.debug());

        // 注册事件监听器
        authListener = new BungeeAuthListener(this, config, authenticator, logger);
        getProxy().getPluginManager().registerListener(this, authListener);

        // 注册命令
        getProxy().getPluginManager().registerCommand(this, new ProxyCommand(this, config, logger));

        // authlib-injector 检测提示
        detectAuthlibInjector(logger);

        logger.info("ArcartXSuite Proxy (BungeeCord) 加载完成。");
    }

    @Override
    public void onDisable() {
        getLogger().info("ArcartXSuite Proxy (BungeeCord) 已关闭。");
    }

    public ProxyConfig getProxyConfig() {
        return config;
    }

    public YggdrasilAuthenticator getAuthenticator() {
        return authenticator;
    }

    private void detectAuthlibInjector(Logger logger) {
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
            logger.warning("[Auth] 若需支持 LittleSkin 多方认证，请在 BungeeCord 启动参数中添加:");
            logger.warning("[Auth]   -javaagent:authlib-injector.jar=https://littleskin.cn/api/yggdrasil?mixed");
            logger.warning("[Auth]   --add-opens java.base/java.lang=ALL-UNNAMED");
            logger.warning("[Auth]   --add-opens java.base/java.net=ALL-UNNAMED");
        }
    }
}
