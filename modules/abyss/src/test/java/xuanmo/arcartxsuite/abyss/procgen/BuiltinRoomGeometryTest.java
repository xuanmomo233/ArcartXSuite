package xuanmo.arcartxsuite.abyss.procgen;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.template.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Run the real paste/rotate/carve pipeline against the bundled block volumes. */
class BuiltinRoomGeometryTest {
    record Pos(int x, int y, int z) { }
    private static final Path ROOMS = Path.of("src/main/resources/room-sets/example");

    @Test void everyBundledRoomHasTheSameFloorAndFiveBlockHeadroom() throws Exception {
        for (var asset : assets()) {
            assertEquals(7, asset.region().height(), asset.name());
            assertEquals(1, asset.spawn() == null ? 1 : asset.spawn()[1], asset.name());
        }
        assertFalse(Files.readString(Path.of("src/main/resources/abysses/example/scripts.yml")).contains("GLOWSTONE"));
    }

    @Test void allBundledRoomsRemainWalkableAcrossBothAxesAndAllRotations() throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object before = field.get(null);
        Server server = mock(Server.class, withSettings().stubOnly());
        Map<String, BlockData> palette = new HashMap<>();
        when(server.createBlockData(anyString())).thenAnswer(call -> palette.computeIfAbsent(call.getArgument(0), data -> {
            BlockData block = mock(BlockData.class, withSettings().stubOnly());
            when(block.getMaterial()).thenReturn(Material.matchMaterial(data.split("\\[")[0]));
            return block;
        }));
        try {
            field.set(null, server);
            var assets = assets();
            var cfg = ProcGenConfig.load(YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/abysses/procgen/procgen.yml").toFile()), "procgen");
            for (int i = 0; i < assets.size(); i++) for (int ra = 0; ra < 360; ra += 90)
                for (int rb = 0; rb < 360; rb += 90) for (char axis : new char[]{'x', 'z'}) {
                    var a = placed(assets.get(i), ra, -37, -29);
                    var b = placed(assets.get((i + 1) % assets.size()), rb,
                        axis == 'x' ? -7 : -37, axis == 'z' ? 1 : -29);
                    var layout = new LayoutGenerator.Layout();
                    layout.rooms.add(a); layout.rooms.add(b); layout.start = a;
                    layout.edges.add(new LayoutGenerator.DoorEdge(a, b, axis));
                    Map<Pos, Material> blocks = new HashMap<>();
                    World world = mock(World.class, withSettings().stubOnly());
                    Block cursor = mock(Block.class, withSettings().stubOnly());
                    Pos[] position = new Pos[1];
                    when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                        position[0] = new Pos(call.getArgument(0), call.getArgument(1), call.getArgument(2));
                        return cursor;
                    });
                    doAnswer(call -> { blocks.put(position[0], call.getArgument(0)); return null; })
                        .when(cursor).setType(any(Material.class), anyBoolean());
                    doAnswer(call -> { blocks.put(position[0], ((BlockData) call.getArgument(0)).getMaterial()); return null; })
                        .when(cursor).setBlockData(any(BlockData.class), anyBoolean());
                    assertNotNull(new ProcGenMaterializer(Logger.getAnonymousLogger()).materialize(world, layout, cfg));
                    assertTrue(reachable(blocks, a, b), a.asset.name() + "/" + b.asset.name()
                        + " axis=" + axis + " rotations=" + ra + "," + rb);
                    for (var room : List.of(a, b)) for (var door : room.doors) {
                        assertEquals(65, door.y1);
                        assertEquals(69, door.y2);
                        for (int x = door.x1; x <= door.x2; x++) for (int z = door.z1; z <= door.z2; z++) {
                            assertTrue(blocks.get(new Pos(x, 64, z)).isSolid(), "door floor");
                            assertEquals(Material.AIR, blocks.get(new Pos(x, 65, z)), "door feet");
                            assertEquals(Material.AIR, blocks.get(new Pos(x, 66, z)), "door head");
                            assertTrue(blocks.get(new Pos(x, 70, z)).isSolid(), "door roof");
                        }
                    }
                }
        } finally { field.set(null, before); }
    }

    private static boolean reachable(Map<Pos, Material> blocks, LayoutGenerator.PlacedRoom a,
                                     LayoutGenerator.PlacedRoom b) {
        Set<Pos> seen = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        int cx = a.worldX + a.w / 2, cz = a.worldZ + a.d / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) queue.add(new Pos(cx + dx, 65, cz + dz));
        while (!queue.isEmpty()) {
            Pos p = queue.remove();
            if (!seen.add(p) || blocks.get(p) != Material.AIR
                || blocks.get(new Pos(p.x, 66, p.z)) != Material.AIR
                || !blocks.getOrDefault(new Pos(p.x, 64, p.z), Material.AIR).isSolid()) continue;
            if (p.x > b.worldX && p.x < b.worldX + b.w - 1
                && p.z > b.worldZ && p.z < b.worldZ + b.d - 1) return true;
            queue.add(new Pos(p.x + 1, 65, p.z)); queue.add(new Pos(p.x - 1, 65, p.z));
            queue.add(new Pos(p.x, 65, p.z + 1)); queue.add(new Pos(p.x, 65, p.z - 1));
        }
        return false;
    }

    private static LayoutGenerator.PlacedRoom placed(ProcGenRoomAsset asset, int rot, int cx, int cz) {
        int[] size = asset.region().rotatedSize(rot);
        return new LayoutGenerator.PlacedRoom(asset, 0, 0, rot, cx - size[0] / 2, 64, cz - size[2] / 2,
            size[0], size[1], size[2], true);
    }

    private static List<ProcGenRoomAsset> assets() throws Exception {
        List<ProcGenRoomAsset> assets = new ArrayList<>();
        try (var files = Files.list(ROOMS)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".region.yml")).sorted().toList()) {
                String name = path.getFileName().toString().replace(".region.yml", "");
                assets.add(Objects.requireNonNull(ProcGenRoomAsset.load(name, ROOMS.resolve(name + ".yml").toFile(),
                    null, RegionTemplate.load(path.toFile()))));
            }
        }
        assertEquals(8, assets.size());
        return assets;
    }

}
