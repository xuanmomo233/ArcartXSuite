package xuanmo.arcartxsuite.qqbot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.AxsCapabilities;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AuthRuntimeCapability;
import xuanmo.arcartxsuite.qqbot.config.QQBotBindingConfig;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotUiConfig;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotResult;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;

class QQBotUiServiceTest {
    private record Packet(String ui, String handler, Map<String, Object> body) {}
    private final Queue<Runnable> mainQueue = new ArrayDeque<>();
    private final Queue<Runnable> workerQueue = new ArrayDeque<>();
    private final List<Packet> packets = new ArrayList<>();
    private final List<Runnable> closeCallbacks = new ArrayList<>();
    private final AtomicBoolean primary = new AtomicBoolean(true);
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<AxsCapabilities> capabilities;
    private Player player;
    private QQBotUiService ui;
    private QQBotBindService bindings;
    private QQBotRepository repository;
    private PacketBridgeAPI bridge;
    private UUID playerId;

    @BeforeEach void setUp() {
        bukkit = mockStatic(Bukkit.class);
        capabilities = mockStatic(AxsCapabilities.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::isPrimaryThread).thenAnswer(call -> primary.get());
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            mainQueue.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            workerQueue.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        bukkit.when(Bukkit::getMaxPlayers).thenReturn(20);
        QQBotConfiguration config = mock(QQBotConfiguration.class);
        QQBotBindingConfig bindConfig = mock(QQBotBindingConfig.class);
        when(bindConfig.enabled()).thenReturn(true);
        when(bindConfig.bindPrefix()).thenReturn("绑定");
        when(config.binding()).thenReturn(bindConfig);
        when(config.groups()).thenReturn(List.of());
        when(config.ui()).thenReturn(new QQBotUiConfig("bind", "notice", "admin", true, true, false));
        repository = mock(QQBotRepository.class);
        when(repository.getBindingsPage(anyInt(), anyInt())).thenReturn(List.of());
        bindings = mock(QQBotBindService.class);
        bridge = mock(PacketBridgeAPI.class);
        when(bridge.isUiOpen(eq(player), anyString())).thenReturn(true);
        when(bridge.openUiWithCallback(eq(player), anyString(), any(Runnable.class))).thenAnswer(call -> {
            closeCallbacks.add(call.getArgument(2)); return true;
        });
        when(bridge.sendPacket(eq(player), anyString(), anyString(), any())).thenAnswer(call -> {
            assertTrue(primary.get(), "UI packets must be delivered on the server thread");
            @SuppressWarnings("unchecked") Map<String, Object> body = call.getArgument(3);
            packets.add(new Packet(call.getArgument(1), call.getArgument(2), body)); return true;
        });
        ui = new QQBotUiService(plugin, config, repository, bindings, bridge, Logger.getAnonymousLogger());
        ui.setBindUiId("bind"); ui.setAdminUiId("admin"); ui.setNotifyUiId("notice");
    }
    @AfterEach void tearDown() { ui.shutdown(); capabilities.close(); bukkit.close(); }
    private void drain(Queue<Runnable> queue, boolean onMain) {
        primary.set(onMain);
        while (!queue.isEmpty()) queue.remove().run();
        primary.set(true);
    }
    private void loadedBinding() { ui.openBindCenter(player); drain(mainQueue, true); drain(workerQueue, false); drain(mainQueue, true); }
    private void loadedAdmin() { ui.openAdminPanel(player); drain(mainQueue, true); drain(workerQueue, false); drain(mainQueue, true); }
    private String token() {
        return packets.stream().map(Packet::body).filter(body -> body.containsKey("action_token"))
            .reduce((first, second) -> second).orElseThrow().get("action_token").toString();
    }
    private Packet last() { return packets.get(packets.size() - 1); }

    @Test void deliveryWaitsForAcknowledgementAndRetainsInFlightState() {
        loadedBinding();
        CompletableFuture<OneBotResult> ack = new CompletableFuture<>();
        AtomicInteger sends = new AtomicInteger();
        ui.setSendPlayerMessageCallback((who, message) -> { sends.incrementAndGet(); return ack; });
        String token = token();
        ui.handleBindPacket(player, "send_message", List.of(token, "hello"));
        assertEquals(true, last().body().get("busy"));
        assertEquals("info", last().body().get("type"));
        ui.handleBindPacket(player, "send_message", List.of(token, "duplicate"));
        assertEquals(1, sends.get());
        primary.set(false);
        ack.complete(OneBotResult.success(new JsonObject()));
        assertEquals(true, last().body().get("busy"));
        drain(mainQueue, true);
        assertEquals(false, last().body().get("busy"));
        assertEquals("success", last().body().get("type"));
    }

    @Test void closedSessionCannotReceiveLateDeliveryOrAcceptOldToken() {
        loadedBinding();
        CompletableFuture<OneBotResult> ack = new CompletableFuture<>();
        AtomicInteger sends = new AtomicInteger();
        ui.setSendPlayerMessageCallback((who, message) -> { sends.incrementAndGet(); return ack; });
        String oldToken = token();
        ui.handleBindPacket(player, "send_message", List.of(oldToken, "hello"));
        closeCallbacks.get(0).run();
        int count = packets.size();
        ack.complete(OneBotResult.success(new JsonObject()));
        drain(mainQueue, true);
        assertEquals(count, packets.size());
        loadedBinding();
        ui.handleBindPacket(player, "send_message", List.of(oldToken, "replayed"));
        assertEquals(1, sends.get());
    }

