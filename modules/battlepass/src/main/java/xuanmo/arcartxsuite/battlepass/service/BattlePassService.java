package xuanmo.arcartxsuite.battlepass.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
import xuanmo.arcartxsuite.battlepass.config.BattlePassModuleConfiguration;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.model.BattlePassReward;
import xuanmo.arcartxsuite.battlepass.model.BattlePassSeason;
import xuanmo.arcartxsuite.battlepass.model.BattlePassTask;
import xuanmo.arcartxsuite.battlepass.model.PlayerTaskInstance;
import xuanmo.arcartxsuite.battlepass.storage.BattlePassRepository;

public final class BattlePassService {

    private final JavaPlugin plugin;
    private final BattlePassModuleConfiguration configuration;
    private final BattlePassRepository repository;
    private final Logger logger;
    private final BattlePassRewardDispatcher rewardDispatcher;
    private final BattlePassTaskEngine taskEngine;

    private final Map<UUID, BattlePassPlayerProgress> cachedProgress = new ConcurrentHashMap<>();
    private final Map<UUID, List<PlayerTaskInstance>> cachedInstances = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> dirtyInstances = ConcurrentHashMap.newKeySet();
    private BukkitTask saveTask;
    private BukkitTask resetTask;
    private volatile boolean active;

    private Supplier<EventBusCapability> eventBusProvider;
    private final TaskAssignmentService assignmentService = new TaskAssignmentService();

    public BattlePassService(
        JavaPlugin plugin,
        BattlePassModuleConfiguration configuration,
        BattlePassRepository repository,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.repository = repository;
        this.logger = logger;
        this.rewardDispatcher = new BattlePassRewardDispatcher(plugin, logger);
        this.taskEngine = new BattlePassTaskEngine(this, configuration);
    }

    public void setEventBusProvider(Supplier<EventBusCapability> eventBusProvider) {
        this.eventBusProvider = eventBusProvider;
    }

