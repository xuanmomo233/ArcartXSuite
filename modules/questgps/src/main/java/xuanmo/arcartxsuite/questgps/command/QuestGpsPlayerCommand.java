package xuanmo.arcartxsuite.questgps.command;

import java.util.List;
import java.util.function.Supplier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.questgps.service.QuestGpsService;

/**
 * QuestGPS 模块的玩家命令处理器。
 * <p>
 * 属于 {@code questgps} 模块，提供 {@code open}（打开 GPS 菜单）和
 * {@code cleartrack}（清除当前导航追踪）两个子操作，仅限玩家执行。
 */
public final class QuestGpsPlayerCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> ROOT_ACTIONS = List.of("open", "cleartrack");

    private final Supplier<QuestGpsService> serviceProvider;
    private final MessageProvider messages;

    /**
     * 构造玩家命令处理器。
     *
     * @param serviceProvider QuestGPS 服务提供者，服务未就绪时返回 {@code null}
     * @param messages         消息提供者，用于拼接本地化提示文本
     */
    public QuestGpsPlayerCommand(Supplier<QuestGpsService> serviceProvider, MessageProvider messages) {
        this.serviceProvider = serviceProvider;
        this.messages = messages;
    }

    private String fullMsg(String key, Object... args) {
        if (messages == null) return "";
        return messages.get("prefix") + messages.get(key, args);
    }

    /**
     * 处理玩家命令，校验权限后分发到 {@code open} 或 {@code cleartrack}。
     *
     * @param sender  命令发送者，非玩家时提示仅玩家可用
     * @param command 命令对象
     * @param label   命令标签
     * @param args    参数数组，{@code args[0]} 为子操作名
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return true;
        }
        if (!player.hasPermission("arcartxsuite.questgps.use")) {
            player.sendMessage(fullMsg("common.no-permission"));
            return true;
        }
        QuestGpsService svc = serviceProvider.get();
        if (svc == null) {
            player.sendMessage(fullMsg("common.service-down"));
            return true;
        }

        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            svc.openMenu(player);
            return true;
        }
        if ("cleartrack".equalsIgnoreCase(args[0])) {
            svc.clearTrack(player);
            return true;
        }
        player.sendMessage(fullMsg("player.usage", label));
        return true;
    }

    /**
     * 提供子操作名的 Tab 补全。
     *
     * @param sender 命令发送者
     * @param command 命令对象
     * @param alias  命令别名
     * @param args   当前参数数组
     * @return 补全候选列表，无匹配时返回空列表
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
}
