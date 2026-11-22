package xuanmo.arcartxsuite.chat.command;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;
import xuanmo.arcartxsuite.chat.model.ChatPlayerState;
import xuanmo.arcartxsuite.chat.model.RedPacketType;
import xuanmo.arcartxsuite.chat.service.ChatService;
import xuanmo.arcartxsuite.chat.service.RedPacketService;

/**
 * 聊天模块玩家命令处理器，实现 {@link org.bukkit.command.TabExecutor}。
 * <p>
 * 通过 {@link CommandMode} 区分三种命令模式：CHAT（频道切换 / 开关 / 屏蔽）、
 * MESSAGE（私聊）、REPLY（回复上次私聊），复用同一套命令逻辑。
 */
public final class ChatPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> CHAT_ACTIONS = List.of("help", "channel", "toggle", "ignore", "unignore",
        "socialspy", "redpacket", "grab", "party", "share", "meeting", "ready",
        "subscribe", "unsubscribe", "quiet", "mentions", "history", "report");
    private static final List<String> TOGGLE_ACTIONS = List.of("private", "mentions");
    private static final List<String> REDPACKET_TYPES = List.of("normal", "lucky");
    private final Supplier<ChatService> serviceProvider;
    private final CommandMode mode;
    private final MessageProvider messages;

    /**
     * @param serviceProvider 聊天服务延迟供应者
     * @param mode             命令模式（CHAT / MESSAGE / REPLY）
     * @param messages         消息提供者，用于本地化提示文本
     */
    public ChatPlayerCommand(Supplier<ChatService> serviceProvider, CommandMode mode, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.mode = mode;
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
        if (!xuanmo.arcartxsuite.api.auth.AuthAccess.admitted(player.getUniqueId())) {
            player.sendMessage(fullMsg("result.login-required"));
            return true;
        }
        ChatService service = serviceProvider.get();
        if (service == null) {
            player.sendMessage(fullMsg("common.module-down"));
            return true;
        }

        return switch (mode) {
            case CHAT -> handleChatCommand(player, label, args, service);
            case MESSAGE -> handleMessageCommand(player, label, args, service);
            case REPLY -> handleReplyCommand(player, label, args, service);
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        ChatService service = serviceProvider.get();
        if (!(sender instanceof Player) || service == null) {
            return List.of();
        }
        if (mode == CommandMode.CHAT) {
            if (args.length == 1) {
                return filter(CHAT_ACTIONS, args[0]);
            }
            if (args.length == 2 && "channel".equalsIgnoreCase(args[0])) {
                return filter(service.channelIds(), args[1]);
            }
            if (args.length == 2 && "toggle".equalsIgnoreCase(args[0])) {
                return filter(TOGGLE_ACTIONS, args[1]);
            }
            if (args.length == 2 && "redpacket".equalsIgnoreCase(args[0])) {
                return filter(REDPACKET_TYPES, args[1]);
            }
            if (args.length == 3 && ("toggle".equalsIgnoreCase(args[0]) || "socialspy".equalsIgnoreCase(args[0]))) {
                return filter(List.of("on", "off"), args[2]);
            }
            if (args.length == 2 && ("ignore".equalsIgnoreCase(args[0]) || "unignore".equalsIgnoreCase(args[0]))) {
                return filter(new ArrayList<>(service.onlinePlayerNames()), args[1]);
            }
            return List.of();
        }
        if (mode == CommandMode.MESSAGE && args.length == 1) {
            return filter(new ArrayList<>(service.onlinePlayerNames()), args[0]);
        }
        return List.of();
    }

    private boolean handleChatCommand(Player player, String label, String[] args, ChatService service) {
        if (!player.hasPermission("arcartxsuite.chat.use")) {
            player.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        if (args.length > 0 && "help".equalsIgnoreCase(args[0])) {
            showHelp(player);
            return true;
        }

        if (args.length == 0) {
            ChatPlayerState state = service.readState(player.getUniqueId());
            if (state == null) {
                player.sendMessage(fullMsg("result.storage-unavailable"));
                return true;
            }
            player.sendMessage(fullMsg("player.current-channel", service.channelDisplayName(state.currentChannelId())));
            player.sendMessage(
                fullMsg("player.toggle-status",
                    state.acceptsPrivateMessages(), state.acceptsMentions(), state.socialSpyEnabled())
            );
            player.sendMessage(
                fullMsg("player.reply-status",
                    service.replyTargetName(player.getUniqueId()), state.ignoredPlayers().size())
            );
            return true;
        }

        ChatOperationResult result;
        switch (args[0].toLowerCase()) {
            case "party" -> {
                service.sendPartyMessage(player, joinTail(args, 1), reply -> sendResult(player, reply));
                return true;
            }
            case "meeting" -> {
                service.links().location(player, true, reply -> sendResult(player, reply));
                return true;
            }
            case "ready" -> {
                service.links().ready(player, reply -> sendResult(player, reply));
                return true;
            }
            case "history" -> {
                service.showHistory(player, args.length >= 2 ? args[1] : "");
                return true;
            }
            case "report" -> {
                if (args.length >= 3) service.reportMessage(player, args[1], joinTail(args, 2));
                else player.sendMessage(fullMsg("moderation.report-usage"));
                return true;
            }
            case "mentions" -> {
                service.readMentions(player);
                return true;
            }
            case "subscribe", "unsubscribe", "quiet" -> {
                boolean quiet = "quiet".equalsIgnoreCase(args[0]);
                Boolean value = quiet && args.length == 3 ? parseToggle(args[2])
                    : quiet ? null : "subscribe".equalsIgnoreCase(args[0]);
                if (args.length < 2 || value == null) {
                    player.sendMessage(fullMsg("links.preferences-usage"));
                    return true;
                }
                result = service.channelPreference(player, args[1], quiet, value);
            }
            case "open" -> {
                if (args.length != 2) return true;
                service.links().open(player, args[1], reply -> {
                    if (!reply.success()) sendResult(player, reply);
                });
                return true;
            }
            case "share" -> {
                if (!share(player, service, args)) player.sendMessage(fullMsg("links.usage"));
                return true;
            }
            case "channel" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.channel-usage", label));
                    return true;
                }
                result = service.changeChannel(player, args[1]);
            }
            case "toggle" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.toggle-usage", label));
                    return true;
                }
                Boolean enabled = args.length >= 3 ? parseToggle(args[2]) : null;
                if (args.length >= 3 && enabled == null) {
                    player.sendMessage(fullMsg("player.toggle-invalid"));
                    return true;
                }
                if ("private".equalsIgnoreCase(args[1])) {
                    result = service.setAcceptsPrivate(player, enabled);
                } else if ("mentions".equalsIgnoreCase(args[1])) {
                    result = service.setAcceptsMentions(player, enabled);
                } else {
                    player.sendMessage(fullMsg("player.toggle-usage", label));
                    return true;
                }
            }
            case "ignore" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.ignore-usage", label));
                    return true;
                }
                service.ignore(player, args[1], outcome -> sendResult(player, outcome));
                return true;
            }
            case "unignore" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.unignore-usage", label));
                    return true;
                }
                service.unignore(player, args[1], outcome -> sendResult(player, outcome));
                return true;
            }
            case "socialspy" -> {
                Boolean enabled = args.length >= 2 ? parseToggle(args[1]) : null;
                if (args.length >= 2 && enabled == null) {
                    player.sendMessage(fullMsg("player.toggle-invalid"));
                    return true;
                }
                result = service.setSocialSpy(player, enabled);
            }
            case "redpacket" -> {
                handleRedPacketSend(player, label, args, service);
                return true;
            }
            case "grab" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.grab-usage", label));
                    return true;
                }
                RedPacketService redPacketService = service.redPacketService();
                if (redPacketService == null) {
                    player.sendMessage(fullMsg("redpacket.disabled"));
                    return true;
                }
                redPacketService.claimPacket(player, args[1]);
                return true;
            }
            default -> {
                player.sendMessage(fullMsg("player.chat-usage", label));
                return true;
            }
        }
        sendResult(player, result);
        return true;
    }

    private boolean handleMessageCommand(Player player, String label, String[] args, ChatService service) {
        if (!player.hasPermission("arcartxsuite.chat.msg")) {
            player.sendMessage(fullMsg("player.no-permission-msg"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(fullMsg("player.msg-usage", label));
            return true;
        }
        service.sendPrivateMessage(player, args[0], joinTail(args, 1), result -> sendResult(player, result));
        return true;
    }

    private boolean share(Player player, ChatService service, String[] args) {
        if (args.length == 2 && "location".equalsIgnoreCase(args[1])) {
            service.links().location(player, false, reply -> sendResult(player, reply));
            return true;
        }
        if (args.length != 3) return false;
        String type = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (type) {
            case "quest" -> service.links().quest(player, args[2], reply -> sendResult(player, reply));
            case "auction", "listing", "shop", "store" -> service.links().market(player, type, args[2], reply -> sendResult(player, reply));
            default -> { return false; }
        }
        return true;
    }

    private boolean handleReplyCommand(Player player, String label, String[] args, ChatService service) {
        if (!player.hasPermission("arcartxsuite.chat.msg")) {
            player.sendMessage(fullMsg("player.no-permission-msg"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(fullMsg("player.reply-usage", label));
            return true;
        }
        service.reply(player, joinTail(args, 0), result -> sendResult(player, result));
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(fullMsg("player.help.title"));
        player.sendMessage(fullMsg("player.help.channel"));
        player.sendMessage(fullMsg("player.help.toggle"));
        player.sendMessage(fullMsg("player.help.ignore"));
        player.sendMessage(fullMsg("player.help.unignore"));
        player.sendMessage(fullMsg("player.help.socialspy"));
        player.sendMessage(fullMsg("player.help.redpacket"));
        player.sendMessage(fullMsg("player.help.grab"));
        player.sendMessage(fullMsg("player.help.links"));
        player.sendMessage(fullMsg("player.help.preferences"));
        player.sendMessage(fullMsg("player.help.reports"));
    }

    /** /chat redpacket normal|lucky <总金额> <个数> [祝福语]。 */
    private boolean handleRedPacketSend(Player player, String label, String[] args, ChatService service) {
        RedPacketService redPacketService = service.redPacketService();
        if (redPacketService == null) {
            player.sendMessage(fullMsg("redpacket.disabled"));
            return true;
        }
        if (args.length < 4) {
            player.sendMessage(fullMsg("player.redpacket-usage", label));
            return true;
        }
        RedPacketType type = RedPacketType.parse(args[1]);
        if (type != RedPacketType.NORMAL && type != RedPacketType.LUCKY) {
            player.sendMessage(fullMsg("player.redpacket-usage", label));
            return true;
        }
        java.math.BigDecimal total;
        try {
            total = new java.math.BigDecimal(args[2]);
        } catch (NumberFormatException exception) {
            player.sendMessage(fullMsg("redpacket.invalid-amount"));
            return true;
        }
        int count;
        try {
            count = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            player.sendMessage(fullMsg("redpacket.invalid-count", 50));
            return true;
        }
        String wish = args.length >= 5 ? joinTail(args, 4) : "";
        redPacketService.sendPlayerPacket(player, type, total, count, wish, reply -> sendResult(player, reply));
        return true;
    }

    private void sendResult(Player player, ChatOperationResult result) {
        if (result.cardNotified()) {
            return;
        }
        player.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static Boolean parseToggle(String value) {
        if ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String joinTail(String[] args, int fromIndex) {
        StringBuilder builder = new StringBuilder();
        for (int index = fromIndex; index < args.length; index++) {
            if (index > fromIndex) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private static List<String> filter(List<String> candidates, String input) {
        String normalized = input.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase().startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 命令模式枚举，区分聊天管理、私聊发送与回复三种命令用途。
     */
    public enum CommandMode {
        CHAT,
        MESSAGE,
        REPLY
    }
}
