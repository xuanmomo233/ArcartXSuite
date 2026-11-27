package xuanmo.arcartxsuite.essentials.service;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

final class VelocityVanishBridge implements Listener {
    private final VelocityVanishProfile profile;
    private final Consumer<Player> applyVanish;
    private final Logger logger;

    static Listener create(Logger logger, BooleanSupplier allowed, Consumer<Player> applyVanish) {
        var profile = VelocityVanishProfile.detect(logger, allowed);
        return profile == null ? null : new VelocityVanishBridge(profile, applyVanish, logger);
    }

    private VelocityVanishBridge(VelocityVanishProfile profile, Consumer<Player> applyVanish, Logger logger) {
        this.profile = profile;
        this.applyVanish = applyVanish;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void captureProxyVanish(AsyncPlayerPreLoginEvent event) {
        try {
            if (profile.capture(event)) return;
        } catch (ReflectiveOperationException failure) {
            logger.log(Level.WARNING, "无法读取 Velocity 隐身交接。", failure);
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "无法完成 Velocity 隐身交接，请重新连接。");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void clearDeniedProxyVanish(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            profile.take(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void applyProxyVanish(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var origin = profile.take(player.getUniqueId());
        if (origin == null) return;
        var address = player.getAddress();
        if (address == null || !origin.equals(address.getAddress())) {
            player.kickPlayer("无法完成 Velocity 隐身交接，请重新连接。");
            return;
        }
        // Paper publishes its native player-list entries after the join event.
        applyVanish.accept(player);
    }
}