    @Test void disconnectedAndUnknownDeliveryAreErrorsRatherThanPrematureSuccess() {
        loadedBinding();
        ui.setSendPlayerMessageCallback((who, message) -> CompletableFuture.completedFuture(OneBotResult.failure(-2, "连接中断，投递结果未知，请勿立即重发。")));
        ui.handleBindPacket(player, "send_message", List.of(token(), "hello"));
        assertEquals("error", last().body().get("type"));
        assertTrue(last().body().get("message").toString().contains("未知"));
        assertEquals(false, last().body().get("busy"));
    }

    @Test void bindingConfirmationUsesHostAndRefreshesAuthoritativeStateOffThread() {
        loadedBinding();
        CompletableFuture<QQBotBindService.BindResult> completed = new CompletableFuture<>();
        when(bindings.confirmBind(player, "123456")).thenAnswer(call -> {
            assertTrue(primary.get()); return completed;
        });
        when(repository.findByPlayer(playerId)).thenAnswer(call -> {
            assertFalse(primary.get(), "binding projections must be read off-thread"); return null;
        });
        ui.handleBindPacket(player, "confirm_bind", List.of(token(), "123456"));
        primary.set(false);
        completed.complete(new QQBotBindService.BindResult(true, "绑定成功"));
        drain(mainQueue, true);
        drain(workerQueue, false);
        drain(mainQueue, true);
        assertEquals("update", last().handler());
        assertEquals(false, last().body().get("bound"));
        assertEquals("绑定成功", last().body().get("message"));
    }

    @Test void moduleAdministratorDoesNotGainConsolePrivileges() {
        when(player.hasPermission("arcartxsuite.admin")).thenReturn(false);
        when(player.hasPermission("arcartxsuite.qqbot.console")).thenReturn(false);
        loadedAdmin();
        ui.handleAdminPacket(player, "exec_command", List.of(token(), "op TestPlayer"));
        assertEquals("error", last().body().get("type"));
        bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never());
    }

    @Test void administratorUnbindsTheStableProfileRatherThanAPlayerName() {
        loadedAdmin();
        UUID target = UUID.randomUUID();
        when(repository.findByPlayer(target)).thenAnswer(call -> {
            assertFalse(primary.get());
            return new QQBotRepository.QQBotBinding(123456L, target, ".BedrockProfile", 1);
        });
        when(bindings.unbindByPlayer(target)).thenAnswer(call -> {
            assertTrue(primary.get());
            return CompletableFuture.completedFuture(new QQBotBindService.BindResult(true, "解绑成功"));
        });
        ui.handleAdminPacket(player, "admin_unbind", List.of(token(), target.toString()));
        drain(workerQueue, false);
        drain(mainQueue, true);
        drain(workerQueue, false);
        drain(mainQueue, true);
        verify(bindings).unbindByPlayer(target);
        verify(repository, never()).findByPlayerName(anyString());
        assertEquals("解绑成功", last().body().get("message"));
    }

    @Test void oldCloseCallbackCannotInvalidateNewlyOpenedPanel() {
        loadedBinding();
        loadedAdmin();
        String newToken = token();
        closeCallbacks.get(0).run();
        int count = packets.size();
        ui.handleAdminPacket(player, "fetch_bindings", List.of(newToken, "0"));
        drain(workerQueue, false);
        drain(mainQueue, true);
        assertEquals(count + 1, packets.size());
        assertEquals("bindings", last().handler());
    }

    @Test void reconnectWithSameUuidCannotReceiveOldSessionResponse() {
        loadedBinding();
        CompletableFuture<OneBotResult> ack = new CompletableFuture<>();
        ui.setSendPlayerMessageCallback((who, message) -> ack);
        ui.handleBindPacket(player, "send_message", List.of(token(), "hello"));
        Player replacement = mock(Player.class);
        bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(replacement);
        int count = packets.size();
        ack.complete(OneBotResult.success(new JsonObject()));
        drain(mainQueue, true);
        assertEquals(count, packets.size());
    }

    @Test void shutdownInvalidatesPendingCallbacks() {
        loadedBinding();
        CompletableFuture<OneBotResult> ack = new CompletableFuture<>();
        ui.setSendPlayerMessageCallback((who, message) -> ack);
        ui.handleBindPacket(player, "send_message", List.of(token(), "hello"));
        ui.shutdown();
        int count = packets.size();
        ack.complete(OneBotResult.success(new JsonObject()));
        drain(mainQueue, true);
        assertEquals(count, packets.size());
    }

    @Test void notificationsRequireInitializedClientAndAdmittedCurrentConnection() {
        AuthRuntimeCapability auth = mock(AuthRuntimeCapability.class);
        capabilities.when(() -> AxsCapabilities.get(AuthRuntimeCapability.class)).thenReturn(auth);
        when(auth.snapshot(playerId)).thenReturn(AuthSnapshot.absent(playerId));
        ui.sendNotification(player, "nick", "hello");
        ui.markClientReady(player);
        ui.sendNotification(player, "nick", "hello");
        assertTrue(packets.isEmpty());
        when(auth.snapshot(playerId)).thenReturn(new AuthSnapshot(playerId, UUID.randomUUID(), 1, UUID.randomUUID(),
            AuthSnapshot.Registration.REGISTERED, AuthSnapshot.Stage.ADMITTED, "test", Set.of(), ""));
        ui.sendNotification(player, "nick", "hello");
        assertEquals("notice", last().ui());
        ui.clearPlayerState(playerId);
        int count = packets.size();
        ui.sendNotification(player, "nick", "hello");
        assertEquals(count, packets.size());
    }

    @Test void pageDecoderAcceptsClientDecimalIntegersAndRejectsUnboundedInput() {
        assertEquals(2, QQBotUiService.parsePage("2.0"));
        for (String value : List.of("NaN", "Infinity", "-1", "1.5", "2147483647", "bad")) assertEquals(0, QQBotUiService.parsePage(value));
    }
}
