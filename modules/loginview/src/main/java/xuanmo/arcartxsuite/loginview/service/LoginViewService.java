package xuanmo.arcartxsuite.loginview.service;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.account.AccountType;
import xuanmo.arcartxsuite.api.account.AccountTypeService;
import xuanmo.arcartxsuite.api.capability.QqBindCapable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.loginview.auth.AuthMeBridge;
import xuanmo.arcartxsuite.loginview.config.LoginViewModuleConfiguration;
import xuanmo.arcartxsuite.loginview.config.LoginViewModuleConfiguration.AuthMode;
import xuanmo.arcartxsuite.loginview.migration.AuthMeMigrationService;
import xuanmo.arcartxsuite.loginview.migration.AuthMeMigrationService.MigrationResult;
import xuanmo.arcartxsuite.loginview.security.LoginViewPasswordHasher;
import xuanmo.arcartxsuite.loginview.storage.LoginViewAccount;
import xuanmo.arcartxsuite.loginview.storage.LoginViewRepository;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;

public final class LoginViewService implements Listener {

    public static final String UI_RESOURCE_PATH = "arcartx/ui/login_view.yml";
    public static final String UI_FILE_PATH = "ui/login_view.yml";
    public static final String MENU_UI_RESOURCE_PATH = "arcartx/ui/login_view_menu.yml";
    public static final String MENU_UI_FILE_PATH = "ui/login_view_menu.yml";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final LoginViewModuleConfiguration configuration;
    private final LoginViewRepository repository;
    private final ArcartXPacketBridge packetBridge;
    private final PacketGuardAPI packetGuard;
    private final Supplier<SignalDispatchable> signalProvider;
    private final String uiId;
    private final LoginViewPasswordHasher passwordHasher = new LoginViewPasswordHasher();
    private final AuthMeBridge authMeBridge = new AuthMeBridge();
    private final AccountTypeService accountTypeService;
    private final Supplier<QqBindCapable> qqBindProvider;
    private final Set<UUID> authenticatedPlayers = new HashSet<>();
    private final Map<UUID, Integer> failedAttempts = new HashMap<>();
    private final Set<String> allowedCommandPrefixes = new HashSet<>();
    private final Set<UUID> loginViewInvulnerablePlayers = new HashSet<>();
    private boolean started;

