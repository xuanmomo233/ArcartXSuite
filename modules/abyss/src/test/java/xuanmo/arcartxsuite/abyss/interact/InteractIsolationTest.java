package xuanmo.arcartxsuite.abyss.interact;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.abyss.instance.AbyssInstance;
import xuanmo.arcartxsuite.abyss.script.*;
import xuanmo.arcartxsuite.abyss.template.InteractConfig.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InteractIsolationTest {
    final AtomicLong clock=new AtomicLong(10_000);
    final InteractManager manager=new InteractManager(null,Logger.getAnonymousLogger(),clock::get);
    final Player player=mock(Player.class);
    final Block block=mock(Block.class);
    InteractIsolationTest(){when(player.getUniqueId()).thenReturn(UUID.randomUUID());}
    AbyssInstance instance(boolean once,List<String> script){
        var instance=new AbyssInstance(UUID.randomUUID(),null,null);
        instance.addPlayer(player);instance.confirmAdmission();
        manager.registerRuntime(instance,new Interact("lever",InteractType.RIGHT_CLICK_BLOCK,null,List.of(),null,null,null,once,null,List.of(),script));
        return instance;
    }
    void click(AbyssInstance instance){manager.onBlockTrigger(instance,player,InteractType.RIGHT_CLICK_BLOCK,block,null);}
    @Test void cleanupDoesNotResetAnotherInstancesDebounce(){
        var count=new AtomicInteger();manager.setCallbacks((i,e)->count.incrementAndGet(),null);
        var a=instance(false,List.of());var b=instance(false,List.of());click(a);click(b);assertEquals(2,count.get());
        manager.cleanup(a);clock.incrementAndGet();click(b);assertEquals(2,count.get());
        clock.addAndGet(500);click(b);assertEquals(3,count.get());
    }
    @Test void onceIsCommittedOnlyAfterSuccessfulScriptAndNoEffectFailureCanRetry(){
        var count=new AtomicInteger();manager.setPuzzleCallback((i,e)->count.incrementAndGet());
        var executions=new ArrayList<ScriptSession>();
        manager.setScriptExecutor((i,e)->{var session=new ScriptSession("lever",List.of(),ScriptContext.of(i,player),null);executions.add(session);return session;},Runnable::run,i->fail("No effects were applied"));
        var instance=instance(true,List.of("grant_reward @trigger test"));click(instance);click(instance);
        assertEquals(1,executions.size());assertFalse(instance.isTriggered("interact:lever"));assertEquals(0,count.get());
        executions.get(0).completion().complete(false);clock.addAndGet(501);click(instance);assertEquals(2,executions.size());
        executions.get(1).completion().complete(true);assertTrue(instance.isTriggered("interact:lever"));assertEquals(1,count.get());
        clock.addAndGet(501);click(instance);assertEquals(2,executions.size());
    }
    @Test void partialEffectsFailTheInstanceInsteadOfAllowingDuplicateRewards()throws Exception{
        var failed=new AtomicInteger();var puzzle=new AtomicInteger();manager.setPuzzleCallback((i,e)->puzzle.incrementAndGet());
        var instance=instance(true,List.of("grant_reward @trigger test"));
        var session=new ScriptSession("lever",List.of(),ScriptContext.of(instance,player),null);
        var field=ScriptSession.class.getDeclaredField("successfulActions");field.setAccessible(true);((AtomicInteger)field.get(session)).set(1);
        manager.setScriptExecutor((i,e)->session,Runnable::run,i->failed.incrementAndGet());click(instance);session.completion().complete(false);
        assertEquals(1,failed.get());assertEquals(0,puzzle.get());assertFalse(instance.isTriggered("interact:lever"));
    }
    @Test void deadPlayersCannotTriggerGameplay(){
        var instance=mock(AbyssInstance.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(instance.getInstanceId()).thenReturn(UUID.randomUUID());when(instance.admitted()).thenReturn(true);
        when(instance.hasPlayer(player.getUniqueId())).thenReturn(true);when(instance.isPlayerCurrentlyDead(player)).thenReturn(true);
        manager.registerRuntime(instance,new Interact("lever",InteractType.RIGHT_CLICK_BLOCK,null,List.of(),null,null,null,false,null,List.of(),List.of()));
        var count=new AtomicInteger();manager.setCallbacks((i,e)->count.incrementAndGet(),null);click(instance);assertEquals(0,count.get());
    }
}
