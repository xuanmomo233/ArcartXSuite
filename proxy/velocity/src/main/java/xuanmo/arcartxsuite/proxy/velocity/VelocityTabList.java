package xuanmo.arcartxsuite.proxy.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.util.GameProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import xuanmo.arcartxsuite.api.crossserver.ProxyTabVisibility;

/** Supplements native tab lists with players admitted and visible on other Suite backends. */
final class VelocityTabList implements AutoCloseable {
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyTabVisibility.CHANNEL);
    private static final byte[] QUERY = {ProxyTabVisibility.QUERY};
    private static final long STATE_TTL = TimeUnit.SECONDS.toNanos(30);
    private static final long MOVE_TTL = TimeUnit.SECONDS.toNanos(15);
    private static final long NATIVE_REMOVE_GRACE = TimeUnit.SECONDS.toNanos(1);

    private record Visibility(ServerConnection connection, byte state, long receivedAt) {}
    private record Move(ServerConnection previous, RegisteredServer destination, boolean carryVanish,
                        boolean failureCorrelated, long startedAt) {}
    private record VanishUpdate(ServerConnection connection, long sequence, boolean pendingGrant) {}
    private record Member(Player player, RegisteredServer backend) {}
    private record Roster(List<Player> players, Set<UUID> ids) {}

    private final Object plugin;
    private final ProxyServer proxy;
    private final int maxRemoteEntries;
    private final Map<UUID, Visibility> visibility = new HashMap<>();
    private final Map<UUID, Move> moves = new HashMap<>();
    private final Map<UUID, VanishUpdate> vanishUpdates = new HashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> awaitingVanish = new HashSet<>();
    private final Map<UUID, Map<UUID, TabListEntry>> injected = new HashMap<>();
    private final ScheduledTask refreshTask;
    private final ScheduledTask queryTask;
    private long reconcileAfter;
    private boolean dirty = true;
    private boolean closed;

    VelocityTabList(Object plugin, ProxyServer proxy, int maxRemoteEntries) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.maxRemoteEntries = maxRemoteEntries;
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getEventManager().register(plugin, this);
        refreshTask = proxy.getScheduler().buildTask(plugin, this::refresh).repeat(200, TimeUnit.MILLISECONDS).schedule();
        queryTask = proxy.getScheduler().buildTask(plugin, this::requestStates).repeat(10, TimeUnit.SECONDS).schedule();
    }

    @Subscribe(order = PostOrder.LAST)
    public synchronized void switching(ServerPreConnectEvent event) {
        if (closed) return;
        Player player = event.getPlayer();
        var old = current(player);
        var destination = event.getResult().getServer().orElse(null);
        if (destination == null || (old != null && destination.equals(old.getServer()))) return;
        UUID id = player.getUniqueId();
        if (grantPending(id)) {
            // Only the source can confirm that Essentials applied the grant to its original session.
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            player.sendMessage(Component.text("隐身状态正在确认，请稍后切服。"));
            return;
        }
        // Modern forwarding takes the profile from this proxy connection before Paper's join event.
        var previousMove = moves.get(id);
        boolean carryVanish = vanished.contains(id) || (previousMove != null && previousMove.carryVanish());
        setProfileVanish(player, carryVanish);
        if (carryVanish) {
            vanished.add(id);
            awaitingVanish.add(id);
        }
        moves.put(id, new Move(old, destination, carryVanish,
            previousMove == null || previousMove.previous() != old, System.nanoTime()));
        visibility.remove(id);
        // Remove owned entries before the destination backend can publish native entries for this UUID.
        clearViewer(id);
        clearTarget(id);
        dirty = true;
    }

    @Subscribe
    public synchronized void connected(ServerPostConnectEvent event) {
        if (closed) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        var connection = current(player);
        Move move = moves.get(id);
        if (move == null || move.previous() != connection) setProfileVanish(player, false);
        if (move != null && move.previous() != connection && moves.remove(id, move)) {
            // The old backend can remove its native entry after the new backend reports visibility.
            // Recheck after the switch burst settles, when it is safe to add the missing remote entry.
            reconcileAfter = System.nanoTime() + NATIVE_REMOVE_GRACE;
        }
        VanishUpdate update = vanishUpdates.get(id);
        if (update != null && update.connection() != connection) vanishUpdates.remove(id);
        Visibility state = visibility.get(id);
        if (state != null && state.connection() != connection) visibility.remove(id);
        if (connection != null) connection.sendPluginMessage(CHANNEL, QUERY);
        dirty = true;
    }

    @Subscribe
    public synchronized void kicked(KickedFromServerEvent event) {
        if (closed || !event.kickedDuringServerConnect()) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Move move = moves.get(id);
        if (move == null || !move.failureCorrelated() || !move.destination().equals(event.getServer())
                || current(player) != move.previous()) return;
        moves.remove(id);
        awaitingVanish.remove(id);
        setProfileVanish(player, false);
        var connection = current(player);
        if (connection != null) connection.sendPluginMessage(CHANNEL, QUERY);
        dirty = true;
    }

    @Subscribe
    public synchronized void disconnected(DisconnectEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        visibility.remove(id);
        moves.remove(id);
        vanishUpdates.remove(id);
        vanished.remove(id);
        awaitingVanish.remove(id);
        injected.remove(id);
        clearTarget(id);
        dirty = true;
    }

    @Subscribe
    public synchronized void received(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (closed || !(event.getSource() instanceof ServerConnection backend)) return;
        Player player = backend.getPlayer();
        if (current(player) != backend) return;
        UUID id = player.getUniqueId();
        byte[] data = event.getData();
        if (data.length != ProxyTabVisibility.STATE_MESSAGE_LENGTH) return;
        long sequence = ProxyTabVisibility.sequence(data);
        VanishUpdate previous = vanishUpdates.get(id);
        if (previous != null && previous.connection() == backend && sequence <= previous.sequence()) return;
        Move move = moves.get(id);
        boolean onSource = move != null && move.previous() == backend;
        byte state = data[0];
        if (state == ProxyTabVisibility.VANISH_REQUEST) {
            grantVanish(backend, id, sequence, previous, onSource);
            return;
        }
        if (onSource && System.nanoTime() - move.startedAt() < MOVE_TTL) return;
        if (state == ProxyTabVisibility.WITHDRAWN) {
            vanishUpdates.put(id, new VanishUpdate(backend, sequence,
                previous != null && previous.connection() == backend && previous.pendingGrant()));
            if (visibility.remove(id) != null) {
                clearTarget(id);
                clearViewer(id);
                dirty = true;
            }
        } else if (state == ProxyTabVisibility.VISIBLE || state == ProxyTabVisibility.VANISHED
                || restricted(state)) {
            vanishUpdates.put(id, new VanishUpdate(backend, sequence, false));
            if (state == ProxyTabVisibility.VANISHED || state == ProxyTabVisibility.VANISHED_RESTRICTED) {
                vanished.add(id);
                if (!onSource) awaitingVanish.remove(id);
            } else {
                if (!onSource && awaitingVanish.contains(id)) return;
                vanished.remove(id);
            }
            Visibility old = visibility.put(id, new Visibility(backend, state, System.nanoTime()));
            if (old == null || old.connection() != backend || old.state() != state) {
                if (state != ProxyTabVisibility.VISIBLE) clearTarget(id);
                if (restricted(state)) clearViewer(id);
                dirty = true;
            }
        }
    }

    private void grantVanish(ServerConnection backend, UUID id, long sequence, VanishUpdate previous, boolean onSource) {
        if (onSource) {
            backend.sendPluginMessage(CHANNEL, ProxyTabVisibility.stateMessage(ProxyTabVisibility.VANISH_DENIED, sequence));
            return;
        }
        // This listener and switching() use the same lock. Once the grant is pending, the
        // source must publish its result before the player can switch.
        vanishUpdates.put(id, new VanishUpdate(backend, sequence, true));
        clearTarget(id);
        dirty = true;
        if (!backend.sendPluginMessage(CHANNEL, ProxyTabVisibility.stateMessage(ProxyTabVisibility.VANISH_GRANTED, sequence))) {
            if (previous == null) vanishUpdates.remove(id);
            else vanishUpdates.put(id, previous);
        }
    }

    private boolean grantPending(UUID id) {
        VanishUpdate update = vanishUpdates.get(id);
        return update != null && update.pendingGrant();
    }

    private static boolean restricted(byte state) {
        return state == ProxyTabVisibility.RESTRICTED || state == ProxyTabVisibility.VANISHED_RESTRICTED;
    }

    private static void setProfileVanish(Player player, boolean hidden) {
        var properties = player.getGameProfileProperties();
        if (!hidden && properties.stream().noneMatch(p -> ProxyTabVisibility.VANISH_PROFILE_PROPERTY.equals(p.getName()))) return;
        var forwarded = new ArrayList<GameProfile.Property>(properties.size() + 1);
        for (var property : properties) {
            if (!ProxyTabVisibility.VANISH_PROFILE_PROPERTY.equals(property.getName())) forwarded.add(property);
        }
        if (hidden) forwarded.add(new GameProfile.Property(ProxyTabVisibility.VANISH_PROFILE_PROPERTY, "1", ""));
        player.setGameProfileProperties(forwarded);
    }

    private synchronized void requestStates() {
        if (closed) return;
        long now = System.nanoTime();
        visibility.values().removeIf(state -> now - state.receivedAt() >= STATE_TTL);
        // Also cover delayed native updates which outlive the switch grace period.
        dirty = true;
        for (Player player : proxy.getAllPlayers()) {
            var connection = current(player);
            if (connection != null) connection.sendPluginMessage(CHANNEL, QUERY);
        }
    }

    private synchronized void refresh() {
        if (closed) return;
        long now = System.nanoTime();
        if (!dirty && (reconcileAfter == 0 || now - reconcileAfter < 0)) return;
        if (reconcileAfter != 0 && now - reconcileAfter >= 0) reconcileAfter = 0;
        dirty = false;
        List<Member> visible = new ArrayList<>();
        Map<Player, RegisteredServer> viewers = new HashMap<>();
        for (Player player : proxy.getAllPlayers()) {
            UUID id = player.getUniqueId();
            Visibility state = visibility.get(id);
            Move move = moves.get(id);
            if (state == null || state.connection() != current(player)
                    || (move != null && now - move.startedAt() < MOVE_TTL)
                    || now - state.receivedAt() >= STATE_TTL || restricted(state.state())) {
                clearViewer(id);
                continue;
            }
            var backend = state.connection().getServer();
            viewers.put(player, backend);
            if (state.state() == ProxyTabVisibility.VISIBLE && !grantPending(id)) {
                visible.add(new Member(player, backend));
            }
        }
        visible.sort(Comparator.comparing((Member member) -> member.player().getUsername(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(member -> member.player().getUniqueId()));
        Map<RegisteredServer, Roster> byBackend = new HashMap<>();
        viewers.forEach((viewer, backend) ->
            update(viewer, byBackend.computeIfAbsent(backend, local -> roster(visible, local))));
    }

    private Roster roster(List<Member> visible, RegisteredServer local) {
        List<Player> players = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (Member member : visible) {
            if (local.equals(member.backend())) continue;
            players.add(member.player());
            ids.add(member.player().getUniqueId());
            if (players.size() == maxRemoteEntries) break;
        }
        return new Roster(players, ids);
    }

    private void update(Player viewer, Roster roster) {
        var tab = viewer.getTabList();
        var owned = injected.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>());
        owned.entrySet().removeIf(row -> {
            UUID id = row.getKey();
            if (tab.getEntry(id).orElse(null) != row.getValue()) return true;
            if (roster.ids().contains(id)) return false;
            tab.removeEntry(id);
            return true;
        });
        for (Player remote : roster.players()) {
            UUID id = remote.getUniqueId();
            if (tab.containsEntry(id)) continue;
            var row = TabListEntry.builder().tabList(tab).profile(remote.getGameProfile())
                .displayName(Component.text(remote.getUsername())).latency((int) Math.max(0, remote.getPing()))
                .gameMode(0).listed(true).build();
            try {
                tab.addEntry(row);
                owned.put(id, tab.getEntry(id).orElse(row));
            } catch (IllegalArgumentException duplicate) {
                if (!tab.containsEntry(id)) throw duplicate;
            }
        }
        if (owned.isEmpty()) injected.remove(viewer.getUniqueId());
    }

    private static ServerConnection current(Player player) {
        return player.getCurrentServer().orElse(null);
    }

    private static void removeOwned(UUID id, TabListEntry row) {
        if (row != null && row.getTabList().getEntry(id).orElse(null) == row) {
            row.getTabList().removeEntry(id);
        }
    }

    private void clearTarget(UUID id) {
        injected.values().forEach(rows -> removeOwned(id, rows.remove(id)));
    }

    private void clearViewer(UUID id) {
        var rows = injected.remove(id);
        if (rows != null) rows.forEach(VelocityTabList::removeOwned);
    }

    @Override
    public synchronized void close() {
        closed = true;
        refreshTask.cancel();
        queryTask.cancel();
        proxy.getEventManager().unregisterListener(plugin, this);
        proxy.getChannelRegistrar().unregister(CHANNEL);
        injected.values().forEach(rows -> rows.forEach(VelocityTabList::removeOwned));
        injected.clear();
        visibility.clear();
        moves.clear();
        vanishUpdates.clear();
        vanished.clear();
        awaitingVanish.clear();
    }
}
