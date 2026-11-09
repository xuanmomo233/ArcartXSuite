package xuanmo.arcartxsuite.battlepass.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.capability.MailDispatchable;
import xuanmo.arcartxsuite.api.currency.CurrencyBridgeAPI;
import xuanmo.arcartxsuite.api.item.ItemSourceRegistry;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassSeason;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;

public final class BattlePassService {

    /**
     * 任务或进度变化后的 UI 刷新回调。
     */
    @FunctionalInterface
    public interface UiRefreshListener {
        /**
         * 通知 UI 刷新。
         *
         * @param playerUuid 玩家 UUID
         * @param structureChanged 任务集合结构是否发生变化
         */
        void onRefresh(UUID playerUuid, boolean structureChanged);
    }



    private final JavaPlugin plugin;
    private final BattlePassModuleConfiguration configuration;
    private final BattlePassRepository repository;
    private final Logger logger;
    private final BattlePassRewardDispatcher rewardDispatcher;
    private final BattlePassTaskEngine taskEngine;

    private final Map<UUID, BattlePassPlayerProgress> cachedProgress = new ConcurrentHashMap<>();
    private final Map<UUID, List<PlayerTaskInstance>> cachedInstances = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> instanceIndexes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, List<String>>> templateIndexes = new ConcurrentHashMap<>();
    private final Map<String, BattlePassTask> taskIndex;
    private final Map<Integer, List<BattlePassReward>> rewardsByLevel;
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> dirtyInstances = new ConcurrentHashMap<>();
    // 加载失败的玩家集合：这些玩家的缓存数据不可信，flushDirtyProgress 必须跳过，避免空进度覆盖 DB 真实数据
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();
    private BukkitTask saveTask;
    private BukkitTask resetTask;
    private volatile boolean active;
    private final java.util.concurrent.CompletableFuture<Void> authDrained=new java.util.concurrent.CompletableFuture<>();
    private final java.util.concurrent.ExecutorService authFacts=new java.util.concurrent.ThreadPoolExecutor(2,2,0,java.util.concurrent.TimeUnit.MILLISECONDS,
        new java.util.concurrent.ArrayBlockingQueue<>(128),r->{ Thread t=new Thread(r,"AXS-BattlePassAuth"); t.setDaemon(true); return t; },new java.util.concurrent.ThreadPoolExecutor.AbortPolicy()) {
            @Override protected void terminated() { authDrained.complete(null); }
        };
    private final Set<java.util.concurrent.CompletableFuture<Void>> authReceipts=ConcurrentHashMap.newKeySet();
    private final Set<UUID> uncertainAuthWrites=ConcurrentHashMap.newKeySet();
    private record AuthTaskDelta(String instance,int increment,int completionXp) {}
    private record AuthProjection(BattlePassPlayerProgress progress,List<PlayerTaskInstance> tasks,List<AuthTaskDelta> deltas) {}
    // Accessed only under repository.withPlayerWrite; an ambiguous commit fences all ordinary snapshot writers.
    private final Map<UUID,Map<String,List<AuthTaskDelta>>> pendingAuthApply=new ConcurrentHashMap<>();

    private volatile UiRefreshListener uiRefreshListener;

    private Supplier<EventBusCapability> eventBusProvider;
    private final TaskAssignmentService assignmentService = new TaskAssignmentService();
    private SeasonSettlementService settlementService;

    public BattlePassService(
        JavaPlugin plugin,
        BattlePassModuleConfiguration configuration,
        BattlePassRepository repository,
        Logger logger,
        @Nullable ItemSourceRegistry itemSourceRegistry,
        @Nullable CurrencyBridgeAPI currencyManager,
        @Nullable java.util.function.Supplier<MailDispatchable> mailProvider,
        @Nullable xuanmo.arcartxsuite.api.item.ItemRewardDispatcher itemRewardDispatcher,
        xuanmo.arcartxsuite.api.item.PendingRewardService pendingRewardService,
        @Nullable xuanmo.arcartxsuite.api.icon.IconResolver iconResolver
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.logger = logger;
        this.rewardDispatcher = new BattlePassRewardDispatcher(plugin, logger, itemSourceRegistry, currencyManager, mailProvider);
        this.rewardDispatcher.setItemRewardDispatcher(itemRewardDispatcher);
        this.rewardDispatcher.setItemResolver(iconResolver);
        Map<String, BattlePassTask> taskIndex = new HashMap<>();
        for (BattlePassTask task : configuration.tasks().all()) {
            taskIndex.putIfAbsent(task.taskId(), task);
        }
        this.taskIndex = Map.copyOf(taskIndex);

        Map<Integer, List<BattlePassReward>> rewardIndex = new HashMap<>();
        for (BattlePassReward reward : configuration.rewards()) {
            rewardIndex.computeIfAbsent(reward.level(), ignored -> new ArrayList<>()).add(reward);
        }
        Map<Integer, List<BattlePassReward>> immutableRewardIndex = new HashMap<>();
        for (Map.Entry<Integer, List<BattlePassReward>> entry : rewardIndex.entrySet()) {
            immutableRewardIndex.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.rewardsByLevel = Map.copyOf(immutableRewardIndex);
        this.taskEngine = new BattlePassTaskEngine(plugin, this, configuration, logger);
        this.settlementService = new SeasonSettlementService(plugin, configuration, repository, this, rewardDispatcher, logger, pendingRewardService);
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    /**
     * 获取赛季结算服务实例。
     *
     * @return 结算服务，不为 null
     */
    public SeasonSettlementService settlementService() {
        return settlementService;
    }


    /**
     * 设置战令 UI 刷新回调。
     *
     * @param listener 刷新回调，为 null 时关闭主动推送
     */
    public void setUiRefreshListener(@Nullable UiRefreshListener listener) {
        this.uiRefreshListener = listener;
    }



    private void notifyUiRefresh(UUID playerUuid, boolean structureChanged) {
        UiRefreshListener listener = uiRefreshListener;
        if (listener == null) return;
        try {
            listener.onRefresh(playerUuid, structureChanged);
        } catch (Exception e) {
            logger.warning("UI 刷新回调失败 [" + playerUuid + "]: " + e.getMessage());
        }
    }

    public void start() {
        active = true;
        // 延迟 1 tick 启动 TaskEngine，确保所有模块的 publishedTopics() 已注册到 EventBus
        // （模块加载顺序不确定，延迟到下一个 tick 时 loadAll 已完成）
        org.bukkit.scheduler.BukkitTask delayedStart = Bukkit.getScheduler().runTask(plugin, () -> {
            if (active) {
                taskEngine.start(eventBusProvider);
            }
        });
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushDirtyProgress, 300L, 300L);
        resetTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkResets, 6000L, 6000L);
    }

