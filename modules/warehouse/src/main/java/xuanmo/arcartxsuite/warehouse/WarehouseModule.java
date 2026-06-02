package xuanmo.arcartxsuite.warehouse;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.api.capability.PickupNotifiable;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.warehouse.command.WarehouseAdminCommand;
import xuanmo.arcartxsuite.warehouse.command.WarehousePlayerCommand;
import xuanmo.arcartxsuite.warehouse.config.WarehouseModuleConfiguration;
import xuanmo.arcartxsuite.warehouse.placeholder.WarehousePlaceholderExpansion;
import xuanmo.arcartxsuite.warehouse.service.WarehouseService;
import xuanmo.arcartxsuite.warehouse.storage.JdbcWarehouseRepository;

public final class WarehouseModule extends AbstractAXSModule implements ModuleCommandHandler {

    private WarehouseAdminCommand adminCommand;

    private static final String STORAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_menu.yml";
    private static final String STORAGE_UI_FILE_PATH = "ui/warehouse_menu.yml";
    private static final String MANAGE_UI_RESOURCE_PATH = "arcartx/ui/warehouse_manage.yml";
    private static final String MANAGE_UI_FILE_PATH = "ui/warehouse_manage.yml";
    private static final String BANK_UI_RESOURCE_PATH = "arcartx/ui/warehouse_bank.yml";
    private static final String BANK_UI_FILE_PATH = "ui/warehouse_bank.yml";

    private WarehouseModuleConfiguration configuration;
    private WarehouseService service;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("warehouse")
            .name("Warehouse")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXWarehouse.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // 仓库类型、分类、共享仓、排序方案均由用户自定义
        return SyncPolicy.builder()
            .dynamicSection("warehouses")
            .dynamicSection("categories")
            .dynamicSection("bank.currencies")
            .dynamicSection("bank.deposit-products")
            .dynamicSection("shared")
            .dynamicSection("sort-profiles")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // storage.mode 必须是 sqlite 或 mysql
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            // pool-size 范围 1-100
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            // flush-interval-ticks 必须为正
            ValidationRule.of("settings.flush-interval-ticks", ValueType.INT)
                .withRange(1, null),
            // 二级密码长度限制
            ValidationRule.required("security.min-length", ValueType.INT)
                .withRange(1, 32),
            ValidationRule.required("security.max-length", ValueType.INT)
                .withRange(4, 64)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(STORAGE_UI_RESOURCE_PATH, STORAGE_UI_FILE_PATH);
        mappings.put(MANAGE_UI_RESOURCE_PATH, MANAGE_UI_FILE_PATH);
        mappings.put(BANK_UI_RESOURCE_PATH, BANK_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXWarehouse.yml 配置文件缺失");
        }
        configuration = WarehouseModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), context.logger());
    }

    @Override
    protected void startService() throws Exception {
        ArcartXPacketBridge packetBridge = (ArcartXPacketBridge) context.packetBridge();
        PacketGuardAPI packetGuard = context.packetGuard();

        WarehouseService.UiResourceExporter uiExporter = (resourcePath, relativeUiPath, overwrite) -> {
            try {
                return context.exportUiResource(resourcePath, relativeUiPath, overwrite, moduleClassLoader());
            } catch (IOException ex) {
                throw ex;
            }
        };

        xuanmo.arcartxsuite.bridge.ArcartXItemStackBridge itemStackBridge =
            (xuanmo.arcartxsuite.bridge.ArcartXItemStackBridge) context.itemStackBridge();
        JdbcWarehouseRepository warehouseRepo = new JdbcWarehouseRepository(
            context.migrateLegacyDataFile(configuration.storage().sqliteFileName()),
            configuration.storage(), context.logger());
        service = new WarehouseService(
            context.plugin(), packetBridge, itemStackBridge, packetGuard, uiExporter, configuration,
            warehouseRepo,
            context.itemSourceRegistry(), context.itemMatcher(), context.currencyManager(),
            () -> context.getCapability(PickupNotifiable.class)
        );
        service.start();
        adminCommand = new WarehouseAdminCommand(() -> service, messages());

        context.registerCapability(WarehouseAutoDepositable.class,
            (player, itemStack) -> service.depositToPersonalWarehouse(player, itemStack));

        context.registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "warehouse"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return warehouseRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { context.logger().warning("Warehouse purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return warehouseRepo.deleteAllPlayerData(); }
                    catch (Exception e) { context.logger().warning("Warehouse purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        context.registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
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

        context.logger().fine(
            "Warehouse 模块已载入，UI=" + configuration.ui().uiId()
                + " | 存储=" + configuration.storage().dialect().configKey()
        );
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        WarehousePlayerCommand cmd = new WarehousePlayerCommand(() -> service, messages());
        return Map.of("warehouse", (TabExecutor) cmd);
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new WarehousePlaceholderExpansion(context.plugin(), () -> service);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientPacket(player, packetId, data);
    }

    public WarehouseService getService() {
        return service;
    }

    public WarehouseModuleConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "warehouse"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}
