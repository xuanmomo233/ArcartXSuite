package xuanmo.arcartxsuite.prop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.SplashPotion;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Consumer;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI.PotionDrink;
import xuanmo.arcartxsuite.api.bridge.PropPlayerHandle;
import xuanmo.arcartxsuite.api.message.MessageProvider;

class PropQuickSlotUseTest {
    @Test
    void drinkingWaitsForCompletionAndReturnsTheOriginalRemainder() {
        try (Fixture fixture = new Fixture(Material.POTION)) {
            PotionDrink drink = mock(PotionDrink.class);
            when(fixture.bridge.preparePotionDrink(eq(fixture.player), any())).thenReturn(Optional.of(drink));
            ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
            when(drink.finish()).thenReturn(bottle);

            fixture.press();
            fixture.press();
            assertEquals(2, fixture.slot.get().getAmount());
            verify(drink, never()).finish();
            verify(fixture.scheduler).runTaskLater(eq(fixture.plugin), any(Runnable.class), eq(32L));
            fixture.completion.run();

            assertEquals(1, fixture.slot.get().getAmount());
            verify(drink).finish();
            verify(fixture.inventory).addItem(bottle);
        }
    }

    @Test
    void slotChangesAndCancelledConsumeEventsPreserveTheItem() {
        try (Fixture fixture = new Fixture(Material.POTION)) {
            PotionDrink drink = mock(PotionDrink.class);
            when(fixture.bridge.preparePotionDrink(eq(fixture.player), any())).thenReturn(Optional.of(drink));
            fixture.press();
            fixture.slot.set(new ItemStack(Material.SPLASH_POTION));
            fixture.completion.run();
            verify(fixture.events, never()).callEvent(any());

            fixture.slot.set(new ItemStack(Material.POTION, 2));
            fixture.press();
            doAnswer(call -> {
                PlayerItemConsumeEvent event = call.getArgument(0);
                event.setCancelled(true);
                return null;
            }).when(fixture.events).callEvent(any(PlayerItemConsumeEvent.class));
            fixture.completion.run();

            assertEquals(2, fixture.slot.get().getAmount());
            verify(drink, never()).finish();
        }
    }

    @Test
    void projectileReceivesTheActualSlotPotionAndCancellationRefundsIt() {
        try (Fixture fixture = new Fixture(Material.LINGERING_POTION)) {
            LingeringPotion projectile = mock(LingeringPotion.class);
            when(fixture.world.spawn(any(Location.class), eq(LingeringPotion.class), any())).thenAnswer(call -> {
                Consumer<LingeringPotion> init = call.getArgument(2);
                init.accept(projectile);
                return projectile;
            });

            fixture.press();
            verify(projectile).setShooter(fixture.player);
            verify(projectile).setItem(new ItemStack(Material.LINGERING_POTION));
            assertEquals(2, fixture.slot.get().getAmount());

            when(projectile.isValid()).thenReturn(true);
            fixture.press();
            assertEquals(1, fixture.slot.get().getAmount());
        }
    }

    @Test
    void failedSlotWritePreventsLaunchingAPotion() {
        try (Fixture fixture = new Fixture(Material.SPLASH_POTION)) {
            when(fixture.handle.trySetSlotItemStack(eq("PotionSlot"), any())).thenReturn(false);
            fixture.press();
            assertEquals(2, fixture.slot.get().getAmount());
            verify(fixture.world, never()).spawn(any(Location.class), eq(SplashPotion.class), any());
            verify(fixture.handle).syncSlotToClient("PotionSlot");
        }
    }

    @Test
    void mmoItemsCanRefundAReservedDoseWithoutModifyingThePlayerHand() {
        try (Fixture fixture = new Fixture(Material.PAPER)) {
            when(fixture.bridge.useMmoItem(eq(fixture.player), any(), any(), any())).thenAnswer(call -> {
                java.util.function.BooleanSupplier take = call.getArgument(2);
                Runnable refund = call.getArgument(3);
                org.junit.jupiter.api.Assertions.assertTrue(take.getAsBoolean());
                assertEquals(1, fixture.slot.get().getAmount());
                refund.run();
                return PropBridgeAPI.MmoClickResult.REJECTED;
            });
            fixture.service.mmoItem(fixture.player, fixture.handle, "PotionSlot", fixture.slot.get());
            assertEquals(2, fixture.slot.get().getAmount());
            verify(fixture.inventory, never()).setItemInMainHand(any());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final org.mockito.MockedStatic<AuthAccess> auth = mockStatic(AuthAccess.class);
        private final Player player = mock(Player.class);
        private final World world = mock(World.class);
        private final JavaPlugin plugin = mock(JavaPlugin.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final PluginManager events = mock(PluginManager.class);
        private final PlayerInventory inventory = mock(PlayerInventory.class);
        private final PropBridgeAPI bridge = mock(PropBridgeAPI.class);
        private final PropPlayerHandle handle = mock(PropPlayerHandle.class);
        private final AtomicReference<ItemStack> slot;
        private final PropQuickSlotUse service;
        private Runnable completion;

        private Fixture(Material material) {
            slot = new AtomicReference<>(new ItemStack(material, 2));
            ItemFactory factory = mock(ItemFactory.class);
            when(factory.equals((ItemMeta) null, null)).thenReturn(true);
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getPluginManager).thenReturn(events);
            UUID playerId = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(playerId);
            auth.when(() -> AuthAccess.snapshot(playerId)).thenReturn(new AuthSnapshot(playerId, playerId, 1, playerId,
                AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "fixture", Set.of(), ""));
            when(player.getWorld()).thenReturn(world);
            when(player.getEyeLocation()).thenAnswer(call -> new Location(world, 0, 82.62, 0));
            when(player.getLocation()).thenAnswer(call -> new Location(world, 0, 81, 0));
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getInventory()).thenReturn(inventory);
            when(handle.getSlotItemStack("PotionSlot")).thenAnswer(call -> slot.get());
            when(handle.trySetSlotItemStack(eq("PotionSlot"), any())).thenAnswer(call -> {
                slot.set(call.getArgument(1));
                return true;
            });
            when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(32L))).thenAnswer(call -> {
                completion = call.getArgument(1);
                return mock(BukkitTask.class);
            });
            var messages = mock(MessageProvider.class);
            when(messages.get(any(String.class), any(Object[].class))).thenAnswer(call -> call.getArgument(0));
            service = new PropQuickSlotUse(plugin, bridge, messages, ignored -> true, Logger.getLogger("PropQuickSlotUseTest"));
        }

        private void press() {
            service.potion(player, handle, "PotionSlot", slot.get());
        }

        @Override
        public void close() {
            service.shutdown();
            auth.close();
            bukkit.close();
        }
    }
}
