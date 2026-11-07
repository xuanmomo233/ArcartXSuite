package xuanmo.arcartxsuite.qqbot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotDeliveryConfig;
import xuanmo.arcartxsuite.qqbot.config.QQBotGroupConfig;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotClient;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotResult;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;

class QQBotMessageGatewayTest {
    private static final long BOTH_GROUP = 10001;
    private static final long OUTBOUND_GROUP = 10002;
    private static final long INBOUND_GROUP = 10003;
    private final Map<UUID, AuthSnapshot> sessions = new ConcurrentHashMap<>();
    private final ArrayDeque<Runnable> mainQueue = new ArrayDeque<>();
    private MockedStatic<Bukkit> bukkit;
    private JavaPlugin plugin;
    private QQBotConfiguration configuration;
    private QQBotRepository repository;
    private OneBotClient client;
    private BukkitScheduler scheduler;
    private QQBotMessageGateway gateway;
    private Player player;

    @BeforeEach void initialize() {
        plugin = mock(JavaPlugin.class);
        configuration = mock(QQBotConfiguration.class);
        repository = mock(QQBotRepository.class);
        client = mock(OneBotClient.class);
        scheduler = mock(BukkitScheduler.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            mainQueue.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(configuration.delivery()).thenReturn(new QQBotDeliveryConfig(32, 60000, 30, false, false));
        when(configuration.serverId()).thenReturn("survival");
        when(configuration.groups()).thenReturn(List.of(group(BOTH_GROUP, "both"),
            group(OUTBOUND_GROUP, "game-to-qq"), group(INBOUND_GROUP, "qq-to-game")));
        when(client.isConnected()).thenReturn(true);
        when(client.sendGroupMessage(anyLong(), anyString())).thenAnswer(call -> acknowledged());
        when(client.sendGroupMessageAt(anyLong(), anyLong(), anyString())).thenAnswer(call -> acknowledged());
        AuthRuntimeCapability auth = mock(AuthRuntimeCapability.class);
        when(auth.snapshot(any(UUID.class))).thenAnswer(call -> sessions.getOrDefault(call.getArgument(0), AuthSnapshot.absent(call.getArgument(0))));
        // Unlike a Mockito static mock, this provider is also visible to the real delivery worker.
        AuthAccess.install(() -> auth);
        player = player("Alice");
        gateway = new QQBotMessageGateway(plugin, configuration, repository, client);
    }

    @AfterEach void shutdown() {
        if (gateway != null) gateway.close();
        AuthAccess.install(() -> null);
        if (bukkit != null) bukkit.close();
    }

    @Test void sendPermissionIsCheckedBeforeAnyDeliveryOrBindingLookup() throws Exception {
        when(player.hasPermission("arcartxsuite.qqbot.send")).thenReturn(false);
        var result = await(gateway.sendPlayer(player, "hello", null));
        assertFalse(result.success());
        assertTrue(result.message().contains("权限"));
        verifyNoInteractions(repository);
        verify(client, never()).sendGroupMessage(anyLong(), anyString());
    }

    @Test void admittedAndOnlineSessionIsRequired() throws Exception {
        sessions.put(player.getUniqueId(), AuthSnapshot.absent(player.getUniqueId()));
        assertFalse(await(gateway.sendPlayer(player, "hello", null)).success());
        sessions.put(player.getUniqueId(), admitted(player.getUniqueId(), UUID.randomUUID(), 2));
        when(player.isOnline()).thenReturn(false);
        assertFalse(await(gateway.sendPlayer(player, "hello", null)).success());
        verify(client, never()).sendGroupMessage(anyLong(), anyString());
    }

    @Test void playerMessageUsesOnlyOutboundEnabledGroupsAndNormalizedDisplayText() throws Exception {
        assertTrue(await(gateway.sendPlayer(player, "  &ahello  ", null)).success());
        verify(client).sendGroupMessage(BOTH_GROUP, "[survival] Alice/Alice: hello");
        verify(client).sendGroupMessage(OUTBOUND_GROUP, "[survival] Alice/Alice: hello");
        verify(client, never()).sendGroupMessage(eq(INBOUND_GROUP), anyString());
    }

    @Test void noOutboundAudienceFailsWithoutSendingIntoAnInboundOnlyGroup() throws Exception {
        when(configuration.groups()).thenReturn(List.of(group(INBOUND_GROUP, "qq-to-game")));
        var result = await(gateway.sendPlayer(player, "hello", null));
        assertFalse(result.success());
        assertTrue(result.message().contains("没有允许接收"));
        verify(client, never()).sendGroupMessage(anyLong(), anyString());
    }

    @Test void bindingRequirementFailsClosedForMissingOrFailedAuthorityLookup() throws Exception {
        requireBinding();
        assertTrue(await(gateway.sendPlayer(player, "hello", null)).message().contains("绑定"));
        gateway.clearPlayer(player.getUniqueId());
        when(repository.findByPlayer(player.getUniqueId())).thenThrow(new QQBotRepository.StorageFailure("offline", new IllegalStateException()));
        assertFalse(await(gateway.sendPlayer(player, "retry", null)).success());
        verify(client, never()).sendGroupMessage(anyLong(), anyString());
    }

    @Test void boundPlayerCanSendAndAcknowledgementIsRequiredForSuccess() throws Exception {
        requireBinding();
        var bound = binding(player);
        when(repository.findByPlayer(player.getUniqueId())).thenReturn(bound);
        CompletableFuture<OneBotResult> ack = new CompletableFuture<>();
        CountDownLatch delivered = new CountDownLatch(2);
        when(client.sendGroupMessage(anyLong(), anyString())).thenAnswer(call -> { delivered.countDown(); return ack; });
        var result = gateway.sendPlayer(player, "hello", null);
        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        assertFalse(result.isDone(), "writing a request is not an acknowledged delivery");
        ack.complete(OneBotResult.success(null));
        assertTrue(await(result).success());
    }

    @Test void successfulSendConsumesPlayerCooldownAcrossTheGateway() throws Exception {
        assertTrue(await(gateway.sendPlayer(player, "first", null)).success());
        var duplicate = await(gateway.sendPlayer(player, "second", null));
        assertFalse(duplicate.success());
        assertTrue(duplicate.message().contains("频繁"));
        verify(client, times(2)).sendGroupMessage(anyLong(), anyString());
    }

    @Test void mentionRequiresItsOwnPermissionAndValidTarget() throws Exception {
        when(player.hasPermission("arcartxsuite.qqbot.at")).thenReturn(false);
        assertFalse(await(gateway.sendPlayer(player, "hello", 123456L)).success());
        when(player.hasPermission("arcartxsuite.qqbot.at")).thenReturn(true);
        assertFalse(await(gateway.sendPlayer(player, "hello", -1L)).success());
        verify(client, never()).sendGroupMessageAt(anyLong(), anyLong(), anyString());
    }

    @Test void mentionCooldownRejectsRepeatedMentionsBeforeAnotherRequestIsIssued() throws Exception {
        assertTrue(await(gateway.sendPlayer(player, "first", 123456L)).success());
        var result = await(gateway.sendPlayer(player, "second", 123456L));
        assertFalse(result.success());
        assertTrue(result.message().contains("@ 操作"));
        verify(client, times(2)).sendGroupMessageAt(anyLong(), eq(123456L), anyString());
    }

    @Test void oversizedBlankAndControlCharacterInputCannotReachOneBot() throws Exception {
        for (String text : List.of("x".repeat(33), "  ", "hello\u0000world", "hello\rworld")) {
            assertFalse(await(gateway.sendPlayer(player, text, null)).success());
        }
        assertFalse(await(gateway.sendPlayer(player, null, null)).success());
        verify(client, never()).sendGroupMessage(anyLong(), anyString());
    }

    @Test void lengthCountsUnicodeCodePointsAndCqSyntaxRemainsPlainText() throws Exception {
        String emoji = "😀".repeat(32);
        assertTrue(await(gateway.sendPlayer(player, emoji, null)).success());
        gateway.clearPlayer(player.getUniqueId());
        assertTrue(await(gateway.sendPlayer(player, "[CQ:at,qq=all]", null)).success());
        verify(client).sendGroupMessage(BOTH_GROUP, "[survival] Alice/Alice: [CQ:at,qq=all]");
        verify(client, never()).sendGroupMessageAt(anyLong(), anyLong(), anyString());
    }

    @Test void disconnectedClientCannotConsumeAPlayerMessage() throws Exception {
        when(client.isConnected()).thenReturn(false);
        assertFalse(await(gateway.sendPlayer(player, "hello", null)).success());
        when(client.isConnected()).thenReturn(true);
        assertTrue(await(gateway.sendPlayer(player, "retry", null)).success());
        verify(client, times(2)).sendGroupMessage(anyLong(), anyString());
    }

    @Test void sameUuidNewConnectionRejectsTheCapturedOldQueuedMessage() throws Exception {
        requireBinding();
        Player blocker = player("Bob");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(repository.findByPlayer(blocker.getUniqueId())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("worker was not released");
            return binding(blocker);
        });
        var bound = binding(player);
        when(repository.findByPlayer(player.getUniqueId())).thenReturn(bound);
        var blockingSend = gateway.sendPlayer(blocker, "blocking", null);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var oldSend = gateway.sendPlayer(player, "old queued message", null);
            assertFalse(oldSend.isDone());
            var previous = sessions.get(player.getUniqueId());
            sessions.put(player.getUniqueId(), admitted(player.getUniqueId(), UUID.randomUUID(), previous.generation()));
            release.countDown();
            assertTrue(await(blockingSend).success());
            var result = await(oldSend);
            assertFalse(result.success());
            assertTrue(result.message().contains("会话已变更"));
            verify(client, never()).sendGroupMessage(anyLong(), contains("old queued message"));
        } finally { release.countDown(); }
    }

    @Test void generationChangeInSameConnectionAlsoRejectsCapturedIntent() throws Exception {
        requireBinding();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(repository.findByPlayer(player.getUniqueId())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("worker was not released");
            return binding(player);
        });
        var send = gateway.sendPlayer(player, "old generation", null);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var previous = sessions.get(player.getUniqueId());
            sessions.put(player.getUniqueId(), admitted(player.getUniqueId(), previous.connectionId(), previous.generation() + 1));
            release.countDown();
            assertFalse(await(send).success());
            verify(client, never()).sendGroupMessage(anyLong(), anyString());
        } finally { release.countDown(); }
    }

    @Test void asynchronousCallerWaitsForMainThreadCapture() throws Exception {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        var result = gateway.sendPlayer(player, "hello", null);
        assertFalse(result.isDone());
        verifyNoInteractions(player);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        mainQueue.remove().run();
        assertTrue(await(result).success());
    }

    @Test void schedulerRejectionSettlesFutureWithoutDelivery() throws Exception {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenThrow(new IllegalStateException("plugin stopped"));
        var result = await(gateway.sendPlayer(player, "hello", null));
        assertFalse(result.success());
        assertTrue(result.message().contains("停止"));
        verify(client, never()).sendGroupMessage(anyLong(), anyString());
    }

    @Test void closeSettlesMarshalledFutureAndLateMainTaskCannotDeliver() throws Exception {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        var result = gateway.sendPlayer(player, "queued", null);
        gateway.close();
        assertEquals(-2, await(result).retcode());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        mainQueue.remove().run();
        assertEquals(-2, await(result).retcode());
        verify(client, never()).sendGroupMessage(anyLong(), anyString());
    }

    @Test void closeSettlesOutstandingAcknowledgementAndLateAckCannotRewriteOutcome() throws Exception {
        CompletableFuture<OneBotResult> ack = new CompletableFuture<>();
        CountDownLatch issued = new CountDownLatch(2);
        when(client.sendGroupMessage(anyLong(), anyString())).thenAnswer(call -> { issued.countDown(); return ack; });
        var result = gateway.sendPlayer(player, "in flight", null);
        assertTrue(issued.await(5, TimeUnit.SECONDS));
        gateway.close();
        assertEquals(-2, await(result).retcode());
        ack.complete(OneBotResult.success(null));
        assertEquals(-2, await(result).retcode());
    }

    @Test void systemTargetMustBeDeclaredAndDoesNotDependOnPlayerChatDirection() throws Exception {
        assertFalse(await(gateway.sendSystem(99999L, "notice")).success());
        assertTrue(await(gateway.sendSystem(INBOUND_GROUP, "system notice")).success());
        verify(client, never()).sendGroupMessage(eq(99999L), anyString());
        verify(client).sendGroupMessage(INBOUND_GROUP, "system notice");
    }

    private void requireBinding() {
        when(configuration.delivery()).thenReturn(new QQBotDeliveryConfig(32, 60000, 30, true, false));
    }

    private Player player(String name) {
        Player result = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(result.getUniqueId()).thenReturn(id);
        when(result.getName()).thenReturn(name);
        when(result.getDisplayName()).thenReturn("§a" + name);
        when(result.isOnline()).thenReturn(true);
        when(result.hasPermission(anyString())).thenReturn(true);
        sessions.put(id, admitted(id, UUID.randomUUID(), 7));
        return result;
    }

    private static AuthSnapshot admitted(UUID id, UUID connection, long generation) {
        return new AuthSnapshot(id, connection, generation, UUID.randomUUID(), AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
    }

    private static QQBotRepository.QQBotBinding binding(Player player) {
        return new QQBotRepository.QQBotBinding(123456, player.getUniqueId(), player.getName(), 1);
    }

    private static QQBotGroupConfig group(long id, String mode) {
        return new QQBotGroupConfig(id, mode, "[{server}] {player}/{display_name}: {message}", "{message}", true, "", "");
    }

    private static CompletableFuture<OneBotResult> acknowledged() {
        return CompletableFuture.completedFuture(OneBotResult.success(null));
    }

    private static OneBotResult await(CompletableFuture<OneBotResult> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }
}
