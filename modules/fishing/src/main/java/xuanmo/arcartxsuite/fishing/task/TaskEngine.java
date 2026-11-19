package xuanmo.arcartxsuite.fishing.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import xuanmo.arcartxsuite.api.scheduler.ModuleCallback;
import xuanmo.arcartxsuite.api.scheduler.ModuleExecutor;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;

import xuanmo.arcartxsuite.fishing.service.FishingService;
import xuanmo.arcartxsuite.fishing.storage.FishingRepository;

/**
 * 钓鱼任务引擎，负责任务分配、事件监听、进度更新和奖励发放。
 * <p>
 * 关联模块：{@code fishing}。通过 EventBusCapability 订阅钓鱼事件，
 * 自动更新匹配任务的进度，任务完成后发放经验奖励。
 * <p>
 * 任务分配策略：每日/每周从任务池中按权重随机抽取指定数量的任务，
 * 赛季任务则全部激活（不随机抽取）。
 */
public final class TaskEngine {

    private volatile boolean stopping;
    private final ModuleCallback callbacks = new ModuleCallback(() -> {});
    private final Set<FutureTask<Void>> pendingWrites = ConcurrentHashMap.newKeySet();
    private final ModuleExecutor closer = new ModuleExecutor(0,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),work -> {
        var thread=new Thread(work,"AXS-Fishing-TaskClose");thread.setDaemon(true);return thread;
    });
    private EventBusCapability subscribedBus;

    private final List<FishingTask> dailyPool;
    private final List<FishingTask> weeklyPool;
    private final List<FishingTask> seasonPool;
    private final int dailyCount;
    private final int weeklyCount;
    private final Supplier<EventBusCapability> eventBusProvider;
    private final Supplier<FishingService> serviceSupplier;
    private final Logger logger;
    private final Plugin plugin;
    private final FishingRepository repository;
    private final String currentSeasonId;

    // 玩家任务实例缓存：uuid → 分类 → 实例列表
    private final Map<UUID, Map<FishingTask.TaskCategory, List<PlayerTaskInstance>>> playerTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Object> loadingTasks = new HashMap<>();
    private final Map<UUID, Runnable> onTasksLoaded = new HashMap<>();
    // EventBus 订阅 ID：shutdown 时退订，防止模块 reload 后订阅叠加、进度双倍增长
    private final List<String> eventBusSubscriptionIds = new ArrayList<>();
    // 周期边界检测任务（daily/weekly reset 调度）
    private BukkitTask resetBoundaryTask;
    private LocalDate lastDailyDate;
    private LocalDate lastWeekMonday;

    public TaskEngine(@NotNull List<FishingTask> dailyPool,
                      @NotNull List<FishingTask> weeklyPool,
                      @NotNull List<FishingTask> seasonPool,
                      int dailyCount,
                      int weeklyCount,
                      @Nullable Supplier<EventBusCapability> eventBusProvider,
                      @NotNull Supplier<FishingService> serviceSupplier,
                      @NotNull Logger logger,
                      @NotNull Plugin plugin,
                      @NotNull FishingRepository repository,
                      @NotNull String currentSeasonId) {
        this.dailyPool = dailyPool;
        this.weeklyPool = weeklyPool;
        this.seasonPool = seasonPool;
        this.dailyCount = dailyCount;
        this.weeklyCount = weeklyCount;
        this.eventBusProvider = eventBusProvider;
        this.serviceSupplier = serviceSupplier;
        this.logger = logger;
        this.plugin = plugin;
        this.repository = repository;
        this.currentSeasonId = currentSeasonId;
    }

    /**
     * 启动任务引擎，恢复在线玩家的任务并注册事件与周期调度。
     */
    public void start() {
        startResetBoundaryScheduler();
        purgeStaleTaskProgress();
        for (Player player : Bukkit.getOnlinePlayers()) {
            assignTasks(player.getUniqueId());
        }

        EventBusCapability eventBus = eventBusProvider != null ? eventBusProvider.get() : null;
        if (eventBus == null) {
            logger.warning("EventBus 不可用，钓鱼任务引擎无法监听事件");
            return;
        }
        subscribedBus = eventBus;
        // 收集所有需要订阅的事件主题（去重）
        java.util.Set<String> topics = new java.util.HashSet<>();
        for (FishingTask task : dailyPool) {
            if (!task.eventTopic().isBlank()) topics.add(task.eventTopic());
        }
        for (FishingTask task : weeklyPool) {
            if (!task.eventTopic().isBlank()) topics.add(task.eventTopic());
        }
        for (FishingTask task : seasonPool) {
            if (!task.eventTopic().isBlank()) topics.add(task.eventTopic());
        }
        // 订阅所有主题
        for (String topic : topics) {
            String subId = eventBus.subscribe(topic, event -> {
                Player player = event.player();
                if (player == null) return;
                // EventBus 同步派发在 publish 线程；强制回主线程，保证 updateTasks 内
                // Bukkit API（sendMessage/giveXp）与 playerTasks 访问都在主线程安全区
                onMain(() -> {
                    try {
                        updateTasks(player, event.topic(), event.payload());
                    } catch (Exception e) {
                        logger.warning("更新任务进度失败 [" + event.topic() + "]: " + e.getMessage());
                    }
                });
            });
            if (subId != null) {
                eventBusSubscriptionIds.add(subId);
            }
        }
    }

    /**
     * 关闭任务引擎：取消周期调度、退订全部 EventBus 订阅并清空任务缓存。
     */
    public void shutdown() {
        synchronized(pendingWrites) {
            if(stopping) return;
            stopping=true;
        }
        callbacks.close();
        try {
        if (resetBoundaryTask != null) {
            resetBoundaryTask.cancel();
            resetBoundaryTask = null;
        }
        EventBusCapability eventBus = subscribedBus;
        if (eventBus != null) {
            for (String subId : eventBusSubscriptionIds) {
                eventBus.unsubscribe(subId);
            }
        }
        eventBusSubscriptionIds.clear();
        playerTasks.clear();
        loadingTasks.clear();
        onTasksLoaded.clear();
        } finally {
            subscribedBus=null;
            callbacks.stopped().whenComplete((ignored,failure)->{
                try { closer.execute(()->{
                    for(var write:List.copyOf(pendingWrites)) {
                        write.run();
                        try { write.get(); }
                        catch(ExecutionException error) { /* done() records the original failure. */ }
                        catch(InterruptedException error) { Thread.currentThread().interrupt();throw new IllegalStateException(error); }
                    }
                }); } finally { closer.shutdown(); }
            });
        }
    }

    public CompletionStage<Void> databaseStopped() { return closer.stopped(); }
    private Runnable guard(Runnable action) { return ()->callbacks.call(()->{if(!stopping)action.run();return null;},null); }
    private void readAsync(Runnable action) { if(!stopping)Bukkit.getScheduler().runTaskAsynchronously(plugin,guard(action)); }
    private void onMain(Runnable action) { if(!stopping)Bukkit.getScheduler().runTask(plugin,guard(action)); }

    /** Normal writes keep Bukkit scheduling; shutdown rescues accepted jobs cancelled before they start. */
    private void writeAsync(Runnable action) {
        var write=new FutureTask<Void>(action,null) {
            @Override protected void done() {
                try { get(); }
                catch(ExecutionException failure) { logger.warning("钓鱼任务写入失败: "+failure.getCause()); }
                catch(InterruptedException failure) { Thread.currentThread().interrupt(); }
                finally { pendingWrites.remove(this); }
            }
        };
        synchronized(pendingWrites) { if(stopping)return;pendingWrites.add(write); }
        try { Bukkit.getScheduler().runTaskAsynchronously(plugin,guard(write)); }
        catch(RuntimeException | LinkageError failure) {
            logger.fine("钓鱼任务尚未调度，保留至停用收尾: "+failure.getMessage());
        }
    }

    /**
     * 更新玩家所有匹配任务的进度。
     *
     * @param player  玩家
     * @param topic   事件主题
     * @param payload 事件载荷
     */
    private void updateTasks(@NotNull Player player, @NotNull String topic,
                              @NotNull Map<String, String> payload) {
        UUID uuid = player.getUniqueId();
        Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap = playerTasks.get(uuid);
        if (categoryMap == null) return;
        ensurePeriodTasks(uuid, categoryMap, LocalDate.now());

        for (Map.Entry<FishingTask.TaskCategory, List<PlayerTaskInstance>> entry : categoryMap.entrySet()) {
            List<PlayerTaskInstance> updated = new ArrayList<>();
            for (PlayerTaskInstance instance : entry.getValue()) {
                if (instance.completed()) {
                    updated.add(instance);
                    continue;
                }
                FishingTask template = findTemplate(instance.templateId(), entry.getKey());
                if (template == null || !template.eventTopic().equals(topic)) {
                    updated.add(instance);
                    continue;
                }
                if (!template.matchesConditions(player, payload)) {
                    updated.add(instance);
                    continue;
                }
                int increment = template.calculateIncrement(player, payload);
                int progress = (int) Math.min(instance.targetCount(), (long) instance.currentProgress() + Math.max(0, increment));
                PlayerTaskInstance newInstance = instance.withProgress(progress);
                if (newInstance.completed()) {
                    FishingService service = serviceSupplier.get();
                    var result = service == null ? null : service.completeTask(newInstance, template.totalXpReward());
                    if (result == null) {
                        updated.add(instance);
                        continue;
                    }
                    if (result.awarded()) {
                        String msg = "§a任务完成: " + template.displayName() + " §7(+" + template.totalXpReward() + " XP)";
                        player.sendMessage(msg);
                    }
                } else {
                    persistAsync(newInstance);
                }
                updated.add(newInstance);
            }
            categoryMap.put(entry.getKey(), updated);
        }
    }

    /**
     * 为玩家分配今日任务（每日 + 每周 + 赛季）。
     *
     * @param uuid 玩家 UUID
     */
    public void assignTasks(@NotNull UUID uuid) {
        prepareTasks(uuid, null);
    }

    /** 已准备好返回 true，否则在恢复完成后刷新最后一次请求。 */
    public boolean prepareTasks(@NotNull UUID uuid, @Nullable Runnable afterLoad) {
        if(stopping)return false;
        LocalDate today = LocalDate.now();
        Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> existing = playerTasks.get(uuid);
        if (existing != null) {
            // 已分配过：纯内存补齐当前周期任务，不查库（主线程安全路径）
            ensurePeriodTasks(uuid, existing, today);
            return true;
        }
        if (afterLoad != null) onTasksLoaded.put(uuid, afterLoad);
        Object request = new Object();
        if (loadingTasks.putIfAbsent(uuid, request) != null) return false;
        // 首次分配：DB 恢复挪异步，回主线程装填+分配，避免 loadTaskProgress 卡主线程
        readAsync(() -> {
            List<PlayerTaskInstance> persisted;
            try {
                persisted = repository.loadTaskProgress(uuid, currentSeasonId);
            } catch (Exception e) {
                logger.warning("恢复任务进度失败 [" + uuid + "]: " + e.getMessage());
                persisted = null;
            }
            List<PlayerTaskInstance> recovered = persisted;
            onMain(() -> {
                if (!loadingTasks.remove(uuid, request)) return;
                Runnable callback = onTasksLoaded.remove(uuid);
                // 异步读库期间玩家可能已退出，跳过装填避免残留离线玩家的内存 entry
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline() || recovered == null) return;
                Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap = new HashMap<>();
                LocalDate fillDay = LocalDate.now(); // 用装填时刻日期，异步延迟跨天边界自动取新值
                for (PlayerTaskInstance p : recovered) {
                    if (isInstanceCurrent(p, fillDay)) {
                        categoryMap.computeIfAbsent(p.category(), k -> new ArrayList<>()).add(p);
                    }
                }
                ensurePeriodTasks(uuid, categoryMap, fillDay);
                playerTasks.put(uuid, categoryMap);
                if (callback != null) callback.run();
            });
        });
        return false;
    }

    /** 主线程内存分配：补齐 daily/weekly/season 当前周期缺失的任务实例。 */
    private void ensurePeriodTasks(@NotNull UUID uuid,
                                   @NotNull Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap,
                                   @NotNull LocalDate today) {
        // 每日任务
        List<PlayerTaskInstance> daily = categoryMap.get(FishingTask.TaskCategory.DAILY);
        if (daily == null || daily.isEmpty() || !daily.get(0).assignedDate().equals(today)) {
            daily = assignFromPool(uuid, dailyPool, FishingTask.TaskCategory.DAILY, dailyCount, today);
            categoryMap.put(FishingTask.TaskCategory.DAILY, daily);
            persistAllAsync(daily);
        }

        // 每周任务（周一重置）
        List<PlayerTaskInstance> weekly = categoryMap.get(FishingTask.TaskCategory.WEEKLY);
        if (weekly == null || weekly.isEmpty() || !isSameWeek(weekly.get(0).assignedDate(), today)) {
            weekly = assignFromPool(uuid, weeklyPool, FishingTask.TaskCategory.WEEKLY, weeklyCount, today.with(DayOfWeek.MONDAY));
            categoryMap.put(FishingTask.TaskCategory.WEEKLY, weekly);
            persistAllAsync(weekly);
        }

        // 赛季任务（全部激活）
        List<PlayerTaskInstance> season = categoryMap.get(FishingTask.TaskCategory.SEASON);
        if (season == null || season.isEmpty()) {
            season = new ArrayList<>();
            for (FishingTask task : seasonPool) {
                season.add(new PlayerTaskInstance(uuid, task.taskId(),
                    FishingTask.TaskCategory.SEASON, task.requiredCount(), today));
            }
            categoryMap.put(FishingTask.TaskCategory.SEASON, season);
            persistAllAsync(season);
        }
    }

    /** 判断持久化的任务实例是否仍处当前有效周期。 */
    private boolean isInstanceCurrent(@NotNull PlayerTaskInstance instance, @NotNull LocalDate today) {
        return switch (instance.category()) {
            case DAILY -> instance.assignedDate().equals(today);
            case WEEKLY -> isSameWeek(instance.assignedDate(), today);
            case SEASON -> true;
        };
    }

    private boolean isSameWeek(@NotNull LocalDate a, @NotNull LocalDate b) {
        return a.with(DayOfWeek.MONDAY).equals(b.with(DayOfWeek.MONDAY));
    }

    /** 异步保存单条任务实例进度。 */
    private void persistAsync(@NotNull PlayerTaskInstance instance) {
        writeAsync(() -> {
            try {
                repository.saveTaskProgress(instance, currentSeasonId);
            } catch (Exception e) {
                logger.warning("持久化任务进度失败 [" + instance.templateId() + "]: " + e.getMessage());
            }
        });
    }

    private void persistAllAsync(@NotNull List<PlayerTaskInstance> instances) {
        for (PlayerTaskInstance instance : instances) {
            persistAsync(instance);
        }
    }

    private List<PlayerTaskInstance> assignFromPool(@NotNull UUID uuid, @NotNull List<FishingTask> pool,
                                                     @NotNull FishingTask.TaskCategory category,
                                                     int count, @NotNull LocalDate date) {
        List<PlayerTaskInstance> result = new ArrayList<>();
        if (pool.isEmpty()) return result;
        List<FishingTask> candidates = new ArrayList<>(pool);
        candidates.sort(Comparator.comparing(FishingTask::taskId));
        // 同一玩家和周期在恢复或切服时保持相同的加权抽选结果。
        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ date.toEpochDay();
        Random random = new Random(seed ^ category.name().hashCode());
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            int totalWeight = candidates.stream().mapToInt(FishingTask::weight).sum();
            int roll = random.nextInt(totalWeight);
            int cumulative = 0;
            int selectedIdx = 0;
            for (int j = 0; j < candidates.size(); j++) {
                cumulative += candidates.get(j).weight();
                if (roll < cumulative) {
                    selectedIdx = j;
                    break;
                }
            }
            FishingTask selected = candidates.remove(selectedIdx);
            result.add(new PlayerTaskInstance(uuid, selected.taskId(),
                category, selected.requiredCount(), date));
        }
        return result;
    }

    /**
     * 获取玩家指定分类的任务实例列表。
     *
     * @param uuid     玩家 UUID
     * @param category 任务分类
     * @return 任务实例列表
     */
    public @NotNull List<PlayerTaskInstance> getTasks(@NotNull UUID uuid, @NotNull FishingTask.TaskCategory category) {
        Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap = playerTasks.get(uuid);
        if (categoryMap == null) return List.of();
        return categoryMap.getOrDefault(category, List.of());
    }

    /**
     * 获取玩家所有任务实例。
     *
     * @param uuid 玩家 UUID
     * @return 所有任务实例列表
     */
    public @NotNull List<PlayerTaskInstance> getAllTasks(@NotNull UUID uuid) {
        Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap = playerTasks.get(uuid);
        if (categoryMap == null) return List.of();
        List<PlayerTaskInstance> all = new ArrayList<>();
        all.addAll(categoryMap.getOrDefault(FishingTask.TaskCategory.DAILY, List.of()));
        all.addAll(categoryMap.getOrDefault(FishingTask.TaskCategory.WEEKLY, List.of()));
        all.addAll(categoryMap.getOrDefault(FishingTask.TaskCategory.SEASON, List.of()));
        return all;
    }

    /**
     * 查找任务模板。
     *
     * @param templateId 模板 ID
     * @param category   分类
     * @return 任务模板，不存在返回 null
     */
    public @Nullable FishingTask findTemplate(@NotNull String templateId, @NotNull FishingTask.TaskCategory category) {
        List<FishingTask> pool = switch (category) {
            case DAILY -> dailyPool;
            case WEEKLY -> weeklyPool;
            case SEASON -> seasonPool;
        };
        return pool.stream().filter(t -> t.taskId().equals(templateId)).findFirst().orElse(null);
    }

    /**
     * 玩家上线时分配任务。
     *
     * @param player 上线玩家
     */
    public void onPlayerJoin(@NotNull Player player) {
        assignTasks(player.getUniqueId());
    }

    /**
     * 玩家退出时清内存缓存（进度已实时持久化，重进时从库恢复）。
     */
    public void onPlayerQuit(@NotNull UUID uuid) {
        playerTasks.remove(uuid);
        loadingTasks.remove(uuid);
        onTasksLoaded.remove(uuid);
    }

    /**
     * 每日重置（0 点调用）。
     */
    public void dailyReset() {
        for (UUID uuid : playerTasks.keySet()) {
            Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap = playerTasks.get(uuid);
            categoryMap.remove(FishingTask.TaskCategory.DAILY);
        }
        // 全库清理过期每日任务行（含离线玩家）
        String today = LocalDate.now().toString();
        writeAsync(() -> repository.purgeTaskProgressBefore("DAILY", today));
        // 在线玩家重新分配
        for (Player player : Bukkit.getOnlinePlayers()) {
            assignTasks(player.getUniqueId());
        }
    }

    /**
     * 每周重置（周一 0 点调用）。
     */
    public void weeklyReset() {
        for (UUID uuid : playerTasks.keySet()) {
            Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap = playerTasks.get(uuid);
            categoryMap.remove(FishingTask.TaskCategory.WEEKLY);
        }
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();
        writeAsync(() -> repository.purgeTaskProgressBefore("WEEKLY", weekStart));
        for (Player player : Bukkit.getOnlinePlayers()) {
            assignTasks(player.getUniqueId());
        }
    }

    // ── 周期重置调度 ────────────────────────────────────────────

    /**
     * 启动每日/每周边界检测（每 60 秒一次）：跨过 0 点触发 {@link #dailyReset()}，
     * 跨周一触发 {@link #weeklyReset()}。停机跨边界时由 {@link #purgeStaleTaskProgress()}
     * 负责清理 DB 过期行，玩家侧由 {@code assignTasks} 的周期比对自愈。
     */
    private void startResetBoundaryScheduler() {
        LocalDate today = LocalDate.now();
        lastDailyDate = today;
        lastWeekMonday = today.with(DayOfWeek.MONDAY);
        resetBoundaryTask = Bukkit.getScheduler().runTaskTimer(plugin, guard(() -> {
            LocalDate now = LocalDate.now();
            if (!now.equals(lastDailyDate)) {
                lastDailyDate = now;
                dailyReset();
            }
            LocalDate monday = now.with(DayOfWeek.MONDAY);
            if (!monday.equals(lastWeekMonday)) {
                lastWeekMonday = monday;
                weeklyReset();
            }
        }), 1200L, 1200L);
    }

    /**
     * 启动时异步清理 DB 中已过期周期的任务行（停机跨天/跨周的兜底清理）。
     * 玩家侧正确性由 join 时的 {@code isInstanceCurrent} 过滤保证，此处仅为卫生清理。
     */
    private void purgeStaleTaskProgress() {
        String today = LocalDate.now().toString();
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();
        writeAsync(() -> {
            try {
                repository.purgeTaskProgressBefore("DAILY", today);
                repository.purgeTaskProgressBefore("WEEKLY", weekStart);
            } catch (Exception e) {
                logger.warning("清理过期任务进度失败: " + e.getMessage());
            }
        });
    }

}
