package xuanmo.arcartxsuite.qqbot.service;

import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;

/** Audit only. Binding and admission are authoritative in the authentication backend. */
public final class QQBotLoginAuditListener implements Listener {
    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final QQBotRepository repository;
    private final Logger logger;
    public QQBotLoginAuditListener(JavaPlugin plugin,QQBotConfiguration config,QQBotRepository repository,Logger logger) {
        this.plugin=plugin; this.config=config; this.repository=repository; this.logger=logger;
    }
    public void register() { org.bukkit.Bukkit.getPluginManager().registerEvents(this,plugin); }
    public void unregister() { HandlerList.unregisterAll(this); }
    @EventHandler public void onAdmission(AuthAdmissionEvent event) {
        if(config.debug()) logger.info("[Auth] profile="+event.snapshot().playerId()+" provider="+event.snapshot().provider()
            +" qqBound="+(repository.findByPlayer(event.snapshot().playerId())!=null));
    }
}
