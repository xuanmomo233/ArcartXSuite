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
import xuanmo.arcartxsuite.proxy.common.model.AccountType;

/**
 * BungeeCord 代理端登录鉴权监听器，在 Login 阶段根据 UUID 版本做账号类型粗略判定，
 * 并按 {@link ProxyConfig} 的策略拒绝或放行离线玩家。
 * 账号类型的精确判定由后端 {@code AccountTypeServiceImpl} 完成。
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

        AccountType type = AccountType.fromUuid(uuid);

        if (config.debug()) {
            logger.info("[Auth] " + username + " 登录 BungeeCord，账号类型: " + type.displayName() + " (UUID: " + uuid + ")");
        }

        if (type == AccountType.OFFLINE && config.denyOffline()) {
            event.setCancelled(true);
            event.setCancelReason(ChatColor.translateAlternateColorCodes('&', config.kickOfflineMessage()));
            logger.info("[Auth] 拒绝离线玩家: " + username);
        }
    }
}
