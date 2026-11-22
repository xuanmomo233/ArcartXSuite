package xuanmo.arcartxsuite.entitytracker.scheduler;

import xuanmo.arcartxsuite.entitytracker.service.RankingRewardService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CompletableFuture;
import xuanmo.arcartxsuite.api.scheduler.ModuleScheduledExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 排行榜奖励定时任务调度器
 */
public class RankingRewardScheduler {
    private final Logger logger;
    private final RankingRewardService rewardService;
    private final ModuleScheduledExecutor scheduler;
    private volatile boolean closed;
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
        this.scheduler = new ModuleScheduledExecutor(2, runnable -> {
            Thread thread = new Thread(runnable, "AXS-EntityTracker-Reward");
            thread.setDaemon(true);
            return thread;
        });
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
            monthlyDay = Math.max(1, Math.min(31, monthlyConfig.getInt("day-of-month", 1)));
            monthlyHour = Math.max(0, Math.min(23, monthlyConfig.getInt("hour", 0)));
            monthlyMinute = Math.max(0, Math.min(59, monthlyConfig.getInt("minute", 0)));
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
    private synchronized void scheduleWeeklyRewards() {
        if (closed) return;
        ZonedDateTime nextExecution = calculateNextWeeklyExecution();
        long initialDelay = calculateDelayMillis(nextExecution);
        
        weeklyTask = scheduler.schedule(() -> {
            try {
                logger.info("开始执行周奖励发放任务");
                executeRewards("weekly", weeklyBoundary(nextExecution.minusWeeks(1)), nextExecution);
            } catch (Exception e) {
                logger.severe("周奖励发放任务执行失败: " + e.getMessage());
            } finally {
                // 动态重新调度下一次
                if (weeklyEnabled && !closed) {
                    scheduleWeeklyRewards();
                }
            }
        }, initialDelay, TimeUnit.MILLISECONDS);
        
        logger.info("周奖励任务已调度: 下次执行时间=" + nextExecution);
    }

    /**
     * 调度月奖励
     */
    private synchronized void scheduleMonthlyRewards() {
        if (closed) return;
        ZonedDateTime nextExecution = calculateNextMonthlyExecution();
        long initialDelay = calculateDelayMillis(nextExecution);
        
        monthlyTask = scheduler.schedule(() -> {
            try {
                logger.info("开始执行月奖励发放任务");
                executeRewards("monthly", monthBoundary(YearMonth.from(nextExecution).minusMonths(1)), nextExecution);
            } catch (Exception e) {
                logger.severe("月奖励发放任务执行失败: " + e.getMessage());
            } finally {
                // 动态重新调度下一次（根据实际月份长度计算）
                if (monthlyEnabled && !closed) {
                    scheduleMonthlyRewards();
                }
            }
        }, initialDelay, TimeUnit.MILLISECONDS);
        
        logger.info("月奖励任务已调度: 下次执行时间=" + nextExecution);
    }

    private ZonedDateTime calculateNextWeeklyExecution() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
        ZonedDateTime boundary = weeklyBoundary(now);
        return boundary.isAfter(now) ? boundary : weeklyBoundary(now.plusWeeks(1));
    }

    private ZonedDateTime weeklyBoundary(ZonedDateTime date) {
        return date.toLocalDate().with(TemporalAdjusters.previousOrSame(weeklyDay))
            .atTime(weeklyHour, weeklyMinute).atZone(date.getZone());
    }

    private ZonedDateTime calculateNextMonthlyExecution() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
        YearMonth month = YearMonth.from(now);
        ZonedDateTime boundary = monthBoundary(month);
        return boundary.isAfter(now) ? boundary : monthBoundary(month.plusMonths(1));
    }

    private ZonedDateTime monthBoundary(YearMonth month) {
        return month.atDay(Math.min(monthlyDay, month.lengthOfMonth()))
            .atTime(monthlyHour, monthlyMinute).atZone(ZoneId.of(timeZone));
    }

    private long calculateDelayMillis(ZonedDateTime targetTime) {
        return Math.max(1000L, java.time.Duration.between(java.time.Instant.now(), targetTime.toInstant()).toMillis());
    }

    private void executeRewards(String type, ZonedDateTime start, ZonedDateTime end) {
        rewardService.distributeRewards(type, start, end).whenComplete((count, error) -> {
            if (error != null) {
                logger.warning("排行榜奖励发放失败: " + error.getMessage());
            } else if (count > 0 && plugin.isEnabled() && !scheduler.isShutdown()) {
                String period = "weekly".equals(type) ? "周" : "月";
                rewardService.onMainIfActive(() -> {
                    if (!scheduler.isShutdown()) {
                        plugin.getServer().broadcastMessage("§6" + period + "排行榜奖励已发放 §a" + count + " §6份");
                    }
                });
            }
        });
    }

    public CompletableFuture<Integer> manualTriggerWeeklyRewards() {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Reward scheduler closed"));
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
        ZonedDateTime end = weeklyBoundary(now);
        if (end.isAfter(now)) end = weeklyBoundary(end.minusWeeks(1));
        return rewardService.distributeRewards("weekly", weeklyBoundary(end.minusWeeks(1)), end);
    }

    public CompletableFuture<Integer> manualTriggerMonthlyRewards() {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Reward scheduler closed"));
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timeZone));
        YearMonth month = YearMonth.from(now);
        ZonedDateTime end = monthBoundary(month);
        if (end.isAfter(now)) {
            month = month.minusMonths(1);
            end = monthBoundary(month);
        }
        return rewardService.distributeRewards("monthly", monthBoundary(month.minusMonths(1)), end);
    }

    /**
     * 更新调度配置
     */
    public synchronized void updateSchedule(ConfigurationSection config) {
        if (closed) return;
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
            ZonedDateTime nextWeekly = calculateNextWeeklyExecution();
            info.append(String.format("下次周奖励: %s\n", nextWeekly));
        }
        
        if (monthlyEnabled && monthlyTask != null) {
            ZonedDateTime nextMonthly = calculateNextMonthlyExecution();
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
    public synchronized void shutdown() {
        closed=true;
        stopAllTasks();
        scheduler.shutdown();
        
        logger.info("排行榜奖励调度器已关闭");
    }

    public java.util.concurrent.CompletionStage<Void> databaseStopped(){return scheduler.stopped();}
}



