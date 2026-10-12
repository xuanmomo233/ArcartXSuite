package xuanmo.arcartxsuite.menu;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.MenuOpenable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.menu.command.MenuAdminCommand;
import xuanmo.arcartxsuite.menu.command.MenuPlayerCommand;
import xuanmo.arcartxsuite.menu.config.MenuModuleConfiguration;
import xuanmo.arcartxsuite.menu.listener.MenuBindingListener;
import xuanmo.arcartxsuite.menu.packet.MenuUiPacketHandler;
import xuanmo.arcartxsuite.menu.service.MenuService;

/**
 * Menu 模块主类，继承 {@link AbstractAXSModule} 并实现 {@link ModuleCommandHandler}。
 * <p>
 * 负责菜单定义的加载、UI 注册、命令绑定、事件监听和客户端包处理，
 * 并通过 {@link xuanmo.arcartxsuite.api.capability.MenuOpenable} 向其他模块暴露菜单打开能力。
 */
public final class MenuModule extends AbstractAXSModule implements ModuleCommandHandler {

    private MenuModuleConfiguration configuration;
    private MenuService service;
    private MenuUiPacketHandler packetHandler;
    private MenuAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("menu")
            .name("Menu")
            .version("1.5.0")
            .mainClass(getClass().getName())
            .externalSoftDepends(List.of("PlaceholderAPI"))
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXMenu.yml")
            .messagesFileName("messages.yml")
            .currentVersion(2)
            .validations(List.of(
                    ValidationRule.required("ui.packet-id", ValueType.STRING),
                    ValidationRule.required("settings.menus-directory", ValueType.STRING)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MenuService.PANEL_UI_RESOURCE_PATH, MenuService.PANEL_UI_FILE_PATH);
        mappings.put(MenuService.ESC_UI_RESOURCE_PATH, MenuService.ESC_UI_FILE_PATH);
        mappings.put(MenuService.ESC_GRID_UI_RESOURCE_PATH, MenuService.ESC_GRID_UI_FILE_PATH);
        mappings.put(MenuService.PANEL_GRID_UI_RESOURCE_PATH, MenuService.PANEL_GRID_UI_FILE_PATH);
        return new ModuleUiSpec(mappings, configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of(
            (player, packetId, data) ->
                    packetHandler != null && packetHandler.handleClientPacket(player, packetId, data), 0, null, null);
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMenu.yml 配置文件缺失");
        }
        configuration = MenuModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile),
            MessageProvider.loadYamlWithBundledDefaults(
                new File(configFile.getParentFile(), configSpec().messagesFileName()),
                configSpec().messagesFileName(),
                moduleClassLoader(),
                logger,
                resourcePath -> openResource(resourcePath, moduleClassLoader())));
        ensureDefaultMenus();
    }

    private void ensureDefaultMenus() {
        File menusDirectory = configuration.menusDirectory(dataFolder);
        if (!menusDirectory.exists()) {
            menusDirectory.mkdirs();
        }
        File[] existing = menusDirectory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        exportMenuIfMissing(menusDirectory, "example.yml");
        exportMenuIfMissing(menusDirectory, "esc_main.yml");
    }

    private void exportMenuIfMissing(File menusDirectory, String fileName) {
        File target = new File(menusDirectory, fileName);
        if (!target.exists()) {
            exportResource("menus/" + fileName, target, false);
        }
    }

    @Override
    protected void startService() throws Exception {
        if (packetBridge == null || !packetBridge.isAvailable()) {
            throw new IllegalStateException("Menu 模块需要 ArcartX PacketBridge");
        }

        UiBinding panelBinding = registerModuleUi(
            MenuService.PANEL_UI_FILE_PATH,
            configuration.ui().panelUiId(),
            configuration.ui().registerUiOnEnable()
        );
        UiBinding escBinding = registerModuleUi(
            MenuService.ESC_UI_FILE_PATH,
            configuration.ui().escUiId(),
            configuration.ui().registerUiOnEnable()
        );
        if (panelBinding.registeredUiId() == null || escBinding.registeredUiId() == null) {
            throw new IllegalStateException("Menu UI 注册失败");
        }

        service = new MenuService(
            plugin, logger,
            packetBridge,
            packetGuard,
            configuration,
            itemStackBridge,
            itemSourceRegistry,
            (Supplier<SignalDispatchable>) () -> getCapability(SignalDispatchable.class),
            messages()
        );
        service.setRuntimeUiIds(panelBinding.runtimeUiId(), escBinding.runtimeUiId());
        service.reload(dataFolder);
        registerCustomMenuUis(panelBinding.runtimeUiId(), escBinding.runtimeUiId());
        packetHandler = new MenuUiPacketHandler(service, configuration.ui().packetId());
        adminCommand = new MenuAdminCommand(service, messages());

        registerCapability(MenuOpenable.class, new MenuOpenable() {
            @Override
            public boolean openMenu(@NotNull org.bukkit.entity.Player player, @NotNull String menuId) {
                return service.openMenu(player, menuId);
            }

            @Override
            public void refreshMenu(@NotNull org.bukkit.entity.Player player) {
                service.refreshMenu(player);
            }
        });

        logger.info("Menu 模块已启动，菜单数量=" + service.menus().size());
    }

    private void registerCustomMenuUis(String panelRuntimeUiId, String escRuntimeUiId) {
        if (service == null || packetBridge == null || !packetBridge.isAvailable()) {
            return;
        }
        java.util.Set<String> builtin = new java.util.HashSet<>();
        builtin.add(panelRuntimeUiId);
        builtin.add(escRuntimeUiId);
        java.util.Set<String> handled = new java.util.HashSet<>();
        File uiDirectory = new File(pluginDataFolder, "ui");
        for (xuanmo.arcartxsuite.menu.config.MenuDefinition definition : service.menus()) {
            for (xuanmo.arcartxsuite.menu.config.MenuUiTarget target : definition.uiTargets()) {
                String uiId = target.uiId();
                if (uiId == null || uiId.isBlank() || builtin.contains(uiId) || !handled.add(uiId)) {
                    continue;
                }
                File uiFile = new File(uiDirectory, uiId + ".yml");
                if (!uiFile.isFile()) {
                    logger.warning("菜单 " + definition.id() + " 引用的自定义 UI 文件不存在，已跳过: ui/" + uiId + ".yml");
                    continue;
                }
                PacketBridgeAPI.UiRegistrationResult result = packetBridge.registerOrReloadUi(uiId, uiFile);
                if (!result.success()) {
                    logger.warning("自定义菜单 UI 注册失败: " + uiId + " -> " + result.message());
                    continue;
                }
                // 纳入基类生命周期管理：onDisable / reload 时自动注销，避免卸载后残留 match 绑定
                recordExternalUi(result.runtimeUiId(), result.registeredUiId());
                if (!uiId.equals(result.runtimeUiId())) {
                    logger.warning("自定义菜单 UI runtime id 与配置不一致(请让 ui-id 使用不带命名空间前缀的 id): 配置="
                        + uiId + " runtime=" + result.runtimeUiId());
                } else {
                    logger.info("已注册自定义菜单 UI: " + uiId + " (" + uiFile.getName() + ")");
                }
            }
        }
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
        }
        packetHandler = null;
        adminCommand = null;
        service = null;
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        MenuPlayerCommand playerCommand = new MenuPlayerCommand(() -> this.service, messages());
        return Map.of("menu", playerCommand);
    }

    @Override
    protected List<Listener> createListeners() {
        MenuBindingListener bindingListener = new MenuBindingListener(() -> this.service);
        return List.of(bindingListener, new Listener() {
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                if (service != null) {
                    service.handlePlayerQuit(event.getPlayer());
                }
            }
        });
    }

    /**
     * 返回模块命令标识。
     *
     * @return 命令 ID {@code "menu"}
     */
    @Override
    public String commandId() {
        return "menu";
    }

    /**
     * 返回管理命令支持的子命令动作列表。
     *
     * @return 子命令名称列表
     */
    @Override
    public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status");
    }

    /**
     * 处理管理命令，reload 子命令直接触发模块热重载，其余委托给 {@link MenuAdminCommand}。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   命令参数数组
     * @return {@code true} 表示命令已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (adminCommand == null) {
            return false;
        }
        String action = args.length < 2 ? "help" : args[1].toLowerCase();
        if ("reload".equals(action)) {
            if (!sender.hasPermission("axs.menu.reload")) {
                sender.sendMessage(messages().get("prefix") + messages().get("common.no-permission"));
                return true;
            }
            try {
                onReload();
                sender.sendMessage(messages().get("prefix") + messages().get("admin.reload-success"));
            } catch (Exception exception) {
                sender.sendMessage(messages().get("prefix") + messages().get("admin.reload-failed", exception.getMessage()));
            }
            return true;
        }
        return adminCommand.onCommand(sender, label, args);
    }

    /**
     * 提供管理命令的 Tab 补全，委托给 {@link MenuAdminCommand}。
     *
     * @param sender 命令发送者
     * @param args   当前已输入的参数数组
     * @return 补全候选列表
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}

