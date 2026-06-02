package xuanmo.arcartxsuite.eventpacket.service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 定时命令服务。
 * <p>
 * 根据配置定时执行控制台命令或玩家命令。
 */
public final class ScheduledCommandService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<ScheduledTask> tasks;
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    public ScheduledCommandService(JavaPlugin plugin, Logger logger, List<ScheduledTask> tasks) {
        this.plugin = plugin;
        this.logger = logger;
        this.tasks = tasks;
    }

    public void start() {
        for (ScheduledTask task : tasks) {
            if (!task.enabled() || task.intervalSeconds() <= 0) continue;
            long intervalTicks = task.intervalSeconds() * 20L;
            long delayTicks = task.delaySeconds() * 20L;
            BukkitTask bt = Bukkit.getScheduler().runTaskTimer(plugin, () -> executeTask(task), delayTicks, intervalTicks);
            runningTasks.add(bt);
        }
        if (!runningTasks.isEmpty()) {
            logger.info("定时命令服务已启动 (" + runningTasks.size() + " 个任务)");
        }
    }

    public void shutdown() {
        for (BukkitTask bt : runningTasks) {
            bt.cancel();
        }
        runningTasks.clear();
    }

    private void executeTask(ScheduledTask task) {
        for (String command : task.commands()) {
            String processed = command.trim();
            if (processed.isEmpty()) continue;

            if (task.asConsole()) {
                // 控制台执行，支持 {online} 占位
                if (processed.contains("{player}")) {
                    // 对每个在线玩家执行一次
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        String cmd = processed.replace("{player}", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
                }
            } else {
                // 玩家执行
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (task.permission() != null && !task.permission().isBlank()
                        && !player.hasPermission(task.permission())) {
                        continue;
                    }
                    String cmd = processed.replace("{player}", player.getName());
                    player.performCommand(cmd);
                }
            }
        }

        if (task.broadcastMessage() != null && !task.broadcastMessage().isBlank()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', task.broadcastMessage()));
        }
    }

    /**
     * 定时任务定义。
     */
    public record ScheduledTask(
        String id,
        boolean enabled,
        int intervalSeconds,
        int delaySeconds,
        boolean asConsole,
        String permission,
        List<String> commands,
        String broadcastMessage
    ) {
        public static ScheduledTask fromSection(String id, org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return new ScheduledTask(id, false, 300, 0, true, null, List.of(), null);
            }
            return new ScheduledTask(
                id,
                section.getBoolean("enabled", true),
                section.getInt("interval-seconds", 300),
                section.getInt("delay-seconds", 0),
                section.getBoolean("as-console", true),
                section.getString("permission", null),
                section.getStringList("commands"),
                section.getString("broadcast-message", null)
            );
        }
    }
}
