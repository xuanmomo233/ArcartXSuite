package xuanmo.arcartxsuite.menu.service;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.menu.config.MenuButtonDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinitionLoader;
import xuanmo.arcartxsuite.menu.config.MenuLayoutType;
import xuanmo.arcartxsuite.menu.config.MenuMessagesConfiguration;
import xuanmo.arcartxsuite.menu.config.MenuModuleConfiguration;
import xuanmo.arcartxsuite.menu.config.MenuPageDefinition;
import java.util.logging.Logger;

public final class MenuService {

    public static final String PANEL_UI_RESOURCE_PATH = "arcartx/ui/menu_panel.yml";
    public static final String PANEL_UI_FILE_PATH = "ui/menu_panel.yml";
    public static final String ESC_UI_RESOURCE_PATH = "arcartx/ui/menu_esc.yml";
    public static final String ESC_UI_FILE_PATH = "ui/menu_esc.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private MenuModuleConfiguration configuration;
    private final Map<String, MenuDefinition> menus = new LinkedHashMap<>();
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    private final MenuActionExecutor actionExecutor;
    private final MenuIconResolver iconResolver;
    private final MenuBindingRegistry bindingRegistry = new MenuBindingRegistry();
    private String panelRuntimeUiId = "menu_panel";
    private String escRuntimeUiId = "menu_esc";

    public MenuService(
        JavaPlugin plugin,
        Logger logger,
        PacketBridgeAPI packetBridge,
        PacketGuardAPI packetGuard,
        MenuModuleConfiguration configuration,
        ItemBridgeAPI itemStackBridge,
        ItemSourceRegistry itemSourceRegistry
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.configuration = configuration;
        this.actionExecutor = new MenuActionExecutor(plugin, this);
        this.iconResolver = new MenuIconResolver(itemStackBridge, itemSourceRegistry);
    }

    public void setRuntimeUiIds(String panelRuntimeUiId, String escRuntimeUiId) {
        if (panelRuntimeUiId != null && !panelRuntimeUiId.isBlank()) {
            this.panelRuntimeUiId = panelRuntimeUiId;
        }
        if (escRuntimeUiId != null && !escRuntimeUiId.isBlank()) {
            this.escRuntimeUiId = escRuntimeUiId;
        }
    }

    public void reload(File dataFolder) throws IOException {
        File menusDirectory = configuration.menusDirectory(dataFolder);
        menus.clear();
        menus.putAll(MenuDefinitionLoader.loadDirectory(
            menusDirectory,
            configuration.settings().defaultLayout(),
            configuration.settings().columns(),
            configuration.settings().buttonsPerPage(),
            this.logger
        ));
        bindingRegistry.rebuild(
            menus.values(),
            configuration.settings().globalItemBinds(),
            this.logger
        );
    }

    public MenuBindingRegistry bindingRegistry() {
        return bindingRegistry;
    }

    public void updateConfiguration(MenuModuleConfiguration configuration) {
        this.configuration = configuration;
    }

    public MenuModuleConfiguration configuration() {
        return configuration;
    }

    public MenuMessagesConfiguration messages() {
        return configuration.messages();
    }

    public Collection<MenuDefinition> menus() {
        return menus.values();
    }

    public MenuDefinition findMenu(String menuId) {
        if (menuId == null) {
            return null;
        }
        return menus.get(menuId.toLowerCase(Locale.ROOT));
    }

