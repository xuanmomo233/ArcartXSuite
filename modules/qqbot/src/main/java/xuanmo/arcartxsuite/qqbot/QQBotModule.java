package xuanmo.arcartxsuite.qqbot;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.scheduler.BukkitTask;
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
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
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
    // 红包过期退款定时任务引用：stopService 时需取消，避免 reload 后定时器仍触发已置 null 的 signInService
    private BukkitTask redPacketRefundTask;
    // 异步启动守卫：startService 后台拉起 SnowLuma 期间若模块被关闭，置位以阻止/回收启动，避免孤儿进程。
    private volatile boolean closed = false;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("qqbot")
            .name("QQBot")
            .version("1.1.0-beta")
            .mainClass(getClass().getName())
            .softDepends(List.of("chat"))
            .build();
    }

    

    

    

    

    

    

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXQQBot.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = QQBotConfiguration.load(yaml, logger);
    }

    @Override
    protected void startService() throws Exception {
        closed = false;
        // 1. 初始化存储
        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor qqDesc;
        javax.sql.DataSource qqDs;
        if (configuration.storage().hasOverride()) {
            qqDesc = configuration.storage().toDescriptor();
            qqDs = storageManager.resolveModuleDataSource("qqbot", null, dataFolder, qqDesc);
        } else {
            qqDesc = storageManager.getDescriptor()
                .withTablePrefix(configuration.storage().tablePrefix());
            qqDs = storageManager.resolveModuleDataSource("qqbot",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        repository = new JdbcQQBotRepository(qqDs, qqDesc, dataFolder, logger);
        repository.initialize();

        // 2. 初始化绑定服务
        QQBotBindService bindService = new QQBotBindService(
            configuration.binding(), repository
        );

        // 3. 创建主服务
        service = new QQBotService(
            plugin, configuration,
            bindService,
            repository,
            logger,
            placeholderResolver,
            messages(),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class)
        );

        // 4. 初始化 OneBot WebSocket 客户端
        oneBotClient = new OneBotClient(
            configuration.onebot().wsUrl(),
            configuration.onebot().accessToken(),
            configuration.onebot().reconnectIntervalSeconds(),
            configuration.onebot().heartbeatIntervalSeconds(),
            logger,
            event -> service.handleOneBotEvent(event),
            () -> service.onBotConnected(),
            reason -> service.onBotDisconnected(reason)
        );

        // 5. 注入 client + capability suppliers
        service.setClient(oneBotClient);
        service.setEssentialsProvider(() -> getCapability(
            xuanmo.arcartxsuite.api.capability.EssentialsQueryable.class));

        // 6. 签到积分服务
        signInService = new xuanmo.arcartxsuite.qqbot.service.QQBotSignInService(
            configuration, repository,
            () -> getCapability(xuanmo.arcartxsuite.api.capability.MailDispatchable.class),
            logger
        );
        service.setSignInService(signInService);
        service.start();

        // 7. SnowLuma 进程管理（必须在 WS 连接前启动）
        // 从 onebot.ws-url 解析出 WS 端口传入，使「按端口清理残留」跟随用户配置，而非写死 3001。
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
        // 8. SnowLuma 启动 + 等待 WS 端口 + 连接 OneBot —— 放到异步线程执行。
        // 这些步骤会阻塞数秒（node/docker 进程启动、端口探测最多 8s），若在主线程
        // （onEnable）里串行执行会触发 Paper 看门狗「server has not responded for 10 seconds」。
        final xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager slm = snowLumaManager;
        final OneBotClient obc = oneBotClient;
        final String wsUrl = configuration.onebot().wsUrl();
        final boolean autoStartSnowLuma = configuration.onebot().snowluma().autoStart();
        AxsScheduler.runAsync(plugin, () -> {
            if (closed) return;                       // 已关闭：不再拉起进程
            slm.init();
            boolean running = slm.getStatus() == xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager.Status.RUNNING;
            if (running) {
                waitForPort(wsUrl, 8000);
            }
            if (closed) {                             // 启动期间模块被关闭：立即回收，避免孤儿进程
                slm.shutdown();
                obc.shutdown();
                return;
            }
            if (running) {
                obc.start();
            } else if (autoStartSnowLuma) {
                logger.warning("SnowLuma 启动失败或已停止，OneBot 连接被跳过。" +
                    "请检查 SnowLuma 进程状态，或手动启动后执行 /axs qqbot reload");
            } else {
                // 用户手动管理 SnowLuma，直接尝试连接
                obc.start();
            }
        });

        // 9. 注册管理命令
        adminCommand = new QQBotAdminCommand(() -> service, () -> repository, snowLumaManager, messages());

        // 10. 注册 capability（PlayerDataPurgeable + DatabaseMigratable）
        JdbcQQBotRepository jdbcRepo = repository;
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "qqbot"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return jdbcRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("QQBot purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return jdbcRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("QQBot purgeAll 失败: " + e.getMessage()); return -1; }
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

        // 11. 注册 QqBindCapable capability（供 loginview 等模块查询绑定状态）
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
                        // 自动加白名单（保持与原有 /qqbot bind 行为一致）
                        if (configuration.whitelist().enabled() && configuration.whitelist().autoAddOnBind()) {
                            try {
                                String wlCmd = xuanmo.arcartxsuite.api.command.SafeCommandExecutor.builder(configuration.whitelist().addCommand())
                                    .bindPlayerName("name", player.getName())
                                    .build();
                                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), wlCmd);
                            } catch (IllegalArgumentException ignored) {
                                // 玩家名不合法时跳过白名单添加
                            }
                        }
                        try { return new BindResult(true, Long.parseLong(result.message()), "绑定成功！QQ: " + result.message()); }
                        catch (NumberFormatException e) { return new BindResult(true, null, "绑定成功！"); }
                    }
                    return new BindResult(false, null, result.message());
                }
            });

        // 12. 注册 QQBotBroadcastable capability
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

        // 13. 注册 QQBotNotifiable capability（反向通知）
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

        // 14. 审计日志：玩家账号类型与绑定状态（仅 debug 输出，不拦截登录）
        loginGateListener = new QQBotLoginGateListener(
            plugin, configuration, repository, accountTypeService, logger
        );
        loginGateListener.register();

        // 15. UI 服务
        uiService = new QQBotUiService(
            plugin, configuration, repository, bindService, packetBridge, logger
        );
        uiService.setSendToGroupCallback(msg -> svc.sendToAllGroups(msg));
        uiService.setServiceConnectedSupplier(() -> svc.isConnected());

        // 注入群消息监听器到 UI 服务
        QQBotUiService uiSvc = uiService;
        final boolean notifyEnabled = configuration.ui().notifyEnabled();
        service.setGroupMessageListener((nick, message, groupId) -> {
            uiSvc.cacheGroupMessage(nick, message, groupId);
            // 广播通知到所有在线玩家（受 ui.notify-enabled 控制）
            if (notifyEnabled) {
                AxsScheduler.runTask(plugin, () ->
                    uiSvc.broadcastNotification(nick, message));
            }
        });

        // 注册 UI 绑定（UI ID 与注册行为从配置 ui 节读取）
        UiBinding bindUi = registerModuleUi(QQBotUiService.BIND_UI_FILE, configuration.ui().bindUiId(), configuration.ui().registerUiOnEnable());
        if (bindUi.registeredUiId() != null) {
            uiService.setBindUiId(bindUi.runtimeUiId());
        }

        UiBinding notifyUi = registerModuleUi(QQBotUiService.NOTIFY_UI_FILE, configuration.ui().notifyUiId(), configuration.ui().registerUiOnEnable());
        if (notifyUi.registeredUiId() != null) {
            uiService.setNotifyUiId(notifyUi.runtimeUiId());
        }

        UiBinding adminUi = registerModuleUi(QQBotUiService.ADMIN_UI_FILE, configuration.ui().adminUiId(), configuration.ui().registerUiOnEnable());
        if (adminUi.registeredUiId() != null) {
            uiService.setAdminUiId(adminUi.runtimeUiId());
        }

        logger.info("QQBot 模块已启动"
            + " | 群数=" + configuration.groups().size()
            + " | 存储=" + configuration.storage().mode()
            + " | 绑定=" + (configuration.binding().enabled() ? "启用" : "禁用")
            + " | 白名单=" + (configuration.whitelist().enabled() ? "启用" : "禁用")
            + " | UI=" + (bindUi.registeredUiId() != null ? "启用" : "禁用")
        );

        // 16. 订阅 EventBus 事件（解耦播报）
        xuanmo.arcartxsuite.api.capability.EventBusCapability eventBus = getCapability(
            xuanmo.arcartxsuite.api.capability.EventBusCapability.class);
        if (eventBus != null) {
            eventBus.subscribe("market.*", event -> {
                if (!"market.auction_purchased".equals(event.topic())) return;
                if (!configuration.marketBroadcast().enabled()) return;
                double price = 0;
                try {
                    price = Double.parseDouble(event.payload().getOrDefault("price", "0"));
                } catch (NumberFormatException ignored) {
                    return;
                }
                if (price < configuration.marketBroadcast().threshold()) return;
                String formattedPrice = event.payload().getOrDefault("formatted_price", String.valueOf(price));
                String playerName = event.player() != null ? event.player().getName() : "未知";
                svc.sendToAllGroups("[交易] " + playerName + " 以 " + formattedPrice + " 购买了拍卖行物品");
            });
            eventBus.subscribe("mail.*", event -> {
                // 预留：邮件相关事件群通知
            });
        }

        // 17. 服务器监控告警
        monitorService = new xuanmo.arcartxsuite.qqbot.service.QQBotMonitorService(
            plugin, configuration, svc::sendToGroup, logger
        );
        monitorService.start();

        // 18. 定时消息
        scheduledMessageService = new xuanmo.arcartxsuite.qqbot.service.QQBotScheduledMessageService(
            plugin, configuration, svc::sendToGroup, logger
        );
        scheduledMessageService.start();

        // 19. 周结算排行榜
        if (configuration.signin().enabled()) {
            weeklyRankService = new QQBotWeeklyRankService(
                plugin, configuration, repository, bindService, svc::sendToAllGroups, logger
            );
            weeklyRankService.start();
        }

        // 20. 红包过期退款定时器（每小时检查一次）
        if (configuration.signin().enabled()) {
            redPacketRefundTask = AxsScheduler.runAsyncTimer(plugin,
                () -> {
                    // 双重保险：stopService 会 cancel 本任务，但仍可能在 cancel 前一刻被调度执行
                    xuanmo.arcartxsuite.qqbot.service.QQBotSignInService signSvc = signInService;
                    if (signSvc != null) signSvc.refundExpiredRedPackets();
                }, 72000L, 72000L);
        }
    }

    

    

    public QQBotUiService getUiService() {
        return uiService;
    }

    @Override
    protected void stopService() {
        closed = true;   // 通知后台异步启动任务停止/回收
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
        // 取消红包退款定时器，避免 reload 后触发已置 null 的 signInService
        if (redPacketRefundTask != null) {
            redPacketRefundTask.cancel();
            redPacketRefundTask = null;
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
        storageManager.closeModuleDataSource("qqbot");
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

    // ─── ModuleCommandHandler 实现 ───────────────────────

    @Override
    public String commandId() {
        return "qqbot";
    }

    @Override
    public List<String> actions() {
        return adminCommand != null ? adminCommand.actions() : List.of("help", "status");
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
     * 等待 WS 端口可达（SnowLuma 启动后 WS 服务需要几秒初始化）。
     */
    private void waitForPort(String wsUrl, int timeoutMs) {
        try {
            java.net.URI uri = java.net.URI.create(wsUrl);
            String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            long deadline = System.currentTimeMillis() + timeoutMs;
            logger.info("等待 WS 端口 " + host + ":" + port + " 就绪...");
            while (System.currentTimeMillis() < deadline) {
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(host, port), 500);
                    logger.info("WS 端口已就绪");
                    return;
                } catch (java.io.IOException ignored) {
                    Thread.sleep(500);
                }
            }
            logger.warning("等待 WS 端口超时 (" + timeoutMs + "ms)，将仍尝试连接");
        } catch (Exception e) {
            logger.warning("端口检测异常: " + e.getMessage());
        }
    }
}


