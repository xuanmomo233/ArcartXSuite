package xuanmo.arcartxsuite.menu.service;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.bridge.ItemBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.icon.IconResolver;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.security.ActionTokenGuard;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.menu.config.MenuButtonDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinition;
import xuanmo.arcartxsuite.menu.config.MenuDefinitionLoader;
import xuanmo.arcartxsuite.menu.config.MenuLayoutType;
import xuanmo.arcartxsuite.menu.config.MenuMessagesConfiguration;
import xuanmo.arcartxsuite.menu.config.MenuModuleConfiguration;
import xuanmo.arcartxsuite.menu.config.MenuPageDefinition;
import xuanmo.arcartxsuite.menu.config.MenuUiTarget;
import java.util.logging.Logger;

/**
 * 菜单核心服务，管理菜单定义的加载、会话生命周期、UI 打开/关闭/刷新、按钮点击处理和绑定注册。
 * <p>
 * 作为 Menu 模块的业务中枢，协调 {@link MenuActionExecutor}、{@link IconResolver}、
 * {@link MenuBindingRegistry} 和 {@link MenuPacketFactory} 等组件，
 * 并通过 {@link xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI} 与客户端 UI 通信。
 */
public final class MenuService {

    public static final String PANEL_UI_RESOURCE_PATH = "arcartx/ui/menu_panel.yml";
    public static final String PANEL_UI_FILE_PATH = "ui/menu_panel.yml";
    public static final String ESC_UI_RESOURCE_PATH = "arcartx/ui/menu_esc.yml";
    public static final String ESC_UI_FILE_PATH = "ui/menu_esc.yml";
    public static final String ESC_GRID_UI_RESOURCE_PATH = "arcartx/ui/menu_esc_grid.yml";
    public static final String ESC_GRID_UI_FILE_PATH = "ui/menu_esc_grid.yml";
    public static final String PANEL_GRID_UI_RESOURCE_PATH = "arcartx/ui/menu_panel_grid.yml";
    public static final String PANEL_GRID_UI_FILE_PATH = "ui/menu_panel_grid.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private MenuModuleConfiguration configuration;
    private final Map<String, MenuDefinition> menus = new LinkedHashMap<>();
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();
    private final ActionTokenGuard tokenGuard = new ActionTokenGuard();
    private final MenuActionExecutor actionExecutor;
    private final IconResolver iconResolver;
    private final MessageProvider messageProvider;
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
        ItemSourceRegistry itemSourceRegistry,
        Supplier<SignalDispatchable> signalProvider,
        MessageProvider messageProvider
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.configuration = configuration;
        this.actionExecutor = new MenuActionExecutor(plugin, this, logger, signalProvider);
        this.iconResolver = new IconResolver(itemStackBridge, itemSourceRegistry,
            MenuConditionEvaluator::applyPlaceholders);
        this.messageProvider = messageProvider;
    }

    /**
     * 设置面板和 ESC 菜单的运行时 UI ID。
     *
     * @param panelRuntimeUiId 面板运行时 UI ID
     * @param escRuntimeUiId   ESC 运行时 UI ID
     */
    public void setRuntimeUiIds(String panelRuntimeUiId, String escRuntimeUiId) {
        if (panelRuntimeUiId != null && !panelRuntimeUiId.isBlank()) {
            this.panelRuntimeUiId = panelRuntimeUiId;
        }
        if (escRuntimeUiId != null && !escRuntimeUiId.isBlank()) {
            this.escRuntimeUiId = escRuntimeUiId;
        }
    }

    /**
     * 重新加载菜单定义目录并重建绑定注册表。
     *
     * @param dataFolder 模块数据目录
     * @throws IOException 读取菜单文件失败时抛出
     */
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

    /**
     * 返回绑定注册表实例。
     *
     * @return 绑定注册表
     */
    public MenuBindingRegistry bindingRegistry() {
        return bindingRegistry;
    }

    /**
     * 更新当前模块配置引用（用于热重载）。
     *
     * @param configuration 新的模块配置
     */
    public void updateConfiguration(MenuModuleConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * 返回当前模块配置。
     *
     * @return 模块配置
     */
    public MenuModuleConfiguration configuration() {
        return configuration;
    }

    /**
     * 返回消息配置。
     *
     * @return 消息配置
     */
    public MenuMessagesConfiguration messages() {
        return configuration.messages();
    }

    /**
     * 返回所有已加载的菜单定义集合。
     *
     * @return 菜单定义集合
     */
    public Collection<MenuDefinition> menus() {
        return menus.values();
    }

    /**
     * 按菜单 ID 精确查找菜单定义（小写匹配）。
     *
     * @param menuId 菜单 ID
     * @return 菜单定义，未找到返回 {@code null}
     */
    public MenuDefinition findMenu(String menuId) {
        if (menuId == null) {
            return null;
        }
        return menus.get(menuId.toLowerCase(Locale.ROOT));
    }

    /**
     * 按菜单 ID 查找菜单定义（忽略大小写）。
     *
     * @param menuId 菜单 ID
     * @return 菜单定义，未找到返回 {@code null}
     */
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

    /**
     * 打开默认 ESC 菜单，查找配置指定的 ESC 菜单 ID 或首个 ESC 布局菜单。
     *
     * @param player 目标玩家
     */
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

    /**
     * 打开指定菜单的指定页面。
     *
     * @param player    目标玩家
     * @param menuId    菜单 ID
     * @param pageIndex 起始页索引
     * @return {@code true} 表示打开成功
     */
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
        String runtimeUiId = primaryRuntimeUiId(definition);
        MenuSession session = new MenuSession(definition.id(), definition.layout(), runtimeUiId, Math.max(0, pageIndex));
        sessions.put(player.getUniqueId(), session);
        actionExecutor.executeAll(player, definition.openActions());

        if (packetBridge == null || !packetBridge.isAvailable()) {
            player.sendMessage(messageProvider.get("common.ui-unavailable"));
            return false;
        }
        if (openUi) {
            packetBridge.openUi(player, runtimeUiId);
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

    /**
     * 打开指定菜单的第一页。
     *
     * @param player 目标玩家
     * @param menuId 菜单 ID
     * @return {@code true} 表示打开成功
     */
    public boolean openMenu(Player player, String menuId) {
        return openMenu(player, menuId, 0);
    }

    /**
     * 刷新玩家当前菜单会话的 UI 数据，不重新打开 UI 窗口。
     *
     * @param player 目标玩家
     */
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

    /**
     * 关闭玩家当前菜单会话，执行关闭动作并关闭 UI 窗口。
     *
     * @param player 目标玩家
     */
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
            packetBridge.closeUi(player, session.runtimeUiId());
        }
    }

    /**
     * 切换玩家当前菜单的页面，支持 prev/next 或指定页面 ID。
     *
     * @param player    目标玩家
     * @param pageToken 页面标识或方向标记
     */
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

    /**
     * 处理按钮点击事件，执行权限和条件检查后触发对应动作。
     *
     * @param player   目标玩家
     * @param buttonId 按钮 ID
     * @param footer   是否为底栏按钮
     * @return {@code true} 表示事件已处理
     */
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
            actionExecutor.executeAll(player, button.denyActions());
            return true;
        }
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。
        actionExecutor.executeAll(player, button.actions());
        if (sessions.containsKey(player.getUniqueId())) {
            refreshMenu(player);
        }
        return true;
    }

    /**
     * 检查 PacketGuard 是否允许该玩家执行指定动作。
     *
     * @param player 目标玩家
     * @param action 动作名称
     * @return {@code true} 表示允许
     */
    public boolean allowClientPacket(Player player, String action) {
        return packetGuard == null || packetGuard.allow(player, "menu", action, false);
    }

    /**
     * 处理玩家退出事件，清理其菜单会话。
     *
     * @param player 退出的玩家
     */
    public void handlePlayerQuit(Player player) {
        sessions.remove(player.getUniqueId());
        tokenGuard.clear(player.getUniqueId());
    }

    /**
     * 模块卸载/服务停止：取消在途延迟动作链并清空会话，
     * 防止卸载后延迟动作仍引用旧 service 继续执行。
     */
    public void shutdown() {
        actionExecutor.shutdown();
        sessions.clear();
    }

    /**
     * 根据命令标签查找绑定的菜单定义。
     *
     * @param commandLabel 命令标签
     * @return 绑定的菜单定义，无绑定返回 {@code null}
     */
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
            configuration.ui().packetId(),
            player,
            definition,
            session,
            iconResolver
        );
        payload.put("action_token", tokenGuard.issue(player.getUniqueId(), session.runtimeUiId()));
        List<MenuUiTarget> targets = definition.uiTargets();
        if (targets.isEmpty()) {
            packetBridge.sendPacket(
                player,
                session.runtimeUiId(),
                initPacket ? "init" : "update",
                payload
            );
            return;
        }
        for (MenuUiTarget target : targets) {
            String handler = target.hasPacketHandler()
                ? target.packetHandler()
                : (initPacket ? "init" : "update");
            packetBridge.sendPacket(player, target.uiId(), handler, payload);
        }
    }

    /**
     * 校验写操作令牌：要求会话 UI 已打开、data[1] 与当前签发 token 一致、且玩家无在途写操作。
     */
    public boolean beginWriteAction(Player player, List<String> data) {
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) return false;
        String scope = session.runtimeUiId();
        String supplied = data != null && data.size() > 1 ? data.get(1) : "";
        return scope != null && packetBridge != null && packetBridge.isUiOpen(player, scope)
            && tokenGuard.begin(player.getUniqueId(), scope, supplied);
    }

    /** 释放玩家的在途写操作槽位。 */
    public void finishWriteAction(Player player) {
        tokenGuard.finish(player.getUniqueId());
    }

    private String primaryRuntimeUiId(MenuDefinition definition) {
        List<MenuUiTarget> targets = definition.uiTargets();
        if (!targets.isEmpty()) {
            return targets.get(0).uiId();
        }
        return runtimeUiId(definition.layout());
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

