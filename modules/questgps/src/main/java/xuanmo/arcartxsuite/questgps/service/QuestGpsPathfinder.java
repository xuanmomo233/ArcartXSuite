package xuanmo.arcartxsuite.questgps.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 基于 A* 的方块级路径寻找器。
 * <p>
 * 特性:
 * - 支持 8 方向水平移动 + 上下 1 格台阶
 * - 自动回避不可通行方块（固体方块、液体、仙人掌等）
 * - 可配置最大迭代次数防止服务端卡顿
 * - 路径简化：移除共线中间点
 */
public final class QuestGpsPathfinder {

    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final double STRAIGHT_COST = 1.0;
    private static final double DIAGONAL_COST = 1.414;
    private static final double SWIM_PENALTY = 1.5;

    private final int maxIterations;

    public QuestGpsPathfinder(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    /**
     * 计算从 start 到 goal 的路径。
     *
     * @param start 起点
     * @param goal  终点
     * @return 路径点列表（从起点到终点），找不到路径时返回空列表
     */
    public List<Location> findPath(Location start, Location goal) {
        if (start == null || goal == null) {
            return List.of();
        }
        World world = start.getWorld();
        if (world == null || !world.equals(goal.getWorld())) {
            return List.of();
        }

        Node startNode = new Node(start.getBlockX(), groundY(world, start.getBlockX(), start.getBlockY(), start.getBlockZ()), start.getBlockZ());
        Node goalNode = new Node(goal.getBlockX(), groundY(world, goal.getBlockX(), goal.getBlockY(), goal.getBlockZ()), goal.getBlockZ());

        if (startNode.equals(goalNode)) {
            return List.of(center(world, startNode));
        }

        Map<Long, Node> closed = new HashMap<>();
        Map<Long, Node> openMap = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        startNode.g = 0;
        startNode.f = heuristic(startNode, goalNode);
        open.add(startNode);
        openMap.put(startNode.key(), startNode);

        int iterations = 0;
        while (!open.isEmpty() && iterations < maxIterations) {
            iterations++;
            Node current = open.poll();
            openMap.remove(current.key());

            if (current.x == goalNode.x && current.z == goalNode.z) {
                return reconstructPath(world, current);
            }

            closed.put(current.key(), current);

            boolean currentInWater = isSwimming(world, current.x, current.y, current.z);

            for (int[] dir : DIRECTIONS) {
                int nx = current.x + dir[0];
                int nz = current.z + dir[1];
                boolean diagonal = dir[0] != 0 && dir[1] != 0;

                // 尝试同层、上1格、下1格、下2格（安全下落）
                int bestY = -1;
                int bestDy = 0;
                for (int dy : new int[]{0, 1, -1, -2}) {
                    int candidateY = current.y + dy;
                    if (isWalkable(world, nx, candidateY, nz)) {
                        // 上跳1格时，需要头顶上方有空间
                        if (dy == 1 && !isPassable(world.getBlockAt(current.x, current.y + 2, current.z))) {
                            continue;
                        }
                        bestY = candidateY;
                        bestDy = dy;
                        break; // 找到第一个可行的就用
                    }
                }
                if (bestY == -1) continue;

                // 对角线移动时检查两个相邻正交方向是否可通行（防止穿墙）
                if (diagonal) {
                    boolean side1Passable = isWalkable(world, current.x + dir[0], current.y, current.z)
                        || isWalkable(world, current.x + dir[0], current.y - 1, current.z);
                    boolean side2Passable = isWalkable(world, current.x, current.y, current.z + dir[1])
                        || isWalkable(world, current.x, current.y - 1, current.z + dir[1]);
                    if (!side1Passable && !side2Passable) {
                        continue;
                    }
                }

                boolean neighborInWater = isSwimming(world, nx, bestY, nz);

                // 水→陆地转换校验：从水中上岸最多只能爬1格
                if (currentInWater && !neighborInWater) {
                    if (bestY > current.y + 1) continue;
                    // 检查当前水位旁是否有空间让玩家浮出水面
                    if (bestY > current.y && !isPassable(world.getBlockAt(current.x, current.y + 1, current.z))) {
                        continue;
                    }
                }

                Node neighbor = new Node(nx, bestY, nz);
                if (closed.containsKey(neighbor.key())) continue;

                double moveCost = diagonal ? DIAGONAL_COST : STRAIGHT_COST;
                if (bestDy == 1) moveCost += 1.0;   // 爬升成本较高
                else if (bestDy == -1) moveCost += 0.3; // 下1格轻微成本
                else if (bestDy == -2) moveCost += 0.8; // 下2格中等成本
                if (neighborInWater) moveCost += SWIM_PENALTY; // 游泳比走路慢
                double tentativeG = current.g + moveCost;

                tryAddNeighbor(open, openMap, neighbor, tentativeG, goalNode, current);
            }

            // 水中垂直移动（上浮/下潜）
            if (currentInWater) {
                for (int vertDy : new int[]{1, -1}) {
                    int ny = current.y + vertDy;
                    if (isWalkable(world, current.x, ny, current.z)) {
                        Node vNeighbor = new Node(current.x, ny, current.z);
                        if (!closed.containsKey(vNeighbor.key())) {
                            double vCost = STRAIGHT_COST + SWIM_PENALTY;
                            double vTentativeG = current.g + vCost;
                            tryAddNeighbor(open, openMap, vNeighbor, vTentativeG, goalNode, current);
                        }
                    }
                }
            }
        }

        // 找不到完整路径时返回到最接近目标的节点的路径（优先陆地节点）
        if (!closed.isEmpty()) {
            // 先尝试找陆地上最近的节点
            Node closestLand = null;
            Node closestAny = null;
            for (Node n : closed.values()) {
                double dist = heuristic(n, goalNode);
                if (dist >= heuristic(startNode, goalNode)) continue;
                if (closestAny == null || dist < heuristic(closestAny, goalNode)) {
                    closestAny = n;
                }
                if (!isSwimming(world, n.x, n.y, n.z)) {
                    if (closestLand == null || dist < heuristic(closestLand, goalNode)) {
                        closestLand = n;
                    }
                }
            }
            // 优先返回陆地路径，除非水中节点显著更近
            Node best = closestLand;
            if (best == null) {
                best = closestAny;
            } else if (closestAny != null && !isSwimming(world, closestAny.x, closestAny.y, closestAny.z)) {
                // closestAny 也是陆地，已经在 closestLand 考虑中
            } else if (closestAny != null && heuristic(closestAny, goalNode) < heuristic(best, goalNode) * 0.5) {
                // 水中节点距离不到陆地节点的一半时才用水中节点
                best = closestAny;
            }
            if (best != null) {
                return reconstructPath(world, best);
            }
        }
        return List.of();
    }

    /**
     * 沿路径按指定间距采样点。
     */
    public static List<Location> samplePath(List<Location> path, double interval, int maxPoints) {
        if (path.isEmpty()) return List.of();
        List<Location> result = new ArrayList<>();
        result.add(path.get(0));

        double accumulated = 0;
        for (int i = 1; i < path.size() && result.size() < maxPoints; i++) {
            Location prev = path.get(i - 1);
            Location curr = path.get(i);
            double segLen = prev.distance(curr);
            accumulated += segLen;

            while (accumulated >= interval && result.size() < maxPoints) {
                accumulated -= interval;
                // 从 curr 往回 accumulated 距离插值
                double ratio = segLen > 0 ? Math.max(0, 1.0 - accumulated / segLen) : 1.0;
                Location point = new Location(
                    curr.getWorld(),
                    prev.getX() + (curr.getX() - prev.getX()) * ratio,
                    prev.getY() + (curr.getY() - prev.getY()) * ratio,
                    prev.getZ() + (curr.getZ() - prev.getZ()) * ratio
                );
                result.add(point);
            }
        }
        return result;
    }

    /**
     * 简化路径：移除共线中间点。
     */
    public static List<Location> simplifyPath(List<Location> path) {
        if (path.size() <= 2) return path;
        List<Location> simplified = new ArrayList<>();
        simplified.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            Location prev = path.get(i - 1);
            Location curr = path.get(i);
            Location next = path.get(i + 1);
            // 检查是否共线（方向向量相同）
            double dx1 = curr.getX() - prev.getX();
            double dz1 = curr.getZ() - prev.getZ();
            double dy1 = curr.getY() - prev.getY();
            double dx2 = next.getX() - curr.getX();
            double dz2 = next.getZ() - curr.getZ();
            double dy2 = next.getY() - curr.getY();

            if (Math.abs(dx1 * dz2 - dz1 * dx2) > 0.01 || Math.abs(dy1 - dy2) > 0.01) {
                simplified.add(curr);
            }
        }
        simplified.add(path.get(path.size() - 1));
        return simplified;
    }

