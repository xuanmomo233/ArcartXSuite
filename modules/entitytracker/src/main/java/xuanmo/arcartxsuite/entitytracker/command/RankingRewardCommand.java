package xuanmo.arcartxsuite.entitytracker.command;

import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.entitytracker.service.RankingRewardService;
import xuanmo.arcartxsuite.entitytracker.scheduler.RankingRewardScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 排行榜奖励命令处理器
 */
public class RankingRewardCommand {
    private final Logger logger;
    private final RankingRewardService rewardService;
    private final RankingRewardScheduler scheduler;
    private final JavaPlugin plugin;
    private final MessageProvider messages;

    public RankingRewardCommand(RankingRewardService rewardService, 
                               RankingRewardScheduler scheduler, 
                               JavaPlugin plugin,
                               MessageProvider messages) {
        this.logger = plugin.getLogger();
        this.rewardService = rewardService;
        this.scheduler = scheduler;
        this.plugin = plugin;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    public boolean onCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("entitytracker.rewards.admin")) {
            sender.sendMessage(fullMsg("rewards.no-permission"));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "manage" -> openRewardManager(sender);
            case "history" -> openRewardHistory(sender);
            case "distribute" -> handleDistribute(sender, args);
            case "status" -> showSchedulerStatus(sender);
            case "reload" -> reloadScheduler(sender);
            default -> showHelp(sender);
        }

        return true;
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("entitytracker.rewards.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return Arrays.asList("manage", "history", "distribute", "status", "reload")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && "distribute".equalsIgnoreCase(args[0])) {
            return Arrays.asList("weekly", "monthly")
                .stream()
                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        return List.of();
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(fullMsg("rewards.help.title"));
        sender.sendMessage(fullMsg("rewards.help.manage"));
        sender.sendMessage(fullMsg("rewards.help.history"));
        sender.sendMessage(fullMsg("rewards.help.distribute"));
        sender.sendMessage(fullMsg("rewards.help.status"));
        sender.sendMessage(fullMsg("rewards.help.reload"));
    }

    /**
     * 打开奖励管理界面
     */
    private void openRewardManager(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("rewards.only-player"));
            return;
        }

        try {
            // 这里需要调用ArcartX UI系统
            player.sendMessage(fullMsg("rewards.manage.opening"));
            // TODO: 集成ArcartX UI系统
            
        } catch (Exception e) {
            sender.sendMessage(fullMsg("rewards.manage.failed", e.getMessage()));
            logger.severe("打开奖励管理界面失败: " + e.getMessage());
        }
    }

    /**
     * 打开发放历史界面
     */
    private void openRewardHistory(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("rewards.only-player"));
            return;
        }

        try {
            player.sendMessage(fullMsg("rewards.history.opening"));
            // TODO: 集成ArcartX UI系统
            
        } catch (Exception e) {
            sender.sendMessage(fullMsg("rewards.history.failed", e.getMessage()));
            logger.severe("打开发放历史界面失败: " + e.getMessage());
        }
    }

    /**
     * 处理手动发放奖励
     */
    private void handleDistribute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(fullMsg("rewards.distribute.usage"));
            return;
        }

        String rewardType = args[1].toLowerCase();
        if (!rewardType.equals("weekly") && !rewardType.equals("monthly")) {
            sender.sendMessage(fullMsg("rewards.distribute.invalid-type"));
            return;
        }

        String typeDisplay = rewardType.equals("weekly") ? fullMsg("rewards.distribute.weekly") : fullMsg("rewards.distribute.monthly");
        sender.sendMessage(fullMsg("rewards.distribute.triggering", typeDisplay));

        CompletableFuture<Integer> future = rewardType.equals("weekly") 
            ? scheduler.manualTriggerWeeklyRewards()
            : scheduler.manualTriggerMonthlyRewards();

        future.thenAccept(totalRewards -> {
            sender.sendMessage(fullMsg("rewards.distribute.success", totalRewards));
            logger.info("手动发放" + rewardType + "奖励完成: 发放数量=" + totalRewards);
        }).exceptionally(throwable -> {
            sender.sendMessage(fullMsg("rewards.distribute.failed", throwable.getMessage()));
            logger.severe("手动发放" + rewardType + "奖励失败: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * 显示调度器状态
     */
    private void showSchedulerStatus(CommandSender sender) {
        sender.sendMessage(fullMsg("rewards.status.title"));
        
        String nextExecution = scheduler.getNextExecutionInfo();
        sender.sendMessage(fullMsg("rewards.status.next-execution"));
        sender.sendMessage(nextExecution);
        
        sender.sendMessage(fullMsg("rewards.status.task-status"));
        sender.sendMessage(fullMsg("rewards.status.weekly-task", scheduler.isWeeklyTaskRunning() ? fullMsg("rewards.status.running") : fullMsg("rewards.status.stopped")));
        sender.sendMessage(fullMsg("rewards.status.monthly-task", scheduler.isMonthlyTaskRunning() ? fullMsg("rewards.status.running") : fullMsg("rewards.status.stopped")));
    }

    /**
     * 重新加载调度器配置
     */
    private void reloadScheduler(CommandSender sender) {
        sender.sendMessage(fullMsg("rewards.reload.reloading"));
        
        try {
            // 重新加载配置
            scheduler.updateSchedule(plugin.getConfig());
            
            sender.sendMessage(fullMsg("rewards.reload.success"));
            logger.info("排行榜奖励调度器配置已重新加载");
            
        } catch (Exception e) {
            sender.sendMessage(fullMsg("rewards.reload.failed", e.getMessage()));
            logger.severe("重新加载调度器配置失败: " + e.getMessage());
        }
    }
}
