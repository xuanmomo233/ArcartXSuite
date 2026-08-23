package xuanmo.arcartxsuite.vanilla;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.vanilla.config.VanillaConfiguration;
import xuanmo.arcartxsuite.vanilla.listener.InventoryOpenListener;
import xuanmo.arcartxsuite.vanilla.service.VanillaService;
import xuanmo.arcartxsuite.vanilla.task.FurnaceStateTask;

/**
 * Vanilla UI 模块：基于 ArcartX UI 系统替换原版工作台与熔炉界面。
 * <p>
 * 提供配方书、中文搜索、一键填充材料、熔炉状态实时推送等功能。
 */
public final class VanillaModule extends AbstractAXSModule {

    private static final String CRAFTING_RESOURCE_PATH = "arcartx/ui/vanilla_crafting.yml";
    private static final String CRAFTING_FILE_PATH = "ui/vanilla_crafting.yml";
    private static final String FURNACE_RESOURCE_PATH = "arcartx/ui/vanilla_furnace.yml";
    private static final String FURNACE_FILE_PATH = "ui/vanilla_furnace.yml";

    private VanillaConfiguration configuration;
    private VanillaService service;
    private FurnaceStateTask furnaceTask;
    private InventoryOpenListener inventoryListener;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("vanilla")
            .name("VanillaUI")
            .version("1.4.4")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXVanilla.yml")
            .syncPolicy(SyncPolicy.strict())
            .currentVersion(2)
            .validations(List.of(
                    ValidationRule.of("ui.crafting-ui-id", ValueType.STRING),
                    ValidationRule.of("ui.furnace-ui-id", ValueType.STRING),
                    ValidationRule.of("ui.overwrite-ui-files", ValueType.BOOLEAN),
                    ValidationRule.of("crafting.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("furnace.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("furnace.push-interval-ticks", ValueType.INT)
                        .withRange(1, 100)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        if (configuration == null || configuration.crafting().enabled()) {
            mappings.put(CRAFTING_RESOURCE_PATH, CRAFTING_FILE_PATH);
        }
        if (configuration == null || configuration.furnace().enabled()) {
            mappings.put(FURNACE_RESOURCE_PATH, FURNACE_FILE_PATH);
        }
        return new ModuleUiSpec(mappings, configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) ->
                    service != null && service.handleClientPacket(player, packetId, data), 0, null, null);
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXVanilla.yml 配置文件缺失");
        }
        configuration = VanillaConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile));
    }

    @Override
    protected void startService() throws Exception {
        service = new VanillaService(packetBridge, vanillaItemNameBridge, configuration, logger);

        // 注册工作台 UI
        if (configuration.crafting().enabled()) {
            UiBinding craftingBinding = registerModuleUi(
                CRAFTING_FILE_PATH,
                configuration.ui().craftingUiId(),
                true
            );
            logger.fine("Vanilla 工作台 UI 已注册: " + craftingBinding.runtimeUiId());
        }

        // 注册熔炉 UI + 状态推送任务
        if (configuration.furnace().enabled()) {
            UiBinding furnaceBinding = registerModuleUi(
                FURNACE_FILE_PATH,
                configuration.ui().furnaceUiId(),
                true
            );
            logger.fine("Vanilla 熔炉 UI 已注册: " + furnaceBinding.runtimeUiId());

            furnaceTask = new FurnaceStateTask(plugin, packetBridge, configuration.ui().furnaceUiId());
            furnaceTask.start(configuration.furnace().pushIntervalTicks());
            inventoryListener = new InventoryOpenListener(furnaceTask);
        }

        logger.info("VanillaUI 模块已启用 | 工作台=" + configuration.crafting().enabled()
            + " 熔炉=" + configuration.furnace().enabled());
    }

    @Override
    protected void stopService() {
        if (furnaceTask != null) {
            furnaceTask.stop();
            furnaceTask = null;
        }
        if (service != null) {
            service.shutdown();
            service = null;
        }
        inventoryListener = null;
        configuration = null;
    }

    @Override
    protected @NotNull List<Listener> createListeners() {
        if (inventoryListener != null) {
            return List.of(inventoryListener);
        }
        return List.of();
    }

}
