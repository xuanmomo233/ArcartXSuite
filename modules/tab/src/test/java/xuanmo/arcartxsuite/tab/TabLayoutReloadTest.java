package xuanmo.arcartxsuite.tab;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TabLayoutReloadTest {
    @Test
    void reloadClosesTheOldHudAndOpensTheOtherLayoutForTheSameAdmittedPlayer(@TempDir Path folder) throws Exception {
        var fixture = new Fixture(folder, "true");
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(fixture.admitted, fixture.restricted));
            bukkit.when(() -> Bukkit.getPlayer(fixture.admittedId)).thenReturn(fixture.admitted);
            bukkit.when(() -> Bukkit.getPlayer(fixture.restrictedId)).thenReturn(fixture.restricted);
            auth.when(() -> AuthAccess.admitted(fixture.admittedId)).thenReturn(true);
            var module = new TabModule();
            try {
                assertTrue(module.onEnable(fixture.context).toCompletableFuture().join());
                fixture.tasks.removeFirst().run();
                verify(fixture.bridge).openUiWithCallback(eq(fixture.admitted), eq("AXS:tab-simple"), any());
                for (String next : List.of("avatar", "simple")) {
                    clearInvocations(fixture.bridge);
                    fixture.layout(next);
                    module.onReload().toCompletableFuture().join();
                    String previous = next.equals("avatar") ? "simple" : "avatar";
                    verify(fixture.bridge).closeUi(fixture.admitted, "AXS:tab-" + previous);
                    verify(fixture.bridge, never()).openUiWithCallback(any(Player.class), anyString(), any());
                    assertEquals(1, fixture.tasks.size());
                    fixture.tasks.removeFirst().run();
                    verify(fixture.bridge).openUiWithCallback(eq(fixture.admitted), eq("AXS:tab-" + next), any());
                    verify(fixture.bridge, never()).openUiWithCallback(eq(fixture.restricted), anyString(), any());
                }
            } finally {
                module.onDisable();
            }
        }
    }

    @Test
    void reloadRespectsManualHudAndStaleCallbacksDoNotOpenIt(@TempDir Path folder) throws Exception {
        var fixture = new Fixture(folder, "'false'");
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(fixture.admitted));
            bukkit.when(() -> Bukkit.getPlayer(fixture.admittedId)).thenReturn(fixture.admitted);
            auth.when(() -> AuthAccess.admitted(fixture.admittedId)).thenReturn(true);
            var module = new TabModule();
            try {
                assertTrue(module.onEnable(fixture.context).toCompletableFuture().join());
                Runnable pending = fixture.tasks.removeFirst();
                fixture.layout("avatar");
                module.onReload().toCompletableFuture().join();
                assertTrue(fixture.tasks.isEmpty());
                pending.run();
                verify(fixture.bridge, never()).openUiWithCallback(any(Player.class), anyString(), any());
            } finally {
                module.onDisable();
            }
        }
    }

    @Test
    void retriesOnlyUntilTheClientAcknowledgesAndBoundsUnconfirmedAttempts(@TempDir Path folder) throws Exception {
        var fixture = new Fixture(folder, "true");
        var acknowledge = new AtomicBoolean();
        doAnswer(call -> {
            if (acknowledge.get()) call.<Runnable>getArgument(2).run();
            return true;
        }).when(fixture.bridge).openUiWithCallback(any(), anyString(), any());
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(fixture.admitted));
            bukkit.when(() -> Bukkit.getPlayer(fixture.admittedId)).thenReturn(fixture.admitted);
            auth.when(() -> AuthAccess.admitted(fixture.admittedId)).thenReturn(true);
            var module = new TabModule();
            try {
                assertTrue(module.onEnable(fixture.context).toCompletableFuture().join());
                fixture.tasks.removeFirst().run();
                assertEquals(1, fixture.tasks.size());
                acknowledge.set(true);
                fixture.tasks.removeFirst().run();
                assertTrue(fixture.tasks.isEmpty());
                verify(fixture.bridge, times(2)).openUiWithCallback(eq(fixture.admitted), eq("AXS:tab-simple"), any());

                acknowledge.set(false);
                fixture.layout("avatar");
                module.onReload().toCompletableFuture().join();
                for (int attempt = 0; attempt < 3; attempt++) {
                    assertEquals(1, fixture.tasks.size());
                    fixture.tasks.removeFirst().run();
                }
                assertTrue(fixture.tasks.isEmpty());
                verify(fixture.bridge, times(3)).openUiWithCallback(eq(fixture.admitted), eq("AXS:tab-avatar"), any());
            } finally {
                module.onDisable();
            }
        }
    }

    private static final class Fixture {
        private final Path folder;
        private final ModuleContext context = mock(ModuleContext.class);
        private final PacketBridgeAPI bridge = mock(PacketBridgeAPI.class);
        private final Player admitted = mock(Player.class);
        private final UUID admittedId = UUID.randomUUID();
        private final Player restricted = mock(Player.class);
        private final UUID restrictedId = UUID.randomUUID();
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        private Fixture(Path folder, String avatarDefaultOpen) throws Exception {
            this.folder = folder;
            layout("simple");
            var ui = Files.createDirectories(folder.resolve("ui/tab"));
            Files.writeString(ui.resolve("tab-simple.yml"), "ui: {isHud: true}\n");
            Files.writeString(ui.resolve("tab-avatar.yml"), "ui: {isHud: true, defaultOpen: " + avatarDefaultOpen + "}\n");
            var plugin = mock(JavaPlugin.class);
            var scheduler = mock(SchedulerAPI.class);
            when(plugin.isEnabled()).thenReturn(true);
            when(context.plugin()).thenReturn(plugin);
            when(context.dataFolder()).thenReturn(folder.toFile());
            when(context.pluginDataFolder()).thenReturn(folder.toFile());
            when(context.packetBridge()).thenReturn(bridge);
            when(context.expansionRegistry()).thenReturn(mock(PlaceholderExpansionRegistry.class));
            when(context.logger()).thenReturn(Logger.getAnonymousLogger());
            when(context.scheduler()).thenReturn(scheduler);
            when(context.cancelScheduledTasks()).thenReturn(CompletableFuture.completedFuture(null));
            when(admitted.getUniqueId()).thenReturn(admittedId);
            when(restricted.getUniqueId()).thenReturn(restrictedId);
            when(admitted.isOnline()).thenReturn(true);
            when(restricted.isOnline()).thenReturn(true);
            when(scheduler.scheduleOnce(eq(plugin), any(), anyLong())).thenAnswer(call -> {
                tasks.addLast(call.getArgument(1));
                return mock(ScheduledTask.class);
            });
            when(bridge.openUiWithCallback(any(), anyString(), any())).thenAnswer(call -> {
                call.<Runnable>getArgument(2).run();
                return true;
            });
            for (String name : List.of("simple", "avatar")) {
                String id = "AXS:tab-" + name;
                when(bridge.registerOrReloadUi(id, ui.resolve("tab-" + name + ".yml").toFile()))
                    .thenReturn(new PacketBridgeAPI.UiRegistrationResult(true, id, id, "register", ""));
            }
        }

        private void layout(String name) throws Exception {
            Files.writeString(folder.resolve("config.yml"), "settings: {layout: " + name + "}\n");
        }
    }
}
