package xuanmo.arcartxsuite.exploration;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import xuanmo.arcartxsuite.api.auth.AuthRestrictionEvent;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;

final class ExplorationService implements Listener {
    private final ExplorationConfig config;
    private final PacketBridgeAPI bridge;
    private final String ui;
    private final Discoveries discoveries;
    private final MessageProvider messages;
    private final Predicate<Player> admitted;
    private final Logger logger;
    private long tick;

    // The key callback may run on the network thread. Only the press flag crosses threads.
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private static final class View {
        final Player player;
        final ExplorationConfig.Region region;
        final AtomicBoolean pressed = new AtomicBoolean();
        Location origin;
        long startTick;
        int percent = -1;

        View(Player player, ExplorationConfig.Region region) {
            this.player = player;
            this.region = region;
        }
    }

    ExplorationService(ExplorationConfig config, PacketBridgeAPI bridge, String ui, Discoveries discoveries,
                       MessageProvider messages, Predicate<Player> admitted, Logger logger) {
        this.config = config;
        this.bridge = bridge;
        this.ui = ui;
        this.discoveries = discoveries;
        this.messages = messages;
        this.admitted = admitted;
        this.logger = logger;
    }

    void press(Player player) {
        View view = views.get(player.getUniqueId());
        if (view != null && view.player == player) view.pressed.set(true);
    }

    void tick(Collection<? extends Player> online) {
        tick++;
        if (tick % 20 == 0) discoveries.refreshAdmissions();
        // Discover newly eligible players four times per second. Only visible HUDs need updates.
        if (tick % 5 == 0) {
            for (Player player : online) {
                if (!views.containsKey(player.getUniqueId())) showPrompt(player);
            }
        }
        for (View view : views.values()) update(view);
    }

    private void showPrompt(Player player) {
        Location location = player.getLocation();
        var regions = config.worlds().get(location.getWorld().getName());
        if (regions == null || discoveries.hasPendingDiscovery(player.getUniqueId())) return;
        var region = select(player, location, regions);
        if (region == null || !eligible(player) || !bridge.openUi(player, ui)) return;
        View view = new View(player, region);
        views.put(player.getUniqueId(), view);
        sync(view, -1);
    }

    private ExplorationConfig.Region select(Player player, Location location, List<ExplorationConfig.Region> regions) {
        long now = System.currentTimeMillis();
        for (var region : regions) {
            if (!region.contains(location)) continue;
            if (region.cooldownSeconds() == 0) return region;
            Long expiry = player.getPersistentDataContainer().get(region.cooldownKey(), PersistentDataType.LONG);
            if (expiry == null) return region;
            if (expiry <= now) {
                player.getPersistentDataContainer().remove(region.cooldownKey());
                return region;
            }
        }
        return null;
    }

    private boolean eligible(Player player) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return false;
        InventoryType top = player.getOpenInventory().getType();
        return (top == InventoryType.CRAFTING || top == InventoryType.CREATIVE) && admitted.test(player);
    }

    private void update(View view) {
        Player player = view.player;
        boolean pressed = view.pressed.getAndSet(false);
        if (!pressed && tick % 5 != 0 && view.origin == null) return;
        Location here = player.getLocation();
        if (!eligible(player) || !view.region.contains(here)) {
            hide(player);
            return;
        }
        if (view.origin == null) {
            // Re-evaluate cooldown and overlap order at the actual key press.
            if (select(player, here, config.worlds().get(view.region.world())) != view.region) {
                hide(player);
                return;
            }
            if (!pressed) return;
            view.origin = here;
            view.startTick = tick;
        }
        if (moved(view, here)) {
            interrupt(view);
            return;
        }
        long elapsed = tick - view.startTick;
        int percent = (int) Math.min(100L, elapsed * 100L / view.region.duration());
        if (percent != view.percent && (percent == 0 || percent == 100 || elapsed % 2 == 0)) {
            if (!sync(view, percent)) return;
        }
        if (elapsed >= view.region.duration()) finish(view);
    }

    private void finish(View view) {
        Player player = view.player;
        hide(player); // Consume the session before opening inventories or invoking other modules.
        var region = view.region;
        var outcome = region.draw(ThreadLocalRandom.current());
        try {
            if (!discoveries.resolve(player, region, outcome)) return;
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "探索结算异常 " + region.id() + "/" + outcome.kind(), failure);
            player.sendMessage(messages.get("unavailable", region.name()));
            // A downstream provider may already have applied side effects.
        }
        if (region.cooldownSeconds() > 0) {
            player.getPersistentDataContainer().set(region.cooldownKey(), PersistentDataType.LONG,
                System.currentTimeMillis() + region.cooldownSeconds() * 1000L);
        }
    }

    private boolean sync(View view, int percent) {
        if (bridge.sendPacket(view.player, ui, "sync", Map.of("name", view.region.name(), "percent", percent))) {
            view.percent = percent;
            return true;
        }
        hide(view.player);
        return false;
    }

    private boolean moved(View view, Location to) {
        return !view.region.contains(to) || !view.origin.getWorld().equals(to.getWorld())
            || view.origin.distanceSquared(to) > config.toleranceSquared();
    }

    private void interrupt(View view) {
        hide(view.player);
        view.player.sendMessage(messages.get("moved"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        View view = views.get(event.getPlayer().getUniqueId());
        if (view != null && view.origin != null && event.getTo() != null && moved(view, event.getTo())) interrupt(view);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        hide(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        hide(event.getEntity());
    }

    @EventHandler
    public void onRestriction(AuthRestrictionEvent event) {
        View view = views.get(event.previous().playerId());
        if (view != null) hide(view.player);
    }

    void hide(Player player) {
        if (views.remove(player.getUniqueId()) != null) bridge.closeUi(player, ui);
    }

    void shutdown() {
        for (View view : List.copyOf(views.values())) hide(view.player);
    }
}
