package xuanmo.arcartxsuite.market;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.market.auction.AuctionBidTerms;
import xuanmo.arcartxsuite.market.auction.AuctionListing;
import xuanmo.arcartxsuite.market.auction.AuctionItemSerializer;
import xuanmo.arcartxsuite.market.auction.AuctionRoomService;
import xuanmo.arcartxsuite.market.auction.AuctionRoomSnapshot;
import xuanmo.arcartxsuite.market.auction.AuctionService;
import xuanmo.arcartxsuite.market.config.MarketModuleConfiguration;
import xuanmo.arcartxsuite.market.dynamic.DynamicShopService;
import xuanmo.arcartxsuite.market.playershop.PlayerShopService;
import xuanmo.arcartxsuite.market.playershop.PlayerShopListing;
import xuanmo.arcartxsuite.market.storage.MarketRepository;
import xuanmo.arcartxsuite.market.storage.MarketOperation;
import xuanmo.arcartxsuite.market.storage.RedisMarketCache;

class MarketShutdownTest {
    @Test
    void shutdownWaitsForStartupWorkBeforeClosingTheCache() throws Exception {
        var service = new MarketService(mock(JavaPlugin.class), mock(MarketModuleConfiguration.class), null,
            null, null, null, null, null, null, null, null, Logger.getAnonymousLogger(), null, null, null, null, null);
        var scope = mock(xuanmo.arcartxsuite.api.storage.ModuleStorage.class);
        var preparing = new CompletableFuture<Void>();
        when(scope.initialize(any())).thenReturn(preparing);
        service.prepareStorage(scope, () -> {});
        var cache = mock(RedisMarketCache.class);
        set(service, "redisCache", cache);
        try {
            service.shutdown();
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
            verify(cache, never()).shutdown();
        } finally {
            preparing.complete(null);
        }
        service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        verify(cache).shutdown();
    }

