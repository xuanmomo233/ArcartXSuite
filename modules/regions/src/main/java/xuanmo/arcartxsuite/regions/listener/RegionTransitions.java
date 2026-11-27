package xuanmo.arcartxsuite.regions.listener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.RegionProtectionQueryable;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.model.Region;
import xuanmo.arcartxsuite.regions.model.RegionFlag;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.service.RegionManager.LocationRules;
import xuanmo.arcartxsuite.regions.service.RegionTimings.Phase;

/** 移动和传送使用同一保护判定，只有未取消的边界穿越才发布进出事件。 */
public final class RegionTransitions implements Listener {

    private final RegionManager manager;
    private final Plugin plugin;
    private final RegionsConfiguration config;
    private final Supplier<EventBusCapability> eventBus;
    private Map<UUID, Pending> pending = new LinkedHashMap<>();
    private BukkitTask drainTask;
    private boolean stopped;
    private final Map<PlayerMoveEvent, Endpoints> guarded = new IdentityHashMap<>();

    public RegionTransitions(Plugin plugin, RegionManager manager, RegionsConfiguration config, Supplier<EventBusCapability> eventBus) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.eventBus = eventBus;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardWalk(PlayerMoveEvent event) {
        if (!(event instanceof PlayerTeleportEvent)) measureGuard(event, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardTeleport(PlayerTeleportEvent event) {
        if (!(event instanceof PlayerPortalEvent)) measureGuard(event, event.getCause());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardPortal(PlayerPortalEvent event) {
        measureGuard(event, event.getCause());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void reportWalk(PlayerMoveEvent event) {
        if (!(event instanceof PlayerTeleportEvent)) report(event, "walk");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void reportTeleport(PlayerTeleportEvent event) {
        if (!(event instanceof PlayerPortalEvent)) report(event, event.getCause().name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void reportPortal(PlayerPortalEvent event) {
        report(event, event.getCause().name().toLowerCase(Locale.ROOT));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    public void stop() {
        stopped = true;
        if (drainTask != null) drainTask.cancel();
        drainTask = null;
        pending.clear();
        guarded.clear();
    }

    private void measureGuard(PlayerMoveEvent event, PlayerTeleportEvent.TeleportCause cause) {
        long start = manager.timings().begin();
        try {
            guard(event, cause);
        } finally {
            manager.timings().finish(Phase.GUARD, start);
        }
    }

    private void guard(PlayerMoveEvent event, PlayerTeleportEvent.TeleportCause cause) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getWorld() == null || from.getWorld() == null) return;
        Player player = event.getPlayer();
        RegionFlag travelFlag = cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL ? RegionFlag.ENDERPEARL
            : cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT ? RegionFlag.CHORUS_FRUIT : null;
        boolean moved = changedBlock(from, to);
        if (cause == null && !moved) return;
        Endpoints endpoints = new Endpoints(from, to);
        LocationRules origin = endpoints.origin;
        LocationRules destination = endpoints.destination;
        if (cause != null && travelDenied(origin, destination, travelFlag, player)
            || moved && (crossesDeniedBoundary(destination, origin, RegionFlag.ENTRY, player)
                || crossesDeniedBoundary(origin, destination, RegionFlag.EXIT, player))) {
            event.setCancelled(true);
            player.sendMessage(color(config.messages().prefix() + config.messages().noPermissionRegion()));
        } else if (moved) {
            guarded.put(event, endpoints);
        }
    }

    private static boolean travelDenied(LocationRules from, LocationRules to, RegionFlag specific, Player player) {
        return from.travel(specific, player).state() == RegionFlag.State.DENY
            || from != to && to.travel(specific, player).state() == RegionFlag.State.DENY;
    }

    private static boolean crossesDeniedBoundary(LocationRules restricted, LocationRules other, RegionFlag flag, Player player) {
        RegionManager.Decision decision = restricted.decide(flag, player);
        if (decision.state() != RegionFlag.State.DENY) return false;
        RegionManager.Decision outside = other.decide(flag, player);
        return outside.state() != RegionFlag.State.DENY || !decision.matched().key().equals(outside.matched().key());
    }

    private void report(PlayerMoveEvent event, String cause) {
        long start = manager.timings().begin();
        try {
            reportMovement(event, cause);
        } finally {
            manager.timings().finish(Phase.TRANSITION, start);
        }
    }

    private void reportMovement(PlayerMoveEvent event, String cause) {
        Endpoints endpoints = guarded.remove(event);
        Location from = event.getFrom();
        Location to = event.getTo();
        if (event.isCancelled() || !changedBlock(from, to)) return;
        if (endpoints == null || !endpoints.matches(from, to)) endpoints = new Endpoints(from, to);
        Player player = event.getPlayer();
        var batch = pending.computeIfAbsent(player.getUniqueId(), ignored -> new Pending(player, new ArrayList<>()));
        batch.moves().add(new Movement(event, endpoints, cause));
        if (drainTask == null) {
            drainTask = plugin.getServer().getScheduler().runTask(plugin, this::drainPending);
        }
    }

    private void drainPending() {
        var drained = pending;
        pending = new LinkedHashMap<>();
        drainTask = null;
        for (Pending batch : drained.values()) {
            if (stopped) return;
            if (batch.player().isOnline()) drain(batch.player(), batch.moves());
        }
    }

    private void drain(Player player, List<Movement> moves) {
        Location actual = player.getLocation();
        var accepted = new ArrayDeque<Movement>();
        // 从实际终点回溯，晚取消或未真正执行的移动会被跳过。
        for (int i = moves.size() - 1; i >= 0; i--) {
            Movement movement = moves.get(i);
            Location target = movement.event.getTo();
            if (!movement.event.isCancelled() && sameBlock(target, actual)) {
                accepted.addFirst(movement);
                actual = movement.endpoints.from;
            }
        }
        for (Movement movement : accepted) {
            if (stopped) return;
            long start = manager.timings().begin();
            try {
                Endpoints saved = movement.endpoints;
                Location to = movement.event.getTo();
                if (changedBlock(saved.from, to)) {
                    Endpoints at = saved.matches(saved.from, to) ? saved : new Endpoints(saved.from, to);
                    if (player.isFlying()) {
                        RegionFlight.enforce(player, at.destination.decide(RegionFlag.FLY, player), config, false);
                    }
                    publishCrossing(player, at.origin, at.destination, movement.cause);
                }
            } finally {
                manager.timings().finish(Phase.CROSSING, start);
            }
        }
    }

    private void publishCrossing(Player player, LocationRules origin, LocationRules destination, String cause) {
        List<Region> previous = origin.regions();
        List<Region> current = destination.regions();
        if (sameRegions(previous, current)) return;
        Set<String> before = keys(previous);
        Set<String> after = keys(current);
        Region left = previous.isEmpty() ? null : previous.get(0);
        Region entered = current.isEmpty() ? null : current.get(0);
        if (left != null && !after.contains(left.key())) {
            notifyPlayer(player, origin, left, RegionFlag.FAREWELL, config.messages().regionLeave());
        }
        if (entered != null && !before.contains(entered.key())) {
            notifyPlayer(player, destination, entered, RegionFlag.GREETING, config.messages().regionEnter());
        }
        for (Region region : previous) {
            if (!after.contains(region.key())) publish(player, region, "leave", cause, RegionFlag.NOTIFY_EXIT);
        }
        for (Region region : current) {
            if (!before.contains(region.key())) publish(player, region, "enter", cause, RegionFlag.NOTIFY_ENTER);
        }
    }

    private static boolean sameRegions(List<Region> first, List<Region> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            Region left = first.get(i);
            Region right = second.get(i);
            if (left != right && !left.key().equals(right.key())) return false;
        }
        return true;
    }

    private static Set<String> keys(List<Region> regions) {
        Set<String> keys = new HashSet<>();
        for (Region region : regions) keys.add(region.key());
        return keys;
    }

    private static boolean changedBlock(Location from, Location to) {
        return valid(from) && valid(to) && !sameBlock(from, to);
    }

    private static boolean valid(Location location) {
        return location != null && location.getWorld() != null;
    }

    private static boolean sameBlock(Location first, Location second) {
        return valid(first) && valid(second) && Objects.equals(first.getWorld(), second.getWorld())
            && first.getBlockX() == second.getBlockX() && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }

    private void publish(Player player, Region region, String type, String cause, RegionFlag notify) {
        EventBusCapability bus = eventBus.get();
        if (bus != null) {
            long start = manager.timings().begin();
            try {
                bus.publish(RegionProtectionQueryable.REGION_CHANGE_TOPIC, player, Map.of(
                    "region_id", region.id(), "world", region.world(), "region_key", region.key(),
                    "type", type, "cause", cause, "region_uuid", region.uid().toString()));
            } finally {
                manager.timings().finish(Phase.EVENT_BUS, start);
            }
        }
        if (manager.regionRule(region, notify).state() != RegionFlag.State.ALLOW) return;
        String message = (type.equals("enter") ? config.messages().regionEnter() : config.messages().regionLeave())
            .replace("{region}", region.id());
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (!recipient.getUniqueId().equals(player.getUniqueId()) && manager.hasAccess(region, recipient)) {
                recipient.sendMessage(color(config.messages().prefix() + player.getName() + " " + message));
            }
        }
    }

    private void notifyPlayer(Player player, LocationRules at, Region region, RegionFlag flag, String fallback) {
        RegionManager.Decision rule = at.decide(flag, null);
        if (rule.state() == RegionFlag.State.DENY) return;
        String message = rule.source() != null ? rule.source().getFlagData(flag) : null;
        if (message == null || message.isBlank()) {
            if (!config.notifications().showActionbar()) return;
            message = fallback.replace("{region}", region.id());
        }
        message = color(message);
        switch (config.notifications().displayMode().toLowerCase(Locale.ROOT)) {
            case "title" -> player.sendTitle("", message, 5, 40, 10);
            case "chat" -> player.sendMessage(message);
            default -> player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
        }
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private final class Endpoints {
        private final long revision = manager.revision();
        private final Location from;
        private final Location to;
        private final LocationRules origin;
        private final LocationRules destination;

        private Endpoints(Location from, Location to) {
            this.from = from.clone();
            this.to = to.clone();
            origin = manager.rulesAt(from);
            destination = sameBlock(from, to) ? origin : manager.rulesAt(to);
        }

        private boolean matches(Location from, Location to) {
            return revision == manager.revision() && sameBlock(this.from, from) && sameBlock(this.to, to);
        }
    }

    private record Movement(PlayerMoveEvent event, Endpoints endpoints, String cause) {}

    private record Pending(Player player, List<Movement> moves) {}
}