    public LoginViewService(
        JavaPlugin plugin,
        LoginViewModuleConfiguration configuration,
        LoginViewRepository repository,
        ArcartXPacketBridge packetBridge,
        PacketGuardAPI packetGuard,
        Supplier<SignalDispatchable> signalProvider,
        AccountTypeService accountTypeService,
        Supplier<QqBindCapable> qqBindProvider,
        String uiId
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.signalProvider = signalProvider;
        this.accountTypeService = accountTypeService;
        this.qqBindProvider = qqBindProvider;
        this.uiId = uiId;
        for (String command : configuration.security().allowCommandsPrefix().split(",")) {
            String normalized = command.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                allowedCommandPrefixes.add(normalized);
            }
        }
    }

    public void start() throws SQLException {
        repository.initialize();
        if (configuration.authMode() == AuthMode.AUTHME && !authMeBridge.initialize()) {
            plugin.getLogger().warning("LoginView 配置为 AuthMe 兼容模式，但未检测到可用 AuthMe 插件，模块不加载。");
            repository.close();
            return;
        }
        boolean authlibAgentLoaded = accountTypeService.isAuthlibInjectorLoaded();
        if (configuration.premiumBypass().enabled()) {
            if (!authlibAgentLoaded) {
                plugin.getLogger().warning("LoginView premium-bypass 已启用，但未检测到 authlib-injector，正版/LittleSkin 免登录当前不可用。");
            } else {
                plugin.getLogger().info("LoginView premium-bypass: authlib-injector 已检测到，正版/LittleSkin 免登录已启用。");
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        started = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduleOpen(player);
        }
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            packetBridge.closeUiUnsafe(player, uiId);
            if (loginViewInvulnerablePlayers.remove(player.getUniqueId())) {
                player.setInvulnerable(false);
            }
        }
        authenticatedPlayers.clear();
        failedAttempts.clear();
        loginViewInvulnerablePlayers.clear();
        repository.close();
        started = false;
    }

    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (!configuration.ui().packetId().equalsIgnoreCase(packetId)) {
            return false;
        }
        String action = data.isEmpty() ? "" : data.get(0).trim().toLowerCase(Locale.ROOT);
        if (packetGuard != null && !packetGuard.allow(player, "loginview", action, configuration.debug())) {
            return true;
        }
        switch (action) {
            case "login" -> handleLogin(player, value(data, 1));
            case "register" -> handleRegister(player, value(data, 1), value(data, 2));
            case "change_password" -> handleChangePassword(player, value(data, 1), value(data, 2), value(data, 3));
            case "bypass_enter" -> handleBypassEnter(player);
            case "bind_code" -> handleBindCode(player, value(data, 1));
            case "refresh" -> openFor(player, true);
            default -> sendResult(player, color("&c未知 LoginView 操作。"), false);
        }
        return true;
    }

    public void handleClientInitialized(Player player) {
        scheduleOpen(player);
    }

    public void openFor(Player player) {
        openFor(player, false);
    }

    public void openFor(Player player, boolean force) {
        if (player == null || !player.isOnline()) {
            return;
        }
        boolean authenticated = isAuthenticated(player);
        if (authenticated && !force) {
            return;
        }

        AccountType type = accountType(player);
        boolean premium = type.premium();
        boolean qqBound = isQqBound(player);
        boolean registered = isRegistered(player);

        packetBridge.openUiUnsafe(player, uiId);

        if (!isAuthenticated(player)) {
            player.setInvulnerable(true);
            loginViewInvulnerablePlayers.add(player.getUniqueId());
        }

        if (premium) {
            // 微软正版 / LittleSkin：显示 bypass 类视图
            boolean requireBind = switch (type) {
                case MICROSOFT -> configuration.qqBinding().microsoftRequireBind();
                case LITTLESKIN -> configuration.qqBinding().littleskinRequireBind();
                default -> false;
            };
            // 若该账号类型不要求绑定，逻辑视为已绑定，前端直接显示「进入服务器」
            boolean effectiveQqBound = qqBound || !requireBind;
            String prompt = effectiveQqBound ? null : String.join("\n", configuration.qqBinding().bindPrompt());
            packetBridge.sendPacket(player, uiId, "init",
                buildInitPayload(player, true, false, true, effectiveQqBound, prompt));
        } else {
            // 离线玩家（若认证层未拦住则 fallback 到密码登录）
            packetBridge.sendPacket(player, uiId, "init",
                buildInitPayload(player, registered, authenticated && force, false, false,
                    color("&e请先完成登录验证")));
        }
    }

    public MigrationResult migrateAuthMe(CommandSender sender, boolean dryRun) throws SQLException {
        AuthMeMigrationService migrationService = new AuthMeMigrationService(configuration.migration(), repository);
        return migrationService.migrate(sender, dryRun);
    }

    public int accountCount() {
        try {
            return repository.countAccounts();
        } catch (SQLException exception) {
            return 0;
        }
    }

    public boolean authMeAvailable() {
        return configuration.authMode() == AuthMode.AUTHME && authMeBridge.available();
    }

    public String uiId() {
        return uiId;
    }

    public AccountType accountType(OfflinePlayer player) {
        return accountTypeService.resolve(player);
    }

    private void handleLogin(Player player, String password) {
        if (!validatePasswordShape(player, password, false)) {
            return;
        }
        try {
            if (configuration.authMode() == AuthMode.AUTHME) {
                if (!authMeBridge.available()) {
                    sendResult(player, color(configuration.messages().authmeUnavailable()), false);
                    return;
                }
                if (!authMeBridge.isRegistered(player.getName())) {
                    sendResult(player, color(configuration.messages().notRegistered()), false);
                    return;
                }
                if (!authMeBridge.checkPassword(player.getName(), password)) {
                    recordFailure(player);
                    return;
                }
                authMeBridge.forceLogin(player);
                completeLogin(player, color(configuration.messages().loginSuccess()));
                dispatchLoginSignal(player, "login_success");
                return;
            }

            LoginViewAccount account = repository.find(player.getName()).orElse(null);
            if (account == null) {
                sendResult(player, color(configuration.messages().notRegistered()), false);
                return;
            }
            if (!passwordHasher.verify(password, account.passwordHash(), account.hashAlgorithm())) {
                recordFailure(player);
                return;
            }
            authenticatedPlayers.add(player.getUniqueId());
            repository.updateLogin(player.getName(), player);
            if (account.migrated() && configuration.security().rehashMigratedPasswordOnLogin()) {
                repository.updatePassword(player.getName(), passwordHasher.hash(password), LoginViewPasswordHasher.AXS_ALGORITHM);
            }
            completeLogin(player, color(configuration.messages().loginSuccess()));
            dispatchLoginSignal(player, "login_success");
        } catch (SQLException exception) {
            sendResult(player, color("&c登录失败: " + exception.getMessage()), false);
        }
    }

    private void handleRegister(Player player, String password, String confirmPassword) {
        if (!validatePasswordShape(player, password, true)) {
            return;
        }
        if (!password.equals(confirmPassword)) {
            sendResult(player, color(configuration.messages().passwordMismatch()), false);
            return;
        }
        try {
            if (configuration.authMode() == AuthMode.AUTHME) {
                if (!authMeBridge.available()) {
                    sendResult(player, color(configuration.messages().authmeUnavailable()), false);
                    return;
                }
                if (authMeBridge.isRegistered(player.getName())) {
                    sendResult(player, color(configuration.messages().alreadyRegistered()), false);
                    return;
                }
                if (!authMeBridge.registerPlayer(player.getName(), password)) {
                    sendResult(player, color("&cAuthMe 注册失败。"), false);
                    return;
                }
                authMeBridge.forceLogin(player);
                completeLogin(player, color(configuration.messages().registerSuccess()));
                dispatchLoginSignal(player, "first_register");
                return;
            }

            if (repository.exists(player.getName())) {
                sendResult(player, color(configuration.messages().alreadyRegistered()), false);
                return;
            }
            repository.create(player.getName(), passwordHasher.hash(password), LoginViewPasswordHasher.AXS_ALGORITHM, player);
            authenticatedPlayers.add(player.getUniqueId());
            completeLogin(player, color(configuration.messages().registerSuccess()));
            dispatchLoginSignal(player, "first_register");
        } catch (SQLException exception) {
            sendResult(player, color("&c注册失败: " + exception.getMessage()), false);
        }
    }

    private void handleBypassEnter(Player player) {
        AccountType accountType = accountType(player);
        if (!accountType.premium()) {
            sendResult(player, color("&c你不是正版/LittleSkin 认证玩家，无法免密登录。"), false);
            return;
        }
        // QQ 绑定检查：按账号类型分别判断是否要求绑定
        boolean requireBind = switch (accountType) {
            case MICROSOFT -> configuration.qqBinding().microsoftRequireBind();
            case LITTLESKIN -> configuration.qqBinding().littleskinRequireBind();
            default -> false;
        };
        if (requireBind && !isQqBound(player)) {
            sendResult(player, color("&c请先完成 QQ 绑定才能进入服务器。"), false);
            return;
        }
        authenticatedPlayers.add(player.getUniqueId());
        completeLogin(player, color(configuration.premiumBypass().message()));
        dispatchLoginSignal(player, "premium_bypass");
    }

    private void handleBindCode(Player player, String code) {
        if (code == null || code.isBlank()) {
            sendResult(player, color("&c请输入绑定验证码。"), false);
            return;
        }
        QqBindCapable bindService = qqBindProvider == null ? null : qqBindProvider.get();
        if (bindService == null) {
            sendResult(player, color("&cQQ 绑定服务当前不可用。"), false);
            return;
        }
        QqBindCapable.BindResult result = bindService.confirmBind(player, code);
        if (result.success()) {
            sendResult(player, color("&a" + result.message()), true);
            // 绑定成功后刷新面板（切换为已绑定状态）
            Bukkit.getScheduler().runTaskLater(plugin, () -> openFor(player, true), 5L);
        } else {
            sendResult(player, color("&c" + result.message()), false);
        }
    }

    private boolean isQqBound(Player player) {
        QqBindCapable bindService = qqBindProvider == null ? null : qqBindProvider.get();
        return bindService != null && bindService.isBound(player.getUniqueId());
    }

    private void handleChangePassword(Player player, String oldPassword, String newPassword, String confirmPassword) {
        if (!validatePasswordShape(player, newPassword, true)) {
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            sendResult(player, color(configuration.messages().passwordMismatch()), false);
            return;
        }
        try {
            if (configuration.authMode() == AuthMode.AUTHME) {
                if (!authMeBridge.available()) {
                    sendResult(player, color(configuration.messages().authmeUnavailable()), false);
                    return;
                }
                if (!authMeBridge.isRegistered(player.getName())) {
                    sendResult(player, color(configuration.messages().notRegistered()), false);
                    return;
                }
                if (!authMeBridge.checkPassword(player.getName(), oldPassword)) {
                    sendResult(player, color(configuration.messages().wrongPassword()), false);
                    return;
                }
                authMeBridge.changePassword(player.getName(), newPassword);
                sendResult(player, color(configuration.messages().changeSuccess()), true);
                return;
            }

            LoginViewAccount account = repository.find(player.getName()).orElse(null);
            if (account == null || !passwordHasher.verify(oldPassword, account.passwordHash(), account.hashAlgorithm())) {
                sendResult(player, color(configuration.messages().wrongPassword()), false);
                return;
            }
            repository.updatePassword(player.getName(), passwordHasher.hash(newPassword), LoginViewPasswordHasher.AXS_ALGORITHM);
            sendResult(player, color(configuration.messages().changeSuccess()), true);
        } catch (SQLException exception) {
            sendResult(player, color("&c修改失败: " + exception.getMessage()), false);
        }
    }

    private boolean isRegistered(Player player) {
        try {
            return configuration.authMode() == AuthMode.AUTHME
                ? authMeBridge.available() && authMeBridge.isRegistered(player.getName())
                : repository.exists(player.getName());
        } catch (SQLException exception) {
            return false;
        }
    }

    private boolean isAuthenticated(Player player) {
        return configuration.authMode() == AuthMode.AUTHME
            ? authMeBridge.available() && authMeBridge.isAuthenticated(player)
            : authenticatedPlayers.contains(player.getUniqueId());
    }

    private void completeLogin(Player player, String message) {
        failedAttempts.remove(player.getUniqueId());
        if (loginViewInvulnerablePlayers.remove(player.getUniqueId())) {
            player.setInvulnerable(false);
        }
        sendResult(player, message, true);
        if (configuration.ui().closeOnLogin()) {
            packetBridge.sendPacket(player, uiId, "close", Map.of("message", message));
            Bukkit.getScheduler().runTaskLater(plugin, () -> packetBridge.closeUiUnsafe(player, uiId), 2L);
        }
    }

    private void recordFailure(Player player) {
        int attempts = failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
        sendResult(player, color(configuration.messages().wrongPassword()) + " (" + attempts + "/" + configuration.security().maxAttempts() + ")", false);
        if (attempts >= configuration.security().maxAttempts() && configuration.security().kickOnMaxAttempts()) {
            Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(color(configuration.messages().kicked())));
        }
    }

    private boolean validatePasswordShape(Player player, String password, boolean registering) {
        int length = password == null ? 0 : password.length();
        if (length < configuration.security().minPasswordLength()) {
            sendResult(player, color(configuration.messages().passwordTooShort()), false);
            return false;
        }
        if (length > configuration.security().maxPasswordLength()) {
            sendResult(player, color(configuration.messages().passwordTooLong()), false);
            return false;
        }
        return true;
    }

    private void scheduleOpen(Player player) {
        if (!started || player == null) {
            return;
        }
        long delay = configuration.ui().openDelayTicks();
        prewarmAccountType(player, () ->
            Bukkit.getScheduler().runTaskLater(plugin, () -> openFor(player), delay));
    }

    /**
     * 在异步线程预热账号类型缓存（委托宿主统一服务），完成后在主线程执行回调。
     * 宿主已在 PreLogin 阶段预热，此处兼顾重载/预热未完成的场景，缓存命中时几乎零开销。
     */
    private void prewarmAccountType(Player player, Runnable callback) {
        if (player == null || player.getUniqueId() == null) {
            if (callback != null) {
                callback.run();
            }
            return;
        }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            accountTypeService.resolveBlocking(playerId, playerName);
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    private Map<String, Object> buildInitPayload(Player player, boolean registered, boolean changeMode, boolean premiumBypass, boolean qqBound, String bindPrompt) {
        Server server = plugin.getServer();
        Map<String, Object> payload = new HashMap<>();
        payload.put("packetId", configuration.ui().packetId());
        String type;
        String title;
        if (premiumBypass) {
            type = qqBound ? "bypass" : "bypass_unbound";
            title = qqBound ? "欢迎回来" : "账号验证";
        } else if (changeMode) {
            type = "change";
            title = "修改密码";
        } else if (registered) {
            type = "login";
            title = configuration.messages().titleLogin();
        } else {
            type = "register";
            title = configuration.messages().titleRegister();
        }
        payload.put("type", type);
        payload.put("title", title);
        payload.put("mode", configuration.authMode().configKey());
        payload.put("registered", registered);
        payload.put("premiumBypass", premiumBypass);
        payload.put("qqBound", qqBound);
        payload.put("bindPrompt", bindPrompt == null ? "" : bindPrompt);
        payload.put("playerName", player.getName());
        payload.put("serverName", server.getName());
        payload.put("online", server.getOnlinePlayers().size());
        payload.put("maxPlayers", server.getMaxPlayers());
        payload.put("address", player.getAddress() == null ? "" : player.getAddress().getHostString());
        payload.put("time", DATE_FORMATTER.format(Instant.now()));
        payload.put("message", premiumBypass ? "" : (changeMode || registered ? "" : color(configuration.messages().notRegistered())));
        return payload;
    }

    private void sendResult(Player player, String message, boolean success) {
        packetBridge.sendPacket(player, uiId, "result", Map.of(
            "success", success,
            "message", message == null ? "" : message
        ));
        if (message != null && !message.isBlank()) {
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleOpen(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        authenticatedPlayers.remove(uuid);
        failedAttempts.remove(uuid);
        if (loginViewInvulnerablePlayers.remove(uuid)) {
            event.getPlayer().setInvulnerable(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!configuration.security().lockMovement() || isAuthenticated(event.getPlayer())) {
            return;
        }
        if (event.getTo() != null && event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!configuration.security().lockCommands() || isAuthenticated(event.getPlayer())) {
            return;
        }
        String command = event.getMessage().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
        for (String allowed : allowedCommandPrefixes) {
            if (command.startsWith(allowed)) {
                return;
            }
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(color(configuration.messages().locked()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (configuration.security().lockChat() && !isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color(configuration.messages().locked()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (!isAuthenticated(event.getPlayer())
            && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static String value(List<String> data, int index) {
        return data.size() > index ? data.get(index) : "";
    }

    private void dispatchLoginSignal(Player player, String signal) {
        if (player == null || !player.isOnline() || signal == null) {
            return;
        }
        SignalDispatchable signalDispatcher = signalProvider == null ? null : signalProvider.get();
        if (signalDispatcher == null) {
            return;
        }
        AccountType accountType = accountType(player);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("auth_mode", configuration.authMode().configKey());
        variables.put("account_type", accountType.id());
        variables.put("account_type_display", accountType.displayName());
        signalDispatcher.dispatchSignal(signal, player, variables);
    }
}
