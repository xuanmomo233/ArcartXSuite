package xuanmo.arcartxsuite.qqbot.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotGroupConfig;
import xuanmo.arcartxsuite.qqbot.config.QQBotScheduledMessageConfig;

/**
 * 定时消息服务。
 * <p>
 * 支持 interval（固定间隔）与 daily（每日定时 HH:mm）两种模式，
 * 周期性向目标群推送配置的消息，支持 {online}/{max}/{tps} 占位符。
 */
public final class QQBotScheduledMessageService {

    @FunctionalInterface
    public interface MessageSender {
        void sendToGroup(long groupId, String message);
    }

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final MessageSender sender;
    private final Logger logger;

    private final Map<String, Long> lastIntervalSent = new HashMap<>();
    private final Map<String, String> lastDailySentDate = new HashMap<>();

    private BukkitTask task;

    public QQBotScheduledMessageService(
        JavaPlugin plugin,
        QQBotConfiguration config,
        MessageSender sender,
        Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.sender = sender;
        this.logger = logger;
    }

    public void start() {
        if (config.scheduledMessages().isEmpty()) return;
        // 初始化 interval 起点为当前时间，避免启动瞬间全部触发
        long now = System.currentTimeMillis();
        for (QQBotScheduledMessageConfig sm : config.scheduledMessages()) {
            if (sm.isInterval()) lastIntervalSent.put(sm.id(), now);
        }
        // 每 20 tick（约 1 秒）检查一次
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        logger.info("[QQBot] 定时消息服务已启用 | 条目数=" + config.scheduledMessages().size());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastIntervalSent.clear();
        lastDailySentDate.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        String currentHm = LocalTime.now().format(HHMM);
        String today = LocalDate.now().toString();

        for (QQBotScheduledMessageConfig sm : config.scheduledMessages()) {
            if (sm.message() == null || sm.message().isBlank()) continue;

            if (sm.isInterval()) {
                if (sm.intervalSeconds() <= 0) continue;
                long last = lastIntervalSent.getOrDefault(sm.id(), 0L);
                if (now - last >= sm.intervalSeconds() * 1000L) {
                    lastIntervalSent.put(sm.id(), now);
                    dispatch(sm);
                }
            } else if (sm.isDaily()) {
                if (currentHm.equals(sm.dailyTime())
                    && !today.equals(lastDailySentDate.get(sm.id()))) {
                    lastDailySentDate.put(sm.id(), today);
                    dispatch(sm);
                }
            }
        }
    }

    private void dispatch(QQBotScheduledMessageConfig sm) {
        String message = applyPlaceholders(sm.message());
        if (sm.pushToAllGroups()) {
            for (QQBotGroupConfig group : config.groups()) {
                sender.sendToGroup(group.groupId(), message);
            }
        } else {
            for (long groupId : sm.targetGroups()) {
                sender.sendToGroup(groupId, message);
            }
        }
    }

    private String applyPlaceholders(String message) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        double[] tps = getServerTps();
        return message
            .replace("{online}", String.valueOf(online))
            .replace("{max}", String.valueOf(max))
            .replace("{tps}", String.format("%.1f", tps.length > 0 ? tps[0] : 20.0));
    }

    private static double[] getServerTps() {
        try {
            var method = Bukkit.class.getMethod("getTPS");
            return (double[]) method.invoke(null);
        } catch (Exception ignored) {}
        return new double[]{20.0, 20.0, 20.0};
    }
}
