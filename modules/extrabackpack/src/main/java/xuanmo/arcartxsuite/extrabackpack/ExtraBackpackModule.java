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
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess;
import xuanmo.arcartxsuite.api.storage.MigrationResult;
import xuanmo.arcartxsuite.api.storage.StorageDescriptor;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.extrabackpack.command.ExtraBackpackAdminCommand;
import xuanmo.arcartxsuite.extrabackpack.config.ExtraBackpackConfiguration;
import xuanmo.arcartxsuite.extrabackpack.placeholder.ExtraBackpackPlaceholderExpansion;
import xuanmo.arcartxsuite.extrabackpack.service.ExtraBackpackService;
import xuanmo.arcartxsuite.extrabackpack.storage.JdbcExtraBackpackRepository;

/**
 * ExtraBackpack 模块入口，继承 {@link AbstractAXSModule}。
 * <p>
 * 负责加载配置、初始化 {@link JdbcExtraBackpackRepository} 与 {@link ExtraBackpackService}，
 * 并注册 {@link xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess}、
 * {@link xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable}、
 * {@link xuanmo.arcartxsuite.api.capability.DatabaseMigratable} 等能力，
 * 接入宿主的玩家数据清除与跨源数据库迁移命令。
 */
public final class ExtraBackpackModule extends AbstractAXSModule implements ModuleCommandHandler {

    private ExtraBackpackConfiguration configuration;
    private JdbcExtraBackpackRepository repository;
    private ExtraBackpackService service;
    private ExtraBackpackAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("extrabackpack")
            .name("ExtraBackpack")
            .version("1.5.0")
            .mainClass(getClass().getName())
            // 共通存储：扩展背包直接读写玩家个人仓库，warehouse 缺席则无存储后端
            .softDepends(java.util.List.of("warehouse"))
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXExtraBackpack.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("extra-backpack")
                    // 共通存储重构后废弃：独立落盘/调试开关/分类独立容量与价格全部移除
                    .obsoletePath("settings")
                    .obsoletePath("extra-backpack.categories.*.slots.initial")
                    .obsoletePath("extra-backpack.categories.*.initial-slots")
                    .obsoletePath("extra-backpack.categories.*.max-slots")
                    .obsoletePath("extra-backpack.categories.*.price")
                .build())
            .currentVersion(2)
            .validations(List.of(
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    ValidationRule.of("storage.pool-size", ValueType.INT)
                        .withRange(1, 100),
                    ValidationRule.of("ui.register-ui-on-enable", ValueType.BOOLEAN),
                    ValidationRule.of("ui.overwrite-ui-files", ValueType.BOOLEAN),
                    ValidationRule.of("extra-backpack.enabled", ValueType.BOOLEAN)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("arcartx/ui/extrabackpack.yml", "ui/extrabackpack_v2.yml");
        return new ModuleUiSpec(mappings, configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((player, packetId, data) -> {
            if (service == null) {
                return false;
            }
            SecondaryPasswordAccess passwordAccess = null;
            if (data != null && !data.isEmpty() && (
                "destroy_confirm".equalsIgnoreCase(data.get(0))
                    || "password_set".equalsIgnoreCase(data.get(0))
                    || "password_unlock".equalsIgnoreCase(data.get(0))
                    || "password_clear".equalsIgnoreCase(data.get(0))
                    || "password_panel_close".equalsIgnoreCase(data.get(0))
            )) {
                passwordAccess = getCapability(SecondaryPasswordAccess.class);
            }
            return service.handleClientPacket(player, packetId, data, passwordAccess);
        }, 0, "AXS_BACKPACK", "extrabackpack");
    }

    /**
     * 加载并解析模块主配置文件。
     *
     * @param configFile 配置文件；为 {@code null} 时抛出 {@link IllegalStateException}
     * @throws IllegalStateException 配置文件缺失时抛出
     */
    @Override
    protected void loadConfiguration(@Nullable File configFile) {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXExtraBackpack.yml configuration is missing");
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = ExtraBackpackConfiguration.load(yaml, logger);
    }

    /**
     * 启动模块服务：初始化存储仓库、创建并启动 {@link ExtraBackpackService}，
     * 注册扩展背包访问、玩家数据清除与数据库迁移能力。
     *
     * @throws Exception 初始化或启动失败时抛出
     */
    @Override
    protected void startService() throws Exception {
        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor ebDesc;
        javax.sql.DataSource ebDs;
        if (configuration.storage().hasOverride()) {
            ebDesc = configuration.storage().toDescriptor();
            ebDs = storageManager.resolveModuleDataSource("extrabackpack", null, dataFolder, ebDesc);
        } else {
            ebDesc = storageManager.getDescriptor().withTablePrefix("");
            ebDs = storageManager.resolveModuleDataSource("extrabackpack",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        repository = new JdbcExtraBackpackRepository(ebDs, ebDesc, dataFolder, logger);
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
            uiExporter,
            configuration,
            messages(),
            vanillaItemNameBridge,
            currencyManager,
            rondoBridge
        );
        service.setEventBusProvider(() -> getCapability(EventBusCapability.class));
        // 共通存储：扩展背包直接读写玩家个人仓库（同一存储，两个入口）
        service.setWarehouseProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.WarehouseAccess.class));
        service.setPanelProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.WarehousePanelAccess.class));
        service.start();
        adminCommand = new ExtraBackpackAdminCommand(() -> service, messages());
        // 将 Service 手动注册的 UI 纳入基类生命周期管理（onDisable 时兜底注销）
        for (String uiId : service.registeredUiIds()) {
            recordExternalUi(uiId, uiId);
        }
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
    protected @Nullable Object createPlaceholderExpansion() {
        return new ExtraBackpackPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    protected List<String> publishedTopics() {
        return List.of(
            ExtraBackpackService.TOPIC_DEPOSITED,
            ExtraBackpackService.TOPIC_WITHDRAWN,
            ExtraBackpackService.TOPIC_UPGRADED,
            ExtraBackpackService.TOPIC_DESTROYED
        );
    }

    @Override public String commandId() { return "extrabackpack"; }

    @Override public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status");
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }

    /**
     * 停止模块服务，关闭 {@link ExtraBackpackService} 与 {@link JdbcExtraBackpackRepository}，释放资源。
     */
    @Override
    protected void stopService() {
        adminCommand = null;
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (repository != null) {
            repository.close();
            repository = null;
        }
        storageManager.closeModuleDataSource("extrabackpack");
    }
}
