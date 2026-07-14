package xuanmo.arcartxsuite.qqbot;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
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
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.qqbot.command.QQBotAdminCommand;
import xuanmo.arcartxsuite.qqbot.command.QQBotPlayerCommand;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.onebot.OneBotClient;
import xuanmo.arcartxsuite.qqbot.placeholder.QQBotPlaceholderExpansion;
import xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager;
import xuanmo.arcartxsuite.qqbot.service.QQBotBindService;
import xuanmo.arcartxsuite.qqbot.service.QQBotLoginGateListener;
import xuanmo.arcartxsuite.qqbot.service.QQBotService;
import xuanmo.arcartxsuite.qqbot.service.QQBotUiService;
import xuanmo.arcartxsuite.qqbot.service.QQBotWeeklyRankService;
import xuanmo.arcartxsuite.qqbot.storage.JdbcQQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;

public final class QQBotModule extends AbstractAXSModule implements ModuleCommandHandler {

    private QQBotConfiguration configuration;
    private JdbcQQBotRepository repository;
    private OneBotClient oneBotClient;
    private QQBotService service;
    private QQBotUiService uiService;
    private xuanmo.arcartxsuite.qqbot.service.QQBotSignInService signInService;
    private xuanmo.arcartxsuite.qqbot.service.QQBotMonitorService monitorService;
    private xuanmo.arcartxsuite.qqbot.service.QQBotScheduledMessageService scheduledMessageService;
    private QQBotWeeklyRankService weeklyRankService;
    private QQBotAdminCommand adminCommand;
    private SnowLumaProcessManager snowLumaManager;
    private QQBotLoginGateListener loginGateListener;
    // å¼‚æ­¥å¯åŠ¨å®ˆå«ï¼šstartService åŽå°æ‹‰èµ· SnowLuma æœŸé—´è‹¥æ¨¡å—è¢«å…³é—­ï¼Œç½®ä½ä»¥é˜»æ­¢/å›žæ”¶å¯åŠ¨ï¼Œé¿å…å­¤å„¿è¿›ç¨‹ã€‚
    private volatile boolean closed = false;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("qqbot")
            .name("QQBot")
            .version("1.0.0-beta")
            .mainClass(getClass().getName())
            .softDepends(List.of("chat"))
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXQQBot.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        return SyncPolicy.builder()
            .dynamicSection("groups")
            .dynamicSection("custom-commands")
            .dynamicSection("prizes")
            .dynamicSection("scheduled-messages")
            .dynamicSection("auto-reply.rules")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("onebot.ws-url", ValueType.STRING),
            ValidationRule.required("storage.mode", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            ValidationRule.required("storage.pool-size", ValueType.INT)
                .withRange(1, 50),
            // ç­¾åˆ°ç§¯åˆ†
            ValidationRule.of("signin.base-points", ValueType.INT).withRange(0, 100000),
            ValidationRule.of("signin.streak-bonus", ValueType.INT).withRange(0, 100000),
            ValidationRule.of("signin.max-streak-bonus", ValueType.INT).withRange(0, 1000000),
            // ç›‘æŽ§å‘Šè­¦
            ValidationRule.of("monitor.tps-threshold", ValueType.DOUBLE).withRange(0.0, 20.0),
            ValidationRule.of("monitor.memory-threshold-percent", ValueType.INT).withRange(1, 100),
            ValidationRule.of("monitor.check-interval-seconds", ValueType.INT).withRange(5, 86400)
        );
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXQQBot.yml é…ç½®æ–‡ä»¶ç¼ºå¤±");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = QQBotConfiguration.load(yaml, logger);
    }

    @Override
    protected void startService() throws Exception {
        closed = false;
        // 1. åˆå§‹åŒ–å­˜å‚¨
        repository = new JdbcQQBotRepository(
            dataFolder,
            configuration.storage(),
            logger
        );
        repository.initialize();

        // 2. åˆå§‹åŒ–ç»‘å®šæœåŠ¡
        QQBotBindService bindService = new QQBotBindService(
            configuration.binding(), repository
        );

        // 3. åˆ›å»ºä¸»æœåŠ¡
        service = new QQBotService(
            plugin, configuration,
            bindService,
            repository,
            logger,
            placeholderResolver,
            messages()
        );

        // 4. åˆå§‹åŒ– OneBot WebSocket å®¢æˆ·ç«¯
        oneBotClient = new OneBotClient(
            configuration.onebot().wsUrl(),
            configuration.onebot().accessToken(),
            configuration.onebot().reconnectIntervalSeconds(),
            configuration.onebot().heartbeatIntervalSeconds(),
            logger,
            event -> service.handleOneBotEvent(event),
            () -> service.onBotConnected(),
            () -> service.onBotDisconnected()
        );

        // 5. æ³¨å…¥ client + capability suppliers
        service.setClient(oneBotClient);
        service.setEssentialsProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EssentialsQueryable.class));
        service.setMailProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.MailDispatchable.class));

        // 6. ç­¾åˆ°ç§¯åˆ†æœåŠ¡
        signInService = new xuanmo.arcartxsuite.qqbot.service.QQBotSignInService(
            configuration, repository,
            () -> getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class),
            logger
        );
        service.setSignInService(signInService);
        service.start();

        // 7. SnowLuma è¿›ç¨‹ç®¡ç†ï¼ˆå¿…é¡»åœ¨ WS è¿žæŽ¥å‰å¯åŠ¨ï¼‰
        // ä»Ž onebot.ws-url è§£æžå‡º WS ç«¯å£ä¼ å…¥ï¼Œä½¿ã€ŒæŒ‰ç«¯å£æ¸…ç†æ®‹ç•™ã€è·Ÿéšç”¨æˆ·é…ç½®ï¼Œè€Œéžå†™æ­» 3001ã€‚
        int snowLumaWsPort = 3001;
        try {
            int p = java.net.URI.create(configuration.onebot().wsUrl()).getPort();
            if (p > 0) snowLumaWsPort = p;
        } catch (Exception ignored) {}
        snowLumaManager = new SnowLumaProcessManager(
            java.nio.file.Path.of("").toAbsolutePath(),
            configuration.onebot().snowluma(),
            logger,
            () -> configuration.debug(),
            snowLumaWsPort
        );
        // 8. SnowLuma å¯åŠ¨ + ç­‰å¾… WS ç«¯å£ + è¿žæŽ¥ OneBot â€”â€” æ”¾åˆ°å¼‚æ­¥çº¿ç¨‹æ‰§è¡Œã€‚
        // è¿™äº›æ­¥éª¤ä¼šé˜»å¡žæ•°ç§’ï¼ˆnode/docker è¿›ç¨‹å¯åŠ¨ã€ç«¯å£æŽ¢æµ‹æœ€å¤š 8sï¼‰ï¼Œè‹¥åœ¨ä¸»çº¿ç¨‹
        // ï¼ˆonEnableï¼‰é‡Œä¸²è¡Œæ‰§è¡Œä¼šè§¦å‘ Paper çœ‹é—¨ç‹—ã€Œserver has not responded for 10 secondsã€ã€‚
        final xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager slm = snowLumaManager;
        final OneBotClient obc = oneBotClient;
        final String wsUrl = configuration.onebot().wsUrl();
        final boolean autoStartSnowLuma = configuration.onebot().snowluma().autoStart();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (closed) return;                       // å·²å…³é—­ï¼šä¸å†æ‹‰èµ·è¿›ç¨‹
            slm.init();
            boolean running = slm.getStatus() == xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager.Status.RUNNING;
            if (running) {
                waitForPort(wsUrl, 8000);
            }
            if (closed) {                             // å¯åŠ¨æœŸé—´æ¨¡å—è¢«å…³é—­ï¼šç«‹å³å›žæ”¶ï¼Œé¿å…å­¤å„¿è¿›ç¨‹
                slm.shutdown();
                obc.shutdown();
                return;
            }
            if (running) {
                obc.start();
            } else if (autoStartSnowLuma) {
                logger.warning("[QQBot] SnowLuma å¯åŠ¨å¤±è´¥æˆ–å·²åœæ­¢ï¼ŒOneBot è¿žæŽ¥è¢«è·³è¿‡ã€‚" +
                    "è¯·æ£€æŸ¥ SnowLuma è¿›ç¨‹çŠ¶æ€ï¼Œæˆ–æ‰‹åŠ¨å¯åŠ¨åŽæ‰§è¡Œ /axs qqbot reload");
            } else {
                // ç”¨æˆ·æ‰‹åŠ¨ç®¡ç† SnowLumaï¼Œç›´æŽ¥å°è¯•è¿žæŽ¥
                obc.start();
            }
        });

        // 9. æ³¨å†Œç®¡ç†å‘½ä»¤
        adminCommand = new QQBotAdminCommand(() -> service, () -> repository, snowLumaManager, messages());

        // 10. æ³¨å†Œ capabilityï¼ˆPlayerDataPurgeable + DatabaseMigratableï¼‰
        JdbcQQBotRepository jdbcRepo = repository;
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "qqbot"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return jdbcRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("QQBot purge å¤±è´¥: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return jdbcRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("QQBot purgeAll å¤±è´¥: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "qqbot"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return jdbcRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return jdbcRepo.getDescriptor();
                }
            });

        // 11. æ³¨å†Œ QqBindCapable capabilityï¼ˆä¾› loginview ç­‰æ¨¡å—æŸ¥è¯¢ç»‘å®šçŠ¶æ€ï¼‰
        QQBotBindService bindSvc = bindService;
        registerCapability(xuanmo.arcartxsuite.api.capability.QqBindCapable.class,
            new xuanmo.arcartxsuite.api.capability.QqBindCapable() {
                @Override public boolean isBound(@NotNull java.util.UUID playerUuid) {
                    return bindSvc.findByPlayer(playerUuid) != null;
                }
                @Override public @Nullable Long getBoundQqId(@NotNull java.util.UUID playerUuid) {
                    var binding = bindSvc.findByPlayer(playerUuid);
                    return binding != null ? binding.qqId() : null;
                }
                @Override public @NotNull BindResult confirmBind(@NotNull org.bukkit.entity.Player player, @NotNull String code) {
                    var result = bindSvc.confirmBind(player, code);
                    if (result.success()) {
                        // è‡ªåŠ¨åŠ ç™½åå•ï¼ˆä¿æŒä¸ŽåŽŸæœ‰ /qqbot bind è¡Œä¸ºä¸€è‡´ï¼‰
                        if (configuration.whitelist().enabled() && configuration.whitelist().autoAddOnBind()) {
                            String wlCmd = configuration.whitelist().addCommand()
                                .replace("{name}", player.getName());
                            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), wlCmd);
                        }
                        try { return new BindResult(true, Long.parseLong(result.message()), "ç»‘å®šæˆåŠŸï¼QQ: " + result.message()); }
                        catch (NumberFormatException e) { return new BindResult(true, null, "ç»‘å®šæˆåŠŸï¼"); }
                    }
                    return new BindResult(false, null, result.message());
                }
            });

        // 12. æ³¨å†Œ QQBotBroadcastable capability
        QQBotService svc = service;
        registerCapability(QQBotBroadcastable.class, new QQBotBroadcastable() {
            @Override
            public void sendToGroup(long groupId, @NotNull String message) {
                svc.sendToGroup(groupId, message);
            }

            @Override
            public void sendToAllGroups(@NotNull String message) {
                svc.sendToAllGroups(message);
            }
        });

        // 13. æ³¨å†Œ QQBotNotifiable capabilityï¼ˆåå‘é€šçŸ¥ï¼‰
        registerCapability(xuanmo.arcartxsuite.api.capability.QQBotNotifiable.class,
            new xuanmo.arcartxsuite.api.capability.QQBotNotifiable() {
                @Override
                public void registerListener(@NotNull xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener listener) {
                    svc.addGroupEventListener(listener);
                }
                @Override
                public void unregisterListener(@NotNull xuanmo.arcartxsuite.api.capability.QQBotNotifiable.QQGroupEventListener listener) {
                    svc.removeGroupEventListener(listener);
                }
            });

        // 14. å®¡è®¡æ—¥å¿—ï¼šçŽ©å®¶è´¦å·ç±»åž‹ä¸Žç»‘å®šçŠ¶æ€ï¼ˆä»… debug è¾“å‡ºï¼Œä¸æ‹¦æˆªç™»å½•ï¼‰
        loginGateListener = new QQBotLoginGateListener(
            plugin, configuration, repository, accountTypeService, logger
        );
        loginGateListener.register();

        // 15. UI æœåŠ¡
        uiService = new QQBotUiService(
            plugin, configuration, repository, bindService, packetBridge, logger
        );
        uiService.setSendToGroupCallback(msg -> svc.sendToAllGroups(msg));
        uiService.setServiceConnectedSupplier(() -> svc.isConnected());

        // æ³¨å…¥ç¾¤æ¶ˆæ¯ç›‘å¬å™¨åˆ° UI æœåŠ¡
        QQBotUiService uiSvc = uiService;
        service.setGroupMessageListener((nick, message, groupId) -> {
            uiSvc.cacheGroupMessage(nick, message, groupId);
            // å¹¿æ’­é€šçŸ¥åˆ°æ‰€æœ‰åœ¨çº¿çŽ©å®¶
            Bukkit.getScheduler().runTask(plugin, () ->
                uiSvc.broadcastNotification(nick, message));
        });

        // æ³¨å†Œ UI ç»‘å®š
        UiBinding bindUi = registerModuleUi(QQBotUiService.BIND_UI_FILE, "AXS:QQBot_Bind", true);
        if (bindUi.registeredUiId() != null) {
            uiService.setBindUiId(bindUi.runtimeUiId());
        }

        UiBinding notifyUi = registerModuleUi(QQBotUiService.NOTIFY_UI_FILE, "AXS:QQBot_Notify", true);
        if (notifyUi.registeredUiId() != null) {
            uiService.setNotifyUiId(notifyUi.runtimeUiId());
        }

        UiBinding adminUi = registerModuleUi(QQBotUiService.ADMIN_UI_FILE, "AXS:QQBot_Admin", true);
        if (adminUi.registeredUiId() != null) {
            uiService.setAdminUiId(adminUi.runtimeUiId());
        }

        logger.info("QQBot æ¨¡å—å·²å¯åŠ¨"
            + " | ç¾¤æ•°=" + configuration.groups().size()
            + " | å­˜å‚¨=" + configuration.storage().mode()
            + " | ç»‘å®š=" + (configuration.binding().enabled() ? "å¯ç”¨" : "ç¦ç”¨")
            + " | ç™½åå•=" + (configuration.whitelist().enabled() ? "å¯ç”¨" : "ç¦ç”¨")
            + " | UI=" + (bindUi.registeredUiId() != null ? "å¯ç”¨" : "ç¦ç”¨")
        );

        // 16. è®¢é˜… EventBus äº‹ä»¶ï¼ˆè§£è€¦æ’­æŠ¥ï¼‰
        xuanmo.arcartxsuite.api.capability.EventBusCapability eventBus = getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class);
        if (eventBus != null) {
            eventBus.subscribe("market.*", event -> {
                if ("market.auction_purchased".equals(event.topic())) {
                    String price = event.payload().getOrDefault("formatted_price", "?");
                    String playerName = event.player() != null ? event.player().getName() : "æœªçŸ¥";
                    svc.sendToAllGroups("[äº¤æ˜“] " + playerName + " ä»¥ " + price + " è´­ä¹°äº†æ‹å–è¡Œç‰©å“");
                }
            });
            eventBus.subscribe("mail.*", event -> {
                // é¢„ç•™ï¼šé‚®ä»¶ç›¸å…³äº‹ä»¶ç¾¤é€šçŸ¥
            });
        }

        // 17. æœåŠ¡å™¨ç›‘æŽ§å‘Šè­¦
        monitorService = new xuanmo.arcartxsuite.qqbot.service.QQBotMonitorService(
            plugin, configuration, svc::sendToGroup, logger
        );
        monitorService.start();

        // 18. å®šæ—¶æ¶ˆæ¯
        scheduledMessageService = new xuanmo.arcartxsuite.qqbot.service.QQBotScheduledMessageService(
            plugin, configuration, svc::sendToGroup, logger
        );
        scheduledMessageService.start();

        // 19. å‘¨ç»“ç®—æŽ’è¡Œæ¦œ
        if (configuration.signin().enabled()) {
            weeklyRankService = new QQBotWeeklyRankService(
                plugin, configuration, repository, bindService, svc::sendToAllGroups, logger
            );
            weeklyRankService.start();
        }

        // 20. çº¢åŒ…è¿‡æœŸé€€æ¬¾å®šæ—¶å™¨ï¼ˆæ¯å°æ—¶æ£€æŸ¥ä¸€æ¬¡ï¼‰
        if (configuration.signin().enabled()) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> signInService.refundExpiredRedPackets(), 72000L, 72000L);
        }
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        return Map.of(
            QQBotUiService.BIND_UI_RESOURCE, QQBotUiService.BIND_UI_FILE,
            QQBotUiService.NOTIFY_UI_RESOURCE, QQBotUiService.NOTIFY_UI_FILE,
            QQBotUiService.ADMIN_UI_RESOURCE, QQBotUiService.ADMIN_UI_FILE
        );
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) -> {
            if (uiService == null || data == null || data.isEmpty()) return false;
            String action = data.get(0);
            List<String> params = data.size() > 1 ? data.subList(1, data.size()) : List.of();
            if ("AXS_qqbot".equals(packetId)) {
                return uiService.handleBindPacket(player, action, params);
            }
            if ("AXS_qqbot_admin".equals(packetId)) {
                return uiService.handleAdminPacket(player, action, params);
            }
            return false;
        };
    }

    public QQBotUiService getUiService() {
        return uiService;
    }

    @Override
    protected void stopService() {
        closed = true;   // é€šçŸ¥åŽå°å¼‚æ­¥å¯åŠ¨ä»»åŠ¡åœæ­¢/å›žæ”¶
        uiService = null;
        if (monitorService != null) {
            monitorService.stop();
            monitorService = null;
        }
        if (scheduledMessageService != null) {
            scheduledMessageService.stop();
            scheduledMessageService = null;
        }
        if (weeklyRankService != null) {
            weeklyRankService.stop();
            weeklyRankService = null;
        }
        signInService = null;
        if (loginGateListener != null) {
            loginGateListener.unregister();
            loginGateListener = null;
        }
        if (snowLumaManager != null) {
            snowLumaManager.shutdown();
            snowLumaManager = null;
        }
        if (oneBotClient != null) {
            oneBotClient.shutdown();
            oneBotClient = null;
        }
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
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        return Map.of(
            "qqbot", new QQBotPlayerCommand(() -> service, () -> uiService, messages())
        );
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new QQBotPlaceholderExpansion(plugin, () -> service);
    }

    // â”€â”€â”€ ModuleCommandHandler å®žçŽ° â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public String commandId() {
        return "qqbot";
    }

    @Override
    public List<String> actions() {
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

    /**
     * ç­‰å¾… WS ç«¯å£å¯è¾¾ï¼ˆSnowLuma å¯åŠ¨åŽ WS æœåŠ¡éœ€è¦å‡ ç§’åˆå§‹åŒ–ï¼‰ã€‚
     */
    private void waitForPort(String wsUrl, int timeoutMs) {
        try {
            java.net.URI uri = java.net.URI.create(wsUrl);
            String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            long deadline = System.currentTimeMillis() + timeoutMs;
            logger.info("[QQBot] ç­‰å¾… WS ç«¯å£ " + host + ":" + port + " å°±ç»ª...");
            while (System.currentTimeMillis() < deadline) {
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(host, port), 500);
                    logger.info("[QQBot] WS ç«¯å£å·²å°±ç»ª");
                    return;
                } catch (java.io.IOException ignored) {
                    Thread.sleep(500);
                }
            }
            logger.warning("[QQBot] ç­‰å¾… WS ç«¯å£è¶…æ—¶ (" + timeoutMs + "ms)ï¼Œå°†ä»å°è¯•è¿žæŽ¥");
        } catch (Exception e) {
            logger.warning("[QQBot] ç«¯å£æ£€æµ‹å¼‚å¸¸: " + e.getMessage());
        }
    }
}



