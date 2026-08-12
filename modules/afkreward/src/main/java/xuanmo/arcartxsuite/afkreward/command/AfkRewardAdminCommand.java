package xuanmo.arcartxsuite.afkreward.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.afkreward.service.AfkRewardService;

/**
 * 挂机奖励管理（admin）命令处理器。
 * <p>
 * 关联模块：afkreward。提供 {@code help}、{@code status}、{@code reload} 等管理子命令，
 * 通过 {@link xuanmo.arcartxsuite.api.ModuleCommandHandler} 接口由模块主类委托调用。
 */
public final class AfkRewardAdminCommand implements ModuleCommandHandler {

    private final Supplier<AfkRewardService> serviceSupplier;
    private final MessageProvider messages;

    /**
     * 构造管理命令处理器。
     *
     * @param serviceSupplier 挂机奖励服务提供者（服务可能尚未就绪，故使用 Supplier 延迟获取）
     * @param messages        消息提供者，用于读取本地化提示文本
     */
    public AfkRewardAdminCommand(Supplier<AfkRewardService> serviceSupplier, MessageProvider messages) {
        this.serviceSupplier = serviceSupplier;
        this.messages = messages;
    }

    private String msg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    @Override
    public String commandId() {
        return "afkreward";
    }

    /**
     * 返回本处理器支持的子命令列表。
     *
     * @return 子命令标识列表
     */
    @Override
    public List<String> actions() {
        return List.of("help", "status");
    }

    /**
     * 处理管理命令。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   参数数组（{@code args[1]} 为子命令）
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("admin.unknown", label));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);

        switch (action) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            default -> sender.sendMessage(msg("admin.unknown", label));
        }
        return true;
    }

    /**
     * 提供子命令补全。
     *
     * @param sender 命令发送者
     * @param args   当前参数数组
     * @return 补全候选列表；无补全时返回空列表
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) {
            return filter(actions(), args[1]);
        }
        return List.of();
    }

    private void sendHelp(CommandSender sender, String label) {
        String cmd = "/" + label + " afkreward";
        sender.sendMessage(msg("help.title"));
        sender.sendMessage(msg("help.status", cmd));
    }

    private void sendStatus(CommandSender sender) {
        AfkRewardService service = serviceSupplier.get();
        if (service == null) {
            sender.sendMessage(msg("common.service-down"));
            return;
        }
        sender.sendMessage(msg("admin.status.title"));
        sender.sendMessage(msg("admin.status.areas", service.areas().size()));
        sender.sendMessage(msg("admin.status.types", service.types().size()));
        int online = 0;
        for (String area : service.areas().keySet()) {
            online += service.getPlayersInArea(area);
        }
        sender.sendMessage(msg("admin.status.online", online));
    }

    private static List<String> filter(List<String> list, String input) {
        String norm = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase(Locale.ROOT).startsWith(norm)) result.add(s);
        }
        return result;
    }
}
