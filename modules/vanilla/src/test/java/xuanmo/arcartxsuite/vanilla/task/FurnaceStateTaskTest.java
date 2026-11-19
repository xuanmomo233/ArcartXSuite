package xuanmo.arcartxsuite.vanilla.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.recipe.WorkshopType;

class FurnaceStateTaskTest {
    private static final WorkshopType FURNACE = WorkshopType.FURNACE;
    private static final WorkshopType BREWING = WorkshopType.BREWING;

    @Test
    void sharesOneReadPerDeviceAndKeepsEachViewerFuelHistory() {
        try (var fixture = new Fixture()) {
            Furnace shared = mock(Furnace.class);
            when(shared.getBurnTime()).thenReturn((short) 10);
            when(shared.getCookTimeTotal()).thenReturn(200);
            Block sharedBlock = fixture.block(1, Material.FURNACE, shared);
            Block distantBlock = fixture.block(35, Material.FURNACE, mock(Furnace.class));
            Player early = fixture.viewer(FURNACE, 1);
            Player distant = fixture.viewer(FURNACE, 35);
            fixture.task.start();
            fixture.advance(2);
            assertEquals(10, fixture.last(early).number("burnTimeTotal"));
            assertEquals(1, fixture.last(distant).number("burnTimeTotal"));

            Player late = fixture.viewer(FURNACE, 1);
            fixture.task.track(late, late.getOpenInventory());
            when(shared.getBurnTime()).thenReturn((short) 7);
            fixture.advance(2);

            assertEquals(2, fixture.sentTo(early).size());
            assertEquals(1, fixture.sentTo(late).size());
            assertEquals(1, fixture.sentTo(distant).size());
            assertEquals(7, fixture.last(early).number("burnTime"));
            assertEquals(10, fixture.last(early).number("burnTimeTotal"));
            assertEquals(7, fixture.last(late).number("burnTimeTotal"));
            assertEquals(200, fixture.last(late).number("cookTimeTotal"));
            verify(sharedBlock, times(2)).getState();
            verify(distantBlock, times(2)).getState();
        }
    }

    @Test
    void sendsBrewingCompletionOnceAndStillReportsAnIdleFuelChange() {
        try (var fixture = new Fixture()) {
            BrewingStand stand = mock(BrewingStand.class);
            when(stand.getFuelLevel()).thenReturn(3);
            fixture.block(1, Material.BREWING_STAND, stand);
            Player player = fixture.viewer(BREWING, 1);
            fixture.task.start();
            fixture.advance(2);
            when(stand.getBrewingTime()).thenReturn(399);
            fixture.advance(2);
            when(stand.getBrewingTime()).thenReturn(0);
            fixture.advance(6);
            when(stand.getFuelLevel()).thenReturn(4);
            fixture.advance(2);

            List<Sent> frames = fixture.sentTo(player);
            assertEquals(List.of(0, 399, 0, 0), frames.stream().map(packet -> packet.number("remaining")).toList());
            assertEquals(List.of(3, 3, 3, 4), frames.stream().map(packet -> packet.number("fuel")).toList());
            for (Sent frame : frames) {
                assertEquals(BREWING.uiId(), frame.uiId());
                assertEquals("brewing_state", frame.handler());
                assertEquals(Set.of("remaining", "total", "fuel"), frame.fields().keySet());
                assertEquals(400, frame.number("total"));
            }
        }
    }

    @Test
    void waitsForTheMatchingArcartXScreenAndRetriesARejectedFirstSend() {
        try (var fixture = new Fixture()) {
            Block block = fixture.block(1, Material.FURNACE, mock(Furnace.class));
            Player player = fixture.viewer(FURNACE, 1);
            fixture.ready.remove(new Screen(player, FURNACE.uiId()));
            fixture.ready.add(new Screen(player, BREWING.uiId()));
            fixture.task.start();
            fixture.advance(4);
            assertEquals(List.of(), fixture.sentTo(player));
            verify(block, never()).getState();

            fixture.ready.add(new Screen(player, FURNACE.uiId()));
            fixture.deliveryAccepted = false;
            fixture.advance(2);
            fixture.deliveryAccepted = true;
            fixture.advance(6);

            List<Sent> sends = fixture.sentTo(player);
            assertEquals(2, sends.size());
            assertEquals(sends.get(0), sends.get(1));
            assertEquals("furnace_state", sends.get(0).handler());
            assertEquals(Set.of("burnTime", "burnTimeTotal", "cookTime", "cookTimeTotal", "burning"),
                sends.get(0).fields().keySet());
        }
    }

    @Test
    void ignoresLateCloseOfOldViewAndRefreshesOnReopen() {
        try (var fixture = new Fixture()) {
            fixture.block(1, Material.FURNACE, mock(Furnace.class));
            fixture.block(2, Material.FURNACE, mock(Furnace.class));
            Player player = fixture.viewer(FURNACE, 1);
            InventoryView old = player.getOpenInventory();
            fixture.task.start();
            fixture.advance(2);

            InventoryView replacement = fixture.view(FURNACE, 2);
            when(player.getOpenInventory()).thenReturn(replacement);
            fixture.task.track(player, replacement);
            fixture.task.untrack(player.getUniqueId(), old);
            fixture.advance(2);
            assertEquals(2, fixture.sentTo(player).size());

            fixture.task.untrack(player.getUniqueId(), replacement);
            fixture.advance(4);
            assertEquals(2, fixture.sentTo(player).size());
            InventoryView reopened = fixture.view(FURNACE, 2);
            when(player.getOpenInventory()).thenReturn(reopened);
            fixture.task.track(player, reopened);
            fixture.advance(2);
            assertEquals(3, fixture.sentTo(player).size());
        }
    }

