package xuanmo.arcartxsuite.map.service;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
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
        MapTerrainPushService service = new MapTerrainPushService(null, Logger.getAnonymousLogger(), ignored -> true,
            id -> admission(id, 1));
        UUID id = UUID.randomUUID();
        Player player = player(id);
        service.submit(player, world, List.of(1L, 1L, 2L), 7);
        service.submit(player, world, List.of(1L, 2L, 3L), 7);
        assertEquals(3, size(service, "pendingRecipients"));
        service.cancelPlayer(id);
        assertEquals(0, size(service, "pendingRecipients"));
        assertEquals(0, size(service, "jobs"));
        service.submit(player, world, List.of(1L), 7);
        assertEquals(1, size(service, "pendingRecipients"));
        service.shutdown();
        service.submit(player, world, List.of(2L), 7);
        assertEquals(0, size(service, "pendingRecipients"));
    }

    @Test void boundsTotalWorkAndPerPlayerWork() throws Exception {
        MapTerrainPushService service = new MapTerrainPushService(null, Logger.getAnonymousLogger(), ignored -> true,
            id -> admission(id, 1));
        List<Long> coords = new ArrayList<>();
        for (long i = 0; i < 2000; i++) coords.add(i);
        Player first = player(UUID.randomUUID());
        service.submit(first, world, coords, 1);
        service.submit(first, world, coords, 2);
        assertEquals(1024, size(service, "pendingRecipients"));
        for (int i = 0; i < 8; i++) service.submit(player(UUID.randomUUID()), world, coords, 1);
        assertEquals(4096, size(service, "pendingRecipients"));
    }

    @Test void splitsCaptureAcrossTicksAndDiscardsCancelledAsyncResults() throws Exception {
        UUID id = UUID.randomUUID();
        Player player = player(id);
        var server = terrainServer(Map.of(id, player));
        var service = new MapTerrainPushService(null, Logger.getAnonymousLogger(), ignored -> true,
            uuid -> admission(uuid, 1));
        service.submit(player, server.world(), List.of(0L), 7);
        var capture = captureOne(service, server);
        assertEquals(16, server.loads()[0]);
        assertEquals(1, server.async().size());
        service.cancelPlayer(id);
        // Same UUID reconnects before the previous async work completes.
        service.submit(player, server.world(), List.of(0L), 7);
        capture.run();
        assertEquals(0, size(service, "outbox"));
        assertEquals(1, size(service, "pendingRecipients"));
    }

    @Test void sharesCaptureAndEncodingAmongCurrentPlayersAndRelays() throws Exception {
        UUID cancelledId = UUID.randomUUID(), viewerId = UUID.randomUUID(), lateId = UUID.randomUUID(), revokedId = UUID.randomUUID();
        UUID readmittedId = UUID.randomUUID();
        Player cancelled = player(cancelledId), viewer = player(viewerId), late = player(lateId), revoked = player(revokedId);
        Player readmitted = player(readmittedId);
        var server = terrainServer(Map.of(cancelledId, cancelled, viewerId, viewer, lateId, late, revokedId, revoked,
            readmittedId, readmitted));
        var allowed = new java.util.HashSet<>(List.of(cancelled, viewer, late, revoked, readmitted));
        var generations = new java.util.HashMap<UUID, Long>();
        var service = new MapTerrainPushService(null, Logger.getAnonymousLogger(), allowed::contains,
            id -> admission(id, generations.getOrDefault(id, 1L)));
        List<Long> coords = List.of(0L);
        List<byte[]> relay = new ArrayList<>();
        service.submit(cancelled, server.world(), coords, 7);
        service.submit(viewer, server.world(), coords, 7);
        service.submit(revoked, server.world(), coords, 7);
        service.submit(readmitted, server.world(), coords, 7);
        service.submitForRelay(server.world(), coords, 7, bytes -> bytes[0] = 42);
        service.submitForRelay(server.world(), coords, 7, relay::add);
        Runnable capture = captureOne(service, server);
        service.cancelPlayer(cancelledId);
        allowed.remove(revoked);
        generations.put(readmittedId, 2L);
        capture.run();
        service.submit(late, server.world(), coords, 7);
        assertEquals(16, server.loads()[0]);
        service.submit(viewer, server.world(), coords, 8);
        pump(service);
        assertTrue(server.loads()[0] > 16);
        assertEquals(1, server.async().size());
        assertEquals(1, size(service, "pendingRecipients"));
        verify(cancelled, never()).sendPluginMessage(any(), anyString(), any());
        verify(revoked, never()).sendPluginMessage(any(), anyString(), any());
        verify(readmitted, never()).sendPluginMessage(any(), anyString(), any());
        var packet = ArgumentCaptor.forClass(byte[].class);
        var another = ArgumentCaptor.forClass(byte[].class);
        verify(viewer).sendPluginMessage(isNull(), eq(MapTerrainPushService.CHANNEL_TERRAIN), packet.capture());
        verify(late).sendPluginMessage(isNull(), eq(MapTerrainPushService.CHANNEL_TERRAIN), another.capture());
        assertSame(packet.getValue(), another.getValue());
        var input = new DataInputStream(new ByteArrayInputStream(packet.getValue()));
        assertEquals("minecraft:overworld", input.readUTF());
        assertEquals(1, relay.size());
        assertArrayEquals(input.readAllBytes(), relay.get(0));
        var body = new DataInputStream(new ByteArrayInputStream(relay.get(0)));
        assertEquals(0, body.readInt());
        assertEquals(0, body.readInt());
        assertEquals(7, body.readInt());
    }

    private record TerrainServer(World world, int[] loads, List<Runnable> async) {}

    private static AuthSnapshot admission(UUID id, long generation) {
        return new AuthSnapshot(id, id, generation, id, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
    }

    private TerrainServer terrainServer(Map<UUID, Player> onlinePlayers) {
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
            case "isChunkLoaded" -> true;
            case "getChunkAt" -> { loads[0]++; yield chunk; }
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
            case "getPlayer" -> onlinePlayers.get(args[0]);
            case "getScheduler" -> scheduler;
            case "getLogger" -> Logger.getAnonymousLogger();
            case "getName", "getVersion", "getBukkitVersion" -> "test";
            default -> null;
        }));
        return new TerrainServer(loadedWorld, loads, async);
    }

    private static Runnable captureOne(MapTerrainPushService service, TerrainServer server) throws Exception {
        int ticks = 0;
        while (server.async().isEmpty() && ticks++ < 20) {
            int before = server.loads()[0];
            pump(service);
            assertTrue(server.loads()[0] - before <= 4, "at most four loads per tick");
        }
        assertTrue(ticks >= 4);
        return server.async().get(0);
    }

    private static void pump(MapTerrainPushService service) throws Exception {
        var pump = MapTerrainPushService.class.getDeclaredMethod("pump");
        pump.setAccessible(true);
        pump.invoke(service);
    }

    private static byte[] request(int count, int actual) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF("world");
        out.writeInt(count);
        for (int i = 0; i < actual; i++) {
            out.writeInt(i);
            out.writeInt(-i);
            out.writeShort(0xffff);
        }
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
        Object value = field.get(service);
        return value instanceof Integer count ? count : ((Collection<?>) value).size();
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
