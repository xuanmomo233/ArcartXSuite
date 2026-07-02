package xuanmo.arcartxsuite.battlepass;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.battlepass.command.BattlePassAdminCommand;
import xuanmo.arcartxsuite.battlepass.command.BattlePassPlayerCommand;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.packet.BattlePassPacketHandler;
import xuanmo.arcartxsuite.battlepass.placeholder.BattlePassPlaceholderExpansion;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;
import xuanmo.arcartxsuite.battlepass.storage.JdbcBattlePassRepository;

public final class BattlePassModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String MAIN_UI_RESOURCE_PATH = "arcartx/ui/battlepass_main.yml";
    private static final String MAIN_UI_FILE_PATH = "ui/battlepass_main.yml";
    private static final String TASKS_UI_RESOURCE_PATH = "arcartx/ui/battlepass_tasks.yml";
    private static final String TASKS_UI_FILE_PATH = "ui/battlepass_tasks.yml";

    private BattlePassModuleConfiguration configuration;
    private BattlePassService service;
    private BattlePassAdminCommand adminCommand;
    private BattlePassPacketHandler packetHandler;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("battlepass")
            .name("BattlePass")
            .version("1.1.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected int currentConfigVersion() {
        return 2;
    }

    @Override
    protected String configFileName() {
        return "ArcartXBattlePass.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("tasks")
            .dynamicSection("rewards")
            .dynamicSection("messages")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            ValidationRule.required("season.season-id", ValueType.STRING),
            ValidationRule.required("season.max-level", ValueType.INT)
                .withRange(1, 10000),
            ValidationRule.required("season.xp-per-level", ValueType.INT)
                .withRange(1, 1000000)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MAIN_UI_RESOURCE_PATH, MAIN_UI_FILE_PATH);
        mappings.put(TASKS_UI_RESOURCE_PATH, TASKS_UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().registerOnEnable();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXBattlePass.yml 配置文件缺失");
        }
        File dataFolder = configFile.getParentFile();
        // 首次安装时导出默认任务文件
        ensureTaskDefaults(dataFolder);
        configuration = BattlePassModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), dataFolder, logger);
    }

    private void ensureTaskDefaults(File dataFolder) {
        File tasksDir = new File(dataFolder, "tasks");
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
        }
        File[] existing = tasksDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        for (String taskFile : new String[]{"daily.yml", "weekly.yml", "season.yml"}) {
            File target = new File(tasksDir, taskFile);
            if (!target.exists()) {
                exportResource("tasks/" + taskFile, target, false);
            }
        }
    }

    @Override
    protected void startService() throws Exception {
        File moduleDataFolder = dataFolder;
        JdbcBattlePassRepository repo = new JdbcBattlePassRepository(
            moduleDataFolder, configuration.storage(), logger);

        UiBinding mainBinding = registerModuleUi(MAIN_UI_FILE_PATH, configuration.ui().mainId(), true);
        UiBinding tasksBinding = registerModuleUi(TASKS_UI_FILE_PATH, configuration.ui().tasksId(), true);
        if (mainBinding.registeredUiId() == null || tasksBinding.registeredUiId() == null) {
            throw new IllegalStateException("BattlePass UI 注册失败");
        }

        service = new BattlePassService(plugin, configuration, repo, logger);
        service.setEventBusProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.start();

        registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "battlepass"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                    @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                return repo.migrateData(target, overwrite);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repo.getDescriptor();
            }
        });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "battlepass"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return repo.deletePlayerData(playerUuid); }
                catch (Exception e) { logger.warning("BattlePass purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return repo.deleteAllPlayerData(); }
                catch (Exception e) { logger.warning("BattlePass purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        adminCommand = new BattlePassAdminCommand(() -> service, this::msg);
        logger.fine("BattlePass 模块已载入 | 赛季=" + configuration.season().seasonId()
            + " | 跨服=" + (configuration.crossServer().enabled() ? "ON" : "OFF"));
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
        BattlePassPlayerCommand cmd = new BattlePassPlayerCommand(
            () -> packetHandler, messages());
        Map<String, TabExecutor> map = new LinkedHashMap<>();
        map.put("bp", cmd);
        map.put("battlepass", cmd);
        return map;
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new BattlePassPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        if (service == null) return null;
        String mainUiId = getModuleUiId(MAIN_UI_FILE_PATH);
        String tasksUiId = getModuleUiId(TASKS_UI_FILE_PATH);
        packetHandler = new BattlePassPacketHandler(
            plugin,
            packetBridge,
            packetGuard,
            service,
            mainUiId != null ? mainUiId : configuration.ui().mainId(),
            tasksUiId != null ? tasksUiId : configuration.ui().tasksId()
        );
        return packetHandler;
    }

    // ─── ModuleCommandHandler（/axs battlepass）───────────────────

    @Override public String commandId() { return "battlepass"; }

    @Override public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }

    // ─── 辅助方法 ────────────────────────────────────────────

    private String msg(String key, Object... args) {
        MessageProvider mp = messages();
        if (mp == null) return key;
        String prefix = mp.get("prefix");
        return prefix + mp.get(key, args);
    }
}




