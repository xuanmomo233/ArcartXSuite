package xuanmo.arcartxsuite.tab;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import xuanmo.arcartxsuite.api.AxsCapabilities;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent;
import xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.api.crossserver.ProxyTabVisibility;

final class TabVisibilityPublisher implements TabProxyRoster, PluginMessageListener {
    private static final long VANISH_REQUEST_TIMEOUT_TICKS = 40;

    private record VanishRequest(long id, Player player, CompletableFuture<Boolean> result) {}

    private final JavaPlugin plugin;
    private final BooleanSupplier proxyAllowed;
    private final Map<UUID, Byte> published = new HashMap<>();
    private final Map<UUID, VanishRequest> pendingVanish = new HashMap<>();
    private final AtomicBoolean globalQueued = new AtomicBoolean();
    private volatile boolean active = true;

    TabVisibilityPublisher(JavaPlugin plugin, BooleanSupplier proxyAllowed) {
        this.plugin = plugin;
        this.proxyAllowed = proxyAllowed;
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, ProxyTabVisibility.CHANNEL, this);
        messenger.registerOutgoingPluginChannel(plugin, ProxyTabVisibility.CHANNEL);
    }

    private boolean canPublish() {
        return active && proxyAllowed.getAsBoolean();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!canPublish() || !ProxyTabVisibility.CHANNEL.equals(channel)) return;
        if (!Bukkit.isPrimaryThread()) {
            byte[] payload = data.clone();
            Bukkit.getScheduler().runTask(plugin, () -> onPluginMessageReceived(channel, player, payload));
            return;
        }
        if (data.length == 1 && data[0] == ProxyTabVisibility.QUERY) {
            publish(player, true);
        } else if (data.length == ProxyTabVisibility.STATE_MESSAGE_LENGTH
                && (data[0] == ProxyTabVisibility.VANISH_GRANTED || data[0] == ProxyTabVisibility.VANISH_DENIED)) {
            UUID id = player.getUniqueId();
            var request = pendingVanish.get(id);
            if (request == null || request.player() != player || request.id() != ProxyTabVisibility.sequence(data)) return;
            pendingVanish.remove(id);
            boolean granted = data[0] == ProxyTabVisibility.VANISH_GRANTED;
            if (granted) published.remove(id);
            request.result().complete(granted);
            if (granted) publish(player, false);
        }
    }

    @Override
    public CompletionStage<Boolean> requestVanish(Player player) {
        UUID id = player.getUniqueId();
        if (!canPublish() || !player.isOnline() || pendingVanish.containsKey(id)) {
            return CompletableFuture.completedFuture(false);
        }
        var request = new VanishRequest(ProxyTabVisibility.nextSequence(), player, new CompletableFuture<>());
        pendingVanish.put(id, request);
        player.sendPluginMessage(plugin, ProxyTabVisibility.CHANNEL,
            ProxyTabVisibility.stateMessage(ProxyTabVisibility.VANISH_REQUEST, request.id()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!pendingVanish.remove(id, request)) return;
            request.result().complete(false);
            publish(player, true);
        }, VANISH_REQUEST_TIMEOUT_TICKS);
        return request.result();
    }

    private void sendState(Player player, byte state) {
        player.sendPluginMessage(plugin, ProxyTabVisibility.CHANNEL,
            ProxyTabVisibility.stateMessage(state, ProxyTabVisibility.nextSequence()));
    }

    @EventHandler
    public void joined(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> publish(event.getPlayer(), false));
    }

    @EventHandler
    public void left(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        published.remove(id);
        var request = pendingVanish.remove(id);
        if (request != null) request.result().complete(false);
    }

    @EventHandler
    public void admitted(AuthAdmissionEvent event) {
        refresh(event.snapshot().playerId());
    }

    @EventHandler
    public void restricted(AuthRestrictionEvent event) {
        refresh(event.previous().playerId());
    }

    private void refresh(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) requestViewerRefresh(player, "admission");
    }

    @Override
    public void requestViewerRefresh(Player viewer, String reason) {
        if (!canPublish()) return;
        if (Bukkit.isPrimaryThread()) publish(viewer, false);
        else Bukkit.getScheduler().runTask(plugin, () -> publish(viewer, false));
    }

    @Override
    public void requestGlobalRefresh(String reason) {
        if (!canPublish() || !globalQueued.compareAndSet(false, true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            globalQueued.set(false);
            if (canPublish()) Bukkit.getOnlinePlayers().forEach(player -> publish(player, false));
        });
    }

    private void publish(Player player, boolean force) {
        if (!canPublish() || !player.isOnline()) return;
        UUID id = player.getUniqueId();
        // An older visible state cannot revoke permission for a pending hide command.
        if (pendingVanish.containsKey(id)) return;
        var essentials = AxsCapabilities.get(EssentialsQueryable.class);
        boolean vanished = essentials != null && essentials.isVanished(id);
        byte state;
        if (vanished) {
            state = AuthAccess.admitted(id) ? ProxyTabVisibility.VANISHED : ProxyTabVisibility.VANISHED_RESTRICTED;
        } else {
            state = AuthAccess.admitted(id) ? ProxyTabVisibility.VISIBLE : ProxyTabVisibility.RESTRICTED;
        }
        if (force || !Byte.valueOf(state).equals(published.get(id))) {
            sendState(player, state);
            published.put(id, state);
        }
    }

    @Override
    public void close() {
        active = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (published.containsKey(player.getUniqueId()) || pendingVanish.containsKey(player.getUniqueId())) {
                sendState(player, ProxyTabVisibility.WITHDRAWN);
            }
        }
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, ProxyTabVisibility.CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, ProxyTabVisibility.CHANNEL);
        published.clear();
        pendingVanish.values().forEach(request -> request.result().complete(false));
        pendingVanish.clear();
    }
}
