package xuanmo.arcartxsuite.bridge;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModerationInitializationTest {
    @TempDir Path directory;
    record Pulse(Runnable action, BukkitTask handle, long period) {}
    static final class Fixture implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final JavaPlugin plugin = mock(JavaPlugin.class);
        final Server server = mock(Server.class);
        final PluginManager manager = mock(PluginManager.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final List<Pulse> periodic = new ArrayList<>();
        final List<Runnable> forced = new ArrayList<>();
        final ContentModeratorImpl moderator;
        Fixture() {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(mock(ConsoleCommandSender.class));
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(manager);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
                var task = mock(BukkitTask.class); periodic.add(new Pulse(call.getArgument(1), task, call.getArgument(3))); return task;
            });
            when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                forced.add(call.getArgument(1)); return mock(BukkitTask.class);
            });
            moderator = new ContentModeratorImpl(plugin);
        }
        @Override public void close() { moderator.shutdown(); bukkit.close(); }
    }

    private static ConfigurationSection config() {
        var section = new YamlConfiguration().createSection("moderation");
        section.set("enabled", true); section.set("replacement", "*");
        section.set("blocked-words", List.of("spam")); return section;
    }
    private static Object state(ContentModeratorImpl value) throws Exception {
        var field = ContentModeratorImpl.class.getDeclaredField("state"); field.setAccessible(true); return field.get(value);
    }
    private static Object component(Object value, String name) throws Exception {
        var method = value.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(value);
    }
    private ConfigurationSection cloud(Path words) {
        var section = config(); section.set("cloud.enabled", true);
        section.set("cloud.urls", List.of(words.toUri().toString())); return section;
    }

    @Test void repeatedInitializationReusesRulesAndDoesNotReadTheConfigFileAgain() throws Exception {
        try (var f = new Fixture()) {
            var section = config(); f.moderator.initialize(section); Object before = state(f.moderator);
            for (int i = 0; i < 30; i++) f.moderator.initialize(section);
            assertSame(before, state(f.moderator)); assertEquals("*", f.moderator.filter("SPAM").text());
            assertTrue(f.periodic.isEmpty()); verify(f.plugin, never()).getDataFolder();
        }
    }

    @Test void localChangesDoNotRestartCloudRefreshOrDiscardDownloadedWords() throws Exception {
        Path words = Files.writeString(directory.resolve("words.txt"), "remote");
        try (var f = new Fixture()) {
            var section = cloud(words); f.moderator.initialize(section); f.periodic.get(0).action().run();
            Object before = state(f.moderator); assertEquals("*", f.moderator.filter("remote").text());
            section.set("blocked-words", List.of("changed")); f.moderator.initialize(section);
            Object after = state(f.moderator);
            assertNotSame(component(before, "rules"), component(after, "rules"));
            assertSame(component(before, "cloud"), component(after, "cloud"));
            assertEquals(1, f.periodic.size()); verify(f.periodic.get(0).handle(), never()).cancel();
            assertEquals("*", f.moderator.filter("changed").text()); assertEquals("*", f.moderator.filter("remote").text());
        }
    }

    @Test void periodChangesReuseTheDictionaryAndOnlyReplaceTheTimer() throws Exception {
        Path words = Files.writeString(directory.resolve("period.txt"), "remote");
        try (var f = new Fixture()) {
            var section = cloud(words); f.moderator.initialize(section); Object before = state(f.moderator);
            section.set("cloud.refresh-minutes", 2); f.moderator.initialize(section);
            assertSame(component(before, "rules"), component(state(f.moderator), "rules"));
            assertSame(component(before, "cloud"), component(state(f.moderator), "cloud"));
            assertEquals(2, f.periodic.size()); assertEquals(2400, f.periodic.get(1).period()); verify(f.periodic.get(0).handle()).cancel();
        }
    }

    @Test void explicitReloadRefreshesWordsWithoutRebuildingRulesOrThePeriodicTask() throws Exception {
        Path words = Files.writeString(directory.resolve("manual.txt"), "oldword");
        try (var f = new Fixture()) {
            var section = cloud(words); f.moderator.initialize(section); f.periodic.get(0).action().run();
            Object before = state(f.moderator); Files.writeString(words, "freshword");
            f.moderator.initialize(section); assertEquals("*", f.moderator.filter("oldword").text());
            f.moderator.reload(section); assertSame(before, state(f.moderator));
            assertEquals(1, f.periodic.size()); assertEquals(1, f.forced.size()); f.forced.get(0).run();
            assertEquals("*", f.moderator.filter("freshword").text()); assertEquals("oldword", f.moderator.filter("oldword").text());
        }
    }

    @Test void disableAndShutdownCancelTasksAndQueuedOldRefreshDoesNoWork() throws Exception {
        Path words = Files.writeString(directory.resolve("cancel.txt"), "remote");
        try (var f = new Fixture()) {
            var section = cloud(words); f.moderator.initialize(section);
            var oldCloud = (ModerationCloudWords) component(state(f.moderator), "cloud"); Object matcher = oldCloud.matcher();
            f.moderator.reload(section); f.moderator.shutdown();
            f.periodic.get(0).action().run(); f.forced.get(0).run();
            assertSame(matcher, oldCloud.matcher()); assertFalse(f.moderator.isAvailable()); verify(f.periodic.get(0).handle()).cancel();
            section.set("enabled", false); f.moderator.initialize(section); assertEquals("spam", f.moderator.filter("spam").text());
        }
    }

    @Test void ignoredCloudWordsAndRemovedSourcesApplyImmediately() throws Exception {
        Path words = Files.writeString(directory.resolve("ignored.txt"), "remote");
        try (var f = new Fixture()) {
            var section = cloud(words); f.moderator.initialize(section); f.periodic.get(0).action().run();
            section.set("cloud.ignored", List.of("REMOTE")); f.moderator.initialize(section);
            assertEquals("remote", f.moderator.filter("remote").text());
            section.set("cloud.enabled", false); f.moderator.initialize(section);
            assertEquals("remote", f.moderator.filter("remote").text()); assertEquals("*", f.moderator.filter("spam").text());
        }
    }

    @Test void externalPluginReplacementIsNotHiddenByTheSettingsCache() throws Exception {
        try (var f = new Fixture(); var redact = mockStatic(RedactModeration.class)) {
            Plugin first = mock(Plugin.class), next = mock(Plugin.class);
            when(first.isEnabled()).thenReturn(true); when(next.isEnabled()).thenReturn(true);
            when(f.manager.getPlugin("Redact")).thenReturn(first);
            var adapter = mock(RedactModeration.class);
            redact.when(() -> RedactModeration.connect(eq(f.plugin), any(Logger.class))).thenReturn(adapter);
            var section = config(); section.set("redact.enabled", true);
            f.moderator.initialize(section); Object before = state(f.moderator);
            f.moderator.initialize(section); assertSame(before, state(f.moderator));
            redact.verify(() -> RedactModeration.connect(eq(f.plugin), any(Logger.class)), times(1));
            when(f.manager.getPlugin("Redact")).thenReturn(next); f.moderator.initialize(section);
            assertNotSame(before, state(f.moderator));
            redact.verify(() -> RedactModeration.connect(eq(f.plugin), any(Logger.class)), times(2));
        }
    }
}
