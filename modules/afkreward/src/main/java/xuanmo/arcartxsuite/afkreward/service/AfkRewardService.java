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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.capability.SignalDispatchable;
import xuanmo.arcartxsuite.api.capability.SubtitlePlayable;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.afkreward.config.AfkRewardConfiguration;
import xuanmo.arcartxsuite.afkreward.model.AfkArea;
import xuanmo.arcartxsuite.afkreward.model.AfkRewardType;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.AreaStats;
import xuanmo.arcartxsuite.afkreward.storage.AfkRewardRepository.PlayerStats;

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

    // 在线玩家状态
    private final Map<UUID, PlayerAfkState> afkStates = new ConcurrentHashMap<>();
    // 玩家统计缓存
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();
    // 区域统计缓存
    private final Map<String, Map<UUID, AreaStats>> areaStatsCache = new ConcurrentHashMap<>();
    // 已发送进入提示的玩家
    private final Set<UUID> enterNotified = ConcurrentHashMap.newKeySet();
    // HUD 显示开关
    private final Set<UUID> hudDisabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dailySeconds = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> viewChanges = new ConcurrentHashMap<>();
    private final Set<UUID> antiAbuseNotified = ConcurrentHashMap.newKeySet();
    private final Set<UUID> overflowNotified = ConcurrentHashMap.newKeySet();
    private final Map<UUID, EndReason> pendingManualEndReasons = new ConcurrentHashMap<>();
    private volatile String dailySecondsDate = LocalDate.now().format(DATE_FMT);
    private final AfkRewardAuditLogger auditLogger;
    private volatile PacketBridgeAPI packetBridge;
    private volatile String hudUiId;

    private BukkitTask tickTask;
    private boolean active;

    // 排行榜缓存
    private volatile List<PlayerStats> leaderboardCache = List.of();
    private volatile long leaderboardLastUpdate = 0;

    // 服务器启动时间，用于崩溃恢复
    private final long serviceStartTime = System.currentTimeMillis();

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
    }

    public void start() {
        active = true;
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);

        // 为在线玩家预加载缓存
        for (Player player : Bukkit.getOnlinePlayers()) {
            preloadStats(player.getUniqueId(), player.getName());
        }

        // 崩溃恢复：尝试结算上次未正常结束的 MANUAL 挂机
        recoverCrashedSessions();

        // 预加载排行榜
        refreshLeaderboard();
    }

    public void shutdown() {
        active = false;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        // 保存 MANUAL 模式下在线玩家的 session，以便崩溃恢复
        for (Map.Entry<UUID, PlayerAfkState> entry : afkStates.entrySet()) {
            if (entry.getValue().mode == AfkMode.MANUAL) {
                saveSession(entry.getKey(), entry.getValue());
            }
        }
        // 保存所有缓存数据
        for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
            trySave(entry.getKey(), entry.getValue());
        }
        afkStates.clear();
        statsCache.clear();
        enterNotified.clear();
        hudDisabled.clear();
        lastCombat.clear();
        dailySeconds.clear();
        viewChanges.clear();
        antiAbuseNotified.clear();
        overflowNotified.clear();
        pendingManualEndReasons.clear();
    }

    private void tick() {
        if (!active) return;
        if (config.performance().pauseOnLowTps()
            && TpsProvider.currentTps() < config.performance().minTps()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            processPlayer(player);
            pushHud(player);
        }
    }

    private void pushHud(Player player) {
        PacketBridgeAPI bridge = packetBridge;
        if (bridge == null || hudUiId == null || !bridge.isAvailable()) return;
        PlayerAfkState state = afkStates.get(player.getUniqueId());
        if (state == null) return;
        String area = state.areaName != null ? state.areaName : "";
        int remaining = 0;
        if (state.areaName != null) {
            int roundSec = config.reward().roundMinutes() * 60;
            remaining = Math.max(0, roundSec - (state.seconds - state.lastRewardSeconds));
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("area", area);
        payload.put("time", formatTime(state.seconds));
        payload.put("next", String.valueOf(remaining));
        payload.put("players", state.areaName != null
            ? String.valueOf(countPlayersInArea(state.areaName)) : "0");
        payload.put("session_rewards", String.valueOf(state.sessionRewards));
        payload.put("session_time", formatTime(state.seconds));
        payload.put("visible", String.valueOf(isHudEnabled(player.getUniqueId())
            && state.areaName != null));
        try {
            bridge.sendPacket(player, hudUiId, "update", payload);
        } catch (RuntimeException ignored) {
            // 客户端桥接不可用时不影响计奖。
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
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();

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
            state.rewardType = currentArea.rewardType();
                state.seconds = 0;
                state.lastRewardSeconds = 0;
                state.sessionRewards = 0;
                state.startTimeMillis = System.currentTimeMillis();
                enterNotified.remove(uuid);
                antiAbuseNotified.remove(uuid);
                overflowNotified.remove(uuid);
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
        if (config.reward().player().enabled()) {
            int count = countPlayersInArea(currentArea.name());
            if (count > config.reward().player().limit()) {
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
        updateTotalTime(uuid, 1);
        updateDailySeconds(uuid, 1);

        // 检查奖励
        int roundSeconds = config.reward().roundMinutes() * 60;
        if (state.seconds - state.lastRewardSeconds >= roundSeconds) {
            grantReward(player, currentArea);
            state.lastRewardSeconds = state.seconds;
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

    private void grantReward(Player player, AfkArea area) {
        UUID uuid = player.getUniqueId();
        if (!passesAntiAbuse(player, afkStates.get(uuid), area, false)) return;
        String today = LocalDate.now().format(DATE_FMT);
        PlayerStats stats = getStats(uuid);

        // 日期切换
        if (!today.equals(stats.todayDate())) {
            stats = new PlayerStats(stats.playerName(), today, 0, stats.totalCount(), stats.totalSeconds());
            statsCache.put(uuid, stats);
        }

        // 每日上限
        if (config.reward().max().enabled() && stats.todayCount() >= config.reward().max().limit()) {
            if (!player.hasPermission("axs.afkreward.not.reward.limit")) {
                player.sendMessage(messages != null ? messages.get("hints.reward-limit") : "§c你今日已达到该区域最大奖励次数限制。");
                return;
            }
        }

        // 找到奖励类型
        AfkRewardType rewardType = config.types().get(area.rewardType());
        if (rewardType == null) return;

        // 确定玩家等级 (从高到低)
        List<String> tierKeys = new ArrayList<>(rewardType.tierCommands().keySet());
        // 常见排序: vip3 > vip2 > vip1 > common，自定义顺序保持配置顺序
        String matchedTier = null;
        for (String tier : tierKeys) {
            if (player.hasPermission("axs.afkreward.start." + area.rewardType() + "." + tier)
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
        if (commands == null || commands.isEmpty()) return;

        boolean mailed = tryOverflowMail(player, rewardType);
        if (!mailed) {
            for (String cmd : commands) {
                String parsed = replaceRewardPlaceholders(cmd, player, afkStates.get(uuid), area);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }

        // 更新统计
        stats = new PlayerStats(
            stats.playerName(), stats.todayDate(),
            stats.todayCount() + 1,
            stats.totalCount() + 1,
            stats.totalSeconds()
        );
        statsCache.put(uuid, stats);
        trySave(uuid, stats);
        PlayerAfkState rewardedState = afkStates.get(uuid);
        if (rewardedState != null) rewardedState.sessionRewards++;

        // 更新区域统计
        updateAreaTime(uuid, area.name(), 0);
        auditLogger.log(player, uuid, area.name(), "REGION",
            mailed ? matchedTier + "(mail)" : matchedTier, 1,
            computeMultiplier(player, afkStates.get(uuid), area));

        // 触发联动
        AfkRewardType rewardTypeObj = config.types().get(area.rewardType());
        if (rewardTypeObj != null) {
            dispatchMail(player, rewardTypeObj.mailPresets());
        }
        PlayerAfkState currentState = afkStates.get(uuid);
        dispatchSignal("afk_reward", player, Map.of(
            "area", area.name(), "mode", "REGION",
            "seconds", String.valueOf(currentState != null ? currentState.seconds : 0),
            "rewards", "1"
        ));
        dispatchSubtitle(player, config.manual().subtitleOnReward());
        publishRewardEvent(player, area.name());

        String msg = messages != null ? messages.get("hints.reward", area.name(), String.valueOf(config.reward().roundMinutes())) : null;
        if (msg != null) player.sendMessage(msg);

        if (config.debug()) {
            logger.info("[AfkReward] " + player.getName() + " 在 " + area.name() + " 获得奖励 (tier=" + matchedTier + ")");
        }
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
        AfkRewardConfiguration.MultiplierConfig multiplier = config.multiplier();
        double result = multiplier.enable() ? multiplier.base() : 1.0;
        if (multiplier.enable()) {
            List<Double> matches = new ArrayList<>();
            LocalTime now = LocalTime.now();
            DayOfWeek day = java.time.LocalDate.now().getDayOfWeek();
            if ((day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
                && multiplier.weekend() != 1.0) matches.add(multiplier.weekend());
            for (AfkRewardConfiguration.ScheduleConfig schedule : multiplier.schedules()) {
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

    private boolean scheduleMatches(AfkRewardConfiguration.ScheduleConfig schedule,
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
        return command.replace("%player_name%", player.getName())
            .replace("%axsafk_multiplier%", value)
            .replace("%axsafkreward_multiplier%", value);
    }

    private boolean tryOverflowMail(Player player, AfkRewardType rewardType) {
        if (!config.reward().overflowToMail().enable()
            || rewardType.fullInventoryMail() == null
            || rewardType.fullInventoryMail().isBlank()
            || player.getInventory().firstEmpty() != -1) {
            return false;
        }
        MailDispatchable mail = mailProvider != null ? mailProvider.get() : null;
        if (mail == null || !mail.dispatchPreset(rewardType.fullInventoryMail(),
            player.getName(), "AfkReward")) {
            return false;
        }
        if (overflowNotified.add(player.getUniqueId())) {
            String msg = messages != null ? messages.get("hints.overflow-mail") : null;
            if (msg != null) player.sendMessage(msg);
        }
        return true;
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
        if (stats == null) return;
        stats = new PlayerStats(
            stats.playerName(), stats.todayDate(),
            stats.todayCount(), stats.totalCount(),
            stats.totalSeconds() + deltaSeconds
        );
        statsCache.put(uuid, stats);

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

    private PlayerStats getStats(UUID uuid) {
        return statsCache.computeIfAbsent(uuid, id -> {
            try {
                return repository.loadStats(id);
            } catch (Exception e) {
                logger.warning("[AfkReward] 加载玩家统计失败: " + e.getMessage());
                return new PlayerStats("", "", 0, 0, 0);
            }
        });
    }

    private void preloadStats(UUID uuid, String playerName) {
        statsCache.computeIfAbsent(uuid, id -> {
            try {
                PlayerStats stats = repository.loadStats(id);
                if (stats.playerName().isEmpty()) {
                    stats = new PlayerStats(playerName, stats.todayDate(), stats.todayCount(), stats.totalCount(), stats.totalSeconds());
                }
                return stats;
            } catch (Exception e) {
                return new PlayerStats(playerName, "", 0, 0, 0);
            }
        });
    }

    private void trySave(UUID uuid, PlayerStats stats) {
        try {
            repository.saveStats(uuid, stats);
        } catch (Exception e) {
            logger.warning("[AfkReward] 保存玩家统计失败: " + e.getMessage());
        }
    }

    private void sendLeaveHint(Player player, String areaName) {
        String msg = messages != null ? messages.get("hints.leave-area", areaName) : null;
        if (msg != null) player.sendMessage(msg);
    }

    // ── MANUAL 模式 API ──

    public boolean startManualAfk(Player player, String areaName) {
        UUID uuid = player.getUniqueId();
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
        if (config.reward().player().enabled()) {
            int count = countPlayersInArea(areaName);
            if (count >= config.reward().player().limit() && !player.hasPermission("axs.afkreward.not.player.limit")) {
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
        state.rewardType = area.rewardType();
        state.seconds = 0;
        state.lastRewardSeconds = 0;
        state.originalWalkSpeed = player.getWalkSpeed();
        state.originalLocation = player.getLocation().clone();
        state.startTimeMillis = System.currentTimeMillis();
        afkStates.put(uuid, state);

        // 传送
        if (isManualProtectionEnabled(ManualProtection.VEHICLE_ENTER)) {
            player.leaveVehicle();
        }
        player.teleport(area.teleport());
        if (isManualProtectionEnabled(ManualProtection.COLLIDABLE)) {
            player.setCollidable(false);
        }

        // 行为封锁
        if (config.manual().restrictActions()) {
            player.setWalkSpeed(0.0001f);
        }

        // 信号触发
        dispatchSignal("afk_start", player, Map.of("area", areaName, "mode", "MANUAL"));

        String msg = messages != null ? messages.get("hints.manual.start", areaName) : null;
        if (msg != null) player.sendMessage(msg);
        if (config.debug()) {
            logger.info("[AfkReward] " + player.getName() + " 开始原地挂机: " + areaName);
        }
        return true;
    }

    public boolean endManualAfk(Player player, boolean silent) {
        return endManualAfk(player, silent, EndReason.MANUAL);
    }

    public boolean endManualAfk(Player player, boolean silent, EndReason reason) {
        UUID uuid = player.getUniqueId();
        EndReason pendingReason = pendingManualEndReasons.remove(uuid);
        if (pendingReason != null && reason == EndReason.MANUAL) {
            reason = pendingReason;
        }
        PlayerAfkState state = afkStates.get(uuid);
        if (state == null || state.mode != AfkMode.MANUAL || state.areaName == null) {
            if (!silent) player.sendMessage(messages != null ? messages.get("hints.manual.not-started") : "§c你未在挂机中。");
            return false;
        }

        AfkArea area = config.areas().get(state.areaName);
        int times = 0;
        if (area != null) {
            times = computeRewardTimes(state.seconds);
            if (times > 0) {
                grantManualRewards(player, area, times);
            }
        }

        // 恢复原始状态
        if (config.manual().restrictActions()) {
            player.setWalkSpeed(state.originalWalkSpeed);
        }
        player.setCollidable(true);
        if (config.manual().returnOnEnd() && state.originalLocation != null) {
            player.teleport(state.originalLocation);
        }

        // 广播
        if (!silent && config.manual().broadcastRewards() && times > 0) {
            String broadcast = messages != null ? messages.get("hints.manual.broadcast",
                player.getName(), formatTime(state.seconds), String.valueOf(times)) : null;
            if (broadcast != null) Bukkit.broadcastMessage(broadcast);
        }

        // 更新统计
        PlayerStats stats = statsCache.get(uuid);
        if (stats != null) {
            trySave(uuid, stats);
        }

        // 保存区域统计
        if (state.areaName != null) {
            saveAreaStats(uuid, state.areaName);
        }

        // 清理 session
        try {
            repository.deleteSession(uuid);
        } catch (Exception e) {
            logger.warning("[AfkReward] 删除 session 失败: " + e.getMessage());
        }

        // 触发结束联动
        dispatchSignal("afk_end", player, Map.of(
            "area", state.areaName, "mode", "MANUAL",
            "seconds", String.valueOf(state.seconds),
            "rewards", String.valueOf(times)
        ));
        dispatchSubtitle(player, config.manual().subtitleOnEnd());
        dispatchMail(player, config.manual().endMailPresets());

        afkStates.remove(uuid);
        enterNotified.remove(uuid);
        overflowNotified.remove(uuid);
        pendingManualEndReasons.remove(uuid);

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
            logger.info("[AfkReward] " + player.getName() + " 结束原地挂机: " + state.areaName + " 奖励次数=" + times);
        }
        refreshLeaderboard();
        return true;
    }

    private int computeRewardTimes(int totalSeconds) {
        int roundSec = config.reward().roundMinutes() * 60;
        return totalSeconds / roundSec;
    }

    private void grantManualRewards(Player player, AfkArea area, int times) {
        AfkRewardType rewardType = config.types().get(area.rewardType());
        if (rewardType == null) return;
        PlayerAfkState state = afkStates.get(player.getUniqueId());
        List<String> tierKeys = new ArrayList<>(rewardType.tierCommands().keySet());
        String matchedTier = null;
        for (String tier : tierKeys) {
            if (player.hasPermission("axs.afkreward.start." + area.rewardType() + "." + tier)
                || player.hasPermission("axs.afkreward.start." + tier)) {
                matchedTier = tier;
                break;
            }
        }
        if (matchedTier == null && !tierKeys.isEmpty()) {
            matchedTier = tierKeys.get(tierKeys.size() - 1);
        }
        List<String> commands = rewardType.tierCommands().get(matchedTier);
        if (commands == null || commands.isEmpty()) return;

        UUID uuid = player.getUniqueId();
        String today = LocalDate.now().format(DATE_FMT);
        PlayerStats stats = getStats(uuid);
        if (!today.equals(stats.todayDate())) {
            stats = new PlayerStats(stats.playerName(), today, 0, stats.totalCount(), stats.totalSeconds());
        }

        int actualTimes = 0;
        boolean mailedAny = false;
        for (int i = 0; i < times; i++) {
            if (!passesAntiAbuse(player, state, area, true)) break;
            // 每日上限检查
            if (config.reward().max().enabled() && stats.todayCount() >= config.reward().max().limit()) {
                if (!player.hasPermission("axs.afkreward.not.reward.limit")) break;
            }
            boolean mailed = tryOverflowMail(player, rewardType);
            mailedAny |= mailed;
            if (!mailed) {
                for (String cmd : commands) {
                    String parsed = replaceRewardPlaceholders(cmd, player, state, area);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                }
            }
            actualTimes++;
            if (state != null) state.sessionRewards++;
            stats = new PlayerStats(stats.playerName(), stats.todayDate(),
                stats.todayCount() + 1, stats.totalCount() + 1, stats.totalSeconds());
        }
        if (actualTimes > 0) {
            statsCache.put(uuid, stats);
            trySave(uuid, stats);
            auditLogger.log(player, uuid, area.name(), "MANUAL",
                mailedAny ? matchedTier + "(mail)" : matchedTier, actualTimes,
                computeMultiplier(player, state, area));
        }
        String msg = messages != null ? messages.get("hints.reward", area.name(), String.valueOf(config.reward().roundMinutes())) : null;
        if (msg != null && actualTimes > 0) player.sendMessage(msg);
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

    private void recoverCrashedSessions() {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                AfkRewardRepository.SessionRecord session = repository.loadSession(player.getUniqueId());
                if (session == null) continue;
                // 计算挂机时长
                long elapsedMs = serviceStartTime - session.startTime();
                if (elapsedMs < 0) elapsedMs = 0;
                int elapsedSec = (int) (elapsedMs / 1000);
                int times = computeRewardTimes(elapsedSec + session.startSeconds());

                // 恢复 stats 到 session 记录时的状态
                PlayerStats recoveredStats = new PlayerStats(
                    session.playerName(), session.todayDate(), session.todayCount(),
                    session.totalCount(), session.totalSeconds()
                );
                statsCache.put(player.getUniqueId(), recoveredStats);

                AfkArea area = config.areas().get(session.areaName());
                if (area != null && times > 0) {
                    grantManualRewards(player, area, times);
                }

                String msg = messages != null ? messages.get("hints.manual.recovered",
                    formatTime(elapsedSec + session.startSeconds()), String.valueOf(times)) : null;
                if (msg != null) player.sendMessage(msg);

                repository.deleteSession(player.getUniqueId());
                if (config.debug()) {
                    logger.info("[AfkReward] 恢复玩家 " + player.getName() + " 的挂机 session，时长="
                        + formatTime(elapsedSec + session.startSeconds()));
                }
            }
        } catch (Exception e) {
            logger.warning("[AfkReward] 崩溃恢复失败: " + e.getMessage());
        }
    }

    private void saveSession(UUID uuid, PlayerAfkState state) {
        PlayerStats stats = statsCache.get(uuid);
        if (stats == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        try {
            repository.saveSession(new AfkRewardRepository.SessionRecord(
                uuid, player.getName(), state.areaName,
                state.rewardType != null ? state.rewardType : "",
                state.mode.name(), state.seconds, System.currentTimeMillis(),
                stats.todayCount(), stats.totalCount(), stats.todayDate(), stats.totalSeconds()
            ));
        } catch (Exception e) {
            logger.warning("[AfkReward] 保存 session 失败: " + e.getMessage());
        }
    }

    // ── 排行榜 ──

    private void refreshLeaderboard() {
        try {
            leaderboardCache = repository.loadLeaderboard(config.manual().leaderboardSize());
            leaderboardLastUpdate = System.currentTimeMillis();
        } catch (Exception e) {
            logger.warning("[AfkReward] 刷新排行榜失败: " + e.getMessage());
        }
    }

    public List<AfkRewardRepository.PlayerStats> getLeaderboard() {
        if (System.currentTimeMillis() - leaderboardLastUpdate > 60000) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshLeaderboard);
        }
        return leaderboardCache;
    }

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
        return config.types();
    }

    public int getRewardRoundMinutes() {
        return config.reward().roundMinutes();
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

    public void onPlayerQuit(UUID playerUuid) {
        PlayerAfkState state = afkStates.get(playerUuid);
        if (state != null && state.mode == AfkMode.MANUAL && state.areaName != null) {
            saveSession(playerUuid, state);
        }
        PlayerStats stats = statsCache.get(playerUuid);
        if (stats != null) {
            trySave(playerUuid, stats);
        }
        // 保存区域统计
        if (state != null && state.areaName != null) {
            saveAreaStats(playerUuid, state.areaName);
        }
        afkStates.remove(playerUuid);
        enterNotified.remove(playerUuid);
        lastCombat.remove(playerUuid);
        overflowNotified.remove(playerUuid);
        pendingManualEndReasons.remove(playerUuid);
        viewChanges.remove(playerUuid);
        statsCache.remove(playerUuid);
    }

    // ── 区域统计 ──

    private void updateAreaTime(UUID uuid, String areaName, int deltaSeconds) {
        String today = LocalDate.now().format(DATE_FMT);
        Map<UUID, AreaStats> cache = areaStatsCache.computeIfAbsent(areaName, k -> new ConcurrentHashMap<>());
        AreaStats stats = cache.computeIfAbsent(uuid, id -> {
            try {
                return repository.loadAreaStats(id, areaName);
            } catch (Exception e) {
                return new AreaStats(areaName, 0, 0, "");
            }
        });
        if (!today.equals(stats.todayDate())) {
            stats = new AreaStats(areaName, stats.totalSeconds(), 0, today);
        }
        stats = new AreaStats(areaName, stats.totalSeconds() + deltaSeconds,
            stats.todaySeconds() + deltaSeconds, today);
        cache.put(uuid, stats);
    }

    private void saveAreaStats(UUID uuid, String areaName) {
        Map<UUID, AreaStats> cache = areaStatsCache.get(areaName);
        if (cache == null) return;
        AreaStats stats = cache.get(uuid);
        if (stats == null) return;
        try {
            repository.saveAreaStats(uuid, areaName, stats.totalSeconds(), stats.todaySeconds(), stats.todayDate());
        } catch (Exception e) {
            logger.warning("[AfkReward] 保存区域统计失败: " + e.getMessage());
        }
    }

    public AreaStats getAreaStats(UUID uuid, String areaName) {
        Map<UUID, AreaStats> cache = areaStatsCache.get(areaName);
        if (cache != null) {
            AreaStats stats = cache.get(uuid);
            if (stats != null) return stats;
        }
        try {
            return repository.loadAreaStats(uuid, areaName);
        } catch (Exception e) {
            return new AreaStats(areaName, 0, 0, "");
        }
    }

    // ── 联动派发 ──

    private void dispatchSignal(String signal, Player player, Map<String, String> variables) {
        if (signal == null || signal.isBlank()) return;
        SignalDispatchable sd = signalProvider != null ? signalProvider.get() : null;
        if (sd != null) {
            try {
                sd.dispatchSignal(signal, player, variables);
            } catch (Exception e) {
                logger.warning("[AfkReward] 信号派发失败 " + signal + ": " + e.getMessage());
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
                logger.warning("[AfkReward] 字幕播放失败 " + groupId + ": " + e.getMessage());
            }
        }
    }

    private void dispatchMail(Player player, List<String> presetIds) {
        if (presetIds == null || presetIds.isEmpty()) return;
        MailDispatchable md = mailProvider != null ? mailProvider.get() : null;
        if (md == null) {
            logger.fine("[AfkReward] Mail 模块不可用，跳过邮件派发");
            return;
        }
        for (String presetId : presetIds) {
            if (presetId == null || presetId.isBlank()) continue;
            try {
                boolean ok = md.dispatchPreset(presetId, player.getName(), "AfkReward");
                if (config.debug()) {
                    logger.info("[AfkReward] 邮件派发 -> preset=" + presetId + " player=" + player.getName() + " success=" + ok);
                }
            } catch (Exception e) {
                logger.warning("[AfkReward] 邮件派发失败 " + presetId + ": " + e.getMessage());
            }
        }
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
        public volatile int lastRewardSeconds;
        public volatile int sessionRewards;
        public volatile float originalWalkSpeed = 0.2f;
        public volatile org.bukkit.Location originalLocation;
        public volatile long startTimeMillis;
    }

    public enum AfkMode {
        REGION, MANUAL
    }
}
