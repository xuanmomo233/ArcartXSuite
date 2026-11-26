package xuanmo.arcartxsuite.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.*;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.currency.CurrencyTransactionResult;
import xuanmo.arcartxsuite.chat.config.ChatChannelDefinition;
import xuanmo.arcartxsuite.chat.config.ChatChannelMode;
import xuanmo.arcartxsuite.chat.config.ChatModuleConfiguration;
import xuanmo.arcartxsuite.chat.model.ChatModerationReceipt;
import xuanmo.arcartxsuite.chat.model.ChatMuteRecord;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.model.RedPacket;
import xuanmo.arcartxsuite.chat.model.RedPacketType;
import xuanmo.arcartxsuite.chat.storage.ChatRepository;

class ChatModerationAsyncTest {
    @Test void adminCardWaitsForCommitAndRetriesOnceAfterTheRouteQueueFills() throws Exception {
        try (var test = new Harness()) {
            String channelId = "global";
            var channel = new ChatChannelDefinition(channelId, true, channelId,
                ChatChannelMode.GLOBAL, "", "", true, 0, "", "", "", "", "");
            var chat = mock(ChatService.class);
            when(chat.nodeId()).thenReturn("local");
            when(chat.channel(channelId)).thenReturn(channel);
            when(chat.moderateRedPacketWish("")).thenReturn("");
            when(test.config.channel(channelId)).thenReturn(channel);
            when(test.config.redPacket().enabled()).thenReturn(true);
            when(test.config.redPacket().adminChannel()).thenReturn(channelId);
            when(test.config.redPacket().adminPermission()).thenReturn("");
            when(test.config.redPacket().maxCount()).thenReturn(20);
            when(test.config.redPacket().maxTotal()).thenReturn(100d);
            when(test.config.redPacket().expireMinutes()).thenReturn(5L);
            when(test.config.redPacket().currency()).thenReturn("test");
            when(test.config.redPacket().cardId()).thenReturn("card");
            var console = mock(ConsoleCommandSender.class);
            when(console.getName()).thenReturn("CONSOLE");
            var currencies = mock(CurrencyBridgeAPI.class);
            var bridge = mock(CurrencyBridgeAPI.CurrencyBridge.class);
            when(currencies.bridge("test")).thenReturn(bridge);
            when(currencies.format(eq("test"), any())).thenReturn("1");
            when(bridge.available()).thenReturn(true);
            var packets = mock(PacketBridgeAPI.class);
            when(packets.sendChatCard(eq(test.target), eq("card"), anyMap())).thenReturn(true);
            var created = new CompletableFuture<RedPacket>();
            Thread main = Thread.currentThread();
            doAnswer(call -> {
                assertNotSame(main, Thread.currentThread());
                created.complete(call.getArgument(0));
                return null;
            }).when(test.repository).insertRedPacket(any(), anyList());
            when(chat.routeRedPacket(eq(channel), isNull(), isNull(), isNull(), any()))
                .thenReturn(false).thenAnswer(call -> {
                    java.util.function.Consumer<List<Player>> deliver = call.getArgument(4);
                    deliver.accept(List.of(test.target));
                    return true;
                });
            var service = new RedPacketService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), test.config,
                test.repository, chat, packets, currencies, null, null, null, null, null, null, false);
            try {
                var reply = new CompletableFuture<ChatOperationResult>();
                service.sendAdminPacket(console, RedPacketType.NORMAL, BigDecimal.ONE, 1, "", reply::complete);
                RedPacket packet = created.get(3, TimeUnit.SECONDS);
                assertFalse(reply.isDone());
                test.runMainTask();
                assertTrue(reply.get(3, TimeUnit.SECONDS).success());
                verifyNoInteractions(packets);
                service.broadcastLocal(packet);
                test.runMainTask();
                verify(chat, times(2)).routeRedPacket(eq(channel), isNull(), isNull(), isNull(), any());
                verify(packets).sendChatCard(eq(test.target), eq("card"), anyMap());
            } finally {
                service.shutdown();
                service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            }
        }
    }

    @Test void redPacketDebitRunsOnlyAfterIntentAndRepliesAfterReceipt() throws Exception {
        verifyRedPacketDebit(false);
    }

    @Test void stoppingBeforeDebitCancelsThePersistedIntent() throws Exception {
        verifyRedPacketDebit(true);
    }

    private void verifyRedPacketDebit(boolean stopBeforeDebit) throws Exception {
        try (var test = new Harness(); var auth = mockStatic(AuthAccess.class)) {
            UUID player = test.target.getUniqueId();
            var session = new AuthSnapshot(player, UUID.randomUUID(), 1, player, AuthSnapshot.Registration.REGISTERED,
                AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
            auth.when(() -> AuthAccess.snapshot(player)).thenReturn(session);
            auth.when(() -> AuthAccess.admitted(player)).thenReturn(true);
            String channelId = "global";
            var channel = new ChatChannelDefinition(channelId, true, channelId,
                ChatChannelMode.GLOBAL, "", "", true, 0, "", "", "", "", "");
            when(test.config.channel(channelId)).thenReturn(channel);
            when(test.config.defaultChannelId()).thenReturn(channelId);
            when(test.config.maxLength()).thenReturn(128);
            when(test.config.redPacket().enabled()).thenReturn(true);
            when(test.config.redPacket().sendPermission()).thenReturn("");
            when(test.config.redPacket().maxCount()).thenReturn(20);
            when(test.config.redPacket().maxTotal()).thenReturn(100d);
            when(test.config.redPacket().expireMinutes()).thenReturn(5L);
            when(test.config.redPacket().currency()).thenReturn("test");
            when(test.repository.loadState(player, channelId)).thenReturn(ChatPlayerState.createDefault(player, channelId));
            when(test.target.getDisplayName()).thenReturn("Target");
            doReturn(List.of()).when(test.server).getOnlinePlayers();
            var currencies = mock(CurrencyBridgeAPI.class);
            var bridge = mock(CurrencyBridgeAPI.CurrencyBridge.class);
            when(currencies.bridge("test")).thenReturn(bridge);
            when(currencies.format(eq("test"), any())).thenReturn("10");
            when(bridge.available()).thenReturn(true);
            when(bridge.balance(test.target)).thenReturn(BigDecimal.TEN);
            Thread main = Thread.currentThread();
            var intent = new java.util.concurrent.atomic.AtomicReference<RedPacket>();
            doAnswer(call -> {
                assertNotSame(main, Thread.currentThread());
                intent.set(call.getArgument(0));
                return null;
            }).when(test.repository).insertRedPacket(any(), anyList());
            when(bridge.withdraw(test.target, BigDecimal.ONE)).thenAnswer(call -> {
                assertSame(main, Thread.currentThread());
                assertEquals(RedPacket.STATUS_DEBITING, intent.get().status());
                return CurrencyTransactionResult.ok();
            });
            when(test.repository.completeDebit(anyString(), anyBoolean())).thenAnswer(call -> {
                assertNotSame(main, Thread.currentThread());
                return true;
            });
            var service = new RedPacketService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), test.config,
                test.repository, test.service, null, currencies, null, null, null, null, null, null, false);
            try {
                var result = new CompletableFuture<ChatOperationResult>();
                service.sendPlayerPacket(test.target, RedPacketType.NORMAL, BigDecimal.ONE, 1, "", result::complete);
                test.runMainTask();
                var effect = test.mainTasks.poll(3, TimeUnit.SECONDS);
                assertNotNull(effect);
                assertFalse(result.isDone());
                verify(bridge, never()).withdraw(any(), any());
                if (stopBeforeDebit) {
                    service.shutdown();
                    effect.run();
                    service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
                    verify(bridge, never()).withdraw(any(), any());
                    verify(test.repository).completeDebit(intent.get().id(), false);
                } else {
                    effect.run();
                    assertFalse(result.isDone());
                    test.runMainTask();
                    assertTrue(result.get(3, TimeUnit.SECONDS).success());
                    verify(test.repository).completeDebit(intent.get().id(), true);
                }
            } finally {
                service.shutdown();
                service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            }
        }
    }

    @Test void sharingLinksBatchesRoutesOffThreadAndRechecksLivePermissions() throws Exception {
        try (var test = new Harness(); var auth = mockStatic(AuthAccess.class)) {
            UUID sender = test.target.getUniqueId();
            Player recipient = mock(Player.class);
            UUID recipientId = UUID.randomUUID();
            when(recipient.getUniqueId()).thenReturn(recipientId);
            when(recipient.isOnline()).thenReturn(true);
            when(recipient.hasPermission("chat.receive")).thenReturn(true);
            when(test.server.getPlayer(recipientId)).thenReturn(recipient);
            doReturn(List.of(test.target, recipient)).when(test.server).getOnlinePlayers();
            for (UUID id : List.of(sender, recipientId)) {
                var snapshot = new AuthSnapshot(id, UUID.randomUUID(), 1, id, AuthSnapshot.Registration.REGISTERED,
                    AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
                auth.when(() -> AuthAccess.snapshot(id)).thenReturn(snapshot);
                auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            }
            String channelId = "global";
            var channel = new ChatChannelDefinition(channelId, true, channelId,
                ChatChannelMode.GLOBAL, "", "chat.receive", true, 0, "", "", "", "", "");
            when(test.config.defaultChannelId()).thenReturn(channelId);
            when(test.config.channel(channelId)).thenReturn(channel);
            when(test.repository.loadState(sender, channelId)).thenReturn(ChatPlayerState.createDefault(sender, channelId));
            var routes = Map.of(
                sender, new ChatRepository.RouteState(ChatPlayerState.createDefault(sender, channelId), true, false),
                recipientId, new ChatRepository.RouteState(ChatPlayerState.createDefault(recipientId, channelId), true, false));
            Thread main = Thread.currentThread();
            when(test.repository.loadRouteStates(Set.of(sender, recipientId), channelId, channelId)).thenAnswer(call -> {
                assertNotSame(main, Thread.currentThread());
                return routes;
            });
            var sent = new CompletableFuture<ChatOperationResult>();
            test.service.sendLink(test.target, null, "link", (selected, viewers) -> {
                assertSame(main, Thread.currentThread());
                assertSame(channel, selected);
                assertEquals(List.of(test.target), viewers);
                return ChatOperationResult.success("sent");
            }, sent::complete);
            assertFalse(sent.isDone());
            when(recipient.hasPermission("chat.receive")).thenReturn(false);
            test.runMainTask();
            assertTrue(sent.get(3, TimeUnit.SECONDS).success());
            verify(test.repository, times(1)).loadRouteStates(anySet(), anyString(), anyString());
        }
    }

    @Test void aStatusReadWaitsOffThreadAndSurfacesStorageFailure() throws Exception {
        try (var test = new Harness()) {
            UUID id = test.target.getUniqueId();
            Thread caller = Thread.currentThread();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            when(test.repository.loadMute(id)).thenAnswer(call -> {
                assertNotSame(caller, Thread.currentThread());
                entered.countDown();
                assertTrue(release.await(3, TimeUnit.SECONDS));
                throw new SQLException("offline");
            });
            var result = test.service.queryMute(id).toCompletableFuture();
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertFalse(result.isDone());
                release.countDown();
                assertInstanceOf(SQLException.class, assertThrows(ExecutionException.class,
                    () -> result.get(3, TimeUnit.SECONDS)).getCause());
            } finally {
                release.countDown();
            }
        }
    }

    @Test void aRemoteMuteRetriesItsReadBeforeAcknowledgingAndDeduplicatesTheReceipt() throws Exception {
        try (var test = new Harness()) {
            Thread caller = Thread.currentThread();
            UUID playerId = test.target.getUniqueId();
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            var mute = new ChatMuteRecord(playerId, "Admin", "reason", now, null);
            var receipt = new ChatModerationReceipt(id, playerId, "Target", "MUTE", "Admin", "reason", "remote", now, null);
            var reads = new AtomicInteger();
            when(test.repository.findModerationReceipt(id)).thenAnswer(call -> {
                assertNotSame(caller, Thread.currentThread());
                if (reads.incrementAndGet() == 1) throw new SQLException("transient failure");
                return Optional.of(receipt);
            });
            when(test.repository.loadMute(playerId)).thenAnswer(call -> {
                assertNotSame(caller, Thread.currentThread());
                return Optional.of(mute);
            });
            var first = test.remoteModeration(id);
            assertSame(first, test.remoteModeration(id));
            test.runMainTask();
            assertFalse(first.isDone());
            test.runMainTask();
            test.runMainTask();
            assertTrue(first.get(3, TimeUnit.SECONDS));
            assertEquals(mute, test.service.getCachedMute(playerId));
            assertTrue(test.remoteModeration(id).isDone());
            verify(test.repository, times(2)).findModerationReceipt(id);
        }
    }

    @Test void databaseWriteDoesNotRunOnTheCallerAndSuccessWaitsForItsCommit() throws Exception {
        try (var test = new Harness()) {
            Thread caller = Thread.currentThread();
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            doAnswer(call -> { assertNotSame(caller, Thread.currentThread()); entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return null; })
                .when(test.repository).saveMute(any(), any());
            var result = test.service.mutePlayer("Target", null, "reason", "Admin").toCompletableFuture();
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS)); assertFalse(result.isDone());
                release.countDown(); assertTrue(result.get(3, TimeUnit.SECONDS).success());
            } finally { release.countDown(); }
            verify(test.repository, times(1)).saveMute(any(), any());
        }
    }

    @Test void anAcceptedWriteDrainsAfterStopAndItsQueuedNotificationCannotRun() throws Exception {
        try (var test = new Harness()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            doAnswer(call -> { entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return null; })
                .when(test.repository).saveMute(any(), any());
            var result = test.service.mutePlayer("Target", null, "reason", "Admin").toCompletableFuture();
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS)); test.service.shutdown();
                assertFalse(test.service.databaseStopped().toCompletableFuture().isDone());
                assertTrue(result.isCompletedExceptionally());
                release.countDown(); test.service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
                test.mainTasks.forEach(Runnable::run);
                verify(test.target, never()).sendMessage(anyString());
                verify(test.repository, times(1)).saveMute(any(), any());
                assertTrue(test.service.queryMute(UUID.randomUUID()).toCompletableFuture().isCompletedExceptionally());
                verify(test.repository, never()).loadMute(any());
            } finally { release.countDown(); }
        }
    }

    private static final class Harness implements AutoCloseable {
        final Field serverField; final Object previous;
        final ChatRepository repository = mock(ChatRepository.class);
        final ChatModuleConfiguration config = mock(ChatModuleConfiguration.class, RETURNS_DEEP_STUBS);
        final Server server = mock(Server.class);
        final Player target = mock(Player.class);
        final BlockingQueue<Runnable> mainTasks = new LinkedBlockingQueue<>();
        final ChatService service;
        Harness() throws Exception {
            Thread owner = Thread.currentThread();
            var scheduler = mock(BukkitScheduler.class);
            when(server.isPrimaryThread()).thenAnswer(call -> Thread.currentThread() == owner);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(call -> { mainTasks.add(call.getArgument(1)); return null; });
            when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong())).thenAnswer(call -> {
                mainTasks.add(call.getArgument(1));
                return null;
            });
            when(target.getUniqueId()).thenReturn(UUID.randomUUID()); when(target.getName()).thenReturn("Target");
            when(target.isOnline()).thenReturn(true);
            when(server.getPlayerExact("Target")).thenReturn(target);
            when(server.getPlayer(target.getUniqueId())).thenReturn(target);
            serverField = Bukkit.class.getDeclaredField("server"); serverField.setAccessible(true);
            previous = serverField.get(null); serverField.set(null, server);
            var crossServer = mock(CrossServerAPI.class); when(crossServer.nodeId()).thenReturn("node");
            when(config.crossServer()).thenReturn(new xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig(true, null, null));
            service = new ChatService(mock(JavaPlugin.class), Logger.getAnonymousLogger(), () -> null,
                config, repository, mock(PacketBridgeAPI.class), mock(ItemBridgeAPI.class),
                "chat", crossServer, null, null, null, null, () -> true);
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<Boolean> remoteModeration(UUID id) throws Exception {
            var admit = ChatService.class.getDeclaredMethod("handleRelayPayload", String.class, String.class, long.class);
            admit.setAccessible(true);
            return ((CompletionStage<Boolean>) admit.invoke(service, "axs_mod|" + id, "remote", 0L)).toCompletableFuture();
        }
        void runMainTask() throws InterruptedException {
            Runnable task = mainTasks.poll(3, TimeUnit.SECONDS);
            assertNotNull(task);
            task.run();
        }
        @Override public void close() throws Exception {
            try { service.shutdown(); service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS); }
            finally { serverField.set(null, previous); }
        }
    }
}
