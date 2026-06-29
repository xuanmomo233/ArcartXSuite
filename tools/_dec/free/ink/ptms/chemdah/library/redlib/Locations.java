/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.event.world.WorldLoadEvent
 *  org.bukkit.util.Vector
 */
package ink.ptms.chemdah.library.redlib;

import ink.ptms.chemdah.library.redlib.EventListener;
import ink.ptms.chemdah.library.redlib.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.util.Vector;

public class Locations {
    public static final BlockFace[] PRIMARY_BLOCK_FACES = new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    private static final Map<String, List<Consumer<World>>> waiting = new HashMap<String, List<Consumer<World>>>();
    private static boolean initialized = false;

    public static boolean isHazard(Material type) {
        if (type.toString().contains("LAVA") || type.toString().contains("WATER")) {
            return true;
        }
        if (type.toString().contains("PORTAL") && !type.toString().endsWith("PORTAL_FRAME")) {
            return true;
        }
        return type.toString().equals("MAGMA_BLOCK") || type.toString().equals("CAMPFIRE");
    }

    public static boolean isSafe(Location loc) {
        Block under = loc.clone().subtract(0.0, 1.0, 0.0).getBlock();
        if (under.getType().isSolid()) {
            Block middle = loc.getBlock();
            Block above = loc.clone().add(0.0, 1.0, 0.0).getBlock();
            if (!Locations.isHazard(middle.getType()) && !Locations.isHazard(above.getType())) {
                return !middle.getType().isSolid() && !above.getType().isSolid() && !middle.isLiquid() && !above.isLiquid();
            }
        }
        return false;
    }

    public static Location getNearestSafeLocation(Location loc, int maxDistance, Predicate<Location> filter) {
        Vector direction = loc.getDirection();
        if (Locations.isSafe(loc = loc.getBlock().getLocation().add(0.5, 0.1, 0.5)) && filter.test(loc)) {
            loc.setDirection(direction);
            return loc;
        }
        Location nearest = null;
        double dist = 0.0;
        int y = 0;
        while (Math.abs(y) <= maxDistance) {
            int x = 0;
            while (Math.abs(x) <= maxDistance) {
                int z = 0;
                while (Math.abs(z) <= maxDistance) {
                    Location check2 = loc.clone().add((double)x, (double)y, (double)z);
                    if (Locations.isSafe(check2) && filter.test(check2)) {
                        check2.setDirection(direction);
                        double distance = check2.distanceSquared(loc);
                        if (nearest == null || distance < dist) {
                            nearest = check2;
                            dist = distance;
                            if (dist <= 1.0) {
                                return nearest;
                            }
                        }
                    }
                    z = z == 0 ? 1 : -z - Math.min(Integer.signum(z), 0);
                }
                x = x == 0 ? 1 : -x - Math.min(Integer.signum(x), 0);
            }
            y = y == 0 ? 1 : -y - Math.min(Integer.signum(y), 0);
        }
        return nearest;
    }

    public static Location getNearestSafeLocation(Location loc, int maxDistance) {
        return Locations.getNearestSafeLocation(loc, maxDistance, l -> true);
    }

    public static Vector getDirection(BlockFace face) {
        return new Vector(face.getModX(), face.getModY(), face.getModZ());
    }

    public static String toString(Location loc, String separator) {
        return Objects.requireNonNull(loc.getWorld()).getName() + separator + loc.getX() + separator + loc.getY() + separator + loc.getZ();
    }

    public static Location fromString(String string, String separator) {
        String[] split = string.split(Pattern.quote(separator));
        World world = Bukkit.getWorld((String)split[0]);
        double x = Double.parseDouble(split[1]);
        double y = Double.parseDouble(split[2]);
        double z = Double.parseDouble(split[3]);
        Location location = new Location(world, x, y, z);
        if (world == null) {
            Locations.waitForWorld(split[0], arg_0 -> ((Location)location).setWorld(arg_0));
        }
        return location;
    }

    public static String toString(Block block, String separator) {
        return block.getWorld().getName() + separator + block.getX() + separator + block.getY() + separator + block.getZ();
    }

    public static String toString(Block block) {
        return Locations.toString(block, " ");
    }

    public static void fromStringLater(String string, String separator, Consumer<Location> callback) {
        String[] split = string.split(Pattern.quote(separator));
        World world = Bukkit.getWorld((String)split[0]);
        double x = Double.parseDouble(split[1]);
        double y = Double.parseDouble(split[2]);
        double z = Double.parseDouble(split[3]);
        if (world != null) {
            callback.accept(new Location(world, x, y, z));
            return;
        }
        new EventListener<WorldLoadEvent>(WorldLoadEvent.class, (l, e) -> {
            if (e.getWorld().getName().equals(split[0])) {
                World w = Bukkit.getWorld((String)split[0]);
                callback.accept(new Location(w, x, y, z));
                l.unregister();
            }
        });
    }

