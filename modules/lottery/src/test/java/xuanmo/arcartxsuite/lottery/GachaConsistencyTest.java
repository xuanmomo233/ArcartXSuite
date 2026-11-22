package xuanmo.arcartxsuite.lottery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.lottery.config.CostConfig;
import xuanmo.arcartxsuite.lottery.config.ExchangeShopItem;
import xuanmo.arcartxsuite.lottery.config.GachaPoolConfig;
import xuanmo.arcartxsuite.lottery.config.LotteryModuleConfiguration;
import xuanmo.arcartxsuite.lottery.config.PoolDefinition;
import xuanmo.arcartxsuite.lottery.config.PoolType;
import xuanmo.arcartxsuite.lottery.model.CaseResult;
import xuanmo.arcartxsuite.lottery.model.PlayerGachaState;
import xuanmo.arcartxsuite.lottery.model.PoolItem;
import xuanmo.arcartxsuite.lottery.storage.JdbcLotteryRepository;

class GachaConsistencyTest {
    private static final Logger LOGGER = Logger.getAnonymousLogger();
    private static final String POOL = "banner";
    private static final String GROUP = "shared";
    private final UUID playerId = UUID.randomUUID();
    private final UUID operationId = UUID.randomUUID();
    @TempDir Path directory;
    private SQLiteDataSource source;
    private JdbcLotteryRepository repository;
    private final LinkedBlockingQueue<Runnable> mainTasks = new LinkedBlockingQueue<>();
    private final List<LotteryService> services = new ArrayList<>();
    private Server server;
    private java.lang.reflect.Field serverField;
    private Object previousServer;
    private Thread owner;

    @BeforeEach
    void setUp() throws Exception {
        source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("lottery.sqlite"));
        repository = new JdbcLotteryRepository(source, StorageDescriptor.sqlite("lottery.sqlite"), directory.toFile(), LOGGER);
        repository.initialize();
        owner = Thread.currentThread();
        server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(server.isPrimaryThread()).thenAnswer(call -> Thread.currentThread() == owner);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> {
            mainTasks.add(call.getArgument(1));
            return null;
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
        var auth = mock(AuthRuntimeCapability.class);
        when(auth.snapshot(playerId)).thenReturn(new AuthSnapshot(playerId, UUID.randomUUID(), 1, null,
            AuthSnapshot.Registration.UNKNOWN, AuthSnapshot.Stage.ADMITTED, "test", Set.of(), ""));
        AuthAccess.install(() -> auth);
    }

    @AfterEach void restore() throws Exception {
        try {
            for (var service : services) {
                service.shutdown();
                await(service.stopped());
            }
        } finally {
            AuthAccess.install(() -> null);
            if (serverField != null) serverField.set(null, previousServer);
        }
    }

