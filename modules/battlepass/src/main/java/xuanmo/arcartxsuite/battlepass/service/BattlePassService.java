package xuanmo.arcartxsuite.battlepass.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.BuildEdition;
import xuanmo.arcartxsuite.api.auth.AuthAccess;
import xuanmo.arcartxsuite.api.auth.AuthSnapshot;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.api.item.PendingRewardService;
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassSeason;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;

public final class BattlePassService {
    private static final long SHARED_TASK_BATCH_TICKS = 10L;

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
    private AbyssTaskFeed abyssTaskFeed;

    private final Map<UUID, BattlePassPlayerProgress> cachedProgress = new ConcurrentHashMap<>();
    private final Map<UUID, List<PlayerTaskInstance>> cachedInstances = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> instanceIndexes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, List<String>>> templateIndexes = new ConcurrentHashMap<>();
    private final Map<String, BattlePassTask> taskIndex;
    private final Map<Integer, List<BattlePassReward>> rewardsByLevel;
    // Only in-memory updates use these monitors. The repository owns worker-side I/O serialization.
    private final Object[] playerLocks = new Object[256];
    { Arrays.setAll(playerLocks, index -> new Object()); }
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> dirtyInstances = new ConcurrentHashMap<>();
    private final Map<UUID, Set<BattlePassTask.TaskCategory>> dirtyPeriods = new ConcurrentHashMap<>();
    private final Set<UUID> pendingEvictions = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, ColdTaskLoad> coldTaskLoads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ProgressSave> progressSaves = new ConcurrentHashMap<>();
    // 加载失败的玩家集合：这些玩家的缓存数据不可信，flushDirtyProgress 必须跳过，避免空进度覆盖 DB 真实数据
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();
    private BukkitTask saveTask;
    private BukkitTask resetTask;
    private volatile boolean active;
    private boolean stopping;
    private final ModuleCallback callbacks = new ModuleCallback(() -> {});
    private final ModuleExecutor finalizer = new ModuleExecutor(0,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r -> {
        var thread = new Thread(r,"AXS-BattlePass-Close");thread.setDaemon(true);return thread;
    });
    private final ModuleExecutor authFacts=new ModuleExecutor(2,2,0,java.util.concurrent.TimeUnit.MILLISECONDS,
        new java.util.concurrent.ArrayBlockingQueue<>(128),r->{ Thread t=new Thread(r,"AXS-BattlePassAuth"); t.setDaemon(true); return t; });
    // Two extra runnable slots let active player batches yield fairly; total accepted requests stay at 130.
    private final ModuleExecutor business = new ModuleExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(130),
        work -> new BusinessWorker(this,work));
    private static final class BusinessWorker extends Thread {
        private final BattlePassService owner;
        private UUID player;
        private boolean refresh;
        private boolean structure;
        BusinessWorker(BattlePassService owner,Runnable work){super(work,"AXS-BattlePass-Business");this.owner=owner;setDaemon(true);}
    }
    private final Map<UUID,BusinessBatch> businessBatches=new HashMap<>();
    private int businessOutstanding;
    private final CurveRuntime xpCurveRuntime=new CurveRuntime();
    private final class CurveRuntime {
        // The existing configuration bounds levels at 10,000; keep this allocation bounded.
        private final java.util.concurrent.atomic.AtomicIntegerArray values=new java.util.concurrent.atomic.AtomicIntegerArray(10002);
        private final CompletableFuture<Void> prepared=new CompletableFuture<>();
        private volatile boolean ready;
        void prepare(){
            try{
                int last=Math.min(values.length()-1,configuration.season().maxLevel()+1);
                for(int level=2;level<=last;level++){
                    if(!active)throw new java.util.concurrent.CancellationException("BattlePass closed during curve preparation");
                    values.set(level,configuration.season().xpCurve().xpRequiredForLevel(level));
                }
                ready=true;prepared.complete(null);
            }catch(Throwable failure){
                prepared.completeExceptionally(failure);
                if(active)logger.log(java.util.logging.Level.WARNING,"BattlePass 经验曲线后台准备失败",failure);
            }
        }
        int xpRequiredForLevel(int level){
            if(level<=1)return 0;
            int cached=level<values.length()?values.get(level):0;
            if(cached>0)return cached;
            if(!active)throw new IllegalStateException("XP curve level is not prepared: "+level);
            int result=configuration.season().xpCurve().xpRequiredForLevel(level);
            if(level<values.length())values.compareAndSet(level,0,result);
            return result;
        }
        int xpRequiredForLevelOr(int level,int fallback){
            try{return xpRequiredForLevel(level);}catch(RuntimeException failure){if(ownBusinessWorker()&&!active)throw failure;return fallback;}
        }
    }
    private static final class BusinessRequest<T> {
        final boolean load;final boolean barrier;final java.util.concurrent.Callable<T> operation;final CompletableFuture<T> result=new CompletableFuture<>();
        T value;Throwable failure;
        BusinessRequest(boolean load,boolean barrier,java.util.concurrent.Callable<T> operation){this.load=load;this.barrier=barrier;this.operation=operation;}
        void apply()throws Exception{value=operation.call();}
        void finish(Throwable commitFailure){
            Throwable error=failure==null?commitFailure:failure;
            if(error==null)result.complete(value);else result.completeExceptionally(error);
        }
    }
    private final class BusinessBatch implements Runnable {
        final UUID player;final java.util.ArrayDeque<BusinessRequest<?>> pending=new java.util.ArrayDeque<>();
        BusinessBatch(UUID player){this.player=player;}
        @Override public void run(){
            while(true){
                var group=new ArrayList<BusinessRequest<?>>(32);
                synchronized(businessBatches){
                    while(group.size()<32 && !pending.isEmpty()){
                        if(!group.isEmpty() && pending.peekFirst().barrier)break;
                        var request=pending.removeFirst();group.add(request);if(request.barrier)break;
                    }
                    if(group.isEmpty()){businessBatches.remove(player,this);return;}
                }
                Throwable commitFailure=null;
                var worker=(BusinessWorker)Thread.currentThread();worker.player=player;worker.refresh=false;worker.structure=false;
                BattlePassRepository.Write<Void> applyGroup = () -> {
                    boolean applied=false;boolean unavailable=false;
                    for(var request:group){
                        try{
                            if(request.load){
                                if(unavailable)throw new SQLException("Player battle pass data is not ready");
                                getProgress(player);
                                if(failedLoads.contains(player)){unavailable=true;throw new SQLException("Player battle pass data is not ready");}
                            }
                            request.apply();applied=true;if(!request.load)unavailable=false;
                        }catch(Throwable failure){
                            request.failure=failure;
                            if (BuildEdition.CROSS_SERVER_INCLUDED && sharedProgressWrite.get() != null) {
                                throw new SQLException("BattlePass progress request failed", failure);
                            }
                        }
                    }
                    if(applied){
                        if(failedLoads.contains(player))throw new SQLException("Player battle pass data is not ready for commit");
                        saveCurrentProgress(player);
                    }
                    return null;
                };
                try {
                    if (group.get(0).barrier) repository.withPlayerWrite(player, applyGroup);
                    else withAuthoritativeProgress(player, applyGroup);
                } catch (Throwable failure) { commitFailure = failure; }
                boolean refresh=worker.refresh;boolean structure=worker.structure;worker.player=null;
                if(commitFailure==null && refresh && group.stream().anyMatch(request->request.failure==null)){
                    try{notifyUiRefresh(player,structure);}
                    catch(Throwable ignored){/* Optional UI dispatch cannot strand committed requests during host shutdown. */}
                }
                synchronized(businessBatches){businessOutstanding-=group.size();}
                for(var request:group)request.finish(commitFailure);
                synchronized(businessBatches){
                    if(pending.isEmpty()){businessBatches.remove(player,this);return;}
                    if(!stopping){
                        try{business.execute(this);return;}
                        catch(java.util.concurrent.RejectedExecutionException closing){/* Drain accepted requests inline during shutdown. */}
                    }
                }
            }
        }
    }
    private final Set<UUID> warmingPlayers = ConcurrentHashMap.newKeySet();
    private final java.util.Queue<Player> warmupQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicBoolean warmupScheduled = new java.util.concurrent.atomic.AtomicBoolean();
    private final Map<UUID,Long> nextWarmup = new ConcurrentHashMap<>();
    private final Set<UUID> uncertainAuthWrites=ConcurrentHashMap.newKeySet();
    private static final class SharedProgressWrite {
        final UUID player;
        boolean tasksLoaded;

        SharedProgressWrite(UUID player) { this.player = player; }
    }
    private final ThreadLocal<SharedProgressWrite> sharedProgressWrite =
        BuildEdition.CROSS_SERVER_INCLUDED ? new ThreadLocal<>() : null;
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
        PendingRewardService pendingRewardService
    ) {
        this.plugin = plugin;
        business.allowCoreThreadTimeOut(true);
        this.configuration = configuration;
        this.repository = repository;
        this.logger = logger;
        this.rewardDispatcher = new BattlePassRewardDispatcher(repository, pendingRewardService);
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
        this.settlementService = new SeasonSettlementService(configuration, repository, this, logger);
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void setAbyssResultProvider(Supplier<xuanmo.arcartxsuite.api.capability.AbyssRunResultAccess> provider,
                                       String nodeId) {
        abyssTaskFeed = new AbyssTaskFeed(this, repository, provider, nodeId, logger);
    }

    boolean catchUpAbyssTasks() {
        return abyssTaskFeed == null || abyssTaskFeed.poll();
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
        if (listener == null || !active) return;
        if(Thread.currentThread() instanceof BusinessWorker worker && worker.owner==this && playerUuid.equals(worker.player)){
            worker.refresh=true;worker.structure|=structureChanged;return;
        }
        if(!Bukkit.isPrimaryThread()){onMain(()->notifyUiRefresh(playerUuid,structureChanged));return;}
        try {
            listener.onRefresh(playerUuid, structureChanged);
        } catch (Exception e) {
            logger.warning("UI 刷新回调失败 [" + playerUuid + "]: " + e.getMessage());
        }
    }

    public void start() {
        active = true;
        business.execute(xpCurveRuntime::prepare);
        taskEngine.start(eventBusProvider);
        if (abyssTaskFeed != null) abyssTaskFeed.start(plugin);
        List<? extends Player> onlinePlayers = List.copyOf(Bukkit.getOnlinePlayers());
        if (!onlinePlayers.isEmpty()) {
            async( () -> {
                for (Player player : onlinePlayers) {
                    if (player.isOnline()) preload(player);
                }
            });
        }
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, guard(this::flushDirtyProgress), 300L, 300L);
        resetTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, guard(this::checkResets), 6000L, 6000L);
    }

    public synchronized java.util.concurrent.CompletionStage<Void> shutdown() {
        if (stopping) return finalizer.stopped();
        synchronized(businessBatches){stopping = true;active = false;}
        callbacks.close();
        try {
        if (abyssTaskFeed != null) abyssTaskFeed.stop();
        taskEngine.shutdown();
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        } finally {
            callbacks.stopped().whenComplete((ignored, failure) -> {business.shutdown();authFacts.shutdown();});
            CompletableFuture.allOf(authFacts.stopped().toCompletableFuture(),business.stopped().toCompletableFuture()).whenComplete((ignored, failure) -> {
                try { finalizer.execute(this::finishShutdown); }
                finally { finalizer.shutdown(); }
            });
        }
        return finalizer.stopped();
    }
    private void finishShutdown() {
        if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) {
            try { flushSharedTaskLoads(); }
            catch (SQLException failure) { logger.log(java.util.logging.Level.WARNING, "战令停用时任务尚未保存", failure); }
        }
        flushDirtyProgress();
        cachedProgress.clear();
        cachedInstances.clear();
        instanceIndexes.clear();
        templateIndexes.clear();
        dirtyPlayers.clear();
        dirtyInstances.clear();
        dirtyPeriods.clear();
        pendingEvictions.clear();
        coldTaskLoads.clear();
        progressSaves.clear();
        failedLoads.clear();
        pendingAuthApply.clear(); uncertainAuthWrites.clear();
        warmingPlayers.clear(); warmupQueue.clear(); nextWarmup.clear();
    }

    public boolean isActive() { return active; }
    public CompletionStage<Void> preparationCompleted(){return xpCurveRuntime.prepared.copy();}
    public int xpRequiredForLevelOr(int level,int fallback){
        if(level<=1)return 0;
        if(level>=xpCurveRuntime.values.length())return fallback;
        int cached=xpCurveRuntime.values.get(level);return cached>0?cached:fallback;
    }
    public CompletionStage<Void> databaseStopped() { return finalizer.stopped(); }
    public <T> T callApi(Supplier<T> operation,T unavailable) { return callbacks.call(operation,unavailable); }
    public Runnable guard(Runnable action) { return () -> callbacks.call(() -> { if(active) action.run(); return null; },null); }
    public void async(Runnable action) { if(active) Bukkit.getScheduler().runTaskAsynchronously(plugin,guard(action)); }
    public void laterAsync(Runnable action,long ticks) { if(active) Bukkit.getScheduler().runTaskLaterAsynchronously(plugin,guard(action),ticks); }
    public void onMain(Runnable action) { if(active) Bukkit.getScheduler().runTask(plugin,guard(action)); }

    private boolean ownBusinessWorker(){return Thread.currentThread() instanceof BusinessWorker worker && worker.owner==this && worker.player!=null;}
    private boolean canMutate(){return active || ownBusinessWorker();}

    private <T> CompletionStage<T> change(UUID player,boolean loadProgress,java.util.concurrent.Callable<T> operation) {
        return change(player,loadProgress,false,operation);
    }

    private <T> CompletionStage<T> change(UUID player,boolean loadProgress,boolean barrier,java.util.concurrent.Callable<T> operation) {
        var request=new BusinessRequest<T>(loadProgress,barrier,operation);
        synchronized(businessBatches){
            if(!active)return CompletableFuture.failedFuture(new IllegalStateException("BattlePass closed"));
            if(!xpCurveRuntime.ready)return CompletableFuture.failedFuture(new IllegalStateException("BattlePass XP curve is not ready"));
            if(businessOutstanding>=130)return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException("BattlePass business queue is full"));
            businessOutstanding++;
            BusinessBatch batch=businessBatches.get(player);boolean schedule=batch==null;
            if(schedule){batch=new BusinessBatch(player);businessBatches.put(player,batch);}
            batch.pending.addLast(request);
            if(schedule){
                try{business.execute(batch);}
                catch(Throwable full){
                    businessBatches.remove(player,batch);var rejected=List.copyOf(batch.pending);batch.pending.clear();businessOutstanding-=rejected.size();
                    for(var rejectedRequest:rejected)rejectedRequest.result.completeExceptionally(full);
                }
            }
        }
        return request.result.copy();
    }

    public CompletionStage<Integer> addXpAsync(Player player,int xp){return change(player.getUniqueId(),true,()->addXp(player,xp));}
    public CompletionStage<Void> setXpAsync(Player player,int xp){return change(player.getUniqueId(),true,()->{setXp(player,xp);return null;});}
    public CompletionStage<Void> removeXpAsync(Player player,int xp){return change(player.getUniqueId(),true,()->{removeXp(player,xp);return null;});}
    public CompletionStage<Void> setLevelAsync(Player player,int level){return change(player.getUniqueId(),true,()->{setLevel(player,level);return null;});}
    public CompletionStage<Boolean> unlockTierAsync(Player player,BattlePassPlayerProgress.PassTier tier){return change(player.getUniqueId(),true,()->unlockTier(player,tier));}
    public CompletionStage<Integer> claimAllRewardsAsync(Player player){return change(player.getUniqueId(),true,true,()->{
        var claim=claimAll(player);if(claim.retry())throw new IllegalStateException("BattlePass reward acceptance is not confirmed");return claim.accepted();
    });}

    public CompletionStage<ClaimResult> claimLevelAsync(Player player, int level, BooleanSupplier requestValid) {
        var allowed = interactiveClaimGuard(player, requestValid);
        if (!allowed.getAsBoolean()) return CompletableFuture.completedFuture(ClaimResult.RETRY);
        return change(player.getUniqueId(), false, true, () -> claimRewards(player, level, allowed));
    }

    public CompletionStage<ClaimBatch> claimBatchAsync(Player player, BooleanSupplier requestValid) {
        var allowed = interactiveClaimGuard(player, requestValid);
        if (!allowed.getAsBoolean()) return CompletableFuture.completedFuture(new ClaimBatch(0, true));
        return change(player.getUniqueId(), false, true, () -> claimAll(player, allowed));
    }

    private static BooleanSupplier interactiveClaimGuard(Player player, BooleanSupplier requestValid) {
        UUID id = player.getUniqueId();
        AuthSnapshot session = AuthAccess.snapshot(id);
        return () -> session.admitted() && player.isOnline()
            && player.hasPermission("arcartxsuite.battlepass.use")
            && sameTaskSession(session, AuthAccess.snapshot(id)) && requestValid.getAsBoolean();
    }
    public CompletionStage<Void> updateTaskProgressAsync(Player player,String task,int increment){
        if(!taskIndex.containsKey(task))return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown task: "+task));
        return change(player.getUniqueId(),true,()->{
            UUID id=player.getUniqueId();getTaskInstances(player);
            if(failedLoads.contains(id)||!cachedInstances.containsKey(id))throw new SQLException("Player tasks are not ready");
            updateTaskProgressNow(player,task,increment);notifyUiRefresh(id,false);return null;
        });
    }
    public CompletionStage<Void> resetProgressAsync(UUID player){return change(player,false,true,()->{
        saveCurrentProgress(player);
        if(!resetPlayerProgress(player)){
            synchronized(playerLock(player)){clearPlayerCache(player);}
            throw new IllegalStateException("BattlePass reset is not confirmed");
        }
        notifyUiRefresh(player,true);return null;
    });}

    /** PAPI reads only trusted cached data; at most one cold load per online player is queued. */
    public @Nullable BattlePassPlayerProgress progressSnapshot(Player player) {
        UUID uuid=player.getUniqueId();
        var progress=failedLoads.contains(uuid)?null:cachedProgress.get(uuid);
        if ((progress==null || !cachedInstances.containsKey(uuid)) && active && player.isOnline() && System.currentTimeMillis()>=nextWarmup.getOrDefault(uuid,0L)) {
            if(warmingPlayers.add(uuid)) warmupQueue.add(player);
            scheduleWarmups();
        }
        return progress;
    }

    private void scheduleWarmups() {
        if(!active || warmupQueue.isEmpty() || !warmupScheduled.compareAndSet(false,true)) return;
        try { async(() -> {
            try {
                for(int i=0;i<32 && active;i++) {
                    Player player=warmupQueue.poll();if(player==null) break;
                    UUID uuid=player.getUniqueId();
                    try { if(player.isOnline()) preload(player); }
                    finally {
                        if(active && player.isOnline() && (failedLoads.contains(uuid) || !cachedProgress.containsKey(uuid) || !cachedInstances.containsKey(uuid))) {
                            nextWarmup.put(uuid,System.currentTimeMillis()+5000L);
                        }
                        warmingPlayers.remove(uuid);
                    }
                }
            } finally { warmupScheduled.set(false);scheduleWarmups(); }
        }); } catch(RuntimeException | LinkageError failure) {
            warmupScheduled.set(false);logger.fine("战令缓存补载未调度: "+failure.getMessage());
        }
    }

    public int activeTasksSnapshot(UUID player) {
        var tasks=cachedInstances.get(player);
        if(tasks==null) return 0;
        int count=0;for(var task:tasks)if(!task.completed())count++;
        return count;
    }

    public BattlePassPlayerProgress getProgress(Player player) {
        return getProgress(player.getUniqueId());
    }

    private BattlePassPlayerProgress getProgress(UUID uuid) {
        BattlePassPlayerProgress cached = cachedProgress.get(uuid);
        if (cached != null && !failedLoads.contains(uuid)) return cached;
        try {
            return repository.withPlayerWrite(uuid, () -> {
                BattlePassPlayerProgress ready = cachedProgress.get(uuid);
                return ready != null && !failedLoads.contains(uuid) ? ready : loadProgress(uuid);
            });
        } catch (SQLException failure) {
            logger.warning("加载战令进度失败: " + failure.getMessage());
            failedLoads.add(uuid);
            return new BattlePassPlayerProgress(uuid, configuration.season().seasonId());
        }
    }

    /**
     * MySQL writers operate on current persisted state. The local fence also keeps the
     * worker's projection separate from concurrent cache eviction and local reads.
     */
    private <T> T withAuthoritativeProgress(UUID uuid, BattlePassRepository.Write<T> work) throws SQLException {
        return repository.withPlayerWrite(uuid, () -> {
            if (!BuildEdition.CROSS_SERVER_INCLUDED || !repository.sharedProgress() || ownsSharedProgress(uuid)) return work.run();
            if (sharedProgressWrite.get() != null) throw new IllegalStateException("BattlePass write targets another player");
            String season = configuration.season().seasonId();
            try {
                return repository.withProgressWrite(uuid, season, () -> {
                    var current = repository.loadProgress(uuid, season);
                    synchronized (playerLock(uuid)) {
                        cachedProgress.put(uuid, current);
                        failedLoads.remove(uuid);
                    }
                    sharedProgressWrite.set(new SharedProgressWrite(uuid));
                    try {
                        T result = work.run();
                        if (failedLoads.contains(uuid)) throw new SQLException("Player battle pass data is not ready");
                        savePlayerStateLocked(uuid);
                        return result;
                    } finally {
                        sharedProgressWrite.remove();
                    }
                });
            } catch (SQLException | RuntimeException | Error failure) {
                synchronized (playerLock(uuid)) { discardProgressProjection(uuid); }
                throw failure;
            }
        });
    }

    private boolean ownsSharedProgress(UUID uuid) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return false;
        var current = sharedProgressWrite.get();
        return current != null && uuid.equals(current.player);
    }

    /** Caller holds playerLock for the projection update. */
    private void discardProgressProjection(UUID uuid) {
        cachedProgress.remove(uuid);
        cachedInstances.remove(uuid);
        instanceIndexes.remove(uuid);
        templateIndexes.remove(uuid);
        dirtyPlayers.remove(uuid);
        dirtyInstances.remove(uuid);
        dirtyPeriods.remove(uuid);
        failedLoads.remove(uuid);
    }

    private BattlePassPlayerProgress loadProgress(UUID uuid) {
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
        return getTaskInstances(player.getUniqueId());
    }

    private List<PlayerTaskInstance> getTaskInstances(UUID uuid) {
        if (BuildEdition.CROSS_SERVER_INCLUDED && ownsSharedProgress(uuid)) {
            var write = sharedProgressWrite.get();
            if (write.tasksLoaded) return Objects.requireNonNullElse(taskSnapshot(uuid), List.of());
            var tasks = loadTaskInstances(uuid);
            write.tasksLoaded = !failedLoads.contains(uuid);
            return tasks;
        }
        List<PlayerTaskInstance> cached = taskSnapshot(uuid);
        if (cached != null) return cached;
        try {
            return withAuthoritativeProgress(uuid, () -> {
                if (BuildEdition.CROSS_SERVER_INCLUDED && ownsSharedProgress(uuid)) return getTaskInstances(uuid);
                var ready = taskSnapshot(uuid);
                return ready == null ? loadTaskInstances(uuid) : ready;
            });
        } catch (SQLException failure) {
            logger.warning("加载玩家任务实例失败: " + failure.getMessage());
            failedLoads.add(uuid);
            return List.of();
        }
    }

    private List<PlayerTaskInstance> taskSnapshot(UUID uuid) {
        if (!cachedInstances.containsKey(uuid)) return null;
        synchronized (playerLock(uuid)) {
            var cached = cachedInstances.get(uuid);
            return cached == null ? null : List.copyOf(cached);
        }
    }

    /** The caller holds the repository write fence for this player. */
    private List<PlayerTaskInstance> loadTaskInstances(UUID uuid) {
        try {
            String seasonId = configuration.season().seasonId();
            LocalDate today = LocalDate.now();
            List<PlayerTaskInstance> result = new ArrayList<>(repository.loadPlayerTasks(uuid, seasonId));
            boolean hasDaily = result.stream().anyMatch(i -> i.category() == BattlePassTask.TaskCategory.DAILY);
            boolean hasWeekly = result.stream().anyMatch(i -> i.category() == BattlePassTask.TaskCategory.WEEKLY);
            List<PlayerTaskInstance> assigned = new ArrayList<>();
            BattlePassPlayerProgress progress = null;
            if (!hasDaily || !hasWeekly) {
                progress = getProgress(uuid);
                if (failedLoads.contains(uuid)) return List.of();
            }
            if (!hasDaily) {
                assigned.addAll(assignmentService.assignDailyTasks(
                    uuid, configuration.tasks().daily(), configuration.tasks().dailyCount(), today));
            }
            if (!hasWeekly) {
                assigned.addAll(assignmentService.assignWeeklyTasks(
                    uuid, configuration.tasks().weekly(), configuration.tasks().weeklyCount(),
                    progress.currentWeekNumber(), today));
            }
            Set<String> seasonTemplates = new HashSet<>();
            for (PlayerTaskInstance instance : result) {
                if (instance.category() == BattlePassTask.TaskCategory.SEASON) seasonTemplates.add(instance.templateId());
            }
            for (BattlePassTask task : configuration.tasks().season()) {
                if (!seasonTemplates.contains(task.taskId())) {
                    assigned.add(new PlayerTaskInstance(
                        uuid, uuid + "|SEASON|" + task.taskId(), task.taskId(),
                        BattlePassTask.TaskCategory.SEASON, task.requiredCount(), 0, false, today, 0));
                }
            }
            if (!assigned.isEmpty()) {
                repository.savePlayerTasks(uuid, seasonId, assigned);
                result.addAll(assigned);
            }
            synchronized (playerLock(uuid)) {
                List<PlayerTaskInstance> ordered = cacheInstances(uuid, result);
                failedLoads.remove(uuid);
                return List.copyOf(ordered);
            }
        } catch (SQLException failure) {
            logger.warning("加载玩家任务实例失败: " + failure.getMessage());
            failedLoads.add(uuid);
            return List.of();
        }
    }

    private Object playerLock(UUID uuid) {
        return playerLocks[Math.floorMod(uuid.hashCode(), playerLocks.length)];
    }

    private List<PlayerTaskInstance> cacheInstances(UUID uuid, List<PlayerTaskInstance> instances) {
        List<PlayerTaskInstance> ordered = new ArrayList<>(instances);
        ordered.sort(Comparator.comparingInt(i -> i.category().ordinal()));
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
        cachedInstances.put(uuid, ordered);
        return ordered;
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
        try { repository.withPlayerWrite(player.getUniqueId(),()->{
            getProgress(player);
            if(!failedLoads.contains(player.getUniqueId())) getTaskInstances(player);
            return null;
        }); }
        catch(SQLException e) { logger.warning("战令缓存等待存储恢复: " + player.getUniqueId()); }
        // 赛季结算奖励补发已由本体统一 PendingRewardService 在 PlayerJoinEvent 中自动处理
    }

    public void playerJoined(UUID uuid) {
        pendingEvictions.remove(uuid);
    }

    /** 退服时安排后台落盘，失败时留给下一轮保存。 */
    public void handleQuit(UUID uuid) {
        if (!active) return;
        if (!BuildEdition.CROSS_SERVER_INCLUDED || !repository.sharedProgress()) coldTaskLoads.remove(uuid);
        nextWarmup.remove(uuid);
        pendingEvictions.add(uuid);
        persistProgressAsync(uuid);
    }

    private void clearPlayerCache(UUID uuid) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED || !repository.sharedProgress()) coldTaskLoads.remove(uuid);
        nextWarmup.remove(uuid);
        discardProgressProjection(uuid);
        taskEngine.clearPlayerLimits(uuid);
        pendingAuthApply.remove(uuid); uncertainAuthWrites.remove(uuid);
    }

    public List<PlayerTaskInstance> getActiveTaskInstances(Player player) {
        return getTaskInstances(player).stream()
            .filter(i -> !i.completed())
            .toList();
    }

    public void updateInstanceProgress(Player player, PlayerTaskInstance instance, int increment) {
        if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            change(uuid, true, () -> {
                getTaskInstances(uuid);
                updateInstanceProgressNow(uuid, name, instance, increment);
                return null;
            }).exceptionally(failure -> {
                logger.warning("战令任务实例更新未确认: " + failure.getMessage());
                return null;
            });
            return;
        }
        callApi(() -> { updateInstanceProgressNow(player.getUniqueId(), player.getName(), instance, increment); return null; }, null);
    }

    private void updateInstanceProgressNow(UUID uuid, String playerName, PlayerTaskInstance instance, int increment) {
        if (!canMutate() || increment <= 0 || !isSeasonActive()) return;
        synchronized (playerLock(uuid)) {
            List<PlayerTaskInstance> instances = cachedInstances.get(uuid);
            Map<String, Integer> indexes = instanceIndexes.get(uuid);
            BattlePassPlayerProgress progress = cachedProgress.get(uuid);
            if (instances == null || indexes == null || progress == null) return;
            Integer idx = indexes.get(instance.instanceId());
            if (idx == null || idx < 0 || idx >= instances.size()) return;

            PlayerTaskInstance current = instances.get(idx);
            if (current.completed()) return;
            PlayerTaskInstance updatedInstance = current.withProgress(
                (int) Math.min((long) current.currentProgress() + increment, current.targetCount()));
            boolean completedNow = updatedInstance.currentProgress() >= updatedInstance.targetCount();
            if (completedNow) {
                updatedInstance = updatedInstance.withCompleted(true);
                BattlePassTask task = findTask(updatedInstance.templateId());
                if (task != null) {
                    int xp = Math.round(task.totalXpReward() * progress.xpMultiplier());
                    BattlePassPlayerProgress updatedProgress = addXp(progress, xp);
                    cachedProgress.put(uuid, updatedProgress);
                    dirtyPlayers.add(uuid);
                    logger.fine(() -> "任务完成: " + playerName + " task=" + task.taskId() + " xp=" + xp);
                }
            }
            instances.set(idx, updatedInstance);
            markDirtyInstance(uuid, updatedInstance.instanceId());
            if (completedNow) persistProgressAsync(uuid);
        }
    }

    public void updateTaskProgress(Player player, String taskId, int increment) {
        callApi(() -> { updateTaskProgressNow(player, taskId, increment); return null; }, null);
    }

    private void updateTaskProgressNow(Player player, String taskId, int increment) {
        if (increment > 0 && taskId != null && taskIndex.containsKey(taskId)) {
            updateTaskProgressNow(player, Map.of(taskId, increment));
        }
    }

    void updateTaskProgress(Player player, Map<String, Integer> increments) {
        callApi(() -> { updateTaskProgressNow(player, increments); return null; }, null);
    }

    private void updateTaskProgressNow(Player player, Map<String, Integer> increments) {
        if (!canMutate() || increments.isEmpty() || !isSeasonActive()) return;
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) {
            if (ownsSharedProgress(uuid)) updateCachedTaskProgress(uuid, playerName, increments);
            else queueColdTaskProgress(player, uuid, playerName, increments);
            return;
        }
        if (coldTaskLoads.containsKey(uuid) || !updateCachedTaskProgress(uuid, playerName, increments)) {
            queueColdTaskProgress(player, uuid, playerName, increments);
        }
    }

    private static final class ColdTaskLoad {
        final Player player;
        final String name;
        final AuthSnapshot session;
        final Map<String, Integer> increments;
        final Map<LocalDate, Map<String, Integer>> sharedIncrements =
            BuildEdition.CROSS_SERVER_INCLUDED ? new LinkedHashMap<>() : null;
        boolean scheduled;
        SharedTaskBatch inFlight;

        ColdTaskLoad(Player player, String name, AuthSnapshot session, Map<String, Integer> increments, LocalDate day) {
            this.player = player;
            this.name = name;
            this.session = session;
            this.increments = new LinkedHashMap<>();
            add(increments, day);
        }

        void add(Map<String, Integer> values, LocalDate day) {
            var target = BuildEdition.CROSS_SERVER_INCLUDED && day != null
                ? sharedIncrements.computeIfAbsent(day, ignored -> new LinkedHashMap<>()) : increments;
            values.forEach((task, amount) -> target.merge(task, amount, (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE)));
        }
    }

    private record SharedTaskBatch(String receipt, Map<LocalDate, Map<String, Integer>> increments) {}

    private void queueColdTaskProgress(Player player, UUID uuid, String name, Map<String, Integer> increments) {
        var session = AuthAccess.snapshot(uuid);
        if (!active || !session.admitted()) return;
        LocalDate sharedDay = BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress() ? LocalDate.now() : null;
        var selected = coldTaskLoads.compute(uuid, (ignored, current) -> {
            if (!active) return null;
            if (current != null && (BuildEdition.CROSS_SERVER_INCLUDED && sharedDay != null
                    || current.player == player && sameTaskSession(current.session, session))) {
                synchronized (current) {
                    current.add(increments, sharedDay);
                }
                return current;
            }
            return new ColdTaskLoad(player, name, session, increments, sharedDay);
        });
        if (selected == null) return;
        scheduleColdTaskLoad(uuid, selected);
    }

    private void scheduleColdTaskLoad(UUID uuid, ColdTaskLoad load) {
        scheduleColdTaskLoad(uuid, load, SHARED_TASK_BATCH_TICKS);
    }

    private void scheduleColdTaskLoad(UUID uuid, ColdTaskLoad load, long delay) {
        synchronized (load) {
            if (load.scheduled) return;
            load.scheduled = true;
        }
        try {
            if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) {
                laterAsync(() -> finishSharedTaskLoad(uuid, load), delay);
            } else {
                async(() -> finishColdTaskLoad(uuid, load));
            }
        } catch (RuntimeException | LinkageError failure) {
            if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) {
                synchronized (load) { load.scheduled = false; }
            } else {
                coldTaskLoads.remove(uuid, load);
            }
            logger.fine("战令任务补载未调度: " + failure.getMessage());
        }
    }

    private void finishSharedTaskLoad(UUID uuid, ColdTaskLoad load) {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        long nextDelay = SHARED_TASK_BATCH_TICKS;
        try {
            persistSharedTaskLoad(uuid, load);
        } catch (SQLException | RuntimeException | LinkageError failure) {
            nextDelay = 100L;
            logger.warning("战令任务等待存储恢复 [" + uuid + "]: " + failure.getMessage());
        } finally {
            var pending = coldTaskLoads.computeIfPresent(uuid, (ignored, current) -> {
                if (current != load) return current;
                synchronized (load) {
                    load.scheduled = false;
                    return load.inFlight == null && load.sharedIncrements.isEmpty() ? null : load;
                }
            });
            if (active && pending == load) scheduleColdTaskLoad(uuid, load, nextDelay);
        }
    }

    private void persistSharedTaskLoad(UUID uuid, ColdTaskLoad load) throws SQLException {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        SharedTaskBatch batch;
        synchronized (load) {
            if (load.inFlight == null && !load.sharedIncrements.isEmpty()) {
                load.inFlight = new SharedTaskBatch(UUID.randomUUID().toString(), new LinkedHashMap<>(load.sharedIncrements));
                load.sharedIncrements.clear();
            }
            batch = load.inFlight;
        }
        if (batch == null) return;
        withAuthoritativeProgress(uuid, () -> {
            String season = configuration.season().seasonId();
            if (repository.receivedAuthFact(uuid, season, batch.receipt()) || repository.isSeasonSettled(season)) return null;
            getTaskInstances(uuid);
            resetPeriods(uuid, true, true);
            for (var entry : batch.increments().entrySet()) {
                if (!updateCachedTaskProgress(uuid, load.name, entry.getValue(), entry.getKey())) {
                    throw new SQLException("Player battle pass tasks are not ready");
                }
            }
            if (dirtyPlayers.contains(uuid) || dirtyInstances.containsKey(uuid)) {
                repository.saveAuthFact(batch.receipt(), cachedProgress.get(uuid), List.of(), false);
            }
            return null;
        });
        synchronized (load) {
            if (load.inFlight == batch) load.inFlight = null;
        }
    }

    private void flushSharedTaskLoads() throws SQLException {
        if (!BuildEdition.CROSS_SERVER_INCLUDED) return;
        SQLException pending = null;
        for (var entry : List.copyOf(coldTaskLoads.entrySet())) {
            try {
                persistSharedTaskLoad(entry.getKey(), entry.getValue());
                // An earlier attempt can have a frozen batch ahead of newly captured increments.
                persistSharedTaskLoad(entry.getKey(), entry.getValue());
            } catch (SQLException | RuntimeException | LinkageError failure) {
                if (pending == null) pending = new SQLException("BattlePass task progress is not confirmed");
                pending.addSuppressed(failure);
            }
        }
        if (pending != null) throw pending;
    }

    private void finishColdTaskLoad(UUID uuid, ColdTaskLoad load) {
        try {
            if (coldTaskLoads.get(uuid) != load || !currentTaskSession(uuid, load)) return;
            if (!taskProgressLoaded(uuid)) preload(load.player);
            if (!taskProgressLoaded(uuid)) return;
            synchronized (playerLock(uuid)) {
                if (coldTaskLoads.remove(uuid, load) && currentTaskSession(uuid, load)) {
                    updateCachedTaskProgress(uuid, load.name, load.increments);
                }
            }
        } finally {
            coldTaskLoads.remove(uuid, load);
        }
    }

    private boolean currentTaskSession(UUID uuid, ColdTaskLoad load) {
        return active && load.player.isOnline() && sameTaskSession(load.session, AuthAccess.snapshot(uuid));
    }

    private static boolean sameTaskSession(AuthSnapshot first, AuthSnapshot current) {
        return current.admitted() && current.generation() == first.generation()
            && Objects.equals(current.connectionId(), first.connectionId());
    }

    private boolean taskProgressLoaded(UUID uuid) {
        return cachedProgress.containsKey(uuid) && cachedInstances.containsKey(uuid)
            && instanceIndexes.containsKey(uuid) && templateIndexes.containsKey(uuid) && !failedLoads.contains(uuid);
    }

    /** @return false if the player's data still needs to be loaded. */
    private boolean updateCachedTaskProgress(UUID uuid, String playerName, Map<String, Integer> increments) {
        return updateCachedTaskProgress(uuid, playerName, increments, null);
    }

    private boolean updateCachedTaskProgress(UUID uuid, String playerName, Map<String, Integer> increments, LocalDate day) {
        if (!taskProgressLoaded(uuid)) return false;
        synchronized (playerLock(uuid)) {
            if (!canMutate() || !isSeasonActive()) return true;
            List<PlayerTaskInstance> instances = cachedInstances.get(uuid);
            Map<String, Integer> indexes = instanceIndexes.get(uuid);
            Map<String, List<String>> templates = templateIndexes.get(uuid);
            if (instances == null || indexes == null || templates == null || !cachedProgress.containsKey(uuid)
                || failedLoads.contains(uuid)) {
                return false;
            }
            boolean completedNow = false;
            for (var entry : increments.entrySet()) {
                List<String> instanceIds = templates.get(entry.getKey());
                int increment = entry.getValue();
                if (instanceIds == null || increment <= 0) continue;
                for (String instanceId : instanceIds) {
                    Integer index = indexes.get(instanceId);
                    if (index == null || index < 0 || index >= instances.size()) continue;
                    PlayerTaskInstance current = instances.get(index);
                    if (current.completed() || day != null && !taskAppliesOn(current, day)) continue;
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
                            logger.fine(() -> "任务完成: " + playerName + " task=" + task.taskId() + " xp=" + xp);
                        }
                    }
                    instances.set(index, updated);
                    markDirtyInstance(uuid, updated.instanceId());
                }
            }
            if (completedNow) persistProgressAsync(uuid);
            return true;
        }
    }
    /** Receipt, task progress and XP commit together; no SQL or waiting on the publishing server thread. */
    public java.util.concurrent.CompletionStage<Void> acceptAuthFact(Player player,String task,int increment,String eventId,LocalDate businessDate) {
        return acceptFact(player.getUniqueId(), task, increment, eventId, businessDate, true, false);
    }

    java.util.concurrent.CompletionStage<Void> acceptTaskFact(UUID player, String task, int increment,
            String eventId, LocalDate businessDate) {
        return acceptFact(player, task, increment, eventId, businessDate, false, false);
    }

    java.util.concurrent.CompletionStage<Void> acceptTaskFactForAssignment(UUID player, String task, int increment,
            String eventId, LocalDate businessDate) {
        return acceptFact(player, task, increment, eventId, businessDate, false, true);
    }

    private java.util.concurrent.CompletionStage<Void> acceptFact(UUID uuid, String task, int increment,
            String eventId, LocalDate businessDate, boolean authentication, boolean assignmentScoped) {
        String season = configuration.season().seasonId();
        BattlePassTask template = taskIndex.get(task);
        if(!active || !xpCurveRuntime.ready) return CompletableFuture.failedFuture(new IllegalStateException("BattlePass is not ready"));
        if (template == null) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException("Unknown task: " + task));
        }
        String receiptPrefix = season + "/" + task + "/";
        boolean presence = authentication && BattlePassTask.PRESENCE_TOPIC.equals(template.eventTopic());
        String factKey = presence ? "presence-day/" + businessDate : (authentication ? "" : template.eventTopic() + "/") + eventId;
        String baseFact = authReceiptId(receiptPrefix + factKey);
        var result=new java.util.concurrent.CompletableFuture<Void>();
        try { authFacts.execute(()->{
            try {
                withAuthoritativeProgress(uuid, () -> {
                    if (repository.isSeasonSettled(season)) return null;
                    if (!cachedProgress.containsKey(uuid)) getProgress(uuid);
                    if (!cachedProgress.containsKey(uuid)) throw new SQLException("Player progress is not ready");
                    if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()
                        && !assignmentScoped && repository.receivedAuthFact(uuid, season, baseFact)) {
                        return null;
                    }
                    if ((BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) || !cachedInstances.containsKey(uuid)) {
                        getTaskInstances(uuid);
                    }
                    reconcileAuthWrites(uuid, season);
                    String fact = baseFact;
                    if (assignmentScoped) {
                        resetPeriods(uuid, true, true);
                        var instance = cachedInstances.getOrDefault(uuid, List.of()).stream()
                            .filter(current -> current.templateId().equals(task) && !current.completed())
                            .findFirst().orElse(null);
                        if (instance == null) return null;
                        fact = authReceiptId(receiptPrefix + "assignment/" + instance.instanceId() + "/" + eventId);
                    }
                    if ((!BuildEdition.CROSS_SERVER_INCLUDED || !repository.sharedProgress() || assignmentScoped)
                        && repository.receivedAuthFact(uuid, season, fact)) return null;
                    if (!assignmentScoped) resetPeriods(uuid,true,true);
                    AuthProjection projection=projectAuthFact(uuid,task,increment,businessDate);
                    try {
                        repository.saveAuthFact(fact,projection.progress(),projection.tasks(),!projection.deltas().isEmpty());
                        if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) {
                            if (!projection.deltas().isEmpty()) {
                                synchronized (playerLock(uuid)) {
                                    cachedProgress.put(uuid, projection.progress());
                                    cacheInstances(uuid, projection.tasks());
                                }
                            }
                        } else {
                            applyAuthDeltas(uuid, projection.deltas());
                        }
                    } catch(SQLException | RuntimeException e) {
                        if (!BuildEdition.CROSS_SERVER_INCLUDED || !repository.sharedProgress()) {
                            pendingAuthApply.computeIfAbsent(uuid, key -> new HashMap<>()).put(fact, projection.deltas());
                            uncertainAuthWrites.add(uuid);
                        }
                        throw e;
                    }
                    return null;
                });
                if (!authentication && !xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(uuid)) {
                    pendingEvictions.add(uuid);
                    try {
                        saveCurrentProgress(uuid);
                    } catch (SQLException e) {
                        logger.warning("战令离线任务缓存保存失败 [" + uuid + "]: " + e.getMessage());
                    }
                }
                result.complete(null);
            } catch(Exception | LinkageError e) { result.completeExceptionally(e); }
        }); } catch(java.util.concurrent.RejectedExecutionException e) { result.completeExceptionally(e); }
        return result.copy();
    }

    private void reconcileAuthWrites(UUID uuid, String season) throws SQLException {
        if (!uncertainAuthWrites.contains(uuid)) return;
        Map<String, List<AuthTaskDelta>> pending = pendingAuthApply.get(uuid);
        if (pending != null) {
            var entries = pending.entrySet().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                if (repository.receivedAuthFact(uuid, season, entry.getKey())) {
                    applyAuthDeltas(uuid, entry.getValue());
                }
                entries.remove();
            }
            pendingAuthApply.remove(uuid);
        }
        uncertainAuthWrites.remove(uuid);
    }

    private static String authReceiptId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
    private AuthProjection projectAuthFact(UUID uuid,String taskId,int increment,LocalDate date) throws SQLException {
        synchronized(playerLock(uuid)) {
            var progress=cachedProgress.get(uuid); var instances=cachedInstances.get(uuid);
            if(failedLoads.contains(uuid) || progress==null || instances==null) throw new SQLException("Player cache is not ready");
            var updated=new ArrayList<PlayerTaskInstance>();
            var deltas=new ArrayList<AuthTaskDelta>();
            boolean seasonDate=!date.isBefore(configuration.season().startDate()) && !date.isAfter(configuration.season().endDate());
            for(var current:instances) {
                if(!seasonDate || !taskAppliesOn(current, date) || increment<=0 || !current.templateId().equals(taskId) || current.completed()) { updated.add(current); continue; }
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

    private static boolean taskAppliesOn(PlayerTaskInstance task, LocalDate day) {
        if (task.category() == BattlePassTask.TaskCategory.SEASON) return true;
        LocalDate assigned = task.assignedDate();
        if (assigned == null) return false;
        if (task.category() == BattlePassTask.TaskCategory.DAILY) return day.equals(assigned);
        return !day.isBefore(assigned) && day.isBefore(assigned.plusDays(7));
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
        long newXp = (long)progress.currentXp() + xp;
        int newLevel = progress.currentLevel();
        var xpCurve = xpCurveRuntime;
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
        return progress.withLevel(newLevel).withXp((int)newXp);
    }

    public enum ClaimResult {
        SUCCESS("claim.success"), PARTIAL("claim.partial"), RETRY("claim.retry"),
        LEVEL_NOT_REACHED("claim.level-not-reached"), ALREADY_CLAIMED("claim.already-claimed"), NOT_FOUND("claim.none");

        private final String messageKey;

        ClaimResult(String messageKey) { this.messageKey = messageKey; }
        public String messageKey() { return messageKey; }
    }

    public record ClaimBatch(int accepted, boolean retry) {}
    record RewardEntry(String id, BattlePassReward reward) {}

    public ClaimBatch claimAll(Player player) {
        return claimAll(player, () -> true);
    }

    private ClaimBatch claimAll(Player player, BooleanSupplier allowed) {
        if (!canMutate()) return new ClaimBatch(0, true);
        UUID uuid = player.getUniqueId();
        try {
            return repository.withPlayerWrite(uuid, () -> {
                if (!allowed.getAsBoolean()) return new ClaimBatch(0, true);
                var progress = rewardProgress(player);
                if (isLoadFailed(uuid)) return new ClaimBatch(0, true);
                return claimForPlayer(player, eligibleRewards(configuration.rewards(), progress), allowed);
            });
        } catch (SQLException error) {
            logger.log(java.util.logging.Level.WARNING, "战令一键领取待重试 [" + uuid + "]", error);
            return new ClaimBatch(0, true);
        }
    }

    public ClaimResult claimRewards(Player player, int level) {
        return claimRewards(player, level, () -> true);
    }

    private ClaimResult claimRewards(Player player, int level, BooleanSupplier allowed) {
        if (!canMutate()) return ClaimResult.RETRY;
        UUID uuid = player.getUniqueId();
        try {
            return repository.withPlayerWrite(uuid, () -> {
                if (!allowed.getAsBoolean()) return ClaimResult.RETRY;
                var progress = rewardProgress(player);
                if (isLoadFailed(uuid)) return ClaimResult.RETRY;
                if (progress.currentLevel() < level) return ClaimResult.LEVEL_NOT_REACHED;
                var eligible = eligibleRewards(rewardsByLevel.getOrDefault(level, List.of()), progress);
                if (eligible.isEmpty()) return ClaimResult.NOT_FOUND;
                var batch = claimForPlayer(player, eligible, allowed);
                if (batch.accepted() > 0) return batch.retry() ? ClaimResult.PARTIAL : ClaimResult.SUCCESS;
                return batch.retry() ? ClaimResult.RETRY : ClaimResult.ALREADY_CLAIMED;
            });
        } catch (SQLException error) {
            logger.log(java.util.logging.Level.WARNING, "战令领取待重试 [" + uuid + "]", error);
            return ClaimResult.RETRY;
        }
    }

    private static List<RewardEntry> eligibleRewards(List<BattlePassReward> rewards, BattlePassPlayerProgress progress) {
        return rewards.stream()
            .filter(reward -> reward.level() <= progress.currentLevel() && canClaim(reward, progress.passTier()))
            .map(reward -> new RewardEntry(reward.rewardId(), reward))
            .toList();
    }

    private BattlePassPlayerProgress rewardProgress(Player player) throws SQLException {
        if (!BuildEdition.CROSS_SERVER_INCLUDED || !repository.sharedProgress()) return getProgress(player);
        return repository.loadProgress(player.getUniqueId(), configuration.season().seasonId());
    }

    static boolean canClaim(BattlePassReward reward, BattlePassPlayerProgress.PassTier tier) {
        return switch (reward.tier()) {
            case FREE -> true;
            case PREMIUM -> tier != BattlePassPlayerProgress.PassTier.FREE;
            case DELUXE -> tier == BattlePassPlayerProgress.PassTier.DELUXE;
        };
    }

    private ClaimBatch claimForPlayer(Player player, List<RewardEntry> eligible, BooleanSupplier allowed) {
        var batch = acceptRewards(player.getUniqueId(), eligible, allowed);
        if (batch.accepted() > 0) flushAcceptedRewards(Set.of(player.getUniqueId()));
        return batch;
    }

    void flushAcceptedRewards(Set<UUID> players) {
        if (players.isEmpty()) return;
        Set<UUID> recipients = Set.copyOf(players);
        try {
            onMain( () -> {
                if (recipients.size() == 1) {
                    Player online = Bukkit.getPlayer(recipients.iterator().next());
                    if (online != null && online.isOnline()) rewardDispatcher.flushOnline(online);
                    return;
                }
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (recipients.contains(online.getUniqueId())) rewardDispatcher.flushOnline(online);
                }
            });
        } catch (RuntimeException error) {
            logger.warning("战令奖励已入队，将在玩家下次上线时发放");
        }
    }

    ClaimBatch acceptRewards(UUID player, List<RewardEntry> eligible) {
        return acceptRewards(player, eligible, () -> true);
    }

    private ClaimBatch acceptRewards(UUID player, List<RewardEntry> eligible, BooleanSupplier allowed) {
        try {
            return repository.withPlayerWrite(player, () -> {
                if (!canMutate()) return new ClaimBatch(0, true);
                String season = configuration.season().seasonId();
                savePlayerStateLocked(player);
                Set<String> claimed = new HashSet<>(repository.loadClaimedRewards(player, season));
                if (!allowed.getAsBoolean()) return new ClaimBatch(0, true);
                int accepted = 0;
                boolean retry = false;
                for (RewardEntry entry : eligible) {
                    if (claimed.contains(entry.id())) continue;
                    try {
                        var result = rewardDispatcher.accept(player, season, entry.id(), entry.reward());
                        if (result == BattlePassRewardDispatcher.Result.RETRY) {
                            retry = true;
                            logger.warning("战令奖励尚未确认，保留待重试 [" + player + "/" + entry.id() + "]");
                            continue;
                        }
                        claimed.add(entry.id());
                        if (result == BattlePassRewardDispatcher.Result.ACCEPTED) accepted++;
                    } catch (SQLException | RuntimeException error) {
                        retry = true;
                        logger.log(java.util.logging.Level.WARNING,
                            "确认战令奖励失败 [" + player + "/" + entry.id() + "]", error);
                    }
                }
                synchronized (playerLock(player)) {
                    cachedProgress.computeIfPresent(player, (ignored, progress) -> progress.withClaimedRewards(claimed));
                }
                return new ClaimBatch(accepted, retry);
            });
        } catch (SQLException error) {
            logger.log(java.util.logging.Level.WARNING, "读取战令领取记录失败 [" + player + "]", error);
            return new ClaimBatch(0, true);
        }
    }

    void flushProgressForSettlement() throws SQLException {
        if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) flushSharedTaskLoads();
        for (UUID player : List.copyOf(cachedProgress.keySet())) {
            repository.withPlayerWrite(player, () -> {
                if (failedLoads.contains(player)) throw new SQLException("BattlePass progress unavailable for " + player);
                savePlayerStateLocked(player);
                return null;
            });
        }
    }

    /** 关键操作后立即异步落盘进度，缩小崩服数据丢失窗口。 */
    private void persistProgressAsync(UUID uuid) {
        if (ownsSharedProgress(uuid)) return;
        if(Thread.currentThread() instanceof BusinessWorker worker && worker.owner==this && uuid.equals(worker.player))return;
        if (!active) return;
        var request = new ProgressSave();
        var selected = progressSaves.compute(uuid, (ignored, current) -> {
            if (!active) return current;
            if (current == null) return request;
            if (current.started) current.followUp = true;
            return current;
        });
        if (selected == request) scheduleProgressSave(uuid, request);
    }

    private static final class ProgressSave {
        // Accessed only inside progressSaves.compute for this player.
        boolean started;
        boolean followUp;
    }

    private void scheduleProgressSave(UUID uuid, ProgressSave request) {
        try {
            async(() -> runProgressSave(uuid, request));
        } catch (RuntimeException | LinkageError failure) {
            progressSaves.remove(uuid, request);
            logger.fine("即时保存战令进度未调度 [" + uuid + "]: " + failure.getMessage());
        }
    }

    private void runProgressSave(UUID uuid, ProgressSave request) {
        var selected = progressSaves.computeIfPresent(uuid, (ignored, current) -> {
            if (current == request) current.started = true;
            return current;
        });
        if (selected != request) return;
        boolean succeeded = false;
        try {
            if (active && needsProgressSave(uuid)) saveCurrentProgress(uuid);
            succeeded = true;
        } catch (SQLException failure) {
            logger.warning("保存战令进度失败 [" + uuid + "]: " + failure.getMessage());
        } finally {
            finishProgressSave(uuid, request, succeeded);
        }
    }

    private void finishProgressSave(UUID uuid, ProgressSave request, boolean succeeded) {
        var next = progressSaves.computeIfPresent(uuid, (ignored, current) -> {
            if (current != request) return current;
            if (!active || !succeeded || !current.followUp || !needsProgressSave(uuid)) return null;
            current.started = false;
            current.followUp = false;
            return current;
        });
        if (next == request) scheduleProgressSave(uuid, request);
    }

    private boolean needsProgressSave(UUID uuid) {
        return dirtyPlayers.contains(uuid) || dirtyInstances.containsKey(uuid)
            || uncertainAuthWrites.contains(uuid) || pendingEvictions.contains(uuid);
    }
    private void saveCurrentProgress(UUID uuid) throws SQLException {
        repository.withPlayerWrite(uuid, () -> {
            savePlayerStateLocked(uuid);
            if (pendingEvictions.contains(uuid)) {
                synchronized (playerLock(uuid)) {
                    if (!dirtyPlayers.contains(uuid) && !dirtyInstances.containsKey(uuid)
                        && !uncertainAuthWrites.contains(uuid) && pendingEvictions.remove(uuid)) {
                        clearPlayerCache(uuid);
                    }
                }
            }
            return null;
        });
    }

    /** Caller holds the repository write fence. The game thread can update the cache during JDBC work. */
    private void savePlayerStateLocked(UUID uuid) throws SQLException {
        if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress() && !ownsSharedProgress(uuid)) {
            if (dirtyPlayers.contains(uuid) || dirtyInstances.containsKey(uuid) || dirtyPeriods.containsKey(uuid)) {
                throw new SQLException("MySQL battle pass progress requires an authoritative transaction");
            }
            return;
        }
        String season = configuration.season().seasonId();
        reconcileAuthWrites(uuid, season);
        if (failedLoads.contains(uuid)) return;
        BattlePassPlayerProgress progress;
        List<PlayerTaskInstance> tasks = List.of();
        Set<BattlePassTask.TaskCategory> replacedCategories;
        Object lock = playerLock(uuid);
        synchronized (lock) {
            progress = dirtyPlayers.contains(uuid) ? cachedProgress.get(uuid) : null;
            replacedCategories = dirtyPeriods.getOrDefault(uuid, Set.of());
            Set<String> dirty = dirtyInstances.get(uuid);
            if (dirty != null) {
                var instances = cachedInstances.get(uuid);
                var indexes = instanceIndexes.get(uuid);
                if (instances == null || indexes == null) return;
                tasks = new ArrayList<>(dirty.size());
                var ids = dirty.iterator();
                while (ids.hasNext()) {
                    Integer index = indexes.get(ids.next());
                    if (index == null) ids.remove();
                    else tasks.add(instances.get(index));
                }
                if (dirty.isEmpty()) dirtyInstances.remove(uuid);
            }
        }
        if (progress != null) {
            repository.saveProgressAndTasks(progress, tasks, replacedCategories);
        } else if (!tasks.isEmpty()) {
            repository.savePlayerTasks(uuid, season, tasks);
        } else {
            return;
        }
        synchronized (lock) {
            if (cachedProgress.get(uuid) == progress) dirtyPlayers.remove(uuid);
            dirtyPeriods.remove(uuid);
            Set<String> dirty = dirtyInstances.get(uuid);
            if (dirty != null) {
                var instances = cachedInstances.get(uuid);
                var indexes = instanceIndexes.get(uuid);
                for (PlayerTaskInstance task : tasks) {
                    if (instances.get(indexes.get(task.instanceId())) == task) dirty.remove(task.instanceId());
                }
                if (dirty.isEmpty()) dirtyInstances.remove(uuid);
            }
        }
        if (active && !replacedCategories.isEmpty()) {
            onMain( () -> {
                if (active) notifyUiRefresh(uuid, true);
            });
        }
    }

    private boolean unlockTier(Player player, BattlePassPlayerProgress.PassTier tier) {
        UUID uuid = player.getUniqueId();
        boolean changed = false;
        synchronized (playerLock(uuid)) {
            BattlePassPlayerProgress progress = Objects.requireNonNull(cachedProgress.get(uuid));
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
    private int addXp(Player player, int xp) {
        if (xp <= 0) return 0;
        UUID uuid = player.getUniqueId();
        int result;
        boolean changed;
        synchronized (playerLock(uuid)) {
            BattlePassPlayerProgress progress = Objects.requireNonNull(cachedProgress.get(uuid));
            BattlePassPlayerProgress updated = addXp(progress, xp);
            cachedProgress.put(uuid, updated);
            dirtyPlayers.add(uuid);
            persistProgressAsync(uuid);
            // 累加升级经过的每级实际所需经验 + 当前级内经验
            var xpCurve = xpCurveRuntime;
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
    private void setXp(Player player, int xp) {
        UUID uuid = player.getUniqueId();
        boolean changed;
        synchronized (playerLock(uuid)) {
            var xpCurve = xpCurveRuntime;
            BattlePassPlayerProgress progress = Objects.requireNonNull(cachedProgress.get(uuid));
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
    private void removeXp(Player player, int xp) {
        if (xp <= 0) return;
        UUID uuid = player.getUniqueId();
        boolean changed;
        synchronized (playerLock(uuid)) {
            BattlePassPlayerProgress progress = Objects.requireNonNull(cachedProgress.get(uuid));
            var xpCurve = xpCurveRuntime;
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
    private void setLevel(Player player, int level) {
        UUID uuid = player.getUniqueId();
        boolean changed;
        synchronized (playerLock(uuid)) {
            int maxLevel = configuration.season().maxLevel();
            int clamped = Math.max(1, Math.min(level, maxLevel));
            BattlePassPlayerProgress progress = Objects.requireNonNull(cachedProgress.get(uuid));
            BattlePassPlayerProgress updated = progress.withLevel(clamped).withXp(0);
            cachedProgress.put(uuid, updated);
            dirtyPlayers.add(uuid);
            persistProgressAsync(uuid);
            changed = updated != progress;
        }
        if (changed) notifyUiRefresh(uuid, false);
    }


    private boolean resetPlayerProgress(UUID playerUuid) {
        try {
            return withAuthoritativeProgress(playerUuid, () -> {
                repository.resetPlayerProgress(playerUuid, configuration.season().seasonId());
                synchronized (playerLock(playerUuid)) {
                    clearPlayerCache(playerUuid);
                }
                return true;
            });
        } catch (SQLException e) {
            logger.warning("重置战令进度失败: " + e.getMessage());
            return false;
        }
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
        Set<UUID> players = new HashSet<>(dirtyPlayers);
        players.addAll(dirtyInstances.keySet());
        players.addAll(uncertainAuthWrites);
        players.addAll(pendingEvictions);
        for (UUID uuid : players) {
            if (active) {
                var request = new ProgressSave();
                if (progressSaves.putIfAbsent(uuid, request) == null) runProgressSave(uuid, request);
                continue;
            }
            try {
                saveCurrentProgress(uuid);
            } catch (SQLException e) {
                logger.warning("战令保存失败 [" + uuid + "]: " + e.getMessage());
            }
        }
    }

    private void markDirtyInstance(UUID uuid, String instanceId) {
        dirtyInstances.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(instanceId);
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
                async( () -> performReset(uuid, dailyNeedsReset, weeklyNeedsReset));
            }
        }
    }

    private void performReset(UUID playerUuid,boolean daily,boolean weekly) {
        try { resetPeriods(playerUuid,daily,weekly); }
        catch(SQLException e) { logger.warning("任务周期重置待重试 ["+playerUuid+"]: "+e.getMessage()); }
    }

    private void resetPeriods(UUID playerUuid, boolean requestedDaily, boolean requestedWeekly) throws SQLException {
        withAuthoritativeProgress(playerUuid, () -> {
            if (failedLoads.contains(playerUuid)) throw new SQLException("Player battle pass data is not ready");
            if (BuildEdition.CROSS_SERVER_INCLUDED && repository.sharedProgress()) getTaskInstances(playerUuid);
            if (uncertainAuthWrites.contains(playerUuid) || dirtyPeriods.containsKey(playerUuid)) {
                savePlayerStateLocked(playerUuid);
            }
            var before = cachedProgress.get(playerUuid);
            if (before == null || !cachedInstances.containsKey(playerUuid)) return null;
            LocalDate today = LocalDate.now();
            boolean daily = requestedDaily && !today.equals(before.lastDailyResetDate());
            boolean weekly = requestedWeekly && (before.lastWeeklyResetDate() == null
                || ChronoUnit.DAYS.between(before.lastWeeklyResetDate(), today) >= 7);
            if (!daily && !weekly) return null;
            synchronized (playerLock(playerUuid)) {
                var progress = cachedProgress.get(playerUuid);
                var tasks = new ArrayList<>(cachedInstances.get(playerUuid));
                var replaced = java.util.EnumSet.noneOf(BattlePassTask.TaskCategory.class);
                if (daily) {
                    replaced.add(BattlePassTask.TaskCategory.DAILY);
                    tasks.removeIf(task->task.category()==BattlePassTask.TaskCategory.DAILY);
                    var assigned = assignmentService.assignDailyTasks(
                        playerUuid, configuration.tasks().daily(), configuration.tasks().dailyCount(), today);
                    tasks.addAll(assigned);
                    assigned.forEach(task->markDirtyInstance(playerUuid,task.instanceId()));
                    progress = progress.withDailyReset(today);
                }
                if (weekly) {
                    replaced.add(BattlePassTask.TaskCategory.WEEKLY);
                    tasks.removeIf(task->task.category()==BattlePassTask.TaskCategory.WEEKLY);
                    int week = progress.currentWeekNumber() + 1;
                    var assigned = assignmentService.assignWeeklyTasks(
                        playerUuid, configuration.tasks().weekly(), configuration.tasks().weeklyCount(), week, today);
                    tasks.addAll(assigned);
                    assigned.forEach(task->markDirtyInstance(playerUuid,task.instanceId()));
                    progress = progress.withWeeklyReset(today, week);
                }
                tasks.sort(Comparator.comparingInt(task->task.category().ordinal()));
                cacheInstances(playerUuid, tasks);
                cachedProgress.put(playerUuid, progress);
                dirtyPlayers.add(playerUuid);
                dirtyPeriods.put(playerUuid, replaced);
            }
            savePlayerStateLocked(playerUuid);
            return null;
        });
    }
}
