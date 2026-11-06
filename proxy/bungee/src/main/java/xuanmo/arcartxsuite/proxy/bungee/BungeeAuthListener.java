package xuanmo.arcartxsuite.proxy.bungee;

import java.util.UUID;
import java.util.logging.Logger;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;

/**
 * BungeeCord 代理端登录鉴权监听器，在 Login 阶段读取代理平台连接状态，
 * 并按 {@link ProxyConfig} 的策略拒绝或放行离线玩家。
 * 游戏准入由后端认证运行时管理。
 */
public class BungeeAuthListener implements Listener {

    private final ArcartXSuiteBungee plugin;
    private final ProxyConfig config;
    private final Logger logger;

    public BungeeAuthListener(ArcartXSuiteBungee plugin, ProxyConfig config, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(PreLoginEvent event) {
        // PreLogin 阶段无 UUID，不做拦截
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLogin(LoginEvent event) {
        PendingConnection connection = event.getConnection();
        UUID uuid = connection.getUniqueId();
        String username = connection.getName();

        boolean onlineHandshakeRequired = connection.isOnlineMode();

        if (config.debug()) {
            logger.info("[Auth] " + username + " onlineHandshakeRequired=" + onlineHandshakeRequired + " (UUID: " + uuid + ")");
        }

        if (!onlineHandshakeRequired && config.denyOffline()) {
            event.setCancelled(true);
            event.setCancelReason(ChatColor.translateAlternateColorCodes('&', config.kickOfflineMessage()));
            logger.info("[Auth] 拒绝离线玩家: " + username);
        }
    }
}
