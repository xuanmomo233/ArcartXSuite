package xuanmo.arcartxsuite.onlinerewards.service;
import xuanmo.arcartxsuite.api.util.AxsScheduler;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;
import xuanmo.arcartxsuite.api.capability.ChatCardSendable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.QQBotBroadcastable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.TitleGrantable;
import xuanmo.arcartxsuite.api.bridge.ClientBridgeAPI;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsDayOfMonthReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsHolidayReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsMilestoneReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardDefinition;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsModuleConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsOfflineSavingsConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPermissionBonusReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsPeriodicReward;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsServerSignInGoalConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsServerSignInGoalTarget;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsSignInConfiguration;
import xuanmo.arcartxsuite.onlinerewards.config.OnlineRewardsTimeBonusGroup;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardEntry;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsLeaderboardScope;
import xuanmo.arcartxsuite.onlinerewards.model.OnlineRewardsPlayerState;
import xuanmo.arcartxsuite.onlinerewards.storage.OnlineRewardsRepository;
import xuanmo.arcartxsuite.api.crossserver.CrossServerAPI;
import xuanmo.arcartxsuite.api.crossserver.CrossServerChannel;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import java.util.logging.Logger;

/**
 * 在线奖励核心服务。
 * <p>
 * 属于 {@code onlinerewards} 模块，负责玩家状态缓存与持久化、每分钟在线时长累计、
 * 阶段/周期奖励发放、签到与补签、排行榜缓存、离线储蓄、跨服刷新通知、客户端菜单数据同步
 * 与客户端包处理等全部业务逻辑。同时实现 {@link Listener} 处理玩家退出事件。
 */
public class OnlineRewardsService implements Listener {

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
    private final Supplier<MailDispatchable> mailProvider;
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
    private final Map<UUID, OnlineRewardsPlayerState> dirtyStates = new ConcurrentHashMap<>();
    // 加载失败的玩家集合：这些玩家的缓存数据不可信，flush 必须跳过，避免空状态覆盖 DB 真实数据
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> calendarMonthOffsets = new ConcurrentHashMap<>();
    private final Map<UUID, LocalDate> selectedCalendarDates = new ConcurrentHashMap<>();
    private final Map<UUID, String> actionTokens = new ConcurrentHashMap<>();
    private final Set<UUID> actionTokensInFlight = ConcurrentHashMap.newKeySet();
    private final EnumMap<OnlineRewardsLeaderboardScope, List<OnlineRewardsLeaderboardEntry>> leaderboardCache =
        new EnumMap<>(OnlineRewardsLeaderboardScope.class);

    private BukkitTask minuteTask;
    private CrossServerChannel crossServerChannel;
    private java.util.List<String> runtimeMenuUiIds = java.util.List.of();
    private java.util.List<String> registeredMenuUiIds = java.util.List.of();