    private static void tryAddNeighbor(
        PriorityQueue<Node> open, Map<Long, Node> openMap,
        Node neighbor, double tentativeG, Node goalNode, Node parent
    ) {
        Node existing = openMap.get(neighbor.key());
        if (existing != null && tentativeG >= existing.g) return;

        neighbor.g = tentativeG;
        neighbor.f = tentativeG + heuristic(neighbor, goalNode);
        neighbor.parent = parent;

        if (existing != null) {
            open.remove(existing);
        }
        open.add(neighbor);
        openMap.put(neighbor.key(), neighbor);
    }

    // ==================== 内部方法 ====================

    private static boolean isWalkable(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);

        // 脚部和头部必须可通行
        if (!isPassable(feet) || !isPassable(head)) return false;

        // 游泳状态：脚部在水中，不需要脚下有固体
        if (feet.getType() == Material.WATER) return true;

        // 脚下必须是可站立的方块（固体方块、楼梯、台阶、或浅水下有实体）
        if (!isStandable(ground, world, x, y - 1, z)) return false;
        // 脚下不能是有害方块
        if (isDangerous(ground)) return false;

        return true;
    }

    /**
     * 判断位置是否处于游泳状态（脚部在水中）。
     */
    private static boolean isSwimming(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType() == Material.WATER;
    }

    private static boolean isPassable(Block block) {
        Material type = block.getType();
        if (type.isAir()) return true;
        if (type == Material.LAVA) return false;
        // 浅水允许通行（涉水）
        if (type == Material.WATER) return true;
        if (!type.isSolid()) return true;
        // 根据方块实际状态判断是否可通行
        if (isPassableBlock(block)) return true;
        return false;
    }

    private static boolean isSolid(Block block) {
        Material type = block.getType();
        if (type.isAir()) return false;
        if (type == Material.WATER || type == Material.LAVA) return false;
        return type.isSolid();
    }

    /**
     * 判断方块是否可以站在上面（含楼梯/台阶/浅水地面）。
     */
    private static boolean isStandable(Block block, World world, int bx, int by, int bz) {
        Material type = block.getType();
        if (type.isAir()) return false;
        if (type == Material.LAVA) return false;
        // 正常固体方块
        if (type.isSolid()) return true;
        // 浅水：水下一格是否有固体方块（可以涉水走）
        if (type == Material.WATER) {
            Block below = world.getBlockAt(bx, by - 1, bz);
            return below.getType().isSolid() && !isDangerous(below);
        }
        return false;
    }

    /**
     * 判断非全高方块是否可通行（玩家能否穿过此方块位置）。
     * <p>
     * 注意：此方法在异步线程中调用，不能使用 getBlockData()（会触发 ConcurrentModificationException）。
     * 半砖/楼梯视为不可通行——它们是固体障碍物（高度按1格计算）。
     * 玩家通过 dy=+1（跳跃）登上它们，在 isStandable 中处理。
     * 栅栏门/活板门保守处理为不可通行（无法异步检测开关状态）。
     */
    private static boolean isPassableBlock(Block block) {
        String name = block.getType().name();
        // 小型方块：始终可通行（不阻挡移动）
        return name.contains("CARPET")
            || name.contains("PRESSURE_PLATE")
            || name.contains("SIGN")
            || name.contains("BANNER");
    }

    private static boolean isDangerous(Block block) {
        Material type = block.getType();
        return type == Material.LAVA
            || type == Material.CACTUS
            || type == Material.CAMPFIRE
            || type == Material.SOUL_CAMPFIRE
            || type == Material.MAGMA_BLOCK
            || type == Material.SWEET_BERRY_BUSH
            || type == Material.WITHER_ROSE
            || type == Material.POINTED_DRIPSTONE;
    }

    private static int groundY(World world, int x, int startY, int z) {
        // 从 startY 向下搜索可站立的地面
        for (int y = startY; y >= Math.max(world.getMinHeight(), startY - 20); y--) {
            if (isWalkable(world, x, y, z)) {
                return y;
            }
        }
        // 从 startY 向上搜索（加大范围到 20 格）
        for (int y = startY + 1; y <= startY + 20; y++) {
            if (isWalkable(world, x, y, z)) {
                return y;
            }
        }
        return startY;
    }

    private static double heuristic(Node a, Node b) {
        double dx = Math.abs(a.x - b.x);
        double dz = Math.abs(a.z - b.z);
        double dy = Math.abs(a.y - b.y);
        // Octile distance + 垂直距离（权重提高到2.0，因为爬升需要绕路）
        return (dx + dz) + (DIAGONAL_COST - 2) * Math.min(dx, dz) + dy * 2.0;
    }

    private static List<Location> reconstructPath(World world, Node node) {
        List<Location> path = new ArrayList<>();
        Node current = node;
        while (current != null) {
            path.add(center(world, current));
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private static Location center(World world, Node node) {
        return new Location(world, node.x + 0.5, node.y, node.z + 0.5);
    }

    private static final class Node {
        final int x, y, z;
        double g = Double.MAX_VALUE;
        double f = Double.MAX_VALUE;
        Node parent;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        long key() {
            return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node n)) return false;
            return x == n.x && y == n.y && z == n.z;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(key());
        }
    }
}
