package xuanmo.arcartxsuite.api.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.util.BoundingBox;

/**
 * A* 方块级寻路服务。
 * <p>
 * 特性：
 * - 8 方向水平移动 + 上下台阶
 * - 自动回避不可通行方块（固体、液体、危险方块）
 * - 可配置最大迭代次数
 * - 路径简化（移除共线点）与等间距采样
 * - 随机地面/空中位置生成
 * - 路径平滑（线性插值）
 *
 * @since 1.5.0
 */
public final class NavigationPathfinder {

    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[] HEIGHT_CHANGES = {0, 1, -1, -2};

    private static final double STRAIGHT_COST = 1.0;
    private static final double DIAGONAL_COST = 1.414;
    private static final double SWIM_PENALTY = 1.5;

    private final int maxIterations;

    /**
     * 创建寻路器。
     *
     * @param maxIterations 最大迭代次数（防止卡顿），推荐 2000~5000
     */
    public NavigationPathfinder(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    /**
     * 计算从 start 到 goal 的路径。
     *
     * @param start 起点
     * @param goal  终点
     * @return 可通行路径。预算内无法到达时可返回向目标推进的部分路径。
     */
    public List<Location> findPath(Location start, Location goal) {
        return findRoute(start, goal).points();
    }

    int maxIterations() {
        return maxIterations;
    }

    Route findRoute(Location start, Location goal) {
        if (start == null || goal == null) {
            return Route.EMPTY;
        }
        World world = start.getWorld();
        if (world == null || !world.equals(goal.getWorld())) {
            return Route.EMPTY;
        }

        Grid grid = new Grid(world);
        int startX = start.getBlockX();
        int startZ = start.getBlockZ();
        Integer startY = grid.endpointY(start, false);
        if (startY == null) return Route.EMPTY;
        int goalX = goal.getBlockX();
        int goalZ = goal.getBlockZ();
        Integer resolvedGoalY = grid.endpointY(goal, true);
        int goalY = resolvedGoalY == null ? goal.getBlockY() : resolvedGoalY;
        Node startNode = new Node(startX, startY, startZ, 0,
            distance(startX, startY, startZ, goalX, goalY, goalZ), null);
        Map<Long, Node> best = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score).thenComparingDouble(Node::remaining));

        open.add(startNode);
        best.put(startNode.key(), startNode);

