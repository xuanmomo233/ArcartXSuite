package xuanmo.arcartxsuite.eventpacket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.condition.ScriptConditionEvaluator;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderResolverAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;

class LocalRulesTest {
    @Test
    void loadsCategorizedRulesAndRejectsDuplicateIds(@TempDir Path directory) throws Exception {
        Path main = directory.resolve("config.yml");
        Files.writeString(main, "runtime: {work-per-tick: 9}\n");
        Files.writeString(directory.resolve("cleanup.yml"), "enabled: true\ninterval-seconds: 30\n");
        var folder = Files.createDirectory(directory.resolve("rules"));
        Files.writeString(folder.resolve("events.yml"), """
            config-version: 1
            rules:
              local:
                trigger: signal
                signal: demo
                actions: [{type: message, text: hello}]
            """);
        var loaded = RuleConfiguration.load(main.toFile());
        assertEquals(9, loaded.workPerTick());
        assertEquals(600L, loaded.local().cleanup().period());
        assertTrue(loaded.topics().containsKey("eventpacket.signal.demo"));
        Files.writeString(folder.resolve("extra.yaml"), Files.readString(folder.resolve("events.yml")));
        var failure = assertThrows(IllegalArgumentException.class, () -> RuleConfiguration.load(main.toFile()));
        assertTrue(failure.getMessage().contains("rules/extra.yaml"));
        assertTrue(failure.getMessage().contains("rules/events.yml"));
    }