    public void start() {
        active = true;
        taskEngine.start(eventBusProvider);
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushDirtyProgress, 1200L, 1200L);
        resetTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkResets, 6000L, 6000L);
    }

    public void shutdown() {
        active = false;
        taskEngine.shutdown();
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
        flushDirtyProgress();
        cachedProgress.clear();
        cachedInstances.clear();
        dirtyPlayers.clear();
        dirtyInstances.clear();
    }

    public BattlePassPlayerProgress getProgress(Player player) {
        return cachedProgress.computeIfAbsent(player.getUniqueId(), uuid -> {
            try {
                return repository.loadProgress(uuid, configuration.season().seasonId());
            } catch (SQLException e) {
                logger.warning("加载战令进度失败: " + e.getMessage());
                return new BattlePassPlayerProgress(uuid, configuration.season().seasonId());
            }
        });
    }

    public List<PlayerTaskInstance> getTaskInstances(Player player) {
        return cachedInstances.computeIfAbsent(player.getUniqueId(), uuid -> {
            try {
                List<PlayerTaskInstance> loaded = repository.loadPlayerTasks(uuid, configuration.season().seasonId());
                if (loaded.isEmpty()) {
                    // 首次加载：自动分配当日/当周任务
                    LocalDate today = LocalDate.now();
                    BattlePassPlayerProgress progress = getProgress(player);
                    List<PlayerTaskInstance> assigned = new ArrayList<>();
                    assigned.addAll(assignmentService.assignDailyTasks(
                        uuid, configuration.tasks().daily(), configuration.tasks().dailyCount(), today));
                    assigned.addAll(assignmentService.assignWeeklyTasks(
                        uuid, configuration.tasks().weekly(), configuration.tasks().weeklyCount(),
                        progress.currentWeekNumber(), today));
                    String seasonId = configuration.season().seasonId();
                    for (PlayerTaskInstance inst : assigned) {
                        repository.savePlayerTask(uuid, seasonId, inst);
                    }
                    return Collections.unmodifiableList(assigned);
                }
                return loaded;
            } catch (SQLException e) {
                logger.warning("加载玩家任务实例失败: " + e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    public List<PlayerTaskInstance> getActiveTaskInstances(Player player) {
        return getTaskInstances(player).stream()
            .filter(i -> !i.completed())
            .toList();
    }

    public void updateInstanceProgress(Player player, PlayerTaskInstance instance, int increment) {
        if (!active || increment <= 0) return;
        List<PlayerTaskInstance> instances = new ArrayList<>(getTaskInstances(player));
        int idx = -1;
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).instanceId().equals(instance.instanceId())) {
                idx = i;
                break;
            }
        }
        if (idx == -1) return;

        PlayerTaskInstance updatedInstance = instances.get(idx).withProgress(
            Math.min(instance.currentProgress() + increment, instance.targetCount())
        );
        if (updatedInstance.currentProgress() >= updatedInstance.targetCount() && !updatedInstance.completed()) {
            updatedInstance = updatedInstance.withCompleted(true);
            BattlePassTask task = findTask(updatedInstance.templateId());
            if (task != null) {
                BattlePassPlayerProgress progress = getProgress(player);
                int xp = Math.round(task.totalXpReward() * progress.xpMultiplier());
                BattlePassPlayerProgress updatedProgress = addXp(progress, xp);
                cachedProgress.put(player.getUniqueId(), updatedProgress);
                dirtyPlayers.add(player.getUniqueId());
                logger.fine("玩家 " + player.getName() + " 完成任务 " + task.taskId() + " 获得 " + xp + " XP");
            }
        }
        instances.set(idx, updatedInstance);
        cachedInstances.put(player.getUniqueId(), Collections.unmodifiableList(instances));
        dirtyInstances.add(player.getUniqueId());
    }

    public void updateTaskProgress(Player player, String taskId, int increment) {
        if (!active || increment <= 0) return;
        // 优先更新 PlayerTaskInstance
        List<PlayerTaskInstance> activeInstances = getActiveTaskInstances(player);
        for (PlayerTaskInstance instance : activeInstances) {
            if (instance.templateId().equals(taskId)) {
                updateInstanceProgress(player, instance, increment);
            }
        }
    }

    public BattlePassPlayerProgress addXp(BattlePassPlayerProgress progress, int xp) {
        if (xp <= 0) return progress;
        int newXp = progress.currentXp() + xp;
        int newLevel = progress.currentLevel();
        int xpPerLevel = configuration.season().xpPerLevel();
        int maxLevel = configuration.season().maxLevel();

        while (newXp >= xpPerLevel && newLevel < maxLevel) {
            newXp -= xpPerLevel;
            newLevel++;
        }
        if (newLevel >= maxLevel) {
            newXp = Math.min(newXp, xpPerLevel - 1);
        }
        return progress.withLevel(newLevel).withXp(newXp);
    }

    public boolean claimReward(Player player, int level) {
        BattlePassPlayerProgress progress = getProgress(player);
        if (progress.currentLevel() < level) return false;

        for (BattlePassReward reward : configuration.rewards()) {
            if (reward.level() != level) continue;
            String rewardId = reward.rewardId();
            if (progress.claimedRewards().contains(rewardId)) continue;
            if (reward.tier() == BattlePassReward.RewardTier.PREMIUM && !progress.unlockedPremium()) continue;
            if (reward.tier() == BattlePassReward.RewardTier.DELUXE && !progress.unlockedDeluxe()) continue;

            rewardDispatcher.dispatch(player, reward);
            cachedProgress.put(player.getUniqueId(), progress.withClaimedReward(rewardId));
            dirtyPlayers.add(player.getUniqueId());
        }
        return true;
    }

    public boolean unlockTier(Player player, BattlePassPlayerProgress.PassTier tier) {
        BattlePassPlayerProgress progress = getProgress(player);
        if (progress.passTier().ordinal() >= tier.ordinal()) return false;
        cachedProgress.put(player.getUniqueId(), progress.withPassTier(tier));
        dirtyPlayers.add(player.getUniqueId());
        return true;
    }

    public void resetPlayerProgress(UUID playerUuid) {
        try {
            repository.resetPlayerProgress(playerUuid, configuration.season().seasonId());
        } catch (SQLException e) {
            logger.warning("重置战令进度失败: " + e.getMessage());
        }
        cachedProgress.remove(playerUuid);
        cachedInstances.remove(playerUuid);
        dirtyPlayers.remove(playerUuid);
        dirtyInstances.remove(playerUuid);
    }

    public @Nullable BattlePassTask findTask(String taskId) {
        for (BattlePassTask task : configuration.tasks().all()) {
            if (task.taskId().equals(taskId)) return task;
        }
        return null;
    }

    public BattlePassModuleConfiguration configuration() {
        return configuration;
    }

    public BattlePassRepository repository() {
        return repository;
    }

    private void flushDirtyProgress() {
        if (dirtyPlayers.isEmpty() && dirtyInstances.isEmpty()) return;
        for (UUID uuid : dirtyPlayers) {
            BattlePassPlayerProgress progress = cachedProgress.get(uuid);
            if (progress == null) continue;
            try {
                repository.saveProgress(progress);
            } catch (SQLException e) {
                logger.warning("保存战令进度失败 [" + uuid + "]: " + e.getMessage());
            }
        }
        for (UUID uuid : dirtyInstances) {
            List<PlayerTaskInstance> instances = cachedInstances.get(uuid);
            if (instances == null) continue;
            String seasonId = configuration.season().seasonId();
            for (PlayerTaskInstance instance : instances) {
                try {
                    repository.savePlayerTask(uuid, seasonId, instance);
                } catch (SQLException e) {
                    logger.warning("保存任务实例失败 [" + uuid + "/" + instance.instanceId() + "]: " + e.getMessage());
                }
            }
        }
        dirtyPlayers.clear();
        dirtyInstances.clear();
    }

    private void checkResets() {
        LocalDate today = LocalDate.now();
        for (Map.Entry<UUID, BattlePassPlayerProgress> entry : cachedProgress.entrySet()) {
            BattlePassPlayerProgress progress = entry.getValue();
            UUID uuid = entry.getKey();
            boolean dailyNeedsReset = progress.lastDailyResetDate() == null || !progress.lastDailyResetDate().equals(today);
            boolean weeklyNeedsReset = progress.lastWeeklyResetDate() == null
                || ChronoUnit.DAYS.between(progress.lastWeeklyResetDate(), today) >= 7;
            if (dailyNeedsReset || weeklyNeedsReset) {
                Bukkit.getScheduler().runTask(plugin, () -> performReset(uuid, dailyNeedsReset, weeklyNeedsReset));
            }
        }
    }

    private void performReset(UUID playerUuid, boolean daily, boolean weekly) {
        BattlePassPlayerProgress progress = cachedProgress.get(playerUuid);
        if (progress == null) return;
        String seasonId = configuration.season().seasonId();
        LocalDate today = LocalDate.now();

        if (daily) {
            try {
                repository.deletePlayerTasksByCategory(playerUuid, seasonId, BattlePassTask.TaskCategory.DAILY);
            } catch (SQLException e) {
                logger.warning("删除旧每日任务失败: " + e.getMessage());
            }
            List<PlayerTaskInstance> dailyInstances = assignmentService.assignDailyTasks(
                playerUuid, configuration.tasks().daily(), configuration.tasks().dailyCount(), today
            );
            List<PlayerTaskInstance> allInstances = new ArrayList<>(dailyInstances);
            List<PlayerTaskInstance> existing = cachedInstances.getOrDefault(playerUuid, Collections.emptyList());
            for (PlayerTaskInstance inst : existing) {
                if (inst.category() != BattlePassTask.TaskCategory.DAILY) {
                    allInstances.add(inst);
                }
            }
            cachedInstances.put(playerUuid, Collections.unmodifiableList(allInstances));
            dirtyInstances.add(playerUuid);
            progress = progress.withDailyReset(today);
        }

        if (weekly) {
            try {
                repository.deletePlayerTasksByCategory(playerUuid, seasonId, BattlePassTask.TaskCategory.WEEKLY);
            } catch (SQLException e) {
                logger.warning("删除旧每周任务失败: " + e.getMessage());
            }
            int newWeek = progress.currentWeekNumber() + 1;
            List<PlayerTaskInstance> weeklyInstances = assignmentService.assignWeeklyTasks(
                playerUuid, configuration.tasks().weekly(), configuration.tasks().weeklyCount(), newWeek, today
            );
            List<PlayerTaskInstance> allInstances = new ArrayList<>(weeklyInstances);
            List<PlayerTaskInstance> existing = cachedInstances.getOrDefault(playerUuid, Collections.emptyList());
            for (PlayerTaskInstance inst : existing) {
                if (inst.category() != BattlePassTask.TaskCategory.WEEKLY) {
                    allInstances.add(inst);
                }
            }
            cachedInstances.put(playerUuid, Collections.unmodifiableList(allInstances));
            dirtyInstances.add(playerUuid);
            progress = progress.withWeeklyReset(today, newWeek);
        }

        cachedProgress.put(playerUuid, progress);
        dirtyPlayers.add(playerUuid);
    }
}
