package xuanmo.arcartxsuite.menu;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.menu.command.MenuAdminCommand;
import xuanmo.arcartxsuite.menu.command.MenuPlayerCommand;
import xuanmo.arcartxsuite.menu.config.MenuModuleConfiguration;
import xuanmo.arcartxsuite.menu.listener.MenuBindingListener;
import xuanmo.arcartxsuite.menu.packet.MenuUiPacketHandler;
import xuanmo.arcartxsuite.menu.service.MenuService;

public final class MenuModule extends AbstractAXSModule implements ModuleCommandHandler {

    private MenuModuleConfiguration configuration;
    private MenuService service;
    private MenuUiPacketHandler packetHandler;
    private MenuAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("menu")
            .name("Menu")
            .version("1.0.0-beta")
            .mainClass(getClass().getName())
            .externalSoftDepends(List.of("PlaceholderAPI"))
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXMenu.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("client.packet-id", ValueType.STRING),
            ValidationRule.required("settings.menus-directory", ValueType.STRING)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MenuService.PANEL_UI_RESOURCE_PATH, MenuService.PANEL_UI_FILE_PATH);
        mappings.put(MenuService.ESC_UI_RESOURCE_PATH, MenuService.ESC_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.client().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXMenu.yml 配置文件缺失");
        }
        configuration = MenuModuleConfiguration.load(YamlConfiguration.loadConfiguration(configFile));
        ensureDefaultMenus();
    }

    private void ensureDefaultMenus() {
        File menusDirectory = configuration.menusDirectory(context.dataFolder());
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
            context.exportResource("menus/" + fileName, target, false);
        }
    }

    @Override
    protected void startService() throws Exception {
        PacketBridgeAPI packetBridge = context.packetBridge();
        PacketGuardAPI packetGuard = context.packetGuard();
        if (packetBridge == null || !packetBridge.isAvailable()) {
            throw new IllegalStateException("Menu 模块需要 ArcartX PacketBridge");
        }

        File panelFile = new File(context.pluginDataFolder(), MenuService.PANEL_UI_FILE_PATH);
        File escFile = new File(context.pluginDataFolder(), MenuService.ESC_UI_FILE_PATH);

        UiBinding panelBinding = context.prepareUiBinding(
            "Menu Panel",
            configuration.client().panelUiId(),
            configuration.client().registerUiOnEnable(),
            panelFile
        );
        UiBinding escBinding = context.prepareUiBinding(
            "Menu ESC",
            configuration.client().escUiId(),
            configuration.client().registerUiOnEnable(),
            escFile
        );
        if (panelBinding == null || escBinding == null) {
            throw new IllegalStateException("Menu UI 注册失败");
        }
        recordUiBinding(MenuService.PANEL_UI_FILE_PATH, panelBinding);
        recordUiBinding(MenuService.ESC_UI_FILE_PATH, escBinding);

        service = new MenuService(
            context.plugin(),
            packetBridge,
            packetGuard,
            configuration,
            context.itemStackBridge(),
            context.itemSourceRegistry()
        );
        service.setRuntimeUiIds(panelBinding.runtimeUiId(), escBinding.runtimeUiId());
        service.reload(context.dataFolder());
        packetHandler = new MenuUiPacketHandler(service, configuration.client().packetId());
        adminCommand = new MenuAdminCommand(service, messages());

        context.registerCapability(MenuOpenable.class, new MenuOpenable() {
            @Override
            public boolean openMenu(@NotNull org.bukkit.entity.Player player, @NotNull String menuId) {
                return service.openMenu(player, menuId);
            }

            @Override
            public void refreshMenu(@NotNull org.bukkit.entity.Player player) {
                service.refreshMenu(player);
            }
        });

        context.logger().info("Menu 模块已启动，菜单数量=" + service.menus().size());
    }

    @Override
    protected void stopService() {
        packetHandler = null;
        adminCommand = null;
        service = null;
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        MenuPlayerCommand playerCommand = new MenuPlayerCommand(service, messages());
        return Map.of("menu", playerCommand);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            packetHandler != null && packetHandler.handleClientPacket(player, packetId, data);
    }

    @Override
    protected List<Listener> createListeners() {
        MenuBindingListener bindingListener = new MenuBindingListener(service, service.bindingRegistry());
        return List.of(bindingListener, new Listener() {
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                if (service != null) {
                    service.handlePlayerQuit(event.getPlayer());
                }
            }
        });
    }

    @Override
    public void onReload() throws Exception {
        File configFile = new File(context.dataFolder(), configFileName());
        loadConfiguration(configFile);
        if (service != null) {
            service.updateConfiguration(configuration);
            service.reload(context.dataFolder());
        }
    }

    @Override
    public String commandId() {
        return "menu";
    }

    @Override
    public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload");
    }

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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}
