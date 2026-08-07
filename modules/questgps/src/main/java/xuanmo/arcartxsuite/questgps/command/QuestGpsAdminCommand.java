package xuanmo.arcartxsuite.questgps.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.questgps.service.QuestGpsService;

/**
 * QuestGPS 模块的管理命令处理器。
 * <p>
 * 属于 {@code questgps} 模块，提供 {@code help}、{@code status}、{@code reload}、{@code open}
 * 四个子操作，供管理员查看模块运行状态或为指定玩家打开 GPS 菜单。
 */
public final class QuestGpsAdminCommand implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of("help", "status", "reload", "open");

    private final Supplier<QuestGpsService> serviceProvider;
    private final MessageProvider messages;

    /**
     * 构造管理命令处理器。
     *
     * @param serviceProvider QuestGPS 服务提供者，服务未就绪时返回 {@code null}
     * @param messages         消息提供者，用于拼接本地化提示文本
     */
    public QuestGpsAdminCommand(Supplier<QuestGpsService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /** @return 命令标识符，固定为 {@code "questgps"} */
    @Override public String commandId() { return "questgps"; }
    /** @return 支持的子操作列表 */
    @Override public List<String> actions() { return ACTIONS; }

    /**
     * 处理管理命令，根据第二个参数分发到对应子操作。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   参数数组，{@code args[1]} 为子操作名
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "reload" -> sender.sendMessage(fullMsg("common.reload-hint", label));
            case "open" -> handleOpen(sender, args);
            default -> sender.sendMessage(fullMsg("common.unknown", label));
        }
        return true;
    }

    /**
     * 提供子操作名的 Tab 补全。
     *
     * @param sender 命令发送者
     * @param args   当前参数数组
     * @return 补全候选列表，无匹配时返回空列表
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " questgps";
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(fullMsg("help.status", cmd));
        sender.sendMessage(fullMsg("help.open", cmd));
    }

    private void sendStatus(CommandSender sender) {
        QuestGpsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        sender.sendMessage(fullMsg("status.title"));
        sender.sendMessage(fullMsg("status.configured-quests", svc.configuredQuestCount()));
        sender.sendMessage(fullMsg("status.tracking-players", svc.trackingPlayerCount()));
        sender.sendMessage(fullMsg("status.nav-ready", svc.navigationRuntimeReady()));
    }

    private void handleOpen(CommandSender sender, String[] args) {
        QuestGpsService svc = serviceProvider.get();
        if (svc == null) { sender.sendMessage(fullMsg("common.service-down")); return; }
        if (args.length < 3) { sender.sendMessage(fullMsg("open.usage")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { sender.sendMessage(fullMsg("open.not-online", args[2])); return; }
        svc.openMenu(target);
        sender.sendMessage(fullMsg("open.success", target.getName()));
    }

    private static List<String> filter(List<String> candidates, String input) {
        String n = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        for (String c : candidates) if (c.toLowerCase(Locale.ROOT).startsWith(n)) r.add(c);
        return r;
    }
}
