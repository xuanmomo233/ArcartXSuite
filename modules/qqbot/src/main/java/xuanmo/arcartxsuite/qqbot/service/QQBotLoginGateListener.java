package xuanmo.arcartxsuite.qqbot.service;

import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.account.AccountType;
import xuanmo.arcartxsuite.api.account.AccountTypeService;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotWhitelistLoginConfig;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;

/**
 * 登录门控日志监听器：记录玩家账号类型与绑定状态，不再在 PreLogin 阶段拦截任何人。
 * <p>
 * QQ 绑定验证已迁移到 {@link xuanmo.arcartxsuite.loginview.service.LoginViewService}
 * 的登录面板中完成。本监听器仅做审计日志，供服务器管理员排查问题。
 */
public final class QQBotLoginGateListener implements Listener {

    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final QQBotRepository repository;
    private final AccountTypeService accountTypeService;
    private final Logger logger;

    public QQBotLoginGateListener(
        JavaPlugin plugin,
        QQBotConfiguration config,
        QQBotRepository repository,
        AccountTypeService accountTypeService,
        Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.accountTypeService = accountTypeService;
        this.logger = logger;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        AccountType type = accountTypeService.resolveBlocking(uuid, name);
        boolean bound = repository.findByPlayerName(name) != null;

        if (config.debug() || config.whitelistLogin().enabled()) {
            logger.info("[QQBot/Gate] " + name + " UUID=" + uuid
                + " v" + (uuid == null ? "?" : uuid.version())
                + " type=" + type.id()
                + " qqBound=" + bound
                + " — 已放行，绑定验证将在登录面板中完成");
        }
    }
}
