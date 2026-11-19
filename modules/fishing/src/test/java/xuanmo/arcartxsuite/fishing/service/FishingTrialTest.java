package xuanmo.arcartxsuite.fishing.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.fishing.command.FishingAdminCommand;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.model.FishingSeasonData;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.talent.TalentEngine;

class FishingTrialTest {
    @TempDir Path dataDir;

    @Test
    void adminCommandStartsTheChosenSizeAndObstacleWithoutSettlingRewards() throws Exception {
        copyExample("fishes/legendary_carp.yml");
        copyExample("waters/water-dragon_lake.yml");
        var logger = Logger.getLogger(getClass().getName());
        var config = FishingModuleConfiguration.load(new YamlConfiguration(), dataDir.toFile(), logger);
        var uuid = UUID.randomUUID();
        var messages = new ArrayList<String>();
        var allowed = new AtomicBoolean();
        var inventory = proxy(PlayerInventory.class, (ignored, method, args) -> switch (method.getName()) {
            case "getItemInMainHand", "getItemInOffHand" -> new ItemStack(Material.AIR);
            default -> throw new AssertionError(method);
        });
        var player = proxy(Player.class, (ignored, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> uuid;
            case "getInventory" -> inventory;
            case "hasPermission" -> "axs.fishing.admin".equals(args[0]) && allowed.get();
            case "sendMessage" -> {
                messages.add((String) args[0]);
                yield null;
            }
            default -> throw new AssertionError(method);
        });
        // Reject any unexpected repository access, including all consumption and settlement writes.
        var repository = proxy(FishingRepository.class, (ignored, method, args) -> switch (method.getName()) {
            case "loadSeasonData" -> FishingSeasonData.empty(uuid, (String) args[1]);
            case "loadBaitQueue" -> List.of();
            default -> throw new AssertionError(method);
        });
        var bridge = proxy(PacketBridgeAPI.class, (ignored, method, args) -> switch (method.getName()) {
            case "isAvailable", "openUi", "closeUi" -> true;
            default -> throw new AssertionError(method);
        });
        var service = new FishingService(null, config, repository, bridge, null, logger, "test-ui",
            new TalentEngine(Map.of(), List.of()), null, List.of(), null, null, null, null, null, null);
        var command = new FishingAdminCommand(() -> service, (key, args) -> key, () -> null, dataDir.toFile(), () -> {});
        String[] reeds = {"fishing", "test", "legendary_carp", "80", "reeds"};

        assertTrue(command.onCommand(player, "axs", reeds));
        assertEquals(List.of("command_no_permission"), messages);
        assertFalse(service.isPlayerInMinigame(uuid));
        assertEquals(List.of(), command.onTabComplete(player, new String[]{"fishing", "test", ""}));
        allowed.set(true);
        assertEquals(List.of("80", "250"), command.onTabComplete(player, new String[]{"fishing", "test", "legendary_carp", ""}));
        assertEquals(List.of("rapids"), command.onTabComplete(player,
            new String[]{"fishing", "test", "legendary_carp", "80", "reeds", "ra"}));
        assertEquals(List.of("personality=alert"), command.onTabComplete(player,
            new String[]{"fishing", "test", "legendary_carp", "80", "reeds", "rapids", "personality=a"}));
        assertEquals(List.of("hook-loop"), command.onTabComplete(player,
            new String[]{"fishing", "test", "legendary_carp", "80", "reeds", "hook-l"}));
        assertTrue(command.onCommand(player, "axs", new String[]{"fishing", "test", "legendary_carp", "251", "clear"}));
        assertFalse(service.isPlayerInMinigame(uuid));

        var cancelled = new AtomicBoolean();
        var task = proxy(BukkitTask.class, (ignored, method, args) -> switch (method.getName()) {
            case "getTaskId" -> 1;
            case "isCancelled" -> cancelled.get();
            case "cancel" -> {
                cancelled.set(true);
                yield null;
            }
            default -> throw new AssertionError(method);
        });
        var scheduler = proxy(BukkitScheduler.class, (ignored, method, args) -> task);
        var server = proxy(Server.class, (ignored, method, args) -> switch (method.getName()) {
            case "getPlayer" -> player;
            case "getScheduler" -> scheduler;
            default -> throw new AssertionError(method);
        });
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        serverField.set(null, server);
        try {
            assertTrue(command.onCommand(player, "axs", reeds));
            var small = service.getActiveMinigame(uuid).getSession();
            assertEquals("legendary_carp", small.fish().id());
            assertEquals(80, small.caughtSize());
            assertEquals("reeds", small.obstacle().id());
            assertNull(small.current());
            assertEquals("normal", small.opening().personality().id());
            assertNull(small.opening().hookSet());
            assertEquals(0, small.playerLevel());
            service.onMinigameComplete(small, true, true, true);
            assertFalse(service.isPlayerInMinigame(uuid));

            assertTrue(command.onCommand(player, "axs",
                new String[]{"fishing", "test", "legendary_carp", "250", "clear", "unknown"}));
            assertFalse(service.isPlayerInMinigame(uuid));
            assertTrue(command.onCommand(player, "axs",
                new String[]{"fishing", "test", "legendary_carp", "250", "clear", "personality=unknown", "hook"}));
            assertFalse(service.isPlayerInMinigame(uuid));
            assertTrue(command.onCommand(player, "axs",
                new String[]{"fishing", "test", "legendary_carp", "250", "clear", "rapids", "personality=alert", "hook"}));
            var large = service.getActiveMinigame(uuid).getSession();
            assertEquals(250, large.caughtSize());
            assertEquals(small.adjustedDifficulty(), large.adjustedDifficulty());
            assertNull(large.obstacle());
            assertEquals("rapids", large.current().id());
            assertEquals("alert", large.opening().personality().id());
            assertEquals(8, large.opening().hookSet().windowTicks());
            assertFalse(large.opening().repeatHook());
            service.onMinigameComplete(large, false, false);
            assertFalse(service.isPlayerInMinigame(uuid));

            assertTrue(command.onCommand(player, "axs",
                new String[]{"fishing", "test", "legendary_carp", "250", "clear", "hook", "hook-loop"}));
            assertFalse(service.isPlayerInMinigame(uuid));
            assertTrue(command.onCommand(player, "axs",
                new String[]{"fishing", "test", "legendary_carp", "250", "clear", "personality=resilient", "hook-loop"}));
            var loop = service.getActiveMinigame(uuid);
            assertTrue(loop.getSession().opening().repeatHook());
            assertEquals(250, loop.getSession().caughtSize());
            assertEquals("resilient", loop.getSession().opening().personality().id());
            loop.giveUp(player);
            assertTrue(cancelled.get());
            assertFalse(service.isPlayerInMinigame(uuid));
        } finally {
            serverField.set(null, previous);
        }
    }

    private void copyExample(String path) throws Exception {
        Path target = dataDir.resolve(path);
        Files.createDirectories(target.getParent());
        try (var stream = getClass().getResourceAsStream("/" + path)) {
            assertNotNull(stream);
            Files.copy(stream, target);
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