    @Test
    void playerShopPurchaseDebitsAfterReservationAndCompletesAfterCommitEvenDuringStop() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var work = new MarketWork(plugin);
        var repository = mock(MarketRepository.class);
        var serializer = mock(AuctionItemSerializer.class);
        UUID buyerId = UUID.randomUUID();
        var buyer = mock(Player.class);
        when(buyer.getUniqueId()).thenReturn(buyerId);
        var auth = mock(AuthRuntimeCapability.class);
        when(auth.snapshot(buyerId)).thenReturn(new AuthSnapshot(buyerId, UUID.randomUUID(), 1, buyerId,
            AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "test", Set.of(), ""));
        var authField = AuthAccess.class.getDeclaredField("runtime");
        authField.setAccessible(true);
        Object previousAuth = authField.get(null);
        AuthAccess.install(() -> auth);
        long now = System.currentTimeMillis();
        var listing = new PlayerShopListing(9, UUID.randomUUID(), "Seller", "snapshot", "Item", "", "", "", 10, "coin",
            PlayerShopListing.ListingStatus.ACTIVE, now, now + 60_000);
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STONE);
        when(item.getAmount()).thenReturn(1);
        when(serializer.deserialize("snapshot")).thenReturn(item);
        when(repository.getPlayerShopListing(9)).thenReturn(listing);
        Thread main = Thread.currentThread();
        var reserved = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.reservePlayerShopPurchase(any())).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            reserved.countDown();
            return true;
        });
        doAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            assertTrue(((CurrencyTransactionResult) call.getArgument(1)).success());
            entered.countDown();
            await(release);
            return null;
        }).when(repository).finishPlayerShopPurchase(any(), any());
        var currencies = mock(CurrencyBridgeAPI.class);
        var bridge = mock(CurrencyBridgeAPI.CurrencyBridge.class);
        when(currencies.bridge("coin")).thenReturn(bridge);
        when(currencies.format(eq("coin"), any())).thenReturn("10");
        when(bridge.available()).thenReturn(true);
        var debitCalls = new AtomicInteger();
        when(bridge.prepareBackgroundDebit(eq(buyer), any())).thenReturn(() -> {
            assertNotSame(main, Thread.currentThread());
            assertEquals(0, reserved.getCount());
            debitCalls.incrementAndGet();
            return CurrencyTransactionResult.ok();
        });
        var messages = mock(MarketModuleConfiguration.MessagesConfiguration.class);
        when(messages.playerShopSold()).thenReturn("{item} {amount}");
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return null;
        });
        var server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, server);
        var debits = new MarketDebits(work, Logger.getAnonymousLogger());
        var service = new PlayerShopService(work, plugin, MarketModuleConfiguration.PlayerShopConfiguration.defaults(),
            messages, repository, null, null, currencies, serializer, Logger.getAnonymousLogger(), null, null, null, debits);
        service.setLedger(new MarketLedger(repository, Logger.getAnonymousLogger()));
        try {
            var result = service.purchase(buyer, 9, null, currencies, () -> Thread.currentThread() == main);
            Runnable prepare = callbacks.poll(3, TimeUnit.SECONDS);
            assertNotNull(prepare);
            prepare.run();
            assertTrue(reserved.await(3, TimeUnit.SECONDS));
            assertEquals(0, debitCalls.get());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (entered.getCount() != 0 && System.nanoTime() < deadline) {
                debits.drain();
                Thread.sleep(1);
            }
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            service.shutdown();
            assertFalse(result.isDone());
            release.countDown();
            assertTrue(result.get(3, TimeUnit.SECONDS).success());
            assertEquals(1, debitCalls.get());
            verify(bridge, never()).withdraw(any(), any());
        } finally {
            release.countDown();
            try { service.shutdown(); debits.close(); work.drain().toCompletableFuture().get(3, TimeUnit.SECONDS); }
            finally { field.set(null, previous); authField.set(null, previousAuth); }
        }
    }

    @Test
    void acceptedPlayerShopCancellationFinishesItsCommitAfterStop() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var work = new MarketWork(plugin);
        var repository = mock(MarketRepository.class);
        var serializer = mock(AuctionItemSerializer.class);
        UUID owner = UUID.randomUUID();
        long now = System.currentTimeMillis();
        var listing = new PlayerShopListing(9, owner, "Seller", "snapshot", "Item", "", "", "", 10, "coin",
            PlayerShopListing.ListingStatus.ACTIVE, now, now + 60_000);
        Thread main = Thread.currentThread();
        when(repository.getPlayerShopListing(9)).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            return listing;
        });
        when(serializer.deserialize("snapshot")).thenAnswer(call -> {
            assertSame(main, Thread.currentThread());
            return null;
        });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(repository.terminatePlayerShopListing(any())).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            var termination = (xuanmo.arcartxsuite.market.storage.PlayerShopTermination) call.getArgument(0);
            assertEquals(owner, termination.deliveries().get(0).player());
            entered.countDown();
            await(release);
            return true;
        });
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return null;
        });
        var server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, server);
        var service = new PlayerShopService(work, plugin, MarketModuleConfiguration.PlayerShopConfiguration.defaults(),
            null, repository, null, null, null, serializer, Logger.getAnonymousLogger(), null, null, null,
            new MarketDebits(work, Logger.getAnonymousLogger()));
        try {
            var result = service.cancelListing(owner, 9, false, () -> Thread.currentThread() == main);
            Runnable prepare = callbacks.poll(3, TimeUnit.SECONDS);
            assertNotNull(prepare);
            assertFalse(result.isDone());
            prepare.run();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            service.shutdown();
            assertFalse(result.isDone());
            release.countDown();
            assertTrue(result.get(3, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            try { service.shutdown(); work.drain().toCompletableFuture().get(3, TimeUnit.SECONDS); }
            finally { field.set(null, previous); }
        }
    }

    @Test
    void lateAuctionCacheWriteCannotRestoreAnInvalidatedPage() throws Exception {
        var cache = new RedisMarketCache(MarketModuleConfiguration.RedisConfiguration.disabled(), Logger.getAnonymousLogger());
        var jedis = mock(redis.clients.jedis.Jedis.class);
        var pool = mock(redis.clients.jedis.JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        var entries = new java.util.HashMap<String, String>();
        when(jedis.get(anyString())).thenAnswer(call -> entries.get(call.getArgument(0)));
        when(jedis.setex(anyString(), anyLong(), anyString())).thenAnswer(call -> {
            entries.put(call.getArgument(0), call.getArgument(2));
            return "OK";
        });
        set(cache, "pool", pool);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        String baseFirst = "market:listings:" + first + ":page";
        String baseSecond = "market:listings:" + second + ":page";
        String stale = cache.versionedListPageKey(first, baseFirst);
        String shared = cache.versionedListPageKey(second, baseSecond);
        cache.invalidateByPrefix("market:listings:" + first + ":");
        cache.cacheListings(stale, "old");
        String fresh = cache.versionedListPageKey(first, baseFirst);
        assertNotEquals(stale, fresh);
        assertNull(cache.getCached(fresh));
        assertEquals(shared, cache.versionedListPageKey(second, baseSecond));
        String shopBase = "market:pshop:" + first + ":page";
        String oldShop = cache.versionedListPageKey(first, shopBase);
        cache.invalidateByPrefix("market:pshop:");
        cache.cacheListings(oldShop, "old");
        assertNull(cache.getCached(cache.versionedListPageKey(first, shopBase)));
        assertEquals(fresh, cache.versionedListPageKey(first, baseFirst));
        cache.invalidateByPrefix("market:listings:");
        assertNotEquals(shared, cache.versionedListPageKey(second, baseSecond));
        verify(jedis, never()).scan(anyString(), any(redis.clients.jedis.params.ScanParams.class));
    }

    @Test
    void auctionBrowserReadsRedisAndTheDatabaseOffThreadAndDiscardsThePriorPage() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var config = mock(MarketModuleConfiguration.class, RETURNS_DEEP_STUBS);
        when(config.ui().auctionId()).thenReturn("listing");
        when(config.ui().packetId()).thenReturn("market");
        when(config.auction().categories()).thenReturn(java.util.Map.of());
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("arcartxsuite.market.use")).thenReturn(true);
        var packets = mock(PacketBridgeAPI.class);
        when(packets.isUiOpen(player, "listing")).thenReturn(true);
        var market = new MarketService(plugin, config, packets, null, null, null, null, null, null, null, null,
            Logger.getAnonymousLogger(), null, null, null, null, null);
        var repository = mock(MarketRepository.class);
        var auction = mock(AuctionService.class);
        var cache = mock(RedisMarketCache.class);
        when(cache.isAvailable()).thenReturn(true);
        Thread main = Thread.currentThread();
        when(cache.versionedListPageKey(eq(id), anyString())).thenAnswer(call -> call.getArgument(1) + ":epoch");
        when(cache.getCached(anyString())).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            return null;
        });
        when(auction.countActive()).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            return 200;
        });
        set(market, "repository", repository);
        set(market, "auctionService", auction);
        set(market, "redisCache", cache);
        var scheduler = mock(BukkitScheduler.class);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return null;
        });
        var server = mock(Server.class);
        when(server.getPlayer(id)).thenReturn(player);
        when(server.getScheduler()).thenReturn(scheduler);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        serverField.set(null, server);
        try (var auth = mockStatic(AuthAccess.class)) {
            var session = new AuthSnapshot(id, UUID.randomUUID(), 1, id, AuthSnapshot.Registration.REGISTERED,
                AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(session);
            market.handleClientPacket(player, "market", List.of("auction_list", "0", "all", ""));
            market.handleClientPacket(player, "market", List.of("auction_list", "2", "all", ""));
            for (int i = 0; i < 2; i++) {
                Runnable callback = callbacks.poll(3, TimeUnit.SECONDS);
                assertNotNull(callback);
                callback.run();
            }
            verify(packets, times(1)).sendPacket(eq(player), eq("listing"), eq("update"), any());
            verify(packets).sendPacket(eq(player), eq("listing"), eq("update"),
                argThat(payload -> payload instanceof java.util.Map<?, ?> map && Integer.valueOf(2).equals(map.get("page"))));
            verify(auction, times(2)).countActive();
        } finally {
            market.shutdown();
            try { market.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS); }
            finally { serverField.set(null, previous); }
        }
    }

    @Test
    void playerShopPagesClampThePageBeforeReadingAndBothPanelsCanLoadTogether() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var config = mock(MarketModuleConfiguration.class, RETURNS_DEEP_STUBS);
        when(config.ui().playerShopId()).thenReturn("shops");
        when(config.ui().packetId()).thenReturn("market");
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("arcartxsuite.market.use")).thenReturn(true);
        var packets = mock(PacketBridgeAPI.class);
        when(packets.isUiOpen(player, "shops")).thenReturn(true);
        var market = new MarketService(plugin, config, packets, null, null, null, null, null, null, null, null,
            Logger.getAnonymousLogger(), null, null, null, null, null);
        var shops = mock(PlayerShopService.class);
        Thread main = Thread.currentThread();
        when(shops.countActive()).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            return 21;
        });
        when(shops.countSellers()).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            return 21;
        });
        when(shops.getActiveListings(1, 20)).thenReturn(List.of());
        when(shops.getSellers(1, 20)).thenReturn(List.of());
        set(market, "playerShopService", shops);
        set(market, "repository", mock(MarketRepository.class));
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return null;
        });
        var server = mock(Server.class);
        when(server.getPlayer(id)).thenReturn(player);
        when(server.getScheduler()).thenReturn(scheduler);
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, server);
        try (var auth = mockStatic(AuthAccess.class)) {
            var session = new AuthSnapshot(id, UUID.randomUUID(), 1, id, AuthSnapshot.Registration.REGISTERED,
                AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(session);
            market.handleClientPacket(player, "market", List.of("pshop_list", "900", "", ""));
            market.handleClientPacket(player, "market", List.of("pshop_sellers", "800"));
            for (int i = 0; i < 2; i++) {
                Runnable callback = callbacks.poll(3, TimeUnit.SECONDS);
                assertNotNull(callback);
                callback.run();
            }
            verify(shops, times(1)).getActiveListings(anyInt(), anyInt());
            verify(shops).getActiveListings(1, 20);
            verify(shops).getSellers(1, 20);
            verify(packets).sendPacket(eq(player), eq("shops"), eq("pshop_list"), any());
            verify(packets).sendPacket(eq(player), eq("shops"), eq("pshop_sellers"), any());
        } finally {
            market.shutdown();
            try { market.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS); }
            finally { field.set(null, previous); }
        }
    }

    @Test
    void auctionQuoteReadsOffThreadAndOnlyRepliesToTheLatestInput() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var packets = mock(PacketBridgeAPI.class);
        var config = mock(MarketModuleConfiguration.class, RETURNS_DEEP_STUBS);
        when(config.ui().auctionId()).thenReturn("listing");
        when(config.ui().auctionRoomId()).thenReturn("room");
        when(config.ui().packetId()).thenReturn("market");
        when(config.auction().maxPrice()).thenReturn(1_000d);
        var bidder = mock(Player.class);
        UUID bidderId = UUID.randomUUID();
        when(bidder.getUniqueId()).thenReturn(bidderId);
        when(bidder.isOnline()).thenReturn(true);
        when(bidder.hasPermission("arcartxsuite.market.use")).thenReturn(true);
        when(packets.isUiOpen(bidder, "listing")).thenReturn(true);
        var currencies = mock(CurrencyBridgeAPI.class);
        var bridge = mock(CurrencyBridgeAPI.CurrencyBridge.class);
        when(currencies.bridge("coin")).thenReturn(bridge);
        when(bridge.available()).thenReturn(true);
        Thread main = Thread.currentThread();
        when(bridge.prepareBackgroundBalance(bidder)).thenReturn(() -> {
            assertNotSame(main, Thread.currentThread());
            return BigDecimal.valueOf(100);
        });
        when(currencies.format(eq("coin"), any())).thenAnswer(call -> ((BigDecimal) call.getArgument(1)).toPlainString());
        var market = new MarketService(plugin, config, packets, currencies,
            null, null, null, null, null, null, null, Logger.getAnonymousLogger(), null, null, null, null, null);
        var auctions = mock(AuctionService.class);
        when(auctions.currencyScale("coin")).thenReturn(2);
        when(auctions.minimumBid(10, 0, false, 2)).thenReturn(BigDecimal.TEN);
        var repository = mock(MarketRepository.class);
        var terms = new AuctionBidTerms(42L, UUID.randomUUID(), 10, 0, null, null, "coin",
            AuctionListing.ListingType.AUCTION, AuctionListing.ListingStatus.ACTIVE, System.currentTimeMillis() + 60_000);
        when(repository.readAuctionBidTerms(42)).thenAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            return terms;
        });
        set(market, "repository", repository);
        set(market, "auctionService", auctions);

        var scheduler = mock(BukkitScheduler.class);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        var server = mock(Server.class);
        when(server.getPlayer(bidderId)).thenReturn(bidder);
        when(server.getScheduler()).thenReturn(scheduler);
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        serverField.set(null, server);
        try (var auth = mockStatic(AuthAccess.class)) {
            var session = new AuthSnapshot(bidderId, UUID.randomUUID(), 1, bidderId,
                AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
            auth.when(() -> AuthAccess.snapshot(bidderId)).thenReturn(session);
            market.openAuctionUi(bidder);
            market.handleClientPacket(bidder, "market", List.of("auction_quote", "42", "0"));
            market.handleClientPacket(bidder, "market", List.of("auction_quote", "42", "15"));
            market.handleClientPacket(bidder, "market", List.of("auction_quote", "42", "30"));
            market.handleClientPacket(bidder, "market", List.of("auction_quote", "42", "30"));
            for (int stage = 0; stage < 3; stage++) {
                Runnable callback = callbacks.poll(3, TimeUnit.SECONDS);
                assertNotNull(callback);
                callback.run();
            }
            verify(repository, times(2)).readAuctionBidTerms(42);
            verify(repository, never()).getListing(42);
            verify(bridge, never()).balance(bidder);
            verify(packets).sendPacket(eq(bidder), eq("listing"), eq("auction_quote"),
                argThat(payload -> payload instanceof java.util.Map<?, ?> map && "30.0".equals(map.get("amount"))
                    && Boolean.TRUE.equals(map.get("canSubmit"))));
        } finally {
            market.shutdown();
            try { market.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS); }
            finally { serverField.set(null, previous); }
        }
    }

    @Test
    void openingASharedAuctionUsesThePreparedRoomOnce() throws Exception {
        var plugin = mock(JavaPlugin.class);
        var work = new MarketWork(plugin);
        var repository = mock(MarketRepository.class);
        var packets = mock(PacketBridgeAPI.class);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(packets.isUiOpen(player, "room")).thenReturn(true);
        var listing = mock(AuctionListing.class);
        when(listing.isActive()).thenReturn(true);
        var snapshot = new AuctionRoomSnapshot(listing, java.util.List.of(), 0, 0, 0, false, 0, 0, "", 0, 0, 0);
        var render = mock(AuctionRoomService.Renderer.class);
        try (var bukkit = mockStatic(Bukkit.class);
             var room = new AuctionRoomService(work, repository, packets, "room", mock(CurrencyBridgeAPI.class), render,
                (viewer, failure) -> fail(failure), Logger.getAnonymousLogger())) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            room.select(player, 42, snapshot, System.nanoTime());
            room.requestInitial(player);
            verify(render).render(player, snapshot, null);
            verifyNoInteractions(repository);
        } finally {
            work.drain().toCompletableFuture().get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void roomBalanceRunsOffThreadAndCannotRenderIntoAnotherRoom() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var work = new MarketWork(plugin);
        var repository = mock(MarketRepository.class);
        var packets = mock(PacketBridgeAPI.class);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(packets.isUiOpen(player, "room")).thenReturn(true);
        var listing = mock(AuctionListing.class);
        when(listing.isActive()).thenReturn(true);
        when(listing.getCurrency()).thenReturn("coin");
        var snapshot = new AuctionRoomSnapshot(listing, List.of(), 0, 0, 0, false, 0, 0, "", 0, 0, 0);
        var currencies = mock(CurrencyBridgeAPI.class);
        var bridge = mock(CurrencyBridgeAPI.CurrencyBridge.class);
        when(currencies.bridge("coin")).thenReturn(bridge);
        when(bridge.available()).thenReturn(true);
        Thread main = Thread.currentThread();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(bridge.prepareBackgroundBalance(player)).thenReturn(() -> {
            assertNotSame(main, Thread.currentThread());
            entered.countDown();
            await(release);
            return BigDecimal.TEN;
        });
        var scheduler = mock(BukkitScheduler.class);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return null;
        });
        var server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPlayer(id)).thenReturn(player);
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, server);
        var render = mock(AuctionRoomService.Renderer.class);
        try (var room = new AuctionRoomService(work, repository, packets, "room", currencies, render,
                (viewer, failure) -> fail(failure), Logger.getAnonymousLogger())) {
            room.select(player, 42, snapshot, System.nanoTime());
            room.requestInitial(player);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            room.select(player, 99, null, 0);
            assertFalse(room.request(player));
            release.countDown();
            Runnable callback = callbacks.poll(3, TimeUnit.SECONDS);
            assertNotNull(callback);
            callback.run();
            verifyNoInteractions(render, repository);
            verify(bridge, never()).balance(player);
        } finally {
            release.countDown();
            try { work.drain().toCompletableFuture().get(3, TimeUnit.SECONDS); }
            finally { field.set(null, previous); }
        }
    }

    @Test
    void closingReturnsAClaimedButUnstartedDeliveryToReadyOffThread() throws Exception {
        var plugin = mock(JavaPlugin.class);
        var work = new MarketWork(plugin);
        var ledger = mock(MarketLedger.class);
        var delivery = mock(MarketOperation.class);
        when(ledger.claimDelivery(delivery)).thenReturn(true);
        var service = new PendingDeliveryService(work, plugin, mock(MarketRepository.class), null, null,
            Logger.getAnonymousLogger(), ledger);
        var effects = new AtomicInteger();
        var claim = PendingDeliveryService.class.getDeclaredMethod("claim", PendingDeliveryService.DeliverySession.class,
            java.util.Iterator.class, MarketOperation.class, java.util.function.Supplier.class,
            boolean.class, boolean.class);
        claim.setAccessible(true);
        var receiptThread = new AtomicReference<String>();
        doAnswer(call -> { receiptThread.set(Thread.currentThread().getName()); return null; })
            .when(ledger).finishDelivery(eq(delivery), eq("READY"), anyString());
        var queueField = PendingDeliveryService.class.getDeclaredField("ready");
        queueField.setAccessible(true);
        var queue = (java.util.Queue<?>) queueField.get(service);
        try {
            var session = new PendingDeliveryService.DeliverySession(mock(org.bukkit.entity.Player.class),
                xuanmo.arcartxsuite.api.auth.AuthSnapshot.absent(UUID.randomUUID()));
            claim.invoke(service, session, java.util.List.of().iterator(),
                delivery, (java.util.function.Supplier<Boolean>) () -> { effects.incrementAndGet(); return true; }, false, false);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (queue.isEmpty() && System.nanoTime() < deadline) Thread.sleep(2);
            assertFalse(queue.isEmpty());
            service.close();
        } finally { service.close(); }
        work.drain().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(0, effects.get());
        assertEquals("AXS-Market-IO", receiptThread.get());
        verify(ledger, times(1)).finishDelivery(eq(delivery), eq("READY"), anyString());
        verify(ledger, never()).finishDelivery(eq(delivery), eq("DONE"), anyString());
    }

    @Test
    void aRunningTimerCanFinishItsReceiptBeforeTheExecutorCloses() throws Exception {
        var work = new MarketWork(mock(JavaPlugin.class));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var receipts = new AtomicInteger();
        Runnable timer = work.callback(() -> {
            entered.countDown();
            await(release);
            work.async(receipts::incrementAndGet);
        });
        var running = CompletableFuture.runAsync(timer);
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var drained = work.drain().toCompletableFuture();
            assertFalse(drained.isDone());
            timer.run(); // Closing rejects a second invocation without waiting for the active one.
        } finally { release.countDown(); }
        running.get(3, TimeUnit.SECONDS);
        work.drain().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(1, receipts.get());
    }

    @Test
    void timersAreCancelledAndQueuedUiCallbacksDoNotReachTheReplacement() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var scheduler = mock(BukkitScheduler.class);
        var task = mock(BukkitTask.class);
        var queued = new AtomicReference<Runnable>();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
        doAnswer(call -> { queued.set(call.getArgument(1)); return mock(BukkitTask.class); })
            .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var old = new MarketWork(plugin);
            var shown = new AtomicInteger();
            old.repeat(shown::incrementAndGet, 1, 20);
            old.main(shown::incrementAndGet);
            old.drain().toCompletableFuture().get(2, TimeUnit.SECONDS);
            verify(task).cancel();
            queued.get().run();
            assertEquals(0, shown.get());
            var replacement = new MarketWork(plugin);
            replacement.main(shown::incrementAndGet);
            queued.get().run();
            replacement.drain().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, shown.get());
        }
    }

    @Test
    void databaseAndDynamicStateCloseOffThreadAfterAcceptedWork() throws Exception {
        var service = new MarketService(mock(JavaPlugin.class), mock(MarketModuleConfiguration.class), null,
            null, null, null, null, null, null, null, null, Logger.getAnonymousLogger(), null, null, null, null, null);
        var repository = mock(MarketRepository.class);
        var dynamic = mock(DynamicShopService.class);
        set(service, "repository", repository);
        set(service, "dynamicShopService", dynamic);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closing = new CountDownLatch(1);
        var closeRelease = new CountDownLatch(1);
        service.work().async(() -> { entered.countDown(); await(release); });
        doAnswer(call -> {
            assertEquals("AXS-Market-Close", Thread.currentThread().getName());
            closing.countDown(); await(closeRelease); return null;
        }).when(repository).shutdown();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            long start = System.nanoTime();
            service.shutdown();
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500));
            verify(repository, never()).shutdown();
            verify(dynamic, never()).finishShutdown();
            release.countDown();
            assertTrue(closing.await(2, TimeUnit.SECONDS));
            assertFalse(service.databaseStopped().toCompletableFuture().isDone());
        } finally { release.countDown(); closeRelease.countDown(); service.shutdown(); }
        service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
        verify(repository, times(1)).shutdown();
        verify(dynamic).finishShutdown();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { throw new AssertionError(error); }
    }
}
