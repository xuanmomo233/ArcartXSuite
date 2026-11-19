package xuanmo.arcartxsuite.qqbot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.AxsCapabilities;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener;
import xuanmo.arcartxsuite.api.event.chat.PublicChatMessageEvent;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.qqbot.config.*;
import xuanmo.arcartxsuite.qqbot.onebot.*;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;

class QQBotServiceTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<AxsCapabilities> capabilities;
    private MockedStatic<AuthAccess> auth;
    private final Queue<Runnable> mainQueue = new ArrayDeque<>();
    private JavaPlugin plugin;
    private QQBotConfiguration config;
    private QQBotRepository repository;
    private QQBotBindService bindings;
    private OneBotClient client;
    private QQBotMessageGateway gateway;
    private QQBotService service;
    private Player player;
    private QQGroupEventListener listener;

    @BeforeEach void setUp() throws Exception {
        bukkit = mockStatic(Bukkit.class);
        capabilities = mockStatic(AxsCapabilities.class);
        auth = mockStatic(AuthAccess.class);
        plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            mainQueue.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
            .thenReturn(mock(BukkitTask.class));
        config = mock(QQBotConfiguration.class, RETURNS_DEEP_STUBS);
        group("both");
        when(config.serverId()).thenReturn("test");
        when(config.onebot().wsUrl()).thenReturn("ws://127.0.0.1:3001");
        repository = mock(QQBotRepository.class);
        bindings = mock(QQBotBindService.class);
        client = mock(OneBotClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.sendGroupMessage(anyLong(), anyString())).thenReturn(ok());
        when(client.sendGroupMessageAt(anyLong(), anyLong(), anyString())).thenReturn(ok());
        when(client.deleteMessage(anyInt())).thenReturn(ok());
        when(client.setGroupBan(anyLong(), anyLong(), anyInt())).thenReturn(ok());
        service = new QQBotService(plugin, config, bindings, repository, Logger.getAnonymousLogger(),
            mock(PlaceholderResolverAPI.class), mock(MessageProvider.class), () -> null);
        service.setClient(client);
        gateway = mock(QQBotMessageGateway.class);
        when(gateway.sendPlayer(any(), anyString(), any())).thenReturn(ok());
        Field boundary = QQBotService.class.getDeclaredField("messageGateway");
        boundary.setAccessible(true); boundary.set(service, gateway);
        service.start();
        service.onBotConnected();
        player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
        listener = mock(QQGroupEventListener.class);
        service.addGroupEventListener(listener);
    }
    @AfterEach void tearDown() {
        service.shutdown();
        auth.close(); capabilities.close(); bukkit.close();
    }
    private static CompletableFuture<OneBotResult> ok() {
        return CompletableFuture.completedFuture(OneBotResult.success(new JsonObject()));
    }
    private void group(String mode) {
        when(config.groups()).thenReturn(List.of(new QQBotGroupConfig(100L, mode,
            "[MC] {player}: {message}", "[QQ] {nick}: {message}", false, "", "", "normal")));
    }
    private static OneBotEvent message(int id, String text) {
        JsonObject json = new JsonObject();
        json.addProperty("post_type", "message"); json.addProperty("message_type", "group");
        json.addProperty("group_id", 100L); json.addProperty("user_id", 200L);
        json.addProperty("message_id", id); json.addProperty("message", text);
        JsonObject sender = new JsonObject(); sender.addProperty("nickname", "群成员");
        json.add("sender", sender);
        return new OneBotEvent(json);
    }
    private static OneBotEvent notice(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("post_type", "notice"); json.addProperty("notice_type", type);
        json.addProperty("group_id", 100L); json.addProperty("user_id", 200L);
        return new OneBotEvent(json);
    }
    private void drainMain() { while (!mainQueue.isEmpty()) mainQueue.remove().run(); }

    @Test void publicCapabilityReceivesMessagesIndependentlyOfChatSync() {
        group("none");
        service.handleOneBotEvent(message(1, "hello"));
        verify(listener).onGroupMessage(100L, 200L, "群成员", "hello");
        assertTrue(mainQueue.isEmpty(), "disabled QQ-to-game chat must not schedule game broadcast");
        verify(player, never()).sendMessage(anyString());
    }

    @Test void joinAndLeaveCallbacksDoNotDependOnWelcomeOrChatSync() {
        group("none");
        service.handleOneBotEvent(notice("group_increase"));
        service.handleOneBotEvent(notice("group_decrease"));
        verify(listener).onMemberJoin(100L, 200L);
        verify(listener).onMemberLeave(100L, 200L);
        verify(client, never()).sendGroupMessageAt(anyLong(), anyLong(), anyString());
    }

    @Test void moderationCooldownStillFiltersSubsequentForbiddenMessages() {
        when(config.moderation().autoModeration()).thenReturn(
            new QQBotModerationConfig.AutoModeration(true, List.of("http://"), 600, 300));
        service.handleOneBotEvent(message(11, "http://example.invalid/first"));
        service.handleOneBotEvent(message(12, "http://example.invalid/second"));
        verify(client).deleteMessage(11);
        verify(client, never()).deleteMessage(12);
        verify(client, times(1)).setGroupBan(100L, 200L, 600);
        verify(listener, never()).onGroupMessage(anyLong(), anyLong(), anyString(), anyString());
        assertTrue(mainQueue.isEmpty());
    }

    @Test void shutdownPreventsPreviouslyQueuedGameBroadcast() {
        service.handleOneBotEvent(message(21, "queued"));
        assertFalse(mainQueue.isEmpty());
        service.shutdown();
        drainMain();
        verify(player, never()).sendMessage(anyString());
        service.handleOneBotEvent(message(22, "late"));
        verify(listener, never()).onGroupMessage(100L, 200L, "群成员", "late");
    }

    @Test void shutdownPreventsPreviouslyQueuedModerationCommand() {
        when(config.moderation().syncBanEnabled()).thenReturn(true);
        when(config.moderation().syncBanCommand()).thenReturn("tempban {name} {duration} {reason}");
        when(config.moderation().syncBanUseDuration()).thenReturn(true);
        when(config.moderation().syncBanReason()).thenReturn("QQ群禁言同步");
        when(bindings.findByQq(200L)).thenReturn(
            new QQBotRepository.QQBotBinding(200L, UUID.randomUUID(), "TestPlayer", 1L));
        JsonObject json = notice("group_ban").raw();
        json.addProperty("sub_type", "ban"); json.addProperty("duration", 600);
        service.handleOneBotEvent(new OneBotEvent(json));
        assertFalse(mainQueue.isEmpty());
        service.shutdown(); drainMain();
        bukkit.verify(() -> Bukkit.dispatchCommand(any(CommandSender.class), anyString()), never());
    }

    @Test void acceptedPublicChatUsesGatewayAndClosedServiceCannotRelayIt() {
        service.onPublicChat(new PublicChatMessageEvent(player, "global", "accepted"));
        verify(gateway).sendPlayer(player, "accepted", null, "global");
        service.shutdown();
        service.onPublicChat(new PublicChatMessageEvent(player, "global", "late"));
        verify(gateway, never()).sendPlayer(player, "late", null, "global");
    }

    @Test void bukkitCancelledRawChatDoesNotRelayAndFallbackRequiresOptIn() throws Exception {
        when(config.delivery().vanillaChatFallback()).thenReturn(true);
        AsyncPlayerChatEvent cancelled = new AsyncPlayerChatEvent(false, player, "cancelled", new HashSet<>());
        cancelled.setCancelled(true);
        var method = QQBotService.class.getMethod("onAsyncPlayerChat", AsyncPlayerChatEvent.class);
        EventHandler annotation = method.getAnnotation(EventHandler.class);
        RegisteredListener registration = new RegisteredListener(service,
            (ignored, event) -> service.onAsyncPlayerChat((AsyncPlayerChatEvent) event),
            EventPriority.MONITOR, plugin, annotation.ignoreCancelled());
        registration.callEvent(cancelled);
        verify(gateway, never()).sendPlayer(player, "cancelled", null);
        when(config.delivery().vanillaChatFallback()).thenReturn(false);
        registration.callEvent(new AsyncPlayerChatEvent(false, player, "disabled", new HashSet<>()));
        verify(gateway, never()).sendPlayer(player, "disabled", null);
        when(config.delivery().vanillaChatFallback()).thenReturn(true);
        registration.callEvent(new AsyncPlayerChatEvent(false, player, "allowed", new HashSet<>()));
        verify(gateway).sendPlayer(player, "allowed", null);
    }
}
