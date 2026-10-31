package xuanmo.arcartxsuite.abyss.service;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import xuanmo.arcartxsuite.abyss.config.AbyssModuleConfiguration;
import xuanmo.arcartxsuite.abyss.instance.*;
import xuanmo.arcartxsuite.abyss.template.*;
import xuanmo.arcartxsuite.abyss.script.*;
import xuanmo.arcartxsuite.abyss.world.AbyssWorldPool;
import xuanmo.arcartxsuite.api.scheduler.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementLifecycleTest {
    record Job(Runnable run,long ticks) { }
    Field serverField;Object previous;Server server;Player player;World home,world;
    AbyssService service;AbyssInstanceManager instances;AbyssInstance instance;AbyssTemplate template;AbyssWorldPool pool;
    final AtomicLong clock=new AtomicLong(10_000);final AtomicReference<World> currentWorld=new AtomicReference<>();
    final List<Job> jobs=new ArrayList<>();SchedulerAPI scheduler;
    static <T>T fake(Class<T> type){return mock(type,withSettings().mockMaker(org.mockito.MockMakers.INLINE));}
    static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    @BeforeEach void setup()throws Exception {
        server=mock(Server.class);serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);previous=serverField.get(null);serverField.set(null,server);
        when(server.isPrimaryThread()).thenReturn(true);home=mock(World.class);world=mock(World.class);
        when(home.getName()).thenReturn("home");when(world.getName()).thenReturn("axs_abyss_test");when(world.getMinHeight()).thenReturn(-64);
        when(server.getWorlds()).thenReturn(List.of(home,world));currentWorld.set(world);
        player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getName()).thenReturn("Tester");when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenAnswer(call->currentWorld.get());when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenAnswer(call->new Location(currentWorld.get(),1,65,1));
        when(player.teleport(any(Location.class))).thenAnswer(call->{currentWorld.set(((Location)call.getArgument(0)).getWorld());return true;});
        when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        scheduler=mock(SchedulerAPI.class);when(scheduler.scheduleOnce(any(),any(Runnable.class),anyLong())).thenAnswer(call->{jobs.add(new Job(call.getArgument(1),call.getArgument(2)));return mock(ScheduledTask.class);});
        var yaml=new YamlConfiguration();yaml.set("statistics.auto-show",false);yaml.set("abyss.instance-cleanup-timeout",0);
        var cfg=AbyssModuleConfiguration.load(yaml);var constructor=AbyssService.class.getDeclaredConstructors()[0];Object[] args=new Object[constructor.getParameterCount()];
        for(int i=0;i<args.length;i++){var type=constructor.getParameterTypes()[i];if(type==AbyssModuleConfiguration.class)args[i]=cfg;if(type==Logger.class)args[i]=Logger.getAnonymousLogger();if(type==SchedulerAPI.class)args[i]=scheduler;}
        service=(AbyssService)constructor.newInstance(args);set(service,"settlementClock",(java.util.function.LongSupplier)clock::get);set(service,"started",true);
        instances=new AbyssInstanceManager();set(service,"instances",instances);pool=fake(AbyssWorldPool.class);set(service,"worldPool",pool);
        var mobs=fake(xuanmo.arcartxsuite.abyss.mob.MonsterGroupManager.class);when(mobs.groupProgress(any())).thenReturn(new int[]{1,1});set(service,"mobGroups",mobs);
        set(service,"obstacles",fake(xuanmo.arcartxsuite.abyss.obstacle.ObstacleManager.class));set(service,"interacts",fake(xuanmo.arcartxsuite.abyss.interact.InteractManager.class));
        set(service,"instanceTasks",fake(xuanmo.arcartxsuite.abyss.task.InstanceTaskScheduler.class));set(service,"npcs",fake(xuanmo.arcartxsuite.abyss.npc.DungeonNpcManager.class));
        set(service,"stats",new xuanmo.arcartxsuite.abyss.stats.CombatStatisticsService(cfg.statistics()));set(service,"playerData",fake(xuanmo.arcartxsuite.abyss.data.PlayerDataManager.class));
        template=fake(AbyssTemplate.class);when(template.id()).thenReturn("test");when(template.name()).thenReturn("test");when(template.mode()).thenReturn(AbyssTemplate.Mode.CLASSIC);
        when(template.scripts()).thenReturn(ScriptConfig.load(new YamlConfiguration()));
        instance=instances.createInstance(template,null);instances.playerEnterInstance(player,instance);instance.setWorld(world);
        instance.saveOriginalLocation(player,new Location(home,1,64,1));instance.savePlayerGameMode(player);instance.markEntered(player.getUniqueId());
        instance.transitionTo(AbyssStatus.PREPARING,AbyssStatus.ACTIVE);
        instance.confirmAdmission();
    }
    @AfterEach void restore()throws Exception{serverField.set(null,previous);}
    void policy(String mode,int seconds)throws Exception {
        var yaml=new YamlConfiguration();yaml.set("exit-mode",mode);yaml.set("exit-delay-seconds",seconds);when(template.settlement()).thenReturn(SettlementConfig.load(yaml));
    }
    void tick()throws Exception {var method=AbyssService.class.getDeclaredMethod("settlementTick",AbyssInstance.class);method.setAccessible(true);method.invoke(service,instance);}
    @Test void countdownKeepsPlayersAndWorldUntilTheConfiguredDeadline()throws Exception {
        policy("COUNTDOWN",5);service.completeDungeon(instance);assertTrue(instance.hasPlayer(player.getUniqueId()));
        verify(player,never()).teleport(any(Location.class));clock.set(14_999);tick();verify(pool,never()).releaseWorld(any());
        clock.set(15_000);tick();assertNull(instances.getInstance(player));verify(pool).releaseWorld(instance.getInstanceId());assertSame(home,currentWorld.get());
    }
    @Test void manualModeSurvivesWatchdogAndOnlyLeavesOnPlayerIntent()throws Exception {
        policy("MANUAL",0);service.completeDungeon(instance);clock.addAndGet(86_400_000);tick();
        set(instance,"createTime",System.currentTimeMillis()-10_000L);
        var watch=AbyssService.class.getDeclaredMethod("watchdog");watch.setAccessible(true);watch.invoke(service);
        assertTrue(instance.hasPlayer(player.getUniqueId()));verify(player,never()).teleport(any(Location.class));
        assertTrue(service.playerExit(instance,player,LeaveReason.QUIT));assertNull(instances.getInstance(player));
    }
    @Test void immediateExitIsDeferredPastTheDeathEventAndRejectedTeleportsKeepOwnership()throws Exception {
        policy("IMMEDIATE",0);doReturn(false).when(player).teleport(any(Location.class));service.completeDungeon(instance);
        verify(player,never()).teleport(any(Location.class));jobs.stream().filter(j->j.ticks()==2).findFirst().orElseThrow().run().run();
        assertSame(instance,instances.getInstance(player));verify(pool,never()).releaseWorld(any());
        doReturn(true).when(player).teleport(any(Location.class));assertTrue(service.playerExit(instance,player,LeaveReason.QUIT));
    }
    @Test void terminalScriptsDrainAfterPlayersLeaveAndKeepTheirRecipientSnapshot()throws Exception {
        policy("IMMEDIATE",0);var scripts=new YamlConfiguration();scripts.set("complete",List.of("wait 100t","target"));when(template.scripts()).thenReturn(ScriptConfig.load(scripts));
        var runner=new ScriptRunner(null,scheduler,()->service,Logger.getAnonymousLogger());var targets=new AtomicInteger();
        runner.actions().register("target",(ctx,args,s)->targets.set(ctx.resolvePlayers("@all").size()));set(service,"scriptRunner",runner);
        service.completeDungeon(instance);jobs.stream().filter(j->j.ticks()==2).findFirst().orElseThrow().run().run();
        assertNull(instances.getInstance(player));assertNotNull(instances.getInstance(instance.getInstanceId()));verify(pool,never()).releaseWorld(any());
        clock.addAndGet(5000);jobs.stream().filter(j->j.ticks()==100).findFirst().orElseThrow().run().run();assertEquals(1,targets.get());
        tick();verify(pool).releaseWorld(instance.getInstanceId());assertTrue(instance.getScriptSessions().isEmpty());
    }
    @Test void externalDepartureCleansMappingAndSettlementProtectionPreventsNewDamage()throws Exception {
        policy("MANUAL",0);service.completeDungeon(instance);
        var listener=new xuanmo.arcartxsuite.abyss.listener.AbyssPlayerListener(service,Logger.getAnonymousLogger());
        var damage=new org.bukkit.event.entity.EntityDamageEvent(player,org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE,5);
        listener.onFatalDamage(damage);assertTrue(damage.isCancelled());
        currentWorld.set(home);listener.onChangedWorld(new org.bukkit.event.player.PlayerChangedWorldEvent(player,world));
        assertNull(instances.getInstance(player));verify(player,never()).teleport(any(Location.class));
    }
    @Test void failedPreparationDoesNotRunFailureRewardsOrTeleportAPlayerWhoNeverEntered()throws Exception {
        instances.playerLeaveInstance(player);instances.destroyInstance(instance);
        instance=instances.createInstance(template,null);instances.playerEnterInstance(player,instance);instance.setWorld(world);currentWorld.set(home);
        var runner=fake(ScriptRunner.class);set(service,"scriptRunner",runner);
        var scripts=new YamlConfiguration();scripts.set("fail",List.of("grant_reward @all must-not-award"));when(template.scripts()).thenReturn(ScriptConfig.load(scripts));
        service.failDungeon(instance,FailReason.SCRIPTED);
        verifyNoInteractions(runner);verify(player,never()).teleport(any(Location.class));assertFalse(service.isSettling(instance));
    }
}
