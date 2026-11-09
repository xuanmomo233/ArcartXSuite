package xuanmo.arcartxsuite.qqbot;

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
import xuanmo.arcartxsuite.qqbot.service.QQBotLoginAuditListener;
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
    // 会被异步线程读取（断开提示回调 / 异步启动块），需保证可见性
    private volatile SnowLumaProcessManager snowLumaManager;
    private QQBotLoginAuditListener loginAuditListener;
    // 红包过期退款定时任务引用：stopService 时需取消，避免 reload 后定时器仍触发已置 null 的 signInService
    private BukkitTask redPacketRefundTask;
    // 异步启动守卫：startService 后台拉起 SnowLuma 期间若模块被关闭，置位以阻止/回收启动，避免孤儿进程。
    private volatile boolean closed = false;
    private volatile long generation;
    private BukkitTask startupTask;
    private java.util.concurrent.CompletableFuture<Void> previousProcessCleanup = java.util.concurrent.CompletableFuture.completedFuture(null);
    // EventBus 订阅 ID：stopService 时退订，防止 /axs reload 后订阅叠加、播报重复
    private final List<String> eventBusSubscriptionIds = new java.util.ArrayList<>();
    private final Map<java.util.UUID, org.bukkit.entity.Player> initializedClients = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("qqbot")
            .name("QQBot")
            .version("2.0.0")
            .mainClass(getClass().getName())
            .softDepends(List.of("chat"))
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXQQBot.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .dynamicSection("groups")
                    .dynamicSection("custom-commands")
                    .dynamicSection("prizes")
                    .dynamicSection("scheduled-messages")
                    .dynamicSection("auto-reply.rules")
                    .opaqueSection("storage")
                .build())
            .currentVersion(6)
            .validations(List.of(
                    ValidationRule.required("onebot.ws-url", ValueType.STRING),
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    // 签到积分
                    ValidationRule.of("signin.base-points", ValueType.INT).withRange(0, 100000),
                    ValidationRule.of("signin.streak-bonus", ValueType.INT).withRange(0, 100000),
                    ValidationRule.of("signin.max-streak-bonus", ValueType.INT).withRange(0, 1000000),
                    // 监控告警
                    ValidationRule.of("monitor.tps-threshold", ValueType.DOUBLE).withRange(0.0, 20.0),
                    ValidationRule.of("monitor.memory-threshold-percent", ValueType.INT).withRange(1, 100),
                    ValidationRule.of("monitor.check-interval-seconds", ValueType.INT).withRange(5, 86400)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        return new ModuleUiSpec(Map.of(
            QQBotUiService.BIND_UI_RESOURCE, QQBotUiService.BIND_UI_FILE,
            QQBotUiService.NOTIFY_UI_RESOURCE, QQBotUiService.NOTIFY_UI_FILE,
            QQBotUiService.ADMIN_UI_RESOURCE, QQBotUiService.ADMIN_UI_FILE
        ), configuration != null && configuration.ui().overwriteUiFiles());
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((player, packetId, data) -> {
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
        }, 0, null, null);
    }

    @Override
    protected xuanmo.arcartxsuite.api.ClientInitializedHandler createInitializedHandler() {
        return player -> {
            initializedClients.put(player.getUniqueId(), player);
            if (uiService != null) uiService.markClientReady(player);
        };
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
        final long startedGeneration = ++generation;
        final java.util.concurrent.CompletableFuture<Void> cleanupBeforeStart = previousProcessCleanup;
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
        final QQBotService startedService = service;
        oneBotClient = new OneBotClient(
            configuration.onebot().wsUrl(),
            configuration.onebot().accessToken(),
            configuration.onebot().reconnectIntervalSeconds(),
            configuration.onebot().heartbeatIntervalSeconds(),
            logger,
            startedService::handleOneBotEvent,
            startedService::onBotConnected,
            startedService::onBotDisconnected
        );
        oneBotClient.setGroupRateLimit(configuration.delivery().groupMessagesPerMinute());

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
        // Connection diagnostics use the WS port; process ownership never depends on a port.
        int snowLumaWsPort = 3001;
        try {
            int p = java.net.URI.create(configuration.onebot().wsUrl()).getPort();
            if (p > 0) snowLumaWsPort = p;
        } catch (Exception ignored) {}
        snowLumaManager = new SnowLumaProcessManager(
            java.nio.file.Path.of("").toAbsolutePath(),
            configuration.onebot().snowluma(),
            logger,
            configuration::debug,
            snowLumaWsPort
        );
        // 8. SnowLuma 启动 + 等待 WS 端口 + 连接 OneBot —— 放到异步线程执行。
        // 这些步骤会阻塞数秒（node/docker 进程启动、端口探测最多 8s、docker 自动部署数分钟），
        // 若在主线程（onEnable）里串行执行会触发 Paper 看门狗「server has not responded for 10 seconds」。
        final xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager slm = snowLumaManager;
        final OneBotClient obc = oneBotClient;
        final String wsUrl = configuration.onebot().wsUrl();
        final boolean localWs = isLocalWsUrl(wsUrl);
        // OneBot 断开告警末尾追加 SnowLuma 状态提示（未安装/未运行时指引到具体命令）
        service.setDisconnectHint(() -> "实现端状态可执行 /axs qqbot snowluma status 查询；外部实现端请核对 WS 与 token。");
        startupTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try { cleanupBeforeStart.join(); }
            catch (RuntimeException cleanupFailed) {
                logger.severe("[QQBot] 上一轮托管进程清理失败，本轮停止启动。请核对进程归属后重载。");
                return;
            }
            if (closed || generation != startedGeneration) return;
            slm.init();
            if (closed || generation != startedGeneration) {
                slm.shutdown();
                obc.shutdown();
                return;
            }
            boolean running = slm.getStatus() == xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager.Status.RUNNING;
            if (running) {
                waitForPort(wsUrl, 8000);
                if (closed || generation != startedGeneration) {
                    slm.shutdown();
                    obc.shutdown();
                    return;
                }
                obc.start();
                return;
            }
            // SnowLuma 未运行：本机 ws 且端口无服务监听 → 打印部署引导（告知 install/start/密码查询）；
            // 端口已有服务（NapCat 等其他实现端在跑）或 ws 指向远程 → 交给客户端自动重连即可。
            if (localWs && !probePort(wsUrl, 800)) {
                logOneBotSetupGuide(slm);
            }
            obc.start(); // 始终启动客户端：实现端稍后就绪时可自动重连，无需手动 reload
        });

        // 9. 注册管理命令
        adminCommand = new QQBotAdminCommand(plugin, () -> service, () -> repository, snowLumaManager, messages());

        // QQ assets are owned by QQ identities, not deleted with a Minecraft character.
        JdbcQQBotRepository jdbcRepo = repository;

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "qqbot"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return jdbcRepo.migrateSafely(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return jdbcRepo.getDescriptor();
                }
            });

        // 12. 注册 QQBotBroadcastable capability
        QQBotService svc = service;
        registerCapability(QQBotBroadcastable.class, new QQBotBroadcastable() {
            @Override
            public void sendToGroup(long groupId, @NotNull String message) {
                svc.sendToGroup(groupId, message).thenAccept(result -> {
                    if (!result.success()) logger.warning("QQ 通知未完成: " + result.message());
                });
            }

            @Override
            public void sendToAllGroups(@NotNull String message) {
                svc.sendToAllGroups(message).thenAccept(result -> {
                    if (!result.success()) logger.warning("QQ 通知未完成: " + result.message());
                });
            }
        });
        registerCapability(xuanmo.arcartxsuite.api.capability.QQBotDelivery.class,
            new xuanmo.arcartxsuite.api.capability.QQBotDelivery() {
                @Override public java.util.concurrent.CompletionStage<Result> sendToGroup(long groupId, String message) {
                    return svc.sendToGroup(groupId, message).thenApply(result -> publicDeliveryResult(result, 1));
                }
                @Override public java.util.concurrent.CompletionStage<Result> sendToAllGroups(String message) {
                    return svc.sendToAllGroups(message).thenApply(result -> publicDeliveryResult(result, svc.configuration().groups().size()));
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
        loginAuditListener = new QQBotLoginAuditListener(
            plugin, configuration, repository, logger
        );
        loginAuditListener.register();

        // 15. UI 服务
        uiService = new QQBotUiService(
            plugin, configuration, repository, bindService, packetBridge, logger
        );
        uiService.setSendPlayerMessageCallback(svc::sendPlayerMessage);
        uiService.setServiceConnectedSupplier(() -> svc.isConnected());
        initializedClients.values().stream().filter(player -> player.isOnline()
            && Bukkit.getPlayer(player.getUniqueId()) == player).forEach(uiService::markClientReady);

        // 玩家退出时清理 UI 动作令牌与在途标记
        registerListener(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                initializedClients.remove(event.getPlayer().getUniqueId(), event.getPlayer());
                if (uiService != null) {
                    uiService.clearPlayerState(event.getPlayer().getUniqueId());
                }
            }
        });

        // 注入群消息监听器到 UI 服务
        QQBotUiService uiSvc = uiService;
        final boolean notifyEnabled = configuration.ui().notifyEnabled();
        service.setGroupMessageListener((nick, message, groupId) -> {
            uiSvc.cacheGroupMessage(nick, message, groupId);
            // 广播通知到所有在线玩家（受 ui.notify-enabled 控制）
            if (notifyEnabled) {
                Bukkit.getScheduler().runTask(plugin, () ->
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
            String marketSubId = eventBus.subscribe("axs.market.*", event -> {
                if (!"axs.market.auction_purchased".equals(event.topic())) return;
                if (!configuration.marketBroadcast().enabled()) return;
                double price = 0;
                try {
                    price = Double.parseDouble(event.payload().getOrDefault("price", "0"));
                } catch (NumberFormatException ignored) {
                    return;
                }
                if (price < configuration.marketBroadcast().threshold()) return;
                String formattedPrice = event.payload().getOrDefault("formatted_price", String.valueOf(price));
                String playerName = event.player() != null
                    ? event.player().getName()
                    : event.payload().getOrDefault("buyer", "未知");
                svc.sendToAllGroups("[交易] " + playerName + " 以 " + formattedPrice + " 购买了拍卖行物品");
            });
            if (marketSubId != null) {
                eventBusSubscriptionIds.add(marketSubId);
            }
            String mailSubId = eventBus.subscribe("axs.mail.*", event -> {
                // 预留：邮件相关事件群通知
            });
            if (mailSubId != null) {
                eventBusSubscriptionIds.add(mailSubId);
            }
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
            redPacketRefundTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
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

    private static xuanmo.arcartxsuite.api.capability.QQBotDelivery.Result publicDeliveryResult(
            xuanmo.arcartxsuite.qqbot.onebot.OneBotResult result, int total) {
        int sent = result.data().has("sentGroups") ? result.data().get("sentGroups").getAsInt() : result.success() ? total : 0;
        return new xuanmo.arcartxsuite.api.capability.QQBotDelivery.Result(result.success(), result.retcode() == -2, sent, total, result.message());
    }

    @Override
    protected void stopService() {
        closed = true;   // 通知后台异步启动任务停止/回收
        generation++;
        if (service != null) service.shutdown();
        if (startupTask != null) { startupTask.cancel(); startupTask = null; }
        if (uiService != null) uiService.shutdown();
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
        if (loginAuditListener != null) {
            loginAuditListener.unregister();
            loginAuditListener = null;
        }
        if (snowLumaManager != null) {
            previousProcessCleanup = snowLumaManager.shutdownAsync();
            snowLumaManager = null;
        }
        if (oneBotClient != null) {
            oneBotClient.shutdown();
            oneBotClient = null;
        }
        if (service != null) {
            service = null;
        }
        if (repository != null) {
            repository.close();
            repository = null;
        }
        // 退订 EventBus，防止 reload 后订阅叠加导致重复播报
        if (!eventBusSubscriptionIds.isEmpty()) {
            xuanmo.arcartxsuite.api.capability.EventBusCapability eventBus = getCapability(
                xuanmo.arcartxsuite.api.capability.EventBusCapability.class);
            if (eventBus != null) {
                for (String subId : eventBusSubscriptionIds) {
                    eventBus.unsubscribe(subId);
                }
            }
            eventBusSubscriptionIds.clear();
        }
        storageManager.closeModuleDataSource("qqbot");
        configuration = null;
        adminCommand = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        return Map.of(
            "qqbot", new QQBotPlayerCommand(plugin, () -> service, () -> uiService, messages())
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

    /** ws-url 是否指向本机回环地址（仅本机部署时才做 SnowLuma 安装引导） */
    private boolean isLocalWsUrl(String wsUrl) {
        try {
            String host = java.net.URI.create(wsUrl).getHost();
            if (host == null) return true;
            host = host.toLowerCase(java.util.Locale.ROOT);
            return host.equals("localhost") || host.equals("::1") || host.equals("0.0.0.0")
                || host.startsWith("127.");
        } catch (Exception e) {
            return false;
        }
    }

    /** 单次 TCP 探测 ws-url 端口是否有服务监听（区别于 waitForPort 的循环等待） */
    private boolean probePort(String wsUrl, int timeoutMs) {
        try {
            java.net.URI uri = java.net.URI.create(wsUrl);
            String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
            int port = uri.getPort() > 0 ? uri.getPort() : "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 打印 OneBot 实现端部署引导（仅当本机 ws 端口无服务时调用）。
     * 区分「未安装」与「已安装未运行」两种情形，并附 WebUI/密码查询方式。
     */
    private static String redactedWsUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            return new java.net.URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) { return "所配置的 OneBot 端点"; }
    }

    private void logOneBotSetupGuide(SnowLumaProcessManager slm) {
        if (configuration == null) return; // 异步期间模块可能已被卸载
        String wsUrl = redactedWsUrl(configuration.onebot().wsUrl());
        if (!configuration.onebot().snowluma().isManaged()) {
            logger.warning("[QQBot] 外部 OneBot 未就绪：请配置正向 Universal WebSocket 与相同 access-token，登录 QQ 后将自动重连。详见 QQBot 安装教学。");
            return;
        }
        boolean docker = configuration.onebot().snowluma().isDocker();
        StringBuilder sb = new StringBuilder();
        sb.append("====================== [QQBot] 部署引导 ======================\n");
        sb.append(" 未检测到 OneBot 实现端（").append(wsUrl).append(" 无服务监听），QQ 机器人暂不可用。\n");
        if (slm.isInstalled()) {
            sb.append(" 检测到内置 SnowLuma 已安装但未运行：\n");
            sb.append("   · /axs qqbot snowluma start     启动 SnowLuma\n");
            sb.append("   · /axs qqbot snowluma status    查看状态与故障原因\n");
            sb.append("   · 或将 onebot.snowluma.auto-start 设为 true 让模块自动拉起\n");
        } else {
            sb.append(" 使用内置 SnowLuma（推荐，模块可自动管理进程）：\n");
            if (docker) {
                sb.append("   · /axs qqbot snowluma install   自动拉取镜像并创建容器\n");
            } else {
                sb.append("   · /axs qqbot snowluma install   从 GitHub 下载安装完整版（自带 Node 运行时）\n");
            }
            sb.append("   · /axs qqbot snowluma start     启动 SnowLuma\n");
        }
        sb.append("   · WebUI 管理面板 ").append(slm.webUiUrl())
            .append("（账号 admin，临时密码执行 /axs qqbot snowluma password 查看）\n");
        if (docker) {
            sb.append("   · Docker 模式下登录 QQ 需打开 noVNC: http://<服务器IP>:")
                .append(configuration.onebot().snowluma().dockerNovncPort()).append("\n");
        }
        sb.append("   · 在 WebUI 中登录 QQ 账号后，模块会自动建立连接\n");
        sb.append(" 使用其他实现端（NapCat/Lagrange 等）：启动实现端并监听 ")
            .append(wsUrl).append("，模块会自动重连。\n");
        sb.append("=============================================================");
        for (String line : sb.toString().split("\n")) logger.warning(line);
    }

    /**
     * 等待 WS 端口可达（SnowLuma 启动后 WS 服务需要几秒初始化）。
     */
    private void waitForPort(String wsUrl, int timeoutMs) {
        try {
            java.net.URI uri = java.net.URI.create(wsUrl);
            String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
            int port = uri.getPort() > 0 ? uri.getPort() : "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
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

