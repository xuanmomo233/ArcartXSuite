package xuanmo.arcartxsuite.abyss.world;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeWorldRemovalTest {
    @TempDir Path dir;
    Field serverField;Object old;Server server;World world;AbyssWorldPool pool;
    final UUID id=UUID.randomUUID();
    @BeforeEach void setup()throws Exception {
        server=mock(Server.class);world=mock(World.class);when(world.getName()).thenReturn("axs_abyss_test");
        when(server.isPrimaryThread()).thenReturn(true);
        when(server.getWorld("axs_abyss_test")).thenReturn(world);
        serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);old=serverField.get(null);serverField.set(null,server);
        var yaml=new YamlConfiguration();yaml.set("abyss.nms-fast-load",false);
        pool=new AbyssWorldPool(null,AbyssModuleConfiguration.load(yaml).abyss(),Logger.getAnonymousLogger());
        map("instanceWorldDirs").put(id,dir.toFile());map("instanceWorldNames").put(id,"axs_abyss_test");
        Files.writeString(dir.resolve("level.dat"),"must survive");
    }
    @SuppressWarnings("unchecked") Map<UUID,Object> map(String name)throws Exception {
        var field=AbyssWorldPool.class.getDeclaredField(name);field.setAccessible(true);return (Map<UUID,Object>)field.get(pool);
    }
    @AfterEach void cleanup()throws Exception {pool.shutdown();serverField.set(null,old);}
    @Test void occupiedWorldIsNeverUnloadedOrDeleted()throws Exception {
        when(world.getPlayers()).thenReturn(List.of(mock(Player.class)));
        pool.releaseWorld(id);
        assertTrue(Files.exists(dir.resolve("level.dat")));assertTrue(map("instanceWorldDirs").containsKey(id));
        verify(server,never()).unloadWorld(any(World.class),anyBoolean());
    }
    @Test void rejectedUnloadPreservesDirectoryAndOwnership()throws Exception {
        when(server.unloadWorld(world,false)).thenReturn(false);pool.releaseWorld(id);
        assertTrue(Files.exists(dir.resolve("level.dat")));assertTrue(map("instanceWorldNames").containsKey(id));
    }
    @Test void oldPreloadCallbackCannotRepopulateThePoolAfterTemplateReload()throws Exception {
        map("instanceWorldDirs").clear();map("instanceWorldNames").clear();
        when(server.unloadWorld(world,false)).thenReturn(true);pool.releasePreload();
        var template=mock(xuanmo.arcartxsuite.abyss.template.AbyssTemplate.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));when(template.id()).thenReturn("test");
        pool.acceptPrepared(template,new AbyssWorldPool.Prepared("axs_abyss_test",dir.toFile(),world,null),0L);
        pool.shutdown();assertEquals(0,pool.preparedCount("test"));assertFalse(Files.exists(dir.resolve("level.dat")));
    }
}
