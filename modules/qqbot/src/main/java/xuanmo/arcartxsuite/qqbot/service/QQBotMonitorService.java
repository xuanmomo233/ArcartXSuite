package xuanmo.arcartxsuite.qqbot.service;

import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xuanmo.arcartxsuite.qqbot.config.QQBotConfiguration;
import xuanmo.arcartxsuite.qqbot.config.QQBotGroupConfig;
import xuanmo.arcartxsuite.qqbot.config.QQBotMonitorConfig;

/**
 * 服务器监控告警服务。
 * <p>
 * 周期性检测 TPS 与内存占用，超过阈值时推送告警到指定 QQ 群（带冷却防刷屏）。
 */
public final class QQBotMonitorService {

    /** 告警发送回调 */
    @FunctionalInterface
    public interface AlarmSender {
        void sendToGroup(long groupId, String message);
    }

    private final JavaPlugin plugin;
    private final QQBotConfiguration config;
    private final AlarmSender sender;
    private final Logger logger;

    private BukkitTask task;
    private long lastTpsAlarm = 0L;
    private long lastMemoryAlarm = 0L;

    public QQBotMonitorService(
        JavaPlugin plugin,
        QQBotConfiguration config,
        AlarmSender sender,
        Logger logger
    ) {
        this.plugin = plugin;
        this.config = config;
        this.sender = sender;
        this.logger = logger;
    }

    public void start() {
        QQBotMonitorConfig mc = config.monitor();
        if (!mc.enabled()) return;
        long intervalTicks = Math.max(20L, mc.checkIntervalSeconds() * 20L);
        // TPS 读取在主线程更安全；内存读取无线程要求
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, intervalTicks, intervalTicks);
        logger.info("[QQBot] 服务器监控告警已启用 | TPS阈值=" + mc.tpsThreshold()
            + " | 内存阈值=" + mc.memoryThresholdPercent() + "%"
            + " | 检测间隔=" + mc.checkIntervalSeconds() + "s");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void check() {
        QQBotMonitorConfig mc = config.monitor();
        long now = System.currentTimeMillis();
        long cooldownMs = mc.cooldownSeconds() * 1000L;

        // ─── TPS 检测 ───
        double[] tps = getServerTps();
        double tps1m = tps.length > 0 ? tps[0] : 20.0;
        if (tps1m < mc.tpsThreshold() && now - lastTpsAlarm >= cooldownMs) {
            lastTpsAlarm = now;
            String msg = mc.tpsAlarmFormat()
                .replace("{tps}", String.format("%.2f", tps1m))
                .replace("{threshold}", String.format("%.1f", mc.tpsThreshold()));
            pushAlarm(mc, msg);
        }

        // ─── 内存检测 ───
        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory() / 1024 / 1024;
        long usedMem = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        int percent = maxMem > 0 ? (int) (usedMem * 100 / maxMem) : 0;
        if (percent >= mc.memoryThresholdPercent() && now - lastMemoryAlarm >= cooldownMs) {
            lastMemoryAlarm = now;
            String msg = mc.memoryAlarmFormat()
                .replace("{used}", String.valueOf(usedMem))
                .replace("{max}", String.valueOf(maxMem))
                .replace("{percent}", String.valueOf(percent));
            pushAlarm(mc, msg);
        }
    }

    private void pushAlarm(QQBotMonitorConfig mc, String message) {
        if (mc.pushToAllGroups()) {
            for (QQBotGroupConfig group : config.groups()) {
                sender.sendToGroup(group.groupId(), message);
            }
        } else {
            for (long groupId : mc.alarmGroups()) {
                sender.sendToGroup(groupId, message);
            }
        }
    }

    private static double[] getServerTps() {
        try {
            var method = Bukkit.class.getMethod("getTPS");
            return (double[]) method.invoke(null);
        } catch (Exception ignored) {}
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            java.lang.reflect.Field tpsField = server.getClass().getField("recentTps");
            return (double[]) tpsField.get(server);
        } catch (Exception ignored) {}
        return new double[]{20.0, 20.0, 20.0};
    }
}