    public MenuDefinition findMenuIgnoreCase(String menuId) {
        if (menuId == null) {
            return null;
        }
        MenuDefinition direct = menus.get(menuId);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, MenuDefinition> entry : menus.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(menuId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void openEscMenu(Player player) {
        String escMenuId = configuration.client().escMenuId();
        MenuDefinition escDefinition = findMenuIgnoreCase(escMenuId);
        if (escDefinition == null) {
            for (MenuDefinition definition : menus.values()) {
                if (definition.layout() == MenuLayoutType.ESC || definition.matchEsc()) {
                    escDefinition = definition;
                    break;
                }
            }
        }
        if (escDefinition == null) {
            return;
        }
        pushMenu(player, escDefinition, 0, false);
    }

    public boolean openMenu(Player player, String menuId, int pageIndex) {
        MenuDefinition definition = findMenuIgnoreCase(menuId);
        if (definition == null) {
            notifyOpenFailed(player, menuId, "not-found");
            return false;
        }
        return pushMenu(player, definition, pageIndex, true);
    }

    private boolean pushMenu(Player player, MenuDefinition definition, int pageIndex, boolean openUi) {
        if (!MenuConditionEvaluator.hasPermission(player, definition.permission())) {
            player.sendMessage(messages().colorize(messages().noPermission()));
            return false;
        }
        if (!MenuConditionEvaluator.passes(player, definition.openRequirements())) {
            notifyOpenFailed(player, definition.id(), "requirements");
            return false;
        }
        MenuSession session = new MenuSession(definition.id(), definition.layout(), Math.max(0, pageIndex));
        sessions.put(player.getUniqueId(), session);
        actionExecutor.executeAll(player, definition.openActions());

        if (packetBridge == null || !packetBridge.isAvailable()) {
            player.sendMessage(messages().colorize("&cArcartX UI 当前不可用。"));
            return false;
        }
        if (openUi) {
            packetBridge.openUi(player, runtimeUiId(definition.layout()));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    pushPayload(player, session, definition, true);
                }
            }, 2L);
            return true;
        }
        pushPayload(player, session, definition, true);
        return true;
    }

    public boolean openMenu(Player player, String menuId) {
        return openMenu(player, menuId, 0);
    }

    public void refreshMenu(Player player) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        MenuDefinition definition = findMenuIgnoreCase(session.menuId());
        if (definition == null) {
            sessions.remove(player.getUniqueId());
            return;
        }
        pushPayload(player, session, definition, false);
    }

    public void closeMenu(Player player) {
        MenuSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        MenuDefinition definition = findMenuIgnoreCase(session.menuId());
        if (definition != null) {
            actionExecutor.executeAll(player, definition.closeActions());
        }
        if (packetBridge != null && packetBridge.isAvailable()) {
            packetBridge.closeUi(player, runtimeUiId(session.layout()));
        }
    }

    public void changePage(Player player, String pageToken) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        MenuDefinition definition = findMenuIgnoreCase(session.menuId());
        if (definition == null) {
            return;
        }
        if (pageToken == null || pageToken.isBlank()) {
            return;
        }
        String normalized = pageToken.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "prev", "previous", "-", "<" -> session.setPageIndex(session.pageIndex() - 1);
            case "next", "+", ">" -> session.setPageIndex(session.pageIndex() + 1);
            default -> session.setPageIndex(definition.pageIndexOf(pageToken));
        }
        pushPayload(player, session, definition, false);
    }

    public boolean handleButtonClick(Player player, String buttonId, boolean footer) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null || buttonId == null || buttonId.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - session.lastClickAt() < configuration.settings().clickCooldownMs()) {
            return true;
        }
        session.markClick(now);

        MenuDefinition definition = findMenuIgnoreCase(session.menuId());
        if (definition == null) {
            return true;
        }
        MenuButtonDefinition button = footer
            ? definition.footerButtons().get(buttonId)
            : findPageButton(definition.pageAt(session.pageIndex()), buttonId);
        if (button == null) {
            player.sendMessage(messages().colorize(messages().buttonUnavailable()));
            refreshMenu(player);
            return true;
        }
        if (!MenuConditionEvaluator.hasPermission(player, button.permission())) {
            player.sendMessage(messages().colorize(messages().buttonUnavailable()));
            refreshMenu(player);
            return true;
        }
        if (!MenuConditionEvaluator.passes(player, button.viewConditions())) {
            player.sendMessage(messages().colorize(messages().buttonUnavailable()));
            refreshMenu(player);
            return true;
        }
        if (!MenuConditionEvaluator.passes(player, button.useConditions())) {
            String deny = button.denyMessage();
            if (deny != null && !deny.isBlank()) {
                player.sendMessage(messages().colorize(MenuConditionEvaluator.applyPlaceholders(player, deny)));
            } else {
                player.sendMessage(messages().colorize(messages().buttonUnavailable()));
            }
            return true;
        }
        if (packetGuard != null && !packetGuard.allow(player, "menu", "click", false)) {
            return true;
        }
        actionExecutor.executeAll(player, button.actions());
        if (sessions.containsKey(player.getUniqueId())) {
            refreshMenu(player);
        }
        return true;
    }

    public boolean allowClientPacket(Player player, String action) {
        return packetGuard == null || packetGuard.allow(player, "menu", action, false);
    }

    public void handlePlayerQuit(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public MenuDefinition resolveCommandMenu(String commandLabel) {
        if (commandLabel == null || commandLabel.isBlank()) {
            return null;
        }
        String normalized = commandLabel.toLowerCase(Locale.ROOT);
        for (MenuDefinition definition : menus.values()) {
            for (String command : definition.commands()) {
                if (command != null && command.equalsIgnoreCase(normalized)) {
                    return definition;
                }
            }
        }
        return null;
    }

    private MenuButtonDefinition findPageButton(MenuPageDefinition page, String buttonId) {
        MenuButtonDefinition direct = page.buttons().get(buttonId);
        if (direct != null) {
            return direct;
        }
        for (MenuButtonDefinition button : page.buttons().values()) {
            if (button.id().equalsIgnoreCase(buttonId)) {
                return button;
            }
        }
        return null;
    }

    private void pushPayload(Player player, MenuSession session, MenuDefinition definition, boolean initPacket) {
        if (packetBridge == null) {
            return;
        }
        Map<String, Object> payload = MenuPacketFactory.buildPayload(
            configuration.client().packetId(),
            player,
            definition,
            session,
            iconResolver
        );
        packetBridge.sendPacket(
            player,
            runtimeUiId(definition.layout()),
            initPacket ? "init" : "update",
            payload
        );
    }

    private String runtimeUiId(MenuLayoutType layout) {
        return MenuPacketFactory.runtimeUiId(layout, panelRuntimeUiId, escRuntimeUiId);
    }

    private void notifyOpenFailed(Player player, String menuId, String reason) {
        if (!configuration.settings().notifyOpenFailed()) {
            return;
        }
        player.sendMessage(messages().colorize(
            messages().format(messages().menuOpenFailed(), "menu", menuId)
                .replace("{reason}", reason)
        ));
    }
}


