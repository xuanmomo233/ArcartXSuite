package xuanmo.arcartxsuite.afkreward.service;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ArrayBlockingQueue;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.command.ThrottledCommandExecutor;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.mail.MailPresetRequest;
import xuanmo.arcartxsuite.api.capability.mail.MailSendResult;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.api.item.PendingRewardService.PendingReward;
import xuanmo.arcartxsuite.api.util.ItemSerializer;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.afkreward.config.AfkRewardConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;
import xuanmo.arcartxsuite.afkreward.model.AfkItemReward;
import xuanmo.arcartxsuite.afkreward.model.AfkRewardType;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.SessionRecord;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.AreaStats;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.PlayerStats;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.QuitCheckpoint;

/**
 * 挂机奖励核心服务，管理玩家 AFK 状态判定、奖励调度与发放。
 * <p>
 * 通过定时任务周期性检查玩家活动状态，对处于 AFK 的玩家按配置发放奖励，
 * 支持 TPS 阈值限制、手动保护、每日上限等策略。
 */
public final class AfkRewardService {

    public enum EndReason {
        MANUAL, COMBAT, AREA_REMOVED, NO_PERMISSION, TIME_LIMIT, FULL
    }

    public enum ManualProtection {
        MOVEMENT, TELEPORT, INTERACT, BLOCK_BREAK, INVENTORY,
        RECEIVE_DAMAGE, DEAL_DAMAGE, ENTITY_TARGET, VEHICLE_ENTER,
        INTERACT_ENTITY, DROP_ITEM, SWAP_HAND, PICKUP_ITEM, EXPERIENCE,
        COLLIDABLE
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long TICK_INTERVAL = 20L; // 每秒一次
    private static final long SAVE_INTERVAL = 600L; // 30 秒定期保存一次（单位：tick，20 tick = 1 秒）
    private static final int STATS_READS_PER_BATCH = 32;
    private static final int REGION_REWARD_BATCH_LIMIT = 512;
    private static final int REGION_REWARD_WRITES_PER_TICK = 8;
    private static final float MANUAL_WALK_SPEED = 0.0001f;
    private static final Map<String, String> HIDDEN_HUD = Map.of(
        "area", "", "time", "", "next", "0", "players", "0",
        "session_rewards", "0", "session_time", "", "visible", "false"
    );

    private final JavaPlugin plugin;
    private final AfkRewardConfiguration config;
    private final AfkRewardRepository repository;
    private final Logger logger;
    private final MessageProvider messages;

    private final Supplier<MailDispatchable> mailProvider;
    private final Supplier<SignalDispatchable> signalProvider;
    private final Supplier<SubtitlePlayable> subtitleProvider;
    private final Supplier<EssentialsQueryable> essentialsProvider;
    private Supplier<EventBusCapability> eventBusProvider;
    private volatile ItemSourceRegistry itemSourceRegistry;
    private PendingRewardService pendingRewards;
    private final Map<UUID, RegionRewardBatch> regionRewardBatches = new LinkedHashMap<>();

    // 在线玩家状态
    private final Map<UUID, PlayerAfkState> afkStates = new ConcurrentHashMap<>();
    // 玩家统计缓存
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayerStats = new HashSet<>();
    private StatsCheckpoint pendingStatsCheckpoint;
    private final Map<UUID, PlayerStats> requestedStats = new LinkedHashMap<>();
    private final List<SettlementReceipt> settlementReceipts = new ArrayList<>();
    private long nextStatsWrite;
    // 区域统计缓存
    private final Map<UUID, PlayerAreaStats> areaStatsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> dirtyAreaStats = new HashMap<>();
    // Only the server thread updates pending loads and their accumulated seconds.
    private final Map<UUID, PendingPlayerStats> playerStatsLoads = new LinkedHashMap<>();
    private final Map<UUID, PlayerAreaStats> areaStatsLoads = new LinkedHashMap<>();
    private boolean statsReadInProgress;
    private long statsTick;
    // 已发送进入提示的玩家
    private final Set<UUID> enterNotified = ConcurrentHashMap.newKeySet();
    // HUD 显示开关
    private final Set<UUID> hudDisabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, HudSnapshot> sentHuds = new HashMap<>();
    private final Set<UUID> refreshHuds = new HashSet<>();
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dailySeconds = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> viewChanges = new ConcurrentHashMap<>();
    private final Set<UUID> antiAbuseNotified = ConcurrentHashMap.newKeySet();
    // 尚未取得可信玩家统计的记录，区域统计的失败独立重试。
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();
    private final Map<UUID, EndReason> pendingManualEndReasons = new ConcurrentHashMap<>();
    private volatile String dailySecondsDate = LocalDate.now().format(DATE_FMT);
    private final AfkRewardAuditLogger auditLogger;
    private volatile PacketBridgeAPI packetBridge;
    private volatile String hudUiId;

    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private volatile boolean active;
    private boolean stopping;
    private final ModuleCallback callbacks = new ModuleCallback(() -> {});
    // A periodic write, a quit batch, a manual command and the final checkpoint can queue independently.
    private final AtomicBoolean queuedManualWrite = new AtomicBoolean();
    private final ModuleExecutor writes = new ModuleExecutor(0, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(4), action -> {
            var thread = new Thread(action, "AXS-AfkReward-IO");
            thread.setDaemon(true);
            return thread;
        });

    // 排行榜缓存
    private volatile List<PlayerStats> leaderboardCache = List.of();
    private final AtomicBoolean leaderboardLoading = new AtomicBoolean();
    private volatile long nextLeaderboardRefresh;

    private final Set<UUID> recoveryChecked = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingSessionRead> sessionReads = new LinkedHashMap<>();
    private final Map<UUID, PendingSessionRead> readySessionReads = new HashMap<>();
    private final Map<UUID, Long> nextRecoveryAttempt = new HashMap<>();
    private final Set<UUID> startingManual = new HashSet<>();
    private final Map<UUID, SettlementReceipt> endingManual = new HashMap<>();
    private final Map<UUID, SessionRecord> pendingSessionWrites = new HashMap<>();
    private final Map<UUID, QuitCheckpoint> quitCheckpoints = new LinkedHashMap<>();
    private List<QuitCheckpoint> inFlightQuitCheckpoints = List.of();
    private CompletableFuture<Boolean> quitWrite;
    private long nextQuitWrite;

    public AfkRewardService(JavaPlugin plugin, AfkRewardConfiguration config,
                            AfkRewardRepository repository, Logger logger,
                            MessageProvider messages,
                            Supplier<MailDispatchable> mailProvider,
                            Supplier<SignalDispatchable> signalProvider,
                            Supplier<SubtitlePlayable> subtitleProvider,
                            Supplier<EssentialsQueryable> essentialsProvider) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.logger = logger;
        this.messages = messages;
        this.mailProvider = mailProvider;
        this.signalProvider = signalProvider;
        this.subtitleProvider = subtitleProvider;
        this.essentialsProvider = essentialsProvider;
        this.auditLogger = new AfkRewardAuditLogger(plugin.getDataFolder(), config.audit(), logger);
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void setHudBridge(PacketBridgeAPI packetBridge, String hudUiId) {
        this.packetBridge = packetBridge;
        this.hudUiId = hudUiId;
        refreshHuds.addAll(sentHuds.keySet());
    }

    public void onClientInitialized(Player player) {
        UUID id = player.getUniqueId();
        if (sentHuds.containsKey(id)) refreshHuds.add(id);
    }

    public void setItemSourceRegistry(ItemSourceRegistry itemSourceRegistry) {
        this.itemSourceRegistry = itemSourceRegistry;
    }

    public void setPendingRewardService(PendingRewardService pendingRewards) {
        this.pendingRewards = pendingRewards;
    }

    public void start() {
        active = true;
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                callbacks.call(() -> { tick(); return null; }, null);
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);

