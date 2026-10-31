package xuanmo.arcartxsuite.abyss.procgen;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.abyss.design.DesignWriter;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.service.AbyssService;
import xuanmo.arcartxsuite.abyss.template.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedAssetSaveTest {
    @TempDir Path dir;
    static Object field(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    static void set(Object object,String name,Object value)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    @SuppressWarnings("unchecked")
    @Test void savingOnePlacedCopyDoesNotAuthoriseAnOlderSiblingOverwrite()throws Exception {
        var library=new RoomSetLibrary(dir.toFile(),Logger.getAnonymousLogger());Path root=Files.createDirectories(library.dir("example").toPath());
        var original=RegionTemplate.of(3,4,3,List.of("minecraft:air"),new byte[4][3*3*2]);
        original.save(root.resolve("room.region.yml").toFile());Files.writeString(root.resolve("room.yml"),"type: combat\n");
        var asset=ProcGenRoomAsset.load("room",root.resolve("room.yml").toFile(),null,original);
        List<ProcGenMaterializer.RoomRuntime> rooms=new ArrayList<>();
        for(int x:List.of(0,10)) {
            var room=new ProcGenMaterializer.RoomRuntime();room.minX=x;room.maxX=x+2;room.minY=64;room.maxY=67;room.minZ=0;room.maxZ=2;
            room.placed=new LayoutGenerator.PlacedRoom(asset,0,0,0,x,64,0,3,4,3,true);rooms.add(room);
        }
        var manager=new ProcGenManager(mock(AbyssService.class),library,Logger.getAnonymousLogger());
        Class<?> type=Arrays.stream(ProcGenManager.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("InstState")).findFirst().orElseThrow();
        var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object state=constructor.newInstance();
        var cfg=new YamlConfiguration();cfg.set("room-set","example");set(state,"cfg",ProcGenConfig.load(cfg,"test"));set(state,"rooms",rooms);
        Map<String,String> baseline=Map.of("room.yml",DesignWriter.hash(root.resolve("room.yml")),"room.region.yml",DesignWriter.hash(root.resolve("room.region.yml")));
        var baselines=(Map<ProcGenMaterializer.RoomRuntime,Map<String,String>>)field(state,"roomBaselines");
        rooms.forEach(room->baselines.put(room,baseline));
        var instance=mock(AbyssInstance.class);when(instance.getInstanceId()).thenReturn(UUID.randomUUID());
        ((Map<UUID,Object>)field(manager,"states")).put(instance.getInstanceId(),state);
        World world=mock(World.class);Block block=mock(Block.class);BlockData data=mock(BlockData.class);when(data.getAsString()).thenReturn("minecraft:stone");
        when(block.getBlockData()).thenReturn(data);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);when(instance.getWorld()).thenReturn(world);
        Server server=mock(Server.class);when(server.createBlockData(anyString())).thenAnswer(call->{
            BlockData result=mock(BlockData.class);when(result.getAsString()).thenReturn(call.getArgument(0));return result;
        });
        Field serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);Object previous=serverField.get(null);
        try {
            serverField.set(null,server);
            var first=manager.captureDesignRoom(instance,new Location(world,1,65,1));first.save();manager.acknowledgeDesignRoom(instance,first);
            String published=DesignWriter.hash(root.resolve("room.region.yml"));
            var oldCopy=manager.captureDesignRoom(instance,new Location(world,11,65,1));
            assertThrows(java.io.IOException.class,oldCopy::save);assertEquals(published,DesignWriter.hash(root.resolve("room.region.yml")));
        } finally {serverField.set(null,previous);}
    }
    @Test void missingGeometryCannotFallBackToAnEmptyPlayableDungeon() {
        var library=mock(RoomSetLibrary.class);when(library.byKind(anyString())).thenReturn(Map.of());
        var instance=mock(AbyssInstance.class);var template=mock(AbyssTemplate.class);when(instance.getTemplate()).thenReturn(template);
        var yaml=new YamlConfiguration();yaml.set("room-set","missing");when(template.procgen()).thenReturn(ProcGenConfig.load(yaml,"test"));
        var manager=new ProcGenManager(mock(AbyssService.class),library,Logger.getAnonymousLogger());
        assertThrows(IllegalStateException.class,()->manager.materialize(instance,mock(World.class)));
    }
}
