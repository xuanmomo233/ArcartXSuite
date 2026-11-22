package xuanmo.arcartxsuite.vanilla.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.WorkshopType;

class WorkbenchStateSyncTest {
    @Test
    void doubleChestRetriesTheFirstStateAndSkipsUnchangedUpdatesAndBlockReads() {
        try (var fixture = new Fixture(WorkshopType.CONTAINER, InventoryType.CHEST, mock(Inventory.class), 54);
             var service = new UtilityWorkbenchService(fixture.plugin, fixture.bridge, fixture.config, null,
                 WorkshopType::uiId, ignored -> false)) {
            var contents = new ItemStack[54];
            contents[0] = new ItemStack(Material.STONE, 5);
            contents[53] = new ItemStack(Material.COAL, 64);
            when(fixture.inventory.getContents()).thenReturn(contents);

            fixture.accepted = false;
            service.handle(fixture.player, fixture.type, "utility_open", List.of());
            fixture.accepted = true;
            fixture.poll.run();
            fixture.poll.run();
            assertEquals(2, fixture.packets.size());
            assertEquals(fixture.packets.get(0), fixture.packets.get(1));
            assertEquals("2 / 54 格", fixture.packets.get(1).get("occupancy"));
            assertEquals("69 个物品", fixture.packets.get(1).get("amount"));
            verify(fixture.inventory, never()).getLocation();

            contents[53] = null;
            fixture.poll.run();
            assertEquals("5 个物品", fixture.packets.get(2).get("amount"));

            when(fixture.bridge.isUiOpen(fixture.player, fixture.type.uiId())).thenReturn(false);
            fixture.poll.run();
            when(fixture.bridge.isUiOpen(fixture.player, fixture.type.uiId())).thenReturn(true);
            fixture.poll.run();
            assertEquals(4, fixture.packets.size());
            assertEquals(fixture.packets.get(2), fixture.packets.get(3));
        }
    }

    @Test
    void failedAnvilRenameAcknowledgementIsRetriedThenStops() {
        try (var fixture = new Fixture(WorkshopType.ANVIL, InventoryType.ANVIL, mock(AnvilInventory.class), 3);
             var service = new EquipmentWorkbenchService(fixture.plugin, fixture.bridge, null, fixture.config, null,
                 WorkshopType::uiId, ignored -> true)) {
            service.handle(fixture.player, fixture.type, "equipment_open", List.of());
            String token = (String) fixture.packets.get(0).get("itemToken");
            fixture.poll.run();
            assertEquals(1, fixture.packets.size());

            fixture.accepted = false;
            service.handle(fixture.player, fixture.type, "rename", List.of("rename", "新名称", token, "7"));
            assertEquals("7", fixture.packets.get(1).get("ack"));
            fixture.accepted = true;
            fixture.poll.run();
            fixture.poll.run();
            assertEquals(3, fixture.packets.size());
            assertEquals(fixture.packets.get(1), fixture.packets.get(2));

            when(fixture.player.getLevel()).thenReturn(8);
            fixture.poll.run();
            assertEquals(8, fixture.packets.get(3).get("level"));
            assertEquals("", fixture.packets.get(3).get("ack"));
        }
    }

    @Test
    void artisanRetriesBeforeTreatingUnchangedStateAsDelivered() {
        try (var fixture = new Fixture(WorkshopType.GRINDSTONE, InventoryType.GRINDSTONE, mock(Inventory.class), 3);
             var service = new ArtisanWorkbenchService(fixture.plugin, fixture.bridge, null, null, fixture.config, null,
                 WorkshopType::uiId, ignored -> false)) {
            fixture.accepted = false;
            service.handle(fixture.player, fixture.type, "artisan_open", List.of());
            fixture.accepted = true;
            fixture.poll.run();
            fixture.poll.run();
            assertEquals(2, fixture.packets.size());
            assertEquals(fixture.packets.get(0), fixture.packets.get(1));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final Plugin plugin = mock(Plugin.class);
        final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        final VanillaConfiguration config = mock(VanillaConfiguration.class);
        final Player player = mock(Player.class);
        final Inventory inventory;
        final WorkshopType type;
        final List<Map<?, ?>> packets = new ArrayList<>();
        Runnable poll;
        boolean accepted = true;

        Fixture(WorkshopType type, InventoryType nativeType, Inventory inventory, int slots) {
            this.type = type;
            this.inventory = inventory;
            var id = UUID.randomUUID();
            var view = mock(InventoryView.class);
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            when(player.getOpenInventory()).thenReturn(view);
            when(view.getPlayer()).thenReturn(player);
            when(view.getTopInventory()).thenReturn(inventory);
            when(view.getTitle()).thenReturn("共享箱子");
            when(view.getType()).thenReturn(nativeType);
            when(inventory.getType()).thenReturn(nativeType);
            when(inventory.getSize()).thenReturn(slots);
            when(config.enabled(type)).thenReturn(true);
            when(config.interval(type)).thenReturn(2);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());

            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            BukkitTask task = mock(BukkitTask.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(2L), eq(2L))).thenAnswer(call -> {
                poll = call.getArgument(1);
                return task;
            });
            when(bridge.isUiOpen(player, type.uiId())).thenReturn(true);
            when(bridge.sendPacket(eq(player), eq(type.uiId()), anyString(), any())).thenAnswer(call -> {
                if (!"ui_notice".equals(call.getArgument(2))) packets.add(Map.copyOf(call.<Map<?, ?>>getArgument(3)));
                return accepted;
            });
        }

        @Override public void close() { bukkit.close(); }
    }
}
