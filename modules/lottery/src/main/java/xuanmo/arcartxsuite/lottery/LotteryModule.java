package xuanmo.arcartxsuite.lottery;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.DatabaseMigratable;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.lottery.command.LotteryAdminCommand;
import xuanmo.arcartxsuite.lottery.command.LotteryPlayerCommand;
import xuanmo.arcartxsuite.lottery.config.LotteryModuleConfiguration;
import xuanmo.arcartxsuite.lottery.packet.LotteryPacketHandler;
import xuanmo.arcartxsuite.lottery.storage.JdbcLotteryRepository;

/**
 * Lottery 模块主类。
 * <p>
 * 继承 {@link AbstractAXSModule}，负责配置加载、服务启动/停止、
 * 命令绑定、PlaceholderAPI 注册、客户端包处理器创建以及
 * 数据库迁移与玩家数据清除能力的注册。
 */
public final class LotteryModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final String GACHA_UI_RESOURCE = "arcartx/ui/lottery_gacha.yml";
    private static final String GACHA_UI_FILE = "ui/lottery_gacha.yml";
    private static final String CASE_UI_RESOURCE = "arcartx/ui/lottery_case.yml";
    private static final String CASE_UI_FILE = "ui/lottery_case.yml";

    private LotteryModuleConfiguration configuration;
    private LotteryService service;
    private LotteryAdminCommand adminCommand;
    private JdbcLotteryRepository repository;
    private LotteryPacketHandler packetHandler;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("lottery")
            .name("Lottery")
            .version("1.3.3")
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
            .dynamicSection("shared-pity-groups")
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
        File poolsDirectory = new File(dataFolder, poolsDir);
        ensurePoolDefaults(poolsDir);
        configuration = LotteryModuleConfiguration.load(yaml, dataFolder, logger);
    }

    private void ensurePoolDefaults(String poolsRelative) {
        File poolsDir = new File(dataFolder, poolsRelative);
        if (!poolsDir.exists()) {
            poolsDir.mkdirs();
        }
        File[] existing = poolsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        for (String poolFile : new String[]{"character_event_1.yml", "weapon_event_1.yml", "default_weapon_case.yml"}) {
            File target = new File(poolsDir, poolFile);
            if (!target.exists()) {
                exportResource("lottery/pools/" + poolFile, target, false);
            }
        }
    }

    @Override
    protected void startService() throws Exception {
        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        JdbcLotteryRepository repo;
        if (configuration.storage().hasOverride()) {
            // 旧路径：模块自建连接池
            repo = new JdbcLotteryRepository(dataFolder, configuration.storage(), logger);
        } else {
            // 新路径：共享本体数据源
            repo = new JdbcLotteryRepository(storageManager, "lottery",
                configuration.storage().tablePrefix(),
                configuration.storage().sqliteFileName(), logger);
        }
        repo.initialize();
        repository = repo;

        java.util.function.Supplier<xuanmo.arcartxsuite.api.capability.MailDispatchable> mailSupplier
            = () -> getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class);

        service = new LotteryService(
            plugin, configuration, repo,
            currencyManager, itemSourceRegistry, mailSupplier, logger);
        service.setMessageProvider(messages());
        service.start();

        // UI 注册
        registerModuleUi(GACHA_UI_FILE, "AXS:lottery_gacha", true);
        registerModuleUi(CASE_UI_FILE, "AXS:lottery_case", true);

        registerCapability(DatabaseMigratable.class, new DatabaseMigratable() {
            @Override public @NotNull String moduleId() { return "lottery"; }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                    @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                return repo.migrateData(target, overwrite);
            }
            @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                return repo.getDescriptor();
            }
        });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "lottery"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return repo.deletePlayerData(playerUuid); }
                catch (Exception e) { logger.warning("Lottery purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return repo.deleteAllPlayerData(); }
                catch (Exception e) { logger.warning("Lottery purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        adminCommand = new LotteryAdminCommand(() -> service, messages(), () -> {
            try {
                onReload();
            } catch (Exception exception) {
                throw new IllegalStateException("Lottery reload failed", exception);
            }
        });
        logger.fine("Lottery 模块已载入 | 奖池=" + configuration.pools().size());
    }

    @Override
    protected @NotNull List<Listener> createListeners() {
        return List.of(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                if (service != null) {
                    service.claimPending(event.getPlayer());
                }
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                if (service != null) {
                    service.removePlayerLock(event.getPlayer().getUniqueId());
                }
                if (packetHandler != null) {
                    packetHandler.onPlayerQuit(event.getPlayer().getUniqueId());
                }
            }
        });
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (repository != null) {
            repository.shutdown();
            repository = null;
        }
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        return Map.of("lottery", new LotteryPlayerCommand(() -> service, messages()));
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new LotteryPlaceholderExpansion(plugin, () -> service);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        if (service == null || packetBridge == null || !packetBridge.isAvailable()) {
            return null;
        }
        xuanmo.arcartxsuite.api.icon.IconResolver iconResolver =
            new xuanmo.arcartxsuite.api.icon.IconResolver(itemStackBridge, itemSourceRegistry);
        packetHandler = new LotteryPacketHandler(service, packetBridge, iconResolver, plugin);
        return packetHandler;
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

