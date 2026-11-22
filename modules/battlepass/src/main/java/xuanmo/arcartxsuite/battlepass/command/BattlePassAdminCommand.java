package xuanmo.arcartxsuite.battlepass.command;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.battlepass.model.BattlePassPlayerProgress;
import xuanmo.arcartxsuite.battlepass.service.BattlePassService;

/**
 * 战令管理员命令处理器，实现 {@link ModuleCommandHandler}。
 * <p>
 * 处理 {@code /axs battlepass} 管理子命令，包括 help、status、reload、reset、season、unlock，
 * 通过 {@link BattlePassService} 执行管理操作并向发送者反馈本地化消息。
 */
public final class BattlePassAdminCommand implements ModuleCommandHandler {
    private final org.bukkit.plugin.Plugin plugin;

    private final Supplier<BattlePassService> serviceProvider;
    private final java.util.function.BiFunction<String, Object[], String> messageResolver;
    private final String moduleVersion;

    /**
     * 构造战令管理员命令处理器。
     *
     * @param serviceProvider  BattlePassService 提供者（延迟获取，可能为 null）
     * @param messageResolver  消息解析函数（key + 参数 → 本地化文本）
     * @param moduleVersion    模块版本号（用于 help 显示）
     */
    public BattlePassAdminCommand(org.bukkit.plugin.Plugin plugin, Supplier<BattlePassService> serviceProvider,
                                   java.util.function.BiFunction<String, Object[], String> messageResolver,
                                   String moduleVersion) {
        this.plugin = plugin;
        this.serviceProvider = serviceProvider;
        this.messageResolver = messageResolver;
        this.moduleVersion = moduleVersion;
    }

    @Override
    public String commandId() {
        return "battlepass";
    }

    @Override
    public List<String> actions() {
        return List.of("help", "status", "reset", "season", "unlock", "addexp", "setexp", "removeexp", "setlevel", "settle");
    }

