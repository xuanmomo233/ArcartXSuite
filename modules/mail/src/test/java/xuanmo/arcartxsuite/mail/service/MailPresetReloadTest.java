package xuanmo.arcartxsuite.mail.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.storage.MailRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailPresetReloadTest {
    private static final Logger LOGGER = Logger.getLogger(MailPresetReloadTest.class.getName());

    @TempDir Path directory;
    private final LinkedBlockingQueue<Runnable> mainTasks = new LinkedBlockingQueue<>();

    @Test
    void reportsRenamedRootWithFileAndRequiredKey() throws Exception {
        write("hdyj.yml", "hdyj:\n  id: hdyj\n");
        IOException error = assertThrows(IOException.class,
            () -> MailPresetLoader.loadPresets(directory.toFile(), LOGGER, 18));
        assertTrue(error.getMessage().contains("hdyj.yml"));
        assertTrue(error.getMessage().contains("preset.id"));
    }

    @Test
    void rejectsMalformedYamlInsteadOfSilentlyLoadingAnEmptyPreset() throws Exception {
        write("starter.yml", "preset:\n  id: [broken\n");
        IOException error = assertThrows(IOException.class,
            () -> MailPresetLoader.loadPresets(directory.toFile(), LOGGER, 18));
        assertTrue(error.getMessage().contains("starter.yml"));
        assertTrue(error.getMessage().contains("YAML"));
    }

    @Test
    void loadsExplicitIdAndFallsBackToFilename() throws Exception {
        write("starter.yml", "preset:\n  id: HD\n  subject: test\n");
        write("fallback.YML", "preset:\n  subject: fallback\n");
        var presets = MailPresetLoader.loadPresets(directory.toFile(), LOGGER, 18);
        assertEquals("test", presets.get("hd").subject());
        assertTrue(presets.containsKey("fallback"));
    }

    @Test
    void rejectsDuplicateIdsInsteadOfChoosingAnArbitraryFile() throws Exception {
        write("a.yml", "preset:\n  id: shared\n");
        write("b.yml", "preset:\n  id: SHARED\n");
        IOException error = assertThrows(IOException.class,
            () -> MailPresetLoader.loadPresets(directory.toFile(), LOGGER, 18));
        assertTrue(error.getMessage().contains("shared"));
        assertTrue(error.getMessage().contains("b.yml"));
    }

    @Test
    void failedReloadKeepsPreviouslyLoadedPresetsAndRecoveryReadsDisk() throws Exception {
        Path presets = Files.createDirectories(directory.resolve("presets"));
        Path starter = presets.resolve("starter.yml");
        Files.writeString(starter, "preset:\n  id: starter\n");
        MailService service = service(mock(MailRepository.class));
        assertTrue(reload(service).success());
        assertEquals(java.util.List.of("starter"), service.presetIds());

        Files.writeString(starter, "hd:\n  id: hd\n");
        assertFalse(reload(service).success());
        assertEquals(java.util.List.of("starter"), service.presetIds());

        Files.writeString(starter, "preset:\n  id: hd\n");
        assertTrue(reload(service).success());
        assertEquals(java.util.List.of("hd"), service.presetIds());
    }

    @Test
    void presetReloadSendsRelativeExpiryInABatchWithoutReadingOldCounts() throws Exception {
        Path presets = Files.createDirectories(directory.resolve("presets"));
        Files.writeString(presets.resolve("starter.yml"), """
            preset:
              id: starter
              cdks:
                - code: STARTER2026
                  max-claims: 200
                  expires-after: 30d
            """);
        MailRepository repository = mock(MailRepository.class);
        assertTrue(reload(service(repository)).success());

        verify(repository).syncPresetCdks(argThat(rows -> {
            assertEquals(1, rows.size());
            var row = rows.get(0);
            assertTrue(row.relativeExpiry());
            assertEquals("starter", row.definition().presetId());
            assertEquals(200, row.definition().maxClaims());
            assertEquals(row.definition().createdAt().plusSeconds(30L * 86400), row.definition().expiresAt());
            return true;
        }));
        verify(repository, never()).loadCdk(anyString());
    }

    @Test
    void blockedDatabaseKeepsOldPresetsVisibleAndRejectsCompetingUpdates() throws Exception {
        Path presets = Files.createDirectories(directory.resolve("presets"));
        Path starter = presets.resolve("starter.yml");
        Files.writeString(starter, "preset:\n  id: starter\n");
        var repository = mock(MailRepository.class);
        var service = service(repository);
        assertTrue(reload(service).success());
        Files.writeString(starter, "preset:\n  id: new\n  cdks:\n    - code: NEWCODE\n      max-claims: 1\n");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var main = Thread.currentThread();
        doAnswer(call -> {
            assertNotSame(main, Thread.currentThread());
            entered.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS));
            return null;
        }).when(repository).syncPresetCdks(anyList());
        try {
            var pending = service.reloadPresets().toCompletableFuture();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var repeated = service.reloadPresets().toCompletableFuture();
            assertFalse(pending.isDone());
            assertFalse(repeated.get(2, TimeUnit.SECONDS).success());
            assertEquals(java.util.List.of("starter"), service.presetIds());
            assertFalse(service.deletePreset("starter").success());
            var player = mock(org.bukkit.entity.Player.class);
            service.redeemCdk(player, "NEWCODE", result -> assertFalse(result.success()));
            verifyNoInteractions(player);
            release.countDown();
            runMainTask();
            assertTrue(pending.get(2, TimeUnit.SECONDS).success());
            assertEquals(java.util.List.of("new"), service.presetIds());
            verify(repository).syncPresetCdks(anyList());
        } finally {
            release.countDown();
            service.shutdown();
        }
    }

    private xuanmo.arcartxsuite.mail.model.MailOperationResult reload(MailService service) throws Exception {
        var result = service.reloadPresets().toCompletableFuture();
        if (!result.isDone()) runMainTask();
        return result.get(2, TimeUnit.SECONDS);
    }

    @Test
    void inboxCdkActionRequiresTheSamePermissionAsTheCommand() throws Exception {
        var config = new YamlConfiguration();
        config.set("cdk.require-permission", true);
        var repository = mock(MailRepository.class);
        var bridge = mock(xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.class);
        var service = new MailService(mock(JavaPlugin.class), LOGGER, directory.toFile(),
            MailModuleConfiguration.load(config, LOGGER), repository, bridge, null, null, null, null, null, null, null);
        var active = MailService.class.getDeclaredField("active");
        active.setAccessible(true);
        active.setBoolean(service, true);
        var inbox = MailService.class.getDeclaredField("inboxUiId");
        inbox.setAccessible(true);
        inbox.set(service, "AXS:mail");
        var player = mock(org.bukkit.entity.Player.class);
        when(player.isOnline()).thenReturn(true);
        when(bridge.isUiOpen(player, "AXS:mail")).thenReturn(true);
        assertTrue(service.handleClientPacket(player, MailService.CLIENT_PACKET_ID, java.util.List.of("cdk", "EXAMPLE")));
        verify(player).hasPermission("arcartxsuite.mail.cdk");
        verifyNoInteractions(repository);
    }

    private void runMainTask() throws InterruptedException {
        var task = mainTasks.poll(2, TimeUnit.SECONDS);
        assertNotNull(task);
        task.run();
    }

    private MailService service(MailRepository repository) {
        MailModuleConfiguration config = MailModuleConfiguration.load(new YamlConfiguration(), LOGGER);
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            mainTasks.add(call.getArgument(1));
            return null;
        });
        return new MailService(plugin, LOGGER, directory.toFile(), config, repository,
            null, null, null, null, null, null, null, null);
    }

    private void write(String name, String yaml) throws IOException {
        Files.writeString(directory.resolve(name), yaml);
    }
}
