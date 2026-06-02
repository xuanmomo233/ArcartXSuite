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
import xuanmo.arcartxsuite.chat.service.ChatService;

public final class ChatPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> CHAT_ACTIONS = List.of("channel", "toggle", "ignore", "unignore", "socialspy");
    private static final List<String> TOGGLE_ACTIONS = List.of("private", "mentions");
    private final Supplier<ChatService> serviceProvider;
    private final CommandMode mode;
    private final MessageProvider messages;

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
        if (args.length == 0) {
            ChatPlayerState state = service.getCachedState(player.getUniqueId());
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
                result = service.ignore(player, args[1]);
            }
            case "unignore" -> {
                if (args.length < 2) {
                    player.sendMessage(fullMsg("player.unignore-usage", label));
                    return true;
                }
                result = service.unignore(player, args[1]);
            }
            case "socialspy" -> {
                Boolean enabled = args.length >= 2 ? parseToggle(args[1]) : null;
                if (args.length >= 2 && enabled == null) {
                    player.sendMessage(fullMsg("player.toggle-invalid"));
                    return true;
                }
                result = service.setSocialSpy(player, enabled);
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
        sendResult(player, service.sendPrivateMessage(player, args[0], joinTail(args, 1)));
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
        sendResult(player, service.reply(player, joinTail(args, 0)));
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

    public enum CommandMode {
        CHAT,
        MESSAGE,
        REPLY
    }
}
