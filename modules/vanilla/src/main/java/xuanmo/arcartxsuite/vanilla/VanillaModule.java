package xuanmo.arcartxsuite.vanilla;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.event.ModuleLifecycleEvent;
import xuanmo.arcartxsuite.api.capability.WarehousePanelAccess;
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
public final class VanillaModule extends AbstractAXSModule implements Listener {

    private static final String CRAFTING_RESOURCE_PATH = "arcartx/ui/vanilla_crafting.yml";
    private static final String CRAFTING_FILE_PATH = "ui/vanilla_crafting_v2.yml";
    private static final String FURNACE_RESOURCE_PATH = "arcartx/ui/vanilla_furnace.yml";
    private static final String FURNACE_FILE_PATH = "ui/vanilla_furnace_v2.yml";

    private VanillaConfiguration configuration;
    private VanillaService service;
    private FurnaceStateTask furnaceTask;
    private InventoryOpenListener inventoryListener;
    private static final Pattern LAYOUT_BOOTSTRAP = Pattern.compile(
        "(?m)^([\\t ]*)var\\.whLayoutEnabled = (?:true|false)[\\t ]*$");
    private final Map<String, File> layoutUiFiles = new LinkedHashMap<>();
    private BukkitTask layoutRefreshTask;
    private boolean stopping;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("vanilla")
            .name("VanillaUI")
            .version("1.4.4")
            .mainClass(getClass().getName())
            .softDepends(List.of("warehouse"))
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
        stopping = false;
        service = new VanillaService(packetBridge, vanillaItemNameBridge, configuration, logger);
        service.setWarehousePanel(() -> getCapability(WarehousePanelAccess.class));
        service.materialPlanner().setProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.WarehouseMaterialAccess.class));

        // 注册工作台 UI
        if (configuration.crafting().enabled()) {
            UiBinding craftingBinding = registerPreparedUi(
                CRAFTING_RESOURCE_PATH,
                CRAFTING_FILE_PATH,
                configuration.ui().craftingUiId()
            );
            logger.fine("Vanilla 工作台 UI 已注册: " + craftingBinding.runtimeUiId());
            service.setCraftingRuntimeUiId(craftingBinding.runtimeUiId());
        }

        // 注册熔炉 UI + 状态推送任务
        if (configuration.furnace().enabled()) {
            UiBinding furnaceBinding = registerPreparedUi(
                FURNACE_RESOURCE_PATH,
                FURNACE_FILE_PATH,
                configuration.ui().furnaceUiId()
            );
            logger.fine("Vanilla 熔炉 UI 已注册: " + furnaceBinding.runtimeUiId());

            service.setFurnaceRuntimeUiId(furnaceBinding.runtimeUiId());
            furnaceTask = new FurnaceStateTask(plugin, packetBridge, furnaceBinding.runtimeUiId());
            furnaceTask.start(configuration.furnace().pushIntervalTicks());
            inventoryListener = new InventoryOpenListener(furnaceTask);
        }

        logger.info("VanillaUI 模块已启用 | 工作台=" + configuration.crafting().enabled()
            + " 熔炉=" + configuration.furnace().enabled());
    }

    @Override
    protected void stopService() {
        stopping = true;
        if (layoutRefreshTask != null) {
            layoutRefreshTask.cancel();
            layoutRefreshTask = null;
        }
        layoutUiFiles.clear();
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
        List<Listener> listeners = new ArrayList<>();
        listeners.add(this);
        listeners.add(new xuanmo.arcartxsuite.vanilla.listener.MaterialTransferInventoryGuard(service.materialPlanner()));
        if (inventoryListener != null) {
            listeners.add(inventoryListener);
        }
        return listeners;
    }

    private UiBinding registerPreparedUi(String resource, String relativePath, String uiId) throws IOException {
        File uiFile = exportUiResource(resource, relativePath,
            configuration.ui().overwriteUiFiles(), moduleClassLoader());
        prepareWarehouseLayout(uiFile);
        layoutUiFiles.put(uiId, uiFile);
        return registerPreparedFile(uiId, uiFile);
    }

    private boolean prepareWarehouseLayout(File uiFile) throws IOException {
        String source = Files.readString(uiFile.toPath(), StandardCharsets.UTF_8);
        Matcher marker = LAYOUT_BOOTSTRAP.matcher(source);
        if (!marker.find()) {
            logger.warning("Vanilla UI 缺少 whLayoutEnabled 初始化标记，保留自定义文件: " + uiFile.getName());
            return false;
        }
        String prepared = marker.replaceFirst(Matcher.quoteReplacement(marker.group(1)
            + "var.whLayoutEnabled = " + (getCapability(WarehousePanelAccess.class) != null)));
        if (source.equals(prepared)) return false;
        Files.writeString(uiFile.toPath(), prepared, StandardCharsets.UTF_8);
        return true;
    }

    private UiBinding registerPreparedFile(String uiId, File uiFile) {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            return new UiBinding(PacketBridgeAPI.normalizeUiId(uiId, uiFile), null);
        }
        var registration = packetBridge.registerOrReloadUi(uiId, uiFile);
        if (!registration.success()) {
            throw new IllegalStateException("Vanilla UI 注册失败: " + registration.message());
        }
        recordExternalUi(registration.runtimeUiId(), registration.registeredUiId());
        return new UiBinding(registration.runtimeUiId(), registration.registeredUiId());
    }

    @EventHandler
    public void onWarehouseLifecycle(ModuleLifecycleEvent event) {
        if (stopping || !"warehouse".equals(event.moduleId())) return;
        if (event.phase() == ModuleLifecycleEvent.Phase.DISABLING || event.phase() == ModuleLifecycleEvent.Phase.DISABLED) {
            if (service != null) service.materialPlanner().cancelPending();
        }
        switch (event.phase()) {
            case ENABLED, DISABLED, RELOADED, ENABLE_FAILED -> { }
            default -> { return; }
        }
        if (layoutRefreshTask != null) layoutRefreshTask.cancel();
        layoutRefreshTask = Bukkit.getScheduler().runTask(plugin, () -> {
            layoutRefreshTask = null;
            if (stopping) return;
            for (var entry : layoutUiFiles.entrySet()) {
                try {
                    if (prepareWarehouseLayout(entry.getValue())) {
                        registerPreparedFile(entry.getKey(), entry.getValue());
                    }
                } catch (Exception exception) {
                    logger.warning("更新 Vanilla 仓库布局失败: " + exception.getMessage());
                }
            }
        });
    }

}
