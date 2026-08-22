package xuanmo.arcartxsuite.proxy.velocity;

import java.util.UUID;
import java.util.logging.Logger;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import xuanmo.arcartxsuite.proxy.common.config.ProxyConfig;
import xuanmo.arcartxsuite.proxy.common.model.AccountType;

/**
 * Velocity 认证事件监听器。
 * <p>
 * 代理端本身不直接处理 Yggdrasil 握手（由 authlib-injector 或 Velocity 内置完成），
 * 但在此层做离线玩家拦截（根据 UUID 版本判定）。
 * 账号类型的精确判定由后端 {@code AccountTypeServiceImpl} 完成，代理端不重复处理。
 */
public class VelocityAuthListener {

    private final ArcartXSuiteVelocity plugin;
    private final ProxyServer server;
    private final ProxyConfig config;
    private final Logger logger;

    public VelocityAuthListener(ArcartXSuiteVelocity plugin, ProxyServer server,
                                 ProxyConfig config, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.config = config;
        this.logger = logger;
    }

    /**
     * PreLogin：Velocity 已完成 Mojang/Yggdrasil 验证后的早期拦截点。
     * <p>
     * PreLogin 阶段 UUID 尚未确定，无法做基于 UUID 版本的账号类型判定。
     * 真正的离线拦截在 {@link #onLogin(LoginEvent)} 阶段（UUID 已确定）执行。
     */
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        // PreLogin 阶段无 UUID，不做拦截
    }

    /**
     * LoginEvent：Velocity 已完成全部验证，玩家即将进入代理端。
     * 在此阶段：
     * 1. 根据 UUID 版本判定账号类型
     * 2. 若为离线且配置拒绝，踢出
     */
    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        AccountType type = AccountType.fromUuid(uuid);

        if (config.debug()) {
            logger.info("[Auth] " + username + " 登录代理端，账号类型判定: " + type.displayName() + " (UUID: " + uuid + ")");
        }

        // 离线拦截
        if (type == AccountType.OFFLINE && config.denyOffline()) {
            player.disconnect(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(legacyToMiniMessage(config.kickOfflineMessage())));
            logger.info("[Auth] 拒绝离线玩家: " + username);
            return;
        }

        // 账号类型的精确判定由后端 AccountTypeServiceImpl 通过 Mojang API / 本地混合代理完成，
        // 代理端不需要通过 Plugin Message 透传账号类型。
    }

    private String legacyToMiniMessage(String legacy) {
        if (legacy == null) return "";
        String msg = legacy
            .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
            .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
            .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
            .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
            .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
            .replace("&f", "<white>").replace("&k", "<obfuscated>").replace("&l", "<bold>")
            .replace("&m", "<strikethrough>").replace("&n", "<underlined>").replace("&o", "<italic>")
            .replace("&r", "<reset>");
        // 处理 &#RRGGBB 格式
        msg = msg.replaceAll("&#([0-9A-Fa-f]{6})", "<#$1>");
        return msg;
    }
}
