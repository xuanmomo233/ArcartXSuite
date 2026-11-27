package xuanmo.arcartxsuite.tab;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.AxsCapabilities;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent;
import xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.api.crossserver.ProxyTabVisibility;

final class TabVisibilityPublisher implements TabCrossServerList, PluginMessageListener {
    private static final long VANISH_REQUEST_TIMEOUT_TICKS = 40;
    private static final long REMOTE_PLAYERS_TTL = TimeUnit.SECONDS.toNanos(10);

    private record VanishRequest(long id, Player player, CompletableFuture<Boolean> result) {}
    private record RemoteEntries(Player viewer, long sequence, long receivedAt, List<TabPlayerList.RemotePlayer> players) {}

    private final JavaPlugin plugin;
    private final BooleanSupplier proxyAllowed;
    private final TabPlayerList playerList;
    private final BukkitTask detailsTask;
    private final Map<UUID, Byte> published = new HashMap<>();
    private final Map<UUID, VanishRequest> pendingVanish = new HashMap<>();
    private final Map<UUID, RemoteEntries> remoteEntries = new HashMap<>();
    private final AtomicBoolean globalQueued = new AtomicBoolean();
    private volatile boolean active = true;

    TabVisibilityPublisher(JavaPlugin plugin, BooleanSupplier proxyAllowed, TabPlayerList playerList) {
        this.plugin = plugin;
        this.proxyAllowed = proxyAllowed;
        this.playerList = playerList;
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, ProxyTabVisibility.CHANNEL, this);
        messenger.registerOutgoingPluginChannel(plugin, ProxyTabVisibility.CHANNEL);
        detailsTask = playerList == null ? null : Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (canPublish()) Bukkit.getOnlinePlayers().forEach(player -> publish(player, false));
        }, 20L, 20L);
    }

    private boolean canPublish() {
        return active && proxyAllowed.getAsBoolean();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!canPublish() || !ProxyTabVisibility.CHANNEL.equals(channel) || data.length == 0) return;
        if (!Bukkit.isPrimaryThread()) {
            byte[] payload = data.clone();
            Bukkit.getScheduler().runTask(plugin, () -> onPluginMessageReceived(channel, player, payload));
            return;
        }
        if (data.length == 1 && data[0] == ProxyTabVisibility.QUERY) {
            publish(player, true);
        } else if (data[0] == ProxyTabVisibility.CROSS_SERVER_LIST && playerList != null) {
            receiveRemotePlayers(player, data);
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

    private void receiveRemotePlayers(Player viewer, byte[] data) {
        UUID id = viewer.getUniqueId();
        if (!viewer.isOnline() || Bukkit.getPlayer(id) != viewer || !AuthAccess.admitted(id)) return;
        List<TabPlayerList.RemotePlayer> players;
        try {
            players = ProxyTabVisibility.readCrossServerList(data).stream().map(player -> {
                var source = player.details();
                var details = source == null ? null
                    : new TabPlayerList.Details(source.inputsKey(), source.permissionMask(), source.sortValue(), source.displayName());
                return new TabPlayerList.RemotePlayer(player.uuid(), player.name(), player.latency(), details);
            }).toList();
        } catch (IllegalArgumentException malformed) {
            return;
        }
        long sequence = ProxyTabVisibility.sequence(data);
        var previous = remoteEntries.get(id);
        if (previous != null && previous.viewer() == viewer && sequence <= previous.sequence()) return;
        remoteEntries.put(id, new RemoteEntries(viewer, sequence, System.nanoTime(), players));
    }

    @Override
    public List<TabPlayerList.RemotePlayer> remotePlayers(Player viewer) {
        if (!canPublish() || !viewer.isOnline()) return List.of();
        UUID id = viewer.getUniqueId();
        var entries = remoteEntries.get(id);
        if (entries == null || entries.viewer() != viewer || Bukkit.getPlayer(id) != viewer || !AuthAccess.admitted(id)
                || System.nanoTime() - entries.receivedAt() >= REMOTE_PLAYERS_TTL) return List.of();
        return entries.players();
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
        remoteEntries.computeIfPresent(id, (key, entries) -> entries.viewer() == event.getPlayer() ? null : entries);
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
        if (state == ProxyTabVisibility.RESTRICTED || state == ProxyTabVisibility.VANISHED_RESTRICTED) {
            remoteEntries.computeIfPresent(id, (key, entries) ->
                entries.viewer() == player
                    ? new RemoteEntries(player, entries.sequence(), entries.receivedAt(), List.of())
                    : null);
        }
        if (force || !Byte.valueOf(state).equals(published.get(id))) {
            sendState(player, state);
            published.put(id, state);
        }
        if (playerList != null && (state == ProxyTabVisibility.VISIBLE || state == ProxyTabVisibility.VANISHED)) {
            // A vanished player can view the list without advertising their own rule inputs.
            ProxyTabVisibility.PlayerDetails details = null;
            if (state == ProxyTabVisibility.VISIBLE
                    && playerList.permissionInputCount() <= Long.SIZE * ProxyTabVisibility.MAX_PERMISSION_WORDS) {
                var local = playerList.detailsFor(player, Bukkit.getOnlinePlayers(), System.nanoTime());
                if (local != null) {
                    String displayName = local.displayName();
                    if (displayName != null && displayName.getBytes(StandardCharsets.UTF_8).length
                            > ProxyTabVisibility.MAX_DISPLAY_NAME_BYTES) {
                        displayName = null;
                    }
                    details = new ProxyTabVisibility.PlayerDetails(
                        local.inputsKey(), local.permissionMask(), local.sortValue(), displayName);
                }
            }
            player.sendPluginMessage(plugin, ProxyTabVisibility.CHANNEL,
                ProxyTabVisibility.playerDetailsMessage(details, ProxyTabVisibility.nextSequence()));
        }
    }

    @Override
    public void close() {
        active = false;
        if (detailsTask != null) detailsTask.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (published.containsKey(player.getUniqueId()) || pendingVanish.containsKey(player.getUniqueId())) {
                sendState(player, ProxyTabVisibility.WITHDRAWN);
            }
        }
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, ProxyTabVisibility.CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, ProxyTabVisibility.CHANNEL);
        published.clear();
        remoteEntries.clear();
        pendingVanish.values().forEach(request -> request.result().complete(false));
        pendingVanish.clear();
    }
}
