package xuanmo.arcartxsuite.pickup;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.PickupNotifiable;
import xuanmo.arcartxsuite.api.capability.WarehouseAutoDepositable;
import xuanmo.arcartxsuite.pickup.command.PickupPlayerCommand;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration;
import xuanmo.arcartxsuite.pickup.config.PickupModuleConfiguration.PickupMode;
import xuanmo.arcartxsuite.pickup.service.LootScannerService;
import xuanmo.arcartxsuite.pickup.service.PickupService;
import xuanmo.arcartxsuite.pickup.ui.PickupHudTemplateWriter;

/**
 * Pickup 拾取模块。
 * 支持两种工作模式：
 * - notification: 拾取通知 HUD（原有功能）
 * - scanner: 掉落物扫描面板，禁用自动拾取，按键交互拾取
 */
public final class PickupModule extends AbstractAXSModule {

    private static final String LOOT_PANEL_RESOURCE_PATH = "arcartx/ui/loot_panel.yml";
    private static final String LOOT_PANEL_FILE_PATH = "ui/loot_panel.yml";
    private static final String LOOT_INTERACT_RESOURCE_PATH = "arcartx/ui/loot_interact.yml";
    private static final String LOOT_INTERACT_FILE_PATH = "ui/loot_interact.yml";

    private PickupModuleConfiguration configuration;
    private PickupService notificationService;
    private LootScannerService scannerService;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("pickup")
            .name("Pickup")
            .version("1.1.0-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXPickup.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.of("notification.max-visible", ValueType.INT)
                .withRange(1, 16),
            ValidationRule.of("notification.entry-ttl-ms", ValueType.INT)
                .withRange(500, 30000),
            ValidationRule.of("scanner.scan-radius", ValueType.DOUBLE)
                .withRange(1.0, 32.0),
            ValidationRule.of("scanner.scan-interval-ticks", ValueType.INT)
                .withRange(1, 100),
            ValidationRule.of("scanner.max-display", ValueType.INT)
                .withRange(1, 16)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(LOOT_PANEL_RESOURCE_PATH, LOOT_PANEL_FILE_PATH);
        mappings.put(LOOT_INTERACT_RESOURCE_PATH, LOOT_INTERACT_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.scanner().overwriteUiFile();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXPickup.yml 配置文件缺失");
        }
        configuration = PickupModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile));
    }

    @Override
    protected void startService() throws Exception {

        if (configuration.mode() == PickupMode.SCANNER) {
            startScannerMode(packetBridge, itemStackBridge);
        } else {
            startNotificationMode(packetBridge, itemStackBridge);
        }
    }

    private void startNotificationMode(PacketBridgeAPI packetBridge, ItemBridgeAPI itemStackBridge) throws IOException {
        File uiFile = exportPickupUiFile(configuration);
        xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.UiRegistrationResult reg =
            packetBridge.registerOrReloadUi(configuration.uiId(), uiFile);
        if (!reg.success()) {
            throw new IOException("Pickup 通知模式 UI 注册失败: " + reg.message());
        }
        UiBinding uiBinding = new UiBinding(reg.runtimeUiId(), reg.registeredUiId());
        recordUiBinding("ui/pickup_hud.yml", uiBinding);

        notificationService = new PickupService(
            plugin, logger, configuration, packetBridge, itemStackBridge,
            uiBinding.runtimeUiId()
        );
        notificationService.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        notificationService.start();

        // 注册通知能力，让 warehouse 等模块知道该玩家已有 HUD 拾取通知
        registerCapability(PickupNotifiable.class,
            playerId -> notificationService != null && notificationService.isEnabled(playerId));

        logger.fine(
            "Pickup 通知模式已载入，max-visible=" + configuration.maxVisible()
                + " | ttl=" + configuration.entryTtlMs()
                + "ms | UI=" + uiBinding.runtimeUiId()
        );
    }

    private void startScannerMode(PacketBridgeAPI packetBridge, ItemBridgeAPI itemStackBridge) throws IOException {
        // HUD（被动显示）
        UiBinding uiBinding = registerModuleUi(
            LOOT_PANEL_FILE_PATH,
            configuration.scanner().uiId(),
            configuration.scanner().registerUiOnEnable()
        );
        if (uiBinding.registeredUiId() == null) {
            throw new IOException("Pickup 扫描模式 HUD UI 注册失败");
        }

        // Menu（交互捕获）
        UiBinding interactBinding = registerModuleUi(
            LOOT_INTERACT_FILE_PATH,
            configuration.scanner().interactUiId(),
            configuration.scanner().registerUiOnEnable()
        );
        if (interactBinding.registeredUiId() == null) {
            throw new IOException("Pickup 扫描模式 Interact Menu UI 注册失败");
        }

        scannerService = new LootScannerService(
            plugin, logger, configuration, packetGuard, packetBridge, itemStackBridge,
            uiBinding.runtimeUiId(), interactBinding.runtimeUiId(),
            () -> getCapability(WarehouseAutoDepositable.class)
        );
        scannerService.start();

        // 注册宿主全局按键回调（优先级 10，拾取优先于对话）
        registerKeybindHandler("AXS_INTERACT", 10, (player, keyName) ->
            scannerService != null && scannerService.handleInteractKeyFromHost(player)
        );

        logger.fine(
            "Pickup 扫描模式已载入，radius=" + configuration.scanner().scanRadius()
                + " | maxDisplay=" + configuration.scanner().maxDisplay()
                + " | HUD=" + uiBinding.runtimeUiId()
                + " | Menu=" + interactBinding.runtimeUiId()
        );
    }

    @Override
    protected void stopService() {
        if (notificationService != null) {
            notificationService.shutdown();
            notificationService = null;
        }
        if (scannerService != null) {
            scannerService.shutdown();
            scannerService = null;
        }
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        return Map.of(
            "pickup", new PickupPlayerCommand(
                () -> notificationService,
                () -> scannerService,
                messages()
            )
        );
    }

    @Override
    protected @Nullable ClientInitializedHandler createInitializedHandler() {
        return player -> {
            if (scannerService != null) {
                scannerService.onClientInitialized(player);
            }
        };
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) -> {
            if (scannerService == null || !"pickup".equalsIgnoreCase(packetId)) {
                return false;
            }
            String action = (data == null || data.isEmpty()) ? "pick" : data.get(0).toLowerCase();
            return scannerService.handleClientPacket(player, action, data);
        };
    }

    public PickupService getNotificationService() {
        return notificationService;
    }

    public LootScannerService getScannerService() {
        return scannerService;
    }

    private File exportPickupUiFile(PickupModuleConfiguration config) throws IOException {
        File target = new File(pluginDataFolder, "ui/pickup_hud.yml");
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!target.exists() || config.notification().overwriteUiFile()) {
            PickupHudTemplateWriter.write(target, config.maxVisible(), config.entryTtlMs());
        }
        return target;
    }
}