    public OnlineRewardsService(
        JavaPlugin plugin,
        Logger logger,
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsRepository repository,
        ClientBridgeAPI clientBridge,
        PacketBridgeAPI packetBridge,
        PacketGuardAPI packetGuard,
        Supplier<MailDispatchable> mailProvider,
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
            packetGuard, mailProvider, signalProvider, chatCardProvider, titleProvider,
            subtitleProvider, qqBotProvider, eventBusProvider, messages, menuUiFileExporter,
            Clock.systemDefaultZone(), crossServer);
    }

    OnlineRewardsService(
        JavaPlugin plugin,
        Logger logger,
        OnlineRewardsModuleConfiguration configuration,
        OnlineRewardsRepository repository,
        ClientBridgeAPI clientBridge,
        PacketBridgeAPI packetBridge,
        PacketGuardAPI packetGuard,
        Supplier<MailDispatchable> mailProvider,
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
        this.clientBridge = clientBridge;
        this.packetBridge = packetBridge;
        this.packetGuard = packetGuard;
        this.mailProvider = mailProvider;
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
        for (OnlineRewardsLeaderboardScope scope : OnlineRewardsLeaderboardScope.values()) {
            leaderboardCache.put(scope, List.of());
        }
    }

    /**
     * 启动服务：初始化存储、绑定 UI、注册事件、刷新排行榜、调度每分钟 tick 与跨服通道。
     *
     * @throws SQLException 存储初始化失败时抛出
     * @throws IOException  UI 资源导出失败时抛出
     */
    public void start() throws SQLException, IOException {
        repository.initialize();
        bindMenuUi();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refreshLeaderboardSnapshots();
        scheduleSyncForOnlinePlayers();
        minuteTask = AxsScheduler.runTaskTimer(plugin, this::tickOnlinePlayers, 20L, 1200L);
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
        if (minuteTask != null) {
            minuteTask.cancel();
            minuteTask = null;
        }
        if (crossServerChannel != null) {
            crossServerChannel.close();
            crossServerChannel = null;
        }
        flushAll();
        cachedStates.clear();
        dirtyStates.clear();
        failedLoads.clear();
        calendarMonthOffsets.clear();
        selectedCalendarDates.clear();
        leaderboardCache.clear();
        HandlerList.unregisterAll(this);
        unregisterMenuUi();
        repository.shutdown();
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
        if (player == null || !player.isOnline()) {
            return;
        }
        AxsScheduler.runTaskLater(plugin, () -> syncPlayer(player), configuration.clientSyncDelayTicks());
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
        if (packetBridge == null || runtimeMenuUiIds.isEmpty()) {
            return new OnlineRewardsOperationResult(false, "在线奖励 UI 当前不可用。");
        }

        attachPlayerState(player);
        packetBridge.openUiAll(player, runtimeMenuUiIds);
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

        String action = data == null || data.isEmpty() ? "refresh" : data.get(0).trim().toLowerCase();
        if (packetGuard != null && !packetGuard.allow(player, "onlinerewards", action, configuration.debug())) {
            return true;
        }

        switch (action) {
            case "open" -> openMenu(player);
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
                if (data != null && data.size() >= 2) {
                    LocalDate date = parseDate(data.get(1));
                    if (date != null) {
                        YearMonth currentMonth = YearMonth.from(currentPeriodContext().date());
                        int offset = Math.max(-1, Math.min(1, (date.getYear() - currentMonth.getYear()) * 12 + date.getMonthValue() - currentMonth.getMonthValue()));
                        calendarMonthOffsets.put(player.getUniqueId(), offset);
                        selectedCalendarDates.put(player.getUniqueId(), date);
                    }
                }
                refreshMenu(player);
            }
            case "makeup" -> {
                if (!acquireActionToken(player, data, 2)) return true;
                OnlineRewardsOperationResult result = data != null && data.size() >= 2
                    ? makeupSignIn(player, data.get(1))
                    : new OnlineRewardsOperationResult(false, configuration.signIn().makeup().invalidDateMessage());
                actionTokensInFlight.remove(player.getUniqueId());
                if (result.success()) actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
                player.sendMessage((result.success() ? "§a" : "§c") + result.message());
                refreshMenu(player);
            }
            case "signin" -> {
                if (!acquireActionToken(player, data, 1)) return true;
                OnlineRewardsOperationResult result = signIn(player);
                actionTokensInFlight.remove(player.getUniqueId());
                if (result.success()) actionTokens.put(player.getUniqueId(), UUID.randomUUID().toString());
                player.sendMessage((result.success() ? "§a" : "§c") + result.message());
                refreshMenu(player);
            }
            case "refresh" -> refreshMenu(player);
            default -> refreshMenu(player);
        }
        return true;
    }

    // NOTE: 此方法可能由 PlaceholderAPI 从异步线程调用。
    // ConcurrentHashMap 保证引用可见性，但 normalizeForPeriod 对 state 字段的修改并非原子操作。
    // 实际影响极低（仅日/周/月切换瞬间可能读到部分更新状态），如需严格线程安全需加同步。
    /**
     * 加载玩家快照，可能由 PlaceholderAPI 异步调用。
     *
     * @param playerUuid 玩家唯一 id
     * @param playerName 玩家名称
     * @return 玩家快照
     */
    public OnlineRewardsPlayerSnapshot loadSnapshot(UUID playerUuid, String playerName) {
        OnlineRewardsPlayerState state = cachedStates.get(playerUuid);
        boolean cached = state != null;
        if (state == null) {
            state = loadState(playerUuid);
        }

        OnlineRewardsPeriodContext context = currentPeriodContext();
        boolean changed = stateEngine.normalizeForPeriod(state, context);
        if (playerName != null && !playerName.isBlank() && !playerName.equals(state.playerName())) {
            state.setPlayerName(playerName);
            changed = true;
        }

        if (changed) {
            if (cached) {
                dirtyStates.put(playerUuid, state.copy());
            } else {
                saveImmediately(playerUuid, state);
            }
        }
        return new OnlineRewardsPlayerSnapshot(state.copy(), stateEngine.snapshot(state), hasSignInRecord(playerUuid, context.rewardDate()) || stateEngine.hasSignedToday(state, context));
    }

    /**
     * 分页加载排行榜条目。
     *
     * @param scope    排行榜范围
     * @param pageSize 每页条数
     * @param page     页码（从 1 开始）
     * @return 排行榜条目列表，失败时返回空列表
     */
    public List<OnlineRewardsLeaderboardEntry> loadLeaderboard(OnlineRewardsLeaderboardScope scope, int pageSize, int page) {
        if (scope == null) {
            return List.of();
        }
        try {
            int safePage = Math.max(1, page);
            int safePageSize = Math.max(1, pageSize);
            return repository.loadLeaderboard(scope, currentPeriodKey(scope), (safePage - 1) * safePageSize, safePageSize);
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "加载在线奖励排行榜失败: " + scope.key(), exception);
            return List.of();
        }
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
        List<OnlineRewardsLeaderboardEntry> entries = leaderboardCache.getOrDefault(scope, List.of());
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
        return leaderboardCache.getOrDefault(scope, List.of()).size();
    }

    /**
     * 加载玩家签到历史涉及的月份列表（降序，限制条数）。
     *
     * @param playerUuid 玩家唯一 id
     * @param limit      返回的最大月份数
     * @return 月份字符串列表（yyyy-MM）
     */
    public List<String> loadSignInHistoryMonths(UUID playerUuid, int limit) {
        Set<String> dates = loadAllSignInDates(playerUuid);
        Set<String> months = new HashSet<>();
        for (String date : dates) {
            LocalDate parsed = parseDate(date);
            if (parsed != null) {
                months.add(parsed.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            }
        }
        List<String> sorted = new ArrayList<>(months);
        sorted.sort(Comparator.reverseOrder());
        return sorted.stream().limit(Math.max(1, limit)).toList();
    }

    /**
     * 统计玩家在指定月份的签到天数。
     *
     * @param playerUuid 玩家唯一 id
     * @param month      目标年月
     * @return 签到天数
     */
    public int countSignInDaysForMonth(UUID playerUuid, YearMonth month) {
        Set<String> dates = loadSignInDates(playerUuid, month.atDay(1), month.atEndOfMonth());
        return dates.size();
    }

    /**
     * 返回今日全服签到人数。
     *
     * @return 签到人数，查询失败返回 0
     */
    public int todaySignInCount() {
        try {
            return repository.countSignInRecords(currentPeriodContext().rewardDate());
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "统计今日签到人数失败", exception);
            return 0;
        }
    }

    /**
     * 查询玩家今日是否已签到（跨模块能力接口）。
     *
     * @param playerUuid 玩家唯一 id
     * @return 今日是否已签到
     */
    public boolean hasSignedTodayPublic(UUID playerUuid) {
        return hasSignInRecord(playerUuid, currentPeriodContext().rewardDate());
    }

    /**
     * 查询玩家今日在线分钟数（跨模块能力接口）。
     *
     * @param playerUuid 玩家唯一 id
     * @return 今日在线分钟数
     */
    public int todayOnlineMinutesPublic(UUID playerUuid) {
        OnlineRewardsPlayerState state = loadState(playerUuid);
        return state.onlineMinutes();
    }

    /**
     * 查询玩家本周在线分钟数（跨模块能力接口）。
     *
     * @param playerUuid 玩家唯一 id
     * @return 本周在线分钟数
     */
    public int weeklyOnlineMinutesPublic(UUID playerUuid) {
        OnlineRewardsPlayerState state = loadState(playerUuid);
        return state.weekMinutes();
    }

    /**
     * 查询玩家本月在线分钟数（跨模块能力接口）。
     *
     * @param playerUuid 玩家唯一 id
     * @return 本月在线分钟数
     */
    public int monthlyOnlineMinutesPublic(UUID playerUuid) {
        OnlineRewardsPlayerState state = loadState(playerUuid);
        return state.monthMinutes();
    }

    /**
     * 为在线玩家增加指定分钟数的在线时长（跨模块能力接口）。
     *
     * @param player  玩家
     * @param minutes 增加的分钟数
     * @return 操作是否成功
     */
    public boolean addOnlineMinutesPublic(Player player, int minutes) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        adjustOnlineTime(player, "add", Math.max(0, minutes));
        return true;
    }

    /**
     * 为在线玩家增加指定数量的补签卡（跨模块能力接口）。
     *
     * @param player 玩家
     * @param amount 增加的数量
     * @return 操作是否成功
     */
    public boolean addMakeupCardsPublic(Player player, int amount) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        adjustMakeupCards(player, "add", Math.max(0, amount));
        return true;
    }

    /**
     * 立即发放玩家当前已达阈值但未领取的阶段奖励（跨模块能力接口）。
     *
     * @param player 玩家
     */
    public void grantDailyRewardsPublic(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        OnlineRewardsPlayerState state = attachPlayerState(player);
        OnlineRewardsPeriodContext context = currentPeriodContext();
        List<OnlineRewardDefinition> triggered = collectReachedRewards(state);
        if (!triggered.isEmpty()) {
            for (OnlineRewardDefinition reward : triggered) {
                executeRewardCommands(player, reward, context);
            }
            saveImmediately(player.getUniqueId(), state);
            dirtyStates.remove(player.getUniqueId());
            pushSnapshot(player, stateEngine.snapshot(state));
            refreshMenu(player);
        }
    }

    /**
     * 返回距离下一个未达成的全服签到目标所需人数，无目标时返回 0。
     *
     * @return 下一个目标所需人数
     */
    public int nextServerSignInGoalRequired() {
        OnlineRewardsServerSignInGoalConfiguration goalConfig = configuration.serverSignInGoal();
        if (!goalConfig.enabled() || goalConfig.targets().isEmpty()) {
            return 0;
        }
        int signed = todaySignInCount();
        int next = 0;
        String today = currentPeriodContext().rewardDate();
        for (OnlineRewardsServerSignInGoalTarget target : goalConfig.targets()) {
            if (target.required() <= signed) {
                try {
                    if (repository.isServerGoalTriggered(today, target.id())) {
                        continue;
                    }
                } catch (SQLException ignored) {
                    continue;
                }
            }
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
    public OnlineRewardsOperationResult signIn(Player player) {
        if (player == null || !player.isOnline()) {
            return new OnlineRewardsOperationResult(false, "玩家当前不在线。");
        }

        OnlineRewardsPlayerState state = attachPlayerState(player);
        OnlineRewardsPeriodContext context = currentPeriodContext();
        if (hasSignInRecord(player.getUniqueId(), context.rewardDate()) || stateEngine.hasSignedToday(state, context)) {
            OnlineRewardsSignInResult repeated = new OnlineRewardsSignInResult(
                false,
                true,
                state.signInStreak(),
                state.signInTotal(),
                context.rewardDate(),
                context.date().getDayOfMonth()
            );
            return new OnlineRewardsOperationResult(false, renderSignInText(configuration.signIn().signInRepeatMessage(), player, repeated));
        }

        OnlineRewardsSignInResult result = applySignInRecord(player, state, context.date(), false);
        executeCommands(player, resolveSignInCommands(configuration.signIn(), result, selectedPermissionBonus(player)), result);
        dispatchSignInMailPresets(player, resolveSignInMailPresetIds(configuration.signIn(), result, selectedPermissionBonus(player)));
        refreshLeaderboardSnapshots();
        refreshMenu(player);
        publishRefresh(player.getUniqueId());
        dispatchSignInSignal(player, result);
        checkServerSignInGoals(player);
        return new OnlineRewardsOperationResult(true, renderSignInText(configuration.signIn().signInSuccessMessage(), player, result));
    }

    /**
     * 消耗补签卡对当月历史日期进行补签。
     *
     * @param player  玩家
     * @param rawDate 目标日期字符串
     * @return 操作结果
     */
    public OnlineRewardsOperationResult makeupSignIn(Player player, String rawDate) {
        if (player == null || !player.isOnline()) {
            return new OnlineRewardsOperationResult(false, "玩家当前不在线。");
        }
        if (!configuration.signIn().makeup().enabled()) {
            return new OnlineRewardsOperationResult(false, configuration.signIn().makeup().invalidDateMessage());
        }
        LocalDate date = parseDate(rawDate);
        OnlineRewardsPeriodContext context = currentPeriodContext();
        if (date == null || !YearMonth.from(date).equals(YearMonth.from(context.date())) || !date.isBefore(context.date())) {
            return new OnlineRewardsOperationResult(false, configuration.signIn().makeup().invalidDateMessage());
        }
        if (hasSignInRecord(player.getUniqueId(), date.format(DATE_FORMATTER))) {
            return new OnlineRewardsOperationResult(false, configuration.signIn().makeup().alreadySignedMessage());
        }

        OnlineRewardsPlayerState state = attachPlayerState(player);
        if (state.makeupCards() <= 0) {
            return new OnlineRewardsOperationResult(false, renderMakeupText(configuration.signIn().makeup().noCardMessage(), state, date));
        }

        state.setMakeupCards(state.makeupCards() - 1);
        OnlineRewardsSignInResult result = applySignInRecord(player, state, date, true);
        OnlineRewardsPermissionBonusReward permissionBonus = selectedPermissionBonus(player);
        executeCommands(player, resolveSignInCommands(configuration.signIn(), result, permissionBonus), result);
        dispatchSignInMailPresets(player, resolveSignInMailPresetIds(configuration.signIn(), result, permissionBonus));
        selectedCalendarDates.put(player.getUniqueId(), date);
        refreshLeaderboardSnapshots();
        refreshMenu(player);
        publishRefresh(player.getUniqueId());
        return new OnlineRewardsOperationResult(true, renderMakeupText(configuration.signIn().makeup().successMessage(), state, date));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        OnlineRewardsPlayerState state = cachedStates.get(playerUuid);
        if (state != null) {
            storeOfflineSavings(player, state);
        }
        flushPlayer(playerUuid);
        cachedStates.remove(playerUuid);
        dirtyStates.remove(playerUuid);
        failedLoads.remove(playerUuid);
        calendarMonthOffsets.remove(playerUuid);
        selectedCalendarDates.remove(playerUuid);
        actionTokens.remove(playerUuid);
        actionTokensInFlight.remove(playerUuid);
        refreshLeaderboardSnapshots();
    }

    private void storeOfflineSavings(Player player, OnlineRewardsPlayerState state) {
        OnlineRewardsOfflineSavingsConfiguration savings = configuration.offlineSavings();
        if (!savings.enabled() || savings.maxMinutes() <= 0 || savings.storageRate() <= 0.0D) {
            return;
        }
        int available = Math.max(0, state.onlineMinutes());
        int stored = (int) Math.floor(available * savings.storageRate());
        int capped = Math.min(stored, savings.maxMinutes());
        if (capped > 0) {
            state.setOfflineSavingsMinutes(Math.min(savings.maxMinutes(), state.offlineSavingsMinutes() + capped));
            dirtyStates.put(player.getUniqueId(), state.copy());
            if (configuration.debug()) {
                this.logger.info(
                    "OfflineRewards 储蓄 -> player=" + player.getName()
                        + " | available=" + available
                        + " | stored=" + capped
                        + " | totalSavings=" + state.offlineSavingsMinutes()
                );
            }
        }
    }

    void tickOnlinePlayers() {
        OnlineRewardsPeriodContext context = currentPeriodContext();
        boolean dirty = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            OnlineRewardsPlayerState state = cachedStates.get(player.getUniqueId());
            if (state == null) {
                continue;
            }
            boolean stateChanged = stateEngine.normalizeForPeriod(state, context);
            stateChanged |= applyOfflineSavings(player, state, context);
            if (!player.getName().equals(state.playerName())) {
                state.setPlayerName(player.getName());
                stateChanged = true;
            }
            if (stateChanged) {
                dirtyStates.put(player.getUniqueId(), state.copy());
                dirty = true;
            }

            int gainedMinutes = resolveOnlineMinuteGain(player, state);
            OnlineRewardsTickResult result = stateEngine.advanceMinutes(state, context, gainedMinutes);
            dirtyStates.put(player.getUniqueId(), state.copy());
            dirty = true;
            boolean savedImmediate = false;
            if (!result.triggeredRewards().isEmpty()) {
                for (OnlineRewardDefinition reward : result.triggeredRewards()) {
                    executeRewardCommands(player, reward, context);
                }
                savedImmediate = true;
            }
            List<OnlineRewardsPeriodicReward> weeklyTriggered = stateEngine.checkWeeklyRewards(state);
            List<OnlineRewardsPeriodicReward> monthlyTriggered = stateEngine.checkMonthlyRewards(state);
            if (!weeklyTriggered.isEmpty() || !monthlyTriggered.isEmpty()) {
                for (OnlineRewardsPeriodicReward reward : weeklyTriggered) {
                    executePeriodicRewardCommands(player, reward, context, "weekly");
                }
                for (OnlineRewardsPeriodicReward reward : monthlyTriggered) {
                    executePeriodicRewardCommands(player, reward, context, "monthly");
                }
                savedImmediate = true;
            }
            if (savedImmediate) {
                saveImmediately(player.getUniqueId(), state);
                dirtyStates.remove(player.getUniqueId());
            }
            pushSnapshot(player, result.snapshot());
            refreshMenu(player);
            if (configuration.debug()) {
                this.logger.info(
                    "OnlineRewards tick -> player="
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
                        + " | gained="
                        + gainedMinutes
                        + " | completed="
                        + result.snapshot().completed()
                );
            }
        }
        if (dirty) {
            flushAll();
            refreshLeaderboardSnapshots();
        }
    }

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
        if (player == null || !player.isOnline()) {
            return;
        }
        OnlineRewardsPlayerState state = attachPlayerState(player);
        OnlineRewardsPeriodContext context = currentPeriodContext();
        if (dirtyStates.containsKey(player.getUniqueId())) {
            dirtyStates.put(player.getUniqueId(), state.copy());
        }
        pushSnapshot(player, stateEngine.snapshot(state));
        if (configuration.signIn().reminderOnJoin() && !stateEngine.hasSignedToday(state, context) && !hasSignInRecord(player.getUniqueId(), context.rewardDate())) {
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

    private void sendMenuPacket(Player player, String handlerName) {
        if (player == null || !player.isOnline() || packetBridge == null || runtimeMenuUiIds.isEmpty()) {
            return;
        }
        OnlineRewardsPlayerSnapshot snapshot = loadSnapshot(player.getUniqueId(), player.getName());
        Map<String, Object> payload = OnlineRewardsMenuPacketFactory.build(configuration, snapshot, buildCalendarView(player, snapshot.state()));
        payload.put("serverSignInGoalCurrent", todaySignInCount());
        payload.put("serverSignInGoalNext", nextServerSignInGoalRequired());
        payload.put("action_token", actionTokens.getOrDefault(player.getUniqueId(), ""));
        packetBridge.sendPacketToAll(
            player,
            runtimeMenuUiIds,
            handlerName,
            payload
        );
    }

    private boolean acquireActionToken(Player player, List<String> data, int tokenIndex) {
        UUID uuid = player.getUniqueId();
        String supplied = data != null && data.size() > tokenIndex ? data.get(tokenIndex) : "";
        String expected = actionTokens.get(uuid);
        return packetBridge != null && packetBridge.isUiOpen(player, runtimeMenuUiId())
            && expected != null && expected.equals(supplied) && actionTokensInFlight.add(uuid);
    }

    private OnlineRewardsPlayerState loadState(UUID playerUuid) {
        OnlineRewardsPlayerState cached = cachedStates.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        // 首次加载失败时短暂等待后重试一次，应对 DB 连接短暂抖动
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                OnlineRewardsPlayerState loaded = repository.loadState(playerUuid);
                cachedStates.put(playerUuid, loaded);
                failedLoads.remove(playerUuid);
                return loaded;
            } catch (SQLException exception) {
                if (attempt == 0) {
                    this.logger.log(Level.WARNING, "加载在线奖励数据失败（将重试一次）: " + playerUuid, exception);
                    try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                } else {
                    this.logger.log(Level.WARNING, "加载在线奖励数据失败（重试仍失败）: " + playerUuid, exception);
                    // 不缓存空状态，避免 flush 把空数据写回 DB 覆盖真实数据
                    // 标记为加载失败，flush 将跳过该玩家
                    failedLoads.add(playerUuid);
                    return new OnlineRewardsPlayerState();
                }
            }
        }
        // 理论上不会走到这里
        failedLoads.add(playerUuid);
        return new OnlineRewardsPlayerState();
    }

    /**
     * 判断指定玩家的状态数据是否因 DB 加载失败而不可用。
     * UI 推送层据此决定是否启动自动重试。
     *
     * @param playerUuid 玩家 UUID
     * @return {@code true} 表示上次加载失败，缓存中无可信数据
     */
    public boolean isLoadFailed(UUID playerUuid) {
        return failedLoads.contains(playerUuid);
    }

    private OnlineRewardsPlayerState attachPlayerState(Player player) {
        UUID playerUuid = player.getUniqueId();
        OnlineRewardsPlayerState state = cachedStates.get(playerUuid);
        if (state == null) {
            state = loadState(playerUuid);
            // loadState 加载失败时不缓存空状态，此处仅在成功时才放入缓存
            if (!failedLoads.contains(playerUuid)) {
                cachedStates.put(playerUuid, state);
            }
        }
        OnlineRewardsPeriodContext context = currentPeriodContext();
        boolean changed = stateEngine.normalizeForPeriod(state, context);
        changed |= applyOfflineSavings(player, state, context);
        if (!player.getName().equals(state.playerName())) {
            state.setPlayerName(player.getName());
            changed = true;
        }
        if (changed) {
            dirtyStates.put(playerUuid, state.copy());
        }
        return state;
    }

    private boolean applyOfflineSavings(Player player, OnlineRewardsPlayerState state, OnlineRewardsPeriodContext context) {
        OnlineRewardsOfflineSavingsConfiguration savings = configuration.offlineSavings();
        if (!savings.enabled() || state.offlineSavingsMinutes() <= 0) {
            return false;
        }
        if (context.rewardDate().equals(state.rewardDate())) {
            return false;
        }
        int bonus = Math.min(state.offlineSavingsMinutes(), savings.maxMinutes());
        if (bonus <= 0) {
            return false;
        }
        state.setOnlineMinutes(state.onlineMinutes() + bonus);
        state.setOfflineSavingsMinutes(0);
        if (configuration.debug()) {
            this.logger.info(
                "OfflineRewards 次日应用 -> player=" + player.getName()
                    + " | bonus=" + bonus
                    + " | onlineMinutes=" + state.onlineMinutes()
            );
        }
        return true;
    }

    private void executeRewardCommands(Player player, OnlineRewardDefinition reward, OnlineRewardsPeriodContext context) {
        for (String rawCommand : reward.commands()) {
            String command = renderCommand(rawCommand, player, new OnlineRewardsSignInResult(
                false,
                false,
                0,
                0,
                context.rewardDate(),
                context.date().getDayOfMonth()
            ));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (configuration.debug()) {
                this.logger.info(
                    "OnlineRewards 发放奖励 -> player="
                        + player.getName()
                        + " | reward="
                        + reward.name()
                        + " | command="
                        + command
                );
            }
        }
        dispatchMailPresets(player, reward.mailPresetIds(), "奖励 '" + reward.name() + "'", "OnlineRewards:" + reward.name());
    }

    private void executePeriodicRewardCommands(Player player, OnlineRewardsPeriodicReward reward, OnlineRewardsPeriodContext context, String periodLabel) {
        for (String rawCommand : reward.commands()) {
            String command = renderCommand(rawCommand, player, new OnlineRewardsSignInResult(
                false,
                false,
                0,
                0,
                context.rewardDate(),
                context.date().getDayOfMonth()
            ));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (configuration.debug()) {
                this.logger.info(
                    "OnlineRewards 发放" + periodLabel + "奖励 -> player="
                        + player.getName()
                        + " | reward="
                        + reward.name()
                        + " | command="
                        + command
                );
            }
        }
        dispatchMailPresets(player, reward.mailPresetIds(), periodLabel + "奖励 '" + reward.name() + "'", "OnlineRewards:" + periodLabel + ":" + reward.name());
    }

    private void dispatchSignInMailPresets(Player player, List<String> mailPresetIds) {
        dispatchMailPresets(player, mailPresetIds, "签到", "OnlineRewards:SignIn");
    }

    private void dispatchMailPresets(Player player, List<String> mailPresetIds, String sourceLabel, String actorName) {
        if (mailPresetIds == null || mailPresetIds.isEmpty()) {
            return;
        }

        MailDispatchable mailService = mailProvider == null ? null : mailProvider.get();
        if (mailService == null) {
            this.logger.warning(
                "OnlineRewards " + sourceLabel + " 配置了邮件预设，但 Mail 模块当前不可用。"
            );
            return;
        }

        for (String presetId : mailPresetIds) {
            if (presetId == null || presetId.isBlank()) {
                continue;
            }
            boolean success = mailService.dispatchPreset(presetId, player.getName(), actorName);
            if (configuration.debug()) {
                this.logger.info(
                    "OnlineRewards 邮件奖励 -> player="
                        + player.getName()
                        + " | source="
                        + sourceLabel
                        + " | preset="
                        + presetId
                        + " | success="
                        + success
                );
            } else if (!success) {
                this.logger.warning(
                    "OnlineRewards 邮件奖励派发失败 -> player="
                        + player.getName()
                        + " | source="
                        + sourceLabel
                        + " | preset="
                        + presetId
                );
            }
        }
    }

    private void executeCommands(Player player, List<String> commands, OnlineRewardsSignInResult result) {
        for (String rawCommand : commands) {
            String command = renderCommand(rawCommand, player, result);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            if (configuration.debug()) {
                this.logger.info(
                    "OnlineRewards 签到奖励 -> player="
                        + player.getName()
                        + " | command="
                        + command
                );
            }
        }
    }

    private void pushSnapshot(Player player, OnlineRewardsProgressSnapshot snapshot) {
        if (player == null || !player.isOnline()) {
            return;
        }
        clientBridge.sendServerVariable(player, configuration.progressVariableName(), snapshot.progress());
        clientBridge.sendServerVariable(player, configuration.titleVariableName(), snapshot.title());
    }

    private void publishRefresh(UUID playerUuid) {
        if (playerUuid != null && crossServerChannel != null && crossServerChannel.isActive()) {
            crossServerChannel.publish("refresh:" + playerUuid);
        }
    }

    private void handleCrossServerMessage(String message) {
        if (configuration.debug()) {
            this.logger.info("收到 OnlineRewards 跨服消息: " + message);
        }
        if (message == null || message.isBlank() || !message.startsWith("refresh:")) {
            return;
        }
        String rawUuid = message.substring("refresh:".length()).trim();
        try {
            UUID playerUuid = UUID.fromString(rawUuid);
            AxsScheduler.runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player == null || !player.isOnline()) {
                    return;
                }
                cachedStates.remove(playerUuid);
                dirtyStates.remove(playerUuid);
                syncPlayer(player);
                refreshMenu(player);
                refreshLeaderboardSnapshots();
            });
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void flushPlayer(UUID playerUuid) {
        // 跳过加载失败的玩家：缓存中的状态不可信，写回会覆盖 DB 真实数据
        if (failedLoads.contains(playerUuid)) {
            return;
        }
        OnlineRewardsPlayerState dirtyState = dirtyStates.remove(playerUuid);
        if (dirtyState == null) {
            return;
        }
        saveImmediately(playerUuid, dirtyState);
    }

    private void flushAll() {
        for (Map.Entry<UUID, OnlineRewardsPlayerState> entry : dirtyStates.entrySet()) {
            // 跳过加载失败的玩家：缓存中的状态不可信，写回会覆盖 DB 真实数据
            if (failedLoads.contains(entry.getKey())) {
                continue;
            }
            saveImmediately(entry.getKey(), entry.getValue());
        }
        dirtyStates.clear();
    }

    private void saveImmediately(UUID playerUuid, OnlineRewardsPlayerState state) {
        try {
            repository.saveState(playerUuid, state);
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "保存在线奖励数据失败: " + playerUuid, exception);
        }
    }

    /**
     * 管理命令：按操作类型调整玩家在线时长。
     *
     * @param player    目标玩家
     * @param operation 操作类型（add/remove/set）
     * @param minutes   分钟数
     * @return 操作结果
     */
    public OnlineRewardsOperationResult adjustOnlineTime(Player player, String operation, int minutes) {
        if (player == null || !player.isOnline()) {
            return new OnlineRewardsOperationResult(false, "目标玩家必须在线。");
        }
        int safeMinutes = Math.max(0, minutes);
        OnlineRewardsPlayerState state = attachPlayerState(player);
        OnlineRewardsPeriodContext context = currentPeriodContext();
        stateEngine.normalizeForPeriod(state, context);

        List<OnlineRewardDefinition> triggeredRewards = new ArrayList<>();
        String normalizedOperation = operation == null ? "" : operation.toLowerCase();
        if ("add".equals(normalizedOperation)) {
            triggeredRewards.addAll(stateEngine.advanceMinutes(state, context, safeMinutes).triggeredRewards());
        } else if ("remove".equals(normalizedOperation)) {
            state.setOnlineMinutes(state.onlineMinutes() - safeMinutes);
            state.setWeekMinutes(state.weekMinutes() - safeMinutes);
            state.setMonthMinutes(state.monthMinutes() - safeMinutes);
            state.setTotalMinutes(state.totalMinutes() - safeMinutes);
        } else if ("set".equals(normalizedOperation)) {
            int previousDaily = state.onlineMinutes();
            int delta = safeMinutes - previousDaily;
            state.setOnlineMinutes(safeMinutes);
            state.setWeekMinutes(state.weekMinutes() + delta);
            state.setMonthMinutes(state.monthMinutes() + delta);
            state.setTotalMinutes(state.totalMinutes() + delta);
            triggeredRewards.addAll(collectReachedRewards(state));
        } else {
            return new OnlineRewardsOperationResult(false, "无效在线时长操作: " + operation);
        }

        dirtyStates.put(player.getUniqueId(), state.copy());
        for (OnlineRewardDefinition reward : triggeredRewards) {
            executeRewardCommands(player, reward, context);
        }
        saveImmediately(player.getUniqueId(), state);
        dirtyStates.remove(player.getUniqueId());
        refreshLeaderboardSnapshots();
        pushSnapshot(player, stateEngine.snapshot(state));
        refreshMenu(player);
        publishRefresh(player.getUniqueId());
        return new OnlineRewardsOperationResult(
            true,
            "已更新 " + player.getName() + " 今日在线=" + OnlineRewardsTextFormats.formatMinutes(state.onlineMinutes())
                + "，本周=" + OnlineRewardsTextFormats.formatMinutes(state.weekMinutes())
                + "，本月=" + OnlineRewardsTextFormats.formatMinutes(state.monthMinutes())
                + "，总计=" + OnlineRewardsTextFormats.formatMinutes(state.totalMinutes())
        );
    }

    /**
     * 管理命令：按操作类型调整玩家离线储蓄分钟数。
     *
     * @param player    目标玩家
     * @param operation 操作类型（add/set/reset）
     * @param minutes   分钟数
     * @return 操作结果
     */
    public OnlineRewardsOperationResult adjustOfflineSavings(Player player, String operation, int minutes) {
        if (player == null || !player.isOnline()) {
            return new OnlineRewardsOperationResult(false, "目标玩家必须在线。");
        }
        int safeMinutes = Math.max(0, minutes);
        OnlineRewardsPlayerState state = attachPlayerState(player);
        String normalizedOperation = operation == null ? "" : operation.toLowerCase();
        switch (normalizedOperation) {
            case "add" -> state.setOfflineSavingsMinutes(state.offlineSavingsMinutes() + safeMinutes);
            case "remove", "take" -> state.setOfflineSavingsMinutes(Math.max(0, state.offlineSavingsMinutes() - safeMinutes));
            case "set" -> state.setOfflineSavingsMinutes(safeMinutes);
            case "reset" -> state.setOfflineSavingsMinutes(0);
            default -> {
                return new OnlineRewardsOperationResult(false, "无效离线储蓄操作: " + operation);
            }
        }
        dirtyStates.put(player.getUniqueId(), state.copy());
        saveImmediately(player.getUniqueId(), state);
        dirtyStates.remove(player.getUniqueId());
        refreshMenu(player);
        publishRefresh(player.getUniqueId());
        return new OnlineRewardsOperationResult(
            true,
            "已更新 " + player.getName() + " 离线储蓄=" + OnlineRewardsTextFormats.formatMinutes(state.offlineSavingsMinutes())
        );
    }

    /**
     * 管理命令：按操作类型调整玩家补签卡数量。
     *
     * @param player    目标玩家
     * @param operation 操作类型（add/remove/set）
     * @param amount    数量
     * @return 操作结果
     */
    public OnlineRewardsOperationResult adjustMakeupCards(Player player, String operation, int amount) {
        if (player == null || !player.isOnline()) {
            return new OnlineRewardsOperationResult(false, "目标玩家必须在线。");
        }
        int safeAmount = Math.max(0, amount);
        OnlineRewardsPlayerState state = attachPlayerState(player);
        String normalizedOperation = operation == null ? "" : operation.toLowerCase();
        switch (normalizedOperation) {
            case "add" -> state.setMakeupCards(state.makeupCards() + safeAmount);
            case "remove", "take" -> state.setMakeupCards(state.makeupCards() - safeAmount);
            case "set" -> state.setMakeupCards(safeAmount);
            default -> {
                return new OnlineRewardsOperationResult(false, "无效补签卡操作: " + operation);
            }
        }
        dirtyStates.put(player.getUniqueId(), state.copy());
        saveImmediately(player.getUniqueId(), state);
        dirtyStates.remove(player.getUniqueId());
        refreshMenu(player);
        publishRefresh(player.getUniqueId());
        return new OnlineRewardsOperationResult(true, "已更新 " + player.getName() + " " + configuration.signIn().makeup().cardName() + "=" + state.makeupCards());
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

    private int resolveOnlineMinuteGain(Player player, OnlineRewardsPlayerState state) {
        double multiplier = selectedTimeMultiplier(player);
        double rawGain = multiplier + state.timeBonusRemainder();
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

    private OnlineRewardsSignInResult applySignInRecord(Player player, OnlineRewardsPlayerState state, LocalDate date, boolean makeup) {
        String dateText = date.format(DATE_FORMATTER);
        try {
            if (!repository.tryInsertSignInRecord(player.getUniqueId(), player.getName(), dateText, makeup)) {
                return new OnlineRewardsSignInResult(
                    false,
                    true,
                    state.signInStreak(),
                    state.signInTotal(),
                    dateText,
                    date.getDayOfMonth()
                );
            }
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "保存签到记录失败: " + player.getUniqueId() + " " + dateText, exception);
            return new OnlineRewardsSignInResult(false, false, state.signInStreak(), state.signInTotal(), dateText, date.getDayOfMonth());
        }
        Set<String> dates = loadAllSignInDates(player.getUniqueId());
        if (!dates.contains(dateText)) {
            dates = new HashSet<>(dates);
            dates.add(dateText);
        }
        LocalDate latestDate = latestDate(dates);
        int total = dates.size();
        int streak = latestDate == null ? 0 : calculateStreakEndingAt(dates, latestDate);
        int rewardStreak = calculateStreakEndingAt(dates, date);
        state.setLastSignInDate(latestDate == null ? "" : latestDate.format(DATE_FORMATTER));
        state.setSignInStreak(streak);
        state.setSignInTotal(total);
        dirtyStates.put(player.getUniqueId(), state.copy());
        saveImmediately(player.getUniqueId(), state);
        dirtyStates.remove(player.getUniqueId());
        return new OnlineRewardsSignInResult(true, false, rewardStreak, total, dateText, date.getDayOfMonth());
    }

    private boolean hasSignInRecord(UUID playerUuid, String date) {
        try {
            return repository.hasSignInRecord(playerUuid, date);
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "检查签到记录失败: " + playerUuid + " " + date, exception);
            return false;
        }
    }

    private Set<String> loadAllSignInDates(UUID playerUuid) {
        try {
            return repository.loadAllSignInDates(playerUuid);
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "加载签到历史失败: " + playerUuid, exception);
            return Set.of();
        }
    }

    private Set<String> loadSignInDates(UUID playerUuid, LocalDate fromDate, LocalDate toDate) {
        try {
            return repository.loadSignInDates(playerUuid, fromDate.format(DATE_FORMATTER), toDate.format(DATE_FORMATTER));
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "加载签到日历失败: " + playerUuid, exception);
            return Set.of();
        }
    }

    private OnlineRewardsCalendarView buildCalendarView(Player player, OnlineRewardsPlayerState state) {
        OnlineRewardsPeriodContext context = currentPeriodContext();
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
        Set<String> signedDates = loadSignInDates(player.getUniqueId(), month.atDay(1), month.atEndOfMonth());
        boolean canMakeup = canMakeup(state, signedDates, selectedDate, context.date());
        return new OnlineRewardsCalendarView(
            offset,
            month,
            context.date(),
            selectedDate,
            signedDates,
            buildRewardPreviewRows(player, selectedDate),
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

    private List<OnlineRewardsRewardPreviewRow> buildRewardPreviewRows(Player player, LocalDate selectedDate) {
        Set<String> dates = new HashSet<>(loadAllSignInDates(player.getUniqueId()));
        String dateText = selectedDate.format(DATE_FORMATTER);
        dates.add(dateText);
        int streak = calculateStreakEndingAt(dates, selectedDate);
        int total = dates.size();
        OnlineRewardsSignInResult preview = new OnlineRewardsSignInResult(true, false, streak, total, dateText, selectedDate.getDayOfMonth());
        return resolveSignInPreviewRows(configuration.signIn(), preview, selectedPermissionBonus(player));
    }

    private LocalDate latestDate(Set<String> dates) {
        return dates.stream()
            .map(this::parseDate)
            .filter(date -> date != null)
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    private int calculateStreakEndingAt(Set<String> dates, LocalDate endingDate) {
        int streak = 0;
        LocalDate cursor = endingDate;
        while (cursor != null && dates.contains(cursor.format(DATE_FORMATTER))) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private void refreshLeaderboardSnapshots() {
        for (OnlineRewardsLeaderboardScope scope : OnlineRewardsLeaderboardScope.values()) {
            try {
                leaderboardCache.put(scope, repository.loadLeaderboard(scope, currentPeriodKey(scope), 0, LEADERBOARD_CACHE_SIZE));
            } catch (SQLException exception) {
                this.logger.log(Level.WARNING, "刷新在线奖励排行榜缓存失败: " + scope.key(), exception);
                leaderboardCache.put(scope, List.of());
            }
        }
    }

    private OnlineRewardsPeriodContext currentPeriodContext() {
        LocalDate currentDate = LocalDate.now(clock);
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

    private String renderCommand(String template, Player player, OnlineRewardsSignInResult result) {
        return renderText(template, player.getName(), result);
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

    private String renderMakeupText(String template, OnlineRewardsPlayerState state, LocalDate date) {
        return (template == null ? "" : template)
            .replace("{card}", configuration.signIn().makeup().cardName())
            .replace("{cards}", Integer.toString(state.makeupCards()))
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
        OnlineRewardsServerSignInGoalConfiguration goalConfig = configuration.serverSignInGoal();
        if (!goalConfig.enabled() || goalConfig.targets().isEmpty()) {
            return;
        }
        String today = currentPeriodContext().rewardDate();
        int signedCount;
        try {
            signedCount = repository.countSignInRecords(today);
        } catch (SQLException exception) {
            this.logger.log(Level.WARNING, "统计今日签到人数失败", exception);
            return;
        }
        for (OnlineRewardsServerSignInGoalTarget target : goalConfig.targets()) {
            if (signedCount < target.required()) {
                continue;
            }
            try {
                if (repository.isServerGoalTriggered(today, target.id())) {
                    continue;
                }
                repository.saveServerGoalTriggered(today, target.id());
            } catch (SQLException exception) {
                this.logger.log(Level.WARNING, "保存全服签到目标状态失败: " + target.id(), exception);
                continue;
            }
            broadcastServerGoal(triggerPlayer, target, signedCount);
            for (Player online : Bukkit.getOnlinePlayers()) {
                executeServerGoalCommands(online, target);
            }
            publishEventBus("onlinerewards.server_goal_reached", triggerPlayer, Map.of(
                "goal_id", target.id(),
                "goal_name", target.name(),
                "required", String.valueOf(target.required()),
                "signed_count", String.valueOf(signedCount)
            ));
        }
    }

    private void broadcastServerGoal(Player triggerPlayer, OnlineRewardsServerSignInGoalTarget target, int signedCount) {
        OnlineRewardsServerSignInGoalConfiguration goalConfig = configuration.serverSignInGoal();
        String message = target.broadcastMessage();
        if (message == null || message.isBlank()) {
            message = "§e[全服签到] §f" + target.name() + " §7已达成！今日签到人数：§f" + signedCount + "§7。";
        }
        if (goalConfig.broadcast()) {
            Bukkit.broadcastMessage(message.replace("{player}", triggerPlayer.getName()));
        }
        QQBotBroadcastable qqBot = qqBotProvider != null ? qqBotProvider.get() : null;
        if (qqBot != null) {
            try {
                qqBot.sendToAllGroups(message.replace("{player}", triggerPlayer.getName()));
            } catch (Exception ignored) {
            }
        }
    }

    private void executeServerGoalCommands(Player player, OnlineRewardsServerSignInGoalTarget target) {
        for (String rawCommand : target.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rawCommand.replace("{player}", player.getName()));
        }
        dispatchMailPresets(player, target.mailPresetIds(), "全服签到目标 '" + target.name() + "'", "OnlineRewards:ServerGoal:" + target.id());
        ChatCardSendable chatCard = chatCardProvider != null ? chatCardProvider.get() : null;
        for (String cardId : target.chatCardIds()) {
            if (chatCard != null) {
                chatCard.sendChatCard(player, cardId, Map.of("goal", target.name()));
            }
        }
        SubtitlePlayable subtitle = subtitleProvider != null ? subtitleProvider.get() : null;
        for (String groupId : target.subtitleGroupIds()) {
            if (subtitle != null) {
                subtitle.playGroup(player, groupId);
            }
        }
        TitleGrantable title = titleProvider != null ? titleProvider.get() : null;
        for (String titleId : target.titleIds()) {
            if (title != null) {
                title.giveTitle(player.getUniqueId(), titleId, "permanent", "OnlineRewards:ServerGoal");
            }
        }
    }

    private void publishEventBus(String topic, Player player, Map<String, String> payload) {
        EventBusCapability bus = eventBusProvider != null ? eventBusProvider.get() : null;
        if (bus != null) {
            bus.publish(topic, player, payload);
        }
    }
}
