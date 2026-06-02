package xuanmo.arcartxsuite.mail.command;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.mail.model.MailOperationResult;
import xuanmo.arcartxsuite.mail.service.MailService;

public final class MailPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("open", "compose", "claimall", "deleteall", "cdk");

    private final Supplier<MailService> serviceProvider;
    private final MessageProvider messages;

    public MailPlayerCommand(Supplier<MailService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }
        if (!sender.hasPermission("arcartxsuite.mail.use")) {
            sender.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        MailService mailService = serviceProvider.get();
        if (mailService == null) {
            sender.sendMessage(fullMsg("player.module-down"));
            return true;
        }
        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            sender.sendMessage(colorize(mailService.openInbox(player)));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "compose" -> sender.sendMessage(colorize(mailService.openCompose(player)));
            case "claimall" -> sender.sendMessage(colorize(mailService.claimAll(player)));
            case "deleteall" -> sender.sendMessage(colorize(mailService.deleteAll(player)));
            case "cdk" -> {
                if (args.length < 2) {
                    sender.sendMessage(fullMsg("player.cdk-usage", label));
                    return true;
                }
                sender.sendMessage(colorize(mailService.redeemCdk(player, args[1])));
            }
            default -> sender.sendMessage(fullMsg("player.usage", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            return ROOT_ACTIONS.stream().filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }

    private String colorize(MailOperationResult result) {
        return (messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message();
    }
}
