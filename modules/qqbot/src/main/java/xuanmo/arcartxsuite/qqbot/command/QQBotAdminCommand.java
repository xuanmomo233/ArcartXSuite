package xuanmo.arcartxsuite.qqbot.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager;
import xuanmo.arcartxsuite.qqbot.service.QQBotService;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.QQBotBinding;

public final class QQBotAdminCommand {

    private static final List<String> SNOWLUMA_ACTIONS = List.of("start", "stop", "status", "install", "logs", "check-update");

    private final Supplier<QQBotService> serviceProvider;
    private final Supplier<QQBotRepository> repositoryProvider;
    private final SnowLumaProcessManager snowLuma;
    private final MessageProvider messages;

    public QQBotAdminCommand(Supplier<QQBotService> serviceProvider, Supplier<QQBotRepository> repositoryProvider, SnowLumaProcessManager snowLuma, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.repositoryProvider = repositoryProvider;
        this.snowLuma = snowLuma;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    public List<String> actions() {
        return List.of("help", "status", "reload", "send", "lookup", "snowluma", "blacklist");
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        QQBotService service = serviceProvider.get();
        String action = args[1].toLowerCase();

        switch (action) {
            case "help" -> sendHelp(sender);
            case "status" -> {
                if (service == null) {
                    sender.sendMessage(fullMsg("common.service-down"));
                    return true;
                }
                sender.sendMessage(fullMsg("status.title"));
                sender.sendMessage(fullMsg("status.connection", service.isConnected() ? fullMsg("status.connected") : fullMsg("status.disconnected")));
                sender.sendMessage(fullMsg("status.groups", service.configuration().groups().size()));
                sender.sendMessage(fullMsg("status.storage", service.configuration().storage().mode()));
                sender.sendMessage(fullMsg("status.binding", service.configuration().binding().enabled() ? fullMsg("status.enabled") : fullMsg("status.disabled")));
                sender.sendMessage(fullMsg("status.whitelist", service.configuration().whitelist().enabled() ? fullMsg("status.enabled") : fullMsg("status.disabled")));
            }
            case "send" -> {
                if (service == null) {
                    sender.sendMessage(fullMsg("common.service-down"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(fullMsg("admin.send.usage"));
                    return true;
                }
                String target = args[2];
                int msgStart = 3;
                boolean toAll = "all".equalsIgnoreCase(target);
                long groupId = 0;
                if (!toAll) {
                    try {
                        groupId = Long.parseLong(target);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(fullMsg("admin.send.invalid-group", target));
                        return true;
                    }
                }
                if (args.length < msgStart + 1) {
                    sender.sendMessage(fullMsg("admin.send.usage"));
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = msgStart; i < args.length; i++) {
                    if (i > msgStart) sb.append(" ");
                    sb.append(args[i]);
                }
                String message = sb.toString();
                if (toAll) {
                    service.sendToAllGroups(message);
                    sender.sendMessage(fullMsg("admin.send.success-all"));
                } else {
                    service.sendToGroup(groupId, message);
                    sender.sendMessage(fullMsg("admin.send.success-group", groupId));
                }
            }
            case "lookup" -> {
                if (service == null) {
                    sender.sendMessage(fullMsg("common.service-down"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(fullMsg("admin.lookup.usage"));
                    return true;
                }
                String query = args[2];
                // 尝试解析为 QQ 号
                try {
                    long qqId = Long.parseLong(query);
                    QQBotBinding binding = service.bindService().findByQq(qqId);
                    if (binding != null) {
                        sender.sendMessage(fullMsg("admin.lookup.by-qq-success", qqId, binding.playerName()));
                    } else {
                        sender.sendMessage(fullMsg("admin.lookup.by-qq-none", qqId));
                    }
                } catch (NumberFormatException e) {
                    QQBotBinding binding = service.bindService().findByPlayerName(query);
                    if (binding != null) {
                        sender.sendMessage(fullMsg("admin.lookup.by-player-success", query, binding.qqId()));
                    } else {
                        sender.sendMessage(fullMsg("admin.lookup.by-player-none", query));
                    }
                }
            }
            case "snowluma" -> handleSnowLuma(sender, args);
            case "blacklist" -> handleBlacklist(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleSnowLuma(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.snowluma.title"));
            sender.sendMessage(fullMsg("admin.snowluma.start"));
            sender.sendMessage(fullMsg("admin.snowluma.stop"));
            sender.sendMessage(fullMsg("admin.snowluma.status"));
            sender.sendMessage(fullMsg("admin.snowluma.install"));
            sender.sendMessage(fullMsg("admin.snowluma.logs"));
            sender.sendMessage(fullMsg("admin.snowluma.check-update"));
            return;
        }
        String sub = args[2].toLowerCase();
        switch (sub) {
            case "start" -> sender.sendMessage((messages != null ? messages.get("prefix") : "") + snowLuma.start());
            case "stop" -> sender.sendMessage((messages != null ? messages.get("prefix") : "") + snowLuma.stop());
            case "status" -> sender.sendMessage(snowLuma.statusReport());
            case "install" -> {
                sender.sendMessage(fullMsg("admin.snowluma.install-start"));
                snowLuma.installAsync().thenAccept(result -> {
                    sender.sendMessage((messages != null ? messages.get("prefix") : "") + result);
                });
            }
            case "logs" -> {
                String logOutput = snowLuma.logs();
                for (String line : logOutput.split("\n")) {
                    sender.sendMessage(line);
                }
            }
            case "check-update" -> {
                sender.sendMessage("&e正在检查 SnowLuma 版本...");
                snowLuma.checkUpdateAsync().thenAccept(result -> {
                    sender.sendMessage((messages != null ? messages.get("prefix") : "") + result);
                });
            }
            default -> sender.sendMessage(fullMsg("admin.snowluma.unknown", sub));
        }
    }

    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            String prefix = args[1].toLowerCase();
            for (String action : actions()) {
                if (action.startsWith(prefix)) completions.add(action);
            }
            return completions;
        }
        if (args.length == 3 && "snowluma".equalsIgnoreCase(args[1])) {
            List<String> completions = new ArrayList<>();
            String prefix = args[2].toLowerCase();
            for (String action : SNOWLUMA_ACTIONS) {
                if (action.startsWith(prefix)) completions.add(action);
            }
            return completions;
        }
        if (args.length == 3 && "blacklist".equalsIgnoreCase(args[1])) {
            List<String> completions = new ArrayList<>();
            String prefix = args[2].toLowerCase();
            for (String action : List.of("add", "remove", "list")) {
                if (action.startsWith(prefix)) completions.add(action);
            }
            return completions;
        }
        if (args.length == 4 && "blacklist".equalsIgnoreCase(args[1])) {
            return List.of(); // QQ号补全暂无
        }
        return null;
    }

    private void handleBlacklist(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.blacklist.usage"));
            return;
        }
        QQBotRepository repo = repositoryProvider.get();
        if (repo == null) {
            sender.sendMessage(fullMsg("common.service-down"));
            return;
        }
        String sub = args[2].toLowerCase();
        switch (sub) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(fullMsg("admin.blacklist.usage"));
                    return;
                }
                long qq = parseLong(args[3]);
                if (qq <= 0) {
                    sender.sendMessage(fullMsg("admin.blacklist.invalid-qq", args[3]));
                    return;
                }
                repo.addBlacklist(qq, 0);
                sender.sendMessage(fullMsg("admin.blacklist.added", qq));
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage(fullMsg("admin.blacklist.usage"));
                    return;
                }
                long qq = parseLong(args[3]);
                if (qq <= 0) {
                    sender.sendMessage(fullMsg("admin.blacklist.invalid-qq", args[3]));
                    return;
                }
                repo.removeBlacklist(qq);
                sender.sendMessage(fullMsg("admin.blacklist.removed", qq));
            }
            case "list" -> {
                List<Long> list = repo.getBlacklist();
                if (list.isEmpty()) {
                    sender.sendMessage(fullMsg("admin.blacklist.empty"));
                    return;
                }
                StringBuilder sb = new StringBuilder(fullMsg("admin.blacklist.list-header", list.size()));
                for (Long qq : list) {
                    sb.append("\n").append(fullMsg("admin.blacklist.list-item", qq));
                }
                sender.sendMessage(sb.toString());
            }
            default -> sender.sendMessage(fullMsg("admin.blacklist.usage"));
        }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status"));
        sender.sendMessage(fullMsg("help.reload"));
        sender.sendMessage(fullMsg("help.send"));
        sender.sendMessage(fullMsg("help.lookup"));
        sender.sendMessage(fullMsg("help.snowluma"));
        sender.sendMessage(fullMsg("help.blacklist"));
    }
}
