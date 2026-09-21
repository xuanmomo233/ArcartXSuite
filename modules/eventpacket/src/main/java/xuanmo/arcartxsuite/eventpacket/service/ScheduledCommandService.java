package xuanmo.arcartxsuite.eventpacket.service;
import xuanmo.arcartxsuite.api.scheduler.SchedulerAPI;
import xuanmo.arcartxsuite.api.scheduler.ScheduleSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 定时命令服务。
 * <p>
 * 根据配置定时执行控制台命令或玩家命令。支持两种调度模式：
 * <ul>
 *   <li><b>日历触发</b>（1.6.0+）：通过 {@code schedule} 配置节指定 hourly/daily/weekly/interval</li>
 *   <li><b>固定间隔</b>（向后兼容）：通过 {@code interval-seconds} + {@code delay-seconds} 指定</li>
 * </ul>
 */
public final class ScheduledCommandService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<ScheduledTask> tasks;
    private final SchedulerAPI scheduler;
    private final List<ScheduledTaskHandle> runningTasks = new ArrayList<>();

    public ScheduledCommandService(JavaPlugin plugin, Logger logger, List<ScheduledTask> tasks) {
        this(plugin, logger, tasks, null);
    }

    public ScheduledCommandService(JavaPlugin plugin, Logger logger, List<ScheduledTask> tasks,
                                   SchedulerAPI scheduler) {
        this.plugin = plugin;
        this.logger = logger;
        this.tasks = tasks;
        this.scheduler = scheduler;
    }

    public void start() {
        for (ScheduledTask task : tasks) {
            if (!task.enabled()) continue;

            if (task.schedule() != null && scheduler != null) {
                // 新模式：日历触发
                ScheduledTaskHandle handle = new ScheduledTaskHandle(
                    scheduler.scheduleCalendar(plugin, () -> executeTask(task), task.schedule())
                );
                runningTasks.add(handle);
            } else if (task.intervalSeconds() > 0) {
                // 旧模式：固定间隔（向后兼容）
                long intervalTicks = task.intervalSeconds() * 20L;
                long delayTicks = task.delaySeconds() * 20L;
                if (scheduler != null) {
                    // 优先用 SchedulerAPI
                    ScheduledTaskHandle handle = new ScheduledTaskHandle(
                        scheduler.scheduleAtFixedRate(plugin, () -> executeTask(task), delayTicks, intervalTicks)
                    );
                    runningTasks.add(handle);
                } else {
                    // 降级到 Bukkit Scheduler
                    org.bukkit.scheduler.BukkitTask bt = Bukkit.getScheduler().runTaskTimer(plugin, () -> executeTask(task), delayTicks, intervalTicks);
                    runningTasks.add(new ScheduledTaskHandle(bt));
                }
            }
        }
        if (!runningTasks.isEmpty()) {
            logger.info("定时命令服务已启动 (" + runningTasks.size() + " 个任务)");
        }
    }

    public void shutdown() {
        for (ScheduledTaskHandle handle : runningTasks) {
            handle.cancel();
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
                        try {
                            String cmd = processed.replace("{player}",
                                xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName()));
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        } catch (IllegalArgumentException e) {
                            // 非法玩家名跳过该玩家，不中断整批
                        }
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
                    // {player} 展开为真实玩家名，仍统一过 validatePlayerName 防线（离线服可注册特殊字符名）
                    String safeName;
                    try {
                        safeName = xuanmo.arcartxsuite.api.command.CommandSanitizer.validatePlayerName(player.getName());
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    player.performCommand(processed.replace("{player}", safeName));
                }
            }
        }

        if (task.broadcastMessage() != null && !task.broadcastMessage().isBlank()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', task.broadcastMessage()));
        }
    }

    /** 统一的任务句柄包装（兼容 ScheduledTask 和 BukkitTask）。 */
    private static final class ScheduledTaskHandle {
        private final xuanmo.arcartxsuite.api.scheduler.ScheduledTask scheduledTask;
        private final org.bukkit.scheduler.BukkitTask bukkitTask;

        ScheduledTaskHandle(xuanmo.arcartxsuite.api.scheduler.ScheduledTask task) {
            this.scheduledTask = task;
            this.bukkitTask = null;
        }

        ScheduledTaskHandle(org.bukkit.scheduler.BukkitTask task) {
            this.scheduledTask = null;
            this.bukkitTask = task;
        }

        void cancel() {
            if (scheduledTask != null) scheduledTask.cancel();
            if (bukkitTask != null) bukkitTask.cancel();
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
        String broadcastMessage,
        ScheduleSpec schedule
    ) {
        public static ScheduledTask fromSection(String id, org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return new ScheduledTask(id, false, 300, 0, true, null, List.of(), null, null);
            }
            // 解析 schedule 节（1.6.0+），不存在时返回 null（走旧模式）
            ScheduleSpec schedule = null;
            org.bukkit.configuration.ConfigurationSection scheduleSec = section.getConfigurationSection("schedule");
            if (scheduleSec != null) {
                try {
                    schedule = ScheduleSpec.load(scheduleSec);
                } catch (Exception e) {
                    // schedule 配置非法时降级到旧模式
                }
            }
            return new ScheduledTask(
                id,
                section.getBoolean("enabled", true),
                section.getInt("interval-seconds", 300),
                section.getInt("delay-seconds", 0),
                section.getBoolean("as-console", true),
                section.getString("permission", null),
                section.getStringList("commands"),
                section.getString("broadcast-message", null),
                schedule
            );
        }
    }
}