        int iterations = 0;
        Node closest = startNode;
        while (!open.isEmpty() && iterations < maxIterations) {
            Node current = open.poll();
            if (best.get(current.key()) != current) continue;
            iterations++;

            if (resolvedGoalY != null && current.x == goalX && current.y == goalY && current.z == goalZ) {
                return new Route(reconstructPath(grid, current), true);
            }
            if (current.remaining < closest.remaining) closest = current;

            for (int[] dir : DIRECTIONS) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];
                boolean diagonal = dir[0] != 0 && dir[1] != 0;
                for (int dy : HEIGHT_CHANGES) {
                    if (diagonal && dy != 0) break;
                    int candidateY = current.y + dy;
                    if (!grid.canMove(current.x, current.y, current.z, nx, candidateY, nz)) continue;
                    double cost = diagonal ? DIAGONAL_COST : STRAIGHT_COST;
                    if (dy == 1) cost += 1.0;
                    else if (dy == -1) cost += 0.3;
                    else if (dy == -2) cost += 0.8;
                    boolean swimming = grid.isSwimming(nx, candidateY, nz);
                    if (swimming) cost += SWIM_PENALTY;
                    offer(open, best, current, nx, candidateY, nz, cost, goalX, goalY, goalZ);
                    // A dry landing has solid support. Lower floors cannot be entered
                    // through that support, and only swimming could open a level above it.
                    if (!swimming && (dy != 0 || !grid.isSwimming(nx, candidateY + 1, nz))) break;
                }
            }
            if (grid.isSwimming(current.x, current.y, current.z)) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    int ny = current.y + dy;
                    if (grid.isWalkable(current.x, ny, current.z)) {
                        offer(open, best, current, current.x, ny, current.z,
                            STRAIGHT_COST + SWIM_PENALTY, goalX, goalY, goalZ);
                    }
                }
            }
        }
        return closest == startNode ? Route.EMPTY : new Route(reconstructPath(grid, closest), false);
    }

    record Route(List<Location> points, boolean complete) {
        private static final Route EMPTY = new Route(List.of(), false);
    }

    /** Returns the first still-usable point at or after the last confirmed position. */
    static int reusableSuffix(Route route, Location start, Location goal, int from) {
        Grid grid = new Grid(start.getWorld());
        List<Location> points = route.points();
        int x = start.getBlockX(), z = start.getBlockZ();
        Integer startY = grid.endpointY(start, false);
        if (startY == null) return -1;
        int firstIndex = from;
        while (firstIndex < points.size()) {
            Location candidate = points.get(firstIndex);
            if (candidate.getBlockX() == x && candidate.getBlockZ() == z && stanceY(candidate) == startY) break;
            firstIndex++;
        }
        if (firstIndex == points.size()) return -1;
        Integer goalY = grid.endpointY(goal, true);
        // Reconstructed stances are on whole or half blocks, with the upper integer used by A*.
        if (goalY == null || goalY != stanceY(points.get(points.size() - 1))) return -1;
        int y = startY;
        if (grid.height(x, y, z) != points.get(firstIndex).getY()) return -1;
        for (int i = firstIndex + 1; i < points.size(); i++) {
            Location next = points.get(i);
            int nx = next.getBlockX(), ny = stanceY(next), nz = next.getBlockZ();
            if (x == nx && z == nz) {
                if (!grid.isSwimming(x, y, z) || !grid.isWalkable(nx, ny, nz)) return -1;
            } else if (!grid.canMove(x, y, z, nx, ny, nz)) {
                return -1;
            }
            if (grid.height(nx, ny, nz) != next.getY()) return -1;
            x = nx;
            y = ny;
            z = nz;
        }
        return firstIndex;
    }

    private static int stanceY(Location location) {
        return (int) Math.ceil(location.getY());
    }

    /**
     * 沿路径按指定间距采样点。
     *
     * @param path      路径点列表
     * @param interval  采样间距（方块）
     * @param maxPoints 最大采样点数
     * @return 采样后的路径点列表
     */
    public static List<Location> samplePath(List<Location> path, double interval, int maxPoints) {
        return samplePath(path, interval, maxPoints, false);
    }

    /** Returns independent marker positions facing the segment they lie on. */
    static List<Location> sampleOrientedPath(List<Location> path, double interval, int maxPoints) {
        return samplePath(path, interval, maxPoints, true);
    }

    private static List<Location> samplePath(List<Location> path, double interval, int maxPoints,
                                             boolean orientToPath) {
        if (path.isEmpty()) return List.of();
        List<Location> result = new java.util.ArrayList<>();
        Location first = path.get(0);
        float yaw = first.getYaw();
        if (orientToPath) {
            first = first.clone();
            if (path.size() > 1) yaw = yawAlong(first, path.get(1), yaw);
            first.setYaw(yaw);
        }
        result.add(first);

        double accumulated = 0;
        for (int i = 1; i < path.size() && result.size() < maxPoints; i++) {
            Location prev = path.get(i - 1);
            Location curr = path.get(i);
            double segLen = prev.distance(curr);
            accumulated += segLen;
            if (orientToPath && i > 1) yaw = yawAlong(prev, curr, yaw);

            while (accumulated >= interval && result.size() < maxPoints) {
                accumulated -= interval;
                double ratio = segLen > 0 ? Math.max(0, 1.0 - accumulated / segLen) : 1.0;
                Location point = new Location(
                    curr.getWorld(),
                    prev.getX() + (curr.getX() - prev.getX()) * ratio,
                    prev.getY() + (curr.getY() - prev.getY()) * ratio,
                    prev.getZ() + (curr.getZ() - prev.getZ()) * ratio
                );
                if (orientToPath) {
                    float markerYaw = ratio == 1.0 && i + 1 < path.size()
                        ? yawAlong(curr, path.get(i + 1), yaw) : yaw;
                    point.setYaw(markerYaw);
                }
                result.add(point);
            }
        }
        return result;
    }

    private static float yawAlong(Location from, Location to, float fallback) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return dx == 0.0 && dz == 0.0 ? fallback : (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * 简化路径：移除共线中间点。
     *
     * @param path 原始路径
     * @return 简化后的路径
     */
    public static List<Location> simplifyPath(List<Location> path) {
        if (path.size() <= 2) return path;
        List<Location> simplified = new java.util.ArrayList<>();
        simplified.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            Location prev = path.get(i - 1);
            Location curr = path.get(i);
            Location next = path.get(i + 1);
            double dx1 = curr.getX() - prev.getX();
            double dz1 = curr.getZ() - prev.getZ();
            double dy1 = curr.getY() - prev.getY();
            double dx2 = next.getX() - curr.getX();
            double dz2 = next.getZ() - curr.getZ();
            double dy2 = next.getY() - curr.getY();

            double dot = dx1 * dx2 + dy1 * dy2 + dz1 * dz2;
            if (dot <= 0 || Math.abs(dx1 * dy2 - dy1 * dx2) > 0.01
                || Math.abs(dx1 * dz2 - dz1 * dx2) > 0.01
                || Math.abs(dy1 * dz2 - dz1 * dy2) > 0.01) {
                simplified.add(curr);
            }
        }
        simplified.add(path.get(path.size() - 1));
        return simplified;
    }

    /**
     * 路径平滑：在路径点之间进行线性插值，生成更密集的路径。
     *
     * @param path        原始路径
     * @param granularity 插值间距（方块），越小越平滑
     * @return 平滑后的路径
     */
    public static List<Location> smoothPath(List<Location> path, double granularity) {
        if (path.size() <= 1 || granularity <= 0) return path;
        List<Location> smoothed = new java.util.ArrayList<>();
        smoothed.add(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            Location prev = path.get(i - 1);
            Location curr = path.get(i);
            double dist = prev.distance(curr);
            int steps = Math.max(1, (int) Math.ceil(dist / granularity));
            for (int s = 1; s <= steps; s++) {
                double t = (double) s / steps;
                smoothed.add(new Location(
                    curr.getWorld(),
                    prev.getX() + (curr.getX() - prev.getX()) * t,
                    prev.getY() + (curr.getY() - prev.getY()) * t,
                    prev.getZ() + (curr.getZ() - prev.getZ()) * t
                ));
            }
        }
        return smoothed;
    }

    /**
     * 在指定中心位置附近生成一个随机可站立地面位置。
     *
     * @param center 中心位置
     * @param radius 搜索半径（方块）
     * @param maxAttempts 最大尝试次数
     * @return 随机地面位置，找不到时返回 null
     */
    public static Location randomGroundLocation(Location center, int radius, int maxAttempts) {
        if (center == null || center.getWorld() == null || radius <= 0 || maxAttempts <= 0) {
            return null;
        }
        Grid grid = new Grid(center.getWorld());
        java.util.Random random = new java.util.Random();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            int bx = center.getBlockX() + dx;
            int bz = center.getBlockZ() + dz;
            Integer by = grid.endpointY(new Location(grid.world, bx + 0.5, center.getY(), bz + 0.5), true);
            if (by != null) return grid.center(bx, by, bz);
        }
        return null;
    }

    /**
     * 生成朝向目标的随机位置（在目标前方扇形区域）。
     *
     * @param origin  起始位置
     * @param target  目标位置
     * @param minDist 最小距离
     * @param maxDist 最大距离
     * @param spread  扇形角度（度），180=半圆
     * @return 随机位置，找不到时返回 null
     */
    public static Location randomLocationFacingTarget(Location origin, Location target, double minDist, double maxDist, double spread) {
        if (origin == null || target == null || origin.getWorld() == null) return null;
        if (!origin.getWorld().equals(target.getWorld())) return null;
        Grid grid = new Grid(origin.getWorld());
        java.util.Random random = new java.util.Random();

        double baseAngle = Math.atan2(target.getZ() - origin.getZ(), target.getX() - origin.getX());
        double spreadRad = Math.toRadians(spread);
        double angle = baseAngle + (random.nextDouble() * 2 - 1) * spreadRad / 2;
        double dist = minDist + random.nextDouble() * (maxDist - minDist);

        int bx = (int) Math.floor(origin.getX() + Math.cos(angle) * dist);
        int bz = (int) Math.floor(origin.getZ() + Math.sin(angle) * dist);
        Integer by = grid.endpointY(new Location(grid.world, bx + 0.5, origin.getY(), bz + 0.5), true);
        return by == null ? null : grid.center(bx, by, bz);
    }

    /**
     * 生成远离目标的随机位置（在目标后方扇形区域）。
     *
     * @param origin  起始位置
     * @param target  目标位置
     * @param minDist 最小距离
     * @param maxDist 最大距离
     * @param spread  扇形角度（度）
     * @return 随机位置，找不到时返回 null
     */
    public static Location randomLocationAwayFromTarget(Location origin, Location target, double minDist, double maxDist, double spread) {
        if (origin == null || target == null || origin.getWorld() == null) return null;
        if (!origin.getWorld().equals(target.getWorld())) return null;
        Grid grid = new Grid(origin.getWorld());
        java.util.Random random = new java.util.Random();

        double baseAngle = Math.atan2(origin.getZ() - target.getZ(), origin.getX() - target.getX());
        double spreadRad = Math.toRadians(spread);
        double angle = baseAngle + (random.nextDouble() * 2 - 1) * spreadRad / 2;
        double dist = minDist + random.nextDouble() * (maxDist - minDist);

        int bx = (int) Math.floor(origin.getX() + Math.cos(angle) * dist);
        int bz = (int) Math.floor(origin.getZ() + Math.sin(angle) * dist);
        Integer by = grid.endpointY(new Location(grid.world, bx + 0.5, origin.getY(), bz + 0.5), true);
        return by == null ? null : grid.center(bx, by, bz);
    }

    /**
     * 生成空中随机位置。
     *
     * @param center    中心位置
     * @param radius    水平半径
     * @param minY      最低 Y
     * @param maxY      最高 Y
     * @return 随机空中位置
     */
    public static Location randomAirLocation(Location center, int radius, int minY, int maxY) {
        if (center == null || center.getWorld() == null || radius <= 0) return null;
        org.bukkit.World world = center.getWorld();
        java.util.Random random = new java.util.Random();
        int dx = random.nextInt(radius * 2 + 1) - radius;
        int dz = random.nextInt(radius * 2 + 1) - radius;
        int dy = minY + random.nextInt(Math.max(1, maxY - minY + 1));
        return new Location(world, center.getX() + dx, dy, center.getZ() + dz);
    }

    /**
     * 生成一条短距离方向指引路径（不穿墙，最多 5 格）。
     * 当 A* 完全无法寻路时作为降级方案。
     *
     * @param start 起点
     * @param goal  终点
     * @return 短路径
     */
    public static List<Location> shortDirectionalPath(Location start, Location goal) {
        if (start == null || goal == null || start.getWorld() == null
            || !start.getWorld().equals(goal.getWorld())) return List.of();
        Grid grid = new Grid(start.getWorld());
        Integer y = grid.endpointY(start, false);
        if (y == null) return List.of();
        int previousX = start.getBlockX();
        int previousZ = start.getBlockZ();
        List<Location> path = new ArrayList<>();
        path.add(new Location(grid.world, start.getX(), grid.height(previousX, y, previousZ), start.getZ()));
        double dx = goal.getX() - start.getX();
        double dz = goal.getZ() - start.getZ();
        double length = Math.hypot(dx, dz);
        for (int step = 1; step <= Math.min(5, Math.ceil(length)); step++) {
            double ratio = Math.min(1.0, step / length);
            double x = start.getX() + dx * ratio;
            double z = start.getZ() + dz * ratio;
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            if (bx != previousX || bz != previousZ) {
                if (!grid.canMove(previousX, y, previousZ, bx, y, bz)) break;
                // The directional fallback does not center its samples in a doorway.
                if (grid.hasDoor(previousX, y, previousZ)) break;
                previousX = bx;
                previousZ = bz;
            }
            if (grid.hasDoor(bx, y, bz)) break;
            path.add(new Location(grid.world, x, grid.height(bx, y, bz), z));
        }
        return path;
    }

    // ==================== 内部方法 ====================

    private static void offer(PriorityQueue<Node> open, Map<Long, Node> best, Node parent,
                              int x, int y, int z, double cost, int goalX, int goalY, int goalZ) {
        long key = positionKey(x, y, z);
        double total = parent.cost + cost;
        Node existing = best.get(key);
        if (existing != null && total >= existing.cost) return;
        Node neighbor = new Node(x, y, z, total, distance(x, y, z, goalX, goalY, goalZ), parent);
        best.put(key, neighbor);
        open.add(neighbor);
    }

    private static double distance(int x, int y, int z, int goalX, int goalY, int goalZ) {
        double dx = Math.abs((double) x - goalX);
        double dz = Math.abs((double) z - goalZ);
        double rise = (double) goalY - y;
        // Lower bound of the allowed moves, including the cheaper direction when descending.
        double elevation = rise >= 0 ? rise : -rise * 0.3;
        return dx + dz + (DIAGONAL_COST - 2) * Math.min(dx, dz) + elevation;
    }

    private static long positionKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
    }

    private static List<Location> reconstructPath(Grid grid, Node node) {
        List<Location> path = new ArrayList<>();
        for (Node current = node; current != null; current = current.parent) {
            path.add(grid.center(current.x, current.y, current.z));
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private record Node(int x, int y, int z, double cost, double remaining, Node parent) {
        long key() {
            return positionKey(x, y, z);
        }

        double score() {
            return cost + remaining;
        }
    }

    /** Terrain reads are shared within one search and limited to currently loaded chunks. */
    private static final class Grid {
        private static final byte PASSABLE = 1;
        private static final byte SWIMMING = 2;
        private static final byte SUPPORT = 4;
        private static final byte HALF_SUPPORT = 8;
        private static final int WEST = 16;
        private static final int EAST = 32;
        private static final int NORTH = 64;
        private static final int SOUTH = 128;
        private static final int DOOR_FACES = WEST | EAST | NORTH | SOUTH;
        private static final double CENTER_CLEARANCE = 0.2;
        private final World world;
        private final int minY;
        private final int maxY;
        private final Map<Long, Byte> cells = new HashMap<>();
        private final Map<Long, Boolean> chunks = new HashMap<>();
        private boolean openDoorSeen;

        Grid(World world) {
            this.world = world;
            minY = world.getMinHeight();
            maxY = world.getMaxHeight();
        }

        Integer endpointY(Location location, boolean isGoal) {
            int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
            if (isWalkable(x, y, z)) return y;
            if ((cell(x, y, z) & SUPPORT) != 0 && isWalkable(x, y + 1, z)
                && (isGoal || location.getY() >= height(x, y + 1, z))) return y + 1;
            if (!passable(x, y + 1, z)) return null;
            for (int below = y; below >= Math.max(minY, y - 20) && passable(x, below, z); below--) {
                if (isWalkable(x, below, z)) return below;
            }
            return null;
        }

        boolean canMove(int x, int y, int z, int nx, int ny, int nz) {
            if (!isWalkable(nx, ny, nz)) return false;
            int dy = ny - y;
            if (dy != 0) {
                double difference = height(nx, ny, nz) - height(x, y, z);
                if (difference > 1.0 || difference < -2.0) return false;
            }
            if (x != nx && z != nz) {
                return dy == 0 && isWalkable(nx, y, z) && isWalkable(x, y, nz)
                    && (!openDoorSeen || (!hasDoor(x, y, z) && !hasDoor(nx, y, z)
                        && !hasDoor(x, y, nz) && !hasDoor(nx, y, nz)));
            }
            if (dy > 0 && !passable(x, y + 2, z)) return false;
            // Enter the destination column at launch height before dropping to its floor.
            for (int level = ny + 2; level <= y + 1; level++) {
                if (!passable(nx, level, nz)) return false;
            }
            return !openDoorSeen || doorwayAllowsMove(x, y, z, nx, ny, nz);
        }

        private boolean doorwayAllowsMove(int x, int y, int z, int nx, int ny, int nz) {
            int exit;
            int enter;
            if (nx > x) {
                exit = EAST;
                enter = WEST;
            } else if (nx < x) {
                exit = WEST;
                enter = EAST;
            } else if (nz > z) {
                exit = SOUTH;
                enter = NORTH;
            } else {
                exit = NORTH;
                enter = SOUTH;
            }
            for (int level = Math.min(y, ny); level <= Math.max(y, ny) + 1; level++) {
                if ((cell(x, level, z) & exit) != 0 || (cell(nx, level, nz) & enter) != 0) return false;
            }
            return true;
        }

        boolean hasDoor(int x, int y, int z) {
            return openDoorSeen && ((cell(x, y, z) | cell(x, y + 1, z)) & DOOR_FACES) != 0;
        }

        boolean isWalkable(int x, int y, int z) {
            byte feet = cell(x, y, z);
            return (feet & PASSABLE) != 0 && passable(x, y + 1, z)
                && ((feet & SWIMMING) != 0 || (cell(x, y - 1, z) & SUPPORT) != 0);
        }

        boolean isSwimming(int x, int y, int z) {
            return (cell(x, y, z) & SWIMMING) != 0;
        }

        private boolean passable(int x, int y, int z) {
            return (cell(x, y, z) & PASSABLE) != 0;
        }

        double height(int x, int y, int z) {
            return !isSwimming(x, y, z) && (cell(x, y - 1, z) & HALF_SUPPORT) != 0 ? y - 0.5 : y;
        }

        Location center(int x, int y, int z) {
            return new Location(world, x + 0.5, height(x, y, z), z + 0.5);
        }

        private byte cell(int x, int y, int z) {
            if (y < minY || y >= maxY) return 0;
            long key = positionKey(x, y, z);
            Byte existing = cells.get(key);
            if (existing != null) return existing;
            int chunkX = x >> 4, chunkZ = z >> 4;
            long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            Boolean loaded = chunks.get(chunkKey);
            if (loaded == null) {
                loaded = world.isChunkLoaded(chunkX, chunkZ);
                chunks.put(chunkKey, loaded);
            }
            byte cell = loaded ? classify(world.getBlockAt(x, y, z)) : 0;
            cells.put(key, cell);
            return cell;
        }

        private byte classify(Block block) {
            Material type = block.getType();
            if (type.isAir()) return PASSABLE;
            if (type == Material.WATER || type == Material.BUBBLE_COLUMN) return PASSABLE | SWIMMING;
            if (dangerous(type)) return 0;
            if (type.isOccluding()) return SUPPORT;

            String blockName = type.name();
            if (blockName.endsWith("_DOOR") && block.getBlockData() instanceof Door door && door.isOpen()) {
                return classifyOpenDoor(block);
            }
            if (blockName.endsWith("_CARPET") || blockName.endsWith("_PRESSURE_PLATE") || block.isPassable()) {
                return PASSABLE;
            }
            if (!type.isSolid() || blockName.endsWith("_FENCE") || blockName.endsWith("_FENCE_GATE")
                || blockName.endsWith("_WALL") || blockName.endsWith("_DOOR") || blockName.endsWith("_TRAPDOOR")
                || blockName.endsWith("GLASS_PANE") || type == Material.IRON_BARS) return 0;
            if (blockName.endsWith("_SLAB") && block.getBlockData() instanceof Slab slab
                && slab.getType() == Slab.Type.BOTTOM) return SUPPORT | HALF_SUPPORT;
            return SUPPORT;
        }

        private byte classifyOpenDoor(Block block) {
            // A standing player has 0.2 blocks between the edge of their body and the edge of a centered cell.
            int faces = 0;
            for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                if (shape.getMaxX() <= CENTER_CLEARANCE) faces |= WEST;
                else if (shape.getMinX() >= 1.0 - CENTER_CLEARANCE) faces |= EAST;
                else if (shape.getMaxZ() <= CENTER_CLEARANCE) faces |= NORTH;
                else if (shape.getMinZ() >= 1.0 - CENTER_CLEARANCE) faces |= SOUTH;
                else return 0;
            }
            if (faces != 0) openDoorSeen = true;
            return (byte) (PASSABLE | faces);
        }

        private static boolean dangerous(Material type) {
            return switch (type) {
                case LAVA, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK, SWEET_BERRY_BUSH, WITHER_ROSE,
                    POINTED_DRIPSTONE, FIRE, SOUL_FIRE, POWDER_SNOW, NETHER_PORTAL, END_PORTAL, END_GATEWAY -> true;
                default -> false;
            };
        }
    }
}
