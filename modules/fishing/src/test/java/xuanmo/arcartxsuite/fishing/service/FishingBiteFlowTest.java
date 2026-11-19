package xuanmo.arcartxsuite.fishing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.model.BaitQueueEntry;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository.BiteProfile;
import xuanmo.arcartxsuite.fishing.talent.TalentEngine;

class FishingBiteFlowTest {
    @TempDir Path directory;

    enum Outcome { CONFIRMED, REJECTED, CANCELLED }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void nativeBiteKeepsVanillaWhileColdAndStartsTheFightOnlyAfterConfirmation(Outcome outcome) throws Exception {
        copy("waters/water-dragon_lake.yml");
        copy("fishes/catfish.yml");
        copy("baits/worm.yml");
        var logger = Logger.getAnonymousLogger();
        var config = FishingModuleConfiguration.load(new YamlConfiguration(), directory.toFile(), logger);
        var repository = mock(FishingRepository.class);
        var bridge = mock(PacketBridgeAPI.class);
        var player = mock(Player.class);
        var world = mock(World.class);
        var inventory = mock(PlayerInventory.class);
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        var callbacks = new LinkedBlockingQueue<Runnable>();
        var read = new CountDownLatch(1);
        var confirm = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var uuid = UUID.randomUUID();
        var hook = UUID.randomUUID();
        var position = new Location(world, 130, 64, 180);
        var auth = new AuthSnapshot(uuid, UUID.randomUUID(), 1, uuid, AuthSnapshot.Registration.REGISTERED,
            AuthSnapshot.Stage.ADMITTED, "test", Set.of(), "");
        var profile = new BiteProfile(new FishingSeasonData(uuid, config.season().seasonId(), 16, 0, 16, 16,
            Map.of(), 0, false), new BaitQueueEntry(uuid, 0, "worm", 2));
        Thread caller = Thread.currentThread();

        when(world.getName()).thenReturn("world");
        when(world.getBiome(anyInt(), anyInt(), anyInt())).thenReturn(Biome.PLAINS);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Tester");
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.AIR));
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            callbacks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        when(bridge.isAvailable()).thenReturn(true);
        when(bridge.openUi(player, "test-ui")).thenReturn(true);
        when(bridge.isUiOpen(player, "test-ui")).thenReturn(true);
        when(repository.loadBiteProfile(uuid, config.season().seasonId())).thenAnswer(call -> {
            assertNotSame(caller, Thread.currentThread());
            read.countDown();
            return profile;
        });
        when(repository.confirmBite(eq(profile), eq(true), any())).thenAnswer(call -> {
            assertNotSame(caller, Thread.currentThread());
            confirm.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            java.util.function.BooleanSupplier stillOpen = call.getArgument(2);
            assertEquals(outcome != Outcome.CANCELLED, stillOpen.getAsBoolean());
            return outcome == Outcome.CONFIRMED;
        });

        var service = new FishingService(plugin, config, repository, bridge, null, logger, "test-ui",
            new TalentEngine(Map.of(), List.of()), null, List.of(), null, null, null, null, null, null);
        service.configureCatchDelivery(mock(PendingRewardService.class), StorageDescriptor.sqlite("test.db"), directory);
        try (var bukkit = mockStatic(Bukkit.class); var access = mockStatic(AuthAccess.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            access.when(() -> AuthAccess.snapshot(uuid)).thenReturn(auth);
            try {
                service.prepareCast(player, hook);
                assertTrue(read.await(2, TimeUnit.SECONDS));
                assertFalse(service.startVanillaBite(player, hook, position));
                verify(bridge, never()).openUi(any(), anyString());
                runNext(callbacks);
                assertTrue(service.startVanillaBite(player, hook, position));
                assertTrue(confirm.await(2, TimeUnit.SECONDS));
                assertFalse(service.getActiveMinigame(uuid).isReady());
                verify(scheduler, never()).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
                if (outcome == Outcome.CANCELLED) service.getActiveMinigame(uuid).giveUp(player);
                release.countDown();
                runNext(callbacks);
                if (outcome == Outcome.CONFIRMED) assertTrue(service.getActiveMinigame(uuid).isReady());
                else {
                    assertNull(service.getActiveMinigame(uuid));
                    verify(bridge).closeUi(player, "test-ui");
                }
                verify(repository, times(1)).loadBiteProfile(uuid, config.season().seasonId());
                verify(repository, times(1)).confirmBite(eq(profile), eq(true), any());
            } finally {
                release.countDown();
                service.shutdown();
                service.databaseStopped().toCompletableFuture().get(3, TimeUnit.SECONDS);
            }
        }
    }

    private void copy(String resource) throws Exception {
        Path path = directory.resolve(resource);
        Files.createDirectories(path.getParent());
        try (var input = getClass().getResourceAsStream("/" + resource)) {
            assertNotNull(input);
            Files.copy(input, path);
        }
    }

    private static void runNext(LinkedBlockingQueue<Runnable> callbacks) throws InterruptedException {
        Runnable callback = callbacks.poll(3, TimeUnit.SECONDS);
        assertNotNull(callback);
        callback.run();
    }
}
