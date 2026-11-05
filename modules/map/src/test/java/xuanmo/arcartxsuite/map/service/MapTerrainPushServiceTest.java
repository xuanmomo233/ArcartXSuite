package xuanmo.arcartxsuite.map.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MapTerrainPushServiceTest {
    private Server previousServer;

    @BeforeEach void rememberServer() { previousServer = Bukkit.getServer(); }

    @AfterEach void restoreServer() throws Exception {
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, previousServer);
    }
    private final UUID worldId = UUID.randomUUID();
    private final World world = proxy(World.class, (method, args) -> switch (method) {
        case "getUID" -> worldId;
        case "getKey" -> NamespacedKey.minecraft("overworld");
        default -> null;
    });

    @Test void rejectsTruncatedOversizedAndTrailingPayloads() throws Exception {
        assertNull(MapTerrainPushService.parseRequest(request(1025, 0)));
        assertNull(MapTerrainPushService.parseRequest(request(1, 0)));
        assertNull(MapTerrainPushService.parseRequest(request(0, 1)));
        assertEquals(2, MapTerrainPushService.parseRequest(request(2, 2)).chunks().size());
    }

    @Test void deduplicatesAcrossRequestsAndReleasesOnQuit() throws Exception {
        MapTerrainPushService service = new MapTerrainPushService(null, Logger.getAnonymousLogger());
        UUID id = UUID.randomUUID();
        Player player = player(id);
        service.submit(player, world, List.of(1L, 1L, 2L), 7);
        service.submit(player, world, List.of(1L, 2L, 3L), 7);
        assertEquals(3, size(service, "pending"));
        service.cancelPlayer(id);
        assertEquals(0, size(service, "pending"));
        assertEquals(0, size(service, "jobs"));
        service.submit(player, world, List.of(1L), 7);
        assertEquals(1, size(service, "pending"));
        service.shutdown();
        service.submit(player, world, List.of(2L), 7);
        assertEquals(0, size(service, "pending"));
    }

    @Test void boundsTotalWorkAndPerPlayerWork() throws Exception {
        MapTerrainPushService service = new MapTerrainPushService(null, Logger.getAnonymousLogger());
        List<Long> coords = new ArrayList<>();
        for (long i = 0; i < 2000; i++) coords.add(i);
        Player first = player(UUID.randomUUID());
        service.submit(first, world, coords, 1);
        service.submit(first, world, coords, 2);
        assertEquals(1024, size(service, "pending"));
        for (int i = 0; i < 8; i++) service.submit(player(UUID.randomUUID()), world, coords, 1);
        assertEquals(4096, size(service, "pending"));
    }

    @Test void splitsCaptureAcrossTicksAndDiscardsCancelledAsyncResults() throws Exception {
        UUID id = UUID.randomUUID();
        Player player = player(id);
        int[] loads = {0};
        List<Runnable> async = new ArrayList<>();
        ChunkSnapshot snapshot = proxy(ChunkSnapshot.class, (method, args) -> switch (method) {
            case "getHighestBlockYAt" -> 64;
            case "getBlockType" -> Material.STONE;
            default -> null;
        });
        Chunk chunk = proxy(Chunk.class, (method, args) -> method.equals("getChunkSnapshot") ? snapshot : null);
        World loadedWorld = proxy(World.class, (method, args) -> switch (method) {
            case "getUID" -> worldId;
            case "getKey" -> NamespacedKey.minecraft("overworld");
            case "loadChunk" -> { loads[0]++; yield true; }
            case "getChunkAt" -> chunk;
            default -> null;
        });
        BukkitTask task = proxy(BukkitTask.class, (method, args) -> null);
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (method, args) -> {
            if (method.equals("runTaskAsynchronously")) { async.add((Runnable) args[1]); return task; }
            return null;
        });
        if (Bukkit.getServer() != null) throw new AssertionError("isolated test server required");
        Bukkit.setServer(proxy(Server.class, (method, args) -> switch (method) {
            case "getWorld" -> loadedWorld;
            case "getPlayer" -> player;
            case "getScheduler" -> scheduler;
            case "getLogger" -> Logger.getAnonymousLogger();
            case "getName", "getVersion", "getBukkitVersion" -> "test";
            default -> null;
        }));
        MapTerrainPushService service = new MapTerrainPushService(null, Logger.getAnonymousLogger());
        service.submit(player, loadedWorld, List.of(0L), 7);
        var pump = MapTerrainPushService.class.getDeclaredMethod("pump");
        pump.setAccessible(true);
        int ticks = 0;
        while (async.isEmpty() && ticks++ < 20) {
            int before = loads[0];
            pump.invoke(service);
            assertTrue(loads[0] - before <= 4, "at most four loads per tick");
        }
        assertEquals(16, loads[0]);
        assertTrue(ticks >= 4);
        assertEquals(1, async.size());
        service.cancelPlayer(id);
        // Same UUID reconnects before the previous async work completes.
        service.submit(player, loadedWorld, List.of(0L), 7);
        async.get(0).run();
        assertEquals(0, size(service, "outbox"));
        assertEquals(1, size(service, "pending"));
    }

    private static byte[] request(int count, int actual) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("world");
        out.writeInt(count);
        for (int i = 0; i < actual; i++) { out.writeInt(i); out.writeInt(-i); }
        return bytes.toByteArray();
    }

    private static Player player(UUID id) {
        return proxy(Player.class, (method, args) -> switch (method) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            default -> null;
        });
    }

    private static int size(Object service, String name) throws Exception {
        var field = service.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return ((Collection<?>) field.get(service)).size();
    }

    interface Call { Object invoke(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call) {
        // JDK Proxy eagerly initializes World method parameter types such as StructureType,
        // which requires a running Bukkit registry on newer 1.20.1 API snapshots.
        return mock(type, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS).defaultAnswer(invocation -> {
            Object value = call.invoke(invocation.getMethod().getName(), invocation.getArguments());
            return value != null || !invocation.getMethod().getReturnType().isPrimitive()
                ? value : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        }));
    }
}
