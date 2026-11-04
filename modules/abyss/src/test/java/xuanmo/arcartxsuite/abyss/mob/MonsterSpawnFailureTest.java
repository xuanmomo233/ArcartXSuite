package xuanmo.arcartxsuite.abyss.mob;
import java.util.*;
import java.lang.reflect.RecordComponent;
import org.bukkit.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import xuanmo.arcartxsuite.abyss.instance.*;
import xuanmo.arcartxsuite.abyss.template.*;
class MonsterSpawnFailureTest {
    AbyssInstance instance;
    MonsterGroupManager manager;
    MonsterGroupManager.GroupCallback callback;
    @BeforeEach void setup()throws Exception {
        var cs=AbyssTemplate.class.getRecordComponents();
        Class<?>[] types=Arrays.stream(cs).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values=new Object[cs.length];
        for(int i=0;i<cs.length;i++)values[i]=types[i]==int.class?0:cs[i].getName().equals("id")?"test":null;
        var template=AbyssTemplate.class.getDeclaredConstructor(types).newInstance(values);
        instance=new AbyssInstance(UUID.randomUUID(),template,null);
        World world=mock(World.class);instance.setWorld(world);
        manager=new MonsterGroupManager(null,null,java.util.logging.Logger.getAnonymousLogger());
        callback=mock(MonsterGroupManager.GroupCallback.class);
        when(callback.provider()).thenReturn(mock(MobProvider.class));
        when(callback.parseLocation(eq(instance),anyString())).thenReturn(new Location(world,0,65,0));
        manager.setCallback(callback);
    }
    MonsterConfig.MonsterGroup group(List<MonsterConfig.MonsterSpawn> mobs) {
        return new MonsterConfig.MonsterGroup("wave",false,MonsterConfig.SpawnTiming.MANUAL,0,-1,List.of(),
            mobs,0,List.of(),List.of(),List.of(),List.of());
    }
    @Test void failedProviderAbortsInsteadOfLeavingEmptyActiveGroup() {
        manager.registerRuntimeGroup(instance,group(List.of(new MonsterConfig.MonsterSpawn("missing","0,65,0",1,1))));
        assertFalse(manager.spawnGroup(instance,"wave",null));
        verify(callback).onSpawnFailed(instance,"wave");verify(callback,never()).onGroupComplete(any(),anyString());
    }
    @Test void intentionallyEmptyGroupCompletesAndDoesNotSoftLock() {
        manager.registerRuntimeGroup(instance,group(List.of()));
        assertTrue(manager.spawnGroup(instance,"wave",null));
        assertTrue(manager.isGroupComplete(instance,"wave"));
        verify(callback).onGroupComplete(instance,"wave");verify(callback,never()).onSpawnFailed(any(),anyString());
        manager.sweepVanished(instance);verify(callback,times(1)).onGroupComplete(instance,"wave");
    }

    @Test void trackedMobCannotBeCompletedByAnotherRunAndDeathIsReportedOnce() {
        var mob = mock(org.bukkit.entity.Entity.class);
        UUID id = UUID.randomUUID();
        when(mob.getUniqueId()).thenReturn(id);
        when(mob.getWorld()).thenReturn(instance.getWorld());
        when(callback.provider().spawn(eq("vanilla:zombie"), any(), anyDouble())).thenReturn(mob);
        manager.registerRuntimeGroup(instance, group(List.of(new MonsterConfig.MonsterSpawn("vanilla:zombie", "0,65,0", 1, 1))));
        assertTrue(manager.spawnGroup(instance, "wave", null));
        var context = manager.entityContext(id);
        assertEquals(instance.getInstanceId(), context.runId());
        var another = new AbyssInstance(UUID.randomUUID(), instance.getTemplate(), null);
        manager.onEntityDeath(id, another, null);
        assertEquals(context, manager.entityContext(id));
        verify(callback, never()).onMobDeath(any(), any(), any());
        manager.onEntityDeath(id, instance, null);
        manager.onEntityDeath(id, instance, null);
        verify(callback, times(1)).onMobDeath(instance, id, null);
        assertNull(manager.entityContext(id));
        assertTrue(manager.entityContexts(instance.getInstanceId()).isEmpty());
    }
}
