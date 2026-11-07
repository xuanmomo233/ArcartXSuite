package xuanmo.arcartxsuite.qqbot.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
    private volatile boolean running;

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

    public synchronized void start() {
        if (running) return;
        running = true;
        long delayTicks = calculateDelayToNextSunday();
        // 首次调度：等到下周日 23:59
        task = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this::runWeeklyRank, delayTicks);
        logger.info("周结算排行榜已调度，下次触发约 " + (delayTicks / 20 / 60) + " 分钟后");
    }

    public synchronized void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void runWeeklyRank() {
        if (!running) return;
        try {
            List<PointAccount> top = repository.getPointsLeaderboard(10);
            StringBuilder sb = new StringBuilder("═══ 每周积分余额榜 Top10 ═══");
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
            if (running && broadcast != null) {
                broadcast.accept(message);
            }
        } catch (Exception e) {
            logger.warning("周结算排行榜异常: " + e.getMessage());
        }
        // 重新调度下周
        scheduleNext();
    }

    private synchronized void scheduleNext() {
        if (running) task = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
            this::runWeeklyRank, calculateDelayToNextSunday());
    }

    /** 计算到下周日 23:59 的 tick 延迟 */
    private long calculateDelayToNextSunday() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime target = now.with(DayOfWeek.SUNDAY).withHour(23).withMinute(59).withSecond(0).withNano(0);
        if (!now.isBefore(target)) {
            // 今天已过周日 23:59，目标改为下周日
            target = target.plusWeeks(1);
        }
        long millis = java.time.Duration.between(now, target).toMillis();
        return Math.max(1, (millis + 49) / 50); // Round up: never fire before the target wall time.
    }
}