    /**
     * {@inheritDoc}
     * <p>
     * 处理战令管理子命令（help/status/reload/reset/season/unlock）。
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        String cmd = "/" + label + " battlepass";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reset" -> handleReset(sender, args);
            case "unlock" -> handleUnlock(sender, args);
            case "addexp" -> handleAddExp(sender, args);
            case "setexp" -> handleSetExp(sender, args);
            case "removeexp" -> handleRemoveExp(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "settle" -> handleSettle(sender, args);
            case "season" -> handleSeason(sender);
            default -> sender.sendMessage(msg("common.unknown", cmd));
        }
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 根据参数位置补全子命令名、玩家名及档位选项。
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(actions(), args[1]);
        }
        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("reset".equals(action) || "unlock".equals(action)
                || "addexp".equals(action) || "setexp".equals(action)
                || "removeexp".equals(action) || "setlevel".equals(action)) {
                return null; // Bukkit 补全在线玩家名
            }
        }
        if (args.length == 4 && "unlock".equals(args[1].toLowerCase(Locale.ROOT))) {
            return filter(List.of("premium", "deluxe"), args[3]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " battlepass";
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", cmd));
        sender.sendMessage(msg("help.season", cmd));
        sender.sendMessage(msg("help.reset", cmd));
        sender.sendMessage(msg("help.unlock", cmd));
        sender.sendMessage(msg("help.addexp", cmd));
        sender.sendMessage(msg("help.setexp", cmd));
        sender.sendMessage(msg("help.removeexp", cmd));
        sender.sendMessage(msg("help.setlevel", cmd));
        sender.sendMessage(msg("help.settle", cmd));
    }

    private void sendStatus(CommandSender sender) {
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("admin.service-unavailable-status"));
            return;
        }
        var config = service.configuration();
        sender.sendMessage(msg("admin.status.title"));
        sender.sendMessage(msg("admin.status.season", config.season().seasonId()));
        try {
            int count = service.repository().countActivePlayers(config.season().seasonId());
            sender.sendMessage(msg("admin.status.players", String.valueOf(count)));
        } catch (Exception e) {
            sender.sendMessage(msg("admin.status.players", "N/A"));
        }
        sender.sendMessage(msg("admin.status.rewards", String.valueOf(config.rewards().size())));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg("admin.reset-usage-full", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.reset.offline", args[2]));
            return;
        }
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        var uuid = target.getUniqueId();
        String name = target.getName();
        service.resetProgressAsync(uuid).whenComplete((ignored,failure)->service.onMain(()->sender.sendMessage(msg(failure==null?"admin.reset-success":"admin.reset-failed",name))));
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(msg("admin.unlock-usage-full", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.reset.offline", args[2]));
            return;
        }
        BattlePassPlayerProgress.PassTier tier;
        try {
            tier = BattlePassPlayerProgress.PassTier.valueOf(args[3].toUpperCase(Locale.ROOT));
            if (tier == BattlePassPlayerProgress.PassTier.FREE) {
                sender.sendMessage(msg("admin.unlock.invalid-tier"));
                return;
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(msg("admin.unlock.invalid-tier"));
            return;
        }
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        String name=target.getName();String display=ChatColor.translateAlternateColorCodes('&',tier.name());
        service.unlockTierAsync(target,tier).whenComplete((changed,failure)->service.onMain(()->{
            if(failure!=null)sender.sendMessage(msg("common.service-unavailable"));
            else sender.sendMessage(msg(changed?"admin.unlock-success":"admin.unlock-already",name,display));
        }));
    }

    private void handleAddExp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(msg("admin.addexp-usage-full", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.reset.offline", args[2]));
            return;
        }
        int xp;
        try {
            xp = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.invalid-amount"));
            return;
        }
        if (xp <= 0) {
            sender.sendMessage(msg("admin.invalid-amount"));
            return;
        }
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        replyAfter(service,sender,service.addXpAsync(target,xp),"admin.addexp-success",target.getName(),String.valueOf(xp));
    }

    private void handleSetExp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(msg("admin.setexp-usage-full", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.reset.offline", args[2]));
            return;
        }
        int xp;
        try {
            xp = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.invalid-amount"));
            return;
        }
        if (xp < 0) {
            sender.sendMessage(msg("admin.invalid-amount"));
            return;
        }
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        replyAfter(service,sender,service.setXpAsync(target,xp),"admin.setexp-success",target.getName(),String.valueOf(xp));
    }

    private void handleRemoveExp(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(msg("admin.removeexp-usage-full", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.reset.offline", args[2]));
            return;
        }
        int xp;
        try {
            xp = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.invalid-amount"));
            return;
        }
        if (xp <= 0) {
            sender.sendMessage(msg("admin.invalid-amount"));
            return;
        }
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        replyAfter(service,sender,service.removeXpAsync(target,xp),"admin.removeexp-success",target.getName(),String.valueOf(xp));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(msg("admin.setlevel-usage-full", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.reset.offline", args[2]));
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(msg("admin.invalid-amount"));
            return;
        }
        if (level < 1) {
            sender.sendMessage(msg("admin.invalid-amount"));
            return;
        }
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        int maxLevel = service.configuration().season().maxLevel();
        if (level > maxLevel) {
            sender.sendMessage(msg("admin.setlevel-exceed-max", String.valueOf(maxLevel)));
            return;
        }
        replyAfter(service,sender,service.setLevelAsync(target,level),"admin.setlevel-success",target.getName(),String.valueOf(level));
    }

    private void replyAfter(BattlePassService service,CommandSender sender,java.util.concurrent.CompletionStage<?> result,String success,Object... args) {
        result.whenComplete((ignored,failure)->service.onMain(()->sender.sendMessage(msg(failure==null?success:"common.service-unavailable",args))));
    }

    private void handleSettle(CommandSender sender, String[] args) {
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        sender.sendMessage(msg("admin.settle-start"));
        // 异步执行结算，避免阻塞主线程
        service.async( () -> {
            String seasonId = service.configuration().season().seasonId();
            String key = switch (service.settlementService().settle(true)) {
                case COMPLETED -> "admin.settle-queued";
                case ALREADY_SETTLED -> "admin.settle-already";
                case RUNNING -> "admin.settle-running";
                case RETRY, NOT_ENDED -> "admin.settle-retry";
            };
            service.onMain( () -> sender.sendMessage(msg(key, seasonId)));
        });
    }

    private void handleSeason(CommandSender sender) {
        BattlePassService service = serviceProvider.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-unavailable"));
            return;
        }
        var season = service.configuration().season();
        sender.sendMessage(msg("admin.season.title"));
        sender.sendMessage(msg("admin.season.id", season.seasonId()));
        sender.sendMessage(msg("admin.season.name", season.displayName()));
        sender.sendMessage(msg("admin.season.max-level", String.valueOf(season.maxLevel())));
        sender.sendMessage(msg("admin.season.dates", season.startDate().toString(), season.endDate().toString()));
        service.async(()->{
            String status=service.settlementService().getSettlementStatus(season.seasonId());
            service.onMain(()->sender.sendMessage(msg("admin.season.settlement",status)));
        });
    }

    private String msg(String key, Object... args) {
        return messageResolver.apply(key, args);
    }

    private static List<String> filter(List<String> candidates, String input) {
        if (input == null || input.isEmpty()) return candidates;
        String normalized = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
            .filter(c -> c.toLowerCase(Locale.ROOT).startsWith(normalized))
            .toList();
    }
}
