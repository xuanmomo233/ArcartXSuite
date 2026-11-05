package xuanmo.arcartxsuite.pickup.service;

import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.bridge.*;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PickupNotificationLifecycleTest {
    MockedStatic<Bukkit> bukkit;
    List<Runnable> tasks = new ArrayList<>();
    PickupService service;
    PacketBridgeAPI bridge;
    Player player;
    ItemStack stack;
    UUID id = UUID.randomUUID();
    @BeforeEach void setup() {
        bukkit=mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        BukkitScheduler scheduler=mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskLater(any(),any(Runnable.class),anyLong())).thenAnswer(inv->{tasks.add(inv.getArgument(1));return mock(BukkitTask.class);});
        player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        bukkit.when(()->Bukkit.getPlayer(id)).thenReturn(player);
        bridge=mock(PacketBridgeAPI.class);
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.openUiWithCallback(any(),any(),any())).thenReturn(true);
        ItemBridgeAPI items=mock(ItemBridgeAPI.class);
        when(items.itemToPreviewJson(any())).thenReturn(Optional.of("{}"));
        stack=mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.DIAMOND);
        service=new PickupService(mock(JavaPlugin.class),Logger.getAnonymousLogger(),PickupModuleConfiguration.load(new YamlConfiguration(),Logger.getAnonymousLogger()),bridge,items,"CUSTOM:hud");
        service.start();
    }
    @AfterEach void teardown(){bukkit.close();}
    @Test void startupRetryDoesNotReopenDisabledHud() {
        service.setEnabled(id,false);
        for(Runnable task:new ArrayList<>(tasks))task.run();
        verify(bridge,never()).openUiWithCallback(any(),any(),any());
    }
    @Test void repeatedPickupsDuringOpeningDoNotRepeatedlyResetHud() {
        tasks.get(0).run();
        assertTrue(service.notifyPickup(player,stack,1));
        assertTrue(service.notifyPickup(player,stack,1));
        verify(bridge,times(1)).openUiWithCallback(eq(player),eq("CUSTOM:hud"),any());
    }
    @Test void disabledSessionDiscardsQueuedAndScheduledNotifications() {
        tasks.get(0).run();
        service.notifyPickup(player,stack,1);
        service.setEnabled(id,false);
        for(Runnable task:new ArrayList<>(tasks))task.run();
        verify(bridge,never()).sendPacket(any(),any(),any(),any());
    }
    @Test void shutdownAlsoClosesHudStillAwaitingConfirmation() {
        tasks.get(0).run();
        service.shutdown();
        verify(bridge).closeUi(player,"CUSTOM:hud");
        for(Runnable task:new ArrayList<>(tasks))task.run();
        verify(bridge,times(1)).openUiWithCallback(eq(player),eq("CUSTOM:hud"),any());
    }
    @Test void notificationsWaitForActualUiOpenCallback() {
        service.notifyPickup(player,stack,1);
        var callback=org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(bridge).openUiWithCallback(eq(player),eq("CUSTOM:hud"),callback.capture());
        verify(bridge,never()).sendPacket(any(),any(),any(),any());
        callback.getValue().run();
        for(Runnable task:new ArrayList<>(tasks))task.run();
        verify(bridge).sendPacket(eq(player),eq("CUSTOM:hud"),eq("pick"),any());
    }
}
