package xuanmo.arcartxsuite.proxy.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.auth.minecraft.ProxyProofs;
import xuanmo.arcartxsuite.proxy.common.auth.ProxyProofForwarding;

/** Velocity connection ownership around the shared receipt and proof protocol. */
public final class VelocityProofForwarder implements AutoCloseable {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("axs", "auth");
    private record Session(Player player, CompletableFuture<ProxyProofs.Identity> identity) {}
    private final Object plugin;
    private final ProxyServer server;
    private final ProxyProofForwarding forwarding;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();
    private final Semaphore pending = new Semaphore(32);

    VelocityProofForwarder(Object plugin, ProxyServer server, Path folder, Map<String, Object> config, Logger log) throws Exception {
        this.plugin = plugin;
        this.server = server;
        forwarding = new ProxyProofForwarding(folder, config, log);
        if (forwarding.enabled()) {
            server.getChannelRegistrar().register(CHANNEL);
            server.getEventManager().register(plugin, this);
        }
    }

    @Subscribe public EventTask login(LoginEvent event) {
        if (!forwarding.enabled() || !event.getPlayer().isOnlineMode()) return null;
        Player player = event.getPlayer();
        var identity = new CompletableFuture<ProxyProofs.Identity>();
        sessions.put(player.getUniqueId(), new Session(player, identity));
        if (!pending.tryAcquire()) {
            identity.completeExceptionally(new IllegalStateException("Busy"));
            return null;
        }
        return EventTask.async(() -> {
            try {
                identity.complete(forwarding.claim(player.getUniqueId(), player.getUsername()));
            } catch (Exception e) {
                identity.completeExceptionally(new IllegalStateException("Gateway proof unavailable"));
            } finally {
                pending.release();
            }
        });
    }

    @Subscribe public void message(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection backend) || event.getData().length > ProxyProofForwarding.MAX_FRAME_BYTES) return;
        Player player = backend.getPlayer();
        if (!player.getCurrentServer().filter(current -> current == backend).isPresent()) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.player() != player || !session.identity().isDone() || session.identity().isCompletedExceptionally()) return;
        long now = System.nanoTime();
        Long previous = lastRequest.put(player.getUniqueId(), now);
        if (previous != null && now - previous < 500_000_000L) return;
        try {
            byte[] response = forwarding.response(backend.getServerInfo().getName(), player.getUniqueId(), player.getUsername(),
                session.identity().join(), event.getData());
            if (response != null) backend.sendPluginMessage(CHANNEL, response);
        } catch (Exception ignored) {
            // A rejected frame cannot create an identity or grant admission.
        }
    }

    @Subscribe public void disconnect(DisconnectEvent event) {
        UUID profile = event.getPlayer().getUniqueId();
        Session session = sessions.get(profile);
        if (session != null && session.player() == event.getPlayer() && sessions.remove(profile, session)) lastRequest.remove(profile);
    }

    @Override public void close() {
        if (forwarding.enabled()) {
            server.getEventManager().unregisterListener(plugin, this);
            server.getChannelRegistrar().unregister(CHANNEL);
        }
        sessions.clear();
        lastRequest.clear();
    }
}