        // 定期保存在线玩家统计到数据库，防止 totalSeconds 长期不持久化
        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                callbacks.call(() -> { saveOnlineStats(true); return null; }, null);
            }
        }.runTaskTimer(plugin, SAVE_INTERVAL, SAVE_INTERVAL);

        for (Player player : Bukkit.getOnlinePlayers()) {
            preloadStats(player.getUniqueId(), player.getName());
        }
        scheduleStatsReads();

        // 预加载排行榜
        requestLeaderboardRefresh();
    }

    public void shutdown() {
        if (stopping) return;
        stopping = true;
        active = false;
        callbacks.close();
        List<QuitCheckpoint> finalCheckpoints = List.of();
        Map<UUID, PlayerStats> finalStats = Map.of();
        List<SettlementReceipt> finalReceipts = List.of();
        StatsCheckpoint priorWrite = pendingStatsCheckpoint;
        var finalRewardBatches = List.copyOf(regionRewardBatches.values());
        regionRewardBatches.clear();
        try {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        finalCheckpoints = captureShutdownCheckpoints();
        finalStats = statsWriteSnapshot();
        var remainingReceipts = new ArrayList<>(settlementReceipts);
        var queuedReceipts = new HashSet<>(settlementReceipts);
        for (var claim : endingManual.values()) {
            if (queuedReceipts.add(claim)) remainingReceipts.add(claim);
        }
        finalReceipts = List.copyOf(remainingReceipts);
        pendingStatsCheckpoint = null;
        requestedStats.clear();
        settlementReceipts.clear();
        for (var entry : afkStates.entrySet()) {
            if (entry.getValue().mode != AfkMode.MANUAL) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) restoreManualState(player, entry.getValue());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideHud(player);
        }
        afkStates.clear();
        recoveryChecked.clear();
        sessionReads.clear();
        readySessionReads.clear();
        nextRecoveryAttempt.clear();
        startingManual.clear();
        endingManual.clear();
        pendingSessionWrites.clear();
        quitCheckpoints.clear();
        inFlightQuitCheckpoints = List.of();
        quitWrite = null;
        statsCache.clear();
        dirtyPlayerStats.clear();
        playerStatsLoads.clear();
        areaStatsLoads.clear();
        dirtyAreaStats.clear();
        areaStatsCache.clear();
        enterNotified.clear();
        hudDisabled.clear();
        sentHuds.clear();
        refreshHuds.clear();
        lastCombat.clear();
        dailySeconds.clear();
        viewChanges.clear();
        antiAbuseNotified.clear();
        failedLoads.clear();
        pendingManualEndReasons.clear();
        } finally {
            var checkpoints = finalCheckpoints;
            var stats = finalStats;
            var receipts = finalReceipts;
            callbacks.stopped().whenComplete((ignored, failure) -> {
                try {
                    if (!checkpoints.isEmpty() || !stats.isEmpty() || !receipts.isEmpty() || !finalRewardBatches.isEmpty()) writes.execute(() -> {
                        for (var batch : finalRewardBatches) {
                            if (!persistRewardBatch(batch.key, batch.rewards)) logger.warning("挂机奖励停用交接未确认: " + batch.key);
                        }
                        try {
                            repository.saveQuitCheckpoints(checkpoints);
                            repository.saveStatsBatch(stats);
                            List<SettlementReceipt> remaining = receipts;
                            if (priorWrite != null && priorWrite.done().isDone() && !priorWrite.done().isCompletedExceptionally()) {
                                remaining = new ArrayList<>(priorWrite.done().join());
                                remaining.addAll(receipts.subList(priorWrite.receipts().size(), receipts.size()));
                            }
                            writeSettlementReceipts(remaining);
                        }
                        catch (SQLException error) {
                            logger.warning("挂机停用保存未确认 " + checkpoints.size() + " 份检查点: " + error.getMessage());
                        }
                    });
                } finally { writes.shutdown(); }
            });
        }
    }

    public CompletionStage<Void> databaseStopped() { return writes.stopped(); }

    public <T> T callApi(Supplier<T> operation, T unavailable) { return callbacks.call(operation, unavailable); }

    private void readAsync(Runnable work) {
        if (!active) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> callbacks.call(() -> {
            if (active) work.run();
            return null;
        }, null));
    }

    private void onMain(Runnable work) {
        if (!active) return;
        Bukkit.getScheduler().runTask(plugin, () -> callbacks.call(() -> {
            if (active) work.run();
            return null;
        }, null));
    }

    private void tick() {
        if (!active) return;
        pumpRegionRewards();
        scheduleQuitCheckpoints();
        finishStatsCheckpoint();
        if (!requestedStats.isEmpty() || !settlementReceipts.isEmpty()) saveOnlineStats(false);
        if (config.performance().pauseOnLowTps()
            && TpsProvider.currentTps() < config.performance().minTps()) return;
        statsTick++;
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        for (Player player : online) {
            if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) continue;
            preloadStats(player.getUniqueId(), player.getName());
            queueSessionRecovery(player);
            UUID uuid = player.getUniqueId();
            if (!startingManual.contains(uuid) && !endingManual.containsKey(uuid)) processPlayer(player);
            if (!active) return;
        }
        if (!requestedStats.isEmpty() || !settlementReceipts.isEmpty()) saveOnlineStats(false);
        scheduleStatsReads();
        pushHuds(online);
    }

    private void pushHuds(Collection<? extends Player> online) {
        PacketBridgeAPI bridge = packetBridge;
        if (bridge == null || hudUiId == null || !bridge.isAvailable()) return;
        if (afkStates.isEmpty() && sentHuds.isEmpty()) return;

        Map<String, Integer> populations = null;
        for (Player player : online) {
            UUID id = player.getUniqueId();
            if (!player.isOnline()) continue;
            PlayerAfkState state = afkStates.get(id);
            HudSnapshot previous = sentHuds.get(id);
            String areaName = state == null ? null : state.areaName;
            if (areaName == null || !isHudEnabled(id) || !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(id)) {
                hideHud(player);
                continue;
            }
            if (populations == null) populations = countOnlineAreas(online);
            AfkArea hudArea = config.areas().get(areaName);
            int roundSeconds = hudArea != null ? hudArea.reward().roundMinutes() * 60 : 15 * 60;
            int remaining = Math.max(0, roundSeconds - (state.seconds - state.lastRewardSeconds));
            HudSnapshot snapshot = new HudSnapshot(areaName, state.seconds, remaining,
                populations.getOrDefault(areaName, 0), state.sessionRewards);
            if ((refreshHuds.contains(id) || !snapshot.equals(previous)) && sendHud(player, snapshot.payload())) {
                sentHuds.put(id, snapshot);
                refreshHuds.remove(id);
            }
        }
    }

    private Map<String, Integer> countOnlineAreas(Collection<? extends Player> online) {
        Map<String, Integer> populations = new HashMap<>();
        for (Player player : online) {
            PlayerAfkState state = afkStates.get(player.getUniqueId());
            String areaName = state == null ? null : state.areaName;
            if (areaName != null && player.isOnline()) {
                populations.merge(areaName, 1, Integer::sum);
            }
        }
        return populations;
    }

    private void hideHud(Player player) {
        UUID id = player.getUniqueId();
        if (sentHuds.containsKey(id) && sendHud(player, HIDDEN_HUD)) {
            sentHuds.remove(id);
            refreshHuds.remove(id);
        }
    }

    private boolean sendHud(Player player, Map<String, String> payload) {
        PacketBridgeAPI bridge = packetBridge;
        if (bridge == null || hudUiId == null || !bridge.isAvailable()) return false;
        try {
            return bridge.sendPacket(player, hudUiId, "update", payload);
        } catch (RuntimeException ignored) {
            // 客户端桥接不可用时不影响挂机状态清理。
            return false;
        }
    }

    private record HudSnapshot(String area, int seconds, int next, int players, int rewards) {
        Map<String, String> payload() {
            String time = formatTime(seconds);
            return Map.of("area", area, "time", time, "next", String.valueOf(next),
                "players", String.valueOf(players), "session_rewards", String.valueOf(rewards),
                "session_time", time, "visible", "true");
        }
    }

    private void processPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerAfkState state = afkStates.get(uuid);

        // MANUAL 模式：不检测位置，只累加时间
        if (state != null && state.mode == AfkMode.MANUAL && state.areaName != null) {
            AfkArea area = config.areas().get(state.areaName);
            int recheck = config.manual().permissionRecheckSeconds();
            boolean shouldRecheck = state.seconds == 0 || recheck <= 1
                || state.seconds % recheck == 0;
            if (shouldRecheck && (area == null || !area.enabled())) {
                // 区域被删除或禁用，强制结束
                endManualAfk(player, true, EndReason.AREA_REMOVED);
                return;
            }
            if (shouldRecheck && !player.hasPermission("axs.afkreward.area." + state.areaName)) {
                endManualAfk(player, true, EndReason.NO_PERMISSION);
                player.sendMessage(messages != null ? messages.get("hints.no-permission-area") : "§c你没有权限在此区域挂机。");
                return;
            }
            state.seconds++;
            updateTotalTime(uuid, 1);
            updateDailySeconds(uuid, 1);
            return;
        }

        // REGION 模式（原有逻辑）
        String world = player.getWorld().getName();
        var location = player.getLocation();
        int x = location.getBlockX();
        int z = location.getBlockZ();

        AfkArea currentArea = findArea(world, x, z);

        if (currentArea == null || !currentArea.enabled()) {
            if (state != null && state.areaName != null) {
                // 离开区域
                dispatchSignal("afk_leave_area", player, Map.of(
                    "area", state.areaName, "mode", "REGION",
                    "seconds", String.valueOf(state.seconds)
                ));
                sendLeaveHint(player, state.areaName);
                state.areaName = null;
                state.seconds = 0;
                state.lastRewardSeconds = 0;
                enterNotified.remove(uuid);
            }
            return;
        }

        // 进入新区域
        if (state == null || !currentArea.name().equals(state.areaName)) {
            if (state == null) {
                state = new PlayerAfkState();
                afkStates.put(uuid, state);
            }
            state.mode = AfkMode.REGION;
            state.areaName = currentArea.name();
            state.rewardType = currentArea.id();
                state.seconds = 0;
                state.lastRewardSeconds = 0;
                state.sessionRewards = 0;
                state.startTimeMillis = System.currentTimeMillis();
                enterNotified.remove(uuid);
                antiAbuseNotified.remove(uuid);
        }

        // 权限检查
        if (!player.hasPermission("axs.afkreward.area." + currentArea.name())) {
            if (!enterNotified.contains(uuid)) {
                player.sendMessage(messages != null ? messages.get("hints.no-permission-area") : "§c你没有权限在此区域挂机。");
                enterNotified.add(uuid);
            }
            return;
        }

        // 人数限制
        if (currentArea.reward().player().enabled()) {
            int count = countPlayersInArea(currentArea.name());
            if (count > currentArea.reward().player().limit()) {
                if (!enterNotified.contains(uuid)) {
                    player.sendMessage(messages != null ? messages.get("hints.player-limit") : "§c该区域已满员，请稍后再来。");
                    enterNotified.add(uuid);
                }
                return;
            }
        }

        // 进入提示 + 信号
        if (!enterNotified.contains(uuid)) {
            dispatchSignal("afk_enter_area", player, Map.of(
                "area", currentArea.name(), "mode", "REGION"
            ));
            String msg = messages != null ? messages.get("hints.enter-area", currentArea.name()) : null;
            if (msg != null) player.sendMessage(msg);
            enterNotified.add(uuid);
        }

        // 累计时间
        state.seconds++;
        if (!recoveryChecked.contains(uuid)) state.regionSecondsBeforeRecovery++;
        updateTotalTime(uuid, 1);
        updateDailySeconds(uuid, 1);

        // 检查奖励
        int roundSeconds = currentArea.reward().roundMinutes() * 60;
        if (state.seconds - state.lastRewardSeconds >= roundSeconds && grantReward(player, currentArea)) {
            state.lastRewardSeconds += roundSeconds;
        }
    }

    private AfkArea findArea(String world, int x, int z) {
        for (AfkArea area : config.areas().values()) {
            if (area.enabled() && area.contains(world, x, z)) {
                return area;
            }
        }
        return null;
    }

    private boolean grantReward(Player player, AfkArea area) {
        UUID uuid = player.getUniqueId();
        if (regionRewardBatches.containsKey(uuid)) return false;
        PlayerStats stats = statsCache.get(uuid);
        if (stats == null) return false;
        if (!passesAntiAbuse(player, afkStates.get(uuid), area, false)) return true;
        String today = LocalDate.now().format(DATE_FMT);

        // 日期切换
        if (!today.equals(stats.todayDate())) {
            stats = new PlayerStats(stats.playerName(), today, 0, stats.totalCount(), stats.totalSeconds());
            updateStatsCache(uuid, stats);
        }

        // 每日上限
        if (area.reward().max().enabled() && stats.todayCount() >= area.reward().max().limit()) {
            if (!player.hasPermission("axs.afkreward.not.reward.limit")) {
                player.sendMessage(messages != null ? messages.get("hints.reward-limit") : "§c你今日已达到该区域最大奖励次数限制。");
                return true;
            }
        }

        // 找到奖励类型
        AfkRewardType rewardType = area.reward().type();

        // 确定玩家等级 (从高到低)
        var tierKeys = rewardTiers(rewardType);
        if (tierKeys.isEmpty()) return true;
        // 常见排序: vip3 > vip2 > vip1 > common，自定义顺序保持配置顺序
        String matchedTier = null;
        for (String tier : tierKeys) {
            if (player.hasPermission("axs.afkreward.start." + area.id() + "." + tier)
                || player.hasPermission("axs.afkreward.start." + tier)) {
                matchedTier = tier;
                break;
            }
        }
        // 若没有任何 tier 权限，默认取最低级（通常是最后一个，因为高权限排前面）
        if (matchedTier == null && !tierKeys.isEmpty()) {
            matchedTier = tierKeys.get(tierKeys.size() - 1);
        }

        List<String> commands = rewardType.tierCommands().get(matchedTier);
        List<AfkItemReward> itemRewards = rewardType.tierItemRewards().get(matchedTier);
        if ((commands == null || commands.isEmpty()) && (itemRewards == null || itemRewards.isEmpty())) return true;

        if (pendingRewards == null || regionRewardBatches.size() >= REGION_REWARD_BATCH_LIMIT) return false;
        String key = "region:" + uuid + ":" + UUID.randomUUID();
        var items = prepareItemRewards(player, itemRewards, key);
        if (items == null) return false;
        var rewards = new ArrayList<PendingReward>();
        if (commands != null) {
            for (String command : commands) {
                String prepared = replaceRewardPlaceholders(command, player, afkStates.get(uuid), area);
                rewards.add(PendingReward.command(uuid, "afkreward", List.of(prepared), key));
            }
        }
        rewards.addAll(items);
        regionRewardBatches.put(uuid, new RegionRewardBatch(key, List.copyOf(rewards)));
        // 更新统计
        stats = new PlayerStats(
            stats.playerName(), stats.todayDate(),
            stats.todayCount() + 1,
            stats.totalCount() + 1,
            stats.totalSeconds()
        );
        updateStatsCache(uuid, stats);
        if (!failedLoads.contains(uuid)) requestedStats.put(uuid, stats);
        PlayerAfkState rewardedState = afkStates.get(uuid);
        if (rewardedState != null) rewardedState.sessionRewards++;

        // 更新区域统计
        updateAreaTime(uuid, area.name(), 0);
        auditLogger.log(player, uuid, area.name(), "REGION",
            matchedTier, 1,
            computeMultiplier(player, afkStates.get(uuid), area));

        // 触发联动
        dispatchMail(player, rewardType.mailPresets());
        PlayerAfkState currentState = afkStates.get(uuid);
        dispatchSignal("afk_reward", player, Map.of(
            "area", area.name(), "mode", "REGION",
            "seconds", String.valueOf(currentState != null ? currentState.seconds : 0),
            "rewards", "1"
        ));
        dispatchSubtitle(player, config.manual().subtitleOnReward());
        publishRewardEvent(player, area.name());

        String msg = messages != null ? messages.get("hints.reward", area.name(), String.valueOf(area.reward().roundMinutes())) : null;
        if (msg != null) player.sendMessage(msg);

        if (config.debug()) {
            logger.info("" + player.getName() + " 在 " + area.name() + " 获得奖励 (tier=" + matchedTier + ")");
        }
        return true;
    }

    private void publishRewardEvent(Player player, String areaName) {
        if (eventBusProvider == null) return;
        EventBusCapability eventBus = eventBusProvider.get();
        if (eventBus == null) return;
        Map<String, String> payload = new HashMap<>();
        payload.put("area", areaName);
        payload.put("mode", "REGION");
        eventBus.publish("axs.afkreward.reward_claimed", player, payload);
    }

    public void markViewChange(UUID uuid) {
        PlayerAfkState state = afkStates.get(uuid);
        if (state == null || state.mode != AfkMode.REGION || state.areaName == null) return;
        Deque<Long> changes = viewChanges.computeIfAbsent(uuid, key -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        long cutoff = now - config.antiAbuse().botDetection().windowSeconds() * 1000L;
        synchronized (changes) {
            changes.addLast(now);
            while (!changes.isEmpty() && changes.peekFirst() < cutoff) changes.removeFirst();
        }
    }

    private boolean passesAntiAbuse(Player player, PlayerAfkState state, AfkArea area, boolean manual) {
        if (state == null || area == null) return true;
        UUID uuid = player.getUniqueId();
        AfkRewardConfiguration.AntiAbuseConfig anti = config.antiAbuse();
        AfkRewardConfiguration.TimeLimitConfig limit = anti.timeLimit();
        boolean exemptLimit = !limit.enable() || player.hasPermission(limit.exemptPermission());
        boolean sessionExceeded = limit.sessionSeconds() > 0 && state.seconds >= limit.sessionSeconds();
        boolean dailyExceeded = limit.dailySeconds() > 0 && dailySeconds.getOrDefault(uuid, 0) >= limit.dailySeconds();
        if (!exemptLimit && (sessionExceeded || dailyExceeded)) {
            String key = sessionExceeded ? "hints.anti-abuse.time-limit-session"
                : "hints.anti-abuse.time-limit-daily";
            if ("KICK".equalsIgnoreCase(limit.onExceed())) {
                if (!manual) {
                    afkStates.remove(uuid);
                    enterNotified.remove(uuid);
                    hideHud(player);
                }
                sendAntiAbuseHint(player, key);
                if (manual) {
                    pendingManualEndReasons.put(uuid, EndReason.TIME_LIMIT);
                }
                return false;
            }
            if ("STOP".equalsIgnoreCase(limit.onExceed())) {
                sendAntiAbuseHint(player, key);
                return false;
            }
        }

        if (!manual && anti.botDetection().enable()
            && !player.hasPermission(anti.botDetection().exemptPermission())) {
            Deque<Long> changes = viewChanges.get(uuid);
            int count = 0;
            long cutoff = System.currentTimeMillis() - anti.botDetection().windowSeconds() * 1000L;
            if (changes != null) {
                synchronized (changes) {
                    while (!changes.isEmpty() && changes.peekFirst() < cutoff) changes.removeFirst();
                    count = changes.size();
                }
            }
            if (count < anti.botDetection().minViewChanges()) {
                if ("KICK".equalsIgnoreCase(anti.botDetection().action())) {
                    afkStates.remove(uuid);
                    enterNotified.remove(uuid);
                    hideHud(player);
                }
                sendAntiAbuseHint(player, "hints.anti-abuse.bot-suspected");
                return false;
            }
        }

        if (anti.ipLimit().enable() && !player.hasPermission(anti.ipLimit().exemptPermission())
            && isIpLimited(player, area.name())) {
            sendAntiAbuseHint(player, "hints.anti-abuse.ip-limit");
            return false;
        }
        return true;
    }

    private void sendAntiAbuseHint(Player player, String key) {
        UUID uuid = player.getUniqueId();
        if (!antiAbuseNotified.add(uuid)) return;
        String msg = messages != null ? messages.get(key) : null;
        if (msg != null) player.sendMessage(msg);
    }

    private boolean isIpLimited(Player player, String areaName) {
        if (player.getAddress() == null || player.getAddress().getAddress() == null) return false;
        String ip = player.getAddress().getAddress().getHostAddress();
        UUID earliest = null;
        long earliestStart = Long.MAX_VALUE;
        for (Map.Entry<UUID, PlayerAfkState> entry : afkStates.entrySet()) {
            Player other = Bukkit.getPlayer(entry.getKey());
            PlayerAfkState otherState = entry.getValue();
            if (other == null || !other.isOnline() || otherState == null
                || !areaName.equals(otherState.areaName) || other.getAddress() == null
                || other.getAddress().getAddress() == null) continue;
            if (!ip.equals(other.getAddress().getAddress().getHostAddress())) continue;
            if (otherState.startTimeMillis < earliestStart) {
                earliestStart = otherState.startTimeMillis;
                earliest = entry.getKey();
            }
        }
        return earliest != null && !earliest.equals(player.getUniqueId());
    }

    public double computeMultiplier(Player player, PlayerAfkState state, AfkArea area) {
        AfkArea.MultiplierConfig multiplier = area.multiplier();
        double result = multiplier.enable() ? multiplier.base() : 1.0;
        if (multiplier.enable()) {
            List<Double> matches = new ArrayList<>();
            LocalTime now = LocalTime.now();
            DayOfWeek day = java.time.LocalDate.now().getDayOfWeek();
            if ((day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
                && multiplier.weekend() != 1.0) matches.add(multiplier.weekend());
            for (AfkArea.ScheduleConfig schedule : multiplier.schedules()) {
                if (scheduleMatches(schedule, day, now)) matches.add(schedule.multiplier());
            }
            for (double value : matches) {
                result = switch (multiplier.combine().toUpperCase()) {
                    case "MULTIPLY" -> result * value;
                    case "LAST" -> value;
                    default -> Math.max(result, value);
                };
            }
        }
        result *= area.rewardWeight();
        if (config.antiAbuse().decay().enable() && state != null
            && state.seconds > config.antiAbuse().decay().startSeconds()) {
            double elapsedHours = (state.seconds - config.antiAbuse().decay().startSeconds()) / 3600.0;
            double decay = Math.max(config.antiAbuse().decay().minMultiplier(),
                1.0 - config.antiAbuse().decay().factorPerHour() * elapsedHours);
            result *= decay;
        }
        AfkRewardConfiguration.TimeLimitConfig limit = config.antiAbuse().timeLimit();
        if (limit.enable() && "DECAY".equalsIgnoreCase(limit.onExceed())
            && state != null && (limit.sessionSeconds() > 0 && state.seconds >= limit.sessionSeconds()
                || limit.dailySeconds() > 0 && dailySeconds.getOrDefault(player.getUniqueId(), 0) >= limit.dailySeconds())) {
            result = Math.min(result, config.antiAbuse().decay().minMultiplier());
        }
        return Math.max(0.0, result);
    }

    private boolean scheduleMatches(AfkArea.ScheduleConfig schedule,
                                    DayOfWeek day, LocalTime now) {
        String days = schedule.days();
        boolean dayMatches = "ALL".equalsIgnoreCase(days);
        if (!dayMatches) {
            for (String value : days.split(",")) {
                if (day.name().startsWith(value.trim().toUpperCase())) {
                    dayMatches = true;
                    break;
                }
            }
        }
        if (!dayMatches) return false;
        try {
            LocalTime start = LocalTime.parse(schedule.start());
            LocalTime end = LocalTime.parse(schedule.end());
            return end.isBefore(start) ? !now.isBefore(start) || !now.isAfter(end)
                : !now.isBefore(start) && !now.isAfter(end);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String replaceRewardPlaceholders(String command, Player player,
                                             PlayerAfkState state, AfkArea area) {
        String value = String.format(java.util.Locale.ROOT, "%.2f",
            computeMultiplier(player, state, area));
        // 验证玩家名防止命令注入
        String safeName = xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
        return command.replace("{player}", safeName).replace("%player_name%", safeName)
            .replace("{multiplier}", value)
            .replace("%axsafk_multiplier%", value)
            .replace("%axsafkreward_multiplier%", value);
    }

    private List<PendingReward> prepareItemRewards(Player player, List<AfkItemReward> rewards, String detail) {
        if (rewards == null || rewards.isEmpty()) return List.of();
        if (itemSourceRegistry == null || pendingRewards == null) return null;
        try {
            var prepared = new ArrayList<PendingReward>(rewards.size());
            UUID recipient = player.getUniqueId();
            for (AfkItemReward reward : rewards) {
                var item = itemSourceRegistry.generateItem(reward.source(), reward.id(), reward.amount(), reward.nbt(), player);
                if (xuanmo.arcartxsuite.api.inventory.InventoryItems.empty(item)) {
                    logger.warning("挂机奖励物品生成失败: " + reward.source() + ":" + reward.id());
                    return null;
                }
                String data = java.util.Base64.getEncoder().encodeToString(ItemSerializer.serialize(item));
                prepared.add(PendingReward.itemSerialized(recipient, "afkreward", data, detail));
            }
            return List.copyOf(prepared);
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("挂机奖励物品快照未确认: " + detail + " " + failure.getMessage());
            return null;
        }
    }

    private static List<String> rewardTiers(AfkRewardType rewardType) {
        var tiers = new java.util.LinkedHashSet<>(rewardType.tierCommands().keySet());
        tiers.addAll(rewardType.tierItemRewards().keySet());
        return List.copyOf(tiers);
    }

    private static final class RegionRewardBatch {
        final String key;
        final List<PendingReward> rewards;
        boolean inFlight;
        long retryAt;

        RegionRewardBatch(String key, List<PendingReward> rewards) {
            this.key = key;
            this.rewards = rewards;
        }
    }

    private void pumpRegionRewards() {
        long now = System.nanoTime();
        int remaining = REGION_REWARD_WRITES_PER_TICK;
        for (var entry : regionRewardBatches.entrySet()) {
            var batch = entry.getValue();
            if (batch.inFlight || batch.retryAt != 0 && batch.retryAt - now > 0) continue;
            UUID recipient = entry.getKey();
            batch.inFlight = submitRewardBatch(batch.key, batch.rewards, accepted -> {
                if (accepted) {
                    regionRewardBatches.remove(recipient, batch);
                } else {
                    batch.inFlight = false;
                    batch.retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                }
            });
            if (!batch.inFlight || --remaining == 0) break;
        }
    }

    private boolean submitRewardBatch(String key, List<PendingReward> rewards, java.util.function.Consumer<Boolean> completed) {
        var scheduler = Bukkit.getScheduler();
        try {
            writes.execute(() -> {
                boolean accepted = persistRewardBatch(key, rewards);
                if (active) scheduler.runTask(plugin, () -> callbacks.call(() -> {
                    if (!active) return null;
                    completed.accept(accepted);
                    if (accepted) {
                        Player recipient = Bukkit.getPlayer(rewards.get(0).playerUuid());
                        if (recipient != null && recipient.isOnline()) pendingRewards.flushPlayer(recipient);
                    }
                    return null;
                }, null));
            });
            return true;
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            return false;
        }
    }

    private boolean persistRewardBatch(String key, List<PendingReward> rewards) {
        try {
            return pendingRewards.savePendingBatchConfirmed(key, rewards);
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("挂机奖励交接待核对: " + key + " " + failure.getMessage());
            return false;
        }
    }

    private int countPlayersInArea(String areaName) {
        int count = 0;
        for (Map.Entry<UUID, PlayerAfkState> entry : afkStates.entrySet()) {
            if (areaName.equals(entry.getValue().areaName)) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) count++;
            }
        }
        return count;
    }

    private void updateTotalTime(UUID uuid, int deltaSeconds) {
        PlayerStats stats = statsCache.get(uuid);
        if (stats == null) {
            playerStatsLoads.computeIfAbsent(uuid, ignored -> new PendingPlayerStats("")).seconds += deltaSeconds;
        } else {
            updateStatsCache(uuid, new PlayerStats(stats.playerName(), stats.todayDate(),
                stats.todayCount(), stats.totalCount(), stats.totalSeconds() + deltaSeconds));
        }

        // 同步更新区域统计
        PlayerAfkState state = afkStates.get(uuid);
        if (state != null && state.areaName != null) {
            updateAreaTime(uuid, state.areaName, deltaSeconds);
        }
    }

    private void updateDailySeconds(UUID uuid, int deltaSeconds) {
        String today = LocalDate.now().format(DATE_FMT);
        if (!today.equals(dailySecondsDate)) {
            dailySeconds.clear();
            dailySecondsDate = today;
        }
        dailySeconds.merge(uuid, deltaSeconds, Integer::sum);
    }

    private void preloadStats(UUID uuid, String playerName) {
        if (!statsCache.containsKey(uuid)) {
            var pending = playerStatsLoads.computeIfAbsent(uuid, ignored -> new PendingPlayerStats(playerName));
            PlayerStats awaitingWrite = requestedStats.get(uuid);
            if (awaitingWrite != null) completePlayerStats(uuid, pending, awaitingWrite);
        }
        PlayerAreaStats areas = areaStatsCache.computeIfAbsent(uuid, ignored -> new PlayerAreaStats());
        if (areas.requested && areas.rows == null) areaStatsLoads.putIfAbsent(uuid, areas);
    }

    private PlayerStats completePlayerStats(UUID uuid, PendingPlayerStats pending, PlayerStats loaded) {
        if (!playerStatsLoads.remove(uuid, pending)) return statsCache.get(uuid);
        loaded = requestedStats.getOrDefault(uuid, loaded);
        String name = loaded.playerName().isEmpty() ? pending.playerName : loaded.playerName();
        PlayerStats merged = new PlayerStats(name, loaded.todayDate(), loaded.todayCount(), loaded.totalCount(),
            loaded.totalSeconds() + pending.seconds);
        statsCache.put(uuid, merged);
        if (pending.seconds != 0) dirtyPlayerStats.add(uuid);
        failedLoads.remove(uuid);
        return merged;
    }

    private void retryPlayerStats(UUID uuid, PendingPlayerStats pending, Exception failure) {
        if (!playerStatsLoads.remove(uuid, pending)) return;
        pending.defer(statsTick);
        playerStatsLoads.put(uuid, pending);
        if (failedLoads.add(uuid)) {
            logger.warning("加载玩家统计失败，将在后台重试: " + uuid + " " + failure.getMessage());
        }
    }

    private void scheduleStatsReads() {
        if (statsReadInProgress || (playerStatsLoads.isEmpty() && areaStatsLoads.isEmpty() && sessionReads.isEmpty())) return;
        // Keep reconnect reads behind this player's pending logout checkpoint.
        Set<UUID> savingQuit = inFlightQuitCheckpoints.isEmpty() ? Set.of()
            : inFlightQuitCheckpoints.stream().map(QuitCheckpoint::uuid).collect(Collectors.toSet());
        Predicate<UUID> quitPending = uuid -> quitCheckpoints.containsKey(uuid) || savingQuit.contains(uuid);
        List<Map.Entry<UUID, PendingSessionRead>> sessions = sessionReads.entrySet().stream()
            .filter(entry -> !quitPending.test(entry.getKey()))
            .limit(playerStatsLoads.isEmpty() && areaStatsLoads.isEmpty() ? STATS_READS_PER_BATCH : STATS_READS_PER_BATCH / 4)
            .map(Map.Entry::copyOf)
            .toList();
        int budget = STATS_READS_PER_BATCH - sessions.size();
        List<Map.Entry<UUID, PendingPlayerStats>> players = playerStatsLoads.entrySet().stream()
            .filter(entry -> entry.getValue().retryAt <= statsTick && !quitPending.test(entry.getKey()))
            .limit(areaStatsLoads.isEmpty() ? budget : budget / 2)
            .map(Map.Entry::copyOf)
            .toList();
        List<Map.Entry<UUID, PlayerAreaStats>> areas = areaStatsLoads.entrySet().stream()
            .filter(entry -> entry.getValue().retryAt <= statsTick && !quitPending.test(entry.getKey()))
            .limit(budget - players.size())
            .map(Map.Entry::copyOf)
            .toList();
        if (players.isEmpty() && areas.isEmpty() && sessions.isEmpty()) return;

        statsReadInProgress = true;
        readAsync(() -> readStatsBatch(players, areas, sessions));
    }

    private void readStatsBatch(List<Map.Entry<UUID, PendingPlayerStats>> players,
                                List<Map.Entry<UUID, PlayerAreaStats>> areas,
                                List<Map.Entry<UUID, PendingSessionRead>> sessions) {
        List<Runnable> completions = new ArrayList<>(players.size() + areas.size() + sessions.size());
        for (var entry : players) {
            if (!active) return;
            UUID uuid = entry.getKey();
            PendingPlayerStats pending = entry.getValue();
            try {
                PlayerStats loaded = Objects.requireNonNull(repository.loadStats(uuid));
                completions.add(() -> completePlayerStats(uuid, pending, loaded));
            } catch (Exception failure) {
                completions.add(() -> retryPlayerStats(uuid, pending, failure));
            }
        }
        for (var entry : areas) {
            if (!active) return;
            UUID uuid = entry.getKey();
            PlayerAreaStats pending = entry.getValue();
            try {
                List<AreaStats> loaded = repository.loadAllAreaStats(uuid);
                completions.add(() -> completeAreaStats(uuid, pending, loaded));
            } catch (Exception failure) {
                completions.add(() -> retryAreaStats(uuid, pending, failure));
            }
        }
        for (var entry : sessions) {
            if (!active) return;
            UUID uuid = entry.getKey();
            PendingSessionRead pending = entry.getValue();
            SessionRecord settlement = pending.loaded;
            try {
                if (settlement != null) {
                    boolean claimed = repository.beginManualSettlement(settlement, pending.claim);
                    completions.add(() -> completeSessionSettlement(uuid, pending, claimed));
                } else {
                    if (pending.checkpoint != null) repository.refreshManualCheckpoint(pending.checkpoint);
                    SessionRecord session = repository.loadSession(uuid);
                    completions.add(() -> completeSessionRead(uuid, pending, session, null));
                }
            } catch (Exception failure) {
                completions.add(() -> completeSessionRead(uuid, pending, null, failure));
            }
        }
        if (!active) return;
        onMain(() -> {
            statsReadInProgress = false;
            if (active) completions.forEach(Runnable::run);
        });
    }

    private void updateStatsCache(UUID uuid, PlayerStats stats) {
        statsCache.put(uuid, stats);
        dirtyPlayerStats.add(uuid);
    }

    private void sendLeaveHint(Player player, String areaName) {
        String msg = messages != null ? messages.get("hints.leave-area", areaName) : null;
        if (msg != null) player.sendMessage(msg);
    }

    // ── MANUAL 模式 API ──

    public boolean startManualAfk(Player player, String areaName) {
        if (!Bukkit.isPrimaryThread() || !active || !player.isOnline()) return false;
        UUID uuid = player.getUniqueId();
        if (startingManual.contains(uuid) || endingManual.containsKey(uuid)) {
            player.sendMessage(messages != null ? messages.get("hints.manual.recovery-pending") : "§e正在后台核对上次挂机，请稍后再试。");
            return false;
        }
        PlayerStats stats = statsCache.get(uuid);
        if (pendingSessionWrites.containsKey(uuid)) recoveryChecked.remove(uuid);
        if (!recoveryChecked.contains(uuid) || stats == null) {
            preloadStats(uuid, player.getName());
            queueSessionRecovery(player);
            player.sendMessage(messages != null ? messages.get("hints.manual.recovery-pending") : "§e正在后台核对上次挂机，请稍后再试。");
            return false;
        }
        if (isInCombat(uuid)) {
            player.sendMessage(messages != null ? messages.get("hints.manual.in-combat") : "§c你刚刚参与过战斗，请稍后再开始挂机。");
            return false;
        }
        AfkArea area = config.areas().get(areaName);
        if (area == null || !area.enabled() || !area.hasTeleport() || !area.manualEnabled()) {
            player.sendMessage(messages != null ? messages.get("hints.manual.no-teleport", areaName) : "§c该区域不支持原地挂机。");
            return false;
        }
        if (!player.hasPermission("axs.afkreward.area." + areaName)) {
            player.sendMessage(messages != null ? messages.get("hints.no-permission-area") : "§c你没有权限在此区域挂机。");
            return false;
        }
        // 人数限制
        if (area.reward().player().enabled()) {
            int count = countPlayersInArea(areaName);
            if (count >= area.reward().player().limit() && !player.hasPermission("axs.afkreward.not.player.limit")) {
                player.sendMessage(messages != null ? messages.get("hints.player-limit") : "§c该区域已满员。");
                return false;
            }
        }
        PlayerAfkState existing = afkStates.get(uuid);
        if (existing != null && existing.areaName != null) {
            player.sendMessage(messages != null ? messages.get("hints.manual.already") : "§c你已经在挂机中。");
            return false;
        }

        // Essentials AFK 互斥检测
        EssentialsQueryable essentials = essentialsProvider != null ? essentialsProvider.get() : null;
        if (essentials != null && essentials.isAfk(uuid)) {
            player.sendMessage(messages != null ? messages.get("hints.manual.essentials-afk") : "§c你当前处于 AFK 状态，请先解除后再开始挂机。");
            return false;
        }

        // 保存原始状态
        PlayerAfkState state = new PlayerAfkState();
        state.mode = AfkMode.MANUAL;
        state.areaName = areaName;
        state.rewardType = area.id();
        state.seconds = 0;
        state.lastRewardSeconds = 0;
        state.originalWalkSpeed = config.manual().restrictActions() ? player.getWalkSpeed() : null;
        state.originalCollidable = isManualProtectionEnabled(ManualProtection.COLLIDABLE)
            ? player.isCollidable() : null;
        state.originalLocation = player.getLocation().clone();
        state.startTimeMillis = System.currentTimeMillis();
        if (!startingManual.add(uuid)) return false;
        var checkpoint = sessionCheckpoint(player, state, stats);
        if (submitManualWrite(() -> repository.startManualSession(checkpoint),
                failure -> completeManualStart(player, area, state, checkpoint, failure))) return true;
        startingManual.remove(uuid);
        manualStorageUnavailable(player);
        return false;
    }

    private void completeManualStart(Player player, AfkArea area, PlayerAfkState state,
                                     SessionRecord checkpoint, Exception failure) {
        UUID uuid = checkpoint.playerUuid();
        String areaName = state.areaName;
        try {
            if (failure != null) {
                logger.warning("无法保存挂机会话: " + uuid + " " + failure.getMessage());
                // Failure can mean an earlier session still owns the row, or that the insert reply was lost.
                recoveryChecked.remove(uuid);
                if (currentManualPlayer(player)) manualStorageUnavailable(player);
                return;
            }
            var existing = afkStates.get(uuid);
            var limit = area.reward().player();
            EssentialsQueryable essentials = essentialsProvider != null ? essentialsProvider.get() : null;
            if (!currentManualPlayer(player) || isInCombat(uuid)
                    || !player.hasPermission("axs.afkreward.area." + areaName)
                    || (existing != null && existing.areaName != null)
                    || (limit.enabled() && countPlayersInArea(areaName) >= limit.limit()
                        && !player.hasPermission("axs.afkreward.not.player.limit"))
                    || (essentials != null && essentials.isAfk(uuid))
                    || (state.originalWalkSpeed != null && Float.compare(state.originalWalkSpeed, player.getWalkSpeed()) != 0)
                    || (state.originalCollidable != null && state.originalCollidable != player.isCollidable())) {
                discardUnstartedSession(player, checkpoint);
                return;
            }
            state.originalLocation = player.getLocation().clone();
            if (!player.teleport(area.teleport()) || !currentManualPlayer(player)) {
                player.sendMessage(messages != null ? messages.get("hints.manual.teleport-failed") : "§c无法传送到挂机区域。");
                discardUnstartedSession(player, checkpoint);
                return;
            }
            if (isManualProtectionEnabled(ManualProtection.VEHICLE_ENTER)) player.leaveVehicle();
            afkStates.put(uuid, state);
        } finally {
            startingManual.remove(uuid);
        }
        if (state.originalCollidable != null) {
            player.setCollidable(false);
        }

        // 行为封锁
        if (state.originalWalkSpeed != null) {
            player.setWalkSpeed(MANUAL_WALK_SPEED);
        }

        // 信号触发
        dispatchSignal("afk_start", player, Map.of("area", areaName, "mode", "MANUAL"));

        String msg = messages != null ? messages.get("hints.manual.start", areaName) : null;
        if (msg != null) player.sendMessage(msg);
        if (config.debug()) {
            logger.info("" + player.getName() + " 开始原地挂机: " + areaName);
        }
    }

    private void discardUnstartedSession(Player player, SessionRecord checkpoint) {
        UUID uuid = checkpoint.playerUuid();
        recoveryChecked.remove(uuid);
        nextRecoveryAttempt.remove(uuid);
        submitManualWrite(() -> repository.removeUnstartedManualSession(checkpoint), failure -> {
            if (failure != null) logger.warning("清理未启动挂机会话失败: " + uuid + " " + failure.getMessage());
        });
        if (currentManualPlayer(player)) manualStorageUnavailable(player);
    }

    private boolean currentManualPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        return Bukkit.getPlayer(uuid) == player && player.isOnline()
            && xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(uuid);
    }

    private boolean canDispatchReward(Player player, AuthSnapshot admission) {
        if (!active || !player.isOnline() || Bukkit.getPlayer(admission.playerId()) != player) return false;
        AuthSnapshot current = AuthAccess.snapshot(admission.playerId());
        return admission.admitted() && current.admitted() && admission.generation() == current.generation()
            && Objects.equals(admission.connectionId(), current.connectionId());
    }

    private void manualStorageUnavailable(Player player) {
        player.sendMessage(messages != null ? messages.get("hints.manual.settlement-unavailable") : "§c挂机结算暂不可用。");
    }

    @FunctionalInterface
    private interface ManualWrite { void run() throws SQLException; }

    private boolean submitManualWrite(ManualWrite work, java.util.function.Consumer<Exception> completed) {
        if (!queuedManualWrite.compareAndSet(false, true)) return false;
        var scheduler = Bukkit.getScheduler();
        try {
            writes.execute(() -> {
                queuedManualWrite.set(false);
                Exception error = null;
                try { work.run(); }
                catch (SQLException | RuntimeException failure) { error = failure; }
                var outcome = error;
                if (active) scheduler.runTask(plugin, () -> callbacks.call(() -> {
                    if (active) completed.accept(outcome);
                    return null;
                }, null));
            });
            return true;
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            queuedManualWrite.set(false);
            return false;
        }
    }

    public boolean endManualAfk(Player player, boolean silent) {
        return endManualAfk(player, silent, EndReason.MANUAL);
    }

    public boolean endManualAfk(Player player, boolean silent, EndReason reason) {
        if (!Bukkit.isPrimaryThread() || !active) return false;
        UUID uuid = player.getUniqueId();
        AuthSnapshot admission = AuthAccess.snapshot(uuid);
        if (!canDispatchReward(player, admission)) return false;
        EndReason pendingReason = pendingManualEndReasons.remove(uuid);
        if (pendingReason != null && reason == EndReason.MANUAL) {
            reason = pendingReason;
        }
        PlayerAfkState state = afkStates.get(uuid);
        if (state == null || state.mode != AfkMode.MANUAL || state.areaName == null) {
            if (!silent) player.sendMessage(messages != null ? messages.get("hints.manual.not-started") : "§c你未在挂机中。");
            return false;
        }

        // 最新在线秒数先落库，再认领结算。数据库离线期间保留尚未保存的检查点。
        PlayerStats checkpointStats = statsCache.get(uuid);
        if (checkpointStats == null) {
            player.sendMessage(messages != null ? messages.get("hints.manual.settlement-unavailable") : "§c挂机结算暂不可用。");
            return false;
        }
        SessionRecord checkpoint = sessionCheckpoint(player, state, checkpointStats);
        afkStates.remove(uuid, state);
        restoreManualState(player, state);
        try {
            saveRestoredMovementBeforeSettlement(player, checkpoint);
        } catch (SQLException failure) {
            failManualEnd(player, admission, state, checkpoint, failure);
            return false;
        }
        EndReason endReason = reason;
        UUID claim = UUID.randomUUID();
        var startedSettlement = new AtomicBoolean();
        endingManual.put(uuid, new SettlementReceipt(checkpoint, false, 0, claim));
        if (submitManualWrite(() -> {
                if (!repository.refreshManualCheckpoint(checkpoint)) {
                    startedSettlement.set(true);
                    throw new SQLException("Session checkpoint no longer current");
                }
                startedSettlement.set(true);
                if (!repository.beginManualSettlement(checkpoint, claim)) throw new SQLException("Session checkpoint no longer current");
            }, failure -> {
                if (failure != null && !startedSettlement.get()) {
                    endingManual.remove(uuid);
                    failManualEnd(player, admission, state, checkpoint, failure);
                } else {
                    completeManualEnd(player, admission, state, checkpoint, claim, silent, endReason, failure);
                }
            })) return true;
        endingManual.remove(uuid);
        failManualEnd(player, admission, state, checkpoint, new SQLException("Manual settlement queue is busy"));
        return false;
    }

    private void failManualEnd(Player player, AuthSnapshot admission, PlayerAfkState state,
                               SessionRecord checkpoint, Exception failure) {
        UUID uuid = checkpoint.playerUuid();
        pendingSessionWrites.put(uuid, checkpoint);
        recoveryChecked.remove(uuid);
        nextRecoveryAttempt.put(uuid, System.currentTimeMillis() + 30_000L);
        logger.warning("无法结束已保存的挂机会话: " + uuid + " " + failure.getMessage());
        if (canDispatchReward(player, admission)) {
            returnFromManualArea(player, state);
            hideHud(player);
            manualStorageUnavailable(player);
        }
    }

    private void completeManualEnd(Player player, AuthSnapshot admission, PlayerAfkState state, SessionRecord checkpoint, UUID claim,
                                   boolean silent, EndReason reason, Exception failure) {
        UUID uuid = checkpoint.playerUuid();
        if (failure != null) {
            // The claim may have committed, but this callback has not begun any reward effects.
            deferUnstartedSettlement(checkpoint, claim);
            nextRecoveryAttempt.put(uuid, System.currentTimeMillis() + 30_000L);
            logger.warning("挂机结算预约待核对: " + uuid + " " + failure.getMessage());
            if (canDispatchReward(player, admission)) {
                returnFromManualArea(player, state);
                hideHud(player);
                manualStorageUnavailable(player);
            }
            return;
        }
        if (!canDispatchReward(player, admission)) {
            deferUnstartedSettlement(checkpoint, claim);
            return;
        }
        endingManual.remove(uuid);
        ManualSettlement settlement = new ManualSettlement(checkpoint, player, admission);
        AfkArea area = config.areas().get(state.areaName);
        int times = settleManualRewards(player, state, area, settlement);
        returnFromManualArea(player, state);

        // 广播
        if (!silent && config.manual().broadcastRewards() && times > 0) {
            String broadcast = messages != null ? messages.get("hints.manual.broadcast",
                player.getName(), formatTime(state.seconds), String.valueOf(times)) : null;
            if (broadcast != null) Bukkit.broadcastMessage(broadcast);
        }

        // 保存区域统计
        queueQuitCheckpoint(uuid, statsCache.get(uuid), null);

        // 触发结束联动
        dispatchSignal("afk_end", player, Map.of(
            "area", state.areaName, "mode", "MANUAL",
            "seconds", String.valueOf(state.seconds),
            "rewards", String.valueOf(times)
        ));
        dispatchSubtitle(player, config.manual().subtitleOnEnd());
        settlement.successful &= xuanmo.arcartxsuite.api.capability.mail.MailPresetHelper.dispatchPresets(
            mailProvider, player.getName(), config.manual().endMailPresets(), "afkreward", settlement.reference(), logger);
        settlement.finishPreparation();

        enterNotified.remove(uuid);
        pendingManualEndReasons.remove(uuid);
        hideHud(player);

        if (!silent) {
            String msg = messages != null ? messages.get(times > 0 ? "hints.manual.end" : "hints.manual.end-no-reward",
                formatTime(state.seconds), String.valueOf(times)) : null;
            if (msg != null) player.sendMessage(msg);
        }
        if (reason != EndReason.MANUAL) {
            String msg = messages != null ? messages.get("hints.manual.end-reason."
                + reason.name().toLowerCase(java.util.Locale.ROOT)) : null;
            if (msg != null) player.sendMessage(msg);
        }
        if (config.debug()) {
            logger.info("" + player.getName() + " 结束原地挂机: " + state.areaName + " 奖励次数=" + times);
        }
        requestLeaderboardRefresh();
    }

    private int computeRewardTimes(AfkArea area, int totalSeconds) {
        int roundSec = area != null ? area.reward().roundMinutes() * 60 : 15 * 60;
        return totalSeconds / roundSec;
    }

    private int settleManualRewards(Player player, PlayerAfkState state, AfkArea area, ManualSettlement settlement) {
        if (area == null) return 0;
        try {
            return grantManualRewards(player, state, area, computeRewardTimes(area, state.seconds), settlement);
        } catch (RuntimeException failure) {
            settlement.successful = false;
            logger.warning("挂机奖励发放未完成: " + settlement.reference() + " " + failure.getMessage());
            return settlement.roundsProcessed;
        }
    }

    private int grantManualRewards(Player player, PlayerAfkState state, AfkArea area, int times,
                                   ManualSettlement settlement) {
        if (times <= 0) return 0;
        AfkRewardType rewardType = area.reward().type();
        var tierKeys = rewardTiers(rewardType);
        if (tierKeys.isEmpty()) return 0;
        String matchedTier = null;
        for (String tier : tierKeys) {
            if (player.hasPermission("axs.afkreward.start." + area.id() + "." + tier)
                || player.hasPermission("axs.afkreward.start." + tier)) {
                matchedTier = tier;
                break;
            }
        }
        if (matchedTier == null && !tierKeys.isEmpty()) {
            matchedTier = tierKeys.get(tierKeys.size() - 1);
        }
        List<String> commands = rewardType.tierCommands().get(matchedTier);
        List<AfkItemReward> itemRewards = rewardType.tierItemRewards().get(matchedTier);
        if ((commands == null || commands.isEmpty()) && (itemRewards == null || itemRewards.isEmpty())) return 0;

        UUID uuid = player.getUniqueId();
        String today = LocalDate.now().format(DATE_FMT);
        PlayerStats stats = statsCache.get(uuid);
        if (stats == null) return 0;
        if (!today.equals(stats.todayDate())) {
            stats = new PlayerStats(stats.playerName(), today, 0, stats.totalCount(), stats.totalSeconds());
        }

        for (int i = 0; i < times; i++) {
            if (!canDispatchReward(player, settlement.admission) || !passesAntiAbuse(player, state, area, true)) break;
            // 每日上限检查
            if (area.reward().max().enabled() && stats.todayCount() >= area.reward().max().limit()) {
                if (!player.hasPermission("axs.afkreward.not.reward.limit")) break;
            }
            settlement.roundsProcessed++;
            if (state != null) state.sessionRewards++;
            stats = new PlayerStats(stats.playerName(), stats.todayDate(),
                stats.todayCount() + 1, stats.totalCount() + 1, stats.totalSeconds());
            updateStatsCache(uuid, stats);
            // Keep the counted round even if a command disconnects the player before the reward receipt arrives.
            requestedStats.put(uuid, stats);
            for (String cmd : commands != null ? commands : List.<String>of()) {
                String parsed = replaceRewardPlaceholders(cmd, player, state, area);
                settlement.dispatchCommand(parsed);
            }
            var items = prepareItemRewards(player, itemRewards, settlement.reference());
            if (items == null) settlement.successful = false;
            else settlement.items.addAll(items);
        }
        int actualTimes = settlement.roundsProcessed;
        if (actualTimes > 0) {
            auditLogger.log(player, uuid, area.name(), "MANUAL",
                matchedTier, actualTimes,
                computeMultiplier(player, state, area));
        }
        String msg = messages != null ? messages.get("hints.reward", area.name(), String.valueOf(area.reward().roundMinutes())) : null;
        if (msg != null && actualTimes > 0) player.sendMessage(msg);
        return actualTimes;
    }

    private final class ManualSettlement {
        private final SessionRecord checkpoint;
        private final Player player;
        private final AuthSnapshot admission;
        private boolean prepared;
        private boolean successful = true;
        private boolean needsPlayerSave;
        private final List<PendingReward> items = new ArrayList<>();
        private boolean itemWritePending;
        private int roundsProcessed;
        private int pendingCommands;

        private ManualSettlement(SessionRecord checkpoint, Player player, AuthSnapshot admission) {
            this.checkpoint = checkpoint;
            this.player = player;
            this.admission = admission;
        }

        private String reference() {
            // Mail prepends the source module before storing this reference in sender_name VARCHAR(64).
            return "manual:" + checkpoint.playerUuid().toString().replace("-", "") + ":"
                + Long.toUnsignedString(checkpoint.startTime(), 36);
        }

        private void dispatchCommand(String command) {
            pendingCommands++;
            ThrottledCommandExecutor.dispatchWithResult(command, () -> canDispatchReward(player, admission), result -> {
                needsPlayerSave |= result != ThrottledCommandExecutor.Result.NOT_EXECUTED;
                successful &= result == ThrottledCommandExecutor.Result.SUCCESS;
                pendingCommands--;
                finishIfReady();
            });
        }

        private void finishPreparation() {
            prepared = true;
            if (!items.isEmpty()) {
                itemWritePending = submitRewardBatch(reference() + ":items", List.copyOf(items), accepted -> {
                    successful &= accepted;
                    itemWritePending = false;
                    finishIfReady();
                });
                successful &= itemWritePending;
            }
            finishIfReady();
        }

        private void finishIfReady() {
            if (!prepared || itemWritePending || pendingCommands != 0 || !active) return;
            prepared = false;
            try {
                UUID uuid = checkpoint.playerUuid();
                if (needsPlayerSave) {
                    if (player.isOnline() && Bukkit.getPlayer(uuid) == player) player.saveData();
                    else successful = false;
                }
                settlementReceipts.add(new SettlementReceipt(checkpoint, successful, roundsProcessed, null));
                if (!successful) {
                    logger.warning("挂机结算需要核对: " + reference() + " area=" + checkpoint.areaName()
                        + " seconds=" + checkpoint.startSeconds() + " rounds-processed=" + roundsProcessed);
                }
            } catch (RuntimeException | LinkageError failure) {
                settlementReceipts.add(new SettlementReceipt(checkpoint, false, roundsProcessed, null));
                logger.warning("挂机结算回执未完成: " + reference() + " " + failure.getMessage());
            }
        }
    }

    public void markCombat(UUID uuid) {
        lastCombat.put(uuid, System.currentTimeMillis());
    }

    public boolean isInCombat(UUID uuid) {
        Long timestamp = lastCombat.get(uuid);
        if (timestamp == null) return false;
        long cooldownMillis = config.manual().combatCooldownSeconds() * 1000L;
        if (cooldownMillis <= 0 || System.currentTimeMillis() - timestamp >= cooldownMillis) {
            lastCombat.remove(uuid, timestamp);
            return false;
        }
        return true;
    }

    public boolean isInManualAfk(UUID uuid) {
        PlayerAfkState state = afkStates.get(uuid);
        return state != null && state.mode == AfkMode.MANUAL && state.areaName != null;
    }

    public boolean isManualProtectionEnabled(ManualProtection protection) {
        var protections = config.manual().protections();
        return switch (protection) {
            case MOVEMENT -> protections.movement();
            case TELEPORT -> protections.teleport();
            case INTERACT -> protections.interact();
            case BLOCK_BREAK -> protections.blockBreak();
            case INVENTORY -> protections.inventory();
            case RECEIVE_DAMAGE -> protections.receiveDamage();
            case DEAL_DAMAGE -> protections.dealDamage();
            case ENTITY_TARGET -> protections.entityTarget();
            case VEHICLE_ENTER -> protections.vehicleEnter();
            case INTERACT_ENTITY -> protections.interactEntity();
            case DROP_ITEM -> protections.dropItem();
            case SWAP_HAND -> protections.swapHand();
            case PICKUP_ITEM -> protections.pickupItem();
            case EXPERIENCE -> protections.experience();
            case COLLIDABLE -> protections.collidable();
        };
    }

    public List<OnlineAfkPlayer> getOnlineAfkPlayers() {
        List<OnlineAfkPlayer> list = new ArrayList<>();
        for (Map.Entry<UUID, PlayerAfkState> entry : afkStates.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;
            PlayerAfkState state = entry.getValue();
            if (state.areaName == null) continue;
            list.add(new OnlineAfkPlayer(p.getName(), state.areaName, state.mode.name(), state.seconds));
        }
        return list;
    }

    // ── 崩溃恢复 ──

    private void queueSessionRecovery(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (recoveryChecked.contains(uuid) || startingManual.contains(uuid) || endingManual.containsKey(uuid)
            || sessionReads.containsKey(uuid)
            || nextRecoveryAttempt.getOrDefault(uuid, 0L) > now) return;
        PendingSessionRead ready = readySessionReads.get(uuid);
        if (ready != null) {
            if (statsCache.containsKey(uuid)) {
                readySessionReads.remove(uuid);
                prepareSessionSettlement(uuid, ready);
            }
            return;
        }
        AuthSnapshot admission = AuthAccess.snapshot(uuid);
        if (canDispatchReward(player, admission)) {
            sessionReads.put(uuid, new PendingSessionRead(player, admission, pendingSessionWrites.get(uuid)));
        }
    }

    private void completeSessionRead(UUID uuid, PendingSessionRead pending, SessionRecord session, Exception failure) {
        boolean registered = sessionReads.remove(uuid, pending);
        if (pending.claim != null && failure != null) {
            deferUnstartedSettlement(pending.loaded, pending.claim);
            nextRecoveryAttempt.put(uuid, System.currentTimeMillis() + 30_000L);
            logger.warning("挂机恢复预约未确认: " + uuid + " " + failure.getMessage());
            return;
        }
        if (!registered || recoveryChecked.contains(uuid)) return;
        Player player = pending.player;
        if (!canDispatchReward(player, pending.admission)) return;
        if (failure != null) {
            nextRecoveryAttempt.put(uuid, System.currentTimeMillis() + 30_000L);
            logger.warning("读取挂机恢复会话失败: " + uuid + " " + failure.getMessage());
            return;
        }
        if (pending.checkpoint != null) pendingSessionWrites.remove(uuid, pending.checkpoint);
        if (session == null || !AfkMode.MANUAL.name().equals(session.mode())) {
            recoveryChecked.add(uuid);
            nextRecoveryAttempt.remove(uuid);
            return;
        }
        pending.loaded = session;
        try {
            if (statsCache.containsKey(uuid)) prepareSessionSettlement(uuid, pending);
            else {
                restoreSavedMovement(player, session);
                readySessionReads.put(uuid, pending);
            }
        } catch (RuntimeException | LinkageError restoreFailure) {
            nextRecoveryAttempt.put(uuid, System.currentTimeMillis() + 30_000L);
            logger.warning("挂机状态恢复失败: " + uuid + " " + restoreFailure.getMessage());
        }
    }

    private void prepareSessionSettlement(UUID uuid, PendingSessionRead pending) {
        if (!canDispatchReward(pending.player, pending.admission)) return;
        try {
            restoreSavedMovement(pending.player, pending.loaded);
            saveRestoredMovementBeforeSettlement(pending.player, pending.loaded);
            pending.claim = UUID.randomUUID();
            endingManual.put(uuid, new SettlementReceipt(pending.loaded, false, 0, pending.claim));
            sessionReads.put(uuid, pending);
        } catch (Exception | LinkageError error) {
            nextRecoveryAttempt.put(uuid, System.currentTimeMillis() + 30_000L);
            logger.warning("挂机恢复失败: " + uuid + " " + error.getMessage());
        }
    }

    private void completeSessionSettlement(UUID uuid, PendingSessionRead pending, boolean claimed) {
        boolean registered = sessionReads.remove(uuid, pending);
        Player player = pending.player;
        PlayerStats stats = statsCache.get(uuid);
        if (!registered || !claimed || stats == null || !canDispatchReward(player, pending.admission)) {
            if (claimed) deferUnstartedSettlement(pending.loaded, pending.claim);
            else {
                endingManual.remove(uuid);
                if (player.isOnline() && Bukkit.getPlayer(uuid) == player) {
                    nextRecoveryAttempt.put(uuid, System.currentTimeMillis() + 30_000L);
                }
            }
            return;
        }
        endingManual.remove(uuid);
        SessionRecord session = pending.loaded;
        recoveryChecked.add(uuid);
        nextRecoveryAttempt.remove(uuid);
        // 新登录后等待会话读取时累计的区域秒数不在旧检查点中。
        PlayerAfkState current = afkStates.get(uuid);
        int recoveredTotalSeconds = session.totalSeconds() + (current == null ? 0 : current.regionSecondsBeforeRecovery);
        if (recoveredTotalSeconds > stats.totalSeconds()) {
            updateStatsCache(uuid, new PlayerStats(stats.playerName(), stats.todayDate(),
                stats.todayCount(), stats.totalCount(), recoveredTotalSeconds));
        }
        AfkArea area = config.areas().get(session.areaName());
        int seconds = Math.max(0, session.startSeconds());
        PlayerAfkState recovered = new PlayerAfkState();
        recovered.mode = AfkMode.MANUAL;
        recovered.areaName = session.areaName();
        recovered.seconds = seconds;
        var settlement = new ManualSettlement(session, player, pending.admission);
        int times;
        try {
            times = settleManualRewards(player, recovered, area, settlement);
            settlement.finishPreparation();
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("挂机恢复结算待核对: " + uuid + " " + failure.getMessage());
            return;
        }
        pendingManualEndReasons.remove(uuid);
        if (messages != null) player.sendMessage(messages.get("hints.manual.recovered", formatTime(seconds), String.valueOf(times)));
    }

    private void deferUnstartedSettlement(SessionRecord checkpoint, UUID claim) {
        UUID uuid = checkpoint.playerUuid();
        recoveryChecked.remove(uuid);
        settlementReceipts.add(endingManual.computeIfAbsent(uuid,
            ignored -> new SettlementReceipt(checkpoint, false, 0, claim)));
    }

    private void restoreManualState(Player player, PlayerAfkState state) {
        if (state.originalWalkSpeed != null) player.setWalkSpeed(state.originalWalkSpeed);
        if (state.originalCollidable != null) player.setCollidable(state.originalCollidable);
    }

    private void returnFromManualArea(Player player, PlayerAfkState state) {
        if (config.manual().returnOnEnd() && state.originalLocation != null) {
            player.teleport(state.originalLocation);
        }
    }

    private void restoreSavedMovement(Player player, SessionRecord session) {
        if (session.originalWalkSpeed() != null && Float.compare(player.getWalkSpeed(), MANUAL_WALK_SPEED) == 0) {
            player.setWalkSpeed(session.originalWalkSpeed());
        }
        if (session.originalCollidable() != null && !player.isCollidable()) {
            player.setCollidable(session.originalCollidable());
        }
    }

    private void saveRestoredMovementBeforeSettlement(Player player, SessionRecord session) throws SQLException {
        if (session.originalWalkSpeed() == null) return;
        if (!player.isOnline()) throw new SQLException("Player unavailable to save restored movement");
        try {
            player.saveData();
        } catch (RuntimeException failure) {
            // Keep the authoritative checkpoint until Paper has saved the restored abilities.
            throw new SQLException("Unable to save restored movement", failure);
        }
    }

    private SessionRecord sessionCheckpoint(Player player, PlayerAfkState state, PlayerStats stats) {
        return new SessionRecord(player.getUniqueId(), player.getName(), state.areaName,
            state.rewardType != null ? state.rewardType : "", state.mode.name(), state.seconds,
            state.startTimeMillis, stats.todayCount(), stats.totalCount(), stats.todayDate(), stats.totalSeconds(),
            state.originalWalkSpeed, state.originalCollidable);
    }

    // ── 排行榜 ──

    private void requestLeaderboardRefresh() {
        if (!active || !leaderboardLoading.compareAndSet(false, true)) return;
        readAsync(this::refreshLeaderboard);
    }

    private void refreshLeaderboard() {
        try {
            if (!active) return;
            int limit = config.manual().leaderboardSize();
            List<PlayerStats> dbList = repository.loadLeaderboard(limit);
            // 合并在线玩家的内存数据：statsCache 中的 totalSeconds 可能比 DB 更新（updateTotalTime 不即时持久化）
            Map<String, PlayerStats> onlineByName = new HashMap<>();
            for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
                if (failedLoads.contains(entry.getKey())) continue;
                PlayerStats s = entry.getValue();
                if (s.playerName().isEmpty()) continue;
                onlineByName.put(s.playerName(), s);
            }
            if (!onlineByName.isEmpty()) {
                // 用内存数据覆盖 DB 中同名条目
                List<PlayerStats> merged = new ArrayList<>(dbList.size() + onlineByName.size());
                Set<String> seen = new HashSet<>();
                for (PlayerStats db : dbList) {
                    PlayerStats live = onlineByName.get(db.playerName());
                    merged.add(live != null ? live : db);
                    seen.add(db.playerName());
                }
                // 加入 DB 中不存在但在线的玩家
                for (PlayerStats live : onlineByName.values()) {
                    if (!seen.contains(live.playerName())) {
                        merged.add(live);
                    }
                }
                // 重新排序并截断
                merged.sort(Comparator.comparingInt(PlayerStats::totalSeconds).reversed());
                leaderboardCache = merged.size() > limit
                    ? List.copyOf(merged.subList(0, limit)) : List.copyOf(merged);
            } else {
                leaderboardCache = dbList;
            }
            nextLeaderboardRefresh = System.currentTimeMillis() + 60_000L;
        } catch (Exception e) {
            nextLeaderboardRefresh = System.currentTimeMillis() + 5_000L;
            logger.warning("刷新排行榜失败: " + e.getMessage());
        } finally {
            leaderboardLoading.set(false);
        }
    }

    public List<AfkRewardRepository.PlayerStats> getLeaderboard() {
        if (System.currentTimeMillis() >= nextLeaderboardRefresh) requestLeaderboardRefresh();
        return leaderboardCache;
    }

    private void saveOnlineStats(boolean periodic) {
        if (!active) return;
        finishStatsCheckpoint();
        if (pendingStatsCheckpoint != null || nextStatsWrite != 0 && System.nanoTime() - nextStatsWrite < 0) return;

        var requested = Map.copyOf(requestedStats);
        Map<UUID, PlayerStats> checkpoint = periodic ? statsWriteSnapshot() : new LinkedHashMap<>(requested);
        if (!periodic) checkpoint.replaceAll((uuid, saved) -> statsCache.getOrDefault(uuid, saved));
        var receipts = List.copyOf(settlementReceipts);
        List<SessionRecord> sessions = new ArrayList<>();
        if (periodic) {
            for (Map.Entry<UUID, PlayerAfkState> entry : afkStates.entrySet()) {
                if (entry.getValue().mode != AfkMode.MANUAL) continue;
                UUID uuid = entry.getKey();
                PlayerStats stats = statsCache.get(uuid);
                Player player = stats == null ? null : Bukkit.getPlayer(uuid);
                if (player != null) sessions.add(sessionCheckpoint(player, entry.getValue(), stats));
            }
        }
        if (checkpoint.isEmpty() && sessions.isEmpty() && receipts.isEmpty()) return;
        var done = new CompletableFuture<List<SettlementReceipt>>();
        writes.execute(() -> {
            try {
                repository.saveStatsBatch(checkpoint, sessions);
                done.complete(writeSettlementReceipts(receipts));
            } catch (SQLException | RuntimeException | LinkageError failure) {
                logger.warning("批量保存挂机检查点失败: " + failure.getMessage());
                done.completeExceptionally(failure);
            }
        });
        pendingStatsCheckpoint = new StatsCheckpoint(checkpoint, requested, receipts, done);
    }

    private Map<UUID, PlayerStats> statsWriteSnapshot() {
        var checkpoint = dirtyStatsSnapshot();
        requestedStats.forEach(checkpoint::putIfAbsent);
        return checkpoint;
    }

    private Map<UUID, PlayerStats> dirtyStatsSnapshot() {
        Map<UUID, PlayerStats> checkpoint = new LinkedHashMap<>();
        for (UUID uuid : dirtyPlayerStats) {
            PlayerStats stats = statsCache.get(uuid);
            if (stats != null && !failedLoads.contains(uuid)) checkpoint.put(uuid, stats);
        }
        return checkpoint;
    }

    private void finishStatsCheckpoint() {
        StatsCheckpoint checkpoint = pendingStatsCheckpoint;
        if (checkpoint == null || !checkpoint.done().isDone()) return;
        if (checkpoint.done().isCompletedExceptionally()) {
            nextStatsWrite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        } else {
            var failed = checkpoint.done().join();
            nextStatsWrite = failed.isEmpty() ? 0 : System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            acknowledgeStatsCheckpoint(checkpoint.stats());
            checkpoint.requested().forEach((uuid, saved) -> {
                if (requestedStats.get(uuid) == saved) requestedStats.remove(uuid);
            });
            Set<SettlementReceipt> failedReceipts = failed.isEmpty() ? Set.of() : new HashSet<>(failed);
            for (var receipt : checkpoint.receipts()) {
                if (receipt.unstartedClaim() != null && !failedReceipts.contains(receipt)) {
                    endingManual.remove(receipt.checkpoint().playerUuid(), receipt);
                }
            }
            settlementReceipts.subList(0, checkpoint.receipts().size()).clear();
            settlementReceipts.addAll(0, failed);
        }
        pendingStatsCheckpoint = null;
    }

    private void acknowledgeStatsCheckpoint(Map<UUID, PlayerStats> checkpoint) {
        for (var entry : checkpoint.entrySet()) {
            if (statsCache.get(entry.getKey()) == entry.getValue()) dirtyPlayerStats.remove(entry.getKey());
        }
    }

    private List<SettlementReceipt> writeSettlementReceipts(List<SettlementReceipt> receipts) {
        if (receipts.isEmpty()) return List.of();
        List<SettlementReceipt> failed = new ArrayList<>();
        for (var receipt : receipts) {
            try {
                if (receipt.unstartedClaim() != null) repository.restoreUnstartedManualSettlement(receipt.checkpoint(), receipt.unstartedClaim());
                else if (receipt.success()) repository.completeManualSettlement(receipt.checkpoint(), receipt.rounds());
                else repository.flagManualSettlementForReview(receipt.checkpoint());
            } catch (SQLException | RuntimeException | LinkageError failure) {
                failed.add(receipt);
                logger.warning("挂机结算回执未确认: " + receipt.checkpoint().playerUuid() + "/"
                    + receipt.checkpoint().startTime() + " " + failure.getMessage());
            }
        }
        return failed;
    }

    private record SettlementReceipt(SessionRecord checkpoint, boolean success, int rounds, UUID unstartedClaim) {}

    private record StatsCheckpoint(Map<UUID, PlayerStats> stats, Map<UUID, PlayerStats> requested,
                                   List<SettlementReceipt> receipts, CompletableFuture<List<SettlementReceipt>> done) {}

    public record OnlineAfkPlayer(String playerName, String areaName, String mode, int seconds) {}

    // ── 公共 API ──

    public boolean toggleHud(UUID playerUuid) {
        if (hudDisabled.contains(playerUuid)) {
            hudDisabled.remove(playerUuid);
            return true;
        }
        hudDisabled.add(playerUuid);
        return false;
    }

    public boolean isHudEnabled(UUID playerUuid) {
        return !hudDisabled.contains(playerUuid);
    }

    public PlayerAfkState getState(UUID playerUuid) {
        return afkStates.get(playerUuid);
    }

    public PlayerStats getStatsSnapshot(UUID playerUuid) {
        return statsCache.get(playerUuid);
    }

    public int getPlayersInArea(String areaName) {
        return countPlayersInArea(areaName);
    }

    public Map<String, AfkArea> areas() {
        return config.areas();
    }

    public Map<String, AfkRewardType> types() {
        Map<String, AfkRewardType> result = new LinkedHashMap<>();
        for (AfkArea area : config.areas().values()) {
            result.put(area.id(), area.reward().type());
        }
        return Collections.unmodifiableMap(result);
    }

    public int getRewardRoundMinutes() {
        return config.areas().values().stream()
            .findFirst()
            .map(area -> area.reward().roundMinutes())
            .orElse(15);
    }

    public int getRewardRoundMinutes(String areaName) {
        AfkArea area = areaName == null ? null : config.areas().get(areaName);
        return area != null ? area.reward().roundMinutes() : getRewardRoundMinutes();
    }

    public int getSessionRewards(UUID uuid) {
        PlayerAfkState state = afkStates.get(uuid);
        return state != null ? state.sessionRewards : 0;
    }

    public int getSessionSeconds(UUID uuid) {
        PlayerAfkState state = afkStates.get(uuid);
        return state != null ? state.seconds : 0;
    }

    public int getDailySeconds(UUID uuid) {
        String today = LocalDate.now().format(DATE_FMT);
        if (!today.equals(dailySecondsDate)) {
            dailySeconds.clear();
            dailySecondsDate = today;
        }
        return dailySeconds.getOrDefault(uuid, 0);
    }

    public int getRemainingDailySeconds(UUID uuid) {
        int limit = config.antiAbuse().timeLimit().dailySeconds();
        if (limit <= 0) return -1;
        return Math.max(0, limit - getDailySeconds(uuid));
    }

    public AfkArea getArea(String areaName) {
        return areaName != null ? config.areas().get(areaName) : null;
    }

    public void onPlayerQuit(Player player) {
        UUID playerUuid = player.getUniqueId();
        areaStatsLoads.remove(playerUuid);
        PlayerAfkState state = afkStates.get(playerUuid);
        PlayerStats stats = statsCache.get(playerUuid);
        if (state != null && state.mode == AfkMode.MANUAL && state.areaName != null) {
            if (stats != null) {
                pendingSessionWrites.put(playerUuid, sessionCheckpoint(player, state, stats));
            }
            restoreManualState(player, state);
        }
        if (stats != null && requestedStats.containsKey(playerUuid)) requestedStats.put(playerUuid, stats);
        queueQuitCheckpoint(playerUuid, stats, pendingSessionWrites.remove(playerUuid));
        dirtyAreaStats.remove(playerUuid);
        areaStatsCache.remove(playerUuid);
        afkStates.remove(playerUuid);
        sentHuds.remove(playerUuid);
        refreshHuds.remove(playerUuid);
        enterNotified.remove(playerUuid);
        lastCombat.remove(playerUuid);
        pendingManualEndReasons.remove(playerUuid);
        viewChanges.remove(playerUuid);
        statsCache.remove(playerUuid);
        dirtyPlayerStats.remove(playerUuid);
        playerStatsLoads.remove(playerUuid);
        failedLoads.remove(playerUuid);
        recoveryChecked.remove(playerUuid);
        var recovering = sessionReads.get(playerUuid);
        if (recovering != null && recovering.claim == null) sessionReads.remove(playerUuid);
        readySessionReads.remove(playerUuid);
        nextRecoveryAttempt.remove(playerUuid);
    }

    private void queueQuitCheckpoint(UUID uuid, PlayerStats stats, SessionRecord session) {
        QuitCheckpoint checkpoint = captureQuitCheckpoint(uuid, stats, session);
        if (checkpoint != null) quitCheckpoints.merge(uuid, checkpoint, AfkRewardService::mergeQuitCheckpoint);
    }

    private static QuitCheckpoint mergeQuitCheckpoint(QuitCheckpoint previous, QuitCheckpoint latest) {
        var rows = new LinkedHashMap<String, AreaStats>();
        previous.areas().forEach(row -> rows.put(row.areaName(), row));
        latest.areas().forEach(row -> rows.put(row.areaName(), row));
        return new QuitCheckpoint(latest.uuid(), latest.stats() != null ? latest.stats() : previous.stats(),
            List.copyOf(rows.values()), latest.session() != null ? latest.session() : previous.session());
    }

    private QuitCheckpoint captureQuitCheckpoint(UUID uuid, PlayerStats stats, SessionRecord session) {
        PlayerStats elapsed = dirtyPlayerStats.contains(uuid) ? stats : null;
        Set<String> dirty = dirtyAreaStats.getOrDefault(uuid, Set.of());
        PlayerAreaStats areas = areaStatsCache.get(uuid);
        var rows = new ArrayList<AreaStats>(dirty.size());
        if (areas != null && areas.rows != null) {
            for (String name : dirty) {
                AreaStats row = areas.rows.get(name);
                if (row != null) rows.add(row);
            }
        }
        if (elapsed == null && rows.isEmpty() && session == null) return null;
        return new QuitCheckpoint(uuid, elapsed, rows, session);
    }

    private void scheduleQuitCheckpoints() {
        long now = System.nanoTime();
        if (quitWrite != null) {
            if (!quitWrite.isDone()) return;
            if (quitWrite.getNow(false)) inFlightQuitCheckpoints = List.of();
            else nextQuitWrite = now + TimeUnit.SECONDS.toNanos(5);
            quitWrite = null;
        }
        if (nextQuitWrite != 0 && nextQuitWrite - now > 0) return;
        nextQuitWrite = 0;
        if (inFlightQuitCheckpoints.isEmpty()) {
            if (quitCheckpoints.isEmpty()) return;
            var batch = new ArrayList<QuitCheckpoint>(Math.min(quitCheckpoints.size(), STATS_READS_PER_BATCH));
            var pending = quitCheckpoints.values().iterator();
            while (batch.size() < STATS_READS_PER_BATCH && pending.hasNext()) {
                batch.add(pending.next());
                pending.remove();
            }
            inFlightQuitCheckpoints = List.copyOf(batch);
        }
        var batch = inFlightQuitCheckpoints;
        try {
            quitWrite = CompletableFuture.supplyAsync(() -> {
                try {
                    repository.saveQuitCheckpoints(batch);
                    return true;
                } catch (SQLException | RuntimeException | LinkageError failure) {
                    logger.warning("后台保存挂机检查点失败，将重试: " + failure.getMessage());
                    return false;
                }
            }, writes);
        } catch (java.util.concurrent.RejectedExecutionException busy) {
            nextQuitWrite = now + TimeUnit.SECONDS.toNanos(5);
        }
    }

    private List<QuitCheckpoint> captureShutdownCheckpoints() {
        var checkpoints = new ArrayList<>(inFlightQuitCheckpoints);
        checkpoints.addAll(quitCheckpoints.values());
        var players = new HashSet<>(dirtyPlayerStats);
        players.addAll(dirtyAreaStats.keySet());
        players.addAll(afkStates.keySet());
        players.addAll(pendingSessionWrites.keySet());
        for (UUID uuid : players) {
            PlayerStats stats = statsCache.get(uuid);
            SessionRecord session = pendingSessionWrites.get(uuid);
            PlayerAfkState state = afkStates.get(uuid);
            if (session == null && stats != null && state != null && state.mode == AfkMode.MANUAL) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) session = sessionCheckpoint(player, state, stats);
            }
            QuitCheckpoint checkpoint = captureQuitCheckpoint(uuid, stats, session);
            if (checkpoint != null) checkpoints.add(checkpoint);
        }
        return List.copyOf(checkpoints);
    }

    /**
     * 判断指定玩家的统计数据是否因 DB 加载失败而不可用。
     *
     * @param uuid 玩家 UUID
     * @return {@code true} 表示上次加载失败，缓存中无可信数据
     */
    public boolean isLoadFailed(UUID uuid) {
        return failedLoads.contains(uuid);
    }

    public void showManualSettlementReviews(CommandSender sender) {
        readAsync(() -> {
            try {
                var reviews = repository.findManualSettlementsForReview(25);
                onMain(() -> {
                    if (reviews.isEmpty()) {
                        sender.sendMessage("§a当前没有待核对的挂机结算。");
                        return;
                    }
                    sender.sendMessage("§e挂机结算核对记录（最近 25 条，DISPATCHING 也可能是正在结算）");
                    for (var row : reviews) {
                        sender.sendMessage("§7" + row.playerName() + " " + row.playerUuid() + " " + row.startTime()
                            + " " + row.status() + " area=" + row.areaName() + ":" + row.rewardType()
                            + " seconds=" + row.seconds());
                    }
                });
            } catch (SQLException failure) {
                logger.warning("读取挂机结算核对记录失败: " + failure.getMessage());
                onMain(() -> sender.sendMessage("§c无法读取挂机结算核对记录，详情见服务器日志。"));
            }
        });
    }

    // ── 区域统计 ──

    private void updateAreaTime(UUID uuid, String areaName, int deltaSeconds) {
        String today = LocalDate.now().format(DATE_FMT);
        PlayerAreaStats areas = areaStatsCache.computeIfAbsent(uuid, ignored -> new PlayerAreaStats());
        Map<String, AreaStats> rows = areas.rows;
        if (rows == null) {
            areaStatsLoads.putIfAbsent(uuid, areas);
            areas.accrued.compute(areaName, (name, stats) -> addAreaSeconds(name, stats, today, deltaSeconds));
            return;
        }
        rows.compute(areaName, (name, stats) -> addAreaSeconds(name, stats, today, deltaSeconds));
        markAreaDirty(uuid, areaName);
    }

    private static AreaStats addAreaSeconds(String name, AreaStats stats, String today, int seconds) {
        if (stats == null) return new AreaStats(name, seconds, seconds, today);
        int todaySeconds = today.equals(stats.todayDate()) ? stats.todaySeconds() : 0;
        return new AreaStats(name, stats.totalSeconds() + seconds, todaySeconds + seconds, today);
    }

    private void completeAreaStats(UUID uuid, PlayerAreaStats pending, List<AreaStats> loaded) {
        if (!areaStatsLoads.remove(uuid, pending)) return;
        String today = LocalDate.now().format(DATE_FMT);
        Map<String, AreaStats> rows = new ConcurrentHashMap<>();
        for (AreaStats stats : loaded) rows.put(stats.areaName(), stats);
        for (AreaStats accrued : pending.accrued.values()) {
            String name = accrued.areaName();
            AreaStats saved = rows.get(name);
            int total = accrued.totalSeconds() + (saved != null ? saved.totalSeconds() : 0);
            int daily = today.equals(accrued.todayDate()) ? accrued.todaySeconds() : 0;
            if (saved != null && today.equals(saved.todayDate())) daily += saved.todaySeconds();
            rows.put(name, new AreaStats(name, total, daily, today));
            markAreaDirty(uuid, name);
        }
        pending.accrued.clear();
        pending.rows = rows;
    }

    private void retryAreaStats(UUID uuid, PlayerAreaStats pending, Exception failure) {
        if (!areaStatsLoads.remove(uuid, pending)) return;
        if (pending.retryAt == 0) {
            logger.warning("加载区域统计失败，将在后台重试: " + uuid + " " + failure.getMessage());
        }
        pending.defer(statsTick);
        areaStatsLoads.put(uuid, pending);
    }

    private void markAreaDirty(UUID uuid, String areaName) {
        dirtyAreaStats.computeIfAbsent(uuid, ignored -> new HashSet<>()).add(areaName);
    }

    public AreaStats getAreaStats(UUID uuid, String areaName) {
        Map<String, AreaStats> rows = cachedAreaRows(uuid);
        AreaStats stats = rows.get(areaName);
        return stats != null ? stats : new AreaStats(areaName, 0, 0, "");
    }

    public int getTrackedAreaSeconds(UUID uuid) {
        var rows = cachedAreaRows(uuid);
        int total = 0;
        for (String name : config.areas().keySet()) {
            AreaStats stats = rows.get(name);
            if (stats != null) total += stats.totalSeconds();
        }
        return total;
    }

    private Map<String, AreaStats> cachedAreaRows(UUID uuid) {
        PlayerAreaStats cache = areaStatsCache.get(uuid);
        if (cache == null) return Map.of();
        Map<String, AreaStats> rows = cache.rows;
        if (rows != null) return rows;
        cache.requested = true;
        return Map.of();
    }

    private static class StatsRetry {
        long retryAt;
        private int retryDelay = 1;

        final void defer(long currentTick) {
            retryAt = currentTick + retryDelay;
            retryDelay = Math.min(30, retryDelay * 2);
        }
    }

    private static final class PendingPlayerStats extends StatsRetry {
        final String playerName;
        int seconds;

        PendingPlayerStats(String playerName) {
            this.playerName = playerName;
        }
    }

    private static final class PendingSessionRead {
        final Player player;
        final AuthSnapshot admission;
        final SessionRecord checkpoint;
        SessionRecord loaded;
        UUID claim;

        PendingSessionRead(Player player, AuthSnapshot admission, SessionRecord checkpoint) {
            this.player = player;
            this.admission = admission;
            this.checkpoint = checkpoint;
        }
    }

    private static final class PlayerAreaStats extends StatsRetry {
        final Map<String, AreaStats> accrued = new HashMap<>();
        volatile Map<String, AreaStats> rows;
        volatile boolean requested;
    }

    // ── 联动派发 ──

    private void dispatchSignal(String signal, Player player, Map<String, String> variables) {
        if (signal == null || signal.isBlank()) return;
        SignalDispatchable sd = signalProvider != null ? signalProvider.get() : null;
        if (sd != null) {
            try {
                sd.dispatchSignal(signal, player, variables);
            } catch (Exception e) {
                logger.warning("信号派发失败 " + signal + ": " + e.getMessage());
            }
        }
    }

    private void dispatchSubtitle(Player player, String groupId) {
        if (groupId == null || groupId.isBlank()) return;
        SubtitlePlayable sp = subtitleProvider != null ? subtitleProvider.get() : null;
        if (sp != null) {
            try {
                sp.playGroup(player, groupId);
            } catch (Exception e) {
                logger.warning("字幕播放失败 " + groupId + ": " + e.getMessage());
            }
        }
    }

    private void dispatchMail(Player player, List<String> presetIds) {
        xuanmo.arcartxsuite.api.capability.mail.MailPresetHelper.dispatchPresets(
            mailProvider, player.getName(), presetIds, "afkreward", logger);
    }

    public static String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d时%02d分%02d秒", hours, minutes, seconds);
        }
        return String.format("%02d分%02d秒", minutes, seconds);
    }

    public static final class PlayerAfkState {
        public volatile AfkMode mode = AfkMode.REGION;
        public volatile String areaName;
        public volatile String rewardType;
        public volatile int seconds;
        private int regionSecondsBeforeRecovery;
        public volatile int lastRewardSeconds;
        public volatile int sessionRewards;
        public volatile Float originalWalkSpeed;
        public volatile Boolean originalCollidable;
        public volatile org.bukkit.Location originalLocation;
        public volatile long startTimeMillis;
    }

    public enum AfkMode {
        REGION, MANUAL
    }
}
