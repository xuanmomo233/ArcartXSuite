package xuanmo.arcartxsuite.afkreward.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.afkreward.AfkRewardModule;
import xuanmo.arcartxsuite.afkreward.config.AfkRewardConfiguration;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.*;
import xuanmo.arcartxsuite.api.storage.*;

class AfkShutdownTest {
    @TempDir Path directory;

    @Test
    void stopReturnsBeforeBlockedFinalMysqlWriteAndRetirementWaitsForIt() throws Exception {
        var repository=mock(AfkRewardRepository.class);
        when(repository.getDescriptor()).thenReturn(StorageDescriptor.mysql("localhost",3306,"isolated","","",2,""));
        var service=service(repository);
        UUID id=UUID.randomUUID();
        seedStats(service,id,new PlayerStats("Steve","2026-11-17",2,7,321));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        Thread primary=Thread.currentThread();
        doAnswer(call->{assertNotSame(primary,Thread.currentThread());entered.countDown();
            assertTrue(release.await(3,TimeUnit.SECONDS));return null;}).when(repository).saveQuitCheckpoints(anyList());
        var module=new AfkRewardModule();var scope=mock(ModuleStorage.class);
        when(scope.retired()).thenReturn(CompletableFuture.completedFuture(null));
        set(module,"service",service);set(module,"storageScope",scope);
        var stop=AfkRewardModule.class.getDeclaredMethod("stopService");stop.setAccessible(true);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            long start=System.nanoTime();stop.invoke(module);
            assertTrue(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(500));
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
            assertFalse(service.callApi(()->true,false));
            verify(scope).retireAfter(any());
            release.countDown();module.shutdownCompletion().toCompletableFuture().get(3,TimeUnit.SECONDS);
        }finally{release.countDown();}
        verify(repository).saveQuitCheckpoints(argThat(rows->rows.size()==1&&rows.get(0).stats().totalSeconds()==321));
    }

    @Test
    void alreadyRunningReadRetiresBeforeTheWriterAndQueuedReadsCannotStartAfterStop() throws Exception {
        var service=service(mock(AfkRewardRepository.class));set(service,"active",true);
        var scheduler=mock(BukkitScheduler.class);var queued=new ConcurrentLinkedQueue<Runnable>();
        when(scheduler.runTaskAsynchronously(any(),any(Runnable.class))).thenAnswer(call->{queued.add(call.getArgument(1));return mock(BukkitTask.class);});
        var read=AfkRewardService.class.getDeclaredMethod("readAsync",Runnable.class);read.setAccessible(true);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var late=new AtomicInteger();
        Thread worker=null;
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            read.invoke(service,(Runnable)()->{entered.countDown();try{release.await();}catch(InterruptedException error){Thread.currentThread().interrupt();}});
            read.invoke(service,(Runnable)late::incrementAndGet);
            worker=new Thread(queued.remove());worker.start();assertTrue(entered.await(1,TimeUnit.SECONDS));
            service.shutdown();assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            queued.remove().run();assertEquals(0,late.get());
            release.countDown();service.databaseStopped().toCompletableFuture().get(3,TimeUnit.SECONDS);
        }finally{release.countDown();if(worker!=null)worker.join(2000);}
    }

    @Test
    void sqliteQuitRetriesItsCheckpointOffMainAndFencesAQuickReconnect() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("quit-retry.db");
        var source = mock(DataSource.class);
        var backgroundOnly = new AtomicBoolean();
        var rejectFirst = new AtomicBoolean();
        Thread primary = Thread.currentThread();
        when(source.getConnection()).thenAnswer(call -> {
            if (backgroundOnly.get()) assertNotSame(primary, Thread.currentThread());
            if (rejectFirst.getAndSet(false)) throw new SQLException("fixture temporarily locked");
            return DriverManager.getConnection(url);
        });
        var repository = new AfkRewardRepository(source, StorageDescriptor.sqlite("quit-retry.db").withTablePrefix("afk_"),
            directory.toFile(), Logger.getAnonymousLogger());
        repository.initialize();
        var service = service(repository);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var saved = new SessionRecord(id, "Steve", "lounge", "lounge", "MANUAL", 60, 10,
            2, 7, "2026-11-17", 300, 0.35f, true);
        repository.saveSession(saved);
        repository.saveStats(id, new PlayerStats("Steve", "2026-11-17", 2, 7, 300));
        var latest = new SessionRecord(id, "Steve", "lounge", "lounge", "MANUAL", 81, 10,
            2, 7, "2026-11-17", 321, 0.35f, true);
        seedStats(service, id, new PlayerStats("Steve", "2026-11-17", 2, 7, 321));
        ((Map<UUID,SessionRecord>)field(service,"pendingSessionWrites")).put(id, latest);
        set(service, "active", true);
        var schedule = AfkRewardService.class.getDeclaredMethod("scheduleQuitCheckpoints");
        schedule.setAccessible(true);
        var preload = AfkRewardService.class.getDeclaredMethod("preloadStats", UUID.class, String.class);
        preload.setAccessible(true);
        var read = AfkRewardService.class.getDeclaredMethod("scheduleStatsReads");
        read.setAccessible(true);
        var scheduler = mock(BukkitScheduler.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            backgroundOnly.set(true);
            rejectFirst.set(true);
            service.onPlayerQuit(player);
            schedule.invoke(service);
            assertEquals(false, ((CompletableFuture<?>)field(service, "quitWrite")).get(3, TimeUnit.SECONDS));
            schedule.invoke(service);
            preload.invoke(service, id, "Steve");
            read.invoke(service);
            verify(scheduler, never()).runTaskAsynchronously(any(), any(Runnable.class));
            set(service, "nextQuitWrite", System.nanoTime() - 1);
            schedule.invoke(service);
            assertEquals(true, ((CompletableFuture<?>)field(service, "quitWrite")).get(3, TimeUnit.SECONDS));
            schedule.invoke(service);
            read.invoke(service);
            verify(scheduler).runTaskAsynchronously(any(), any(Runnable.class));
        } finally {
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
                service.shutdown();
                service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            }
            backgroundOnly.set(false);
        }
        assertEquals(321, repository.loadStats(id).totalSeconds());
        assertEquals(latest, repository.loadSession(id));
    }

    @Test
    void sqliteFinalCheckpointKeepsCountersWithoutRecreatingConsumedRewardSession() throws Exception {
        String url="jdbc:sqlite:"+directory.resolve("final.db");
        var source=mock(DataSource.class);var mustBeBackground=new AtomicBoolean();Thread primary=Thread.currentThread();
        when(source.getConnection()).thenAnswer(call->{if(mustBeBackground.get())assertNotSame(primary,Thread.currentThread());return DriverManager.getConnection(url);});
        var repository=new AfkRewardRepository(source,StorageDescriptor.sqlite("final.db").withTablePrefix("afk_"),directory.toFile(),Logger.getAnonymousLogger());
        repository.initialize();UUID id=UUID.randomUUID();
        var session=new SessionRecord(id,"Steve","lounge","lounge","MANUAL",60,10,2,7,"2026-11-17",300,0.35f,true);
        repository.saveSession(session);assertTrue(repository.beginManualSettlement(session, java.util.UUID.randomUUID()));
        var latest=new PlayerStats("Steve","2026-11-17",2,7,321);
        var service=service(repository);seedStats(service,id,latest);
        ((Map<UUID,SessionRecord>)field(service,"pendingSessionWrites")).put(id,session);
        mustBeBackground.set(true);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            service.shutdown();service.databaseStopped().toCompletableFuture().get(3,TimeUnit.SECONDS);
        }finally{mustBeBackground.set(false);}
        assertEquals(latest,repository.loadStats(id));assertNull(repository.loadSession(id));
        assertFalse(repository.beginManualSettlement(session, java.util.UUID.randomUUID()));
        assertEquals("DISPATCHING",repository.findManualSettlementsForReview(10).get(0).status());
    }

    private AfkRewardService service(AfkRewardRepository repository) {
        var plugin=mock(JavaPlugin.class);when(plugin.getDataFolder()).thenReturn(directory.toFile());
        var logger=Logger.getAnonymousLogger();
        return new AfkRewardService(plugin,AfkRewardConfiguration.load(new YamlConfiguration(),logger),repository,logger,null,null,null,null,null);
    }
    private static void seedStats(AfkRewardService service,UUID id,PlayerStats stats)throws Exception {
        ((Map<UUID,PlayerStats>)field(service,"statsCache")).put(id,stats);
        ((Set<UUID>)field(service,"dirtyPlayerStats")).add(id);
    }
    private static Object field(Object target,String name)throws Exception {Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static void set(Object target,String name,Object value)throws Exception {Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
}
