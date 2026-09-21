package xuanmo.arcartxsuite.fishing.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
    private final File dataFolder;
    private final String currentSeasonId;

    // 玩家任务实例缓存：uuid → 分类 → 实例列表
    private final Map<UUID, Map<FishingTask.TaskCategory, List<PlayerTaskInstance>>> playerTasks = new java.util.concurrent.ConcurrentHashMap<>();
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
                      @NotNull File dataFolder,
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
        this.dataFolder = dataFolder;
        this.currentSeasonId = currentSeasonId;
    }

    /**
     * 启动任务引擎：检测赛季切换、清理过期行、注册 EventBus 订阅并启动周期重置调度。
     */
    public void start() {
        checkSeasonRollover();
        startResetBoundaryScheduler();
        purgeStaleTaskProgress();

        EventBusCapability eventBus = eventBusProvider != null ? eventBusProvider.get() : null;
        if (eventBus == null) {
            logger.warning("EventBus 不可用，钓鱼任务引擎无法监听事件");
            return;
        }
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
                Bukkit.getScheduler().runTask(plugin, () -> {
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
        if (resetBoundaryTask != null) {
            resetBoundaryTask.cancel();
            resetBoundaryTask = null;
        }
        EventBusCapability eventBus = eventBusProvider != null ? eventBusProvider.get() : null;
        if (eventBus != null) {
            for (String subId : eventBusSubscriptionIds) {
                eventBus.unsubscribe(subId);
            }
        }
        eventBusSubscriptionIds.clear();
        playerTasks.clear();
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
                PlayerTaskInstance newInstance = instance.withProgress(instance.currentProgress() + increment);
                updated.add(newInstance);
                persistAsync(newInstance);

                // 任务完成时发放经验
                if (newInstance.completed() && !instance.completed()) {
                    FishingService service = serviceSupplier.get();
                    if (service != null) {
                        service.giveXp(uuid, template.totalXpReward());
                        String msg = "§a任务完成: " + template.displayName() + " §7(+" + template.totalXpReward() + " XP)";
                        player.sendMessage(msg);
                    }
                }
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
        LocalDate today = LocalDate.now();
        Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> existing = playerTasks.get(uuid);
        if (existing != null && !existing.isEmpty()) {
            // 已分配过：纯内存补齐当前周期任务，不查库（主线程安全路径）
            ensurePeriodTasks(uuid, existing, today);
            return;
        }
        // 首次分配：DB 恢复挪异步，回主线程装填+分配，避免 loadTaskProgress 卡主线程
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerTaskInstance> persisted;
            try {
                persisted = repository.loadTaskProgress(uuid);
            } catch (Exception e) {
                logger.warning("恢复任务进度失败 [" + uuid + "]: " + e.getMessage());
                persisted = List.of();
            }
            List<PlayerTaskInstance> recovered = persisted;
            Bukkit.getScheduler().runTask(plugin, () -> {
                // 异步读库期间玩家可能已退出，跳过装填避免残留离线玩家的内存 entry
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline()) return;
                Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap =
                    playerTasks.computeIfAbsent(uuid, k -> new HashMap<>());
                LocalDate fillDay = LocalDate.now(); // 用装填时刻日期，异步延迟跨天边界自动取新值
                for (PlayerTaskInstance p : recovered) {
                    if (isInstanceCurrent(p, fillDay)) {
                        categoryMap.computeIfAbsent(p.category(), k -> new ArrayList<>()).add(p);
                    }
                }
                ensurePeriodTasks(uuid, categoryMap, fillDay);
            });
        });
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
            weekly = assignFromPool(uuid, weeklyPool, FishingTask.TaskCategory.WEEKLY, weeklyCount, today);
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.saveTaskProgress(instance);
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
        // 按权重随机抽取
        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            int totalWeight = candidates.stream().mapToInt(FishingTask::weight).sum();
            int roll = ThreadLocalRandom.current().nextInt(totalWeight);
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.purgeTaskProgressBefore("DAILY", today));
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.purgeTaskProgressBefore("WEEKLY", weekStart));
        for (Player player : Bukkit.getOnlinePlayers()) {
            assignTasks(player.getUniqueId());
        }
    }

    /**
     * 赛季重置（赛季切换时调用）：仅清 SEASON 分类的任务进度，
     * 不动 daily/weekly——避免赛季切换被利用重刷当日/当周任务奖励。
     * 在线玩家随后重新分配新赛季任务。
     */
    public void seasonReset() {
        for (Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap : playerTasks.values()) {
            categoryMap.remove(FishingTask.TaskCategory.SEASON);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> repository.purgeTaskProgressByCategory("SEASON"));
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
        resetBoundaryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
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
        }, 1200L, 1200L);
    }

    /**
     * 启动时异步清理 DB 中已过期周期的任务行（停机跨天/跨周的兜底清理）。
     * 玩家侧正确性由 join 时的 {@code isInstanceCurrent} 过滤保证，此处仅为卫生清理。
     */
    private void purgeStaleTaskProgress() {
        String today = LocalDate.now().toString();
        String weekStart = LocalDate.now().with(DayOfWeek.MONDAY).toString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.purgeTaskProgressBefore("DAILY", today);
                repository.purgeTaskProgressBefore("WEEKLY", weekStart);
            } catch (Exception e) {
                logger.warning("清理过期任务进度失败: " + e.getMessage());
            }
        });
    }

    /**
     * 检测赛季 ID 是否变化（跨启动/跨 reload）：marker 文件记录上次生效的 seasonId，
     * 与当前配置不同则触发 {@link #seasonReset()} 清空旧赛季任务并更新 marker。
     */
    private void checkSeasonRollover() {
        File marker = new File(dataFolder, ".last-season-id");
        String lastSeasonId = null;
        if (marker.exists()) {
            try {
                lastSeasonId = Files.readString(marker.toPath(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                logger.warning("读取赛季标记失败: " + e.getMessage());
            }
        }
        if (currentSeasonId.equals(lastSeasonId)) {
            return;
        }
        if (lastSeasonId != null) {
            logger.info("检测到赛季切换: " + lastSeasonId + " -> " + currentSeasonId + "，清空赛季任务进度");
            seasonReset();
        }
        try {
            Files.writeString(marker.toPath(), currentSeasonId, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("写入赛季标记失败: " + e.getMessage());
        }
    }
}