    private <T> T await(CompletionStage<T> operation) throws Exception {
        var result = operation.toCompletableFuture();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!result.isDone() && System.nanoTime() < deadline) {
            Runnable callback = mainTasks.poll(10, TimeUnit.MILLISECONDS);
            if (callback != null) callback.run();
        }
        return result.get(0, TimeUnit.SECONDS);
    }

    @Test
    void commitMovesPityHistoryAndPointsTogether() throws SQLException {
        assertTrue(repository.addPoints(playerId, "ticket", 20));
        assertTrue(repository.beginGachaOperation(playerId, operationId, POOL, "cost"));
        var prize = new PoolItem("prize", "Prize", PoolItem.DeliveryType.DIRECT, List.of(), null,
            "minecraft", "diamond", null, null, List.of(), 1, 1, null);
        repository.commitGachaPull(operationId, pity(7), POOL, 1, "[prize]", false, List.of(prize),
            Map.of("ticket", 3L), Map.of("ticket", 1L));
        assertEquals(18, repository.getPoint(playerId, "ticket"));
        assertEquals(pity(7), repository.loadGachaState(playerId, GROUP));
        assertEquals("[prize]", repository.getGachaHistory(playerId, POOL, 5).get(0).itemsJson());
        assertFalse(repository.changeGachaOperation(playerId, operationId, "DEBITING", "REFUNDING"));
        assertEquals("COMMITTED", repository.findGachaOperation(playerId).status());
        assertEquals(List.of(prize), repository.loadCommittedPrizes(playerId, operationId).items());
        assertTrue(repository.beginPrize(playerId, operationId, 0, "开始发奖"));
        assertNull(repository.loadCommittedPrizes(playerId, operationId));
        assertTrue(repository.completePrize(playerId, operationId, 0, "发放确认"));
        assertEquals(1, repository.loadCommittedPrizes(playerId, operationId).nextIndex());
        assertFalse(repository.beginPrize(playerId, operationId, 0, "重复发放"));
    }

    @Test
    void failedHistoryWriteRollsBackPityPointsAndCommitMarker() throws SQLException {
        assertTrue(repository.addPoints(playerId, "ticket", 20));
        assertTrue(repository.beginGachaOperation(playerId, operationId, POOL, "cost"));
        failHistoryInsert();
        assertThrows(SQLException.class, () -> repository.commitGachaPull(operationId, pity(7),
            POOL, 1, "[prize]", false, List.of(), Map.of("ticket", 3L), Map.of("bonus", 1L)));
        assertEquals(20, repository.getPoint(playerId, "ticket"));
        assertEquals(0, repository.getPoint(playerId, "bonus"));
        assertEquals(PlayerGachaState.empty(playerId, GROUP), repository.loadGachaState(playerId, GROUP));
        assertTrue(repository.getGachaHistory(playerId, POOL, 5).isEmpty());
        assertTrue(repository.changeGachaOperation(playerId, operationId, "DEBITING", "REFUNDING"));
    }

    @Test
    void caseLogFailureRollsBackCountPointsAndReservationTransition() throws SQLException {
        var item = new PoolItem("prize", "Prize", PoolItem.DeliveryType.DIRECT, List.of(), null,
            "minecraft", "diamond", null, null, List.of(), 1, 1, "COMMON");
        var results = List.of(new CaseResult(item, "COMMON", 1), new CaseResult(item, "COMMON", 1));
        assertTrue(repository.addPoints(playerId, "ticket", 20));
        assertTrue(repository.beginGachaOperation(playerId, operationId, POOL, "开箱"));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TRIGGER test_reject_case BEFORE INSERT ON case_log
                BEGIN SELECT RAISE(ABORT, 'simulated case write failure'); END
                """);
        }
        assertThrows(SQLException.class, () -> repository.commitCaseOpen(playerId, operationId,
            POOL, results, Map.of("ticket", 4L), Map.of("bonus", 2L)));
        assertEquals(0, repository.loadCaseState(playerId, POOL).openCount());
        assertEquals(20, repository.getPoint(playerId, "ticket"));
        assertEquals(0, repository.getPoint(playerId, "bonus"));
        assertEquals("DEBITING", repository.findGachaOperation(playerId).status());

        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER test_reject_case");
        }
        repository.commitCaseOpen(playerId, operationId, POOL, results, Map.of("ticket", 4L), Map.of("bonus", 2L));
        assertEquals(2, repository.loadCaseState(playerId, POOL).openCount());
        assertEquals(16, repository.getPoint(playerId, "ticket"));
        assertEquals(2, repository.getPoint(playerId, "bonus"));
        assertEquals("COMMITTED", repository.findGachaOperation(playerId).status());
    }

    @Test
    void reservationSpansPoolsAndReconciliationChecksExactOperationAndPhase() throws SQLException {
        var secondRepository = new JdbcLotteryRepository(source, StorageDescriptor.sqlite("lottery.sqlite"),
            directory.toFile(), LOGGER);
        secondRepository.initialize();
        var nextOperation = UUID.randomUUID();
        assertTrue(repository.beginGachaOperation(playerId, operationId, POOL, "cost"));
        assertFalse(secondRepository.beginGachaOperation(playerId, nextOperation, "other-banner", "cost"));
        assertTrue(repository.appendGachaEvidence(playerId, operationId, "DEBITING", "确认扣费"));
        assertTrue(repository.changeGachaOperation(playerId, operationId, "DEBITING", "DEBIT_REVIEW"));
        assertFalse(secondRepository.resolveGachaOperation(playerId, operationId, "DEBITING", "checked"));
        assertFalse(secondRepository.resolveGachaOperation(playerId, nextOperation, "DEBIT_REVIEW", "checked"));
        assertTrue(secondRepository.findGachaOperation(playerId).details().contains("确认扣费"));
        assertTrue(secondRepository.resolveGachaOperation(playerId, operationId, "DEBIT_REVIEW", "checked"));
        assertNull(repository.findGachaOperation(playerId));
        assertTrue(repository.beginGachaOperation(playerId, nextOperation, POOL, "next"));
        assertFalse(secondRepository.resolveGachaOperation(playerId, operationId, "DEBITING", "stale"));
    }

    @Test
    void storageReadFailureNeverChargesOrDelivers() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TABLE gacha_state");
        }
        Player player = player();
        var bridge = bridge();
        var items = mock(ItemSourceRegistry.class);
        var result = await(service(bridge, items).pullGacha(player, POOL, 1));
        assertTrue(result.items().isEmpty());
        verify(bridge, never()).withdraw(any(), any());
        verifyNoInteractions(items);
        assertNull(repository.findGachaOperation(playerId));
    }

    @Test
    void failedCommitRefundsConfirmedCurrencyWithoutDeliveringPrize() throws Exception {
        failHistoryInsert();
        Player player = player();
        var bridge = bridge();
        when(bridge.withdraw(player, BigDecimal.TEN)).thenAnswer(call -> {
            assertSame(owner, Thread.currentThread());
            return CurrencyTransactionResult.ok();
        });
        when(bridge.deposit(player, BigDecimal.TEN)).thenReturn(CurrencyTransactionResult.ok());
        var items = mock(ItemSourceRegistry.class);
        assertTrue(await(service(bridge, items).pullGacha(player, POOL, 1)).items().isEmpty());
        var calls = inOrder(bridge);
        calls.verify(bridge).withdraw(player, BigDecimal.TEN);
        calls.verify(bridge).deposit(player, BigDecimal.TEN);
        verifyNoInteractions(items);
        assertNull(repository.findGachaOperation(playerId));
        assertEquals(PlayerGachaState.empty(playerId, GROUP), repository.loadGachaState(playerId, GROUP));
    }

    @Test
    void exchangeReportsFailureWhenRewardNeedsManualReview() throws Exception {
        Player player = player();
        var bridge = bridge();
        when(bridge.withdraw(player, BigDecimal.TEN)).thenReturn(CurrencyTransactionResult.ok());
        var items = mock(ItemSourceRegistry.class);
        assertFalse(await(service(bridge, items).exchange(player, "exchange-prize")));
        verify(bridge).withdraw(player, BigDecimal.TEN);
        verify(bridge, never()).deposit(any(), any());
        assertEquals(1, repository.getExchangeCount(playerId, "exchange-prize"));
        assertEquals(1, repository.getPendingClaims(playerId).size());
        assertNull(repository.getPendingClaims(playerId).get(0).itemData());
        assertEquals("PRIZE_REVIEW", repository.findGachaOperation(playerId).status());
    }

    @Test
    void aQueuedMainEffectIsCancelledOnStopAndDoesNotCharge() throws Exception {
        Player player = player();
        var bridge = bridge();
        var service = service(bridge, mock(ItemSourceRegistry.class));
        var future = service.pullGacha(player, POOL, 1);
        Runnable staleTask = mainTasks.poll(3, TimeUnit.SECONDS);
        assertNotNull(staleTask);
        assertFalse(future.toCompletableFuture().isDone());
        service.shutdown();
        assertTrue(await(future).items().isEmpty());
        await(service.stopped());
        staleTask.run();
        assertNull(repository.findGachaOperation(playerId));
        verify(bridge, never()).withdraw(any(), any());
    }

    @Test
    void stopAfterCommitResumesTheUnstartedPrizeWithoutAnotherDebit() throws Exception {
        Player player = player();
        var bridge = bridge();
        when(bridge.withdraw(player, BigDecimal.TEN)).thenReturn(CurrencyTransactionResult.ok());
        var items = mock(ItemSourceRegistry.class);
        var service = service(bridge, items);
        var future = service.pullGacha(player, POOL, 1);
        for (int i = 0; i < 2; i++) {
            Runnable mainEffect = mainTasks.poll(3, TimeUnit.SECONDS);
            assertNotNull(mainEffect);
            mainEffect.run();
        }
        Runnable queuedPrize = mainTasks.poll(3, TimeUnit.SECONDS);
        assertNotNull(queuedPrize);
        assertEquals("DELIVERING", repository.findGachaOperation(playerId).status());
        service.shutdown();
        assertTrue(await(future).items().isEmpty());
        await(service.stopped());
        queuedPrize.run();
        assertEquals("COMMITTED", repository.findGachaOperation(playerId).status());
        assertTrue(repository.findGachaOperation(playerId).details().contains("发放前取消"));
        assertEquals(0, repository.loadCommittedPrizes(playerId, repository.findGachaOperation(playerId).id()).nextIndex());
        verifyNoInteractions(items);
        await(service(bridge, items).claimPending(player));
        assertEquals("PRIZE_REVIEW", repository.findGachaOperation(playerId).status());
        verify(bridge, times(1)).withdraw(player, BigDecimal.TEN);
        verify(bridge, never()).deposit(any(), any());
        verify(items).generateItem(anyString(), anyString(), anyInt(), nullable(String.class), eq(player));
    }

    @Test
    void exchangeLimitFailureRollsBackPointsAndCommitState() throws Exception {
        assertTrue(repository.addPoints(playerId, "ticket", 10));
        assertTrue(repository.beginGachaOperation(playerId, operationId, POOL, "first exchange"));
        var prize = new PoolItem("prize", "Prize", PoolItem.DeliveryType.DIRECT, List.of(), null,
            "minecraft", "diamond", null, null, List.of(), 1, 1, null);
        repository.commitExchange(playerId, operationId, "prize", 1, "item", prize, Map.of("ticket", 3L));
        assertTrue(repository.finishGachaOperation(playerId, operationId, "COMMITTED"));
        UUID second = UUID.randomUUID();
        assertTrue(repository.beginGachaOperation(playerId, second, POOL, "second exchange"));
        assertThrows(SQLException.class, () -> repository.commitExchange(playerId, second, "prize", 1, "item", prize, Map.of("ticket", 3L)));
        assertEquals(7, repository.getPoint(playerId, "ticket"));
        assertEquals(1, repository.getExchangeCount(playerId, "prize"));
        assertEquals("DEBITING", repository.findGachaOperation(playerId).status());
    }

    private PlayerGachaState pity(int count) {
        return new PlayerGachaState(playerId, GROUP, count, 1, false, 0, null);
    }

    private void failHistoryInsert() throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TRIGGER test_reject_history BEFORE INSERT ON gacha_log
                BEGIN SELECT RAISE(ABORT, 'simulated history write failure'); END
                """);
        }
    }

    private Player player() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(server.getPlayer(playerId)).thenReturn(player);
        return player;
    }

    private CurrencyBridgeAPI.CurrencyBridge bridge() {
        var bridge = mock(CurrencyBridgeAPI.CurrencyBridge.class);
        when(bridge.available()).thenReturn(true);
        when(bridge.balance(any())).thenReturn(BigDecimal.valueOf(100));
        return bridge;
    }

    private LotteryService service(CurrencyBridgeAPI.CurrencyBridge bridge, ItemSourceRegistry items) {
        var currencies = mock(CurrencyBridgeAPI.class);
        when(currencies.bridge("gold")).thenReturn(bridge);
        var costs = new CostConfig(List.of(new CostConfig.CostEntry(CostConfig.CostMode.CURRENCY, "gold", 10,
            null, null, null)), List.of());
        var prize = new PoolItem("prize", "Prize", PoolItem.DeliveryType.DIRECT, List.of(), null,
            "minecraft", "diamond", null, null, List.of(), 1, 1, null);
        var config = new GachaPoolConfig(GachaPoolConfig.GachaPoolType.STANDARD, 90, 10, 73, 0, 0, 0,
            0, 0, GROUP, List.of(prize), List.of(prize), List.of(prize), List.of(prize), List.of(prize));
        var pool = new PoolDefinition(POOL, PoolType.GACHA, true, POOL, "", "", null, costs, config,
            null, null, false, Map.of());
        var exchange = new ExchangeShopItem("exchange-prize", "Prize", null, POOL, costs.single(), prize, 1);
        var module = new LotteryModuleConfiguration(null, null, "", "", false, Map.of(), Map.of(POOL, pool),
            Map.of(exchange.id(), exchange));
        var service = new LotteryService(mock(JavaPlugin.class), module, repository, currencies, items, null, null, LOGGER, null);
        services.add(service);
        return service;
    }
}
