package xuanmo.arcartxsuite.abyss.buff;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.template.*;
import xuanmo.arcartxsuite.api.scheduler.*;

class BuffSelectionTest {
    Player player; AbyssInstance instance; BuffManager manager; BuffManager.Callback callback;
    List<Runnable> timers=new ArrayList<>();Field serverField;Object previousServer;
    @AfterEach void restore()throws Exception { if(serverField!=null)serverField.set(null,previousServer); }
    void setup(int timeout)throws Exception {
        player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);
        Server server=mock(Server.class);when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);previousServer=serverField.get(null);serverField.set(null,server);
        var options=List.of(option("a"),option("b"),option("c"));
        var pool=new BuffConfig.Pool("pool","祝福",2,3,"panel",timeout,"first",false,options);
        var cs=AbyssTemplate.class.getRecordComponents();
        Class<?>[] types=Arrays.stream(cs).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values=new Object[cs.length];
        for(int i=0;i<cs.length;i++){
            values[i]=types[i]==int.class?0:null;
            if(cs[i].getName().equals("id"))values[i]="test";
            if(cs[i].getName().equals("buffs"))values[i]=new BuffConfig(Map.of("pool",pool));
        }
        var template=AbyssTemplate.class.getDeclaredConstructor(types).newInstance(values);
        instance=new AbyssInstance(UUID.randomUUID(),template,null);instance.addPlayer(player);
        SchedulerAPI scheduler=mock(SchedulerAPI.class);
        when(scheduler.scheduleOnceSeconds(isNull(Plugin.class),any(Runnable.class),anyLong()))
            .thenAnswer(call->{timers.add(call.getArgument(1));return mock(ScheduledTask.class);});
        manager=new BuffManager(null,scheduler,java.util.logging.Logger.getAnonymousLogger());
        callback=mock(BuffManager.Callback.class);manager.setCallback(callback);
    }
    BuffConfig.Option option(String id) {
        return new BuffConfig.Option(id,id,"STONE","common",1,List.of(),List.of("message @trigger "+id));
    }
    @Test void multiPickReopensRemainingOptionsAndRejectsReplay()throws Exception {
        setup(0);assertTrue(manager.offer(instance,"pool",player));
        assertTrue(manager.pick(instance,player,"pool","a"));
        assertFalse(manager.pick(instance,player,"pool","a"));
        assertTrue(manager.pick(instance,player,"pool","b"));
        assertFalse(manager.pick(instance,player,"pool","c"));
        assertEquals(2,instance.getVariables().get("buff.pool.count"));
        var pools=org.mockito.ArgumentCaptor.forClass(BuffConfig.Pool.class);
        verify(callback,times(2)).openBuffUi(eq(player),eq(instance),pools.capture(),anyList(),eq(0));
        assertEquals(1,pools.getAllValues().get(1).pick());
    }
    @Test void timeoutFillsOnlyUnselectedChoices()throws Exception {
        setup(30);manager.offer(instance,"pool",player);manager.pick(instance,player,"pool","a");
        timers.get(0).run();
        assertEquals(2,instance.getVariables().get("buff.pool.count"));
        assertNotEquals("a",instance.getVariables().get("buff.pool"));
    }
    @Test void previousOfferTimerCannotExpireReplacementOffer()throws Exception {
        setup(30);manager.offer(instance,"pool",player);manager.offer(instance,"pool",player);
        timers.get(0).run();
        assertNull(instance.getVariables().get("buff.pool.count"));
        assertTrue(manager.pick(instance,player,"pool","a"));
    }
}
