package xuanmo.arcartxsuite.entitytracker.command;

import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.entitytracker.service.RankingRewardService;
import xuanmo.arcartxsuite.entitytracker.scheduler.RankingRewardScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
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
    private final File moduleConfigFile;
    private final MessageProvider messages;
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final java.util.function.BiPredicate<Player, Boolean> openRewardUi;

    public RankingRewardCommand(RankingRewardService rewardService,
                               RankingRewardScheduler scheduler,
                               File moduleConfigFile,
                               Logger logger,
                               MessageProvider messages,
                               org.bukkit.plugin.java.JavaPlugin plugin,
                               java.util.function.BiPredicate<Player, Boolean> openRewardUi) {
        this.rewardService = rewardService;
        this.scheduler = scheduler;
        this.moduleConfigFile = moduleConfigFile;
        this.logger = logger;
        this.messages = messages;
        this.plugin = plugin;
        this.openRewardUi = openRewardUi;
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
            showHelp(sender, label);
            return true;
        }

        if ("help".equalsIgnoreCase(args[0])) {
            showHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "manage" -> openRewardManager(sender);
            case "history" -> openRewardHistory(sender);
            case "distribute" -> handleDistribute(sender, args);
            case "status" -> showSchedulerStatus(sender);
            case "reload" -> reloadScheduler(sender);
            case "retry", "inspect", "resolve" -> handleActionReview(sender, args, subCommand);
            default -> showHelp(sender, label);
        }

        return true;
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("entitytracker.rewards.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return Arrays.asList("help", "manage", "history", "distribute", "status", "reload", "retry", "inspect", "resolve")
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

    private void handleActionReview(CommandSender sender, String[] args, String action) {
        boolean resolve = "resolve".equals(action);
        if (resolve && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage("§c请通过服务器控制台登记核对结果。");
            return;
        }
        try {
            if (args.length != (resolve ? 5 : 2)) throw new IllegalArgumentException();
            int id = Integer.parseInt(args[1]);
            CompletableFuture<String> response;
            if (resolve) {
                int step = Integer.parseInt(args[2]);
                java.util.UUID attempt = java.util.UUID.fromString(args[3]);
                boolean delivered = "delivered".equalsIgnoreCase(args[4]);
                if (!delivered && !"not-delivered".equalsIgnoreCase(args[4])) throw new IllegalArgumentException();
                response = rewardService.resolveReviewedAction(id, step, attempt, delivered);
            } else if ("inspect".equals(action)) {
                response = rewardService.inspectReward(id);
            } else {
                response = rewardService.retryDistribution(id);
            }
            response.thenAccept(message -> {
                if (plugin.isEnabled()) rewardService.onMainIfActive(() -> sender.sendMessage(message));
            });
        } catch (IllegalArgumentException failure) {
            sender.sendMessage(resolve
                ? "§e用法: axs entitytracker rewards resolve <记录ID> <步骤编号> <尝试UUID> <delivered|not-delivered>"
                : "§e用法: /axs entitytracker rewards " + action + " <记录ID>");
        }
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(CommandSender sender, String label) {
        String cmd = "/" + label;
        sender.sendMessage(fullMsg("ranking.help.title"));
        sender.sendMessage(fullMsg("ranking.help.manage", cmd));
        sender.sendMessage(fullMsg("ranking.help.history", cmd));
        sender.sendMessage(fullMsg("ranking.help.distribute", cmd));
        sender.sendMessage(fullMsg("ranking.help.status", cmd));
        sender.sendMessage(fullMsg("ranking.help.reload", cmd));
        sender.sendMessage("§e/axs entitytracker rewards retry <记录ID> §7继续发放待发步骤");
        sender.sendMessage("§e/axs entitytracker rewards inspect <记录ID> §7查看待核对步骤与尝试编号");
        if (sender instanceof org.bukkit.command.ConsoleCommandSender) {
            sender.sendMessage("§eaxs entitytracker rewards resolve <记录ID> <步骤编号> <尝试UUID> <delivered|not-delivered>");
            sender.sendMessage("§7确认该次尝试已经停止，并核对到账结果后再登记。");
        }
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
            if (!openRewardUi.test(player, false)) {
                player.sendMessage(fullMsg("rewards.manage.failed", "界面不可用"));
            }
            
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
            if (!openRewardUi.test(player, true)) {
                player.sendMessage(fullMsg("rewards.history.failed", "界面不可用"));
            }
            
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
            // 重新读取模块配置文件并刷新调度
            if (!moduleConfigFile.exists()) {
                throw new IllegalStateException("模块配置文件缺失: " + moduleConfigFile.getPath());
            }
            scheduler.updateSchedule(YamlConfiguration.loadConfiguration(moduleConfigFile));
            
            sender.sendMessage(fullMsg("rewards.reload.success"));
            logger.info("排行榜奖励调度器配置已重新加载");
            
        } catch (Exception e) {
            sender.sendMessage(fullMsg("rewards.reload.failed", e.getMessage()));
            logger.severe("重新加载调度器配置失败: " + e.getMessage());
        }
    }
}



