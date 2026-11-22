package xuanmo.arcartxsuite.warehouse.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.capability.WarehouseRewardDepositable.Result;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.storage.JdbcWarehouseRepository;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SharedWarehouseRecord;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SlotChange;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository.SlotItemRecord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarehouseBatchDepositTest {
    @TempDir Path temp;
    private final UUID playerId = UUID.randomUUID();
    private final ItemStack[] backpack = new ItemStack[36];
    private JdbcWarehouseRepository repository;
    private WarehouseService service;
    private WarehouseService.ViewState view;
    private Player player;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ItemSerializer> serializer;

    @BeforeEach
    void setup() throws Exception {
        var logger = Logger.getAnonymousLogger();
        var sqlite = new SQLiteDataSource();
        sqlite.setUrl("jdbc:sqlite:" + temp.resolve("warehouse.db"));
        repository = spy(new JdbcWarehouseRepository(sqlite, StorageDescriptor.sqlite("warehouse.db"), temp.toFile(), logger));
        repository.initialize();
        repository.ensureDefaultPersonalWarehouse(playerId, "personal", 1);

        var plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("ArcartXSuite");
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Player");
        var inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(backpack.length);
        when(inventory.getItem(anyInt())).thenAnswer(call -> backpack[call.getArgument(0)]);
        doAnswer(call -> {
            backpack[(int) call.getArgument(0)] = call.getArgument(1);
            return null;
        }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));

        var yaml = new YamlConfiguration();
        yaml.set("warehouses.personal.base-slots", 2);
        var config = WarehouseModuleConfiguration.load(yaml, logger);
        service = spy(new WarehouseService(plugin, logger, null, null, null, null, config, repository,
            mock(ItemSourceRegistry.class), null, null, () -> null, null));
        doReturn(true).when(service).isSecondaryUnlocked(player);
        view = service.state(player);
        view.setWarehouseId("personal");

        bukkit = mockStatic(Bukkit.class);
        var itemFactory = mock(ItemFactory.class);
        when(itemFactory.equals(isNull(), isNull())).thenReturn(true);
        bukkit.when(Bukkit::getItemFactory).thenReturn(itemFactory);
        serializer = mockStatic(ItemSerializer.class);
        serializer.when(() -> ItemSerializer.serialize(any(ItemStack.class)))
            .thenAnswer(call -> ((ItemStack) call.getArgument(0)).getType().name().getBytes(StandardCharsets.UTF_8));
        serializer.when(() -> ItemSerializer.deserialize(any(byte[].class))).thenReturn(new ItemStack(Material.STONE));
    }

    @AfterEach
    void closeMocks() {
        if (serializer != null) serializer.close();
        if (bukkit != null) bukkit.close();
    }

    @Test
    void combinesRepeatedItemsAndCommitsOnce() throws Exception {
        backpack[9] = new ItemStack(Material.STONE, 64);
        backpack[10] = new ItemStack(Material.DIRT, 32);
        backpack[11] = new ItemStack(Material.STONE, 16);
        depositAll();

        verify(repository).loadSlotSummaries("personal", playerId.toString(), "personal");
        verify(repository).applySlotChanges(argThat(changes -> changes.size() == 2));
        var rows = repository.loadSlots("personal", playerId.toString(), "personal");
        assertEquals(List.of("STONE", "DIRT"), rows.stream().map(SlotItemRecord::materialId).toList());
        assertEquals(List.of(80L, 32L), rows.stream().map(SlotItemRecord::amount).toList());
        assertNull(backpack[9]);
        assertNull(backpack[10]);
        assertNull(backpack[11]);
    }

    @Test
    void rewardOverflowCommitsOneReceiptAndKeepsDeclinesStableWhenCapacityChanges() throws Exception {
        var active = WarehouseService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        UUID inserted = UUID.randomUUID();
        UUID declined = UUID.randomUUID();
        doAnswer(call -> {
            assertEquals(true, call.callRealMethod());
            throw new SQLException("入库已提交，回执丢失");
        }).when(repository).commitRewardDeposit(anyList(), argThat(receipt -> receipt != null && inserted.toString().equals(receipt.id())));
        var batch = List.of(new ItemStack(Material.STONE, 10), new ItemStack(Material.DIRT, 8), new ItemStack(Material.STONE, 4));
        try {
            assertEquals(Result.STORED, service.depositReward(inserted, playerId, batch).toCompletableFuture().get(3, TimeUnit.SECONDS));
            assertEquals(Result.STORED, service.depositReward(inserted, playerId, batch).toCompletableFuture().get(3, TimeUnit.SECONDS));
            assertEquals(Result.RETRY, service.depositReward(inserted, playerId, List.of(new ItemStack(Material.STONE)))
                .toCompletableFuture().get(3, TimeUnit.SECONDS));
            var rows = repository.loadSlots("personal", playerId.toString(), "personal");
            assertEquals(List.of(14L, 8L), rows.stream().map(SlotItemRecord::amount).toList());

            var tooLarge = List.of(new ItemStack(Material.STONE, 2), new ItemStack(Material.GRAVEL));
            assertEquals(Result.DECLINED, service.depositReward(declined, playerId, tooLarge).toCompletableFuture().get(3, TimeUnit.SECONDS));
            assertEquals(rows, repository.loadSlots("personal", playerId.toString(), "personal"));
            assertTrue(repository.applySlotChanges(List.of(new SlotChange(rows.get(1), null))));
            assertEquals(Result.DECLINED, service.depositReward(declined, playerId, tooLarge).toCompletableFuture().get(3, TimeUnit.SECONDS));
            assertEquals(List.of(14L), repository.loadSlots("personal", playerId.toString(), "personal")
                .stream().map(SlotItemRecord::amount).toList());
        } finally {
            service.shutdown();
            service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void separatesItemsWithTheSameMaterialButDifferentSerializedData() throws Exception {
        backpack[9] = new ItemStack(Material.STONE, 16);
        ItemStack variant = mock(ItemStack.class);
        ItemStack variantPrototype = mock(ItemStack.class);
        when(variant.getType()).thenReturn(Material.STONE);
        when(variant.getAmount()).thenReturn(8);
        when(variant.clone()).thenReturn(variantPrototype);
        when(variantPrototype.getType()).thenReturn(Material.STONE);
        serializer.when(() -> ItemSerializer.serialize(same(variantPrototype)))
            .thenReturn("different-data".getBytes(StandardCharsets.UTF_8));
        backpack[10] = variant;

        depositAll();

        var rows = repository.loadSlots("personal", playerId.toString(), "personal");
        assertEquals(List.of(16L, 8L), rows.stream().map(SlotItemRecord::amount).toList());
        assertNotEquals(rows.get(0).itemHash(), rows.get(1).itemHash());
    }

    @Test
    void leavesPartialAndRejectedStacksInTheirBackpackSlots() throws Exception {
        var stone = storedSlot(0, Material.STONE, WarehouseService.MAX_AGGREGATED_AMOUNT - 5);
        var dirt = storedSlot(1, Material.DIRT, 64);
        assertTrue(repository.applySlotChanges(List.of(new SlotChange(null, stone), new SlotChange(null, dirt))));
        clearInvocations(repository);
        backpack[9] = new ItemStack(Material.STONE, 12);
        backpack[10] = new ItemStack(Material.GRAVEL, 8);
        backpack[11] = new ItemStack(Material.DIRT, 16);
        backpack[12] = new ItemStack(Material.DIRT, 16);
        doReturn(true).when(service).isItemLocked(same(backpack[11]));
        doReturn(true).when(service).isBlacklisted(same(backpack[12]));

        depositAll();

        verify(repository).applySlotChanges(argThat(changes -> changes.size() == 1));
        assertEquals(WarehouseService.MAX_AGGREGATED_AMOUNT,
            repository.loadSlot("personal", playerId.toString(), "personal", 0).orElseThrow().amount());
        assertEquals(7, backpack[9].getAmount());
        assertEquals(8, backpack[10].getAmount());
        assertEquals(16, backpack[11].getAmount());
        assertEquals(16, backpack[12].getAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "member", "viewer"})
    void sharedBatchRespectsRoleAndRecordsTheAggregatedAmount(String role) throws Exception {
        UUID owner = openShared(role);
        backpack[9] = new ItemStack(Material.STONE, 12);
        backpack[10] = new ItemStack(Material.STONE, 8);
        depositAll();

        var rows = repository.loadSlots("shared", "shared", "shared");
        if (role.equals("viewer")) {
            assertTrue(rows.isEmpty());
            assertEquals(12, backpack[9].getAmount());
            assertEquals(8, backpack[10].getAmount());
            verify(repository, never()).applySharedSlotChanges(anyList(), any());
        } else {
            verify(repository).applySharedSlotChanges(argThat(changes -> changes.size() == 1), any());
            assertEquals(List.of(20L), rows.stream().map(SlotItemRecord::amount).toList());
            var activity = repository.sharedActivity("shared", owner, "", "", 0, 0, 8);
            assertEquals(List.of(20L), activity.stream().map(row -> row.amount()).toList());
            assertNull(backpack[9]);
            assertNull(backpack[10]);
        }
    }

    @Test
    void expiredSharedLeaseRejectsTheWholeBatchBeforeBackpackChanges() throws Exception {
        openShared("member");
        var lease = repository.sharedLease("shared").orElseThrow();
        repository.releaseSharedLease("shared", lease.player(), lease.token());
        backpack[9] = new ItemStack(Material.STONE, 12);
        backpack[10] = new ItemStack(Material.DIRT, 8);

        assertThrows(SQLException.class, this::depositAll);

        assertTrue(repository.loadSlots("shared", "shared", "shared").isEmpty());
        assertEquals(12, backpack[9].getAmount());
        assertEquals(8, backpack[10].getAmount());
    }

    @Test
    void storageRefreshReusesTheVisibleSelectionAndCountsUnfilteredSlots() throws Exception {
        assertTrue(repository.applySlotChanges(List.of(
            new SlotChange(null, storedSlot(0, Material.STONE, 12)),
            new SlotChange(null, storedSlot(1, Material.DIRT, 8)))));
        view.setSearch("stone");
        view.setSelectedSlot(0);
        var packet = service.packetFactory.buildStoragePacket(player, view, null, SecondaryPasswordAccess.Status.LOCKED);
        assertEquals(2L, packet.get("used"));
        assertEquals(2L, packet.get("usedSlots"));
        assertEquals(1, packet.get("matchedSlots"));
        assertEquals(12L, ((Map<?, ?>) packet.get("selectedItem")).get("amount"));
        assertEquals(2L, packet.get("capacity"));
        verify(repository).loadPersonalWarehouses(playerId);
        verify(repository, never()).loadSlot("personal", playerId.toString(), "personal", 0);
        verify(repository, never()).countSlots("personal", playerId.toString(), "personal");
    }

    private UUID openShared(String role) throws Exception {
        UUID owner = role.equals("owner") ? playerId : UUID.randomUUID();
        repository.createSharedWarehouse(new SharedWarehouseRecord("shared", owner, "Shared", 1, 72, 1, 1, "owner", true));
        if (!role.equals("owner")) repository.upsertSharedMember("shared", owner, playerId, role, 6, 1);
        view.setOwnerType("shared");
        view.setOwnerId("shared");
        view.setWarehouseId("shared");
        view.setSharedEditMode(true);
        Method acquire = WarehouseService.class.getDeclaredMethod("acquireCurrentSharedLock", Player.class, WarehouseService.ViewState.class);
        acquire.setAccessible(true);
        assertEquals(!role.equals("viewer"), acquire.invoke(service, player, view));
        return owner;
    }

    private SlotItemRecord storedSlot(int slot, Material material, long amount) {
        String hash = WarehouseTexts.sha256Hex(material.name().getBytes(StandardCharsets.UTF_8));
        return new SlotItemRecord("personal", playerId.toString(), "personal", slot, hash, "other", material.name(),
            material.name(), material.name(), "", "", "serialized", "{}", amount, 1, 1, "");
    }

    private void depositAll() throws Exception {
        Method method = WarehouseService.class.getDeclaredMethod("depositAllBackpack", Player.class);
        method.setAccessible(true);
        try {
            method.invoke(service, player);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw exception;
        }
    }
}
