package xuanmo.arcartxsuite.warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration.WarehouseDefinition;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.*;

class WarehouseMaterialsAuthorizationTest {
    @TempDir Path directory;
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Player player = mock(Player.class);
    private final WarehouseRepository repository = mock(WarehouseRepository.class);
    private final SecondaryPasswordAccess passwords = mock(SecondaryPasswordAccess.class);
    private final AtomicReference<SecondaryPasswordAccess> provider = new AtomicReference<>(passwords);
    private final AtomicBoolean unlocked = new AtomicBoolean(true);
    private final AtomicBoolean identityCurrent = new AtomicBoolean(true);
    private final BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
    private final UUID owner = UUID.randomUUID();
    private MockedStatic<Bukkit> bukkit;
    private WarehouseService service;
    private WarehouseMaterials materials;
    private SlotItemRecord row;
    private java.lang.reflect.Field serverField;
    private Object previousServer;

    @BeforeEach void setup() throws Exception {
        when(plugin.getName()).thenReturn("ArcartXSuite");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(player.getUniqueId()).thenReturn(owner);
        when(player.isOnline()).thenReturn(true);
        var config = mock(WarehouseModuleConfiguration.class);
        when(config.warehouses()).thenReturn(Map.of("personal", mock(WarehouseDefinition.class)));
        service = spy(new WarehouseService(plugin, Logger.getAnonymousLogger(), null, null, null, null,
            config, repository, null, null, null, null, null, null, provider::get, null));
        row = new SlotItemRecord("personal", owner.toString(), "personal", 0, "hash", "other",
            "橡木木板", "OAK_PLANKS", "", "", "", "encoded", "{}", 100, 10, 10, "");
        when(repository.loadPersonalWarehouses(owner)).thenReturn(List.of(
            new WarehouseRecord(owner, "personal", 1, "", false, 10, 0)));
        when(repository.loadSlots("personal", owner.toString(), "personal")).thenReturn(List.of(row));
        when(repository.loadSlot("personal", owner.toString(), "personal", 0)).thenReturn(Optional.of(row));
        doReturn(new org.bukkit.inventory.ItemStack(Material.OAK_PLANKS)).when(service).deserializeSlotItem(row);
        when(repository.escrowMaterials(any(), any())).thenReturn(true);
        when(repository.transitionMaterialTransfer(anyString(), eq(owner.toString()), anyString(), anyString(), anyString())).thenReturn(true);
        when(passwords.statusAsync(player)).thenReturn(CompletableFuture.completedFuture(
            new SecondaryPasswordAccess.Status(false, unlocked::get,
                () -> CompletableFuture.completedFuture(identityCurrent.get()))));
        var scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        doAnswer(call -> { main.add(call.getArgument(1)); return task; })
            .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
        Server server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        // The database worker calls Bukkit outside Mockito's thread-local static scope.
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        materials = new WarehouseMaterials(service, plugin, directory.toFile(), false);
        assertFalse(service.isSecondaryUnlocked(player), "no main warehouse UI action is running");
    }

    @AfterEach void close() throws Exception {
        try {
            if (materials != null) {
                materials.close();
                materials.stopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            }
        } finally {
            if (bukkit != null && !bukkit.isClosed()) bukkit.close();
            if (serverField != null) serverField.set(null, previousServer);
        }
    }

    @Test void externalReadAndTransferReuseTheIndependentPasswordAuthorization() throws Exception {
        var snapshot = read();
        assertTrue(snapshot.available(), snapshot.unavailableReason());
        assertEquals(100, snapshot.entries().get(0).amount());
        var prepared = prepare(snapshot);
        assertTrue(prepared.success(), prepared.message());
        var authorized = new AtomicReference<Boolean>();
        materials.authorizeDelivery(player, prepared.operationId(), authorized::set);
        assertTrue(await(authorized));
        var complete = new AtomicReference<Boolean>();
        materials.completeDelivery(player, prepared.operationId(), complete::set);
        assertTrue(await(complete));
        verify(passwords, times(1)).statusAsync(player);
        verify(passwords, never()).isUnlocked(any());
        verify(repository).transitionMaterialTransfer(eq(prepared.operationId()), eq(owner.toString()),
            eq("APPLYING"), eq("APPLIED"), anyString());
    }

