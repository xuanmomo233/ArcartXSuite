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

    @Test void optedInDisconnectedPlayerReturnsToOriginalRunWithDeathState() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("reconnect-grace-seconds", 60);
        when(template.requirements()).thenReturn(EntryRequirements.load(yaml));
        when(template.spawn()).thenReturn("1,65,1");
        set(service, "partyManager", new xuanmo.arcartxsuite.abyss.party.PartyManager(
            service.config().party(), text -> { }, null));
        instance.recordDeath(player);
        when(player.isOnline()).thenReturn(false);
        service.handleQuit(player);
        var access = service.reconnectAccess();
        var status = access.reconnectStatus(player.getUniqueId());
        assertTrue(status.eligible());
        assertEquals(instance.getInstanceId(), status.runId());
        when(player.isOnline()).thenReturn(true);
        currentWorld.set(home);
        assertEquals("NO_ACTIVE_RESERVATION", access.requestReconnect(player.getUniqueId(), UUID.randomUUID()).code());
        assertEquals("REJOINED", access.requestReconnect(player.getUniqueId(), instance.getInstanceId()).code());
        assertSame(instance, instances.getInstance(player));
        assertTrue(instance.isPlayerCurrentlyDead(player));
        assertEquals(1, instance.getDeaths(player.getUniqueId()));
        verify(player).setGameMode(GameMode.SPECTATOR);
        assertFalse(access.reconnectStatus(player.getUniqueId()).eligible());
    }

    @Test void externalRosterIsVerifiedWithoutCreatingANativeParty() throws Exception {
        var parties = new xuanmo.arcartxsuite.abyss.party.PartyManager(service.config().party(), text -> { }, null);
        set(service, "partyManager", parties);
        var access = new ExternalParties(service);
        UUID leader = player.getUniqueId(), member = UUID.randomUUID();
        var intent = new xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent(
            "xc", UUID.randomUUID(), leader, List.of(leader, member), "test",
            null, null, null, "squad", 2, 3);
        var roster = new AtomicReference<>(new xuanmo.arcartxsuite.api.capability.AbyssExternalPartyAccess.Roster(
            "squad", leader, List.of(leader, member), "guild", 10, 2, 3, true));
        try (var ignored = access.register("xc", request -> roster.get())) {
            var verified = access.verify(intent);
            assertNotNull(verified);
            assertTrue(ExternalParties.context(verified).allReady());
            assertFalse(parties.inParty(leader));
            roster.set(new xuanmo.arcartxsuite.api.capability.AbyssExternalPartyAccess.Roster(
                "squad", leader, List.of(leader, member), "guild", 10, 3, 3, true));
            assertNull(access.verify(intent));
        }
        assertNull(access.verify(intent));
    }

    @Test void runControlChecksOwnerRevisionAndIdempotency() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("external-control-owner", "xc");
        when(template.requirements()).thenReturn(EntryRequirements.load(yaml));
        var field = AbyssService.class.getDeclaredField("admission");
        field.setAccessible(true);
        var admission = (AdmissionAccess) field.get(service);
        UUID owner = player.getUniqueId(), run = instance.getInstanceId();
        admission.attach(instance, new AdmissionAccess.Operation(new xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent(
            "xc", UUID.randomUUID(), owner, List.of(owner), "test", null, null, null)));
        var access = service.runAccess();
        long revision = access.snapshot(run).revision();
        var request = new xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Operation(UUID.randomUUID(), run, revision,
            "xc", xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Action.SET_VARIABLE, null, "flag", "yes");
        var result = access.execute(request);
        assertTrue(result.applied());
        assertEquals(result, access.execute(request));
        assertEquals("yes", instance.getVariables().get("external:xc:flag"));
        assertEquals(result.revision(), access.snapshot(run).revision());
        var stale = new xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Operation(UUID.randomUUID(), run, revision,
            "xc", request.action(), null, "flag", "no");
        assertEquals("STALE_REVISION", access.execute(stale).code());
        var other = new xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Operation(UUID.randomUUID(), run,
            result.revision(), "other", request.action(), null, "flag", "no");
        assertEquals("NOT_OWNER", access.execute(other).code());
        try (var ignored = access.registerObjectives("xc", id -> List.of(
                new xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Objective("chapter", "gate", "PENDING", 0, 1)))) {
            var current = access.snapshot(run);
            assertTrue(current.objectives().stream().anyMatch(objective -> objective.kind().equals("external:xc")
                && objective.id().equals("gate")));
            var complete = new xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Operation(UUID.randomUUID(), run,
                current.revision(), "xc", xuanmo.arcartxsuite.api.capability.AbyssRunAccess.Action.COMPLETE, null, null, null);
            assertEquals("OBJECTIVES_INCOMPLETE", access.execute(complete).code());
            assertEquals(AbyssStatus.ACTIVE, instance.getStatus());
        }
    }
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void partialAdmissionReportsActualReturnAndKeepsAnOccupiedWorld(boolean canReturn)throws Exception {
        instances.playerLeaveInstance(player);
        instances.destroyInstance(instance);
        instance = instances.createInstance(template, null);
        instances.playerEnterInstance(player, instance);
        instance.setWorld(world);
        instance.saveOriginalLocation(player, new Location(home, 1, 64, 1));
        var failed = mock(Player.class);
        UUID failedId = UUID.randomUUID();
        when(failed.getUniqueId()).thenReturn(failedId);
        when(failed.isOnline()).thenReturn(true);
        when(failed.getWorld()).thenReturn(home);
        when(server.getPlayer(failedId)).thenReturn(failed);
        instances.playerEnterInstance(failed, instance);
        var intent = new xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Intent(
            "xc", UUID.randomUUID(), player.getUniqueId(), List.of(player.getUniqueId(), failedId),
            "test", null, null, null);
        var operation = new AdmissionAccess.Operation(intent);
        operation.outcomes.put(player.getUniqueId(), "ARRIVED");
        var refunds = new AtomicInteger();
        var charge = new RequirementChecker.Charge(true, null, refunds::incrementAndGet, () -> fail("No commit"));
        if (!canReturn) doReturn(false).when(player).teleport(any(Location.class));
        var rollback = AbyssService.class.getDeclaredMethod("rollbackAdmission", AbyssInstance.class,
            AdmissionAccess.Operation.class, Player.class, RequirementChecker.Charge.class);
        rollback.setAccessible(true);
        rollback.invoke(service, instance, operation, failed, charge);
        assertEquals(1, refunds.get());
        assertEquals(canReturn ? xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Phase.COMPENSATED
            : xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.Phase.REVIEW_REQUIRED, operation.phase);
        assertEquals(canReturn ? "RETURNED" : "RETURN_PENDING", operation.outcomes.get(player.getUniqueId()));
        assertEquals("TELEPORT_FAILED", operation.outcomes.get(failedId));
        if (canReturn) {
            assertNull(instances.getInstance(player));
            verify(pool).releaseWorld(instance.getInstanceId());
        } else {
            assertSame(instance, instances.getInstance(player));
            verify(pool, never()).releaseWorld(any());
        }
    }

    @Test void rejectedReconnectTeleportPreservesRecoveryAndOldLoginCannotConsumeIt()throws Exception {
        var data=fake(xuanmo.arcartxsuite.abyss.data.PlayerDataManager.class);set(service,"playerData",data);
        var field=AbyssService.class.getDeclaredField("recoveringPlayers");field.setAccessible(true);
        @SuppressWarnings("unchecked") var recovering=(Map<UUID,Player>)field.get(service);recovering.put(player.getUniqueId(),player);
        var location=new xuanmo.arcartxsuite.abyss.data.JdbcAbyssRepository.PendingLocation("home",1,64,1,0,0,123);
        when(server.getWorld("home")).thenReturn(home);doReturn(false).when(player).teleport(any(Location.class));
        var restore=AbyssService.class.getDeclaredMethod("restoreJoinedPlayer",Player.class,location.getClass(),int.class);restore.setAccessible(true);
        restore.invoke(service,player,location,2);verify(data,never()).confirmPendingLocation(any(),any());assertTrue(recovering.containsKey(player.getUniqueId()));
        var replacement=mock(Player.class);recovering.put(player.getUniqueId(),replacement);clearInvocations(player);
        restore.invoke(service,player,location,0);verify(player,never()).teleport(any(Location.class));
        recovering.put(player.getUniqueId(),player);doReturn(true).when(player).teleport(any(Location.class));
        when(data.confirmPendingLocation(player.getUniqueId(),location)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        restore.invoke(service,player,location,0);verify(data).confirmPendingLocation(player.getUniqueId(),location);assertFalse(recovering.containsKey(player.getUniqueId()));
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
