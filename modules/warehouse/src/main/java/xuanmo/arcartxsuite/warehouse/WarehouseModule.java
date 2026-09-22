package xuanmo.arcartxsuite.warehouse;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfig;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannelConfigs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.ExtraBackpackAccess;
import xuanmo.arcartxsuite.api.capability.PickupNotifiable;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.warehouse.command.WarehouseAdminCommand;
import xuanmo.arcartxsuite.warehouse.command.WarehousePlayerCommand;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.placeholder.WarehousePlaceholderExpansion;
import xuanmo.arcartxsuite.warehouse.service.WarehouseService;
import xuanmo.arcartxsuite.warehouse.storage.JdbcWarehouseRepository;

/**
 * Warehouse 模块入口，提供个人仓库、共享仓库与多货币银行系统。
 * <p>
 * 三套 AXUI 界面：
 * <ul>
 *   <li>warehouse_menu.yml — 仓库存取主界面</li>
 *   <li>warehouse_manage.yml — 共享管理与设置界面</li>
 *   <li>warehouse_bank.yml — 银行活期/定期界面</li>
 * </ul>
 * <p>
 * 注册能力：
 * <ul>
 *   <li>{@link WarehouseAutoDepositable} — 供 Pickup 等外部模块调用自动入库</li>
 *   <li>{@code PlayerDataPurgeable} — 支持 /axs purge 清除玩家数据</li>
 *   <li>{@code DatabaseMigratable} — 支持 /axs migrate 跨源迁移</li>
 * </ul>
 */
public final class WarehouseModule extends AbstractAXSModule implements ModuleCommandHandler {

    private WarehouseAdminCommand adminCommand;

    private static final String STORAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_storage.yml";
    private static final String STORAGE_UI_FILE_PATH = "ui/warehouse_storage.yml";
    private static final String MANAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_manage.yml";
    private static final String MANAGE_UI_FILE_PATH = "ui/warehouse_manage.yml";
    private static final String BANK_UI_RESOURCE_PATH = "arcartx/ui/warehouse_bank.yml";
    private static final String BANK_UI_FILE_PATH = "ui/warehouse_bank.yml";

