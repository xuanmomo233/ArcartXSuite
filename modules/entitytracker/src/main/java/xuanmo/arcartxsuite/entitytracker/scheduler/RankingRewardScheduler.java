package xuanmo.arcartxsuite.entitytracker.scheduler;

import xuanmo.arcartxsuite.entitytracker.service.RankingRewardService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 排行榜奖励定时任务调度器
 */
public class RankingRewardScheduler {
    private final Logger logger;
    private final RankingRewardService rewardService;
    private final ScheduledExecutorService scheduler;
    private final JavaPlugin plugin;
    
    private ScheduledFuture<?> weeklyTask;
    private ScheduledFuture<?> monthlyTask;
    
    // 配置参数
    private boolean weeklyEnabled;
    private DayOfWeek weeklyDay;
    private int weeklyHour;
    private int weeklyMinute;
    private String timeZone;
    
    private boolean monthlyEnabled;
    private int monthlyDay;
    private int monthlyHour;
    private int monthlyMinute;

    public RankingRewardScheduler(RankingRewardService rewardService, JavaPlugin plugin, Logger logger) {
        this.rewardService = rewardService;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * 初始化调度器
     */
    public void initialize(ConfigurationSection config) {
        loadConfiguration(config);
        
        if (weeklyEnabled) {
            scheduleWeeklyRewards();
        }
        
        if (monthlyEnabled) {
            scheduleMonthlyRewards();
        }
        
        logger.info("排行榜奖励调度器已初始化: 周奖励=" + weeklyEnabled + ", 月奖励=" + monthlyEnabled);
    }

    /**
     * 加载配置
     */
    private void loadConfiguration(ConfigurationSection config) {
        ConfigurationSection rewardRoot = config.getConfigurationSection("new-features.ranking-rewards");
        if (rewardRoot == null) {
            logger.warning("未找到奖励调度配置，使用默认值");
            loadDefaultConfiguration();
            return;
        }

        ConfigurationSection scheduleConfig = rewardRoot.getConfigurationSection("schedule");

        // 周奖励配置
        ConfigurationSection weeklyConfig = scheduleConfig != null ? scheduleConfig.getConfigurationSection("weekly") : null;
        if (weeklyConfig != null) {
            weeklyEnabled = weeklyConfig.getBoolean("enabled", false);
            String dayOfWeek = weeklyConfig.getString("day-of-week", "MONDAY");
            weeklyDay = DayOfWeek.valueOf(dayOfWeek.toUpperCase());
            weeklyHour = weeklyConfig.getInt("hour", 0);
            weeklyMinute = weeklyConfig.getInt("minute", 0);
        }

        // 月奖励配置
        ConfigurationSection monthlyConfig = scheduleConfig != null ? scheduleConfig.getConfigurationSection("monthly") : null;
        if (monthlyConfig != null) {
            monthlyEnabled = monthlyConfig.getBoolean("enabled", false);
            monthlyDay = monthlyConfig.getInt("day-of-month", 1);
            monthlyHour = monthlyConfig.getInt("hour", 0);
            monthlyMinute = monthlyConfig.getInt("minute", 0);
        }

        // 时区配置（在 ranking-rewards 根节点）
        timeZone = rewardRoot.getString("timezone", "Asia/Shanghai");
    }

    /**
     * 加载默认配置
     */
    private void loadDefaultConfiguration() {
        weeklyEnabled = true;
        weeklyDay = DayOfWeek.MONDAY;
        weeklyHour = 0;
        weeklyMinute = 0;
        
        monthlyEnabled = true;
        monthlyDay = 1;
        monthlyHour = 0;
        monthlyMinute = 0;
        
        timeZone = "Asia/Shanghai";
    }

    /**
     * 调度周奖励
     */
    private void scheduleWeeklyRewards() {
        LocalDateTime nextExecution = calculateNextWeeklyExecution();
        long initialDelay = calculateDelayMillis(nextExecution);
        
        weeklyTask = scheduler.schedule(() -> {
            try {
                logger.info("开始执行周奖励发放任务");
                executeWeeklyRewards();
            } catch (Exception e) {
                logger.severe("周奖励发放任务执行失败: " + e.getMessage());
            } finally {
                // 动态重新调度下一次
                if (weeklyEnabled) {
                    scheduleWeeklyRewards();
                }
            }
        }, initialDelay, TimeUnit.MILLISECONDS);
        
        logger.info("周奖励任务已调度: 下次执行时间=" + nextExecution);
    }

    /**
     * 调度月奖励
     */
    private void scheduleMonthlyRewards() {
        LocalDateTime nextExecution = calculateNextMonthlyExecution();
        long initialDelay = calculateDelayMillis(nextExecution);
        
        monthlyTask = scheduler.schedule(() -> {
            try {
                logger.info("开始执行月奖励发放任务");
                executeMonthlyRewards();
            } catch (Exception e) {
                logger.severe("月奖励发放任务执行失败: " + e.getMessage());
            } finally {
                // 动态重新调度下一次（根据实际月份长度计算）
                if (monthlyEnabled) {
                    scheduleMonthlyRewards();
                }
            }
        }, initialDelay, TimeUnit.MILLISECONDS);
        
        logger.info("月奖励任务已调度: 下次执行时间=" + nextExecution);
    }

    /**
     * 计算下一个周奖励执行时间
     */
    private LocalDateTime calculateNextWeeklyExecution() {
        ZoneId zoneId = ZoneId.of(timeZone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        LocalDateTime nextExecution = now
            .with(TemporalAdjusters.nextOrSame(weeklyDay))
            .withHour(weeklyHour)
            .withMinute(weeklyMinute)
            .withSecond(0)
            .withNano(0);
        
        // 如果当前时间已过，推迟到下周
        if (nextExecution.isBefore(now) || nextExecution.isEqual(now)) {
            nextExecution = nextExecution.plusWeeks(1);
        }
        
        return nextExecution;
    }

    /**
     * 计算下一个月奖励执行时间
     */
    private LocalDateTime calculateNextMonthlyExecution() {
        ZoneId zoneId = ZoneId.of(timeZone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        // 计算本月的目标日期
        int targetDay = Math.min(monthlyDay, now.toLocalDate().lengthOfMonth());
        LocalDateTime nextExecution = now
            .withDayOfMonth(targetDay)
            .withHour(monthlyHour)
            .withMinute(monthlyMinute)
            .withSecond(0)
            .withNano(0);
        
        // 如果当前时间已过，推迟到下月
        if (nextExecution.isBefore(now) || nextExecution.isEqual(now)) {
            nextExecution = nextExecution.plusMonths(1);
            // 确保下月也有该日期
            int nextMonthLength = nextExecution.toLocalDate().lengthOfMonth();
            if (monthlyDay > nextMonthLength) {
                nextExecution = nextExecution.withDayOfMonth(nextMonthLength);
            }
        }
        
        return nextExecution;
    }

    /**
     * 计算延迟时间（毫秒）
     */
    private long calculateDelayMillis(LocalDateTime targetTime) {
        ZoneId zoneId = ZoneId.of(timeZone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        long millis = java.time.Duration.between(now, targetTime).toMillis();
        // 保护：若延迟 <= 0 则至少等 1 秒，防止立即执行造成死循环
        return Math.max(millis, 1000L);
    }

    /**
     * 执行周奖励发放
     */
    private void executeWeeklyRewards() {
        ZoneId zoneId = ZoneId.of(timeZone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        // 计算上周的时间范围
        LocalDateTime periodStart = now.minusWeeks(1)
            .with(TemporalAdjusters.previousOrSame(weeklyDay))
            .withHour(weeklyHour)
            .withMinute(weeklyMinute)
            .withSecond(0)
            .withNano(0);
        
        LocalDateTime periodEnd = now
            .withHour(weeklyHour)
            .withMinute(weeklyMinute)
            .withSecond(0)
            .withNano(0);
        
        logger.info("执行周奖励发放: 周期=" + periodStart + " - " + periodEnd);
        
        rewardService.distributeRewards("weekly", periodStart, periodEnd)
            .thenAccept(totalRewards -> {
                logger.info("周奖励发放完成: 总奖励数量=" + totalRewards);
                broadcastRewardResult("周", totalRewards);
            })
            .exceptionally(throwable -> {
                logger.severe("周奖励发放失败: " + throwable.getMessage());
                return null;
            });
    }

    /**
     * 执行月奖励发放
     */
    private void executeMonthlyRewards() {
        ZoneId zoneId = ZoneId.of(timeZone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        // 计算上月的时间范围
        LocalDateTime periodStart = now.minusMonths(1)
            .withDayOfMonth(monthlyDay)
            .withHour(monthlyHour)
            .withMinute(monthlyMinute)
            .withSecond(0)
            .withNano(0);
        
        LocalDateTime periodEnd = now
            .withDayOfMonth(monthlyDay)
            .withHour(monthlyHour)
            .withMinute(monthlyMinute)
            .withSecond(0)
            .withNano(0);
        
        logger.info("执行月奖励发放: 周期=" + periodStart + " - " + periodEnd);
        
        rewardService.distributeRewards("monthly", periodStart, periodEnd)
            .thenAccept(totalRewards -> {
                logger.info("月奖励发放完成: 总奖励数量=" + totalRewards);
                broadcastRewardResult("月", totalRewards);
            })
            .exceptionally(throwable -> {
                logger.severe("月奖励发放失败: " + throwable.getMessage());
                return null;
            });
    }

    /**
     * 广播奖励发放结果
     */
    private void broadcastRewardResult(String periodType, int totalRewards) {
        String message = String.format("§6§l%s排行榜奖励发放完成！§e共发放 §a%d §e个奖励", periodType, totalRewards);
        plugin.getServer().broadcastMessage(message);
    }

    /**
     * 手动触发周奖励发放
     */
    public CompletableFuture<Integer> manualTriggerWeeklyRewards() {
        ZoneId zoneId = ZoneId.of(timeZone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        LocalDateTime periodStart = now.minusWeeks(1);
        LocalDateTime periodEnd = now;
        
        logger.info("手动触发周奖励发放: 周期=" + periodStart + " - " + periodEnd);
        
        return rewardService.distributeRewards("weekly", periodStart, periodEnd);
    }

    /**
     * 手动触发月奖励发放
     */
    public CompletableFuture<Integer> manualTriggerMonthlyRewards() {
        ZoneId zoneId = ZoneId.of(timeZone);
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        LocalDateTime periodStart = now.minusMonths(1);
        LocalDateTime periodEnd = now;
        
        logger.info("手动触发月奖励发放: 周期=" + periodStart + " - " + periodEnd);
        
        return rewardService.distributeRewards("monthly", periodStart, periodEnd);
    }

    /**
     * 更新调度配置
     */
    public void updateSchedule(ConfigurationSection config) {
        // 停止现有任务
        stopAllTasks();
        
        // 重新加载配置
        loadConfiguration(config);
        
        // 重新调度任务
        if (weeklyEnabled) {
            scheduleWeeklyRewards();
        }
        
        if (monthlyEnabled) {
            scheduleMonthlyRewards();
        }
        
        logger.info("奖励调度已更新: 周奖励=" + weeklyEnabled + ", 月奖励=" + monthlyEnabled);
    }

    /**
     * 停止所有任务
     */
    public void stopAllTasks() {
        if (weeklyTask != null && !weeklyTask.isCancelled()) {
            weeklyTask.cancel(false);
            weeklyTask = null;
        }
        
        if (monthlyTask != null && !monthlyTask.isCancelled()) {
            monthlyTask.cancel(false);
            monthlyTask = null;
        }
        
        logger.info("所有奖励调度任务已停止");
    }

    /**
     * 获取下次执行时间信息
     */
    public String getNextExecutionInfo() {
        StringBuilder info = new StringBuilder();
        
        if (weeklyEnabled && weeklyTask != null) {
            LocalDateTime nextWeekly = calculateNextWeeklyExecution();
            info.append(String.format("下次周奖励: %s\n", nextWeekly));
        }
        
        if (monthlyEnabled && monthlyTask != null) {
            LocalDateTime nextMonthly = calculateNextMonthlyExecution();
            info.append(String.format("下次月奖励: %s\n", nextMonthly));
        }
        
        if (info.isEmpty()) {
            info.append("未启用自动奖励发放");
        }
        
        return info.toString().trim();
    }

    /**
     * 检查任务状态
     */
    public boolean isWeeklyTaskRunning() {
        return weeklyTask != null && !weeklyTask.isCancelled();
    }

    public boolean isMonthlyTaskRunning() {
        return monthlyTask != null && !monthlyTask.isCancelled();
    }

    /**
     * 关闭调度器
     */
    public void shutdown() {
        stopAllTasks();
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("排行榜奖励调度器已关闭");
    }
}



