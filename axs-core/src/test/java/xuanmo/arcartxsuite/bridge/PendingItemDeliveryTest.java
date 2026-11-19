package xuanmo.arcartxsuite.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.capability.WarehouseRewardDepositable;
import xuanmo.arcartxsuite.api.mail.AxsDeliveryReceipt;
import xuanmo.arcartxsuite.api.mail.AxsMailRequest;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.bridge.PendingItemDelivery.SaveEvidence;
import xuanmo.arcartxsuite.bridge.PendingItemSaveStore.Save;
import xuanmo.arcartxsuite.bridge.PendingItemSaveStore.Mode;
import xuanmo.arcartxsuite.bridge.PendingItemSaveStore.Resolution;

class PendingItemDeliveryTest {
    @TempDir Path directory;
    private final Logger logger = Logger.getAnonymousLogger();
    private final UUID player = UUID.randomUUID();
    private StorageManager storage;
    private DefaultPendingRewardService rewards;
    private PendingItemSaveStore store;

    @BeforeEach
    void openStorage() throws Exception {
        storage = mock(StorageManager.class);
        when(storage.getHostConnection()).thenAnswer(call ->
            DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("rewards.db")));
        rewards = new DefaultPendingRewardService(null, logger, storage, null);
        rewards.initialize();
        store = new PendingItemSaveStore(storage);
    }

    @Test
    void confirmsMatchingSaveAndWillNotReleaseAnotherNodesBatch() throws Exception {
        long id = claim();
        var save = new Save(player, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            PendingItemDelivery.BOOT, Mode.CHECKPOINT, List.of(id));
        assertFalse(store.reserve(save, "stale", 5));
        assertTrue(store.load(save.node(), "", 5).isEmpty());
        assertTrue(store.reserve(save, "claim", 5));
        assertEquals(-2, rewards.loadPending(player).get(0).attempts());
        assertFalse(store.reserve(new Save(player, save.node(), UUID.randomUUID().toString(), save.boot(), save.mode(), List.of(id)),
            "claim", 5));
        assertTrue(store.load("another-node", "", 5).isEmpty());

        Path file = directory.resolve(player + ".dat");
        assertThrows(NoSuchFileException.class, () -> PendingItemDelivery.version(file));
        playerData(file, "older-receipt");
        assertEquals(SaveEvidence.WAIT, PendingItemDelivery.evidence(file, PendingItemDelivery.version(file), save));
        assertEquals(1, rewards.loadPending(player).size());
        playerData(file, save.receipt());
        assertEquals(SaveEvidence.STORED, PendingItemDelivery.evidence(file, PendingItemDelivery.version(file), save));
        assertTrue(store.finish(save, Resolution.STORED));
        assertFalse(store.finish(save, Resolution.STORED));
        assertTrue(rewards.loadPending(player).isEmpty());
        assertTrue(store.load(save.node(), "", 5).isEmpty());
    }

    @Test
    void previousBootCanReleaseTheExactBatchWhenAStableSaveHasNoReceipt() throws Exception {
        long id = claim();
        var save = new Save(player, "owner", UUID.randomUUID().toString(), PendingItemDelivery.BOOT - 1, Mode.CHECKPOINT, List.of(id));
        assertTrue(store.reserve(save, "claim", 5));
        Path file = directory.resolve(player + ".dat");
        playerData(file, "older-receipt");
        assertEquals(SaveEvidence.UNAPPLIED, PendingItemDelivery.evidence(file, PendingItemDelivery.version(file), save));
        assertTrue(store.finish(save, Resolution.UNAPPLIED));
        assertEquals(0, rewards.loadPending(player).get(0).attempts());
        assertEquals(id, rewards.loadPending(player).get(0).id());
        assertTrue(store.load(save.node(), "", 5).isEmpty());
    }

    @Test
    void migratingAnOldBatchPreservesItAndSendsMissingEvidenceToReview() throws Exception {
        long id = claim();
        var saved = new Save(player, "original", UUID.randomUUID().toString(),
            PendingItemDelivery.BOOT - 1, Mode.NATURAL, List.of(id));
        assertTrue(store.reserve(saved, "claim", 5));
        try (var conn = storage.getHostConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE axs_pending_item_saves DROP COLUMN save_mode");
            store.initialize(stmt, false);
            store.initialize(stmt, false);
        }
        assertEquals(List.of(saved), store.load("original", "", 5));
        Path file = directory.resolve(player + ".dat");
        playerData(file, "old-marker");
        assertEquals(SaveEvidence.REVIEW, PendingItemDelivery.evidence(file, PendingItemDelivery.version(file), saved));
        assertTrue(store.finish(saved, Resolution.REVIEW));
        assertEquals(-1, rewards.loadPending(player).get(0).attempts());
        assertEquals(id, rewards.loadPending(player).get(0).id());
        assertTrue(store.load("original", "", 5).isEmpty());
    }

    @Test
    void fullInventoryLeavesTheWholeRewardPendingAndRetriesWhenSpaceChanges() throws Exception {
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        var inventory = mock(PlayerInventory.class);
        var recipient = mock(Player.class);
        var timer = mock(BukkitTask.class);
        var stock = mock(ItemStack.class);
        var original = mock(ItemStack.class);
        var fish = mock(ItemStack.class);
        when(stock.getType()).thenReturn(Material.STONE);
        when(stock.getAmount()).thenReturn(64);
        when(stock.clone()).thenReturn(original);
        when(original.getType()).thenReturn(Material.STONE);
        when(original.getAmount()).thenReturn(64);
        when(fish.getType()).thenReturn(Material.COD);
        when(fish.getAmount()).thenReturn(1);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stock});
        when(recipient.getUniqueId()).thenReturn(player);
        when(recipient.getInventory()).thenReturn(inventory);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(timer);
        @SuppressWarnings("unchecked")
        Consumer<Player> retry = mock(Consumer.class);
        var auth = new AuthSnapshot(player, UUID.randomUUID(), 1, player, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        var pending = PendingReward.itemSerialized(player, "fishing", "data", "test");
        var released = new ArrayList<PendingReward>();
        var items = new PendingItemDelivery(plugin, logger, directory, store, retry, () -> null, () -> null, (online, reward) -> fish);
        try {
            items.stage(recipient, auth, "claim", 5, List.of(pending), released::addAll);
            assertEquals(List.of(pending), released);
            verify(inventory, never()).setStorageContents(any());
            verify(recipient, never()).saveData();
            items.inventoryChanged(recipient);
            items.inventoryChanged(recipient);
            verify(retry, times(1)).accept(recipient);
        } finally {
            items.shutdown().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "fishing,true,serialized", "fishing,false,serialized", "entitytracker,true,serialized",
        "entitytracker,true,raw", "fishing,true,spec", "afkreward,true,serialized", "lottery,true,serialized"
    })
    void liveInventoryWaitsForMatchingDiskEvidence(String source, boolean savedToDisk, String format) throws Exception {
        boolean raw = !"serialized".equals(format);
        boolean natural = raw || !"fishing".equals(source);
        var reward = switch (format) {
            case "raw" -> PendingReward.item(player, source, "minecraft", "COD", 3, null, "test");
            case "spec" -> new PendingReward(0, player, source, PendingReward.Type.ITEM_SPEC, "{}", "test",
                System.currentTimeMillis(), 0);
            default -> PendingReward.itemSerialized(player, source, "data", "test");
        };
        assertTrue(PendingItemSaveStore.tracks(reward));
        assertTrue(rewards.savePendingBatchConfirmed("capture", List.of(reward)));
        var claim = DefaultPendingRewardService.class.getDeclaredMethod("claimAndLoadPending", UUID.class);
        claim.setAccessible(true);
        Object batch = claim.invoke(rewards, player);
        var owner = batch.getClass().getDeclaredMethod("owner");
        var stamp = batch.getClass().getDeclaredMethod("stamp");
        owner.setAccessible(true);
        stamp.setAccessible(true);
        var pending = rewards.loadPending(player).get(0);
        assertEquals(0, pending.attempts(), "the initial claim must be recoverable before staging");

        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        var recipient = mock(Player.class);
        var inventory = mock(PlayerInventory.class);
        var pdc = mock(PersistentDataContainer.class);
        var fish = mock(ItemStack.class);
        var applied = mock(ItemStack.class);
        var applications = new LinkedBlockingQueue<Runnable>();
        var scan = new AtomicReference<Runnable>();
        var auth = new AuthSnapshot(player, UUID.randomUUID(), 1, player, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            scan.set(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(1L))).thenAnswer(call -> {
            applications.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(recipient.isOnline()).thenReturn(true);
        when(recipient.getInventory()).thenReturn(inventory);
        when(recipient.getPersistentDataContainer()).thenReturn(pdc);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{null});
        when(fish.getType()).thenReturn(Material.COD);
        when(fish.getAmount()).thenReturn(3);
        when(fish.getMaxStackSize()).thenReturn(64);
        when(fish.clone()).thenReturn(applied);
        var items = new PendingItemDelivery(plugin, logger, directory, store, ignored -> {}, () -> null, () -> null, (online, pendingItem) -> fish);
        try (var bukkit = mockStatic(Bukkit.class); var authAccess = mockStatic(AuthAccess.class);
             var serializer = mockStatic(ItemSerializer.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player)).thenReturn(recipient);
            authAccess.when(() -> AuthAccess.snapshot(player)).thenReturn(auth);
            if (raw) serializer.when(() -> ItemSerializer.serialize(fish)).thenReturn(new byte[]{1, 2, 3});
            items.stage(recipient, auth, (String) owner.invoke(batch), (long) stamp.invoke(batch), List.of(pending),
                unexpected -> fail("unexpected release"));
            Runnable apply = applications.poll(3, TimeUnit.SECONDS);
            assertNotNull(apply);
            verify(inventory, never()).setStorageContents(any());
            String node = Files.readString(directory.resolve("pending-item-installation.id"));
            var save = store.load(node, "", 5).get(0);
            assertEquals(natural ? Mode.NATURAL : Mode.CHECKPOINT, save.mode());
            var staged = rewards.loadPending(player).get(0);
            assertEquals(-2, staged.attempts());
            assertEquals("spec".equals(format) ? PendingReward.Type.ITEM_NATURAL : PendingReward.Type.ITEM_SERIALIZED, staged.type());
            if (raw) assertTrue(staged.payload().contains("AQID"));
            Path file = directory.resolve(player + ".dat");
            playerData(file, "old-marker");
            doAnswer(call -> {
                verify(pdc).set(PendingItemDelivery.RECEIPT, PersistentDataType.STRING, save.receipt());
                if (savedToDisk) playerData(file, save.receipt());
                return null;
            }).when(recipient).saveData();
            apply.run();
            verify(inventory).setStorageContents(argThat(contents -> contents.length == 1 && contents[0] == applied));
            if (natural) {
                verify(recipient, never()).saveData();
                assertEquals(-2, rewards.loadPending(player).get(0).attempts());
                verify(pdc).set(PendingItemDelivery.RECEIPT, PersistentDataType.STRING, save.receipt());
                playerData(file, save.receipt());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (!rewards.loadPending(player).isEmpty() && System.nanoTime() < deadline) {
                    scan.get().run();
                    Thread.sleep(10);
                }
            } else {
                verify(recipient).saveData();
                if (!savedToDisk) {
                    verify(inventory).setStorageContents(argThat(contents -> contents.length == 1 && contents[0] == null));
                    verify(pdc).remove(PendingItemDelivery.RECEIPT);
                }
            }
        } finally {
            items.shutdown().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
        if (savedToDisk) assertTrue(rewards.loadPending(player).isEmpty());
        else assertEquals(-1, rewards.loadPending(player).get(0).attempts());
    }

    @ParameterizedTest
    @CsvSource({"fishing,false", "fishing,true", "entitytracker,false", "entitytracker,true", "chat,true"})
    void overflowKeepsItsMailRouteAndRequestKeyAfterLosingTheReceipt(String source, boolean warehouseEnabled) throws Exception {
        claim(source);
        var pending = rewards.loadPending(player).get(0);
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        var inventory = mock(PlayerInventory.class);
        var recipient = mock(Player.class);
        var stock = mock(ItemStack.class);
        var stored = mock(ItemStack.class);
        var fish = mock(ItemStack.class);
        var mail = mock(AxsMailService.class);
        var warehouse = warehouseEnabled ? mock(WarehouseRewardDepositable.class) : null;
        var warehouseDecision = new CompletableFuture<WarehouseRewardDepositable.Result>();
        if (warehouse != null) when(warehouse.depositReward(any(), any(), any()))
            .thenReturn(warehouseDecision);
        var applications = new LinkedBlockingQueue<Runnable>();
        var timer = new AtomicReference<Runnable>();
        var requests = new ArrayList<AxsMailRequest>();
        var interrupted = new CompletableFuture<AxsDeliveryReceipt>();
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            timer.set(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(1L))).thenAnswer(call -> {
            applications.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(recipient.getInventory()).thenReturn(inventory);
        when(stock.getType()).thenReturn(Material.STONE);
        when(stock.getAmount()).thenReturn(64);
        when(stock.clone()).thenReturn(stored);
        when(stored.getType()).thenReturn(Material.STONE);
        when(stored.getAmount()).thenReturn(64);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stock});
        when(fish.getType()).thenReturn(Material.COD);
        when(fish.getAmount()).thenReturn(3);
        when(mail.sendDurable(any())).thenAnswer(call -> {
            requests.add(call.getArgument(0));
            return requests.size() == 1 ? interrupted : CompletableFuture.completedFuture(AxsDeliveryReceipt.duplicate(17));
        });
        var auth = new AuthSnapshot(player, UUID.randomUUID(), 1, player, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");

        var items = new PendingItemDelivery(plugin, logger, directory, store, ignored -> {}, () -> mail, () -> warehouse, (online, reward) -> fish);
        Save staged;
        String node;
        try {
            items.stage(recipient, auth, "claim", 5, List.of(pending), unexpected -> fail("overflow released after staging"));
            var send = applications.poll(3, TimeUnit.SECONDS);
            assertNotNull(send);
            node = Files.readString(directory.resolve("pending-item-installation.id"));
            staged = store.load(node, "", 5).get(0);
            if (warehouse != null) {
                assertEquals(Mode.WAREHOUSE, staged.mode());
                send.run();
                verify(warehouse).depositReward(UUID.fromString(staged.receipt()), player, List.of(fish));
                assertTrue(requests.isEmpty());
                assertEquals(Mode.WAREHOUSE, store.load(node, "", 5).get(0).mode());
                warehouseDecision.complete(WarehouseRewardDepositable.Result.DECLINED);
                send = applications.poll(3, TimeUnit.SECONDS);
                assertNotNull(send);
                staged = store.load(node, "", 5).get(0);
            }
            assertEquals(Mode.MAIL, staged.mode());
            send.run();
            assertEquals(-2, rewards.loadPending(player).get(0).attempts());
            interrupted.completeExceptionally(new SQLException("mail accepted but the acknowledgement was lost"));
        } finally {
            items.shutdown().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }

        assertEquals(List.of(staged), store.load(node, "", 5));
        try (var conn = storage.getHostConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE axs_pending_item_saves SET retry_at=0");
        }
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{null});
        var recovered = new PendingItemDelivery(plugin, logger, directory, store, ignored -> {}, () -> mail, () -> warehouse, (online, reward) -> fish);
        try {
            timer.get().run();
            var send = applications.poll(3, TimeUnit.SECONDS);
            assertNotNull(send);
            send.run();
        } finally {
            recovered.shutdown().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
        assertEquals(2, requests.size());
        assertEquals(source, requests.get(0).sourcePlugin());
        assertEquals(source, requests.get(1).sourcePlugin());
        assertEquals(requests.get(0).idempotencyKey(), requests.get(1).idempotencyKey());
        assertEquals(List.of(fish), requests.get(1).items());
        assertEquals(List.of(player), requests.get(1).recipients());
        assertTrue(rewards.loadPending(player).isEmpty());
        assertTrue(store.load(node, "", 5).isEmpty());
        verify(inventory, never()).setStorageContents(any());
        verify(recipient, never()).saveData();
    }

    @Test
    void sharedMailLeaseHasOneWinnerAndCannotSwitchToAnInventoryRoute() throws Exception {
        long id = claim();
        var save = new Save(player, PendingItemSaveStore.SHARED_MAIL, UUID.randomUUID().toString(),
            PendingItemDelivery.BOOT, Mode.MAIL, List.of(id));
        assertTrue(store.reserve(save, "claim", 5));
        long afterInitialLease = System.currentTimeMillis() + 31_000;
        assertTrue(store.load("other-node", false, false, "", 5, afterInitialLease).isEmpty());
        assertEquals(List.of(save), store.load("other-node", true, false, "", 5, afterInitialLease));
        assertTrue(store.claimRouted(save, afterInitialLease));
        assertFalse(store.claimRouted(save, afterInitialLease));
        var inventory = new Save(player, "other-node", UUID.randomUUID().toString(), save.boot(), Mode.CHECKPOINT, List.of(id));
        assertFalse(store.reserve(inventory, "claim", 5));
        assertEquals(-2, store.loadRoutedRewards(save).get(0).attempts());
    }

    private long claim() throws Exception {
        return claim("fishing");
    }

    private long claim(String source) throws Exception {
        assertTrue(rewards.savePendingBatchConfirmed("capture", List.of(PendingReward.itemSerialized(player, source, "data", "test"))));
        long id = rewards.loadPending(player).get(0).id();
        try (var conn = storage.getHostConnection(); var ps = conn.prepareStatement(
                "UPDATE axs_pending_rewards SET claimed_by='claim', claimed_at=5 WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
        return id;
    }

    private static void playerData(Path file, String receipt) throws Exception {
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("BukkitValues");
            out.writeByte(8);
            out.writeUTF(PendingItemDelivery.RECEIPT.toString());
            out.writeUTF(receipt);
            out.writeByte(0);
            out.writeByte(0);
        }
    }
}
