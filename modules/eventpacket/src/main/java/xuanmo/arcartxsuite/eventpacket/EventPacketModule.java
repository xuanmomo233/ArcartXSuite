package xuanmo.arcartxsuite.eventpacket;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.account.AccountType;
import xuanmo.arcartxsuite.api.account.AccountTypeService;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.eventpacket.command.EventPacketAdminCommand;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.QuestGpsNavigable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.eventpacket.config.EventPacketContext;
import xuanmo.arcartxsuite.eventpacket.config.PluginConfiguration;
import xuanmo.arcartxsuite.eventpacket.listener.ChemdahEventBridge;
import xuanmo.arcartxsuite.eventpacket.listener.PlayerEventPacketListener;
import xuanmo.arcartxsuite.eventpacket.service.EntityCleanupService;
import xuanmo.arcartxsuite.eventpacket.service.EventPacketDispatchService;
import xuanmo.arcartxsuite.eventpacket.service.PapiWatcherService;
import xuanmo.arcartxsuite.eventpacket.service.ScheduledCommandService;
import xuanmo.arcartxsuite.eventpacket.storage.EventPacketRepository;
import xuanmo.arcartxsuite.eventpacket.storage.JdbcEventPacketRepository;

public final class EventPacketModule extends AbstractAXSModule implements ModuleCommandHandler {

    private EventPacketAdminCommand adminCommand;

