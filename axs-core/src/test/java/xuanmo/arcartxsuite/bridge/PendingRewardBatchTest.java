package xuanmo.arcartxsuite.bridge;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.storage.StorageManager;
import xuanmo.arcartxsuite.module.ModuleRegistry;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingRewardBatchTest {
    @TempDir Path dir;
    DefaultPendingRewardService service;
    StorageManager storage;
    final UUID player=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        storage=mock(StorageManager.class);
        when(storage.getHostConnection()).thenAnswer(call->DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("pending.db")));
        service=new DefaultPendingRewardService(null,Logger.getAnonymousLogger(),storage,null);
        service.initialize();
    }
    PendingReward coins(int amount) { return PendingReward.currency(player,"abyss","money",amount,"test"); }
    @Test void identicalOperationIsOnlyPersistedOnceEvenAfterDelivery() {
        assertTrue(service.savePendingBatchConfirmed("instance-player-reward",List.of(coins(5))));
        assertTrue(service.savePendingBatchConfirmed("instance-player-reward",List.of(coins(5))));
        var pending=service.loadPending(player);assertEquals(1,pending.size());
        service.markDelivered(pending.get(0).id());
        assertTrue(service.savePendingBatchConfirmed("instance-player-reward",List.of(coins(5))));
        assertTrue(service.loadPending(player).isEmpty());
    }
    @Test void conflictingReplayIsRejected() {
        assertTrue(service.savePendingBatchConfirmed("key",List.of(coins(5))));
        assertFalse(service.savePendingBatchConfirmed("key",List.of(coins(6))));
        assertEquals(1,service.loadPending(player).size());
    }
    @Test void failedSecondInsertRollsBackRewardsAndReceipt() {
        var invalid=new PendingReward(0,player,"abyss",PendingReward.Type.CURRENCY,null,"bad",0,0);
        assertFalse(service.savePendingBatchConfirmed("retryable",List.of(coins(5),invalid)));
        assertTrue(service.loadPending(player).isEmpty());
        assertTrue(service.savePendingBatchConfirmed("retryable",List.of(coins(5))));
        assertEquals(1,service.loadPending(player).size());
    }
    @Test void batchesCannotMixRecipientsOrSources() {
        assertThrows(IllegalArgumentException.class,()->service.savePendingBatchConfirmed("key",List.of(coins(5),
            PendingReward.currency(UUID.randomUUID(),"abyss","money",5,"wrong"))));
        assertTrue(service.loadPending(player).isEmpty());
    }
    @Test void unavailableDatabaseUsesHostOutboxAndImportsExactlyOnceAfterRecovery()throws Exception {
        StorageManager unavailable=mock(StorageManager.class);
        when(unavailable.getHostConnection()).thenAnswer(call->DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("pending.db")));
        var plugin=mock(org.bukkit.plugin.java.JavaPlugin.class);when(plugin.getDataFolder()).thenReturn(dir.toFile());
        var host=new DefaultPendingRewardService(plugin,Logger.getAnonymousLogger(),unavailable,null);
        host.initialize();
        doThrow(new java.sql.SQLException("offline")).when(unavailable).getHostConnection();
        assertTrue(host.savePendingBatchConfirmed("outage",List.of(coins(9))));
        assertTrue(host.savePendingBatchConfirmed("outage",List.of(coins(9))));
        assertFalse(host.savePendingBatchConfirmed("outage",List.of(coins(10))));
        doAnswer(call->DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("pending.db"))).when(unavailable).getHostConnection();
        var replay=DefaultPendingRewardService.class.getDeclaredMethod("importOutbox",UUID.class);replay.setAccessible(true);
        var otherStorage=mock(StorageManager.class);
        when(otherStorage.getHostConnection()).thenAnswer(call->DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("other.db")));
        var other=new DefaultPendingRewardService(plugin,Logger.getAnonymousLogger(),otherStorage,null);
        other.initialize();
        replay.invoke(other,player);
        assertTrue(other.loadPending(player).isEmpty());
        replay.invoke(host,player);replay.invoke(host,player);
        assertEquals(1,host.loadPending(player).size());
    }

    @Test void interruptedPageReclaimsOnlyTheRewardThatHadNotStarted() throws Exception {
        assertTrue(service.savePendingBatchConfirmed("settlement",
            List.of(PendingReward.currency(player, "battlepass", "money", 10, "test"),
                PendingReward.currency(player, "battlepass", "money", 5, "unstarted"))));
        Object batch = claim(service);
        var loaded = claimedRewards(batch);
        assertEquals(2, loaded.size());
        var started = loaded.get(0);
        var unstarted = loaded.get(1);
        beginDispatch(batch, started);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("pending.db"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE axs_pending_rewards SET claimed_at = 1");
        }
        var reclaimed = claimedRewards(claim(service));
        assertEquals(List.of(unstarted.id()), reclaimed.stream().map(PendingReward::id).toList());
        assertEquals(0, reclaimed.get(0).attempts());
        assertEquals(-1, service.loadPending(player).stream().filter(row -> row.id() == started.id())
            .findFirst().orElseThrow().attempts());
    }

    @Test void interruptedCurrencyDeliveryFromAnotherModuleStaysPendingWithoutAutomaticReplay() throws Exception {
        assertTrue(service.savePendingBatchConfirmed("abyss-delivery", List.of(coins(5))));
        Object batch = claim(service);
        var reward = claimedRewards(batch).get(0);
        beginDispatch(batch, reward);
        long id = reward.id();
        service.incrementAttempts(id);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("pending.db"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE axs_pending_rewards SET claimed_at = 1");
        }
        assertTrue(claimedRewards(claim(service)).isEmpty());
        assertEquals(-1, service.loadPending(player).get(0).attempts());
    }

    @SuppressWarnings("unchecked")
    private static List<PendingReward> claimedRewards(Object batch) throws Exception {
        var rewards = batch.getClass().getDeclaredMethod("rewards");
        rewards.setAccessible(true);
        return (List<PendingReward>) rewards.invoke(batch);
    }

    private void beginDispatch(Object batch, PendingReward reward) throws Exception {
        var begin = DefaultPendingRewardService.class.getDeclaredMethod("beginDispatch", batch.getClass(), PendingReward.class);
        begin.setAccessible(true);
        assertEquals(Boolean.TRUE, begin.invoke(service, batch, reward));
    }

    @Test void partialLegacyBattlePassMailBatchRequiresReconciliation() throws Exception {
        ModuleRegistry registry = mock(ModuleRegistry.class);
        MailDispatchable mail = mock(MailDispatchable.class);
        when(registry.getCapability(MailDispatchable.class)).thenReturn(mail);
        when(mail.dispatchPreset(any())).thenReturn(MailSendResult.ok(), MailSendResult.failure("unavailable"));
        var host = new DefaultPendingRewardService(null, Logger.getAnonymousLogger(), null, registry);
        var dispatch = DefaultPendingRewardService.class.getDeclaredMethod("dispatchMailPreset", String.class, PendingReward.class);
        dispatch.setAccessible(true);
        var reward = PendingReward.mailPreset(player, "battlepass", List.of("first", "second"), "test");

        var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
            () -> dispatch.invoke(host, "Tester", reward));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        verify(mail, times(2)).dispatchPreset(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"battlepass", "fishing", "abyss"})
    void commandsAreOrderedAndReceiptsWaitForEveryResult(String module) throws Exception {
        String first = module.equals("battlepass") ? "example Tester " + player + " [reward]" : "example Tester [reward]";
        String template = module.equals("battlepass") ? "/example {player} {uuid} [reward]" : "/example {player} [reward]";
        try (var delivery = new CommandDelivery()) {
            delivery.bukkits.when(() -> Bukkit.dispatchCommand(delivery.console, first)).thenReturn(true);
            delivery.bukkits.when(() -> Bukkit.dispatchCommand(delivery.console, "example last")).thenReturn(true);
            delivery.queue(module, List.of(template, "/example last"));
            assertEquals(1, service.loadPending(player).size());

            delivery.tick();
            delivery.bukkits.verify(() -> Bukkit.dispatchCommand(delivery.console, first));
            delivery.bukkits.verify(() -> Bukkit.dispatchCommand(delivery.console, "example last"), never());
            assertEquals(1, service.loadPending(player).size());

            delivery.tick();
            delivery.bukkits.verify(() -> Bukkit.dispatchCommand(delivery.console, "example last"));
            delivery.awaitReceipts();
            assertTrue(service.loadPending(player).isEmpty());
        }
    }

    @Test void shutdownReleasesCommandsProvenNotToHaveRun() throws Exception {
        try (var delivery = new CommandDelivery()) {
            delivery.queue("abyss", List.of("example waiting"));
            assertEquals(1, service.loadPending(player).get(0).attempts());
            ThrottledCommandExecutor.shutdown();
            delivery.tick();
            delivery.awaitReceipts();
            delivery.bukkits.verify(() -> Bukkit.dispatchCommand(delivery.console, "example waiting"), never());
            assertEquals(0, service.loadPending(player).get(0).attempts());
        }
    }

    @Test void aNewConnectionBeforeDispatchReleasesTheUnstartedReward() throws Exception {
        try (var delivery = new CommandDelivery()) {
            delivery.queue("fishing", List.of("example waiting"));
            delivery.auth.when(() -> AuthAccess.snapshot(player)).thenReturn(delivery.session());
            delivery.tick();
            delivery.awaitReceipts();
            delivery.bukkits.verify(() -> Bukkit.dispatchCommand(delivery.console, "example waiting"), never());
            assertEquals(0, service.loadPending(player).get(0).attempts());
        }
    }

    @Test void partialExecutionNeverRunsTheRemainingCommandsOrReclaimsAfterExpiry() throws Exception {
        try (var delivery = new CommandDelivery()) {
            delivery.bukkits.when(() -> Bukkit.dispatchCommand(delivery.console, "example first")).thenReturn(true);
            delivery.bukkits.when(() -> Bukkit.dispatchCommand(delivery.console, "example uncertain")).thenReturn(false);
            delivery.queue("abyss", List.of("example first", "example uncertain", "example last"));
            delivery.tick();
            delivery.tick();
            delivery.awaitReceipts();
            delivery.bukkits.verify(() -> Bukkit.dispatchCommand(delivery.console, "example first"));
            delivery.bukkits.verify(() -> Bukkit.dispatchCommand(delivery.console, "example uncertain"));
            delivery.bukkits.verify(() -> Bukkit.dispatchCommand(delivery.console, "example last"), never());
            try (var connection = storage.getHostConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE axs_pending_rewards SET claimed_at=1");
            }
            var reclaimed = claim(service);
            var rewards = reclaimed.getClass().getDeclaredMethod("rewards");
            rewards.setAccessible(true);
            assertEquals(List.of(), rewards.invoke(reclaimed));
            assertEquals(1, service.loadPending(player).get(0).attempts());
        }
    }

    private Object claim(DefaultPendingRewardService host) throws Exception {
        var claim = DefaultPendingRewardService.class.getDeclaredMethod("claimAndLoadPending", UUID.class);
        claim.setAccessible(true);
        return claim.invoke(host, player);
    }

    private final class CommandDelivery implements AutoCloseable {
        final MockedStatic<Bukkit> bukkits = mockStatic(Bukkit.class);
        final MockedStatic<AuthAccess> auth = mockStatic(AuthAccess.class);
        final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        final Player target = mock(Player.class);
        final AtomicReference<Runnable> timer = new AtomicReference<>();
        final AuthSnapshot admission = session();
        final PendingCommandReceipts writer;
        final DefaultPendingRewardService host;

        CommandDelivery() throws Exception {
            StorageManager receiptStorage = mock(StorageManager.class);
            var current = Thread.currentThread();
            when(receiptStorage.getHostConnection()).thenAnswer(call -> {
                assertNotSame(current, Thread.currentThread());
                return storage.getHostConnection();
            });
            writer = new PendingCommandReceipts(receiptStorage, Logger.getAnonymousLogger());
            host = new DefaultPendingRewardService(null, Logger.getAnonymousLogger(), storage, null, writer);
            var scheduler = mock(BukkitScheduler.class);
            var plugin = mock(Plugin.class);
            when(scheduler.runTaskAsynchronously(isNull(), any(Runnable.class))).thenAnswer(call -> {
                call.<Runnable>getArgument(1).run();
                return mock(BukkitTask.class);
            });
            when(scheduler.runTask(isNull(), any(Runnable.class))).thenAnswer(call -> {
                call.<Runnable>getArgument(1).run();
                return mock(BukkitTask.class);
            });
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenAnswer(call -> {
                timer.set(call.getArgument(1));
                return mock(BukkitTask.class);
            });
            when(target.getName()).thenReturn("Tester");
            when(target.getUniqueId()).thenReturn(player);
            when(target.isOnline()).thenReturn(true);
            bukkits.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkits.when(Bukkit::getConsoleSender).thenReturn(console);
            bukkits.when(Bukkit::getLogger).thenReturn(Logger.getAnonymousLogger());
            auth.when(() -> AuthAccess.snapshot(player)).thenReturn(admission);
            ThrottledCommandExecutor.init(plugin, 1);
        }

        AuthSnapshot session() {
            return new AuthSnapshot(player, UUID.randomUUID(), 1L, player, AuthSnapshot.Registration.REGISTERED,
                AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        }

        void queue(String module, List<String> commands) throws Exception {
            assertTrue(service.savePendingBatchConfirmed("command-reward",
                List.of(PendingReward.command(player, module, commands, "test"))));
            var batch = claim(host);
            var flush = DefaultPendingRewardService.class.getDeclaredMethod("flushPending",
                Player.class, batch.getClass(), AuthSnapshot.class);
            flush.setAccessible(true);
            flush.invoke(host, target, batch, admission);
            assertNotNull(timer.get());
        }

        void tick() {
            timer.get().run();
        }

        void awaitReceipts() throws Exception {
            writer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }

        @Override public void close() throws Exception {
            try {
                ThrottledCommandExecutor.shutdown();
                awaitReceipts();
            } finally {
                ThrottledCommandExecutor.init(null, 0);
                auth.close();
                bukkits.close();
            }
        }
    }
}
