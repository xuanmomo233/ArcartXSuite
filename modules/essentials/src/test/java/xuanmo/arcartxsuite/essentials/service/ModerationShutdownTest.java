package xuanmo.arcartxsuite.essentials.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration.ModerationConfiguration;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

class ModerationShutdownTest {
    @TempDir Path directory;
    final java.util.logging.Logger logger=java.util.logging.Logger.getAnonymousLogger();
    final ModerationConfiguration config=new ModerationConfiguration("Ban {reason}","","",5,30,false);

    @Test void aQueryFinishingAfterDisableDoesNotRejectTheLogin() throws Exception {
        var real=source();var initial=repository(real);initial.initialize();var player=UUID.randomUUID();
        initial.ban(player,"Player","reason","Console",-1,null);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var gate=mock(DataSource.class);
        when(gate.getConnection()).thenAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return real.getConnection();});
        var work=new EssentialsWork(null);var service=new ModerationService(repository(gate),config,logger,work);
        var login=new AsyncPlayerPreLoginEvent("Player",InetAddress.getLoopbackAddress(),player);
        var executor=Executors.newSingleThreadExecutor();
        try {
            var query=executor.submit(()->service.onPreLogin(login));assertTrue(entered.await(5,TimeUnit.SECONDS));
            work.close();service.stop();assertFalse(work.stopped().toCompletableFuture().isDone());
            release.countDown();query.get(5,TimeUnit.SECONDS);work.stopped().toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED,login.getLoginResult());
            service.onPreLogin(login);verify(gate,times(1)).getConnection();
        }finally{release.countDown();work.close();executor.shutdownNow();}
    }

    @Test void acceptedJoinNameWritesRemainAfterStop() throws Exception {
        var source=source();var repository=repository(source);repository.initialize();
        var plugin=mock(JavaPlugin.class);var scheduler=mock(BukkitScheduler.class);
        when(scheduler.runTaskTimerAsynchronously(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenReturn(mock(BukkitTask.class));
        var work=new EssentialsWork(plugin);var service=new ModerationService(repository,config,logger,work);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);service.start(plugin);
            work.submit(()->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return null;});assertTrue(entered.await(5,TimeUnit.SECONDS));
            for(int i=0;i<32;i++) {
                var player=mock(Player.class);when(player.getUniqueId()).thenReturn(new UUID(0,i+1));when(player.getName()).thenReturn("Player"+i);
                service.onJoin(new PlayerJoinEvent(player,""));
            }
            work.close();service.stop();assertFalse(work.stopped().toCompletableFuture().isDone());release.countDown();
            work.stopped().toCompletableFuture().get(10,TimeUnit.SECONDS);
            for(int i=0;i<32;i++)assertEquals("Player"+i,repository.knownPlayerName(new UUID(0,i+1)));
        }finally{release.countDown();work.close();}
    }

    private SQLiteDataSource source(){var source=new SQLiteDataSource();source.setUrl("jdbc:sqlite:"+directory.resolve("essentials.sqlite"));return source;}
    private EssentialsRepository repository(DataSource source){return new EssentialsRepository(source,StorageDescriptor.sqlite("essentials.sqlite"),directory.toFile(),logger);}
}
