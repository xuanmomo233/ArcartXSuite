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

/**
 * 邮件玩家命令处理器，提供打开收件箱、写信、一键领取、一键删除与 CDK 兑换功能。
 * <p>
 * 关联模块：mail。实现 Bukkit {@link org.bukkit.command.TabExecutor}，绑定到 {@code /mail} 命令。
 */
public final class MailPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("open", "compose", "claimall", "deleteall", "cdk");

    private final Supplier<MailService> serviceProvider;
    private final MessageProvider messages;

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceProvider 邮件服务提供者（延迟获取，允许服务未就绪时返回 {@code null}）
     * @param messages        消息提供者
     */
    public MailPlayerCommand(Supplier<MailService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /**
     * 处理玩家命令分发。
     *
     * @param sender  命令发送者
     * @param command 命令对象
     * @param label   命令标签
     * @param args    参数数组
     * @return {@code true} 表示已处理
     */
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
                // 配置了 require-permission 时需要 arcartxsuite.mail.cdk 权限
                if (mailService.isCdkPermissionRequired() && !player.hasPermission("arcartxsuite.mail.cdk")) {
                    sender.sendMessage(fullMsg("player.cdk-no-permission"));
                    return true;
                }
                sender.sendMessage(colorize(mailService.redeemCdk(player, args[1])));
            }
            default -> sender.sendMessage(fullMsg("player.usage", label));
        }
        return true;
    }

    /**
     * 提供玩家命令的 Tab 补全。
     *
     * @param sender  命令发送者
     * @param command 命令对象
     * @param alias   命令别名
     * @param args    参数数组
     * @return 补全候选列表
     */
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