    @Test
    void avoidsReadingAnUnloadedBlockOrSendingInvalidState() {
        try (var fixture = new Fixture()) {
            Block unloaded = fixture.block(49, Material.FURNACE, mock(Furnace.class));
            when(fixture.world.isChunkLoaded(3, 0)).thenReturn(false);
            fixture.viewer(FURNACE, 49);
            Block invalid = fixture.block(1, Material.STONE, mock(BlockState.class));
            fixture.viewer(FURNACE, 1);
            fixture.task.start();
            fixture.advance(4);

            verify(unloaded, never()).getState();
            verify(invalid, never()).getState();
            assertEquals(List.of(), fixture.sent);
        }
    }

    private record Screen(Player player, String uiId) {}

    private record Sent(Player player, String uiId, String handler, Map<?, ?> fields) {
        int number(String field) {
            return ((Number) fields.get(field)).intValue();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world = mock(World.class);
        final Plugin plugin = mock(Plugin.class);
        final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final Map<Integer, Block> blocks = new HashMap<>();
        final Map<Integer, Inventory> inventories = new HashMap<>();
        final Map<UUID, Player> online = new HashMap<>();
        final Set<Screen> ready = new HashSet<>();
        final List<Sent> sent = new ArrayList<>();
        final FurnaceStateTask task;
        Runnable tick;
        long now;
        long next;
        long period;
        boolean deliveryAccepted = true;

        Fixture() {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            BukkitTask scheduled = mock(BukkitTask.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(ignored -> List.copyOf(online.values()));
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.get(call.getArgument(0)));
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
                tick = call.getArgument(1);
                next = now + Math.max(1, (long) call.getArgument(2));
                period = call.getArgument(3);
                return scheduled;
            });
            UUID worldId = new UUID(0, 100);
            when(world.getUID()).thenReturn(worldId);
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> blocks.get(call.getArgument(0)));
            when(world.getBlockAt(any(Location.class))).thenAnswer(call -> blocks.get(call.<Location>getArgument(0).getBlockX()));
            when(bridge.isAvailable()).thenReturn(true);
            when(bridge.isUiOpen(any(Player.class), anyString())).thenAnswer(call ->
                ready.contains(new Screen(call.getArgument(0), call.getArgument(1))));
            when(bridge.sendPacket(any(Player.class), anyString(), anyString(), any())).thenAnswer(call -> {
                sent.add(new Sent(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                    Map.copyOf(call.<Map<?, ?>>getArgument(3))));
                return deliveryAccepted;
            });
            task = new FurnaceStateTask(plugin, bridge, Map.of(FURNACE, FURNACE.uiId(), BREWING, BREWING.uiId()),
                VanillaConfiguration.load(new YamlConfiguration()));
        }

        Block block(int x, Material type, BlockState state) {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(type);
            when(block.getState()).thenReturn(state);
            if (state instanceof Furnace furnace) {
                FurnaceInventory inventory = (FurnaceInventory) inventory(FURNACE, x);
                when(furnace.getInventory()).thenReturn(inventory);
            } else if (state instanceof BrewingStand stand) {
                BrewerInventory inventory = (BrewerInventory) inventory(BREWING, x);
                when(stand.getInventory()).thenReturn(inventory);
            }
            blocks.put(x, block);
            return block;
        }

        Player viewer(WorkshopType type, int x) {
            Player player = mock(Player.class);
            UUID id = new UUID(0, online.size() + 1);
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            InventoryView view = view(type, x);
            when(player.getOpenInventory()).thenReturn(view);
            online.put(id, player);
            ready.add(new Screen(player, type.uiId()));
            return player;
        }

        InventoryView view(WorkshopType type, int x) {
            Inventory top = inventory(type, x);
            InventoryView view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(top);
            when(view.getType()).thenReturn(type == BREWING ? InventoryType.BREWING : InventoryType.FURNACE);
            return view;
        }

        private Inventory inventory(WorkshopType type, int x) {
            return inventories.computeIfAbsent(x, coordinate -> {
                Inventory top = type == BREWING ? mock(BrewerInventory.class) : mock(FurnaceInventory.class);
                when(top.getSize()).thenReturn(type.size());
                when(top.getLocation()).thenReturn(new Location(world, coordinate, 64, 1));
                return top;
            });
        }

        void advance(int ticks) {
            for (int elapsed = 0; elapsed < ticks; elapsed++) {
                if (++now == next && tick != null) {
                    next += period;
                    tick.run();
                }
            }
        }

        List<Sent> sentTo(Player player) {
            return sent.stream().filter(packet -> packet.player() == player).toList();
        }

        Sent last(Player player) {
            List<Sent> frames = sentTo(player);
            return frames.get(frames.size() - 1);
        }

        @Override
        public void close() {
            task.stop();
            bukkit.close();
        }
    }
}
