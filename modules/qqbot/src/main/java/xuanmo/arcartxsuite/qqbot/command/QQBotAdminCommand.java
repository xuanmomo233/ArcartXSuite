package xuanmo.arcartxsuite.qqbot.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.qqbot.process.SnowLumaProcessManager;
import xuanmo.arcartxsuite.qqbot.service.QQBotService;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository;
import xuanmo.arcartxsuite.qqbot.storage.QQBotRepository.RedemptionResolution;

/** Administrative operations keep database and process IO away from the server thread. */
public final class QQBotAdminCommand {
    private static final List<String> SNOWLUMA_ACTIONS = List.of("start", "stop", "status", "install", "logs", "password", "check-update");
    private final JavaPlugin plugin;
    private final Supplier<QQBotService> serviceProvider;
    private final Supplier<QQBotRepository> repositoryProvider;
    private final SnowLumaProcessManager snowLuma;
    private final MessageProvider messages;

    public QQBotAdminCommand(JavaPlugin plugin, Supplier<QQBotService> serviceProvider,
                             Supplier<QQBotRepository> repositoryProvider, SnowLumaProcessManager snowLuma, MessageProvider messages) {
        this.plugin = plugin; this.serviceProvider = serviceProvider; this.repositoryProvider = repositoryProvider;
        this.snowLuma = snowLuma; this.messages = messages;
    }
    private String msg(String key, Object... args) { return messages == null ? key : messages.get("prefix") + messages.get(key, args); }
    public List<String> actions() { return List.of("help", "status", "send", "lookup", "snowluma", "blacklist", "redemptions"); }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("arcartxsuite.admin") && !sender.hasPermission("arcartxsuite.qqbot.admin")) {
            sender.sendMessage(msg("common.no-permission")); return true;
        }
        if (args.length < 2 || "help".equalsIgnoreCase(args[1])) { help(sender, label); return true; }
        QQBotService service = serviceProvider.get();
        if (service == null) { sender.sendMessage(msg("common.service-down")); return true; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                sender.sendMessage(msg("status.title"));
                sender.sendMessage(msg("status.connection", service.isConnected() ? "§a已连接" : "§c已断开"));
                sender.sendMessage(msg("status.groups", service.configuration().groups().size()));
                sender.sendMessage(msg("status.storage", service.configuration().storage().mode()));
                sender.sendMessage(msg("status.binding", service.configuration().binding().enabled() ? "§a启用" : "§7停用"));
                if (snowLuma != null) io(sender, service, snowLuma::statusReport);
            }
            case "send" -> {
                if (args.length < 4) { sender.sendMessage(msg("admin.send.usage")); return true; }
                Long group = null;
                if (!"all".equalsIgnoreCase(args[2])) {
                    try { group = Long.parseLong(args[2]); if (group <= 0) throw new NumberFormatException(); }
                    catch (NumberFormatException error) { sender.sendMessage(msg("admin.send.invalid-group", args[2])); return true; }
                }
                sender.sendMessage(msg("admin.send.pending"));
                service.sendAdminMessage(group, String.join(" ", Arrays.copyOfRange(args, 3, args.length)))
                    .whenComplete((result, error) -> reply(sender, service, error == null ? (result.success() ? "§a" : "§c") + result.message()
                        : msg("common.operation-failed")));
            }
            case "lookup" -> {
                if (args.length != 3) { sender.sendMessage(msg("admin.lookup.usage")); return true; }
                String query = args[2];
                io(sender, service, () -> {
                    QQBotRepository repo = repositoryProvider.get();
                    if (repo == null) return msg("common.service-down");
                    try {
                        long qq = Long.parseLong(query);
                        List<QQBotRepository.QQBotBinding> rows = repo.findAllByQq(qq);
                        if (rows.isEmpty()) return msg("admin.lookup.by-qq-none", qq);
                        return "§bQQ " + qq + "：§f" + rows.stream().map(QQBotRepository.QQBotBinding::playerName).reduce((a,b) -> a + ", " + b).orElse("");
                    } catch (NumberFormatException error) {
                        var row = repo.findByPlayerName(query);
                        return row == null ? msg("admin.lookup.by-player-none", query) : msg("admin.lookup.by-player-success", query, row.qqId());
                    }
                });
            }
            case "blacklist" -> blacklist(sender, service, args);
            case "snowluma" -> process(sender, service, args);
            case "redemptions" -> redemptions(sender, service, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void blacklist(CommandSender sender, QQBotService service, String[] args) {
        if (args.length < 3) { sender.sendMessage(msg("admin.blacklist.usage")); return; }
        String action = args[2].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            io(sender, service, () -> {
                var repo = repositoryProvider.get();
                if (repo == null) return msg("common.service-down");
                var rows = repo.getBlacklist();
                return rows.isEmpty() ? msg("admin.blacklist.empty") : "§bQQ 黑名单：§f" + rows;
            });
            return;
        }
        if (args.length != 4 || !List.of("add", "remove").contains(action)) { sender.sendMessage(msg("admin.blacklist.usage")); return; }
        long qq;
        try { qq = Long.parseLong(args[3]); if (qq <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException error) { sender.sendMessage(msg("admin.blacklist.invalid-qq", args[3])); return; }
        io(sender, service, () -> {
            var repo = repositoryProvider.get();
            if (repo == null) return msg("common.service-down");
            if ("add".equals(action)) repo.addBlacklist(qq, 0); else repo.removeBlacklist(qq);
            return msg("admin.blacklist." + ("add".equals(action) ? "added" : "removed"), qq);
        });
    }

    private void process(CommandSender sender, QQBotService service, String[] args) {
        if (snowLuma == null) { sender.sendMessage(msg("common.service-down")); return; }
        if (args.length < 3) {
            for (String key : List.of("title", "install", "start", "stop", "status", "logs", "password", "check-update"))
                sender.sendMessage(msg("admin.snowluma." + key));
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "start" -> io(sender, service, snowLuma::start);
            case "stop" -> io(sender, service, snowLuma::stop);
            case "status" -> io(sender, service, snowLuma::statusReport);
            case "logs" -> io(sender, service, snowLuma::logs);
            case "password" -> {
                if (args.length >= 4 && "reset".equalsIgnoreCase(args[3])) io(sender, service, snowLuma::resetWebUiPassword);
                else io(sender, service, snowLuma::webUiPasswordReport);
            }
            case "install" -> {
                sender.sendMessage(msg("admin.snowluma.install-start"));
                snowLuma.installAsync(progress -> reply(sender, service, "§7" + progress))
                    .whenComplete((result, error) -> reply(sender, service, error == null ? result : msg("common.operation-failed")));
            }
            case "check-update" -> {
                sender.sendMessage(msg("admin.snowluma.checking"));
                snowLuma.checkUpdateAsync().whenComplete((result, error) ->
                    reply(sender, service, error == null ? result : msg("common.operation-failed")));
            }
            default -> sender.sendMessage(msg("admin.snowluma.unknown", args[2]));
        }
    }

    private void redemptions(CommandSender sender, QQBotService service, String[] args) {
        var signIn = service.getSignInService();
        if (signIn == null) { sender.sendMessage("§c签到与积分服务未启用。"); return; }
        String action = args.length < 3 ? "list" : args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                int limit = 20;
                if (args.length >= 4) {
                    try { limit = Integer.parseInt(args[3]); }
                    catch (NumberFormatException error) { sender.sendMessage("§c数量须为 1–100 的整数。"); return; }
                }
                if (limit < 1 || limit > 100) { sender.sendMessage("§c数量须为 1–100 的整数。"); return; }
                int count = limit;
                io(sender, service, () -> {
                    var rows = signIn.listRedemptions(count);
                    if (rows.isEmpty()) return "§a没有待复核的兑换记录。";
                    StringBuilder output = new StringBuilder("§e待复核兑换（请先逐项核对邮件实际投递结果）：");
                    for (var row : rows) output.append("\n§f").append(row.id()).append(" §7").append(row.playerName())
                        .append(" / ").append(row.prizeId()).append(" / ").append(row.state());
                    return output.toString();
                });
            }
            case "get" -> {
                if (args.length != 4) { redemptionUsage(sender); return; }
                io(sender, service, () -> {
                    var row = signIn.getRedemption(args[3]);
                    if (row == null) return "§c兑换记录不存在。";
                    StringBuilder output = new StringBuilder("§b兑换 ").append(row.id()).append("\n§f玩家 ")
                        .append(row.playerName()).append(" / QQ ").append(row.qqId()).append("\n§f奖品 ")
                        .append(row.prizeId()).append(" / 扣款 ").append(row.cost()).append(" / 状态 ").append(row.state());
                    for (var item : row.items()) output.append("\n§7#").append(item.ordinal()).append(" ").append(item.presetId())
                        .append(" / ").append(item.state()).append(" / ").append(item.detail());
                    output.append("\n§7备注：").append(row.note());
                    return output.toString();
                });
            }
            case "resolve" -> {
                if (args.length < 7 || !"confirm".equalsIgnoreCase(args[5])) { redemptionUsage(sender); return; }
                RedemptionResolution decision;
                if ("delivered".equalsIgnoreCase(args[4])) decision = RedemptionResolution.CONFIRM_DELIVERED;
                else if ("not-delivered".equalsIgnoreCase(args[4])) decision = RedemptionResolution.CONFIRM_NOT_DELIVERED;
                else { redemptionUsage(sender); return; }
                String note = String.join(" ", Arrays.copyOfRange(args, 6, args.length)).trim();
                if (note.length() < 4 || note.length() > 500) { sender.sendMessage("§c核对备注需为 4–500 个字符。"); return; }
                String actor = sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
                io(sender, service, () -> signIn.resolveRedemption(args[3], decision, "actor=" + actor + "; " + note));
            }
            default -> redemptionUsage(sender);
        }
    }
    private void redemptionUsage(CommandSender sender) {
        sender.sendMessage("§e/axs qqbot redemptions list [1–100]");
        sender.sendMessage("§e/axs qqbot redemptions get <兑换ID>");
        sender.sendMessage("§e/axs qqbot redemptions resolve <兑换ID> delivered|not-delivered confirm <核对备注>");
        sender.sendMessage("§7delivered：已核对并手动补齐全部邮件，保留扣款结案；not-delivered：已独立核实全部未发，申请退款。");
    }

    private void io(CommandSender sender, QQBotService expected, Supplier<String> work) {
        expected.submitIo(work).whenComplete((result, error) -> {
            if (serviceProvider.get() != expected) return;
            if (error == null) reply(sender, expected, result);
            else {
                plugin.getLogger().warning("QQBot 管理操作失败: " + error.getMessage());
                reply(sender, expected, msg("common.operation-failed"));
            }
        });
    }
    private void reply(CommandSender sender, QQBotService expected, String text) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (serviceProvider.get() != expected) return;
            if (sender instanceof Player player && (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player)) return;
            for (String line : text.split("\n")) sender.sendMessage(line);
        });
    }
    @Nullable public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        List<String> choices = List.of();
        if (args.length == 2) choices = actions();
        else if (args.length == 3 && "snowluma".equalsIgnoreCase(args[1])) choices = SNOWLUMA_ACTIONS;
        else if (args.length == 3 && "blacklist".equalsIgnoreCase(args[1])) choices = List.of("add", "remove", "list");
        else if (args.length == 3 && "redemptions".equalsIgnoreCase(args[1])) choices = List.of("list", "get", "resolve");
        else if (args.length == 5 && "redemptions".equalsIgnoreCase(args[1]) && "resolve".equalsIgnoreCase(args[2]))
            choices = List.of("delivered", "not-delivered");
        else if (args.length == 6 && "redemptions".equalsIgnoreCase(args[1]) && "resolve".equalsIgnoreCase(args[2])) choices = List.of("confirm");
        else if (args.length == 4 && "snowluma".equalsIgnoreCase(args[1]) && "password".equalsIgnoreCase(args[2])) choices = List.of("reset");
        else if (args.length == 3 && "send".equalsIgnoreCase(args[1])) {
            choices = new ArrayList<>(List.of("all"));
            var service = serviceProvider.get();
            if (service != null) for (var group : service.configuration().groups()) choices.add(Long.toString(group.groupId()));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.startsWith(prefix)).toList();
    }
    private void help(CommandSender sender, String label) {
        String command = "/" + label + " qqbot";
        sender.sendMessage(msg("help.title"));
        for (String action : List.of("status", "send", "lookup", "snowluma", "blacklist", "redemptions"))
            sender.sendMessage(msg("help." + action, command));
    }
}
