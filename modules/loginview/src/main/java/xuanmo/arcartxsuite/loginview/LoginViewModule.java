package xuanmo.arcartxsuite.loginview;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import java.util.Set;
import xuanmo.arcartxsuite.api.ClientInitializedHandler;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.bridge.ArcartXPacketBridge;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.loginview.config.LoginViewModuleConfiguration;
import xuanmo.arcartxsuite.loginview.migration.AuthMeMigrationService.MigrationResult;
import xuanmo.arcartxsuite.loginview.placeholder.LoginViewPlaceholderExpansion;
import xuanmo.arcartxsuite.loginview.service.LoginViewService;
import xuanmo.arcartxsuite.loginview.storage.JdbcLoginViewRepository;
import xuanmo.arcartxsuite.loginview.storage.LoginViewRepository;

public final class LoginViewModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "open", "migrate-authme", "migration-commands");
    private LoginViewModuleConfiguration configuration;
    private LoginViewService service;
    private LoginViewRepository repository;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("loginview")
            .name("LoginView")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    public String commandId() {
        return "loginview";
    }

    @Override
    public List<String> actions() {
        return ACTIONS;
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    private String msg(String key, Object... args) {
        return messages().get("prefix") + messages().get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> sendHelp(sender, label, args[0]);
            case "status" -> sendStatus(sender);
            case "reload" -> reload(sender);
            case "open" -> open(sender, args);
            case "migrate-authme" -> migrateAuthMe(sender, args);
            case "migration-commands" -> sendMigrationCommands(sender);
            default -> {
                sender.sendMessage(msg("common.unknown-command", action));
                sendHelp(sender, label, args[0]);
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(ACTIONS, args[1]);
        }
        if (args.length == 3 && "open".equalsIgnoreCase(args[1])) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[2]);
        }
        if (args.length == 3 && "migrate-authme".equalsIgnoreCase(args[1])) {
            return filter(List.of("dry-run"), args[2]);
        }
        return List.of();
    }

    @Override
    protected String configFileName() {
        return "ArcartXLoginView.yml";
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // storage.mode 必须是 sqlite 或 mysql
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            // 登录视图显示持续时间（秒）
            ValidationRule.of("settings.display-duration-seconds", ValueType.INT)
                .withRange(1, 300),
            // 最大登录失败次数
            ValidationRule.of("settings.max-login-attempts", ValueType.INT)
                .withRange(1, 10),
            // 锁定时间（秒）
            ValidationRule.of("settings.lockout-duration-seconds", ValueType.INT)
                .withRange(0, 3600)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        return Map.of(
            LoginViewService.UI_RESOURCE_PATH, LoginViewService.UI_FILE_PATH,
            LoginViewService.MENU_UI_RESOURCE_PATH, LoginViewService.MENU_UI_FILE_PATH
        );
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().overwriteUiFiles();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXLoginView.yml 配置文件缺失");
        }
        configuration = LoginViewModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), context.logger());
    }

    @Override
    protected void startService() throws Exception {
        String selectedUiPath = configuration.ui().relativeUiPath();
        File uiFile = new File(context.pluginDataFolder(), selectedUiPath);
        UiBinding uiBinding = context.prepareUiBinding(
            "LoginView", configuration.ui().uiId(), configuration.ui().registerUiOnEnable(), uiFile
        );
        if (uiBinding == null) {
            throw new IllegalStateException("LoginView UI 注册失败");
        }
        recordUiBinding(selectedUiPath, uiBinding);

        ArcartXPacketBridge packetBridge = (ArcartXPacketBridge) context.packetBridge();
        PacketGuardAPI packetGuard = context.packetGuard();

        repository = new JdbcLoginViewRepository(
            context.migrateLegacyDataFile(configuration.storage().sqliteFileName()),
            configuration.storage(), context.logger());
        service = new LoginViewService(
            context.plugin(), configuration, repository, packetBridge, packetGuard,
            () -> context.getCapability(SignalDispatchable.class), context.accountTypeService(),
            () -> context.getCapability(xuanmo.arcartxsuite.api.capability.QqBindCapable.class),
            uiBinding.runtimeUiId()
        );
        service.start();

        JdbcLoginViewRepository lvRepo = (JdbcLoginViewRepository) repository;
        context.registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "loginview"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return lvRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return lvRepo.getDescriptor();
                }
            });

        context.logger().fine("LoginView 模块已载入，UI: " + uiBinding.runtimeUiId());
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (repository != null) {
            repository.close();
            repository = null;
        }
        configuration = null;
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientPacket(player, packetId, data);
    }

    @Override
    protected @Nullable ClientInitializedHandler createInitializedHandler() {
        return player -> {
            if (service != null) {
                service.openFor(player, false);
            }
        };
    }

    public LoginViewService getService() {
        return service;
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new LoginViewPlaceholderExpansion(context.plugin(), () -> service);
    }

    private void sendHelp(CommandSender sender, String label, String commandAlias) {
        String commandPrefix = "/" + label + " " + commandAlias;
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", commandPrefix));
        sender.sendMessage(msg("help.reload", commandPrefix));
        sender.sendMessage(msg("help.open", commandPrefix));
        sender.sendMessage(msg("help.migrate", commandPrefix));
        sender.sendMessage(msg("help.migration-commands", commandPrefix));
        sender.sendMessage(msg("help.setup-authlib", commandPrefix));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(msg("status.title"));
        sender.sendMessage(msg("status.service",
            service == null ? messages().get("status.service-stopped") : messages().get("status.service-running")));
        if (configuration != null) {
            sender.sendMessage(msg("status.auth-mode", configuration.authMode().configKey()));
            sender.sendMessage(msg("status.storage", configuration.storage().dialect().configKey()));
            sender.sendMessage(msg("status.ui", service == null ? configuration.ui().uiId() : service.uiId()));
            sender.sendMessage(msg("status.ui-file", configuration.ui().relativeUiPath()));
            sender.sendMessage(msg("status.lock",
                configuration.security().lockMovement(),
                configuration.security().lockChat(),
                configuration.security().lockCommands()));
        }
        if (service != null) {
            sender.sendMessage(msg("status.accounts", service.accountCount()));
            sender.sendMessage(msg("status.authme", service.authMeAvailable()));
        }
    }

    private void reload(CommandSender sender) {
        try {
            onReload();
            sender.sendMessage(msg("reload.success"));
        } catch (Exception exception) {
            sender.sendMessage(msg("reload.failed", exception.getMessage()));
        }
    }

    private void open(CommandSender sender, String[] args) {
        if (service == null) {
            sender.sendMessage(msg("common.service-down"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(msg("open.usage"));
            return;
        }
        Player player = Bukkit.getPlayerExact(args[2]);
        if (player == null || !player.isOnline()) {
            sender.sendMessage(msg("open.player-offline", args[2]));
            return;
        }
        service.openFor(player, true);
        sender.sendMessage(msg("open.success", player.getName()));
    }

    private void migrateAuthMe(CommandSender sender, String[] args) {
        if (service == null) {
            sender.sendMessage(msg("common.service-down"));
            return;
        }
        boolean dryRun = args.length >= 3 && "dry-run".equalsIgnoreCase(args[2]);
        try {
            MigrationResult result = service.migrateAuthMe(sender, dryRun);
            sender.sendMessage(msg(dryRun ? "migrate.preview" : "migrate.done",
                result.scanned(), result.imported(), result.skipped()));
        } catch (SQLException exception) {
            sender.sendMessage(msg("migrate.failed", exception.getMessage()));
        }
    }

    private void sendMigrationCommands(CommandSender sender) {
        sender.sendMessage(msg("migration.title"));
        sender.sendMessage(msg("migration.step1"));
        sender.sendMessage(msg("migration.step2"));
        sender.sendMessage(msg("migration.step3"));
        sender.sendMessage(msg("migration.step4"));
    }

    private List<String> filter(List<String> candidates, String input) {
        List<String> result = new ArrayList<>();
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