    private WarehouseModuleConfiguration configuration;
    private CrossServerChannelConfig crossServerChannelConfig = CrossServerChannelConfig.disabled();
    private WarehouseService service;
    private JdbcWarehouseRepository repository;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("warehouse")
            .name("Warehouse")
            .version("1.4.5")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXWarehouse.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("warehouses")
                    .dynamicSection("categories")
                    .dynamicSection("bank.currencies")
                    .dynamicSection("bank.deposit-products")
                    .dynamicSection("shared")
                    .dynamicSection("sort-profiles")
                .build())
            .currentVersion(4)
            .validations(List.of(
                    // storage.shared 必须是布尔值
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    // pickup 服务器级开关必须是布尔值
                    ValidationRule.of("pickup.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("pickup.mythic-loot-enabled", ValueType.BOOLEAN),
                    // pool-size 范围 1-100（仅在 shared: false 时生效）
                    ValidationRule.of("storage.pool-size", ValueType.INT)
                        .withRange(1, 100),
                    // flush-interval-ticks 必须为正
                    ValidationRule.of("settings.flush-interval-ticks", ValueType.INT)
                        .withRange(1, null),
                    // 二级密码长度限制
                    ValidationRule.required("security.min-length", ValueType.INT)
                        .withRange(1, 32),
                    ValidationRule.required("security.max-length", ValueType.INT)
                        .withRange(4, 64)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(STORAGE_UI_RESOURCE_PATH, STORAGE_UI_FILE_PATH);
        mappings.put(MANAGE_UI_RESOURCE_PATH, MANAGE_UI_FILE_PATH);
        mappings.put(BANK_UI_RESOURCE_PATH, BANK_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) ->
                    service != null && service.handleClientPacket(player, packetId, data), 0, configuration == null ? null : configuration.ui().packetId(), "warehouse");
    }

    /**
     * 配置校验规则。字段类型/范围/枚举不匹配时，诊断引擎会提示并可通过 {@code /axs config apply warehouse} 修复。
     */

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXWarehouse.yml 配置文件缺失");
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = WarehouseModuleConfiguration.load(yaml, logger, currencyManager);
        crossServerChannelConfig = CrossServerChannelConfigs.fromSection(yaml.getConfigurationSection("cross-server"));
    }

    /**
     * 启动服务流程：
     * <ol>
     *   <li>初始化仓库 Repository（SQLite / MySQL）</li>
     *   <li>导出并注册三套 AXUI 界面</li>
     *   <li>创建 WarehouseService 并启动</li>
     *   <li>注册命令、PAPI 扩展、客户端包处理器</li>
     *   <li>注册 {@link WarehouseAutoDepositable} / {@code PlayerDataPurgeable} / {@code DatabaseMigratable} 能力</li>
     * </ol>
     */
    @Override
    protected List<String> publishedTopics() {
        return List.of("axs.warehouse.item_deposited");
    }

    @Override
    protected void startService() throws Exception {

        WarehouseService.UiResourceExporter uiExporter = (resourcePath, relativeUiPath, overwrite) -> {
            try {
                return exportUiResource(resourcePath, relativeUiPath, overwrite, moduleClassLoader());
            } catch (IOException ex) {
                throw ex;
            }
        };

        // 共享模式：使用本体统一数据源，模块只需提供 SQLite 文件名
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor whDesc;
        javax.sql.DataSource whDs;
        if (configuration.storage().hasOverride()) {
            whDesc = configuration.storage().toDescriptor();
            whDs = storageManager.resolveModuleDataSource("warehouse", null, dataFolder, whDesc);
        } else {
            whDesc = storageManager.getDescriptor().withTablePrefix("");
            whDs = storageManager.resolveModuleDataSource("warehouse",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        JdbcWarehouseRepository warehouseRepo = new JdbcWarehouseRepository(whDs, whDesc, dataFolder, logger);
        repository = warehouseRepo;
        service = new WarehouseService(
            plugin, logger, packetBridge, itemStackBridge, packetGuard, uiExporter, configuration,
            warehouseRepo,
            itemSourceRegistry, itemMatcher, currencyManager,
            () -> getCapability(PickupNotifiable.class),
            crossServer, crossServerChannelConfig, messages(),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.SecondaryPasswordAccess.class),
            vanillaItemNameBridge
        );
        service.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.setPickupInterceptorProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.PickupInterceptor.class));
        // 拾取溢出兜底：仓库放不下时把剩余物品转存 ExtraBackpack（模块缺席时安全降级）
        service.setExtraBackpackProvider(this::getExtraBackpackAccess);
        service.start();
        // 将 Service 手动注册的 UI 纳入基类生命周期管理（onDisable 时兜底注销）
        for (String uiId : service.registeredUiIds()) {
            recordExternalUi(uiId, uiId);
        }

        adminCommand = new WarehouseAdminCommand(() -> service, messages());

        registerCapability(WarehouseAutoDepositable.class,
            (player, itemStack) -> service.depositToPersonalWarehouse(player, itemStack));

        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "warehouse"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return warehouseRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Warehouse purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return warehouseRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Warehouse purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "warehouse"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return warehouseRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return warehouseRepo.getDescriptor();
                }
            });

        logger.fine(
            "Warehouse 模块已载入，UI=" + configuration.ui().uiId()
                + " | 存储=" + configuration.storage().mode()
                + " | 跨服编辑锁=" + (service.crossServerActive() ? "ON" : "OFF")
        );
    }

    /**
     * 停止服务：注销 UI 回调、清理玩家状态、关闭数据库连接。
     */
    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (repository != null) {
            storageManager.closeModuleDataSource("warehouse");
            repository = null;
        }
        configuration = null;
    }

    /**
     * 注册玩家命令 {@code /warehouse}（别名 {@code /wh}）。
     */
    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        WarehousePlayerCommand cmd = new WarehousePlayerCommand(() -> service, messages());
        return Map.of("warehouse", (TabExecutor) cmd);
    }

    /**
     * 注册 PAPI 占位符扩展，前缀 {@code %axswarehouse_xxx%}。
     */
    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new WarehousePlaceholderExpansion(plugin, () -> service);
    }

    /**
     * 注册客户端包处理器，处理包 ID 为 {@code AXS_WAREHOUSE} 的客户端操作请求。
     */

    public WarehouseService getService() {
        return service;
    }

    /**
     * Returns the optional extra-backpack provider without coupling warehouse
     * behavior to the provider module's presence.
     */
    public @Nullable ExtraBackpackAccess getExtraBackpackAccess() {
        return getCapability(ExtraBackpackAccess.class);
    }

    public WarehouseModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "warehouse"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}