    private PluginConfiguration configuration;
    private EventPacketRepository repository;
    private EventPacketDispatchService dispatchService;
    private PapiWatcherService watcherService;
    private PlayerEventPacketListener listener;
    private EntityCleanupService cleanupService;
    private ScheduledCommandService scheduledCommandService;
    private ChemdahEventBridge chemdahBridge;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("eventpacket")
            .name("EventPacket")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXEventPacket.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // scheduled-commands 允许用户新增自定义定时任务
        return SyncPolicy.builder()
            .dynamicSection("scheduled-commands")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // storage.mode 必须是 sqlite 或 mysql
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            // pool-size 范围 1-100
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 100),
            // 包超时时间（毫秒）
            ValidationRule.of("packet.timeout-ms", ValueType.INT)
                .withRange(100, 30000),
            // PAPI检测间隔（ticks）
            ValidationRule.of("papi.watcher-interval-ticks", ValueType.INT)
                .withRange(1, null),
            // 事件优先级验证
            ValidationRule.of("event.priority", ValueType.STRING)
                .withEnum(Set.of("LOWEST", "LOW", "NORMAL", "HIGH", "HIGHEST", "MONITOR"))
        );
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXEventPacket.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String rulesDirRelative = yaml.getString("rules-directory", "rules");
        File rulesDirectory = new File(dataFolder, rulesDirRelative);
        ensureRuleDefaults(rulesDirRelative);
        File presetsDir = new File(pluginDataFolder, "eventpacket/packet-command-presets");
        configuration = PluginConfiguration.load(yaml, logger, presetsDir, rulesDirectory);
    }

    private void ensureRuleDefaults(String rulesRelative) {
        File rulesDir = new File(dataFolder, rulesRelative);
        if (!rulesDir.exists()) {
            rulesDir.mkdirs();
        }
        File[] existing = rulesDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        File target = new File(rulesDir, "examples.yml");
        if (!target.exists()) {
            exportResource("rules/examples.yml", target, false);
        }
    }

    @Override
    protected void startService() throws Exception {

        repository = new JdbcEventPacketRepository(
            dataFolder,
            configuration.storage(), logger
        );
        repository.initialize();

        dispatchService = new EventPacketDispatchService(
            logger,
            packetGuard,
            packetBridge,
            () -> configuration,
            () -> getCapability(QuestGpsNavigable.class),
            () -> getCapability(TitleGrantable.class),
            () -> getCapability(SubtitlePlayable.class),
            () -> getCapability(ChatCardSendable.class),
            () -> getCapability(MailDispatchable.class),
            () -> getCapability(QQBotBroadcastable.class),
            () -> "",
            () -> repository
        );

        // 注入账号类型解析器，使规则可使用 {account_type} / {account_type_display} / {account_premium}
        EventPacketContext.setAccountInfoResolver((uuidStr, name) -> {
            UUID uuid = null;
            if (uuidStr != null && !uuidStr.isBlank()) {
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException ignored) {
                    // uuid 字符串非法时仅按玩家名解析
                }
            }
            AccountType type = accountTypeService.resolve(uuid, name);
            return new String[]{type.id(), type.displayName(), Boolean.toString(type.premium())};
        });

        boolean placeholderApiAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        boolean hasMobKillRules = configuration.rules().stream()
            .anyMatch(rule -> rule.enabled()
                && rule.trigger() == xuanmo.arcartxsuite.eventpacket.config.EventPacketTrigger.MOB_KILL_COUNT);
        boolean hasScriptTriggers = configuration.rules().stream()
            .anyMatch(rule -> rule.enabled() && rule.isScriptTrigger());
        int papiPacketCount = configuration.papiPacketCount();
        if (papiPacketCount > 0 && !placeholderApiAvailable) {
            logger.warning(
                "EventPacket 模块检测到 " + papiPacketCount
                    + " 个 PAPI 触发配置，但当前未安装 PlaceholderAPI，这部分触发器不会生效。"
            );
        }
        if ((papiPacketCount > 0 && placeholderApiAvailable) || hasMobKillRules || hasScriptTriggers) {
            watcherService = new PapiWatcherService(
                plugin, logger, dispatchService, configuration, packetBridge, repository,
                placeholderResolver, scriptConditionEvaluator
            );
            watcherService.start();
        }

        registerCapability(SignalDispatchable.class, (signal, subject, variables) ->
            dispatchService.dispatchSignal(signal, subject, variables));

        adminCommand = new EventPacketAdminCommand(() -> dispatchService, () -> cleanupService, messages());
        listener = new PlayerEventPacketListener(dispatchService, watcherService);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // 启动实体清理服务
        cleanupService = new EntityCleanupService(
            plugin, logger, configuration.entityCleanup());
        cleanupService.start();

        // 启动定时命令服务
        scheduledCommandService = new ScheduledCommandService(
            plugin, logger, configuration.scheduledCommands());
        scheduledCommandService.start();

        JdbcEventPacketRepository epRepo = (JdbcEventPacketRepository) repository;
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "eventpacket"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return epRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("EventPacket purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return epRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("EventPacket purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "eventpacket"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return epRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return epRepo.getDescriptor();
                }
            });

        // 注册 Chemdah 事件桥接
        boolean chemdahAvailable = Bukkit.getPluginManager().isPluginEnabled("Chemdah");
        boolean hasChemdahRules = configuration.rules().stream()
            .anyMatch(rule -> rule.enabled() && rule.trigger() != null && rule.trigger().chemdahTrigger());
        if (chemdahAvailable && hasChemdahRules) {
            try {
                chemdahBridge = new ChemdahEventBridge(plugin, logger, dispatchService);
                chemdahBridge.register();
            } catch (Exception e) {
                logger.warning("EventPacket Chemdah 桥接初始化失败: " + e.getMessage());
                chemdahBridge = null;
            }
        } else if (hasChemdahRules && !chemdahAvailable) {
            logger.warning(
                "EventPacket 模块检测到 Chemdah 触发配置，但当前未安装 Chemdah，这部分触发器不会生效。"
            );
        }

        logger.fine(
            "EventPacket 模块已载入，rules=" + configuration.enabledRuleCount()
                + "/" + configuration.rules().size()
                + " | client-presets=" + configuration.clientPacketPresetCount()
        );
    }

    @Override
    protected void stopService() {
        EventPacketContext.setAccountInfoResolver(null);
        if (chemdahBridge != null) {
            chemdahBridge.unregister();
            chemdahBridge = null;
        }
        if (scheduledCommandService != null) {
            scheduledCommandService.shutdown();
            scheduledCommandService = null;
        }
        if (cleanupService != null) {
            cleanupService.shutdown();
            cleanupService = null;
        }
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (watcherService != null) {
            watcherService.shutdown();
            watcherService = null;
        }
        if (dispatchService != null) {
            dispatchService.shutdown();
            dispatchService = null;
        }
        if (repository != null) {
            repository.close();
            repository = null;
        }
        configuration = null;
    }

    @Override
    @Nullable
    protected ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) -> {
            if (dispatchService == null || configuration == null) {
                return false;
            }
            String configuredPacketId = configuration.clientPacketId();
            if (configuredPacketId.isBlank() || !configuredPacketId.equalsIgnoreCase(packetId)) {
                return false;
            }
            String presetId = data == null || data.isEmpty() ? "" : data.get(0);
            if (presetId.isBlank()) {
                return false;
            }
            return dispatchService.dispatchClientPacket(packetId, presetId, player);
        };
    }

    @Override
    protected int packetHandlerPriority() {
        return 100;
    }

    public EventPacketDispatchService getDispatchService() {
        return dispatchService;
    }

    public PluginConfiguration getConfiguration() {
        return configuration;
    }

    @Override public String commandId() { return "eventpacket"; }
    @Override public List<String> actions() { return adminCommand != null ? adminCommand.actions() : List.of("help", "status", "reload"); }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onCommand(sender, label, args) : false;
    }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return adminCommand != null ? adminCommand.onTabComplete(sender, args) : null;
    }
}




