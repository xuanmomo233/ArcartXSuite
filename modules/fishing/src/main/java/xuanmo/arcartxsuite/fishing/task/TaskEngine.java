package xuanmo.arcartxsuite.fishing.task;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.capability.EventBusCapability;
import xuanmo.arcartxsuite.fishing.service.FishingService;

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

    // 玩家任务实例缓存：uuid → 分类 → 实例列表
    private final Map<UUID, Map<FishingTask.TaskCategory, List<PlayerTaskInstance>>> playerTasks = new HashMap<>();

    public TaskEngine(@NotNull List<FishingTask> dailyPool,
                      @NotNull List<FishingTask> weeklyPool,
                      @NotNull List<FishingTask> seasonPool,
                      int dailyCount,
                      int weeklyCount,
                      @Nullable Supplier<EventBusCapability> eventBusProvider,
                      @NotNull Supplier<FishingService> serviceSupplier,
                      @NotNull Logger logger) {
        this.dailyPool = dailyPool;
        this.weeklyPool = weeklyPool;
        this.seasonPool = seasonPool;
        this.dailyCount = dailyCount;
        this.weeklyCount = weeklyCount;
        this.eventBusProvider = eventBusProvider;
        this.serviceSupplier = serviceSupplier;
        this.logger = logger;
    }

    /**
     * 启动任务引擎，注册 EventBus 订阅。
     */
    public void start() {
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
            eventBus.subscribe(topic, event -> {
                try {
                    if (event.player() != null) {
                        updateTasks(event.player(), event.topic(), event.payload());
                    }
                } catch (Exception e) {
                    logger.warning("更新任务进度失败 [" + event.topic() + "]: " + e.getMessage());
                }
            });
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
        Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap = playerTasks.computeIfAbsent(uuid, k -> new HashMap<>());

        // 每日任务
        List<PlayerTaskInstance> daily = categoryMap.get(FishingTask.TaskCategory.DAILY);
        if (daily == null || daily.isEmpty() || !daily.get(0).assignedDate().equals(today)) {
            daily = assignFromPool(uuid, dailyPool, FishingTask.TaskCategory.DAILY, dailyCount, today);
            categoryMap.put(FishingTask.TaskCategory.DAILY, daily);
        }

        // 每周任务（周一重置）
        int weekNumber = today.getDayOfYear() / 7;
        List<PlayerTaskInstance> weekly = categoryMap.get(FishingTask.TaskCategory.WEEKLY);
        if (weekly == null || weekly.isEmpty()) {
            weekly = assignFromPool(uuid, weeklyPool, FishingTask.TaskCategory.WEEKLY, weeklyCount, today);
            categoryMap.put(FishingTask.TaskCategory.WEEKLY, weekly);
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
     * 每日重置（0 点调用）。
     */
    public void dailyReset() {
        for (UUID uuid : playerTasks.keySet()) {
            Map<FishingTask.TaskCategory, List<PlayerTaskInstance>> categoryMap = playerTasks.get(uuid);
            categoryMap.remove(FishingTask.TaskCategory.DAILY);
        }
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            assignTasks(player.getUniqueId());
        }
    }

    /**
     * 赛季重置（赛季结束时调用）。
     */
    public void seasonReset() {
        playerTasks.clear();
    }
}
