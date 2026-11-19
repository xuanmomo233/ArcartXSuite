package xuanmo.arcartxsuite.mail.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.mail.*;
import xuanmo.arcartxsuite.api.currency.*;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.mail.MailModule;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.model.*;
import xuanmo.arcartxsuite.mail.storage.JdbcMailRepository;
import xuanmo.arcartxsuite.mail.storage.MailRepository.ClaimPhase;

class MailShutdownTest {
    @TempDir Path directory;

    @Test
    void queuedUiRefreshCannotRunAfterTheOldServiceHasRetired() throws Exception {
        var repository=repository("ui-callback");var service=service(repository,null);set(service,"active",true);
        var scheduler=mock(BukkitScheduler.class);var queued=new ArrayDeque<Runnable>();var calls=new AtomicInteger();
        when(scheduler.runTask(any(JavaPlugin.class),any(Runnable.class))).thenAnswer(call->{queued.add(call.getArgument(1));return mock(org.bukkit.scheduler.BukkitTask.class);});
        var onMain=MailService.class.getDeclaredMethod("onMain",Runnable.class);onMain.setAccessible(true);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            onMain.invoke(service,(Runnable)calls::incrementAndGet);queued.remove().run();assertEquals(1,calls.get());
            onMain.invoke(service,(Runnable)calls::incrementAndGet);
            service.shutdown();service.databaseStopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
            queued.remove().run();assertEquals(1,calls.get());
        }finally{repository.close();}
    }

    @Test
    void aMissingCommitDependencyCompletesThePublicFutureInsteadOfLeavingItPending() throws Exception {
        var repository=spy(repository("missing"));
        doThrow(new NoClassDefFoundError("isolated dependency removal")).when(repository).insertDurableMail(anyString(),any());
        var adapter=new AxsMailServiceAdapter(mock(JavaPlugin.class),service(repository,null),Logger.getAnonymousLogger(),false);
        try {
            var request=AxsMailRequest.builder("missing-dependency","Stored").sourcePlugin("lifecycle").recipient(UUID.randomUUID()).currency("money",10).build();
            var failure=assertThrows(ExecutionException.class,()->adapter.sendDurable(request).get(2,TimeUnit.SECONDS));
            assertInstanceOf(NoClassDefFoundError.class,failure.getCause());
        }finally{adapter.close();adapter.stopped().toCompletableFuture().get(2,TimeUnit.SECONDS);repository.close();}
    }

    @Test
    void acceptedDurableSendsDrainBeforeRepositoryCloseAndReplayKeepsTheirIds() throws Exception {
        var repository = spy(repository("drain"));
        var service = service(repository,null);
        var adapter = new AxsMailServiceAdapter(mock(JavaPlugin.class),service,Logger.getAnonymousLogger(),false);
        var module = new MailModule();
        set(module,"service",service);set(module,"repository",repository);set(module,"mailApiAdapter",adapter);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); await(release); return call.callRealMethod(); })
            .when(repository).insertDurableMail(anyString(),any());
        UUID owner = UUID.randomUUID();
        var requests = new ArrayList<AxsMailRequest>();
        var receipts = new ArrayList<CompletableFuture<AxsDeliveryReceipt>>();
        for (int i=0;i<32;i++) {
            var request=AxsMailRequest.builder("retire-"+i,"Stored").sourcePlugin("lifecycle").recipient(owner).currency("money",10).build();
            requests.add(request);receipts.add(adapter.sendDurable(request));
        }
        try {
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            var stop=MailModule.class.getDeclaredMethod("stopService");stop.setAccessible(true);
            long start=System.nanoTime();stop.invoke(module);
            assertTrue(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(500));
            assertFalse(module.shutdownCompletion().toCompletableFuture().isDone());
            verify(repository,never()).close();
        } finally { release.countDown(); }
        module.shutdownCompletion().toCompletableFuture().get(8,TimeUnit.SECONDS);
        var ids=new ArrayList<Long>();
        for(var receipt:receipts){var saved=receipt.get(1,TimeUnit.SECONDS);assertTrue(saved.mailId()>0);ids.add(saved.mailId());}
        assertEquals(32,new HashSet<>(ids).size());
        verify(repository,times(1)).close();
        var recovered=repository("drain");
        var replacement=new AxsMailServiceAdapter(mock(JavaPlugin.class),service(recovered,null),Logger.getAnonymousLogger(),false);
        try {
            for(int i=0;i<requests.size();i++){
                var replay=replacement.sendDurable(requests.get(i)).get(3,TimeUnit.SECONDS);
                assertTrue(replay.duplicate());assertEquals(ids.get(i).longValue(),replay.mailId());
            }
            assertEquals(32,recovered.loadInbox(owner).size());
        } finally { replacement.close();replacement.stopped().toCompletableFuture().get(3,TimeUnit.SECONDS);recovered.close(); }
    }

    @Test
    void closingAnUnstartedClaimCompletesTheApiFutureAndPreventsLateInvocation() throws Exception {
        var plugin=mock(JavaPlugin.class);var service=mock(MailService.class);
        var scheduler=mock(BukkitScheduler.class);var queued=new AtomicReference<Runnable>();
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(call->{queued.set(call.getArgument(1));return mock(org.bukkit.scheduler.BukkitTask.class);});
        var adapter=new AxsMailServiceAdapter(plugin,service,Logger.getAnonymousLogger(),false);
        UUID owner=UUID.randomUUID();
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var claim=adapter.claim(owner,17);assertFalse(claim.isDone());
            adapter.close();adapter.stopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
            assertTrue(claim.isDone());queued.get().run();
            verify(service,never()).claimMailByUuid(any(),anyLong());
            assertNull(adapter.getMail(owner,17).get());
            assertEquals(0,adapter.getUnreadCount(owner).get());
        }
    }

    @Test
    void shutdownReturnsAnUnexecutedCurrencyReservationToReadyWithoutCreditingIt() throws Exception {
        var repository=repository("reservation");
        var currencies=mock(CurrencyBridgeAPI.class);var credit=mock(CurrencyBridgeAPI.CurrencyBridge.class);
        when(currencies.bridge("coins")).thenReturn(credit);when(credit.available()).thenReturn(true);
        var service=spy(service(repository,currencies));set(service,"active",true);
        UUID owner=UUID.randomUUID();var player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(owner);when(player.isOnline()).thenReturn(true);
        doReturn(true).when(service).isCurrentClaimPlayer(player,owner);
        doAnswer(call->{call.<Runnable>getArgument(0).run();return null;}).when(service).scheduleClaimWorker(any());
        var attachment=new MailAttachment(0,0,MailAttachmentType.CURRENCY,"","coins",10,"",null);
        long id=repository.insertMail(service.systemMail(owner,"Queued","",List.of(attachment),List.of(),Instant.now().plusSeconds(60),"System")).id();
        try (var auth = mockStatic(xuanmo.arcartxsuite.api.auth.AuthAccess.class)) {
            auth.when(() -> xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(owner)).thenReturn(admission(owner));
            var claim=service.startClaim(player,id,false);
            var effects=(Queue<Runnable>)field(service,"claimEffects");
            assertNotNull(effects.peek());effects.poll().run();
            assertEquals(ClaimPhase.EXECUTING,repository.loadClaimProgress(owner,id).phase());
            assertFalse(claim.isDone());
        }
        service.shutdown();service.databaseStopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
        assertEquals(ClaimPhase.READY,repository.loadClaimProgress(owner,id).phase());
        verify(credit,never()).deposit(any(Player.class),any(BigDecimal.class));
        verify(player,never()).saveData();repository.close();
    }

    @Test
    void aReceiptScheduledByTheCurrentPulseAfterReentrantStopStillRuns() throws Exception {
        var repository=repository("pulse");var service=service(repository,null);set(service,"active",true);
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Class<?> jobType=Class.forName(MailService.class.getName()+"$ClaimJob");
        var constructor=jobType.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object job=constructor.newInstance(service,player,admission(player.getUniqueId()),1L,false);
        Class<?> workType=Class.forName(MailService.class.getName()+"$ClaimWork");
        var receipts=new AtomicInteger();
        Object work=Proxy.newProxyInstance(workType.getClassLoader(),new Class<?>[]{workType},(proxy,method,args)->{receipts.incrementAndGet();return null;});
        var claimWorker=MailService.class.getDeclaredMethod("claimWorker",jobType,workType);claimWorker.setAccessible(true);
        ((Queue<Runnable>)field(service,"claimEffects")).add(()->{
            service.shutdown();
            try{claimWorker.invoke(service,job,work);}catch(Exception error){throw new IllegalStateException(error);}
        });
        ((ModuleCallback)field(service,"claimPulse")).run();
        service.databaseStopped().toCompletableFuture().get(2,TimeUnit.SECONDS);
        assertEquals(1,receipts.get());repository.close();
    }

    private JdbcMailRepository repository(String name) throws Exception {
        var source=mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call->{var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(name+".sqlite"));try(var sql=connection.createStatement()){sql.execute("PRAGMA busy_timeout=5000");}return connection;});
        var repo=new JdbcMailRepository(source,StorageDescriptor.sqlite(name+".sqlite"),directory.toFile(),Logger.getAnonymousLogger());repo.initialize();return repo;
    }
    private MailService service(JdbcMailRepository repository,CurrencyBridgeAPI currencies) {
        var logger=Logger.getAnonymousLogger();return new MailService(mock(JavaPlugin.class),logger,directory.toFile(),MailModuleConfiguration.load(new YamlConfiguration(),logger),repository,
            null,null,null,null,null,currencies,null,null);
    }

    private static xuanmo.arcartxsuite.api.auth.AuthSnapshot admission(UUID player) {
        return new xuanmo.arcartxsuite.api.auth.AuthSnapshot(player, UUID.randomUUID(), 1, player,
            xuanmo.arcartxsuite.api.auth.AuthSnapshot.Registration.REGISTERED,
            xuanmo.arcartxsuite.api.auth.AuthSnapshot.Stage.ADMITTED, "test", java.util.Set.of(), "");
    }
    private static Object field(Object target,String name) throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()){try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(name);}
    private static void set(Object target,String name,Object value) throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()){try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(name);}
    private static void await(CountDownLatch latch){try{assertTrue(latch.await(5,TimeUnit.SECONDS));}catch(InterruptedException error){throw new AssertionError(error);}}
}
