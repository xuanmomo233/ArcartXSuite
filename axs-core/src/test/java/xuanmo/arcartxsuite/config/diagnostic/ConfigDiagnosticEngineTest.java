package xuanmo.arcartxsuite.config.diagnostic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xuanmo.arcartxsuite.api.config.ConfigDiagnosisReport;
import xuanmo.arcartxsuite.api.config.ConfigIssueKind;
import xuanmo.arcartxsuite.api.config.ConfigSyncSpec;
import xuanmo.arcartxsuite.api.config.ModuleConfigSpec;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.config.YamlConfigSynchronizer;

class ConfigDiagnosticEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void mergeMutatesLiveConfig() throws Exception {
        YamlConfiguration live = new YamlConfiguration();
        live.set("modules.aubade.enabled", true);

        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("root", "value");

        YamlConfigSynchronizer.MergeOutcome outcome = YamlConfigSynchronizer.merge(live, defaults, SyncPolicy.strict());

        assertFalse(live.isSet("modules.aubade"));
        assertFalse(live.isSet("modules.aubade.enabled"));
        assertTrue(live.isSet("root"));
    }

    @Test
    void discoveredModuleEnableSurvivesDiagnosisWhileUnknownKeyIsPruned() throws Exception {
        Path pluginData = Files.createDirectories(tempDir.resolve("plugin"));
        Path targetFile = pluginData.resolve("config.yml");

        YamlConfiguration live = new YamlConfiguration();
        live.set("modules.aubade.enabled", true);
        live.set("modules.bogus.enabled", true);
        live.save(targetFile.toFile());

        ConfigDiagnosticEngine engine = new ConfigDiagnosticEngine(
            pluginData.toFile(),
            Instant.parse("2026-07-06T00:00:00Z"),
            (ownerId, resourcePath, loader) -> new ByteArrayInputStream("root: value\n".getBytes(StandardCharsets.UTF_8)),
            getClass().getClassLoader(),
            Logger.getLogger("ConfigDiagnosticEngineTest"),
            () -> Set.of("aubade")
        );

        ModuleConfigSpec spec = ModuleConfigSpec.basic("axs-core", new ConfigSyncSpec("config.yml", "config.yml", SyncPolicy.strict()));
        ConfigDiagnosisReport report = engine.diagnose(spec, getClass().getClassLoader(), true);

        assertNotNull(report.proposedFile());

        YamlConfiguration proposed = new YamlConfiguration();
        proposed.load(report.proposedFile().toFile());

        assertTrue(proposed.isSet("modules.aubade"));
        assertTrue(proposed.isSet("modules.aubade.enabled"));
        assertTrue(proposed.getBoolean("modules.aubade.enabled"));
        assertFalse(proposed.isSet("modules.bogus.enabled"));

        assertTrue(report.issues().stream().noneMatch(issue ->
            issue.kind() == ConfigIssueKind.OBSOLETE_KEY
                && issue.configPath().startsWith("modules.aubade")
        ));
        assertTrue(report.issues().stream().anyMatch(issue ->
            issue.kind() == ConfigIssueKind.OBSOLETE_KEY
                && issue.configPath().startsWith("modules.bogus")
        ));
    }
}
