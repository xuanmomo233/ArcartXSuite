package xuanmo.arcartxsuite.proxy.bungee;

import java.io.File;
import java.util.logging.Logger;

import net.md_5.bungee.api.plugin.Plugin;

import xuanmo.arcartxsuite.proxy.common.auth.AuthlibInjectorDetector;
import xuanmo.arcartxsuite.proxy.common.auth.AuthlibInjectorManager;
import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;

/**
 * ArcartXSuite BungeeCord 代理端伴侣插件。
 * <p>
 * 提供离线玩家拦截 + authlib-injector 混合登录管理。
 * 混合登录通过 /axsproxy auth setup 生成启动脚本，脚本先启动本地 Yggdrasil 混合代理，
 * 再启动 BungeeCord（带 -javaagent:authlib-injector.jar=http://127.0.0.1:25599）。
 */
public class ArcartXSuiteBungee extends Plugin {

    private ProxyConfig config;
    private BungeeAuthListener authListener;

    @Override
    public void onEnable() {
        Logger logger = getLogger();

        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║      ArcartXSuite Proxy (BungeeCord) 加载中...             ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");

        // 加载配置
        config = new ProxyConfig(logger, getDataFolder());
        config.load("proxy-config.yml");

        // 注册事件监听器
        authListener = new BungeeAuthListener(this, config, logger);
        getProxy().getPluginManager().registerListener(this, authListener);

        // 注册命令
        getProxy().getPluginManager().registerCommand(this, new ProxyCommand(this, config, logger));

        // authlib-injector + 混合代理检测
        detectAuthAndProxy(logger);

        logger.info("ArcartXSuite Proxy (BungeeCord) 加载完成。");
    }

    @Override
    public void onDisable() {
        getLogger().info("ArcartXSuite Proxy (BungeeCord) 已关闭。");
    }

    public ProxyConfig getProxyConfig() {
        return config;
    }

    private void detectAuthAndProxy(Logger logger) {
        boolean agentLoaded = AuthlibInjectorDetector.detect();

        if (!config.authEnabled()) {
            // auth 未启用：只做基础提示
            if (agentLoaded) {
                logger.info("[Auth] 已检测到 authlib-injector，但 auth.enabled=false。");
                logger.info("[Auth] 若需混合登录，请在 proxy-config.yml 中设置 auth.enabled: true");
            }
            return;
        }

        // auth 已启用：完整检测
        AuthlibInjectorManager mgr = new AuthlibInjectorManager(
            logger, getDataFolder(), config.mixedProxyPort(),
            config.mixedProxyBindHost(), config.mixedProxyToken(), config.mixedProxyAllowIps(),
            AuthlibInjectorManager.Platform.BUNGEECORD);

        if (!agentLoaded) {
            logger.warning("[Auth] 未检测到 authlib-injector！");
            logger.warning("[Auth] 请执行 /axsproxy auth setup 生成启动脚本，");
            logger.warning("[Auth] 然后使用 start-mixed-auth 脚本启动 BungeeCord。");
            return;
        }

        logger.info("[Auth] 已检测到 authlib-injector。");

        // 检测混合代理连通性
        if (mgr.isMixedProxyReachable()) {
            logger.info("[Auth] 本地混合代理已就绪 (端口 " + config.mixedProxyPort() + ")。");
            logger.info("[Auth] 后端子服可通过此端口查询权威账号类型。");
        } else {
            logger.warning("[Auth] 本地混合代理未启动 (端口 " + config.mixedProxyPort() + ")！");
            logger.warning("[Auth] 请使用 start-mixed-auth 脚本启动 BungeeCord，");
            logger.warning("[Auth] 脚本会先启动混合代理再启动 BungeeCord。");
        }

        // 版本检测
        if (config.authAutoCheckVersion()) {
            mgr.checkVersionAndNotify();
        }
    }
}
