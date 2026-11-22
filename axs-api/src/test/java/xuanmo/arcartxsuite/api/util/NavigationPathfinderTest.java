package xuanmo.arcartxsuite.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;

class NavigationPathfinderTest {
    private final NavigationPathfinder pathfinder = new NavigationPathfinder(300);

    @Test
    void findsRouteAcrossNegativeOne() {
        Terrain terrain = new Terrain();
        terrain.floor(-2, -2, 5, -2, 2);

        List<Location> path = pathfinder.findPath(terrain.at(0, -1, 0), terrain.at(3, -1, 0));

        assertEquals(terrain.at(3, -1, 0), last(path));
    }

    @Test
    void reachesTheUpperFloorWhenTheLowerFloorSharesItsColumn() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 2, 0, 1);
        for (int x = 0; x <= 4; x++) {
            terrain.put(x, x, 2, Material.STONE);
        }
        terrain.floor(4, 1, 4, 0, 1);

        List<Location> path = pathfinder.findPath(terrain.at(0, 1, 0), terrain.at(1, 5, 0));

        assertEquals(terrain.at(1, 5, 0), last(path));
    }

    @Test
    void routesAroundAnObstructedDiagonal() {
        Terrain terrain = new Terrain();
        terrain.floor(0, -2, 3, -2, 3);
        terrain.put(1, 1, 0, Material.STONE);
        terrain.put(1, 2, 0, Material.STONE);
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(1, 1, 1);

        assertEquals(List.of(start, terrain.at(0, 1, 1), goal), pathfinder.findPath(start, goal));
    }

    @Test
    void traversesOpenDoorFromBothSidesWithoutCrossingTheCollisionPanel() {
        Terrain terrain = new Terrain();
        terrain.floor(0, 0, 2, 0, 0);
        terrain.put(1, 0, -1, Material.STONE);
        terrain.northPanelDoor(1, 1, 0, true);
        Location west = terrain.at(0, 1, 0);
        Location doorway = terrain.at(1, 1, 0);
        Location east = terrain.at(2, 1, 0);
        Location north = terrain.at(1, 1, -1);

        assertEquals(List.of(west, doorway, east), pathfinder.findPath(west, east));
        assertEquals(List.of(east, doorway, west), pathfinder.findPath(east, west));
        assertNotEquals(north, last(pathfinder.findPath(west, north)));
        assertNotEquals(west, last(pathfinder.findPath(north, west)));
    }

    @Test
    void rejectsADropWhenTheEntryColumnLacksHeadroom() {
        Terrain terrain = new Terrain();
        terrain.put(0, 2, 0, Material.STONE);
        terrain.floor(0, 1, 2, 0, 0);
        Location start = terrain.at(0, 3, 0);
        Location goal = terrain.at(2, 1, 0);
        assertEquals(goal, last(pathfinder.findPath(start, goal)));

        terrain.put(1, 3, 0, Material.STONE);
        terrain.put(1, 4, 0, Material.STONE);

        assertEquals(List.of(), pathfinder.findPath(start, goal));
    }

    @Test
    void directionalFallbackStopsBeforeAGapOrLava() {
        Terrain terrain = new Terrain();
        terrain.put(0, 0, 0, Material.STONE);
        terrain.floor(0, 2, 4, 0, 0);
        terrain.put(3, 1, 0, Material.LAVA);
        Location start = terrain.at(0, 1, 0);
        Location goal = terrain.at(4, 1, 0);
        assertEquals(List.of(start), NavigationPathfinder.shortDirectionalPath(start, goal));

        terrain.put(1, 0, 0, Material.STONE);

        assertEquals(List.of(start, terrain.at(1, 1, 0), terrain.at(2, 1, 0)),
            NavigationPathfinder.shortDirectionalPath(start, goal));
    }

    @Test
    void stopsBeforeUnloadedChunksWithoutReadingTheirBlocks() {
        Terrain terrain = new Terrain((x, z) -> x == 0 && z == 0);
        terrain.floor(0, 10, 20, 5, 10);

        List<Location> path = pathfinder.findPath(terrain.at(14, 1, 8), terrain.at(17, 1, 8));

        assertEquals(0, terrain.unloadedBlockReads);
        assertEquals(terrain.at(15, 1, 8), last(path));
    }

    private static Location last(List<Location> path) {
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    static final class Terrain {
        private record Cell(int x, int y, int z) {}

        private final Map<Cell, Block> cells = new HashMap<>();
        private final Map<Material, Block> blocks = new EnumMap<>(Material.class);
        private final World world;
        private long gameTime;
        private int blockReads;
        private int unloadedBlockReads;

        Terrain() {
            this((x, z) -> true);
        }

        Terrain(BiPredicate<Integer, Integer> loaded) {
            UUID worldId = UUID.randomUUID();
            // A JDK World proxy initializes Bukkit's structure registry without a server.
            world = mock(World.class, call -> switch (call.getMethod().getName()) {
                case "toString", "getName" -> "pathfinder-test";
                case "getUID" -> worldId;
                case "getGameTime" -> gameTime;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "isChunkLoaded" -> loaded.test(call.getArgument(0), call.getArgument(1));
                case "getBlockAt" -> {
                    blockReads++;
                    Cell cell = new Cell(call.getArgument(0), call.getArgument(1), call.getArgument(2));
                    if (!loaded.test(cell.x() >> 4, cell.z() >> 4)) unloadedBlockReads++;
                    yield cells.getOrDefault(cell, blocks.computeIfAbsent(Material.AIR, Terrain::block));
                }
                default -> throw new UnsupportedOperationException(call.getMethod().toString());
            });
        }

        Location at(int x, int y, int z) {
            return new Location(world, x + 0.5, y, z + 0.5);
        }

        int blockReads() {
            return blockReads;
        }

        void advance(long ticks) {
            gameTime += ticks;
        }

        void put(int x, int y, int z, Material material) {
            cells.put(new Cell(x, y, z), blocks.computeIfAbsent(material, Terrain::block));
        }

        void northPanelDoor(int x, int y, int z, boolean open) {
            putDoorHalf(x, y, z, Bisected.Half.BOTTOM, open);
            putDoorHalf(x, y + 1, z, Bisected.Half.TOP, open);
        }

        private void putDoorHalf(int x, int y, int z, Bisected.Half half, boolean open) {
            Door data = proxy(Door.class, (self, method, args) -> switch (method.getName()) {
                case "getMaterial" -> Material.OAK_DOOR;
                case "getHalf" -> half;
                case "isOpen" -> open;
                default -> throw new UnsupportedOperationException(method.toString());
            });
            BoundingBox panel = open ? new BoundingBox(0, 0, 0, 1, 1, 0.1875)
                : new BoundingBox(0.8125, 0, 0, 1, 1, 1);
            VoxelShape shape = new VoxelShape() {
                @Override public Collection<BoundingBox> getBoundingBoxes() { return List.of(panel); }
                @Override public boolean overlaps(BoundingBox box) { return panel.overlaps(box); }
            };
            Block door = proxy(Block.class, (self, method, args) -> switch (method.getName()) {
                case "getType" -> Material.OAK_DOOR;
                case "isPassable" -> false;
                case "getBlockData" -> data;
                case "getCollisionShape" -> shape;
                case "getBoundingBox" -> panel.clone().shift(x, y, z);
                default -> throw new UnsupportedOperationException(method.toString());
            });
            cells.put(new Cell(x, y, z), door);
        }

        void floor(int y, int minX, int maxX, int minZ, int maxZ) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    put(x, y, z, Material.STONE);
                }
            }
        }

        private static Block block(Material material) {
            return proxy(Block.class, (self, method, args) -> switch (method.getName()) {
                case "getType" -> material;
                case "isPassable" -> material.isAir();
                default -> throw new UnsupportedOperationException(method.toString());
            });
        }
    }
}
