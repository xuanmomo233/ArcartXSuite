package xuanmo.arcartxsuite.tooltip.command;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.tooltip.cache.TooltipDataCache;
import xuanmo.arcartxsuite.tooltip.service.GunListService;

/**
 * Tooltip 模块的管理员命令处理器。
 * <p>
 * 提供 {@code help}、{@code status}、{@code push} 子命令：
 * {@code status} 查看缓存与枪械 UI 模式；{@code push <玩家>} 手动向指定玩家
 * 重推当前枪浏览模式（调试用）。
 */
public final class TooltipAdminCommand {

    private static final List<String> ACTIONS = List.of("help", "status", "push");

    private final TooltipDataCache cache;
    private final MessageProvider messages;
    private final GunListService gunListService;

    public TooltipAdminCommand(@NotNull TooltipDataCache cache, @NotNull MessageProvider messages,
                               @Nullable GunListService gunListService) {
        this.cache = cache;
        this.messages = messages;
        this.gunListService = gunListService;
    }

    public List<String> actions() {
        return ACTIONS;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> showHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "push" -> pushMode(sender, args);
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
        sender.sendMessage(msg("help.push", label));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(msg("admin.status-title"));
        int playerCount = cache.playerCount();
        sender.sendMessage(msg("admin.status-cache-size", playerCount));
        sender.sendMessage(msg("admin.status-packet-id", "AXS_TOOLTIP_DATA"));
        sender.sendMessage(msg("admin.status-ttl", 60));
        if (gunListService != null) {
            sender.sendMessage(msg("admin.status-gun-ui-mode",
                gunListService.mode().name().toLowerCase(Locale.ROOT)));
            sender.sendMessage(msg("admin.status-gun-ui-channel", GunListService.MODE_CHANNEL));
        } else {
            sender.sendMessage(msg("admin.status-gun-ui-unavailable"));
        }
        if (playerCount == 0) {
            sender.sendMessage(msg("admin.status-no-cache"));
        }
    }

    /** {@code axs tooltip push <玩家>} —— 手动重推枪械 UI 模式，验证推送链路。 */
    private void pushMode(CommandSender sender, String[] args) {
        if (gunListService == null) {
            sender.sendMessage(msg("admin.push-unavailable"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(msg("admin.push-usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(msg("admin.push-notfound", args[2]));
            return;
        }
        gunListService.pushMode(target);
        sender.sendMessage(msg("admin.push-ok", target.getName(),
            gunListService.mode().name().toLowerCase(Locale.ROOT)));
    }

    private String msg(String key, Object... args) {
        return messages.get("prefix") + messages.get(key, args);
    }
}
