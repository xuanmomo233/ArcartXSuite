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
    public BattlePassAdminCommand(Supplier<BattlePassService> serviceProvider,
                                   java.util.function.BiFunction<String, Object[], String> messageResolver,
                                   String moduleVersion) {
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
        return List.of("help", "status", "reset", "season", "unlock");
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
            case "season" -> sender.sendMessage(msg("admin.season-unimplemented"));
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
            if ("reset".equals(action) || "unlock".equals(action)) {
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
        sender.sendMessage(msg("admin.status.cross-server", config.crossServer().enabled() ? msg("common.enabled") : msg("common.disabled")));
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
        service.resetPlayerProgress(target.getUniqueId());
        sender.sendMessage(msg("admin.reset-success", target.getName()));
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
        if (service.unlockTier(target, tier)) {
            sender.sendMessage(msg("admin.unlock-success", target.getName(), ChatColor.translateAlternateColorCodes('&', tier.name())));
        } else {
            sender.sendMessage(msg("admin.unlock-already", target.getName(), ChatColor.translateAlternateColorCodes('&', tier.name())));
        }
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
