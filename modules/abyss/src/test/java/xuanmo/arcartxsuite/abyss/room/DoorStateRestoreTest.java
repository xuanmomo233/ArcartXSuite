package xuanmo.arcartxsuite.abyss.room;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class DoorStateRestoreTest {
    @SuppressWarnings("unchecked")
    @Test void lockedDoorRestoresItsFullBlockStateInsteadOfOnlyItsMaterial()throws Exception {
        var manager=mock(RoomManager.class,withSettings().defaultAnswer(CALLS_REAL_METHODS).mockMaker(org.mockito.MockMakers.INLINE));
        var room=mock(RoomManager.RoomRuntime.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        Class<?> type=Arrays.stream(RoomManager.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("DoorRuntime")).findFirst().orElseThrow();
        var constructor=type.getDeclaredConstructor(String.class);constructor.setAccessible(true);Object door=constructor.newInstance("BARRIER");
        var world=mock(World.class);var block=mock(Block.class);var data=mock(BlockData.class);var copy=mock(BlockData.class);
        when(data.clone()).thenReturn(copy);when(block.getBlockData()).thenReturn(data);
        var location=new Location(world,1,65,1);when(block.getLocation()).thenReturn(location);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
        var blocks=type.getDeclaredField("blocks");blocks.setAccessible(true);((List<Block>)blocks.get(door)).add(block);
        var doors=RoomManager.RoomRuntime.class.getDeclaredField("doors");doors.setAccessible(true);doors.set(room,List.of(door));
        var lock=RoomManager.class.getDeclaredMethod("lockDoors",RoomManager.RoomRuntime.class);lock.setAccessible(true);lock.invoke(manager,room);
        var unlock=RoomManager.class.getDeclaredMethod("unlockDoors",RoomManager.RoomRuntime.class);unlock.setAccessible(true);unlock.invoke(manager,room);
        verify(block).setType(Material.BARRIER,false);verify(block).setBlockData(copy,false);
    }
}
