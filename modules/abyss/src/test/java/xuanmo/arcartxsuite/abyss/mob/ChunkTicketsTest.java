package xuanmo.arcartxsuite.abyss.mob;

import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChunkTicketsTest {
    @Test void groupsShareOneLeaseAndOnlyTheLastReleaseRemovesIt() {
        var plugin = mock(Plugin.class);
        var world = mock(World.class);
        when(world.addPluginChunkTicket(1, 2, plugin)).thenReturn(true);
        when(world.removePluginChunkTicket(1, 2, plugin)).thenReturn(true);
        var tickets = new ChunkTickets(plugin);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(tickets.acquire(first, "a", world, 1, 2));
        assertTrue(tickets.acquire(first, "a", world, 1, 2));
        assertTrue(tickets.acquire(second, "b", world, 1, 2));
        verify(world, times(1)).addPluginChunkTicket(1, 2, plugin);
        tickets.releaseRun(first);
        verify(world, never()).removePluginChunkTicket(anyInt(), anyInt(), any());
        tickets.releaseRun(second);
        tickets.close();
        verify(world, times(1)).removePluginChunkTicket(1, 2, plugin);
    }

    @Test void borrowedTicketsArePreservedAndWorldsAreIndependent() {
        var plugin = mock(Plugin.class);
        var first = mock(World.class);
        var second = mock(World.class);
        when(first.getPluginChunkTickets(0, 0)).thenReturn(List.of(plugin));
        when(second.addPluginChunkTicket(0, 0, plugin)).thenReturn(true);
        when(second.removePluginChunkTicket(0, 0, plugin)).thenReturn(true);
        var tickets = new ChunkTickets(plugin);
        UUID run = UUID.randomUUID();
        assertTrue(tickets.acquire(run, "a", first, 0, 0));
        assertTrue(tickets.acquire(run, "a", second, 0, 0));
        tickets.close();
        verify(first, never()).removePluginChunkTicket(anyInt(), anyInt(), any());
        verify(second).removePluginChunkTicket(0, 0, plugin);
    }

    @Test void failedRemovalRemainsEligibleForShutdownRetry() {
        var plugin = mock(Plugin.class);
        var world = mock(World.class);
        when(world.addPluginChunkTicket(0, 0, plugin)).thenReturn(true);
        when(world.getPluginChunkTickets(0, 0)).thenReturn(List.of(plugin));
        when(world.removePluginChunkTicket(0, 0, plugin)).thenReturn(false, true);
        var tickets = new ChunkTickets(plugin);
        UUID run = UUID.randomUUID();
        tickets.acquire(run, "a", world, 0, 0);
        tickets.release(run, "a");
        tickets.close();
        verify(world, times(2)).removePluginChunkTicket(0, 0, plugin);
    }
}
