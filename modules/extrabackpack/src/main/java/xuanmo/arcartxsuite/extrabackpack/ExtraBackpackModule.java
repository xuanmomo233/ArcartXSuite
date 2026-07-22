package xuanmo.arcartxsuite.extrabackpack;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.storage.MigrationResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration;
import xuanmo.arcartxsuite.extrabackpack.service.ExtraBackpackService;
import xuanmo.arcartxsuite.extrabackpack.storage.JdbcExtraBackpackRepository;

public final class ExtraBackpackModule extends AbstractAXSModule {

    private ExtraBackpackConfiguration configuration;
    private JdbcExtraBackpackRepository repository;
    private ExtraBackpackService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("extrabackpack")
            .name("ExtraBackpack")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @Nullable String configFileName() {
        return "ArcartXExtraBackpack.yml";
    }

    @Override
    protected @Nullable String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("extra-backpack")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(java.util.Set.of("sqlite", "mysql")),
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            ValidationRule.of("settings.debug", ValueType.BOOLEAN),
            ValidationRule.of("ui.register-ui-on-enable", ValueType.BOOLEAN),
            ValidationRule.of("ui.overwrite-ui-files", ValueType.BOOLEAN),
            ValidationRule.of("extra-backpack.enabled", ValueType.BOOLEAN)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("arcartx/ui/extrabackpack.yml", "ui/extrabackpack.yml");
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXExtraBackpack.yml configuration is missing");
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = ExtraBackpackConfiguration.load(yaml, logger);
    }

    @Override
    protected void startService() throws Exception {
        repository = new JdbcExtraBackpackRepository(dataFolder, configuration.storage(), logger);
        repository.initialize();
        ExtraBackpackService.UiResourceExporter uiExporter = (resourcePath, relativeUiPath, overwrite) -> {
            try {
                return exportUiResource(resourcePath, relativeUiPath, overwrite, moduleClassLoader());
            } catch (IOException exception) {
                throw exception;
            }
        };
        service = new ExtraBackpackService(
            plugin,
            logger,
            packetBridge,
            itemStackBridge,
            packetGuard,
            uiExporter,
            configuration,
            repository,
            currencyManager,
            messages()
        );
        service.start();
        registerCapability(ExtraBackpackAccess.class, service);

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override
            public @NotNull String moduleId() {
                return "extrabackpack";
            }

            @Override
            public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try {
                    return repository.deletePlayerData(playerUuid);
                } catch (Exception exception) {
                    logger.warning("ExtraBackpack purge failed: " + exception.getMessage());
                    return -1;
                }
            }

            @Override
            public int purgeAllPlayerData() {
                try {
                    return repository.deleteAllPlayerData();
                } catch (Exception exception) {
                    logger.warning("ExtraBackpack purgeAll failed: " + exception.getMessage());
                    return -1;
                }
            }
        });

        registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override
            public @NotNull String moduleId() {
                return "extrabackpack";
            }

            @Override
            public @NotNull MigrationResult migrateDatabase(
                @NotNull StorageDescriptor targetDescriptor,
                boolean overwriteTarget
            ) {
                return repository.migrateData(targetDescriptor, overwriteTarget);
            }

            @Override
            public @NotNull StorageDescriptor currentDescriptor() {
                return repository.getDescriptor();
            }
        });
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) -> {
            if (service == null) {
                return false;
            }
            SecondaryPasswordAccess passwordAccess = null;
            if (data != null && !data.isEmpty() && (
                "vanilla_destroy".equalsIgnoreCase(data.get(0))
                    || "password_set".equalsIgnoreCase(data.get(0))
                    || "password_unlock".equalsIgnoreCase(data.get(0))
                    || "password_clear".equalsIgnoreCase(data.get(0))
                    || "password_panel_close".equalsIgnoreCase(data.get(0))
            )) {
                passwordAccess = getCapability(SecondaryPasswordAccess.class);
            }
            return service.handleClientPacket(player, packetId, data, passwordAccess);
        };
    }
}
