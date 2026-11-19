package xuanmo.arcartxsuite.market.auction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;
import xuanmo.arcartxsuite.market.MarketLedger;
import xuanmo.arcartxsuite.market.MarketWork;
import xuanmo.arcartxsuite.market.storage.AuctionBidOrder;
import xuanmo.arcartxsuite.market.storage.AuctionSettlement;
import xuanmo.arcartxsuite.market.storage.JdbcMarketRepository;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;

class AuctionPublicationFlowTest {
    @TempDir Path directory;

    @Test void stoppingBeforeCancellationAuthorizationCompletesWithoutChangingTheListing() throws Exception {
        runCancellation(true);
    }

    @Test void stoppingWhileCancellationCommitsStillReturnsTheDurableResult() throws Exception {
        runCancellation(false);
    }

    private void runCancellation(boolean stopBeforeAuthorization) throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var scheduler = mock(BukkitScheduler.class);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return null;
        });
        var server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        serverField.set(null, server);
        var work = new MarketWork(plugin);
        var repository = mock(MarketRepository.class);
        UUID seller = UUID.randomUUID();
        long now = System.currentTimeMillis();
        var listing = new AuctionListing(42, seller, "Seller", "item", "Item", "", "", "other", 0,
            10, 0, null, "coin", AuctionListing.ListingType.AUCTION, AuctionListing.ListingStatus.ACTIVE, now, now + 60_000);
        Thread main = Thread.currentThread();
        when(repository.getListing(42)).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            return listing;
        });
        var entered = new CompletableFuture<AuctionSettlement>();
        var commit = new CountDownLatch(1);
        when(repository.cancelAuction(any(), eq(seller), anyBoolean())).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            entered.complete(call.getArgument(0));
            assertTrue(commit.await(3, TimeUnit.SECONDS));
            return true;
        });
        var service = new AuctionService(work, plugin, MarketModuleConfiguration.AuctionConfiguration.defaults(),
            null, repository, mock(RedisMarketCache.class), null, null, null, Logger.getAnonymousLogger(), null, null, null);
        try {
            var result = service.cancelListing(seller, 42, false, () -> {
                assertSame(main, Thread.currentThread());
                return true;
            });
            Runnable authorize = callbacks.poll(3, TimeUnit.SECONDS);
            assertNotNull(authorize);
            assertFalse(result.isDone());
            if (stopBeforeAuthorization) {
                service.shutdown();
                assertFalse(result.get(3, TimeUnit.SECONDS));
                authorize.run();
                verify(repository, never()).cancelAuction(any(), any(), anyBoolean());
            } else {
                authorize.run();
                assertEquals("ITEM", entered.get(3, TimeUnit.SECONDS).deliveries().get(0).kind());
                service.shutdown();
                assertFalse(result.isDone());
                commit.countDown();
                assertTrue(result.get(3, TimeUnit.SECONDS));
            }
        } finally {
            commit.countDown();
            try { service.shutdown(); work.drain().toCompletableFuture().get(3, TimeUnit.SECONDS); }
            finally { serverField.set(null, previous); }
        }
    }

    @Test void preparedBidConfirmsBothDebitAndReceiptOffThread() throws Exception {
        runBid(false);
    }

    @Test void stoppingAfterReservationSettlesWithoutCharging() throws Exception {
        runBid(true);
    }

    private void runBid(boolean stopBeforePayment) throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var scheduler = mock(BukkitScheduler.class);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return null;
        });
        var server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        var bidder = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(bidder.getUniqueId()).thenReturn(playerId);
        when(bidder.getName()).thenReturn("Buyer");
        var auth = mock(AuthRuntimeCapability.class);
        when(auth.snapshot(playerId)).thenReturn(new AuthSnapshot(playerId, UUID.randomUUID(), 1, playerId,
            AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "test", Set.of(), ""));
        var serverField = Bukkit.class.getDeclaredField("server");
        var authField = AuthAccess.class.getDeclaredField("runtime");
        serverField.setAccessible(true);
        authField.setAccessible(true);
        Object previousServer = serverField.get(null);
        Object previousAuth = authField.get(null);
        serverField.set(null, server);
        AuthAccess.install(() -> auth);
        var work = new MarketWork(plugin);
        var repository = mock(MarketRepository.class);
        long now = System.currentTimeMillis();
        var listing = new AuctionListing(42, UUID.randomUUID(), "Seller", "item", "Item", "", "", "other", 0,
            10, 0, null, "coin", AuctionListing.ListingType.AUCTION, AuctionListing.ListingStatus.ACTIVE, now, now + 60_000);
        when(repository.getListing(42)).thenReturn(listing);
        Thread main = Thread.currentThread();
        var reserved = new CompletableFuture<AuctionBidOrder>();
        when(repository.reserveAuctionBid(any())).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            reserved.complete(call.getArgument(0));
            return true;
        });
        var payment = new CompletableFuture<CurrencyTransactionResult>();
        when(repository.finishAuctionBid(any(), any(), anyLong())).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            CurrencyTransactionResult result = call.getArgument(1);
            payment.complete(result);
            return result.success() ? AuctionBidOrder.Outcome.PLACED : AuctionBidOrder.Outcome.DECLINED;
        });
        var currencies = mock(CurrencyBridgeAPI.class);
        var bridge = mock(CurrencyBridgeAPI.CurrencyBridge.class);
        var background = mock(java.util.function.Supplier.class);
        when(currencies.bridge("coin")).thenReturn(bridge);
        when(bridge.available()).thenReturn(true);
        when(bridge.prepareBackgroundDebit(eq(bidder), any())).thenReturn(background);
        when(background.get()).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            assertTrue(reserved.isDone());
            return CurrencyTransactionResult.ok();
        });
        var messages = mock(MarketModuleConfiguration.MessagesConfiguration.class);
        var service = new AuctionService(work, plugin, MarketModuleConfiguration.AuctionConfiguration.defaults(),
            messages, repository, mock(RedisMarketCache.class), null, currencies, null, Logger.getAnonymousLogger(), null, null, null);
        service.setLedger(new MarketLedger(repository, Logger.getAnonymousLogger()));
        try {
            var reply = new CompletableFuture<AuctionService.BidResult>();
            service.placeBid(bidder, new AuctionService.BidConfirmation(42, 25, AuctionBidTerms.revision(listing),
                System.nanoTime() + TimeUnit.SECONDS.toNanos(10)), currencies, () -> true, reply::complete);
            Runnable prepare = callbacks.poll(3, TimeUnit.SECONDS);
            assertNotNull(prepare);
            prepare.run();
            assertEquals("25.0", reserved.get(3, TimeUnit.SECONDS).debit().amount());
            assertFalse(reply.isDone());
            verifyNoInteractions(background);
            if (stopBeforePayment) {
                service.shutdown();
                assertFalse(payment.get(3, TimeUnit.SECONDS).success());
                verifyNoInteractions(background);
            } else {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (!payment.isDone() && System.nanoTime() < deadline) {
                    service.drainBidPayments();
                    Thread.sleep(1);
                }
                assertTrue(payment.get(3, TimeUnit.SECONDS).success());
                Runnable finish = callbacks.poll(3, TimeUnit.SECONDS);
                assertNotNull(finish);
                finish.run();
                assertTrue(reply.get(3, TimeUnit.SECONDS).success());
                verify(background).get();
            }
            verify(bridge, never()).withdraw(any(), any());
        } finally {
            try {
                service.shutdown();
                work.drain().toCompletableFuture().get(3, TimeUnit.SECONDS);
            } finally {
                serverField.set(null, previousServer);
                authField.set(null, previousAuth);
            }
        }
    }

    @Test void waitingForDurableConfirmationDoesNotPublishAnOrdinaryEvent() throws Exception {
        var bus = mock(xuanmo.arcartxsuite.api.capability.EventBusCapability.class);
        var fields = Map.of("event_id", "auction-expiry:1");
        var committed = new CompletableFuture<Boolean>();
        when(bus.publishConfirmed("axs.market.auction_purchased", null, fields)).thenReturn(committed);
        var service = mock(xuanmo.arcartxsuite.market.MarketService.class, CALLS_REAL_METHODS);
        var provider = service.getClass().getDeclaredField("eventBusProvider");
        provider.setAccessible(true);
        provider.set(service, (java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.EventBusCapability>) () -> bus);
        var logger = service.getClass().getDeclaredField("logger");
        logger.setAccessible(true); logger.set(service, Logger.getAnonymousLogger());
        var publish = service.getClass().getDeclaredMethod("publishAuctionEvent", String.class,
            org.bukkit.entity.Player.class, Map.class);
        publish.setAccessible(true);
        var returned = (java.util.concurrent.CompletionStage<?>) publish.invoke(service,
            "axs.market.auction_purchased", null, fields);
        assertFalse(returned.toCompletableFuture().isDone());
        committed.complete(true);
        assertEquals(Boolean.TRUE, returned.toCompletableFuture().join());
        verify(bus).publishConfirmed("axs.market.auction_purchased", null, fields);
        verify(bus, never()).publish("axs.market.auction_purchased", null, fields);
    }

    @Test void aTaxPermissionCannotIncreaseTheBaseRate() throws Exception {
        var config = mock(MarketModuleConfiguration.AuctionConfiguration.class);
        when(config.transactionTaxRate()).thenReturn(0.05);
        when(config.taxDiscount()).thenReturn(Map.of("vip", 0.08), Map.of("vip", 0.02));
        var player = mock(org.bukkit.entity.Player.class);
        when(player.hasPermission("vip")).thenReturn(true);
        var service = mock(AuctionService.class, CALLS_REAL_METHODS);
        var field = AuctionService.class.getDeclaredField("config");
        field.setAccessible(true); field.set(service, config);
        var tax = AuctionService.class.getDeclaredMethod("getEffectiveTaxRateOnline", org.bukkit.entity.Player.class);
        tax.setAccessible(true);
        assertEquals(0.05, (double) tax.invoke(service, player));
        assertEquals(0.02, (double) tax.invoke(service, player));
    }

    @Test void sqliteReplaysACommittedEventUntilTheConsumerAcknowledgesIt() throws Exception {
        exercise("jdbc:sqlite:" + directory.resolve("publication.sqlite"), false);
    }

    @Test void mysqlReplaysACommittedEventUntilTheConsumerAcknowledgesIt() throws Exception {
        String url = System.getenv("AXS_EVENTPACKET_MYSQL_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(url != null
            && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/eventpacket_verify(?:_[0-9]+)?"));
        exercise(url, true);
    }

    private void exercise(String url, boolean mysql) throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(call -> {
            var connection = mysql ? DriverManager.getConnection(url, "root", "") : DriverManager.getConnection(url);
            if (!mysql) try (var statement = connection.createStatement()) { statement.execute("PRAGMA busy_timeout=5000"); }
            return connection;
        });
        var descriptor = mysql ? StorageDescriptor.mysql("127.0.0.1", 1, "isolated", "root", "", 2, "producer_")
            : StorageDescriptor.sqlite("publication.sqlite").withTablePrefix("producer_");
        var logger = Logger.getAnonymousLogger();
        var repository = new JdbcMarketRepository(source, descriptor, directory.toFile(), logger);
        repository.initialize();
        var seller = UUID.randomUUID(); var buyer = UUID.randomUUID();
        long now = System.currentTimeMillis();
        var listing = new AuctionListing(0, seller, "Seller", "snapshot", "Reward", "", "", "other", 0,
            20, 20, buyer, "money", AuctionListing.ListingType.AUCTION, AuctionListing.ListingStatus.ACTIVE,
            now - 100_000, now - 1);
        assertTrue(repository.insertListing(listing));
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var scheduler = mock(BukkitScheduler.class);
        var cache = mock(RedisMarketCache.class);
        var currencies = mock(CurrencyBridgeAPI.class);
        when(currencies.format(eq("money"), any(BigDecimal.class))).thenAnswer(call -> call.getArgument(1).toString());
        var serializer = mock(AuctionItemSerializer.class);
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STONE);
        when(serializer.deserialize("snapshot")).thenReturn(item);
        var seen = new ArrayList<Map<String, String>>();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
            doAnswer(call -> { call.<Runnable>getArgument(1).run(); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
            var service = service(plugin, repository, cache, currencies, serializer, logger);
            service.setEventPublisher((topic, player, fields) -> {
                assertEquals("axs.market.auction_purchased", topic);
                assertEquals(AuctionListing.ListingStatus.SOLD, repository.getListing(listing.getId()).getStatus());
                assertEquals(1, repository.getHistoryByPlayer(buyer, 0, 10).size());
                seen.add(Map.copyOf(fields));
                return CompletableFuture.completedFuture(false);
            });
            assertEquals(1, service.triggerExpiredProcessing());
            invoke(service, "settleExpired");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (repository.getHistoryByPlayer(buyer, 0, 10).isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(1, repository.getHistoryByPlayer(buyer, 0, 10).size());
            assertEquals(1, repository.operations("READY", buyer, 0, 20).stream().filter(o -> o.kind().equals("ITEM")).count());
            invoke(service, "publishSettlements");
            assertEquals(1, seen.size());
            assertEquals("auction-expiry:" + listing.getId(), seen.get(0).get("event_id"));
            assertEquals(buyer.toString(), seen.get(0).get("buyer_uuid"));
            assertEquals("20.0", seen.get(0).get("price"));
            assertEquals("1.0", seen.get(0).get("tax"));
            stop(service);
            // Resume from a new service and repository after the first consumer rejected the event.
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE producer_auction_publications SET lease_until=0");
            }
            var recovered = new JdbcMarketRepository(source, descriptor, directory.toFile(), logger);
            recovered.initialize();
            var replacement = service(plugin, recovered, cache, currencies, serializer, logger);
            replacement.setEventPublisher((topic, player, fields) -> {
                seen.add(Map.copyOf(fields));
                return CompletableFuture.completedFuture(true);
            });
            invoke(replacement, "publishSettlements");
            assertEquals(2, seen.size());
            assertEquals(seen.get(0), seen.get(1));
            assertTrue(recovered.claimAuctionPublications("observer", Long.MAX_VALUE - 30_000, 10).isEmpty());
            invoke(replacement, "publishSettlements");
            assertEquals(2, seen.size());
            assertEquals(1, recovered.getHistoryByPlayer(buyer, 0, 10).size());
            stop(replacement);
        }
    }

    private AuctionService service(JavaPlugin plugin, JdbcMarketRepository repository, RedisMarketCache cache,
                                   CurrencyBridgeAPI currencies, AuctionItemSerializer serializer, Logger logger) {
        return new AuctionService(new xuanmo.arcartxsuite.market.MarketWork(plugin), plugin, MarketModuleConfiguration.AuctionConfiguration.defaults(),
            MarketModuleConfiguration.MessagesConfiguration.defaults(), repository, cache, null, currencies, serializer,
            logger, null, null, null);
    }

    private static void invoke(AuctionService service, String method) throws Exception {
        var operation = AuctionService.class.getDeclaredMethod(method);
        operation.setAccessible(true);
        operation.invoke(service);
    }

    private static void stop(AuctionService service) throws Exception {
        var field = AuctionService.class.getDeclaredField("work");
        field.setAccessible(true);
        var work = (xuanmo.arcartxsuite.market.MarketWork) field.get(service);
        work.stopCallbacks();
        service.shutdown();
        work.drain().toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS);
    }
}