    public static Location center(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    public static Location toBlockLocation(Location loc) {
        loc.setX((double)loc.getBlockX());
        loc.setY((double)loc.getBlockY());
        loc.setZ((double)loc.getBlockZ());
        return loc;
    }

    public static Location center(Location loc) {
        return loc.add(0.5, 0.5, 0.5);
    }

    public static void fromStringLater(String string, Consumer<Location> callback) {
        Locations.fromStringLater(string, " ", callback);
    }

    public static String toString(Location loc) {
        return Locations.toString(loc, " ");
    }

    public static Location fromString(String string) {
        return Locations.fromString(string, " ");
    }

    private static void initializeListener() {
        if (initialized) {
            return;
        }
        initialized = true;
        new EventListener<WorldLoadEvent>(WorldLoadEvent.class, e -> {
            List<Consumer<World>> list2 = waiting.remove(e.getWorld().getName());
            if (list2 == null) {
                return;
            }
            list2.forEach(c -> c.accept(e.getWorld()));
        });
    }

    public static void waitForWorld(String worldname, Consumer<World> callback) {
        World world = Bukkit.getWorld((String)worldname);
        if (world != null) {
            callback.accept(world);
            return;
        }
        waiting.putIfAbsent(worldname, new ArrayList());
        List<Consumer<World>> list2 = waiting.get(worldname);
        list2.add(callback);
        Locations.initializeListener();
    }

    public static int[] getChunkCoordinates(Location loc) {
        return new int[]{loc.getBlockX() >> 4, loc.getBlockZ() >> 4};
    }

    public static List<Location> directPathfind(Block start, Block end, int max2, Predicate<Block> filter) {
        ArrayList<Location> path = new ArrayList<Location>(Locations.pathfind(start, end, max2, filter));
        int i = 0;
        while (i + 2 < path.size()) {
            Location second;
            Location first = (Location)path.get(i);
            if (Path.getPath(first, second = (Location)path.get(i + 2), 0.25).stream().map(Location::getBlock).allMatch(filter)) {
                path.remove(i + 1);
                i -= 2;
            }
            i += 2;
        }
        return path;
    }

    public static List<Location> directPathfind(Block start, Block end, int max2) {
        return Locations.directPathfind(start, end, max2, b -> !b.getType().isSolid());
    }

    public static Deque<Location> pathfind(Block start, Block end, int max2) {
        return Locations.pathfind(start, end, max2, b -> !b.getType().isSolid());
    }

    public static Deque<Location> pathfind(Block start, Block end, int max2, Predicate<Block> filter) {
        if (!start.getWorld().equals((Object)end.getWorld())) {
            throw new IllegalArgumentException("Start and end must be in the same world");
        }
        HashSet<Block> nodes = new HashSet<Block>();
        PriorityQueue<Node> queue = new PriorityQueue<Node>(Comparator.comparingInt(n -> n.score));
        HashSet<Block> exclude = new HashSet<Block>();
        Node node = new Node(start, 0);
        node.score = Locations.score(node, start, end);
        nodes.add(node.block);
        queue.add(node);
        Node least = node;
        int leastDist = Locations.distance(least.block, end);
        for (int iter = 0; iter < max2; ++iter) {
            node = queue.poll();
            if (node == null) {
                return Locations.tracePath(least);
            }
            nodes.remove(node.block);
            int dist = Locations.distance(node.block, end);
            if (dist == 0 || dist == 1 && !filter.test(end)) {
                return Locations.tracePath(node);
            }
            if (dist < leastDist) {
                leastDist = dist;
                least = node;
            }
            exclude.add(node.block);
            Locations.getAdjacent(node, start, end, n -> {
                if (exclude.contains(n.block) || !filter.test(n.block)) {
                    exclude.add(n.block);
                    return;
                }
                if (nodes.add(n.block)) {
                    queue.add((Node)n);
                }
            });
        }
        return Locations.tracePath(least);
    }

    private static Deque<Location> tracePath(Node node) {
        ArrayDeque<Location> path = new ArrayDeque<Location>();
        while (node != null) {
            path.addFirst(node.block.getLocation().add(0.5, 0.5, 0.5));
            node = node.parent;
        }
        return path;
    }

    private static void getAdjacent(Node block, Block start, Block end, Consumer<Node> lambda) {
        lambda.accept(Locations.getRelative(block, start, end, 1, 0, 0));
        lambda.accept(Locations.getRelative(block, start, end, -1, 0, 0));
        lambda.accept(Locations.getRelative(block, start, end, 0, 1, 0));
        lambda.accept(Locations.getRelative(block, start, end, 0, -1, 0));
        lambda.accept(Locations.getRelative(block, start, end, 0, 0, 1));
        lambda.accept(Locations.getRelative(block, start, end, 0, 0, -1));
    }

    private static Node getRelative(Node block, Block start, Block end, int x, int y, int z) {
        Block b = block.block.getRelative(x, y, z);
        int score = Locations.score(block, start, end);
        Node node = new Node(b, score);
        node.parent = block;
        return node;
    }

    private static int score(Node node, Block start, Block end) {
        return Locations.distance(node.block, start) + Locations.distance(node.block, end) * 2;
    }

    private static int distance(Block first, Block second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ());
    }

    private static class Node {
        public Block block;
        public int score;
        public Node parent;

        public Node(Block block, int score) {
            this.block = block;
            this.score = score;
        }
    }
}

