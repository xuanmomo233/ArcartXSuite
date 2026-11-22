package xuanmo.arcartxsuite.pickup.service;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.bridge.*;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.item.ItemMatcherAPI;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LootScannerServiceTest {
    MockedStatic<Bukkit> bukkit;
    Player player;
    World world;
    PlayerInventory inventory;
    PluginManager manager;
    PacketBridgeAPI packets;
    VanillaItemNameBridge names;
    WarehouseAutoDepositable warehouse;
    LootScannerService service;
    List<Entity> entities = new ArrayList<>();
    List<Runnable> delayed = new ArrayList<>();
    Map<ItemStack,String> metadata = new IdentityHashMap<>();
    List<Integer> delivered = new ArrayList<>();
    UUID id = UUID.randomUUID();

    @BeforeEach void setup() throws Exception {
        bukkit = mockStatic(Bukkit.class);
        manager = mock(PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong())).thenAnswer(inv -> {
            delayed.add(inv.getArgument(1)); return mock(BukkitTask.class);
        });
        world = mock(World.class);
        Item returned = mock(Item.class);
        when(returned.isValid()).thenReturn(true);
        when(world.dropItem(any(Location.class), any(ItemStack.class), any())).thenReturn(returned);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getCanPickupItems()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world,0,64,0));
        when(player.getNearbyEntities(anyDouble(),anyDouble(),anyDouble())).thenAnswer(inv -> new ArrayList<>(entities));
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        packets = mock(PacketBridgeAPI.class);
        when(packets.isAvailable()).thenReturn(true);
        when(packets.openUi(any(),anyString())).thenReturn(true);
        when(packets.isUiOpen(any(),anyString())).thenReturn(true);
        when(packets.sendPacket(any(),anyString(),anyString(),any())).thenReturn(true);
        when(inventory.addItem(any(ItemStack[].class))).thenAnswer(inv -> {
            ItemStack stack = inv.getArgument(0); delivered.add(stack.getAmount()); return new HashMap<Integer,ItemStack>();
        });
        ItemBridgeAPI items = mock(ItemBridgeAPI.class);
        when(items.itemToPreviewJson(any())).thenAnswer(inv -> Optional.of(metadata.get(inv.getArgument(0))));
        names = mock(VanillaItemNameBridge.class);
        when(names.resolveDisplayName(any())).thenReturn("同名物品");
        warehouse = mock(WarehouseAutoDepositable.class);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("scanner.pickup-delay-ticks", 0);
        yaml.set("scanner.warehouse-auto-deposit", true);
        service = new LootScannerService(mock(JavaPlugin.class),Logger.getAnonymousLogger(),PickupModuleConfiguration.load(yaml,Logger.getAnonymousLogger()),
            null,packets,items,names,mock(ItemMatcherAPI.class),"CUSTOM:hud","CUSTOM:menu",()->warehouse,mock(xuanmo.arcartxsuite.api.message.MessageProvider.class));
        service.start();
    }
    @AfterEach void teardown() { bukkit.close(); }

    private final class TestStack extends ItemStack {
        final String key;
        TestStack(String key, int amount) { super(Material.DIAMOND,amount);this.key=key;metadata.put(this,key); }
        @Override public TestStack clone() { return new TestStack(key,getAmount()); }
        @Override public boolean isSimilar(ItemStack other) { return other instanceof TestStack test && key.equals(test.key); }
        @Override public org.bukkit.inventory.meta.ItemMeta getItemMeta() { return null; }
        @Override public boolean hasItemMeta() { return false; }
        @Override public int hashCode() { return key.hashCode(); }
    }
    ItemStack stack(String key,int amount) { return new TestStack(key,amount); }
    Item item(String key,int amount) {
        Item item = mock(Item.class);
        AtomicReference<ItemStack> contents = new AtomicReference<>(stack(key,amount));
        AtomicBoolean valid = new AtomicBoolean(true);
        when(item.getUniqueId()).thenReturn(UUID.randomUUID());
        when(item.isValid()).thenAnswer(inv -> valid.get());
        doAnswer(inv -> {valid.set(false);return null;}).when(item).remove();
        when(item.getItemStack()).thenAnswer(inv -> contents.get());
        doAnswer(inv -> {contents.set(inv.getArgument(0));return null;}).when(item).setItemStack(any());
        when(item.getLocation()).thenReturn(new Location(world,1,64,0));
        when(item.getWorld()).thenReturn(world);
        when(item.getTicksLived()).thenReturn(100);
        entities.add(item);
        return item;
    }
    void drain() throws Exception {
        Field jobs=LootScannerService.class.getDeclaredField("collectionJobs");jobs.setAccessible(true);
        for(int i=0;i<1000 && !((Map<?,?>)jobs.get(service)).isEmpty();i++) invoke("collectionTick",new Class<?>[]{});
        assertTrue(((Map<?,?>)jobs.get(service)).isEmpty());
    }
    @SuppressWarnings("unchecked") Map<String,Object> payload() throws Exception {
        return payload(player);
    }
    @SuppressWarnings("unchecked") Map<String,Object> payload(Player viewer) throws Exception {
        Field states=LootScannerService.class.getDeclaredField("playerStates");states.setAccessible(true);
        Object state=((Map<?,?>)states.get(service)).get(viewer.getUniqueId());
        Method build=LootScannerService.class.getDeclaredMethod("buildPayload",Player.class,state.getClass(),int.class);
        build.setAccessible(true);
        return (Map<String,Object>)build.invoke(service,viewer,state,5);
    }
    void scan() throws Exception { invoke("scanForPlayer",new Class<?>[]{Player.class},player); }
    Object invoke(String name,Class<?>[] types,Object... args) throws Exception {
        Method method = LootScannerService.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(service,args);
    }
    @SuppressWarnings("unchecked") String token() throws Exception {
        Field field=LootScannerService.class.getDeclaredField("sessionTokens");field.setAccessible(true);
        return ((Map<UUID,String>)field.get(service)).get(id);
    }
    void pick(Item item) throws Exception {
        scan(); assertTrue(service.handleInteractKeyFromHost(player));
        drain();
    }
    @Test void failedWarehouseResultCannotDeleteItems() throws Exception {
        Item item=item("plain",32);
        when(warehouse.depositToPersonalWarehouse(any(),any())).thenReturn(new WarehouseAutoDepositable.DepositResult(false,0,0,"拒绝"));
        pick(item);
        assertEquals(List.of(32),delivered);
        verify(item).remove();
    }
    @Test void partialWarehouseKeepsTheExactRemainderOnTheSameEntity() throws Exception {
        Item item=item("plain",32);
        when(warehouse.depositFromGroundAsync(any(),any(),any(),any(),any())).thenAnswer(invocation -> {
            BooleanSupplier take = invocation.getArgument(3);
            Predicate<WarehouseAutoDepositable.GroundDepositResult> completed = invocation.getArgument(4);
            assertTrue(take.getAsBoolean());
            assertEquals(32, item.getItemStack().getAmount());
            item.setItemStack(stack("plain", 16));
            assertTrue(completed.test(new WarehouseAutoDepositable.GroundDepositResult(true,false,16,true,"receipt")));
            return true;
        });
        var events=mock(xuanmo.arcartxsuite.api.capability.EventBusCapability.class);
        service.setEventBusProvider(()->events);
        pick(item);
        verify(item, never()).remove();
        verify(world, never()).dropItem(any(Location.class), any(), any());
        assertEquals(16, item.getItemStack().getAmount());
        assertTrue(delivered.isEmpty());
        verify(events).publish(eq("axs.pickup.item_pickup"),eq(player),argThat(payload->payload.get("amount").equals("16")));
    }

    @Test void asyncPreparationDoesNotTouchSourceAndConfirmedRemainderSurvivesQuit() throws Exception {
        Item item = item("plain", 32);
        AtomicReference<BooleanSupplier> take = new AtomicReference<>();
        AtomicReference<Predicate<WarehouseAutoDepositable.GroundDepositResult>> completed = new AtomicReference<>();
        when(warehouse.depositFromGroundAsync(any(),any(),any(),any(),any())).thenAnswer(invocation -> {
            take.set(invocation.getArgument(3));
            completed.set(invocation.getArgument(4));
            return true;
        });
        scan();
        assertTrue(service.handleInteractKeyFromHost(player));
        invoke("collectionTick", new Class<?>[]{});
        verify(item, never()).remove();
        assertTrue(delivered.isEmpty());
        assertTrue(take.get().getAsBoolean());
        assertEquals(32, item.getItemStack().getAmount());
        when(player.isOnline()).thenReturn(false);
        service.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(player, ""));
        var result = new WarehouseAutoDepositable.GroundDepositResult(true,false,16,true,"receipt");
        assertTrue(completed.get().test(result));
        verify(world, never()).dropItem(any(Location.class), any(), any());
        assertTrue(delivered.isEmpty());
    }

    @Test void unknownWarehouseCommitDoesNotReturnOrReplayReservedGroundItem() throws Exception {
        Item item = item("plain", 32);
        when(warehouse.depositFromGroundAsync(any(),any(),any(),any(),any())).thenAnswer(invocation -> {
            BooleanSupplier take = invocation.getArgument(3);
            Predicate<WarehouseAutoDepositable.GroundDepositResult> completed = invocation.getArgument(4);
            assertTrue(take.getAsBoolean());
            assertFalse(completed.test(new WarehouseAutoDepositable.GroundDepositResult(true,true,0,true,"receipt")));
            return true;
        });
        pick(item);
        verify(item, never()).remove();
        verify(world, never()).dropItem(any(Location.class), any(ItemStack.class), any());
        assertTrue(delivered.isEmpty());
    }
    @Test void mergedRowActuallyPicksAllMatchingEntities() throws Exception {
        Item first=item("plain",32);Item second=item("plain",16);
        when(warehouse.depositToPersonalWarehouse(any(),any())).thenAnswer(inv -> new WarehouseAutoDepositable.DepositResult(false,0,0,""));
        pick(first);
        assertEquals(48,delivered.stream().mapToInt(Integer::intValue).sum());
        verify(first).remove();verify(second).remove();
    }
    @Test void sameNameDifferentMetadataIsNotPickedAsOneGroup() throws Exception {
        Item first=item("enchanted",1);Item second=item("plain",1);
        when(warehouse.depositToPersonalWarehouse(any(),any())).thenReturn(new WarehouseAutoDepositable.DepositResult(false,0,0,""));
        pick(first);verify(second,never()).remove();assertEquals(List.of(1),delivered);
    }
    @Test void cancellationPreventsBothWarehouseAndInventoryWrites() throws Exception {
        Item item=item("plain",32);
        doAnswer(inv -> {((EntityPickupItemEvent)inv.getArgument(0)).setCancelled(true);return null;}).when(manager).callEvent(any());
        pick(item);verify(warehouse,never()).depositToPersonalWarehouse(any(),any());verify(item,never()).remove();assertTrue(delivered.isEmpty());
    }
    @Test void foreignOwnerAndPickupCooldownAreNotSelectable() throws Exception {
        Item foreign=item("owned",1);when(foreign.getOwner()).thenReturn(UUID.randomUUID());
        Item delay=item("delay",1);when(delay.getPickupDelay()).thenReturn(10);
        scan();assertFalse(service.handleInteractKeyFromHost(player));
    }

    @Test void sharedScanReusesPublicDropWithoutExposingOwnerOnlyDrop() throws Exception {
        Item sharedItem = item("public", 2);
        Item personal = item("personal", 3);
        when(personal.getOwner()).thenReturn(id);
        Player second = mock(Player.class);
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.isOnline()).thenReturn(true);
        when(second.getCanPickupItems()).thenReturn(true);
        when(second.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(second.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(entities);

        Map<UUID, ?> tickDrops = new HashMap<>();
        Class<?>[] input = {Player.class, Map.class};
        invoke("scanForPlayer", input, player, tickDrops);
        invoke("scanForPlayer", input, second, tickDrops);

        assertEquals(2, payload().get("totalCount"));
        Map<String, Object> secondView = payload(second);
        assertEquals(1, secondView.get("totalCount"));
        assertEquals(sharedItem.getUniqueId().toString(), secondView.get("item0Uuid"));
        verify(sharedItem, times(1)).getItemStack();
    }

    @Test void onlyPlayersWithOverlappingScanCirclesShareTheTickCache() throws Exception {
        ArrayDeque<Player> queue = new ArrayDeque<>();
        queue.add(viewerAt(world, 0));
        queue.add(viewerAt(world, 16));
        queue.add(viewerAt(world, 9));
        queue.add(viewerAt(mock(World.class), 0));
        Field scanQueue = LootScannerService.class.getDeclaredField("scanQueue");
        scanQueue.setAccessible(true);
        @SuppressWarnings("unchecked")
        ArrayDeque<Player> active = (ArrayDeque<Player>) scanQueue.get(service);

        active.addAll(queue);
        Class<?>[] args = {int.class, double.class};
        assertEquals(0L, invoke("overlappingScans", args, 2, 5d));
        assertEquals(0b0111L, invoke("overlappingScans", args, 4, 5d));

        active.removeFirst();
        active.removeFirst();
        assertEquals(0L, invoke("overlappingScans", args, 2, 5d));
    }

    private Player viewerAt(World viewerWorld, double x) {
        Player viewer = mock(Player.class);
        when(viewer.getLocation(any(Location.class))).thenAnswer(invocation -> {
            Location target = invocation.getArgument(0);
            target.setWorld(viewerWorld);
            target.setX(x);
            target.setY(64);
            target.setZ(0);
            return target;
        });
        return viewer;
    }

    @Test void changingSameEntityAmountOrMetadataRefreshesTheHud() throws Exception {
        when(names.resolveDisplayName(any())).thenAnswer(inv -> metadata.get(inv.getArgument(0)));
        Item item = item("old", 2);
        scan();
        String entityId = item.getUniqueId().toString();
        assertEquals("old", payload().get("item0Name"));

        item.setItemStack(stack("old", 4));
        scan();
        assertEquals(4L, payload().get("item0Amount"));
        assertEquals("old", payload().get("item0Name"));

        item.setItemStack(stack("renamed", 3));
        scan();
        Map<String, Object> renamed = payload();
        assertEquals(entityId, renamed.get("item0Uuid"));
        assertEquals(3L, renamed.get("item0Amount"));
        assertEquals("renamed", renamed.get("item0Name"));
        assertEquals("renamed", renamed.get("item0ItemJson"));
    }
    @Test void menuClosureInvalidatesOutstandingPick() throws Exception {
        Item item=item("plain",1);scan();service.handleHostAction(player,"details");String token=token();
        service.handleClientPacket(player,"close_menu",List.of(token));
        service.handleClientPacket(player,"pick",List.of(token,item.getUniqueId().toString()));
        verify(item,never()).remove();assertTrue(delivered.isEmpty());
    }
    @Test void openingHudPushesExistingUnchangedScanState() throws Exception {
        item("plain",1);scan();
        invoke("openHudForPlayer",new Class<?>[]{Player.class},player);
        for (Runnable task : new ArrayList<>(delayed)) task.run();
        verify(packets,atLeastOnce()).sendPacket(eq(player),eq("CUSTOM:hud"),eq("update"),any());
    }
    @Test void directCollectionDoesNotOpenMouseMenuAndWaitsForWorker() throws Exception {
        Item item=item("plain",4);
        when(warehouse.depositToPersonalWarehouse(any(),any())).thenReturn(new WarehouseAutoDepositable.DepositResult(false,0,4,""));
        assertTrue(service.handleInteractKeyFromHost(player));
        assertTrue(delivered.isEmpty());
        verify(packets,never()).openUi(player,"CUSTOM:menu");
        drain();assertEquals(List.of(4),delivered);verify(item).remove();
    }
    @Test void visibleRowsDoNotLimitTheActualListAndSelectionPages() throws Exception {
        for(int i=0;i<18;i++) item("different-"+i,1);
        scan();assertEquals(18,payload().get("totalCount"));assertEquals(5,payload().get("count"));
        for(int i=0;i<6;i++)service.handleHostAction(player,"next");
        assertEquals(7,payload().get("position"));assertEquals(1,payload().get("selectedIndex"));
        service.handleHostAction(player,"previous");assertEquals(6,payload().get("position"));
    }
    @Test void detailPagesWrapCorrectlyWhenTheLastPageIsNotFull() throws Exception {
        for(int i=0;i<18;i++)item("type-"+i,1);
        service.handleHostAction(player,"details");String token=token();
        service.handleClientPacket(player,"page_down",List.of(token));assertEquals(17,payload().get("position"));
        service.handleClientPacket(player,"page_down",List.of(token));assertEquals(1,payload().get("position"));
        service.handleClientPacket(player,"page_up",List.of(token));assertEquals(17,payload().get("position"));
    }
    @Test void newNearbyDropsAppendWithoutChangingTheSelectedGroup() throws Exception {
        Item first=item("first",1);item("second",1);
        scan();service.handleHostAction(player,"next");String selected=payload().get("item1Uuid").toString();
        Item newest=item("new",1);when(newest.getLocation()).thenReturn(new Location(world,0.1,64,0));
        scan();assertEquals(selected,payload().get("item1Uuid"));assertEquals(2,payload().get("position"));
        assertEquals(newest.getUniqueId().toString(),payload().get("item2Uuid"));
    }
    @Test void mergedGroupKeepsIdentityWhenItsFirstEntityDisappears() throws Exception {
        Item first=item("same",1);item("same",1);scan();Object group=payload().get("item0Uuid");
        when(first.isValid()).thenReturn(false);scan();
        assertEquals(group,payload().get("item0Uuid"));assertEquals(1L,payload().get("item0Amount"));
    }
    @Test void batchUsesOneSnapshotAndOneSummary() throws Exception {
        for(int i=0;i<20;i++)item("type-"+i,1);
        when(warehouse.depositToPersonalWarehouse(any(),any())).thenReturn(new WarehouseAutoDepositable.DepositResult(false,0,1,""));
        assertTrue(service.handleHostAction(player,"collect_all"));
        invoke("collectionTick",new Class<?>[]{});
        assertTrue(delivered.size()>0 && delivered.size()<=8);
        Item later=item("later",1);drain();
        assertEquals(20,delivered.size());verify(later,never()).remove();
        verify(packets,times(1)).sendPacket(eq(player),eq("CUSTOM:hud"),eq("result"),any());
        verify(player,never()).sendMessage(anyString());
    }
    @Test void repeatedKeysDoNotExtendTheSnapshotToNewQuantity() throws Exception {
        Item item=item("plain",32);
        when(warehouse.depositToPersonalWarehouse(any(),any())).thenReturn(new WarehouseAutoDepositable.DepositResult(false,0,0,""));
        service.handleInteractKeyFromHost(player);
        service.handleInteractKeyFromHost(player);
        verify(player,times(2)).getNearbyEntities(anyDouble(),anyDouble(),anyDouble());
        item.setItemStack(stack("plain",64));
        drain();assertEquals(List.of(32),delivered);assertEquals(32,item.getItemStack().getAmount());
    }
    @Test void offCancelsPendingCollectionAndRepeatedKeysDoNotDuplicateJobs() throws Exception {
        Item item=item("plain",32);
        service.handleInteractKeyFromHost(player);service.handleInteractKeyFromHost(player);
        service.setEnabled(id,false);drain();
        verify(item,never()).remove();assertTrue(delivered.isEmpty());
    }
    @Test void ownershipIsRecheckedWhenTheWorkerRuns() throws Exception {
        Item item=item("plain",1);service.handleInteractKeyFromHost(player);
        when(item.getOwner()).thenReturn(UUID.randomUUID());drain();
        verify(item,never()).remove();verify(warehouse,never()).depositToPersonalWarehouse(any(),any());
    }
    @Test void matchingBatchWithEmptyRulesDoesNotCollectAnything() throws Exception {
        YamlConfiguration yaml=new YamlConfiguration();yaml.set("scanner.pickup-delay-ticks",0);yaml.set("scanner.batch.mode","matching");
        Field config=LootScannerService.class.getDeclaredField("configuration");config.setAccessible(true);
        config.set(service,PickupModuleConfiguration.load(yaml,Logger.getAnonymousLogger()));
        item("plain",1);service.handleHostAction(player,"collect_all");drain();assertTrue(delivered.isEmpty());
        service.handleHostAction(player,"pick");drain();assertEquals(List.of(1),delivered);
    }
    @Test void matchingRulesRestrictOnlyTheBatchAction() throws Exception {
        YamlConfiguration yaml=new YamlConfiguration();yaml.set("scanner.pickup-delay-ticks",0);yaml.set("scanner.batch.mode","matching");
        yaml.set("scanner.batch.matcher.name-contains",List.of("wanted"));
        Field config=LootScannerService.class.getDeclaredField("configuration");config.setAccessible(true);
        config.set(service,PickupModuleConfiguration.load(yaml,Logger.getAnonymousLogger()));
        Field matcherField=LootScannerService.class.getDeclaredField("itemMatcher");matcherField.setAccessible(true);
        ItemMatcherAPI matcher=(ItemMatcherAPI)matcherField.get(service);
        when(matcher.matches(any(),any())).thenAnswer(inv -> !((xuanmo.arcartxsuite.api.item.ItemMatcher)inv.getArgument(0)).nameContains().isEmpty()
            && "wanted".equals(metadata.get(inv.getArgument(1))));
        Item wanted=item("wanted",1),other=item("other",1);
        service.handleHostAction(player,"collect_all");drain();
        verify(wanted).remove();verify(other,never()).remove();
        service.handleHostAction(player,"pick");drain();verify(other).remove();
    }
    @Test void fullVanillaMergeDoesNotLoseTheCapturedSourceQuota() throws Exception {
        Item target=item("same",32),source=item("same",32);
        when(warehouse.depositToPersonalWarehouse(any(),any())).thenReturn(new WarehouseAutoDepositable.DepositResult(false,0,0,""));
        service.handleHostAction(player,"collect_all");
        service.onItemMerge(new org.bukkit.event.entity.ItemMergeEvent(source,target));
        target.setItemStack(stack("same",64));source.remove();
        drain();assertEquals(64,delivered.stream().mapToInt(Integer::intValue).sum());
    }
    @Test void partialVanillaMergeSplitsRatherThanDropsTheSourceQuota() throws Exception {
        Item target=item("same",48),source=item("same",32);
        when(warehouse.depositToPersonalWarehouse(any(),any())).thenReturn(new WarehouseAutoDepositable.DepositResult(false,0,0,""));
        service.handleHostAction(player,"collect_all");
        service.onItemMerge(new org.bukkit.event.entity.ItemMergeEvent(source,target));
        target.setItemStack(stack("same",64));source.setItemStack(stack("same",16));
        drain();assertEquals(80,delivered.stream().mapToInt(Integer::intValue).sum());
    }
}
