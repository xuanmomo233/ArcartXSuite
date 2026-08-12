package xuanmo.arcartxsuite.tooltip.command;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;

/**
 * Tooltip 模块的管理员命令处理器。
 * <p>
 * 提供 {@code help} 和 {@code status} 子命令，支持通过 {@code /axs tooltip status}
 * 查看模块缓存状态。
 */
public final class TooltipAdminCommand {

    private static final List<String> ACTIONS = List.of("help", "status");

    private final TooltipDataCache cache;
    private final MessageProvider messages;

    public TooltipAdminCommand(@NotNull TooltipDataCache cache, @NotNull MessageProvider messages) {
        this.cache = cache;
        this.messages = messages;
    }

    public List<String> actions() {
        return ACTIONS;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> showHelp(sender, label);
            case "status" -> sendStatus(sender);
            default -> showHelp(sender, label);
        }
        return true;
    }

    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return ACTIONS.stream()
                .filter(a -> a.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .toList();
        }
        return null;
    }

    private void showHelp(CommandSender sender, String label) {
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", label));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(msg("admin.status-title"));
        int playerCount = cache.playerCount();
        sender.sendMessage(msg("admin.status-cache-size", playerCount));
        sender.sendMessage(msg("admin.status-packet-id", "AXS_TOOLTIP_DATA"));
        sender.sendMessage(msg("admin.status-ttl", 60));
        if (playerCount == 0) {
            sender.sendMessage(msg("admin.status-no-cache"));
        }
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }
}
