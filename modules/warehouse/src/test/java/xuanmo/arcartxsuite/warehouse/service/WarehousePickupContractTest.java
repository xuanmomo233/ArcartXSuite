package xuanmo.arcartxsuite.warehouse.service;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.storage.WarehouseRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import java.util.List;
import java.util.HashMap;
import java.util.function.BiPredicate;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable.GroundDepositResult;

class WarehousePickupContractTest {
    WarehouseService service;
    WarehouseRepository repository;
    ItemMatcherAPI matcher;
    Player player;
    ItemStack stack;
    UUID id = UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("ArcartXSuite");
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        stack = mock(ItemStack.class);
        when(stack.clone()).thenReturn(stack);
        when(stack.getType()).thenReturn(Material.STONE);
        when(stack.getAmount()).thenReturn(32);
        repository = mock(WarehouseRepository.class);
        when(repository.loadPlayerSettings(id)).thenReturn(Optional.empty());
        matcher = mock(ItemMatcherAPI.class);
        when(matcher.matchesAny(anyList(), any())).thenAnswer(call -> {
            List<xuanmo.arcartxsuite.api.item.ItemMatcher> rules = call.getArgument(0);
            ItemStack candidate = call.getArgument(1);
            return rules.stream().anyMatch(rule -> matcher.matches(rule, candidate));
        });
        when(matcher.matches(any(), eq(stack))).thenReturn(true);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blacklist.name-contains",List.of("禁止存入"));
        yaml.set("blacklist.lore-contains",List.of("任务物品"));
        var config = WarehouseModuleConfiguration.load(yaml, Logger.getAnonymousLogger());
        service = new WarehouseService(plugin, Logger.getAnonymousLogger(), null, null, null, null, config, repository,
            mock(ItemSourceRegistry.class), matcher, null, () -> null, null);
    }
    @Test void rejectedAutoDepositKeepsEveryItem() {
        var result = service.depositToPersonalWarehouse(player, stack);
        assertFalse(result.success());
        assertEquals(0, result.storedAmount());
        assertEquals(32, result.remainingAmount());
    }
    @Test void matchingNameAloneBlocksWithoutAlsoRequiringLore() {
        when(matcher.matches(any(),eq(stack))).thenAnswer(inv -> !((xuanmo.arcartxsuite.api.item.ItemMatcher)inv.getArgument(0)).nameContains().isEmpty());
        assertTrue(service.isBlacklisted(stack));
    }
    @Test void rejectedPublicDepositAlsoKeepsEveryItem() {
        assertEquals(32, service.deposit(player, stack).remainingAmount());
    }
    @Test void directSlotWriteAlsoRespectsWarehouseBlacklist() {
        assertFalse(service.setSlot(player,0,stack).success());
    }
    @Test void findingAnEmptySlotDoesNotAllocateTheConfiguredCapacity() {
        assertEquals(0,WarehouseService.firstEmptySlot(List.of(),Long.MAX_VALUE));
        assertEquals(-1,WarehouseService.firstEmptySlot(List.of(),0));
    }
    @Test void scannerInterceptionPreventsSecondAutomaticDeposit() throws Exception {
        service.setPickupInterceptorProvider(()->playerId->true);
        org.bukkit.entity.Item item=mock(org.bukkit.entity.Item.class);
        var event=new org.bukkit.event.entity.EntityPickupItemEvent(player,item,0);
        service.onEntityPickupItem(event);
        verifyNoInteractions(repository,matcher);
    }
    @Test void personalPickupPreferenceAlsoControlsScannerBridge() throws Exception {
        when(repository.loadPlayerSettings(id)).thenReturn(Optional.of(new WarehouseRepository.PlayerSettingsRecord(id,false,false,false,1)));
        assertFalse(service.depositToPersonalWarehouse(player, stack).success());
        verifyNoInteractions(matcher);
        assertFalse(service.shouldNotifyAutoDeposit(player));
    }
    @Test void playerPurgeInvalidatesCachedPickupPreferences() throws Exception {
        assertTrue(service.shouldNotifyAutoDeposit(player));
        when(repository.loadPlayerSettings(id)).thenReturn(Optional.of(new WarehouseRepository.PlayerSettingsRecord(id,false,false,false,1)));
        var active=WarehouseService.class.getDeclaredField("active");active.setAccessible(true);active.set(service,true);
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
            service.invalidatePlayerData(id);
        }
        assertFalse(service.shouldNotifyAutoDeposit(player));
    }
    @Test void nativePickupQueuesTheWholeStackWithoutCallingTheRepositoryOrMutatingTheEntity() throws Exception {
        when(matcher.matches(any(),eq(stack))).thenReturn(false);
        when(player.getCanPickupItems()).thenReturn(true);
        java.lang.reflect.Field active = WarehouseService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(service, true);
        WarehouseService pickupService = spy(service);
        org.bukkit.entity.Item item=mock(org.bukkit.entity.Item.class);
        ItemStack visible = new ItemStack(Material.STONE, 16);
        ItemStack original = new ItemStack(Material.STONE, 32);
        when(item.getItemStack()).thenReturn(visible);
        when(item.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(item.isValid()).thenReturn(true);
        when(item.getUniqueId()).thenReturn(UUID.randomUUID());
        var event=new org.bukkit.event.entity.EntityPickupItemEvent(player,item,16);
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        var queued = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        doReturn(true).when(pickupService).depositFromGroundAsync(eq(player), eq(item), any(ItemStack.class), any(),
            org.mockito.ArgumentMatchers.<BiPredicate<String, GroundDepositResult>>any());
        var world = mock(org.bukkit.World.class);
        when(player.getWorld()).thenReturn(world);
        when(item.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 64, 0));
        when(item.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 64, 0));
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);
            var factory = mock(org.bukkit.inventory.ItemFactory.class);
            when(factory.equals(null, null)).thenReturn(true);
            bukkit.when(org.bukkit.Bukkit::getItemFactory).thenReturn(factory);
            pickupService.onEntityPickupItem(event);
            assertTrue(event.isCancelled());
            verify(scheduler).runTask(any(JavaPlugin.class), queued.capture());
            verifyNoInteractions(repository);
            when(item.getItemStack()).thenReturn(original);
            queued.getValue().run();
        }
        verify(pickupService).depositFromGroundAsync(eq(player), eq(item), argThat(value -> value.getAmount() == 32), any(),
            org.mockito.ArgumentMatchers.<BiPredicate<String, GroundDepositResult>>any());
        verify(item,never()).setItemStack(any());
        verify(item,never()).remove();
    }
    @Test void previewCannotMakePersonalWarehouseWritable() {
        var state = WarehouseService.ViewState.preview(new WarehousePreview(id, false), "personal", "personal", "time");
        assertDoesNotThrow(() -> assertFalse(service.canModifyCurrent(player, state)));
    }

    @Test void screenSlotKeepsItsDisplayedIdentityInsteadOfRequeryingSortOrder() throws Exception {
        var state=WarehouseService.ViewState.initial(player,false,false,false,"money","time");
        var displayed=new WarehouseRepository.SlotItemRecord("personal",id.toString(),"personal",17,"hash","all","石头","STONE","","","","eA==","{}",64,1,1,"");
        state.displayedSlots.put(0,displayed);
        assertEquals(17,service.packetFactory.actualSlotFromDisplay(state,0));
        verify(repository,never()).loadSlotSummaries(anyString(),anyString(),anyString());
    }

    @Test void failedWarehouseWriteRestoresInventoryAfterWithdrawal() throws Exception {
        service=spy(service);
        doReturn(true).when(service).isSecondaryUnlocked(player);
        var state=WarehouseService.ViewState.initial(player,false,false,false,"money","time");
        state.setWarehouseId("personal");
        service.viewStates.put(id,state);
        var displayed=new WarehouseRepository.SlotItemRecord("personal",id.toString(),"personal",7,"hash","all","石头","STONE","","","","eA==","{}",64,1,1,"");
        state.displayedSlots.put(0,displayed);
        when(repository.loadSlot("personal",id.toString(),"personal",7)).thenReturn(Optional.of(displayed));
        PlayerInventory inventory=mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack existing=mock(ItemStack.class);
        when(existing.clone()).thenReturn(existing);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[]{existing});
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        when(stack.getMaxStackSize()).thenReturn(64);
        try(var serializer=mockStatic(ItemSerializer.class)) {
            serializer.when(()->ItemSerializer.deserialize(any(byte[].class))).thenReturn(stack);
            var method=WarehouseService.class.getDeclaredMethod("withdraw",Player.class,int.class,long.class,boolean.class,boolean.class);
            method.setAccessible(true);
            var exception=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(service,player,0,1L,false,false));
            assertInstanceOf(java.sql.SQLException.class,exception.getCause());
        }
        verify(inventory).addItem(any(ItemStack[].class));
        verify(inventory).setStorageContents(argThat(contents->contents.length==1 && contents[0]==existing));
    }
}
