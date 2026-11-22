package xuanmo.arcartxsuite.combateffect;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.jar.JarFile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import xuanmo.arcartxsuite.api.ModuleContext;
import xuanmo.arcartxsuite.api.bridge.ChronosAccess;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.AbyssQueryable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.placeholder.PlaceholderExpansionRegistry;
import xuanmo.arcartxsuite.combateffect.chronos.SkillEffectService;
import xuanmo.arcartxsuite.combateffect.deathbuffer.DeathBufferService;
import xuanmo.arcartxsuite.module.SimpleEventBus;

class CombatEffectLifecycleTest {
    private static final String CHRONOS_API = "priv.seventeen.artist.arcartx.chronos.api.";
    private static final String ABYSS_DEATH = "axs.abyss.player_death";

    @TempDir Path folder;
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final PluginManager plugins = mock(PluginManager.class);
    private final JavaPlugin host = mock(JavaPlugin.class);
    private final PacketBridgeAPI packets = mock(PacketBridgeAPI.class);
    private final AbyssQueryable abyss = mock(AbyssQueryable.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final BukkitTask timeout = mock(BukkitTask.class);
    private final SimpleEventBus events = new SimpleEventBus(logger);
    private final CombatEffectModule module = new CombatEffectModule();
    private final ModuleContext context = mock(ModuleContext.class);
    private final Player player = mock(Player.class);
    private final AtomicBoolean invulnerable = new AtomicBoolean();
    private final AtomicReference<Float> walk = new AtomicReference<>(0.37f);
    private final AtomicReference<Float> fly = new AtomicReference<>(0.16f);
    private final ArgumentCaptor<Listener> listeners = ArgumentCaptor.forClass(Listener.class);
    private final ArgumentCaptor<Runnable> delayed = ArgumentCaptor.forClass(Runnable.class);
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getServicesManager).thenReturn(mock(ServicesManager.class));
        when(host.isEnabled()).thenReturn(true);
        when(scheduler.runTaskLater(eq(host), delayed.capture(), anyLong())).thenReturn(timeout);
        when(context.dataFolder()).thenReturn(folder.toFile());
        when(context.pluginDataFolder()).thenReturn(folder.toFile());
        when(context.plugin()).thenReturn(host);
        when(context.logger()).thenReturn(logger);
        when(context.expansionRegistry()).thenReturn(mock(PlaceholderExpansionRegistry.class));
        when(context.packetBridge()).thenReturn(packets);
        when(context.getCapability(AbyssQueryable.class)).thenReturn(abyss);
        when(context.getCapability(EventBusCapability.class)).thenReturn(events);
        Files.writeString(folder.resolve("ui.yml"), "ui: {packetHandler: {combo_sync: 'var.count = packet'}}\n");
        when(context.exportUiResource(anyString(), anyString(), anyBoolean(), any()))
            .thenReturn(folder.resolve("ui.yml").toFile());
        when(packets.isAvailable()).thenReturn(true);
        when(packets.registerOrReloadUi(anyString(), any())).thenAnswer(call -> {
            String id = call.getArgument(0);
            return new PacketBridgeAPI.UiRegistrationResult(true, id, id, "register", "");
        });
        when(packets.sendPacket(any(), anyString(), anyString(), any())).thenReturn(true);
        doNothing().when(plugins).registerEvents(listeners.capture(), eq(host));
        doAnswer(call -> {
            Class<?> type = call.getArgument(0);
            HandlerList handlers = (HandlerList) type.getMethod("getHandlerList").invoke(null);
            handlers.register(new RegisteredListener(call.getArgument(1), call.getArgument(3),
                call.getArgument(2), host, call.getArgument(5)));
            return null;
        }).when(plugins).registerEvent(any(), any(), any(EventPriority.class), any(EventExecutor.class), eq(host), anyBoolean());

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.AIR));
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getName()).thenReturn("Tester");
        when(player.getType()).thenReturn(EntityType.PLAYER);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getHealth()).thenReturn(20.0);
        when(player.isInvulnerable()).thenAnswer(call -> invulnerable.get());
        doAnswer(call -> { invulnerable.set(call.getArgument(0)); return null; }).when(player).setInvulnerable(anyBoolean());
        when(player.getWalkSpeed()).thenAnswer(call -> walk.get());
        doAnswer(call -> { walk.set(call.getArgument(0)); return null; }).when(player).setWalkSpeed(anyFloat());
        when(player.getFlySpeed()).thenAnswer(call -> fly.get());
        doAnswer(call -> { fly.set(call.getArgument(0)); return null; }).when(player).setFlySpeed(anyFloat());
        Files.writeString(folder.resolve("config.yml"), """
            kill-effect:
              settings: {}
            death-buffer:
              enabled: true
              abyss-packet-id: abyss-down
            state-trigger:
              enabled: true
            """);
        Files.createDirectory(folder.resolve("packets"));
        Files.writeString(folder.resolve("packets/test.yml"), """
            death-buffer:
              trigger: death
              ui-id: 'AXS:death_buffer'
              packet-handler: death
              recipients: [target]
              pack: {victim: '{target}'}
            abyss-down:
              trigger: manual
              ui-id: 'AXS:abyss_feedback'
              packet-handler: down
              pack: {title: '{dungeon} #{deaths}'}
            chronos-state:
              trigger: state
              ui-id: 'AXS:chronos_test'
              packet-handler: state
              conditions: {state-id: attack}
              pack: {state: '{state_id}'}
            """);
    }

    @AfterEach
    void tearDown() {
        if (module.isReady()) module.onDisable();
        if (bukkit != null) bukkit.close();
    }

    @Test
    void reloadRestoresPendingBufferAndCancelsItsTask() throws Exception {
        assertEquals(ChronosAccess.Status.MISSING, ChronosAccess.probe().status());
        assertTrue(module.onEnable(context).toCompletableFuture().join());
        var hit = lethalHit();
        buffer().onDamage(hit);
        assertTrue(hit.isCancelled());
        assertTrue(invulnerable.get());
        assertEquals(0f, walk.get());
        assertEquals(0f, fly.get());

        module.onReload().toCompletableFuture().join();
        assertFalse(invulnerable.get());
        assertEquals(0.37f, walk.get());
        assertEquals(0.16f, fly.get());
        verify(timeout).cancel();
        verify(packets).closeUi(player, DeathBufferService.BUFFER_UI_ID);
        delayed.getValue().run();
        verify(player, never()).setHealth(anyDouble());
        assertFalse(buffer().isInBuffer(player));
    }

    @Test
    void quitSettlesOnlyOnceAndPreservesOriginalProtection() throws Exception {
        assertTrue(module.onEnable(context).toCompletableFuture().join());
        invulnerable.set(true);
        var hit = lethalHit();
        var service = buffer();
        service.onDamage(hit);
        service.onQuit(new PlayerQuitEvent(player, ""));
        delayed.getValue().run();
        assertTrue(invulnerable.get());
        assertEquals(0.37f, walk.get());
        assertEquals(0.16f, fly.get());
        verify(player).setLastDamageCause(hit);
        verify(player).setHealth(0);
    }

    @Test
    void abyssKeepsDeathOwnershipAndReloadDoesNotDuplicateDownedPackets() throws Exception {
        assertTrue(module.onEnable(context).toCompletableFuture().join());
        when(abyss.isInDungeon(player.getUniqueId())).thenReturn(true);
        var hit = lethalHit();
        buffer().onDamage(hit);
        assertFalse(hit.isCancelled());
        verify(scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
        module.onReload().toCompletableFuture().join();
        events.publish(ABYSS_DEATH, player, (Object) Map.of("dungeon", "vault", "deaths", 2));
        verify(packets).sendPacket(player, "AXS:abyss_feedback", "down", Map.of("title", "vault #2"));
        verify(player, never()).setHealth(anyDouble());
        module.onDisable();
        events.publish(ABYSS_DEATH, player, (Object) Map.of("dungeon", "vault", "deaths", 2));
        verify(packets, times(1)).sendPacket(eq(player), eq("AXS:abyss_feedback"), eq("down"), any());
    }

    @Test
    void totemIsLeftToVanilla() throws Exception {
        assertTrue(module.onEnable(context).toCompletableFuture().join());
        when(player.getInventory().getItemInOffHand()).thenReturn(new ItemStack(Material.TOTEM_OF_UNDYING));
        var hit = lethalHit();
        buffer().onDamage(hit);
        assertFalse(hit.isCancelled());
        assertFalse(buffer().isInBuffer(player));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AXS_CHRONOS_TEST_JAR", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "AXS_CHRONOS_ARIA_JAR", matches = ".+")
    void realChronosApiStateEventsAndSkillEffectsSurviveModuleReload() throws Exception {
        verifyChronosJar(Path.of(System.getenv("AXS_CHRONOS_TEST_JAR")));
    }

    private void verifyChronosJar(Path jar) throws Exception {
        PluginDescriptionFile description;
        try (JarFile archive = new JarFile(jar.toFile());
             var stream = archive.getInputStream(archive.getJarEntry("plugin.yml"))) {
            description = new PluginDescriptionFile(stream);
        }
        var aria = Path.of(System.getenv("AXS_CHRONOS_ARIA_JAR")).toUri().toURL();
        try (var loader = new URLClassLoader(new URL[] {jar.toUri().toURL(), aria}, getClass().getClassLoader())) {
            var enabled = new AtomicBoolean();
            Plugin chronos = (Plugin) Proxy.newProxyInstance(loader, new Class<?>[] {Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDescription" -> description;
                    case "getName" -> description.getName();
                    case "isEnabled" -> enabled.get();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            when(plugins.getPlugin("Chronos")).thenReturn(chronos);
            assertEquals(ChronosAccess.Status.DISABLED, ChronosAccess.probe().status());
            enabled.set(true);
            assertEquals(ChronosAccess.Status.API_UNAVAILABLE, ChronosAccess.probe().status());
            Class<?> api = loader.loadClass(CHRONOS_API + "ChronosAPI");
            var receivedDuration = new AtomicReference<Double>();
            var supported = new AtomicBoolean();
            var executionHandler = new AtomicReference<Object>();
            var closes = new AtomicInteger();
            Object implementation = Proxy.newProxyInstance(loader, new Class<?>[] {api}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "getCapabilities" -> supported.get()
                        ? Set.of("chronos:action-instance/1", "chronos:execute-loop/1") : Set.of();
                    case "getPlayerStateId" -> "attack";
                    case "getPlayerControllerId" -> "sword";
                    case "tryEnterControlledState" -> { receivedDuration.set((Double) args[2]); yield null; }
                    case "registerExecutionHandler" -> {
                        assertSame(host, args[0]);
                        assertEquals("axs:play_effect", args[1]);
                        Object handler = args[2];
                        assertTrue(executionHandler.compareAndSet(null, handler), "previous handler must be closed");
                        Class<?> registration = method.getReturnType();
                        yield Proxy.newProxyInstance(loader, new Class<?>[]{registration}, (handle, action, arguments) ->
                            switch (action.getName()) {
                                case "isActive" -> executionHandler.get() == handler;
                                case "close" -> {
                                    if (executionHandler.compareAndSet(handler, null)) closes.incrementAndGet();
                                    yield null;
                                }
                                default -> throw new UnsupportedOperationException(action.getName());
                            });
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            var publishApi = loader.loadClass(CHRONOS_API + "ChronosPlugin").getMethod("setChronosAPI", api);
            publishApi.invoke(null, implementation);
            try {
                assertEquals(ChronosAccess.Status.UNSUPPORTED, ChronosAccess.probe().status());
                supported.set(true);
                var probe = ChronosAccess.probe();
                assertEquals(ChronosAccess.Status.READY, probe.status(), description.getVersion());
                assertEquals("attack", probe.access().stateId(player));
                probe.access().enterControlledState(player, "death", 1750);
                assertEquals(1750.0, receivedDuration.get());
                assertTrue(module.onEnable(context).toCompletableFuture().join());
                module.onReload().toCompletableFuture().join();
                Class<?> type = loader.loadClass(CHRONOS_API + "event.PlayerEnterStateEvent");
                Event event = (Event) type.getConstructor(Player.class, String.class).newInstance(player, "attack");
                var handlers = event.getHandlers().getRegisteredListeners();
                assertEquals(1, handlers.length, description.getVersion());
                handlers[0].callEvent(event);
                verify(packets).sendPacket(player, "AXS:chronos_test", "state", Map.of("state", "attack"));
                module.onDisable();
                assertEquals(0, event.getHandlers().getRegisteredListeners().length);
                clearInvocations(packets);
                verifySkillActions(loader, executionHandler, closes);
            } finally {
                if (module.isReady()) module.onDisable();
                publishApi.invoke(null, (Object) null);
            }
        }
    }

    private void verifySkillActions(ClassLoader loader, AtomicReference<Object> handler, AtomicInteger closes) throws Exception {
        Files.writeString(folder.resolve("config.yml"), """
            kill-effect:
              settings: {}
            skill-effects:
              enabled: true
              rules:
                charge:
                  skill: example:charge
                  profession: example:warrior
                  slot: primary
                  hud-text: 'Charging {skill_id}'
                  start-packets: [start]
                  cleanup-packets: [cleanup]
            """);
        Files.writeString(folder.resolve("packets/skill.yml"), """
            start:
              trigger: manual
              ui-id: 'AXS:skill_start_test'
              packet-handler: start
              pack: {slot: '{slot_id}'}
            cleanup:
              trigger: manual
              ui-id: 'AXS:skill_start_test'
              packet-handler: cleanup
              pack: {reason: '{end_reason}'}
            pulse:
              trigger: manual
              ui-id: 'AXS:skill_pulse_test'
              packet-handler: pulse
              pack: {state: '{state_id}', step: '{execution_index}'}
            """);
        List<Runnable> mounts = new ArrayList<>();
        List<BukkitTask> mountTasks = new ArrayList<>();
        doAnswer(call -> {
            mounts.add(call.getArgument(1));
            BukkitTask task = mock(BukkitTask.class);
            mountTasks.add(task);
            return task;
        })
            .when(scheduler).runTask(eq(host), any(Runnable.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        when(packets.openUi(eq(player), anyString())).thenReturn(true);
        when(packets.closeUi(eq(player), anyString())).thenReturn(true);
        assertTrue(module.onEnable(context).toCompletableFuture().join());
        Object previousHandler = handler.get();
        assertNotNull(previousHandler);
        Event entry = stateEvent(loader, "PlayerEnterStateEvent", "attack");

        UUID wrong = UUID.randomUUID();
        fire(actionEvent(loader, wrong, "secondary", null));
        fire(entry);
        assertTrue(mounts.isEmpty(), "all selectors must match");
        fire(actionEvent(loader, wrong, "secondary", "END"));

        UUID rejected = UUID.randomUUID();
        fire(actionEvent(loader, rejected, "primary", null));
        fire(actionEvent(loader, rejected, "primary", "ENTRY_FAILED"));
        fire(entry);
        assertTrue(mounts.isEmpty(), "failed entry must not show a skill effect");

        UUID interrupted = UUID.randomUUID();
        fire(actionEvent(loader, interrupted, "primary", null));
        assertTrue(mounts.isEmpty(), "StartEvent precedes entry");
        fire(entry);
        assertEquals(2, mounts.size());
        fire(actionEvent(loader, interrupted, "primary", "INTERRUPTED"));
        mountTasks.forEach(task -> verify(task).cancel());
        flushMounts(mounts); // simulate tasks already dequeued when cancellation happened
        verify(packets, never()).sendPacket(eq(player), anyString(), anyString(), any());
        verify(packets).closeUi(player, SkillEffectService.HUD_ID);
        verify(packets).closeUi(player, "AXS:skill_start_test");
        clearInvocations(packets);

        UUID active = UUID.randomUUID();
        fire(actionEvent(loader, active, "primary", null));
        fire(entry);
        flushMounts(mounts);
        verify(packets).sendPacket(player, SkillEffectService.HUD_ID, "show", Map.of("text", "Charging example:charge"));
        verify(packets).sendPacket(player, "AXS:skill_start_test", "start", Map.of("slot", "primary"));
        UUID state = UUID.randomUUID();
        assertEquals("CONTINUE", execute(loader, previousHandler, active, state, 0));
        assertEquals("CONTINUE", execute(loader, previousHandler, active, state, 0));
        flushMounts(mounts);
        verify(packets).sendPacket(player, "AXS:skill_pulse_test", "pulse", Map.of("state", "attack", "step", "0"));
        fire(actionEvent(loader, interrupted, "primary", "LATE"));
        verify(packets, never()).closeUi(eq(player), anyString());
        fire(actionEvent(loader, active, "primary", "INTERRUPTED"));
        verify(packets).sendPacket(player, "AXS:skill_start_test", "cleanup", Map.of("reason", "INTERRUPTED"));
        verify(packets).closeUi(player, SkillEffectService.HUD_ID);
        verify(packets).closeUi(player, "AXS:skill_pulse_test");
        assertEquals("CANCEL_THIS_LOOP", execute(loader, previousHandler, active, state, 1));

        UUID reloading = UUID.randomUUID();
        clearInvocations(packets);
        fire(actionEvent(loader, reloading, "primary", null));
        fire(entry);
        flushMounts(mounts);
        assertEquals("CONTINUE", execute(loader, previousHandler, reloading, UUID.randomUUID(), 0));
        BukkitTask pendingOnReload = mountTasks.get(mountTasks.size() - 1);
        module.onReload().toCompletableFuture().join();
        verify(pendingOnReload).cancel();
        flushMounts(mounts);
        assertEquals(1, closes.get());
        assertNotSame(previousHandler, handler.get());
        assertEquals(1, entry.getHandlers().getRegisteredListeners().length);
        verify(packets).sendPacket(player, "AXS:skill_start_test", "cleanup", Map.of("reason", "shutdown"));
        verify(packets).closeUi(player, SkillEffectService.HUD_ID);
        verify(packets).closeUi(player, "AXS:skill_pulse_test");
        verify(packets, never()).sendPacket(eq(player), eq("AXS:skill_pulse_test"), anyString(), any());
        assertEquals("CANCEL_THIS_LOOP", execute(loader, previousHandler, reloading, UUID.randomUUID(), 1));
        assertEquals("CANCEL_THIS_LOOP", execute(loader, handler.get(), reloading, UUID.randomUUID(), 1));
        module.onDisable();
        assertEquals(2, closes.get());
        assertNull(handler.get());
        assertEquals(0, entry.getHandlers().getRegisteredListeners().length);
    }

    private Event actionEvent(ClassLoader loader, UUID id, String slot, String reason) throws Exception {
        String type = reason == null ? "PlayerSkillActionStartEvent" : "PlayerSkillActionEndEvent";
        Class<?> event = loader.loadClass(CHRONOS_API + "event." + type);
        if (reason == null) {
            return (Event) event.getConstructor(Player.class, UUID.class, String.class, String.class, String.class, int.class)
                .newInstance(player, id, "example:warrior", slot, "example:charge", 2);
        }
        return (Event) event.getConstructor(Player.class, UUID.class, String.class, String.class, String.class, int.class, String.class)
            .newInstance(player, id, "example:warrior", slot, "example:charge", 2, reason);
    }

    private Event stateEvent(ClassLoader loader, String type, String state) throws Exception {
        return (Event) loader.loadClass(CHRONOS_API + "event." + type)
            .getConstructor(Player.class, String.class).newInstance(player, state);
    }

    private String execute(ClassLoader loader, Object handler, UUID id, UUID state, int index) throws Exception {
        Class<?> type = loader.loadClass(CHRONOS_API + "execution.ExecutionContext");
        Object execution = type.getConstructors()[0].newInstance(player, "pulse", index, 2, 100.0, false,
            state, "attack", "main", "chain", id, "example:warrior", 1, "example:charge", 2, null,
            Map.of("packet-id", "pulse"));
        return loader.loadClass(CHRONOS_API + "execution.ExecutionHandler").getMethod("execute", type)
            .invoke(handler, execution).toString();
    }

    private static void fire(Event event) throws Exception {
        for (var listener : event.getHandlers().getRegisteredListeners()) listener.callEvent(event);
    }

    private static void flushMounts(List<Runnable> mounts) {
        for (Runnable mount : List.copyOf(mounts)) mount.run();
        mounts.clear();
    }

    private EntityDamageEvent lethalHit() {
        return new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FIRE, 25);
    }

    private DeathBufferService buffer() {
        return listeners.getAllValues().stream().filter(DeathBufferService.class::isInstance)
            .map(DeathBufferService.class::cast).reduce((first, last) -> last).orElseThrow();
    }
}
