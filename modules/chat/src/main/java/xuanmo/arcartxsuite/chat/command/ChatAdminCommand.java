package xuanmo.arcartxsuite.chat.command;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.chat.model.ChatOperationResult;
import xuanmo.arcartxsuite.chat.model.RedPacketType;
import xuanmo.arcartxsuite.chat.service.ChatService;
import xuanmo.arcartxsuite.chat.service.RedPacketService;

/**
 * 聊天模块管理员命令处理器。
 * <p>
 * 支持 help / status / reload / mute / unmute / spy 六个子动作，
 * 通过 {@link Supplier} 延迟获取 {@link ChatService}，避免模块加载顺序问题。
 */
public final class ChatAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "mute", "unmute", "spy", "redpacket");
    private static final List<String> REDPACKET_KEYWORDS = List.of("normal", "lucky", "list", "reload");
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$", Pattern.CASE_INSENSITIVE);

    private final Supplier<ChatService> serviceProvider;
    private final MessageProvider messages;

    /**
     * @param serviceProvider 聊天服务延迟供应者
     * @param messages         消息提供者，用于本地化提示文本
     */
    public ChatAdminCommand(Supplier<ChatService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override public String commandId() { return "chat"; }
    @Override public List<String> actions() { return ACTIONS; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "mute" -> handleMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "spy" -> handleSpy(sender, args);
            case "redpacket" -> handleRedPacket(sender, label, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3 && List.of("mute", "unmute", "spy").contains(args[1].toLowerCase(Locale.ROOT))) {
            return null; // player names
        }
        if (args.length == 3 && "redpacket".equalsIgnoreCase(args[1])) {
            ChatService svc = serviceProvider.get();
            List<String> candidates = new ArrayList<>(REDPACKET_KEYWORDS);
            if (svc != null && svc.redPacketService() != null) {
                candidates.addAll(svc.redPacketService().presetIds());
            }
            return filter(candidates, args[2]);
        }
        if (args.length == 4) {
            if ("mute".equalsIgnoreCase(args[1])) return filter(List.of("permanent", "7d", "12h", "30m"), args[3]);
            if ("spy".equalsIgnoreCase(args[1])) return filter(List.of("on", "off"), args[3]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " chat";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.mute", cmd));
        sender.sendMessage(fullMsg("help.unmute", cmd));
        sender.sendMessage(fullMsg("help.spy", cmd));
        sender.sendMessage(fullMsg("help.redpacket", cmd));
    }

    private void sendStatus(CommandSender sender) {
        ChatService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.channels", svc.channelCount()));
        sender.sendMessage(fullMsg("status.cache-state", svc.cachedStateCount()));
        sender.sendMessage(fullMsg("status.cache-mute", svc.cachedMuteCount()));
        sender.sendMessage(fullMsg("status.cross-server",
            svc.crossServerActive() ? ChatColor.GREEN + "已启用" : ChatColor.YELLOW + "未启用"));
    }

    private void handleMute(CommandSender sender, String[] args) {
        ChatService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 4) { sender.sendMessage(fullMsg("mute.usage")); return; }
        Instant expiresAt = parseDuration(args[3]);
        String reason = args.length >= 5 ? joinArgs(args, 4) : "";
        ChatOperationResult result = svc.mutePlayer(args[2], expiresAt, reason, sender.getName());
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        ChatService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("unmute.usage")); return; }
        ChatOperationResult result = svc.unmutePlayer(args[2]);
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private void handleSpy(CommandSender sender, String[] args) {
        ChatService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 4) { sender.sendMessage(fullMsg("spy.usage")); return; }
        boolean enabled = "on".equalsIgnoreCase(args[3]);
        ChatOperationResult result = svc.setSocialSpy(args[2], enabled, sender.getName());
        sender.sendMessage((messages != null ? messages.get("prefix") : "") + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    /**
     * /axs chat redpacket 子命令：
     * <ul>
     *   <li>{@code redpacket normal|lucky <金额> <个数> [祝福语]} — 免扣款货币红包</li>
     *   <li>{@code redpacket <presetId> [个数]} — 预设奖励池红包</li>
     *   <li>{@code redpacket list} — 列出可用预设</li>
     *   <li>{@code redpacket reload} — 重载预设</li>
     * </ul>
     */
    private void handleRedPacket(CommandSender sender, String label, String[] args) {
        ChatService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        RedPacketService rp = svc.redPacketService();
        if (rp == null) { sender.sendMessage(fullMsg("redpacket.disabled")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("redpacket.admin-usage", "/" + label + " chat")); return; }
        String sub = args[2].toLowerCase(Locale.ROOT);
        ChatOperationResult result;
        switch (sub) {
            case "list" -> {
                List<String> ids = rp.presetIds();
                sender.sendMessage(fullMsg("redpacket.preset-list", ids.isEmpty() ? "-" : String.join(", ", ids)));
                return;
            }
            case "reload" -> {
                int loaded = rp.reloadPresets();
                sender.sendMessage(fullMsg("redpacket.preset-reloaded", loaded));
                return;
            }
            case "normal", "lucky" -> {
                if (args.length < 5) { sender.sendMessage(fullMsg("redpacket.admin-usage", "/" + label + " chat")); return; }
                java.math.BigDecimal total;
                try {
                    total = new java.math.BigDecimal(args[3]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(fullMsg("redpacket.invalid-amount"));
                    return;
                }
                int count;
                try {
                    count = Integer.parseInt(args[4]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(fullMsg("redpacket.invalid-count", 50));
                    return;
                }
                String wish = args.length >= 6 ? joinArgs(args, 5) : "";
                result = rp.sendAdminPacket(sender, RedPacketType.parse(sub), total, count, wish);
            }
            default -> {
                Integer count = null;
                if (args.length >= 4) {
                    try {
                        count = Integer.parseInt(args[3]);
                    } catch (NumberFormatException exception) {
                        sender.sendMessage(fullMsg("redpacket.invalid-count", 50));
                        return;
                    }
                }
                result = rp.sendPresetPacket(sender, sub, count);
            }
        }
        sender.sendMessage((messages != null ? messages.get("prefix") : "")
            + (result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private Instant parseDuration(String input) {
        if ("permanent".equalsIgnoreCase(input)) return null;
        Matcher m = DURATION_PATTERN.matcher(input.toLowerCase(Locale.ROOT));
        if (!m.matches()) return Instant.now().plus(Duration.ofDays(1));
        long val = Long.parseLong(m.group(1));
        return Instant.now().plus(switch (m.group(2)) {
            case "s" -> Duration.ofSeconds(val);
            case "m" -> Duration.ofMinutes(val);
            case "h" -> Duration.ofHours(val);
            case "d" -> Duration.ofDays(val);
            default -> Duration.ofDays(1);
        });
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) { if (i > from) sb.append(' '); sb.append(args[i]); }
        return sb.toString();
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
