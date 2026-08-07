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

/**
 * ExtraBackpack 模块入口，继承 {@link AbstractAXSModule}。
 * <p>
 * 负责加载配置、初始化 {@link JdbcExtraBackpackRepository} 与 {@link ExtraBackpackService}，
 * 并注册 {@link xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess}、
 * {@link xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable}、
 * {@link xuanmo.arcartxsuite.api.capability.DatabaseMigratable} 等能力，
 * 接入宿主的玩家数据清除与跨源数据库迁移命令。
 */
public final class ExtraBackpackModule extends AbstractAXSModule {

    private ExtraBackpackConfiguration configuration;
    private JdbcExtraBackpackRepository repository;
    private ExtraBackpackService service;

    /**
     * {@inheritDoc}
     * <p>
     * 返回 ExtraBackpack 模块的描述符（id: {@code extrabackpack}，版本 {@code 1.0.0}）。
     */
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("extrabackpack")
            .name("ExtraBackpack")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    /**
     * 返回模块主配置文件名。
     *
     * @return {@code "ArcartXExtraBackpack.yml"}
     */
    @Override
    protected @Nullable String configFileName() {
        return "ArcartXExtraBackpack.yml";
    }

    /**
     * 返回模块消息文件名。
     *
     * @return {@code "messages.yml"}
     */
    @Override
    protected @Nullable String messagesFileName() {
        return "messages.yml";
    }

    /**
     * 返回默认配置同步策略，将 {@code extra-backpack} 节声明为动态节，
     * 允许用户自由增删分类而不被同步器清理。
     *
     * @return 同步策略
     */
    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("extra-backpack")
            .build();
    }

    /**
     * 返回主配置的校验规则列表，覆盖存储模式枚举、连接池范围与调试/UI/启用开关类型校验。
     *
     * @return 校验规则列表
     */
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

    /**
     * 返回 UI 资源映射，将 jar 内 UI 模板导出到模块数据目录。
     *
     * @return 资源路径到目标相对路径的映射
     */
    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("arcartx/ui/extrabackpack.yml", "ui/extrabackpack.yml");
        return mappings;
    }

    /**
     * 返回是否在启用时覆盖已存在的 UI 文件。
     *
     * @return 配置中 {@code ui.overwrite-ui-files} 的值
     */
    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFiles();
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
        if (configuration.storage().hasOverride()) {
            // 旧路径：模块自建连接池
            repository = new JdbcExtraBackpackRepository(dataFolder, configuration.storage(), logger);
        } else {
            // 新路径：共享本体数据源
            repository = new JdbcExtraBackpackRepository(
                storageManager, "extrabackpack",
                "",
                configuration.storage().sqliteFileName(), logger);
        }
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
            messages(),
            vanillaItemNameBridge
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

    /**
     * 停止模块服务，关闭 {@link ExtraBackpackService} 与 {@link JdbcExtraBackpackRepository}，释放资源。
     */
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

    /**
     * 创建客户端数据包处理器，将背包操作数据包转发给 {@link ExtraBackpackService}，
     * 对涉及销毁/密码操作的数据包额外注入 {@link SecondaryPasswordAccess} 能力。
     *
     * @return 数据包处理器实例
     */
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

    /**
     * 返回本模块声明的归属数据包 ID，用于数据包归属校验。
     *
     * @return {@code "AXS_BACKPACK"}
     */
    @Override
    protected @Nullable String packetOwnershipPacketId() {
        return "AXS_BACKPACK";
    }

    /**
     * 返回数据包守卫的模块键，用于数据包安全校验。
     *
     * @return {@code "extrabackpack"}
     */
    @Override
    protected @Nullable String packetGuardModuleKey() {
        return "extrabackpack";
    }
}