    @Test void aLockedOrUnavailablePasswordProviderCannotReadStock() throws Exception {
        unlocked.set(false);
        assertFalse(read().available());
        verify(repository, never()).loadSlots(anyString(), anyString(), anyString());
        provider.set(null);
        assertFalse(read().available());
    }

    @Test void aStoredIdentityChangeAfterPreviewBlocksEscrowBeforeAnyDeduction() throws Exception {
        var snapshot = read();
        identityCurrent.set(false);
        var prepared = prepare(snapshot);
        assertFalse(prepared.success());
        verify(repository, never()).escrowMaterials(any(), any());
    }

    @Test void replacingThePasswordProviderInvalidatesIssuedStock() throws Exception {
        var snapshot = read();
        provider.set(mock(SecondaryPasswordAccess.class));
        assertFalse(prepare(snapshot).success());
        verify(repository, never()).escrowMaterials(any(), any());
    }

    @Test void lockingBeforeAuthorizationDoesNotEnterApplying() throws Exception {
        var prepared = prepare(read());
        assertTrue(prepared.success());
        unlocked.set(false);
        var authorized = new AtomicReference<Boolean>();
        materials.authorizeDelivery(player, prepared.operationId(), authorized::set);
        assertFalse(await(authorized));
        verify(repository, never()).transitionMaterialTransfer(eq(prepared.operationId()), anyString(),
            eq("HELD"), eq("APPLYING"), anyString());
    }

    @Test void aStoredIdentityChangeWhileSavingTheReceiptReturnsEscrowBeforeRejectingDelivery() throws Exception {
        var held = new AtomicReference<MaterialTransfer>();
        when(repository.escrowMaterials(any(), any())).thenAnswer(call -> {
            held.set(call.getArgument(1));
            return true;
        });
        var prepared = prepare(read());
        var saved = held.get();
        var applying = new MaterialTransfer(saved.id(), saved.ownerId(), saved.warehouseId(), saved.serverId(),
            saved.runId(), "APPLYING", saved.adapter(), saved.target(), saved.evidence(), saved.payload(),
            saved.createdAt(), saved.updatedAt(), "");
        when(repository.materialTransfer(prepared.operationId(), owner.toString())).thenReturn(Optional.of(applying));
        when(repository.restoreHeldMaterialTransfer(eq(applying), anyList())).thenReturn(true);
        when(repository.transitionMaterialTransfer(eq(prepared.operationId()), eq(owner.toString()),
            eq("HELD"), eq("APPLYING"), anyString())).thenAnswer(call -> { identityCurrent.set(false); return true; });
        var authorized = new AtomicReference<Boolean>();
        materials.authorizeDelivery(player, prepared.operationId(), authorized::set);
        assertFalse(await(authorized));
        verify(repository).transitionMaterialTransfer(eq(prepared.operationId()), eq(owner.toString()),
            eq("APPLYING"), eq("HELD"), anyString());
        verify(repository).restoreHeldMaterialTransfer(eq(applying), anyList());
    }

    @Test void aLegacySynchronousReadRefreshesItsAuthorizationBeforePreparing() throws Exception {
        when(passwords.isUnlocked(player)).thenReturn(true);
        var snapshot = materials.materials(player);
        assertTrue(snapshot.available());
        assertTrue(prepare(snapshot).success());
        verify(passwords).statusAsync(player);
    }

    private WarehouseMaterialAccess.Snapshot read() throws Exception {
        var result = new AtomicReference<WarehouseMaterialAccess.Snapshot>();
        materials.materialsAsync(player, result::set);
        return await(result);
    }
    private WarehouseMaterialAccess.Prepared prepare(WarehouseMaterialAccess.Snapshot snapshot) throws Exception {
        var result = new AtomicReference<WarehouseMaterialAccess.Prepared>();
        materials.prepareMaterials(player, snapshot, List.of(new WarehouseMaterialAccess.Request(0, 5)),
            new WarehouseMaterialAccess.Target("vanilla:test", "inventory", "EPHEMERAL", "before after"), result::set);
        return await(result);
    }
    private <T> T await(AtomicReference<T> result) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (result.get() == null && System.nanoTime() < deadline) {
            Runnable callback = main.poll(20, TimeUnit.MILLISECONDS);
            if (callback != null) callback.run();
        }
        return java.util.Objects.requireNonNull(result.get(), "asynchronous operation did not complete");
    }
}
