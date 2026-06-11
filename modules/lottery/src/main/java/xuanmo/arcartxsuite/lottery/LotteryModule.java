package xuanmo.arcartxsuite.lottery;

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
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.lottery.command.LotteryAdminCommand;
import xuanmo.arcartxsuite.lottery.command.LotteryPlayerCommand;
import xuanmo.arcartxsuite.lottery.config.LotteryModuleConfiguration;
import xuanmo.arcartxsuite.lottery.storage.JdbcLotteryRepository;

public final class LotteryModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String GACHA_UI_RESOURCE = "arcartx/ui/lottery_gacha.yml";
    private static final String GACHA_UI_FILE = "ui/lottery_gacha.yml";
    private static final String CASE_UI_RESOURCE = "arcartx/ui/lottery_case.yml";
    private static final String CASE_UI_FILE = "ui/lottery_case.yml";

    private LotteryModuleConfiguration configuration;
    private LotteryService service;
    private LotteryAdminCommand adminCommand;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("lottery")
            .name("Lottery")
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
        return "ArcartXLottery.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
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
            ValidationRule.required("pools-directory", ValueType.STRING)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(GACHA_UI_RESOURCE, GACHA_UI_FILE);
        mappings.put(CASE_UI_RESOURCE, CASE_UI_FILE);
        return mappings;
    }

    @Override
    protected boolean overwriteUiFiles() {
        return false;
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXLottery.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String poolsDir = yaml.getString("pools-directory", "lottery/pools");
        File poolsDirectory = new File(context.dataFolder(), poolsDir);
        ensurePoolDefaults(poolsDir);
        configuration = LotteryModuleConfiguration.load(yaml, context.dataFolder(), context.logger());
    }

    private void ensurePoolDefaults(String poolsRelative) {
        File poolsDir = new File(context.dataFolder(), poolsRelative);
        if (!poolsDir.exists()) {
            poolsDir.mkdirs();
        }
        for (String poolFile : new String[]{"character_event_1.yml", "weapon_event_1.yml", "default_weapon_case.yml"}) {
            File target = new File(poolsDir, poolFile);
            if (!target.exists()) {
                context.exportResource("lottery/pools/" + poolFile, target, false);
            }
        }
    }

    @Override
    protected void startService() throws Exception {
        File moduleDataFolder = context.dataFolder();
        JdbcLotteryRepository repo = new JdbcLotteryRepository(
            moduleDataFolder, configuration.storage(), context.logger());

        CurrencyBridgeAPI currencyManager = context.currencyManager();
        ItemSourceRegistry itemSourceRegistry = context.itemSourceRegistry();

        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailSupplier
            = () -> context.getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class);

        service = new LotteryService(
            context.plugin(), configuration, repo,
            currencyManager, itemSourceRegistry, mailSupplier, context.logger());
        service.setMessageProvider(messages());
        service.start();

        context.registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "lottery"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                    @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                return repo.migrateData(target, overwrite);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repo.getDescriptor();
            }
        });

        adminCommand = new LotteryAdminCommand(() -> service, messages());
        context.logger().fine("Lottery 模块已载入 | 奖池=" + configuration.pools().size());
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
        return Map.of("lottery", new LotteryPlayerCommand(() -> service, messages()));
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new LotteryPlaceholderExpansion(context.plugin(), () -> service);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return null;
    }

    // ─── ModuleCommandHandler (/axs lottery) ──────────────────

    @Override public String commandId() { return "lottery"; }

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

    private String msg(String key, Object... args) {
        MessageProvider mp = messages();
        if (mp == null) return key;
        String prefix = mp.get("prefix");
        return prefix + mp.get(key, args);
    }
}