    public java.util.concurrent.CompletionStage<Void> shutdown() {
        active = false;
        authFacts.shutdownNow();
        authReceipts.forEach(future->future.completeExceptionally(new java.util.concurrent.CancellationException("BattlePass closed")));
        taskEngine.shutdown();
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        return authDrained;
    }
    public void finishShutdown() {
        flushDirtyProgress();
        cachedProgress.clear();
        cachedInstances.clear();
        instanceIndexes.clear();
        templateIndexes.clear();
        playerLocks.clear();
        dirtyPlayers.clear();
        dirtyInstances.clear();
        failedLoads.clear();
        pendingAuthApply.clear(); uncertainAuthWrites.clear();
    }

    public BattlePassPlayerProgress getProgress(Player player) {
        UUID uuid = player.getUniqueId();
        BattlePassPlayerProgress cached = cachedProgress.get(uuid);
        if (cached != null) return cached;
        // 首次加载失败时短暂等待后重试一次，应对 DB 连接短暂抖动
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                BattlePassPlayerProgress loaded = repository.loadProgress(uuid, configuration.season().seasonId());
                cachedProgress.put(uuid, loaded);
                failedLoads.remove(uuid);
                return loaded;
            } catch (SQLException e) {
                if (attempt == 0) {
                    logger.warning("加载战令进度失败（将重试一次）: " + e.getMessage());
                    try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                } else {
                    logger.warning("加载战令进度失败（重试仍失败）: " + e.getMessage());
                    // 不缓存空进度，避免 flushDirtyProgress 把空数据写回 DB 覆盖真实数据
                    // 标记为加载失败，flushDirtyProgress 将跳过该玩家
                    failedLoads.add(uuid);
                    return new BattlePassPlayerProgress(uuid, configuration.season().seasonId());
                }
            }
        }
        // 理论上不会走到这里
        failedLoads.add(uuid);
        return new BattlePassPlayerProgress(uuid, configuration.season().seasonId());
    }

    public List<PlayerTaskInstance> getTaskInstances(Player player) {
        UUID uuid = player.getUniqueId();
        List<PlayerTaskInstance> cached = cachedInstances.get(uuid);
        if (cached != null) return List.copyOf(cached);
        synchronized (playerLock(uuid)) {
            cached = cachedInstances.get(uuid);
            if (cached != null) return List.copyOf(cached);
            try {
                String seasonId = configuration.season().seasonId();
                LocalDate today = LocalDate.now();
                List<PlayerTaskInstance> result = new ArrayList<>(
                    repository.loadPlayerTasks(uuid, seasonId));
                boolean hasDaily = result.stream().anyMatch(i -> i.category() == BattlePassTask.TaskCategory.DAILY);
                boolean hasWeekly = result.stream().anyMatch(i -> i.category() == BattlePassTask.TaskCategory.WEEKLY);
                BattlePassPlayerProgress progress = null;
                if (!hasDaily || !hasWeekly) {
                    progress = getProgress(player);
                }
                if (!hasDaily) {
                    result.addAll(assignmentService.assignDailyTasks(
                        uuid, configuration.tasks().daily(), configuration.tasks().dailyCount(), today));
                }
                if (!hasWeekly) {
                    result.addAll(assignmentService.assignWeeklyTasks(
                        uuid, configuration.tasks().weekly(), configuration.tasks().weeklyCount(),
                        progress.currentWeekNumber(), today));
                }
                Set<String> seasonTemplates = new java.util.HashSet<>();
                for (PlayerTaskInstance inst : result) {
                    if (inst.category() == BattlePassTask.TaskCategory.SEASON) {
                        seasonTemplates.add(inst.templateId());
                    }
                }
                List<PlayerTaskInstance> newlyCreated = new ArrayList<>();
                for (BattlePassTask task : configuration.tasks().season()) {
                    if (!seasonTemplates.contains(task.taskId())) {
                        PlayerTaskInstance inst = new PlayerTaskInstance(
                            uuid, uuid + "|SEASON|" + task.taskId(), task.taskId(),
                            BattlePassTask.TaskCategory.SEASON, task.requiredCount(),
                            0, false, today, 0);
                        result.add(inst);
                        newlyCreated.add(inst);
                    }
                }
                result.sort(Comparator.comparingInt(i -> i.category().ordinal()));
                List<PlayerTaskInstance> toSave = new ArrayList<>();
                for (PlayerTaskInstance inst : result) {
                    if (inst.category() != BattlePassTask.TaskCategory.SEASON
                        || newlyCreated.contains(inst)) {
                        toSave.add(inst);
                    }
                }
                if (!toSave.isEmpty()) {
                    repository.savePlayerTasks(uuid, seasonId, toSave);
                }
                cacheInstances(uuid, result);
                failedLoads.remove(uuid);
                return List.copyOf(result);
            } catch (SQLException e) {
                logger.warning("加载玩家任务实例失败: " + e.getMessage());
                failedLoads.add(uuid);
                return Collections.emptyList();
            }
        }
    }

    private Object playerLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, ignored -> new Object());
    }

    private void cacheInstances(UUID uuid, List<PlayerTaskInstance> instances) {
        List<PlayerTaskInstance> ordered = new ArrayList<>(instances);
        ordered.sort(Comparator.comparingInt(i -> i.category().ordinal()));
        cachedInstances.put(uuid, ordered);
        Map<String, Integer> instanceIndex = new HashMap<>();
        Map<String, List<String>> templateIndex = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            PlayerTaskInstance instance = ordered.get(i);
            instanceIndex.put(instance.instanceId(), i);
            templateIndex.computeIfAbsent(instance.templateId(), ignored -> new ArrayList<>())
                .add(instance.instanceId());
        }
        instanceIndexes.put(uuid, instanceIndex);
        templateIndexes.put(uuid, templateIndex);
    }

    /**
     * 判断指定玩家的进度数据是否因 DB 加载失败而不可用。
     * UI 推送层据此决定是否启动自动重试。
     *
     * @param uuid 玩家 UUID
     * @return {@code true} 表示上次加载失败，缓存中无可信数据
     */
    public boolean isLoadFailed(UUID uuid) {
        return failedLoads.contains(uuid);
    }

    /** 赛季时间窗口内才累积任务进度。 */
    public boolean isSeasonActive() {
        LocalDate today = LocalDate.now();
        var season = configuration.season();
        return !today.isBefore(season.startDate()) && !today.isAfter(season.endDate());
    }

    /** 进服预热缓存（异步线程调用，避免主线程 DB IO）。 */
    public void preload(Player player) {
        if (!active) return;
        try { repository.withPlayerWrite(player.getUniqueId(),()->{ getProgress(player); getTaskInstances(player); return null; }); }
        catch(SQLException e) { logger.warning("战令缓存等待存储恢复: " + player.getUniqueId()); }
        // 赛季结算奖励补发已由本体统一 PendingRewardService 在 PlayerJoinEvent 中自动处理
    }

    /** 退服时落盘并驱逐缓存。 */
    public void handleQuit(UUID uuid) {
        try { repository.withPlayerWrite(uuid,()->{ evictPlayer(uuid); return null; }); }
        catch(SQLException e) { logger.warning("战令退服保存失败: " + uuid); }
    }
    private void evictPlayer(UUID uuid) {
        flushPlayer(uuid);
        cachedProgress.remove(uuid);
        cachedInstances.remove(uuid);
        instanceIndexes.remove(uuid);
        templateIndexes.remove(uuid);
        playerLocks.remove(uuid);
        dirtyPlayers.remove(uuid);
        dirtyInstances.remove(uuid);
                taskEngine.clearPlayerLimits(uuid);

failedLoads.remove(uuid);
        pendingAuthApply.remove(uuid); uncertainAuthWrites.remove(uuid);
    }

    public List<PlayerTaskInstance> getActiveTaskInstances(Player player) {
        return getTaskInstances(player).stream()
            .filter(i -> !i.completed())
            .toList();
    }

    public void updateInstanceProgress(Player player, PlayerTaskInstance instance, int increment) {
        if (!active || increment <= 0 || !isSeasonActive()) return;
        UUID uuid = player.getUniqueId();
        boolean changed = false;
        synchronized (playerLock(uuid)) {
            List<PlayerTaskInstance> instances = cachedInstances.get(uuid);
            Map<String, Integer> indexes = instanceIndexes.get(uuid);
            if (instances == null || indexes == null) return;
            Integer idx = indexes.get(instance.instanceId());
            if (idx == null || idx < 0 || idx >= instances.size()) return;

            PlayerTaskInstance current = instances.get(idx);
            PlayerTaskInstance updatedInstance = current.withProgress(
                Math.min(current.currentProgress() + increment, current.targetCount()));
            boolean completedNow = updatedInstance.currentProgress() >= updatedInstance.targetCount()
                && !updatedInstance.completed();
            if (completedNow) {
                updatedInstance = updatedInstance.withCompleted(true);
                BattlePassTask task = findTask(updatedInstance.templateId());
                BattlePassPlayerProgress progress = cachedProgress.get(uuid);
                if (task != null && progress != null) {
                    int xp = Math.round(task.totalXpReward() * progress.xpMultiplier());
                    BattlePassPlayerProgress updatedProgress = addXp(progress, xp);
                    cachedProgress.put(uuid, updatedProgress);
                    dirtyPlayers.add(uuid);
                    persistProgressAsync(uuid);
                    logger.fine("任务完成: " + player.getName() + " task=" + task.taskId() + " xp=" + xp);
                }
            }
            instances.set(idx, updatedInstance);
            markDirtyInstance(uuid, updatedInstance.instanceId());
        }
    }

    public void updateTaskProgress(Player player, String taskId, int increment) {
        if (!active || increment <= 0) return;
        UUID uuid=player.getUniqueId(); String playerName=player.getName();
        synchronized (playerLock(uuid)) {
            List<PlayerTaskInstance> instances = cachedInstances.get(uuid);
            Map<String, Integer> indexes = instanceIndexes.get(uuid);
            Map<String, List<String>> templates = templateIndexes.get(uuid);
            if (instances == null || indexes == null || templates == null) return;
            List<String> instanceIds = templates.get(taskId);
            if (instanceIds == null) return;
            boolean completedNow = false;
            for (String instanceId : instanceIds) {
                Integer index = indexes.get(instanceId);
                if (index == null || index < 0 || index >= instances.size()) continue;
                PlayerTaskInstance current = instances.get(index);
                if (current.completed()) continue;
                PlayerTaskInstance updated = current.withProgress(
                    (int)Math.min((long)current.currentProgress() + increment, current.targetCount()));
                if (updated.currentProgress() >= updated.targetCount()) {
                    updated = updated.withCompleted(true);
                    completedNow = true;
                    BattlePassTask task = findTask(updated.templateId());
                    BattlePassPlayerProgress progress = cachedProgress.get(uuid);
                    if (task != null && progress != null) {
                        int xp = Math.round(task.totalXpReward() * progress.xpMultiplier());
                        cachedProgress.put(uuid, addXp(progress, xp));
                        dirtyPlayers.add(uuid);
                        logger.fine("任务完成: " + playerName + " task=" + task.taskId() + " xp=" + xp);
                    }
                }
                instances.set(index, updated);
                markDirtyInstance(uuid, updated.instanceId());
            }
            if (completedNow) persistProgressAsync(uuid);
        }
    }
    /** Receipt, task progress and XP commit together; no SQL or waiting on the publishing server thread. */
    public java.util.concurrent.CompletionStage<Void> acceptAuthFact(Player player,String task,int increment,String eventId,LocalDate businessDate) {
        UUID uuid=player.getUniqueId(); String season=configuration.season().seasonId();
        String fact=UUID.nameUUIDFromBytes((season+"/"+task+"/"+eventId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var result=new java.util.concurrent.CompletableFuture<Void>(); authReceipts.add(result);
        result.whenComplete((ignored,error)->authReceipts.remove(result));
        try { authFacts.execute(()->{
            try {
                repository.withPlayerWrite(uuid,()->{
                    if(!active) throw new SQLException("BattlePass closed");
                    if(!cachedProgress.containsKey(uuid) || !cachedInstances.containsKey(uuid)) {
                        getProgress(player); getTaskInstances(player);
                    }
                    var pending=pendingAuthApply.computeIfAbsent(uuid,key->new HashMap<>());
                    // Resolve prior ambiguous commits before another cache snapshot may be stored.
                    for(var entry:new ArrayList<>(pending.entrySet())) {
                        if(repository.receivedAuthFact(uuid,season,entry.getKey())) {
                            applyAuthDeltas(uuid,entry.getValue());
                        }
                        pending.remove(entry.getKey());
                    }
                    uncertainAuthWrites.remove(uuid);
                    if(repository.receivedAuthFact(uuid,season,fact)) return null;
                    resetPeriods(uuid,true,true);
                    AuthProjection projection=projectAuthFact(uuid,task,increment,businessDate);
                    pending.put(fact,projection.deltas());
                    try {
                        repository.saveAuthFact(fact,projection.progress(),projection.tasks(),!projection.deltas().isEmpty());
                        applyAuthDeltas(uuid,projection.deltas());
                        pending.remove(fact);
                    } catch(SQLException | RuntimeException e) { uncertainAuthWrites.add(uuid); throw e; }
                    return null;
                });
                result.complete(null);
            } catch(Exception e) { result.completeExceptionally(e); }
        }); } catch(java.util.concurrent.RejectedExecutionException e) { result.completeExceptionally(e); }
        return result;
    }
    private AuthProjection projectAuthFact(UUID uuid,String taskId,int increment,LocalDate date) throws SQLException {
        synchronized(playerLock(uuid)) {
            var progress=cachedProgress.get(uuid); var instances=cachedInstances.get(uuid);
            if(failedLoads.contains(uuid) || progress==null || instances==null) throw new SQLException("Player cache is not ready");
            var updated=new ArrayList<PlayerTaskInstance>();
            var deltas=new ArrayList<AuthTaskDelta>();
            boolean seasonDate=!date.isBefore(configuration.season().startDate()) && !date.isAfter(configuration.season().endDate());
            for(var current:instances) {
                boolean periodDate=current.category()==BattlePassTask.TaskCategory.SEASON || current.assignedDate()!=null
                    && (current.category()==BattlePassTask.TaskCategory.DAILY?date.equals(current.assignedDate())
                        :!date.isBefore(current.assignedDate()) && date.isBefore(current.assignedDate().plusDays(7)));
                if(!seasonDate || !periodDate || increment<=0 || !current.templateId().equals(taskId) || current.completed()) { updated.add(current); continue; }
                var template=findTask(taskId);
                int completionXp=template==null?0:Math.round(template.totalXpReward()*progress.xpMultiplier());
                var task=current.withProgress((int)Math.min((long)current.currentProgress()+increment,current.targetCount()));
                if(task.currentProgress()>=task.targetCount()) {
                    task=task.withCompleted(true); progress=addXp(progress,completionXp);
                }
                deltas.add(new AuthTaskDelta(current.instanceId(),task.currentProgress()-current.currentProgress(),completionXp));
                updated.add(task);
            }
            return new AuthProjection(progress,List.copyOf(updated),List.copyOf(deltas));
        }
    }
    private void applyAuthDeltas(UUID uuid,List<AuthTaskDelta> deltas) {
        synchronized(playerLock(uuid)) {
            var instances=cachedInstances.get(uuid); var indexes=instanceIndexes.get(uuid);
            if(instances==null || indexes==null) return;
            for(var delta:deltas) {
                Integer index=indexes.get(delta.instance());
                if(index==null || index<0 || index>=instances.size()) continue;
                var current=instances.get(index);
                if(current.completed()) continue;
                var updated=current.withProgress((int)Math.min((long)current.currentProgress()+delta.increment(),current.targetCount()));
                if(updated.currentProgress()>=updated.targetCount()) {
                    updated=updated.withCompleted(true); var progress=cachedProgress.get(uuid);
                    if(progress!=null) { cachedProgress.put(uuid,addXp(progress,delta.completionXp())); dirtyPlayers.add(uuid); }
                }
                instances.set(index,updated); markDirtyInstance(uuid,updated.instanceId());
            }
        }
    }

    public BattlePassPlayerProgress addXp(BattlePassPlayerProgress progress, int xp) {
        if (xp <= 0) return progress;
        int newXp = progress.currentXp() + xp;
        int newLevel = progress.currentLevel();
        var xpCurve = configuration.season().xpCurve();
        int maxLevel = configuration.season().maxLevel();

        // 升到 newLevel+1 级所需经验随等级变化，逐级扣减
        while (newLevel < maxLevel) {
            int required = xpCurve.xpRequiredForLevel(newLevel + 1);
            if (newXp < required) break;
            newXp -= required;
            newLevel++;
        }
        if (newLevel >= maxLevel) {
            // 满级后截断到当前级上限 - 1
            int cap = xpCurve.xpRequiredForLevelOr(maxLevel, Integer.MAX_VALUE);
            newXp = Math.min(newXp, Math.max(0, cap - 1));
        }
        return progress.withLevel(newLevel).withXp(newXp);
    }

    public enum ClaimResult {
        SUCCESS, LEVEL_NOT_REACHED, ALREADY_CLAIMED, NOT_FOUND
    }

    /** 一键领取所有已解锁等级中当前可领的全部档位奖励，返回成功领取的数量。 */
    public int claimAllRewards(Player player) {
        UUID uuid = player.getUniqueId();
        BattlePassPlayerProgress loaded = getProgress(player);
        List<java.util.concurrent.CompletableFuture<Boolean>> futures = new ArrayList<>();
        synchronized (playerLock(uuid)) {
            BattlePassPlayerProgress progress = cachedProgress.get(uuid);
            if (progress == null) progress = loaded;
            int currentLevel = progress.currentLevel();
            for (BattlePassReward reward : configuration.rewards()) {
                if (reward.level() > currentLevel) continue;
                if (reward.tier() == BattlePassReward.RewardTier.PREMIUM && !progress.unlockedPremium()) continue;
                if (reward.tier() == BattlePassReward.RewardTier.DELUXE && !progress.unlockedDeluxe()) continue;
                String rewardId = reward.rewardId();
                if (progress.claimedRewards().contains(rewardId)) continue;
                String seasonId = configuration.season().seasonId();
                java.util.concurrent.CompletableFuture<Boolean> future = rewardDispatcher.dispatch(player, reward);
                future.whenComplete((ok, err) -> {
                    if (err == null && Boolean.TRUE.equals(ok)) {
                        synchronized (playerLock(uuid)) {
                            BattlePassPlayerProgress p = cachedProgress.get(uuid);
                            if (p != null) {
                                cachedProgress.put(uuid, p.withClaimedReward(rewardId));
                                dirtyPlayers.add(uuid);
                            }
                        }
                        persistClaimAsync(uuid, rewardId);
                    } else {
                        logger.warning("战令奖励发放失败 [" + uuid + "/" + rewardId + "]"
                            + (err != null ? ": " + err.getMessage() : ""));
                    }
                });
                futures.add(future);
            }
        }
        // 锁外等待全部 dispatch 完成，返回真实成功数（而非发起数），供上层如实提示玩家
        if (!futures.isEmpty()) {
            java.util.concurrent.CompletableFuture
                .allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        }
        return (int) futures.stream().filter(f -> Boolean.TRUE.equals(f.getNow(false))).count();
    }

    /** 领取指定等级下当前可领的全部档位奖励。 */
    public ClaimResult claimRewards(Player player, int level) {
        UUID uuid = player.getUniqueId();
        BattlePassPlayerProgress loaded = getProgress(player);
        synchronized (playerLock(uuid)) {
            BattlePassPlayerProgress progress = cachedProgress.get(uuid);
            if (progress == null) progress = loaded;
            if (progress.currentLevel() < level) return ClaimResult.LEVEL_NOT_REACHED;
            boolean found = false;
            boolean claimed = false;
            for (BattlePassReward reward : rewardsByLevel.getOrDefault(level, Collections.emptyList())) {
                if (reward.tier() == BattlePassReward.RewardTier.PREMIUM && !progress.unlockedPremium()) continue;
                if (reward.tier() == BattlePassReward.RewardTier.DELUXE && !progress.unlockedDeluxe()) continue;
                found = true;
                String rewardId = reward.rewardId();
                if (progress.claimedRewards().contains(rewardId)) continue;
                rewardDispatcher.dispatch(player, reward).whenComplete((ok, err) -> {
                    if (err == null && Boolean.TRUE.equals(ok)) {
                        synchronized (playerLock(uuid)) {
                            BattlePassPlayerProgress p = cachedProgress.get(uuid);
                            if (p != null) {
                                cachedProgress.put(uuid, p.withClaimedReward(rewardId));
                                dirtyPlayers.add(uuid);
                            }
                        }
                        persistClaimAsync(uuid, rewardId);
                    } else {
                        logger.warning("战令奖励发放失败 [" + uuid + "/" + rewardId + "]"
                            + (err != null ? ": " + err.getMessage() : ""));
                    }
                });
                claimed = true;
            }
            if (claimed) {
                return ClaimResult.SUCCESS;
            }
            return found ? ClaimResult.ALREADY_CLAIMED : ClaimResult.NOT_FOUND;
        }
    }

    /** 领取成功后立即异步落盘，缩小崩服重复领取窗口。 */
    private void persistClaimAsync(UUID uuid, String rewardId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.saveClaimedReward(uuid, configuration.season().seasonId(), rewardId);
            } catch (SQLException e) {
                logger.warning("保存奖励领取记录失败 [" + uuid + "/" + rewardId + "]: " + e.getMessage());
            }
        });
    }

    /** 关键操作后立即异步落盘进度，缩小崩服数据丢失窗口。 */
    private void persistProgressAsync(UUID uuid) {
        if (failedLoads.contains(uuid)) return;
        BattlePassPlayerProgress progress = cachedProgress.get(uuid);
        if (progress == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                saveCurrentProgress(uuid);
                if (cachedProgress.get(uuid) == progress) {
                    dirtyPlayers.remove(uuid);
                }
            } catch (SQLException e) {
                logger.warning("即时保存战令进度失败 [" + uuid + "]: " + e.getMessage());
            }
        });
    }
    private void saveCurrentProgress(UUID uuid) throws SQLException {
        repository.withPlayerWrite(uuid,()->{
            if(uncertainAuthWrites.contains(uuid)) throw new SQLException("Authentication fact commit requires reconciliation");
            var current=cachedProgress.get(uuid);
            if(current!=null) repository.saveProgress(current);
            return null;
        });
    }

    public boolean unlockTier(Player player, BattlePassPlayerProgress.PassTier tier) {
        UUID uuid = player.getUniqueId();
        boolean changed = false;
        synchronized (playerLock(uuid)) {
            BattlePassPlayerProgress progress = getProgress(player);
            if (progress.passTier().ordinal() >= tier.ordinal()) return false;
            cachedProgress.put(uuid, progress.withPassTier(tier));
            dirtyPlayers.add(uuid);
            persistProgressAsync(uuid);
            changed = true;
        }
        if (changed) notifyUiRefresh(uuid, false);
        return changed;
    }


    /**
     * 管理员给玩家增加指定经验值，自动升级。
     *
     * @param player 目标玩家
     * @param xp     增加的经验值（正数）
     * @return 实际增加后的总经验（当前等级内经验 + 升级经过的每级所需经验之和）
     */
    public int addXp(Player player, int xp) {
        if (xp <= 0) return 0;
        UUID uuid = player.getUniqueId();
        int result;
        boolean changed;
        synchronized (playerLock(uuid)) {
            BattlePassPlayerProgress progress = getProgress(player);
            BattlePassPlayerProgress updated = addXp(progress, xp);
            cachedProgress.put(uuid, updated);
            dirtyPlayers.add(uuid);
            persistProgressAsync(uuid);
            // 累加升级经过的每级实际所需经验 + 当前级内经验
            var xpCurve = configuration.season().xpCurve();
            int sum = updated.currentXp();
            for (int lv = progress.currentLevel() + 1; lv <= updated.currentLevel(); lv++) {
                sum += xpCurve.xpRequiredForLevelOr(lv, 0);
            }
            result = sum;
            changed = updated != progress;
        }
        if (changed) notifyUiRefresh(uuid, false);
        return result;
    }


    /**
     * 管理员设置玩家当前等级内的经验值（不触发升级，超出部分截断）。
     *
     * @param player 目标玩家
     * @param xp     目标经验值
     */
    public void setXp(Player player, int xp) {
        UUID uuid = player.getUniqueId();
        boolean changed;
        synchronized (playerLock(uuid)) {
            var xpCurve = configuration.season().xpCurve();
            BattlePassPlayerProgress progress = getProgress(player);
            // 当前级所需经验作为上限，超出截断到 required - 1
            int required = xpCurve.xpRequiredForLevelOr(progress.currentLevel() + 1, Integer.MAX_VALUE);
            int clamped = Math.max(0, Math.min(xp, Math.max(0, required - 1)));
            BattlePassPlayerProgress updated = progress.withXp(clamped);
            cachedProgress.put(uuid, updated);
            dirtyPlayers.add(uuid);
            persistProgressAsync(uuid);
            changed = updated != progress;
        }
        if (changed) notifyUiRefresh(uuid, false);
    }


    /**
     * 管理员扣除玩家经验值，可能降级。
     *
     * @param player 目标玩家
     * @param xp     扣除的经验值（正数）
     */
    public void removeXp(Player player, int xp) {
        if (xp <= 0) return;
        UUID uuid = player.getUniqueId();
        boolean changed;
        synchronized (playerLock(uuid)) {
            BattlePassPlayerProgress progress = getProgress(player);
            var xpCurve = configuration.season().xpCurve();
            int maxLevel = configuration.season().maxLevel();
            // 计算玩家从 1 级开始累积的总经验
            int totalXp = progress.currentXp();
            for (int lv = 2; lv <= progress.currentLevel(); lv++) {
                totalXp += xpCurve.xpRequiredForLevelOr(lv, 0);
            }
            int newTotal = Math.max(0, totalXp - xp);
            // 从 1 级逐级累加确定新等级与新级内经验
            int newLevel = 1;
            int newXp = newTotal;
            while (newLevel < maxLevel) {
                int required = xpCurve.xpRequiredForLevelOr(newLevel + 1, Integer.MAX_VALUE);
                if (newXp < required) break;
                newXp -= required;
                newLevel++;
            }
            if (newLevel < 1) {
                newLevel = 1;
                newXp = 0;
            }
            BattlePassPlayerProgress updated = progress.withLevel(newLevel).withXp(newXp);
            cachedProgress.put(uuid, updated);
            dirtyPlayers.add(uuid);
            persistProgressAsync(uuid);
            changed = updated != progress;
        }
        if (changed) notifyUiRefresh(uuid, false);
    }


    /**
     * 管理员直接设置玩家等级，经验清零。
     *
     * @param player 目标玩家
     * @param level  目标等级
     */
    public void setLevel(Player player, int level) {
        UUID uuid = player.getUniqueId();
        boolean changed;
        synchronized (playerLock(uuid)) {
            int maxLevel = configuration.season().maxLevel();
            int clamped = Math.max(1, Math.min(level, maxLevel));
            BattlePassPlayerProgress progress = getProgress(player);
            BattlePassPlayerProgress updated = progress.withLevel(clamped).withXp(0);
            cachedProgress.put(uuid, updated);
            dirtyPlayers.add(uuid);
            persistProgressAsync(uuid);
            changed = updated != progress;
        }
        if (changed) notifyUiRefresh(uuid, false);
    }


    public void resetPlayerProgress(UUID playerUuid) {
        try {
            repository.resetPlayerProgress(playerUuid, configuration.season().seasonId());
        } catch (SQLException e) {
            logger.warning("重置战令进度失败: " + e.getMessage());
        }
        cachedProgress.remove(playerUuid);
        cachedInstances.remove(playerUuid);
        instanceIndexes.remove(playerUuid);
        templateIndexes.remove(playerUuid);
        playerLocks.remove(playerUuid);
        dirtyPlayers.remove(playerUuid);
        dirtyInstances.remove(playerUuid);
                taskEngine.clearPlayerLimits(playerUuid);

failedLoads.remove(playerUuid);
    }

    public @Nullable BattlePassTask findTask(String taskId) {
        return taskIndex.get(taskId);
    }

    public BattlePassModuleConfiguration configuration() {
        return configuration;
    }

    public BattlePassRepository repository() {
        return repository;
    }

    private void flushDirtyProgress() {
        for (UUID uuid : new ArrayList<>(dirtyPlayers)) {
            if (failedLoads.contains(uuid)) continue;
            if (!dirtyPlayers.remove(uuid)) continue;
            BattlePassPlayerProgress progress = cachedProgress.get(uuid);
            if (progress == null) continue;
            try {
                saveCurrentProgress(uuid);
            } catch (SQLException e) {
                dirtyPlayers.add(uuid);
                logger.warning("进度保存失败 [" + uuid + "]: " + e.getMessage());
            }
        }
        for (UUID uuid : new ArrayList<>(dirtyInstances.keySet())) {
            flushDirtyInstances(uuid);
        }
    }

    private void markDirtyInstance(UUID uuid, String instanceId) {
        dirtyInstances.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(instanceId);
    }

    private void markDirtyInstances(UUID uuid, Set<String> instanceIds) {
        if (!instanceIds.isEmpty()) {
            dirtyInstances.compute(uuid, (ignored, current) -> {
                if (current == null) current = ConcurrentHashMap.newKeySet();
                current.addAll(instanceIds);
                return current;
            });
        }
    }

    private void flushDirtyInstances(UUID uuid) {
        try { repository.withPlayerWrite(uuid,()->{
            if(uncertainAuthWrites.contains(uuid)) throw new SQLException("Authentication fact commit requires reconciliation");
            flushDirtyInstancesLocked(uuid); return null;
        }); } catch(SQLException e) { logger.warning("任务保存等待认证事件确认: " + uuid); }
    }
    private void flushDirtyInstancesLocked(UUID uuid) {
        if (failedLoads.contains(uuid)) return;
        List<PlayerTaskInstance> instances;
        Set<String> ids;
        synchronized (playerLock(uuid)) {
            Set<String> dirty = dirtyInstances.get(uuid);
            if (dirty == null || dirty.isEmpty()) return;
            ids = new HashSet<>();
            dirtyInstances.computeIfPresent(uuid, (ignored, current) -> {
                ids.addAll(current);
                return null;
            });
            if (ids.isEmpty()) return;
            List<PlayerTaskInstance> cached = cachedInstances.get(uuid);
            if (cached == null) {
                markDirtyInstances(uuid, ids);
                return;
            }
            Map<String, Integer> indexes = instanceIndexes.get(uuid);
            instances = new ArrayList<>();
            for (String id : ids) {
                Integer index = indexes == null ? null : indexes.get(id);
                if (index != null && index >= 0 && index < cached.size()) {
                    instances.add(cached.get(index));
                }
            }
        }
        if (instances.isEmpty()) return;
        try {
            repository.savePlayerTasks(uuid, configuration.season().seasonId(), instances);
        } catch (SQLException e) {
            markDirtyInstances(uuid, ids);
            logger.warning("任务实例保存失败 [" + uuid + "]: " + e.getMessage());
        }
    }

    private void flushPlayer(UUID uuid) {
        if (failedLoads.contains(uuid)) return;
        BattlePassPlayerProgress progress = cachedProgress.get(uuid);
        if (progress != null && dirtyPlayers.contains(uuid)) {
            try {
                saveCurrentProgress(uuid);
                dirtyPlayers.remove(uuid);
            } catch (SQLException e) {
                logger.warning("玩家进度保存失败 [" + uuid + "]: " + e.getMessage());
            }
        }
        flushDirtyInstances(uuid);
    }

    private void checkResets() {
        LocalDate today = LocalDate.now();
        // 赛季结束自动结算检测
        if (settlementService != null) {
            try {
                settlementService.checkAndAutoSettle();
            } catch (Exception e) {
                logger.warning("赛季自动结算检查失败: " + e.getMessage());
            }
        }
        for (Map.Entry<UUID, BattlePassPlayerProgress> entry : cachedProgress.entrySet()) {
            BattlePassPlayerProgress progress = entry.getValue();
            UUID uuid = entry.getKey();
            boolean dailyNeedsReset = progress.lastDailyResetDate() == null || !progress.lastDailyResetDate().equals(today);
            boolean weeklyNeedsReset = progress.lastWeeklyResetDate() == null
                || ChronoUnit.DAYS.between(progress.lastWeeklyResetDate(), today) >= 7;
            if (dailyNeedsReset || weeklyNeedsReset) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performReset(uuid, dailyNeedsReset, weeklyNeedsReset));
            }
        }
    }

    private void performReset(UUID playerUuid,boolean daily,boolean weekly) {
        try { resetPeriods(playerUuid,daily,weekly); }
        catch(SQLException e) { logger.warning("任务周期重置待重试 ["+playerUuid+"]: "+e.getMessage()); }
    }

    private void resetPeriods(UUID playerUuid,boolean requestedDaily,boolean requestedWeekly) throws SQLException {
        boolean changed=repository.withPlayerWrite(playerUuid,()->{
            if(!active || uncertainAuthWrites.contains(playerUuid)) throw new SQLException("Authentication writes have not drained");
            var before=cachedProgress.get(playerUuid);
            if(before==null) return false;
            LocalDate today=LocalDate.now();
            boolean daily=requestedDaily && !today.equals(before.lastDailyResetDate());
            boolean weekly=requestedWeekly && (before.lastWeeklyResetDate()==null || ChronoUnit.DAYS.between(before.lastWeeklyResetDate(),today)>=7);
            if(!daily && !weekly) return false;
            String season=configuration.season().seasonId();
            // Database work owns only the worker-side write fence, never the lock used by game-thread mutations.
            if(daily) repository.deletePlayerTasksByCategory(playerUuid,season,BattlePassTask.TaskCategory.DAILY);
            if(weekly) repository.deletePlayerTasksByCategory(playerUuid,season,BattlePassTask.TaskCategory.WEEKLY);
            synchronized(playerLock(playerUuid)) {
                if(!active) throw new SQLException("BattlePass closed");
                var progress=cachedProgress.get(playerUuid);
                if(progress==null) return false;
                var tasks=new ArrayList<>(cachedInstances.getOrDefault(playerUuid,List.of()));
                if(daily) {
                    tasks.removeIf(task->task.category()==BattlePassTask.TaskCategory.DAILY);
                    var assigned=assignmentService.assignDailyTasks(playerUuid,configuration.tasks().daily(),configuration.tasks().dailyCount(),today);
                    tasks.addAll(assigned);
                    assigned.forEach(task->markDirtyInstance(playerUuid,task.instanceId()));
                    progress=progress.withDailyReset(today);
                }
                if(weekly) {
                    tasks.removeIf(task->task.category()==BattlePassTask.TaskCategory.WEEKLY);
                    int week=progress.currentWeekNumber()+1;
                    var assigned=assignmentService.assignWeeklyTasks(playerUuid,configuration.tasks().weekly(),configuration.tasks().weeklyCount(),week,today);
                    tasks.addAll(assigned);
                    assigned.forEach(task->markDirtyInstance(playerUuid,task.instanceId()));
                    progress=progress.withWeeklyReset(today,week);
                }
                tasks.sort(Comparator.comparingInt(task->task.category().ordinal()));
                cacheInstances(playerUuid,tasks); cachedProgress.put(playerUuid,progress); dirtyPlayers.add(playerUuid);
            }
            saveCurrentProgress(playerUuid); flushDirtyInstances(playerUuid);
            return true;
        });
        if(changed && active) Bukkit.getScheduler().runTask(plugin,()->{ if(active) notifyUiRefresh(playerUuid,true); });
    }
}
