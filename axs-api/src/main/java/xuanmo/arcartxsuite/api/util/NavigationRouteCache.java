package xuanmo.arcartxsuite.api.util;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Location;
import org.bukkit.World;

/** One raw route per active marker and search budget. Terrain reads run on the renderer's server thread. */
final class NavigationRouteCache {
    // Replan periodically even if the old route is still safe, so newly opened shortcuts can be used.
    private static final int REPLAN_TICKS = 40;
    private final ConcurrentMap<Key, Entry> routes = new ConcurrentHashMap<>();

    List<Location> route(UUID playerId, String markerKey, NavigationPathfinder pathfinder,
                         Location start, Location goal) {
        Key key = new Key(playerId, markerKey, pathfinder.maxIterations());
        long now = start.getWorld().getGameTime();
        Entry entry = routes.get(key);
        if (entry != null && Objects.equals(entry.checkedStart.getWorld(), start.getWorld()) && samePosition(entry.goal, goal)) {
            boolean sameStart = samePosition(entry.checkedStart, start);
            if (entry.checkedAt == now && sameStart) return entry.simplified;
            if (now >= entry.checkedAt && now - entry.plannedAt < REPLAN_TICKS && entry.route.complete()) {
                int first = NavigationPathfinder.reusableSuffix(entry.route, start, goal, entry.first);
                if (first >= 0) {
                    if (first != entry.first) {
                        var points = entry.route.points();
                        entry.simplified = NavigationPathfinder.simplifyPath(points.subList(first, points.size()));
                        entry.first = first;
                    }
                    entry.checkedAt = now;
                    if (!sameStart) entry.checkedStart = start.clone();
                    return entry.simplified;
                }
            }
        }

        NavigationPathfinder.Route route = pathfinder.findRoute(start, goal);
        List<Location> points = route.points().isEmpty()
            ? NavigationPathfinder.shortDirectionalPath(start, goal)
            : route.points();
        List<Location> simplified = NavigationPathfinder.simplifyPath(points);
        if (entry != null && entry.simplified.equals(simplified)) simplified = entry.simplified;
        routes.put(key, new Entry(start, goal, route, simplified, now));
        return simplified;
    }

    void remove(UUID playerId, String markerKey, NavigationPathfinder pathfinder) {
        routes.remove(new Key(playerId, markerKey, pathfinder.maxIterations()));
    }

    private static boolean samePosition(Location a, Location b) {
        return samePosition(a, b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    static boolean samePosition(Location location, World world, double x, double y, double z) {
        return Objects.equals(location.getWorld(), world) && location.getX() == x
            && location.getY() == y && location.getZ() == z;
    }

    private record Key(UUID playerId, String markerKey, int iterations) {}

    private static final class Entry {
        Location checkedStart;
        final Location goal;
        final NavigationPathfinder.Route route;
        List<Location> simplified;
        final long plannedAt;
        long checkedAt;
        int first;

        Entry(Location start, Location goal, NavigationPathfinder.Route route, List<Location> simplified, long tick) {
            this.checkedStart = start.clone();
            this.goal = goal.clone();
            this.route = route;
            this.simplified = simplified;
            this.plannedAt = tick;
            this.checkedAt = tick;
        }
    }
}
