package xuanmo.arcartxsuite.qqbot.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.PointAccount;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

/**
 * 签到积分周结算排行榜服务。
 * 每周日 23:59 触发，推送 Top10 到 QQ 群。
 */
public final class QQBotWeeklyRankService {

    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final QQBotRepository repository;
    private final QQBotBindService bindService;
    private final Consumer<String> broadcast;
    private final Logger logger;

    private BukkitTask task;

    public QQBotWeeklyRankService(
        JavaPlugin plugin,
        QQBotConfiguration config,
        QQBotRepository repository,
        QQBotBindService bindService,
        Consumer<String> broadcast,
        Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.bindService = bindService;
        this.broadcast = broadcast;
        this.logger = logger;
    }

    public void start() {
        long delayTicks = calculateDelayToNextSunday();
        // 首次调度：等到下周日 23:59
        task = plugin.getServer().getScheduler().runTaskLater(plugin, this::runWeeklyRank, delayTicks);
        logger.info("[QQBot] 周结算排行榜已调度，下次触发约 " + (delayTicks / 20 / 60) + " 分钟后");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void runWeeklyRank() {
        try {
            List<PointAccount> top = repository.getPointsLeaderboard(10);
            StringBuilder sb = new StringBuilder("═══ 本周积分排行榜 Top10 ═══");
            if (top.isEmpty()) {
                sb.append("\n（暂无数据）");
            } else {
                int rank = 1;
                for (PointAccount acc : top) {
                    QQBotBinding binding = bindService.findByQq(acc.qqId());
                    String name = binding != null ? binding.playerName() : String.valueOf(acc.qqId());
                    sb.append("\n").append(rank++).append(". ").append(name)
                        .append(" - ").append(acc.balance()).append("积分");
                }
            }
            String message = sb.toString();
            if (broadcast != null) {
                broadcast.accept(message);
            }
        } catch (Exception e) {
            logger.warning("[QQBot] 周结算排行榜异常: " + e.getMessage());
        }
        // 重新调度下周
        long nextDelay = 7 * 24 * 60 * 60 * 20L; // 7 天 ticks
        task = plugin.getServer().getScheduler().runTaskLater(plugin, this::runWeeklyRank, nextDelay);
    }

    /** 计算到下周日 23:59 的 tick 延迟 */
    private long calculateDelayToNextSunday() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime target = now.with(DayOfWeek.SUNDAY).withHour(23).withMinute(59).withSecond(0).withNano(0);
        if (!now.isBefore(target)) {
            // 今天已过周日 23:59，目标改为下周日
            target = target.plusWeeks(1);
        }
        long seconds = ChronoUnit.SECONDS.between(now, target);
        return Math.max(1, seconds * 20L); // 20 ticks / second
    }
}
