package xuanmo.arcartxsuite.onlinerewards.service;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.capability.mail.MailAttachmentSpec;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsDayOfMonthReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsHolidayReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsItemReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsMilestoneReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardDefinition;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPermissionBonusReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPeriodicReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsServerSignInGoalConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsServerSignInGoalTarget;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsSignInConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsTimeBasedReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsTimeBonusGroup;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsSignInHistory;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Attendance;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Delivery;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.DeliveryReview;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.MakeupCardOperation;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressMutation;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressPlan;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressRequest;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.ProgressResult;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.Reward;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.RewardBatch;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.SignInCommit;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository.SignInStatus;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.mail.AxsMailService;
import xuanmo.arcartxsuite.api.inventory.InventoryItems;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduledTask;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import java.util.logging.Logger;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;

/**
 * 在线奖励核心服务。
 * <p>
 * 属于 {@code onlinerewards} 模块，负责玩家状态缓存与持久化、每分钟在线时长累计、
 * 阶段/周期奖励发放、签到与补签、排行榜缓存、离线储蓄、跨服刷新通知、客户端菜单数据同步
 * 与客户端包处理等全部业务逻辑。同时实现 {@link Listener} 处理玩家退出事件。
 */
public class OnlineRewardsService implements Listener {
    private static final Gson DELIVERY_JSON = new Gson();
    private static final int PROGRESS_BATCH_SIZE = 128;
    private static final long PRESENCE_TTL_MILLIS = 150_000L;
    private final OnlineRewardsDeliveryWorker<DeliverySession> deliveryWorker;
    private final OnlineRewardsScheduledAwards scheduledAwards;
    private final OnlineRewardsMenuReads menuReads;
    private final OnlineRewardsPlaceholderReads placeholderReads;
    private final Map<UUID, CompletableFuture<OnlineRewardsPlayerState>> requestedStates = new ConcurrentHashMap<>();
    private final Map<UUID, PendingView> pendingViews = new java.util.HashMap<>();
    private final Map<UUID, PendingView> waitingViewReads = new LinkedHashMap<>();
    private final Set<UUID> joinRemindersSent = new HashSet<>();
    private int runningViewReads;
    private OnlineRewardsItemDelivery itemDelivery;
    private OnlineRewardsItemMail durableItemMail;
    private final UUID presenceServiceId = UUID.randomUUID();
    private volatile boolean active;
    private volatile boolean stopping;
    private final Set<CompletableFuture<Void>> pendingDispatches = new HashSet<>();
    private final CompletableFuture<Void> databaseStopped = new CompletableFuture<>();
    private final ModuleExecutor databaseWriter = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(128), task -> {
            Thread thread = new Thread(task, "AXS-OnlineRewards-Write");
            thread.setDaemon(true);
            return thread;
        });
    private final ModuleExecutor leaderboardWorker = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "AXS-OnlineRewards-Leaderboard");
            thread.setDaemon(true);
            return thread;
        });
    private final ModuleExecutor finalizer = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "AXS-OnlineRewards-Close");
            thread.setDaemon(true);
            return thread;
        });

    public static final String MENU_UI_RESOURCE_PATH = "arcartx/ui/online_rewards_menu.yml";
    public static final String MENU_UI_FILE_PATH = "ui/online_rewards_menu.yml";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;
    private static final int LEADERBOARD_CACHE_SIZE = 10;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final OnlineRewardsModuleConfiguration configuration;
    private final MessageProvider messages;
    private final OnlineRewardsRepository repository;
    private final ClientBridgeAPI clientBridge;
    private final PacketBridgeAPI packetBridge;
    private final PacketGuardAPI packetGuard;
    private final Supplier<SignalDispatchable> signalProvider;
    private final Supplier<ChatCardSendable> chatCardProvider;
    private final Supplier<TitleGrantable> titleProvider;
    private final Supplier<SubtitlePlayable> subtitleProvider;
    private final Supplier<QQBotBroadcastable> qqBotProvider;
    private final Supplier<EventBusCapability> eventBusProvider;
    private final Function<Boolean, File> menuUiFileExporter;
    private final OnlineRewardsStateEngine stateEngine;
    private final Clock clock;
    private final CrossServerAPI crossServer;
    private final Map<UUID, OnlineRewardsPlayerState> cachedStates = new ConcurrentHashMap<>();
    // 签到历史缓存：避免每次菜单刷新都全表扫描签到记录，签到/补签时失效
    private final Map<UUID, Set<String>> signInDatesCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> calendarMonthOffsets = new ConcurrentHashMap<>();
    private final Map<UUID, LocalDate> selectedCalendarDates = new ConcurrentHashMap<>();
    private final Map<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> actionTokensInFlight = ConcurrentHashMap.newKeySet();
    private final Map<OnlineRewardsLeaderboardScope, PeriodLeaderboard> leaderboardCache = new ConcurrentHashMap<>();
    private final AtomicBoolean leaderboardLoading = new AtomicBoolean();
    private final AtomicBoolean leaderboardDirty = new AtomicBoolean();
    private final AtomicBoolean goalCheckPending = new AtomicBoolean();
    private volatile boolean leaderboardActive;

    private BukkitTask minuteTask;
    private boolean minuteWritePending;
    private BukkitTask deliveryTask;
    private final Set<UUID> deliveryRequests = new HashSet<>();
    private Map<UUID, DeliverySession> deliveryAudience = Map.of();
    private long deliveryAudienceId;
    private boolean deliveryScanAll;
    private boolean deliveryContinue;
    private long nextScheduleQueueWarning;
    private BukkitTask leaderboardRefreshTask;
    private ScheduledTask schedulerMinuteTask;
    private ScheduledTask schedulerLeaderboardTask;
    private final List<ScheduledTask> timeBasedRewardTasks = new ArrayList<>();
    private SchedulerAPI scheduler;
    private CrossServerChannel crossServerChannel;
    private java.util.List<String> runtimeMenuUiIds = java.util.List.of();
    private java.util.List<String> registeredMenuUiIds = java.util.List.of();
    private xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry;
    private xuanmo.arcartxsuite.api.icon.IconResolver iconResolver;

    public OnlineRewardsService(
        JavaPlugin plugin,
        Logger logger,
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsRepository repository,
        ClientBridgeAPI clientBridge,
        PacketBridgeAPI packetBridge,
        PacketGuardAPI packetGuard,
        Supplier<SignalDispatchable> signalProvider,
        Supplier<ChatCardSendable> chatCardProvider,
        Supplier<TitleGrantable> titleProvider,
        Supplier<SubtitlePlayable> subtitleProvider,
        Supplier<QQBotBroadcastable> qqBotProvider,
        Supplier<EventBusCapability> eventBusProvider,
        MessageProvider messages,
        Function<Boolean, File> menuUiFileExporter,
        CrossServerAPI crossServer
    ) {
        this(plugin, logger, configuration, repository, clientBridge, packetBridge,
            packetGuard, signalProvider, chatCardProvider, titleProvider,
            subtitleProvider, qqBotProvider, eventBusProvider, messages, menuUiFileExporter,
            resolveClock(configuration), crossServer);
    }

    /**
     * 根据配置的时区创建 Clock，未配置时使用服务器默认时区。
     *
     * @param configuration 模块配置
     * @return 对应时区的 Clock
     */
    private static java.time.Clock resolveClock(OnlineRewardsModuleConfiguration configuration) {
        String timezone = configuration.timezone();
        if (timezone == null || timezone.isBlank()) {
            return java.time.Clock.systemDefaultZone();
        }
        try {
            return java.time.Clock.system(java.time.ZoneId.of(timezone));
        } catch (Exception exception) {
            return java.time.Clock.systemDefaultZone();
        }
    }

    OnlineRewardsService(
        JavaPlugin plugin,
        Logger logger,
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsRepository repository,
        ClientBridgeAPI clientBridge,
        PacketBridgeAPI packetBridge,
        PacketGuardAPI packetGuard,
        Supplier<SignalDispatchable> signalProvider,
        Supplier<ChatCardSendable> chatCardProvider,
        Supplier<TitleGrantable> titleProvider,
        Supplier<SubtitlePlayable> subtitleProvider,
        Supplier<QQBotBroadcastable> qqBotProvider,
        Supplier<EventBusCapability> eventBusProvider,
        MessageProvider messages,
        Function<Boolean, File> menuUiFileExporter,
        Clock clock,
        CrossServerAPI crossServer
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.configuration = configuration;
        this.messages = messages;
        this.repository = repository;
        this.menuReads = new OnlineRewardsMenuReads(repository);
        this.placeholderReads = new OnlineRewardsPlaceholderReads(repository, menuReads);
        this.deliveryWorker = new OnlineRewardsDeliveryWorker<>(repository, logger);
        this.scheduledAwards = new OnlineRewardsScheduledAwards(repository, logger);
        this.clientBridge = clientBridge;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.signalProvider = signalProvider;
        this.chatCardProvider = chatCardProvider;
        this.titleProvider = titleProvider;
        this.subtitleProvider = subtitleProvider;
        this.qqBotProvider = qqBotProvider;
        this.eventBusProvider = eventBusProvider;
        this.menuUiFileExporter = menuUiFileExporter;
        this.clock = clock;
        this.crossServer = crossServer;
        this.stateEngine = new OnlineRewardsStateEngine(configuration);
    }

    /** 注入 ItemSourceRegistry，用于发放物品奖励。 */
    public void setItemSourceRegistry(xuanmo.arcartxsuite.api.item.ItemSourceRegistry itemSourceRegistry) {
        this.itemSourceRegistry = itemSourceRegistry;
    }

    /** 注入调度器，用于底层定时任务和时刻奖励调度。 */
    public void setScheduler(SchedulerAPI scheduler) {
        this.scheduler = scheduler;
    }

    /** 注入图标解析器，用于菜单 packet 中解析奖励图标为 itemJson。 */
    public void setIconResolver(xuanmo.arcartxsuite.api.icon.IconResolver iconResolver) {
        this.iconResolver = iconResolver;
    }

    /**
     * 启动服务：初始化存储、绑定 UI、注册事件、刷新排行榜、调度每分钟 tick 与跨服通道。
     *
     * @throws SQLException 存储初始化失败时抛出
     * @throws IOException  UI 资源导出失败时抛出
     */
    public void start() throws SQLException, IOException {
        if (!Bukkit.getWorlds().isEmpty()) {
            var playerData = Bukkit.getWorlds().get(0).getWorldFolder().toPath().resolve("playerdata");
            itemDelivery = new OnlineRewardsItemDelivery(plugin, repository, playerData, () -> durableItemMail, logger);
        }
        active = true;
        bindMenuUi();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        leaderboardActive = true;
        refreshLeaderboardSnapshots();
        recordStartupClocks();
        scheduleSyncForOnlinePlayers();
        refreshPresence();
        // 底层调度：优先使用 SchedulerAPI，降级到 Bukkit Scheduler
        if (scheduler != null) {
            schedulerMinuteTask = scheduler.scheduleAtFixedRate(plugin, this::tickOnlinePlayers, 20L, 1200L);
            // 排行榜定时刷新：每 5 分钟刷新一次，避免每个玩家退出都触发 4 次 DB 查询
            schedulerLeaderboardTask = scheduler.scheduleAtFixedRate(plugin, this::refreshLeaderboardSnapshots, 100L, 6000L);
        } else {
            minuteTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickOnlinePlayers, 20L, 1200L);
            // 排行榜定时刷新：每 5 分钟刷新一次，避免每个玩家退出都触发 4 次 DB 查询
            leaderboardRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboardSnapshots, 100L, 6000L);
        }
        registerTimeBasedRewards();
        crossServerChannel = crossServer.openChannel(
            "onlinerewards",
            configuration.crossServer(),
            delivery -> handleCrossServerMessage(delivery.payload())
        );
        if (crossServerChannel.isActive()) {
            this.logger.fine("OnlineRewards 跨服通知已启用。");
        }
    }

    /**
     * 关闭服务：取消任务、关闭跨服通道、落盘全部脏状态、注销事件与 UI 并关闭存储。
     */
    public void shutdown() {
        CompletableFuture<Void> dispatched;
        synchronized (pendingDispatches) {
            if (stopping) return;
            stopping = true;
            active = false;
            dispatched = CompletableFuture.allOf(pendingDispatches.toArray(CompletableFuture[]::new));
        }
        String presenceNode = sharesPresence() ? crossServer.nodeId() : null;
        leaderboardActive = false;
        try {
        if (itemDelivery != null) itemDelivery.close();
        if (minuteTask != null) {
            minuteTask.cancel();
            minuteTask = null;
        }
        if (leaderboardRefreshTask != null) {
            leaderboardRefreshTask.cancel();
            leaderboardRefreshTask = null;
        }
        if (schedulerMinuteTask != null) {
            schedulerMinuteTask.cancel();
            schedulerMinuteTask = null;
        }
        if (schedulerLeaderboardTask != null) {
            schedulerLeaderboardTask.cancel();
            schedulerLeaderboardTask = null;
        }
        for (ScheduledTask task : timeBasedRewardTasks) {
            task.cancel();
        }
        timeBasedRewardTasks.clear();
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
        if (deliveryTask != null) {
            deliveryTask.cancel();
            deliveryTask = null;
        }
        deliveryRequests.clear();
        deliveryAudience = Map.of();
        qualifiedClocks.clear();
        startupClocks.clear();
        cachedStates.clear();
        signInDatesCache.clear();
        calendarMonthOffsets.clear();
        selectedCalendarDates.clear();
        actionTokens.clear();
        actionTokensInFlight.clear();
        pendingViews.clear();
        placeholderReads.clear();
        waitingViewReads.clear();
        joinRemindersSent.clear();
        leaderboardCache.clear();
        HandlerList.unregisterAll(this);
        unregisterMenuUi();
        } finally {
            if (itemDelivery != null) itemDelivery.close();
            scheduledAwards.close();
            menuReads.close();
            leaderboardWorker.shutdown();
            databaseWriter.shutdown();
            CompletableFuture.allOf(dispatched, scheduledAwards.stopped().toCompletableFuture(),
                leaderboardWorker.stopped().toCompletableFuture(), databaseWriter.stopped().toCompletableFuture(),
                menuReads.stopped().toCompletableFuture(), itemDelivery == null
                    ? CompletableFuture.completedFuture(null) : itemDelivery.stopped().toCompletableFuture())
                .handle((ignored, failure) -> {
                    // Item staging and external title callbacks can still enqueue a receipt during retirement.
                    deliveryWorker.close();
                    return failure;
                }).thenCombine(deliveryWorker.stopped(), (failure, ignored) -> failure)
                .whenComplete((failure, closeFailure) -> finishShutdown(presenceNode,
                    closeFailure != null ? closeFailure : failure));
        }
    }

    public boolean isStopping() { return stopping; }

    public CompletionStage<Void> databaseStopped() { return databaseStopped.copy(); }

    private <T> CompletionStage<T> writeAsync(Callable<T> action, java.util.function.Consumer<T> afterCommit) {
        var result = new CompletableFuture<T>();
        try {
            if (!active) throw new RejectedExecutionException("OnlineRewards 正在停用");
            databaseWriter.execute(() -> {
                T value;
                try { value = action.call(); }
                catch (Throwable failure) { result.completeExceptionally(failure); return; }
                try { if (afterCommit != null) runOnMain(() -> afterCommit.accept(value)); }
                finally { result.complete(value); }
            });
        } catch (RejectedExecutionException busy) {
            result.completeExceptionally(busy);
        }
        return result;
    }

    public void runOnMain(Runnable action) {
        if (!active) return;
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }
        try { Bukkit.getScheduler().runTask(plugin, () -> { if (active) action.run(); }); }
        catch (org.bukkit.plugin.IllegalPluginAccessException stopped) { /* The transaction has already finished. */ }
    }

    private void finishShutdown(String presenceNode, Throwable priorFailure) {
        var saved = new CompletableFuture<Void>();
        finalizer.execute(() -> {
            Throwable failure = priorFailure;
            try {
                if (presenceNode != null) repository.removePresence(presenceNode, presenceServiceId, null);
            } catch (Throwable writeFailure) {
                if (failure == null) failure = writeFailure;
                else failure.addSuppressed(writeFailure);
            } finally {
                try { repository.shutdown(); }
                catch (Throwable closeFailure) {
                    if (failure == null) failure = closeFailure;
                    else failure.addSuppressed(closeFailure);
                }
                if (failure == null) saved.complete(null);
                else saved.completeExceptionally(failure);
                finalizer.shutdown();
            }
        });
        CompletableFuture.allOf(saved, finalizer.stopped().toCompletableFuture()).whenComplete((ignored, failure) -> {
            if (failure == null) databaseStopped.complete(null);
            else databaseStopped.completeExceptionally(failure);
        });
    }

    /**
     * 返回已注册到 ArcartX 的 UI ID 列表（供 Module 记录到基类生命周期管理）。
     *
     * @return 已注册的 UI ID 列表，未注册时返回空列表
     */
    public java.util.List<String> registeredUiIds() {
        return registeredMenuUiIds;
    }

    /**
     * 返回跨服通道是否处于活跃状态。
     *
     * @return 跨服启用返回 {@code true}
     */
    public boolean crossServerActive() {
        return crossServerChannel != null && crossServerChannel.isActive();
    }

    /**
     * 客户端初始化完成回调，延迟同步玩家菜单数据。
     *
     * @param player 上线玩家
     */
    public void handleClientInitialized(Player player) {
        if (!active || player == null || !player.isOnline()) {
            return;
        }
        if (xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) {
            savePresence(Map.of(player.getUniqueId(), player.getName()));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncPlayer(player), configuration.clientSyncDelayTicks());
    }

    /**
     * 返回当前缓存的玩家状态数量。
     *
     * @return 缓存玩家数
     */
    public int cachedPlayerCount() {
        return cachedStates.size();
    }

    public boolean debug() {
        return configuration.debug();
    }

    public OnlineRewardsModuleConfiguration configuration() {
        return configuration;
    }

    public String runtimeMenuUiId() {
        return runtimeMenuUiIds.isEmpty() ? "" : runtimeMenuUiIds.get(0);
    }

    public java.util.List<String> runtimeMenuUiIds() {
        return runtimeMenuUiIds;
    }

    /**
     * 打开在线奖励菜单并发送初始数据包。
     *
     * @param player 目标玩家
     * @return 操作结果
     */
    public OnlineRewardsOperationResult openMenu(Player player) {
        if (player == null || !player.isOnline()) {
            return new OnlineRewardsOperationResult(false, "玩家当前不在线。");
        }
        if (!canUseMenu(player)) {
            return new OnlineRewardsOperationResult(false, "你没有权限打开在线奖励界面。");
        }
        if (!active || packetBridge == null || runtimeMenuUiIds.isEmpty()) {
            return new OnlineRewardsOperationResult(false, "在线奖励 UI 当前不可用。");
        }

        if (!packetBridge.openUiAll(player, runtimeMenuUiIds)) {
            return new OnlineRewardsOperationResult(false, "在线奖励 UI 当前不可用。");
        }
        actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
        sendMenuPacket(player, "init");
        return new OnlineRewardsOperationResult(true, "已打开在线奖励界面。");
    }

    /**
     * 处理客户端菜单数据包，根据 action 分发到打开、日历翻页、预览、补签、签到、刷新等逻辑。
     *
     * @param player   玩家
     * @param packetId 数据包 id
     * @param data     数据载荷
     * @return 是否已处理（包 id 不匹配返回 {@code false}）
     */
    public boolean handleClientPacket(Player player, String packetId, List<String> data) {
        if (player == null || !player.isOnline() || packetId == null || !configuration.ui().packetId().equalsIgnoreCase(packetId)) {
            return false;
        }

        if (!active || !canUseMenu(player)) return true;
        String action = data == null || data.isEmpty() ? "refresh" : data.get(0);
        if (action == null) return true;
        action = action.trim().toLowerCase(Locale.ROOT);
        if ("open".equals(action)) {
            openMenu(player);
            return true;
        }
        if (!hasOpenMenu(player)) return true;
        // PacketGuard 已由 ClientPacketRouter 在路由层统一调用，此处不再重复检查。
        switch (action) {
            case "calendar_prev" -> {
                calendarMonthOffsets.put(player.getUniqueId(), Math.max(-1, calendarMonthOffsets.getOrDefault(player.getUniqueId(), 0) - 1));
                selectedCalendarDates.remove(player.getUniqueId());
                refreshMenu(player);
            }
            case "calendar_next" -> {
                calendarMonthOffsets.put(player.getUniqueId(), Math.min(1, calendarMonthOffsets.getOrDefault(player.getUniqueId(), 0) + 1));
                selectedCalendarDates.remove(player.getUniqueId());
                refreshMenu(player);
            }
            case "calendar_today" -> {
                calendarMonthOffsets.put(player.getUniqueId(), 0);
                selectedCalendarDates.put(player.getUniqueId(), currentPeriodContext().date());
                refreshMenu(player);
            }
            case "preview_day" -> {
                if (data == null || data.size() < 2) return true;
                LocalDate date = parseDate(data.get(1));
                if (date == null) return true;
                YearMonth currentMonth = YearMonth.from(currentPeriodContext().date());
                long offset = (date.getYear() - (long) currentMonth.getYear()) * 12
                    + date.getMonthValue() - currentMonth.getMonthValue();
                if (offset < -1 || offset > 1) return true;
                calendarMonthOffsets.put(player.getUniqueId(), (int) offset);
                selectedCalendarDates.put(player.getUniqueId(), date);
                refreshMenu(player);
            }
            case "makeup" -> {
                if (!acquireActionToken(player, data, 2)) return true;
                finishSignInPacket(player, makeupSignIn(player, data.get(1), false));
            }
            case "signin" -> {
                if (!acquireActionToken(player, data, 1)) return true;
                finishSignInPacket(player, signIn(player, false));
            }
            case "refresh" -> refreshMenu(player);
            default -> { }
        }
        return true;
    }

    private void finishSignInPacket(Player player, CompletionStage<OnlineRewardsOperationResult> operation) {
        UUID id = player.getUniqueId();
        var session = new DeliverySession(player, xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(id));
        operation.whenComplete((result, failure) -> runOnMain(() -> {
            if (Bukkit.getPlayer(id) != player) return;
            actionTokensInFlight.remove(id);
            if (!session.isCurrent() || !canUseMenu(player)) return;
            if (failure != null) {
                player.sendMessage("§c签到结果未确认，请查看签到记录后再试。");
            } else {
                if (result.success()) actionTokens.put(id, UUID.randomUUID().toString());
                player.sendMessage((result.success() ? "§a" : "§c") + result.message());
            }
            refreshMenu(player);
        }));
    }

    /** Reads the current persisted state without populating a connection's display cache. */
    private CompletionStage<OnlineRewardsPlayerState> queryPlayerState(UUID playerUuid) {
        if (!active) return CompletableFuture.failedFuture(new RejectedExecutionException("OnlineRewards 正在停用"));
        var request = new CompletableFuture<OnlineRewardsPlayerState>();
        var existing = requestedStates.putIfAbsent(playerUuid, request);
        if (existing != null) return existing;
        queryRead(() -> repository.loadState(playerUuid)).whenComplete((state, failure) -> {
            requestedStates.remove(playerUuid, request);
            if (failure == null) request.complete(state);
            else request.completeExceptionally(failure);
        });
        return request;
    }

    private <T> CompletionStage<T> queryRead(Callable<T> read) {
        if (!active) return CompletableFuture.failedFuture(new RejectedExecutionException("OnlineRewards 正在停用"));
        return menuReads.query(() -> {
            if (!active) throw new RejectedExecutionException("OnlineRewards 正在停用");
            return read.call();
        });
    }

    public CompletionStage<OnlineRewardsPlayerSnapshot> queryStatus(UUID playerUuid, String playerName) {
        return queryRead(() -> {
            var context = currentPeriodContext();
            var snapshot = repository.loadState(playerUuid);
            stateEngine.normalizeForPeriod(snapshot, context);
            if (playerName != null && !playerName.isBlank()) snapshot.setPlayerName(playerName);
            boolean signed = stateEngine.hasSignedToday(snapshot, context)
                || repository.hasSignInRecord(playerUuid, context.rewardDate());
            return new OnlineRewardsPlayerSnapshot(snapshot, stateEngine.snapshot(snapshot), signed);
        });
    }

    /**
     * 分页加载排行榜条目。
     *
     * @param scope    排行榜范围
     * @param pageSize 每页条数
     * @param page     页码（从 1 开始）
     * @return 当前周期的排行页
     */
    public CompletionStage<List<OnlineRewardsLeaderboardEntry>> loadLeaderboard(OnlineRewardsLeaderboardScope scope, int pageSize, int page) {
        int safePageSize = Math.max(1, pageSize);
        long offset = (Math.max(1, page) - 1L) * safePageSize;
        if (scope == null || offset > Integer.MAX_VALUE) return CompletableFuture.completedFuture(List.of());
        return queryRead(() -> List.copyOf(repository.loadLeaderboard(scope, currentPeriodKey(scope), (int) offset, safePageSize)));
    }

    /**
     * 返回排行榜缓存中指定名次的条目。
     *
     * @param scope 排行榜范围
     * @param rank  名次（从 1 开始）
     * @return 条目，越界或无效时返回 {@code null}
     */
    public OnlineRewardsLeaderboardEntry leaderboardEntry(OnlineRewardsLeaderboardScope scope, int rank) {
        if (scope == null || rank <= 0) {
            return null;
        }
        List<OnlineRewardsLeaderboardEntry> entries = leaderboardRows(scope);
        if (rank > entries.size()) {
            return null;
        }
        return entries.get(rank - 1);
    }

    /**
     * 返回排行榜缓存的条目数量。
     *
     * @param scope 排行榜范围
     * @return 缓存条目数
     */
    public int leaderboardSnapshotSize(OnlineRewardsLeaderboardScope scope) {
        return scope == null ? 0 : leaderboardRows(scope).size();
    }

    private List<OnlineRewardsLeaderboardEntry> leaderboardRows(OnlineRewardsLeaderboardScope scope) {
        var entries = leaderboardCache.get(scope);
        if (entries != null && entries.period().equals(currentPeriodKey(scope))) return entries.rows();
        if (leaderboardActive && !leaderboardLoading.get()) refreshLeaderboardSnapshots();
        return List.of();
    }

    public CompletionStage<List<SignInMonth>> querySignInHistory(UUID playerUuid, int limit) {
        return queryRead(() -> {
            var months = new java.util.TreeMap<YearMonth, Integer>(Comparator.reverseOrder());
            for (String date : repository.loadAllSignInDates(playerUuid)) {
                LocalDate parsed = parseDate(date);
                if (parsed != null) months.merge(YearMonth.from(parsed), 1, Integer::sum);
            }
            return months.entrySet().stream().limit(Math.max(1, limit))
                .map(entry -> new SignInMonth(entry.getKey(), entry.getValue())).toList();
        });
    }

    public record SignInMonth(YearMonth month, int signedDays) {}
    private record PeriodLeaderboard(String period, List<OnlineRewardsLeaderboardEntry> rows) {}

    public int countSignInDaysForMonth(UUID playerUuid, YearMonth month) {
        if (!active) return 0;
        var history = signInDatesCache.get(playerUuid);
        if (history == null) return placeholderReads.days(playerUuid, month);
        int signed = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            if (history.contains(month.atDay(day).toString())) signed++;
        }
        return signed;
    }

    public int todaySignInCount() {
        return active ? menuReads.goalsForDisplay(currentPeriodContext().rewardDate()).signedCount() : 0;
    }

    public CompletionStage<Boolean> hasSignedTodayPublic(UUID playerUuid) {
        return queryRead(() -> repository.hasSignInRecord(playerUuid, currentPeriodContext().rewardDate()));
    }

    public CompletionStage<Integer> todayOnlineMinutesPublic(UUID playerUuid) {
        return queryPlayerState(playerUuid).thenApply(state ->
            currentPeriodContext().rewardDate().equals(state.rewardDate()) ? state.onlineMinutes() : 0);
    }

    public CompletionStage<Integer> weeklyOnlineMinutesPublic(UUID playerUuid) {
        return queryPlayerState(playerUuid).thenApply(state ->
            currentPeriodContext().weekKey().equals(state.weekKey()) ? state.weekMinutes() : 0);
    }

    public CompletionStage<Integer> monthlyOnlineMinutesPublic(UUID playerUuid) {
        return queryPlayerState(playerUuid).thenApply(state ->
            currentPeriodContext().monthKey().equals(state.monthKey()) ? state.monthMinutes() : 0);
    }

    public PlaceholderValues placeholderValues(UUID id, boolean needSignInStatus) {
        if (!active) return PlaceholderValues.EMPTY;
        var context = currentPeriodContext();
        var state = cachedStates.get(id);
        boolean signed;
        if (state == null) {
            var stored = placeholderReads.player(id, context.rewardDate());
            if (stored == null) return PlaceholderValues.EMPTY;
            state = stored.state();
            signed = stored.signedToday();
        } else {
            var dates = signInDatesCache.get(id);
            signed = context.rewardDate().equals(state.lastSignInDate()) || dates != null && dates.contains(context.rewardDate());
            if (!signed && needSignInStatus && dates == null) {
                var stored = placeholderReads.player(id, context.rewardDate());
                signed = stored != null && stored.signedToday();
            }
        }
        return new PlaceholderValues(
            context.rewardDate().equals(state.rewardDate()) ? state.onlineMinutes() : 0,
            context.weekKey().equals(state.weekKey()) ? state.weekMinutes() : 0,
            context.monthKey().equals(state.monthKey()) ? state.monthMinutes() : 0,
            state.totalMinutes(), state.offlineSavingsMinutes(), signed, state.signInStreak(), state.signInTotal());
    }

    public record PlaceholderValues(int today, int week, int month, int total, int savings,
                                    boolean signedToday, int streak, int signIns) {
        static final PlaceholderValues EMPTY = new PlaceholderValues(0, 0, 0, 0, 0, false, 0, 0);
    }

    /**
     * 为在线玩家增加指定分钟数的在线时长（跨模块能力接口）。
     *
     * @param player  玩家
     * @param minutes 增加的分钟数
     * @return 操作是否成功
     */
    public CompletionStage<Boolean> addOnlineMinutesPublic(Player player, int minutes) {
        return adjustOnlineTime(player, "add", Math.max(0, minutes)).thenApply(OnlineRewardsOperationResult::success);
    }

    /**
     * 为在线玩家增加指定数量的补签卡（跨模块能力接口）。
     *
     * @param player 玩家
     * @param amount 增加的数量
     * @return 操作是否成功
     */
    public CompletionStage<Boolean> addMakeupCardsPublic(Player player, int amount) {
        return adjustMakeupCards(player, "add", Math.max(0, amount)).thenApply(OnlineRewardsOperationResult::success);
    }

    /**
     * 立即发放玩家当前已达阈值但未领取的阶段奖励（跨模块能力接口）。
     *
     * @param player 玩家
     */
    public CompletionStage<Boolean> grantDailyRewardsPublic(Player player) {
        if (player == null || !player.isOnline()) return CompletableFuture.completedFuture(false);
        String name = player.getName();
        return updateDuration(player, (state, unused) -> {
            var context = currentPeriodContext();
            normalizeManualProgress(state, context);
            var triggered = collectReachedRewards(state);
            return triggered.isEmpty() ? ProgressPlan.UNCHANGED
                : new ProgressPlan(true, dailyPlans(name, state, context, triggered));
        }).thenApply(ProgressResult::queuedRewards);
    }

    /**
     * 返回距离下一个未达成的全服签到目标所需人数，无目标时返回 0。
     *
     * @return 下一个目标所需人数
     */
    public int nextServerSignInGoalRequired() {
        OnlineRewardsServerSignInGoalConfiguration goalConfig = configuration.serverSignInGoal();
        if (!active || !goalConfig.enabled() || goalConfig.targets().isEmpty()) return 0;
        return nextServerSignInGoalRequired(goalConfig, menuReads.goalsForDisplay(currentPeriodContext().rewardDate()).triggeredGoalIds());
    }

    private int nextServerSignInGoalRequired(OnlineRewardsServerSignInGoalConfiguration goalConfig, Set<String> triggered) {
        if (!goalConfig.enabled()) return 0;
        int next = 0;
        for (OnlineRewardsServerSignInGoalTarget target : goalConfig.targets()) {
            if (triggered.contains(target.id())) continue;
            if (next == 0 || target.required() < next) {
                next = target.required();
            }
        }
        return next;
    }

    /**
     * 执行签到，发放基础与加成奖励并刷新排行榜。
     *
     * @param player 签到玩家
     * @return 操作结果
     */
    public CompletionStage<OnlineRewardsOperationResult> signIn(Player player) {
        return signIn(player, true);
    }

    private CompletionStage<OnlineRewardsOperationResult> signIn(Player player, boolean updateMenu) {
        return submitSignIn(player, currentPeriodContext().date(), false, updateMenu);
    }

    /**
     * 消耗补签卡对当月历史日期进行补签。
     *
     * @param player  玩家
     * @param rawDate 目标日期字符串
     * @return 操作结果
     */
    public CompletionStage<OnlineRewardsOperationResult> makeupSignIn(Player player, String rawDate) {
        return makeupSignIn(player, rawDate, true);
    }

    private CompletionStage<OnlineRewardsOperationResult> makeupSignIn(Player player, String rawDate, boolean updateMenu) {
        if (!configuration.signIn().makeup().enabled()) {
            return rejectedOperation(configuration.signIn().makeup().invalidDateMessage());
        }
        LocalDate date = parseDate(rawDate);
        OnlineRewardsPeriodContext context = currentPeriodContext();
        if (date == null || !YearMonth.from(date).equals(YearMonth.from(context.date())) || !date.isBefore(context.date())) {
            return rejectedOperation(configuration.signIn().makeup().invalidDateMessage());
        }
        return submitSignIn(player, date, true, updateMenu);
    }

    private static CompletionStage<OnlineRewardsOperationResult> rejectedOperation(String message) {
        return CompletableFuture.completedFuture(new OnlineRewardsOperationResult(false, message));
    }

    private CompletionStage<OnlineRewardsOperationResult> submitSignIn(Player player, LocalDate date, boolean makeup, boolean updateMenu) {
        if (player == null || !player.isOnline()) return rejectedOperation("玩家当前不在线。");
        UUID id = player.getUniqueId();
        String name = player.getName();
        var session = new DeliverySession(player, xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(id));
        if (!session.snapshot().admitted()) return rejectedOperation("玩家尚未完成登录。");
        var bonus = selectedPermissionBonus(player);
        return writeAsync(() -> {
            if (!session.authCurrent()) throw new RejectedExecutionException("签到会话已变化");
            return commitSignIn(id, name, date, makeup, bonus);
        }, committed -> finishSignIn(session, date, makeup, updateMenu, committed)).handle((committed, failure) -> {
            if (failure == null) return committed.result();
            if (!(failure instanceof RejectedExecutionException)) logger.log(Level.WARNING, "签到结果待核对: " + id + " " + date, failure);
            return new OnlineRewardsOperationResult(false, "签到结果未确认，请查看签到记录后再试。");
        });
    }

    private void finishSignIn(DeliverySession session, LocalDate date, boolean makeup, boolean updateMenu, SignInWrite write) {
        Player player = session.player();
        UUID id = session.snapshot().playerId();
        if (write.result().success()) menuReads.invalidateGoals();
        if (session.isCurrent()) {
            if (makeup || write.result().success()) {
                cachedStates.computeIfPresent(id, (ignored, state) -> {
                    var updated = state.copy();
                    if (makeup) updated.setMakeupCards(write.commit().makeupCards());
                    if (write.result().success()) {
                        updated.setLastSignInDate(write.history().latestDate());
                        updated.setSignInStreak(write.history().streak());
                        updated.setSignInTotal(write.commit().dates().size());
                    }
                    return updated;
                });
            }
            if (write.result().success()) {
                signInDatesCache.put(id, write.commit().dates());
                if (makeup) selectedCalendarDates.put(id, date);
                refreshLeaderboardSnapshots();
            }
            if (write.commit().status() != SignInStatus.NO_CARDS) {
                scheduleOnlineDeliveries(player);
                checkServerSignInGoals(player);
            }
            if (updateMenu) refreshMenu(player);
        }
        if (write.result().success()) publishRefresh(id);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) {
            savePresence(Map.of(player.getUniqueId(), player.getName()));
            recoverItemDelivery(player);
        }
    }

    @EventHandler
    public void onAdmission(xuanmo.arcartxsuite.api.auth.AuthAdmissionEvent event) {
        if (!active || !event.snapshot().admitted()) return;
        Player player = Bukkit.getPlayer(event.snapshot().playerId());
        if (player != null && player.isOnline()) {
            savePresence(Map.of(player.getUniqueId(), player.getName()));
            syncPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        // 在线时长以周期结算的完整分钟为准，离服按已保存的进度处理储蓄并清理连接凭证。
        var previous = qualifiedClocks.remove(playerUuid);
        startupClocks.remove(playerUuid);
        UUID connection = previous == null ? xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(playerUuid).connectionId()
            : previous.connectionId();
        deliveryRequests.remove(playerUuid);
        if (itemDelivery != null) itemDelivery.forget(playerUuid);
        String presenceNode = sharesPresence() ? crossServer.nodeId() : null;
        boolean savings = configuration.offlineSavings().enabled();
        if (active && (savings || presenceNode != null || connection != null)) {
            String name = player.getName();
            var context = currentPeriodContext();
            writeAsync(() -> {
                if (presenceNode != null) {
                    try { repository.removePresence(presenceNode, presenceServiceId, playerUuid); }
                    catch (SQLException failure) {
                        logger.log(Level.WARNING, "删除在线奖励跨服在线记录失败: " + playerUuid, failure);
                    }
                }
                ProgressResult result = null;
                if (savings) {
                    var request = new ProgressRequest(playerUuid, name, null, (state, unused) ->
                        new ProgressPlan(stateEngine.storeOfflineSavings(state, context), List.of()));
                    result = repository.updateProgress(List.of(request)).get(0);
                }
                if (connection != null) {
                    try { repository.forgetAttendance(playerUuid, connection); }
                    catch (SQLException failure) { logger.log(Level.WARNING, "清理已关闭的在线计时凭证失败: " + playerUuid, failure); }
                }
                return result;
            }, result -> {
                if (result != null && result.changed()) acceptProgress(result, null);
            }).whenComplete((ignored, failure) -> {
                if (failure != null) logger.log(Level.WARNING, "离服状态未确认: " + playerUuid, failure);
            });
        }
        cachedStates.remove(playerUuid);
        placeholderReads.invalidate(playerUuid, currentPeriodContext().date());
        signInDatesCache.remove(playerUuid);
        calendarMonthOffsets.remove(playerUuid);
        selectedCalendarDates.remove(playerUuid);
        actionTokens.remove(playerUuid);
        actionTokensInFlight.remove(playerUuid);
        pendingViews.remove(playerUuid);
        waitingViewReads.remove(playerUuid);
        joinRemindersSent.remove(playerUuid);
        // 不在退出时刷新排行榜，改为定时刷新避免高峰期 DB 压力
    }

    void tickOnlinePlayers() {
        if (!active) return;
        refreshPresence();
        boolean goalChecked = false;
        OnlineRewardsPeriodContext context = currentPeriodContext();
        var staged = new LinkedHashMap<UUID, MinuteProgress>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            var snapshot = xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(player.getUniqueId());
            if (!snapshot.admitted()) continue;
            if (!goalChecked) {
                goalChecked = true;
                checkServerSignInGoals(player);
            }
            if (minuteWritePending) break;
            MinuteProgress update = prepareMinuteProgress(player, snapshot, context);
            if (update != null) staged.put(snapshot.playerId(), update);
        }
        if (!staged.isEmpty()) commitMinuteProgress(staged);
        retryWaitingItemDeliveries();
        scheduleOnlineDeliveries();
    }

    private MinuteProgress prepareMinuteProgress(Player player, AuthSnapshot snapshot, OnlineRewardsPeriodContext context) {
        UUID id = snapshot.playerId();
        OnlineRewardsPlayerState cached = cachedStates.get(id);
        String name = player.getName();
        long eligible = eligibleMinutes(id);
        var initial = startupClocks.get(id);
        long baseline = initial != null && initial.connectionId().equals(snapshot.connectionId()) ? initial.eligibleMinutes() : 0;
        var clock = snapshot.connectionId() == null ? null : new Attendance(snapshot.connectionId(), eligible, baseline);
        var acknowledged = qualifiedClocks.get(id);
        boolean pendingMinutes = eligible > 0 && clock != null && (acknowledged == null
            || !clock.connectionId().equals(acknowledged.connectionId()) || eligible > acknowledged.eligibleMinutes());
        if (!pendingMinutes && cached != null
                && context.rewardDate().equals(cached.rewardDate()) && context.weekKey().equals(cached.weekKey())
                && context.monthKey().equals(cached.monthKey()) && name.equals(cached.playerName())
                && (cached.offlineSavingsMinutes() <= 0 || cached.offlineSavingsDay().isBlank()
                    || cached.offlineSavingsDay().compareTo(context.rewardDate()) >= 0)) return null;

        return new MinuteProgress(new DeliverySession(player, snapshot), name, clock, selectedTimeMultiplier(player));
    }

    private ProgressPlan planMinuteProgress(String name, OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context,
                                            double multiplier, long qualified) {
        if (state.rewardDate().compareTo(context.rewardDate()) > 0) return ProgressPlan.UNCHANGED;
        boolean changed = stateEngine.normalizeForPeriod(state, context);
        boolean redeemed = stateEngine.redeemOfflineSavings(state, context);
        changed |= redeemed;
        if (!name.equals(state.playerName())) {
            state.setPlayerName(name);
            changed = true;
        }
        if (qualified == 0 && !changed) return ProgressPlan.UNCHANGED;
        var rewards = new ArrayList<RewardBatch>();
        if (qualified > 0) {
            int gained = resolveOnlineMinuteGain(multiplier, state, qualified);
            OnlineRewardsTickResult result = stateEngine.advanceMinutes(state, context, gained);
            stateEngine.recordSavingsEarnings(state, gained);
            rewards.addAll(dailyPlans(name, state, context, result.triggeredRewards()));
            for (OnlineRewardsPeriodicReward reward : stateEngine.checkWeeklyRewards(state)) {
                rewards.add(periodicPlan(name, reward, context, "1-weekly", context.weekKey(), "本周"));
            }
            for (OnlineRewardsPeriodicReward reward : stateEngine.checkMonthlyRewards(state)) {
                rewards.add(periodicPlan(name, reward, context, "2-monthly", context.monthKey(), "本月"));
            }
        } else if (redeemed) {
            rewards.addAll(dailyPlans(name, state, context, collectReachedRewards(state)));
        }
        return new ProgressPlan(true, rewards);
    }

    private void commitMinuteProgress(Map<UUID, MinuteProgress> staged) {
        minuteWritePending = true;
        writeAsync(() -> {
            var context = currentPeriodContext();
            var requests = staged.values().stream().map(input -> new ProgressRequest(input.session().snapshot().playerId(),
                input.name(), input.clock(), (state, qualified) ->
                    planMinuteProgress(input.name(), state, context, input.multiplier(), qualified))).toList();
            var committed = new ArrayList<ProgressResult>(requests.size());
            for (int from = 0; from < requests.size(); from += PROGRESS_BATCH_SIZE) {
                try {
                    committed.addAll(repository.updateProgress(requests.subList(from, Math.min(requests.size(), from + PROGRESS_BATCH_SIZE))));
                } catch (SQLException | RuntimeException failure) {
                    logger.log(Level.WARNING, "在线时长批次未确认，将从计时凭证继续", failure);
                    break;
                }
            }
            return committed;
        }, committed -> {
            minuteWritePending = false;
            boolean rankChanged = false;
            boolean rewards = false;
            for (var result : committed) {
                var input = staged.get(result.playerUuid());
                var clock = input.clock();
                if (clock != null && input.session().isCurrent()) {
                    qualifiedClocks.put(result.playerUuid(), new Attendance(clock.connectionId(), result.accountedMinutes()));
                }
                acceptProgress(result, input.session());
                rankChanged |= result.changed();
                rewards |= result.queuedRewards();
            }
            if (rankChanged) refreshLeaderboardSnapshots();
            if (rewards) scheduleOnlineDeliveries();
        }).whenComplete((ignored, failure) -> {
            if (failure != null) runOnMain(() -> minuteWritePending = false);
        });
    }

    private record MinuteProgress(DeliverySession session, String name, Attendance clock, double multiplier) {}

    private void scheduleSyncForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            handleClientInitialized(player);
        }
    }

    private void bindMenuUi() throws IOException {
        runtimeMenuUiIds = new java.util.ArrayList<>(configuration.ui().menuUiIds());
        registeredMenuUiIds = new java.util.ArrayList<>();
        if (packetBridge == null) {
            return;
        }

        File uiFile = menuUiFileExporter.apply(configuration.ui().overwriteUiFiles());
        if (!configuration.ui().registerUiOnEnable()) {
            java.util.List<String> normalized = new java.util.ArrayList<>();
            for (String candidateUiId : configuration.ui().menuUiIds()) {
                normalized.add(xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.normalizeUiId(candidateUiId, uiFile));
            }
            runtimeMenuUiIds = java.util.List.copyOf(normalized);
            this.logger.fine("ArcartX OnlineRewards UI 自动注册已关闭，将直接使用 UI 标识: " + runtimeMenuUiIds);
            return;
        }

        java.util.List<String> resolvedRuntime = new java.util.ArrayList<>();
        java.util.List<String> resolvedRegistered = new java.util.ArrayList<>();
        for (String candidateUiId : configuration.ui().menuUiIds()) {
            xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI.UiRegistrationResult registration = packetBridge.registerOrReloadUi(candidateUiId, uiFile);
            if (!registration.success()) {
                throw new IOException("注册在线奖励 UI 失败 (" + candidateUiId + "): " + registration.message());
            }
            resolvedRuntime.add(registration.runtimeUiId());
            resolvedRegistered.add(registration.registeredUiId());
        }
        runtimeMenuUiIds = java.util.List.copyOf(resolvedRuntime);
        registeredMenuUiIds = java.util.List.copyOf(resolvedRegistered);
    }

    private void unregisterMenuUi() {
        if (packetBridge == null || registeredMenuUiIds.isEmpty()) {
            registeredMenuUiIds = java.util.List.of();
            return;
        }
        for (String id : registeredMenuUiIds) {
            packetBridge.unregisterUi(id);
        }
        registeredMenuUiIds = java.util.List.of();
    }

    private void syncPlayer(Player player) {
        if (!active || player == null || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player) {
            return;
        }
        var auth = xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(player.getUniqueId());
        if (!auth.admitted()) return;
        scheduleOnlineDeliveries(player);
        recoverItemDelivery(player);
        requestView(player, auth, true, false, false);
    }

    private void syncPlayerView(Player player, OnlineRewardsPlayerSnapshot snapshot, OnlineRewardsPeriodContext context) {
        OnlineRewardsPlayerState state = snapshot.state();
        pushSnapshot(player, snapshot.progress());
        if (configuration.signIn().reminderOnJoin() && !snapshot.signedToday() && joinRemindersSent.add(player.getUniqueId())) {
            player.sendMessage(renderSignInText(configuration.signIn().signInReminderMessage(), player, new OnlineRewardsSignInResult(
                false,
                false,
                state.signInStreak(),
                state.signInTotal(),
                context.rewardDate(),
                context.date().getDayOfMonth()
            )));
        }
        if (configuration.debug()) {
            this.logger.info(
                "OnlineRewards sync -> player="
                    + player.getName()
                    + " | minutes="
                    + state.onlineMinutes()
                    + " | stage="
                    + state.rewardStage()
                    + " | week="
                    + state.weekMinutes()
                    + " | month="
                    + state.monthMinutes()
                    + " | total="
                    + state.totalMinutes()
                    + " | variable="
                    + configuration.progressVariableName()
            );
        }
    }

    private void refreshMenu(Player player) {
        sendMenuPacket(player, "update");
    }

    private boolean canUseMenu(Player player) {
        return player.hasPermission("arcartxsuite.onlinerewards.use")
            && xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId());
    }

    private boolean hasOpenMenu(Player player) {
        if (packetBridge == null) return false;
        for (String uiId : runtimeMenuUiIds) {
            if (packetBridge.isUiOpen(player, uiId)) return true;
        }
        return false;
    }

    private void sendMenuPacket(Player player, String handlerName) {
        if (!active || player == null || !player.isOnline() || packetBridge == null || runtimeMenuUiIds.isEmpty()) {
            return;
        }
        if (hasOpenMenu(player) && player.hasPermission("arcartxsuite.onlinerewards.use")) {
            requestView(player, xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(player.getUniqueId()),
                false, true, "init".equals(handlerName));
        }
    }

    private void requestView(Player player, AuthSnapshot auth, boolean sync, boolean menu, boolean initialize) {
        if (!auth.admitted()) return;
        UUID id = auth.playerId();
        var pending = pendingViews.get(id);
        if (pending != null && pending.session.player() == player && pending.session.matches(auth)) {
            pending.sync |= sync;
            pending.menu |= menu;
            pending.initialize |= initialize;
            return;
        }
        pending = new PendingView(new DeliverySession(player, auth), sync, menu, initialize);
        pendingViews.put(id, pending);
        loadView(id, pending);
    }

    private void loadView(UUID id, PendingView pending) {
        waitingViewReads.put(id, pending);
        startWaitingViews();
    }

    private void startWaitingViews() {
        while (active && runningViewReads < OnlineRewardsMenuReads.CAPACITY && !waitingViewReads.isEmpty()) {
            var iterator = waitingViewReads.entrySet().iterator();
            var entry = iterator.next();
            UUID id = entry.getKey();
            PendingView pending = entry.getValue();
            iterator.remove();
            if (pendingViews.get(id) != pending) continue;
            runningViewReads++;
            var date = pending.menu ? currentPeriodContext().rewardDate() : null;
            menuReads.load(id, !cachedStates.containsKey(id), !signInDatesCache.containsKey(id), date,
                () -> active && pending.session.authCurrent()).whenComplete((loaded, failure) -> runOnMain(() -> {
                    runningViewReads--;
                    try { finishView(id, pending, loaded, failure); }
                    finally { startWaitingViews(); }
                }));
        }
    }

    private void finishView(UUID id, PendingView pending, OnlineRewardsMenuReads.Loaded loaded, Throwable failure) {
        if (pendingViews.get(id) != pending) return;
        if (!pending.session.isCurrent()) {
            pendingViews.remove(id);
            return;
        }
        Player player = pending.session.player();
        boolean menu = pending.menu && hasOpenMenu(player) && canUseMenu(player);
        if (!pending.sync && !menu) {
            pendingViews.remove(id);
            return;
        }
        pending.menu = menu;
        if (pending.stale) {
            pending.stale = false;
            loadView(id, pending);
            return;
        }
        if (failure != null) {
            pendingViews.remove(id);
            if (!(failure instanceof RejectedExecutionException)) logger.log(Level.FINE, "读取在线奖励界面失败: " + id, failure);
            if (menu && pending.initialize) player.sendMessage("§e在线奖励数据暂不可用，请刷新界面。");
            return;
        }
        if (loaded.state() != null) {
            cachedStates.putIfAbsent(id, loaded.state());
        }
        if (loaded.dates() != null) signInDatesCache.putIfAbsent(id, loaded.dates());
        var context = currentPeriodContext();
        var state = cachedStates.get(id);
        var dates = signInDatesCache.get(id);
        if (state == null || dates == null
                || menu && (loaded.goals() == null || !loaded.goals().date().equals(context.rewardDate()))) {
            loadView(id, pending);
            return;
        }
        pendingViews.remove(id);
        var snapshotState = state.copy();
        stateEngine.normalizeForPeriod(snapshotState, context);
        snapshotState.setPlayerName(player.getName());
        var snapshot = new OnlineRewardsPlayerSnapshot(snapshotState, stateEngine.snapshot(snapshotState),
            dates.contains(context.rewardDate()) || stateEngine.hasSignedToday(snapshotState, context));
        if (pending.sync) syncPlayerView(player, snapshot, context);
        if (menu) renderMenu(player, pending.initialize ? "init" : "update", snapshot, dates, context, loaded.goals());
    }

    private void renderMenu(Player player, String handlerName, OnlineRewardsPlayerSnapshot snapshot, Set<String> dates,
                            OnlineRewardsPeriodContext context, OnlineRewardsMenuReads.Goals goals) {
        var recipients = new ArrayList<String>(runtimeMenuUiIds.size());
        for (String uiId : runtimeMenuUiIds) if (packetBridge.isUiOpen(player, uiId)) recipients.add(uiId);
        if (recipients.isEmpty() || !canUseMenu(player)) return;
        int goalCurrent = goals.status().signedCount();
        OnlineRewardsServerSignInGoalConfiguration goalConfig = configuration.serverSignInGoal();
        int goalNext = nextServerSignInGoalRequired(goalConfig, goals.status().triggeredGoalIds());
        Map<String, Object> payload = OnlineRewardsMenuPacketFactory.build(
            configuration, snapshot, buildCalendarView(player, snapshot.state(), context, dates), goalCurrent, goalNext,
            player, iconResolver);
        payload.put("action_token", actionTokens.getOrDefault(player.getUniqueId(), ""));
        packetBridge.sendPacketToAll(
            player,
            recipients,
            handlerName,
            payload
        );
    }

    private static final class PendingView {
        final DeliverySession session;
        boolean sync;
        boolean menu;
        boolean initialize;
        boolean stale;

        PendingView(DeliverySession session, boolean sync, boolean menu, boolean initialize) {
            this.session = session;
            this.sync = sync;
            this.menu = menu;
            this.initialize = initialize;
        }
    }

    private boolean acquireActionToken(Player player, List<String> data, int tokenIndex) {
        UUID uuid = player.getUniqueId();
        String supplied = data != null && data.size() > tokenIndex ? data.get(tokenIndex) : "";
        String expected = actionTokens.get(uuid);
        return expected != null && expected.equals(supplied) && actionTokensInFlight.add(uuid);
    }


    /**
     * 执行时刻奖励：给所有在线玩家发放指定奖励。
     * <p>
     * 由 {@link SchedulerAPI#scheduleCalendar} 在指定时刻触发。
     *
     * @param reward 时刻奖励定义
     * @since 1.6.0
     */
    private void registerTimeBasedRewards() {
        if (scheduler == null || configuration.timeBasedRewards() == null) return;
        Set<String> configuredIds = new HashSet<>();
        for (OnlineRewardsTimeBasedReward reward : configuration.timeBasedRewards()) {
            if (!reward.enabled()) continue;
            if (!configuredIds.add(reward.id())) {
                logger.warning("忽略重复的时刻奖励 ID " + reward.id());
                continue;
            }
            try {
                ScheduleSpec spec = reward.schedule();
                ScheduledTask task;
                if (spec.mode() == ScheduleSpec.Mode.INTERVAL) {
                    // Interval schedules keep their independent per-registration tick cadence.
                    UUID registration = UUID.randomUUID();
                    AtomicLong sequence = new AtomicLong();
                    task = scheduler.scheduleAtFixedRate(plugin, () -> executeIntervalReward(reward,
                        registration, sequence.incrementAndGet()),
                        spec.intervalTicks(), spec.intervalTicks());
                } else {
                    task = registerCalendarReward(reward);
                }
                timeBasedRewardTasks.add(task);
                logger.info("时刻奖励已调度 " + reward.id() + " (" + spec + ")");
            } catch (RuntimeException failure) {
                logger.log(Level.WARNING, "时刻奖励调度失败 " + reward.id(), failure);
            }
        }
    }

    private ScheduledTask registerCalendarReward(OnlineRewardsTimeBasedReward reward) {
        ScheduleSpec spec = reward.schedule();
        ZoneId zone = ZoneId.systemDefault();
        return scheduler.scheduleCalendar(plugin,
            () -> executeCalendarReward(reward, calendarOccurrence(spec, ZonedDateTime.now(zone))), spec);
    }

    private static ZonedDateTime calendarOccurrence(ScheduleSpec spec, ZonedDateTime observed) {
        if (spec.mode() == ScheduleSpec.Mode.HOURLY) return observed.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        LocalDate date = observed.toLocalDate();
        boolean weekly = spec.mode() == ScheduleSpec.Mode.WEEKLY;
        if (weekly) date = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(spec.dayOfWeek()));
        var nominal = date.atTime(spec.hour(), spec.minute());
        ZoneId zone = observed.getZone();
        if (nominal.atZone(zone).isAfter(observed)) {
            nominal = weekly ? nominal.minusWeeks(1) : nominal.minusDays(1);
        }
        return nominal.atZone(zone);
    }

    private void executeIntervalReward(OnlineRewardsTimeBasedReward reward, UUID registration, long sequence) {
        executeTimeBasedReward(reward, "interval:" + registration + ":" + sequence, currentPeriodContext());
    }

    private void executeCalendarReward(OnlineRewardsTimeBasedReward reward, ZonedDateTime due) {
        String occurrence = reward.schedule().mode() == ScheduleSpec.Mode.HOURLY
            ? Long.toString(due.toEpochSecond()) : due.toLocalDate().toString();
        executeTimeBasedReward(reward, "at:" + occurrence, periodContext(due.withZoneSameInstant(clock.getZone()).toLocalDate()));
    }

    private void executeTimeBasedReward(OnlineRewardsTimeBasedReward reward, String occurrence,
                                        OnlineRewardsPeriodContext context) {
        if (!active) return;
        UUID rewardIdentity = UUID.nameUUIDFromBytes(reward.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String batch = "time:" + rewardIdentity + ":" + occurrence;
        String label = "时刻奖励 '" + reward.name() + "'";
        Map<UUID, List<Reward>> recipients = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) continue;
            recipients.put(player.getUniqueId(), durationPlan(player.getName(), context, label, reward.commands(),
                reward.mailPresetIds(), reward.itemRewards()));
        }
        if (recipients.isEmpty()) return;
        if (!scheduledAwards.submit(batch, Map.copyOf(recipients), accepted -> scheduledAwardCommitted(reward, accepted))) {
            long now = System.nanoTime();
            if (now >= nextScheduleQueueWarning) {
                logger.warning("时刻奖励后台队列已满，本次未入库 " + batch);
                nextScheduleQueueWarning = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            }
        }
    }

    private void scheduledAwardCommitted(OnlineRewardsTimeBasedReward reward, int accepted) {
        if (!active) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!active) return;
                scheduleOnlineDeliveries();
                if (accepted > 0 && reward.broadcastMessage() != null && !reward.broadcastMessage().isBlank()) {
                    Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', reward.broadcastMessage()));
                }
            });
        } catch (org.bukkit.plugin.IllegalPluginAccessException stopped) {
            // The durable rows will be read by the next service instance.
        }
    }

    private static String dailyBatchId(OnlineRewardsPeriodContext context, int stage) {
        // Old configurations persist an ordinal stage only. Keep that stage identity for this reward date.
        return "duration:0-daily:" + context.rewardDate() + ":" + String.format(Locale.ROOT, "%010d", stage);
    }

    private List<RewardBatch> dailyPlans(String playerName, OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context,
                                         List<OnlineRewardDefinition> triggered) {
        List<RewardBatch> batches = new ArrayList<>(triggered.size());
        int stage = state.rewardStage() - triggered.size();
        for (OnlineRewardDefinition reward : triggered) {
            batches.add(new RewardBatch(dailyBatchId(context, stage++), durationPlan(playerName, context,
                "奖励 '" + reward.name() + "'", reward.commands(), reward.mailPresetIds(), reward.itemRewards())));
        }
        return batches;
    }

    private RewardBatch periodicPlan(String playerName, OnlineRewardsPeriodicReward reward, OnlineRewardsPeriodContext context,
                                      String type, String period, String label) {
        UUID identity = UUID.nameUUIDFromBytes(reward.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String batch = "duration:" + type + ":" + (reward.repeat() ? period : "once") + ":" + identity;
        return new RewardBatch(batch, durationPlan(playerName, context, label + "奖励 '" + reward.name() + "'",
            reward.commands(), reward.mailPresetIds(), reward.itemRewards()));
    }

    private List<Reward> durationPlan(String playerName, OnlineRewardsPeriodContext context, String label,
                                      List<String> commands, List<String> presets, List<OnlineRewardsItemReward> items) {
        var rendering = new OnlineRewardsSignInResult(false, false, 0, 0,
            context.rewardDate(), context.date().getDayOfMonth());
        List<Reward> rewards = new ArrayList<>();
        for (String raw : commands) rewards.add(planCommand(raw, () -> renderCommand(raw, playerName, rendering)));
        for (String preset : presets) rewards.add(new Reward("MAIL", preset, label));
        for (OnlineRewardsItemReward item : items) rewards.add(new Reward("ITEM", DELIVERY_JSON.toJson(item), label));
        return List.copyOf(rewards);
    }

    private void pushSnapshot(Player player, OnlineRewardsProgressSnapshot snapshot) {
        if (player == null || !player.isOnline()) {
            return;
        }
        clientBridge.sendServerVariable(player, configuration.progressVariableName(), snapshot.progress());
        clientBridge.sendServerVariable(player, configuration.titleVariableName(), snapshot.title());
    }

    private void publishRefresh(UUID playerUuid) {
        if (playerUuid == null) return;
        placeholderReads.invalidate(playerUuid, currentPeriodContext().date());
        if (crossServerChannel != null && crossServerChannel.isActive()) {
            crossServerChannel.publish("refresh:" + playerUuid);
        }
    }

    private void handleCrossServerMessage(String message) {
        if (!active) return;
        if (configuration.debug()) {
            this.logger.info("收到 OnlineRewards 跨服消息: " + message);
        }
        if (message == null || message.isBlank() || !message.startsWith("refresh:")) {
            return;
        }
        String rawUuid = message.substring("refresh:".length()).trim();
        try {
            UUID playerUuid = UUID.fromString(rawUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!active) return;
                menuReads.invalidateGoals();
                placeholderReads.invalidate(playerUuid, currentPeriodContext().date());
                Player player = Bukkit.getPlayer(playerUuid);
                if (player == null || !player.isOnline()) {
                    return;
                }
                cachedStates.remove(playerUuid);
                signInDatesCache.remove(playerUuid);
                var pending = pendingViews.get(playerUuid);
                if (pending != null) pending.stale = true;
                syncPlayer(player);
                refreshMenu(player);
                refreshLeaderboardSnapshots();
            });
        } catch (IllegalArgumentException ignored) {
        }
    }

    private CompletionStage<ProgressResult> updateDuration(Player player, ProgressMutation action) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        var session = new DeliverySession(player, xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(id));
        var request = new ProgressRequest(id, name, null, (state, unused) -> {
            state.setPlayerName(name);
            return action.apply(state, unused);
        });
        return writeAsync(() -> repository.updateProgress(List.of(request)).get(0), result -> {
            if (!result.changed()) return;
            acceptProgress(result, session);
            if (result.queuedRewards()) scheduleOnlineDeliveries();
            refreshLeaderboardSnapshots();
            publishRefresh(id);
        });
    }

    private void acceptProgress(ProgressResult result, DeliverySession source) {
        UUID id = result.playerUuid();
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) return;
        if (source != null && !source.isCurrent() && player == source.player()) return;
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(id)) return;
        cachedStates.put(id, result.state());
        pushSnapshot(player, stateEngine.snapshot(result.state()));
        refreshMenu(player);
    }

    private void normalizeManualProgress(OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        if (state.rewardDate().compareTo(context.rewardDate()) > 0) {
            throw new IllegalStateException("数据库中的在线奖励周期晚于本节点");
        }
        stateEngine.normalizeForPeriod(state, context);
    }

    /**
     * 管理命令：按操作类型调整玩家在线时长。
     *
     * @param player    目标玩家
     * @param operation 操作类型（add/remove/set）
     * @param minutes   分钟数
     * @return 操作结果
     */
    public CompletionStage<OnlineRewardsOperationResult> adjustOnlineTime(Player player, String operation, int minutes) {
        if (player == null || !player.isOnline()) return rejectedOperation("目标玩家必须在线。");
        String mode = operation == null ? "" : operation.toLowerCase(Locale.ROOT);
        if (!mode.equals("add") && !mode.equals("remove") && !mode.equals("set")) {
            return rejectedOperation("无效在线时长操作: " + operation);
        }
        String name = player.getName();
        int safeMinutes = Math.max(0, minutes);
        return updateDuration(player, (state, unused) -> planTimeAdjustment(name, mode, safeMinutes, state)).thenApply(result -> {
            var state = result.state();
            return new OnlineRewardsOperationResult(true,
                "已更新 " + name + " 今日在线=" + OnlineRewardsTextFormats.formatMinutes(state.onlineMinutes())
                    + "，本周=" + OnlineRewardsTextFormats.formatMinutes(state.weekMinutes())
                    + "，本月=" + OnlineRewardsTextFormats.formatMinutes(state.monthMinutes())
                    + "，总计=" + OnlineRewardsTextFormats.formatMinutes(state.totalMinutes()));
        });
    }

    private ProgressPlan planTimeAdjustment(String name, String operation, int minutes, OnlineRewardsPlayerState state) {
        OnlineRewardsPeriodContext context = currentPeriodContext();
        normalizeManualProgress(state, context);

        List<OnlineRewardDefinition> triggeredRewards = new ArrayList<>();
        List<RewardBatch> milestones = new ArrayList<>();
        if ("add".equals(operation)) {
            triggeredRewards.addAll(stateEngine.advanceMinutes(state, context, minutes).triggeredRewards());
        } else if ("remove".equals(operation)) {
            state.setOnlineMinutes(state.onlineMinutes() - minutes);
            state.setWeekMinutes(state.weekMinutes() - minutes);
            state.setMonthMinutes(state.monthMinutes() - minutes);
            state.setTotalMinutes(state.totalMinutes() - minutes);
            // 回退 rewardStage 使其与减少后的 onlineMinutes 一致
            int reachedBefore = state.rewardStage();
            rewindRewardStage(state);
            // A pre-upgrade stage may have been paid before durable batches existed.
            for (int stage = state.rewardStage(); stage < reachedBefore; stage++) {
                milestones.add(new RewardBatch(dailyBatchId(context, stage), List.of()));
            }
        } else {
            int previousDaily = state.onlineMinutes();
            int delta = minutes - previousDaily;
            state.setOnlineMinutes(minutes);
            state.setWeekMinutes(state.weekMinutes() + delta);
            state.setMonthMinutes(state.monthMinutes() + delta);
            state.setTotalMinutes(state.totalMinutes() + delta);
            triggeredRewards.addAll(collectReachedRewards(state));
        }

        milestones.addAll(dailyPlans(name, state, context, triggeredRewards));
        return new ProgressPlan(true, milestones);
    }

    /**
     * 管理命令：按操作类型调整玩家离线储蓄分钟数。
     *
     * @param player    目标玩家
     * @param operation 操作类型（add/set/reset）
     * @param minutes   分钟数
     * @return 操作结果
     */
    public CompletionStage<OnlineRewardsOperationResult> adjustOfflineSavings(Player player, String operation, int minutes) {
        if (player == null || !player.isOnline()) return rejectedOperation("目标玩家必须在线。");
        String mode = operation == null ? "" : operation.toLowerCase(Locale.ROOT);
        if (!mode.equals("add") && !mode.equals("remove") && !mode.equals("take") && !mode.equals("set") && !mode.equals("reset")) {
            return rejectedOperation("无效离线储蓄操作: " + operation);
        }
        String name = player.getName();
        int safeMinutes = Math.max(0, minutes);
        return updateDuration(player, (state, unused) -> planSavingsAdjustment(state, mode, safeMinutes))
            .thenApply(result -> new OnlineRewardsOperationResult(true,
                "已更新 " + name + " 离线储蓄=" + OnlineRewardsTextFormats.formatMinutes(result.state().offlineSavingsMinutes())));
    }

    private ProgressPlan planSavingsAdjustment(OnlineRewardsPlayerState state, String operation, int safeMinutes) {
        normalizeManualProgress(state, currentPeriodContext());
        switch (operation) {
            case "add" -> state.setOfflineSavingsMinutes((int) Math.min(Integer.MAX_VALUE,
                (long) state.offlineSavingsMinutes() + safeMinutes));
            case "remove", "take" -> state.setOfflineSavingsMinutes(Math.max(0, state.offlineSavingsMinutes() - safeMinutes));
            case "set" -> state.setOfflineSavingsMinutes(safeMinutes);
            case "reset" -> state.setOfflineSavingsMinutes(0);
            default -> throw new IllegalArgumentException(operation);
        }
        if (state.offlineSavingsMinutes() == 0) {
            state.setOfflineSavingsDay("");
        } else if ("set".equals(operation) || ("add".equals(operation) && safeMinutes > 0)) {
            state.setOfflineSavingsDay(state.rewardDate());
        }
        return new ProgressPlan(true, List.of());
    }

    /**
     * 管理命令：按操作类型调整玩家补签卡数量。
     *
     * @param player    目标玩家
     * @param operation 操作类型（add/remove/set）
     * @param amount    数量
     * @return 操作结果
     */
    public CompletionStage<OnlineRewardsOperationResult> adjustMakeupCards(Player player, String operation, int amount) {
        if (player == null || !player.isOnline()) {
            return rejectedOperation("目标玩家必须在线。");
        }
        MakeupCardOperation cardOperation = switch (operation == null ? "" : operation.toLowerCase(Locale.ROOT)) {
            case "add" -> MakeupCardOperation.ADD;
            case "remove", "take" -> MakeupCardOperation.REMOVE;
            case "set" -> MakeupCardOperation.SET;
            default -> null;
        };
        if (cardOperation == null) {
            return rejectedOperation("无效补签卡操作: " + operation);
        }
        UUID id = player.getUniqueId();
        String name = player.getName();
        return writeAsync(() -> repository.adjustMakeupCards(id, name, cardOperation, Math.max(0, amount)), cards -> {
            if (player.isOnline() && Bukkit.getPlayer(id) == player) {
                cachedStates.computeIfPresent(id, (ignored, state) -> {
                    var updated = state.copy();
                    updated.setMakeupCards(cards);
                    return updated;
                });
                refreshMenu(player);
            }
            publishRefresh(id);
        }).handle((cards, failure) -> {
            if (failure == null) {
                return new OnlineRewardsOperationResult(true, "已更新 " + name + " " + configuration.signIn().makeup().cardName() + "=" + cards);
            }
            if (!(failure instanceof RejectedExecutionException)) logger.log(Level.WARNING, "保存补签卡失败: " + id, failure);
            return new OnlineRewardsOperationResult(false, "补签卡结果未确认，请先核对当前数量。");
        });
    }

    private List<OnlineRewardDefinition> collectReachedRewards(OnlineRewardsPlayerState state) {
        List<OnlineRewardDefinition> triggeredRewards = new ArrayList<>();
        while (state.rewardStage() < configuration.rewards().size()) {
            OnlineRewardDefinition currentReward = configuration.rewards().get(state.rewardStage());
            if (state.onlineMinutes() < currentReward.minutes()) {
                break;
            }
            triggeredRewards.add(currentReward);
            state.setRewardStage(state.rewardStage() + 1);
        }
        return List.copyOf(triggeredRewards);
    }

    /**
     * 回退 rewardStage 使其与当前 onlineMinutes 一致。
     * <p>
     * 当管理员通过 remove 操作减少在线时长后，已领取的阶段奖励序号需要回退，
     * 否则 snapshot 会基于错误的 rewardStage 计算进度。
     *
     * @param state 玩家状态
     */
    private void rewindRewardStage(OnlineRewardsPlayerState state) {
        List<OnlineRewardDefinition> rewards = configuration.rewards();
        while (state.rewardStage() > 0
            && state.rewardStage() <= rewards.size()
            && state.onlineMinutes() < rewards.get(state.rewardStage() - 1).minutes()) {
            state.setRewardStage(state.rewardStage() - 1);
        }
    }

    private final Map<UUID, Attendance> qualifiedClocks = new java.util.HashMap<>();
    private final Map<UUID, Attendance> startupClocks = new java.util.HashMap<>();

    // When changing from an older module on a live host there may be no stored cursor for an existing connection.
    private void recordStartupClocks() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            var snapshot = xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(id);
            if (snapshot.admitted() && snapshot.connectionId() != null) {
                long minutes = eligibleMinutes(id);
                if (minutes > 0) startupClocks.put(id, new Attendance(snapshot.connectionId(), minutes));
            }
        }
    }

    private static long eligibleMinutes(UUID id) {
        return xuanmo.arcartxsuite.api.auth.AuthAccess.eligibleNanos(id) / 60_000_000_000L;
    }

    private int resolveOnlineMinuteGain(double multiplier, OnlineRewardsPlayerState state, long qualifiedMinutes) {
        double rawGain = multiplier * qualifiedMinutes + state.timeBonusRemainder();
        int minutes = Math.max(1, (int) Math.floor(rawGain));
        state.setTimeBonusRemainder(rawGain - minutes);
        return minutes;
    }

    private double selectedTimeMultiplier(Player player) {
        OnlineRewardsTimeBonusGroup selected = null;
        for (OnlineRewardsTimeBonusGroup group : configuration.timeBonusGroups()) {
            if (player.hasPermission(group.permission())
                && (selected == null || group.priority() > selected.priority() || (group.priority() == selected.priority() && group.multiplier() > selected.multiplier()))) {
                selected = group;
            }
        }
        return selected == null ? 1.0D : Math.max(1.0D, selected.multiplier());
    }

    private OnlineRewardsPermissionBonusReward selectedPermissionBonus(Player player) {
        OnlineRewardsPermissionBonusReward selected = null;
        for (OnlineRewardsPermissionBonusReward reward : configuration.signIn().permissionBonusGroups()) {
            if (player.hasPermission(reward.permission())
                && (selected == null || reward.priority() > selected.priority())) {
                selected = reward;
            }
        }
        return selected;
    }

    private record SignInWrite(OnlineRewardsOperationResult result, SignInCommit commit, OnlineRewardsSignInHistory history) {}

    private SignInWrite commitSignIn(UUID id, String name, LocalDate date, boolean makeup,
                                    OnlineRewardsPermissionBonusReward bonus) throws SQLException {
        String dateText = date.format(DATE_FORMATTER);
        SignInCommit commit = repository.commitSignInWithRewards(id, name, dateText, makeup,
            signedDates -> signInPlan(name, date, signedDates, makeup, bonus));
        if (commit.status() != SignInStatus.APPLIED) {
            String message;
            if (commit.status() == SignInStatus.NO_CARDS) {
                message = renderMakeupText(configuration.signIn().makeup().noCardMessage(), commit.makeupCards(), date);
            } else if (makeup) {
                message = configuration.signIn().makeup().alreadySignedMessage();
            } else {
                var state = repository.loadState(id);
                var repeated = new OnlineRewardsSignInResult(false, true, state.signInStreak(), state.signInTotal(), dateText, date.getDayOfMonth());
                message = renderText(configuration.signIn().signInRepeatMessage(), name, repeated);
            }
            return new SignInWrite(new OnlineRewardsOperationResult(false, message), commit, null);
        }
        Set<String> dates = commit.dates();
        var result = new OnlineRewardsSignInResult(true, false, calculateStreakEndingAt(dates, date), dates.size(), dateText, date.getDayOfMonth());
        String message = makeup ? renderMakeupText(configuration.signIn().makeup().successMessage(), commit.makeupCards(), date)
            : renderText(configuration.signIn().signInSuccessMessage(), name, result);
        return new SignInWrite(new OnlineRewardsOperationResult(true, message), commit, OnlineRewardsSignInHistory.summarize(dates));
    }

    private List<Reward> signInPlan(String playerName, LocalDate date, Set<String> dates, boolean makeup,
                                    OnlineRewardsPermissionBonusReward bonus) {
        var result = new OnlineRewardsSignInResult(true, false, calculateStreakEndingAt(dates, date),
            dates.size(), date.format(DATE_FORMATTER), date.getDayOfMonth());
        List<Reward> rewards = new ArrayList<>();
        if (!makeup) rewards.add(new Reward("SIGNIN_EVENT", DELIVERY_JSON.toJson(result), ""));
        for (String command : resolveSignInCommands(configuration.signIn(), result, bonus)) {
            rewards.add(planCommand(command, () -> renderCommand(command, playerName, result)));
        }
        for (String preset : resolveSignInMailPresetIds(configuration.signIn(), result, bonus)) {
            rewards.add(new Reward("MAIL", preset, "SignIn"));
        }
        for (OnlineRewardsItemReward item : resolveSignInItemRewards(configuration.signIn(), result, bonus)) {
            rewards.add(new Reward("ITEM", DELIVERY_JSON.toJson(item), makeup ? "补签" : "签到"));
        }
        if (!makeup) rewards.add(new Reward("SIGNIN_SIGNAL", DELIVERY_JSON.toJson(result), ""));
        return rewards;
    }

    private static Reward planCommand(String rawCommand, Supplier<String> render) {
        try {
            return new Reward("COMMAND", render.get(), "");
        } catch (IllegalArgumentException invalid) {
            return new Reward("INVALID_COMMAND", rawCommand, invalid.getMessage());
        }
    }

    public void setDurableMail(Supplier<AxsMailService> provider, boolean mysqlRewards) {
        durableItemMail = new OnlineRewardsItemMail(provider, mysqlRewards && !configuration.storage().hasOverride(),
            mysqlRewards, crossServer == null ? null : crossServer.nodeId());
    }

    private void recoverItemDelivery(Player player) {
        if (itemDelivery == null) return;
        AuthSnapshot auth = xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(player.getUniqueId());
        if (auth.admitted()) {
            DeliverySession session = new DeliverySession(player, auth);
            itemDelivery.recover(player, session::isCurrent);
        }
    }

    private void retryWaitingItemDeliveries() {
        if (itemDelivery == null) return;
        for (UUID playerId : itemDelivery.waitingPlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) recoverItemDelivery(player);
            else itemDelivery.forget(playerId);
        }
    }

    private void scheduleOnlineDeliveries() {
        if (!active || Bukkit.getOnlinePlayers().isEmpty()) return;
        deliveryScanAll = true;
        startDeliveryTask();
    }

    private void scheduleOnlineDeliveries(Player player) {
        if (!active || !player.isOnline()) return;
        deliveryRequests.add(player.getUniqueId());
        startDeliveryTask();
    }

    private void startDeliveryTask() {
        if (deliveryTask == null) {
            deliveryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drainOnlineDeliveries, 1L, 1L);
        }
    }

    private void drainOnlineDeliveries() {
        if (!active) return;
        var claim = deliveryWorker.next();
        if (claim != null) {
            Delivery delivery = claim.delivery();
            UUID playerId = delivery.playerUuid();
            DeliverySession session = claim.session();
            if (session == null || !session.isCurrent()) {
                deliveryRequests.add(playerId);
                deliveryWorker.skipped(claim);
            } else {
                dispatchDelivery(session.player(), delivery, claim.attempt(), (outcome, failure) ->
                    deliveryWorker.settle(claim, () -> settleDelivery(delivery, claim.attempt(), outcome, failure)));
            }
        }
        deliveryContinue |= deliveryWorker.finishPage();
        if (!active || deliveryTask == null || deliveryWorker.pagePending()) return;
        if (deliveryContinue || deliveryScanAll || !deliveryRequests.isEmpty()) {
            if (!deliveryWorker.canFetch()) return;
            if (deliveryScanAll || !deliveryRequests.isEmpty()) refreshDeliveryAudience();
            if (deliveryWorker.fetch(deliveryAudience, deliveryAudienceId)) {
                deliveryContinue = false;
                return;
            }
        }
        deliveryContinue = false;
        deliveryAudience = Map.of();
        deliveryTask.cancel();
        deliveryTask = null;
    }

    private void refreshDeliveryAudience() {
        Map<UUID, DeliverySession> online = new LinkedHashMap<>();
        if (deliveryScanAll) {
            for (Player player : Bukkit.getOnlinePlayers()) addDeliverySession(online, player);
        } else {
            if (deliveryContinue) online.putAll(deliveryAudience);
            for (UUID id : deliveryRequests) {
                online.remove(id);
                addDeliverySession(online, Bukkit.getPlayer(id));
            }
        }
        deliveryRequests.clear();
        deliveryScanAll = false;
        deliveryAudience = Map.copyOf(online);
        deliveryAudienceId++;
    }

    private static void addDeliverySession(Map<UUID, DeliverySession> online, Player player) {
        if (player == null || !player.isOnline()) return;
        UUID id = player.getUniqueId();
        AuthSnapshot snapshot = xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(id);
        if (snapshot.admitted()) online.put(id, new DeliverySession(player, snapshot));
    }

    private record DeliverySession(Player player, AuthSnapshot snapshot) {
        boolean isCurrent() {
            UUID playerId = snapshot.playerId();
            if (!player.isOnline() || Bukkit.getPlayer(playerId) != player) return false;
            return authCurrent();
        }

        boolean authCurrent() {
            return matches(xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(snapshot.playerId()));
        }

        boolean matches(AuthSnapshot current) {
            return current.admitted() && current.generation() == snapshot.generation()
                && java.util.Objects.equals(current.connectionId(), snapshot.connectionId());
        }
    }

    private enum DeliveryOutcome { SUCCEEDED, FAILED, ITEM_OWNED, UNSTARTED }
    private record DeliveryResult(DeliveryOutcome outcome, Throwable failure) {}

    private BiConsumer<DeliveryOutcome, Throwable> trackAsyncDelivery(BiConsumer<DeliveryOutcome, Throwable> finished) {
        var result = new CompletableFuture<DeliveryResult>();
        var receipt = result.thenAccept(value -> finished.accept(value.outcome(), value.failure()));
        synchronized (pendingDispatches) {
            if (stopping) return null;
            pendingDispatches.add(receipt);
        }
        receipt.whenComplete((ignored, failure) -> {
            synchronized (pendingDispatches) { pendingDispatches.remove(receipt); }
            if (failure != null) logger.log(Level.WARNING, "在线奖励异步结果未能确认，保留凭证核对", failure);
        });
        return (outcome, failure) -> result.complete(new DeliveryResult(outcome, failure));
    }

    private void dispatchDelivery(Player player, Delivery delivery, UUID attempt, BiConsumer<DeliveryOutcome, Throwable> finished) {
        Reward reward = delivery.reward();
        try {
            if ("ITEM".equals(reward.kind())) {
                var tracked = trackAsyncDelivery(finished);
                if (tracked != null) {
                    try { stageItemDelivery(player, delivery, attempt, reward, tracked); }
                    catch (RuntimeException failure) { tracked.accept(DeliveryOutcome.FAILED, failure); }
                }
                return;
            }
            if ("MAIL".equals(reward.kind())) {
                dispatchPresetMail(delivery, finished);
                return;
            }
            if ("TITLE".equals(reward.kind())) {
                TitleGrantable title = titleProvider == null ? null : titleProvider.get();
                if (title != null) {
                    var tracked = trackAsyncDelivery(finished);
                    if (tracked != null) {
                        try {
                            title.grantPermanent(player.getUniqueId(), reward.value(), "OnlineRewards:ServerGoal")
                                .whenComplete((ok, failure) -> tracked.accept(
                                    failure == null && Boolean.TRUE.equals(ok) ? DeliveryOutcome.SUCCEEDED : DeliveryOutcome.FAILED, failure));
                        } catch (RuntimeException failure) { tracked.accept(DeliveryOutcome.FAILED, failure); }
                    }
                    return;
                }
            }
            boolean success = switch (reward.kind()) {
                case "COMMAND" -> {
                    String command = reward.value().startsWith("/") ? reward.value().substring(1) : reward.value();
                    yield Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        xuanmo.arcartxsuite.api.command.CommandSanitizer.validateExpandedCommand(command));
                }
                case "CHAT_CARD" -> {
                    ChatCardSendable chatCard = chatCardProvider == null ? null : chatCardProvider.get();
                    yield chatCard != null && chatCard.sendChatCard(player, reward.value(), Map.of("goal", reward.context()));
                }
                case "SUBTITLE" -> {
                    SubtitlePlayable subtitle = subtitleProvider == null ? null : subtitleProvider.get();
                    yield subtitle != null && subtitle.playGroup(player, reward.value());
                }
                case "SIGNIN_EVENT" -> {
                    var result = DELIVERY_JSON.fromJson(reward.value(), OnlineRewardsSignInResult.class);
                    publishEventBus("axs.onlinerewards.signin_completed", player, Map.of(
                        "event_id", result.signInDate(), "business_date", result.signInDate(),
                        "streak", String.valueOf(result.streak()), "total", String.valueOf(result.total())));
                    yield true;
                }
                case "SIGNIN_SIGNAL" -> {
                    dispatchSignInSignal(player, DELIVERY_JSON.fromJson(reward.value(), OnlineRewardsSignInResult.class));
                    yield true;
                }
                case "MESSAGE" -> {
                    player.sendMessage(reward.value());
                    yield true;
                }
                case "QQ" -> {
                    QQBotBroadcastable qqBot = qqBotProvider == null ? null : qqBotProvider.get();
                    if (qqBot != null) qqBot.sendToAllGroups(reward.value());
                    yield true;
                }
                case "GOAL_EVENT" -> {
                    var event = DELIVERY_JSON.fromJson(reward.value(), GoalEvent.class);
                    publishEventBus("axs.onlinerewards.server_goal_reached", player, Map.of(
                        "goal_id", event.id(), "goal_name", event.name(), "required", String.valueOf(event.required()),
                        "signed_count", String.valueOf(event.signedCount())));
                    yield true;
                }
                default -> false;
            };
            finished.accept(success ? DeliveryOutcome.SUCCEEDED : DeliveryOutcome.FAILED, null);
        } catch (RuntimeException exception) {
            finished.accept(DeliveryOutcome.FAILED, exception);
        }
    }

    private void dispatchPresetMail(Delivery delivery, BiConsumer<DeliveryOutcome, Throwable> finished) {
        if (durableItemMail == null) {
            finished.accept(DeliveryOutcome.FAILED, null);
            return;
        }
        var tracked = trackAsyncDelivery(finished);
        if (tracked == null) {
            finished.accept(DeliveryOutcome.UNSTARTED, null);
            return;
        }
        try {
            durableItemMail.deliverPreset(delivery).whenComplete((sent, failure) -> tracked.accept(
                failure == null && Boolean.TRUE.equals(sent) ? DeliveryOutcome.SUCCEEDED : DeliveryOutcome.FAILED, failure));
        } catch (RuntimeException | LinkageError failure) {
            tracked.accept(DeliveryOutcome.FAILED, failure);
        }
    }

    private void stageItemDelivery(Player player, Delivery delivery, UUID attempt, Reward reward,
                                   BiConsumer<DeliveryOutcome, Throwable> finished) {
        if (itemDelivery == null || itemSourceRegistry == null) {
            finished.accept(DeliveryOutcome.FAILED, new IllegalStateException("在线物品奖励服务未就绪"));
            return;
        }
        var definition = DELIVERY_JSON.fromJson(reward.value(), OnlineRewardsItemReward.class);
        ItemStack item = itemSourceRegistry.generateItem(definition.source(), definition.id(), definition.amount(), definition.nbt(), player);
        if (InventoryItems.empty(item)) {
            finished.accept(DeliveryOutcome.FAILED, new IllegalStateException("无法生成在线奖励物品 " + definition.id()));
            return;
        }
        AuthSnapshot auth = xuanmo.arcartxsuite.api.auth.AuthAccess.snapshot(player.getUniqueId());
        DeliverySession session = new DeliverySession(player, auth);
        itemDelivery.stage(player, delivery, attempt, item, session::isCurrent, stage -> finished.accept(switch (stage) {
            case OWNED -> DeliveryOutcome.ITEM_OWNED;
            case UNSTARTED -> DeliveryOutcome.UNSTARTED;
            case INVALID -> DeliveryOutcome.FAILED;
        }, null));
    }

    private void settleDelivery(Delivery delivery, UUID attempt, DeliveryOutcome outcome, Throwable failure) {
        switch (outcome) {
            case SUCCEEDED, FAILED -> finishDelivery(delivery, attempt, outcome == DeliveryOutcome.SUCCEEDED, failure);
            case UNSTARTED -> {
                try {
                    repository.releaseUnstartedDelivery(delivery, attempt);
                } catch (SQLException exception) {
                    logger.log(Level.WARNING, "退回未开始发放的在线奖励失败: " + delivery.playerUuid(), exception);
                }
            }
            case ITEM_OWNED -> { }
        }
    }

    private void finishDelivery(Delivery delivery, UUID attempt, boolean success, Throwable failure) {
        String identity = delivery.playerUuid() + " " + delivery.batchId() + "/" + (delivery.step() + 1) + " " + attempt;
        try {
            repository.finishDelivery(delivery, attempt, success);
            if (!success) logger.log(Level.WARNING, "在线奖励结果待核对: " + identity, failure);
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "写入在线奖励回执失败，保留发放中的记录: " + identity, exception);
        }
    }

    public List<DeliveryReview> deliveriesForReview(UUID playerUuid) throws SQLException {
        return repository.deliveriesForReview(playerUuid, 50);
    }

    public boolean resolveDelivery(UUID playerUuid, String batch, int step, UUID attempt, boolean confirmed,
                                   String actor) throws SQLException {
        boolean retryPreset = durableItemMail != null && durableItemMail.canRetryPreset();
        boolean resolved = repository.resolveDelivery(playerUuid, batch, step, attempt, confirmed, retryPreset);
        if (resolved) {
            logger.info("管理员核对在线奖励: actor=" + actor + " player=" + playerUuid + " batch=" + batch
                + " step=" + (step + 1) + " attempt=" + attempt + " result=" + (confirmed ? "confirmed" : "retry"));
            if (!confirmed) runOnMain(() -> {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) scheduleOnlineDeliveries(player);
            });
        }
        return resolved;
    }

    private record GoalEvent(String id, String name, int required, int signedCount) {}

    private OnlineRewardsCalendarView buildCalendarView(Player player, OnlineRewardsPlayerState state,
                                                         OnlineRewardsPeriodContext context, Set<String> history) {
        int offset = Math.max(-1, Math.min(1, calendarMonthOffsets.getOrDefault(player.getUniqueId(), 0)));
        YearMonth month = YearMonth.from(context.date()).plusMonths(offset);
        LocalDate selectedDate = selectedCalendarDates.getOrDefault(
            player.getUniqueId(),
            offset == 0 ? context.date() : month.atDay(1)
        );
        if (!YearMonth.from(selectedDate).equals(month)) {
            selectedDate = offset == 0 ? context.date() : month.atDay(1);
        }
        selectedCalendarDates.put(player.getUniqueId(), selectedDate);
        Set<String> signedDates = new HashSet<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            String key = month.atDay(day).toString();
            if (history.contains(key)) signedDates.add(key);
        }
        boolean canMakeup = canMakeup(state, signedDates, selectedDate, context.date());
        return new OnlineRewardsCalendarView(
            offset,
            month,
            context.date(),
            selectedDate,
            signedDates,
            buildRewardPreviewRows(player, selectedDate, history),
            canMakeup
        );
    }

    private boolean canMakeup(OnlineRewardsPlayerState state, Set<String> signedDates, LocalDate selectedDate, LocalDate today) {
        return configuration.signIn().makeup().enabled()
            && state.makeupCards() > 0
            && YearMonth.from(selectedDate).equals(YearMonth.from(today))
            && selectedDate.isBefore(today)
            && !signedDates.contains(selectedDate.format(DATE_FORMATTER));
    }

    private List<OnlineRewardsRewardPreviewRow> buildRewardPreviewRows(Player player, LocalDate selectedDate, Set<String> dates) {
        String dateText = selectedDate.format(DATE_FORMATTER);
        int streak = 1 + calculateStreakEndingAt(dates, selectedDate.minusDays(1));
        int total = dates.size() + (dates.contains(dateText) ? 0 : 1);
        OnlineRewardsSignInResult preview = new OnlineRewardsSignInResult(true, false, streak, total, dateText, selectedDate.getDayOfMonth());
        return resolveSignInPreviewRows(configuration.signIn(), preview, selectedPermissionBonus(player));
    }

    private int calculateStreakEndingAt(Set<String> dates, LocalDate endingDate) {
        return OnlineRewardsSignInHistory.streakEndingAt(dates, endingDate);
    }

    private void refreshLeaderboardSnapshots() {
        if (!leaderboardActive) return;
        leaderboardDirty.set(true);
        if (!leaderboardLoading.compareAndSet(false, true)) return;
        leaderboardDirty.set(false);
        try {
            leaderboardWorker.execute(this::loadLeaderboardSnapshots);
        } catch (RuntimeException failure) {
            leaderboardLoading.set(false);
            if (leaderboardActive) throw failure;
        }
    }

    private void loadLeaderboardSnapshots() {
        Map<OnlineRewardsLeaderboardScope, List<OnlineRewardsLeaderboardEntry>> snapshots =
            new EnumMap<>(OnlineRewardsLeaderboardScope.class);
        Map<OnlineRewardsLeaderboardScope, String> periods = new EnumMap<>(OnlineRewardsLeaderboardScope.class);
        for (OnlineRewardsLeaderboardScope scope : OnlineRewardsLeaderboardScope.values()) {
            if (!leaderboardActive) return;
            String period = currentPeriodKey(scope);
            periods.put(scope, period);
            try {
                snapshots.put(scope, List.copyOf(repository.loadLeaderboard(scope, period, 0, LEADERBOARD_CACHE_SIZE)));
            } catch (SQLException | RuntimeException exception) {
                this.logger.log(Level.WARNING, "刷新在线奖励排行榜缓存失败: " + scope.key(), exception);
                snapshots.put(scope, List.of());
            }
        }
        if (!leaderboardActive) return;
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!leaderboardActive) return;
                snapshots.forEach((scope, rows) -> {
                    if (periods.get(scope).equals(currentPeriodKey(scope))) leaderboardCache.put(scope, new PeriodLeaderboard(periods.get(scope), rows));
                    else leaderboardDirty.set(true);
                });
                leaderboardLoading.set(false);
                if (leaderboardDirty.get()) refreshLeaderboardSnapshots();
            });
        } catch (RuntimeException failure) {
            leaderboardLoading.set(false);
            if (leaderboardActive) throw failure;
        }
    }

    private volatile OnlineRewardsPeriodContext currentPeriod;

    private OnlineRewardsPeriodContext currentPeriodContext() {
        LocalDate today = LocalDate.now(clock);
        var snapshot = currentPeriod;
        if (snapshot != null && snapshot.date().equals(today)) return snapshot;
        snapshot = periodContext(today);
        currentPeriod = snapshot;
        return snapshot;
    }

    private OnlineRewardsPeriodContext periodContext(LocalDate currentDate) {
        return new OnlineRewardsPeriodContext(
            currentDate,
            currentDate.format(DATE_FORMATTER),
            currentWeekKey(currentDate),
            currentDate.format(MONTH_FORMATTER)
        );
    }

    private String currentPeriodKey(OnlineRewardsLeaderboardScope scope) {
        OnlineRewardsPeriodContext context = currentPeriodContext();
        return switch (scope) {
            case DAILY -> context.rewardDate();
            case WEEKLY -> context.weekKey();
            case MONTHLY -> context.monthKey();
            case TOTAL -> "";
        };
    }

    private String currentWeekKey(LocalDate date) {
        int weekBasedYear = date.get(WEEK_FIELDS.weekBasedYear());
        int week = date.get(WEEK_FIELDS.weekOfWeekBasedYear());
        return "%04d-W%02d".formatted(weekBasedYear, week);
    }

    static List<String> resolveSignInCommands(
        OnlineRewardsSignInConfiguration configuration,
        OnlineRewardsSignInResult result,
        OnlineRewardsPermissionBonusReward permissionBonus
    ) {
        List<String> commands = new ArrayList<>(configuration.baseCommands());
        for (OnlineRewardsDayOfMonthReward reward : configuration.dayOfMonthRewards()) {
            if (reward.day() == result.dayOfMonth()) {
                commands.addAll(reward.commands());
            }
        }
        for (OnlineRewardsHolidayReward reward : configuration.holidayRewards()) {
            LocalDate date = parseIsoDate(result.signInDate());
            if (date != null && reward.month() == date.getMonthValue() && reward.day() == date.getDayOfMonth()) {
                commands.addAll(reward.commands());
            }
        }
        for (OnlineRewardsMilestoneReward reward : configuration.streakRewards()) {
            if (reward.days() == result.streak()) {
                commands.addAll(reward.commands());
            }
        }
        for (OnlineRewardsMilestoneReward reward : configuration.totalRewards()) {
            if (reward.days() == result.total()) {
                commands.addAll(reward.commands());
            }
        }
        if (permissionBonus != null) {
            commands.addAll(permissionBonus.commands());
        }
        return List.copyOf(commands);
    }

    static List<String> resolveSignInMailPresetIds(
        OnlineRewardsSignInConfiguration configuration,
        OnlineRewardsSignInResult result,
        OnlineRewardsPermissionBonusReward permissionBonus
    ) {
        List<String> mailPresetIds = new ArrayList<>(configuration.baseMailPresetIds());
        for (OnlineRewardsDayOfMonthReward reward : configuration.dayOfMonthRewards()) {
            if (reward.day() == result.dayOfMonth()) {
                mailPresetIds.addAll(reward.mailPresetIds());
            }
        }
        for (OnlineRewardsHolidayReward reward : configuration.holidayRewards()) {
            LocalDate date = parseIsoDate(result.signInDate());
            if (date != null && reward.month() == date.getMonthValue() && reward.day() == date.getDayOfMonth()) {
                mailPresetIds.addAll(reward.mailPresetIds());
            }
        }
        for (OnlineRewardsMilestoneReward reward : configuration.streakRewards()) {
            if (reward.days() == result.streak()) {
                mailPresetIds.addAll(reward.mailPresetIds());
            }
        }
        for (OnlineRewardsMilestoneReward reward : configuration.totalRewards()) {
            if (reward.days() == result.total()) {
                mailPresetIds.addAll(reward.mailPresetIds());
            }
        }
        if (permissionBonus != null) {
            mailPresetIds.addAll(permissionBonus.mailPresetIds());
        }
        return List.copyOf(mailPresetIds);
    }

    static List<xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsItemReward> resolveSignInItemRewards(
        OnlineRewardsSignInConfiguration configuration,
        OnlineRewardsSignInResult result,
        OnlineRewardsPermissionBonusReward permissionBonus
    ) {
        List<xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsItemReward> itemRewards =
            new ArrayList<>(configuration.baseItemRewards());
        for (OnlineRewardsDayOfMonthReward reward : configuration.dayOfMonthRewards()) {
            if (reward.day() == result.dayOfMonth()) {
                itemRewards.addAll(reward.itemRewards());
            }
        }
        for (OnlineRewardsHolidayReward reward : configuration.holidayRewards()) {
            LocalDate date = parseIsoDate(result.signInDate());
            if (date != null && reward.month() == date.getMonthValue() && reward.day() == date.getDayOfMonth()) {
                itemRewards.addAll(reward.itemRewards());
            }
        }
        for (OnlineRewardsMilestoneReward reward : configuration.streakRewards()) {
            if (reward.days() == result.streak()) {
                itemRewards.addAll(reward.itemRewards());
            }
        }
        for (OnlineRewardsMilestoneReward reward : configuration.totalRewards()) {
            if (reward.days() == result.total()) {
                itemRewards.addAll(reward.itemRewards());
            }
        }
        if (permissionBonus != null) {
            itemRewards.addAll(permissionBonus.itemRewards());
        }
        return List.copyOf(itemRewards);
    }

    static List<OnlineRewardsRewardPreviewRow> resolveSignInPreviewRows(
        OnlineRewardsSignInConfiguration configuration,
        OnlineRewardsSignInResult result,
        OnlineRewardsPermissionBonusReward permissionBonus
    ) {
        List<OnlineRewardsRewardPreviewRow> rows = new ArrayList<>();
        rows.add(new OnlineRewardsRewardPreviewRow("base", "每日签到", "每次成功签到", rewardText(configuration.baseRewardText(), configuration.baseCommands().size(), configuration.baseMailPresetIds().size())));
        for (OnlineRewardsDayOfMonthReward reward : configuration.dayOfMonthRewards()) {
            if (reward.day() == result.dayOfMonth()) {
                rows.add(new OnlineRewardsRewardPreviewRow("month-" + reward.day(), "每月 " + reward.day() + " 日", "每月 " + reward.day() + " 日签到", rewardText(reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size())));
            }
        }
        LocalDate date = parseIsoDate(result.signInDate());
        for (OnlineRewardsHolidayReward reward : configuration.holidayRewards()) {
            if (date != null && reward.month() == date.getMonthValue() && reward.day() == date.getDayOfMonth()) {
                rows.add(new OnlineRewardsRewardPreviewRow("holiday-" + reward.month() + "-" + reward.day(), reward.name(), reward.month() + "月" + reward.day() + "日签到", rewardText(reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size())));
            }
        }
        for (OnlineRewardsMilestoneReward reward : configuration.streakRewards()) {
            if (reward.days() == result.streak()) {
                rows.add(new OnlineRewardsRewardPreviewRow("streak-" + reward.days(), "连续 " + reward.days() + " 天", "连续签到恰好 " + reward.days() + " 天", rewardText(reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size())));
            }
        }
        for (OnlineRewardsMilestoneReward reward : configuration.totalRewards()) {
            if (reward.days() == result.total()) {
                rows.add(new OnlineRewardsRewardPreviewRow("total-" + reward.days(), "累计 " + reward.days() + " 天", "累计签到恰好 " + reward.days() + " 天", rewardText(reward.rewardText(), reward.commands().size(), reward.mailPresetIds().size())));
            }
        }
        if (permissionBonus != null) {
            rows.add(new OnlineRewardsRewardPreviewRow("permission-bonus", "权限额外奖励", "拥有权限 " + permissionBonus.permission(), rewardText(permissionBonus.rewardText(), permissionBonus.commands().size(), permissionBonus.mailPresetIds().size())));
        }
        return List.copyOf(rows);
    }

    private String renderCommand(String template, String playerName, OnlineRewardsSignInResult result) {
        // 验证玩家名防止命令注入（离线服可注册含特殊字符的玩家名）
        String safeName = xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(playerName);
        return renderText(template, safeName, result);
    }

    private String renderSignInText(String template, Player player, OnlineRewardsSignInResult result) {
        return renderText(template, player.getName(), result);
    }

    private String renderText(String template, String playerName, OnlineRewardsSignInResult result) {
        return (template == null ? "" : template)
            .replace("{player}", playerName)
            .replace("{streak}", Integer.toString(result.streak()))
            .replace("{total}", Integer.toString(result.total()))
            .replace("{date}", result.signInDate())
            .replace("{day}", Integer.toString(result.dayOfMonth()));
    }

    private String renderMakeupText(String template, int cards, LocalDate date) {
        return (template == null ? "" : template)
            .replace("{card}", configuration.signIn().makeup().cardName())
            .replace("{cards}", Integer.toString(cards))
            .replace("{date}", date.format(DATE_FORMATTER));
    }

    private LocalDate parseDate(String value) {
        return parseIsoDate(value);
    }

    private static LocalDate parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String rewardText(String configuredText, int commandCount, int mailPresetCount) {
        if (configuredText != null && !configuredText.isBlank()) {
            return configuredText;
        }
        List<String> parts = new ArrayList<>();
        if (commandCount > 0) {
            parts.add("命令 " + commandCount);
        }
        if (mailPresetCount > 0) {
            parts.add("邮件 " + mailPresetCount);
        }
        return parts.isEmpty() ? "无额外奖励" : String.join(" / ", parts);
    }

    private void dispatchSignInSignal(Player player, OnlineRewardsSignInResult result) {
        if (player == null || !player.isOnline() || result == null || !result.success()) {
            return;
        }
        SignalDispatchable signal = signalProvider != null ? signalProvider.get() : null;
        if (signal == null) {
            return;
        }
        Map<String, String> variables = new java.util.LinkedHashMap<>();
        variables.put("streak", String.valueOf(result.streak()));
        variables.put("total", String.valueOf(result.total()));
        variables.put("date", result.signInDate());
        variables.put("day_of_month", String.valueOf(result.dayOfMonth()));
        signal.dispatchSignal("signin_success", player, variables);
    }

    private void checkServerSignInGoals(Player triggerPlayer) {
        var goalConfig = configuration.serverSignInGoal();
        if (!goalConfig.enabled() || goalConfig.targets().isEmpty() || !goalCheckPending.compareAndSet(false, true)) return;
        String today = currentPeriodContext().rewardDate();
        UUID triggerId = triggerPlayer.getUniqueId();
        String triggerName = triggerPlayer.getName();
        boolean shared = sharesPresence();
        var names = new LinkedHashMap<UUID, String>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(online.getUniqueId())) {
                names.put(online.getUniqueId(), online.getName());
            }
        }
        var local = Map.copyOf(names);
        writeAsync(() -> recordServerGoals(goalConfig, today, triggerId, triggerName, shared, local), committed -> {
            if (committed) {
                menuReads.invalidateGoals();
                scheduleOnlineDeliveries();
            }
        }).whenComplete((ignored, failure) -> {
            goalCheckPending.set(false);
            if (failure != null && !(failure instanceof RejectedExecutionException)) {
                logger.log(Level.WARNING, "保存全服签到目标与待发奖励失败: " + today, failure);
            }
        });
    }

    private boolean recordServerGoals(OnlineRewardsServerSignInGoalConfiguration goalConfig, String today,
                                      UUID triggerId, String triggerName, boolean shared, Map<UUID, String> local) {
        boolean committed = false;
        try {
            int signedCount = repository.countSignInRecords(today);
            Map<UUID, String> onlineNames = null;
            for (var target : goalConfig.targets()) {
                if (signedCount < target.required() || repository.isServerGoalTriggered(today, target.id())) continue;
                if (onlineNames == null) {
                    onlineNames = new LinkedHashMap<>();
                    if (shared) onlineNames.putAll(repository.loadPresence(clock.millis()));
                    onlineNames.putAll(local);
                }
                String broadcast = target.broadcastMessage();
                if (broadcast == null || broadcast.isBlank()) {
                    broadcast = "§e[全服签到] §f" + target.name() + " §7已达成！今日签到人数：§f" + signedCount + "§7。";
                }
                broadcast = broadcast.replace("{player}", triggerName);
                var recipients = new java.util.LinkedHashMap<UUID, List<Reward>>();
                for (var entry : onlineNames.entrySet()) {
                    List<Reward> rewards = goalPlan(entry.getValue(), target);
                    if (goalConfig.broadcast()) rewards.add(0, new Reward("MESSAGE", broadcast, ""));
                    recipients.put(entry.getKey(), rewards);
                }
                List<Reward> announcer = recipients.computeIfAbsent(triggerId, ignored -> new ArrayList<>());
                announcer.add(new Reward("QQ", broadcast, ""));
                announcer.add(new Reward("GOAL_EVENT", DELIVERY_JSON.toJson(
                    new GoalEvent(target.id(), target.name(), target.required(), signedCount)), ""));
                committed |= repository.recordServerGoalWithRewards(today, target.id(), recipients);
            }
        } catch (SQLException | RuntimeException exception) {
            logger.log(Level.WARNING, "保存全服签到目标与待发奖励失败: " + today, exception);
        }
        return committed;
    }

    private List<Reward> goalPlan(String playerName, OnlineRewardsServerSignInGoalTarget target) {
        List<Reward> rewards = new ArrayList<>();
        for (String command : target.commands()) {
            rewards.add(planCommand(command, () -> command.replace("{player}",
                xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(playerName))));
        }
        for (String preset : target.mailPresetIds()) rewards.add(new Reward("MAIL", preset, "ServerGoal:" + target.id()));
        for (OnlineRewardsItemReward item : target.itemRewards()) {
            rewards.add(new Reward("ITEM", DELIVERY_JSON.toJson(item), "全服签到目标 " + target.name()));
        }
        for (String card : target.chatCardIds()) rewards.add(new Reward("CHAT_CARD", card, target.name()));
        for (String group : target.subtitleGroupIds()) rewards.add(new Reward("SUBTITLE", group, ""));
        for (String title : target.titleIds()) rewards.add(new Reward("TITLE", title, ""));
        return rewards;
    }

    private boolean sharesPresence() {
        return configuration.crossServer().enabled() && crossServer != null
            && crossServer.nodeId() != null && !crossServer.nodeId().isBlank();
    }

    private void refreshPresence() {
        if (!sharesPresence()) return;
        Map<UUID, String> online = new java.util.LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) {
                online.put(player.getUniqueId(), player.getName());
            }
        }
        savePresence(online);
    }

    private void savePresence(Map<UUID, String> online) {
        if (!active || !sharesPresence() || online.isEmpty()) return;
        String node = crossServer.nodeId();
        long expires = clock.millis() + PRESENCE_TTL_MILLIS;
        var snapshot = Map.copyOf(online);
        writeAsync(() -> {
            if (clock.millis() < expires) repository.savePresence(node, presenceServiceId, snapshot, expires);
            return null;
        }, null).whenComplete((ignored, failure) -> {
            if (failure != null) logger.log(Level.WARNING, "写入在线奖励跨服在线名单失败", failure);
        });
    }

    private void publishEventBus(String topic, Player player, Map<String, String> payload) {
        EventBusCapability bus = eventBusProvider != null ? eventBusProvider.get() : null;
        if (bus != null) {
            bus.publish(topic, player, payload);
        }
    }
}