    @Test
    void sharesOnePlaceholderReadAndDetectsOnlyRealChanges() throws Exception {
        var rules = parse("""
            sampling: {interval-ticks: 20, work-per-tick: 1}
            rules:
              race:
                trigger: placeholder-change
                placeholder: "%player_level%"
                match: reach
                threshold: 10
                actions: [{type: message, text: "{new_value}"}]
              rise:
                trigger: placeholder-change
                placeholder: "%player_level%"
                match: increase
                actions: [{type: message, text: "{new_value}"}]
            """);
        assertEquals(1, rules.local().samples().size());
        var player = mock(Player.class);
        var uuid = UUID.randomUUID();
        var session = new AuthSnapshot(uuid, UUID.randomUUID(), 1, null, AuthSnapshot.Registration.UNKNOWN,
            AuthSnapshot.Stage.ADMITTED, "server", Set.of(), "");
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        var resolver = mock(PlaceholderResolverAPI.class);
        when(resolver.available()).thenReturn(true);
        when(resolver.applyPlaceholders(player, "%player_level%")).thenReturn("9", "10", "10");
        var scheduler = mock(SchedulerAPI.class);
        var callback = ArgumentCaptor.forClass(Runnable.class);
        var task = mock(ScheduledTask.class);
        var realtime = mock(RealtimeRules.class);
        var plugin = mock(JavaPlugin.class);
        when(scheduler.scheduleAtFixedRate(eq(plugin), callback.capture(), eq(1L), eq(1L))).thenReturn(task);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            var pluginManager = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            auth.when(() -> AuthAccess.snapshot(uuid)).thenReturn(session);
            try (var inputs = new LocalTriggerRuntime(rules.local(), realtime, resolver,
                    mock(ScriptConditionEvaluator.class), Logger.getAnonymousLogger())) {
                inputs.start(plugin, scheduler);
                callback.getValue().run();
                verifyNoInteractions(realtime);
                for (int tick = 2; tick <= 41; tick++) callback.getValue().run();
                Map<String, String> fields = Map.of("old_value", "9", "new_value", "10", "placeholder", "%player_level%");
                verify(realtime).offer("eventpacket.local.race", session, fields);
                verify(realtime).offer("eventpacket.local.rise", session, fields);
                verifyNoMoreInteractions(realtime);
                verify(resolver, times(3)).applyPlaceholders(player, "%player_level%");
            }
            verify(task).cancel();
        }
    }

    @Test
    void rejectsUnpermissionedClientRulesAndOnlineActionsOnQuit() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
            rules:
              unguarded:
                trigger: client-packet
                actions: [{type: command, executor: console, command: save-all}]
            """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
            rules:
              quit:
                trigger: player-quit
                actions: [{type: ui.packet, ui: "AXS:test", handler: open}]
            """));
    }

    @Test
    void usesOneCheckAcrossMultipleActionsAndCopiesReferencedFields() throws Exception {
        var rules = parse("""
            runtime: {work-per-tick: 1, budget-micros: 5000}
            rules:
              local:
                trigger: signal
                signal: demo
                checks: ["{new_level} > {old_level}"]
                actions:
                  - {type: ui.packet, ui: "AXS:test", handler: update, data: {detail: "{name}"}}
                  - {type: message, text: "更新"}
            """);
        var runtime = new RealtimeRules();
        var callback = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var task = mock(ScheduledTask.class);
        var evaluations = new java.util.concurrent.atomic.AtomicInteger();
        var actions = new java.util.ArrayList<RealtimeRules.Execution>();
        runtime.install(rules, run -> { callback.set(run); return task; }, (rule, event) -> {
            evaluations.incrementAndGet();
            return event.fields().equals(Map.of("new_level", "11", "old_level", "10", "name", "Ada"));
        }, actions::add);
        assertTrue(runtime.offer("eventpacket.signal.demo", null,
            Map.of("new_level", "11", "old_level", "10", "name", "Ada", "unreferenced", "discard")));
        for (int i = 0; i < 4; i++) callback.get().run();
        assertEquals(1, evaluations.get());
        assertEquals(2, actions.size());
        assertEquals("Ada", actions.getFirst().action().data().get("detail").render(actions.getFirst().event().fields()));
        verify(task).cancel();
    }

    @Test
    void cleanupHonorsTickWorkLimitAndSkipsUnloadedAndNamedEntities() throws Exception {
        var settings = parse("""
            cleanup:
              enabled: true
              interval-seconds: 15
              warning-seconds: 0
              result-message: ""
              work-per-tick: 1
              items: true
            """).local().cleanup();
        var world = mock(World.class);
        UUID id = UUID.randomUUID();
        when(world.getUID()).thenReturn(id);
        var unloaded = mock(Chunk.class);
        var loaded = mock(Chunk.class);
        when(world.getLoadedChunks()).thenReturn(new Chunk[]{unloaded, loaded});
        when(loaded.isLoaded()).thenReturn(true);
        when(loaded.isEntitiesLoaded()).thenReturn(true);
        var item = mock(Item.class);
        var named = mock(Item.class);
        when(item.getType()).thenReturn(EntityType.DROPPED_ITEM);
        when(item.isValid()).thenReturn(true);
        when(named.isValid()).thenReturn(true);
        when(named.getCustomName()).thenReturn("保留");
        when(loaded.getEntities()).thenReturn(new Entity[]{item, named});
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(() -> Bukkit.getWorld(id)).thenReturn(world);
            var cleanup = new EntityCleanup(settings);
            for (int tick = 300; tick < 303; tick++) cleanup.tick(tick);
            verify(item, never()).remove();
            for (int tick = 303; tick < 335; tick++) cleanup.tick(tick);
            verify(unloaded, never()).getEntities();
            verify(item).remove();
            verify(named, never()).remove();
        }
    }

    @Test
    void manualCleanupRunsThroughTheTickQueueAndReturnsItsCount() throws Exception {
        var settings = parse("""
            cleanup:
              enabled: true
              interval-seconds: 15
              warning-seconds: 5
              result-message: ""
              work-per-tick: 1
            """).local().cleanup();
        var world = mock(World.class);
        var worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        var chunk = mock(Chunk.class);
        when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
        when(chunk.isLoaded()).thenReturn(true);
        when(chunk.isEntitiesLoaded()).thenReturn(true);
        var item = mock(Item.class);
        when(item.getType()).thenReturn(EntityType.DROPPED_ITEM);
        when(item.isValid()).thenReturn(true);
        when(chunk.getEntities()).thenReturn(new Entity[]{item});
        var completed = new java.util.ArrayList<Integer>();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            var cleanup = new EntityCleanup(settings);
            assertTrue(cleanup.request(10, completed::add));
            assertFalse(cleanup.request(10, completed::add));
            verify(item, never()).remove();
            cleanup.tick(11);
            assertFalse(cleanup.request(11, completed::add));
            verify(item, never()).remove();
            for (int tick = 12; tick <= 50; tick++) cleanup.tick(tick);
            assertEquals(List.of(1), completed);
            verify(item).remove();
            bukkit.verify(() -> Bukkit.broadcastMessage(anyString()), never());
        }
    }

    @Test
    void killCountResumesFromPlayerDataAndFiresOnce() throws Exception {
        var rules = parse("""
            rules:
              milestone:
                trigger: mob-kills
                entity-type: ZOMBIE
                count: 2
                worlds: [test_world]
                actions: [{type: message, text: "已完成"}]
            """);
        var id = UUID.randomUUID();
        var session = new AuthSnapshot(id, UUID.randomUUID(), 1, null, AuthSnapshot.Registration.UNKNOWN,
            AuthSnapshot.Stage.ADMITTED, "server", Set.of(), "");
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        var container = mock(PersistentDataContainer.class);
        when(player.getPersistentDataContainer()).thenReturn(container);
        var count = new java.util.concurrent.atomic.AtomicInteger(1);
        when(container.getOrDefault(any(NamespacedKey.class), eq(PersistentDataType.INTEGER), eq(0)))
            .thenAnswer(ignored -> count.get());
        doAnswer(call -> {
            count.set(call.getArgument(2));
            return null;
        }).when(container).set(any(NamespacedKey.class), eq(PersistentDataType.INTEGER), anyInt());
        var entity = mock(LivingEntity.class);
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.getKiller()).thenReturn(player);
        var world = mock(World.class);
        when(world.getName()).thenReturn("test_world");
        when(entity.getWorld()).thenReturn(world);
        var death = mock(EntityDeathEvent.class);
        when(death.getEntity()).thenReturn(entity);
        var plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("ArcartX-Suite");
        var realtime = mock(RealtimeRules.class);
        var payload = Map.of("world", "test_world", "entity_type", "ZOMBIE", "mythic_mob", "", "kill_count", "2");
        when(realtime.offer("eventpacket.local.milestone", session, payload)).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var auth = mockStatic(AuthAccess.class)) {
            var manager = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            auth.when(() -> AuthAccess.snapshot(id)).thenReturn(session);
            try (var inputs = new LocalTriggerRuntime(rules.local(), realtime, mock(PlaceholderResolverAPI.class),
                    mock(ScriptConditionEvaluator.class), Logger.getAnonymousLogger())) {
                inputs.start(plugin, mock(SchedulerAPI.class));
                inputs.death(death);
                inputs.death(death);
                verify(realtime).offer("eventpacket.local.milestone", session, payload);
                assertEquals(2, count.get());
            }
        }
    }

    private static RuleConfiguration parse(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return RuleConfiguration.parse(yaml);
    }
}
