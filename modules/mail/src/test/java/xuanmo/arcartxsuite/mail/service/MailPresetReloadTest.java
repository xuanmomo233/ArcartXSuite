package xuanmo.arcartxsuite.mail.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import xuanmo.arcartxsuite.mail.config.MailModuleConfiguration;
import xuanmo.arcartxsuite.mail.model.MailCdkDefinition;
import xuanmo.arcartxsuite.mail.storage.MailRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailPresetReloadTest {
    private static final Logger LOGGER = Logger.getLogger(MailPresetReloadTest.class.getName());

    @TempDir Path directory;

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
        assertTrue(service.reloadPresets().success());
        assertEquals(java.util.List.of("starter"), service.presetIds());

        Files.writeString(starter, "hd:\n  id: hd\n");
        assertFalse(service.reloadPresets().success());
        assertEquals(java.util.List.of("starter"), service.presetIds());

        Files.writeString(starter, "preset:\n  id: hd\n");
        assertTrue(service.reloadPresets().success());
        assertEquals(java.util.List.of("hd"), service.presetIds());
    }

    @Test
    void presetReloadUpdatesCdkWithoutResettingClaimsOrRelativeExpiry() throws Exception {
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
        Instant created = Instant.parse("2026-10-01T00:00:00Z");
        when(repository.loadCdk("STARTER2026")).thenReturn(Optional.of(new MailCdkDefinition(
            "STARTER2026", "old", 100, 17, null, true, "admin", created, created)));

        assertTrue(service(repository).reloadPresets().success());

        ArgumentCaptor<MailCdkDefinition> saved = ArgumentCaptor.forClass(MailCdkDefinition.class);
        verify(repository).saveCdk(saved.capture());
        assertEquals("starter", saved.getValue().presetId());
        assertEquals(200, saved.getValue().maxClaims());
        assertEquals(17, saved.getValue().claimedCount());
        assertEquals(created, saved.getValue().createdAt());
        assertEquals(created.plusSeconds(30L * 86400), saved.getValue().expiresAt());
    }

    private MailService service(MailRepository repository) {
        MailModuleConfiguration config = MailModuleConfiguration.load(new YamlConfiguration(), LOGGER);
        return new MailService(null, LOGGER, directory.toFile(), config, repository,
            null, null, null, null, null, null, null, null);
    }

    private void write(String name, String yaml) throws IOException {
        Files.writeString(directory.resolve(name), yaml);
    }
}
