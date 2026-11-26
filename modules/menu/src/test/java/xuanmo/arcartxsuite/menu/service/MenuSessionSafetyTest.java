package xuanmo.arcartxsuite.menu.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;
import xuanmo.arcartxsuite.menu.config.MenuModuleConfiguration;
import xuanmo.arcartxsuite.menu.packet.MenuUiPacketHandler;

class MenuSessionSafetyTest {
    @Test
    void freeMenuSkipsProxyConnectButRunsLocalActions(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: single
            layout: panel
            open-actions: ["connect: remote", "message: local-action"]
            """);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.openPanel("single");
            fixture.runTasks();
            verify(fixture.player).sendMessage("local-action");
            verify(fixture.player, never()).sendPluginMessage(any(), eq("BungeeCord"), any());
        }
    }

    @Test
    void escNeedsAnOpenUiAndCloseNeedsTheLiveSessionToken(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.packet("esc_open");
            fixture.tasks.remove(0).run();
            verify(fixture.player, never()).sendMessage("opened");

            when(fixture.bridge.isUiOpen(fixture.player, "menu_esc")).thenReturn(true);
            fixture.packet("esc_open");
            fixture.packet("esc_open");
            verify(fixture.player, times(1)).sendMessage("opened");
            fixture.packet("close");
            fixture.packet("close", "wrong-token");
            verify(fixture.player, never()).sendMessage("closed");

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(fixture.bridge).sendPacket(eq(fixture.player), eq("menu_esc"), eq("init"), payload.capture());
            String token = (String) ((Map<?, ?>) payload.getValue()).get("action_token");
            fixture.packet("close", token);
            fixture.packet("close", token);
            verify(fixture.player, times(1)).sendMessage("closed");
        }
    }

    @Test
    void matchedEscUsesRegisteredIdAndCanReopenAfterClientClose(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory);
        fixture.service.setRuntimeUiIds("AXS:menu_panel", "AXS:menu_esc");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Player>> onClose = ArgumentCaptor.forClass(Consumer.class);
        when(fixture.bridge.registerUiCloseCallback(eq("AXS:menu_esc"), onClose.capture())).thenReturn(true);
        fixture.service.trackUiClose("AXS:menu_esc");
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(fixture.bridge.isUiOpen(fixture.player, "AXS:menu_esc")).thenReturn(true);
            fixture.packet("esc_open");
            fixture.packet("page", "next");
            verify(fixture.bridge, never()).sendPacket(eq(fixture.player), eq("AXS:menu_esc"), eq("update"), any());
            fixture.packet("refresh");
            verify(fixture.bridge).sendPacket(eq(fixture.player), eq("AXS:menu_esc"), eq("update"), any());

            when(fixture.bridge.isUiOpen(fixture.player, "AXS:menu_esc")).thenReturn(false);
            onClose.getValue().accept(fixture.player);
            when(fixture.bridge.isUiOpen(fixture.player, "AXS:menu_esc")).thenReturn(true);
            fixture.packet("esc_open");
            verify(fixture.bridge, times(2))
                .sendPacket(eq(fixture.player), eq("AXS:menu_esc"), eq("init"), any());
            verify(fixture.player, times(2)).sendMessage("opened");
            verify(fixture.bridge, never()).isUiOpen(fixture.player, "menu_esc");
        }
    }

    @Test
    void delayedInitCannotReplaceASecondMenu(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            when(fixture.bridge.openUi(fixture.player, "menu_panel")).thenReturn(true);
            when(fixture.bridge.isUiOpen(fixture.player, "menu_panel")).thenReturn(true);
            assertTrue(fixture.service.openMenu(fixture.player, "first"));
            assertTrue(fixture.service.openMenu(fixture.player, "second"));
            fixture.tasks.forEach(Runnable::run);
            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(fixture.bridge).sendPacket(eq(fixture.player), eq("menu_panel"), eq("init"), payload.capture());
            assertEquals("second", ((Map<?, ?>) payload.getValue()).get("menuId"));
        }
    }

    @Test
    void onlyDisplayedButtonsCanExecuteAndTheirOldTokenCannotReplay(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: first
            buttons-per-page: 1
            pages:
              main:
                buttons:
                  visible:
                    order: 0
                    actions: ["message: accepted"]
                  truncated:
                    order: 1
                    actions: ["message: hidden-action"]
            """);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.openPanel("first");
            var initial = fixture.latestPayload();
            fixture.packet("click", fixture.latestToken(), "truncated");
            verify(fixture.player, never()).sendMessage("hidden-action");
            String token = fixture.latestToken();
            assertNotEquals(initial.get("action_token"), token);
            assertSame(initial.get("buttonRows"), fixture.latestPayload().get("buttonRows"));
            fixture.packet("click", token, "visible");
            fixture.packet("click", token, "visible");
            verify(fixture.player, times(1)).sendMessage("accepted");
        }
    }

    @Test
    void oldTokenCannotSelectAnUnpublishedButtonWithTheSameId(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: first
            pages:
              main:
                buttons:
                  shared:
                    actions: ["message: first-action"]
              second:
                title: "%dynamic_title%"
                buttons:
                  shared:
                    actions: ["message: second-action"]
            """);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.openPanel("first");
            String oldToken = fixture.latestToken();
            try (var conditions = mockStatic(MenuConditionEvaluator.class, CALLS_REAL_METHODS)) {
                conditions.when(() -> MenuConditionEvaluator.applyPlaceholders(fixture.player, "%dynamic_title%"))
                    .thenThrow(new IllegalStateException("test rendering failure"));
                assertThrows(IllegalStateException.class, () -> fixture.packet("page", "next"));
            }

            fixture.packet("click", oldToken, "shared");
            verify(fixture.player, never()).sendMessage("first-action");
            verify(fixture.player, never()).sendMessage("second-action");
            fixture.packet("refresh");
            fixture.packet("click", fixture.latestToken(), "shared");
            verify(fixture.player).sendMessage("second-action");
        }
    }

    @Test
    void pageActionsPublishOnceAndPageIndicesStayWithinTheDefinition(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: first
            pages:
              main:
                buttons:
                  next:
                    actions: ["page: next", "message: after-page"]
              last:
                buttons: {}
            """);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.openPanel("first");
            doAnswer(call -> {
                verify(fixture.bridge, never())
                    .sendPacket(eq(fixture.player), eq("menu_panel"), eq("update"), any());
                return null;
            }).when(fixture.player).sendMessage("after-page");
            fixture.packet("click", fixture.latestToken(), "next");
            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(fixture.bridge, times(1))
                .sendPacket(eq(fixture.player), eq("menu_panel"), eq("update"), payload.capture());
            assertEquals(1, ((Map<?, ?>) payload.getValue()).get("pageIndex"));
            fixture.packet("page", "next");
            verify(fixture.bridge, times(1))
                .sendPacket(eq(fixture.player), eq("menu_panel"), eq("update"), any());
            fixture.packet("refresh");
            payload = ArgumentCaptor.forClass(Object.class);
            verify(fixture.bridge, times(2))
                .sendPacket(eq(fixture.player), eq("menu_panel"), eq("update"), payload.capture());
            assertEquals(1, ((Map<?, ?>) payload.getValue()).get("pageIndex"));
            assertEquals(false, ((Map<?, ?>) payload.getValue()).get("hasNext"));
        }
    }

    @Test
    void passiveRefreshSkipsUnchangedDynamicViewsAndPublishesChanges(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: first
            title: "%dynamic_title%"
            pages:
              main:
                buttons: {}
            """);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit();
             var conditions = mockStatic(MenuConditionEvaluator.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            conditions.when(() -> MenuConditionEvaluator.applyPlaceholders(fixture.player, "%dynamic_title%"))
                .thenReturn("原有显示");
            fixture.openPanel("first");
            String originalToken = fixture.latestToken();

            fixture.service.refreshMenu(fixture.player);
            fixture.service.refreshMenu(fixture.player);
            verify(fixture.bridge, never()).sendPacket(eq(fixture.player), eq("menu_panel"), eq("update"), any());
            assertEquals(originalToken, fixture.latestToken());

            conditions.when(() -> MenuConditionEvaluator.applyPlaceholders(fixture.player, "%dynamic_title%"))
                .thenReturn("更新显示");
            fixture.service.refreshMenu(fixture.player);
            assertEquals("更新显示", fixture.latestPayload().get("title"));
            assertNotEquals(originalToken, fixture.latestToken());
            verify(fixture.bridge).sendPacket(eq(fixture.player), eq("menu_panel"), eq("update"), any());
        }
    }

    @Test
    void aForgedTokenDoesNotRunMenuOpenConditions(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.openPanel("first");
            try (var conditions = mockStatic(MenuConditionEvaluator.class, CALLS_REAL_METHODS)) {
                fixture.packet("click", "outdated", "visible");
                conditions.verifyNoInteractions();
            }
        }
    }

    @Test
    void anExecutedClickCannotReplayWhenItsRefreshFails(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: first
            title: "%dynamic_title%"
            pages:
              main:
                buttons:
                  visible:
                    actions: ["message: accepted"]
            """);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.openPanel("first");
            String spent = fixture.latestToken();
            try (var conditions = mockStatic(MenuConditionEvaluator.class, CALLS_REAL_METHODS)) {
                conditions.when(() -> MenuConditionEvaluator.applyPlaceholders(fixture.player, "%dynamic_title%"))
                    .thenThrow(new IllegalStateException("test rendering failure"));
                assertThrows(IllegalStateException.class, () -> fixture.packet("click", spent, "visible"));
                fixture.packet("click", spent, "visible");
            }
            verify(fixture.player, times(1)).sendMessage("accepted");
            fixture.packet("refresh");
            assertNotEquals(spent, fixture.latestToken());
            fixture.packet("click", fixture.latestToken(), "visible");
            verify(fixture.player, times(2)).sendMessage("accepted");
        }
    }

    @Test
    void aFailedPublicationDoesNotAuthorizeItsToken(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory);
        when(fixture.bridge.sendPacket(any(), anyString(), anyString(), any())).thenReturn(false);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.openPanel("first");
            assertFalse(fixture.service.beginWriteAction(fixture.player, List.of("close", fixture.latestToken())));
        }
    }

    @Test
    void revokedMenuPermissionRejectsTheCurrentTokenAndClosesOnRefresh(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: first
            permission: private.menu
            close-actions: ["message: should-not-run"]
            pages:
              main:
                buttons:
                  visible:
                    actions: ["message: should-not-run"]
            """);
        when(fixture.player.hasPermission("private.menu")).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            fixture.openPanel("first");
            String token = fixture.latestToken();
            when(fixture.player.hasPermission("private.menu")).thenReturn(false);
            fixture.packet("click", token, "visible");
            fixture.packet("close", token);
            verify(fixture.player, never()).sendMessage("should-not-run");
            fixture.packet("refresh");
            verify(fixture.bridge).closeUi(fixture.player, "menu_panel");
            assertFalse(fixture.service.beginWriteAction(fixture.player, List.of("click", token, "visible")));
        }
    }

    @Test
    void delayedPlayerActionSurvivesClosingItsMenuButNotRetiringItsConnection(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: first
            pages:
              main:
                buttons:
                  run:
                    actions: ["close", "delay: 10", "command: list", "close"]
            """);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            bukkit.when(() -> Bukkit.getPlayer(fixture.id)).thenReturn(fixture.player);
            fixture.openPanel("first");
            fixture.packet("click", fixture.latestToken(), "run");
            fixture.openPanel("first"); // resume the retired menu's action while another session opens
            fixture.runTasks(); // execute the next-tick command
            verify(fixture.player).performCommand("list");
            verify(fixture.bridge, times(1)).closeUi(fixture.player, "menu_panel");

            fixture.openPanel("first");
            fixture.packet("click", fixture.latestToken(), "run");
            var handle = fixture.handles.get(fixture.handles.size() - 1);
            fixture.service.handlePlayerQuit(fixture.player);
            verify(handle).cancel();
            fixture.runTasks();
            verify(fixture.player, times(1)).performCommand("list");
        }
    }

    @Test
    void throttledConsoleCommandRechecksPermissionConnectionAndModuleLifetime(@TempDir Path directory) throws Exception {
        var fixture = new Fixture(directory, """
            id: first
            pages:
              main:
                buttons:
                  run:
                    permission: menu.run
                    actions: ["console: say accepted"]
            """);
        var console = mock(ConsoleCommandSender.class);
        var drain = ArgumentCaptor.forClass(Runnable.class);
        when(fixture.scheduler.runTaskTimer(eq(fixture.plugin), drain.capture(), eq(1L), eq(1L)))
            .thenReturn(mock(BukkitTask.class));
        when(fixture.player.hasPermission("menu.run")).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var auth = fixture.admit()) {
            bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            bukkit.when(() -> Bukkit.getPlayer(fixture.id)).thenReturn(fixture.player);
            ThrottledCommandExecutor.init(fixture.plugin, 20);
            try {
                fixture.openPanel("first");
                fixture.packet("click", fixture.latestToken(), "run");
                drain.getValue().run();
                bukkit.verify(() -> Bukkit.dispatchCommand(console, "say accepted"), times(1));

                fixture.openPanel("first");
                fixture.packet("click", fixture.latestToken(), "run");
                assertEquals(1, ThrottledCommandExecutor.pendingCount());
                when(fixture.player.hasPermission("menu.run")).thenReturn(false);
                drain.getValue().run();
                bukkit.verify(() -> Bukkit.dispatchCommand(console, "say accepted"), times(1));

                when(fixture.player.hasPermission("menu.run")).thenReturn(true);
                fixture.openPanel("first");
                fixture.packet("click", fixture.latestToken(), "run");
                auth.when(() -> AuthAccess.snapshot(fixture.id)).thenReturn(fixture.session(UUID.randomUUID()));
                drain.getValue().run();
                bukkit.verify(() -> Bukkit.dispatchCommand(console, "say accepted"), times(1));

                fixture.openPanel("first");
                fixture.packet("click", fixture.latestToken(), "run");
                fixture.service.shutdown();
                drain.getValue().run();
                bukkit.verify(() -> Bukkit.dispatchCommand(console, "say accepted"), times(1));
                assertEquals(0, ThrottledCommandExecutor.pendingCount());

                var receipt = new java.util.concurrent.atomic.AtomicReference<Boolean>();
                bukkit.when(() -> Bukkit.dispatchCommand(console, "say receipt")).thenReturn(true);
                ThrottledCommandExecutor.dispatch("say receipt", receipt::set);
                drain.getValue().run();
                assertEquals(Boolean.TRUE, receipt.get());
            } finally {
                ThrottledCommandExecutor.shutdown();
            }
        }
    }

    private static final class Fixture {
        final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final Player player = mock(Player.class);
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final UUID id = UUID.randomUUID();
        final List<Runnable> tasks = new ArrayList<>();
        final List<BukkitTask> handles = new ArrayList<>();
        final MenuService service;
        final MenuUiPacketHandler packets;

        Fixture(Path directory) throws Exception {
            this(directory, """
                id: esc
                layout: esc
                ui-id: menu_esc
                open-actions: ["message: opened"]
                close-actions: ["message: closed"]
                ---
                id: first
                layout: panel
                ---
                id: second
                layout: panel
                """);
        }

        Fixture(Path directory, String definitionsYaml) throws Exception {
            Server server = mock(Server.class);
            Messenger messenger = mock(Messenger.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getMessenger()).thenReturn(messenger);
            when(bridge.isAvailable()).thenReturn(true);
            when(bridge.sendPacket(any(), anyString(), anyString(), any())).thenReturn(true);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn("tester");
            when(player.getDisplayName()).thenReturn("tester");
            when(player.isOnline()).thenReturn(true);
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                tasks.add(call.getArgument(1));
                var handle = mock(BukkitTask.class);
                handles.add(handle);
                return handle;
            });
            when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
                tasks.add(call.getArgument(1));
                var handle = mock(BukkitTask.class);
                handles.add(handle);
                return handle;
            });
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("settings.click-cooldown-ms", 0);
            MenuModuleConfiguration config = MenuModuleConfiguration.load(yaml);
            Path definitions = Files.createDirectories(config.menusDirectory(directory.toFile()).toPath());
            Files.writeString(definitions.resolve("menus.yml"), definitionsYaml);
            service = new MenuService(plugin, Logger.getAnonymousLogger(), bridge, null, config,
                null, null, null, () -> false, null);
            service.reload(directory.toFile());
            packets = new MenuUiPacketHandler(service, "AXS_MENU");
        }

        MockedStatic<AuthAccess> admit() {
            var auth = mockStatic(AuthAccess.class);
            auth.when(() -> AuthAccess.admitted(id)).thenReturn(true);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(session(UUID.randomUUID()));
            return auth;
        }

        AuthSnapshot session(UUID connection) {
            return new AuthSnapshot(id, connection, 1, id, AuthSnapshot.Registration.REGISTERED,
                AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        }

        void packet(String... args) {
            assertTrue(packets.handleClientPacket(player, "AXS_MENU", List.of(args)));
        }

        void openPanel(String menuId) {
            when(bridge.openUi(player, "menu_panel")).thenReturn(true);
            when(bridge.isUiOpen(player, "menu_panel")).thenReturn(true);
            assertTrue(service.openMenu(player, menuId));
            runTasks();
        }

        void runTasks() {
            var batch = List.copyOf(tasks);
            tasks.clear();
            batch.forEach(Runnable::run);
        }

        String latestToken() {
            return (String) latestPayload().get("action_token");
        }

        Map<?, ?> latestPayload() {
            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(bridge, atLeastOnce()).sendPacket(eq(player), anyString(), anyString(), payload.capture());
            return (Map<?, ?>) payload.getValue();
        }
    }
}
