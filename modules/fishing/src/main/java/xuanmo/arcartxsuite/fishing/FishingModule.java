package xuanmo.arcartxsuite.fishing;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import xuanmo.arcartxsuite.fishing.command.FishingAdminCommand;
import xuanmo.arcartxsuite.fishing.command.FishingPlayerCommand;
import xuanmo.arcartxsuite.fishing.config.FishingModuleConfiguration;
import xuanmo.arcartxsuite.fishing.listener.FishingListener;
import xuanmo.arcartxsuite.fishing.packet.FishingPacketHandler;
import xuanmo.arcartxsuite.fishing.placeholder.FishingPlaceholderExpansion;
import xuanmo.arcartxsuite.fishing.service.FishingService;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;
import xuanmo.arcartxsuite.fishing.storage.JdbcFishingRepository;

public final class FishingModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String MINIGAME_UI_RESOURCE = "arcartx/ui/fishing_minigame.yml";
    private static final String MINIGAME_UI_FILE = "ui/fishing_minigame.yml";
    private static final String COLLECTION_UI_RESOURCE = "arcartx/ui/fishing_collection.yml";
    private static final String COLLECTION_UI_FILE = "ui/fishing_collection.yml";

    private FishingModuleConfiguration configuration;
    private FishingService service;
    private FishingAdminCommand adminCommand;
    private String minigameUiId;
    private String collectionUiId;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("fishing")
            .name("Fishing")
            .version("1.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected int currentConfigVersion() {
        return 1;
    }

    @Override
    protected String configFileName() {
        return "ArcartXFishing.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("fishes")
            .dynamicSection("treasures")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            ValidationRule.required("fishing.replace-vanilla", ValueType.BOOLEAN),
            ValidationRule.required("fishing.minigame-tick-interval", ValueType.INT)
                .withRange(1, 20)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(MINIGAME_UI_RESOURCE, MINIGAME_UI_FILE);
        mappings.put(COLLECTION_UI_RESOURCE, COLLECTION_UI_FILE);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.ui().registerOnEnable();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXFishing.yml 配置文件缺失");
        }
        configuration = FishingModuleConfiguration.load(
            YamlConfiguration.loadConfiguration(configFile), context.dataFolder(), context.logger());
    }

    @Override
    protected void startService() throws Exception {
        File moduleDataFolder = context.dataFolder();
        JdbcFishingRepository repo = new JdbcFishingRepository(
            moduleDataFolder, configuration.storage(), context.logger());

        // 注册 UI 并获取 UI ID
        UiBinding binding1 = registerModuleUi(
            MINIGAME_UI_FILE,
            configuration.ui().minigameId(),
            configuration.ui().registerOnEnable()
        );
        if (binding1.registeredUiId() != null) {
            minigameUiId = binding1.registeredUiId();
        } else {
            minigameUiId = configuration.ui().minigameId();
        }

        UiBinding binding2 = registerModuleUi(
            COLLECTION_UI_FILE,
            configuration.ui().collectionId(),
            configuration.ui().registerOnEnable()
        );
        if (binding2.registeredUiId() != null) {
            collectionUiId = binding2.registeredUiId();
        } else {
            collectionUiId = configuration.ui().collectionId();
        }

        service = new FishingService(
            context.plugin(), configuration, repo,
            context.packetBridge(), context.logger(), minigameUiId);
        service.setMessageProvider(messages());
        service.setEventBusProvider(() -> context.getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        service.setSignalProvider(() -> context.getCapability(
            xuanmo.arcartxsuite.api.capability.SignalDispatchable.class));
        service.setCurrencyProvider(() -> context.currencyManager());
        service.setTitleProvider(() -> context.getCapability(
            xuanmo.arcartxsuite.api.capability.TitleGrantable.class));
        service.setMailProvider(() -> context.getCapability(
            xuanmo.arcartxsuite.api.capability.MailDispatchable.class));
        service.start();

        context.registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "fishing"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                    @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                return repo.migrateData(target, overwrite);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repo.getDescriptor();
            }
        });

        context.registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "fishing"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return repo.deletePlayerData(playerUuid); }
                catch (Exception e) { context.logger().warning("Fishing purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return repo.deleteAllPlayerData(); }
                catch (Exception e) { context.logger().warning("Fishing purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        adminCommand = new FishingAdminCommand(() -> service, this::msg);
        context.logger().fine("Fishing 模块已载入 | 鱼种=" + configuration.fishes().size()
            + " | 替换原版=" + configuration.fishing().replaceVanilla());
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
    protected @NotNull List<org.bukkit.event.Listener> createListeners() {
        return service != null ? List.of(new FishingListener(service)) : List.of();
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        return Map.of("fishing", new FishingPlayerCommand(
            () -> context.packetBridge(),
            () -> collectionUiId,
            () -> service
        ));
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new FishingPlaceholderExpansion(context.plugin(), () -> service);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        if (service == null) return null;
        return new FishingPacketHandler(player -> {
            if (service == null) return null;
            return service.getActiveMinigame(player.getUniqueId());
        });
    }

    // ─── ModuleCommandHandler ─────────────────────────────────

    @Override public String commandId() { return "fishing"; }

    @Override public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "stats", "reload");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null && adminCommand.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }

    private String msg(String key, Object... args) {
        MessageProvider mp = messages();
        if (mp == null) return key;
        String prefix = mp.get("prefix");
        return prefix + mp.get(key, args);
    }
}
